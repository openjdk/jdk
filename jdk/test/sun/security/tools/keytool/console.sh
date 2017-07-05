#
# Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6418647 8005527
# @summary Doc bug 5035358 shows sun.security.util.Password.readPassword() is buggy.
# @author Weijun Wang
# @run shell/manual console.sh

if [ "$ALT_PASS" = "" ]; then
	PASSW=äöäöäö
else
  	PASSW=$ALT_PASS
fi

KS=/tmp/kkk.$$

cat <<____

ATTENTION
===============================================================

This test is about non-ASCII password input compatibility between
JDK 5.0 and later versions. Before running the test, make sure that --

    \$J5 points to a JDK 5.0 installation
    \$JM points to the current installation

The password string used in this test is $PASSW. If you find difficulty
entering it in in your system, feel free to change it to something else
by providing \$ALT_PASS. It should be no less than 6 characters and include
some non-ASCII characters.

For each test, type into the characters as described in the test header.
<R> means the RETURN (or ENTER key). Please wait for a little while
after <R> is pressed each time.

\$J5 is now $J5
\$JM is now $JM

____


if [ "$J5" = "" -o "$JM" = "" ]; then
	echo "Define \$J5 and \$JM first"
	exit 1
fi

echo "Press ENTER to start the test, or Ctrl-C to stop it"
read x

echo
echo "=========================================="
echo "Test #1: 5->6, non-prompt. Please type <R>"
echo "=========================================="
echo
rm $KS 2> /dev/null
$J5/bin/keytool -keystore $KS -genkey -dname CN=olala -storepass $PASSW || exit 1
$JM/bin/keytool -keystore $KS -list -storepass $PASSW || exit 2

echo "=========================================="
echo "Test #2: 6->5, non-prompt. Please type <R>"
echo "=========================================="
echo

rm $KS 2> /dev/null
$JM/bin/keytool -keystore $KS -genkey -dname CN=olala -storepass $PASSW || exit 3
$J5/bin/keytool -keystore $KS -list -storepass $PASSW || exit 4

echo "============================================================"
echo "Test #3: 5->6, prompt. Please type $PASSW <R> <R> $PASSW <R>"
echo "============================================================"
echo

rm $KS 2> /dev/null
$J5/bin/keytool -keystore $KS -genkey -dname CN=olala || exit 5
$JM/bin/keytool -keystore $KS -list || exit 6
echo $PASSW| $J5/bin/keytool -keystore $KS -list || exit 7
echo $PASSW| $JM/bin/keytool -keystore $KS -list || exit 8

echo "======================================================================="
echo "Test #4: 6->5, prompt. Please type $PASSW <R> $PASSW <R> <R> $PASSW <R>"
echo "======================================================================="
echo

rm $KS 2> /dev/null
$JM/bin/keytool -keystore $KS -genkey -dname CN=olala || exit 9
$J5/bin/keytool -keystore $KS -list || exit 10
echo $PASSW| $JM/bin/keytool -keystore $KS -list || exit 11
echo $PASSW| $J5/bin/keytool -keystore $KS -list || exit 12

echo "==========================================="
echo "Test #5: 5->6, pipe. Please type $PASSW <R>"
echo "==========================================="
echo

rm $KS 2> /dev/null
echo $PASSW| $J5/bin/keytool -keystore $KS -genkey -dname CN=olala || exit 13
$JM/bin/keytool -keystore $KS -list || exit 14
echo $PASSW| $J5/bin/keytool -keystore $KS -list || exit 15
echo $PASSW| $JM/bin/keytool -keystore $KS -list || exit 16

rm $KS 2> /dev/null

echo
echo "Success"

exit 0
