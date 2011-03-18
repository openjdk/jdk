#
# Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
PACKAGE = sun.dc
LIBRARY = dcpr
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES_c.gmk

FILES_java = \
 sun/dc/DuctusRenderingEngine.java \
 sun/dc/path/FastPathProducer.java \
 sun/dc/path/PathConsumer.java \
 sun/dc/path/PathError.java \
 sun/dc/path/PathException.java \
 sun/dc/pr/PathDasher.java \
 sun/dc/pr/PathFiller.java \
 sun/dc/pr/PathStroker.java \
 sun/dc/pr/PRError.java \
 sun/dc/pr/PRException.java \
 sun/dc/pr/Rasterizer.java

FILES_export = \
	sun/dc/pr/PathDasher.java \
	sun/dc/pr/PathFiller.java \
	sun/dc/pr/PathStroker.java

#
# Rules.
#
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

SERVICEDIR = $(CLASSBINDIR)/META-INF/services

FILES_copy = \
	$(SERVICEDIR)/sun.java2d.pipe.RenderingEngine

build: copy-files

copy-files: $(FILES_copy)

$(SERVICEDIR)/%: $(SHARE_SRC)/classes/sun/dc/META-INF/services/%
	$(install-file)

#
# Add to the ambient vpath to pick up files in subdirectories
#
vpath %.c   $(CLOSED_SRC)/share/native/$(PKGDIR)/doe
vpath %.c   $(CLOSED_SRC)/share/native/$(PKGDIR)/path
vpath %.c   $(CLOSED_SRC)/share/native/$(PKGDIR)/pr
vpath %.c   $(CLOSED_SRC)/share/native/$(PKGDIR)/util
vpath %.c   $(SHARE_SRC)/native/sun/java2d/pipe

ifneq ($(PLATFORM), windows)
  OTHER_LDLIBS = $(LIBM)
endif # PLATFORM

CPPFLAGS += \
	-I$(CLOSED_SRC)/share/native/$(PKGDIR)/doe \
	-I$(CLOSED_SRC)/share/native/$(PKGDIR)/path \
	-I$(CLOSED_SRC)/share/native/$(PKGDIR)/pr \
	-I$(CLOSED_SRC)/share/native/$(PKGDIR)/util \
	-I$(SHARE_SRC)/native/sun/java2d/pipe \
	-I$(PLATFORM_SRC)/native/sun/java2d

