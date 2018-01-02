#!/bin/sh

#mvn clean install
java -cp target/hello-world-0.1-SNAPSHOT-jar-with-dependencies.jar com.davidron.fargatedemo.Deploy "$@"