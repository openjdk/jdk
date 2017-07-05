/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 *      Implementation of class StackFrameInfo
 */

#include <stdio.h>
#include <signal.h>

#include "jni.h"
#include "jvm.h"

#include "java_lang_StackFrameInfo.h"


/*
 * Class:     java_lang_StackFrameInfo
 * Method:    fillInStackFrames
 * Signature: (I[Ljava/lang/Object;[Ljava/lang/Object;II)V
 */
JNIEXPORT void JNICALL Java_java_lang_StackFrameInfo_fillInStackFrames
  (JNIEnv *env, jclass dummy, jint startIndex,
   jobjectArray stackFrames, jint fromIndex, jint toIndex) {
    JVM_FillStackFrames(env, dummy, startIndex,
                        stackFrames, fromIndex, toIndex);
}

/*
 * Class:     java_lang_StackFrameInfo
 * Method:    setMethodInfo
 * Signature: (Ljava/lang/Class;)V
 */
JNIEXPORT void JNICALL Java_java_lang_StackFrameInfo_setMethodInfo
  (JNIEnv *env, jobject stackframeinfo) {
     JVM_SetMethodInfo(env, stackframeinfo);
}
