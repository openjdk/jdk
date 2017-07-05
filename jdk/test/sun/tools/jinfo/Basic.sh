#!/bin/sh
#
# Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6402766
# @summary Unit test for jinfo utility
#
# @library ../common
# @build SimpleApplication ShutdownSimpleApplication
# @run shell Basic.sh

. ${TESTSRC}/../common/CommonSetup.sh
. ${TESTSRC}/../common/ApplicationSetup.sh

# Start application and use PORTFILE for coordination
PORTFILE="${TESTCLASSES}"/shutdown.port
startApplication SimpleApplication "${PORTFILE}"

# all return statuses are checked in this test
set +e

failed=0

runSA=true

if [ $isMacos = true -o $isAIX = true -o `uname -m` = ppc64 ]; then
    runSA=false
fi

if [ $isLinux = true ]; then
    # Some Linux systems disable non-child ptrace (see 7050524)
    ptrace_scope=`/sbin/sysctl -n kernel.yama.ptrace_scope`
    if [ $? = 0 ]; then
        if [ $ptrace_scope = 1 ]; then
            runSA=false
        fi
    fi
fi

if [ $runSA = true ]; then
    # -sysprops option
    ${JINFO} -J-XX:+UsePerfData -F -sysprops $appJavaPid
    if [ $? != 0 ]; then failed=1; fi

    # -flags option
    ${JINFO} -J-XX:+UsePerfData -F -flags $appJavaPid
    if [ $? != 0 ]; then failed=1; fi

    # no option
    ${JINFO} -J-XX:+UsePerfData -F $appJavaPid
    if [ $? != 0 ]; then failed=1; fi

    # -flag option
    ${JINFO} -J-XX:+UsePerfData -F -flag +PrintGC $appJavaPid
    if [ $? != 0 ]; then failed=1; fi 

    ${JINFO} -J-XX:+UsePerfData -F -flag -PrintGC $appJavaPid
    if [ $? != 0 ]; then failed=1; fi

    ${JINFO} -J-XX:+UsePerfData -F -flag PrintGC $appJavaPid
    if [ $? != 0 ]; then failed=1; fi
fi

# -sysprops option
${JINFO} -J-XX:+UsePerfData -sysprops $appJavaPid
if [ $? != 0 ]; then failed=1; fi

# -flags option
${JINFO} -J-XX:+UsePerfData -flags $appJavaPid
if [ $? != 0 ]; then failed=1; fi

# no option
${JINFO} -J-XX:+UsePerfData $appJavaPid
if [ $? != 0 ]; then failed=1; fi

# -flag option
${JINFO} -J-XX:+UsePerfData -flag +PrintGC $appJavaPid
if [ $? != 0 ]; then failed=1; fi 

${JINFO} -J-XX:+UsePerfData -flag -PrintGC $appJavaPid
if [ $? != 0 ]; then failed=1; fi

${JINFO} -J-XX:+UsePerfData -flag PrintGC $appJavaPid
if [ $? != 0 ]; then failed=1; fi

if $isSolaris; then

    ${JINFO} -J-XX:+UsePerfData -flag +ExtendedDTraceProbes $appJavaPid
    if [ $? != 0 ]; then failed=1; fi

    ${JINFO} -J-XX:+UsePerfData -flag -ExtendedDTraceProbes $appJavaPid
    if [ $? != 0 ]; then failed=1; fi

    ${JINFO} -J-XX:+UsePerfData -flag ExtendedDTraceProbes $appJavaPid
    if [ $? != 0 ]; then failed=1; fi

fi

set -e

stopApplication "${PORTFILE}"
waitForApplication

exit $failed
