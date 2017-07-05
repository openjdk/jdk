#!/bin/sh

#
# Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4627316 6743526
# @summary Test option to limit direct memory allocation
#
# @requires (os.arch == "x86_64") | (os.arch == "amd64") | (os.arch == "sparcv9")
# @build LimitDirectMemory
# @run shell LimitDirectMemory.sh

TMP1=tmp_$$

runTest() {
  echo "Testing: $*"
  ${TESTJAVA}/bin/java ${TESTVMOPTS} $*
  if [ $? -eq 0 ]
  then echo "--- passed as expected"
  else
    echo "--- failed"
    exit 1
  fi
}


launchFail() {
  echo "Testing: -XX:MaxDirectMemorySize=$* -cp ${TESTCLASSES} \
     LimitDirectMemory true DEFAULT DEFAULT+1M"
  ${TESTJAVA}/bin/java ${TESTVMOPTS} -XX:MaxDirectMemorySize=$* -cp ${TESTCLASSES} \
     LimitDirectMemory true DEFAULT DEFAULT+1M > ${TMP1} 2>&1
  cat ${TMP1}
  cat ${TMP1} | grep -s "Unrecognized VM option: \'MaxDirectMemorySize="
  if [ $? -ne 0 ]
    then echo "--- failed as expected"
  else
    echo "--- failed"
    exit 1
  fi
}

# $java LimitDirectMemory throwp fill_direct_memory size_per_buffer

# Memory is properly limited using multiple buffers.
runTest -XX:MaxDirectMemorySize=10 -cp ${TESTCLASSES} LimitDirectMemory true 10 1
runTest -XX:MaxDirectMemorySize=1k -cp ${TESTCLASSES} LimitDirectMemory true 1k 100
runTest -XX:MaxDirectMemorySize=10m -cp ${TESTCLASSES} LimitDirectMemory true 10m 10m

# We can increase the amount of available memory.
runTest -XX:MaxDirectMemorySize=65M -cp ${TESTCLASSES} \
  LimitDirectMemory false 64M 65M

# Exactly the default amount of memory is available.
runTest -cp ${TESTCLASSES} LimitDirectMemory false 10 1
runTest -Xmx64m -cp ${TESTCLASSES} LimitDirectMemory false 0 DEFAULT
runTest -Xmx64m -cp ${TESTCLASSES} LimitDirectMemory true 0 DEFAULT+1

# We should be able to eliminate direct memory allocation entirely.
runTest -XX:MaxDirectMemorySize=0 -cp ${TESTCLASSES} LimitDirectMemory true 0 1

# Setting the system property should not work so we should be able to allocate
# the default amount.
runTest -Dsun.nio.MaxDirectMemorySize=1K -Xmx64m -cp ${TESTCLASSES} \
  LimitDirectMemory false DEFAULT-1 DEFAULT/2

# Various bad values fail to launch the VM.
launchFail foo
launchFail 10kmt
launchFail -1

# Clean-up
rm ${TMP1}
