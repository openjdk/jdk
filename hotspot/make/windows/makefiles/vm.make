#
# Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

# Resource file containing VERSIONINFO
Res_Files=.\version.res

!include ..\generated\objfiles.make

COMMONSRC=$(WorkSpace)\src
ALTSRC=$(WorkSpace)\src\closed

!ifdef RELEASE
CXX_FLAGS=$(CXX_FLAGS) /D "PRODUCT"
!else
CXX_FLAGS=$(CXX_FLAGS) /D "ASSERT"
!if "$(BUILDARCH)" == "amd64"
CXX_FLAGS=$(CXX_FLAGS) /homeparams
!endif
!endif

!if "$(Variant)" == "compiler1"
CXX_FLAGS=$(CXX_FLAGS) /D "COMPILER1" /D INCLUDE_JVMCI=0
!endif

!if "$(Variant)" == "compiler2"
CXX_FLAGS=$(CXX_FLAGS) /D "COMPILER2"
!if "$(BUILDARCH)" == "i486"
CXX_FLAGS=$(CXX_FLAGS) /D INCLUDE_JVMCI=0
!endif
!endif

!if "$(Variant)" == "tiered"
CXX_FLAGS=$(CXX_FLAGS) /D "COMPILER1" /D "COMPILER2"
!if "$(BUILDARCH)" == "i486"
CXX_FLAGS=$(CXX_FLAGS) /D INCLUDE_JVMCI=0
!endif
!endif

!if "$(BUILDARCH)" == "i486"
HOTSPOT_LIB_ARCH=i386
!else
HOTSPOT_LIB_ARCH=$(BUILDARCH)
!endif

# The following variables are defined in the generated local.make file.
CXX_FLAGS=$(CXX_FLAGS) /D "HOTSPOT_VERSION_STRING=\"$(HOTSPOT_VERSION_STRING)\""
CXX_FLAGS=$(CXX_FLAGS) /D "VERSION_MAJOR=$(VERSION_MAJOR)"
CXX_FLAGS=$(CXX_FLAGS) /D "VERSION_MINOR=$(VERSION_MINOR)"
CXX_FLAGS=$(CXX_FLAGS) /D "VERSION_SECURITY=$(VERSION_SECURITY)"
CXX_FLAGS=$(CXX_FLAGS) /D "VERSION_PATCH=$(VERSION_PATCH)"
CXX_FLAGS=$(CXX_FLAGS) /D "VERSION_BUILD=$(VERSION_BUILD)"
CXX_FLAGS=$(CXX_FLAGS) /D "VERSION_STRING=\"$(VERSION_STRING)\""
CXX_FLAGS=$(CXX_FLAGS) /D "DEBUG_LEVEL=\"$(DEBUG_LEVEL)\""
CXX_FLAGS=$(CXX_FLAGS) /D "HOTSPOT_LIB_ARCH=\"$(HOTSPOT_LIB_ARCH)\""
CXX_FLAGS=$(CXX_FLAGS) /D "HOTSPOT_BUILD_TARGET=\"$(BUILD_FLAVOR)\""
CXX_FLAGS=$(CXX_FLAGS) /D "HOTSPOT_BUILD_USER=\"$(BuildUser)\""
CXX_FLAGS=$(CXX_FLAGS) /D "HOTSPOT_VM_DISTRO=\"$(HOTSPOT_VM_DISTRO)\""

CXX_FLAGS=$(CXX_FLAGS) $(CXX_INCLUDE_DIRS)

# Define that so jni.h is on correct side
CXX_FLAGS=$(CXX_FLAGS) /D "_JNI_IMPLEMENTATION_"

!if "$(BUILDARCH)" == "ia64"
STACK_SIZE="/STACK:1048576,262144"
!else
STACK_SIZE=
!endif

!if "$(BUILDARCH)" == "ia64"
# AsyncGetCallTrace is not supported on IA64 yet
AGCT_EXPORT=
!else
AGCT_EXPORT=/export:AsyncGetCallTrace
!endif

# If you modify exports below please do the corresponding changes in
# src/share/tools/ProjectCreator/WinGammaPlatformVC7.java
!if "$(BUILDARCH)" == "amd64"
EXPORT_LIST=
!else
EXPORT_LIST=/export:JNI_GetDefaultJavaVMInitArgs \
            /export:JNI_CreateJavaVM             \
            /export:JVM_FindClassFromBootLoader  \
            /export:JNI_GetCreatedJavaVMs        \
            /export:jio_snprintf                 \
            /export:jio_printf                   \
            /export:jio_fprintf                  \
            /export:jio_vfprintf                 \
            /export:jio_vsnprintf                \
            $(AGCT_EXPORT)                       \
            /export:JVM_GetVersionInfo           \
            /export:JVM_InitAgentProperties
!endif

LD_FLAGS=$(LD_FLAGS) $(STACK_SIZE) /subsystem:windows /dll /base:0x8000000 $(EXPORT_LIST)

CXX_INCLUDE_DIRS=/I "..\generated"

!ifndef OPENJDK
!if exists($(ALTSRC)\share\vm)
CXX_INCLUDE_DIRS=$(CXX_INCLUDE_DIRS) /I "$(ALTSRC)\share\vm"
!endif

!if exists($(ALTSRC)\os\windows\vm)
CXX_INCLUDE_DIRS=$(CXX_INCLUDE_DIRS) /I "$(ALTSRC)\os\windows\vm"
!endif

!if exists($(ALTSRC)\os_cpu\windows_$(Platform_arch)\vm)
CXX_INCLUDE_DIRS=$(CXX_INCLUDE_DIRS) /I "$(ALTSRC)\os_cpu\windows_$(Platform_arch)\vm"
!endif

!if exists($(ALTSRC)\cpu\$(Platform_arch)\vm)
CXX_INCLUDE_DIRS=$(CXX_INCLUDE_DIRS) /I "$(ALTSRC)\cpu\$(Platform_arch)\vm"
!endif
!endif # OPENJDK

CXX_INCLUDE_DIRS=$(CXX_INCLUDE_DIRS) \
  /I "$(COMMONSRC)\share\vm" \
  /I "$(COMMONSRC)\share\vm\precompiled" \
  /I "$(COMMONSRC)\share\vm\prims" \
  /I "$(COMMONSRC)\os\windows\vm" \
  /I "$(COMMONSRC)\os_cpu\windows_$(Platform_arch)\vm" \
  /I "$(COMMONSRC)\cpu\$(Platform_arch)\vm"

CXX_DONT_USE_PCH=/D DONT_USE_PRECOMPILED_HEADER

!if "$(USE_PRECOMPILED_HEADER)" != "0"
CXX_USE_PCH=/Fp"vm.pch" /Yu"precompiled.hpp"
!if "$(MSC_VER)" > "1600"
# VS2012 requires this object file to be listed:
LD_FLAGS=$(LD_FLAGS) _build_pch_file.obj
!endif
!else
CXX_USE_PCH=$(CXX_DONT_USE_PCH)
!endif

# Where to find the source code for the virtual machine (is this used?)
VM_PATH=../generated
VM_PATH=$(VM_PATH);../generated/adfiles
VM_PATH=$(VM_PATH);../generated/jvmtifiles
VM_PATH=$(VM_PATH);../generated/tracefiles
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/c1
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/jvmci
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/compiler
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/code
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/interpreter
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/ci
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/classfile
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/gc/parallel
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/gc/shared
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/gc/serial
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/gc/cms
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/gc/g1
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/asm
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/logging
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/memory
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/oops
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/prims
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/prims/wbtestmethods
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/runtime
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/services
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/trace
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/utilities
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/libadt
VM_PATH=$(VM_PATH);$(WorkSpace)/src/os/windows/vm
VM_PATH=$(VM_PATH);$(WorkSpace)/src/os_cpu/windows_$(Platform_arch)/vm
VM_PATH=$(VM_PATH);$(WorkSpace)/src/cpu/$(Platform_arch)/vm
VM_PATH=$(VM_PATH);$(WorkSpace)/src/share/vm/opto

!ifndef OPENJDK
!if exists($(ALTSRC)\share\vm\jfr)
VM_PATH=$(VM_PATH);$(ALTSRC)/share/vm/jfr
VM_PATH=$(VM_PATH);$(ALTSRC)/share/vm/jfr/buffers
!endif
!endif # OPENJDK

VM_PATH={$(VM_PATH)}

# Special case files not using precompiled header files.

c1_RInfo_$(Platform_arch).obj: $(WorkSpace)\src\cpu\$(Platform_arch)\vm\c1_RInfo_$(Platform_arch).cpp
	 $(CXX) $(CXX_FLAGS) $(CXX_DONT_USE_PCH) /c $(WorkSpace)\src\cpu\$(Platform_arch)\vm\c1_RInfo_$(Platform_arch).cpp

os_windows.obj: $(WorkSpace)\src\os\windows\vm\os_windows.cpp
        $(CXX) $(CXX_FLAGS) $(CXX_DONT_USE_PCH) /c $(WorkSpace)\src\os\windows\vm\os_windows.cpp

os_windows_$(Platform_arch).obj: $(WorkSpace)\src\os_cpu\windows_$(Platform_arch)\vm\os_windows_$(Platform_arch).cpp
        $(CXX) $(CXX_FLAGS) $(CXX_DONT_USE_PCH) /c $(WorkSpace)\src\os_cpu\windows_$(Platform_arch)\vm\os_windows_$(Platform_arch).cpp

osThread_windows.obj: $(WorkSpace)\src\os\windows\vm\osThread_windows.cpp
        $(CXX) $(CXX_FLAGS) $(CXX_DONT_USE_PCH) /c $(WorkSpace)\src\os\windows\vm\osThread_windows.cpp

conditionVar_windows.obj: $(WorkSpace)\src\os\windows\vm\conditionVar_windows.cpp
        $(CXX) $(CXX_FLAGS) $(CXX_DONT_USE_PCH) /c $(WorkSpace)\src\os\windows\vm\conditionVar_windows.cpp

getThread_windows_$(Platform_arch).obj: $(WorkSpace)\src\os_cpu\windows_$(Platform_arch)\vm\getThread_windows_$(Platform_arch).cpp
        $(CXX) $(CXX_FLAGS) $(CXX_DONT_USE_PCH) /c $(WorkSpace)\src\os_cpu\windows_$(Platform_arch)\vm\getThread_windows_$(Platform_arch).cpp

opcodes.obj: $(WorkSpace)\src\share\vm\opto\opcodes.cpp
        $(CXX) $(CXX_FLAGS) $(CXX_DONT_USE_PCH) /c $(WorkSpace)\src\share\vm\opto\opcodes.cpp

bytecodeInterpreter.obj: $(WorkSpace)\src\share\vm\interpreter\bytecodeInterpreter.cpp
        $(CXX) $(CXX_FLAGS) $(CXX_DONT_USE_PCH) /c $(WorkSpace)\src\share\vm\interpreter\bytecodeInterpreter.cpp

bytecodeInterpreterWithChecks.obj: ..\generated\jvmtifiles\bytecodeInterpreterWithChecks.cpp
        $(CXX) $(CXX_FLAGS) $(CXX_DONT_USE_PCH) /c ..\generated\jvmtifiles\bytecodeInterpreterWithChecks.cpp

# Default rules for the Virtual Machine
{$(COMMONSRC)\share\vm\c1}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\compiler}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\code}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\interpreter}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\ci}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\classfile}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\jvmci}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\gc\parallel}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\gc\shared}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\gc\serial}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\gc\cms}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\gc\g1}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\asm}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\logging}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\memory}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\oops}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\prims}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\prims\wbtestmethods}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\runtime}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\services}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\trace}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\utilities}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\libadt}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\share\vm\opto}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\os\windows\vm}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

# This guy should remain a single colon rule because
# otherwise we can't specify the output filename.
{$(COMMONSRC)\os\windows\vm}.rc.res:
        @$(RC) $(RC_FLAGS) /fo"$@" $<

{$(COMMONSRC)\cpu\$(Platform_arch)\vm}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(COMMONSRC)\os_cpu\windows_$(Platform_arch)\vm}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

!ifndef OPENJDK
{$(ALTSRC)\share\vm\c1}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\compiler}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\code}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\interpreter}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\ci}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\classfile}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\gc\parallel}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\gc\shared}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\gc\serial}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\gc\cms}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\gc\g1}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\asm}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\logging}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\memory}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\oops}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\prims}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\prims\wbtestmethods}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\runtime}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\services}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\trace}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\utilities}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\libadt}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\opto}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\os\windows\vm}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

# otherwise we can't specify the output filename.
{$(ALTSRC)\os\windows\vm}.rc.res:
        @$(RC) $(RC_FLAGS) /fo"$@" $<

{$(ALTSRC)\cpu\$(Platform_arch)\vm}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\os_cpu\windows_$(Platform_arch)\vm}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\jfr}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{$(ALTSRC)\share\vm\jfr\buffers}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<
!endif

{..\generated\incls}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{..\generated\adfiles}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{..\generated\jvmtifiles}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

{..\generated\tracefiles}.cpp.obj::
        $(CXX) $(CXX_FLAGS) $(CXX_USE_PCH) /c $<

default::

_build_pch_file.obj:
        @echo #include "precompiled.hpp" > ../generated/_build_pch_file.cpp
        $(CXX) $(CXX_FLAGS) /Fp"vm.pch" /Yc"precompiled.hpp" /c ../generated/_build_pch_file.cpp

vm.def: $(Obj_Files)
	sh $(WorkSpace)/make/windows/build_vm_def.sh
