#
# Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

# WARNING: Make sure the OPENJDK plugs are up-to-date, see make/common/internal/BinaryPlugs.gmk

BUILDDIR = ../..
PACKAGE = javax.sound
LIBRARY = jsound
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

# include defines for sound
include SoundDefs.gmk

# support for different mixer provider files on different platforms
#MXSP = javax.sound.sampled.spi.MixerProvider
# Mixer Service Provider Additional path
#MXSPP_ADD =

#
# Add use of mapfile
#
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Files
#
include FILES_c.gmk

# add java files
AUTO_FILES_JAVA_DIRS = javax/sound com/sun/media/sound

#
# Files that just need cp.
#
SERVICEDIR = $(CLASSBINDIR)/META-INF/services

FILES_copy = \
	$(SERVICEDIR)/javax.sound.midi.spi.MidiDeviceProvider \
	$(SERVICEDIR)/javax.sound.midi.spi.MidiFileWriter \
	$(SERVICEDIR)/javax.sound.midi.spi.MidiFileReader \
	$(SERVICEDIR)/javax.sound.midi.spi.SoundbankReader \
	$(SERVICEDIR)/javax.sound.sampled.spi.AudioFileWriter \
	$(SERVICEDIR)/javax.sound.sampled.spi.AudioFileReader \
	$(SERVICEDIR)/javax.sound.sampled.spi.FormatConversionProvider \
	$(SERVICEDIR)/javax.sound.sampled.spi.MixerProvider \
	$(LIBDIR)/sound.properties

FILES_mkdirs = \
	$(CLASSBINDIR)/META-INF \
	$(CLASSBINDIR)/META-INF/services

FILES_copydirs = \
	$(CLASSBINDIR) \
	$(LIBDIR) \
	$(FILES_mkdirs)

FILES_c += $(FILES_$(PLATFORM))


#
# system dependent flags
#
ifeq ($(PLATFORM), windows)
  CPPFLAGS += 	-DUSE_PLATFORM_MIDI_OUT=TRUE \
		-DUSE_PLATFORM_MIDI_IN=TRUE \
		-DUSE_PORTS=TRUE
  LDLIBS += winmm.lib

  # Windows always provides MIDI and ports
  INCLUDE_MIDI  = TRUE
  INCLUDE_PORTS = TRUE

  # DirectSound handles directaudio (both i586 and amd64)
  SUBDIRS += jsoundds
  EXTRA_SOUND_JNI_LIBS += jsoundds
  #MXSPP_ADD = $(PLATFORM)-$(ARCH)/
endif # PLATFORM win32

ifeq ($(PLATFORM), linux)
  # ALSA handles directaudio, ports, and MIDI
  SUBDIRS += jsoundalsa
  EXTRA_SOUND_JNI_LIBS += jsoundalsa
  #MXSPP_ADD = $(PLATFORM)-$(ARCH)/
endif # PLATFORM linux

ifeq ($(PLATFORM), solaris)
  # build with ports and direct audio
  CPPFLAGS += -DUSE_PORTS=TRUE 	\
              -DUSE_DAUDIO=TRUE

  INCLUDE_PORTS = TRUE
  INCLUDE_DAUDIO = TRUE
  # build with empty MIDI i/o
  INCLUDE_MIDI = TRUE
endif # PLATFORM solaris

# for dynamic inclusion of extra sound libs: these
# JNI libs will be loaded from Platform.java
CPPFLAGS += -DEXTRA_SOUND_JNI_LIBS='"$(EXTRA_SOUND_JNI_LIBS)"'

# integrate MIDI i/o in jsound lib
ifeq ($(INCLUDE_MIDI),TRUE)
  FILES_c += $(MIDIFILES_c)
  FILES_export += $(MIDIFILES_export)
endif

# integrate port files in jsound lib
ifeq ($(INCLUDE_PORTS),TRUE)
  FILES_c += $(PORTFILES_c)
  FILES_export += $(PORTFILES_export)
endif

# integrate port files in jsound lib
ifeq ($(INCLUDE_DAUDIO),TRUE)
  FILES_c += $(DAUDIOFILES_c)
  FILES_export += $(DAUDIOFILES_export)
endif

#
# Extra cc/linker flags.
#
CPPFLAGS += \
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

#
# Rules that copy files.
#

build: copy-files
	$(SUBDIRS-loop)

copy-files: $(FILES_copy)

#$(SERVICEDIR)/$(MXSP): $(SHARE_SRC)/classes/com/sun/media/sound/services/$(MXSPP_ADD)$(MXSP)
#	$(install-file)

$(SERVICEDIR)/%: $(SHARE_SRC)/classes/com/sun/media/sound/services/%
	$(install-file)

$(LIBDIR)/audio/%: $(SHARE_SRC)/lib/audio/%
	$(install-file)

$(LIBDIR)/%: $(SHARE_SRC)/lib/%
	$(install-file)

#
# Since we build more than the javax.sound (as stated in the
# PACKAGE variable), we need to nuke the extra packages explicitly.
#
clean clobber::
	$(SUBDIRS-loop)

clean clobber::
	$(RM) -r $(CLASSBINDIR)/com/sun/media/sound $(LIBDIR)/audio
	$(RM) $(FILES_copy)

#
# Declare extra phony targets.
#
.PHONY: copy-files
