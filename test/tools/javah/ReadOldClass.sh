#!/bin/sh

#
# Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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

#
# @test
# @bug     4164450
# @summary Ensure that javah/javadoc doesn't try to read (new) source files
# @author  Peter von der Ah\u00e9
# @run shell ReadOldClass.sh
#

TS=${TESTSRC-.}
TC=${TESTCLASSES-.}

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | CYGWIN* )
    PS=":"
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

cat > "${TC}/ReadOldClass.java" <<EOF
public class ReadOldClass {
    public static void main(String[] args) {
    }
}
EOF

rm -f ${TC}/ReadOldClass.h

set -e

# compile the file
"${TESTJAVA}${FS}bin${FS}javac" ${TESTTOOLVMOPTS} -d "${TC}" "${TC}/ReadOldClass.java"
# ensure the source file is newer than the class file
touch "${TC}/ReadOldClass.java"
"${TESTJAVA}${FS}bin${FS}javah" ${TESTTOOLVMOPTS} -jni -classpath "${TC}" -d "${TC}" ReadOldClass

test -f "${TC}/ReadOldClass.h"
