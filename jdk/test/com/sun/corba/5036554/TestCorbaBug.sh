#!/bin/sh
#
# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

# @test  
# @bug 5036554 6357706
# @summary unmarshal error on CORBA alias type in CORBA any
# @run shell TestCorbaBug.sh

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
  SunOS | Linux | Darwin | AIX )
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    PS=";"
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

CLASSPATH=.${PS}${TESTCLASSES}; export CLASSPATH

THIS_DIR=`pwd`

${TESTJAVA}${FS}bin${FS}java -version

mkdir bug

cp ${TESTSRC}${FS}bug.idl .
${TESTJAVA}${FS}bin${FS}idlj bug.idl

cp ${TESTSRC}${FS}JavaBug.java bug

chmod -fR 777 bug

${TESTJAVA}${FS}bin${FS}javac -d . bug${FS}*.java

${TESTJAVA}${FS}bin${FS}java -cp . bug/JavaBug > test.out 2>&1 

grep "NullPointerException" test.out

ERROR=$?

cat test.out

if [ $ERROR = 0 ]
then
    echo "Test Failed"
    exit 1
fi

grep "Any: hello" test.out

STATUS=$?

if [ $STATUS = 0 ]
then
    echo "Test Passed"
    exit 0
else
    echo "Invalid output"
    cat test.out
    exit 2
fi
