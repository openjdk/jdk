#
# Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
# Install a wrapper for the "rmid" command. The associated classfiles
# are built at a higher level.
#

BUILDDIR = ../../..
MODULE  = rmi-activation
PACKAGE = sun.rmi.activation
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

build: stubs rmid

#
# Resources
#
LOCALE_SET_DEFINITION = jre
RESOURCE_BUNDLES_UNCOMPILED_PROPERTIES = sun/rmi/server/resources/rmid.properties

#
# Extra dependencies.
#
classes: stubs

#
# Rules to build activation-system stubs
#

# Full package names of implementations requiring stubs
REMOTE_impls = \
	sun.rmi.server.Activation$$ActivationSystemImpl \
	java.rmi.activation.ActivationGroup

REMOTE_files = $(subst .,/,$(REMOTE_impls))
FILES_stubs = $(REMOTE_files:%=$(CLASSBINDIR)/%_Stub.class)

#
# Compile stubs for remote implementations
# (use -v1.2 stubs for activation system)
#

$(CLASSBINDIR)/%_Stub.class: $(CLASSBINDIR)/%.class
	$(RMIC) -v1.2 -classpath $(CLASSBINDIR)	\
	    	-d $(CLASSBINDIR) '$(subst /,.,$(<:$(CLASSBINDIR)/%.class=%))'
	@$(java-vm-cleanup)

stubs: $(FILES_stubs)

#
# Rules to
#
include $(BUILDDIR)/common/Classes.gmk

rmid:
	$(call make-launcher, rmid, sun.rmi.server.Activation, , )
