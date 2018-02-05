/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
#include <malloc.h>

#include "net_util.h"
#include "NetworkInterface.h"

#include "java_net_TwoStacksPlainDatagramSocketImpl.h"
#include "java_net_SocketOptions.h"
#include "java_net_NetworkInterface.h"
#include "java_net_InetAddress.h"

#ifndef IPTOS_TOS_MASK
#define IPTOS_TOS_MASK 0x1e
#endif
#ifndef IPTOS_PREC_MASK
#define IPTOS_PREC_MASK 0xe0
#endif


#define IN_CLASSD(i)    (((long)(i) & 0xf0000000) == 0xe0000000)
#define IN_MULTICAST(i) IN_CLASSD(i)

extern int getAllInterfacesAndAddresses(JNIEnv *env, netif **netifPP);

/************************************************************************
 * TwoStacksPlainDatagramSocketImpl
 */

static jfieldID IO_fd_fdID;
static jfieldID pdsi_trafficClassID;
jfieldID pdsi_fdID;
jfieldID pdsi_fd1ID;
jfieldID pdsi_fduseID;
jfieldID pdsi_lastfdID;
jfieldID pdsi_timeoutID;

jfieldID pdsi_localPortID;
jfieldID pdsi_connected;

static jclass ia4_clazz;
static jmethodID ia4_ctor;

/*
 * Notes about UDP/IPV6 on Windows (XP and 2003 server):
 *
 * fd always points to the IPv4 fd, and fd1 points to the IPv6 fd.
 * Both fds are used when we bind to a wild-card address. When a specific
 * address is used, only one of them is used.
 */

/*
 * Returns a java.lang.Integer based on 'i'
 */
jobject createInteger(JNIEnv *env, int i) {
    static jclass i_class = NULL;
    static jmethodID i_ctrID;
    static jfieldID i_valueID;

    if (i_class == NULL) {
        jclass c = (*env)->FindClass(env, "java/lang/Integer");
        CHECK_NULL_RETURN(c, NULL);
        i_ctrID = (*env)->GetMethodID(env, c, "<init>", "(I)V");
        CHECK_NULL_RETURN(i_ctrID, NULL);
        i_class = (*env)->NewGlobalRef(env, c);
        CHECK_NULL_RETURN(i_class, NULL);
    }

    return (*env)->NewObject(env, i_class, i_ctrID, i);
}

/*
 * Returns a java.lang.Boolean based on 'b'
 */
jobject createBoolean(JNIEnv *env, int b) {
    static jclass b_class = NULL;
    static jmethodID b_ctrID;
    static jfieldID b_valueID;

    if (b_class == NULL) {
        jclass c = (*env)->FindClass(env, "java/lang/Boolean");
        CHECK_NULL_RETURN(c, NULL);
        b_ctrID = (*env)->GetMethodID(env, c, "<init>", "(Z)V");
        CHECK_NULL_RETURN(b_ctrID, NULL);
        b_class = (*env)->NewGlobalRef(env, c);
        CHECK_NULL_RETURN(b_class, NULL);
    }

    return (*env)->NewObject(env, b_class, b_ctrID, (jboolean)(b!=0));
}

static int getFD(JNIEnv *env, jobject this) {
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);

    if (fdObj == NULL) {
        return -1;
    }
    return (*env)->GetIntField(env, fdObj, IO_fd_fdID);
}

static int getFD1(JNIEnv *env, jobject this) {
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fd1ID);

    if (fdObj == NULL) {
        return -1;
    }
    return (*env)->GetIntField(env, fdObj, IO_fd_fdID);
}

/*
 * This function "purges" all outstanding ICMP port unreachable packets
 * outstanding on a socket and returns JNI_TRUE if any ICMP messages
 * have been purged. The rational for purging is to emulate normal BSD
 * behaviour whereby receiving a "connection reset" status resets the
 * socket.
 */
static jboolean purgeOutstandingICMP(JNIEnv *env, jobject this, jint fd)
{
    jboolean got_icmp = JNI_FALSE;
    char buf[1];
    fd_set tbl;
    struct timeval t = { 0, 0 };
    SOCKETADDRESS rmtaddr;
    int addrlen = sizeof(SOCKETADDRESS);

    memset((char *)&rmtaddr, 0, sizeof(rmtaddr));

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
        if (recvfrom(fd, buf, 1, MSG_PEEK, &rmtaddr.sa, &addrlen) != SOCKET_ERROR) {
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


/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_init(JNIEnv *env, jclass cls) {
    /* get fieldIDs */
    pdsi_fdID = (*env)->GetFieldID(env, cls, "fd", "Ljava/io/FileDescriptor;");
    CHECK_NULL(pdsi_fdID);
    pdsi_fd1ID = (*env)->GetFieldID(env, cls, "fd1", "Ljava/io/FileDescriptor;");
    CHECK_NULL(pdsi_fd1ID);
    pdsi_timeoutID = (*env)->GetFieldID(env, cls, "timeout", "I");
    CHECK_NULL(pdsi_timeoutID);
    pdsi_fduseID = (*env)->GetFieldID(env, cls, "fduse", "I");
    CHECK_NULL(pdsi_fduseID);
    pdsi_lastfdID = (*env)->GetFieldID(env, cls, "lastfd", "I");
    CHECK_NULL(pdsi_lastfdID);
    pdsi_trafficClassID = (*env)->GetFieldID(env, cls, "trafficClass", "I");
    CHECK_NULL(pdsi_trafficClassID);
    pdsi_localPortID = (*env)->GetFieldID(env, cls, "localPort", "I");
    CHECK_NULL(pdsi_localPortID);
    pdsi_connected = (*env)->GetFieldID(env, cls, "connected", "Z");
    CHECK_NULL(pdsi_connected);

    cls = (*env)->FindClass(env, "java/io/FileDescriptor");
    CHECK_NULL(cls);
    IO_fd_fdID = NET_GetFileDescriptorID(env);
    CHECK_NULL(IO_fd_fdID);

    ia4_clazz = (*env)->FindClass(env, "java/net/Inet4Address");
    CHECK_NULL(ia4_clazz);
    ia4_clazz = (*env)->NewGlobalRef(env, ia4_clazz);
    CHECK_NULL(ia4_clazz);
    ia4_ctor = (*env)->GetMethodID(env, ia4_clazz, "<init>", "()V");
    CHECK_NULL(ia4_ctor);
}

JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_bind0(JNIEnv *env, jobject this,
                                           jint port, jobject addressObj,
                                           jboolean exclBind) {
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    int ipv6_supported = ipv6_available();
    int fd, fd1 = -1, lcladdrlen = 0;
    jint family;
    SOCKETADDRESS lcladdr;

    family = getInetAddress_family(env, addressObj);
    JNU_CHECK_EXCEPTION(env);
    if (family == java_net_InetAddress_IPv6 && !ipv6_supported) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Protocol family not supported");
        return;
    }
    if (IS_NULL(fdObj) || (ipv6_supported && IS_NULL(fd1Obj))) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "socket closed");
        return;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
        if (ipv6_supported) {
            fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
        }
    }

    if (IS_NULL(addressObj)) {
        JNU_ThrowNullPointerException(env, "argument address");
        return;
    }

    if (NET_InetAddressToSockaddr(env, addressObj, port, &lcladdr,
                                  &lcladdrlen, JNI_FALSE) != 0) {
        return;
    }

    if (ipv6_supported) {
        struct ipv6bind v6bind;
        v6bind.addr = &lcladdr;
        v6bind.ipv4_fd = fd;
        v6bind.ipv6_fd = fd1;
        if (NET_BindV6(&v6bind, exclBind) != -1) {
            /* check if the fds have changed */
            if (v6bind.ipv4_fd != fd) {
                fd = v6bind.ipv4_fd;
                if (fd == -1) {
                    /* socket is closed. */
                    (*env)->SetObjectField(env, this, pdsi_fdID, NULL);
                } else {
                    /* socket was re-created */
                    (*env)->SetIntField(env, fdObj, IO_fd_fdID, fd);
                }
            }
            if (v6bind.ipv6_fd != fd1) {
                fd1 = v6bind.ipv6_fd;
                if (fd1 == -1) {
                    /* socket is closed. */
                    (*env)->SetObjectField(env, this, pdsi_fd1ID, NULL);
                } else {
                    /* socket was re-created */
                    (*env)->SetIntField(env, fd1Obj, IO_fd_fdID, fd1);
                }
            }
        } else {
            /* NET_BindV6() closes both sockets upon a failure */
            (*env)->SetObjectField(env, this, pdsi_fdID, NULL);
            (*env)->SetObjectField(env, this, pdsi_fd1ID, NULL);
            NET_ThrowCurrent (env, "Cannot bind");
            return;
        }
    } else {
        if (NET_WinBind(fd, &lcladdr, lcladdrlen, exclBind) == -1) {
            if (WSAGetLastError() == WSAEACCES) {
                WSASetLastError(WSAEADDRINUSE);
            }
            (*env)->SetObjectField(env, this, pdsi_fdID, NULL);
            NET_ThrowCurrent(env, "Cannot bind");
            closesocket(fd);
            return;
        }
    }

    if (port == 0) {
        if (getsockname(fd == -1 ? fd1 : fd, &lcladdr.sa, &lcladdrlen) == -1) {
            NET_ThrowCurrent(env, "getsockname");
            return;
        }
        port = ntohs((u_short)GET_PORT(&lcladdr));
    }
    (*env)->SetIntField(env, this, pdsi_localPortID, port);
}


/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    connect0
 * Signature: (Ljava/net/InetAddress;I)V
 */

JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_connect0
  (JNIEnv *env, jobject this, jobject address, jint port)
{
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    jint fd = -1, fd1 = -1, fdc, family;
    SOCKETADDRESS rmtaddr;
    int rmtaddrlen = 0;
    DWORD x1, x2; /* ignored result codes */
    int res, t;

    if (IS_NULL(fdObj) && IS_NULL(fd1Obj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    }

    if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }

    if (!IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
    }

    if (IS_NULL(address)) {
        JNU_ThrowNullPointerException(env, "address");
        return;
    }

    family = getInetAddress_family(env, address);
    JNU_CHECK_EXCEPTION(env);
    if (family == java_net_InetAddress_IPv6 && !ipv6_available()) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Protocol family not supported");
        return;
    }

    fdc = family == java_net_InetAddress_IPv4 ? fd : fd1;

    /* SIO_UDP_CONNRESET fixes a bug introduced in Windows 2000, which
     * returns connection reset errors on connected UDP sockets (as well
     * as connected sockets). The solution is to only enable this feature
     * when the socket is connected
     */
    t = TRUE;
    res = WSAIoctl(fdc, SIO_UDP_CONNRESET, &t, sizeof(t), &x1, sizeof(x1), &x2, 0, 0);

    if (NET_InetAddressToSockaddr(env, address, port, &rmtaddr,
                                  &rmtaddrlen, JNI_FALSE) != 0) {
        return;
    }

    if (connect(fdc, &rmtaddr.sa, rmtaddrlen) == -1) {
        NET_ThrowCurrent(env, "connect");
        return;
    }
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    disconnect0
 * Signature: ()V
 */

JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_disconnect0(JNIEnv *env, jobject this, jint family) {
    /* The object's field */
    jobject fdObj;
    /* The fdObj'fd */
    jint fd, len;
    SOCKETADDRESS addr;
    DWORD x1 = 0, x2 = 0; /* ignored result codes */
    int t;

    if (family == java_net_InetAddress_IPv4) {
        fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
        len = sizeof(struct sockaddr_in);
    } else {
        fdObj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
        len = sizeof(struct sockaddr_in6);
    }

    if (IS_NULL(fdObj)) {
        /* disconnect doesn't throw any exceptions */
        return;
    }
    fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);

    memset((char *)&addr, 0, len);
    connect(fd, &addr.sa, len);

    /*
     * use SIO_UDP_CONNRESET
     * to disable ICMP port unreachable handling here.
     */
    t = FALSE;
    WSAIoctl(fd, SIO_UDP_CONNRESET, &t, sizeof(t), &x1, sizeof(x1), &x2, 0, 0);
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    send
 * Signature: (Ljava/net/DatagramPacket;)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_send
  (JNIEnv *env, jobject this, jobject packet)
{
    char BUF[MAX_BUFFER_LEN];
    char *fullPacket;
    jobject fdObj;
    jint fd;

    jobject iaObj;
    jint family;

    jint packetBufferOffset, packetBufferLen, packetPort;
    jbyteArray packetBuffer;
    jboolean connected;

    SOCKETADDRESS rmtaddr;
    struct sockaddr *addrp = 0;
    int addrlen = 0;

    if (IS_NULL(packet)) {
        JNU_ThrowNullPointerException(env, "null packet");
        return;
    }

    iaObj = (*env)->GetObjectField(env, packet, dp_addressID);

    packetPort = (*env)->GetIntField(env, packet, dp_portID);
    packetBufferOffset = (*env)->GetIntField(env, packet, dp_offsetID);
    packetBuffer = (jbyteArray)(*env)->GetObjectField(env, packet, dp_bufID);
    connected = (*env)->GetBooleanField(env, this, pdsi_connected);

    if (IS_NULL(iaObj) || IS_NULL(packetBuffer)) {
        JNU_ThrowNullPointerException(env, "null address || null buffer");
        return;
    }

    family = getInetAddress_family(env, iaObj);
    JNU_CHECK_EXCEPTION(env);
    if (family == java_net_InetAddress_IPv4) {
        fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    } else {
        if (!ipv6_available()) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Protocol not allowed");
            return;
        }
        fdObj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    }

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    }
    fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);

    packetBufferLen = (*env)->GetIntField(env, packet, dp_lengthID);
    /* Note: the buffer needn't be greater than 65,536 (0xFFFF)...
     * the maximum size of an IP packet. Anything bigger is truncated anyway.
     */
    if (packetBufferLen > MAX_PACKET_LEN) {
        packetBufferLen = MAX_PACKET_LEN;
    }

    // sockaddr arg to sendto() is null if already connected
    if (!connected) {
        if (NET_InetAddressToSockaddr(env, iaObj, packetPort, &rmtaddr,
                                      &addrlen, JNI_FALSE) != 0) {
            return;
        }
        addrp = &rmtaddr.sa;
    }

    if (packetBufferLen > MAX_BUFFER_LEN) {
        /* When JNI-ifying the JDK's IO routines, we turned
         * reads and writes of byte arrays of size greater
         * than 2048 bytes into several operations of size 2048.
         * This saves a malloc()/memcpy()/free() for big
         * buffers.  This is OK for file IO and TCP, but that
         * strategy violates the semantics of a datagram protocol.
         * (one big send) != (several smaller sends).  So here
         * we *must* alloc the buffer.  Note it needn't be bigger
         * than 65,536 (0xFFFF) the max size of an IP packet.
         * anything bigger is truncated anyway.
         */
        fullPacket = (char *)malloc(packetBufferLen);
        if (!fullPacket) {
            JNU_ThrowOutOfMemoryError(env, "Send buf native heap allocation failed");
            return;
        }
    } else {
        fullPacket = &(BUF[0]);
    }

    (*env)->GetByteArrayRegion(env, packetBuffer, packetBufferOffset,
                               packetBufferLen, (jbyte *)fullPacket);
    if (sendto(fd, fullPacket, packetBufferLen, 0, addrp,
               addrlen) == SOCKET_ERROR)
    {
        NET_ThrowCurrent(env, "Datagram send failed");
    }

    if (packetBufferLen > MAX_BUFFER_LEN) {
        free(fullPacket);
    }
}

/*
 * check which socket was last serviced when there was data on both sockets.
 * Only call this if sure that there is data on both sockets.
 */
static int checkLastFD (JNIEnv *env, jobject this, int fd, int fd1) {
    int nextfd, lastfd = (*env)->GetIntField(env, this, pdsi_lastfdID);
    if (lastfd == -1) {
        /* arbitrary. Choose fd */
        (*env)->SetIntField(env, this, pdsi_lastfdID, fd);
        return fd;
    } else {
        if (lastfd == fd) {
            nextfd = fd1;
        } else {
            nextfd = fd;
        }
        (*env)->SetIntField(env, this, pdsi_lastfdID, nextfd);
        return nextfd;
    }
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    peek
 * Signature: (Ljava/net/InetAddress;)I
 */
JNIEXPORT jint JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_peek(JNIEnv *env, jobject this,
                                           jobject addressObj) {

    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jint timeout = (*env)->GetIntField(env, this, pdsi_timeoutID);
    jint fd;

    /* The address and family fields of addressObj */
    jint address, family;

    int n;
    SOCKETADDRESS remote_addr;
    jint remote_addrsize = sizeof(SOCKETADDRESS);
    char buf[1];
    BOOL retry;
    jlong prevTime = 0;

    if (IS_NULL(fdObj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
        return -1;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
        if (fd < 0) {
           JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                           "socket closed");
           return -1;
        }
    }
    if (IS_NULL(addressObj)) {
        JNU_ThrowNullPointerException(env, "Null address in peek()");
        return -1;
    } else {
        address = getInetAddress_addr(env, addressObj);
        JNU_CHECK_EXCEPTION_RETURN(env, -1);
        /* We only handle IPv4 for now. Will support IPv6 once its in the os */
        family = AF_INET;
    }

    do {
        retry = FALSE;

        /*
         * If a timeout has been specified then we select on the socket
         * waiting for a read event or a timeout.
         */
        if (timeout) {
            int ret;
            prevTime = JVM_CurrentTimeMillis(env, 0);
            ret = NET_Timeout (fd, timeout);
            if (ret == 0) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                "Peek timed out");
                return ret;
            } else if (ret == -1) {
                NET_ThrowCurrent(env, "timeout in datagram socket peek");
                return ret;
            }
        }

        /* now try the peek */
        n = recvfrom(fd, buf, 1, MSG_PEEK, &remote_addr.sa, &remote_addrsize);

        if (n == SOCKET_ERROR) {
            if (WSAGetLastError() == WSAECONNRESET) {
                jboolean connected;

                /*
                 * An icmp port unreachable - we must receive this as Windows
                 * does not reset the state of the socket until this has been
                 * received.
                 */
                purgeOutstandingICMP(env, this, fd);

                connected =  (*env)->GetBooleanField(env, this, pdsi_connected);
                if (connected) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "PortUnreachableException",
                                       "ICMP Port Unreachable");
                    return 0;
                }

                /*
                 * If a timeout was specified then we need to adjust it because
                 * we may have used up some of the timeout befor the icmp port
                 * unreachable arrived.
                 */
                if (timeout) {
                    jlong newTime = JVM_CurrentTimeMillis(env, 0);
                    timeout -= (jint)(newTime - prevTime);
                    if (timeout <= 0) {
                        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                "Receive timed out");
                        return 0;
                    }
                    prevTime = newTime;
                }

                /* Need to retry the recv */
                retry = TRUE;
            }
        }
    } while (retry);

    if (n == SOCKET_ERROR && WSAGetLastError() != WSAEMSGSIZE) {
        NET_ThrowCurrent(env, "Datagram peek failed");
        return 0;
    }
    setInetAddress_addr(env, addressObj, ntohl(remote_addr.sa4.sin_addr.s_addr));
    JNU_CHECK_EXCEPTION_RETURN(env, -1);
    setInetAddress_family(env, addressObj, java_net_InetAddress_IPv4);
    JNU_CHECK_EXCEPTION_RETURN(env, -1);

    /* return port */
    return ntohs(remote_addr.sa4.sin_port);
}

JNIEXPORT jint JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_peekData(JNIEnv *env, jobject this,
                                           jobject packet) {

     char BUF[MAX_BUFFER_LEN];
    char *fullPacket;
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    jint timeout = (*env)->GetIntField(env, this, pdsi_timeoutID);

    jbyteArray packetBuffer;
    jint packetBufferOffset, packetBufferLen;

    int fd = -1, fd1 = -1, fduse, nsockets = 0, errorCode;
    int port;

    int checkBoth = 0;
    int n;
    SOCKETADDRESS remote_addr;
    jint remote_addrsize = sizeof(SOCKETADDRESS);
    BOOL retry;
    jlong prevTime = 0;

    if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
        if (fd < 0) {
           JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                           "socket closed");
           return -1;
        }
        nsockets = 1;
    }

    if (!IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
        if (fd1 < 0) {
           JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                           "socket closed");
           return -1;
        }
        nsockets ++;
    }

    switch (nsockets) {
      case 0:
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                       "socket closed");
        return -1;
      case 1:
        if (!IS_NULL(fdObj)) {
           fduse = fd;
        } else {
           fduse = fd1;
        }
        break;
      case 2:
        checkBoth = TRUE;
        break;
    }

    if (IS_NULL(packet)) {
        JNU_ThrowNullPointerException(env, "packet");
        return -1;
    }

    packetBuffer = (*env)->GetObjectField(env, packet, dp_bufID);

    if (IS_NULL(packetBuffer)) {
        JNU_ThrowNullPointerException(env, "packet buffer");
        return -1;
    }

    packetBufferOffset = (*env)->GetIntField(env, packet, dp_offsetID);
    packetBufferLen = (*env)->GetIntField(env, packet, dp_bufLengthID);

    if (packetBufferLen > MAX_BUFFER_LEN) {

        /* When JNI-ifying the JDK's IO routines, we turned
         * read's and write's of byte arrays of size greater
         * than 2048 bytes into several operations of size 2048.
         * This saves a malloc()/memcpy()/free() for big
         * buffers.  This is OK for file IO and TCP, but that
         * strategy violates the semantics of a datagram protocol.
         * (one big send) != (several smaller sends).  So here
         * we *must* alloc the buffer.  Note it needn't be bigger
         * than 65,536 (0xFFFF) the max size of an IP packet.
         * anything bigger is truncated anyway.
         */
        fullPacket = (char *)malloc(packetBufferLen);
        if (!fullPacket) {
            JNU_ThrowOutOfMemoryError(env, "Native heap allocation failed");
            return -1;
        }
    } else {
        fullPacket = &(BUF[0]);
    }

    do {
        int ret;
        retry = FALSE;

        /*
         * If a timeout has been specified then we select on the socket
         * waiting for a read event or a timeout.
         */
        if (checkBoth) {
            int t = timeout == 0 ? -1: timeout;
            prevTime = JVM_CurrentTimeMillis(env, 0);
            ret = NET_Timeout2 (fd, fd1, t, &fduse);
            /* all subsequent calls to recv() or select() will use the same fd
             * for this call to peek() */
            if (ret <= 0) {
                if (ret == 0) {
                    JNU_ThrowByName(env,JNU_JAVANETPKG "SocketTimeoutException",
                                        "Peek timed out");
                } else if (ret == -1) {
                    NET_ThrowCurrent(env, "timeout in datagram socket peek");
                }
                if (packetBufferLen > MAX_BUFFER_LEN) {
                    free(fullPacket);
                }
                return -1;
            }
            if (ret == 2) {
                fduse = checkLastFD (env, this, fd, fd1);
            }
            checkBoth = FALSE;
        } else if (timeout) {
            if (prevTime == 0) {
                prevTime = JVM_CurrentTimeMillis(env, 0);
            }
            ret = NET_Timeout (fduse, timeout);
            if (ret <= 0) {
                if (ret == 0) {
                    JNU_ThrowByName(env,JNU_JAVANETPKG "SocketTimeoutException",
                                    "Receive timed out");
                } else if (ret == -1) {
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
        n = recvfrom(fduse, fullPacket, packetBufferLen, MSG_PEEK,
                     &remote_addr.sa, &remote_addrsize);
        port = (int) ntohs ((u_short) GET_PORT((SOCKETADDRESS *)&remote_addr));
        if (n == SOCKET_ERROR) {
            if (WSAGetLastError() == WSAECONNRESET) {
                jboolean connected;

                /*
                 * An icmp port unreachable - we must receive this as Windows
                 * does not reset the state of the socket until this has been
                 * received.
                 */
                purgeOutstandingICMP(env, this, fduse);

                connected = (*env)->GetBooleanField(env, this, pdsi_connected);
                if (connected) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "PortUnreachableException",
                                       "ICMP Port Unreachable");

                    if (packetBufferLen > MAX_BUFFER_LEN) {
                        free(fullPacket);
                    }
                    return -1;
                }

                /*
                 * If a timeout was specified then we need to adjust it because
                 * we may have used up some of the timeout befor the icmp port
                 * unreachable arrived.
                 */
                if (timeout) {
                    jlong newTime = JVM_CurrentTimeMillis(env, 0);
                    timeout -= (jint)(newTime - prevTime);
                    if (timeout <= 0) {
                        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                "Receive timed out");
                        if (packetBufferLen > MAX_BUFFER_LEN) {
                            free(fullPacket);
                        }
                        return -1;
                    }
                    prevTime = newTime;
                }
                retry = TRUE;
            }
        }
    } while (retry);

    /* truncate the data if the packet's length is too small */
    if (n > packetBufferLen) {
        n = packetBufferLen;
    }
    if (n < 0) {
        errorCode = WSAGetLastError();
        /* check to see if it's because the buffer was too small */
        if (errorCode == WSAEMSGSIZE) {
            /* it is because the buffer is too small. It's UDP, it's
             * unreliable, it's all good. discard the rest of the
             * data..
             */
            n = packetBufferLen;
        } else {
            /* failure */
            (*env)->SetIntField(env, packet, dp_lengthID, 0);
        }
    }
    if (n == -1) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "socket closed");
        if (packetBufferLen > MAX_BUFFER_LEN) {
            free(fullPacket);
        }
        return -1;
    } else if (n == -2) {
        JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                        "operation interrupted");
        if (packetBufferLen > MAX_BUFFER_LEN) {
            free(fullPacket);
        }
        return -1;
    } else if (n < 0) {
        NET_ThrowCurrent(env, "Datagram receive failed");
        if (packetBufferLen > MAX_BUFFER_LEN) {
            free(fullPacket);
        }
        return -1;
    } else {
        jobject packetAddress;

        /*
         * Check if there is an InetAddress already associated with this
         * packet. If so we check if it is the same source address. We
         * can't update any existing InetAddress because it is immutable
         */
        packetAddress = (*env)->GetObjectField(env, packet, dp_addressID);
        if (packetAddress != NULL) {
            if (!NET_SockaddrEqualsInetAddress(env, &remote_addr,
                                               packetAddress)) {
                /* force a new InetAddress to be created */
                packetAddress = NULL;
            }
        }
        if (packetAddress == NULL) {
            packetAddress = NET_SockaddrToInetAddress(env, &remote_addr,
                                                      &port);
            /* stuff the new Inetaddress in the packet */
            (*env)->SetObjectField(env, packet, dp_addressID, packetAddress);
        }

        /* populate the packet */
        (*env)->SetByteArrayRegion(env, packetBuffer, packetBufferOffset, n,
                                   (jbyte *)fullPacket);
        (*env)->SetIntField(env, packet, dp_portID, port);
        (*env)->SetIntField(env, packet, dp_lengthID, n);
    }

    /* make sure receive() picks up the right fd */
    (*env)->SetIntField(env, this, pdsi_fduseID, fduse);

    if (packetBufferLen > MAX_BUFFER_LEN) {
        free(fullPacket);
    }
    return port;
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    receive
 * Signature: (Ljava/net/DatagramPacket;)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_receive0(JNIEnv *env, jobject this,
                                              jobject packet) {

    char BUF[MAX_BUFFER_LEN];
    char *fullPacket;
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    jint timeout = (*env)->GetIntField(env, this, pdsi_timeoutID);
    jbyteArray packetBuffer;
    jint packetBufferOffset, packetBufferLen;
    int ipv6_supported = ipv6_available();

    /* as a result of the changes for ipv6, peek() or peekData()
     * must be called prior to receive() so that fduse can be set.
     */
    int fd = -1, fd1 = -1, fduse, errorCode;

    int n, nsockets=0;
    SOCKETADDRESS remote_addr;
    jint remote_addrsize = sizeof(SOCKETADDRESS);
    BOOL retry;
    jlong prevTime = 0, selectTime=0;
    jboolean connected;

    if (IS_NULL(fdObj) && IS_NULL(fd1Obj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    }

    if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
        nsockets ++;
    }
    if (!IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
        nsockets ++;
    }

    if (nsockets == 2) { /* need to choose one of them */
        /* was fduse set in peek? */
        fduse = (*env)->GetIntField(env, this, pdsi_fduseID);
        if (fduse == -1) {
            /* not set in peek(), must select on both sockets */
            int ret, t = (timeout == 0) ? -1: timeout;
            ret = NET_Timeout2 (fd, fd1, t, &fduse);
            if (ret == 2) {
                fduse = checkLastFD (env, this, fd, fd1);
            } else if (ret <= 0) {
                if (ret == 0) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                    "Receive timed out");
                } else if (ret == -1) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                    "Socket closed");
                }
                return;
            }
        }
    } else if (!ipv6_supported) {
        fduse = fd;
    } else if (IS_NULL(fdObj)) {
        /* ipv6 supported: and this socket bound to an IPV6 only address */
        fduse = fd1;
    } else {
        /* ipv6 supported: and this socket bound to an IPV4 only address */
        fduse = fd;
    }

    if (IS_NULL(packet)) {
        JNU_ThrowNullPointerException(env, "packet");
        return;
    }

    packetBuffer = (*env)->GetObjectField(env, packet, dp_bufID);

    if (IS_NULL(packetBuffer)) {
        JNU_ThrowNullPointerException(env, "packet buffer");
        return;
    }

    packetBufferOffset = (*env)->GetIntField(env, packet, dp_offsetID);
    packetBufferLen = (*env)->GetIntField(env, packet, dp_bufLengthID);

    if (packetBufferLen > MAX_BUFFER_LEN) {

        /* When JNI-ifying the JDK's IO routines, we turned
         * read's and write's of byte arrays of size greater
         * than 2048 bytes into several operations of size 2048.
         * This saves a malloc()/memcpy()/free() for big
         * buffers.  This is OK for file IO and TCP, but that
         * strategy violates the semantics of a datagram protocol.
         * (one big send) != (several smaller sends).  So here
         * we *must* alloc the buffer.  Note it needn't be bigger
         * than 65,536 (0xFFFF) the max size of an IP packet.
         * anything bigger is truncated anyway.
         */
        fullPacket = (char *)malloc(packetBufferLen);
        if (!fullPacket) {
            JNU_ThrowOutOfMemoryError(env, "Receive buf native heap allocation failed");
            return;
        }
    } else {
        fullPacket = &(BUF[0]);
    }



    /*
     * If we are not connected then we need to know if a timeout has been
     * specified and if so we need to pick up the current time. These are
     * required in order to implement the semantics of timeout, viz :-
     * timeout set to t1 but ICMP port unreachable arrives in t2 where
     * t2 < t1. In this case we must discard the ICMP packets and then
     * wait for the next packet up to a maximum of t1 minus t2.
     */
    connected = (*env)->GetBooleanField(env, this, pdsi_connected);
    if (!connected && timeout && !ipv6_supported) {
        prevTime = JVM_CurrentTimeMillis(env, 0);
    }

    if (timeout && nsockets == 1) {
        int ret;
        ret = NET_Timeout(fduse, timeout);
        if (ret <= 0) {
            if (ret == 0) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                "Receive timed out");
            } else if (ret == -1) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                "Socket closed");
            }
            if (packetBufferLen > MAX_BUFFER_LEN) {
                free(fullPacket);
            }
            return;
        }
    }

    /*
     * Loop only if we discarding ICMP port unreachable packets
     */
    do {
        retry = FALSE;

        /* receive the packet */
        n = recvfrom(fduse, fullPacket, packetBufferLen, 0, &remote_addr.sa,
                     &remote_addrsize);

        if (n == SOCKET_ERROR) {
            if (WSAGetLastError() == WSAECONNRESET) {
                /*
                 * An icmp port unreachable has been received - consume any other
                 * outstanding packets.
                 */
                purgeOutstandingICMP(env, this, fduse);

                /*
                 * If connected throw a PortUnreachableException
                 */

                if (connected) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "PortUnreachableException",
                                       "ICMP Port Unreachable");

                    if (packetBufferLen > MAX_BUFFER_LEN) {
                        free(fullPacket);
                    }

                    return;
                }

                /*
                 * If a timeout was specified then we need to adjust it because
                 * we may have used up some of the timeout before the icmp port
                 * unreachable arrived.
                 */
                if (timeout) {
                    int ret;
                    jlong newTime = JVM_CurrentTimeMillis(env, 0);
                    timeout -= (jint)(newTime - prevTime);
                    prevTime = newTime;

                    if (timeout <= 0) {
                        ret = 0;
                    } else {
                        ret = NET_Timeout(fduse, timeout);
                    }

                    if (ret <= 0) {
                        if (ret == 0) {
                            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                            "Receive timed out");
                        } else if (ret == -1) {
                            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                            "Socket closed");
                        }
                        if (packetBufferLen > MAX_BUFFER_LEN) {
                            free(fullPacket);
                        }
                        return;
                    }
                }

                /*
                 * An ICMP port unreachable was received but we are
                 * not connected so ignore it.
                 */
                retry = TRUE;
            }
        }
    } while (retry);

    /* truncate the data if the packet's length is too small */
    if (n > packetBufferLen) {
        n = packetBufferLen;
    }
    if (n < 0) {
        errorCode = WSAGetLastError();
        /* check to see if it's because the buffer was too small */
        if (errorCode == WSAEMSGSIZE) {
            /* it is because the buffer is too small. It's UDP, it's
             * unreliable, it's all good. discard the rest of the
             * data..
             */
            n = packetBufferLen;
        } else {
            /* failure */
            (*env)->SetIntField(env, packet, dp_lengthID, 0);
        }
    }
    if (n == -1) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "socket closed");
    } else if (n == -2) {
        JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                        "operation interrupted");
    } else if (n < 0) {
        NET_ThrowCurrent(env, "Datagram receive failed");
    } else {
        int port = 0;
        jobject packetAddress;

        /*
         * Check if there is an InetAddress already associated with this
         * packet. If so we check if it is the same source address. We
         * can't update any existing InetAddress because it is immutable
         */
        packetAddress = (*env)->GetObjectField(env, packet, dp_addressID);
        if (packetAddress != NULL) {
            if (!NET_SockaddrEqualsInetAddress(env, &remote_addr,
                                               packetAddress)) {
                /* force a new InetAddress to be created */
                packetAddress = NULL;
            }
        }
        if (packetAddress == NULL) {
            packetAddress = NET_SockaddrToInetAddress(env, &remote_addr,
                                                      &port);
            /* stuff the new Inetaddress in the packet */
            (*env)->SetObjectField(env, packet, dp_addressID, packetAddress);
        } else {
            /* only get the new port number */
            port = NET_GetPortFromSockaddr(&remote_addr);
        }
        /* populate the packet */
        (*env)->SetByteArrayRegion(env, packetBuffer, packetBufferOffset, n,
                                   (jbyte *)fullPacket);
        (*env)->SetIntField(env, packet, dp_portID, port);
        (*env)->SetIntField(env, packet, dp_lengthID, n);
    }
    if (packetBufferLen > MAX_BUFFER_LEN) {
        free(fullPacket);
    }
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    datagramSocketCreate
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_datagramSocketCreate(JNIEnv *env,
                                                           jobject this) {
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);

    int fd, fd1;
    int t = TRUE;
    DWORD x1, x2; /* ignored result codes */
    int ipv6_supported = ipv6_available();

    int arg = -1;

    if (IS_NULL(fdObj) || (ipv6_supported && IS_NULL(fd1Obj))) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
        return;
    } else {
        fd =  (int) socket (AF_INET, SOCK_DGRAM, 0);
    }
    if (fd == SOCKET_ERROR) {
        NET_ThrowCurrent(env, "Socket creation failed");
        return;
    }
    SetHandleInformation((HANDLE)(UINT_PTR)fd, HANDLE_FLAG_INHERIT, FALSE);
    (*env)->SetIntField(env, fdObj, IO_fd_fdID, fd);
    NET_SetSockOpt(fd, SOL_SOCKET, SO_BROADCAST, (char*)&t, sizeof(BOOL));

    if (ipv6_supported) {
        /* SIO_UDP_CONNRESET fixes a bug introduced in Windows 2000, which
         * returns connection reset errors un connected UDP sockets (as well
         * as connected sockets. The solution is to only enable this feature
         * when the socket is connected
         */
        t = FALSE;
        WSAIoctl(fd,SIO_UDP_CONNRESET,&t,sizeof(t),&x1,sizeof(x1),&x2,0,0);
        t = TRUE;
        fd1 = socket (AF_INET6, SOCK_DGRAM, 0);
        if (fd1 == SOCKET_ERROR) {
            NET_ThrowCurrent(env, "Socket creation failed");
            return;
        }
        NET_SetSockOpt(fd1, SOL_SOCKET, SO_BROADCAST, (char*)&t, sizeof(BOOL));
        t = FALSE;
        WSAIoctl(fd1,SIO_UDP_CONNRESET,&t,sizeof(t),&x1,sizeof(x1),&x2,0,0);
        (*env)->SetIntField(env, fd1Obj, IO_fd_fdID, fd1);
        SetHandleInformation((HANDLE)(UINT_PTR)fd1, HANDLE_FLAG_INHERIT, FALSE);
    } else {
        /* drop the second fd */
        (*env)->SetObjectField(env, this, pdsi_fd1ID, NULL);
    }
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    datagramSocketClose
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_datagramSocketClose(JNIEnv *env,
                                                          jobject this) {
    /*
     * REMIND: PUT A LOCK AROUND THIS CODE
     */
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    int ipv6_supported = ipv6_available();
    int fd = -1, fd1 = -1;

    if (IS_NULL(fdObj) && (!ipv6_supported || IS_NULL(fd1Obj))) {
        return;
    }

    if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
        if (fd != -1) {
            (*env)->SetIntField(env, fdObj, IO_fd_fdID, -1);
            NET_SocketClose(fd);
        }
    }

    if (ipv6_supported && fd1Obj != NULL) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
        if (fd1 == -1) {
            return;
        }
        (*env)->SetIntField(env, fd1Obj, IO_fd_fdID, -1);
        NET_SocketClose(fd1);
    }
}

/*
 * check the addresses attached to the NetworkInterface object
 * and return the first one (of the requested family Ipv4 or Ipv6)
 * in *iaddr
 */

static int getInetAddrFromIf (JNIEnv *env, int family, jobject nif, jobject *iaddr)
{
    jobjectArray addrArray;
    static jfieldID ni_addrsID=0;
    jsize len;
    jobject addr;
    int i;

    if (ni_addrsID == NULL ) {
        jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
        CHECK_NULL_RETURN (c, -1);
        ni_addrsID = (*env)->GetFieldID(env, c, "addrs",
                                        "[Ljava/net/InetAddress;");
        CHECK_NULL_RETURN (ni_addrsID, -1);
    }

    addrArray = (*env)->GetObjectField(env, nif, ni_addrsID);
    len = (*env)->GetArrayLength(env, addrArray);

    /*
     * Check that there is at least one address bound to this
     * interface.
     */
    if (len < 1) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
            "bad argument for IP_MULTICAST_IF2: No IP addresses bound to interface");
        return -1;
    }
    for (i=0; i<len; i++) {
        int fam;
        addr = (*env)->GetObjectArrayElement(env, addrArray, i);
        fam = getInetAddress_family(env, addr);
        JNU_CHECK_EXCEPTION_RETURN(env, -1);
        if (fam == family) {
            *iaddr = addr;
            return 0;
        }
    }
    return -1;
}

static int getInet4AddrFromIf (JNIEnv *env, jobject nif, struct in_addr *iaddr)
{
    jobject addr;

    int ret = getInetAddrFromIf(env, java_net_InetAddress_IPv4, nif, &addr);
    if (ret == -1) {
        return -1;
    }

    iaddr->s_addr = htonl(getInetAddress_addr(env, addr));
    JNU_CHECK_EXCEPTION_RETURN(env, -1);
    return 0;
}

/* Get the multicasting index from the interface */

static int getIndexFromIf (JNIEnv *env, jobject nif) {
    static jfieldID ni_indexID = NULL;

    if (ni_indexID == NULL) {
        jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
        CHECK_NULL_RETURN(c, -1);
        ni_indexID = (*env)->GetFieldID(env, c, "index", "I");
        CHECK_NULL_RETURN(ni_indexID, -1);
    }

    return (*env)->GetIntField(env, nif, ni_indexID);
}

static int isAdapterIpv6Enabled(JNIEnv *env, int index) {
  netif *ifList, *curr;
  int ipv6Enabled = 0;
  if (getAllInterfacesAndAddresses(env, &ifList) < 0) {
      return ipv6Enabled;
  }

  /* search by index */
  curr = ifList;
  while (curr != NULL) {
      if (index == curr->index) {
          break;
      }
      curr = curr->next;
  }

  /* if found ipv6Index != 0 then interface is configured with IPV6 */
  if ((curr != NULL) && (curr->ipv6Index !=0)) {
      ipv6Enabled = 1;
  }

  /* release the interface list */
  free_netif(ifList);

  return ipv6Enabled;
}

/*
 * Sets the multicast interface.
 *
 * SocketOptions.IP_MULTICAST_IF (argument is an InetAddress) :-
 *      IPv4:   set outgoing multicast interface using
 *              IPPROTO_IP/IP_MULTICAST_IF
 *
 *      IPv6:   Get the interface to which the
 *              InetAddress is bound
 *              and do same as SockOptions.IF_MULTICAST_IF2
 *
 * SockOptions.IF_MULTICAST_IF2 (argument is a NetworkInterface ) :-
 *      For each stack:
 *      IPv4:   Obtain IP address bound to network interface
 *              (NetworkInterface.addres[0])
 *              set outgoing multicast interface using
 *              IPPROTO_IP/IP_MULTICAST_IF
 *
 *      IPv6:   Obtain NetworkInterface.index
 *              Set outgoing multicast interface using
 *              IPPROTO_IPV6/IPV6_MULTICAST_IF
 *
 */
static void setMulticastInterface(JNIEnv *env, jobject this, int fd, int fd1,
                                  jint opt, jobject value)
{
    int ipv6_supported = ipv6_available();

    if (opt == java_net_SocketOptions_IP_MULTICAST_IF) {
        /*
         * value is an InetAddress.
         * On IPv4 system use IP_MULTICAST_IF socket option
         * On IPv6 system get the NetworkInterface that this IP
         * address is bound to and use the IPV6_MULTICAST_IF
         * option instead of IP_MULTICAST_IF
         */
        if (ipv6_supported) {
            static jclass ni_class = NULL;
            if (ni_class == NULL) {
                jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
                CHECK_NULL(c);
                ni_class = (*env)->NewGlobalRef(env, c);
                CHECK_NULL(ni_class);
            }

            value = Java_java_net_NetworkInterface_getByInetAddress0(env, ni_class, value);
            if (value == NULL) {
                if (!(*env)->ExceptionOccurred(env)) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                         "bad argument for IP_MULTICAST_IF"
                         ": address not bound to any interface");
                }
                return;
            }
            opt = java_net_SocketOptions_IP_MULTICAST_IF2;
        } else {
            struct in_addr in;

            in.s_addr = htonl(getInetAddress_addr(env, value));
            JNU_CHECK_EXCEPTION(env);
            if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF,
                               (const char*)&in, sizeof(in)) < 0) {
                JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "Error setting socket option");
            }
            return;
        }
    }

    if (opt == java_net_SocketOptions_IP_MULTICAST_IF2) {
        /*
         * value is a NetworkInterface.
         * On IPv6 system get the index of the interface and use the
         * IPV6_MULTICAST_IF socket option
         * On IPv4 system extract addr[0] and use the IP_MULTICAST_IF
         * option. For IPv6 both must be done.
         */
        if (ipv6_supported) {
            static jfieldID ni_indexID = NULL;
            struct in_addr in;
            int index;

            if (ni_indexID == NULL) {
                jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
                CHECK_NULL(c);
                ni_indexID = (*env)->GetFieldID(env, c, "index", "I");
                CHECK_NULL(ni_indexID);
            }
            index = (*env)->GetIntField(env, value, ni_indexID);

            if (isAdapterIpv6Enabled(env, index) != 0) {
                if (setsockopt(fd1, IPPROTO_IPV6, IPV6_MULTICAST_IF,
                               (const char*)&index, sizeof(index)) < 0) {
                    if (errno == EINVAL && index > 0) {
                        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                            "IPV6_MULTICAST_IF failed (interface has IPv4 "
                            "address only?)");
                    } else {
                        JNU_ThrowByNameWithMessageAndLastError
                            (env, JNU_JAVANETPKG "SocketException", "Error setting socket option");
                    }
                    return;
                }
            }
            /* If there are any IPv4 addresses on this interface then
             * repeat the operation on the IPv4 fd */

            if (getInet4AddrFromIf(env, value, &in) < 0) {
                return;
            }
            if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF,
                               (const char*)&in, sizeof(in)) < 0) {
                JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "Error setting socket option");
            }
            return;
        } else {
            struct in_addr in;

            if (getInet4AddrFromIf (env, value, &in) < 0) {
                if ((*env)->ExceptionOccurred(env)) {
                    return;
                }
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "no InetAddress instances of requested type");
                return;
            }

            if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF,
                               (const char*)&in, sizeof(in)) < 0) {
                JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "Error setting socket option");
            }
            return;
        }
    }
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    socketNativeSetOption
 * Signature: (ILjava/lang/Object;)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_socketNativeSetOption
  (JNIEnv *env,jobject this, jint opt,jobject value)
{
    int fd = -1, fd1 = -1;
    int levelv4 = 0, levelv6 = 0, optnamev4 = 0, optnamev6 = 0, optlen = 0;
    union {
        int i;
        char c;
    } optval = { 0 };
    int ipv6_supported = ipv6_available();
    fd = getFD(env, this);

    if (ipv6_supported) {
        fd1 = getFD1(env, this);
    }
    if (fd < 0 && fd1 < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "socket closed");
        return;
    }

    if ((opt == java_net_SocketOptions_IP_MULTICAST_IF) ||
        (opt == java_net_SocketOptions_IP_MULTICAST_IF2)) {

        setMulticastInterface(env, this, fd, fd1, opt, value);
        return;
    }

    /*
     * Map the Java level socket option to the platform specific
     * level(s) and option name(s).
     */
    if (fd1 != -1) {
        if (NET_MapSocketOptionV6(opt, &levelv6, &optnamev6)) {
            JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
            return;
        }
    }
    if (fd != -1) {
        if (NET_MapSocketOption(opt, &levelv4, &optnamev4)) {
            JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
            return;
        }
    }

    switch (opt) {
        case java_net_SocketOptions_SO_SNDBUF :
        case java_net_SocketOptions_SO_RCVBUF :
        case java_net_SocketOptions_IP_TOS :
            {
                jclass cls;
                jfieldID fid;

                cls = (*env)->FindClass(env, "java/lang/Integer");
                CHECK_NULL(cls);
                fid =  (*env)->GetFieldID(env, cls, "value", "I");
                CHECK_NULL(fid);

                optval.i = (*env)->GetIntField(env, value, fid);
                optlen = sizeof(optval.i);
            }
            break;

        case java_net_SocketOptions_SO_REUSEADDR:
        case java_net_SocketOptions_SO_BROADCAST:
        case java_net_SocketOptions_IP_MULTICAST_LOOP:
            {
                jclass cls;
                jfieldID fid;
                jboolean on;

                cls = (*env)->FindClass(env, "java/lang/Boolean");
                CHECK_NULL(cls);
                fid =  (*env)->GetFieldID(env, cls, "value", "Z");
                CHECK_NULL(fid);

                on = (*env)->GetBooleanField(env, value, fid);
                optval.i = (on ? 1 : 0);
                /*
                 * setLoopbackMode (true) disables IP_MULTICAST_LOOP rather
                 * than enabling it.
                 */
                if (opt == java_net_SocketOptions_IP_MULTICAST_LOOP) {
                    optval.i = !optval.i;
                }
                optlen = sizeof(optval.i);
            }
            break;

        default :
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                "Socket option not supported by PlainDatagramSocketImp");
            return;

    }

    if (fd1 != -1) {
        if (NET_SetSockOpt(fd1, levelv6, optnamev6, (void *)&optval, optlen) < 0) {
            NET_ThrowCurrent(env, "setsockopt IPv6");
            return;
        }
    }
    if (fd != -1) {
        if (NET_SetSockOpt(fd, levelv4, optnamev4, (void *)&optval, optlen) < 0) {
            NET_ThrowCurrent(env, "setsockopt");
            return;
        }
    }
}

/*
 *
 * called by getMulticastInterface to retrieve a NetworkInterface
 * configured for IPv4.
 * The ipv4Mode parameter, is a closet boolean, which allows for a NULL return,
 * or forces the creation of a NetworkInterface object with null data.
 * It relates to its calling context in getMulticastInterface.
 * ipv4Mode == 1, the context is IPV4 processing only.
 * ipv4Mode == 0, the context is IPV6 processing
 *
 */
static jobject getIPv4NetworkInterface (JNIEnv *env, jobject this, int fd, jint opt, int ipv4Mode) {
        static jclass inet4_class;
        static jmethodID inet4_ctrID;

        static jclass ni_class; static jmethodID ni_ctrID;
        static jfieldID ni_indexID;
        static jfieldID ni_addrsID;

        jobjectArray addrArray;
        jobject addr;
        jobject ni;

        struct in_addr in;
        struct in_addr *inP = &in;
        int len = sizeof(struct in_addr);
        if (getsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF,
                           (char *)inP, &len) < 0) {
            JNU_ThrowByNameWithMessageAndLastError
                (env, JNU_JAVANETPKG "SocketException", "Error getting socket option");
            return NULL;
        }

        /*
         * Construct and populate an Inet4Address
         */
        if (inet4_class == NULL) {
            jclass c = (*env)->FindClass(env, "java/net/Inet4Address");
            CHECK_NULL_RETURN(c, NULL);
            inet4_ctrID = (*env)->GetMethodID(env, c, "<init>", "()V");
            CHECK_NULL_RETURN(inet4_ctrID, NULL);
            inet4_class = (*env)->NewGlobalRef(env, c);
            CHECK_NULL_RETURN(inet4_class, NULL);
        }
        addr = (*env)->NewObject(env, inet4_class, inet4_ctrID, 0);
        CHECK_NULL_RETURN(addr, NULL);

        setInetAddress_addr(env, addr, ntohl(in.s_addr));
        JNU_CHECK_EXCEPTION_RETURN(env, NULL);
        /*
         * For IP_MULTICAST_IF return InetAddress
         */
        if (opt == java_net_SocketOptions_IP_MULTICAST_IF) {
            return addr;
        }

        /*
         * For IP_MULTICAST_IF2 we get the NetworkInterface for
         * this address and return it
         */
        if (ni_class == NULL) {
            jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
            CHECK_NULL_RETURN(c, NULL);
            ni_ctrID = (*env)->GetMethodID(env, c, "<init>", "()V");
            CHECK_NULL_RETURN(ni_ctrID, NULL);
            ni_indexID = (*env)->GetFieldID(env, c, "index", "I");
            CHECK_NULL_RETURN(ni_indexID, NULL);
            ni_addrsID = (*env)->GetFieldID(env, c, "addrs",
                                            "[Ljava/net/InetAddress;");
            CHECK_NULL_RETURN(ni_addrsID, NULL);
            ni_class = (*env)->NewGlobalRef(env, c);
            CHECK_NULL_RETURN(ni_class, NULL);
        }
        ni = Java_java_net_NetworkInterface_getByInetAddress0(env, ni_class, addr);
        if (ni) {
            return ni;
        }
        if (ipv4Mode) {
            ni = (*env)->NewObject(env, ni_class, ni_ctrID, 0);
            CHECK_NULL_RETURN(ni, NULL);

            (*env)->SetIntField(env, ni, ni_indexID, -1);
            addrArray = (*env)->NewObjectArray(env, 1, inet4_class, NULL);
            CHECK_NULL_RETURN(addrArray, NULL);
            (*env)->SetObjectArrayElement(env, addrArray, 0, addr);
            (*env)->SetObjectField(env, ni, ni_addrsID, addrArray);
        } else {
            ni = NULL;
        }
        return ni;
}

/*
 * Return the multicast interface:
 *
 * SocketOptions.IP_MULTICAST_IF
 *      IPv4:   Query IPPROTO_IP/IP_MULTICAST_IF
 *              Create InetAddress
 *              IP_MULTICAST_IF returns struct ip_mreqn on 2.2
 *              kernel but struct in_addr on 2.4 kernel
 *      IPv6:   Query IPPROTO_IPV6 / IPV6_MULTICAST_IF or
 *              obtain from impl is Linux 2.2 kernel
 *              If index == 0 return InetAddress representing
 *              anyLocalAddress.
 *              If index > 0 query NetworkInterface by index
 *              and returns addrs[0]
 *
 * SocketOptions.IP_MULTICAST_IF2
 *      IPv4:   Query IPPROTO_IP/IP_MULTICAST_IF
 *              Query NetworkInterface by IP address and
 *              return the NetworkInterface that the address
 *              is bound too.
 *      IPv6:   Query IPPROTO_IPV6 / IPV6_MULTICAST_IF
 *              (except Linux .2 kernel)
 *              Query NetworkInterface by index and
 *              return NetworkInterface.
 */
jobject getMulticastInterface(JNIEnv *env, jobject this, int fd, int fd1, jint opt) {
    jboolean isIPV4 = !ipv6_available() || fd1 == -1;

    /*
     * IPv4 implementation
     */
    if (isIPV4) {
        jobject netObject = NULL; // return is either an addr or a netif
        netObject = getIPv4NetworkInterface(env, this, fd, opt, 1);
        return netObject;
    }

    /*
     * IPv6 implementation
     */
    if ((opt == java_net_SocketOptions_IP_MULTICAST_IF) ||
        (opt == java_net_SocketOptions_IP_MULTICAST_IF2)) {

        static jclass ni_class;
        static jmethodID ni_ctrID;
        static jfieldID ni_indexID;
        static jfieldID ni_addrsID;
        static jclass ia_class;
        static jmethodID ia_anyLocalAddressID;

        int index;
        int len = sizeof(index);

        jobjectArray addrArray;
        jobject addr;
        jobject ni;

        {
            if (getsockopt(fd1, IPPROTO_IPV6, IPV6_MULTICAST_IF,
                               (char*)&index, &len) < 0) {
                JNU_ThrowByNameWithMessageAndLastError
                    (env, JNU_JAVANETPKG "SocketException", "Error getting socket option");
                return NULL;
            }
        }

        if (ni_class == NULL) {
            jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
            CHECK_NULL_RETURN(c, NULL);
            ni_ctrID = (*env)->GetMethodID(env, c, "<init>", "()V");
            CHECK_NULL_RETURN(ni_ctrID, NULL);
            ni_indexID = (*env)->GetFieldID(env, c, "index", "I");
            CHECK_NULL_RETURN(ni_indexID, NULL);
            ni_addrsID = (*env)->GetFieldID(env, c, "addrs",
                                            "[Ljava/net/InetAddress;");
            CHECK_NULL_RETURN(ni_addrsID, NULL);

            ia_class = (*env)->FindClass(env, "java/net/InetAddress");
            CHECK_NULL_RETURN(ia_class, NULL);
            ia_class = (*env)->NewGlobalRef(env, ia_class);
            CHECK_NULL_RETURN(ia_class, NULL);
            ia_anyLocalAddressID = (*env)->GetStaticMethodID(env,
                                                             ia_class,
                                                             "anyLocalAddress",
                                                             "()Ljava/net/InetAddress;");
            CHECK_NULL_RETURN(ia_anyLocalAddressID, NULL);
            ni_class = (*env)->NewGlobalRef(env, c);
            CHECK_NULL_RETURN(ni_class, NULL);
        }

        /*
         * If multicast to a specific interface then return the
         * interface (for IF2) or the any address on that interface
         * (for IF).
         */
        if (index > 0) {
            ni = Java_java_net_NetworkInterface_getByIndex0(env, ni_class,
                                                                   index);
            if (ni == NULL) {
                char errmsg[255];
                sprintf(errmsg,
                        "IPV6_MULTICAST_IF returned index to unrecognized interface: %d",
                        index);
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", errmsg);
                return NULL;
            }

            /*
             * For IP_MULTICAST_IF2 return the NetworkInterface
             */
            if (opt == java_net_SocketOptions_IP_MULTICAST_IF2) {
                return ni;
            }

            /*
             * For IP_MULTICAST_IF return addrs[0]
             */
            addrArray = (*env)->GetObjectField(env, ni, ni_addrsID);
            if ((*env)->GetArrayLength(env, addrArray) < 1) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                    "IPV6_MULTICAST_IF returned interface without IP bindings");
                return NULL;
            }

            addr = (*env)->GetObjectArrayElement(env, addrArray, 0);
            return addr;
        } else if (index == 0) { // index == 0 typically means IPv6 not configured on the interfaces
            // falling back to treat interface as configured for IPv4
            jobject netObject = NULL;
            netObject = getIPv4NetworkInterface(env, this, fd, opt, 0);
            if (netObject != NULL) {
                return netObject;
            }
        }

        /*
         * Multicast to any address - return anyLocalAddress
         * or a NetworkInterface with addrs[0] set to anyLocalAddress
         */

        addr = (*env)->CallStaticObjectMethod(env, ia_class, ia_anyLocalAddressID,
                                              NULL);
        if (opt == java_net_SocketOptions_IP_MULTICAST_IF) {
            return addr;
        }

        ni = (*env)->NewObject(env, ni_class, ni_ctrID, 0);
        CHECK_NULL_RETURN(ni, NULL);
        (*env)->SetIntField(env, ni, ni_indexID, -1);
        addrArray = (*env)->NewObjectArray(env, 1, ia_class, NULL);
        CHECK_NULL_RETURN(addrArray, NULL);
        (*env)->SetObjectArrayElement(env, addrArray, 0, addr);
        (*env)->SetObjectField(env, ni, ni_addrsID, addrArray);
        return ni;
    }
    return NULL;
}


/*
 * Returns relevant info as a jint.
 *
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    socketGetOption
 * Signature: (I)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_socketGetOption
  (JNIEnv *env, jobject this, jint opt)
{
    int fd = -1, fd1 = -1;
    int level, optname, optlen;
    union {
        int i;
    } optval = {0};
    int ipv6_supported = ipv6_available();

    fd = getFD(env, this);
    if (ipv6_supported) {
        fd1 = getFD1(env, this);
    }

    if (fd < 0 && fd1 < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return NULL;
    }

    /*
     * Handle IP_MULTICAST_IF separately
     */
    if (opt == java_net_SocketOptions_IP_MULTICAST_IF ||
        opt == java_net_SocketOptions_IP_MULTICAST_IF2) {
        return getMulticastInterface(env, this, fd, fd1, opt);
    }

    /*
     * Map the Java level socket option to the platform specific
     * level and option name.
     */
    if (NET_MapSocketOption(opt, &level, &optname)) {
        JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
        return NULL;
    }

    if (fd == -1) {
        if (NET_MapSocketOptionV6(opt, &level, &optname)) {
            JNU_ThrowByName(env, "java/net/SocketException", "Invalid option");
            return NULL;
        }
        fd = fd1; /* must be IPv6 only */
    }

    optlen = sizeof(optval.i);
    if (NET_GetSockOpt(fd, level, optname, (void *)&optval, &optlen) < 0) {
        char tmpbuf[255];
        int size = 0;
        char errmsg[255 + 31];
        getErrorString(errno, tmpbuf, sizeof(tmpbuf));
        sprintf(errmsg, "error getting socket option: %s", tmpbuf);
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", errmsg);
        return NULL;
    }

    switch (opt) {
        case java_net_SocketOptions_SO_BROADCAST:
        case java_net_SocketOptions_SO_REUSEADDR:
            return createBoolean(env, optval.i);

        case java_net_SocketOptions_IP_MULTICAST_LOOP:
            /* getLoopbackMode() returns true if IP_MULTICAST_LOOP is disabled */
            return createBoolean(env, !optval.i);

        case java_net_SocketOptions_SO_SNDBUF:
        case java_net_SocketOptions_SO_RCVBUF:
        case java_net_SocketOptions_IP_TOS:
            return createInteger(env, optval.i);

        default :
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                "Socket option not supported by TwoStacksPlainDatagramSocketImpl");
            return NULL;

    }
}

/*
 * Returns local address of the socket.
 *
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    socketLocalAddress
 * Signature: (I)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_socketLocalAddress
  (JNIEnv *env, jobject this, jint family)
{
    int fd = -1, fd1 = -1;
    SOCKETADDRESS sa;
    int len = 0;
    int port;
    jobject iaObj;
    int ipv6_supported = ipv6_available();

    fd = getFD(env, this);
    if (ipv6_supported) {
        fd1 = getFD1(env, this);
    }

    if (fd < 0 && fd1 < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return NULL;
    }

    /* find out local IP address */

    len = sizeof(struct sockaddr_in);

    /* family==-1 when socket is not connected */
    if ((family == java_net_InetAddress_IPv6) || (family == -1 && fd == -1)) {
        fd = fd1; /* must be IPv6 only */
        len = sizeof(struct sockaddr_in6);
    }

    if (fd == -1) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return NULL;
    }

    if (getsockname(fd, &sa.sa, &len) == -1) {
        JNU_ThrowByNameWithMessageAndLastError
            (env, JNU_JAVANETPKG "SocketException", "Error getting socket name");
        return NULL;
    }
    iaObj = NET_SockaddrToInetAddress(env, &sa, &port);

    return iaObj;
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    setTimeToLive
 * Signature: (I)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_setTimeToLive(JNIEnv *env, jobject this,
                                                    jint ttl) {

    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    int fd = -1, fd1 = -1;
    int ittl = (int)ttl;

    if (IS_NULL(fdObj) && IS_NULL(fd1Obj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    } else {
      if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
      }
      if (!IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
      }
    }

    /* setsockopt to be correct ttl */
    if (fd >= 0) {
      if (NET_SetSockOpt(fd, IPPROTO_IP, IP_MULTICAST_TTL, (char*)&ittl,
                         sizeof (ittl)) < 0) {
        NET_ThrowCurrent(env, "set IP_MULTICAST_TTL failed");
        return;
      }
    }

    if (fd1 >= 0) {
      if (NET_SetSockOpt(fd1, IPPROTO_IPV6, IPV6_MULTICAST_HOPS, (char *)&ittl,
                         sizeof(ittl)) <0) {
        NET_ThrowCurrent(env, "set IPV6_MULTICAST_HOPS failed");
      }
    }
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    setTTL
 * Signature: (B)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_setTTL(JNIEnv *env, jobject this,
                                             jbyte ttl) {
    Java_java_net_TwoStacksPlainDatagramSocketImpl_setTimeToLive(env, this,
                                                        (jint)ttl & 0xFF);
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    getTimeToLive
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_getTimeToLive(JNIEnv *env, jobject this) {
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    int fd = -1, fd1 = -1;
    int ttl = 0;
    int len = sizeof(ttl);

    if (IS_NULL(fdObj) && IS_NULL(fd1Obj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return -1;
    } else {
      if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
      }
      if (!IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
      }
    }

    /* getsockopt of ttl */
    if (fd >= 0) {
      if (NET_GetSockOpt(fd, IPPROTO_IP, IP_MULTICAST_TTL, (char*)&ttl, &len) < 0) {
        NET_ThrowCurrent(env, "get IP_MULTICAST_TTL failed");
        return -1;
      }
      return (jint)ttl;
    }
    if (fd1 >= 0) {
      if (NET_GetSockOpt(fd1, IPPROTO_IPV6, IPV6_MULTICAST_HOPS, (char*)&ttl, &len) < 0) {
        NET_ThrowCurrent(env, "get IP_MULTICAST_TTL failed");
        return -1;
      }
      return (jint)ttl;
    }
    return -1;
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    getTTL
 * Signature: ()B
 */
JNIEXPORT jbyte JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_getTTL(JNIEnv *env, jobject this) {
    int result = Java_java_net_TwoStacksPlainDatagramSocketImpl_getTimeToLive(env, this);

    return (jbyte)result;
}

/* join/leave the named group on the named interface, or if no interface specified
 * then the interface set with setInterfac(), or the default interface otherwise */

static void mcast_join_leave(JNIEnv *env, jobject this,
                             jobject iaObj, jobject niObj,
                             jboolean join)
{
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    jint fd = -1, fd1 = -1;

    SOCKETADDRESS name;
    struct ip_mreq mname;
    struct ipv6_mreq mname6;

    struct in_addr in;
    DWORD ifindex = 0;

    int len, family;
    int ipv6_supported = ipv6_available();
    int cmd;

    memset((char *)&in, 0, sizeof(in));
    memset((char *)&name, 0, sizeof(name));

    if (IS_NULL(fdObj) && IS_NULL(fd1Obj)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return;
    }
    if (!IS_NULL(fdObj)) {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
    }
    if (ipv6_supported && !IS_NULL(fd1Obj)) {
        fd1 = (*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
    }

    if (IS_NULL(iaObj)) {
        JNU_ThrowNullPointerException(env, "address");
        return;
    }

    if (NET_InetAddressToSockaddr(env, iaObj, 0, &name, &len, JNI_FALSE) != 0) {
      return;
    }

    /* Set the multicast group address in the ip_mreq field
     * eventually this check should be done by the security manager
     */
    family = name.sa.sa_family;

    if (family == AF_INET) {
        int address = name.sa4.sin_addr.s_addr;
        if (!IN_MULTICAST(ntohl(address))) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "not in multicast");
            return;
        }
        mname.imr_multiaddr.s_addr = address;
        if (fd < 0) {
          JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Can't join an IPv4 group on an IPv6 only socket");
          return;
        }
        if (IS_NULL(niObj)) {
            len = sizeof(in);
            if (NET_GetSockOpt(fd, IPPROTO_IP, IP_MULTICAST_IF,
                           (char *)&in, &len) < 0) {
                NET_ThrowCurrent(env, "get IP_MULTICAST_IF failed");
                return;
            }
            mname.imr_interface.s_addr = in.s_addr;
        } else {
            if (getInet4AddrFromIf (env, niObj, &mname.imr_interface) != 0) {
                NET_ThrowCurrent(env, "no Inet4Address associated with interface");
                return;
            }
        }

        cmd = join ? IP_ADD_MEMBERSHIP: IP_DROP_MEMBERSHIP;

        /* Join the multicast group */
        if (NET_SetSockOpt(fd, IPPROTO_IP, cmd, (char *) &mname, sizeof (mname)) < 0) {
            if (WSAGetLastError() == WSAENOBUFS) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                    "IP_ADD_MEMBERSHIP failed (out of hardware filters?)");
            } else {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException","error setting options");
            }
        }
    } else /* AF_INET6 */ {
        if (ipv6_supported) {
            struct in6_addr *address;
            address = &name.sa6.sin6_addr;
            if (!IN6_IS_ADDR_MULTICAST(address)) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "not in6 multicast");
                return;
            }
            mname6.ipv6mr_multiaddr = *address;
        } else {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "IPv6 not supported");
            return;
        }
        if (fd1 < 0) {
          JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Can't join an IPv6 group on a IPv4 socket");
          return;
        }
        if (IS_NULL(niObj)) {
            len = sizeof (ifindex);
            if (NET_GetSockOpt(fd1, IPPROTO_IPV6, IPV6_MULTICAST_IF, &ifindex, &len) < 0) {
                NET_ThrowCurrent(env, "get IPV6_MULTICAST_IF failed");
                return;
            }
        } else {
            ifindex = getIndexFromIf (env, niObj);
            if (ifindex == -1) {
                if ((*env)->ExceptionOccurred(env)) {
                    return;
                }
                NET_ThrowCurrent(env, "get ifindex failed");
                return;
            }
        }
        mname6.ipv6mr_interface = ifindex;
        cmd = join ? IPV6_ADD_MEMBERSHIP: IPV6_DROP_MEMBERSHIP;

        /* Join the multicast group */
        if (NET_SetSockOpt(fd1, IPPROTO_IPV6, cmd, (char *) &mname6, sizeof (mname6)) < 0) {
            if (WSAGetLastError() == WSAENOBUFS) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                    "IP_ADD_MEMBERSHIP failed (out of hardware filters?)");
            } else {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException","error setting options");
            }
        }
    }

    return;
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    join
 * Signature: (Ljava/net/InetAddress;)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_join(JNIEnv *env, jobject this,
                                           jobject iaObj, jobject niObj)
{
    mcast_join_leave (env, this, iaObj, niObj, JNI_TRUE);
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    leave
 * Signature: (Ljava/net/InetAddress;)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_leave(JNIEnv *env, jobject this,
                                            jobject iaObj, jobject niObj)
{
    mcast_join_leave (env, this, iaObj, niObj, JNI_FALSE);
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    dataAvailable
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_java_net_TwoStacksPlainDatagramSocketImpl_dataAvailable
(JNIEnv *env, jobject this) {
    SOCKET fd;
    SOCKET fd1;
    int  rv = -1, rv1 = -1;
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj;

    if (!IS_NULL(fdObj)) {
        int retval = 0;
        fd = (SOCKET)(*env)->GetIntField(env, fdObj, IO_fd_fdID);
        rv = ioctlsocket(fd, FIONREAD, &retval);
        if (retval > 0) {
            return retval;
        }
    }

    fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    if (!IS_NULL(fd1Obj)) {
        int retval = 0;
        fd1 = (SOCKET)(*env)->GetIntField(env, fd1Obj, IO_fd_fdID);
        rv1 = ioctlsocket(fd1, FIONREAD, &retval);
        if (retval > 0) {
            return retval;
        }
    }

    if (rv < 0 && rv1 < 0) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return -1;
    }

    return 0;
}
