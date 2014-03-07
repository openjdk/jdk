#
# Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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

#!/bin/ksh -p
#
#   @test    IOExceptionIfEncodedURLTest.sh
#   @bug     6193279 6619458
#   @summary REGRESSION: AppletViewer throws IOException when path is encoded URL
#   @author  Dmitry Cherepanov: area=appletviewer
#   @run compile IOExceptionIfEncodedURLTest.java
#   @run main IOExceptionIfEncodedURLTest
#   @run shell IOExceptionIfEncodedURLTest.sh

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

#Call this to run the test with a file name
test()
 {
   ${TESTJAVA}${FILESEP}bin${FILESEP}appletviewer -Xnosecurity ${URL} > err 2>&1 &
   APPLET_ID=$!
   sleep 15
   kill -9 $APPLET_ID

   # these exceptions will be thrown if the test fails
   cat err | grep "I/O exception while reading"
   exception=$?
   if [ $exception = "0" ];
       then fail "test failed for "${URL}", see err file and CRs #6193279,6329251,6376334"
   fi

   cat err | grep "java.lang.ClassNotFoundException"
   exception=$?
   if [ $exception = "0" ];
       then fail "test failed for "${URL}", see err file and CRs #6193279,6329251,6376334"
   fi

   # the applet will log the same message
   cat err | grep "the appletviewer started"
   started=$?

   echo $started | grep "2"
   if [ $? = 0 ] ;
       then fail "test failed for "${URL}": syntax errors or inaccessible files"
   fi

   if [ $started = "0" ];
       then echo "the test passed for "${URL}
       else fail "test failed for "${URL}": the appletviewer behaviour is unexpected: "$started", see err file"
   fi
 }

# end of subroutines


# The beginning of the script proper

# Checking for proper OS
OS=`uname -s`
case "$OS" in
   SunOS )
      VAR="One value for Sun"
      DEFAULT_JDK=/
      FILESEP="/"
      PATHSEP=":"
      TMP="/tmp"
      ;;

   Linux )
      VAR="A different value for Linux"
      DEFAULT_JDK=/
      FILESEP="/"
      PATHSEP=":"
      TMP="/tmp"
      ;;

   Darwin )
      VAR="A different value for MacOSX"
      DEFAULT_JDK=/usr
      FILESEP="/"
      PATHSEP=":"
      TMP="/tmp"
      ;;

   Windows* )
      VAR="A different value for Win32"
      DEFAULT_JDK="C:/Program Files/Java/jdk1.8.0"
      FILESEP="\\"
      PATHSEP=";"
      TMP=`cd "${SystemRoot}/Temp"; echo ${PWD}`
      ;;

    CYGWIN* )
      VAR="A different value for Cygwin"
      DEFAULT_JDK="/cygdrive/c/Program\ Files/Java/jdk1.8.0"
      FILESEP="/"
      PATHSEP=";"
      TMP=`cd "${SystemRoot}/Temp"; echo ${PWD}`
      ;;

    AIX )
      VAR="A different value for AIX"
      DEFAULT_JDK=/
      FILESEP="/"
      PATHSEP=":"
      TMP="/tmp"
      ;;

   # catch all other OSs
   * )
      echo "Unrecognized system!  $OS"
      fail "Unrecognized system!  $OS"
      ;;
esac

# 6438730: Only a minimal set of env variables are set for shell tests.
# To guarantee that env variable holds correct value we need to set it ourselves.
if [ -z "${PWD}" ] ; then
    PWD=`pwd`
fi

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
if [ -n "${STANDALONE}" ] ;
   then
   #if standalone, remind user to cd to dir. containing test before running it
   echo "Just a reminder: cd to the dir containing this test when running it"
   # then compile all .java files (if there are any) into .class files
   if [ -a *.java ] ;
      then echo "Reminder, this test should be in its own directory with all"
      echo "supporting files it needs in the directory with it."
      ${TESTJAVA}/bin/javac ./*.java ;
   fi
   # else in harness so copy all the class files from where jtreg put them
   # over to the scratch directory this test is running in.
   else cp ${TESTCLASSES}/*.class . ;
fi

#if in test harness, then copy the entire directory that the test is in over
# to the scratch directory.  This catches any support files needed by the test.
#if [ -z "${STANDALONE}" ] ;
#   then cp ${TESTSRC}/* .
#fi

#Just before executing anything, make sure it has executable permission!
chmod 777 ./*

###############  YOUR TEST CODE HERE!!!!!!!  #############

#All files required for the test should be in the same directory with
# this file.  If converting a standalone test to run with the harness,
# as long as all files are in the same directory and it returns 0 for
# pass, you should be able to cut and paste it into here and it will
# run with the test harness.

# This is an example of running something -- test
# The stuff below catches the exit status of test then passes or fails
# this shell test as appropriate ( 0 status is considered a pass here )

# The test verifies that appletviewer correctly works with the different
# names of the files, including relative and absolute paths

# 6619458: exclude left brace from the name of the files managed by the VCS
NAME='test.html'

ENCODED='te%7Bst.html'
UNENCODED='te{st.html'

# Copy needed files into the harness's scratch directory
# or create a copy with the required name if the test is
# running as stand-alone
cp ${TESTSRC}${FILESEP}${NAME} ${UNENCODED}

# the encoded name, the path is absolute
URL="file:"${PWD}${FILESEP}${ENCODED}
test

# the encoded name, the path is relative
URL="file:"${ENCODED}
test

# the unencoded name, the path is absolute
URL="file:"${PWD}${FILESEP}${UNENCODED}
test

# the unencoded name, the path is relative
URL="file:"${UNENCODED}
test

# pick up our toys from the scratch directory
rm ${UNENCODED}
