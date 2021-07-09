import play.sbt.PlayImport._
import sbt.Keys._
import sbt.Project.projectToRef
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import webscalajs._

ThisBuild / organization := "org.scala-exercises"
ThisBuild / githubOrganization := "47degrees"
ThisBuild / scalaVersion := "2.13.3"

addCommandAlias("ci-test", "scalafmtCheckAll; scalafmtSbtCheck; testCovered")
addCommandAlias("ci-docs", "github; documentation/mdoc; headerCreateAll")
addCommandAlias("ci-publish", "github; ci-release")

lazy val `scala-exercises` = (project in file("."))
  .settings(moduleName := "scala-exercises")
  .settings(skip in publish := true)
  .aggregate(server, client, coreJs, coreJvm)
  .dependsOn(server, client, coreJs, coreJvm)

lazy val core = (crossProject(JSPlatform, JVMPlatform) in file("core"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % V.cats
    )
  )

lazy val coreJvm = core.jvm
lazy val coreJs  = core.js.disablePlugins(ScoverageSbtPlugin)

lazy val server = (project in file("server"))
  .aggregate(clients.map(projectToRef): _*)
  .dependsOn(coreJvm)
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .enablePlugins(JavaAppPackaging)
  .settings(skip in publish := true)
  .settings(
    scalaJSProjects := clients,
    pipelineStages in Assets := Seq(scalaJSPipeline),
    pipelineStages := Seq(scalaJSProd, gzip),
    routesGenerator := InjectedRoutesGenerator,
    routesImport += "config.Routes._",
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.Specs2, "console")),
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++= Seq(
      filters,
      jdbc,
      evolutions,
      cacheApi,
      ws,
      caffeine,
      "org.scala-exercises"        %% "runtime"                         % V.runtime,
      "org.scala-exercises"        %% "exercises-stdlib"                % V.exercisesStdlib,
      "org.scala-exercises"        %% "exercises-cats"                  % V.exercisesCats,
      "org.scala-exercises"        %% "exercises-shapeless"             % V.exercisesShapeless,
      "org.scala-exercises"        %% "exercises-scalatutorial"         % V.exercisesScalatutorial,
      "org.scala-exercises"        %% "exercises-fpinscala"             % V.exercisesFpinscala,
      "org.scala-exercises"        %% "exercises-doobie"                % V.exercisesDoobie,
      "org.scala-exercises"        %% "exercises-scalacheck"            % V.exercisesScalacheck,
      "org.scala-exercises"        %% "exercises-fetch"                 % V.exercisesFetch,
      "org.scala-exercises"        %% "exercises-monocle"               % V.exercisesMonocle,
      "org.scala-exercises"        %% "exercises-circe"                 % V.exercisesCirce,
      "com.vmunier"                %% "scalajs-scripts"                 % V.scalajsscripts,
      "com.lihaoyi"                %% "upickle"                         % V.upickle,
      "org.webjars"                %% "webjars-play"                    % V.webjars,
      "org.webjars"                 % "highlightjs"                     % V.highlightjs,
      "org.foundweekends"          %% "knockoff"                        % V.knockoff,
      "com.newrelic.agent.java"     % "newrelic-agent"                  % V.newrelic,
      "org.typelevel"              %% "cats-effect"                     % V.catsEffect,
      "commons-io"                  % "commons-io"                      % V.commonsio,
      "org.webjars.bower"           % "bootstrap-sass"                  % V.bootstrap,
      "com.47deg"                  %% "github4s"                        % V.github4s,
      "org.scalatest"              %% "scalatest"                       % V.scalatest           % Runtime,
      "org.scalatestplus"          %% "scalacheck-1-14"                 % V.scalatestplusScheck % Test,
      "org.tpolecat"               %% "doobie-core"                     % V.doobie,
      "org.tpolecat"               %% "doobie-hikari"                   % V.doobie,
      "org.tpolecat"               %% "doobie-postgres"                 % V.doobie,
      "com.dimafeng"               %% "testcontainers-scala-scalatest"  % V.testcontainers      % Test,
      "com.dimafeng"               %% "testcontainers-scala-postgresql" % V.testcontainers      % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.14"       % V.scalacheckShapeless % Test,
      "org.tpolecat"               %% "doobie-scalatest"                % V.doobie              % Test
    )
  )

lazy val client = (project in file("client"))
  .dependsOn(coreJs)
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
  .disablePlugins(ScoverageSbtPlugin)
  .settings(name := "client")
  .settings(skip in publish := true)
  .settings(
    scalaJSUseMainModuleInitializer := true,
    scalaJSUseMainModuleInitializer in Test := false,
    sourceMappings := SourceMappings.fromFiles(Seq(coreJs.base / "..")),
    jsDependencies += "org.webjars" % "jquery" % "3.6.0" / "3.4.1/jquery.js",
    skip in packageJSDependencies := false,
    testFrameworks += new TestFramework("utest.runner.Framework"),
    scalacOptions += "-Ymacro-annotations",
    libraryDependencies ++= Seq(
      "io.monix"      %%% "monix"          % V.monix,
      "org.typelevel" %%% "cats-core"      % V.cats,
      "com.lihaoyi"   %%% "scalatags"      % V.jsDependencyScalatags,
      "org.scala-js"  %%% "scalajs-dom"    % V.jsDependencyScalajsdom,
      "be.doeraene"   %%% "scalajs-jquery" % V.jsDependencyScalajsjquery,
      "com.lihaoyi"   %%% "upickle"        % V.upickle,
      "com.lihaoyi"   %%% "utest"          % V.jsDependencyUtest % Test
    )
  )

lazy val clients = Seq(client)

lazy val documentation = project
  .settings(mdocOut := file("."))
  .settings(publish / skip := true)
  .enablePlugins(MdocPlugin)
