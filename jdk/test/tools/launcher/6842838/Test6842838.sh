#!/bin/sh -x

#
# @test @(#)Test6842838.sh
# @bug 6842838
# @summary Test 6842838 64-bit launcher failure due to corrupt jar
# @run shell Test6842838.sh
#

#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS )
    NULL=/dev/null
    PS=":"
    FS="/"
    JAVA_EXE=${TESTJAVA}${FS}bin${FS}sparcv9${FS}java
    ;;
  * )
    echo "Only testing on sparcv9 (use libumem to reliably catch buffer overrun)"
    exit 0;
    ;;
esac

BADFILE=newbadjar.jar

${JAVA_EXE} -version
rm -f ${BADFILE}
${TESTJAVA}/bin/javac CreateBadJar.java
${JAVA_EXE} CreateBadJar ${BADFILE} "META-INF/MANIFEST.MF"
LD_PRELOAD=/lib/64/libumem.so ${JAVA_EXE} -jar ${BADFILE} > test.out 2>&1

grep "Invalid or corrupt jarfile" test.out
exit $?
