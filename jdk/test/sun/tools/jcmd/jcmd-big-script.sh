#!/bin/sh

#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 7154822
# @summary test if we can send a file over 1024 bytes large via jcmd -f
# @author David Buck
#
# @library ../common
# @build SimpleApplication ShutdownSimpleApplication
# @run shell jcmd-big-script.sh

. ${TESTSRC}/../common/CommonSetup.sh
. ${TESTSRC}/../common/ApplicationSetup.sh

# Start application and use PORTFILE for coordination
PORTFILE="${TESTCLASSES}"/shutdown.port
startApplication SimpleApplication "${PORTFILE}"

failed=0;

# -f <script>
rm -f jcmd.out 2>/dev/null
set +e # even if jcmd fails, we do not want abort the script yet.
${JCMD} -J-XX:+UsePerfData $appJavaPid -f ${TESTSRC}/dcmd-big-script.txt > jcmd.out 2>&1
status="$?"
set -e
if [ "$status" != 0 ]; then
  echo "jcmd command returned non-zero exit code (status=$status). Failed."
  failed=1;
fi
cat jcmd.out
set +e # if the test passes, grep will "fail" with an exit code of 1
grep Exception jcmd.out > /dev/null 2>&1
status="$?"
set -e
if [ "$status" = 0 ]; then
  echo "Output of \"jcmd [pid] -f dcmd-big-script.txt\" contains string \"Exception\". Failed."
  failed=1;
fi

# clean up
rm -f jcmd.out 2>/dev/null
stopApplication "${PORTFILE}"
waitForApplication

exit $failed
