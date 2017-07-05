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
# @bug 8022761
# @summary regression: SecurityException is NOT thrown while trying to pack a wrongly signed Indexed Jar file
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

F=abcde
KS=jvindex.jks
JFILE=jvindex.jar

KT="$TESTJAVA${FS}bin${FS}keytool -storepass changeit -keypass changeit \
        -keystore $KS -keyalg rsa"
JAR=$TESTJAVA${FS}bin${FS}jar
JARSIGNER="$TESTJAVA${FS}bin${FS}jarsigner -keystore $KS -storepass changeit"

rm $F $KS $JFILE 2> /dev/null

echo 12345 > $F
$JAR cvf $JFILE $F

ERR=""

$KT -alias a -dname CN=a -genkey -validity 300 || ERR="$ERR 1"

$JARSIGNER $JFILE a || ERR="$ERR 2"
$JAR i $JFILE

# Make sure the $F line has "sm" (signed and in manifest)
$JARSIGNER -verify -verbose $JFILE | grep $F | grep sm || ERR="$ERR 3"

if [ "$ERR" = "" ]; then
    exit 0
else
    echo "ERR is $ERR"
    exit 1
fi


