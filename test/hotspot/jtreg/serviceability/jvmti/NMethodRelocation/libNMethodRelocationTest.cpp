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

#include <atomic>
#include <inttypes.h>
#include "jvmti_common.hpp"

extern "C" {

// Track nmethod addresses for LOAD and UNLOAD events
static const void* first_load_addr      = nullptr;
static const void* second_load_addr     = nullptr;
static const void* first_unload_addr    = nullptr;
static const void* second_unload_addr   = nullptr;

// Keep track of test completion
static std::atomic<bool> should_exit{false};

/**
 * Callback for COMPILED_METHOD_LOAD event.
 */
JNIEXPORT void JNICALL
callbackCompiledMethodLoad(jvmtiEnv* jvmti, jmethodID method,
                            jint code_size, const void* code_addr,
                            jint map_length, const jvmtiAddrLocationMap* map,
                            const void* compile_info) {

    // Only track events for "compiledMethod"
    char* name = get_method_name(jvmti, method);
    if (strcmp(name, "compiledMethod") != 0) {
        return;
    }

    LOG("<COMPILED_METHOD_LOAD>:   name: %s, code: 0x%016" PRIxPTR "\n", name, (uintptr_t)code_addr);

    if (first_load_addr == nullptr) {
        first_load_addr = code_addr;
    } else if (second_load_addr == nullptr) {
        second_load_addr = code_addr;

        // Verify that the addresses are different
        if (first_load_addr == second_load_addr) {
            fatal("Load events for 'compiledMethod' are expected to use different addresses");
        }
    } else {
        fatal("Received too many load events for 'compiledMethod'");
    }
}

/**
 * Callback for COMPILED_METHOD_UNLOAD event.
 */
JNIEXPORT void JNICALL
callbackCompiledMethodUnload(jvmtiEnv* jvmti, jmethodID method,
                             const void* code_addr) {

    // Only track events for "compiledMethod"
    char* name = get_method_name(jvmti, method);
    if (strcmp(name, "compiledMethod") != 0) {
        return;
    }

    LOG("<COMPILED_METHOD_UNLOAD>:   name: %s, code: 0x%016" PRIxPTR "\n", name, (uintptr_t)code_addr);

    // Validate both loads have occurred
    if (first_load_addr == nullptr || second_load_addr == nullptr) {
        fatal("UNLOAD event for 'compiledMethod' occurred before both LOAD events");
    }

    if (first_unload_addr == nullptr) {
        first_unload_addr = code_addr;
    } else if (second_unload_addr == nullptr) {
        second_unload_addr = code_addr;

        // Verify that the addresses are different
        if (first_unload_addr == second_unload_addr) {
            fatal("Unload events for 'compiledMethod' are expected to use different addresses");
        }

        // LOAD and UNLOAD events should report the same two addresses, but the order of
        // the UNLOADs is not guaranteed, since the GC may unload either nmethod first.
        if ((first_load_addr == first_unload_addr  && second_load_addr == second_unload_addr) ||
            (first_load_addr == second_unload_addr && second_load_addr == first_unload_addr)) {

            // Update should_exit to signal test completion
            should_exit.store(true);
        } else {
            fatal("Address mismatch for 'compiledMethod' events");
        }
    } else {
        fatal("Received too many unload events for 'compiledMethod'");
    }
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
    jvmtiEnv* jvmti = nullptr;

    if (jvm->GetEnv((void **)&jvmti, JVMTI_VERSION_1_0) != JNI_OK) {
        LOG("Unable to access JVMTI!\n");
        return JNI_ERR;
    }

    // Add required capabilities
    jvmtiCapabilities caps;
    memset(&caps, 0, sizeof(caps));
    caps.can_generate_compiled_method_load_events = 1;
    jvmtiError error = jvmti->AddCapabilities(&caps);
    check_jvmti_error(error, "Unable to add capabilities");

    // Set event callbacks
    jvmtiEventCallbacks eventCallbacks;
    memset(&eventCallbacks, 0, sizeof(eventCallbacks));
    eventCallbacks.CompiledMethodLoad = callbackCompiledMethodLoad;
    eventCallbacks.CompiledMethodUnload = callbackCompiledMethodUnload;
    error = jvmti->SetEventCallbacks(&eventCallbacks, sizeof(eventCallbacks));
    check_jvmti_error(error, "Unable to set event callbacks");

    // Enable events
    error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_COMPILED_METHOD_LOAD, nullptr);
    check_jvmti_error(error, "Unable to enable COMPILED_METHOD_LOAD event");

    error = jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_COMPILED_METHOD_UNLOAD, nullptr);
    check_jvmti_error(error, "Unable to enable COMPILED_METHOD_UNLOAD event");

    return JNI_OK;
}

JNIEXPORT jboolean JNICALL
Java_NMethodRelocationTest_shouldExit(JNIEnv *env, jclass cls) {
  return should_exit.load();
}

}
