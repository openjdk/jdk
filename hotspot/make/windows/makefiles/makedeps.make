#
# Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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
# NOTE: unfortunately the MakeDepsSources list must be kept
# synchronized between this and the Solaris version
# (make/solaris/makefiles/makedeps.make).

MakeDepsSources=\
        $(WorkSpace)\src\share\tools\MakeDeps\Database.java \
        $(WorkSpace)\src\share\tools\MakeDeps\DirectoryTree.java \
        $(WorkSpace)\src\share\tools\MakeDeps\DirectoryTreeNode.java \
        $(WorkSpace)\src\share\tools\MakeDeps\FileFormatException.java \
        $(WorkSpace)\src\share\tools\MakeDeps\FileList.java \
        $(WorkSpace)\src\share\tools\MakeDeps\FileName.java \
        $(WorkSpace)\src\share\tools\MakeDeps\Macro.java \
        $(WorkSpace)\src\share\tools\MakeDeps\MacroDefinitions.java \
        $(WorkSpace)\src\share\tools\MakeDeps\MakeDeps.java \
        $(WorkSpace)\src\share\tools\MakeDeps\MetroWerksMacPlatform.java \
        $(WorkSpace)\src\share\tools\MakeDeps\Platform.java \
        $(WorkSpace)\src\share\tools\MakeDeps\UnixPlatform.java \
        $(WorkSpace)\src\share\tools\MakeDeps\WinGammaPlatform.java \
        $(WorkSpace)\src\share\tools\MakeDeps\WinGammaPlatformVC6.java \
        $(WorkSpace)\src\share\tools\MakeDeps\WinGammaPlatformVC7.java \
        $(WorkSpace)\src\share\tools\MakeDeps\WinGammaPlatformVC8.java \
        $(WorkSpace)\src\share\tools\MakeDeps\WinGammaPlatformVC9.java \
        $(WorkSpace)\src\share\tools\MakeDeps\Util.java \
        $(WorkSpace)\src\share\tools\MakeDeps\BuildConfig.java \
        $(WorkSpace)\src\share\tools\MakeDeps\ArgsParser.java

# This is only used internally
MakeDepsIncludesPRIVATE=\
        -relativeInclude src\share\vm\c1 \
        -relativeInclude src\share\vm\compiler \
        -relativeInclude src\share\vm\code \
        -relativeInclude src\share\vm\interpreter \
        -relativeInclude src\share\vm\ci \
        -relativeInclude src\share\vm\classfile \
        -relativeInclude src\share\vm\gc_implementation\parallelScavenge \
        -relativeInclude src\share\vm\gc_implementation\shared \
        -relativeInclude src\share\vm\gc_implementation\parNew \
        -relativeInclude src\share\vm\gc_implementation\concurrentMarkSweep \
        -relativeInclude src\share\vm\gc_implementation\g1 \
        -relativeInclude src\share\vm\gc_interface \
        -relativeInclude src\share\vm\asm \
        -relativeInclude src\share\vm\memory \
        -relativeInclude src\share\vm\oops \
        -relativeInclude src\share\vm\prims \
        -relativeInclude src\share\vm\runtime \
        -relativeInclude src\share\vm\services \
        -relativeInclude src\share\vm\utilities \
        -relativeInclude src\share\vm\libadt \
        -relativeInclude src\share\vm\opto \
        -relativeInclude src\os\windows\vm \
        -relativeInclude src\os_cpu\windows_$(Platform_arch)\vm \
        -relativeInclude src\cpu\$(Platform_arch)\vm

# This is referenced externally by both the IDE and batch builds
MakeDepsOptions=

# This is used externally, but only by the IDE builds, so we can
# reference environment variables which aren't defined in the batch
# build process.

MakeDepsIDEOptions = \
        -useToGeneratePch  java.cpp \
        -disablePch        os_windows.cpp \
        -disablePch        os_windows_$(Platform_arch).cpp \
        -disablePch        osThread_windows.cpp \
        -disablePch        bytecodeInterpreter.cpp \
        -disablePch        bytecodeInterpreterWithChecks.cpp \
	-disablePch        getThread_windows_$(Platform_arch).cpp \
        -disablePch_compiler2     opcodes.cpp    

# Common options for the IDE builds for core, c1, and c2
MakeDepsIDEOptions=\
        $(MakeDepsIDEOptions) \
        -sourceBase $(HOTSPOTWORKSPACE) \
	-buildBase $(HOTSPOTBUILDSPACE)\%f\%b \
        -startAt src \
	-compiler $(VcVersion) \
        -projectFileName $(HOTSPOTBUILDSPACE)\$(ProjectFile) \
        -jdkTargetRoot $(HOTSPOTJDKDIST) \
        -define ALIGN_STACK_FRAMES \
        -define VM_LITTLE_ENDIAN \
        -additionalFile includeDB_compiler1 \
        -additionalFile includeDB_compiler2 \
        -additionalFile includeDB_core \
        -additionalFile includeDB_features \
        -additionalFile includeDB_jvmti \
        -additionalFile includeDB_gc \
        -additionalFile includeDB_gc_parallel \
        -additionalFile includeDB_gc_parallelScavenge \
        -additionalFile includeDB_gc_concurrentMarkSweep \
        -additionalFile includeDB_gc_g1 \
        -additionalFile includeDB_gc_parNew \
        -additionalFile includeDB_gc_shared \
        -additionalFile includeDB_gc_serial \
        -additionalGeneratedFile $(HOTSPOTBUILDSPACE)\%f\%b vm.def \
        -prelink  "" "Generating vm.def..." "cd $(HOTSPOTBUILDSPACE)\%f\%b	set HOTSPOTMKSHOME=$(HOTSPOTMKSHOME)	$(HOTSPOTMKSHOME)\sh $(HOTSPOTWORKSPACE)\make\windows\build_vm_def.sh $(LINK_VER)" \
       $(MakeDepsIncludesPRIVATE)

# Add in build-specific options
!if "$(BUILDARCH)" == "i486"
MakeDepsIDEOptions=$(MakeDepsIDEOptions) -define IA32
!endif

##################################################
# JKERNEL specific options
##################################################
MakeDepsIDEOptions=$(MakeDepsIDEOptions) \
 -define_kernel KERNEL \

##################################################
# Client(C1) compiler specific options
##################################################
MakeDepsIDEOptions=$(MakeDepsIDEOptions) \
 -define_compiler1 COMPILER1 \

##################################################
# Server(C2) compiler specific options
##################################################
#NOTE! This list must be kept in sync with GENERATED_NAMES in adlc.make.
MakeDepsIDEOptions=$(MakeDepsIDEOptions) \
 -define_compiler2 COMPILER2 \
 -absoluteInclude_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls \
 -additionalFile_compiler2 $(Platform_arch_model).ad \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls ad_$(Platform_arch_model).cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls ad_$(Platform_arch_model).hpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls ad_$(Platform_arch_model)_clone.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls ad_$(Platform_arch_model)_expand.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls ad_$(Platform_arch_model)_format.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls ad_$(Platform_arch_model)_gen.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls ad_$(Platform_arch_model)_misc.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls ad_$(Platform_arch_model)_peephole.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls ad_$(Platform_arch_model)_pipeline.cpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls adGlobals_$(Platform_arch_model).hpp \
 -additionalGeneratedFile_compiler2 $(HOTSPOTBUILDSPACE)/%f/incls dfa_$(Platform_arch_model).cpp 

# Add in the jvmti (JSR-163) options
# NOTE: do not pull in jvmtiEnvRecommended.cpp.  This file is generated
#       so the programmer can diff it with jvmtiEnv.cpp to be sure the
#       code merge was done correctly (@see jvmti.make and jvmtiEnvFill.java).
#       If so, they would then check it in as a new version of jvmtiEnv.cpp.
MakeDepsIDEOptions=$(MakeDepsIDEOptions) \
 -absoluteInclude $(HOTSPOTBUILDSPACE)/jvmtifiles \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/jvmtifiles jvmtiEnv.hpp \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/jvmtifiles jvmtiEnter.cpp \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/jvmtifiles jvmtiEnterTrace.cpp \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/jvmtifiles jvmti.h \
 -additionalGeneratedFile $(HOTSPOTBUILDSPACE)/jvmtifiles bytecodeInterpreterWithChecks.cpp 
