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
# @bug 8008296
# @summary confirm that keytool correctly imports user passwords
#
# @run shell StorePasswordsByShell.sh

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
  SunOS | Linux | Darwin | AIX)
    PATHSEP=":"
    FILESEP="/"
    ;;
  CYGWIN* )
    PATHSEP=";"
    FILESEP="/"
    ;;
  Windows* )
    PATHSEP=";"
    FILESEP="\\"
    ;;
  * )
    echo "Unrecognized system!"
    exit 1;
    ;;
esac

PBE_ALGORITHMS="\
 default-PBE-algorithm \
 PBEWithMD5AndDES \
 PBEWithSHA1AndDESede \
 PBEWithSHA1AndRC2_40 \
 PBEWithSHA1AndRC2_128 
 PBEWithSHA1AndRC4_40 \
 PBEWithSHA1AndRC4_128 \
 PBEWithHmacSHA1AndAES_128 \
 PBEWithHmacSHA224AndAES_128 \
 PBEWithHmacSHA256AndAES_128 \
 PBEWithHmacSHA384AndAES_128 \
 PBEWithHmacSHA512AndAES_128 \
 PBEWithHmacSHA1AndAES_256 \
 PBEWithHmacSHA224AndAES_256 \
 PBEWithHmacSHA256AndAES_256 \
 PBEWithHmacSHA384AndAES_256 \
 PBEWithHmacSHA512AndAES_256"

USER_PWD="hello1\n"
ALIAS_PREFIX="this entry is protected by "
COUNTER=0

# cleanup
rm mykeystore.p12 > /dev/null 2>&1

echo
for i in $PBE_ALGORITHMS; do

    if [ $i = "default-PBE-algorithm" ]; then
        KEYALG=""
    else
        KEYALG="-keyalg ${i}"
    fi

    if [ $COUNTER -lt 5 ]; then
        IMPORTPASSWORD="-importpassword"
    else
        IMPORTPASSWORD="-importpass"
    fi

    echo "Storing user password (protected by ${i})"
    echo "${USER_PWD}" | \
        ${TESTJAVA}${FILESEP}bin${FILESEP}keytool ${IMPORTPASSWORD} \
            -storetype pkcs12 -keystore mykeystore.p12 -storepass changeit \
            -alias "${ALIAS_PREFIX}${i}" ${KEYALG} > /dev/null 2>&1
    if [ $? -ne 0 ]; then
        echo Error
    else
        echo OK
        COUNTER=`expr ${COUNTER} + 1`
    fi
done
echo

COUNTER2=`${TESTJAVA}${FILESEP}bin${FILESEP}keytool -list -storetype pkcs12 \
  -keystore mykeystore.p12 -storepass changeit | grep -c "${ALIAS_PREFIX}"`

RESULT="stored ${COUNTER} user passwords, detected ${COUNTER2} user passwords"
if [ $COUNTER -ne $COUNTER2 -o $COUNTER -lt 11 ]; then
    echo "ERROR: $RESULT"
    exit 1
else
    echo "OK: $RESULT"
    exit 0
fi
