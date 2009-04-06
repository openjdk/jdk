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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"
#include <sys/mman.h>
#include <sys/stat.h>
#include "sun_nio_ch_FileChannelImpl.h"
#include "java_lang_Integer.h"
#include "nio.h"
#include "nio_util.h"
#include <dlfcn.h>

static jfieldID chan_fd;        /* jobject 'fd' in sun.io.FileChannelImpl */

#ifdef __solaris__
typedef struct sendfilevec64 {
    int     sfv_fd;         /* input fd */
    uint_t  sfv_flag;       /* Flags. see below */
    off64_t sfv_off;        /* offset to start reading from */
    size_t  sfv_len;        /* amount of data */
} sendfilevec_t;

/* Function pointer for sendfilev on Solaris 8+ */
typedef ssize_t sendfile_func(int fildes, const struct sendfilevec64 *vec,
                              int sfvcnt, size_t *xferred);

sendfile_func* my_sendfile_func = NULL;
#endif

#ifdef __linux__
#include <sys/sendfile.h>

/* Function pointer for sendfile64 on Linux 2.6 (and newer 2.4 kernels) */
typedef ssize_t sendfile64_func(int out_fd, int in_fd, off64_t *offset, size_t count);

sendfile64_func* my_sendfile64_func = NULL;
#endif

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_initIDs(JNIEnv *env, jclass clazz)
{
    jlong pageSize = sysconf(_SC_PAGESIZE);
    chan_fd = (*env)->GetFieldID(env, clazz, "fd", "Ljava/io/FileDescriptor;");

#ifdef __solaris__
    if (dlopen("/usr/lib/libsendfile.so.1", RTLD_GLOBAL | RTLD_LAZY) != NULL) {
        my_sendfile_func = (sendfile_func*) dlsym(RTLD_DEFAULT, "sendfilev64");
    }
#endif

#ifdef __linux__
    my_sendfile64_func = (sendfile64_func*) dlsym(RTLD_DEFAULT, "sendfile64");
#endif

    return pageSize;
}

static jlong
handle(JNIEnv *env, jlong rv, char *msg)
{
    if (rv >= 0)
        return rv;
    if (errno == EINTR)
        return IOS_INTERRUPTED;
    JNU_ThrowIOExceptionWithLastError(env, msg);
    return IOS_THROWN;
}


JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_map0(JNIEnv *env, jobject this,
                                     jint prot, jlong off, jlong len)
{
    void *mapAddress = 0;
    jobject fdo = (*env)->GetObjectField(env, this, chan_fd);
    jint fd = fdval(env, fdo);
    int protections = 0;
    int flags = 0;

    if (prot == sun_nio_ch_FileChannelImpl_MAP_RO) {
        protections = PROT_READ;
        flags = MAP_SHARED;
    } else if (prot == sun_nio_ch_FileChannelImpl_MAP_RW) {
        protections = PROT_WRITE | PROT_READ;
        flags = MAP_SHARED;
    } else if (prot == sun_nio_ch_FileChannelImpl_MAP_PV) {
        protections =  PROT_WRITE | PROT_READ;
        flags = MAP_PRIVATE;
    }

    mapAddress = mmap64(
        0,                    /* Let OS decide location */
        len,                  /* Number of bytes to map */
        protections,          /* File permissions */
        flags,                /* Changes are shared */
        fd,                   /* File descriptor of mapped file */
        off);                 /* Offset into file */

    if (mapAddress == MAP_FAILED) {
        if (errno == ENOMEM) {
            JNU_ThrowOutOfMemoryError(env, "Map failed");
            return IOS_THROWN;
        }
        return handle(env, -1, "Map failed");
    }

    return ((jlong) (unsigned long) mapAddress);
}


JNIEXPORT jint JNICALL
Java_sun_nio_ch_FileChannelImpl_unmap0(JNIEnv *env, jobject this,
                                       jlong address, jlong len)
{
    void *a = (void *)jlong_to_ptr(address);
    return handle(env,
                  munmap(a, (size_t)len),
                  "Unmap failed");
}


JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_position0(JNIEnv *env, jobject this,
                                          jobject fdo, jlong offset)
{
    jint fd = fdval(env, fdo);
    jlong result = 0;

    if (offset < 0) {
        result = lseek64(fd, 0, SEEK_CUR);
    } else {
        result = lseek64(fd, offset, SEEK_SET);
    }
    return handle(env, result, "Position failed");
}


JNIEXPORT void JNICALL
Java_sun_nio_ch_FileChannelImpl_close0(JNIEnv *env, jobject this, jobject fdo)
{
    jint fd = fdval(env, fdo);
    if (fd != -1) {
        jlong result = close(fd);
        if (result < 0) {
            JNU_ThrowIOExceptionWithLastError(env, "Close failed");
        }
    }
}

JNIEXPORT jlong JNICALL
Java_sun_nio_ch_FileChannelImpl_transferTo0(JNIEnv *env, jobject this,
                                            jint srcFD,
                                            jlong position, jlong count,
                                            jint dstFD)
{
#ifdef __linux__
    jlong max = (jlong)java_lang_Integer_MAX_VALUE;
    jlong n;

    if (my_sendfile64_func == NULL) {
        off_t offset;
        if (position > max)
            return IOS_UNSUPPORTED_CASE;
        if (count > max)
            count = max;
        offset = (off_t)position;
        n = sendfile(dstFD, srcFD, &offset, (size_t)count);
    } else {
        off64_t offset = (off64_t)position;
        n = (*my_sendfile64_func)(dstFD, srcFD, &offset, (size_t)count);
    }
    if (n < 0) {
        if (errno == EAGAIN)
            return IOS_UNAVAILABLE;
        if ((errno == EINVAL) && ((ssize_t)count >= 0))
            return IOS_UNSUPPORTED_CASE;
        if (errno == EINTR) {
            return IOS_INTERRUPTED;
        }
        JNU_ThrowIOExceptionWithLastError(env, "Transfer failed");
        return IOS_THROWN;
    }
    return n;
#endif

#ifdef __solaris__
    if (my_sendfile_func == NULL) {
        return IOS_UNSUPPORTED;
    } else {
        sendfilevec_t sfv;
        size_t numBytes = 0;
        jlong result;

        sfv.sfv_fd = srcFD;
        sfv.sfv_flag = 0;
        sfv.sfv_off = (off64_t)position;
        sfv.sfv_len = count;

        result = (*my_sendfile_func)(dstFD, &sfv, 1, &numBytes);

        /* Solaris sendfilev() will return -1 even if some bytes have been
         * transferred, so we check numBytes first.
         */
        if (numBytes > 0)
            return numBytes;
        if (result < 0) {
            if (errno == EAGAIN)
                return IOS_UNAVAILABLE;
            if (errno == EOPNOTSUPP)
                return IOS_UNSUPPORTED_CASE;
            if ((errno == EINVAL) && ((ssize_t)count >= 0))
                return IOS_UNSUPPORTED_CASE;
            if (errno == EINTR)
                return IOS_INTERRUPTED;
            JNU_ThrowIOExceptionWithLastError(env, "Transfer failed");
            return IOS_THROWN;
        }
        return result;
    }
#endif
}
