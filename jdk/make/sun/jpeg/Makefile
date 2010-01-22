#
# Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
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
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

#
# Add to ambient vpath to get files in a subdirectory
#
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/image/jpeg

CLASSES.export += java.io.InputStream

