#!/bin/sh

#
# Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
#  @test
#  @run shell/timeout=380 test.sh
#  @bug 6212146
#  @summary URLConnection.connect() fails on JAR Entry it creates file handler leak
#
# set platform-dependent variables

OS=`uname -s`
case "$OS" in
  SunOS | Darwin )
    PS=":"
    FS="/"
    ;;
  Linux )
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    PS=";"
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

if [ -d jars ]; then
    rm -rf jars
fi

mkdir jars

cp ${TESTSRC}${FS}test.jar  jars

${TESTJAVA}${FS}bin${FS}javac -d . ${TESTSRC}${FS}Test.java

WD=`pwd`
ulimit -H -n 300
${TESTJAVA}${FS}bin${FS}java Test ${WD}/jars/ test.jar
result=$?
rm -rf jars
exit $?
