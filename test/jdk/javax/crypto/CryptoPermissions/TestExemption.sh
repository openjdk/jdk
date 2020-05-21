#
# Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8161527
# @summary NPE is thrown if exempt application is bundled with specific
#     cryptoPerms
# @requires java.runtime.name ~= "OpenJDK.*"

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Linux | Darwin | AIX | CYGWIN* )
    FS="/"
    ;;
  Windows_* )
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

if [ "${TESTSRC}" = "" ] ; then
  TESTSRC="."
fi
if [ "${TESTCLASSES}" = "" ] ; then
  TESTCLASSES="."
fi
if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`${FS}..
  COMPILEJAVA="${TESTJAVA}"
fi

# Build
${COMPILEJAVA}${FS}bin${FS}javac \
    -d . \
    ${TESTSRC}${FS}TestExemption.java \
    || exit 10

# Package
${COMPILEJAVA}${FS}bin${FS}jar \
    -cvf TestExemption.jar \
    TestExemption.class \
    -C ${TESTSRC} cryptoPerms \
    || exit 10

# Test
${TESTJAVA}${FS}bin${FS}java \
    -classpath TestExemption.jar TestExemption
status=$?

exit $status

