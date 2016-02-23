#!/bin/sh

#
# Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
# @bug 7106773
# @summary 512 bits RSA key cannot work with SHA384 and SHA512
# @requires os.family == "windows"
# @run shell ShortRSAKey1024.sh 1024
# @run shell ShortRSAKey1024.sh 768
# @run shell ShortRSAKey1024.sh 512

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory
if [ "${TESTSRC}" = "" ] ; then
   TESTSRC="."
fi

if [ "${TESTCLASSES}" = "" ] ; then
   TESTCLASSES="."
fi

if [ "${TESTJAVA}" = "" ] ; then
   echo "TESTJAVA not set.  Test cannot execute."
   echo "FAILED!!!"
   exit 1
fi

OS=`uname -s`
case "$OS" in
  SunOS | Linux | Darwin | CYGWIN* )
    FS="/"
    ;;
  Windows_* )
    FS="\\"
    ;;
esac

BITS=$1

case "$OS" in
    Windows* | CYGWIN* )

        echo "Removing the keypair if it already exists (for unknown reason)..."
        ${TESTJAVA}${FS}bin${FS}keytool ${TESTTOOLVMOPTS} \
            -delete \
            -storetype Windows-My \
            -debug \
            -alias 7106773.$BITS

        echo "Creating a temporary RSA keypair in the Windows-My store..."
        ${TESTJAVA}${FS}bin${FS}keytool ${TESTTOOLVMOPTS} \
            -genkeypair \
            -storetype Windows-My \
            -keyalg RSA \
            -alias 7106773.$BITS \
            -keysize $BITS \
            -dname "cn=localhost,c=US" \
            -debug \
            -noprompt

        if [ "$?" -ne "0" ]; then
            echo "Unable to generate key pair in Windows-My keystore"
            exit 1
        fi

        echo
        echo "Running the test..."
        ${TESTJAVA}${FS}bin${FS}javac ${TESTTOOLVMOPTS} ${TESTJAVACOPTS} -d . \
            ${TESTSRC}${FS}ShortRSAKeyWithinTLS.java
        ${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} ShortRSAKeyWithinTLS 7106773.$BITS $BITS \
            TLSv1.2 TLS_DHE_RSA_WITH_AES_128_CBC_SHA

        rc=$?

        echo
        echo "Removing the temporary RSA keypair from the Windows-My store..."
        ${TESTJAVA}${FS}bin${FS}keytool ${TESTTOOLVMOPTS} \
            -delete \
            -storetype Windows-My \
            -debug \
            -alias 7106773.$BITS

        echo "Done".
        exit $rc
        ;;

    * )
        echo "This test is not intended for '$OS' - passing test"
        exit 0
        ;;
esac
