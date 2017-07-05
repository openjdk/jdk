#
# Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4952558
# @summary Verify names that aren't legal Java names are accepted by forName.
# @author Joseph D. Darcy
# @compile -source 1.5 -target 1.5 NonJavaNames.java
# @run shell NonJavaNames.sh

# This test uses hand-generated class files stored in the ./classes
# directory.  After the renaming done below, those class files have
# single character names that are legal class names under class file
# version 49 but *not* legal Java language identifiers; e.g. "3" and
# "+".  First, Z.java is compiled to Z.class using "-target 1.5."
# Next, to create a test class file, the appropriate name structures
# within the class files are updated, as is the "Hello world" string
# the class's main method prints out.  If the definition of the
# semantics of "-target 1.5" changes, the test class files should be
# regenerated.

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

# All preconditions are met; run the tests

OS=`uname -s`;
# Set classpath separator
case "$OS" in
        Windows* | CYGWIN* )
	SEP=";"
        ;;

	* )
	SEP=":"
esac

# Copy "hyphen.class" to "-.class"

COPYHYPHEN="cp ${TESTSRC}/classes/hyphen.class ${TESTCLASSES}/-.class"
$COPYHYPHEN

COPYCOMMA="cp ${TESTSRC}/classes/comma.class ${TESTCLASSES}/,.class"
$COPYCOMMA

COPYPERIOD="cp ${TESTSRC}/classes/period.class ${TESTCLASSES}/..class"
$COPYPERIOD

COPYLEFTSQUARE="cp ${TESTSRC}/classes/left-square.class ${TESTCLASSES}/[.class"
$COPYLEFTSQUARE

COPYRIGHTSQUARE="cp ${TESTSRC}/classes/right-square.class ${TESTCLASSES}/].class"
$COPYRIGHTSQUARE

COPYPLUS="cp ${TESTSRC}/classes/plus.class ${TESTCLASSES}/+.class"
$COPYPLUS

COPYSEMICOLON="cp ${TESTSRC}/classes/semicolon.class ${TESTCLASSES}/;.class"
$COPYSEMICOLON

JAVA="$TESTJAVA/bin/java -classpath ${TESTSRC}/classes${SEP}${TESTCLASSES}"

$JAVA NonJavaNames
RESULT=$?

case "$RESULT" in
        0 )
        exit 0;
        ;;

        * )
        exit 1
esac

