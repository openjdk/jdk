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
#include "asm/macroAssembler.hpp"
#include "code/codeBlob.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "prims/foreign_globals.inline.hpp"
#include "prims/universalNativeInvoker.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/formatBuffer.hpp"

#define __ _masm->

void ProgrammableInvoker::Generator::generate() {
  __ enter();

  // Put the context pointer in ebx/rbx - it's going to be heavily used below both before and after the call
  Register ctxt_reg = rbx;
  Register used_regs[] = { ctxt_reg, rcx, rsi, rdi };
  GrowableArray<Register> preserved_regs;

  for (size_t i = 0; i < sizeof(used_regs)/sizeof(Register); i++) {
    Register used_reg = used_regs[i];
    if (!_abi->is_volatile_reg(used_reg)) {
      preserved_regs.push(used_reg);
    }
  }

  __ block_comment("init_and_alloc_stack");

  for (int i = 0; i < preserved_regs.length(); i++) {
    __ push(preserved_regs.at(i));
  }

  __ movptr(ctxt_reg, c_rarg0); // FIXME c args? or java?

  __ block_comment("allocate_stack");
  __ movptr(rcx, Address(ctxt_reg, (int) _layout->stack_args_bytes));
  __ subptr(rsp, rcx);
  __ andptr(rsp, -_abi->_stack_alignment_bytes);

  // Note: rcx is used below!


  __ block_comment("load_arguments");

  __ shrptr(rcx, LogBytesPerWord); // bytes -> words
  __ movptr(rsi, Address(ctxt_reg, (int) _layout->stack_args));
  __ movptr(rdi, rsp);
  __ rep_mov();


  for (int i = 0; i < _abi->_vector_argument_registers.length(); i++) {
    // [1] -> 64 bit -> xmm
    // [2] -> 128 bit -> xmm
    // [4] -> 256 bit -> ymm
    // [8] -> 512 bit -> zmm

    XMMRegister reg = _abi->_vector_argument_registers.at(i);
    size_t offs = _layout->arguments_vector + i * xmm_reg_size;
    __ movdqu(reg, Address(ctxt_reg, (int)offs));
  }

  for (int i = 0; i < _abi->_integer_argument_registers.length(); i++) {
    size_t offs = _layout->arguments_integer + i * sizeof(uintptr_t);
    __ movptr(_abi->_integer_argument_registers.at(i), Address(ctxt_reg, (int)offs));
  }

  if (_abi->_shadow_space_bytes != 0) {
    __ block_comment("allocate shadow space for argument register spill");
    __ subptr(rsp, _abi->_shadow_space_bytes);
  }

  // call target function
  __ block_comment("call target function");
  __ call(Address(ctxt_reg, (int) _layout->arguments_next_pc));

  if (_abi->_shadow_space_bytes != 0) {
    __ block_comment("pop shadow space");
    __ addptr(rsp, _abi->_shadow_space_bytes);
  }

  __ block_comment("store_registers");
  for (int i = 0; i < _abi->_integer_return_registers.length(); i++) {
    ssize_t offs = _layout->returns_integer + i * sizeof(uintptr_t);
    __ movptr(Address(ctxt_reg, offs), _abi->_integer_return_registers.at(i));
  }

  for (int i = 0; i < _abi->_vector_return_registers.length(); i++) {
    // [1] -> 64 bit -> xmm
    // [2] -> 128 bit -> xmm (SSE)
    // [4] -> 256 bit -> ymm (AVX)
    // [8] -> 512 bit -> zmm (AVX-512, aka AVX3)

    XMMRegister reg = _abi->_vector_return_registers.at(i);
    size_t offs = _layout->returns_vector + i * xmm_reg_size;
    __ movdqu(Address(ctxt_reg, (int)offs), reg);
  }

  for (size_t i = 0; i < _abi->_X87_return_registers_noof; i++) {
    size_t offs = _layout->returns_x87 + i * (sizeof(long double));
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

address ProgrammableInvoker::generate_adapter(jobject jabi, jobject jlayout) {
  ResourceMark rm;
  const ABIDescriptor abi = ForeignGlobals::parse_abi_descriptor(jabi);
  const BufferLayout layout = ForeignGlobals::parse_buffer_layout(jlayout);

  BufferBlob* _invoke_native_blob = BufferBlob::create("invoke_native_blob", native_invoker_size);

  CodeBuffer code2(_invoke_native_blob);
  ProgrammableInvoker::Generator g2(&code2, &abi, &layout);
  g2.generate();
  code2.log_section_sizes("InvokeNativeBlob");

  return _invoke_native_blob->code_begin();
}

static const int native_invoker_code_size = 1024;

class NativeInvokerGenerator : public StubCodeGenerator {
  BasicType* _signature;
  int _num_args;
  BasicType _ret_bt;
  int _shadow_space_bytes;

  const GrowableArray<VMReg>& _input_registers;
  const GrowableArray<VMReg>& _output_registers;

  int _frame_complete;
  int _framesize;
  OopMapSet* _oop_maps;
public:
  NativeInvokerGenerator(CodeBuffer* buffer,
                         BasicType* signature,
                         int num_args,
                         BasicType ret_bt,
                         int shadow_space_bytes,
                         const GrowableArray<VMReg>& input_registers,
                         const GrowableArray<VMReg>& output_registers)
   : StubCodeGenerator(buffer, PrintMethodHandleStubs),
     _signature(signature),
     _num_args(num_args),
     _ret_bt(ret_bt),
     _shadow_space_bytes(shadow_space_bytes),
     _input_registers(input_registers),
     _output_registers(output_registers),
     _frame_complete(0),
     _framesize(0),
     _oop_maps(NULL) {
    assert(_output_registers.length() <= 1
           || (_output_registers.length() == 2 && !_output_registers.at(1)->is_valid()), "no multi-reg returns");

  }

  void generate();

  int frame_complete() const {
    return _frame_complete;
  }

  int framesize() const {
    return (_framesize >> (LogBytesPerWord - LogBytesPerInt));
  }

  OopMapSet* oop_maps() const {
    return _oop_maps;
  }

private:
#ifdef ASSERT
bool target_uses_register(VMReg reg) {
  return _input_registers.contains(reg) || _output_registers.contains(reg);
}
#endif
};

RuntimeStub* ProgrammableInvoker::make_native_invoker(BasicType* signature,
                                                      int num_args,
                                                      BasicType ret_bt,
                                                      int shadow_space_bytes,
                                                      const GrowableArray<VMReg>& input_registers,
                                                      const GrowableArray<VMReg>& output_registers) {
  int locs_size  = 64;
  CodeBuffer code("nep_invoker_blob", native_invoker_code_size, locs_size);
  NativeInvokerGenerator g(&code, signature, num_args, ret_bt, shadow_space_bytes, input_registers, output_registers);
  g.generate();
  code.log_section_sizes("nep_invoker_blob");

  RuntimeStub* stub =
    RuntimeStub::new_runtime_stub("nep_invoker_blob",
                                  &code,
                                  g.frame_complete(),
                                  g.framesize(),
                                  g.oop_maps(), false);

  if (TraceNativeInvokers) {
    stub->print_on(tty);
  }

  return stub;
}

void NativeInvokerGenerator::generate() {
  assert(!(target_uses_register(r15_thread->as_VMReg()) || target_uses_register(rscratch1->as_VMReg())), "Register conflict");

  enum layout {
    rbp_off,
    rbp_off2,
    return_off,
    return_off2,
    framesize_base // inclusive of return address
    // The following are also computed dynamically:
    // shadow space
    // spill area
    // out arg area (e.g. for stack args)
  };

  Register input_addr_reg = rscratch1;
  JavaCallConv in_conv;
  DowncallNativeCallConv out_conv(_input_registers, input_addr_reg->as_VMReg());
  ArgumentShuffle arg_shuffle(_signature, _num_args, _signature, _num_args, &in_conv, &out_conv, rbx->as_VMReg());

#ifdef ASSERT
  LogTarget(Trace, panama) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    arg_shuffle.print_on(&ls);
  }
#endif

  // in bytes
  int allocated_frame_size = 0;
  allocated_frame_size += arg_shuffle.out_arg_stack_slots() << LogBytesPerInt;
  allocated_frame_size += _shadow_space_bytes;

  RegSpiller out_reg_spiller(_output_registers);
  int spill_rsp_offset = 0;

  // spill area can be shared with the above, so we take the max of the 2
  allocated_frame_size = out_reg_spiller.spill_size_bytes() > allocated_frame_size
    ? out_reg_spiller.spill_size_bytes()
    : allocated_frame_size;
  allocated_frame_size = align_up(allocated_frame_size, 16);
  // _framesize is in 32-bit stack slots:
  _framesize += framesize_base + (allocated_frame_size >> LogBytesPerInt);
  assert(is_even(_framesize/2), "sp not 16-byte aligned");

  _oop_maps  = new OopMapSet();
  MacroAssembler* masm = _masm;

  address start = __ pc();

  __ enter();

  // return address and rbp are already in place
  __ subptr(rsp, allocated_frame_size); // prolog

  _frame_complete = __ pc() - start;

  address the_pc = __ pc();

  __ block_comment("{ thread java2native");
  __ set_last_Java_frame(rsp, rbp, (address)the_pc);
  OopMap* map = new OopMap(_framesize, 0);
  _oop_maps->add_gc_map(the_pc - start, map);

  // State transition
  __ movl(Address(r15_thread, JavaThread::thread_state_offset()), _thread_in_native);
  __ block_comment("} thread java2native");

  __ block_comment("{ argument shuffle");
  arg_shuffle.generate(_masm);
  __ block_comment("} argument shuffle");

  __ call(input_addr_reg);

  // Unpack native results.
  switch (_ret_bt) {
    case T_BOOLEAN: __ c2bool(rax);            break;
    case T_CHAR   : __ movzwl(rax, rax);       break;
    case T_BYTE   : __ sign_extend_byte (rax); break;
    case T_SHORT  : __ sign_extend_short(rax); break;
    case T_INT    : /* nothing to do */        break;
    case T_DOUBLE :
    case T_FLOAT  :
      // Result is in xmm0 we'll save as needed
      break;
    case T_VOID: break;
    case T_LONG: break;
    default       : ShouldNotReachHere();
  }

  __ block_comment("{ thread native2java");
  __ restore_cpu_control_state_after_jni();

  __ movl(Address(r15_thread, JavaThread::thread_state_offset()), _thread_in_native_trans);

  // Force this write out before the read below
  __ membar(Assembler::Membar_mask_bits(
          Assembler::LoadLoad | Assembler::LoadStore |
          Assembler::StoreLoad | Assembler::StoreStore));

  Label L_after_safepoint_poll;
  Label L_safepoint_poll_slow_path;

  __ safepoint_poll(L_safepoint_poll_slow_path, r15_thread, true /* at_return */, false /* in_nmethod */);
  __ cmpl(Address(r15_thread, JavaThread::suspend_flags_offset()), 0);
  __ jcc(Assembler::notEqual, L_safepoint_poll_slow_path);

  __ bind(L_after_safepoint_poll);

  // change thread state
  __ movl(Address(r15_thread, JavaThread::thread_state_offset()), _thread_in_Java);

  __ block_comment("reguard stack check");
  Label L_reguard;
  Label L_after_reguard;
  __ cmpl(Address(r15_thread, JavaThread::stack_guard_state_offset()), StackOverflow::stack_guard_yellow_reserved_disabled);
  __ jcc(Assembler::equal, L_reguard);
  __ bind(L_after_reguard);

  __ reset_last_Java_frame(r15_thread, true);
  __ block_comment("} thread native2java");

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  //////////////////////////////////////////////////////////////////////////////

  __ block_comment("{ L_safepoint_poll_slow_path");
  __ bind(L_safepoint_poll_slow_path);
  __ vzeroupper();

  out_reg_spiller.generate_spill(_masm, spill_rsp_offset);

  __ mov(c_rarg0, r15_thread);
  __ mov(r12, rsp); // remember sp
  __ subptr(rsp, frame::arg_reg_save_area_bytes); // windows
  __ andptr(rsp, -16); // align stack as required by ABI
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans)));
  __ mov(rsp, r12); // restore sp
  __ reinit_heapbase();

  out_reg_spiller.generate_fill(_masm, spill_rsp_offset);

  __ jmp(L_after_safepoint_poll);
  __ block_comment("} L_safepoint_poll_slow_path");

  //////////////////////////////////////////////////////////////////////////////

  __ block_comment("{ L_reguard");
  __ bind(L_reguard);
  __ vzeroupper();

  out_reg_spiller.generate_spill(_masm, spill_rsp_offset);

  __ mov(r12, rsp); // remember sp
  __ subptr(rsp, frame::arg_reg_save_area_bytes); // windows
  __ andptr(rsp, -16); // align stack as required by ABI
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages)));
  __ mov(rsp, r12); // restore sp
  __ reinit_heapbase();

  out_reg_spiller.generate_fill(_masm, spill_rsp_offset);

  __ jmp(L_after_reguard);

  __ block_comment("} L_reguard");

  //////////////////////////////////////////////////////////////////////////////

  __ flush();
}

bool ProgrammableInvoker::supports_native_invoker() {
  return true;
}
