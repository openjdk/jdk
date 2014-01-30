#
# Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4312217 4785473
# @summary Test the use of the -classpath switch in the serialver application.
# @author Naveen Sanjeeva
#
# @build Test
# @run shell run.sh

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory

if [ "${TESTSRC}" = "" ] ; then
  TESTSRC="."
fi
if [ "${TESTCLASSES}" = "" ] ; then
  TESTCLASSES="."
fi
if [ "${TESTJAVA}" = "" ] ; then
  echo "TESTJAVA not set.  Test cannot execute."
  echo "FAILED!!!"
  exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin | AIX )
    PS=":"    ;;
  Windows* | CYGWIN* )
    PS=";"    ;;
  * )
    echo "Unrecognized system!"
    exit 1    ;;
esac

# the test code

echo "Using the classpath .${PS}${TESTCLASSES}"
${TESTJAVA}/bin/serialver -classpath ".${PS}${TESTCLASSES}" 'serialver.Test.Test1.Test2'

exit $?
