/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include <io.h>
#include "sun_nio_ch_DatagramChannelImpl.h"
#include "nio.h"
#include "nio_util.h"
#include "net_util.h"
#include <winsock2.h>

static jfieldID dci_senderID;   /* sender in sun.nio.ch.DatagramChannelImpl */
static jfieldID dci_senderAddrID; /* sender InetAddress in sun.nio.ch.DatagramChannelImpl */
static jfieldID dci_senderPortID; /* sender port in sun.nio.ch.DatagramChannelImpl */
static jclass isa_class;        /* java.net.InetSocketAddress */
static jmethodID isa_ctorID;    /* java.net.InetSocketAddress(InetAddress, int) */


JNIEXPORT void JNICALL
Java_sun_nio_ch_DatagramChannelImpl_initIDs(JNIEnv *env, jclass clazz)
{
    clazz = (*env)->FindClass(env, "java/net/InetSocketAddress");
    isa_class = (*env)->NewGlobalRef(env, clazz);
    isa_ctorID = (*env)->GetMethodID(env, clazz, "<init>",
                                     "(Ljava/net/InetAddress;I)V");

    clazz = (*env)->FindClass(env, "sun/nio/ch/DatagramChannelImpl");
    dci_senderID = (*env)->GetFieldID(env, clazz, "sender",
                                      "Ljava/net/SocketAddress;");
    dci_senderAddrID = (*env)->GetFieldID(env, clazz,
                                          "cachedSenderInetAddress",
                                          "Ljava/net/InetAddress;");
    dci_senderPortID = (*env)->GetFieldID(env, clazz,
                                          "cachedSenderPort", "I");
}

/*
 * This function "purges" all outstanding ICMP port unreachable packets
 * outstanding on a socket and returns JNI_TRUE if any ICMP messages
 * have been purged. The rational for purging is to emulate normal BSD
 * behaviour whereby receiving a "connection reset" status resets the
 * socket.
 */
jboolean purgeOutstandingICMP(JNIEnv *env, jclass clazz, jint fd)
{
    jboolean got_icmp = JNI_FALSE;
    char buf[1];
    fd_set tbl;
    struct timeval t = { 0, 0 };
    SOCKETADDRESS sa;
    int addrlen = sizeof(sa);

    /*
     * Peek at the queue to see if there is an ICMP port unreachable. If there
     * is then receive it.
     */
    FD_ZERO(&tbl);
    FD_SET((u_int)fd, &tbl);
    while(1) {
        if (select(/*ignored*/fd+1, &tbl, 0, 0, &t) <= 0) {
            break;
        }
        if (recvfrom(fd, buf, 1, MSG_PEEK,
                     (struct sockaddr *)&sa, &addrlen) != SOCKET_ERROR) {
            break;
        }
        if (WSAGetLastError() != WSAECONNRESET) {
            /* some other error - we don't care here */
            break;
        }

        recvfrom(fd, buf, 1, 0,  (struct sockaddr *)&sa, &addrlen);
        got_icmp = JNI_TRUE;
    }

    return got_icmp;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_DatagramChannelImpl_disconnect0(JNIEnv *env, jobject this,
                                                jobject fdo, jboolean isIPv6)
{
    jint fd = fdval(env, fdo);
    int rv = 0;
    SOCKETADDRESS sa;
    int sa_len = sizeof(sa);

    memset(&sa, 0, sa_len);

    rv = connect((SOCKET)fd, (struct sockaddr *)&sa, sa_len);
    if (rv == SOCKET_ERROR) {
        handleSocketError(env, WSAGetLastError());
    } else {
        /* Disable WSAECONNRESET errors as socket is no longer connected */
        BOOL enable = FALSE;
        DWORD bytesReturned = 0;
        WSAIoctl((SOCKET)fd, SIO_UDP_CONNRESET, &enable, sizeof(enable),
                 NULL, 0, &bytesReturned, NULL, NULL);
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_DatagramChannelImpl_receive0(JNIEnv *env, jobject this,
                                            jobject fdo, jlong address,
                                            jint len, jboolean connected)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);
    SOCKETADDRESS sa;
    int sa_len = sizeof(sa);
    BOOL retry = FALSE;
    jint n;
    jobject senderAddr;

    do {
        retry = FALSE;
        n = recvfrom((SOCKET)fd,
                     (char *)buf,
                     len,
                     0,
                     (struct sockaddr *)&sa,
                     &sa_len);

        if (n == SOCKET_ERROR) {
            int theErr = (jint)WSAGetLastError();
            if (theErr == WSAEMSGSIZE) {
                /* Spec says the rest of the data will be discarded... */
                n = len;
            } else if (theErr == WSAECONNRESET) {
                purgeOutstandingICMP(env, this, fd);
                if (connected == JNI_FALSE) {
                    retry = TRUE;
                } else {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "PortUnreachableException", 0);
                    return IOS_THROWN;
                }
            } else if (theErr == WSAEWOULDBLOCK) {
                return IOS_UNAVAILABLE;
            } else return handleSocketError(env, theErr);
        }
    } while (retry);

    /*
     * If the source address and port match the cached address
     * and port in DatagramChannelImpl then we don't need to
     * create InetAddress and InetSocketAddress objects.
     */
    senderAddr = (*env)->GetObjectField(env, this, dci_senderAddrID);
    if (senderAddr != NULL) {
        if (!NET_SockaddrEqualsInetAddress(env, (struct sockaddr *)&sa,
                                           senderAddr)) {
            senderAddr = NULL;
        } else {
            jint port = (*env)->GetIntField(env, this, dci_senderPortID);
            if (port != NET_GetPortFromSockaddr((struct sockaddr *)&sa)) {
                senderAddr = NULL;
            }
        }
    }
    if (senderAddr == NULL) {
        jobject isa = NULL;
        int port;
        jobject ia = NET_SockaddrToInetAddress(env, (struct sockaddr *)&sa,
                                               &port);

        if (ia != NULL) {
            isa = (*env)->NewObject(env, isa_class, isa_ctorID, ia, port);
        }

        if (isa == NULL) {
            JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
            return IOS_THROWN;
        }

        // update cachedSenderInetAddress/cachedSenderPort
        (*env)->SetObjectField(env, this, dci_senderAddrID, ia);
        (*env)->SetIntField(env, this, dci_senderPortID,
                            NET_GetPortFromSockaddr((struct sockaddr *)&sa));
        (*env)->SetObjectField(env, this, dci_senderID, isa);
    }
    return n;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_DatagramChannelImpl_send0(JNIEnv *env, jobject this,
                                          jboolean preferIPv6, jobject fdo,
                                          jlong address, jint len,
                                          jobject destAddress, jint destPort)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);
    SOCKETADDRESS sa;
    int sa_len;
    jint rv = 0;

    if (NET_InetAddressToSockaddr(env, destAddress, destPort,
                                  (struct sockaddr *)&sa,
                                   &sa_len, preferIPv6) != 0) {
      return IOS_THROWN;
    }

    rv = sendto((SOCKET)fd,
               buf,
               len,
               0,
               (struct sockaddr *)&sa,
               sa_len);
    if (rv == SOCKET_ERROR) {
        int theErr = (jint)WSAGetLastError();
        if (theErr == WSAEWOULDBLOCK) {
            return IOS_UNAVAILABLE;
        }
        return handleSocketError(env, (jint)WSAGetLastError());
    }
    return rv;
}
