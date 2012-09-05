#
# Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @run shell jps-Vvml_2.sh
# @summary Test that output of 'jps -Vvml' shows proper output when no JVM arguments, flags, or main args are present

. ${TESTSRC-.}/../../jvmstat/testlibrary/utils.sh

setup
verify_os

cleanup() {
  kill_proc ${SLEEPER_PID}
}

trap 'cleanup' 0 HUP INT QUIT TERM

JPS="${TESTJAVA}/bin/jps"

JAVA="${TESTJAVA}/bin/java"

# fire up a Sleeper that block indefinitely - but don't pass
# any args to Sleeper.main() or any jvm flags or options, as we
# need to inspect jps output for the no args condition.
#
# Note: this test can not pass on a VM with UsePerfData disabled by default,
# and we can not set -XX:+UsePerfData as that invalidates the test premise of
# there being no jvm flags

${JAVA} -cp ${TESTCLASSPATH:-${TESTCLASSES}} Sleeper &
SLEEPER_PID=$!

${JPS} -J-XX:Flags=${TESTSRC}/vmflags -Vvml | awk -f ${TESTSRC}/jps-Vvml_Output2.awk
RC=$?

cleanup

exit ${RC}

