#!/bin/sh
# 
# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8013496
# @summary Test checks that the order in which ReversedCodeCacheSize and 
#          InitialCodeCacheSize are passed to the VM is irrelevant.  
# @run shell Test8013496.sh
#
#
## some tests require path to find test source dir
if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh
set -x

${TESTJAVA}/bin/java ${TESTVMOPTS} -XX:ReservedCodeCacheSize=2m -XX:InitialCodeCacheSize=500K -version > 1.out 2>&1
${TESTJAVA}/bin/java ${TESTVMOPTS} -XX:InitialCodeCacheSize=500K -XX:ReservedCodeCacheSize=2m -version > 2.out 2>&1

diff 1.out 2.out

result=$?
if [ $result -eq 0 ] ; then  
  echo "Test Passed"
  exit 0
else
  echo "Test Failed"
  exit 1
fi
