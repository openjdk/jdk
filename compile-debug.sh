#! /usr/bin/env bash

target=$(find build -name *fastdebug)

./"${target}"/images/jdk/bin/javac \
  -classpath ../jtreg/build/deps/testng/testng-7.3.0.jar \
  -J-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 \
  ./test/jdk/java/util/Map/SyntaxSugarMapTest.java