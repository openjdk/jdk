#
# Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
# @summary Test of JNDI factories using classes exported by third-party modules.

# Demonstrates Java object storage/retrieval, LDAP control and URL context
# usage using an LDAP directory. The objects and their associated object
# factories, state factories, control factories and URL context factories
# are exported from third-party modules.
#
# Seven types of object are used:
#   - an AWT object (Serializable) from the 'java.desktop' JDK module
#   - a Person object (DirContext) from the 'person' third-party module
#   - a Fruit object (Referenceable) from the 'fruit' third-party module
#   - an RMI object (Remote) from the 'hello' third-party module
#   - an LDAP request control (Control) from the 'foo' third-party module
#   - an LDAP response control (Control) from the 'authz' third-party module
#   - an ldapv4 URL (DirContext) from the 'ldapv4' third-party module
#

set -e

if [ -z "$TESTJAVA" ]; then
  if [ $# -lt 1 ]; then exit 1; fi
  TESTJAVA="$1"; shift
  COMPILEJAVA="${TESTJAVA}"
  TESTSRC="`pwd`"
  TESTCLASSES="`pwd`"
fi

JAVAC="$COMPILEJAVA/bin/javac"
JAVA="$TESTJAVA/bin/java"

echo "\nPreparing the 'person' module..."
mkdir -p mods/person
$JAVAC -d mods/person `find $TESTSRC/src/person -name "*.java"`

echo "\nPreparing the 'fruit' module..."
mkdir -p mods/fruit
$JAVAC -d mods/fruit `find $TESTSRC/src/fruit -name "*.java"`

echo "\nPreparing the 'hello' module..."
mkdir -p mods/hello
$JAVAC -d mods/hello `find $TESTSRC/src/hello -name "*.java"`

echo "\nPreparing the 'foo' module..."
mkdir -p mods/foo
$JAVAC -d mods/foo `find $TESTSRC/src/foo -name "*.java"`

echo "\nPreparing the 'authz' module..."
mkdir -p mods/authz
$JAVAC -d mods/authz `find $TESTSRC/src/authz -name "*.java"`

echo "\nPreparing the 'ldapv4' module..."
mkdir -p mods/ldapv4
$JAVAC -d mods/ldapv4 `find $TESTSRC/src/ldapv4 -name "*.java"`

echo "\nPreparing the 'test' module..."
mkdir -p mods/test
$JAVAC -d mods -modulesourcepath $TESTSRC/src `find $TESTSRC/src/test -name "*.java"`


echo "\nRunning with the 'java.desktop' module..."
$JAVA -Dtest.src=${TESTSRC} -mp mods -m test/test.StoreObject ldap://localhost/dc=ie,dc=oracle,dc=com

echo "\nRunning with the 'person' module..."
$JAVA -Dtest.src=${TESTSRC} -mp mods -m test/test.StorePerson ldap://localhost/dc=ie,dc=oracle,dc=com

echo "\nRunning with the 'fruit' module..."
$JAVA -Dtest.src=${TESTSRC} -mp mods -m test/test.StoreFruit ldap://localhost/dc=ie,dc=oracle,dc=com

echo "\nRunning with the 'hello' module..."
$JAVA -Dtest.src=${TESTSRC} -mp mods -m test/test.StoreRemote ldap://localhost/dc=ie,dc=oracle,dc=com

echo "\nRunning with the 'foo' module..."
$JAVA -Dtest.src=${TESTSRC} -mp mods -m test/test.ConnectWithFoo ldap://localhost/dc=ie,dc=oracle,dc=com

echo "\nRunning with the 'authz' module..."
$JAVA -Dtest.src=${TESTSRC} -mp mods -m test/test.ConnectWithAuthzId ldap://localhost/dc=ie,dc=oracle,dc=com

echo "\nRunning with the 'ldapv4' module..."
$JAVA -Dtest.src=${TESTSRC} -mp mods -m test/test.ReadByUrl ldap://localhost/dc=ie,dc=oracle,dc=com

