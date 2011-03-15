#
# Copyright (c) 1996, 2010, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../../..
PACKAGE = java.text
PRODUCT = sun

include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES_java.gmk

#
# Include
#
include $(BUILDDIR)/common/Classes.gmk

#
# Rules to add data files BreakIterator
#
TEXT_CLASSES = $(BUILDTOOLCLASSDIR)/java.text/classes

# input
#
#   Notes: sun.text.resources.BreakIteratorRules no longer goes to runtime.
#     They are used at JDK build phase in order to create $(BIFILES) which
#     are used on runtime instead.
#
TEXT_SRCDIR  = $(SHARE_SRC)/classes/sun/text/resources
TEXT_SOURCES = $(TEXT_SRCDIR)/BreakIteratorRules.java \
               $(TEXT_SRCDIR)/BreakIteratorInfo.java
UNICODEDATA  = $(BUILDDIR)/tools/UnicodeData

# output
TEXT_CLASSDIR  = $(CLASSBINDIR)/sun/text/resources
BIFILES = $(TEXT_CLASSDIR)/CharacterBreakIteratorData \
          $(TEXT_CLASSDIR)/WordBreakIteratorData \
          $(TEXT_CLASSDIR)/LineBreakIteratorData \
          $(TEXT_CLASSDIR)/SentenceBreakIteratorData

ICU_FILES = $(TEXT_CLASSDIR)/unorm.icu \
            $(TEXT_CLASSDIR)/uprops.icu \
            $(TEXT_CLASSDIR)/ubidi.icu

# builder
GENERATEBREAKITERATORDATA_JARFILE = \
    $(BUILDTOOLJARDIR)/generatebreakiteratordata.jar

$(BIFILES): $(GENERATEBREAKITERATORDATA_JARFILE) \
    $(UNICODEDATA)/UnicodeData.txt \
    $(TEXT_SOURCES)
	$(prep-target)
	$(RM) -r $(TEXT_CLASSES)
	$(MKDIR) -p $(TEXT_CLASSES)
	$(BOOT_JAVAC_CMD) -d $(TEXT_CLASSES) \
		-sourcepath $(TEXT_SRCDIR) \
		$(TEXT_SOURCES)
	$(MKDIR) -p  $(TEXT_CLASSDIR)
	$(BOOT_JAVA_CMD) -Xbootclasspath/p:$(TEXT_CLASSES) \
		-jar $(GENERATEBREAKITERATORDATA_JARFILE) \
		-o $(TEXT_CLASSDIR) \
		-spec $(UNICODEDATA)/UnicodeData.txt
	@$(java-vm-cleanup)

build: $(BIFILES) $(ICU_FILES)

#
# Extra rules to copy unorm.icu, uprops.icu, and ubidi.icu
#
$(TEXT_CLASSDIR)/unorm.icu: $(TEXT_SRCDIR)/unorm.icu
	$(install-file)

$(TEXT_CLASSDIR)/uprops.icu: $(TEXT_SRCDIR)/uprops.icu
	$(install-file)

$(TEXT_CLASSDIR)/ubidi.icu: $(TEXT_SRCDIR)/ubidi.icu
	$(install-file)

clean clobber::
	$(RM) -r $(TEXT_CLASSES)
	$(RM) -r $(BIFILES)
	$(RM) $(ICU_FILES)

