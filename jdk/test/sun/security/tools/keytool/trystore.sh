#
# Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
# @bug 7047200
# @summary keytool can try save to a byte array before overwrite the file

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
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

rm trystore.jks 2> /dev/null

KEYTOOL="${TESTJAVA}${FS}bin${FS}keytool -storetype jks -keystore trystore.jks"
$KEYTOOL -genkeypair -alias a -dname CN=A -storepass changeit -keypass changeit
$KEYTOOL -genkeypair -alias b -dname CN=B -storepass changeit -keypass changeit

# We use -protected for JKS keystore. This is illegal so the command should
# fail. Then we can check if the keystore is damaged.

$KEYTOOL -genkeypair -protected -alias b -delete -debug

if [ $? = 0 ]; then
    echo "What? -protected works for JKS?"
    exit 1
fi

$KEYTOOL -list -storepass changeit

if [ $? != 0 ]; then
    echo "Keystore file damaged"
    exit 2
fi
