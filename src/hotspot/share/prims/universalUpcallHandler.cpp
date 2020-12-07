/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "memory/resourceArea.hpp"
#include "prims/universalUpcallHandler.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.inline.hpp"

#define FOREIGN_ABI "jdk/internal/foreign/abi/"

extern struct JavaVM_ main_vm;

void ProgrammableUpcallHandler::upcall_helper(JavaThread* thread, jobject rec, address buff) {
  JavaThread* THREAD = thread;
  ThreadInVMfromNative tiv(THREAD);
  const UpcallMethod& upcall_method = instance().upcall_method;

  ResourceMark rm(THREAD);
  JavaValue result(T_VOID);
  JavaCallArguments args(2); // long = 2 slots

  args.push_jobject(rec);
  args.push_long((jlong) buff);

  JavaCalls::call_static(&result, upcall_method.klass, upcall_method.name, upcall_method.sig, &args, CATCH);
}

void ProgrammableUpcallHandler::attach_thread_and_do_upcall(jobject rec, address buff) {
  Thread* thread = Thread::current_or_null();
  bool should_detach = false;
  if (thread == nullptr) {
    JavaVM_ *vm = (JavaVM *)(&main_vm);
    JNIEnv* p_env = nullptr; // unused
    jint result = vm->functions->AttachCurrentThread(vm, (void**) &p_env, nullptr);
    guarantee(result == JNI_OK, "Could not attach thread for upcall. JNI error code: %d", result);
    should_detach = true;
    thread = Thread::current();
  }

  upcall_helper(thread->as_Java_thread(), rec, buff);

  if (should_detach) {
    JavaVM_ *vm = (JavaVM *)(&main_vm);
    vm->functions->DetachCurrentThread(vm);
  }
}

const ProgrammableUpcallHandler& ProgrammableUpcallHandler::instance() {
  static ProgrammableUpcallHandler handler;
  return handler;
}

ProgrammableUpcallHandler::ProgrammableUpcallHandler() {
  Thread* THREAD = Thread::current();
  ResourceMark rm(THREAD);
  Symbol* sym = SymbolTable::new_symbol(FOREIGN_ABI "ProgrammableUpcallHandler");
  Klass* k = SystemDictionary::resolve_or_null(sym, Handle(), Handle(), CATCH);
  k->initialize(CATCH);

  upcall_method.klass = k;
  upcall_method.name = SymbolTable::new_symbol("invoke");
  upcall_method.sig = SymbolTable::new_symbol("(L" FOREIGN_ABI "ProgrammableUpcallHandler;J)V");

  assert(upcall_method.klass->lookup_method(upcall_method.name, upcall_method.sig) != nullptr,
    "Could not find upcall method: %s.%s%s", upcall_method.klass->external_name(),
    upcall_method.name->as_C_string(), upcall_method.sig->as_C_string());
}

JNI_ENTRY(jlong, PUH_AllocateUpcallStub(JNIEnv *env, jobject rec, jobject abi, jobject buffer_layout))
  Handle receiver(THREAD, JNIHandles::resolve(rec));
  jobject global_rec = JNIHandles::make_global(receiver);
  return (jlong) ProgrammableUpcallHandler::generate_upcall_stub(global_rec, abi, buffer_layout);
JNI_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod PUH_methods[] = {
  {CC "allocateUpcallStub", CC "(L" FOREIGN_ABI "ABIDescriptor;L" FOREIGN_ABI "BufferLayout;" ")J", FN_PTR(PUH_AllocateUpcallStub)},
};

/**
 * This one function is exported, used by NativeLookup.
 */
JNI_LEAF(void, JVM_RegisterProgrammableUpcallHandlerMethods(JNIEnv *env, jclass PUH_class))
  int status = env->RegisterNatives(PUH_class, PUH_methods, sizeof(PUH_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionOccurred(),
            "register jdk.internal.foreign.abi.ProgrammableUpcallHandler natives");
JNI_END
