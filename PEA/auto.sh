#!/bin/bash

JVM_FLAGS="-XX:+PrintEscapeAnalysis -XX:+PrintEliminateAllocations -XX:+PrintOptoAssembly -Xlog:gc -XX:+DoPartialEscapeAnalysis"

echo "using" `which java`
java --version

for t in run*.sh
do
    ./$t $JVM_FLAGS > $t.log

    exitcode=$?
    if [ $exitcode -eq 0 ] || [ $exitcode -eq 3 ];  then
        echo -e "[$t]\tpassed."
    else
        echo -e "[$t]\tfailed!"
        exit 1
    fi
done
