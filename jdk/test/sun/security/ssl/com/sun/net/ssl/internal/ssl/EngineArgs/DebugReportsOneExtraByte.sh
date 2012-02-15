#! /bin/sh

#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 7126889
# @summary Incorrect SSLEngine debug output
#
# ${TESTJAVA} is pointing to the JDK under test.
#
# set platform-dependent variables

OS=`uname -s`
case "$OS" in
  SunOS )
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

${TESTJAVA}${FS}bin${FS}javac -d . ${TESTSRC}${FS}DebugReportsOneExtraByte.java

STRING='main, WRITE: TLSv1 Application Data, length = 8'

echo "Examining debug output for the string:"
echo "${STRING}"
echo "========="

${TESTJAVA}${FS}bin${FS}java -Djavax.net.debug=all \
    -Dtest.src=${TESTSRC} \
    DebugReportsOneExtraByte 2>&1 | \
    grep "${STRING}"
RETVAL=$?

echo "========="

if [ ${RETVAL} -ne 0 ]; then
    echo "Did NOT see the expected debug output."
    exit 1
else
    echo "Received the expected debug output."
    exit 0
fi
