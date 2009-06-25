#
# Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#
# Makefile for building all of font tools
#

BUILDDIR = ../..
PACKAGE = sun.font
LIBRARY = fontmanager
PRODUCT = sun

# Indicate we want the C++ compiler to do the linking.
CPLUSPLUSLIBRARY=true

# Use higher optimization level
OPTIMIZATION_LEVEL = HIGHER

include $(BUILDDIR)/common/Defs.gmk

#
# Created without -z defs on linux
#
ifeq ($(PLATFORM), linux)
  LDFLAGS_DEFS_OPTION =
endif

#
# Files
#

include FILES_c.gmk

AUTO_FILES_JAVA_DIRS = sun/font
FILES_java += java/awt/Font.java java/text/Bidi.java 

FILES_export = \
    sun/font/SunLayoutEngine.java \
    java/awt/Font.java \
    java/text/Bidi.java \
    sun/font/FileFont.java \
    sun/font/FileFontStrike.java \
    sun/font/FontManager.java \
    sun/font/GlyphList.java \
    sun/font/NativeFont.java \
    sun/font/StrikeCache.java \
    sun/font/NullFontScaler.java \
    sun/font/FreetypeFontScaler.java \
    sun/java2d/loops/DrawGlyphList.java \
    sun/java2d/loops/DrawGlyphListAA.java \
    sun/java2d/loops/DrawGlyphListLCD.java \
    sun/java2d/loops/GraphicsPrimitive.java

ifeq ($(PLATFORM), windows)

  LDLIBS += user32.lib gdi32.lib $(OBJDIR)/../../../sun.awt/awt/$(OBJDIRNAME)/awt.lib  

  OTHER_CFLAGS += -DCC_NOEX
  OTHER_CXXFLAGS += -DCC_NOEX

else # PLATFORM unix

  FILES_export += \
    sun/font/NativeStrike.java \
    sun/font/NativeStrikeDisposer.java \
    sun/font/X11TextRenderer.java \
    sun/awt/X11GraphicsEnvironment.java 

endif # PLATFORM

#In the non-OpenJDK mode we need to build T2K
ifndef OPENJDK
t2k:
	$(ECHO) "lib=" $(ACTUAL_LIBRARY)
	$(MAKE) -C t2k

all build: t2k
t2k: library
clean clobber::
	$(MAKE) -C t2k clean
else

FILES_m=mapfile-vers.openjdk

#make sure freetype dll will be available at runtime as well as link time
#
#NB: Default freetype build system uses -h linker option and 
#   result .so contains hardcoded library name that is later 
#   used for adding dependencies to other objects 
#   (e.g. libfontmanager.so).
#   
#   It is not obvious how to extract that hardcoded name (libfreetype.so.6)
#   without overcomplicating logic here.
#   To workaround this we hardcode .6 suffix for now.
#
#   Note that .so.6 library will not be found by System.loadLibrary()
#        but fortunately we need to load FreeType library explicitly 
#        on windows only  
#
#TODO: rework this to avoid hardcoding library name in the makefile
#
library:: $(LIB_LOCATION)/$(LIB_PREFIX)freetype.$(LIBRARY_SUFFIX)

$(LIB_LOCATION)/$(LIB_PREFIX)freetype.$(LIBRARY_SUFFIX):
ifeq ($(PLATFORM), windows)
	$(CP) $(FREETYPE_LIB_PATH)/$(LIB_PREFIX)freetype.$(LIBRARY_SUFFIX) $@
else 
  ifeq ($(USING_SYSTEM_FT_LIB), false)
	$(CP) $(FREETYPE_LIB_PATH)/$(LIB_PREFIX)freetype.$(LIBRARY_SUFFIX) $@.6
  endif
endif 
endif

#ifeq ($(PLATFORM), solaris)
#  FILES_reorder += reorder-$(ARCH)
#endif
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

.PHONY: t2k build all clean clobber

#
# Add to the ambient vpath to pick up files in subdirectories
#
vpath %.c   $(PLATFORM_SRC)/native/$(PKGDIR)
vpath %.cpp   $(SHARE_SRC)/native/$(PKGDIR)/layout
vpath %.cpp   $(SHARE_SRC)/native/$(PKGDIR)

#
# Fontmanager is headless (independent of X/Motif)
#
CFLAGS       += -DHEADLESS
CXXFLAGS     += -DHEADLESS

ifndef OPENJDK
  CPPFLAGS += -I$(CLOSED_SRC)/share/native/$(PKGDIR)/t2k
else
  CPPFLAGS += -I$(FREETYPE_HEADERS_PATH) -I$(FREETYPE_HEADERS_PATH)/freetype2
  OTHER_LDLIBS += $(FREETYPE_LIB)
endif

ifeq ($(PLATFORM), windows)
vpath %.cpp   $(PLATFORM_SRC)/native/sun/windows
else # PLATFORM

#
# Libraries to link, and other C flags.
#

ifeq ($(PLATFORM), solaris)
  # Note that on Solaris, fontmanager is built against the headless library.
  LDFLAGS      += -L$(LIBDIR)/$(LIBARCH)/headless
  OTHER_LDLIBS += -lawt -L$(LIBDIR)/$(LIBARCH)/xawt -lmawt -lc $(LIBM) $(LIBCXX)
else # PLATFORM is linux
 OTHER_LDLIBS  += -lawt $(LIBM) $(LIBCXX)
  ifeq ("$(CC_VER_MAJOR)", "3")
    OTHER_LDLIBS  += -Wl,-Bstatic -lgcc_eh -Wl,-Bdynamic
  endif                           
endif

endif # PLATFORM

# set up compile flags..

CPPFLAGS += -I$(SHARE_SRC)/native/$(PKGDIR) \
            -I$(SHARE_SRC)/native/$(PKGDIR)/layout \
	    -I$(SHARE_SRC)/native/sun/awt/image/cvutils \
	    -I$(PLATFORM_SRC)/native/sun/awt \
	    -I$(SHARE_SRC)/native/sun/awt/debug \
	    -I$(SHARE_SRC)/native/sun/java2d/loops \
	    -I$(SHARE_SRC)/native/sun/java2d/pipe \
            -I$(PLATFORM_SRC)/native/sun/java2d \
	    -I$(SHARE_SRC)/native/sun/java2d \
	    -I$(TEMPDIR)/../../sun.awt/awt/CClassHeaders

ifeq ($(PLATFORM), windows)
CPPFLAGS += -I$(PLATFORM_SRC)/native/sun/windows
endif

