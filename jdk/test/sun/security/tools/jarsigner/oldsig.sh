#
# Copyright 2007-2010 Sun Microsystems, Inc.  All Rights Reserved.
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
  SunOS | Linux )
    NULL=/dev/null
    PS=":"
    FS="/"
    CP="${FS}bin${FS}cp -f"
    TMP=/tmp
    ;;
  CYGWIN* )
    NULL=/dev/null
    PS=";"
    FS="/"
    CP="cp -f"
    TMP=/tmp
    ;;
  Windows_* )
    NULL=NUL
    PS=";"
    FS="\\"
    CP="cp -f"
    TMP="c:/temp"
    ;;
  * )
    echo "Unrecognized operating system!"
    exit 1;
    ;;
esac

# copy jar file into writeable location
${CP} ${TESTSRC}${FS}oldsig${FS}A.jar B.jar
${CP} ${TESTSRC}${FS}oldsig${FS}A.class B.class

${TESTJAVA}${FS}bin${FS}jar uvf B.jar B.class 
${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -digestalg SHA1 \
    B.jar c
${TESTJAVA}${FS}bin${FS}jarsigner -verify B.jar
