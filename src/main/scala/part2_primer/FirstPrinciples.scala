package part2_primer

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future
import scala.util.Random

/**
 * User: patricio
 * Date: 23/7/21
 * Time: 19:25
 */
object FirstPrinciples extends App {

  val system = ActorSystem("FirstPrinciples")
  implicit val materializer = Materializer(system)

  //  source
  val source = Source(1 to 10)
  // sink
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)
  graph.run()

  // flows transform elements
  val flow = Flow[Int].map(x => x + 1)
  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

  /*sourceWithFlow.to(sink).run()
  source.to(flowWithSink).run()
  source.via(flow).to(sink).run()*/

  // nulls are NOT allowed
  //val illegalSource = Source.single[String](null)
  //illegalSource.to(Sink.foreach(println)).run()
  // use options instead
  val legalSource = Source.single[Option[String]](None)
  legalSource.to(Sink.foreach(println)).run()

  // various kinds of sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(LazyList.from(1)) // do not confuse AKKA streams with collection streams

  import scala.concurrent.ExecutionContext.Implicits.global

  val futureSource = Source.future(Future(42))

  // sinks
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink = Sink.head[Int] // retrieves head and then closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)

  // flows - usually mapped to collection operators
  val mapFlow = Flow[Int].map(x => 2 * x)
  val takeFlow = Flow[Int].take(5)
  // drop, filter
  // NOT have flatMap

  // source -> flow -> flow -> ... -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  //  doubleFlowGraph.run()

  // syntactic sugars
  val mapSource = Source(1 to 10).map(x => x * 2) // Source(1 to 10).via(Flow[Int].map(x => x * 2))
  // run streams directly
  //  mapSource.runForeach(println) // mapSource.to(Sink.foreach[Int](println)).run()

  // OPERATORS = components

  /**
   * Exercise: create a stream that takes the names of persons, then you will keep the first 2 names with length > 5 characters.
   *
   */

  val aGeneratorString = (value: Int) => {
    var a = "a"
    for (_ <- 1 to value) {
      a += "a"
    }
    a
  }

  val r = Random
  val randomLengthNames = for (i <- 1 to r.nextInt(9999)) yield aGeneratorString(r.nextInt(15))

  val nameSource = Source(randomLengthNames)
  val flowLongerThanFive = Flow[String].filter(str => str.length >= 5)
  val flowPickTwo = Flow[String].take(2)

  nameSource.via(flowLongerThanFive).via(flowPickTwo).to(foreachSink).run()

}
