#
# Copyright (c) 2008, 2018, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6599979
# @summary Ensure that re-assigning the alias works
#
# @library /test/lib ..
# @build SecretKeysBasic
# @run shell SecretKeysBasic.sh
#
# To run by hand:
#    %sh SecretKeysBasic.sh
#
# Note:
#    . test only runs on solaris at the moment

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory

# if running by hand on windows, change TESTSRC and TESTCLASSES to "."
if [ "${TESTSRC}" = "" ] ; then
    TESTSRC=`pwd`
fi
if [ "${TESTCLASSES}" = "" ] ; then
    TESTCLASSES=`pwd`
fi
if [ "${TESTJAVA}" = "" ] ; then
    JAVAC_CMD=`which javac`
    TESTJAVA=`dirname $JAVAC_CMD`/..
fi
echo TESTSRC=${TESTSRC}
echo TESTCLASSES=${TESTCLASSES}
echo TESTJAVA=${TESTJAVA}
echo CPAPPEND=${CPAPPEND}
echo ""

#DEBUG=sunpkcs11,pkcs11keystore

echo DEBUG=${DEBUG}
echo ""

OS=`uname -s`
case "$OS" in
  SunOS )
    FS="/"
    PS=":"
    OS_VERSION=`uname -r`
    case "${OS_VERSION}" in
      5.1* )
        SOFTTOKEN_DIR=${TESTCLASSES}
        export SOFTTOKEN_DIR
        TOKENS="nss solaris"
        ;;
      * )
        # SunPKCS11-Solaris Test only runs on Solaris 5.10 and later
        TOKENS="nss"
        ;;
    esac
    ;;
  Windows_* )
    FS="\\"
    PS=";"
    TOKENS="nss"
    ;;
  CYGWIN* )
    FS="/"
    PS=";"
    TOKENS="nss"
    ;;
  * )
    FS="/"
    PS=":"
    TOKENS="nss"
    ;;
esac

CP="cp -f"
RM="rm -rf"
MKDIR="mkdir -p"
CHMOD="chmod"


STATUS=0
for token in ${TOKENS}
do

if [ ${token} = "nss" ]
then
    # make cert/key DBs writable if token is NSS
    ${CP} ${TESTSRC}${FS}..${FS}nss${FS}db${FS}cert8.db ${TESTCLASSES}
    ${CHMOD} +w ${TESTCLASSES}${FS}cert8.db

    ${CP} ${TESTSRC}${FS}..${FS}nss${FS}db${FS}key3.db ${TESTCLASSES}
    ${CHMOD} +w ${TESTCLASSES}${FS}key3.db
    USED_FILE_LIST="${TESTCLASSES}${FS}cert8.db ${TESTCLASSES}${FS}key3.db"
elif [ ${token} = "solaris" ]
then
    # copy keystore into write-able location
    if [ -d ${TESTCLASSES}${FS}pkcs11_softtoken ]
    then
        echo "Removing old pkcs11_keystore, creating new pkcs11_keystore"

        echo ${RM} ${TESTCLASSES}${FS}pkcs11_softtoken
        ${RM} ${TESTCLASSES}${FS}pkcs11_softtoken
    fi
    echo ${MKDIR} ${TESTCLASSES}${FS}pkcs11_softtoken${FS}private
    ${MKDIR} ${TESTCLASSES}${FS}pkcs11_softtoken${FS}private

    echo ${MKDIR} ${TESTCLASSES}${FS}pkcs11_softtoken${FS}public
    ${MKDIR} ${TESTCLASSES}${FS}pkcs11_softtoken${FS}public

    echo ${CP} ${TESTSRC}${FS}BasicData${FS}pkcs11_softtoken${FS}objstore_info \
    ${TESTCLASSES}${FS}pkcs11_softtoken
    ${CP} ${TESTSRC}${FS}BasicData${FS}pkcs11_softtoken${FS}objstore_info \
    ${TESTCLASSES}${FS}pkcs11_softtoken

    echo ${CHMOD} +w ${TESTCLASSES}${FS}pkcs11_softtoken${FS}objstore_info
    ${CHMOD} 600 ${TESTCLASSES}${FS}pkcs11_softtoken${FS}objstore_info
    USED_FILE_LIST="${TESTCLASSES}${FS}pkcs11_softtoken"
fi

# run test
cd ${TESTSRC}
${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} \
        -DDIR=${TESTSRC}${FS}BasicData${FS} \
        -classpath \
        ${TESTCLASSES}${PS}${TESTCLASSES}${FS}..${PS}${TESTSRC}${FS}loader.jar${PS}${CPAPPEND} \
        -DCUSTOM_DB_DIR=${TESTCLASSES} \
        -DCUSTOM_P11_CONFIG=${TESTSRC}${FS}BasicData${FS}p11-${token}.txt \
        -DNO_DEFAULT=true \
        -DNO_DEIMOS=true \
        -DTOKEN=${token} \
        -Djava.security.debug=${DEBUG} \
        SecretKeysBasic

#	-DCUSTOM_P11_CONFIG=${TESTSRC}${FS}BasicData${FS}p11-${token}.txt \

# save error status
if [ $? != 0 ]
then
    echo "Test against " ${token} " Failed!"
    STATUS=1
fi

# clean up
${RM} ${USED_FILE_LIST}

done

# return
exit ${STATUS}
