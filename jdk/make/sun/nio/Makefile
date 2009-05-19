#
# Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for character converters.
#

BUILDDIR = ../..

PACKAGE = sun.nio
PRODUCT = sun

# This re-directs all the class files to a separate location
CLASSDESTDIR = $(TEMPDIR)/classes

OTHER_JAVACFLAGS += -Xlint:serial -Werror
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES_java.gmk
AUTO_FILES_JAVA_DIRS = sun/nio/cs/ext

# Exclude a few sources on windows
ifeq ($(PLATFORM), windows)
  AUTO_JAVA_PRUNE = sun/nio/cs/ext/COMPOUND_TEXT.java         \
                    sun/nio/cs/ext/COMPOUND_TEXT_Decoder.java \
                    sun/nio/cs/ext/COMPOUND_TEXT_Encoder.java \
                    sun/nio/cs/ext/CompoundTextSupport.java
endif # PLATFORM

# For Cygwin, command line arguments that are paths must be converted to
# windows style paths. These paths cannot be used as targets, however, because 
# the ":" in them  will interfere with GNU Make rules, generating "multiple
# target pattern" errors.

# this define is for the rule:
CHARSETS_JAR = $(LIBDIR)/charsets.jar

# extsbcs
FILES_genout_extcs = $(FILES_gen_extcs:%.java=$(GENSRCDIR)/%.java)

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

build: $(FILES_genout_extcs) $(CHARSETS_JAR)

#
# Extra rules to build character converters.

SERVICE_DESCRIPTION = java.nio.charset.spi.CharsetProvider
SERVICE_DESCRIPTION_PATH = META-INF/services/$(SERVICE_DESCRIPTION)

GENCSDATASRC = $(BUILDDIR)/tools/CharsetMapping
GENCSSRCDIR = $(BUILDDIR)/tools/src/build/tools/charsetmapping
GENCSEXT = $(GENSRCDIR)/sun/nio/cs/ext

FILES_MAP = $(GENCSDATASRC)/sjis0213.map
FILES_DAT = $(CLASSDESTDIR)/sun/nio/cs/ext/sjis0213.dat
CHARSETMAPPING_JARFILE = $(BUILDTOOLJARDIR)/charsetmapping.jar

$(FILES_DAT): $(FILES_MAP)
	@$(prep-target)
	$(BOOT_JAVA_CMD) -jar $(CHARSETMAPPING_JARFILE) \
		$(FILES_MAP) $(FILES_DAT) sjis0213


$(FILES_genout_extcs): $(GENCSDATASRC)/SingleByte-X.java $(GENCSDATASRC)/extsbcs
	@$(prep-target)
	$(RM) -r $(GENCSEXT)
	$(MKDIR) -p $(GENCSEXT)
	$(BOOT_JAVA_CMD) -jar $(CHARSETMAPPING_JARFILE)	$(GENCSDATASRC) $(GENCSEXT) extsbcs
	$(BOOT_JAVA_CMD) -jar $(CHARSETMAPPING_JARFILE) $(GENCSDATASRC) $(GENCSEXT) euctw \
		$(GENCSSRCDIR)/GenerateEUC_TW.java

$(CLASSDESTDIR)/$(SERVICE_DESCRIPTION_PATH): \
  $(SHARE_SRC)/classes/sun/nio/cs/ext/$(SERVICE_DESCRIPTION_PATH)
	$(install-file)

$(CHARSETS_JAR): $(FILES_class) $(CLASSDESTDIR)/$(SERVICE_DESCRIPTION_PATH) $(FILES_DAT)
	$(BOOT_JAR_CMD) cf $(CHARSETS_JAR) \
	      -C $(CLASSDESTDIR) sun \
	      -C $(CLASSDESTDIR) $(SERVICE_DESCRIPTION_PATH)  \
	      $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

clean::
	$(RM) -r $(CLASSDESTDIR)
	$(RM) $(CHARSETS_JAR)
