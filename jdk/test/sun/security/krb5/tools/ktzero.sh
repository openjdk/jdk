#
# Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8014196
# @summary ktab creates a file with zero kt_vno
# @run shell ktzero.sh
#

if [ "${TESTJAVA}" = "" ] ; then
  JAVAC_CMD=`which javac`
  TESTJAVA=`dirname $JAVAC_CMD`/..
fi

if [ "${TESTSRC}" = "" ] ; then
  TESTSRC="."
fi

OS=`uname -s`
case "$OS" in
  CYGWIN* )
    FS="/"
    ;;
  Windows_* )
    FS="\\"
    ;;
  * )
    FS="/"
    echo "Unsupported system!"
    exit 0;
    ;;
esac

KEYTAB=ktzero.tmp

rm $KEYTAB 2> /dev/null
KTAB="${TESTJAVA}${FS}bin${FS}ktab -k $KEYTAB"

# Listing non-existing ktab should fail
$KTAB -l && exit 1

# Can add to non-existing ktab
$KTAB -a me@LOCAL mine || exit 2

# Now can be listed
$KTAB -l || exit 3

echo ABCDEFG > $KEYTAB

# Invalid keytab should fail for all commands
$KTAB -l && exit 4
$KTAB -a me@LOCAL mine && exit 2

exit 0
