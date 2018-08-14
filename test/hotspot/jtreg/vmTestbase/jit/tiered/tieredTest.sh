#!/bin/bash
#
# Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

JAVA_OPTS="$TESTJAVAOPTS $TESTVMOPTS"
JAVA="$TESTJAVA/bin/java"

log=output.txt
tiered="off"

if echo "$JAVA_OPTS" | grep "[-]XX:+TieredCompilation"; then
    tiered="on"
elif echo "$JAVA_OPTS" | grep "[-]XX:-TieredCompilation"; then
    tiered="off"
else
    echo "TEST PASSED: TieredCompilation option is not specified. Nothing to test"
    exit 0
fi

echo "Tiered is ${tiered}"

$JAVA $JAVA_OPTS -XX:+PrintTieredEvents -version >$log 2>&1

if grep "Client VM" $log; then
    echo "TEST PASSED: Client VM. The test is useless"
    exit 0
fi

if grep "TieredCompilation not supported in this VM" $log; then
    echo "TEST PASSED: Non-tiered Server VM. The test is useless"
    exit 0
fi

if ! egrep '^[0-9.]+: \[compile level=[0-9]' $log; then
    if [ "${tiered}" == "on" ]; then
        echo "TEST FAILED: No PrintTieredEvents output"
        exit 2
    else
        echo "TEST PASSED: No events with TieredCompilation turned off"
        exit 0
    fi
else
    if [ "${tiered}" == "off" ]; then
        echo "TEST FAILED: PrintTieredEvents output found but TieredCompilation is turned off"
        exit 2
    else
        echo "TEST PASSED: PrintTieredEvents output found"
        exit 0
    fi
fi
