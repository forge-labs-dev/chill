import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings

val akkaVersion = "2.6.20"
val algebirdVersion = "0.13.9"
val bijectionVersion = "0.9.7"
val kryoVersion = "4.0.3"
val scroogeVersion = "21.2.0"
val asmVersion = "4.16"
val protobufVersion = "3.25.5"

def scalaVersionSpecificFolders(srcBaseDir: java.io.File, scalaVersion: String): List[File] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((2, y)) if y <= 12 =>
      new java.io.File(s"${srcBaseDir.getPath}-2.12-") :: Nil
    case Some((2, y)) if y >= 13 =>
      new java.io.File(s"${srcBaseDir.getPath}-2.13+") :: Nil
    case Some((3, _)) =>
      // Scala 3 uses the same collection APIs as Scala 2.13
      new java.io.File(s"${srcBaseDir.getPath}-2.13+") :: Nil
    case _ => Nil
  }

val scala212 = "2.12.21"
val scala213 = "2.13.18"
val scala3 = "3.3.4"
val scala2Versions = Seq(scala212, scala213)
val allScalaVersions = scala2Versions :+ scala3

val sharedSettings = Seq(
  organization := "com.twitter",
  scalaVersion := scala213,
  crossScalaVersions := allScalaVersions,
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq("-unchecked", "-deprecation", "-Ywarn-unused", "-release", "17")
      case Some((3, _)) => Seq("-unchecked", "-deprecation", "-Wunused:all", "-release", "17")
      case _            => Seq()
    }
  },
  javacOptions ++= Seq("-source", "17", "-target", "17"),
  Test / fork := true,
  Test / javaOptions ++= Seq(
    "--add-opens",
    "java.base/java.util=ALL-UNNAMED",
    "--add-opens",
    "java.base/java.lang.invoke=ALL-UNNAMED"
  ),
  doc / javacOptions := Seq("-source", "17"),
  resolvers ++= Resolver.sonatypeOssRepos("snapshots") ++ Resolver.sonatypeOssRepos("releases") ++ Seq(
    "clojars".at("https://clojars.org/repo")
  ),
  libraryDependencies ++= Seq(
    "org.scalacheck" %% "scalacheck" % "1.19.0" % "test",
    "org.scalatest" %% "scalatest" % "3.2.19" % "test",
    "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % "test",
    "com.esotericsoftware" % "kryo-shaded" % kryoVersion
  ),
  Test / parallelExecution := true,
  pomExtra := <url>https://github.com/twitter/chill</url>
        <licenses>
      <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        <distribution>repo</distribution>
      <comments>A business-friendly OSS license</comments>
      </license>
      </licenses>
      <developers>
      <developer>
      <id>oscar</id>
      <name>Oscar Boykin</name>
      <url>http://twitter.com/posco</url>
        </developer>
      <developer>
      <id>sritchie</id>
      <name>Sam Ritchie</name>
      <url>http://twitter.com/sritchie</url>
        </developer>
      </developers>,
  Compile / unmanagedSourceDirectories ++= scalaVersionSpecificFolders(
    (Compile / scalaSource).value,
    scalaVersion.value
  ),
  Test / unmanagedSourceDirectories ++= scalaVersionSpecificFolders(
    (Test / scalaSource).value,
    scalaVersion.value
  ),
  Compile / unmanagedSourceDirectories ++= scalaVersionSpecificFolders(
    (Compile / javaSource).value,
    scalaVersion.value
  )
)

// Aggregated project
lazy val chillAll = Project(
  id = "chill-all",
  base = file(".")
).settings(sharedSettings)
  .settings(noPublishSettings)
  .settings(
    mimaPreviousArtifacts := Set.empty,
    crossScalaVersions := Nil
  )
  .aggregate(
    chill,
    chillBijection,
    chillScrooge,
    chillStorm,
    chillJava,
    chillHadoop,
    chillThrift,
    chillProtobuf,
    chillAkka,
    chillAvro,
    chillAlgebird
  )

lazy val noPublishSettings = Seq(
  publish / skip := true,
  publish := {},
  publishLocal := {},
  test := {},
  publishArtifact := false
)

/**
 * This returns the youngest jar we released that is compatible with the current.
 */
val unreleasedModules = Set[String]("akka", "avro")
val javaOnly = Set[String]("storm", "java", "hadoop", "thrift", "protobuf")
val binaryCompatVersion = "0.10.0"

def youngestForwardCompatible(subProj: String) =
  Some(subProj)
    .filterNot(unreleasedModules.contains)
    .map { s =>
      if (javaOnly.contains(s))
        "com.twitter" % ("chill-" + s) % binaryCompatVersion
      else
        "com.twitter" %% ("chill-" + s) % binaryCompatVersion
    }

val ignoredABIProblems = {
  import com.typesafe.tools.mima.core._
  import com.typesafe.tools.mima.core.ProblemFilters._
  Seq(
    exclude[MissingTypesProblem]("com.twitter.chill.storm.BlizzardKryoFactory"),
    exclude[MissingTypesProblem]("com.twitter.chill.InnerClosureFinder"),
    exclude[IncompatibleResultTypeProblem]("com.twitter.chill.InnerClosureFinder.visitMethod"),
    exclude[IncompatibleResultTypeProblem]("com.twitter.chill.FieldAccessFinder.visitMethod"),
    exclude[MissingClassProblem]("com.twitter.chill.FieldAccessFinder"),
    exclude[MissingTypesProblem]("com.twitter.chill.FieldAccessFinder"),
    exclude[DirectMissingMethodProblem]("com.twitter.chill.FieldAccessFinder.this"),
    exclude[IncompatibleResultTypeProblem]("com.twitter.chill.Tuple1*Serializer.read"),
    exclude[IncompatibleMethTypeProblem]("com.twitter.chill.Tuple1*Serializer.write"),
    exclude[IncompatibleResultTypeProblem]("com.twitter.chill.Tuple2*Serializer.read"),
    exclude[IncompatibleMethTypeProblem]("com.twitter.chill.Tuple2*Serializer.write")
  )
}

def module(name: String) = {
  val id = "chill-%s".format(name)
  Project(id = id, base = file(id))
    .settings(sharedSettings)
    .settings(
      Keys.name := id,
      mimaPreviousArtifacts := youngestForwardCompatible(name).toSet,
      mimaBinaryIssueFilters ++= ignoredABIProblems
    )
}

// We usually do the pattern of having a core module, but we don't want to cause
// pain for legacy deploys. With this, they can stay the same.
lazy val chill = Project(
  id = "chill",
  base = file("chill-scala")
).settings(sharedSettings)
  .settings(
    name := "chill",
    mimaPreviousArtifacts := Set("com.twitter" %% "chill" % binaryCompatVersion),
    mimaBinaryIssueFilters ++= ignoredABIProblems,
    libraryDependencies += "org.apache.xbean" % "xbean-asm7-shaded" % asmVersion
  )
  .dependsOn(chillJava)

def akka(scalaVersion: String) =
  ("com.typesafe.akka" %% "akka-actor" % akkaVersion) % "provided"

// Akka 2.6 only supports Scala 2.x
lazy val chillAkka = module("akka")
  .settings(
    crossScalaVersions := scala2Versions,
    resolvers += Resolver.typesafeRepo("releases"),
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.2",
      scalaVersion(sv => akka(sv)).value
    )
  )
  .dependsOn(chill % "test->test;compile->compile")

// Bijection only supports Scala 2.x
lazy val chillBijection = module("bijection")
  .settings(
    crossScalaVersions := scala2Versions,
    libraryDependencies ++= Seq(
      "com.twitter" %% "bijection-core" % bijectionVersion
    )
  )
  .dependsOn(chill % "test->test;compile->compile")

// This can only have java deps!
lazy val chillJava = module("java").settings(
  crossPaths := false,
  autoScalaLibrary := false
)

// This can only have java deps!
lazy val chillStorm = module("storm")
  .settings(
    crossPaths := false,
    autoScalaLibrary := false,
    libraryDependencies += "org.apache.storm" % "storm-core" % "2.6.4" % "provided"
  )
  .dependsOn(chillJava)

// This can only have java deps!
lazy val chillHadoop = module("hadoop")
  .settings(
    crossPaths := false,
    autoScalaLibrary := false,
    libraryDependencies ++= Seq(
      "org.apache.hadoop" % "hadoop-core" % "1.2.1" % "provided",
      "org.slf4j" % "slf4j-api" % "2.0.16",
      "org.slf4j" % "slf4j-log4j12" % "2.0.16" % "provided"
    )
  )
  .dependsOn(chillJava)

// This can only have java deps!
lazy val chillThrift = module("thrift").settings(
  crossPaths := false,
  autoScalaLibrary := false,
  libraryDependencies ++= Seq(
    "org.apache.thrift" % "libthrift" % "0.21.0" % "provided"
  )
)

// Scrooge only supports Scala 2.x
lazy val chillScrooge = module("scrooge")
  .settings(
    crossScalaVersions := scala2Versions,
    libraryDependencies ++= Seq(
      ("org.apache.thrift" % "libthrift" % "0.21.0").exclude("junit", "junit"),
      "com.twitter" %% "scrooge-serializer" % scroogeVersion
    )
  )
  .dependsOn(chill % "test->test;compile->compile")

// This can only have java deps!
lazy val chillProtobuf = module("protobuf")
  .settings(
    crossPaths := false,
    autoScalaLibrary := false,
    libraryDependencies += "com.google.protobuf" % "protobuf-java" % protobufVersion % Provided,
    Test / PB.targets := Seq(
      PB.gens.java(protobufVersion) -> (Test / sourceManaged).value
    )
  )
  .dependsOn(chillJava)

// Avro depends on bijection which only supports Scala 2.x
lazy val chillAvro = module("avro")
  .settings(
    crossScalaVersions := scala2Versions,
    libraryDependencies ++= Seq(
      "com.twitter" %% "bijection-avro" % bijectionVersion,
      "junit" % "junit" % "4.13.2" % "test"
    )
  )
  .dependsOn(chill, chillJava, chillBijection)

// Algebird only supports Scala 2.x
lazy val chillAlgebird = module("algebird")
  .settings(
    crossScalaVersions := scala2Versions,
    libraryDependencies ++= Seq(
      "com.twitter" %% "algebird-core" % algebirdVersion
    )
  )
  .dependsOn(chill)
