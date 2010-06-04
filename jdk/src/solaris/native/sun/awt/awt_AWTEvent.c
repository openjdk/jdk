/*
 * Copyright (c) 1998, 2002, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Implements the native code for the java.awt.AWTEvent class
 * and all of the classes in the java.awt.event package.
 *
 * THIS FILE DOES NOT IMPLEMENT ANY OF THE OBSOLETE java.awt.Event
 * CLASS. SEE awt_Event.[ch] FOR THAT CLASS' IMPLEMENTATION.
 */

#ifdef HEADLESS
    #error This file should not be included in headless library
#endif

#include "awt_p.h"
#include "java_awt_AWTEvent.h"
#include "java_awt_event_InputEvent.h"
#include "java_awt_event_KeyEvent.h"
#include "jni_util.h"

#include "canvas.h"
#include "awt_AWTEvent.h"
#include "awt_Component.h"

struct AWTEventIDs awtEventIDs;
struct InputEventIDs inputEventIDs;
struct KeyEventIDs keyEventIDs;
struct MComponentPeerIDs mComponentPeerIDs;

JNIEXPORT void JNICALL
Java_java_awt_AWTEvent_initIDs(JNIEnv *env, jclass cls)
{
    awtEventIDs.bdata = (*env)->GetFieldID(env, cls, "bdata", "[B");
    awtEventIDs.consumed = (*env)->GetFieldID(env, cls, "consumed", "Z");
    awtEventIDs.id = (*env)->GetFieldID(env, cls, "id", "I");
}

JNIEXPORT void JNICALL
Java_java_awt_event_InputEvent_initIDs(JNIEnv *env, jclass cls)
{
    inputEventIDs.modifiers = (*env)->GetFieldID(env, cls, "modifiers", "I");
}

JNIEXPORT void JNICALL
Java_java_awt_event_KeyEvent_initIDs(JNIEnv *env, jclass cls)
{
    keyEventIDs.keyCode = (*env)->GetFieldID(env, cls, "keyCode", "I");
    keyEventIDs.keyChar = (*env)->GetFieldID(env, cls, "keyChar", "C");
}
#ifndef XAWT
JNIEXPORT void JNICALL
Java_java_awt_AWTEvent_nativeSetSource(JNIEnv *env, jobject self,
                                       jobject newSource)
{
    jbyteArray bdata;

    AWT_LOCK();

    bdata = (jbyteArray)(*env)->GetObjectField(env, self, awtEventIDs.bdata);

    if (bdata != NULL) {
        XEvent *xev;
        Window w;
        jboolean dummy;

        /* get the widget out of the peer newSource */
        struct ComponentData *cdata = (struct ComponentData *)
            JNU_GetLongFieldAsPtr(env, newSource, mComponentPeerIDs.pData);
        if (JNU_IsNull(env, cdata) || (cdata == NULL) ||
            ((cdata->widget != NULL) && (XtIsObject(cdata->widget)) &&
             (cdata->widget->core.being_destroyed))) {
            JNU_ThrowNullPointerException(env, "null widget");
            AWT_UNLOCK();
            return;
        }

        /* get the Window out of the widget */
        w = XtWindow(cdata->widget);

        if (w == None) {
            JNU_ThrowNullPointerException(env, "null window");
            AWT_UNLOCK();
            return;
        }

        /* reset the filed in the event */
        xev = (XEvent *)(*env)->GetPrimitiveArrayCritical(env, bdata, &dummy);
        if (xev == NULL) {
            JNU_ThrowNullPointerException(env, "null data");
            AWT_UNLOCK();
            return;
        }
        xev->xany.window = w;
        (*env)->ReleasePrimitiveArrayCritical(env, bdata, (void *)xev, 0);
    }

    AWT_UNLOCK();
}
#else
JNIEXPORT void JNICALL
Java_java_awt_AWTEvent_nativeSetSource(JNIEnv *env, jobject self,
                                       jobject newSource)
{

}

#endif
