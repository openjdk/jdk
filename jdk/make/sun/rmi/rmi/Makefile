#
# Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building sun.rmi.*
#

BUILDDIR = ../../..
MODULE  = rmi
PACKAGE = sun.rmi
PRODUCT = sun
LIBRARY = rmi
include $(BUILDDIR)/common/Defs.gmk

#
# Add use of a mapfile
#
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Java files to compile.
#
AUTO_FILES_JAVA_DIRS = \
	java/rmi \
	sun/rmi/log \
	sun/rmi/registry \
	sun/rmi/runtime \
	sun/rmi/server \
	sun/rmi/transport \
	com/sun/rmi

#
# Native files to compile.
#
FILES_c = \
	sun/rmi/server/MarshalInputStream.c

#
# Add ambient vpath to pick up files not part of sun.rmi package
#
vpath %.c $(SHARE_SRC)/native/sun/rmi/server

#
# Exported files that require generated .h 
#
FILES_export = \
    sun/rmi/server/MarshalInputStream.java

#
# Link to JVM for JVM_LatestUserDefinedLoader
#
OTHER_LDLIBS = $(JVMLIB)

#
# Rules
#
include $(BUILDDIR)/common/Library.gmk

#
# Full package names of implementations requiring stubs
#
REMOTE_impls = \
	sun.rmi.registry.RegistryImpl \
	sun.rmi.transport.DGCImpl

ifeq ($(PLATFORM), windows)
build: stubs
else # PLATFORM
ifneq ($(ARCH_DATA_MODEL), 32)
build: stubs
else # ARCH_DATA_MODEL
build: stubs bin
endif
endif

clean clobber:: bin.clean


#
# Compile stubs and skeletons for remote implementations
# (use -v1.1 for backward interoperability)
#
#  gnumake 3.78.1 on windows attempts to build the target even
#  though it exists. Not sure why, but a check for file existence
#  has been added...
#
$(CLASSBINDIR)/%_Skel.class $(CLASSBINDIR)/%_Stub.class: $(CLASSBINDIR)/%.class
	if [ ! -s $@ ] ; \
	then $(RMIC) -v1.1 -classpath \
	"$(CLASSBINDIR)" \
	-d $(CLASSBINDIR) '$(subst /,.,$(<:$(CLASSBINDIR)/%.class=%))' ; \
	fi
	@$(java-vm-cleanup)

REMOTE_files = $(subst .,/,$(REMOTE_impls))
FILES_stubs  = $(REMOTE_files:%=$(CLASSBINDIR)/%_Stub.class)
FILES_skels  = $(REMOTE_files:%=$(CLASSBINDIR)/%_Skel.class)

stubs: $(FILES_stubs) $(FILES_skels)

bin: $(BINDIR)/java-rmi.cgi

$(BINDIR)/java-rmi.cgi: $(PLATFORM_SRC)/bin/java-rmi.cgi.sh
	$(install-file)
	$(call chmod-file, a+x)

bin.clean:
	$(RM) $(BINDIR)/java-rmi.cgi

.PHONY: stubs bin bin.clean

