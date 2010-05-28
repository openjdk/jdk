#
# Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

#
# Makefile for native threads HPI.
#

BUILDDIR     = ../../..
MODULE       = base
LIBRARY      = hpi
PRODUCT      = java
THREADDIR    = native_threads
LIB_LOCATION = $(LIBDIR)/$(LIBARCH)/$(THREADDIR)
include $(BUILDDIR)/common/Defs.gmk

#
# Native threads specific C and .s files.
#
FILES_c = \
	monitor_md.c \
	threads_md.c \
	condvar_md.c \
	interrupt_md.c \
	mutex_md.c \
	sys_api_td.c \
	threads_$(PLATFORM).c

#
# Other files/flags shared between the HPIs.
#
include $(BUILDDIR)/java/hpi/hpi_common.gmk

#
# Rules for the .so file.
#
ifeq ($(PLATFORM), solaris)
  ifneq ($(ARCH), amd64)
    FILES_reorder += reorder-$(ARCH)
  endif
endif
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

#
# HPI flags for native threads.
#
OTHER_CPPFLAGS += -D_REENTRANT -DNATIVE

ifeq ($(USE_PTHREADS),true)
OTHER_CPPFLAGS += -DUSE_PTHREADS
ifeq ($(MOOT_PRIORITIES),true)
OTHER_CPPFLAGS += -DMOOT_PRIORITIES
endif
LIBPOSIX4	= -lposix4
OTHER_LDLIBS   += -lpthread $(LIBPOSIX4)
endif

HAVE_GETHRVTIME=true
ifeq ($(HAVE_GETHRVTIME),true)
OTHER_CPPFLAGS += -DHAVE_GETHRVTIME
endif

HAVE_FILIOH=true
ifeq ($(HAVE_FILIOH),true)
OTHER_CPPFLAGS += -DHAVE_FILIOH
endif

ifeq ($(NO_INTERRUPTIBLE_IO),true)
OTHER_CPPFLAGS += -DNO_INTERRUPTIBLE_IO
endif

