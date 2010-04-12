/*
 * Copyright 2000-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "jvm.h"
#include "jni_util.h"
#include "net_util.h"

#include "java_net_Inet4AddressImpl.h"

/* the initial size of our hostent buffers */
#define HENT_BUF_SIZE 1024
#define BIG_HENT_BUF_SIZE 10240  /* a jumbo-sized one */

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
    char hostname[MAXHOSTNAMELEN+1];

    hostname[0] = '\0';
    if (JVM_GetHostName(hostname, MAXHOSTNAMELEN)) {
        /* Something went wrong, maybe networking is not setup? */
        strcpy(hostname, "localhost");
    } else {
#ifdef __linux__
        /* On Linux gethostname() says "host.domain.sun.com".  On
         * Solaris gethostname() says "host", so extra work is needed.
         */
#else
        /* Solaris doesn't want to give us a fully qualified domain name.
         * We do a reverse lookup to try and get one.  This works
         * if DNS occurs before NIS in /etc/resolv.conf, but fails
         * if NIS comes first (it still gets only a partial name).
         * We use thread-safe system calls.
         */
#endif /* __linux__ */
        struct hostent res, res2, *hp;
        char buf[HENT_BUF_SIZE];
        char buf2[HENT_BUF_SIZE];
        int h_error=0;

#ifdef __GLIBC__
        gethostbyname_r(hostname, &res, buf, sizeof(buf), &hp, &h_error);
#else
        hp = gethostbyname_r(hostname, &res, buf, sizeof(buf), &h_error);
#endif
        if (hp) {
#ifdef __GLIBC__
            gethostbyaddr_r(hp->h_addr, hp->h_length, AF_INET,
                            &res2, buf2, sizeof(buf2), &hp, &h_error);
#else
            hp = gethostbyaddr_r(hp->h_addr, hp->h_length, AF_INET,
                                 &res2, buf2, sizeof(buf2), &h_error);
#endif
            if (hp) {
                /*
                 * If gethostbyaddr_r() found a fully qualified host name,
                 * returns that name. Otherwise, returns the hostname
                 * found by gethostname().
                 */
                char *p = hp->h_name;
                if ((strlen(hp->h_name) > strlen(hostname))
                    && (strncmp(hostname, hp->h_name, strlen(hostname)) == 0)
                    && (*(p + strlen(hostname)) == '.'))
                    strcpy(hostname, hp->h_name);
            }
        }
    }
    return (*env)->NewStringUTF(env, hostname);
}

static jclass ni_iacls;
static jclass ni_ia4cls;
static jmethodID ni_ia4ctrID;
static jfieldID ni_iaaddressID;
static jfieldID ni_iahostID;
static jfieldID ni_iafamilyID;
static int initialized = 0;

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
    struct hostent res, *hp = 0;
    char buf[HENT_BUF_SIZE];

    /* temporary buffer, on the off chance we need to expand */
    char *tmp = NULL;
    int h_error=0;

    if (!initialized) {
      ni_iacls = (*env)->FindClass(env, "java/net/InetAddress");
      ni_iacls = (*env)->NewGlobalRef(env, ni_iacls);
      ni_ia4cls = (*env)->FindClass(env, "java/net/Inet4Address");
      ni_ia4cls = (*env)->NewGlobalRef(env, ni_ia4cls);
      ni_ia4ctrID = (*env)->GetMethodID(env, ni_ia4cls, "<init>", "()V");
      ni_iaaddressID = (*env)->GetFieldID(env, ni_iacls, "address", "I");
      ni_iafamilyID = (*env)->GetFieldID(env, ni_iacls, "family", "I");
      ni_iahostID = (*env)->GetFieldID(env, ni_iacls, "hostName", "Ljava/lang/String;");
      initialized = 1;
    }

    if (IS_NULL(host)) {
        JNU_ThrowNullPointerException(env, "host is null");
        return 0;
    }
    hostname = JNU_GetStringPlatformChars(env, host, JNI_FALSE);
    CHECK_NULL_RETURN(hostname, NULL);

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

    /* Try once, with our static buffer. */
#ifdef __GLIBC__
    gethostbyname_r(hostname, &res, buf, sizeof(buf), &hp, &h_error);
#else
    hp = gethostbyname_r(hostname, &res, buf, sizeof(buf), &h_error);
#endif

    /* With the re-entrant system calls, it's possible that the buffer
     * we pass to it is not large enough to hold an exceptionally
     * large DNS entry.  This is signaled by errno->ERANGE.  We try once
     * more, with a very big size.
     */
    if (hp == NULL && errno == ERANGE) {
        if ((tmp = (char*)malloc(BIG_HENT_BUF_SIZE))) {
#ifdef __GLIBC__
            gethostbyname_r(hostname, &res, tmp, BIG_HENT_BUF_SIZE,
                            &hp, &h_error);
#else
            hp = gethostbyname_r(hostname, &res, tmp, BIG_HENT_BUF_SIZE,
                                 &h_error);
#endif
        }
    }
    if (hp != NULL) {
        struct in_addr **addrp = (struct in_addr **) hp->h_addr_list;
        int i = 0;

        while (*addrp != (struct in_addr *) 0) {
            i++;
            addrp++;
        }

        ret = (*env)->NewObjectArray(env, i, ni_iacls, NULL);
        if (IS_NULL(ret)) {
            /* we may have memory to free at the end of this */
            goto cleanupAndReturn;
        }
        addrp = (struct in_addr **) hp->h_addr_list;
        i = 0;
        while (*addrp) {
          jobject iaObj = (*env)->NewObject(env, ni_ia4cls, ni_ia4ctrID);
          if (IS_NULL(iaObj)) {
            ret = NULL;
            goto cleanupAndReturn;
          }
          (*env)->SetIntField(env, iaObj, ni_iaaddressID,
                              ntohl((*addrp)->s_addr));
          (*env)->SetObjectField(env, iaObj, ni_iahostID, host);
          (*env)->SetObjectArrayElement(env, ret, i, iaObj);
          addrp++;
          i++;
        }
    } else {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException",
                        (char *)hostname);
        ret = NULL;
    }

cleanupAndReturn:
    JNU_ReleaseStringPlatformChars(env, host, hostname);
    if (tmp != NULL) {
        free(tmp);
    }
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
    jint addr;
    struct hostent hent, *hp = 0;
    char buf[HENT_BUF_SIZE];
    int h_error = 0;
    char *tmp = NULL;

    /*
     * We are careful here to use the reentrant version of
     * gethostbyname because at the Java level this routine is not
     * protected by any synchronization.
     *
     * Still keeping the reentrant platform dependent calls temporarily
     * We should probably conform to one interface later.
     *
     */
    jbyte caddr[4];
    (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
    addr = ((caddr[0]<<24) & 0xff000000);
    addr |= ((caddr[1] <<16) & 0xff0000);
    addr |= ((caddr[2] <<8) & 0xff00);
    addr |= (caddr[3] & 0xff);
    addr = htonl(addr);
#ifdef __GLIBC__
    gethostbyaddr_r((char *)&addr, sizeof(addr), AF_INET, &hent,
                    buf, sizeof(buf), &hp, &h_error);
#else
    hp = gethostbyaddr_r((char *)&addr, sizeof(addr), AF_INET, &hent,
                         buf, sizeof(buf), &h_error);
#endif
    /* With the re-entrant system calls, it's possible that the buffer
     * we pass to it is not large enough to hold an exceptionally
     * large DNS entry.  This is signaled by errno->ERANGE.  We try once
     * more, with a very big size.
     */
    if (hp == NULL && errno == ERANGE) {
        if ((tmp = (char*)malloc(BIG_HENT_BUF_SIZE))) {
#ifdef __GLIBC__
            gethostbyaddr_r((char *)&addr, sizeof(addr), AF_INET,
                            &hent, tmp, BIG_HENT_BUF_SIZE, &hp, &h_error);
#else
            hp = gethostbyaddr_r((char *)&addr, sizeof(addr), AF_INET,
                                 &hent, tmp, BIG_HENT_BUF_SIZE, &h_error);
#endif
        } else {
            JNU_ThrowOutOfMemoryError(env, "getHostByAddr");
        }
    }
    if (hp == NULL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", NULL);
    } else {
        ret = (*env)->NewStringUTF(env, hp->h_name);
    }
    if (tmp) {
        free(tmp);
    }
    return ret;
}

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
          if (icmplen >= 8 && icmp->icmp_type == ICMP_ECHOREPLY &&
               (ntohs(icmp->icmp_id) == pid) &&
               (him->sin_addr.s_addr == sa_recv.sin_addr.s_addr)) {
            close(fd);
            return JNI_TRUE;
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
          /*
           * On some Linuxes, when bound to the loopback interface, connect
           * will fail and errno will be set to EINVAL. When that happens,
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
