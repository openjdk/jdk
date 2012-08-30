#
# Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building sunec.jar and sunec native library.
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
#     all/clobber/clean		The usual, plus the native libraries.
#				    If OpenJDK, installs sunec.jar.
#				    If JDK, installs prebuilt
#				    sunec.jar.
#
#     jar			Builds/installs sunec.jar
#				    If OpenJDK, does not sign.
#				    If JDK, tries to sign.
#
# Other lesser-used Targets (JDK/OPENJDK):
#
#     build-jar			Builds sunec.jar
#				    (does not sign/install)
#
#     install-jar		Alias for "jar" above.
#
# Other targets (JDK only):
#
#     sign			Alias for sign-jar
#	  sign-jar		Builds/signs sunec.jar (no install)
#
#     release			Builds all targets in preparation
#				for workspace integration.
#
#     install-prebuilt		Installs the pre-built jar files
#
# This makefile was written to support parallel target execution.
#

BUILDDIR = ../../..
PACKAGE = sun.security.ec
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

JAVAC_MAX_WARNINGS=false
JAVAC_LINT_OPTIONS=-Xlint:all,-deprecation
JAVAC_WARNINGS_FATAL=true
include $(BUILDDIR)/common/Defs.gmk

#
# Location for the newly built classfiles.
#
CLASSDESTDIR = $(TEMPDIR)/classes

#
# Java files
#
AUTO_FILES_JAVA_DIRS = $(PKGDIR)

#
# Exclude the sources that get built by ../other/Makefile
#
AUTO_JAVA_PRUNE = \
    ECParameters.java \
    ECPrivateKeyImpl.java \
    ECPublicKeyImpl.java \
    NamedCurve.java

#
# Some licensees do not get the native ECC sources, but we still need to
# be able to build "all" for them.  Check here to see if the sources are
# available.  If not, then skip them.
#

NATIVE_ECC_AVAILABLE := $(shell \
    if [ -d $(SHARE_SRC)/native/$(PKGDIR)/impl ] ; then \
	$(ECHO) true; \
    else \
	$(ECHO) false; \
    fi)

ifeq ($(NATIVE_ECC_AVAILABLE), true)

  LIBRARY = sunec

  #
  # Java files that define native methods
  #
  FILES_export = \
      $(PKGDIR)/ECDHKeyAgreement.java \
      $(PKGDIR)/ECDSASignature.java \
      $(PKGDIR)/ECKeyPairGenerator.java

  JAVAHFLAGS = -bootclasspath \
      "$(CLASSDESTDIR)$(CLASSPATH_SEPARATOR)$(CLASSBINDIR)$(JCE_PATH)"


  #
  # C and C++ files
  #
  include FILES_c.gmk

  FILES_cpp = ECC_JNI.cpp

  CPLUSPLUSLIBRARY=true

  FILES_m = mapfile-vers

  #
  # Find native code
  #
  vpath %.cpp $(SHARE_SRC)/native/$(PKGDIR)

  vpath %.c $(SHARE_SRC)/native/$(PKGDIR)/impl

  #
  # Find include files
  #
  OTHER_INCLUDES += -I$(SHARE_SRC)/native/$(PKGDIR)/impl

  #
  # Compiler flags
  #
  OTHER_CFLAGS += -DMP_API_COMPATIBLE -DNSS_ECC_MORE_THAN_SUITE_B

  #
  # Libraries to link
  #
  ifneq ($(PLATFORM), windows)
    OTHER_LDLIBS = $(LIBCXX)
  endif

  include $(BUILDDIR)/common/Mapfile-vers.gmk

  include $(BUILDDIR)/common/Library.gmk

else # NATIVE_ECC_AVAILABLE

  include $(BUILDDIR)/common/Classes.gmk

endif # NATIVE_ECC_AVAILABLE

#
# We use a variety of subdirectories in the $(TEMPDIR) depending on what
# part of the build we're doing.  Both OPENJDK/JDK builds are initially
# done in the unsigned area.  When files are signed in JDK,
# they will be placed in the appropriate area.
#
UNSIGNED_DIR = $(TEMPDIR)/unsigned

include $(BUILDDIR)/javax/crypto/Defs-jce.gmk

#
# Rules
#

ifdef OPENJDK
all: build-jar install-jar
else
all: build-jar install-prebuilt
	$(build-warning)
endif


# =====================================================
# Build the unsigned sunec.jar file.
#

JAR_DESTFILE = $(EXTDIR)/sunec.jar

#
# Since the -C option to jar is used below, each directory entry must be
# preceded with the appropriate directory to "cd" into.
#
JAR_DIRS = $(patsubst %, -C $(CLASSDESTDIR) %, $(AUTO_FILES_JAVA_DIRS))

build-jar: $(UNSIGNED_DIR)/sunec.jar

#
# Build sunec.jar.
#
$(UNSIGNED_DIR)/sunec.jar: build
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

sign-jar: $(SIGNED_DIR)/sunec.jar

ifndef ALT_JCE_BUILD_DIR
$(SIGNED_DIR)/sunec.jar: $(UNSIGNED_DIR)/sunec.jar
else
#
# We have to remove the build dependency, otherwise, we'll try to rebuild it
# which we can't do on a read-only filesystem.
#
$(SIGNED_DIR)/sunec.jar:
	@if [ ! -r $(UNSIGNED_DIR)/sunec.jar ] ; then \
	    $(ECHO) "Couldn't find $(UNSIGNED_DIR)/sunec.jar"; \
	    exit 1; \
	fi
endif
	$(call sign-file, $(UNSIGNED_DIR)/sunec.jar)


# =====================================================
# Create the Release Engineering files.  Signed builds, etc.
#

release: $(SIGNED_DIR)/sunec.jar
	$(RM) $(JCE_BUILD_DIR)/release/sunec.jar
	$(MKDIR) -p $(JCE_BUILD_DIR)/release
	$(CP) $(SIGNED_DIR)/sunec.jar $(JCE_BUILD_DIR)/release
	$(release-warning)

endif # OPENJDK


# =====================================================
# Install routines.
#

#
# Install sunec.jar, depending on which type is requested.
#
install-jar jar: $(JAR_DESTFILE)
ifndef OPENJDK
	$(release-warning)
endif

ifdef OPENJDK
$(JAR_DESTFILE): $(UNSIGNED_DIR)/sunec.jar
else
$(JAR_DESTFILE): $(SIGNED_DIR)/sunec.jar
endif
	$(install-file)

ifndef OPENJDK
install-prebuilt:
	@$(ECHO) "\n>>>Installing prebuilt SunEC provider..."
	$(RM) $(JAR_DESTFILE)
	$(CP) $(PREBUILT_DIR)/ec/sunec.jar $(JAR_DESTFILE)
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
