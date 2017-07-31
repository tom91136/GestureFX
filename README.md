GestureFX
==========

A lightweight gesture enabled pane for JavaFX
 
 
Features

 * Accepts any `Node` or implementations of `Transformable`
 * Pinch-to-zoom
 * Configurable behavior for trackpad events
 * Works with touch screen devices
 * Works in SceneBuilder
 * Zoom to coordinate with animations
 * Zero dependency

For comparison, this library is similar to [PhotoView](https://github.com/chrisbanes/PhotoView) 
for Android but supports gestures on any `Node` subclass.


## How to use

For Maven users, add the following to pom
```xml
    <dependency>
        <groupId>net.kurobako.gesturefx</groupId>
        <artifactId>gesturefx</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
```

For SBT    
```scala
    "net.kurobako.gesturefx" % "gesturefx" % "1.0-SNAPSHOT"    
```
Alternatively, you can download the following jar and add it to your classpath:

// TODO add jar path

You can download the samples [here]() or clone the project and run:

    

## Quick start


```java
    Node node = new ImageView(getClass().getResource("/lena.png").toExternalForm());
    GesturePane page = new GesturePane(node);
```

Take a look at the [samples](TODO) for more interesting examples

## Samples

Several samples have been included demoing several uses of the gesture pane.

You can download the sample jar [here](TODO) or clone the project and run:

    ./mvnw install
    ./mvnw exec:java -pl gesturefx-sample

## How to build

Prerequisites:

 * JDK 8 

Clone the project and then in project root:

    # *nix:
    ./mvnw clean package 
    # Windows:
    mvnw clean package

Note: This project uses maven wrapper so you do not need to install maven
beforehand.

## Motivation

Someone has to do it.

## Acknowledgement

Features or designs of this library was originally developed as part of an undergraduate coursework 
at the *University of Bristol* in the UK. 

## License

    Copyright 2017 WEI CHEN LIN
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.