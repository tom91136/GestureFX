GestureFX
==========

A lightweight gesture enabled pane for JavaFX
 
Features

 * Accepts any `Node` or implementations of `net.kurobako.gesturefx.GesturePane.Transformable`
 * Pinch-to-zoom
 * Configurable behavior for trackpad events
 * Works with touch screen devices
 * Zoom/scroll to coordinate with animations
 * Mostly works in SceneBuilder*
 * Zero dependency

For comparison, this library is similar to [PhotoView](https://github.com/chrisbanes/PhotoView) 
for Android but supports gestures on any `Node` subclass.

*SceneBuilder renders the control properly and all the exposed properties are editable in the 
sidebar. Unfortunately, I have no idea how to make SceneBuilder treat this control as a 
container/control so the only way to add `GesturePane` to your FXML is to add it in XML and then 
open it in SceneBuilder. Pull requests welcome on solving this.

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
Alternatively, you can download the jar [here]() and add it to your classpath.

## Quick start

Adding an `ImageView` to `GesturePane`:

```java
Node node = new ImageView(getClass().getResource("/lena.png").toExternalForm());
GesturePane pane = new GesturePane(node);
```

For more interesting examples, take a look at the [samples](TODO).

## Samples

Several samples have been included demoing interesting uses of the gesture pane.

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

This project uses maven wrapper so you do not need to install maven
beforehand.

**NOTE: Be aware that running the tests will spawn actual windows and take over the mouse 
and keyboard; you will see the test window flicker while different unit tests are invoked.**

## Motivation

Someone has to do it.

## Acknowledgement

Features or designs of this library was originally developed as part of an undergraduate coursework 
assignment at the 
[*University of Bristol*](http://www.bristol.ac.uk/engineering/departments/computerscience/). 

## Licence

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