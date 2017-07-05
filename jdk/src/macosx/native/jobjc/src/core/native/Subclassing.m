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

#include "com_apple_jobjc_Subclassing.h"

#include <math.h>
#include <assert.h>
#include <errno.h>

#include <objc/runtime.h>
#include <objc/message.h>

#include <ffi/ffi.h>
#include <sys/mman.h>

#include <JavaNativeFoundation/JavaNativeFoundation.h>

// Subclassing of Obj-C classes in Java
//
// See:
//  - Objective-C Runtime documentation
//  - man ffi_prep_closure
//  - Subclassing.java


#pragma mark Accessing object in IVar

#define JOBJ_IVAR_NAME "jObjWrapper"
static jobject getJObjectFromIVar(id obj);

jobject getJObjectFromIVar(id obj)
{
    JNFJObjectWrapper *wrapper = NULL;
    object_getInstanceVariable(obj, JOBJ_IVAR_NAME, (void**) &wrapper);
    return wrapper ? [wrapper jObject] : NULL;
}

JNIEXPORT jobject JNICALL Java_com_apple_jobjc_Subclassing_getJObjectFromIVar
(JNIEnv *env, jclass jClass, jlong jPtr)
{
    id obj = (id) jlong_to_ptr(jPtr);
    if(obj == NULL){
        (*env)->ThrowNew(env, (*env)->FindClass(env,
            "java/lang/NullPointerException"), "obj");
        return NULL;
    }

    JNFJObjectWrapper *wrapper;

    if(!object_getInstanceVariable(obj, JOBJ_IVAR_NAME, (void**) &wrapper)){
        NSLog(@"IVar '%s' not found. obj: %@", JOBJ_IVAR_NAME, obj);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"),
            "Could not find instance variable that holds Java object.");
        return NULL;
    }

    return wrapper ? [wrapper jObject] : NULL;
}

JNIEXPORT void JNICALL Java_com_apple_jobjc_Subclassing_initJObjectToIVar
(JNIEnv *env, jclass jClass, jlong jPtr, jobject jObject)
{
    id obj = (id) jlong_to_ptr(jPtr);
    JNFJObjectWrapper *wrapper = [[JNFJObjectWrapper alloc]
        initWithJObject:jObject withEnv:env];
    [wrapper retain];

    if(!object_setInstanceVariable(obj, JOBJ_IVAR_NAME, wrapper)){
        NSLog(@"IVar '%s' not found. obj: %@", JOBJ_IVAR_NAME, obj);
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/RuntimeException"),
            "Could not find instance variable that holds Java object.");
        return;
    }
}

#pragma mark Registering class

JNIEXPORT jlong JNICALL Java_com_apple_jobjc_Subclassing_allocateClassPair
(JNIEnv *env, jclass clazz, jlong jSuperClass, jstring jName)
{
    const Class superClass = (Class)jlong_to_ptr(jSuperClass);
    assert(superClass);

    const char *name = (*env)->GetStringUTFChars(env, jName, JNI_FALSE);
    const Class newClass = objc_allocateClassPair(superClass, name, 0);
    (*env)->ReleaseStringUTFChars(env, jName, name);

    return ptr_to_jlong(newClass);
}

JNIEXPORT jboolean JNICALL Java_com_apple_jobjc_Subclassing_addIVarForJObj
(JNIEnv *env, jclass clazz, jlong jSynthClass)
{
    return class_addIvar(
        jlong_to_ptr(jSynthClass),
        JOBJ_IVAR_NAME,
        sizeof(id),
        (uint8_t)log2((double)sizeof(id)),
        "@");
}

JNIEXPORT void JNICALL Java_com_apple_jobjc_Subclassing_registerClassPair
(JNIEnv *env, jclass clazz, jlong jClass)
{
    Class c = jlong_to_ptr(jClass);
//    NSLog(@"Registering class pair %p / %s", c, class_getName(c));
    objc_registerClassPair(c);
}


#pragma mark Patching +alloc

static id patchedAllocIMP(id obj, SEL sel);
static void addJavaInstance(id obj);

JNIEXPORT jboolean JNICALL Java_com_apple_jobjc_Subclassing_patchAlloc
(JNIEnv *env, jclass clazz, jlong jNativeClass)
{
    Class metaClass = object_getClass(jlong_to_ptr(jNativeClass));
    return class_addMethod(metaClass,
       sel_registerName("alloc"),
       (IMP) patchedAllocIMP,
       "@@:");
}

static id patchedAllocIMP(id cls, SEL sel){
    id inst = class_createInstance(cls, 0);
    addJavaInstance(inst);
    return inst;
}

static void addJavaInstance(id obj){
//    NSLog(@"addJavaInstance %p", obj);
//    NSLog(@"... calling up to Java");

    static JNF_CLASS_CACHE(jc_Subclassing, "com/apple/jobjc/Subclassing");
    static JNF_STATIC_MEMBER_CACHE(jm_Subclassing_initJObject,
        jc_Subclassing,
        "initJObject",
        "(J)V");

    JNFThreadContext threadWasAttached = JNFThreadDetachOnThreadDeath;
    JNIEnv *env = JNFObtainEnv(&threadWasAttached);
    JNFCallStaticVoidMethod(env, jm_Subclassing_initJObject,
        ptr_to_jlong(obj));

    JNFReleaseEnv(env, &threadWasAttached);
}


#pragma mark Adding methods

static ffi_closure *make_closure(ffi_cif *cif, void *user_data);
static void sel_closure_call(ffi_cif* cif, void* result, void** args, void* user_data);

typedef struct closure_data_t{
    JNFJObjectWrapper *jMethod;
    JNFJObjectWrapper *jCIF;
} closure_data_t;

static ffi_closure *make_closure(ffi_cif *cif, void *user_data){
    // Allocate a page to hold the closure with read and write permissions.
    ffi_closure *closure;
    if ((closure = mmap(NULL, sizeof(ffi_closure), PROT_READ | PROT_WRITE,
                        MAP_ANON | MAP_PRIVATE, -1, (off_t) 0)) == (void*)-1)
    {
        fprintf(stderr, "mmap failed with errno: %d", errno);
        return NULL;
    }

    // Prepare the ffi_closure structure.
    ffi_status status;
    if ((status = ffi_prep_closure(closure, cif, sel_closure_call, (void *)user_data)) != FFI_OK)
    {
        fprintf(stderr, "ffi_prep_closure failed with ffi_status: %d", status);
        munmap(closure, sizeof(ffi_closure));
        return NULL;
    }

    // Ensure that the closure will execute on all architectures.
    if (mprotect(closure, sizeof(closure), PROT_READ | PROT_EXEC) == -1)
    {
        fprintf(stderr, "mprotect failed with errno: %d", errno);
        munmap(closure, sizeof(ffi_closure));
        return NULL;
    }
    return closure;
}

JNIEXPORT jboolean JNICALL Java_com_apple_jobjc_Subclassing_addMethod
(JNIEnv *env, jclass clazz, jlong jClass, jstring jSelName, jobject jMethod,
    jobject jCIF, jlong jCIFPtr, jstring jObjCEncodedType)
{
    ffi_cif *cif = jlong_to_ptr(jCIFPtr);

    closure_data_t *user_data = malloc(sizeof(closure_data_t));
    user_data->jMethod = [[JNFJObjectWrapper alloc] initWithJObject:jMethod withEnv:env];
    user_data->jCIF = [[JNFJObjectWrapper alloc] initWithJObject:jCIF withEnv:env];

    ffi_closure *closure;;
    if(!(closure = make_closure(cif, user_data))){
        [user_data->jMethod release];
        [user_data->jCIF release];
        free(user_data);
        return NO;
    }

    const Class objcClass = (Class)jlong_to_ptr(jClass);

    const char *selName = (*env)->GetStringUTFChars(env, jSelName, JNI_FALSE);
    const char *objCEncodedType = (*env)->GetStringUTFChars(env, jObjCEncodedType, JNI_FALSE);

//    NSLog(@"Adding method '%s' :: '%s' to '%s' / %p",
//        selName,
//        objCEncodedType,
//        class_getName(objcClass),
//        objcClass);

    BOOL ret = class_addMethod(objcClass, sel_registerName(selName), (IMP) closure, objCEncodedType);

    (*env)->ReleaseStringUTFChars(env, jSelName, selName);
    (*env)->ReleaseStringUTFChars(env, jObjCEncodedType, objCEncodedType);

    if(!ret){
        NSLog(@"class_addMethod failed");
        munmap(closure, sizeof(ffi_closure));
        [user_data->jMethod release];
        [user_data->jCIF release];
        free(user_data);
        return NO;
    }

    return ret;
}

static void sel_closure_call(ffi_cif* cif, void* result, void** args, void* user_data)
{
    id obj = *(id*) args[0];
//    SEL sel = *(SEL*) args[1];

//    NSLog(@"Subclassing: sel_closure_call: %p %p", obj, sel);
//    NSLog(@"Subclassing: sel_closure_call: obj class: %@  sel name: %s", object_getClass(obj), sel_getName(sel));

    jobject jObj = getJObjectFromIVar(obj);

    if(!jObj){
        addJavaInstance(obj);
        jObj = getJObjectFromIVar(obj);
    }

    closure_data_t *jmeta = user_data;
    jobject jMethod = [jmeta->jMethod jObject];
    jobject jCIF = [jmeta->jCIF jObject];

    JNFThreadContext threadWasAttached = JNFThreadDetachOnThreadDeath;
    JNIEnv *env = JNFObtainEnv(&threadWasAttached);

    if((*env)->ExceptionOccurred(env)) goto bail;

        static JNF_CLASS_CACHE(jc, "com/apple/jobjc/Subclassing");
        static JNF_STATIC_MEMBER_CACHE(jm_invokeFromJNI, jc, "invokeFromJNI",
        "(Lcom/apple/jobjc/ID;Ljava/lang/reflect/Method;Lcom/apple/jobjc/CIF;JJ)V");

    JNFCallStaticVoidMethod(env, jm_invokeFromJNI,
        jObj,
        jMethod,
        jCIF,
        ptr_to_jlong(result),
        ptr_to_jlong(args));

bail:
    JNFReleaseEnv(env, &threadWasAttached);

    if((*env)->ExceptionOccurred(env)){
        NSLog(@"Exception!");
        (*env)->ExceptionDescribe(env);
    }
    JNFReleaseEnv(env, &threadWasAttached);
}
