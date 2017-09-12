#
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
#

# @test
# @bug 8180375 8185251
# @summary Tests resource bundles are correctly loaded from
#   modules through "<packageName>.spi.<simpleName>Provider" types.

set -e

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA="$1"; shift
  COMPILEJAVA="${TESTJAVA}"
  TESTSRC="`pwd`"
  TESTCLASSES="`pwd`"
fi

JAVAC="$COMPILEJAVA/bin/javac"
JAVA="$TESTJAVA/bin/java"

rm -rf mods
$JAVAC --module-source-path $TESTSRC/src -d mods --module m1,m2

mkdir -p mods/m1/p/resources mods/m2/p/resources
cp $TESTSRC/src/m1/p/resources/*.properties mods/m1/p/resources
cp $TESTSRC/src/m2/p/resources/*.properties mods/m2/p/resources

mkdir classes
$JAVAC -d classes $TESTSRC/src/Main.java

$JAVA -cp classes Main
