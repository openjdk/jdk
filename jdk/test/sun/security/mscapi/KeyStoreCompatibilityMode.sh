#!/bin/sh

#
# Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6324294
# @run shell KeyStoreCompatibilityMode.sh
# @summary Confirm that a null stream or password is not permitted when 
#          compatibility mode is enabled (and vice versa).
#
# Run only on non-64-bit Windows platform.

OS=`uname -s`
case "$OS" in
    Windows* | CYGWIN* )

    # 'uname -m' does not give us enough information -
    #  should rely on $PROCESSOR_IDENTIFIER (as is done in Defs-windows.gmk),
    #  but JTREG does not pass this env variable when executing a shell script.
    #
    #  execute test program - rely on it to exit if platform unsupported

	${TESTJAVA}/bin/javac -d . ${TESTSRC}\\KeyStoreCompatibilityMode.java

    # mode implicitly enabled
	${TESTJAVA}/bin/java KeyStoreCompatibilityMode

    # mode explicitly enabled
	${TESTJAVA}/bin/java \
	    -Dsun.security.mscapi.keyStoreCompatibilityMode="true" \
	    KeyStoreCompatibilityMode

    # mode explicitly disabled
	${TESTJAVA}/bin/java \
	    -Dsun.security.mscapi.keyStoreCompatibilityMode="false" \
	    KeyStoreCompatibilityMode -disable

	exit
	;;

    * )
        echo "This test is not intended for '$OS' - passing test"
        exit 0
        ;;
esac

