package me.ezcode.akka.routing.japi

import scala.concurrent.Future

object Converts{
  def toFuture[T](f: Future[Any]): Future[T] = f.asInstanceOf[Future[T]]
}