#!/bin/sh
# @test test.sh
# @bug 8017248
# @summary Compiler Diacritics Issue
# @run shell test.sh

OSNAME=`uname -s`
if [ "$OSNAME" == "Darwin" ] 
then
  rm *.class
  ${TESTJAVA}/bin/javac *.java
  ${TESTJAVA}/bin/java `echo *.class | cut -d. -f1`
else
  echo Test is specific to Mac OS X, skipping.
  exit 0
fi
