#!/bin/sh

#
# Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 4507088
#  @summary TTY: Add a comment delimiter to the jdb command set
#  @author Tim Bell
#  @run shell CommandCommentDelimiter.sh
#

createJavaFile()
{
    cat <<EOF > $classname.java.1
public class $classname {
    public static void main(String args[]) {
        System.out.print  ("Hello");
        System.out.print  (", ");
        System.out.print  ("world");
        System.out.println("!");
    }
}

EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
   #set -x
   cmd stop in $classname.main
   runToBkpt
   cmd step
   cmd \#
   cmd \#foo
   cmd 3 \#blah
   cmd \# connectors
   cmd step
   cmd cont
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
jdbFailIfPresent "Unrecognized command: '#'.  Try help..." 50
jdbFailIfPresent "Available connectors are" 50
pass
