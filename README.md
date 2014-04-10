# socket.io for Play! Framework 2.0 (for Scala)

Socket.io server side support for Play framework.

## What is socket.io?

socket.io provides browser independent bidirectional realtime socket-like communication between server and browser. 
From [socket.io site](http://socket.io/)
>
> Socket.IO aims to make realtime apps possible in every browser and mobile device, blurring the differences between the different transport mechanisms. It's care-free realtime 100% in JavaScript.
>

## Features


* Support for Websocket transport for modern browsers
* Support for xhr-handling for everything else
* Push events/messages to clients, respond to client side events/messages
* Dead clients time out

## TODO

* Get feedback and freeze the API

## How To Use

The easiest way to getting started would be to look at the sample app.

Manual Steps:

The below mentioned repo might not be updated, so it's best to compile and publish locally.

* Add Sonatype OSS repo to your Play framework

```scala
  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    resolvers += "OSS Repo" at "https://oss.sonatype.org/content/repositories/snapshots"
  )
```
* Open your Build.scala and add the following to the dependencies

```scala
 "com.imaginea" %% "socket.io.play" % "0.0.3-SNAPSHOT"
```
* Create a controller which extends SocketIOController trait. It will handle client events/messages.

Inside this implement the partial function, 'processMessage' which will handle the events/messages. Set clientTimeout to a duration(in seconds) for timeouts for heartbeat, client.

An example implementation from sample -

```scala
    object MySocketIOController extends SocketIOController {

      val clientTimeout = Timeout(10.seconds)

      def processMessage(sessionId: String, packet: Packet) {

        ...
        }

      }
```

You have these functions to enqueue events/messages to clients:

```scala
    def enqueueMsg(sessionId: String, msg: String) 

    def enqueueEvent(sessionId: String, event: String)

    def enqueueJsonMsg(sessionId: String, msg: String)

    def broadcastMsg(msg: String)

    def broadcastEvent(event: String)

    def broadcastJsonMsg(msg: String)
```

* Go to your conf/routes file and add the socket.io route to it

```scala
  GET     /socket.io/1/$socketUrl<.*>     controllers.MySocketIOController.handler(socketUrl)
```

* That's it. Now you can start using socket.io clients as you normally would.


### Note:

* There is a parser available to you, all the events/messages that you enqueue are supposed to be already encoded strings.
  Use encodePacket from Parser. See sample app for details.

* You get a sessionId in the processMessage, you can maintain a sessionId container and process events specific to clients.


## I found a bug

Please report it in github issues

## Need help running it?

Mail -

saurabh.rawat90@gmail.com

Please mention socket.io.play in the subject.


## This is great! I want to use it on my project!

Glad that you like it, go ahead and use it. It is licensed under Apache License, Version 2.0.

If possible put a mail to us, opensource at imaginea dot com.
