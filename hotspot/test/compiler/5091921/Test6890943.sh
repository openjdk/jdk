#!/bin/sh
# 
# Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
## some tests require path to find test source dir
if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh


set -x

cp ${TESTSRC}/Test6890943.java .
cp ${TESTSRC}/input6890943.txt .
cp ${TESTSRC}/output6890943.txt .
cp ${TESTSRC}/Test6890943.sh .

${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} -d . Test6890943.java

${TESTJAVA}/bin/java -XX:-PrintVMOptions -XX:+IgnoreUnrecognizedVMOptions ${TESTVMOPTS} Test6890943 < input6890943.txt > pretest.out 2>&1

# This test sometimes tickles an unrelated performance warning that interferes with diff.
grep -v 'warning: Performance bug: SystemDictionary' pretest.out > test.out

diff output6890943.txt test.out

result=$?
if [ $result -eq 0 ]
then
  echo "Passed"
  exit 0
else
  echo "Failed"
  exit 1
fi
