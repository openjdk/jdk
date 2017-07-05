#!/bin/sh

#
#  Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
#  Copyright (c) 2011 SAP AG.  All Rights Reserved.
#  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
#  This code is free software; you can redistribute it and/or modify it
#  under the terms of the GNU General Public License version 2 only, as
#  published by the Free Software Foundation.
#
#  This code is distributed in the hope that it will be useful, but WITHOUT
#  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
#  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
#  version 2 for more details (a copy is included in the LICENSE file that
#  accompanied this code).
#
#  You should have received a copy of the GNU General Public License version
#  2 along with this work; if not, write to the Free Software Foundation,
#  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
#  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
#  or visit www.oracle.com if you need additional information or have any
#  questions.
#

##
## @test Test7107135.sh
## @bug 7107135
## @summary Stack guard pages lost after loading library with executable stack.
## @run shell Test7107135.sh
##

if [ "${TESTSRC}" = "" ]
then TESTSRC=.
fi

if [ "${TESTJAVA}" = "" ]
then
  PARENT=`dirname \`which java\``
  TESTJAVA=`dirname ${PARENT}`
  echo "TESTJAVA not set, selecting " ${TESTJAVA}
  echo "If this is incorrect, try setting the variable manually."
fi

BIT_FLAG=""

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Linux)
    NULL=/dev/null
    PS=":"
    FS="/"
    ;;
  *)
    NULL=NUL
    PS=";"
    FS="\\"
    echo "Test passed; only valid for Linux"
    exit 0;
    ;;
esac

ARCH=`uname -m`

THIS_DIR=`pwd`

cp ${TESTSRC}${FS}*.java ${THIS_DIR}
${TESTJAVA}${FS}bin${FS}javac *.java

gcc -fPIC -shared -c -o test.o -I${TESTJAVA}${FS}include -I${TESTJAVA}${FS}include${FS}linux ${TESTSRC}${FS}test.c
ld -shared -z   execstack -o libtest-rwx.so test.o
ld -shared -z noexecstack -o libtest-rw.so  test.o


LD_LIBRARY_PATH=${THIS_DIR}
echo   LD_LIBRARY_PATH = ${LD_LIBRARY_PATH}
export LD_LIBRARY_PATH

# This should not fail.
echo Check testprogram. Expected to pass:
echo ${TESTJAVA}${FS}bin${FS}java -cp ${THIS_DIR} Test test-rw
${TESTJAVA}${FS}bin${FS}java -cp ${THIS_DIR} Test test-rw

echo
echo Test changing of stack protection:
echo ${TESTJAVA}${FS}bin${FS}java -cp ${THIS_DIR} Test test-rw
${TESTJAVA}${FS}bin${FS}java -cp ${THIS_DIR} Test test-rwx

if [ "$?" == "0" ]
then
  echo
  echo ${TESTJAVA}${FS}bin${FS}java -cp ${THIS_DIR} TestMT test-rwx
  ${TESTJAVA}${FS}bin${FS}java -cp ${THIS_DIR} TestMT test-rwx
fi

exit $?
