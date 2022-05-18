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

static jclass class_InputStream = NULL;
static jmethodID mid_InputStream_close = NULL;

// in.close();
void closeInputStream(JNIEnv *env, jobject in) {
    (*env)->CallObjectMethod(env, in, mid_InputStream_close);
    if ((*env)->ExceptionOccurred(env) != NULL) {
        printf("ERROR: Exception was thrown calling InputStream::close.\n");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }
}

/*
 * The java test running this native test creates a test module named 'n'
 * which opens the package 'open'.  It has a text file resource named
 * 'test.txt' in the open package.  It also has a class called
 * open.OpenResources.  One should be able to get the resource through
 * either the Class or the Module with getResourceAsStream.
 *
 * Class c = open.OpenResources.fetchClass();
 * InputStream in1 = c.getResourceAsStream("test.txt");
 * Module n = c.getModule();
 * InputStream in2 = n.getResourceAsStream("open/test.txt");
 *
 * The test also checks that closed resources are not available and
 * don't throw any exceptions.  The test module contains a class
 * called closed.ClosedResources and a file 'test.txt' in the package
 * 'closed'.
 *
 * Class closed = closed.ClosedResources.fetchClass();
 * assert(closed.getResourceAsStream("test.txt") == null);
 * assert(n.getResourceAsStream("closed/test.txt") == null);
 *
 */
int main(int argc, char** args) {
    JavaVM *jvm;
    JNIEnv *env;
    JavaVMInitArgs vm_args;
    JavaVMOption options[4];
    jint rc;

    options[0].optionString = "--module-path=mods";
    options[1].optionString = "--add-modules=n";

    vm_args.version = JNI_VERSION_9;
    vm_args.nOptions = 2;
    vm_args.options = options;

    if ((rc = JNI_CreateJavaVM(&jvm, (void**)&env, &vm_args)) != JNI_OK) {
        printf("ERROR: cannot create VM.\n");
        exit(-1);
    }

    // initialize for stream close
    class_InputStream = (*env)->FindClass(env, "java/io/InputStream");
    assert(class_InputStream != NULL);
    mid_InputStream_close = (*env)->GetMethodID(env, class_InputStream, "close", "()V" );
    assert(mid_InputStream_close != NULL);

    // the open and closed classes
    jclass class_OpenResources = (*env)->FindClass(env, "open/OpenResources");
    assert(class_OpenResources != NULL);
    jclass class_ClosedResources = (*env)->FindClass(env, "closed/ClosedResources");
    assert(class_ClosedResources != NULL);

    // Fetch the Module from one of the classes in the module
    jclass class_Class = (*env)->FindClass(env, "java/lang/Class");
    assert(class_Class != NULL);
    jmethodID mid_Class_getModule = (*env)->GetMethodID(env, class_Class, "getModule", "()Ljava/lang/Module;" );
    assert(mid_Class_getModule != NULL);
    jobject n =(*env)->CallObjectMethod(env, class_OpenResources, mid_Class_getModule);
    if ((*env)->ExceptionOccurred(env) != NULL) {
        printf("ERROR: Exception was thrown calling Class::getModule.\n");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }
    assert(n != NULL);

    // Attempt to fetch an open resource from the module.  It should return a valid stream.
    // InputStream in = n.getResourceAsStream("open/test.txt");
    jclass class_Module = (*env)->FindClass(env, "java/lang/Module");
    assert(class_Module != NULL);
    jmethodID mid_Module_getResourceAsStream =
        (*env)->GetMethodID(env, class_Module, "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;" );
    assert(mid_Module_getResourceAsStream != NULL);
    jobject in = (*env)->CallObjectMethod(env, n, mid_Module_getResourceAsStream,
        (*env)->NewStringUTF(env, "open/test.txt"));
    if ((*env)->ExceptionOccurred(env) != NULL) {
        printf("ERROR: Exception was thrown calling Module::getResourceAsStream on 'open/test.txt'.\n");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }
    if (in == NULL) {
        printf("ERROR: Module::getResourceAsStream, expected valid stream for open resource\n");
        exit(-1);
    }

    // in.close();
    closeInputStream(env, in);

    // Attempt to fetch closed resource from the module.  It should return null.
    // in = n.getResourceAsStream("closed/test.txt");
    in =  (*env)->CallObjectMethod(env, n, mid_Module_getResourceAsStream,
        (*env)->NewStringUTF(env, "closed/test.txt"));
    if ((*env)->ExceptionOccurred(env) != NULL) {
        printf("ERROR: Exception was thrown calling Module::getResourceAsStream on 'closed/test.txt'.\n");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }
    if (in != NULL) {
        printf("ERROR: Module::getResourceAsStream, expected null value for closed resource\n");
        exit(-1);
    }

    // Attempt to fetch open resource from the class.  It should return a valid stream.
    // in = open.OpenReosurces.class.getResourceAsStream("test.txt");
    jmethodID mid_Class_getResourceAsStream =
        (*env)->GetMethodID(env, class_Class, "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;" );
    assert(mid_Class_getResourceAsStream != NULL);
    in =  (*env)->CallObjectMethod(env, class_OpenResources, mid_Class_getResourceAsStream,
        (*env)->NewStringUTF(env, "test.txt"));
    if ((*env)->ExceptionOccurred(env) != NULL) {
        printf("ERROR: Exception was thrown calling Class::getResourceAsStream on 'test.txt'.\n");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }
    if (in == NULL) {
        printf("ERROR: Class::getResourceAsStream, expected valid stream for open resource\n");
        exit(-1);
    }

    // in.close();
    closeInputStream(env, in);

    // Attempt to fetch closed resource from the class.  It should return null.
    // in = closed.ClosedResources.class.getResourceAsStream("test.txt");
    in =  (*env)->CallObjectMethod(env, class_ClosedResources, mid_Class_getResourceAsStream,
        (*env)->NewStringUTF(env, "test.txt"));
    if ((*env)->ExceptionOccurred(env) != NULL) {
        printf("ERROR: Exception was thrown calling Class::getResourceAsStream on closed 'test.txt'.\n");
        (*env)->ExceptionDescribe(env);
        exit(-1);
    }
    if (in != NULL) {
        printf("ERROR: Class::getResourceAsStream, expected null value for closed resource\n");
        exit(-1);
    }

    (*jvm)->DestroyJavaVM(jvm);
    return 0;
}

