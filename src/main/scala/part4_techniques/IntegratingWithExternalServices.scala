package part4_techniques

import java.util.Date
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.dispatch.MessageDispatcher
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.Future
import scala.language.postfixOps

/**
 * User: patricio
 * Date: 10/8/21
 * Time: 03:34
 */
object IntegratingWithExternalServices extends App {

  implicit val system: ActorSystem = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer: Materializer = Materializer(system) // not recommended
  implicit val dispatcher: MessageDispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericService[A, B](element: A): Future[B] = ???

  // example: simplified PageDuty

  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure Broke", new Date()),
    PagerEvent("FastDataPipeline", "Illegal elements in pipeline", new Date()),
    PagerEvent("AkkaInfra", "A services stopped responding", new Date()),
    PagerEvent("SuperFrontend", "The button doesn't work", new Date()),
  ))

  object PagerService {
    private val engineers = List("Barnie", "Lenny", "Carl")
    private val emails = Map(
      "Barnie" -> "mr.Gomez@duff.com",
      "Lenny" -> "lenny@nuclear.sp",
      "Carl" -> "carl@atomic.net"
    )

    def processEvent(pagerEvent: PagerEvent): Future[String] = Future {
      val engineerIndex = ((pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length).toInt
      val engineer = engineers(engineerIndex)
      val engineerEmail = emails(engineer)

      println(s"Sending engineer $engineerEmail a higher priority notification $pagerEvent")
      Thread.sleep(1000)
      // return the email
      engineerEmail
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  // guarantees the relative order of elements
  val pagedEngineerEmails = infraEvents.mapAsync(parallelism = 1)({ event =>
    val eventResp = PagerService.processEvent(event)
    println(eventResp)
    eventResp
  })
  //do not guarantees the relative order of elements
  //val pagedEngineerEmail = infraEvents.mapAsyncUnordered(parallelism = 4)(PagerService.processEvent)

  val pagedEmailsSink = Sink.foreach[String](email => println(s"Successful sent notification $email"))

  //pagedEngineerEmails.to(pagedEmailsSink).run()

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Barnie", "Lenny", "Carl")
    private val emails = Map(
      "Barnie" -> "mr.Gomez@duff.com",
      "Lenny" -> "lenny@nuclear.sp",
      "Carl" -> "carl@atomic.net"
    )

    def processEvent(pagerEvent: PagerEvent): String = {
      val engineerIndex = ((pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length).toInt
      val engineer = engineers(engineerIndex)
      val engineerEmail = emails(engineer)

      println(s"Sending engineer $engineerEmail a higher priority notification $pagerEvent")
      Thread.sleep(1000)
      // return the email
      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender() ! processEvent(pagerEvent)
    }

  }

  import akka.pattern.ask
  import scala.concurrent.duration._
  val actor = system.actorOf(Props[PagerActor], "pagerActor")
  implicit val timeout: Timeout = Timeout(6 seconds)
  val alternativePagedEngineerEmails = infraEvents.mapAsync(parallelism = 1)(event => {
    val eventResp = (actor ? event).mapTo[String]
    println(eventResp)
    eventResp
  })
  alternativePagedEngineerEmails.to(pagedEmailsSink).run()


}
