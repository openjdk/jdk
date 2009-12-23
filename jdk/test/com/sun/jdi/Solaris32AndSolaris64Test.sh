#!/bin/ksh -p

#
# Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#
#   @test       Solaris32AndSolaris64Test.sh
#   @bug        4478312 4780570 4913748 6730273
#   @summary    Test debugging with mixed 32/64bit VMs.
#   @author     Tim Bell
#   Based on test/java/awt/TEMPLATE/AutomaticShellTest.sh
#
#   @run build TestScaffold VMConnection TargetListener TargetAdapter
#   @run compile -g FetchLocals.java
#   @run compile -g DataModelTest.java
#   @run shell/timeout=240 Solaris32AndSolaris64Test.sh DataModelTest
#   @run shell/timeout=240 Solaris32AndSolaris64Test.sh FetchLocals

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

if [ $# = 0 ] ; then
    echo "Error: no testname specified on cmd line"
    exit 1
fi
testName=$1
shift

#Set appropriate jdk 

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
          echo "no JDK specified on command line so using JAVA_HOME=$JAVA_HOME"
	  TESTJAVA=$JAVA_HOME
   fi
   TESTSRC=.
   TESTCLASSES=.
   STANDALONE=1;
fi
echo "JDK under test is: $TESTJAVA"


# The beginning of the script proper

# Checking for proper OS and processor type.
#
# This test is only interested in SunOS SPARC sparcv9 and SunOS AMD64
# (supporting both 32 and 64 bit Solaris binaries).
#
# All other platforms will instantly complete with passing
# status.
#
OS=`uname -s`
case "$OS" in
   SunOS )
      PATHSEP=":"
      PTYPE=`uname -p`
      if [ -x /usr/bin/isainfo ]; then
	  # Instruction set being used by the OS
	  ISET=`isainfo -k`
      else
	  #SunOS 5.6 didn't have "isainfo"
          pass "This test always passes on $OS/$PTYPE (32-bit ${ISET})"
      fi
      ;;

   Linux )
      pass "This test always passes on $OS"
      ;;

   Windows* | CYGWIN*)
      pass "This test always passes on $OS"
      ;;

   # catch all other OSs
   * )
      echo "Unrecognized system!  $OS"
      fail "Unrecognized system!  $OS"
      ;;
esac

# Is the OS running in sparcv9 or amd64 mode?
case "${ISET}" in
  sparc )
      pass "This test always passes on $OS/$PTYPE (32-bit ${ISET})"
      ;;
  i386 )
      pass "This test always passes on $OS/$PTYPE (32-bit ${ISET})"
      ;;
  amd64 )
      echo "OS is running in ${ISET} mode"
      ;;
  sparcv9 )
      echo "OS is running in ${ISET} mode"
      ;;
  # catch all others
  * )
      echo "Unrecognized instruction set!  $OS/$PTYPE/${ISET}"
      fail "Unrecognized instruction set!  $OS/$PTYPE/${ISET}"
      ;;
esac

# SunOS 32 and 64 bit binaries must be available
#     to test in the remainder of the script below.
$TESTJAVA/bin/java -d64 -version > /dev/null 2<&1
if [ $? = 1 ]; then
   # The 64 bit version is not installed.  Make the test pass.
   pass "This test always passes on $OS/$PTYPE if 64 bit jdk is not installed"
fi

# Want this test to run standalone as well as in the harness, so do the 
#  following to copy the test's directory into the harness's scratch directory 
#  and set all appropriate variables:

#Deal with .class files:
if [ -n "${STANDALONE}" ] ; then 
   #if running standalone, compile the support files
   ${TESTJAVA}/bin/javac -d ${TESTCLASSES} \
            -classpath "$TESTJAVA/lib/tools.jar${PATHSEP}${TESTSRC}" \
            TestScaffold.java VMConnection.java TargetListener.java TargetAdapter.java
   ${TESTJAVA}/bin/javac -d ${TESTCLASSES} \
            -classpath "$TESTJAVA/lib/tools.jar${PATHSEP}${TESTSRC}" -g \
            FetchLocals.java DataModelTest.java
fi

# Get DEBUGGEE flags
DEBUGGEEFLAGS=
filename=$TESTCLASSES/@debuggeeVMOptions
if [ ! -r ${filename} ] ; then
    filename=$TESTCLASSES/../@debuggeeVMOptions
fi
# Remove -d32, -d64 if present, and remove -XX:[+-]UseCompressedOops 
# if present since it is illegal in 32 bit mode.
if [ -r ${filename} ] ; then
    DEBUGGEEFLAGS=`cat ${filename} | sed \
                        -e 's/-d32//g' \
                        -e 's/-d64//g' \
                        -e 's/-XX:.UseCompressedOops//g' \
                        `
fi

#
CLASSPATH="$TESTJAVA/lib/tools.jar${PATHSEP}${TESTCLASSES}"
export CLASSPATH
CP="-classpath \"${CLASSPATH}\""

for DEBUGGERMODEL in \
    32 \
    64 \
; do

    for TARGETMODEL in \
        32 \
        64 \
    ; do
        DEBUGGERFLAGS="-d${DEBUGGERMODEL} -showversion -DEXPECTED=${TARGETMODEL}"
        CONNECTSTRING="-connect 'com.sun.jdi.CommandLineLaunch:options=-d${TARGETMODEL} $DEBUGGEEFLAGS -showversion'"

	for TARGETCLASS in $testName ; do
	    echo "--------------------------------------------"
	    echo "debugger=${DEBUGGERMODEL} debugee=${TARGETMODEL} class=${TARGETCLASS}"
	    echo "--------------------------------------------"
	    echo ${TESTJAVA}/bin/java -DHANGINGJAVA_DEB ${DEBUGGERFLAGS} ${CP} ${TARGETCLASS} ${CONNECTSTRING}
	    eval ${TESTJAVA}/bin/java -DHANGINGJAVA_DEB ${DEBUGGERFLAGS} ${CP} ${TARGETCLASS} ${CONNECTSTRING}
	    status=$?
	    if [ $status -ne "0" ];
	       then fail "$DEBUGGERMODEL to $TARGETMODEL test failed for class=$TARGETCLASS!"
	    fi
	done
    done
done  
#
# pass or fail the test based on status of the command
if [ $status -eq "0" ];
   then pass ""

   else fail "unspecified test failure"
fi
