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
# @bug 8044767 8139067
# @summary Test case for having resource bundles in a local named module
#          with no ResourceBundleProviders.


set -e

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA="$1"; shift
  COMPILEJAVA="${TESTJAVA}"
  TESTSRC="`pwd`"
  TESTCLASSES="`pwd`"
fi

JAVAC="$COMPILEJAVA/bin/javac"
JAR="$COMPILEJAVA/bin/jar"
JAVA="$TESTJAVA/bin/java"

rm -rf mods
mkdir -p mods/test

#
# Copy .properties files
#
PROPS="`(cd $TESTSRC/src; find . -name '*.properties')`"
if [ "x$PROPS" != x ]; then
    for P in $PROPS
    do
      D=`dirname $P`
      mkdir -p mods/$D
      cp $TESTSRC/src/$P mods/$D/
    done
fi

$JAVAC -g -d mods --module-source-path $TESTSRC/src \
       -cp mods/bundles `find $TESTSRC/src/test -name "*.java"`

# Create a jar to be added to the class path. Expected properties files are
# picked up from the class path.
rm -f extra.jar
mkdir -p classes
$JAR -cf extra.jar -C $TESTSRC/src/extra jdk/test/resources

STATUS=0

echo 'jdk.test.Main should load bundles local to named module "test".'
$JAVA -p mods -m test/jdk.test.Main de fr ja zh-tw en de || STATUS=1

echo "jdk.test.Main should load bundles from the jar file specified by the class-path."
$JAVA -cp extra.jar -p mods -m test/jdk.test.Main vi || STATUS=1


exit $STATUS
