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

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Linux)
    NULL=/dev/null
    PS=":"
    FS="/"
    ;;
  * )
    echo "Test passed; only valid for Linux"
    exit 0;
    ;;
esac

# Choose arch: i386 or amd64 (test is Linux-specific)
# Cannot simply look at TESTVMOPTS as -d64 is not
# passed if there is only a 64-bit JVM available.

${TESTJAVA}/bin/java ${TESTVMOPTS} -version 2>1 | grep "64-Bit" >/dev/null
if [ "$?" = "0" ]
then
  ARCH=amd64
else
  ARCH=i386
fi

LD_LIBRARY_PATH=.:${TESTJAVA}/jre/lib/${ARCH}/client:/usr/openwin/lib:/usr/dt/lib:/usr/lib:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

THIS_DIR=`pwd`

cp ${TESTSRC}${FS}invoke.c ${THIS_DIR}
cp ${TESTSRC}${FS}T.java ${THIS_DIR}


${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -fullversion

${TESTJAVA}${FS}bin${FS}javac T.java

gcc -o invoke -I${TESTJAVA}/include -I${TESTJAVA}/include/linux invoke.c ${TESTJAVA}/jre/lib/${ARCH}/client/libjvm.so
./invoke
exit $?
