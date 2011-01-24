#
# Copyright (c) 1998, 2010, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR  = ../../..
MODULE    = swing
PACKAGE   = javax.swing.plaf
PRODUCT   = com
SWING_SRC = $(SHARE_SRC)/classes/javax/swing
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES.gmk
AUTO_FILES_JAVA_DIRS = javax/swing/plaf sun/swing com/sun/java/swing/plaf

ifeq ($(PLATFORM), windows)
    # Don't build GTK L&F on Windows
    AUTO_JAVA_PRUNE = gtk
endif

ifdef DISABLE_NIMBUS
    AUTO_JAVA_PRUNE += nimbus
endif

MISC_FILES = $(MISC_SWING_FILES)
ifneq ($(PLATFORM), windows)
    # Only include GTK icons on Solaris/Linux
    MISC_FILES += $(MISC_SWING_FILES_UNIX)
endif

NIMBUS_GENSRC_DIR = $(GENSRCDIR)/javax/swing/plaf/nimbus
NIMBUS_SKIN_FILE = $(SWING_SRC)/plaf/nimbus/skin.laf
NIMBUS_GENERATOR_JAR = $(BUILDTOOLJARDIR)/generatenimbus.jar

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

$(NIMBUS_GENSRC_DIR): $(NIMBUS_SKIN_FILE) $(NIMBUS_GENERATOR_JAR)
	@$(ECHO) "Generating Nimbus source files:"
	$(RM) -r $(NIMBUS_GENSRC_DIR)
	$(BOOT_JAVA_CMD) -jar $(NIMBUS_GENERATOR_JAR) \
	    -skinFile $(NIMBUS_SKIN_FILE) -buildDir $(GENSRCDIR) \
	    -packagePrefix $(PACKAGE).nimbus -lafName Nimbus
	@$(ECHO) "Finished generating Nimbus source files"

clean:: classes.clean
	$(RM) -r $(NIMBUS_GENSRC_DIR)

#
# Include
#
ifndef DISABLE_NIMBUS
    CLASSES_INIT = $(NIMBUS_GENSRC_DIR)
endif

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

