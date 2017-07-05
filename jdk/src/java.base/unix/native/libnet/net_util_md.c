/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/tcp.h>        /* Defines TCP_NODELAY, needed for 2.6 */
#include <netinet/in.h>
#include <net/if.h>
#include <netdb.h>
#include <stdlib.h>
#include <dlfcn.h>

#ifndef _ALLBSD_SOURCE
#include <values.h>
#else
#include <limits.h>
#include <sys/param.h>
#include <sys/sysctl.h>
#include <sys/ioctl.h>
#ifndef MAXINT
#define MAXINT INT_MAX
#endif
#endif

#ifdef __solaris__
#include <sys/filio.h>
#include <sys/sockio.h>
#include <stropts.h>
#include <inet/nd.h>
#endif

#ifdef __linux__
#include <sys/ioctl.h>
#include <arpa/inet.h>
#include <net/route.h>
#include <sys/utsname.h>

#ifndef IPV6_FLOWINFO_SEND
#define IPV6_FLOWINFO_SEND      33
#endif

#endif

#ifdef _AIX
#include <sys/ioctl.h>
#endif

#include "jni_util.h"
#include "jvm.h"
#include "net_util.h"

#include "java_net_SocketOptions.h"

/*
 * EXCLBIND socket options only on Solaris
 */
#if defined(__solaris__) && !defined(TCP_EXCLBIND)
#define TCP_EXCLBIND            0x21
#endif
#if defined(__solaris__) && !defined(UDP_EXCLBIND)
#define UDP_EXCLBIND            0x0101
#endif

void setDefaultScopeID(JNIEnv *env, struct sockaddr *him)
{
#ifdef MACOSX
    static jclass ni_class = NULL;
    static jfieldID ni_defaultIndexID;
    if (ni_class == NULL) {
        jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
        CHECK_NULL(c);
        c = (*env)->NewGlobalRef(env, c);
        CHECK_NULL(c);
        ni_defaultIndexID = (*env)->GetStaticFieldID(
            env, c, "defaultIndex", "I");
        ni_class = c;
    }
    int defaultIndex;
    struct sockaddr_in6 *sin6 = (struct sockaddr_in6 *)him;
    if (sin6->sin6_family == AF_INET6 && (sin6->sin6_scope_id == 0)) {
        defaultIndex = (*env)->GetStaticIntField(env, ni_class,
                                                 ni_defaultIndexID);
        sin6->sin6_scope_id = defaultIndex;
    }
#endif
}

int getDefaultScopeID(JNIEnv *env) {
    int defaultIndex = 0;
    static jclass ni_class = NULL;
    static jfieldID ni_defaultIndexID;
    if (ni_class == NULL) {
        jclass c = (*env)->FindClass(env, "java/net/NetworkInterface");
        CHECK_NULL_RETURN(c, 0);
        c = (*env)->NewGlobalRef(env, c);
        CHECK_NULL_RETURN(c, 0);
        ni_defaultIndexID = (*env)->GetStaticFieldID(env, c,
                                                     "defaultIndex", "I");
        ni_class = c;
    }
    defaultIndex = (*env)->GetStaticIntField(env, ni_class,
                                             ni_defaultIndexID);
    return defaultIndex;
}

#define RESTARTABLE(_cmd, _result) do { \
    do { \
        _result = _cmd; \
    } while((_result == -1) && (errno == EINTR)); \
} while(0)

int NET_SocketAvailable(int s, jint *pbytes) {
    int result;
    RESTARTABLE(ioctl(s, FIONREAD, pbytes), result);
    // note: ioctl can return 0 when successful, NET_SocketAvailable
    // is expected to return 0 on failure and 1 on success.
    return (result == -1) ? 0 : 1;
}

#ifdef __solaris__
static int init_tcp_max_buf, init_udp_max_buf;
static int tcp_max_buf;
static int udp_max_buf;
static int useExclBind = 0;

/*
 * Get the specified parameter from the specified driver. The value
 * of the parameter is assumed to be an 'int'. If the parameter
 * cannot be obtained return -1
 */
int net_getParam(char *driver, char *param)
{
    struct strioctl stri;
    char buf [64];
    int s;
    int value;

    s = open (driver, O_RDWR);
    if (s < 0) {
        return -1;
    }
    strncpy (buf, param, sizeof(buf));
    stri.ic_cmd = ND_GET;
    stri.ic_timout = 0;
    stri.ic_dp = buf;
    stri.ic_len = sizeof(buf);
    if (ioctl (s, I_STR, &stri) < 0) {
        value = -1;
    } else {
        value = atoi(buf);
    }
    close (s);
    return value;
}

/*
 * Iterative way to find the max value that SO_SNDBUF or SO_RCVBUF
 * for Solaris versions that do not support the ioctl() in net_getParam().
 * Ugly, but only called once (for each sotype).
 *
 * As an optimization, we make a guess using the default values for Solaris
 * assuming they haven't been modified with ndd.
 */

#define MAX_TCP_GUESS 1024 * 1024
#define MAX_UDP_GUESS 2 * 1024 * 1024

#define FAIL_IF_NOT_ENOBUFS if (errno != ENOBUFS) return -1

static int findMaxBuf(int fd, int opt, int sotype) {
    int a = 0;
    int b = MAXINT;
    int initial_guess;
    int limit = -1;

    if (sotype == SOCK_DGRAM) {
        initial_guess = MAX_UDP_GUESS;
    } else {
        initial_guess = MAX_TCP_GUESS;
    }

    if (setsockopt(fd, SOL_SOCKET, opt, &initial_guess, sizeof(int)) == 0) {
        initial_guess++;
        if (setsockopt(fd, SOL_SOCKET, opt, &initial_guess,sizeof(int)) < 0) {
            FAIL_IF_NOT_ENOBUFS;
            return initial_guess - 1;
        }
        a = initial_guess;
    } else {
        FAIL_IF_NOT_ENOBUFS;
        b = initial_guess - 1;
    }
    do {
        int mid = a + (b-a)/2;
        if (setsockopt(fd, SOL_SOCKET, opt, &mid, sizeof(int)) == 0) {
            limit = mid;
            a = mid + 1;
        } else {
            FAIL_IF_NOT_ENOBUFS;
            b = mid - 1;
        }
    } while (b >= a);

    return limit;
}
#endif

#ifdef __linux__
static int vinit = 0;
static int kernelV24 = 0;
static int vinit24 = 0;

int kernelIsV24 () {
    if (!vinit24) {
        struct utsname sysinfo;
        if (uname(&sysinfo) == 0) {
            sysinfo.release[3] = '\0';
            if (strcmp(sysinfo.release, "2.4") == 0) {
                kernelV24 = JNI_TRUE;
            }
        }
        vinit24 = 1;
    }
    return kernelV24;
}

int getScopeID (struct sockaddr *him) {
    struct sockaddr_in6 *hext = (struct sockaddr_in6 *)him;
    return hext->sin6_scope_id;
}

int cmpScopeID (unsigned int scope, struct sockaddr *him) {
    struct sockaddr_in6 *hext = (struct sockaddr_in6 *)him;
    return hext->sin6_scope_id == scope;
}

#else

int getScopeID (struct sockaddr *him) {
    struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
    return him6->sin6_scope_id;
}

int cmpScopeID (unsigned int scope, struct sockaddr *him) {
    struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
    return him6->sin6_scope_id == scope;
}

#endif


void
NET_ThrowByNameWithLastError(JNIEnv *env, const char *name,
                   const char *defaultDetail) {
    char errmsg[255];
    sprintf(errmsg, "errno: %d, error: %s\n", errno, defaultDetail);
    JNU_ThrowByNameWithLastError(env, name, errmsg);
}

void
NET_ThrowCurrent(JNIEnv *env, char *msg) {
    NET_ThrowNew(env, errno, msg);
}

void
NET_ThrowNew(JNIEnv *env, int errorNumber, char *msg) {
    char fullMsg[512];
    if (!msg) {
        msg = "no further information";
    }
    switch(errorNumber) {
    case EBADF:
        jio_snprintf(fullMsg, sizeof(fullMsg), "socket closed: %s", msg);
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", fullMsg);
        break;
    case EINTR:
        JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException", msg);
        break;
    default:
        errno = errorNumber;
        JNU_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException", msg);
        break;
    }
}


jfieldID
NET_GetFileDescriptorID(JNIEnv *env)
{
    jclass cls = (*env)->FindClass(env, "java/io/FileDescriptor");
    CHECK_NULL_RETURN(cls, NULL);
    return (*env)->GetFieldID(env, cls, "fd", "I");
}

#if defined(DONT_ENABLE_IPV6)
jint  IPv6_supported()
{
    return JNI_FALSE;
}

#else /* !DONT_ENABLE_IPV6 */

jint  IPv6_supported()
{
#ifndef AF_INET6
    return JNI_FALSE;
#endif

#ifdef AF_INET6
    int fd;
    void *ipv6_fn;
    SOCKADDR sa;
    socklen_t sa_len = sizeof(sa);

    fd = socket(AF_INET6, SOCK_STREAM, 0) ;
    if (fd < 0) {
        /*
         *  TODO: We really cant tell since it may be an unrelated error
         *  for now we will assume that AF_INET6 is not available
         */
        return JNI_FALSE;
    }

    /*
     * If fd 0 is a socket it means we've been launched from inetd or
     * xinetd. If it's a socket then check the family - if it's an
     * IPv4 socket then we need to disable IPv6.
     */
    if (getsockname(0, (struct sockaddr *)&sa, &sa_len) == 0) {
        struct sockaddr *saP = (struct sockaddr *)&sa;
        if (saP->sa_family != AF_INET6) {
            return JNI_FALSE;
        }
    }

    /**
     * Linux - check if any interface has an IPv6 address.
     * Don't need to parse the line - we just need an indication.
     */
#ifdef __linux__
    {
        FILE *fP = fopen("/proc/net/if_inet6", "r");
        char buf[255];
        char *bufP;

        if (fP == NULL) {
            close(fd);
            return JNI_FALSE;
        }
        bufP = fgets(buf, sizeof(buf), fP);
        fclose(fP);
        if (bufP == NULL) {
            close(fd);
            return JNI_FALSE;
        }
    }
#endif

    /**
     * On Solaris 8 it's possible to create INET6 sockets even
     * though IPv6 is not enabled on all interfaces. Thus we
     * query the number of IPv6 addresses to verify that IPv6
     * has been configured on at least one interface.
     *
     * On Linux it doesn't matter - if IPv6 is built-in the
     * kernel then IPv6 addresses will be bound automatically
     * to all interfaces.
     */
#ifdef __solaris__

#ifdef SIOCGLIFNUM
    {
        struct lifnum numifs;

        numifs.lifn_family = AF_INET6;
        numifs.lifn_flags = 0;
        if (ioctl(fd, SIOCGLIFNUM, (char *)&numifs) < 0) {
            /**
             * SIOCGLIFNUM failed - assume IPv6 not configured
             */
            close(fd);
            return JNI_FALSE;
        }
        /**
         * If no IPv6 addresses then return false. If count > 0
         * it's possible that all IPv6 addresses are "down" but
         * that's okay as they may be brought "up" while the
         * VM is running.
         */
        if (numifs.lifn_count == 0) {
            close(fd);
            return JNI_FALSE;
        }
    }
#else
    /* SIOCGLIFNUM not defined in build environment ??? */
    close(fd);
    return JNI_FALSE;
#endif

#endif /* __solaris */

    /*
     *  OK we may have the stack available in the kernel,
     *  we should also check if the APIs are available.
     */
    ipv6_fn = JVM_FindLibraryEntry(RTLD_DEFAULT, "inet_pton");
    close(fd);
    if (ipv6_fn == NULL ) {
        return JNI_FALSE;
    } else {
        return JNI_TRUE;
    }
#endif /* AF_INET6 */
}
#endif /* DONT_ENABLE_IPV6 */

void NET_ThrowUnknownHostExceptionWithGaiError(JNIEnv *env,
                                               const char* hostname,
                                               int gai_error)
{
    int size;
    char *buf;
    const char *format = "%s: %s";
    const char *error_string = gai_strerror(gai_error);
    if (error_string == NULL)
        error_string = "unknown error";

    size = strlen(format) + strlen(hostname) + strlen(error_string) + 2;
    buf = (char *) malloc(size);
    if (buf) {
        jstring s;
        sprintf(buf, format, hostname, error_string);
        s = JNU_NewStringPlatform(env, buf);
        if (s != NULL) {
            jobject x = JNU_NewObjectByName(env,
                                            "java/net/UnknownHostException",
                                            "(Ljava/lang/String;)V", s);
            if (x != NULL)
                (*env)->Throw(env, x);
        }
        free(buf);
    }
}

void
NET_AllocSockaddr(struct sockaddr **him, int *len) {
#ifdef AF_INET6
    if (ipv6_available()) {
        struct sockaddr_in6 *him6 = (struct sockaddr_in6*)malloc(sizeof(struct sockaddr_in6));
        *him = (struct sockaddr*)him6;
        *len = sizeof(struct sockaddr_in6);
    } else
#endif /* AF_INET6 */
        {
            struct sockaddr_in *him4 = (struct sockaddr_in*)malloc(sizeof(struct sockaddr_in));
            *him = (struct sockaddr*)him4;
            *len = sizeof(struct sockaddr_in);
        }
}

#if defined(__linux__) && defined(AF_INET6)


/* following code creates a list of addresses from the kernel
 * routing table that are routed via the loopback address.
 * We check all destination addresses against this table
 * and override the scope_id field to use the relevant value for "lo"
 * in order to work-around the Linux bug that prevents packets destined
 * for certain local addresses from being sent via a physical interface.
 */

struct loopback_route {
    struct in6_addr addr; /* destination address */
    int plen; /* prefix length */
};

static struct loopback_route *loRoutes = 0;
static int nRoutes = 0; /* number of routes */
static int loRoutes_size = 16; /* initial size */
static int lo_scope_id = 0;

static void initLoopbackRoutes();

void printAddr (struct in6_addr *addr) {
    int i;
    for (i=0; i<16; i++) {
        printf ("%02x", addr->s6_addr[i]);
    }
    printf ("\n");
}

static jboolean needsLoopbackRoute (struct in6_addr* dest_addr) {
    int byte_count;
    int extra_bits, i;
    struct loopback_route *ptr;

    if (loRoutes == 0) {
        initLoopbackRoutes();
    }

    for (ptr = loRoutes, i=0; i<nRoutes; i++, ptr++) {
        struct in6_addr *target_addr=&ptr->addr;
        int dest_plen = ptr->plen;
        byte_count = dest_plen >> 3;
        extra_bits = dest_plen & 0x3;

        if (byte_count > 0) {
            if (memcmp(target_addr, dest_addr, byte_count)) {
                continue;  /* no match */
            }
        }

        if (extra_bits > 0) {
            unsigned char c1 = ((unsigned char *)target_addr)[byte_count];
            unsigned char c2 = ((unsigned char *)&dest_addr)[byte_count];
            unsigned char mask = 0xff << (8 - extra_bits);
            if ((c1 & mask) != (c2 & mask)) {
                continue;
            }
        }
        return JNI_TRUE;
    }
    return JNI_FALSE;
}


static void initLoopbackRoutes() {
    FILE *f;
    char srcp[8][5];
    char hopp[8][5];
    int dest_plen, src_plen, use, refcnt, metric;
    unsigned long flags;
    char dest_str[40];
    struct in6_addr dest_addr;
    char device[16];
    struct loopback_route *loRoutesTemp;

    if (loRoutes != 0) {
        free (loRoutes);
    }
    loRoutes = calloc (loRoutes_size, sizeof(struct loopback_route));
    if (loRoutes == 0) {
        return;
    }
    /*
     * Scan /proc/net/ipv6_route looking for a matching
     * route.
     */
    if ((f = fopen("/proc/net/ipv6_route", "r")) == NULL) {
        return ;
    }
    while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %02x "
                     "%4s%4s%4s%4s%4s%4s%4s%4s %02x "
                     "%4s%4s%4s%4s%4s%4s%4s%4s "
                     "%08x %08x %08x %08lx %8s",
                     dest_str, &dest_str[5], &dest_str[10], &dest_str[15],
                     &dest_str[20], &dest_str[25], &dest_str[30], &dest_str[35],
                     &dest_plen,
                     srcp[0], srcp[1], srcp[2], srcp[3],
                     srcp[4], srcp[5], srcp[6], srcp[7],
                     &src_plen,
                     hopp[0], hopp[1], hopp[2], hopp[3],
                     hopp[4], hopp[5], hopp[6], hopp[7],
                     &metric, &use, &refcnt, &flags, device) == 31) {

        /*
         * Some routes should be ignored
         */
        if ( (dest_plen < 0 || dest_plen > 128)  ||
             (src_plen != 0) ||
             (flags & (RTF_POLICY | RTF_FLOW)) ||
             ((flags & RTF_REJECT) && dest_plen == 0) ) {
            continue;
        }

        /*
         * Convert the destination address
         */
        dest_str[4] = ':';
        dest_str[9] = ':';
        dest_str[14] = ':';
        dest_str[19] = ':';
        dest_str[24] = ':';
        dest_str[29] = ':';
        dest_str[34] = ':';
        dest_str[39] = '\0';

        if (inet_pton(AF_INET6, dest_str, &dest_addr) < 0) {
            /* not an Ipv6 address */
            continue;
        }
        if (strcmp(device, "lo") != 0) {
            /* Not a loopback route */
            continue;
        } else {
            if (nRoutes == loRoutes_size) {
                loRoutesTemp = realloc (loRoutes, loRoutes_size *
                                        sizeof (struct loopback_route) * 2);

                if (loRoutesTemp == 0) {
                    free(loRoutes);
                    fclose (f);
                    return;
                }
                loRoutes=loRoutesTemp;
                loRoutes_size *= 2;
            }
            memcpy (&loRoutes[nRoutes].addr,&dest_addr,sizeof(struct in6_addr));
            loRoutes[nRoutes].plen = dest_plen;
            nRoutes ++;
        }
    }

    fclose (f);
    {
        /* now find the scope_id for "lo" */

        char devname[21];
        char addr6p[8][5];
        int plen, scope, dad_status, if_idx;

        if ((f = fopen("/proc/net/if_inet6", "r")) != NULL) {
            while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %08x %02x %02x %02x %20s\n",
                      addr6p[0], addr6p[1], addr6p[2], addr6p[3],
                      addr6p[4], addr6p[5], addr6p[6], addr6p[7],
                  &if_idx, &plen, &scope, &dad_status, devname) == 13) {

                if (strcmp(devname, "lo") == 0) {
                    /*
                     * Found - so just return the index
                     */
                    fclose(f);
                    lo_scope_id = if_idx;
                    return;
                }
            }
            fclose(f);
        }
    }
}

/*
 * Following is used for binding to local addresses. Equivalent
 * to code above, for bind().
 */

struct localinterface {
    int index;
    char localaddr [16];
};

static struct localinterface *localifs = 0;
static int localifsSize = 0;    /* size of array */
static int nifs = 0;            /* number of entries used in array */

/* not thread safe: make sure called once from one thread */

static void initLocalIfs () {
    FILE *f;
    unsigned char staddr [16];
    char ifname [33];
    struct localinterface *lif=0;
    int index, x1, x2, x3;
    unsigned int u0,u1,u2,u3,u4,u5,u6,u7,u8,u9,ua,ub,uc,ud,ue,uf;

    if ((f = fopen("/proc/net/if_inet6", "r")) == NULL) {
        return ;
    }
    while (fscanf (f, "%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x%2x "
                "%d %x %x %x %32s",&u0,&u1,&u2,&u3,&u4,&u5,&u6,&u7,
                &u8,&u9,&ua,&ub,&uc,&ud,&ue,&uf,
                &index, &x1, &x2, &x3, ifname) == 21) {
        staddr[0] = (unsigned char)u0;
        staddr[1] = (unsigned char)u1;
        staddr[2] = (unsigned char)u2;
        staddr[3] = (unsigned char)u3;
        staddr[4] = (unsigned char)u4;
        staddr[5] = (unsigned char)u5;
        staddr[6] = (unsigned char)u6;
        staddr[7] = (unsigned char)u7;
        staddr[8] = (unsigned char)u8;
        staddr[9] = (unsigned char)u9;
        staddr[10] = (unsigned char)ua;
        staddr[11] = (unsigned char)ub;
        staddr[12] = (unsigned char)uc;
        staddr[13] = (unsigned char)ud;
        staddr[14] = (unsigned char)ue;
        staddr[15] = (unsigned char)uf;
        nifs ++;
        if (nifs > localifsSize) {
            localifs = (struct localinterface *) realloc (
                        localifs, sizeof (struct localinterface)* (localifsSize+5));
            if (localifs == 0) {
                nifs = 0;
                fclose (f);
                return;
            }
            lif = localifs + localifsSize;
            localifsSize += 5;
        } else {
            lif ++;
        }
        memcpy (lif->localaddr, staddr, 16);
        lif->index = index;
    }
    fclose (f);
}

/* return the scope_id (interface index) of the
 * interface corresponding to the given address
 * returns 0 if no match found
 */

static int getLocalScopeID (char *addr) {
    struct localinterface *lif;
    int i;
    if (localifs == 0) {
        initLocalIfs();
    }
    for (i=0, lif=localifs; i<nifs; i++, lif++) {
        if (memcmp (addr, lif->localaddr, 16) == 0) {
            return lif->index;
        }
    }
    return 0;
}

void platformInit () {
    initLoopbackRoutes();
    initLocalIfs();
}

#elif defined(_AIX)

/* Initialize stubs for blocking I/O workarounds (see src/solaris/native/java/net/linux_close.c) */
extern void aix_close_init();

void platformInit () {
    aix_close_init();
}

#else

void platformInit () {}

#endif

void parseExclusiveBindProperty(JNIEnv *env) {
#ifdef __solaris__
    jstring s, flagSet;
    jclass iCls;
    jmethodID mid;

    s = (*env)->NewStringUTF(env, "sun.net.useExclusiveBind");
    CHECK_NULL(s);
    iCls = (*env)->FindClass(env, "java/lang/System");
    CHECK_NULL(iCls);
    mid = (*env)->GetStaticMethodID(env, iCls, "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;");
    CHECK_NULL(mid);
    flagSet = (*env)->CallStaticObjectMethod(env, iCls, mid, s);
    if (flagSet != NULL) {
        useExclBind = 1;
    }
#endif
}

JNIEXPORT jint JNICALL
NET_EnableFastTcpLoopback(int fd) {
    return 0;
}

/* In the case of an IPv4 Inetaddress this method will return an
 * IPv4 mapped address where IPv6 is available and v4MappedAddress is TRUE.
 * Otherwise it will return a sockaddr_in structure for an IPv4 InetAddress.
*/
JNIEXPORT int JNICALL
NET_InetAddressToSockaddr(JNIEnv *env, jobject iaObj, int port, struct sockaddr *him,
                          int *len, jboolean v4MappedAddress) {
    jint family;
    family = getInetAddress_family(env, iaObj);
#ifdef AF_INET6
    /* needs work. 1. family 2. clean up him6 etc deallocate memory */
    if (ipv6_available() && !(family == IPv4 && v4MappedAddress == JNI_FALSE)) {
        struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
        jbyte caddr[16];
        jint address;


        if (family == IPv4) { /* will convert to IPv4-mapped address */
            memset((char *) caddr, 0, 16);
            address = getInetAddress_addr(env, iaObj);
            if (address == INADDR_ANY) {
                /* we would always prefer IPv6 wildcard address
                   caddr[10] = 0xff;
                   caddr[11] = 0xff; */
            } else {
                caddr[10] = 0xff;
                caddr[11] = 0xff;
                caddr[12] = ((address >> 24) & 0xff);
                caddr[13] = ((address >> 16) & 0xff);
                caddr[14] = ((address >> 8) & 0xff);
                caddr[15] = (address & 0xff);
            }
        } else {
            getInet6Address_ipaddress(env, iaObj, (char *)caddr);
        }
        memset((char *)him6, 0, sizeof(struct sockaddr_in6));
        him6->sin6_port = htons(port);
        memcpy((void *)&(him6->sin6_addr), caddr, sizeof(struct in6_addr) );
        him6->sin6_family = AF_INET6;
        *len = sizeof(struct sockaddr_in6) ;

#if defined(_ALLBSD_SOURCE) && defined(_AF_INET6)
// XXXBSD: should we do something with scope id here ? see below linux comment
/* MMM: Come back to this! */
#endif

        /*
         * On Linux if we are connecting to a link-local address
         * we need to specify the interface in the scope_id (2.4 kernel only)
         *
         * If the scope was cached the we use the cached value. If not cached but
         * specified in the Inet6Address we use that, but we first check if the
         * address needs to be routed via the loopback interface. In this case,
         * we override the specified value with that of the loopback interface.
         * If no cached value exists and no value was specified by user, then
         * we try to determine a value from the routing table. In all these
         * cases the used value is cached for further use.
         */
#ifdef __linux__
        if (IN6_IS_ADDR_LINKLOCAL(&(him6->sin6_addr))) {
            int cached_scope_id = 0, scope_id = 0;

            if (ia6_cachedscopeidID) {
                cached_scope_id = (int)(*env)->GetIntField(env, iaObj, ia6_cachedscopeidID);
                /* if cached value exists then use it. Otherwise, check
                 * if scope is set in the address.
                 */
                if (!cached_scope_id) {
                    if (ia6_scopeidID) {
                        scope_id = getInet6Address_scopeid(env, iaObj);
                    }
                    if (scope_id != 0) {
                        /* check user-specified value for loopback case
                         * that needs to be overridden
                         */
                        if (kernelIsV24() && needsLoopbackRoute (&him6->sin6_addr)) {
                            cached_scope_id = lo_scope_id;
                            (*env)->SetIntField(env, iaObj, ia6_cachedscopeidID, cached_scope_id);
                        }
                    } else {
                        /*
                         * Otherwise consult the IPv6 routing tables to
                         * try determine the appropriate interface.
                         */
                        if (kernelIsV24()) {
                            cached_scope_id = getDefaultIPv6Interface( &(him6->sin6_addr) );
                        } else {
                            cached_scope_id = getLocalScopeID( (char *)&(him6->sin6_addr) );
                            if (cached_scope_id == 0) {
                                cached_scope_id = getDefaultIPv6Interface( &(him6->sin6_addr) );
                            }
                        }
                        (*env)->SetIntField(env, iaObj, ia6_cachedscopeidID, cached_scope_id);
                    }
                }
            }

            /*
             * If we have a scope_id use the extended form
             * of sockaddr_in6.
             */

            struct sockaddr_in6 *him6 =
                    (struct sockaddr_in6 *)him;
            him6->sin6_scope_id = cached_scope_id != 0 ?
                                        cached_scope_id    : scope_id;
            *len = sizeof(struct sockaddr_in6);
        }
#else
        /* handle scope_id for solaris */

        if (family != IPv4) {
            if (ia6_scopeidID) {
                him6->sin6_scope_id = getInet6Address_scopeid(env, iaObj);
            }
        }
#endif
    } else
#endif /* AF_INET6 */
        {
            struct sockaddr_in *him4 = (struct sockaddr_in*)him;
            jint address;
            if (family == IPv6) {
              JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Protocol family unavailable");
              return -1;
            }
            memset((char *) him4, 0, sizeof(struct sockaddr_in));
            address = getInetAddress_addr(env, iaObj);
            him4->sin_port = htons((short) port);
            him4->sin_addr.s_addr = (uint32_t) htonl(address);
            him4->sin_family = AF_INET;
            *len = sizeof(struct sockaddr_in);
        }
    return 0;
}

void
NET_SetTrafficClass(struct sockaddr *him, int trafficClass) {
#ifdef AF_INET6
    if (him->sa_family == AF_INET6) {
        struct sockaddr_in6 *him6 = (struct sockaddr_in6 *)him;
        him6->sin6_flowinfo = htonl((trafficClass & 0xff) << 20);
    }
#endif /* AF_INET6 */
}

JNIEXPORT jint JNICALL
NET_GetPortFromSockaddr(struct sockaddr *him) {
#ifdef AF_INET6
    if (him->sa_family == AF_INET6) {
        return ntohs(((struct sockaddr_in6*)him)->sin6_port);

        } else
#endif /* AF_INET6 */
            {
                return ntohs(((struct sockaddr_in*)him)->sin_port);
            }
}

int
NET_IsIPv4Mapped(jbyte* caddr) {
    int i;
    for (i = 0; i < 10; i++) {
        if (caddr[i] != 0x00) {
            return 0; /* false */
        }
    }

    if (((caddr[10] & 0xff) == 0xff) && ((caddr[11] & 0xff) == 0xff)) {
        return 1; /* true */
    }
    return 0; /* false */
}

int
NET_IPv4MappedToIPv4(jbyte* caddr) {
    return ((caddr[12] & 0xff) << 24) | ((caddr[13] & 0xff) << 16) | ((caddr[14] & 0xff) << 8)
        | (caddr[15] & 0xff);
}

int
NET_IsEqual(jbyte* caddr1, jbyte* caddr2) {
    int i;
    for (i = 0; i < 16; i++) {
        if (caddr1[i] != caddr2[i]) {
            return 0; /* false */
        }
    }
    return 1;
}

int NET_IsZeroAddr(jbyte* caddr) {
    int i;
    for (i = 0; i < 16; i++) {
        if (caddr[i] != 0) {
            return 0;
        }
    }
    return 1;
}

/*
 * Map the Java level socket option to the platform specific
 * level and option name.
 */
int
NET_MapSocketOption(jint cmd, int *level, int *optname) {
    static struct {
        jint cmd;
        int level;
        int optname;
    } const opts[] = {
        { java_net_SocketOptions_TCP_NODELAY,           IPPROTO_TCP,    TCP_NODELAY },
        { java_net_SocketOptions_SO_OOBINLINE,          SOL_SOCKET,     SO_OOBINLINE },
        { java_net_SocketOptions_SO_LINGER,             SOL_SOCKET,     SO_LINGER },
        { java_net_SocketOptions_SO_SNDBUF,             SOL_SOCKET,     SO_SNDBUF },
        { java_net_SocketOptions_SO_RCVBUF,             SOL_SOCKET,     SO_RCVBUF },
        { java_net_SocketOptions_SO_KEEPALIVE,          SOL_SOCKET,     SO_KEEPALIVE },
        { java_net_SocketOptions_SO_REUSEADDR,          SOL_SOCKET,     SO_REUSEADDR },
        { java_net_SocketOptions_SO_BROADCAST,          SOL_SOCKET,     SO_BROADCAST },
        { java_net_SocketOptions_IP_TOS,                IPPROTO_IP,     IP_TOS },
        { java_net_SocketOptions_IP_MULTICAST_IF,       IPPROTO_IP,     IP_MULTICAST_IF },
        { java_net_SocketOptions_IP_MULTICAST_IF2,      IPPROTO_IP,     IP_MULTICAST_IF },
        { java_net_SocketOptions_IP_MULTICAST_LOOP,     IPPROTO_IP,     IP_MULTICAST_LOOP },
    };

    int i;

    /*
     * Different multicast options if IPv6 is enabled
     */
#ifdef AF_INET6
    if (ipv6_available()) {
        switch (cmd) {
            case java_net_SocketOptions_IP_MULTICAST_IF:
            case java_net_SocketOptions_IP_MULTICAST_IF2:
                *level = IPPROTO_IPV6;
                *optname = IPV6_MULTICAST_IF;
                return 0;

            case java_net_SocketOptions_IP_MULTICAST_LOOP:
                *level = IPPROTO_IPV6;
                *optname = IPV6_MULTICAST_LOOP;
                return 0;
        }
    }
#endif

    /*
     * Map the Java level option to the native level
     */
    for (i=0; i<(int)(sizeof(opts) / sizeof(opts[0])); i++) {
        if (cmd == opts[i].cmd) {
            *level = opts[i].level;
            *optname = opts[i].optname;
            return 0;
        }
    }

    /* not found */
    return -1;
}

/*
 * Determine the default interface for an IPv6 address.
 *
 * 1. Scans /proc/net/ipv6_route for a matching route
 *    (eg: fe80::/10 or a route for the specific address).
 *    This will tell us the interface to use (eg: "eth0").
 *
 * 2. Lookup /proc/net/if_inet6 to map the interface
 *    name to an interface index.
 *
 * Returns :-
 *      -1 if error
 *       0 if no matching interface
 *      >1 interface index to use for the link-local address.
 */
#if defined(__linux__) && defined(AF_INET6)
int getDefaultIPv6Interface(struct in6_addr *target_addr) {
    FILE *f;
    char srcp[8][5];
    char hopp[8][5];
    int dest_plen, src_plen, use, refcnt, metric;
    unsigned long flags;
    char dest_str[40];
    struct in6_addr dest_addr;
    char device[16];
    jboolean match = JNI_FALSE;

    /*
     * Scan /proc/net/ipv6_route looking for a matching
     * route.
     */
    if ((f = fopen("/proc/net/ipv6_route", "r")) == NULL) {
        return -1;
    }
    while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %02x "
                     "%4s%4s%4s%4s%4s%4s%4s%4s %02x "
                     "%4s%4s%4s%4s%4s%4s%4s%4s "
                     "%08x %08x %08x %08lx %8s",
                     dest_str, &dest_str[5], &dest_str[10], &dest_str[15],
                     &dest_str[20], &dest_str[25], &dest_str[30], &dest_str[35],
                     &dest_plen,
                     srcp[0], srcp[1], srcp[2], srcp[3],
                     srcp[4], srcp[5], srcp[6], srcp[7],
                     &src_plen,
                     hopp[0], hopp[1], hopp[2], hopp[3],
                     hopp[4], hopp[5], hopp[6], hopp[7],
                     &metric, &use, &refcnt, &flags, device) == 31) {

        /*
         * Some routes should be ignored
         */
        if ( (dest_plen < 0 || dest_plen > 128)  ||
             (src_plen != 0) ||
             (flags & (RTF_POLICY | RTF_FLOW)) ||
             ((flags & RTF_REJECT) && dest_plen == 0) ) {
            continue;
        }

        /*
         * Convert the destination address
         */
        dest_str[4] = ':';
        dest_str[9] = ':';
        dest_str[14] = ':';
        dest_str[19] = ':';
        dest_str[24] = ':';
        dest_str[29] = ':';
        dest_str[34] = ':';
        dest_str[39] = '\0';

        if (inet_pton(AF_INET6, dest_str, &dest_addr) < 0) {
            /* not an Ipv6 address */
            continue;
        } else {
            /*
             * The prefix len (dest_plen) indicates the number of bits we
             * need to match on.
             *
             * dest_plen / 8    => number of bytes to match
             * dest_plen % 8    => number of additional bits to match
             *
             * eg: fe80::/10 => match 1 byte + 2 additional bits in the
             *                  the next byte.
             */
            int byte_count = dest_plen >> 3;
            int extra_bits = dest_plen & 0x3;

            if (byte_count > 0) {
                if (memcmp(target_addr, &dest_addr, byte_count)) {
                    continue;  /* no match */
                }
            }

            if (extra_bits > 0) {
                unsigned char c1 = ((unsigned char *)target_addr)[byte_count];
                unsigned char c2 = ((unsigned char *)&dest_addr)[byte_count];
                unsigned char mask = 0xff << (8 - extra_bits);
                if ((c1 & mask) != (c2 & mask)) {
                    continue;
                }
            }

            /*
             * We have a match
             */
            match = JNI_TRUE;
            break;
        }
    }
    fclose(f);

    /*
     * If there's a match then we lookup the interface
     * index.
     */
    if (match) {
        char devname[21];
        char addr6p[8][5];
        int plen, scope, dad_status, if_idx;

        if ((f = fopen("/proc/net/if_inet6", "r")) != NULL) {
            while (fscanf(f, "%4s%4s%4s%4s%4s%4s%4s%4s %08x %02x %02x %02x %20s\n",
                      addr6p[0], addr6p[1], addr6p[2], addr6p[3],
                      addr6p[4], addr6p[5], addr6p[6], addr6p[7],
                  &if_idx, &plen, &scope, &dad_status, devname) == 13) {

                if (strcmp(devname, device) == 0) {
                    /*
                     * Found - so just return the index
                     */
                    fclose(f);
                    return if_idx;
                }
            }
            fclose(f);
        } else {
            /*
             * Couldn't open /proc/net/if_inet6
             */
            return -1;
        }
    }

    /*
     * If we get here it means we didn't there wasn't any
     * route or we couldn't get the index of the interface.
     */
    return 0;
}
#endif


/*
 * Wrapper for getsockopt system routine - does any necessary
 * pre/post processing to deal with OS specific oddities :-
 *
 * IP_TOS is a no-op with IPv6 sockets as it's setup when
 * the connection is established.
 *
 * On Linux the SO_SNDBUF/SO_RCVBUF values must be post-processed
 * to compensate for an incorrect value returned by the kernel.
 */
int
NET_GetSockOpt(int fd, int level, int opt, void *result,
               int *len)
{
    int rv;
    socklen_t socklen = *len;

#ifdef AF_INET6
    if ((level == IPPROTO_IP) && (opt == IP_TOS)) {
        if (ipv6_available()) {

            /*
             * For IPv6 socket option implemented at Java-level
             * so return -1.
             */
            int *tc = (int *)result;
            *tc = -1;
            return 0;
        }
    }
#endif

    rv = getsockopt(fd, level, opt, result, &socklen);
    *len = socklen;

    if (rv < 0) {
        return rv;
    }

#ifdef __linux__
    /*
     * On Linux SO_SNDBUF/SO_RCVBUF aren't symmetric. This
     * stems from additional socket structures in the send
     * and receive buffers.
     */
    if ((level == SOL_SOCKET) && ((opt == SO_SNDBUF)
                                  || (opt == SO_RCVBUF))) {
        int n = *((int *)result);
        n /= 2;
        *((int *)result) = n;
    }
#endif

/* Workaround for Mac OS treating linger value as
 *  signed integer
 */
#ifdef MACOSX
    if (level == SOL_SOCKET && opt == SO_LINGER) {
        struct linger* to_cast = (struct linger*)result;
        to_cast->l_linger = (unsigned short)to_cast->l_linger;
    }
#endif
    return rv;
}

/*
 * Wrapper for setsockopt system routine - performs any
 * necessary pre/post processing to deal with OS specific
 * issue :-
 *
 * On Solaris need to limit the suggested value for SO_SNDBUF
 * and SO_RCVBUF to the kernel configured limit
 *
 * For IP_TOS socket option need to mask off bits as this
 * aren't automatically masked by the kernel and results in
 * an error. In addition IP_TOS is a NOOP with IPv6 as it
 * should be setup as connection time.
 */
int
NET_SetSockOpt(int fd, int level, int  opt, const void *arg,
               int len)
{

#ifndef IPTOS_TOS_MASK
#define IPTOS_TOS_MASK 0x1e
#endif
#ifndef IPTOS_PREC_MASK
#define IPTOS_PREC_MASK 0xe0
#endif

#if defined(_ALLBSD_SOURCE)
#if defined(KIPC_MAXSOCKBUF)
    int mib[3];
    size_t rlen;
#endif

    int *bufsize;

#ifdef __APPLE__
    static int maxsockbuf = -1;
#else
    static long maxsockbuf = -1;
#endif
#endif

    /*
     * IPPROTO/IP_TOS :-
     * 1. IPv6 on Solaris/Mac OS: NOOP and will be set
     *    in flowinfo field when connecting TCP socket,
     *    or sending UDP packet.
     * 2. IPv6 on Linux: By default Linux ignores flowinfo
     *    field so enable IPV6_FLOWINFO_SEND so that flowinfo
     *    will be examined. We also set the IPv4 TOS option in this case.
     * 3. IPv4: set socket option based on ToS and Precedence
     *    fields (otherwise get invalid argument)
     */
    if (level == IPPROTO_IP && opt == IP_TOS) {
        int *iptos;

#if defined(AF_INET6) && (defined(__solaris__) || defined(MACOSX))
        if (ipv6_available()) {
            return 0;
        }
#endif

#if defined(AF_INET6) && defined(__linux__)
        if (ipv6_available()) {
            int optval = 1;
            if (setsockopt(fd, IPPROTO_IPV6, IPV6_FLOWINFO_SEND,
                           (void *)&optval, sizeof(optval)) < 0) {
                return -1;
            }
        }
#endif

        iptos = (int *)arg;
        *iptos &= (IPTOS_TOS_MASK | IPTOS_PREC_MASK);
    }

    /*
     * SOL_SOCKET/{SO_SNDBUF,SO_RCVBUF} - On Solaris we may need to clamp
     * the value when it exceeds the system limit.
     */
#ifdef __solaris__
    if (level == SOL_SOCKET) {
        if (opt == SO_SNDBUF || opt == SO_RCVBUF) {
            int sotype=0;
            socklen_t arglen;
            int *bufsize, maxbuf;
            int ret;

            /* Attempt with the original size */
            ret = setsockopt(fd, level, opt, arg, len);
            if ((ret == 0) || (ret == -1 && errno != ENOBUFS))
                return ret;

            /* Exceeded system limit so clamp and retry */

            arglen = sizeof(sotype);
            if (getsockopt(fd, SOL_SOCKET, SO_TYPE, (void *)&sotype,
                           &arglen) < 0) {
                return -1;
            }

            /*
             * We try to get tcp_maxbuf (and udp_max_buf) using
             * an ioctl() that isn't available on all versions of Solaris.
             * If that fails, we use the search algorithm in findMaxBuf()
             */
            if (!init_tcp_max_buf && sotype == SOCK_STREAM) {
                tcp_max_buf = net_getParam("/dev/tcp", "tcp_max_buf");
                if (tcp_max_buf == -1) {
                    tcp_max_buf = findMaxBuf(fd, opt, SOCK_STREAM);
                    if (tcp_max_buf == -1) {
                        return -1;
                    }
                }
                init_tcp_max_buf = 1;
            } else if (!init_udp_max_buf && sotype == SOCK_DGRAM) {
                udp_max_buf = net_getParam("/dev/udp", "udp_max_buf");
                if (udp_max_buf == -1) {
                    udp_max_buf = findMaxBuf(fd, opt, SOCK_DGRAM);
                    if (udp_max_buf == -1) {
                        return -1;
                    }
                }
                init_udp_max_buf = 1;
            }

            maxbuf = (sotype == SOCK_STREAM) ? tcp_max_buf : udp_max_buf;
            bufsize = (int *)arg;
            if (*bufsize > maxbuf) {
                *bufsize = maxbuf;
            }
        }
    }
#endif

#ifdef _AIX
    if (level == SOL_SOCKET) {
        if (opt == SO_SNDBUF || opt == SO_RCVBUF) {
            /*
             * Just try to set the requested size. If it fails we will leave the
             * socket option as is. Setting the buffer size means only a hint in
             * the jse2/java software layer, see javadoc. In the previous
             * solution the buffer has always been truncated to a length of
             * 0x100000 Byte, even if the technical limit has not been reached.
             * This kind of absolute truncation was unexpected in the jck tests.
             */
            int ret = setsockopt(fd, level, opt, arg, len);
            if ((ret == 0) || (ret == -1 && errno == ENOBUFS)) {
                // Accept failure because of insufficient buffer memory resources.
                return 0;
            } else {
                // Deliver all other kinds of errors.
                return ret;
            }
        }
    }
#endif

    /*
     * On Linux the receive buffer is used for both socket
     * structures and the the packet payload. The implication
     * is that if SO_RCVBUF is too small then small packets
     * must be discard.
     */
#ifdef __linux__
    if (level == SOL_SOCKET && opt == SO_RCVBUF) {
        int *bufsize = (int *)arg;
        if (*bufsize < 1024) {
            *bufsize = 1024;
        }
    }
#endif

#if defined(_ALLBSD_SOURCE)
    /*
     * SOL_SOCKET/{SO_SNDBUF,SO_RCVBUF} - On FreeBSD need to
     * ensure that value is <= kern.ipc.maxsockbuf as otherwise we get
     * an ENOBUFS error.
     */
    if (level == SOL_SOCKET) {
        if (opt == SO_SNDBUF || opt == SO_RCVBUF) {
#ifdef KIPC_MAXSOCKBUF
            if (maxsockbuf == -1) {
               mib[0] = CTL_KERN;
               mib[1] = KERN_IPC;
               mib[2] = KIPC_MAXSOCKBUF;
               rlen = sizeof(maxsockbuf);
               if (sysctl(mib, 3, &maxsockbuf, &rlen, NULL, 0) == -1)
                   maxsockbuf = 1024;

#if 1
               /* XXXBSD: This is a hack to workaround mb_max/mb_max_adj
                  problem.  It should be removed when kern.ipc.maxsockbuf
                  will be real value. */
               maxsockbuf = (maxsockbuf/5)*4;
#endif
           }
#elif defined(__OpenBSD__)
           maxsockbuf = SB_MAX;
#else
           maxsockbuf = 64 * 1024;      /* XXX: NetBSD */
#endif

           bufsize = (int *)arg;
           if (*bufsize > maxsockbuf) {
               *bufsize = maxsockbuf;
           }

           if (opt == SO_RCVBUF && *bufsize < 1024) {
                *bufsize = 1024;
           }

        }
    }
#endif

#if defined(_ALLBSD_SOURCE) || defined(_AIX)
    /*
     * On Solaris, SO_REUSEADDR will allow multiple datagram
     * sockets to bind to the same port. The network jck tests check
     * for this "feature", so we need to emulate it by turning on
     * SO_REUSEPORT as well for that combination.
     */
    if (level == SOL_SOCKET && opt == SO_REUSEADDR) {
        int sotype;
        socklen_t arglen;

        arglen = sizeof(sotype);
        if (getsockopt(fd, SOL_SOCKET, SO_TYPE, (void *)&sotype, &arglen) < 0) {
            return -1;
        }

        if (sotype == SOCK_DGRAM) {
            setsockopt(fd, level, SO_REUSEPORT, arg, len);
        }
    }
#endif

    return setsockopt(fd, level, opt, arg, len);
}

/*
 * Wrapper for bind system call - performs any necessary pre/post
 * processing to deal with OS specific issues :-
 *
 * Linux allows a socket to bind to 127.0.0.255 which must be
 * caught.
 *
 * On Solaris with IPv6 enabled we must use an exclusive
 * bind to guarantee a unique port number across the IPv4 and
 * IPv6 port spaces.
 *
 */
int
NET_Bind(int fd, struct sockaddr *him, int len)
{
#if defined(__solaris__) && defined(AF_INET6)
    int level = -1;
    int exclbind = -1;
#endif
    int rv;

#ifdef __linux__
    /*
     * ## get bugId for this issue - goes back to 1.2.2 port ##
     * ## When IPv6 is enabled this will be an IPv4-mapped
     * ## with family set to AF_INET6
     */
    if (him->sa_family == AF_INET) {
        struct sockaddr_in *sa = (struct sockaddr_in *)him;
        if ((ntohl(sa->sin_addr.s_addr) & 0x7f0000ff) == 0x7f0000ff) {
            errno = EADDRNOTAVAIL;
            return -1;
        }
    }
#endif

#if defined(__solaris__) && defined(AF_INET6)
    /*
     * Solaris has separate IPv4 and IPv6 port spaces so we
     * use an exclusive bind when SO_REUSEADDR is not used to
     * give the illusion of a unified port space.
     * This also avoids problems with IPv6 sockets connecting
     * to IPv4 mapped addresses whereby the socket conversion
     * results in a late bind that fails because the
     * corresponding IPv4 port is in use.
     */
    if (ipv6_available()) {
        int arg;
        socklen_t len;

        len = sizeof(arg);
        if (useExclBind || getsockopt(fd, SOL_SOCKET, SO_REUSEADDR,
                       (char *)&arg, &len) == 0) {
            if (useExclBind || arg == 0) {
                /*
                 * SO_REUSEADDR is disabled or sun.net.useExclusiveBind
                 * property is true so enable TCP_EXCLBIND or
                 * UDP_EXCLBIND
                 */
                len = sizeof(arg);
                if (getsockopt(fd, SOL_SOCKET, SO_TYPE, (char *)&arg,
                               &len) == 0) {
                    if (arg == SOCK_STREAM) {
                        level = IPPROTO_TCP;
                        exclbind = TCP_EXCLBIND;
                    } else {
                        level = IPPROTO_UDP;
                        exclbind = UDP_EXCLBIND;
                    }
                }

                arg = 1;
                setsockopt(fd, level, exclbind, (char *)&arg,
                           sizeof(arg));
            }
        }
    }

#endif

    rv = bind(fd, him, len);

#if defined(__solaris__) && defined(AF_INET6)
    if (rv < 0) {
        int en = errno;
        /* Restore *_EXCLBIND if the bind fails */
        if (exclbind != -1) {
            int arg = 0;
            setsockopt(fd, level, exclbind, (char *)&arg,
                       sizeof(arg));
        }
        errno = en;
    }
#endif

    return rv;
}

/**
 * Wrapper for poll with timeout on a single file descriptor.
 *
 * flags (defined in net_util_md.h can be any combination of
 * NET_WAIT_READ, NET_WAIT_WRITE & NET_WAIT_CONNECT.
 *
 * The function will return when either the socket is ready for one
 * of the specified operation or the timeout expired.
 *
 * It returns the time left from the timeout (possibly 0), or -1 if it expired.
 */

jint
NET_Wait(JNIEnv *env, jint fd, jint flags, jint timeout)
{
    jlong prevTime = JVM_CurrentTimeMillis(env, 0);
    jint read_rv;

    while (1) {
        jlong newTime;
        struct pollfd pfd;
        pfd.fd = fd;
        pfd.events = 0;
        if (flags & NET_WAIT_READ)
          pfd.events |= POLLIN;
        if (flags & NET_WAIT_WRITE)
          pfd.events |= POLLOUT;
        if (flags & NET_WAIT_CONNECT)
          pfd.events |= POLLOUT;

        errno = 0;
        read_rv = NET_Poll(&pfd, 1, timeout);

        newTime = JVM_CurrentTimeMillis(env, 0);
        timeout -= (newTime - prevTime);
        if (timeout <= 0) {
          return read_rv > 0 ? 0 : -1;
        }
        prevTime = newTime;

        if (read_rv > 0) {
          break;
        }


      } /* while */

    return timeout;
}
