#!/bin/ksh -p

#
# Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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
#   @test       JITDebug.sh 1.7 03/09/05
#   @bug        4291701 4376819 4422312 4522770 4913748
#   @summary    Test JIT debugging - assure that launching on
#               uncaught exception works
#   @author     Tim Bell
#   Based on test/java/awt/TEMPLATE/AutomaticShellTest.sh
#
#   @run build TestScaffold VMConnection TargetListener TargetAdapter
#   @run compile -g JITDebug.java
#   @run shell JITDebug.sh

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

OS=`uname -s`
export TRANSPORT_METHOD
case "$OS" in
   SunOS | Linux | Darwin | AIX )
      PATHSEP=":"
      TRANSPORT_METHOD=dt_socket
      ;;

   Windows* | CYGWIN*)
      PATHSEP=";"
      TRANSPORT_METHOD=dt_shmem
      ;;

   # catch all other OSs
   * )
      echo "Unrecognized system!  $OS"
      fail "Unrecognized system!  $OS"
      ;;
esac
#
# Want this test to run standalone as well as in the harness, so do the
#  following to copy the test's directory into the harness's scratch directory
#  and set all appropriate variables:

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
   #if running standalone (no test harness of any kind), compile the
   #support files and the test case
   ${TESTJAVA}/bin/javac -d ${TESTCLASSES} \
            -classpath "$TESTJAVA/lib/tools.jar${PATHSEP}${TESTSRC}" \
            TestScaffold.java VMConnection.java TargetListener.java TargetAdapter.java
   ${TESTJAVA}/bin/javac  -d ${TESTCLASSES} \
            -classpath "$TESTJAVA/lib/tools.jar${PATHSEP}${TESTSRC}" -g \
            JITDebug.java
fi
echo "JDK under test is: $TESTJAVA"
#
CLASSPATH="$TESTJAVA/lib/tools.jar${PATHSEP}${TESTCLASSES}"
export CLASSPATH
CP="-classpath \"${CLASSPATH}\""
#
TARGETCLASS=JITDebug
RUNFLAGS='-showversion -DTRANSPORT_METHOD="${TRANSPORT_METHOD}"'
RUNFLAGS="-Dtest.classes=${TESTCLASSES} ${RUNFLAGS}"
#
echo ""
echo "Environment variable definitions are:"
echo ""
env | sort
echo ""
echo ""
#
echo "Starting test:"
echo ${TESTJAVA}/bin/java -DHANGINGJAVA_DEB ${RUNFLAGS} ${CP} ${TARGETCLASS}
eval ${TESTJAVA}/bin/java -DHANGINGJAVA_DEB ${RUNFLAGS} ${CP} ${TARGETCLASS}
status=$?
if [ $status -ne "0" ];
then fail "test failed for class=$TARGETCLASS!"
fi
#
# pass or fail the test based on status of the command
if [ $status -eq "0" ];
   then pass ""

   else fail "unspecified test failure"
fi
