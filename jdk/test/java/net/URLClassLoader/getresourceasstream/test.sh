#!/bin/sh

#
# Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

set -x
# @test
# @bug 5103449
# @run shell test.sh
# @summary REGRESSION: getResourceAsStream is broken in JDK1.5.0-rc
#      


cat << POLICY > policy
grant {
    permission java.lang.RuntimePermission "createClassLoader";
};
POLICY

checkExit () {
    if [ $? != 0 ]; then
	exit 1;
    fi
}

${TESTJAVA}/bin/javac -d . ${TESTSRC}/Test.java
cp ${TESTSRC}/test.jar .

${TESTJAVA}/bin/java Test
checkExit 

# try with security manager

${TESTJAVA}/bin/java -Djava.security.policy=file:./policy -Djava.security.manager Test
checkExit 
exit 0
