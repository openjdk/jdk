#
# Copyright (c) 2007, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6543842 6543440 6939248 8009636 8024302
# @summary checking response of timestamp
#
# @run shell/timeout=600 ts.sh

# Run for a long time because jarsigner with timestamp needs to create a
# 64-bit random number and it might be extremely slow on a machine with
# not enough entropy pool

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

if [ "${TESTSRC}" = "" ] ; then
  TESTSRC="."
fi
if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

JAR="${TESTJAVA}${FS}bin${FS}jar"
JAVA="${TESTJAVA}${FS}bin${FS}java"
JAVAC="${TESTJAVA}${FS}bin${FS}javac"
KT="${TESTJAVA}${FS}bin${FS}keytool -keystore tsks -storepass changeit -keypass changeit -keyalg rsa -validity 200"

rm tsks
echo Nothing > A
rm old.jar
$JAR cvf old.jar A

# ca is CA
# old is signer for code
# ts is signer for timestamp
# tsbad1 has no extendedKeyUsage
# tsbad2's extendedKeyUsage is non-critical
# tsbad3's extendedKeyUsage has no timestamping

$KT -alias ca -genkeypair -ext bc -dname CN=CA
$KT -alias old -genkeypair -dname CN=old
$KT -alias ts -genkeypair -dname CN=ts
$KT -alias tsbad1 -genkeypair -dname CN=tsbad1
$KT -alias tsbad2 -genkeypair -dname CN=tsbad2
$KT -alias tsbad3 -genkeypair -dname CN=tsbad3
$KT -alias ts -certreq | \
        $KT -alias ca -gencert -ext eku:critical=ts | \
        $KT -alias ts -importcert
$KT -alias tsbad1 -certreq | \
        $KT -alias ca -gencert | \
        $KT -alias tsbad1 -importcert
$KT -alias tsbad2 -certreq | \
        $KT -alias ca -gencert -ext eku=ts | \
        $KT -alias tsbad2 -importcert
$KT -alias tsbad3 -certreq | \
        $KT -alias ca -gencert -ext eku:critical=cs | \
        $KT -alias tsbad3 -importcert

$JAVAC -d . ${TESTSRC}/TimestampCheck.java
$JAVA ${TESTVMOPTS} TimestampCheck

