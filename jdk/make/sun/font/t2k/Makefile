#
# Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building t2k rasteriser.
#

BUILDDIR = ../../..
MODULE  = font
PACKAGE = sun.font
LIBRARY = t2k
PRODUCT = sun

# Indicate we want the C++ compiler to do the linking.
CPLUSPLUSLIBRARY=true

# Part of the sun.font package, but doesn't rebuild classes (except
# for a few ones with native methods) so shouldn't clobber them.
DONT_CLOBBER_CLASSES=true

# Use higher optimization level
OPTIMIZATION_LEVEL = HIGHER

include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES_c.gmk

FILES_export = \
    java/awt/Font.java \
    sun/font/FileFont.java \
    sun/font/FontManager.java \
    sun/font/GlyphList.java \
    sun/font/NativeFont.java \
    sun/font/StrikeCache.java \
    sun/font/T2KFontScaler.java 

ifeq ($(PLATFORM), windows)

  # t2k imports several shared methods from fontmanager.dll
  LDLIBS += user32.lib $(OBJDIR)/../../../sun.font/fontmanager/$(OBJDIRNAME)/fontmanager.lib

  OTHER_CFLAGS += -DCC_NOEX
  OTHER_CXXFLAGS += -DCC_NOEX

endif # PLATFORM

#
# In the OpenJDK mode we are not building anything
#
ifndef OPENJDK
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk
endif 

#
# Add to the ambient vpath to pick up files in subdirectories
#
vpath %.c   $(SHARE_SRC)/share/native/$(PKGDIR)
vpath %.c   $(PLATFORM_SRC)/native/$(PKGDIR)
vpath %.c   $(CLOSED_SRC)/share/native/$(PKGDIR)
vpath %.c   $(CLOSED_SRC)/share/native/$(PKGDIR)/t2k
vpath %.c   $(CLOSED_SRC)/share/native/$(PKGDIR)/t2k/ttHints

vpath %.cpp   $(SHARE_SRC)/native/$(PKGDIR)
vpath %.cpp   $(CLOSED_SRC)/share/native/$(PKGDIR)

# Libraries to link, and other C flags.
#
# setup the list of libraries to link in...
ifneq ($(PLATFORM), windows)
  OTHER_LDLIBS  +=  $(LIBM) $(LIBCXX) -lfontmanager
  ifeq ($(PLATFORM), linux)
    ifeq ("$(CC_VER_MAJOR)", "3")
      OTHER_LDLIBS  += -Wl,-Bstatic -lgcc_eh -Wl,-Bdynamic
    endif                           
  else
    ifeq ($(PLATFORM), solaris)
      OTHER_LDLIBS += -lawt -L$(LIBDIR)/$(LIBARCH)/xawt -lmawt
    endif
  endif
endif


# set up compile flags..

CPPFLAGS += -I$(SHARE_SRC)/native/$(PKGDIR) \
            -I$(CLOSED_SRC)/share/native/$(PKGDIR)/t2k \
            -I$(CLOSED_SRC)/share/native/$(PKGDIR) \
            -I$(SHARE_SRC)/share/native/$(PKGDIR) \
            -I$(PLATFORM_SRC)/native/$(PKGDIR) \
            -I$(SHARE_SRC)/native/sun/java2d/loops \
            -I$(SHARE_SRC)/native/sun/java2d/pipe \
            -I$(PLATFORM_SRC)/native/sun/java2d \
            -I$(SHARE_SRC)/native/sun/java2d \
            -I$(TEMPDIR)/../fontmanager/CClassHeaders
