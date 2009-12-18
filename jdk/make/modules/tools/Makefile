#
# Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for building the classanalyzer tool
#

BUILDDIR = ../..
PACKAGE = com.sun.classanalyzer
PRODUCT = tools
PROGRAM = classanalyzer
include $(BUILDDIR)/common/Defs.gmk

BUILDTOOL_SOURCE_ROOT = src
BUILDTOOL_MAIN        = $(PKGDIR)/ClassAnalyzer.java
BUILTTOOL_MAINCLASS   = $(subst /,.,$(BUILDTOOL_MAIN:%.java=%))

BUILDTOOL_MAIN_SOURCE_FILE = $(BUILDTOOL_SOURCE_ROOT)/$(BUILDTOOL_MAIN)
BUILDTOOL_MANIFEST_FILE    = $(BUILDTOOLCLASSDIR)/$(PROGRAM)_manifest.mf
BUILDTOOL_JAR_FILE         = $(BUILDTOOLJARDIR)/$(PROGRAM).jar

FILES_java := $(shell $(CD) $(BUILDTOOL_SOURCE_ROOT) \
    && $(FIND) $(PKGDIR) $(SCM_DIRS_prune) -o -type f -print)
FILES_class = $(FILES_java:%.java=$(BUILDTOOLCLASSDIR)/%.class)

all build: $(BUILDTOOL_JAR_FILE) tool_info

$(BUILDTOOL_MANIFEST_FILE): $(BUILDTOOL_MAIN_SOURCE_FILE)
	@$(prep-target)
	$(ECHO) "Main-Class: $(BUILTTOOL_MAINCLASS)" > $@

$(BUILDTOOLCLASSDIR)/%.class : $(BUILDTOOL_SOURCE_ROOT)/%.java
	@$(prep-target)
	$(JAVAC_CMD) \
            -sourcepath $(BUILDTOOL_SOURCE_ROOT) \
            -d $(BUILDTOOLCLASSDIR) $<

$(BUILDTOOL_JAR_FILE): $(BUILDTOOL_MANIFEST_FILE) $(FILES_class)
	@$(prep-target)
	$(BOOT_JAR_CMD) cfm $@ $(BUILDTOOL_MANIFEST_FILE) \
	    -C $(BUILDTOOLCLASSDIR) $(PKGDIR) \
	    $(BOOT_JAR_JFLAGS) || $(RM) $@
	$(java-vm-cleanup)

# Printing out a build tool information line
define printBuildToolSetting
if [ "$2" != "" ] ; then $(PRINTF) "%-25s %s\n" "$1:" "$2"; fi
endef

# Print out the build tool information
tool_info:
	@$(ECHO) "========================================================="
	@$(call printBuildToolSetting,BUILDTOOL,$(PROGRAM))
	@$(call printBuildToolSetting,PACKAGE,$(PACKAGE))
	@$(call printBuildToolSetting,BUILDTOOL_SOURCE_ROOT,$(BUILDTOOL_SOURCE_ROOT))
	@$(call printBuildToolSetting,BUILTTOOL_MAINCLASS,$(BUILTTOOL_MAINCLASS))
	@$(call printBuildToolSetting,BUILDTOOL_JAR_FILE,$(BUILDTOOL_JAR_FILE))
	@$(ECHO) "========================================================="

clean clobber::
	@$(RM) -rf $(BUILDTOOLCLASSDIR)/$(PKGDIR)
	@$(RM) $(BUILDTOOL_MANIFEST_FILE)
	@$(RM) $(BUILDTOOL_JAR_FILE)
