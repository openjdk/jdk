/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "awt.h"
#include "awt_KeyboardFocusManager.h"
#include "awt_Component.h"
#include "awt_Toolkit.h"
#include <java_awt_KeyboardFocusManager.h>

jclass AwtKeyboardFocusManager::keyboardFocusManagerCls;
jmethodID AwtKeyboardFocusManager::shouldNativelyFocusHeavyweightMID;
jmethodID AwtKeyboardFocusManager::heavyweightButtonDownMID;
jmethodID AwtKeyboardFocusManager::markClearGlobalFocusOwnerMID;
jmethodID AwtKeyboardFocusManager::removeLastFocusRequestMID;
jfieldID  AwtKeyboardFocusManager::isProxyActive;
jmethodID AwtKeyboardFocusManager::processSynchronousTransfer;

static jobject getNativeFocusState(JNIEnv *env, void*(*ftn)()) {
    jobject lFocusState = NULL;

    jobject gFocusState = reinterpret_cast<jobject>(AwtToolkit::GetInstance().
        InvokeFunction(ftn));
    if (gFocusState != NULL) {
        lFocusState = env->NewLocalRef(gFocusState);
        env->DeleteGlobalRef(gFocusState);
    }

    return lFocusState;
}

extern "C" {

/*
 * Class:     java_awt_KeyboardFocusManager
 * Method:    initIDs
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_java_awt_KeyboardFocusManager_initIDs
    (JNIEnv *env, jclass cls)
{
    TRY;

    AwtKeyboardFocusManager::keyboardFocusManagerCls = (jclass)
        env->NewGlobalRef(cls);
    AwtKeyboardFocusManager::shouldNativelyFocusHeavyweightMID =
        env->GetStaticMethodID(cls, "shouldNativelyFocusHeavyweight",
            "(Ljava/awt/Component;Ljava/awt/Component;ZZJLsun/awt/CausedFocusEvent$Cause;)I");
    AwtKeyboardFocusManager::heavyweightButtonDownMID =
        env->GetStaticMethodID(cls, "heavyweightButtonDown",
            "(Ljava/awt/Component;J)V");
    AwtKeyboardFocusManager::markClearGlobalFocusOwnerMID =
        env->GetStaticMethodID(cls, "markClearGlobalFocusOwner",
                               "()Ljava/awt/Window;");
    AwtKeyboardFocusManager::removeLastFocusRequestMID =
        env->GetStaticMethodID(cls, "removeLastFocusRequest",
                               "(Ljava/awt/Component;)V");

    AwtKeyboardFocusManager::processSynchronousTransfer =
        env->GetStaticMethodID(cls, "processSynchronousLightweightTransfer",
                               "(Ljava/awt/Component;Ljava/awt/Component;ZZJ)Z");

    jclass keyclass = env->FindClass("java/awt/event/KeyEvent");
    DASSERT (keyclass != NULL);

    AwtKeyboardFocusManager::isProxyActive =
        env->GetFieldID(keyclass, "isProxyActive", "Z");

    env->DeleteLocalRef(keyclass);

    DASSERT(AwtKeyboardFocusManager::keyboardFocusManagerCls != NULL);
    DASSERT(AwtKeyboardFocusManager::shouldNativelyFocusHeavyweightMID !=
            NULL);
    DASSERT(AwtKeyboardFocusManager::heavyweightButtonDownMID != NULL);
    DASSERT(AwtKeyboardFocusManager::markClearGlobalFocusOwnerMID != NULL);
    DASSERT(AwtKeyboardFocusManager::removeLastFocusRequestMID != NULL);
    DASSERT(AwtKeyboardFocusManager::processSynchronousTransfer != NULL);
    CATCH_BAD_ALLOC;
}


/*
 * Class:     sun_awt_KeyboardFocusManagerPeerImpl
 * Method:    getNativeFocusOwner
 * Signature: ()Ljava/awt/Component;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_KeyboardFocusManagerPeerImpl_getNativeFocusOwner
    (JNIEnv *env, jclass cls)
{
    TRY;

    return getNativeFocusState(env, AwtComponent::GetNativeFocusOwner);

    CATCH_BAD_ALLOC_RET(NULL);
}

/*
 * Class:     sun_awt_KeyboardFocusManagerPeerImpl
 * Method:    getNativeFocusedWindow
 * Signature: ()Ljava/awt/Window;
 */
JNIEXPORT jobject JNICALL
Java_sun_awt_KeyboardFocusManagerPeerImpl_getNativeFocusedWindow
    (JNIEnv *env, jclass cls)
{
    TRY;

    return getNativeFocusState(env, AwtComponent::GetNativeFocusedWindow);

    CATCH_BAD_ALLOC_RET(NULL);
}

/*
 * Class:     sun_awt_KeyboardFocusManagerPeerImpl
 * Method:    clearNativeGlobalFocusOwner
 * Signature: (Ljava/awt/Window;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_KeyboardFocusManagerPeerImpl_clearNativeGlobalFocusOwner
    (JNIEnv *env, jobject self, jobject activeWindow)
{
    TRY;

    AwtToolkit::GetInstance().InvokeFunction
        ((void*(*)(void))AwtComponent::ClearGlobalFocusOwner);

    CATCH_BAD_ALLOC;
}
}
