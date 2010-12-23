#
# Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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

AGENT_DIR = $(GAMMADIR)/agent

SASRCDIR = $(AGENT_DIR)/src/os/$(Platform_os_family)/proc

SASRCFILES = $(SASRCDIR)/saproc.cpp

SAMAPFILE = $(SASRCDIR)/mapfile

DEST_SAPROC = $(JDK_LIBDIR)/$(LIBSAPROC)

# if $(AGENT_DIR) does not exist, we don't build SA

ifneq ($(wildcard $(AGENT_DIR)),)
  BUILDLIBSAPROC = $(LIBSAPROC)
endif

SA_LFLAGS = $(MAPFLAG:FILENAME=$(SAMAPFILE))

ifdef USE_GCC
SA_LFLAGS += -D_REENTRANT
else
SA_LFLAGS += -mt -xnolib -norunpath
endif

$(LIBSAPROC): $(SASRCFILES) $(SAMAPFILE)
	$(QUIETLY) if [ "$(BOOT_JAVA_HOME)" = "" ]; then \
	  echo "ALT_BOOTDIR, BOOTDIR or JAVA_HOME needs to be defined to build SA"; \
	  exit 1; \
	fi
	@echo Making SA debugger back-end...
	$(QUIETLY) $(CPP)                                               \
                   $(SYMFLAG) $(ARCHFLAG) $(SHARED_FLAG) $(PICFLAG)     \
	           -I$(SASRCDIR)                                        \
	           -I$(GENERATED)                                       \
	           -I$(BOOT_JAVA_HOME)/include                          \
	           -I$(BOOT_JAVA_HOME)/include/$(Platform_os_family)    \
	           $(SASRCFILES)                                        \
	           $(SA_LFLAGS)                                         \
	           -o $@                                                \
	           -ldl -ldemangle -lthread -lc
	[ -f $(LIBSAPROC_G) ] || { ln -s $@ $(LIBSAPROC_G); }

install_saproc: $(BULDLIBSAPROC)
	$(QUIETLY) if [ -f $(LIBSAPROC) ] ; then             \
	  echo "Copying $(LIBSAPROC) to $(DEST_SAPROC)";     \
	  cp -f $(LIBSAPROC) $(DEST_SAPROC) && echo "Done";  \
	fi

.PHONY: install_saproc
