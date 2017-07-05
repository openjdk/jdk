#
# Copyright 1997-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
# Makefile for building the old java compiler.
#

BUILDDIR = ../../..
MODULE   = oldjavac
PRODUCT	 = oldjavac

include $(BUILDDIR)/common/Defs.gmk

#
# Files to compile.
#
include FILES_java.gmk

#
# Resources
#
LOCALE_SET_DEFINITION = j2sdk
RESOURCE_BUNDLES_UNCOMPILED_PROPERTIES = sun/tools/javac/resources/javac.properties

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk

clean clobber::
	$(RM) -r $(CLASSBINDIR)/sun/tools/asm
	$(RM) -r $(CLASSBINDIR)/sun/tools/java
	$(RM) -r $(CLASSBINDIR)/sun/tools/javac
	$(RM) -r $(CLASSBINDIR)/sun/tools/tree
	$(RM) -r $(CLASSBINDIR)/sun/tools/util

