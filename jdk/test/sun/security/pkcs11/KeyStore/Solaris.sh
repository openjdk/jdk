#
# Copyright (c) 2004, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 5038659
# @summary Enable PKCS#11 KeyStore for SunPKCS11-Solaris
#
# @run shell Solaris.sh

# To run by hand:
#    %sh Solaris.sh <recompile> [yes|no]
#                   <command>   [list|basic]
#
#    %sh Solaris.sh no list
#
# Note:
#    . test only runs on solaris at the moment
#    . 'list' lists the token aliases
#    . 'basic' tests different things

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory

# if running by hand on windows, change TESTSRC and TESTCLASSES to "."
if [ "${TESTSRC}" = "" ] ; then
    TESTSRC=`pwd`
fi
if [ "${TESTCLASSES}" = "" ] ; then
    TESTCLASSES=`pwd`
fi

# if running by hand on windows, change this to appropriate value
if [ "${TESTJAVA}" = "" ] ; then
    TESTJAVA="/net/radiant/export1/charlie/mustang/build/solaris-sparc"
fi
if [ "${COMPILEJAVA}" = "" ]; then
  COMPILEJAVA="${TESTJAVA}"
fi
echo TESTSRC=${TESTSRC}
echo TESTCLASSES=${TESTCLASSES}
echo TESTJAVA=${TESTJAVA}
echo COMPILEJAVA=${COMPILEJAVA}
echo ""

# get command from input args -
# default to 'solaris basic'

RECOMPILE="yes"
if [ $# = '2' ] ; then
    RECOMPILE=$1
    TEST=$2
elif [ $# = '1' ] ; then
    TEST=$1
else
    TEST="basic"
fi

DEBUG=sunpkcs11,pkcs11keystore

echo RECOMPILE=${RECOMPILE}
echo TEST=${TEST}
echo DEBUG=${DEBUG}
echo ""

OS=`uname -s`
case "$OS" in
  SunOS )
    FS="/"
    PS=":"
    SCCS="${FS}usr${FS}ccs${FS}bin${FS}sccs"
    CP="${FS}bin${FS}cp -f"
    RM="${FS}bin${FS}rm -rf"
    MKDIR="${FS}bin${FS}mkdir -p"
    CHMOD="${FS}bin${FS}chmod"
    ;;
  * )
    echo "Unsupported System ${OS} - Test only runs on Solaris 10"
    exit 0;
    ;;
esac

OS_VERSION=`uname -r`
case "$OS_VERSION" in
  5.1* )
    SOFTTOKEN_DIR=${TESTCLASSES}
    export SOFTTOKEN_DIR
    ;;
  * )
    echo "Unsupported Version ${OS_VERSION} - Test only runs on Solaris 10"
    exit 0;
    ;;
esac

# copy keystore into write-able location

echo "Removing old pkcs11_keystore, creating new pkcs11_keystore"

echo ${RM} ${TESTCLASSES}${FS}pkcs11_softtoken
${RM} ${TESTCLASSES}${FS}pkcs11_softtoken

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

# compile test

if [ "${RECOMPILE}" = "yes" ] ; then
    cd ${TESTCLASSES}
    ${RM} *.class
    ${COMPILEJAVA}${FS}bin${FS}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
	-classpath ${TESTSRC}${FS}..${PS}${TESTSRC}${FS}loader.jar \
	-d ${TESTCLASSES} \
	${TESTSRC}${FS}Basic.java \
	${TESTSRC}${FS}..${FS}PKCS11Test.java
fi

# run test

cd ${TESTSRC}
${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} \
	-classpath ${TESTCLASSES}${PS}${TESTSRC}${FS}loader.jar \
	-DDIR=${TESTSRC}${FS}BasicData${FS} \
	-DCUSTOM_P11_CONFIG=${TESTSRC}${FS}BasicData${FS}p11-solaris.txt \
	-DNO_DEFAULT=true \
	-DNO_DEIMOS=true \
	-DTOKEN=solaris \
	-DTEST=${TEST} \
	-Djava.security.debug=${DEBUG} \
	Basic sm Basic.policy

# clean up

#${RM} ${TESTCLASSES}${FS}pkcs11_softtoken

# return

exit $?
