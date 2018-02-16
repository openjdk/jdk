# Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
#!/bin/sh

#
# @test testme.sh
# @summary Linux kernel stack guard should not cause segfaults on x86-32
# @compile T.java
# @run shell testme.sh
#

if [ "${TESTSRC}" = "" ]
then
  TESTSRC=${PWD}
  echo "TESTSRC not set.  Using "${TESTSRC}" as default"
fi
echo "TESTSRC=${TESTSRC}"
## Adding common setup Variables for running shell tests.
. ${TESTSRC}/../../test_env.sh

if [ "${VM_OS}" != "linux" ]
then
  echo "Test only valid for Linux"
  exit 0
fi

LD_LIBRARY_PATH=.:${TESTJAVA}/lib/${VM_TYPE}:/usr/lib:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH

${TESTNATIVEPATH}/stack-gap || exit $?
${TESTNATIVEPATH}/stack-gap -XX:+DisablePrimordialThreadGuardPages || exit $?
