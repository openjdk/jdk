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
MODULE  = awt
PACKAGE = sun.lwawt
LIBRARY = lwawt
PRODUCT = sun

LIB_LOCATION = $(LIBDIR)/lwawt

include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES_c_macosx.gmk
include FILES_export_macosx.gmk
AUTO_FILES_JAVA_DIRS = sun/awt sun/font sun/lwawt sun/lwawt/macosx sun/java2d sun/java2d/opengl com/apple/eawt

#
# Rules.
#
# include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

#
# Add to the ambient vpath to pick up files in subdirectories
#
vpath %.c   $(SHARE_SRC)/native/sun/awt/debug
vpath %.c   $(SHARE_SRC)/native/sun/awt/image
vpath %.c   $(SHARE_SRC)/native/sun/awt/image/gif
vpath %.c   $(SHARE_SRC)/native/sun/awt/image/cvutils
vpath %.c   $(SHARE_SRC)/native/sun/awt/shell
vpath %.c   $(SHARE_SRC)/native/sun/java2d
vpath %.c   $(SHARE_SRC)/native/sun/java2d/loops
vpath %.c   $(SHARE_SRC)/native/sun/java2d/pipe
vpath %.c   $(SHARE_SRC)/native/sun/java2d/opengl
vpath %.c   $(SHARE_SRC)/native/sun/awt/medialib
vpath %.c   $(SHARE_SRC)/native/sun/awt/utility
vpath %.c   $(SHARE_SRC)/native/sun/font
vpath %.c   $(call NativeSrcDirList,,native/sun/awt)

vpath %.cpp $(SHARE_SRC)/native/sun/image

vpath %.m   $(call NativeSrcDirList,,native/sun/awt)
vpath %.m   $(call NativeSrcDirList,,native/sun/font)
vpath %.m   $(call NativeSrcDirList,,native/sun/java2d)
vpath %.m   $(call NativeSrcDirList,,native/sun/java2d/opengl)

OTHER_LDLIBS += \
	-lawt -lmlib_image -losxapp $(JVMLIB) $(LIBM) \
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
        -framework QuartzCore
CPPFLAGS += \
        -I$(CLASSHDRDIR) \
        -I$(SHARE_SRC)/native/sun/awt/debug \
        -I$(SHARE_SRC)/native/sun/awt/image/cvutils \
        -I$(SHARE_SRC)/native/sun/java2d \
	-I$(SHARE_SRC)/native/sun/java2d/loops \
	-I$(SHARE_SRC)/native/sun/awt/image/cvutils \
	-I$(SHARE_SRC)/native/sun/awt/image \
	-I$(SHARE_SRC)/native/sun/font \
	-I$(SHARE_SRC)/native/sun/java2d/pipe \
	-I$(SHARE_SRC)/native/sun/java2d/opengl \
	-I$(SHARE_SRC)/native/sun/dc/path \
	-I$(SHARE_SRC)/native/sun/dc/doe \
	-I$(SHARE_SRC)/native/sun/awt/alphacomposite \
        -I$(SHARE_SRC)/native/sun/awt/medialib \
        -I$(SHARE_SRC)/native/sun/awt \
        $(call NativeSrcDirList,-I,/native/sun/awt) \
        $(call NativeSrcDirList,-I,/native/sun/osxapp) \
        $(call NativeSrcDirList,-I,/native/sun/font) \
	$(call NativeSrcDirList,-I,/native/sun/java2d) \
	$(call NativeSrcDirList,-I,/native/sun/java2d/opengl)

ifeq ($(MILESTONE), internal)
  CPPFLAGS += -DINTERNAL_BUILD
endif

clean clobber::

.PHONY:
