#
# Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

# Rules to build JVM and related libraries, included from vm.make in the build
# directory.

# Common build rules.
MAKEFILES_DIR=$(GAMMADIR)/make/$(Platform_os_family)/makefiles
include $(MAKEFILES_DIR)/rules.make
include $(GAMMADIR)/make/altsrc.make

default: build

#----------------------------------------------------------------------
# Defs

GENERATED     = ../generated
DEP_DIR       = $(GENERATED)/dependencies

# reads the generated files defining the set of .o's and the .o .h dependencies
-include $(DEP_DIR)/*.d

# read machine-specific adjustments (%%% should do this via buildtree.make?)
include $(MAKEFILES_DIR)/$(BUILDARCH).make

# set VPATH so make knows where to look for source files
# Src_Dirs_V is everything in src/share/vm/*, plus the right os/*/vm and cpu/*/vm
# The adfiles directory contains ad_<arch>.[ch]pp.
# The jvmtifiles directory contains jvmti*.[ch]pp
Src_Dirs_V += $(GENERATED)/adfiles $(GENERATED)/jvmtifiles $(GENERATED)/tracefiles
VPATH += $(Src_Dirs_V:%=%:)

# set INCLUDES for C preprocessor
Src_Dirs_I += $(GENERATED)
INCLUDES += $(Src_Dirs_I:%=-I%)

# SYMFLAG is used by {dtrace,jsig,saproc}.make.
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
  # always build with debug info when we can create .debuginfo files
  # and disable 'lazy debug info' so the .so has everything.
  SYMFLAG = -g -xs
else
  ifeq (${VERSION}, debug)
    SYMFLAG = -g
  else
    SYMFLAG =
  endif
endif

# The following variables are defined in the generated flags.make file.
JDK_VER_DEFS  = -DJDK_MAJOR_VERSION="\"$(JDK_MAJOR_VERSION)\"" \
		-DJDK_MINOR_VERSION="\"$(JDK_MINOR_VERSION)\"" \
		-DJDK_MICRO_VERSION="\"$(JDK_MICRO_VERSION)\"" \
		-DJDK_BUILD_NUMBER="\"$(JDK_BUILD_NUMBER)\""
VM_VER_DEFS   = -DHOTSPOT_RELEASE_VERSION="\"$(HS_BUILD_VER)\"" \
		-DJRE_RELEASE_VERSION="\"$(JRE_RELEASE_VER)\""  \
		$(JDK_VER_DEFS)
HS_LIB_ARCH   = -DHOTSPOT_LIB_ARCH=\"$(LIBARCH)\"
BUILD_USER    = -DHOTSPOT_BUILD_USER="\"$(HOTSPOT_BUILD_USER)\""
VM_DISTRO     = -DHOTSPOT_VM_DISTRO="\"$(HOTSPOT_VM_DISTRO)\""

CXXFLAGS =           \
  ${SYSDEFS}         \
  ${INCLUDES}        \
  ${BUILD_USER}      \
  ${HS_LIB_ARCH}     \
  ${VM_DISTRO}

# This is VERY important! The version define must only be supplied to vm_version.o
# If not, ccache will not re-use the cache at all, since the version string might contain
# a time and date.
CXXFLAGS/vm_version.o += ${VM_VER_DEFS}

CXXFLAGS/BYFILE = $(CXXFLAGS/$@)

# File specific flags
CXXFLAGS += $(CXXFLAGS/BYFILE)

# Large File Support
ifneq ($(LP64), 1)
CXXFLAGS/ostream.o += -D_FILE_OFFSET_BITS=64
endif # ifneq ($(LP64), 1)

# CFLAGS_WARN holds compiler options to suppress/enable warnings.
CFLAGS += $(CFLAGS_WARN)

# Do not use C++ exception handling
CFLAGS += $(CFLAGS/NOEX)

# Extra flags from gnumake's invocation or environment
CFLAGS += $(EXTRA_CFLAGS)

# Math Library (libm.so), do not use -lm.
#    There might be two versions of libm.so on the build system:
#    libm.so.1 and libm.so.2, and we want libm.so.1.
#    Depending on the Solaris release being used to build with,
#    /usr/lib/libm.so could point at a libm.so.2, so we are
#    explicit here so that the libjvm.so you have built will work on an
#    older Solaris release that might not have libm.so.2.
#    This is a critical factor in allowing builds on Solaris 10 or newer
#    to run on Solaris 8 or 9.
#
LIBM=/usr/lib$(ISA_DIR)/libm.so.1

ifeq ("${Platform_compiler}", "sparcWorks")
# The whole megilla:
ifeq ($(shell expr $(COMPILER_REV_NUMERIC) \>= 505), 1)
# Old Comment: List the libraries in the order the compiler was designed for
# Not sure what the 'designed for' comment is referring too above.
#   The order may not be too significant anymore, but I have placed this
#   older libm before libCrun, just to make sure it's found and used first.
LIBS += -lsocket -lsched -ldl $(LIBM) -lCrun -lthread -ldoor -lc -ldemangle -lnsl
else
ifeq ($(COMPILER_REV_NUMERIC), 502)
# SC6.1 has it's own libm.so: specifying anything else provokes a name conflict.
LIBS += -ldl -lthread -lsocket -lm -lsched -ldoor -ldemangle
else
LIBS += -ldl -lthread -lsocket $(LIBM) -lsched -ldoor -ldemangle
endif # 502
endif # 505
else
LIBS += -lsocket -lsched -ldl $(LIBM) -lthread -lc -ldemangle
endif # sparcWorks

LIBS += -lkstat -lrt

# By default, link the *.o into the library, not the executable.
LINK_INTO$(LINK_INTO) = LIBJVM

JDK_LIBDIR = $(JAVA_HOME)/lib/$(LIBARCH)

#----------------------------------------------------------------------
# jvm_db & dtrace
include $(MAKEFILES_DIR)/dtrace.make

#----------------------------------------------------------------------
# JVM

JVM      = jvm
LIBJVM   = lib$(JVM).so

LIBJVM_DEBUGINFO   = lib$(JVM).debuginfo
LIBJVM_DIZ         = lib$(JVM).diz

SPECIAL_PATHS:=adlc c1 dist gc opto shark libadt

SOURCE_PATHS=\
  $(shell find $(HS_COMMON_SRC)/share/vm/* -type d \! \
      \( -name DUMMY $(foreach dir,$(SPECIAL_PATHS),-o -name $(dir)) \))
SOURCE_PATHS+=$(HS_COMMON_SRC)/os/$(Platform_os_family)/vm
SOURCE_PATHS+=$(HS_COMMON_SRC)/os/posix/vm
SOURCE_PATHS+=$(HS_COMMON_SRC)/cpu/$(Platform_arch)/vm
SOURCE_PATHS+=$(HS_COMMON_SRC)/os_cpu/$(Platform_os_arch)/vm

CORE_PATHS=$(foreach path,$(SOURCE_PATHS),$(call altsrc,$(path)) $(path))
CORE_PATHS+=$(GENERATED)/jvmtifiles $(GENERATED)/tracefiles

ifneq ($(INCLUDE_TRACE), false)
CORE_PATHS+=$(shell if [ -d $(HS_ALT_SRC)/share/vm/jfr ]; then \
  find $(HS_ALT_SRC)/share/vm/jfr -type d; \
  fi)
endif

COMPILER1_PATHS := $(call altsrc,$(HS_COMMON_SRC)/share/vm/c1)
COMPILER1_PATHS += $(HS_COMMON_SRC)/share/vm/c1

COMPILER2_PATHS := $(call altsrc,$(HS_COMMON_SRC)/share/vm/opto)
COMPILER2_PATHS += $(call altsrc,$(HS_COMMON_SRC)/share/vm/libadt)
COMPILER2_PATHS += $(HS_COMMON_SRC)/share/vm/opto
COMPILER2_PATHS += $(HS_COMMON_SRC)/share/vm/libadt
COMPILER2_PATHS +=  $(GENERATED)/adfiles

# Include dirs per type.
Src_Dirs/CORE      := $(CORE_PATHS)
Src_Dirs/COMPILER1 := $(CORE_PATHS) $(COMPILER1_PATHS)
Src_Dirs/COMPILER2 := $(CORE_PATHS) $(COMPILER2_PATHS)
Src_Dirs/TIERED    := $(CORE_PATHS) $(COMPILER1_PATHS) $(COMPILER2_PATHS)
Src_Dirs/ZERO      := $(CORE_PATHS)
Src_Dirs/SHARK     := $(CORE_PATHS) $(SHARK_PATHS)
Src_Dirs := $(Src_Dirs/$(TYPE))

COMPILER2_SPECIFIC_FILES := opto libadt bcEscapeAnalyzer.cpp c2_\* runtime_\*
COMPILER1_SPECIFIC_FILES := c1_\*
SHARK_SPECIFIC_FILES     := shark
ZERO_SPECIFIC_FILES      := zero

# Always exclude these.
Src_Files_EXCLUDE += dtrace jsig.c jvmtiEnvRecommended.cpp jvmtiEnvStub.cpp

# Exclude per type.
Src_Files_EXCLUDE/CORE      := $(COMPILER1_SPECIFIC_FILES) $(COMPILER2_SPECIFIC_FILES) $(ZERO_SPECIFIC_FILES) $(SHARK_SPECIFIC_FILES) ciTypeFlow.cpp
Src_Files_EXCLUDE/COMPILER1 := $(COMPILER2_SPECIFIC_FILES) $(ZERO_SPECIFIC_FILES) $(SHARK_SPECIFIC_FILES) ciTypeFlow.cpp
Src_Files_EXCLUDE/COMPILER2 := $(COMPILER1_SPECIFIC_FILES) $(ZERO_SPECIFIC_FILES) $(SHARK_SPECIFIC_FILES)
Src_Files_EXCLUDE/TIERED    := $(ZERO_SPECIFIC_FILES) $(SHARK_SPECIFIC_FILES)
Src_Files_EXCLUDE/ZERO      := $(COMPILER1_SPECIFIC_FILES) $(COMPILER2_SPECIFIC_FILES) $(SHARK_SPECIFIC_FILES) ciTypeFlow.cpp
Src_Files_EXCLUDE/SHARK     := $(COMPILER1_SPECIFIC_FILES) $(COMPILER2_SPECIFIC_FILES) $(ZERO_SPECIFIC_FILES)

Src_Files_EXCLUDE +=  $(Src_Files_EXCLUDE/$(TYPE))

# Special handling of arch model.
ifeq ($(Platform_arch_model), x86_32)
Src_Files_EXCLUDE += \*x86_64\*
endif
ifeq ($(Platform_arch_model), x86_64)
Src_Files_EXCLUDE += \*x86_32\*
endif

# Locate all source files in the given directory, excluding files in Src_Files_EXCLUDE.
define findsrc
	$(notdir $(shell find $(1)/. ! -name . -prune \
		-a \( -name \*.c -o -name \*.cpp -o -name \*.s \) \
		-a ! \( -name DUMMY $(addprefix -o -name ,$(Src_Files_EXCLUDE)) \)))
endef

Src_Files := $(foreach e,$(Src_Dirs),$(call findsrc,$(e)))

Obj_Files = $(sort $(addsuffix .o,$(basename $(Src_Files))))

JVM_OBJ_FILES = $(Obj_Files) $(DTRACE_OBJS)

vm_version.o: $(filter-out vm_version.o,$(JVM_OBJ_FILES))

MAPFILE_SHARE  := $(GAMMADIR)/make/share/makefiles/mapfile-vers

MAPFILE_EXT_SRC := $(HS_ALT_MAKE)/share/makefiles/mapfile-ext
ifneq ("$(wildcard $(MAPFILE_EXT_SRC))","")
MAPFILE_EXT     := $(MAPFILE_EXT_SRC)
endif

mapfile : $(MAPFILE) $(MAPFILE_SHARE) vm.def $(MAPFILE_EXT)
	rm -f $@
	cat $(MAPFILE) $(MAPFILE_DTRACE_OPT) \
	    | $(NAWK) '{                                         \
	              if ($$0 ~ "INSERT VTABLE SYMBOLS HERE") {  \
	                  system ("cat ${MAPFILE_SHARE} $(MAPFILE_EXT) vm.def"); \
	              } else {                                   \
	                  print $$0;                             \
	              }                                          \
	          }' > $@

mapfile_extended : mapfile $(MAPFILE_DTRACE_OPT)
	rm -f $@
	cat $^ > $@

vm.def: $(Obj_Files)
	sh $(GAMMADIR)/make/solaris/makefiles/build_vm_def.sh *.o > $@


ifeq ($(LINK_INTO),AOUT)
  LIBJVM.o                 =
  LIBJVM_MAPFILE           =
  LIBS_VM                  = $(LIBS)
else
  LIBJVM.o                 = $(JVM_OBJ_FILES)
  LIBJVM_MAPFILE$(LDNOMAP) = mapfile_extended
  LFLAGS_VM$(LDNOMAP)      += $(MAPFLAG:FILENAME=$(LIBJVM_MAPFILE))
  LFLAGS_VM                += $(SONAMEFLAG:SONAME=$(LIBJVM))
  LFLAGS_VM                += -Wl,-z,defs
ifndef USE_GCC
  LIBS_VM                  = $(LIBS)
else
  # JVM is statically linked with libgcc[_s] and libstdc++; this is needed to
  # get around library dependency and compatibility issues. Must use gcc not
  # g++ to link.
  LFLAGS_VM                += $(STATIC_LIBGCC)
  LIBS_VM                  += $(STATIC_STDCXX) $(LIBS)
endif
endif

LFLAGS_VM += $(EXTRA_LDFLAGS)

ifdef USE_GCC
LINK_VM = $(LINK_LIB.CC)
else
LINK_VM = $(LINK_LIB.CXX)
endif
# making the library:
$(LIBJVM): $(LIBJVM.o) $(LIBJVM_MAPFILE)
ifeq ($(filter -sbfast -xsbfast, $(CFLAGS_BROWSE)),)
	@echo $(LOG_INFO) Linking vm...
	$(QUIETLY) $(LINK_LIB.CXX/PRE_HOOK)
	$(QUIETLY) $(LINK_VM) $(LFLAGS_VM) -o $@ $(sort $(LIBJVM.o)) $(LIBS_VM)
	$(QUIETLY) $(LINK_LIB.CXX/POST_HOOK)
	$(QUIETLY) rm -f $@.1 && ln -s $@ $@.1
ifeq ($(ENABLE_FULL_DEBUG_SYMBOLS),1)
	$(QUIETLY) $(OBJCOPY) --only-keep-debug $@ $(LIBJVM_DEBUGINFO)
	$(QUIETLY) $(OBJCOPY) --add-gnu-debuglink=$(LIBJVM_DEBUGINFO) $@
  ifeq ($(STRIP_POLICY),all_strip)
	$(QUIETLY) $(STRIP) $@
  else
    ifeq ($(STRIP_POLICY),min_strip)
	$(QUIETLY) $(STRIP) -x $@
    # implied else here is no stripping at all
    endif
  endif
  ifeq ($(ZIP_DEBUGINFO_FILES),1)
	$(ZIPEXE) -q -y $(LIBJVM_DIZ) $(LIBJVM_DEBUGINFO)
	$(RM) $(LIBJVM_DEBUGINFO)
  endif
endif
endif # filter -sbfast -xsbfast


DEST_SUBDIR        = $(JDK_LIBDIR)/$(VM_SUBDIR)
DEST_JVM           = $(DEST_SUBDIR)/$(LIBJVM)
DEST_JVM_DEBUGINFO = $(DEST_SUBDIR)/$(LIBJVM_DEBUGINFO)
DEST_JVM_DIZ       = $(DEST_SUBDIR)/$(LIBJVM_DIZ)

install_jvm: $(LIBJVM)
	@echo "Copying $(LIBJVM) to $(DEST_JVM)"
	$(QUIETLY) test ! -f $(LIBJVM_DEBUGINFO) || \
	    $(CP) -f $(LIBJVM_DEBUGINFO) $(DEST_JVM_DEBUGINFO)
	$(QUIETLY) test ! -f $(LIBJVM_DIZ) || \
	    $(CP) -f $(LIBJVM_DIZ) $(DEST_JVM_DIZ)
	$(QUIETLY) $(CP) -f $(LIBJVM) $(DEST_JVM) && echo "Done"

#----------------------------------------------------------------------
# Other files

# Signal interposition library
include $(MAKEFILES_DIR)/jsig.make

# Serviceability agent
include $(MAKEFILES_DIR)/saproc.make

#----------------------------------------------------------------------

build: $(LIBJVM) $(LAUNCHER) $(LIBJSIG) $(LIBJVM_DB) $(LIBJVM_DTRACE) $(BUILDLIBSAPROC) dtraceCheck

install: install_jvm install_jsig install_saproc

.PHONY: default build install install_jvm
