/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
#include "jni.h"
#include "jni_util.h"

#include "awt_KeyboardFocusManager.h"
#include "java_awt_KeyboardFocusManager.h"
#include "java_awt_event_FocusEvent.h"
#include "awt_Component.h"
#include "canvas.h"
#include "awt_MToolkit.h"

extern struct MComponentPeerIDs mComponentPeerIDs;

struct KeyboardFocusManagerIDs keyboardFocusManagerIDs;

/*
 * Class:     java_awt_KeyboardFocusManager
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_awt_KeyboardFocusManager_initIDs
    (JNIEnv *env, jclass cls)
{
    jclass keyclass = NULL;

    keyboardFocusManagerIDs.keyboardFocusManagerCls = (jclass)
        (*env)->NewGlobalRef(env, cls);
    keyboardFocusManagerIDs.shouldNativelyFocusHeavyweightMID =
        (*env)->GetStaticMethodID(env, cls, "shouldNativelyFocusHeavyweight",
            "(Ljava/awt/Component;Ljava/awt/Component;ZZJLsun/awt/CausedFocusEvent$Cause;)I");
    keyboardFocusManagerIDs.heavyweightButtonDownMID =
        (*env)->GetStaticMethodID(env, cls, "heavyweightButtonDown",
            "(Ljava/awt/Component;J)V");
    keyboardFocusManagerIDs.heavyweightButtonDownZMID =
        (*env)->GetStaticMethodID(env, cls, "heavyweightButtonDown",
            "(Ljava/awt/Component;JZ)V");
    keyboardFocusManagerIDs.markClearGlobalFocusOwnerMID =
        (*env)->GetStaticMethodID(env, cls, "markClearGlobalFocusOwner",
                                  "()Ljava/awt/Window;");

    keyboardFocusManagerIDs.processSynchronousTransferMID =
        (*env)->GetStaticMethodID(env, cls, "processSynchronousLightweightTransfer",
                                  "(Ljava/awt/Component;Ljava/awt/Component;ZZJ)Z");

    keyclass = (*env)->FindClass(env, "java/awt/event/KeyEvent");
    DASSERT (keyclass != NULL);

    keyboardFocusManagerIDs.isProxyActive =
        (*env)->GetFieldID(env, keyclass, "isProxyActive",
                           "Z");

    (*env)->DeleteLocalRef(env, keyclass);

    DASSERT(keyboardFocusManagerIDs.keyboardFocusManagerCls != NULL);
    DASSERT(keyboardFocusManagerIDs.shouldNativelyFocusHeavyweightMID !=
            NULL);
    DASSERT(keyboardFocusManagerIDs.heavyweightButtonDownMID != NULL);
    DASSERT(keyboardFocusManagerIDs.heavyweightButtonDownZMID != NULL);
    DASSERT(keyboardFocusManagerIDs.markClearGlobalFocusOwnerMID != NULL);
    DASSERT(keyboardFocusManagerIDs.processSynchronousTransferMID != NULL);
}

/*
 * Class:     java_awt_KeyboardFocusManager
 * Method:    getNativeFocusOwner
 * Signature: ()Ljava/awt/Component;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_KeyboardFocusManagerPeerImpl_getNativeFocusOwner
    (JNIEnv *env, jclass cls)
{
    jobject l_peer;

    AWT_LOCK();
    l_peer = awt_canvas_getFocusOwnerPeer();
    AWT_UNLOCK();

    return (l_peer != NULL)
        ? (*env)->GetObjectField(env, l_peer, mComponentPeerIDs.target)
        : NULL;
}

/*
 * Class:     java_awt_KeyboardFocusManager
 * Method:    getNativeFocusedWindow
 * Signature: ()Ljava/awt/Window;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_KeyboardFocusManagerPeerImpl_getNativeFocusedWindow
    (JNIEnv *env, jclass cls)
{
    jobject l_peer;

    AWT_LOCK();
    l_peer = awt_canvas_getFocusedWindowPeer();
    AWT_UNLOCK();

    return (l_peer != NULL)
        ? (*env)->GetObjectField(env, l_peer, mComponentPeerIDs.target)
        : NULL;
}

/*
 * Class:     java_awt_KeyboardFocusManager
 * Method:    clearGlobalFocusOwner
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_sun_awt_KeyboardFocusManagerPeerImpl_clearNativeGlobalFocusOwner
    (JNIEnv *env, jobject self, jobject activeWindow)
{
  /* Redirect focus to the focus proxy of the active Window. The effect
     we want is for the active Window to remain active, but for none of
     its children to be the focus owner. AWT maintains state to know
     that any key events delivered after this call (but before focus is
     re-established elsewhere) get ignored. */

    Widget proxy;

    if ((*env)->EnsureLocalCapacity(env, 1) < 0) {
        return;
    }

    AWT_LOCK();

    if (activeWindow != NULL) {
        // Setting focus owner to proxy will be equivalent to having
        // null focus owner in Java layer while we will still be
        // able to receive key events.
        proxy = findWindowsProxy(activeWindow, env);

        if (proxy != NULL) {
            Widget curFocusWidget = XmGetFocusWidget(proxy);
            if (curFocusWidget != NULL) {
                callFocusHandler(curFocusWidget, FocusOut, NULL);
            }

            // Disable all but proxy widgets
            processTree(curFocusWidget, proxy, False);

            XmProcessTraversal(proxy, XmTRAVERSE_CURRENT);
        }
    }

    AWT_UNLOCK();
}
