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
#  @bug 4705330
#  @summary Netbeans Fix and Continue crashes JVM
#  @author Jim Holmlund/Swamy Venkataramanappa
#  @run shell RedefineClearBreakpoint.sh

#  The failure occurs after a bkpt is set and then cleared
#  after a class redefinition, in a method that was EMCP.
#  This sequence creates a state in which subsequent operations
#  such as accessing local vars via JVMDI, can cause a hotspot crash.

# These are variables that can be set to control execution

compileOptions=-g
createJavaFile()
{
    cat <<EOF > $1.java.1

public class $1 {
        
        public $1() {
            int a=23;
            a=m(a);
            
        }
        public int m(int b){
            int bb=89;
            System.out.println("$1 -  constructor" + b); //@1 breakpoint
            return b*b;
        }
        
        public static void main(java.lang.String[] args) {
            new $1();        
            int jj = 0;   //@1 delete
        }
            
}
EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1
    redefineClass @1
    #cmd clear    NOTE this shows that jdb thinks the bpt is still set :-(
    setBkpts @1
    cmd next     # This is needed to make the crash happen at the 'locals' cmd
    cmd clear shtest:11
    cmd locals   # The crash happens here.
    #where
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
debuggeeFailIfPresent "Internal exception:"
pass
