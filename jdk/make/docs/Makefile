# Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
# Definitions for $(DOCSDIR), $(MKDIR), $(BINDIR), etc.
#
BUILDDIR=..
PRODUCT=docs
include $(BUILDDIR)/common/Defs.gmk

# Get CopyrightLine macro and other shared variables
include $(BUILDDIR)/common/shared/Defs-javadoc.gmk

# Url to root of documents
DOCSDIR_URL = {@docroot}/$(GET2DOCSDIR)

# Url to copyright html file
COPYRIGHT_URL-7 = $(DOCSDIR_URL)/legal/cpyr.html
COPYRIGHT_URL = $(COPYRIGHT_URL-$(JDK_MINOR_VERSION))

# Url to bug filing site
BUG_SUBMIT_URL = http://bugreport.sun.com/bugreport/

# Common line for how to submit a bug or rfe
BUG_SUBMIT_LINE = <a href="$(BUG_SUBMIT_URL)">Submit a bug or feature</a>

# Url to devdocs page
#   Was: http://java.sun.com/javase/6/webnotes/devdocs-vs-specs.html
DEV_DOCS_URL-5 = http://java.sun.com/j2se/1.5.0/docs/index.html
DEV_DOCS_URL-6 = http://download.oracle.com/javase/6/docs/index.html
DEV_DOCS_URL-7 = http://download.oracle.com/javase/7/docs/index.html
DEV_DOCS_URL-8 = http://download.oracle.com/javase/8/docs/index.html
DEV_DOCS_URL = $(DEV_DOCS_URL-$(JDK_MINOR_VERSION))
DOCS_BASE_URL = http://download.oracle.com/javase/7/docs

# Url to Java Language Spec
#JLS3_URL = http://java.sun.com/docs/books/jls/

# Common Java trademark line
JAVA_TRADEMARK_LINE = Java is a trademark or registered trademark of \
$(FULL_COMPANY_NAME) in the US and other countries.

#
# Definitions for imported components
#
include $(BUILDDIR)/common/internal/ImportComponents.gmk

# We override whatever the max VM memory setting is here.
#    NOTE: javadoc will not complete without these larger settings.
#    WARNING: This could cause thrashing on low memory machines.
ifeq ($(ARCH_DATA_MODEL),64)
  MAX_VM_MEMORY = 1024
else ifeq ($(ARCH),universal)
  MAX_VM_MEMORY = 1024
else
  MAX_VM_MEMORY = 768
endif

# List of all possible directories for javadoc to look for sources
#    NOTE: Quotes are required around sourcepath argument only on Windows.
#          Otherwise, you get "No packages or classes specified." due 
#          to $(CLASSPATH_SEPARATOR) being interpreted as an end of 
#          command (newline or shell ; character)
ALL_SOURCE_DIRS = $(SHARE_SRC)/classes \
                  $(IMPORTSRCDIR) \
                  $(GENSRCDIR) \
                  $(SHARE_SRC)/../solaris/classes \
	          $(SHARE_SRC)/../windows/classes \
		  $(SHARE_SRC)/doc/stub

# List of directories that actually exist
ALL_EXISTING_SOURCE_DIRS := $(wildcard $(ALL_SOURCE_DIRS))

# List with classpath separator between them
EMPTY:=
SPACE:= $(EMPTY) $(EMPTY)
RELEASEDOCS_SOURCEPATH = \
    $(subst $(SPACE),$(CLASSPATH_SEPARATOR),$(strip $(ALL_SOURCE_DIRS)))

# Prep for javadoc creation, assumes $@ is an index.html file
define prep-javadoc
@if [ -f "$@" -a "$?" != "" ] ; then \
  $(ECHO) "# Dependencies have changed: $?"; \
fi
$(RM) -r $(@D)
$(MKDIR) -p $(@D)
endef

# A cache of the directories in ALL_SOURCE_DIRS
DIRECTORY_CACHE = $(DOCSTMPDIR)/directory.cache

# Given a list of packages, return a list of files or dirs to be dependent on
#   (Currently only returning a list of directories)
define PackageDependencies # packages
$(shell							\
  if [ "$1" != "" -a -f $(DIRECTORY_CACHE) ] ; then	\
    for p in $1 ; do 					\
      pd=`$(ECHO) $${p} | $(SED) -e 's@[.]@/@g'`;	\
      $(CAT) $(DIRECTORY_CACHE) | $(GREP) "/$${pd}/" ; 	\
    done; 						\
  fi							\
)
endef

# Given a list of packages, add packages that exist to $@, print summary
define PackageFilter # packages
@if [ "$1" != "" ] ; then 				\
  for p in $1 ; do 					\
    pd=`$(ECHO) $${p} | $(SED) -e 's@[.]@/@g'`; 	\
    found="false"; 					\
    for cp in $(ALL_SOURCE_DIRS) ; do 			\
      if [ -d $${cp}/$${pd} ] ; then 			\
        $(ECHO) "$${p}" >> $@; 				\
	found="true";					\
	break; 						\
      fi; 						\
    done; 						\
    if [ "$${found}" = "false" ] ; then 		\
      $(ECHO) "WARNING: Package not found: $${p}"; 	\
    fi; 						\
  done; 						\
fi
endef

# Print out a summary of the javadoc command about to be run
define JavadocSummary # optionsfile packagesfile
@$(ECHO) "# Summary for $@";\
 $(ECHO) "# Options (`$(BASENAME) $1`):"; $(SED) -e 's@^@#   @' $1; \
 $(ECHO) "# Packages (`$(BASENAME) $2`):";$(SED) -e 's@^@#   @' $2
endef

#
# Temporary directory for javadoc creation
#
DOCSTMPDIR = $(TEMPDIR)/doctmp

#
# Different api directories created from root directory
#
COREAPI_DOCSDIR   = $(DOCSDIR)/api
JDK_API_DOCSDIR   = $(DOCSDIR)/jdk/api
JRE_API_DOCSDIR   = $(DOCSDIR)/jre/api
PLATFORM_DOCSDIR  = $(DOCSDIR)/platform

# The non-core api javadocs need to be able to access the root of the core
#   api directory, so for jdk/api or jre/api to get to the core api/
#   directory we would use this:
JDKJRE2COREAPI    = ../../api

# Common bottom argument
define CommonBottom # year
<font size="-1"><br> $(call CopyrightLine,,$1,)</font>
endef

# Common trademark bottom argument (Not sure why this is used sometimes)
define CommonTrademarkBottom # year
<font size="-1">\
$(BUG_SUBMIT_LINE)<br>$(JAVA_TRADEMARK_LINE)<br>\
$(call CopyrightLine,,$1,$(COMPANY_ADDRESS))\
</font>
endef

# Common echo of option
define OptionOnly # opt
$(PRINTF) "%s\n" "$1"
endef
define OptionPair # opt arg
$(PRINTF) "%s '%s'\n" "$1" '$2'
endef
define OptionTrip # opt arg arg
$(PRINTF) "%s '%s' '%s'\n" "$1" '$2' '$3'
endef

# Core api bottom argument (with special sauce)
COREAPI_BOTTOM = <font size="-1"> $(BUG_SUBMIT_LINE)\
<br>For further API reference and developer documentation, \
see <a href="$(DEV_DOCS_URL)" target="_blank">Java SE Documentation</a>. \
That documentation contains more detailed, developer-targeted descriptions, \
with conceptual overviews, definitions of terms, workarounds, \
and working code examples.<br>\
$(call CopyrightLine,$(COPYRIGHT_URL),$(FIRST_COPYRIGHT_YEAR),)\
</font>

# Common javadoc options used by all
COMMON_JAVADOCFLAGS =					\
                $(NO_PROPRIETARY_API_WARNINGS)          \
		-quiet					\
                -use					\
                -keywords				\
		-Xdoclint:none				\
                -Xprofilespath $(JDK_TOPDIR)/makefiles/profile-rtjar-includes.txt \
		$(ADDITIONAL_JAVADOCFLAGS)

ifdef OPENJDK
  ADDITIONAL_JAVADOCFLAGS =				\
                -Xdocrootparent $(DOCS_BASE_URL)
else
  ADDITIONAL_JAVADOCFLAGS =
endif

# Draft used for non-fcs documents
JDK_IS_FCS = false
DRAFT_HEADER =
ifeq ($(JDK_MINOR_VERSION),5)
  JDK_IS_FCS = true
endif
ifeq ($(JDK_MINOR_VERSION),6)
  JDK_IS_FCS = true
endif
ifeq ($(JDK_MINOR_VERSION),7)
  JDK_IS_FCS = true
endif
ifeq ($(JDK_IS_FCS),false)
  ifneq ($(MILESTONE), fcs)
    DRAFT_HEADER = <br><strong>DRAFT&nbsp;$(MILESTONE)-$(BUILD_NUMBER)</strong>
    DRAFT_BOTTOM = <br><strong>DRAFT&nbsp;$(MILESTONE)-$(BUILD_NUMBER)</strong>
    DRAFT_WINTITLE = $(BUILD_NUMBER)
    # Early access top text (not used in FCS releases)
    COREAPI_TOP_EARLYACCESS = \
<div style="background-color: \#EEEEEE"> \
<div style="padding: 6px; margin-top: 2px; margin-bottom: 6px; \
margin-left: 6px; margin-right: 6px; text-align: justify; \
font-size: 80%; font-family: Helvetica, Arial, sans-serif; \
font-weight: normal;"> \
Please note that the specifications and other information \
contained herein are not final and are subject to change. \
The information is being made available to you solely for purpose of \
evaluation. \
</div> </div>
  endif
endif

#################################################################

#
# CORE_PKGS environment variable has been moved to the following file
#
include CORE_PKGS.gmk

#
# Load environment variables for API package names that are not part of
# the Java SE platform
#
include NON_CORE_PKGS.gmk

#################################################################

#
# Default target is same as docs target, create core api and all others it can
#

all: docs
docs: coredocs otherdocs

#################################################################
# Production Targets -- USE THESE TARGETS WHEN:
#    a) You're generating docs outside of release engineering's
#       standard control build.
#    b) The docs will be pushed to the web and/or included in
#       the downloaded doc bundle.
#
# See:  Notes.html#releaseTargets
# Note: Spaces precede ifdef/ifndef indents. Tabs precede target commands (!)
#

sanitycheckcoredocs:
	@$(ECHO) ""
	@$(ECHO) "Building core api docs with these values:"
	@$(ECHO) "    BUILD_NUMBER = $(BUILD_NUMBER)"
	@$(ECHO) "    MILESTONE = $(MILESTONE)"
	@$(ECHO) ""
  ifeq ($(BUILD_NUMBER), b00)
	@$(ECHO) "ERROR: Build number must be defined"
	@$(ECHO) "MILESTONE is set to $(MILESTONE)"
	@$(ECHO) ""
	exit 1
  endif

#############################################################
#
# coredocs
#
COREAPI_DOCTITLE  = Java$(TRADEMARK) Platform, Standard Edition \
$(JDK_MINOR_VERSION)<br>API Specification
COREAPI_WINDOWTITLE  = Java Platform SE $(JDK_MINOR_VERSION)
COREAPI_HEADER = \
<strong>Java$(TRADEMARK)&nbsp;Platform<br>Standard&nbsp;Ed.&nbsp;$(JDK_MINOR_VERSION)</strong>

# Java language specification cite
TAG_JLS = jls:a:See <cite> \
The Java&trade; Language Specification</cite>:

# Overview file for core apis
COREAPI_OVERVIEW = $(SHARE_SRC)/classes/overview-core.html

# The index.html, options, and packages files
COREAPI_INDEX_FILE    = $(COREAPI_DOCSDIR)/index.html
COREAPI_OPTIONS_FILE  = $(DOCSTMPDIR)/coredocs.options
COREAPI_PACKAGES_FILE = $(DOCSTMPDIR)/coredocs.packages

coredocs: $(COREAPI_INDEX_FILE)

# Set relative location to core api document root
$(COREAPI_INDEX_FILE): GET2DOCSDIR=..

# Run javadoc if the index file is out of date or missing
$(COREAPI_INDEX_FILE): $(COREAPI_OPTIONS_FILE) $(COREAPI_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(COREAPI_OPTIONS_FILE),$(COREAPI_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(COREAPI_OPTIONS_FILE) @$(COREAPI_PACKAGES_FILE)

# Create file with javadoc options in it
$(COREAPI_OPTIONS_FILE): $(COREAPI_OVERVIEW)
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ISO-8859-1)			; \
	  $(call OptionPair,-tag,beaninfo:X)				; \
	  $(call OptionPair,-tag,revised:X)				; \
	  $(call OptionPair,-tag,since.unbundled:X)			; \
	  $(call OptionPair,-tag,spec:X)				; \
	  $(call OptionPair,-tag,specdefault:X)				; \
	  $(call OptionPair,-tag,Note:X)				; \
	  $(call OptionPair,-tag,ToDo:X)				; \
          $(call OptionPair,-tag,$(TAG_JLS)) 				; \
	  $(call OptionOnly,-splitIndex)				; \
	  $(call OptionPair,-overview,$(COREAPI_OVERVIEW))		; \
	  $(call OptionPair,-doctitle,$(COREAPI_DOCTITLE))		; \
	  $(call OptionPair,-windowtitle,$(COREAPI_WINDOWTITLE) $(DRAFT_WINTITLE)) ;\
	  $(call OptionPair,-header,$(COREAPI_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(COREAPI_BOTTOM)$(DRAFT_BOTTOM))	; \
        ) >> $@
ifdef COREAPI_TOP_EARLYACCESS
	@$(call OptionPair,-top,$(COREAPI_TOP_EARLYACCESS)) >> $@
endif

# Create a file with the package names in it
$(COREAPI_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(CORE_PKGS))
	$(prep-target)
	$(call PackageFilter,$(CORE_PKGS))

#############################################################
#
# docletapidocs
#

# Part of langtools
ifdef LANGTOOLS_DIST
  ALL_OTHER_TARGETS += docletapidocs
endif

DOCLETAPI_DOCDIR       := $(JDK_API_DOCSDIR)/javadoc/doclet
DOCLETAPI2COREAPI      := ../../$(JDKJRE2COREAPI)
DOCLETAPI_DOCTITLE     := Doclet API
DOCLETAPI_WINDOWTITLE  := Doclet API
DOCLETAPI_HEADER       := <strong>Doclet API</strong>
DOCLETAPI_BOTTOM       := $(call CommonTrademarkBottom,$(DOCLETAPI_FIRST_COPYRIGHT_YEAR))
DOCLETAPI_GROUPNAME    := Packages
DOCLETAPI_REGEXP       := com.sun.javadoc
# DOCLETAPI_PKGS is located in NON_CORE_PKGS.gmk

# The index.html, options, and packages files
DOCLETAPI_INDEX_FILE    = $(DOCLETAPI_DOCDIR)/index.html
DOCLETAPI_OPTIONS_FILE  = $(DOCSTMPDIR)/docletapi.options
DOCLETAPI_PACKAGES_FILE = $(DOCSTMPDIR)/docletapi.packages

docletapidocs: $(DOCLETAPI_INDEX_FILE)

# Set relative location to core api document root
$(DOCLETAPI_INDEX_FILE): GET2DOCSDIR=$(DOCLETAPI2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(DOCLETAPI_INDEX_FILE): $(DOCLETAPI_OPTIONS_FILE) $(DOCLETAPI_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(DOCLETAPI_OPTIONS_FILE),$(DOCLETAPI_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(DOCLETAPI_OPTIONS_FILE) @$(DOCLETAPI_PACKAGES_FILE)

# Create file with javadoc options in it
$(DOCLETAPI_OPTIONS_FILE):
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-breakiterator)				; \
	  $(call OptionPair,-doctitle,$(DOCLETAPI_DOCTITLE))		; \
	  $(call OptionPair,-windowtitle,$(DOCLETAPI_WINDOWTITLE) $(DRAFT_WINTITLE));\
	  $(call OptionPair,-header,$(DOCLETAPI_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(DOCLETAPI_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-group,$(DOCLETAPI_GROUPNAME),$(DOCLETAPI_REGEXP)); \
	  $(call OptionTrip,-linkoffline,$(DOCLETAPI2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(DOCLETAPI_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(DOCLETAPI_PKGS))
	$(prep-target)
	$(call PackageFilter,$(DOCLETAPI_PKGS))

#############################################################
#
# tagletapidocs
#

# Part of langtools
ifdef LANGTOOLS_DIST
  ALL_OTHER_TARGETS += tagletapidocs
endif

TAGLETAPI_DOCDIR   := $(JDK_API_DOCSDIR)/javadoc/taglet
TAGLETAPI2COREAPI  := ../../$(JDKJRE2COREAPI)
TAGLETAPI_BOTTOM   := $(call CommonTrademarkBottom,$(TAGLETAPI_FIRST_COPYRIGHT_YEAR))
# TAGLETAPI_FILE is located in NON_CORE_PKGS.gmk

# Temporary directory (special generation rules)
TAGLETAPI_TEMPDIR       = $(DOCSTMPDIR)/taglets_temp

# The index.html, options, and packages files
TAGLETAPI_INDEX_FILE    = $(TAGLETAPI_DOCDIR)/index.html
TAGLETAPI_OPTIONS_FILE  = $(DOCSTMPDIR)/tagletapi.options
TAGLETAPI_PACKAGES_FILE = $(DOCSTMPDIR)/tagletapi.packages

tagletapidocs: $(TAGLETAPI_INDEX_FILE)

# Set relative location to core api document root
$(TAGLETAPI_INDEX_FILE): GET2DOCSDIR=$(TAGLETAPI2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(TAGLETAPI_INDEX_FILE): $(TAGLETAPI_OPTIONS_FILE) $(TAGLETAPI_PACKAGES_FILE)
	$(prep-javadoc)
	$(RM) -r $(TAGLETAPI_TEMPDIR)
	$(MKDIR) -p $(TAGLETAPI_TEMPDIR)
	$(call JavadocSummary,$(TAGLETAPI_OPTIONS_FILE),$(TAGLETAPI_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(TAGLETAPI_TEMPDIR) \
	  @$(TAGLETAPI_OPTIONS_FILE) @$(TAGLETAPI_PACKAGES_FILE)
	cp -r $(TAGLETAPI_TEMPDIR)/com $(@D)
	cp $(TAGLETAPI_TEMPDIR)/stylesheet.css $(@D)
	$(RM) -r $(TAGLETAPI_TEMPDIR)

# Create file with javadoc options in it
$(TAGLETAPI_OPTIONS_FILE):
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-nonavbar)					; \
	  $(call OptionOnly,-noindex)					; \
	  $(call OptionPair,-bottom,$(TAGLETAPI_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-linkoffline,$(TAGLETAPI2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(TAGLETAPI_PACKAGES_FILE): $(IMPORTSRCDIR)/$(TAGLETAPI_FILE)
	$(prep-target)
	@($(ECHO) "$(IMPORTSRCDIR)/$(TAGLETAPI_FILE)" ) > $@

#############################################################
#
# domapidocs
#

ALL_OTHER_TARGETS += domapidocs

DOMAPI_DOCDIR       := $(JRE_API_DOCSDIR)/plugin/dom
DOMAPI2COREAPI      := ../../$(JDKJRE2COREAPI)
DOMAPI_DOCTITLE     := Common DOM API
DOMAPI_WINDOWTITLE  := Common DOM API
DOMAPI_HEADER       := <strong>Common DOM API</strong>
DOMAPI_BOTTOM       := $(call CommonTrademarkBottom,$(DOMAPI_FIRST_COPYRIGHT_YEAR))
DOMAPI_GROUPNAME    := Packages
DOMAPI_REGEXP       := com.sun.java.browser.dom:org.w3c.dom*
# DOMAPI_PKGS is located in NON_CORE_PKGS.gmk

# The index.html, options, and packages files
DOMAPI_INDEX_FILE    = $(DOMAPI_DOCDIR)/index.html
DOMAPI_OPTIONS_FILE  = $(DOCSTMPDIR)/domapi.options
DOMAPI_PACKAGES_FILE = $(DOCSTMPDIR)/domapi.packages

domapidocs: $(DOMAPI_INDEX_FILE)

# Set relative location to core api document root
$(DOMAPI_INDEX_FILE): GET2DOCSDIR=$(DOMAPI2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(DOMAPI_INDEX_FILE): $(DOMAPI_OPTIONS_FILE) $(DOMAPI_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(DOMAPI_OPTIONS_FILE),$(DOMAPI_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(DOMAPI_OPTIONS_FILE) @$(DOMAPI_PACKAGES_FILE)

# Create file with javadoc options in it
$(DOMAPI_OPTIONS_FILE):
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-splitIndex)				; \
	  $(call OptionPair,-doctitle,$(DOMAPI_DOCTITLE))		; \
	  $(call OptionPair,-windowtitle,$(DOMAPI_WINDOWTITLE) $(DRAFT_WINTITLE));\
	  $(call OptionPair,-header,$(DOMAPI_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(DOMAPI_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-group,$(DOMAPI_GROUPNAME),$(DOMAPI_REGEXP)); \
	  $(call OptionTrip,-linkoffline,$(DOMAPI2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(DOMAPI_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(DOMAPI_PKGS))
	$(prep-target)
	$(call PackageFilter,$(DOMAPI_PKGS))

#############################################################
#
# jpdadocs
#

ALL_OTHER_TARGETS += jpdadocs

jpdadocs: jdidocs jdwpdocs jvmtidocs

#############################################################
#
# jdidocs
#

ALL_OTHER_TARGETS += jdidocs

JDI_DOCDIR      := $(JDK_API_DOCSDIR)/jpda/jdi
JDI2COREAPI     := ../../$(JDKJRE2COREAPI)
JDI_DOCTITLE    := Java$(TRADEMARK) Debug Interface
JDI_WINDOWTITLE := Java Debug Interface
JDI_HEADER      := <strong>Java Debug Interface</strong>
JDI_BOTTOM      := $(call CommonBottom,$(JDI_FIRST_COPYRIGHT_YEAR))
JDI_OVERVIEW    := $(SHARE_SRC)/classes/jdi-overview.html
# JDI_PKGS is located in NON_CORE_PKGS.gmk

# The index.html, options, and packages files
JDI_INDEX_FILE    = $(JDI_DOCDIR)/index.html
JDI_OPTIONS_FILE  = $(DOCSTMPDIR)/jdi.options
JDI_PACKAGES_FILE = $(DOCSTMPDIR)/jdi.packages

jdidocs: $(JDI_INDEX_FILE)

# Set relative location to core api document root
$(JDI_INDEX_FILE): GET2DOCSDIR=$(JDI2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(JDI_INDEX_FILE): $(JDI_OPTIONS_FILE) $(JDI_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(JDI_OPTIONS_FILE),$(JDI_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(JDI_OPTIONS_FILE) @$(JDI_PACKAGES_FILE)

# Create file with javadoc options in it
$(JDI_OPTIONS_FILE): $(JDI_OVERVIEW)
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionPair,-overview,$(JDI_OVERVIEW))			; \
	  $(call OptionPair,-doctitle,$(JDI_DOCTITLE))			; \
	  $(call OptionPair,-windowtitle,$(JDI_WINDOWTITLE) $(DRAFT_WINTITLE)); \
	  $(call OptionPair,-header,$(JDI_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(JDI_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-linkoffline,$(JDI2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(JDI_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(JDI_PKGS))
	$(prep-target)
	$(call PackageFilter,$(JDI_PKGS))

#############################################################
#
# jdwpdocs
#

ALL_OTHER_TARGETS += jdwpdocs

JDWP_DOCDIR     = $(PLATFORM_DOCSDIR)/jpda/jdwp
JDWP_SPEC       = $(BUILDDIR)/jpda/jdwp/jdwp.spec
JDWPGEN_JARFILE = $(BUILDTOOLJARDIR)/jdwpgen.jar

jdwpdocs: $(JDWP_DOCDIR)/jdwp-protocol.html
$(JDWP_DOCDIR)/jdwp-protocol.html: $(JDWPGEN_JARFILE) $(JDWP_SPEC)
	$(prep-javadoc)
	$(BOOT_JAVA_CMD) -jar $(JDWPGEN_JARFILE) $(JDWP_SPEC) -doc $@

#############################################################
#
# jvmtidocs
#

ALL_OTHER_TARGETS += jvmtidocs

JVMTI_DOCDIR      = $(PLATFORM_DOCSDIR)/jvmti
JVMTI_HTML        = $(HOTSPOT_DOCS_IMPORT_PATH)/platform/jvmti/jvmti.html

jvmtidocs:  $(JVMTI_DOCDIR)/jvmti.html
$(JVMTI_DOCDIR)/jvmti.html:
	@$(prep-javadoc)
	@if [ -f $(JVMTI_HTML) ] ; then		                           \
	  $(ECHO) "$(CP) $(JVMTI_HTML) $@";                 	           \
	  $(CP) $(JVMTI_HTML) $@; 	                 	           \
	else 						                   \
	  $(ECHO) "WARNING: Generated file does not exist: $(JVMTI_HTML)"; \
	fi

#############################################################
#
# jaasdocs
#

ALL_OTHER_TARGETS += jaasdocs

JAAS_DOCDIR	 := $(JRE_API_DOCSDIR)/security/jaas/spec
JAAS2COREAPI     := ../../../$(JDKJRE2COREAPI)
JAAS_DOCTITLE    := Java$(TRADEMARK) Authentication and Authorization Service
JAAS_WINDOWTITLE := Java Authentication and Authorization Service
JAAS_HEADER      := <strong>Java Authentication and Authorization Service</strong>
JAAS_BOTTOM      := $(call CommonBottom,$(JAAS_FIRST_COPYRIGHT_YEAR))
# JAAS_PKGS is located in NON_CORE_PKGS.gmk
JAAS_OVERVIEW    := $(SHARE_SRC)/classes/com/sun/security/auth/jaas-overview.html

# The index.html, options, and packages files
JAAS_INDEX_FILE    = $(JAAS_DOCDIR)/index.html
JAAS_OPTIONS_FILE  = $(DOCSTMPDIR)/jaas.options
JAAS_PACKAGES_FILE = $(DOCSTMPDIR)/jaas.packages

jaasdocs: $(JAAS_INDEX_FILE)

# Set relative location to core api document root
$(JAAS_INDEX_FILE): GET2DOCSDIR=$(JAAS2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(JAAS_INDEX_FILE): $(JAAS_OPTIONS_FILE) $(JAAS_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(JAAS_OPTIONS_FILE),$(JAAS_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(JAAS_OPTIONS_FILE) @$(JAAS_PACKAGES_FILE)

# Create file with javadoc options in it
$(JAAS_OPTIONS_FILE): $(JAAS_OVERVIEW)
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionPair,-overview,$(JAAS_OVERVIEW))			; \
	  $(call OptionPair,-doctitle,$(JAAS_DOCTITLE))			; \
	  $(call OptionPair,-windowtitle,$(JAAS_WINDOWTITLE) $(DRAFT_WINTITLE)); \
	  $(call OptionPair,-header,$(JAAS_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(JAAS_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-linkoffline,$(JAAS2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(JAAS_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(JAAS_PKGS))
	$(prep-target)
	$(call PackageFilter,$(JAAS_PKGS))

#############################################################
#
# jgssdocs
#

ALL_OTHER_TARGETS += jgssdocs

JGSS_DOCDIR	 := $(JRE_API_DOCSDIR)/security/jgss/spec
JGSS2COREAPI     := ../../../$(JDKJRE2COREAPI)
JGSS_DOCTITLE    := Java$(TRADEMARK) GSS-API Utilities
JGSS_WINDOWTITLE := Java GSS-API Utilities
JGSS_HEADER      := <strong>Java GSS-API Utilities</strong>
JGSS_BOTTOM      := $(call CommonBottom,$(JGSS_FIRST_COPYRIGHT_YEAR))
JGSS_OVERVIEW    := $(SHARE_SRC)/classes/com/sun/security/jgss/jgss-overview.html
# JGSS_PKGS is located in NON_CORE_PKGS.gmk

# The index.html, options, and packages files
JGSS_INDEX_FILE    = $(JGSS_DOCDIR)/index.html
JGSS_OPTIONS_FILE  = $(DOCSTMPDIR)/jgss.options
JGSS_PACKAGES_FILE = $(DOCSTMPDIR)/jgss.packages

jgssdocs: $(JGSS_INDEX_FILE)

# Set relative location to core api document root
$(JGSS_INDEX_FILE): GET2DOCSDIR=$(JGSS2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(JGSS_INDEX_FILE): $(JGSS_OPTIONS_FILE) $(JGSS_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(JGSS_OPTIONS_FILE),$(JGSS_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(JGSS_OPTIONS_FILE) @$(JGSS_PACKAGES_FILE)

# Create file with javadoc options in it
$(JGSS_OPTIONS_FILE): $(JGSS_OVERVIEW)
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-nodeprecatedlist)				; \
	  $(call OptionPair,-overview,$(JGSS_OVERVIEW))			; \
	  $(call OptionPair,-doctitle,$(JGSS_DOCTITLE))			; \
	  $(call OptionPair,-windowtitle,$(JGSS_WINDOWTITLE) $(DRAFT_WINTITLE)); \
	  $(call OptionPair,-header,$(JGSS_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(JGSS_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-linkoffline,$(JGSS2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(JGSS_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(JGSS_PKGS))
	$(prep-target)
	$(call PackageFilter,$(JGSS_PKGS))

#############################################################
#
# smartcardiodocs
#

ALL_OTHER_TARGETS += smartcardiodocs

SMARTCARDIO_DOCDIR	:= $(JRE_API_DOCSDIR)/security/smartcardio/spec
SMARTCARDIO2COREAPI     := ../../../$(JDKJRE2COREAPI)
SMARTCARDIO_DOCTITLE    := Java$(TRADEMARK) Smart Card I/O
SMARTCARDIO_WINDOWTITLE := Java Smart Card I/O
SMARTCARDIO_HEADER      := <strong>Java Smart Card I/O</strong>
SMARTCARDIO_BOTTOM      := $(call CommonBottom,$(SMARTCARDIO_FIRST_COPYRIGHT_YEAR))
# SMARTCARDIO_PKGS is located in NON_CORE_PKGS.gmk

# The index.html, options, and packages files
SMARTCARDIO_INDEX_FILE    = $(SMARTCARDIO_DOCDIR)/index.html
SMARTCARDIO_OPTIONS_FILE  = $(DOCSTMPDIR)/smartcardio.options
SMARTCARDIO_PACKAGES_FILE = $(DOCSTMPDIR)/smartcardio.packages

smartcardiodocs: $(SMARTCARDIO_INDEX_FILE)

# Set relative location to core api document root
$(SMARTCARDIO_INDEX_FILE): GET2DOCSDIR=$(SMARTCARDIO2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(SMARTCARDIO_INDEX_FILE): $(SMARTCARDIO_OPTIONS_FILE) $(SMARTCARDIO_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(SMARTCARDIO_OPTIONS_FILE),$(SMARTCARDIO_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(SMARTCARDIO_OPTIONS_FILE) @$(SMARTCARDIO_PACKAGES_FILE)

# Create file with javadoc options in it
$(SMARTCARDIO_OPTIONS_FILE):
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-nodeprecatedlist)				; \
	  $(call OptionPair,-doctitle,$(SMARTCARDIO_DOCTITLE))		; \
	  $(call OptionPair,-windowtitle,$(SMARTCARDIO_WINDOWTITLE) $(DRAFT_WINTITLE));\
	  $(call OptionPair,-header,$(SMARTCARDIO_HEADER)$(DRAFT_HEADER)); \
	  $(call OptionPair,-bottom,$(SMARTCARDIO_BOTTOM)$(DRAFT_BOTTOM)); \
	  $(call OptionTrip,-linkoffline,$(SMARTCARDIO2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(SMARTCARDIO_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(SMARTCARDIO_PKGS))
	$(prep-target)
	$(call PackageFilter,$(SMARTCARDIO_PKGS))

#############################################################
#
# httpserverdocs
#

ALL_OTHER_TARGETS += httpserverdocs

HTTPSERVER_DOCDIR      := $(JRE_API_DOCSDIR)/net/httpserver/spec
HTTPSERVER2COREAPI     := ../../../$(JDKJRE2COREAPI)
HTTPSERVER_DOCTITLE    := Java$(TRADEMARK) HTTP Server
HTTPSERVER_WINDOWTITLE := Java HTTP Server
HTTPSERVER_HEADER      := <strong>Java HTTP Server</strong>
HTTPSERVER_BOTTOM      := $(call CommonBottom,$(HTTPSERVER_FIRST_COPYRIGHT_YEAR))
# HTTPSERVER_PKGS is located in NON_CORE_PKGS.gmk

HTTPSERVER_INDEX_HTML    = $(HTTPSERVER_DOCDIR)/index.html
HTTPSERVER_OPTIONS_FILE  = $(DOCSTMPDIR)/httpserver.options
HTTPSERVER_PACKAGES_FILE = $(DOCSTMPDIR)/httpserver.packages

httpserverdocs: $(HTTPSERVER_INDEX_HTML)

# Set relative location to core api document root
$(HTTPSERVER_INDEX_HTML): GET2DOCSDIR=$(HTTPSERVER2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(HTTPSERVER_INDEX_HTML): $(HTTPSERVER_OPTIONS_FILE) $(HTTPSERVER_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(HTTPSERVER_OPTIONS_FILE),$(HTTPSERVER_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(HTTPSERVER_OPTIONS_FILE) @$(HTTPSERVER_PACKAGES_FILE)

# Create file with javadoc options in it
$(HTTPSERVER_OPTIONS_FILE):
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-nodeprecatedlist)				; \
	  $(call OptionPair,-doctitle,$(HTTPSERVER_DOCTITLE))		; \
	  $(call OptionPair,-windowtitle,$(HTTPSERVER_WINDOWTITLE) $(DRAFT_WINTITLE));\
	  $(call OptionPair,-header,$(HTTPSERVER_HEADER)$(DRAFT_HEADER)); \
	  $(call OptionPair,-bottom,$(HTTPSERVER_BOTTOM)$(DRAFT_BOTTOM)); \
	  $(call OptionTrip,-linkoffline,$(HTTPSERVER2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(HTTPSERVER_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(HTTPSERVER_PKGS))
	$(prep-target)
	$(call PackageFilter,$(HTTPSERVER_PKGS))

#############################################################
#
# mgmtdocs
#

ALL_OTHER_TARGETS += mgmtdocs

MGMT_DOCDIR	 := $(JRE_API_DOCSDIR)/management/extension
MGMT2COREAPI     := ../../$(JDKJRE2COREAPI)
JVM_MIB_NAME     := JVM-MANAGEMENT-MIB.mib
JVM_MIB_SRC      := $(CLOSED_SRC)/share/classes/sun/management/snmp/$(JVM_MIB_NAME)
MGMT_DOCTITLE    := Monitoring and Management Interface for the Java$(TRADEMARK) Platform
MGMT_WINDOWTITLE := Monitoring and Management Interface for the Java Platform
MGMT_HEADER      := <strong>Monitoring and Management Interface for the Java Platform</strong>
MGMT_BOTTOM      := $(call CommonBottom,$(MGMT_FIRST_COPYRIGHT_YEAR))
MGMT_OVERVIEW    := $(SHARE_SRC)/classes/com/sun/management/mgmt-overview.html
# MGMT_PKGS is located in NON_CORE_PKGS.gmk

# The index.html, options, and packages files
MGMT_INDEX_FILE    = $(MGMT_DOCDIR)/index.html
MGMT_OPTIONS_FILE  = $(DOCSTMPDIR)/mgmt.options
MGMT_PACKAGES_FILE = $(DOCSTMPDIR)/mgmt.packages

mgmtdocs: $(MGMT_INDEX_FILE)

# Set relative location to core api document root
$(MGMT_INDEX_FILE): GET2DOCSDIR=$(MGMT2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(MGMT_INDEX_FILE): $(MGMT_OPTIONS_FILE) $(MGMT_PACKAGES_FILE)
	$(prep-javadoc)
	@if [ -f $(JVM_MIB_SRC) ] ; then 				\
	  $(ECHO) "$(CP) $(JVM_MIB_SRC) $(@D)/.."; 			\
	  $(CP) $(JVM_MIB_SRC) $(@D)/.. ; 				\
	else 								\
	  $(ECHO) "WARNING: File $(JVM_MIB_NAME) not available."; 	\
	fi
	$(call JavadocSummary,$(MGMT_OPTIONS_FILE),$(MGMT_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(MGMT_OPTIONS_FILE) @$(MGMT_PACKAGES_FILE)

# Create file with javadoc options in it
$(MGMT_OPTIONS_FILE): $(MGMT_OVERVIEW)
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-nodeprecatedlist)				; \
	  $(call OptionPair,-overview,$(MGMT_OVERVIEW))			; \
	  $(call OptionPair,-doctitle,$(MGMT_DOCTITLE))			; \
	  $(call OptionPair,-windowtitle,$(MGMT_WINDOWTITLE) $(DRAFT_WINTITLE)); \
	  $(call OptionPair,-header,$(MGMT_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(MGMT_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-linkoffline,$(MGMT2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(MGMT_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(MGMT_PKGS))
	$(prep-target)
	$(call PackageFilter,$(MGMT_PKGS))

#############################################################
#
# attachdocs
#

ALL_OTHER_TARGETS += attachdocs

ATTACH_DOCDIR      := $(JDK_API_DOCSDIR)/attach/spec
ATTACH2COREAPI     := ../../$(JDKJRE2COREAPI)
ATTACH_DOCTITLE    := Attach API
ATTACH_WINDOWTITLE := Attach API
ATTACH_HEADER      := <strong>Attach API</strong>
ATTACH_BOTTOM      := $(call CommonBottom,$(ATTACH_FIRST_COPYRIGHT_YEAR))
# ATTACH_PKGS is located in NON_CORE_PKGS.gmk

ATTACH_INDEX_HTML    = $(ATTACH_DOCDIR)/index.html
ATTACH_OPTIONS_FILE  = $(DOCSTMPDIR)/attach.options
ATTACH_PACKAGES_FILE = $(DOCSTMPDIR)/attach.packages

attachdocs: $(ATTACH_INDEX_HTML)

# Set relative location to core api document root
$(ATTACH_INDEX_HTML): GET2DOCSDIR=$(ATTACH2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(ATTACH_INDEX_HTML): $(ATTACH_OPTIONS_FILE) $(ATTACH_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(ATTACH_OPTIONS_FILE),$(ATTACH_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(ATTACH_OPTIONS_FILE) @$(ATTACH_PACKAGES_FILE)

# Create file with javadoc options in it
$(ATTACH_OPTIONS_FILE):
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-nodeprecatedlist)				; \
	  $(call OptionPair,-doctitle,$(ATTACH_DOCTITLE))		; \
	  $(call OptionPair,-windowtitle,$(ATTACH_WINDOWTITLE) $(DRAFT_WINTITLE));\
	  $(call OptionPair,-header,$(ATTACH_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(ATTACH_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-linkoffline,$(ATTACH2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(ATTACH_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(ATTACH_PKGS))
	$(prep-target)
	$(call PackageFilter,$(ATTACH_PKGS))

#############################################################
#
# jconsoledocs
#

ALL_OTHER_TARGETS += jconsoledocs

JCONSOLE_DOCDIR      := $(JDK_API_DOCSDIR)/jconsole/spec
JCONSOLE2COREAPI     := ../../$(JDKJRE2COREAPI)
JCONSOLE_DOCTITLE    := JConsole API
JCONSOLE_WINDOWTITLE := JConsole API
JCONSOLE_HEADER      := <strong>JConsole API</strong>
JCONSOLE_BOTTOM      := $(call CommonBottom,$(JCONSOLE_FIRST_COPYRIGHT_YEAR))
# JCONSOLE_PKGS is located in NON_CORE_PKGS.gmk

JCONSOLE_INDEX_HTML    = $(JCONSOLE_DOCDIR)/index.html
JCONSOLE_OPTIONS_FILE  = $(DOCSTMPDIR)/jconsole.options
JCONSOLE_PACKAGES_FILE = $(DOCSTMPDIR)/jconsole.packages

jconsoledocs: $(JCONSOLE_INDEX_HTML)

# Set relative location to core api document root
$(JCONSOLE_INDEX_HTML): GET2DOCSDIR=$(JCONSOLE2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(JCONSOLE_INDEX_HTML): $(JCONSOLE_OPTIONS_FILE) $(JCONSOLE_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(JCONSOLE_OPTIONS_FILE),$(JCONSOLE_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(JCONSOLE_OPTIONS_FILE) @$(JCONSOLE_PACKAGES_FILE)

# Create file with javadoc options in it
$(JCONSOLE_OPTIONS_FILE):
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-nodeprecatedlist)				; \
	  $(call OptionPair,-doctitle,$(JCONSOLE_DOCTITLE))		; \
	  $(call OptionPair,-windowtitle,$(JCONSOLE_WINDOWTITLE) $(DRAFT_WINTITLE));\
	  $(call OptionPair,-header,$(JCONSOLE_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(JCONSOLE_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-linkoffline,$(JCONSOLE2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(JCONSOLE_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(JCONSOLE_PKGS))
	$(prep-target)
	$(call PackageFilter,$(JCONSOLE_PKGS))

#############################################################
#
# treeapidocs
#

# Part of langtools
ifdef LANGTOOLS_DIST
  ALL_OTHER_TARGETS += treeapidocs
endif

TREEAPI_DOCDIR       := $(JDK_API_DOCSDIR)/javac/tree
TREEAPI2COREAPI      := ../../$(JDKJRE2COREAPI)
TREEAPI_DOCTITLE     := Compiler Tree API
TREEAPI_WINDOWTITLE  := Compiler Tree API
TREEAPI_HEADER       := <strong>Compiler Tree API</strong>
TREEAPI_BOTTOM       := $(call CommonBottom,$(TREEAPI_FIRST_COPYRIGHT_YEAR))
TREEAPI_GROUPNAME    := Packages
TREEAPI_REGEXP       := com.sun.source.*
# TREEAPI_PKGS is located in NON_CORE_PKGS.gmk

TREEAPI_INDEX_HTML    = $(TREEAPI_DOCDIR)/index.html
TREEAPI_OPTIONS_FILE  = $(DOCSTMPDIR)/treeapi.options
TREEAPI_PACKAGES_FILE = $(DOCSTMPDIR)/treeapi.packages

treeapidocs: $(TREEAPI_INDEX_HTML)

# Set relative location to core api document root
$(TREEAPI_INDEX_HTML): GET2DOCSDIR=$(TREEAPI2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(TREEAPI_INDEX_HTML): $(TREEAPI_OPTIONS_FILE) $(TREEAPI_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(TREEAPI_OPTIONS_FILE),$(TREEAPI_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(TREEAPI_OPTIONS_FILE) @$(TREEAPI_PACKAGES_FILE)

# Create file with javadoc options in it
$(TREEAPI_OPTIONS_FILE):
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionPair,-doctitle,$(TREEAPI_DOCTITLE))		; \
	  $(call OptionPair,-windowtitle,$(TREEAPI_WINDOWTITLE) $(DRAFT_WINTITLE));\
	  $(call OptionPair,-header,$(TREEAPI_HEADER)$(DRAFT_HEADER))	; \
          $(call OptionPair,-tag,$(TAG_JLS)) 				; \
	  $(call OptionPair,-bottom,$(TREEAPI_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-group,$(TREEAPI_GROUPNAME),$(TREEAPI_REGEXP)); \
	  $(call OptionTrip,-linkoffline,$(TREEAPI2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(TREEAPI_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(TREEAPI_PKGS))
	$(prep-target)
	$(call PackageFilter,$(TREEAPI_PKGS))

#############################################################
#
# sctpdocs
#

ALL_OTHER_TARGETS += sctpdocs

SCTPAPI_DOCDIR      := $(JRE_API_DOCSDIR)/nio/sctp/spec
SCTPAPI2COREAPI     := ../../../$(JDKJRE2COREAPI)
SCTPAPI_DOCTITLE    := SCTP API
SCTPAPI_WINDOWTITLE := SCTP API
SCTPAPI_HEADER      := <strong>SCTP API</strong>
SCTPAPI_BOTTOM      := $(call CommonBottom,$(SCTPAPI_FIRST_COPYRIGHT_YEAR))
# SCTPAPI_PKGS is located in NON_CORE_PKGS.gmk

SCTPAPI_INDEX_HTML    = $(SCTPAPI_DOCDIR)/index.html
SCTPAPI_OPTIONS_FILE  = $(DOCSTMPDIR)/sctp.options
SCTPAPI_PACKAGES_FILE = $(DOCSTMPDIR)/sctp.packages

sctpdocs: $(SCTPAPI_INDEX_HTML)

# Set relative location to core api document root
$(SCTSCTSCTP: GET2DOCSDIR=$(SCTPAPI2COREAPI)/..

# Run javadoc if the index file is out of date or missing
$(SCTPAPI_INDEX_HTML): $(SCTPAPI_OPTIONS_FILE) $(SCTPAPI_PACKAGES_FILE)
	$(prep-javadoc)
	$(call JavadocSummary,$(SCTPAPI_OPTIONS_FILE),$(SCTPAPI_PACKAGES_FILE))
	$(JAVADOC_CMD) $(JAVADOC_VM_MEMORY_FLAGS) -d $(@D) \
	  @$(SCTPAPI_OPTIONS_FILE) @$(SCTPAPI_PACKAGES_FILE)

# Create file with javadoc options in it
$(SCTPAPI_OPTIONS_FILE):
	$(prep-target)
	@($(call OptionOnly,$(COMMON_JAVADOCFLAGS))			; \
	  $(call OptionPair,-sourcepath,$(RELEASEDOCS_SOURCEPATH))	; \
	  $(call OptionPair,-encoding,ascii)				; \
	  $(call OptionOnly,-nodeprecatedlist)				; \
	  $(call OptionPair,-doctitle,$(SCTPAPI_DOCTITLE))		; \
	  $(call OptionPair,-windowtitle,$(SCTPAPI_WINDOWTITLE) $(DRAFT_WINTITLE));\
	  $(call OptionPair,-header,$(SCTPAPI_HEADER)$(DRAFT_HEADER))	; \
	  $(call OptionPair,-bottom,$(SCTPAPI_BOTTOM)$(DRAFT_BOTTOM))	; \
	  $(call OptionTrip,-linkoffline,$(SCTPAPI2COREAPI),$(COREAPI_DOCSDIR)/); \
        ) >> $@

# Create a file with the package names in it
$(SCTPAPI_PACKAGES_FILE): $(DIRECTORY_CACHE) $(call PackageDependencies,$(SCTPAPI_PKGS))
	$(prep-target)
	$(call PackageFilter,$(SCTPAPI_PKGS))

#############################################################
#
# Get a cache of all the directories

$(DIRECTORY_CACHE): $(ALL_EXISTING_SOURCE_DIRS)
	$(prep-target)
	@for cp in $(ALL_EXISTING_SOURCE_DIRS) ; do 	\
	  $(ECHO) "$(FIND) $${cp} -type f >> $@"; 	\
	  $(FIND) $${cp} -type f >> $@; 		\
	done

#############################################################
#release version of core packages ########
# Maximize performance and ensure that build number & milestone are set.

rel-coredocs: sanitycheckcoredocs
	$(MAKE) coredocs

rel-docs: rel-coredocs $(ALL_OTHER_TARGETS)
#
# end of production targets

otherdocs: $(ALL_OTHER_TARGETS)

clean:
	$(RM) -r $(DOCSDIR) $(DOCSTMPDIR)

#############################################################
# DEBUG TARGET
# List the values defined in the makefile hierarchy, to make sure everything
# is set properly, and to help identify values we can use instead of making new ones.
# (Most of them come from common/shared/Defs.gmk)
#
#  Notes:
#    * BUILD_NUMBER defaults to b00 if not set on command line with BUILD_NUMBER=<value>
#    * MILESTONE defaults to internal unless set to beta, rc, or fcs on command line
#

echovalues:
	@$(ECHO) ""
	@$(ECHO) --------------Imports---------------------------
	@$(ECHO) "IMPORT_PACKAGES = $(IMPORT_PACKAGES)"
	@$(ECHO) "IMPORT_PACKAGE_FILTER = $(IMPORT_PACKAGE_FILTER)"
	@$(ECHO) --------------Imports---------------------------
	@$(ECHO) ""
	@$(ECHO) --------------Shared---------------------------
	@$(ECHO) BUILD_NUMBER = $(BUILD_NUMBER)
	@$(ECHO) FULL_VERSION = $(FULL_VERSION)
	@$(ECHO) JDK_VERSION = $(JDK_VERSION)
	@$(ECHO) JDK_MAJOR_VERSION = $(JDK_MAJOR_VERSION)
	@$(ECHO) JDK_MINOR_VERSION = $(JDK_MINOR_VERSION)
	@$(ECHO) JDK_MICRO_VERSION = $(JDK_MICRO_VERSION)
	@$(ECHO) JDK_UPDATE_VERSION = $(JDK_UPDATE_VERSION)
	@$(ECHO) JDK_MKTG_VERSION = $(JDK_MKTG_VERSION)
	@$(ECHO) JDK_UNDERSCORE_VERSION = $(JDK_UNDERSCORE_VERSION)
	@$(ECHO) JDK_MKTG_UNDERSCORE_VERSION = $(JDK_MKTG_UNDERSCORE_VERSION)
	@$(ECHO) MARKETING_NUMBER = $(MARKETING_NUMBER)
	@$(ECHO) MARKET_NAME = $(MARKET_NAME)
	@$(ECHO) MILESTONE = $(MILESTONE)
	@$(ECHO) RELEASE = $(RELEASE)
	@$(ECHO) USER_RELEASE_SUFFIX = $(USER_RELEASE_SUFFIX)
	@$(ECHO) --------------Shared---------------------------
	@$(ECHO) ""
	@$(ECHO) --------------common/Defs---------------------------
	@$(ECHO) "RELEASEDOCS_SOURCEPATH"
	@$(ECHO) "   SHARE_SRC/classes: $(SHARE_SRC)/classes"
	@$(ECHO) "   PLATFORM_SRC/classes: $(PLATFORM_SRC)/classes"
	@$(ECHO) "   GENSRCDIR: $(GENSRCDIR)"
	@$(ECHO) "   SHARE_SRC/doc/stub: $(SHARE_SRC)/doc/stub"
	@$(ECHO) "   IMPORTSRCDIR: $(IMPORTSRCDIR)"
	@$(ECHO) --------------common/Defs---------------------------
	@$(ECHO) ""

#############################################################
.PHONY: all docs coredocs rel-docs echovalues otherdocs rel-coredocs \
        sanitycheckcoredocs $(ALL_OTHER_TARGETS)

