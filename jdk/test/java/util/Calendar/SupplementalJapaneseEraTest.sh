#
# Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
# @bug 8048123
# @summary Test for jdk.calendar.japanese.supplemental.era support
# @build SupplementalJapaneseEraTest
# @run shell SupplementalJapaneseEraTest.sh

PROPERTY=jdk.calendar.japanese.supplemental.era
STATUS=0

# get the start time of the fictional next era
SINCE=`${TESTJAVA}/bin/java -cp "${TESTCLASSES}" SupplementalJapaneseEraTest -s`

echo "Tests with valid property values..."
for P in "name=NewEra,abbr=N.E.,since=$SINCE" \
         "name = NewEra, abbr = N.E., since = $SINCE"
do
    if ${TESTJAVA}/bin/java ${TESTVMOPTS} -cp "${TESTCLASSES}" \
           -D$PROPERTY="$P" SupplementalJapaneseEraTest -t; then
        echo "$P: passed"
    else
        echo "$P: failed"
        STATUS=1
    fi
done

# get the name of the current era to be used to confirm that
# invalid property values are ignored.
ERA=`${TESTJAVA}/bin/java -cp "${TESTCLASSES}" SupplementalJapaneseEraTest -e`

echo "Tests with invalid property values..."
for P in "foo=Bar,name=NewEra,abbr=N.E.,since=$SINCE" \
         "=NewEra,abbr=N.E.,since=$SINCE" \
         "=,abbr=N.E.,since=$SINCE" \
         "name,abbr=N.E.,since=$SINCE" \
         "abbr=N.E.,since=$SINCE" \
         "name=NewEra,since=$SINCE" \
         "name=,abbr=N.E.,since=$SINCE" \
         "name=NewEra,abbr=,since=$SINCE" \
         "name=NewEra,abbr=N.E." \
         "name=NewEra,abbr=N.E.,since=0" \
         "name=NewEra,abbr=N.E.,since=9223372036854775808" # Long.MAX_VALUE+1
do
    if ${TESTJAVA}/bin/java ${TESTVMOPTS} -cp "${TESTCLASSES}" \
           -D$PROPERTY="$P" SupplementalJapaneseEraTest -b "$ERA"; then
        echo "$P: passed"
    else
        echo "$P: failed"
        STATUS=1
    fi
done
exit $STATUS
