#!/bin/sh

#
# Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 8031195
#  @summary JDB allows evaluation of calls to static interface methods
#  @author Jaroslav Bachorik
#
#  @run shell/timeout=300 EvalInterfaceStatic.sh

#  The test exercises the ability to invoke static methods on interfaces.
#  Static interface methods are a new feature added in JDK8.
#
#  The test makes sure that it is, at all, possible to invoke an interface
#  static method and that the static methods are not inherited by extending
#  interfaces.

classname=EvalStaticInterfaces

createJavaFile()
{
    cat <<EOF > $classname.java.1
public interface $classname {
    static String staticMethod1() {
        return "base:staticMethod1";
    }

    static String staticMethod2() {
        return "base:staticMethod2";
    }

    public static void main(String[] args) {
        // prove that these work
        System.out.println("base staticMethod1(): " + $classname.staticMethod1());
        System.out.println("base staticMethod2(): " + $classname.staticMethod2());
        System.out.println("overridden staticMethod2(): " + Extended$classname.staticMethod2());
        System.out.println("base staticMethod3(): " + Extended$classname.staticMethod3());

        gus();
    }

    static void gus() {
        int x = 0;             // @1 breakpoint
    }
}

interface Extended$classname extends $classname {
    static String staticMethod2() {
        return "extended:staticMethod2";
    }

    static String staticMethod3() {
        return "extended:staticMethod3";
    }
}



EOF
}

# drive jdb by sending cmds to it and examining its output
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1

    cmd eval "$classname.staticMethod1()"
    jdbFailIfNotPresent "base:staticMethod1" 2

    cmd eval "$classname.staticMethod2()"
    jdbFailIfNotPresent "base:staticMethod2" 2

    cmd eval "Extended$classname.staticMethod1()"
    jdbFailIfPresent "base:staticMethod1" 2

    cmd eval "Extended$classname.staticMethod2()"
    jdbFailIfNotPresent "extended:staticMethod2" 2

    cmd eval "Extended$classname.staticMethod3()"
    jdbFailIfNotPresent "extended:staticMethod3" 2
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
pass
