#
# Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

# libsaproc.so(dylib): serviceability agent
SAPROC   = saproc

ifeq ($(OS_VENDOR), Darwin)
  LIBSAPROC           = lib$(SAPROC).$(LIBRARY_SUFFIX)

  LIBSAPROC_DEBUGINFO = lib$(SAPROC).$(LIBRARY_SUFFIX).dSYM
  LIBSAPROC_DIZ       = lib$(SAPROC).diz
else
  LIBSAPROC           = lib$(SAPROC).so

  LIBSAPROC_DEBUGINFO = lib$(SAPROC).debuginfo
  LIBSAPROC_DIZ       = lib$(SAPROC).diz
endif

AGENT_DIR = $(GAMMADIR)/agent

SASRCDIR = $(AGENT_DIR)/src/os/$(Platform_os_family)

BSD_NON_STUB_SASRCFILES = $(SASRCDIR)/salibelf.c             \
                      $(SASRCDIR)/symtab.c                   \
                      $(SASRCDIR)/libproc_impl.c             \
                      $(SASRCDIR)/ps_proc.c                  \
                      $(SASRCDIR)/ps_core.c                  \
                      $(SASRCDIR)/BsdDebuggerLocal.c         \
                      $(AGENT_DIR)/src/share/native/sadis.c

DARWIN_NON_STUB_SASRCFILES = $(SASRCDIR)/symtab.c            \
                      $(SASRCDIR)/libproc_impl.c             \
                      $(SASRCDIR)/ps_core.c                  \
                      $(SASRCDIR)/MacosxDebuggerLocal.m      \
                      $(AGENT_DIR)/src/share/native/sadis.c

ifeq ($(OS_VENDOR), FreeBSD)
  SASRCFILES = $(BSD_NON_STUB_SASRCFILES)
  SALIBS = -lutil -lthread_db
  SAARCH = $(ARCHFLAG)
else
  ifeq ($(OS_VENDOR), Darwin)
    SASRCFILES = $(DARWIN_NON_STUB_SASRCFILES)
    SALIBS = -g \
             -framework Foundation \
             -framework JavaNativeFoundation \
             -framework Security \
             -framework CoreFoundation
    #objc compiler blows up on -march=i586, perhaps it should not be included in the macosx intel 32-bit C++ compiles?
    SAARCH = $(subst -march=i586,,$(ARCHFLAG))

    # This is needed to locate JavaNativeFoundation.framework
    ifeq ($(SYSROOT_CFLAGS),)
      # this will happen when building without spec.gmk, set SDKROOT to a valid SDK
      # path if your system does not have headers installed in the system frameworks
      SA_SYSROOT_FLAGS = -F"$(SDKROOT)/System/Library/Frameworks/JavaVM.framework/Frameworks"
    else
      # Just use SYSROOT_CFLAGS
      SA_SYSROOT_FLAGS=$(SYSROOT_CFLAGS)
    endif
  else
    SASRCFILES = $(SASRCDIR)/StubDebuggerLocal.c
    SALIBS =
    SAARCH = $(ARCHFLAG)
  endif
endif

SAMAPFILE = $(SASRCDIR)/mapfile

DEST_SAPROC           = $(JDK_LIBDIR)/$(LIBSAPROC)
DEST_SAPROC_DEBUGINFO = $(JDK_LIBDIR)/$(LIBSAPROC_DEBUGINFO)
DEST_SAPROC_DIZ       = $(JDK_LIBDIR)/$(LIBSAPROC_DIZ)

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

BOOT_JAVA_INCLUDES = -I$(BOOT_JAVA_HOME)/include \
  -I$(BOOT_JAVA_HOME)/include/$(shell uname -s | tr "[:upper:]" "[:lower:]")

$(LIBSAPROC): $(SASRCFILES) $(SAMAPFILE)
	$(QUIETLY) if [ "$(BOOT_JAVA_HOME)" = "" ]; then \
	  echo "ALT_BOOTDIR, BOOTDIR or JAVA_HOME needs to be defined to build SA"; \
	  exit 1; \
	fi
	@echo $(LOG_INFO) Making SA debugger back-end...
	$(QUIETLY) $(CC) -D$(BUILDARCH) -D_GNU_SOURCE                   \
	           $(SA_SYSROOT_FLAGS)                                  \
	           $(SYMFLAG) $(SAARCH) $(SHARED_FLAG) $(PICFLAG)       \
	           -I$(SASRCDIR)                                        \
	           -I$(GENERATED)                                       \
	           $(BOOT_JAVA_INCLUDES)                                \
	           $(SASRCFILES)                                        \
	           $(SA_LFLAGS)                                         \
	           $(SA_DEBUG_CFLAGS)                                   \
	           -o $@                                                \
	           $(SALIBS)
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(OS_VENDOR), Darwin)
	$(DSYMUTIL) $@
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -r -y $(LIBSAPROC_DIZ) $(LIBSAPROC_DEBUGINFO)
	$(RM) -r $(LIBSAPROC_DEBUGINFO)
    endif
  else
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
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -y $(LIBSAPROC_DIZ) $(LIBSAPROC_DEBUGINFO)
	$(RM) $(LIBSAPROC_DEBUGINFO)
    endif
  endif
endif

install_saproc: $(BUILDLIBSAPROC)
	@echo "Copying $(LIBSAPROC) to $(DEST_SAPROC)"
ifeq ($(OS_VENDOR), Darwin)
	$(QUIETLY) test ! -d $(LIBSAPROC_DEBUGINFO) || \
	    $(CP) -f -r $(LIBSAPROC_DEBUGINFO) $(DEST_SAPROC_DEBUGINFO)
else
	$(QUIETLY) test ! -f $(LIBSAPROC_DEBUGINFO) || \
	    $(CP) -f $(LIBSAPROC_DEBUGINFO) $(DEST_SAPROC_DEBUGINFO)
endif
	$(QUIETLY) test ! -f $(LIBSAPROC_DIZ) || \
	    $(CP) -f $(LIBSAPROC_DIZ) $(DEST_SAPROC_DIZ)
	$(QUIETLY) $(CP) -f $(LIBSAPROC) $(DEST_SAPROC) && echo "Done"

.PHONY: install_saproc
