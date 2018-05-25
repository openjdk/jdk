#!/bin/sh

#
# Copyright (c) 2004, 2018, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 6202891
#  @summary TTY: Add support for method exit event return values to jdb
#  @author Jim Holmlund
#  @run shell JdbMethodExitTest.sh

# These are variables that can be set to control execution

#pkg=untitled7
classname=JdbMethodExitTest
compileOptions=-g
#java="java_g"
#set -x

createJavaFile()
{
    cat <<EOF > $classname.java.1
import java.util.*;
import java.net.URLClassLoader;
import java.net.URL;

/*
 * This tests the jdb trace command
 */

class $classname {
    // These are the values that will be returned by the methods
    static URL[] urls = new URL[1];
    public static byte      byteValue = 89;
    public static char      charValue = 'x';
    public static double    doubleValue = 2.2;
    public static float     floatValue = 3.3f;
    public static int       intValue = 1;
    public static short     shortValue = 8;
    public static boolean   booleanValue = false;

    public static Class       classValue = Object.class;
    public static ClassLoader classLoaderValue;
    {
        try {
            urls[0] = new URL("file:/foo");
        } catch (java.net.MalformedURLException ee) {
        }
        classLoaderValue = new URLClassLoader(urls);
    }

    public static Thread      threadValue;
    public static ThreadGroup threadGroupValue;
    public static String      stringValue = "abc";
    public static int[]       intArrayValue = new int[] {1, 2, 3};

    public static $classname  objectValue = 
        new $classname();
    public String ivar = stringValue;

    // These are the instance methods
    public byte i_bytef()            { return byteValue; }
    public char i_charf()            { return charValue; }
    public double i_doublef()        { return doubleValue; }
    public float i_floatf()          { return floatValue; }
    public int i_intf()              { return intValue; }
    public short i_shortf()          { return shortValue; }
    public boolean i_booleanf()      { return booleanValue; }
    public String i_stringf()        { return stringValue; }
    public Class i_classf()          { return classValue; }
    public ClassLoader i_classLoaderf()
                                     { return classLoaderValue; }
    public Thread i_threadf()        { return threadValue = Thread.currentThread(); }
    public ThreadGroup i_threadGroupf()  
                                     { return threadGroupValue = threadValue.getThreadGroup(); }
    public int[] i_intArrayf()       { return intArrayValue; }
    public Object i_nullObjectf()    { return null; }
    public Object i_objectf()        { return objectValue; }
    public void i_voidf()            {}

    static void doit($classname xx) {

        xx.i_bytef();
        xx.i_charf();
        xx.i_doublef();
        xx.i_floatf();
        xx.i_intf();
        xx.i_shortf();
        xx.i_booleanf();
        xx.i_stringf();
        xx.i_intArrayf();
        xx.i_classf();
        xx.i_classLoaderf();
        xx.i_threadf();
        xx.i_threadGroupf();
        xx.i_nullObjectf();
        xx.i_objectf();
        xx.i_voidf();

        // Prove it works for native methods too
        StrictMath.sin(doubleValue);
        stringValue.intern();
    }

    public static void bkpt() {
       int i = 0;     //@1 breakpoint
    }

    public static String traceMethods() {
        return "traceMethods";
    }

    public static String traceMethods1() {
        return "traceMethods1";
    }

    public static String traceExits() {
        return "traceExits";
    }

    public static String traceExits1() {
        return "traceExits1";
    }

    public static String traceExit() {
        return "traceExit";
    }

    public static String traceExit1() {
        return "traceExit1";
    }

    public static void main(String[] args) {
        // The debugger will stop at the start of main,
        // enable method exit events, and then do
        // a resume.

        $classname xx = new $classname();
        System.out.println("threadid="+Thread.currentThread().getId());
        bkpt();

        // test all possible return types
        doit(xx);
        bkpt();
        
       // test trace methods
       traceMethods();

       // test trace go methods
       traceMethods1();
       bkpt();

       // test trace method exits
       traceExits();

       // test trace method exits
       traceExits1();
       bkpt();
       
       // test trace method exit
       traceExit();

       // test trace method exit
       traceExit1();
       bkpt();
       
    }
}
EOF
}


# drive jdb by sending cmds to it and examining its output
dojdbCmds()
{
    setBkpts @1

    # test all possible return types
    runToBkpt @1
    debuggeeMatchRegexp "s/threadid=\(.*\)/\1/g"
    threadid=$?
    cmd untrace

    cmd trace methods
    cmd trace
    jdbFailIfNotPresent "trace methods in effect"

    cmd trace go methods
    cmd trace
    jdbFailIfNotPresent "trace go methods in effect"

    cmd trace method exits
    cmd trace
    jdbFailIfNotPresent "trace method exits in effect"

    cmd trace go method exits
    cmd trace
    jdbFailIfNotPresent "trace go method exits in effect"

    cmd trace method exit
    cmd trace
    jdbFailIfNotPresent "trace method exit in effect for JdbMethodExitTest.bkpt"

    cmd trace go method exit
    cmd trace
    jdbFailIfNotPresent "trace go method exit in effect for JdbMethodExitTest.bkpt"


    # trace exit of methods with all the return values
    # (but just check a couple of them)
    cmd trace go method exits $threadid
    cmd cont
    jdbFailIfNotPresent "instance of JdbMethodExitTest"
    jdbFailIfNotPresent "return value = 8"

    # Get out of bkpt back to the call to traceMethods
    cmd step up


    cmd trace methods $threadid
    cmd cont
    jdbFailIfNotPresent "Method entered:"
    cmd cont
    jdbFailIfNotPresent "Method exited: return value = \"traceMethods\""
    cmd step up


    cmd trace go methods $threadid
    cmd cont
    cmd cont
    cmd cont
    jdbFailIfNotPresent "Method entered: \"thread=main\", JdbMethodExitTest.traceMethods1"
    jdbFailIfNotPresent 'Method exited: .* JdbMethodExitTest.traceMethods1'
    cmd untrace
    cmd step up


    cmd trace method exits $threadid
    cmd cont
    jdbFailIfNotPresent "Method exited: return value = \"traceExits\""
    cmd untrace
    cmd step up


    cmd trace go method exits $threadid
    cmd cont
    jdbFailIfNotPresent 'Method exited: .* JdbMethodExitTest.traceExits1'
    cmd untrace
    cmd step up


    cmd step            # step into traceExit()
    cmd trace method exit $threadid
    cmd cont
    jdbFailIfNotPresent "Method exited: return value = \"traceExit\""
    cmd untrace
    cmd step up


    cmd step
    cmd step           # skip over setting return value in caller :-(
    cmd trace go method exit $threadid
    cmd cont
    jdbFailIfNotPresent 'Method exited: .*JdbMethodExitTest.traceExit1'
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
