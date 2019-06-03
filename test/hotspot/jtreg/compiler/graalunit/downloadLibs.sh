#!/bin/bash
#
# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

if [ -z "$1" ]; then
    echo use:
    echo '$0 <libs dir to download in>'
    echo
    exit 0
fi

LIBS_DIR=$1
mkdir -p ${LIBS_DIR}

LIBS="https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/asm-5.0.4.jar"
LIBS="$LIBS https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/asm-tree-5.0.4.jar"
LIBS="$LIBS https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/junit-4.12.jar"
LIBS="$LIBS https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/hamcrest-core-1.3.jar"
LIBS="$LIBS https://lafo.ssw.uni-linz.ac.at/pub/java-allocation-instrumenter/java-allocation-instrumenter.jar"

for l in ${LIBS} ;
do
   echo "Download $l"
   wget -P ${LIBS_DIR} $l
done
