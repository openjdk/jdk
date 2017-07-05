#!/bin/sh

#
# Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6557093
# @summary Check SSL config file permission for out-of-the-box management
#
# @run shell SSLConfigFilePermissionTest.sh

createJavaFile()
{
    cat << EOF > $1/$2.java
    class $2 {
        public static void main(String[] args) {
            System.out.println("Inside main method...");
        }
    }
EOF
}

createManagementConfigFile() {
    cat << EOF > $1
# management.properties
com.sun.management.jmxremote.authenticate=false
com.sun.management.jmxremote.ssl.config.file=$2
EOF
}

createSSLConfigFile() {
    if [ -f "$1" ] ; then
	rm -f $1 || echo WARNING: $1 already exists - unable to remove old copy
    fi
    cat << EOF > $1
javax.net.ssl.keyStore=$2
javax.net.ssl.keyStorePassword=password
EOF
}

# Check we are run from jtreg
if [ -z "${TESTCLASSES}" ]; then
    echo "Test is designed to be run from jtreg only"
    exit 0
fi

# Test not suitable for Windows as chmod may not be able to
# security the password file.

os=`uname -s`
if [ "$os" != "Linux" -a "$os" != "SunOS" ]; then
    echo "Test not designed to run on this operating system, skipping..."
    exit 0
fi

# Create management and SSL configuration files

LIBDIR=${TESTCLASSES}/lib
MGMT=${LIBDIR}/management.properties
SSL=${LIBDIR}/jmxremote.ssl.config
rm -f ${MGMT}
rm -f ${SSL}
mkdir ${LIBDIR} 2>&1
createJavaFile ${TESTCLASSES} Dummy
createManagementConfigFile ${MGMT} ${SSL}
createSSLConfigFile ${SSL} ${TESTSRC}/ssl/keystore

# Compile test

${TESTJAVA}/bin/javac -d ${TESTCLASSES} ${TESTCLASSES}/Dummy.java

JAVA=${TESTJAVA}/bin/java
CLASSPATH=${TESTCLASSES}
export CLASSPATH

failures=0

mp=-Dcom.sun.management.config.file=${MGMT}
pp=-Dcom.sun.management.jmxremote.port=4999

go() {
    echo ''
    sh -xc "$JAVA $1 $2 $3 $4 $5 $6 $7 $8" 2>&1
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}

# Test 1 - SSL config file is secure - VM should start
chmod 700 ${SSL}
sh -xc "$JAVA $mp $pp Dummy" 2>&1
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# Test 2 - SSL config file is not secure - VM should fail to start
chmod o+rx ${SSL}
sh -xc "$JAVA $mp $pp Dummy" 2>&1
if [ $? = 0 ]; then failures=`expr $failures + 1`; fi

# Reset the file permissions on the generated SSL config file
chmod 777 ${SSL}

#
# Results
#
echo ''
if [ $failures -gt 0 ];
  then echo "$failures test(s) failed";
  else echo "All test(s) passed"; fi
exit $failures
