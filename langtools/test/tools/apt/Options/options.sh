#!/bin/sh

#
# Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4993950 4993277
# @run shell ../verifyVariables.sh
# @run shell options.sh
# @summary Test availabilty of command line options in processors
# @author Joseph D. Darcy

OS=`uname -s`;
case "${OS}" in
        Windows* | CYGWIN* )
                SEP=";"
        ;;

	* )
	SEP=":"
	;;
esac

JARCP=option.jar
SOURCEP=${TESTSRC}


# Construct path to apt executable
APT="${TESTJAVA}/bin/apt ${TESTTOOLVMOPTS} -nocompile "

printf "%s\n" "APT = ${APT}"

# Construct path to javac executable
JAVAC="${TESTJAVA}/bin/javac ${TESTTOOLVMOPTS} -source 1.5 -sourcepath ${TESTSRC} -classpath ${TESTJAVA}/lib/tools.jar -d . "
JAR="${TESTJAVA}/bin/jar "

${JAVAC} ${TESTSRC}/OptionChecker.java
RESULT=$?

case "${RESULT}" in
        0  )
        ;;

        * )
        echo "Compilation failed."
        exit 1
esac


echo "Making services directory and copying services information."
mkdir -p META-INF/services

cp ${TESTSRC}/servicesOptions META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory

${JAR} cf0 option.jar OptionChecker*.class META-INF

# Jar files created; verify options properly present on both initial
# and recursive apt runs

#---------------------------------------------------------

unset CLASSPATH

printf "%s\n" "-classpath ${JARCP}"     > options
printf "%s\n" "-sourcepath ${SOURCEP}" >> options
printf "%s\n" "${TESTSRC}/Marked.java" >> options

${APT} @options

RESULT=$?
case "${RESULT}" in
        0  )
	echo "Failed to indentify missing options"
	exit 1
	;;	
	
	* )
        ;;
esac

printf "%s\n" "-Afoo -Abar" >> options

${APT} @options

RESULT=$?
case "${RESULT}" in
        0  )
	;;	
	
	* )
	echo "Options not found properly."
	exit 1
        ;;
esac

exit 0;
