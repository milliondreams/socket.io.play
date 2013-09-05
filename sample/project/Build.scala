import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "socketio"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    anorm
     "com.imaginea" % "socket-io-play_2.10" % "0.0.3-SNAPSHOT"
  )


  val main = play.Project(appName, appVersion, appDependencies).settings(
    // Add your own project settings here  
    
  )

}
