#
# Copyright 2006-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

# Simple Makefile for javac

BUILD = build
BUILD_BOOTCLASSES = $(BUILD)/bootclasses
BUILD_CLASSES = $(BUILD)/classes
BUILD_JAVAC_SRCFILES = $(BUILD)/javac.srcfiles
GENSRCDIR = $(BUILD)/gensrc
DIST = dist
DIST_JAVAC = $(DIST)
ABS_DIST_JAVAC = $(shell cd $(DIST_JAVAC) ; pwd)
SRC_BIN = src/bin
SRC_CLASSES = src/share/classes

#--------------------------------------------------------------------------------
#
# version info for generated compiler

JDK_VERSION = 1.7.0
RELEASE=$(JDK_VERSION)-opensource
BUILD_NUMBER = b00
USER_RELEASE_SUFFIX := $(shell echo $(USER)_`date '+%d_%b_%Y_%H_%M' | tr "A-Z" "a-z"`)
FULL_VERSION = $(RELEASE)-$(USER_RELEASE_SUFFIX)-$(BUILD_NUMBER)

#--------------------------------------------------------------------------------

CAT	= /bin/cat
CHMOD	= /bin/chmod
CP	= /bin/cp
MKDIR 	= /bin/mkdir
RM 	= /bin/rm
SED 	= /bin/sed


SYSTEM_UNAME := $(shell uname)

# Platform settings specific to Solaris
ifeq ($(SYSTEM_UNAME), SunOS)
  # Intrinsic unix command, with backslash-escaped character interpretation
  #   (not using -e  will cause build failure when using /bin/bash)
  #   (using -e breaks something else)
  ECHO           = /usr/bin/echo
  PLATFORM       = solaris
endif

# Platform settings specific to Linux
ifeq ($(SYSTEM_UNAME), Linux)
  # Intrinsic unix command, with backslash-escaped character interpretation
  ECHO           = echo -e
  PLATFORM       = linux
endif


# Set BOOTDIR to specify the JDK used to build the compiler
ifdef	BOOTDIR
JAR	= $(BOOTDIR)/bin/jar
JAVA	= $(BOOTDIR)/bin/java
JAVAC	= $(BOOTDIR)/bin/javac
JAVADOC = $(BOOTDIR)/bin/javadoc
else
JAR	= jar
JAVA	= java
JAVAC	= javac
JAVADOC = javadoc
endif

ifndef JTREG
ifdef JTREG_HOME
JTREG   = $(JTREG_HOME)/$(PLATFORM)/bin/jtreg
else
JTREG	= jtreg
endif
endif

ifndef	JTREG_OPTS
JTREG_OPTS = -s -verbose:summary
endif

ifndef	JTREG_TESTS
JTREG_TESTS = test/tools/javac
endif

# Set this to the baseline version of JDK used for the tests
# TESTJDKHOME = 

COMPILER_SOURCE_LEVEL = 1.5

#--------------------------------------------------------------------------------
SCM_DIRS = -name .hg -o -name .svn -o -name CVS -o -name RCS -o -name SCCS
JAVAC_SRCS = $(shell find \
        $(SRC_CLASSES)/javax/annotation/processing \
        $(SRC_CLASSES)/javax/lang/model \
        $(SRC_CLASSES)/javax/tools \
        $(SRC_CLASSES)/com/sun/source \
        $(SRC_CLASSES)/com/sun/tools/javac \
	\( $(SCM_DIRS) -o -name \*-template.\* \) -prune -o -name \*.java -print )

JAVAC_RESOURCES = $(shell ls $(SRC_CLASSES)/com/sun/tools/javac/resources/*.properties | $(SED) -e 's/-template//' )


#--------------------------------------------------------------------------------

default: build

all: build docs

clean:
	$(RM) -rf $(BUILD) $(DIST)

build:	sanity $(DIST_JAVAC)/lib/javac.jar $(DIST_JAVAC)/bin/javac 


# javac.jar

$(DIST_JAVAC)/lib/javac.jar: \
		$(JAVAC_SRCS) \
		$(patsubst $(SRC_CLASSES)/%,$(BUILD_BOOTCLASSES)/%,$(JAVAC_RESOURCES)) \
		$(patsubst $(SRC_CLASSES)/%,$(BUILD_CLASSES)/%,$(JAVAC_RESOURCES)) 
	@$(ECHO) $(JAVAC_SRCS) > $(BUILD_JAVAC_SRCFILES)
	$(JAVAC) -d $(BUILD_BOOTCLASSES) -source $(COMPILER_SOURCE_LEVEL) -g:source,lines @$(BUILD_JAVAC_SRCFILES)
	$(JAVA) -cp $(BUILD_BOOTCLASSES) com.sun.tools.javac.Main \
		-d $(BUILD_CLASSES) -g:source,lines @$(BUILD_JAVAC_SRCFILES)
	( $(ECHO) "Main-Class: com.sun.tools.javac.Main" ; \
	  $(ECHO) "Built-By: $$USER" ; \
	  $(ECHO) "Built-At: `date`" ) > $(BUILD)/javac.MF
	$(MKDIR) -p $(DIST_JAVAC)/lib
	$(JAR) -cmf $(BUILD)/javac.MF $(DIST_JAVAC)/lib/javac.jar -C ${BUILD_CLASSES} .


# javac resources

$(BUILD_BOOTCLASSES)/com/sun/tools/javac/resources/version.properties \
$(BUILD_CLASSES)/com/sun/tools/javac/resources/version.properties: \
		$(SRC_CLASSES)/com/sun/tools/javac/resources/version-template.properties
	$(MKDIR) -p $(@D)
	$(SED) 	-e 's/$$(JDK_VERSION)/$(JDK_VERSION)/'  \
		-e 's/$$(FULL_VERSION)/$(FULL_VERSION)/' \
		-e 's/$$(RELEASE)/$(RELEASE)/' \
		< $< > $@

$(BUILD_BOOTCLASSES)/com/sun/tools/javac/resources/%.properties: \
		$(SRC_CLASSES)/com/sun/tools/javac/resources/%.properties
	$(MKDIR) -p $(@D)
	$(CP) $^ $@

$(BUILD_CLASSES)/com/sun/tools/javac/resources/%.properties: \
		$(SRC_CLASSES)/com/sun/tools/javac/resources/%.properties
	$(MKDIR) -p $(@D)
	$(CP) $^ $@


# javac wrapper script

$(DIST_JAVAC)/bin/javac: $(SRC_BIN)/javac.sh
	$(MKDIR) -p $(@D)
	$(CP) $^ $@
	$(CHMOD) +x $@

# javadoc

JLS3_URL = http://java.sun.com/docs/books/jls/
JLS3_CITE = <a href="$(JLS3_URL)"> \
		The Java Language Specification, Third Edition</a>
TAG_JLS3 = -tag 'jls3:a:See <cite>$(JLS3_CITE)</cite>:'

TAGS = $(IGNORED_TAGS:%=-tag %:X) $(TAG_JLS3)

docs:	
	$(JAVADOC) -sourcepath $(SRC_CLASSES) -d $(DIST_JAVAC)/doc/api \
	    $(TAGS) \
            -subpackages javax.annotation.processing:javax.lang.model:javax.tools:com.sun.source:com.sun.tools.javac

#--------------------------------------------------------------------------------

test: test-sanity $(DIST_JAVAC)/lib/javac.jar
	$(JTREG) $(JTREG_OPTS) -noshell \
		-jdk:$(TESTJDKHOME) \
		-Xbootclasspath/p:$(ABS_DIST_JAVAC)/lib/javac.jar \
		-w:$(BUILD)/jtreg/work \
		-r:$(BUILD)/jtreg/report \
		$(JTREG_TESTS)

#--------------------------------------------------------------------------------

ifndef ERROR_FILE
  ERROR_FILE   = $(BUILD)/sanityCheckErrors.txt
endif

presanity:
	@$(RM) -f $(ERROR_FILE)
	@$(MKDIR) -p `dirname $(ERROR_FILE)`

######################################################
# CLASSPATH cannot be set, unless you are insane.
######################################################
sane-classpath:
ifdef CLASSPATH
	@$(ECHO) "ERROR: Your CLASSPATH environment variable is set.  This will \n" \
	   "      most likely cause the build to fail.  Please unset it \n" \
	   "      and start your build again. \n" \
	   "" >> $(ERROR_FILE)
endif

######################################################
# JAVA_HOME cannot be set, unless you are insane.
######################################################
sane-java_home:
ifdef JAVA_HOME
	@$(ECHO) "ERROR: Your JAVA_HOME environment variable is set.  This will \n" \
	   "      most likely cause the build to fail.  Please unset it \n" \
	   "      and start your build again. \n" \
	   "" >> $(ERROR_FILE)
endif


######################################################
# TESTJDKHOME needs to be set to run tests
######################################################
sane-testjdk:
ifndef TESTJDKHOME
	@$(ECHO) "ERROR: TESTJDKHOME needs to be set to the baseline version \n" \
	   "     version of JDK used to run the compiler tests.\n" \
	   "" >> $(ERROR_FILE)
endif


sane-lastrule:
	@if [ -r $(ERROR_FILE) ]; then \
	  if [ "x$(INSANE)" = x ]; then \
	    $(ECHO) "Exiting because of the above error(s). \n" \
	      "">> $(ERROR_FILE); \
	  fi ; \
	  $(CAT) $(ERROR_FILE) ; \
	  if [ "x$(INSANE)" = x ]; then \
	    exit 1 ; \
	  fi ; \
	fi

sanity \
build-sanity: presanity sane-classpath sane-java_home sane-lastrule

test-sanity: presanity sane-classpath sane-java_home sane-testjdk sane-lastrule




#--------------------------------------------------------------------------------

.PHONY: all build clean default docs prep test \
	presanity sanity build-sanity test-sanity \
	sane-classpath sane-java_home sane-testjdk sane-lastrule 
