#!/bin/sh

#FLAGS="-Djava.lang.invoke.MethodHandle.COMPILE_THRESHOLD=3 -Djava.lang.invoke.MethodHandle.DUMP_CLASS_FILES=true -Djava.lang.invoke.MethodHandle.TRACE_METHOD_LINKAGE=true -Djava.lang.invoke.MethodHandle.TRACE_INTERPRETER=true"

DIR=..
NASHORN_JAR=$DIR/dist/nashorn.jar

$JAVA_HOME/bin/java \
$FLAGS \
-ea \
-esa \
-Xbootclasspath/p:$NASHORN_JAR \
-Xms2G -Xmx2G \
-XX:+UnlockCommercialFeatures \
-XX:TypeProfileLevel=222 \
-XX:+UnlockExperimentalVMOptions \
-XX:+UseTypeSpeculation \
-XX:+UseMathExactIntrinsics \
-XX:+UnlockDiagnosticVMOptions \
-XX:+UseNewCode \
-cp $CLASSPATH:../build/test/classes/ \
jdk.nashorn.tools.Shell ${@}

#-XX:+ShowHiddenFrames \
#-XX:+PrintOptoAssembly \
#-XX:-TieredCompilation \
#-XX:CICompilerCount=1 \
