#!/bin/sh

#
# Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
# @ignore until 6203816 is dealt with.
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
rm -f Deadlock2*.class
if [ -d testlib ] ; then
    rm -rf testlib
fi
cp -r ${TESTJAVA}${FILESEP}lib${FILESEP}ext testlib

# compile and package the test program
${TESTJAVA}${FILESEP}bin${FILESEP}javac \
    -d ${TESTCLASSES} \
    ${TESTSRC}${FILESEP}CreateSerialized.java \
    ${TESTSRC}${FILESEP}Deadlock2.java

${TESTJAVA}${FILESEP}bin${FILESEP}jar \
    -cvf testlib${FILESEP}Deadlock2.jar \
    Deadlock2*.class

rm Deadlock2*.class

# create serialized object and run the test
${TESTJAVA}${FILESEP}bin${FILESEP}java CreateSerialized
${TESTJAVA}${FILESEP}bin${FILESEP}java -Djava.ext.dirs=${TESTCLASSES}${FILESEP}testlib Deadlock2
STATUS=$?

# clean up
rm object.tmp CreateSerialized.class
rm -rf testlib
exit ${STATUS}
