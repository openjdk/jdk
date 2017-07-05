#
# Copyright 2004-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for building jconsole
#

BUILDDIR = ../..
PACKAGE = sun.tools.jconsole
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

# This program must contain a manifest that defines the execution level
# needed to follow standard Vista User Access Control Guidelines
# This must be set before Program.gmk is included
#
BUILD_MANIFEST=true


JARFILE = $(LIBDIR)/jconsole.jar

OTHER_JAVACFLAGS += -encoding iso8859-1

#
# Files
#
include FILES.gmk

AUTO_FILES_JAVA_DIRS =  sun/tools/jconsole \
			com/sun/tools/jconsole

FILES_png = $(PNG_FILES:%.png=$(CLASSBINDIR)/%.png)
FILES_gif = $(GIF_FILES:%.gif=$(CLASSBINDIR)/%.gif)

build: $(GENSRCDIR)/sun/tools/jconsole/Version.java

#
# Resources
#
LOCALE_SET_DEFINITION = jdk
RESOURCE_BUNDLES_JAVA = $(PKGDIR)/resources/JConsoleResources.java

#
# Rules.
#
include $(BUILDDIR)/common/Classes.gmk

build: $(FILES_png) $(FILES_gif) $(TEMPDIR)/manifest $(JARFILE)

$(GENSRCDIR)/sun/tools/jconsole/Version.java: \
                $(SHARE_SRC)/classes/sun/tools/jconsole/Version.java.template
	$(MKDIR) -p $(@D)
	$(SED) -e 's/@@jconsole_version@@/$(FULL_VERSION)/g' $< > $@

$(CLASSBINDIR)/$(PKGDIR)/resources/%: $(SHARE_SRC)/classes/$(PKGDIR)/resources/%
	@$(MKDIR) -p $(@D)
	$(install-file)

$(TEMPDIR)/manifest: $(SHARE_SRC)/classes/$(PKGDIR)/manifest
	$(install-file)

#
# Extra rule to build jconsole.jar
#

$(JARFILE): $(LIBDIR) $(FILES_class) $(FILES_png) $(FILES_gif) $(TEMPDIR)/manifest
	$(BOOT_JAR_CMD) -cfm $(JARFILE) $(TEMPDIR)/manifest \
               -C $(CLASSBINDIR) sun/tools/jconsole \
               -C $(CLASSBINDIR) com/sun/tools/jconsole \
		    $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

clean clobber::
	$(RM) $(TEMPDIR)/manifest $(JARFILE)

