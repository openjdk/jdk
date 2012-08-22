#!/bin/sh

##
## @test
## @bug 6878713
## @summary Verifier heap corruption, relating to backward jsrs
## @run shell/timeout=120 Test6878713.sh
##

if [ "${TESTSRC}" = "" ]
then TESTSRC=.
fi

if [ "${TESTJAVA}" = "" ]
then
  PARENT=`dirname \`which java\``
  TESTJAVA=`dirname ${PARENT}`
  echo "TESTJAVA not set, selecting " ${TESTJAVA}
  echo "If this is incorrect, try setting the variable manually."
fi

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    NULL=/dev/null
    PS=":"
    FS="/"
    ;;
  Windows_* )
    NULL=NUL
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

JEMMYPATH=${CPAPPEND}
CLASSPATH=.${PS}${TESTCLASSES}${PS}${JEMMYPATH} ; export CLASSPATH

THIS_DIR=`pwd`

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -version

${TESTJAVA}${FS}bin${FS}jar xvf ${TESTSRC}${FS}testcase.jar

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
