#
# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6942989
# @ignore until 6964018 is fixed
# @summary Check for WeakReference leak in anonymous Logger objects
# @author Daniel D. Daugherty
#
# @run build AnonLoggerWeakRefLeak
# @run shell/timeout=180 AnonLoggerWeakRefLeak.sh

# The timeout is: 2 minutes for infrastructure and 1 minute for the test
#

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

JAVA="${TESTJAVA}"/bin/java
JMAP="${TESTJAVA}"/bin/jmap
JPS="${TESTJAVA}"/bin/jps

set -eu

TEST_NAME="AnonLoggerWeakRefLeak"
TARGET_CLASS="java\.lang\.ref\.WeakReference"

is_cygwin=false
is_mks=false
is_windows=false

case `uname -s` in
CYGWIN*)
    is_cygwin=true
    is_windows=true
    ;;
Windows_*)
    is_mks=true
    is_windows=true
    ;;
*)
    ;;
esac


# wrapper for grep
#
grep_cmd() {
    set +e
    if $is_windows; then
        # need dos2unix to get rid of CTRL-M chars from java output
        dos2unix | grep "$@"
        status="$?"
    else
        grep "$@"
        status="$?"
    fi
    set -e
}


# MAIN begins here
#

seconds=
if [ "$#" -gt 0 ]; then
    seconds="$1"
fi

# see if this version of jmap supports the '-histo:live' option
jmap_option="-histo:live"
set +e
"${JMAP}" "$jmap_option" 0 > "$TEST_NAME.jmap" 2>&1
grep '^Usage: ' "$TEST_NAME.jmap" > /dev/null 2>&1
status="$?"
set -e
if [ "$status" = 0 ]; then
    echo "INFO: switching jmap option from '$jmap_option'\c"
    jmap_option="-histo"
    echo " to '$jmap_option'."
fi

"${JAVA}" ${TESTVMOPTS} -classpath "${TESTCLASSES}" \
    "$TEST_NAME" $seconds > "$TEST_NAME.log" 2>&1 &
test_pid="$!"
echo "INFO: starting $TEST_NAME as pid = $test_pid"

# wait for test program to get going
count=0
while [ "$count" -lt 30 ]; do
    sleep 2
    grep_cmd '^INFO: call count = 0$' < "$TEST_NAME.log" > /dev/null 2>&1
    if [ "$status" = 0 ]; then
        break
    fi
    count=`expr $count + 1`
done

if [ "$count" -ge 30 ]; then
    echo "ERROR: $TEST_NAME failed to get going." >&2
    echo "INFO: killing $test_pid"
    kill "$test_pid"
    exit 1
elif [ "$count" -gt 1 ]; then
    echo "INFO: $TEST_NAME took $count loops to start."
fi

if $is_cygwin; then
    # We need the Windows pid for jmap and not the Cygwin pid.
    # Note: '\t' works on Cygwin, but doesn't seem to work on Solaris.
    jmap_pid=`"${JPS}"| grep_cmd "[ \t]$TEST_NAME$" | sed 's/[ \t].*//'`
    if [ -z "$jmap_pid" ]; then
        echo "FAIL: jps could not map Cygwin pid to Windows pid." >&2
        echo "INFO: killing $test_pid"
        kill "$test_pid"
        exit 2
    fi
    echo "INFO: pid = $test_pid maps to Windows pid = $jmap_pid"
else
    jmap_pid="$test_pid"
fi

decreasing_cnt=0
increasing_cnt=0
loop_cnt=0
prev_instance_cnt=0

while true; do
    # Output format for 'jmap -histo' in JDK1.5.0:
    #
    #     <#bytes> <#instances> <class_name>
    #
    # Output format for 'jmap -histo:live':
    #
    #     <num>: <#instances> <#bytes> <class_name>
    #
    set +e
    "${JMAP}" "$jmap_option" "$jmap_pid" > "$TEST_NAME.jmap" 2>&1
    status="$?"
    set -e

    if [ "$status" != 0 ]; then
        echo "INFO: jmap exited with exit code = $status"
        if [ "$loop_cnt" = 0 ]; then
            echo "INFO: on the first iteration so no samples were taken."
            echo "INFO: start of jmap output:"
            cat "$TEST_NAME.jmap"
            echo "INFO: end of jmap output."
            echo "FAIL: jmap is unable to take any samples." >&2
            echo "INFO: killing $test_pid"
            kill "$test_pid"
            exit 2
        fi
        echo "INFO: The likely reason is that $TEST_NAME has finished running."
        break
    fi

    instance_cnt=`grep_cmd "[ 	]$TARGET_CLASS$" \
        < "$TEST_NAME.jmap" \
        | sed '
            # strip leading whitespace; does nothing in JDK1.5.0
            s/^[ 	][ 	]*//
            # strip <#bytes> in JDK1.5.0; does nothing otherwise
            s/^[1-9][0-9]*[ 	][ 	]*//
            # strip <num>: field; does nothing in JDK1.5.0
            s/^[1-9][0-9]*:[ 	][ 	]*//
            # strip <class_name> field
            s/[ 	].*//
            '`
    if [ -z "$instance_cnt" ]; then
        echo "INFO: instance count is unexpectedly empty"
        if [ "$loop_cnt" = 0 ]; then
            echo "INFO: on the first iteration so no sample was found."
            echo "INFO: There is likely a problem with the sed filter."
            echo "INFO: start of jmap output:"
            cat "$TEST_NAME.jmap"
            echo "INFO: end of jmap output."
            echo "FAIL: cannot find the instance count value." >&2
            echo "INFO: killing $test_pid"
            kill "$test_pid"
            exit 2
        fi
    else
        echo "INFO: instance_cnt = $instance_cnt"

        if [ "$instance_cnt" -gt "$prev_instance_cnt" ]; then
            increasing_cnt=`expr $increasing_cnt + 1`
        else
            decreasing_cnt=`expr $decreasing_cnt + 1`
        fi
        prev_instance_cnt="$instance_cnt"
    fi

    # delay between samples
    sleep 5

    loop_cnt=`expr $loop_cnt + 1`
done

echo "INFO: increasing_cnt = $increasing_cnt"
echo "INFO: decreasing_cnt = $decreasing_cnt"

echo "INFO: The instance count of" `eval echo $TARGET_CLASS` "objects"
if [ "$decreasing_cnt" = 0 ]; then
    echo "INFO: is always increasing."
    echo "FAIL: This indicates that there is a memory leak." >&2
    exit 2
fi

echo "INFO: is both increasing and decreasing."
echo "PASS: This indicates that there is not a memory leak."
exit 0
