# Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

# @test
# @bug 5030265
# @summary Verify that the J2RE can handle all legal Unicode characters
#          in class names unless limited by the file system encoding
#          or the encoding used for command line arguments.
# @author Norbert Lindenberg


# Verify directory context variables are set
if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

JAVAC="${TESTJAVA}"/bin/javac
JAVA="${TESTJAVA}"/bin/java
JAR="${TESTJAVA}"/bin/jar

mkdir UnicodeTest-src UnicodeTest-classes

echo "creating test source files"
"$JAVAC" -d . "${TESTSRC}"/UnicodeTest.java
CLASS_NAME=`"$JAVA" UnicodeTest | sed -e 's@\\r@@g' `

if [ "$CLASS_NAME" = "" ]
then
  echo "CLASS_NAME not generated.  Test failed."
  exit 1
fi

echo "building test apps"
"$JAVAC" -encoding UTF-8 -sourcepath UnicodeTest-src \
    -d UnicodeTest-classes UnicodeTest-src/"${CLASS_NAME}".java || exit 1
"$JAR" -cvfm UnicodeTest.jar UnicodeTest-src/MANIFEST.MF \
    -C UnicodeTest-classes . || exit 1

echo "running test app using class file"
"$JAVA" -classpath UnicodeTest-classes "$CLASS_NAME" || exit 1

echo "delete generated files with non-ASCII names"
# do it now because on Unix they may not be accessible when locale changes
# do it in Java because shells on Windows can't handle full Unicode
"$JAVAC" -d . "${TESTSRC}"/UnicodeCleanup.java || exit 1
"$JAVA" UnicodeCleanup UnicodeTest-src UnicodeTest-classes || exit 1

echo "running test app using newly built jar file"
"$JAVA" -jar UnicodeTest.jar || exit 1

echo "running test app using jar file built in Solaris UTF-8 locale"
"$JAVA" -jar "${TESTSRC}"/UnicodeTest.jar || exit 1

# if we can switch to a C locale, then test whether jar files with
# non-ASCII characters in the manifest still work in this crippled
# environment
if test -n "`locale -a 2>/dev/null | grep '^C$'`"
then
    LC_ALL=C
    export LC_ALL

    echo "running test app using newly built jar file in C locale"
    "$JAVA" -jar UnicodeTest.jar || exit 1

    echo "running test app using premade jar file in C locale"
    "$JAVA" -jar "${TESTSRC}"/UnicodeTest.jar || exit 1
fi

exit 0

