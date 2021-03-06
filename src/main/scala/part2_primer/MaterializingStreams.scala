package part2_primer

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}

/**
 * User: patricio
 * Date: 24/7/21
 * Time: 11:33
 */
object MaterializingStreams extends App {

  val system = ActorSystem("MaterializingStreams")
  implicit val materializer: Materializer = Materializer(system)
  import system.dispatcher

  val simpleGraph= Source(1 to 10).to(Sink.foreach(println))
  //val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink = Sink.reduce[Int]((a, b) => a + b)
  val sumFuture = source.runWith(sink)

  //import scala.concurrent.ExecutionContext.Implicits._
  sumFuture.onComplete{
    case Success(value) => println(s"The result fo the execution is $value")
    case Failure(exception) => println(s"The sum of the elements could not be computed: $exception")
  }

  // choosing materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow = Flow[Int].map(x => x + 1)
  val simpleSink = Sink.foreach[Int](println)
  val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  graph.run().onComplete {
    case Success(_) => println("Stream processing finished.")
    case Failure(exception) => println(s"Stream processing failed with: $exception")
  }

  // sugars
  Source(1 to 10).runWith(Sink.reduce[Int](_ + _)) // source.to(Sink.reduce)(Keep.right)
  Source(1 to 10).runReduce[Int](_ + _) // same

  // backwards
  Sink.foreach[Int](println).runWith(Source.single(42)) // source(..).to(sink...).run()
  // both ways
  Flow[Int].map(x => 2 * x).runWith(simpleSource, simpleSink)

  /**
   * - return the last element out of a source (Sink.last)
   * - compute hte total word out of a stream of sentences (map, fold, reduce)
   */

  val sequence = 1 to 10
  val lastElement = Source(sequence).runWith(Sink.last)
  val lastElementFuture = Source(sequence).toMat(Sink.last)(Keep.right).run()
  val lastElementV2 = Source(sequence).to(Sink.last).run()

  val sentences = List("this is a sentence", "another sentence", "and another one", "so on and so forth")
  val sentenceSource = Source(sentences)

  val workCountSink = Sink.fold[Int, String](0)((prev, curr) => prev + curr.split(" ").length)

  val totalWordsCount = Source(sentences).toMat(workCountSink)(Keep.right).run()
  val wordCountSink = Sink.fold[Int, String](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  totalWordsCount.map(println)

  val g1 = sentenceSource.toMat(wordCountSink)(Keep.right).run()
  val g2 = sentenceSource.runWith(wordCountSink)
  val g3 = sentenceSource.runFold(0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)

  val wordCountFlow = Flow[String].fold[Int](0)((currentWords, newSentence) => currentWords + newSentence.split(" ").length)
  val g4 = sentenceSource.via(wordCountFlow).toMat(Sink.head)(Keep.right).run()
  val g5 = sentenceSource.viaMat(wordCountFlow)(Keep.left).toMat(Sink.head)(Keep.right).run()
  val g6 = sentenceSource.via(wordCountFlow).runWith(Sink.head)
  val g7 = wordCountFlow.runWith(sentenceSource, Sink.head)._2



}
