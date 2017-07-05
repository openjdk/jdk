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

#  @ test
#  This is a manual test.  The script isn't smart enough
#  to detect the correct ordering of the output since it
#  is produced by multiple threads and can be interleaved
#  in many different ways.
#
#  @bug 4629548
#  @summary Deferred StepRequests are lost in multithreaded debuggee
#  @author Jim Holmlund
#
#  @run shell/manual DeferredStepTest.sh

#  Run this script to see the bug.  See comments at the end
#  of the .java file for info on what the bug looks like.

# These are variables that can be set to control execution

#pkg=untitled7
classname=DeferredStepTest
#compileOptions=-g
#java=java_g
#mode=-Xcomp

createJavaFile()
{
    cat <<EOF > $classname.java.1
public class $classname {
  static class  jj1 implements Runnable {
    public void  run() {
        int count = 0;
        
        for ( int ii = 0; ii < 10; ii++) {  // line 6
            int intInPotato04 = 666;        // line 7
            ++count;                        // line 8; @1 breakpoint
            System.out.println("Thread: " + Thread.currentThread().getName());  // line 9
        }
    }
  }

  static class jj2 implements Runnable {
    public void run() {
        int count2 = 0;
        
        for (int ii = 0; ii < 10; ii++) {      // line 18
            String StringInPotato05 = "I am";  // line 19
            ++count2;                          // line 20; @1 breakpoint
            System.out.println("Thread: " + Thread.currentThread().getName());  // line 21
        }
    }
  }

  public static void  main(String argv[]) {
      System.out.println("Version = " + System.getProperty("java.version"));

      jj1 aRP = new jj1();
      jj2 asRP = new jj2();
      new Thread(aRP,  "jj1 *").start();
      new Thread(asRP, "jj2 **").start();
//    new Thread(aRP,  "jj3 ***").start();
//    new Thread(asRP, "jj4 ****").start();
  }
}

/****************************
To see this bug, do this

  jdb DeferredStep
  stop at DeferredStepTest$jj1:8
  stop at DeferredStepTest$jj2:20
  run
  next
  next
   :

********/

EOF
}

#sleepcmd="sleep 2"

# This is called to feed cmds to jdb.
dojdbCmds()
{
   #set -x
   # We can't use setBkpts because it can only set bkpts in one class :-(
   #setBkpts @1
   cmd stop at $classname'$jj1:8'
   cmd stop at $classname'$jj2:20'
   #cmd run; $sleepcmd
   runToBkpt @1
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
   cmd next; $sleepcmd
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

cat <<EOF
****************************************************************
This test should be run and checked manually.

If this works right, you should see StepEvents/Breakpoint events for lines
   8, 9, 6, 7, 8, 9, 6, ....   for thread jj11
and
  20, 21, 18, 19, 20, 21, 18, ... for thread jj2 

Since both threads are running at the same time, these
events can be intermixed.

The bug is that you will frequently see step events missing.
EG, you will see
  8, 9, 8
or
  20, 21, 20, 21
etc

============================================================
At some point you might get the msg 'Nothing suspended'
This is bug:
   4619349 Step Over fails in a multi threaded debuggee

Kill the test and rerun it if this happens.
****************************************************************

EOF
runit
#jdbFailIfPresent "Nothing suspended" 
#pass
