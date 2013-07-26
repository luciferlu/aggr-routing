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
import com.typesafe.config.*;
import me.ezcode.akka.routing.*;

public class Main{
  private static class UpperCaseActor extends UntypedActor{
    @Override
    public void onReceive(Object message){
      if(message instanceof String){
        System.out.println(getSelf().toString() + " is process message [" + message.toString() + "]");
        String c = (String)message;
        getSender().tell(c.toUpperCase(), getSelf());
      }
    }
  }

  public static void main(String[] args) throws Exception{
    ActorSystem system = ActorSystem.create("aggrRouter-Sample");

    try{
      ActorRef routerRef = system.actorOf(Props.create(UpperCaseActor.class).withRouter(new FromConfig()), "aggr-router");

      List<Object> message = new ArrayList<Object>();
      message.add("h");
      message.add("e");
      message.add("l");
      message.add("l");
      message.add("o");

      Future<Object> f = ask(routerRef, new ParallelMessage((Iterable<Object>)message, FiniteDuration.apply(1, TimeUnit.SECONDS), 2), new Timeout(1, TimeUnit.SECONDS));
      Object result = Await.result(f, Duration.create("1 second"));
      System.out.println("Final Result: " + ((scala.collection.Iterable<String>)result).mkString());
    }
    catch(Exception e){
      e.printStackTrace();
      throw e;
    }
    finally{
      system.shutdown();
      system.awaitTermination();
    }
  }
}