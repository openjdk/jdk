/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include <winsock2.h>
#include <WS2tcpip.h>

typedef union {
    struct sockaddr     sa;
    struct sockaddr_in  sa4;
    struct sockaddr_in6 sa6;
} SOCKETADDRESS;

/**
 * With dual socket implementation the
 * IPv4 addresseses might be mapped as IPv6.
 * The IPv4 loopback adapter address ranges (127.0.0.0 through 127.255.255.255) will
 * be mapped as the following IPv6 ::ffff:127.0.0.0 through ::ffff:127.255.255.255.
 * For example, this is done by NET_InetAddressToSockaddr.
 */
#define IN6_IS_ADDR_V4MAPPED_LOOPBACK(x) ( \
    (((x)->s6_words[0] == 0)               &&  \
     ((x)->s6_words[1] == 0)               &&  \
     ((x)->s6_words[2] == 0)               &&  \
     ((x)->s6_words[3] == 0)               &&  \
     ((x)->s6_words[4] == 0)               &&  \
     ((x)->s6_words[5] == 0xFFFF)          &&  \
     (((x)->s6_words[6] & 0x00FF) == 0x007F)) \
)

/**
 * Check for IPv4 loopback adapter address ranges (127.0.0.0 through 127.255.255.255)
 */
#define IN4_IS_ADDR_NETLONG_LOOPBACK(l) ( \
    ((l & 0xFF000000) == 0x7F000000) \
)

#define IS_LOOPBACK_ADDRESS(x) ( \
    ((x)->sa.sa_family == AF_INET) ? \
        (IN4_IS_ADDR_NETLONG_LOOPBACK(ntohl((x)->sa4.sin_addr.s_addr))) : \
        ((IN6_IS_ADDR_LOOPBACK(&(x)->sa6.sin6_addr)) || \
         (IN6_IS_ADDR_V4MAPPED_LOOPBACK(&(x)->sa6.sin6_addr))) \
)

int NET_Socket(int domain, int type, int protocol);

void NET_ThrowByNameWithLastError(JNIEnv *env, const char *name,
                                  const char *defaultDetail);

JNIEXPORT int JNICALL NET_WinBind(int s, SOCKETADDRESS *sa, int len,
                                  jboolean exclBind);

JNIEXPORT jint JNICALL NET_EnableFastTcpLoopbackConnect(int fd);

