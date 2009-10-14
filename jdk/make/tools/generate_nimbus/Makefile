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
PACKAGE = build.tools.generatenimbus
PRODUCT = tools
PROGRAM = generatenimbus
include $(BUILDDIR)/common/Defs.gmk

BUILDTOOL_SOURCE_ROOT = $(BUILDDIR)/tools/src
BUILDTOOL_MAIN        = $(PKGDIR)/Generator.java


#
# Files
#

SOURCE_FILES    = Generator.java \
                  ObjectFactory.java \
                  Paint.java \
                  SynthModel.java \
                  UIDefault.java \
                  UIStyle.java
TEMPLATE_FILES  = Defaults.template \
                  PainterImpl.template \
                  StateImpl.template
TEMPLATE_DEST   = $(BUILDTOOLCLASSDIR)/$(PKGDIR)/resources


#
# Rules
#

all: $(TEMPLATE_DEST) $(BUILDTOOL_JAR_FILE)

include $(BUILDDIR)/common/BuildToolJar.gmk

$(TEMPLATE_DEST): $(TEMPLATE_FILES:%=$(SHARE_SRC)/classes/javax/swing/plaf/nimbus/%)
	$(MKDIR) -p $@
	$(RM) $(TEMPLATE_FILES:%=$@/%)
	$(CP) $^ $@

BUILDTOOL_MAIN_SOURCE_FILE = $(SOURCE_FILES:%=$(BUILDTOOL_SOURCE_ROOT)/$(PKGDIR)/%)

clean clobber::
	$(RM) -r $(TEMPLATE_DEST)
