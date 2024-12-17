/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeBlob.hpp"
#include "code/codeCache.hpp"
#include "runtime/interfaceSupport.inline.hpp"

JVM_ENTRY(static jboolean, UH_FreeUpcallStub0(JNIEnv *env, jobject _unused, jlong addr))
  // safe to call 'find_blob' without code cache lock, because stub is always alive
  CodeBlob* cb = CodeCache::find_blob((char*)addr);
  if (cb == nullptr) {
    return false;
  }
  UpcallStub::free(cb->as_upcall_stub());
  return true;
JVM_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)
#define LANG "Ljava/lang/"

// These are the native methods on jdk.internal.foreign.NativeInvoker.
static JNINativeMethod UH_methods[] = {
  {CC "freeUpcallStub0",     CC "(J)Z",                FN_PTR(UH_FreeUpcallStub0)}
};

/**
 * This one function is exported, used by NativeLookup.
 */
JVM_LEAF(void, JVM_RegisterUpcallHandlerMethods(JNIEnv *env, jclass UH_class))
  int status = env->RegisterNatives(UH_class, UH_methods, sizeof(UH_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionCheck(),
            "register jdk.internal.foreign.abi.UpcallStubs natives");
JVM_END

