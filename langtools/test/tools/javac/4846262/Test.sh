#!/bin/sh -f

#
# Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4846262
# @summary check that javac operates correctly in EBCDIC locale


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

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    FS="/"
    ;;
  CYGWIN* )
    FS="/"
    DIFFOPTS="--strip-trailing-cr"
    ;;
  Windows* )
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

rm -f Test.java Test.out

"${TESTJAVA}${FS}bin${FS}native2ascii" ${TESTTOOLVMOPTS} -reverse -encoding IBM1047 ${TESTSRC}${FS}Test.java Test.java

"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -J-Duser.language=en -J-Duser.region=US -J-Dfile.encoding=IBM1047 Test.java 2>Test.tmp

"${TESTJAVA}${FS}bin${FS}native2ascii" ${TESTTOOLVMOPTS} -encoding IBM1047 Test.tmp Test.out

diff ${DIFFOPTS} -c "${TESTSRC}${FS}Test.out" Test.out
result=$?

if [ $result -eq 0 ]
then
  echo "Passed"
else
  echo "Failed"
fi
exit $result
