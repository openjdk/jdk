#!/bin/sh

#
# Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4984412 4985113 4908512 4990905 4998007 4998218 5002340 5023882 6370261 6363481
# @run shell ../verifyVariables.sh
# @compile PhantomUpdate.java
# @run shell discovery.sh
# @summary Test consistency of annotation discovery
# @author Joseph D. Darcy


# If the file does not exist, exit with an error
TestFile() {
	if [ ! -f ${1} ]; then
		printf "%s\n" "File ${1} not found."
		exit 1
	fi
}

OS=`uname -s`;
case "${OS}" in
        Windows* | CYGWIN* )
                SEP=";"
        ;;

	* )
	SEP=":"
	;;
esac

TOOLSJAR=${TESTJAVA}/lib/tools.jar

OLDCP=${CLASSPATH}

JARCP=tweedle.jar${SEP}touch.jar${SEP}${TOOLSJAR}
SOURCEP=${TESTSRC}
FULLCP=${JARCP}${SEP}${SOURCEP}
BADCP=tweedle.jar${SEP}badTouch.jar${SEP}${TOOLSJAR}

# Construct path to apt executable
APT="${TESTJAVA}/bin/apt ${TESTTOOLVMOPTS} "


# Construct path to apt executable, no compilation
APTNC="${APT} -nocompile "


printf "%s\n" "APT = ${APT}"

# Construct path to javac executable
JAVAC="${TESTJAVA}/bin/javac ${TESTTOOLVMOPTS} -source 1.5 -sourcepath ${TESTSRC} -classpath ${TOOLSJAR} -d . "
JAR="${TESTJAVA}/bin/jar "

$JAVAC ${TESTSRC}/Dee.java ${TESTSRC}/Dum.java ${TESTSRC}/Touch.java ${TESTSRC}/PhantomTouch.java ${TESTSRC}/Empty.java
RESULT=$?

case "${RESULT}" in
        0  )
        ;;

        * )
        echo "Compilation failed."
        exit 1
esac

mv Empty.class Empty.clazz

echo "Making services directory and copying services information."
mkdir -p META-INF/services
mkdir -p phantom

rm -f touch.jar
rm -f badTouch.jar

cp ${TESTSRC}/servicesTweedle META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory

${JAR} cf0 tweedle.jar Dee*.class Dum*.class META-INF

rm -f META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory
cp ${TESTSRC}/servicesTouch ./META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory

${JAR} cf0 touch.jar Touch*.class META-INF

rm -f META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory
cp ${TESTSRC}/servicesPhantomTouch ./META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory

${JAR} cf0 phantom/phantom.jar PhantomTouch*.class META-INF

# cleanup file to prevent accidental discovery in current directory
rm -f META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory

# Jar files created; verify right output file is touched

#---------------------------------------------------------

# Test different combinations of classpath, sourcepath, and
# destination directories


#
# Classpath on commandline; no output directories
#

rm -f touched 
rm -f HelloWorld.java
rm -f Empty.class

unset CLASSPATH
printf "%s\n" "-classpath ${JARCP}"     > options1
printf "%s\n" "-sourcepath ${SOURCEP}" >> options1
printf "%s\n" "${TESTSRC}/Touch.java"  >> options1

${APTNC} @options1

echo "Testing case 1"
TestFile "touched"
TestFile "HelloWorld.java"
TestFile "Empty.class"

#
# Class path set through environment variable
#

rm -f touched
rm -f HelloWorld.java
rm -f Empty.class

unset CLASSPATH
CLASSPATH=${JARCP}
export CLASSPATH
printf "%s\n" "-sourcepath ${SOURCEP}" > options2
printf "%s\n" "${TESTSRC}/Touch.java" >> options2

${APTNC} @options2

echo "Testing case 2"
TestFile "touched"
TestFile "HelloWorld.java"
TestFile "Empty.class"

#
# No explicit source path
#

rm -f touched
rm -f HelloWorld.java
rm -f Empty.class

unset CLASSPATH
CLASSPATH=${FULLCP}
export CLASSPATH
printf "%s\n" "${TESTSRC}/Touch.java"  > options3

${APTNC} @options3

echo "Testing case 3"
TestFile "touched"
TestFile "HelloWorld.java"
TestFile "Empty.class"


#
# Classpath on commandline; class output directory
#

rm -f touched
rm -f HelloWorld.java
rm -Rf classes/Empty.class

unset CLASSPATH
printf "%s\n" "-classpath ${JARCP}"     > options4
printf "%s\n" "-sourcepath ${SOURCEP}" >> options4
printf "%s\n" "-d classes"             >> options4
printf "%s\n" "${TESTSRC}/Touch.java"  >> options4

${APTNC} @options4

echo "Testing case 4"
TestFile "touched"
TestFile "HelloWorld.java"
TestFile "classes/Empty.class"

#
# Classpath on commandline; source output directory
#

rm -f touched
rm -Rf src
rm -f Empty.class

unset CLASSPATH
printf "%s\n" "-classpath ${JARCP}"     > options5
printf "%s\n" "-sourcepath ${SOURCEP}" >> options5
printf "%s\n" "-s src"                 >> options5
printf "%s\n" "${TESTSRC}/Touch.java"  >> options5

${APTNC} @options5

echo "Testing case 5"
TestFile "touched"
TestFile "src/HelloWorld.java"
TestFile "Empty.class"


#
# Classpath on commandline; class and source output directory
#

rm -f touched
rm -Rf src
rm -Rf classes

unset CLASSPATH
printf "%s\n" "-classpath ${JARCP}"     > options6
printf "%s\n" "-sourcepath ${SOURCEP}" >> options6
printf "%s\n" "-d classes"             >> options6
printf "%s\n" "-s src"                 >> options6
printf "%s\n" "${TESTSRC}/Touch.java"  >> options6

${APTNC} @options6

echo "Testing case 6"
TestFile "touched"
TestFile "src/HelloWorld.java"
TestFile "classes/Empty.class"

#
# Classpath appended to bootclasspath; no output directories
#

rm -f touched
rm -f HelloWorld.java
rm -f Empty.class

unset CLASSPATH
printf "%s\n" "-Xbootclasspath/a:${JARCP}" > options7
printf "%s\n" "-classpath /dev/null"      >> options7
printf "%s\n" "${TESTSRC}/Touch.java"     >> options7

${APTNC} @options7

echo "Testing case 7"
TestFile "touched"
TestFile "HelloWorld.java"
TestFile "Empty.class"

#
# Classpath in extdirs; no output directories
#

rm -f touched
rm -f HelloWorld.java
rm -f Empty.class

unset CLASSPATH
printf "%s\n" "-extdirs ."              > options8
printf "%s\n" "-classpath ${TOOLSJAR}" >> options8
printf "%s\n" "${TESTSRC}/Touch.java"  >> options8

${APTNC} @options8

echo "Testing case 8"
TestFile "touched"
TestFile "HelloWorld.java"
TestFile "Empty.class"

#
# Classpath in extdirs, take 2; no output directories
#

rm -f touched
rm -f HelloWorld.java
rm -f Empty.class

unset CLASSPATH
printf "%s\n" "-Djava.ext.dirs=."        > options9
printf "%s\n" "-classpath ${TOOLSJAR}"  >> options9
printf "%s\n" "${TESTSRC}/Touch.java"   >> options9

${APTNC} @options9

echo "Testing case 9"
TestFile "touched"
TestFile "HelloWorld.java"
TestFile "Empty.class"

#
# Classpath in -endorseddirs; no output directories
#

rm -f touched
rm -f HelloWorld.java
rm -f Empty.class

unset CLASSPATH
printf "%s\n" "-endorseddirs ."          > options10
printf "%s\n" "-classpath ${TOOLSJAR}"  >> options10
printf "%s\n" "${TESTSRC}/Touch.java"   >> options10

${APTNC} @options10

echo "Testing case 10"
TestFile "touched"
TestFile "HelloWorld.java"
TestFile "Empty.class"

#
# Testing apt invocation with no command line options
#

rm -f touched
rm -f HelloWorld.java
rm -f Empty.class

unset CLASSPATH
CLASSPATH=./phantom/phantom.jar
export CLASSPATH

${APT}

echo "Testing empty command line"
TestFile "touched"
TestFile "HelloWorld.java"


#
# Verify apt and JSR 269 annotation processors can be run from same
# invocation and both use the output directories
#

rm -f touched
rm -f src/HelloWorld.java
rm -f src/GoodbyeWorld.java
rm -f classes/HelloWorld.class
rm -f classes/GoodbyeWorld.class

unset CLASSPATH


printf "%s\n" "-classpath ./phantom/phantom.jar" > options11
printf "%s\n" "-sourcepath ${SOURCEP}"          >> options11
printf "%s\n" "-factory PhantomTouch "          >> options11
printf "%s\n" "-s src"                          >> options11
printf "%s\n" "-d classes"                      >> options11
printf "%s\n" "-A"             	                >> options11
printf "%s\n" "-Afoo"          	                >> options11
printf "%s\n" "-Abar=baz"      	                >> options11
printf "%s\n" "-processorpath $TESTCLASSES"     >> options11
printf "%s\n" "-processor PhantomUpdate"        >> options11

${APT} @options11

echo "Testing combined apt and JSR 269 processing"
TestFile touched
TestFile "src/HelloWorld.java"
TestFile "src/GoodbyeWorld.java"
TestFile "classes/HelloWorld.class"
TestFile "classes/GoodbyeWorld.class"

#
# Verify running with badTouch doesn't exit successfully
#

rm -f ./META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory
cp ${TESTSRC}/servicesBadTouch ./META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory

${JAR} cf0 badTouch.jar Touch*.class META-INF


unset CLASSPATH
printf "%s\n" "-classpath ${BADCP}"     > optionsBad
printf "%s\n" "-sourcepath ${SOURCEP}" >> optionsBad
printf "%s\n" "${TESTSRC}/Touch.java"  >> optionsBad

${APTNC} @optionsBad 2> /dev/null

RESULT=$?

case "${RESULT}" in
        0  )
	echo "Improper exit zero with bad services information."
	exit 1
        ;;
esac


exit 0;
