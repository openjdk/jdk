#
# Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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
# Java Launcher Infrastructure Library (libjli)
#
# This library provides shared support for the Java launcher in all of
# its manifestations (java, javaw, javac, ...).
#
BUILDDIR = ../..
MODULE  = base
LIBRARY = jli
PRODUCT = java

#
# Must be included before Defs.gmk to be functional.
#
# Note that for Windows, both a dynamic and static version are built.
# Doing the compiles with the static library specified can be overridden
# by the link step, but not the reverse.
#
MS_RUNTIME_STATIC = true

include $(BUILDDIR)/common/Defs.gmk

ZIP_SRC = $(SHARE_SRC)/native/java/util/zip/zlib-$(ZLIB_VERSION)
LAUNCHER_SHARE_SRC = $(SHARE_SRC)/bin
LAUNCHER_PLATFORM_SRC = $(PLATFORM_SRC)/bin

ifeq ($(ZERO_BUILD), true)
ERGO_FAMILY=zero
else
ifeq ($(ARCH_FAMILY), amd64)
ERGO_FAMILY=i586
else
ERGO_FAMILY=$(ARCH_FAMILY)
endif
endif


#
# Files to compile.
#
FILES_c = \
	java.c \
	splashscreen_stubs.c \
	java_md.c \
	parse_manifest.c \
	version_comp.c \
	wildcard.c \
	jli_util.c \
	inflate.c \
	inftrees.c \
	inffast.c \
	zadler32.c \
	zcrc32.c \
	zutil.c

ifneq ($(PLATFORM), windows)

FILES_c += \
	$(CTARGDIR)ergo.c \
	$(CTARGDIR)ergo_$(ERGO_FAMILY).c
endif


# Names of arch directories
LIBARCH_DEFINES = -DLIBARCHNAME='"$(LIBARCH)"'
ifeq ($(PLATFORM), solaris)
  LIBARCH_DEFINES += -DLIBARCH32NAME='"$(LIBARCH32)"'
  LIBARCH_DEFINES += -DLIBARCH64NAME='"$(LIBARCH64)"'
endif

OTHER_CPPFLAGS += $(LIBARCH_DEFINES)


ifneq ($(PLATFORM), windows)	# UNIX systems
        LD_RUNPATH_EXTRAS += ..
	LIB_LOCATION = $(LIBDIR)/$(LIBARCH)/jli
	# Note: its important to keep this order meaning -lc is the
	# last library otherwise it could cause compatibility issues
	# by pulling in SUNW_private symbols from libc
	LDLIBS = -ldl -lc
ifeq ($(USE_PTHREADS),true)
	LDLIBS += -lpthread
endif # USE_PTHREADS 
endif # PLATFORM

ifeq ($(PLATFORM), windows)
	EXTRA_LIBS = advapi32.lib \
		     comctl32.lib \
		     user32.lib

	JAVALIB =
	OTHER_LCF =  -export:JLI_Launch \
		     -export:JLI_ManifestIterate \
		     -export:JLI_SetTraceLauncher \
		     -export:JLI_ReportErrorMessage \
		     -export:JLI_ReportErrorMessageSys \
		     -export:JLI_ReportMessage \
		     -export:JLI_ReportExceptionDescription

endif

OTHER_INCLUDES += -I$(LAUNCHER_SHARE_SRC)
OTHER_INCLUDES += -I$(LAUNCHER_PLATFORM_SRC)
OTHER_INCLUDES += -I$(ZIP_SRC)

#
# Library to compile.
#
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

#
# On Windows, some executable objects need to be statically linked against
# the jli library.  Hence, we need both a standard library (archive) and
# an import library (associated with a dll).  These both usually have the
# extension .LIB, so they need to be placed in different directories.  The
# import library is build (as usual) in the $(OBJDIR) directory while the
# standard library is built in a "static" subdirectory.  The standard library
# is not delivered as part of the product, but is only needed as part of
# the build process.  The import library is built by the standard rules
# in Library.gmk.  The additional rules which follow build the standard
# library.
#
ifeq ($(PLATFORM), windows)

STATIC_LIBRARY = $(OBJDIR)/static/$(LIBPREFIX)$(LIBRARY).lib

$(STATIC_LIBRARY): $(FILES_o)
	@$(prep-target)
	$(LIBEXE) -nologo -out:$@ $(FILES_o)

library:: $(STATIC_LIBRARY)

endif	# PLATFORM

#
# Add to ambient vpath so we pick up the library files
#
vpath %.c $(LAUNCHER_SHARE_SRC) $(ZIP_SRC) $(LAUNCHER_PLATFORM_SRC)
