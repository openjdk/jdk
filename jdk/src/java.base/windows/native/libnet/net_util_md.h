/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

/* typedefs that were defined correctly for the first time
 * in Nov. 2001 SDK, which we need to include here.
 * Specifically, in6_addr and sockaddr_in6 (which is defined but
 * not correctly). When moving to a later SDK remove following
 * code between START and END
 */

/* --- START --- */

/* WIN64 already uses newer SDK */
#ifdef _WIN64

#define SOCKADDR_IN6 sockaddr_in6

#else

#ifdef _MSC_VER
#define WS2TCPIP_INLINE __inline
#else
#define WS2TCPIP_INLINE extern inline /* GNU style */
#endif

#if defined(_MSC_VER) && _MSC_VER >= 1310

#define SOCKADDR_IN6 sockaddr_in6

#else

/*SO_REUSEPORT is not supported on Windows, define it to 0*/
#define SO_REUSEPORT 0

/* Retain this code a little longer to support building in
 * old environments.  _MSC_VER is defined as:
 *     1200 for MSVC++ 6.0
 *     1310 for Vc7
 */

#define IPPROTO_IPV6    41
#define IPV6_MULTICAST_IF 9

struct in6_addr {
    union {
        u_char Byte[16];
        u_short Word[8];
    } u;
};

/*
** Defines to match RFC 2553.
*/
#define _S6_un     u
#define _S6_u8     Byte
#define s6_addr    _S6_un._S6_u8

/*
** Defines for our implementation.
*/
#define s6_bytes   u.Byte
#define s6_words   u.Word

/* IPv6 socket address structure, RFC 2553 */

struct SOCKADDR_IN6 {
        short   sin6_family;    /* AF_INET6 */
        u_short sin6_port;      /* Transport level port number */
        u_long  sin6_flowinfo;  /* IPv6 flow information */
        struct in6_addr sin6_addr; /* IPv6 address */
        u_long sin6_scope_id;  /* set of interfaces for a scope */
};


/* Error codes from getaddrinfo() */

#define EAI_AGAIN       WSATRY_AGAIN
#define EAI_BADFLAGS    WSAEINVAL
#define EAI_FAIL        WSANO_RECOVERY
#define EAI_FAMILY      WSAEAFNOSUPPORT
#define EAI_MEMORY      WSA_NOT_ENOUGH_MEMORY
//#define EAI_NODATA      WSANO_DATA
#define EAI_NONAME      WSAHOST_NOT_FOUND
#define EAI_SERVICE     WSATYPE_NOT_FOUND
#define EAI_SOCKTYPE    WSAESOCKTNOSUPPORT

#define EAI_NODATA      EAI_NONAME

/* Structure used in getaddrinfo() call */

typedef struct addrinfo {
    int ai_flags;              /* AI_PASSIVE, AI_CANONNAME, AI_NUMERICHOST */
    int ai_family;             /* PF_xxx */
    int ai_socktype;           /* SOCK_xxx */
    int ai_protocol;           /* 0 or IPPROTO_xxx for IPv4 and IPv6 */
    size_t ai_addrlen;         /* Length of ai_addr */
    char *ai_canonname;        /* Canonical name for nodename */
    struct sockaddr *ai_addr;  /* Binary address */
    struct addrinfo *ai_next;  /* Next structure in linked list */
} ADDRINFO, FAR * LPADDRINFO;

/* Flags used in "hints" argument to getaddrinfo() */

#define AI_PASSIVE     0x1  /* Socket address will be used in bind() call */
#define AI_CANONNAME   0x2  /* Return canonical name in first ai_canonname */
#define AI_NUMERICHOST 0x4  /* Nodename must be a numeric address string */

/* IPv6 Multicasting definitions */

/* Argument structure for IPV6_JOIN_GROUP and IPV6_LEAVE_GROUP */

typedef struct ipv6_mreq {
    struct in6_addr ipv6mr_multiaddr;  /* IPv6 multicast address */
    unsigned int    ipv6mr_interface;  /* Interface index */
} IPV6_MREQ;

#define IPV6_ADD_MEMBERSHIP     12 /* Add an IP group membership */
#define IPV6_DROP_MEMBERSHIP    13 /* Drop an IP group membership */
#define IPV6_MULTICAST_LOOP     11 /* Set/get IP multicast loopback */

WS2TCPIP_INLINE int
IN6_IS_ADDR_MULTICAST(const struct in6_addr *a)
{
    return (a->s6_bytes[0] == 0xff);
}

WS2TCPIP_INLINE int
IN6_IS_ADDR_LINKLOCAL(const struct in6_addr *a)
{
    return (a->s6_bytes[0] == 0xfe
            && a->s6_bytes[1] == 0x80);
}

#define NI_MAXHOST  1025  /* Max size of a fully-qualified domain name */
#define NI_MAXSERV    32  /* Max size of a service name */

#define INET_ADDRSTRLEN  16 /* Max size of numeric form of IPv4 address */
#define INET6_ADDRSTRLEN 46 /* Max size of numeric form of IPv6 address */

/* Flags for getnameinfo() */

#define NI_NOFQDN       0x01  /* Only return nodename portion for local hosts */
#define NI_NUMERICHOST  0x02  /* Return numeric form of the host's address */
#define NI_NAMEREQD     0x04  /* Error if the host's name not in DNS */
#define NI_NUMERICSERV  0x08  /* Return numeric form of the service (port #) */
#define NI_DGRAM        0x10  /* Service is a datagram service */


#define IN6_IS_ADDR_V4MAPPED(a) \
    (((a)->s6_words[0] == 0) && ((a)->s6_words[1] == 0) &&      \
    ((a)->s6_words[2] == 0) && ((a)->s6_words[3] == 0) &&       \
    ((a)->s6_words[4] == 0) && ((a)->s6_words[5] == 0xffff))


/* --- END --- */
#endif /* end 'else older build environment' */

#endif

#if !INCL_WINSOCK_API_TYPEDEFS

typedef
int
(WSAAPI * LPFN_GETADDRINFO)(
    IN const char FAR * nodename,
    IN const char FAR * servname,
    IN const struct addrinfo FAR * hints,
    OUT struct addrinfo FAR * FAR * res
    );

typedef
void
(WSAAPI * LPFN_FREEADDRINFO)(
    IN struct addrinfo FAR * ai
    );

typedef
int
(WSAAPI * LPFN_GETNAMEINFO)(
    IN  const struct sockaddr FAR * sa,
    IN  int             salen,
    OUT char FAR *      host,
    IN  DWORD           hostlen,
    OUT char FAR *      serv,
    IN  DWORD           servlen,
    IN  int             flags
    );
#endif

/* used to disable connection reset messages on Windows XP */
#ifndef SIO_UDP_CONNRESET
#define SIO_UDP_CONNRESET _WSAIOW(IOC_VENDOR,12)
#endif

#ifndef IN6_IS_ADDR_ANY
#define IN6_IS_ADDR_ANY(a)      \
    (((a)->s6_words[0] == 0) && ((a)->s6_words[1] == 0) &&      \
    ((a)->s6_words[2] == 0) && ((a)->s6_words[3] == 0) &&       \
    ((a)->s6_words[4] == 0) && ((a)->s6_words[5] == 0) &&       \
    ((a)->s6_words[6] == 0) && ((a)->s6_words[7] == 0))
#endif

#ifndef IPV6_V6ONLY
#define IPV6_V6ONLY     27 /* Treat wildcard bind as AF_INET6-only. */
#endif

#include "java_io_FileDescriptor.h"
#include "java_net_SocketOptions.h"

#define MAX_BUFFER_LEN          2048
#define MAX_HEAP_BUFFER_LEN     65536


/* true if SO_RCVTIMEO is supported by underlying provider */
extern jboolean isRcvTimeoutSupported;

void NET_ThrowCurrent(JNIEnv *env, char *msg);

/*
 * Return default Type Of Service
 */
int NET_GetDefaultTOS(void);

typedef union {
    struct sockaddr     him;
    struct sockaddr_in  him4;
    struct SOCKADDR_IN6 him6;
} SOCKETADDRESS;

/*
 * passed to NET_BindV6. Both ipv4_fd and ipv6_fd must be created and unbound
 * sockets. On return they may refer to different sockets.
 */
struct ipv6bind {
    SOCKETADDRESS       *addr;
    SOCKET               ipv4_fd;
    SOCKET               ipv6_fd;
};

#define SOCKETADDRESS_LEN(X)    \
        (((X)->him.sa_family==AF_INET6)? sizeof(struct SOCKADDR_IN6) : \
                         sizeof(struct sockaddr_in))

#define SOCKETADDRESS_COPY(DST,SRC) {                           \
    if ((SRC)->sa_family == AF_INET6) {                         \
        memcpy ((DST), (SRC), sizeof (struct SOCKADDR_IN6));    \
    } else {                                                    \
        memcpy ((DST), (SRC), sizeof (struct sockaddr_in));     \
    }                                                           \
}

#define SET_PORT(X,Y) {                         \
    if ((X)->him.sa_family == AF_INET) {        \
        (X)->him4.sin_port = (Y);               \
    } else {                                    \
        (X)->him6.sin6_port = (Y);              \
    }                                           \
}

#define GET_PORT(X) ((X)->him.sa_family==AF_INET ?(X)->him4.sin_port: (X)->him6.sin6_port)

#define IS_LOOPBACK_ADDRESS(x) ( \
    ((x)->him.sa_family == AF_INET) ? \
        (ntohl((x)->him4.sin_addr.s_addr)==INADDR_LOOPBACK) : \
        (IN6ADDR_ISLOOPBACK (x)) \
)

JNIEXPORT int JNICALL NET_SocketClose(int fd);

JNIEXPORT int JNICALL NET_Timeout(int fd, long timeout);

int NET_Socket(int domain, int type, int protocol);

void NET_ThrowByNameWithLastError(JNIEnv *env, const char *name,
         const char *defaultDetail);

void NET_ThrowSocketException(JNIEnv *env, char* msg);

/*
 * differs from NET_Timeout() as follows:
 *
 * If timeout = -1, it blocks forever.
 *
 * returns 1 or 2 depending if only one or both sockets
 * fire at same time.
 *
 * *fdret is (one of) the active fds. If both sockets
 * fire at same time, *fd == fd always.
 */
JNIEXPORT int JNICALL NET_Timeout2(int fd, int fd1, long timeout, int *fdret);

JNIEXPORT int JNICALL NET_BindV6(struct ipv6bind* b, jboolean exclBind);

#define NET_WAIT_READ   0x01
#define NET_WAIT_WRITE  0x02
#define NET_WAIT_CONNECT        0x04

extern jint NET_Wait(JNIEnv *env, jint fd, jint flags, jint timeout);

JNIEXPORT int JNICALL NET_WinBind(int s, struct sockaddr *him, int len,
                                   jboolean exclBind);

/* XP versions of the native routines */

JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByName0_XP
    (JNIEnv *env, jclass cls, jstring name);

JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByIndex0_XP
  (JNIEnv *env, jclass cls, jint index);

JNIEXPORT jobject JNICALL Java_java_net_NetworkInterface_getByInetAddress0_XP
    (JNIEnv *env, jclass cls, jobject iaObj);

JNIEXPORT jobjectArray JNICALL Java_java_net_NetworkInterface_getAll_XP
    (JNIEnv *env, jclass cls);

JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_supportsMulticast0_XP
(JNIEnv *env, jclass cls, jstring name, jint index);

JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isUp0_XP
(JNIEnv *env, jclass cls, jstring name, jint index);

JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isP2P0_XP
(JNIEnv *env, jclass cls, jstring name, jint index);

JNIEXPORT jbyteArray JNICALL Java_java_net_NetworkInterface_getMacAddr0_XP
(JNIEnv *env, jclass cls, jstring name, jint index);

JNIEXPORT jint JNICALL Java_java_net_NetworkInterface_getMTU0_XP
(JNIEnv *env, jclass class, jstring name, jint index);

JNIEXPORT jboolean JNICALL Java_java_net_NetworkInterface_isLoopback0_XP
(JNIEnv *env, jclass cls, jstring name, jint index);

