#
# Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for locale data.
#

BUILDDIR = ../..

MODULE  = localedata
PACKAGE = sun.text
PRODUCT = sun

include $(BUILDDIR)/common/Defs.gmk

# Re-direct classes to another location (we are building localedata.jar)
CLASSDESTDIR = $(TEMPDIR)/classes

#
# Files
#
include FILES_java.gmk
include FILES_properties.gmk

#
# Compile the properties files
#
COMPILED_PROPERTIES_SUPERCLASS=LocaleNamesBundle

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

#
# Rules to add data files BreakIterator (for th locale)
#
TEXT_CLASSES = $(BUILDTOOLCLASSDIR)/sun.text/classes

# input
#
#   Notes: sun.text.resources.BreakIteratorRules_th no longer goes to runtime.
#     They are used at JDK build phase in order to create $(BIFILES) which
#     are used on runtime instead.
#
TEXT_SRCDIR = $(SHARE_SRC)/classes/sun/text/resources
BIRULES = $(TEXT_SRCDIR)/BreakIteratorRules.java \
          $(TEXT_SRCDIR)/BreakIteratorRules_th.java
BIINFO = $(TEXT_SRCDIR)/BreakIteratorInfo_th.java
UNICODEDATA = $(BUILDDIR)/tools/UnicodeData

# output
BIFILES = $(CLASSDESTDIR)/sun/text/resources/WordBreakIteratorData_th \
          $(CLASSDESTDIR)/sun/text/resources/LineBreakIteratorData_th

# builder
GENERATEBREAKITERATORDATA_JARFILE = \
    $(BUILDTOOLJARDIR)/generatebreakiteratordata.jar

$(BIFILES): $(GENERATEBREAKITERATORDATA_JARFILE) $(BIRULES) $(BIINFO) \
    $(UNICODEDATA)/UnicodeData.txt
	$(prep-target)
	$(MKDIR) -p $(TEXT_CLASSES)
	$(BOOT_JAVAC_CMD) -d $(TEXT_CLASSES) \
		-sourcepath $(TEXT_SRCDIR) \
		$(BIRULES) $(BIINFO)
	$(BOOT_JAVA_CMD) -Xbootclasspath/p:$(TEXT_CLASSES) \
		-jar $(GENERATEBREAKITERATORDATA_JARFILE) \
		-o $(CLASSDESTDIR)/sun/text/resources \
		-spec $(UNICODEDATA)/UnicodeData.txt \
		-language th
	@$(java-vm-cleanup)

clean::
	$(RM) -r $(TEXT_CLASSES)
	$(RM) -r $(BIFILES)
#
# End of rules to add data files for BreakIterator
#

#
# Extra rules to build locale data.
#
LOCALEDATA_JAR = $(EXTDIR)/localedata.jar

SPECIALFILES = $(CLASSDESTDIR)/sun/text/resources/thai_dict

$(CLASSDESTDIR)/sun/text/resources/% : $(TEXT_SRCDIR)/%
	$(install-file)

$(LOCALEDATA_JAR): $(EXTDIR) $(FILES_class) $(BIFILES) $(SPECIALFILES)
	$(prep-target)
	$(BOOT_JAR_CMD) -cf $@ -C $(CLASSDESTDIR) sun \
	     $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

build: $(LOCALEDATA_JAR)

clean clobber::
	$(RM) -r $(CLASSDESTDIR) 
	$(RM) $(LOCALEDATA_JAR)

