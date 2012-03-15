/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
#include <JavaNativeFoundation/JavaNativeFoundation.h>
#include <ffi/ffi.h>

#include "com_apple_jobjc_FFIType.h"

JNIEXPORT void JNICALL Java_com_apple_jobjc_FFIType_makeFFIType
(JNIEnv *env, jclass clazz, jlong ffi_type_jlong, jlong ffi_type_elements_jlong)
{
    ffi_type *type = jlong_to_ptr(ffi_type_jlong);
    type->elements = jlong_to_ptr(ffi_type_elements_jlong);
    type->type = FFI_TYPE_STRUCT;
    type->size = type->alignment = 0;
}

JNIEXPORT jint JNICALL Java_com_apple_jobjc_FFIType_getFFITypeSizeof
(JNIEnv *env, jclass clazz)
{
    return (jint) sizeof(ffi_type);
}
