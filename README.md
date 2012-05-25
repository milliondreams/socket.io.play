# socket.io for Play! Framework 2.0 (for Scala)

First a couple of questions, which you would already know about if you are reading this! ;)

## What is socket.io?

socket.io provides browser independent bidirectional realtime socket-like communication between server ad browser.
From [socket.io site](http://socket.io/)
>
> Socket.IO aims to make realtime apps possible in every browser and mobile device, blurring the differences between the different transport mechanisms. It's care-free realtime 100% in JavaScript.
>

## What does this project aim todo?

I aim to make it dead simple to make socket.io server side component to work with the standard (unmoddified) soccket.io JS client library.
This means a developer should be able to focus on writing the logic for his/her application rather than having to deal with the nitty grities of the protocol.

## What is the current state of this project?

This project is in its *very initial days* and I am working on it *whenever I get time* to. That *doesn't* mean there is nothing here!

I am able to currently use the library successfully (see the sample app).

* The socket.io packet parsing and event handling is implemented
* There is basic support for broadcast
* Only transport layer supported is websockets (that means *modern* browsers only)

## What is on your todo list?

Somewhat ordered by priority -

* Implement XHR polling and json polling for transport
* Get feedback and freeze the API
* Code cleanup/review/reorganization
* Sample application (chat or something else?)
* Code and usage documentation
* Test cases
* Implement flashsocket for transport
* Support Play! for Java

## How do I use this?

The easiest way to getting started would be to look at the sample app! But I will list down the steps here. Assuming you already have a Play! Application

1. Open your Build.scala and add the following to the dependencies

```
 "com.imaginea" %% "socket.io.play" % "0.0.3-SNAPSHOT"
```

2. Create the actor that will handle the events. This actor should implement socketio.SocketIOActor

```scala
 class MySocketIO extends SocketIOActor
```

3. Now, you need to implement the partial function, 'processMessage' which will handle the event.

```scala
    def processMessage: PartialFunction[(String, (String, String, Any)), Unit] = {
        //Process regular message
        case ("message", (sessionId: String, namespace: String, msg: String)) => { ... }
        //Handle event
        case ("someEvt", (sessionId: String, namespace: String, eventData: JsValue)) => { ... }
    }
```


4. Create a controller extending SocketIOController and set the socketIOActor in it

```scala
    object MySocketIOController extends SocketIOController {
      lazy val socketIOActor: ActorRef = {
        Akka.system.actorOf(Props[MySocketIO])
      }
    }
```

5. Go to your conf/routes file and add the socket.io route to it

```
  GET     /socket.io/1/$socketUrl<.*>     controllers.MySocketIOController.handler(socketUrl)
```

6. You are all set! Now you can start using socket.io JS client as you normally would.

## I found a bug! What to do?

Create a new issue in [github issues](https://github.com/rohit-tingendab/socket.io.play/issues)!

## I couldn't get it to run, or I got an error trying to run it!

Mail on the Play! Framework 2.0 mailing list, tagging it with [socketio4play] in subject. You can also tweet to @milliondreams with #socketio4play hashtag or create a new issue.

## This is great! I want to use it on my project!

Glad that you like it, go ahead and use it. It is licensed under Apache License, Version 2.0 and you are free to use it. If possible put a mail to us, *opensource* at *imaginea* dot com.

## I need paid support!

Mail *opensource* at *imaginea* dot com and we should be able to help.



