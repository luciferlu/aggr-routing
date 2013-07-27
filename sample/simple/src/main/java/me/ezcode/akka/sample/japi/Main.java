package me.ezcode.akka.sample.japi;

import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

import scala.concurrent.Future;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.UntypedActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.routing.FromConfig;
import static akka.pattern.Patterns.ask;
import akka.util.Timeout;
import me.ezcode.akka.routing.ParallelMessage;
import me.ezcode.akka.routing.japi.Converts;

public class Main {
	private static class UpperCaseActor extends UntypedActor {
		@Override
		public void onReceive(Object message) {
			if (message instanceof String) {
				System.out.println(getSelf().toString()
						+ " is process message [" + message.toString() + "]");
				String c = (String) message;
				getSender().tell(c.toUpperCase(), getSelf());
			}
		}
	}

	public static void main(String[] args) throws Exception {
		ActorSystem system = ActorSystem.create("aggrRouter-Sample");

		try {
			ActorRef routerRef = system.actorOf(
					Props.create(UpperCaseActor.class).withRouter(
							new FromConfig()), "aggr-router");

			List<String> message = new ArrayList<String>();
			message.add("h");
			message.add("e");
			message.add("l");
			message.add("l");
			message.add("o");
			Future<scala.collection.Iterable<String>> f = Converts.toFuture(
					ask(routerRef, new ParallelMessage<String>(message, "1 second", 2), new Timeout(1, TimeUnit.SECONDS)));
			scala.collection.Iterable<String> result = Await.result(f, Duration.create("1 second"));
			System.out.println("Final Result: " + result.mkString());
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			system.shutdown();
			system.awaitTermination();
		}
	}
}