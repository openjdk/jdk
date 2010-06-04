/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <dlfcn.h>
#include <link.h>

#ifdef __solaris__
#include <strings.h>
#endif

#ifdef __linux__
#include <string.h>
#endif

/* Definitions for GIO */

#define G_FILE_ATTRIBUTE_STANDARD_CONTENT_TYPE "standard::content-type"

typedef void* gpointer;
typedef struct _GFile GFile;
typedef struct _GFileInfo GFileInfo;
typedef struct _GCancellable GCancellable;
typedef struct _GError GError;

typedef enum {
  G_FILE_QUERY_INFO_NONE = 0
} GFileQueryInfoFlags;

typedef void (*g_type_init_func)(void);
typedef void (*g_object_unref_func)(gpointer object);
typedef GFile* (*g_file_new_for_path_func)(const char* path);
typedef GFileInfo* (*g_file_query_info_func)(GFile *file,
    const char *attributes, GFileQueryInfoFlags flags,
    GCancellable *cancellable, GError **error);
typedef char* (*g_file_info_get_content_type_func)(GFileInfo *info);

static g_type_init_func g_type_init;
static g_object_unref_func g_object_unref;
static g_file_new_for_path_func g_file_new_for_path;
static g_file_query_info_func g_file_query_info;
static g_file_info_get_content_type_func g_file_info_get_content_type;


/* Definitions for GNOME VFS */

typedef int gboolean;

typedef gboolean (*gnome_vfs_init_function)(void);
typedef const char* (*gnome_vfs_mime_type_from_name_function)
    (const char* filename);

static gnome_vfs_init_function gnome_vfs_init;
static gnome_vfs_mime_type_from_name_function gnome_vfs_mime_type_from_name;


#include "sun_nio_fs_GnomeFileTypeDetector.h"


JNIEXPORT jboolean JNICALL
Java_sun_nio_fs_GnomeFileTypeDetector_initializeGio
    (JNIEnv* env, jclass this)
{
    void* gio_handle;

    gio_handle = dlopen("libgio-2.0.so", RTLD_LAZY);
    if (gio_handle == NULL) {
        gio_handle = dlopen("libgio-2.0.so.0", RTLD_LAZY);
        if (gio_handle == NULL) {
            return JNI_FALSE;
        }
    }

    g_type_init = (g_type_init_func)dlsym(gio_handle, "g_type_init");
    (*g_type_init)();

    g_object_unref = (g_object_unref_func)dlsym(gio_handle, "g_object_unref");

    g_file_new_for_path =
        (g_file_new_for_path_func)dlsym(gio_handle, "g_file_new_for_path");

    g_file_query_info =
        (g_file_query_info_func)dlsym(gio_handle, "g_file_query_info");

    g_file_info_get_content_type = (g_file_info_get_content_type_func)
        dlsym(gio_handle, "g_file_info_get_content_type");


    if (g_type_init == NULL ||
        g_object_unref == NULL ||
        g_file_new_for_path == NULL ||
        g_file_query_info == NULL ||
        g_file_info_get_content_type == NULL)
    {
        dlclose(gio_handle);
        return JNI_FALSE;
    }

    (*g_type_init)();
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_fs_GnomeFileTypeDetector_probeUsingGio
    (JNIEnv* env, jclass this, jlong pathAddress)
{
    char* path = (char*)jlong_to_ptr(pathAddress);
    GFile* gfile;
    GFileInfo* gfileinfo;
    jbyteArray result = NULL;

    gfile = (*g_file_new_for_path)(path);
    gfileinfo = (*g_file_query_info)(gfile, G_FILE_ATTRIBUTE_STANDARD_CONTENT_TYPE,
        G_FILE_QUERY_INFO_NONE, NULL, NULL);
    if (gfileinfo != NULL) {
        const char* mime = (*g_file_info_get_content_type)(gfileinfo);
        if (mime != NULL) {
            jsize len = strlen(mime);
            result = (*env)->NewByteArray(env, len);
            if (result != NULL) {
                (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)mime);
            }
        }
        (*g_object_unref)(gfileinfo);
    }
    (*g_object_unref)(gfile);

    return result;
}

JNIEXPORT jboolean JNICALL
Java_sun_nio_fs_GnomeFileTypeDetector_initializeGnomeVfs
    (JNIEnv* env, jclass this)
{
    void* vfs_handle;

    vfs_handle = dlopen("libgnomevfs-2.so", RTLD_LAZY);
    if (vfs_handle == NULL) {
        vfs_handle = dlopen("libgnomevfs-2.so.0", RTLD_LAZY);
    }
    if (vfs_handle == NULL) {
        return JNI_FALSE;
    }

    gnome_vfs_init = (gnome_vfs_init_function)dlsym(vfs_handle, "gnome_vfs_init");
    gnome_vfs_mime_type_from_name = (gnome_vfs_mime_type_from_name_function)
        dlsym(vfs_handle, "gnome_vfs_mime_type_from_name");

    if (gnome_vfs_init == NULL ||
        gnome_vfs_mime_type_from_name == NULL)
    {
        dlclose(vfs_handle);
        return JNI_FALSE;
    }

    (*gnome_vfs_init)();
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_sun_nio_fs_GnomeFileTypeDetector_probeUsingGnomeVfs
    (JNIEnv* env, jclass this, jlong pathAddress)
{
    char* path = (char*)jlong_to_ptr(pathAddress);
    const char* mime = (*gnome_vfs_mime_type_from_name)(path);

    if (mime == NULL) {
        return NULL;
    } else {
        jbyteArray result;
        jsize len = strlen(mime);
        result = (*env)->NewByteArray(env, len);
        if (result != NULL) {
            (*env)->SetByteArrayRegion(env, result, 0, len, (jbyte*)mime);
        }
        return result;
    }
}
