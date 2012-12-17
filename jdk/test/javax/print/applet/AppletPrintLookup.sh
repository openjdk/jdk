#!/bin/sh

#
# Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

#
# @test
# @bug 4457046 6592906
#     @summary checks that applets can lookup print services and will not
#     see services registered by other applets from difference code bases.
# @run shell/manual AppletPrintLookup.sh

echo "TESTJAVA=${TESTJAVA}"
echo "TESTSRC=${TESTSRC}"
echo "TESTCLASSES=${TESTCLASSES}"

echo "Wait until 5 applets have initialised and started and display string"
echo "messages. Applet 0 and Applet 2 should find one less print service"
echo "than the rest."
echo "Specifically all except Applets 0 and 2 should find a service called"
echo "Applet N printer where N is the number of the applet. They should NOT"
echo "find Applet M printer (where M != N)."

OS=`uname -s`

SEP="/"
OS=`uname -s`
case "$OS" in
 Win* )
 echo "WINDOWS"
 SEP="\\" 
 ;;
 * )
 ;;
esac

JAVAC_CMD=${TESTJAVA}${SEP}bin${SEP}javac

(cd ${TESTSRC} ; ${JAVAC_CMD} -d ${TESTCLASSES} YesNo.java)

mkdir -p ${TESTCLASSES}${SEP}applet0
(cd ${TESTSRC}${SEP}applet0 ; ${JAVAC_CMD} -d ${TESTCLASSES}${SEP}applet0 Applet0.java)

mkdir -p ${TESTCLASSES}${SEP}applet1
(cd ${TESTSRC}${SEP}applet1 ; ${JAVAC_CMD} -d ${TESTCLASSES}${SEP}applet1 Applet1.java Applet1PrintService.java Applet1PrintServiceLookup.java)
rm -rf ${TESTCLASSES}${SEP}applet1/META-INF/services
mkdir -p ${TESTCLASSES}${SEP}applet1/META-INF/services
cp -p ${TESTSRC}${SEP}applet1/META-INF/services/javax.print.PrintServiceLookup  ${TESTCLASSES}${SEP}applet1/META-INF/services
(cd ${TESTCLASSES}${SEP}applet1 ; ${TESTJAVA}${SEP}bin${SEP}jar -cf applet1.jar *.class META-INF)

mkdir -p ${TESTCLASSES}${SEP}applet2
(cd ${TESTSRC}${SEP}applet2 ; ${JAVAC_CMD} -d ${TESTCLASSES}${SEP}applet2 Applet2.java Applet2PrintService.java Applet2PrintServiceLookup.java)

mkdir -p ${TESTCLASSES}${SEP}applet3
(cd ${TESTSRC}${SEP}applet3 ; ${JAVAC_CMD} -d ${TESTCLASSES}${SEP}applet3 Applet3.java Applet3PrintService.java)

mkdir -p ${TESTCLASSES}${SEP}applet4
(cd ${TESTSRC}${SEP}applet4 ; ${JAVAC_CMD} -d ${TESTCLASSES}${SEP}applet4 Applet4.java Applet4PrintService.java Applet4PrintServiceLookup.java)

cp ${TESTSRC}${SEP}AppletPrintLookup.html  ${TESTCLASSES}

${TESTJAVA}${SEP}bin${SEP}appletviewer ${TESTCLASSES}${SEP}AppletPrintLookup.html &

cd  ${TESTCLASSES} 
${TESTJAVA}${SEP}bin${SEP}java ${TESTVMOPTS} YesNo
 if [ $? -ne 0 ]
    then
      echo "Test fails!"
      exit 1
    fi

echo "Test passes."
exit 0
