/*
 * Copyright (c) 1994, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include <stddef.h>

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "io_util.h"
#include "io_util_md.h"

/* IO helper functions */

jint
readSingle(JNIEnv *env, jobject this, jfieldID fid) {
    jint nread;
    char ret;
    FD fd = getFD(env, this, fid);
    if (fd == -1) {
        JNU_ThrowIOException(env, "Stream Closed");
        return -1;
    }
    nread = IO_Read(fd, &ret, 1);
    if (nread == 0) { // EOF
        return -1;
    } else if (nread == -1) { // error
        JNU_ThrowIOExceptionWithLastError(env, "Read error");
    }
    return ret & 0xFF;
}

// The default size of a stack-allocated buffer.
#define BUF_SIZE 8192

// The maximum size of a dynamically allocated buffer.
#define MAX_MALLOC_SIZE 1048576

/*
 * Returns true if the array slice defined by the given offset and length
 * is out of bounds.
 */
static int
outOfBounds(JNIEnv *env, jint off, jint len, jbyteArray array) {
    return ((off < 0) ||
            (len < 0) ||
            // We are very careful to avoid signed integer overflow,
            // the result of which is undefined in C.
            ((*env)->GetArrayLength(env, array) - off < len));
}

jint
readBytes(JNIEnv *env, jobject this, jbyteArray bytes,
          jint off, jint len, jfieldID fid)
{
    jint n, nread, read_size;
    char stackBuf[BUF_SIZE];
    char *buf = NULL;
    jint remaining, buf_size;
    FD fd;

    if (IS_NULL(bytes)) {
        JNU_ThrowNullPointerException(env, NULL);
        return -1;
    }

    if (outOfBounds(env, off, len, bytes)) {
        JNU_ThrowByName(env, "java/lang/IndexOutOfBoundsException", NULL);
        return -1;
    }

    if (len == 0) {
        return 0;
    } else if (len > BUF_SIZE) {
        buf_size = len < MAX_MALLOC_SIZE ? len : MAX_MALLOC_SIZE;
        buf = malloc(buf_size);
        if (buf == NULL) {
            JNU_ThrowOutOfMemoryError(env, NULL);
            return 0;
        }
    } else {
        buf = stackBuf;
        buf_size = BUF_SIZE;
    }

    nread = 0;
    while (nread < len) {
        remaining = len - nread;
        read_size = remaining <= buf_size ? remaining : buf_size;
        fd = getFD(env, this, fid);
        if (fd == -1) {
            JNU_ThrowIOException(env, "Stream Closed");
            nread = -1;
            break;
        }
        n = IO_Read(fd, buf, read_size);
        if (n > 0) {
            (*env)->SetByteArrayRegion(env, bytes, off, n, (jbyte*)buf);
            nread += n;
            // Exit loop on short read
            if (n < read_size)
                break;
            off += n;
        } else if (n == -1) {
            JNU_ThrowIOExceptionWithLastError(env, "Read error");
            break;
        } else { // EOF
            if (nread == 0)
                nread = -1;
            break;
        }
    }

    if (buf != stackBuf) {
        free(buf);
    }
    return nread;
}

void
writeSingle(JNIEnv *env, jobject this, jint byte, jboolean append, jfieldID fid) {
    // Discard the 24 high-order bits of byte. See OutputStream#write(int)
    char c = (char) byte;
    jint n;
    FD fd = getFD(env, this, fid);
    if (fd == -1) {
        JNU_ThrowIOException(env, "Stream Closed");
        return;
    }
    if (append == JNI_TRUE) {
        n = IO_Append(fd, &c, 1);
    } else {
        n = IO_Write(fd, &c, 1);
    }
    if (n == -1) {
        JNU_ThrowIOExceptionWithLastError(env, "Write error");
    }
}

void
writeBytes(JNIEnv *env, jobject this, jbyteArray bytes,
           jint off, jint len, jboolean append, jfieldID fid)
{
    jint n, nwritten, write_size;
    char stackBuf[BUF_SIZE];
    char *buf = NULL;
    jint remaining, buf_size;
    FD fd;

    if (IS_NULL(bytes)) {
        JNU_ThrowNullPointerException(env, NULL);
        return;
    }

    if (outOfBounds(env, off, len, bytes)) {
        JNU_ThrowByName(env, "java/lang/IndexOutOfBoundsException", NULL);
        return;
    }

    if (len == 0) {
        return;
    } else if (len > BUF_SIZE) {
        buf_size = len < MAX_MALLOC_SIZE ? len : MAX_MALLOC_SIZE;
        buf = malloc(buf_size);
        if (buf == NULL) {
            JNU_ThrowOutOfMemoryError(env, NULL);
            return;
        }
    } else {
        buf = stackBuf;
        buf_size = BUF_SIZE;
    }

    nwritten = 0;
    while (nwritten < len) {
        remaining = len - nwritten;
        write_size = remaining <= buf_size ? remaining : buf_size;
        (*env)->GetByteArrayRegion(env, bytes, off, write_size, (jbyte*)buf);
        if (!(*env)->ExceptionOccurred(env)) {
            fd = getFD(env, this, fid);
            if (fd == -1) {
                JNU_ThrowIOException(env, "Stream Closed");
                break;
            }
            if (append == JNI_TRUE) {
                n = IO_Append(fd, buf, write_size);
            } else {
                n = IO_Write(fd, buf, write_size);
            }
            if (n > 0) {
                off += n;
                nwritten += n;
            } else if (n == -1) {
                JNU_ThrowIOExceptionWithLastError(env, "Write error");
                break;
            }
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
    size_t n;
    jobject x;
    jstring why = NULL;

    n = getLastErrorString(buf, sizeof(buf));
    if (n > 0) {
        why = JNU_NewStringPlatform(env, buf);
        CHECK_NULL(why);
    }
    x = JNU_NewObjectByName(env,
                            "java/io/FileNotFoundException",
                            "(Ljava/lang/String;Ljava/lang/String;)V",
                            path, why);
    if (x != NULL) {
        (*env)->Throw(env, x);
    }
}
