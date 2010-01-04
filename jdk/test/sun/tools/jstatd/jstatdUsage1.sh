#
# Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# @test
# @bug 4990825
# @run shell jstatdUsage1.sh
# @summary Test that output of 'jstatd -help' matches the usage.out file

. ${TESTSRC-.}/../../jvmstat/testlibrary/utils.sh

setup

JSTATD="${TESTJAVA}/bin/jstatd"

JSTATD_1_OUT="jstatd_$$_1.out"
JSTATD_2_OUT="jstatd_$$_2.out"

${JSTATD} -? > ${JSTATD_1_OUT} 2>&1

diff -w ${JSTATD_1_OUT} ${TESTSRC}/usage.out
if [ $? != 0 ]
then
  echo "Output of jstatd -? differs from expected output. Failed."
  exit 1
fi

${JSTATD} -help > ${JSTATD_2_OUT} 2>&1

diff -w ${JSTATD_2_OUT} ${TESTSRC}/usage.out
if [ $? != 0 ]
then
  echo "Output of jstatd -help differs from expected output. Failed."
  exit 1
fi

exit 0
