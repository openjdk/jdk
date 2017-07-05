#
# Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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
LIBRARY = hprof
PRODUCT = sun
LIBRARY_OUTPUT = hprof_jvmti

# Use highest optimization
OPTIMIZATION_LEVEL = HIGHEST

# Configure the CFLAGS for this library.
FILES_m = mapfile-vers

include $(BUILDDIR)/common/Defs.gmk

SRCDIR=$(SHARE_SRC)/demo/jvmti/hprof
PSRCDIR=$(PLATFORM_SRC)/demo/jvmti/hprof

# Use the mapfile-vers (See the mapfile located with this Makefile)
ifdef FILES_m
include $(BUILDDIR)/common/Mapfile-vers.gmk
endif

#
# Files to compile.
#
FILES_c = 				\
	debug_malloc.c 	\
	hprof_blocks.c 	\
	hprof_check.c 	\
	hprof_class.c 	\
	hprof_cpu.c 		\
	hprof_error.c 	\
	hprof_event.c 	\
	hprof_frame.c 	\
	hprof_init.c 	\
	hprof_io.c 		\
	hprof_ioname.c 	\
	hprof_listener.c 	\
	hprof_loader.c 	\
	hprof_md.c 		\
	hprof_monitor.c	\
	hprof_object.c	\
	hprof_reference.c	\
	hprof_site.c 	\
	hprof_stack.c 	\
	hprof_string.c 	\
	hprof_table.c 	\
	hprof_tag.c 		\
	hprof_tls.c 		\
	hprof_trace.c	\
	hprof_tracker.c 	\
	hprof_util.c

OTHER_INCLUDES = -I$(SRCDIR) \
	         -I$(SHARE_SRC)/npt \
	         -I$(PLATFORM_SRC)/npt \
	         -I$(SHARE_SRC)/demo/jvmti/java_crw_demo

ifeq ($(PLATFORM), windows)
  OTHER_LDLIBS += wsock32.lib winmm.lib
else
  OTHER_LDLIBS += $(LIBSOCKET) $(LIBNSL) $(LIBDL)
endif

#
# Tell library.gmk to copy the txt file first
#
INIT += $(LIBDIR)/jvm.hprof.txt

#
# This puts logging code in
#
CPPFLAGS_DBG += -DHPROF_LOGGING

#
# Library to compile.
#
include $(BUILDDIR)/common/Library.gmk

# We don't want to link against -ljava
JAVALIB=

#
# Add to ambient vpath so we pick up the library files
#
vpath %.c $(SRCDIR):$(PSRCDIR)

#
# Install the hprof prelude
#
$(LIBDIR)/jvm.hprof.txt: $(SRCDIR)/jvm.hprof.txt
	$(install-file)

clean clobber::
	$(RM) $(LIBDIR)/jvm.hprof.txt

