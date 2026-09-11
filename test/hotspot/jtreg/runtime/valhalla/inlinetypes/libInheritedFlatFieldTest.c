/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#define VALUE_SIGNATURE "Lruntime/valhalla/inlinetypes/InheritedFlatFieldTest$Value;"
#define BASE_FIELD_NAME "baseValue"
#define DERIVED_FIELD_NAME "derivedValue"

static jfieldID get_field_id(JNIEnv* env, jobject obj, const char* field_name) {
    jclass receiver_class = (*env)->GetObjectClass(env, obj);
    return (*env)->GetFieldID(env, receiver_class, field_name, VALUE_SIGNATURE);
}

static jobject read_field(JNIEnv* env, jobject obj, const char* field_name) {
    jfieldID field_id = get_field_id(env, obj, field_name);
    return (*env)->GetObjectField(env, obj, field_id);
}

JNIEXPORT jobject JNICALL
Java_runtime_valhalla_inlinetypes_InheritedFlatFieldTest_readBaseValue(JNIEnv* env, jclass klass, jobject obj) {
    return read_field(env, obj, BASE_FIELD_NAME);
}

JNIEXPORT jobject JNICALL
Java_runtime_valhalla_inlinetypes_InheritedFlatFieldTest_readDerivedValue(JNIEnv* env, jclass klass, jobject obj) {
    return read_field(env, obj, DERIVED_FIELD_NAME);
}

JNIEXPORT void JNICALL
Java_runtime_valhalla_inlinetypes_InheritedFlatFieldTest_writeBaseValue(JNIEnv* env, jclass klass, jobject obj, jobject value) {
    jfieldID field_id = get_field_id(env, obj, BASE_FIELD_NAME);
    (*env)->SetObjectField(env, obj, field_id, value);
}

#undef VALUE_SIGNATURE
#undef BASE_FIELD_NAME
#undef DERIVED_FIELD_NAME
