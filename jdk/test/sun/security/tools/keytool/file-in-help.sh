#
# Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6922482
# @summary keytool's help on -file always shows 'output file'
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

LANG=C
$TESTJAVA${FS}bin${FS}keytool ${TESTTOOLVMOPTS} -printcertreq -help 2> h1 || exit 1
$TESTJAVA${FS}bin${FS}keytool ${TESTTOOLVMOPTS} -exportcert -help 2> h2 || exit 2

grep "input file" h1 || exit 3
grep "output file" h2 || exit 4

exit 0

