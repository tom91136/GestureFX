GestureFX
==========

[![Build Status](https://travis-ci.org/tom91136/GestureFX.svg?branch=master)](https://travis-ci.org/tom91136/GestureFX)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Download](https://api.bintray.com/packages/tom91136/maven/gesturefx/images/download.svg)](https://bintray.com/tom91136/maven/gesturefx/_latestVersion)

A lightweight gesture enabled pane for JavaFX
 
Features

 * Accepts any `Node` or implementations of `net.kurobako.gesturefx.GesturePane.Transformable`
 * Pinch-to-zoom
 * Configurable behavior for trackpad events
 * Works with touch screen devices
 * Works in Swing via JFXPanel
 * Zoom/scroll to coordinate with animations
 * Mostly works in SceneBuilder*
 * Zero dependency
 * Works with both Java 8 and OpenJFX 11

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
    <groupId>net.kurobako</groupId>
    <artifactId>gesturefx</artifactId>
    <version>0.3.0</version>
</dependency>
```
You also need to add jcenter repo to your pom:
```xml
<repositories>
    <repository>
        <id>jcenter</id>
        <url>https://jcenter.bintray.com/</url>
    </repository>
</repositories>
```

For SBT
```scala
"net.kurobako" % "gesturefx" % "0.3.0"    
```
And also jcenter:
```scala
resolvers ++= Seq(Resolver.jcenterRepo)
```

Alternatively, you can download the jar [here](https://dl.bintray.com/tom91136/maven/net/kurobako/gesturefx/0.3.0/gesturefx-0.3.0.jar)
 and add it to your classpath. This library has no dependencies so you do not need to download 
anything else.
 
Version history in available in [CHANGELOG.md](CHANGELOG.md)

## Quick start

Adding an `ImageView` to `GesturePane`:

```java
Node node = new ImageView(getClass().getResource("/lena.png").toExternalForm());
GesturePane pane = new GesturePane(node);
```

Translate or zoom:

```java
GesturePane pane = //...

// zoom to 1x 
pane.zoomTo(1);

// centre on point [42,42] 
pane.centreOn(new Point2D(42, 42));

```
And with animations:

```java
pane.animate(Duration.millis(200)).zoomTo(1);
// animate with some options
pane.animate(Duration.millis(200))
		.interpolateWith(Interpolator.EASE_BOTH)
		.beforeStart(() -> System.out.println("Starting..."))
		.afterFinished(() -> System.out.println("Done!"))
		.centreOn(new Point2D(42, 42));

```
Double click to zoom in:
```java
// zoom*2 on double-click
GesturePane pane = //...
pane.setOnMouseClicked(e -> {
	if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
		Point2D pivotOnTarget = pane.targetPointAt(new Point2D(e.getX(), e.getY()))
				                        .orElse(pane.targetPointAtViewportCentre());
		// increment of scale makes more sense exponentially instead of linearly 
		pane.animate(Duration.millis(200))
				.interpolateWith(Interpolator.EASE_BOTH)
				.zoomBy(pane.getCurrentScale(), pivotOnTarget);
	}
});
```

For more interesting examples, take a look at the [samples](gesturefx-sample/src/main/java/net/kurobako/gesturefx/sample).

## Samples

Several samples have been included demoing interesting uses of the gesture pane.

You can download the sample jar [here](https://dl.bintray.com/tom91136/maven/net/kurobako/gesturefx-sample/0.3.0/gesturefx-sample-0.3.0-jar-with-dependencies.jar) 
or clone the project and run:

    ./mvnw install
    ./mvnw exec:java -pl gesturefx-sample

## How to build

To ensure the project is usable with Java 8 and [OpenJFX](https://openjfx.io/), you must build against Java 8. 

Prerequisites:

 * JDK 8 with JavaFX
 
Be aware that some OpenJDK distributions does not include JavaFX or have missing webkit libraries which is required for the sample to build. 

Clone the project and then in project root:

    # *nix:
    ./mvnw clean package 
    # Windows:
    mvnw clean package

If JDK 8 is not your main JDK, prepend the correct `JAVA_HOME` before any maven command (e.g `JAVA_HOME=/usr/java/jdk1.8.0_161/ mvn clean compile`).

This project uses maven wrapper so you do not need to install maven
beforehand.

For testing on new platforms, it is recommended to run tests headful. Add the headful flag to test
with real window:

    mvnw test -Dheadful

**NOTE: Be aware that running the tests headful will spawn actual windows and take over the mouse 
and keyboard; you will see the test window flicker while different unit tests are invoked.**

## Release process

1. Commit all changes before release
2. Make sure `${user.home}/.m2/settings.xml` exist, if not copy it from maven home (i.e `cp usr/share/maven/conf/settings.xml ~/.m2/settings.xml` ) and add the following section to `<servers></servers>`:

    ```xml
    <server>
      <id>bintray-${bintray.user}-maven</id>
      <username>${bintray-username}</username>
      <password>${bintray-api-key}</password>
    </server>
    ```
    Look up bintray-api-key and bintray-username in the bintray profile page, also make sure machine has SSH access to GitHub
3. Run `mvn release:prepare -DdryRun=true`, make sure it succeeds and then run `mvn release:clean`
4. Run `mvn release:prepare`, maven will tag and commit the new version. Inspect the commits and do a push, also push the tags via `git push --tags`
5. Finally, run `mvn clean release:perform` to create docs and sources and upload to bintray 


## Motivation

Someone has to do it.

## Acknowledgement

Features or designs of this library was originally developed as part of an undergraduate coursework 
assignment at the 
[*University of Bristol*](http://www.bristol.ac.uk/engineering/departments/computerscience/). 

## Licence

    Copyright 2018 WEI CHEN LIN
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.