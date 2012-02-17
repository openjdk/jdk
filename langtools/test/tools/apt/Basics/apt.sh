#!/bin/sh

#
# Copyright (c) 2004, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4908512 5024825 4957203 4993280 4996963 6174696 6177059 7041249
# @run shell ../verifyVariables.sh
# @run shell apt.sh
# @summary Make sure apt is removed and doesn't come back
# @author Joseph D. Darcy

OS=`uname -s`;
case "${OS}" in
        CYGWIN* )
                DIFFOPTS="--strip-trailing-cr"
        ;;

	* )
	;;
esac

# Verify apt executable does not exist
if [ -f "${TESTJAVA}/bin/apt" -o -f "${TESTJAVA}/bin/apt.exe" ];then
    echo "apt executable should not exist."
    exit 1
fi

# Construct path to javac executable
JAVAC="${TESTJAVA}/bin/javac ${TESTTOOLVMOPTS} -source 1.5 -sourcepath ${TESTSRC} -classpath ${TESTJAVA}/lib/tools.jar -d . "

$JAVAC ${TESTSRC}/NullAPF.java
RESULT=$?

case "${RESULT}" in
        0  )
        echo "Compilation of apt-using source passed improperly."
        exit 1
	;;

        * )
	;;
esac
