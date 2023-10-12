/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "net_util.h"

#include "java_net_InetAddress.h"
#include "java_net_SocketOptions.h"

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
    snprintf(exc, sizeof(exc), "%s%s", JNU_JAVANETPKG, excP);
    JNU_ThrowByName(env, exc, fullMsg);
}

void
NET_ThrowByNameWithLastError(JNIEnv *env, const char *name,
                   const char *defaultDetail) {
    JNU_ThrowByNameWithMessageAndLastError(env, name, defaultDetail);
}

jint  IPv4_supported()
{
    SOCKET s = socket(AF_INET, SOCK_STREAM, 0);
    if (s == INVALID_SOCKET) {
        return JNI_FALSE;
    }
    closesocket(s);

    return JNI_TRUE;
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

jint reuseport_supported(int ipv6_available)
{
    /* SO_REUSEPORT is not supported on Windows */
    return JNI_FALSE;
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

            *((int *)optval) = 0;
            rv = 0;
        }
    }

    return rv;
}

/*
 * Sets SO_EXCLUSIVEADDRUSE if SO_REUSEADDR is not already set.
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
NET_Bind(int s, SOCKETADDRESS *sa, int len)
{
    int rv = 0;
    rv = bind(s, &sa->sa, len);

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
NET_WinBind(int s, SOCKETADDRESS *sa, int len, jboolean exclBind)
{
    if (exclBind == JNI_TRUE)
        setExclusiveBind(s);
    return NET_Bind(s, sa, len);
}


void dumpAddr (char *str, void *addr) {
    struct sockaddr_in6 *a = (struct sockaddr_in6 *)addr;
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

int
IsWindows10RS3OrGreater() {
    OSVERSIONINFOEXW osvi = { sizeof(osvi), 0, 0, 0, 0, {0}, 0, 0 };
    DWORDLONG const cond_mask = VerSetConditionMask(
        VerSetConditionMask(
          VerSetConditionMask(
            0, VER_MAJORVERSION, VER_GREATER_EQUAL),
               VER_MINORVERSION, VER_GREATER_EQUAL),
               VER_BUILDNUMBER,  VER_GREATER_EQUAL);

    osvi.dwMajorVersion = HIBYTE(_WIN32_WINNT_WIN10);
    osvi.dwMinorVersion = LOBYTE(_WIN32_WINNT_WIN10);
    osvi.dwBuildNumber  = 16299; // RS3 (Redstone 3)

    return VerifyVersionInfoW(&osvi, VER_MAJORVERSION | VER_MINORVERSION | VER_BUILDNUMBER, cond_mask) != 0;
}

/**
 * Shortens the default Windows socket
 * connect timeout. Recommended for usage
 * on the loopback adapter only.
 */
JNIEXPORT jint JNICALL
NET_EnableFastTcpLoopbackConnect(int fd) {
    TCP_INITIAL_RTO_PARAMETERS rto = {
        TCP_INITIAL_RTO_UNSPECIFIED_RTT,    // Use the default or overridden by the Administrator
        1                                   // Minimum possible value before Windows 10 RS3
    };

    /**
     * In Windows 10 RS3+ we can use the no retransmissions flag to
     * completely remove the timeout delay, which is fixed to 500ms
     * if Windows receives RST when the destination port is not open.
     */
    if (IsWindows10RS3OrGreater()) {
        rto.MaxSynRetransmissions = TCP_INITIAL_RTO_NO_SYN_RETRANSMISSIONS;
    }

    DWORD result_byte_count = -1;
    int result = WSAIoctl(fd,                       // descriptor identifying a socket
                          SIO_TCP_INITIAL_RTO,      // dwIoControlCode
                          &rto,                     // pointer to TCP_INITIAL_RTO_PARAMETERS structure
                          sizeof(rto),              // size, in bytes, of the input buffer
                          NULL,                     // pointer to output buffer
                          0,                        // size of output buffer
                          &result_byte_count,       // number of bytes returned
                          NULL,                     // OVERLAPPED structure
                          NULL);                    // completion routine
    return (result == SOCKET_ERROR) ? WSAGetLastError() : 0;
}

/**
 * See net_util.h for documentation
 */
JNIEXPORT int JNICALL
NET_InetAddressToSockaddr(JNIEnv *env, jobject iaObj, int port,
                          SOCKETADDRESS *sa, int *len,
                          jboolean v4MappedAddress)
{
    jint family = getInetAddress_family(env, iaObj);
    JNU_CHECK_EXCEPTION_RETURN(env, -1);
    memset((char *)sa, 0, sizeof(SOCKETADDRESS));

    if (ipv6_available() &&
        !(family == java_net_InetAddress_IPv4 &&
          v4MappedAddress == JNI_FALSE))
    {
        jbyte caddr[16];
        jint address;
        unsigned int scopeid = 0;

        if (family == java_net_InetAddress_IPv4) {
            // convert to IPv4-mapped address
            memset((char *)caddr, 0, 16);
            address = getInetAddress_addr(env, iaObj);
            JNU_CHECK_EXCEPTION_RETURN(env, -1);
            if (address == INADDR_ANY) {
                /* we would always prefer IPv6 wildcard address
                 * caddr[10] = 0xff;
                 * caddr[11] = 0xff; */
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
        }
        sa->sa6.sin6_port = (u_short)htons((u_short)port);
        memcpy((void *)&sa->sa6.sin6_addr, caddr, sizeof(struct in6_addr));
        sa->sa6.sin6_family = AF_INET6;
        sa->sa6.sin6_scope_id = scopeid;
        if (len != NULL) {
            *len = sizeof(struct sockaddr_in6);
        }
    } else {
        jint address;
        if (family != java_net_InetAddress_IPv4) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Protocol family unavailable");
            return -1;
        }
        address = getInetAddress_addr(env, iaObj);
        JNU_CHECK_EXCEPTION_RETURN(env, -1);
        sa->sa4.sin_port = htons((short)port);
        sa->sa4.sin_addr.s_addr = (u_long)htonl(address);
        sa->sa4.sin_family = AF_INET;
        if (len != NULL) {
            *len = sizeof(struct sockaddr_in);
        }
    }
    return 0;
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
