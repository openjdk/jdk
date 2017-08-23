# Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

pushd `dirname $0` > /dev/null
DIR=`pwd`
popd > /dev/null

# set env variables
. $DIR/test-env.sh

$JAVA_HOME/bin/java -XX:+UnlockDiagnosticVMOptions -XX:+UseAOTStrictLoading -XX:+PrintAOT -version | grep "aot library" || exit 1

# Dump CDS archive.
$JAVA_HOME/bin/java -Xshare:dump || exit 1

FILE="empty.js"

TIMEFORMAT="%3R"
N=5

rm -f libjdk.nashorn.$SO_TYPE
$JAVA_HOME/bin/jaotc --info --compile-commands jdk.scripting.nashorn-list.txt --module jdk.scripting.nashorn --output libjdk.nashorn.$SO_TYPE || exit 1

echo "Tiered C1:"
for i in `seq 1 $N`; do
    time $JAVA_HOME/bin/jjs $JAVA_OPTS -J-XX:-UseAOT -J-XX:TieredStopAtLevel=1 $FILE
    if [ $? -ne 0 ]; then
        exit 1
    fi
done

echo "Tiered C1/C2:"
for i in `seq 1 $N`; do
    time $JAVA_HOME/bin/jjs $JAVA_OPTS -J-XX:-UseAOT $FILE
    if [ $? -ne 0 ]; then
        exit 1
    fi
done

echo "Tiered AOT:"
for i in `seq 1 $N`; do
    time $JAVA_HOME/bin/jjs $JAVA_OPTS -J-XX:+UnlockDiagnosticVMOptions -J-XX:+UseAOTStrictLoading -J-XX:AOTLibrary=./libjdk.nashorn.$SO_TYPE $FILE
    if [ $? -ne 0 ]; then
        exit 1
    fi
done

echo "Tiered AOT -Xshare:on:"
for i in `seq 1 $N`; do
    time $JAVA_HOME/bin/jjs $JAVA_OPTS -J-Xshare:on -J-XX:+UnlockDiagnosticVMOptions -J-XX:+UseAOTStrictLoading -J-XX:AOTLibrary=./libjdk.nashorn.$SO_TYPE $FILE
    if [ $? -ne 0 ]; then
        exit 1
    fi
done

rm -f libjdk.nashorn.$SO_TYPE
