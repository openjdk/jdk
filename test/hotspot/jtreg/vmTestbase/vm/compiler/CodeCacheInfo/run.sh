#!/bin/bash
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

JAVA_OPTS="$TESTJAVAOPTS $TESTVMOPTS"
TESTED_JAVA_HOME="$TESTJAVA"

digit="[0123456789]"
number="${digit}*"
size_pattern="${number}Kb"
bound_pattern="0x[0123456789abcdef]*"
s1=" size=${size_pattern} used=${size_pattern} max_used=${size_pattern} free=${size_pattern}"
s2=" bounds \[${bound_pattern}, ${bound_pattern}, ${bound_pattern}\]"
s3=" total_blobs=${number} nmethods=${number} adapters=${number}"
s4=" compilation: enabled"
summary_pat_nonseg="CodeCache:${s1}${s2}${s3}${s4}"
summary_pat_seg="((CodeHeap.*):${s1}${s2})+${s3}${s4}"

# check whether SegmentedCodeCache enabled
segmented=$(${TESTED_JAVA_HOME}/bin/java ${JAVA_OPTS} -XX:+PrintFlagsFinal -version | egrep -c ' +SegmentedCodeCache +:?= +true')
out=$(${TESTED_JAVA_HOME}/bin/java ${JAVA_OPTS} -XX:+PrintCodeCache -version)
if [ "${segmented}" = "1" ]; then
        summary_pat=${summary_pat_seg}
        status="enabled"
else
        summary_pat=${summary_pat_nonseg}
        status="disabled"
fi
echo "INFO: SegmentedCodeCache is ${status}"

res=$(echo ${out} | egrep -c "${summary_pat}")
if [ "${res}" = "1" ]; then
        echo "passed"
        true
else
        echo ${summary_pat}
        echo "did not match for:"
        echo ${out}
        false
fi
