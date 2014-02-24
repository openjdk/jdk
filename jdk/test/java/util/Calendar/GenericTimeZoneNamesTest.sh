#
# Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8003267
# @summary Unit test for generic time zone names support
# @compile -XDignore.symbol.file GenericTimeZoneNamesTest.java
# @run shell GenericTimeZoneNamesTest.sh

# This test is locale data-dependent and assumes that both JRE and CLDR
# have the same geneic time zone names in English.

STATUS=0
echo "Locale providers: default"
if ! ${TESTJAVA}/bin/java -esa ${TESTVMOPTS} -cp "${TESTCLASSES}" GenericTimeZoneNamesTest en-US; then
    STATUS=1
fi

echo "Locale providers: CLDR"
if ! ${TESTJAVA}/bin/java -esa ${TESTVMOPTS} -cp "${TESTCLASSES}" -Djava.locale.providers=CLDR GenericTimeZoneNamesTest en-US; then
   STATUS=1
fi
exit ${STATUS}

