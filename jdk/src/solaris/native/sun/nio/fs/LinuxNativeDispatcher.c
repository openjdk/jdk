/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"

#include <stdio.h>
#include <string.h>
#include <dlfcn.h>
#include <errno.h>
#include <mntent.h>

#include "sun_nio_fs_LinuxNativeDispatcher.h"

typedef size_t fgetxattr_func(int fd, const char* name, void* value, size_t size);
typedef int fsetxattr_func(int fd, const char* name, void* value, size_t size, int flags);
typedef int fremovexattr_func(int fd, const char* name);
typedef int flistxattr_func(int fd, char* list, size_t size);

fgetxattr_func* my_fgetxattr_func = NULL;
fsetxattr_func* my_fsetxattr_func = NULL;
fremovexattr_func* my_fremovexattr_func = NULL;
flistxattr_func* my_flistxattr_func = NULL;

static jfieldID entry_name;
static jfieldID entry_dir;
static jfieldID entry_fstype;
static jfieldID entry_options;

static void throwUnixException(JNIEnv* env, int errnum) {
    jobject x = JNU_NewObjectByName(env, "sun/nio/fs/UnixException",
        "(I)V", errnum);
    if (x != NULL) {
        (*env)->Throw(env, x);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_init(JNIEnv *env, jclass clazz)
{
    my_fgetxattr_func = (fgetxattr_func*)dlsym(RTLD_DEFAULT, "fgetxattr");
    my_fsetxattr_func = (fsetxattr_func*)dlsym(RTLD_DEFAULT, "fsetxattr");
    my_fremovexattr_func = (fremovexattr_func*)dlsym(RTLD_DEFAULT, "fremovexattr");
    my_flistxattr_func = (flistxattr_func*)dlsym(RTLD_DEFAULT, "flistxattr");

    clazz = (*env)->FindClass(env, "sun/nio/fs/UnixMountEntry");
    if (clazz == NULL)
        return;

    entry_name = (*env)->GetFieldID(env, clazz, "name", "[B");
    entry_dir = (*env)->GetFieldID(env, clazz, "dir", "[B");
    entry_fstype = (*env)->GetFieldID(env, clazz, "fstype", "[B");
    entry_options = (*env)->GetFieldID(env, clazz, "opts", "[B");
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_fgetxattr0(JNIEnv* env, jclass clazz,
    jint fd, jlong nameAddress, jlong valueAddress, jint valueLen)
{
    size_t res = -1;
    const char* name = jlong_to_ptr(nameAddress);
    void* value = jlong_to_ptr(valueAddress);

    if (my_fgetxattr_func == NULL) {
        errno = ENOTSUP;
    } else {
        /* EINTR not documented */
        res = (*my_fgetxattr_func)(fd, name, value, valueLen);
    }
    if (res == (size_t)-1)
        throwUnixException(env, errno);
    return (jint)res;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_fsetxattr0(JNIEnv* env, jclass clazz,
    jint fd, jlong nameAddress, jlong valueAddress, jint valueLen)
{
    int res = -1;
    const char* name = jlong_to_ptr(nameAddress);
    void* value = jlong_to_ptr(valueAddress);

    if (my_fsetxattr_func == NULL) {
        errno = ENOTSUP;
    } else {
        /* EINTR not documented */
        res = (*my_fsetxattr_func)(fd, name, value, valueLen, 0);
    }
    if (res == -1)
        throwUnixException(env, errno);
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_fremovexattr0(JNIEnv* env, jclass clazz,
    jint fd, jlong nameAddress)
{
    int res = -1;
    const char* name = jlong_to_ptr(nameAddress);

    if (my_fremovexattr_func == NULL) {
        errno = ENOTSUP;
    } else {
        /* EINTR not documented */
        res = (*my_fremovexattr_func)(fd, name);
    }
    if (res == -1)
        throwUnixException(env, errno);
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_flistxattr(JNIEnv* env, jclass clazz,
    jint fd, jlong listAddress, jint size)
{
    size_t res = -1;
    char* list = jlong_to_ptr(listAddress);

    if (my_flistxattr_func == NULL) {
        errno = ENOTSUP;
    } else {
        /* EINTR not documented */
        res = (*my_flistxattr_func)(fd, list, (size_t)size);
    }
    if (res == (size_t)-1)
        throwUnixException(env, errno);
    return (jint)res;
}

JNIEXPORT jlong JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_setmntent0(JNIEnv* env, jclass this, jlong pathAddress,
                                                 jlong modeAddress)
{
    FILE* fp = NULL;
    const char* path = (const char*)jlong_to_ptr(pathAddress);
    const char* mode = (const char*)jlong_to_ptr(modeAddress);

    do {
        fp = setmntent(path, mode);
    } while (fp == NULL && errno == EINTR);
    if (fp == NULL) {
        throwUnixException(env, errno);
    }
    return ptr_to_jlong(fp);
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_getmntent(JNIEnv* env, jclass this,
    jlong value, jobject entry)
{
    struct mntent ent;
    char buf[1024];
    int buflen = sizeof(buf);
    struct mntent* m;
    FILE* fp = jlong_to_ptr(value);
    jsize len;
    jbyteArray bytes;
    char* name;
    char* dir;
    char* fstype;
    char* options;

    m = getmntent_r(fp, &ent, (char*)&buf, buflen);
    if (m == NULL)
        return -1;
    name = m->mnt_fsname;
    dir = m->mnt_dir;
    fstype = m->mnt_type;
    options = m->mnt_opts;

    len = strlen(name);
    bytes = (*env)->NewByteArray(env, len);
    if (bytes == NULL)
        return -1;
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)name);
    (*env)->SetObjectField(env, entry, entry_name, bytes);

    len = strlen(dir);
    bytes = (*env)->NewByteArray(env, len);
    if (bytes == NULL)
        return -1;
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)dir);
    (*env)->SetObjectField(env, entry, entry_dir, bytes);

    len = strlen(fstype);
    bytes = (*env)->NewByteArray(env, len);
    if (bytes == NULL)
        return -1;
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)fstype);
    (*env)->SetObjectField(env, entry, entry_fstype, bytes);

    len = strlen(options);
    bytes = (*env)->NewByteArray(env, len);
    if (bytes == NULL)
        return -1;
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte*)options);
    (*env)->SetObjectField(env, entry, entry_options, bytes);

    return 0;
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_LinuxNativeDispatcher_endmntent(JNIEnv* env, jclass this, jlong stream)
{
    FILE* fp = jlong_to_ptr(stream);
    /* FIXME - man page doesn't explain how errors are returned */
    endmntent(fp);
}
