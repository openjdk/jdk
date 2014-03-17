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
# @bug 8000983
# @summary Unit test for narrow names support
# @build NarrowNamesTest
# @run shell NarrowNamesTest.sh

# This test is locale data-dependent and assumes that both JRE and CLDR
# have the same narrow names.

STATUS=0
for P in "JRE,SPI" "CLDR"
do
    echo "Locale providers: $P"
    if ! ${TESTJAVA}/bin/java -esa ${TESTVMOPTS} -cp "${TESTCLASSES}" -Djava.locale.providers="${P}" NarrowNamesTest; then
        STATUS=1
    fi
done
exit ${STATUS}
