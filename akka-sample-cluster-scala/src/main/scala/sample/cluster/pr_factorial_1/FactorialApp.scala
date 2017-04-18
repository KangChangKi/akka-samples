package sample.cluster.pr_factorial_1

object FactorialApp {
  def main(args: Array[String]): Unit = {
    // backend: seed nodes
    FactorialBackend.main(Seq("2551").toArray)
    FactorialBackend.main(Seq("2552").toArray)

    // backend: rest
    FactorialBackend.main(Array.empty)

    // frontend
    FactorialFrontend.main(Array.empty)
    FactorialFrontend.main(Array.empty)
  }
}
