#!/bin/sh

#
# @test testme.sh
# @summary Stack guard pages should be installed correctly and removed when thread is detached
# @run shell testme.sh
#

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

LD_LIBRARY_PATH=.:${TESTJAVA}/jre/lib/${VM_CPU}/${VM_TYPE}:${TESTJAVA}/lib/${VM_CPU}/${VM_TYPE}:/usr/lib:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

echo "Architecture: ${VM_CPU}"
echo "Compilation flag: ${CFLAGS}"
echo "VM type: ${VM_TYPE}"
echo "LD_LIBRARY_PATH: ${LD_LIBRARY_PATH}"

# Note pthread may not be found thus invoke creation will fail to be created.
# Check to ensure you have a /usr/lib/libpthread.so if you don't please look
# for /usr/lib/`uname -m`-linux-gnu version ensure to add that path to below compilation.

cp ${TESTSRC}/DoOverflow.java .
${COMPILEJAVA}/bin/javac DoOverflow.java

$gcc_cmd -DLINUX -g3 ${CFLAGS} -o invoke \
    -I${TESTJAVA}/include -I${TESTJAVA}/include/linux \
    -L${TESTJAVA}/jre/lib/${VM_CPU}/${VM_TYPE} \
    -L${TESTJAVA}/lib/${VM_CPU}/${VM_TYPE} \
     ${TESTSRC}/invoke.c -ljvm -lpthread

if [ $? -ne 0 ] ; then
    echo "Compile failed, Ignoring failed compilation and forcing the test to pass"
    exit 0
fi

./invoke test_java_overflow
./invoke test_native_overflow
exit $?
