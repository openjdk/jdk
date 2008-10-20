/*
 * Copyright 1998-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
#ifndef _JAVASOFT_WIN32_SOCKET_MD_H

#include <jni.h>
#include <sys/types.h>
#include "sys.h"
#include "socket_md.h"

#define DBG_POLLIN              1
#define DBG_POLLOUT             2

#define DBG_EINPROGRESS         -150
#define DBG_ETIMEOUT            -200

int dbgsysSocketClose(int fd);
int dbgsysConnect(int fd, struct sockaddr *him, int len);
int dbgsysFinishConnect(int fd, long timeout);
int dbgsysAccept(int fd, struct sockaddr *him, int *len);
int dbgsysSendTo(int fd, char *buf, int len, int flags, struct sockaddr *to,
              int tolen);
int dbgsysRecvFrom(int fd, char *buf, int nbytes, int flags,
                struct sockaddr *from, int *fromlen);
int dbgsysListen(int fd, int backlog);
int dbgsysRecv(int fd, char *buf, int nBytes, int flags);
int dbgsysSend(int fd, char *buf, int nBytes, int flags);
struct hostent *dbgsysGetHostByName(char *hostname);
int dbgsysSocket(int domain, int type, int protocol);
int dbgsysBind(int fd, struct sockaddr *name, int namelen);
int dbgsysSetSocketOption(int fd, jint cmd, jboolean on, jvalue value);
uint32_t dbgsysInetAddr(const char* cp);
uint32_t dbgsysHostToNetworkLong(uint32_t hostlong);
unsigned short dbgsysHostToNetworkShort(unsigned short hostshort);
uint32_t dbgsysNetworkToHostLong(uint32_t netlong);
unsigned short dbgsysNetworkToHostShort(unsigned short netshort);
int dbgsysGetSocketName(int fd, struct sockaddr *him, int *len);
int dbgsysConfigureBlocking(int fd, jboolean blocking);
int dbgsysPoll(int fd, jboolean rd, jboolean wr, long timeout);
int dbgsysGetLastIOError(char *buf, jint size);
long dbgsysCurrentTimeMillis();

/*
 * TLS support
 */
int dbgsysTlsAlloc();
void dbgsysTlsFree(int index);
void dbgsysTlsPut(int index, void *value);
void* dbgsysTlsGet(int index);

#endif
