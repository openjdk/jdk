#
# Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6449565
# @run shell/timeout=140 OldSocketImpl.sh
# @summary Pre-1.4 SocketImpl no longer supported

OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin )
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    PS=";"
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

# no need to compile the test. It is already compiled
# with 1.3 and in OldStyleImpl.jar

# run
${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -cp ${TESTSRC}${FS}OldSocketImpl.jar OldSocketImpl
result=$?
if [ "$result" -ne "0" ]; then
    exit 1
fi

# no failures, exit.
exit 0

