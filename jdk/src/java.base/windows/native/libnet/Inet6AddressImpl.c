/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include <iphlpapi.h>
#include <icmpapi.h>

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

JNIEXPORT jobjectArray JNICALL
Java_java_net_Inet6AddressImpl_lookupAllHostAddr(JNIEnv *env, jobject this,
                                                jstring host) {
    const char *hostname;
    jobjectArray ret = 0;
    int retLen = 0;
    jboolean preferIPv6Address;

    int error=0;
    struct addrinfo hints, *res, *resNew = NULL;

    initInetAddressIDs(env);
    JNU_CHECK_EXCEPTION_RETURN(env, NULL);

    if (IS_NULL(host)) {
        JNU_ThrowNullPointerException(env, "host is null");
        return 0;
    }
    hostname = JNU_GetStringPlatformChars(env, host, JNI_FALSE);
    CHECK_NULL_RETURN(hostname, NULL);

    /* get the address preference */
    preferIPv6Address
        = (*env)->GetStaticBooleanField(env, ia_class, ia_preferIPv6AddressID);

    /* Try once, with our static buffer. */
    memset(&hints, 0, sizeof(hints));
    hints.ai_flags = AI_CANONNAME;
    hints.ai_family = AF_UNSPEC;

    error = getaddrinfo(hostname, NULL, &hints, &res);

    if (error) {
        if (WSAGetLastError() == WSATRY_AGAIN) {
            NET_ThrowByNameWithLastError(env,
                                         JNU_JAVANETPKG "UnknownHostException",
                                         hostname);
            JNU_ReleaseStringPlatformChars(env, host, hostname);
            return NULL;
        } else {
            /* report error */
            JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException",
                            (char *)hostname);
            JNU_ReleaseStringPlatformChars(env, host, hostname);
            return NULL;
        }
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
        ret = (*env)->NewObjectArray(env, retLen, ia_class, NULL);

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
              jobject iaObj = (*env)->NewObject(env, ia4_class, ia4_ctrID);
              if (IS_NULL(iaObj)) {
                ret = NULL;
                goto cleanupAndReturn;
              }
              setInetAddress_addr(env, iaObj, ntohl(((struct sockaddr_in*)iterator->ai_addr)->sin_addr.s_addr));
              setInetAddress_hostName(env, iaObj, host);
              (*env)->SetObjectArrayElement(env, ret, inetIndex, iaObj);
                inetIndex ++;
            } else if (iterator->ai_family == AF_INET6) {
              jint scope = 0;
              jboolean ret1;
              jobject iaObj = (*env)->NewObject(env, ia6_class, ia6_ctrID);
              if (IS_NULL(iaObj)) {
                ret = NULL;
                goto cleanupAndReturn;
              }
              ret1 = setInet6Address_ipaddress(env, iaObj, (jbyte *)&(((struct sockaddr_in6*)iterator->ai_addr)->sin6_addr));
              if (ret1 == JNI_FALSE) {
                ret = NULL;
                goto cleanupAndReturn;
              }
              scope = ((struct sockaddr_in6*)iterator->ai_addr)->sin6_scope_id;
              if (scope != 0) { /* zero is default value, no need to set */
                setInet6Address_scopeid(env, iaObj, scope);
              }
              setInetAddress_hostName(env, iaObj, host);
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
        CHECK_NULL_RETURN(ret, NULL);
    }

    if (ret == NULL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", NULL);
    }

    return ret;
}

#ifdef AF_INET6

/**
 * ping implementation using tcp port 7 (echo)
 */
static jboolean
tcp_ping6(JNIEnv *env,
          jint timeout,
          jint ttl,
          struct sockaddr_in6 him6,
          struct sockaddr_in6* netif,
          int len)
{
    jint fd;
    WSAEVENT hEvent;
    int connect_rv = -1;

    fd = NET_Socket(AF_INET6, SOCK_STREAM, 0);
    if (fd == SOCKET_ERROR) {
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
    return JNI_FALSE;
}

/**
 * ping implementation.
 * Send a ICMP_ECHO_REQUEST packet every second until either the timeout
 * expires or a answer is received.
 * Returns true is an ECHO_REPLY is received, otherwise, false.
 */
static jboolean
ping6(JNIEnv *env,
      struct sockaddr_in6* src,
      struct sockaddr_in6* dest,
      jint timeout,
      HANDLE hIcmpFile)
{
    DWORD dwRetVal = 0;
    char SendData[32] = {0};
    LPVOID ReplyBuffer = NULL;
    DWORD ReplySize = 0;
    IP_OPTION_INFORMATION ipInfo = {255, 0, 0, 0, NULL};
    struct sockaddr_in6 sa6Source;

    ReplySize = sizeof(ICMPV6_ECHO_REPLY) + sizeof(SendData);
    ReplyBuffer = (VOID*) malloc(ReplySize);
    if (ReplyBuffer == NULL) {
        IcmpCloseHandle(hIcmpFile);
        NET_ThrowNew(env, WSAGetLastError(), "Unable to allocate memory");
        return JNI_FALSE;
    }

    //define local source information
    sa6Source.sin6_addr = in6addr_any;
    sa6Source.sin6_family = AF_INET6;
    sa6Source.sin6_flowinfo = 0;
    sa6Source.sin6_port = 0;

    dwRetVal = Icmp6SendEcho2(hIcmpFile,    // HANDLE IcmpHandle,
                              NULL,         // HANDLE Event,
                              NULL,         // PIO_APC_ROUTINE ApcRoutine,
                              NULL,         // PVOID ApcContext,
                              &sa6Source,   // struct sockaddr_in6 *SourceAddress,
                              dest,         // struct sockaddr_in6 *DestinationAddress,
                              SendData,     // LPVOID RequestData,
                              sizeof(SendData), // WORD RequestSize,
                              &ipInfo,      // PIP_OPTION_INFORMATION RequestOptions,
                              ReplyBuffer,  // LPVOID ReplyBuffer,
                              ReplySize,    // DWORD ReplySize,
                              timeout);     // DWORD Timeout

    free(ReplyBuffer);
    IcmpCloseHandle(hIcmpFile);


    if (dwRetVal != 0) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
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
    jint sz;
    struct sockaddr_in6 him6;
    struct sockaddr_in6* netif = NULL;
    struct sockaddr_in6 inf6;
    int len = 0;
    HANDLE hIcmpFile;

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

    hIcmpFile = Icmp6CreateFile();
    if (hIcmpFile == INVALID_HANDLE_VALUE) {
        int err = WSAGetLastError();
        if (err == ERROR_ACCESS_DENIED) {
            // fall back to TCP echo if access is denied to ICMP
            return tcp_ping6(env, timeout, ttl, him6, netif, len);
        } else {
            NET_ThrowNew(env, err, "Unable to create ICMP file handle");
            return JNI_FALSE;
        }
    } else {
        return ping6(env, netif, &him6, timeout, hIcmpFile);
    }

#endif /* AF_INET6 */
    return JNI_FALSE;
}
