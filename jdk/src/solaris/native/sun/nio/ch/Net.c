/*
 * Copyright 2001-2007 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "java_net_SocketOptions.h"
#include "nio.h"

#ifdef __linux__
#include <sys/utsname.h>

#define IPV6_MULTICAST_IF 17
#ifndef SO_BSDCOMPAT
#define SO_BSDCOMPAT  14
#endif
#endif

JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_initIDs(JNIEnv *env, jclass clazz)
{
    /* Here because Windows native code does need to init IDs */
}

JNIEXPORT int JNICALL
Java_sun_nio_ch_Net_socket0(JNIEnv *env, jclass cl, jboolean stream,
                            jboolean reuse)
{
    int fd;

#ifdef AF_INET6
    if (ipv6_available())
        fd = socket(AF_INET6, (stream ? SOCK_STREAM : SOCK_DGRAM), 0);
    else
#endif /* AF_INET6 */
        fd = socket(AF_INET, (stream ? SOCK_STREAM : SOCK_DGRAM), 0);

    if (fd < 0) {
        return handleSocketError(env, errno);
    }
    if (reuse) {
        int arg = 1;
        if (NET_SetSockOpt(fd, SOL_SOCKET, SO_REUSEADDR, (char*)&arg,
                           sizeof(arg)) < 0) {
            JNU_ThrowByNameWithLastError(env,
                                         JNU_JAVANETPKG "SocketException",
                                         "sun.nio.ch.Net.setIntOption");
        }
    }
    return fd;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_bind(JNIEnv *env, jclass clazz, /* ## Needs rest of PSI gunk */
                         jobject fdo, jobject ia, int port)
{
    SOCKADDR sa;
    int sa_len = SOCKADDR_LEN;
    int rv = 0;

    if (NET_InetAddressToSockaddr(env, ia, port, (struct sockaddr *)&sa, &sa_len, JNI_TRUE) != 0) {
      return;
    }

    rv = NET_Bind(fdval(env, fdo), (struct sockaddr *)&sa, sa_len);
    if (rv != 0) {
        handleSocketError(env, errno);
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_connect(JNIEnv *env, jclass clazz,
                                jobject fdo, jobject iao, jint port,
                                jint trafficClass)
{
    SOCKADDR sa;
    int sa_len = SOCKADDR_LEN;
    int rv;

    if (NET_InetAddressToSockaddr(env, iao, port, (struct sockaddr *) &sa, &sa_len, JNI_TRUE) != 0) {
      return IOS_THROWN;
    }

#ifdef AF_INET6
#if 0
    if (trafficClass != 0 && ipv6_available()) { /* ## FIX */
        NET_SetTrafficClass((struct sockaddr *)&sa, trafficClass);
    }
#endif
#endif

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


#ifdef NEEDED

/* ## This is gross.  We should generate platform-specific constant
 * ## definitions into a .java file and use those directly.
 */

static int
mapOption(JNIEnv *env, int opt, int *klevel, int *kopt)
{

    switch (opt) {

    case java_net_SocketOptions_IP_TOS:
        *klevel = IPPROTO_IP;
        *kopt = IP_TOS;
        break;

    case java_net_SocketOptions_SO_BROADCAST:
    case java_net_SocketOptions_SO_KEEPALIVE:
    case java_net_SocketOptions_SO_LINGER:
    case java_net_SocketOptions_SO_OOBINLINE:
    case java_net_SocketOptions_SO_RCVBUF:
    case java_net_SocketOptions_SO_REUSEADDR:
    case java_net_SocketOptions_SO_SNDBUF:
        *klevel = SOL_SOCKET;
        break;

    case java_net_SocketOptions_TCP_NODELAY:
        *klevel = IPPROTO_IP;
        *kopt = TCP_NODELAY;
        return 0;

    default:
        JNU_ThrowByName(env, "java/lang/IllegalArgumentException", NULL);
        return -1;
    }

    switch (opt) {

    case java_net_SocketOptions_SO_BROADCAST:   *kopt = SO_BROADCAST;  break;
    case java_net_SocketOptions_SO_KEEPALIVE:   *kopt = SO_KEEPALIVE;  break;
    case java_net_SocketOptions_SO_LINGER:      *kopt = SO_LINGER;  break;
    case java_net_SocketOptions_SO_OOBINLINE:   *kopt = SO_OOBINLINE;  break;
    case java_net_SocketOptions_SO_RCVBUF:      *kopt = SO_RCVBUF;  break;
    case java_net_SocketOptions_SO_REUSEADDR:   *kopt = SO_REUSEADDR;  break;
    case java_net_SocketOptions_SO_SNDBUF:      *kopt = SO_SNDBUF;  break;

    default:
        return -1;
    }

    return 0;
}
#endif


JNIEXPORT jint JNICALL
Java_sun_nio_ch_Net_getIntOption0(JNIEnv *env, jclass clazz,
                                  jobject fdo, jint opt)
{
    int klevel, kopt;
    int result;
    struct linger linger;
    void *arg;
    int arglen;

    if (NET_MapSocketOption(opt, &klevel, &kopt) < 0) {
        JNU_ThrowByNameWithLastError(env,
                                     JNU_JAVANETPKG "SocketException",
                                     "Unsupported socket option");
        return -1;
    }

    if (opt == java_net_SocketOptions_SO_LINGER) {
        arg = (void *)&linger;
        arglen = sizeof(linger);
    } else {
        arg = (void *)&result;
        arglen = sizeof(result);
    }

    if (NET_GetSockOpt(fdval(env, fdo), klevel, kopt, arg, &arglen) < 0) {
        JNU_ThrowByNameWithLastError(env,
                                     JNU_JAVANETPKG "SocketException",
                                     "sun.nio.ch.Net.getIntOption");
        return -1;
    }

    if (opt == java_net_SocketOptions_SO_LINGER)
        return linger.l_onoff ? linger.l_linger : -1;
    else
        return result;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_Net_setIntOption0(JNIEnv *env, jclass clazz,
                                  jobject fdo, jint opt, jint arg)
{
    int klevel, kopt;
    int result;
    struct linger linger;
    void *parg;
    int arglen;

    if (NET_MapSocketOption(opt, &klevel, &kopt) < 0) {
        JNU_ThrowByNameWithLastError(env,
                                     JNU_JAVANETPKG "SocketException",
                                     "Unsupported socket option");
        return;
    }

    if (opt == java_net_SocketOptions_SO_LINGER) {
        parg = (void *)&linger;
        arglen = sizeof(linger);
        if (arg >= 0) {
            linger.l_onoff = 1;
            linger.l_linger = arg;
        } else {
            linger.l_onoff = 0;
            linger.l_linger = 0;
        }
    } else {
        parg = (void *)&arg;
        arglen = sizeof(arg);
    }

    if (NET_SetSockOpt(fdval(env, fdo), klevel, kopt, parg, arglen) < 0) {
        JNU_ThrowByNameWithLastError(env,
                                     JNU_JAVANETPKG "SocketException",
                                     "sun.nio.ch.Net.setIntOption");
    }
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
