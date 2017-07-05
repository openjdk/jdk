#
# Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# @test
# @bug 6890876
# @summary jarsigner can add CRL info into signed jar
#

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

# set platform-dependent variables
# PF: platform name, say, solaris-sparc

PF=""

OS=`uname -s`
case "$OS" in
  Windows* )
    FS="\\"
    ;;
  * )
    FS="/"
    ;;
esac

KS=crl.jks
JFILE=crl.jar

KT="$TESTJAVA${FS}bin${FS}keytool -storepass changeit -keypass changeit -keystore $KS"
JAR=$TESTJAVA${FS}bin${FS}jar
JARSIGNER=$TESTJAVA${FS}bin${FS}jarsigner

rm $KS $JFILE

# Generates some crl files, each containing two entries

$KT -alias a -dname CN=a -keyalg rsa -genkey -validity 300
$KT -alias a -gencrl -id 1:1 -id 2:2 -file crl1
$KT -alias a -gencrl -id 3:3 -id 4:4 -file crl2
$KT -alias b -dname CN=b -keyalg rsa -genkey -validity 300
$KT -alias b -gencrl -id 5:1 -id 6:2 -file crl3

$KT -alias c -dname CN=c -keyalg rsa -genkey -validity 300 \
    -ext crl=uri:file://`pwd`/crl1

echo A > A

# Test -crl:auto, cRLDistributionPoints is a local file

$JAR cvf $JFILE A
$JARSIGNER -keystore $KS -storepass changeit $JFILE c \
        -crl:auto || exit 1
$JARSIGNER -keystore $KS -verify -debug -strict $JFILE || exit 6
$KT -printcert -jarfile $JFILE | grep CRLs || exit 7

# Test -crl <file>

$JAR cvf $JFILE A
$JARSIGNER -keystore $KS -storepass changeit $JFILE a \
        -crl crl1 -crl crl2 || exit 1
$JARSIGNER -keystore $KS -storepass changeit $JFILE b \
        -crl crl3 -crl crl2 || exit 1
$JARSIGNER -keystore $KS -verify -debug -strict $JFILE || exit 3
$KT -printcert -jarfile $JFILE | grep CRLs || exit 4
CRLCOUNT=`$KT -printcert -jarfile $JFILE | grep SerialNumber | wc -l`
if [ $CRLCOUNT != 8 ]; then exit 5; fi

exit 0
