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
#include "include/jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/arrayOop.inline.hpp"
#include "prims/methodHandles.hpp"
#include "prims/universalNativeInvoker.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaCalls.hpp"

static void generate_invoke_native(MacroAssembler* _masm,
                                   const ABIDescriptor& abi,
                                   const BufferLayout& layout) {
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

  // Name registers used in the stub code. These are all caller-save so
  // may be clobbered by the call to the native function. Avoid using
  // rscratch1 here as it's r8 which is the indirect result register in
  // the standard ABI.
  Register Rctx = r10, Rstack_size = r11;
  Register Rwords = r12, Rtmp = r13;
  Register Rsrc_ptr = r14, Rdst_ptr = r15;

  assert_different_registers(Rctx, Rstack_size, rscratch1, rscratch2);

  // TODO: if the callee is not using the standard C ABI then we need to
  //       preserve more registers here.

  __ block_comment("init_and_alloc_stack");

  __ mov(Rctx, c_rarg0);
  __ str(Rctx, Address(__ pre(sp, -2 * wordSize)));

  assert(abi._stack_alignment_bytes % 16 == 0, "stack must be 16 byte aligned");

  __ block_comment("allocate_stack");
  __ ldr(Rstack_size, Address(Rctx, (int) layout.stack_args_bytes));
  __ add(rscratch2, Rstack_size, abi._stack_alignment_bytes - 1);
  __ andr(rscratch2, rscratch2, -abi._stack_alignment_bytes);
  __ sub(sp, sp, rscratch2);

  __ block_comment("load_arguments");

  __ ldr(Rsrc_ptr, Address(Rctx, (int) layout.stack_args));
  __ lsr(Rwords, Rstack_size, LogBytesPerWord);
  __ mov(Rdst_ptr, sp);

  Label Ldone, Lnext;
  __ bind(Lnext);
  __ cbz(Rwords, Ldone);
  __ ldr(Rtmp, __ post(Rsrc_ptr, wordSize));
  __ str(Rtmp, __ post(Rdst_ptr, wordSize));
  __ sub(Rwords, Rwords, 1);
  __ b(Lnext);
  __ bind(Ldone);

  for (int i = 0; i < abi._vector_argument_registers.length(); i++) {
    ssize_t offs = layout.arguments_vector + i * sizeof(VectorRegister);
    __ ldrq(abi._vector_argument_registers.at(i), Address(Rctx, offs));
  }

  for (int i = 0; i < abi._integer_argument_registers.length(); i++) {
    ssize_t offs = layout.arguments_integer + i * sizeof(uintptr_t);
    __ ldr(abi._integer_argument_registers.at(i), Address(Rctx, offs));
  }

  assert(abi._shadow_space_bytes == 0, "shadow space not supported on AArch64");

  // call target function
  __ block_comment("call target function");
  __ ldr(rscratch2, Address(Rctx, (int) layout.arguments_next_pc));
  __ blr(rscratch2);

  __ ldr(Rctx, Address(rfp, -2 * wordSize));   // Might have clobbered Rctx

  __ block_comment("store_registers");

  for (int i = 0; i < abi._integer_return_registers.length(); i++) {
    ssize_t offs = layout.returns_integer + i * sizeof(uintptr_t);
    __ str(abi._integer_return_registers.at(i), Address(Rctx, offs));
  }

  for (int i = 0; i < abi._vector_return_registers.length(); i++) {
    ssize_t offs = layout.returns_vector + i * sizeof(VectorRegister);
    __ strq(abi._vector_return_registers.at(i), Address(Rctx, offs));
  }

  __ leave();
  __ ret(lr);

  __ flush();
}

class ProgrammableInvokerGenerator : public StubCodeGenerator {
private:
  const ABIDescriptor* _abi;
  const BufferLayout* _layout;
public:
  ProgrammableInvokerGenerator(CodeBuffer* code, const ABIDescriptor* abi, const BufferLayout* layout)
    : StubCodeGenerator(code, PrintMethodHandleStubs),
      _abi(abi),
      _layout(layout) {}

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
