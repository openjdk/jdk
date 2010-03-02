#
# Copyright 1995-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR = ../..
MODULE   = base

include $(BUILDDIR)/common/Defs.gmk

#
# Make the directories of include files shipped with the JDK.
#

FILES_h = $(INCLUDEDIR)/jni.h 			\
	  $(PLATFORM_INCLUDE)/jni_md.h		\
	  $(INCLUDEDIR)/jvmti.h			\
	  $(INCLUDEDIR)/jvmticmlr.h		\
	  $(INCLUDEDIR)/classfile_constants.h

$(INCLUDEDIR)/%.h: $(SHARE_SRC)/javavm/export/%.h
	$(install-non-module-file)

$(PLATFORM_INCLUDE)/%.h: $(PLATFORM_SRC)/javavm/export/%.h
	$(install-non-module-file)

JVMCFG = $(LIBDIR)/$(LIBARCH)/jvm.cfg

#
# How to install jvm.cfg.
#
ifeq ($(ZERO_BUILD), true)
JVMCFG_ARCH = zero
else
JVMCFG_ARCH = $(ARCH)
endif

$(JVMCFG): $(PLATFORM_SRC)/bin/$(JVMCFG_ARCH)/jvm.cfg
	$(install-file)

all: build

build: $(FILES_h) $(JVMCFG)

clean clobber:: 
	$(RM) $(FILES_h) $(JVMCFG)

