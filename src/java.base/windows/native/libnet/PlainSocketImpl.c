/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "java_net_PlainSocketImpl.h"
#include "java_net_SocketOptions.h"

#define SET_BLOCKING     0
#define SET_NONBLOCKING  1

static jclass isa_class;        /* java.net.InetSocketAddress */
static jmethodID isa_ctorID;    /* InetSocketAddress(InetAddress, int) */

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_initIDs
  (JNIEnv *env, jclass clazz) {

    jclass cls = (*env)->FindClass(env, "java/net/InetSocketAddress");
    CHECK_NULL(cls);
    isa_class = (*env)->NewGlobalRef(env, cls);
    CHECK_NULL(isa_class);
    isa_ctorID = (*env)->GetMethodID(env, cls, "<init>",
                                     "(Ljava/net/InetAddress;I)V");
    CHECK_NULL(isa_ctorID);
    initInetAddressIDs(env);

    // implement read timeout with select.
    isRcvTimeoutSupported = JNI_FALSE;
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    socket0
 * Signature: (ZZ)I
 */
JNIEXPORT jint JNICALL Java_java_net_PlainSocketImpl_socket0
  (JNIEnv *env, jclass clazz, jboolean stream) {
    int fd, rv, opt = 0;
    int type = (stream ? SOCK_STREAM : SOCK_DGRAM);
    int domain = ipv6_available() ? AF_INET6 : AF_INET;

    fd = NET_Socket(domain, type, 0);

    if (fd == INVALID_SOCKET) {
        NET_ThrowNew(env, WSAGetLastError(), "create");
        return -1;
    }

    if (domain == AF_INET6) {
        rv = setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, (char *) &opt,
                        sizeof(opt));
        if (rv == SOCKET_ERROR) {
            NET_ThrowNew(env, WSAGetLastError(), "create");
            closesocket(fd);
            return -1;
        }
    }

    return fd;
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    bind0
 * Signature: (ILjava/net/InetAddress;I)V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_bind0
  (JNIEnv *env, jclass clazz, jint fd, jobject iaObj, jint port,
   jboolean exclBind)
{
    SOCKETADDRESS sa;
    int rv, sa_len = 0;
    jboolean v4MappedAddress = ipv6_available() ? JNI_TRUE : JNI_FALSE;

    if (NET_InetAddressToSockaddr(env, iaObj, port, &sa,
                                  &sa_len, v4MappedAddress) != 0) {
        return;
    }

    rv = NET_WinBind(fd, &sa, sa_len, exclBind);

    if (rv == SOCKET_ERROR)
        NET_ThrowNew(env, WSAGetLastError(), "NET_Bind");
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    connect0
 * Signature: (ILjava/net/InetAddress;I)I
 */
JNIEXPORT jint JNICALL Java_java_net_PlainSocketImpl_connect0
  (JNIEnv *env, jclass clazz, jint fd, jobject iaObj, jint port) {
    SOCKETADDRESS sa;
    int rv, sa_len = 0;
    jboolean v4MappedAddress = ipv6_available() ? JNI_TRUE : JNI_FALSE;

    if (NET_InetAddressToSockaddr(env, iaObj, port, &sa,
                                  &sa_len, v4MappedAddress) != 0) {
        return -1;
    }

    rv = connect(fd, &sa.sa, sa_len);
    if (rv == SOCKET_ERROR) {
        int err = WSAGetLastError();
        if (err == WSAEWOULDBLOCK) {
            return java_net_PlainSocketImpl_WOULDBLOCK;
        } else if (err == WSAEADDRNOTAVAIL) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "ConnectException",
                "connect: Address is invalid on local machine,"
                " or port is not valid on remote machine");
        } else {
            NET_ThrowNew(env, err, "connect");
        }
        // return value not important.
    }
    return rv;
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    waitForConnect
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_waitForConnect
  (JNIEnv *env, jclass clazz, jint fd, jint timeout) {
    int rv, retry;
    int optlen = sizeof(rv);
    fd_set wr, ex;
    struct timeval t;

    FD_ZERO(&wr);
    FD_ZERO(&ex);
    FD_SET(fd, &wr);
    FD_SET(fd, &ex);
    t.tv_sec = timeout / 1000;
    t.tv_usec = (timeout % 1000) * 1000;

    /*
     * Wait for timeout, connection established or
     * connection failed.
     */
    rv = select(fd+1, 0, &wr, &ex, &t);

    /*
     * Timeout before connection is established/failed so
     * we throw exception and shutdown input/output to prevent
     * socket from being used.
     * The socket should be closed immediately by the caller.
     */
    if (rv == 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                        "connect timed out");
        shutdown(fd, SD_BOTH);
        return;
    }

    /*
     * Socket is writable or error occurred. On some Windows editions
     * the socket will appear writable when the connect fails so we
     * check for error rather than writable.
     */
    if (!FD_ISSET(fd, &ex)) {
        return;         /* connection established */
    }

    /*
     * Connection failed. The logic here is designed to work around
     * bug on Windows NT whereby using getsockopt to obtain the
     * last error (SO_ERROR) indicates there is no error. The workaround
     * on NT is to allow winsock to be scheduled and this is done by
     * yielding and retrying. As yielding is problematic in heavy
     * load conditions we attempt up to 3 times to get the error reason.
     */
    for (retry = 0; retry < 3; retry++) {
        NET_GetSockOpt(fd, SOL_SOCKET, SO_ERROR,
                       (char*)&rv, &optlen);
        if (rv) {
            break;
        }
        Sleep(0);
    }

    if (rv == 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Unable to establish connection");
    } else if (!ipv6_available() && rv == WSAEADDRNOTAVAIL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "ConnectException",
                        "connect: Address is invalid on local machine,"
                        " or port is not valid on remote machine");
    } else {
        NET_ThrowNew(env, rv, "connect");
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    localPort0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_java_net_PlainSocketImpl_localPort0
  (JNIEnv *env, jclass clazz, jint fd) {
    SOCKETADDRESS sa;
    int len = sizeof(sa);

    if (getsockname(fd, &sa.sa, &len) == SOCKET_ERROR) {
        if (WSAGetLastError() == WSAENOTSOCK) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                    "Socket closed");
        } else {
            NET_ThrowNew(env, WSAGetLastError(), "getsockname failed");
        }
        return -1;
    }
    return (int) ntohs((u_short)GET_PORT(&sa));
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    localAddress
 * Signature: (ILjava/net/InetAddressContainer;)V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_localAddress
  (JNIEnv *env, jclass clazz, jint fd, jobject iaContainerObj) {
    int port;
    SOCKETADDRESS sa;
    int len = sizeof(sa);
    jobject iaObj;
    jclass iaContainerClass;
    jfieldID iaFieldID;

    if (getsockname(fd, &sa.sa, &len) == SOCKET_ERROR) {
        NET_ThrowNew(env, WSAGetLastError(), "Error getting socket name");
        return;
    }
    iaObj = NET_SockaddrToInetAddress(env, &sa, &port);
    CHECK_NULL(iaObj);

    iaContainerClass = (*env)->GetObjectClass(env, iaContainerObj);
    iaFieldID = (*env)->GetFieldID(env, iaContainerClass, "addr", "Ljava/net/InetAddress;");
    CHECK_NULL(iaFieldID);
    (*env)->SetObjectField(env, iaContainerObj, iaFieldID, iaObj);
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    listen0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_listen0
  (JNIEnv *env, jclass clazz, jint fd, jint backlog) {
    if (listen(fd, backlog) == SOCKET_ERROR) {
        NET_ThrowNew(env, WSAGetLastError(), "listen failed");
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    accept0
 * Signature: (I[Ljava/net/InetSocketAddress;)I
 */
JNIEXPORT jint JNICALL Java_java_net_PlainSocketImpl_accept0
  (JNIEnv *env, jclass clazz, jint fd, jobjectArray isaa) {
    int newfd, port = 0;
    jobject isa;
    jobject ia;
    SOCKETADDRESS sa;
    int len = sizeof(sa);

    memset((char *)&sa, 0, len);
    newfd = accept(fd, &sa.sa, &len);

    if (newfd == INVALID_SOCKET) {
        NET_ThrowNew(env, WSAGetLastError(), "accept failed");
        return -1;
    }

    SetHandleInformation((HANDLE)(UINT_PTR)newfd, HANDLE_FLAG_INHERIT, 0);

    ia = NET_SockaddrToInetAddress(env, &sa, &port);
    if (ia == NULL){
        closesocket(newfd);
        return -1;
    }
    isa = (*env)->NewObject(env, isa_class, isa_ctorID, ia, port);
    if (isa == NULL) {
        closesocket(newfd);
        return -1;
    }
    (*env)->SetObjectArrayElement(env, isaa, 0, isa);

    return newfd;
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    waitForNewConnection
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_waitForNewConnection
  (JNIEnv *env, jclass clazz, jint fd, jint timeout) {
    int rv;

    rv = NET_Timeout(fd, timeout);
    if (rv == 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                        "Accept timed out");
    } else if (rv == -1) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "socket closed");
    } else if (rv == -2) {
        JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                        "operation interrupted");
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    available0
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_java_net_PlainSocketImpl_available0
  (JNIEnv *env, jclass clazz, jint fd) {
    jint available = -1;

    if ((ioctlsocket(fd, FIONREAD, &available)) == SOCKET_ERROR) {
        NET_ThrowNew(env, WSAGetLastError(), "socket available");
    }

    return available;
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    close0
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_close0
  (JNIEnv *env, jclass clazz, jint fd) {
     NET_SocketClose(fd);
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    shutdown0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_shutdown0
  (JNIEnv *env, jclass clazz, jint fd, jint howto) {
    shutdown(fd, howto);
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    setIntOption
 * Signature: (III)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_setIntOption
  (JNIEnv *env, jclass clazz, jint fd, jint cmd, jint value)
{
    int level = 0, opt = 0;
    struct linger linger = {0, 0};
    char *parg;
    int arglen;

    if (NET_MapSocketOption(cmd, &level, &opt) < 0) {
        JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
        return;
    }

    if (opt == java_net_SocketOptions_SO_LINGER) {
        parg = (char *)&linger;
        arglen = sizeof(linger);
        if (value >= 0) {
            linger.l_onoff = 1;
            linger.l_linger = (unsigned short)value;
        } else {
            linger.l_onoff = 0;
            linger.l_linger = 0;
        }
    } else {
        parg = (char *)&value;
        arglen = sizeof(value);
    }

    if (NET_SetSockOpt(fd, level, opt, parg, arglen) < 0) {
        NET_ThrowNew(env, WSAGetLastError(), "setsockopt");
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    setSoTimeout0
 * Signature: (II)V
 */
JNIEXPORT void JNICALL
Java_java_net_PlainSocketImpl_setSoTimeout0
  (JNIEnv *env, jclass clazz, jint fd, jint timeout)
{
    /*
     * SO_TIMEOUT is the socket option used to specify the timeout
     * for ServerSocket.accept and Socket.getInputStream().read.
     * It does not typically map to a native level socket option.
     * For Windows we special-case this and use the SOL_SOCKET/SO_RCVTIMEO
     * socket option to specify a receive timeout on the socket. This
     * receive timeout is applicable to Socket only and the socket
     * option should not be set on ServerSocket.
     */

    /*
     * SO_RCVTIMEO is only supported on Microsoft's implementation
     * of Windows Sockets so if WSAENOPROTOOPT returned then
     * reset flag and timeout will be implemented using
     * select() -- see SocketInputStream.socketRead.
     */
    if (isRcvTimeoutSupported) {
        /*
         * Disable SO_RCVTIMEO if timeout is <= 5 second.
         */
        if (timeout <= 5000) {
            timeout = 0;
        }

        if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, (char *)&timeout,
            sizeof(timeout)) < 0) {
            int err = WSAGetLastError();
            if (err == WSAENOPROTOOPT) {
                isRcvTimeoutSupported = JNI_FALSE;
            } else {
                NET_ThrowNew(env, err, "setsockopt SO_RCVTIMEO");
            }
        }
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    getIntOption
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_java_net_PlainSocketImpl_getIntOption
  (JNIEnv *env, jclass clazz, jint fd, jint cmd)
{
    int level = 0, opt = 0;
    int result = 0;
    struct linger linger = {0, 0};
    char *arg;
    int arglen;

    if (NET_MapSocketOption(cmd, &level, &opt) < 0) {
        JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
        return -1;
    }

    if (opt == java_net_SocketOptions_SO_LINGER) {
        arg = (char *)&linger;
        arglen = sizeof(linger);
    } else {
        arg = (char *)&result;
        arglen = sizeof(result);
    }

    if (NET_GetSockOpt(fd, level, opt, arg, &arglen) < 0) {
        NET_ThrowNew(env, WSAGetLastError(), "getsockopt");
        return -1;
    }

    if (opt == java_net_SocketOptions_SO_LINGER)
        return linger.l_onoff ? linger.l_linger : -1;
    else
        return result;
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    sendOOB
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_sendOOB
  (JNIEnv *env, jclass clazz, jint fd, jint data) {
    jint n;
    unsigned char d = (unsigned char) data & 0xff;

    n = send(fd, (char *)&data, 1, MSG_OOB);
    if (n == SOCKET_ERROR) {
        NET_ThrowNew(env, WSAGetLastError(), "send");
    }
}

/*
 * Class:     java_net_PlainSocketImpl
 * Method:    configureBlocking
 * Signature: (IZ)V
 */
JNIEXPORT void JNICALL Java_java_net_PlainSocketImpl_configureBlocking
  (JNIEnv *env, jclass clazz, jint fd, jboolean blocking) {
    u_long arg;
    int result;

    if (blocking == JNI_TRUE) {
        arg = SET_BLOCKING;      // 0
    } else {
        arg = SET_NONBLOCKING;   // 1
    }

    result = ioctlsocket(fd, FIONBIO, &arg);
    if (result == SOCKET_ERROR) {
        NET_ThrowNew(env, WSAGetLastError(), "configureBlocking");
    }
}
