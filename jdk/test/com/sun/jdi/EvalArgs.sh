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

#  @test
#  @bug 4663146
#  @summary Arguments match no method error 
#  @author Jim Holmlund/Suvasis
#
#  @run shell/timeout=300 EvalArgs.sh

#  The bug is that, for example, if a String is passed
#  as an arg to a func where an Object is expected,
#  the above error occurs.  jdb doesnt notice that this is
#  legal because String is an instance of Object.


# These are variables that can be set to control execution

#pkg=untitled7
classname=EvalArgs
#compileOptions=-g
#java="java_g"

createJavaFile()
{
    cat <<EOF > $classname.java.1
public class $classname {

    static jj1 myjj1;
    static jj2 myjj2;
    static oranges myoranges;
    static boolean jjboolean = true;
    static byte    jjbyte = 1;
    static char    jjchar = 'a';
    static double  jjdouble = 2.2;
    static float   jjfloat = 3.1f;
    static int     jjint = 4;
    static long    jjlong = 5;
    static short   jjshort = 6;
    static int[]   jjintArray = {7, 8};
    static float[] jjfloatArray = {9.1f, 10.2f};


    public static void main(String args[]) {
        myjj1 = new jj1();
        myjj2 = new jj2();
        myoranges = new oranges();

        // prove that these work
        System.out.println( ffjj1(myjj1));
        System.out.println( ffjj1(myjj2));

        System.out.println("$classname.ffoverload($classname.jjboolean) = " + 
                            $classname.ffoverload($classname.jjboolean));
        System.out.println("$classname.ffoverload($classname.jjbyte) = " + 
                            $classname.ffoverload($classname.jjbyte));
        System.out.println("$classname.ffoverload($classname.jjchar) = " + 
                            $classname.ffoverload($classname.jjchar));
        System.out.println("$classname.ffoverload($classname.jjdouble) = " + 
                            $classname.ffoverload($classname.jjdouble));


        //This doesn't even compile
        //System.out.println( "ffintArray(jjfloatArray) = " + ffintArray(jjfloatArray));
        gus();
    }

    static void gus() {
        int x = 0;             // @1 breakpoint
    }

    public static String ffjj1(jj1 arg) {
        return arg.me;
    }
    
    public static String ffjj2(jj2 arg) {
        return arg.me;
    }
    
    static String ffboolean(boolean p1) {
        return "ffbool: p1 = " + p1;
    }

    static String ffbyte(byte p1) {
        return "ffbyte: p1 = " + p1;
    }
        
    static String ffchar(char p1) {
        return "ffchar: p1 = " + p1;
    }
        
    static String ffdouble(double p1) {
        return "ffdouble: p1 = " + p1;
    }
        
    static String fffloat(float p1) {
        return "fffloat: p1 = " + p1;
    }
        
    static String ffint(int p1) {
        return "ffint: p1 = " + p1;
    }
        
    static String fflong(long p1) {
        return "fflong: p1 = " + p1;
    }
        
    static String ffshort(short p1) {
        return "ffshort: p1 = " + p1;
    }
        
    static String ffintArray(int[] p1) {
        return "ffintArray: p1 = " + p1;
    }

    // Overloaded funcs
    public static String ffoverload(jj1 arg) {
        return arg.me;
    }
    
    static String ffoverload(boolean p1) {
        return "ffoverload: boolean p1 = " + p1;
    }
/***        
    static String ffoverload(byte p1) {
        return "ffoverload: byte p1 = " + p1;
    }
***/        
    static String ffoverload(char p1) {
        return "ffoverload: char p1 = " + p1;
    }

    static String ffoverload(double p1) {
        return "ffoverload: double p1 = " + p1;
    }

    static String ffoverload(float p1) {
        return "ffoverload: float p1 = " + p1;
    }
/***        
    static String ffoverload(int p1) {
        return "ffoverload: int p1 = " + p1;
    }
***/        
    static String ffoverload(long p1) {
        return "ffoverload: long p1 = " + p1;
    }

    static String ffoverload(short p1) {
        return "ffoverload: short p1 = " + p1;
    }
      
    static String ffoverload(int[] p1) {
        return "ffoverload: int array p1 = " + p1;
    }

  static class jj1 {
    String me;
    jj1() {
        me = "jj1name";
    }
    public String toString() {
        return me;
    }
    
  }

  static class jj2 extends jj1 {
    jj2() {
        super();
        me = "jj2name";
    }
  }

  static class oranges {
    oranges() {
    }
  }
}



EOF
}

# drive jdb by sending cmds to it and examining its output
dojdbCmds()
{
    setBkpts @1
    runToBkpt @1

    # verify that it works ok when arg types are the same as
    # the param types
    cmd eval "$classname.ffboolean($classname.jjboolean)"
    cmd eval "$classname.ffbyte($classname.jjbyte)"
    cmd eval "$classname.ffchar($classname.jjchar)"
    cmd eval "$classname.ffdouble($classname.jjdouble)"
    cmd eval "$classname.fffloat($classname.jjfloat)"
    cmd eval "$classname.ffint($classname.jjint)"
    cmd eval "$classname.fflong($classname.jjlong)"
    cmd eval "$classname.ffshort($classname.jjshort)"
    cmd eval "$classname.ffintArray($classname.jjintArray)"
    cmd eval "$classname.ffjj1($classname.myjj1)"

    # Provide a visual break in the output
    cmd print 1

    # Verify mixing primitive types works ok 
    # These should work even though the arg types are
    # not the same because there is only one
    # method with each name.
    cmd eval "$classname.ffbyte($classname.jjint)"
    cmd eval "$classname.ffchar($classname.jjdouble)"
    cmd eval "$classname.ffdouble($classname.jjfloat)"
    cmd eval "$classname.fffloat($classname.jjshort)"
    cmd eval "$classname.ffint($classname.jjlong)"
    cmd eval "$classname.fflong($classname.jjchar)"
    cmd eval "$classname.ffshort($classname.jjbyte)"

    cmd print 1

    # Verify that passing a subclass object works
    cmd eval "$classname.ffjj1($classname.myjj2)"
    cmd eval "$classname.myjj1.toString().equals("jj1name")"

    cmd print 1

    # Overloaded methods.  These should pass
    # because there is an exact  match.
    cmd eval "$classname.ffoverload($classname.jjboolean)"

    cmd eval "$classname.ffoverload($classname.jjchar)"
    cmd eval "$classname.ffoverload($classname.jjdouble)"
    cmd eval "$classname.ffoverload($classname.jjfloat)"
    cmd eval "$classname.ffoverload($classname.jjlong)"
    cmd eval "$classname.ffoverload($classname.jjshort)"
    cmd eval "$classname.ffoverload($classname.jjintArray)"
    cmd eval "$classname.ffoverload($classname.myjj1)"
    cmd eval "$classname.ffoverload($classname.myjj2)"
    jdbFailIfPresent "Arguments match no method"

    cmd print 1
    cmd print '"These should fail with msg Arguments match multiple methods"'

    # These overload calls should fail because ther
    # isn't an exact match and jdb isn't smart  enough
    # to figure out which of several possibilities
    # should be called
    cmd eval "$classname.ffoverload($classname.jjbyte)"
    jdbFailIfNotPresent "Arguments match multiple methods" 3

    cmd eval "$classname.ffoverload($classname.jjint)"
    jdbFailIfNotPresent "Arguments match multiple methods" 3

    cmd print 1
    cmd print '"These should fail with InvalidTypeExceptions"'

    cmd eval "$classname.ffboolean($classname.jjbyte)"
    jdbFailIfNotPresent "InvalidTypeException" 3

    cmd eval "$classname.ffintArray($classname.jjint)"
    jdbFailIfNotPresent "InvalidTypeException" 3

    cmd eval "$classname.ffintArray($classname.jjfloatArray)"
    jdbFailIfNotPresent "InvalidTypeException" 3

    cmd eval "$classname.ffjj2($classname.myjj1)"
    jdbFailIfNotPresent "InvalidTypeException" 3

    cmd eval "$classname.ffjj2($classname.myoranges)"
    jdbFailIfNotPresent "InvalidTypeException" 3
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
