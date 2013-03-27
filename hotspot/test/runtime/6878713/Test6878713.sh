#!/bin/sh

##
## @test
## @bug 6878713
## @summary Verifier heap corruption, relating to backward jsrs
## @run shell/timeout=120 Test6878713.sh
##
## some tests require path to find test source dir
if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

${COMPILEJAVA}${FS}bin${FS}jar xvf ${TESTSRC}${FS}testcase.jar

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} OOMCrashClass1960_2 > test.out 2>&1

if [ -s core -o -s "hs_*.log" ]
then
    cat hs*.log
    echo "Test Failed"
    exit 1
else
    echo "Test Passed"
    exit 0
fi
