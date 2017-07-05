/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>

#include "jvm.h"
#include "jni.h"
#include "jni_util.h"

#include "jvm_symbols.h"
#include "sun_tracing_dtrace_JVM.h"

#ifdef __cplusplus
extern "C" {
#endif

static JvmSymbols* jvm_symbols = NULL;

static void initialize() {
    static int initialized = 0;
    if (initialized == 0) {
        jvm_symbols = lookupJvmSymbols();
        initialized = 1;
    }
}

/*
 * Class:     sun_tracing_dtrace_JVM
 * Method:    isSupported0
 * Signature: ()I
 */
JNIEXPORT jboolean JNICALL Java_sun_tracing_dtrace_JVM_isSupported0(
        JNIEnv* env, jclass cls) {
    initialize();
    if (jvm_symbols != NULL) {
        return jvm_symbols->IsSupported(env) ? JNI_TRUE : JNI_FALSE;
    } else {
        return JNI_FALSE;
    }
}

// Macros that cause an immediate return if we detect an exception
#define CHECK if ((*env)->ExceptionOccurred(env)) { return; }
#define CHECK_(x) if ((*env)->ExceptionOccurred(env)) { return x; }

static void readProbeData (
        JNIEnv* env, jobject probe, JVM_DTraceProbe* jvm_probe) {
    jclass clazz;
    jmethodID mid;
    jobject method;

    if (jvm_probe == NULL) {
        return; // just in case
    }

    clazz = (*env)->GetObjectClass(env, probe); CHECK

    mid = (*env)->GetMethodID(
        env, clazz, "getFunctionName", "()Ljava/lang/String;"); CHECK
    jvm_probe->function = (jstring)(*env)->CallObjectMethod(
        env, probe, mid); CHECK

    mid = (*env)->GetMethodID(
        env, clazz, "getProbeName", "()Ljava/lang/String;"); CHECK
    jvm_probe->name = (jstring)(*env)->CallObjectMethod(env, probe, mid); CHECK

    mid = (*env)->GetMethodID(
        env, clazz, "getMethod", "()Ljava/lang/reflect/Method;"); CHECK
    method = (*env)->CallObjectMethod(env, probe, mid); CHECK
    jvm_probe->method = (*env)->FromReflectedMethod(env, method); CHECK
}

static void readFieldInterfaceAttributes(
        char* annotationName, JNIEnv* env, jobject provider,
        JVM_DTraceInterfaceAttributes* attrs) {
    jobject result;
    jobject result_clazz;
    jclass provider_clazz;
    jclass annotation_clazz;
    jmethodID get;
    jmethodID enc;

    provider_clazz = (*env)->GetObjectClass(env, provider); CHECK
    annotation_clazz = (*env)->FindClass(env, annotationName); CHECK

    get = (*env)->GetMethodID(env, provider_clazz, "getNameStabilityFor",
        "(Ljava/lang/Class;)Lcom/sun/tracing/dtrace/StabilityLevel;"); CHECK
    result = (*env)->CallObjectMethod(
        env, provider, get, annotation_clazz); CHECK
    result_clazz = (*env)->GetObjectClass(env, result); CHECK
    enc = (*env)->GetMethodID(env, result_clazz, "getEncoding", "()I"); CHECK
    attrs->nameStability = (*env)->CallIntMethod(env, result, enc); CHECK

    get = (*env)->GetMethodID(env, provider_clazz, "getDataStabilityFor",
        "(Ljava/lang/Class;)Lcom/sun/tracing/dtrace/StabilityLevel;"); CHECK
    result = (*env)->CallObjectMethod(
        env, provider, get, annotation_clazz); CHECK
    result_clazz = (*env)->GetObjectClass(env, result); CHECK
    enc = (*env)->GetMethodID(env, result_clazz, "getEncoding", "()I"); CHECK
    attrs->dataStability = (*env)->CallIntMethod(env, result, enc); CHECK

    get = (*env)->GetMethodID(env, provider_clazz, "getDependencyClassFor",
        "(Ljava/lang/Class;)Lcom/sun/tracing/dtrace/DependencyClass;"); CHECK
    result = (*env)->CallObjectMethod(
        env, provider, get, annotation_clazz); CHECK
    result_clazz = (*env)->GetObjectClass(env, result); CHECK
    enc = (*env)->GetMethodID(env, result_clazz, "getEncoding", "()I"); CHECK
    attrs->dependencyClass = (*env)->CallIntMethod(env, result, enc); CHECK
}

static void readInterfaceAttributes(
        JNIEnv* env, jobject provider, JVM_DTraceProvider* jvm_provider) {
    readFieldInterfaceAttributes("com/sun/tracing/dtrace/ProviderAttributes",
        env, provider, &(jvm_provider->providerAttributes));
    readFieldInterfaceAttributes("com/sun/tracing/dtrace/ModuleAttributes",
        env, provider, &(jvm_provider->moduleAttributes));
    readFieldInterfaceAttributes("com/sun/tracing/dtrace/FunctionAttributes",
        env, provider, &(jvm_provider->functionAttributes));
    readFieldInterfaceAttributes("com/sun/tracing/dtrace/NameAttributes",
        env, provider, &(jvm_provider->nameAttributes));
    readFieldInterfaceAttributes("com/sun/tracing/dtrace/ArgsAttributes",
        env, provider, &(jvm_provider->argsAttributes));
}

static void readProviderData(
        JNIEnv* env, jobject provider, JVM_DTraceProvider* jvm_provider) {
    jmethodID mid;
    jobjectArray probes;
    jsize i;
    jclass clazz = (*env)->GetObjectClass(env, provider); CHECK
    mid = (*env)->GetMethodID(
        env, clazz, "getProbes", "()[Lsun/tracing/dtrace/DTraceProbe;"); CHECK
    probes = (jobjectArray)(*env)->CallObjectMethod(
        env, provider, mid); CHECK

    // Fill JVM structure, describing provider
    jvm_provider->probe_count = (*env)->GetArrayLength(env, probes); CHECK
    jvm_provider->probes = (JVM_DTraceProbe*)calloc(
        jvm_provider->probe_count, sizeof(*jvm_provider->probes));
    mid = (*env)->GetMethodID(
        env, clazz, "getProviderName", "()Ljava/lang/String;"); CHECK
    jvm_provider->name = (jstring)(*env)->CallObjectMethod(
        env, provider, mid); CHECK

    readInterfaceAttributes(env, provider, jvm_provider); CHECK

    for (i = 0; i < jvm_provider->probe_count; ++i) {
        jobject probe = (*env)->GetObjectArrayElement(env, probes, i); CHECK
        readProbeData(env, probe, &jvm_provider->probes[i]); CHECK
    }
}

/*
 * Class:     sun_tracing_dtrace_JVM
 * Method:    activate0
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_sun_tracing_dtrace_JVM_activate0(
        JNIEnv* env, jclass cls, jstring moduleName, jobjectArray providers) {
    jlong handle = 0;
    jsize num_providers;
    jsize i;
    JVM_DTraceProvider* jvm_providers;

    initialize();

    if (jvm_symbols == NULL) {
      return 0;
    }

    num_providers = (*env)->GetArrayLength(env, providers); CHECK_(0L)

    jvm_providers = (JVM_DTraceProvider*)calloc(
        num_providers, sizeof(*jvm_providers));

    for (i = 0; i < num_providers; ++i) {
        JVM_DTraceProvider* p = &(jvm_providers[i]);
        jobject provider = (*env)->GetObjectArrayElement(
            env, providers, i);
        readProviderData(env, provider, p);
    }

    handle = jvm_symbols->Activate(
        env, JVM_TRACING_DTRACE_VERSION, moduleName,
        num_providers, jvm_providers);

    for (i = 0; i < num_providers; ++i) {
        JVM_DTraceProvider* p = &(jvm_providers[i]);
        free(p->probes);
    }
    free(jvm_providers);

    return handle;
}

/*
 * Class:     sun_tracing_dtrace_JVM
 * Method:    dispose0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_sun_tracing_dtrace_JVM_dispose0(
        JNIEnv* env, jclass cls, jlong handle) {
    if (jvm_symbols != NULL && handle != 0) {
        jvm_symbols->Dispose(env, handle);
    }
}

/*
 * Class:     sun_tracing_dtrace_JVM
 * Method:    isEnabled0
 * Signature: (Ljava/lang/String;Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL Java_sun_tracing_dtrace_JVM_isEnabled0(
        JNIEnv* env, jclass cls, jobject method) {
    jmethodID mid;
    if (jvm_symbols != NULL && method != NULL) {
        mid = (*env)->FromReflectedMethod(env, method);
        return jvm_symbols->IsProbeEnabled(env, mid);
    }
    return JNI_FALSE;
}

/*
 * Class:     sun_tracing_dtrace_JVM
 * Method:    defineClass0
 * Signature: (Ljava/lang/ClassLoader;Ljava/lang/String;[BII)Ljava/lang/Class;
 *
 * The implementation of this native static method is a copy of that of
 * the native instance method Java_java_lang_ClassLoader_defineClass0()
 * with the implicit "this" parameter becoming the "loader" parameter.
 *
 * This code was cloned and modified from java_lang_reflect_Proxy
 */
JNIEXPORT jclass JNICALL
Java_sun_tracing_dtrace_JVM_defineClass0(
        JNIEnv *env, jclass ignore, jobject loader, jstring name, jbyteArray data,
        jint offset, jint length)
{
    jbyte *body;
    char *utfName;
    jclass result = 0;
    char buf[128];

    if (data == NULL) {
        return 0;
    }

    /* Work around 4153825. malloc crashes on Solaris when passed a
     * negative size.
     */
    if (length < 0) {
        return 0;
    }

    body = (jbyte *)malloc(length);

    if (body == 0) {
        return 0;
    }

    (*env)->GetByteArrayRegion(env, data, offset, length, body);

    if ((*env)->ExceptionOccurred(env))
        goto free_body;

    if (name != NULL) {
        int i;
        int len = (*env)->GetStringUTFLength(env, name);
        int unicode_len = (*env)->GetStringLength(env, name);
        if (len >= sizeof(buf)) {
            utfName = malloc(len + 1);
            if (utfName == NULL) {
                goto free_body;
            }
        } else {
            utfName = buf;
        }
        (*env)->GetStringUTFRegion(env, name, 0, unicode_len, utfName);

        // Convert '.' to '/' in the package name
        for (i = 0; i < unicode_len; ++i) {
            if (utfName[i] == '.') {
                utfName[i] = '/';
            }
        }
    } else {
        utfName = NULL;
    }

    result = (*env)->DefineClass(env, utfName, loader, body, length);

    if (utfName && utfName != buf)
        free(utfName);

 free_body:
    free(body);
    return result;
}

#ifdef __cplusplus
}
#endif
