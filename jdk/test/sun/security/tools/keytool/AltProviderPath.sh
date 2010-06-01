#
# Copyright (c) 2004, 2005, Oracle and/or its affiliates. All rights reserved.
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
# @bug 4906940
# @summary Add -providerPath option for keytool allowing one to specify
#          an additional classpath to search for providers.
# @author Andrew Fan
#
# @run build DummyProvider
# @run shell AltProviderPath.sh
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
    TMP=/tmp
    ;;
  CYGWIN* )
    NULL=/dev/null
    PS=";"
    FS="/"
    TMP=/tmp
    ;;
  Windows_* )
    NULL=NUL
    PS=";"
    FS="\\"
    TMP="c:/temp"
    ;;
  * )
    echo "Unrecognized operating system!"
    exit 1;
    ;;
esac

# the test code
#genkey
${TESTJAVA}${FS}bin${FS}keytool -genkey -v -alias dummyTestCA \
    -keyalg "RSA" -keysize 1024 -sigalg "ShA1WithRSA" \
    -dname "cn=Dummy Test CA, ou=JSN, o=JavaSoft, c=US" -validity 3650 \
    -keypass storepass -keystore keystoreCA.dks -storepass storepass \
    -storetype "dks" -provider "org.test.dummy.DummyProvider" \
    -providerPath ${TESTCLASSES}

if [ $? -ne 0 ]; then 
    exit 1
fi

#Change keystore password
${TESTJAVA}${FS}bin${FS}keytool -storepasswd -new storepass2 \
    -keystore keystoreCA.dks -storetype "dks" -storepass storepass \
    -provider "org.test.dummy.DummyProvider" -providerPath ${TESTCLASSES}

if [ $? -ne 0 ]; then 
    exit 1
fi


#Change keystore key password
${TESTJAVA}${FS}bin${FS}keytool -keypasswd -alias "dummyTestCA" \
    -keypass storepass -new keypass -keystore keystoreCA.dks \
    -storetype "dks" -storepass storepass2 \
    -provider "org.test.dummy.DummyProvider" -providerPath ${TESTCLASSES}

if [ $? -ne 0 ]; then 
    exit 1
fi

#Export certificate
${TESTJAVA}${FS}bin${FS}keytool -v -export -rfc -alias "dummyTestCA" \
    -file "dummyTestCA.der" -keystore keystoreCA.dks -storetype "dks" \
    -storepass storepass2 -provider "org.test.dummy.DummyProvider" \
    -providerPath ${TESTCLASSES}

if [ $? -ne 0 ]; then 
    exit 1
fi

#list keystore
${TESTJAVA}${FS}bin${FS}keytool -v -list -keystore keystoreCA.dks \
    -storetype "dks" -storepass storepass2 \
    -provider "org.test.dummy.DummyProvider" -providerPath ${TESTCLASSES}

if [ $? -ne 0 ]; then 
    exit 1
fi

exit 0
