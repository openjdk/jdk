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

rm -f libHelloWorld*.$SO_TYPE HelloWorld.class

$JAVA_HOME/bin/javac -d . $DIR/HelloWorld.java

# Run once with non-compressed oops.
OPTS="-J-Xmx4g -J-XX:-UseCompressedOops --info --verbose"
$JAVA_HOME/bin/jaotc $OPTS --output libHelloWorld.$SO_TYPE HelloWorld.class || exit 1

JAVA_OPTS="-Xmx4g -XX:-UseCompressedOops -XX:+UnlockDiagnosticVMOptions -XX:+UseAOTStrictLoading -XX:AOTLibrary=./libHelloWorld.$SO_TYPE"

$JAVA_HOME/bin/java $JAVA_OPTS -XX:+PrintAOT -version | grep "aot library" || exit 1
$JAVA_HOME/bin/java $JAVA_OPTS HelloWorld || exit 1

TIMEFORMAT="%3R"
N=5

LIBRARY=libHelloWorld-coop.$SO_TYPE

for gc in UseG1GC UseParallelGC; do
    # Now with compressed oops.
    OPTS="-J-XX:+UseCompressedOops -J-XX:+$gc --info --verbose"
    $JAVA_HOME/bin/jaotc $OPTS --output $LIBRARY HelloWorld.class

    # Dump CDS archive.
    $JAVA_HOME/bin/java -Xshare:dump -XX:-UseAOT -XX:+$gc || exit 1

    JAVA_OPTS="-Xmx256m"

    echo "Tiered C1 $gc:"
    for i in `seq 1 $N`; do
        OUT=`time $JAVA_HOME/bin/java -XX:+$gc -XX:-UseCompressedOops -XX:-UseAOT -XX:TieredStopAtLevel=1 $JAVA_OPTS HelloWorld`
        if [ "$OUT" != "Hello, world!" ]; then
            echo $OUT
            exit 1
        fi
    done

    echo "Tiered C1/C2 $gc:"
    for i in `seq 1 $N`; do
        OUT=`time $JAVA_HOME/bin/java -XX:+$gc -XX:-UseCompressedOops -XX:-UseAOT $JAVA_OPTS HelloWorld`
        if [ "$OUT" != "Hello, world!" ]; then
            echo $OUT
            exit 1
        fi
    done

    JAVA_OPTS="-Xmx256m -XX:+UseCompressedOops -XX:+UnlockDiagnosticVMOptions -XX:+UseAOTStrictLoading -XX:AOTLibrary=./$LIBRARY"


    echo "AOT $gc:"
    for i in `seq 1 $N`; do
        OUT=`time $JAVA_HOME/bin/java -XX:+$gc $JAVA_OPTS HelloWorld`
        if [ "$OUT" != "Hello, world!" ]; then
            echo $OUT
            exit 1
        fi
    done

    echo "AOT -Xshare:on $gc:"
    for i in `seq 1 $N`; do
        OUT=`time $JAVA_HOME/bin/java -Xshare:on -XX:+$gc $JAVA_OPTS HelloWorld`
        if [ "$OUT" != "Hello, world!" ]; then
            echo $OUT
            exit 1
        fi
    done
done

rm -f libHelloWorld*.$SO_TYPE HelloWorld.class
