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
# Makefile for CLDR locale data.
#

BUILDDIR = ../..

PACKAGE = sun.text.resources.cldr
PRODUCT = sun
JAVAC_MAX_WARNINGS = true
JAVAC_WARNINGS_FATAL = true

include $(BUILDDIR)/common/Defs.gmk

# Re-direct classes to another location (we are building cldrdata.jar)
CLASSDESTDIR = $(TEMPDIR)/classes
CLDRVERSION  = 21.0.1
CLDRSRCDIR = $(SHARE_SRC)/classes/sun/util/cldr/resources/$(subst .,_,$(CLDRVERSION))
CLDRDATA_JAR = $(EXTDIR)/cldrdata.jar
CLDRCONVERTER_JAR = $(BUILDTOOLJARDIR)/cldrconverter.jar

#
# Files
#
CLDRGENSRCDIR = $(GENSRCDIR)/sun/text/resources/cldr \
			$(GENSRCDIR)/sun/util/cldr \
			$(GENSRCDIR)/sun/util/resources/cldr 
MANIFEST_FILE = $(TEMPDIR)/manifest.mf

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

gensrc: $(GENSRCDIR)/sun/util/cldr/CLDRLocaleDataMetaInfo.java

$(GENSRCDIR)/sun/util/cldr/CLDRLocaleDataMetaInfo.java: \
		$(wildcard $(CLDRSRCDIR)/common/dtd/*.dtd) \
		$(wildcard $(CLDRSRCDIR)/common/main/*.xml) \
		$(wildcard $(CLDRSRCDIR)/common/supplemental/*.xml)
	$(prep-target)
	@$(BOOT_JAVA_CMD) -jar $(CLDRCONVERTER_JAR) \
		-base $(CLDRSRCDIR) \
		-o $(GENSRCDIR)
	@$(java-vm-cleanup)

$(CLDRDATA_JAR): $(EXTDIR) $(GENSRCDIR)/sun/util/cldr/CLDRLocaleDataMetaInfo.java $(MANIFEST_FILE)
	$(prep-target)
	$(MKDIR) -p $(CLASSDESTDIR)
	$(shell \
	    for dir in $(GENSRCDIR); do \
	        if [ -d $$dir ] ; then \
	            ( $(CD) $$dir; \
	                for sdir in $(CLDRGENSRCDIR); do \
	                    if [ -d $$sdir ] ; then \
	                        $(FIND) $$sdir \
	                            -name '*.java' -print >> $(JAVA_SOURCE_LIST) ; \
	                    fi ; \
	                done \
	            ); \
	        fi; \
	    done \
	)
	$(JAVAC_CMD) -sourcepath "$(SOURCEPATH)" -d $(CLASSDESTDIR) @$(JAVA_SOURCE_LIST)
	$(BOOT_JAR_CMD) -cmf $(MANIFEST_FILE) $@ -C $(CLASSDESTDIR) sun \
	     $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

$(MANIFEST_FILE):
	@$(prep-target)
	$(ECHO) "CLDR-Version: $(CLDRVERSION)" > $@

build: gensrc $(CLDRDATA_JAR)

clean clobber::
	$(RM) -r $(CLASSDESTDIR) 
	$(RM) -r $(GENSRCDIR)/sun/text/resources/cldr
	$(RM) -r $(GENSRCDIR)/sun/util/cldr
	$(RM) -r $(GENSRCDIR)/sun/util/resources/cldr
	$(RM) $(MANIFEST_FILE)
	$(RM) $(CLDRDATA_JAR)

.PHONY: gensrc
