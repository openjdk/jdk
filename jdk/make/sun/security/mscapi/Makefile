#
# Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Sun designates this
# particular file as subject to the "Classpath" exception as provided
# by Sun in the LICENSE file that accompanied this code.
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
# Makefile for building sunmscapi.jar and native libraries.
#
# This file was derived from make/com/sun/crypto/provider/Makefile.
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
# This Makefile does the "real" build of the JCE files.  For OpenJDK,
# the jar files built here are installed directly into the OpenJDK.
#
# For JDK, the binaries use pre-built/pre-signed binary files stored in
# the closed workspace that are not shipped in the OpenJDK workspaces.
# We still build the JDK files here to verify the files compile, and in
# preparation for possible signing.  Developers working on JCE in JDK
# must sign the JCE files before testing.  The JCE signing key is kept
# separate from the JDK workspace to prevent its disclosure.
#
# SPECIAL NOTE TO JCE/JDK developers:  The source files must eventually
# be built, signed, and then the resulting jar files MUST BE CHECKED
# INTO THE CLOSED PART OF THE WORKSPACE*.  This separate step *MUST NOT
# BE FORGOTTEN*, otherwise a bug fixed in the source code will not be
# reflected in the shipped binaries.  The "release" target should be
# used to generate the required files.
#
# There are a number of targets to help both JDK/OpenJDK developers.
#
# Main Targets (JDK/OPENJDK):
#
#     all/clobber/clean        The usual, plus the native libraries.
#                                  If OpenJDK, installs sunmscapi.jar.
#                                  If JDK, installs prebuilt
#                                      sunmscapi.jar.
#
#     jar                      Builds/installs sunmscapi.jar
#                                  If OpenJDK, does not sign.
#                                  If JDK, tries to sign.
#
# Other lesser-used Targets (JDK/OPENJDK):
#
#     build-jar                Builds sunmscapi.jar
#                                  (does not sign/install)
#
#     install-jar              Alias for "jar" above.
#
# Other targets (JDK only):
#
#     sign                     Alias for sign-jar
#          sign-jar            Builds/signs sunmscapi.jar (no install)
#
#     release                  Builds all targets in preparation
#                              for workspace integration.
#
#     install-prebuilt         Installs the pre-built jar files
#
# This makefile was written to support parallel target execution.
#

BUILDDIR = ../../..
PACKAGE = sun.security.mscapi
LIBRARY = sunmscapi
PRODUCT = sun

#
# The following is for when we need to do postprocessing
# (signing/obfuscation) against a read-only build.  If the OUTPUTDIR
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

CPLUSPLUSLIBRARY=true

#
# C++ and Java Files
#
include FILES_cpp.gmk

AUTO_FILES_JAVA_DIRS = sun/security/mscapi

#
# Java files that define native methods
#
FILES_export = \
    sun/security/mscapi/KeyStore.java \
    sun/security/mscapi/Key.java \
    sun/security/mscapi/PRNG.java \
    sun/security/mscapi/RSACipher.java \
    sun/security/mscapi/RSAPublicKey.java \
    sun/security/mscapi/RSASignature.java \
    sun/security/mscapi/RSAKeyPairGenerator.java

#
# Find native code
#
vpath %.cpp \
  $(PLATFORM_SRC)/native/sun/security/mscapi

#
# Find include files
#
OTHER_INCLUDES += \
  -I$(PLATFORM_SRC)/native/sun/security/mscapi

#
# Rules
#
CLASSDESTDIR = $(TEMPDIR)/classes

include $(BUILDDIR)/common/Mapfile-vers.gmk

include $(BUILDDIR)/common/Library.gmk

#
# Libraries to link
#
ifeq ($(PLATFORM), windows)
  OTHER_LDLIBS += $(JVMLIB) Crypt32.Lib
endif

#
# We use a variety of subdirectories in the $(TEMPDIR) depending on what
# part of the build we're doing.  Both OPENJDK/JDK builds are initially
# done in the unsigned area.  When files are signed in JDK,
# they will be placed in the appropriate area.
#
UNSIGNED_DIR = $(TEMPDIR)/unsigned

#
# Rules
#

ifdef OPENJDK
all: build-jar install-jar
else
all: build-jar install-prebuilt
	$(build-warning)
endif

include $(BUILDDIR)/javax/crypto/Defs-jce.gmk


# =====================================================
# Build the unsigned sunmscapi.jar file.
#

JAR_DESTFILE = $(EXTDIR)/sunmscapi.jar

#
# The sunmscapi.jar needs to be in the extension class directory,
# therefore none of its classes can appear in $(CLASSBINDIR).
# Currently no one is using any of the MSCAPI internals, so these files
# should not have been built.
#

#
# Since the -C option to jar is used below, each directory entry must be
# preceded with the appropriate directory to "cd" into.
#
JAR_DIRS = $(patsubst %, -C $(CLASSDESTDIR) %, $(AUTO_FILES_JAVA_DIRS))

build-jar: $(UNSIGNED_DIR)/sunmscapi.jar

#
# Build sunmscapi.jar.
#
$(UNSIGNED_DIR)/sunmscapi.jar: build
	$(prep-target)
	$(BOOT_JAR_CMD) cf $@ $(JAR_DIRS) \
	    $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)


ifndef OPENJDK
# =====================================================
# Sign the provider jar file.  Not needed for OpenJDK.
#

SIGNED_DIR = $(JCE_BUILD_DIR)/signed

sign: sign-jar

sign-jar: $(SIGNED_DIR)/sunmscapi.jar

ifndef ALT_JCE_BUILD_DIR
$(SIGNED_DIR)/sunmscapi.jar: $(UNSIGNED_DIR)/sunmscapi.jar
else
#
# We have to remove the build dependency, otherwise, we'll try to rebuild it
# which we can't do on a read-only filesystem.
#
$(SIGNED_DIR)/sunmscapi.jar:
	@if [ ! -r $(UNSIGNED_DIR)/sunmscapi.jar ] ; then \
	    $(ECHO) "Couldn't find $(UNSIGNED_DIR)/sunmscapi.jar"; \
	    exit 1; \
	fi
endif
	$(call sign-file, $(UNSIGNED_DIR)/sunmscapi.jar)


# =====================================================
# Create the Release Engineering files.  Signed builds, etc.
#

release: $(SIGNED_DIR)/sunmscapi.jar
	$(RM) $(JCE_BUILD_DIR)/release/sunmscapi.jar
	$(MKDIR) -p $(JCE_BUILD_DIR)/release
	$(CP) $(SIGNED_DIR)/sunmscapi.jar $(JCE_BUILD_DIR)/release
	$(release-warning)

endif # OPENJDK


# =====================================================
# Install routines.
#

#
# Install sunmscapi.jar, depending on which type is requested.
#
install-jar jar: $(JAR_DESTFILE)
ifndef OPENJDK
	$(release-warning)
endif

ifdef OPENJDK
$(JAR_DESTFILE): $(UNSIGNED_DIR)/sunmscapi.jar
else
$(JAR_DESTFILE): $(SIGNED_DIR)/sunmscapi.jar
endif
	$(install-file)

ifndef OPENJDK
install-prebuilt:
	@$(ECHO) "\n>>>Installing prebuilt SunMSCAPI provider..."
	$(RM) $(JAR_DESTFILE)
	$(CP) $(PREBUILT_DIR)/mscapi/sunmscapi.jar $(JAR_DESTFILE)
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
