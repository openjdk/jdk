#
# Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

PACKAGE = com.sun.corba.se.impl.corespi
PRODUCT = sun

include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
CORBA_JMK_DIRECTORY=$(TOPDIR)/make/com/sun/corba/minclude/
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_ior.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_oa.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_orb.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_protocol.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_resolver.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_transport.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_orbutil.jmk
include $(CORBA_JMK_DIRECTORY)com_sun_corba_se_spi_presentation_rmi.jmk

#
# Include
#

FILES_java =    $(com_sun_corba_se_spi_ior_java) \
                $(com_sun_corba_se_spi_oa_java) \
                $(com_sun_corba_se_spi_orb_java) \
                $(com_sun_corba_se_spi_protocol_java) \
                $(com_sun_corba_se_spi_resolver_java) \
                $(com_sun_corba_se_spi_transport_java) \
                $(com_sun_corba_se_spi_orbutil_java) \
                $(com_sun_corba_se_spi_presentation_rmi_java)

include $(BUILDDIR)/common/Classes.gmk
