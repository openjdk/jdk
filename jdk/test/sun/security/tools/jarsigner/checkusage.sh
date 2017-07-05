#
# Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 7004168
# @summary jarsigner -verify checks for KeyUsage codesigning ext on all certs
#  instead of just signing cert
#
# @run shell checkusage.sh
#

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

KT="$TESTJAVA${FS}bin${FS}keytool ${TESTTOOLVMOPTS} -storepass changeit -keypass changeit -keyalg rsa"
JAR="$TESTJAVA${FS}bin${FS}jar ${TESTTOOLVMOPTS}"
JARSIGNER="$TESTJAVA${FS}bin${FS}jarsigner ${TESTTOOLVMOPTS}"

rm js.jks trust.jks unrelated.jks 2> /dev/null

echo x > x
$JAR cvf a.jar x

################### 3 Keystores #######################

# Keystore js.jks: including CA and Publisher
# CA contains a non-empty KeyUsage
$KT -keystore js.jks -genkeypair -alias ca -dname CN=CA -ext KU=kCS -ext bc -validity 365
$KT -keystore js.jks -genkeypair -alias pub -dname CN=Publisher

# Publisher contains the correct KeyUsage
$KT -keystore js.jks -certreq -alias pub | \
        $KT -keystore js.jks -gencert -alias ca -ext KU=dig -validity 365 | \
        $KT -keystore js.jks -importcert -alias pub

# Keystore trust.jks: including CA only
$KT -keystore js.jks -exportcert -alias ca | \
        $KT -keystore trust.jks -importcert -alias ca -noprompt

# Keystore unrelated.jks: unrelated
$KT -keystore unrelated.jks -genkeypair -alias nothing -dname CN=Nothing -validity 365


################### 4 Tests #######################

# Test 1: Sign should be OK

$JARSIGNER -keystore js.jks -storepass changeit a.jar pub
RESULT=$?
echo $RESULT
#[ $RESULT = 0 ] || exit 1

# Test 2: Verify should be OK

$JARSIGNER -keystore trust.jks -strict -verify a.jar
RESULT=$?
echo $RESULT
#[ $RESULT = 0 ] || exit 2

# Test 3: When no keystore is specified, the error is only
# "chain not validated"

$JARSIGNER -strict -verify a.jar
RESULT=$?
echo $RESULT
#[ $RESULT = 4 ] || exit 3

# Test 4: When unrelated keystore is specified, the error is
# "chain not validated" and "not alias in keystore"

$JARSIGNER -keystore unrelated.jks -strict -verify a.jar
RESULT=$?
echo $RESULT
#[ $RESULT = 36 ] || exit 4

exit 0
