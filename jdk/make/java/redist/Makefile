#
# Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

ifeq ($(PLATFORM), windows)
  LIB_LOCATION = $(BINDIR)
else
  LIB_LOCATION = $(LIBDIR)/$(LIBARCH)
endif

JVM_NAME               = $(LIB_PREFIX)jvm.$(LIBRARY_SUFFIX)
JVMLIB_NAME            = $(LIB_PREFIX)jvm.$(LIB_SUFFIX)
JVMMAP_NAME            = $(LIB_PREFIX)jvm.map
JVMPDB_NAME            = $(LIB_PREFIX)jvm.pdb
LIBJSIG_NAME           = $(LIB_PREFIX)jsig.$(LIBRARY_SUFFIX)
JVMDB_NAME             = $(LIB_PREFIX)jvm$(DB_SUFFIX).$(LIBRARY_SUFFIX)

CLASSSHARINGDATA_DIR   = $(BUILDDIR)/tools/sharing

# Needed to do file copy
ABS_BUILDDIR :=$(call FullPath,$(BUILDDIR))

all: build

# List of files created here or coming from BUILDDIR area (this workspace)
INTERNAL_IMPORT_LIST = $(LIBDIR)/classlist

# List of files coming from outside this workspace
IMPORT_LIST = $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_NAME) \
              $(LIB_LOCATION)/$(SERVER_LOCATION)/Xusage.txt

# INCLUDE_SA is false on platforms where SA is not supported.
# On platforms where it is supported, we want to allow it to
# not be present, at least temporarily.  So,
# if the SA files (well, just sa-jdi.jar) do not exist 
# in the HOTSPOT_IMPORT_PATH, then we won't build SA.
SA_EXISTS := $(shell if [ -r $(HOTSPOT_IMPORT_PATH)/lib/sa-jdi.jar ] ; then \
                          $(ECHO) true; \
                        else \
                          $(ECHO) false; \
                        fi)

ifeq ($(SA_EXISTS), false)
  INCLUDE_SA := false
endif

ifeq ($(INCLUDE_SA), true)
  IMPORT_LIST += $(LIBDIR)/sa-jdi.jar \
                 $(LIB_LOCATION)/$(SALIB_NAME)
  ifeq ($(PLATFORM), windows)
    IMPORT_LIST += $(LIB_LOCATION)/$(SAMAP_NAME) \
                   $(LIB_LOCATION)/$(SAPDB_NAME)
  endif
endif # INCLUDE_SA

# Hotspot client is only available on 32-bit non-Zero builds
ifneq ($(ZERO_BUILD), true)
ifeq ($(ARCH_DATA_MODEL), 32)
  IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_NAME) \
                 $(LIB_LOCATION)/$(CLIENT_LOCATION)/Xusage.txt
endif
endif

ifeq ($(PLATFORM), windows)
#  Windows     vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv  Windows

IMPORT_LIST += $(MS_RUNTIME_LIBRARIES:%=$(BINDIR)/%)

# NOTE: These might actually come from BUILDDIR, depends on the settings.
$(BINDIR)/msvcrt.dll: $(MSVCRT_DLL_PATH)/msvcrt.dll
	$(install-import-file)
	$(CHMOD) a+x $@
$(BINDIR)/$(MSVCRNN_DLL): $(MSVCRNN_DLL_PATH)/$(MSVCRNN_DLL)
	$(install-import-file)
	$(CHMOD) a+x $@

# Get the hotspot .map and .pdb files for client and server
IMPORT_LIST += \
	$(LIBDIR)/$(JVMLIB_NAME) \
	$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMMAP_NAME) \
	$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMPDB_NAME)

# Add .map and .pdb files to the import path for client and kernel VMs. 
# These are only available on 32-bit windows builds. 
ifeq ($(ARCH_DATA_MODEL), 32)
  IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMMAP_NAME) \
                 $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMPDB_NAME)
  ifeq ($(DO_KERNEL), true)
    IMPORT_LIST += $(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVMMAP_NAME) \
                   $(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVMPDB_NAME)
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

$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMMAP_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_SERVER_PATH)/$(JVMMAP_NAME) $@

$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMPDB_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_CLIENT_PATH)/$(JVMPDB_NAME)  $@

$(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVMPDB_NAME):
	@$(prep-target)
	-$(CP) $(HOTSPOT_KERNEL_PATH)/$(JVMPDB_NAME)  $@

$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMPDB_NAME): 
	@$(prep-target)
	-$(CP) $(HOTSPOT_SERVER_PATH)/$(JVMPDB_NAME) $@

#  Windows     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^  Windows
else # PLATFORM
#  NOT Windows vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv  NOT Windows

IMPORT_LIST += \
        $(LIB_LOCATION)/$(LIBJSIG_NAME) \
	$(LIB_LOCATION)/$(SERVER_LOCATION)/$(LIBJSIG_NAME)

ifeq ($(PLATFORM), solaris)
  IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDB_NAME)
endif 

ifneq ($(ZERO_BUILD), true)
ifeq ($(ARCH_DATA_MODEL), 32)

IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_NAME)

ifeq ($(PLATFORM), solaris)
#  solaris   vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv  solaris

ifeq ($(ARCH), sparc)
  IMPORT_LIST += $(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDB_NAME)
endif

IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDB_NAME)

ifeq ($(ARCH), sparc)
  IMPORT_LIST += $(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDB_NAME)
endif

# For backwards compatability, make a link of the 32-bit client JVM to $(LIBDIR)
IMPORT_LIST += $(LIB_LOCATION)/$(JVM_NAME)

# create a link from lib/libjvm.so to client/libjvm.so
$(LIB_LOCATION)/$(JVM_NAME): $(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_NAME)
	@$(prep-target)
	$(LN) -s $(CLIENT_LOCATION)/$(JVM_NAME) $@

#  solaris   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ solaris
endif # 32bit solaris

endif # 32bit

endif # ZERO_BUILD

#  NOT Windows ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ NOT Windows

endif # PLATFORM

$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVM_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVM_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(KERNEL_LOCATION)/$(JVM_NAME): $(HOTSPOT_KERNEL_PATH)/$(JVM_NAME)
	$(install-file)

$(LIB_LOCATION)/$(LIBJSIG_NAME): $(HOTSPOT_SERVER_PATH)/$(LIBJSIG_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(LIBJSIG_NAME) \
$(LIB_LOCATION)/$(SERVER_LOCATION)/$(LIBJSIG_NAME):
	@$(prep-target)
	$(LN) -s ../$(LIBJSIG_NAME) $@

$(LIB_LOCATION)/$(CLIENT_LOCATION)/$(JVMDB_NAME): $(HOTSPOT_CLIENT_PATH)/$(JVMDB_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(CLIENT_LOCATION)/64/$(JVMDB_NAME): $(HOTSPOT_CLIENT_PATH)/64/$(JVMDB_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVMDB_NAME): $(HOTSPOT_SERVER_PATH)/$(JVMDB_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/64/$(JVMDB_NAME): $(HOTSPOT_SERVER_PATH)/64/$(JVMDB_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/$(JVM_NAME): $(HOTSPOT_SERVER_PATH)/$(JVM_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SERVER_LOCATION)/Xusage.txt : $(HOTSPOT_SERVER_PATH)/Xusage.txt
	$(install-import-file)

$(LIB_LOCATION)/$(CLIENT_LOCATION)/Xusage.txt : $(HOTSPOT_CLIENT_PATH)/Xusage.txt
	$(install-import-file)

$(LIB_LOCATION)/$(KERNEL_LOCATION)/Xusage.txt : $(HOTSPOT_KERNEL_PATH)/Xusage.txt
	$(install-file)

ifeq ($(INCLUDE_SA), true)
# The Serviceability Agent is built in the Hotspot workspace.
# It contains two files:
#  - sa-jdi.jar:  This goes into the same dir as tools.jar.
#  - a shared library:  sawindbg.dll on windows / libproc.sa on unix
#		        This goes into the same dir as the other
#			shared libs, eg. libjdwp.so.
$(LIBDIR)/sa-jdi.jar: $(HOTSPOT_IMPORT_PATH)/lib/sa-jdi.jar
	$(install-import-file)

$(LIB_LOCATION)/$(SALIB_NAME): $(HOTSPOT_SALIB_PATH)/$(SALIB_NAME)
	$(install-import-file)

ifeq ($(PLATFORM), windows)
$(LIB_LOCATION)/$(SAPDB_NAME): $(HOTSPOT_SALIB_PATH)/$(SAPDB_NAME)
	$(install-import-file)

$(LIB_LOCATION)/$(SAMAP_NAME): $(HOTSPOT_SALIB_PATH)/$(SAMAP_NAME)
	$(install-import-file)
endif # windows
endif # INCLUDE_SA

#
# Specific to OpenJDK building
#
ifdef OPENJDK

  ifeq ($(IMPORT_BINARY_PLUGS),true)
    include $(BUILDDIR)/common/internal/BinaryPlugs.gmk

    build: import-binary-plugs
  endif

else # !OPENJDK

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

ifndef OPENJDK

# Lucida font files are not included in the OpenJDK distribution.
# Get names of font files
include FILES.gmk

# Copy font files into OUTPUTDIR area

FONTFILES = $(SHARED_FONTFILES)
FONTSDIR  = $(LIBDIR)/fonts
FONTSDIRFILE  = $(FONTSDIR)/fonts.dir
INTERNAL_IMPORT_LIST += $(FONTFILES) 

ifneq ($(PLATFORM), windows)
  INTERNAL_IMPORT_LIST += $(FONTSDIRFILE)
endif

$(LIBDIR)/fonts/%.ttf: $(CLOSED_SRC)/share/lib/fonts/%.ttf
	$(install-file)

$(FONTSDIRFILE): $(PLATFORM_SRC)/classes/sun/awt/motif/java.fonts.dir
	$(install-file)

ifeq ($(PLATFORM), linux)

# The oblique fonts are only needed/wanted on Linux.

OBLFONTSDIR  = $(LIBDIR)/oblique-fonts
OBLFONTSDIRFILE	 = $(OBLFONTSDIR)/fonts.dir
INTERNAL_IMPORT_LIST += $(OBLIQUE_FONTFILES) $(OBLFONTSDIRFILE)

$(LIBDIR)/oblique-fonts/%.ttf: $(CLOSED_SRC)/share/lib/fonts/oblique/%.ttf
	$(install-file)

$(OBLFONTSDIRFILE): $(PLATFORM_SRC)/classes/sun/awt/motif/java.oblique-fonts.dir
	$(install-file)

endif # linux
endif # !OPENJDK

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

