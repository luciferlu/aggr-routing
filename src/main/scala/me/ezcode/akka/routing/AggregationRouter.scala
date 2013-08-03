package me.ezcode.akka.routing

import scala.language.postfixOps

import scala.concurrent._
import scala.collection._
import scala.collection.immutable
import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.concurrent.duration._
import akka.actor._
import akka.japi.Util.immutableSeq
import akka.dispatch.Dispatchers
import akka.pattern.{ ask, pipe }
import akka.routing._
import akka.util.Timeout
import com.typesafe.config._

object AggregationRouter {
  val defaultConfig = """
	  aggr-router{
	  	nr-of-instances = 1
	  	routees{
	  		paths = []
	  	}
	  }
    """

  def apply(routees: immutable.Iterable[ActorRef]): AggregationRouter = {
    new AggregationRouter(routees map (_.path.toString))
  }

  def apply(nrOfInstances: Int): AggregationRouter =
    new AggregationRouter(nrOfInstances)

  val defaultStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _ ⇒ SupervisorStrategy.Escalate
  }

  private[routing] class ReliableDeliveryActor(routees: Iterable[ActorRef],
                                               withIn: FiniteDuration = 2 seconds) extends Actor {
    import context.dispatcher
    implicit val timeout = Timeout(withIn)
    def receive = {
      case msg ⇒ {
        Future.firstCompletedOf(routees.map(_ ? msg)).pipeTo(sender)
        context.stop(self)
      }
    }
  }

  private[routing] class ParallelDeliveryActor(receivers: Iterable[Iterable[ActorRef]],
                                               withIn: FiniteDuration = 2 seconds) extends Actor {
    import context.dispatcher
    implicit val timeout = Timeout(withIn)

    def reliableAsk(msg: Any, receivers: Iterable[ActorRef]): Future[Any] =
      Future.firstCompletedOf(receivers.map(_ ? msg))

    def parallelAsk(msgs: Iterable[Any], receivers: Iterable[Iterable[ActorRef]]): Iterable[Future[Any]] =
      msgs.zip(receivers).map(t ⇒ reliableAsk(t._1, t._2))

    def receive = {
      case msgs: Iterable[_] ⇒ {
        Future.sequence(parallelAsk(msgs, receivers)).pipeTo(sender)
        context.stop(self)
      }
      case msgs ⇒ {
        sys.error("wrong messages {%s} to send with receivers {%s}" format (msgs, receivers))
        context.stop(self)
      }
    }
  }
}

@SerialVersionUID(1L)
final case class FireAndForget(message: Any) extends RouterEnvelope

@SerialVersionUID(1L)
final case class ReliableMessage(message: Any, withIn: FiniteDuration = 2 seconds, maxFork: Int = 2) extends RouterEnvelope

@SerialVersionUID(1L)
final case class ParallelMessage[T](message: Iterable[T], withIn: FiniteDuration = 2 seconds, maxFork: Int = 1) extends RouterEnvelope{
  //Java API
  def this(message: java.lang.Iterable[T], withIn: String, maxFork: Int) {
    this(immutableSeq(message), Duration(withIn).asInstanceOf[FiniteDuration], maxFork)
  }

  def this(message: java.lang.Iterable[T], withIn: String) =
    this(immutableSeq(message), withIn = Duration(withIn).asInstanceOf[FiniteDuration])

  def this(message: java.lang.Iterable[T], maxFork: Int) =
    this(immutableSeq(message), maxFork = maxFork)
}

case class AggregationRouter(config: Config)
    extends RouterConfig with AggregationLike {

  def this(nrOfInstances: Int) {
    this(ConfigFactory.parseString("aggr-router.nr-of-instances = %s" format nrOfInstances))
  }

  def this(routees: immutable.Iterable[String]) {
    this(ConfigFactory.parseString("aggr-router.routees.paths = %s" format routees.map("\"" + _ + "\"").mkString("[", ",", "]")))
  }

  val deployment = config.withFallback(ConfigFactory.parseString(AggregationRouter.defaultConfig)).getConfig("aggr-router")
  override val nrOfInstances = deployment.getInt("nr-of-instances")
  override val routees: immutable.Iterable[String] = immutableSeq(deployment.getStringList("routees.paths"))

  override val resizer: Option[Resizer] = {
    if (config.getConfig("aggr-router").hasPath("resizer"))
      Some(DefaultResizer(deployment.getConfig("resizer")))
    else
      None
  }

  val routerDispatcher: String = Dispatchers.DefaultDispatcherId
  val supervisorStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy
}

trait AggregationLike { this: RouterConfig ⇒
  import AggregationRouter._
  def nrOfInstances: Int
  def routees: immutable.Iterable[String]

  def fisherYatesShuffle[T](data: Iterable[T]): Iterable[T] = {
    import scala.collection.mutable.Buffer
    import scala.util.Random._
    val result = Buffer.empty[T]
    data.foreach(d ⇒ {
      val j = nextInt(result.length + 1)
      if (j == result.length) {
        result.append(d)
      }
      else {
        result.append(result(j))
        result(j) = d
      }
    })
    result
  }

  def createReliableActor(context: ActorContext,
                          withIn: FiniteDuration,
                          receivers: Iterable[ActorRef]): ActorRef =
    context.actorOf(Props(classOf[ReliableDeliveryActor], receivers, withIn))

  def createParallelActor(context: ActorContext,
                          withIn: FiniteDuration,
                          receivers: Iterable[Iterable[ActorRef]]): ActorRef =
    context.actorOf(Props(classOf[ParallelDeliveryActor], receivers, withIn))

  def createRoute(routeeProvider: RouteeProvider): Route = {
    if (resizer.isEmpty) {
      if (routees.isEmpty)
        routeeProvider.createRoutees(nrOfInstances)
      else
        routeeProvider.registerRouteesFor(routees)
    }

    val context = routeeProvider.context

    def getNext(maxNr: Int = 1): Iterable[ActorRef] = {
      val currentRoutees = routeeProvider.routees
      if (currentRoutees.isEmpty) context.system.deadLetters :: Nil
      else fisherYatesShuffle(currentRoutees).slice(0, maxNr)
    }

    def reliableDestination(sender: ActorRef,
                            message: Any,
                            withIn: FiniteDuration,
                            maxFork: Int): immutable.Iterable[Destination] =
      Destination(sender, createReliableActor(context, withIn, getNext(maxFork))) :: Nil

    def paralleDestination(sender: ActorRef,
                           messages: Iterable[Any],
                           withIn: FiniteDuration,
                           maxFork: Int): immutable.Iterable[Destination] =
      Destination(sender,
        createParallelActor(context,
          withIn,
          messages.to[immutable.Iterable].map(_ ⇒ getNext(maxFork)))) :: Nil

    {
      case (sender, message) ⇒
        message match {
          case Broadcast(msg)                        ⇒ toAll(sender, routeeProvider.routees)
          case ReliableMessage(msg, withIn, maxFork) ⇒ reliableDestination(sender, msg, withIn, maxFork)
          case ParallelMessage(msg, withIn, maxFork) ⇒ paralleDestination(sender, msg, withIn, maxFork)
          case msg                                   ⇒ Destination(sender, getNext().head) :: Nil
        }
    }
  }
}
