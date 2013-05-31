import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    val appName         = "socket-io-play-sample"
    val appVersion      = "1.0"

    val appDependencies = Seq(
      // Add your project dependencies here,
      "com.imaginea" %% "socket-io-play" % "0.0.3-SNAPSHOT"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      // Add your own project settings here      
      // resolvers += "Local Play Repository" at "file://home/rohit/.ivy2/local"
      resolvers += "OSS Repo" at "https://oss.sonatype.org/content/repositories/snapshots"
    )

}



