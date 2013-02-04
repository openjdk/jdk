#! /bin/sh

#
# Copyright (c) 2005, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4361044 4388202 4418643 4523159 4730642
# @summary various resource and classloading bugs related to jar files
#set -x
DEST=`pwd`

OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    PS=":"
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    ;;
  CYGWIN* )
    PS=";"
    FS="/"
    #
    # javac does not like /cygdrive produced by `pwd`.
    #
    DEST=`cygpath -d ${DEST}`
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

#
# build jar1
#
mkdir -p ${DEST}${FS}jar1
cd ${TESTSRC}${FS}etc${FS}jar1
cp -r . ${DEST}${FS}jar1
${COMPILEJAVA}${FS}bin${FS}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${DEST}${FS}jar1 \
    ${TESTSRC}${FS}src${FS}jar1${FS}LoadResourceBundle.java
${COMPILEJAVA}${FS}bin${FS}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${DEST}${FS}jar1 \
    ${TESTSRC}${FS}src${FS}jar1${FS}GetResource.java
cd ${DEST}${FS}jar1
${COMPILEJAVA}${FS}bin${FS}jar ${TESTTOOLVMOPTS} cfM jar1.jar jar1 res1.txt
mv jar1.jar ..
#
# build the test sources and run them
#
${COMPILEJAVA}${FS}bin${FS}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${DEST} ${TESTSRC}${FS}src${FS}test${FS}*.java
cd ${DEST}
${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} RunAllTests
result=$?
if [ "$result" -ne "0" ]; then
    exit 1
fi
rm -rf *
exit 0
