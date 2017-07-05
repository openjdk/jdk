#
# Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
# @bug 6228231
# @summary Test that RMI registry uses SSL.
# @author Luis-Miguel Alventosa
# @run clean RmiRegistrySslTest
# @run build RmiRegistrySslTest
# @run shell/timeout=300 RmiRegistrySslTest.sh

echo -------------------------------------------------------------
echo `basename $0 .sh` : Non SSL RMIRegistry - Non SSL Lookup
echo -------------------------------------------------------------

${TESTJAVA}/bin/java ${TESTVMOPTS} -classpath ${TESTCLASSES} \
    -Dtest.src=${TESTSRC} \
    -DtestID=Test1 \
    -Dcom.sun.management.config.file=${TESTSRC}/rmiregistry.properties \
    RmiRegistrySslTest || exit $?

echo -------------------------------------------------------------
echo `basename $0 .sh` : SSL RMIRegistry - Non SSL Lookup
echo -------------------------------------------------------------

${TESTJAVA}/bin/java ${TESTVMOPTS} -classpath ${TESTCLASSES} \
    -Dtest.src=${TESTSRC} \
    -DtestID=Test2 \
    -Dcom.sun.management.config.file=${TESTSRC}/rmiregistryssl.properties \
    RmiRegistrySslTest || exit $?

echo -------------------------------------------------------------
echo `basename $0 .sh` : SSL RMIRegistry - SSL Lookup
echo -------------------------------------------------------------

${TESTJAVA}/bin/java ${TESTVMOPTS} -classpath ${TESTCLASSES} \
    -Dtest.src=${TESTSRC} \
    -DtestID=Test3 \
    -Djavax.net.ssl.keyStore=${TESTSRC}/ssl/keystore \
    -Djavax.net.ssl.keyStorePassword=password \
    -Djavax.net.ssl.trustStore=${TESTSRC}/ssl/truststore \
    -Djavax.net.ssl.trustStorePassword=trustword \
    -Dcom.sun.management.config.file=${TESTSRC}/rmiregistryssl.properties \
    RmiRegistrySslTest || exit $?
