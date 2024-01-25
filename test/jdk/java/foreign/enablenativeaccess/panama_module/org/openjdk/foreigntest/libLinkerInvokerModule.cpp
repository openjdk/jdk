/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include "jni.h"
#include "testlib_threads.h"

typedef struct {
   JavaVM* jvm;
   jobject linker;
   jobject desc;
   jobject opts;
   jthrowable exception;
} Context;

void call(void* arg) {
    Context* context = (Context*)arg;
    JNIEnv* env;
    context->jvm->AttachCurrentThread((void**)&env, NULL);
    jclass linkerClass = env->FindClass("java/lang/foreign/Linker");
    jmethodID nativeLinkerMethod = env->GetMethodID(linkerClass, "downcallHandle",
            "(Ljava/lang/foreign/FunctionDescriptor;[Ljava/lang/foreign/Linker$Option;)Ljava/lang/invoke/MethodHandle;");
    env->CallVoidMethod(context->linker, nativeLinkerMethod, context->desc, context->opts);
    context->exception = (jthrowable) env->NewGlobalRef(env->ExceptionOccurred());
    env->ExceptionClear();
    context->jvm->DetachCurrentThread();
}

extern "C" {
    JNIEXPORT void JNICALL
    Java_org_openjdk_foreigntest_PanamaMainJNI_nativeLinker0(JNIEnv *env, jclass cls, jobject linker, jobject desc, jobjectArray opts) {
        Context context;
        env->GetJavaVM(&context.jvm);
        context.linker = env->NewGlobalRef(linker);
        context.desc = env->NewGlobalRef(desc);
        context.opts = env->NewGlobalRef(opts);
        run_in_new_thread_and_join(call, &context);
        if (context.exception != nullptr) {
            env->Throw(context.exception); // transfer exception to this thread
        }
        env->DeleteGlobalRef(context.linker);
        env->DeleteGlobalRef(context.desc);
        env->DeleteGlobalRef(context.opts);
        env->DeleteGlobalRef(context.exception);
    }
}
