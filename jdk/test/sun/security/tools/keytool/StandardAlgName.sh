#
# Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

# @test 1.1 04/11/12
# @bug 4909889
# @summary KeyTool accepts any input that user make as long as we can make some
#          sense out of it, but when comes to present the info the user, it
#          promotes a standard look.
# @author Andrew Fan
#
# @run shell/timeout=240 StandardAlgName.sh
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
#CA
${TESTJAVA}${FS}bin${FS}keytool -genkey -v -alias pkcs12testCA -keyalg "RsA" -keysize 2048 -sigalg "ShA1wItHRSA" -dname "cn=PKCS12 Test CA, ou=Security SQE, o=JavaSoft, c=US" -validity 3650 -keypass storepass -keystore keystoreCA.jceks.data -storepass storepass -storetype jceKS 2>&1 | egrep 'RsA|ShA1wItHRSA'

RESULT=$?
if [ $RESULT -eq 0 ]; then 
    exit 1
else
    #Lead
    ${TESTJAVA}${FS}bin${FS}keytool -genkey -v -alias pkcs12testLead -keyalg "rSA" -keysize 1024 -sigalg "mD5withRSA" -dname "cn=PKCS12 Test Lead, ou=Security SQE, o=JavaSoft, c=US" -validity 3650 -keypass storepass -keystore keystoreLead.jceks.data -storepass storepass -storetype jCeks 2>&1 | egrep 'rSA|mD5withRSA'
    RESULT=$?
    if [ $RESULT -eq 0 ]; then 
        exit 1
    else
        #End User 1
        ${TESTJAVA}${FS}bin${FS}keytool -genkey -v -alias pkcs12testEndUser1 -keyalg "RSa" -keysize 1024 -sigalg "sHa1wIThRSA" -dname "cn=PKCS12 Test End User 1, ou=Security SQE, o=JavaSoft, c=US" -validity 3650 -keypass storepass -keystore keystoreEndUser1.jceks.data -storepass storepass -storetype Jceks 2>&1 | egrep 'RSa|sHa1wIThRSA'
        RESULT=$?
        if [ $RESULT -eq 0 ]; then 
            exit 1
        else
            exit 0
        fi
    fi
fi

