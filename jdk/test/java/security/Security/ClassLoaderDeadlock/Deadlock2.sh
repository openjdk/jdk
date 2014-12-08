#!/bin/sh

#
# Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6440846
# @summary make sure we do not deadlock between ExtClassLoader and AppClassLoader
# @author Valerie Peng
# @run shell/timeout=20 Deadlock2.sh

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory

if [ "${TESTSRC}" = "" ] ; then
   TESTSRC="."
fi

if [ "${TESTCLASSES}" = "" ] ; then
   TESTCLASSES="."
fi

if [ "${TESTJAVA}" = "" ] ; then
   echo "TESTJAVA not set.  Test cannot execute."
   echo "FAILED!!!"
   exit 1
fi

if [ "${COMPILEJAVA}" = "" ]; then
   COMPILEJAVA="${TESTJAVA}"
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS )
    PATHSEP=":"
    FILESEP="/"
    ;;
  Linux )
    PATHSEP=":"
    FILESEP="/"
    ;;
  CYGWIN* )
    PATHSEP=";"
    FILESEP="/"
    ;;
  Darwin )
    PATHSEP=":"
    FILESEP="/"
    ;;
  AIX )
    PATHSEP=":"
    FILESEP="/"
    ;;
  Windows* )
    PATHSEP=";"
    FILESEP="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# remove old class files
cd ${TESTCLASSES}
if [ -d testlib ] ; then
    rm -rf testlib
fi
mkdir testlib

# compile and package the test program
${COMPILEJAVA}${FILESEP}bin${FILESEP}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
    -d ${TESTCLASSES} \
    ${TESTSRC}${FILESEP}CreateSerialized.java \
    ${TESTSRC}${FILESEP}Deadlock2.java

${COMPILEJAVA}${FILESEP}bin${FILESEP}jar ${TESTTOOLVMOPTS} \
    -cvf testlib${FILESEP}Deadlock2.jar \
    Deadlock2*.class

rm Deadlock2*.class

# create serialized object and run the test
${TESTJAVA}${FILESEP}bin${FILESEP}java ${TESTVMOPTS} CreateSerialized
${TESTJAVA}${FILESEP}bin${FILESEP}java ${TESTVMOPTS} \
    -Djava.ext.dirs=${TESTCLASSES}${FILESEP}testlib${PATHSEP}${TESTJAVA}${FILESEP}lib${FILESEP}ext Deadlock2
STATUS=$?

# clean up
rm object.tmp CreateSerialized.class
rm -rf testlib
exit ${STATUS}
