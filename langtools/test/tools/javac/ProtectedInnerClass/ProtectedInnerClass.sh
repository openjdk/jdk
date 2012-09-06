#
# Copyright (c) 1998, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4087314 4800342
# @summary Verify allowed access to protected class from another package.
# @author William Maddox (maddox)
#
# @run shell ProtectedInnerClass.sh


if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTSRC=${TESTSRC}"
if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTJAVA=${TESTJAVA}"
if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi
echo "TESTCLASSES=${TESTCLASSES}"
echo "CLASSPATH=${CLASSPATH}"

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    PS=":"
    FS="/"
    ;;
  CYGWIN* ) 
    PS=";" # native PS, not Cygwin PS
    FS="/"
    ;;
  Windows* )
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

rm -f ${TESTCLASSES}${FS}p1${FS}*.class ${TESTCLASSES}${FS}p2${FS}*.class

"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d "${TESTCLASSES}" "${TESTSRC}${FS}p1${FS}ProtectedInnerClass1.java" "${TESTSRC}${FS}p2${FS}ProtectedInnerClass2.java"
"${TESTJAVA}${FS}bin${FS}java" ${TESTVMOPTS} -classpath "${CLASSPATH}${PS}${TESTCLASSES}" p2.ProtectedInnerClass2
result=$?
if [ $result -eq 0 ]
then
  echo "Passed"
else
  echo "Failed"
fi
exit $result
