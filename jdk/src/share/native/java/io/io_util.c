/*
 * Copyright 1994-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

#include <stdlib.h>
#include <string.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "io_util.h"
#include "io_util_md.h"

/* IO helper functions */

int
readSingle(JNIEnv *env, jobject this, jfieldID fid) {
    int nread;
    char ret;
    FD fd = GET_FD(this, fid);
    if (fd == -1) {
        JNU_ThrowIOException(env, "Stream Closed");
        return -1;
    }
    nread = IO_Read(fd, &ret, 1);
    if (nread == 0) { /* EOF */
        return -1;
    } else if (nread == JVM_IO_ERR) { /* error */
        JNU_ThrowIOExceptionWithLastError(env, "Read error");
    } else if (nread == JVM_IO_INTR) {
        JNU_ThrowByName(env, "java/io/InterruptedIOException", 0);
    }
    return ret & 0xFF;
}

/* The maximum size of a stack-allocated buffer.
 */
#define BUF_SIZE 8192


int
readBytes(JNIEnv *env, jobject this, jbyteArray bytes,
          jint off, jint len, jfieldID fid)
{
    int nread, datalen;
    char stackBuf[BUF_SIZE];
    char *buf = 0;
    FD fd;

    if (IS_NULL(bytes)) {
        JNU_ThrowNullPointerException(env, 0);
        return -1;
    }
    datalen = (*env)->GetArrayLength(env, bytes);

    if ((off < 0) || (off > datalen) ||
        (len < 0) || ((off + len) > datalen) || ((off + len) < 0)) {
        JNU_ThrowByName(env, "java/lang/IndexOutOfBoundsException", 0);
        return -1;
    }

    if (len == 0) {
        return 0;
    } else if (len > BUF_SIZE) {
        buf = malloc(len);
        if (buf == 0) {
            JNU_ThrowOutOfMemoryError(env, 0);
            return 0;
        }
    } else {
        buf = stackBuf;
    }

    fd = GET_FD(this, fid);
    if (fd == -1) {
        JNU_ThrowIOException(env, "Stream Closed");
        return  -1;
    }

    nread = IO_Read(fd, buf, len);
    if (nread > 0) {
        (*env)->SetByteArrayRegion(env, bytes, off, nread, (jbyte *)buf);
    } else if (nread == JVM_IO_ERR) {
        JNU_ThrowIOExceptionWithLastError(env, "Read error");
    } else if (nread == JVM_IO_INTR) { /* EOF */
        JNU_ThrowByName(env, "java/io/InterruptedIOException", 0);
    } else { /* EOF */
        nread = -1;
    }

    if (buf != stackBuf) {
        free(buf);
    }
    return nread;
}

void
writeSingle(JNIEnv *env, jobject this, jint byte, jfieldID fid) {
    char c = byte;
    int n;
    FD fd = GET_FD(this, fid);
    if (fd == -1) {
        JNU_ThrowIOException(env, "Stream Closed");
        return;
    }
    n = IO_Write(fd, &c, 1);
    if (n == JVM_IO_ERR) {
        JNU_ThrowIOExceptionWithLastError(env, "Write error");
    } else if (n == JVM_IO_INTR) {
        JNU_ThrowByName(env, "java/io/InterruptedIOException", 0);
    }
}

void
writeBytes(JNIEnv *env, jobject this, jbyteArray bytes,
          jint off, jint len, jfieldID fid)
{
    int n, datalen;
    char stackBuf[BUF_SIZE];
    char *buf = 0;
    FD fd;

    if (IS_NULL(bytes)) {
        JNU_ThrowNullPointerException(env, 0);
        return;
    }
    datalen = (*env)->GetArrayLength(env, bytes);

    if ((off < 0) || (off > datalen) ||
        (len < 0) || ((off + len) > datalen) || ((off + len) < 0)) {
        JNU_ThrowByName(env, "java/lang/IndexOutOfBoundsException", 0);
        return;
    }

    if (len == 0) {
        return;
    } else if (len > BUF_SIZE) {
        buf = malloc(len);
        if (buf == 0) {
            JNU_ThrowOutOfMemoryError(env, 0);
            return;
        }
    } else {
        buf = stackBuf;
    }

    (*env)->GetByteArrayRegion(env, bytes, off, len, (jbyte *)buf);

    if (!(*env)->ExceptionOccurred(env)) {
        off = 0;
        while (len > 0) {
            fd = GET_FD(this, fid);
            if (fd == -1) {
                JNU_ThrowIOException(env, "Stream Closed");
                break;
            }
            n = IO_Write(fd, buf+off, len);
            if (n == JVM_IO_ERR) {
                JNU_ThrowIOExceptionWithLastError(env, "Write error");
                break;
            } else if (n == JVM_IO_INTR) {
                JNU_ThrowByName(env, "java/io/InterruptedIOException", 0);
                break;
            }
            off += n;
            len -= n;
        }
    }
    if (buf != stackBuf) {
        free(buf);
    }
}

void
throwFileNotFoundException(JNIEnv *env, jstring path)
{
    char buf[256];
    int n;
    jobject x;
    jstring why = NULL;

    n = JVM_GetLastErrorString(buf, sizeof(buf));
    if (n > 0) {
    why = JNU_NewStringPlatform(env, buf);
    }
    x = JNU_NewObjectByName(env,
                "java/io/FileNotFoundException",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                path, why);
    if (x != NULL) {
    (*env)->Throw(env, x);
    }
}
