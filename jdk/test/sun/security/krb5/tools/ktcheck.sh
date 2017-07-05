#
# Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6950546
# @summary "ktab -d name etype" to "ktab -d name [-e etype] [kvno | all | old]"
# @modules java.security.jgss/sun.security.krb5.internal.ktab
#          java.security.jgss/sun.security.krb5
# @compile KtabCheck.java
# @run shell ktcheck.sh
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

KEYTAB=ktab.tmp

rm $KEYTAB

EXTRA_OPTIONS="-Djava.security.krb5.conf=${TESTSRC}${FS}onlythree.conf"
KTAB="${TESTJAVA}${FS}bin${FS}ktab -J${EXTRA_OPTIONS} -k $KEYTAB -f"
CHECK="${TESTJAVA}${FS}bin${FS}java -cp ${TESTCLASSES} ${TESTVMOPTS} ${EXTRA_OPTIONS} \
        --add-exports java.security.jgss/sun.security.krb5.internal.ktab=ALL-UNNAMED \
        --add-exports java.security.jgss/sun.security.krb5=ALL-UNNAMED \
        KtabCheck $KEYTAB"

echo ${EXTRA_OPTIONS}

$KTAB -a me mine
$CHECK 1 16 1 23 1 17 || exit 1
$KTAB -a me mine -n 0
$CHECK 0 16 0 23 0 17 || exit 1
$KTAB -a me mine -n 1 -append
$CHECK 0 16 0 23 0 17 1 16 1 23 1 17 || exit 1
$KTAB -a me mine -append
$CHECK 0 16 0 23 0 17 1 16 1 23 1 17 2 16 2 23 2 17 || exit 1
$KTAB -a me mine
$CHECK 3 16 3 23 3 17 || exit 1
$KTAB -a me mine -n 4 -append
$CHECK 3 16 3 23 3 17 4 16 4 23 4 17 || exit 1
$KTAB -a me mine -n 5 -append
$CHECK 3 16 3 23 3 17 4 16 4 23 4 17 5 16 5 23 5 17 || exit 1
$KTAB -a me mine -n 6 -append
$CHECK 3 16 3 23 3 17 4 16 4 23 4 17 5 16 5 23 5 17 6 16 6 23 6 17 || exit 1
$KTAB -d me 3
$CHECK 4 16 4 23 4 17 5 16 5 23 5 17 6 16 6 23 6 17 || exit 1
$KTAB -d me -e 16 6
$CHECK 4 16 4 23 4 17 5 16 5 23 5 17 6 23 6 17 || exit 1
$KTAB -d me -e 17 6
$CHECK 4 16 4 23 4 17 5 16 5 23 5 17 6 23 || exit 1
$KTAB -d me -e 16 5
$CHECK 4 16 4 23 4 17 5 23 5 17 6 23 || exit 1
$KTAB -d me old
$CHECK 4 16 5 17 6 23 || exit 1
$KTAB -d me old
$CHECK 4 16 5 17 6 23 || exit 1
$KTAB -d me
$CHECK || exit 1
