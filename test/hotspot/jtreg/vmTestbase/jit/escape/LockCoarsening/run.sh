#!/bin/sh
#
# Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

EXECUTE_CLASS=jit.escape.LockCoarsening.LockCoarsening
JAVA="$TESTJAVA/bin/java"
JAVA_OPTS="-cp $TESTCLASSPATH $TESTJAVAOPTS $TESTVMOPTS"

#
# Run the test in EA mode if -server and -Xcomp JVM options specified
# Set EliminateLocks commandline option to enable -XX:+EliminateLocks
#

if [ "$1" = "EliminateLocks" ]; then
    EA_OPTS="-XX:+DoEscapeAnalysis -XX:+EliminateLocks"
    TEST_ARGS="$TEST_ARGS -eliminateLocks"
else
    EA_OPTS="-XX:-DoEscapeAnalysis -XX:-EliminateLocks"
fi

# Additional VM options
ADD_OPTS="-XX:-TieredCompilation -XX:+IgnoreUnrecognizedVMOptions -XX:-DeoptimizeALot"

echo "Check if EscapeAnalysis is supported"
$JAVA $JAVA_OPTS $EA_OPTS -version

if [ "$?" = 0 ]; then
        echo "EA options '$EA_OPTS' are supported"

        b1=0
        b2=0

        for param in $JAVA_OPTS; do
                case "$param" in
                        -server )
                                b1=1
                                ;;
                        -Xcomp )
                                b2=1
                                ;;
                esac
        done

        if [ "$b1$b2" = 11 ]; then
                JAVA_OPTS="$JAVA_OPTS $ADD_OPTS $EA_OPTS"
                echo "Java options: $JAVA_OPTS"

                $JAVA $JAVA_OPTS $EXECUTE_CLASS $TEST_ARGS

                exit $?
        else
                echo "JVM options '-server -Xcomp' not specified"

                exit 0
        fi
fi

echo "EA not supported, passing test"

exit 0
