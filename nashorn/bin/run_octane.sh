#!/bin/bash
#
# Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

LOG="./octane_$(date|sed "s/ /_/g"|sed "s/:/_/g").log"

run_one() {
    sh ../bin/runopt.sh -scripting ../test/script/basic/run-octane.js -- $1 --verbose --iterations 25 | tee -a $LOG
}

if [ -z $1 ]; then 

    run_one "box2d"
    run_one "code-load"
    run_one "crypto"
    run_one "deltablue"
    run_one "earley-boyer"
    run_one "gbemu"
    run_one "mandreel"
    run_one "navier-stokes"
    run_one "pdfjs"
    run_one "raytrace"
    run_one "regexp"
    run_one "richards"
    run_one "splay"
    run_one "typescript"
    run_one "zlib"

else
    run_one $1
fi
