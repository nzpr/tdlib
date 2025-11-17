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
    organization := "nzpr",
    name := "tdlib",
    version := "0.1.0-SNAPSHOT",

    // Exclude native libraries from the published JAR
    Compile / packageBin / mappings := (Compile / packageBin / mappings).value
      .filter { case (_, path) =>
        !path.startsWith("native/")
      },

    // Run in a separate JVM so native libs can be loaded
    fork := true,
    run / connectInput := true,
    javaOptions ++= Seq("--enable-native-access=ALL-UNNAMED"),

    // Publishing configuration
    publishTo := {
      val nexus = "https://s01.oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    publishMavenStyle := true,
    Test / publishArtifact := false,
    pomIncludeRepository := { _ => false },

    // Project metadata
    homepage := Some(url("https://github.com/nzpr/tdlib")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/nzpr/tdlib"),
        "scm:git@github.com:nzpr/tdlib.git"
      )
    ),
    developers := List(
      Developer(
        id = "nzpr",
        name = "nzpr",
        email = "",
        url = url("https://github.com/nzpr")
      )
    ),
    licenses := Seq(
      "BSL-1.0" -> url("https://opensource.org/licenses/BSL-1.0")
    ),

    // GPG signing
    credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials")
  )
