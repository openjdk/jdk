/*
 * Copyright (c) 2000, 2011, Oracle and/or its affiliates. All rights reserved.
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
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <malloc.h>
#include <sys/types.h>
#include <process.h>

#include "java_net_InetAddress.h"
#include "java_net_Inet4AddressImpl.h"
#include "java_net_Inet6AddressImpl.h"
#include "net_util.h"
#include "icmp.h"

#ifdef WIN32
#ifndef _WIN64

/* Retain this code a little longer to support building in
 * old environments.  _MSC_VER is defined as:
 *     1200 for MSVC++ 6.0
 *     1310 for Vc7
 */
#if defined(_MSC_VER) && _MSC_VER < 1310
#define sockaddr_in6 SOCKADDR_IN6
#endif
#endif
#define uint32_t UINT32
#endif

/*
 * Inet6AddressImpl
 */

/*
 * Class:     java_net_Inet6AddressImpl
 * Method:    getLocalHostName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet6AddressImpl_getLocalHostName (JNIEnv *env, jobject this) {
    char hostname [256];

    if (gethostname (hostname, sizeof (hostname)) == -1) {
        strcpy (hostname, "localhost");
    }
    return JNU_NewStringPlatform (env, hostname);
}

static jclass ni_iacls;
static jclass ni_ia4cls;
static jclass ni_ia6cls;
static jmethodID ni_ia4ctrID;
static jmethodID ni_ia6ctrID;
static jfieldID ni_iaaddressID;
static jfieldID ni_iahostID;
static jfieldID ni_iafamilyID;
static jfieldID ni_ia6ipaddressID;
static int initialized = 0;

JNIEXPORT jobjectArray JNICALL
Java_java_net_Inet6AddressImpl_lookupAllHostAddr(JNIEnv *env, jobject this,
                                                jstring host) {
    const char *hostname;
    jobjectArray ret = 0;
    int retLen = 0;
    jboolean preferIPv6Address;
    static jfieldID ia_preferIPv6AddressID;

    int error=0;
    struct addrinfo hints, *res, *resNew = NULL;

    if (!initialized) {
      ni_iacls = (*env)->FindClass(env, "java/net/InetAddress");
      ni_iacls = (*env)->NewGlobalRef(env, ni_iacls);
      ni_ia4cls = (*env)->FindClass(env, "java/net/Inet4Address");
      ni_ia4cls = (*env)->NewGlobalRef(env, ni_ia4cls);
      ni_ia6cls = (*env)->FindClass(env, "java/net/Inet6Address");
      ni_ia6cls = (*env)->NewGlobalRef(env, ni_ia6cls);
      ni_ia4ctrID = (*env)->GetMethodID(env, ni_ia4cls, "<init>", "()V");
      ni_ia6ctrID = (*env)->GetMethodID(env, ni_ia6cls, "<init>", "()V");
      ni_iaaddressID = (*env)->GetFieldID(env, ni_iacls, "address", "I");
      ni_iafamilyID = (*env)->GetFieldID(env, ni_iacls, "family", "I");
      ni_iahostID = (*env)->GetFieldID(env, ni_iacls, "hostName", "Ljava/lang/String;");
      ni_ia6ipaddressID = (*env)->GetFieldID(env, ni_ia6cls, "ipaddress", "[B");
      initialized = 1;
    }
    if (IS_NULL(host)) {
        JNU_ThrowNullPointerException(env, "host is null");
        return 0;
    }
    hostname = JNU_GetStringPlatformChars(env, host, JNI_FALSE);
    CHECK_NULL_RETURN(hostname, NULL);

    if (ia_preferIPv6AddressID == NULL) {
        jclass c = (*env)->FindClass(env,"java/net/InetAddress");
        if (c)  {
            ia_preferIPv6AddressID =
                (*env)->GetStaticFieldID(env, c, "preferIPv6Address", "Z");
        }
        if (ia_preferIPv6AddressID == NULL) {
            JNU_ReleaseStringPlatformChars(env, host, hostname);
            return NULL;
        }
    }
    /* get the address preference */
    preferIPv6Address
        = (*env)->GetStaticBooleanField(env, ia_class, ia_preferIPv6AddressID);

    /* Try once, with our static buffer. */
    memset(&hints, 0, sizeof(hints));
    hints.ai_flags = AI_CANONNAME;
    hints.ai_family = AF_UNSPEC;

    error = getaddrinfo(hostname, NULL, &hints, &res);

    if (error) {
        /* report error */
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException",
                        (char *)hostname);
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return NULL;
    } else {
        int i = 0;
        int inetCount = 0, inet6Count = 0, inetIndex, inet6Index;
        struct addrinfo *itr, *last, *iterator = res;
        while (iterator != NULL) {
            int skip = 0;
            itr = resNew;
            while (itr != NULL) {
                if (iterator->ai_family == itr->ai_family &&
                    iterator->ai_addrlen == itr->ai_addrlen) {
                    if (itr->ai_family == AF_INET) { /* AF_INET */
                        struct sockaddr_in *addr1, *addr2;
                        addr1 = (struct sockaddr_in *)iterator->ai_addr;
                        addr2 = (struct sockaddr_in *)itr->ai_addr;
                        if (addr1->sin_addr.s_addr ==
                            addr2->sin_addr.s_addr) {
                            skip = 1;
                            break;
                        }
                    } else {
                        int t;
                        struct sockaddr_in6 *addr1, *addr2;
                        addr1 = (struct sockaddr_in6 *)iterator->ai_addr;
                        addr2 = (struct sockaddr_in6 *)itr->ai_addr;

                        for (t = 0; t < 16; t++) {
                            if (addr1->sin6_addr.s6_addr[t] !=
                                addr2->sin6_addr.s6_addr[t]) {
                                break;
                            }
                        }
                        if (t < 16) {
                            itr = itr->ai_next;
                            continue;
                        } else {
                            skip = 1;
                            break;
                        }
                    }
                } else if (iterator->ai_family != AF_INET &&
                           iterator->ai_family != AF_INET6) {
                    /* we can't handle other family types */
                    skip = 1;
                    break;
                }
                itr = itr->ai_next;
            }

            if (!skip) {
                struct addrinfo *next
                    = (struct addrinfo*) malloc(sizeof(struct addrinfo));
                if (!next) {
                    JNU_ThrowOutOfMemoryError(env, "Native heap allocation failed");
                    ret = NULL;
                    goto cleanupAndReturn;
                }
                memcpy(next, iterator, sizeof(struct addrinfo));
                next->ai_next = NULL;
                if (resNew == NULL) {
                    resNew = next;
                } else {
                    last->ai_next = next;
                }
                last = next;
                i++;
                if (iterator->ai_family == AF_INET) {
                    inetCount ++;
                } else if (iterator->ai_family == AF_INET6) {
                    inet6Count ++;
                }
            }
            iterator = iterator->ai_next;
        }
        retLen = i;
        iterator = resNew;
        i = 0;
        ret = (*env)->NewObjectArray(env, retLen, ni_iacls, NULL);

        if (IS_NULL(ret)) {
            /* we may have memory to free at the end of this */
            goto cleanupAndReturn;
        }

        if (preferIPv6Address) {
            inetIndex = inet6Count;
            inet6Index = 0;
        } else {
            inetIndex = 0;
            inet6Index = inetCount;
        }

        while (iterator != NULL) {
            if (iterator->ai_family == AF_INET) {
              jobject iaObj = (*env)->NewObject(env, ni_ia4cls, ni_ia4ctrID);
              if (IS_NULL(iaObj)) {
                ret = NULL;
                goto cleanupAndReturn;
              }
              (*env)->SetIntField(env, iaObj, ni_iaaddressID,
                                  ntohl(((struct sockaddr_in*)iterator->ai_addr)->sin_addr.s_addr));
              (*env)->SetObjectField(env, iaObj, ni_iahostID, host);
              (*env)->SetObjectArrayElement(env, ret, inetIndex, iaObj);
                inetIndex ++;
            } else if (iterator->ai_family == AF_INET6) {
              jint scope = 0;
              jbyteArray ipaddress;
              jobject iaObj = (*env)->NewObject(env, ni_ia6cls, ni_ia6ctrID);
              if (IS_NULL(iaObj)) {
                ret = NULL;
                goto cleanupAndReturn;
              }
              ipaddress = (*env)->NewByteArray(env, 16);
              if (IS_NULL(ipaddress)) {
                ret = NULL;
                goto cleanupAndReturn;
              }
              (*env)->SetByteArrayRegion(env, ipaddress, 0, 16,
                                         (jbyte *)&(((struct sockaddr_in6*)iterator->ai_addr)->sin6_addr));
              scope = ((struct sockaddr_in6*)iterator->ai_addr)->sin6_scope_id;
              if (scope != 0) { /* zero is default value, no need to set */
                (*env)->SetIntField(env, iaObj, ia6_scopeidID, scope);
                (*env)->SetBooleanField(env, iaObj, ia6_scopeidsetID, JNI_TRUE);
              }
              (*env)->SetObjectField(env, iaObj, ni_ia6ipaddressID, ipaddress);
              (*env)->SetObjectField(env, iaObj, ni_iahostID, host);
              (*env)->SetObjectArrayElement(env, ret, inet6Index, iaObj);
              inet6Index ++;
            }
            iterator = iterator->ai_next;
        }
    }

cleanupAndReturn:
    {
        struct addrinfo *iterator, *tmp;
        iterator = resNew;
        while (iterator != NULL) {
            tmp = iterator;
            iterator = iterator->ai_next;
            free(tmp);
        }
        JNU_ReleaseStringPlatformChars(env, host, hostname);
    }

    freeaddrinfo(res);

    return ret;
}

/*
 * Class:     java_net_Inet6AddressImpl
 * Method:    getHostByAddr
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet6AddressImpl_getHostByAddr(JNIEnv *env, jobject this,
                                            jbyteArray addrArray) {
    jstring ret = NULL;

    char host[NI_MAXHOST+1];
    int error = 0;
    int len = 0;
    jbyte caddr[16];

    struct sockaddr_in him4;
    struct sockaddr_in6 him6;
    struct sockaddr *sa;

    /*
     * For IPv4 addresses construct a sockaddr_in structure.
     */
    if ((*env)->GetArrayLength(env, addrArray) == 4) {
        jint addr;
        (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
        addr = ((caddr[0]<<24) & 0xff000000);
        addr |= ((caddr[1] <<16) & 0xff0000);
        addr |= ((caddr[2] <<8) & 0xff00);
        addr |= (caddr[3] & 0xff);
        memset((char *) &him4, 0, sizeof(him4));
        him4.sin_addr.s_addr = (uint32_t) htonl(addr);
        him4.sin_family = AF_INET;
        sa = (struct sockaddr *) &him4;
        len = sizeof(him4);
    } else {
        /*
         * For IPv6 address construct a sockaddr_in6 structure.
         */
        (*env)->GetByteArrayRegion(env, addrArray, 0, 16, caddr);
        memset((char *) &him6, 0, sizeof(him6));
        memcpy((void *)&(him6.sin6_addr), caddr, sizeof(struct in6_addr) );
        him6.sin6_family = AF_INET6;
        sa = (struct sockaddr *) &him6 ;
        len = sizeof(him6) ;
    }

    error = getnameinfo(sa, len, host, NI_MAXHOST, NULL, 0, NI_NAMEREQD);

    if (!error) {
        ret = (*env)->NewStringUTF(env, host);
    }

    if (ret == NULL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", NULL);
    }

    return ret;
}

#ifdef AF_INET6


/**
 * ping implementation.
 * Send a ICMP_ECHO_REQUEST packet every second until either the timeout
 * expires or a answer is received.
 * Returns true is an ECHO_REPLY is received, otherwise, false.
 */
static jboolean
ping6(JNIEnv *env, jint fd, struct SOCKADDR_IN6* him, jint timeout,
      struct SOCKADDR_IN6* netif, jint ttl) {
    jint size;
    jint n, len, i;
    char sendbuf[1500];
    char auxbuf[1500];
    unsigned char recvbuf[1500];
    struct icmp6_hdr *icmp6;
    struct SOCKADDR_IN6 sa_recv;
    unsigned short pid, seq;
    int read_rv = 0;
    WSAEVENT hEvent;
    struct ip6_pseudo_hdr *pseudo_ip6;
    int timestamp;
    int tmout2;

    /* Initialize the sequence number to a suitable random number and
       shift right one place to allow sufficient room for increamenting. */
    seq = ((unsigned short)rand()) >> 1;

    /* icmp_id is a 16 bit data type, therefore down cast the pid */
    pid = (unsigned short) _getpid();

    size = 60*1024;
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, (const char *)&size, sizeof(size));
    /**
     * A TTL was specified, let's set the socket option.
     */
    if (ttl > 0) {
      setsockopt(fd, IPPROTO_IPV6, IPV6_UNICAST_HOPS, (const char *) &ttl, sizeof(ttl));
    }

    /**
     * A network interface was specified, let's bind to it.
     */
    if (netif != NULL) {
      if (NET_Bind(fd, (struct sockaddr*)netif, sizeof(struct sockaddr_in6)) < 0){
        NET_ThrowNew(env, WSAGetLastError(), "Can't bind socket to interface");
        closesocket(fd);
        return JNI_FALSE;
      }
    }

    /*
     * Make the socket non blocking
     */
    hEvent = WSACreateEvent();
    WSAEventSelect(fd, hEvent, FD_READ|FD_CONNECT|FD_CLOSE);

    /**
     * send 1 ICMP REQUEST every second until either we get a valid reply
     * or the timeout expired.
     */
    do {
      /* let's tag the ECHO packet with our pid so we can identify it */
      timestamp = GetCurrentTime();
      memset(sendbuf, 0, 1500);
      icmp6 = (struct icmp6_hdr *) sendbuf;
      icmp6->icmp6_type = ICMP6_ECHO_REQUEST;
      icmp6->icmp6_code = 0;
      icmp6->icmp6_id = htons(pid);
      icmp6->icmp6_seq = htons(seq);
      icmp6->icmp6_cksum = 0;
      memcpy((icmp6 + 1), &timestamp, sizeof(int));
      if (netif != NULL) {
        memset(auxbuf, 0, 1500);
        pseudo_ip6 = (struct ip6_pseudo_hdr*) auxbuf;
        memcpy(&pseudo_ip6->ip6_src, &netif->sin6_addr, sizeof(struct in6_addr));
        memcpy(&pseudo_ip6->ip6_dst, &him->sin6_addr, sizeof(struct in6_addr));
        pseudo_ip6->ip6_plen= htonl( 64 );
        pseudo_ip6->ip6_nxt = htonl( IPPROTO_ICMPV6 );
        memcpy(auxbuf + sizeof(struct ip6_pseudo_hdr), icmp6, 64);
        /**
         * We shouldn't have to do that as computing the checksum is supposed
         * to be done by the IPv6 stack. Unfortunately windows, here too, is
         * uterly broken, or non compliant, so let's do it.
         * Problem is to compute the checksum I need to know the source address
         * which happens only if I know the interface to be used...
         */
        icmp6->icmp6_cksum = in_cksum((u_short *)pseudo_ip6, sizeof(struct ip6_pseudo_hdr) + 64);
      }

      /**
       * Ping!
       */
      n = sendto(fd, sendbuf, 64, 0, (struct sockaddr*) him, sizeof(struct sockaddr_in6));
      if (n < 0 && (WSAGetLastError() == WSAEINTR || WSAGetLastError() == WSAEADDRNOTAVAIL)) {
        // Happens when using a "tunnel interface" for instance.
        // Or trying to send a packet on a different scope.
        closesocket(fd);
        WSACloseEvent(hEvent);
        return JNI_FALSE;
      }
      if (n < 0 && WSAGetLastError() != WSAEWOULDBLOCK) {
        NET_ThrowNew(env, WSAGetLastError(), "Can't send ICMP packet");
        closesocket(fd);
        WSACloseEvent(hEvent);
        return JNI_FALSE;
      }

      tmout2 = timeout > 1000 ? 1000 : timeout;
      do {
        tmout2 = NET_Wait(env, fd, NET_WAIT_READ, tmout2);

        if (tmout2 >= 0) {
          len = sizeof(sa_recv);
          memset(recvbuf, 0, 1500);
          /**
           * For some unknown reason, besides plain stupidity, windows
           * truncates the first 4 bytes of the icmpv6 header some we can't
           * check for the ICMP_ECHOREPLY value.
           * we'll check the other values, though
           */
          n = recvfrom(fd, recvbuf + 4, sizeof(recvbuf) - 4, 0, (struct sockaddr*) &sa_recv, &len);
          icmp6 = (struct icmp6_hdr *) (recvbuf);
          memcpy(&i, (icmp6 + 1), sizeof(int));
          /**
           * Is that the reply we were expecting?
           */
          if (n >= 8 && ntohs(icmp6->icmp6_seq) == seq &&
              ntohs(icmp6->icmp6_id) == pid && i == timestamp) {
            closesocket(fd);
            WSACloseEvent(hEvent);
            return JNI_TRUE;
          }
        }
      } while (tmout2 > 0);
      timeout -= 1000;
      seq++;
    } while (timeout > 0);
    closesocket(fd);
    WSACloseEvent(hEvent);
    return JNI_FALSE;
}
#endif /* AF_INET6 */

/*
 * Class:     java_net_Inet6AddressImpl
 * Method:    isReachable0
 * Signature: ([bII[bI)Z
 */
JNIEXPORT jboolean JNICALL
Java_java_net_Inet6AddressImpl_isReachable0(JNIEnv *env, jobject this,
                                           jbyteArray addrArray,
                                           jint scope,
                                           jint timeout,
                                           jbyteArray ifArray,
                                           jint ttl, jint if_scope) {
#ifdef AF_INET6
    jbyte caddr[16];
    jint fd, sz;
    struct sockaddr_in6 him6;
    struct sockaddr_in6* netif = NULL;
    struct sockaddr_in6 inf6;
    WSAEVENT hEvent;
    int len = 0;
    int connect_rv = -1;

    /*
     * If IPv6 is not enable, then we can't reach an IPv6 address, can we?
     * Actually, we probably shouldn't even get here.
     */
    if (!ipv6_available()) {
      return JNI_FALSE;
    }
    /*
     * If it's an IPv4 address, ICMP won't work with IPv4 mapped address,
     * therefore, let's delegate to the Inet4Address method.
     */
    sz = (*env)->GetArrayLength(env, addrArray);
    if (sz == 4) {
      return Java_java_net_Inet4AddressImpl_isReachable0(env, this,
                                                         addrArray,
                                                         timeout,
                                                         ifArray, ttl);
    }

    memset((char *) caddr, 0, 16);
    memset((char *) &him6, 0, sizeof(him6));
    (*env)->GetByteArrayRegion(env, addrArray, 0, 16, caddr);
    memcpy((void *)&(him6.sin6_addr), caddr, sizeof(struct in6_addr) );
    him6.sin6_family = AF_INET6;
    if (scope > 0) {
      him6.sin6_scope_id = scope;
    }
    len = sizeof(struct sockaddr_in6);
    /**
     * A network interface was specified, let's convert the address
     */
    if (!(IS_NULL(ifArray))) {
      memset((char *) caddr, 0, 16);
      memset((char *) &inf6, 0, sizeof(inf6));
      (*env)->GetByteArrayRegion(env, ifArray, 0, 16, caddr);
      memcpy((void *)&(inf6.sin6_addr), caddr, sizeof(struct in6_addr) );
      inf6.sin6_family = AF_INET6;
      inf6.sin6_port = 0;
      inf6.sin6_scope_id = if_scope;
      netif = &inf6;
    }

#if 0
    /*
     * Windows implementation of ICMP & RAW sockets is too unreliable for now.
     * Therefore it's best not to try it at all and rely only on TCP
     * We may revisit and enable this code in the future.
     */

    /*
     * Right now, windows doesn't generate the ICMP checksum automatically
     * so we have to compute it, but we can do it only if we know which
     * interface will be used. Therefore, don't try to use ICMP if no
     * interface was specified.
     * When ICMPv6 support improves in windows, we may change this.
     */
    if (!(IS_NULL(ifArray))) {
      /*
       * If we can create a RAW socket, then when can use the ICMP ECHO_REQUEST
       * otherwise we'll try a tcp socket to the Echo port (7).
       * Note that this is empiric, and not connecting could mean it's blocked
       * or the echo servioe has been disabled.
       */
      fd = NET_Socket(AF_INET6, SOCK_RAW, IPPROTO_ICMPV6);

      if (fd != -1) { /* Good to go, let's do a ping */
        return ping6(env, fd, &him6, timeout, netif, ttl);
      }
    }
#endif

    /* No good, let's fall back on TCP */
    fd = NET_Socket(AF_INET6, SOCK_STREAM, 0);
    if (fd == JVM_IO_ERR) {
        /* note: if you run out of fds, you may not be able to load
         * the exception class, and get a NoClassDefFoundError
         * instead.
         */
        NET_ThrowNew(env, errno, "Can't create socket");
        return JNI_FALSE;
    }

    /**
     * A TTL was specified, let's set the socket option.
     */
    if (ttl > 0) {
      setsockopt(fd, IPPROTO_IPV6, IPV6_UNICAST_HOPS, (const char *)&ttl, sizeof(ttl));
    }

    /**
     * A network interface was specified, let's bind to it.
     */
    if (netif != NULL) {
      if (NET_Bind(fd, (struct sockaddr*)netif, sizeof(struct sockaddr_in6)) < 0) {
        NET_ThrowNew(env, WSAGetLastError(), "Can't bind socket to interface");
        closesocket(fd);
        return JNI_FALSE;
      }
    }

    /**
     * Make the socket non blocking.
     */
    hEvent = WSACreateEvent();
    WSAEventSelect(fd, hEvent, FD_READ|FD_CONNECT|FD_CLOSE);

    /* no need to use NET_Connect as non-blocking */
    him6.sin6_port = htons((short) 7); /* Echo port */
    connect_rv = connect(fd, (struct sockaddr *)&him6, len);

    /**
     * connection established or refused immediately, either way it means
     * we were able to reach the host!
     */
    if (connect_rv == 0 || WSAGetLastError() == WSAECONNREFUSED) {
        WSACloseEvent(hEvent);
        closesocket(fd);
        return JNI_TRUE;
    } else {
        int optlen;

        switch (WSAGetLastError()) {
        case WSAEHOSTUNREACH:   /* Host Unreachable */
        case WSAENETUNREACH:    /* Network Unreachable */
        case WSAENETDOWN:       /* Network is down */
        case WSAEPFNOSUPPORT:   /* Protocol Family unsupported */
          WSACloseEvent(hEvent);
          closesocket(fd);
          return JNI_FALSE;
        }

        if (WSAGetLastError() != WSAEWOULDBLOCK) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ConnectException",
                                         "connect failed");
            WSACloseEvent(hEvent);
            closesocket(fd);
            return JNI_FALSE;
        }

        timeout = NET_Wait(env, fd, NET_WAIT_CONNECT, timeout);

        if (timeout >= 0) {
          /* has connection been established? */
          optlen = sizeof(connect_rv);
          if (getsockopt(fd, SOL_SOCKET, SO_ERROR, (void*)&connect_rv,
                         &optlen) <0) {
            connect_rv = WSAGetLastError();
          }

          if (connect_rv == 0 || connect_rv == WSAECONNREFUSED) {
            WSACloseEvent(hEvent);
            closesocket(fd);
            return JNI_TRUE;
          }
        }
    }
    WSACloseEvent(hEvent);
    closesocket(fd);
#endif /* AF_INET6 */
    return JNI_FALSE;
}
