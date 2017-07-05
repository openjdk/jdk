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

static jfieldID isa_addrID;     /* address in java.net.InetSocketAddress */
static jfieldID isa_portID;     /* port in java.net.InetSocketAddress */
static jfieldID dci_senderID;   /* sender in sun.nio.ch.DatagramChannelImpl */
static jfieldID dci_senderAddrID; /* sender InetAddress in sun.nio.ch.DatagramChannelImpl */
static jfieldID dci_senderPortID; /* sender port in sun.nio.ch.DatagramChannelImpl */
static jfieldID ia_addrID;
static jfieldID ia_famID;
static jclass isa_class;        /* java.net.InetSocketAddress */
static jclass ia_class;
static jmethodID isa_ctorID;    /*   .InetSocketAddress(InetAddress, int) */
static jmethodID ia_ctorID;

/*
 * Returns JNI_TRUE if DatagramChannelImpl has already cached an
 * InetAddress/port corresponding to the socket address.
 */
static jboolean isSenderCached(JNIEnv *env, jobject this, struct sockaddr_in *sa) {
    jobject senderAddr;

    /* shouldn't happen until we have dual IPv4/IPv6 stack (post-XP ?) */
    if (sa->sin_family != AF_INET) {
        return JNI_FALSE;
    }

    /*
     * Compare source address to cached InetAddress
     */
    senderAddr = (*env)->GetObjectField(env, this, dci_senderAddrID);
    if (senderAddr == NULL) {
        return JNI_FALSE;
    }
    if ((jint)ntohl(sa->sin_addr.s_addr) !=
        (*env)->GetIntField(env, senderAddr, ia_addrID)) {
        return JNI_FALSE;
    }

    /*
     * Compare source port to cached port
     */
    if ((jint)ntohs(sa->sin_port) !=
        (*env)->GetIntField(env, this, dci_senderPortID)) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_DatagramChannelImpl_initIDs(JNIEnv *env, jclass clazz)
{
    clazz = (*env)->FindClass(env, "java/net/InetSocketAddress");
    isa_class = (*env)->NewGlobalRef(env, clazz);
    isa_ctorID = (*env)->GetMethodID(env, clazz, "<init>",
                                     "(Ljava/net/InetAddress;I)V");
    isa_addrID = (*env)->GetFieldID(env, clazz, "addr",
                                    "Ljava/net/InetAddress;");
    isa_portID = (*env)->GetFieldID(env, clazz, "port", "I");

    clazz = (*env)->FindClass(env, "sun/nio/ch/DatagramChannelImpl");
    dci_senderID = (*env)->GetFieldID(env, clazz, "sender",
                                      "Ljava/net/SocketAddress;");
    dci_senderAddrID = (*env)->GetFieldID(env, clazz,
                                          "cachedSenderInetAddress",
                                          "Ljava/net/InetAddress;");
    dci_senderPortID = (*env)->GetFieldID(env, clazz,
                                          "cachedSenderPort", "I");
    clazz = (*env)->FindClass(env, "java/net/Inet4Address");
    ia_class = (*env)->NewGlobalRef(env, clazz);
    ia_addrID = (*env)->GetFieldID(env, clazz, "address", "I");
    ia_famID = (*env)->GetFieldID(env, clazz, "family", "I");
    ia_ctorID = (*env)->GetMethodID(env, clazz, "<init>", "()V");
}

/*
 * Return JNI_TRUE if this Windows edition supports ICMP Port Unreachable
 */
__inline static jboolean supportPortUnreachable() {
    static jboolean initDone;
    static jboolean portUnreachableSupported;

    if (!initDone) {
        OSVERSIONINFO ver;
        ver.dwOSVersionInfoSize = sizeof(ver);
        GetVersionEx(&ver);
        if (ver.dwPlatformId == VER_PLATFORM_WIN32_NT && ver.dwMajorVersion >= 5) {
            portUnreachableSupported = JNI_TRUE;
        } else {
            portUnreachableSupported = JNI_FALSE;
        }
        initDone = JNI_TRUE;
    }
    return portUnreachableSupported;
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
    struct sockaddr_in rmtaddr;
    int addrlen = sizeof(rmtaddr);

    /*
     * A no-op if this OS doesn't support it.
     */
    if (!supportPortUnreachable()) {
        return JNI_FALSE;
    }

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
                     (struct sockaddr *)&rmtaddr, &addrlen) != SOCKET_ERROR) {
            break;
        }
        if (WSAGetLastError() != WSAECONNRESET) {
            /* some other error - we don't care here */
            break;
        }

        recvfrom(fd, buf, 1, 0,  (struct sockaddr *)&rmtaddr, &addrlen);
        got_icmp = JNI_TRUE;
    }

    return got_icmp;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_DatagramChannelImpl_disconnect0(JNIEnv *env, jobject this,
                                                jobject fdo)
{
    jint fd = fdval(env, fdo);
    int rv = 0;
    struct sockaddr_in psa;
    int sa_len = sizeof(psa);

    memset(&psa, 0, sa_len);

    rv = connect((SOCKET)fd, (struct sockaddr *)&psa, sa_len);
    if (rv == SOCKET_ERROR) {
        handleSocketError(env, WSAGetLastError());
    }
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_DatagramChannelImpl_receive0(JNIEnv *env, jobject this,
                                            jobject fdo, jlong address,
                                            jint len, jboolean connected)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);
    struct sockaddr_in psa;
    int sa_len = sizeof(psa);
    BOOL retry = FALSE;
    jint n;

    do {
        retry = FALSE;
        n = recvfrom((SOCKET)fd,
                     (char *)buf,
                     len,
                     0,
                     (struct sockaddr *)&psa,
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

    if (!isSenderCached(env, this, &psa)) {
        int port = ntohs(psa.sin_port);
        jobject ia = (*env)->NewObject(env, ia_class, ia_ctorID);
        jobject isa = NULL;

        if (psa.sin_family != AF_INET) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                            "Protocol family unavailable");
        }

        if (ia != NULL) {
            // populate InetAddress (assumes AF_INET)
            (*env)->SetIntField(env, ia, ia_addrID, ntohl(psa.sin_addr.s_addr));

            // create InetSocketAddress
            isa = (*env)->NewObject(env, isa_class, isa_ctorID, ia, port);
        }

        if (isa == NULL) {
            JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
            return IOS_THROWN;
        }

        // update cachedSenderInetAddress/cachedSenderPort
        (*env)->SetObjectField(env, this, dci_senderAddrID, ia);
        (*env)->SetIntField(env, this, dci_senderPortID, port);

        // update sender
        (*env)->SetObjectField(env, this, dci_senderID, isa);
    }
    return n;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_DatagramChannelImpl_send0(JNIEnv *env, jobject this,
                                            jobject fdo, jlong address,
                                            jint len, jobject dest)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);
    SOCKETADDRESS psa;
    int sa_len = sizeof(psa);
    jint rv = 0;
    jobject destAddress = (*env)->GetObjectField(env, dest, isa_addrID);
    jint destPort = (*env)->GetIntField(env, dest, isa_portID);


    if (NET_InetAddressToSockaddr(env, destAddress, destPort,
                                  (struct sockaddr *)&psa,
                                   &sa_len, JNI_FALSE) != 0) {
      return IOS_THROWN;
    }

    rv = sendto((SOCKET)fd,
               buf,
               len,
               0,
               (struct sockaddr *)&psa,
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
