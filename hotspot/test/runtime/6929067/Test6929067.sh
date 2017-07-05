#!/bin/sh

##
## @test Test6929067.sh
## @bug 6929067
## @bug 8021296
## @bug 8025519
## @summary Stack guard pages should be removed when thread is detached
## @run shell Test6929067.sh
##

if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

if [ "${VM_OS}" != "linux" ]
then
  echo "Test only valid for Linux"
  exit 0
fi

gcc_cmd=`which gcc`
if [ "x$gcc_cmd" = "x" ]; then
  echo "WARNING: gcc not found. Cannot execute test." 2>&1
  exit 0;
fi

CFLAGS=-m${VM_BITS}

LD_LIBRARY_PATH=.:${TESTJAVA}/jre/lib/${VM_CPU}/${VM_TYPE}:/usr/lib:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

cp ${TESTSRC}/*.java ${THIS_DIR}
${COMPILEJAVA}/bin/javac *.java

echo "Architecture: ${VM_CPU}"
echo "Compilation flag: ${CFLAGS}"
echo "VM type: ${VM_TYPE}"
echo "LD_LIBRARY_PATH: ${LD_LIBRARY_PATH}"

# Note pthread may not be found thus invoke creation will fail to be created.
# Check to ensure you have a /usr/lib/libpthread.so if you don't please look
# for /usr/lib/`uname -m`-linux-gnu version ensure to add that path to below compilation.

$gcc_cmd -DLINUX ${CFLAGS} -o invoke \
    -I${TESTJAVA}/include -I${TESTJAVA}/include/linux \
    -L${TESTJAVA}/jre/lib/${VM_CPU}/${VM_TYPE} \
     ${TESTSRC}/invoke.c -ljvm -lpthread

./invoke
exit $?
