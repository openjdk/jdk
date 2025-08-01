#
# Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

# The list of keywords supported in the entire test suite.  The
# "intermittent" keyword marks tests known to fail intermittently.
# The "randomness" keyword marks tests using randomness with test
# cases differing from run to run. (A test using a fixed random seed
# would not count as "randomness" by this definition.) Extra care
# should be taken to handle test failures of intermittent or
# randomness tests.

# Group definitions
groups=TEST.groups

# Minimum jtreg version
requiredVersion=7.5.2+1

# Use new module options
useNewOptions=true

# Use --patch-module instead of -Xmodule:
useNewPatchModule=true

# Path to libraries in the topmost test directory. This is needed so @library
# does not need ../../ notation to reach them
external.lib.roots = ../../
