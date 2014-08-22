#
# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8023130
# @summary (process) ProcessBuilder#inheritIO does not work on Windows
# @run shell InheritIO.sh

if [ "x${TESTSRC}" = "x" ]; then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "x${TESTJAVA}" = "x" ]; then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "x${COMPILEJAVA}" = "x" ]; then
  COMPILEJAVA="${TESTJAVA}"
fi

JAVA="${TESTJAVA}/bin/java"
JAVAC="${COMPILEJAVA}/bin/javac"

cp -f ${TESTSRC}/InheritIO.java .

# compile the class ourselves, so this can run as a standalone test

${JAVAC} InheritIO.java
RES="$?"
if [ ${RES} != 0 ]; then
    echo 'FAIL: Cannot compile InheritIO.java'
    exit ${RES}
fi


for TEST_NAME in TestInheritIO TestRedirectInherit
do
    ${JAVA} ${TESTVMOPTS} -classpath . \
        'InheritIO$'${TEST_NAME} printf message > stdout.txt 2> stderr.txt

    RES="$?"
    if [ ${RES} != 0 ]; then
        echo 'FAIL: InheritIO$'${TEST_NAME}' failed with '${RES}
        exit ${RES}
    fi

    OUT_EXPECTED='message'
    OUT_RECEIVED=`cat stdout.txt`
    if [ "x${OUT_RECEIVED}" != "x${OUT_EXPECTED}" ]; then
        echo "FAIL: unexpected '${OUT_RECEIVED}' in stdout"
        exit 1
    fi

    ERR_EXPECTED='exit value: 0'
    ERR_RECEIVED=`cat stderr.txt`
    if [ "x${ERR_RECEIVED}" != "x${ERR_EXPECTED}" ]; then
        echo "FAIL: unexpected '${ERR_RECEIVED}' in stderr"
        exit 1
    fi
done

echo 'PASS: InheritIO works as expected'
