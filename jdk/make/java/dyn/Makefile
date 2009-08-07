#
# Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR = ../..

PACKAGE = java.dyn
PRODUCT = java
include $(BUILDDIR)/common/Defs.gmk

AUTO_FILES_JAVA_DIRS = java/dyn sun/dyn

# The sources built here use new language syntax to generate
# method handle calls.  Let's be sure we are using that format.
LANGUAGE_VERSION = -source 7
CLASS_VERSION = -target 7

# Actually, it will be less disruptive to compile with the same
# -target option as the rest of the system, and just turn on
# the specific compiler option we need here:
OTHER_JAVACFLAGS = -XDinvokedynamic

include $(BUILDDIR)/common/Classes.gmk
