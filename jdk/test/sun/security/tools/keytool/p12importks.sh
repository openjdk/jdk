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
# @bug 8010125
# @summary keytool -importkeystore could create a pkcs12 keystore with
#  different storepass and keypass
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

LANG=C
KT="$TESTJAVA${FS}bin${FS}keytool ${TESTTOOLVMOPTS}"

# Part 1: JKS keystore with same storepass and keypass

rm jks 2> /dev/null
$KT -genkeypair -keystore jks -storetype jks -alias me -dname CN=Me \
	-keyalg rsa -storepass pass1111 -keypass pass1111 || exit 11

# Cannot only change storepass
rm p12 2> /dev/null
$KT -importkeystore -noprompt \
    -srcstoretype jks -srckeystore jks -destkeystore p12 -deststoretype pkcs12 \
    -srcstorepass pass1111 \
    -deststorepass pass2222 \
        && exit 12

# You can keep storepass unchanged
rm p12 2> /dev/null
$KT -importkeystore -noprompt \
    -srcstoretype jks -srckeystore jks -destkeystore p12 -deststoretype pkcs12 \
    -srcstorepass pass1111 \
    -deststorepass pass1111 \
        || exit 13
$KT -certreq -storetype pkcs12 -keystore p12 -alias me \
	-storepass pass1111 -keypass pass1111 || exit 14

# Or change storepass and keypass both
rm p12 2> /dev/null
$KT -importkeystore -noprompt \
    -srcstoretype jks -srckeystore jks -destkeystore p12 -deststoretype pkcs12 \
    -srcstorepass pass1111 \
    -deststorepass pass2222 -destkeypass pass2222 \
        || exit 15
$KT -certreq -storetype pkcs12 -keystore p12 -alias me \
	-storepass pass2222 -keypass pass2222 || exit 16

# Part 2: JKS keystore with different storepass and keypass
# Must import by alias (-srckeypass is not available when importing all)

rm jks 2> /dev/null
$KT -genkeypair -keystore jks -storetype jks -alias me -dname CN=Me \
	-keyalg rsa -storepass pass1111 -keypass pass2222 || exit 21

# Can use old keypass as new storepass so new storepass and keypass are same
rm p12 2> /dev/null
$KT -importkeystore -noprompt -srcalias me \
    -srcstoretype jks -srckeystore jks -destkeystore p12 -deststoretype pkcs12 \
    -srcstorepass pass1111 -srckeypass pass2222  \
	-deststorepass pass2222 \
	    || exit 22
$KT -certreq -storetype pkcs12 -keystore p12 -alias me \
	-storepass pass2222 -keypass pass2222 || exit 23

# Or specify both storepass and keypass to brand new ones
rm p12 2> /dev/null
$KT -importkeystore -noprompt -srcalias me \
    -srcstoretype jks -srckeystore jks -destkeystore p12 -deststoretype pkcs12 \
    -srcstorepass pass1111 -srckeypass pass2222  \
	-deststorepass pass3333 -destkeypass pass3333 \
	    || exit 24
$KT -certreq -storetype pkcs12 -keystore p12 -alias me \
	-storepass pass3333 -keypass pass3333 || exit 25

# Anyway you cannot make new storepass and keypass different
rm p12 2> /dev/null
$KT -importkeystore -noprompt -srcalias me \
    -srcstoretype jks -srckeystore jks -destkeystore p12 -deststoretype pkcs12 \
    -srcstorepass pass1111 -srckeypass pass2222  \
	-deststorepass pass1111 \
	    && exit 26

exit 0
