#
# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8024302
# @summary Clarify jar verifications
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

KS=warnings.jks
JFILE=warnings.jar

KT="$TESTJAVA${FS}bin${FS}keytool -storepass changeit -keypass changeit \
        -keystore $KS"
JAR=$TESTJAVA${FS}bin${FS}jar
JARSIGNER="$TESTJAVA${FS}bin${FS}jarsigner -keystore $KS -storepass changeit"

rm $KS 2> /dev/null

export LANG=C

echo 12345 > file

ERR=""

# Normal signer expiring on 2100-01-01
$KT -alias s1 -dname CN=s1 -genkey -startdate 2000/01/01 -validity 36525 || ERR="$ERR keytool s1,"
# Cert expiring soon, informational warning
$KT -alias s2 -dname CN=s2 -genkey -validity 100 || ERR="$ERR keytool s2,"
# Cert expired, severe warning
$KT -alias s3 -dname CN=s3 -genkey -startdate -200d -validity 100 || ERR="$ERR keytool s3,"

# noTimestamp is informatiional warning and includes a date
$JAR cvf $JFILE file
$JARSIGNER $JFILE s1 > output1 || ERR="$ERR jarsigner s1,"
$JARSIGNER -strict $JFILE s1 >> output1 || ERR="$ERR jarsigner s1 strict,"
$JARSIGNER -verify $JFILE s1 >> output1 || ERR="$ERR jarsigner s1,"
$JARSIGNER -verify -strict $JFILE s1 >> output1 || ERR="$ERR jarsigner s1 strict,"

cat output1 | grep Warning || ERR="$ERR s1 warning,"
cat output1 | grep Error && ERR="$ERR s1 error,"
cat output1 | grep timestamp | grep 2100-01-01 || ERR="$ERR s1 timestamp,"
cat output1 | grep "with signer errors" && ERR="$ERR s1 err,"

# hasExpiringCert is informatiional warning
$JAR cvf $JFILE file
$JARSIGNER $JFILE s2 > output2 || ERR="$ERR jarsigner s2,"
$JARSIGNER -strict $JFILE s2 >> output2 || ERR="$ERR jarsigner s2 strict,"
$JARSIGNER -verify $JFILE s2 >> output2 || ERR="$ERR jarsigner s2,"
$JARSIGNER -verify -strict $JFILE s2 >> output2 || ERR="$ERR jarsigner s2 strict,"

cat output2 | grep Warning || ERR="$ERR s2 warning,"
cat output2 | grep Error && ERR="$ERR s2 error,"
cat output2 | grep timestamp || ERR="$ERR s2 timestamp,"
cat output2 | grep "will expire" || ERR="$ERR s2 expiring,"
cat output2 | grep "with signer errors" && ERR="$ERR s2 err,"

# hasExpiredCert is severe warning
$JAR cvf $JFILE file
$JARSIGNER $JFILE s3 > output3 || ERR="$ERR jarsigner s3,"
$JARSIGNER -strict $JFILE s3 > output3s && ERR="$ERR jarsigner s3 strict,"
$JARSIGNER -verify $JFILE s3 >> output3 || ERR="$ERR jarsigner s3,"
$JARSIGNER -verify -strict $JFILE s3 >> output3s && ERR="$ERR jarsigner s3 strict,"

# warning without -strict
cat output3 | grep Warning || ERR="$ERR s3 warning,"
cat output3 | grep Error && ERR="$ERR s3 error,"
cat output3 | grep "with signer errors" && ERR="$ERR s3 err,"

# error with -strict
cat output3s | grep Warning || ERR="$ERR s3s warning,"
cat output3s | grep Error || ERR="$ERR s3s error,"
cat output3s | grep "with signer errors" || ERR="$ERR s3 err,"

if [ "$ERR" = "" ]; then
    exit 0
else
    echo "ERR is $ERR"
    exit 1
fi


