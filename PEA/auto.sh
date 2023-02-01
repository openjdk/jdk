#!/bin/bash 
#
function jdk_select() {
    if [ -e $1/bin/java ]; then
        P=$(realpath $1)
        export JAVA_HOME=$P
        export PATH=$JAVA_HOME/bin:$PATH
        echo "choose $P"
        java -version
    else
        echo "can't find valid jdk!"
    fi
}
jdk_select ../build/linux-x86_64-server-fastdebug/images/jdk

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
