#!/bin/sh
FILENAME="./pessimistic_$(date|sed "s/ /_/g"|sed "s/:/_/g").jfr"
$JAVA_HOME/bin/java -ea -Xms2G -Xmx2G -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:FlightRecorderOptions=defaultrecording=true,disk=true,dumponexit=true,dumponexitpath=$FILENAME,stackdepth=128 -Djava.ext.dirs=dist jdk.nashorn.tools.Shell ${@}
