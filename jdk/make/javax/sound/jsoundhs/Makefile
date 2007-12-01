#
# Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
PACKAGE = javax.sound
LIBRARY = jsoundhs
PRODUCT = sun
CPLUSPLUSLIBRARY = true
include $(BUILDDIR)/common/Defs.gmk

# this Makefile compiles "closed" JavaSound library

ifdef OPENJDK

# precompiled lib will be copied by the rules in Library.gmk instead of compiling.
USE_BINARY_PLUG_LIBRARY=true

build: import-binary-plug-jsound-library

include $(BUILDDIR)/common/internal/BinaryPlugs.gmk

else # OPENJDK

# include defines for sound
include ../SoundDefs.gmk

#
# Add use of mapfile
#
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Files
#
include FILES.gmk

FILES_c += $(FILES_engine) $(FILES_$(PLATFORM))

#
# Extra cc/linker flags.
#
# flags needed for all platforms
CPPFLAGS += \
	-DJAVA_SOUND -DJAVA_THREAD \
	-I$(CLOSED_SHARE_SRC)/native/com/sun/media/sound \
	-I$(CLOSED_SHARE_SRC)/native/com/sun/media/sound/engine

# system dependent flags
ifeq ($(PLATFORM), windows)
  CPPFLAGS += 	-DUSE_DIRECTSOUND=0 \
		-DUSE_EXTERNAL_SYNTH=TRUE
  LDLIBS += winmm.lib
endif # PLATFORM windows

ifeq ($(PLATFORM), linux)
endif # PLATFORM linux

ifeq ($(PLATFORM), solaris)
endif # PLATFORM solaris


#
# Add to the ambient VPATH.
#
vpath %.c $(CLOSED_SHARE_SRC)/native/com/sun/media/sound
vpath %.c $(CLOSED_SHARE_SRC)/native/com/sun/media/sound/engine
vpath %.c $(CLOSED_PLATFORM_SRC)/native/com/sun/media/sound/engine


endif # OPENJDK


#
# Include rules
#
include $(BUILDDIR)/common/Library.gmk

