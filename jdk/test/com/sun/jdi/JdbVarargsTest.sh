#!/bin/sh

#
# Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
#  @bug 4870984
#  @summary  JPDA: Add support for RFE 4856541 - varargs
#
#  @author jjh
#
#  @run shell JdbVarargsTest.sh

classname=JdbVarargsTest
createJavaFile()
{
    cat <<EOF > $classname.java.1
public class $classname {
   
    public static void main(String args[]) {
        int ii = 0; // @1 breakpoint

        // Call the varargs method so the bkpt will be hit
        varString(new String[] {"a", "b"});
    }

    static String varString(String... ss) {
        if (ss == null) {
            return "-null-";
        }
        if (ss.length == 0) {
            return "NONE";
        }
        String retVal = "";
        for (int ii = 0; ii < ss.length; ii++) {
            retVal += ss[ii];
        }
        return retVal;
    }

}
EOF
}


# drive jdb by sending cmds to it and examining its output
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1

    # check that 'methods' shows the ...
    cmd methods "$classname"

    # check that we can call with no args
    cmd eval  "$classname.varString();"

    # check that we can call with var args
    cmd eval "$classname.varString(\"aa\", \"bb\");"
    
    # check that we can stop in ...
    cmd stop in "$classname.varString(java.lang.String...)"
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
jdbFailIfNotPresent "NONE"
jdbFailIfNotPresent "aabb"
jdbFailIfNotPresent "$classname varString\(java\.lang\.String\.\.\.\)"
jdbFailIfNotPresent 'Breakpoint hit:.*varString\(\)'
pass
