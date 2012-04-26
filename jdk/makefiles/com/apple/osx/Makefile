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
PACKAGE = com.apple.osx
LIBRARY = osx
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk


#
# Files
#
AUTO_FILES_JAVA_DIRS = apple/launcher apple/security com/apple/concurrent com/apple/eio java/util/prefs

FILES_objc += \
    $(TARGDIR)Dispatch.m \
    $(TARGDIR)CFileManager.m \
    $(TARGDIR)KeystoreImpl.m \
    $(TARGDIR)JavaAppLauncher.m \
    $(TARGDIR)MacOSXPreferencesFile.m \
    $(TARGDIR)SCDynamicStoreConfig.m

FILES_export += \
	com/apple/concurrent/LibDispatchNative.java \
	com/apple/eio/FileManager.java \
	apple/security/KeychainStore.java \
	apple/launcher/JavaAppLauncher.java \
	java/util/prefs/MacOSXPreferencesFile.java

# TODO: couldn't figure out how to get resources working
#LOCALE_SET_DEFINITION = jre
#RESOURCE_BUNDLES_UNCOMPILED_PROPERTIES = apple/launcher/appLauncherErrors.properties


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
vpath %.m   $(call NativeSrcDirList,,native/com/apple/concurrent)
vpath %.m   $(call NativeSrcDirList,,native/com/apple/eio)
vpath %.m   $(call NativeSrcDirList,,native/apple/launcher)
vpath %.m   $(call NativeSrcDirList,,native/apple/security)
vpath %.m   $(call NativeSrcDirList,,native/java/util)

CPPFLAGS += \
	$(call NativeSrcDirList,-I,native/com/apple/laf) \
	$(call NativeSrcDirList,-I,native/apple/awt) \
	$(call NativeSrcDirList,-I,native/sun/awt) \
	$(call NativeSrcDirList,-I,native/sun/osxapp)

OTHER_LDLIBS = \
    -losxapp \
    -framework Cocoa \
    -framework ApplicationServices \
    -framework JavaNativeFoundation \
    -framework JavaRuntimeSupport \
    -framework Security \
    -framework SystemConfiguration
