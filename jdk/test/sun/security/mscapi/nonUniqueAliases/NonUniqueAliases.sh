#!/bin/sh

#
# Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
# @ignore Uses certutil.exe that isn't guaranteed to be installed
# @bug 6483657
# @requires os.family == "windows"
# @run shell NonUniqueAliases.sh
# @summary Test "keytool -list" displays correcly same named certificates

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

        # 'uname -m' does not give us enough information -
        #  should rely on $PROCESSOR_IDENTIFIER (as is done in Defs-windows.gmk),
        #  but JTREG does not pass this env variable when executing a shell script.
        #
        #  execute test program - rely on it to exit if platform unsupported

        echo "removing the alias NonUniqueName if it already exists"
        certutil -user -delstore MY NonUniqueName

        echo "Importing 1st certificate into MY keystore using certutil tool"
        certutil -user -addstore MY ${TESTSRC}/nonUniq1.pem

        echo "Importing 2nd certificate into MY keystore using certutil tool"
        certutil -user -addstore MY ${TESTSRC}/nonUniq2.pem

        echo "Listing certificates with keytool"
        ${TESTJAVA}/bin/keytool ${TESTTOOLVMOPTS} -list -storetype Windows-My

        echo "Counting expected entries"
        count0=`${TESTJAVA}/bin/keytool ${TESTTOOLVMOPTS} -list -storetype Windows-My | grep 'NonUniqueName,' | wc -l`

        if [ ! $count0 = 1 ]; then
            echo "error: unexpected number of entries ($count0) in the Windows-MY store"
            certutil -user -delstore MY NonUniqueName
            exit 115
        fi

        echo "Counting expected entries"
        count1=`${TESTJAVA}/bin/keytool ${TESTTOOLVMOPTS} -list -storetype Windows-My | grep 'NonUniqueName (1),' | wc -l`

        if [ ! $count1 = 1 ]; then
            echo "error: unexpected number of entries ($count1) in the Windows-MY store"
            certutil -user -delstore MY NonUniqueName
            exit 116
        fi

        echo "Cleaning up"
        certutil -user -delstore MY NonUniqueName

        exit 0
        ;;

    * )
        echo "This test is not intended for '$OS' - passing test"
        exit 0
        ;;
esac
