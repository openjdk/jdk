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

#import "apple_applescript_AppleScriptEngine.h"
#import "apple_applescript_AppleScriptEngineFactory.h"

#import <JavaNativeFoundation/JavaNativeFoundation.h>

#import "NS_Java_ConversionUtils.h"
#import "AppleScriptExecutionContext.h"

//#define DEBUG 1


/*
 * Class:     apple_applescript_AppleScriptEngineFactory
 * Method:    initNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_apple_applescript_AppleScriptEngineFactory_initNative
(JNIEnv *env, jclass clazz)
{
    return;
}


/*
 * Class:     apple_applescript_AppleScriptEngine
 * Method:    initNative
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_apple_applescript_AppleScriptEngine_initNative
(JNIEnv *env, jclass clazz)
{
    return;
}


/*
 * Class:     apple_applescript_AppleScriptEngine
 * Method:    createContextFrom
 * Signature: (Ljava/lang/Object;)J
 */
JNIEXPORT jlong JNICALL Java_apple_applescript_AppleScriptEngine_createContextFrom
(JNIEnv *env, jclass clazz, jobject javaContext)
{
    NSObject *obj = nil;

JNF_COCOA_ENTER(env);

    obj = [[JavaAppleScriptEngineCoercion coercer] coerceJavaObject:javaContext withEnv:env];

#ifdef DEBUG
    NSLog(@"converted context: %@", obj);
#endif

    CFRetain(obj);

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(obj);
}


/*
 * Class:     apple_applescript_AppleScriptEngine
 * Method:    createObjectFrom
 * Signature: (J)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_apple_applescript_AppleScriptEngine_createObjectFrom
(JNIEnv *env, jclass clazz, jlong nativeContext)
{
    jobject obj = NULL;

JNF_COCOA_ENTER(env);

    obj = [[JavaAppleScriptEngineCoercion coercer] coerceNSObject:(id)jlong_to_ptr(nativeContext) withEnv:env];

JNF_COCOA_EXIT(env);

    return obj;
}


/*
 * Class:     apple_applescript_AppleScriptEngine
 * Method:    disposeContext
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_apple_applescript_AppleScriptEngine_disposeContext
(JNIEnv *env, jclass clazz, jlong nativeContext)
{

JNF_COCOA_ENTER(env);

    id obj = (id)jlong_to_ptr(nativeContext);
    if (obj != nil) CFRelease(obj);

JNF_COCOA_EXIT(env);

}


/*
 * Class:     apple_applescript_AppleScriptEngine
 * Method:    evalScript
 * Signature: (Ljava/lang/String;J)J
 */
JNIEXPORT jlong JNICALL Java_apple_applescript_AppleScriptEngine_evalScript
(JNIEnv *env, jclass clazz, jstring ascript, jlong contextptr)
{
    id retval = nil;

JNF_COCOA_ENTER(env);

    NSDictionary *ncontext = jlong_to_ptr(contextptr);
    NSString *source = JNFJavaToNSString(env, ascript);

#ifdef DEBUG
    NSLog(@"evalScript(source:\"%@\" context: %@)", source, ncontext);
#endif

    AppleScriptExecutionContext *scriptInvocationCtx = [[[AppleScriptExecutionContext alloc] initWithSource:source context:ncontext] autorelease];
    retval = [scriptInvocationCtx invokeWithEnv:env];

#ifdef DEBUG
    NSLog(@"returning: %@", retval);
#endif

    if (retval) CFRetain(retval);

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(retval);
}


/*
 * Class:     apple_applescript_AppleScriptEngine
 * Method:    evalScriptFromURL
 * Signature: (Ljava/lang/String;J)J
 */
JNIEXPORT jlong JNICALL Java_apple_applescript_AppleScriptEngine_evalScriptFromURL
(JNIEnv *env, jclass clazz, jstring afilename, jlong contextptr)
{
    id retval = nil;

JNF_COCOA_ENTER(env);

    NSDictionary *ncontext = jlong_to_ptr(contextptr);
    NSString *filename = JNFJavaToNSString(env, afilename);

#ifdef DEBUG
    NSLog(@"evalScript(filename:\"%@\" context: %@)", filename, ncontext);
#endif

    AppleScriptExecutionContext *scriptInvocationCtx = [[[AppleScriptExecutionContext alloc] initWithFile:filename context:ncontext] autorelease];
    retval = [scriptInvocationCtx invokeWithEnv:env];

#ifdef DEBUG
    NSLog(@"returning: %@", retval);
#endif

    if (retval) CFRetain(retval);

JNF_COCOA_EXIT(env);

    return ptr_to_jlong(retval);
}
