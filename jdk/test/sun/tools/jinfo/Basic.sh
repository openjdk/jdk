#!/bin/sh

#
# Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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

# Start application (send output to shutdown.port)
PORTFILE="${TESTCLASSES}"/shutdown.port
startApplication \
    -classpath "${TESTCLASSES}" SimpleApplication "${PORTFILE}"

failed=0

if [ "$OS" != "Windows" ]; then
    # -sysprops option
    ${JINFO} -sysprops $pid
    if [ $? != 0 ]; then failed=1; fi

    # -flags option
    ${JINFO} -flags $pid
    if [ $? != 0 ]; then failed=1; fi

    # no option
    ${JINFO} $pid
    if [ $? != 0 ]; then failed=1; fi

fi


# -flag option
${JINFO} -flag +PrintGC $pid
if [ $? != 0 ]; then failed=1; fi 

${JINFO} -flag -PrintGC $pid
if [ $? != 0 ]; then failed=1; fi

${JINFO} -flag PrintGC $pid
if [ $? != 0 ]; then failed=1; fi

if [ "$OS" = "SunOS" ]; then

    ${JINFO} -flag +ExtendedDTraceProbes $pid
    if [ $? != 0 ]; then failed=1; fi

    ${JINFO} -flag -ExtendedDTraceProbes $pid
    if [ $? != 0 ]; then failed=1; fi

    ${JINFO} -flag ExtendedDTraceProbes $pid
    if [ $? != 0 ]; then failed=1; fi

fi

stopApplication ShutdownSimpleApplication "${PORTFILE}"

exit $failed

