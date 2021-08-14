package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source, SubFlow}
import org.openjdk.jol.vm.VM

import scala.util.{Failure, Success}

/**
 * User: patricio
 * Date: 14/8/21
 * Time: 09:16
 */
object Substreams extends App {

  implicit val system: ActorSystem = ActorSystem("Substreams")
  implicit val materializer: Materializer = Materializer(system)

  // 1 - grouping a stream by a certain function

  val wordsSource: Source[String, NotUsed] = Source(List("Akka", "is", "amazing", "learning", "substreams"))
  val groups: SubFlow[String, NotUsed, wordsSource.Repr, RunnableGraph[NotUsed]] = wordsSource.groupBy(30, w => if (w.isEmpty) '\u0000' else w.toLowerCase().charAt(0))

  groups.to(Sink.fold(0)((count, word) => {
    val newCount =  count + 1
    println(s"I just receive $word, count is $newCount. Address: ${VM.current().details()}")
    newCount
  }))
    //.run()

  // 2 - merge substreams back
  val textSource: Source[String, NotUsed] = Source(
    List(
      "I love akka streams",
      "this is amazing",
      "learning from rocktheJVM",
      "blah blah blhableta",
      "more about substreams")
  )

  val totalCharCountFuture = textSource.groupBy(2, string => string.length % 2)
    .map(_.length) // do expensive computation
    .mergeSubstreams//WithParallelism(2) || unbounded
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  import system.dispatcher

  totalCharCountFuture.onComplete {
    case Success(value) => println(s"Total char count: $value")
    case Failure(exception) => println(s"Total char count failed: $exception")
  }

  // 3 splitting stream into substreams, when a condition met

  val text: String =
      "I love akka streams\n" +
      "this is amazing\n" +
      "learning from rocktheJVM\n" +
      "blah blah blhableta\n" +
      "more about substreams\n"

  val anotherCharCountFuture = Source(text.toList)
    .splitWhen(c => c == '\n')
    .filter(_ != '\n')
    .map(_ => 1)
    .mergeSubstreams
    .toMat(Sink.reduce[Int](_ + _))(Keep.right)
    .run()

  anotherCharCountFuture.onComplete {
    case Success(value) => println(s"Total char count alternative: $value")
    case Failure(exception) => println(s"Total char count failed: $exception")
  }

  // 4 flattening

  val simpleSource = Source(1 to 10)
  simpleSource.flatMapConcat(x => Source(x to (3 * x))).runWith(Sink.foreach(println))
  simpleSource.flatMapMerge(2, x => Source(x to (3 * x))).runWith(Sink.foreach(println))
}
