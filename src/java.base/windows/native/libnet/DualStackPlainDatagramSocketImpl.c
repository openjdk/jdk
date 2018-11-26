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

#include "java_net_DualStackPlainDatagramSocketImpl.h"

/*
 * This function "purges" all outstanding ICMP port unreachable packets
 * outstanding on a socket and returns JNI_TRUE if any ICMP messages
 * have been purged. The rational for purging is to emulate normal BSD
 * behaviour whereby receiving a "connection reset" status resets the
 * socket.
 */
static jboolean purgeOutstandingICMP(JNIEnv *env, jint fd)
{
    jboolean got_icmp = JNI_FALSE;
    char buf[1];
    fd_set tbl;
    struct timeval t = { 0, 0 };
    SOCKETADDRESS rmtaddr;
    int addrlen = sizeof(rmtaddr);

    /*
     * Peek at the queue to see if there is an ICMP port unreachable. If there
     * is then receive it.
     */
    FD_ZERO(&tbl);
    FD_SET(fd, &tbl);
    while(1) {
        if (select(/*ignored*/fd+1, &tbl, 0, 0, &t) <= 0) {
            break;
        }
        if (recvfrom(fd, buf, 1, MSG_PEEK,
                     &rmtaddr.sa, &addrlen) != SOCKET_ERROR) {
            break;
        }
        if (WSAGetLastError() != WSAECONNRESET) {
            /* some other error - we don't care here */
            break;
        }

        recvfrom(fd, buf, 1, 0, &rmtaddr.sa, &addrlen);
        got_icmp = JNI_TRUE;
    }

    return got_icmp;
}

static jfieldID IO_fd_fdID = NULL;
static jfieldID pdsi_fdID = NULL;

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_initIDs
  (JNIEnv *env, jclass clazz)
{
    pdsi_fdID = (*env)->GetFieldID(env, clazz, "fd",
                                   "Ljava/io/FileDescriptor;");
    CHECK_NULL(pdsi_fdID);
    IO_fd_fdID = NET_GetFileDescriptorID(env);
    CHECK_NULL(IO_fd_fdID);
    JNU_CHECK_EXCEPTION(env);

    initInetAddressIDs(env);
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketCreate
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_socketCreate
  (JNIEnv *env, jclass clazz) {
    int fd, rv, opt=0, t=TRUE;
    DWORD x1, x2; /* ignored result codes */

    fd = (int) socket(AF_INET6, SOCK_DGRAM, 0);
    if (fd == INVALID_SOCKET) {
        NET_ThrowNew(env, WSAGetLastError(), "Socket creation failed");
        return -1;
    }

    rv = setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, (char *) &opt, sizeof(opt));
    if (rv == SOCKET_ERROR) {
        NET_ThrowNew(env, WSAGetLastError(), "Socket creation failed");
        closesocket(fd);
        return -1;
    }

    SetHandleInformation((HANDLE)(UINT_PTR)fd, HANDLE_FLAG_INHERIT, FALSE);
    NET_SetSockOpt(fd, SOL_SOCKET, SO_BROADCAST, (char*)&t, sizeof(BOOL));

    /* SIO_UDP_CONNRESET fixes a "bug" introduced in Windows 2000, which
     * returns connection reset errors on unconnected UDP sockets (as well
     * as connected sockets). The solution is to only enable this feature
     * when the socket is connected.
     */
    t = FALSE;
    WSAIoctl(fd, SIO_UDP_CONNRESET, &t, sizeof(t), &x1, sizeof(x1), &x2, 0, 0);

    return fd;
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketBind
 * Signature: (ILjava/net/InetAddress;I)V
 */
JNIEXPORT void JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_socketBind
  (JNIEnv *env, jclass clazz, jint fd, jobject iaObj, jint port, jboolean exclBind) {
    SOCKETADDRESS sa;
    int rv, sa_len = 0;

    if (NET_InetAddressToSockaddr(env, iaObj, port, &sa,
                                 &sa_len, JNI_TRUE) != 0) {
        return;
    }
    rv = NET_WinBind(fd, &sa, sa_len, exclBind);

    if (rv == SOCKET_ERROR) {
        if (WSAGetLastError() == WSAEACCES) {
            WSASetLastError(WSAEADDRINUSE);
        }
        NET_ThrowNew(env, WSAGetLastError(), "Cannot bind");
    }
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketConnect
 * Signature: (ILjava/net/InetAddress;I)V
 */
JNIEXPORT void JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_socketConnect
  (JNIEnv *env, jclass clazz, jint fd, jobject iaObj, jint port) {
    SOCKETADDRESS sa;
    int rv, sa_len = 0, t;
    DWORD x1, x2; /* ignored result codes */

    if (NET_InetAddressToSockaddr(env, iaObj, port, &sa,
                                   &sa_len, JNI_TRUE) != 0) {
        return;
    }

    rv = connect(fd, &sa.sa, sa_len);
    if (rv == SOCKET_ERROR) {
        NET_ThrowNew(env, WSAGetLastError(), "connect");
        return;
    }

    /* see comment in socketCreate */
    t = TRUE;
    WSAIoctl(fd, SIO_UDP_CONNRESET, &t, sizeof(t), &x1, sizeof(x1), &x2, 0, 0);
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketDisconnect
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_socketDisconnect
  (JNIEnv *env, jclass clazz, jint fd ) {
    SOCKETADDRESS sa;
    int sa_len = sizeof(sa);
    DWORD x1, x2; /* ignored result codes */
    int t = FALSE;

    memset(&sa, 0, sa_len);
    connect(fd, &sa.sa, sa_len);

    /* see comment in socketCreate */
    WSAIoctl(fd, SIO_UDP_CONNRESET, &t, sizeof(t), &x1, sizeof(x1), &x2, 0, 0);
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketClose
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_socketClose
  (JNIEnv *env, jclass clazz , jint fd) {
    NET_SocketClose(fd);
}


/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketLocalPort
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_socketLocalPort
  (JNIEnv *env, jclass clazz, jint fd) {
    SOCKETADDRESS sa;
    int len = sizeof(sa);

    if (getsockname(fd, &sa.sa, &len) == SOCKET_ERROR) {
        NET_ThrowNew(env, WSAGetLastError(), "getsockname");
        return -1;
    }
    return (int) ntohs((u_short)GET_PORT(&sa));
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketLocalAddress
 * Signature: (I)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_socketLocalAddress
  (JNIEnv *env , jclass clazz, jint fd) {
    SOCKETADDRESS sa;
    int len = sizeof(sa);
    jobject iaObj;
    int port;

    if (getsockname(fd, &sa.sa, &len) == SOCKET_ERROR) {
        NET_ThrowNew(env, WSAGetLastError(), "Error getting socket name");
        return NULL;
    }

    iaObj = NET_SockaddrToInetAddress(env, &sa, &port);
    return iaObj;
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketReceiveOrPeekData
 * Signature: (ILjava/net/DatagramPacket;IZZ)I
 */
JNIEXPORT jint JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_socketReceiveOrPeekData
  (JNIEnv *env, jclass clazz, jint fd, jobject dpObj,
   jint timeout, jboolean connected, jboolean peek) {
    SOCKETADDRESS sa;
    int sa_len = sizeof(sa);
    int port, rv, flags=0;
    char BUF[MAX_BUFFER_LEN];
    char *fullPacket;
    BOOL retry;
    jlong prevTime = 0;

    jint packetBufferOffset, packetBufferLen;
    jbyteArray packetBuffer;

    /* if we are only peeking. Called from peekData */
    if (peek) {
        flags = MSG_PEEK;
    }

    packetBuffer = (*env)->GetObjectField(env, dpObj, dp_bufID);
    packetBufferOffset = (*env)->GetIntField(env, dpObj, dp_offsetID);
    packetBufferLen = (*env)->GetIntField(env, dpObj, dp_bufLengthID);
    /* Note: the buffer needn't be greater than 65,536 (0xFFFF)
    * the max size of an IP packet. Anything bigger is truncated anyway.
    */
    if (packetBufferLen > MAX_PACKET_LEN) {
        packetBufferLen = MAX_PACKET_LEN;
    }

    if (packetBufferLen > MAX_BUFFER_LEN) {
        fullPacket = (char *)malloc(packetBufferLen);
        if (!fullPacket) {
            JNU_ThrowOutOfMemoryError(env, "Native heap allocation failed");
            return -1;
        }
    } else {
        fullPacket = &(BUF[0]);
    }

    do {
        retry = FALSE;

        if (timeout) {
            if (prevTime == 0) {
                prevTime = JVM_CurrentTimeMillis(env, 0);
            }
            rv = NET_Timeout(fd, timeout);
            if (rv <= 0) {
                if (rv == 0) {
                    JNU_ThrowByName(env,JNU_JAVANETPKG "SocketTimeoutException",
                                    "Receive timed out");
                } else if (rv == -1) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                    "Socket closed");
                }
                if (packetBufferLen > MAX_BUFFER_LEN) {
                    free(fullPacket);
                }
                return -1;
            }
        }

        /* receive the packet */
        rv = recvfrom(fd, fullPacket, packetBufferLen, flags,
                      &sa.sa, &sa_len);

        if (rv == SOCKET_ERROR && (WSAGetLastError() == WSAECONNRESET)) {
            /* An icmp port unreachable - we must receive this as Windows
             * does not reset the state of the socket until this has been
             * received.
             */
            purgeOutstandingICMP(env, fd);

            if (connected) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "PortUnreachableException",
                                "ICMP Port Unreachable");
                if (packetBufferLen > MAX_BUFFER_LEN)
                    free(fullPacket);
                return -1;
            } else if (timeout) {
                /* Adjust timeout */
                jlong newTime = JVM_CurrentTimeMillis(env, 0);
                timeout -= (jint)(newTime - prevTime);
                if (timeout <= 0) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                    "Receive timed out");
                    if (packetBufferLen > MAX_BUFFER_LEN)
                        free(fullPacket);
                    return -1;
                }
                prevTime = newTime;
            }
            retry = TRUE;
        }
    } while (retry);

    port = (int) ntohs ((u_short) GET_PORT((SOCKETADDRESS *)&sa));

    /* truncate the data if the packet's length is too small */
    if (rv > packetBufferLen) {
        rv = packetBufferLen;
    }
    if (rv < 0) {
        if (WSAGetLastError() == WSAEMSGSIZE) {
            /* it is because the buffer is too small. It's UDP, it's
             * unreliable, it's all good. discard the rest of the
             * data..
             */
            rv = packetBufferLen;
        } else {
            /* failure */
            (*env)->SetIntField(env, dpObj, dp_lengthID, 0);
        }
    }

    if (rv == -1) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "socket closed");
    } else if (rv == -2) {
        JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                        "operation interrupted");
    } else if (rv < 0) {
        NET_ThrowCurrent(env, "Datagram receive failed");
    } else {
        jobject packetAddress;
        /*
         * Check if there is an InetAddress already associated with this
         * packet. If so, we check if it is the same source address. We
         * can't update any existing InetAddress because it is immutable
         */
        packetAddress = (*env)->GetObjectField(env, dpObj, dp_addressID);
        if (packetAddress != NULL) {
            if (!NET_SockaddrEqualsInetAddress(env, &sa, packetAddress)) {
                /* force a new InetAddress to be created */
                packetAddress = NULL;
            }
        }
        if (!(*env)->ExceptionCheck(env)){
            if (packetAddress == NULL ) {
                packetAddress = NET_SockaddrToInetAddress(env, &sa, &port);
                if (packetAddress != NULL) {
                    /* stuff the new InetAddress into the packet */
                    (*env)->SetObjectField(env, dpObj, dp_addressID, packetAddress);
                }
            }
            /* populate the packet */
            (*env)->SetByteArrayRegion(env, packetBuffer, packetBufferOffset, rv,
                                   (jbyte *)fullPacket);
            (*env)->SetIntField(env, dpObj, dp_portID, port);
            (*env)->SetIntField(env, dpObj, dp_lengthID, rv);
        }
    }

    if (packetBufferLen > MAX_BUFFER_LEN) {
        free(fullPacket);
    }
    return port;
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketSend
 * Signature: (I[BIILjava/net/InetAddress;IZ)V
 */
JNIEXPORT void JNICALL Java_java_net_DualStackPlainDatagramSocketImpl_socketSend
  (JNIEnv *env, jclass clazz, jint fd, jbyteArray data, jint offset, jint length,
     jobject iaObj, jint port, jboolean connected) {
    SOCKETADDRESS sa;
    int rv, sa_len = 0;
    struct sockaddr *sap = 0;
    char BUF[MAX_BUFFER_LEN];
    char *fullPacket;

    // if already connected, sap arg to sendto() is null
    if (!connected) {
        if (NET_InetAddressToSockaddr(env, iaObj, port, &sa,
                                      &sa_len, JNI_TRUE) != 0) {
            return;
        }
        sap = &sa.sa;
    }

    if (length > MAX_BUFFER_LEN) {
        /* Note: the buffer needn't be greater than 65,536 (0xFFFF)
         * the max size of an IP packet. Anything bigger is truncated anyway.
         */
        if (length > MAX_PACKET_LEN) {
            length = MAX_PACKET_LEN;
        }
        fullPacket = (char *)malloc(length);
        if (!fullPacket) {
            JNU_ThrowOutOfMemoryError(env, "Native heap allocation failed");
            return;
        }
    } else {
        fullPacket = &(BUF[0]);
    }

    (*env)->GetByteArrayRegion(env, data, offset, length,
                               (jbyte *)fullPacket);
    rv = sendto(fd, fullPacket, length, 0, sap, sa_len);
    if (rv == SOCKET_ERROR) {
        if (rv == -1) {
            NET_ThrowNew(env, WSAGetLastError(), "Datagram send failed");
        }
    }

    if (length > MAX_BUFFER_LEN) {
        free(fullPacket);
    }
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketSetIntOption
 * Signature: (III)V
 */
JNIEXPORT void JNICALL
Java_java_net_DualStackPlainDatagramSocketImpl_socketSetIntOption
  (JNIEnv *env, jclass clazz, jint fd, jint cmd, jint value)
{
    int level = 0, opt = 0;

    if (NET_MapSocketOption(cmd, &level, &opt) < 0) {
        JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
        return;
    }

    if (NET_SetSockOpt(fd, level, opt, (char *)&value, sizeof(value)) < 0) {
        NET_ThrowNew(env, WSAGetLastError(), "setsockopt");
    }
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    socketGetIntOption
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL
Java_java_net_DualStackPlainDatagramSocketImpl_socketGetIntOption
  (JNIEnv *env, jclass clazz, jint fd, jint cmd)
{
    int level = 0, opt = 0, result = 0;
    int result_len = sizeof(result);

    if (NET_MapSocketOption(cmd, &level, &opt) < 0) {
        JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
        return -1;
    }

    if (NET_GetSockOpt(fd, level, opt, (void *)&result, &result_len) < 0) {
        NET_ThrowNew(env, WSAGetLastError(), "getsockopt");
        return -1;
    }

    return result;
}

/*
 * Class:     java_net_DualStackPlainDatagramSocketImpl
 * Method:    dataAvailable
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_java_net_DualStackPlainDatagramSocketImpl_dataAvailable
  (JNIEnv *env, jobject this)
{
    SOCKET fd;
    int  rv = -1;
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);

    if (!IS_NULL(fdObj)) {
        int retval = 0;
        fd = (SOCKET)(*env)->GetIntField(env, fdObj, IO_fd_fdID);
        rv = ioctlsocket(fd, FIONREAD, &retval);
        if (retval > 0) {
            return retval;
        }
    }

    if (rv < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return -1;
    }

    return 0;
}
