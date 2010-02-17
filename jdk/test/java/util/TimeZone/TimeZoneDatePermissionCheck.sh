# Testcase for PR381 Stackoverflow error with security manager, signed jars
# and -Djava.security.debug set.
#
# Copyright (c) 2009, Red Hat Inc.
#
# This code is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2, or (at your option)
# any later version.
# 
# This code is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# General Public License for more details.
# 
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# @test
# @bug 6584033
# @summary Stackoverflow error with security manager, signed jars and debug.
# @build TimeZoneDatePermissionCheck
# @run shell TimeZoneDatePermissionCheck.sh

# Set default if not run under jtreg from test dir itself
if [ "${TESTCLASSES}" = "" ] ; then
  TESTCLASSES="."
fi
if [ "${TESTJAVA}" = "" ] ; then
  TESTJAVA=/usr
fi

# create a test keystore and dummy cert
rm -f ${TESTCLASSES}/timezonedatetest.store
${TESTJAVA}/bin/keytool -genkeypair -alias testcert \
  -keystore ${TESTCLASSES}/timezonedatetest.store \
  -storepass testpass -validity 360 \
  -dname "cn=Mark Wildebeest, ou=FreeSoft, o=Red Hat, c=NL" \
  -keypass testpass

# create a jar file to sign with the test class in it.
rm -f ${TESTCLASSES}/timezonedatetest.jar
${TESTJAVA}/bin/jar cf \
  ${TESTCLASSES}/timezonedatetest.jar \
  -C ${TESTCLASSES} TimeZoneDatePermissionCheck.class

# sign it
${TESTJAVA}/bin/jarsigner \
  -keystore ${TESTCLASSES}/timezonedatetest.store \
  -storepass testpass ${TESTCLASSES}/timezonedatetest.jar testcert

# run it with the security manager on, plus accesscontroller debugging
# will go into infinite recursion trying to get enough permissions for
# printing Date of failing certificate unless fix is applied.
${TESTJAVA}/bin/java -Djava.security.manager \
  -Djava.security.debug=access,failure,policy \
  -cp ${TESTCLASSES}/timezonedatetest.jar TimeZoneDatePermissionCheck
