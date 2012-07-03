#
# Copyright (c) 1995, 2012, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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

# This must be the first rule
default: all

include $(SPEC)
include MakeBase.gmk
include JavaCompilation.gmk
include NativeCompilation.gmk

# Setup the java compilers for the JDK build.
include Setup.gmk

# Setup the build tools.
include Tools.gmk

all: $(BUILD_TOOLS)
#       Import (corba jaxp jaxws langtools hotspot)
	+$(MAKE) -f Import.gmk
#
	+$(MAKE) -f GenerateJavaSources.gmk
#       Ok, now gensrc is fully populated.
	+$(MAKE) -f GenerateData.gmk
	+$(MAKE) -f CompileJavaClasses.gmk
#       The classes are now built and
#       any javah files have now been generated.
	+$(MAKE) -f CompileNativeLibraries.gmk
#       Finally compile the launchers.
	+$(MAKE) -f CompileLaunchers.gmk
#	Generate classes that have other sources. Needs
#       to execute launchers.
	+$(MAKE) -f GenerateClasses.gmk
#       Now we have a complete jdk, which you can run.
#       It is not yet wrapped up as an installed image.
#       The demos are compiled against this jdk.
ifndef NO_DEMOS
	+$(MAKE) -f CompileDemos.gmk
endif
#	Now copy the sample sources into the jdk.
ifndef NO_SAMPLES
	+$(MAKE) -f CopySamples.gmk
endif

# Create the final jdk and jre images, to be wrapped up
# into packages, or installed.
images:
	+$(MAKE) -f CreateJars.gmk
	+$(MAKE) -f Images.gmk 


BINARIES:=$(shell if test -d $(IMAGES_OUTPUTDIR)/j2sdk-image/bin; then cd $(IMAGES_OUTPUTDIR)/j2sdk-image/bin && $(LS) ; fi)
INSTALLDIR:=openjdk-$(RELEASE)

# Install the jdk image, in a very crude way. Not taking into
# account, how to install properly on macosx or windows etc.
install: images
	echo Installing jdk image into $(INSTALL_PREFIX)/jvm/$(INSTALLDIR)
	echo and creating $(words $(BINARIES)) links from $(INSTALL_PREFIX)/bin into the jdk.
	$(MKDIR) -p $(INSTALL_PREFIX)/jvm/$(INSTALLDIR)
	$(RM) -r $(INSTALL_PREFIX)/jvm/$(INSTALLDIR)/*
	$(CP) -rp $(IMAGES_OUTPUTDIR)/j2sdk-image/* $(INSTALL_PREFIX)/jvm/$(INSTALLDIR)
	$(RM) $(addprefix $(INSTALL_PREFIX)/bin/,$(BINARIES))
	$(foreach b,$(BINARIES),$(LN) -s $(INSTALL_PREFIX)/jvm/$(INSTALLDIR)/bin/$b $(INSTALL_PREFIX)/bin/$b &&) true

.PHONY: all install images
