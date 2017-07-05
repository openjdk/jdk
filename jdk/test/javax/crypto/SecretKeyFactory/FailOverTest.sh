#
# Copyright (c) 2006, 2007, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6370923
# @summary SecretKeyFactory failover does not work
# @author Brad R. Wetmore
#

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTJAVA=${TESTJAVA}"

if [ "${TESTSRC}" = "" ]
then
    TESTSRC="."
fi
echo "TESTSRC=${TESTSRC}"


if [ "${TESTCLASSES}" = "" ]
then
    TESTCLASSES="."
fi
echo "TESTCLASSES=${TESTCLASSES}"

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    NULL=/dev/null
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    NULL=/dev/null
    PS=";"
    FS="/"
    ;;
  Windows* )
    NULL=NUL
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

${TESTJAVA}${FS}bin${FS}javac \
    -d . \
    -classpath "${TESTSRC}${FS}P1.jar${PS}${TESTSRC}${FS}P2.jar" \
    ${TESTSRC}${FS}FailOverTest.java

if [ $? -ne 0 ]; then
    exit 1
fi

${TESTJAVA}${FS}bin${FS}java \
    -classpath "${TESTSRC}${FS}P1.jar${PS}${TESTSRC}${FS}P2.jar${PS}." \
    FailOverTest
result=$?

if [ $result -eq 0 ]
then
  echo "Passed"
else
  echo "Failed"
fi
exit $result
