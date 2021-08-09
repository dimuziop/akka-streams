package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip}
import akka.stream.{ClosedShape, Materializer, OverflowStrategy, UniformFanInShape}

/**
 * User: patricio
 * Date: 9/8/21
 * Time: 03:30
 */
object GraphCycles extends App {

  val system = ActorSystem("GraphCycles")
  implicit val materializer: Materializer = Materializer(system)

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape

    ClosedShape
  }

  //  RunnableGraph.fromGraph(accelerator).run()
  // graph cycle deadlock!

  /*
    Solution 1: MergePreferred
   */
  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }

  //RunnableGraph.fromGraph(actualAccelerator).run()

  /*
  Solution 2: buffers
  */
  val bufferedRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Accelerating $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape <~ repeaterShape

    ClosedShape
  }

  //RunnableGraph.fromGraph(bufferedRepeater).run()

  /*
    cycles risk deadlocking
    - add bounds to the number of elements in the cycle
    boundedness vs liveness
   */

  /**
   * Challenge: create a fan-in shape
   * - two inputs which will be fed with EXACTLY ONE number (1 and 1)
   * - output will emit an INFINITE FIBONACCI SEQUENCE based off those 2 numbers
   * 1, 2, 3, 5, 8 ...
   *
   * Hint: Use ZipWith and cycles, MergePreferred
   */


  val inputSource1 = Source(List(1))
  val inputSource2 = Source(List(1))
  val fibonacciGraphCycle = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
    val zip = builder.add(Zip[BigInt, BigInt])
    val mergeShape = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val fibonacciMapper = builder.add(Flow[(BigInt, BigInt)].map{ pair =>
        val last = pair._1
        val previous = pair._2
      Thread.sleep(100)
      (last+previous, last)
    })
    val broadcastShape = builder.add(Broadcast[(BigInt, BigInt)](2))
    val extractLastShape = builder.add(Flow[(BigInt, BigInt)].map {pair =>
      pair._1
    })

    zip.out ~> mergeShape ~> fibonacciMapper ~> broadcastShape ~> extractLastShape
               mergeShape.preferred          <~ broadcastShape

    UniformFanInShape(extractLastShape.out, zip.in0, zip.in1)
  }

  val fiboGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
    val sourceShape1 = builder.add(Source.single[BigInt](1))
    val sourceShape2 = builder.add(Source.single[BigInt](1))
    val sinkShape = builder.add(Sink.foreach[BigInt](println))
    val fibo = builder.add(fibonacciGraphCycle)
      sourceShape1 ~> fibo.in(0)
      sourceShape2 ~> fibo.in(1); fibo.out ~> sinkShape
      ClosedShape
    } )

  fiboGraph.run()

}
