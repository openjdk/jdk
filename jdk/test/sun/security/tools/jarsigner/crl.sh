#
# Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

KT="$TESTJAVA${FS}bin${FS}keytool -storepass changeit -keypass changeit -keystore $KS -keyalg rsa"

rm $KS 2> /dev/null

# Test keytool -gencrl

$KT -alias a -dname CN=a -keyalg rsa -genkey -validity 300
$KT -alias a -gencrl -id 1:1 -id 2:2 -file crl1 || exit 1
$KT -alias a -gencrl -id 3:3 -id 4:4 -file crl2 || exit 2
$KT -alias a -gencrl -id 5:1 -id 6:2 -file crl3 || exit 4

# Test keytool -printcrl

$KT -printcrl -file crl1 || exit 5
$KT -printcrl -file crl2 || exit 6
$KT -printcrl -file crl3 || exit 7


# Test keytool -ext crl

$KT -alias b -dname CN=c -keyalg rsa -genkey -validity 300 \
    -ext crl=uri:http://www.example.com/crl || exit 10

exit 0
