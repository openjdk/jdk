#
# Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

ifeq ($(PLATFORM), macosx)
  CFLAGS += -DWITH_MACOSX

  # CFLAGS and CPPFLAGS are added when linking as well, so we use VARIANT
  # instead to specify that we're actually compiling objective-c code here
  CFLAGS_$(VARIANT)/java_awt_SplashScreen.o = -x objective-c
  CFLAGS_$(VARIANT)/splashscreen_gfx_impl.o = -x objective-c
  CFLAGS_$(VARIANT)/splashscreen_gif.o = -x objective-c
  CFLAGS_$(VARIANT)/splashscreen_impl.o = -x objective-c
  CFLAGS_$(VARIANT)/splashscreen_jpeg.o = -x objective-c
  CFLAGS_$(VARIANT)/splashscreen_png.o = -x objective-c
  CFLAGS_$(VARIANT)/splashscreen_sys.o = -x objective-c

  OTHER_CFLAGS += -F/System/Library/Frameworks/ApplicationServices.framework/Frameworks
  CPPFLAGS += -I/System/Library/Frameworks/AppKit.framework/Versions/C/Headers
  OTHER_LDLIBS += $(LIBM) -lpthread -liconv -losxapp \
				  -framework ApplicationServices \
				  -framework Foundation \
				  -framework Cocoa \
				  -framework JavaNativeFoundation
else ifneq ($(PLATFORM), windows)
  CFLAGS += -DWITH_X11
  ifeq ($(PLATFORM), macosx))
    OTHER_LDLIBS += -liconv
    CPPFLAGS += -I$(OPENWIN_HOME)/include \
                -I$(OPENWIN_HOME)/include/X11/extensions
    OTHER_LDLIBS += -L$(OPENWIN_LIB) -lX11 -lXext $(LIBM) -pthread
  else
    CPPFLAGS += -I$(OPENWIN_HOME)/include -I$(OPENWIN_HOME)/include/X11/extensions
    OTHER_LDLIBS += -L$(OPENWIN_LIB) -lX11 -lXext $(LIBM) -lpthread
  endif
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
ifneq ($(SYSTEM_ZLIB),true)
  vpath %.c   $(SHARE_SRC)/native/java/util/zip/zlib-$(ZLIB_VERSION)
endif
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/libpng
vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)/image/jpeg
ifneq ($(PLATFORM), macosx)
  vpath %.c   $(PLATFORM_SRC)/native/$(PKGDIR)/splashscreen
else
  vpath %.m   $(call NativeSrcDirList,,native/$(PKGDIR)/splashscreen)
endif

ifneq ($(PLATFORM), macosx)
  CPPFLAGS += -I$(PLATFORM_SRC)/native/$(PKGDIR)/splashscreen
else
  CPPFLAGS += $(call NativeSrcDirList,-I,native/$(PKGDIR)/splashscreen)
  CPPFLAGS += $(call NativeSrcDirList,-I,/native/sun/osxapp)
endif
CPPFLAGS += -I$(SHARE_SRC)/native/$(PKGDIR)/splashscreen
CPPFLAGS += -I$(SHARE_SRC)/native/$(PKGDIR)/image/jpeg
ifneq ($(SYSTEM_ZLIB),true)
  CPPFLAGS += -I$(SHARE_SRC)/native/java/util/zip/zlib-$(ZLIB_VERSION)
else
  OTHER_LDLIBS += -lz
endif

# Shun the less than portable MMX assembly code in pnggccrd.c,
# and use alternative implementations in C.
CPPFLAGS += -DPNG_NO_MMX_CODE
