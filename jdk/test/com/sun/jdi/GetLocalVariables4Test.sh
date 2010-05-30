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

#
#  @test
#  @bug 4070747 4486859
#  @summary Compiler fails to generate local var tbl entry for exception passed to catch
#  @author Tim Bell
#
#  @run shell GetLocalVariables4Test.sh
#
classname=GetLocalVariables4Targ

createJavaFile()
{
    cat <<EOF > $classname.java.1
public class GetLocalVariables4Targ {
    public static void main(String[] args) {
        System.out.println("Howdy!");
	int i = 0;
	try {
	    i = 16 / i;
	} catch(Exception e) {
	    System.out.println("e should be visible");    // @1 breakpoint
	}
        System.out.println("Goodbye from GetLocalVariables4Targ!");
    }
}
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
   setBkpts @1
   runToBkpt @1
   cmd locals
   cmd cont
}

mysetup()
{
    compileOptions="-g"
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

mysetup

runit
jdbFailIfNotPresent "e = instance of java.lang.ArithmeticException"
debuggeeFailIfNotPresent "Howdy"
debuggeeFailIfNotPresent "Goodbye from GetLocalVariables4Targ"
pass
