#
# Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

      Src_Files_EXCLUDE += filemap.cpp metaspaceShared.cpp
endif

ifeq ($(INCLUDE_ALL_GCS), false)
      CXXFLAGS += -DINCLUDE_ALL_GCS=0
      CFLAGS += -DINCLUDE_ALL_GCS=0

      gc_impl := $(GAMMADIR)/src/share/vm/gc_implementation
      gc_exclude :=							\
	$(notdir $(wildcard $(gc_impl)/concurrentMarkSweep/*.cpp))	\
	$(notdir $(wildcard $(gc_impl)/g1/*.cpp))			\
	$(notdir $(wildcard $(gc_impl)/parallelScavenge/*.cpp))		\
	$(notdir $(wildcard $(gc_impl)/parNew/*.cpp))
      Src_Files_EXCLUDE += $(gc_exclude)

      # Exclude everything in $(gc_impl)/shared except the files listed
      # in $(gc_shared_keep).
      gc_shared_all := $(notdir $(wildcard $(gc_impl)/shared/*.cpp))
      gc_shared_keep :=							\
	adaptiveSizePolicy.cpp						\
	ageTable.cpp							\
	collectorCounters.cpp						\
	cSpaceCounters.cpp						\
	gcPolicyCounters.cpp						\
	gcStats.cpp							\
	gcTimer.cpp							\
	gcTrace.cpp							\
	gcTraceSend.cpp							\
	gcTraceTime.cpp							\
	gcUtil.cpp							\
	generationCounters.cpp						\
	markSweep.cpp							\
	objectCountEventSender.cpp					\
	spaceDecorator.cpp						\
	vmGCOperations.cpp
      Src_Files_EXCLUDE += $(filter-out $(gc_shared_keep),$(gc_shared_all))

      # src/share/vm/services
      Src_Files_EXCLUDE +=						\
	g1MemoryPool.cpp						\
	psMemoryPool.cpp
endif

ifeq ($(INCLUDE_NMT), false)
      CXXFLAGS += -DINCLUDE_NMT=0
      CFLAGS += -DINCLUDE_NMT=0

      Src_Files_EXCLUDE += \
	 memBaseline.cpp memPtr.cpp memRecorder.cpp memReporter.cpp memSnapshot.cpp memTrackWorker.cpp \
	 memTracker.cpp nmtDCmd.cpp
endif

-include $(HS_ALT_MAKE)/excludeSrc.make

.PHONY: $(HS_ALT_MAKE)/excludeSrc.make
