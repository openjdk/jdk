#!/bin/sh

#
# Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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
echo "CLASSPATH=${CLASSPATH}"

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | CYGWIN* )
    FS="/"
    ;;
  Windows_95 | Windows_98 | Windows_NT )
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

TMP1=T4955930.javap

cp "${TESTSRC}${FS}T4955930.java" .
"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -source 6 -target 6 T4955930.java
result=$?
if [ $result -ne 0 ]
then
    exit $result
fi

"${TESTJAVA}${FS}bin${FS}javap" ${TESTTOOLVMOPTS} -verbose T4955930 > ${TMP1}
grep "StackMapTable: number_of_entries = 2" ${TMP1}
result=$?

if [ $result -eq 0 ]
then
  echo "Passed"
else
  echo "Failed"
fi
exit $result
