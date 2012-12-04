#!/bin/sh

# Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6332666 7180362 8003846
# @summary tests the capability of replacing the currency data with user
#     specified currency properties file
# @build PropertiesTest
# @run shell/timeout=600 PropertiesTest.sh

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
  SunOS | Linux | Darwin )
    PS=":"
    FS="/"
    ;;
  Windows* | CYGWIN* )
    PS=";"
    FS="/"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

failures=0

run() {
    echo ''
    sh -xc "${TESTJAVA}${FS}bin${FS}java -cp ${TESTCLASSES} $*" 2>&1
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}

PROPS=${TESTSRC}${FS}currency.properties


# Dump built-in currency data

run PropertiesTest -d dump1


# Dump built-in currency data + overrides in properties file specified
# by system property.

run -Djava.util.currency.data=${PROPS} PropertiesTest -d dump2
run PropertiesTest -c dump1 dump2 ${PROPS}


# Dump built-in currency data + overrides in properties file copied into
# JRE image.

# copy the test properties file
COPIED=0
if [ -w $TESTJAVA ]
then
  WRITABLEJDK=$TESTJAVA
else
  WRITABLEJDK=.${FS}testjava
  cp -r $TESTJAVA $WRITABLEJDK
  COPIED=1
fi

if [ -d ${WRITABLEJDK}${FS}jre ]
then
  PROPLOCATION=${WRITABLEJDK}${FS}jre${FS}lib
else
  PROPLOCATION=${WRITABLEJDK}${FS}lib
fi
cp ${PROPS} $PROPLOCATION

# run
echo ''
sh -xc "${WRITABLEJDK}${FS}bin${FS}java -cp ${TESTCLASSES} PropertiesTest -d dump3"
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# Cleanup
rm -f ${PROPLOCATION}${FS}currency.properties
if [ $COPIED -eq 1 ]
then
  rm -rf $WRITABLEJDK
fi

# compare the two dump files
run PropertiesTest -c dump1 dump3 ${PROPS}


# Results
echo ''
if [ $failures -gt 0 ];
  then echo "$failures tests failed";
  else echo "All tests passed"; fi
exit $failures
