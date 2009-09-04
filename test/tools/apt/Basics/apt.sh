#!/bin/sh

#
# Copyright 2004-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#

# @test
# @bug 4908512 5024825 4957203 4993280 4996963 6174696 6177059
# @run shell ../verifyVariables.sh
# @build Milk MethodAnnotations NestedClassAnnotations StaticFieldAnnotations StaticMethodAnnotations ParameterAnnotations 
# @run shell apt.sh
# @summary test consistency of annotation discovery
# @author Joseph D. Darcy

OS=`uname -s`;
case "${OS}" in
        CYGWIN* )
                DIFFOPTS="--strip-trailing-cr"
        ;;

	* )
	;;
esac

# Construct path to apt executable
APT="${TESTJAVA}/bin/apt ${TESTTOOLVMOPTS} -XDsuppress-tool-api-removal-message "

printf "%s\n" "-classpath ${TESTCLASSES}"                    > options
printf "%s\n" "-factorypath ./nullap.jar"                   >> options
printf "%s\n" "-sourcepath ${TESTSRC} "                     >> options
printf "%s\n" "-nocompile"                                  >> options
printf "%s\n" "-XListAnnotationTypes"                       >> options

printf "%s\n" "-classpath ${TESTCLASSES}"                    > options1
printf "%s\n" "-factorypath ./nullap.jar"                   >> options1
printf "%s\n" "-sourcepath ${TESTSRC} "                     >> options1
printf "%s\n" "-nocompile"                                  >> options1
printf "%s\n" "-XListAnnotationTypes"                       >> options1
printf "%s\n" "-XclassesAsDecls"                            >> options1


# Construct path to javac executable
JAVAC="${TESTJAVA}/bin/javac ${TESTTOOLVMOPTS} -source 1.5 -sourcepath ${TESTSRC} -classpath ${TESTJAVA}/lib/tools.jar -d . "
JAR="${TESTJAVA}/bin/jar "

$JAVAC ${TESTSRC}/NullAPF.java \
${TESTSRC}/FreshnessApf.java  \
${TESTSRC}/TestGetTypeDeclarationApf.java \
${TESTSRC}/TestGetPackageApf.java
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
cp ${TESTSRC}/com.sun.mirror.apt.AnnotationProcessorFactory ./META-INF/services
$JAR cvf0 nullap.jar NullAPF*.class META-INF

ANNOTATION_FILES="${TESTSRC}/ClassAnnotations.java \
${TESTSRC}/MethodAnnotations.java \
${TESTSRC}/NestedClassAnnotations.java \
${TESTSRC}/StaticFieldAnnotations.java \
${TESTSRC}/StaticMethodAnnotations.java \
${TESTSRC}/ParameterAnnotations.java"

for i in ${ANNOTATION_FILES}
do
	printf "%s\n" "Testing annotations on source file ${i}"
	${APT} @options ${i} 2> result.txt
	diff ${DIFFOPTS} ${TESTSRC}/golden.txt result.txt

	RESULT=$?
	case "$RESULT" in
	        0  )
	        ;;

	        * )
	        echo "Unexpected set of annotations on source files found."
	        exit 1
	esac

	CLASS=`basename ${i} .java`
	printf "%s\n" "Testing annotations on class file ${CLASS}"
	${APT} @options1 ${CLASS} 2> result2.txt
	diff ${DIFFOPTS} ${TESTSRC}/golden.txt result2.txt

	RESULT=$?
	case "$RESULT" in
	        0  )
	        ;;

	        * )
	        echo "Unexpected set of annotations on class files found."
	        exit 1
	esac
done

# Verify source files are favored over class files

printf "%s\n" "-factorypath ."			 > options2
printf "%s\n" "-factory FreshnessApf"		>> options2
printf "%s\n" "-sourcepath ${TESTSRC}"		>> options2
printf "%s\n" "-classpath  ${TESTCLASSES}"	>> options2
printf "%s\n" "-nocompile"			>> options2

${APT} @options2 ${TESTSRC}/Indirect.java

RESULT=$?
case "$RESULT" in
        0  )
        ;;

        * )
        exit 1
esac

# Verify new classes can be loaded by getTypeDeclaration

printf "%s\n" "-factorypath ."			 	> options3
printf "%s\n" "-factory TestGetTypeDeclarationApf"	>> options3
printf "%s\n" "-sourcepath ${TESTSRC}"			>> options3

# ${APT} @options3

RESULT=$?
case "$RESULT" in
        0  )
        ;;

        * )
        exit 1
esac

# Verify packages can be loaded by getPackage

printf "%s\n" "-factorypath ."			 	> options4
printf "%s\n" "-factory TestGetPackageApf"		>> options4
printf "%s\n" "-sourcepath ${TESTSRC}"			>> options4

${APT} @options4

RESULT=$?
case "$RESULT" in
        0  )
        ;;

        * )
        exit 1
esac
exit 0
