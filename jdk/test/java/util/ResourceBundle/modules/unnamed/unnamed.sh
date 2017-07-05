#
# Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
# @summary Test unnamed module to find resource bundles exported
#          from a named module 


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

mkdir -p mods/test

B=bundles
mkdir -p mods/$B
CLASSES="`find $TESTSRC/src/$B -name '*.java'`"
if [ "x$CLASSES" != x ]; then
    $JAVAC -g -d mods --module-source-path $TESTSRC/src $CLASSES
fi
PROPS="`(cd $TESTSRC/src/$B; find . -name '*.properties')`"
if [ "x$PROPS" != x ]; then
    for P in $PROPS
    do
      D=`dirname $P`
      mkdir -p mods/$B/$D
      cp $TESTSRC/src/$B/$P mods/$B/$D/
    done
fi

mkdir classes
$JAVAC -d classes $TESTSRC/Main.java

# access resource bundles that are exported private unconditionally
$JAVA -cp classes --module-path mods --add-modules bundles \
      Main de fr ja zh-tw en de

# --add-exports can't open resources 
$JAVA -cp classes --module-path mods --add-modules bundles \
      --add-opens bundles/jdk.test.internal.resources=ALL-UNNAMED \
      Main de fr ja zh-tw en de

exit $?
