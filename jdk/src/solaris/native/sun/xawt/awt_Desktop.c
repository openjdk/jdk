/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include <dlfcn.h>

typedef int gboolean;

typedef gboolean (GNOME_URL_SHOW_TYPE)(const char *, void **);
typedef gboolean (GNOME_VFS_INIT_TYPE)(void);

GNOME_URL_SHOW_TYPE *gnome_url_show;
GNOME_VFS_INIT_TYPE *gnome_vfs_init;

int init(){
    void *vfs_handle;
    void *gnome_handle;
    const char *errmsg;

    vfs_handle = dlopen("libgnomevfs-2.so.0", RTLD_LAZY);
    if (vfs_handle == NULL) {
#ifdef INTERNAL_BUILD
        fprintf(stderr, "can not load libgnomevfs-2.so\n");
#endif
        return 0;
    }
    dlerror(); /* Clear errors */
    gnome_vfs_init = (GNOME_VFS_INIT_TYPE*)dlsym(vfs_handle, "gnome_vfs_init");
    if ((errmsg = dlerror()) != NULL) {
#ifdef INTERNAL_BUILD
        fprintf(stderr, "can not find symble gnome_vfs_init\n");
#endif
        return 0;
    }
    // call gonme_vfs_init()
    (*gnome_vfs_init)();

    gnome_handle = dlopen("libgnome-2.so.0", RTLD_LAZY);
    if (gnome_handle == NULL) {
#ifdef INTERNAL_BUILD
        fprintf(stderr, "can not load libgnome-2.so\n");
#endif
        return 0;
    }
    dlerror(); /* Clear errors */
    gnome_url_show = (GNOME_URL_SHOW_TYPE*)dlsym(gnome_handle, "gnome_url_show");
    if ((errmsg = dlerror()) != NULL) {
#ifdef INTERNAL_BUILD
        fprintf(stderr, "can not find symble gnome_url_show\n");
#endif
        return 0;
    }

    return 1;
}

/*
 * Class:     sun_awt_X11_XDesktopPeer
 * Method:    init
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_X11_XDesktopPeer_init
  (JNIEnv *env, jclass cls)
{
    int init_ok = init();
    return init_ok ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     sun_awt_X11_XDesktopPeer
 * Method:    gnome_url_show
 * Signature: (Ljava/lang/[B;)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_X11_XDesktopPeer_gnome_1url_1show
  (JNIEnv *env, jobject obj, jbyteArray url_j)
{
    gboolean success;
    const char* url_c;

    if (gnome_url_show == NULL) {
        return JNI_FALSE;
    }

    url_c = (char*)(*env)->GetByteArrayElements(env, url_j, NULL);
    // call gnome_url_show(const char* , GError**)
    success = (*gnome_url_show)(url_c, NULL);
    (*env)->ReleaseByteArrayElements(env, url_j, (signed char*)url_c, 0);

    return success ? JNI_TRUE : JNI_FALSE;
}
