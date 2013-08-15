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
# @bug 8021789
# @summary jarsigner parses alias as command line option (depending on locale)
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

F=collator
KS=collator.jks
JFILE=collator.jar

KT="$TESTJAVA${FS}bin${FS}keytool -storepass changeit -keypass changeit \
        -keystore $KS"
JAR=$TESTJAVA${FS}bin${FS}jar
JARSIGNER="$TESTJAVA${FS}bin${FS}jarsigner -keystore $KS -storepass changeit"

rm $F $KS $JFILE 2> /dev/null

echo 12345 > $F
$JAR cvf $JFILE $F

ERR=""

$KT -alias debug -dname CN=debug -genkey -validity 300 || ERR="$ERR 1"

# use "debug" as alias name
$JARSIGNER $JFILE debug || ERR="$ERR 2"

# use "" as alias name (although there will be a warning)
$JARSIGNER -verify $JFILE "" || ERR="$ERR 3"

if [ "$ERR" = "" ]; then
    exit 0
else
    echo "ERR is $ERR"
    exit 1
fi


