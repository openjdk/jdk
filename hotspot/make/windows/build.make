#
# Copyright (c) 1998, 2015, Oracle and/or its affiliates. All rights reserved.
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

# Note: this makefile is invoked both from build.bat and from the J2SE
# control workspace in exactly the same manner; the required
# environment variables (Variant, WorkSpace, BootStrapDir, BuildUser, HOTSPOT_BUILD_VERSION)
# are passed in as command line arguments.

# Note: Running nmake or build.bat from the Windows command shell requires
# that "sh" be accessible on the PATH. An MKS install does this.

# If we haven't set an ARCH yet use x86
# create.bat and build.bat will set it, if used.
!ifndef ARCH
ARCH=x86
!endif


# Must be one of these values (if value comes in from env, can't trust it)
!if "$(ARCH)" != "x86"
!if "$(ARCH)" != "ia64"
ARCH=x86
!endif
!endif

# At this point we should be certain that ARCH has a definition
# now determine the BUILDARCH
#

# the default BUILDARCH
BUILDARCH=i486

# Allow control workspace to force Itanium or AMD64 builds with LP64
ARCH_TEXT=
!ifdef LP64
!if "$(LP64)" == "1"
ARCH_TEXT=64-Bit
!if "$(ARCH)" == "x86"
BUILDARCH=amd64
!else
BUILDARCH=ia64
!endif
!endif
!endif

!if "$(BUILDARCH)" != "ia64"
!ifndef CC_INTERP
!ifndef FORCE_TIERED
FORCE_TIERED=1
!endif
!endif
!endif

!if "$(BUILDARCH)" == "amd64"
Platform_arch=x86
Platform_arch_model=x86_64
!endif
!if "$(BUILDARCH)" == "i486"
Platform_arch=x86
Platform_arch_model=x86_32
!endif

# Supply these from the command line or the environment
#  It doesn't make sense to default this one
Variant=
#  It doesn't make sense to default this one
WorkSpace=

variantDir = windows_$(BUILDARCH)_$(Variant)

realVariant=$(Variant)
VARIANT_TEXT=Core
!if "$(Variant)" == "compiler1"
VARIANT_TEXT=Client
!elseif "$(Variant)" == "compiler2"
!if "$(FORCE_TIERED)" == "1"
VARIANT_TEXT=Server
realVariant=tiered
!else
VARIANT_TEXT=Server
!endif
!elseif "$(Variant)" == "tiered"
VARIANT_TEXT=Tiered
!endif

#########################################################################
# Parameters for VERSIONINFO resource for jvm.dll.
# These can be overridden via the nmake.exe command line.
# They are overridden by RE during the control builds.
#
!include "$(WorkSpace)/make/jdk_version"

# Define HOTSPOT_VM_DISTRO based on settings in make/openjdk_distro
# or make/hotspot_distro.
!ifndef HOTSPOT_VM_DISTRO
!ifndef OPENJDK
!if exists($(WorkSpace)\src\closed)
!include $(WorkSpace)\make\hotspot_distro
!else
!include $(WorkSpace)\make\openjdk_distro
!endif
!else
!include $(WorkSpace)\make\openjdk_distro
!endif
!endif

HS_FILEDESC=$(HOTSPOT_VM_DISTRO) $(ARCH_TEXT) $(VARIANT_TEXT) VM

# JDK ProductVersion:
# 1.5.0_<wx>-b<yz> will have DLL version 5.0.wx*10.yz
# Thus, 1.5.0_10-b04  will be 5.0.100.4
#       1.6.0-b01     will be 6.0.0.1
#       1.6.0_01a-b02 will be 6.0.11.2
#
# STANDALONE_JDK_* variables are defined in make/jdk_version or on command line
#
!if "$(JDK_VER)" == ""
JDK_VER=$(STANDALONE_JDK_MAJOR_VER),$(STANDALONE_JDK_MINOR_VER),$(STANDALONE_JDK_SECURITY_VER),$(STANDALONE_JDK_PATCH_VER)
!endif
!if "$(JDK_DOTVER)" == ""
JDK_DOTVER=$(STANDALONE_JDK_MAJOR_VER).$(STANDALONE_JDK_MINOR_VER).$(STANDALONE_JDK_SECURITY_VER).$(STANDALONE_JDK_PATCH_VER)
!endif
!if "$(VERSION_SHORT)" == ""
VERSION_SHORT=$(STANDALONE_JDK_MAJOR_VER).$(STANDALONE_JDK_MINOR_VER).$(STANDALONE_JDK_SECURITY_VER)
!endif

HS_VER=$(JDK_VER)
HS_DOTVER=$(JDK_DOTVER)

!if "$(HOTSPOT_RELEASE_VERSION)" == ""
HOTSPOT_RELEASE_VERSION=$(VERSION_STRING)
!endif

!if "$(HOTSPOT_VERSION_STRING)" == ""
HOTSPOT_VERSION_STRING=$(HOTSPOT_RELEASE_VERSION)
!endif

# End VERSIONINFO parameters

# if hotspot-only build and/or OPENJDK isn't passed down, need to set OPENJDK
!ifndef OPENJDK
!if !exists($(WorkSpace)\src\closed)
OPENJDK=true
!endif
!endif

#########################################################################

defaultTarget: product

# The product or release build is an optimized build, and is the default

# note that since all the build targets depend on local.make that BUILDARCH
# and Platform_arch and Platform_arch_model will get set in local.make
# and there is no need to pass them thru here on the command line
#
product release optimized: checks $(variantDir) $(variantDir)\local.make sanity
	cd $(variantDir)
	nmake -nologo -f $(WorkSpace)\make\windows\makefiles\top.make BUILD_FLAVOR=product ARCH=$(ARCH)

# The debug build is an optional build
debug: checks $(variantDir) $(variantDir)\local.make sanity
	cd $(variantDir)
	nmake -nologo -f $(WorkSpace)\make\windows\makefiles\top.make BUILD_FLAVOR=debug ARCH=$(ARCH)
fastdebug: checks $(variantDir) $(variantDir)\local.make sanity
	cd $(variantDir)
	nmake -nologo -f $(WorkSpace)\make\windows\makefiles\top.make BUILD_FLAVOR=fastdebug ARCH=$(ARCH)

# target to create just the directory structure
tree: checks $(variantDir) $(variantDir)\local.make sanity
	mkdir $(variantDir)\product
	mkdir $(variantDir)\debug
	mkdir $(variantDir)\fastdebug

sanity:
	@ echo;
	@ cd $(variantDir)
	@ nmake -nologo -f $(WorkSpace)\make\windows\makefiles\sanity.make
	@ cd ..
	@ echo;

clean: checkVariant
	- rm -r -f $(variantDir)

$(variantDir):
	mkdir $(variantDir)

$(variantDir)\local.make: checks
	@ echo # Generated file					>  $@
	@ echo Variant=$(realVariant)				>> $@
	@ echo WorkSpace=$(WorkSpace)				>> $@
	@ echo BootStrapDir=$(BootStrapDir)			>> $@
	@ if "$(USERNAME)" NEQ "" echo BuildUser=$(USERNAME)	>> $@
	@ echo HS_VER=$(HS_VER)					>> $@
	@ echo HS_DOTVER=$(HS_DOTVER)				>> $@
	@ echo HS_COMPANY=$(COMPANY_NAME)			>> $@
	@ echo HS_FILEDESC=$(HS_FILEDESC)			>> $@
	@ echo HOTSPOT_VM_DISTRO=$(HOTSPOT_VM_DISTRO)		>> $@
	@ if "$(OPENJDK)" NEQ "" echo OPENJDK=$(OPENJDK)	>> $@
	@ echo HS_COPYRIGHT=$(HOTSPOT_VM_COPYRIGHT)		>> $@
	@ echo HS_NAME=$(PRODUCT_NAME) $(VERSION_SHORT)		>> $@
	@ echo HOTSPOT_VERSION_STRING=$(HOTSPOT_VERSION_STRING)	>> $@
	@ echo JDK_VER=$(JDK_VER)				>> $@
	@ echo JDK_DOTVER=$(JDK_DOTVER)				>> $@
	@ echo VERSION_STRING=$(VERSION_STRING)			>> $@
	@ echo BUILDARCH=$(BUILDARCH)         			>> $@
	@ echo Platform_arch=$(Platform_arch)        		>> $@
	@ echo Platform_arch_model=$(Platform_arch_model)	>> $@
	@ echo CXX=$(CXX)					>> $@
	@ echo LD=$(LD)						>> $@
	@ echo MT=$(MT)						>> $@
	@ echo RC=$(RC)						>> $@
	@ sh $(WorkSpace)/make/windows/get_msc_ver.sh		>> $@
	@ if "$(ENABLE_FULL_DEBUG_SYMBOLS)" NEQ "" echo ENABLE_FULL_DEBUG_SYMBOLS=$(ENABLE_FULL_DEBUG_SYMBOLS) >> $@
	@ if "$(ZIP_DEBUGINFO_FILES)" NEQ "" echo ZIP_DEBUGINFO_FILES=$(ZIP_DEBUGINFO_FILES) >> $@
	@ if "$(RM)" NEQ "" echo RM=$(RM)                       >> $@
	@ if "$(CP)" NEQ "" echo CP=$(CP)                       >> $@
	@ if "$(MV)" NEQ "" echo MV=$(MV)                       >> $@
	@ if "$(ZIPEXE)" NEQ "" echo ZIPEXE=$(ZIPEXE)           >> $@

checks: checkVariant checkWorkSpace

checkVariant:
	@ if "$(Variant)"=="" echo Need to specify "Variant=[tiered|compiler2|compiler1|core]" && false
	@ if "$(Variant)" NEQ "tiered" if "$(Variant)" NEQ "compiler2" if "$(Variant)" NEQ "compiler1" if "$(Variant)" NEQ "core" \
          echo Need to specify "Variant=[tiered|compiler2|compiler1|core]" && false

checkWorkSpace:
	@ if "$(WorkSpace)"=="" echo Need to specify "WorkSpace=..." && false

checkBuildID:
	@ if "$(BuildID)"=="" echo Need to specify "BuildID=..." && false
