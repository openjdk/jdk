/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
#include <string.h>
#include <stdlib.h>
#include "jvmti.h"
#include "jni.h"

#ifdef __cplusplus
extern "C" {
#endif


static jvmtiEnv *jvmti = nullptr;

// valid while a test is executed
static jobject testResultObject = nullptr;
static jclass testResultClass = nullptr;
// we log object values handling FieldModification event and this cause FieldAccess events are triggered.
// The flag to disable FieldAccess handling.
static bool disableAccessEvent = false;

static void reportError(const char *msg, int err) {
    printf("%s, error: %d\n", msg, err);
}

static void printJValue(const char *prefix, JNIEnv *jni_env, char signature_type, jvalue value) {
    // print new_value to ensure the value is valid
    // use String.valueOf(...) to get string representation
    /*
    Z boolean
    B byte
    C char
    S short
    I int
    J long
    F float
    D double
    L fully-qualified-class ;   fully-qualified-class
    [ type                      type[]
    */
    char signature[64] = {};
    if (signature_type == 'Q' || signature_type == 'L') {
        snprintf(signature, sizeof(signature), "(Ljava/lang/Object;)Ljava/lang/String;");
    } else {
        snprintf(signature, sizeof(signature), "(%c)Ljava/lang/String;", signature_type);
    }

    jclass clsString = jni_env->FindClass("java/lang/String");
    jmethodID mid = jni_env->GetStaticMethodID(clsString, "valueOf", signature);
    jstring objJStr = (jstring)jni_env->CallStaticObjectMethodA(clsString, mid, &value);

    const char* objStr = "UNKNOWN";
    if (objJStr != nullptr) {
        objStr = jni_env->GetStringUTFChars(objJStr, nullptr);
    }

    printf("    %s is: '%s'\n", prefix, objStr);
    fflush(0);

    if (objJStr != nullptr) {
        jni_env->ReleaseStringUTFChars(objJStr, objStr);
    }
}


// logs the notification and updates currentTestResult
static void handleNotification(jvmtiEnv *jvmti, JNIEnv *jni_env,
    jmethodID method,
    jobject object,
    jfieldID field,
    jclass field_klass,
    bool modified,
    jlocation location)
{
    jvmtiError err;
    char *name = nullptr;
    char *signature = nullptr;
    char *mname = nullptr;
    char *mgensig = nullptr;
    jclass methodClass = nullptr;
    char *csig = nullptr;

    if (testResultObject == nullptr) {
        // we are out of test
        return;
    }

    err = jvmti->GetFieldName(field_klass, field, &name, &signature, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        reportError("GetFieldName failed", err);
        return;
    }

    err = jvmti->GetMethodName(method, &mname, nullptr, &mgensig);
    if (err != JVMTI_ERROR_NONE) {
        reportError("GetMethodName failed", err);
        return;
    }

    err = jvmti->GetMethodDeclaringClass(method, &methodClass);
    if (err != JVMTI_ERROR_NONE) {
        reportError("GetMethodDeclaringClass failed", err);
        return;
    }

    err = jvmti->GetClassSignature(methodClass, &csig, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        reportError("GetClassSignature failed", err);
        return;
    }

    printf("  \"class: %s method: %s%s\" %s field: \"%s\" (type '%s'), location: %d\n",
        csig, mname, mgensig, modified ? "modified" : "accessed", name, signature, (int)location);

    // For FieldModification event print current value.
    // Note: this will cause FieldAccess event.
    if (modified) {
        jvalue curValue = {};
        switch (signature[0]) {
        case 'L':
        case 'Q':
            curValue.l = jni_env->GetObjectField(object, field); break;
        case 'Z':   // boolean
            curValue.z = jni_env->GetBooleanField(object, field); break;
        case 'B':   // byte
            curValue.b = jni_env->GetByteField(object, field); break;
        case 'C':   // char
            curValue.c = jni_env->GetCharField(object, field); break;
        case 'S':   // short
            curValue.s = jni_env->GetShortField(object, field); break;
        case 'I':   // int
            curValue.i = jni_env->GetIntField(object, field); break;
        case 'J':   // long
            curValue.j = jni_env->GetLongField(object, field); break;
        case 'F':   // float
            curValue.f = jni_env->GetFloatField(object, field); break;
        case 'D':   // double
            curValue.d = jni_env->GetDoubleField(object, field); break;
        default:
            printf("ERROR: unexpected signature: %s\n", signature);
            return;
        }
        printJValue("current value: ", jni_env, signature[0], curValue);
    }

    // set TestResult
    if (testResultObject != nullptr && testResultClass != nullptr) {
        jfieldID fieldID;
        // field names in TestResult are "<field_name>_access"/"<field_name>_modify"
        char *fieldName = (char *)malloc(strlen(name) + 16);
        strcpy(fieldName, name);
        strcat(fieldName, modified ? "_modify" : "_access");

        fieldID = jni_env->GetFieldID(testResultClass, fieldName, "Z");
        if (fieldID != nullptr) {
            jni_env->SetBooleanField(testResultObject, fieldID, JNI_TRUE);
        } else {
            // the field is not interesting for the test
        }
        // clear any possible exception
        jni_env->ExceptionClear();

        free(fieldName);
    }

    jvmti->Deallocate((unsigned char*)csig);
    jvmti->Deallocate((unsigned char*)mname);
    jvmti->Deallocate((unsigned char*)mgensig);
    jvmti->Deallocate((unsigned char*)name);
    jvmti->Deallocate((unsigned char*)signature);
}

static void JNICALL
onFieldAccess(jvmtiEnv *jvmti_env,
            JNIEnv* jni_env,
            jthread thread,
            jmethodID method,
            jlocation location,
            jclass field_klass,
            jobject object,
            jfieldID field)
{
    if (disableAccessEvent) {
        return;
    }
    handleNotification(jvmti_env, jni_env, method, object, field, field_klass, false, location);
}

static void JNICALL
onFieldModification(jvmtiEnv *jvmti_env,
            JNIEnv* jni_env,
            jthread thread,
            jmethodID method,
            jlocation location,
            jclass field_klass,
            jobject object,
            jfieldID field,
            char signature_type,
            jvalue new_value)
{
    disableAccessEvent = true;

    handleNotification(jvmti_env, jni_env, method, object, field, field_klass, true, location);

    printJValue("new value", jni_env, signature_type, new_value);

    disableAccessEvent = false;
}


JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *jvm, char *options, void *reserved)
{
    jvmtiError err;
    jvmtiCapabilities caps = {};
    jvmtiEventCallbacks callbacks = {};
    jint res = jvm->GetEnv((void **) &jvmti, JVMTI_VERSION_1_1);
    if (res != JNI_OK || jvmti == nullptr) {
        reportError("GetEnv failed", res);
        return JNI_ERR;
    }

    caps.can_generate_field_modification_events = 1;
    caps.can_generate_field_access_events = 1;
    caps.can_tag_objects = 1;
    err = jvmti->AddCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        reportError("Failed to set capabilities", err);
        return JNI_ERR;
    }

    callbacks.FieldModification = &onFieldModification;
    callbacks.FieldAccess = &onFieldAccess;

    err = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (err != JVMTI_ERROR_NONE) {
        reportError("Failed to set event callbacks", err);
        return JNI_ERR;
    }

    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_ACCESS, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        reportError("Failed to set access notifications", err);
        return JNI_ERR;
    }

    err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_FIELD_MODIFICATION, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        reportError("Failed to set modification notifications", err);
        return JNI_ERR;
    }
    setbuf(stdout, nullptr);
    return JNI_OK;
}


JNIEXPORT jboolean JNICALL
Java_FieldAccessModify_initWatchers(JNIEnv *env, jclass thisClass, jclass cls, jobject field)
{
    jfieldID fieldId;
    jvmtiError err;

    if (jvmti == nullptr) {
        reportError("jvmti is nullptr", 0);
        return JNI_FALSE;
    }

    fieldId = env->FromReflectedField(field);

    err = jvmti->SetFieldModificationWatch(cls, fieldId);
    if (err != JVMTI_ERROR_NONE) {
        reportError("SetFieldModificationWatch failed", err);
        return JNI_FALSE;
    }

    err = jvmti->SetFieldAccessWatch(cls, fieldId);
    if (err != JVMTI_ERROR_NONE) {
        reportError("SetFieldAccessWatch failed", err);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}


JNIEXPORT jboolean JNICALL
Java_FieldAccessModify_startTest(JNIEnv *env, jclass thisClass, jobject testResults)
{
    testResultObject = env->NewGlobalRef(testResults);
    testResultClass = (jclass)env->NewGlobalRef(env->GetObjectClass(testResultObject));

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_FieldAccessModify_stopTest(JNIEnv *env, jclass thisClass)
{
    if (testResultObject != nullptr) {
        env->DeleteGlobalRef(testResultObject);
        testResultObject = nullptr;
    }
    if (testResultClass != nullptr) {
        env->DeleteGlobalRef(testResultClass);
        testResultClass = nullptr;
    }
}


#ifdef __cplusplus
}
#endif

