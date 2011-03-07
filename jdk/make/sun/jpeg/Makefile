#
# Copyright (c) 1995, 2010, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../..
MODULE  = java2d
PACKAGE = sun.awt
LIBRARY = jpeg
PRODUCT = sun

# Use highest optimization level
OPTIMIZATION_LEVEL = HIGHEST

include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES_c.gmk

AUTO_FILES_JAVA_DIRS = sun/awt/image com/sun/imageio/plugins/jpeg

FILES_export = \
	sun/awt/image/JPEGImageDecoder.java \
	com/sun/imageio/plugins/jpeg/JPEGImageReader.java \
	com/sun/imageio/plugins/jpeg/JPEGImageWriter.java

# OpenJDK doesn't use the non-standard jpeg classes which we hope to remove.
ifndef OPENJDK
FILES_m = mapfile-vers-closed
FILES_export += \
	sun/awt/image/codec/JPEGImageDecoderImpl.java \
	sun/awt/image/codec/JPEGImageEncoderImpl.java
vpath %.c   $(CLOSED_SRC)/share/native/$(PKGDIR)/image/jpeg
OTHER_INCLUDES += -I$(CLOSED_SRC)/share/native/$(PKGDIR)/image/jpeg
endif

OTHER_INCLUDES += -I$(SHARE_SRC)/native/$(PKGDIR)/image/jpeg

# Rules
#
ifeq ($(PLATFORM), solaris)
  ifneq ($(ARCH), amd64)
    FILES_reorder += reorder-$(ARCH)
  endif
endif

ifeq ($(PLATFORM), linux)

  # Suppress gcc warnings like "variable might be clobbered by 'longjmp'
  # or 'vfork'": this warning indicates that some variable is placed to
  # a register by optimized compiler and it's value might be lost on longjmp().
  # Recommended way to avoid such warning is to declare the variable as
  # volatile to prevent the optimization. However, this approach does not
  # work because we have to declare all variables as volatile in result.

  OTHER_CFLAGS += -Wno-clobbered
endif

include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

#
# Add to ambient vpath to get files in a subdirectory
#
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/image/jpeg

CLASSES.export += java.io.InputStream

