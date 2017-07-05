#
# Copyright 1996-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
PACKAGE = sun.security.other
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
AUTO_FILES_JAVA_DIRS = \
    sun/security/acl \
    sun/security/jca \
    sun/security/pkcs \
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
    sun/security/ec/ECKeyFactory.java \
    sun/security/ec/ECParameters.java \
    sun/security/ec/ECPrivateKeyImpl.java \
    sun/security/ec/ECPublicKeyImpl.java \
    sun/security/ec/NamedCurve.java

#
# Rules
#
include $(BUILDDIR)/common/Classes.gmk
