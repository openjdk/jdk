#!/bin/sh

#
# Copyright 2004-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 5033855 4990902 5023880 5043516 5048534 5048535 5041279 5048539 5067261 5068145 5023881 4996963 5095716 6191667 6433634
# @run shell ../verifyVariables.sh
# @build ErrorAPF
# @build WarnAPF
# @build StaticApf
# @build ClassDeclApf
# @build ClassDeclApf2
# @build Rounds
# @build Round1Apf Round2Apf Round3Apf Round4Apf
# @build WrappedStaticApf
# @run shell compile.sh
# @summary Test simple usages of apt, including delegating to javac
# @author Joseph D. Darcy

# If the file *does* exist, exit with an error
TestNoFile() {
        if [ -f ${1} ]; then
                printf "%s\n" "File ${1} found."
                exit 1
        fi
}

# If the file does not exist, exit with an error
TestFile() {
        if [ ! -f ${1} ]; then
                printf "%s\n" "File ${1} not found."
                exit 1
        fi
}


OS=`uname -s`;
case "${OS}" in
        Windows* )
                SEP=";"
        ;;

        CYGWIN* )
		DIFFOPTS="--strip-trailing-cr"
                SEP=";"
        ;;

        * )
        SEP=":"
        ;;
esac


APT="${TESTJAVA}/bin/apt ${TESTTOOLVMOPTS} -XDsuppress-tool-api-removal-message "
JAVA="${TESTJAVA}/bin/java ${TESTVMOPTS} "
JAVAC="${TESTJAVA}/bin/javac ${TESTTOOLVMOPTS} "

unset CLASSPATH


# ---------------------------------------------------------------
echo "Verify that source 1.6 is not supported
rm -f HelloWorld.class

printf "%s\n" "-source 1.6"     > options0
printf "%s\n" "${TESTSRC}/HelloWorld.java"  >> options0
${APT} @options0

RESULT=$?
case "$RESULT" in
	0  )
        echo "FAILED: accepted source 1.6"
	exit 1
        ;;
esac

TestNoFile "HelloWorld.class"

# ---------------------------------------------------------------

echo "Verify that target 1.6 is not supported
rm -f HelloWorld.class

printf "%s\n" "-target 1.6"     > options00
printf "%s\n" "${TESTSRC}/HelloWorld.java"  >> options00
${APT} @options00

RESULT=$?
case "$RESULT" in
	0  )
        echo "FAILED: accepted target 1.6"
	exit 1
        ;;
esac

TestNoFile "HelloWorld.class"

# ---------------------------------------------------------------

echo "Testing javac pass-through with -A in options file"
rm -f HelloWorld.class

printf "%s\n" "-A"     > options1
printf "%s\n" "-d ."     >> options1
printf "%s\n" "${TESTSRC}/HelloWorld.java"  >> options1
${APT} @options1

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: javac with -A in options file did not compile"
        exit 1
esac
TestFile "HelloWorld.class"


# ---------------------------------------------------------------

echo "Verifying reporting an error will prevent compilation"
rm -f HelloWorld.class
if [ ! -f HelloWorld.java ]; then
	cp ${TESTSRC}/HelloWorld.java .
fi


printf "%s\n" "-factory ErrorAPF"            > options2
printf "%s\n" "-d ."                        >> options2
printf "%s\n" "-cp ${TESTCLASSES}"          >> options2
printf "%s\n" "HelloWorld.java"             >> options2
${APT} @options2 2> output

TestNoFile "HelloWorld.class"

diff ${DIFFOPTS} output ${TESTSRC}/golden.txt

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: did not record expected error messages"
        exit 1
esac



# ---------------------------------------------------------------

echo "Verifying reporting a warning *won't* prevent compilation"

rm -f HelloAnnotation.class
if [ ! -f HelloAnnotation.java ]; then
	cp ${TESTSRC}/HelloAnnotation.java .
fi


printf "%s\n" "-factory WarnAPF"             > options3
printf "%s\n" "-d ."                        >> options3
printf "%s\n" "-cp ${TESTCLASSES}"          >> options3
printf "%s\n" "HelloAnnotation.java"        >> options3
${APT} @options3 2> output

diff ${DIFFOPTS} output ${TESTSRC}/goldenWarn.txt

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: did not record expected warning messages"
        exit 1
esac

TestFile "HelloAnnotation.class"

# ---------------------------------------------------------------

echo "Verifying static state is available across apt rounds; -factory, -cp"

mkdir -p ./src
mkdir -p ./class

rm -Rf ./src/*
rm -Rf ./class/*

printf "%s\n" "-factory StaticApf"           > options4
printf "%s\n" "-s ./src"                    >> options4
printf "%s\n" "-d ./class"                  >> options4
printf "%s\n" "-cp ${TESTCLASSES}"          >> options4
# printf "%s\n" "-XPrintAptRounds"            >> options4
${APT} @options4

TestFile "./class/AndAhTwo.class"

# ---------------------------------------------------------------

echo "Verifying static state is available across apt rounds; -factory, -factorypath"

rm -Rf ./src/*
rm -Rf ./class/*

printf "%s\n" "-factory StaticApf"           > options5
printf "%s\n" "-s ./src"                    >> options5
printf "%s\n" "-d ./class"                  >> options5
printf "%s\n" "-factorypath ${TESTCLASSES}" >> options5
# printf "%s\n" "-XPrintAptRounds"          >> options5
${APT} @options5

TestFile "./class/AndAhTwo.class"

# -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

# Create jar file for StaticApf
JAR="${TESTJAVA}/bin/jar "
mkdir -p META-INF/services
cp ${TESTSRC}/servicesStaticApf META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory
cp ${TESTCLASSES}/StaticApf*.class .
${JAR} cf0 staticApf.jar StaticApf*.class META-INF

# ---------------------------------------------------------------

echo "Verifying static state is available across apt rounds; -cp"

rm -Rf ./src/*
rm -Rf ./class/*

printf "%s\n" "-cp staticApf.jar"           > options6
printf "%s\n" "-s ./src"                    >> options6
printf "%s\n" "-d ./class"                  >> options6
printf "%s\n" "-XPrintAptRounds"          >> options6
${APT} @options6

TestFile "./class/AndAhTwo.class"

# ---------------------------------------------------------------

echo "Verifying static state is available across apt rounds; -factorypath"

rm -Rf ./src/*
rm -Rf ./class/*

printf "%s\n" "-factorypath staticApf.jar"   > options7
printf "%s\n" "-s ./src"                    >> options7
printf "%s\n" "-d ./class"                  >> options7
printf "%s\n" "-XPrintAptRounds"            >> options7
${APT} @options7

TestFile "./class/AndAhTwo.class"

# ---------------------------------------------------------------

echo "Verifying -XclassesAsDecls handles class files properly"

rm -Rf ./src/*
rm -Rf ./class/*

mkdir -p ./tmp/classes

${JAVAC} -d ./tmp/classes ${TESTSRC}/src/Round1Class.java ${TESTSRC}/src/AhOneClass.java ${TESTSRC}/src/AndAhTwoClass.java

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: javac failed to succesfully compile."
        exit 1
esac

printf "%s\n" "-factorypath ${TESTCLASSES}"  > options7a
printf "%s\n" "-factory ClassDeclApf"       >> options7a
printf "%s\n" "-s ./src"                    >> options7a
printf "%s\n" "-d ./class"                  >> options7a
printf "%s\n" "-XPrintAptRounds"            >> options7a
printf "%s\n" "-XclassesAsDecls"            >> options7a
${APT} @options7a

TestFile "./class/AndAhTwoClass.class"

# ---------------------------------------------------------------

echo "Verifying -XclassesAsDecls works with command-line arguments"

rm -Rf ./src/*
rm -Rf ./class/*
rm -Rf ./tmp/classes

mkdir -p ./tmp/classes

${JAVAC} -d ./tmp/classes ${TESTSRC}/src/Round1Class.java ${TESTSRC}/src/AndAhTwoClass.java

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: javac failed to succesfully compile."
        exit 1
esac

printf "%s\n" "-factorypath ${TESTCLASSES}"  > options7b
printf "%s\n" "-factory ClassDeclApf2"       >> options7b
printf "%s\n" "-XPrintAptRounds"            >> options7b
printf "%s\n" "-XclassesAsDecls"            >> options7b
printf "%s\n" "-cp ${TESTCLASSES}"          >> options7b
printf "%s\n" "ErrorAPF"                    >> options7b
printf "%s\n" "WarnAPF"                     >> options7b
printf "%s\n" "-s ./src"                    >> options7b
printf "%s\n" "-d ./class"                  >> options7b
printf "%s\n" "ClassDeclApf"                >> options7b
${APT} @options7b

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: apt exited with an error code."
        exit 1
esac

TestFile "./class/AndAhTwoClass.class"
TestFile "./class/AhOne.class"

# ---------------------------------------------------------------

echo "Verifying -XclassesAsDecls works with all source files"

rm -Rf ./src/*
rm -Rf ./class/*
rm -Rf ./tmp/classes

mkdir -p ./tmp/classes

${JAVAC} -d ./tmp/classes ${TESTSRC}/src/Round1Class.java ${TESTSRC}/src/AndAhTwoClass.java

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: javac failed to succesfully compile."
        exit 1
esac

printf "%s\n" "-factorypath ${TESTCLASSES}"      > options7c
printf "%s\n" "-factory ClassDeclApf2"          >> options7c
printf "%s\n" "-s ./src"                        >> options7c
printf "%s\n" "-d ./class"                      >> options7c
printf "%s\n" "-sourcepath ${TESTSRC}"          >> options7c
printf "%s\n" "${TESTSRC}/HelloAnnotation.java" >> options7c
printf "%s\n" "${TESTSRC}/HelloWorld.java"      >> options7c
printf "%s\n" "${TESTSRC}/Dummy1.java"          >> options7c
printf "%s\n" "-XPrintAptRounds"                >> options7c
printf "%s\n" "-XclassesAsDecls"                >> options7c
printf "%s\n" "-cp ${TESTCLASSES}"              >> options7c
${APT} @options7c

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: apt exited with an error code."
        exit 1
esac

TestFile "./class/AndAhTwoClass.class"
TestFile "./class/AhOne.class"
TestFile "./class/HelloWorld.class"

# ---------------------------------------------------------------

echo "Verifying -XclassesAsDecls works with mixed class and source files"

rm -Rf ./src/*
rm -Rf ./class/*
rm -Rf ./tmp/classes

mkdir -p ./tmp/classes

${JAVAC} -d ./tmp/classes ${TESTSRC}/src/Round1Class.java ${TESTSRC}/src/AndAhTwoClass.java

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: javac failed to succesfully compile."
        exit 1
esac

printf "%s\n" "-factorypath ${TESTCLASSES}"  > options7d
printf "%s\n" "-factory ClassDeclApf2"      >> options7d
printf "%s\n" "-s ./src"                    >> options7d
printf "%s\n" "-XclassesAsDecls"            >> options7d
printf "%s\n" "ClassDeclApf"                >> options7d
printf "%s\n" "-d ./class"                  >> options7d
printf "%s\n" "ErrorAPF"                    >> options7d
printf "%s\n" "-XPrintAptRounds"            >> options7d
printf "%s\n" "${TESTSRC}/HelloWorld.java"  >> options7d
printf "%s\n" "-cp ${TESTCLASSES}"          >> options7d
${APT} @options7d

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: apt exited with an error code."
        exit 1
esac

TestFile "./class/AndAhTwoClass.class"
TestFile "./class/AhOne.class"
TestFile "./class/HelloWorld.class"

# ---------------------------------------------------------------

echo "Testing productive factories are called on subsequent rounds"

rm -Rf ./src/*
rm -Rf ./class/*

rm -Rf META-INF/services/*
cp ${TESTSRC}/servicesRound1 META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory
cp ${TESTCLASSES}/Round1Apf*.class .
${JAR} cf0 round1Apf.jar Round1Apf*.class META-INF

rm -Rf META-INF/services/*
cp ${TESTSRC}/servicesRound2 META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory
cp ${TESTCLASSES}/Round2Apf*.class .
${JAR} cf0 round2Apf.jar Round2Apf*.class META-INF

rm -Rf META-INF/services/*
cp ${TESTSRC}/servicesRound3 META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory
cp ${TESTCLASSES}/Round3Apf*.class .
${JAR} cf0 round3Apf.jar Round3Apf*.class META-INF

rm -Rf META-INF/services/*
cp ${TESTSRC}/servicesRound4 META-INF/services/com.sun.mirror.apt.AnnotationProcessorFactory
cp ${TESTCLASSES}/Round4Apf*.class .
${JAR} cf0 round4Apf.jar Round4Apf*.class META-INF

cp ${TESTCLASSES}/Round?.class .
${JAR} cf0 rounds.jar Round?.class

# cleanup file to prevent accidental discovery in current directory
rm -Rf META-INF/services/*

printf "%s\n" "-factorypath round1Apf.jar${SEP}round2Apf.jar${SEP}round3Apf.jar${SEP}round4Apf.jar"   > options8
printf "%s\n" "-classpath rounds.jar"  >> options8
printf "%s\n" "-s ./src"               >> options8
printf "%s\n" "-d ./class"             >> options8
#printf "%s\n" "-XPrintFactoryInfo"     >> options8
#printf "%s\n" "-XPrintAptRounds"       >> options8
printf "%s\n" "${TESTSRC}/Dummy1.java" >> options8
${APT} @options8 > multiRoundOutput 2> multiRoundError

diff ${DIFFOPTS} multiRoundOutput  ${TESTSRC}/goldenFactory.txt

RESULT=$?
case "$RESULT" in
	0  )
        ;;

        * )
        echo "FAILED: unexpected factory state"
        exit 1
esac

TestFile "./class/Dummy5.class"

# ---------------------------------------------------------------

echo "Verifying static state with programmatic apt entry; no factory options"
rm -Rf ./src/*
rm -Rf ./class/*
${JAVA} -cp ${TESTJAVA}/lib/tools.jar${SEP}${TESTCLASSES} WrappedStaticApf -s ./src -d ./class -XPrintAptRounds
TestFile "./class/AndAhTwo.class"

echo "Verifying static state with programmatic apt entry; -factory"
rm -Rf ./src/*
rm -Rf ./class/*
${JAVA} -cp ${TESTJAVA}/lib/tools.jar${SEP}${TESTCLASSES} WrappedStaticApf -factory ErrorAPF -s ./src -d ./class -XPrintAptRounds
TestFile "./class/AndAhTwo.class"

echo "Verifying static state with programmatic apt entry; -factorypath"
rm -Rf ./src/*
rm -Rf ./class/*
${JAVA} -cp ${TESTJAVA}/lib/tools.jar${SEP}${TESTCLASSES} WrappedStaticApf -factorypath round1Apf.jar -s ./src -d ./class -XPrintAptRounds
TestFile "./class/AndAhTwo.class"

echo "Verifying static state with programmatic apt entry; -factory and -factorypath"
rm -Rf ./src/*
rm -Rf ./class/*
${JAVA} -cp ${TESTJAVA}/lib/tools.jar${SEP}${TESTCLASSES} WrappedStaticApf -factorypath round1Apf.jar -factory Round1Apf -s ./src -d ./class -XPrintAptRounds
TestFile "./class/AndAhTwo.class"

exit 0
