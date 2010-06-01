#! /bin/sh

#
# Copyright (c) 2006, 2008, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6418647
# @summary Doc bug 5035358 shows sun.security.util.Password.readPassword() is buggy.
# @author Weijun Wang
#
# @run shell/manual console.sh

if [ "$ALT_PASS" = "" ]; then
  export PASS=äöäöäöäö
else
  export PASS=$ALT_PASS
fi

echo "ATTENTION"
echo "==============================================================="
echo
echo "This test is about console password input compatibility between"
echo "Tiger and Mustang. Before running the test, make sure that --"
echo "\$J5 points to a JDK 5.0 installation"
echo "\$JM points to a JDK 6 installation".
echo
echo "The password string used in this test is $PASS. If you find difficulty"
echo "entering it in in your system, feel free to change it to something else"
echo "by providing \$ALT_PASS (should be not less than 6 characters)"
echo
echo "For all prompt of \"Enter keystore password\", type $PASS and press ENTER"
echo "For all prompt of \"Enter key password for <mykey> (RETURN if same as keystore password)\", press ENTER"
echo "If you see both the prompts appear, say --"
echo "   Enter key password for <mykey>"
echo "         (RETURN if same as keystore password):  Enter keystore password:"
echo "only response to the last prompt by typing $PASS and press ENTER"
echo
echo "Only if all the command run correctly without showing any error "
echo "or warning, this test passes."
echo
echo "Press ENTER to start the test, or Ctrl-C to stop it"
read
echo
echo "Test #1: 5->6, non-prompt"
rm kkk
$J5/bin/keytool -keystore kkk -genkey -dname CN=olala -storepass $PASS
$JM/bin/keytool -keystore kkk -list -storepass $PASS
echo "Test #2: 6->5, non-prompt"
rm kkk
$JM/bin/keytool -keystore kkk -genkey -dname CN=olala -storepass $PASS
$J5/bin/keytool -keystore kkk -list -storepass $PASS
echo "Test #3: 5->6, prompt"
rm kkk
$J5/bin/keytool -keystore kkk -genkey -dname CN=olala
$JM/bin/keytool -keystore kkk -list
echo $PASS| $J5/bin/keytool -keystore kkk -list
echo $PASS| $JM/bin/keytool -keystore kkk -list
echo "Test #4: 6->5, prompt"
rm kkk
$JM/bin/keytool -keystore kkk -genkey -dname CN=olala
$J5/bin/keytool -keystore kkk -list
echo $PASS| $JM/bin/keytool -keystore kkk -list
echo $PASS| $J5/bin/keytool -keystore kkk -list
echo "Test #5: 5->6, pipe"
rm kkk
echo $PASS| $J5/bin/keytool -keystore kkk -genkey -dname CN=olala
$JM/bin/keytool -keystore kkk -list
echo $PASS| $J5/bin/keytool -keystore kkk -list
echo $PASS| $JM/bin/keytool -keystore kkk -list
rm kkk

exit 0
