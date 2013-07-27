package me.ezcode.akka.sample

import scala.language.postfixOps

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import akka.routing.FromConfig
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config._
import me.ezcode.akka.routing._

object Main extends App {
  val system = ActorSystem("aggrRouter-Sample")
  import system.dispatcher
  implicit val timeout = Timeout(2 seconds)

  val routerRef = system.actorOf(Props(new Actor {
    def receive = {
      case c: Char ⇒ {
        println("%s is processing message [%s]" format (self.toString, c))
        sender ! c.toUpper
      }
    }
  }).withRouter(FromConfig), "aggr-router")

  val f = (routerRef ? ParallelMessage("hello", maxFork = 2)).asInstanceOf[Future[Iterable[Char]]]
  val result = Await.result(f, 5 seconds)
  println("Final Result: " + result.mkString)
  system.shutdown()
  system.awaitTermination()
}