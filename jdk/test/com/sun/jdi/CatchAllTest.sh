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
#  @bug 4749692
#  @summary REGRESSION: jdb rejects the syntax catch java.lang.IndexOutOfBoundsException
#  @author Tim Bell
#
#  @run shell CatchAllTest.sh
#
classname=CatchAllTestTarg

createJavaFile()
{
    cat <<EOF > $classname.java.1
class $classname {
    public void bar() {
        System.out.println("bar");        // @1 breakpoint
    }

    public static void main(String[] args) {
        CatchAllTestTarg my = new CatchAllTestTarg();
        my.bar();
    }
}
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
   setBkpts @1
   runToBkpt @1
   cmd catch           java.lang.IndexOutOfBoundsException
   cmd catch
   cmd ignore
   cmd ignore          java.lang.IndexOutOfBoundsException
   cmd catch  all      java.lang.IndexOutOfBoundsException
   cmd ignore all      java.lang.IndexOutOfBoundsException
   cmd catch  caught   java.lang.IndexOutOfBoundsException
   cmd ignore caught   java.lang.IndexOutOfBoundsException
   cmd catch  uncaught java.lang.IndexOutOfBoundsException
   cmd ignore uncaught java.lang.IndexOutOfBoundsException
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
jdbFailIfPresent "Usage: catch"
jdbFailIfPresent "Usage: ignore"
#
pass
