#
# Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
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

# Rules to build serviceability agent library, used by vm.make

# libsaproc[_g].so: serviceability agent
SAPROC   = saproc
SAPROC_G = $(SAPROC)$(G_SUFFIX)

ifeq ($(OS_VENDOR), Darwin)
  LIBSAPROC   = lib$(SAPROC).dylib
  LIBSAPROC_G = lib$(SAPROC_G).dylib
else
  LIBSAPROC   = lib$(SAPROC).so
  LIBSAPROC_G = lib$(SAPROC_G).so
endif

AGENT_DIR = $(GAMMADIR)/agent

SASRCDIR = $(AGENT_DIR)/src/os/$(Platform_os_family)

NON_STUB_SASRCFILES = $(SASRCDIR)/salibelf.c          \
                      $(SASRCDIR)/symtab.c            \
                      $(SASRCDIR)/libproc_impl.c      \
                      $(SASRCDIR)/ps_proc.c           \
                      $(SASRCDIR)/ps_core.c           \
                      $(SASRCDIR)/BsdDebuggerLocal.c

ifeq ($(OS_VENDOR), FreeBSD)
  SASRCFILES = $(NON_STUB_SASRCFILES)
  SALIBS = -lutil -lthread_db
  SAARCH = $(ARCHFLAG)
else
  ifeq ($(OS_VENDOR), Darwin)
    SASRCFILES = $(SASRCDIR)/MacosxDebuggerLocal.m
    SALIBS = -g -framework Foundation -F/System/Library/Frameworks/JavaVM.framework/Frameworks -framework JavaNativeFoundation -framework Security -framework CoreFoundation
    #objc compiler blows up on -march=i586, perhaps it should not be included in the macosx intel 32-bit C++ compiles?
    SAARCH = $(subst -march=i586,,$(ARCHFLAG))
  else
    SASRCFILES = $(SASRCDIR)/StubDebuggerLocal.c
    SALIBS = 
    SAARCH = $(ARCHFLAG)
  endif
endif

SAMAPFILE = $(SASRCDIR)/mapfile

DEST_SAPROC = $(JDK_LIBDIR)/$(LIBSAPROC)

# DEBUG_BINARIES overrides everything, use full -g debug information
ifeq ($(DEBUG_BINARIES), true)
  SA_DEBUG_CFLAGS = -g
endif

# if $(AGENT_DIR) does not exist, we don't build SA
# also, we don't build SA on Itanium, PPC, ARM or zero.

ifneq ($(wildcard $(AGENT_DIR)),)
ifneq ($(filter-out ia64 arm ppc zero,$(SRCARCH)),)
  BUILDLIBSAPROC = $(LIBSAPROC)
endif
endif


ifneq ($(OS_VENDOR), Darwin)
SA_LFLAGS = $(MAPFLAG:FILENAME=$(SAMAPFILE))
endif
SA_LFLAGS += $(LDFLAGS_HASH_STYLE)

ifeq ($(OS_VENDOR), Darwin)
  BOOT_JAVA_INCLUDES = -I$(BOOT_JAVA_HOME)/include \
    -I$(BOOT_JAVA_HOME)/include/$(shell uname -s | tr "[:upper:]" "[:lower:]") \
    -I/System/Library/Frameworks/JavaVM.framework/Headers
else
  BOOT_JAVA_INCLUDES = -I$(BOOT_JAVA_HOME)/include \
    -I$(BOOT_JAVA_HOME)/include/$(shell uname -s | tr "[:upper:]" "[:lower:]")
endif

$(LIBSAPROC): $(SASRCFILES) $(SAMAPFILE)
	$(QUIETLY) if [ "$(BOOT_JAVA_HOME)" = "" ]; then \
	  echo "ALT_BOOTDIR, BOOTDIR or JAVA_HOME needs to be defined to build SA"; \
	  exit 1; \
	fi
	@echo Making SA debugger back-end...
	$(QUIETLY) $(CC) -D$(BUILDARCH) -D_GNU_SOURCE                   \
                   $(SYMFLAG) $(SAARCH) $(SHARED_FLAG) $(PICFLAG)     \
	           -I$(SASRCDIR)                                        \
	           -I$(GENERATED)                                       \
	           $(BOOT_JAVA_INCLUDES)                                \
	           $(SASRCFILES)                                        \
	           $(SA_LFLAGS)                                         \
	           $(SA_DEBUG_CFLAGS)                                   \
	           -o $@                                                \
	           $(SALIBS)
	$(QUIETLY) [ -f $(LIBSAPROC_G) ] || { ln -s $@ $(LIBSAPROC_G); }

install_saproc: $(BUILDLIBSAPROC)
	$(QUIETLY) if [ -e $(LIBSAPROC) ] ; then             \
	  echo "Copying $(LIBSAPROC) to $(DEST_SAPROC)";     \
	  cp -f $(LIBSAPROC) $(DEST_SAPROC) && echo "Done";  \
	fi

.PHONY: install_saproc
