/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

#include <windef.h>
#include <winsock.h>

#include "hpi_impl.h"

#include "mutex_md.h"

struct sockaddr;

#define FN_RECV           0
#define FN_SEND           1
#define FN_LISTEN         2
#define FN_BIND           3
#define FN_ACCEPT         4
#define FN_RECVFROM       5
#define FN_SENDTO         6
#define FN_SELECT         7
#define FN_CONNECT        8
#define FN_CLOSESOCKET    9
#define FN_SHUTDOWN       10
#define FN_GETHOSTNAME    11
#define FN_GETHOSTBYADDR  12
#define FN_GETHOSTBYNAME  13
#define FN_HTONS          14
#define FN_HTONL          15
#define FN_NTOHS          16
#define FN_NTOHL          17
#define FN_GETSOCKOPT     18
#define FN_SETSOCKOPT     19
#define FN_GETPROTOBYNAME 20
#define FN_GETSOCKNAME    21
#define FN_SOCKET         22
#define FN_WSASENDDISCONNECT 23
#define FN_SOCKETAVAILABLE 24

static int (PASCAL FAR *sockfnptrs[])() =
    {NULL, NULL, NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, NULL,
     };

static bool_t sockfnptrs_initialized = FALSE;
static mutex_t sockFnTableMutex;

/* is Winsock2 loaded? better to be explicit than to rely on sockfnptrs */
static bool_t winsock2Available = FALSE;

/* Winsock2 options at the IPPROTO_IP level
   We need the following translation in order to deal with the multiple
   definitions for IPPROTO_IP level options in different winsock versions.

in                         winsock.h vs. ws2tcpip.h
#define IP_OPTIONS         1             1
#define IP_MULTICAST_IF    2             9
#define IP_MULTICAST_TTL   3             10
#define IP_MULTICAST_LOOP  4             11
#define IP_ADD_MEMBERSHIP  5             12
#define IP_DROP_MEMBERSHIP 6             13
#define IP_TTL             7             4
#define IP_TOS             8             3
#define IP_DONTFRAGMENT    9             14
*/
static int IPPROTO_OPTIONS[] = {-1, 1, 9, 10, 11, 12, 13, 4, 3, 14};

/* IMPORTANT: whenever possible, we want to use Winsock2 (ws2_32.dll)
 * instead of Winsock (wsock32.dll). Other than the fact that it is
 * newer, less buggy and faster than Winsock, Winsock2 lets us to work
 * around the following problem:
 *
 * Generally speaking, it is important to shutdown a socket before
 * closing it, since failing to do so can sometimes result in a TCP
 * RST (abortive close) which is disturbing to the peer of the
 * connection.
 *
 * The Winsock way to shutdown a socket is the Berkeley call
 * shutdown(). We do not want to call it on Win95, since it
 * sporadically leads to an OS crash in IFS_MGR.VXD.  Complete hull
 * breach.  Blue screen.  Ugly.
 *
 * So, in initSockTable we look for Winsock 2, and if we find it we
 * assign wsassendisconnectfn function pointer. When we close, we
 * first check to see if it's bound, and if it is, we call it. Winsock
 * 2 will always be there on NT, and we recommend that win95 user
 * install it.
 *
 * - br 10/11/97
 */

static void
initSockFnTable() {
    int (PASCAL FAR* WSAStartupPtr)(WORD, LPWSADATA);
    WSADATA wsadata;
    OSVERSIONINFO info;

    mutexInit(&sockFnTableMutex);
    mutexLock(&sockFnTableMutex);
    if (sockfnptrs_initialized == FALSE) {
        HANDLE hWinsock;

        /* try to load Winsock2, and if that fails, load Winsock */
        hWinsock = LoadLibrary("ws2_32.dll");
        if (hWinsock == NULL) {
            hWinsock = LoadLibrary("wsock32.dll");
            winsock2Available = FALSE;
        } else {
            winsock2Available = TRUE;
        }

        if (hWinsock == NULL) {
            VM_CALL(jio_fprintf)(stderr, "Could not load Winsock 1 or 2 (error: %d)\n",
                        GetLastError());
        }

        /* If we loaded a DLL, then we might as well initialize it.  */
        WSAStartupPtr = (int (PASCAL FAR *)(WORD, LPWSADATA))
                            GetProcAddress(hWinsock, "WSAStartup");
        if (WSAStartupPtr(MAKEWORD(1,1), &wsadata) != 0) {
            VM_CALL(jio_fprintf)(stderr, "Could not initialize Winsock\n");
        }

        sockfnptrs[FN_RECV]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "recv");
        sockfnptrs[FN_SEND]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "send");
        sockfnptrs[FN_LISTEN]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "listen");
        sockfnptrs[FN_BIND]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "bind");
        sockfnptrs[FN_ACCEPT]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "accept");
        sockfnptrs[FN_RECVFROM]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "recvfrom");
        sockfnptrs[FN_SENDTO]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "sendto");
        sockfnptrs[FN_SELECT]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "select");
        sockfnptrs[FN_CONNECT]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "connect");
        sockfnptrs[FN_CLOSESOCKET]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "closesocket");
        /* we don't use this */
        sockfnptrs[FN_SHUTDOWN]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "shutdown");
        sockfnptrs[FN_GETHOSTNAME]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "gethostname");
        sockfnptrs[FN_GETHOSTBYADDR]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "gethostbyaddr");
        sockfnptrs[FN_GETHOSTBYNAME]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "gethostbyname");
        sockfnptrs[FN_HTONS]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "htons");
        sockfnptrs[FN_HTONL]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "htonl");
        sockfnptrs[FN_NTOHS]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "ntohs");
        sockfnptrs[FN_NTOHL]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "ntohl");
        sockfnptrs[FN_GETSOCKOPT]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "getsockopt");
        sockfnptrs[FN_SETSOCKOPT]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "setsockopt");
        sockfnptrs[FN_GETPROTOBYNAME]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "getprotobyname");
        sockfnptrs[FN_GETSOCKNAME]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "getsockname");

        sockfnptrs[FN_SOCKET]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock, "socket");
        /* in winsock 1, this will simply be 0 */
        sockfnptrs[FN_WSASENDDISCONNECT]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock,
                                                   "WSASendDisconnect");
        sockfnptrs[FN_SOCKETAVAILABLE]
            = (int (PASCAL FAR *)())GetProcAddress(hWinsock,
                                                   "ioctlsocket");
    }

    sysAssert(sockfnptrs[FN_RECV] != NULL);
    sysAssert(sockfnptrs[FN_SEND] != NULL);
    sysAssert(sockfnptrs[FN_LISTEN] != NULL);
    sysAssert(sockfnptrs[FN_BIND] != NULL);
    sysAssert(sockfnptrs[FN_ACCEPT] != NULL);
    sysAssert(sockfnptrs[FN_RECVFROM] != NULL);
    sysAssert(sockfnptrs[FN_SENDTO] != NULL);
    sysAssert(sockfnptrs[FN_SELECT] != NULL);
    sysAssert(sockfnptrs[FN_CONNECT] != NULL);
    sysAssert(sockfnptrs[FN_CLOSESOCKET] != NULL);
    sysAssert(sockfnptrs[FN_SHUTDOWN] != NULL);
    sysAssert(sockfnptrs[FN_GETHOSTNAME] != NULL);
    sysAssert(sockfnptrs[FN_GETHOSTBYADDR] != NULL);
    sysAssert(sockfnptrs[FN_GETHOSTBYNAME] != NULL);
    sysAssert(sockfnptrs[FN_HTONS] != NULL);
    sysAssert(sockfnptrs[FN_HTONL] != NULL);
    sysAssert(sockfnptrs[FN_NTOHS] != NULL);
    sysAssert(sockfnptrs[FN_NTOHL] != NULL);
    sysAssert(sockfnptrs[FN_GETSOCKOPT] != NULL);
    sysAssert(sockfnptrs[FN_SETSOCKOPT] != NULL);
    sysAssert(sockfnptrs[FN_GETPROTOBYNAME] != NULL);
    sysAssert(sockfnptrs[FN_GETSOCKNAME] != NULL);
    sysAssert(sockfnptrs[FN_SOCKET] != NULL);

    if (winsock2Available) {
        sysAssert(sockfnptrs[FN_WSASENDDISCONNECT] != NULL);
    }

    sysAssert(sockfnptrs[FN_SOCKETAVAILABLE] != NULL);

    sockfnptrs_initialized = TRUE;
    mutexUnlock(&sockFnTableMutex);
}

/*
 * If we get a nonnull function pointer it might still be the case
 * that some other thread is in the process of initializing the socket
 * function pointer table, but our pointer should still be good.
 */
int
sysListen(int fd, int count) {
    int (PASCAL FAR *listenfn)();
    if ((listenfn = sockfnptrs[FN_LISTEN]) == NULL) {
        initSockFnTable();
        listenfn = sockfnptrs[FN_LISTEN];
    }
    sysAssert(sockfnptrs_initialized == TRUE && listenfn != NULL);
    return (*listenfn)(fd, (long)count);
}

int
sysConnect(int fd, struct sockaddr *name, int namelen) {
    int (PASCAL FAR *connectfn)();
    if ((connectfn = sockfnptrs[FN_CONNECT]) == NULL) {
        initSockFnTable();
        connectfn = sockfnptrs[FN_CONNECT];
    }
    sysAssert(sockfnptrs_initialized == TRUE);
    sysAssert(connectfn != NULL);
    return (*connectfn)(fd, name, namelen);
}

int
sysBind(int fd, struct sockaddr *name, int namelen) {
    int (PASCAL FAR *bindfn)();
    if ((bindfn = sockfnptrs[FN_BIND]) == NULL) {
        initSockFnTable();
        bindfn = sockfnptrs[FN_BIND];
    }
    sysAssert(sockfnptrs_initialized == TRUE);
    sysAssert(bindfn != NULL);
    return (*bindfn)(fd, name, namelen);
}

int
sysAccept(int fd, struct sockaddr *name, int *namelen) {
    int (PASCAL FAR *acceptfn)();
    if ((acceptfn = sockfnptrs[FN_ACCEPT]) == NULL) {
        initSockFnTable();
        acceptfn = sockfnptrs[FN_ACCEPT];
    }
    sysAssert(sockfnptrs_initialized == TRUE && acceptfn != NULL);
    return (*acceptfn)(fd, name, namelen);
}

int
sysRecvFrom(int fd, char *buf, int nBytes,
                  int flags, struct sockaddr *from, int *fromlen) {
    int (PASCAL FAR *recvfromfn)();
    if ((recvfromfn = sockfnptrs[FN_RECVFROM]) == NULL) {
        initSockFnTable();
        recvfromfn = sockfnptrs[FN_RECVFROM];
    }
    sysAssert(sockfnptrs_initialized == TRUE && recvfromfn != NULL);
    return (*recvfromfn)(fd, buf, nBytes, flags, from, fromlen);
}

int
sysSendTo(int fd, char *buf, int len,
                int flags, struct sockaddr *to, int tolen) {
    int (PASCAL FAR *sendtofn)();
    if ((sendtofn = sockfnptrs[FN_SENDTO]) == NULL) {
        initSockFnTable();
        sendtofn = sockfnptrs[FN_SENDTO];
    }
    sysAssert(sockfnptrs_initialized == TRUE && sendtofn != NULL);
    return (*sendtofn)(fd, buf, len, flags, to, tolen);
}

int
sysRecv(int fd, char *buf, int nBytes, int flags) {
    int (PASCAL FAR *recvfn)();
    if ((recvfn = sockfnptrs[FN_RECV]) == NULL) {
        initSockFnTable();
        recvfn = sockfnptrs[FN_RECV];
    }
    sysAssert(sockfnptrs_initialized == TRUE && recvfn != NULL);
    return (*recvfn)(fd, buf, nBytes, flags);
}

int
sysSend(int fd, char *buf, int nBytes, int flags) {
    int (PASCAL FAR *sendfn)();
    if ((sendfn = sockfnptrs[FN_SEND]) == NULL) {
        initSockFnTable();
        sendfn = sockfnptrs[FN_SEND];
    }
    sysAssert(sockfnptrs_initialized == TRUE && sendfn != NULL);
    return (*sendfn)(fd, buf, nBytes, flags);
}


int
sysGetHostName(char *hostname, int namelen) {
    int (PASCAL FAR *fn)();
    if ((fn = sockfnptrs[FN_GETHOSTNAME]) == NULL) {
        initSockFnTable();
        fn = sockfnptrs[FN_GETHOSTNAME];
    }
    sysAssert(sockfnptrs_initialized == TRUE && fn != NULL);
    return (*fn)(hostname, namelen);
}

struct hostent *
sysGetHostByAddr(const char *hostname, int len, int type) {
    struct hostent * (PASCAL FAR *fn)();
    if ((fn = (struct hostent * (PASCAL FAR *)()) sockfnptrs[FN_GETHOSTBYADDR]) == NULL) {
        initSockFnTable();
        fn = (struct hostent * (PASCAL FAR *)()) sockfnptrs[FN_GETHOSTBYADDR];
    }
    sysAssert(sockfnptrs_initialized == TRUE && fn != NULL);
    return (*fn)(hostname, len, type);
}

struct hostent *
sysGetHostByName(char *hostname) {
    struct hostent * (PASCAL FAR *fn)();
    if ((fn = (struct hostent * (PASCAL FAR *)()) sockfnptrs[FN_GETHOSTBYNAME]) == NULL) {
        initSockFnTable();
        fn = (struct hostent * (PASCAL FAR *)()) sockfnptrs[FN_GETHOSTBYNAME];
    }
    sysAssert(sockfnptrs_initialized == TRUE && fn != NULL);
    return (*fn)(hostname);
}

int
sysSocket(int domain, int type, int protocol) {
    int sock;
    int (PASCAL FAR *socketfn)();
    if ((socketfn = sockfnptrs[FN_SOCKET]) == NULL) {
        initSockFnTable();
        socketfn = sockfnptrs[FN_SOCKET];
    }
    sysAssert(sockfnptrs_initialized == TRUE && socketfn != NULL);
    sock = (*socketfn)(domain, type, protocol);
    if (sock != INVALID_SOCKET) {
        SetHandleInformation((HANDLE)(uintptr_t)sock, HANDLE_FLAG_INHERIT, FALSE);
    }
    return sock;
}

int sysSocketShutdown(int fd, int how)  {
    if (fd > 0) {
        int (PASCAL FAR *shutdownfn)();
        if ((shutdownfn = sockfnptrs[FN_SHUTDOWN]) == NULL) {
            initSockFnTable();
            shutdownfn = sockfnptrs[FN_SHUTDOWN];
        }
        /* At this point we are guaranteed the sockfnptrs are initialized */
        sysAssert(sockfnptrs_initialized == TRUE && shutdownfn != NULL);
        (void) (*shutdownfn)(fd, how);
   }
return TRUE;
}

/*
 * This function is carefully designed to work around a bug in Windows
 * 95's networking winsock. Please see the beginning of this file for
 * a complete description of the problem.
 */
int sysSocketClose(int fd) {

    if (fd > 0) {
        int (PASCAL FAR *closesocketfn)();
        int (PASCAL FAR *wsasenddisconnectfn)();
        int dynamic_ref = -1;

        if ((closesocketfn = sockfnptrs[FN_CLOSESOCKET]) == NULL) {
            initSockFnTable();
        }
        /* At this point we are guaranteed the sockfnptrs are initialized */
        sysAssert(sockfnptrs_initialized == TRUE);

        closesocketfn = sockfnptrs[FN_CLOSESOCKET];
        sysAssert(closesocketfn != NULL);

        if (winsock2Available) {
            struct linger l;
            int len = sizeof(l);

            if (sysGetSockOpt(fd, SOL_SOCKET, SO_LINGER, (char *)&l, &len) == 0) {
                if (l.l_onoff == 0) {
                    wsasenddisconnectfn = sockfnptrs[FN_WSASENDDISCONNECT];
                    (*wsasenddisconnectfn)(fd, NULL);
                }
            }
        }
        (void) (*closesocketfn)(fd);
    }
    return TRUE;
}

/*
 * Poll the fd for reading for timeout ms.  Returns 1 if something's
 * ready, 0 if it timed out, -1 on error, -2 if interrupted (although
 * interruption isn't implemented yet).  Timeout in milliseconds.  */
int
sysTimeout(int fd, long timeout) {
    int res;
    fd_set tbl;
    struct timeval t;
    int (PASCAL FAR *selectfn)();

    t.tv_sec = timeout / 1000;
    t.tv_usec = (timeout % 1000) * 1000;
    FD_ZERO(&tbl);
    FD_SET(fd, &tbl);

    if ((selectfn = sockfnptrs[FN_SELECT]) == NULL) {
        initSockFnTable();
        selectfn = sockfnptrs[FN_SELECT];
    }
    sysAssert(sockfnptrs_initialized == TRUE && selectfn != NULL);
    res = (*selectfn)(fd + 1, &tbl, 0, 0, &t);
    return res;
}

long
sysSocketAvailable(int fd, jint *pbytes)
{
    int (PASCAL FAR *socketfn)();
    if ((socketfn = sockfnptrs[FN_SOCKETAVAILABLE]) == NULL) {
        initSockFnTable();
        socketfn = sockfnptrs[FN_SOCKETAVAILABLE];
    }
    sysAssert(sockfnptrs_initialized == TRUE && socketfn != NULL);
    return (*socketfn)(fd, FIONREAD, pbytes);
}

int
sysGetSockName(int fd, struct sockaddr *name, int *namelen) {
    int (PASCAL FAR *getsocknamefn)();
    if ((getsocknamefn = sockfnptrs[FN_GETSOCKNAME]) == NULL) {
        initSockFnTable();
        getsocknamefn = sockfnptrs[FN_GETSOCKNAME];
    }
    sysAssert(sockfnptrs_initialized == TRUE);
    sysAssert(getsocknamefn != NULL);
    return (*getsocknamefn)(fd, name, namelen);
}

int
sysGetSockOpt(int fd, int level, int optname, char *optval, int *optlen ) {
    int (PASCAL FAR *getsockoptfn)();
    if ((getsockoptfn = sockfnptrs[FN_GETSOCKOPT]) == NULL) {
        initSockFnTable();
        getsockoptfn = sockfnptrs[FN_GETSOCKOPT];
    }
    sysAssert(sockfnptrs_initialized == TRUE);
    sysAssert(getsockoptfn != NULL);

    /* We need the following translation in order to deal with the multiple
       definitions for IPPROTO_IP level options in different winsock versions
       */
    if (winsock2Available && level == IPPROTO_IP &&
        optname >= IP_OPTIONS && optname <= IP_DONTFRAGMENT) {
      optname = IPPROTO_OPTIONS[optname];
    }
    return (*getsockoptfn)(fd, level, optname, optval, optlen);
}

int
sysSetSockOpt(int fd, int level, int optname, const char *optval, int optlen ) {
    int (PASCAL FAR *setsockoptfn)();
    if ((setsockoptfn = sockfnptrs[FN_SETSOCKOPT]) == NULL) {
        initSockFnTable();
        setsockoptfn = sockfnptrs[FN_SETSOCKOPT];
    }
    sysAssert(sockfnptrs_initialized == TRUE);
    sysAssert(setsockoptfn != NULL);

    /* We need the following translation in order to deal with the multiple
       definitions for IPPROTO_IP level options in different winsock versions
       */
    if (winsock2Available && level == IPPROTO_IP &&
        optname >= IP_OPTIONS && optname <= IP_DONTFRAGMENT) {
      optname = IPPROTO_OPTIONS[optname];
    }

    return (*setsockoptfn)(fd, level, optname, optval, optlen);
}

struct protoent *
sysGetProtoByName(char *name) {
    struct protoent * (PASCAL FAR *getprotobynamefn)();
    if ((getprotobynamefn = (struct protoent * (PASCAL FAR *)()) sockfnptrs[FN_GETPROTOBYNAME]) == NULL) {
        initSockFnTable();
        getprotobynamefn = (struct protoent * (PASCAL FAR *)()) sockfnptrs[FN_GETPROTOBYNAME];
    }
    sysAssert(sockfnptrs_initialized == TRUE);
    sysAssert(getprotobynamefn != NULL);
    return (*getprotobynamefn)(name);
}
