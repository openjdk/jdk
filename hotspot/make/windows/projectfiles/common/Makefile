#
# Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

WorkSpace=$(HOTSPOTWORKSPACE)

!ifdef ALT_BOOTDIR
BootStrapDir=$(ALT_BOOTDIR)
!else
!ifdef BOOTDIR
BootStrapDir=$(BOOTDIR)
!else
!ifdef JAVA_HOME
BootStrapDir=$(JAVA_HOME)
!endif
!endif
!endif

!include $(HOTSPOTWORKSPACE)/make/windows/makefiles/makedeps.make

# Pick up rules for building JVMTI (JSR-163)
JvmtiOutDir=$(HOTSPOTBUILDSPACE)\jvmtifiles
!include $(HOTSPOTWORKSPACE)/make/windows/makefiles/jvmti.make

Platform=$(HOTSPOTWORKSPACE)/make/windows/platform_$(BUILDARCH)

default:: $(AdditionalTargets) $(JvmtiGeneratedFiles)

IncludeDBs_base=$(HOTSPOTWORKSPACE)/src/share/vm/includeDB_core  \
                $(HOTSPOTWORKSPACE)/src/share/vm/includeDB_jvmti \
                $(HOTSPOTWORKSPACE)/src/share/vm/includeDB_gc    \
                $(HOTSPOTWORKSPACE)/src/share/vm/gc_implementation/includeDB_gc_serial

# Parallel gc files
IncludeDBs_gc=$(HOTSPOTWORKSPACE)/src/share/vm/includeDB_gc_parallel       \
    $(HOTSPOTWORKSPACE)/src/share/vm/gc_implementation/includeDB_gc_shared \
    $(HOTSPOTWORKSPACE)/src/share/vm/gc_implementation/includeDB_gc_parNew \
    $(HOTSPOTWORKSPACE)/src/share/vm/gc_implementation/includeDB_gc_parallelScavenge \
    $(HOTSPOTWORKSPACE)/src/share/vm/gc_implementation/includeDB_gc_concurrentMarkSweep \
    $(HOTSPOTWORKSPACE)/src/share/vm/gc_implementation/includeDB_gc_g1


IncludeDBs_kernel =$(IncludeDBs_base) \
                    $(HOTSPOTWORKSPACE)/src/share/vm/includeDB_compiler1

IncludeDBs_core =$(IncludeDBs_base) $(IncludeDBs_gc) \
                 $(HOTSPOTWORKSPACE)/src/share/vm/includeDB_features

IncludeDBs_compiler1=$(IncludeDBs_core) \
                     $(HOTSPOTWORKSPACE)/src/share/vm/includeDB_compiler1

IncludeDBs_compiler2=$(IncludeDBs_core) \
                    $(HOTSPOTWORKSPACE)/src/share/vm/includeDB_compiler2

IncludeDBs_tiered=$(IncludeDBs_core) \
                    $(HOTSPOTWORKSPACE)/src/share/vm/includeDB_compiler1 \
                    $(HOTSPOTWORKSPACE)/src/share/vm/includeDB_compiler2


!if "$(Variant)" == "compiler1"
IncludeDBs = $(IncludeDBs_compiler1)
!endif

!if "$(Variant)" == "compiler2"
IncludeDBs = $(IncludeDBs_compiler2)
# Pick up rules for building adlc
!include $(HOTSPOTWORKSPACE)/make/windows/makefiles/adlc.make
!endif

!if "$(Variant)" == "tiered"
IncludeDBs = $(IncludeDBs_tiered)
# Pick up rules for building adlc
!include $(HOTSPOTWORKSPACE)/make/windows/makefiles/adlc.make
!endif

!if "$(Variant)" == "core"
IncludeDBs = $(IncludeDBs_core)
!endif

!if "$(Variant)" == "kernel"
IncludeDBs = $(IncludeDBs_kernel)
!endif

!include $(HOTSPOTWORKSPACE)/make/hotspot_version

!if "$(HOTSPOT_RELEASE_VERSION)" != ""
HOTSPOT_RELEASE_VERSION="$(HOTSPOT_RELEASE_VERSION)"
!else
HOTSPOT_RELEASE_VERSION="$(HS_MAJOR_VER).$(HS_MINOR_VER)-b$(HS_BUILD_NUMBER)"
!endif
HOTSPOT_BUILD_VERSION$(HOTSPOT_BUILD_VERSION) = internal
!if "$(HOTSPOT_BUILD_VERSION)" != ""
HOTSPOT_RELEASE_VERSION="$(HOTSPOT_RELEASE_VERSION)-$(HOTSPOT_BUILD_VERSION)"
!endif
!if "$(JRE_RELEASE_VERSION)" != ""
JRE_RELEASE_VERSION="$(JRE_RELEASE_VERSION)"
!else
JRE_RELEASE_VERSION="$(JDK_MAJOR_VER).$(JDK_MINOR_VER).$(JDK_MICRO_VER)"
!endif

# Define HOTSPOT_VM_DISTRO if HOTSPOT_VM_DISTRO is set,
# and if it is not see if we have the src/closed directory
!if "$(HOTSPOT_VM_DISTRO)" != ""
HOTSPOT_VM_DISTRO="$(HOTSPOT_VM_DISTRO)"
!else
!if exists($(HOTSPOTWORKSPACE)\src\closed)
HOTSPOT_VM_DISTRO="Java HotSpot(TM)"
!else
HOTSPOT_VM_DISTRO="OpenJDK"
!endif
!endif

MakeDepsIDEOptions =       $(MakeDepsIDEOptions) \
      -includeDB_kernel    $(HOTSPOTBUILDSPACE)\includeDB_kernel \
      -includeDB_core      $(HOTSPOTBUILDSPACE)\includeDB_core \
      -includeDB_compiler1 $(HOTSPOTBUILDSPACE)\includeDB_compiler1 \
      -includeDB_compiler2 $(HOTSPOTBUILDSPACE)\includeDB_compiler2 \
      -includeDB_tiered    $(HOTSPOTBUILDSPACE)\includeDB_tiered \
      -platform            $(Platform) \
      -define              HOTSPOT_RELEASE_VERSION=\\\"$(HOTSPOT_RELEASE_VERSION)\\\" \
      -define              JRE_RELEASE_VERSION=\\\"$(JRE_RELEASE_VERSION)\\\" \
      -define              HOTSPOT_VM_DISTRO=\\\"$(HOTSPOT_VM_DISTRO)\\\"

incls:
	@mkdir incls

includeDB.current $(ProjectFile) Dependencies: local.make $(HOTSPOTBUILDSPACE)/classes/MakeDeps.class \
                                               $(IncludeDBs) incls
	@rm -f includeDB $(HOTSPOTBUILDSPACE)\includeDB_kernel \
                         $(HOTSPOTBUILDSPACE)\includeDB_core \
                         $(HOTSPOTBUILDSPACE)\includeDB_compiler1 \
                         $(HOTSPOTBUILDSPACE)\includeDB_compiler2 \
                         $(HOTSPOTBUILDSPACE)\includeDB_tiered
	@cat $(IncludeDBs_kernel) > $(HOTSPOTBUILDSPACE)\includeDB_kernel
	@cat $(IncludeDBs_core) > $(HOTSPOTBUILDSPACE)\includeDB_core
	@cat $(IncludeDBs_compiler1)   > $(HOTSPOTBUILDSPACE)\includeDB_compiler1
	@cat $(IncludeDBs_compiler2)   > $(HOTSPOTBUILDSPACE)\includeDB_compiler2
	@cat $(IncludeDBs_tiered)      > $(HOTSPOTBUILDSPACE)\includeDB_tiered
	@echo java.cpp jni.h >  includeDB
	@$(RUN_JAVA) -Djava.class.path=$(HOTSPOTBUILDSPACE)/classes MakeDeps diffs WinGammaPlatform$(VcVersion) \
                  $(Platform) includeDB.current $(Platform) includeDB $(MakeDepsOptions) $(MakeDepsIDEOptions)
	@rm -f includeDB.current
	@cp includeDB includeDB.current

lists: $(HOTSPOTBUILDSPACE)/classes/MakeDeps.class FORCE
	@if exist incls rmdir /s /q incls
	@rm -f includeDB
	@cat $(IncludeDBs) > includeDB
	@mkdir incls
	@$(RUN_JAVA) -Djava.class.path=$(HOTSPOTBUILDSPACE)/classes MakeDeps WinGammaPlatform$(VcVersion) \
                 $(Platform) includeDB $(MakeDepsOptions) $(MakeDepsIDEOptions)
	@rm -f includeDB.current
	@cp includeDB includeDB.current

clean:
	@rm -rf incls $(HOTSPOTBUILDSPACE)/classes
	@rm -f includeDB includeDB.current $(ProjectFile) Dependencies

$(HOTSPOTBUILDSPACE)/classes/MakeDeps.class: $(MakeDepsSources)
	@if exist $(HOTSPOTBUILDSPACE)\classes rmdir /s /q $(HOTSPOTBUILDSPACE)\classes
	@mkdir $(HOTSPOTBUILDSPACE)\classes
	@$(COMPILE_JAVAC) -classpath $(HOTSPOTWORKSPACE)\src\share\tools\MakeDeps -d $(HOTSPOTBUILDSPACE)/classes $(MakeDepsSources)

FORCE:
