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

BUILDDIR = ../../..
PACKAGE = sun.security.other
PRODUCT = sun
JAVAC_MAX_WARNINGS=true
JAVAC_LINT_OPTIONS=-Xlint:all,-deprecation
JAVAC_WARNINGS_FATAL=true
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
AUTO_FILES_JAVA_DIRS = \
    sun/security/acl \
    sun/security/jca \
    sun/security/pkcs \
    sun/security/pkcs10 \
    sun/security/pkcs12 \
    sun/security/provider \
    sun/security/rsa \
    sun/security/ssl \
    sun/security/ssl/krb5 \
    sun/security/timestamp \
    sun/security/validator \
    sun/security/x509 \
    com/sun/net/ssl/internal/ssl

#
# EC classes used by the packages above
#
FILES_java += \
    sun/security/ec/ECParameters.java \
    sun/security/ec/ECPrivateKeyImpl.java \
    sun/security/ec/ECPublicKeyImpl.java \
    sun/security/ec/NamedCurve.java

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk
