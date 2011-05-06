#
# Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

# This file defines variables and macros which are used in the makefiles to 
# allow distributions to augment or replace common hotspot code with 
# distribution-specific source files. This capability is disabled when
# an OPENJDK build is requested, unless HS_ALT_SRC_REL has been set externally.

# Requires: GAMMADIR
# Provides:
#   variables: HS_COMMON_SRC, HS_ALT_SRC, HS_COMMON_SRC_REL, and HS_ALT_SRC_REL
#   functions: altsrc-equiv, if-has-altsrc, altsrc, altsrc-replace

HS_COMMON_SRC_REL=src

ifneq ($(OPENJDK),true)
  # This needs to be changed to a more generic location, but we keep it 
  # as this for now for compatibility
  HS_ALT_SRC_REL=src/closed
else
  HS_ALT_SRC_REL=NO_SUCH_PATH
endif

HS_COMMON_SRC=$(GAMMADIR)/$(HS_COMMON_SRC_REL)
HS_ALT_SRC=$(GAMMADIR)/$(HS_ALT_SRC_REL)

## altsrc-equiv 
# 
# Convert a common source path to an alternative source path
#
# Parameter: An absolute path into the common sources
# Result: The matching path to the alternate-source location
#
altsrc-equiv=$(subst $(HS_COMMON_SRC)/,$(HS_ALT_SRC)/,$(1))


## if-has-altsrc
#
# Conditional macro to test for the existence of an alternate source path
#
# Parameter: An absolute path into the common sources
# Parameter: Result if the alternative-source location exists
# Parameter: Result if the alternative-source location does not exist
# Result: expands to parameter 2 or 3 depending on existence of alternate source
#
if-has-altsrc=$(if $(wildcard $(call altsrc-equiv,$(1))),$(2),$(3))


## altsrc
#
# Converts common source path to alternate source path if the alternate 
# path exists, otherwise evaluates to nul (empty string)
# 
# Parameter: An absolute path into the common sources
# Result: The equivalent path to the alternate-source location, if such a 
#         location exists on the filesystem.  Otherwise it expands to empty.
# 
altsrc=$(call if-has-altsrc,$(1),$(call altsrc-equiv,$(1)))

## commonsrc
# 
# Returns parameter.
#
commonsrc=$(1)


## altsrc-replace
#
# Converts a common source path to an alternate source path if the alternate
# source path exists.  Otherwise it evaluates to the input common source path.
#
# Parameter: An absolute path into the common sources
# Result: A path to either the common or alternate sources
#
altsrc-replace=$(call if-has-altsrc,$(1),$(call altsrc-equiv,$(1)),$(1))
