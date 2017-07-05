#!/bin/sh

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
# @summary Check for WeakReference leak in anonymous Logger objects
# @author Daniel D. Daugherty
#
# @library ../../../sun/tools/common
# @build SimpleApplication ShutdownSimpleApplication
# @build AnonLoggerWeakRefLeak
# @run shell/timeout=240 AnonLoggerWeakRefLeak.sh

# The timeout is: 2 minutes for infrastructure and 2 minutes for the test
#

. ${TESTSRC}/../../../sun/tools/common/CommonSetup.sh
. ${TESTSRC}/../../../sun/tools/common/ApplicationSetup.sh


TEST_NAME="AnonLoggerWeakRefLeak"
TARGET_CLASS="java\.lang\.ref\.WeakReference"


# MAIN begins here
#

seconds=
if [ "$#" -gt 0 ]; then
    seconds="$1"
fi

# see if this version of jmap supports the '-histo:live' option
jmap_option="-histo:live"
set +e
"${JMAP}" 2>&1 | grep ':live' > /dev/null 2>&1
status="$?"
set -e
if [ "$status" != 0 ]; then
    # usage message doesn't show ':live' option

    if $isWindows; then
        # If SA isn't present, then jmap gives a different usage message
        # that doesn't show the ':live' option. However, that's a bug that
        # is covered by 6971851 so we try using the option just to be sure.
        # For some reason, this problem has only been seen on OpenJDK6 on
        # Windows. Not sure why.
        set +e
        # Note: Don't copy this code to try probing process 0 on Linux; it
        # will kill the process group in strange ways.
        "${JMAP}" "$jmap_option" 0 2>&1 | grep 'Usage' > /dev/null 2>&1
        status="$?"
        set -e
        if [ "$status" = 0 ]; then
            # Usage message generated so flag the problem.
            status=1
        else
            # No usage message so clear the flag.
            status=0
        fi
    fi

    if [ "$status" != 0 ]; then
        echo "ERROR: 'jmap $jmap_option' is not supported so this test"
        echo "ERROR: cannot work reliably. Aborting!"
        exit 2
    fi
fi

# Start application and use TEST_NAME.port for coordination
startApplication "$TEST_NAME" "$TEST_NAME.port" $seconds

finished_early=false

decreasing_cnt=0
increasing_cnt=0
loop_cnt=0
prev_instance_cnt=0

MAX_JMAP_TRY_CNT=10
jmap_retry_cnt=0
loop_cnt_on_retry=0

while true; do
    # see if the target process has finished its run and bail if it has
    set +e
    grep "^INFO: final loop count = " "$appOutput" > /dev/null 2>&1
    status="$?"
    set -e
    if [ "$status" = 0 ]; then
        break
    fi

    # Output format for 'jmap -histo' in JDK1.5.0:
    #
    #     <#bytes> <#instances> <class_name>
    #
    # Output format for 'jmap -histo:live':
    #
    #     <num>: <#instances> <#bytes> <class_name>
    #
    set +e
    "${JMAP}" "$jmap_option" "$appJavaPid" > "$TEST_NAME.jmap" 2>&1
    status="$?"
    set -e

    if [ "$status" != 0 ]; then
        echo "INFO: jmap exited with exit code = $status"

        # There are intermittent jmap failures; see 6498448.
        #
        # So far the following have been observed in a jmap call
        # that was not in a race with target process termination:
        #
        # (Solaris specific, 2nd sample)
        # <pid>: Unable to open door: target process not responding or HotSpot VM not loaded
        # The -F option can be used when the target process is not responding
        #
        # (on Solaris so far)
        # java.io.IOException
        #
        # (on Solaris so far, 1st sample)
        # <pid>: Permission denied
        #
        sed 's/^/INFO: /' "$TEST_NAME.jmap"

        if [ "$loop_cnt" = "$loop_cnt_on_retry" ]; then
            # loop count hasn't changed
            jmap_retry_cnt=`expr $jmap_retry_cnt + 1`
        else
            # loop count has changed so remember it
            jmap_retry_cnt=1
            loop_cnt_on_retry="$loop_cnt"
        fi

        # This is '-ge' because we have the original attempt plus
        # MAX_JMAP_TRY_CNT - 1 retries.
        if [ "$jmap_retry_cnt" -ge "$MAX_JMAP_TRY_CNT" ]; then
            echo "INFO: jmap failed $MAX_JMAP_TRY_CNT times in a row" \
                "without making any progress."
            echo "FAIL: jmap is unable to take any samples." >&2
            killApplication
            exit 2
        fi

        # short delay and try again
        # Note: sleep 1 didn't help with "<pid>: Permission denied"
        sleep 2
        echo "INFO: retrying jmap (retry=$jmap_retry_cnt, loop=$loop_cnt)."
        continue
    fi

    set +e
    instance_cnt=`grep "${PATTERN_WS}${TARGET_CLASS}${PATTERN_EOL}" \
        "$TEST_NAME.jmap" \
        | sed '
            # strip leading whitespace; does nothing in JDK1.5.0
            s/^'"${PATTERN_WS}${PATTERN_WS}"'*//
            # strip <#bytes> in JDK1.5.0; does nothing otherwise
            s/^[1-9][0-9]*'"${PATTERN_WS}${PATTERN_WS}"'*//
            # strip <num>: field; does nothing in JDK1.5.0
            s/^[1-9][0-9]*:'"${PATTERN_WS}${PATTERN_WS}"'*//
            # strip <class_name> field
            s/'"${PATTERN_WS}"'.*//
            '`
    set -e
    if [ -z "$instance_cnt" ]; then
        echo "INFO: instance count is unexpectedly empty"
        if [ "$loop_cnt" = 0 ]; then
            echo "INFO: on the first iteration so no sample was found."
            echo "INFO: There is likely a problem with the sed filter."
            echo "INFO: start of jmap output:"
            cat "$TEST_NAME.jmap"
            echo "INFO: end of jmap output."
            echo "FAIL: cannot find the instance count value." >&2
            killApplication
            exit 2
        fi
    else
        echo "INFO: instance_cnt = $instance_cnt"

        if [ "$instance_cnt" -gt "$prev_instance_cnt" ]; then
            increasing_cnt=`expr $increasing_cnt + 1`
        else
            # actually decreasing or the same
            decreasing_cnt=`expr $decreasing_cnt + 1`

            # For this particular WeakReference leak, the count was
            # always observed to be increasing so if we get a decreasing
            # or the same count, then the leak is fixed in the bits
            # being tested.
            echo "INFO: finishing early due to non-increasing instance count."
            finished_early=true
            killApplication
            break
        fi
        prev_instance_cnt="$instance_cnt"
    fi

    # delay between samples
    sleep 5

    loop_cnt=`expr $loop_cnt + 1`
done

if [ $finished_early = false ]; then
    stopApplication "$TEST_NAME.port"
    waitForApplication
fi

echo "INFO: $TEST_NAME has finished running."
echo "INFO: increasing_cnt = $increasing_cnt"
echo "INFO: decreasing_cnt = $decreasing_cnt"
if [ "$jmap_retry_cnt" -gt 0 ]; then
    echo "INFO: jmap_retry_cnt = $jmap_retry_cnt (in $loop_cnt iterations)"
fi

if [ "$loop_cnt" = 0 ]; then
    echo "FAIL: jmap is unable to take any samples." >&2
    exit 2
fi

echo "INFO: The instance count of" `eval echo $TARGET_CLASS` "objects"
if [ "$decreasing_cnt" = 0 ]; then
    echo "INFO: is always increasing."
    echo "FAIL: This indicates that there is a memory leak." >&2
    exit 2
fi

echo "INFO: is not always increasing."
echo "PASS: This indicates that there is not a memory leak."
exit 0
