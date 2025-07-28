#
# Copyright (c) 2025, Red Hat, Inc.
#
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
# @summary Ensures the java executable is able to load extra security
# properties files from anonymous pipes (non-regular files).
# @bug 8352728
# @requires os.family == "linux"
# @run shell/timeout=30 ConfigFileTestAnonymousPipes.sh

if [ -z "${TESTJAVA}" ]; then
    JAVA=java
else
    JAVA="${TESTJAVA}/bin/java"
fi

TEST_PROP="ConfigFileTestAnonymousPipes.property.name=PROPERTY_VALUE"

function check_java() {
    local java_output java_exit_code
    java_output="$("${JAVA}" ${TESTVMOPTS} ${TESTJAVAOPTS} -XshowSettings:security:properties \
                             -Djava.security.debug=properties "$@" -version 2>&1)"
    java_exit_code=$?
    if [ ${java_exit_code} -ne 0 ] || ! grep -qF "${TEST_PROP}" <<<"${java_output}"; then
        echo "TEST FAILED (java exit code: ${java_exit_code})"
        echo "${java_output}"
        exit 1
    fi
}

# https://www.gnu.org/software/bash/manual/bash.html#Pipelines
echo "Extra properties from pipeline"
echo "${TEST_PROP}" | check_java -Djava.security.properties=/dev/stdin || exit 1

# https://www.gnu.org/software/bash/manual/bash.html#Process-Substitution
echo "Extra properties from process-substitution, include other from pipeline"
echo "${TEST_PROP}" | check_java -Djava.security.properties=<(echo "include /dev/stdin") || exit 1

echo "TEST PASS - OK"
exit 0
