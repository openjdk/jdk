#!/bin/sh

#
# Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 4724076
#  @summary Redefine does not work in for/while loop
#  @author Jim Holmlund/Swamy Venkataramanappa
#
#  The failure occurs when a method is active and
#  a method that it calls multiple times is redefined
#  more than once.
#  @run shell/timeout=240 RedefineMulti.sh

compileOptions=-g
#java=java_g

createJavaFile()
{
    cat <<EOF > $1.java.1

public class $1 {

    String field1;
    String field2;

    // The first time thru the loop in start,
    // "Before update..." should be printed.
    // After the first redefine, "After update..." should be printed
    // After the 2nd redefine, "abcde..." should be printed.
    // The bug is that "After update..." is printed instead because
    // stat() calls version 2 of doSomething() instead of
    // version 3.
    private void doSomething()  {
        System.out.println("Before update...");  // @1 commentout
        // @1 uncomment System.out.println("After update...");  // @2 commentout
        // @2 uncomment System.out.println("abcde...");
    }

    public void start() {
        for (int i=0; i < 3; i++)   {
            doSomething();      // @1 breakpoint here  line 16
            System.out.println("field1 = " + field1);
            System.out.println("field2 = " + field2);
        }
        // Redefinex myx = new Redefinex();
        //  for (int i = 0; i < 5; i++) {
        //    myx.methodx1();                     // line 22
        //    System.out.println("fieldx1 = " + myx.fieldx1);
        //    System.out.println("fieldx2 = " + myx.fieldx2);
        //  }
    }

    public static void main(String[] args) {
        $1 xxx = new $1();
        xxx.field1 = "field1";
        xxx.field2 = "field2";
        xxx.start();
    }
}

class Redefinex {
    public String fieldx1;
    public String fieldx2;

    Redefinex() {
        fieldx1 = "fieldx1";
        fieldx2 = "fieldx2";
    }

    public void methodx1() {
        System.out.println("redefinex 1");
        //System.out.println("redefinex 2");
        //System.out.println("redefinex 3");
    }
     
}

    /*********
Steps to reproduce this problem:
   a. add line breakpoint  in start()
   b. debug
   c. when breakpoint is hit, type continue. You should see output
"Before update..."
   d. change "Before update" to  "After update"
   e. redefine,  and set line breakpoint (see step a)
   f. type continue. You should see output "After update"
   g. change "After update" to "abcde"
   h. redefine, and set line breakpoint (see step a)
   i.  type continue. The output is shown as "After update"

   j. to see "abcde" output,  users will have to pop the stack, and
re-execute method start().
    ************/
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1
    contToBkpt
    redefineClass @1
    setBkpts @1
    contToBkpt
    redefineClass @2
    cmd cont
    cmd quit
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
debuggeeFailIfPresent "Internal exception:"
debuggeeFailIfNotPresent "abcde"
pass
