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
# @bug 6870812
# @summary enhance security tools to use ECC algorithm
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

KS=ec.jks
JFILE=ec.jar

KT="$TESTJAVA${FS}bin${FS}keytool -storepass changeit -keypass changeit -keystore $KS"
JAR=$TESTJAVA${FS}bin${FS}jar
JARSIGNER=$TESTJAVA${FS}bin${FS}jarsigner

rm $KS $JFILE
echo A > A
$JAR cvf $JFILE A

$KT -alias a -dname CN=a -keyalg ec -genkey -validity 300 || exit 11
$KT -alias b -dname CN=b -keyalg ec -genkey -validity 300 || exit 12
$KT -alias c -dname CN=c -keyalg ec -genkey -validity 300 || exit 13
$KT -alias x -dname CN=x -keyalg ec -genkey -validity 300 || exit 14

$JARSIGNER -keystore $KS -storepass changeit $JFILE a -debug -strict || exit 21
$JARSIGNER -keystore $KS -storepass changeit $JFILE b -debug -strict -sigalg SHA1withECDSA || exit 22
$JARSIGNER -keystore $KS -storepass changeit $JFILE c -debug -strict -sigalg SHA512withECDSA || exit 23

$JARSIGNER -keystore $KS -storepass changeit -verify $JFILE a -debug -strict || exit 31
$JARSIGNER -keystore $KS -storepass changeit -verify $JFILE b -debug -strict || exit 32
$JARSIGNER -keystore $KS -storepass changeit -verify $JFILE c -debug -strict || exit 33

# Not signed by x, should exit with non-zero
$JARSIGNER -keystore $KS -storepass changeit -verify $JFILE x -debug -strict && exit 34

exit 0

