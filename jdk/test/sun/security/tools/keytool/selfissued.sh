#
# Copyright 2009-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 6825352 6937978
# @summary support self-issued certificate in keytool and let -gencert generate the chain
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
$KT -alias ca1 -dname CN=CA -genkeypair
$KT -alias ca2 -dname CN=CA -genkeypair
$KT -alias e1 -dname CN=E1 -genkeypair

# ca signs ca1, ca1 signs ca2, all self-issued
$KT -alias ca1 -certreq | $KT -alias ca -gencert -ext san=dns:ca1 \
        | $KT -alias ca1 -importcert
$KT -alias ca2 -certreq | $KT -alias ca1 -gencert -ext san=dns:ca2 \
        | $KT -alias ca2 -importcert

# Import e1 signed by ca2, should add ca2 and ca1, at least 3 certs in the chain
$KT -alias e1 -certreq | $KT -alias ca2 -gencert > e1.cert
$KT -alias ca1 -delete
$KT -alias ca2 -delete
cat e1.cert | $KT -alias e1 -importcert
$KT -alias e1 -list -v | grep '\[3\]' || { echo Bad E1; exit 1; }

echo Good

