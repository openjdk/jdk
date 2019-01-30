#
# Copyright (c) 2005, 2019, Oracle and/or its affiliates. All rights reserved.
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
keys=cte_test jcmd nmt regression gc stress metaspace headful intermittent

groups=TEST.groups TEST.quick-groups

# Source files for classes that will be used at the beginning of each test suite run,
# to determine additional characteristics of the system for use with the @requires tag.
# Note: compiled bootlibs code will be located in the folder 'bootClasses'
requires.extraPropDefns = ../../jtreg-ext/requires/VMProps.java
requires.extraPropDefns.bootlibs = ../../lib/sun ../../lib/jdk/test/lib/Platform.java
requires.extraPropDefns.vmOpts = -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:bootClasses
requires.properties= \
    sun.arch.data.model \
    vm.simpleArch \
    vm.bits \
    vm.flightRecorder \
    vm.gc.G1 \
    vm.gc.Serial \
    vm.gc.Parallel \
    vm.gc.ConcMarkSweep \
    vm.gc.Shenandoah \
    vm.gc.Epsilon \
    vm.gc.Z \
    vm.jvmci \
    vm.emulatedClient \
    vm.cpu.features \
    vm.debug \
    vm.hasSA \
    vm.hasSAandCanAttach \
    vm.hasJFR \
    vm.rtm.cpu \
    vm.rtm.compiler \
    vm.aot \
    vm.aot.enabled \
    vm.cds \
    vm.cds.custom.loaders \
    vm.cds.archived.java.heap \
    vm.graal.enabled \
    vm.compiler1.enabled \
    vm.compiler2.enabled \
    docker.support \
    test.vm.gc.nvdimm

# Minimum jtreg version
requiredVersion=4.2 b13

# Path to libraries in the topmost test directory. This is needed so @library
# does not need ../../../ notation to reach them
external.lib.roots = ../../../

# Use new module options
useNewOptions=true

# Use --patch-module instead of -Xmodule:
useNewPatchModule=true
