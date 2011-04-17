#
# Copyright (c) 1995, 2011, Oracle and/or its affiliates. All rights reserved.
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
PACKAGE = java.net
LIBRARY = net
PRODUCT = sun
include $(BUILDDIR)/common/Defs.gmk

#
# Files
#
include FILES_c.gmk

AUTO_FILES_JAVA_DIRS = java/net

ifeq ($(PLATFORM), windows)
    FILES_c += NTLMAuthSequence.c
    FILES_c += NetworkInterface_winXP.c
else
    FILES_c += SdpSupport.c
endif

FILES_export = \
    java/net/Socket.java \
    java/net/SocketOptions.java \
    java/net/SocketImpl.java \
    java/net/AbstractPlainSocketImpl.java \
    java/net/PlainSocketImpl.java \
    java/net/ServerSocket.java \
    java/net/InetAddress.java \
    java/net/Inet4Address.java \
    java/net/Inet6Address.java \
    java/net/InetAddressImpl.java \
    java/net/Inet4AddressImpl.java \
    java/net/Inet6AddressImpl.java \
    java/net/NetworkInterface.java \
    java/net/SocketInputStream.java \
    java/net/SocketOutputStream.java \
    java/net/DefaultDatagramSocketImplFactory.java \
    java/net/DatagramPacket.java \
    java/net/DatagramSocket.java \
    java/net/DatagramSocketImpl.java \
    java/net/AbstractPlainDatagramSocketImpl.java \
    java/net/MulticastSocket.java \
    java/net/UnknownHostException.java \
    java/net/ProtocolException.java \
    sun/net/spi/DefaultProxySelector.java

ifeq ($(PLATFORM), windows) 
    FILES_export += java/net/TwoStacksPlainSocketImpl.java
    FILES_export += java/net/DualStackPlainSocketImpl.java
    FILES_export += java/net/TwoStacksPlainDatagramSocketImpl.java
    FILES_export += java/net/DualStackPlainDatagramSocketImpl.java
else
    FILES_export += java/net/PlainDatagramSocketImpl.java
endif

#
# Find platform specific native code
#
vpath %.c $(PLATFORM_SRC)/native/sun/net/dns $(PLATFORM_SRC)/native/sun/net/www/protocol/http/ntlm \
    $(PLATFORM_SRC)/native/sun/net/sdp $(PLATFORM_SRC)/native/sun/net/spi

#
# Include rules
#
include $(BUILDDIR)/common/Mapfile-vers.gmk

include $(BUILDDIR)/common/Library.gmk

ifeq ($(PLATFORM), windows)
  OTHER_LDLIBS = ws2_32.lib $(JVMLIB) \
                 secur32.lib iphlpapi.lib delayimp.lib \
                 /DELAYLOAD:secur32.dll /DELAYLOAD:iphlpapi.dll
else
  OTHER_LDLIBS = $(LIBSOCKET) $(LIBNSL) -ldl $(JVMLIB)
endif
ifeq ($(PLATFORM), linux)
  OTHER_LDLIBS += -lpthread
endif

CLASSES.export += java.lang.Integer java.io.FileDescriptor java.net.InetAddressImplFactory java.net.Inet4AddressImpl java.net.Inet6AddressImpl

#
# Resources
#
LOCALE_SET_DEFINITION = jre

MISC_FILES = $(LIBDIR) $(LIBDIR)/net.properties

$(LIBDIR)/net.properties: $(SHARE_SRC)/lib/net.properties
	$(install-file)

# 
# SDP configuration template
#
ifeq ($(PLATFORM), solaris)
SDP_PATH = sdp/sdp.conf.template
SDP_CONF = $(LIBDIR)/$(SDP_PATH)
$(SDP_CONF): $(PLATFORM_SRC)/lib/$(SDP_PATH)
	@$(RM) $*
	$(install-file)

MISC_FILES += $(SDP_CONF)
endif

build: $(MISC_FILES)

