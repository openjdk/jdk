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
#  @bug 4660756
#  @summary TTY: Need to clear source cache after doing a redefine class
#  @author Jim Holmlund
#  @run shell/timeout=240 RedefineTTYLineNumber.sh

#set -x
# These are variables that can be set to control execution

#pkg=untitled7
#classname=Untitled3
compileOptions=-g
#java=java_g

createJavaFile()
{
    cat <<EOF > $1.java.1

public class $1 {

  public void B() {
    System.out.println("in B");
    System.out.println("in B: @1 delete");
  }

  // line number sensitive!!! Next line must be line 10.
  public void A() {
    System.out.println("in A, about to call B");  // 11 before, 10 afterward
    System.out.println("out from B");
  }

  public static void main(String[] args) {
    $1 untitled41 = new $1();
    untitled41.A();
    System.out.println("done");
  }
}
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    cmd stop in shtest.A
    runToBkpt
    #jdbFailIfNotPresent "System\.out\.println" 3
    redefineClass @1
    cmd pop
    cmd stop in shtest.A
    contToBkpt
    #jdbFailIfNotPresent "System\.out\.println" 3
}


mysetup()
{
    if [ -z "$TESTSRC" ] ; then
        TESTSRC=.
    fi

    if [ -r $TESTSRC/ShellScaffold.sh ] ; then
        . $TESTSRC/ShellScaffold.sh 
    elif [ -r $TESTSRC/../ShellScaffold.sh ] ; then
        . $TESTSRC/../ShellScaffold.sh
    fi
}

# You could replace this next line with the contents
# of ShellScaffold.sh and this script will run just the same.
mysetup

runit
debuggeeFailIfPresent "Internal exception:"
pass
