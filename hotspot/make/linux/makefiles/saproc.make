#
# Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

SAPROC = saproc
LIBSAPROC = lib$(SAPROC).so

SAPROC_G = $(SAPROC)$(G_SUFFIX)
LIBSAPROC_G = lib$(SAPROC_G).so

LIBSAPROC_DEBUGINFO   = lib$(SAPROC).debuginfo
LIBSAPROC_G_DEBUGINFO = lib$(SAPROC_G).debuginfo

AGENT_DIR = $(GAMMADIR)/agent

SASRCDIR = $(AGENT_DIR)/src/os/$(Platform_os_family)

SASRCFILES = $(SASRCDIR)/salibelf.c                   \
             $(SASRCDIR)/symtab.c                     \
             $(SASRCDIR)/libproc_impl.c               \
             $(SASRCDIR)/ps_proc.c                    \
             $(SASRCDIR)/ps_core.c                    \
             $(SASRCDIR)/LinuxDebuggerLocal.c

SAMAPFILE = $(SASRCDIR)/mapfile

DEST_SAPROC           = $(JDK_LIBDIR)/$(LIBSAPROC)
DEST_SAPROC_DEBUGINFO = $(JDK_LIBDIR)/$(LIBSAPROC_DEBUGINFO)

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


SA_LFLAGS = $(MAPFLAG:FILENAME=$(SAMAPFILE)) $(LDFLAGS_HASH_STYLE)

$(LIBSAPROC): $(SASRCFILES) $(SAMAPFILE)
	$(QUIETLY) if [ "$(BOOT_JAVA_HOME)" = "" ]; then \
	  echo "ALT_BOOTDIR, BOOTDIR or JAVA_HOME needs to be defined to build SA"; \
	  exit 1; \
	fi
	@echo Making SA debugger back-end...
	$(QUIETLY) $(CC) -D$(BUILDARCH) -D_GNU_SOURCE                   \
                   $(SYMFLAG) $(ARCHFLAG) $(SHARED_FLAG) $(PICFLAG)     \
	           -I$(SASRCDIR)                                        \
	           -I$(GENERATED)                                       \
	           -I$(BOOT_JAVA_HOME)/include                          \
	           -I$(BOOT_JAVA_HOME)/include/$(Platform_os_family)    \
	           $(SASRCFILES)                                        \
	           $(SA_LFLAGS)                                         \
	           $(SA_DEBUG_CFLAGS)                                   \
	           -o $@                                                \
	           -lthread_db
	$(QUIETLY) [ -f $(LIBSAPROC_G) ] || { ln -s $@ $(LIBSAPROC_G); }
ifneq ($(OBJCOPY),)
	$(QUIETLY) $(OBJCOPY) --only-keep-debug $@ $(LIBSAPROC_DEBUGINFO)
	$(QUIETLY) $(OBJCOPY) --add-gnu-debuglink=$(LIBSAPROC_DEBUGINFO) $@
  ifeq ($(STRIP_POLICY),all_strip)
	$(QUIETLY) $(STRIP) $@
  else
    ifeq ($(STRIP_POLICY),min_strip)
	$(QUIETLY) $(STRIP) -g $@
    # implied else here is no stripping at all
    endif
  endif
	[ -f $(LIBSAPROC_G_DEBUGINFO) ] || { ln -s $(LIBSAPROC_DEBUGINFO) $(LIBSAPROC_G_DEBUGINFO); }
endif

install_saproc: $(BUILDLIBSAPROC)
	$(QUIETLY) if [ -e $(LIBSAPROC) ] ; then             \
	  echo "Copying $(LIBSAPROC) to $(DEST_SAPROC)";     \
	  test -f $(LIBSAPROC_DEBUGINFO) &&                  \
	    cp -f $(LIBSAPROC_DEBUGINFO) $(DEST_SAPROC_DEBUGINFO); \
	  cp -f $(LIBSAPROC) $(DEST_SAPROC) && echo "Done";  \
	fi

.PHONY: install_saproc
