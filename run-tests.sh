#!/bin/bash

CONF=${CONF:-linux-riscv64-server-release}

TESTSUITE=test/hotspot/jtreg
NATIVEPATH=$(pwd)/build/${CONF}/images/test/hotspot/jtreg/native

TMPDIR=${TMPDIR:-$(pwd)/build/run-test/tmp}
mkdir -p ${TMPDIR}

ARGS=(
    # JVM arguments
    -Xms64M
    -Xmx1600M
    -Duser.language=en
    -Duser.country=US
    -Djava.library.path="$(pwd)/build/${CONF}/images/test/failure_handler"
    -Dprogram=jtreg
    -jar /workspace/jtreg/build/images/jtreg/lib/jtreg.jar
    # JTreg arguments
    -agentvm
    -verbose:summary,time
    -retain:fail,error
    -concurrency:$(nproc)
    -timeoutFactor:16
    -vmoption:-XX:MaxRAMPercentage=12.5
    -vmoption:-Djava.io.tmpdir="${TMPDIR}"
    -automatic
    -ignore:quiet
    -e:JIB_DATA_DIR
    -e:TEST_IMAGE_DIR=$(pwd)/build/${CONF}/images/test
    -dir:$(pwd)
    -reportDir:$(pwd)/build/run-test/test-results
    -workDir:$(pwd)/build/run-test/test-support
    -testjdk:$(pwd)/build/${CONF}/images/jdk
    $(test -n "${NATIVEPATH}" && echo "-nativepath:${NATIVEPATH}"|| true)
    -exclude:${TESTSUITE}/ProblemList.txt
    -exclude:${TESTSUITE}/ProblemList-GHA.txt
)

build/${CONF}/images/jdk/bin/java ${ARGS[@]} ${*:-${TESTSUITE}}
