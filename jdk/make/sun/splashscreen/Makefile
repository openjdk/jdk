#
# Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
MODULE  = awt
PACKAGE = sun.awt
LIBRARY = splashscreen
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES_c.gmk

FILES_java = \
    java/awt/SplashScreen.java

FILES_export = \
    java/awt/SplashScreen.java


#
# Rules
#
# FIXME!!!!
#ifeq ($(PLATFORM), solaris)
#  ifneq ($(ARCH), amd64)
#    FILES_reorder += reorder-$(ARCH)
#  endif
#endif
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

JAVALIB=

#
# C Flags
#

CFLAGS += -DSPLASHSCREEN

ifneq ($(PLATFORM), windows)
  CFLAGS += -DWITH_X11
  CPPFLAGS += -I$(OPENWIN_HOME)/include -I$(OPENWIN_HOME)/include/X11/extensions
  OTHER_LDLIBS += -L$(OPENWIN_LIB) -lX11 -lXext $(LIBM) -lpthread
else # PLATFORM
  CFLAGS += -DWITH_WIN32
  OTHER_LDLIBS += kernel32.lib user32.lib gdi32.lib delayimp.lib /DELAYLOAD:user32.dll
#$(JVMLIB) $(OBJDIR)/../../jpeg/$(OBJDIRNAME)/jpeg$(SUFFIX).lib
endif # PLATFORM

#
# Add to ambient vpath to get files in a subdirectory
#
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/splashscreen
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/giflib
vpath %.c   $(SHARE_SRC)/native/java/util/zip/zlib-$(ZLIB_VERSION)
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/libpng
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/image/jpeg
vpath %.c   $(PLATFORM_SRC)/native/$(PKGDIR)/splashscreen

CPPFLAGS += -I$(PLATFORM_SRC)/native/$(PKGDIR)/splashscreen -I$(SHARE_SRC)/native/$(PKGDIR)/splashscreen
CPPFLAGS += -I$(SHARE_SRC)/native/$(PKGDIR)/image/jpeg -I$(SHARE_SRC)/native/java/util/zip/zlib-$(ZLIB_VERSION)

# Shun the less than portable MMX assembly code in pnggccrd.c,
# and use alternative implementations in C.
CPPFLAGS += -DPNG_NO_MMX_CODE
