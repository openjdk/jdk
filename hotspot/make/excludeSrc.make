#
# Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

include $(GAMMADIR)/make/altsrc.make

ifeq ($(INCLUDE_JVMTI), false)
      CXXFLAGS += -DINCLUDE_JVMTI=0
      CFLAGS += -DINCLUDE_JVMTI=0

      Src_Files_EXCLUDE += jvmtiGetLoadedClasses.cpp jvmtiThreadState.cpp jvmtiExtensions.cpp \
	jvmtiImpl.cpp jvmtiManageCapabilities.cpp jvmtiRawMonitor.cpp jvmtiUtil.cpp jvmtiTrace.cpp \
	jvmtiCodeBlobEvents.cpp jvmtiEnv.cpp jvmtiRedefineClasses.cpp jvmtiEnvBase.cpp jvmtiEnvThreadState.cpp \
	jvmtiTagMap.cpp jvmtiEventController.cpp evmCompat.cpp jvmtiEnter.xsl jvmtiExport.cpp \
	jvmtiClassFileReconstituter.cpp
endif

ifeq ($(INCLUDE_FPROF), false)
      CXXFLAGS += -DINCLUDE_FPROF=0
      CFLAGS += -DINCLUDE_FPROF=0

      Src_Files_EXCLUDE += fprofiler.cpp
endif

ifeq ($(INCLUDE_VM_STRUCTS), false)
      CXXFLAGS += -DINCLUDE_VM_STRUCTS=0
      CFLAGS += -DINCLUDE_VM_STRUCTS=0

      Src_Files_EXCLUDE += vmStructs.cpp
endif

ifeq ($(INCLUDE_JNI_CHECK), false)
      CXXFLAGS += -DINCLUDE_JNI_CHECK=0
      CFLAGS += -DINCLUDE_JNI_CHECK=0

      Src_Files_EXCLUDE += jniCheck.cpp
endif

ifeq ($(INCLUDE_SERVICES), false)
      CXXFLAGS += -DINCLUDE_SERVICES=0
      CFLAGS += -DINCLUDE_SERVICES=0

      Src_Files_EXCLUDE += heapDumper.cpp heapInspection.cpp \
	attachListener_linux.cpp attachListener.cpp
endif

ifeq ($(INCLUDE_MANAGEMENT), false)
      CXXFLAGS += -DINCLUDE_MANAGEMENT=0
      CFLAGS += -DINCLUDE_MANAGEMENT=0
endif

ifeq ($(INCLUDE_CDS), false)
      CXXFLAGS += -DINCLUDE_CDS=0
      CFLAGS += -DINCLUDE_CDS=0

      Src_Files_EXCLUDE += classListParser.cpp classLoaderExt.cpp \
        filemap.cpp metaspaceShared*.cpp sharedClassUtil.cpp sharedPathsMiscInfo.cpp \
        systemDictionaryShared.cpp
endif

ifeq ($(INCLUDE_ALL_GCS), false)
      CXXFLAGS += -DINCLUDE_ALL_GCS=0
      CFLAGS += -DINCLUDE_ALL_GCS=0

      gc_dir := $(HS_COMMON_SRC)/share/vm/gc
      gc_dir_alt := $(HS_ALT_SRC)/share/vm/gc
      gc_subdirs := cms g1 parallel
      gc_exclude := $(foreach gc,$(gc_subdirs),				\
		     $(notdir $(wildcard $(gc_dir)/$(gc)/*.cpp))	\
		     $(notdir $(wildcard $(gc_dir_alt)/$(gc)/*.cpp)))
      Src_Files_EXCLUDE += $(gc_exclude)				\
	concurrentGCThread.cpp						\
	plab.cpp

      # src/share/vm/services
      Src_Files_EXCLUDE +=						\
	g1MemoryPool.cpp						\
	psMemoryPool.cpp
endif

ifeq ($(INCLUDE_NMT), false)
      CXXFLAGS += -DINCLUDE_NMT=0
      CFLAGS += -DINCLUDE_NMT=0

      Src_Files_EXCLUDE += \
	 memBaseline.cpp memReporter.cpp mallocTracker.cpp virtualMemoryTracker.cpp nmtCommon.cpp \
	 memTracker.cpp nmtDCmd.cpp mallocSiteTable.cpp
endif

ifneq (,$(findstring $(Platform_arch_model), x86_64, sparc))
      # JVMCI is supported only on x86_64 and SPARC.
else
      INCLUDE_JVMCI := false
endif

ifeq ($(INCLUDE_JVMCI), false)
      CXXFLAGS += -DINCLUDE_JVMCI=0
      CFLAGS += -DINCLUDE_JVMCI=0

      jvmci_dir := $(HS_COMMON_SRC)/share/vm/jvmci
      jvmci_dir_alt := $(HS_ALT_SRC)/share/vm/jvmci
      jvmci_exclude := $(notdir $(wildcard $(jvmci_dir)/*.cpp))	\
			$(notdir $(wildcard $(jvmci_dir_alt)/*.cpp))
      Src_Files_EXCLUDE += $(jvmci_exclude) \
	jvmciCodeInstaller_aarch64.cpp jvmciCodeInstaller_ppc.cpp jvmciCodeInstaller_sparc.cpp \
	jvmciCodeInstaller_x86.cpp
endif

-include $(HS_ALT_MAKE)/excludeSrc.make

.PHONY: $(HS_ALT_MAKE)/excludeSrc.make
