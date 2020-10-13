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
#include "prims/universalUpcallHandler.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"

JVM_ENTRY(jlong, PUH_AllocateUpcallStub(JNIEnv *env, jobject rec, jobject abi, jobject buffer_layout))
  Handle receiver(THREAD, JNIHandles::resolve(rec));
  jobject global_rec = JNIHandles::make_global(receiver);
  ThreadToNativeFromVM ttnfvm(thread);

  return ProgrammableUpcallHandler::generate_upcall_stub(env, global_rec, abi, buffer_layout);
JVM_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)
#define LANG "Ljava/lang/"

#define FOREIGN_ABI "Ljdk/internal/foreign/abi"

static JNINativeMethod PUH_methods[] = {
  {CC "allocateUpcallStub", CC "(" FOREIGN_ABI "/ABIDescriptor;" FOREIGN_ABI "/BufferLayout;" ")J", FN_PTR(PUH_AllocateUpcallStub)},
};

/**
 * This one function is exported, used by NativeLookup.
 */
JVM_ENTRY(void, JVM_RegisterProgrammableUpcallHandlerMethods(JNIEnv *env, jclass PUH_class)) {
  {
    ThreadToNativeFromVM ttnfv(thread);

    int status = env->RegisterNatives(PUH_class, PUH_methods, sizeof(PUH_methods)/sizeof(JNINativeMethod));
    guarantee(status == JNI_OK && !env->ExceptionOccurred(),
              "register jdk.internal.foreign.abi.ProgrammableUpcallHandler natives");
  }
}
JVM_END
