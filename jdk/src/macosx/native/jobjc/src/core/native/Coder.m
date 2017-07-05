/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "com_apple_jobjc_Coder.h"

#include <JavaNativeFoundation/JavaNativeFoundation.h>
#define MACOSX
#include <ffi/ffi.h>
#include <AppKit/AppKit.h>

/*
 * Class:     com_apple_jobjc_Coder
 * Method:    getNativeFFITypeCodeForCode
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_com_apple_jobjc_Coder_getNativeFFITypePtrForCode
(JNIEnv *env, jclass clazz, jint code)
{
    switch ((long)code) {
        case com_apple_jobjc_Coder_FFI_VOID:        return ptr_to_jlong(&ffi_type_void);
        case com_apple_jobjc_Coder_FFI_PTR:            return ptr_to_jlong(&ffi_type_pointer);
        case com_apple_jobjc_Coder_FFI_SINT8:        return ptr_to_jlong(&ffi_type_sint8);
        case com_apple_jobjc_Coder_FFI_UINT8:        return ptr_to_jlong(&ffi_type_uint8);
        case com_apple_jobjc_Coder_FFI_SINT16:        return ptr_to_jlong(&ffi_type_sint16);
        case com_apple_jobjc_Coder_FFI_UINT16:        return ptr_to_jlong(&ffi_type_uint16);
        case com_apple_jobjc_Coder_FFI_SINT32:        return ptr_to_jlong(&ffi_type_sint32);
        case com_apple_jobjc_Coder_FFI_UINT32:        return ptr_to_jlong(&ffi_type_uint32);
        case com_apple_jobjc_Coder_FFI_SINT64:        return ptr_to_jlong(&ffi_type_sint64);
        case com_apple_jobjc_Coder_FFI_UINT64:        return ptr_to_jlong(&ffi_type_uint64);
        case com_apple_jobjc_Coder_FFI_FLOAT:        return ptr_to_jlong(&ffi_type_float);
        case com_apple_jobjc_Coder_FFI_DOUBLE:        return ptr_to_jlong(&ffi_type_double);
        case com_apple_jobjc_Coder_FFI_LONGDOUBLE:    return ptr_to_jlong(&ffi_type_longdouble);
    }

    return ptr_to_jlong(NULL);
}
