#!/bin/sh
#
# Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

. $TESTSRC/../share/common.sh

JAVA_OPTS="${JAVA_OPTS} -XX:-UseGCOverheadLimit -XX:-TransmitErrorReport"

if [ $CORE_SUPPORTED -eq 0 ]; then
        pass "Core dump is not supported"
fi

DUMPFILE=heap.bin

rm -f ${DUMPFILE}

ulimit -c unlimited || true

echo "Below 'Unexpected error' is actually expected - JVM is forced to dump core"
${JAVA} ${JAVA_OPTS} heapdump.share.EatMemory -core &

pid=$!

wait $pid

status=$?

if [ $status -eq 0 ]; then
        pass "Java exited with exit status: $status"
fi

for CORE in core* /cores/core.$pid; do
        [ -e "$CORE" ] && break;
done

if [ ! -f "$CORE" ]; then
        fail "Java exited with exit status: $status, but core file was not created"
fi
echo "Found core file: $CORE"

JMAP_DUMP_OPT="--binaryheap --dumpfile=${DUMPFILE}"
EXE_OPT="--exe"
CORE_OPT="--core"
JHSDB_OPT="jmap"

${JHSDB} ${JHSDB_OPT} ${JMAP_DUMP_OPT} ${EXE_OPT} ${JAVA} ${CORE_OPT} ${CORE}

status=$?
if [ $status -ne 0 ]; then
        fail "jmap exited with exit status $status"
fi

if [ ! -f "${DUMPFILE}" ]; then
        fail "Dump file was not created: $DUMPFILE"
fi

verify_heapdump ${DUMPFILE}

if [ $? -ne 0 ]; then
        fail "Verification of heap dump failed"
fi

if [ "$TEST_CLEANUP" != "false" ]; then
        rm -f ${CORE}
        rm -f hs_err_pid*
fi

pass
