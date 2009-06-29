#
# Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#
# Makefile for building all of sun tools
#

BUILDDIR = ..
PRODUCT  = sun
include $(BUILDDIR)/common/Defs.gmk

# Rhino/Mozilla java sources
ORG_EXISTS := $(call DirExists,$(CLOSED_SRC)/share/classes/sun/org,,)
ifneq ("$(ORG_EXISTS)", "")
  ORG_SUBDIR = org
endif

# Non windows subdirs
ifneq ($(PLATFORM), windows)
  ifndef OPENJDK
    ifeq ($(PLATFORM), solaris)
      ifneq ($(ARCH), amd64)
        DGA_SUBDIR = jdga
      endif
    endif
  endif
  HEADLESS_SUBDIR = headless
  XAWT_SUBDIR     = xawt
endif

ifndef OPENJDK
  JDBC_SUBDIR = jdbc
endif
ifdef OPENJDK
  RENDER_SUBDIR = pisces
else
  RENDER_SUBDIR = dcpr
endif
# nio need to be compiled before awt to have all charsets ready
SUBDIRS = jar security javazic misc net audio $(RENDER_SUBDIR) image \
	  nio awt splashscreen $(XAWT_SUBDIR) \
          $(HEADLESS_SUBDIR) $(DGA_SUBDIR) \
	  font jpeg cmm applet rmi beans $(JDBC_SUBDIR) \
	  jawt text launcher management $(ORG_SUBDIR) \
          native2ascii serialver tools jconsole tracing

all build clean clobber::
	$(SUBDIRS-loop)

