#!/bin/sh

set -e

JAVA_HOME='/export/lanai/build/macosx-x64-debug/jdk'

$JAVA_HOME/bin/javac MyMacCanvas.java

gcc -v myfile.m -I"$JAVA_HOME/include" -c -o myfile.o

gcc -v myfile.o -L$JAVA_HOME/lib -ljawt -framework AppKit -framework OpenGL -framework Metal -framework Quartz -shared -o libmylib.jnilib

$JAVA_HOME/bin/java -Djava.library.path=. -Dsun.java2d.metal=True MyMacCanvas
