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

JAVA="$TESTJAVA/bin/java"
JAVA_OPTS="$TESTJAVAOPTS $TESTVMOPTS -cp $TESTCLASSPATH"

TST="metaspace.flags.maxMetaspaceSize.maxMetaspaceSize"
echo ""
echo "$JAVA $JAVA_OPTS -XX:MaxMetaspaceSize=100m $TST"
echo ""
$JAVA $JAVA_OPTS -XX:MaxMetaspaceSize=100m $TST
res=$?

printf "\n\n"
if [ $res -eq 0 ]; then
        echo Test passed
else
        grep -s "Out of Memory Error" hs_err_pid*.log
        res2=$?
        if [ $res2 -eq 0 ]; then
            echo JAVA crashed with expected Out of Memory Error error.
            echo Test passed
        else
            echo Test failed
            exit 1
        fi
fi
