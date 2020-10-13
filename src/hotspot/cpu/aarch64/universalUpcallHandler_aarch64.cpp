/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "include/jvm.h"
#include "jni.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/arrayOop.inline.hpp"
#include "prims/universalUpcallHandler.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/jniHandles.inline.hpp"

extern struct JavaVM_ main_vm;

static struct {
  bool inited;
  struct {
    Klass* klass;
    Symbol* name;
    Symbol* sig;
  } upcall_method;  // jdk.internal.foreign.abi.UniversalUpcallHandler::invoke
} upcall_info;

// FIXME: This should be initialized explicitly instead of lazily/racily
static void upcall_init() {
#if 0
  fprintf(stderr, "upcall_init()\n");
#endif

  TRAPS = Thread::current();
  ResourceMark rm;

  const char* cname = "jdk/internal/foreign/abi/ProgrammableUpcallHandler";
  const char* mname = "invoke";
  const char* mdesc = "(Ljdk/internal/foreign/abi/ProgrammableUpcallHandler;J)V";
  Symbol* cname_sym = SymbolTable::new_symbol(cname, (int)strlen(cname));
  Symbol* mname_sym = SymbolTable::new_symbol(mname, (int)strlen(mname));
  Symbol* mdesc_sym = SymbolTable::new_symbol(mdesc, (int)strlen(mdesc));

#if 0
  ::fprintf(stderr, "cname_sym: %p\n", cname_sym);
  ::fprintf(stderr, "mname_sym: %p\n", mname_sym);
  ::fprintf(stderr, "mdesc_sym: %p\n", mdesc_sym);
#endif

  Klass* k = SystemDictionary::resolve_or_null(cname_sym, THREAD);
#if 0
  ::fprintf(stderr, "Klass: %p\n", k);
#endif

  Method* method = k->lookup_method(mname_sym, mdesc_sym);
#if 0
  ::fprintf(stderr, "Method: %p\n", method);
#endif

  upcall_info.upcall_method.klass = k;
  upcall_info.upcall_method.name = mname_sym;
  upcall_info.upcall_method.sig = mdesc_sym;

  upcall_info.inited = true;
}

static void upcall_helper(jobject rec, address buff) {
  void *p_env = NULL;

  Thread* thread = Thread::current_or_null();
  if (thread == NULL) {
    JavaVM_ *vm = (JavaVM *)(&main_vm);
    vm -> functions -> AttachCurrentThreadAsDaemon(vm, &p_env, NULL);
    thread = Thread::current();
  }

  assert(thread->is_Java_thread(), "really?");

  ThreadInVMfromNative __tiv((JavaThread *)thread);

  if (!upcall_info.inited) {
    upcall_init();
  }

  ResourceMark rm;
  JavaValue result(T_VOID);
  JavaCallArguments args(2); // long = 2 slots

  args.push_jobject(rec);
  args.push_long((jlong) buff);

  JavaCalls::call_static(&result, upcall_info.upcall_method.klass,
                         upcall_info.upcall_method.name, upcall_info.upcall_method.sig,
                         &args, thread);
}

static address generate_upcall_stub(jobject rec, const ABIDescriptor& abi,
                                    const BufferLayout& layout) {
  ResourceMark rm;
  CodeBuffer buffer("upcall_stub", 1024, 1024);

  MacroAssembler* _masm = new MacroAssembler(&buffer);

  // stub code
  __ enter();

  // save pointer to JNI receiver handle into constant segment
  Address rec_adr = InternalAddress(__ address_constant((address)rec));

  assert(abi._stack_alignment_bytes % 16 == 0, "stack must be 16 byte aligned");

  __ sub(sp, sp, (int) align_up(layout.buffer_size, abi._stack_alignment_bytes));

  // TODO: This stub only uses registers which are caller-save in the
  //       standard C ABI. If this is called from a different ABI then
  //       we need to save registers here according to abi.is_volatile_reg.

  for (int i = 0; i < abi._integer_argument_registers.length(); i++) {
    Register reg = abi._integer_argument_registers.at(i);
    ssize_t offset = layout.arguments_integer + i * sizeof(uintptr_t);
    __ str(reg, Address(sp, offset));
  }

  for (int i = 0; i < abi._vector_argument_registers.length(); i++) {
    FloatRegister reg = abi._vector_argument_registers.at(i);
    ssize_t offset = layout.arguments_vector + i * sizeof(VectorRegister);
    __ strq(reg, Address(sp, offset));
  }

  // Capture prev stack pointer (stack arguments base)
  __ add(rscratch1, rfp, 16);   // Skip saved FP and LR
  __ str(rscratch1, Address(sp, layout.stack_args));

  // Call upcall helper
  __ ldr(c_rarg0, rec_adr);
  __ mov(c_rarg1, sp);
  __ movptr(rscratch1, CAST_FROM_FN_PTR(uint64_t, upcall_helper));
  __ blr(rscratch1);

  for (int i = 0; i < abi._integer_return_registers.length(); i++) {
    ssize_t offs = layout.returns_integer + i * sizeof(uintptr_t);
    __ ldr(abi._integer_return_registers.at(i), Address(sp, offs));
  }

  for (int i = 0; i < abi._vector_return_registers.length(); i++) {
    FloatRegister reg = abi._vector_return_registers.at(i);
    ssize_t offs = layout.returns_vector + i * sizeof(VectorRegister);
    __ ldrq(reg, Address(sp, offs));
  }

  __ leave();
  __ ret(lr);

  __ flush();

  BufferBlob* blob = BufferBlob::create("upcall_stub", &buffer);

  return blob->code_begin();
}

jlong ProgrammableUpcallHandler::generate_upcall_stub(JNIEnv *env, jobject rec, jobject jabi, jobject jlayout) {
  const ABIDescriptor abi = parseABIDescriptor(env, jabi);
  const BufferLayout layout = parseBufferLayout(env, jlayout);

  return (jlong) ::generate_upcall_stub(rec, abi, layout);
}
