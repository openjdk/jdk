/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "include/jvm.h"
#include "jni.h"
#include "prims/universalUpcallHandler.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "oops/arrayOop.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "classfile/symbolTable.hpp"

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

  JavaCalls::call_static(&result, upcall_info.upcall_method.klass, upcall_info.upcall_method.name, upcall_info.upcall_method.sig, &args, thread);
}

static address generate_upcall_stub(jobject rec, const ABIDescriptor& abi, const BufferLayout& layout) {
  ResourceMark rm;
  CodeBuffer buffer("upcall_stub", 1024, 1024);

  MacroAssembler* _masm = new MacroAssembler(&buffer);
  int stack_alignment_C = 16; // bytes
  int register_size = sizeof(uintptr_t);
  int buffer_alignment = sizeof(VectorRegister);

  // stub code
  __ enter();

  // save pointer to JNI receiver handle into constant segment
  Address rec_adr = __ as_Address(InternalAddress(__ address_constant((address)rec)));

  __ subptr(rsp, (int) align_up(layout.buffer_size, buffer_alignment));

  Register used[] = { c_rarg0, c_rarg1, rax, rbx, rdi, rsi, r12, r13, r14, r15 };
  GrowableArray<Register> preserved;
  // TODO need to preserve anything killed by the upcall that is non-volatile, needs XMM regs as well, probably
  for (size_t i = 0; i < sizeof(used)/sizeof(Register); i++) {
    Register reg = used[i];
    if (!abi.is_volatile_reg(reg)) {
      preserved.push(reg);
    }
  }

  int preserved_size = align_up(preserved.length() * register_size, stack_alignment_C); // includes register alignment
  int buffer_offset = preserved_size; // offset from rsp

  __ subptr(rsp, preserved_size);
  for (int i = 0; i < preserved.length(); i++) {
    __ movptr(Address(rsp, i * register_size), preserved.at(i));
  }

  for (int i = 0; i < abi._integer_argument_registers.length(); i++) {
    size_t offs = buffer_offset + layout.arguments_integer + i * sizeof(uintptr_t);
    __ movptr(Address(rsp, (int)offs), abi._integer_argument_registers.at(i));
  }

  for (int i = 0; i < abi._vector_argument_registers.length(); i++) {
    XMMRegister reg = abi._vector_argument_registers.at(i);
    size_t offs = buffer_offset + layout.arguments_vector + i * sizeof(VectorRegister);
    if (UseAVX >= 3) {
      __ evmovdqul(Address(rsp, (int)offs), reg, Assembler::AVX_512bit);
    } else if (UseAVX >= 1) {
      __ vmovdqu(Address(rsp, (int)offs), reg);
    } else {
      __ movdqu(Address(rsp, (int)offs), reg);
    }
  }

  // Capture prev stack pointer (stack arguments base)
#ifndef _WIN64
  __ lea(rax, Address(rbp, 16)); // skip frame+return address
#else
  __ lea(rax, Address(rbp, 16 + 32)); // also skip shadow space
#endif
  __ movptr(Address(rsp, buffer_offset + (int) layout.stack_args), rax);
#ifndef PRODUCT
  __ movptr(Address(rsp, buffer_offset + (int) layout.stack_args_bytes), -1); // unknown
#endif

  // Call upcall helper

  __ movptr(c_rarg0, rec_adr);
  __ lea(c_rarg1, Address(rsp, buffer_offset));

#ifdef _WIN64
  __ block_comment("allocate shadow space for argument register spill");
  __ subptr(rsp, 32);
#endif

  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, upcall_helper)));

#ifdef _WIN64
  __ block_comment("pop shadow space");
  __ addptr(rsp, 32);
#endif

  for (int i = 0; i < abi._integer_return_registers.length(); i++) {
    size_t offs = buffer_offset + layout.returns_integer + i * sizeof(uintptr_t);
    __ movptr(abi._integer_return_registers.at(i), Address(rsp, (int)offs));
  }

  for (int i = 0; i < abi._vector_return_registers.length(); i++) {
    XMMRegister reg = abi._vector_return_registers.at(i);
    size_t offs = buffer_offset + layout.returns_vector + i * sizeof(VectorRegister);
    if (UseAVX >= 3) {
      __ evmovdqul(reg, Address(rsp, (int)offs), Assembler::AVX_512bit);
    } else if (UseAVX >= 1) {
      __ vmovdqu(reg, Address(rsp, (int)offs));
    } else {
      __ movdqu(reg, Address(rsp, (int)offs));
    }
  }

  for (size_t i = abi._X87_return_registers_noof; i > 0 ; i--) {
      ssize_t offs = buffer_offset + layout.returns_x87 + (i - 1) * (sizeof(long double));
      __ fld_x (Address(rsp, (int)offs));
  }

  // Restore preserved registers
  for (int i = 0; i < preserved.length(); i++) {
    __ movptr(preserved.at(i), Address(rsp, i * register_size));
  }

  __ leave();
  __ ret(0);

  _masm->flush();

  BufferBlob* blob = BufferBlob::create("upcall_stub", &buffer);

  return blob->code_begin();
}

jlong ProgrammableUpcallHandler::generate_upcall_stub(JNIEnv *env, jobject rec, jobject jabi, jobject jlayout) {
  const ABIDescriptor abi = parseABIDescriptor(env, jabi);
  const BufferLayout layout = parseBufferLayout(env, jlayout);

  return (jlong) ::generate_upcall_stub(rec, abi, layout);
}
