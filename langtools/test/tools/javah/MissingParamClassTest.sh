#!/bin/sh

#
# Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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
#
# @bug 4942232

#
# Verifies that javah won't attempt to generate a header file
# if a native method in a supplied class contains a parameter
# type whose corresponding class is missing or not in the 
# classpath

TMP1=OUTPUT.txt

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTSRC=${TESTSRC}"
if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTJAVA=${TESTJAVA}"
if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTCLASSES=${TESTCLASSES}"
echo "CLASSPATH=${CLASSPATH}"

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin | CYGWIN* )
    PS=":"
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
 esac

GENERATED_HEADER_FILE=ParamClassTest.h

rm -f ParamClassTest.class MissingParamClassException.class ParamClassTest.h
rm -f ${TMP1}

"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d . "${TESTSRC}${FS}ParamClassTest.java" "${TESTSRC}${FS}MissingParamClassException.java"

# Before running javah remove dependent class file
rm -f MissingParamClassException.class 

"${TESTJAVA}${FS}bin${FS}javah" ${TESTTOOLVMOPTS} ParamClassTest 2>${TMP1}

if [ -f $GENERATED_HEADER_FILE ]; then
     echo "1-- Failed: $GENERATED_HEADER_FILE found"
     rc=1
fi
if [ ! -s ${TMP1} ]; then
     echo "1-- Failed: ${TMP1} is empty"
     rc=1
fi

# Clean out work dir
rm -f MissingParamClassException.class ParamClassTest.class
rm -f $GENERATED_HEADER_FILE $TMP1 

# Re-compile everything

"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d . "${TESTSRC}${FS}ParamClassTest.java" "${TESTSRC}${FS}MissingParamClassException.java"


# Before re-run of javah remove dependent class file Param.class 
rm -f Param.class

"${TESTJAVA}${FS}bin${FS}javah" ${TESTTOOLVMOPTS} ParamClassTest 2>${TMP1}

if [ -f $GENERATED_HEADER_FILE ]; then
     echo "2-- Failed: $GENERATED_HEADER_FILE found"
     rc=1
fi
if [ ! -s ${TMP1} ]; then
     echo "2-- Failed: ${TMP1} is empty"
     rc=1
fi

if [ "$rc" = "" ]; then
    echo Passed
else
    echo Failed
    exit 1
fi
