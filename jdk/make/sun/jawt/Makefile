#
# Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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
MODULE  = awt
PACKAGE = sun.awt
LIBRARY = jawt
PRODUCT = sun

include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
ifeq ($(PLATFORM), windows)
FILES_cpp = jawt.cpp
else # PLATFORM
FILES_c = jawt.c
endif # PLATFORM

FILES_h = $(INCLUDEDIR)/jawt.h \
    $(PLATFORM_INCLUDE)/jawt_md.h

#
# Use mapfile
#
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk


ifeq ($(PLATFORM), windows)
#
# Extra C flags.
#

OTHER_CXXFLAGS += $(GX_OPTION) -DUNICODE -D_UNICODE

#
# Other extra flags needed for compiling.
#
CPPFLAGS += -I$(SHARE_SRC)/native/common \
	-I$(DXSDK_INCLUDE_PATH) \
	-I$(PLATFORM_SRC)/native/sun/windows \
	-I$(CLASSHDRDIR)/../../awt/CClassHeaders \
        -I$(SHARE_SRC)/native/sun/awt/debug \
        -I$(SHARE_SRC)/native/sun/font \
        -I$(SHARE_SRC)/native/sun/java2d \
        -I$(SHARE_SRC)/native/sun/java2d/loops \
        -I$(SHARE_SRC)/native/sun/java2d/pipe \
	-I$(PLATFORM_SRC)/native/sun/java2d \
	-I$(PLATFORM_SRC)/native/sun/java2d/windows \
	-I$(PLATFORM_SRC)/native/sun/java2d/d3d \
	-I$(SHARE_SRC)/native/$(PKGDIR)/image/cvutils \
	-I$(SHARE_SRC)/javavm/export \
	-I$(PLATFORM_SRC)/javavm/export
INSTALL_DOT_LIB = true

vpath %.cpp   $(PLATFORM_SRC)/native/sun/windows

#
# Libraries to link in.
#
ifeq ($(ARCH_DATA_MODEL), 64)
OTHER_LDLIBS = $(OBJDIR)/../../awt/$(OBJDIRNAME)/awt.lib 
else
OTHER_LDLIBS = kernel32.lib \
	       $(OBJDIR)/../../awt/$(OBJDIRNAME)/awt.lib 
endif
else # PLATFORM

#
# Other extra flags needed for compiling.
#
CPPFLAGS += -I$(OPENWIN_HOME)/include \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/debug \
            -I$(SHARE_SRC)/native/$(PKGDIR)/image \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/image/cvutils \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/alphacomposite \
            -I$(SHARE_SRC)/native/$(PKGDIR)/medialib \
            -I$(PLATFORM_SRC)/native/$(PKGDIR)/medialib \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../java2d/loops \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../java2d/pipe \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../java2d/opengl \
	    -I$(PLATFORM_SRC)/native/$(PKGDIR)/../java2d/opengl \
	    -I$(PLATFORM_SRC)/native/$(PKGDIR)/../java2d/x11 \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../dc/doe \
	    -I$(SHARE_SRC)/native/$(PKGDIR)/../dc/path \
            -I$(PLATFORM_SRC)/native/$(PKGDIR)/../jdga \
	    $(EVENT_MODEL)
#
# Libraries to link in.
#
ifeq ($(PLATFORM), solaris)
OTHER_LDLIBS = -L$(LIBDIR)/$(LIBARCH) -L$(OPENWIN_LIB) -L$(LIBDIR)/$(LIBARCH)/xawt -lmawt -L/usr/openwin/sfw/lib$(ISA_DIR) -lXrender
endif # PLATFORM

ifeq ($(PLATFORM), linux)
OTHER_LDLIBS = -L$(LIBDIR)/$(LIBARCH) -lawt -L$(LIBDIR)/$(LIBARCH)/xawt -lmawt
endif # PLATFORM

endif # PLATFORM


#
# Lets not remove any class files.
#
DONT_CLOBBER_CLASSES = true

#
# Rules
#
include $(BUILDDIR)/common/Library.gmk

build: $(FILES_h)

ifeq ($(PLATFORM), windows)
include make.depend
endif

#
# Install header files.
#
$(INCLUDEDIR)/%.h: $(SHARE_SRC)/javavm/export/%.h
	$(install-file)
$(PLATFORM_INCLUDE)/%.h: $(PLATFORM_SRC)/javavm/export/%.h
	$(install-file)


#
# Extra clean rule.
#
clean clobber::
	$(RM) $(FILES_h)

