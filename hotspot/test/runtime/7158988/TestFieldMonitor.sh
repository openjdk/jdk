#!/bin/sh

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
  SunOS | Linux | Darwin)
    NULL=/dev/null
    PS=":"
    FS="/"
    ;;
  Windows_95 | Windows_98 | Windows_ME )
    NULL=NUL
    PS=";"
    FS="\\"
    echo "Test skipped, only for WinNT"
    exit 0
    ;;
  Windows_NT )
    NULL=NUL
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

#CLASSPATH=.${PS}${TESTCLASSES} ; export CLASSPATH

cp ${TESTSRC}${FS}*.java .

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -fullversion

${TESTJAVA}${FS}bin${FS}javac -classpath .${PS}$TESTJAVA${FS}lib${FS}tools.jar *.java

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -classpath .${PS}$TESTJAVA${FS}lib${FS}tools.jar FieldMonitor > test.out

grep "A fatal error has been detected" test.out > ${NULL}
if [ $? = 0 ]; then
    cat test.out
    STATUS=1
fi

exit $STATUS
