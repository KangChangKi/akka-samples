package sample.cluster.pr_factorial_1

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.Cluster
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try
import scala.concurrent._

object FactorialFrontend {
  def main(args: Array[String]): Unit = {
    val upToN = 200

    val config = ConfigFactory
      .parseString(
        s"""
         akka.cluster.roles = [frontend]
       """)
      .withFallback(ConfigFactory.load("pr_factorial_1/application"))

    val system = ActorSystem("ClusterSystem", config)

    Cluster(system) registerOnMemberUp {
      system.actorOf(Props(classOf[FactorialFrontend], upToN, true), name="factorialFrontend")

      system.actorOf(Props[MetricsListener], name="metricsListener")
    }

    Cluster(system) registerOnMemberRemoved {
      system.registerOnTermination(System.exit(0))
      system.terminate()

      import ExecutionContext.Implicits.global

      // In case ActorSystem shutdown takes longer than 10 seconds,
      // exit the JVM forcefully anyway.
      // We must spawn a separate thread to not block current thread,
      // since that would have blocked the shutdown of the ActorSystem.
      Future {
        if (Try(Await.ready(system.whenTerminated, 10 seconds)).isFailure)
          System.exit(-1)
      }
    }
  }
}

class FactorialFrontend(upToN: Int, repeat: Boolean) extends Actor
  with ActorLogging {

  // path: /factorialFrontend/factorialBackendRouter
  val backend = context.actorOf(FromConfig.props(), name = "factorialBackendRouter")

  override def preStart(): Unit = {
    sendJobs()
    if (repeat) {
      context.setReceiveTimeout(10 seconds)
    }
  }

  def receive = {
    case (n: Int, factorial: BigInt) =>
      if (n == upToN) {
        log.debug("{}! = {}", n, factorial)
        if (repeat) sendJobs()
        else context.stop(self)
      }

    case ReceiveTimeout =>
      log.info("Timeout")
      sendJobs()
  }

  def sendJobs(): Unit = {
    log.info("Starting batch of factorials up to [{}]", upToN)
    1 to upToN foreach {
      backend ! _
    }
  }
}
