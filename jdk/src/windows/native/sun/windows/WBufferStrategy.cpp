/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

#include "sun_awt_windows_WBufferStrategy.h"
#include "jni_util.h"


static jmethodID getBackBufferID;

/*
 * Class:     sun_awt_windows_WBufferStrategy
 * Method:    initIDs
 * Signature: (Ljava/lang/Class;)V
 */
JNIEXPORT void JNICALL
Java_sun_awt_windows_WBufferStrategy_initIDs(JNIEnv *env, jclass wbs,
                                             jclass componentClass)
{
    getBackBufferID = env->GetMethodID(componentClass, "getBackBuffer",
                                       "()Ljava/awt/Image;");
}

/**
 * Native method of WBufferStrategy.java.  Given a Component
 * object, this method will find the back buffer associated
 * with the Component's BufferStrategy and return a handle
 * to it.
 */
extern "C" JNIEXPORT jobject JNICALL
Java_sun_awt_windows_WBufferStrategy_getDrawBuffer(JNIEnv *env, jclass wbs,
                                                   jobject component)
{
    if (!JNU_IsNull(env, getBackBufferID)) {
        return env->CallObjectMethod(component, getBackBufferID);
    } else {
        return NULL;
    }
}
