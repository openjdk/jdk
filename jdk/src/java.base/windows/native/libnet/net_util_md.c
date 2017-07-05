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
#include <ws2tcpip.h>

#include "net_util.h"
#include "jni.h"

// Taken from mstcpip.h in Windows SDK 8.0 or newer.
#define SIO_LOOPBACK_FAST_PATH              _WSAIOW(IOC_VENDOR,16)

#ifndef IPTOS_TOS_MASK
#define IPTOS_TOS_MASK 0x1e
#endif
#ifndef IPTOS_PREC_MASK
#define IPTOS_PREC_MASK 0xe0
#endif

/* true if SO_RCVTIMEO is supported */
jboolean isRcvTimeoutSupported = JNI_TRUE;

/*
 * Table of Windows Sockets errors, the specific exception we
 * throw for the error, and the error text.
 *
 * Note that this table excludes OS dependent errors.
 *
 * Latest list of Windows Sockets errors can be found at :-
 * http://msdn.microsoft.com/library/psdk/winsock/errors_3wc2.htm
 */
static struct {
    int errCode;
    const char *exc;
    const char *errString;
} const winsock_errors[] = {
    { WSAEACCES,                0,      "Permission denied" },
    { WSAEADDRINUSE,            "BindException",        "Address already in use" },
    { WSAEADDRNOTAVAIL,         "BindException",        "Cannot assign requested address" },
    { WSAEAFNOSUPPORT,          0,      "Address family not supported by protocol family" },
    { WSAEALREADY,              0,      "Operation already in progress" },
    { WSAECONNABORTED,          0,      "Software caused connection abort" },
    { WSAECONNREFUSED,          "ConnectException",     "Connection refused" },
    { WSAECONNRESET,            0,      "Connection reset by peer" },
    { WSAEDESTADDRREQ,          0,      "Destination address required" },
    { WSAEFAULT,                0,      "Bad address" },
    { WSAEHOSTDOWN,             0,      "Host is down" },
    { WSAEHOSTUNREACH,          "NoRouteToHostException",       "No route to host" },
    { WSAEINPROGRESS,           0,      "Operation now in progress" },
    { WSAEINTR,                 0,      "Interrupted function call" },
    { WSAEINVAL,                0,      "Invalid argument" },
    { WSAEISCONN,               0,      "Socket is already connected" },
    { WSAEMFILE,                0,      "Too many open files" },
    { WSAEMSGSIZE,              0,      "The message is larger than the maximum supported by the underlying transport" },
    { WSAENETDOWN,              0,      "Network is down" },
    { WSAENETRESET,             0,      "Network dropped connection on reset" },
    { WSAENETUNREACH,           0,      "Network is unreachable" },
    { WSAENOBUFS,               0,      "No buffer space available (maximum connections reached?)" },
    { WSAENOPROTOOPT,           0,      "Bad protocol option" },
    { WSAENOTCONN,              0,      "Socket is not connected" },
    { WSAENOTSOCK,              0,      "Socket operation on nonsocket" },
    { WSAEOPNOTSUPP,            0,      "Operation not supported" },
    { WSAEPFNOSUPPORT,          0,      "Protocol family not supported" },
    { WSAEPROCLIM,              0,      "Too many processes" },
    { WSAEPROTONOSUPPORT,       0,      "Protocol not supported" },
    { WSAEPROTOTYPE,            0,      "Protocol wrong type for socket" },
    { WSAESHUTDOWN,             0,      "Cannot send after socket shutdown" },
    { WSAESOCKTNOSUPPORT,       0,      "Socket type not supported" },
    { WSAETIMEDOUT,             "ConnectException",     "Connection timed out" },
    { WSATYPE_NOT_FOUND,        0,      "Class type not found" },
    { WSAEWOULDBLOCK,           0,      "Resource temporarily unavailable" },
    { WSAHOST_NOT_FOUND,        0,      "Host not found" },
    { WSA_NOT_ENOUGH_MEMORY,    0,      "Insufficient memory available" },
    { WSANOTINITIALISED,        0,      "Successful WSAStartup not yet performed" },
    { WSANO_DATA,               0,      "Valid name, no data record of requested type" },
    { WSANO_RECOVERY,           0,      "This is a nonrecoverable error" },
    { WSASYSNOTREADY,           0,      "Network subsystem is unavailable" },
    { WSATRY_AGAIN,             0,      "Nonauthoritative host not found" },
    { WSAVERNOTSUPPORTED,       0,      "Winsock.dll version out of range" },
    { WSAEDISCON,               0,      "Graceful shutdown in progress" },
    { WSA_OPERATION_ABORTED,    0,      "Overlapped operation aborted" },
};

/*
 * Initialize Windows Sockets API support
 */
BOOL WINAPI
DllMain(HINSTANCE hinst, DWORD reason, LPVOID reserved)
{
    WSADATA wsadata;

    switch (reason) {
        case DLL_PROCESS_ATTACH:
            if (WSAStartup(MAKEWORD(2,2), &wsadata) != 0) {
                return FALSE;
            }
            break;

        case DLL_PROCESS_DETACH:
            WSACleanup();
            break;

        default:
            break;
    }
    return TRUE;
}

void platformInit() {}
void parseExclusiveBindProperty(JNIEnv *env) {}

/*
 * Since winsock doesn't have the equivalent of strerror(errno)
 * use table to lookup error text for the error.
 */
JNIEXPORT void JNICALL
NET_ThrowNew(JNIEnv *env, int errorNum, char *msg)
{
    int i;
    int table_size = sizeof(winsock_errors) /
                     sizeof(winsock_errors[0]);
    char exc[256];
    char fullMsg[256];
    char *excP = NULL;

    /*
     * If exception already throw then don't overwrite it.
     */
    if ((*env)->ExceptionOccurred(env)) {
        return;
    }

    /*
     * Default message text if not provided
     */
    if (!msg) {
        msg = "no further information";
    }

    /*
     * Check table for known winsock errors
     */
    i=0;
    while (i < table_size) {
        if (errorNum == winsock_errors[i].errCode) {
            break;
        }
        i++;
    }

    /*
     * If found get pick the specific exception and error
     * message corresponding to this error.
     */
    if (i < table_size) {
        excP = (char *)winsock_errors[i].exc;
        jio_snprintf(fullMsg, sizeof(fullMsg), "%s: %s",
                     (char *)winsock_errors[i].errString, msg);
    } else {
        jio_snprintf(fullMsg, sizeof(fullMsg),
                     "Unrecognized Windows Sockets error: %d: %s",
                     errorNum, msg);

    }

    /*
     * Throw SocketException if no specific exception for this
     * error.
     */
    if (excP == NULL) {
        excP = "SocketException";
    }
    sprintf(exc, "%s%s", JNU_JAVANETPKG, excP);
    JNU_ThrowByName(env, exc, fullMsg);
}

void
NET_ThrowCurrent(JNIEnv *env, char *msg)
{
    NET_ThrowNew(env, WSAGetLastError(), msg);
}

void
NET_ThrowSocketException(JNIEnv *env, char* msg)
{
    static jclass cls = NULL;
    if (cls == NULL) {
        cls = (*env)->FindClass(env, "java/net/SocketException");
        CHECK_NULL(cls);
        cls = (*env)->NewGlobalRef(env, cls);
        CHECK_NULL(cls);
    }
    (*env)->ThrowNew(env, cls, msg);
}

void
NET_ThrowByNameWithLastError(JNIEnv *env, const char *name,
                   const char *defaultDetail) {
    char errmsg[255];
    sprintf(errmsg, "errno: %d, error: %s\n", WSAGetLastError(), defaultDetail);
    JNU_ThrowByNameWithLastError(env, name, errmsg);
}

jfieldID
NET_GetFileDescriptorID(JNIEnv *env)
{
    jclass cls = (*env)->FindClass(env, "java/io/FileDescriptor");
    CHECK_NULL_RETURN(cls, NULL);
    return (*env)->GetFieldID(env, cls, "fd", "I");
}

jint  IPv6_supported()
{
    SOCKET s = socket(AF_INET6, SOCK_STREAM, 0) ;
    if (s == INVALID_SOCKET) {
        return JNI_FALSE;
    }
    closesocket(s);

    return JNI_TRUE;
}

/*
 * Return the default TOS value
 */
int NET_GetDefaultTOS() {
    static int default_tos = -1;
    OSVERSIONINFO ver;
    HKEY hKey;
    LONG ret;

    /*
     * If default ToS already determined then return it
     */
    if (default_tos >= 0) {
        return default_tos;
    }

    /*
     * Assume default is "normal service"
     */
    default_tos = 0;

    /*
     * Which OS is this?
     */
    ver.dwOSVersionInfoSize = sizeof(ver);
    GetVersionEx(&ver);

    /*
     * If 2000 or greater then no default ToS in registry
     */
    if (ver.dwPlatformId == VER_PLATFORM_WIN32_NT) {
        if (ver.dwMajorVersion >= 5) {
            return default_tos;
        }
    }

    /*
     * Query the registry to see if a Default ToS has been set.
     * Different registry entry for NT vs 95/98/ME.
     */
    if (ver.dwPlatformId == VER_PLATFORM_WIN32_NT) {
        ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                           "SYSTEM\\CurrentControlSet\\Services\\Tcp\\Parameters",
                           0, KEY_READ, (PHKEY)&hKey);
    } else {
        ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                           "SYSTEM\\CurrentControlSet\\Services\\VxD\\MSTCP\\Parameters",
                           0, KEY_READ, (PHKEY)&hKey);
    }
    if (ret == ERROR_SUCCESS) {
        DWORD dwLen;
        DWORD dwDefaultTOS;
        ULONG ulType;
        dwLen = sizeof(dwDefaultTOS);

        ret = RegQueryValueEx(hKey, "DefaultTOS",  NULL, &ulType,
                             (LPBYTE)&dwDefaultTOS, &dwLen);
        RegCloseKey(hKey);
        if (ret == ERROR_SUCCESS) {
            default_tos = (int)dwDefaultTOS;
        }
    }
    return default_tos;
}

/* call NET_MapSocketOptionV6 for the IPv6 fd only
 * and NET_MapSocketOption for the IPv4 fd
 */
JNIEXPORT int JNICALL
NET_MapSocketOptionV6(jint cmd, int *level, int *optname) {

    switch (cmd) {
        case java_net_SocketOptions_IP_MULTICAST_IF:
        case java_net_SocketOptions_IP_MULTICAST_IF2:
            *level = IPPROTO_IPV6;
            *optname = IPV6_MULTICAST_IF;
            return 0;

        case java_net_SocketOptions_IP_MULTICAST_LOOP:
            *level = IPPROTO_IPV6;
            *optname = IPV6_MULTICAST_LOOP;
            return 0;
    }
    return NET_MapSocketOption (cmd, level, optname);
}

/*
 * Map the Java level socket option to the platform specific
 * level and option name.
 */

JNIEXPORT int JNICALL
NET_MapSocketOption(jint cmd, int *level, int *optname) {

    typedef struct {
        jint cmd;
        int level;
        int optname;
    } sockopts;

    static sockopts opts[] = {
        { java_net_SocketOptions_TCP_NODELAY,   IPPROTO_TCP,    TCP_NODELAY },
        { java_net_SocketOptions_SO_OOBINLINE,  SOL_SOCKET,     SO_OOBINLINE },
        { java_net_SocketOptions_SO_LINGER,     SOL_SOCKET,     SO_LINGER },
        { java_net_SocketOptions_SO_SNDBUF,     SOL_SOCKET,     SO_SNDBUF },
        { java_net_SocketOptions_SO_RCVBUF,     SOL_SOCKET,     SO_RCVBUF },
        { java_net_SocketOptions_SO_KEEPALIVE,  SOL_SOCKET,     SO_KEEPALIVE },
        { java_net_SocketOptions_SO_REUSEADDR,  SOL_SOCKET,     SO_REUSEADDR },
        { java_net_SocketOptions_SO_BROADCAST,  SOL_SOCKET,     SO_BROADCAST },
        { java_net_SocketOptions_IP_MULTICAST_IF,   IPPROTO_IP, IP_MULTICAST_IF },
        { java_net_SocketOptions_IP_MULTICAST_LOOP, IPPROTO_IP, IP_MULTICAST_LOOP },
        { java_net_SocketOptions_IP_TOS,            IPPROTO_IP, IP_TOS },

    };


    int i;

    /*
     * Map the Java level option to the native level
     */
    for (i=0; i<(int)(sizeof(opts) / sizeof(opts[0])); i++) {
        if (cmd == opts[i].cmd) {
            *level = opts[i].level;
            *optname = opts[i].optname;
            return 0;
        }
    }

    /* not found */
    return -1;
}


/*
 * Wrapper for setsockopt dealing with Windows specific issues :-
 *
 * IP_TOS and IP_MULTICAST_LOOP can't be set on some Windows
 * editions.
 *
 * The value for the type-of-service (TOS) needs to be masked
 * to get consistent behaviour with other operating systems.
 */
JNIEXPORT int JNICALL
NET_SetSockOpt(int s, int level, int optname, const void *optval,
               int optlen)
{
    int rv = 0;
    int parg = 0;
    int plen = sizeof(parg);

    if (level == IPPROTO_IP && optname == IP_TOS) {
        int *tos = (int *)optval;
        *tos &= (IPTOS_TOS_MASK | IPTOS_PREC_MASK);
    }

    if (optname == SO_REUSEADDR) {
        /*
         * Do not set SO_REUSEADDE if SO_EXCLUSIVEADDUSE is already set
         */
        rv = NET_GetSockOpt(s, SOL_SOCKET, SO_EXCLUSIVEADDRUSE, (char *)&parg, &plen);
        if (rv == 0 && parg == 1) {
            return rv;
        }
    }

    rv = setsockopt(s, level, optname, optval, optlen);

    if (rv == SOCKET_ERROR) {
        /*
         * IP_TOS & IP_MULTICAST_LOOP can't be set on some versions
         * of Windows.
         */
        if ((WSAGetLastError() == WSAENOPROTOOPT) &&
            (level == IPPROTO_IP) &&
            (optname == IP_TOS || optname == IP_MULTICAST_LOOP)) {
            rv = 0;
        }

        /*
         * IP_TOS can't be set on unbound UDP sockets.
         */
        if ((WSAGetLastError() == WSAEINVAL) &&
            (level == IPPROTO_IP) &&
            (optname == IP_TOS)) {
            rv = 0;
        }
    }

    return rv;
}

/*
 * Wrapper for setsockopt dealing with Windows specific issues :-
 *
 * IP_TOS is not supported on some versions of Windows so
 * instead return the default value for the OS.
 */
JNIEXPORT int JNICALL
NET_GetSockOpt(int s, int level, int optname, void *optval,
               int *optlen)
{
    int rv;

    if (level == IPPROTO_IPV6 && optname == IPV6_TCLASS) {
        int *intopt = (int *)optval;
        *intopt = 0;
        *optlen = sizeof(*intopt);
        return 0;
    }

    rv = getsockopt(s, level, optname, optval, optlen);


    /*
     * IPPROTO_IP/IP_TOS is not supported on some Windows
     * editions so return the default type-of-service
     * value.
     */
    if (rv == SOCKET_ERROR) {

        if (WSAGetLastError() == WSAENOPROTOOPT &&
            level == IPPROTO_IP && optname == IP_TOS) {

            int *tos;
            tos = (int *)optval;
            *tos = NET_GetDefaultTOS();

            rv = 0;
        }
    }

    return rv;
}

/*
 * Sets SO_ECLUSIVEADDRUSE if SO_REUSEADDR is not already set.
 */
void setExclusiveBind(int fd) {
    int parg = 0;
    int plen = sizeof(parg);
    int rv = 0;
    rv = NET_GetSockOpt(fd, SOL_SOCKET, SO_REUSEADDR, (char *)&parg, &plen);
    if (rv == 0 && parg == 0) {
        parg = 1;
        rv = NET_SetSockOpt(fd, SOL_SOCKET, SO_EXCLUSIVEADDRUSE, (char*)&parg, plen);
    }
}

/*
 * Wrapper for bind winsock call - transparent converts an
 * error related to binding to a port that has exclusive access
 * into an error indicating the port is in use (facilitates
 * better error reporting).
 *
 * Should be only called by the wrapper method NET_WinBind
 */
JNIEXPORT int JNICALL
NET_Bind(int s, struct sockaddr *him, int len)
{
    int rv = 0;
    rv = bind(s, him, len);

    if (rv == SOCKET_ERROR) {
        /*
         * If bind fails with WSAEACCES it means that a privileged
         * process has done an exclusive bind (NT SP4/2000/XP only).
         */
        if (WSAGetLastError() == WSAEACCES) {
            WSASetLastError(WSAEADDRINUSE);
        }
    }

    return rv;
}

/*
 * Wrapper for NET_Bind call. Sets SO_EXCLUSIVEADDRUSE
 * if required, and then calls NET_BIND
 */
JNIEXPORT int JNICALL
NET_WinBind(int s, struct sockaddr *him, int len, jboolean exclBind)
{
    if (exclBind == JNI_TRUE)
        setExclusiveBind(s);
    return NET_Bind(s, him, len);
}

JNIEXPORT int JNICALL
NET_SocketClose(int fd) {
    struct linger l = {0, 0};
    int ret = 0;
    int len = sizeof (l);
    if (getsockopt(fd, SOL_SOCKET, SO_LINGER, (char *)&l, &len) == 0) {
        if (l.l_onoff == 0) {
            WSASendDisconnect(fd, NULL);
        }
    }
    ret = closesocket (fd);
    return ret;
}

JNIEXPORT int JNICALL
NET_Timeout(int fd, long timeout) {
    int ret;
    fd_set tbl;
    struct timeval t;
    t.tv_sec = timeout / 1000;
    t.tv_usec = (timeout % 1000) * 1000;
    FD_ZERO(&tbl);
    FD_SET(fd, &tbl);
    ret = select (fd + 1, &tbl, 0, 0, &t);
    return ret;
}


/*
 * differs from NET_Timeout() as follows:
 *
 * If timeout = -1, it blocks forever.
 *
 * returns 1 or 2 depending if only one or both sockets
 * fire at same time.
 *
 * *fdret is (one of) the active fds. If both sockets
 * fire at same time, *fdret = fd always.
 */
JNIEXPORT int JNICALL
NET_Timeout2(int fd, int fd1, long timeout, int *fdret) {
    int ret;
    fd_set tbl;
    struct timeval t, *tP = &t;
    if (timeout == -1) {
        tP = 0;
    } else {
        t.tv_sec = timeout / 1000;
        t.tv_usec = (timeout % 1000) * 1000;
    }
    FD_ZERO(&tbl);
    FD_SET(fd, &tbl);
    FD_SET(fd1, &tbl);
    ret = select (0, &tbl, 0, 0, tP);
    switch (ret) {
    case 0:
        return 0; /* timeout */
    case 1:
        if (FD_ISSET (fd, &tbl)) {
            *fdret= fd;
        } else {
            *fdret= fd1;
        }
        return 1;
    case 2:
        *fdret= fd;
        return 2;
    }
    return -1;
}


void dumpAddr (char *str, void *addr) {
    struct SOCKADDR_IN6 *a = (struct SOCKADDR_IN6 *)addr;
    int family = a->sin6_family;
    printf ("%s\n", str);
    if (family == AF_INET) {
        struct sockaddr_in *him = (struct sockaddr_in *)addr;
        printf ("AF_INET: port %d: %x\n", ntohs(him->sin_port),
                                          ntohl(him->sin_addr.s_addr));
    } else {
        int i;
        struct in6_addr *in = &a->sin6_addr;
        printf ("AF_INET6 ");
        printf ("port %d ", ntohs (a->sin6_port));
        printf ("flow %d ", a->sin6_flowinfo);
        printf ("addr ");
        for (i=0; i<7; i++) {
            printf ("%04x:", ntohs(in->s6_words[i]));
        }
        printf ("%04x", ntohs(in->s6_words[7]));
        printf (" scope %d\n", a->sin6_scope_id);
    }
}

/* Macro, which cleans-up the iv6bind structure,
 * closes the two sockets (if open),
 * and returns SOCKET_ERROR. Used in NET_BindV6 only.
 */

#define CLOSE_SOCKETS_AND_RETURN do {   \
    if (fd != -1) {                     \
        closesocket (fd);               \
        fd = -1;                        \
    }                                   \
    if (ofd != -1) {                    \
        closesocket (ofd);              \
        ofd = -1;                       \
    }                                   \
    if (close_fd != -1) {               \
        closesocket (close_fd);         \
        close_fd = -1;                  \
    }                                   \
    if (close_ofd != -1) {              \
        closesocket (close_ofd);        \
        close_ofd = -1;                 \
    }                                   \
    b->ipv4_fd = b->ipv6_fd = -1;       \
    return SOCKET_ERROR;                \
} while(0)

/*
 * if ipv6 is available, call NET_BindV6 to bind to the required address/port.
 * Because the same port number may need to be reserved in both v4 and v6 space,
 * this may require socket(s) to be re-opened. Therefore, all of this information
 * is passed in and returned through the ipv6bind structure.
 *
 * If the request is to bind to a specific address, then this (by definition) means
 * only bind in either v4 or v6, and this is just the same as normal. ie. a single
 * call to bind() will suffice. The other socket is closed in this case.
 *
 * The more complicated case is when the requested address is ::0 or 0.0.0.0.
 *
 * Two further cases:
 * 2. If the reqeusted port is 0 (ie. any port) then we try to bind in v4 space
 *    first with a wild-card port argument. We then try to bind in v6 space
 *    using the returned port number. If this fails, we repeat the process
 *    until a free port common to both spaces becomes available.
 *
 * 3. If the requested port is a specific port, then we just try to get that
 *    port in both spaces, and if it is not free in both, then the bind fails.
 *
 * On failure, sockets are closed and an error returned with CLOSE_SOCKETS_AND_RETURN
 */

JNIEXPORT int JNICALL
NET_BindV6(struct ipv6bind* b, jboolean exclBind) {
    int fd=-1, ofd=-1, rv, len;
    /* need to defer close until new sockets created */
    int close_fd=-1, close_ofd=-1;
    SOCKETADDRESS oaddr; /* other address to bind */
    int family = b->addr->him.sa_family;
    int ofamily;
    u_short port; /* requested port parameter */
    u_short bound_port;

    if (family == AF_INET && (b->addr->him4.sin_addr.s_addr != INADDR_ANY)) {
        /* bind to v4 only */
        int ret;
        ret = NET_WinBind ((int)b->ipv4_fd, (struct sockaddr *)b->addr,
                                sizeof (struct sockaddr_in), exclBind);
        if (ret == SOCKET_ERROR) {
            CLOSE_SOCKETS_AND_RETURN;
        }
        closesocket (b->ipv6_fd);
        b->ipv6_fd = -1;
        return 0;
    }
    if (family == AF_INET6 && (!IN6_IS_ADDR_ANY(&b->addr->him6.sin6_addr))) {
        /* bind to v6 only */
        int ret;
        ret = NET_WinBind ((int)b->ipv6_fd, (struct sockaddr *)b->addr,
                                sizeof (struct SOCKADDR_IN6), exclBind);
        if (ret == SOCKET_ERROR) {
            CLOSE_SOCKETS_AND_RETURN;
        }
        closesocket (b->ipv4_fd);
        b->ipv4_fd = -1;
        return 0;
    }

    /* We need to bind on both stacks, with the same port number */

    memset (&oaddr, 0, sizeof(oaddr));
    if (family == AF_INET) {
        ofamily = AF_INET6;
        fd = (int)b->ipv4_fd;
        ofd = (int)b->ipv6_fd;
        port = (u_short)GET_PORT (b->addr);
        IN6ADDR_SETANY (&oaddr.him6);
        oaddr.him6.sin6_port = port;
    } else {
        ofamily = AF_INET;
        ofd = (int)b->ipv4_fd;
        fd = (int)b->ipv6_fd;
        port = (u_short)GET_PORT (b->addr);
        oaddr.him4.sin_family = AF_INET;
        oaddr.him4.sin_port = port;
        oaddr.him4.sin_addr.s_addr = INADDR_ANY;
    }

    rv = NET_WinBind(fd, (struct sockaddr *)b->addr, SOCKETADDRESS_LEN(b->addr), exclBind);
    if (rv == SOCKET_ERROR) {
        CLOSE_SOCKETS_AND_RETURN;
    }

    /* get the port and set it in the other address */
    len = SOCKETADDRESS_LEN(b->addr);
    if (getsockname(fd, (struct sockaddr *)b->addr, &len) == -1) {
        CLOSE_SOCKETS_AND_RETURN;
    }
    bound_port = GET_PORT (b->addr);
    SET_PORT (&oaddr, bound_port);
    if ((rv=NET_WinBind (ofd, (struct sockaddr *) &oaddr,
                         SOCKETADDRESS_LEN (&oaddr), exclBind)) == SOCKET_ERROR) {
        int retries;
        int sotype, arglen=sizeof(sotype);

        /* no retries unless, the request was for any free port */

        if (port != 0) {
            CLOSE_SOCKETS_AND_RETURN;
        }

        getsockopt(fd, SOL_SOCKET, SO_TYPE, (void *)&sotype, &arglen);

#define SOCK_RETRIES 50
        /* 50 is an arbitrary limit, just to ensure that this
         * cannot be an endless loop. Would expect socket creation to
         * succeed sooner.
         */
        for (retries = 0; retries < SOCK_RETRIES; retries ++) {
            int len;
            close_fd = fd; fd = -1;
            close_ofd = ofd; ofd = -1;
            b->ipv4_fd = SOCKET_ERROR;
            b->ipv6_fd = SOCKET_ERROR;

            /* create two new sockets */
            fd = (int)socket (family, sotype, 0);
            if (fd == SOCKET_ERROR) {
                CLOSE_SOCKETS_AND_RETURN;
            }
            ofd = (int)socket (ofamily, sotype, 0);
            if (ofd == SOCKET_ERROR) {
                CLOSE_SOCKETS_AND_RETURN;
            }

            /* bind random port on first socket */
            SET_PORT (&oaddr, 0);
            rv = NET_WinBind (ofd, (struct sockaddr *)&oaddr, SOCKETADDRESS_LEN(&oaddr),
                              exclBind);
            if (rv == SOCKET_ERROR) {
                CLOSE_SOCKETS_AND_RETURN;
            }
            /* close the original pair of sockets before continuing */
            closesocket (close_fd);
            closesocket (close_ofd);
            close_fd = close_ofd = -1;

            /* bind new port on second socket */
            len = SOCKETADDRESS_LEN(&oaddr);
            if (getsockname(ofd, (struct sockaddr *)&oaddr, &len) == -1) {
                CLOSE_SOCKETS_AND_RETURN;
            }
            bound_port = GET_PORT (&oaddr);
            SET_PORT (b->addr, bound_port);
            rv = NET_WinBind (fd, (struct sockaddr *)b->addr, SOCKETADDRESS_LEN(b->addr),
                              exclBind);

            if (rv != SOCKET_ERROR) {
                if (family == AF_INET) {
                    b->ipv4_fd = fd;
                    b->ipv6_fd = ofd;
                } else {
                    b->ipv4_fd = ofd;
                    b->ipv6_fd = fd;
                }
                return 0;
            }
        }
        CLOSE_SOCKETS_AND_RETURN;
    }
    return 0;
}

/*
 * Determine the default interface for an IPv6 address.
 *
 * Returns :-
 *      0 if error
 *      > 0 interface index to use
 */
jint getDefaultIPv6Interface(JNIEnv *env, struct SOCKADDR_IN6 *target_addr)
{
    int ret;
    DWORD b;
    struct sockaddr_in6 route;
    SOCKET fd = socket(AF_INET6, SOCK_STREAM, 0);
    if (fd == INVALID_SOCKET) {
        return 0;
    }

    ret = WSAIoctl(fd, SIO_ROUTING_INTERFACE_QUERY,
                    (void *)target_addr, sizeof(struct sockaddr_in6),
                    (void *)&route, sizeof(struct sockaddr_in6),
                    &b, 0, 0);
    if (ret == SOCKET_ERROR) {
        // error
        closesocket(fd);
        return 0;
    } else {
        closesocket(fd);
        return route.sin6_scope_id;
    }
}

/**
 * Enables SIO_LOOPBACK_FAST_PATH
 */
JNIEXPORT jint JNICALL
NET_EnableFastTcpLoopback(int fd) {
    int enabled = 1;
    DWORD result_byte_count = -1;
    int result = WSAIoctl(fd,
                          SIO_LOOPBACK_FAST_PATH,
                          &enabled,
                          sizeof(enabled),
                          NULL,
                          0,
                          &result_byte_count,
                          NULL,
                          NULL);
    return result == SOCKET_ERROR ? WSAGetLastError() : 0;
}

/* If address types is IPv6, then IPv6 must be available. Otherwise
 * no address can be generated. In the case of an IPv4 Inetaddress this
 * method will return an IPv4 mapped address where IPv6 is available and
 * v4MappedAddress is TRUE. Otherwise it will return a sockaddr_in
 * structure for an IPv4 InetAddress.
*/
JNIEXPORT int JNICALL
NET_InetAddressToSockaddr(JNIEnv *env, jobject iaObj, int port, struct sockaddr *him,
                          int *len, jboolean v4MappedAddress) {
    jint family, iafam;
    iafam = getInetAddress_family(env, iaObj);
    family = (iafam == IPv4)? AF_INET : AF_INET6;
    if (ipv6_available() && !(family == AF_INET && v4MappedAddress == JNI_FALSE)) {
        struct SOCKADDR_IN6 *him6 = (struct SOCKADDR_IN6 *)him;
        jbyte caddr[16];
        jint address, scopeid = 0;
        jint cached_scope_id = 0;

        if (family == AF_INET) { /* will convert to IPv4-mapped address */
            memset((char *) caddr, 0, 16);
            address = getInetAddress_addr(env, iaObj);
            if (address == INADDR_ANY) {
                /* we would always prefer IPv6 wildcard address
                caddr[10] = 0xff;
                caddr[11] = 0xff; */
            } else {
                caddr[10] = 0xff;
                caddr[11] = 0xff;
                caddr[12] = ((address >> 24) & 0xff);
                caddr[13] = ((address >> 16) & 0xff);
                caddr[14] = ((address >> 8) & 0xff);
                caddr[15] = (address & 0xff);
            }
        } else {
            getInet6Address_ipaddress(env, iaObj, (char *)caddr);
            scopeid = getInet6Address_scopeid(env, iaObj);
            cached_scope_id = (jint)(*env)->GetIntField(env, iaObj, ia6_cachedscopeidID);
        }

        memset((char *)him6, 0, sizeof(struct SOCKADDR_IN6));
        him6->sin6_port = (u_short) htons((u_short)port);
        memcpy((void *)&(him6->sin6_addr), caddr, sizeof(struct in6_addr) );
        him6->sin6_family = AF_INET6;
        if ((family == AF_INET6) && IN6_IS_ADDR_LINKLOCAL( &(him6->sin6_addr) )
            && (!scopeid && !cached_scope_id)) {
            cached_scope_id = getDefaultIPv6Interface(env, him6);
            (*env)->SetIntField(env, iaObj, ia6_cachedscopeidID, cached_scope_id);
        }
        him6->sin6_scope_id = scopeid != 0 ? scopeid : cached_scope_id;
        *len = sizeof(struct SOCKADDR_IN6) ;
    } else {
        struct sockaddr_in *him4 = (struct sockaddr_in*)him;
        jint address;
        if (family != AF_INET) {
          JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Protocol family unavailable");
          return -1;
        }
        memset((char *) him4, 0, sizeof(struct sockaddr_in));
        address = getInetAddress_addr(env, iaObj);
        him4->sin_port = htons((short) port);
        him4->sin_addr.s_addr = (u_long) htonl(address);
        him4->sin_family = AF_INET;
        *len = sizeof(struct sockaddr_in);
    }
    return 0;
}

JNIEXPORT jint JNICALL
NET_GetPortFromSockaddr(struct sockaddr *him) {
    if (him->sa_family == AF_INET6) {
        return ntohs(((struct sockaddr_in6*)him)->sin6_port);
    } else {
        return ntohs(((struct sockaddr_in*)him)->sin_port);
    }
}

int
NET_IsIPv4Mapped(jbyte* caddr) {
    int i;
    for (i = 0; i < 10; i++) {
        if (caddr[i] != 0x00) {
            return 0; /* false */
        }
    }

    if (((caddr[10] & 0xff) == 0xff) && ((caddr[11] & 0xff) == 0xff)) {
        return 1; /* true */
    }
    return 0; /* false */
}

int
NET_IPv4MappedToIPv4(jbyte* caddr) {
    return ((caddr[12] & 0xff) << 24) | ((caddr[13] & 0xff) << 16) | ((caddr[14] & 0xff) << 8)
        | (caddr[15] & 0xff);
}

int
NET_IsEqual(jbyte* caddr1, jbyte* caddr2) {
    int i;
    for (i = 0; i < 16; i++) {
        if (caddr1[i] != caddr2[i]) {
            return 0; /* false */
        }
    }
    return 1;
}

int getScopeID (struct sockaddr *him) {
    struct SOCKADDR_IN6 *him6 = (struct SOCKADDR_IN6 *)him;
    return him6->sin6_scope_id;
}

int cmpScopeID (unsigned int scope, struct sockaddr *him) {
    struct SOCKADDR_IN6 *him6 = (struct SOCKADDR_IN6 *)him;
    return him6->sin6_scope_id == scope;
}

/**
 * Wrapper for select/poll with timeout on a single file descriptor.
 *
 * flags (defined in net_util_md.h can be any combination of
 * NET_WAIT_READ, NET_WAIT_WRITE & NET_WAIT_CONNECT.
 *
 * The function will return when either the socket is ready for one
 * of the specified operation or the timeout expired.
 *
 * It returns the time left from the timeout, or -1 if it expired.
 */

jint
NET_Wait(JNIEnv *env, jint fd, jint flags, jint timeout)
{
    jlong prevTime = JVM_CurrentTimeMillis(env, 0);
    jint read_rv;

    while (1) {
        jlong newTime;
        fd_set rd, wr, ex;
        struct timeval t;

        t.tv_sec = timeout / 1000;
        t.tv_usec = (timeout % 1000) * 1000;

        FD_ZERO(&rd);
        FD_ZERO(&wr);
        FD_ZERO(&ex);
        if (flags & NET_WAIT_READ) {
          FD_SET(fd, &rd);
        }
        if (flags & NET_WAIT_WRITE) {
          FD_SET(fd, &wr);
        }
        if (flags & NET_WAIT_CONNECT) {
          FD_SET(fd, &wr);
          FD_SET(fd, &ex);
        }

        errno = 0;
        read_rv = select(fd+1, &rd, &wr, &ex, &t);

        newTime = JVM_CurrentTimeMillis(env, 0);
        timeout -= (jint)(newTime - prevTime);
        if (timeout <= 0) {
          return read_rv > 0 ? 0 : -1;
        }
        newTime = prevTime;

        if (read_rv > 0) {
          break;
        }


      } /* while */

    return timeout;
}

int NET_Socket (int domain, int type, int protocol) {
    SOCKET sock;
    sock = socket (domain, type, protocol);
    if (sock != INVALID_SOCKET) {
        SetHandleInformation((HANDLE)(uintptr_t)sock, HANDLE_FLAG_INHERIT, FALSE);
    }
    return (int)sock;
}
