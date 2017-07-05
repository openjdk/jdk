#
# Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4938185 7106773
# @summary KeyStore support for NSS cert/key databases
#          512 bits RSA key cannot work with SHA384 and SHA512
#
# @run shell ClientAuth.sh

# set a few environment variables so that the shell-script can run stand-alone
# in the source directory

if [ "${TESTSRC}" = "" ] ; then
    TESTSRC=`pwd`
fi
if [ "${TESTCLASSES}" = "" ] ; then
    TESTCLASSES=`pwd`
fi
if [ "${TESTJAVA}" = "" ] ; then
    TESTJAVA="/net/radiant/export1/charlie/mustang/build/solaris-sparc"
fi
echo TESTSRC=${TESTSRC}
echo TESTCLASSES=${TESTCLASSES}
echo TESTJAVA=${TESTJAVA}
echo ""

OS=`uname -s`
case "$OS" in
  SunOS )
    ARCH=`isainfo`
    case "$ARCH" in
      sparc* )
	FS="/"
	PS=":"
	CP="${FS}bin${FS}cp"
	CHMOD="${FS}bin${FS}chmod"
	;;
      i[3-6]86 )
	FS="/"
	PS=":"
	CP="${FS}bin${FS}cp"
	CHMOD="${FS}bin${FS}chmod"
	;;
      amd64* )
	FS="/"
	PS=":"
	CP="${FS}bin${FS}cp"
	CHMOD="${FS}bin${FS}chmod"
	;;
      * )
#     ?itanium? )
#     amd64* )
	echo "Unsupported System: Solaris ${ARCH}"
	exit 0;
	;;
    esac
    ;;
  Linux )
    ARCH=`uname -m`
    case "$ARCH" in
      i[3-6]86 )
	FS="/"
	PS=":"
	CP="${FS}bin${FS}cp"
	CHMOD="${FS}bin${FS}chmod"
	;;
      * )
#     ia64 )
#     x86_64 )
	echo "Unsupported System: Linux ${ARCH}"
	exit 0;
	;;
    esac
    ;;
  Windows* )
    FS="\\"
    PS=";"
    CP="cp"
    CHMOD="chmod"

    # 'uname -m' does not give us enough information -
    #  should rely on $PROCESSOR_IDENTIFIER (as is done in Defs-windows.gmk),
    #  but JTREG does not pass this env variable when executing a shell script.
    #
    #  execute test program - rely on it to exit if platform unsupported

    ;;
  * )
    echo "Unsupported System: ${OS}"
    exit 0;
    ;;
esac

# first make cert/key DBs writable

${CP} ${TESTSRC}${FS}ClientAuthData${FS}cert8.db ${TESTCLASSES}
${CHMOD} +w ${TESTCLASSES}${FS}cert8.db

${CP} ${TESTSRC}${FS}ClientAuthData${FS}key3.db ${TESTCLASSES}
${CHMOD} +w ${TESTCLASSES}${FS}key3.db

# compile test
${TESTJAVA}${FS}bin${FS}javac \
	-classpath ${TESTSRC}${FS}..${PS}${TESTSRC}${FS}loader.jar \
	-d ${TESTCLASSES} \
	${TESTSRC}${FS}ClientAuth.java

# run test
echo "Run ClientAuth ..."
${TESTJAVA}${FS}bin${FS}java \
	-classpath ${TESTCLASSES}${PS}${TESTSRC}${FS}loader.jar \
	-DDIR=${TESTSRC}${FS}ClientAuthData${FS} \
	-DCUSTOM_DB_DIR=${TESTCLASSES} \
	-DCUSTOM_P11_CONFIG=${TESTSRC}${FS}ClientAuthData${FS}p11-nss.txt \
	-DNO_DEFAULT=true \
	-DNO_DEIMOS=true \
	-Dtest.src=${TESTSRC} \
	-Dtest.classes=${TESTCLASSES} \
	ClientAuth

# save error status
status=$?

# return if failed
if [ "${status}" != "0" ] ; then
    exit $status
fi

# run test with specified TLS protocol and cipher suite
echo "Run ClientAuth TLSv1.2 TLS_DHE_RSA_WITH_AES_128_CBC_SHA"
${TESTJAVA}${FS}bin${FS}java \
	-classpath ${TESTCLASSES}${PS}${TESTSRC}${FS}loader.jar \
	-DDIR=${TESTSRC}${FS}ClientAuthData${FS} \
	-DCUSTOM_DB_DIR=${TESTCLASSES} \
	-DCUSTOM_P11_CONFIG=${TESTSRC}${FS}ClientAuthData${FS}p11-nss.txt \
	-DNO_DEFAULT=true \
	-DNO_DEIMOS=true \
	-Dtest.src=${TESTSRC} \
	-Dtest.classes=${TESTCLASSES} \
	ClientAuth TLSv1.2 TLS_DHE_RSA_WITH_AES_128_CBC_SHA

# save error status
status=$?

# return
exit $status
