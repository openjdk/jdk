#
# Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 7104647
# @run shell jcmd-help.sh
# @summary Test that output of 'jcmd -h' matches the usage.out file

JCMD="${TESTJAVA}/bin/jcmd"

rm -f jcmd.out 2>/dev/null
${JCMD} -J-XX:+UsePerfData -h > jcmd.out 2>&1

diff -w jcmd.out ${TESTSRC}/usage.out
if [ $? != 0 ]
then
  echo "Output of jcmd -h differ from expected output. Failed."
  rm -f jcmd.out 2>/dev/null
  exit 1
fi

rm -f jcmd.out 2>/dev/null
${JCMD} -J-XX:+UsePerfData -help > jcmd.out 2>&1

diff -w jcmd.out ${TESTSRC}/usage.out
if [ $? != 0 ]
then
  echo "Output of jcmd -help differ from expected output. Failed."
  rm -f jcmd.out 2>/dev/null
  exit 1
fi

rm -f jcmd.out 2>/dev/null
exit 0
