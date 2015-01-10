#
# Copyright (c) 2009, 2014 Oracle and/or its affiliates. All rights reserved.
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
# @bug 6876328
# @summary different names for the same digest algorithms breaks jarsigner
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

KS=nc.ks
JFILE=nc.jar

KT="$TESTJAVA${FS}bin${FS}keytool ${TESTTOOLVMOPTS} -storepass changeit -keypass changeit -keystore $KS"
JAR="$TESTJAVA${FS}bin${FS}jar ${TESTTOOLVMOPTS}"
JARSIGNER="$TESTJAVA${FS}bin${FS}jarsigner ${TESTTOOLVMOPTS} -keystore $KS -storepass changeit"

rm $KS $JFILE

$KT -alias a -dname CN=a -keyalg rsa -genkey -validity 300
$KT -alias b -dname CN=b -keyalg rsa -genkey -validity 300

echo A > A
$JAR cvf $JFILE A

$JARSIGNER $JFILE a -digestalg SHA1 || exit 1
$JARSIGNER $JFILE b -digestalg SHA-1 || exit 2

$JARSIGNER -verify -debug -strict $JFILE || exit 3

exit 0

