#!/bin/sh
# 
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
# 
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
# 
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
# 
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
# 
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
# 
# 

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTSRC=${TESTSRC}"
if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTJAVA=${TESTJAVA}"
if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTCLASSES=${TESTCLASSES}"
echo "CLASSPATH=${CLASSPATH}"

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
  CYGWIN_* )
    NULL=/dev/null
    PS=";"
    FS="/"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac


${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -Xinternalversion | sed 's/amd64/x86/' | grep "x86" | grep "Server VM" | grep "debug"

# Only test fastdebug Server VM on x86
if [ $? != 0 ]
then
    echo "Test Passed"
    exit 0
fi

cp ${TESTSRC}${FS}TestIntVect.java .
${TESTJAVA}${FS}bin${FS}javac -d . TestIntVect.java

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -Xbatch -XX:-TieredCompilation -XX:CICompilerCount=1 -XX:+PrintCompilation -XX:+TraceNewVectors TestIntVect > test.out 2>&1

COUNT=`grep AddVI test.out | wc -l | awk '{print $1}'`
if [ $COUNT -lt 4 ]
then
    echo "Test Failed: AddVI $COUNT < 4"
    exit 1
fi

# AddVI is generated for test_subc
COUNT=`grep SubVI test.out | wc -l | awk '{print $1}'`
if [ $COUNT -lt 4 ]
then
    echo "Test Failed: SubVI $COUNT < 4"
    exit 1
fi

# LShiftVI+SubVI is generated for test_mulc
COUNT=`grep MulVI test.out | wc -l | awk '{print $1}'`
if [ $COUNT -lt 2 ]
then
    echo "Test Failed: MulVI $COUNT < 2"
    exit 1
fi

COUNT=`grep AndV test.out | wc -l | awk '{print $1}'`
if [ $COUNT -lt 3 ]
then
    echo "Test Failed: AndV $COUNT < 3"
    exit 1
fi

COUNT=`grep OrV test.out | wc -l | awk '{print $1}'`
if [ $COUNT -lt 3 ]
then
    echo "Test Failed: OrV $COUNT < 3"
    exit 1
fi

COUNT=`grep XorV test.out | wc -l | awk '{print $1}'`
if [ $COUNT -lt 3 ]
then
    echo "Test Failed: XorV $COUNT < 3"
    exit 1
fi

COUNT=`grep LShiftVI test.out | wc -l | awk '{print $1}'`
if [ $COUNT -lt 5 ]
then
    echo "Test Failed: LShiftVI $COUNT < 5"
    exit 1
fi

# RShiftVI + URShiftVI
COUNT=`grep RShiftVI test.out | wc -l | awk '{print $1}'`
if [ $COUNT -lt 6 ]
then
    echo "Test Failed: RShiftVI $COUNT < 6"
    exit 1
fi

COUNT=`grep URShiftVI test.out | wc -l | awk '{print $1}'`
if [ $COUNT -lt 3 ]
then
    echo "Test Failed: URShiftVI $COUNT < 3"
    exit 1
fi

