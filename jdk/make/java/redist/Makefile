#
# Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
# Imports files exported by a hotspot build or provided from an external
#   location into the OUTPUTDIR, and also primes the OUTPUTDIR with files
#   that are provided inside this workspace.
#
# IMPORT_LIST contains the list of destination files that are copied 
#     from external places (outside this workspace).
#
# INTERNAL_IMPORT_LIST is the list of destination files from BUILDDIR.
# 

BUILDDIR = ../..
PRODUCT  = java
include $(BUILDDIR)/common/Defs.gmk

SERVER_LOCATION = server
CLIENT_LOCATION = client

DB_SUFFIX = _db
DTRACE_SUFFIX = _dtrace

ifeq ($(PLATFORM), windows)
  LIB_LOCATION = $(BINDIR)
else ifeq ($(PLATFORM), macosx)
  LIB_LOCATION = $(LIBDIR)
else
  LIB_LOCATION = $(LIBDIR)/$(LIBARCH)
endif

JVM_NAME               = $(LIB_PREFIX)jvm.$(LIBRARY_SUFFIX)
JVMLIB_NAME            = $(LIB_PREFIX)jvm.$(LIB_SUFFIX)
JVMMAP_NAME            = $(LIB_PREFIX)jvm.map
JVMPDB_NAME            = $(LIB_PREFIX)jvm.pdb
LIBJSIG_NAME           = $(LIB_PREFIX)jsig.$(LIBRARY_SUFFIX)
JVMDB_NAME             = $(LIB_PREFIX)jvm$(DB_SUFFIX).$(LIBRARY_SUFFIX)
JVMDTRACE_NAME         = $(LIB_PREFIX)jvm$(DTRACE_SUFFIX).$(LIBRARY_SUFFIX)

JVM_DEBUGINFO_NAME       = $(LIB_PREFIX)jvm.debuginfo
JVM_DIZ_NAME             = $(LIB_PREFIX)jvm.diz
LIBJSIG_DEBUGINFO_NAME   = $(LIB_PREFIX)jsig.debuginfo
LIBJSIG_DIZ_NAME         = $(LIB_PREFIX)jsig.diz
JVMDB_DEBUGINFO_NAME     = $(LIB_PREFIX)jvm$(DB_SUFFIX).debuginfo
JVMDB_DIZ_NAME           = $(LIB_PREFIX)jvm$(DB_SUFFIX).diz
JVMDTRACE_DEBUGINFO_NAME = $(LIB_PREFIX)jvm$(DTRACE_SUFFIX).debuginfo
JVMDTRACE_DIZ_NAME       = $(LIB_PREFIX)jvm$(DTRACE_SUFFIX).diz

CLASSSHARINGDATA_DIR   = $(BUILDDIR)/tools/sharing

# Needed to do file copy
ABS_BUILDDIR :=$(call FullPath,$(BUILDDIR))

SUBDIRS_desktop = fonts
SUBDIRS_tools   = sajdi
include $(BUILDDIR)/common/Subdirs.gmk

all clean clobber::
	$(SUBDIRS-loop)

all:: build

# List of files created here or coming from BUILDDIR area (this workspace)
INTERNAL_IMPORT_LIST = $(LIBDIR)/classlist

# List of files coming from outside this workspace
ifndef BUILD_CLIENT_ONLY
  IMPORT_LIST = $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_NAME) \
                $(LIB_LOCATION)/$(SERVER_LOCATION)/Xusage.txt
  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
      # the import JDK may not contain .diz files
      ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/$(JVM_DIZ_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_DIZ_NAME)
      endif
    else
      # the import JDK may not contain .debuginfo files
      ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/$(JVM_DEBUGINFO_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_DEBUGINFO_NAME)
      endif
    endif
  endif
else
  IMPORT_LIST =
endif

# Hotspot client is only available on 32-bit non-Zero builds
ifneq ($(ZERO_BUILD), true)
ifeq ($(ARCH_DATA_MODEL), 32)
  IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_NAME) \
                 $(LIB_LOCATION)/$(CLIENT_LOCATION)/Xusage.txt
  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
      # the import JDK may not contain .diz files
      ifneq ($(wildcard $(HOTSPOT_CLIENT_PATH)/$(JVM_DIZ_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_DIZ_NAME)
      endif
    else
      # the import JDK may not contain .debuginfo files
      ifneq ($(wildcard $(HOTSPOT_CLIENT_PATH)/$(JVM_DEBUGINFO_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_DEBUGINFO_NAME)
      endif
    endif
  endif
endif
endif

ifeq ($(PLATFORM), windows)
#  Windows     vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv  Windows

IMPORT_LIST += $(MS_RUNTIME_LIBRARIES:%=$(BINDIR)/%)

$(BINDIR)/$(MSVCRNN_DLL): $(MSVCRNN_DLL_PATH)/$(MSVCRNN_DLL)
	$(install-import-file)
	$(call chmod-file, a+x)

# Get the hotspot .map and .pdb files for client and server
ifndef BUILD_CLIENT_ONLY
  IMPORT_LIST += $(LIBDIR)/$(JVMLIB_NAME)
  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
      # the import JDK may not contain .diz files
      ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/$(JVM_DIZ_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_DIZ_NAME)
      endif
    else
      # the import JDK may not contain .pdb files
      ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/$(JVMPDB_NAME)),)
        # assume .map file is present if .pdb file is preset
        IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMMAP_NAME) \
	  $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMPDB_NAME)
      endif
    endif
  endif
endif

# Add .map and .pdb files to the import path for client and kernel VMs. 
# These are only available on 32-bit windows builds. 
ifeq ($(ARCH_DATA_MODEL), 32)
  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
      # the import JDK may not contain .diz files
      ifneq ($(wildcard $(HOTSPOT_CLIENT_PATH)/$(JVM_DIZ_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_DIZ_NAME)
      endif
    else
      # the import JDK may not contain .pdb files
      ifneq ($(wildcard $(HOTSPOT_CLIENT_PATH)/$(JVMPDB_NAME)),)
        # assume .map file is present if .pdb file is preset
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMMAP_NAME) \
                       $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMPDB_NAME)
      endif
    endif
    ifeq ($(DO_KERNEL), true)
      ifeq ($(ZIP_DEBUGINFO_FILES),1)
        # the import JDK may not contain .diz files
        ifneq ($(wildcard $(HOTSPOT_KERNEL_PATH)/$(JVM_DIZ_NAME)),)
          IMPORT_LIST += $(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVM_DIZ_NAME)
        endif
      else
        # the import JDK may not contain .pdb files
        ifneq ($(wildcard $(HOTSPOT_KERNEL_PATH)/$(JVMPDB_NAME)),)
          # assume .map file is present if .pdb file is preset
          IMPORT_LIST += $(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVMMAP_NAME) \
                         $(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVMPDB_NAME)
        endif
      endif
    endif
  endif
endif

$(LIBDIR)/$(JVMLIB_NAME): $(HOTSPOT_LIB_PATH)/$(JVMLIB_NAME)
	$(install-import-file)

# it is OK for the .map and .pdb files to not exist, so do not force a 
# dependency on them from the bootstrap location, and allow the copy to fail.
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMMAP_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_CLIENT_PATH)/$(JVMMAP_NAME)  $@

$(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVMMAP_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_KERNEL_PATH)/$(JVMMAP_NAME)  $@

ifndef BUILD_CLIENT_ONLY
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMMAP_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_SERVER_PATH)/$(JVMMAP_NAME) $@
endif

$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMPDB_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_CLIENT_PATH)/$(JVMPDB_NAME)  $@

$(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVMPDB_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_KERNEL_PATH)/$(JVMPDB_NAME)  $@

$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_DIZ_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_CLIENT_PATH)/$(JVM_DIZ_NAME)  $@

$(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVM_DIZ_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_KERNEL_PATH)/$(JVM_DIZ_NAME)  $@

ifndef BUILD_CLIENT_ONLY
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMPDB_NAME): 
	@$(prep-target)
	-$(CP) $(HOTSPOT_SERVER_PATH)/$(JVMPDB_NAME) $@

$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_DIZ_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_SERVER_PATH)/$(JVM_DIZ_NAME) $@
endif

#  Windows     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^  Windows
else # PLATFORM
#  NOT Windows vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv  NOT Windows

IMPORT_LIST += $(LIB_LOCATION)/$(LIBJSIG_NAME) 
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
    # the import JDK may not contain .diz files
    ifneq ($(wildcard $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(LIBJSIG_DIZ_NAME)),)
      IMPORT_LIST += $(LIB_LOCATION)/$(LIBJSIG_DIZ_NAME)
    endif
  else
    # the import JDK may not contain .debuginfo files
    ifneq ($(wildcard $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(LIBJSIG_DEBUGINFO_NAME)),)
      IMPORT_LIST += $(LIB_LOCATION)/$(LIBJSIG_DEBUGINFO_NAME)
    endif
  endif
endif
ifndef BUILD_CLIENT_ONLY
  IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(LIBJSIG_NAME)
  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
      # the import JDK may not contain the target of the symlink
      ifneq ($(wildcard $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(LIBJSIG_DIZ_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(LIBJSIG_DIZ_NAME)
      endif
    else
      # the import JDK may not contain the target of the symlink
      ifneq ($(wildcard $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(LIBJSIG_DEBUGINFO_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(LIBJSIG_DEBUGINFO_NAME)
      endif
    endif
  endif
endif

ifeq ($(PLATFORM), solaris)
  ifndef BUILD_CLIENT_ONLY
    IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDB_NAME)
    ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
      ifeq ($(ZIP_DEBUGINFO_FILES),1)
        # the import JDK may not contain .diz files
        ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/$(JVMDB_DIZ_NAME)),)
          IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDB_DIZ_NAME)
        endif
      else
        # the import JDK may not contain .debuginfo files
        ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/$(JVMDB_DEBUGINFO_NAME)),)
          IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDB_DEBUGINFO_NAME)
        endif
      endif
    endif
    # The conditional can be removed when import JDKs contain these files.
    ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/$(JVMDTRACE_NAME)),)
      IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDTRACE_NAME)
      ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
        ifeq ($(ZIP_DEBUGINFO_FILES),1)
          # the import JDK may not contain .diz files
          ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/$(JVMDTRACE_DIZ_NAME)),)
            IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDTRACE_DIZ_NAME)
          endif
        else
          # the import JDK may not contain .debuginfo files
          ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/$(JVMDTRACE_DEBUGINFO_NAME)),)
            IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDTRACE_DEBUGINFO_NAME)
          endif
        endif
      endif
    else
      $(warning WARNING: $(HOTSPOT_SERVER_PATH)/$(JVMDB_NAME) not found!)
    endif 
  endif 
endif

ifneq ($(ZERO_BUILD), true)
ifeq ($(ARCH_DATA_MODEL), 32)

IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_NAME)
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
    # the import JDK may not contain the target of the symlink
    ifneq ($(wildcard $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(LIBJSIG_DIZ_NAME)),)
      IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_DIZ_NAME)
    endif
  else
    # the import JDK may not contain the target of the symlink
    ifneq ($(wildcard $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(LIBJSIG_DEBUGINFO_NAME)),)
      IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_DEBUGINFO_NAME)
    endif
  endif
endif

ifeq ($(PLATFORM), solaris)
#  solaris   vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv  solaris

IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDB_NAME)
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
    # the import JDK may not contain .diz files
    ifneq ($(wildcard $(HOTSPOT_CLIENT_PATH)/$(JVMDB_DIZ_NAME)),)
      IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDB_DIZ_NAME)
    endif
  else
    # the import JDK may not contain .debuginfo files
    ifneq ($(wildcard $(HOTSPOT_CLIENT_PATH)/$(JVMDB_DEBUGINFO_NAME)),)
      IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDB_DEBUGINFO_NAME)
    endif
  endif
endif

# The conditional can be removed when import JDKs contain these files.
ifneq ($(wildcard $(HOTSPOT_CLIENT_PATH)/$(JVMDTRACE_NAME)),)
  IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDTRACE_NAME)
  IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDB_NAME)
  IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDTRACE_NAME)
  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
      # the import JDK may not contain .diz files
      ifneq ($(wildcard $(HOTSPOT_CLIENT_PATH)/$(JVMDTRACE_DIZ_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDTRACE_DIZ_NAME)
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDB_DIZ_NAME)
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDTRACE_DIZ_NAME)
      endif
    else
      # the import JDK may not contain .debuginfo files
      ifneq ($(wildcard $(HOTSPOT_CLIENT_PATH)/$(JVMDTRACE_DEBUGINFO_NAME)),)
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDTRACE_DEBUGINFO_NAME)
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDB_DEBUGINFO_NAME)
        IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDTRACE_DEBUGINFO_NAME)
      endif
    endif
  endif
else
  $(warning WARNING: $(HOTSPOT_CLIENT_PATH)/$(JVMDTRACE_NAME) not found!)
endif

ifndef BUILD_CLIENT_ONLY
  # The conditional can be removed when import JDKs contain these files.
  ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/64/$(JVMDB_NAME)),)
    IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDB_NAME)
    ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
      ifeq ($(ZIP_DEBUGINFO_FILES),1)
        # the import JDK may not contain .diz files
        ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/64/$(JVMDB_DIZ_NAME)),)
          IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDB_DIZ_NAME)
        endif
      else
        # the import JDK may not contain .debuginfo files
        ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/64/$(JVMDB_DEBUGINFO_NAME)),)
          IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDB_DEBUGINFO_NAME)
        endif
      endif
    endif
  else
    $(warning WARNING: $(HOTSPOT_SERVER_PATH)/64/$(JVMDB_NAME) not found!)
  endif

  # The conditional can be removed when import JDKs contain these files.
  ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/64/$(JVMDTRACE_NAME)),)
    IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDTRACE_NAME)
    ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
      ifeq ($(ZIP_DEBUGINFO_FILES),1)
        # the import JDK may not contain .diz files
        ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/64/$(JVMDTRACE_DIZ_NAME)),)
          IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDTRACE_DIZ_NAME)
        endif
      else
        # the import JDK may not contain .debuginfo files
        ifneq ($(wildcard $(HOTSPOT_SERVER_PATH)/64/$(JVMDTRACE_DEBUGINFO_NAME)),)
          IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDTRACE_DEBUGINFO_NAME)
        endif
      endif
    endif
  else
    $(warning WARNING: $(HOTSPOT_SERVER_PATH)/64/$(JVMDTRACE_NAME) not found!)
 endif
endif

# For backwards compatability, make a link of the 32-bit client JVM to $(LIBDIR)
IMPORT_LIST += $(LIB_LOCATION)/$(JVM_NAME)

$(LIB_LOCATION)/$(JVM_NAME): $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_NAME)
	@$(prep-target)

#  solaris   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ solaris
endif # 32bit solaris

endif # 32bit

endif # ZERO_BUILD

#  NOT Windows ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ NOT Windows

endif # PLATFORM

$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVM_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_DIZ_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVM_DIZ_NAME)
	$(install-import-file)
  else
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_DEBUGINFO_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVM_DEBUGINFO_NAME)
	$(install-import-file)
  endif
endif

$(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVM_NAME): $(HOTSPOT_KERNEL_PATH)/$(JVM_NAME)
	$(install-file)
	@$(call binary_file_verification,$@)

$(LIB_LOCATION)/$(LIBJSIG_NAME): $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(LIBJSIG_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
$(LIB_LOCATION)/$(LIBJSIG_DIZ_NAME): $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(LIBJSIG_DIZ_NAME)
	$(install-import-file)
  else
$(LIB_LOCATION)/$(LIBJSIG_DEBUGINFO_NAME): $(HOTSPOT_IMPORT_PATH)/$(ARCH_VM_SUBDIR)/$(LIBJSIG_DEBUGINFO_NAME)
	$(install-import-file)
  endif
endif

ifndef BUILD_CLIENT_ONLY
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_NAME) \
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(LIBJSIG_NAME):
	@$(prep-target)
	$(call install-sym-link, ../$(LIBJSIG_NAME))

  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
# We don't create a symlink to a libjsig.diz file, but we do put
# the libjsig.debuginfo symlink into a libjsig.diz file. The aurora
# system does not like dangling symlinks.
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_DIZ_NAME) \
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(LIBJSIG_DIZ_NAME):
	@$(prep-target)
	$(LN) -s ../$(LIBJSIG_DEBUGINFO_NAME) $(@D)/$(LIBJSIG_DEBUGINFO_NAME)
	( $(CD) $(@D) ; \
	 $(ZIPEXE) -y $(LIBJSIG_DIZ_NAME) $(LIBJSIG_DEBUGINFO_NAME) ; \
	 $(RM) $(LIBJSIG_DEBUGINFO_NAME) ; \
	)
    else
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_DEBUGINFO_NAME) \
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(LIBJSIG_DEBUGINFO_NAME):
	@$(prep-target)
	$(call install-sym-link, ../$(LIBJSIG_DEBUGINFO_NAME))
    endif
  endif
else
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_NAME):
	@$(prep-target)
	$(call install-sym-link, ../$(LIBJSIG_NAME))

  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
# We don't create a symlink to a libjsig.diz file, but we do put
# the libjsig.debuginfo symlink into a libjsig.diz file. The aurora
# system does not like dangling symlinks.
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_DIZ_NAME):
	@$(prep-target)
	$(LN) -s ../$(LIBJSIG_DEBUGINFO_NAME) $(@D)/$(LIBJSIG_DEBUGINFO_NAME)
	( $(CD) $(@D) ; \
	 $(ZIPEXE) -y $(LIBJSIG_DIZ_NAME) $(LIBJSIG_DEBUGINFO_NAME) ; \
	 $(RM) $(LIBJSIG_DEBUGINFO_NAME) ; \
	)
    else
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_DEBUGINFO_NAME):
	@$(prep-target)
	$(call install-sym-link, ../$(LIBJSIG_DEBUGINFO_NAME))
    endif
  endif
endif

$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDB_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVMDB_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

$(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDB_NAME): $(HOTSPOT_CLIENT_PATH)/64/$(JVMDB_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDB_DIZ_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVMDB_DIZ_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDB_DIZ_NAME): $(HOTSPOT_CLIENT_PATH)/64/$(JVMDB_DIZ_NAME)
	$(install-import-file)
  else
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDB_DEBUGINFO_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVMDB_DEBUGINFO_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDB_DEBUGINFO_NAME): $(HOTSPOT_CLIENT_PATH)/64/$(JVMDB_DEBUGINFO_NAME)
	$(install-import-file)
  endif
endif

ifndef BUILD_CLIENT_ONLY
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDB_NAME): $(HOTSPOT_SERVER_PATH)/$(JVMDB_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

$(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDB_NAME): $(HOTSPOT_SERVER_PATH)/64/$(JVMDB_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

  ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
    ifeq ($(ZIP_DEBUGINFO_FILES),1)
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDB_DIZ_NAME): $(HOTSPOT_SERVER_PATH)/$(JVMDB_DIZ_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDB_DIZ_NAME): $(HOTSPOT_SERVER_PATH)/64/$(JVMDB_DIZ_NAME)
	$(install-import-file)
    else
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDB_DEBUGINFO_NAME): $(HOTSPOT_SERVER_PATH)/$(JVMDB_DEBUGINFO_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDB_DEBUGINFO_NAME): $(HOTSPOT_SERVER_PATH)/64/$(JVMDB_DEBUGINFO_NAME)
	$(install-import-file)
    endif
  endif
endif

$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDTRACE_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVMDTRACE_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

$(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDTRACE_NAME): $(HOTSPOT_CLIENT_PATH)/64/$(JVMDTRACE_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDTRACE_DIZ_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVMDTRACE_DIZ_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDTRACE_DIZ_NAME): $(HOTSPOT_CLIENT_PATH)/64/$(JVMDTRACE_DIZ_NAME)
	$(install-import-file)
  else
$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDTRACE_DEBUGINFO_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVMDTRACE_DEBUGINFO_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDTRACE_DEBUGINFO_NAME): $(HOTSPOT_CLIENT_PATH)/64/$(JVMDTRACE_DEBUGINFO_NAME)
	$(install-import-file)
  endif
endif

ifndef BUILD_CLIENT_ONLY
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDTRACE_NAME): $(HOTSPOT_SERVER_PATH)/$(JVMDTRACE_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

$(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDTRACE_NAME): $(HOTSPOT_SERVER_PATH)/64/$(JVMDTRACE_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_NAME): $(HOTSPOT_SERVER_PATH)/$(JVM_NAME)
	$(install-import-file)
	@$(call binary_file_verification,$@)

ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDTRACE_DIZ_NAME): $(HOTSPOT_SERVER_PATH)/$(JVMDTRACE_DIZ_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDTRACE_DIZ_NAME): $(HOTSPOT_SERVER_PATH)/64/$(JVMDTRACE_DIZ_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_DIZ_NAME): $(HOTSPOT_SERVER_PATH)/$(JVM_DIZ_NAME)
	$(install-import-file)
  else
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDTRACE_DEBUGINFO_NAME): $(HOTSPOT_SERVER_PATH)/$(JVMDTRACE_DEBUGINFO_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDTRACE_DEBUGINFO_NAME): $(HOTSPOT_SERVER_PATH)/64/$(JVMDTRACE_DEBUGINFO_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_DEBUGINFO_NAME): $(HOTSPOT_SERVER_PATH)/$(JVM_DEBUGINFO_NAME)
	$(install-import-file)
  endif
endif

$(LIB_LOCATION)/$(SERVER_LOCATION)/Xusage.txt : $(HOTSPOT_SERVER_PATH)/Xusage.txt
	$(install-import-file)
endif

$(LIB_LOCATION)/$(CLIENT_LOCATION)/Xusage.txt : $(HOTSPOT_CLIENT_PATH)/Xusage.txt
	$(install-import-file)

$(LIB_LOCATION)/$(KERNEL_LOCATION)/Xusage.txt : $(HOTSPOT_KERNEL_PATH)/Xusage.txt
	$(install-file)

#
# Specific to non-OpenJDK building
#
ifndef OPENJDK

INTERNAL_IMPORT_LIST += \
            $(LIBDIR)/security/US_export_policy.jar \
            $(LIBDIR)/security/local_policy.jar \
            $(LIBDIR)/jce.jar

$(LIBDIR)/jce.jar: \
	    $(BUILDDIR)/closed/tools/crypto/jce/jce.jar
	$(install-file)
$(LIBDIR)/security/US_export_policy.jar: \
	    $(BUILDDIR)/closed/tools/crypto/jce/US_export_policy.jar
	$(install-file)
$(LIBDIR)/security/local_policy.jar: \
	    $(BUILDDIR)/closed/tools/crypto/jce/local_policy.jar
	$(install-file)

endif # OPENJDK

ADDJSUM_JARFILE = $(BUILDTOOLJARDIR)/addjsum.jar

# Construct classlist file
$(LIBDIR)/classlist: \
    $(CLASSSHARINGDATA_DIR)/classlist.$(PLATFORM) \
    $(ADDJSUM_JARFILE)
	@$(prep-target)
	@$(RM) -f $@.temp
	$(BOOT_JAVA_CMD) -jar $(ADDJSUM_JARFILE) \
	    $(CLASSSHARINGDATA_DIR)/classlist.$(PLATFORM) $@.temp
	$(MV) $@.temp $@

# Import internal files (ones that are stashed in this source tree)
import_internal_files : $(INTERNAL_IMPORT_LIST)

# Import files from the JDK that we are not building
import_files: $(IMPORT_LIST)

# Get component information variables and rules
include $(BUILDDIR)/common/internal/ImportComponents.gmk

# Security files we need to import
SEC_FILES_ZIP=$(ABS_BUILDDIR)/tools/crypto/sec-bin.zip
SEC_FILES_WIN_ZIP=$(ABS_BUILDDIR)/tools/crypto/sec-windows-bin.zip
JGSS_WIN32_FILES_ZIP=$(ABS_BUILDDIR)/tools/crypto/jgss-windows-i586-bin.zip
JGSS_WIN64_FILES_ZIP=$(ABS_BUILDDIR)/tools/crypto/jgss-windows-x64-bin.zip

# Unzip zip file $2 into directory $1 (if $2 exists)
#   Warning: $2 must be absolute path not relative
define SecUnzipper
if [ -f $2 ] ; then \
  $(MKDIR) -p $1; \
  $(ECHO) "( $(CD) $1 && $(UNZIP) -o $2 )"; \
  ( $(CD) $1 && $(UNZIP) -o $2 ); \
fi
endef

# If sec-bin exists, unpack it into the build directory
#   Also, the library recompile build indirectly depends on two SSL classes,
#   so copy those as well   FIXUP
#   if sec-windows-bin exists, unpack it into the build directory
#   if JGSS files exists, unpack it into the build directory
$(TEMPDIR)/security_imported:
	@$(prep-target)
	@$(call SecUnzipper,$(OUTPUTDIR),$(SEC_FILES_ZIP))
ifeq ($(PLATFORM), windows)
	@$(call SecUnzipper,$(OUTPUTDIR),$(SEC_FILES_WIN_ZIP))
endif
ifeq ($(PLATFORM), windows)
    ifeq ($(ARCH_DATA_MODEL), 32)
	@$(call SecUnzipper,$(OUTPUTDIR),$(JGSS_WIN32_FILES_ZIP))
    else
	@$(call SecUnzipper,$(OUTPUTDIR),$(JGSS_WIN64_FILES_ZIP))
    endif
endif
	@$(ECHO) "Imported on `$(DATE)`" > $@

# Import all files from other components
$(TEMPDIR)/components_imported:
	@$(prep-target)
	$(call import-component-binaries,$(ABS_OUTPUTDIR))
	$(call import-component-sources,$(IMPORTSRCDIR))
	$(call import-component-docs,$(IMPORTDOCDIR))
	$(call import-component-classes,$(CLASSDESTDIR))
	@$(ECHO) "Imported on `$(DATE)`" > $@

# Do pretty much everything
build : import_files \
	import_internal_files \
	$(TEMPDIR)/components_imported \
	$(TEMPDIR)/security_imported

# Clean up what we imported (except for component files)
clean clobber::
	$(RM) $(IMPORT_LIST)
	$(RM) $(INTERNAL_IMPORT_LIST)
	$(call import-component-sources-clean,$(IMPORTSRCDIR))
	$(call import-component-docs-clean,$(IMPORTDOCDIR))
	$(call import-component-classes-clean,$(CLASSDESTDIR))
	$(RM) $(TEMPDIR)/components_imported
	$(RM) $(TEMPDIR)/security_imported

.PHONY: import_files import_internal_files

