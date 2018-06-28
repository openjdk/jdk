#!/bin/bash
# Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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

JAVA="$TESTJAVA/bin/java"
JAVA_OPTS="$TESTJAVAOPTS $TESTVMOPTS -cp $TESTCLASSPATH -agentlib:alloc001"

. ${TESTSRC}/../../../../../test_env.sh

# Set virtual memory usage limit to be not 'unlimited' on unix platforms
# This is workaround for 6683371.
case $VM_OS in
aix | bsd | linux | solaris)
    echo "Check virtual memory usage limits"
    soft_limit=`ulimit -S -v` || ""
    hard_limit=`ulimit -H -v` || ""
    echo "Virtual memory usage limit (hard): $hard_limit"
    echo "Virtual memory usage limit (soft): $soft_limit"

    # Need to set ulimit if currently unlimited or > 4GB (1GB on 32 bit)
    if [ $VM_BITS -eq 32 ]
    then
        max_ulimit=1048576
        max_heap=256m
    else
        max_ulimit=4194304
        max_heap=512m
    fi

    should_update_ulimit=0
    if [ -n "$soft_limit" ]; then
        if [ "$soft_limit" = "unlimited" ]; then
            should_update_ulimit=1
        elif [ "$soft_limit" -gt "$max_ulimit" ]; then
            should_update_ulimit=1
        fi
    fi

    if [ "$should_update_ulimit" = "1" ]; then
        echo "Try to limit virtual memory usage to $max_ulimit"
        ulimit -S -v $max_ulimit || true
    fi

    # When we limit virtual memory then we need to also limit other GC args and MALLOC_ARENA_MAX.
    # Otherwise the JVM may not start. See JDK-8043516
    JAVA_OPTS="${JAVA_OPTS} -XX:MaxHeapSize=$max_heap -XX:CompressedClassSpaceSize=64m"
    export MALLOC_ARENA_MAX=4
    soft_limit=`ulimit -S -v`
    echo "Virtual memory usage limit (soft): $soft_limit"
    echo "New JAVA_OPTS: $JAVA_OPTS"
    echo "export MALLOC_ARENA_MAX=4"
    ;;
*)
    ;;
esac

export DYLD_LIBRARY_PATH=$DYLD_LIBRARY_PATH:$TESTNATIVEPATH
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$TESTNATIVEPATH
export PATH=$PATH:$TESTNATIVEPATH

echo $JAVA ${JAVA_OPTS} nsk.jvmti.Allocate.alloc001
$JAVA ${JAVA_OPTS} nsk.jvmti.Allocate.alloc001
exit=$?

if [ $exit -ne 95 ]
then
    exit $exit
fi
