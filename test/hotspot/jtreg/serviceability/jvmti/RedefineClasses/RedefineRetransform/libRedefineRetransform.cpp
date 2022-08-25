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
#include <jvmti.h>
#include <jni.h>
#include <string.h>


// set by Agent_OnLoad
static jvmtiEnv* jvmti = NULL;

static const char testClassName[] = "RedefineRetransform$TestClass";

// to redefine from ClassFileLoadHock callback
// set by caller:
static jbyteArray classLoadHookNewClassBytes = nullptr;
// set by ClassFileLoadHock callback:
static unsigned char* classLoadHookSavedClassBytes = nullptr;
static jint classLoadHookSavedClassBytesLen = 0;

extern "C" {

static void _log(const char* format, ...) {
    va_list args;
    va_start(args, format);
    vprintf(format, args);
    va_end(args);
    fflush(0);
}

static bool isTestClass(const char* name) {
    return name != nullptr && strcmp(name, testClassName) == 0;
}

JNIEXPORT void JNICALL
callbackClassFileLoadHook(jvmtiEnv *jvmti_env,
        JNIEnv* jni_env,
        jclass class_being_redefined,
        jobject loader,
        const char* name,
        jobject protection_domain,
        jint class_data_len,
        const unsigned char* class_data,
        jint* new_class_data_len,
        unsigned char** new_class_data) {
    if (isTestClass(name)) {
        _log(">>ClassFileLoadHook: %s, %ld bytes, ptr = %p\n", name, class_data_len, class_data);

        // save class bytes
        jvmtiError err = jvmti->Allocate(class_data_len, &classLoadHookSavedClassBytes);
        if (err != JVMTI_ERROR_NONE) {
            _log("ClassFileLoadHook: failed to allocate %ld bytes for saved class bytes: %d", class_data_len, err);
            return;
        }
        memcpy(classLoadHookSavedClassBytes, class_data, class_data_len);
        classLoadHookSavedClassBytesLen = class_data_len;

        // set new class bytes
        if (classLoadHookNewClassBytes != nullptr) {
            jsize len = jni_env->GetArrayLength(classLoadHookNewClassBytes);
            unsigned char* buf = nullptr;
            err = jvmti->Allocate(len, &buf);
            if (err != JVMTI_ERROR_NONE) {
                _log("ClassFileLoadHook: failed to allocate %ld bytes for new class bytes: %d", len, err);
                return;
            }

            jbyte* arrayPtr = jni_env->GetByteArrayElements(classLoadHookNewClassBytes, nullptr);
            if (arrayPtr == nullptr) {
                _log("ClassFileLoadHook: failed to get array elements\n");
                jvmti->Deallocate(buf);
                return;
            }

            memcpy(buf, arrayPtr, len);

            jni_env->ReleaseByteArrayElements(classLoadHookNewClassBytes, arrayPtr, JNI_ABORT);

            *new_class_data = buf;
            *new_class_data_len = len;

            _log("  ClassFileLoadHook: set new class bytes\n");
        }
        _log("<<ClassFileLoadHook\n");
    }
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM* jvm, char* options, void* reserved) {
    jint res = jvm->GetEnv((void **)&jvmti, JVMTI_VERSION_1_1);
    if (res != JNI_OK) {
        _log("Failed to get JVMTI interface: %ld\n", res);
        return JNI_ERR;
    }

    jvmtiCapabilities caps;
    memset(&caps, 0, sizeof(caps));

    caps.can_redefine_classes = 1;
    caps.can_retransform_classes = 1;
    jvmti->AddCapabilities(&caps);

    jvmtiEventCallbacks eventCallbacks;
    memset(&eventCallbacks, 0, sizeof(eventCallbacks));
    eventCallbacks.ClassFileLoadHook = callbackClassFileLoadHook;
    res = jvmti->SetEventCallbacks(&eventCallbacks, sizeof(eventCallbacks));
    if (res != JVMTI_ERROR_NONE) {
        _log("Error setting event callbacks: %ld\n", res);
        return JNI_ERR;
    }

    return JNI_OK;
}

JNIEXPORT void JNICALL
Agent_OnUnload(JavaVM* jvm) {
    return;
}


JNIEXPORT jbyteArray JNICALL
Java_RedefineRetransform_nRedefine(JNIEnv* env, jclass klass,
                                   jclass testClass, jbyteArray classBytes, jbyteArray classLoadHookBytes) {

    _log(">>nRedefine\n");
    jsize len = env->GetArrayLength(classBytes);
    jbyte* arrayPtr = env->GetByteArrayElements(classBytes, nullptr);
    if (arrayPtr == nullptr) {
        _log("nRedefine: Failed to get array elements\n");
        return nullptr;
    }
    if (classLoadHookBytes != nullptr) {
        classLoadHookNewClassBytes = (jbyteArray)env->NewGlobalRef(classLoadHookBytes);
    }


    jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        env->ReleaseByteArrayElements(classBytes, arrayPtr, JNI_ABORT);
        _log("nRedefine: SetEventNotificationMode(JVMTI_ENABLE) error %d\n", err);
        return nullptr;
    }

    jvmtiClassDefinition classDef;
    memset(&classDef, 0, sizeof(classDef));
    classDef.klass = testClass;
    classDef.class_byte_count = len;
    classDef.class_bytes = (unsigned char *)arrayPtr;

    jvmtiError err2 = jvmti->RedefineClasses(1, &classDef);

    if (err2 != JVMTI_ERROR_NONE) {
        _log("nRedefine: RedefineClasses error %d", err2);
        // don't exit here, need to cleanup
    }

    err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        _log("nRedefine: SetEventNotificationMode(JVMTI_DISABLE) error %d\n", err);
    }

    env->ReleaseByteArrayElements(classBytes, arrayPtr, JNI_ABORT);

    if (classLoadHookBytes != nullptr) {
        env->DeleteGlobalRef(classLoadHookNewClassBytes);
        classLoadHookNewClassBytes = nullptr;
    }

    if (err != JVMTI_ERROR_NONE || err2 != JVMTI_ERROR_NONE) {
        return nullptr;
    }

    if (classLoadHookSavedClassBytes == nullptr) {
        _log("nRedefine: classLoadHookSavedClassBytes is NULL\n");
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(classLoadHookSavedClassBytesLen);
    if (result == nullptr) {
        _log("nRedefine: NewByteArray(%ld) failed\n", classLoadHookSavedClassBytesLen);
    } else {
        jbyte* arrayPtr = env->GetByteArrayElements(result, nullptr);
        if (arrayPtr == nullptr) {
            _log("nRedefine: Failed to get array elements\n");
            result = nullptr;
        } else {
            memcpy(arrayPtr, classLoadHookSavedClassBytes, classLoadHookSavedClassBytesLen);
            env->ReleaseByteArrayElements(result, arrayPtr, JNI_COMMIT);
        }
    }

    jvmti->Deallocate(classLoadHookSavedClassBytes);
    classLoadHookSavedClassBytes = nullptr;

    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_RedefineRetransform_nRetransform(JNIEnv* env, jclass klass, jclass testClass, jbyteArray classBytes) {

    _log(">>nRetransform\n");
    if (classBytes != nullptr) {
        classLoadHookNewClassBytes = (jbyteArray)env->NewGlobalRef(classBytes);
    }

    jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        _log("nRetransform: SetEventNotificationMode(JVMTI_ENABLE) error %d\n", err);
        return nullptr;
    }

    jvmtiError err2 = jvmti->RetransformClasses(1, &testClass);
    if (err2 != JVMTI_ERROR_NONE) {
        _log("nRetransform: RetransformClasses error %d\n", err2);
        // don't exit here, disable CFLH event
    }

    err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
    if (err != JVMTI_ERROR_NONE) {
        _log("nRetransform: SetEventNotificationMode(JVMTI_DISABLE) error %d\n", err);
    }

    if (classBytes != nullptr) {
        env->DeleteGlobalRef(classLoadHookNewClassBytes);
        classLoadHookNewClassBytes = nullptr;
    }

    if (err != JVMTI_ERROR_NONE || err2 != JVMTI_ERROR_NONE) {
        return nullptr;
    }

    if (classLoadHookSavedClassBytes == nullptr) {
        _log("nRetransform: classLoadHookSavedClassBytes is NULL\n");
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(classLoadHookSavedClassBytesLen);
    if (result == nullptr) {
        _log("nRetransform: NewByteArray(%ld) failed\n", classLoadHookSavedClassBytesLen);
    } else {
        jbyte* arrayPtr = env->GetByteArrayElements(result, nullptr);
        if (arrayPtr == nullptr) {
            _log("nRetransform: Failed to get array elements\n");
            result = nullptr;
        }
        else {
            memcpy(arrayPtr, classLoadHookSavedClassBytes, classLoadHookSavedClassBytesLen);
            env->ReleaseByteArrayElements(result, arrayPtr, JNI_COMMIT);
        }
    }

    jvmti->Deallocate(classLoadHookSavedClassBytes);
    classLoadHookSavedClassBytes = nullptr;

    return result;
}

}
