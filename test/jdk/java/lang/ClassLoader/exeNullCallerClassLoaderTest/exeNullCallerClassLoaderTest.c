/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#include <stdio.h>
#include <stdlib.h>

#include "jni.h"
#include "assert.h"

int main(int argc, char** args) {
    JavaVM *jvm;
    JNIEnv *env;
    JavaVMInitArgs vm_args;
    JavaVMOption options[1];
    jint rc;


    vm_args.version = JNI_VERSION_1_2;
    vm_args.nOptions = 0;
    vm_args.options = options;

    if ((rc = JNI_CreateJavaVM(&jvm, (void**)&env, &vm_args)) != JNI_OK) {
        printf("ERROR: cannot create VM.\n");
        exit(-1);
    }

    // call ClassLoader.registerAsParallelCapable()
    jclass class_ClassLoader = (*env)->FindClass(env, "java/lang/ClassLoader");
    assert(class_ClassLoader != NULL);
    jmethodID mid_ClassLoader_registerAsParallelCapable = (*env)->GetStaticMethodID(env, class_ClassLoader, "registerAsParallelCapable", "()Z" );
    assert(mid_ClassLoader_registerAsParallelCapable != NULL);
    jboolean b = (*env)->CallStaticBooleanMethod(env, class_ClassLoader, mid_ClassLoader_registerAsParallelCapable );
    if ((*env)->ExceptionOccurred(env) != NULL) {
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }
    if (b != JNI_FALSE) {
        printf("ERROR: Unexpected true return value.\n");
        exit(1);
    }

    (*jvm)->DestroyJavaVM(jvm);
    return 0;
}

