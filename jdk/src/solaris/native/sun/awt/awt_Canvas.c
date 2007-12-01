/*
 * Copyright 1995-2002 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "java_awt_Canvas.h"
#include "sun_awt_motif_MCanvasPeer.h"
#include "sun_awt_motif_MComponentPeer.h"
#include "color.h"
#include "canvas.h"
#include "awt_util.h"

#include "awt_Component.h"
#include "awt_GraphicsEnv.h"

#include <jni.h>
#include <jni_util.h>
#include "multi_font.h"

extern struct MComponentPeerIDs mComponentPeerIDs;
extern struct X11GraphicsConfigIDs x11GraphicsConfigIDs;
extern AwtGraphicsConfigDataPtr
    copyGraphicsConfigToPeer(JNIEnv *env, jobject this);
struct CanvasIDs mCanvasIDs;

/*
 * Class:     sun_awt_motif_MCanvasPeer
 * Method:    create
 * Signature: (Lsun/awt/motif/MComponentPeer;)V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MCanvasPeer_create
  (JNIEnv * env, jobject this, jobject parent)
{
    AwtGraphicsConfigDataPtr awtData;

    struct CanvasData *wdata;
    struct CanvasData *cdata;
    jobject globalRef = awtJNI_CreateAndSetGlobalRef(env, this);

    AWT_LOCK();
    if (JNU_IsNull(env, parent)) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    cdata = (struct CanvasData *)
        JNU_GetLongFieldAsPtr(env, parent, mComponentPeerIDs.pData);
    if (cdata == NULL) {
        JNU_ThrowNullPointerException(env, "NullPointerException");
        AWT_UNLOCK();
        return;
    }

    wdata = ZALLOC(CanvasData);
    if (wdata == NULL) {
        JNU_ThrowOutOfMemoryError(env, "OutOfMemoryError");
        AWT_UNLOCK();
        return;
    }
    JNU_SetLongFieldFromPtr(env, this, mComponentPeerIDs.pData, wdata);

    awtData = copyGraphicsConfigToPeer(env, this);

    wdata->comp.widget = awt_canvas_create((XtPointer) globalRef,
                                           cdata->comp.widget,
                                           "",
                                           1, 1, False, NULL, awtData);
    XtVaSetValues(wdata->comp.widget,
                  XmNinsertPosition, awt_util_insertCallback,
                  NULL);

    /* Add an event handler so that we can track focus change requests
       which will be initiated by Motif in response to ButtonPress events */

    wdata->flags = 0;
    wdata->shell = cdata->shell;

    AWT_UNLOCK();
}

/*
 * Class:     sun_awt_motif_MCanvasPeer
 * Method:    resetTargetGC
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MCanvasPeer_resetTargetGC
(JNIEnv * env, jobject this, jobject target)
{
    (*env)->CallVoidMethod(env, target, mCanvasIDs.setGCFromPeerMID);
}

/*
 * Class:     sun_awt_motif_MCanvasPeer
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_sun_awt_motif_MCanvasPeer_initIDs
(JNIEnv * env, jclass cls)
{
    jclass canvasCls = (*env)->FindClass(env, "java/awt/Canvas");
    mCanvasIDs.setGCFromPeerMID =
     (*env)->GetMethodID(env, canvasCls, "setGCFromPeer","()V");

    DASSERT(mCanvasIDs.setGCFromPeerMID);
}
