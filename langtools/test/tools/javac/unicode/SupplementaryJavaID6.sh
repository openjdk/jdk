#!/bin/sh

#
# Copyright 2004-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#
# @test
# @bug 4914724 4973116 5014511
# @summary Ensure that a supplementary character can be used as part/whole of a class
#          name on platforms that have Unicode aware filesystems.
# @author Naoto Sato
# @run shell SupplementaryJavaID6.sh SupplementaryJavaID6


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
    if [ -d /usr/lib/locale/en_US.UTF-8 -o -d /usr/lib/locale/en_US.utf8 ]
    then
        ENV="env LANG=en_US.UTF-8"
        PS=":"
        FS="/"
    else
        echo "As there is no en_US.UTF-8 locale available on this system, the compilation of the test case may or may not fail.  Ignoring this test."
        exit 0;
    fi
    ;;
  Windows_98 | Windows_ME )
    echo "As Windows 98/Me do not provide Unicode-aware file system, the compilation of the test case is expected to fail on these platforms.  Ignoring this test."
    exit 0;
    ;;
  Windows* )
    ENV=""
    PS=";"
    FS="\\"
    ;;
  CYGWIN* )
    ENV=""
    PS=";" # platform PS, not cygwin PS
    FS="/"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# compile
cp "${TESTSRC}${FS}$1.java" .
${ENV} "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d . -classpath ".${PS}${TESTSRC}" $1.java
result=$?

if [ $result -ne 0 ]
then
  echo "Failed"
  exit $result
fi

# run
${ENV} "${TESTJAVA}${FS}bin${FS}java" ${TESTVMOPTS} $1 
result=$?

if [ $result -eq 0 ]
then
  echo "Passed"
else
  echo "Failed"
fi

# Cleanup
${ENV} rm -f ./$1*.class

exit $result
