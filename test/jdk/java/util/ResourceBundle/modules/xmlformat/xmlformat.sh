#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
# @summary Simple test case for ResourceBundle with modules;
#          ResourceBundle.getBundle caller is in module named "test" and
#          all resource bundles are in single module named "bundles".


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
PROPS="`(cd $TESTSRC/src/$B; find . -name '*.xml')`"
if [ "x$PROPS" != x ]; then
    for P in $PROPS
    do
      D=`dirname $P`
      mkdir -p mods/$B/$D
      cp $TESTSRC/src/$B/$P mods/$B/$D/
    done
fi

$JAVAC -g -d mods --module-source-path $TESTSRC/src \
       -cp mods/bundles `find $TESTSRC/src/test -name "*.java"`

$JAVA -p mods -m test/jdk.test.Main de fr ja zh-tw en de

exit $?
