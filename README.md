GestureFX
==========

[![Java CI](https://github.com/tom91136/GestureFX/actions/workflows/main.yaml/badge.svg)](https://github.com/tom91136/GestureFX/actions/workflows/main.yaml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

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
 * Works with both Java 8 and OpenJFX 11+

For comparison, this library is similar to [PhotoView](https://github.com/chrisbanes/PhotoView) 
for Android but supports gestures on any `Node` subclass.

*SceneBuilder renders the control properly and all the exposed properties are editable in the 
sidebar. Unfortunately, I have no idea how to make SceneBuilder treat this control as a 
container/control so the only way to add `GesturePane` to your FXML is to add it in XML and then 
open it in SceneBuilder. Pull requests welcome on solving this.

## How to use

**Versions <= 0.6.0 was published on JCenter, versions >= 0.7.1 is now published on Maven Central**

For Maven users, add the following to pom
```xml
<dependency>
    <groupId>net.kurobako</groupId>
    <artifactId>gesturefx</artifactId>
    <version>0.7.1</version>
</dependency>
```

For SBT
```scala
"net.kurobako" % "gesturefx" % "0.7.1"    
```

Alternatively, you can download the jar [here](https://repo.maven.apache.org/maven2/net/kurobako/gesturefx/0.7.1/gesturefx-0.7.1.jar)
 and add it to your classpath. This library has no dependencies, so you do not need to download 
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

**JavaFX 8**

You can download the sample jar [here](https://repo.maven.apache.org/maven2/net/kurobako/gesturefx-sample/0.7.1/gesturefx-sample-0.7.1-jar-with-dependencies.jar) 
or clone the project and run:

    ./mvnw install
    ./mvnw exec:java -pl gesturefx-sample
    

**OpenJFX 11+**

Make sure you have at least JDK 11 installed:

    > java -version
    openjdk version "11.0.4" 2019-07-16
    OpenJDK Runtime Environment 18.9 (build 11.0.4+11)
    OpenJDK 64-Bit Server VM 18.9 (build 11.0.4+11, mixed mode, sharing)


Run the sample jar with the following:

    java -Dglass.gtk.uiScale=200% --module-path path/to/javafx-sdk-13/lib --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.swing -jar gesturefx-sample-0.7.1-jar-with-dependencies.jar

See <https://openjfx.io/openjfx-docs/#install-javafx> for more details.

The `-Dglass.gtk.uiScale=200%` flag is optional if OpenJFX does not detect HiDPI monitors automatically. 
On Windows the flag should be `-Dglass.win.uiScale=N%`.


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
      <id>ossrh</id>
      <username>${jira-username}</username>
      <password>${jira-password}</password>
    </server>
    ```
    Look up jira-username and jira-password is the username and password for sonatype, also make sure machine has SSH access to GitHub
3. Run `mvn release:prepare -DdryRun=true -Darguments=-DskipTests`, make sure it succeeds 
4. Run `mvn release:clean` to clean up from the release dry run 
5. Run `mvn release:prepare -Darguments=-DskipTests`, maven will tag and commit the new version. 
6. Inspect the commits after `release:prepare` and do a push, also push the tags via `git push --tags`
7. Finally, run `mvn clean release:perform -Darguments=-DskipTests` to create docs and sources and upload sonatype
8. Complete the release process by closing via `cd target/checkout && mvn nexus-staging:release` 

## Motivation

Someone has to do it.

## Acknowledgements

Features or designs of this library was originally developed as part of an undergraduate coursework 
assignment at the 
[*University of Bristol*](http://www.bristol.ac.uk/engineering/departments/computerscience/). 


<img src="https://www.yourkit.com/images/yklogo.png" align="right" />

YourKit supports the GestureFX project with its full-featured Java Profiler.
YourKit, LLC is the creator [YourKit Java Profiler](https://www.yourkit.com/java/profiler/index.jsp)
and [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/index.jsp),
innovative and intelligent tools for profiling Java and .NET applications.


## Licence

    Copyright 2021 WeiChen Lin
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
       http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.