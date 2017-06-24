#
# Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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

#

# This file identifies the root of the test-suite hierarchy.
# It also contains test-suite configuration information.

# The list of keywords supported in this test suite
keys=cte_test jcmd nmt regression gc stress

groups=TEST.groups [closed/TEST.groups]

# Source files for classes that will be used at the beginning of each test suite run,
# to determine additional characteristics of the system for use with the @requires tag.
# Note: compiled bootlibs code will be located in the folder 'bootClasses'
requires.extraPropDefns = ../../test/jtreg-ext/requires/VMProps.java [../../closed/test/jtreg-ext/requires/VMPropsExt.java]
requires.extraPropDefns.bootlibs = ../../test/lib/sun
requires.extraPropDefns.vmOpts = -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:bootClasses
requires.properties= \
    sun.arch.data.model \
    vm.simpleArch \
    vm.flightRecorder \
    vm.gc.G1 \
    vm.gc.Serial \
    vm.gc.Parallel \
    vm.gc.ConcMarkSweep \
    vm.jvmci \
    vm.emulatedClient \
    vm.cpu.features \
    vm.debug

# Tests using jtreg 4.2 b07 features
requiredVersion=4.2 b07

# Path to libraries in the topmost test directory. This is needed so @library
# does not need ../../ notation to reach them
external.lib.roots = ../../

# Use new module options
useNewOptions=true

# Use --patch-module instead of -Xmodule:
useNewPatchModule=true
