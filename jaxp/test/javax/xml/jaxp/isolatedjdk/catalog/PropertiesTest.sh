#!/bin/sh

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

# @test
# @bug 8077931
# @summary This case tests if the properties FILES, DEFER, PREFER, RESOLVE in
#          jaxp.properties and system properties could be used.
# @key intermittent
# @library ../../libs/
# @build catalog.CatalogTestUtils
# @build PropertiesTest
# @run shell/timeout=600 ../IsolatedJDK.sh JAXP_PROPS
# @run shell/timeout=600 PropertiesTest.sh

echo "Copies properties.xml to class path"
TEST_CATALOG_PATH=${TESTCLASSES}/catalog/catalogFiles
echo "TEST_CATALOG_PATH=${TEST_CATALOG_PATH}"
mkdir -p ${TEST_CATALOG_PATH}
cp ${TESTSRC}/catalogFiles/properties.xml ${TEST_CATALOG_PATH}/properties.xml

# Execute test
ISOLATED_JDK=./ISOLATED_JDK_JAXP_PROPS
echo "Executes PropertiesTest"
${ISOLATED_JDK}/bin/java -Dtest.src="${TESTSRC}/.." ${TESTVMOPTS} -cp "${TESTCLASSPATH}" catalog.PropertiesTest
exitCode=$?

# Cleanup ISOLATED_JDK
rm -rf ${ISOLATED_JDK}

# Results
echo ''
if [ $exitCode -gt 0 ]; then
  echo "PropertiesTest failed";
else
  echo "PropertiesTest passed";
fi
exit $exitCode

