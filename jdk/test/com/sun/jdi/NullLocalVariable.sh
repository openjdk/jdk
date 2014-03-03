#!/bin/sh

#
# Copyright (c) 2002, 2014 Oracle and/or its affiliates. All rights reserved.
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
#  @bug 4690242 4695338
#  @summary TTY: jdb throws NullPointerException when printing local variables
#  @author Tim Bell
#
#  @run shell NullLocalVariable.sh
#
classname=badscope

createJavaFile()
{
    cat <<EOF > $classname.java.1
public class badscope {
    public static final void main(String args[]) {
        try {
            System.out.println("hi!");               // @1 breakpoint
        } catch (Exception e) {         
            e.printStackTrace();
        } finally {
            System.out.println("done"); 
        }
    }
}
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
   #set -x
   cmd stop at badscope:4	; $sleepcmd
   runToBkpt			; $sleepcmd
   cmd next			; $sleepcmd
   cmd next			; $sleepcmd
   cmd locals			; $sleepcmd
   cmd allowExit cont
}

mysetup()
{
    compileOptions=-g
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
jdbFailIfPresent "Internal exception" 50
pass
