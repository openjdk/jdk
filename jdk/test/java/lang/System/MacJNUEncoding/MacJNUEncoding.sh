#!/bin/sh

#
# Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 8003228
# @summary Test the value of sun.jnu.encoding on Mac
# @author Brent Christian
#
# @run shell MacJNUEncoding.sh

# Only run test on Mac
OS=`uname -s`
case "$OS" in
  Darwin )  ;;
  * )
    exit 0
    ;;
esac

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

JAVAC="${TESTJAVA}"/bin/javac
JAVA="${TESTJAVA}"/bin/java

echo "Building test classes..."
"$JAVAC" -d "${TESTCLASSES}" "${TESTSRC}"/ExpectedEncoding.java 

echo ""
echo "Running test for C locale"
export LANG=C
export LC_ALL=C
"${JAVA}" ${TESTVMOPTS} -classpath "${TESTCLASSES}" ExpectedEncoding US-ASCII UTF-8
result1=$?

echo ""
echo "Running test for en_US.UTF-8 locale"
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8
"${JAVA}" ${TESTVMOPTS} -classpath "${TESTCLASSES}" ExpectedEncoding UTF-8 UTF-8
result2=$?

echo ""
echo "Cleanup"
rm ${TESTCLASSES}/ExpectedEncoding.class

if [ ${result1} -ne 0 ] ; then
    echo "Test failed for C locale"
    echo "  LANG=\"${LANG}\""
    echo "  LC_ALL=\"${LC_ALL}\""
    exit ${result1}
fi
if [ ${result2} -ne 0 ] ; then
    echo "Test failed for en_US.UTF-8 locale"
    echo "  LANG=\"${LANG}\""
    echo "  LC_ALL=\"${LC_ALL}\""
    exit ${result2}
fi
exit 0

