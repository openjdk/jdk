#
# Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

default: build

#----------------------------------------------------------------------
# Defs

GENERATED     = ../generated

# read a generated file defining the set of .o's and the .o .h dependencies
include $(GENERATED)/Dependencies

# read machine-specific adjustments (%%% should do this via buildtree.make?)
ifeq ($(ZERO_BUILD), true)
  include $(MAKEFILES_DIR)/zeroshark.make
else
  include $(MAKEFILES_DIR)/$(BUILDARCH).make
endif

# set VPATH so make knows where to look for source files
# Src_Dirs is everything in src/share/vm/*, plus the right os/*/vm and cpu/*/vm
# The incls directory contains generated header file lists for inclusion.
# The adfiles directory contains ad_<arch>.[ch]pp.
# The jvmtifiles directory contains jvmti*.[ch]pp
Src_Dirs_V = $(GENERATED)/adfiles $(GENERATED)/jvmtifiles ${Src_Dirs} $(GENERATED)/incls
VPATH    += $(Src_Dirs_V:%=%:)

# set INCLUDES for C preprocessor
Src_Dirs_I = $(PRECOMPILED_HEADER_DIR) $(GENERATED)/adfiles $(GENERATED)/jvmtifiles ${Src_Dirs} $(GENERATED)
INCLUDES += $(Src_Dirs_I:%=-I%)

ifeq (${VERSION}, debug)
  SYMFLAG = -g
else
  SYMFLAG =
endif

# HOTSPOT_RELEASE_VERSION and HOTSPOT_BUILD_VERSION are defined 
# in $(GAMMADIR)/make/defs.make
ifeq ($(HOTSPOT_BUILD_VERSION),)
  BUILD_VERSION = -DHOTSPOT_RELEASE_VERSION="\"$(HOTSPOT_RELEASE_VERSION)\""
else
  BUILD_VERSION = -DHOTSPOT_RELEASE_VERSION="\"$(HOTSPOT_RELEASE_VERSION)-$(HOTSPOT_BUILD_VERSION)\""
endif

# The following variables are defined in the generated flags.make file.
BUILD_VERSION = -DHOTSPOT_RELEASE_VERSION="\"$(HS_BUILD_VER)\""
JRE_VERSION   = -DJRE_RELEASE_VERSION="\"$(JRE_RELEASE_VER)\""
HS_LIB_ARCH   = -DHOTSPOT_LIB_ARCH=\"$(LIBARCH)\"
BUILD_TARGET  = -DHOTSPOT_BUILD_TARGET="\"$(TARGET)\""
BUILD_USER    = -DHOTSPOT_BUILD_USER="\"$(HOTSPOT_BUILD_USER)\""
VM_DISTRO     = -DHOTSPOT_VM_DISTRO="\"$(HOTSPOT_VM_DISTRO)\""

CPPFLAGS =           \
  ${SYSDEFS}         \
  ${INCLUDES}        \
  ${BUILD_VERSION}   \
  ${BUILD_TARGET}    \
  ${BUILD_USER}      \
  ${HS_LIB_ARCH}     \
  ${JRE_VERSION}     \
  ${VM_DISTRO}

# CFLAGS_WARN holds compiler options to suppress/enable warnings.
CFLAGS += $(CFLAGS_WARN/BYFILE)

# Do not use C++ exception handling
CFLAGS += $(CFLAGS/NOEX)

# Extra flags from gnumake's invocation or environment
CFLAGS += $(EXTRA_CFLAGS)

LIBS += -lm -ldl -lpthread

# By default, link the *.o into the library, not the executable.
LINK_INTO$(LINK_INTO) = LIBJVM

JDK_LIBDIR = $(JAVA_HOME)/jre/lib/$(LIBARCH)

#----------------------------------------------------------------------
# jvm_db & dtrace
include $(MAKEFILES_DIR)/dtrace.make

#----------------------------------------------------------------------
# JVM

JVM      = jvm
LIBJVM   = lib$(JVM).so
LIBJVM_G = lib$(JVM)$(G_SUFFIX).so

JVM_OBJ_FILES = $(Obj_Files)

vm_version.o: $(filter-out vm_version.o,$(JVM_OBJ_FILES))

mapfile : $(MAPFILE) vm.def
	rm -f $@
	awk '{ if ($$0 ~ "INSERT VTABLE SYMBOLS HERE")	\
                 { system ("cat vm.def"); }		\
               else					\
                 { print $$0 }				\
             }' > $@ < $(MAPFILE)

mapfile_reorder : mapfile $(REORDERFILE)
	rm -f $@
	cat $^ > $@

vm.def: $(Res_Files) $(Obj_Files)
	sh $(GAMMADIR)/make/linux/makefiles/build_vm_def.sh *.o > $@

ifeq ($(ZERO_LIBARCH), ppc64)
  STATIC_CXX = false
else
  STATIC_CXX = true
endif

ifeq ($(LINK_INTO),AOUT)
  LIBJVM.o                 =
  LIBJVM_MAPFILE           =
  LIBS_VM                  = $(LIBS)
else
  LIBJVM.o                 = $(JVM_OBJ_FILES)
  LIBJVM_MAPFILE$(LDNOMAP) = mapfile_reorder
  LFLAGS_VM$(LDNOMAP)      += $(MAPFLAG:FILENAME=$(LIBJVM_MAPFILE))
  LFLAGS_VM                += $(SONAMEFLAG:SONAME=$(LIBJVM))

  # JVM is statically linked with libgcc[_s] and libstdc++; this is needed to
  # get around library dependency and compatibility issues. Must use gcc not
  # g++ to link.
  ifeq ($(STATIC_CXX), true)
    LFLAGS_VM              += $(STATIC_LIBGCC)
    LIBS_VM                += $(STATIC_STDCXX)
  else
    LIBS_VM                += -lstdc++
  endif

  LIBS_VM                  += $(LIBS)
endif
ifeq ($(ZERO_BUILD), true)
  LIBS_VM += $(LIBFFI_LIBS)
endif

LINK_VM = $(LINK_LIB.c)

# rule for building precompiled header
$(PRECOMPILED_HEADER): $(Precompiled_Files)
	$(QUIETLY) echo Generating precompiled header $@
	$(QUIETLY) mkdir -p $(PRECOMPILED_HEADER_DIR)/incls
	$(QUIETLY) $(COMPILE.CC) -x c++-header -c $(GENERATED)/incls/_precompiled.incl -o $@ $(COMPILE_DONE)

# making the library:

ifneq ($(JVM_BASE_ADDR),)
# By default shared library is linked at base address == 0. Modify the
# linker script if JVM prefers a different base location. It can also be
# implemented with 'prelink -r'. But 'prelink' is not (yet) available on
# our build platform (AS-2.1).
LD_SCRIPT = libjvm.so.lds
$(LD_SCRIPT): $(LIBJVM_MAPFILE)
	$(QUIETLY) {                                                \
	  rm -rf $@;                                                \
	  $(LINK_VM) -Wl,--verbose $(LFLAGS_VM) 2>&1             |  \
	    sed -e '/^======/,/^======/!d'                          \
		-e '/^======/d'                                     \
		-e 's/0\( + SIZEOF_HEADERS\)/$(JVM_BASE_ADDR)\1/'   \
		> $@;                                               \
	}
LD_SCRIPT_FLAG = -Wl,-T,$(LD_SCRIPT)
endif

# With more recent Redhat releases (or the cutting edge version Fedora), if
# SELinux is configured to be enabled, the runtime linker will fail to apply
# the text relocation to libjvm.so considering that it is built as a non-PIC
# DSO. To workaround that, we run chcon to libjvm.so after it is built. See 
# details in bug 6538311.
$(LIBJVM): $(LIBJVM.o) $(LIBJVM_MAPFILE) $(LD_SCRIPT)
	$(QUIETLY) {                                                    \
	    echo Linking vm...;                                         \
	    $(LINK_LIB.CC/PRE_HOOK)                                     \
	    $(LINK_VM) $(LD_SCRIPT_FLAG)                                \
		       $(LFLAGS_VM) -o $@ $(LIBJVM.o) $(LIBS_VM);       \
	    $(LINK_LIB.CC/POST_HOOK)                                    \
	    rm -f $@.1; ln -s $@ $@.1;                                  \
	    [ -f $(LIBJVM_G) ] || { ln -s $@ $(LIBJVM_G); ln -s $@.1 $(LIBJVM_G).1; }; \
	    if [ -x /usr/sbin/selinuxenabled ] ; then                   \
	      /usr/sbin/selinuxenabled;                                 \
              if [ $$? = 0 ] ; then					\
		/usr/bin/chcon -t textrel_shlib_t $@;                   \
		if [ $$? != 0 ]; then                                   \
		  echo "ERROR: Cannot chcon $@";			\
		fi							\
	      fi							\
	    fi                                                          \
	}

DEST_JVM = $(JDK_LIBDIR)/$(VM_SUBDIR)/$(LIBJVM)

install_jvm: $(LIBJVM)
	@echo "Copying $(LIBJVM) to $(DEST_JVM)"
	$(QUIETLY) cp -f $(LIBJVM) $(DEST_JVM) && echo "Done"

#----------------------------------------------------------------------
# Other files

# Gamma launcher
include $(MAKEFILES_DIR)/launcher.make

# Signal interposition library
include $(MAKEFILES_DIR)/jsig.make

# Serviceability agent
include $(MAKEFILES_DIR)/saproc.make

#----------------------------------------------------------------------

build: $(LIBJVM) $(LAUNCHER) $(LIBJSIG) $(LIBJVM_DB) checkAndBuildSA

install: install_jvm install_jsig install_saproc

.PHONY: default build install install_jvm
