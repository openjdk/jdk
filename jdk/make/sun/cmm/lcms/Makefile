#
# Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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
MODULE  = java2d
PACKAGE = sun.java2d.cmm.lcms
LIBRARY = lcms
PRODUCT = sun

include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
ifeq ($(PLATFORM), windows)
include FILES_c_windows.gmk
else # PLATFORM
include FILES_c_unix.gmk
endif # PLATFORM

FILES_java =                                            \
	$(TAGDIR)sun/java2d/cmm/lcms/LCMS.java              \
	$(TAGDIR)sun/java2d/cmm/lcms/LCMSImageLayout.java   \
	$(TAGDIR)sun/java2d/cmm/lcms/LCMSTransform.java

FILES_export = sun/java2d/cmm/lcms/LCMS.java

#
# Rules
#
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

SERVICEDIR = $(CLASSBINDIR)/META-INF/services

FILES_copy = \
	$(SERVICEDIR)/sun.java2d.cmm.PCMM

build: copy-files

copy-files: $(FILES_copy)

$(SERVICEDIR)/%: $(SHARE_SRC)/classes/sun/java2d/cmm/lcms/META-INF/services/%
	$(install-file)

#
# Extra rules
#

ifeq ($(PLATFORM), solaris)
CFLAGS := $(subst -xc99=%none,-xc99=no_lib,$(CFLAGS))
endif

clean clobber:: 
	$(RM) $(FILES_copy)

vpath %.c   $(SHARE_SRC)/native/$(PKGDIR)
vpath %.c   $(SHARE_SRC)/native/sun/java2d

ifeq ($(PLATFORM), windows)
OTHER_CFLAGS += -DCMS_IS_WINDOWS_

ifeq ($(COMPILER_VERSION), VS2003)
OTHER_CFLAGS += -Dsqrtf=sqrt
endif

OTHER_LDLIBS = $(OBJDIR)/../../../sun.awt/awt/$(OBJDIRNAME)/awt.lib
OTHER_INCLUDES += -I$(SHARE_SRC)/native/sun/java2d \
                  -I$(SHARE_SRC)/native/sun/awt/debug

clean:: res.clean

res.clean:
	$(RM) $(OBJDIR)/cmm.rc
	$(RM) $(OBJDIR)/cmm.res
	$(RM) $(OBJDIR)/cmm.h

else
OTHER_LDLIBS = $(LIBM) -lawt -L$(LIBDIR)/$(LIBARCH)/xawt 
CPPFLAGS += -I$(SHARE_SRC)/native/sun/java2d \
            -I$(SHARE_SRC)/native/sun/awt/debug
endif

