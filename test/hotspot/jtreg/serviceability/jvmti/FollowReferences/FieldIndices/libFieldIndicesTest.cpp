/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include <stdint.h>
#include "jvmti.h"
#include "jvmti_common.hpp"

extern "C" {

static jvmtiEnv* jvmti = nullptr;
static bool test_failed = false;

static void* allocate(JNIEnv* env, jlong size) {
  unsigned char* result = nullptr;
  check_jvmti_status(env,
    jvmti->Allocate(size, &result),
    "Allocate failed");
  return result;
}

static void deallocate(JNIEnv* env, void* mem) {
  check_jvmti_status(env,
    jvmti->Deallocate((unsigned char*)mem),
    "Deallocate failed");
}

// Converts JNI class name signature to simple name (in place).
static void sig2name(char* str) {
  size_t len = strlen(str);
  if (len >=2 && str[0] == 'L' && str[len-1] == ';') {
    len -=2;
    memmove(str, str+1, len);
    str[len] = '\0';
  }
  // Replace '/' with '.'.
  for (char* pos = str; (pos = strchr(pos, '/')) != nullptr; ) {
    *pos = '.';
  }
}

static bool is_static_field(JNIEnv* env, jclass klass, jfieldID fid) {
  enum {
    ACC_STATIC        = 0x0008
  };

  jint access_flags = 0;
  check_jvmti_status(env,
    jvmti->GetFieldModifiers(klass, fid, &access_flags),
    "GetFieldModifiers failed");
  return (access_flags & ACC_STATIC) != 0;
}

static void verify_int_field(JNIEnv* env, jclass klass, jfieldID fid) {
  char* name = nullptr;
  char* sig = nullptr;
  check_jvmti_status(env,
    jvmti->GetFieldName(klass, fid, &name, &sig, nullptr),
    "GetFieldName failed");

  if (strcmp(sig, "I") != 0) {
    printf("ERROR: field '%s' is not int ('%s')\n", name, sig);
    fflush(nullptr);
    fatal(env, "unexpected field type");
  }

  deallocate(env, name);
  deallocate(env, sig);
}


/*
Per jvmtiHeapReferenceInfoField spec (reference information for
JVMTI_HEAP_REFERENCE_FIELD and JVMTI_HEAP_REFERENCE_STATIC_FIELD references.):
If the referrer object is not an interface, then the field indices are determined as follows:
- make a list of all the fields in C and its superclasses,
  starting with all the fields in java.lang.Object and ending with all the fields in C.
- Within this list, put the fields for a given class in the order returned by GetClassFields.
- Assign the fields in this list indices n, n+1, ..., in order,
  where n is the count of the fields in all the interfaces implemented by C.
  Note that C implements all interfaces directly implemented by its superclasses;
  as well as all superinterfaces of these interfaces.
If the referrer object is an interface, then the field indices are determined as follows:
- make a list of the fields directly declared in I.
- Within this list, put the fields in the order returned by GetClassFields.
- Assign the fields in this list indices n, n+1, ..., in order,
  where n is the count of the fields in all the superinterfaces of I.

'Klass' struct contains all required data to calculate field indices.
Also contains static field values.
For each test class, the 'Klass' struct is created and a pointer to it is set as the jclass's tag.
*/

struct Klass {
  jclass klass;
  char* name;
  Klass* super_klass;

  struct Field {
    jfieldID id;
    char* name;

    // Field value for static fields (0 for instance fields).
    // All fields in the test classes are 'int'.
    jint value;

    void init(JNIEnv* env, jclass klass, jfieldID fid);
  };

  // Fields of the class and its superclasses
  // as described in jvmtiHeapReferenceInfoField spec.
  Field* fields;
  jint field_count;

  // Interfaces implemented by this klass, superclasses and superinterfaces.
  Klass** interfaces;
  jint interface_count;

  // Number of fields in all implemented interfaces.
  jint interface_field_count;

  static Klass* explore(JNIEnv* env, jclass klass);

private:
  // Initializes fields, field_count.
  void explore_fields(JNIEnv* env);
  // Initializes interfaces, interface_count.
  void explore_interfaces(JNIEnv* env);

  void print() const;
};

/*
For each test object, the 'Object' struct is created and a pointer to it is set as the jobject's tag.
*/
struct Object {
  Klass* klass;
  // Values of instance fields (0 for static fields).
  // Size of the array == klass->field_count.
  jint* field_values;

  static Object* explore(JNIEnv* env, jobject obj);
};


void Klass::Field::init(JNIEnv* env, jclass klass, jfieldID fid) {
  id = fid;
  check_jvmti_status(env,
    jvmti->GetFieldName(klass, fid, &name, nullptr, nullptr),
    "GetFieldName failed");
  if (is_static_field(env, klass, fid)) {
    verify_int_field(env, klass, fid);
    value = env->GetStaticIntField(klass, fid);
  } else {
    value = 0;
  }
}

void Klass::explore_fields(JNIEnv* env) {
  jint this_count;
  jfieldID* this_fields;
  check_jvmti_status(env,
    jvmti->GetClassFields(klass, &this_count, &this_fields),
    "GetClassFields failed");

  jint super_count = super_klass != nullptr ? super_klass->field_count : 0;

  fields = (Field*)allocate(env, sizeof(Field) * (super_count + this_count));
  field_count = 0;

  if (super_klass != 0) {
    // super_klass->fields already contains fields from all superclasses in the required order.
    for (int i = 0; i < super_count; i++) {
      fields[field_count++].init(env, super_klass->klass, super_klass->fields[i].id);
    }
  }

  // Add field of this class to the end of the list.
  for (int i = 0; i < this_count; i++) {
    fields[field_count++].init(env, klass, this_fields[i]);
  }
  deallocate(env, this_fields);
}


// Calculates maximum number of implemented interfaces of the klass and its superinterfaces.
static jint get_max_interface_count(JNIEnv* env, jclass klass) {
  jint interface_count;
  jclass* interfaces;
  check_jvmti_status(env,
    jvmti->GetImplementedInterfaces(klass, &interface_count, &interfaces),
    "GetImplementedInterfaces failed");

  jint result = interface_count;
  // interfaces implemented by superinterfaces
  for (jint i = 0; i < interface_count; i++) {
    result += get_max_interface_count(env, interfaces[i]);
  }

  deallocate(env, interfaces);

  return result;
}

// Explores all interfaces implemented by 'klass', sorts out duplicates,
// and stores the interfaces in the 'arr' starting from 'index'.
// Returns number of the interfaces added.
static jint fill_interfaces(Klass** arr, jint index, JNIEnv* env, jclass klass) {
  jint interface_count;
  jclass* interfaces;
  check_jvmti_status(env,
    jvmti->GetImplementedInterfaces(klass, &interface_count, &interfaces),
    "GetImplementedInterfaces failed");

  jint count = 0;
  for (jint i = 0; i < interface_count; i++) {
    // Skip interface if it's already in the array
    // (i.e. implemented by another superclass/superinterface).
    bool dup = false;
    for (jint j = 0; j < index; j++) {
      if (env->IsSameObject(arr[j]->klass, interfaces[i]) == JNI_TRUE) {
        dup = true;
        break;
      }
    }
    if (dup) {
      continue;
    }

    // Add the interface.
    arr[index + count] = Klass::explore(env, interfaces[i]);
    count++;

    // And explore its superinterfaces.
    count += fill_interfaces(arr, index + count, env, interfaces[i]);
  }

  deallocate(env, interfaces);

  return count;
}

void Klass::explore_interfaces(JNIEnv* env) {
  jint max_count = get_max_interface_count(env, klass);
  if (super_klass != nullptr) {
    max_count += super_klass->interface_count;
  }

  // Allocate array for maximum possible count.
  interfaces = (Klass**)allocate(env, sizeof(Klass*) * max_count);

  interface_count = 0;
  if (super_klass != nullptr) {
    // Add all interfaces implemented by super_klass first.
    interface_count = super_klass->interface_count;
    if (super_klass->interfaces != nullptr) {
      memcpy(interfaces, super_klass->interfaces, sizeof(Klass*) * super_klass->interface_count);
    }
  }

  // Interfaces implemented by the klass.
  interface_count += fill_interfaces(interfaces, interface_count, env, klass);
}

void Klass::print() const {
  printf("Explored klass: %s, super: %s\n",
         name, super_klass == nullptr ? nullptr : super_klass->name);
  printf("  interfaces (%d):\n", (int)interface_count);
  for (jint i = 0; i < interface_count; i++) {
    printf("    %d: %s\n", (int)i, interfaces[i]->name);
  }
  printf("  fields (%d):\n", (int)field_count);
  for (jint i = 0; i < field_count; i++) {
    printf("    %d: %s (value = %d)\n",
           (int)i, fields[i].name, (int)fields[i].value);
  }
  printf("  interface_field_count: %d\n", (int)interface_field_count);
}

Klass* Klass::explore(JNIEnv* env, jclass klass) {
  jlong tag = 0;
  check_jvmti_status(env,
    jvmti->GetTag(klass, &tag),
    "GetTag failed");
  if (tag != 0) { // already explored
    return (Klass*)tag;
  }

  Klass* result = (Klass*)allocate(env, sizeof(Klass));

  result->klass = (jclass)env->NewGlobalRef(klass);

  check_jvmti_status(env,
    jvmti->GetClassSignature(klass, &result->name, nullptr),
    "GetClassSignature failed");
  sig2name(result->name);

  // Explore superclass first.
  jclass super_klass = env->GetSuperclass(klass);
  result->super_klass = super_klass == nullptr ? nullptr : Klass::explore(env, super_klass);

  result->explore_fields(env);

  result->explore_interfaces(env);

  // Calculate interface_field_count.
  result->interface_field_count = 0;
  for (jint i = 0; i < result->interface_count; i++) {
    result->interface_field_count += result->interfaces[i]->field_count;
  }

  check_jvmti_status(env,
    jvmti->SetTag(klass, (jlong)result),
    "SetTag failed");

  result->print();

  return result;
}

Object* Object::explore(JNIEnv* env, jobject obj) {
  jlong tag = 0;
  check_jvmti_status(env,
    jvmti->GetTag(obj, &tag),
    "GetTag failed");
  if (tag != 0) { // already explored
    return (Object*)tag;
  }

  jclass obj_klass = env->GetObjectClass(obj);
  Klass* klass = Klass::explore(env, obj_klass);
  jint* values = (jint*)allocate(env, sizeof(jint) * klass->field_count);

  for (jint i = 0; i < klass->field_count; i++) {
    jfieldID fid = klass->fields[i].id;
    if (is_static_field(env, obj_klass, fid)) {
      values[i] = 0;
    } else {
      verify_int_field(env, obj_klass, fid);
      values[i] = env->GetIntField(obj, fid);
    }
  }

  Object* result = (Object*)allocate(env, sizeof(Object));
  result->klass = klass;
  result->field_values = values;

  check_jvmti_status(env,
    jvmti->SetTag(obj, (jlong)result),
    "SetTag failed");

  return result;
}


JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *jvm, char *options, void *reserved) {
  jint res = jvm->GetEnv((void **)&jvmti, JVMTI_VERSION_1_1);
  if (res != JNI_OK || jvmti == nullptr) {
    printf("jvm->GetEnv failed\n");
    fflush(nullptr);
    return JNI_ERR;
  }

  jvmtiCapabilities caps;
  memset(&caps, 0, sizeof(caps));
  caps.can_tag_objects = 1;
  jvmtiError err = jvmti->AddCapabilities(&caps);
  if (err != JVMTI_ERROR_NONE) {
    printf("AddCapabilities failed: %s (%d)\n", TranslateError(err), err);
    fflush(nullptr);
    return JNI_ERR;
  }

  return JNI_OK;
}


static bool check_index_bounds(jint index, Klass* klass) {
  if (index < klass->interface_field_count) {
    printf("ERROR: field_index is too small (%d < %d)\n",
           (int)index, (int)klass->interface_field_count);
    test_failed = true;
    return false;
  }
  if (index >= klass->interface_field_count + klass->field_count) {
    printf("ERROR: field_index is too big (%d >= %d)\n",
           (int)index, (int)(klass->interface_field_count + klass->field_count));
    test_failed = true;
    return false;
  }
  return true;
}

static char* get_field_name(Klass* klass, jint index) {
  index -= klass->interface_field_count;
  if (index < 0 || index >= klass->field_count) {
    return nullptr;
  }
  return klass->fields[index].name;
}


jint JNICALL primitiveFieldCallback(
   jvmtiHeapReferenceKind        reference_kind,
   const jvmtiHeapReferenceInfo* reference_info,
   jlong                         class_tag,
   jlong*                        tag_ptr,
   jvalue                        value,
   jvmtiPrimitiveType            value_type,
   void*                         user_data)
{
  if (*tag_ptr == 0) {
    return 0;
  }

  jint index = reference_info->field.index;
  jint int_value = value.i;
  if (value_type != JVMTI_PRIMITIVE_TYPE_INT) {
    printf("ERROR: unexpected value type in primitiveFieldCallback: '%c'\n", (char)value_type);
    test_failed = true;
    int_value = -1;
  }

  if (reference_kind == JVMTI_HEAP_REFERENCE_FIELD) {
    Object* obj = (Object*)(*tag_ptr);
    Klass* klass = obj->klass;
    printf("primitiveFieldCallback(JVMTI_HEAP_REFERENCE_FIELD): "
           "klass=%s, index=%d, type=%c, value=%d\n",
           klass->name, index,
           (int)value_type, (int)value.i);
    if (check_index_bounds(index, klass)) {
      jint expected_value = obj->field_values[index - klass->interface_field_count];
      if (int_value != expected_value) {
        printf("  ERROR: wrong instance value: (%d, expected %d)\n",
               (int)int_value, (int)expected_value);
        test_failed = true;
      } else {
        printf("  OK: field %s.%s, value %d\n",
               klass->name, get_field_name(klass, index), (int)int_value);
      }
    }
  } else if (reference_kind == JVMTI_HEAP_REFERENCE_STATIC_FIELD) {
    Klass* klass = (Klass*)(*tag_ptr);
    printf("primitiveFieldCallback(JVMTI_HEAP_REFERENCE_STATIC_FIELD): "
           "klass=%s, index=%d, type=%c, value=%d\n",
           klass->name, index,
           (int)value_type, (int)value.i);
    if (check_index_bounds(index, klass)) {
      jint expected_value = klass->fields[index - klass->interface_field_count].value;
      if (int_value != expected_value) {
        printf("  ERROR: wrong static value: (%d, expected %d)\n\n\n",
               (int)int_value, (int)expected_value);
        test_failed = true;
      } else {
        printf("  OK: field %s.%s, value %d\n",
               klass->name, get_field_name(klass, index), (int)int_value);
      }
    }
  } else {
    printf("ERROR: unexpected reference_kind in primitiveFieldCallback: %d\n", (int)reference_kind);
    test_failed = true;
  }

  fflush(nullptr);
  return 0;
}


JNIEXPORT void JNICALL
Java_FieldIndicesTest_prepare(JNIEnv *env, jclass cls, jobject testObj) {
  Object::explore(env, testObj);
  fflush(nullptr);
}

JNIEXPORT void JNICALL
Java_FieldIndicesTest_test(JNIEnv *env, jclass cls, jobject rootObject) {
  jvmtiHeapCallbacks heapCallbacks;
  memset(&heapCallbacks, 0, sizeof(heapCallbacks));

  heapCallbacks.primitive_field_callback = primitiveFieldCallback;

  check_jvmti_status(env,
    jvmti->FollowReferences(JVMTI_HEAP_FILTER_UNTAGGED, // heap_filter
                            nullptr,                    // class
                            rootObject,                 // initial_object
                            &heapCallbacks,
                            nullptr),
    "FollowReferences failed");
  fflush(nullptr);
}

JNIEXPORT jboolean JNICALL
Java_FieldIndicesTest_testFailed(JNIEnv *env, jclass cls) {
  return test_failed ? JNI_TRUE : JNI_FALSE;
}

}
