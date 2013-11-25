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
#include <netinet/in_systm.h>
#include <netinet/in.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>
#include <netdb.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>

#ifdef _ALLBSD_SOURCE
#include <unistd.h>
#include <sys/param.h>
#endif

#include "jvm.h"
#include "jni_util.h"
#include "net_util.h"

#include "java_net_Inet4AddressImpl.h"

#if defined(__GLIBC__) || (defined(__FreeBSD__) && (__FreeBSD_version >= 601104))
#define HAS_GLIBC_GETHOSTBY_R   1
#endif

static jclass ni_iacls;
static jclass ni_ia4cls;
static jmethodID ni_ia4ctrID;

static jboolean initializeInetClasses(JNIEnv *env)
{
    static int initialized = 0;
    if (!initialized) {
        ni_iacls = (*env)->FindClass(env, "java/net/InetAddress");
        CHECK_NULL_RETURN(ni_iacls, JNI_FALSE);
        ni_iacls = (*env)->NewGlobalRef(env, ni_iacls);
        CHECK_NULL_RETURN(ni_iacls, JNI_FALSE);
        ni_ia4cls = (*env)->FindClass(env, "java/net/Inet4Address");
        CHECK_NULL_RETURN(ni_ia4cls, JNI_FALSE);
        ni_ia4cls = (*env)->NewGlobalRef(env, ni_ia4cls);
        CHECK_NULL_RETURN(ni_ia4cls, JNI_FALSE);
        ni_ia4ctrID = (*env)->GetMethodID(env, ni_ia4cls, "<init>", "()V");
        CHECK_NULL_RETURN(ni_ia4ctrID, JNI_FALSE);
        initialized = 1;
    }
    return JNI_TRUE;
}


#if defined(_ALLBSD_SOURCE) && !defined(HAS_GLIBC_GETHOSTBY_R)
extern jobjectArray lookupIfLocalhost(JNIEnv *env, const char *hostname, jboolean includeV6);

/* Use getaddrinfo(3), which is thread safe */
/************************************************************************
 * Inet4AddressImpl
 */

/*
 * Class:     java_net_Inet4AddressImpl
 * Method:    getLocalHostName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet4AddressImpl_getLocalHostName(JNIEnv *env, jobject this) {
    char hostname[NI_MAXHOST+1];

    hostname[0] = '\0';
    if (JVM_GetHostName(hostname, NI_MAXHOST)) {
        /* Something went wrong, maybe networking is not setup? */
        strcpy(hostname, "localhost");
    } else {
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
    return (*env)->NewStringUTF(env, hostname);
}

/*
 * Find an internet address for a given hostname.  Note that this
 * code only works for addresses of type INET. The translation
 * of %d.%d.%d.%d to an address (int) occurs in java now, so the
 * String "host" shouldn't *ever* be a %d.%d.%d.%d string
 *
 * Class:     java_net_Inet4AddressImpl
 * Method:    lookupAllHostAddr
 * Signature: (Ljava/lang/String;)[[B
 */

JNIEXPORT jobjectArray JNICALL
Java_java_net_Inet4AddressImpl_lookupAllHostAddr(JNIEnv *env, jobject this,
                                                jstring host) {
    const char *hostname;
    jobject name;
    jobjectArray ret = 0;
    int retLen = 0;

    int error=0;
    struct addrinfo hints, *res, *resNew = NULL;

    if (!initializeInetClasses(env))
        return NULL;

    if (IS_NULL(host)) {
        JNU_ThrowNullPointerException(env, "host is null");
        return 0;
    }
    hostname = JNU_GetStringPlatformChars(env, host, JNI_FALSE);
    CHECK_NULL_RETURN(hostname, NULL);

    memset(&hints, 0, sizeof(hints));
    hints.ai_flags = AI_CANONNAME;
    hints.ai_family = AF_INET;

    /*
     * Workaround for Solaris bug 4160367 - if a hostname contains a
     * white space then 0.0.0.0 is returned
     */
    if (isspace((unsigned char)hostname[0])) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException",
                        (char *)hostname);
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return NULL;
    }

#ifdef MACOSX
    /* If we're looking up the local machine, bypass DNS lookups and get
     * address from getifaddrs.
     */
    ret = lookupIfLocalhost(env, hostname, JNI_FALSE);
    if (ret != NULL || (*env)->ExceptionCheck(env)) {
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return ret;
    }
#endif

    error = getaddrinfo(hostname, NULL, &hints, &res);

    if (error) {
        /* report error */
        ThrowUnknownHostExceptionWithGaiError(env, hostname, error);
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return NULL;
    } else {
        int i = 0;
        struct addrinfo *itr, *last = NULL, *iterator = res;
        while (iterator != NULL) {
            int skip = 0;
            itr = resNew;

            while (itr != NULL) {
                struct sockaddr_in *addr1, *addr2;

                addr1 = (struct sockaddr_in *)iterator->ai_addr;
                addr2 = (struct sockaddr_in *)itr->ai_addr;
                if (addr1->sin_addr.s_addr ==
                    addr2->sin_addr.s_addr) {
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
            }
            iterator = iterator->ai_next;
        }

        retLen = i;
        iterator = resNew;
        i = 0;

        name = (*env)->NewStringUTF(env, hostname);
        if (IS_NULL(name)) {
          goto cleanupAndReturn;
        }

        ret = (*env)->NewObjectArray(env, retLen, ni_iacls, NULL);
        if (IS_NULL(ret)) {
            /* we may have memory to free at the end of this */
            goto cleanupAndReturn;
        }

        while (iterator != NULL) {
            /* We need 4 bytes to store ipv4 address; */
            int len = 4;

            jobject iaObj = (*env)->NewObject(env, ni_ia4cls, ni_ia4ctrID);
            if (IS_NULL(iaObj)) {
                /* we may have memory to free at the end of this */
                ret = NULL;
                goto cleanupAndReturn;
            }
            setInetAddress_addr(env, iaObj, ntohl(((struct sockaddr_in*)(iterator->ai_addr))->sin_addr.s_addr));
            setInetAddress_hostName(env, iaObj, name);
            (*env)->SetObjectArrayElement(env, ret, retLen - i -1, iaObj);
            i++;
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
 * Class:     java_net_Inet4AddressImpl
 * Method:    getHostByAddr
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet4AddressImpl_getHostByAddr(JNIEnv *env, jobject this,
                                            jbyteArray addrArray) {
    jstring ret = NULL;

    char host[NI_MAXHOST+1];
    jfieldID fid;
    int error = 0;
    jint family;
    struct sockaddr *him ;
    int len = 0;
    jbyte caddr[4];
    jint addr;

    struct sockaddr_in him4;
    struct sockaddr *sa;

    /*
         * For IPv4 addresses construct a sockaddr_in structure.
         */
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

    error = getnameinfo(sa, len, host, NI_MAXHOST, NULL, 0,
                               NI_NAMEREQD);

    if (!error) {
        ret = (*env)->NewStringUTF(env, host);
    }

    if (ret == NULL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", NULL);
    }

    return ret;

}

#else /* defined(_ALLBSD_SOURCE) && !defined(HAS_GLIBC_GETHOSTBY_R) */

/* the initial size of our hostent buffers */
#ifndef NI_MAXHOST
#define NI_MAXHOST 1025
#endif

/************************************************************************
 * Inet4AddressImpl
 */

/*
 * Class:     java_net_Inet4AddressImpl
 * Method:    getLocalHostName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet4AddressImpl_getLocalHostName(JNIEnv *env, jobject this) {
    char hostname[NI_MAXHOST+1];

    hostname[0] = '\0';
    if (JVM_GetHostName(hostname, sizeof(hostname))) {
        /* Something went wrong, maybe networking is not setup? */
        strcpy(hostname, "localhost");
    } else {
        struct addrinfo hints, *res;
        int error;

        hostname[NI_MAXHOST] = '\0';
        memset(&hints, 0, sizeof(hints));
        hints.ai_flags = AI_CANONNAME;
        hints.ai_family = AF_INET;

        error = getaddrinfo(hostname, NULL, &hints, &res);

        if (error == 0) {/* host is known to name service */
            getnameinfo(res->ai_addr,
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
    return (*env)->NewStringUTF(env, hostname);
}

/*
 * Find an internet address for a given hostname.  Note that this
 * code only works for addresses of type INET. The translation
 * of %d.%d.%d.%d to an address (int) occurs in java now, so the
 * String "host" shouldn't *ever* be a %d.%d.%d.%d string
 *
 * Class:     java_net_Inet4AddressImpl
 * Method:    lookupAllHostAddr
 * Signature: (Ljava/lang/String;)[[B
 */

JNIEXPORT jobjectArray JNICALL
Java_java_net_Inet4AddressImpl_lookupAllHostAddr(JNIEnv *env, jobject this,
                                                jstring host) {
    const char *hostname;
    jobjectArray ret = 0;
    int retLen = 0;
    int error = 0;
    struct addrinfo hints, *res, *resNew = NULL;

    if (!initializeInetClasses(env))
        return NULL;

    if (IS_NULL(host)) {
        JNU_ThrowNullPointerException(env, "host is null");
        return 0;
    }
    hostname = JNU_GetStringPlatformChars(env, host, JNI_FALSE);
    CHECK_NULL_RETURN(hostname, NULL);

    /* Try once, with our static buffer. */
    memset(&hints, 0, sizeof(hints));
    hints.ai_flags = AI_CANONNAME;
    hints.ai_family = AF_INET;

#ifdef __solaris__
    /*
     * Workaround for Solaris bug 4160367 - if a hostname contains a
     * white space then 0.0.0.0 is returned
     */
    if (isspace((unsigned char)hostname[0])) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException",
                        (char *)hostname);
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return NULL;
    }
#endif

    error = getaddrinfo(hostname, NULL, &hints, &res);

    if (error) {
        /* report error */
        ThrowUnknownHostExceptionWithGaiError(env, hostname, error);
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return NULL;
    } else {
        int i = 0;
        struct addrinfo *itr, *last = NULL, *iterator = res;

        while (iterator != NULL) {
            // remove the duplicate one
            int skip = 0;
            itr = resNew;
            while (itr != NULL) {
                struct sockaddr_in *addr1, *addr2;
                addr1 = (struct sockaddr_in *)iterator->ai_addr;
                addr2 = (struct sockaddr_in *)itr->ai_addr;
                if (addr1->sin_addr.s_addr ==
                    addr2->sin_addr.s_addr) {
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
            }
            iterator = iterator->ai_next;
        }

        retLen = i;
        iterator = resNew;

        ret = (*env)->NewObjectArray(env, retLen, ni_iacls, NULL);

        if (IS_NULL(ret)) {
            /* we may have memory to free at the end of this */
            goto cleanupAndReturn;
        }

        i = 0;
        while (iterator != NULL) {
            jobject iaObj = (*env)->NewObject(env, ni_ia4cls, ni_ia4ctrID);
            if (IS_NULL(iaObj)) {
                ret = NULL;
                goto cleanupAndReturn;
            }
            setInetAddress_addr(env, iaObj, ntohl(((struct sockaddr_in*)iterator->ai_addr)->sin_addr.s_addr));
            setInetAddress_hostName(env, iaObj, host);
            (*env)->SetObjectArrayElement(env, ret, i++, iaObj);
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
 * Class:     java_net_Inet4AddressImpl
 * Method:    getHostByAddr
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet4AddressImpl_getHostByAddr(JNIEnv *env, jobject this,
                                            jbyteArray addrArray) {
    jstring ret = NULL;

    char host[NI_MAXHOST+1];
    int error = 0;
    int len = 0;
    jbyte caddr[4];

    struct sockaddr_in him4;
    struct sockaddr *sa;

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

    error = getnameinfo(sa, len, host, NI_MAXHOST, NULL, 0,
                        NI_NAMEREQD);

    if (!error) {
        ret = (*env)->NewStringUTF(env, host);
    }

    if (ret == NULL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", NULL);
    }

    return ret;
}

#endif /* _ALLBSD_SOURCE */

#define SET_NONBLOCKING(fd) {           \
        int flags = fcntl(fd, F_GETFL); \
        flags |= O_NONBLOCK;            \
        fcntl(fd, F_SETFL, flags);      \
}

/**
 * ping implementation.
 * Send a ICMP_ECHO_REQUEST packet every second until either the timeout
 * expires or a answer is received.
 * Returns true is an ECHO_REPLY is received, otherwise, false.
 */
static jboolean
ping4(JNIEnv *env, jint fd, struct sockaddr_in* him, jint timeout,
      struct sockaddr_in* netif, jint ttl) {
    jint size;
    jint n, hlen1, icmplen;
    socklen_t len;
    char sendbuf[1500];
    char recvbuf[1500];
    struct icmp *icmp;
    struct ip *ip;
    struct sockaddr_in sa_recv;
    jchar pid;
    jint tmout2, seq = 1;
    struct timeval tv;
    size_t plen;

    /* icmp_id is a 16 bit data type, therefore down cast the pid */
    pid = (jchar)getpid();
    size = 60*1024;
    setsockopt(fd, SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));
    /*
     * sets the ttl (max number of hops)
     */
    if (ttl > 0) {
      setsockopt(fd, IPPROTO_IP, IP_TTL, &ttl, sizeof(ttl));
    }
    /*
     * a specific interface was specified, so let's bind the socket
     * to that interface to ensure the requests are sent only through it.
     */
    if (netif != NULL) {
      if (bind(fd, (struct sockaddr*)netif, sizeof(struct sockaddr_in)) < 0) {
        NET_ThrowNew(env, errno, "Can't bind socket");
        close(fd);
        return JNI_FALSE;
      }
    }
    /*
     * Make the socket non blocking so we can use select
     */
    SET_NONBLOCKING(fd);
    do {
      /*
       * create the ICMP request
       */
      icmp = (struct icmp *) sendbuf;
      icmp->icmp_type = ICMP_ECHO;
      icmp->icmp_code = 0;
      icmp->icmp_id = htons(pid);
      icmp->icmp_seq = htons(seq);
      seq++;
      gettimeofday(&tv, NULL);
      memcpy(icmp->icmp_data, &tv, sizeof(tv));
      plen = ICMP_ADVLENMIN + sizeof(tv);
      icmp->icmp_cksum = 0;
      icmp->icmp_cksum = in_cksum((u_short *)icmp, plen);
      /*
       * send it
       */
      n = sendto(fd, sendbuf, plen, 0, (struct sockaddr *)him,
                 sizeof(struct sockaddr));
      if (n < 0 && errno != EINPROGRESS ) {
#ifdef __linux__
        if (errno != EINVAL && errno != EHOSTUNREACH)
          /*
           * On some Linux versions, when a socket is bound to the loopback
           * interface, sendto will fail and errno will be set to
           * EINVAL or EHOSTUNREACH. When that happens, don't throw an
           * exception, just return false.
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
          n = recvfrom(fd, recvbuf, sizeof(recvbuf), 0, (struct sockaddr *)&sa_recv, &len);
          ip = (struct ip*) recvbuf;
          hlen1 = (ip->ip_hl) << 2;
          icmp = (struct icmp *) (recvbuf + hlen1);
          icmplen = n - hlen1;
          /*
           * We did receive something, but is it what we were expecting?
           * I.E.: A ICMP_ECHOREPLY packet with the proper PID.
           */
          if (icmplen >= 8 && icmp->icmp_type == ICMP_ECHOREPLY
               && (ntohs(icmp->icmp_id) == pid)) {
            if ((him->sin_addr.s_addr == sa_recv.sin_addr.s_addr)) {
              close(fd);
              return JNI_TRUE;
            }

            if (him->sin_addr.s_addr == 0) {
              close(fd);
              return JNI_TRUE;
            }
         }

        }
      } while (tmout2 > 0);
      timeout -= 1000;
    } while (timeout >0);
    close(fd);
    return JNI_FALSE;
}

/*
 * Class:     java_net_Inet4AddressImpl
 * Method:    isReachable0
 * Signature: ([bI[bI)Z
 */
JNIEXPORT jboolean JNICALL
Java_java_net_Inet4AddressImpl_isReachable0(JNIEnv *env, jobject this,
                                           jbyteArray addrArray,
                                           jint timeout,
                                           jbyteArray ifArray,
                                           jint ttl) {
    jint addr;
    jbyte caddr[4];
    jint fd;
    struct sockaddr_in him;
    struct sockaddr_in* netif = NULL;
    struct sockaddr_in inf;
    int len = 0;
    int connect_rv = -1;
    int sz;

    memset((char *) caddr, 0, sizeof(caddr));
    memset((char *) &him, 0, sizeof(him));
    memset((char *) &inf, 0, sizeof(inf));
    sz = (*env)->GetArrayLength(env, addrArray);
    if (sz != 4) {
      return JNI_FALSE;
    }
    (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
    addr = ((caddr[0]<<24) & 0xff000000);
    addr |= ((caddr[1] <<16) & 0xff0000);
    addr |= ((caddr[2] <<8) & 0xff00);
    addr |= (caddr[3] & 0xff);
    addr = htonl(addr);
    him.sin_addr.s_addr = addr;
    him.sin_family = AF_INET;
    len = sizeof(him);
    /*
     * If a network interface was specified, let's create the address
     * for it.
     */
    if (!(IS_NULL(ifArray))) {
      memset((char *) caddr, 0, sizeof(caddr));
      (*env)->GetByteArrayRegion(env, ifArray, 0, 4, caddr);
      addr = ((caddr[0]<<24) & 0xff000000);
      addr |= ((caddr[1] <<16) & 0xff0000);
      addr |= ((caddr[2] <<8) & 0xff00);
      addr |= (caddr[3] & 0xff);
      addr = htonl(addr);
      inf.sin_addr.s_addr = addr;
      inf.sin_family = AF_INET;
      inf.sin_port = 0;
      netif = &inf;
    }

    /*
     * Let's try to create a RAW socket to send ICMP packets
     * This usually requires "root" privileges, so it's likely to fail.
     */
    fd = JVM_Socket(AF_INET, SOCK_RAW, IPPROTO_ICMP);
    if (fd != -1) {
      /*
       * It didn't fail, so we can use ICMP_ECHO requests.
       */
      return ping4(env, fd, &him, timeout, netif, ttl);
    }

    /*
     * Can't create a raw socket, so let's try a TCP socket
     */
    fd = JVM_Socket(AF_INET, SOCK_STREAM, 0);
    if (fd == JVM_IO_ERR) {
        /* note: if you run out of fds, you may not be able to load
         * the exception class, and get a NoClassDefFoundError
         * instead.
         */
        NET_ThrowNew(env, errno, "Can't create socket");
        return JNI_FALSE;
    }
    if (ttl > 0) {
      setsockopt(fd, IPPROTO_IP, IP_TTL, &ttl, sizeof(ttl));
    }

    /*
     * A network interface was specified, so let's bind to it.
     */
    if (netif != NULL) {
      if (bind(fd, (struct sockaddr*)netif, sizeof(struct sockaddr_in)) < 0) {
        NET_ThrowNew(env, errno, "Can't bind socket");
        close(fd);
        return JNI_FALSE;
      }
    }

    /*
     * Make the socket non blocking so we can use select/poll.
     */
    SET_NONBLOCKING(fd);

    /* no need to use NET_Connect as non-blocking */
    him.sin_port = htons(7);    /* Echo */
    connect_rv = JVM_Connect(fd, (struct sockaddr *)&him, len);

    /**
     * connection established or refused immediately, either way it means
     * we were able to reach the host!
     */
    if (connect_rv == 0 || errno == ECONNREFUSED) {
        close(fd);
        return JNI_TRUE;
    } else {
        int optlen;

        switch (errno) {
        case ENETUNREACH: /* Network Unreachable */
        case EAFNOSUPPORT: /* Address Family not supported */
        case EADDRNOTAVAIL: /* address is not available on  the  remote machine */
#ifdef __linux__
        case EINVAL:
        case EHOSTUNREACH:
          /*
           * On some Linux versions, when a socket is bound to the loopback
           * interface, connect will fail and errno will be set to EINVAL
           * or EHOSTUNREACH.  When that happens, don't throw an exception,
           * just return false.
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
          /* has connection been established? */
          optlen = sizeof(connect_rv);
          if (JVM_GetSockOpt(fd, SOL_SOCKET, SO_ERROR, (void*)&connect_rv,
                             &optlen) <0) {
            connect_rv = errno;
          }
          if (connect_rv == 0 || connect_rv == ECONNREFUSED) {
            close(fd);
            return JNI_TRUE;
          }
        }
        close(fd);
        return JNI_FALSE;
    }
}
