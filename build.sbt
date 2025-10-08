inThisBuild(
  List(
    scalaVersion := "2.13.17",
    crossVersion := CrossVersion.full,
    crossScalaVersions := Seq("2.12.20", scalaVersion.value),
    organization := "io.regadas",
    organizationName := "regadas",
    licenses := Seq(
      "APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")
    ),
    homepage := Some(url("https://github.com/regadas/socco-ng")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/regadas/socco-ng"),
        "scm:git@github.com:regadas/socco-ng.git"
      )
    ),
    developers := List(
      Developer(
        id = "guillaumebort",
        name = "Guillaume Bort",
        email = "g.bort@criteo.com",
        url = url("https://github.com/guillaumebort")
      ),
      Developer(
        id = "regadas",
        name = "Filipe Regadas",
        email = "oss@regadas.email",
        url = url("https://twitter.com/regadas")
      )
    ),
    publishMavenStyle := true
  )
)

lazy val socco =
  (project in file("."))
    .settings(
      name := "socco-ng",
      crossVersion := CrossVersion.full,
      scalacOptions ++= Seq(
        "-deprecation",
        "-encoding",
        "UTF-8",
        "-feature",
        "-unchecked",
        "-Xlint",
        "-Ywarn-dead-code",
        "-Xfuture"
        // "-Yno-adapted-args",
        // "-Ywarn-unused-import"
      ),
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-compiler" % scalaVersion.value,
        "org.planet42" %% "laika-core" % "0.19.5"
      ),
      pomPostProcess := removeDependencies("org.planet42", "org.scala-lang"),
      // Vendorise internal libs
      Compile / packageBin / publishArtifact := false,
      Compile / assembly / artifact := {
        val core = (Compile / packageBin / artifact).value
        val vendorised = (Compile / assembly / artifact).value
        vendorised
      },
      assembly / assemblyExcludedJars := {
        (assembly / fullClasspath).value.filter {
          case jar if jar.data.getName.startsWith("scala-reflect-")  => true
          case jar if jar.data.getName.startsWith("scala-library-")  => true
          case jar if jar.data.getName.startsWith("scala-compiler-") => true
          case _                                                     => false
        }
      },
      // Used to generate examples
      commands += Command.command("generateExamples") { (state) =>
        def enablePlugin(
            userStyle: Option[String] = None,
            linkScala: Boolean = false
        ) = {
          val X = Project.extract(state)
          val sv = X.get(scalaVersion)
          val soccoVersion = X.get(version)

          s"""
          set scalacOptions in examples := Seq(
            "-Xplugin:target/scala-${sv
              .split("[.]")
              .take(2)
              .mkString(".")}/socco-ng-assembly-${soccoVersion}.jar",
            ${userStyle
              .map(style =>
                "\"-P:socco:style:examples/src/main/styles/" + style + ".css\","
              )
              .getOrElse("")}
            ${if (linkScala)
              "\"-P:socco:package_scala:http://www.scala-lang.org/api/current/\","
            else ""}
            "-P:socco:out:examples/target/html${userStyle
              .map(style => s"/$style")
              .getOrElse("")}",
            "-P:socco:package_fs2:https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_2.12/0.9.5/fs2-core_2.12-0.9.5-javadoc.jar/!",
            "-P:socco:package_io.circe:http://circe.github.io/circe/api/"
          )
        """.trim.replaceAll("\n", " ")
        }

        "examples/clean" :: "assembly" ::
          enablePlugin() :: "examples/compile" ::
          enablePlugin(Some("userStyle1")) :: "examples/compile" ::
          enablePlugin(Some("userStyle2"), true) :: "examples/compile" ::
          state
      }
    )
    .settings(addArtifact(Compile / assembly / artifact, assembly): _*)

lazy val examples =
  project.settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "3.2.2"
    ),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-optics"
    ).map(_ % "0.14.1")
  )

def removeDependencies(groups: String*)(xml: scala.xml.Node) = {
  import scala.xml._
  import scala.xml.transform._
  (new RuleTransformer(
    new RewriteRule {
      override def transform(n: Node): Seq[Node] =
        n match {
          case dependency @ Elem(_, "dependency", _, _, _*) =>
            if (
              dependency.child.collect { case e: Elem => e }.headOption.exists {
                e =>
                  groups
                    .exists(group => e.toString == s"<groupId>$group</groupId>")
              }
            ) Nil
            else dependency
          case x => x
        }
    }
  ))(xml)
}
