#
#  Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
#  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
#  This code is free software; you can redistribute it and/or modify it
#  under the terms of the GNU General Public License version 2 only, as
#  published by the Free Software Foundation.
#
#  This code is distributed in the hope that it will be useful, but WITHOUT
#  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
#  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
#  version 2 for more details (a copy is included in the LICENSE file that
#  accompanied this code).
#
#  You should have received a copy of the GNU General Public License version
#  2 along with this work; if not, write to the Free Software Foundation,
#  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
#  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
#  or visit www.oracle.com if you need additional information or have any
#  questions.
#


# @test Test8000968.sh
# @bug 8000968
# @summary NPG: UseCompressedKlassPointers asserts with ObjectAlignmentInBytes=32
# @run shell Test8000968.sh
#

if [ "${TESTJAVA}" = "" ]
then
  PARENT=`dirname \`which java\``
  TESTJAVA=`dirname ${PARENT}`
  printf "TESTJAVA not set, selecting " ${TESTJAVA}
  printf "  If this is incorrect, try setting the variable manually.\n"
fi


# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Windows_* )
    FS="\\"
    NULL=NUL
    ;;
  * )
    FS="/"
    NULL=/dev/null
    ;;
esac

JAVA=${TESTJAVA}${FS}bin${FS}java

#
# See if platform has 64 bit java.
#
${JAVA} ${TESTVMOPTS} -d64 -version 2>&1 | grep -i "does not support" > ${NULL}
if [ "$?" != "1" ]
then
  printf "Platform is 32 bit, does not support -XX:ObjectAlignmentInBytes= option.\n"
  printf "Passed.\n"
  exit 0
fi

#
# Test -XX:ObjectAlignmentInBytes with -XX:+UseCompressedKlassPointers -XX:+UseCompressedOops.
#
${JAVA} ${TESTVMOPTS} -d64 -XX:+UseCompressedKlassPointers -XX:+UseCompressedOops -XX:ObjectAlignmentInBytes=16 -version 2>&1 > ${NULL}
if [ "$?" != "0" ]
then
  printf "FAILED: -XX:ObjectAlignmentInBytes=16 option did not work.\n"
  exit 1
fi

${JAVA} ${TESTVMOPTS} -d64 -XX:+UseCompressedKlassPointers -XX:+UseCompressedOops -XX:ObjectAlignmentInBytes=32 -version 2>&1 > ${NULL}
if [ "$?" != "0" ]
then
  printf "FAILED: -XX:ObjectAlignmentInBytes=32 option did not work.\n"
  exit 1
fi

${JAVA} ${TESTVMOPTS} -d64 -XX:+UseCompressedKlassPointers -XX:+UseCompressedOops -XX:ObjectAlignmentInBytes=64 -version 2>&1 > ${NULL}
if [ "$?" != "0" ]
then
  printf "FAILED: -XX:ObjectAlignmentInBytes=64 option did not work.\n"
  exit 1
fi

${JAVA} ${TESTVMOPTS} -d64 -XX:+UseCompressedKlassPointers -XX:+UseCompressedOops -XX:ObjectAlignmentInBytes=128 -version 2>&1 > ${NULL}
if [ "$?" != "0" ]
then
  printf "FAILED: -XX:ObjectAlignmentInBytes=128 option did not work.\n"
  exit 1
fi


printf "Passed.\n"
exit 0
