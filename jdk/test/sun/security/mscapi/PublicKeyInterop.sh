#!/bin/sh

#
# Copyright (c) 2011, 2015 Oracle and/or its affiliates. All rights reserved.
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
# @bug 6888925
# @requires os.family == "windows"
# @run shell PublicKeyInterop.sh
# @summary SunMSCAPI's Cipher can't use RSA public keys obtained from other
#          sources.
#

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
    Windows* | CYGWIN* )

        echo "Creating a temporary RSA keypair in the Windows-My store..."
        ${TESTJAVA}/bin/keytool ${TESTTOOLVMOPTS} \
	    -genkeypair \
	    -storetype Windows-My \
	    -keyalg RSA \
	    -alias 6888925 \
	    -dname "cn=6888925,c=US" \
	    -noprompt

        echo
	echo "Running the test..."
        ${TESTJAVA}/bin/javac ${TESTTOOLVMOPTS} ${TESTJAVACOPTS} -d . ${TESTSRC}\\PublicKeyInterop.java
        ${TESTJAVA}/bin/java ${TESTVMOPTS} PublicKeyInterop

        rc=$?

        echo
        echo "Removing the temporary RSA keypair from the Windows-My store..."
        ${TESTJAVA}/bin/keytool ${TESTTOOLVMOPTS} \
	    -delete \
	    -storetype Windows-My \
	    -alias 6888925

	echo done.
        exit $rc
        ;;

    * )
        echo "This test is not intended for '$OS' - passing test"
        exit 0
        ;;
esac
