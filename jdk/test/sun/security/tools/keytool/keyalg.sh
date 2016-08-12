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
# @bug 8029659
# @summary Keytool, print key algorithm of certificate or key entry
#

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

TESTTOOLVMOPTS="$TESTTOOLVMOPTS -J-Duser.language=en -J-Duser.country=US"

KS=ks
KEYTOOL="$TESTJAVA/bin/keytool ${TESTTOOLVMOPTS} -keystore ks -storepass changeit -keypass changeit"

rm $KS 2> /dev/null

$KEYTOOL -genkeypair -alias ca -dname CN=CA -keyalg EC || exit 1
$KEYTOOL -genkeypair -alias user -dname CN=User -keyalg RSA -keysize 1024 || exit 2
$KEYTOOL -certreq -alias user |
        $KEYTOOL -gencert -alias ca -rfc -sigalg SHA1withECDSA |
        $KEYTOOL -printcert > user.dump || exit 3

cat user.dump | grep "Signature algorithm name:" | grep SHA1withECDSA || exit 4
cat user.dump | grep "Subject Public Key Algorithm:" | grep RSA | grep 1024 || exit 5

