#
# Copyright (c) 2005, 2022, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8285838
# @summary This test checks to ensure that daylight savings rules are followed 
# appropriately when setting a custom timezone ID via the TZ env variable.
# @requires os.family != "windows"
# @run shell runCustomTzIDCheckDST.sh

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set.  Test cannot execute.  Failed."
  exit 1
fi

. ${TESTROOT}/javax/script/CommonSetup.sh

OS=`uname -s`
case "$OS" in 
  Linux | AIX )
    TEST=CustomTzIDCheckDST

    export TZ="MEZ-1MESZ,M3.5.0,M10.5.0"

    ${JAVAC} ${TESTVMOPTS} -d ${TESTCLASSES}/ ${TESTSRC}/${TEST}.java
    ${JAVA} ${TESTVMOPTS} -classpath ${TESTCLASSES} ${TEST}

    ret=$?
    if [ $ret -ne 0 ]
    then
      exit $ret
    fi
  ;;
esac

