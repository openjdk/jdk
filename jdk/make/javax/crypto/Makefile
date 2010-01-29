#
# Copyright 2007-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for building jce.jar and the various cryptographic strength
# policy jar files.
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
# be built and signed, and the resulting jar files *MUST BE CHECKED INTO
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
#                                  If OpenJDK: builds/installs the
#                                      jce.jar/limited policy files.
#                                  If JDK: builds but does not install.
#                                     During full tops-down builds,
#                                     prebuilt/presigned jce.jar &
#                                     limited policy files are copied
#                                     in by make/java/redist/Makefile.
#                                     If you are working in this directory
#                                     and want to install the prebuilts,
#                                     use the "install-prebuilt" target.
#
#     jar                      Builds/installs jce.jar
#                                  If OpenJDK, does not sign
#                                  If JDK, tries to sign
#
# Other lesser-used Targets (JDK/OPENJDK):
#
#     build-jar                Builds jce.jar (does not sign/install)
#
#     build-policy             Builds policy files (does not sign/install)
#
#     install-jar              Alias for "jar" above
#
#     install-limited          Builds/installs limited policy files
#                                  If OpenJDK, does not sign
#                                  If JDK, tries to sign
#     install-unlimited        Builds/nstalls unlimited policy files
#                                  If OpenJDK, does not sign
#                                  If JDK, tries to sign
#
# Other targets (JDK only):
#
#     sign                     Alias for sign-jar and sign-policy
#          sign-jar            Builds/signs jce.jar file (no install)
#          sign-policy         Builds/signs policy files (no install)
#
#     release                  Builds all targets in preparation
#                              for workspace integration.
#
#     install-prebuilt         Installs the pre-built jar files
#
# This makefile was written to support parallel target execution.
#

BUILDDIR = ../..
MODULE  = base
PACKAGE = javax.crypto
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
    javax/crypto \
    sun/security/internal/interfaces \
    sun/security/internal/spec

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
all: build-jar install-jar build-policy install-limited
else  # OPENJDK
ifeq ($(strip $(FILES_java)),)
all:
	$(no-source-warning)
else  # FILES_java/policy files available
all: build-jar build-policy
	$(build-warning)
endif # $(FILES_java)/policy files available
endif # OPENJDK

#
# We use a variety of subdirectories in the $(TEMPDIR) depending on what
# part of the build we're doing.  Both OPENJDK/JDK builds are initially
# done in the unsigned area.  When files are signed in JDK, they will be
# placed in the appropriate areas.
#
UNSIGNED_DIR = $(TEMPDIR)/unsigned

include Defs-jce.gmk


# =====================================================
# Build the unsigned jce.jar file.  Signing comes later.
#

JAR_DESTFILE = $(LIBDIR)/jce.jar

#
# JCE building is somewhat involved.
#
# OpenJDK:  Since we do not ship prebuilt JCE files, previous compiles
# in the build may have needed JCE class signatures.  There were then
# implicitly built by javac (likely using the boot javac).  While using
# those class files was fine for signatures, we need to rebuild using
# the right compiler.
#
# JDK:  Even through the jce.jar was previously installed, since the
# source files are accessible in the source directories, they will
# always be "newer" than the prebuilt files inside the jar, and thus
# make will always rebuild them.  (We could "hide" the JCE source in a
# separate directory, but that would make the build logic for JDK and
# OpenJDK more complicated.)
#
# Thus in either situation, we shouldn't use these files.
#
# To make sure the classes were built with the right compiler options,
# delete the existing files in $(CLASSBINDIR), rebuild the right way in a
# directory under $(TEMPDIR), then copy the files back to
# $(CLASSBINDIR).   Building in $(TEMPDIR) allows us to use our make
# infrastructure without modification:  .classes.list, macros, etc.
#

#
# The list of directories that will be remade from scratch, using the
# right compilers/options.
#
DELETE_DIRS = $(patsubst %, $(CLASSBINDIR)/%, $(AUTO_FILES_JAVA_DIRS))

#
# Since the -C option to jar is used below, each directory entry must be
# preceded with the appropriate directory to "cd" into.
#
JAR_DIRS = $(patsubst %, -C $(CLASSDESTDIR) %, $(AUTO_FILES_JAVA_DIRS))

build-jar: $(UNSIGNED_DIR)/jce.jar

#
# Build jce.jar, then replace the previously built JCE files in the
# classes directory with these.  This ensures we have consistently built
# files throughout the workspaces.
#
$(UNSIGNED_DIR)/jce.jar: prebuild build $(JCE_MANIFEST_FILE)
	$(prep-target)
	$(BOOT_JAR_CMD) cmf $(JCE_MANIFEST_FILE) $@ $(JAR_DIRS) \
	    $(BOOT_JAR_JFLAGS)
	$(CP) -r $(CLASSDESTDIR)/* $(CLASSBINDIR)
	@$(java-vm-cleanup)

build: prebuild

prebuild:
	$(RM) -r $(DELETE_DIRS)


# =====================================================
# Build the unsigned policy files.
#
# Given the current state of world export/import policies,
# these settings work for Sun's situation.  This note is not
# legal guidance, you must still resolve any export/import issues
# applicable for your situation.  Contact your export/import
# counsel for more information.
#

POLICY_DESTDIR			= $(LIBDIR)/security
UNSIGNED_POLICY_BUILDDIR	= $(UNSIGNED_DIR)/policy

build-policy: unlimited limited

#
# Build the unsigned unlimited policy files.
#
unlimited: \
	    $(UNSIGNED_POLICY_BUILDDIR)/unlimited/US_export_policy.jar	\
	    $(UNSIGNED_POLICY_BUILDDIR)/unlimited/local_policy.jar

$(UNSIGNED_POLICY_BUILDDIR)/unlimited/US_export_policy.jar:		\
	    policy/unlimited/default_US_export.policy			\
	    policy/unlimited/UNLIMITED
	$(prep-target)
	$(BOOT_JAR_CMD) cmf policy/unlimited/UNLIMITED $@		\
	    -C policy/unlimited default_US_export.policy		\
	    $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

$(UNSIGNED_POLICY_BUILDDIR)/unlimited/local_policy.jar:			\
	    policy/unlimited/default_local.policy			\
	    policy/unlimited/UNLIMITED
	$(prep-target)
	$(BOOT_JAR_CMD) cmf policy/unlimited/UNLIMITED $@		\
	    -C policy/unlimited default_local.policy			\
	    $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

#
# Build the unsigned limited policy files.
#
# NOTE:  We currently do not place restrictions on our limited export
# policy.  This was not a typo.
#
limited: \
	    $(UNSIGNED_POLICY_BUILDDIR)/limited/US_export_policy.jar	\
	    $(UNSIGNED_POLICY_BUILDDIR)/limited/local_policy.jar

$(UNSIGNED_POLICY_BUILDDIR)/limited/US_export_policy.jar:		\
	    $(UNSIGNED_POLICY_BUILDDIR)/unlimited/US_export_policy.jar
	$(install-non-module-file)

$(UNSIGNED_POLICY_BUILDDIR)/limited/local_policy.jar:			\
	    policy/limited/default_local.policy				\
	    policy/limited/exempt_local.policy				\
	    policy/limited/LIMITED
	$(prep-target)
	$(BOOT_JAR_CMD) cmf policy/limited/LIMITED $@			\
	    -C policy/limited default_local.policy			\
	    -C policy/limited exempt_local.policy			\
	    $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

UNSIGNED_POLICY_FILES = \
    $(UNSIGNED_POLICY_BUILDDIR)/unlimited/US_export_policy.jar		\
    $(UNSIGNED_POLICY_BUILDDIR)/unlimited/local_policy.jar		\
    $(UNSIGNED_POLICY_BUILDDIR)/limited/US_export_policy.jar		\
    $(UNSIGNED_POLICY_BUILDDIR)/limited/local_policy.jar		\


ifndef OPENJDK
# =====================================================
# Sign the various jar files.  Not needed for OpenJDK.
#

SIGNED_DIR		= $(JCE_BUILD_DIR)/signed
SIGNED_POLICY_BUILDDIR	= $(SIGNED_DIR)/policy

SIGNED_POLICY_FILES = \
    $(patsubst $(UNSIGNED_POLICY_BUILDDIR)/%,$(SIGNED_POLICY_BUILDDIR)/%, \
	$(UNSIGNED_POLICY_FILES))

sign: sign-jar sign-policy

sign-jar: $(SIGNED_DIR)/jce.jar

sign-policy: $(SIGNED_POLICY_FILES)

ifndef ALT_JCE_BUILD_DIR
$(SIGNED_DIR)/jce.jar: $(UNSIGNED_DIR)/jce.jar
else
#
# We have to remove the build dependency, otherwise, we'll try to rebuild it
# which we can't do on a read-only filesystem.
#
$(SIGNED_DIR)/jce.jar:
	@if [ ! -r $(UNSIGNED_DIR)/jce.jar ] ; then \
	    $(ECHO) "Couldn't find $(UNSIGNED_DIR)/jce.jar"; \
	    exit 1; \
	fi
endif
	$(call sign-file, $(UNSIGNED_DIR)/jce.jar)

$(SIGNED_POLICY_BUILDDIR)/unlimited/US_export_policy.jar:	\
	    $(UNSIGNED_POLICY_BUILDDIR)/unlimited/US_export_policy.jar
	$(call sign-file, $<)

$(SIGNED_POLICY_BUILDDIR)/unlimited/local_policy.jar:		\
	    $(UNSIGNED_POLICY_BUILDDIR)/unlimited/local_policy.jar
	$(call sign-file, $<)

$(SIGNED_POLICY_BUILDDIR)/limited/US_export_policy.jar:		\
	    $(UNSIGNED_POLICY_BUILDDIR)/limited/US_export_policy.jar
	$(call sign-file, $<)

$(SIGNED_POLICY_BUILDDIR)/limited/local_policy.jar:		\
	    $(UNSIGNED_POLICY_BUILDDIR)/limited/local_policy.jar
	$(call sign-file, $<)


# =====================================================
# Create the Release Engineering files.  Signed builds,
# unlimited policy file distribution, etc.
#

CLOSED_DIR = $(BUILDDIR)/closed/javax/crypto

release: $(SIGNED_DIR)/jce.jar sign-policy $(CLOSED_DIR)/doc/COPYRIGHT.html \
         $(CLOSED_DIR)/doc/README.txt
	$(RM) -r \
	    $(JCE_BUILD_DIR)/release/UnlimitedJCEPolicy              \
	    $(JCE_BUILD_DIR)/release/jce.jar                         \
	    $(JCE_BUILD_DIR)/release/US_export_policy.jar            \
	    $(JCE_BUILD_DIR)/release/local_policy.jar                \
	    $(JCE_BUILD_DIR)/release/UnlimitedJCEPolicy.zip
	$(MKDIR) -p $(JCE_BUILD_DIR)/release/UnlimitedJCEPolicy
	$(CP) $(SIGNED_DIR)/jce.jar $(JCE_BUILD_DIR)/release
	$(CP) \
	    $(SIGNED_POLICY_BUILDDIR)/limited/US_export_policy.jar   \
	    $(SIGNED_POLICY_BUILDDIR)/limited/local_policy.jar       \
	    $(JCE_BUILD_DIR)/release
	$(CP) \
	    $(SIGNED_POLICY_BUILDDIR)/unlimited/US_export_policy.jar \
	    $(SIGNED_POLICY_BUILDDIR)/unlimited/local_policy.jar     \
	    $(CLOSED_DIR)/doc/COPYRIGHT.html                         \
	    $(CLOSED_DIR)/doc/README.txt                             \
	    $(JCE_BUILD_DIR)/release/UnlimitedJCEPolicy
	cd $(JCE_BUILD_DIR)/release ; \
	$(ZIPEXE) -qr UnlimitedJCEPolicy.zip UnlimitedJCEPolicy
	$(release-warning)

endif # OPENJDK


# =====================================================
# Install routines.
#

#
# Install jce.jar, depending on which type is requested.
#
install-jar jar: $(JAR_DESTFILE)
ifndef OPENJDK
	$(release-warning)
endif

ifdef OPENJDK
$(JAR_DESTFILE): $(UNSIGNED_DIR)/jce.jar
else
$(JAR_DESTFILE): $(SIGNED_DIR)/jce.jar
endif
	$(install-non-module-file)

#
# Install the appropriate policy file, depending on the type of build.
#
ifdef OPENJDK
INSTALL_POLICYDIR = $(UNSIGNED_POLICY_BUILDDIR)
else
INSTALL_POLICYDIR = $(SIGNED_POLICY_BUILDDIR)
endif

install-limited-jars: \
	    $(INSTALL_POLICYDIR)/limited/US_export_policy.jar	\
	    $(INSTALL_POLICYDIR)/limited/local_policy.jar
	$(MKDIR) -p $(POLICY_DESTDIR)
	$(RM) \
	    $(POLICY_DESTDIR)/US_export_policy.jar		\
	    $(POLICY_DESTDIR)/local_policy.jar
	$(CP) $^ $(POLICY_DESTDIR)

install-limited: install-limited-jars install-module-files
ifndef OPENJDK
	$(release-warning)
endif

install-unlimited-jars: \
	    $(INSTALL_POLICYDIR)/unlimited/US_export_policy.jar	\
	    $(INSTALL_POLICYDIR)/unlimited/local_policy.jar 
	$(MKDIR) -p $(POLICY_DESTDIR)
	$(RM) \
	    $(POLICY_DESTDIR)/US_export_policy.jar		\
	    $(POLICY_DESTDIR)/local_policy.jar
	$(CP) $^ $(POLICY_DESTDIR)

install-unlimited: install-unlimited-jars install-module-files
ifndef OPENJDK
	$(release-warning)
endif

ifndef OPENJDK
install-prebuilt-jars:
	@$(ECHO) "\n>>>Installing prebuilt JCE framework..."
	$(RM) $(JAR_DESTFILE) \
	    $(POLICY_DESTDIR)/US_export_policy.jar \
	    $(POLICY_DESTDIR)/local_policy.jar
	$(CP) $(PREBUILT_DIR)/jce/jce.jar $(JAR_DESTFILE)
	$(CP) \
	    $(PREBUILT_DIR)/jce/US_export_policy.jar \
	    $(PREBUILT_DIR)/jce/local_policy.jar \
	    $(POLICY_DESTDIR)

install-prebuilt: install-prebuilt-jars install-module-files
endif

install-module-files: \
	   $(POLICY_DESTDIR)/US_export_policy.jar \
	   $(POLICY_DESTDIR)/local_policy.jar

$(POLICY_DESTDIR)/%.jar :
	$(install-module-file)

# =====================================================
# Support routines.
#

clobber clean::
	$(RM) -r $(JAR_DESTFILE) $(POLICY_DESTDIR)/US_export_policy.jar \
	    $(POLICY_DESTDIR)/local_policy.jar $(DELETE_DIRS) $(TEMPDIR) \
	    $(JCE_BUILD_DIR)

.PHONY: build-jar jar build-policy unlimited limited install-jar \
	install-limited install-unlimited
ifndef OPENJDK
.PHONY: sign sign-jar sign-policy release install-prebuilt
endif
