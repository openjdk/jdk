#
# Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
# @bug 5009652
# @library ../../jvmstat/testlibrary
# @build Sleeper
# @run shell jps-m_2.sh
# @summary Test that output of 'jps -m' shows proper output for main with no args.

. ${TESTSRC-.}/../../jvmstat/testlibrary/utils.sh

setup
verify_os

cleanup() {
  kill_proc ${SLEEPER_PID}
}

trap 'cleanup' 0 HUP INT QUIT TERM

JPS="${TESTJAVA}/bin/jps"
JAVA="${TESTJAVA}/bin/java"

# fire up a Sleeper that blocks indefinitely - but don't pass
# any args to Sleeper.main(), as we need to inspect jps output
# for the no args condition.
#
${JAVA} -cp ${TESTCLASSES} Sleeper &
SLEEPER_PID=$!

${JPS} -m | awk -f ${TESTSRC}/jps-m_Output2.awk
RC=$?

cleanup

exit ${RC}

