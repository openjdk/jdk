#
# Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4075221
# @run shell/timeout=300 run.sh
# @summary Enable serialize of nonSerializable Class descriptor.

set -ex	

if [ "${TESTJAVA}" = "" ]
then
  echo "TESTJAVA not set.  Test cannot execute.  Failed."
  exit 1
fi

if [ "${COMPILEJAVA}" = "" ] ; then
  COMPILEJAVA="${TESTJAVA}"
fi

if [ "${TESTSRC}" = "" ]
then
TESTSRC="."
fi

${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d . ${TESTSRC}/Test.java

echo Write NonSerial1, Read NonSerial1
rm -f A.java     
cp ${TESTSRC}/NonSerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -s A
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -d
echo

echo Write NonSerial1, Read NonSerial2
rm -f A.java     
cp ${TESTSRC}/NonSerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -s A
rm -f A.java     
cp ${TESTSRC}/NonSerialA_2.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -d
echo

echo Write NonSerial1, Read Serial1
rm -f A.java     
cp ${TESTSRC}/NonSerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java 
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -s A
rm -f A.java     
cp ${TESTSRC}/SerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -d
echo

echo Write Serial1, Read NonSerial1
rm -f A.java     
cp ${TESTSRC}/SerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java 
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -s A
rm -f A.java     
cp ${TESTSRC}/NonSerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -doe
echo

echo Write Serial1, Read Serial2
rm -f A.java     
cp ${TESTSRC}/SerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java 
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -s A
rm -f A.java     
cp ${TESTSRC}/SerialA_2.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -d
echo

echo Write Serial2, Read Serial1
rm -f A.java     
cp ${TESTSRC}/SerialA_2.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java 
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -s A
rm -f A.java     
cp ${TESTSRC}/SerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -d
echo

echo Write Serial1, Read Serial3
rm -f A.java     
cp ${TESTSRC}/SerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java 
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -s A
rm -f A.java     
cp ${TESTSRC}/SerialA_3.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -de
echo

echo Write Serial3, Read Serial1
rm -f A.java     
cp ${TESTSRC}/SerialA_3.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java 
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -s A
rm -f A.java     
cp ${TESTSRC}/SerialA_1.java A.java
${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} A.java
${TESTJAVA}/bin/java ${TESTVMOPTS} Test -de
echo

echo Passed
