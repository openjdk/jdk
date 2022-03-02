/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#undef NDEBUG
#include "assert.h"
#include "string.h"


/*
 * The java test running this native test passes in an argument to provide as
 * an option for the configuration of the JVM.  The system classpath has the
 * classpath of the java test appended so it can pick up the resource that
 * was created by the java part of the test.
 */
int main(int argc, char** args) {
    JavaVM *jvm;
    JNIEnv *env;
    JavaVMInitArgs vm_args;
    JavaVMOption options[1];
    jint rc;

    assert(argc == 2);
    options[0].optionString = args[1];

    vm_args.version = JNI_VERSION_1_2;
    vm_args.nOptions = 1;
    vm_args.options = options;

    if ((rc = JNI_CreateJavaVM(&jvm, (void**)&env, &vm_args)) != JNI_OK) {
        printf("ERROR: cannot create VM.\n");
        exit(-1);
    }

    // b = ResourceBundle.getBundle("NullCallerResource");
    jclass class_ResourceBundle = (*env)->FindClass(env, "java/util/ResourceBundle");
    assert(class_ResourceBundle != NULL);
    jmethodID mid_ResourceBundle_getBundle = (*env)->GetStaticMethodID(env, class_ResourceBundle, "getBundle", "(Ljava/lang/String;)Ljava/util/ResourceBundle;" );
    assert(mid_ResourceBundle_getBundle != NULL);
    jobject resourceName = (*env)->NewStringUTF(env, "NullCallerResource");
    assert(resourceName != NULL);
    jobject b = (*env)->CallStaticObjectMethod(env, class_ResourceBundle, mid_ResourceBundle_getBundle, resourceName);
    if ((*env)->ExceptionOccurred(env) != NULL) {
        printf("ERROR: Exception was thrown calling ResourceBundle::getBundle.\n");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }

    // msg = b.getString("message");
    jmethodID mid_ResourceBundle_getString = (*env)->GetMethodID(env, class_ResourceBundle, "getString", "(Ljava/lang/String;)Ljava/lang/String;" );
    assert(mid_ResourceBundle_getString != NULL);
    jobject key = (*env)->NewStringUTF(env, "message");
    jobject msg =(*env)->CallObjectMethod(env, b, mid_ResourceBundle_getString, key);
    if ((*env)->ExceptionOccurred(env) != NULL) {
        printf("ERROR: Exception was thrown calling ResourceBundle::getString.\n");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }
    assert(msg != NULL);

    // check the message
    const char* cstr = (*env)->GetStringUTFChars(env, msg, NULL);
    assert(cstr != NULL);
    assert(strcmp(cstr,"Hello!") == 0);

    // ResourceBundle.clearCache()
    jmethodID mid_ResourceBundle_clearCache = (*env)->GetStaticMethodID(env, class_ResourceBundle, "clearCache", "()V" );
    assert(mid_ResourceBundle_clearCache != NULL);
    (*env)->CallStaticVoidMethod(env, class_ResourceBundle, mid_ResourceBundle_clearCache);
    if ((*env)->ExceptionOccurred(env) != NULL) {
        printf("ERROR: Exception was thrown calling ResourceBundle::clearCache.\n");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }

    (*jvm)->DestroyJavaVM(jvm);
    return 0;
}

