#
# Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4348370
# @bug 8015274
# @bug 8015276
# @bug 8016158
# @summary policytool not i18n compliant
#
# @run applet/manual=done i18n.html
# @run shell/timeout=1200 i18n.sh
# @run applet/manual=yesno i18n.html

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
    NULL=/dev/null
    PS=":"
    FS="/"
    ;;
  CYGWIN* )
    NULL=/dev/null
    PS=";"
    FS="/"
    ;;
  Windows* )
    NULL=NUL
    PS=";"
    FS="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

# the test code

echo "HELLO!"

echo "Checking for $HOME/.java.policy"

# 8015274
if [ -e $HOME/.java.policy ]; then
    echo "You have a .java.policy file in your HOME directory"
    echo "The file must be removed before running this test"
    exit 1
fi

${TESTJAVA}${FS}bin${FS}keytool -genkeypair -alias hello -dname CN=Hello \
        -storepass changeit -keypass changeit -keystore ks
echo changeit > good
echo badpass > bad
${TESTJAVA}${FS}bin${FS}policytool

exit $?

