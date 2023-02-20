/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#ifdef AIX
#include <pthread.h>
#endif //AIX

static void* run(void *arg) {
    JavaVM *jvm;
    JNIEnv* env;
    JavaVMInitArgs args;
    JavaVMOption options[1];

    char classpath[4096];
    snprintf(classpath, sizeof classpath,
             "-Djava.class.path=%s", getenv("CLASSPATH"));
    options[0].optionString = classpath;

    args.version = JNI_VERSION_1_8;
    args.nOptions = 1;
    args.options = &options[0];
    args.ignoreUnrecognized = 0;

    int ret = JNI_CreateJavaVM(&jvm, (void**)&env, &args);
    if (ret < 0) {
      exit(10);
    }

    jclass test_class;
    jmethodID test_method;

    test_class = (*env)->FindClass(env, "TestNativeProcessBuilder$Test");
    if (test_class == NULL) {
      exit(11);
    }

    test_method = (*env)->GetStaticMethodID(env, test_class, "test", "()V");
    if (test_method == NULL) {
      exit(12);
    }

    (*env)->CallStaticVoidMethod(env, test_class, test_method);
    return 0;
}

int main(int argc, char *argv[]) {
#ifdef AIX
    size_t adjusted_stack_size = 1024*1024;
    pthread_t id;
    int result;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, adjusted_stack_size);
    result = pthread_create(&id, &attr, run, (void *)argv);
    if (result != 0) {
      fprintf(stderr, "Error: pthread_create failed with error code %d \n", result);
      return -1;
    }
    pthread_join(id, NULL);
#else
    run(&argv);
#endif //AIX
}
