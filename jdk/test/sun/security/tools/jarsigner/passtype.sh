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
# @bug 6868579
# @summary RFE: jarsigner to support reading password from environment variable
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

KS=pt.jks
JFILE=pt.jar

KT="$TESTJAVA${FS}bin${FS}keytool -keystore $KS -validity 300"
JAR=$TESTJAVA${FS}bin${FS}jar
JARSIGNER=$TESTJAVA${FS}bin${FS}jarsigner

rm $KS $JFILE

$KT -alias a -dname CN=a -keyalg rsa -genkey \
        -storepass test12 -keypass test12 || exit 1
PASSENV=test12 $KT -alias b -dname CN=b -keyalg rsa -genkey \
        -storepass:env PASSENV -keypass:env PASSENV || exit 2
echo test12 > passfile
$KT -alias c -dname CN=c -keyalg rsa -genkey \
        -storepass:file passfile -keypass:file passfile || exit 3

echo A > A
$JAR cvf $JFILE A

$JARSIGNER -keystore $KS -storepass test12 $JFILE a || exit 4
PASSENV=test12 $JARSIGNER -keystore $KS -storepass:env PASSENV $JFILE b || exit 5
$JARSIGNER -keystore $KS -storepass:file passfile $JFILE b || exit 6

$JARSIGNER -keystore $KS -verify -debug -strict $JFILE || exit 7

exit 0

