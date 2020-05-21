#
# Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4938185
# @library /test/lib
# @summary KeyStore support for NSS cert/key databases
#
# @run shell Basic.sh

# To run by hand:
#    %sh Basic.sh <recompile> [yes|no]
#		  <token>     [activcard|ibutton|nss|sca1000]
#                 <command>   [list|basic]
#
#    %sh Basic.sh no ibutton list
#
# Note:
#    . 'list' lists the token aliases
#    . 'basic' does not run with activcard,
#      and tests different things depending on what is supported by each token

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
echo CPAPPEND=${CPAPPEND}
echo ""

# get command from input args -
# default to 'nss basic'

RECOMPILE="yes"
if [ $# = '3' ] ; then
    RECOMPILE=$1
    TOKEN=$2
    TEST=$3
elif [ $# = '2' ] ; then
    TOKEN=$1
    TEST=$2
else
    TOKEN="nss"
    TEST="basic"
fi

DEBUG=sunpkcs11,pkcs11keystore

echo RECOMPILE=${RECOMPILE}
echo TOKEN=${TOKEN}
echo TEST=${TEST}
echo DEBUG=${DEBUG}
echo ""

OS=`uname -s`
case "$OS" in
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

# first make cert/key DBs writable if token is NSS

if [ "${TOKEN}" = "nss" ] ; then
    ${CP} ${TESTSRC}${FS}..${FS}nss${FS}db${FS}cert8.db ${TESTCLASSES}
    ${CHMOD} +w ${TESTCLASSES}${FS}cert8.db

    ${CP} ${TESTSRC}${FS}..${FS}nss${FS}db${FS}key3.db ${TESTCLASSES}
    ${CHMOD} +w ${TESTCLASSES}${FS}key3.db
fi

# compile test

if [ "${RECOMPILE}" = "yes" ] ; then
    ${COMPILEJAVA}${FS}bin${FS}javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} \
  -classpath ${TESTSRC}${FS}..${PS}${TESTSRC}${FS}loader.jar \
  -d ${TESTCLASSES} \
  ${TESTSRC}${FS}..${FS}..${FS}..${FS}..${FS}..${FS}lib${FS}jdk${FS}test${FS}lib${FS}artifacts${FS}*.java \
  ${TESTSRC}${FS}Basic.java \
  ${TESTSRC}${FS}..${FS}PKCS11Test.java
fi

# run test

${TESTJAVA}${FS}bin${FS}java ${TESTVMOPTS} \
	-classpath ${TESTCLASSES}${PS}${TESTSRC}${FS}loader.jar${PS}${CPAPPEND} \
	-DDIR=${TESTSRC}${FS}BasicData \
	-DCUSTOM_DB_DIR=${TESTCLASSES} \
	-DCUSTOM_P11_CONFIG=${TESTSRC}${FS}BasicData${FS}p11-${TOKEN}.txt \
	-DNO_DEFAULT=true \
	-DNO_DEIMOS=true \
	-DTOKEN=${TOKEN} \
	-DTEST=${TEST} \
	-Dtest.src=${TESTSRC} \
	-Dtest.classes=${TESTCLASSES} \
	-Djava.security.debug=${DEBUG} \
	Basic sm Basic.policy

# save error status
status=$?

# return
exit $status
