#!/bin/ksh
# Copyright (c) 1995, 2018, Oracle and/or its affiliates. All rights reserved.
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

JAVA:="$TESTJAVA/bin/java"
JAVA_OPTS:="$TESTJAVAOPTS $TESTVMOPTS -cp $TESTCLASSPATH"

# Test JVM startup with different memory options.
#
# This checks that 64-bit VMs can start with huge values of memory
# options. It is intended to be run on machines with more than 4G
# available memory
#
# Based on InitMaxHeapSize, InitNegativeHeapSize, InitMinHeapSize,
# InitZeroHeapSize
#
# TODO: Actually we need to determine amount of virtual memory available


# go to the testDir passed as the first command line argument
#testDir=$1
#shift
#cd $testDir


java="$JAVA $JAVA_OPTS"

# Succeed if we are not running 64-bit
$java nsk.share.PrintProperties sun.arch.data.model | grep 64 >> /dev/null
if [ $? -ne 0 ]; then
        echo 'Skipping the test; a 64-bit VM is required.'
        exit 0
fi

# Try to determine available memory
os=`uname`;
if [ $os = "SunOS" ]; then
        VMStatSystemMemory=`vmstat 2 2 | awk 'NR == 4 { printf("%d", $5 / 1024); }'`
        PrtSystemMemory=`/usr/sbin/prtconf 2>&1 | awk '/^Memory size:/ { memsize = $3; }END { printf("%d", memsize); }'`

        if [ $PrtSystemMemory -lt $VMStatSystemMemory ]; then
                # it's running in solaris zone
                # vmstat reports free memory available for all kernel not zone
                SystemMemory=$(($PrtSystemMemory * 3 / 4)) # expectation that free memory greater than 0.75 of total memory
                Exact=0
        else
                SystemMemory=$VMStatSystemMemory
                Exact=1
        fi

        Exact=1
elif [ $os = "Linux" ]; then
        SystemMemory=`awk '/^(MemFree|SwapFree|Cached):/ { sum += $2; }END { printf("%d", sum / 1024); }' /proc/meminfo`
        Exact=1
elif [ $os = "Darwin" ]; then
        SystemMemory=`vm_stat  | awk 'NR == 2 { printf("%d",$3 * 4 / 1024) }'`
        Exact=1
else
        echo "Unknown arch: $os"
        SystemMemory=0
fi

case $m in
Win*|CYG*)
        echo "Do not checking ulimit on windows host."
        ;;
*)
        ULimitMemory=`ulimit -a | grep virtual | awk '{printf($5)}'`
        echo "ulimit is: $ULimitMemory"
        if [ $ULimitMemory != "unlimited" ]; then
                ULimitMemory=$(($ULimitMemory / 1024 ))
                if [ $ULimitMemory -lt $SystemMemory ]; then
                        echo "ulimit is less then system memory."
                        SystemMemory=$ULimitMemory
                fi
        fi
        ;;
esac

echo "Available memory in the system: $SystemMemory (mb)"
if [ $SystemMemory -eq 0 ]; then
        echo "Unable to determine available memory"
        exit 0
#We required more than 4096m, because there are Metaspace, codecache, stack, and memory fragmentation
elif [ $Exact -eq 1 -a $SystemMemory -lt 5120 ]; then
        echo "Not enough memory in the system to test 64bit functionality"
        exit 0
elif [ $Exact -eq 0 -a $SystemMemory -lt 6120 ]; then
        echo "Not enough memory in the system to test 64bit functionality"
        exit 0
else
        echo "Heap memory size beyond a 32 bit address range can be allocated for JVM"
fi

# Compile the InitMaxHeapSize program
error=0

test_successful() {
        message=$1
        cmd=$2
        printf "\n$message\n"
        echo "$cmd"
        $cmd
        rc=$?
        if [ $rc -ne 0 ]; then
                echo "Exit code: $rc"
                error=1
        fi
}

test_unsuccessful() {
        message=$1
        cmd=$2
        printf "\n$message\n"
        echo "$cmd"
        $cmd
        rc=$?
        if [ $rc -eq 0 ]; then
                echo "Exit code: $rc"
                error=1
        fi
}

MemStat="gc.huge.quicklook.largeheap.MemOptions.MemStat"

test_successful "Maximum heap size within 32-bit address range" "$java -Xmx2G $MemStat"
test_successful "Maximum heap size at 32-bit address range" "$java -Xmx4G $MemStat"
test_successful "Maximum heap size outside 32-bit address range" "$java -Xmx5G $MemStat"
test_unsuccessful "Maximum heap size of negative value" "$java -Xmx-1m $MemStat"
test_unsuccessful "Maximum heap size of zero value" "$java -Xmx0m $MemStat"

#test_unsuccessful "Less than minimum required heap size" "$java -Xms2176k -Xmx2176k $MemStat"
#test_successful "Minimum required heap size" "$java -Xms2177k -Xmx2177k $MemStat"

test_successful "Initial heap size within 32-bit address range" "$java -Xms2G -Xmx2G $MemStat"
test_successful "Initial heap size at 32-bit address range" "$java -Xms4G  -Xmx4G $MemStat"
test_successful "Initial heap size outside 32-bit address range" "$java -Xms4200M  -Xmx5G $MemStat"
test_unsuccessful "Initial heap size of negative value" "$java -Xms-1m $MemStat"
test_successful "Initial heap size of zero value" "$java -Xms0m $MemStat"

#test_successful "Initial young generation size within 32-bit range" "$java -Xmx3G -XX:NewSize=2G $MemStat"
#test_successful "Initial young generation size at 32-bit range" "$java -Xmx5G -XX:NewSize=4G $MemStat"
#test_successful "Initial young generation size outside 32-bit range" "$java -Xmx5G -XX:NewSize=4G $MemStat"

#test_successful "Initial old generation size within 32-bit range" "$java -Xmx3G -XX:OldSize=2G $MemStat"
#test_successful "Initial old generation size at 32-bit range" "$java -Xmx5G -XX:OldSize=4G $MemStat"
#test_successful "Initial old generation size outside 32-bit range" "$java -Xmx5G -XX:OldSize=4G $MemStat"

printf "\n\n"
if [ $error -eq 0 ]; then
        echo Test passed
else
        echo Test failed
        exit 1
fi
