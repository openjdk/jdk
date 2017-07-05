#!/bin/sh

#
# Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 6394084
#  @summary Redefine class can't handle addition of 64 bit constants in JDK1.5.0_05
#
#  @key intermittent
#  @run shell RedefineIntConstantToLong.sh

compileOptions=-g
compileOptions2=-g

# Uncomment this to see the JDI trace
#jdbOptions=-dbgtrace

createJavaFile()
{
    cat <<EOF > $1.java.1

public final class $1 {

    public long m1(int i) {
        long r=0;
        r = m2(i * 2); // @1 commentout
        // @1 uncomment      r =m2(i * 2L);
        return r;
    }

    public long m2(int j) {
        System.out.println(System.getProperty("line.separator") +
                           "**** public long m2(int j) with value: " + j);
        return j;
    }

    public long m2(long j) {
        System.out.println(System.getProperty("line.separator") +
                           "**** public long m2(long j) with value: " + j);
        return j;
    }

    public void doit() throws Exception {
        long r1 = 0;
        long r2;
        r1 = m1(1000);
        r2 = 0;         // @1 breakpoint
        r2 = m1(1000);
        if (r1 != r2) { // @1 breakpoint
             throw new Exception("FAILURE: Expected value: " + r1 + " Actual value: " + r2);
        } else {
             System.out.println("SUCCESS: Expected value: " + r1 + " Actual value: " + r2);
        }
    }

    public static void main(String args[]) throws Exception {
        new $1().doit();
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

jdbFailIfPresent 'FAILURE:'
pass
