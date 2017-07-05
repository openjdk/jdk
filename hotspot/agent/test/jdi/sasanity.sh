#!/bin/ksh
#
# Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
#

# This script is used to run sanity check on vmStructs.
# Each SA class is checked against a given VM. "PASSED" is 
# printed if vmStructs are consistent. Else, "FAILED" is
# printed and an exception stack trace follows.

usage() {
    echo "usage: ./sasanity.sh <jdk>"
    echo "<jdk> is the 1.5 j2se directory against which you want to run sanity check"
    exit 1   
}

if [ "$1" == "" ]; then
    usage
fi

if [ "$1" == "-help" ]; then
    usage
fi

jdk=$1
OS=`uname`

if [ "$OS" != "Linux" ]; then
   OPTIONS="-Dsun.jvm.hotspot.debugger.useProcDebugger"
fi

javacp=$jdk/lib/sa-jdi.jar:./workdir

mkdir -p workdir
if [ SASanityChecker.java -nt ./workdir/SASanityChecker.class ] ; then
    $jdk/bin/javac -d ./workdir -classpath $javacp SASanityChecker.java
    if [ $? != 0 ] ; then
        exit 1
    fi
fi

if [ sagtarg.java -nt ./workdir/sagtarg.class ]; then
    $jdk/bin/javac -g  -classpath -d $workdir sagtarg.java
    if [ $? != 0 ] ; then
        exit 1
    fi
fi

tmp=/tmp/sagsetup
rm -f $tmp
$jdk/bin/java sagtarg > $tmp &
pid=$!
while [ ! -s $tmp ] ; do
  # Kludge alert!
  sleep 2
done

$jdk/bin/java -showversion ${OPTIONS} -classpath $javacp SASanityChecker $pid
kill -9 $pid
