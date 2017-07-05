#!/bin/bash
# Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

ITERS=$1
if [ -z $ITERS ]; then 
    ITERS=7
fi
NASHORN_JAR=dist/nashorn.jar
JVM_FLAGS="-Djava.ext.dirs=`dirname $0`/../dist:$JAVA_HOME/jre/lib/ext -XX:+UnlockDiagnosticVMOptions -Dnashorn.unstable.relink.threshold=8 -Xms2G -Xmx2G -XX:+TieredCompilation -server -jar ${NASHORN_JAR}"
JVM_FLAGS7="-Xbootclasspath/p:${NASHORN_JAR} ${JVM_FLAGS}"
OCTANE_ARGS="--verbose --iterations ${ITERS}"

BENCHMARKS=( "box2d.js" "code-load.js" "crypto.js" "deltablue.js" "earley-boyer.js" "gbemu.js" "navier-stokes.js" "pdfjs.js" "raytrace.js" "regexp.js" "richards.js" "splay.js" )
# TODO mandreel.js has metaspace issues

if [ ! -z $JAVA7_HOME ]; then	
    echo "running ${ITERS} iterations with java7 using JAVA_HOME=${JAVA7_HOME}..."
    for BENCHMARK in "${BENCHMARKS[@]}"
    do 
	CMD="${JAVA7_HOME}/bin/java ${JVM_FLAGS} test/script/basic/run-octane.js -- test/script/external/octane/${BENCHMARK} ${OCTANE_ARGS}"
	$CMD
    done
else
    echo "no JAVA7_HOME set. skipping java7"
fi

if [ ! -z $JAVA8_HOME ]; then
    echo "running ${ITERS} iterations with java8 using JAVA_HOME=${JAVA8_HOME}..."   
    for BENCHMARK in "${BENCHMARKS[@]}"
    do 
	CMD="${JAVA8_HOME}/bin/java ${JVM_FLAGS} test/script/basic/run-octane.js -- test/script/external/octane/${BENCHMARK} ${OCTANE_ARGS}"
	$CMD
    done
else 
    echo "no JAVA8_HOME set."
fi

echo "Done"
