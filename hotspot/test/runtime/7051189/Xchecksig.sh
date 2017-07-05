# 
#  Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
#  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
# 
#  This code is free software; you can redistribute it and/or modify it
#  under the terms of the GNU General Public License version 2 only, as
#  published by the Free Software Foundation.
# 
#  This code is distributed in the hope that it will be useful, but WITHOUT
#  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
#  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
#  version 2 for more details (a copy is included in the LICENSE file that
#  accompanied this code).
# 
#  You should have received a copy of the GNU General Public License version
#  2 along with this work; if not, write to the Free Software Foundation,
#  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
# 
#  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
#  or visit www.oracle.com if you need additional information or have any
#  questions.
# 

 
# @test Xchecksig.sh
# @bug 7051189
# @summary Need to suppress info message if -xcheck:jni used with libjsig.so
# @run shell Xchecksig.sh
#

if [ "${TESTSRC}" = "" ]
  then TESTSRC=.
fi

if [ "${TESTJAVA}" = "" ]
then
  PARENT=`dirname \`which java\``
  TESTJAVA=`dirname ${PARENT}`
  printf "TESTJAVA not set, selecting " ${TESTJAVA}
  printf "  If this is incorrect, try setting the variable manually.\n"
fi


OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    FS="/"
    ;;
  Windows_* )
    printf "Not testing libjsig.so on Windows. PASSED.\n "
    exit 0
    ;;
  * )
    printf "Not testing libjsig.so on unrecognised system. PASSED.\n "
    exit 0
    ;;
esac


JAVA=${TESTJAVA}${FS}bin${FS}java

# LD_PRELOAD arch needs to match the binary we run, so run the java
# 64-bit binary directly if we are testing 64-bit (bin/ARCH/java).
# Check if TESTVMOPS contains -d64, but cannot use 
# java ${TESTVMOPS} to run "java -d64"  with LD_PRELOAD.

if [ ${OS} -eq "SunOS" ]
then
  printf  "SunOS test TESTVMOPTS = ${TESTVMOPTS}"
  printf ${TESTVMOPTS} | grep d64 > /dev/null
  if [ $? -eq 0 ]
  then
    printf "SunOS 64-bit test\n"
    BIT_FLAG=-d64
  fi
fi

ARCH=`uname -p`
case $ARCH in
  i386)
    if [ X${BIT_FLAG} != "X" ]
    then
      ARCH=amd64
      JAVA=${TESTJAVA}${FS}bin${FS}${ARCH}${FS}java
    fi
    ;;
  sparc)
    if [ X${BIT_FLAG} != "X" ]
    then
      ARCH=sparcv9
      JAVA=${TESTJAVA}${FS}bin${FS}${ARCH}${FS}java
    fi
    ;;
  * )
    printf "Not testing architecture $ARCH, skipping test.\n"
    exit 0
  ;; 
esac

LIBJSIG=${TESTJAVA}${FS}jre${FS}lib${FS}${ARCH}${FS}libjsig.so

# If libjsig and binary do not match, skip test.

A=`file ${LIBJSIG} | awk '{ print $3 }'`
B=`file ${JAVA}    | awk '{ print $3 }'`

if [ $A -ne $B ]
then
  printf "Mismatching binary and library to preload, skipping test.\n"
  exit 0
fi

if [ ! -f ${LIBJSIG} ]
then
  printf "Skipping test: libjsig missing for given architecture: ${LIBJSIG}\n"
  exit 0
fi
# Use java -version to test, java version info appears on stderr,
# the libjsig message we are removing appears on stdout.

# grep returns zero meaning found, non-zero means not found:

LD_PRELOAD=${LIBJSIG} ${JAVA} ${TESTVMOPTS} -Xcheck:jni -version 2>&1  | grep "libjsig is activated"
if [ $? -eq 0 ]; then
  printf "Failed: -Xcheck:jni prints message when libjsig.so is loaded.\n"
  exit 1
fi


LD_PRELOAD=${LIBJSIG} ${JAVA} ${TESTVMOPTS} -Xcheck:jni -verbose:jni -version 2>&1 | grep "libjsig is activated"
if [ $? != 0 ]; then
  printf "Failed: -Xcheck:jni does not print message when libjsig.so is loaded and -verbose:jni is set.\n"
  exit 1
fi

printf "PASSED\n"
exit 0

