package sample.cluster.price

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.Cluster
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, _}
import scala.util.{Random, Try}

object PriceFrontend {
  def main(args: Array[String]): Unit = {
    val config = ConfigFactory
      .parseString(
        s"""
         akka.cluster.roles = [frontend]
       """)
      .withFallback(ConfigFactory.load("price/application"))

    val system = ActorSystem("ClusterSystem", config)

    Cluster(system) registerOnMemberUp {
      system.actorOf(Props(classOf[PriceFrontend]), name="priceFrontend")

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

object PriceFrontendMsg {
  case object Tick
}

class PriceFrontend() extends Actor
  with ActorLogging {

  import PriceBackendMsg._
  import PriceFrontendMsg._

  // path: /priceFrontend/priceBackendRouter
  val backend = context.actorOf(FromConfig.props(), name = "priceBackendRouter")

  implicit val executionContext: ExecutionContext = context.system.dispatcher

  override def preStart(): Unit = {
    // context.setReceiveTimeout(10 seconds)

    context.system.scheduler.schedule(1 second, 1 second, self, Tick)
  }

  val r = Random

  def sendMessage() = {
    val size = r.nextInt(2) + 1
    val quantity = r.nextInt(5) + 1
    val coupon = r.nextBoolean()

    implicit val timeout: Timeout = 5 seconds

    val res = (backend ? Order(size, quantity, coupon)).mapTo[String]
    res foreach { e => log.info(e) }
  }

  def receive = {
    case Tick =>
      1 to 5000 foreach { e =>
        sendMessage()
      }

    case ReceiveTimeout =>
      log.info("Timeout")
  }
}
