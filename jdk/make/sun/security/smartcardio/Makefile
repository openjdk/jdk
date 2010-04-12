#
# Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

BUILDDIR = ../../..
MODULE  = security-smartcardio
PACKAGE = sun.security.smartcardio
LIBRARY = j2pcsc
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# C and Java Files
#
include FILES_c.gmk

AUTO_FILES_JAVA_DIRS = sun/security/smartcardio

#
# Java files that define native methods
#
FILES_export = \
    sun/security/smartcardio/PCSC.java \
    sun/security/smartcardio/PlatformPCSC.java

#
# Find native code
#
vpath %.c \
  $(SHARE_SRC)/native/sun/security/smartcardio \
  $(PLATFORM_SRC)/native/sun/security/smartcardio

#
# Find include files
#
OTHER_INCLUDES += \
  -I$(SHARE_SRC)/native/sun/security/smartcardio \
  -I$(PLATFORM_SRC)/native/sun/security/smartcardio \
  -I$(PLATFORM_SRC)/native/sun/security/smartcardio/MUSCLE

#
# Rules
#
#CLASSDESTDIR = $(TMPDIR)/classes

#FILES_class = $(FILES_java:%.java=$(CLASSDESTDIR)/%.class)

include $(BUILDDIR)/common/Mapfile-vers.gmk

include $(BUILDDIR)/common/Library.gmk

#
# Libraries to link
#
ifeq ($(PLATFORM), windows)
  OTHER_LDLIBS = $(JVMLIB) winscard.lib
else
  OTHER_LDLIBS = -ldl $(JVMLIB)
  OTHER_CFLAGS = -D__sun_jdk
endif
