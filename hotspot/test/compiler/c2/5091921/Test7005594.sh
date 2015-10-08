#!/bin/sh
# 
# Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
## some tests require path to find test source dir
if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../../test_env.sh

# Amount of physical memory in megabytes
MEM=0
if [ -f "/proc/meminfo" ]; then
  # Linux, Windows/Cygwin
  MEM=`cat /proc/meminfo |grep ^MemTotal: | awk '{print $2}'`
  MEM="$(($MEM / 1024))"
elif [ -x "/usr/sbin/prtconf" ]; then
  # Solaris
  MEM=`/usr/sbin/prtconf | grep "^Memory size" | awk '{print $3}'`
elif [ -x "/usr/sbin/system_profiler" ]; then
  # MacOS
  MEMo=`/usr/sbin/system_profiler SPHardwareDataType | grep Memory:`
  MEM=`echo "$MEMo" | awk '{print $2}'`
  MEMu=`echo "$MEMo" | awk '{print $3}'`
  case $MEMu in
  GB)
    MEM="$(($MEM * 1024))"
    ;;
  MB)
    ;;
  *)
    echo "Unknown memory unit in system_profile output: $MEMu"
    ;;
  esac
elif [ -n "$ROOTDIR" -a -x "$ROOTDIR/mksnt/sysinf" ]; then
  # Windows/MKS
  MEM=`"$ROOTDIR/mksnt/sysinf" memory -v | grep "Total Physical Memory: " | sed 's/Total Physical Memory: *//g'`
  MEM="$(($machine_memory / 1024))"
else
  echo "Unable to determine amount of physical memory on the machine"
fi

if [ $MEM -lt 2000 ]; then
  echo "Test skipped due to low (or unknown) memory on the system: $MEM Mb"
  exit 0
fi

echo "MEMORY=$MEM Mb"

set -x

cp ${TESTSRC}/Test7005594.java .
cp ${TESTSRC}/Test7005594.sh .

${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} -d . Test7005594.java

${TESTJAVA}/bin/java ${TESTOPTS} -Xmx1600m -Xms1600m -XX:+IgnoreUnrecognizedVMOptions -XX:-ZapUnusedHeapArea -Xcomp -XX:CompileOnly=Test7005594.test -XX:CompileCommand=quiet Test7005594 > test.out 2>&1

result=$?

cat test.out

if [ $result -eq 95 ]
then
  echo "Passed"
  exit 0
fi

if [ $result -eq 97 ]
then
  echo "Failed"
  exit 1
fi

# The test should pass when no enough space for object heap
grep "Could not reserve enough space for .*object heap" test.out
if [ $? = 0 ]
then
  echo "Passed"
  exit 0
else
  echo "Failed"
  exit 1
fi
