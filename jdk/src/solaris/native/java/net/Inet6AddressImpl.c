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

#include <errno.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <string.h>
#include <strings.h>
#include <stdlib.h>
#include <ctype.h>
#ifdef MACOSX
#include <ifaddrs.h>
#include <net/if.h>
#include <unistd.h> /* gethostname */
#endif

#include "jvm.h"
#include "jni_util.h"
#include "net_util.h"
#ifndef IPV6_DEFS_H
#include <netinet/icmp6.h>
#endif

#include "java_net_Inet4AddressImpl.h"
#include "java_net_Inet6AddressImpl.h"

/* the initial size of our hostent buffers */
#ifndef NI_MAXHOST
#define NI_MAXHOST 1025
#endif


/************************************************************************
 * Inet6AddressImpl
 */

/*
 * Class:     java_net_Inet6AddressImpl
 * Method:    getLocalHostName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet6AddressImpl_getLocalHostName(JNIEnv *env, jobject this) {
    int ret;
    char hostname[NI_MAXHOST+1];

    hostname[0] = '\0';
    ret = gethostname(hostname, NI_MAXHOST);
    if (ret == -1) {
        /* Something went wrong, maybe networking is not setup? */
        strcpy(hostname, "localhost");
    } else {
        // ensure null-terminated
        hostname[NI_MAXHOST] = '\0';
    }

#if defined(__solaris__) && defined(AF_INET6)
    if (ret == 0) {
        /* Solaris doesn't want to give us a fully qualified domain name.
         * We do a reverse lookup to try and get one.  This works
         * if DNS occurs before NIS in /etc/resolv.conf, but fails
         * if NIS comes first (it still gets only a partial name).
         * We use thread-safe system calls.
         */
        struct addrinfo  hints, *res;
        int error;

        memset(&hints, 0, sizeof(hints));
        hints.ai_flags = AI_CANONNAME;
        hints.ai_family = AF_UNSPEC;

        error = getaddrinfo(hostname, NULL, &hints, &res);

        if (error == 0) {
            /* host is known to name service */
            error = getnameinfo(res->ai_addr,
                                res->ai_addrlen,
                                hostname,
                                NI_MAXHOST,
                                NULL,
                                0,
                                NI_NAMEREQD);

            /* if getnameinfo fails hostname is still the value
               from gethostname */

            freeaddrinfo(res);
        }
    }
#endif

    return (*env)->NewStringUTF(env, hostname);
}

#ifdef MACOSX
/* also called from Inet4AddressImpl.c */
__private_extern__ jobjectArray
lookupIfLocalhost(JNIEnv *env, const char *hostname, jboolean includeV6)
{
    jobjectArray result = NULL;
    char myhostname[NI_MAXHOST+1];
    struct ifaddrs *ifa = NULL;
    int familyOrder = 0;
    int count = 0, i, j;
    int addrs4 = 0, addrs6 = 0, numV4Loopbacks = 0, numV6Loopbacks = 0;
    jboolean includeLoopback = JNI_FALSE;
    jobject name;

    initInetAddressIDs(env);
    JNU_CHECK_EXCEPTION_RETURN(env, NULL);

    /* If the requested name matches this host's hostname, return IP addresses
     * from all attached interfaces. (#2844683 et al) This prevents undesired
     * PPP dialup, but may return addresses that don't actually correspond to
     * the name (if the name actually matches something in DNS etc.
     */
    myhostname[0] = '\0';
    if (gethostname(myhostname, NI_MAXHOST) == -1) {
        /* Something went wrong, maybe networking is not setup? */
        return NULL;
    }
    myhostname[NI_MAXHOST] = '\0';

    if (strcmp(myhostname, hostname) != 0) {
        // Non-self lookup
        return NULL;
    }

    if (getifaddrs(&ifa) != 0) {
        NET_ThrowNew(env, errno, "Can't get local interface addresses");
        return NULL;
    }

    name = (*env)->NewStringUTF(env, hostname);
    CHECK_NULL_RETURN(name, NULL);

    /* Iterate over the interfaces, and total up the number of IPv4 and IPv6
     * addresses we have. Also keep a count of loopback addresses. We need to
     * exclude them in the normal case, but return them if we don't get an IP
     * address.
     */
    struct ifaddrs *iter = ifa;
    while (iter) {
        int family = iter->ifa_addr->sa_family;
        if (iter->ifa_name[0] != '\0'  &&  iter->ifa_addr)
        {
            jboolean isLoopback = iter->ifa_flags & IFF_LOOPBACK;
            if (family == AF_INET) {
                addrs4++;
                if (isLoopback) numV4Loopbacks++;
            } else if (family == AF_INET6 && includeV6) {
                addrs6++;
                if (isLoopback) numV6Loopbacks++;
            } else {
                /* We don't care e.g. AF_LINK */
            }
        }
        iter = iter->ifa_next;
    }

    if (addrs4 == numV4Loopbacks && addrs6 == numV6Loopbacks) {
        // We don't have a real IP address, just loopback. We need to include
        // loopback in our results.
        includeLoopback = JNI_TRUE;
    }

    /* Create and fill the Java array. */
    int arraySize = addrs4 + addrs6 -
        (includeLoopback ? 0 : (numV4Loopbacks + numV6Loopbacks));
    result = (*env)->NewObjectArray(env, arraySize, ia_class, NULL);
    if (!result) goto done;

    if ((*env)->GetStaticBooleanField(env, ia_class, ia_preferIPv6AddressID)) {
        i = includeLoopback ? addrs6 : (addrs6 - numV6Loopbacks);
        j = 0;
    } else {
        i = 0;
        j = includeLoopback ? addrs4 : (addrs4 - numV4Loopbacks);
    }

    // Now loop around the ifaddrs
    iter = ifa;
    while (iter != NULL) {
        jboolean isLoopback = iter->ifa_flags & IFF_LOOPBACK;
        int family = iter->ifa_addr->sa_family;

        if (iter->ifa_name[0] != '\0'  &&  iter->ifa_addr
            && (family == AF_INET || (family == AF_INET6 && includeV6))
            && (!isLoopback || includeLoopback))
        {
            int port;
            int index = (family == AF_INET) ? i++ : j++;
            jobject o = NET_SockaddrToInetAddress(env, iter->ifa_addr, &port);
            if (!o) {
                freeifaddrs(ifa);
                if (!(*env)->ExceptionCheck(env))
                    JNU_ThrowOutOfMemoryError(env, "Object allocation failed");
                return NULL;
            }
            setInetAddress_hostName(env, o, name);
            (*env)->SetObjectArrayElement(env, result, index, o);
            (*env)->DeleteLocalRef(env, o);
        }
        iter = iter->ifa_next;
    }

  done:
    freeifaddrs(ifa);

    return result;
}
#endif

/*
 * Find an internet address for a given hostname.  Note that this
 * code only works for addresses of type INET. The translation
 * of %d.%d.%d.%d to an address (int) occurs in java now, so the
 * String "host" shouldn't *ever* be a %d.%d.%d.%d string
 *
 * Class:     java_net_Inet6AddressImpl
 * Method:    lookupAllHostAddr
 * Signature: (Ljava/lang/String;)[[B
 */

JNIEXPORT jobjectArray JNICALL
Java_java_net_Inet6AddressImpl_lookupAllHostAddr(JNIEnv *env, jobject this,
                                                jstring host) {
    const char *hostname;
    jobjectArray ret = 0;
    int retLen = 0;

    int error=0;
#ifdef AF_INET6
    struct addrinfo hints, *res, *resNew = NULL;
#endif /* AF_INET6 */

    initInetAddressIDs(env);
    JNU_CHECK_EXCEPTION_RETURN(env, NULL);

    if (IS_NULL(host)) {
        JNU_ThrowNullPointerException(env, "host is null");
        return 0;
    }
    hostname = JNU_GetStringPlatformChars(env, host, JNI_FALSE);
    CHECK_NULL_RETURN(hostname, NULL);

#ifdef MACOSX
    /*
     * If we're looking up the local machine, attempt to get the address
     * from getifaddrs. This ensures we get an IPv6 address for the local
     * machine.
     */
    ret = lookupIfLocalhost(env, hostname, JNI_TRUE);
    if (ret != NULL || (*env)->ExceptionCheck(env)) {
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return ret;
    }
#endif

#ifdef AF_INET6
    /* Try once, with our static buffer. */
    memset(&hints, 0, sizeof(hints));
    hints.ai_flags = AI_CANONNAME;
    hints.ai_family = AF_UNSPEC;

#ifdef __solaris__
    /*
     * Workaround for Solaris bug 4160367 - if a hostname contains a
     * white space then 0.0.0.0 is returned
     */
    if (isspace((unsigned char)hostname[0])) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException",
                        hostname);
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return NULL;
    }
#endif

    error = getaddrinfo(hostname, NULL, &hints, &res);

    if (error) {
        /* report error */
        NET_ThrowUnknownHostExceptionWithGaiError(env, hostname, error);
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return NULL;
    } else {
        int i = 0;
        int inetCount = 0, inet6Count = 0, inetIndex, inet6Index;
        struct addrinfo *itr, *last = NULL, *iterator = res;
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

        ret = (*env)->NewObjectArray(env, retLen, ia_class, NULL);

        if (IS_NULL(ret)) {
            /* we may have memory to free at the end of this */
            goto cleanupAndReturn;
        }

        if ((*env)->GetStaticBooleanField(env, ia_class, ia_preferIPv6AddressID)) {
            /* AF_INET addresses will be offset by inet6Count */
            inetIndex = inet6Count;
            inet6Index = 0;
        } else {
            /* AF_INET6 addresses will be offset by inetCount */
            inetIndex = 0;
            inet6Index = inetCount;
        }

        while (iterator != NULL) {
            jboolean ret1;
            if (iterator->ai_family == AF_INET) {
                jobject iaObj = (*env)->NewObject(env, ia4_class, ia4_ctrID);
                if (IS_NULL(iaObj)) {
                    ret = NULL;
                    goto cleanupAndReturn;
                }
                setInetAddress_addr(env, iaObj, ntohl(((struct sockaddr_in*)iterator->ai_addr)->sin_addr.s_addr));
                setInetAddress_hostName(env, iaObj, host);
                (*env)->SetObjectArrayElement(env, ret, inetIndex, iaObj);
                inetIndex++;
            } else if (iterator->ai_family == AF_INET6) {
                jint scope = 0;

                jobject iaObj = (*env)->NewObject(env, ia6_class, ia6_ctrID);
                if (IS_NULL(iaObj)) {
                    ret = NULL;
                    goto cleanupAndReturn;
                }
                ret1 = setInet6Address_ipaddress(env, iaObj, (char *)&(((struct sockaddr_in6*)iterator->ai_addr)->sin6_addr));
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
                inet6Index++;
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
#endif /* AF_INET6 */

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

#ifdef AF_INET6
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
        memset((void *) &him4, 0, sizeof(him4));
        him4.sin_addr.s_addr = (uint32_t) htonl(addr);
        him4.sin_family = AF_INET;
        sa = (struct sockaddr *) &him4;
        len = sizeof(him4);
    } else {
        /*
         * For IPv6 address construct a sockaddr_in6 structure.
         */
        (*env)->GetByteArrayRegion(env, addrArray, 0, 16, caddr);
        memset((void *) &him6, 0, sizeof(him6));
        memcpy((void *)&(him6.sin6_addr), caddr, sizeof(struct in6_addr) );
        him6.sin6_family = AF_INET6;
        sa = (struct sockaddr *) &him6 ;
        len = sizeof(him6) ;
    }

    error = getnameinfo(sa, len, host, NI_MAXHOST, NULL, 0,
                        NI_NAMEREQD);

    if (!error) {
        ret = (*env)->NewStringUTF(env, host);
        CHECK_NULL_RETURN(ret, NULL);
    }
#endif /* AF_INET6 */

    if (ret == NULL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", NULL);
    }

    return ret;
}

#define SET_NONBLOCKING(fd) {           \
        int flags = fcntl(fd, F_GETFL); \
        flags |= O_NONBLOCK;            \
        fcntl(fd, F_SETFL, flags);      \
}

#ifdef AF_INET6
static jboolean
ping6(JNIEnv *env, jint fd, struct sockaddr_in6* him, jint timeout,
      struct sockaddr_in6* netif, jint ttl) {
    jint size;
    jint n;
    socklen_t len;
    char sendbuf[1500];
    unsigned char recvbuf[1500];
    struct icmp6_hdr *icmp6;
    struct sockaddr_in6 sa_recv;
    jbyte *caddr, *recv_caddr;
    jchar pid;
    jint tmout2, seq = 1;
    struct timeval tv;
    size_t plen;

#ifdef __linux__
    {
    int csum_offset;
    /**
     * For some strange reason, the linux kernel won't calculate the
     * checksum of ICMPv6 packets unless you set this socket option
     */
    csum_offset = 2;
    setsockopt(fd, SOL_RAW, IPV6_CHECKSUM, &csum_offset, sizeof(int));
    }
#endif

    caddr = (jbyte *)&(him->sin6_addr);

    /* icmp_id is a 16 bit data type, therefore down cast the pid */
    pid = (jchar)getpid();
    size = 60*1024;
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));
    if (ttl > 0) {
      setsockopt(fd, IPPROTO_IPV6, IPV6_UNICAST_HOPS, &ttl, sizeof(ttl));
    }
    if (netif != NULL) {
      if (bind(fd, (struct sockaddr*)netif, sizeof(struct sockaddr_in6)) <0) {
        NET_ThrowNew(env, errno, "Can't bind socket");
        close(fd);
        return JNI_FALSE;
      }
    }
    SET_NONBLOCKING(fd);

    do {
      icmp6 = (struct icmp6_hdr *) sendbuf;
      icmp6->icmp6_type = ICMP6_ECHO_REQUEST;
      icmp6->icmp6_code = 0;
      /* let's tag the ECHO packet with our pid so we can identify it */
      icmp6->icmp6_id = htons(pid);
      icmp6->icmp6_seq = htons(seq);
      seq++;
      icmp6->icmp6_cksum = 0;
      gettimeofday(&tv, NULL);
      memcpy(sendbuf + sizeof(struct icmp6_hdr), &tv, sizeof(tv));
      plen = sizeof(struct icmp6_hdr) + sizeof(tv);
      n = sendto(fd, sendbuf, plen, 0, (struct sockaddr*) him, sizeof(struct sockaddr_in6));
      if (n < 0 && errno != EINPROGRESS) {
#ifdef __linux__
        if (errno != EINVAL && errno != EHOSTUNREACH)
          /*
           * On some Linux versions, when a socket is  bound to the
           * loopback interface, sendto will fail and errno will be
           * set to EINVAL or EHOSTUNREACH.
           * When that happens, don't throw an exception, just return false.
           */
#endif /*__linux__ */
        NET_ThrowNew(env, errno, "Can't send ICMP packet");
        close(fd);
        return JNI_FALSE;
      }

      tmout2 = timeout > 1000 ? 1000 : timeout;
      do {
        tmout2 = NET_Wait(env, fd, NET_WAIT_READ, tmout2);

        if (tmout2 >= 0) {
          len = sizeof(sa_recv);
          n = recvfrom(fd, recvbuf, sizeof(recvbuf), 0, (struct sockaddr*) &sa_recv, &len);
          icmp6 = (struct icmp6_hdr *) (recvbuf);
          recv_caddr = (jbyte *)&(sa_recv.sin6_addr);
          /*
           * We did receive something, but is it what we were expecting?
           * I.E.: An ICMP6_ECHO_REPLY packet with the proper PID and
           *       from the host that we are trying to determine is reachable.
           */
          if (n >= 8 && icmp6->icmp6_type == ICMP6_ECHO_REPLY &&
              (ntohs(icmp6->icmp6_id) == pid)) {
            if (NET_IsEqual(caddr, recv_caddr)) {
              close(fd);
              return JNI_TRUE;
            }
            if (NET_IsZeroAddr(caddr)) {
              close(fd);
              return JNI_TRUE;
            }
          }
        }
      } while (tmout2 > 0);
      timeout -= 1000;
    } while (timeout > 0);
    close(fd);
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
    struct sockaddr_in6 inf6;
    struct sockaddr_in6* netif = NULL;
    int len = 0;
    int connect_rv = -1;

    /*
     * If IPv6 is not enable, then we can't reach an IPv6 address, can we?
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

    memset((void *) caddr, 0, 16);
    memset((void *) &him6, 0, sizeof(him6));
    (*env)->GetByteArrayRegion(env, addrArray, 0, 16, caddr);
    memcpy((void *)&(him6.sin6_addr), caddr, sizeof(struct in6_addr) );
    him6.sin6_family = AF_INET6;
#ifdef __linux__
    if (scope > 0)
      him6.sin6_scope_id = scope;
    else
      him6.sin6_scope_id = getDefaultIPv6Interface( &(him6.sin6_addr));
    len = sizeof(struct sockaddr_in6);
#else
    if (scope > 0)
      him6.sin6_scope_id = scope;
    len = sizeof(struct sockaddr_in6);
#endif
    /*
     * If a network interface was specified, let's create the address
     * for it.
     */
    if (!(IS_NULL(ifArray))) {
      memset((void *) caddr, 0, 16);
      memset((void *) &inf6, 0, sizeof(inf6));
      (*env)->GetByteArrayRegion(env, ifArray, 0, 16, caddr);
      memcpy((void *)&(inf6.sin6_addr), caddr, sizeof(struct in6_addr) );
      inf6.sin6_family = AF_INET6;
      inf6.sin6_scope_id = if_scope;
      netif = &inf6;
    }
    /*
     * If we can create a RAW socket, then when can use the ICMP ECHO_REQUEST
     * otherwise we'll try a tcp socket to the Echo port (7).
     * Note that this is empiric, and not connecting could mean it's blocked
     * or the echo service has been disabled.
     */

    fd = socket(AF_INET6, SOCK_RAW, IPPROTO_ICMPV6);

    if (fd != -1) { /* Good to go, let's do a ping */
        return ping6(env, fd, &him6, timeout, netif, ttl);
    }

    /* No good, let's fall back on TCP */
    fd = socket(AF_INET6, SOCK_STREAM, 0);
    if (fd == -1) {
        /* note: if you run out of fds, you may not be able to load
         * the exception class, and get a NoClassDefFoundError
         * instead.
         */
        NET_ThrowNew(env, errno, "Can't create socket");
        return JNI_FALSE;
    }
    if (ttl > 0) {
      setsockopt(fd, IPPROTO_IPV6, IPV6_UNICAST_HOPS, &ttl, sizeof(ttl));
    }

    /*
     * A network interface was specified, so let's bind to it.
     */
    if (netif != NULL) {
      if (bind(fd, (struct sockaddr*)netif, sizeof(struct sockaddr_in6)) <0) {
        NET_ThrowNew(env, errno, "Can't bind socket");
        close(fd);
        return JNI_FALSE;
      }
    }
    SET_NONBLOCKING(fd);

    him6.sin6_port = htons((short) 7); /* Echo port */
    connect_rv = NET_Connect(fd, (struct sockaddr *)&him6, len);

    /**
     * connection established or refused immediately, either way it means
     * we were able to reach the host!
     */
    if (connect_rv == 0 || errno == ECONNREFUSED) {
        close(fd);
        return JNI_TRUE;
    } else {
        socklen_t optlen = (socklen_t)sizeof(connect_rv);

        switch (errno) {
        case ENETUNREACH: /* Network Unreachable */
        case EAFNOSUPPORT: /* Address Family not supported */
        case EADDRNOTAVAIL: /* address is not available on  the  remote machine */
#ifdef __linux__
        case EINVAL:
        case EHOSTUNREACH:
          /*
           * On some Linux versions, when  a socket is bound to the
           * loopback interface, connect will fail and errno will
           * be set to EINVAL or EHOSTUNREACH.  When that happens,
           * don't throw an exception, just return false.
           */
#endif /* __linux__ */
          close(fd);
          return JNI_FALSE;
        }

        if (errno != EINPROGRESS) {
            NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "ConnectException",
                                         "connect failed");
            close(fd);
            return JNI_FALSE;
        }

        timeout = NET_Wait(env, fd, NET_WAIT_CONNECT, timeout);

        if (timeout >= 0) {
          /* has connection been established */
          if (getsockopt(fd, SOL_SOCKET, SO_ERROR, (void*)&connect_rv,
                         &optlen) <0) {
            connect_rv = errno;
          }
          if (connect_rv == 0 || ECONNREFUSED) {
            close(fd);
            return JNI_TRUE;
          }
        }
        close(fd);
        return JNI_FALSE;
    }
#else /* AF_INET6 */
    return JNI_FALSE;
#endif /* AF_INET6 */
}
