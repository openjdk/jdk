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
    if [ -f ${FILE_LOCATION}${FS}JDK64BIT -a ${OS} = "SunOS" -a `uname -p`='sparc' ]
    then
        BIT_FLAG="-d64"
    fi
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

${TESTJAVA}${FS}bin${FS}java ${BIT_FLAG} -fullversion

${TESTJAVA}${FS}bin${FS}javac -classpath .${PS}$TESTJAVA${FS}lib${FS}tools.jar *.java

${TESTJAVA}${FS}bin${FS}java ${BIT_FLAG} -classpath .${PS}$TESTJAVA${FS}lib${FS}tools.jar FieldMonitor > test.out 2>&1 &

P_PID=$!

sleep 60
STATUS=0

case "$OS" in
    SunOS | Linux )
        ps -ef | grep $P_PID | grep -v grep > ${NULL}
        if [ $? = 0 ]; then
            kill -9 $P_PID
            STATUS=1
        fi
        ;;
      * )
        ps | grep -i "FieldMonitor" | grep -v grep > ${NULL}
        if [ $? = 0 ]; then
            C_PID=`ps | grep -i "FieldMonitor" | awk '{print $1}'`
            kill -s 9 $C_PID
            STATUS=1
        fi
        ;;
esac

grep "A fatal error has been detected" test.out > ${NULL}
if [ $? = 0 ]; then
    cat test.out
    STATUS=1
fi

exit $STATUS
