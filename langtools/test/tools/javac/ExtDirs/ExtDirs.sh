#!/bin/sh

#
# Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4204897 4256097 4785453 4863609
# @summary Test that '.jar' files in -extdirs are found.
# @author maddox
#
# @run shell/timeout=180 ExtDirs.sh

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
  SunOS | Linux )
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    PS=";" # native PS, not Cygwin PS
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

fail() {
	echo 'FAIL: unexpected result encountered'
        exit 1
}

javac="${TESTJAVA}${FS}bin${FS}javac"

for i in 1 2 3; do
    if test ! -d ext${i}; then mkdir ext${i}; fi
    cp ${TESTSRC}${FS}ext${i}${FS}*.jar ext${i}
done

echo "Test 1"
$javac ${TESTTOOLVMOPTS} -d . -extdirs ext1 "${TESTSRC}${FS}ExtDirTest_1.java"
if [ $? -ne 0 ] ; then fail ; fi

echo "Test 2"
$javac ${TESTTOOLVMOPTS} -d . -extdirs ext1${PS}ext2 "${TESTSRC}${FS}ExtDirTest_2.java"
if [ $? -ne 0 ] ; then fail ; fi

echo "Test 3"
$javac ${TESTTOOLVMOPTS} -d . -extdirs ext3 "${TESTSRC}${FS}ExtDirTest_3.java"
if [ $? -ne 0 ] ; then fail ; fi

echo PASS: all tests gave expected results
exit 0
