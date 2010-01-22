#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR = ../../..
MODULE   = font
PRODUCT  = java
include $(BUILDDIR)/common/Defs.gmk


#
# List of lucida font files that we redistribute.
#

SHARED_FONTFILES =					\
	$(LIBDIR)/fonts/LucidaTypewriterRegular.ttf	\
	$(LIBDIR)/fonts/LucidaTypewriterBold.ttf        \
	$(LIBDIR)/fonts/LucidaBrightRegular.ttf         \
	$(LIBDIR)/fonts/LucidaBrightDemiBold.ttf        \
	$(LIBDIR)/fonts/LucidaBrightItalic.ttf          \
	$(LIBDIR)/fonts/LucidaBrightDemiItalic.ttf	\
	$(LIBDIR)/fonts/LucidaSansRegular.ttf       	\
	$(LIBDIR)/fonts/LucidaSansDemiBold.ttf       	\

ifeq ($(PLATFORM), linux)

# The oblique versions of the font are derived from the base versions
# and since 2D can do this derivation on the fly at run time there is no
# need to redistribute the fonts. An exception to this is on Linux.
# The reason is that the Lucidas are specified in the font.properties files
# on Linux, and so AWT/Motif components expect to be able to ask the Xserver
# for these oblique fonts, but the Xserver cannot do the same derivation as
# 2D can.

OBLIQUE_FONTFILES =                                             \
	$(LIBDIR)/oblique-fonts/LucidaTypewriterOblique.ttf     \
	$(LIBDIR)/oblique-fonts/LucidaTypewriterBoldOblique.ttf \
	$(LIBDIR)/oblique-fonts/LucidaSansOblique.ttf       	\
	$(LIBDIR)/oblique-fonts/LucidaSansDemiOblique.ttf       \

endif

ifndef OPENJDK

# Lucida font files are not included in the OpenJDK distribution.
# Get names of font files

# Copy font files into OUTPUTDIR area

FONTFILES = $(SHARED_FONTFILES)
FONTSDIR  = $(LIBDIR)/fonts
FONTSDIRFILE  = $(FONTSDIR)/fonts.dir
INTERNAL_IMPORT_LIST = $(FONTFILES) 

ifneq ($(PLATFORM), windows)
  INTERNAL_IMPORT_LIST += $(FONTSDIRFILE)
endif

$(LIBDIR)/fonts/%.ttf: $(CLOSED_SRC)/share/lib/fonts/%.ttf
	$(install-file)

$(FONTSDIRFILE): $(PLATFORM_SRC)/classes/sun/awt/motif/java.fonts.dir
	$(install-file)

ifeq ($(PLATFORM), linux)

# The oblique fonts are only needed/wanted on Linux.

OBLFONTSDIR  = $(LIBDIR)/oblique-fonts
OBLFONTSDIRFILE	 = $(OBLFONTSDIR)/fonts.dir
INTERNAL_IMPORT_LIST += $(OBLIQUE_FONTFILES) $(OBLFONTSDIRFILE)

$(LIBDIR)/oblique-fonts/%.ttf: $(CLOSED_SRC)/share/lib/fonts/oblique/%.ttf
	$(install-file)

$(OBLFONTSDIRFILE): $(PLATFORM_SRC)/classes/sun/awt/motif/java.oblique-fonts.dir
	$(install-file)

endif # linux

all build : $(INTERNAL_IMPORT_LIST)

clean clobber::
	$(RM) $(INTERNAL_IMPORT_LIST)

else 

all build clean clobber :

endif # !OPENJDK
