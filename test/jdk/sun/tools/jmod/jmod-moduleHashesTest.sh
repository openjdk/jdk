#!/bin/sh

#
# Copyright (c) 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
# @bug 8240903
# @run shell jmod-moduleHashesTest.sh
# @summary Test consistency of moduleHashes attribute between builds

. ${TESTSRC-.}/common.sh

setup

NUM_MODULES=64

mkdir -p src/ma
echo "module ma {}" > src/ma/module-info.java

for i in $(seq $NUM_MODULES); do
    mb_name=m${i}b
    mkdir -p src/$mb_name
    echo "module $mb_name {requires ma;}" > src/$mb_name/module-info.java
    ${JAVAC} --module-source-path src --module $mb_name -d ${TESTCLASSES}/classes
done

mkdir jmods-first
mkdir jmods-second

for i in $(seq $NUM_MODULES); do
    mb_name=m${i}b

    ${JMOD} create -class-path ${TESTCLASSES}/classes/$mb_name --date="2021-01-06T14:36:00+02:00" \
        jmods-first/$mb_name.jmod

    ${JMOD} create -class-path ${TESTCLASSES}/classes/$mb_name --date="2021-01-06T14:36:00+02:00" \
        jmods-second/$mb_name.jmod
done

${JMOD} create -class-path ${TESTCLASSES}/classes/ma --date="2021-01-06T14:36:00+02:00" \
    --module-path jmods-first --hash-modules ".*" jmods-first/ma.jmod

${JMOD} create -class-path ${TESTCLASSES}/classes/ma --date="2021-01-06T14:36:00+02:00" \
    --module-path jmods-second --hash-modules ".*" jmods-second/ma.jmod

$golden_diff jmods-first/ma.jmod jmods-second/ma.jmod
if [ $? != 0 ]
then
    echo "ModuleHashes attribute not reproducible between builds. Failed."
    exit 1
fi
echo "Passed"
exit 0
