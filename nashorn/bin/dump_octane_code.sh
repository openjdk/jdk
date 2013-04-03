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

#
# The purpose of this script is to provide a large amount of IR/bytecode from a known
# application to be diffed against the same output with a different Nashorn version.
# That way we can quickly detect if a seemingly minute change modifies a lot of code,
# which it most likely shouldn't. One example of this was when AccessSpecializer was
# moved into Lower the first time, it worked fine, but as a lot of Scope information
# at the time was finalized further down the code pipeline it did a lot fewer callsite
# specializations. This would have been immediately detected with a before and after 
# diff using the output from this script.
#

ITERS=$1
if [ -z $ITERS ]; then 
    ITERS=7
fi
NASHORN_JAR=dist/nashorn.jar
JVM_FLAGS="-ea -esa -server -jar ${NASHORN_JAR}"

BENCHMARKS=( "box2d.js" "code-load.js" "crypto.js" "deltablue.js" "earley-boyer.js" "gbemu.js" "mandreel.js" "navier-stokes.js" "pdfjs.js" "raytrace.js" "regexp.js" "richards.js" "splay.js" )

for BENCHMARK in "${BENCHMARKS[@]}"
do     
    echo "START: ${BENCHMARK}"
    CMD="${JAVA_HOME}/bin/java ${JVM_FLAGS} -co --print-lower-parse test/script/external/octane/${BENCHMARK}"
    $CMD
    echo "END: ${BENCHMARK}"
    echo ""
done

echo "Done"
