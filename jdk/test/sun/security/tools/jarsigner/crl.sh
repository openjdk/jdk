#
# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6890876 6950931
# @summary jarsigner can add CRL info into signed jar (updated)
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

rm $KS $JFILE 2> /dev/null

# Generates some crl files, each containing two entries

$KT -alias a -dname CN=a -keyalg rsa -genkey -validity 300
$KT -alias a -gencrl -id 1:1 -id 2:2 -file crl1
$KT -alias a -gencrl -id 3:3 -id 4:4 -file crl2
$KT -alias b -dname CN=b -keyalg rsa -genkey -validity 300
$KT -alias b -gencrl -id 5:1 -id 6:2 -file crl3

cat > ToURI.java <<EOF
class ToURI {
    public static void main(String[] args) throws Exception {
        System.out.println(new java.io.File("crl1").toURI());
    }
}
EOF
$TESTJAVA${FS}bin${FS}javac ToURI.java
$TESTJAVA${FS}bin${FS}java ToURI > uri
$KT -alias c -dname CN=c -keyalg rsa -genkey -validity 300 \
    -ext crl=uri:`cat uri`

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
        -crl crl1 -crl crl2 || exit 2
$JARSIGNER -keystore $KS -storepass changeit $JFILE b \
        -crl crl3 -crl crl2 || exit 3
$JARSIGNER -keystore $KS -verify -debug -strict $JFILE || exit 3
$KT -printcert -jarfile $JFILE | grep CRLs || exit 4
CRLCOUNT=`$KT -printcert -jarfile $JFILE | grep SerialNumber | wc -l`
if [ $CRLCOUNT != 8 ]; then exit 5; fi

exit 0
