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


if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi
printf '%s' "TESTSRC=${TESTSRC}" ; echo
if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi
printf '%s' "TESTJAVA=${TESTJAVA}" ; echo
if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi
printf '%s' "TESTCLASSES=${TESTCLASSES}" ; echo
printf '%s' "CLASSPATH=${CLASSPATH}" ; echo
echo

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
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

TMP1=OUTPUT.txt

cp "${TESTSRC}${FS}NoJavaLang.java" .

echo "- verifing that fatal error is not produced in the regular case" 
"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} NoJavaLang.java 2> "${TMP1}"
result=$?

if [ $result -eq 0 ]
then
  echo "Passed - base compilation successful"
else
  echo "Failed - unable to compile test"
  exit $result
fi

echo

echo "- verifing the fatal error is produced"
rm "${TMP1}"
"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -bootclasspath . NoJavaLang.java 2> "${TMP1}"

# return code should be EXIT_SYSERR
result=$?
if [ $result -ne 3 ]
then
  echo "Failed - unexpected return code"
  exit $result
else
  echo "Passed - expected return code"
fi

# expected message
cat "${TMP1}"
diff ${DIFFOPTS} -c "${TESTSRC}${FS}NoJavaLang.out" "${TMP1}"
result=$?
rm "${TMP1}"

if [ $result -eq 0 ]
then
  echo "Passed - expected message"
else
  echo "Failed - unexpected message"
  exit $result

fi

exit
