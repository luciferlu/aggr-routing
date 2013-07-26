package me.ezcode.akka.routing

import language.postfixOps

import scala.concurrent.Await
import scala.concurrent.duration._
import org.scalatest._
import org.scalatest.matchers.MustMatchers
import akka.actor.{ Actor, ActorSystem, Props }
import akka.routing.FromConfig
import akka.testkit._

import me.ezcode.akka.testkit.AkkaSpec

object AggregationRouterSpec {
  val config = """
    akka.actor.deployment {
      /aggregation-with-config {
        router = me.ezcode.akka.routing.AggregationRouter
		    aggr-router{
		      nr-of-instances = 5
		    }
      }
    }
  """
}

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ReliableDeliveryActorSpec extends AkkaSpec(AggregationRouterSpec.config) with ImplicitSender {
  "reliable message delivery actor" must {
    "fork message to specific routees" in {
      val tb1 = TestProbe()
      val tb2 = TestProbe()
      val tb3 = TestProbe()

      val reliableAF = TestActorRef(new AggregationRouter.ReliableDeliveryActor(List(tb1.ref, tb2.ref)))

      tb3 watch reliableAF
      reliableAF ! "hello"

      tb1.expectMsg(100 millis, "hello")
      tb1.reply("Hello")

      tb2.expectMsg(100 millis, "hello")
      tb2.reply("HELLO")

      expectMsg(500 millis, "Hello")
      tb3.expectTerminated(reliableAF)
      expectNoMsg(100 millis)
    }
  }
}

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ParallelDeliveryActorSpec extends AkkaSpec(AggregationRouterSpec.config) with ImplicitSender {
  "parallel message delivery actor" must {
    "send message to specific routees" in {
      val tb1 = TestProbe()
      val tb2 = TestProbe()
      val tb3 = TestProbe()

      val parallelAF = TestActorRef(new AggregationRouter.ParallelDeliveryActor(
        List(List(tb1.ref, tb1.ref),List(tb2.ref, tb2.ref))))

      tb3 watch parallelAF
      parallelAF ! List("a", "b")

      tb1.expectMsg(100 millis, "a")
      tb1.reply("A")

      tb1.expectMsg(100 millis, "a")
      tb1.reply("1")

      tb2.expectMsg(100 millis, "b")
      tb2.reply("B")

      tb2.expectMsg(100 millis, "b")
      // tb2.reply("2")

      expectMsg(List("A", "B"))

      tb3.expectTerminated(parallelAF)
      expectNoMsg(100 millis)
    }
  }
}

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class AggregationRouterSpec extends AkkaSpec(AggregationRouterSpec.config) with ImplicitSender {
  implicit val ec = system.dispatcher
  import AggregationRouterSpec._

  "aggregation router" must {
    "be able to shutdown its instances" in {
      val helloLatch = new TestLatch(5)
      val stopLatch = new TestLatch(5)

      val router = system.actorOf(Props(new Actor {
        def receive = {
          case "hello" ⇒ helloLatch.countDown()
        }

        override def postStop() {
          stopLatch.countDown()
        }
      }).withRouter(AggregationRouter(5)), "aggregation-shutdown")

      router ! "hello"
      router ! "hello"
      router ! "hello"
      router ! "hello"
      router ! "hello"

      Await.ready(helloLatch, 5 seconds)
      system.stop(router)
      Await.ready(stopLatch, 5 seconds)
    }

    "use configured nr-of-instances when FromConfig" in {
      val helloLatch = new TestLatch(5)
      val stopLatch = new TestLatch(5)

      val router = system.actorOf(Props(new Actor {
        def receive = {
          case "hello" ⇒ helloLatch.countDown()
        }

        override def postStop() {
          stopLatch.countDown()
        }
      }).withRouter(FromConfig), "aggregation-with-config")

      router ! "hello"
      router ! "hello"
      router ! "hello"
      router ! "hello"
      router ! "hello"

      Await.ready(helloLatch, 5 seconds)
      system.stop(router)
      Await.ready(stopLatch, 5 seconds)
    }

    "forward normal msg or FireAndForget msg to routee" in {
      val tbs = List.fill(1) { TestProbe() }
      val router = system.actorOf(Props.empty.withRouter(AggregationRouter(tbs.map { _.ref })), "fire-and-forget-msg")

      router ! "hello"
      router ! FireAndForget("hello")

      tbs.head.expectMsg(500.millis, "hello")
      tbs.head.expectMsg(500.millis, "hello")

      system.stop(router)
    }

    "fork to specific numbers of routee when send ReliableMessage" in {
      val count = new TestLatch(2)
      val router = system.actorOf(Props(new Actor {
        def receive = {
          case "hello" ⇒ {
            count.countDown()
            sender ! "hello back"
          }
        }
      }).withRouter(AggregationRouter(5)), "reliable-message-deliver")

      router ! ReliableMessage("hello", 1 seconds, 2)

      Await.ready(count, 500 millis)
      expectMsg(100 millis, "hello back")
      expectNoMsg(500 millis)
      system.stop(router)
    }

    "fork to all routee if fork number large then routee number when send ReliableMessage" in {
      val count = new TestLatch(2)
      val router = system.actorOf(Props(new Actor {
        def receive = {
          case "hello" ⇒ {
            count.countDown()
            sender ! "hello back"
          }
        }
      }).withRouter(AggregationRouter(2)), "reliable-message-deliver")

      router ! ReliableMessage("hello", 1 seconds, 5)

      Await.ready(count, 500 millis)
      expectMsg(100 millis, "hello back")
      expectNoMsg(500 millis)
      system.stop(router)
    }

    "send each message to exactly one routee when send ParallelMessge with default maxFork" in {
      val count = new TestLatch(3)

      val router = system.actorOf(Props(new Actor {
        def receive = {
          case msg: String ⇒ {
            count.countDown()
            sender ! msg.toUpperCase
          }
        }
      }).withRouter(AggregationRouter(5)), "reliable-message-deliver")

      router ! ParallelMessage(List("a", "b", "c"))

      Await.ready(count, 500 millis)
      expectMsg(100 millis, List("A", "B", "C"))
      expectNoMsg(500 millis)
      system.stop(router)
    }

    "send each message to two routees when send ParallelMessge with maxFork 2" in {
      val count = new TestLatch(6)

      val router = system.actorOf(Props(new Actor {
        def receive = {
          case msg: String ⇒ {
            count.countDown()
            sender ! msg.toUpperCase
          }
        }
      }).withRouter(AggregationRouter(5)), "reliable-message-deliver")

      router ! ParallelMessage(List("a", "b", "c"), maxFork = 2)

      Await.ready(count, 500 millis)
      expectMsg(100 millis, List("A", "B", "C"))
      expectNoMsg(500 millis)
      system.stop(router)
    }
  }
}
