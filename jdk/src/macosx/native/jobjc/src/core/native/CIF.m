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

#include "com_apple_jobjc_CIF.h"

#define MACOSX
#include <ffi/ffi.h>
#include <JavaNativeFoundation/JavaNativeFoundation.h>

#include "NativeBuffer.h"

JNIEXPORT jint JNICALL Java_com_apple_jobjc_CIF_getSizeofCIF
(JNIEnv *env, jclass clazz)
{
    return (jint) sizeof(ffi_cif);
}

JNIEXPORT jboolean JNICALL Java_com_apple_jobjc_CIF_prepCIF
(JNIEnv *env, jclass clazz, jlong jCIFPtr, jint jNargs, jlong jRetTypePtr, jlong jArgsPtr)
{
    ffi_cif *cif = jlong_to_ptr(jCIFPtr);
    unsigned int nargs = jNargs;
    ffi_type *rtype = jlong_to_ptr(jRetTypePtr);
    ffi_type **atypes = jlong_to_ptr(jArgsPtr);

//    NSLog(@"rtype->(size: %d, alignment: %d, type: %d)", rtype->size, rtype->alignment, rtype->type);
    return (jboolean) (FFI_OK == ffi_prep_cif(cif, FFI_DEFAULT_ABI, nargs, rtype, atypes));
}
