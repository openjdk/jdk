#!/bin/sh

#
# Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 4689395
#  @summary "step over" after a class is redefined acts like "step out"
#  @author Jim Holmlund
#  @key intermittent
#  @run shell RedefineStep.sh
#

#pkg=untitled7
classname=gus1
compileOptions=-g
#java=java_g

# Uncomment this to see the JDI trace
#jdbOptions=-dbgtrace

createJavaFile()
{
    cat <<EOF > $1.java.1

public class $1 {
    static int counter;
    static public void main(String[] args) {
       $1 mine = new $1();
       mine.a1(10);
       System.out.println("done");  // should not see this
    }

    public void a1(int p1) {
        System.out.println("jj0");   // @1 breakpoint   line 10
        a2();
        System.out.println("jj3");    // @1 delete
    }
    public void a2() {
        System.out.println("a2");
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

    cmd next
    cmd next
    cmd next
    cmd next
    cmd next
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

jdbFailIfPresent 'should not see this'
pass
