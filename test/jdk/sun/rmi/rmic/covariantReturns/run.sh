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
# @bug 4892308
# @summary This test verifies that rmic does not fail if it encounters
# a class that takes advantage of covariant return types (Tiger
# language feature).  The cases used here are remote interfaces that
# reference java.util.Collection or a subtype.
# @author Peter Jones
#
# @build G2 G2Impl G5 G5Impl
# @run shell run.sh

if [ "${TESTJAVA}" = "" ]
then
    echo "TESTJAVA not set.  Test cannot execute.  Failed."
    exit 1
fi

set -ex

${TESTJAVA}/bin/rmic -classpath ${TESTCLASSES:-.} -d ${TESTCLASSES:-.} G2Impl
${TESTJAVA}/bin/rmic -classpath ${TESTCLASSES:-.} -d ${TESTCLASSES:-.} G5Impl
