#
# Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for core Java libraries
# (java.lang, java.lang.ref, java.lang.reflect, java.io, java.util)
#

BUILDDIR = ../..
MODULE  = base
PACKAGE = java.lang
LIBRARY = java
PRODUCT = java
include $(BUILDDIR)/common/Defs.gmk

# windows compiler flags
ifeq ($(PLATFORM),windows)
  OTHER_CFLAGS =
  # build directly into BINDIR...
  LIB_LOCATION = $(BINDIR)
  # Exported functions
  OTHER_LCF = -export:winFileHandleOpen -export:handleLseek
endif

OTHER_CFLAGS += -DJDK_MAJOR_VERSION='"$(JDK_MAJOR_VERSION)"' \
                -DJDK_MINOR_VERSION='"$(JDK_MINOR_VERSION)"' \
                -DJDK_MICRO_VERSION='"$(JDK_MICRO_VERSION)"' \
                -DJDK_BUILD_NUMBER='"$(JDK_BUILD_NUMBER)"' 

ifdef JDK_UPDATE_VERSION
OTHER_CFLAGS += -DJDK_UPDATE_VERSION='"$(JDK_UPDATE_VERSION)"'
endif


#
# Files to compile.
#
include FILES_c.gmk
include FILES_java.gmk
include Exportedfiles.gmk

ifeq ($(PLATFORM),windows)
FILES_java += 	java/io/Win32FileSystem.java \
		java/io/WinNTFileSystem.java \
		java/util/prefs/WindowsPreferences.java \
                java/util/prefs/WindowsPreferencesFactory.java

FILES_c    +=   ProcessImpl_md.c \
		Win32FileSystem_md.c \
		WinNTFileSystem_md.c \
		canonicalize_md.c \
		dirent_md.c \
		TimeZone.c \
		TimeZone_md.c \
		WindowsPreferences.c \
		sun/security/provider/WinCAPISeedGenerator.c \
		sun/io/Win32ErrorMode.c

else # PLATFORM
FILES_java += 	java/lang/UNIXProcess.java \
		java/io/UnixFileSystem.java \
		java/util/prefs/FileSystemPreferences.java \
                java/util/prefs/FileSystemPreferencesFactory.java \

FILES_c    +=   UNIXProcess_md.c \
		UnixFileSystem_md.c \
		canonicalize_md.c \
		TimeZone.c \
		TimeZone_md.c \
		FileSystemPreferences.c

INIT += $(GENSRCDIR)/java/lang/UNIXProcess.java

endif # PLATFORM

#
# Make sure first rule does 'all'
#
default_rule: all

#
# Source files to generate before we try to compile anything
#

#
# genlocales.gmk define rules to generate LocaleDataMetaInfo.java
# which contains locale string list for sun.text.resources and
# sun.util.resources.
#
include genlocales.gmk

CLASSES_INIT = gencharsrc niosrc rbcontrolsrc genlocales

niosrc: ; ($(CD) ../nio; $(MAKE) sources)

gencharsrc: $(GENSRCDIR)/java/lang/CharacterData00.java \
    $(GENSRCDIR)/java/lang/CharacterData01.java \
    $(GENSRCDIR)/java/lang/CharacterData02.java \
    $(GENSRCDIR)/java/lang/CharacterData0E.java \
    $(GENSRCDIR)/java/lang/CharacterDataLatin1.java \
    $(GENSRCDIR)/java/lang/CharacterDataUndefined.java \
    $(GENSRCDIR)/java/lang/CharacterDataPrivateUse.java

rbcontrolsrc: $(GENSRCDIR)/sun/util/CoreResourceBundleControl.java

#
# Add to the default C file search paths
#
vpath %.c $(PLATFORM_SRC)/native/java/lang:$(SHARE_SRC)/native/java/lang
vpath %.c $(SHARE_SRC)/native/java/lang/ref
vpath %.c $(SHARE_SRC)/native/java/lang/reflect
vpath %.c $(SHARE_SRC)/native/java/io
vpath %.c $(PLATFORM_SRC)/native/java/io
vpath %.c $(SHARE_SRC)/native/java/nio
vpath %.c $(SHARE_SRC)/native/java/security
vpath %.c $(SHARE_SRC)/native/common
vpath %.c $(SHARE_SRC)/native/sun/misc
vpath %.c $(SHARE_SRC)/native/sun/reflect
vpath %.c $(SHARE_SRC)/native/java/sql
vpath %.c $(SHARE_SRC)/native/java/util
vpath %.c $(SHARE_SRC)/native/java/util/concurrent/atomic
vpath %.c $(PLATFORM_SRC)/native/common
vpath %.c $(PLATFORM_SRC)/native/java/util
vpath %.c $(PLATFORM_SRC)/native/sun/security/provider
vpath %.c $(PLATFORM_SRC)/native/sun/io

#
# Includes the fdlibm header file.
#
OTHER_INCLUDES += -I$(SHARE_SRC)/native/java/lang/fdlibm/include

#
# Also include the io_util.h header file.
#
OTHER_INCLUDES += -I$(SHARE_SRC)/native/java/io
OTHER_INCLUDES += -I$(PLATFORM_SRC)/native/java/io

#
# Also include the TimeZone_md.h header file.
#
OTHER_INCLUDES += -I$(PLATFORM_SRC)/native/java/util

#
# Is the altzone extern documented in ctime(3C) available?
#
ifneq ($(PLATFORM), windows)
HAVE_ALTZONE=true
endif

ifeq ($(HAVE_ALTZONE),true)
OTHER_CPPFLAGS += -DHAVE_ALTZONE
endif

#
# System.c needs RELEASE.
# java_props_md.c needs ARCH on non-Solaris platforms.
#
OTHER_CPPFLAGS += $(VERSION_DEFINES)

#
# Include name of LIBARCH
#
OTHER_CPPFLAGS += -DARCHPROPNAME='"$(ARCHPROP)"'

#
# Is the key in the call nl_langinfo(CODESET) called something other than
# CODESET?
#
ifdef ALT_CODESET_KEY
OTHER_CPPFLAGS += -DALT_CODESET_KEY=$(ALT_CODESET_KEY)
endif

#
# Install .lib file.
#
INSTALL_DOT_LIB = true

#
# What to link?
#
ifeq ($(PLATFORM),windows)
OTHER_LDLIBS += $(JVMLIB) -libpath:$(OBJDIR)/../../../fdlibm/$(OBJDIRNAME) fdlibm.lib \
		       -libpath:$(OBJDIR)/../../../verify/$(OBJDIRNAME) verify.lib
else
OTHER_LDLIBS += $(JVMLIB) -lverify $(LIBSOCKET) -lnsl -ldl \
		-L$(OBJDIR)/../../../fdlibm/$(OBJDIRNAME) -lfdlibm.$(ARCH)
endif

#
# Rules.
#
ifeq ($(PLATFORM), solaris)
  ifneq ($(ARCH), amd64)
    FILES_reorder += reorder-$(ARCH)
  endif
endif

SUBDIRS = reflect
include $(BUILDDIR)/common/Subdirs.gmk

all build:
	$(SUBDIRS-loop)
clean clobber::
	$(SUBDIRS-loop)

include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk

ifeq ($(PLATFORM), solaris)
  ifeq ($(CC_VER), 5.8)
    ifndef REMOVE_ALL_WORKAROUNDS
      CFLAGS_OPT/ObjectInputStream.o = -xO3 \
	  $(warning  "WARNING: Using workaround for SS11 bug 6346242, on $@")
    endif
  endif
endif

ifneq ($(PLATFORM),windows)

# UNIXProcess.java is different for solaris and linux. We need to copy
# the correct UNIXProcess.java over to $(GENSRCDIR)/java/lang/.

$(GENSRCDIR)/java/lang/UNIXProcess.java: \
    $(PLATFORM_SRC)/classes/java/lang/UNIXProcess.java.$(PLATFORM)
	$(install-non-module-file)

clean:: 
	$(RM) $(GENSRCDIR)/java/lang/UNIXProcess.java

endif


#
# Because we can't link against ourselves!
#
JAVALIB =

#
# Special rules.
#
clean:: 
	$(RM) -r $(CLASSHDRDIR)

clobber:: 
	$(RM) -r $(CLASSBINDIR)/java/io $(CLASSBINDIR)/java/lang \
		$(CLASSBINDIR)/java/security $(CLASSBINDIR)/java/util \
		$(CLASSBINDIR)/sun/misc

#
# Additional rule from sun/net/GNUmakefile to copy content-type.properties
# file so that the MimeTable class won't complain while bootstrapping...
#
PROPS = content-types.properties

#
# Rule to copy calendars.properties file.
#
CAL_PROPS = calendars.properties

#
# Rule to copy tzmappings file on Windows
#
ifeq ($(PLATFORM), windows)
TZMAP = $(LIBDIR)/tzmappings
TZMAPFILE = $(PLATFORM_SRC)/lib/tzmappings

$(TZMAP): $(TZMAPFILE)
	$(install-file)
	$(call chmod-file, 444)
endif

build: $(LIBDIR)/$(PROPS) $(LIBDIR)/$(CAL_PROPS) $(TZMAP)

$(LIBDIR)/$(PROPS): $(PLATFORM_SRC)/lib/$(PROPS)
	$(install-file)

$(LIBDIR)/$(CAL_PROPS): $(SHARE_SRC)/lib/$(CAL_PROPS)
	$(install-file)

clean:: 
	$(RM) -r $(LIBDIR)/$(PROPS) $(TZMAP)

#
# Rules to create lib/currency.data
#

CURDATA = $(LIBDIR)/currency.data
GENERATECURRENCYDATA_JARFILE = $(BUILDTOOLJARDIR)/generatecurrencydata.jar

build: $(CURDATA)

$(CURDATA): \
  $(GENERATECURRENCYDATA_JARFILE) \
  $(SHARE_SRC)/classes/java/util/CurrencyData.properties
	$(RM) $(CURDATA)
	$(BOOT_JAVA_CMD) -jar $(GENERATECURRENCYDATA_JARFILE) -o $@.temp \
		< $(SHARE_SRC)/classes/java/util/CurrencyData.properties
	$(MV) $@.temp $@
	$(install-module-file)
	$(call chmod-file, 444)

clean:: 
	$(RM) $(CURDATA)


#
# Rules to create $(GENSRCDIR)/sun/lang/CharacterData*.java 
#
CHARACTERDATA = $(BUILDDIR)/tools/GenerateCharacter
UNICODEDATA   = $(BUILDDIR)/tools/UnicodeData

GENERATECHARACTER_JARFILE = $(BUILDTOOLJARDIR)/generatecharacter.jar

$(GENSRCDIR)/java/lang/CharacterDataLatin1.java \
  $(GENSRCDIR)/java/lang/CharacterData00.java \
  $(GENSRCDIR)/java/lang/CharacterData01.java \
  $(GENSRCDIR)/java/lang/CharacterData02.java \
  $(GENSRCDIR)/java/lang/CharacterData0E.java: \
    $(wildcard $(CHARACTERDATA)/*) \
    $(GENERATECHARACTER_JARFILE)
	@$(prep-target)
	$(BOOT_JAVA_CMD) -jar $(GENERATECHARACTER_JARFILE) \
		-template $(CHARACTERDATA)/CharacterDataLatin1.java.template \
		-spec $(UNICODEDATA)/UnicodeData.txt \
		-specialcasing $(UNICODEDATA)/SpecialCasing.txt \
		-o $(GENSRCDIR)/java/lang/CharacterDataLatin1.java -string \
		-usecharforbyte -latin1 8
	$(BOOT_JAVA_CMD) -jar $(GENERATECHARACTER_JARFILE) -plane 0 \
		-template $(CHARACTERDATA)/CharacterData00.java.template \
		-spec $(UNICODEDATA)/UnicodeData.txt \
		-specialcasing $(UNICODEDATA)/SpecialCasing.txt \
		-o $(GENSRCDIR)/java/lang/CharacterData00.java -string \
		-usecharforbyte 11 4 1
	$(BOOT_JAVA_CMD) -jar $(GENERATECHARACTER_JARFILE) -plane 1 \
		-template $(CHARACTERDATA)/CharacterData01.java.template \
		-spec $(UNICODEDATA)/UnicodeData.txt \
		-specialcasing $(UNICODEDATA)/SpecialCasing.txt \
		-o $(GENSRCDIR)/java/lang/CharacterData01.java -string \
		-usecharforbyte  11 4 1
	$(BOOT_JAVA_CMD) -jar $(GENERATECHARACTER_JARFILE) -plane 2 \
		-template $(CHARACTERDATA)/CharacterData02.java.template \
		-spec $(UNICODEDATA)/UnicodeData.txt \
		-specialcasing $(UNICODEDATA)/SpecialCasing.txt \
		-o $(GENSRCDIR)/java/lang/CharacterData02.java -string \
		-usecharforbyte 11 4 1
	$(BOOT_JAVA_CMD) -jar $(GENERATECHARACTER_JARFILE) -plane 14 \
		-template $(CHARACTERDATA)/CharacterData0E.java.template \
		-spec $(UNICODEDATA)/UnicodeData.txt \
		-specialcasing $(UNICODEDATA)/SpecialCasing.txt \
		-o $(GENSRCDIR)/java/lang/CharacterData0E.java -string \
		-usecharforbyte 11 4 1

$(GENSRCDIR)/java/lang/%.java : $(CHARACTERDATA)/%.java.template
	$(install-non-module-file)

clean::
	$(RM) $(GENSRCDIR)/java/lang/CharacterDataLatin1.java 
	$(RM) $(GENSRCDIR)/java/lang/CharacterData00.java
	$(RM) $(GENSRCDIR)/java/lang/CharacterData01.java 
	$(RM) $(GENSRCDIR)/java/lang/CharacterData02.java
	$(RM) $(GENSRCDIR)/java/lang/CharacterData0E.java
	$(RM) $(GENSRCDIR)/java/lang/CharacterDataUndefined.java 
	$(RM) $(GENSRCDIR)/java/lang/CharacterDataPrivateUse.java

#
# End of rules to create $(GENSRCDIR)/java/lang/CharacterDataXX.java
#

#
# Rule to precompile CoreResourceBundleControl.java 
#
LOCALES_GEN_SH = localelist.sh

$(GENSRCDIR)/sun/util/CoreResourceBundleControl.java: \
	$(SHARE_SRC)/classes/sun/util/CoreResourceBundleControl-XLocales.java.template $(LOCALES_GEN_SH)
	@$(prep-target) 
	NAWK="$(NAWK)" SED="$(SED)" $(SH) $(LOCALES_GEN_SH) "$(JRE_NONEXIST_LOCALES)" \
		$< $@ 
clean::
	$(RM) $(GENSRCDIR)/sun/util/CoreResourceBundleControl.java

