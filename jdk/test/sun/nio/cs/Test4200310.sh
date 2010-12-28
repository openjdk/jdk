#
# Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4200310
# @summary make sure class files are not duplicated between rt.jar,
# charsets.jar, and localedata.jar
# @author Norbert Lindenberg
# @run shell Test4200310.sh

2>&1 $TESTJAVA/bin/jar -tf "$TESTJAVA/jre/lib/rt.jar" > class-list
2>&1 $TESTJAVA/bin/jar -tf "$TESTJAVA/jre/lib/charsets.jar" >> class-list
2>&1 $TESTJAVA/bin/jar -tf "$TESTJAVA/jre/lib/ext/localedata.jar" >> class-list
duplicates=`grep '\.class$' class-list | sort | uniq -d`

rm -f class-list
if [ "$duplicates" != "" ]; then
   echo FAILED: $duplicates are duplicated between rt.jar, charsets.jar, and localedata.jar
   exit 1
fi

exit 0
