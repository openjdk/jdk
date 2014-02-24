#
# Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @summary (almost) all keytool behaviors
# @author Weijun Wang
# @run shell/timeout=600 standard.sh
#
# This test is always excecuted.
#
# set a few environment variables so that the shell-script can run stand-alone
# in the source directory
if [ "${TESTSRC}" = "" ] ; then
  TESTSRC="."
fi
if [ "${TESTCLASSES}" = "" ] ; then
  TESTCLASSES="."
fi
if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
  COMPILEJAVA="${TESTJAVA}"
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin | AIX | CYGWIN* )
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

${COMPILEJAVA}${FS}bin${FS}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d . -XDignore.symbol.file ${TESTSRC}${FS}KeyToolTest.java || exit 10

echo | ${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} -Dfile KeyToolTest
status=$?

rm HumanInputStream*.class
rm KeyToolTest*.class
rm TestException.class

exit $status

