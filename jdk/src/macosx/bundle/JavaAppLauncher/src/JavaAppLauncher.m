/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#import "JavaAppLauncher.h"

#import <dlfcn.h>

#import "jni.h"

#define kLaunchFailure "JavaAppLauncherFailure"


typedef jint (JNICALL *CreateJavaVM_t)(JavaVM **pvm, void **env, void *args);
typedef void (JNICALL *SetPreferredJVM_t)(const char *prefJVM);


@implementation JavaAppLauncher

@synthesize args;

- (void) findAndLoadJVM {
    NSAutoreleasePool *pool = [NSAutoreleasePool new];

    @try {
        // load the libjli.dylib of the embedded JRE (or JDK) bundle
        NSURL *jreBundleURL = [args.jreBundle bundleURL];
        CFBundleRef jreBundle = CFBundleCreate(NULL, (CFURLRef)jreBundleURL);

        NSError *err = nil;
        Boolean jreBundleLoaded = CFBundleLoadExecutableAndReturnError(jreBundle, (CFErrorRef *)&err);
        if (err != nil || !jreBundleLoaded) {
            [NSException raise:@kLaunchFailure format:@"could not load the JRE/JDK: %@", err];
        }

        // if there is a preferred libjvm to load, set it here
        if (args.preferredJVMLib != NULL) {
            SetPreferredJVM_t setPrefJVMFxnPtr = CFBundleGetFunctionPointerForName(jreBundle, CFSTR("JLI_SetPreferredJVM"));
            if (setPrefJVMFxnPtr != NULL) {
                setPrefJVMFxnPtr(args.preferredJVMLib);
            } else {
                NSLog(@"No JLI_SetPreferredJVM in JRE/JDK primary executable, failed to set preferred JVM library to: %s", args->preferredJVMLib);
            }
        }

        // pull the JNI_CreateJavaVM function pointer out of the primary executable of the JRE/JDK bundle
        CreateJavaVM_t createJVMFxnPtr = CFBundleGetFunctionPointerForName(jreBundle, CFSTR("JNI_CreateJavaVM"));
        if (createJVMFxnPtr == NULL) {
            [NSException raise:@kLaunchFailure format:@"null JNI_CreateJavaVM fxn ptr from: %@", jreBundle];
        }

        // instantiate the JVM
        JNIEnv *env;
        jint createJVMStatus = createJVMFxnPtr(&jvm, (void **)&env, &(args->vm_args));
        if (createJVMStatus != JNI_OK) {
            [NSException raise:@kLaunchFailure format:@"failed to JNI_CreateJavaVM (%d): %@", createJVMStatus, jreBundle];
        }

        // check the app needs to run the Java main() on the main thread
        if (args.startOnFirstThread) {
            dispatch_sync(dispatch_get_main_queue(), ^(void) {
                JNIEnv *mainThreadEnv;
                (*jvm)->AttachCurrentThread(jvm, (void **)&mainThreadEnv, NULL);
                [self invokeBundledAppJavaLauncherWithEnv:mainThreadEnv];
                (*jvm)->DetachCurrentThread(jvm);
            });
        } else {
            [self invokeBundledAppJavaLauncherWithEnv:env];
        }

    } @catch (NSException *e) {
        NSLog(@"%@: %@", e, [e callStackSymbols]);
    }

    if (jvm) {
        (*jvm)->DetachCurrentThread(jvm);
        (*jvm)->DestroyJavaVM(jvm);
    }

    [pool drain];
}

static const char kLauncherClassName[] = "apple/launcher/JavaAppLauncher";

- (void) invokeBundledAppJavaLauncherWithEnv:(JNIEnv *)env {
    // hand off control to the apple.launcher.JavaAppLauncher class

    jclass mainClass = (*env)->FindClass(env, kLauncherClassName);
    if (mainClass == NULL) {
        fprintf(stderr, kLaunchFailure " FindClass() failed for class %s:\n", kLauncherClassName);
        (*env)->ExceptionDescribe(env);
        return;
    }

    jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "launch", "(JZ)V");
    if ((mainMethod == NULL) || (*env)->ExceptionOccurred(env)) {
        fprintf(stderr, kLaunchFailure " GetStaticMethodID() failed for launch() method");
        (*env)->ExceptionDescribe(env);
        return;
    }

    CFDictionaryRef jvmInfo = CFRetain(args.jvmInfo);

    (*env)->CallStaticVoidMethod(env, mainClass, mainMethod, (jlong)jvmInfo, (jboolean)args.debug);
    if ((*env)->ExceptionOccurred(env)) {
        fprintf(stderr, kLaunchFailure " CallStaticVoidMethod() threw an exception\n");
        (*env)->ExceptionDescribe(env);
        return;
    }
}

@end
