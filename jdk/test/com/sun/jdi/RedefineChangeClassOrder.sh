#!/bin/sh

#
# Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 6270982
#  @summary Redefine a class so that the order of external classes in
#  the constant pool are changed.
#  @author dcubed
#
#  @run shell RedefineChangeClassOrder.sh

compileOptions=-g

# Would like to run this test with this option:
# -XX:-FailOverToOldVerifier

# Uncomment this to see the JDI trace
#jdbOptions=-dbgtrace

createJavaFile()
{
    cat <<EOF > $1.java.1

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class $1 {
    public static void main(String[] args) {
        new $1().hi(false);
        new $1().hi(true);  // @1 breakpoint
    }

    public void hi(boolean expected) {
        boolean isNewVersion = false; // @1 commentout
        // @1 uncomment boolean isNewVersion = true;

        if (expected == isNewVersion) {
            System.out.println("PASS: expected and isNewVersion match.");
        } else {
            System.out.println("FAIL: expected and isNewVersion do not match.");
            System.out.println("expected=" + expected
              + "  isNewVersion=" + isNewVersion);
        }

        Foo1 foo1 = new Foo1();  // @1 commentout
        foo1.hi();  // @1 commentout

        // This Hack code block exists to force some verification_type_info
        // objects of subtype Object_variable_info into the StackMapTable.
        //
        // In the redefined code, the above Foo1 code is effectively
        // moved after the Foo2 code below which causes things to be
        // layed out in a different order in the constant pool. The
        // cpool_index in the Object_variable_info has to be updated
        // in the redefined code's StackMapTable to refer to right
        /// constant pool index in the merged constant pool.
        Hack hack = getClass().getAnnotation(Hack.class);
        if (hack != null) {
            String class_annotation = hack.value();
            System.out.println("class annotation is: " + class_annotation);
            if (isNewVersion) {
                if (class_annotation.equals("JUNK")) {
                    System.out.println("class_annotation is JUNK.");
                } else {
                    System.out.println("class_annotation is NOT JUNK.");
                }
            }
        }

        Foo2 foo2 = new Foo2();
        foo2.hi();

        // @1 uncomment Foo1 foo1 = new Foo1();
        // @1 uncomment foo1.hi();
    }
}

class Foo1 {
    public void hi() {
        System.out.println("Hello from " + getClass());
    }
}

class Foo2 {
    public void hi() {
        System.out.println("Hello from " + getClass());
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface Hack {
    String value();
}

EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1
    redefineClass @1
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

debuggeeFailIfPresent 'FAIL:'
pass
