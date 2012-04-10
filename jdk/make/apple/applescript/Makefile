#
# Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../..
PACKAGE = apple.applescript
LIBRARY = AppleScriptEngine
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk


#
# Files
#
AUTO_FILES_JAVA_DIRS = apple/applescript

FILES_objc = \
        $(TARGDIR)AppleScriptEngine.m \
        $(TARGDIR)AppleScriptExecutionContext.m \
        $(TARGDIR)AS_NS_ConversionUtils.m \
        $(TARGDIR)NS_Java_ConversionUtils.m


FILES_export = \
	apple/applescript/AppleScriptEngine.java \
	apple/applescript/AppleScriptEngineFactory.java

#
# Rules
#
include $(BUILDDIR)/common/Mapfile-vers.gmk
include $(BUILDDIR)/common/Library.gmk


#
# Extra rules
#

#
# Add to ambient vpath to get files in a subdirectory
#
vpath %.m   $(call NativeSrcDirList,,native/apple/applescript)

CPPFLAGS += \
	-I$(TEMPDIR)/../../sun.awt/awt/CClassHeaders

OTHER_LDLIBS = \
    -framework Cocoa \
    -framework Carbon \
    -framework JavaNativeFoundation
