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
#  @bug 4671838
#  @summary TTY: surprising ExceptionSpec.resolveEventRequest() wildcard results
#  @author Tim Bell
#
#  @run shell CatchPatternTest.sh
classname=CatchPatternTestTarg
createJavaFile()
{
    cat <<EOF > $classname.java.1
public class $classname {
    public void bark(int i) {
	System.out.println(" bark: " + i);
	switch (i) {
	case 0:
	    throw new IllegalArgumentException("IllegalArgumentException");
	case 1:
	    throw new ArithmeticException("ArithmeticException");
	case 2:
	    throw new IllegalMonitorStateException("IllegalMonitorStateException");
	case 3:
	    throw new IndexOutOfBoundsException("IndexOutOfBoundsException");
	default:
	    throw new Error("should not happen");
	}
    }
    public void loop(int max) {
	for (int i = 0; i <= max; i++) {
	    try {
		bark(i);
	    } catch(RuntimeException re) {
		System.out.println(" loop: " + re.getMessage() +
				   " caught and ignored.");
	    }
	}
    }
    public void partOne() {
        loop(2);
	System.out.println("partOne completed");
    }
    public void partTwo() {
        loop(3);
	System.out.println("partTwo completed");
    }
    public static void main(String[] args) {
	System.out.println("Howdy!");
        $classname my = new $classname();
	my.partOne();
	my.partTwo();
	System.out.println("Goodbye from $classname!");
    }
}
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
   #set -x
   cmd stop in ${classname}.main
   cmd stop in ${classname}.partTwo
   runToBkpt
   cmd ignore uncaught java.lang.Throwable
   cmd catch all java.lang.I*
   cmd cont
   cmd cont
   cmd cont
   cmd ignore all java.lang.I*
   cmd cont
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
#
jdbFailIfNotPresent "Exception occurred: java.lang.IllegalArgumentException"
jdbFailIfNotPresent "Exception occurred: java.lang.IllegalMonitorStateException"
jdbFailIfPresent "Exception occurred: ArithmeticException"
jdbFailIfPresent "Exception occurred: IndexOutOfBoundsException"
jdbFailIfPresent "Exception occurred: IllegalStateException"
jdbFailIfPresent "should not happen"
debuggeeFailIfNotPresent "partOne completed"
debuggeeFailIfNotPresent "partTwo completed"
#
pass
