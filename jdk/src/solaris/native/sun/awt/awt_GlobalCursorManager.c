/*
 * Copyright 1999-2001 Sun Microsystems, Inc.  All Rights Reserved.
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
#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#include "awt_p.h"
#include "awt_Component.h"
#include "sun_awt_motif_MComponentPeer.h"

#include "jni.h"
#include "jni_util.h"

static jfieldID xID;
static jfieldID yID;

extern struct MComponentPeerIDs mComponentPeerIDs;
extern struct ComponentIDs componentIDs;
extern struct ContainerIDs containerIDs;
extern jobject getCurComponent();

/*
 * Class:     sun_awt_motif_MGlobalCursorManager
 * Method:    cacheInit
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MGlobalCursorManager_cacheInit
  (JNIEnv *env, jclass cls)
{
    jclass clsDimension = (*env)->FindClass(env, "java/awt/Point");
    xID = (*env)->GetFieldID(env, clsDimension, "x", "I");
    yID = (*env)->GetFieldID(env, clsDimension, "y", "I");
}

/*
 * Class:     sun_awt_motif_MGlobalCursorManager
 * Method:    getCursorPos
 * Signature: (Ljava/awt/Point;)Ljava/awt/Component
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MGlobalCursorManager_getCursorPos
  (JNIEnv *env, jobject this, jobject point)
{
    Window root, rw, cw;
    int32_t rx, ry, x, y;
    uint32_t kbs;

    AWT_LOCK();
    root = RootWindow(awt_display, DefaultScreen(awt_display));
    XQueryPointer(awt_display, root, &rw, &cw, &rx, &ry, &x, &y, &kbs);

    (*env)->SetIntField(env, point, xID, rx);
    (*env)->SetIntField(env, point, yID, ry);
    AWT_FLUSH_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MGlobalCursorManager
 * Method:    getCursorPos
 * Signature: ()Ljava/awt/Component
 */
JNIEXPORT jobject JNICALL Java_sun_awt_motif_MGlobalCursorManager_findHeavyweightUnderCursor
  (JNIEnv *env, jobject this)
{
        jobject target;

    AWT_LOCK();
        target = getCurComponent();
    AWT_FLUSH_UNLOCK();
        return target;
}

/*
 * Class:     sun_awt_motif_MGlobalCursorManager
 * Method:    getLocationOnScreen
 * Signature: (Ljava/awt/Component;)Ljava/awt/Point
 */
JNIEXPORT jobject JNICALL Java_sun_awt_motif_MGlobalCursorManager_getLocationOnScreen
  (JNIEnv *env, jobject this, jobject component)
{
    jobject point =
        (*env)->CallObjectMethod(env, component,
                                 componentIDs.getLocationOnScreen);
    return point;
}

/*
 * Class:     sun_awt_motif_MGlobalCursorManager
 * Method:    findComponentAt
 * Signature: (Ljava/awt/Container;II)Ljava/awt/Component
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_motif_MGlobalCursorManager_findComponentAt
    (JNIEnv *env, jobject this, jobject container, jint x, jint y)
{
    /*
     * Call private version of Container.findComponentAt with the following
     * flag set: ignoreEnabled = false (i.e., don't return or recurse into
     * disabled Components).
     * NOTE: it may return a JRootPane's glass pane as the target Component.
     */
    jobject component =
        (*env)->CallObjectMethod(env, container, containerIDs.findComponentAt,
                                 x, y, JNI_FALSE);
    return component;
}
