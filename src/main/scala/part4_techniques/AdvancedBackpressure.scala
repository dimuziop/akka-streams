package part4_techniques

import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import java.util.Date
import scala.language.postfixOps

/**
 * User: patricio
 * Date: 10/8/21
 * Time: 04:46
 */
object AdvancedBackpressure extends App {

  implicit val system: ActorSystem = ActorSystem("AdvancedBackpressure")
  implicit val materializer: Materializer = Materializer(system)

  // control backpressure
  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)

  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Infrastructure Broke", new Date()),
    PagerEvent("Illegal elements in pipeline", new Date()),
    PagerEvent("A services stopped responding", new Date()),
    PagerEvent("The button doesn't work", new Date()),
    PagerEvent("The button doesn't work", new Date()),
    PagerEvent("The button doesn't work", new Date()),
    PagerEvent("The button doesn't work", new Date()),
    PagerEvent("The button doesn't work", new Date()),
    PagerEvent("The button doesn't work", new Date()),
    PagerEvent("The button doesn't work", new Date()),
    PagerEvent("The button doesn't work", new Date()),
  )

  val eventSource = Source(events)

  val onCallEngineer = "somemail@mail.com" // fast service for fetching oncall emails

  def sendEmail(notification: Notification): Unit =
    println(s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}") // lest say actually sends an email

  val notificationSink = Flow[PagerEvent].map(event => Notification(onCallEngineer, event))
    .to(Sink.foreach[Notification](sendEmail))

  // standard way
  //eventSource.to(notificationSink).run()

  /*
  UN BACKPRESSURABLE SOURCE
   */
  def sendEmailSlow(notification: Notification): Unit = {
    Thread.sleep(1000)
    println(s"Dear ${notification.email}, you have an event: ${notification.pagerEvent}")
  }

  val aggregateNotificationFlow = Flow[PagerEvent].conflate((event, event1) => {
    val nInstances = event.nInstances + event1.nInstances
    PagerEvent(s"You have $nInstances events that required your attention ASAP", new Date, nInstances)
  }).map(resultingEvent => Notification(onCallEngineer, resultingEvent))

  //  eventSource.via(aggregateNotificationFlow).async.to(Sink.foreach[Notification](sendEmailSlow)).run()
  // alternative to backpressure

  /*
    Slow producers: extrapolate/expand
   */
  import scala.concurrent.duration._
  val slowCounter = Source(Stream.from(1)).throttle(1, 1 second)
  val hungrySink = Sink.foreach[Int](println)

  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))
  val repeater = Flow[Int].extrapolate(element => Iterator.continually(element))

  slowCounter.via(repeater).to(hungrySink).run()

  val expander = Flow[Int].expand(element => Iterator.from(element))

}
