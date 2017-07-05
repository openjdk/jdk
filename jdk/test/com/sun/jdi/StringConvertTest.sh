#!/bin/sh

#
# Copyright (c) 2002, 2014, Oracle and/or its affiliates. All rights reserved.
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
#  @bug 4511950 4843082
#  @summary 1. jdb's expression evaluation doesn't perform string conversion properly
#           2. TTY: run on expression evaluation
#  @author jim/suvasis mukherjee
#
#  @key intermittent
#  @run shell StringConvertTest.sh

#  Run this script to see the bug.  See comments at the end
#  of the .java file for info on what the bug looks like.

# These are variables that can be set to control execution

#pkg=untitled7
classname=StringConvertTest
compileOptions=-g
#java=java_g
#mode=-Xcomp

#jdbOptions=-dbgtrace
createJavaFile()
{
    cat <<EOF > $1.java.1

class $classname {
    String me;
    static JJ1 x1;
    static JJ2 x2;
    static JJ2[] x3 = new JJ2[2];
    static String x4 = "abc";
    static int ii = 89;
    static String grower = "grower";
    static StringBuffer sbGrower = new StringBuffer("sbGrower");
    int ivar = 89;
    $classname(String xx) {
        me = xx;
    }

    static String fred() {
        return "a static method";
    }

    void  gus() {
        int gusLoc = 1;
        StringBuffer sbTim = new StringBuffer("tim");
        int kk = 1;                          //@1 breakpoint
    }

    static String growit(String extra) {
        grower += extra;
        return grower;
    }

    static String sbGrowit(String extra) {
        sbGrower.append(extra);
        return sbGrower.toString();
    }

    public static void main(String[] args) {
        x1 = new JJ1("first JJ1");
        x2 = new JJ2("first JJ2");
        x3[0] = new JJ2("array0");
        x3[1] = new JJ2("array1");
        $classname  loc1 = new $classname("first me");
        
        // These just show what output should look like
        System.out.println("x1 = " + x1);
        System.out.println("x2 = " + x2);
        System.out.println("x3.toString = " + x3.toString());
        System.out.println("x4.toString = " + x4.toString());

        // Dont want to call growit since it would change
        // the value.

        System.out.println("loc1 = " + loc1);
        System.out.println("-" + loc1);
        loc1.gus();
     }

  // This does not have a toString method
  static class JJ1 {
    String me;

    JJ1(String whoAmI) {
        me = whoAmI;
    }
  }

  // This has a toString method
  static class JJ2 {
    String me;

    JJ2(String whoAmI) {
        me = whoAmI;
    }
    public String toString() {
        return me;
    }

    public int meth1() {
        return 89;
    }
  }
}

EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1

    # Each print without the 'toString()' should print the
    # same thing as the following print with the toString().
    # The print 1s are just spacers

    cmd print $classname.x1
    cmd print "$classname.x1.toString()"
    cmd print 1

    cmd print $classname.x2
    cmd print "$classname.x2.toString()"
    cmd print 1

    # An unreported bug: this isn't handled correctly.
    # If we uncomment this line, we will get an 'instance of...'  line
    # which will cause the test to fail.
    #cmd print "(Object)($classname.x3)"
    cmd print "((Object)$classname.x3).toString()"
    cmd print 1

    cmd print $classname.x4
    cmd print "$classname.x4.toString()"
    cmd print 1

    # Make sure jdb doesn't call a method multiple times.
    cmd print "$classname.growit(\"xyz\")"
    cmd eval  "$classname.sbGrower.append(\"xyz\")"
    cmd print 1

    cmd eval "sbTim.toString()"
    cmd print 1

    cmd print this
    cmd print "this.toString()"
    cmd print 1

    # A possible bug is that this ends up with multiple "s
    cmd print '"--" + '$classname.x1
    cmd print 1

    # This too
    cmd print "$classname.x4 + 2"
    cmd print 1

    cmd print "this.ivar"
    cmd print gusLoc
    cmd print 1
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
jdbFailIfPresent '""'
jdbFailIfPresent 'instance of'
jdbFailIfPresent 'xyzxyz'
pass
