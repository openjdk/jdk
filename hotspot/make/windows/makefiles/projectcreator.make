#
# Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

!include $(WorkSpace)/make/windows/makefiles/rules.make

# This is used externally by both batch and IDE builds, so can't
# reference any of the HOTSPOTWORKSPACE, HOTSPOTBUILDSPACE,
# HOTSPOTRELEASEBINDEST, or HOTSPOTDEBUGBINDEST environment variables.
#
# NOTE: unfortunately the ProjectCreatorSources list must be kept
# synchronized between this and the Solaris version
# (make/solaris/makefiles/projectcreator.make).

ProjectCreatorSources=\
        $(WorkSpace)\src\share\tools\ProjectCreator\DirectoryTree.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\DirectoryTreeNode.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\FileFormatException.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\Macro.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\MacroDefinitions.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\ProjectCreator.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\WinGammaPlatform.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\WinGammaPlatformVC6.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\WinGammaPlatformVC7.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\WinGammaPlatformVC8.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\WinGammaPlatformVC9.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\Util.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\BuildConfig.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\ArgsParser.java

# This is only used internally
ProjectCreatorIncludesPRIVATE=\
        -relativeInclude src\share\vm \
        -relativeInclude src\share\vm\prims \
        -relativeInclude src\os\windows\vm \
        -relativeInclude src\os_cpu\windows_$(Platform_arch)\vm \
        -relativeInclude src\cpu\$(Platform_arch)\vm \
        -absoluteInclude $(HOTSPOTBUILDSPACE)/%f/generated \
        -ignorePath $(HOTSPOTBUILDSPACE)/%f/generated \
        -ignorePath src\share\vm\adlc \
        -ignorePath src\share\vm\shark

# This is referenced externally by both the IDE and batch builds
ProjectCreatorOptions=

# This is used externally, but only by the IDE builds, so we can
# reference environment variables which aren't defined in the batch
# build process.

ProjectCreatorIDEOptions = \
        -useToGeneratePch  java.cpp \
        -disablePch        os_windows.cpp \
        -disablePch        os_windows_$(Platform_arch).cpp \
        -disablePch        osThread_windows.cpp \
        -disablePch        bytecodeInterpreter.cpp \
        -disablePch        bytecodeInterpreterWithChecks.cpp \
        -disablePch        getThread_windows_$(Platform_arch).cpp \
        -disablePch_compiler2     opcodes.cpp    

# Common options for the IDE builds for core, c1, and c2
ProjectCreatorIDEOptions=\
        $(ProjectCreatorIDEOptions) \
        -sourceBase $(HOTSPOTWORKSPACE) \
        -buildBase $(HOTSPOTBUILDSPACE)\%f\%b \
        -startAt src \
        -compiler $(VcVersion) \
        -projectFileName $(HOTSPOTBUILDSPACE)\$(ProjectFile) \
        -jdkTargetRoot $(HOTSPOTJDKDIST) \
        -define ALIGN_STACK_FRAMES \
        -define VM_LITTLE_ENDIAN \
        -prelink  "" "Generating vm.def..." "cd $(HOTSPOTBUILDSPACE)\%f\%b	set HOTSPOTMKSHOME=$(HOTSPOTMKSHOME)	$(HOTSPOTMKSHOME)\sh $(HOTSPOTWORKSPACE)\make\windows\build_vm_def.sh $(LINK_VER)" \
        -ignoreFile jsig.c \
        -ignoreFile jvmtiEnvRecommended.cpp \
        -ignoreFile jvmtiEnvStub.cpp \
        -ignoreFile globalDefinitions_gcc.hpp \
        -ignoreFile globalDefinitions_sparcWorks.hpp \
        -ignoreFile version.rc \
        -ignoreFile Xusage.txt \
        -define TARGET_ARCH_x86 \
        -define TARGET_OS_ARCH_windows_x86 \
        -define TARGET_OS_FAMILY_windows \
        -define TARGET_COMPILER_visCPP \
       $(ProjectCreatorIncludesPRIVATE)

# Add in build-specific options
!if "$(BUILDARCH)" == "i486"
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
        -define IA32 \
        -ignorePath x86_64 \
        -define TARGET_ARCH_MODEL_x86_32
!else
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
        -ignorePath x86_32 \
        -define TARGET_ARCH_MODEL_x86_64
!endif

ProjectCreatorIDEOptionsIgnoreCompiler1=\
 -ignorePath_TARGET c1_

ProjectCreatorIDEOptionsIgnoreCompiler2=\
 -ignorePath_TARGET src/share/vm/opto \
 -ignorePath_TARGET src/share/vm/libadt \
 -ignorePath_TARGET adfiles \
 -ignoreFile_TARGET bcEscapeAnalyzer.cpp \
 -ignoreFile_TARGET bcEscapeAnalyzer.hpp \
 -ignorePath_TARGET chaitin \
 -ignorePath_TARGET c2_ \
 -ignorePath_TARGET runtime_ \
 -ignoreFile_TARGET ciTypeFlow.cpp \
 -ignoreFile_TARGET ciTypeFlow.hpp \
 -ignoreFile_TARGET $(Platform_arch_model).ad

##################################################
# Without compiler(core) specific options
##################################################
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
$(ProjectCreatorIDEOptionsIgnoreCompiler1:TARGET=core) \
$(ProjectCreatorIDEOptionsIgnoreCompiler2:TARGET=core)

##################################################
# JKERNEL specific options
##################################################
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
 -define_kernel KERNEL \
$(ProjectCreatorIDEOptionsIgnoreCompiler2:TARGET=kernel) \
 -ignorePath_kernel src/share/vm/gc_implementation/parallelScavenge \
 -ignorePath_kernel src/share/vm/gc_implementation/parNew \
 -ignorePath_kernel src/share/vm/gc_implementation/concurrentMarkSweep \
 -ignorePath_kernel src/share/vm/gc_implementation/g1 \
 -ignoreFile_kernel attachListener.cpp \
 -ignoreFile_kernel attachListener_windows.cpp \
 -ignoreFile_kernel dump.cpp \
 -ignoreFile_kernel dump_$(Platform_arch_model).cpp \
 -ignoreFile_kernel forte.cpp \
 -ignoreFile_kernel fprofiler.cpp \
 -ignoreFile_kernel heapDumper.cpp \
 -ignoreFile_kernel heapInspection.cpp \
 -ignoreFile_kernel jniCheck.cpp \
 -ignoreFile_kernel jvmtiCodeBlobEvents.cpp \
 -ignoreFile_kernel jvmtiExtensions.cpp \
 -ignoreFile_kernel jvmtiImpl.cpp \
 -ignoreFile_kernel jvmtiRawMonitor.cpp \
 -ignoreFile_kernel jvmtiTagMap.cpp \
 -ignoreFile_kernel jvmtiTrace.cpp \
 -ignoreFile_kernel jvmtiTrace.hpp \
 -ignoreFile_kernel restore.cpp \
 -ignoreFile_kernel serialize.cpp \
 -ignoreFile_kernel vmStructs.cpp \
 -ignoreFile_kernel g1MemoryPool.cpp \
 -ignoreFile_kernel g1MemoryPool.hpp \
 -ignoreFile_kernel psMemoryPool.cpp \
 -ignoreFile_kernel psMemoryPool.hpp \
 -ignoreFile_kernel gcAdaptivePolicyCounters.cpp \
 -ignoreFile_kernel concurrentGCThread.cpp \
 -ignoreFile_kernel mutableNUMASpace.cpp \
 -ignoreFile_kernel ciTypeFlow.cpp \
 -ignoreFile_kernel ciTypeFlow.hpp \
 -ignoreFile_kernel oop.pcgc.inline.hpp \
 -ignoreFile_kernel oop.psgc.inline.hpp \
 -ignoreFile_kernel allocationStats.cpp \
 -ignoreFile_kernel allocationStats.hpp \
 -ignoreFile_kernel concurrentGCThread.hpp \
 -ignoreFile_kernel gSpaceCounters.cpp \
 -ignoreFile_kernel gSpaceCounters.hpp \
 -ignoreFile_kernel gcAdaptivePolicyCounters.hpp \
 -ignoreFile_kernel immutableSpace.cpp \
 -ignoreFile_kernel mutableNUMASpace.hpp \
 -ignoreFile_kernel mutableSpace.cpp \
 -ignoreFile_kernel spaceCounters.cpp \
 -ignoreFile_kernel spaceCounters.hpp \
 -ignoreFile_kernel yieldingWorkgroup.cpp \
 -ignoreFile_kernel yieldingWorkgroup.hpp \
 -ignorePath_kernel vmStructs_ \
 -ignoreFile_kernel $(Platform_arch_model).ad \
 -additionalFile_kernel gcTaskManager.hpp

##################################################
# Client(C1) compiler specific options
##################################################
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
 -define_compiler1 COMPILER1 \
$(ProjectCreatorIDEOptionsIgnoreCompiler2:TARGET=compiler1)

##################################################
# Server(C2) compiler specific options
##################################################
#NOTE! This list must be kept in sync with GENERATED_NAMES in adlc.make.
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
 -define_compiler2 COMPILER2 \
 -additionalFile_compiler2 $(Platform_arch_model).ad \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles ad_$(Platform_arch_model).cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles ad_$(Platform_arch_model).hpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles ad_$(Platform_arch_model)_clone.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles ad_$(Platform_arch_model)_expand.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles ad_$(Platform_arch_model)_format.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles ad_$(Platform_arch_model)_gen.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles ad_$(Platform_arch_model)_misc.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles ad_$(Platform_arch_model)_peephole.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles ad_$(Platform_arch_model)_pipeline.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles adGlobals_$(Platform_arch_model).hpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/generated/adfiles dfa_$(Platform_arch_model).cpp \
 $(ProjectCreatorIDEOptionsIgnoreCompiler1:TARGET=compiler2)

# Add in the jvmti (JSR-163) options
# NOTE: do not pull in jvmtiEnvRecommended.cpp.  This file is generated
#       so the programmer can diff it with jvmtiEnv.cpp to be sure the
#       code merge was done correctly (@see jvmti.make and jvmtiEnvFill.java).
#       If so, they would then check it in as a new version of jvmtiEnv.cpp.
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/%f/generated/jvmtifiles jvmtiEnv.hpp \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/%f/generated/jvmtifiles jvmtiEnter.cpp \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/%f/generated/jvmtifiles jvmtiEnterTrace.cpp \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/%f/generated/jvmtifiles jvmti.h \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/%f/generated/jvmtifiles bytecodeInterpreterWithChecks.cpp
