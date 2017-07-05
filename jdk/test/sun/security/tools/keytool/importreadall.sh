#
# Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6819272
# @summary keytool -importcert should read the whole input
#
# @run shell importreadall.sh

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory
if [ "${TESTSRC}" = "" ] ; then
   TESTSRC="."
fi

if [ "${TESTJAVA}" = "" ] ; then
  JAVA_CMD=`which java`
  TESTJAVA=`dirname $JAVA_CMD`/..
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Windows_* )
    FS="\\"
    ;;
  * )
    FS="/"
    ;;
esac

KEYTOOL="${TESTJAVA}${FS}bin${FS}keytool -keystore importreadall.jks -storepass changeit -keypass changeit -keyalg rsa"

# In case the test is run twice in the same directory

$KEYTOOL -delete -alias a
$KEYTOOL -delete -alias ca
$KEYTOOL -genkeypair -alias a -dname CN=a || exit 1
$KEYTOOL -genkeypair -alias ca -dname CN=ca || exit 2
$KEYTOOL -certreq -alias a | $KEYTOOL -gencert -alias ca | $KEYTOOL -importcert -alias a

exit $?
