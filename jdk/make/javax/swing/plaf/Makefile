#
# Copyright 1998-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR  = ../../..
PACKAGE   = javax.swing.plaf
PRODUCT   = com
SWING_SRC = $(SHARE_SRC)/classes/javax/swing
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES.gmk
AUTO_FILES_JAVA_DIRS = javax/swing/plaf sun/swing com/sun/java/swing/plaf
ifndef DISABLE_NIMBUS
  SUBDIRS = nimbus
endif

# Nimbus is handled in its own directory
AUTO_JAVA_PRUNE = nimbus

ifeq ($(PLATFORM), windows)
    # Don't build GTK L&F on Windows
    AUTO_JAVA_PRUNE += gtk
endif

MISC_FILES = $(MISC_SWING_FILES)
ifneq ($(PLATFORM), windows)
    # Only include GTK icons on Solaris/Linux
    MISC_FILES += $(MISC_SWING_FILES_UNIX)
endif

FILES_java = $(FILES_SWING_java)

#
# Resources
#
LOCALE_SET_DEFINITION = jre
RESOURCE_BUNDLES_COMPILED_PROPERTIES = \
	com/sun/swing/internal/plaf/basic/resources/basic.properties \
	com/sun/swing/internal/plaf/metal/resources/metal.properties \
	com/sun/swing/internal/plaf/synth/resources/synth.properties \
	com/sun/java/swing/plaf/motif/resources/motif.properties \
        com/sun/java/swing/plaf/windows/resources/windows.properties
ifneq ($(PLATFORM), windows)
    # Only compile GTK resource bundles on Solaris/Linux
    RESOURCE_BUNDLES_COMPILED_PROPERTIES += \
        com/sun/java/swing/plaf/gtk/resources/gtk.properties
endif

#
# Rules.
# Process LOGO_ICONS and Motif Icons first.
#
build: $(LOGO_ICONS) $(MISC_SWING_FILES_MOTIF_GIF) $(MISC_SWING_FILES_MOTIF_PNG) other_files
	$(SUBDIRS-loop)

clean clobber::
	$(SUBDIRS-loop)

#
# Include
#
include $(BUILDDIR)/common/Classes.gmk

ifndef OPENJDK
$(LOGO_ICONS): $(CLASSBINDIR)/%.png: $(CLOSED_SRC)/share/classes/%.png
	$(install-file)

$(MISC_SWING_FILES_MOTIF_GIF): $(CLASSBINDIR)/%.gif: $(CLOSED_SRC)/share/classes/%.gif
	$(install-file)

$(MISC_SWING_FILES_MOTIF_PNG): $(CLASSBINDIR)/%.png: $(CLOSED_SRC)/share/classes/%.png
	$(install-file)
endif	

other_files: $(MISC_FILES)

$(CLASSBINDIR)/%.wav: $(SHARE_SRC)/classes/%.wav
	$(install-file)

$(CLASSBINDIR)/%.gif: $(SHARE_SRC)/classes/%.gif
	$(install-file)

$(CLASSBINDIR)/%.png: $(SHARE_SRC)/classes/%.png
	$(install-file)

$(CLASSBINDIR)/%.xml: $(SHARE_SRC)/classes/%.xml
	$(install-file)

$(CLASSBINDIR)/%.css: $(SHARE_SRC)/classes/%.css
	$(install-file)

$(CLASSBINDIR)/%.bdtd: $(SHARE_SRC)/classes/%.bdtd
	$(install-file)

$(CLASSBINDIR)/%.txt: $(SHARE_SRC)/classes/%.txt
	$(install-file)

