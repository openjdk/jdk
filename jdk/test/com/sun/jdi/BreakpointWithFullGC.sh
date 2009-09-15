#!/bin/sh

#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

#  @test
#  @bug 6862295
#  @summary Verify breakpoints still work after a full GC.
#  @author dcubed (based on the test program posted to the following
#  Eclipse thread https://bugs.eclipse.org/bugs/show_bug.cgi?id=279137)
#
#  @run shell BreakpointWithFullGC.sh

compileOptions=-g
# Hijacking the mode parameter to make sure we use a small amount
# of memory and can see what GC is doing.
mode="-Xmx32m -verbose:gc"
# Force use of a GC framework collector to see the original failure.
#mode="$mode -XX:+UseSerialGC"

# Uncomment this to see the JDI trace
#jdbOptions=-dbgtrace

createJavaFile()
{
    cat <<EOF > $1.java.1

import java.util.ArrayList;
import java.util.List;

public class $1 {
    public static List<Object> objList = new ArrayList<Object>();

    private static void init(int numObjs) {
        for (int i = 0; i < numObjs; i++) {
            objList.add(new Object());
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println("top of loop");     // @1 breakpoint
            init(1000000);
            objList.clear();
            System.out.println("bottom of loop");  // @1 breakpoint
        }
        System.out.println("end of test");         // @1 breakpoint
    }
}

EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1

    # get to the first loop breakpoint
    runToBkpt
    # 19 "cont" commands gets us through all the loop breakpoints.
    # Use for-loop instead of while-loop to avoid creating processes
    # for '[' and 'expr'.
    for ii in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19; do
        contToBkpt
    done
    # get to the last breakpoint
    contToBkpt
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

# make sure we hit the first breakpoint at least once
jdbFailIfNotPresent 'System\..*top of loop'

# make sure we hit the second breakpoint at least once
jdbFailIfNotPresent 'System\..*bottom of loop'

# make sure we hit the last breakpoint
jdbFailIfNotPresent 'System\..*end of test'

# make sure we had at least one full GC
debuggeeFailIfNotPresent 'Full GC'

# check for error message due to thread ID change
debuggeeFailIfPresent \
    'Exception in thread "event-handler" java.lang.NullPointerException'

pass
