#
# Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

# This file identifies the root of the test-suite hierarchy.
# It also contains test-suite configuration information.

# The list of keywords supported in this test suite
# randomness:           test uses randomness, test cases differ from run to run
keys=randomness

# Minimum jtreg version
requiredVersion=7.5.2+1

# Allow querying of various System properties in @requires clauses
requires.extraPropDefns = ../jtreg-ext/requires/VMProps.java
requires.extraPropDefns.bootlibs = ../lib/jdk/test/whitebox
requires.extraPropDefns.libs = \
    ../lib/jdk/test/lib/Platform.java \
    ../lib/jdk/test/lib/Container.java
requires.extraPropDefns.javacOpts = \
    --add-exports java.base/jdk.internal.foreign=ALL-UNNAMED \
    --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
requires.extraPropDefns.vmOpts = \
    -XX:+UnlockDiagnosticVMOptions \
    -XX:+WhiteBoxAPI \
    --add-exports java.base/jdk.internal.foreign=ALL-UNNAMED \
    --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
requires.properties= \
    jdk.static

# Path to libraries in the topmost test directory. This is needed so @library
# does not need ../../ notation to reach them
external.lib.roots = ../../

groups=TEST.groups
