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

#include "com_apple_jobjc_Utils_Threads.h"

#import <AppKit/AppKit.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>


@interface JObjCCallable : NSObject {
    @public jobject returnValue;
    @public jthrowable exception;
}
@property jobject returnValue;
@property jthrowable exception;
- (void) performCallable:(JNFJObjectWrapper *)callableWrapper;
@end

/*
 * Class:     com_apple_jobjc_Utils_Threads
 * Method:    performRunnableOnMainThreadNative
 * Signature: (Ljava/lang/Runnable;Z)V
 */
JNIEXPORT void JNICALL Java_com_apple_jobjc_Utils_00024Threads_performRunnableOnMainThread
(JNIEnv *env, jclass clazz, jobject runnable, jboolean jWaitUntilDone)
{
JNF_COCOA_ENTER(env);
    [JNFRunLoop performOnMainThreadWaiting:jWaitUntilDone
                                 withBlock:[JNFRunnable blockWithRunnable:runnable
                                                                  withEnv:env]];
JNF_COCOA_EXIT(env);
}

/*
 * Class:     com_apple_jobjc_Utils_Threads
 * Method:    performCallableOnMainThreadNative
 * Signature: (Ljava/util/concurrent/Callable;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_com_apple_jobjc_Utils_00024Threads_performCallableOnMainThread
(JNIEnv *env, jclass clazz, jobject callable)
{
    jobject returnValue = NULL;

JNF_COCOA_ENTER(env);
    JNFJObjectWrapper *callableWrapper = [[JNFJObjectWrapper alloc] initWithJObject:callable withEnv:env];
    JObjCCallable *ncallable = [JObjCCallable alloc];

    [ncallable performSelectorOnMainThread:@selector(performCallable:)
                                withObject:callableWrapper
                             waitUntilDone:true];

    returnValue = ncallable.returnValue;
    jthrowable exception = ncallable.exception;

    [ncallable release];
    if(exception) (*env)->Throw(env, exception);

JNF_COCOA_EXIT(env);

    return returnValue;
}


@implementation JObjCCallable
@synthesize returnValue;
@synthesize exception;

- (void) performCallable:(JNFJObjectWrapper *)callableWrapper {
    static JNF_CLASS_CACHE(jc_Callable, "java/util/concurrent/Callable");
    static JNF_MEMBER_CACHE(jm_Callable_call, jc_Callable, "call", "()Ljava/lang/Object;");

    JNFThreadContext threadWasAttached = JNFThreadDetachOnThreadDeath;
    JNIEnv *env = JNFObtainEnv(&threadWasAttached);
    jobject callable = [callableWrapper jObject];

    @try{
        self.returnValue = JNFCallObjectMethod(env, callable, jm_Callable_call);
    } @catch (JNFException *x) {
        [x raiseToJava:env];
    }

    self.exception = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);

    [callableWrapper release];
    JNFReleaseEnv(env, &threadWasAttached);
}

@end
