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
#include "prims/universalNativeInvoker.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "oops/arrayOop.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "prims/methodHandles.hpp"

void generate_invoke_native(MacroAssembler* _masm, const ABIDescriptor& abi, const BufferLayout& layout) {

#if 0
  fprintf(stderr, "generate_invoke_native()\n");
#endif

  /**
   * invoke_native_stub(struct ShuffleDowncallContext* ctxt) {
   *   rbx = ctxt;
   *
   *   stack = alloca(ctxt->arguments.stack_args_bytes);
   *
   *   load_all_registers();
   *   memcpy(stack, ctxt->arguments.stack_args, arguments.stack_args_bytes);
   *
   *   (*ctxt->arguments.next_pc)();
   *
   *   store_all_registers();
   * }
   */

  __ enter();

  // Put the context pointer in ebx/rbx - it's going to be heavily used below both before and after the call
  Register ctxt_reg = rbx;
  Register used_regs[] = { ctxt_reg, rcx, rsi, rdi };
  GrowableArray<Register> preserved_regs;

  for (size_t i = 0; i < sizeof(used_regs)/sizeof(Register); i++) {
    Register used_reg = used_regs[i];
    if (!abi.is_volatile_reg(used_reg)) {
      preserved_regs.push(used_reg);
    }
  }

  __ block_comment("init_and_alloc_stack");

  for (int i = 0; i < preserved_regs.length(); i++) {
    __ push(preserved_regs.at(i));
  }

  __ movptr(ctxt_reg, c_rarg0); // FIXME c args? or java?

  __ block_comment("allocate_stack");
  __ movptr(rcx, Address(ctxt_reg, (int) layout.stack_args_bytes));
  __ subptr(rsp, rcx);
  __ andptr(rsp, -abi._stack_alignment_bytes);

  // Note: rcx is used below!


  __ block_comment("load_arguments");

  __ shrptr(rcx, LogBytesPerWord); // bytes -> words
  __ movptr(rsi, Address(ctxt_reg, (int) layout.stack_args));
  __ movptr(rdi, rsp);
  __ rep_mov();


  for (int i = 0; i < abi._vector_argument_registers.length(); i++) {
    // [1] -> 64 bit -> xmm
    // [2] -> 128 bit -> xmm
    // [4] -> 256 bit -> ymm
    // [8] -> 512 bit -> zmm

    XMMRegister reg = abi._vector_argument_registers.at(i);
    size_t offs = layout.arguments_vector + i * sizeof(VectorRegister);
    if (UseAVX >= 3) {
      __ evmovdqul(reg, Address(ctxt_reg, (int)offs), Assembler::AVX_512bit);
    } else if (UseAVX >= 1) {
      __ vmovdqu(reg, Address(ctxt_reg, (int)offs));
    } else {
      __ movdqu(reg, Address(ctxt_reg, (int)offs));
    }
  }

  for (int i = 0; i < abi._integer_argument_registers.length(); i++) {
    size_t offs = layout.arguments_integer + i * sizeof(uintptr_t);
    __ movptr(abi._integer_argument_registers.at(i), Address(ctxt_reg, (int)offs));
  }

  if (abi._shadow_space_bytes != 0) {
    __ block_comment("allocate shadow space for argument register spill");
    __ subptr(rsp, abi._shadow_space_bytes);
  }

  // call target function
  __ block_comment("call target function");
  __ call(Address(ctxt_reg, (int) layout.arguments_next_pc));

  if (abi._shadow_space_bytes != 0) {
    __ block_comment("pop shadow space");
    __ addptr(rsp, abi._shadow_space_bytes);
  }

  __ block_comment("store_registers");
  for (int i = 0; i < abi._integer_return_registers.length(); i++) {
    ssize_t offs = layout.returns_integer + i * sizeof(uintptr_t);
    __ movptr(Address(ctxt_reg, offs), abi._integer_return_registers.at(i));
  }

  for (int i = 0; i < abi._vector_return_registers.length(); i++) {
    // [1] -> 64 bit -> xmm
    // [2] -> 128 bit -> xmm (SSE)
    // [4] -> 256 bit -> ymm (AVX)
    // [8] -> 512 bit -> zmm (AVX-512, aka AVX3)

    XMMRegister reg = abi._vector_return_registers.at(i);
    size_t offs = layout.returns_vector + i * sizeof(VectorRegister);
    if (UseAVX >= 3) {
      __ evmovdqul(Address(ctxt_reg, (int)offs), reg, Assembler::AVX_512bit);
    } else if (UseAVX >= 1) {
      __ vmovdqu(Address(ctxt_reg, (int)offs), reg);
    } else {
      __ movdqu(Address(ctxt_reg, (int)offs), reg);
    }
  }

  for (size_t i = 0; i < abi._X87_return_registers_noof; i++) {
    size_t offs = layout.returns_x87 + i * (sizeof(long double));
    __ fstp_x(Address(ctxt_reg, (int)offs)); //pop ST(0)
  }

  // Restore backed up preserved register
  for (int i = 0; i < preserved_regs.length(); i++) {
    __ movptr(preserved_regs.at(i), Address(rbp, -(int)(sizeof(uintptr_t) * (i + 1))));
  }

  __ leave();
  __ ret(0);

  __ flush();
}

class ProgrammableInvokerGenerator : public StubCodeGenerator {
private:
  const ABIDescriptor* _abi;
  const BufferLayout* _layout;
public:
  ProgrammableInvokerGenerator(CodeBuffer* code, const ABIDescriptor* abi, const BufferLayout* layout)
   : StubCodeGenerator(code, PrintMethodHandleStubs), _abi(abi), _layout(layout) {}

  void generate() {
      generate_invoke_native(_masm, *_abi, *_layout);
  }
};

jlong ProgrammableInvoker::generate_adapter(JNIEnv* env, jobject jabi, jobject jlayout) {
    ResourceMark rm;
    const ABIDescriptor abi = parseABIDescriptor(env, jabi);
    const BufferLayout layout = parseBufferLayout(env, jlayout);

    BufferBlob* _invoke_native_blob = BufferBlob::create("invoke_native_blob", MethodHandles::adapter_code_size);

    CodeBuffer code2(_invoke_native_blob);
    ProgrammableInvokerGenerator g2(&code2, &abi, &layout);
    g2.generate();
    code2.log_section_sizes("InvokeNativeBlob");

    return (jlong) _invoke_native_blob->code_begin();
}
