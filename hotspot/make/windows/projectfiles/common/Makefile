#
# Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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

!ifdef LOCAL_MAKE
!include $(LOCAL_MAKE)
!endif


WorkSpace=$(HOTSPOTWORKSPACE)

!ifdef ALT_BOOTDIR
BootStrapDir=$(ALT_BOOTDIR)
!else
!ifdef BOOTDIR
BootStrapDir=$(BOOTDIR)
!else
!ifdef JAVA_HOME
BootStrapDir=$(JAVA_HOME)
!else
!ifdef HOTSPOTJDKDIST
BootStrapDir=$(HOTSPOTJDKDIST)
!endif
!endif
!endif
!endif

# if hotspot-only build and/or OPENJDK isn't passed down, need to set OPENJDK
!ifndef OPENJDK
!if !exists($(WorkSpace)\src\closed)
OPENJDK=true
!endif
!endif


!include $(HOTSPOTWORKSPACE)/make/windows/makefiles/projectcreator.make
!include $(WorkSpace)/make/windows/makefiles/compile.make

# Pick up rules for building JVMTI (JSR-163)
JvmtiOutDir=$(HOTSPOTBUILDSPACE)\$(Variant)\generated\jvmtifiles
!include $(HOTSPOTWORKSPACE)/make/windows/makefiles/jvmti.make

# Pick up rules for building trace
TraceOutDir=$(HOTSPOTBUILDSPACE)\$(Variant)\generated\tracefiles
!include $(HOTSPOTWORKSPACE)/make/windows/makefiles/trace.make

!if "$(Variant)" == "compiler2"
# Pick up rules for building adlc
!include $(HOTSPOTWORKSPACE)/make/windows/makefiles/adlc.make
!endif

!if "$(Variant)" == "tiered"
# Pick up rules for building adlc
!include $(HOTSPOTWORKSPACE)/make/windows/makefiles/adlc.make
!endif

HS_INTERNAL_NAME=jvm

default:: $(AdditionalTargets) $(JvmtiGeneratedFiles) $(TraceGeneratedFiles)

!include $(HOTSPOTWORKSPACE)/make/jdk_version

!if "$(VERSION_OPT)" != ""
HOTSPOT_BUILD_VERSION = internal-$(VERSION_OPT)
!else
HOTSPOT_BUILD_VERSION = internal
!endif
!if "$(VERSION_STRING)" != ""
JRE_RELEASE_VERSION="\\\"$(VERSION_STRING)\\\""
!else
JRE_RELEASE_VERSION="\\\"$(STANDALONE_JDK_MAJOR_VER).$(STANDALONE_JDK_MINOR_VER).$(STANDALONE_JDK_SECURITY_VER)\\\""
!endif
!if "$(HOTSPOT_RELEASE_VERSION)" != ""
HOTSPOT_RELEASE_VERSION="\\\"$(HOTSPOT_RELEASE_VERSION)\\\""
!else
HOTSPOT_RELEASE_VERSION=$(JRE_RELEASE_VERSION)
!endif
# Define HOTSPOT_VM_DISTRO if HOTSPOT_VM_DISTRO is set,
# and if it is not see if we have the src/closed directory
!if "$(HOTSPOT_VM_DISTRO)" != ""
HOTSPOT_VM_DISTRO=$(HOTSPOT_VM_DISTRO)
!else
!if exists($(HOTSPOTWORKSPACE)\src\closed)
HOTSPOT_VM_DISTRO="\\\"Java HotSpot(TM)\\\""
!else
HOTSPOT_VM_DISTRO="\\\"OpenJDK\\\""
!endif
!if "$(VERSION_BUILD)" == ""
VERSION_BUILD=0
!endif
!endif

VERSION_MAJOR=$(STANDALONE_JDK_MAJOR_VER)
VERSION_MINOR=$(STANDALONE_JDK_MINOR_VER)
VERSION_SECURITY=$(STANDALONE_JDK_SECURITY_VER)
VERSION_PATCH=$(STANDALONE_JDK_PATCH_VER)

ReleaseOptions = -define HOTSPOT_RELEASE_VERSION=$(HOTSPOT_RELEASE_VERSION) -define JRE_RELEASE_VERSION=$(JRE_RELEASE_VERSION) -define HOTSPOT_VM_DISTRO=$(HOTSPOT_VM_DISTRO) -define VERSION_MAJOR=$(VERSION_MAJOR) -define VERSION_MINOR=$(VERSION_MINOR) -define VERSION_SECURITY=$(VERSION_SECURITY) -define VERSION_PATCH=$(VERSION_PATCH) -define VISUAL_STUDIO_BUILD=true
ProjectCreatorIDEOptions = $(ProjectCreatorIDEOptions) $(ReleaseOptions)

$(HOTSPOTBUILDSPACE)/$(ProjectFile): $(HOTSPOTBUILDSPACE)/classes/ProjectCreator.class
	@$(RUN_JAVA) -Djava.class.path="$(HOTSPOTBUILDSPACE)/classes" ProjectCreator WinGammaPlatform$(VcVersion) $(ProjectCreatorIDEOptions)

clean:
	@rm -rf $(HOTSPOTBUILDSPACE)/classes
	@rm -r $(HOTSPOTBUILDSPACE)/$(ProjectFile)

$(HOTSPOTBUILDSPACE)/classes/ProjectCreator.class: $(ProjectCreatorSources)
	@if exist $(HOTSPOTBUILDSPACE)\classes rmdir /s /q $(HOTSPOTBUILDSPACE)\classes
	@mkdir $(HOTSPOTBUILDSPACE)\classes
	@$(COMPILE_JAVAC) -classpath $(HOTSPOTWORKSPACE)\src\share\tools\ProjectCreator -d $(HOTSPOTBUILDSPACE)/classes $(ProjectCreatorSources)

FORCE:
