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
# @bug 8023197
# @summary Pre-configured command line options for keytool and jarsigner
#

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

TESTTOOLVMOPTS="$TESTTOOLVMOPTS -J-Duser.language=en -J-Duser.country=US"

KS=ks
KEYTOOL="$TESTJAVA/bin/keytool ${TESTTOOLVMOPTS}"

rm $KS 2> /dev/null

PASS=changeit
export PASS

cat <<EOF > kt.conf
# A Pre-configured options file
keytool.all = -storepass:env PASS -keypass:env PASS -keystore \${user.dir}/$KS -debug
keytool.genkey = -keyalg ec -ext bc
keytool.delete = -keystore nothing
EOF

# kt.conf is read
$KEYTOOL -conf kt.conf -genkeypair -dname CN=A -alias a || exit 1
$KEYTOOL -conf kt.conf -list -alias a -v > a_certinfo || exit 2
grep "Signature algorithm name" a_certinfo | grep ECDSA || exit 3
grep "BasicConstraints" a_certinfo || exit 4

# kt.conf is read, and dup multi-valued options processed as expected
$KEYTOOL -conf kt.conf -genkeypair -dname CN=B -alias b -ext ku=ds \
        || exit 11
$KEYTOOL -conf kt.conf -list -alias b -v > b_certinfo || exit 12
grep "BasicConstraints" b_certinfo || exit 14
grep "DigitalSignature" b_certinfo || exit 15

# Single-valued option in command section override all
$KEYTOOL -conf kt.conf -delete -alias a && exit 16

# Single-valued option on command line overrides again
$KEYTOOL -conf kt.conf -delete -alias b -keystore $KS || exit 17

# Error cases

# File does not exist
$KEYTOOL -conf no-such-file -help -list && exit 31

# Cannot have both standard name (-genkeypair) and legacy name (-genkey)
cat <<EOF > bad.conf
keytool.all = -storepass:env PASS -keypass:env PASS -keystore ks
keytool.genkeypair = -keyalg rsa
keytool.genkey = -keyalg ec
EOF

$KEYTOOL -conf bad.conf -genkeypair -alias me -dname "cn=me" && exit 32

# Unknown options are rejected by tool
cat <<EOF > bad.conf
keytool.all=-unknown
EOF

$KEYTOOL -conf bad.conf -help -list && exit 33

# System property must be present
cat <<EOF > bad.conf
keytool.all = -keystore \${no.such.prop}
EOF

$KEYTOOL -conf bad.conf -help -list && exit 34

echo Done
exit 0
