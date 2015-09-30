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
#include "net_util.h"
#include "icmp.h"


/*
 * Returns true if hostname is in dotted IP address format. Note that this
 * function performs a syntax check only. For each octet it just checks that
 * the octet is at most 3 digits.
 */
jboolean isDottedIPAddress(const char *hostname, unsigned int *addrp) {
    char *c = (char *)hostname;
    int octets = 0;
    unsigned int cur = 0;
    int digit_cnt = 0;

    while (*c) {
        if (*c == '.') {
            if (digit_cnt == 0) {
                return JNI_FALSE;
            } else {
                if (octets < 4) {
                    addrp[octets++] = cur;
                    cur = 0;
                    digit_cnt = 0;
                } else {
                    return JNI_FALSE;
                }
            }
            c++;
            continue;
        }

        if ((*c < '0') || (*c > '9')) {
            return JNI_FALSE;
        }

        digit_cnt++;
        if (digit_cnt > 3) {
            return JNI_FALSE;
        }

        /* don't check if current octet > 255 */
        cur = cur*10 + (*c - '0');

        /* Move onto next character and check for EOF */
        c++;
        if (*c == '\0') {
            if (octets < 4) {
                addrp[octets++] = cur;
            } else {
                return JNI_FALSE;
            }
        }
    }

    return (jboolean)(octets == 4);
}

/*
 * Inet4AddressImpl
 */

/*
 * Class:     java_net_Inet4AddressImpl
 * Method:    getLocalHostName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_java_net_Inet4AddressImpl_getLocalHostName (JNIEnv *env, jobject this) {
    char hostname[256];

    if (gethostname(hostname, sizeof hostname) == -1) {
        strcpy(hostname, "localhost");
    }
    return JNU_NewStringPlatform(env, hostname);
}

/*
 * Find an internet address for a given hostname.  Not this this
 * code only works for addresses of type INET. The translation
 * of %d.%d.%d.%d to an address (int) occurs in java now, so the
 * String "host" shouldn't be a %d.%d.%d.%d string. The only
 * exception should be when any of the %d are out of range and
 * we fallback to a lookup.
 *
 * Class:     java_net_Inet4AddressImpl
 * Method:    lookupAllHostAddr
 * Signature: (Ljava/lang/String;)[[B
 *
 * This is almost shared code
 */

JNIEXPORT jobjectArray JNICALL
Java_java_net_Inet4AddressImpl_lookupAllHostAddr(JNIEnv *env, jobject this,
                                                jstring host) {
    const char *hostname;
    struct hostent *hp;
    unsigned int addr[4];

    jobjectArray ret = NULL;

    initInetAddressIDs(env);
    JNU_CHECK_EXCEPTION_RETURN(env, NULL);

    if (IS_NULL(host)) {
        JNU_ThrowNullPointerException(env, "host argument");
        return NULL;
    }
    hostname = JNU_GetStringPlatformChars(env, host, JNI_FALSE);
    CHECK_NULL_RETURN(hostname, NULL);

    /*
     * The NT/2000 resolver tolerates a space in front of localhost. This
     * is not consistent with other implementations of gethostbyname.
     * In addition we must do a white space check on Solaris to avoid a
     * bug whereby 0.0.0.0 is returned if any host name has a white space.
     */
    if (isspace(hostname[0])) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", hostname);
        goto cleanupAndReturn;
    }

    /*
     * If the format is x.x.x.x then don't use gethostbyname as Windows
     * is unable to handle octets which are out of range.
     */
    if (isDottedIPAddress(hostname, &addr[0])) {
        unsigned int address;
        jobject iaObj;

        /*
         * Are any of the octets out of range?
         */
        if (addr[0] > 255 || addr[1] > 255 || addr[2] > 255 || addr[3] > 255) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", hostname);
            goto cleanupAndReturn;
        }

        /*
         * Return an byte array with the populated address.
         */
        address = (addr[3]<<24) & 0xff000000;
        address |= (addr[2]<<16) & 0xff0000;
        address |= (addr[1]<<8) & 0xff00;
        address |= addr[0];

        ret = (*env)->NewObjectArray(env, 1, ia_class, NULL);

        if (IS_NULL(ret)) {
            goto cleanupAndReturn;
        }

        iaObj = (*env)->NewObject(env, ia4_class, ia4_ctrID);
        if (IS_NULL(iaObj)) {
          ret = NULL;
          goto cleanupAndReturn;
        }
        setInetAddress_addr(env, iaObj, ntohl(address));
        (*env)->SetObjectArrayElement(env, ret, 0, iaObj);
        JNU_ReleaseStringPlatformChars(env, host, hostname);
        return ret;
    }

    /*
     * Perform the lookup
     */
    if ((hp = gethostbyname((char*)hostname)) != NULL) {
        struct in_addr **addrp = (struct in_addr **) hp->h_addr_list;
        int len = sizeof(struct in_addr);
        int i = 0;

        while (*addrp != (struct in_addr *) 0) {
            i++;
            addrp++;
        }

        ret = (*env)->NewObjectArray(env, i, ia_class, NULL);

        if (IS_NULL(ret)) {
            goto cleanupAndReturn;
        }

        addrp = (struct in_addr **) hp->h_addr_list;
        i = 0;
        while (*addrp != (struct in_addr *) 0) {
          jobject iaObj = (*env)->NewObject(env, ia4_class, ia4_ctrID);
          if (IS_NULL(iaObj)) {
            ret = NULL;
            goto cleanupAndReturn;
          }
          setInetAddress_addr(env, iaObj, ntohl((*addrp)->s_addr));
          setInetAddress_hostName(env, iaObj, host);
          (*env)->SetObjectArrayElement(env, ret, i, iaObj);
          addrp++;
          i++;
        }
    } else if (WSAGetLastError() == WSATRY_AGAIN) {
        NET_ThrowByNameWithLastError(env,
                                     JNU_JAVANETPKG "UnknownHostException",
                                     hostname);
    } else {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", hostname);
    }

cleanupAndReturn:
    JNU_ReleaseStringPlatformChars(env, host, hostname);
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
    struct hostent *hp;
    jbyte caddr[4];
    jint addr;
    (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
    addr = ((caddr[0]<<24) & 0xff000000);
    addr |= ((caddr[1] <<16) & 0xff0000);
    addr |= ((caddr[2] <<8) & 0xff00);
    addr |= (caddr[3] & 0xff);
    addr = htonl(addr);

    hp = gethostbyaddr((char *)&addr, sizeof(addr), AF_INET);
    if (hp == NULL) {
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", 0);
        return NULL;
    }
    if (hp->h_name == NULL) { /* Deal with bug in Windows XP */
        JNU_ThrowByName(env, JNU_JAVANETPKG "UnknownHostException", 0);
        return NULL;
    }
    return JNU_NewStringPlatform(env, hp->h_name);
}


/**
 * ping implementation.
 * Send a ICMP_ECHO_REQUEST packet every second until either the timeout
 * expires or a answer is received.
 * Returns true is an ECHO_REPLY is received, otherwise, false.
 */
static jboolean
ping4(JNIEnv *env,
      unsigned long src_addr,
      unsigned long dest_addr,
      jint timeout)
{
    // See https://msdn.microsoft.com/en-us/library/aa366050%28VS.85%29.aspx

    HANDLE hIcmpFile;
    DWORD dwRetVal = 0;
    char SendData[32] = {0};
    LPVOID ReplyBuffer = NULL;
    DWORD ReplySize = 0;

    hIcmpFile = IcmpCreateFile();
    if (hIcmpFile == INVALID_HANDLE_VALUE) {
        NET_ThrowNew(env, WSAGetLastError(), "Unable to open handle");
        return JNI_FALSE;
    }

    ReplySize = sizeof(ICMP_ECHO_REPLY) + sizeof(SendData);
    ReplyBuffer = (VOID*) malloc(ReplySize);
    if (ReplyBuffer == NULL) {
        IcmpCloseHandle(hIcmpFile);
        NET_ThrowNew(env, WSAGetLastError(), "Unable to allocate memory");
        return JNI_FALSE;
    }

    if (src_addr == 0) {
        dwRetVal = IcmpSendEcho(hIcmpFile,  // HANDLE IcmpHandle,
                                dest_addr,  // IPAddr DestinationAddress,
                                SendData,   // LPVOID RequestData,
                                sizeof(SendData),   // WORD RequestSize,
                                NULL,       // PIP_OPTION_INFORMATION RequestOptions,
                                ReplyBuffer,// LPVOID ReplyBuffer,
                                ReplySize,  // DWORD ReplySize,
                                timeout);   // DWORD Timeout
    } else {
        dwRetVal = IcmpSendEcho2Ex(hIcmpFile,  // HANDLE IcmpHandle,
                                   NULL,       // HANDLE Event
                                   NULL,       // PIO_APC_ROUTINE ApcRoutine
                                   NULL,       // ApcContext
                                   src_addr,   // IPAddr SourceAddress,
                                   dest_addr,  // IPAddr DestinationAddress,
                                   SendData,   // LPVOID RequestData,
                                   sizeof(SendData),   // WORD RequestSize,
                                   NULL,       // PIP_OPTION_INFORMATION RequestOptions,
                                   ReplyBuffer,// LPVOID ReplyBuffer,
                                   ReplySize,  // DWORD ReplySize,
                                   timeout);   // DWORD Timeout
    }

    free(ReplyBuffer);
    IcmpCloseHandle(hIcmpFile);

    if (dwRetVal != 0) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
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
    jint src_addr = 0;
    jint dest_addr = 0;
    jbyte caddr[4];
    int sz;

    /**
     * Convert IP address from byte array to integer
     */
    sz = (*env)->GetArrayLength(env, addrArray);
    if (sz != 4) {
      return JNI_FALSE;
    }
    memset((char *) caddr, 0, sizeof(caddr));
    (*env)->GetByteArrayRegion(env, addrArray, 0, 4, caddr);
    dest_addr = ((caddr[0]<<24) & 0xff000000);
    dest_addr |= ((caddr[1] <<16) & 0xff0000);
    dest_addr |= ((caddr[2] <<8) & 0xff00);
    dest_addr |= (caddr[3] & 0xff);
    dest_addr = htonl(dest_addr);

    /**
     * If a network interface was specified, let's convert its address
     * as well.
     */
    if (!(IS_NULL(ifArray))) {
        memset((char *) caddr, 0, sizeof(caddr));
        (*env)->GetByteArrayRegion(env, ifArray, 0, 4, caddr);
        src_addr = ((caddr[0]<<24) & 0xff000000);
        src_addr |= ((caddr[1] <<16) & 0xff0000);
        src_addr |= ((caddr[2] <<8) & 0xff00);
        src_addr |= (caddr[3] & 0xff);
        src_addr = htonl(src_addr);
    }

    return ping4(env, src_addr, dest_addr, timeout);
}

