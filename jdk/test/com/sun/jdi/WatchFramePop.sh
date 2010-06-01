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
#  @bug 4546478
#  @summary Enabling a watchpoint can kill following NotifyFramePops
#  @author Jim Holmlund
#
#  @run shell WatchFramePop.sh

# These are variables that can be set to control execution

#pkg=untitled7
#classname=Untitled3
#compileOptions=-g
#java="java_g"

createJavaFile()
{
    cat <<EOF > $1.java.1

public class $1 {
    int watchMe;
    static public void main(String[] args) {
       System.out.println("In Main");
       $1 mine = new $1();
       mine.a1();
       System.out.println("Test completed");
    }

    public void a1() {
      a2();                            // @1 breakpoint. We'll do a watch of watchMe here
    }

    public void a2() {
      System.out.println("in a2");
      a3();
    }                                  // line 18

    public void a3() {
      System.out.println("in a3");     // After the watch, we'll run to here, line 21
      a4();                            // We'll do a 'next' to here.  The failure is that this
    }                                  // runs to completion, or asserts with java_g
                                       

    public void a4() {
      System.out.println("in a4");
    }

}
EOF
}

# drive jdb by sending cmds to it and examining its output
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1
    cmd watch  shtest.watchMe
    cmd stop in shtest.a3   # bkpt at line 17
    contToBkpt              # stops at the bkpt at 
    cmd next                # The bug is that this next runs to completion
                            # In which case, so does jdb
    cmd quit                # so we never get here.
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
jdbFailIfPresent 'The application exited'
pass
