package part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, GraphStageWithMaterializedValue, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Materializer, Outlet, SinkShape, SourceShape}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Random, Success}

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
  //randomGeneratorSource.to(batcherSink).run()

  /**
   * Exercise: a custom flow - a simple filter flow
   * - 2 ports: an input port and an ourput port
   */

  class FilterFlow[S](predicate: S => Boolean) extends GraphStage[FlowShape[S,S]] {

    val outPort = Outlet[S]("outputFilterFlow")
    val inPort = Inlet[S]("inputInletFlow")

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {


      setHandler(outPort, new OutHandler {
        override def onPull(): Unit = {
          pull(inPort)
        }
      })
      setHandler(inPort, new InHandler {
        override def onPush(): Unit = {
          try {
            val element = grab(inPort)
            if (predicate(element)) {
              push(outPort, element)
            } else {
              pull(inPort)
            }
          } catch {
            case e: Throwable => failStage(e)
          }
        }

      })
    }

    override def shape: FlowShape[S, S] = FlowShape[S, S](inPort, outPort)
  }

  val filterFlow = Flow.fromGraph(new FilterFlow[Int](_ >= 50))

  //randomGeneratorSource.via(filterFlow).to(batcherSink).run()

  /*
  Materialized values in graph stages
   */
  // 3 - flow that counts the number of elements that goes through it

  class CounterFlow[T] extends GraphStageWithMaterializedValue[FlowShape[T, T], Future[Int]] {
    val outPort = Outlet[T]("counterIn")
    val inPort = Inlet[T]("counterOut")

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
      val promise = Promise[Int]
      val logic: GraphStageLogic = new GraphStageLogic(shape) {
        var counter = 0
        setHandler(outPort, new OutHandler {
          override def onPull(): Unit = {
            pull(inPort)
          }

          override def onDownstreamFinish(): Unit = {
            promise.success(counter)
            super.onDownstreamFinish()
          }
        })
        setHandler(inPort, new InHandler {
          override def onPush(): Unit = {
            try {
              val element = grab(inPort)
              counter +=1
              push(outPort, element)
            } catch {
              case e: Throwable => failStage(e)
            }
          }

          override def onUpstreamFinish(): Unit = {
            promise.success(counter)
            super.onUpstreamFinish()
          }

          override def onUpstreamFailure(ex: Throwable): Unit = {
            promise.failure(ex)
            super.onUpstreamFailure(ex)
          }
        })
      }
      (logic, promise.future)
    }

    override val shape: FlowShape[T, T] = FlowShape(inPort, outPort)
  }

  val counterFlow = Flow.fromGraph(new CounterFlow[Int])

  val countFuture = Source(1 to 10).viaMat(counterFlow)(Keep.right).to(Sink.foreach[Int](println)).run()

  import system.dispatcher

  countFuture.onComplete {
    case Success(value) => println(s"Quantiyty is $value")
    case Failure(exception) => println(s"Error is: $exception")
  }

}
