#
# Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building a ResourceBundleControlProvider jar file for testing.
#
#    Usage: make JDK_HOME=... all install
#

DESTDIR = ..
TMPDIR = tmp
SERVICESDIR = $(TMPDIR)/META-INF/services
TARGETJAR = rbcontrolprovider.jar
BINDIR = $(JDK_HOME)/bin


all: $(TARGETJAR)

install: all
	cp $(TARGETJAR) $(DESTDIR)

SERVICES = java.util.spi.ResourceBundleControlProvider

FILES_JAVA = UserControlProvider.java \
             UserXMLControl.java

RESOURCE_FILES = XmlRB.xml \
                 XmlRB_ja.xml

$(TARGETJAR): $(SERVICES) $(FILES_JAVA) $(RESOURCE_FILES)
	rm -rf $(TMPDIR) $@
	mkdir -p $(SERVICESDIR)
	$(BINDIR)/javac -d $(TMPDIR) $(FILES_JAVA)
	cp $(SERVICES) $(SERVICESDIR)
	cp $(RESOURCE_FILES) $(TMPDIR)/com/foo
	$(BINDIR)/jar  cvf $@ -C $(TMPDIR) .

clean:
	rm -rf $(TMPDIR) $(TARGETJAR)

.PHONY: all install clean
