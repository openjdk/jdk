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

# $1 - error code
# $2 - test name
# $3,.. - decription
test_fail() {
    error=$1
    shift
    name=$1
    shift
    echo "TEST [$name] FAILED:"
    echo "$@"
    exit $error
}

# $@ - additional vm opts
start_test() {
    # disable core dump on *nix
    ulimit -S -c 0
    # disable core dump on windows
    VMOPTS="$@ -XX:-CreateMinidumpOnCrash"
    cmd="${JAVA} ${VMOPTS} -XX:+ReplayCompiles -XX:ReplayDataFile=${replay_data}"
    echo $cmd
    $cmd
    return $?
}

# $1 - error_code
# $2 - test name
# $3,.. - additional vm opts
positive_test() {
    error=$1
    shift
    name=$1
    shift
    VMOPTS="${TESTVMOPTS} $@"
    echo "POSITIVE TEST [$name]"
    start_test ${VMOPTS}
    exit_code=$?
    if [ ${exit_code} -ne 0 ]
    then
        test_fail $error "$name" "exit_code[${exit_code}] != 0 during replay "\
                "w/ vmopts: ${VMOPTS}"
    fi
}

# $1 - error_code
# $2 - test name
# $2,.. - additional vm opts
negative_test() {
    error=$1
    shift
    name=$1
    shift
    VMOPTS="${TESTVMOPTS} $@"
    echo "NEGATIVE TEST [$name]"
    start_test ${VMOPTS}
    exit_code=$?
    if [ ${exit_code} -eq 0 ]
    then
        test_fail $error "$name" "exit_code[${exit_code}] == 0 during replay "\
                "w/ vmopts: ${VMOPTS}"
    fi
}

# $1 - initial error_code
common_tests() {
    positive_test $1 "COMMON :: THE SAME FLAGS"
    if [ $tiered_available -eq 1 ]
    then
        positive_test `expr $1 + 1` "COMMON :: TIERED" -XX:+TieredCompilation
    fi
}

# $1 - initial error_code
# $2 - non-tiered comp_level 
nontiered_tests() {
    level=`grep "^compile " $replay_data | awk '{print $6}'`
    # is level available in non-tiere
    if [ "$level" -eq $2 ]
    then
        positive_test $1 "NON-TIERED :: AVAILABLE COMP_LEVEL" \
                -XX:-TieredCompilation
    else
        negative_test `expr $1 + 1` "NON-TIERED :: UNAVAILABLE COMP_LEVEL" \
        negative_test `expr $1 + 1` "NON-TIERED :: UNAVAILABLE COMP_LEVEL" \
                -XX:-TieredCompilation
    fi
}

# $1 - initial error_code
client_tests() {
    # testing in opposite VM
    if [ $server_available -eq 1 ]
    then
        negative_test $1 "SERVER :: NON-TIERED" -XX:-TieredCompilation \
                -server
        if [ $tiered_available -eq 1 ]
        then
            positive_test `expr $1 + 1` "SERVER :: TIERED" -XX:+TieredCompilation \
                    -server
        fi
    fi
    nontiered_tests `expr $1 + 2` $client_level 
}

# $1 - initial error_code
server_tests() {
    # testing in opposite VM
    if [ $client_available -eq 1 ]
    then
        # tiered is unavailable in client vm, so results w/ flags will be the same as w/o flags
        negative_test $1 "CLIENT" -client
    fi
    nontiered_tests `expr $1 + 2` $server_level
}

cleanup() {
    ${RM} -f core*
    ${RM} -f replay*.txt
    ${RM} -f hs_err_pid*.log
    ${RM} -f test_core
    ${RM} -f test_replay.txt
}

JAVA=${TESTJAVA}${FS}bin${FS}java

replay_data=test_replay.txt

${JAVA} ${TESTVMOPTS} -Xinternalversion 2>&1 | grep debug

# Only test fastdebug 
if [ $? -ne 0 ]
then
    echo TEST SKIPPED: product build
    exit 0
fi

is_int=`${JAVA} ${TESTVMOPTS} -version 2>&1 | grep -c "interpreted mode"`
# Not applicable for Xint
if [ $is_int -ne 0 ]
then
    echo TEST SKIPPED: interpreted mode
    exit 0
fi

cleanup

client_available=`${JAVA} ${TESTVMOPTS} -client -Xinternalversion 2>&1 | \
        grep -c Client`
server_available=`${JAVA} ${TESTVMOPTS} -server -Xinternalversion 2>&1 | \
        grep -c Server`
tiered_available=`${JAVA} ${TESTVMOPTS} -XX:+TieredCompilation -XX:+PrintFlagsFinal -version | \
        grep TieredCompilation | \
        grep -c true`
is_tiered=`${JAVA} ${TESTVMOPTS} -XX:+PrintFlagsFinal -version | \
        grep TieredCompilation | \
        grep -c true`
# CompLevel_simple -- C1
client_level=1
# CompLevel_full_optimization -- C2 or Shark 
server_level=4

echo "client_available=$client_available"
echo "server_available=$server_available"
echo "tiered_available=$tiered_available"
echo "is_tiered=$is_tiered"

# crash vm in compiler thread with generation replay data and 'small' dump-file
# $@ - additional vm opts
generate_replay() {
    if [ $VM_OS != "windows" ]
    then
        # enable core dump
        ulimit -c unlimited

        if [ $VM_OS = "solaris" ]
        then
            coreadm -p core $$
        fi
    fi

    cmd="${JAVA} ${TESTVMOPTS} $@ \
            -Xms8m \
            -Xmx32m \
            -XX:MetaspaceSize=4m \
            -XX:MaxMetaspaceSize=16m \
            -XX:InitialCodeCacheSize=512k \
            -XX:ReservedCodeCacheSize=4m \
            -XX:ThreadStackSize=512 \
            -XX:VMThreadStackSize=512 \
            -XX:CompilerThreadStackSize=512 \
            -XX:ParallelGCThreads=1 \
            -XX:CICompilerCount=1 \
            -Xcomp \
            -XX:CICrashAt=1 \
            -XX:+CreateMinidumpOnCrash \
            -XX:+DumpReplayDataOnError \
            -XX:ReplayDataFile=${replay_data} \
            -version"
    echo GENERATION OF REPLAY.TXT:
    echo $cmd

    ${cmd} > crash.out 2>&1
    
    core_locations=`grep -i core crash.out | grep "location:" | \
            sed -e 's/.*location: //'`
    rm crash.out 
    # processing core locations for *nix
    if [ $VM_OS != "windows" ]
    then
        # remove 'or' between '/core.<pid>' and 'core'
        core_locations=`echo $core_locations | \
                sed -e 's/\([^ ]*\) or \([^ ]*\)/\1 \2/'`
        # add <core_path>/core.<pid> core.<pid>
        core_with_dir=`echo $core_locations | awk '{print $1}'`
        dir=`dirname $core_with_dir`
        core_with_pid=`echo $core_locations | awk '{print $2}'`
        if [ -n ${core_with_pid} ]
        then
            core_locations="$core_locations $dir${FS}$core_with_pid $core_with_pid"
        fi
    fi

    echo "LOOKING FOR CORE IN ${core_locations}"
    for core in $core_locations
    do
        if [ -r "$core" ]
        then
            core_file=$core
        fi
    done

    # core-file was found
    if [ -n "$core_file" ]
    then
        ${MV} "${core_file}" test_core
        core_file=test_core
    fi

    ${RM} -f hs_err_pid*.log
}

