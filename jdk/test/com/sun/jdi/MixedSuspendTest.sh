#!/bin/sh

#
# Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 6224859
# @key intermittent
# @summary JDWP: Mixing application suspends and debugger suspends can cause hangs
# @author Jim Holmlund
#
# @run build TestScaffold VMConnection TargetListener TargetAdapter
# @run shell MixedSuspendTest.sh

classname=MixedSuspendTarg

createJavaFile()
{
    cat <<EOF > $classname.java.1

import java.util.*;

public class $classname extends Thread {

    static volatile boolean started = true;
    static String lock = "startLock";

    public static void main(String[] args){
        System.out.println("Howdy from MixedSuspendTarg");

        MixedSuspendTarg mytarg = new MixedSuspendTarg();

        synchronized(lock) {
            mytarg.start();
            try {
                lock.wait();
            } catch(InterruptedException ee) {
            }
        }
        mytarg.suspend();
        bkpt();
        System.out.println("Debuggee: resuming thread");

        // If the bug occurs, this resume hangs in the back-end
        mytarg.resume();
        System.out.println("Debuggee: resumed thread");
        synchronized(lock) {
            started = false;
        }
        System.out.println("Debuggee: exitting, started = " + started);
    }

    public void run() {
        synchronized(lock) {
            lock.notifyAll();
        }
        while (true) {
            synchronized(lock) {
                if (!started) {
                    break;
                }
                int i = 0;
            }
        }

        System.out.println("Debuggee: end of thread");
    }

    static void bkpt() {
        //System.out.println("bkpt reached, thread = " + this.getName());
        int i = 0;   // @1 breakpoint
    }
}

EOF
}

dojdbCmds()
{
    setBkpts @1
    runToBkpt @1
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
## This test fails by timing out.
pass
