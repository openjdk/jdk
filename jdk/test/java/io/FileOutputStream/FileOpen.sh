#!/bin/sh

#
# Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6364894
# @run shell FileOpen.sh
# @summary Test to ensure that opening of hidden Vs non-hidden,
# read/write Vs read-only files for writing works as expected.


# We use a TMP directory on a local disk because this test
# requires that the file to be tested be present on the local disk,
# not on a samba mounted drive or on a drive that is mapped.
# The cmd 'attrib' works only on the local files.
TMP="C:\TEMP"
hfile=${TMP}"\random_file1.txt"
ATTRIB=${SystemRoot}"\system32\attrib.exe"

OS=`uname -s`
case "$OS" in
    Windows_* )
	if [ ! -d ${TMP} ] ; then 
           echo "Could not find the directory-" ${TMP} "- passing test"
	   exit 0;
	fi
	${TESTJAVA}/bin/javac -d . ${TESTSRC}\\FileOpenPos.java
	${TESTJAVA}/bin/javac -d . ${TESTSRC}\\FileOpenNeg.java

	echo "Opening Writable Normal File.."
	${TESTJAVA}/bin/java FileOpenPos ${hfile}

	echo "Opening Writable Hidden File.."
	${ATTRIB} +h ${hfile}
	${TESTJAVA}/bin/java FileOpenNeg ${hfile}

	echo "Opening Read-Only Normal File.."
	${ATTRIB} -h ${hfile}
	${ATTRIB} +r ${hfile}
	${TESTJAVA}/bin/java FileOpenNeg ${hfile}

	echo "Opening Read-Only Hidden File.." 
	${ATTRIB} +h ${hfile}
	${TESTJAVA}/bin/java FileOpenNeg ${hfile}

        rm -f ${hfile}
	exit
        ;;

    * )
        echo "This test is not intended for this OS - passing test"
	exit 0
        ;;
esac
