package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ClosedShape, Graph, Inlet, Materializer, Outlet, Shape}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

/**
 * User: patricio
 * Date: 14/8/21
 * Time: 11:15
 */
object CustomGraphShapes extends App {

  implicit val system: ActorSystem = ActorSystem("CustomGraphShapes")
  implicit val materializer: Materializer = Materializer(system)

  // balanced 2 x 3
  case class Balance2x3(
                         in0: Inlet[Int],
                         in1: Inlet[Int],
                         out0: Outlet[Int],
                         out1: Outlet[Int],
                         out2: Outlet[Int],
                       ) extends Shape {
    override def inlets: Seq[Inlet[_]] = List(in0, in1)

    override def outlets: Seq[Outlet[_]] = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2x3(
      in0.carbonCopy(),
      in1.carbonCopy(),
      out0.carbonCopy(),
      out1.carbonCopy(),
      out2.carbonCopy(),
    )
  }

  val balance2x3Impl = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](3))

    merge ~> balance

    Balance2x3(
      merge.in(0),
      merge.in(1),
      balance.out(0),
      balance.out(1),
      balance.out(2),
    )
  }

  val balanced2x3Graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
    val fastSource = Source(LazyList.from(1)).throttle(2, 1 second)

    def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
      println(s"[$index] : Received $element, current count is $count")
      count + 1
    })

    val sink1 = builder.add(createSink(1))
    val sink2 = builder.add(createSink(2))
    val sink3 = builder.add(createSink(3))

    val balance2x3 = builder.add(balance2x3Impl)

    slowSource ~> balance2x3.in0
    fastSource ~> balance2x3.in1

    balance2x3.out0 ~> sink1
    balance2x3.out1 ~> sink2
    balance2x3.out2 ~> sink3

    ClosedShape
  })

  //balanced2x3Graph.run()


  /**
   * Exercise: generalize the  Balance component, make it M x N
   */

  case class BalancedShape[T,S](override val inlets: Seq[Inlet[T]], override val outlets: Seq[Outlet[S]]) extends Shape {

    override def deepCopy(): Shape = BalancedShape(
      inlets.map(_.carbonCopy()),
      outlets.map(_.carbonCopy())
    )
  }

  object BalancedShape {
    def apply[T, S >: T](inputCount: Int, outputOut: Int): Graph[BalancedShape[T, S], NotUsed] = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val merge = builder.add(Merge[T](inputCount))
      val balance = builder.add(Balance[S](outputOut))

      merge ~> balance

      BalancedShape(
        merge.inlets,
        balance.outlets
      )
    }
  }


  val balancedShapedGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val slowSource = Source(LazyList.from(1)).throttle(1, 1 second)
    val fastSource = Source(LazyList.from(1)).throttle(2, 1 second)

    def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
      println(s"[$index] : Received $element, current count is $count")
      count + 1
    })

    val sink1 = builder.add(createSink(1))
    val sink2 = builder.add(createSink(2))
    val sink3 = builder.add(createSink(3))

    val balanceShape = builder.add(BalancedShape[Int, Int](2,3))

    slowSource ~> balanceShape.inlets.head
    fastSource ~> balanceShape.inlets(1)

    balanceShape.outlets.head ~> sink1
    balanceShape.outlets(1) ~> sink2
    balanceShape.outlets(2) ~> sink3

    ClosedShape
  })

  balancedShapedGraph.run()



}




