package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{FlowShape, Materializer, SinkShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * User: patricio
 * Date: 7/8/21
 * Time: 11:57
 */
object GraphMaterializedValues extends App {

  val system = ActorSystem("GraphMaterializedValues")
  implicit val materializer: Materializer = Materializer(system)

  val source = Source(List("Akka", "Is", "Awesome", "rock", "the", "JVM"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((a, _) => a + 1)

  /*
  A composite component (sink)
  - prints our all string which are lowercase
  - COUNTS the strings that are short (<5 chars)
   */

  val complexWordSink = GraphDSL.create(counter) { implicit builder =>
    counterShape =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFilter = builder.add(Flow[String].filter(str => str == str.toLowerCase))
      val counterBelowFiveFilter = builder.add(Flow[String].filter(_.length < 5))
      val sink = builder.add(Sink.foreach[String](println))

      broadcast.out(0) ~> lowercaseFilter ~> sink
      broadcast.out(1) ~> counterBelowFiveFilter ~> counterShape

      SinkShape(broadcast.in)
  }

  val future = source.toMat(complexWordSink)(Keep.right).run()

  import system.dispatcher

  future.onComplete {
    case Success(value) => println(s"The total number of short string is: $value")
    case Failure(exception) => println(s"The total number of short failed due to: $exception")
  }

  val complexWordSink2 = GraphDSL.create(printer, counter)((printerMatValue, counterMatValue) => counterMatValue) { implicit builder =>
    (printerShape, counterShape) =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFilter = builder.add(Flow[String].filter(str => str == str.toLowerCase))
      val counterBelowFiveFilter = builder.add(Flow[String].filter(_.length < 5))

      broadcast.out(0) ~> lowercaseFilter ~> printerShape
      broadcast.out(1) ~> counterBelowFiveFilter ~> counterShape

      SinkShape(broadcast.in)
  }

  val future2 = source.toMat(complexWordSink2)(Keep.right).run()
  future2.onComplete {
    case Success(value) => println(s"The total number of short string is: $value")
    case Failure(exception) => println(s"The total number of short failed due to: $exception")
  }

  /**
   * Exercise
   * composite component (count numbers)
   *
   * broadcast and Sink.fold
   */
  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {

    val counterSink = Sink.fold[Int, B](0)((count, _) => count + 1)

    Flow.fromGraph(GraphDSL.create(counterSink) { implicit builder =>
      counterSinkShape =>
        import GraphDSL.Implicits._
        val boradcast = builder.add(Broadcast[B](2))
        val originalFlowShape = builder.add(flow)
        originalFlowShape ~> boradcast ~> counterSinkShape
        FlowShape(originalFlowShape.in, boradcast.out(1))
    })
  }

  val simpleSource = Source(1 to 42)
  val simpleFlow = Flow[Int].map(x => x)

  //simpleSource.via(enhanceFlow[Int, Int](simpleFlow)).toMat(Sink.ignore)(Keep.right).run()

  val enhancedFlowCountFuture = simpleSource.viaMat(enhanceFlow(simpleFlow))(Keep.right).toMat(Sink.ignore)(Keep.left).run()

  enhancedFlowCountFuture.onComplete {
    case Success(value) => println(s"Cunt elements went through enhanced flow: $value")
    case Failure(exception) => println(s"We had an exception: $exception")
  }


}
