#!/bin/sh

#
# Copyright 2003-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# @test	
#
# @bug 4786406 4781221 4780341 6214324

# Validates rewritten javah handling of class defined constants
# and ensures that the appropriate macro definitions are placed
# in the generated header file.

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

EXPECTED_JAVAH_OUT_FILE=SubClassConsts.out

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    PS=":"
    FS="/"
    DIFFOPTS="--strip-trailing-cr"
    EXPECTED_JAVAH_OUT_FILE=SubClassConsts.win
    ;;
  Windows* )
    PS=";"
    FS="\\"
    EXPECTED_JAVAH_OUT_FILE=SubClassConsts.win
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
 esac

GENERATED_HEADER_FILE=SubClassConsts.h
HEADER_FILE_FILTERED=SubClassConsts.h.linefeed-filtered

rm -rf SuperClassConsts.class SubClassConsts.class

cp "${TESTSRC}${FS}SuperClassConsts.java" .
cp "${TESTSRC}${FS}SubClassConsts.java" .

"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d . "${TESTSRC}${FS}SubClassConsts.java"

"${TESTJAVA}${FS}bin${FS}javah" ${TESTTOOLVMOPTS} SubClassConsts

diff ${DIFFOPTS} "${TESTSRC}${FS}${EXPECTED_JAVAH_OUT_FILE}" "${GENERATED_HEADER_FILE}"
result=$?
rm ${GENERATED_HEADER_FILE}

if [ $result -eq 0 ]
then
  echo "Passed"
else
  echo "Failed"
fi
exit $result
