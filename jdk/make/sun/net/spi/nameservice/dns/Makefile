#
# Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for building JNDI DNS name service provider
#

BUILDDIR = ../../../../..

# dns should probably be its own module
MODULE  = net-dns
PACKAGE = sun.net.spi.nameservice.dns
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk
JARFILE = $(EXTDIR)/dnsns.jar

#
# Files to compile
#
FILES_java = \
	sun/net/spi/nameservice/dns/DNSNameServiceDescriptor.java \
        sun/net/spi/nameservice/dns/DNSNameService.java

#
# Rules
#
CLASSDESTDIR = $(TEMPDIR)/classes

FILES_class = $(FILES_java:%.java=$(CLASSDESTDIR)/%.class)


all : build $(JARFILE)

#
# META-INF/services
#
SERVICE_DESCRIPTION = sun.net.spi.nameservice.NameServiceDescriptor

$(CLASSDESTDIR)/META-INF/services/$(SERVICE_DESCRIPTION): \
  $(SHARE_SRC)/classes/$(PKGDIR)/META-INF/services/$(SERVICE_DESCRIPTION)
	$(install-file)

#
# JAR file
#
$(JARFILE): $(CLASSDESTDIR)/META-INF/services/$(SERVICE_DESCRIPTION) \
	$(FILES_class)
	$(BOOT_JAR_CMD) -cf $(JARFILE) \
	     -C $(CLASSDESTDIR) sun \
	     -C $(CLASSDESTDIR) META-INF \
	     $(BOOT_JAR_JFLAGS)
	@$(java-vm-cleanup)

include $(BUILDDIR)/common/Classes.gmk

clean clobber::
	$(RM) -r $(CLASSDESTDIR) \
		 $(EXTDIR)/$(JARFILE)
