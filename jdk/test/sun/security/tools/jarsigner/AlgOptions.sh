#
# Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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
# @bug 5094028 6219522
# @summary test new jarsigner -sigalg and -digestalg options
# @author Sean Mullan
#
# @run shell AlgOptions.sh
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
${CP} ${TESTSRC}${FS}AlgOptions.jar ${TESTCLASSES}${FS}AlgOptionsTmp.jar

failed=0
# test missing signature algorithm arg
${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -sigalg \
    ${TESTCLASSES}${FS}AlgOptionsTmp.jar c
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 1 failed"
    failed=1
else 
    echo "test 1 passed"
fi

# test missing digest algorithm arg
${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -digestalg \
    ${TESTCLASSES}${FS}AlgOptionsTmp.jar c
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 2 failed"
    failed=1
else 
    echo "test 2 passed"
fi

# test BOGUS signature algorithm 
${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -sigalg BOGUS \
    ${TESTCLASSES}${FS}AlgOptionsTmp.jar c
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 3 failed"
    failed=1
else 
    echo "test 3 passed"
fi

# test BOGUS digest algorithm 
${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -digestalg BOGUS \
    ${TESTCLASSES}${FS}AlgOptionsTmp.jar c
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 4 failed"
    failed=1
else 
    echo "test 4 passed"
fi

# test incompatible signature algorithm 
${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -sigalg SHA1withDSA \
    ${TESTCLASSES}${FS}AlgOptionsTmp.jar c
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 5 failed"
    failed=1
else 
    echo "test 5 passed"
fi

# test compatible signature algorithm 
${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -sigalg SHA512withRSA \
    ${TESTCLASSES}${FS}AlgOptionsTmp.jar c
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 6 passed"
else 
    echo "test 6 failed"
    failed=1
fi

# verify it 
${TESTJAVA}${FS}bin${FS}jarsigner -verify ${TESTCLASSES}${FS}AlgOptionsTmp.jar
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 7 passed"
else 
    echo "test 7 failed"
    failed=1
fi

# test non-default digest algorithm 
${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -digestalg SHA-256 \
    ${TESTCLASSES}${FS}AlgOptionsTmp.jar c
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 8 passed"
else 
    echo "test 8 failed"
    failed=1
fi

# verify it 
${TESTJAVA}${FS}bin${FS}jarsigner -verify ${TESTCLASSES}${FS}AlgOptionsTmp.jar
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 9 passed"
else 
    echo "test 9 failed"
    failed=1
fi

# test SHA-512 digest algorithm (creates long lines)
${TESTJAVA}${FS}bin${FS}jarsigner \
    -keystore ${TESTSRC}${FS}JarSigning.keystore \
    -storepass bbbbbb \
    -digestalg SHA-512 \
    -sigalg SHA512withRSA \
    ${TESTCLASSES}${FS}AlgOptionsTmp.jar c
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 10 passed"
else 
    echo "test 10 failed"
    failed=1
fi

# verify it 
${TESTJAVA}${FS}bin${FS}jarsigner -verify ${TESTCLASSES}${FS}AlgOptionsTmp.jar
RESULT=$?
if [ $RESULT -eq 0 ]; then
    echo "test 11 passed"
else 
    echo "test 11 failed"
    failed=1
fi

if [ $failed -eq 1 ]; then
    exit 1
else
    exit 0
fi
