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
#  @bug 4762765
#  @summary REGRESSION: jdb / jdi not stopping at some breakpoints and steps in j2sdk1.4.
#  @author Jim Holmlund
#
#  @key intermittent
#  @run shell JdbMissStep.sh

# These are variables that can be set to control execution

#pkg=untitled7
classname=JdbMissStep
compileOptions=-g
#java="java_g"

createJavaFile()
{
    cat <<EOF > $classname.java.1
public class $classname {
   
    public static void main(String args[]) {
        $classname dbb = new $classname();
        System.out.println("ANSWER IS: " + dbb.getIntVal());
        jj2 gus = new jj2();
        System.out.println("ANSWER2 IS: " + gus.getIntVal());
    }

    static int statVal;
    int intVal = 89;
    public int getIntVal() {
        return intVal;  //@ 1 breakpoint
    }

  static class jj2 {
    static int statVal;
    int intVal = 89;
    public int getIntVal() {
        return intVal;  //@1 breakpoint  line 20
    }
  }
}

EOF
}


# drive jdb by sending cmds to it and examining its output
dojdbCmds()
{
    cmd stop at $classname'$jj2:20'
    runToBkpt
    cmd step
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
