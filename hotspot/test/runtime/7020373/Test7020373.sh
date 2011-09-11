#!/bin/sh

##
## @test
## @bug 7020373 7055247
## @key cte_test
## @summary JSR rewriting can overflow memory address size variables
## @ignore Ignore it until 7053586 fixed
## @run shell Test7020373.sh
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

BIT_FLAG=""

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    NULL=/dev/null
    PS=":"
    FS="/"
    ## for solaris, linux it's HOME
    FILE_LOCATION=$HOME
    if [ -f ${FILE_LOCATION}${FS}JDK64BIT -a ${OS} = "SunOS" ]
    then
        BIT_FLAG=`cat ${FILE_LOCATION}${FS}JDK64BIT | grep -v '^#'`
    fi
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

${TESTJAVA}${FS}bin${FS}java ${BIT_FLAG} -version

${TESTJAVA}${FS}bin${FS}jar xvf ${TESTSRC}${FS}testcase.jar

${TESTJAVA}${FS}bin${FS}java ${BIT_FLAG} OOMCrashClass4000_1 > test.out 2>&1

cat test.out

egrep "SIGSEGV|An unexpected error has been detected" test.out

if [ $? = 0 ]
then
    echo "Test Failed"
    exit 1
else
    grep "java.lang.LinkageError" test.out
    if [ $? = 0 ]
    then
        echo "Test Passed"
        exit 0
    else
        echo "Test Failed"
        exit 1
    fi
fi
