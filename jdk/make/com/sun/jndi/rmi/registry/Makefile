#
# Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building JNDI RMI registry service provider
#

BUILDDIR = ../../../../..
MODULE  = jndi-rmiregistry
PACKAGE = com.sun.jndi.rmi.registry
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files to compile
#
AUTO_FILES_JAVA_DIRS = com/sun/jndi/rmi/registry com/sun/jndi/url/rmi

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

build: stubs

#
# The remainder of this makefile contains defs and rules
# for generating RMI stubs.
# With inspiration from build/solaris/sun/rmi/rmi/GNUmakefile.
#

#
# Full class names of implementations requiring stubs
#
REMOTE_impls = \
	$(PACKAGE).ReferenceWrapper

REMOTE_files = $(subst .,/,$(REMOTE_impls))
FILES_stubs  = $(REMOTE_files:%=$(CLASSDESTDIR)/%_Stub.class)
FILES_skels  = $(REMOTE_files:%=$(CLASSDESTDIR)/%_Skel.class)

#
# Compile stubs and skeletons for remote implementations
#
$(CLASSDESTDIR)/%_Skel.class $(CLASSDESTDIR)/%_Stub.class:	\
		$(CLASSDESTDIR)/%.class
	$(RMIC) -classpath "$(CLASSDESTDIR)"	\
		-d $(CLASSDESTDIR) 				\
		$(subst /,.,$(<:$(CLASSDESTDIR)/%.class=%))
	@$(java-vm-cleanup)

stubs: $(FILES_stubs) $(FILES_skels)
