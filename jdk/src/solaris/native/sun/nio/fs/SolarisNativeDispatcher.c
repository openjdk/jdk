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

#include <strings.h>
#include <errno.h>
#include <sys/acl.h>
#include <sys/mnttab.h>
#include <sys/mkdev.h>

#include "jni.h"

#include "sun_nio_fs_SolarisNativeDispatcher.h"

static jfieldID entry_name;
static jfieldID entry_dir;
static jfieldID entry_fstype;
static jfieldID entry_options;
static jfieldID entry_dev;

static void throwUnixException(JNIEnv* env, int errnum) {
    jobject x = JNU_NewObjectByName(env, "sun/nio/fs/UnixException",
        "(I)V", errnum);
    if (x != NULL) {
        (*env)->Throw(env, x);
    }
}

JNIEXPORT void JNICALL
Java_sun_nio_fs_SolarisNativeDispatcher_init(JNIEnv *env, jclass clazz) {
    clazz = (*env)->FindClass(env, "sun/nio/fs/UnixMountEntry");
    if (clazz == NULL)
        return;

    entry_name = (*env)->GetFieldID(env, clazz, "name", "[B");
    entry_dir = (*env)->GetFieldID(env, clazz, "dir", "[B");
    entry_fstype = (*env)->GetFieldID(env, clazz, "fstype", "[B");
    entry_options = (*env)->GetFieldID(env, clazz, "opts", "[B");
    entry_dev = (*env)->GetFieldID(env, clazz, "dev", "J");
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_SolarisNativeDispatcher_facl(JNIEnv* env, jclass this, jint fd,
    jint cmd, jint nentries, jlong address)
{
    void* aclbufp = jlong_to_ptr(address);
    int n = -1;

    n = facl((int)fd, (int)cmd, (int)nentries, aclbufp);
    if (n == -1) {
        throwUnixException(env, errno);
    }
    return (jint)n;
}

JNIEXPORT jint JNICALL
Java_sun_nio_fs_SolarisNativeDispatcher_getextmntent(JNIEnv* env, jclass this,
    jlong value, jobject entry)
{
    struct extmnttab ent;
    FILE* fp = jlong_to_ptr(value);
    jsize len;
    jbyteArray bytes;
    char* name;
    char* dir;
    char* fstype;
    char* options;
    dev_t dev;

    if (getextmntent(fp, &ent, 0))
        return -1;
    name = ent.mnt_special;
    dir = ent.mnt_mountp;
    fstype = ent.mnt_fstype;
    options = ent.mnt_mntopts;
    dev = makedev(ent.mnt_major, ent.mnt_minor);
    if (dev == NODEV) {
        throwUnixException(env, errno);
        return -1;
    }

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

    if (dev != 0)
        (*env)->SetLongField(env, entry, entry_dev, (jlong)dev);

    return 0;
}
