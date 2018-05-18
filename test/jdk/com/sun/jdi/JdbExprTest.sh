#!/bin/sh

#
# Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

#  @test
#  @bug 4660158
#  @author Staffan Larsen
#  @key intermittent
#  @run shell JdbExprTest.sh

# These are variables that can be set to control execution

#pkg=untitled7
classname=JdbExprTest
compileOptions=-g
#java="java_g"
#set -x

createJavaFile()
{
    cat <<EOF > $classname.java.1
import java.util.*;
import java.net.URLClassLoader;
import java.net.URL;

class $classname {

    static Long lMax = new Long(java.lang.Long.MAX_VALUE); // force initialization of Long class
    static long aLong;
    static int anInt;
    static boolean aBoolean;

    public static void bkpt() {
       int i = 0;     //@1 breakpoint
    }

    public static void main(String[] args) {
        bkpt();
    }
}
EOF
}


# drive jdb by sending cmds to it and examining its output
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1

    cmd print java.lang.Long.MAX_VALUE
    jdbFailIfNotPresent " \= 9223372036854775807" 3

    cmd print java.lang.Long.MIN_VALUE
    jdbFailIfNotPresent " \= \-9223372036854775808" 3

    cmd print 9223372036854775807L
    jdbFailIfNotPresent "9223372036854775807L = 9223372036854775807" 3
    cmd print 9223372036854775807
    jdbFailIfNotPresent "9223372036854775807 = 9223372036854775807" 3

    cmd print -9223372036854775807L
    jdbFailIfNotPresent "\-9223372036854775807L = \-9223372036854775807" 3
    cmd print -9223372036854775807
    jdbFailIfNotPresent "\-9223372036854775807 = \-9223372036854775807" 3

    cmd print -1
    jdbFailIfNotPresent "\-1 = \-1" 3
    cmd print 1L
    jdbFailIfNotPresent "1L = 1" 3
    cmd print -1L
    jdbFailIfNotPresent "\-1L = \-1" 3
    cmd print 0x1
    jdbFailIfNotPresent "0x1 = 1" 3

    cmd set $classname.aLong = 9223372036854775807L
    cmd print $classname.aLong
    jdbFailIfNotPresent "$classname.aLong = 9223372036854775807" 3

    cmd set $classname.anInt = java.lang.Integer.MAX_VALUE + 1
    cmd print $classname.anInt
    jdbFailIfNotPresent "$classname.anInt = \-2147483648" 3

    cmd set $classname.aLong = java.lang.Integer.MAX_VALUE + 1L
    cmd print $classname.aLong
    jdbFailIfNotPresent "$classname.aLong = 2147483648" 3

    cmd set $classname.anInt = 0x80000000
    jdbFailIfNotPresent "InvalidTypeException: .* convert 2147483648 to int" 3
    cmd set $classname.anInt = 0x8000000000000000L
    jdbFailIfNotPresent "java.lang.NumberFormatException: For input string: \"8000000000000000\"" 3

    cmd set $classname.anInt = 0x7fffffff
    jdbFailIfNotPresent "0x7fffffff = 2147483647" 3
    cmd set $classname.aLong = 0x7fffffffffffffff
    jdbFailIfNotPresent "0x7fffffffffffffff = 9223372036854775807" 3

    cmd print 3.1415
    jdbFailIfNotPresent "3.1415 = 3.1415" 3
    cmd print -3.1415
    jdbFailIfNotPresent "\-3.1415 = \-3.1415" 3
    cmd print 011
    jdbFailIfNotPresent "011 = 9" 3

    cmd set $classname.aBoolean = false
    jdbFailIfNotPresent "JdbExprTest.aBoolean = false = false" 3
    cmd print $classname.aBoolean
    jdbFailIfNotPresent "JdbExprTest.aBoolean = false" 3
    cmd print !$classname.aBoolean
    jdbFailIfNotPresent "JdbExprTest.aBoolean = true" 3

    cmd print ~1
    jdbFailIfNotPresent "~1 = -2" 3
}


mysetup()
{
    if [ -z "$TESTSRC" ] ; then
        TESTSRC=.
    fi

    for ii in . $TESTSRC $TESTSRC/.. ; do
        if [ -r "$ii/ShellScaffold.sh" ] ; then
            . $ii/ShellScaffold.sh
            break
        fi
    done
}

# You could replace this next line with the contents
# of ShellScaffold.sh and this script will run just the same.
mysetup

runit
jdbFailIfNotPresent "Breakpoint hit"
pass
