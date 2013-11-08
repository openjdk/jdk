#
# Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6561126
# @summary keytool should use larger default keysize for keypairs
#
# @run shell newsize7.sh

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory
if [ "${TESTSRC}" = "" ] ; then
   TESTSRC="."
fi

if [ "${TESTJAVA}" = "" ] ; then
  JAVA_CMD=`which java`
  TESTJAVA=`dirname $JAVA_CMD`/..
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  Windows_* )
    FS="\\"
    ;;
  * )
    FS="/"
    ;;
esac

KSFILE=ns7.jks

KT="${TESTJAVA}${FS}bin${FS}keytool -keystore ns7.jks -storepass changeit -keypass changeit -keyalg rsa"
JAR="${TESTJAVA}${FS}bin${FS}jar"
JS="${TESTJAVA}${FS}bin${FS}jarsigner -keystore ns7.jks -storepass changeit"

rm ns7.*

$KT -genkeypair -alias me -dname CN=Me

touch ns7.txt
$JAR cvf ns7.jar ns7.txt

$JS ns7.jar me
$JAR xvf ns7.jar

grep SHA-256 META-INF/MANIFEST.MF || exit 1
grep SHA-256 META-INF/ME.SF || exit 2

#rm -rf META-INF

exit 0
