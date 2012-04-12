#!/bin/sh

#
# Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4241229 4785453
# @summary Test -classpath option and classpath defaults.
# @author maddox
#
# @run shell/timeout=180 ClassPathTest.sh

# TODO: Should test sourcepath and classpath separately.

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
  SunOS | Linux | Darwin | CYGWIN* )
    FS="/"
    ;;
  Windows* )
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

javac="${TESTJAVA}${FS}bin${FS}javac"

cleanup() {
	rm -f *.class pkg${FS}*.class foo${FS}pkg${FS}*.class bar${FS}pkg${FS}*.class
	cp -rf $TESTSRC${FS}* .
}

fail() {
	echo "FAIL: $1"
	failed="yes"
}

# report expectedResult $?
report() {
	if   test "$1" = "success" -a "$2" = 0; then
		echo "PASS: succeeded as expected"
	elif test "$1" = "failure" -a "$2" != 0; then
		echo "PASS: failed as expected"
	elif test "$1" = "success" -a "$2" != 0; then
		fail "test failed unexpectedly"
	elif test "$1" = "failure" -a "$2" = 0; then
		fail "test succeeded unexpectedly"
	else
		fail "internal error"
	fi
}

# testJavac expectedResult javacArgs...
testJavac() {
	expectedResult="$1"; shift
	cleanup
	echo $javac ${TESTTOOLVMOPTS} "$@"
	"$javac" ${TESTTOOLVMOPTS} "$@"
	report $expectedResult $?
}

unset CLASSPATH

# classpath should default to current directory

testJavac success ClassPathTest3.java
testJavac failure ClassPathTest1.java

# if CLASSPATH is set, it should be honored

CLASSPATH=bar; export CLASSPATH

testJavac success ClassPathTest2.java
testJavac failure ClassPathTest1.java
testJavac failure ClassPathTest3.java

# -classpath option should override default

testJavac success -classpath foo ClassPathTest1.java
testJavac failure -classpath foo ClassPathTest2.java
testJavac failure -classpath foo ClassPathTest3.java

if test -n "$failed"; then
	echo "Some tests failed"
	exit 1
else
	echo PASS: all tests gave expected results
	exit 0
fi
