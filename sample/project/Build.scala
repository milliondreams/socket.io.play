import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "socket.io.play.sample"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      // Add your project dependencies here,
      "com.imaginea" %% "socket.io.play" % "0.0.3-SNAPSHOT"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
      // Add your own project settings here      
      // resolvers += "Local Play Repository" at "file://home/rohit/.ivy2/local"
    )

}
