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

#
#  @test
#  @bug 4422141 4695338
#  @summary TTY: .length field for arrays in print statements in jdb not recognized
#           TTY: dump <ArrayReference> command not implemented.
#  @author Tim Bell
#
#  @key intermittent
#  @run shell ArrayLengthDumpTest.sh
#
classname=ArrayLengthDumpTarg

createJavaFile()
{
    cat <<EOF > $classname.java.1
class $classname {
    static final int [] i = {0,1,2,3,4,5,6};
    String [] s = {"zero", "one", "two", "three", "four"};
    String [][] t = {s, s, s, s, s, s, s, s, s, s, s};
    int length = 5;

    public void bar() {
    }

    public void foo() {
        ArrayLengthDumpTarg u[] = { new ArrayLengthDumpTarg(),
                                    new ArrayLengthDumpTarg(),
                                    new ArrayLengthDumpTarg(),
                                    new ArrayLengthDumpTarg(),
                                    new ArrayLengthDumpTarg(),
                                    new ArrayLengthDumpTarg() };
        int k = u.length;
        System.out.println("        u.length is: " + k);
        k = this.s.length;
        System.out.println("   this.s.length is: " + k);
        k = this.t.length;
        System.out.println("   this.t.length is: " + k);
        k = this.t[1].length;
        System.out.println("this.t[1].length is: " + k);
        k = i.length;
        System.out.println("        i.length is: " + k);
        bar();                      // @1 breakpoint
    }

    public static void main(String[] args) {
        ArrayLengthDumpTarg my = new ArrayLengthDumpTarg();
        my.foo();
    }
}
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
   setBkpts @1
   runToBkpt @1
   cmd dump this
   cmd dump this.s.length
   cmd dump this.s
   cmd dump this.t.length
   cmd dump this.t[1].length
   cmd dump ArrayLengthDumpTarg.i.length
   cmd dump this.length
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
#
# Test the fix for 4690242:
#
jdbFailIfPresent "No instance field or method with the name length in" 50
jdbFailIfPresent "No static field or method with the name length" 50
#
# Test the fix for 4695338:
#
jdbFailIfNotPresent "\"zero\", \"one\", \"two\", \"three\", \"four\"" 50
pass
