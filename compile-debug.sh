#! /usr/bin/env bash

target=$(find build -name *fastdebug)

./${target}/images/jdk/bin/javac \
  -J-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 \
  ./test/jdk/java/util/Map/SyntaxSugarMapTest.java