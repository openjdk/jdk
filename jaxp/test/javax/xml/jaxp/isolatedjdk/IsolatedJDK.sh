#!/bin/sh

# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

if [ $# = 0 ]; then
  echo "The suffix of ISOLATED_JDK is mandatory"
  exit 1
fi

checkVariable() {
  variable='$'$1

  if [ "${variable}" = "" ]; then
    echo "Failed due to $1 is not set."
    exit 1
  fi
}

checkVariables() {
  for variable in $*
  do
    checkVariable ${variable}
  done
}

# Check essential variables
checkVariables TESTJAVA TESTSRC TESTCLASSES TESTCLASSPATH

echo "TESTJAVA=${TESTJAVA}"
echo "TESTSRC=${TESTSRC}"
echo "TESTCLASSES=${TESTCLASSES}"
echo "TESTCLASSPATH=${TESTCLASSPATH}"

# Make an isolated copy of the testing JDK
ISOLATED_JDK="./ISOLATED_JDK_$1"
echo "ISOLATED_JDK=${ISOLATED_JDK}"

echo "Copy testing JDK started"
cp -H -R ${TESTJAVA} ${ISOLATED_JDK} || exit 1
chmod -R +w ${ISOLATED_JDK} || exit 1
echo "Copy testing JDK ended"

