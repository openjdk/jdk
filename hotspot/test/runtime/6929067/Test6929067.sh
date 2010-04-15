#!/bin/sh

##
## @test Test6929067.sh
## @bug 6929067
## @summary Stack guard pages should be removed when thread is detached
## @run shell Test6929067.sh
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

BIT_FLAG=""

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Linux)
    NULL=/dev/null
    PS=":"
    FS="/"
    ;;
  SunOS | Windows_* )
    NULL=NUL
    PS=";"
    FS="\\"
    echo "Test passed; only valid for Linux"
    exit 0;
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

LD_LIBRARY_PATH=.:${TESTJAVA}/jre/lib/i386/client:/usr/openwin/lib:/usr/dt/lib:/usr/lib:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

THIS_DIR=`pwd`

cp ${TESTSRC}${FS}invoke.c ${THIS_DIR}
cp ${TESTSRC}${FS}T.java ${THIS_DIR}


${TESTJAVA}${FS}bin${FS}java ${BIT_FLAG} -fullversion

${TESTJAVA}${FS}bin${FS}javac T.java

gcc -o invoke -I${TESTJAVA}/include -I${TESTJAVA}/include/linux invoke.c ${TESTJAVA}/jre/lib/i386/client/libjvm.so
./invoke
exit $?
