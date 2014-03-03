#!/bin/sh

#
# Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 4788344
#  @summary RedefineClasses is an apparent no-op if instance method is final
#
#  @run shell RedefineFinal.sh

compileOptions=-g
compileOptions2=-g

# Uncomment this to see the JDI trace
#jdbOptions=-dbgtrace

createJavaFile()
{
    cat <<EOF > $1.java.1

public final class $1 {

    public int m1(int i) {
	// @1 uncomment System.out.println("I'm here");
	return m2(i, 1000);
    }

    public int m2(int i, int j) {
	if (i < 0 || j < 0) {	// @1 breakpoint
            throw new IllegalArgumentException();
	}
	return i+j;
    }

    $1() {
        m1(0);
        m1(0);
    }

    public static void main(String args[]) {
	new $1();
    }
}
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1
    redefineClass @1
    setBkpts @1
    contToBkpt
    cmd where
    cmd allowExit cont
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

jdbFailIfPresent 'obsolete'
pass
