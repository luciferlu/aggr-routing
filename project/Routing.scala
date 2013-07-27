package com.easycode.akka

import sbt._
import sbt.Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object AkkaStudy extends Build {
  lazy val buildSettings = Seq(
    organization := "com.easycode",
    version := "0.1",
    scalaVersion := "2.10.2",
    scalaBinaryVersion <<= (scalaVersion, scalaBinaryVersion)((v, bv) ⇒ System.getProperty("akka.scalaBinaryVersion", if (v contains "-") v else bv)))

  override lazy val settings =
    super.settings ++
      buildSettings ++
      Seq(
        shellPrompt := { s ⇒ Project.extract(s).currentProject.id + " > " })

  lazy val baseSettings = Defaults.defaultSettings

  lazy val defaultSettings = baseSettings ++ Seq(
    // compile options
    scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-encoding", "UTF-8", "-Xlint:unchecked", "-Xlint:deprecation"),

    crossVersion := CrossVersion.binary,

    // ivyLoggingLevel in ThisBuild := UpdateLogging.Quiet,
    resolvers ++= Seq(
      "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"))

  lazy val aggrRouting = Project(
    id = "aggr-routing",
    base = file("."),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.aggrRouting))

  lazy val simpleSample = Project(
    id = "simple-sample",
    base = file("sample/simple"),
    settings = defaultSettings ++ Seq(
      libraryDependencies ++= Dependencies.simpleSample)).dependsOn(aggrRouting)
}

object Dependencies {

  object Compile {
    val actor = "com.typesafe.akka" %% "akka-actor" % "2.2.0" // ApacheV2
    // val cluster = "com.typesafe.akka" %% "akka-cluster" % "2.2.0" // ApacheV2
    val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.2.0" // ApacheV2
    val config = "com.typesafe" % "config" % "1.0.2" // ApacheV2

    // val sigar = "org.fusesource" % "sigar" % "1.6.4" exclude ("log4j", "log4j") // ApacheV2

    val slf4jApi = "org.slf4j" % "slf4j-api" % "1.7.2" // MIT
    // val log4jslf4j = "org.slf4j" % "log4j-over-slf4j" % "1.7.2" // MIT
    val logback = "ch.qos.logback" % "logback-classic" % "1.0.7" // EPL 1.0 / LGPL 2.1

    // Test

    object Test {
      val testkit = "com.typesafe.akka" %% "akka-testkit" % "2.2.0" % "test" // ApacheV2
      val junit = "junit" % "junit" % "4.10" % "test" // Common Public License 1.0
      val mockito = "org.mockito" % "mockito-all" % "1.8.1" % "test" // MIT
      val scalatest = "org.scalatest" %% "scalatest" % "1.9.1" % "test" // ApacheV2
      val scalacheck = "org.scalacheck" %% "scalacheck" % "1.10.0" % "test" // New BSD
      val junitIntf = "com.novocode" % "junit-interface" % "0.8" % "test" // MIT
    }
  }

  import Compile._

  val aggrRouting = Seq(actor, akkaSlf4j, logback, Test.scalatest, Test.testkit, Test.junit)
  val simpleSample = Seq(actor, akkaSlf4j, logback, config)
}
