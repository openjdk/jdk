#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 6825352
# @summary support self-issued certificate in keytool
#
# @run shell selfissued.sh
#

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
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

KS=selfsigned.jks
KT="$TESTJAVA${FS}bin${FS}keytool -storepass changeit -keypass changeit -keystore $KS"

rm $KS

$KT -alias ca -dname CN=CA -genkeypair
$KT -alias me -dname CN=CA -genkeypair
$KT -alias e1 -dname CN=E1 -genkeypair
$KT -alias e2 -dname CN=E2 -genkeypair

# me signed by ca, self-issued
$KT -alias me -certreq | $KT -alias ca -gencert | $KT -alias me -importcert

# Import e1 signed by me, should add me and ca
$KT -alias e1 -certreq | $KT -alias me -gencert | $KT -alias e1 -importcert
$KT -alias e1 -list -v | grep '\[3\]' || { echo Bad E1; exit 1; }

# Import (e2 signed by me,ca,me), should reorder to (e2,me,ca)
( $KT -alias e2 -certreq | $KT -alias me -gencert; $KT -exportcert -alias ca; $KT -exportcert -alias me ) | $KT -alias e2 -importcert
$KT -alias e2 -list -v | grep '\[3\]' || { echo Bad E2; exit 1; }

echo Good

