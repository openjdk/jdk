#!/bin/sh
#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @test
# @bug 4700857 6997928 7079486
# @summary tests for Locale.getDefault(Locale.Category) and
#    Locale.setDefault(Locale.Category, Locale)
# @library /java/text/testlib
# @build LocaleCategory TestUtils
# @run shell/timeout=600 LocaleCategory.sh

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
echo "TESTCLASSPATH=${TESTCLASSPATH}"
echo "CLASSPATH=${CLASSPATH}"

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | *BSD | Darwin | AIX )
    PS=":"
    FS="/"
    ;;
  Windows* | CYGWIN* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# test user.xxx.display user.xxx.format properties

# run
RUNCMD="${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -classpath ${TESTCLASSPATH} -Duser.language.display=ja -Duser.language.format=zh LocaleCategory"

echo ${RUNCMD}
${RUNCMD}
result=$?

if [ $result -eq 0 ]
then
  echo "Execution successful"
else
  echo "Execution of the test case failed."
fi

# test user.xxx properties overriding user.xxx.display/format

# run
RUNCMD="${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -classpath ${TESTCLASSPATH} -Duser.language=en -Duser.language.display=ja -Duser.language.format=zh LocaleCategory"

echo ${RUNCMD}
${RUNCMD}
result=$?

if [ $result -eq 0 ]
then
  echo "Execution successful"
else
  echo "Execution of the test case failed."
fi

exit $result
