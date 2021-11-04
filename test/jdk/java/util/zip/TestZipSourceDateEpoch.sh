#!/bin/sh

# Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8276400 
# @summary Test ZipOutputStream support for SOURCE_DATE_EPOCH 
# @build ZipSourceDateEpoch 
# @run shell/timeout=600 TestZipSourceDateEpoch.sh

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
  Linux | Darwin | AIX )
    PS=":"
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="/"
    ;;
  CYGWIN* )
    PS=";"
    FS="/"
    TESTJAVA=`cygpath -u ${TESTJAVA}`
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

failures=0

run() {
    echo ''
    ${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -cp ${TESTCLASSES} $* 2>&1
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}

# Run with source date epoch set to 15/03/2022 
export SOURCE_DATE_EPOCH=1647302400
run ZipSourceDateEpoch 

# Run with no source date epoch set 
unset SOURCE_DATE_EPOCH
run ZipSourceDateEpoch

# Results
echo ''
if [ $failures -gt 0 ];
  then echo "$failures tests failed";
  else echo "All tests passed"; fi
exit $failures

