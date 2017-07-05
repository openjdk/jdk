/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

#import <AppKit/AppKit.h>
#import <JavaNativeFoundation/JavaNativeFoundation.h>
#import <objc/message.h>

#import "ThreadUtilities.h"


// The following must be named "jvm", as there are extern references to it in AWT
JavaVM *jvm = NULL;
static JNIEnv *appKitEnv = NULL;

@implementation ThreadUtilities

+ (JNIEnv*)getJNIEnv {
AWT_ASSERT_APPKIT_THREAD;
    if (appKitEnv == NULL) {
        (*jvm)->AttachCurrentThreadAsDaemon(jvm, (void **)&appKitEnv, NULL);
    }
    return appKitEnv;
}

+ (JNIEnv*)getJNIEnvUncached {
    JNIEnv *env = NULL;
    (*jvm)->AttachCurrentThreadAsDaemon(jvm, (void **)&env, nil);
    return env;
}

+ (void)performOnMainThreadWaiting:(BOOL)wait block:(void (^)())block {
    if ([NSThread isMainThread] && wait == YES) {
        block(); 
    } else { 
        [JNFRunLoop performOnMainThreadWaiting:wait withBlock:block]; 
    }
}

+ (void)performOnMainThread:(SEL)aSelector on:(id)target withObject:(id)arg waitUntilDone:(BOOL)wait {
    if ([NSThread isMainThread] && wait == YES) {
        [target performSelector:aSelector withObject:arg];
    } else {
        [JNFRunLoop performOnMainThread:aSelector on:target withObject:arg waitUntilDone:wait];
    }
}

@end


void OSXAPP_SetJavaVM(JavaVM *vm)
{
    jvm = vm;
}

