#
# Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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
if [ "${COMPILEJAVA}" = "" ]; then
  COMPILEJAVA="${TESTJAVA}"
fi

find_one() {
  for TARGET_FILE in $@; do
    if [ -e "$TARGET_FILE" ]; then
      echo $TARGET_FILE
      return
    fi
  done
}

# set platform-dependent variables
OS=`uname -s`
case "$OS" in
  SunOS )
    FS="/"
    LIBNAME="/usr/lib/mps/`isainfo -n`/libsoftokn3.so"
    ;;
  Linux )
    FS="/"
    ${TESTJAVA}${FS}bin${FS}java -XshowSettings:properties -version 2> allprop
    cat allprop | grep os.arch | grep 64
    if [ "$?" != "0" ]; then
        LIBNAME=`find_one \
            "/usr/lib/libsoftokn3.so" \
            "/usr/lib/i386-linux-gnu/nss/libsoftokn3.so"`
    else
        LIBNAME=`find_one \
            "/usr/lib64/libsoftokn3.so" \
            "/usr/lib/x86_64-linux-gnu/nss/libsoftokn3.so"`
    fi
    ;;
  * )
    echo "Will not run test on: ${OS}"
    exit 0;
    ;;
esac

if [ "$LIBNAME" = "" ]; then
  echo "Cannot find libsoftokn3.so"
  exit 1
fi

${COMPILEJAVA}${FS}bin${FS}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d . -XDignore.symbol.file \
        ${TESTSRC}${FS}KeyToolTest.java || exit 10

NSS=${TESTSRC}${FS}..${FS}..${FS}pkcs11${FS}nss

cp ${TESTSRC}${FS}p11-nss.txt .
cp ${NSS}${FS}db${FS}cert8.db .
cp ${NSS}${FS}db${FS}key3.db .
cp ${NSS}${FS}db${FS}secmod.db .

chmod u+w key3.db
chmod u+w cert8.db

echo | ${TESTJAVA}${FS}bin${FS}java -Dnss \
   -Dnss.lib=${LIBNAME} \
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
