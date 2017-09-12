#
# Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4666958
# @summary rmic should not try to compile source files in its (binary)
# class path other than those that is generates.
# @author Peter Jones
#
# @run shell run.sh

if [ "${TESTJAVA}" = "" ]
then
    echo "TESTJAVA not set.  Test cannot execute.  Failed."
    exit 1
fi

set -x

javafile=./Impl.java
classfile=./Impl.class

rm -f $javafile $classfile

echo "public class Impl implements java.rmi.Remote { }" >> $javafile

${TESTJAVA}/bin/rmic -classpath . -d . Impl

result=$?
if [ $result -eq 0 ]
then
    echo "TEST FAILED: rmic invocation succeeded"
    exit 1
fi
echo "TEST PASSED: rmic invocation failed"
exit 0
