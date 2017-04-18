package sample.cluster.pr_factorial_1

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.annotation.tailrec
import scala.concurrent.Future
import akka.pattern._

object FactorialBackend {
  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory
      .parseString(
      s"""
          akka.remote.netty.tcp.port=$port
          akka.cluster.roles = [backend]
       """)
      .withFallback(ConfigFactory.load("pr_factorial_1/application"))

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[FactorialBackend], name="factorialBackend")

    system.actorOf(Props[MetricsListener], name="metricsListener")
  }

}

class FactorialBackend extends Actor
  with ActorLogging {

  import context.dispatcher

  def receive = {
    case (n: Int) =>
      Future {
        print(".")
        factorial(n)
      } map { res => (n, res) } pipeTo sender()
  }

  def factorial(n: Int): BigInt = {
    @tailrec def factorialAcc(acc: BigInt, n: Int): BigInt = {
      if (n <= 1) acc
      else factorialAcc(acc * n, n - 1)
    }
    factorialAcc(BigInt(1), n)
  }

}
