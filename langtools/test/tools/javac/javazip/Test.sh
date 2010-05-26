#! /bin/sh -f

#
# Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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

# @test
# @bug 4098712 6304984 6388453
# @summary check that source files inside zip files on the class path are ignored
# @run shell Test.sh

TS=${TESTSRC-.}
TC=${TESTCLASSES-.}
SCR=`pwd`

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux )
    FS="/"
    SCR=`pwd`
    ;;
  CYGWIN* )
    FS="/"
    SCR=`pwd | cygpath -d`
    ;;
  Windows* )
    FS="\\"
    SCR=`pwd`
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

check() {
    expected=$1
    shift

    # clean old classes
    rm -f ${TC}${FS}*.class 

    echo "$*"
    if $* 2>&1 ; then
      actual=ok
    else
      actual=err
    fi
    if [ "$actual" != "$expected" ]; then
      case "$actual" in
        ok  ) echo "error: unexpected result: command succeeded" ;;
        err ) echo "error: unexpected result: command failed"
      esac
      exit 1
    else 
      case "$actual" in
        ok  ) echo "command succeeded as expected" ;;
        err ) echo "command failed as expected."
      esac
    fi

    echo 
}

echo "# create zip/jar files with source code"
check ok   "${TESTJAVA}${FS}bin${FS}jar" cf "${SCR}${FS}good.jar" -C "${TESTSRC}${FS}good" B.java
check ok   "${TESTJAVA}${FS}bin${FS}jar" cf "${SCR}${FS}good.zip" -C "${TESTSRC}${FS}good" B.java
check ok   "${TESTJAVA}${FS}bin${FS}jar" cf "${SCR}${FS}bad.jar"  -C "${TESTSRC}${FS}bad" B.java
check ok   "${TESTJAVA}${FS}bin${FS}jar" cf "${SCR}${FS}bad.zip"  -C "${TESTSRC}${FS}bad" B.java

echo "# control tests, with no paths"
check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} "${TESTSRC}${FS}A.java" "${TESTSRC}${FS}good${FS}B.java"
check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} "${TESTSRC}${FS}A.java" "${TESTSRC}${FS}bad${FS}B.java"

echo "# test that source files are found in directories on path"
check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -classpath "${TESTSRC}${FS}good"   "${TESTSRC}${FS}A.java"
check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -sourcepath "${TESTSRC}${FS}good"  "${TESTSRC}${FS}A.java"
check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -classpath "${TESTSRC}${FS}bad"    "${TESTSRC}${FS}A.java"
check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -sourcepath "${TESTSRC}${FS}bad"   "${TESTSRC}${FS}A.java"

echo "# test that source files are found in zip/jar files on path"
check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -classpath "${SCR}${FS}good.zip"   "${TESTSRC}${FS}A.java"
check ok   "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -classpath "${SCR}${FS}good.jar"   "${TESTSRC}${FS}A.java"
check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -sourcepath "${SCR}${FS}bad.zip"   "${TESTSRC}${FS}A.java"  
check err  "${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d ${TC} -sourcepath "${SCR}${FS}bad.jar"   "${TESTSRC}${FS}A.java" 
