GestureFX
==========

A lightweight gesture enabled pane

Features

 * Accepts any `Node`
 * Pinch-to-zoom
 * Configurable behavior for trackpad events
 * Zoom to coordinate with animations
 * Zero dependency

## How to use

For Maven users, add the following to pom

    <dependency>
        <groupId>net.kurobako.gesturefx</groupId>
        <artifactId>gesturefx</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    
Alternatively, you can download the following jar and add it to your classpath:

// TODO add jar path
 
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