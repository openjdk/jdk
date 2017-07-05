#!/bin/sh

#
# Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 4628760
#  @summary RedefineClasses gets assertion: "Should be a method entry in cpcache!"
#  @author jjh
#
#  @run shell/timeout=180 RedefineClasses.sh

# On windows, with b90, this gets this:
# assert(u2_at_bcp >= 0 && u2_at_bcp < old_cache->length(), "Bad cpcache index!")
#
# Error happened during: VM_RedefineClasses
#
# Error ID: D:/jdk1.4/hotspot\src\share\vm\prims\jvmdi_hotswap.cpp, 331

# On solaris, and on windows with 4559100 fixed, this test fails with:
#
# HotSpot Virtual Machine Error, assertion failure
# Please report this error at
# http://java.sun.com/cgi-bin/bugreport.cgi
#
# Java VM: Java HotSpot(TM) Client VM (1.4-internal-debug mixed mode)
#
# assert(old_cache->entry_at(u2_at_bcp)->is_method_entry(), "Should be a method entry in cpcache!")
#
# Error happened during: VM_RedefineClasses
#
# Error ID: M:\ws\m\b2\service_hs_baseline\src\share\vm\prims\jvmdi_hotswap.cpp, 335


# With -Xcomp on solaris this passes, but takes 2 minutes, thus the /timeout above.

# These are variables that can be set to control execution

java=java

createJavaFile()
{
    cat <<EOF > $1.java.1


import java.lang.Thread;
import java.util.HashMap;
import javax.swing.*;
import java.util.*;


public class $1 {
  int xxx = 20;
  //ThreadGroup k = new ThreadGroup("group");
  int i;

  public $1() {
  }

  public void a1() {
      a2();
  }

  public void a2() {
    a3();
  }

  public void a3() {
     System.out.println("out from a3");   // @1 breakpoint
     //System.out.println("hello world"); // @ 1 delete this isn't even necesary
  }
  public void a4() {
    System.out.println("in a4");
    int i = 2;
    int j = 3333;
    System.out.println("i + j = " + (i + j));
    System.out.println("out from a4");
    System.out.println("def");
    a1();
  }

  public void aa() {
    a4();
    System.out.println("out from aa");
  }


  public static void main(String[] args) {
    byte xyz[] = new byte[] { 'a', 'b', 'c' };

    int x1 = 100;
    x1 = 101;
    x1 = 102;
    x1 = 103;
    String m1 = "def";
    String m2 = "abc";
    String m3 = "def";

    int[] m = new int[] { 100, 200, 300 };

    $1 untitled31 = new $1();
    untitled31.aa();
  }
}

EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1
    runToBkpt
    cmd redefine $classname $tmpFileDir/$classname.class
    cmd redefine $classname $tmpFileDir/$classname.class
    cmd redefine $classname $tmpFileDir/$classname.class
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
pass
