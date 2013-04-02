#
# Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
# On Solaris, the standard 'make' utility will not work with these makefiles.
#    This little rule is only understood by Solaris make, and is harmless
#    when seen by the GNU make tool. If using Solaris make, this causes the
#    make command to fail.
#
SUN_MAKE_TEST:sh = @echo "ERROR: PLEASE USE GNU VERSION OF MAKE"; exit 33


# Default target and expected 'do everything' target
all:
	echo Nashorn can only be built with NEWBUILD=true

# Standard make clobber target
clobber:

#-------------------------------------------------------------------
#
# Targets for Oracle's internal JPRT build system

CD = cd
ZIP = zip

JPRT_ARCHIVE_BUNDLE=$(ABS_OUTPUTDIR)/$(JPRT_BUILD_FLAVOR)-bundle.zip

jprt_build_product jprt_build_debug jprt_build_fastdebug: all
	( $(CD) $(OUTPUTDIR) && \
	  $(ZIP) -q -r $(JPRT_ARCHIVE_BUNDLE) build dist )

#-------------------------------------------------------------------

# Declare these phony (not filenames)
.PHONY: $(ANT_TARGETS) all clobber \
	jprt_build_product jprt_build_debug jprt_build_fastdebug
