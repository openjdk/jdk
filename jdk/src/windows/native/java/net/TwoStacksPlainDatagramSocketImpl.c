/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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

#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <malloc.h>
#include <sys/types.h>

#ifndef IPTOS_TOS_MASK
#define IPTOS_TOS_MASK 0x1e
#endif
#ifndef IPTOS_PREC_MASK
#define IPTOS_PREC_MASK 0xe0
#endif

#include "java_net_TwoStacksPlainDatagramSocketImpl.h"
#include "java_net_SocketOptions.h"
#include "java_net_NetworkInterface.h"

#include "jvm.h"
#include "jni_util.h"
#include "net_util.h"

#define IN_CLASSD(i)    (((long)(i) & 0xf0000000) == 0xe0000000)
#define IN_MULTICAST(i) IN_CLASSD(i)

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

static CRITICAL_SECTION sizeCheckLock;

/* Windows OS version is XP or better */
static int xp_or_later = 0;
/* Windows OS version is Windows 2000 or better */
static int w2k_or_later = 0;

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
    static jclass i_class;
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

    return ( (*env)->NewObject(env, i_class, i_ctrID, i) );
}

/*
 * Returns a java.lang.Boolean based on 'b'
 */
jobject createBoolean(JNIEnv *env, int b) {
    static jclass b_class;
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

    return( (*env)->NewObject(env, b_class, b_ctrID, (jboolean)(b!=0)) );
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
 * This function returns JNI_TRUE if the datagram size exceeds the underlying
 * provider's ability to send to the target address. The following OS
 * oddies have been observed :-
 *
 * 1. On Windows 95/98 if we try to send a datagram > 12k to an application
 *    on the same machine then the send will fail silently.
 *
 * 2. On Windows ME if we try to send a datagram > supported by underlying
 *    provider then send will not return an error.
 *
 * 3. On Windows NT/2000 if we exceeds the maximum size then send will fail
 *    with WSAEADDRNOTAVAIL.
 *
 * 4. On Windows 95/98 if we exceed the maximum size when sending to
 *    another machine then WSAEINVAL is returned.
 *
 */
jboolean exceedSizeLimit(JNIEnv *env, jint fd, jint addr, jint size)
{
#define DEFAULT_MSG_SIZE        65527
    static jboolean initDone;
    static jboolean is95or98;
    static int maxmsg;

    typedef struct _netaddr  {          /* Windows 95/98 only */
        unsigned long addr;
        struct _netaddr *next;
    } netaddr;
    static netaddr *addrList;
    netaddr *curr;

    /*
     * First time we are called we must determine which OS this is and also
     * get the maximum size supported by the underlying provider.
     *
     * In addition on 95/98 we must enumerate our IP addresses.
     */
    if (!initDone) {
        EnterCriticalSection(&sizeCheckLock);

        if (initDone) {
            /* another thread got there first */
            LeaveCriticalSection(&sizeCheckLock);

        } else {
            OSVERSIONINFO ver;
            int len;

            /*
             * Step 1: Determine which OS this is.
             */
            ver.dwOSVersionInfoSize = sizeof(ver);
            GetVersionEx(&ver);

            is95or98 = JNI_FALSE;
            if (ver.dwPlatformId == VER_PLATFORM_WIN32_WINDOWS &&
                ver.dwMajorVersion == 4 &&
                (ver.dwMinorVersion == 0 || ver.dwMinorVersion == 10)) {

                is95or98 = JNI_TRUE;
            }

            /*
             * Step 2: Determine the maximum datagram supported by the
             * underlying provider. On Windows 95 if winsock hasn't been
             * upgraded (ie: unsupported configuration) then we assume
             * the default 64k limit.
             */
            len = sizeof(maxmsg);
            if (NET_GetSockOpt(fd, SOL_SOCKET, SO_MAX_MSG_SIZE, (char *)&maxmsg, &len) < 0) {
                maxmsg = DEFAULT_MSG_SIZE;
            }

            /*
             * Step 3: On Windows 95/98 then enumerate the IP addresses on
             * this machine. This is necesary because we need to check if the
             * datagram is being sent to an application on the same machine.
             */
            if (is95or98) {
                char hostname[255];
                struct hostent *hp;

                if (gethostname(hostname, sizeof(hostname)) == -1) {
                    LeaveCriticalSection(&sizeCheckLock);
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Unable to obtain hostname");
                    return JNI_TRUE;
                }
                hp = (struct hostent *)gethostbyname(hostname);
                if (hp != NULL) {
                    struct in_addr **addrp = (struct in_addr **) hp->h_addr_list;

                    while (*addrp != (struct in_addr *) 0) {
                        curr = (netaddr *)malloc(sizeof(netaddr));
                        if (curr == NULL) {
                            while (addrList != NULL) {
                                curr = addrList->next;
                                free(addrList);
                                addrList = curr;
                            }
                            LeaveCriticalSection(&sizeCheckLock);
                            JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
                            return JNI_TRUE;
                        }
                        curr->addr = htonl((*addrp)->S_un.S_addr);
                        curr->next = addrList;
                        addrList = curr;
                        addrp++;
                    }
                }
            }

            /*
             * Step 4: initialization is done so set flag and unlock cs
             */
            initDone = JNI_TRUE;
            LeaveCriticalSection(&sizeCheckLock);
        }
    }

    /*
     * Now examine the size of the datagram :-
     *
     * (a) If exceeds size of service provider return 'false' to indicate that
     *     we exceed the limit.
     * (b) If not 95/98 then return 'true' to indicate that the size is okay.
     * (c) On 95/98 if the size is <12k we are okay.
     * (d) On 95/98 if size > 12k then check if the destination is the current
     *     machine.
     */
    if (size > maxmsg) {        /* step (a) */
        return JNI_TRUE;
    }
    if (!is95or98) {            /* step (b) */
        return JNI_FALSE;
    }
    if (size <= 12280) {        /* step (c) */
        return JNI_FALSE;
    }

    /* step (d) */

    if ((addr & 0x7f000000) == 0x7f000000) {
        return JNI_TRUE;
    }
    curr = addrList;
    while (curr != NULL) {
        if (curr->addr == addr) {
            return JNI_TRUE;
        }
        curr = curr->next;
    }
    return JNI_FALSE;
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
static jboolean purgeOutstandingICMP(JNIEnv *env, jobject this, jint fd)
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
    FD_SET(fd, &tbl);
    while(1) {
        if (select(/*ignored*/fd+1, &tbl, 0, 0, &t) <= 0) {
            break;
        }
        if (recvfrom(fd, buf, 1, MSG_PEEK,
                         (struct sockaddr *)&rmtaddr, &addrlen) != JVM_IO_ERR) {
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


/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_init(JNIEnv *env, jclass cls) {

    OSVERSIONINFO ver;
    int version;
    ver.dwOSVersionInfoSize = sizeof(ver);
    GetVersionEx(&ver);

    version = ver.dwMajorVersion * 10 + ver.dwMinorVersion;
    xp_or_later = (ver.dwPlatformId == VER_PLATFORM_WIN32_NT) && (version >= 51);
    w2k_or_later = (ver.dwPlatformId == VER_PLATFORM_WIN32_NT) && (version >= 50);

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


    InitializeCriticalSection(&sizeCheckLock);
}

JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_bind0(JNIEnv *env, jobject this,
                                           jint port, jobject addressObj) {
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);

    int fd, fd1, family;
    int ipv6_supported = ipv6_available();

    SOCKETADDRESS lcladdr;
    int lcladdrlen;
    int address;

    family = (*env)->GetIntField(env, addressObj, ia_familyID);
    if (family == IPv6 && !ipv6_supported) {
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
    } else {
        address = (*env)->GetIntField(env, addressObj, ia_addressID);
    }

    if (NET_InetAddressToSockaddr(env, addressObj, port, (struct sockaddr *)&lcladdr, &lcladdrlen, JNI_FALSE) != 0) {
      return;
    }

    if (ipv6_supported) {
        struct ipv6bind v6bind;
        v6bind.addr = &lcladdr;
        v6bind.ipv4_fd = fd;
        v6bind.ipv6_fd = fd1;
        if (NET_BindV6(&v6bind) != -1) {
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
            NET_ThrowCurrent (env, "Cannot bind");
            return;
        }
    } else {
        if (bind(fd, (struct sockaddr *)&lcladdr, lcladdrlen) == -1) {
            if (WSAGetLastError() == WSAEACCES) {
                WSASetLastError(WSAEADDRINUSE);
            }
            NET_ThrowCurrent(env, "Cannot bind");
            return;
        }
    }

    if (port == 0) {
        if (fd == -1) {
            /* must be an IPV6 only socket. */
            fd = fd1;
        }
        if (getsockname(fd, (struct sockaddr *)&lcladdr, &lcladdrlen) == -1) {
            NET_ThrowCurrent(env, "JVM_GetSockName");
            return;
        }
        port = ntohs((u_short) GET_PORT (&lcladdr));
    }
    (*env)->SetIntField(env, this, pdsi_localPortID, port);
}


/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    connect0
 * Signature: (Ljava/net/InetAddress;I)V
 */

JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_connect0(JNIEnv *env, jobject this,
                                               jobject address, jint port) {
    /* The object's field */
    jobject fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
    jobject fd1Obj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
    /* The fdObj'fd */
    jint fd=-1, fd1=-1, fdc;
    /* The packetAddress address, family and port */
    jint addr, family;
    SOCKETADDRESS rmtaddr;
    int rmtaddrlen;
    int ipv6_supported = ipv6_available();

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

    addr = (*env)->GetIntField(env, address, ia_addressID);

    family = (*env)->GetIntField(env, address, ia_familyID);
    if (family == IPv6 && !ipv6_supported) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Protocol family not supported");
        return;
    }

    fdc = family == IPv4? fd: fd1;

    if (xp_or_later) {
        /* SIO_UDP_CONNRESET fixes a bug introduced in Windows 2000, which
         * returns connection reset errors un connected UDP sockets (as well
         * as connected sockets. The solution is to only enable this feature
         * when the socket is connected
         */
        DWORD x1, x2; /* ignored result codes */
        int res, t = TRUE;
        res = WSAIoctl(fdc,SIO_UDP_CONNRESET,&t,sizeof(t),&x1,sizeof(x1),&x2,0,0);
    }

    if (NET_InetAddressToSockaddr(env, address, port,(struct sockaddr *)&rmtaddr, &rmtaddrlen, JNI_FALSE) != 0) {
      return;
    }

    if (connect(fdc, (struct sockaddr *)&rmtaddr, sizeof(rmtaddr)) == -1) {
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

    if (family == IPv4) {
        fdObj = (*env)->GetObjectField(env, this, pdsi_fdID);
        len = sizeof (struct sockaddr_in);
    } else {
        fdObj = (*env)->GetObjectField(env, this, pdsi_fd1ID);
        len = sizeof (struct SOCKADDR_IN6);
    }

    if (IS_NULL(fdObj)) {
        /* disconnect doesn't throw any exceptions */
        return;
    }
    fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);

    memset(&addr, 0, len);
    connect(fd, (struct sockaddr *)&addr, len);

    /*
     * use SIO_UDP_CONNRESET
     * to disable ICMP port unreachable handling here.
     */
    if (xp_or_later) {
        DWORD x1, x2; /* ignored result codes */
        int t = FALSE;
        WSAIoctl(fd,SIO_UDP_CONNRESET,&t,sizeof(t),&x1,sizeof(x1),&x2,0,0);
    }
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    send
 * Signature: (Ljava/net/DatagramPacket;)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_send(JNIEnv *env, jobject this,
                                           jobject packet) {

    char BUF[MAX_BUFFER_LEN];
    char *fullPacket;
    jobject fdObj;
    jint fd;

    jobject iaObj;
    jint address;
    jint family;

    jint packetBufferOffset, packetBufferLen, packetPort;
    jbyteArray packetBuffer;
    jboolean connected;

    SOCKETADDRESS rmtaddr;
    SOCKETADDRESS *addrp = &rmtaddr;
    int addrlen;


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

    family = (*env)->GetIntField(env, iaObj, ia_familyID);
    if (family == IPv4) {
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

    if (connected) {
        addrp = 0; /* arg to JVM_Sendto () null in this case */
        addrlen = 0;
    } else {
      if (NET_InetAddressToSockaddr(env, iaObj, packetPort, (struct sockaddr *)&rmtaddr, &addrlen, JNI_FALSE) != 0) {
        return;
      }
    }

    if (packetBufferLen > MAX_BUFFER_LEN) {

        /*
         * On 95/98 if we try to send a datagram >12k to an application
         * on the same machine then this will fail silently. Thus we
         * catch this situation here so that we can throw an exception
         * when this arises.
         * On ME if we try to send a datagram with a size greater than
         * that supported by the service provider then no error is
         * returned.
         */
        if (!w2k_or_later) { /* avoid this check on Win 2K or better. Does not work with IPv6.
                      * Check is not necessary on these OSes */
            if (connected) {
                address = (*env)->GetIntField(env, iaObj, ia_addressID);
            } else {
                address = ntohl(rmtaddr.him4.sin_addr.s_addr);
            }

            if (exceedSizeLimit(env, fd, address, packetBufferLen)) {
                if (!((*env)->ExceptionOccurred(env))) {
                    NET_ThrowNew(env, WSAEMSGSIZE, "Datagram send failed");
                }
                return;
            }
        }

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
            JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
            return;
        }
    } else {
        fullPacket = &(BUF[0]);
    }

    (*env)->GetByteArrayRegion(env, packetBuffer, packetBufferOffset, packetBufferLen,
                               (jbyte *)fullPacket);
    switch (sendto(fd, fullPacket, packetBufferLen, 0,
                       (struct sockaddr *)addrp, addrlen)) {
        case JVM_IO_ERR:
            NET_ThrowCurrent(env, "Datagram send failed");
            break;

        case JVM_IO_INTR:
            JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                            "operation interrupted");
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
    struct sockaddr_in remote_addr;
    jint remote_addrsize = sizeof (remote_addr);
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
    } else {
        address = (*env)->GetIntField(env, addressObj, ia_addressID);
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
            } else if (ret == JVM_IO_ERR) {
                NET_ThrowCurrent(env, "timeout in datagram socket peek");
                return ret;
            } else if (ret == JVM_IO_INTR) {
                JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                                "operation interrupted");
                return ret;
            }
        }

        /* now try the peek */
        n = recvfrom(fd, buf, 1, MSG_PEEK,
                         (struct sockaddr *)&remote_addr, &remote_addrsize);

        if (n == JVM_IO_ERR) {
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

    if (n == JVM_IO_ERR && WSAGetLastError() != WSAEMSGSIZE) {
        NET_ThrowCurrent(env, "Datagram peek failed");
        return 0;
    }
    if (n == JVM_IO_INTR) {
        JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException", 0);
        return 0;
    }
    (*env)->SetIntField(env, addressObj, ia_addressID,
                        ntohl(remote_addr.sin_addr.s_addr));
    (*env)->SetIntField(env, addressObj, ia_familyID, IPv4);

    /* return port */
    return ntohs(remote_addr.sin_port);
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

    int fd, fd1, fduse, nsockets=0, errorCode;
    int port;

    int checkBoth = 0;
    int n;
    SOCKETADDRESS remote_addr;
    jint remote_addrsize=sizeof(remote_addr);
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
            JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
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
                } else if (ret == JVM_IO_ERR) {
                    NET_ThrowCurrent(env, "timeout in datagram socket peek");
                } else if (ret == JVM_IO_INTR) {
                    JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                                    "operation interrupted");
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
                } else if (ret == JVM_IO_ERR) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                    "Socket closed");
                } else if (ret == JVM_IO_INTR) {
                    JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                                    "operation interrupted");
                }
                if (packetBufferLen > MAX_BUFFER_LEN) {
                    free(fullPacket);
                }
                return -1;
            }
        }

        /* receive the packet */
        n = recvfrom(fduse, fullPacket, packetBufferLen, MSG_PEEK,
                         (struct sockaddr *)&remote_addr, &remote_addrsize);
        port = (int) ntohs ((u_short) GET_PORT((SOCKETADDRESS *)&remote_addr));
        if (n == JVM_IO_ERR) {
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
    } else if (n == -2) {
        JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                        "operation interrupted");
    } else if (n < 0) {
        NET_ThrowCurrent(env, "Datagram receive failed");
    } else {
        jobject packetAddress;

        /*
         * Check if there is an InetAddress already associated with this
         * packet. If so we check if it is the same source address. We
         * can't update any existing InetAddress because it is immutable
         */
        packetAddress = (*env)->GetObjectField(env, packet, dp_addressID);
        if (packetAddress != NULL) {
            if (!NET_SockaddrEqualsInetAddress(env, (struct sockaddr *)
                                                &remote_addr, packetAddress)) {
                /* force a new InetAddress to be created */
                packetAddress = NULL;
            }
        }
        if (packetAddress == NULL) {
            packetAddress = NET_SockaddrToInetAddress(env, (struct sockaddr *)
                                &remote_addr, &port);
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
    int fd, fd1, fduse, errorCode;

    int n, nsockets=0;
    SOCKETADDRESS remote_addr;
    jint remote_addrsize=sizeof(remote_addr);
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
                } else if (ret == JVM_IO_ERR) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                    "Socket closed");
                } else if (ret == JVM_IO_INTR) {
                    JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                                    "operation interrupted");
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
            JNU_ThrowOutOfMemoryError(env, "heap allocation failed");
            return;
        }
    } else {
        fullPacket = &(BUF[0]);
    }



    /*
     * If this Windows edition supports ICMP port unreachable and if we
     * are not connected then we need to know if a timeout has been specified
     * and if so we need to pick up the current time. These are required in
     * order to implement the semantics of timeout, viz :-
     * timeout set to t1 but ICMP port unreachable arrives in t2 where
     * t2 < t1. In this case we must discard the ICMP packets and then
     * wait for the next packet up to a maximum of t1 minus t2.
     */
    connected = (*env)->GetBooleanField(env, this, pdsi_connected);
    if (supportPortUnreachable() && !connected && timeout &&!ipv6_supported) {
        prevTime = JVM_CurrentTimeMillis(env, 0);
    }

    if (timeout && nsockets == 1) {
        int ret;
        ret = NET_Timeout(fduse, timeout);
        if (ret <= 0) {
            if (ret == 0) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                                "Receive timed out");
            } else if (ret == JVM_IO_ERR) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                "Socket closed");
            } else if (ret == JVM_IO_INTR) {
                JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                                "operation interrupted");
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
        n = recvfrom(fduse, fullPacket, packetBufferLen, 0,
                         (struct sockaddr *)&remote_addr, &remote_addrsize);

        if (n == JVM_IO_ERR) {
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
                        } else if (ret == JVM_IO_ERR) {
                            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                                            "Socket closed");
                        } else if (ret == JVM_IO_INTR) {
                            JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                                            "operation interrupted");
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
        int port;
        jobject packetAddress;

        /*
         * Check if there is an InetAddress already associated with this
         * packet. If so we check if it is the same source address. We
         * can't update any existing InetAddress because it is immutable
         */
        packetAddress = (*env)->GetObjectField(env, packet, dp_addressID);

        if (packetAddress != NULL) {
            if (!NET_SockaddrEqualsInetAddress(env, (struct sockaddr *)&remote_addr, packetAddress)) {
                /* force a new InetAddress to be created */
                packetAddress = NULL;
            }
        }
        if (packetAddress == NULL) {
            packetAddress = NET_SockaddrToInetAddress(env, (struct sockaddr *)&remote_addr, &port);
            /* stuff the new Inetaddress in the packet */
            (*env)->SetObjectField(env, packet, dp_addressID, packetAddress);
        } else {
            /* only get the new port number */
            port = NET_GetPortFromSockaddr((struct sockaddr *)&remote_addr);
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
    if (fd == JVM_IO_ERR) {
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
        if (fd1 == JVM_IO_ERR) {
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
    int fd=-1, fd1=-1;

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
    static jfieldID ia_familyID=0;
    jsize len;
    jobject addr;
    int i;

    if (ni_addrsID == NULL || ia_familyID == NULL) {
        jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
        CHECK_NULL_RETURN (c, -1);
        ni_addrsID = (*env)->GetFieldID(env, c, "addrs",
                                        "[Ljava/net/InetAddress;");
        CHECK_NULL_RETURN (ni_addrsID, -1);
        c = (*env)->FindClass(env,"java/net/InetAddress");
        CHECK_NULL_RETURN (c, -1);
        ia_familyID = (*env)->GetFieldID(env, c, "family", "I");
        CHECK_NULL_RETURN (ia_familyID, -1);
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
        fam = (*env)->GetIntField(env, addr, ia_familyID);
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
    static jfieldID ia_addressID;

    int ret = getInetAddrFromIf (env, IPv4, nif, &addr);
    if (ret == -1) {
        return -1;
    }

    if (ia_addressID == 0) {
        jclass c = (*env)->FindClass(env,"java/net/InetAddress");
        CHECK_NULL_RETURN (c, -1);
        ia_addressID = (*env)->GetFieldID(env, c, "address", "I");
        CHECK_NULL_RETURN (ia_addressID, -1);
    }
    iaddr->s_addr = htonl((*env)->GetIntField(env, addr, ia_addressID));
    return 0;
}

/* Get the multicasting index from the interface */

static int getIndexFromIf (JNIEnv *env, jobject nif) {
    static jfieldID ni_indexID;

    if (ni_indexID == NULL) {
        jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
        CHECK_NULL_RETURN(c, -1);
        ni_indexID = (*env)->GetFieldID(env, c, "index", "I");
        CHECK_NULL_RETURN(ni_indexID, -1);
    }

    return (*env)->GetIntField(env, nif, ni_indexID);
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
            static jclass ni_class;
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
            static jfieldID ia_addressID;
            struct in_addr in;

            if (ia_addressID == NULL) {
                        jclass c = (*env)->FindClass(env,"java/net/InetAddress");
                CHECK_NULL(c);
                ia_addressID = (*env)->GetFieldID(env, c, "address", "I");
                CHECK_NULL(ia_addressID);
            }

            in.s_addr = htonl((*env)->GetIntField(env, value, ia_addressID));

            if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF,
                               (const char*)&in, sizeof(in)) < 0) {
                NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "Error setting socket option");
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
            static jfieldID ni_indexID;
            struct in_addr in;
            int index;

            if (ni_indexID == NULL) {
                jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
                CHECK_NULL(c);
                ni_indexID = (*env)->GetFieldID(env, c, "index", "I");
                CHECK_NULL(ni_indexID);
            }
            index = (*env)->GetIntField(env, value, ni_indexID);

            if (setsockopt(fd1, IPPROTO_IPV6, IPV6_MULTICAST_IF,
                               (const char*)&index, sizeof(index)) < 0) {
                if (errno == EINVAL && index > 0) {
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "IPV6_MULTICAST_IF failed (interface has IPv4 "
                        "address only?)");
                } else {
                    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                   "Error setting socket option");
                }
                return;
            }

            /* If there are any IPv4 addresses on this interface then
             * repeat the operation on the IPv4 fd */

            if (getInet4AddrFromIf (env, value, &in) < 0) {
                return;
            }
            if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF,
                               (const char*)&in, sizeof(in)) < 0) {
                NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                 "Error setting socket option");
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
                NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                               "Error setting socket option");
            }
            return;
        }
    }
}

/*
 * Class:     java_net_TwoStacksPlainDatagramSocketImpl
 * Method:    socketSetOption
 * Signature: (ILjava/lang/Object;)V
 */
JNIEXPORT void JNICALL
Java_java_net_TwoStacksPlainDatagramSocketImpl_socketSetOption(JNIEnv *env,jobject this,
                                                      jint opt,jobject value) {

    int fd=-1, fd1=-1;
    int levelv4, levelv6, optnamev4, optnamev6, optlen;
    union {
        int i;
        char c;
    } optval;
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
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Invalid option");
            return;
        }
    }
    if (fd != -1) {
        if (NET_MapSocketOption(opt, &levelv4, &optnamev4)) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Invalid option");
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
            break;

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
        static jclass inet4_class;
        static jmethodID inet4_ctrID;
        static jfieldID inet4_addrID;

        static jclass ni_class;
        static jmethodID ni_ctrID;
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
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                             "Error getting socket option");
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
            inet4_addrID = (*env)->GetFieldID(env, c, "address", "I");
            CHECK_NULL_RETURN(inet4_addrID, NULL);
            inet4_class = (*env)->NewGlobalRef(env, c);
            CHECK_NULL_RETURN(inet4_class, NULL);
        }
        addr = (*env)->NewObject(env, inet4_class, inet4_ctrID, 0);
        CHECK_NULL_RETURN(addr, NULL);

        (*env)->SetIntField(env, addr, inet4_addrID, ntohl(in.s_addr));

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

        /*
         * The address doesn't appear to be bound at any known
         * NetworkInterface. Therefore we construct a NetworkInterface
         * with this address.
         */
        ni = (*env)->NewObject(env, ni_class, ni_ctrID, 0);
        CHECK_NULL_RETURN(ni, NULL);

        (*env)->SetIntField(env, ni, ni_indexID, -1);
        addrArray = (*env)->NewObjectArray(env, 1, inet4_class, NULL);
        CHECK_NULL_RETURN(addrArray, NULL);
        (*env)->SetObjectArrayElement(env, addrArray, 0, addr);
        (*env)->SetObjectField(env, ni, ni_addrsID, addrArray);
        return ni;
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
                NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                               "Error getting socket option");
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
Java_java_net_TwoStacksPlainDatagramSocketImpl_socketGetOption(JNIEnv *env, jobject this,
                                                      jint opt) {

    int fd=-1, fd1=-1;
    int level, optname, optlen;
    union {
        int i;
    } optval;
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

    if (opt == java_net_SocketOptions_SO_BINDADDR) {
        /* find out local IP address */
        SOCKETADDRESS him;
        int len = 0;
        int port;
        jobject iaObj;

        len = sizeof (struct sockaddr_in);

        if (fd == -1) {
            fd = fd1; /* must be IPv6 only */
            len = sizeof (struct SOCKADDR_IN6);
        }

        if (getsockname(fd, (struct sockaddr *)&him, &len) == -1) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                           "Error getting socket name");
            return NULL;
        }
        iaObj = NET_SockaddrToInetAddress(env, (struct sockaddr *)&him, &port);

        return iaObj;
    }

    /*
     * Map the Java level socket option to the platform specific
     * level and option name.
     */
    if (NET_MapSocketOption(opt, &level, &optname)) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Invalid option");
        return NULL;
    }

    if (fd == -1) {
        if (NET_MapSocketOptionV6(opt, &level, &optname)) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Invalid option");
            return NULL;
        }
        fd = fd1; /* must be IPv6 only */
    }

    optlen = sizeof(optval.i);
    if (NET_GetSockOpt(fd, level, optname, (void *)&optval, &optlen) < 0) {
        char errmsg[255];
        sprintf(errmsg, "error getting socket option: %s\n", strerror(errno));
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
    DWORD ifindex;

    int len, family;
    int ipv6_supported = ipv6_available();
    int cmd ;

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

    if (NET_InetAddressToSockaddr(env, iaObj, 0, (struct sockaddr *)&name, &len, JNI_FALSE) != 0) {
      return;
    }

    /* Set the multicast group address in the ip_mreq field
     * eventually this check should be done by the security manager
     */
    family = name.him.sa_family;

    if (family == AF_INET) {
        int address = name.him4.sin_addr.s_addr;
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
            len = sizeof (in);
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
            address = &name.him6.sin6_addr;
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
