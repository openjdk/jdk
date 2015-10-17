#
# Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

ProjectCreatorSources=\
        $(WorkSpace)\src\share\tools\ProjectCreator\ProjectCreator.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\FileTreeCreator.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\FileTreeCreatorVC10.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\WinGammaPlatform.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\WinGammaPlatformVC10.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\Util.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\BuildConfig.java \
        $(WorkSpace)\src\share\tools\ProjectCreator\ArgsParser.java

# This is only used internally
ProjectCreatorIncludesPRIVATE=\
        -relativeAltSrcInclude src\closed \
        -altRelativeInclude share\vm \
        -altRelativeInclude os\windows\vm \
        -altRelativeInclude os_cpu\windows_$(Platform_arch)\vm \
        -altRelativeInclude cpu\$(Platform_arch)\vm \
        -relativeInclude src\share\vm \
        -relativeInclude src\share\vm\precompiled \
        -relativeInclude src\share\vm\prims\wbtestmethods \
        -relativeInclude src\share\vm\prims \
        -relativeInclude src\os\windows\vm \
        -relativeInclude src\os_cpu\windows_$(Platform_arch)\vm \
        -relativeInclude src\cpu\$(Platform_arch)\vm \
        -absoluteInclude $(HOTSPOTBUILDSPACE)/%f/generated \
        -relativeSrcInclude src \
        -absoluteSrcInclude $(HOTSPOTBUILDSPACE) \
        -ignorePath $(HOTSPOTBUILDSPACE) \
        -ignorePath share\vm\adlc \
        -ignorePath share\vm\shark \
        -ignorePath share\tools \
        -ignorePath solaris \
        -ignorePath posix \
        -ignorePath sparc \
        -ignorePath linux \
        -ignorePath bsd \
        -ignorePath osx \
        -ignorePath arm \
        -ignorePath ppc \
        -ignorePath zero \
        -ignorePath aix \
        -ignorePath aarch64 \
        -hidePath .hg


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

!if "$(BUILD_WIN_SA)" != "1"
BUILD_VM_DEF_FLAG=-nosa
!endif

# Common options for the IDE builds for c1, and c2
ProjectCreatorIDEOptions=\
        $(ProjectCreatorIDEOptions) \
        -sourceBase $(HOTSPOTWORKSPACE) \
        -buildBase $(HOTSPOTBUILDSPACE)\%f\%b \
        -buildSpace $(HOTSPOTBUILDSPACE) \
        -startAt src \
        -compiler $(VcVersion) \
        -projectFileName $(HOTSPOTBUILDSPACE)\$(ProjectFile) \
        -jdkTargetRoot $(HOTSPOTJDKDIST) \
        -define ALIGN_STACK_FRAMES \
        -define VM_LITTLE_ENDIAN \
        -prelink  "" "Generating vm.def..." "cd $(HOTSPOTBUILDSPACE)\%f\%b	set HOTSPOTMKSHOME=$(HOTSPOTMKSHOME)	set JAVA_HOME=$(HOTSPOTJDKDIST)	$(HOTSPOTMKSHOME)\sh $(HOTSPOTWORKSPACE)\make\windows\build_vm_def.sh $(BUILD_VM_DEF_FLAG) $(LD_VER)" \
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
        -define INCLUDE_TRACE=1 \
       $(ProjectCreatorIncludesPRIVATE)

# Add in build-specific options
!if "$(BUILDARCH)" == "i486"
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
	-platformName Win32 \
        -define IA32 \
        -ignorePath x86_64 \
        -define TARGET_ARCH_MODEL_x86_32
!else
!if "$(BUILDARCH)" == "amd64"
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
	-platformName x64 \
        -define AMD64 \
	-define _LP64 \
        -ignorePath x86_32 \
        -define TARGET_ARCH_MODEL_x86_64 \
	-define TARGET_OS_ARCH_MODEL_windows_x86_64
!endif
!endif

ProjectCreatorIDEOptionsIgnoreCompiler1=\
 -ignorePath_TARGET compiler1 \
 -ignorePath_TARGET tiered \
 -ignorePath_TARGET c1_

ProjectCreatorIDEOptionsIgnoreJVMCI=\
 -ignorePath_TARGET src/share/vm/jvmci \
 -ignorePath_TARGET vm/jvmci

ProjectCreatorIDEOptionsIgnoreCompiler2=\
 -ignorePath_TARGET compiler2 \
 -ignorePath_TARGET tiered \
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
# Client(C1) compiler specific options
##################################################
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
 -define_compiler1 COMPILER1 \
 -define_compiler1 INCLUDE_JVMCI=0 \
$(ProjectCreatorIDEOptionsIgnoreJVMCI:TARGET=compiler1) \
$(ProjectCreatorIDEOptionsIgnoreCompiler2:TARGET=compiler1)

##################################################
# Server(C2) compiler specific options
##################################################
#NOTE! This list must be kept in sync with GENERATED_NAMES in adlc.make.
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
 -define_compiler2 COMPILER2 \
 -additionalFile_compiler2 $(Platform_arch_model).ad \
 -additionalFile_compiler2 ad_$(Platform_arch_model).cpp \
 -additionalFile_compiler2 ad_$(Platform_arch_model).hpp \
 -additionalFile_compiler2 ad_$(Platform_arch_model)_clone.cpp \
 -additionalFile_compiler2 ad_$(Platform_arch_model)_expand.cpp \
 -additionalFile_compiler2 ad_$(Platform_arch_model)_format.cpp \
 -additionalFile_compiler2 ad_$(Platform_arch_model)_gen.cpp \
 -additionalFile_compiler2 ad_$(Platform_arch_model)_misc.cpp \
 -additionalFile_compiler2 ad_$(Platform_arch_model)_peephole.cpp \
 -additionalFile_compiler2 ad_$(Platform_arch_model)_pipeline.cpp \
 -additionalFile_compiler2 adGlobals_$(Platform_arch_model).hpp \
 -additionalFile_compiler2 dfa_$(Platform_arch_model).cpp \
 $(ProjectCreatorIDEOptionsIgnoreCompiler1:TARGET=compiler2)

# Add in the jvmti (JSR-163) options
# NOTE: do not pull in jvmtiEnvRecommended.cpp.  This file is generated
#       so the programmer can diff it with jvmtiEnv.cpp to be sure the
#       code merge was done correctly (@see jvmti.make and jvmtiEnvFill.java).
#       If so, they would then check it in as a new version of jvmtiEnv.cpp.
ProjectCreatorIDEOptions=$(ProjectCreatorIDEOptions) \
 -additionalFile jvmtiEnv.hpp \
 -additionalFile jvmtiEnter.cpp \
 -additionalFile jvmtiEnterTrace.cpp \
 -additionalFile jvmti.h \
 -additionalFile bytecodeInterpreterWithChecks.cpp \
 -additionalFile traceEventClasses.hpp \
 -additionalFile traceEventIds.hpp \
!if "$(OPENJDK)" != "true"
 -additionalFile traceRequestables.hpp \
 -additionalFile traceEventControl.hpp \
 -additionalFile traceProducer.cpp \
!endif
 -additionalFile traceTypes.hpp
