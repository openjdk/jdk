/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

#include <dlfcn.h>
#include <string.h>

#define MAGIC_MIME_TYPE 0x000010 /* Return the MIME type */

typedef struct magic_set magic_t;

typedef magic_t* (*magic_open_func)(int flags);
typedef int (*magic_load_func)(magic_t* cookie, const char* filename);
typedef const char* (*magic_file_func)(magic_t* cookie, const char* filename);
typedef void (*magic_close_func)(magic_t* cookie);

static void* magic_handle;
static magic_open_func magic_open;
static magic_load_func magic_load;
static magic_file_func magic_file;
static magic_close_func magic_close;

#include "sun_nio_fs_MagicFileTypeDetector.h"

JNIEXPORT jboolean JNICALL
Java_sun_nio_fs_MagicFileTypeDetector_initialize0
    (JNIEnv* env, jclass this)
{
    magic_handle = dlopen("libmagic.so", RTLD_LAZY);
    if (magic_handle == NULL) {
        magic_handle = dlopen("libmagic.so.1", RTLD_LAZY);
        if (magic_handle == NULL) {
            return JNI_FALSE;
        }
    }

    magic_open = (magic_open_func)dlsym(magic_handle, "magic_open");

    magic_load = (magic_load_func)dlsym(magic_handle, "magic_load");

    magic_file = (magic_file_func)dlsym(magic_handle, "magic_file");

    magic_close = (magic_close_func)dlsym(magic_handle, "magic_close");

    if (magic_open == NULL ||
        magic_load == NULL ||
        magic_file == NULL ||
        magic_close == NULL)
    {
        dlclose(magic_handle);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_fs_MagicFileTypeDetector_probe0
    (JNIEnv* env, jclass this, jlong pathAddress)
{
    char* path = (char*)jlong_to_ptr(pathAddress);
    magic_t* cookie;
    jbyteArray result = NULL;

    cookie = (*magic_open)(MAGIC_MIME_TYPE);

    if (cookie != NULL) {
        if ((*magic_load)(cookie, NULL) != -1) {
            const char* type = (*magic_file)(cookie, path);
            if (type != NULL) {
                jsize len = strlen(type);
                result = (*env)->NewByteArray(env, len);
                if (result != NULL) {
                    (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)type);
                }
            }
        }
        (*magic_close)(cookie);
    }

    return result;
}
