/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include <jvmti.h>
#include "agent_common.h"
#include "jni_tools.h"
#include "jvmti_tools.h"
#include "jvmti_FollowRefObjects.h"

extern "C" {

/* ============================================================================= */

static jlong g_timeout = 0;

#define JAVA_LANG_STRING_CLASS_NAME "java/lang/String"
#define JAVA_IO_SERIALIZABLE_CLASS_NAME "java/io/Serializable"
#define JAVA_UTIL_CALENDAR_CLASS_NAME "java/util/Calendar"


/* ============================================================================= */

static void verifyReturnCodes(JNIEnv* jni, jvmtiEnv* jvmti)
{
    jvmtiError retCode;
    jlong tag;
    jvmtiHeapCallbacks emptyHeapCallbacks;

    NSK_DISPLAY0("FollowReferences: Invalid class:");

    retCode = jvmti->FollowReferences((jint) 0,                 /* heap filter */
                                      (jclass) &g_wrongHeapCallbacks ,   /* invalid class, but valid memory address */
                                      nullptr,                     /* inital object */
                                      &g_wrongHeapCallbacks,
                                      (const void *) &g_fakeUserData);

    if (!NSK_VERIFY(retCode == JVMTI_ERROR_INVALID_CLASS)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("FollowReferences: Invalid initial object:");
    // When FollowReferences() is called with an invalid initial object
    // the behaviour according to the jvmti spec is optional.
    // It may return JVMTI_ERROR_INVALID_OBJECT and not follow any references.
    // Or it may treat the object as nullptr, and follow all references.
    //
    // We will accept both behaviours. We use empty callbacks since the existing
    // callback marks the test as failed.

    emptyHeapCallbacks.heap_iteration_callback = nullptr;
    emptyHeapCallbacks.heap_reference_callback = nullptr;
    emptyHeapCallbacks.primitive_field_callback = nullptr;
    emptyHeapCallbacks.array_primitive_value_callback = nullptr;
    emptyHeapCallbacks.string_primitive_value_callback = nullptr;
    retCode = jvmti->FollowReferences((jint) 0,               // heap filter
                                      nullptr,                   // class
                                      (jobject) &g_wrongHeapCallbacks,  // invalid inital object
                                      &emptyHeapCallbacks,    // No callbacks
                                      (const void *) &g_fakeUserData);

    // Accept both JVMTI_ERROR_INVALID_OBJECT and JVMTI_ERROR_NONE
    if (!NSK_VERIFY(retCode == JVMTI_ERROR_INVALID_OBJECT || retCode == JVMTI_ERROR_NONE)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("FollowReferences: Invalid callbacks:");

    retCode = jvmti->FollowReferences((jint) 0,     /* heap filter */
                                      nullptr,         /* class */
                                      nullptr,         /* inital object */
                                      nullptr,
                                      (const void *) &g_fakeUserData);

    if (!NSK_VERIFY(retCode == JVMTI_ERROR_NULL_POINTER)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("GetTag: Invalid object:");

    retCode = jvmti->GetTag((jobject) &g_wrongHeapCallbacks,  /* invalid inital object */
                            &tag);

    if (!NSK_VERIFY(retCode == JVMTI_ERROR_INVALID_OBJECT)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("GetTag: null object pointer:");

    retCode = jvmti->GetTag(nullptr, &tag);

    if (!NSK_VERIFY(retCode == JVMTI_ERROR_INVALID_OBJECT)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("GetTag: null tag pointer:");

    retCode = jvmti->GetTag((jobject) &g_wrongHeapCallbacks, nullptr);

    if (!NSK_VERIFY(retCode == JVMTI_ERROR_NULL_POINTER)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("SetTag: Invalid object:");

    tag = 1;
    retCode = jvmti->SetTag((jobject) &g_wrongHeapCallbacks,  /* invalid inital object */
                            tag);

    if (!NSK_VERIFY(retCode == JVMTI_ERROR_INVALID_OBJECT)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("SetTag: null object pointer:");

    retCode = jvmti->GetTag(nullptr, &tag);

    if (!NSK_VERIFY(retCode == JVMTI_ERROR_INVALID_OBJECT)) {
        nsk_jvmti_setFailStatus();
    }

    NSK_DISPLAY0("GetTag: null tag pointer:");

} /* verifyReturnCodes */

/* ============================================================================= */

static void checkNoObjIterated(JNIEnv* jni, jvmtiEnv* jvmti, const char * szClassName)
{
    jvmtiError retCode;
    jclass klass;

    NSK_DISPLAY1("Verify, that no objects are returned if initial object is %s", szClassName);
    if (!NSK_JNI_VERIFY(jni, (klass = jni->FindClass(szClassName)) != nullptr)) {
        nsk_jvmti_setFailStatus();
        return;
    }

    retCode = jvmti->FollowReferences((jint) 0,     /* heap filter */
                                      klass, /* class */
                                      nullptr,         /* inital object */
                                      &g_wrongHeapCallbacks,
                                      (const void *) &g_fakeUserData);

    if (!NSK_VERIFY(retCode == JVMTI_ERROR_NONE)) {
        nsk_jvmti_setFailStatus();
    }

} /* checkNoObjIterated */

/* ============================================================================= */

/** Agent algorithm. */

static void JNICALL agentProc(jvmtiEnv* jvmti, JNIEnv* jni, void* arg)
{
    NSK_DISPLAY0("Call FollowReferences() with invalid arguments and check return codes");

    verifyReturnCodes(jni, jvmti);
    checkNoObjIterated(jni, jvmti, JAVA_IO_SERIALIZABLE_CLASS_NAME);
    checkNoObjIterated(jni, jvmti, JAVA_UTIL_CALENDAR_CLASS_NAME);

    NSK_DISPLAY0("Let debugee to finish");
    fflush(0);

    if (!NSK_VERIFY(nsk_jvmti_waitForSync(g_timeout))) {
        return;
    }

    if (!NSK_VERIFY(nsk_jvmti_resumeSync())) {
        return;
    }

} /* agentProc */


/* ============================================================================= */

/** Agent library initialization. */

#ifdef STATIC_BUILD
JNIEXPORT jint JNICALL Agent_OnLoad_followref006(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNICALL Agent_OnAttach_followref006(JavaVM *jvm, char *options, void *reserved) {
    return Agent_Initialize(jvm, options, reserved);
}
JNIEXPORT jint JNI_OnLoad_followref006(JavaVM *jvm, char *options, void *reserved) {
    return JNI_VERSION_1_8;
}
#endif
jint  Agent_Initialize(JavaVM *jvm, char *options, void *reserved)
{
    jvmtiEnv* jvmti = nullptr;

    if (!NSK_VERIFY(nsk_jvmti_parseOptions(options))) {
        return JNI_ERR;
    }

    g_timeout = nsk_jvmti_getWaitTime() * 60 * 1000;

    if (!NSK_VERIFY((jvmti = nsk_jvmti_createJVMTIEnv(jvm, reserved)) != nullptr)) {
        return JNI_ERR;
    }

    jvmti_FollowRefObject_init();

    {
        jvmtiCapabilities caps;

        memset(&caps, 0, sizeof(caps));
        caps.can_tag_objects = 1;
        if (!NSK_JVMTI_VERIFY(jvmti->AddCapabilities(&caps))) {
            return JNI_ERR;
        }
    }

    if (!NSK_VERIFY(nsk_jvmti_setAgentProc(agentProc, nullptr))) {
        return JNI_ERR;
    }

    return JNI_OK;

} /* Agent_OnLoad */


/* ============================================================================= */

}
