#! /bin/sh

#
# Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

# @test
# @bug 4361044 4388202 4418643 4523159 4730642
# @summary various resource and classloading bugs related to jar files
#set -x
DEST=`pwd`
#
# build jar1 
#
mkdir ${DEST}/jar1
cd ${TESTSRC}/etc/jar1 
cp -r . ${DEST}/jar1
${TESTJAVA}/bin/javac -d ${DEST}/jar1 ${TESTSRC}/src/jar1/LoadResourceBundle.java
${TESTJAVA}/bin/javac -d ${DEST}/jar1 ${TESTSRC}/src/jar1/GetResource.java
cd ${DEST}/jar1
${TESTJAVA}/bin/jar cfM jar1.jar jar1 res1.txt
mv jar1.jar ..
#
# build the test sources and run them
#
${TESTJAVA}/bin/javac -d ${DEST} ${TESTSRC}/src/test/*.java
cd ${DEST}
${TESTJAVA}/bin/java RunAllTests
result=$?
if [ "$result" -ne "0" ]; then
    exit 1
fi
rm -rf *
exit 0
