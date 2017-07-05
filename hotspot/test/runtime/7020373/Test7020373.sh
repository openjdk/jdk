#!/bin/sh

##
## @test
## @bug 7020373 7055247 7053586 7185550
## @key cte_test
## @summary JSR rewriting can overflow memory address size variables
## @ignore Ignore it as 7053586 test uses lots of memory. See bug report for detail.
## @run shell Test7020373.sh
##

if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

${COMPILEJAVA}${FS}bin${FS}jar xvf ${TESTSRC}${FS}testcase.jar

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} OOMCrashClass4000_1 > test.out 2>&1

cat test.out

egrep "SIGSEGV|An unexpected error has been detected" test.out

if [ $? = 0 ]
then
    echo "Test Failed"
    exit 1
else
    egrep "java.lang.LinkageError|java.lang.NoSuchMethodError|Main method not found in class OOMCrashClass4000_1|insufficient memory" test.out
    if [ $? = 0 ]
    then
        echo "Test Passed"
        exit 0
    else
        echo "Test Failed"
        exit 1
    fi
fi
