#!/bin/sh

#
# Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6415696 6931562
# @run shell KeytoolChangeAlias.sh
# @summary Test "keytool -changealias" using the Microsoft CryptoAPI provider.

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

        echo "Creating the alias '246810' in the Windows-My store..."
        ${TESTJAVA}/bin/keytool \
	    -import \
	    -storetype Windows-My \
	    -file ${TESTSRC}/246810.cer \
	    -alias 246810 \
	    -noprompt

	if [ $? -ne 0 ] ; then
            exit $?
	fi

	echo "Removing the alias '13579', if it is already present..."
        ${TESTJAVA}/bin/keytool \
	    -list \
            -storetype Windows-My \
	    -alias 13579 > /dev/null 2>&1

	if [ $? ] ; then
            ${TESTJAVA}/bin/keytool \
		-delete \
		-storetype Windows-My \
		-alias 13579 \
		-noprompt
	fi

	echo "Counting the entries in the store..."
	count=`${TESTJAVA}/bin/keytool -list -storetype Windows-My | wc -l`
	before=$count

	echo "Changing the alias name from '246810' to '13579'..."

        ${TESTJAVA}/bin/keytool \
	    -changealias \
	    -storetype Windows-My \
	    -alias 246810 \
	    -destalias 13579

	if [ $? -ne 0 ] ; then
            exit $?
	fi

	echo "Re-counting the entries in the store..."
	count=`${TESTJAVA}/bin/keytool -list -storetype Windows-My | wc -l`
	after=$count

	if [ ! $before = $after ]; then
	    echo "error: unexpected number of entries in the Windows-MY store"
	    exit 101
	fi

	echo "Confirming that the new alias is present..."
        ${TESTJAVA}/bin/keytool \
	    -list \
            -storetype Windows-My \
	    -alias 13579 > /dev/null 2>&1

	if [ $? -ne 0 ] ; then
	    echo "error: cannot find the new alias name in the Windows-MY store"
	    exit 102
	fi

	echo "Removing the new alias '13579'..."
        ${TESTJAVA}/bin/keytool \
	    -delete \
            -storetype Windows-My \
	    -alias 13579 > /dev/null 2>&1

	echo done.
        exit 0
        ;;

    * )
        echo "This test is not intended for '$OS' - passing test"
        exit 0
        ;;
esac



