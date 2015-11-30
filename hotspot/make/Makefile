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

# Top level gnumake file for hotspot builds
#
# Default is to build the both product images and construct an export dir.
#  The default export directory name is `pwd`/export-$(PLATFORM).
#
#   Use: 'gnumake help' for more information.
#
# This makefile uses the default settings for where to find compilers and
#    tools, and obeys the ALT_* variable settings used by the other JDK
#    workspaces.
#

# Expected/optional make variables defined on make command line:
#  LP64=1 or ARCH_DATA_MODEL=64 for 64bit build
#
# Expected/optional make variables or environment variables:
#  ALT_SLASH_JAVA             Location of /java or J:
#  ALT_BOOTDIR                Previous JDK home directory for javac compiler
#  ALT_OUTPUTDIR              Output directory to use for hotspot build
#  ALT_EXPORT_PATH            Directory to export hotspot build to
#  ALT_JDK_IMPORT_PATH        Current JDK build (only for create_jdk rules)
#  ALT_JDK_TARGET_IMPORT_PATH Current JDK build when cross-compiling
#
# Version strings and numbers:
#  JDK_VERSION                Current JDK version (e.g. 1.6.0)
#  PREVIOUS_JDK_VERSION       Previous (bootdir) JDK version (e.g. 1.5.0)
#  FULL_VERSION               Full version string to use (e.g. "1.6.0-ea-b42")
#
# Version strings and numbers especially needed on Windows:
#  COOKED_JDK_UPDATE_VERSION  Just the update release number (e.g. 02)
#  COOKED_BUILD_NUMBER        Just the build number (e.g. 42)
#  JDK_MKTG_VERSION           Marketing JDK version (e.g. 6.0)
#  JDK_MAJOR_VERSION          Major number for version (e.g. 1) always 1?
#  JDK_MINOR_VERSION          Minor number for version (e.g. 6)
#  JDK_MICRO_VERSION          Micro number for version (e.g. 0)
#

# Default is build both product fastdebug and create export area

# Allow to build HotSpot in local directory from sources specified by GAMMADIR.
# After make/defs.make GAMMADIR is defined.
ifdef GAMMADIR
  ifndef ALT_OUTPUTDIR
    ALT_OUTPUTDIR := $(shell pwd)
  endif
  include $(GAMMADIR)/make/defs.make
else
  include defs.make
endif

include $(GAMMADIR)/make/altsrc.make

-include $(HS_ALT_MAKE)/Makefile.make

ifneq ($(ALT_OUTPUTDIR),)
  ALT_OUT=ALT_OUTPUTDIR=$(ALT_OUTPUTDIR)
else
  ALT_OUT=
endif

# Typical C1/C2 targets made available with this Makefile
C1_VM_TARGETS=product1 fastdebug1 optimized1 debug1
C2_VM_TARGETS=product  fastdebug  optimized  debug
CORE_VM_TARGETS=productcore fastdebugcore optimizedcore debugcore
ZERO_VM_TARGETS=productzero fastdebugzero optimizedzero debugzero
SHARK_VM_TARGETS=productshark fastdebugshark optimizedshark debugshark
MINIMAL1_VM_TARGETS=productminimal1 fastdebugminimal1 debugminimal1

COMMON_VM_PRODUCT_TARGETS=product product1 docs export_product
COMMON_VM_FASTDEBUG_TARGETS=fastdebug fastdebug1 docs export_fastdebug
COMMON_VM_DEBUG_TARGETS=debug debug1 docs export_debug
COMMON_VM_OPTIMIZED_TARGETS=optimized optimized1 docs export_optimized

# JDK directory list
JDK_DIRS=bin include lib demo

all:           all_product all_fastdebug

ifeq ($(JVM_VARIANT_MINIMAL1),true)
all_product:	productminimal1
all_fastdebug:	fastdebugminimal1
all_debug:	debugminimal1
endif

ifdef BUILD_CLIENT_ONLY
all_product:   product1 docs export_product
all_fastdebug: fastdebug1 docs export_fastdebug
all_debug:     debug1 docs export_debug
all_optimized: optimized1 docs export_optimized
else
ifeq ($(MACOSX_UNIVERSAL),true)
all_product:   universal_product
all_fastdebug: universal_fastdebug
all_debug:     universal_debug
all_optimized: universal_optimized
else
all_product:   $(COMMON_VM_PRODUCT_TARGETS)
all_fastdebug: $(COMMON_VM_FASTDEBUG_TARGETS)
all_debug:     $(COMMON_VM_DEBUG_TARGETS)
all_optimized: $(COMMON_VM_OPTIMIZED_TARGETS)
endif
endif

allzero:           all_productzero all_fastdebugzero
all_productzero:   productzero docs export_product
all_fastdebugzero: fastdebugzero docs export_fastdebug
all_debugzero:     debugzero docs export_debug
all_optimizedzero: optimizedzero docs export_optimized

allshark:           all_productshark all_fastdebugshark
all_productshark:   productshark docs export_product
all_fastdebugshark: fastdebugshark docs export_fastdebug
all_debugshark:     debugshark docs export_debug
all_optimizedshark: optimizedshark docs export_optimized

allcore:           all_productcore all_fastdebugcore
all_productcore:   productcore docs export_product
all_fastdebugcore: fastdebugcore docs export_fastdebug
all_debugcore:     debugcore docs export_debug
all_optimizedcore: optimizedcore docs export_optimized

# Do everything
world:         all create_jdk

# Build or export docs
docs:
ifeq ($(OSNAME),windows)
	@$(ECHO) "No docs ($(VM_TARGET)) for windows"
else
# We specify 'BUILD_FLAVOR=product' so that the proper
# ENABLE_FULL_DEBUG_SYMBOLS value is used.
	$(CD) $(OUTPUTDIR); \
	    $(MAKE) -f $(ABS_OS_MAKEFILE) \
		      $(MAKE_ARGS) BUILD_FLAVOR=product docs
endif

# Output directories
C1_DIR      =$(OUTPUTDIR)/$(VM_PLATFORM)_compiler1
C2_DIR      =$(OUTPUTDIR)/$(VM_PLATFORM)_compiler2
CORE_DIR    =$(OUTPUTDIR)/$(VM_PLATFORM)_core
MINIMAL1_DIR=$(OUTPUTDIR)/$(VM_PLATFORM)_minimal1
ZERO_DIR    =$(OUTPUTDIR)/$(VM_PLATFORM)_zero
SHARK_DIR   =$(OUTPUTDIR)/$(VM_PLATFORM)_shark

# Build variation of hotspot
$(C1_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) BUILD_DIR=$(C1_DIR) BUILD_FLAVOR=$(@:%1=%) VM_TARGET=$@ generic_build1 $(ALT_OUT)

$(C2_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) BUILD_DIR=$(C2_DIR) BUILD_FLAVOR=$@ VM_TARGET=$@ generic_build2 $(ALT_OUT)

$(CORE_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) BUILD_DIR=$(CORE_DIR) BUILD_FLAVOR=$(@:%core=%) VM_TARGET=$@ generic_buildcore $(ALT_OUT)

$(ZERO_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) BUILD_DIR=$(ZERO_DIR) BUILD_FLAVOR=$(@:%zero=%) VM_TARGET=$@ generic_buildzero $(ALT_OUT)

$(SHARK_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) BUILD_DIR=$(SHARK_DIR) BUILD_FLAVOR=$(@:%shark=%) VM_TARGET=$@ generic_buildshark $(ALT_OUT)

$(MINIMAL1_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) BUILD_DIR=$(MINIMAL1_DIR) BUILD_FLAVOR=$(@:%minimal1=%) VM_TARGET=$@ generic_buildminimal1 $(ALT_OUT)

# Install hotspot script in build directory
HOTSPOT_SCRIPT=$(BUILD_DIR)/$(BUILD_FLAVOR)/hotspot
$(HOTSPOT_SCRIPT): $(GAMMADIR)/make/hotspot.script
	$(QUIETLY) $(MKDIR) -p $(BUILD_DIR)/$(BUILD_FLAVOR)
	$(QUIETLY) cat $< | sed -e 's|@@LIBARCH@@|$(LIBARCH)|g' | sed -e 's|@@JDK_IMPORT_PATH@@|$(JDK_IMPORT_PATH)|g' > $@
	$(QUIETLY) chmod +x $@

# Build compiler1 (client) rule, different for platforms
generic_build1: $(HOTSPOT_SCRIPT)
	$(MKDIR) -p $(OUTPUTDIR)
ifeq ($(OSNAME),windows)
  ifeq ($(ARCH_DATA_MODEL), 32)
	$(CD) $(OUTPUTDIR); \
	    $(NMAKE) -f $(ABS_OS_MAKEFILE) \
		      Variant=compiler1 \
                      WorkSpace=$(ABS_GAMMADIR) \
		      BootStrapDir=$(ABS_BOOTDIR) \
                      BuildUser=$(USERNAME) \
		      $(MAKE_ARGS) $(VM_TARGET:%1=%)
  else
	@$(ECHO) "No compiler1 ($(VM_TARGET)) for ARCH_DATA_MODEL=$(ARCH_DATA_MODEL)"
  endif
else
  ifeq ($(ARCH_DATA_MODEL), 32)
	$(CD) $(OUTPUTDIR); \
	    $(MAKE) -f $(ABS_OS_MAKEFILE) \
		      $(MAKE_ARGS) $(VM_TARGET)
  else
	@$(ECHO) "No compiler1 ($(VM_TARGET)) for ARCH_DATA_MODEL=$(ARCH_DATA_MODEL)"
  endif
endif

# Build compiler2 (server) rule, different for platforms
generic_build2: $(HOTSPOT_SCRIPT)
	$(MKDIR) -p $(OUTPUTDIR)
ifeq ($(OSNAME),windows)
	$(CD) $(OUTPUTDIR); \
	    $(NMAKE) -f $(ABS_OS_MAKEFILE) \
		      Variant=compiler2 \
                      WorkSpace=$(ABS_GAMMADIR) \
		      BootStrapDir=$(ABS_BOOTDIR) \
                      BuildUser=$(USERNAME) \
		      $(MAKE_ARGS) $(VM_TARGET)
else
	$(CD) $(OUTPUTDIR); \
	    $(MAKE) -f $(ABS_OS_MAKEFILE) \
		      $(MAKE_ARGS) $(VM_TARGET)
endif

generic_buildcore: $(HOTSPOT_SCRIPT)
ifeq ($(HS_ARCH),ppc)
  ifeq ($(ARCH_DATA_MODEL),64)
	$(MKDIR) -p $(OUTPUTDIR)
	$(CD) $(OUTPUTDIR); \
		$(MAKE) -f $(ABS_OS_MAKEFILE) \
			$(MAKE_ARGS) $(VM_TARGET)
  else
	@$(ECHO) "No ($(VM_TARGET)) for ppc ARCH_DATA_MODEL=$(ARCH_DATA_MODEL)"
  endif
else
	@$(ECHO) "No ($(VM_TARGET)) for $(HS_ARCH)"
endif

generic_buildzero: $(HOTSPOT_SCRIPT)
	$(MKDIR) -p $(OUTPUTDIR)
	$(CD) $(OUTPUTDIR); \
		$(MAKE) -f $(ABS_OS_MAKEFILE) \
			$(MAKE_ARGS) $(VM_TARGET)

generic_buildshark: $(HOTSPOT_SCRIPT)
	$(MKDIR) -p $(OUTPUTDIR)
	$(CD) $(OUTPUTDIR); \
		$(MAKE) -f $(ABS_OS_MAKEFILE) \
			$(MAKE_ARGS) $(VM_TARGET)

generic_buildminimal1: $(HOTSPOT_SCRIPT)
ifeq ($(JVM_VARIANT_MINIMAL1),true)
	$(MKDIR) -p $(OUTPUTDIR)
  ifeq ($(ARCH_DATA_MODEL), 32)
    ifeq ($(OSNAME),windows)
	$(ECHO) "No ($(VM_TARGET)) for $(OSNAME) ARCH_DATA_MODEL=$(ARCH_DATA_MODEL)" ;
    else
      ifeq ($(OSNAME),solaris)
	$(ECHO) "No ($(VM_TARGET)) for $(OSNAME) ARCH_DATA_MODEL=$(ARCH_DATA_MODEL)" ;
      else
	$(CD) $(OUTPUTDIR); \
	$(MAKE) -f $(ABS_OS_MAKEFILE) $(MAKE_ARGS) $(VM_TARGET) ;
      endif
    endif
  else
	    @$(ECHO) "No ($(VM_TARGET)) for $(OSNAME) ARCH_DATA_MODEL=$(ARCH_DATA_MODEL)"
  endif
else
	@$(ECHO) "Error: trying to build a minimal target but JVM_VARIANT_MINIMAL1 is not true."
endif

remove_old_debuginfo:
ifeq ($(JVM_VARIANT_CLIENT), true)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
    ifeq ($(OSNAME), windows)
	  $(RM) -f $(EXPORT_CLIENT_DIR)/jvm.map $(EXPORT_CLIENT_DIR)/jvm.pdb
    else
	  $(RM) -f $(EXPORT_CLIENT_DIR)/libjvm.debuginfo
    endif
  else
	$(RM) -f $(EXPORT_CLIENT_DIR)/libjvm.diz
  endif
endif
ifeq ($(findstring true, $(JVM_VARIANT_SERVER) $(JVM_VARIANT_ZERO) $(JVM_VARIANT_ZEROSHARK)), true)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
    ifeq ($(OSNAME), windows)
	  $(RM) -f $(EXPORT_SERVER_DIR)/jvm.map $(EXPORT_SERVER_DIR)/jvm.pdb
    else
      ifeq ($(OS_VENDOR), Darwin)
	    $(RM) -rf $(EXPORT_SERVER_DIR)/libjvm.dylib.dSYM
      else
	    $(RM) -f $(EXPORT_SERVER_DIR)/libjvm.debuginfo
      endif
    endif
  else
	$(RM) -f $(EXPORT_SERVER_DIR)/libjvm.diz
  endif
endif
ifeq ($(JVM_VARIANT_MINIMAL1),true)
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(RM) -f $(EXPORT_MINIMAL_DIR)/libjvm.debuginfo
  else
	$(RM) -f $(EXPORT_MINIMAL_DIR)/libjvm.diz
  endif
endif

# Export file rule
generic_export: $(EXPORT_LIST) remove_old_debuginfo

export_product:
	$(MAKE) BUILD_FLAVOR=$(@:export_%=%) generic_export
export_fastdebug:
	$(MAKE) BUILD_FLAVOR=$(@:export_%=%) EXPORT_SUBDIR=/$(@:export_%=%) generic_export
export_debug:
	$(MAKE) BUILD_FLAVOR=$(@:export_%=%) EXPORT_SUBDIR=/$(@:export_%=%) generic_export
export_optimized:
	$(MAKE) BUILD_FLAVOR=$(@:export_%=%) EXPORT_SUBDIR=/$(@:export_%=%) generic_export

export_product_jdk::
	$(MAKE) BUILD_FLAVOR=$(@:export_%_jdk=%) ALT_EXPORT_PATH=$(JDK_IMAGE_DIR) generic_export
export_optimized_jdk::
	$(MAKE) BUILD_FLAVOR=$(@:export_%_jdk=%) ALT_EXPORT_PATH=$(JDK_IMAGE_DIR)/$(@:export_%_jdk=%) generic_export
export_fastdebug_jdk::
	$(MAKE) BUILD_FLAVOR=$(@:export_%_jdk=%) ALT_EXPORT_PATH=$(JDK_IMAGE_DIR)/$(@:export_%_jdk=%) generic_export
export_debug_jdk::
	$(MAKE) BUILD_FLAVOR=$(@:export_%_jdk=%) ALT_EXPORT_PATH=$(JDK_IMAGE_DIR)/$(@:export_%_jdk=%) generic_export

# Export file copy rules
XUSAGE=$(HS_SRC_DIR)/share/vm/Xusage.txt
DOCS_DIR=$(OUTPUTDIR)/$(VM_PLATFORM)_docs
C1_BUILD_DIR      =$(C1_DIR)/$(BUILD_FLAVOR)
C2_BUILD_DIR      =$(C2_DIR)/$(BUILD_FLAVOR)
CORE_BUILD_DIR    =$(CORE_DIR)/$(BUILD_FLAVOR)
MINIMAL1_BUILD_DIR=$(MINIMAL1_DIR)/$(BUILD_FLAVOR)
ZERO_BUILD_DIR    =$(ZERO_DIR)/$(BUILD_FLAVOR)
SHARK_BUILD_DIR   =$(SHARK_DIR)/$(BUILD_FLAVOR)

# Server (C2)
ifeq ($(JVM_VARIANT_SERVER), true)
# Common
$(EXPORT_SERVER_DIR)/%.diz:       		$(C2_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_LIB_DIR)/%.jar:			$(C2_BUILD_DIR)/../generated/%.jar
	$(install-file)
$(EXPORT_INCLUDE_DIR)/%:			$(C2_BUILD_DIR)/../generated/jvmtifiles/%
	$(install-file)
# Windows
$(EXPORT_SERVER_DIR)/%.dll:			$(C2_BUILD_DIR)/%.dll
	$(install-file)
$(EXPORT_SERVER_DIR)/%.pdb:			$(C2_BUILD_DIR)/%.pdb
	$(install-file)
$(EXPORT_SERVER_DIR)/%.map:			$(C2_BUILD_DIR)/%.map
	$(install-file)
$(EXPORT_LIB_DIR)/%.lib:			$(C2_BUILD_DIR)/%.lib
	$(install-file)
$(EXPORT_BIN_DIR)/%.diz:			$(C2_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_BIN_DIR)/%.dll:			$(C2_BUILD_DIR)/%.dll
	$(install-file)
$(EXPORT_BIN_DIR)/%.pdb:			$(C2_BUILD_DIR)/%.pdb
	$(install-file)
$(EXPORT_BIN_DIR)/%.map:			$(C2_BUILD_DIR)/%.map
	$(install-file)
# Unix
$(EXPORT_LIB_ARCH_DIR)/%.$(LIBRARY_SUFFIX): $(C2_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_SERVER_DIR)/%.$(LIBRARY_SUFFIX):       $(C2_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_SERVER_DIR)/64/%.$(LIBRARY_SUFFIX):    $(C2_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.debuginfo: 	$(C2_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_SERVER_DIR)/%.debuginfo:       	$(C2_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_SERVER_DIR)/64/%.debuginfo:    	$(C2_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.diz: 		$(C2_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_SERVER_DIR)/64/%.diz:    		$(C2_BUILD_DIR)/%.diz
	$(install-file)
# MacOS X
$(EXPORT_LIB_ARCH_DIR)/%.dSYM: 		$(C2_BUILD_DIR)/%.dSYM
	$(install-dir)
$(EXPORT_SERVER_DIR)/%.dSYM:       		$(C2_BUILD_DIR)/%.dSYM
	$(install-dir)
$(EXPORT_SERVER_DIR)/%.symbols:                 $(C2_BUILD_DIR)/%.symbols
	$(install-file)
endif

# Client (C1)
ifeq ($(JVM_VARIANT_CLIENT), true)
# Common
$(EXPORT_CLIENT_DIR)/%.diz:       		$(C1_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_LIB_DIR)/%.jar:			$(C1_BUILD_DIR)/../generated/%.jar
	$(install-file)
$(EXPORT_INCLUDE_DIR)/%:			$(C1_BUILD_DIR)/../generated/jvmtifiles/%
	$(install-file)
# Windows
$(EXPORT_CLIENT_DIR)/%.dll:			$(C1_BUILD_DIR)/%.dll
	$(install-file)
$(EXPORT_CLIENT_DIR)/%.pdb:			$(C1_BUILD_DIR)/%.pdb
	$(install-file)
$(EXPORT_CLIENT_DIR)/%.map:			$(C1_BUILD_DIR)/%.map
	$(install-file)
$(EXPORT_LIB_DIR)/%.lib:			$(C1_BUILD_DIR)/%.lib
	$(install-file)
$(EXPORT_BIN_DIR)/%.diz:			$(C1_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_BIN_DIR)/%.dll:			$(C1_BUILD_DIR)/%.dll
	$(install-file)
$(EXPORT_BIN_DIR)/%.pdb:			$(C1_BUILD_DIR)/%.pdb
	$(install-file)
$(EXPORT_BIN_DIR)/%.map:			$(C1_BUILD_DIR)/%.map
	$(install-file)
# Unix
$(EXPORT_LIB_ARCH_DIR)/%.$(LIBRARY_SUFFIX): $(C1_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_CLIENT_DIR)/%.$(LIBRARY_SUFFIX):       $(C1_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_CLIENT_DIR)/64/%.$(LIBRARY_SUFFIX):    $(C1_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.debuginfo: 	$(C1_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_CLIENT_DIR)/%.debuginfo:       	$(C1_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_CLIENT_DIR)/64/%.debuginfo:    	$(C1_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.diz: 		$(C1_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_CLIENT_DIR)/64/%.diz:    		$(C1_BUILD_DIR)/%.diz
	$(install-file)
# MacOS X
$(EXPORT_LIB_ARCH_DIR)/%.dSYM: 		$(C1_BUILD_DIR)/%.dSYM
	$(install-dir)
$(EXPORT_CLIENT_DIR)/%.dSYM:       		$(C1_BUILD_DIR)/%.dSYM
	$(install-dir)
endif

# Minimal1
ifeq ($(JVM_VARIANT_MINIMAL1), true)
# Common
$(EXPORT_MINIMAL_DIR)/%.diz:			$(MINIMAL1_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_LIB_DIR)/%.jar:			$(MINIMAL1_BUILD_DIR)/../generated/%.jar
	$(install-file)
$(EXPORT_INCLUDE_DIR)/%:			$(MINIMAL1_BUILD_DIR)/../generated/jvmtifiles/%
	$(install-file)
# Windows
$(EXPORT_MINIMAL_DIR)/%.dll:			$(MINIMAL1_BUILD_DIR)/%.dll
	$(install-file)
$(EXPORT_MINIMAL_DIR)/%.pdb:			$(MINIMAL1_BUILD_DIR)/%.pdb
	$(install-file)
$(EXPORT_MINIMAL_DIR)/%.map:			$(MINIMAL1_BUILD_DIR)/%.map
	$(install-file)
$(EXPORT_LIB_DIR)/%.lib:			$(MINIMAL1_BUILD_DIR)/%.lib
	$(install-file)
$(EXPORT_BIN_DIR)/%.diz:			$(MINIMAL1_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_BIN_DIR)/%.dll:			$(MINIMAL1_BUILD_DIR)/%.dll
	$(install-file)
$(EXPORT_BIN_DIR)/%.pdb:			$(MINIMAL1_BUILD_DIR)/%.pdb
	$(install-file)
$(EXPORT_BIN_DIR)/%.map:			$(MINIMAL1_BUILD_DIR)/%.map
	$(install-file)
# Unix
$(EXPORT_LIB_ARCH_DIR)/%.$(LIBRARY_SUFFIX):	$(MINIMAL1_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_MINIMAL_DIR)/%.$(LIBRARY_SUFFIX):	$(MINIMAL1_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_MINIMAL_DIR)/64/%.$(LIBRARY_SUFFIX):	$(MINIMAL1_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.debuginfo:		$(MINIMAL1_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_MINIMAL_DIR)/%.debuginfo:		$(MINIMAL1_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_MINIMAL_DIR)/64/%.debuginfo:		$(MINIMAL1_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.diz:		$(MINIMAL1_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_MINIMAL_DIR)/64/%.diz:			$(MINIMAL1_BUILD_DIR)/%.diz
	$(install-file)
# MacOS X does not support Minimal1 config
endif

# Zero
ifeq ($(JVM_VARIANT_ZERO), true)
# Common
$(EXPORT_LIB_DIR)/%.jar:			$(ZERO_BUILD_DIR)/../generated/%.jar
	$(install-file)
$(EXPORT_INCLUDE_DIR)/%:			$(ZERO_BUILD_DIR)/../generated/jvmtifiles/%
	$(install-file)
# Unix
$(EXPORT_LIB_ARCH_DIR)/%.$(LIBRARY_SUFFIX): $(ZERO_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.debuginfo:		$(ZERO_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.diz:		$(ZERO_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_SERVER_DIR)/%.$(LIBRARY_SUFFIX):       $(ZERO_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_SERVER_DIR)/%.debuginfo:		$(ZERO_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_SERVER_DIR)/%.diz:			$(ZERO_BUILD_DIR)/%.diz
	$(install-file)
# MacOS X
$(EXPORT_LIB_ARCH_DIR)/%.dSYM: 		$(ZERO_BUILD_DIR)/%.dSYM
	$(install-dir)
$(EXPORT_SERVER_DIR)/%.dSYM:			$(ZERO_BUILD_DIR)/%.dSYM
	$(install-dir)
endif

# Core
ifeq ($(JVM_VARIANT_CORE), true)
# Common
$(EXPORT_LIB_DIR)/%.jar:			$(CORE_BUILD_DIR)/../generated/%.jar
	$(install-file)
$(EXPORT_INCLUDE_DIR)/%:			$(CORE_BUILD_DIR)/../generated/jvmtifiles/%
	$(install-file)
# Unix
$(EXPORT_LIB_ARCH_DIR)/%.$(LIBRARY_SUFFIX):	$(CORE_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.debuginfo:		$(CORE_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.diz:		$(CORE_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_SERVER_DIR)/%.$(LIBRARY_SUFFIX):	$(CORE_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_SERVER_DIR)/%.debuginfo:		$(CORE_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_SERVER_DIR)/%.diz:			$(CORE_BUILD_DIR)/%.diz
	$(install-file)
endif

# Shark
ifeq ($(JVM_VARIANT_ZEROSHARK), true)
# Common
$(EXPORT_LIB_DIR)/%.jar:			$(SHARK_BUILD_DIR)/../generated/%.jar
	$(install-file)
$(EXPORT_INCLUDE_DIR)/%:			$(SHARK_BUILD_DIR)/../generated/jvmtifiles/%
	$(install-file)
# Unix
$(EXPORT_LIB_ARCH_DIR)/%.$(LIBRARY_SUFFIX): $(SHARK_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.debuginfo):	$(SHARK_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_LIB_ARCH_DIR)/%.diz:		$(SHARK_BUILD_DIR)/%.diz
	$(install-file)
$(EXPORT_SERVER_DIR)/%.$(LIBRARY_SUFFIX):       $(SHARK_BUILD_DIR)/%.$(LIBRARY_SUFFIX)
	$(install-file)
$(EXPORT_SERVER_DIR)/%.debuginfo:		$(SHARK_BUILD_DIR)/%.debuginfo
	$(install-file)
$(EXPORT_SERVER_DIR)/%.diz:			$(SHARK_BUILD_DIR)/%.diz
	$(install-file)
# MacOS X
$(EXPORT_LIB_ARCH_DIR)/%.dSYM: 		$(SHARK_BUILD_DIR)/%.dSYM
	$(install-dir)
$(EXPORT_SERVER_DIR)/%.dSYM:			$(SHARK_BUILD_DIR)/%.dSYM
	$(install-dir)
endif

$(EXPORT_INCLUDE_DIR)/%: $(HS_SRC_DIR)/share/vm/code/%
	$(install-file)

$(EXPORT_INCLUDE_DIR)/%: $(HS_SRC_DIR)/share/vm/prims/%
	$(install-file)

HS_JNI_ARCH_SRC=$(call altsrc-replace,$(HS_COMMON_SRC)/cpu/$(HS_ARCH)/vm/jni_$(HS_ARCH).h)
$(EXPORT_INCLUDE_DIR)/$(JDK_INCLUDE_SUBDIR)/jni_md.h: $(HS_JNI_ARCH_SRC)
	$(install-file)

$(EXPORT_INCLUDE_DIR)/%: $(HS_SRC_DIR)/share/vm/services/%
	$(install-file)

JFR_EXISTS=$(shell if [ -d $(HS_ALT_SRC) ]; then echo 1; else echo 0; fi)
# export jfr.h
ifeq ($JFR_EXISTS,1)
$(EXPORT_INCLUDE_DIR)/%: $(HS_ALT_SRC)/share/vm/jfr/%
	$(install-file)
else
$(EXPORT_INCLUDE_DIR)/jfr.h:
endif

# Doc files (jvmti.html)
$(EXPORT_DOCS_DIR)/platform/jvmti/%: $(DOCS_DIR)/%
	$(install-file)

# Xusage file
$(EXPORT_SERVER_DIR)/Xusage.txt $(EXPORT_CLIENT_DIR)/Xusage.txt $(EXPORT_MINIMAL_DIR)/Xusage.txt: $(XUSAGE)
	$(prep-target)
	$(RM) $@.temp
	$(SED) 's/\(separated by \)[;:]/\1$(PATH_SEP)/g' $< > $@.temp
	$(MV) $@.temp $@

#
# Clean rules
#
clobber clean: clean_build clean_export clean_jdk
clean_build:
	$(RM) -r $(C1_DIR)
	$(RM) -r $(C2_DIR)
	$(RM) -r $(CORE_DIR)
	$(RM) -r $(ZERO_DIR)
	$(RM) -r $(SHARK_DIR)
	$(RM) -r $(MINIMAL1_DIR)
clean_export:
	$(RM) -r $(EXPORT_PATH)
clean_jdk:
	$(RM) -r $(JDK_IMAGE_DIR)

#
# Create JDK and place this build into it
#
create_jdk: copy_jdk update_jdk

update_jdk: export_product_jdk export_fastdebug_jdk test_jdk

copy_jdk: $(JDK_IMAGE_DIR)/bin/java

$(JDK_IMAGE_DIR)/bin/java:
	$(RM) -r $(JDK_IMAGE_DIR)
	$(MKDIR) -p $(JDK_IMAGE_DIR)
	($(CD) $(JDK_IMPORT_PATH) && \
	 $(TAR) -cf - *) | \
	 ($(CD) $(JDK_IMAGE_DIR) && $(TAR) -xf -)


# Testing the built JVM
RUN_JVM=JAVA_HOME=$(JDK_IMPORT_PATH) $(JDK_IMPORT_PATH)/bin/java -d$(ARCH_DATA_MODEL) -XXaltjvm=$(ALTJVM_DIR) -Dsun.java.launcher.is_altjvm=true
generic_test:
	@$(ECHO) "Running with: $(ALTJVM_DIR)"
	@$(RUN_JVM) -Xinternalversion
	@$(RUN_JVM) -showversion -help

# C2 test targets
test_product test_optimized test_fastdebug test_debug:
	@$(MAKE) generic_test ALTJVM_DIR="$(C2_DIR)/$(@:test_%=%)"

# C1 test targets
test_product1 test_optimized1 test_fastdebug1 test_debug1:
  ifeq ($(ARCH_DATA_MODEL), 32)
	@$(MAKE) generic_test ALTJVM_DIR="$(C1_DIR)/$(@:test_%1=%)"
  else
	@$(ECHO) "No compiler1 ($(@:test_%=%)) for ARCH_DATA_MODEL=$(ARCH_DATA_MODEL)"
  endif

# Zero test targets
test_productzero test_optimizedzero test_fastdebugzero test_debugzero:
	@$(MAKE) generic_test ALTJVM_DIR="$(ZERO_DIR)/$(@:test_%zero=%)"

# Shark test targets
test_productshark test_optimizedshark test_fastdebugshark test_debugshark:
	@$(MAKE) generic_test ALTJVM_DIR="$(SHARK_DIR)/$(@:test_%shark=%)"

# Minimal1 test targets
test_productminimal1 test_optimizedminimal1 test_fastdebugminimal1 test_debugminimal1:
	@$(MAKE) generic_test ALTJVM_DIR="$(MINIMAL1_DIR)/$(@:test_%minimal1=%)"


test_jdk:
  ifeq ($(JVM_VARIANT_CLIENT), true)
	$(JDK_IMAGE_DIR)/bin/java -d$(ARCH_DATA_MODEL) -client -Xinternalversion
	$(JDK_IMAGE_DIR)/bin/java -d$(ARCH_DATA_MODEL) -client -version
  endif
  ifeq ($(findstring true, $(JVM_VARIANT_SERVER)\
		$(JVM_VARIANT_ZERO)$(JVM_VARIANT_ZEROSHARK)), true)
	$(JDK_IMAGE_DIR)/bin/java -d$(ARCH_DATA_MODEL) -server -Xinternalversion
	$(JDK_IMAGE_DIR)/bin/java -d$(ARCH_DATA_MODEL) -server -version
  endif

copy_product_jdk::
	$(RM) -r $(JDK_IMAGE_DIR)
	$(MKDIR) -p $(JDK_IMAGE_DIR)
	($(CD) $(JDK_IMPORT_PATH) && \
	 $(TAR) -cf - $(JDK_DIRS)) | \
	 ($(CD) $(JDK_IMAGE_DIR) && $(TAR) -xf -)

copy_fastdebug_jdk::
	$(RM) -r $(JDK_IMAGE_DIR)/fastdebug
	$(MKDIR) -p $(JDK_IMAGE_DIR)/fastdebug
	if [ -d $(JDK_IMPORT_PATH)/fastdebug ] ; then \
	  ($(CD) $(JDK_IMPORT_PATH)/fastdebug && \
	   $(TAR) -cf - $(JDK_DIRS)) | \
	   ($(CD) $(JDK_IMAGE_DIR)/fastdebug && $(TAR) -xf -) ; \
	else \
	  ($(CD) $(JDK_IMPORT_PATH) && \
	   $(TAR) -cf - $(JDK_DIRS)) | \
	   ($(CD) $(JDK_IMAGE_DIR)/fastdebug && $(TAR) -xf -) ; \
	fi

copy_debug_jdk::
	$(RM) -r $(JDK_IMAGE_DIR)/debug
	$(MKDIR) -p $(JDK_IMAGE_DIR)/debug
	if [ -d $(JDK_IMPORT_PATH)/debug ] ; then \
	  ($(CD) $(JDK_IMPORT_PATH)/debug && \
	   $(TAR) -cf - $(JDK_DIRS)) | \
	   ($(CD) $(JDK_IMAGE_DIR)/debug && $(TAR) -xf -) ; \
	elif [ -d $(JDK_IMPORT_PATH)/fastdebug ] ; then \
	  ($(CD) $(JDK_IMPORT_PATH)/fastdebug && \
	   $(TAR) -cf - $(JDK_DIRS)) | \
	   ($(CD) $(JDK_IMAGE_DIR)/debug && $(TAR) -xf -) ; \
	else \
	  ($(CD) $(JDK_IMPORT_PATH) && \
	   $(TAR) -cf - $(JDK_DIRS)) | \
	   ($(CD) $(JDK_IMAGE_DIR)/debug && $(TAR) -xf -) ; \
	fi

copy_optimized_jdk::
	$(RM) -r $(JDK_IMAGE_DIR)/optimized
	$(MKDIR) -p $(JDK_IMAGE_DIR)/optimized
	if [ -d $(JDK_IMPORT_PATH)/optimized ] ; then \
	  ($(CD) $(JDK_IMPORT_PATH)/optimized && \
	   $(TAR) -cf - $(JDK_DIRS)) | \
	   ($(CD) $(JDK_IMAGE_DIR)/optimized && $(TAR) -xf -) ; \
	else \
	  ($(CD) $(JDK_IMPORT_PATH) && \
	   $(TAR) -cf - $(JDK_DIRS)) | \
	   ($(CD) $(JDK_IMAGE_DIR)/optimized && $(TAR) -xf -) ; \
	fi

#
# Check target
#
check: variable_check

#
# Help target
#
help: intro_help target_help variable_help notes_help examples_help

# Intro help message
intro_help:
	@$(ECHO) \
"Makefile for the Hotspot workspace."
	@$(ECHO) \
"Default behavior is to build and create an export area for the j2se builds."

# Target help
target_help:
	@$(ECHO) "help:             This help message"
	@$(ECHO) "all:              Same as: all_product all_fastdebug"
	@$(ECHO) "world:            Same as: all create_jdk"
	@$(ECHO) "all_product:      Same as: product product1 export_product"
	@$(ECHO) "all_fastdebug:    Same as: fastdebug fastdebug1 export_fastdebug"
	@$(ECHO) "all_debug:        Same as: debug debug1 export_debug"
	@$(ECHO) "all_optimized:    Same as: optimized optimized1 export_optimized"
	@$(ECHO) "clean:            Clean all areas"
	@$(ECHO) "export_product:   Export product files to EXPORT_PATH"
	@$(ECHO) "export_fastdebug: Export fastdebug files to EXPORT_PATH"
	@$(ECHO) "export_debug:     Export debug files to EXPORT_PATH"
	@$(ECHO) "export_optimized: Export optimized files to EXPORT_PATH"
	@$(ECHO) "create_jdk:       Create JDK image, export all files into it"
	@$(ECHO) "update_jdk:       Update JDK image with fresh exported files"
	@$(ECHO) " "
	@$(ECHO) "Other targets are:"
	@$(ECHO) "   $(C1_VM_TARGETS)"
	@$(ECHO) "   $(C2_VM_TARGETS)"
	@$(ECHO) "   $(MINIMAL1_VM_TARGETS)"

# Variable help (only common ones used by this workspace)
variable_help: variable_help_intro variable_list variable_help_end
variable_help_intro:
	@$(ECHO) "--- Common Variables ---"
variable_help_end:
	@$(ECHO) " "
	@$(ECHO) "--- Make Arguments ---"
	@$(ECHO) "MAKE_ARGS=$(MAKE_ARGS)"

# One line descriptions for the variables
SLASH_JAVA.desc            = Root of all build tools, e.g. /java or J:
OUTPUTDIR.desc             = Output directory, default is build/<osname>
BOOTDIR.desc               = JDK used to compile agent java source and test with
JDK_IMPORT_PATH.desc       = Promoted JDK to copy for 'create_jdk'
JDK_IMAGE_DIR.desc         = Directory to place JDK to copy
EXPORT_PATH.desc           = Directory to place files to export for JDK build

# Make variables to print out (description and value)
VARIABLE_PRINTVAL_LIST +=       \
    SLASH_JAVA                  \
    OUTPUTDIR                   \
    BOOTDIR                     \
    JDK_IMPORT_PATH             \
    JDK_IMAGE_DIR               \
    EXPORT_PATH

# Make variables that should refer to directories that exist
VARIABLE_CHECKDIR_LIST +=       \
    SLASH_JAVA                  \
    BOOTDIR                     \
    JDK_IMPORT_PATH

# For pattern rules below, so all are treated the same
DO_PRINTVAL_LIST=$(VARIABLE_PRINTVAL_LIST:%=%.printval)
DO_CHECKDIR_LIST=$(VARIABLE_CHECKDIR_LIST:%=%.checkdir)

# Complete variable check
variable_check: $(DO_CHECKDIR_LIST)
variable_list: $(DO_PRINTVAL_LIST) variable_check

# Pattern rule for printing out a variable
%.printval:
	@$(ECHO) "  ALT_$* - $($*.desc)"
	@$(ECHO) "        $*=$($*)"

# Pattern rule for checking to see if a variable with a directory exists
%.checkdir:
	@if [ ! -d $($*) ] ; then \
	    $(ECHO) "WARNING: $* does not exist, try $(MAKE) sanity"; \
	fi

# Pattern rule for checking to see if a variable with a file exists
%.checkfil:
	@if [ ! -f $($*) ] ; then \
	    $(ECHO) "WARNING: $* does not exist, try $(MAKE) sanity"; \
	fi

# Misc notes on help
notes_help:
	@$(ECHO) \
"--- Notes --- "
	@$(ECHO) \
"- JDK_IMPORT_PATH must refer to a compatible build, not all past promoted"
	@$(ECHO) \
"        builds or previous release JDK builds will work."
	@$(ECHO) \
"- The fastest builds have been when the workspace and the BOOTDIR are on"
	@$(ECHO) \
"        local disk."

examples_help:
	@$(ECHO) \
"--- Examples --- "
	@$(ECHO) \
"  $(MAKE) all"
	@$(ECHO) \
"  $(MAKE) world"
	@$(ECHO) \
"  $(MAKE) ALT_BOOTDIR=/opt/java/jdk$(PREVIOUS_JDK_VERSION)"
	@$(ECHO) \
"  $(MAKE) ALT_JDK_IMPORT_PATH=/opt/java/jdk$(JDK_VERSION)"

# Universal build support
ifeq ($(OS_VENDOR), Darwin)
ifeq ($(MACOSX_UNIVERSAL),true)
include $(GAMMADIR)/make/$(OSNAME)/makefiles/universal.gmk
endif
endif

# Compatibility for transition to new naming
warn_jvmg_deprecated:
	echo "Warning: The jvmg target has been replaced with debug"
	echo "Warning: Please update your usage"

jvmg: warn_jvmg_deprecated debug

jvmg1: warn_jvmg_deprecated debug1

jvmgminimal1: warn_jvmg_deprecated debugminimal1

jvmgcore: warn_jvmg_deprecated debugcore

jvmgzero: warn_jvmg_deprecated debugzero

jvmgshark: warn_jvmg_deprecated debugshark

# JPRT rule to build this workspace
include $(GAMMADIR)/make/jprt.gmk

.PHONY: all world clobber clean help $(C1_VM_TARGETS) $(C2_VM_TARGETS) \
        $(MINIMAL1_VM_TARGETS) \
	generic_build1 generic_build2 generic_buildminimal1 generic_export \
	export_product export_fastdebug export_debug export_optimized \
	export_jdk_product export_jdk_fastdebug export_jdk_debug \
	create_jdk copy_jdk update_jdk test_jdk \
	copy_product_jdk copy_fastdebug_jdk copy_debug_jdk  \
	$(HS_ALT_MAKE)/Makefile.make remove_old_debuginfo

.NOTPARALLEL:
