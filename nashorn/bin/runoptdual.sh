#!/bin/sh
FILENAME="./optimistic_dual_$(date|sed "s/ /_/g"|sed "s/:/_/g").jfr"
$JAVA_HOME/bin/java -ea -Dnashorn.fields.dual -Dnashorn.optimistic -Xms2G -Xmx2G -XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:FlightRecorderOptions=defaultrecording=true,disk=true,dumponexit=true,dumponexitpath=$FILENAME,stackdepth=128 -XX:TypeProfileLevel=222 -XX:+UnlockExperimentalVMOptions -XX:+UseTypeSpeculation -XX:-UseMathExactIntrinsics ${@}
