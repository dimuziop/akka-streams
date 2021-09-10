package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, Inlet, Materializer, Outlet, SinkShape, SourceShape}

import scala.collection.mutable
import scala.util.Random

/**
 * User: patricio
 * Date: 17/8/21
 * Time: 13:01
 */
object CustomOperators extends App {

  implicit val system: ActorSystem = ActorSystem("CustomOperators")
  implicit val materializer: Materializer = Materializer(system)

  // 1 - a custom source which emits random numbers until canceled

  class RandomNumberGenerator(max: Int) extends GraphStage[/*STEP0: define the shape*/SourceShape[Int]] {
    val outPort = Outlet[Int]("randomGenerator")
    val random = new Random()

    /*
    Step 2: construct the shape
     */
    override def shape: SourceShape[Int] = SourceShape(outPort)

    /*
    STEP 3 : create the logic
     */
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      /*
      Step4: define mutable state
       */
      setHandler(outPort, new OutHandler {
        // demanded from downstream
        override def onPull(): Unit = {
          // emit a new element
          val nextNumber = random.nextInt(max)
          push(outPort, nextNumber)
        }
      })
    }


  }

  val randomGeneratorSource = Source.fromGraph(new RandomNumberGenerator(100))
  //randomGeneratorSource.runWith(Sink.foreach(println))

  // 2 - a custom sink that print elements in batches of a given size

  class Batcher(size: Int) extends GraphStage[SinkShape[Int]] {
    val inPort = Inlet[Int]("batcher")

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

      override def preStart(): Unit = {
        pull(inPort)
      }

      // mutable state
      val batch = new mutable.Queue[Int]
      setHandler(inPort, new InHandler {
        // when the upstream wants to send me an element
        override def onPush(): Unit = {
          // Auto backpressure
          Thread.sleep(100)
          val nextElement = grab(inPort)
          batch.enqueue(nextElement)
          if (batch.size >= size) {
            println("new Batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
          }
          pull(inPort) //send demand upstream
        }

        override def onUpstreamFinish(): Unit = {
          if(batch.nonEmpty) {
            println("new Batch: " + batch.dequeueAll(_ => true).mkString("[", ", ", "]"))
            println("Batch finished")
          }
        }
      })
    }

    override def shape: SinkShape[Int] = SinkShape[Int](inPort)
  }

  val batcherSink = Sink.fromGraph(new Batcher(50))
  randomGeneratorSource.to(batcherSink).run()
}
