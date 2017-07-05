/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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

#include <windowsx.h>
#include <jni.h>
#include <jni_util.h>
#include "awt.h"
#include "awt_Object.h"
#include "awt_Component.h"

extern "C" {

/*
 * Class:     sun_awt_DefaultMouseInfoPeer
 * Method:    isWindowUnderMouse
 * Signature: (Ljava/awt/Window)Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_awt_DefaultMouseInfoPeer_isWindowUnderMouse(JNIEnv *env, jclass cls,
                                                        jobject window)
{
    POINT pt;

    if (env->EnsureLocalCapacity(1) < 0) {
        return JNI_FALSE;
    }

    jobject winPeer = AwtObject::GetPeerForTarget(env, window);
    PDATA pData;
    pData = JNI_GET_PDATA(winPeer);
    env->DeleteLocalRef(winPeer);
    if (pData == NULL) {
        return JNI_FALSE;
    }

    AwtComponent * ourWindow = (AwtComponent *)pData;
    HWND hwnd = ourWindow->GetHWnd();
    VERIFY(::GetCursorPos(&pt));

    AwtComponent * componentFromPoint = AwtComponent::GetComponent(::WindowFromPoint(pt));

    while (componentFromPoint != NULL
        && componentFromPoint->GetHWnd() != hwnd
        && !AwtComponent::IsTopLevelHWnd(componentFromPoint->GetHWnd()))
    {
        componentFromPoint = componentFromPoint->GetParent();
    }

    return ((componentFromPoint != NULL) && (componentFromPoint->GetHWnd() == hwnd)) ? JNI_TRUE : JNI_FALSE;

}

/*
 * Class:     sun_awt_DefaultMouseInfoPeer
 * Method:    fillPointWithCoords
 * Signature: (Ljava/awt/Point)I
 */
JNIEXPORT jint JNICALL
Java_sun_awt_DefaultMouseInfoPeer_fillPointWithCoords(JNIEnv *env, jclass cls, jobject point)
{
    static jclass pointClass = NULL;
    static jfieldID xID, yID;
    POINT pt;

    VERIFY(::GetCursorPos(&pt));
    if (pointClass == NULL) {
        jclass pointClassLocal = env->FindClass("java/awt/Point");
        DASSERT(pointClassLocal != NULL);
        if (pointClassLocal == NULL) {
            return (jint)0;
        }
        pointClass = (jclass)env->NewGlobalRef(pointClassLocal);
        env->DeleteLocalRef(pointClassLocal);
    }
    xID = env->GetFieldID(pointClass, "x", "I");
    yID = env->GetFieldID(pointClass, "y", "I");
    env->SetIntField(point, xID, pt.x);
    env->SetIntField(point, yID, pt.y);

    // Always return 0 on Windows: we assume there's always a
    // virtual screen device used.
    return (jint)0;
}

} // extern "C"
