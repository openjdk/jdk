#!/bin/sh

#
# Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6805864
# @summary Redefine an abstract class that is called via a concrete
#   class and via two interface objects and verify that the right
#   methods are called.
# @author Daniel D. Daugherty
#
# @key intermittent
# @run shell RedefineAbstractClass.sh

compileOptions=-g

# Uncomment this to see the JDI trace
#jdbOptions=-dbgtrace

createJavaFile()
{
    cat <<EOF > $1.java.1

public class $1 {
  public static void main(String[] args) {
    System.out.println("This is RedefineAbstractClass");

    MyConcreteClass foo = new MyConcreteClass();
    // do the work once before redefine
    foo.doWork();

    System.out.println("stop here for redefine");  // @1 breakpoint

    // do the work again after redefine
    foo.doWork();

    System.out.println("stop here to check results");  // @2 breakpoint
  }
}

interface MyInterface1 {
  public boolean checkFunc();
  public boolean isMyInterface1();
}

interface MyInterface2 {
  public boolean checkFunc();
  public boolean isMyInterface2();
}

abstract class MyAbstractClass implements MyInterface1, MyInterface2 {
  static int counter = 0;
  public boolean checkFunc() {
    counter++;
    System.out.println("MyAbstractClass.checkFunc() called.");
    // @1 uncomment System.out.println("This is call " + counter + " to checkFunc");
    return true;
  }
  public boolean isMyInterface1() {
    System.out.println("MyAbstractClass.isMyInterface1() called.");
    return true;
  }
  public boolean isMyInterface2() {
    System.out.println("MyAbstractClass.isMyInterface2() called.");
    return true;
  }
}

class MyConcreteClass extends MyAbstractClass {
  public void doWork() {
    // checkFunc() is called via invokevirtual here; MyConcreteClass
    // inherits via MyAbstractClass
    System.out.println("In doWork() calling checkFunc(): " + checkFunc());

    MyInterface1 if1 = (MyInterface1) this;
    // checkFunc() is called via invokeinterface here; this call will
    // use the first itable entry
    System.out.println("In doWork() calling if1.checkFunc(): " + if1.checkFunc());

    MyInterface2 if2 = (MyInterface2) this;
    // checkFunc() is called via invokeinterface here; this call will
    // use the second itable entry
    System.out.println("In doWork() calling if2.checkFunc(): " + if2.checkFunc());
  }
}

EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1
    setBkpts @2
    runToBkpt @1
    # modified version of redefineClass function
    vers=2
    abs_class=MyAbstractClass
    cmd redefine $pkgDot$abs_class $tmpFileDir/vers$vers/$abs_class.class
    cp $tmpFileDir/$classname.java.$vers \
        $tmpFileDir/$classname.java
    # end modified version of redefineClass function

    # this will continue to the second breakpoint
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

debuggeeFailIfNotPresent 'This is call 4 to checkFunc'
debuggeeFailIfNotPresent 'This is call 5 to checkFunc'
debuggeeFailIfNotPresent 'This is call 6 to checkFunc'
pass
