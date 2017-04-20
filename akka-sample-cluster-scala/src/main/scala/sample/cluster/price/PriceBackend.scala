package sample.cluster.price

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.pipe
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Promise}

object PriceBackend {
  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory
      .parseString(
      s"""
          akka.remote.netty.tcp.port=$port
          akka.cluster.roles = [backend]
       """)
      .withFallback(ConfigFactory.load("price/application"))

    val system = ActorSystem("ClusterSystem", config)

    system.actorOf(Props[PriceBackend], name="priceBackend")

    system.actorOf(Props[MetricsListener], name="metricsListener")
  }

}

object PriceBackendMsg {
  case class Order(size: Int, quantity: Int, coupon: Boolean)
}

class PriceBackend extends Actor
  with ActorLogging {

  import PriceBackendMsg._
  implicit val executionContext: ExecutionContext = context.dispatcher

  def calculatePrice(promise: Promise[String], size: Int, quantity: Int, coupon: Boolean): Unit = {

    val basePrice = (size match {
      case 1 => 10
      case 2 => 15
      case 3 => 20
    }) * quantity

    val res = if (coupon) (basePrice * 0.9).toInt
    else basePrice

    promise.success(s"total cost: $res")
  }

  def receive = {
    case Order(_, quantity, _) if quantity <= 0 => // do nothing
    case Order(size, _, _) if size > 3 || size < 1 => // do nothing
    case Order(size, quantity, coupon) =>
      val promise = Promise[String]()
      promise.future pipeTo sender()

      calculatePrice(promise, size, quantity, coupon)
  }
}
