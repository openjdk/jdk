#
# Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4087295 4785472
# @summary Enable resolveClass() to accommodate package renaming.
# This fix enables one to implement a resolveClass method that maps a
# Serialiazable class within a serialization stream to the same class
# in a different package within the JVM runtime. See run shell script
# for instructions on how to run this test.


if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${COMPILEJAVA}" = "" ] ; then
  COMPILEJAVA="${TESTJAVA}"
fi


OS=`uname -s`
# Need to determine the classpath separator and filepath separator based on the
# operating system.
case "$OS" in
SunOS | Linux | Darwin | AIX )
  PS=":"  ;;
Windows* | CYGWIN* )
  PS=";"  ;;
* )
  echo "Unrecognized system!"
  exit 1  ;;
esac

JAVA=${TESTJAVA}/bin/java
JAVAC=${COMPILEJAVA}/bin/javac
MKDIR=mkdir
RDEL="rm -r"

if [ -d ${TESTCLASSES}/oclasses ]
then
   ${RDEL} ${TESTCLASSES}/oclasses
fi
if [ -d ${TESTCLASSES}/nclasses ]
then
   ${RDEL} ${TESTCLASSES}/nclasses
fi
if [ -d ${TESTCLASSES}/share ]
then
   ${RDEL} ${TESTCLASSES}/share
fi
if [ -f ${TESTCLASSES}/stream.ser ]
then
   ${RDEL} ${TESTCLASSES}/stream.ser
fi

mkdir ${TESTCLASSES}/oclasses
mkdir ${TESTCLASSES}/share
mkdir ${TESTCLASSES}/nclasses

# Build sources
set -e
${JAVAC} ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${TESTCLASSES}/share \
    ${TESTSRC}/extension/ExtendedObjectInputStream.java
CLASSPATH=${TESTCLASSES}/share; export CLASSPATH;
${JAVAC} ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${TESTCLASSES}/oclasses \
    ${TESTSRC}/test/SerialDriver.java
CLASSPATH=${TESTCLASSES}/share; export CLASSPATH;
${JAVAC} ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${TESTCLASSES}/nclasses \
    ${TESTSRC}/install/SerialDriver.java

# Run Case 1. Map test.SerialDriver within stream to install.SerialDriver.
CLASSPATH="${TESTCLASSES}/oclasses${PS}${TESTCLASSES}/share"; export CLASSPATH;
${JAVA} ${TESTVMOPTS} test.SerialDriver -s
CLASSPATH="${TESTCLASSES}/nclasses${PS}${TESTCLASSES}/share"; export CLASSPATH;
${JAVA} ${TESTVMOPTS} install.SerialDriver -d
rm stream.ser

# Run Case 2. Map install.SerialDriver within stream to test.SerialDriver.
CLASSPATH="${TESTCLASSES}/nclasses${PS}${TESTCLASSES}/share"; export CLASSPATH;
${JAVA} ${TESTVMOPTS} install.SerialDriver -s
CLASSPATH="${TESTCLASSES}/oclasses${PS}${TESTCLASSES}/share"; export CLASSPATH;
${JAVA} ${TESTVMOPTS} test.SerialDriver -d
