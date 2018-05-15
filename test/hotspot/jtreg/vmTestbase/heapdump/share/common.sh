#!/bin/bash
# Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

# This script contains useful functions for testing heapdump
# feature of VM.

: ${JAVA:="$TESTJAVA/bin/java"}
: ${JAVA_OPTS:="$TESTJAVAOPTS $TESTVMOPTS -cp $TESTCLASSPATH"}
: ${CP:="$TESTCLASSPATH"}
: ${TEST_CLEANUP:="false"}
: ${JMAP:="$TESTJAVA/bin/jmap"}
: ${JHSDB:="$TESTJAVA/bin/jhsdb"}

export PATH=$PATH:$TESTNATIVEPATH
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$TESTNATIVEPATH
export DYLD_LIBRARY_PATH=$DYLD_LIBRARY_PATH:$TESTNATIVEPATH

if [ -z "${JAVA}" ]; then
        echo JAVA variable is not set.
        exit 1
fi

if [ -n "${testWorkDir}" ]; then
        cd ${testWorkDir}
fi

if [ -z "${DUMPBASE}" ]; then
        DUMPBASE=.
fi
if [ -z "$DEBUGGER_JAVA_OPTS" ]; then
        DEBUGGER_JAVA_OPTS="$JAVA_OPTS"
fi

CORE_SUPPORTED=1

for opt in $DEBUGGER_JAVA_OPTS; do
        case $opt in
        -D*)
                JMAP="$JMAP -J$opt"
                ;;
        esac
done

export CORE_SUPPORTED

# Verify heap dump
# This function starts HprofParser and looks for message "Server is ready."
# in output, in which case heap dump is verified.
verify_heapdump() {
        filename=$1
        shift
        echo Verifying ${filename}
        echo ${JAVA} -cp $CP jdk.test.lib.hprof.HprofParser ${filename}
        ${JAVA} -cp $CP jdk.test.lib.hprof.HprofParser ${filename}
}

cleanup() {
        result="$1"
        if [ -n "$DUMPFILE" ]; then
                if [ "$TEST_CLEANUP" != "false" ]; then
                        rm -f "$DUMPFILE"
                else
                        gzip "$DUMPFILE" || true
                fi
        fi
}

fail() {
        message="$1"
        res=1
        echo "$message"
        echo "TEST FAILED"
        cleanup $res
        exit 1
}

pass() {
        message="$1"
        if [ -n "$message" ]; then
                echo "$message"
        fi
        echo "TEST PASSED"
        cleanup 0
        exit 0
}

# Parse VM options that have size argument and return it's value in bytes.
# Function applicable to -Xmn, -Xms, -Xms and all possible -XX: options.
parse_heap_size() {
    OPTION=$1
    SIZE=0
    MULTIPLIER=0

    # On Solaris sed don't support '+' quantificator, so <smth><smth>* is used.
    # There is no support for '?' too, so <smth>* is used instead.
    # Generally speaking, there sed on Solaris support only basic REs.
    case "$OPTION" in
        -Xm*)
            SIZE=`echo $OPTION | sed -e 's#-Xm[xns]\([0-9][0-9]*\).*#\1#'`
            MULTIPLIER=`echo $OPTION | sed -e 's#-Xm[xns][0-9][0-9]*\([kKmMgG]*\)#\1#'`
            ;;
        -XX*)
            SIZE=`echo $OPTION | sed -e 's#[^=][^=]*=\([0-9][0-9]*\).*#\1#'`
            MULTIPLIER=`echo $OPTION | sed -e 's#[^=][^=]*=[0-9][0-9]*\([kKmMgG]*\)#\1#'`
            ;;
    esac

    case "$MULTIPLIER" in
        k|K)
            SIZE=$(( SIZE * 1024 ))
            ;;
        m|M)
            SIZE=$(( SIZE * 1024 * 1024 ))
            ;;
        g|G)
            SIZE=$(( SIZE * 1024 * 1024 * 1024 ))
            ;;
    esac

    echo $SIZE
}

# Derivate max heap size from passed option list.
get_max_heap_size() {
    MaxHeapSize=
    InitialHeapSize=
    MaxNewSize=
    NewSize=
    OldSize=

    for OPTION in "$@"; do
        case "$OPTION" in
            -Xmx*|-XX:MaxHeapSize=*)
                MaxHeapSize=`parse_heap_size $OPTION`
                ;;
            -Xms*|-XX:InitialHeapSize=*)
                InitialHeapSize=`parse_heap_size $OPTION`
                ;;
            -Xmn*|-XX:MaxNewSize=*)
                MaxNewSize=`parse_heap_size $OPTION`
                ;;
            -XX:NewSize=*)
                NewSize=`parse_heap_size $OPTION`
                ;;
            -XX:OldSize=*)
                OldSize=`parse_heap_size $OPTION`
                ;;
        esac
    done

    if [ -n "$MaxHeapSize" ]; then
        echo "$MaxHeapSize"
    elif [ -n "$InitialHeapSize" ]; then
        echo "$InitialHeapSize"
    elif [ -n "$MaxNewSize" -a -n "$OldSize" ]; then
        echo $(( MaxHeapSize + OldSize ))
    elif [ -n "$NewSize" -a -n "$OldSize" ]; then
        echo $(( 2 * NewSize + OldSize ))
    elif [ -n "$OldSize" ]; then
        echo $(( 2 * OldSize ))
    elif [ -n "$MaxNewSize" ]; then
        echo $(( 2 * MaxNewSize ))
    elif [ -n "$NewSize" ]; then
        echo $(( 3 * NewSize ))
    else
        echo "128M"
    fi
}
