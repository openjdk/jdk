/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "compiler/compilationPolicy.hpp"
#include "memory/resourceArea.hpp"
#include "prims/universalUpcallHandler.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.inline.hpp"

#define FOREIGN_ABI "jdk/internal/foreign/abi/"

extern struct JavaVM_ main_vm;

void ProgrammableUpcallHandler::upcall_helper(JavaThread* thread, jobject rec, address buff) {
  JavaThread* THREAD = thread; // For exception macros.
  ThreadInVMfromNative tiv(THREAD);
  const UpcallMethod& upcall_method = instance().upcall_method;

  ResourceMark rm(THREAD);
  JavaValue result(T_VOID);
  JavaCallArguments args(2); // long = 2 slots

  args.push_jobject(rec);
  args.push_long((jlong) buff);

  JavaCalls::call_static(&result, upcall_method.klass, upcall_method.name, upcall_method.sig, &args, CATCH);
}

Thread* ProgrammableUpcallHandler::maybe_attach_and_get_thread(bool* should_detach) {
  Thread* thread = Thread::current_or_null();
  if (thread == nullptr) {
    JavaVM_ *vm = (JavaVM *)(&main_vm);
    JNIEnv* p_env = nullptr; // unused
    jint result = vm->functions->AttachCurrentThread(vm, (void**) &p_env, nullptr);
    guarantee(result == JNI_OK, "Could not attach thread for upcall. JNI error code: %d", result);
    *should_detach = true;
    thread = Thread::current();
  } else {
    *should_detach = false;
  }
  return thread;
}

void ProgrammableUpcallHandler::detach_thread(Thread* thread) {
  JavaVM_ *vm = (JavaVM *)(&main_vm);
  vm->functions->DetachCurrentThread(vm);
}

void ProgrammableUpcallHandler::attach_thread_and_do_upcall(jobject rec, address buff) {
  bool should_detach = false;
  Thread* thread = maybe_attach_and_get_thread(&should_detach);

  {
    MACOS_AARCH64_ONLY(ThreadWXEnable wx(WXWrite, thread));
    upcall_helper(JavaThread::cast(thread), rec, buff);
  }

  if (should_detach) {
    detach_thread(thread);
  }
}

const ProgrammableUpcallHandler& ProgrammableUpcallHandler::instance() {
  static ProgrammableUpcallHandler handler;
  return handler;
}

ProgrammableUpcallHandler::ProgrammableUpcallHandler() {
  JavaThread* THREAD = JavaThread::current(); // For exception macros.
  ResourceMark rm(THREAD);
  Symbol* sym = SymbolTable::new_symbol(FOREIGN_ABI "ProgrammableUpcallHandler");
  Klass* k = SystemDictionary::resolve_or_null(sym, Handle(), Handle(), CATCH);
  k->initialize(CATCH);

  upcall_method.klass = k;
  upcall_method.name = SymbolTable::new_symbol("invoke");
  upcall_method.sig = SymbolTable::new_symbol("(Ljava/lang/invoke/MethodHandle;J)V");

  assert(upcall_method.klass->lookup_method(upcall_method.name, upcall_method.sig) != nullptr,
    "Could not find upcall method: %s.%s%s", upcall_method.klass->external_name(),
    upcall_method.name->as_C_string(), upcall_method.sig->as_C_string());
}

void ProgrammableUpcallHandler::handle_uncaught_exception(oop exception) {
  // Based on CATCH macro
  tty->print_cr("Uncaught exception:");
  exception->print();
  ShouldNotReachHere();
}

JVM_ENTRY(jlong, PUH_AllocateUpcallStub(JNIEnv *env, jclass unused, jobject rec, jobject abi, jobject buffer_layout))
  Handle receiver(THREAD, JNIHandles::resolve(rec));
  jobject global_rec = JNIHandles::make_global(receiver);
  return (jlong) ProgrammableUpcallHandler::generate_upcall_stub(global_rec, abi, buffer_layout);
JNI_END

JVM_ENTRY(jlong, PUH_AllocateOptimizedUpcallStub(JNIEnv *env, jclass unused, jobject mh, jobject abi, jobject conv))
  Handle mh_h(THREAD, JNIHandles::resolve(mh));
  jobject mh_j = JNIHandles::make_global(mh_h);

  oop lform = java_lang_invoke_MethodHandle::form(mh_h());
  oop vmentry = java_lang_invoke_LambdaForm::vmentry(lform);
  Method* entry = java_lang_invoke_MemberName::vmtarget(vmentry);
  const methodHandle mh_entry(THREAD, entry);

  assert(entry->method_holder()->is_initialized(), "no clinit barrier");
  CompilationPolicy::compile_if_required(mh_entry, CHECK_0);

  return (jlong) ProgrammableUpcallHandler::generate_optimized_upcall_stub(mh_j, entry, abi, conv);
JVM_END

JVM_ENTRY(jboolean, PUH_SupportsOptimizedUpcalls(JNIEnv *env, jclass unused))
  return (jboolean) ProgrammableUpcallHandler::supports_optimized_upcalls();
JVM_END

#define CC (char*)  /*cast a literal from (const char*)*/
#define FN_PTR(f) CAST_FROM_FN_PTR(void*, &f)

static JNINativeMethod PUH_methods[] = {
  {CC "allocateUpcallStub", CC "(" "Ljava/lang/invoke/MethodHandle;" "L" FOREIGN_ABI "ABIDescriptor;" "L" FOREIGN_ABI "BufferLayout;" ")J", FN_PTR(PUH_AllocateUpcallStub)},
  {CC "allocateOptimizedUpcallStub", CC "(" "Ljava/lang/invoke/MethodHandle;" "L" FOREIGN_ABI "ABIDescriptor;" "L" FOREIGN_ABI "ProgrammableUpcallHandler$CallRegs;" ")J", FN_PTR(PUH_AllocateOptimizedUpcallStub)},
  {CC "supportsOptimizedUpcalls", CC "()Z", FN_PTR(PUH_SupportsOptimizedUpcalls)},
};

/**
 * This one function is exported, used by NativeLookup.
 */
JNI_ENTRY(void, JVM_RegisterProgrammableUpcallHandlerMethods(JNIEnv *env, jclass PUH_class))
  ThreadToNativeFromVM ttnfv(thread);
  int status = env->RegisterNatives(PUH_class, PUH_methods, sizeof(PUH_methods)/sizeof(JNINativeMethod));
  guarantee(status == JNI_OK && !env->ExceptionOccurred(),
            "register jdk.internal.foreign.abi.ProgrammableUpcallHandler natives");
JNI_END
