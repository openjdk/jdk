#
# Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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


# @test Test6842838.sh
# @bug 6842838
# @summary Test 6842838 64-bit launcher failure due to corrupt jar
# @compile CreateBadJar.java
# @run shell Test6842838.sh

set -x

if [ "${TESTJAVA}" = "" ]; then
  PARENT=`dirname \`which java\``
  TESTJAVA=`dirname ${PARENT}`
  printf "TESTJAVA not set.  Test cannot execute.  Failed.\n"
fi

if [ "${TESTCLASSES}" = "" ]; then
  printf "TESTCLASSES not set.  Test cannot execute.  Failed.\n"
  exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS )
    NULL=/dev/null
    PS=":"
    FS="/"
    JAVA_EXE=${TESTJAVA}${FS}bin${FS}java
    ;;
  * )
    printf "Only testing on solaris 64-bit (use libumem to reliably catch buffer overrun)\n"
    exit 0;
    ;;
esac

if [ ! -x ${JAVA_EXE} ]; then
    printf "Warning: 64-bit components not installed - skipping test.\n"
    exit 0
fi

LIBUMEM=/lib/64/libumem.so
if [ ! -x ${LIBUMEM} ]; then
    printf "Warning: libumem not installed - skipping test.\n"
    exit 0
fi

BADFILE=newbadjar.jar
${JAVA_EXE} ${TESTVMOPTS} -version
${JAVA_EXE} ${TESTVMOPTS} -cp ${TESTCLASSES} CreateBadJar ${BADFILE} "META-INF/MANIFEST.MF"
LD_PRELOAD=${LIBUMEM} ${JAVA_EXE} -jar ${BADFILE} > test.out 2>&1

grep "Invalid or corrupt jarfile" test.out
exit $?
