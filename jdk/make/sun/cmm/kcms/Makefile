#
# Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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
PACKAGE = sun.java2d.cmm.kcms
LIBRARY = kcms
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

FILES_java = \
    sun/java2d/cmm/kcms/CMM.java

FILES_export = \
    sun/java2d/cmm/kcms/CMM.java 

ifeq ($(PLATFORM), windows)
  # Override the default version info with our own resource file (see 5043594)
  VERSIONINFO_RESOURCE = $(CLOSED_SRC)/share/native/sun/java2d/cmm/kcms/cmm.rc
endif

# Rules
#
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

SERVICEDIR = $(CLASSBINDIR)/META-INF/services

FILES_copy = \
	$(SERVICEDIR)/sun.java2d.cmm.PCMM


build: copy-files

copy-files: $(FILES_copy)

$(SERVICEDIR)/%: $(CLOSED_SRC)/share/classes/sun/java2d/cmm/kcms/META-INF/services/%
	$(install-file)

#
# Extra rules
#
ifeq  ($(PLATFORM), linux)
LDLIBS += -lpthread
endif

clean clobber:: 
	$(RM) $(FILES_copy)

#
# Add to ambient vpath to get files in a subdirectory
#
vpath %.c   $(CLOSED_SRC)/share/native/$(PKGDIR)

ifeq ($(PLATFORM), windows)

OTHER_LDLIBS = user32.lib version.lib

clean:: res.clean

res.clean:
	$(RM) $(OBJDIR)/cmm.rc
	$(RM) $(OBJDIR)/cmm.res
	$(RM) $(OBJDIR)/cmm.h

else # PLATFORM
OTHER_LDLIBS = $(LIBM)

# Use pic instead of PIC on this library
GLOBAL_KPIC = $(PIC_CODE_SMALL)

CPPFLAGS += -I$(CLASSHDRDIR) \

endif # PLATFORM

#CFLAGS += -DJAVACMM -DFUT_CALC_EX -DNO_FUT_GCONST
CFLAGS += -DFUT_CALC_EX -DNO_FUT_GCONST

