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
# @bug 6964018
# @summary Unit test for common tools infrastructure.
#
# @build SimpleApplication SleeperApplication ShutdownSimpleApplication
# @run shell CommonTests.sh

. ${TESTSRC}/CommonSetup.sh
. ${TESTSRC}/ApplicationSetup.sh

# hope for the best:
status=0


# Test program path constants from CommonSetup.sh:
#
for name in JAVA JHAT JINFO JMAP JPS JSTACK; do
    eval value=$`echo $name`

    echo "INFO: $name=$value"
    if [ -x "$value" ]; then
        echo "INFO: '$value' is executable."
    else
        echo "ERROR: '$value' is not executable." >&2
        status=1
    fi
done


# Display flag values from CommonSetup.sh:
#
for name in isCygwin isMKS isLinux isSolaris isUnknownOS isWindows; do
    eval value=$`echo $name`
    echo "INFO: flag $name=$value"
done


# Test OS constant from CommonSetup.sh:
#
if [ -z "$OS" ]; then
    echo "ERROR: OS constant cannot be empty." >&2
    status=1
fi


# Display the PATTERN_EOL value:
#
echo "INFO: PATTERN_EOL="`echo "$PATTERN_EOL" | od -c`


# Test PATTERN_EOL with 'grep' for a regular line.
#
TESTOUT="${TESTCLASSES}/testout.grep_reg_line_eol"
set +e
echo 'regular line' | grep "line${PATTERN_EOL}" > "$TESTOUT"
set -e
if [ -s "$TESTOUT" ]; then
    echo "INFO: PATTERN_EOL works for regular line with grep."
else
    echo "ERROR: PATTERN_EOL does not work for regular line with grep." >&2
    status=1
fi


if $isWindows; then
    # Test PATTERN_EOL with 'grep' for a CR line.
    #
    TESTOUT="${TESTCLASSES}/testout.grep_cr_line_eol"
    set +e
    echo 'CR line' | grep "line${PATTERN_EOL}" > "$TESTOUT"
    set -e
    if [ -s "$TESTOUT" ]; then
        echo "INFO: PATTERN_EOL works for CR line with grep."
    else
        echo "ERROR: PATTERN_EOL does not work for CR line with grep." >&2
        status=1
    fi
fi


# Test PATTERN_EOL with 'sed' for a regular line.
#
TESTOUT="${TESTCLASSES}/testout.sed_reg_line_eol"
echo 'regular line' | sed -n "/line${PATTERN_EOL}/p" > "$TESTOUT"
if [ -s "$TESTOUT" ]; then
    echo "INFO: PATTERN_EOL works for regular line with sed."
else
    echo "ERROR: PATTERN_EOL does not work for regular line with sed." >&2
    status=1
fi


if $isWindows; then
    # Test PATTERN_EOL with 'sed' for a CR line.
    #
    TESTOUT="${TESTCLASSES}/testout.sed_cr_line_eol"
    echo 'CR line' | sed -n "/line${PATTERN_EOL}/p" > "$TESTOUT"
    if [ -s "$TESTOUT" ]; then
        echo "INFO: PATTERN_EOL works for CR line with sed."
    else
        echo "ERROR: PATTERN_EOL does not work for CR line with sed." >&2
        status=1
    fi
fi


# Display the PATTERN_WS value:
#
echo "INFO: PATTERN_WS="`echo "$PATTERN_WS" | od -c`


# Test PATTERN_WS with 'grep' for a blank.
#
TESTOUT="${TESTCLASSES}/testout.grep_blank"
set +e
echo 'blank: ' | grep "$PATTERN_WS" > "$TESTOUT"
set -e
if [ -s "$TESTOUT" ]; then
    echo "INFO: PATTERN_WS works for blanks with grep."
else
    echo "ERROR: PATTERN_WS does not work for blanks with grep." >&2
    status=1
fi


# Test PATTERN_WS with 'grep' for a tab.
#
TESTOUT="${TESTCLASSES}/testout.grep_tab"
set +e
echo 'tab:	' | grep "$PATTERN_WS" > "$TESTOUT"
set -e
if [ -s "$TESTOUT" ]; then
    echo "INFO: PATTERN_WS works for tabs with grep."
else
    echo "ERROR: PATTERN_WS does not work for tabs with grep." >&2
    status=1
fi


# Test PATTERN_WS with 'sed' for a blank.
#
TESTOUT="${TESTCLASSES}/testout.sed_blank"
echo 'blank: ' | sed -n "/$PATTERN_WS/p" > "$TESTOUT"
if [ -s "$TESTOUT" ]; then
    echo "INFO: PATTERN_WS works for blanks with sed."
else
    echo "ERROR: PATTERN_WS does not work for blanks with sed." >&2
    status=1
fi


# Test PATTERN_WS with 'sed' for a tab.
#
TESTOUT="${TESTCLASSES}/testout.sed_tab"
echo 'tab:	' | sed -n "/$PATTERN_WS/p" > "$TESTOUT"
if [ -s "$TESTOUT" ]; then
    echo "INFO: PATTERN_WS works for tabs with sed."
else
    echo "ERROR: PATTERN_WS does not work for tabs with sed." >&2
    status=1
fi


# Test startApplication and use PORTFILE for coordination
# The app sleeps for 30 seconds.
#
PORTFILE="${TESTCLASSES}"/shutdown.port
startApplication SleeperApplication "${PORTFILE}" 30


# Test appJavaPid in "ps" cmd output.
#
TESTOUT="${TESTCLASSES}/testout.ps_app"
set +e
if $isCygwin; then
    # On Cygwin, appJavaPid is the Windows pid for the Java process
    # and appOtherPid is the Cygwin pid for the Java process.
    ps -p "$appOtherPid" \
        | grep "${PATTERN_WS}${appJavaPid}${PATTERN_WS}" > "$TESTOUT"
else
    # output only pid and comm columns to avoid mismatches
    ps -eo pid,comm \
        | grep "^${PATTERN_WS}*${appJavaPid}${PATTERN_WS}" > "$TESTOUT"
fi
set -e
if [ -s "$TESTOUT" ]; then
    echo "INFO: begin appJavaPid=$appJavaPid in 'ps' cmd output:"
    cat "$TESTOUT"
    echo "INFO: end appJavaPid=$appJavaPid in 'ps' cmd output."
else
    echo "ERROR: 'ps' cmd should show appJavaPid=$appJavaPid." >&2
    status=1
fi

if [ -n "$appOtherPid" ]; then
    # Test appOtherPid in "ps" cmd output, if we have one.
    #
    TESTOUT="${TESTCLASSES}/testout.ps_other"
    set +e
    if $isCygwin; then
        ps -p "$appOtherPid" \
            | grep "${PATTERN_WS}${appOtherPid}${PATTERN_WS}" > "$TESTOUT"
    else
        # output only pid and comm columns to avoid mismatches
        ps -eo pid,comm \
            | grep "^${PATTERN_WS}*${appOtherPid}${PATTERN_WS}" > "$TESTOUT"
    fi
    set -e
    if [ -s "$TESTOUT" ]; then
        echo "INFO: begin appOtherPid=$appOtherPid in 'ps' cmd output:"
        cat "$TESTOUT"
        echo "INFO: end appOtherPid=$appOtherPid in 'ps' cmd output."
    else
        echo "ERROR: 'ps' cmd should show appOtherPid=$appOtherPid." >&2
        status=1
    fi
fi


# Test stopApplication and PORTFILE for coordination
#
stopApplication "${PORTFILE}"


# Test application still running after stopApplication.
#
# stopApplication just lets the app know that it can stop, but the
# app might still be doing work. This test just demonstrates that
# fact and doesn't fail if the app is already done.
#
TESTOUT="${TESTCLASSES}/testout.after_stop"
set +e
if $isCygwin; then
    # On Cygwin, appJavaPid is the Windows pid for the Java process
    # and appOtherPid is the Cygwin pid for the Java process.
    ps -p "$appOtherPid" \
        | grep "${PATTERN_WS}${appJavaPid}${PATTERN_WS}" > "$TESTOUT"
else
    # output only pid and comm columns to avoid mismatches
    ps -eo pid,comm \
        | grep "^${PATTERN_WS}*${appJavaPid}${PATTERN_WS}" > "$TESTOUT"
fi
set -e
if [ -s "$TESTOUT" ]; then
    echo "INFO: it is okay for appJavaPid=$appJavaPid to still be running" \
        "after stopApplication() is called."
    echo "INFO: begin 'after_stop' output:"
    cat "$TESTOUT"
    echo "INFO: end 'after_stop' output."
fi


# Test waitForApplication
#
# The app might already be gone so this function shouldn't generate
# a fatal error in either call.
#
waitForApplication

if [ $isWindows = false ]; then
    # Windows can recycle pids quickly so we can't use this test there
    TESTOUT="${TESTCLASSES}/testout.after_kill"
    set +e
    # output only pid and comm columns to avoid mismatches
    ps -eo pid,comm \
        | grep "^${PATTERN_WS}*${appJavaPid}${PATTERN_WS}" > "$TESTOUT"
    set -e
    if [ -s "$TESTOUT" ]; then
        echo "ERROR: 'ps' cmd should not show appJavaPid." >&2
        echo "ERROR: begin 'after_kill' output:" >&2
        cat "$TESTOUT" >&2
        echo "ERROR: end 'after_kill' output." >&2
        status=1
    else
        echo "INFO: 'ps' cmd does not show appJavaPid after" \
            "waitForApplication() is called."
    fi
fi


# Test killApplication
#
# The app is already be gone so this function shouldn't generate
# a fatal error.
#
killApplication

exit $status
