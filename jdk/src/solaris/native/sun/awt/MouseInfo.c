/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#include "awt_p.h"
#include "awt_Component.h"

#include <jni.h>
#include <jni_util.h>

extern int awt_numScreens;
extern AwtScreenDataPtr x11Screens;
extern struct ComponentIDs componentIDs;
extern struct MComponentPeerIDs mComponentPeerIDs;

/*
 * Class:     sun_awt_DefaultMouseInfoPeer
 * Method:    fillPointWithCoords
 * Signature: (Ljava/awt/Point)I
 */
JNIEXPORT jint JNICALL
Java_sun_awt_DefaultMouseInfoPeer_fillPointWithCoords(JNIEnv *env, jclass cls,
                                                          jobject point)
{
     static jclass pointClass = NULL;
     jclass pointClassLocal;
     static jfieldID xID, yID;
     Window rootWindow, childWindow;
     int i;
     int32_t xr, yr, xw, yw;
     uint32_t keys;
     Bool pointerFound;

     AWT_LOCK();
     if (pointClass == NULL) {
         pointClassLocal = (*env)->FindClass(env, "java/awt/Point");
         DASSERT(pointClassLocal != NULL);
         if (pointClassLocal == NULL) {
             AWT_UNLOCK();
             return (jint)0;
         }
         pointClass = (jclass)(*env)->NewGlobalRef(env, pointClassLocal);
         (*env)->DeleteLocalRef(env, pointClassLocal);
         xID = (*env)->GetFieldID(env, pointClass, "x", "I");
         yID = (*env)->GetFieldID(env, pointClass, "y", "I");
     }

     for (i = 0; i < awt_numScreens; i++) {
         pointerFound = XQueryPointer(awt_display, x11Screens[i].root,
                           &rootWindow, &childWindow,
                           &xr, &yr, &xw, &yw, &keys);
         if (pointerFound) {
             (*env)->SetIntField(env, point, xID, xr);
             (*env)->SetIntField(env, point, yID, yr);
             AWT_UNLOCK();
             return (jint)i;
         }
     }
     /* This should never happen */
     DASSERT(FALSE);
     AWT_UNLOCK();
     return (jint)0;
}

/*
 * Class:     sun_awt_DefaultMouseInfoPeer
 * Method:    isWindowUnderMouse
 * Signature: (Ljava/awt/Window)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_awt_DefaultMouseInfoPeer_isWindowUnderMouse
  (JNIEnv * env, jclass cls, jobject window)
{
    Window rootWindow = None, parentWindow = None, siblingWindow = None;
    Window * children = NULL;
    int i = 0;
    int is_the_same_screen = 0;
    int32_t xr = 0, yr = 0, xw = 0, yw = 0;
    uint32_t keys = 0;
    uint32_t nchildren = 0;
    Bool pointerFound = 0;
    struct FrameData *wdata = NULL;
    jobject winPeer = NULL;

    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        return JNI_FALSE;
    }
    winPeer = (*env)->GetObjectField(env, window, componentIDs.peer);
    if (JNU_IsNull(env, winPeer)) {
        return JNI_FALSE;
    }

    wdata = (struct FrameData *)
        JNU_GetLongFieldAsPtr(env, winPeer, mComponentPeerIDs.pData);
    (*env)->DeleteLocalRef(env, winPeer);

    if (wdata == NULL) {
        return JNI_FALSE;
    }

    AWT_LOCK();

    XQueryTree(awt_display, XtWindow(wdata->winData.comp.widget),
                    &rootWindow, &parentWindow, &children, &nchildren);

    is_the_same_screen = XQueryPointer(awt_display, parentWindow,
            &rootWindow, &siblingWindow, &xr, &yr, &xw, &yw, &keys);

    if (siblingWindow == XtWindow(wdata->winData.comp.widget) && is_the_same_screen) {
        AWT_UNLOCK();
        return JNI_TRUE;
    }

    AWT_UNLOCK();
    return JNI_FALSE ;

}
