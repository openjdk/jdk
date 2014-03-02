/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef NET_UTILS_MD_H
#define NET_UTILS_MD_H

#include <sys/socket.h>
#include <sys/types.h>
#include <netdb.h>
#include <netinet/in.h>
#include <unistd.h>

#include <sys/poll.h>

int NET_Timeout(JNIEnv *env, int s, long timeout);
int NET_Read(int s, void* buf, size_t len);
int NET_RecvFrom(int s, void *buf, int len, unsigned int flags,
                 struct sockaddr *from, socklen_t *fromlen);
int NET_ReadV(int s, const struct iovec * vector, int count);
int NET_Send(int s, void *msg, int len, unsigned int flags);
int NET_SendTo(int s, const void *msg, int len,  unsigned  int
               flags, const struct sockaddr *to, int tolen);
int NET_Writev(int s, const struct iovec * vector, int count);
int NET_Connect(int s, struct sockaddr *addr, int addrlen);
int NET_Accept(int s, struct sockaddr *addr, socklen_t *addrlen);
int NET_SocketClose(int s);
int NET_Dup2(int oldfd, int newfd);
int NET_Poll(struct pollfd *ufds, unsigned int nfds, int timeout);
int NET_SocketAvailable(int s, jint *pbytes);

void NET_ThrowUnknownHostExceptionWithGaiError(JNIEnv *env,
                                               const char* hostname,
                                               int gai_error);
void NET_ThrowByNameWithLastError(JNIEnv *env, const char *name,
                                  const char *defaultDetail);

#define NET_WAIT_READ    0x01
#define NET_WAIT_WRITE   0x02
#define NET_WAIT_CONNECT 0x04

jint NET_Wait(JNIEnv *env, jint fd, jint flags, jint timeout);

/************************************************************************
 * Macros and constants
 */

/*
 * On 64-bit JDKs we use a much larger stack and heap buffer.
 */
#ifdef _LP64
#define MAX_BUFFER_LEN 65536
#define MAX_HEAP_BUFFER_LEN 131072
#else
#define MAX_BUFFER_LEN 8192
#define MAX_HEAP_BUFFER_LEN 65536
#endif

#ifdef AF_INET6

#define SOCKADDR        union { \
                            struct sockaddr_in him4; \
                            struct sockaddr_in6 him6; \
                        }

#define SOCKADDR_LEN    (ipv6_available() ? sizeof(SOCKADDR) : \
                         sizeof(struct sockaddr_in))

#else

#define SOCKADDR        union { struct sockaddr_in him4; }
#define SOCKADDR_LEN    sizeof(SOCKADDR)

#endif

/************************************************************************
 *  Utilities
 */

#ifdef __linux__
int kernelIsV24();
#ifdef AF_INET6
int getDefaultIPv6Interface(struct in6_addr *target_addr);
#endif
#endif

#ifdef __solaris__
int net_getParam(char *driver, char *param);
#endif

#endif /* NET_UTILS_MD_H */
