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
# @bug 8044755
# @summary Add a test for algorithm constraints check in jarsigner
#

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

# The sigalg used is MD2withRSA, which is obsolete.

KT="$TESTJAVA/bin/keytool ${TESTTOOLVMOPTS} -keystore ks
    -storepass changeit -keypass changeit
    -keyalg rsa -sigalg MD2withRSA -debug"
JS="$TESTJAVA/bin/jarsigner ${TESTTOOLVMOPTS} -keystore ks
    -storepass changeit -strict -debug"
JAR="$TESTJAVA/bin/jar ${TESTTOOLVMOPTS}"

rm ks 2> /dev/null

$KT -genkeypair -alias ca -dname CN=CA -ext bc
$KT -genkeypair -alias signer -dname CN=Signer

$KT -certreq -alias signer | \
        $KT -gencert -alias ca -ext ku=dS -rfc | \
        $KT -importcert -alias signer

$JAR cvf a.jar ks

# We always trust a TrustedCertificateEntry
$JS a.jar ca | grep "chain is not validated" && exit 1

# An end-entity cert must follow algorithm constraints
$JS a.jar signer | grep "chain is not validated" || exit 2

exit 0
