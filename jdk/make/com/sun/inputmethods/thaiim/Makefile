#
# Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for thai input method.
#

BUILDDIR = ../../../..

PACKAGE = com.sun.inputmethods.internal.thaiim
PRODUCT = sun

CLASSDESTDIR = $(TEMPDIR)/classes
SERVICESDIR=META-INF/services
IMJAR = $(IMJARDIR)/thaiim.jar
IMJARDIR=$(LIBDIR)/im
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
AUTO_FILES_JAVA_DIRS = com/sun/inputmethods/internal/thaiim

FILES_copy = \
	$(CLASSDESTDIR)/com/sun/inputmethods/internal/thaiim/resources/DisplayNames.properties

PROVIDER_CONF_FILE = \
	$(CLASSDESTDIR)/$(SERVICESDIR)/java.awt.im.spi.InputMethodDescriptor

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

build: $(IMJAR)

#
# Extra rules to build im.
#

$(CLASSDESTDIR)/com/sun/inputmethods/internal/thaiim/resources/% : $(SHARE_SRC)/classes/com/sun/inputmethods/internal/thaiim/resources/%
	$(install-file)

$(CLASSDESTDIR)/$(SERVICESDIR)/java.awt.im.spi.InputMethodDescriptor : $(SHARE_SRC)/classes/com/sun/inputmethods/internal/thaiim/java.awt.im.spi.InputMethodDescriptor
	$(install-file)

$(IMJAR): $(FILES_class) $(FILES_copy) $(PROVIDER_CONF_FILE)
	$(prep-target)
	$(BOOT_JAR_CMD) -cf $@ \
	     -C $(CLASSDESTDIR) com \
	     -C $(CLASSDESTDIR) $(SERVICESDIR) \
	     $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

clean::
	$(RM) -r $(CLASSDESTDIR)
	$(RM) $(IMJAR)
