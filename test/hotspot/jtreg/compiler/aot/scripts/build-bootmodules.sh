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

MODULES="java.base"

TEST=HelloWorld

for m in $MODULES; do
    rm -f $JAVA_HOME/lib/lib$m*.$SO_TYPE
done

$JAVA_HOME/bin/javac -d . $DIR/$TEST.java

JAOTC_OPTS="-J-ea -J-Xmx4g --compile-for-tiered --info"
JAVA_OPTS="-Xmx4g -XX:+UseAOT -XX:+UnlockDiagnosticVMOptions -XX:+UseAOTStrictLoading"

# Compile with: +UseCompressedOops +UseG1GC
LIBRARIES=""
for m in $MODULES; do
    if [ -f $DIR/$m-list.txt ]; then
	LIST="--compile-commands $DIR/$m-list.txt"
    else
	LIST=""
    fi
    $JAVA_HOME/bin/jaotc -J-XX:+UseCompressedOops -J-XX:+UseG1GC $JAOTC_OPTS $LIST --output lib$m-coop.$SO_TYPE --module $m || exit 1
    LIBRARIES="$LIBRARIES$PWD/lib$m-coop.$SO_TYPE:"
done
$JAVA_HOME/bin/java $JAVA_OPTS -XX:+UseCompressedOops -XX:+UseG1GC -XX:AOTLibrary=$LIBRARIES $TEST || exit 1

# Compile with: +UseCompressedOops +UseParallelGC
LIBRARIES=""
for m in $MODULES; do
    if [ -f $DIR/$m-list.txt ]; then
	LIST="--compile-commands $DIR/$m-list.txt"
    else
	LIST=""
    fi
    $JAVA_HOME/bin/jaotc -J-XX:+UseCompressedOops -J-XX:+UseParallelGC $JAOTC_OPTS $LIST --output lib$m-coop-nong1.$SO_TYPE --module $m || exit 1
    LIBRARIES="$LIBRARIES$PWD/lib$m-coop-nong1.$SO_TYPE:"
done
$JAVA_HOME/bin/java $JAVA_OPTS -XX:+UseCompressedOops -XX:+UseParallelGC -XX:AOTLibrary=$LIBRARIES $TEST || exit 1

# Compile with: -UseCompressedOops +UseG1GC
LIBRARIES=""
for m in $MODULES; do
    if [ -f $DIR/$m-list.txt ]; then
	LIST="--compile-commands $DIR/$m-list.txt"
    else
	LIST=""
    fi
    $JAVA_HOME/bin/jaotc -J-XX:-UseCompressedOops -J-XX:+UseG1GC $JAOTC_OPTS $LIST --output lib$m.$SO_TYPE --module $m || exit 1
    LIBRARIES="$LIBRARIES$PWD/lib$m.$SO_TYPE:"
done
$JAVA_HOME/bin/java $JAVA_OPTS -XX:-UseCompressedOops -XX:+UseG1GC -XX:AOTLibrary=$LIBRARIES $TEST || exit 1

# Compile with: -UseCompressedOops +UseParallelGC
LIBRARIES=""
for m in $MODULES; do
    if [ -f $DIR/$m-list.txt ]; then
	LIST="--compile-commands $DIR/$m-list.txt"
    else
	LIST=""
    fi
    $JAVA_HOME/bin/jaotc -J-XX:-UseCompressedOops -J-XX:+UseParallelGC $JAOTC_OPTS $LIST --output lib$m-nong1.$SO_TYPE --module $m || exit 1
    LIBRARIES="$LIBRARIES$PWD/lib$m-nong1.$SO_TYPE:"
done
$JAVA_HOME/bin/java $JAVA_OPTS -XX:-UseCompressedOops -XX:+UseParallelGC -XX:AOTLibrary=$LIBRARIES $TEST || exit 1

echo "Installing shared libraries in: $JAVA_HOME/lib/"
for m in $MODULES; do
    mv -f lib$m*.$SO_TYPE $JAVA_HOME/lib/
done

# Test installed libraries.
$JAVA_HOME/bin/java $JAVA_OPTS -XX:-UseCompressedOops -XX:+UseG1GC       -XX:+PrintAOT $TEST | grep "aot library" || exit 1
$JAVA_HOME/bin/java $JAVA_OPTS -XX:-UseCompressedOops -XX:+UseParallelGC -XX:+PrintAOT $TEST | grep "aot library" || exit 1
$JAVA_HOME/bin/java $JAVA_OPTS -XX:+UseCompressedOops -XX:+UseG1GC       -XX:+PrintAOT $TEST | grep "aot library" || exit 1
$JAVA_HOME/bin/java $JAVA_OPTS -XX:+UseCompressedOops -XX:+UseParallelGC -XX:+PrintAOT $TEST | grep "aot library" || exit 1

rm -f $TEST.class

