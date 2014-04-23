#!/bin/sh

#FLAGS="-Djava.lang.invoke.MethodHandle.COMPILE_THRESHOLD=3 -Djava.lang.invoke.MethodHandle.DUMP_CLASS_FILES=true -Djava.lang.invoke.MethodHandle.TRACE_METHOD_LINKAGE=true -Djava.lang.invoke.MethodHandle.TRACE_INTERPRETER=true"
#FLAGS="-Djava.security.manager -Djava.security.policy=../build/nashorn.policy -Dnashorn.debug"

FILENAME="./optimistic_$(date|sed "s/ /_/g"|sed "s/:/_/g").jfr"

DIR=..
NASHORN_JAR=$DIR/dist/nashorn.jar

$JAVA_HOME/bin/java \
$FLAGS \
-ea \
-esa \
-Xbootclasspath/p:$NASHORN_JAR \
-Xms2G -Xmx2G \
-XX:+UnlockCommercialFeatures \
-XX:+FlightRecorder \
-XX:FlightRecorderOptions=defaultrecording=true,disk=true,dumponexit=true,dumponexitpath=$FILENAME,stackdepth=1024 \
-XX:TypeProfileLevel=222 \
-XX:+UnlockExperimentalVMOptions \
-XX:+UseTypeSpeculation \
-XX:+UseMathExactIntrinsics \
-XX:+UnlockDiagnosticVMOptions \
-cp $CLASSPATH:../build/test/classes/ \
jdk.nashorn.tools.Shell ${@}

#-Djava.security.manager= -Djava.security.policy=$DIR/build/nashorn.policy \
#-XX:+ShowHiddenFrames \
#-XX:+PrintOptoAssembly \
#-XX:-TieredCompilation \
#-XX:CICompilerCount=1 \
