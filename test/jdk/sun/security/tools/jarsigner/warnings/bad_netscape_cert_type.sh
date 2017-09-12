#
# Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

#!/bin/sh

# This script creates JKS keystore with a certificate
# that contains Netscape Certificate Type extension
# that does not allow code signing
# The keystore is used by BadNetscapeCertTypeTest.java test

rm -rf keystore.jks
echo "nsCertType = client" > ext.cfg

openssl req -new -out cert.req -keyout key.pem -days 3650 \
    -passin pass:password -passout pass:password -subj "/CN=Test"
openssl x509 -in cert.req -out cert.pem -req -signkey key.pem -days 3650 \
    -passin pass:password -extfile ext.cfg
openssl pkcs12 -export -in cert.pem -inkey key.pem -out keystore.p12 \
    -passin pass:password -passout pass:password -name alias

${JAVA_HOME}/bin/keytool -importkeystore \
    -srckeystore keystore.p12 -srcstoretype pkcs12 \
    -srcstorepass password -alias alias \
    -destkeystore bad_netscape_cert_type.jks -deststoretype jks \
    -deststorepass password -destalias alias \

openssl base64 < bad_netscape_cert_type.jks > bad_netscape_cert_type.jks.base64
rm -rf cert.req key.pem cert.pem keystore.p12 ext.cfg bad_netscape_cert_type.jks
