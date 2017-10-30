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

AOT_OPTS="-XX:+UseAOT"

$JAVA_HOME/bin/java $AOT_OPTS -XX:+PrintAOT -version | grep "aot library" || exit 1

# Dump CDS archive.
$JAVA_HOME/bin/java $AOT_OPTS -Xshare:dump || exit 1

FILE="HelloWorld"

APP="com.sun.tools.javac.Main"

JAVA_OPTS="-XX:-UseCompressedOops"

rm -f $FILE.class

$JAVA_HOME/bin/java $JAVA_OPTS $AOT_OPTS $APP -verbose $FILE.java || exit 1
$JAVA_HOME/bin/java $AOT_OPTS $FILE || exit 1

JAVA_OPTS="-XX:+UseCompressedOops"

rm -f $FILE.class

$JAVA_HOME/bin/java $JAVA_OPTS $AOT_OPTS $APP -verbose $FILE.java || exit 1
$JAVA_HOME/bin/java $AOT_OPTS $FILE || exit 1

rm -f $FILE.class

TIMEFORMAT="%3R"
N=5

#echo "-Xint:"
#for i in `seq 1 10`; do
#    time $JAVA_HOME/bin/java -Xint $JAVA_OPTS $APP $FILE.java
#    if [ $? -ne 0 ]; then
#        exit 1
#    fi
#    rm -f $FILE.class
#done

echo "Tiered C1:"
for i in `seq 1 $N`; do
    time $JAVA_HOME/bin/java $JAVA_OPTS -XX:-UseAOT -XX:TieredStopAtLevel=1 $APP $FILE.java
    if [ $? -ne 0 ]; then
        exit 1
    fi
    rm -f $FILE.class
done

echo "Tiered C1/C2:"
for i in `seq 1 $N`; do
    time $JAVA_HOME/bin/java $JAVA_OPTS -XX:-UseAOT $APP $FILE.java
    if [ $? -ne 0 ]; then
        exit 1
    fi
    rm -f $FILE.class
done

echo "Tiered C1/C2 -Xshare:on:"
for i in `seq 1 $N`; do
    time $JAVA_HOME/bin/java $JAVA_OPTS -XX:-UseAOT -Xshare:on $APP $FILE.java
    if [ $? -ne 0 ]; then
        exit 1
    fi
    rm -f $FILE.class
done

echo "Tiered AOT:"
for i in `seq 1 $N`; do
    time $JAVA_HOME/bin/java $JAVA_OPTS $AOT_OPTS $APP $FILE.java
    if [ $? -ne 0 ]; then
        exit 1
    fi
    rm -f $FILE.class
done

echo "Tiered AOT -Xshare:on:"
for i in `seq 1 $N`; do
    time $JAVA_HOME/bin/java $JAVA_OPTS $AOT_OPTS -Xshare:on $APP $FILE.java
    if [ $? -ne 0 ]; then
        exit 1
    fi
    rm -f $FILE.class
done

NAME="jvmci"
DIR="$DIR/../../../../../../src/jdk.internal.vm.ci"
FILES=`find $DIR -type f -name '*.java'`
COUNT=`find $DIR -type f -name '*.java' | wc -l`

rm -rf tmp

echo "Tiered C1 (compiling $NAME: $COUNT classes):"
for i in `seq 1 $N`; do
    mkdir tmp
    time $JAVA_HOME/bin/javac -J-XX:-UseAOT -J-XX:TieredStopAtLevel=1 -XDignore.symbol.file -d tmp $FILES
    if [ $? -ne 0 ]; then
        exit 1
    fi
    rm -rf tmp
done

echo "Tiered C1/C2 (compiling $NAME: $COUNT classes):"
for i in `seq 1 $N`; do
    mkdir tmp
    time $JAVA_HOME/bin/javac -J-XX:-UseAOT -XDignore.symbol.file -cp /java/devtools/share/junit/latest/junit.jar -d tmp $FILES
    if [ $? -ne 0 ]; then
        exit 1
    fi
    rm -rf tmp
done

echo "Tiered AOT (compiling $NAME: $COUNT classes):"
for i in `seq 1 $N`; do
    mkdir tmp
    time $JAVA_HOME/bin/javac -J-XX:+UseAOT -XDignore.symbol.file -cp /java/devtools/share/junit/latest/junit.jar -d tmp $FILES
    if [ $? -ne 0 ]; then
        exit 1
    fi
    rm -rf tmp
done

echo "Tiered AOT -Xshare:on (compiling $NAME: $COUNT classes):"
for i in `seq 1 $N`; do
    mkdir tmp
    time $JAVA_HOME/bin/javac -J-Xshare:on -J-XX:+UseAOT -XDignore.symbol.file -cp /java/devtools/share/junit/latest/junit.jar -d tmp $FILES
    if [ $? -ne 0 ]; then
        exit 1
    fi
    rm -rf tmp
done


