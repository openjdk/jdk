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

#
#  @test
#  @bug 4467887 4913748
#  @summary TTY: NullPointerException at
#           com.sun.tools.jdi.MirrorImpl.validateMirrors
#  @author Tim Bell
#  @run shell NotAField.sh
#

createJavaFile()
{
    cat <<EOF > $classname.java.1
public class $classname {
    public static void main(String args[]) {
        System.out.println("Hello, world!");
    }
}

EOF
}

# This is called to feed cmds to jdb.
dojdbCmds()
{
   #set -x
   cmd stop in $classname.main
   runToBkpt
   #This works:
   cmd print "java.lang.Class.reflectionFactory.hashCode()"
   #This should result in a ParseException: ("No such field in ..."):
   cmd print "java.lang.Class.reflectionFactory.hashCode"
   cmd allowExit cont
}

mysetup()
{
   if [ -z "${TESTJAVA}" ] ; then
      # TESTJAVA is not set, so the test is running stand-alone.
      # TESTJAVA holds the path to the root directory of the build of the JDK
      # to be tested.  That is, any java files run explicitly in this shell
      # should use TESTJAVA in the path to the java interpreter.
      # So, we'll set this to the JDK spec'd on the command line.  If none
      # is given on the command line, tell the user that and use a default.
      # THIS IS THE JDK BEING TESTED.
      if [ -n "$1" ] ; then
             TESTJAVA=$1
         else
             TESTJAVA=$JAVA_HOME
      fi
      TESTSRC=.
      TESTCLASSES=.
   fi
   echo "JDK under test is: $TESTJAVA"

   if [ -z "$TESTSRC" ] ; then
        TESTSRC=.
   fi

   if [ -r $TESTSRC/ShellScaffold.sh ] ; then
        . $TESTSRC/ShellScaffold.sh 
   elif [ -r $TESTSRC/../ShellScaffold.sh ] ; then
        . $TESTSRC/../ShellScaffold.sh
   fi
}

# You could replace this next line with the contents
# of ShellScaffold.sh and this script will run just the same.
mysetup

runit
jdbFailIfNotPresent "com.sun.tools.example.debug.expr.ParseException" 50
pass
