/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <sys/types.h>
#include <sys/socket.h>
#include <string.h>
#include <netinet/in.h>
#include <netinet/tcp.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include "sun_nio_ch_Net.h"
#include "net_util.h"
#include "net_util_md.h"
#include "nio_util.h"
#include "nio.h"

/**
 * Definitions for source-specific multicast to allow for building
 * with older header files.
 */

#ifdef __solaris__

#ifndef IP_BLOCK_SOURCE

#define IP_BLOCK_SOURCE                 0x15
#define IP_UNBLOCK_SOURCE               0x16
#define IP_ADD_SOURCE_MEMBERSHIP        0x17
#define IP_DROP_SOURCE_MEMBERSHIP       0x18

#define MCAST_BLOCK_SOURCE              0x2b
#define MCAST_UNBLOCK_SOURCE            0x2c
#define MCAST_JOIN_SOURCE_GROUP         0x2d
#define MCAST_LEAVE_SOURCE_GROUP        0x2e

#endif  /* IP_BLOCK_SOURCE */

struct my_ip_mreq_source {
        struct in_addr  imr_multiaddr;
        struct in_addr  imr_sourceaddr;
        struct in_addr  imr_interface;
};

/*
 * Use #pragma pack() construct to force 32-bit alignment on amd64.
 */
#if defined(amd64)
#pragma pack(4)
#endif

struct my_group_source_req {
        uint32_t                gsr_interface;  /* interface index */
        struct sockaddr_storage gsr_group;      /* group address */
        struct sockaddr_storage gsr_source;     /* source address */
};

#if defined(amd64)
#pragma pack()
#endif

#endif  /* __solaris__ */


#ifdef __linux__

#ifndef IP_BLOCK_SOURCE

#define IP_BLOCK_SOURCE                 38
#define IP_UNBLOCK_SOURCE               37
#define IP_ADD_SOURCE_MEMBERSHIP        39
#define IP_DROP_SOURCE_MEMBERSHIP       40

#define MCAST_BLOCK_SOURCE              43
#define MCAST_UNBLOCK_SOURCE            44
#define MCAST_JOIN_SOURCE_GROUP         42
#define MCAST_LEAVE_SOURCE_GROUP        45

#endif  /* IP_BLOCK_SOURCE */

struct my_ip_mreq_source {
        struct in_addr  imr_multiaddr;
        struct in_addr  imr_interface;
        struct in_addr  imr_sourceaddr;
};

struct my_group_source_req {
        uint32_t                gsr_interface;  /* interface index */
        struct sockaddr_storage gsr_group;      /* group address */
        struct sockaddr_storage gsr_source;     /* source address */
};

#endif   /* __linux__ */


#define COPY_INET6_ADDRESS(env, source, target) \
    (*env)->GetByteArrayRegion(env, source, 0, 16, target)

/*
 * Copy IPv6 group, interface index, and IPv6 source address
 * into group_source_req structure.
 */
static void initGroupSourceReq(JNIEnv* env, jbyteArray group, jint index,
                               jbyteArray source, struct my_group_source_req* req)
{
    struct sockaddr_in6* sin6;

    req->gsr_interface = (uint32_t)index;

    sin6 = (struct sockaddr_in6*)&(req->gsr_group);
    sin6->sin6_family = AF_INET6;
    COPY_INET6_ADDRESS(env, group, (jbyte*)&(sin6->sin6_addr));

    sin6 = (struct sockaddr_in6*)&(req->gsr_source);
    sin6->sin6_family = AF_INET6;
    COPY_INET6_ADDRESS(env, source, (jbyte*)&(sin6->sin6_addr));
}


JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_initIDs(JNIEnv *env, jclass clazz)
{
    /* Here because Windows native code does need to init IDs */
}

JNIEXPORT jboolean JNICALL
Java_sun_nio_ch_Net_isIPv6Available0(JNIEnv* env, jclass cl)
{
    return (ipv6_available()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT int JNICALL
Java_sun_nio_ch_Net_socket0(JNIEnv *env, jclass cl, jboolean preferIPv6,
                            jboolean stream, jboolean reuse)
{
    int fd;
    int type = (stream ? SOCK_STREAM : SOCK_DGRAM);
    int domain = (ipv6_available() && preferIPv6) ? AF_INET6 : AF_INET;

    fd = socket(domain, type, 0);
    if (fd < 0) {
        return handleSocketError(env, errno);
    }
    if (reuse) {
        int arg = 1;
        if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (char*)&arg,
                       sizeof(arg)) < 0) {
            JNU_ThrowByNameWithLastError(env,
                                         JNU_JAVANETPKG "SocketException",
                                         "sun.nio.ch.Net.setIntOption");
            close(fd);
            return -1;
        }
    }
#ifdef __linux__
    /* By default, Linux uses the route default */
    if (domain == AF_INET6 && type == SOCK_DGRAM) {
        int arg = 1;
        if (setsockopt(fd, IPPROTO_IPV6, IPV6_MULTICAST_HOPS, &arg,
                       sizeof(arg)) < 0) {
            JNU_ThrowByNameWithLastError(env,
                                         JNU_JAVANETPKG "SocketException",
                                         "sun.nio.ch.Net.setIntOption");
            close(fd);
            return -1;
        }
    }
#endif
    return fd;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_bind0(JNIEnv *env, jclass clazz, jboolean preferIPv6,
                          jobject fdo, jobject iao, int port)
{
    SOCKADDR sa;
    int sa_len = SOCKADDR_LEN;
    int rv = 0;

    if (NET_InetAddressToSockaddr(env, iao, port, (struct sockaddr *)&sa, &sa_len, preferIPv6) != 0) {
      return;
    }

    rv = NET_Bind(fdval(env, fdo), (struct sockaddr *)&sa, sa_len);
    if (rv != 0) {
        handleSocketError(env, errno);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_listen(JNIEnv *env, jclass cl, jobject fdo, jint backlog)
{
    if (listen(fdval(env, fdo), backlog) < 0)
        handleSocketError(env, errno);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_connect0(JNIEnv *env, jclass clazz, jboolean preferIPv6,
                             jobject fdo, jobject iao, jint port)
{
    SOCKADDR sa;
    int sa_len = SOCKADDR_LEN;
    int rv;

    if (NET_InetAddressToSockaddr(env, iao, port, (struct sockaddr *) &sa,
                                  &sa_len, preferIPv6) != 0)
    {
      return IOS_THROWN;
    }

    rv = connect(fdval(env, fdo), (struct sockaddr *)&sa, sa_len);
    if (rv != 0) {
        if (errno == EINPROGRESS) {
            return IOS_UNAVAILABLE;
        } else if (errno == EINTR) {
            return IOS_INTERRUPTED;
        }
        return handleSocketError(env, errno);
    }
    return 1;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_localPort(JNIEnv *env, jclass clazz, jobject fdo)
{
    SOCKADDR sa;
    socklen_t sa_len = SOCKADDR_LEN;
    if (getsockname(fdval(env, fdo), (struct sockaddr *)&sa, &sa_len) < 0) {
        handleSocketError(env, errno);
        return -1;
    }
    return NET_GetPortFromSockaddr((struct sockaddr *)&sa);
}

JNIEXPORT jobject JNICALL
Java_sun_nio_ch_Net_localInetAddress(JNIEnv *env, jclass clazz, jobject fdo)
{
    SOCKADDR sa;
    socklen_t sa_len = SOCKADDR_LEN;
    int port;
    if (getsockname(fdval(env, fdo), (struct sockaddr *)&sa, &sa_len) < 0) {
        handleSocketError(env, errno);
        return NULL;
    }
    return NET_SockaddrToInetAddress(env, (struct sockaddr *)&sa, &port);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_getIntOption0(JNIEnv *env, jclass clazz, jobject fdo,
                                  jboolean mayNeedConversion, jint level, jint opt)
{
    int result;
    struct linger linger;
    u_char carg;
    void *arg;
    int arglen, n;

    /* Option value is an int except for a few specific cases */

    arg = (void *)&result;
    arglen = sizeof(result);

    if (level == IPPROTO_IP &&
        (opt == IP_MULTICAST_TTL || opt == IP_MULTICAST_LOOP)) {
        arg = (void*)&carg;
        arglen = sizeof(carg);
    }

    if (level == SOL_SOCKET && opt == SO_LINGER) {
        arg = (void *)&linger;
        arglen = sizeof(linger);
    }

    if (mayNeedConversion) {
        n = NET_GetSockOpt(fdval(env, fdo), level, opt, arg, &arglen);
    } else {
        n = getsockopt(fdval(env, fdo), level, opt, arg, &arglen);
    }
    if (n < 0) {
        JNU_ThrowByNameWithLastError(env,
                                     JNU_JAVANETPKG "SocketException",
                                     "sun.nio.ch.Net.getIntOption");
        return -1;
    }

    if (level == IPPROTO_IP &&
        (opt == IP_MULTICAST_TTL || opt == IP_MULTICAST_LOOP))
    {
        return (jint)carg;
    }

    if (level == SOL_SOCKET && opt == SO_LINGER)
        return linger.l_onoff ? (jint)linger.l_linger : (jint)-1;

    return (jint)result;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_setIntOption0(JNIEnv *env, jclass clazz, jobject fdo,
                                  jboolean mayNeedConversion, jint level, jint opt, jint arg)
{
    int result;
    struct linger linger;
    u_char carg;
    void *parg;
    int arglen, n;

    /* Option value is an int except for a few specific cases */

    parg = (void*)&arg;
    arglen = sizeof(arg);

    if (level == IPPROTO_IP &&
        (opt == IP_MULTICAST_TTL || opt == IP_MULTICAST_LOOP)) {
        parg = (void*)&carg;
        arglen = sizeof(carg);
        carg = (u_char)arg;
    }

    if (level == SOL_SOCKET && opt == SO_LINGER) {
        parg = (void *)&linger;
        arglen = sizeof(linger);
        if (arg >= 0) {
            linger.l_onoff = 1;
            linger.l_linger = arg;
        } else {
            linger.l_onoff = 0;
            linger.l_linger = 0;
        }
    }

    if (mayNeedConversion) {
        n = NET_SetSockOpt(fdval(env, fdo), level, opt, parg, arglen);
    } else {
        n = setsockopt(fdval(env, fdo), level, opt, parg, arglen);
    }
    if (n < 0) {
        JNU_ThrowByNameWithLastError(env,
                                     JNU_JAVANETPKG "SocketException",
                                     "sun.nio.ch.Net.setIntOption");
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_joinOrDrop4(JNIEnv *env, jobject this, jboolean join, jobject fdo,
                                jint group, jint interf, jint source)
{
    struct ip_mreq mreq;
    struct my_ip_mreq_source mreq_source;
    int opt, n, optlen;
    void* optval;

    if (source == 0) {
        mreq.imr_multiaddr.s_addr = htonl(group);
        mreq.imr_interface.s_addr = htonl(interf);
        opt = (join) ? IP_ADD_MEMBERSHIP : IP_DROP_MEMBERSHIP;
        optval = (void*)&mreq;
        optlen = sizeof(mreq);
    } else {
        mreq_source.imr_multiaddr.s_addr = htonl(group);
        mreq_source.imr_sourceaddr.s_addr = htonl(source);
        mreq_source.imr_interface.s_addr = htonl(interf);
        opt = (join) ? IP_ADD_SOURCE_MEMBERSHIP : IP_DROP_SOURCE_MEMBERSHIP;
        optval = (void*)&mreq_source;
        optlen = sizeof(mreq_source);
    }

    n = setsockopt(fdval(env,fdo), IPPROTO_IP, opt, optval, optlen);
    if (n < 0) {
        if (join && (errno == ENOPROTOOPT))
            return IOS_UNAVAILABLE;
        handleSocketError(env, errno);
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_blockOrUnblock4(JNIEnv *env, jobject this, jboolean block, jobject fdo,
                                    jint group, jint interf, jint source)
{
    struct my_ip_mreq_source mreq_source;
    int n;
    int opt = (block) ? IP_BLOCK_SOURCE : IP_UNBLOCK_SOURCE;

    mreq_source.imr_multiaddr.s_addr = htonl(group);
    mreq_source.imr_sourceaddr.s_addr = htonl(source);
    mreq_source.imr_interface.s_addr = htonl(interf);

    n = setsockopt(fdval(env,fdo), IPPROTO_IP, opt,
                   (void*)&mreq_source, sizeof(mreq_source));
    if (n < 0) {
        if (block && (errno == ENOPROTOOPT))
            return IOS_UNAVAILABLE;
        handleSocketError(env, errno);
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_joinOrDrop6(JNIEnv *env, jobject this, jboolean join, jobject fdo,
                                jbyteArray group, jint index, jbyteArray source)
{
    struct ipv6_mreq mreq6;
    struct my_group_source_req req;
    int opt, n, optlen;
    void* optval;

    if (source == NULL) {
        COPY_INET6_ADDRESS(env, group, (jbyte*)&(mreq6.ipv6mr_multiaddr));
        mreq6.ipv6mr_interface = (int)index;
        opt = (join) ? IPV6_ADD_MEMBERSHIP : IPV6_DROP_MEMBERSHIP;
        optval = (void*)&mreq6;
        optlen = sizeof(mreq6);
    } else {
#ifdef __linux__
        /* Include-mode filtering broken on Linux at least to 2.6.24 */
        return IOS_UNAVAILABLE;
#else
        initGroupSourceReq(env, group, index, source, &req);
        opt = (join) ? MCAST_JOIN_SOURCE_GROUP : MCAST_LEAVE_SOURCE_GROUP;
        optval = (void*)&req;
        optlen = sizeof(req);
#endif
    }

    n = setsockopt(fdval(env,fdo), IPPROTO_IPV6, opt, optval, optlen);
    if (n < 0) {
        if (join && (errno == ENOPROTOOPT))
            return IOS_UNAVAILABLE;
        handleSocketError(env, errno);
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_blockOrUnblock6(JNIEnv *env, jobject this, jboolean block, jobject fdo,
                                    jbyteArray group, jint index, jbyteArray source)
{
    struct my_group_source_req req;
    int n;
    int opt = (block) ? MCAST_BLOCK_SOURCE : MCAST_UNBLOCK_SOURCE;

    initGroupSourceReq(env, group, index, source, &req);

    n = setsockopt(fdval(env,fdo), IPPROTO_IPV6, opt,
        (void*)&req, sizeof(req));
    if (n < 0) {
        if (block && (errno == ENOPROTOOPT))
            return IOS_UNAVAILABLE;
        handleSocketError(env, errno);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_setInterface4(JNIEnv* env, jobject this, jobject fdo, jint interf)
{
    struct in_addr in;
    int arglen = sizeof(struct in_addr);
    int n;

    in.s_addr = htonl(interf);

    n = setsockopt(fdval(env, fdo), IPPROTO_IP, IP_MULTICAST_IF,
                   (void*)&(in.s_addr), arglen);
    if (n < 0) {
        handleSocketError(env, errno);
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_getInterface4(JNIEnv* env, jobject this, jobject fdo)
{
    struct in_addr in;
    int arglen = sizeof(struct in_addr);
    int n;

    n = getsockopt(fdval(env, fdo), IPPROTO_IP, IP_MULTICAST_IF, (void*)&in, &arglen);
    if (n < 0) {
        handleSocketError(env, errno);
        return -1;
    }
    return ntohl(in.s_addr);
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_setInterface6(JNIEnv* env, jobject this, jobject fdo, jint index)
{
    int value = (jint)index;
    int arglen = sizeof(value);
    int n;

    n = setsockopt(fdval(env, fdo), IPPROTO_IPV6, IPV6_MULTICAST_IF,
                   (void*)&(index), arglen);
    if (n < 0) {
        handleSocketError(env, errno);
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_getInterface6(JNIEnv* env, jobject this, jobject fdo)
{
    int index;
    int arglen = sizeof(index);
    int n;

    n = getsockopt(fdval(env, fdo), IPPROTO_IPV6, IPV6_MULTICAST_IF, (void*)&index, &arglen);
    if (n < 0) {
        handleSocketError(env, errno);
        return -1;
    }
    return (jint)index;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_shutdown(JNIEnv *env, jclass cl, jobject fdo, jint jhow)
{
    int how = (jhow == sun_nio_ch_Net_SHUT_RD) ? SHUT_RD :
        (jhow == sun_nio_ch_Net_SHUT_WR) ? SHUT_WR : SHUT_RDWR;
    if ((shutdown(fdval(env, fdo), how) < 0) && (errno != ENOTCONN))
        handleSocketError(env, errno);
}

/* Declared in nio_util.h */

jint
handleSocketError(JNIEnv *env, jint errorValue)
{
    char *xn;
    switch (errorValue) {
        case EINPROGRESS:       /* Non-blocking connect */
            return 0;
        case EPROTO:
            xn = JNU_JAVANETPKG "ProtocolException";
            break;
        case ECONNREFUSED:
            xn = JNU_JAVANETPKG "ConnectException";
            break;
        case ETIMEDOUT:
            xn = JNU_JAVANETPKG "ConnectException";
            break;
        case EHOSTUNREACH:
            xn = JNU_JAVANETPKG "NoRouteToHostException";
            break;
        case EADDRINUSE:  /* Fall through */
        case EADDRNOTAVAIL:
            xn = JNU_JAVANETPKG "BindException";
            break;
        default:
            xn = JNU_JAVANETPKG "SocketException";
            break;
    }
    errno = errorValue;
    JNU_ThrowByNameWithLastError(env, xn, "NioSocketError");
    return IOS_THROWN;
}
