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
# @bug 8049834
# @summary Two security tools tests do not run with only JRE
#

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

KS=ks
KEYTOOL="$TESTJAVA/bin/keytool ${TESTTOOLVMOPTS}"
JAR="$TESTJAVA/bin/jar ${TESTTOOLVMOPTS}"
JARSIGNER="$TESTJAVA/bin/jarsigner ${TESTTOOLVMOPTS}"

rm $KS 2> /dev/null

PASS=changeit
export PASS

$KEYTOOL -genkeypair -dname CN=A -alias a \
         -storepass:env PASS -keypass:env PASS -keystore $KS \
         -keyalg rsa || exit 1

cat <<EOF > js.conf
jarsigner.all = -keystore \${user.dir}/$KS -storepass:env PASS -debug -strict
jarsigner.sign = -digestalg SHA1
jarsigner.verify = -verbose:summary

EOF

$JAR cvf a.jar ks js.conf

$JARSIGNER -conf js.conf a.jar a || exit 21
$JARSIGNER -conf js.conf -verify a.jar > jarsigner.out || exit 22
grep "and 1 more" jarsigner.out || exit 23
$JAR xvf a.jar META-INF/MANIFEST.MF
grep "SHA1-Digest" META-INF/MANIFEST.MF || exit 24

echo Done
exit 0
