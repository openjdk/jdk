#
# Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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

# libsaproc.so: serviceability agent

SAPROC = saproc
SADIS = sadis
LIBSAPROC = lib$(SAPROC).so
SADISOBJ = $(SADIS).o

LIBSAPROC_DEBUGINFO   = lib$(SAPROC).debuginfo
LIBSAPROC_DIZ         = lib$(SAPROC).diz

AGENT_DIR = $(GAMMADIR)/agent

SASRCDIR = $(AGENT_DIR)/src/os/$(Platform_os_family)/proc

SASRCFILES = $(SASRCDIR)/saproc.cpp

SADISSRCFILES = $(AGENT_DIR)/src/share/native/sadis.c

SAMAPFILE = $(SASRCDIR)/mapfile

DEST_SAPROC           = $(JDK_LIBDIR)/$(LIBSAPROC)
DEST_SAPROC_DEBUGINFO = $(JDK_LIBDIR)/$(LIBSAPROC_DEBUGINFO)
DEST_SAPROC_DIZ       = $(JDK_LIBDIR)/$(LIBSAPROC_DIZ)

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

# The libproc Pstack_iter() interface changed in Nevada-B159.
# Use 'uname -r -v' to determine the Solaris version as per
# Solaris Nevada team request. This logic needs to match:
# agent/src/os/solaris/proc/saproc.cpp: set_has_newer_Pstack_iter():
#   - skip SunOS 4 or older
#   - skip Solaris 10 or older
#   - skip two digit internal Nevada builds
#   - skip three digit internal Nevada builds thru 149
#   - skip internal Nevada builds 150-158
#   - if not skipped, print define for Nevada-B159 or later
SOLARIS_11_B159_OR_LATER := \
$(shell uname -r -v \
    | sed -n \
          -e '/^[0-4]\. /b' \
          -e '/^5\.[0-9] /b' \
          -e '/^5\.10 /b' \
          -e '/ snv_[0-9][0-9]$$/b' \
          -e '/ snv_[01][0-4][0-9]$$/b' \
          -e '/ snv_15[0-8]$$/b' \
          -e 's/.*/-DSOLARIS_11_B159_OR_LATER/' \
          -e 'p' \
          )

# Uncomment the following to simulate building on Nevada-B159 or later
# when actually building on Nevada-B158 or earlier:
#SOLARIS_11_B159_OR_LATER=-DSOLARIS_11_B159_OR_LATER


$(LIBSAPROC): $(ADD_GNU_DEBUGLINK) $(FIX_EMPTY_SEC_HDR_FLAGS) $(SASRCFILES) $(SADISOBJ) $(SAMAPFILE)
	$(QUIETLY) if [ "$(BOOT_JAVA_HOME)" = "" ]; then \
	  echo "ALT_BOOTDIR, BOOTDIR or JAVA_HOME needs to be defined to build SA"; \
	  exit 1; \
	fi
	@echo Making SA debugger back-end...
	           $(QUIETLY) $(CXX)                                    \
                   $(SYMFLAG) $(ARCHFLAG) $(SHARED_FLAG) $(PICFLAG)     \
	           -I$(SASRCDIR)                                        \
	           -I$(GENERATED)                                       \
	           -I$(BOOT_JAVA_HOME)/include                          \
	           -I$(BOOT_JAVA_HOME)/include/$(Platform_os_family)    \
	           $(SOLARIS_11_B159_OR_LATER)                          \
	           $(SASRCFILES)                                        \
	           $(SADISOBJ)                                          \
	           $(SA_LFLAGS)                                         \
	           -o $@                                                \
	           -ldl -ldemangle -lthread -lc

$(SADISOBJ): $(SADISSRCFILES)
	           $(QUIETLY) $(CC)                                     \
	           $(SYMFLAG) $(ARCHFLAG) $(SHARED_FLAG) $(PICFLAG)     \
	           -I$(SASRCDIR)                                        \
	           -I$(GENERATED)                                       \
	           -I$(BOOT_JAVA_HOME)/include                          \
	           -I$(BOOT_JAVA_HOME)/include/$(Platform_os_family)    \
	           $(SOLARIS_11_B159_OR_LATER)                          \
	           $(SADISSRCFILES)                                     \
	           -c -o $(SADISOBJ)
	
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
# gobjcopy crashes on "empty" section headers with the SHF_ALLOC flag set.
# Clear the SHF_ALLOC flag (if set) from empty section headers.
# An empty section header has sh_addr == 0 and sh_size == 0.
# This problem has only been seen on Solaris X64, but we call this tool
# on all Solaris builds just in case.
	$(QUIETLY) $(FIX_EMPTY_SEC_HDR_FLAGS) $@
	$(QUIETLY) $(OBJCOPY) --only-keep-debug $@ $(LIBSAPROC_DEBUGINFO)
# $(OBJCOPY) --add-gnu-debuglink=... corrupts SUNW_* sections.
# Use $(ADD_GNU_DEBUGLINK) until a fixed $(OBJCOPY) is available.
#	$(QUIETLY) $(OBJCOPY) --add-gnu-debuglink=$(LIBSAPROC_DEBUGINFO) $@
	$(QUIETLY) $(ADD_GNU_DEBUGLINK) $(LIBSAPROC_DEBUGINFO) $@
  ifeq ($(STRIP_POLICY),all_strip)
	$(QUIETLY) $(STRIP) $@
  else
    ifeq ($(STRIP_POLICY),min_strip)
	$(QUIETLY) $(STRIP) -x $@
    # implied else here is no stripping at all
    endif
  endif
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -y $(LIBSAPROC_DIZ) $(LIBSAPROC_DEBUGINFO)
	$(RM) $(LIBSAPROC_DEBUGINFO)
  endif
endif

install_saproc: $(BULDLIBSAPROC)
	$(QUIETLY) if [ -f $(LIBSAPROC) ] ; then                   \
	  echo "Copying $(LIBSAPROC) to $(DEST_SAPROC)";           \
	  test ! -f $(LIBSAPROC_DEBUGINFO) ||                      \
	    cp -f $(LIBSAPROC_DEBUGINFO) $(DEST_SAPROC_DEBUGINFO); \
	  test ! -f $(LIBSAPROC_DIZ) ||                            \
	    cp -f $(LIBSAPROC_DIZ) $(DEST_SAPROC_DIZ);             \
	  cp -f $(LIBSAPROC) $(DEST_SAPROC) && echo "Done";        \
	fi

.PHONY: install_saproc
