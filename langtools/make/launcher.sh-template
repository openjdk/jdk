#!/bin/sh

#
# Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

mydir="`dirname $0`"
case `uname -s` in
    CYGWIN*)
      mydir=`cygpath -m $mydir`
      ;;
esac
mylib="$mydir/../lib"

# By default, put the jar file and its dependencies on the bootclasspath.
# This is always required on a Mac, because the system langtools classes
# are always on the main class path; in addition, it may be required on
# standard versions of JDK (i.e. using rt.jar and tools.jar) because some
# langtools interfaces are in rt.jar.
# Assume that the jar file being invoked lists all the necessary langtools
# jar files in its Class-Path manifest entry, so there is no need to search
# dependent jar files for additional dependencies.

if [ "$LANGTOOLS_USE_BOOTCLASSPATH" != "no" ]; then
   cp=`unzip -c "$mylib/#PROGRAM#.jar" META-INF/MANIFEST.MF |
       grep "Class-Path:" |
       sed -e 's|Class-Path: *||' -e 's|\([a-z]*\.jar\) *|'"$mylib"'/\1#PS#|g'`
   bcp="$mylib/#PROGRAM#.jar#PS#$cp"
fi

# tools currently assumes that assertions are enabled in the launcher
ea=-ea:com.sun.tools...

# Any parameters starting with -J are passed to the JVM.
# All other parameters become parameters of #PROGRAM#.

# Separate out -J* options for the JVM
# Unset IFS and use newline as arg separator to preserve spaces in args
DUALCASE=1  # for MKS: make case statement case-sensitive (6709498)
saveIFS="$IFS"
nl='
'
for i in "$@" ; do
   IFS=
   case $i in
   -J* )       javaOpts=$javaOpts$nl`echo $i | sed -e 's/^-J//'` ;;
   *   )       toolOpts=$toolOpts$nl$i ;;
   esac
   IFS="$saveIFS"
done
unset DUALCASE

IFS=$nl
"#TARGET_JAVA#" "${bcp:+-Xbootclasspath/p:"$bcp"}" ${ea} ${javaOpts} -jar "${mylib}/#PROGRAM#.jar" ${toolOpts}
