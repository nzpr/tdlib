val scala3Version = "3.7.3"

val catsVersion = "2.13.0"
val catsEffectVersion = "3.6.3"
val fs2Version = "3.12.2"
val logbackVersion = "1.4.11"
val log4catsVersion = "2.7.1"
val scalatestVersion = "3.2.19"
val caTestingVersion = "1.7.0"

val cats = "org.typelevel" %% "cats-core" % catsVersion
val catsEffect = "org.typelevel" %% "cats-effect" % catsEffectVersion
val fs2Core = "co.fs2" %% "fs2-core" % fs2Version
val logback = "ch.qos.logback" % "logback-classic" % logbackVersion
val log4Cats = "org.typelevel" %% "log4cats-slf4j" % log4catsVersion

val scalaTest = "org.scalatest" %% "scalatest" % scalatestVersion % "test"
val caTesting =
  "org.typelevel" %% "cats-effect-testing-scalatest" % caTestingVersion % "test"

val langDeps = Seq(
  cats,
  catsEffect,
  fs2Core,
  logback,
  log4Cats
)

val testDeps = Seq(scalaTest, caTesting)

lazy val langSettings = Seq(
  scalaVersion := scala3Version,
  javacOptions ++= Seq("-source", "17", "-target", "17"),
  libraryDependencies ++= langDeps ++ testDeps
)

lazy val root = (project in file("."))
  .settings(langSettings: _*)
  .settings(
    name := "tdlib",
    version := "0.1.0-SNAPSHOT",

    // Run in a separate JVM so native libs can be loaded
    fork := true,
    run / connectInput := true,
    javaOptions ++= Seq("--enable-native-access=ALL-UNNAMED")
  )
