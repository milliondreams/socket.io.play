import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  override lazy val settings = super.settings ++ buildSettings
  def buildSettings = Seq(
    organization    := "com.imaginea",
    publishArtifact in packageDoc := false,
    publishArtifact in Test := false,
    publishMavenStyle := true,
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/rohit-tingendab/socket.io.play")),
    pomIncludeRepository := { _ => false },
    pomExtra := (
        <scm>
          <url>git@github.com:rohit-tingendab/socket.io.play.git</url>
          <connection>scm:git:git@github.com:rohit-tingendab/socket.io.play.git</connection>
        </scm>
        <developers>
          <developer>
            <id>rohitbrai</id>
            <name>Rohit Rai</name>
            <url>https://github.com/rohit-tingendab/</url>
          </developer>
        </developers>)
  )

  val appName         = "socket-io-play"
  val appVersion      = "0.0.3-SNAPSHOT"
  val description     =
    """
       socket.io.play is a play module that provides a easy way to build the server component
       for socket.io in your Play! 2.0 application.
    """.stripMargin

  val appDependencies = Seq(
    // Add your project dependencies here,
  )

  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here      
  )

}


