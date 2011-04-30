#
# Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building  Pack200
#

BUILDDIR = ../../../..
PACKAGE = com.sun.java.util.jar.pack
LIBRARY = unpack
PRODUCT = sun
PGRM = unpack200
include $(BUILDDIR)/common/Defs.gmk

CPLUSPLUSLIBRARY=true

FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Files to compile
#
AUTO_FILES_JAVA_DIRS = com/sun/java/util/jar/pack

include FILES_cpp.gmk

FILES_export = $(PKGDIR)/NativeUnpack.java

#
# Rules
include $(BUILDDIR)/common/Library.gmk

build: unpacker

vpath %.cpp $(SHARE_SRC)/native/$(PKGDIR)

ifeq ($(STANDALONE),true)
  ZIPOBJDIR = $(OUTPUTDIR)/tmp/sun/java.util.zip/zip/$(OBJDIRNAME)

  ZIPOBJS =  $(ZIPOBJDIR)/zcrc32.$(OBJECT_SUFFIX) \
	     $(ZIPOBJDIR)/deflate.$(OBJECT_SUFFIX) \
	     $(ZIPOBJDIR)/trees.$(OBJECT_SUFFIX) \
	     $(ZIPOBJDIR)/zadler32.$(OBJECT_SUFFIX) \
	     $(ZIPOBJDIR)/compress.$(OBJECT_SUFFIX) \
	     $(ZIPOBJDIR)/zutil.$(OBJECT_SUFFIX) \
	     $(ZIPOBJDIR)/inflate.$(OBJECT_SUFFIX) \
	     $(ZIPOBJDIR)/infback.$(OBJECT_SUFFIX) \
	     $(ZIPOBJDIR)/inftrees.$(OBJECT_SUFFIX) \
	     $(ZIPOBJDIR)/inffast.$(OBJECT_SUFFIX)

  ZINCLUDE=-I$(SHARE_SRC)/native/java/util/zip/zlib-$(ZLIB_VERSION)
  OTHER_CXXFLAGS += $(ZINCLUDE)
  LDDFLAGS += $(ZIPOBJS)
else
  OTHER_CXXFLAGS += -DNO_ZLIB -DUNPACK_JNI
  OTHER_LDLIBS += $(JVMLIB)
endif

CXXFLAGS_DBG += -DFULL
CXXFLAGS_OPT += -DPRODUCT
CXXFLAGS_COMMON += -DFULL

ifeq ($(PLATFORM), windows) 
  CXXFLAGS_COMMON += $(MS_RUNTIME_OPTION)
  LDOUTPUT = -Fe

  # JDK name required here
  RC_FLAGS += /D "JDK_FNAME=$(PGRM).exe" \
	/D "JDK_INTERNAL_NAME=$(PGRM)" \
	/D "JDK_FTYPE=0x1L"

  RES = $(OBJDIR)/$(PGRM).res
else
  LDOUTPUT = -o #Have a space 
  LDDFLAGS += -lc
  OTHER_LDLIBS  += $(LIBCXX)
# setup the list of libraries to link in...
ifeq ($(PLATFORM), linux)
ifeq ("$(CC_VER_MAJOR)", "3")
  OTHER_LDLIBS  += -Wl,-Bstatic -lgcc_eh -Wl,-Bdynamic
endif
endif #LINUX
endif #PLATFORM

UNPACK_EXE = $(BINDIR)/unpack200$(EXE_SUFFIX)

UNPACK_EXE_FILES_cpp =  $(filter-out jni.cpp,$(FILES_cpp))

UNPACK_EXE_FILES_cpp += main.cpp

UNPACK_EXE_FILES_o = $(addprefix $(COBJDIR)/, \
		     	$(notdir  \
		     	$(patsubst %.cpp,%.$(OBJECT_SUFFIX),$(UNPACK_EXE_FILES_cpp)) \
		     ))

COBJDIR = $(strip $(subst unpack,unpack-cmd,$(OBJDIR)))

all: build

build: prop pack200-tool

prop:
	$(MAKE) -C prop

pack200-tool:
	$(call make-launcher, pack200, com.sun.java.util.jar.pack.Driver, , --pack)

# ignore mapfile for non-product binary 
unpacker:
	$(MAKE) $(UNPACK_EXE) STANDALONE=true LDMAPFLAGS_DBG=

ifeq ($(PLATFORM), windows)
IMVERSIONVALUE=$(JDK_MINOR_VERSION).$(JDK_MICRO_VERSION).$(JDK_UPDATE_VER).$(COOKED_BUILD_NUMBER)
SED_ALL_MANIFEST=$(SED) -e 's%IMVERSION%$(IMVERSIONVALUE)%g'
updatefiles:: 
	$(CAT) $(TOPDIR)/src/windows/resource/unpack200_proto.exe.manifest | $(SED_ALL_MANIFEST) > $(OBJDIR)/unpack200.exe.manifest
winres::  $(RES)
else
updatefiles::
	$(ECHO) "Manifest not required for Unix"
winres::
	$(ECHO) "Resource files not required for Unix"
endif

# Mapfile-vers.gmk, does not copy over the mapfile-vers-unpack200, when
# the make utiliy is re-invoked, as in this case. In order to workaround
# this special case, the mapfile required for the unpack200 command, is
# explicitly copied over to the expected location.
$(UNPACK_EXE): $(UNPACK_EXE_FILES_o) updatefiles winres
	$(prep-target)
	$(RM) $(TEMPDIR)/mapfile-vers
	$(CP) mapfile-vers-unpack200 $(TEMPDIR)/mapfile-vers
	$(LINKER)  $(LDDFLAGS) $(UNPACK_EXE_FILES_o) $(RES) $(LIBCXX) $(LDOUTPUT)$(TEMPDIR)/unpack200$(EXE_SUFFIX)
ifdef MT
	$(MT) /manifest $(OBJDIR)/unpack200$(EXE_SUFFIX).manifest /outputresource:$(TEMPDIR)/unpack200$(EXE_SUFFIX);#1
endif
	$(CP) $(TEMPDIR)/unpack200$(EXE_SUFFIX) $(UNPACK_EXE)
	@$(call binary_file_verification,$@)

ifeq ($(PLATFORM), windows) 
$(RES):: $(VERSIONINFO_RESOURCE)
	$(prep-target)
	$(RC) $(RC_FLAGS) $(CC_OBJECT_OUTPUT_FLAG)$(@) $(VERSIONINFO_RESOURCE)
endif

$(COBJDIR)/%.$(OBJECT_SUFFIX): %.cpp
	$(prep-target)
	$(COMPILE.cc) $(CC_OBJECT_OUTPUT_FLAG)$(COBJDIR)/$*.$(OBJECT_SUFFIX) $<

#
# Clean up.
#
clean::
	$(RM) -r $(CLASSDESTDIR)/com/sun/java/util/jar/pack $(TEMPDIR)
	$(RM) -r $(OUTPUTDIR)/tmp/$(PRODUCT)/$(PACKAGE)
	$(MAKE) -C prop clean

.PHONY: unpacker clean winres prop
