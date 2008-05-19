#
# Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
# Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# CA 95054 USA or visit www.sun.com if you need additional information or
# have any questions.
#  
#

HS_INTERNAL_NAME=jvm
HS_FNAME=$(HS_INTERNAL_NAME).dll
AOUT=$(HS_FNAME)
SAWINDBG=sawindbg.dll
GENERATED=../generated

default:: _build_pch_file.obj $(AOUT) checkAndBuildSA

!include ../local.make
!include compile.make

CPP_FLAGS=$(CPP_FLAGS) $(FASTDEBUG_OPT_OPTION)

!include $(WorkSpace)/make/windows/makefiles/vm.make
!include local.make

!include $(GENERATED)/Dependencies

HS_BUILD_ID=$(HS_BUILD_VER)-fastdebug

# Force resources to be rebuilt every time
$(Res_Files): FORCE

$(AOUT): $(Res_Files) $(Obj_Files)
	sh $(WorkSpace)/make/windows/build_vm_def.sh
	$(LINK) @<<
  $(LINK_FLAGS) /out:$@ /implib:$*.lib /def:vm.def $(Obj_Files) $(Res_Files)
<<

!include $(WorkSpace)/make/windows/makefiles/shared.make
!include $(WorkSpace)/make/windows/makefiles/sa.make
