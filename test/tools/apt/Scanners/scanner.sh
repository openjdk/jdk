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
# @bug 4989093 5009164 5023880 5029482 6206786
# @run shell ../verifyVariables.sh
# @run shell scanner.sh
# @summary Test source order scanner
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

JARCP=scanner.jar

# Construct path to apt executable
APT="${TESTJAVA}/bin/apt ${TESTTOOLVMOPTS} -nocompile "

printf "%s\n" "APT = ${APT}"

# Construct path to javac executable
JAVAC="${TESTJAVA}/bin/javac ${TESTTOOLVMOPTS} -source 1.5 -sourcepath ${TESTSRC} -classpath ${TESTJAVA}/lib/tools.jar -d . "
JAR="${TESTJAVA}/bin/jar "

${JAVAC} ${TESTSRC}/Scanner.java ${TESTSRC}/VisitOrder.java ${TESTSRC}/Counter.java ${TESTSRC}/MemberOrderApf.java
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

cp ${TESTSRC}/servicesScanner META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory

${JAR} cf0 scanner.jar Scanner*.class META-INF

# Jar files created; verify options properly present on both initial
# and recursive apt runs

#---------------------------------------------------------

unset CLASSPATH

printf "%s\n" "-classpath ${JARCP}"     > options
printf "%s\n" "-sourcepath ${TESTSRC}" >> options
printf "%s\n" "${TESTSRC}/Order.java"  >> options

${APT} @options

RESULT=$?
case "${RESULT}" in
        0  )
	;;	
	
	* )
	echo "Program elements visited in wrong order"
	exit 1
        ;;
esac

#---------------------------------------------------------

# Verify that plain decl' scanner and source order decl' scanner
# record the same number of elements for an enum

printf "%s\n" "-factorypath ."            > options2
printf "%s\n" "-factory Counter"         >> options2
printf "%s\n" "-sourcepath ${TESTSRC}"   >> options2
printf "%s\n" "${TESTSRC}/TestEnum.java" >> options2


$APT @options2

RESULT=$?
case "${RESULT}" in
        0  )
	;;	
	
	* )
	echo "Improper counts"
	exit 1
        ;;
esac

#---------------------------------------------------------

# Verify that members happen to be returned in source order

printf "%s\n" "-factorypath ."            > options3
printf "%s\n" "-factory MemberOrderApf"  >> options3
printf "%s\n" "-sourcepath ${TESTSRC}"   >> options3
printf "%s\n" "${TESTSRC}/Order.java"    >> options3

$APT @options3

RESULT=$?
case "${RESULT}" in
        0  )
	;;	
	
	* )
	echo "Improper counts"
	exit 1
        ;;
esac


exit 0;
