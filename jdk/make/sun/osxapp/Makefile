#
# Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
LIBRARY = osxapp
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

GEN_DIR=$(GENSRCDIR)/sun/osxapp
ICON_DATA = $(GEN_DIR)/AWTIconData.h

CLASSES_INIT += $(ICON_DATA)

#
# Files
#
FILES_objc = \
	NSApplicationAWT.m \
	QueuingApplicationDelegate.m \
	PropertiesUtilities.m \
	ThreadUtilities.m

#
# Rules.
#
include $(BUILDDIR)/common/Library.gmk

#
# Add to the ambient vpath to pick up files in subdirectories
#
vpath %.m   $(call NativeSrcDirList,,native/sun/osxapp)

# TODO: perhaps not all of the below frameworks are required
OTHER_LDLIBS += \
	-framework Accelerate \
	-framework ApplicationServices \
	-framework AudioToolbox \
	-framework Carbon \
	-framework Cocoa \
	-framework Security \
	-framework ExceptionHandling \
	-framework JavaNativeFoundation \
	-framework JavaRuntimeSupport \
	-framework OpenGL \
	-framework IOSurface \
	-framework QuartzCore

CPPFLAGS += \
        -I$(GEN_DIR) \
        $(call NativeSrcDirList,-I,/native/sun/osxapp)


ifeq ($(MILESTONE), internal)
  CPPFLAGS += -DINTERNAL_BUILD
endif

TEMPDIR_CLASSES = $(TEMPDIR)/classes

$(TEMPDIR_CLASSES)/sun/osxapp/ToBin.class: ToBin.java
	@$(prep-target)
	$(BOOT_JAVAC_CMD) -d $(TEMPDIR_CLASSES) $<

ifdef OPENJDK
    ICONS_PATH_PREFIX=$(PLATFORM_SRC_MACOS)
else
    ICONS_PATH_PREFIX=$(CLOSED_SRC)/macosx
endif

generated.clean:
	$(RM) -r $(GEN_DIR)/*.h

ICONPATH=$(ICONS_PATH_PREFIX)/native/sun/osxapp/resource/icons
ICON = $(ICONPATH)/JavaApp.icns

$(GEN_DIR)/AWTIconData.h: $(TEMPDIR_CLASSES)/sun/osxapp/ToBin.class $(ICON) 
	$(prep-target)
	$(RM) $(ICON_DATA)
	$(ECHO) "static unsigned char sAWTIconData[] = { " >> $(ICON_DATA);  \
	$(CAT) $(ICON) | \
	    $(BOOT_JAVA_CMD) -cp $(TEMPDIR_CLASSES) -Djava.awt.headless=true \
	    sun.osxapp.ToBin >> $(ICON_DATA); \
	$(ECHO) "};" >> $(ICON_DATA);


clean clobber:: generated.clean

.PHONY: generated.clean
