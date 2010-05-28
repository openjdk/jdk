#
# Copyright (c) 2007, 2009, Oracle and/or its affiliates. All rights reserved.
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

#
# Makefile for building sunjce_provider.jar.
#
# This file was derived from make/javax/crypto/Makefile.
#

#
# (The terms "OpenJDK" and "JDK" below refer to OpenJDK and Sun JDK builds
# respectively.)
#
# JCE builds are very different between OpenJDK and JDK.  The OpenJDK JCE
# jar files do not require signing, but those for JDK do.  If an unsigned
# jar file is installed into JDK, things will break when the crypto
# routines are called.
#
# This Makefile does the "real" build of the JCE files.  There are some
# javac options currently specific to JCE, so we recompile now to make
# sure any implicit compilations didn't use any incorrect flags.
#
# For OpenJDK, the jar files built here are installed directly into the
# OpenJDK.
#
# For JDK, the binaries use pre-built/pre-signed binary files stored in
# the closed workspace that are not shipped in the OpenJDK workspaces.
# We still build the JDK files here to verify the files compile, and in
# preparation for possible signing.  Developers working on JCE in JDK
# must sign the JCE files before testing.  The JCE signing key is kept
# separate from the JDK workspace to prevent its disclosure.
# 
# SPECIAL NOTE TO JCE/JDK developers:  The source files must eventually
# be built and signed, and the resulting jar files MUST BE CHECKED INTO
# THE CLOSED PART OF THE WORKSPACE*.  This separate step *MUST NOT BE
# FORGOTTEN*, otherwise a bug fixed in the source code will not be
# reflected in the shipped binaries.  The "release" target should be
# used to generate the required files.
#
# There are a number of targets to help both JDK/OpenJDK developers.
#
# Main Targets (JDK/OPENJDK):
#
#     all/clobber/clean        The usual.
#                                  If OpenJDK, installs sunjce_provider.jar.
#                                  If JDK, installs prebuilt
#                                      sunjce_provider.jar.
#
#     jar                      Builds/installs sunjce_provider.jar
#                                  If OpenJDK, does not sign.
#                                  If JDK, tries to sign.
#
# Other lesser-used Targets (JDK/OPENJDK):
#
#     build-jar                Builds sunjce_provider.jar
#                                  (does not sign/install)
#
#     install-jar              Alias for "jar" above.
#
# Other targets (JDK only):
#
#     sign                     Alias for sign-jar
#         sign-jar             Builds/signs sunjce_provider.jar (no install)
#
#     release                  Builds all targets in preparation
#                              for workspace integration.
#
#     install-prebuilt         Installs the pre-built jar files
#
# This makefile was written to support parallel target execution.
#

BUILDDIR = ../../../..
PACKAGE = com.sun.crypto.provider
PRODUCT = sun

#
# The following is for when we need to do postprocessing
# (signing) against a read-only build.  If the OUTPUTDIR
# isn't writable, the build currently crashes out.
#
ifndef OPENJDK
  ifdef ALT_JCE_BUILD_DIR
    # =====================================================
    # Where to place the output, in case we're building from a read-only
    # build area.  (e.g. a release engineering build.)
    JCE_BUILD_DIR=${ALT_JCE_BUILD_DIR}
    IGNORE_WRITABLE_OUTPUTDIR_TEST=true
  else
    JCE_BUILD_DIR=${TEMPDIR}
  endif
endif

include $(BUILDDIR)/common/Defs.gmk

#
# Location for the newly built classfiles.
#
CLASSDESTDIR = $(TEMPDIR)/classes

#
# Subdirectories of these are automatically included.
#
AUTO_FILES_JAVA_DIRS = \
    com/sun/crypto/provider

include $(BUILDDIR)/common/Classes.gmk

#
# Rules
#

#
# Some licensees do not get the security sources, but we still need to
# be able to build "all" for them.  Check here to see if the sources were
# available.  If not, then we don't need to continue this rule.
#

ifdef OPENJDK
all: build-jar install-jar
else  # OPENJDK
ifeq ($(strip $(FILES_java)),)
all: install-prebuilt
	$(no-source-warning)
else  # FILES_java available
all: build-jar install-prebuilt
	$(build-warning)
endif # $(FILES_java) available
endif # OPENJDK

#
# We use a variety of subdirectories in the $(TEMPDIR) depending on what
# part of the build we're doing.  Both OPENJDK/JDK builds are initially
# done in the unsigned area.  When files are signed in JDK, they will be
# placed in the appropriate areas.
#
UNSIGNED_DIR = $(TEMPDIR)/unsigned

include $(BUILDDIR)/javax/crypto/Defs-jce.gmk


# =====================================================
# Build the unsigned sunjce_provider.jar file.
#

JAR_DESTFILE = $(EXTDIR)/sunjce_provider.jar

#
# The sunjce_provider.jar needs to be in the extension class directory,
# therefore none of its classes should appear in $(CLASSBINDIR).
# Currently no one is using any of the SunJCE internals, so these files
# should not have been built.
#

#
# Since the -C option to jar is used below, each directory entry must be
# preceded with the appropriate directory to "cd" into.
#
JAR_DIRS = $(patsubst %, -C $(CLASSDESTDIR) %, $(AUTO_FILES_JAVA_DIRS))

build-jar: $(UNSIGNED_DIR)/sunjce_provider.jar

#
# Build sunjce_provider.jar.
#
$(UNSIGNED_DIR)/sunjce_provider.jar: build $(JCE_MANIFEST_FILE)
	$(prep-target)
	$(BOOT_JAR_CMD) cmf $(JCE_MANIFEST_FILE) $@ $(JAR_DIRS) \
	    $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)


ifndef OPENJDK
# =====================================================
# Sign the provider jar file.  Not needed for OpenJDK.
#

SIGNED_DIR = $(JCE_BUILD_DIR)/signed

sign: sign-jar

sign-jar: $(SIGNED_DIR)/sunjce_provider.jar

ifndef ALT_JCE_BUILD_DIR
$(SIGNED_DIR)/sunjce_provider.jar: $(UNSIGNED_DIR)/sunjce_provider.jar
else
#
# We have to remove the build dependency, otherwise, we'll try to rebuild it
# which we can't do on a read-only filesystem.
#
$(SIGNED_DIR)/sunjce_provider.jar:
	@if [ ! -r $(UNSIGNED_DIR)/sunjce_provider.jar ] ; then \
	    $(ECHO) "Couldn't find $(UNSIGNED_DIR)/sunjce_provider.jar"; \
	    exit 1; \
	fi
endif
	$(call sign-file, $(UNSIGNED_DIR)/sunjce_provider.jar)


# =====================================================
# Create the Release Engineering files.  Signed builds, etc.
#

release: $(SIGNED_DIR)/sunjce_provider.jar
	$(RM) $(JCE_BUILD_DIR)/release/sunjce_provider.jar
	$(MKDIR) -p $(JCE_BUILD_DIR)/release
	$(CP) $(SIGNED_DIR)/sunjce_provider.jar $(JCE_BUILD_DIR)/release
	$(release-warning)

endif # OPENJDK


# =====================================================
# Install routines.
#

#
# Install sunjce_provider.jar, depending on which type is requested.
#
install-jar jar: $(JAR_DESTFILE)
ifndef OPENJDK
	$(release-warning)
endif

ifdef OPENJDK
$(JAR_DESTFILE): $(UNSIGNED_DIR)/sunjce_provider.jar
else
$(JAR_DESTFILE): $(SIGNED_DIR)/sunjce_provider.jar
endif
	$(install-non-module-file)

ifndef OPENJDK
install-prebuilt:
	@$(ECHO) "\n>>>Installing prebuilt SunJCE provider..."
	$(RM) $(JAR_DESTFILE)
	$(CP) $(PREBUILT_DIR)/jce/sunjce_provider.jar $(JAR_DESTFILE)
endif


# =====================================================
# Support routines.
#

clobber clean::
	$(RM) -r $(JAR_DESTFILE) $(TEMPDIR) $(JCE_BUILD_DIR)

.PHONY: build-jar jar install-jar
ifndef OPENJDK
.PHONY: sign sign-jar release install-prebuilt
endif
