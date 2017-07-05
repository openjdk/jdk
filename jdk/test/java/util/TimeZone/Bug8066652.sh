#!/bin/sh

# Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8066652
# @requires os.family == "mac"
# @summary tests thread safe native function localtime_r is accessed by multiple threads at same time and
# zone id should not be  “GMT+00:00” if default timezone is “GMT” and user specifies a fake timezone.
# @build Bug8066652
# @run shell/timeout=600 Bug8066652.sh


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


# set system TimeZone to GMT using environment variable TZ
export TZ="GMT"

# Setting invalid TimeZone using VM option
${TESTJAVA}/bin/java -Duser.timezone=Foo/Bar  ${TESTVMOPTS} -cp ${TESTCLASSES}  Bug8066652

status=$?
if [ $status -eq 0 ]
then
  echo "Success, Test Passed";
else
  echo "Test Failed";
fi

exit $status
