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

doUsage()
{
    cat <<EOF
    Run multivm.class using Serviceability Agent to talk to 2 pids
    simultaneousely. i.e, before detaching one attach another.
    Usage:  multivm.sh <jdk-pathname> <pid1> <pid2>

EOF
}

if [ $# = 4 ] ; then
    doUsage
    exit 1
fi

jdk=$1
javacp="$jdk/lib/sa-jdi.jar:$classesDir:$jdk/lib/tools.jar:$jdk/classes:./workdir"

mkdir -p workdir
if [ sagdoit.java -nt ./workdir/sagdoit.class ] ; then
    $jdk/bin/javac -d ./workdir -classpath $javacp sagdoit.java
    if [ $? != 0 ] ; then
        exit 1
    fi
fi
if [ multivm.java -nt ./workdir/multivm.class ] ; then
    $jdk/bin/javac -d ./workdir -classpath $javacp multivm.java
    if [ $? != 0 ] ; then
        exit 1
    fi
fi

$jdk/bin/java -Dsun.jvm.hotspot.jdi.ConnectorImpl.DEBUG -Dsun.jvm.hotspot.jdi.SAJDIClassLoader.DEBUG -Djava.class.path=$javacp multivm $2 $3
