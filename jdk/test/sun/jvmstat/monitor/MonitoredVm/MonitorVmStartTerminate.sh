#
# Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @test
# @bug 4990825
# @summary attach to external but local JVM processes
# @library ../../testlibrary
# @build Sleeper
# @build JavaProcess
# @build MonitorVmStartTerminate
# @run shell MonitorVmStartTerminate.sh
#

. ${TESTSRC-.}/../../testlibrary/utils.sh

setup
verify_os

JAVA="${TESTJAVA}/bin/java"
CP=${TESTJAVA}${FS}lib${FS}tools.jar${PS}${TESTCLASSES}${PS}${TESTCLASSES}${FS}..${FS}..${FS}testlibrary

${JAVA} ${TESTVMOPTS} -classpath ${CP} MonitorVmStartTerminate
