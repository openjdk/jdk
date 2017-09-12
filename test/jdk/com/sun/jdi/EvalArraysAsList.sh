#!/bin/sh
#
# Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 8160024
#  @summary jdb returns invalid argument count if first parameter to Arrays.asList is null
#
#  @run shell/timeout=300 EvalArraysAsList.sh
#
#  The test checks if evaluation of the expression java.util.Arrays.asList(null, "a")
#  works normally and does not throw an IllegalArgumentException.

classname=EvalArraysAsList

createJavaFile()
{
    cat <<EOF > $classname.java.1
public class $classname {
    public static void main(String[] args) {
        java.util.List<Object> l = java.util.Arrays.asList(null, "a");
        System.out.println("java.util.Arrays.asList(null, \"a\") returns: " + l);
        return;    // @1 breakpoint
    }
}
EOF
}

# drive jdb by sending cmds to it and examining its output
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1

    cmd eval "java.util.Arrays.asList(null, null)"
    jdbFailIfPresent "IllegalArgumentException" 3

    cmd eval "java.util.Arrays.asList(null, \"a\")"
    jdbFailIfPresent "IllegalArgumentException" 3

    cmd eval "java.util.Arrays.asList(\"a\", null)"
    jdbFailIfPresent "IllegalArgumentException" 3
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
