# Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

JAOTC_OPTS="-J-Xmx4g -J-XX:-UseCompressedOops"

rm -f libjdk.aot.$SO_TYPE

$JAVA_HOME/bin/jaotc $JAOTC_OPTS --info --module jdk.aot --output libjdk.aot.$SO_TYPE || exit 1

rm -f libjava.base-aot.$SO_TYPE

$JAVA_HOME/bin/jaotc $JAOTC_OPTS -J-XX:AOTLibrary=./libjdk.aot.$SO_TYPE --info --compile-commands $DIR/java.base-list.txt --output libjava.base-aot.$SO_TYPE --module java.base || exit 1

$JAVA_HOME/bin/javac -d . $DIR/HelloWorld.java

$JAVA_HOME/bin/java -XX:-UseCompressedOops -XX:+UnlockExperimentalVMOptions -XX:AOTLibrary=./libjava.base-aot.$SO_TYPE HelloWorld

rm -f HelloWorld.class libjdk.aot.$SO_TYPE libjava.base-aot.$SO_TYPE
