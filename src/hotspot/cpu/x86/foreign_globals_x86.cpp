/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include CPU_HEADER(foreign_globals)

bool ABIDescriptor::is_volatile_reg(Register reg) const {
    return _integer_argument_registers.contains(reg)
        || _integer_additional_volatile_registers.contains(reg);
}

bool ABIDescriptor::is_volatile_reg(XMMRegister reg) const {
    return _vector_argument_registers.contains(reg)
        || _vector_additional_volatile_registers.contains(reg);
}

#define FOREIGN_ABI "jdk/internal/foreign/abi/"

#define INTEGER_TYPE 0
#define VECTOR_TYPE 1
#define X87_TYPE 2
#define STACK_TYPE 3

template<typename T, typename Func>
void loadArray(JNIEnv* env, jfieldID indexField, jobjectArray jarray, jint type_index, GrowableArray<T>& array, Func converter) {
    jobjectArray subarray = (jobjectArray) env->GetObjectArrayElement(jarray, type_index);
    jint subarray_length = env->GetArrayLength(subarray);
    for (jint i = 0; i < subarray_length; i++) {
        jobject storage = env->GetObjectArrayElement(subarray, i);
        jint index = env->GetIntField(storage, indexField);
        array.push(converter(index));
    }
}

const ABIDescriptor parseABIDescriptor(JNIEnv* env, jobject jabi) {
    jclass jc_ABIDescriptor = env->FindClass(FOREIGN_ABI "ABIDescriptor");
    jfieldID jfID_inputStorage = env->GetFieldID(jc_ABIDescriptor, "inputStorage", "[[L" FOREIGN_ABI "VMStorage;");
    jfieldID jfID_outputStorage = env->GetFieldID(jc_ABIDescriptor, "outputStorage", "[[L" FOREIGN_ABI "VMStorage;");
    jfieldID jfID_volatileStorage = env->GetFieldID(jc_ABIDescriptor, "volatileStorage", "[[L" FOREIGN_ABI "VMStorage;");
    jfieldID jfID_stackAlignment = env->GetFieldID(jc_ABIDescriptor, "stackAlignment", "I");
    jfieldID jfID_shadowSpace = env->GetFieldID(jc_ABIDescriptor, "shadowSpace", "I");

    jclass jc_VMStorage = env->FindClass(FOREIGN_ABI "VMStorage");
    jfieldID jfID_storageIndex = env->GetFieldID(jc_VMStorage, "index", "I");

    ABIDescriptor abi;

    jobjectArray inputStorage = (jobjectArray) env->GetObjectField(jabi, jfID_inputStorage);
    loadArray(env, jfID_storageIndex, inputStorage, INTEGER_TYPE, abi._integer_argument_registers, as_Register);
    loadArray(env, jfID_storageIndex, inputStorage, VECTOR_TYPE, abi._vector_argument_registers, as_XMMRegister);

    jobjectArray outputStorage = (jobjectArray) env->GetObjectField(jabi, jfID_outputStorage);
    loadArray(env, jfID_storageIndex, outputStorage, INTEGER_TYPE, abi._integer_return_registers, as_Register);
    loadArray(env, jfID_storageIndex, outputStorage, VECTOR_TYPE, abi._vector_return_registers, as_XMMRegister);
    jobjectArray subarray = (jobjectArray) env->GetObjectArrayElement(outputStorage, X87_TYPE);
    abi._X87_return_registers_noof = env->GetArrayLength(subarray);

    jobjectArray volatileStorage = (jobjectArray) env->GetObjectField(jabi, jfID_volatileStorage);
    loadArray(env, jfID_storageIndex, volatileStorage, INTEGER_TYPE, abi._integer_additional_volatile_registers, as_Register);
    loadArray(env, jfID_storageIndex, volatileStorage, VECTOR_TYPE, abi._vector_additional_volatile_registers, as_XMMRegister);

    abi._stack_alignment_bytes = env->GetIntField(jabi, jfID_stackAlignment);
    abi._shadow_space_bytes = env->GetIntField(jabi, jfID_shadowSpace);

    return abi;
}

const BufferLayout parseBufferLayout(JNIEnv* env, jobject jlayout) {
    jclass jc_BufferLayout = env->FindClass(FOREIGN_ABI "BufferLayout");
    jfieldID jfID_size = env->GetFieldID(jc_BufferLayout, "size", "J");
    jfieldID jfID_arguments_next_pc = env->GetFieldID(jc_BufferLayout, "arguments_next_pc", "J");
    jfieldID jfID_stack_args_bytes = env->GetFieldID(jc_BufferLayout, "stack_args_bytes", "J");
    jfieldID jfID_stack_args = env->GetFieldID(jc_BufferLayout, "stack_args", "J");
    jfieldID jfID_input_type_offsets = env->GetFieldID(jc_BufferLayout, "input_type_offsets", "[J");
    jfieldID jfID_output_type_offsets = env->GetFieldID(jc_BufferLayout, "output_type_offsets", "[J");

    BufferLayout layout;

    layout.stack_args_bytes = env->GetLongField(jlayout, jfID_stack_args_bytes);
    layout.stack_args = env->GetLongField(jlayout, jfID_stack_args);
    layout.arguments_next_pc = env->GetLongField(jlayout, jfID_arguments_next_pc);

    jlongArray input_offsets = (jlongArray) env->GetObjectField(jlayout, jfID_input_type_offsets);
    jlong* input_offsets_prim = env->GetLongArrayElements(input_offsets, NULL);
    layout.arguments_integer = (size_t) input_offsets_prim[INTEGER_TYPE];
    layout.arguments_vector = (size_t) input_offsets_prim[VECTOR_TYPE];
    env->ReleaseLongArrayElements(input_offsets, input_offsets_prim, JNI_ABORT);

    jlongArray output_offsets = (jlongArray) env->GetObjectField(jlayout, jfID_output_type_offsets);
    jlong* output_offsets_prim = env->GetLongArrayElements(output_offsets, NULL);
    layout.returns_integer = (size_t) output_offsets_prim[INTEGER_TYPE];
    layout.returns_vector = (size_t) output_offsets_prim[VECTOR_TYPE];
    layout.returns_x87 = (size_t) output_offsets_prim[X87_TYPE];
    env->ReleaseLongArrayElements(output_offsets, output_offsets_prim, JNI_ABORT);

    layout.buffer_size = env->GetLongField(jlayout, jfID_size);

    return layout;
}
