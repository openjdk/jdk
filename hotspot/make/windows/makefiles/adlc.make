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

!include $(WorkSpace)/make/windows/makefiles/compile.make

# Rules for building adlc.exe

# Need exception handling support here
# $(MS_RUNTIME_OPTION) ( with /D_STATIC_CPPLIB)
# causes adlc.exe to link with the static
# multithread Standard C++ library (libcpmt.lib) instead of
# the dynamic version (msvcprt.lib), which is not included
# in any of the free tools.
EXH_FLAGS=$(GX_OPTION) $(MS_RUNTIME_OPTION)

!ifdef ALT_ADLC_PATH
ADLC=$(ALT_ADLC_PATH)\adlc.exe
!else
ADLC=adlc
!endif

!ifdef LP64
ADLCFLAGS=-q -T -D_LP64
!else
ADLCFLAGS=-q -T -U_LP64
!endif

CPP_FLAGS=$(CPP_FLAGS) /D _CRT_SECURE_NO_WARNINGS /D _CRT_SECURE_NO_DEPRECATE  

CPP_INCLUDE_DIRS=\
  /I "..\generated" \
  /I "$(WorkSpace)\src\share\vm" \
  /I "$(WorkSpace)\src\os\windows\vm" \
  /I "$(WorkSpace)\src\cpu\$(Platform_arch)\vm"

# NOTE! If you add any files here, you must also update GENERATED_NAMES_IN_DIR
# and ProjectCreatorIDEOptions in projectcreator.make. 
GENERATED_NAMES=\
  ad_$(Platform_arch_model).cpp \
  ad_$(Platform_arch_model).hpp \
  ad_$(Platform_arch_model)_clone.cpp \
  ad_$(Platform_arch_model)_expand.cpp \
  ad_$(Platform_arch_model)_format.cpp \
  ad_$(Platform_arch_model)_gen.cpp \
  ad_$(Platform_arch_model)_misc.cpp \
  ad_$(Platform_arch_model)_peephole.cpp \
  ad_$(Platform_arch_model)_pipeline.cpp \
  adGlobals_$(Platform_arch_model).hpp \
  dfa_$(Platform_arch_model).cpp

# NOTE! This must be kept in sync with GENERATED_NAMES
GENERATED_NAMES_IN_DIR=\
  $(AdlcOutDir)\ad_$(Platform_arch_model).cpp \
  $(AdlcOutDir)\ad_$(Platform_arch_model).hpp \
  $(AdlcOutDir)\ad_$(Platform_arch_model)_clone.cpp \
  $(AdlcOutDir)\ad_$(Platform_arch_model)_expand.cpp \
  $(AdlcOutDir)\ad_$(Platform_arch_model)_format.cpp \
  $(AdlcOutDir)\ad_$(Platform_arch_model)_gen.cpp \
  $(AdlcOutDir)\ad_$(Platform_arch_model)_misc.cpp \
  $(AdlcOutDir)\ad_$(Platform_arch_model)_peephole.cpp \
  $(AdlcOutDir)\ad_$(Platform_arch_model)_pipeline.cpp \
  $(AdlcOutDir)\adGlobals_$(Platform_arch_model).hpp \
  $(AdlcOutDir)\dfa_$(Platform_arch_model).cpp

{$(WorkSpace)\src\share\vm\adlc}.cpp.obj::
        $(CPP) $(CPP_FLAGS) $(EXH_FLAGS) $(CPP_INCLUDE_DIRS) /c $<

{$(WorkSpace)\src\share\vm\opto}.cpp.obj::
        $(CPP) $(CPP_FLAGS) $(EXH_FLAGS) $(CPP_INCLUDE_DIRS) /c $<

adlc.exe: main.obj adlparse.obj archDesc.obj arena.obj dfa.obj dict2.obj filebuff.obj \
          forms.obj formsopt.obj formssel.obj opcodes.obj output_c.obj output_h.obj
	$(LINK) $(LINK_FLAGS) /subsystem:console /out:$@ $**
!if "$(MT)" != ""
# The previous link command created a .manifest file that we want to
# insert into the linked artifact so we do not need to track it
# separately.  Use ";#2" for .dll and ";#1" for .exe:
	$(MT) /manifest $@.manifest /outputresource:$@;#1
!endif

$(GENERATED_NAMES_IN_DIR): $(Platform_arch_model).ad adlc.exe
	rm -f $(GENERATED_NAMES)
	if exist $(AdlcOutDir) rmdir /s /q $(AdlcOutDir)
	mkdir $(AdlcOutDir)
	$(ADLC) $(ADLCFLAGS) $(Platform_arch_model).ad
	mv $(GENERATED_NAMES) $(AdlcOutDir)/

$(Platform_arch_model).ad: $(WorkSpace)/src/cpu/$(Platform_arch)/vm/$(Platform_arch_model).ad $(WorkSpace)/src/os_cpu/windows_$(Platform_arch)/vm/windows_$(Platform_arch_model).ad
	rm -f $(Platform_arch_model).ad
	cat $(WorkSpace)/src/cpu/$(Platform_arch)/vm/$(Platform_arch_model).ad  \
	    $(WorkSpace)/src/os_cpu/windows_$(Platform_arch)/vm/windows_$(Platform_arch_model).ad >$(Platform_arch_model).ad
