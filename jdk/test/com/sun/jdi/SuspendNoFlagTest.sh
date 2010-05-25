#!/bin/ksh -p

#
# Copyright (c) 2003, 2006, Oracle and/or its affiliates. All rights reserved.
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
#   @test       SuspendNoFlagTest.sh
#   @bug        4914611
#   @summary    Test for JDWP: -agentlib:jdwp=suspend=n hanging
#   @author     Kelly O'Hair
#   Based on test/java/awt/TEMPLATE/AutomaticShellTest.sh
#
#   @run compile -g HelloWorld.java
#   @run shell/timeout=60 SuspendNoFlagTest.sh
#

# Beginning of subroutines:
status=1

#Call this from anywhere to fail the test with an error message
# usage: fail "reason why the test failed"
fail() 
 { echo "The test failed :-("
   echo "$*" 1>&2
   echo "exit status was $status"
   exit $status
 } #end of fail()

#Call this from anywhere to pass the test with a message
# usage: pass "reason why the test passed if applicable"
pass() 
 { echo "The test passed!!!"
   echo "$*" 1>&2
   exit 0
 } #end of pass()

# end of subroutines

# The beginning of the script proper

TARGETCLASS="HelloWorld"
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
   #Deal with .class files:
   ${TESTJAVA}/bin/javac -d ${TESTCLASSES} \
            -classpath "${TESTCLASSES}" -g \
            ${TARGETCLASS}.java
fi
#
echo "JDK under test is: $TESTJAVA"
#
CP="-classpath \"${TESTCLASSES}\""
#
DEBUGEEFLAGS=
if [ -r $TESTCLASSES/@debuggeeVMOptions ] ; then
   DEBUGEEFLAGS=`cat $TESTCLASSES/@debuggeeVMOptions`
elif [ -r $TESTCLASSES/../@debuggeeVMOptions ] ; then
   DEBUGEEFLAGS=`cat $TESTCLASSES/../@debuggeeVMOptions`
fi
DEBUGEEFLAGS="$DEBUGEEFLAGS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n"

java=java
echo ${TESTJAVA}/bin/$java ${DEBUGEEFLAGS} ${CP} ${TARGETCLASS}
eval ${TESTJAVA}/bin/$java ${DEBUGEEFLAGS} ${CP} ${TARGETCLASS}
status=$?
echo "test status was: $status"
if [ $status -eq "0" ] ;
   then pass "status = 0 and no timeout occured"

   else fail "unspecified test failure (timed out or hung)"
fi
