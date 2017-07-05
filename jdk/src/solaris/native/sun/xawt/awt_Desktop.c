/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "jni_util.h"
#include "gtk2_interface.h"
#include "gnome_interface.h"

static gboolean gtk_has_been_loaded = FALSE;
static gboolean gnome_has_been_loaded = FALSE;

/*
 * Class:     sun_awt_X11_XDesktopPeer
 * Method:    init
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_X11_XDesktopPeer_init
  (JNIEnv *env, jclass cls)
{

    if (gtk_has_been_loaded || gnome_has_been_loaded) {
        return JNI_TRUE;
    }

    if (gtk2_load(env) && gtk2_show_uri_load(env)) {
        gtk_has_been_loaded = TRUE;
        return JNI_TRUE;
    } else if (gnome_load()) {
        gnome_has_been_loaded = TRUE;
        return JNI_TRUE;
    }

    return JNI_FALSE;
}

/*
 * Class:     sun_awt_X11_XDesktopPeer
 * Method:    gnome_url_show
 * Signature: (Ljava/lang/[B;)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_X11_XDesktopPeer_gnome_1url_1show
  (JNIEnv *env, jobject obj, jbyteArray url_j)
{
    gboolean success = FALSE;
    const gchar* url_c;

    url_c = (char*)(*env)->GetByteArrayElements(env, url_j, NULL);
    if (url_c == NULL) {
        if (!(*env)->ExceptionCheck(env)) {
            JNU_ThrowOutOfMemoryError(env, 0);
        }
        return JNI_FALSE;
    }

    if (gtk_has_been_loaded) {
        fp_gdk_threads_enter();
        success = fp_gtk_show_uri(NULL, url_c, GDK_CURRENT_TIME, NULL);
        fp_gdk_threads_leave();
    } else if (gnome_has_been_loaded) {
        success = (*gnome_url_show)(url_c, NULL);
    }

    (*env)->ReleaseByteArrayElements(env, url_j, (signed char*)url_c, 0);

    return success ? JNI_TRUE : JNI_FALSE;
}
