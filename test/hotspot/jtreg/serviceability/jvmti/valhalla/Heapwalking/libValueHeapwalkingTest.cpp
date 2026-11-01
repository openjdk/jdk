/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <jvmti.h>
#include "jvmti_common.hpp"

static jvmtiEnv *jvmti = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_ValueHeapwalkingTest_setTag(JNIEnv* jni_env, jclass clazz, jobject object, jlong tag) {
  jvmtiError err = jvmti->SetTag(object, tag);
  check_jvmti_error(err, "could not set tag");
}

extern "C" JNIEXPORT jlong JNICALL
Java_ValueHeapwalkingTest_getTag(JNIEnv* jni_env, jclass clazz, jobject object) {
  jlong tag;
  check_jvmti_error(jvmti->GetTag(object, &tag), "could not get tag");
  return tag;
}

const int TAG_VALUE_CLASS = 1;
const int TAG_VALUE2_CLASS = 2;
const int TAG_HOLDER_CLASS = 3;
const int TAG_VALUE_ARRAY = 4;
const int TAG_VALUE3_ARRAY = 5;
const int MAX_TAG = 5;
const int START_TAG = 10; // start value for tagging objects

static const char* tag_str(jlong tag) {
  switch (tag) {
  case 0: return "None";
  case TAG_VALUE_CLASS: return "Value class";
  case TAG_VALUE2_CLASS: return "Value2 class";
  case TAG_HOLDER_CLASS: return "ValueHolder class";
  case TAG_VALUE_ARRAY: return "Value[] object";
  case TAG_VALUE3_ARRAY: return "Value2[] object";
  }
  return "Unknown";
}

struct Callback_Data  {
  // Updated by heap_iteration_callback.
  jint counters[MAX_TAG + 1];
  // Updated by heap_reference_callback.
  jint ref_counters[MAX_TAG + 1][MAX_TAG + 1];
  // Updated by primitive_field_callback.
  jint primitive_counters[MAX_TAG + 1];
  jlong tag_counter;
};

static Callback_Data callbackData;

extern "C" JNIEXPORT void JNICALL
Java_ValueHeapwalkingTest_reset(JNIEnv* jni_env, jclass clazz) {
  memset(&callbackData, 0, sizeof(callbackData));
  callbackData.tag_counter = START_TAG;
}

extern "C" JNIEXPORT jint JNICALL
Java_ValueHeapwalkingTest_count(JNIEnv* jni_env, jclass clazz, jint tag) {
  return callbackData.counters[tag];
}

extern "C" JNIEXPORT jint JNICALL
Java_ValueHeapwalkingTest_refCount(JNIEnv* jni_env, jclass clazz, jint fromTag, jint toTag) {
  return callbackData.ref_counters[fromTag][toTag];
}

extern "C" JNIEXPORT jint JNICALL
Java_ValueHeapwalkingTest_primitiveFieldCount(JNIEnv* jni_env, jclass clazz, jint tag) {
  return callbackData.primitive_counters[tag];
}

extern "C" JNIEXPORT jlong JNICALL
Java_ValueHeapwalkingTest_getMaxTag(JNIEnv* jni_env, jclass clazz) {
  return callbackData.tag_counter;
}

static jlong safe_deref(jlong* ref) {
    return ref == nullptr ? 0 : *ref;
}

static jint JNICALL
heap_iteration_callback(jlong class_tag,
                        jlong size,
                        jlong* tag_ptr,
                        jint length,
                        void* user_data) {
  Callback_Data* data = (Callback_Data*)user_data;

  if (class_tag != 0 && class_tag <= MAX_TAG) {
    data->counters[class_tag]++;
    printf("heap_iteration_callback: class_tag = %d (%s), tag = %d (%s), length = %d\n",
           (int)class_tag, tag_str(class_tag), (int)*tag_ptr, tag_str(*tag_ptr), length);
    fflush(nullptr);
  }
  return 0;
}

static jint JNICALL
heap_reference_callback(jvmtiHeapReferenceKind reference_kind,
                        const jvmtiHeapReferenceInfo* reference_info,
                        jlong class_tag,
                        jlong referrer_class_tag,
                        jlong size,
                        jlong* tag_ptr,
                        jlong* referrer_tag_ptr,
                        jint length,
                        void* user_data) {
  Callback_Data* data = (Callback_Data*)user_data;

  jlong tag = class_tag;
  if (tag == 0 && *tag_ptr != 0 && *tag_ptr <= MAX_TAG) {
    tag = *tag_ptr;
  }
  jlong referrer_tag = referrer_class_tag;
  if (referrer_tag == 0 && safe_deref(referrer_tag_ptr) != 0 && safe_deref(referrer_tag_ptr) <= MAX_TAG) {
    referrer_tag = *referrer_tag_ptr;
  }

  if (tag != 0 && referrer_tag != 0) {
    // For testing we count only JVMTI_HEAP_REFERENCE_FIELD and JVMTI_HEAP_REFERENCE_ARRAY_ELEMENT references.
    if (reference_kind == JVMTI_HEAP_REFERENCE_FIELD || reference_kind == JVMTI_HEAP_REFERENCE_ARRAY_ELEMENT) {
      data->ref_counters[referrer_tag][tag]++;
    }

    jlong cur_tag = *tag_ptr;
    char new_tag_str[64] = {};
    if (*tag_ptr == 0) { // i.e. class_tag != 0, but the object is untagged
      *tag_ptr = ++data->tag_counter;
      snprintf(new_tag_str, sizeof(new_tag_str), ", set tag to %d", (int)*tag_ptr);
    }
    printf("heap_reference_callback: kind = %d, class_tag = %d (%s), tag = %d (%s), referrer_tag = %d (%s) %s\n",
           (int)reference_kind, (int)class_tag, tag_str(class_tag), (int)cur_tag, tag_str(*tag_ptr),
           (int)referrer_tag, tag_str(referrer_tag), new_tag_str);
    fflush(nullptr);
  }

  return JVMTI_VISIT_OBJECTS;
}

static jint JNICALL
primitive_field_callback(jvmtiHeapReferenceKind kind,
                         const jvmtiHeapReferenceInfo* info,
                         jlong object_class_tag,
                         jlong* object_tag_ptr,
                         jvalue value,
                         jvmtiPrimitiveType value_type,
                         void* user_data) {
  Callback_Data* data = (Callback_Data*)user_data;
  if (object_class_tag != 0) {
    char value_str[64] = {};
    switch (value_type) {
    case JVMTI_PRIMITIVE_TYPE_BOOLEAN: snprintf(value_str, sizeof(value_str), "(boolean) %s", value.z ? "true" : "false"); break;
    case JVMTI_PRIMITIVE_TYPE_BYTE:    snprintf(value_str, sizeof(value_str), "(byte) %d", value.b); break;
    case JVMTI_PRIMITIVE_TYPE_CHAR:    snprintf(value_str, sizeof(value_str), "(char) %c", value.c); break;
    case JVMTI_PRIMITIVE_TYPE_SHORT:   snprintf(value_str, sizeof(value_str), "(short): %d", value.s); break;
    case JVMTI_PRIMITIVE_TYPE_INT:     snprintf(value_str, sizeof(value_str), "(int): %d", value.i); break;
    case JVMTI_PRIMITIVE_TYPE_LONG:    snprintf(value_str, sizeof(value_str), "(long): %lld", (long long)value.j); break;
    case JVMTI_PRIMITIVE_TYPE_FLOAT:   snprintf(value_str, sizeof(value_str), "(float): %f", value.f); break;
    case JVMTI_PRIMITIVE_TYPE_DOUBLE:  snprintf(value_str, sizeof(value_str), "(double): %f", value.d);  break;
    default: snprintf(value_str, sizeof(value_str), "invalid_type %d (%c)", (int)value_type, (char)value_type);
    }

    if (object_class_tag != 0 && object_class_tag <= MAX_TAG) {
      data->primitive_counters[object_class_tag]++;
      if (*object_tag_ptr != 0) {
        *object_tag_ptr = *object_tag_ptr;
      }
    }

    printf("primitive_field_callback: kind = %d, class_tag = %d (%s), tag = %d (%s), value = %s\n",
           (int)kind, (int)object_class_tag, tag_str(object_class_tag),
           (int)*object_tag_ptr, tag_str(*object_tag_ptr), value_str);
    fflush(nullptr);
  }
  return 0;
}

static jint JNICALL
array_primitive_value_callback(jlong class_tag,
                               jlong size,
                               jlong* tag_ptr,
                               jint element_count,
                               jvmtiPrimitiveType element_type,
                               const void* elements,
                               void* user_data) {
  Callback_Data* data = (Callback_Data*)user_data;
  if (class_tag != 0 || *tag_ptr != 0) {
    printf("array_primitive_value_callback: class_tag = %d (%s), tag = %d (%s), element_count = %d, element_type = %c\n",
           (int)class_tag, tag_str(class_tag), (int)*tag_ptr, tag_str(*tag_ptr), element_count, (char)element_type);
    fflush(nullptr);
  }
  return 0;
}

static jint JNICALL
string_primitive_value_callback(jlong class_tag,
                                jlong size,
                                jlong* tag_ptr,
                                const jchar* value,
                                jint value_length,
                                void* user_data) {
  Callback_Data* data = (Callback_Data*)user_data;
  if (class_tag != 0 || *tag_ptr != 0) {
    jchar value_copy[1024] = {}; // fills with 0
    if (value_length > 1023) {
      value_length = 1023;
    }
    memcpy(value_copy, value, value_length * sizeof(jchar));
    printf("string_primitive_value_callback: class_tag = %d (%s), tag = %d (%s), value=\"%ls\"\n",
           (int)class_tag, tag_str(class_tag), (int)*tag_ptr, tag_str(*tag_ptr), (wchar_t*)value_copy);
    fflush(nullptr);
  }
  return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_ValueHeapwalkingTest_followReferences(JNIEnv* jni_env, jclass clazz) {
  jvmtiHeapCallbacks callbacks = {};
  callbacks.heap_iteration_callback = heap_iteration_callback;
  callbacks.heap_reference_callback = heap_reference_callback;
  callbacks.primitive_field_callback = primitive_field_callback;
  callbacks.array_primitive_value_callback = array_primitive_value_callback;
  callbacks.string_primitive_value_callback = string_primitive_value_callback;

  jvmtiError err = jvmti->FollowReferences(0 /* filter nothing */,
                                           nullptr /* no class filter */,
                                           nullptr /* no initial object, follow roots */,
                                           &callbacks,
                                           &callbackData);
  check_jvmti_error(err, "FollowReferences failed");
}

extern "C" JNIEXPORT void JNICALL
Java_ValueHeapwalkingTest_iterateThroughHeap(JNIEnv* jni_env, jclass clazz) {
  jvmtiHeapCallbacks callbacks = {};
  callbacks.heap_iteration_callback = heap_iteration_callback;
  callbacks.heap_reference_callback = heap_reference_callback;
  callbacks.primitive_field_callback = primitive_field_callback;
  callbacks.array_primitive_value_callback = array_primitive_value_callback;
  callbacks.string_primitive_value_callback = string_primitive_value_callback;

  jvmtiError err = jvmti->IterateThroughHeap(0 /* filter nothing */,
                                             nullptr /* no class filter */,
                                             &callbacks,
                                             &callbackData);
  check_jvmti_error(err, "IterateThroughHeap failed");
}

extern "C" JNIEXPORT jint JNICALL
Java_ValueHeapwalkingTest_getObjectWithTags(JNIEnv* jni_env, jclass clazz, jlong minTag, jlong maxTag, jobjectArray objects, jlongArray tags) {
  jsize len = jni_env->GetArrayLength(objects);

  jint tag_count = (jint)(maxTag - minTag + 1);
  jlong* scan_tags = nullptr;
  check_jvmti_error(jvmti->Allocate(tag_count * sizeof(jlong), (unsigned char**)&scan_tags),
                    "Allocate failed");

  for (jlong i = 0; i < tag_count; i++) {
      scan_tags[i] = i + minTag;
  }

  jint count = 0;
  jobject* object_result = nullptr;
  jlong* tag_result = nullptr;

  check_jvmti_error(jvmti->GetObjectsWithTags(tag_count, scan_tags, &count, &object_result, &tag_result),
                    "GetObjectsWithTags failed");

  if (count > len) {
    printf("GetObjectsWithTags returned too many entries: %d (object length is %d)\n", count, (int)len);
    fflush(nullptr);
    abort();
  }

  for (jint i = 0; i < count; i++) {
    jni_env->SetObjectArrayElement(objects, i, object_result[i]);
  }
  jni_env->SetLongArrayRegion(tags, 0, count, tag_result);

  jvmti->Deallocate((unsigned char*)scan_tags);
  jvmti->Deallocate((unsigned char*)object_result);
  jvmti->Deallocate((unsigned char*)tag_result);

  return count;
}

extern "C" JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved) {
  if (vm->GetEnv(reinterpret_cast<void **>(&jvmti), JVMTI_VERSION) != JNI_OK || !jvmti) {
    LOG("Could not initialize JVMTI\n");
    abort();
  }
  jvmtiCapabilities capabilities;
  memset(&capabilities, 0, sizeof(capabilities));
  capabilities.can_tag_objects = 1;
  check_jvmti_error(jvmti->AddCapabilities(&capabilities), "adding capabilities");
  return JVMTI_ERROR_NONE;
}

