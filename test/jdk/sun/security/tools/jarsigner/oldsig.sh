#
# Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6543940 6868865
# @summary Exception thrown when signing a jarfile in java 1.5
#
# @run shell oldsig.sh

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
  SunOS | Linux | Darwin | AIX )
    NULL=/dev/null
    PS=":"
    FS="/"
    CP="${FS}bin${FS}cp -f"
    ;;
  CYGWIN* )
    NULL=/dev/null
    PS=";"
    FS="/"
    CP="cp -f"
    ;;
  Windows_* )
    NULL=NUL
    PS=";"
    FS="\\"
    CP="cp -f"
    ;;
  * )
    echo "Unrecognized operating system!"
    exit 1;
    ;;
esac

# copy jar file into writeable location
${CP} ${TESTSRC}${FS}oldsig${FS}A.jar B.jar
${CP} ${TESTSRC}${FS}oldsig${FS}A.class B.class

${TESTJAVA}${FS}bin${FS}jar ${TESTTOOLVMOPTS} uvf B.jar B.class
${TESTJAVA}${FS}bin${FS}jarsigner ${TESTTOOLVMOPTS} \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -digestalg SHA1 \
    B.jar c
${TESTJAVA}${FS}bin${FS}jarsigner ${TESTTOOLVMOPTS} -verify B.jar
