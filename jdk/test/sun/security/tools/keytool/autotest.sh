#
# Copyright (c) 2006, 2009, Oracle and/or its affiliates. All rights reserved.
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
# @summary (almost) all keytool behaviors
# @author Weijun Wang
#
# This test is only executed on several platforms
#
# set a few environment variables so that the shell-script can run stand-alone
# in the source directory
if [ "${TESTSRC}" = "" ] ; then
  TESTSRC="."
fi
if [ "${TESTCLASSES}" = "" ] ; then
  TESTCLASSES="."
fi
if [ "${TESTJAVA}" = "" ] ; then
  echo "TESTJAVA not set.  Test cannot execute."
  echo "FAILED!!!"
  exit 1
fi

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS )
    FS="/"
    LIBNAME=libsoftokn3.so
    ARCH=`isainfo`
    case "$ARCH" in
      sparc* )
        PF="solaris-sparc"
        ;;
      * )
        echo "Will not run test on: Solaris ${ARCH}"
        exit 0;
        ;;
    esac
    ;;
  Linux )
    LIBNAME=libsoftokn3.so
    ARCH=`uname -m`
    FS="/"
    case "$ARCH" in
      i[3-6]86 )
        PF="linux-i586"
        ;;
      * )
        echo "Will not run test on: Linux ${ARCH}"
        exit 0;
        ;;
    esac
    ;;
  * )
    echo "Will not run test on: ${OS}"
    exit 0;
    ;;
esac

${TESTJAVA}${FS}bin${FS}javac -d . ${TESTSRC}${FS}KeyToolTest.java || exit 10

NSS=${TESTSRC}${FS}..${FS}..${FS}pkcs11${FS}nss

cp ${TESTSRC}${FS}p11-nss.txt .
cp ${NSS}${FS}db${FS}cert8.db .
cp ${NSS}${FS}db${FS}key3.db .
cp ${NSS}${FS}db${FS}secmod.db .

chmod u+w key3.db
chmod u+w cert8.db

echo | ${TESTJAVA}${FS}bin${FS}java -Dnss \
   -Dnss.lib=${NSS}${FS}lib${FS}${PF}${FS}${LIBNAME} \
   KeyToolTest
status=$?

rm -f p11-nss.txt
rm -f cert8.db
rm -f key3.db
rm -f secmod.db

rm HumanInputStream*.class
rm KeyToolTest*.class
rm TestException.class

exit $status

