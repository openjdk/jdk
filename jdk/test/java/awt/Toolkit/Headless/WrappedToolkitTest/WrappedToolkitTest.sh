#!/bin/ksh -p

#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#   @test
#   @bug 6282388
#   @summary Tests that AWT use correct toolkit to be wrapped into HeadlessToolkit
#   @author artem.ananiev@sun.com: area=awt.headless
#   @compile TestWrapped.java
#   @run shell WrappedToolkitTest.sh

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

# Checking for proper OS
OS=`uname -s`
case "$OS" in
   SunOS )
      VAR="One value for Sun"
      DEFAULT_JDK=/usr/local/java/jdk1.2/solaris
      FILESEP="/"
      ;;

   Linux )
      VAR="A different value for Linux"
      DEFAULT_JDK=/usr/local/java/jdk1.4/linux-i386
      FILESEP="/"
      ;;

   Windows* | CYGWIN* )
      VAR="A different value for Win32"
      DEFAULT_JDK=/usr/local/java/jdk1.2/win32
      FILESEP="\\"
      ;;

    Darwin)
      VAR="Lets not forget about Mac"
      DEFAULT_JDK=$(/usr/libexec/java_home)
      FILESEP="/"
      ;;

   # catch all other OSs
   * )
      echo "Unrecognized system!  $OS"
      fail "Unrecognized system!  $OS"
      ;;
esac

# check that some executable or other file you need is available, abort if not
#  note that the name of the executable is in the fail string as well.
# this is how to check for presence of the compiler, etc.
#RESOURCE=`whence SomeProgramOrFileNeeded`
#if [ "${RESOURCE}" = "" ] ; 
#   then fail "Need SomeProgramOrFileNeeded to perform the test" ; 
#fi

# Want this test to run standalone as well as in the harness, so do the 
#  following to copy the test's directory into the harness's scratch directory 
#  and set all appropriate variables:

if [ -z "${TESTJAVA}" ] ; then
   # TESTJAVA is not set, so the test is running stand-alone.
   # TESTJAVA holds the path to the root directory of the build of the JDK
   # to be tested.  That is, any java files run explicitly in this shell
   # should use TESTJAVA in the path to the java interpreter.
   # So, we'll set this to the JDK spec'd on the command line.  If none
   # is given on the command line, tell the user that and use a cheesy
   # default.
   # THIS IS THE JDK BEING TESTED.
   if [ -n "$1" ] ;
      then TESTJAVA=$1
      else echo "no JDK specified on command line so using default!"
     TESTJAVA=$DEFAULT_JDK
   fi
   TESTSRC=.
   TESTCLASSES=.
   STANDALONE=1;
fi
echo "JDK under test is: $TESTJAVA"

#Deal with .class files:
if [ -n "${STANDALONE}" ] ; then
   # then compile all .java files (if there are any) into .class files
   if [ -a *.java ]; then
      ${TESTJAVA}/bin/javac$ ./*.java ;
   fi
   # else in harness so copy all the class files from where jtreg put them
   # over to the scratch directory this test is running in. 
   else cp ${TESTCLASSES}/*.class . ;
fi

#if in test harness, then copy the entire directory that the test is in over 
# to the scratch directory.  This catches any support files needed by the test.
if [ -z "${STANDALONE}" ] ; 
   then cp ${TESTSRC}/* . 
fi

#Just before executing anything, make sure it has executable permission!
chmod 777 ./*

###############  YOUR TEST CODE HERE!!!!!!!  #############

case "$OS" in
  Windows* | CYGWIN* )
    ${TESTJAVA}/bin/java -Djava.awt.headless=true \
                         TestWrapped sun.awt.windows.WToolkit
    status=$?
    if [ ! $status -eq "0" ]; then
      fail "Test FAILED: toolkit wrapped into HeadlessToolkit is not an instance of sun.awt.windows.WToolkit";
    fi
    ${TESTJAVA}/bin/java -Djava.awt.headless=true \
                         -Dawt.toolkit=sun.awt.windows.WToolkit \
                         TestWrapped sun.awt.windows.WToolkit
    status=$?
    if [ ! $status -eq "0" ]; then
      fail "Test FAILED: toolkit wrapped into HeadlessToolkit is not an instance of sun.awt.windows.WToolkit";
    fi
    ;;

  SunOS | Linux )
    ${TESTJAVA}/bin/java -Djava.awt.headless=true \
                         -Dawt.toolkit=sun.awt.X11.XToolkit \
                         TestWrapped sun.awt.X11.XToolkit
    status=$?
    if [ ! $status -eq "0" ]; then
      fail "Test FAILED: toolkit wrapped into HeadlessToolkit is not an instance of sun.awt.xawt.XToolkit";
    fi
    AWT_TOOLKIT=XToolkit ${TESTJAVA}/bin/java -Djava.awt.headless=true \
                                              TestWrapped sun.awt.X11.XToolkit
    status=$?
    if [ ! $status -eq "0" ]; then
      fail "Test FAILED: toolkit wrapped into HeadlessToolkit is not an instance of sun.awt.xawt.XToolkit";
    fi
    ;;

  Darwin)
    ${TESTJAVA}/bin/java -Djava.awt.headless=true \
                         TestWrapped sun.lwawt.macosx.LWCToolkit
    status=$?
    if [ ! $status -eq "0" ]; then
      fail "Test FAILED: toolkit wrapped into HeadlessToolkit is not an instance of sun.lwawt.macosx.LWCToolkit";
    fi
    ${TESTJAVA}/bin/java -Djava.awt.headless=true \
                         -Dawt.toolkit=sun.lwawt.macosx.LWCToolkit \
                         TestWrapped sun.lwawt.macosx.LWCToolkit
    status=$?
    if [ ! $status -eq "0" ]; then
      fail "Test FAILED: toolkit wrapped into HeadlessToolkit is not an instance of sun.lwawt.macosx.LWCToolkit";
    fi
    ;;

esac

pass "All the tests are PASSED";

#For additional examples of how to write platform independent KSH scripts,
# see the jtreg file itself.  It is a KSH script for both Solaris and Win32
