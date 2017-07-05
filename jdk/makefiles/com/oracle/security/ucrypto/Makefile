#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building ucrypto.jar and its native libraries.
#
# This file was modified from make/sun/security/pkcs11/Makefile.
#
#
# (The terms "OpenJDK" and "JDK" below refer to OpenJDK and Oracle
# JDK builds respectively.)
#
# This Makefile does the "real" build of the Ucrypto provider files.
# Since the sources are unavailable for OpenJDK, this Makefile is only
# useful for JDK.
#
#
# Main Targets (JDK on Solaris):
#
#     all                      The usual, ucrypto.jar plus the native libraries.
#                                  builds and installs the prebuilt/signed jar.
#
#     clobber/clean            Cleans up the temp directory, ucrypto.jar, the
#                              native libraries, and the config file from the
#                              build area
#
#     jar                      Builds, signs and installs ucrypto.jar
#                              (Can only be done on machines with access to
#                               the signing keystore)
#
# Other lesser-used Targets (JDK on Solaris):
#
#     build-jar                Builds ucrypto.jar (no sign/install)
#
#     sign                     Builds/signs ucrypto.jar (no install)
#
#     release                  Builds all targets in preparation
#                              for workspace integration.
#                              (Can only be done on machines with access to
#                               the signing keystore)
#
#     install-prebuilt         Installs the pre-built jar files
#
# NOTE: None of the above target will update the prebuilt provider binary
# under the closed workspace. To update it, you must explicitly copy the
# binary from either the tmp/signed or lib/ext directory.
#
# This makefile was written to support parallel target execution.
#

BUILDDIR = ../../../..

include $(BUILDDIR)/common/Defs.gmk

ifndef OPENJDK
  ifneq ($(PLATFORM), solaris)
    all:
  else
    PACKAGE = com.oracle.security.ucrypto
  LIBRARY = j2ucrypto
  PRODUCT = oracle

  #
  # The following is for when we need to do postprocessing
  # (signing/obfuscation) against a read-only build.  If the OUTPUTDIR
  # isn't writable, the build currently crashes out.
  #
  ifdef ALT_JCE_BUILD_DIR
    # =====================================================
    # Where to place the output, in case we're building from a read-only
    # build area.  (e.g. a release engineering build.)
    JCE_BUILD_DIR=${ALT_JCE_BUILD_DIR}
    IGNORE_WRITABLE_OUTPUTDIR_TEST=true
  else
    JCE_BUILD_DIR=${TEMPDIR}
  endif

  JAVAC_MAX_WARNINGS=false
  JAVAC_LINT_OPTIONS=-Xlint:all,-deprecation
  JAVAC_WARNINGS_FATAL=true

  #
  # C and Java Files
  #
  include FILES_c.gmk

  #
  # Subdirectories of these are automatically included.
  #
  AUTO_FILES_JAVA_DIRS = com/oracle/security/ucrypto

  #
  # Java files that define native methods
  #
  FILES_export = \
      com/oracle/security/ucrypto/UcryptoProvider.java \
      com/oracle/security/ucrypto/NativeCipher.java \
      com/oracle/security/ucrypto/NativeDigest.java \
      com/oracle/security/ucrypto/NativeKey.java \
      com/oracle/security/ucrypto/NativeRSASignature.java \
      com/oracle/security/ucrypto/NativeRSACipher.java

  #
  # Find native code
  #
  vpath %.c \
    $(CLOSED_PLATFORM_SRC)/native/com/oracle/security/ucrypto

  #
  # Find include files
  #
  OTHER_INCLUDES += \
    -I$(CLOSED_PLATFORM_SRC)/native/com/oracle/security/ucrypto

  #
  # Rules
  #
  CLASSDESTDIR = $(TEMPDIR)/classes
  JAVAHFLAGS = -bootclasspath \
    "$(CLASSDESTDIR)$(CLASSPATH_SEPARATOR)$(CLASSBINDIR)"

  include $(BUILDDIR)/common/Mapfile-vers.gmk
  include $(BUILDDIR)/common/Library.gmk

  #
  # Libraries to link
  #
  OTHER_LDLIBS = -ldl

  # Default config file
  UCRYPTO_CFG_SRC   = $(CLOSED_SRC)/share/lib/security/ucrypto-solaris.cfg
  UCRYPTO_CFG_BUILD = $(LIBDIR)/security/ucrypto-solaris.cfg

  #
  # We use a variety of subdirectories in the $(TEMPDIR) depending on what
  # part of the build we're doing.  Build is initially done in the unsigned
  # area and when files are signed, they will be placed in the appropriate area.
  #
  UNSIGNED_DIR = $(TEMPDIR)/unsigned

  #
  # Rules
  #
  all: ucrypto-cfg build-jar install-prebuilt
	$(build-warning)

  ucrypto-cfg: $(UCRYPTO_CFG_BUILD)

  $(UCRYPTO_CFG_BUILD): $(UCRYPTO_CFG_SRC)
	$(install-file)

  include $(BUILDDIR)/javax/crypto/Defs-jce.gmk


  # =====================================================
  # Build the unsigned ucrypto.jar file.
  #

  JAR_DESTFILE = $(EXTDIR)/ucrypto.jar

  #
  # The ucrypto.jar needs to be in the extension class directory,
  # therefore none of its classes can appear in $(CLASSBINDIR).
  # Currently no one is using any of the internals, so these files
  # should not have been built.
  #

  #
  # Since the -C option to jar is used below, each directory entry must be
  # preceded with the appropriate directory to "cd" into.
  #
  JAR_DIRS = $(patsubst %, -C $(CLASSDESTDIR) %, $(AUTO_FILES_JAVA_DIRS))

  build-jar: $(UNSIGNED_DIR)/ucrypto.jar

  #
  # Build ucrypto.jar.
  #
  $(UNSIGNED_DIR)/ucrypto.jar: build
	$(prep-target)
	$(BOOT_JAR_CMD) cf $@ $(JAR_DIRS) \
	    $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

  #
  # Sign ucrypto.jar
  #
  SIGNED_DIR = $(JCE_BUILD_DIR)/signed

  sign: $(SIGNED_DIR)/ucrypto.jar

  ifndef ALT_JCE_BUILD_DIR
    $(SIGNED_DIR)/ucrypto.jar: $(UNSIGNED_DIR)/ucrypto.jar
  else
    #
    # We have to remove the build dependency, otherwise, we'll try to rebuild it
    # which we can't do on a read-only filesystem.
    #
    $(SIGNED_DIR)/ucrypto.jar:
	@if [ ! -r $(UNSIGNED_DIR)/ucrypto.jar ] ; then \
            $(ECHO) "Couldn't find $(UNSIGNED_DIR)/ucrypto.jar"; \
            exit 1; \
        fi
  endif
	$(call sign-file, $(UNSIGNED_DIR)/ucrypto.jar)


  # =====================================================
  # Create the Release Engineering files.  Signed builds, etc.
  #

  release: $(SIGNED_DIR)/ucrypto.jar
	$(RM) $(JCE_BUILD_DIR)/release/ucrypto.jar
	$(MKDIR) -p $(JCE_BUILD_DIR)/release
	$(CP) $(SIGNED_DIR)/ucrypto.jar $(JCE_BUILD_DIR)/release
	$(release-warning)


  # =====================================================
  # Install routines.
  #

  #
  # Install ucrypto.jar, depending on which type is requested.
  #
  jar: $(JAR_DESTFILE)
	$(release-warning)

  $(JAR_DESTFILE): $(SIGNED_DIR)/ucrypto.jar
	$(install-file)

  install-prebuilt:
	@$(ECHO) "\n>>>Installing prebuilt OracleUcrypto provider..."
	$(RM) $(JAR_DESTFILE)
	$(CP) $(PREBUILT_DIR)/ucrypto/ucrypto.jar $(JAR_DESTFILE)


  # =====================================================
  # Support routines.
  #
  clobber clean::
	$(RM) -r $(JAR_DESTFILE) $(TEMPDIR) $(JCE_BUILD_DIR)
	$(RM) -r $(UCRYPTO_CFG_BUILD)

  .PHONY: build-jar jar sign release install-prebuilt

  endif #ifneq ($(PLATFORM), solaris)
endif #ifndef OPENJDK
