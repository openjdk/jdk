/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include <jvmti.h>
#include <stdio.h>
#include <string.h>

// set by Agent_OnLoad
static jvmtiEnv* jvmti = nullptr;

static const char testClassName[] = "RedefineRetransform$TestClass";

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

/*
 * Helper class for data exchange between RedefineClasses/RetransformClasses and
 * ClassFileLoadHook callback (saves class bytes to be passed to CFLH,
 * allows setting new class bytes to return from CFLH).
 * Callers create an instance on the stack, ClassFileLoadHook handler uses getInstance().
 */
class ClassFileLoadHookHelper {
    const char* mode;   // for logging only
    bool eventEnabled;
    JNIEnv* env;
    jbyteArray classBytes = nullptr;

    unsigned char* savedClassBytes = nullptr;
    jint savedClassBytesLen = 0;

    // single instance
    static ClassFileLoadHookHelper *instance;
public:
    ClassFileLoadHookHelper(const char* mode, JNIEnv* jni_env, jbyteArray hookClassBytes)
        : mode(mode), eventEnabled(false), env(jni_env), classBytes(nullptr),
        savedClassBytes(nullptr), savedClassBytesLen(0)
    {
        _log(">>%s\n", mode);
        if (hookClassBytes != nullptr) {
            classBytes = (jbyteArray)env->NewGlobalRef(hookClassBytes);
        }
    }

    ~ClassFileLoadHookHelper() {
        // cleanup on error
        stop();
        if (classBytes != nullptr) {
            env->DeleteGlobalRef(classBytes);
        }
        if (savedClassBytes != nullptr) {
            jvmti->Deallocate(savedClassBytes);
        }
        _log("<<%s\n", mode);
    }

    bool start() {
        instance = this;
        jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
        if (err != JVMTI_ERROR_NONE) {
            _log("%s: SetEventNotificationMode(JVMTI_ENABLE) error %d\n", mode, err);
            eventEnabled = true;
            return false;
        }
        return true;
    }

    void stop() {
        instance = nullptr;
        if (eventEnabled) {
            jvmtiError err = jvmti->SetEventNotificationMode(JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, nullptr);
            if (err != JVMTI_ERROR_NONE) {
                _log("%s: SetEventNotificationMode(JVMTI_DISABLE) error %d\n", mode, err);
                return;
            }
            eventEnabled = false;
        }
    }

    // valid only between start() and stop()
    static ClassFileLoadHookHelper* getInstance() {
        return instance;
    }

    bool getHookClassBytes(unsigned char** newClassBytes, jint* newLen) {
        if (classBytes != nullptr) {
            jsize len = env->GetArrayLength(classBytes);
            unsigned char* buf = nullptr;
            jvmtiError err = jvmti->Allocate(len, &buf);
            if (err != JVMTI_ERROR_NONE) {
                _log("ClassFileLoadHook: failed to allocate %ld bytes for new class bytes: %d", len, err);
                return false;
            }

            jbyte* arrayPtr = env->GetByteArrayElements(classBytes, nullptr);
            if (arrayPtr == nullptr) {
                _log("ClassFileLoadHook: failed to get array elements\n");
                jvmti->Deallocate(buf);
                return false;
            }

            memcpy(buf, arrayPtr, len);

            env->ReleaseByteArrayElements(classBytes, arrayPtr, JNI_ABORT);

            *newClassBytes = buf;
            *newLen = len;

            _log("  ClassFileLoadHook: set new class bytes\n");
        }
        return true;
    }

    void setSavedHookClassBytes(const unsigned char* bytes, jint len) {
        jvmtiError err = jvmti->Allocate(len, &savedClassBytes);
        if (err != JVMTI_ERROR_NONE) {
            _log("ClassFileLoadHook: failed to allocate %ld bytes for saved class bytes: %d", len, err);
            return;
        }
        memcpy(savedClassBytes, bytes, len);
        savedClassBytesLen = len;
    }

    jbyteArray getSavedHookClassBytes() {
        if (savedClassBytes == nullptr) {
            _log("%s: savedClassBytes is null\n", mode);
            return nullptr;
        }

        jbyteArray result = env->NewByteArray(savedClassBytesLen);
        if (result == nullptr) {
            _log("%s: NewByteArray(%ld) failed\n", mode, savedClassBytesLen);
        } else {
            jbyte* arrayPtr = env->GetByteArrayElements(result, nullptr);
            if (arrayPtr == nullptr) {
                _log("%s: Failed to get array elements\n", mode);
                result = nullptr;
            } else {
                memcpy(arrayPtr, savedClassBytes, savedClassBytesLen);
                env->ReleaseByteArrayElements(result, arrayPtr, 0);
            }
        }
        return result;
    }
};

ClassFileLoadHookHelper* ClassFileLoadHookHelper::instance = nullptr;


extern "C" {

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

        ClassFileLoadHookHelper* helper = ClassFileLoadHookHelper::getInstance();
        if (helper == nullptr) {
            _log("ClassFileLoadHook ERROR: helper instance is not initialized\n");
            return;
        }
        // save class bytes
        helper->setSavedHookClassBytes(class_data, class_data_len);
        // set new class bytes
        helper->getHookClassBytes(new_class_data, new_class_data_len);

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
    jvmtiError err = jvmti->AddCapabilities(&caps);
    if (err != JVMTI_ERROR_NONE) {
        _log("Failed to add capabilities: %d\n", err);
        return JNI_ERR;
    }

    jvmtiEventCallbacks eventCallbacks;
    memset(&eventCallbacks, 0, sizeof(eventCallbacks));
    eventCallbacks.ClassFileLoadHook = callbackClassFileLoadHook;
    err = jvmti->SetEventCallbacks(&eventCallbacks, sizeof(eventCallbacks));
    if (err != JVMTI_ERROR_NONE) {
        _log("Error setting event callbacks: %d\n", err);
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

    ClassFileLoadHookHelper helper("nRedefine", env, classLoadHookBytes);

    jsize len = env->GetArrayLength(classBytes);
    jbyte* arrayPtr = env->GetByteArrayElements(classBytes, nullptr);
    if (arrayPtr == nullptr) {
        _log("nRedefine: Failed to get array elements\n");
        return nullptr;
    }

    if (helper.start()) {
        jvmtiClassDefinition classDef;
        memset(&classDef, 0, sizeof(classDef));
        classDef.klass = testClass;
        classDef.class_byte_count = len;
        classDef.class_bytes = (unsigned char*)arrayPtr;

        jvmtiError err = jvmti->RedefineClasses(1, &classDef);

        if (err != JVMTI_ERROR_NONE) {
            _log("nRedefine: RedefineClasses error %d", err);
            // don't exit here, need to cleanup
        }
        helper.stop();
    }

    env->ReleaseByteArrayElements(classBytes, arrayPtr, JNI_ABORT);

    return helper.getSavedHookClassBytes();
}

JNIEXPORT jbyteArray JNICALL
Java_RedefineRetransform_nRetransform(JNIEnv* env, jclass klass, jclass testClass, jbyteArray classBytes) {

    ClassFileLoadHookHelper helper("nRetransform", env, classBytes);
    if (helper.start()) {
        jvmtiError err = jvmti->RetransformClasses(1, &testClass);
        if (err != JVMTI_ERROR_NONE) {
            _log("nRetransform: RetransformClasses error %d\n", err);
            // don't exit here, disable CFLH event
        }
        helper.stop();
    }
    return helper.getSavedHookClassBytes();
}

}
