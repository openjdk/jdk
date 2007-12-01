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
# Makefile for building RMI/IIOP
#

BUILDDIR = ../../..
PACKAGE = com.sun.corba.se.internal.io
PRODUCT = sun
LIBRARY = ioser12
include $(BUILDDIR)/common/Defs.gmk

#
# Use mapfile
#
FILES_m = mapfile-vers
include $(BUILDDIR)/common/Mapfile-vers.gmk

#
# Files to compile.
#
CORBA_JMK_DIRECTORY=$(TOPDIR)/make/com/sun/corba/minclude/
include $(CORBA_JMK_DIRECTORY)javax_rmi.jmk
include $(CORBA_JMK_DIRECTORY)javax_rmi_CORBA.jmk
include $(CORBA_JMK_DIRECTORY)javax_transaction.jmk
include $(CORBA_JMK_DIRECTORY)javax_activity.jmk
include $(CORBA_JMK_DIRECTORY)ioser_io.jmk
include $(CORBA_JMK_DIRECTORY)sun_corba.jmk

ifdef STANDALONE_CORBA_WS
# FIXUP: What is this all about?
OTHER_LDFLAGS=-L$(BOOTDIR)/jre/lib/$(ARCH) -L$(BOOTDIR)/jre/lib/$(LIBARCH)/native_threads -ljvm
OTHER_INCLUDES+=-ICClassHeaders -I$(BOOTDIR)/include -I$(BOOTDIR)/include/$(PLATFORM)
else
OTHER_LDLIBS=$(JVMLIB)
OTHER_INCLUDES+=-ICClassHeaders -I$(BOOTDIR)/include -I$(BOOTDIR)/include/$(PLATFORM)
endif


FILES_c = ioser.c

FILES_java = \
	$(javax_rmi_java) \
	$(javax_rmi_CORBA_java) \
	$(javax_transaction_java) \
	$(javax_activity_java) \
	$(IOSER_IO_java) \
	$(sun_corba_java)

#
# Generate header files for.
#
FILES_export = \
	com/sun/corba/se/internal/io/IIOPInputStream.java \
	com/sun/corba/se/internal/io/IIOPOutputStream.java \
	com/sun/corba/se/internal/io/ObjectStreamClass.java \
	com/sun/corba/se/internal/io/LibraryManager.java 
#
# Resources
#
LOCALE_SET_DEFINITION = jre
RESOURCE_BUNDLES_PROPERTIES = \
      com/sun/corba/se/impl/orbutil/resources/sunorb.properties

#
# Rules
#
include $(BUILDDIR)/common/Library.gmk

#
# Extra clean rules because we build more than one package.
#
clean:: classheaders.clean objects.clean
	$(RM) -r $(CLASSBINDIR)/javax/rmi
	$(RM) -r $(CLASSBINDIR)/javax/transaction
	$(RM) -r $(CLASSBINDIR)/javax/activity
	$(RM) -r $(CLASSBINDIR)/com/sun/corba/se/impl

