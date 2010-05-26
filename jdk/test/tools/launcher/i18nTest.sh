#
# Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4761384
# @run shell deleteI18n.sh
# @build CreatePlatformFile
# @run main CreatePlatformFile
# @run shell i18nTest.sh
# @summary Test to see if class files with non-ASCII characters can be run
# @author Joseph D. Darcy


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

JAVAC="${TESTJAVA}/bin/javac -d . "
JAVA="${TESTJAVA}/bin/java -classpath . "

NAME=`ls i18n*.java | sed s/.java//`
echo $NAME
$JAVAC ${NAME}.java

RESULT=$?
case "$RESULT" in
        0  )
        ;;

        * )
	echo "Compile of i18n*.java failed."
        exit 1
esac

$JAVA ${NAME}
RESULT=$?

case "$RESULT" in
        0  )
        exit 0;
        ;;

        * )
	echo "Class $NAME did not run successfully."
        exit 1
esac
