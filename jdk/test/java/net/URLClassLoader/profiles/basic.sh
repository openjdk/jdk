#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8003255
# @compile -XDignore.symbol.file Basic.java Lib.java
# @summary Test that UnsupportedProfileException thrown when attempting to
#     load classes or resources from a JAR file with the Profile attribute
# @run shell basic.sh

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA=$1; shift
  COMPILEJAVA=$TESTJAVA
  TESTSRC=`pwd`
  TESTCLASSES=`pwd`
fi

echo "Creating GoodLib.jar ..."
echo "Profile: compact3" > good.mf
$COMPILEJAVA/bin/jar cvfm GoodLib.jar good.mf -C $TESTCLASSES lib

echo "Create BadLib.jar ..."
echo "Profile: badname" > bad.mf
$COMPILEJAVA/bin/jar cvfm BadLib.jar bad.mf -C $TESTCLASSES lib

# remove classes so that they aren't on the classpath
rm -rf $TESTCLASSES/lib

echo "Test with GoodLib.jar ..."
$TESTJAVA/bin/java -cp $TESTCLASSES Basic GoodLib.jar lib.Lib

echo "Test with BadLib.jar ..."
$TESTJAVA/bin/java -cp $TESTCLASSES Basic BadLib.jar lib.Lib

