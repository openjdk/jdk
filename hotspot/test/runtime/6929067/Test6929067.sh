#!/bin/sh

##
## @test Test6929067.sh
## @bug 6929067
## @summary Stack guard pages should be removed when thread is detached
## @compile T.java
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

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -Xinternalversion > vm_version.out 2>&1 

# Bitness:
# Cannot simply look at TESTVMOPTS as -d64 is not
# passed if there is only a 64-bit JVM available.

grep "64-Bit" vm_version.out > ${NULL}
if [ "$?" = "0" ]
then
  COMP_FLAG="-m64"
else
  COMP_FLAG="-m32"
fi


# Architecture:
# Translate uname output to JVM directory name, but permit testing
# 32-bit x86 on an x64 platform.
ARCH=`uname -m`
case "$ARCH" in
  x86_64)
    if [ "$COMP_FLAG" = "-m32" ]; then
      ARCH=i386
    else 
      ARCH=amd64
    fi
    ;;
  ppc64)
    if [ "$COMP_FLAG" = "-m32" ]; then
      ARCH=ppc
    else 
      ARCH=ppc64
    fi
    ;;
  sparc64)
    if [ "$COMP_FLAG" = "-m32" ]; then
      ARCH=sparc
    else 
      ARCH=sparc64
    fi
    ;;
  arm*)
    # 32-bit ARM machine: compiler may not recognise -m32
    COMP_FLAG=""
    ARCH=arm
    ;;
  aarch64)
    # 64-bit arm machine, could be testing 32 or 64-bit:
    if [ "$COMP_FLAG" = "-m32" ]; then
      ARCH=arm
    else 
      ARCH=aarch64
    fi
    ;;
  i586)
    ARCH=i386
    ;;
  i686)
    ARCH=i386
    ;;
  # Assuming other ARCH values need no translation
esac


# VM type: need to know server or client
VMTYPE=client
grep Server vm_version.out > ${NULL}
if [ "$?" = "0" ]
then
  VMTYPE=server
fi


LD_LIBRARY_PATH=.:${TESTJAVA}/jre/lib/${ARCH}/${VMTYPE}:/usr/lib:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

cp ${TESTSRC}${FS}invoke.c .

# Copy the result of our @compile action:
cp ${TESTCLASSES}${FS}T.class .

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -fullversion

echo "Architecture: ${ARCH}"
echo "Compilation flag: ${COMP_FLAG}"
echo "VM type: ${VMTYPE}"

gcc -DLINUX ${COMP_FLAG} -o invoke \
  -I${TESTJAVA}/include -I${TESTJAVA}/include/linux \
  -L${TESTJAVA}/jre/lib/${ARCH}/${VMTYPE} \
  -ljvm -lpthread invoke.c

./invoke
exit $?
