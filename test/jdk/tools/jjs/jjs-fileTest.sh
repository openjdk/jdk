#!/bin/sh

#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8145750
# @summary jjs fails to run simple scripts with security manager turned on
# @run shell jjs-fileTest.sh
# Tests basic script file execution. Execute file.js and check output
# against file.out file

. ${TESTSRC-.}/common.sh

setup
rm -f jjs-fileTest.out 2>/dev/null
${JJS} -J-Djava.security.manager ${TESTSRC}/file.js > jjs-fileTest.out 2>&1

$golden_diff jjs-fileTest.out ${TESTSRC}/file.out
if [ $? != 0 ]
then
  echo "Output of jjs file.js differ from expected output. Failed."
  rm -f jjs-fileTest.out 2>/dev/null
  exit 1
fi

rm -f jjs-fTest.out
echo "Passed"
exit 0
