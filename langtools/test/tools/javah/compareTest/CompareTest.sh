#!/bin/sh
#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

jdk=${1:-/opt/jdk/1.6.0}
javah=${jdk}/bin/javah
rtjar=${jdk}/jre/lib/rt.jar

# compile test
mkdir -p build/compareTest
/opt/jdk/1.7.0/bin/javac -classpath build/classes -d build/compareTest test/tools/javah/compareTest/*.java

# run test
/opt/jdk/1.7.0/bin/java -classpath build/compareTest:build/classes CompareTest $javah $rtjar 2>&1 | tee CompareTest.out

# show diffs for tests that failed
grep 'error:' CompareTest.out | sed -e 's|.*new/||' -e 's/\.h$//' -e 's|_|.|g' > CompareTest.classes.fail

for i in $(cat CompareTest.classes.fail) ; do 
	/opt/jdk/1.7.0/bin/java -classpath compareTest:build/classes CompareTest $javah $rtjar $i 
	diff -r old new 
done 

