/*
 * Copyright (c) 2026, Microsoft and/or its affiliates. All rights reserved.
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
#include <windows.h>
#include <intrin.h>

#pragma intrinsic(_ReturnAddress)

/*
 * We use the fact that both the JNI function as well as the generated native
 * wrapper both live in the code cache.  So by probing the Windows function
 * table for an entry for the return address, we effectively check whether the
 * code cache area is registered or not.
 */

JNIEXPORT jlong JNICALL
Java_CodeCacheRuntimeFunctionTableTest_callerRuntimeFunction(JNIEnv* env, jclass cls) {
    DWORD64 control_pc = (DWORD64)_ReturnAddress();
    DWORD64 image_base = 0;
    PRUNTIME_FUNCTION runtime_function = RtlLookupFunctionEntry(control_pc, &image_base, NULL);
    return (jlong)runtime_function;
}
