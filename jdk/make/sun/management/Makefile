#
# Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for building JDK out of the box management support 
#

BUILDDIR = ../..
include $(BUILDDIR)/common/Defs.gmk

MGMT_LIBDIR = $(LIBDIR)/management
MGMT_LIB_SRC = $(SHARE_SRC)/lib/management

all build:: properties aclfile jmxremotefiles

# When building the openjdk, build snmp only if importing binary plugs,
ifdef OPENJDK
  ifeq ($(IMPORT_BINARY_PLUGS),true)
    SUBDIRS = snmp
  endif
else
  SUBDIRS = snmp
endif
SUBDIRS += jmxremote
 
all build clean clobber::
	$(SUBDIRS-loop)

properties: $(MGMT_LIBDIR)/management.properties

aclfile: $(MGMT_LIBDIR)/snmp.acl.template

jmxremotefiles: $(MGMT_LIBDIR)/jmxremote.password.template $(MGMT_LIBDIR)/jmxremote.access

$(MGMT_LIBDIR)/management.properties: $(MGMT_LIB_SRC)/management.properties
	$(install-file)
	$(CHMOD) 644 $@

$(MGMT_LIBDIR)/snmp.acl.template: $(MGMT_LIB_SRC)/snmp.acl.template
	$(install-file)
	$(CHMOD) 444 $@

$(MGMT_LIBDIR)/jmxremote.password.template: $(MGMT_LIB_SRC)/jmxremote.password.template
	$(install-file)
	$(CHMOD) 444 $@

$(MGMT_LIBDIR)/jmxremote.access: $(MGMT_LIB_SRC)/jmxremote.access 
	$(install-file)
	$(CHMOD) 644 $@

