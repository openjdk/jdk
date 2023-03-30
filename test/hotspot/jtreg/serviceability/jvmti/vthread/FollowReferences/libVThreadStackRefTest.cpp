/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

//#include <string.h>
//#include <atomic>

//#include "jvmti.h"
//#include "jvmti_common.h"

#include <jvmti.h>
#include <cstdlib>
#include <cstring>

namespace {
jvmtiEnv *jvmti = nullptr;

void checkJvmti(int code, const char* message) {
	if (code != JVMTI_ERROR_NONE) {
		printf("Error %s: %d\n", message, code);
		abort();
	}
}

const int TAG_START = 100;

struct RefCounters {
    jint testClassCount;
	jint *count;
	jlong *threadId;
	
	RefCounters(): testClassCount(0), count(nullptr) {}
	
	void init(jint testClassCount) {
	    this->testClassCount = testClassCount;
		count = new jint[testClassCount];
		memset(count, 0, sizeof(count[0]) *  testClassCount);
		threadId = new jlong[testClassCount];
		memset(threadId, 0, sizeof(threadId[0]) *  testClassCount);
	}
} refCounters;

}

jint JNICALL testJvmtiHeapReferenceCallback(jvmtiHeapReferenceKind reference_kind, const jvmtiHeapReferenceInfo* reference_info,
    jlong class_tag, jlong referrer_class_tag, jlong size, jlong* tag_ptr, jlong* referrer_tag_ptr, jint length, void* user_data) {
    if (class_tag >= TAG_START) {
		jlong index = class_tag - TAG_START;
//        ((jint*)user_data)[class_tag - TAG_START]++;
	    switch (reference_kind) {
	    case JVMTI_HEAP_REFERENCE_STACK_LOCAL: {
			jvmtiHeapReferenceInfoStackLocal *stackInfo = (jvmtiHeapReferenceInfoStackLocal *)reference_info;
			refCounters.count[index]++;
			refCounters.threadId[index] = stackInfo->thread_id;
			printf("Stack local: index = %d, threadId = %d\n",
			       (int)index, (int)stackInfo->thread_id);
		}
        break;
	    case JVMTI_HEAP_REFERENCE_JNI_LOCAL: {
			jvmtiHeapReferenceInfoJniLocal *jniInfo = (jvmtiHeapReferenceInfoJniLocal *)reference_info;
			refCounters.count[index]++;
			refCounters.threadId[index] = jniInfo->thread_id;
			printf("JNI local: index = %d, threadId = %d\n",
				   (int)index, (int)jniInfo->thread_id);
		}
		break;
		default:
		    // unexpected ref.kind
    		printf("ERROR: unexpected ref_kind for class %d: %d\n",
        		   (int)index, (int)reference_kind);
	    }
    }
    return JVMTI_VISIT_OBJECTS;
}

extern "C" JNIEXPORT void JNICALL
Java_VThreadStackRefTest_test(JNIEnv* env, jclass clazz, jobjectArray classes) {
    jsize classesCount = env->GetArrayLength(classes);
    for (int i=0; i<classesCount; i++) {
        jvmti->SetTag(env->GetObjectArrayElement(classes, i), TAG_START + i);
    }
	refCounters.init(classesCount);
    jvmtiHeapCallbacks heapCallBacks;
    memset(&heapCallBacks, 0, sizeof(jvmtiHeapCallbacks));
    heapCallBacks.heap_reference_callback = testJvmtiHeapReferenceCallback;
    checkJvmti(jvmti->FollowReferences(0, nullptr, nullptr, &heapCallBacks, nullptr), "follow references");
}

extern "C" JNIEXPORT jint JNICALL
Java_VThreadStackRefTest_getRefCount(JNIEnv* env, jclass clazz, jint index) {
	return refCounters.count[index];
}

extern "C" JNIEXPORT jlong JNICALL
Java_VThreadStackRefTest_getRefThreadID(JNIEnv* env, jclass clazz, jint index) {
	return refCounters.threadId[index];
}

extern "C" JNIEXPORT jint JNICALL
Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
    if (vm->GetEnv(reinterpret_cast<void **>(&jvmti), JVMTI_VERSION) != JNI_OK || !jvmti) {
        printf("Could not initialize JVMTI\n");
        abort();
    }
    jvmtiCapabilities capabilities;
    memset(&capabilities, 0, sizeof(capabilities));
    capabilities.can_tag_objects = 1;
	//capabilities.can_support_virtual_threads = 1;
    checkJvmti(jvmti->AddCapabilities(&capabilities), "adding capabilities");
    return JVMTI_ERROR_NONE;
} 
