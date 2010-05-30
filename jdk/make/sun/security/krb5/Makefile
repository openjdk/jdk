#
# Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../../..
MODULE  = security-kerberos
PACKAGE = sun.security.krb5
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Java Files
#
AUTO_FILES_JAVA_DIRS = sun/security/krb5

ifeq ($(PLATFORM), windows)
#
# Java files that define native methods
#
FILES_export = \
	sun/security/krb5/Credentials.java \
	sun/security/krb5/Config.java
include FILES_c_windows.gmk
# We need some extra libs for win32
LIBRARY = w2k_lsa_auth
EXTRA_LIBS += Secur32.lib netapi32.lib\
 kernel32.lib user32.lib gdi32.lib winspool.lib comdlg32.lib\
 advapi32.lib shell32.lib ole32.lib oleaut32.lib uuid.lib odbc32.lib\
 odbccp32.lib wsock32.lib
endif # PLATFORM

#
# Find native code
#
vpath %.c \
  $(PLATFORM_SRC)/native/sun/security/krb5

JGSS_NATIVE_SRC=$(PLATFORM_SRC)/native/sun/security/krb5
JGSS_NATIVE_DIR_EXISTS := $(shell if [ -d $(JGSS_NATIVE_SRC) ] ; then echo true; else echo false; fi)

#
# Rules
#
ifeq ($(PLATFORM), windows)
  ifeq ($(JGSS_NATIVE_DIR_EXISTS), true)
    include $(BUILDDIR)/common/Library.gmk
  endif
else
include $(BUILDDIR)/common/Classes.gmk
endif # PLATFORM

#
# Libraries to link
#
ifeq ($(PLATFORM), windows)
  OTHER_LDLIBS = $(JVMLIB)
else
  OTHER_LDLIBS = -ldl $(JVMLIB)
endif

build:
ifeq ($(PLATFORM),windows)
	$(call make-launcher, kinit, sun.security.krb5.internal.tools.Kinit, , )
	$(call make-launcher, klist, sun.security.krb5.internal.tools.Klist, , )
	$(call make-launcher, ktab, sun.security.krb5.internal.tools.Ktab, , )
endif
