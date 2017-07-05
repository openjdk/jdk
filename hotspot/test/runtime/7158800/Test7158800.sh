#!/bin/sh
# 
#  Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#
#   Run test for InternTest.java
#

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
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

JEMMYPATH=${CPAPPEND}
CLASSPATH=.${PS}${TESTCLASSES}${PS}${JEMMYPATH} ; export CLASSPATH

THIS_DIR=`pwd`

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -fullversion

${TESTJAVA}${FS}bin${FS}javac -d . ${TESTSRC}${FS}InternTest.java

cp ${TESTSRC}${FS}badstrings.txt .

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -XX:+PrintStringTableStatistics -XX:+TraceSafepointCleanupTime InternTest bad > test.out 2>&1 &
C_PID=$!

sleep 60

ps | grep ${C_PID} | grep -v grep

if [ $? = 0 ]
then
    kill -9 ${C_PID}
    echo "Test Failed"
    exit 1
else
    echo "Test Passed"
    exit 0
fi
