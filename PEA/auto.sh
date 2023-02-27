#!/bin/bash 

set -x
JVM_FLAGS="-XX:+PrintEscapeAnalysis -XX:+PrintEliminateAllocations -XX:+PrintOptoAssembly"
./run1.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
./run1_ivanov.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
./run2.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
./run2_merykitty.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
./run2b.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
./run2_1.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
./run3_1.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
./run3_2.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
./run3.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
./run_str.sh  -Xlog:gc -XX:+DoPartialEscapeAnalysis -XX:-UseTLAB  $JVM_FLAGS
./run_exception.sh -Xlog:gc -XX:+DoPartialEscapeAnalysis $JVM_FLAGS
