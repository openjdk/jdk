#
# Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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
# Create all the build tools
#

BUILDDIR = ..

SUBDIRS_MAKEFLAGS += JAVAC_MAX_WARNINGS=true JAVAC_WARNINGS_FATAL=true
include $(BUILDDIR)/common/Defs.gmk

# Note: freetypecheck is built by Sanity.gmk if needed
SUBDIRS =                   \
  addjsum                   \
  addtorestrictedpkgs       \
  buildmetaindex            \
  cldrconverter             \
  commentchecker            \
  compile_font_config       \
  compile_properties        \
  dir_diff                  \
  dtdbuilder                \
  generate_break_iterator   \
  GenerateCharacter         \
  generatecurrencydata      \
  hasher_classes            \
  jarreorder                \
  jarsplit                  \
  jdwpgen                   \
  makeclasslist             \
  strip_properties          \
  spp                       \
  tzdb                      \
  CharsetMapping

ifndef DISABLE_NIMBUS
  SUBDIRS += generate_nimbus
endif

ifeq ($(PLATFORM), solaris)
  # temporary tools to work around gobjcopy bugs
  SUBDIRS += add_gnu_debuglink fix_empty_sec_hdr_flags
endif

include $(BUILDDIR)/common/Subdirs.gmk

all build clean clobber::
	$(SUBDIRS-loop)

clean clobber::
	$(RM) -r $(BUILDTOOLCLASSDIR)
	$(RM) -r $(BUILDTOOLJARDIR)
	$(RM) -r $(BUILDTOOLBINDIR)

