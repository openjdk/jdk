#
# Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
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
#  ALT_BUILD_WIN_SA           Building SA on Windows is disabled by default.
#                             Set ALT_BUILD_WIN_SA=1 to enable building SA on
#                             Windows.
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


ifneq ($(ALT_OUTPUTDIR),)
  ALT_OUT=ALT_OUTPUTDIR=$(ALT_OUTPUTDIR)
else
  ALT_OUT=
endif

# Typical C1/C2 targets made available with this Makefile
C1_VM_TARGETS=product1 fastdebug1 optimized1 jvmg1
C2_VM_TARGETS=product  fastdebug  optimized  jvmg
KERNEL_VM_TARGETS=productkernel fastdebugkernel optimizedkernel jvmgkernel
ZERO_VM_TARGETS=productzero fastdebugzero optimizedzero jvmgzero

# JDK directory list
JDK_DIRS=bin include jre lib demo

all:           all_product all_fastdebug
all_product:   product product1 productkernel docs export_product
all_fastdebug: fastdebug fastdebug1 fastdebugkernel docs export_fastdebug
all_debug:     jvmg jvmg1 jvmgkernel docs export_debug
all_optimized: optimized optimized1 optimizedkernel docs export_optimized

allzero:           all_productzero all_fastdebugzero
all_productzero:   productzero docs export_product
all_fastdebugzero: fastdebugzero docs export_fastdebug
all_debugzero:     jvmgzero docs export_debug
all_optimizedzero: optimizedzero docs export_optimized

# Do everything
world:         all create_jdk

# Build or export docs
docs:
ifeq ($(OSNAME),windows)
	@$(ECHO) "No docs ($(VM_TARGET)) for windows"
else
	$(CD) $(OUTPUTDIR); \
	    $(MAKE) -f $(ABS_OS_MAKEFILE) \
		      $(MAKE_ARGS) docs
endif

# Build variation of hotspot
$(C1_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) VM_TARGET=$@ generic_build1 $(ALT_OUT)

$(C2_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) VM_TARGET=$@ generic_build2 $(ALT_OUT)

$(KERNEL_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) VM_TARGET=$@ generic_buildkernel $(ALT_OUT)

$(ZERO_VM_TARGETS):
	$(CD) $(GAMMADIR)/make; \
	$(MAKE) VM_TARGET=$@ generic_buildzero $(ALT_OUT)

# Build compiler1 (client) rule, different for platforms
generic_build1:
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
generic_build2:
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

generic_buildkernel:
	$(MKDIR) -p $(OUTPUTDIR)
ifeq ($(OSNAME),windows)
  ifeq ($(ARCH_DATA_MODEL), 32)
	$(CD) $(OUTPUTDIR); \
	    $(NMAKE) -f $(ABS_OS_MAKEFILE) \
		      Variant=kernel \
                      WorkSpace=$(ABS_GAMMADIR) \
		      BootStrapDir=$(ABS_BOOTDIR) \
                      BuildUser=$(USERNAME) \
		      $(MAKE_ARGS) $(VM_TARGET:%kernel=%)
  else
	@$(ECHO) "No kernel ($(VM_TARGET)) for ARCH_DATA_MODEL=$(ARCH_DATA_MODEL)"
  endif
else
	@$(ECHO) "No kernel ($(VM_TARGET)) for OS_NAME=$(OSNAME)"
endif

generic_buildzero:
	$(MKDIR) -p $(OUTPUTDIR)
	$(CD) $(OUTPUTDIR); \
		$(MAKE) -f $(ABS_OS_MAKEFILE) \
			$(MAKE_ARGS) $(VM_TARGET)

# Export file rule
generic_export: $(EXPORT_LIST)
export_product:
	$(MAKE) VM_SUBDIR=product                            generic_export
export_fastdebug:
	$(MAKE) VM_SUBDIR=fastdebug EXPORT_SUBDIR=/fastdebug generic_export
export_debug:
	$(MAKE) VM_SUBDIR=${VM_DEBUG} EXPORT_SUBDIR=/debug   generic_export
export_optimized:
	$(MAKE) VM_SUBDIR=optimized EXPORT_SUBDIR=/optimized generic_export
export_product_jdk:
	$(MAKE) ALT_EXPORT_PATH=$(JDK_IMAGE_DIR) \
		VM_SUBDIR=product                            generic_export
export_optimized_jdk:
	$(MAKE) ALT_EXPORT_PATH=$(JDK_IMAGE_DIR) \
		VM_SUBDIR=optimized                          generic_export
export_fastdebug_jdk:
	$(MAKE) ALT_EXPORT_PATH=$(JDK_IMAGE_DIR)/fastdebug \
		VM_SUBDIR=fastdebug                          generic_export
export_debug_jdk:
	$(MAKE) ALT_EXPORT_PATH=$(JDK_IMAGE_DIR)/debug \
		VM_SUBDIR=${VM_DEBUG}                        generic_export


# Export file copy rules
XUSAGE=$(HS_SRC_DIR)/share/vm/Xusage.txt
DOCS_DIR=$(OUTPUTDIR)/$(VM_PLATFORM)_docs
C1_BASE_DIR=$(OUTPUTDIR)/$(VM_PLATFORM)_compiler1
C2_BASE_DIR=$(OUTPUTDIR)/$(VM_PLATFORM)_compiler2
KERNEL_BASE_DIR=$(OUTPUTDIR)/$(VM_PLATFORM)_kernel
ZERO_BASE_DIR=$(OUTPUTDIR)/$(VM_PLATFORM)_zero
C1_DIR=$(C1_BASE_DIR)/$(VM_SUBDIR)
C2_DIR=$(C2_BASE_DIR)/$(VM_SUBDIR)
KERNEL_DIR=$(KERNEL_BASE_DIR)/$(VM_SUBDIR)
ZERO_DIR=$(ZERO_BASE_DIR)/$(VM_SUBDIR)

# Misc files and generated files need to come from C1 or C2 area
ifeq ($(ZERO_BUILD), true)
  MISC_DIR=$(ZERO_DIR)
  GEN_DIR=$(ZERO_BASE_DIR)/generated
else
ifeq ($(ARCH_DATA_MODEL), 32)
  MISC_DIR=$(C1_DIR)
  GEN_DIR=$(C1_BASE_DIR)/generated
else
  MISC_DIR=$(C2_DIR)
  GEN_DIR=$(C2_BASE_DIR)/generated
endif
endif

# Bin files (windows)
ifeq ($(OSNAME),windows)

# Get jvm.lib 
$(EXPORT_LIB_DIR)/%.lib:  $(MISC_DIR)/%.lib
	$(install-file)

# Other libraries (like SA)
$(EXPORT_JRE_BIN_DIR)/%.dll: $(MISC_DIR)/%.dll
	$(install-file)
$(EXPORT_JRE_BIN_DIR)/%.pdb: $(MISC_DIR)/%.pdb
	$(install-file)
$(EXPORT_JRE_BIN_DIR)/%.map: $(MISC_DIR)/%.map
	$(install-file)

# Client files always come from C1 area
$(EXPORT_CLIENT_DIR)/%.dll:  $(C1_DIR)/%.dll
	$(install-file)
$(EXPORT_CLIENT_DIR)/%.pdb:  $(C1_DIR)/%.pdb
	$(install-file)
$(EXPORT_CLIENT_DIR)/%.map:  $(C1_DIR)/%.map
	$(install-file)

# Server files always come from C2 area
$(EXPORT_SERVER_DIR)/%.dll:  $(C2_DIR)/%.dll
	$(install-file)
$(EXPORT_SERVER_DIR)/%.pdb:  $(C2_DIR)/%.pdb
	$(install-file)
$(EXPORT_SERVER_DIR)/%.map:  $(C2_DIR)/%.map
	$(install-file)

# Kernel files always come from kernel area
$(EXPORT_KERNEL_DIR)/%.dll:  $(KERNEL_DIR)/%.dll
	$(install-file)
$(EXPORT_KERNEL_DIR)/%.pdb:  $(KERNEL_DIR)/%.pdb
	$(install-file)
$(EXPORT_KERNEL_DIR)/%.map:  $(KERNEL_DIR)/%.map
	$(install-file)
endif

# Shared Library
ifneq ($(OSNAME),windows)
  ifeq ($(ZERO_BUILD), true)
$(EXPORT_JRE_LIB_ARCH_DIR)/%.so: $(ZERO_DIR)/%.so
	$(install-file)
$(EXPORT_SERVER_DIR)/%.so:       $(ZERO_DIR)/%.so
	$(install-file)
  else
$(EXPORT_JRE_LIB_ARCH_DIR)/%.so: $(C2_DIR)/%.so
	$(install-file)
$(EXPORT_CLIENT_DIR)/%.so:       $(C1_DIR)/%.so
	$(install-file)
$(EXPORT_CLIENT_DIR)/64/%.so:    $(C1_DIR)/%.so
	$(install-file)
$(EXPORT_SERVER_DIR)/%.so:       $(C2_DIR)/%.so
	$(install-file)
$(EXPORT_SERVER_DIR)/64/%.so:    $(C2_DIR)/%.so
	$(install-file)
  endif
endif

# Jar file (sa-jdi.jar)
$(EXPORT_LIB_DIR)/%.jar: $(GEN_DIR)/%.jar
	$(install-file)

# Include files (jvmti.h, jni.h, $(JDK_INCLUDE_SUBDIR)/jni_md.h, jmm.h)
$(EXPORT_INCLUDE_DIR)/%: $(GEN_DIR)/jvmtifiles/%
	$(install-file)

$(EXPORT_INCLUDE_DIR)/%: $(HS_SRC_DIR)/share/vm/prims/%
	$(install-file)

$(EXPORT_INCLUDE_DIR)/$(JDK_INCLUDE_SUBDIR)/jni_md.h:  $(HS_SRC_DIR)/cpu/$(HS_ARCH)/vm/jni_$(HS_ARCH).h
	$(install-file)

$(EXPORT_INCLUDE_DIR)/%: $(HS_SRC_DIR)/share/vm/services/%
	$(install-file)

# Doc files (jvmti.html)
$(EXPORT_DOCS_DIR)/platform/jvmti/%: $(DOCS_DIR)/%
	$(install-file)

# Xusage file
$(EXPORT_SERVER_DIR)/Xusage.txt $(EXPORT_CLIENT_DIR)/Xusage.txt $(EXPORT_KERNEL_DIR)/Xusage.txt: $(XUSAGE)
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
	$(RM) -r $(KERNEL_DIR)
	$(RM) -r $(ZERO_DIR)
clean_export:
	$(RM) -r $(EXPORT_PATH)
clean_jdk:
	$(RM) -r $(JDK_IMAGE_DIR)

#
# Create JDK and place this build into it
#
create_jdk: copy_jdk update_jdk

update_jdk: export_product_jdk export_fastdebug_jdk test_jdk

copy_jdk: $(JDK_IMAGE_DIR)/jre/lib/rt.jar

$(JDK_IMAGE_DIR)/jre/lib/rt.jar:
	$(RM) -r $(JDK_IMAGE_DIR)
	$(MKDIR) -p $(JDK_IMAGE_DIR)
	($(CD) $(JDK_IMPORT_PATH) && \
	 $(TAR) -cf - *) | \
	 ($(CD) $(JDK_IMAGE_DIR) && $(TAR) -xf -)

test_jdk:
  ifneq ($(ZERO_BUILD), true)
    ifeq ($(ARCH_DATA_MODEL), 32)
	$(JDK_IMAGE_DIR)/bin/java -client -version
    endif
  endif
	$(JDK_IMAGE_DIR)/bin/java -server -version

copy_product_jdk:
	$(RM) -r $(JDK_IMAGE_DIR)
	$(MKDIR) -p $(JDK_IMAGE_DIR)
	($(CD) $(JDK_IMPORT_PATH) && \
	 $(TAR) -cf - $(JDK_DIRS)) | \
	 ($(CD) $(JDK_IMAGE_DIR) && $(TAR) -xf -)

copy_fastdebug_jdk:
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

copy_debug_jdk:
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
	@$(ECHO) "all_debug:        Same as: jvmg jvmg1 export_debug"
	@$(ECHO) "all_optimized:    Same as: optimized optimized1 export_optimized"
	@$(ECHO) "clean:            Clean all areas"
	@$(ECHO) "export_product:   Export product files to EXPORT_PATH"
	@$(ECHO) "export_fastdebug: Export fastdebug files to EXPORT_PATH"
	@$(ECHO) "export_debug:     Export debug files to EXPORT_PATH"
	@$(ECHO) "export_optimized: Export optimized files to EXPORT_PATH"
	@$(ECHO) "create_jdk:       Create JDK image, export all files into it"
	@$(ECHO) "update_jdk:       Update JDK image with fresh exported files"
	@$(ECHO) " "
	@$(ECHO) "Others targets are:"
	@$(ECHO) "   $(C1_VM_TARGETS)"
	@$(ECHO) "   $(C2_VM_TARGETS)"
	@$(ECHO) "   $(KERNEL_VM_TARGETS)"

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
EXPORT_PATH.desc           = Directory to place files to export for JDK build

# Make variables to print out (description and value)
VARIABLE_PRINTVAL_LIST +=       \
    SLASH_JAVA                  \
    OUTPUTDIR                   \
    BOOTDIR                     \
    JDK_IMPORT_PATH             \
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

# JPRT rule to build this workspace
include $(GAMMADIR)/make/jprt.gmk

.PHONY: all world clobber clean help $(C1_VM_TARGETS) $(C2_VM_TARGETS) \
        $(KERNEL_VM_TARGETS) \
	generic_build1 generic_build2 generic_buildkernel generic_export \
	export_product export_fastdebug export_debug export_optimized \
	export_jdk_product export_jdk_fastdebug export_jdk_debug \
	create_jdk copy_jdk update_jdk test_jdk \
	copy_product_jdk copy_fastdebug_jdk copy_debug_jdk

