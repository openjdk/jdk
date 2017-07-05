/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>

#include "jvm.h"
#include "jni_util.h"
#include "net_util.h"

#include "java_net_SocketInputStream.h"


/************************************************************************
 * SocketInputStream
 */

static jfieldID IO_fd_fdID;

/*
 * Class:     java_net_SocketInputStream
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_net_SocketInputStream_init(JNIEnv *env, jclass cls) {
    IO_fd_fdID = NET_GetFileDescriptorID(env);
}

/*
 * Class:     java_net_SocketInputStream
 * Method:    socketRead0
 * Signature: (Ljava/io/FileDescriptor;[BIII)I
 */
JNIEXPORT jint JNICALL
Java_java_net_SocketInputStream_socketRead0(JNIEnv *env, jobject this,
                                            jobject fdObj, jbyteArray data,
                                            jint off, jint len, jint timeout)
{
    char BUF[MAX_BUFFER_LEN];
    char *bufP;
    jint fd, nread;

    if (IS_NULL(fdObj)) {
        /* shouldn't this be a NullPointerException? -br */
        JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
        return -1;
    } else {
        fd = (*env)->GetIntField(env, fdObj, IO_fd_fdID);
        /* Bug 4086704 - If the Socket associated with this file descriptor
         * was closed (sysCloseFD), then the file descriptor is set to -1.
         */
        if (fd == -1) {
            JNU_ThrowByName(env, "java/net/SocketException", "Socket closed");
            return -1;
        }
    }

    /*
     * If the read is greater than our stack allocated buffer then
     * we allocate from the heap (up to a limit)
     */
    if (len > MAX_BUFFER_LEN) {
        if (len > MAX_HEAP_BUFFER_LEN) {
            len = MAX_HEAP_BUFFER_LEN;
        }
        bufP = (char *)malloc((size_t)len);
        if (bufP == NULL) {
            bufP = BUF;
            len = MAX_BUFFER_LEN;
        }
    } else {
        bufP = BUF;
    }

    if (timeout) {
        nread = NET_Timeout(fd, timeout);
        if (nread <= 0) {
            if (nread == 0) {
                JNU_ThrowByName(env, JNU_JAVANETPKG "SocketTimeoutException",
                            "Read timed out");
            } else if (nread == -1) {
                if (errno == EBADF) {
                     JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException", "Socket closed");
                } else if (errno == ENOMEM) {
                    JNU_ThrowOutOfMemoryError(env, "NET_Timeout native heap allocation failed");
                } else {
                    NET_ThrowByNameWithLastError(env, JNU_JAVANETPKG "SocketException",
                                                  "select/poll failed");
                }
            }
            if (bufP != BUF) {
                free(bufP);
            }
            return -1;
        }
    }

    nread = NET_Read(fd, bufP, len);

    if (nread <= 0) {
        if (nread < 0) {

            switch (errno) {
                case ECONNRESET:
                case EPIPE:
                    JNU_ThrowByName(env, "sun/net/ConnectionResetException",
                        "Connection reset");
                    break;

                case EBADF:
                    JNU_ThrowByName(env, JNU_JAVANETPKG "SocketException",
                        "Socket closed");
                    break;

                case EINTR:
                     JNU_ThrowByName(env, JNU_JAVAIOPKG "InterruptedIOException",
                           "Operation interrupted");
                     break;

                default:
                    NET_ThrowByNameWithLastError(env,
                        JNU_JAVANETPKG "SocketException", "Read failed");
            }
        }
    } else {
        (*env)->SetByteArrayRegion(env, data, off, nread, (jbyte *)bufP);
    }

    if (bufP != BUF) {
        free(bufP);
    }
    return nread;
}
