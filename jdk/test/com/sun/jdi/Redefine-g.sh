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
#  @bug 4777868
#  @summary Compile with java -g, do a RedefineClasses, and you don't get local vars
#  @author Jim Holmlund
#
#  @run shell Redefine-g.sh
#pkg=untitled7

# Compile the first version without -g and the 2nd version with -g.
compileOptions=
compileOptions2=-g
#java=java_g

# Uncomment this to see the JDI trace
# jdbOptions=-dbgtrace

createJavaFile()
{
    cat <<EOF > $1.java.1

public class $1 {
  public $1() {
  }
  public static void main(String[] args) {
    int gus = 22;
    $1 kk = new $1();
    kk.m1("ab");
  }

  void m1(String p1) {
    int m1l1 = 1;
    System.out.println("m1(String) called");
    m1(p1, "2nd");
    // @1 uncomment System.out.println("Hello Milpitas!");
  }

  void m1(String p1, String p2) {
    int m1l2 = 2;
    System.out.println("m2" + p1 + p2);  // @1 breakpoint
  }

}
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1
    cmd where
    cmd locals

    redefineClass @1
    cmd where
    cmd locals

    cmd pop
    cmd where
    cmd locals

    cmd pop
    cmd where
    cmd locals

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

jdbFailIfNotPresent 'p1 = "ab"'
jdbFailIfNotPresent 'p2 = "2nd"'
jdbFailIfNotPresent 'm1l2 = 2'
jdbFailIfPresent    'm1l1'

jdbFailIfNotPresent 'args = instance of java.lang.String'
jdbFailIfNotPresent 'gus = 22'
jdbFailIfNotPresent 'kk = instance of shtest'
pass
