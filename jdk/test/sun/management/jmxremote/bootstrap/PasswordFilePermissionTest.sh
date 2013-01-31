#!/bin/sh

#
# Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
# @bug 5008047
# @summary Check password file permission for out-of-the-box management
#
# @run shell PasswordFilePermissionTest.sh

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

createConfigFile() {
    cat << EOF > $1
# management.properties
com.sun.management.jmxremote.ssl=false
com.sun.management.jmxremote.password.file=$2
EOF
}

createPasswordFile() {
    if [ -f "$1" ] ; then
	rm -f $1 || echo WARNING: $1 already exists - unable to remove old copy
    fi
    cat << EOF > $1
# jmxremote.password
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


# Create configuration file and dummy password file

LIBDIR=${TESTCLASSES}/lib
CONFIG=${LIBDIR}/management.properties
PASSWD=${LIBDIR}/jmxremote.password
rm -f ${CONFIG}
rm -f ${PASSWD}
mkdir ${LIBDIR} 2>&1
createJavaFile ${TESTCLASSES} Null
createConfigFile ${CONFIG} ${PASSWD}
createPasswordFile ${PASSWD}

# Compile test 

${COMPILEJAVA}/bin/javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${TESTCLASSES} ${TESTCLASSES}/Null.java


JAVA=${TESTJAVA}/bin/java
CLASSPATH=${TESTCLASSES}
export CLASSPATH

failures=0

mp=-Dcom.sun.management.config.file=${CONFIG}
pp=-Dcom.sun.management.jmxremote.port=4888

go() {
    echo ''
    sh -xc "$JAVA ${TESTVMOPTS} $1 $2 $3 $4 $5 $6 $7 $8" 2>&1
    if [ $? != 0 ]; then failures=`expr $failures + 1`; fi
}

# Test 1 - password file is secure - VM should start
chmod 700 ${PASSWD}
sh -xc "$JAVA ${TESTVMOPTS} $mp $pp Null" 2>&1
if [ $? != 0 ]; then failures=`expr $failures + 1`; fi

# Test 2 - password file is not secure - VM should fail to start
chmod o+rx ${PASSWD}
sh -xc "$JAVA ${TESTVMOPTS} $mp $pp Null" 2>&1
if [ $? = 0 ]; then failures=`expr $failures + 1`; fi

# Reset the file permissions on the generated password file
chmod 777 ${PASSWD}

#
# Results
#
echo ''
if [ $failures -gt 0 ];
  then echo "$failures test(s) failed";
  else echo "All test(s) passed"; fi
exit $failures



