#
# Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8036709
# @summary Java 7 jarsigner displays warning about cert policy tree
#
# @run shell certpolicy.sh
#

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

KT="$TESTJAVA/bin/keytool $TESTTOOLVMOPTS \
        -keypass changeit -storepass changeit -keystore ks -keyalg rsa"
JS="$TESTJAVA/bin/jarsigner $TESTTOOLVMOPTS -storepass changeit -keystore ks"
JAR="$TESTJAVA/bin/jar $TESTTOOLVMOPTS"

rm ks 2> /dev/null
$KT -genkeypair -alias ca -dname CN=CA -ext bc
$KT -genkeypair -alias int -dname CN=Int
$KT -genkeypair -alias ee -dname CN=EE

# CertificatePolicies [[PolicyId: [1.2.3]], [PolicyId: [1.2.4]]]
# PolicyConstraints: [Require: 0; Inhibit: unspecified]
$KT -certreq -alias int | \
        $KT -gencert -rfc -alias ca \
                -ext 2.5.29.32="30 0C 30 04 06 02 2A 03 30 04 06 02 2A 04" \
                -ext "2.5.29.36=30 03 80 01 00" -ext bc | \
        $KT -import -alias int

# CertificatePolicies [[PolicyId: [1.2.3]]]
$KT -certreq -alias ee | \
        $KT -gencert -rfc -alias int \
                -ext 2.5.29.32="30 06 30 04 06 02 2A 03" | \
        $KT -import -alias ee

$KT -export -alias ee -rfc > cc
$KT -export -alias int -rfc >> cc
$KT -export -alias ca -rfc >> cc

$KT -delete -alias int

ERR=''
$JAR cvf a.jar cc

# Make sure the certchain in the signed jar contains all 3 certs
$JS -strict -certchain cc a.jar ee -debug || ERR="sign"
$JS -strict -verify a.jar -debug || ERR="$ERR verify"

if [ "$ERR" = "" ]; then
    echo "Success"
    exit 0
else
    echo "Failed: $ERR"
    exit 1
fi

