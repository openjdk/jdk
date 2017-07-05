/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

#import "apple_launcher_JavaAppLauncher.h"

#import <Cocoa/Cocoa.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>


/*
 * Class:     apple_launcher_JavaAppLauncher
 * Method:    nativeConvertAndRelease
 * Signature: (J)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_apple_launcher_JavaAppLauncher_nativeConvertAndRelease
(JNIEnv *env, jclass clazz, jlong nsObjectPtr) {

    jobject value = NULL;

JNF_COCOA_ENTER(env);

    id obj = jlong_to_ptr(nsObjectPtr);
    value = [[JNFDefaultCoercions defaultCoercer] coerceNSObject:obj withEnv:env];
    CFRelease(obj);

JNF_COCOA_EXIT(env);

    return value;
}

/*
 * Class:     apple_launcher_JavaAppLauncher
 * Method:    nativeInvokeNonPublic
 * Signature: (Ljava/lang/Class;Ljava/lang/reflect/Method;[Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_apple_launcher_JavaAppLauncher_nativeInvokeNonPublic
(JNIEnv *env, jclass clazz, jclass targetClass, jobject targetMethod, jobjectArray args) {
    jmethodID mainMethodID = (*env)->FromReflectedMethod(env, targetMethod);
    if ((*env)->ExceptionOccurred(env)) return;
    (*env)->CallStaticVoidMethod(env, targetClass, mainMethodID, args);
}
