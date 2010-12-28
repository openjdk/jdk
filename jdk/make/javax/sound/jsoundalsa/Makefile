#
# Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../../..
MODULE  = sound
PACKAGE = javax.sound
LIBRARY = jsoundalsa
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

# this Makefile compiles the native files for alsa support in java sound

# include defines for sound
include ../SoundDefs.gmk

#
# Use mapfile
#
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Files
#

FILES_c = \
	Utilities.c \
	$(DAUDIOFILES_c) \
	$(MIDIFILES_c) \
	$(PORTFILES_c)

# platform dependent files
FILES_c += \
	PLATFORM_API_LinuxOS_ALSA_CommonUtils.c   \
	PLATFORM_API_LinuxOS_ALSA_PCM.c     \
	PLATFORM_API_LinuxOS_ALSA_PCMUtils.c   \
	PLATFORM_API_LinuxOS_ALSA_MidiIn.c  \
	PLATFORM_API_LinuxOS_ALSA_MidiOut.c \
	PLATFORM_API_LinuxOS_ALSA_MidiUtils.c \
	PLATFORM_API_LinuxOS_ALSA_Ports.c

FILES_export = \
	$(DAUDIOFILES_export) \
	$(MIDIFILES_export) \
	$(PORTFILES_export)

LDFLAGS += -lasound

CPPFLAGS += \
	-DUSE_DAUDIO=TRUE \
	-DUSE_PORTS=TRUE  \
	-DUSE_PLATFORM_MIDI_OUT=TRUE \
	-DUSE_PLATFORM_MIDI_IN=TRUE \
	-I$(SHARE_SRC)/native/com/sun/media/sound

#
# Add to the ambient VPATH.
#
vpath %.c $(SHARE_SRC)/native/com/sun/media/sound
vpath %.c $(PLATFORM_SRC)/native/com/sun/media/sound
vpath %.cpp $(PLATFORM_SRC)/native/com/sun/media/sound


#
# Include rules
#
include $(BUILDDIR)/common/Library.gmk



