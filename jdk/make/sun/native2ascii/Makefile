#
# Copyright (c) 1996, 2011, Oracle and/or its affiliates. All rights reserved.
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
# Makefile for building native2ascii
#

BUILDDIR = ../..
PACKAGE = sun.tools.native2ascii
PRODUCT = sun
OTHER_JAVACFLAGS += -Xlint:serial -Werror
include $(BUILDDIR)/common/Defs.gmk

# This program must contain a manifest that defines the execution level
# needed to follow standard Vista User Access Control Guidelines
# This must be set before Program.gmk is included
#
BUILD_MANIFEST=true


#
# Files
#
FILES_java = \
	sun/tools/native2ascii/Main.java \
        sun/tools/native2ascii/A2NFilter.java \
        sun/tools/native2ascii/N2AFilter.java

#
# Resources
#
LOCALE_SET_DEFINITION = jdk
RESOURCE_BUNDLES_JAVA = sun/tools/native2ascii/resources/MsgNative2ascii.java

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk
