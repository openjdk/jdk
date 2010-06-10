#
# Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

BUILDDIR = ../../../../..

PACKAGE = com.sun.corba.se.impl.core
PRODUCT = sun

include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
CORBA_JMK_DIRECTORY=$(TOPDIR)/make/com/sun/corba/minclude/
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_corba.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_core.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_dynamicany.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_encoding.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_legacy.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_servicecontext.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_ior.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_orb.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_orbutil.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_protocol.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_resolver.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_transport.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_util.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_io.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_impl_presentation_rmi.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_internal_LegacyFiles.jmk



#
# Include
#

FILES_java =	$(com_sun_corba_se_impl_corba_java) \
		$(com_sun_corba_se_impl_core_java) \
		$(com_sun_corba_se_impl_dynamicany_java) \
		$(com_sun_corba_se_impl_encoding_java) \
		$(com_sun_corba_se_impl_legacy_java) \
		$(com_sun_corba_se_spi_servicecontext_java) \
		$(com_sun_corba_se_impl_ior_java) \
		$(com_sun_corba_se_impl_orb_java) \
		$(com_sun_corba_se_impl_orbutil_java) \
		$(com_sun_corba_se_impl_protocol_java) \
		$(com_sun_corba_se_impl_resolver_java) \
		$(com_sun_corba_se_impl_transport_java) \
		$(com_sun_corba_se_impl_util_java) \
		$(com_sun_corba_se_impl_io_java) \
		$(com_sun_corba_se_impl_presentation_rmi_java) \
		$(com_sun_corba_se_internal_LegacyFiles_java)

include $(BUILDDIR)/common/Classes.gmk
