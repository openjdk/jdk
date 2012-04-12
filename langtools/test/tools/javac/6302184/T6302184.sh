#! /bin/sh -f

#
# Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6302184 6350124 6357979
# @summary javac hidden options that generate source should use the given encoding, if available
# @run shell T6302184.sh

TS=${TESTSRC-.}
TC=${TESTCLASSES-.}

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

"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -cp ${TC} -encoding iso-8859-1 -XD-printsource ${TS}${FS}T6302184.java 2>&1
diff ${DIFFOPTS} -c ${TC}${FS}T6302184.java ${TS}${FS}T6302184.out
result=$?


if [ $result -eq 0 ]
then
  echo "Passed"
else
  echo "Failed"
fi
exit $result
