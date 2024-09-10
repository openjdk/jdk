/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include <string.h>
#include <jvmti.h>
#include <jni.h>
#include <pthread.h>

static jvmtiEnv *jvmti;
static JavaVM *_jvm;
static JNIEnv *_rb_env;

#define BUFFER_SIZE 100000
static size_t ring_buffer[BUFFER_SIZE] = {0};
static volatile int ring_buffer_idx = 0;
static int reader_created = 0;

void *get_method_details(void *arg)
{
    jmethodID method = (jmethodID)arg;

    jclass method_class;
    char *class_name = NULL;

    jvmtiError err = JVMTI_ERROR_NONE;

    // For JVM 17, 21, 22 calling GetMethodDeclaringClass is enough.
    if ((err = jvmti->GetMethodDeclaringClass(method, &method_class)) == 0)
    {
        // JVM 8 needs this to crash
        jvmti->GetClassSignature(method_class, &class_name, NULL);
        jvmti->Deallocate((unsigned char *)class_name);
    }
    return NULL;
}

void *read_ringbuffer(void *arg)
{
    JNIEnv *env;
    _jvm->AttachCurrentThread((void **)&env, NULL);
    _rb_env = env;

    for (;;)
    {
        size_t id = ring_buffer[rand() % BUFFER_SIZE];
        if (id > 0)
        {
            get_method_details((void *)id);
        }
    }
    return NULL;
}

static void JNICALL ClassPrepareCallback(jvmtiEnv *jvmti_env,
                                         JNIEnv *jni_env,
                                         jthread thread,
                                         jclass klass)
{
    if (reader_created == 0)
    {
        pthread_t tid;
        pthread_create(&tid, NULL, read_ringbuffer, NULL);

        reader_created = 1;
    }

    // Get the list of methods
    jint method_count;
    jmethodID *methods;
    if (jvmti_env->GetClassMethods(klass, &method_count, &methods) == JVMTI_ERROR_NONE)
    {
        for (int i = 0; i < method_count; i++)
        {
            ring_buffer[ring_buffer_idx++] = (size_t)methods[i];
            ring_buffer_idx = ring_buffer_idx % BUFFER_SIZE;
        }
        jvmti_env->Deallocate((unsigned char *)methods);
    }
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved)
{
    jvmtiEventCallbacks callbacks;
    jvmtiError error;

    _jvm = jvm;

    if (jvm->GetEnv((void **)&jvmti, JVMTI_VERSION_1_0) != JNI_OK)
    {
        fprintf(stderr, "Unable to access JVMTI!\n");
        return JNI_ERR;
    }

    // Set up the event callbacks
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.ClassPrepare = &ClassPrepareCallback;

    // Register the callbacks
    error = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (error != JVMTI_ERROR_NONE)
    {
        fprintf(stderr, "Error setting event callbacks: %d\n", error);
        return JNI_ERR;
    }

    // Enable the ClassPrepare event
    error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_CLASS_PREPARE, NULL);
    if (error != JVMTI_ERROR_NONE)
    {
        fprintf(stderr, "Error enabling ClassPrepare event: %d\n", error);
        return JNI_ERR;
    }

    return JNI_OK;
}
