#!/bin/sh
#
# Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

JAVA_OPTS="-cp $TESTCLASSPATH $TESTJAVAOPTS $TESTVMOPTS"
TESTED_JAVA_HOME="$TESTJAVA"

ver=$(${TESTED_JAVA_HOME}/bin/java ${JAVA_OPTS} -version 2>&1)
isComp=$( echo ${ver} | grep -c "compiled mode")
if [[ $isComp  != 1 ]]; then
        echo "skipped. This test works only with -Xcomp"
        exit
fi

digit="[0123456789]"
number="${digit}+"
size_pattern="${number}Kb"
pattern="(CodeCache|(CodeHeap.*)): size=${size_pattern} used=${size_pattern} max_used=${size_pattern} free=${size_pattern}"

res=$(${TESTED_JAVA_HOME}/bin/java ${JAVA_OPTS} -XX:+PrintCodeCacheOnCompilation -XX:-Inline vm.compiler.CodeCacheInfoOnCompilation.PrintOnCall | egrep -ce "${pattern}")
echo "res: " ${res}

if (( "${res}" != "0" )); then
        echo "passed"
        true
else
        echo $pattern
        echo " not found"
        false
fi
