#!/bin/sh

#
# Copyright (c) 1999, 2016, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4170614
# @summary Test internal hashCode() functions
#

set -x
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
if [ "${COMPILEJAVA}" = "" ]; then
   COMPILEJAVA="${TESTJAVA}"
fi
echo "COMPILEJAVA=${COMPILEJAVA}"
if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTCLASSES=${TESTCLASSES}"

goback=`pwd`

cd ${TESTSRC}

TEST_JAVABASE=${TESTCLASSES}/java.base
mkdir -p ${TEST_JAVABASE}
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
    -Xmodule:java.base \
    -d ${TEST_JAVABASE} Bug4170614Test.java

${TESTJAVA}/bin/java ${TESTVMOPTS} -Xpatch:java.base=${TEST_JAVABASE} java.text.Bug4170614Test

result=$?

cd ${goback}

if [ $result -eq 0 ]
then
  echo "Passed"
else
  echo "Failed"
fi
exit $result



