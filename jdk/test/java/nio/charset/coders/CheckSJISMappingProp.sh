#!/bin/sh

#
# Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
# @summary Verify that sun.nio.cs.map property interpreted in ja multibyte locales
# @bug 4879123
# @build SJISPropTest
#
# @run shell/timeout=300 CheckSJISMappingProp.sh

# set platform-dependent variables

OS=`uname -s`
case "$OS" in
  SunOS | Linux ) ;;
  # Skip locale test for Windows
  Windows* )
    echo "Passed"; exit 0 ;;
  * ) echo "Unrecognized system!" ;  exit 1 ;;
esac

expectPass() {
  if [ $1 -eq 0 ]
  then echo "--- passed as expected"
  else
    echo "--- failed"
    exit $1
  fi
}


JAVA="${TESTJAVA}/bin/java -cp ${TESTCLASSES}"
runTest() {
  echo "Testing:" ${1}
  LC_ALL="$1" ; export LC_ALL
  locale
  # Firstly, test with property set
  # (shift_jis should map to windows-31J charset) 
  ${JAVA} -Dsun.nio.cs.map="Windows-31J/Shift_JIS" SJISPropTest MS932
  expectPass $?

  # Next, test without property set - "shift_jis" follows IANA conventions
  # and should map to the sun.nio.cs.ext.Shift_JIS charset
  ${JAVA} SJISPropTest Shift_JIS
  expectPass $?
}

# Run the test in the common Solaris/Linux locales
# Tests will simply run in current locale if locale isn't supported
# on the test machine/platform

for i in "ja" "ja_JP.PCK" "ja_JP.eucJP"  ; do
  runTest ${i}
done
