package sample.cluster.pr_factorial_1

import akka.actor.Props
import akka.cluster.Cluster
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

object FactorialSpecConfig extends MultiNodeConfig {
  val backend1 = role("backend1")
  val backend2 = role("backend2")
  val backend3 = role("backend3")

  val frontend1 = role("frontend1")
  val frontend2 = role("frontend2")

  def nodeList = Seq(backend1, backend2, backend3, frontend1, frontend2)

  nodeList foreach { role =>
    nodeConfig(role) {
      ConfigFactory.parseString(
        s"""
           akka.cluster.metrics.enabled=off
           akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
           akka.cluster.metrics.native-library-extract-folder=target/native/${role.name}
         """.stripMargin)
    }
  }

  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString(s"""
    akka.actor.provider = cluster
    akka.remote.log-remote-lifecycle-events = off
    """.stripMargin))

  nodeConfig(frontend1, frontend2) {
    ConfigFactory.parseString("akka.cluster.roles = [frontend]")
  }
  nodeConfig(backend1, backend2, backend3) {
    ConfigFactory.parseString("akka.cluster.roles = [backend]")
  }
}

// need one concrete test class per node
class FactorialSpecMultiJvmNode1 extends FactorialSpec
class FactorialSpecMultiJvmNode2 extends FactorialSpec
class FactorialSpecMultiJvmNode3 extends FactorialSpec
class FactorialSpecMultiJvmNode4 extends FactorialSpec
class FactorialSpecMultiJvmNode5 extends FactorialSpec

abstract class FactorialSpec extends MultiNodeSpec(FactorialSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  import FactorialSpecConfig._

  "The factorial example" must {
    "illustrate how to start first frontend" in within(15 seconds) {
//      runOn(backend1) {
//        Cluster(system) join node(backend1).address
//        system.actorOf(Props[FactorialBackend], name="backend")
//      }
//      runOn(backend2) {
//        Cluster(system) join node(backend2).address
//        system.actorOf(Props[FactorialBackend], name="backend")
//      }

      runOn(frontend1) {
        // this will only run on the 'first' node
        Cluster(system) join node(frontend1).address

        val factorialFrontend = system.actorOf(Props[FactorialFrontend], name="frontend")
        factorialFrontend ! "hello"
        expectMsg("hello")
      }

      // this will run on all nodes
      // use barrier to coordinate test steps
      // testConductor.enter("frontend1-started")
    }
  }

}