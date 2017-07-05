#
# Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../..
PACKAGE = sun.awt.X11
LIBRARY = awt_xawt
PRODUCT = sun

include $(BUILDDIR)/common/Defs.gmk

GEN_DIR=$(GENSRCDIR)/sun/awt/X11

CLASSES_INIT += $(TEMPDIR)/.gen.wrappers touch.wrappers

.PHONY: generated.clean

#
# Files
#
include FILES_c_unix.gmk
include FILES_export_unix.gmk
AUTO_FILES_JAVA_DIRS = sun/awt/X11
AUTO_JAVA_PRUNE = WrapperGenerator.java

LDFLAGS += -L$(OPENWIN_LIB)

# For Xrender extension.
ifeq ($(PLATFORM), solaris)
LDFLAGS += -L/usr/openwin/sfw/lib$(ISA_DIR) -R/usr/openwin/sfw/lib$(ISA_DIR)
endif

ifeq ($(PLATFORM), linux)
LDFLAGS += -lpthread
dummy := $(shell $(MKDIR) -p $(LIB_LOCATION))
endif

ifeq ($(PLATFORM), macosx)
LDFLAGS += -pthread
dummy := $(shell $(MKDIR) -p $(LIB_LOCATION))
endif

# Since this library will be living in a subdirectory below the other libraries
#   we need to add an extra runpath so that libraries in the upper directory
#   are found at runtime.
LD_RUNPATH_EXTRAS = ..

#
# Rules.
#
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

#
# Add to the ambient vpath to pick up files in subdirectories
#
vpath %.c   $(SHARE_SRC)/native/sunawt/alphacomposite
vpath %.c   $(PLATFORM_SRC)/native/sun/xawt
vpath %.c   $(PLATFORM_SRC)/native/sun/awt
vpath %.c   $(SHARE_SRC)/native/sun/awt/debug
vpath %.c   $(SHARE_SRC)/native/sun/awt/image
vpath %.c   $(SHARE_SRC)/native/sun/awt/image/gif
vpath %.c   $(SHARE_SRC)/native/sun/awt/image/cvutils
vpath %.c   $(SHARE_SRC)/native/sun/awt/shell
vpath %.c   $(SHARE_SRC)/native/sun/java2d
vpath %.c   $(SHARE_SRC)/native/sun/java2d/loops
vpath %.c   $(SHARE_SRC)/native/sun/java2d/pipe
vpath %.c   $(SHARE_SRC)/native/sun/awt/medialib
vpath %.c   $(SHARE_SRC)/native/sun/awt/utility
vpath %.cpp $(SHARE_SRC)/native/sun/image
vpath %.c   $(SHARE_SRC)/native/sun/font
vpath %.c   $(PLATFORM_SRC)/native/sun/awt/robot_child
vpath %.c   $(SHARE_SRC)/native/sun/java2d/opengl
vpath %.c   $(PLATFORM_SRC)/native/sun/java2d/opengl
vpath %.c   $(PLATFORM_SRC)/native/sun/java2d/x11

OTHER_LDLIBS = $(LIBM) -lawt -lXext -lX11 -lXrender $(LIBDL) \
                   $(LDFLAGS_COMMON) $(AWT_RUNPATH) $(OTHER_LDFLAGS) -lXtst -lXi

ifeq  ($(PLATFORM), solaris)
CPPFLAGS += -DFUNCPROTO=15
dummy := $(shell $(MKDIR) -p $(LIB_LOCATION))
endif

CPPFLAGS += -I$(CUPS_HEADERS_PATH)

CPPFLAGS += -DXAWT -DXAWT_HACK \
        -I$(TEMPDIR)/../../sun.awt/awt/CClassHeaders \
        -I$(PLATFORM_SRC)/native/sun/awt \
	-I$(PLATFORM_SRC)/native/sun/xawt \
        -I$(PLATFORM_SRC)/native/sun/jdga \
        -I$(SHARE_SRC)/native/sun/awt/debug \
        -I$(SHARE_SRC)/native/sun/awt/image/cvutils \
        -I$(SHARE_SRC)/native/sun/java2d \
	-I$(SHARE_SRC)/native/sun/java2d/loops \
	-I$(SHARE_SRC)/native/sun/awt/image/cvutils \
	-I$(SHARE_SRC)/native/sun/awt/image \
	-I$(SHARE_SRC)/native/sun/font \
	-I$(PLATFORM_SRC)/native/sun/java2d \
	-I$(SHARE_SRC)/native/sun/java2d/pipe \
	-I$(SHARE_SRC)/native/sun/java2d/opengl \
	-I$(PLATFORM_SRC)/native/sun/java2d/opengl \
	-I$(PLATFORM_SRC)/native/sun/java2d/x11 \
	-I$(SHARE_SRC)/native/sun/dc/path \
	-I$(SHARE_SRC)/native/sun/dc/doe \
	-I$(SHARE_SRC)/native/sun/awt/alphacomposite \
        -I$(SHARE_SRC)/native/sun/awt/medialib \
	-I$(PLATFORM_SRC)/native/sun/awt/medialib \
        -I$(PLATFORM_SRC)/native/sun/font \
        -I$(SHARE_SRC)/native/sun/awt \
        -I$(PLATFORM_SRC)/native/sun/awt

ifeq ($(PLATFORM), linux)
  ifndef CROSS_COMPILE_ARCH
    # Allows for builds on Debian GNU Linux, X11 is in a different place 
    # This should really be handled at a higher-level so we don't have to
    # work-around this when cross-compiling
    CPPFLAGS += -I/usr/X11R6/include/X11/extensions \
                -I/usr/include/X11/extensions \
                -I$(OPENWIN_HOME)/include 
  else
    CPPFLAGS += -I$(OPENWIN_HOME)/include/X11/extensions \
                -I$(OPENWIN_HOME)/include 
  endif
endif

# We have some odd logic here because some Solaris 10 updates
# have a render.h file that suggests gradients are supported, but
# the Xrender.h doesn't have the corresponding type definitions.
# Earlier updates have neither. We'd like to know if there's a mismatch.
# Whilst in the C preprocessor we can tell if the render.h define's are set
# we can't tell anything about C declarations.
# A grep of Xrender.h is the only way to know this. If they are absent
# we will set a flag indicating this mismatch and the JDK source file
# will interpret it to resolve the problem.
ifeq ($(PLATFORM), solaris)
  CPPFLAGS += -I$(OPENWIN_HOME)/include/X11/extensions
  OS_VERSION := $(shell uname -r)
  XRENDER_H := $(OPENWIN_HOME)/share/include/X11/extensions/Xrender.h
  ifeq ($(OS_VERSION),5.10) 
     LINEARGRADIENT_CNT := $(shell $(EGREP) -c XLinearGradient $(XRENDER_H))
     ifeq ($(LINEARGRADIENT_CNT),0) 
       CFLAGS+= -DSOLARIS10_NO_XRENDER_STRUCTS
     endif
 endif
endif

ifeq ($(PLATFORM), macosx)
  CPPFLAGS += -DX11_PATH=\"$(X11_PATH)\" -DPACKAGE_PATH=\"$(PACKAGE_PATH)\"
endif

ifeq ($(MILESTONE), internal)
  CPPFLAGS += -DINTERNAL_BUILD
endif

#
# Generation of wrappers
#
SIZER_DIR=$(GEN_DIR)/generator
SIZER = $(SIZER_DIR)/sizer
SIZER_32_C = $(SIZER).32.c
SIZER_64_C = $(SIZER).64.c
CFLAGS_32=$(CFLAGS)
CFLAGS_64=$(CFLAGS)

ifeq ($(PLATFORM), solaris)

isalist:=$(shell $(ISAINFO))

ifneq (,$(findstring sparcv9, $(isalist)))
# On sparcv9 we generate both 32 and 64-bit sizers in spite of ARCH_DATA_MODEL.
# On sparcv9 CFLAGS already contain $(XARCH_OPTION/64), so to generate 32-bit sizer we need to change this option.
CFLAGS_32=$(subst $(XARCH_OPTION/64),$(XARCH_OPTION/32),$(CFLAGS))
SIZERS = $(SIZER).32 $(SIZER).64
SIZERS_C = $(SIZER_32_C) $(SIZER_64_C)
SIZES = $(WRAPPER_GENERATOR_DIR)/sizes.32 $(WRAPPER_GENERATOR_DIR)/sizes.64
CFLAGS_64=$(CFLAGS) $(XARCH_OPTION/64)
else
ifneq (,$(findstring amd64, $(isalist)))
# On amd64 we generate both 32 and 64-bit sizers in spite of ARCH_DATA_MODEL.
# On amd64 CFLAGS already contain $(XARCH_OPTION/64), so to generate 32-bit sizer we need to change this option.
CFLAGS_32=$(subst $(XARCH_OPTION/64),$(XARCH_OPTION/32),$(CFLAGS))
SIZERS = $(SIZER).32 $(SIZER).64
SIZERS_C = $(SIZER_32_C) $(SIZER_64_C)
SIZES = $(WRAPPER_GENERATOR_DIR)/sizes.32 $(WRAPPER_GENERATOR_DIR)/sizes.64
CFLAGS_64=$(CFLAGS) $(XARCH_OPTION/64)
else # !sparcv9 : includes (32-bit) sparc, i586
SIZERS = $(SIZER).32
SIZERS_C = $(SIZER_32_C)
SIZES = $(WRAPPER_GENERATOR_DIR)/sizes.32
endif # amd64
endif # sparcv9

else # !solaris

ifeq ($(PLATFORM), macosx)
CFLAGS_32=-arch i386
SIZERS = $(SIZER).32 $(SIZER).64
SIZERS_C = $(SIZER_32_C) $(SIZER_64_C)
SIZES = $(WRAPPER_GENERATOR_DIR)/sizes.32 $(WRAPPER_GENERATOR_DIR)/sizes.64
CFLAGS_64=-arch x86_64
else # !macosx
ifeq ($(ARCH_DATA_MODEL), 32)
SIZERS = $(SIZER).32
SIZERS_C = $(SIZER_32_C)
SIZES = $(WRAPPER_GENERATOR_DIR)/sizes.32
ifdef CROSS_COMPILE_ARCH
CFLAGS_32 = -m32
endif
else # !32
SIZERS = $(SIZER).64
SIZERS_C = $(SIZER_64_C)
SIZES = $(WRAPPER_GENERATOR_DIR)/sizes.64
ifdef CROSS_COMPILE_ARCH
CFLAGS_64 = -m64
endif
endif # 32
endif # !macosx
endif # solaris

# XXX Hack for 6185483 - use hard-coded sizes.
# Add the 64-bit platforms that need to be included into 32-bit build
# and have sizes.64-$(PLATFORM)-$(LIBARCH) hardcoded in the workspace
ifeq ($(PLATFORM)-$(LIBARCH), solaris-i386)
# If you define DOHACK=true for some combination of $(PLATFORM)-$(LIBARCH),
# make sure you have sizes.64-$(PLATFORM)-$(LIBARCH) pre-generated in
# $(PLATFORM_SRC)/classes/sun/awt/X11/generator/
DOHACK=true
endif # Hack

ifeq ($(DOHACK), true)
PREDEFINED_SIZES_TMPL=$(PLATFORM_SRC)/classes/sun/awt/X11/generator/sizes.64-$(PLATFORM)-$(LIBARCH)
PREDEFINED_SIZES=$(WRAPPER_GENERATOR_DIR)/sizes.64
SIZES += $(WRAPPER_GENERATOR_DIR)/sizes.64
endif

ifeq ($(PLATFORM)-$(LIBARCH), solaris-amd64)
DOCOMPARE=true
endif
# 64 bit sizers are generated on platform-libarch (left) for use 
# on platform-libarch (right) and stored under the latter name.
# Do compare manually stored and automatically generated pair(s) 
# if DOCOMPARE=true, just after the generation.
STORED_SIZES_TMPL_solaris_amd64=$(PLATFORM_SRC)/classes/sun/awt/X11/generator/sizes.64-solaris-i386

WRAPPER_GENERATOR_JAVA=$(PLATFORM_SRC)/classes/sun/awt/X11/generator/WrapperGenerator.java
WRAPPER_GENERATOR_DIR=$(GENSRCDIR)/sun/awt/X11/generator
WRAPPER_GENERATOR_TEMPDIR=$(TEMPDIR)/sun/awt/X11/generator
WRAPPER_GENERATOR_CLASS=$(WRAPPER_GENERATOR_TEMPDIR)/WrapperGenerator.class 
XLIBTYPES=$(PLATFORM_SRC)/classes/sun/awt/X11/generator/xlibtypes.txt

ifndef CROSS_COMPILE_ARCH
SIZERS_CC = $(CC)
else
SIZERS_CC = $(HOST_CC)
endif

$(SIZERS): $(SIZERS_C) 
	$(prep-target)
	$(SIZERS_CC) $(CFLAGS_$(subst .,,$(suffix $@))) $(CPPFLAGS) -c -o $(SIZER)$(suffix $@).o $(SIZER)$(suffix $@).c
	$(SIZERS_CC) $(CFLAGS_$(subst .,,$(suffix $@))) -o $@ $(CPPFLAGS) $(SIZER)$(suffix $@).o

$(WRAPPER_GENERATOR_CLASS): $(WRAPPER_GENERATOR_JAVA)
	$(prep-target)
	$(BOOT_JAVAC_CMD) -d $(WRAPPER_GENERATOR_TEMPDIR) $(WRAPPER_GENERATOR_JAVA)

$(SIZERS_C): $(WRAPPER_GENERATOR_CLASS) $(XLIBTYPES)
	$(prep-target)
	$(MKDIR) -p $(WRAPPER_GENERATOR_TEMPDIR)
	$(BOOT_JAVA_CMD) -cp $(WRAPPER_GENERATOR_TEMPDIR) WrapperGenerator \
	    $(SIZER_DIR) $(XLIBTYPES) "sizer" $(subst .,,$(suffix $(basename $@)))

$(SIZES): $(SIZERS)
	@if [ "$(DOHACK)$@" = "true$(PREDEFINED_SIZES)" ]; then \
	    $(ECHO) COPYING $(PREDEFINED_SIZES_TMPL) into $@; \
	    $(CP) $(PREDEFINED_SIZES_TMPL) $@; \
	    $(CHMOD) +w $@;\
	else	\
	    $(ECHO) GENERATING $@; \
	    $(WRAPPER_GENERATOR_DIR)/sizer$(suffix $@) > $@; \
	fi
	@if [ "$(DOCOMPARE)$(suffix $@)" = "true.64" ]; then \
	    $(ECHO) COMPARING $@ and $(STORED_SIZES_TMPL_$(PLATFORM)_$(LIBARCH)); \
	    $(DIFF) $@ $(STORED_SIZES_TMPL_$(PLATFORM)_$(LIBARCH)); \
	fi

$(TEMPDIR)/.gen.wrappers: $(SIZES) $(WRAPPER_GENERATOR_CLASS) $(XLIBTYPES)
	$(BOOT_JAVA_CMD) -cp $(WRAPPER_GENERATOR_TEMPDIR) WrapperGenerator \
	    $(GEN_DIR) $(XLIBTYPES) "gen" $(WRAPPER_GENERATOR_DIR)/sizes

touch.wrappers: $(TEMPDIR)/.gen.wrappers
	$(MKDIR) -p $(TEMPDIR)
	$(TOUCH) $(TEMPDIR)/.gen.wrappers

generated.clean:
	$(RM) -r $(WRAPPER_GENERATOR_TEMPDIR)
	$(RM) -r $(WRAPPER_GENERATOR_DIR)
	$(RM) -r $(GEN_DIR)/*.java


clean clobber:: generated.clean

.PHONY: generated.clean robot_child

