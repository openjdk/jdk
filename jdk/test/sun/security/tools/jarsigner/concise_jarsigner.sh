#
# Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6802846
# @summary jarsigner needs enhanced cert validation(options)
#
# @run shell concise_jarsigner.sh
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

KT="$TESTJAVA${FS}bin${FS}keytool -storepass changeit -keypass changeit -keystore js.jks"
JAR=$TESTJAVA${FS}bin${FS}jar
JARSIGNER=$TESTJAVA${FS}bin${FS}jarsigner
JAVAC=$TESTJAVA${FS}bin${FS}javac

rm js.jks

echo class A1 {} > A1.java
echo class A2 {} > A2.java
echo class A3 {} > A3.java
echo class A4 {} > A4.java
echo class A5 {} > A5.java
echo class A6 {} > A6.java

$JAVAC A1.java A2.java A3.java A4.java A5.java A6.java
YEAR=`date +%Y`

# ==========================================================
# First part: output format
# ==========================================================

$KT -genkeypair -alias a1 -dname CN=a1 -validity 365
$KT -genkeypair -alias a2 -dname CN=a2 -validity 365

# a.jar includes 8 unsigned, 2 signed by a1 and a2, 2 signed by a3
$JAR cvf a.jar A1.class A2.class
$JARSIGNER -keystore js.jks -storepass changeit a.jar a1
$JAR uvf a.jar A3.class A4.class
$JARSIGNER -keystore js.jks -storepass changeit a.jar a2
$JAR uvf a.jar A5.class A6.class

# Verify OK
$JARSIGNER -verify a.jar
[ $? = 0 ] || exit $LINENO

# 4(chainNotValidated)+16(hasUnsignedEntry)
$JARSIGNER -verify a.jar -strict
[ $? = 20 ] || exit $LINENO

# 16(hasUnsignedEntry)
$JARSIGNER -verify a.jar -strict -keystore js.jks
[ $? = 16 ] || exit $LINENO

# 16(hasUnsignedEntry)+32(notSignedByAlias)
$JARSIGNER -verify a.jar a1 -strict -keystore js.jks
[ $? = 48 ] || exit $LINENO

# 16(hasUnsignedEntry)
$JARSIGNER -verify a.jar a1 a2 -strict -keystore js.jks
[ $? = 16 ] || exit $LINENO

# 12 entries all together
LINES=`$JARSIGNER -verify a.jar -verbose | grep $YEAR | wc -l`
[ $LINES = 12 ] || exit $LINENO

# 12 entries all listed
LINES=`$JARSIGNER -verify a.jar -verbose:grouped | grep $YEAR | wc -l`
[ $LINES = 12 ] || exit $LINENO

# 4 groups: MANIFST, unrelated, signed, unsigned
LINES=`$JARSIGNER -verify a.jar -verbose:summary | grep $YEAR | wc -l`
[ $LINES = 4 ] || exit $LINENO

# still 4 groups, but MANIFEST group has no other file
LINES=`$JARSIGNER -verify a.jar -verbose:summary | grep "more)" | wc -l`
[ $LINES = 3 ] || exit $LINENO

# 5 groups: MANIFEST, unrelated, signed by a1/a2, signed by a2, unsigned
LINES=`$JARSIGNER -verify a.jar -verbose:summary -certs | grep $YEAR | wc -l`
[ $LINES = 5 ] || exit $LINENO

# 2 for MANIFEST, 2*2 for A1/A2, 2 for A3/A4
LINES=`$JARSIGNER -verify a.jar -verbose -certs | grep "\[certificate" | wc -l`
[ $LINES = 8 ] || exit $LINENO

# a1,a2 for MANIFEST, a1,a2 for A1/A2, a2 for A3/A4
LINES=`$JARSIGNER -verify a.jar -verbose:grouped -certs | grep "\[certificate" | wc -l`
[ $LINES = 5 ] || exit $LINENO

# a1,a2 for MANIFEST, a1,a2 for A1/A2, a2 for A3/A4
LINES=`$JARSIGNER -verify a.jar -verbose:summary -certs | grep "\[certificate" | wc -l`
[ $LINES = 5 ] || exit $LINENO

# still 5 groups, but MANIFEST group has no other file
LINES=`$JARSIGNER -verify a.jar -verbose:summary -certs | grep "more)" | wc -l`
[ $LINES = 4 ] || exit $LINENO

# ==========================================================
# Second part: exit code 2, 4, 8
# 16 and 32 already covered in the first part
# ==========================================================

$KT -genkeypair -alias expiring -dname CN=expiring -startdate -1m
$KT -genkeypair -alias expired -dname CN=expired -startdate -10m
$KT -genkeypair -alias notyetvalid -dname CN=notyetvalid -startdate +1m
$KT -genkeypair -alias badku -dname CN=badku -ext KU=cRLSign -validity 365
$KT -genkeypair -alias badeku -dname CN=badeku -ext EKU=sa -validity 365
$KT -genkeypair -alias goodku -dname CN=goodku -ext KU=dig -validity 365
$KT -genkeypair -alias goodeku -dname CN=goodeku -ext EKU=codesign -validity 365

# badchain signed by ca, but ca is removed later
$KT -genkeypair -alias badchain -dname CN=badchain -validity 365
$KT -genkeypair -alias ca -dname CN=ca -ext bc -validity 365
$KT -certreq -alias badchain | $KT -gencert -alias ca -validity 365 | \
        $KT -importcert -alias badchain
$KT -delete -alias ca

$JARSIGNER -strict -keystore js.jks -storepass changeit a.jar expiring
[ $? = 2 ] || exit $LINENO

$JARSIGNER -strict -keystore js.jks -storepass changeit a.jar expired
[ $? = 4 ] || exit $LINENO

$JARSIGNER -strict -keystore js.jks -storepass changeit a.jar notyetvalid
[ $? = 4 ] || exit $LINENO

$JARSIGNER -strict -keystore js.jks -storepass changeit a.jar badku
[ $? = 8 ] || exit $LINENO

$JARSIGNER -strict -keystore js.jks -storepass changeit a.jar badeku
[ $? = 8 ] || exit $LINENO

$JARSIGNER -strict -keystore js.jks -storepass changeit a.jar goodku
[ $? = 0 ] || exit $LINENO

$JARSIGNER -strict -keystore js.jks -storepass changeit a.jar goodeku
[ $? = 0 ] || exit $LINENO

$JARSIGNER -strict -keystore js.jks -storepass changeit a.jar badchain
[ $? = 4 ] || exit $LINENO

$JARSIGNER -verify a.jar
[ $? = 0 ] || exit $LINENO

# ==========================================================
# Third part: -certchain test
# ==========================================================

# altchain signed by ca2, but ca2 is removed later
$KT -genkeypair -alias altchain -dname CN=altchain -validity 365
$KT -genkeypair -alias ca2 -dname CN=ca2 -ext bc -validity 365
$KT -certreq -alias altchain | $KT -gencert -alias ca2 -validity 365 -rfc > certchain
$KT -exportcert -alias ca2 -rfc >> certchain
$KT -delete -alias ca2

# Now altchain is still self-signed
$JARSIGNER -strict -keystore js.jks -storepass changeit a.jar altchain
[ $? = 0 ] || exit $LINENO

# If -certchain is used, then it's bad
$JARSIGNER -strict -keystore js.jks -storepass changeit -certchain certchain a.jar altchain
[ $? = 4 ] || exit $LINENO

$JARSIGNER -verify a.jar
[ $? = 0 ] || exit $LINENO

echo OK
exit 0
