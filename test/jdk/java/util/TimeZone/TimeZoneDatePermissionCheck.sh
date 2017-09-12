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
if [ "${COMPILEJAVA}" = "" ]; then
  COMPILEJAVA="${TESTJAVA}"
fi

# create a test keystore and dummy cert. Note that we use the COMPILEJAVA
# as this test is a TimeZone test, it doesn't test keytool
rm -f ${TESTCLASSES}/timezonedatetest.store
${COMPILEJAVA}/bin/keytool ${TESTTOOLVMOPTS} -genkeypair -alias testcert \
  -keystore ${TESTCLASSES}/timezonedatetest.store \
  -storepass testpass -validity 360 \
  -keyalg rsa \
  -dname "cn=Mark Wildebeest, ou=FreeSoft, o=Red Hat, c=NL" \
  -keypass testpass

# create a jar file to sign with the test class in it.
rm -f ${TESTCLASSES}/timezonedatetest.jar
${COMPILEJAVA}/bin/jar ${TESTTOOLVMOPTS} cf \
  ${TESTCLASSES}/timezonedatetest.jar \
  -C ${TESTCLASSES} TimeZoneDatePermissionCheck.class

# sign it
${COMPILEJAVA}/bin/jarsigner ${TESTTOOLVMOPTS} \
  -keystore ${TESTCLASSES}/timezonedatetest.store \
  -storepass testpass ${TESTCLASSES}/timezonedatetest.jar testcert

# run it with the security manager on, plus accesscontroller debugging
# will go into infinite recursion trying to get enough permissions for
# printing Date of failing certificate unless fix is applied.
${TESTJAVA}/bin/java ${TESTVMOPTS} -Djava.security.manager \
  -Djava.security.debug=access,failure,policy \
  -cp ${TESTCLASSES}/timezonedatetest.jar TimeZoneDatePermissionCheck
