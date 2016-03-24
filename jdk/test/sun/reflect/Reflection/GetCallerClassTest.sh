#
# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8010117
# @summary Test if the VM enforces sun.reflect.Reflection.getCallerClass 
#          be called by methods annotated with sun.reflect.CallerSensitive
#
# @run shell GetCallerClassTest.sh

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
if [ "${COMPILEJAVA}" = "" ]
then
  COMPILEJAVA="${TESTJAVA}"
fi
echo "COMPILEJAVA=${COMPILEJAVA}"
if [ "${TESTCLASSES}" = "" ]
then
  echo "TESTCLASSES not set.  Test cannot execute.  Failed."
  exit 1
fi

BCP=${TESTCLASSES}/bcp
rm -rf ${BCP}
mkdir ${BCP}

EXTRAOPTS="-XaddExports:java.base/sun.reflect=ALL-UNNAMED"

# Compile GetCallerClass in bootclasspath
${COMPILEJAVA}/bin/javac ${TESTTOOLVMOPTS} ${EXTRAOPTS} \
     -XDignore.symbol.file \
     -d ${BCP} ${TESTSRC}/GetCallerClass.java  || exit 1

${COMPILEJAVA}/bin/javac ${TESTTOOLVMOPTS} ${EXTRAOPTS} \
     -XDignore.symbol.file -cp ${BCP} \
     -d ${TESTCLASSES} ${TESTSRC}/GetCallerClassTest.java  || exit 2

${TESTJAVA}/bin/java ${TESTVMOPTS} ${EXTRAOPTS} -Xbootclasspath/a:${BCP} \
     -cp ${TESTCLASSES} GetCallerClassTest || exit 3
