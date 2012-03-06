#
# Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
PACKAGE = com.apple.osxui
LIBRARY = osxui
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk


#
# Files
#
AUTO_FILES_JAVA_DIRS = apple/laf com/apple/laf com/apple/eawt

FILES_objc = \
        $(TARGDIR)AquaFileView.m \
        $(TARGDIR)AquaLookAndFeel.m \
        $(TARGDIR)AquaNativeResources.m \
        $(TARGDIR)JRSUIConstantSync.m \
        $(TARGDIR)JRSUIController.m \
        $(TARGDIR)JRSUIFocus.m \
        $(TARGDIR)ScreenPopupFactory.m \
        $(TARGDIR)ScreenMenu.m

FILES_export = \
	apple/laf/AquaLookAndFeel.java \
	apple/laf/JRSUIConstants.java \
	apple/laf/JRSUIControl.java \
	apple/laf/JRSUIFocus.java \
	apple/laf/JRSUIState.java \
	apple/laf/JRSUIStateFactory.java \
	apple/laf/JRSUIUtils.java \
	com/apple/laf/AquaFileView.java \
	com/apple/laf/AquaNativeResources.java \
	com/apple/laf/ScreenPopupFactory.java \
	com/apple/laf/ScreenMenu.java \
	com/apple/laf/ScreenMenuBar.java \
	com/apple/laf/ScreenMenuBarProvider.java \
	com/apple/laf/ScreenMenuItem.java \
	com/apple/laf/ScreenMenuItemCheckbox.java \
	com/apple/laf/ScreenMenuItemUI.java \
	com/apple/laf/ScreenMenuPropertyHandler.java \
	com/apple/laf/ScreenMenuPropertyListener.java

#RESOURCE_BUNDLES_COMPILED_PROPERTIES += \
#	com/apple/laf/resources/aqua.properties \
#	com/apple/laf/resources/aqua_de.properties \
#	com/apple/laf/resources/aqua_fr.properties \
#	com/apple/laf/resources/aqua_ja.properties


#
# Rules
#
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk


#
# Extra rules
#

#
# Add to ambient vpath to get files in a subdirectory
#
vpath %.m   $(call NativeSrcDirList,,native/com/apple/laf)
vpath %.m $(call NativeSrcDirList,,native/com/apple/eawt)

CPPFLAGS += \
	$(call NativeSrcDirList,-I,native/com/apple/laf) \
	$(call NativeSrcDirList,-I,native/apple/awt) \
	$(call NativeSrcDirList,-I,native/sun/awt) \
	$(call NativeSrcDirList,-I,native/sun/osxapp) \
	-I$(TEMPDIR)/../../sun.awt/awt/CClassHeaders

OTHER_LDLIBS = \
    -lawt -losxapp \
    -llwawt -L$(LIBDIR)/lwawt -Xlinker -rpath -Xlinker @loader_path/lwawt \
    -framework Cocoa \
    -framework Carbon \
    -framework ApplicationServices \
    -framework JavaNativeFoundation \
    -framework JavaRuntimeSupport
