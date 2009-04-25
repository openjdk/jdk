#
# Copyright 1998-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for building the Nimbus generator
#

BUILDDIR = ../..
PACKAGE = org.jdesktop.synthdesigner.generator
PRODUCT = tools
PROGRAM = nimbus_generator
include $(BUILDDIR)/common/Defs.gmk

BUILDTOOL_SOURCE_ROOT = classes
BUILDTOOL_MAIN        = $(PKGDIR)/Generator.java

#
# Files
#

MAIN_CLASS_FILE = $(BUILDTOOLCLASSDIR)/$(BUILDTOOL_MAIN:%.java=%.class)
SOURCE_FILES    = $(shell $(FIND) $(BUILDTOOL_SOURCE_ROOT) -name '*.java' -print)

TEMPLATE_FILES  = $(SHARE_SRC)/classes/javax/swing/plaf/nimbus/*.template
TEMPLATE_DEST   = $(BUILDTOOLCLASSDIR)/org/jdesktop/synthdesigner/generator/resources

JIBX_FILES      = $(BUILDTOOL_SOURCE_ROOT)/org/jdesktop/swingx/designer/Designer.jibx.xml \
                  $(BUILDTOOL_SOURCE_ROOT)/org/jdesktop/synthdesigner/synthmodel/SynthModel.jibx.xml
JIBX_LIBS_CP    = $(JIBX_LIBS_PATH)/bcel.jar$(CLASSPATH_SEPARATOR)$(JIBX_LIBS_PATH)/xpp3.jar$(CLASSPATH_SEPARATOR)$(JIBX_LIBS_PATH)/jibx-bind.jar$(CLASSPATH_SEPARATOR)$(JIBX_LIBS_PATH)/jibx-run.jar
JIBX_LIBS_LIST  = $(subst $(CLASSPATH_SEPARATOR), ,$(JIBX_LIBS_CP))


#
# Rules
#

include $(BUILDDIR)/common/BuildToolJar.gmk

$(MAIN_CLASS_FILE): $(SOURCE_FILES) $(JIBX_LIBS_LIST)
	@$(MKDIR) -p $(BUILDTOOLCLASSDIR)
	$(BOOT_JAVAC_CMD) -classpath "$(JIBX_LIBS_CP)" \
	    -d $(BUILDTOOLCLASSDIR) -sourcepath $(BUILDTOOL_SOURCE_ROOT) \
	    $(SOURCE_FILES)

$(TEMPLATE_DEST): $(TEMPLATE_FILES)
	$(MKDIR) -p $(TEMPLATE_DEST)
	$(RM) $(TEMPLATE_DEST)/*.template
	$(CP) $(TEMPLATE_FILES) $(TEMPLATE_DEST)

$(BUILDTOOL_MANIFEST_FILE): $(MAIN_CLASS_FILE)
	$(ECHO) "Main-Class: $(BUILTTOOL_MAINCLASS)" > $@
	$(ECHO) "Class-Path: $(JIBX_LIBS_LIST:$(JIBX_LIBS_PATH)/%=%)" >> $@
	$(CP) $(JIBX_LIBS_LIST) $(BUILDTOOLJARDIR)

$(BUILDTOOL_JAR_FILE): $(MAIN_CLASS_FILE) $(TEMPLATE_DEST) \
    $(JIBX_FILES) $(BUILDTOOL_MANIFEST_FILE)
	@$(prep-target)
	$(BOOT_JAVA_CMD) \
	  -classpath "$(JIBX_LIBS_CP)$(CLASSPATH_SEPARATOR)$(BUILDTOOLCLASSDIR)" \
	  org.jibx.binding.Compile $(JIBX_FILES)
	$(BOOT_JAR_CMD) cfm $@ $(BUILDTOOL_MANIFEST_FILE) \
	    -C $(BUILDTOOLCLASSDIR) org \
	    $(BOOT_JAR_JFLAGS) || $(RM) $@
	@$(java-vm-cleanup)

clean clobber::
	$(RM) -r $(TEMPLATE_DEST)
