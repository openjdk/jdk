/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2024, Huawei Technologies Co., Ltd. All rights reserved.
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
 *
 */

#include "asm/assembler.hpp"
#include "asm/assembler.inline.hpp"
#include "code/compiledIC.hpp"
#include "compiler/disassembler.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/accessDecorators.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/klass.inline.hpp"
#include "oops/oop.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/powerOfTwo.hpp"
#ifdef COMPILER2
#include "opto/compile.hpp"
#include "opto/node.hpp"
#include "opto/output.hpp"
#endif

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) block_comment(str)
#endif
#define STOP(str) stop(str);
#define BIND(label) bind(label); __ BLOCK_COMMENT(#label ":")



Register MacroAssembler::extract_rs1(address instr) {
  assert_cond(instr != nullptr);
  return as_Register(Assembler::extract(Assembler::ld_instr(instr), 19, 15));
}

Register MacroAssembler::extract_rs2(address instr) {
  assert_cond(instr != nullptr);
  return as_Register(Assembler::extract(Assembler::ld_instr(instr), 24, 20));
}

Register MacroAssembler::extract_rd(address instr) {
  assert_cond(instr != nullptr);
  return as_Register(Assembler::extract(Assembler::ld_instr(instr), 11, 7));
}

uint32_t MacroAssembler::extract_opcode(address instr) {
  assert_cond(instr != nullptr);
  return Assembler::extract(Assembler::ld_instr(instr), 6, 0);
}

uint32_t MacroAssembler::extract_funct3(address instr) {
  assert_cond(instr != nullptr);
  return Assembler::extract(Assembler::ld_instr(instr), 14, 12);
}

bool MacroAssembler::is_pc_relative_at(address instr) {
  // auipc + jalr
  // auipc + addi
  // auipc + load
  // auipc + fload_load
  return (is_auipc_at(instr)) &&
         (is_addi_at(instr + MacroAssembler::instruction_size) ||
          is_jalr_at(instr + MacroAssembler::instruction_size) ||
          is_load_at(instr + MacroAssembler::instruction_size) ||
          is_float_load_at(instr + MacroAssembler::instruction_size)) &&
         check_pc_relative_data_dependency(instr);
}

// ie:ld(Rd, Label)
bool MacroAssembler::is_load_pc_relative_at(address instr) {
  return is_auipc_at(instr) && // auipc
         is_ld_at(instr + MacroAssembler::instruction_size) && // ld
         check_load_pc_relative_data_dependency(instr);
}

bool MacroAssembler::is_movptr1_at(address instr) {
  return is_lui_at(instr) && // Lui
         is_addi_at(instr + MacroAssembler::instruction_size) && // Addi
         is_slli_shift_at(instr + MacroAssembler::instruction_size * 2, 11) && // Slli Rd, Rs, 11
         is_addi_at(instr + MacroAssembler::instruction_size * 3) && // Addi
         is_slli_shift_at(instr + MacroAssembler::instruction_size * 4, 6) && // Slli Rd, Rs, 6
         (is_addi_at(instr + MacroAssembler::instruction_size * 5) ||
          is_jalr_at(instr + MacroAssembler::instruction_size * 5) ||
          is_load_at(instr + MacroAssembler::instruction_size * 5)) && // Addi/Jalr/Load
         check_movptr1_data_dependency(instr);
}

bool MacroAssembler::is_movptr2_at(address instr) {
  return is_lui_at(instr) && // lui
         is_lui_at(instr + MacroAssembler::instruction_size) && // lui
         is_slli_shift_at(instr + MacroAssembler::instruction_size * 2, 18) && // slli Rd, Rs, 18
         is_add_at(instr + MacroAssembler::instruction_size * 3) &&
         (is_addi_at(instr + MacroAssembler::instruction_size * 4) ||
          is_jalr_at(instr + MacroAssembler::instruction_size * 4) ||
          is_load_at(instr + MacroAssembler::instruction_size * 4)) && // Addi/Jalr/Load
         check_movptr2_data_dependency(instr);
}

bool MacroAssembler::is_li16u_at(address instr) {
  return is_lui_at(instr) && // lui
         is_srli_at(instr + MacroAssembler::instruction_size) && // srli
         check_li16u_data_dependency(instr);
}

bool MacroAssembler::is_li32_at(address instr) {
  return is_lui_at(instr) && // lui
         is_addiw_at(instr + MacroAssembler::instruction_size) && // addiw
         check_li32_data_dependency(instr);
}

bool MacroAssembler::is_lwu_to_zr(address instr) {
  assert_cond(instr != nullptr);
  return (extract_opcode(instr) == 0b0000011 &&
          extract_funct3(instr) == 0b110 &&
          extract_rd(instr) == zr);         // zr
}

uint32_t MacroAssembler::get_membar_kind(address addr) {
  assert_cond(addr != nullptr);
  assert(is_membar(addr), "no membar found");

  uint32_t insn = Bytes::get_native_u4(addr);

  uint32_t predecessor = Assembler::extract(insn, 27, 24);
  uint32_t successor = Assembler::extract(insn, 23, 20);

  return MacroAssembler::pred_succ_to_membar_mask(predecessor, successor);
}

void MacroAssembler::set_membar_kind(address addr, uint32_t order_kind) {
  assert_cond(addr != nullptr);
  assert(is_membar(addr), "no membar found");

  uint32_t predecessor = 0;
  uint32_t successor = 0;

  MacroAssembler::membar_mask_to_pred_succ(order_kind, predecessor, successor);

  uint32_t insn = Bytes::get_native_u4(addr);
  address pInsn = (address) &insn;
  Assembler::patch(pInsn, 27, 24, predecessor);
  Assembler::patch(pInsn, 23, 20, successor);

  address membar = addr;
  Assembler::sd_instr(membar, insn);
}

static void pass_arg0(MacroAssembler* masm, Register arg) {
  if (c_rarg0 != arg) {
    masm->mv(c_rarg0, arg);
  }
}

static void pass_arg1(MacroAssembler* masm, Register arg) {
  if (c_rarg1 != arg) {
    masm->mv(c_rarg1, arg);
  }
}

static void pass_arg2(MacroAssembler* masm, Register arg) {
  if (c_rarg2 != arg) {
    masm->mv(c_rarg2, arg);
  }
}

static void pass_arg3(MacroAssembler* masm, Register arg) {
  if (c_rarg3 != arg) {
    masm->mv(c_rarg3, arg);
  }
}

void MacroAssembler::push_cont_fastpath(Register java_thread) {
  if (!Continuations::enabled()) return;
  Label done;
  ld(t0, Address(java_thread, JavaThread::cont_fastpath_offset()));
  bleu(sp, t0, done);
  sd(sp, Address(java_thread, JavaThread::cont_fastpath_offset()));
  bind(done);
}

void MacroAssembler::pop_cont_fastpath(Register java_thread) {
  if (!Continuations::enabled()) return;
  Label done;
  ld(t0, Address(java_thread, JavaThread::cont_fastpath_offset()));
  bltu(sp, t0, done);
  sd(zr, Address(java_thread, JavaThread::cont_fastpath_offset()));
  bind(done);
}

int MacroAssembler::align(int modulus, int extra_offset) {
  CompressibleScope scope(this);
  intptr_t before = offset();
  while ((offset() + extra_offset) % modulus != 0) { nop(); }
  return (int)(offset() - before);
}

void MacroAssembler::call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions) {
  call_VM_base(oop_result, noreg, noreg, nullptr, entry_point, number_of_arguments, check_exceptions);
}

// Implementation of call_VM versions

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             bool check_exceptions) {
  call_VM_helper(oop_result, entry_point, 0, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             bool check_exceptions) {
  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 1, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             bool check_exceptions) {
  assert_different_registers(arg_1, c_rarg2);
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 2, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             Register arg_3,
                             bool check_exceptions) {
  assert_different_registers(arg_1, c_rarg2, c_rarg3);
  assert_different_registers(arg_2, c_rarg3);
  pass_arg3(this, arg_3);

  pass_arg2(this, arg_2);

  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 3, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             int number_of_arguments,
                             bool check_exceptions) {
  call_VM_base(oop_result, xthread, last_java_sp, nullptr, entry_point, number_of_arguments, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             bool check_exceptions) {
  pass_arg1(this, arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 1, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             bool check_exceptions) {

  assert_different_registers(arg_1, c_rarg2);
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 2, check_exceptions);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             Register arg_3,
                             bool check_exceptions) {
  assert_different_registers(arg_1, c_rarg2, c_rarg3);
  assert_different_registers(arg_2, c_rarg3);
  pass_arg3(this, arg_3);
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM(oop_result, last_java_sp, entry_point, 3, check_exceptions);
}

void MacroAssembler::post_call_nop() {
  assert(!in_compressible_scope(), "Must be");
  assert_alignment(pc());
  if (!Continuations::enabled()) {
    return;
  }
  relocate(post_call_nop_Relocation::spec());
  InlineSkippedInstructionsCounter skipCounter(this);
  nop();
  li32(zr, 0);
}

// these are no-ops overridden by InterpreterMacroAssembler
void MacroAssembler::check_and_handle_earlyret(Register java_thread) {}
void MacroAssembler::check_and_handle_popframe(Register java_thread) {}

// Calls to C land
//
// When entering C land, the fp, & esp of the last Java frame have to be recorded
// in the (thread-local) JavaThread object. When leaving C land, the last Java fp
// has to be reset to 0. This is required to allow proper stack traversal.
void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         Register last_java_pc) {

  if (last_java_pc->is_valid()) {
    sd(last_java_pc, Address(xthread,
                             JavaThread::frame_anchor_offset() +
                             JavaFrameAnchor::last_Java_pc_offset()));
  }

  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = esp;
  }

  // last_java_fp is optional
  if (last_java_fp->is_valid()) {
    sd(last_java_fp, Address(xthread, JavaThread::last_Java_fp_offset()));
  }

  // We must set sp last.
  sd(last_java_sp, Address(xthread, JavaThread::last_Java_sp_offset()));

}

void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         address  last_java_pc,
                                         Register tmp) {
  assert(last_java_pc != nullptr, "must provide a valid PC");

  la(tmp, last_java_pc);
  sd(tmp, Address(xthread, JavaThread::frame_anchor_offset() + JavaFrameAnchor::last_Java_pc_offset()));

  set_last_Java_frame(last_java_sp, last_java_fp, noreg);
}

void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         Label &L,
                                         Register tmp) {
  if (L.is_bound()) {
    set_last_Java_frame(last_java_sp, last_java_fp, target(L), tmp);
  } else {
    L.add_patch_at(code(), locator());
    IncompressibleScope scope(this); // the label address will be patched back.
    set_last_Java_frame(last_java_sp, last_java_fp, pc() /* Patched later */, tmp);
  }
}

void MacroAssembler::reset_last_Java_frame(bool clear_fp) {
  // we must set sp to zero to clear frame
  sd(zr, Address(xthread, JavaThread::last_Java_sp_offset()));

  // must clear fp, so that compiled frames are not confused; it is
  // possible that we need it only for debugging
  if (clear_fp) {
    sd(zr, Address(xthread, JavaThread::last_Java_fp_offset()));
  }

  // Always clear the pc because it could have been set by make_walkable()
  sd(zr, Address(xthread, JavaThread::last_Java_pc_offset()));
}

void MacroAssembler::call_VM_base(Register oop_result,
                                  Register java_thread,
                                  Register last_java_sp,
                                  Label*   return_pc,
                                  address  entry_point,
                                  int      number_of_arguments,
                                  bool     check_exceptions) {
   // determine java_thread register
  if (!java_thread->is_valid()) {
    java_thread = xthread;
  }

  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = esp;
  }

  // debugging support
  assert(number_of_arguments >= 0   , "cannot have negative number of arguments");
  assert(java_thread == xthread, "unexpected register");

  assert(java_thread != oop_result  , "cannot use the same register for java_thread & oop_result");
  assert(java_thread != last_java_sp, "cannot use the same register for java_thread & last_java_sp");

  // push java thread (becomes first argument of C function)
  mv(c_rarg0, java_thread);

  // set last Java frame before call
  assert(last_java_sp != fp, "can't use fp");

  Label l;
  set_last_Java_frame(last_java_sp, fp, return_pc != nullptr ? *return_pc : l, t0);

  // do the call, remove parameters
  MacroAssembler::call_VM_leaf_base(entry_point, number_of_arguments, &l);

  // reset last Java frame
  // Only interpreter should have to clear fp
  reset_last_Java_frame(true);

   // C++ interp handles this in the interpreter
  check_and_handle_popframe(java_thread);
  check_and_handle_earlyret(java_thread);

  if (check_exceptions) {
    // check for pending exceptions (java_thread is set upon return)
    ld(t0, Address(java_thread, in_bytes(Thread::pending_exception_offset())));
    Label ok;
    beqz(t0, ok);
    j(RuntimeAddress(StubRoutines::forward_exception_entry()));
    bind(ok);
  }

  // get oop result if there is one and reset the value in the thread
  if (oop_result->is_valid()) {
    get_vm_result_oop(oop_result, java_thread);
  }
}

void MacroAssembler::get_vm_result_oop(Register oop_result, Register java_thread) {
  ld(oop_result, Address(java_thread, JavaThread::vm_result_oop_offset()));
  sd(zr, Address(java_thread, JavaThread::vm_result_oop_offset()));
  verify_oop_msg(oop_result, "broken oop in call_VM_base");
}

void MacroAssembler::get_vm_result_metadata(Register metadata_result, Register java_thread) {
  ld(metadata_result, Address(java_thread, JavaThread::vm_result_metadata_offset()));
  sd(zr, Address(java_thread, JavaThread::vm_result_metadata_offset()));
}

void MacroAssembler::clinit_barrier(Register klass, Register tmp, Label* L_fast_path, Label* L_slow_path) {
  assert(L_fast_path != nullptr || L_slow_path != nullptr, "at least one is required");
  assert_different_registers(klass, xthread, tmp);

  Label L_fallthrough, L_tmp;
  if (L_fast_path == nullptr) {
    L_fast_path = &L_fallthrough;
  } else if (L_slow_path == nullptr) {
    L_slow_path = &L_fallthrough;
  }

  // Fast path check: class is fully initialized
  lbu(tmp, Address(klass, InstanceKlass::init_state_offset()));
  membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
  sub(tmp, tmp, InstanceKlass::fully_initialized);
  beqz(tmp, *L_fast_path);

  // Fast path check: current thread is initializer thread
  ld(tmp, Address(klass, InstanceKlass::init_thread_offset()));

  if (L_slow_path == &L_fallthrough) {
    beq(xthread, tmp, *L_fast_path);
    bind(*L_slow_path);
  } else if (L_fast_path == &L_fallthrough) {
    bne(xthread, tmp, *L_slow_path);
    bind(*L_fast_path);
  } else {
    Unimplemented();
  }
}

void MacroAssembler::_verify_oop(Register reg, const char* s, const char* file, int line) {
  if (!VerifyOops) { return; }

  // Pass register number to verify_oop_subroutine
  const char* b = nullptr;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("verify_oop: %s: %s (%s:%d)", reg->name(), s, file, line);
    b = code_string(ss.as_string());
  }
  BLOCK_COMMENT("verify_oop {");

  push_reg(RegSet::of(ra, t0, t1, c_rarg0), sp);

  mv(c_rarg0, reg); // c_rarg0 : x10
  {
    // The length of the instruction sequence emitted should not depend
    // on the address of the char buffer so that the size of mach nodes for
    // scratch emit and normal emit matches.
    IncompressibleScope scope(this); // Fixed length
    movptr(t0, (address) b);
  }

  // Call indirectly to solve generation ordering problem
  ld(t1, RuntimeAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  jalr(t1);

  pop_reg(RegSet::of(ra, t0, t1, c_rarg0), sp);

  BLOCK_COMMENT("} verify_oop");
}

void MacroAssembler::_verify_oop_addr(Address addr, const char* s, const char* file, int line) {
  if (!VerifyOops) {
    return;
  }

  const char* b = nullptr;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("verify_oop_addr: %s (%s:%d)", s, file, line);
    b = code_string(ss.as_string());
  }
  BLOCK_COMMENT("verify_oop_addr {");

  push_reg(RegSet::of(ra, t0, t1, c_rarg0), sp);

  if (addr.uses(sp)) {
    la(x10, addr);
    ld(x10, Address(x10, 4 * wordSize));
  } else {
    ld(x10, addr);
  }

  {
    // The length of the instruction sequence emitted should not depend
    // on the address of the char buffer so that the size of mach nodes for
    // scratch emit and normal emit matches.
    IncompressibleScope scope(this); // Fixed length
    movptr(t0, (address) b);
  }

  // Call indirectly to solve generation ordering problem
  ld(t1, RuntimeAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  jalr(t1);

  pop_reg(RegSet::of(ra, t0, t1, c_rarg0), sp);

  BLOCK_COMMENT("} verify_oop_addr");
}

Address MacroAssembler::argument_address(RegisterOrConstant arg_slot,
                                         int extra_slot_offset) {
  // cf. TemplateTable::prepare_invoke(), if (load_receiver).
  int stackElementSize = Interpreter::stackElementSize;
  int offset = Interpreter::expr_offset_in_bytes(extra_slot_offset+0);
#ifdef ASSERT
  int offset1 = Interpreter::expr_offset_in_bytes(extra_slot_offset+1);
  assert(offset1 - offset == stackElementSize, "correct arithmetic");
#endif
  if (arg_slot.is_constant()) {
    return Address(esp, arg_slot.as_constant() * stackElementSize + offset);
  } else {
    assert_different_registers(t0, arg_slot.as_register());
    shadd(t0, arg_slot.as_register(), esp, t0, exact_log2(stackElementSize));
    return Address(t0, offset);
  }
}

#ifndef PRODUCT
extern "C" void findpc(intptr_t x);
#endif

void MacroAssembler::debug64(char* msg, int64_t pc, int64_t regs[])
{
  // In order to get locks to work, we need to fake a in_VM state
  if (ShowMessageBoxOnError) {
    JavaThread* thread = JavaThread::current();
    JavaThreadState saved_state = thread->thread_state();
    thread->set_thread_state(_thread_in_vm);
#ifndef PRODUCT
    if (CountBytecodes || TraceBytecodes || StopInterpreterAt) {
      ttyLocker ttyl;
      BytecodeCounter::print();
    }
#endif
    if (os::message_box(msg, "Execution stopped, print registers?")) {
      ttyLocker ttyl;
      tty->print_cr(" pc = 0x%016lx", pc);
#ifndef PRODUCT
      tty->cr();
      findpc(pc);
      tty->cr();
#endif
      tty->print_cr(" x0 = 0x%016lx", regs[0]);
      tty->print_cr(" x1 = 0x%016lx", regs[1]);
      tty->print_cr(" x2 = 0x%016lx", regs[2]);
      tty->print_cr(" x3 = 0x%016lx", regs[3]);
      tty->print_cr(" x4 = 0x%016lx", regs[4]);
      tty->print_cr(" x5 = 0x%016lx", regs[5]);
      tty->print_cr(" x6 = 0x%016lx", regs[6]);
      tty->print_cr(" x7 = 0x%016lx", regs[7]);
      tty->print_cr(" x8 = 0x%016lx", regs[8]);
      tty->print_cr(" x9 = 0x%016lx", regs[9]);
      tty->print_cr("x10 = 0x%016lx", regs[10]);
      tty->print_cr("x11 = 0x%016lx", regs[11]);
      tty->print_cr("x12 = 0x%016lx", regs[12]);
      tty->print_cr("x13 = 0x%016lx", regs[13]);
      tty->print_cr("x14 = 0x%016lx", regs[14]);
      tty->print_cr("x15 = 0x%016lx", regs[15]);
      tty->print_cr("x16 = 0x%016lx", regs[16]);
      tty->print_cr("x17 = 0x%016lx", regs[17]);
      tty->print_cr("x18 = 0x%016lx", regs[18]);
      tty->print_cr("x19 = 0x%016lx", regs[19]);
      tty->print_cr("x20 = 0x%016lx", regs[20]);
      tty->print_cr("x21 = 0x%016lx", regs[21]);
      tty->print_cr("x22 = 0x%016lx", regs[22]);
      tty->print_cr("x23 = 0x%016lx", regs[23]);
      tty->print_cr("x24 = 0x%016lx", regs[24]);
      tty->print_cr("x25 = 0x%016lx", regs[25]);
      tty->print_cr("x26 = 0x%016lx", regs[26]);
      tty->print_cr("x27 = 0x%016lx", regs[27]);
      tty->print_cr("x28 = 0x%016lx", regs[28]);
      tty->print_cr("x30 = 0x%016lx", regs[30]);
      tty->print_cr("x31 = 0x%016lx", regs[31]);
      BREAKPOINT;
    }
  }
  fatal("DEBUG MESSAGE: %s", msg);
}

void MacroAssembler::resolve_jobject(Register value, Register tmp1, Register tmp2) {
  assert_different_registers(value, tmp1, tmp2);
  Label done, tagged, weak_tagged;

  beqz(value, done);           // Use null as-is.
  // Test for tag.
  andi(tmp1, value, JNIHandles::tag_mask);
  bnez(tmp1, tagged);

  // Resolve local handle
  access_load_at(T_OBJECT, IN_NATIVE | AS_RAW, value, Address(value, 0), tmp1, tmp2);
  verify_oop(value);
  j(done);

  bind(tagged);
  // Test for jweak tag.
  STATIC_ASSERT(JNIHandles::TypeTag::weak_global == 0b1);
  test_bit(tmp1, value, exact_log2(JNIHandles::TypeTag::weak_global));
  bnez(tmp1, weak_tagged);

  // Resolve global handle
  access_load_at(T_OBJECT, IN_NATIVE, value,
                 Address(value, -JNIHandles::TypeTag::global), tmp1, tmp2);
  verify_oop(value);
  j(done);

  bind(weak_tagged);
  // Resolve jweak.
  access_load_at(T_OBJECT, IN_NATIVE | ON_PHANTOM_OOP_REF, value,
                 Address(value, -JNIHandles::TypeTag::weak_global), tmp1, tmp2);
  verify_oop(value);

  bind(done);
}

void MacroAssembler::resolve_global_jobject(Register value, Register tmp1, Register tmp2) {
  assert_different_registers(value, tmp1, tmp2);
  Label done;

  beqz(value, done);           // Use null as-is.

#ifdef ASSERT
  {
    STATIC_ASSERT(JNIHandles::TypeTag::global == 0b10);
    Label valid_global_tag;
    test_bit(tmp1, value, exact_log2(JNIHandles::TypeTag::global)); // Test for global tag.
    bnez(tmp1, valid_global_tag);
    stop("non global jobject using resolve_global_jobject");
    bind(valid_global_tag);
  }
#endif

  // Resolve global handle
  access_load_at(T_OBJECT, IN_NATIVE, value,
                 Address(value, -JNIHandles::TypeTag::global), tmp1, tmp2);
  verify_oop(value);

  bind(done);
}

void MacroAssembler::stop(const char* msg) {
  BLOCK_COMMENT(msg);
  illegal_instruction(Assembler::csr::time);
  emit_int64((uintptr_t)msg);
}

void MacroAssembler::unimplemented(const char* what) {
  const char* buf = nullptr;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("unimplemented: %s", what);
    buf = code_string(ss.as_string());
  }
  stop(buf);
}

void MacroAssembler::emit_static_call_stub() {
  IncompressibleScope scope(this); // Fixed length: see CompiledDirectCall::to_interp_stub_size().
  // CompiledDirectCall::set_to_interpreted knows the
  // exact layout of this stub.

  mov_metadata(xmethod, (Metadata*)nullptr);

  // Jump to the entry point of the c2i stub.
  int32_t offset = 0;
  movptr2(t1, 0, offset, t0); // lui + lui + slli + add
  jr(t1, offset);
}

void MacroAssembler::call_VM_leaf_base(address entry_point,
                                       int number_of_arguments,
                                       Label *retaddr) {
  int32_t offset = 0;
  push_reg(RegSet::of(t1, xmethod), sp);   // push << t1 & xmethod >> to sp
  movptr(t1, entry_point, offset, t0);
  jalr(t1, offset);
  if (retaddr != nullptr) {
    bind(*retaddr);
  }
  pop_reg(RegSet::of(t1, xmethod), sp);   // pop << t1 & xmethod >> from sp
}

void MacroAssembler::call_VM_leaf(address entry_point, int number_of_arguments) {
  call_VM_leaf_base(entry_point, number_of_arguments);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0) {
  pass_arg0(this, arg_0);
  call_VM_leaf_base(entry_point, 1);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0, Register arg_1) {
  assert_different_registers(arg_1, c_rarg0);
  pass_arg0(this, arg_0);
  pass_arg1(this, arg_1);
  call_VM_leaf_base(entry_point, 2);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0,
                                  Register arg_1, Register arg_2) {
  assert_different_registers(arg_1, c_rarg0);
  assert_different_registers(arg_2, c_rarg0, c_rarg1);
  pass_arg0(this, arg_0);
  pass_arg1(this, arg_1);
  pass_arg2(this, arg_2);
  call_VM_leaf_base(entry_point, 3);
}

void MacroAssembler::super_call_VM_leaf(address entry_point, Register arg_0) {
  pass_arg0(this, arg_0);
  MacroAssembler::call_VM_leaf_base(entry_point, 1);
}

void MacroAssembler::super_call_VM_leaf(address entry_point, Register arg_0, Register arg_1) {

  assert_different_registers(arg_0, c_rarg1);
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  MacroAssembler::call_VM_leaf_base(entry_point, 2);
}

void MacroAssembler::super_call_VM_leaf(address entry_point, Register arg_0, Register arg_1, Register arg_2) {
  assert_different_registers(arg_0, c_rarg1, c_rarg2);
  assert_different_registers(arg_1, c_rarg2);
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  MacroAssembler::call_VM_leaf_base(entry_point, 3);
}

void MacroAssembler::super_call_VM_leaf(address entry_point, Register arg_0, Register arg_1, Register arg_2, Register arg_3) {
  assert_different_registers(arg_0, c_rarg1, c_rarg2, c_rarg3);
  assert_different_registers(arg_1, c_rarg2, c_rarg3);
  assert_different_registers(arg_2, c_rarg3);

  pass_arg3(this, arg_3);
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  MacroAssembler::call_VM_leaf_base(entry_point, 4);
}

void MacroAssembler::la(Register Rd, const address addr) {
  int32_t offset;
  la(Rd, addr, offset);
  addi(Rd, Rd, offset);
}

void MacroAssembler::la(Register Rd, const address addr, int32_t &offset) {
  int64_t distance = addr - pc();
  assert(is_valid_32bit_offset(distance), "Must be");
  auipc(Rd, (int32_t)distance + 0x800);
  offset = ((int32_t)distance << 20) >> 20;
}

// Materialize with auipc + addi sequence if adr is a literal
// address inside code cache. Emit a movptr sequence otherwise.
void MacroAssembler::la(Register Rd, const Address &adr) {
  switch (adr.getMode()) {
    case Address::literal: {
      relocInfo::relocType rtype = adr.rspec().reloc()->type();
      if (rtype == relocInfo::none) {
        mv(Rd, (intptr_t)(adr.target()));
      } else {
        if (CodeCache::contains(adr.target())) {
          relocate(adr.rspec(), [&] {
            la(Rd, adr.target());
          });
        } else {
          relocate(adr.rspec(), [&] {
            movptr(Rd, adr.target());
          });
        }
      }
      break;
    }
    case Address::base_plus_offset: {
      Address new_adr = legitimize_address(Rd, adr);
      if (!(new_adr.base() == Rd && new_adr.offset() == 0)) {
        addi(Rd, new_adr.base(), new_adr.offset());
      }
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void MacroAssembler::la(Register Rd, Label &label) {
  IncompressibleScope scope(this); // the label address may be patched back.
  wrap_label(Rd, label, &MacroAssembler::la);
}

void MacroAssembler::li16u(Register Rd, uint16_t imm) {
  lui(Rd, (uint32_t)imm << 12);
  srli(Rd, Rd, 12);
}

void MacroAssembler::li32(Register Rd, int32_t imm) {
  // int32_t is in range 0x8000 0000 ~ 0x7fff ffff, and imm[31] is the sign bit
  int64_t upper = imm, lower = imm;
  lower = (imm << 20) >> 20;
  upper -= lower;
  upper = (int32_t)upper;
  // lui Rd, imm[31:12] + imm[11]
  lui(Rd, upper);
  addiw(Rd, Rd, lower);
}

void MacroAssembler::li(Register Rd, int64_t imm) {
  // int64_t is in range 0x8000 0000 0000 0000 ~ 0x7fff ffff ffff ffff
  // li -> c.li
  if (do_compress() && (is_simm6(imm) && Rd != x0)) {
    c_li(Rd, imm);
    return;
  }

  int shift = 12;
  int64_t upper = imm, lower = imm;
  // Split imm to a lower 12-bit sign-extended part and the remainder,
  // because addi will sign-extend the lower imm.
  lower = ((int32_t)imm << 20) >> 20;
  upper -= lower;

  // Test whether imm is a 32-bit integer.
  if (!(((imm) & ~(int64_t)0x7fffffff) == 0 ||
        (((imm) & ~(int64_t)0x7fffffff) == ~(int64_t)0x7fffffff))) {
    while (((upper >> shift) & 1) == 0) { shift++; }
    upper >>= shift;
    li(Rd, upper);
    slli(Rd, Rd, shift);
    if (lower != 0) {
      addi(Rd, Rd, lower);
    }
  } else {
    // 32-bit integer
    Register hi_Rd = zr;
    if (upper != 0) {
      lui(Rd, (int32_t)upper);
      hi_Rd = Rd;
    }
    if (lower != 0 || hi_Rd == zr) {
      addiw(Rd, hi_Rd, lower);
    }
  }
}

void MacroAssembler::j(const address dest, Register temp) {
  assert(CodeCache::contains(dest), "Must be");
  assert_cond(dest != nullptr);
  int64_t distance = dest - pc();

  // We can't patch C, i.e. if Label wasn't bound we need to patch this jump.
  IncompressibleScope scope(this);
  if (is_simm21(distance) && ((distance % 2) == 0)) {
    Assembler::jal(x0, distance);
  } else {
    assert(temp != noreg && temp != x0, "Expecting a register");
    assert(temp != x1 && temp != x5, "temp register must not be x1/x5.");
    int32_t offset = 0;
    la(temp, dest, offset);
    jr(temp, offset);
  }
}

void MacroAssembler::j(const Address &dest, Register temp) {
  switch (dest.getMode()) {
    case Address::literal: {
      if (CodeCache::contains(dest.target())) {
        far_jump(dest, temp);
      } else {
        relocate(dest.rspec(), [&] {
          int32_t offset;
          movptr(temp, dest.target(), offset);
          jr(temp, offset);
        });
      }
      break;
    }
    case Address::base_plus_offset: {
      int32_t offset = ((int32_t)dest.offset() << 20) >> 20;
      la(temp, Address(dest.base(), dest.offset() - offset));
      jr(temp, offset);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}

void MacroAssembler::j(Label &lab, Register temp) {
  assert_different_registers(x0, temp);
  if (lab.is_bound()) {
    MacroAssembler::j(target(lab), temp);
  } else {
    lab.add_patch_at(code(), locator());
    MacroAssembler::j(pc(), temp);
  }
}

void MacroAssembler::jr(Register Rd, int32_t offset) {
  assert(Rd != noreg, "expecting a register");
  assert(Rd != x1 && Rd != x5, "Rd register must not be x1/x5.");
  Assembler::jalr(x0, Rd, offset);
}

void MacroAssembler::call(const address dest, Register temp) {
  assert_cond(dest != nullptr);
  assert(temp != noreg, "expecting a register");
  assert(temp != x5, "temp register must not be x5.");
  int32_t offset = 0;
  la(temp, dest, offset);
  jalr(temp, offset);
}

void MacroAssembler::jalr(Register Rs, int32_t offset) {
  assert(Rs != noreg, "expecting a register");
  assert(Rs != x5, "Rs register must not be x5.");
  Assembler::jalr(x1, Rs, offset);
}

void MacroAssembler::rt_call(address dest, Register tmp) {
  assert(tmp != x5, "tmp register must not be x5.");
  RuntimeAddress target(dest);
  if (CodeCache::contains(dest)) {
    far_call(target, tmp);
  } else {
    relocate(target.rspec(), [&] {
      int32_t offset;
      movptr(tmp, target.target(), offset);
      jalr(tmp, offset);
    });
  }
}

void MacroAssembler::wrap_label(Register Rt, Label &L, jal_jalr_insn insn) {
  if (L.is_bound()) {
    (this->*insn)(Rt, target(L));
  } else {
    L.add_patch_at(code(), locator());
    (this->*insn)(Rt, pc());
  }
}

void MacroAssembler::wrap_label(Register r1, Register r2, Label &L,
                                compare_and_branch_insn insn,
                                compare_and_branch_label_insn neg_insn, bool is_far) {
  if (is_far) {
    Label done;
    (this->*neg_insn)(r1, r2, done, /* is_far */ false);
    j(L);
    bind(done);
  } else {
    if (L.is_bound()) {
      (this->*insn)(r1, r2, target(L));
    } else {
      L.add_patch_at(code(), locator());
      (this->*insn)(r1, r2, pc());
    }
  }
}

#define INSN(NAME, NEG_INSN)                                                              \
  void MacroAssembler::NAME(Register Rs1, Register Rs2, Label &L, bool is_far) {          \
    wrap_label(Rs1, Rs2, L, &MacroAssembler::NAME, &MacroAssembler::NEG_INSN, is_far);    \
  }

  INSN(beq,  bne);
  INSN(bne,  beq);
  INSN(blt,  bge);
  INSN(bge,  blt);
  INSN(bltu, bgeu);
  INSN(bgeu, bltu);

#undef INSN

#define INSN(NAME)                                                                \
  void MacroAssembler::NAME##z(Register Rs, const address dest) {                 \
    NAME(Rs, zr, dest);                                                           \
  }                                                                               \
  void MacroAssembler::NAME##z(Register Rs, Label &l, bool is_far) {              \
    NAME(Rs, zr, l, is_far);                                                      \
  }                                                                               \

  INSN(beq);
  INSN(bne);
  INSN(blt);
  INSN(ble);
  INSN(bge);
  INSN(bgt);

#undef INSN

#define INSN(NAME, NEG_INSN)                                                      \
  void MacroAssembler::NAME(Register Rs, Register Rt, const address dest) {       \
    NEG_INSN(Rt, Rs, dest);                                                       \
  }                                                                               \
  void MacroAssembler::NAME(Register Rs, Register Rt, Label &l, bool is_far) {    \
    NEG_INSN(Rt, Rs, l, is_far);                                                  \
  }

  INSN(bgt,  blt);
  INSN(ble,  bge);
  INSN(bgtu, bltu);
  INSN(bleu, bgeu);

#undef INSN

// cmov
void MacroAssembler::cmov_eq(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    xorr(t0, cmp1, cmp2);
    czero_eqz(dst, dst, t0);
    czero_nez(t0 , src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  bne(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_ne(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    xorr(t0, cmp1, cmp2);
    czero_nez(dst, dst, t0);
    czero_eqz(t0 , src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  beq(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_le(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    slt(t0, cmp2, cmp1);
    czero_eqz(dst, dst, t0);
    czero_nez(t0,  src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  bgt(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_leu(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    sltu(t0, cmp2, cmp1);
    czero_eqz(dst, dst, t0);
    czero_nez(t0,  src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  bgtu(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_ge(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    slt(t0, cmp1, cmp2);
    czero_eqz(dst, dst, t0);
    czero_nez(t0,  src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  blt(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_geu(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    sltu(t0, cmp1, cmp2);
    czero_eqz(dst, dst, t0);
    czero_nez(t0,  src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  bltu(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_lt(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    slt(t0, cmp1, cmp2);
    czero_nez(dst, dst, t0);
    czero_eqz(t0,  src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  bge(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_ltu(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    sltu(t0, cmp1, cmp2);
    czero_nez(dst, dst, t0);
    czero_eqz(t0,  src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  bgeu(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_gt(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    slt(t0, cmp2, cmp1);
    czero_nez(dst, dst, t0);
    czero_eqz(t0,  src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  ble(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_gtu(Register cmp1, Register cmp2, Register dst, Register src) {
  if (UseZicond) {
    sltu(t0, cmp2, cmp1);
    czero_nez(dst, dst, t0);
    czero_eqz(t0,  src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  bleu(cmp1, cmp2, no_set);
  mv(dst, src);
  bind(no_set);
}

// ----------- cmove float/double -----------

void MacroAssembler::cmov_fp_eq(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  bne(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_ne(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  beq(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_le(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  bgt(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_leu(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  bgtu(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_ge(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  blt(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_geu(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  bltu(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_lt(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  bge(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_ltu(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  bgeu(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_gt(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  ble(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_gtu(Register cmp1, Register cmp2, FloatRegister dst, FloatRegister src, bool is_single) {
  Label no_set;
  bleu(cmp1, cmp2, no_set);
  if (is_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

// ----------- cmove, compare float/double -----------
//
// For CmpF/D + CMoveI/L, ordered ones are quite straight and simple,
// so, just list behaviour of unordered ones as follow.
//
// Set dst (CMoveI (Binary cop (CmpF/D op1 op2)) (Binary dst src))
// (If one or both inputs to the compare are NaN, then)
//    1. (op1 lt op2) => true  => CMove: dst = src
//    2. (op1 le op2) => true  => CMove: dst = src
//    3. (op1 gt op2) => false => CMove: dst = dst
//    4. (op1 ge op2) => false => CMove: dst = dst
//    5. (op1 eq op2) => false => CMove: dst = dst
//    6. (op1 ne op2) => true  => CMove: dst = src

void MacroAssembler::cmov_cmp_fp_eq(FloatRegister cmp1, FloatRegister cmp2, Register dst, Register src, bool is_single) {
  if (UseZicond) {
    if (is_single) {
      feq_s(t0, cmp1, cmp2);
    } else {
      feq_d(t0, cmp1, cmp2);
    }
    czero_nez(dst, dst, t0);
    czero_eqz(t0 , src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  if (is_single) {
    // jump if cmp1 != cmp2, including the case of NaN
    // fallthrough (i.e. move src to dst) if cmp1 == cmp2
    float_bne(cmp1, cmp2, no_set);
  } else {
    double_bne(cmp1, cmp2, no_set);
  }
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_cmp_fp_ne(FloatRegister cmp1, FloatRegister cmp2, Register dst, Register src, bool is_single) {
  if (UseZicond) {
    if (is_single) {
      feq_s(t0, cmp1, cmp2);
    } else {
      feq_d(t0, cmp1, cmp2);
    }
    czero_eqz(dst, dst, t0);
    czero_nez(t0 , src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  if (is_single) {
    // jump if cmp1 == cmp2
    // fallthrough (i.e. move src to dst) if cmp1 != cmp2, including the case of NaN
    float_beq(cmp1, cmp2, no_set);
  } else {
    double_beq(cmp1, cmp2, no_set);
  }
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_cmp_fp_le(FloatRegister cmp1, FloatRegister cmp2, Register dst, Register src, bool is_single) {
  if (UseZicond) {
    if (is_single) {
      flt_s(t0, cmp2, cmp1);
    } else {
      flt_d(t0, cmp2, cmp1);
    }
    czero_eqz(dst, dst, t0);
    czero_nez(t0 , src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  if (is_single) {
    // jump if cmp1 > cmp2
    // fallthrough (i.e. move src to dst) if cmp1 <= cmp2 or either is NaN
    float_bgt(cmp1, cmp2, no_set);
  } else {
    double_bgt(cmp1, cmp2, no_set);
  }
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_cmp_fp_ge(FloatRegister cmp1, FloatRegister cmp2, Register dst, Register src, bool is_single) {
  if (UseZicond) {
    if (is_single) {
      fle_s(t0, cmp2, cmp1);
    } else {
      fle_d(t0, cmp2, cmp1);
    }
    czero_nez(dst, dst, t0);
    czero_eqz(t0 , src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  if (is_single) {
    // jump if cmp1 < cmp2 or either is NaN
    // fallthrough (i.e. move src to dst) if cmp1 >= cmp2
    float_blt(cmp1, cmp2, no_set, false, true);
  } else {
    double_blt(cmp1, cmp2, no_set, false, true);
  }
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_cmp_fp_lt(FloatRegister cmp1, FloatRegister cmp2, Register dst, Register src, bool is_single) {
  if (UseZicond) {
    if (is_single) {
      fle_s(t0, cmp2, cmp1);
    } else {
      fle_d(t0, cmp2, cmp1);
    }
    czero_eqz(dst, dst, t0);
    czero_nez(t0 , src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  if (is_single) {
    // jump if cmp1 >= cmp2
    // fallthrough (i.e. move src to dst) if cmp1 < cmp2 or either is NaN
    float_bge(cmp1, cmp2, no_set);
  } else {
    double_bge(cmp1, cmp2, no_set);
  }
  mv(dst, src);
  bind(no_set);
}

void MacroAssembler::cmov_cmp_fp_gt(FloatRegister cmp1, FloatRegister cmp2, Register dst, Register src, bool is_single) {
  if (UseZicond) {
    if (is_single) {
      flt_s(t0, cmp2, cmp1);
    } else {
      flt_d(t0, cmp2, cmp1);
    }
    czero_nez(dst, dst, t0);
    czero_eqz(t0 , src, t0);
    orr(dst, dst, t0);
    return;
  }
  Label no_set;
  if (is_single) {
    // jump if cmp1 <= cmp2 or either is NaN
    // fallthrough (i.e. move src to dst) if cmp1 > cmp2
    float_ble(cmp1, cmp2, no_set, false, true);
  } else {
    double_ble(cmp1, cmp2, no_set, false, true);
  }
  mv(dst, src);
  bind(no_set);
}

// ----------- cmove float/double, compare float/double -----------

// Move src to dst only if cmp1 == cmp2,
// otherwise leave dst unchanged, including the case where one of them is NaN.
// Clarification:
//   java code      :  cmp1 != cmp2 ? dst : src
//   transformed to :  CMove dst, (cmp1 eq cmp2), dst, src
void MacroAssembler::cmov_fp_cmp_fp_eq(FloatRegister cmp1, FloatRegister cmp2,
                                       FloatRegister dst, FloatRegister src,
                                       bool cmp_single, bool cmov_single) {
  Label no_set;
  if (cmp_single) {
    // jump if cmp1 != cmp2, including the case of NaN
    // not jump (i.e. move src to dst) if cmp1 == cmp2
    float_bne(cmp1, cmp2, no_set);
  } else {
    double_bne(cmp1, cmp2, no_set);
  }
  if (cmov_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

// Keep dst unchanged only if cmp1 == cmp2,
// otherwise move src to dst, including the case where one of them is NaN.
// Clarification:
//   java code      :  cmp1 == cmp2 ? dst : src
//   transformed to :  CMove dst, (cmp1 ne cmp2), dst, src
void MacroAssembler::cmov_fp_cmp_fp_ne(FloatRegister cmp1, FloatRegister cmp2,
                                       FloatRegister dst, FloatRegister src,
                                       bool cmp_single, bool cmov_single) {
  Label no_set;
  if (cmp_single) {
    // jump if cmp1 == cmp2
    // not jump (i.e. move src to dst) if cmp1 != cmp2, including the case of NaN
    float_beq(cmp1, cmp2, no_set);
  } else {
    double_beq(cmp1, cmp2, no_set);
  }
  if (cmov_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

// When cmp1 <= cmp2 or any of them is NaN then dst = src, otherwise, dst = dst
// Clarification
//   scenario 1:
//     java code      :  cmp2 < cmp1 ? dst : src
//     transformed to :  CMove dst, (cmp1 le cmp2), dst, src
//   scenario 2:
//     java code      :  cmp1 > cmp2 ? dst : src
//     transformed to :  CMove dst, (cmp1 le cmp2), dst, src
void MacroAssembler::cmov_fp_cmp_fp_le(FloatRegister cmp1, FloatRegister cmp2,
                                       FloatRegister dst, FloatRegister src,
                                       bool cmp_single, bool cmov_single) {
  Label no_set;
  if (cmp_single) {
    // jump if cmp1 > cmp2
    // not jump (i.e. move src to dst) if cmp1 <= cmp2 or either is NaN
    float_bgt(cmp1, cmp2, no_set);
  } else {
    double_bgt(cmp1, cmp2, no_set);
  }
  if (cmov_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_cmp_fp_ge(FloatRegister cmp1, FloatRegister cmp2,
                                       FloatRegister dst, FloatRegister src,
                                       bool cmp_single, bool cmov_single) {
  Label no_set;
  if (cmp_single) {
    // jump if cmp1 < cmp2 or either is NaN
    // not jump (i.e. move src to dst) if cmp1 >= cmp2
    float_blt(cmp1, cmp2, no_set, false, true);
  } else {
    double_blt(cmp1, cmp2, no_set, false, true);
  }
  if (cmov_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

// When cmp1 < cmp2 or any of them is NaN then dst = src, otherwise, dst = dst
// Clarification
//   scenario 1:
//     java code      :  cmp2 <= cmp1 ? dst : src
//     transformed to :  CMove dst, (cmp1 lt cmp2), dst, src
//   scenario 2:
//     java code      :  cmp1 >= cmp2 ? dst : src
//     transformed to :  CMove dst, (cmp1 lt cmp2), dst, src
void MacroAssembler::cmov_fp_cmp_fp_lt(FloatRegister cmp1, FloatRegister cmp2,
                                       FloatRegister dst, FloatRegister src,
                                       bool cmp_single, bool cmov_single) {
  Label no_set;
  if (cmp_single) {
    // jump if cmp1 >= cmp2
    // not jump (i.e. move src to dst) if cmp1 < cmp2 or either is NaN
    float_bge(cmp1, cmp2, no_set);
  } else {
    double_bge(cmp1, cmp2, no_set);
  }
  if (cmov_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

void MacroAssembler::cmov_fp_cmp_fp_gt(FloatRegister cmp1, FloatRegister cmp2,
                                       FloatRegister dst, FloatRegister src,
                                       bool cmp_single, bool cmov_single) {
  Label no_set;
  if (cmp_single) {
    // jump if cmp1 <= cmp2 or either is NaN
    // not jump (i.e. move src to dst) if cmp1 > cmp2
    float_ble(cmp1, cmp2, no_set, false, true);
  } else {
    double_ble(cmp1, cmp2, no_set, false, true);
  }
  if (cmov_single) {
    fmv_s(dst, src);
  } else {
    fmv_d(dst, src);
  }
  bind(no_set);
}

// Float compare branch instructions

#define INSN(NAME, FLOATCMP, BRANCH)                                                                                    \
  void MacroAssembler::float_##NAME(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far, bool is_unordered) {   \
    FLOATCMP##_s(t0, Rs1, Rs2);                                                                                         \
    BRANCH(t0, l, is_far);                                                                                              \
  }                                                                                                                     \
  void MacroAssembler::double_##NAME(FloatRegister Rs1, FloatRegister Rs2, Label &l, bool is_far, bool is_unordered) {  \
    FLOATCMP##_d(t0, Rs1, Rs2);                                                                                         \
    BRANCH(t0, l, is_far);                                                                                              \
  }

  INSN(beq, feq, bnez);
  INSN(bne, feq, beqz);

#undef INSN


#define INSN(NAME, FLOATCMP1, FLOATCMP2)                                              \
  void MacroAssembler::float_##NAME(FloatRegister Rs1, FloatRegister Rs2, Label &l,   \
                                    bool is_far, bool is_unordered) {                 \
    if (is_unordered) {                                                               \
      /* jump if either source is NaN or condition is expected */                     \
      FLOATCMP2##_s(t0, Rs2, Rs1);                                                    \
      beqz(t0, l, is_far);                                                            \
    } else {                                                                          \
      /* jump if no NaN in source and condition is expected */                        \
      FLOATCMP1##_s(t0, Rs1, Rs2);                                                    \
      bnez(t0, l, is_far);                                                            \
    }                                                                                 \
  }                                                                                   \
  void MacroAssembler::double_##NAME(FloatRegister Rs1, FloatRegister Rs2, Label &l,  \
                                     bool is_far, bool is_unordered) {                \
    if (is_unordered) {                                                               \
      /* jump if either source is NaN or condition is expected */                     \
      FLOATCMP2##_d(t0, Rs2, Rs1);                                                    \
      beqz(t0, l, is_far);                                                            \
    } else {                                                                          \
      /* jump if no NaN in source and condition is expected */                        \
      FLOATCMP1##_d(t0, Rs1, Rs2);                                                    \
      bnez(t0, l, is_far);                                                            \
    }                                                                                 \
  }

  INSN(ble, fle, flt);
  INSN(blt, flt, fle);

#undef INSN

#define INSN(NAME, CMP)                                                              \
  void MacroAssembler::float_##NAME(FloatRegister Rs1, FloatRegister Rs2, Label &l,  \
                                    bool is_far, bool is_unordered) {                \
    float_##CMP(Rs2, Rs1, l, is_far, is_unordered);                                  \
  }                                                                                  \
  void MacroAssembler::double_##NAME(FloatRegister Rs1, FloatRegister Rs2, Label &l, \
                                     bool is_far, bool is_unordered) {               \
    double_##CMP(Rs2, Rs1, l, is_far, is_unordered);                                 \
  }

  INSN(bgt, blt);
  INSN(bge, ble);

#undef INSN

void MacroAssembler::csrr(Register Rd, unsigned csr) {
  // These three are specified in zicntr and are unused.
  // Before adding use-cases add the appropriate hwprobe and flag.
  assert(csr != CSR_INSTRET && csr != CSR_CYCLE && csr != CSR_TIME,
         "Not intended for use without enabling zicntr.");
  csrrs(Rd, csr, x0);
}

#define INSN(NAME, OPFUN)                                      \
  void MacroAssembler::NAME(unsigned csr, Register Rs) {       \
    OPFUN(x0, csr, Rs);                                        \
  }

  INSN(csrw, csrrw);
  INSN(csrs, csrrs);
  INSN(csrc, csrrc);

#undef INSN

#define INSN(NAME, OPFUN)                                      \
  void MacroAssembler::NAME(unsigned csr, unsigned imm) {      \
    OPFUN(x0, csr, imm);                                       \
  }

  INSN(csrwi, csrrwi);
  INSN(csrsi, csrrsi);
  INSN(csrci, csrrci);

#undef INSN

#define INSN(NAME, CSR)                                      \
  void MacroAssembler::NAME(Register Rd, Register Rs) {      \
    csrrw(Rd, CSR, Rs);                                      \
  }

  INSN(fscsr,   CSR_FCSR);
  INSN(fsrm,    CSR_FRM);
  INSN(fsflags, CSR_FFLAGS);

#undef INSN

#define INSN(NAME)                              \
  void MacroAssembler::NAME(Register Rs) {      \
    NAME(x0, Rs);                               \
  }

  INSN(fscsr);
  INSN(fsrm);
  INSN(fsflags);

#undef INSN

void MacroAssembler::fsrmi(Register Rd, unsigned imm) {
  guarantee(imm < 5, "Rounding Mode is invalid in Rounding Mode register");
  csrrwi(Rd, CSR_FRM, imm);
}

void MacroAssembler::fsflagsi(Register Rd, unsigned imm) {
   csrrwi(Rd, CSR_FFLAGS, imm);
}

#define INSN(NAME)                             \
  void MacroAssembler::NAME(unsigned imm) {    \
    NAME(x0, imm);                             \
  }

  INSN(fsrmi);
  INSN(fsflagsi);

#undef INSN

void MacroAssembler::restore_cpu_control_state_after_jni(Register tmp) {
  if (RestoreMXCSROnJNICalls) {
    Label skip_fsrmi;
    frrm(tmp);
    // Set FRM to the state we need. We do want Round to Nearest.
    // We don't want non-IEEE rounding modes.
    guarantee(RoundingMode::rne == 0, "must be");
    beqz(tmp, skip_fsrmi);        // Only reset FRM if it's wrong
    fsrmi(RoundingMode::rne);
    bind(skip_fsrmi);
  }
}

void MacroAssembler::push_reg(Register Rs)
{
  subi(esp, esp, wordSize);
  sd(Rs, Address(esp, 0));
}

void MacroAssembler::pop_reg(Register Rd)
{
  ld(Rd, Address(esp, 0));
  addi(esp, esp, wordSize);
}

int MacroAssembler::bitset_to_regs(unsigned int bitset, unsigned char* regs) {
  int count = 0;
  // Scan bitset to accumulate register pairs
  for (int reg = 31; reg >= 0; reg--) {
    if ((1U << 31) & bitset) {
      regs[count++] = reg;
    }
    bitset <<= 1;
  }
  return count;
}

// Push integer registers in the bitset supplied. Don't push sp.
// Return the number of words pushed
int MacroAssembler::push_reg(unsigned int bitset, Register stack) {
  DEBUG_ONLY(int words_pushed = 0;)
  unsigned char regs[32];
  int count = bitset_to_regs(bitset, regs);
  // reserve one slot to align for odd count
  int offset = is_even(count) ? 0 : wordSize;

  if (count) {
    sub(stack, stack, count * wordSize + offset);
  }
  for (int i = count - 1; i >= 0; i--) {
    sd(as_Register(regs[i]), Address(stack, (count - 1 - i) * wordSize + offset));
    DEBUG_ONLY(words_pushed++;)
  }

  assert(words_pushed == count, "oops, pushed != count");

  return count;
}

int MacroAssembler::pop_reg(unsigned int bitset, Register stack) {
  DEBUG_ONLY(int words_popped = 0;)
  unsigned char regs[32];
  int count = bitset_to_regs(bitset, regs);
  // reserve one slot to align for odd count
  int offset = is_even(count) ? 0 : wordSize;

  for (int i = count - 1; i >= 0; i--) {
    ld(as_Register(regs[i]), Address(stack, (count - 1 - i) * wordSize + offset));
    DEBUG_ONLY(words_popped++;)
  }

  if (count) {
    add(stack, stack, count * wordSize + offset);
  }
  assert(words_popped == count, "oops, popped != count");

  return count;
}

// Push floating-point registers in the bitset supplied.
// Return the number of words pushed
int MacroAssembler::push_fp(unsigned int bitset, Register stack) {
  DEBUG_ONLY(int words_pushed = 0;)
  unsigned char regs[32];
  int count = bitset_to_regs(bitset, regs);
  int push_slots = count + (count & 1);

  if (count) {
    subi(stack, stack, push_slots * wordSize);
  }

  for (int i = count - 1; i >= 0; i--) {
    fsd(as_FloatRegister(regs[i]), Address(stack, (push_slots - 1 - i) * wordSize));
    DEBUG_ONLY(words_pushed++;)
  }

  assert(words_pushed == count, "oops, pushed(%d) != count(%d)", words_pushed, count);

  return count;
}

int MacroAssembler::pop_fp(unsigned int bitset, Register stack) {
  DEBUG_ONLY(int words_popped = 0;)
  unsigned char regs[32];
  int count = bitset_to_regs(bitset, regs);
  int pop_slots = count + (count & 1);

  for (int i = count - 1; i >= 0; i--) {
    fld(as_FloatRegister(regs[i]), Address(stack, (pop_slots - 1 - i) * wordSize));
    DEBUG_ONLY(words_popped++;)
  }

  if (count) {
    addi(stack, stack, pop_slots * wordSize);
  }

  assert(words_popped == count, "oops, popped(%d) != count(%d)", words_popped, count);

  return count;
}

/**
 * Emits code to update CRC-32 with a byte value according to constants in table
 *
 * @param [in,out]crc   Register containing the crc.
 * @param [in]val       Register containing the byte to fold into the CRC.
 * @param [in]table     Register containing the table of crc constants.
 *
 * uint32_t crc;
 * val = crc_table[(val ^ crc) & 0xFF];
 * crc = val ^ (crc >> 8);
 *
 */
void MacroAssembler::update_byte_crc32(Register crc, Register val, Register table) {
  assert_different_registers(crc, val, table);

  xorr(val, val, crc);
  zext(val, val, 8);
  shadd(val, val, table, val, 2);
  lwu(val, Address(val));
  srli(crc, crc, 8);
  xorr(crc, val, crc);
}

/**
 * Emits code to update CRC-32 with a 32-bit value according to tables 0 to 3
 *
 * @param [in,out]crc   Register containing the crc.
 * @param [in]v         Register containing the 32-bit to fold into the CRC.
 * @param [in]table0    Register containing table 0 of crc constants.
 * @param [in]table1    Register containing table 1 of crc constants.
 * @param [in]table2    Register containing table 2 of crc constants.
 * @param [in]table3    Register containing table 3 of crc constants.
 *
 * uint32_t crc;
 *   v = crc ^ v
 *   crc = table3[v&0xff]^table2[(v>>8)&0xff]^table1[(v>>16)&0xff]^table0[v>>24]
 *
 */
void MacroAssembler::update_word_crc32(Register crc, Register v, Register tmp1, Register tmp2, Register tmp3,
        Register table0, Register table1, Register table2, Register table3, bool upper) {
  assert_different_registers(crc, v, tmp1, tmp2, tmp3, table0, table1, table2, table3);

  if (upper)
    srli(v, v, 32);
  xorr(v, v, crc);

  zext(tmp1, v, 8);
  shadd(tmp1, tmp1, table3, tmp2, 2);
  lwu(crc, Address(tmp1));

  slli(tmp1, v, 16);
  slli(tmp3, v, 8);

  srliw(tmp1, tmp1, 24);
  srliw(tmp3, tmp3, 24);

  shadd(tmp1, tmp1, table2, tmp1, 2);
  lwu(tmp2, Address(tmp1));

  shadd(tmp3, tmp3, table1, tmp3, 2);
  xorr(crc, crc, tmp2);

  lwu(tmp2, Address(tmp3));
  // It is more optimal to use 'srli' instead of 'srliw' for case when it is not necessary to clean upper bits
  if (upper)
    srli(tmp1, v, 24);
  else
    srliw(tmp1, v, 24);

  // no need to clear bits other than lowest two
  shadd(tmp1, tmp1, table0, tmp1, 2);
  xorr(crc, crc, tmp2);
  lwu(tmp2, Address(tmp1));
  xorr(crc, crc, tmp2);
}


#ifdef COMPILER2
// This improvement (vectorization) is based on java.base/share/native/libzip/zlib/zcrc32.c.
// To make it, following steps are taken:
//  1. in zcrc32.c, modify N to 16 and related code,
//  2. re-generate the tables needed, we use tables of (N == 16, W == 4)
//  3. finally vectorize the code (original implementation in zcrc32.c is just scalar code).
// New tables for vector version is after table3.
void MacroAssembler::vector_update_crc32(Register crc, Register buf, Register len,
                                         Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5,
                                         Register table0, Register table3) {
    assert_different_registers(t1, crc, buf, len, tmp1, tmp2, tmp3, tmp4, tmp5, table0, table3);
    const int N = 16, W = 4;
    const int64_t single_table_size = 256;
    const Register blks = tmp2;
    const Register tmpTable = tmp3, tableN16 = tmp4;
    const VectorRegister vcrc = v4, vword = v8, vtmp = v12;
    Label VectorLoop;
    Label LastBlock;

    add(tableN16, table3, 1 * single_table_size * sizeof(juint), tmp1);
    mv(tmp5, 0xff);

    if (MaxVectorSize == 16) {
      vsetivli(zr, N, Assembler::e32, Assembler::m4, Assembler::ma, Assembler::ta);
    } else if (MaxVectorSize == 32) {
      vsetivli(zr, N, Assembler::e32, Assembler::m2, Assembler::ma, Assembler::ta);
    } else {
      assert(MaxVectorSize > 32, "sanity");
      vsetivli(zr, N, Assembler::e32, Assembler::m1, Assembler::ma, Assembler::ta);
    }

    vmv_v_x(vcrc, zr);
    vmv_s_x(vcrc, crc);

    // multiple of 64
    srli(blks, len, 6);
    slli(t1, blks, 6);
    sub(len, len, t1);
    subi(blks, blks, 1);
    blez(blks, LastBlock);

    bind(VectorLoop);
    {
      mv(tmpTable, tableN16);

      vle32_v(vword, buf);
      vxor_vv(vword, vword, vcrc);

      addi(buf, buf, N*4);

      vand_vx(vtmp, vword, tmp5);
      vsll_vi(vtmp, vtmp, 2);
      vluxei32_v(vcrc, tmpTable, vtmp);

      mv(tmp1, 1);
      for (int k = 1; k < W; k++) {
        addi(tmpTable, tmpTable, single_table_size*4);

        slli(t1, tmp1, 3);
        vsrl_vx(vtmp, vword, t1);

        vand_vx(vtmp, vtmp, tmp5);
        vsll_vi(vtmp, vtmp, 2);
        vluxei32_v(vtmp, tmpTable, vtmp);

        vxor_vv(vcrc, vcrc, vtmp);

        addi(tmp1, tmp1, 1);
      }

      subi(blks, blks, 1);
      bgtz(blks, VectorLoop);
    }

    bind(LastBlock);
    {
      vle32_v(vtmp, buf);
      vxor_vv(vcrc, vcrc, vtmp);
      mv(crc, zr);
      for (int i = 0; i < N; i++) {
        vmv_x_s(tmp2, vcrc);
        // in vmv_x_s, the value is sign-extended to SEW bits, but we need zero-extended here.
        zext(tmp2, tmp2, 32);
        vslidedown_vi(vcrc, vcrc, 1);
        xorr(crc, crc, tmp2);
        for (int j = 0; j < W; j++) {
          andr(t1, crc, tmp5);
          shadd(t1, t1, table0, tmp1, 2);
          lwu(t1, Address(t1, 0));
          srli(tmp2, crc, 8);
          xorr(crc, tmp2, t1);
        }
      }
      addi(buf, buf, N*4);
    }
}

void MacroAssembler::crc32_vclmul_fold_16_bytes_vectorsize_16(VectorRegister vx, VectorRegister vt,
                      VectorRegister vtmp1, VectorRegister vtmp2, VectorRegister vtmp3, VectorRegister vtmp4,
                      Register buf, Register tmp, const int STEP) {
  assert_different_registers(vx, vt, vtmp1, vtmp2, vtmp3, vtmp4);
  vclmul_vv(vtmp1, vx, vt);
  vclmulh_vv(vtmp2, vx, vt);
  vle64_v(vtmp4, buf); addi(buf, buf, STEP);
  // low parts
  vredxor_vs(vtmp3, vtmp1, vtmp4);
  // high parts
  vslidedown_vi(vx, vtmp4, 1);
  vredxor_vs(vtmp1, vtmp2, vx);
  // merge low and high back
  vslideup_vi(vx, vtmp1, 1);
  vmv_x_s(tmp, vtmp3);
  vmv_s_x(vx, tmp);
}

void MacroAssembler::crc32_vclmul_fold_16_bytes_vectorsize_16_2(VectorRegister vx, VectorRegister vy, VectorRegister vt,
                      VectorRegister vtmp1, VectorRegister vtmp2, VectorRegister vtmp3, VectorRegister vtmp4,
                      Register tmp) {
  assert_different_registers(vx, vy, vt, vtmp1, vtmp2, vtmp3, vtmp4);
  vclmul_vv(vtmp1, vx, vt);
  vclmulh_vv(vtmp2, vx, vt);
  // low parts
  vredxor_vs(vtmp3, vtmp1, vy);
  // high parts
  vslidedown_vi(vtmp4, vy, 1);
  vredxor_vs(vtmp1, vtmp2, vtmp4);
  // merge low and high back
  vslideup_vi(vx, vtmp1, 1);
  vmv_x_s(tmp, vtmp3);
  vmv_s_x(vx, tmp);
}

void MacroAssembler::crc32_vclmul_fold_16_bytes_vectorsize_16_3(VectorRegister vx, VectorRegister vy, VectorRegister vt,
                      VectorRegister vtmp1, VectorRegister vtmp2, VectorRegister vtmp3, VectorRegister vtmp4,
                      Register tmp) {
  assert_different_registers(vx, vy, vt, vtmp1, vtmp2, vtmp3, vtmp4);
  vclmul_vv(vtmp1, vx, vt);
  vclmulh_vv(vtmp2, vx, vt);
  // low parts
  vredxor_vs(vtmp3, vtmp1, vy);
  // high parts
  vslidedown_vi(vtmp4, vy, 1);
  vredxor_vs(vtmp1, vtmp2, vtmp4);
  // merge low and high back
  vslideup_vi(vy, vtmp1, 1);
  vmv_x_s(tmp, vtmp3);
  vmv_s_x(vy, tmp);
}

void MacroAssembler::kernel_crc32_vclmul_fold_vectorsize_16(Register crc, Register buf, Register len,
                                              Register vclmul_table, Register tmp1, Register tmp2) {
  assert_different_registers(crc, buf, len, vclmul_table, tmp1, tmp2, t1);
  assert(MaxVectorSize == 16, "sanity");

  const int TABLE_STEP = 16;
  const int STEP = 16;
  const int LOOP_STEP = 128;
  const int N = 2;

  Register loop_step = t1;

  // ======== preparation ========

  mv(loop_step, LOOP_STEP);
  sub(len, len, loop_step);

  vsetivli(zr, N, Assembler::e64, Assembler::m1, Assembler::mu, Assembler::tu);
  vle64_v(v0, buf); addi(buf, buf, STEP);
  vle64_v(v1, buf); addi(buf, buf, STEP);
  vle64_v(v2, buf); addi(buf, buf, STEP);
  vle64_v(v3, buf); addi(buf, buf, STEP);
  vle64_v(v4, buf); addi(buf, buf, STEP);
  vle64_v(v5, buf); addi(buf, buf, STEP);
  vle64_v(v6, buf); addi(buf, buf, STEP);
  vle64_v(v7, buf); addi(buf, buf, STEP);

  vmv_v_x(v31, zr);
  vsetivli(zr, 1, Assembler::e32, Assembler::m1, Assembler::mu, Assembler::tu);
  vmv_s_x(v31, crc);
  vsetivli(zr, N, Assembler::e64, Assembler::m1, Assembler::mu, Assembler::tu);
  vxor_vv(v0, v0, v31);

  // load table
  vle64_v(v31, vclmul_table);

  Label L_16_bytes_loop;
  j(L_16_bytes_loop);


  // ======== folding 128 bytes in data buffer per round ========

  align(OptoLoopAlignment);
  bind(L_16_bytes_loop);
  {
    crc32_vclmul_fold_16_bytes_vectorsize_16(v0, v31, v8, v9, v10, v11, buf, tmp2, STEP);
    crc32_vclmul_fold_16_bytes_vectorsize_16(v1, v31, v12, v13, v14, v15, buf, tmp2, STEP);
    crc32_vclmul_fold_16_bytes_vectorsize_16(v2, v31, v16, v17, v18, v19, buf, tmp2, STEP);
    crc32_vclmul_fold_16_bytes_vectorsize_16(v3, v31, v20, v21, v22, v23, buf, tmp2, STEP);
    crc32_vclmul_fold_16_bytes_vectorsize_16(v4, v31, v24, v25, v26, v27, buf, tmp2, STEP);
    crc32_vclmul_fold_16_bytes_vectorsize_16(v5, v31, v8, v9, v10, v11, buf, tmp2, STEP);
    crc32_vclmul_fold_16_bytes_vectorsize_16(v6, v31, v12, v13, v14, v15, buf, tmp2, STEP);
    crc32_vclmul_fold_16_bytes_vectorsize_16(v7, v31, v16, v17, v18, v19, buf, tmp2, STEP);
  }
  sub(len, len, loop_step);
  bge(len, loop_step, L_16_bytes_loop);


  // ======== folding into 64 bytes from 128 bytes in register ========

  // load table
  addi(vclmul_table, vclmul_table, TABLE_STEP);
  vle64_v(v31, vclmul_table);

  crc32_vclmul_fold_16_bytes_vectorsize_16_2(v0, v4, v31, v8, v9, v10, v11, tmp2);
  crc32_vclmul_fold_16_bytes_vectorsize_16_2(v1, v5, v31, v12, v13, v14, v15, tmp2);
  crc32_vclmul_fold_16_bytes_vectorsize_16_2(v2, v6, v31, v16, v17, v18, v19, tmp2);
  crc32_vclmul_fold_16_bytes_vectorsize_16_2(v3, v7, v31, v20, v21, v22, v23, tmp2);


  // ======== folding into 16 bytes from 64 bytes in register ========

  addi(vclmul_table, vclmul_table, TABLE_STEP);
  vle64_v(v31, vclmul_table);
  crc32_vclmul_fold_16_bytes_vectorsize_16_3(v0, v3, v31, v8, v9, v10, v11, tmp2);

  addi(vclmul_table, vclmul_table, TABLE_STEP);
  vle64_v(v31, vclmul_table);
  crc32_vclmul_fold_16_bytes_vectorsize_16_3(v1, v3, v31, v12, v13, v14, v15, tmp2);

  addi(vclmul_table, vclmul_table, TABLE_STEP);
  vle64_v(v31, vclmul_table);
  crc32_vclmul_fold_16_bytes_vectorsize_16_3(v2, v3, v31, v16, v17, v18, v19, tmp2);

  #undef FOLD_2_VCLMUL_3


  // ======== final: move result to scalar regsiters ========

  vmv_x_s(tmp1, v3);
  vslidedown_vi(v1, v3, 1);
  vmv_x_s(tmp2, v1);
}

void MacroAssembler::crc32_vclmul_fold_to_16_bytes_vectorsize_32(VectorRegister vx, VectorRegister vy, VectorRegister vt,
                            VectorRegister vtmp1, VectorRegister vtmp2, VectorRegister vtmp3, VectorRegister vtmp4) {
  assert_different_registers(vx, vy, vt, vtmp1, vtmp2, vtmp3, vtmp4);
  vclmul_vv(vtmp1, vx, vt);
  vclmulh_vv(vtmp2, vx, vt);
  // low parts
  vredxor_vs(vtmp3, vtmp1, vy);
  // high parts
  vslidedown_vi(vtmp4, vy, 1);
  vredxor_vs(vtmp1, vtmp2, vtmp4);
  // merge low and high back
  vslideup_vi(vy, vtmp1, 1);
  vmv_x_s(t1, vtmp3);
  vmv_s_x(vy, t1);
}

void MacroAssembler::kernel_crc32_vclmul_fold_vectorsize_32(Register crc, Register buf, Register len,
                                              Register vclmul_table, Register tmp1, Register tmp2) {
  assert_different_registers(crc, buf, len, vclmul_table, tmp1, tmp2, t1);
  assert(MaxVectorSize >= 32, "sanity");

  // utility: load table
  #define CRC32_VCLMUL_LOAD_TABLE(vt, rt, vtmp, rtmp) \
  vid_v(vtmp); \
  mv(rtmp, 2); \
  vremu_vx(vtmp, vtmp, rtmp); \
  vsll_vi(vtmp, vtmp, 3); \
  vluxei64_v(vt, rt, vtmp);

  const int TABLE_STEP = 16;
  const int STEP = 128;  // 128 bytes per round
  const int N = 2 * 8;   // 2: 128-bits/64-bits, 8: 8 pairs of double 64-bits

  Register step = tmp2;


  // ======== preparation ========

  mv(step, STEP);
  sub(len, len, step); // 2 rounds of folding with carry-less multiplication

  vsetivli(zr, N, Assembler::e64, Assembler::m4, Assembler::mu, Assembler::tu);
  // load data
  vle64_v(v4, buf);
  add(buf, buf, step);

  // load table
  CRC32_VCLMUL_LOAD_TABLE(v8, vclmul_table, v28, t1);
  // load mask,
  //    v28 should already contains: 0, 8, 0, 8, ...
  vmseq_vi(v2, v28, 0);
  //    now, v2 should contains: 101010...
  vmnand_mm(v1, v2, v2);
  //    now, v1 should contains: 010101...

  // initial crc
  vmv_v_x(v24, zr);
  vsetivli(zr, 1, Assembler::e32, Assembler::m4, Assembler::mu, Assembler::tu);
  vmv_s_x(v24, crc);
  vsetivli(zr, N, Assembler::e64, Assembler::m4, Assembler::mu, Assembler::tu);
  vxor_vv(v4, v4, v24);

  Label L_128_bytes_loop;
  j(L_128_bytes_loop);


  // ======== folding 128 bytes in data buffer per round ========

  align(OptoLoopAlignment);
  bind(L_128_bytes_loop);
  {
    // v4: data
    // v4: buf, reused
    // v8: table
    // v12: lows
    // v16: highs
    // v20: low_slides
    // v24: high_slides
    vclmul_vv(v12, v4, v8);
    vclmulh_vv(v16, v4, v8);
    vle64_v(v4, buf);
    add(buf, buf, step);
    // lows
    vslidedown_vi(v20, v12, 1);
    vmand_mm(v0, v2, v2);
    vxor_vv(v12, v12, v20, v0_t);
    // with buf data
    vxor_vv(v4, v4, v12, v0_t);

    // highs
    vslideup_vi(v24, v16, 1);
    vmand_mm(v0, v1, v1);
    vxor_vv(v16, v16, v24, v0_t);
    // with buf data
    vxor_vv(v4, v4, v16, v0_t);
  }
  sub(len, len, step);
  bge(len, step, L_128_bytes_loop);


  // ======== folding into 64 bytes from 128 bytes in register ========

  // load table
  addi(vclmul_table, vclmul_table, TABLE_STEP);
  CRC32_VCLMUL_LOAD_TABLE(v8, vclmul_table, v28, t1);

  // v4:  data, first (low) part, N/2 of 64-bits
  // v20: data, second (high) part, N/2 of 64-bits
  // v8:  table
  // v10: lows
  // v12: highs
  // v14: low_slides
  // v16: high_slides

  // high part
  vslidedown_vi(v20, v4, N/2);

  vsetivli(zr, N/2, Assembler::e64, Assembler::m2, Assembler::mu, Assembler::tu);

  vclmul_vv(v10, v4, v8);
  vclmulh_vv(v12, v4, v8);

  // lows
  vslidedown_vi(v14, v10, 1);
  vmand_mm(v0, v2, v2);
  vxor_vv(v10, v10, v14, v0_t);
  // with data part 2
  vxor_vv(v4, v20, v10, v0_t);

  // highs
  vslideup_vi(v16, v12, 1);
  vmand_mm(v0, v1, v1);
  vxor_vv(v12, v12, v16, v0_t);
  // with data part 2
  vxor_vv(v4, v20, v12, v0_t);


  // ======== folding into 16 bytes from 64 bytes in register ========

  // v4:  data, first part, 2 of 64-bits
  // v16: data, second part, 2 of 64-bits
  // v18: data, third part, 2 of 64-bits
  // v20: data, second part, 2 of 64-bits
  // v8:  table

  vslidedown_vi(v16, v4, 2);
  vslidedown_vi(v18, v4, 4);
  vslidedown_vi(v20, v4, 6);

  vsetivli(zr, 2, Assembler::e64, Assembler::m1, Assembler::mu, Assembler::tu);

  addi(vclmul_table, vclmul_table, TABLE_STEP);
  vle64_v(v8, vclmul_table);
  crc32_vclmul_fold_to_16_bytes_vectorsize_32(v4, v20, v8, v28, v29, v30, v31);

  addi(vclmul_table, vclmul_table, TABLE_STEP);
  vle64_v(v8, vclmul_table);
  crc32_vclmul_fold_to_16_bytes_vectorsize_32(v16, v20, v8, v28, v29, v30, v31);

  addi(vclmul_table, vclmul_table, TABLE_STEP);
  vle64_v(v8, vclmul_table);
  crc32_vclmul_fold_to_16_bytes_vectorsize_32(v18, v20, v8, v28, v29, v30, v31);


  // ======== final: move result to scalar regsiters ========

  vmv_x_s(tmp1, v20);
  vslidedown_vi(v4, v20, 1);
  vmv_x_s(tmp2, v4);

  #undef CRC32_VCLMUL_LOAD_TABLE
}

// For more details of the algorithm, please check the paper:
//   "Fast CRC Computation for Generic Polynomials Using PCLMULQDQ Instruction - Intel"
//
// Please also refer to the corresponding code in aarch64 or x86 ones.
//
// As the riscv carry-less multiplication is a bit different from the other platforms,
// so the implementation itself is also a bit different from others.

void MacroAssembler::kernel_crc32_vclmul_fold(Register crc, Register buf, Register len,
                        Register table0, Register table1, Register table2, Register table3,
                        Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5) {
  const int64_t single_table_size = 256;
  const int64_t table_num = 8;   // 4 for scalar, 4 for plain vector
  const ExternalAddress table_addr = StubRoutines::crc_table_addr();
  Register vclmul_table = tmp3;

  la(vclmul_table, table_addr);
  add(vclmul_table, vclmul_table, table_num * single_table_size * sizeof(juint), tmp1);
  la(table0, table_addr);

  if (MaxVectorSize == 16) {
    kernel_crc32_vclmul_fold_vectorsize_16(crc, buf, len, vclmul_table, tmp1, tmp2);
  } else {
    kernel_crc32_vclmul_fold_vectorsize_32(crc, buf, len, vclmul_table, tmp1, tmp2);
  }

  mv(crc, zr);
  update_word_crc32(crc, tmp1, tmp3, tmp4, tmp5, table0, table1, table2, table3, false);
  update_word_crc32(crc, tmp1, tmp3, tmp4, tmp5, table0, table1, table2, table3, true);
  update_word_crc32(crc, tmp2, tmp3, tmp4, tmp5, table0, table1, table2, table3, false);
  update_word_crc32(crc, tmp2, tmp3, tmp4, tmp5, table0, table1, table2, table3, true);
}

#endif // COMPILER2

/**
 * @param crc   register containing existing CRC (32-bit)
 * @param buf   register pointing to input byte buffer (byte*)
 * @param len   register containing number of bytes
 * @param table register that will contain address of CRC table
 * @param tmp   scratch registers
 */
void MacroAssembler::kernel_crc32(Register crc, Register buf, Register len,
        Register table0, Register table1, Register table2, Register table3,
        Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register tmp6) {
  assert_different_registers(crc, buf, len, table0, table1, table2, table3, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6);
  Label L_vector_entry,
        L_unroll_loop,
        L_by4_loop_entry, L_by4_loop,
        L_by1_loop, L_exit, L_skip1, L_skip2;

  const int64_t single_table_size = 256;
  const int64_t unroll = 16;
  const int64_t unroll_words = unroll*wordSize;

  // tmp5 = 0xffffffff
  notr(tmp5, zr);
  srli(tmp5, tmp5, 32);

  andn(crc, tmp5, crc);

  const ExternalAddress table_addr = StubRoutines::crc_table_addr();
  la(table0, table_addr);
  add(table1, table0, 1 * single_table_size * sizeof(juint), tmp1);
  add(table2, table0, 2 * single_table_size * sizeof(juint), tmp1);
  add(table3, table2, 1 * single_table_size * sizeof(juint), tmp1);

  // Ensure basic 4-byte alignment of input byte buffer
  mv(tmp1, 4);
  blt(len, tmp1, L_by1_loop);
  test_bit(tmp1, buf, 0);
  beqz(tmp1, L_skip1);
    subiw(len, len, 1);
    lbu(tmp1, Address(buf));
    addi(buf, buf, 1);
    update_byte_crc32(crc, tmp1, table0);
  bind(L_skip1);
    test_bit(tmp1, buf, 1);
    beqz(tmp1, L_skip2);
    subiw(len, len, 2);
    lhu(tmp1, Address(buf));
    addi(buf, buf, 2);
    zext(tmp2, tmp1, 8);
    update_byte_crc32(crc, tmp2, table0);
    srli(tmp2, tmp1, 8);
    update_byte_crc32(crc, tmp2, table0);
  bind(L_skip2);

#ifdef COMPILER2
  if (UseRVV) {
    const int64_t tmp_limit =
            UseZvbc ? 128 * 3 // 3 rounds of folding with carry-less multiplication
                    : MaxVectorSize >= 32 ? unroll_words*3 : unroll_words*5;
    mv(tmp1, tmp_limit);
    bge(len, tmp1, L_vector_entry);
  }
#endif // COMPILER2

  mv(tmp1, unroll_words);
  blt(len, tmp1, L_by4_loop_entry);

  const Register loop_buf_end = tmp3;

  align(CodeEntryAlignment);
  // Entry for L_unroll_loop
    add(loop_buf_end, buf, len); // loop_buf_end will be used as endpoint for loop below
    andi(len, len, unroll_words - 1); // len = (len % unroll_words)
    sub(loop_buf_end, loop_buf_end, len);
  bind(L_unroll_loop);
    for (int i = 0; i < unroll; i++) {
      ld(tmp1, Address(buf, i*wordSize));
      update_word_crc32(crc, tmp1, tmp2, tmp4, tmp6, table0, table1, table2, table3, false);
      update_word_crc32(crc, tmp1, tmp2, tmp4, tmp6, table0, table1, table2, table3, true);
    }

    addi(buf, buf, unroll_words);
    blt(buf, loop_buf_end, L_unroll_loop);

  bind(L_by4_loop_entry);
    mv(tmp1, 4);
    blt(len, tmp1, L_by1_loop);
    add(loop_buf_end, buf, len); // loop_buf_end will be used as endpoint for loop below
    andi(len, len, 3);
    sub(loop_buf_end, loop_buf_end, len);
  bind(L_by4_loop);
    lwu(tmp1, Address(buf));
    update_word_crc32(crc, tmp1, tmp2, tmp4, tmp6, table0, table1, table2, table3, false);
    addi(buf, buf, 4);
    blt(buf, loop_buf_end, L_by4_loop);

  bind(L_by1_loop);
    beqz(len, L_exit);

    subiw(len, len, 1);
    lbu(tmp1, Address(buf));
    update_byte_crc32(crc, tmp1, table0);
    beqz(len, L_exit);

    subiw(len, len, 1);
    lbu(tmp1, Address(buf, 1));
    update_byte_crc32(crc, tmp1, table0);
    beqz(len, L_exit);

    subiw(len, len, 1);
    lbu(tmp1, Address(buf, 2));
    update_byte_crc32(crc, tmp1, table0);

#ifdef COMPILER2
  // put vector code here, otherwise "offset is too large" error occurs.
  if (UseRVV) {
    // only need to jump exit when UseRVV == true, it's a jump from end of block `L_by1_loop`.
    j(L_exit);

    bind(L_vector_entry);
    if (UseZvbc) { // carry-less multiplication
      kernel_crc32_vclmul_fold(crc, buf, len,
                               table0, table1, table2, table3,
                               tmp1, tmp2, tmp3, tmp4, tmp6);
    } else { // plain vector instructions
      vector_update_crc32(crc, buf, len, tmp1, tmp2, tmp3, tmp4, tmp6, table0, table3);
    }

    bgtz(len, L_by4_loop_entry);
  }
#endif // COMPILER2

  bind(L_exit);
    andn(crc, tmp5, crc);
}

#ifdef COMPILER2
// Push vector registers in the bitset supplied.
// Return the number of words pushed
int MacroAssembler::push_v(unsigned int bitset, Register stack) {
  int vector_size_in_bytes = Matcher::scalable_vector_reg_size(T_BYTE);

  // Scan bitset to accumulate register pairs
  unsigned char regs[32];
  int count = bitset_to_regs(bitset, regs);

  for (int i = 0; i < count; i++) {
    sub(stack, stack, vector_size_in_bytes);
    vs1r_v(as_VectorRegister(regs[i]), stack);
  }

  return count * vector_size_in_bytes / wordSize;
}

int MacroAssembler::pop_v(unsigned int bitset, Register stack) {
  int vector_size_in_bytes = Matcher::scalable_vector_reg_size(T_BYTE);

  // Scan bitset to accumulate register pairs
  unsigned char regs[32];
  int count = bitset_to_regs(bitset, regs);

  for (int i = count - 1; i >= 0; i--) {
    vl1r_v(as_VectorRegister(regs[i]), stack);
    add(stack, stack, vector_size_in_bytes);
  }

  return count * vector_size_in_bytes / wordSize;
}
#endif // COMPILER2

void MacroAssembler::push_call_clobbered_registers_except(RegSet exclude) {
  // Push integer registers x7, x10-x17, x28-x31.
  push_reg(RegSet::of(x7) + RegSet::range(x10, x17) + RegSet::range(x28, x31) - exclude, sp);

  // Push float registers f0-f7, f10-f17, f28-f31.
  subi(sp, sp, wordSize * 20);
  int offset = 0;
  for (int i = 0; i < 32; i++) {
    if (i <= f7->encoding() || i >= f28->encoding() || (i >= f10->encoding() && i <= f17->encoding())) {
      fsd(as_FloatRegister(i), Address(sp, wordSize * (offset++)));
    }
  }
}

void MacroAssembler::pop_call_clobbered_registers_except(RegSet exclude) {
  int offset = 0;
  for (int i = 0; i < 32; i++) {
    if (i <= f7->encoding() || i >= f28->encoding() || (i >= f10->encoding() && i <= f17->encoding())) {
      fld(as_FloatRegister(i), Address(sp, wordSize * (offset++)));
    }
  }
  addi(sp, sp, wordSize * 20);

  pop_reg(RegSet::of(x7) + RegSet::range(x10, x17) + RegSet::range(x28, x31) - exclude, sp);
}

void MacroAssembler::push_CPU_state(bool save_vectors, int vector_size_in_bytes) {
  // integer registers, except zr(x0) & ra(x1) & sp(x2) & gp(x3) & tp(x4)
  push_reg(RegSet::range(x5, x31), sp);

  // float registers
  subi(sp, sp, 32 * wordSize);
  for (int i = 0; i < 32; i++) {
    fsd(as_FloatRegister(i), Address(sp, i * wordSize));
  }

  // vector registers
  if (save_vectors) {
    sub(sp, sp, vector_size_in_bytes * VectorRegister::number_of_registers);
    vsetvli(t0, x0, Assembler::e64, Assembler::m8);
    for (int i = 0; i < VectorRegister::number_of_registers; i += 8) {
      add(t0, sp, vector_size_in_bytes * i);
      vse64_v(as_VectorRegister(i), t0);
    }
  }
}

void MacroAssembler::pop_CPU_state(bool restore_vectors, int vector_size_in_bytes) {
  // vector registers
  if (restore_vectors) {
    vsetvli(t0, x0, Assembler::e64, Assembler::m8);
    for (int i = 0; i < VectorRegister::number_of_registers; i += 8) {
      vle64_v(as_VectorRegister(i), sp);
      add(sp, sp, vector_size_in_bytes * 8);
    }
  }

  // float registers
  for (int i = 0; i < 32; i++) {
    fld(as_FloatRegister(i), Address(sp, i * wordSize));
  }
  addi(sp, sp, 32 * wordSize);

  // integer registers, except zr(x0) & ra(x1) & sp(x2) & gp(x3) & tp(x4)
  pop_reg(RegSet::range(x5, x31), sp);
}

static int patch_offset_in_jal(address branch, int64_t offset) {
  assert(Assembler::is_simm21(offset) && ((offset % 2) == 0),
         "offset (%ld) is too large to be patched in one jal instruction!\n", offset);
  Assembler::patch(branch, 31, 31, (offset >> 20) & 0x1);                       // offset[20]    ==> branch[31]
  Assembler::patch(branch, 30, 21, (offset >> 1)  & 0x3ff);                     // offset[10:1]  ==> branch[30:21]
  Assembler::patch(branch, 20, 20, (offset >> 11) & 0x1);                       // offset[11]    ==> branch[20]
  Assembler::patch(branch, 19, 12, (offset >> 12) & 0xff);                      // offset[19:12] ==> branch[19:12]
  return MacroAssembler::instruction_size;                                   // only one instruction
}

static int patch_offset_in_conditional_branch(address branch, int64_t offset) {
  assert(Assembler::is_simm13(offset) && ((offset % 2) == 0),
         "offset (%ld) is too large to be patched in one beq/bge/bgeu/blt/bltu/bne instruction!\n", offset);
  Assembler::patch(branch, 31, 31, (offset >> 12) & 0x1);                       // offset[12]    ==> branch[31]
  Assembler::patch(branch, 30, 25, (offset >> 5)  & 0x3f);                      // offset[10:5]  ==> branch[30:25]
  Assembler::patch(branch, 7,  7,  (offset >> 11) & 0x1);                       // offset[11]    ==> branch[7]
  Assembler::patch(branch, 11, 8,  (offset >> 1)  & 0xf);                       // offset[4:1]   ==> branch[11:8]
  return MacroAssembler::instruction_size;                                   // only one instruction
}

static int patch_offset_in_pc_relative(address branch, int64_t offset) {
  const int PC_RELATIVE_INSTRUCTION_NUM = 2;                                    // auipc, addi/jalr/load
  Assembler::patch(branch, 31, 12, ((offset + 0x800) >> 12) & 0xfffff);         // Auipc.          offset[31:12]  ==> branch[31:12]
  Assembler::patch(branch + 4, 31, 20, offset & 0xfff);                         // Addi/Jalr/Load. offset[11:0]   ==> branch[31:20]
  return PC_RELATIVE_INSTRUCTION_NUM * MacroAssembler::instruction_size;
}

static int patch_addr_in_movptr1(address branch, address target) {
  int32_t lower = ((intptr_t)target << 35) >> 35;
  int64_t upper = ((intptr_t)target - lower) >> 29;
  Assembler::patch(branch + 0,  31, 12, upper & 0xfffff);                       // Lui.             target[48:29] + target[28] ==> branch[31:12]
  Assembler::patch(branch + 4,  31, 20, (lower >> 17) & 0xfff);                 // Addi.            target[28:17] ==> branch[31:20]
  Assembler::patch(branch + 12, 31, 20, (lower >> 6) & 0x7ff);                  // Addi.            target[16: 6] ==> branch[31:20]
  Assembler::patch(branch + 20, 31, 20, lower & 0x3f);                          // Addi/Jalr/Load.  target[ 5: 0] ==> branch[31:20]
  return MacroAssembler::movptr1_instruction_size;
}

static int patch_addr_in_movptr2(address instruction_address, address target) {
  uintptr_t addr = (uintptr_t)target;

  assert(addr < (1ull << 48), "48-bit overflow in address constant");
  unsigned int upper18 = (addr >> 30ull);
  int lower30 = (addr & 0x3fffffffu);
  int low12 = (lower30 << 20) >> 20;
  int mid18 = ((lower30 - low12) >> 12);

  Assembler::patch(instruction_address + (MacroAssembler::instruction_size * 0), 31, 12, (upper18 & 0xfffff)); // Lui
  Assembler::patch(instruction_address + (MacroAssembler::instruction_size * 1), 31, 12, (mid18   & 0xfffff)); // Lui
                                                                                                                  // Slli
                                                                                                                  // Add
  Assembler::patch(instruction_address + (MacroAssembler::instruction_size * 4), 31, 20, low12 & 0xfff);      // Addi/Jalr/Load

  assert(MacroAssembler::target_addr_for_insn(instruction_address) == target, "Must be");

  return MacroAssembler::movptr2_instruction_size;
}

static int patch_imm_in_li16u(address branch, uint16_t target) {
  Assembler::patch(branch, 31, 12, target); // patch lui only
  return MacroAssembler::instruction_size;
}

int MacroAssembler::patch_imm_in_li32(address branch, int32_t target) {
  const int LI32_INSTRUCTIONS_NUM = 2;                                          // lui + addiw
  int64_t upper = (intptr_t)target;
  int32_t lower = (((int32_t)target) << 20) >> 20;
  upper -= lower;
  upper = (int32_t)upper;
  Assembler::patch(branch + 0,  31, 12, (upper >> 12) & 0xfffff);               // Lui.
  Assembler::patch(branch + 4,  31, 20, lower & 0xfff);                         // Addiw.
  return LI32_INSTRUCTIONS_NUM * MacroAssembler::instruction_size;
}

static long get_offset_of_jal(address insn_addr) {
  assert_cond(insn_addr != nullptr);
  long offset = 0;
  unsigned insn = Assembler::ld_instr(insn_addr);
  long val = (long)Assembler::sextract(insn, 31, 12);
  offset |= ((val >> 19) & 0x1) << 20;
  offset |= (val & 0xff) << 12;
  offset |= ((val >> 8) & 0x1) << 11;
  offset |= ((val >> 9) & 0x3ff) << 1;
  offset = (offset << 43) >> 43;
  return offset;
}

static long get_offset_of_conditional_branch(address insn_addr) {
  long offset = 0;
  assert_cond(insn_addr != nullptr);
  unsigned insn = Assembler::ld_instr(insn_addr);
  offset = (long)Assembler::sextract(insn, 31, 31);
  offset = (offset << 12) | (((long)(Assembler::sextract(insn, 7, 7) & 0x1)) << 11);
  offset = offset | (((long)(Assembler::sextract(insn, 30, 25) & 0x3f)) << 5);
  offset = offset | (((long)(Assembler::sextract(insn, 11, 8) & 0xf)) << 1);
  offset = (offset << 41) >> 41;
  return offset;
}

static long get_offset_of_pc_relative(address insn_addr) {
  long offset = 0;
  assert_cond(insn_addr != nullptr);
  offset = ((long)(Assembler::sextract(Assembler::ld_instr(insn_addr), 31, 12))) << 12;                               // Auipc.
  offset += ((long)Assembler::sextract(Assembler::ld_instr(insn_addr + 4), 31, 20));                                  // Addi/Jalr/Load.
  offset = (offset << 32) >> 32;
  return offset;
}

static address get_target_of_movptr1(address insn_addr) {
  assert_cond(insn_addr != nullptr);
  intptr_t target_address = (((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr), 31, 12)) & 0xfffff) << 29; // Lui.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 4), 31, 20)) << 17;                 // Addi.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 12), 31, 20)) << 6;                 // Addi.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 20), 31, 20));                      // Addi/Jalr/Load.
  return (address) target_address;
}

static address get_target_of_movptr2(address insn_addr) {
  assert_cond(insn_addr != nullptr);
  int32_t upper18 = ((Assembler::sextract(Assembler::ld_instr(insn_addr + MacroAssembler::instruction_size * 0), 31, 12)) & 0xfffff); // Lui
  int32_t mid18   = ((Assembler::sextract(Assembler::ld_instr(insn_addr + MacroAssembler::instruction_size * 1), 31, 12)) & 0xfffff); // Lui
                                                                                                                       // 2                              // Slli
                                                                                                                       // 3                              // Add
  int32_t low12  = ((Assembler::sextract(Assembler::ld_instr(insn_addr + MacroAssembler::instruction_size * 4), 31, 20))); // Addi/Jalr/Load.
  address ret = (address)(((intptr_t)upper18<<30ll) + ((intptr_t)mid18<<12ll) + low12);
  return ret;
}

address MacroAssembler::get_target_of_li32(address insn_addr) {
  assert_cond(insn_addr != nullptr);
  intptr_t target_address = (((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr), 31, 12)) & 0xfffff) << 12; // Lui.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 4), 31, 20));                       // Addiw.
  return (address)target_address;
}

// Patch any kind of instruction; there may be several instructions.
// Return the total length (in bytes) of the instructions.
int MacroAssembler::pd_patch_instruction_size(address instruction_address, address target) {
  assert_cond(instruction_address != nullptr);
  int64_t offset = target - instruction_address;
  if (MacroAssembler::is_jal_at(instruction_address)) {                         // jal
    return patch_offset_in_jal(instruction_address, offset);
  } else if (MacroAssembler::is_branch_at(instruction_address)) {               // beq/bge/bgeu/blt/bltu/bne
    return patch_offset_in_conditional_branch(instruction_address, offset);
  } else if (MacroAssembler::is_pc_relative_at(instruction_address)) {          // auipc, addi/jalr/load
    return patch_offset_in_pc_relative(instruction_address, offset);
  } else if (MacroAssembler::is_movptr1_at(instruction_address)) {              // movptr1
    return patch_addr_in_movptr1(instruction_address, target);
  } else if (MacroAssembler::is_movptr2_at(instruction_address)) {              // movptr2
    return patch_addr_in_movptr2(instruction_address, target);
  } else if (MacroAssembler::is_li32_at(instruction_address)) {                 // li32
    int64_t imm = (intptr_t)target;
    return patch_imm_in_li32(instruction_address, (int32_t)imm);
  } else if (MacroAssembler::is_li16u_at(instruction_address)) {
    int64_t imm = (intptr_t)target;
    return patch_imm_in_li16u(instruction_address, (uint16_t)imm);
  } else {
#ifdef ASSERT
    tty->print_cr("pd_patch_instruction_size: instruction 0x%x at " INTPTR_FORMAT " could not be patched!\n",
                  Assembler::ld_instr(instruction_address), p2i(instruction_address));
    Disassembler::decode(instruction_address - 16, instruction_address + 16);
#endif
    ShouldNotReachHere();
    return -1;
  }
}

address MacroAssembler::target_addr_for_insn(address insn_addr) {
  long offset = 0;
  assert_cond(insn_addr != nullptr);
  if (MacroAssembler::is_jal_at(insn_addr)) {                     // jal
    offset = get_offset_of_jal(insn_addr);
  } else if (MacroAssembler::is_branch_at(insn_addr)) {           // beq/bge/bgeu/blt/bltu/bne
    offset = get_offset_of_conditional_branch(insn_addr);
  } else if (MacroAssembler::is_pc_relative_at(insn_addr)) {      // auipc, addi/jalr/load
    offset = get_offset_of_pc_relative(insn_addr);
  } else if (MacroAssembler::is_movptr1_at(insn_addr)) {          // movptr1
    return get_target_of_movptr1(insn_addr);
  } else if (MacroAssembler::is_movptr2_at(insn_addr)) {          // movptr2
    return get_target_of_movptr2(insn_addr);
  } else if (MacroAssembler::is_li32_at(insn_addr)) {             // li32
    return get_target_of_li32(insn_addr);
  } else {
    ShouldNotReachHere();
  }
  return address(((uintptr_t)insn_addr + offset));
}

int MacroAssembler::patch_oop(address insn_addr, address o) {
  // OOPs are either narrow (32 bits) or wide (48 bits).  We encode
  // narrow OOPs by setting the upper 16 bits in the first
  // instruction.
  if (MacroAssembler::is_li32_at(insn_addr)) {
    // Move narrow OOP
    uint32_t n = CompressedOops::narrow_oop_value(cast_to_oop(o));
    return patch_imm_in_li32(insn_addr, (int32_t)n);
  } else if (MacroAssembler::is_movptr1_at(insn_addr)) {
    // Move wide OOP
    return patch_addr_in_movptr1(insn_addr, o);
  } else if (MacroAssembler::is_movptr2_at(insn_addr)) {
    // Move wide OOP
    return patch_addr_in_movptr2(insn_addr, o);
  }
  ShouldNotReachHere();
  return -1;
}

void MacroAssembler::reinit_heapbase() {
  if (UseCompressedOops) {
    if (Universe::is_fully_initialized()) {
      mv(xheapbase, CompressedOops::base());
    } else {
      ld(xheapbase, ExternalAddress(CompressedOops::base_addr()));
    }
  }
}

void MacroAssembler::movptr(Register Rd, const Address &addr, Register temp) {
  assert(addr.getMode() == Address::literal, "must be applied to a literal address");
  relocate(addr.rspec(), [&] {
    movptr(Rd, addr.target(), temp);
  });
}

void MacroAssembler::movptr(Register Rd, address addr, Register temp) {
  int offset = 0;
  movptr(Rd, addr, offset, temp);
  addi(Rd, Rd, offset);
}

void MacroAssembler::movptr(Register Rd, address addr, int32_t &offset, Register temp) {
  uint64_t uimm64 = (uint64_t)addr;
#ifndef PRODUCT
  {
    char buffer[64];
    os::snprintf_checked(buffer, sizeof(buffer), "0x%" PRIx64, uimm64);
    block_comment(buffer);
  }
#endif
  assert(uimm64 < (1ull << 48), "48-bit overflow in address constant");

  if (temp == noreg) {
    movptr1(Rd, uimm64, offset);
  } else {
    movptr2(Rd, uimm64, offset, temp);
  }
}

void MacroAssembler::movptr1(Register Rd, uint64_t imm64, int32_t &offset) {
  // Load upper 31 bits
  //
  // In case of 11th bit of `lower` is 0, it's straightforward to understand.
  // In case of 11th bit of `lower` is 1, it's a bit tricky, to help understand,
  // imagine divide both `upper` and `lower` into 2 parts respectively, i.e.
  // [upper_20, upper_12], [lower_20, lower_12], they are the same just before
  // `lower = (lower << 52) >> 52;`.
  // After `upper -= lower;`,
  //    upper_20' = upper_20 - (-1) == upper_20 + 1
  //    upper_12 = 0x000
  // After `lui(Rd, upper);`, `Rd` = upper_20' << 12
  // Also divide `Rd` into 2 parts [Rd_20, Rd_12],
  //    Rd_20 == upper_20'
  //    Rd_12 == 0x000
  // After `addi(Rd, Rd, lower);`,
  //    Rd_20 = upper_20' + (-1) == upper_20 + 1 - 1 = upper_20
  //    Rd_12 = lower_12
  // So, finally Rd == [upper_20, lower_12]
  int64_t imm = imm64 >> 17;
  int64_t upper = imm, lower = imm;
  lower = (lower << 52) >> 52;
  upper -= lower;
  upper = (int32_t)upper;
  lui(Rd, upper);
  addi(Rd, Rd, lower);

  // Load the rest 17 bits.
  slli(Rd, Rd, 11);
  addi(Rd, Rd, (imm64 >> 6) & 0x7ff);
  slli(Rd, Rd, 6);

  // This offset will be used by following jalr/ld.
  offset = imm64 & 0x3f;
}

void MacroAssembler::movptr2(Register Rd, uint64_t addr, int32_t &offset, Register tmp) {
  assert_different_registers(Rd, tmp, noreg);

  // addr: [upper18, lower30[mid18, lower12]]

  int64_t upper18 = addr >> 18;
  lui(tmp, upper18);

  int64_t lower30 = addr & 0x3fffffff;
  int64_t mid18 = lower30, lower12 = lower30;
  lower12 = (lower12 << 52) >> 52;
  // For this tricky part (`mid18 -= lower12;` + `offset = lower12;`),
  // please refer to movptr1 above.
  mid18 -= (int32_t)lower12;
  lui(Rd, mid18);

  slli(tmp, tmp, 18);
  add(Rd, Rd, tmp);

  offset = lower12;
}

// floating point imm move
bool MacroAssembler::can_hf_imm_load(short imm) {
  jshort h_bits = (jshort)imm;
  if (h_bits == 0) {
    return true;
  }
  return can_zfa_zli_half_float(imm);
}

bool MacroAssembler::can_fp_imm_load(float imm) {
  jint f_bits = jint_cast(imm);
  if (f_bits == 0) {
    return true;
  }
  return can_zfa_zli_float(imm);
}

bool MacroAssembler::can_dp_imm_load(double imm) {
  julong d_bits = julong_cast(imm);
  if (d_bits == 0) {
    return true;
  }
  return can_zfa_zli_double(imm);
}

void MacroAssembler::fli_h(FloatRegister Rd, short imm) {
  jshort h_bits = (jshort)imm;
  if (h_bits == 0) {
    fmv_h_x(Rd, zr);
    return;
  }
  int Rs = zfa_zli_lookup_half_float(h_bits);
  assert(Rs != -1, "Must be");
  _fli_h(Rd, Rs);
}

void MacroAssembler::fli_s(FloatRegister Rd, float imm) {
  jint f_bits = jint_cast(imm);
  if (f_bits == 0) {
    fmv_w_x(Rd, zr);
    return;
  }
  int Rs = zfa_zli_lookup_float(f_bits);
  assert(Rs != -1, "Must be");
  _fli_s(Rd, Rs);
}

void MacroAssembler::fli_d(FloatRegister Rd, double imm) {
  uint64_t d_bits = (uint64_t)julong_cast(imm);
  if (d_bits == 0) {
    fmv_d_x(Rd, zr);
    return;
  }
  int Rs = zfa_zli_lookup_double(d_bits);
  assert(Rs != -1, "Must be");
  _fli_d(Rd, Rs);
}

void MacroAssembler::add(Register Rd, Register Rn, int64_t increment, Register tmp) {
  if (is_simm12(increment)) {
    addi(Rd, Rn, increment);
  } else {
    assert_different_registers(Rn, tmp);
    mv(tmp, increment);
    add(Rd, Rn, tmp);
  }
}

void MacroAssembler::sub(Register Rd, Register Rn, int64_t decrement, Register tmp) {
  add(Rd, Rn, -decrement, tmp);
}

void MacroAssembler::addw(Register Rd, Register Rn, int64_t increment, Register tmp) {
  if (is_simm12(increment)) {
    addiw(Rd, Rn, increment);
  } else {
    assert_different_registers(Rn, tmp);
    mv(tmp, increment);
    addw(Rd, Rn, tmp);
  }
}

void MacroAssembler::subw(Register Rd, Register Rn, int64_t decrement, Register tmp) {
  addw(Rd, Rn, -decrement, tmp);
}

void MacroAssembler::andrw(Register Rd, Register Rs1, Register Rs2) {
  andr(Rd, Rs1, Rs2);
  sext(Rd, Rd, 32);
}

void MacroAssembler::orrw(Register Rd, Register Rs1, Register Rs2) {
  orr(Rd, Rs1, Rs2);
  sext(Rd, Rd, 32);
}

void MacroAssembler::xorrw(Register Rd, Register Rs1, Register Rs2) {
  xorr(Rd, Rs1, Rs2);
  sext(Rd, Rd, 32);
}

// Rd = Rs1 & (~Rd2)
void MacroAssembler::andn(Register Rd, Register Rs1, Register Rs2) {
  if (UseZbb) {
    Assembler::andn(Rd, Rs1, Rs2);
    return;
  }

  notr(Rd, Rs2);
  andr(Rd, Rs1, Rd);
}

// Rd = Rs1 | (~Rd2)
void MacroAssembler::orn(Register Rd, Register Rs1, Register Rs2) {
  if (UseZbb) {
    Assembler::orn(Rd, Rs1, Rs2);
    return;
  }

  notr(Rd, Rs2);
  orr(Rd, Rs1, Rd);
}

// Note: load_unsigned_short used to be called load_unsigned_word.
int MacroAssembler::load_unsigned_short(Register dst, Address src) {
  int off = offset();
  lhu(dst, src);
  return off;
}

int MacroAssembler::load_unsigned_byte(Register dst, Address src) {
  int off = offset();
  lbu(dst, src);
  return off;
}

int MacroAssembler::load_signed_short(Register dst, Address src) {
  int off = offset();
  lh(dst, src);
  return off;
}

int MacroAssembler::load_signed_byte(Register dst, Address src) {
  int off = offset();
  lb(dst, src);
  return off;
}

void MacroAssembler::load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed) {
  switch (size_in_bytes) {
    case  8:  ld(dst, src); break;
    case  4:  is_signed ? lw(dst, src) : lwu(dst, src); break;
    case  2:  is_signed ? load_signed_short(dst, src) : load_unsigned_short(dst, src); break;
    case  1:  is_signed ? load_signed_byte( dst, src) : load_unsigned_byte( dst, src); break;
    default:  ShouldNotReachHere();
  }
}

void MacroAssembler::store_sized_value(Address dst, Register src, size_t size_in_bytes) {
  switch (size_in_bytes) {
    case  8:  sd(src, dst); break;
    case  4:  sw(src, dst); break;
    case  2:  sh(src, dst); break;
    case  1:  sb(src, dst); break;
    default:  ShouldNotReachHere();
  }
}

// granularity is 1 OR 2 bytes per load. dst and src.base() allowed to be the same register
void MacroAssembler::load_short_misaligned(Register dst, Address src, Register tmp, bool is_signed, int granularity) {
  if (granularity != 1 && granularity != 2) {
    ShouldNotReachHere();
  }
  if (AvoidUnalignedAccesses && (granularity != 2)) {
    assert_different_registers(dst, tmp);
    assert_different_registers(tmp, src.base());
    is_signed ? lb(tmp, Address(src.base(), src.offset() + 1)) : lbu(tmp, Address(src.base(), src.offset() + 1));
    slli(tmp, tmp, 8);
    lbu(dst, src);
    add(dst, dst, tmp);
  } else {
    is_signed ? lh(dst, src) : lhu(dst, src);
  }
}

// granularity is 1, 2 OR 4 bytes per load, if granularity 2 or 4 then dst and src.base() allowed to be the same register
void MacroAssembler::load_int_misaligned(Register dst, Address src, Register tmp, bool is_signed, int granularity) {
  if (AvoidUnalignedAccesses && (granularity != 4)) {
    switch(granularity) {
      case 1:
        assert_different_registers(dst, tmp, src.base());
        lbu(dst, src);
        lbu(tmp, Address(src.base(), src.offset() + 1));
        slli(tmp, tmp, 8);
        add(dst, dst, tmp);
        lbu(tmp, Address(src.base(), src.offset() + 2));
        slli(tmp, tmp, 16);
        add(dst, dst, tmp);
        is_signed ? lb(tmp, Address(src.base(), src.offset() + 3)) : lbu(tmp, Address(src.base(), src.offset() + 3));
        slli(tmp, tmp, 24);
        add(dst, dst, tmp);
        break;
      case 2:
        assert_different_registers(dst, tmp);
        assert_different_registers(tmp, src.base());
        is_signed ? lh(tmp, Address(src.base(), src.offset() + 2)) : lhu(tmp, Address(src.base(), src.offset() + 2));
        slli(tmp, tmp, 16);
        lhu(dst, src);
        add(dst, dst, tmp);
        break;
      default:
        ShouldNotReachHere();
    }
  } else {
    is_signed ? lw(dst, src) : lwu(dst, src);
  }
}

// granularity is 1, 2, 4 or 8 bytes per load, if granularity 4 or 8 then dst and src.base() allowed to be same register
void MacroAssembler::load_long_misaligned(Register dst, Address src, Register tmp, int granularity) {
  if (AvoidUnalignedAccesses && (granularity != 8)) {
    switch(granularity){
      case 1:
        assert_different_registers(dst, tmp, src.base());
        lbu(dst, src);
        lbu(tmp, Address(src.base(), src.offset() + 1));
        slli(tmp, tmp, 8);
        add(dst, dst, tmp);
        lbu(tmp, Address(src.base(), src.offset() + 2));
        slli(tmp, tmp, 16);
        add(dst, dst, tmp);
        lbu(tmp, Address(src.base(), src.offset() + 3));
        slli(tmp, tmp, 24);
        add(dst, dst, tmp);
        lbu(tmp, Address(src.base(), src.offset() + 4));
        slli(tmp, tmp, 32);
        add(dst, dst, tmp);
        lbu(tmp, Address(src.base(), src.offset() + 5));
        slli(tmp, tmp, 40);
        add(dst, dst, tmp);
        lbu(tmp, Address(src.base(), src.offset() + 6));
        slli(tmp, tmp, 48);
        add(dst, dst, tmp);
        lbu(tmp, Address(src.base(), src.offset() + 7));
        slli(tmp, tmp, 56);
        add(dst, dst, tmp);
        break;
      case 2:
        assert_different_registers(dst, tmp, src.base());
        lhu(dst, src);
        lhu(tmp, Address(src.base(), src.offset() + 2));
        slli(tmp, tmp, 16);
        add(dst, dst, tmp);
        lhu(tmp, Address(src.base(), src.offset() + 4));
        slli(tmp, tmp, 32);
        add(dst, dst, tmp);
        lhu(tmp, Address(src.base(), src.offset() + 6));
        slli(tmp, tmp, 48);
        add(dst, dst, tmp);
        break;
      case 4:
        assert_different_registers(dst, tmp);
        assert_different_registers(tmp, src.base());
        lwu(tmp, Address(src.base(), src.offset() + 4));
        slli(tmp, tmp, 32);
        lwu(dst, src);
        add(dst, dst, tmp);
        break;
      default:
        ShouldNotReachHere();
    }
  } else {
    ld(dst, src);
  }
}

// reverse bytes in lower word, sign-extend
// Rd[32:0] = Rs[7:0] Rs[15:8] Rs[23:16] Rs[31:24]
void MacroAssembler::revbw(Register Rd, Register Rs, Register tmp1, Register tmp2) {
  if (UseZbb) {
    rev8(Rd, Rs);
    srai(Rd, Rd, 32);
    return;
  }
  assert_different_registers(Rs, tmp1, tmp2);
  assert_different_registers(Rd, tmp1, tmp2);
  zext(tmp1, Rs, 8);
  slli(tmp1, tmp1, 8);
  for (int step = 8; step < 24; step += 8) {
    srli(tmp2, Rs, step);
    zext(tmp2, tmp2, 8);
    orr(tmp1, tmp1, tmp2);
    slli(tmp1, tmp1, 8);
  }
  srli(Rd, Rs, 24);
  zext(Rd, Rd, 8);
  orr(Rd, tmp1, Rd);
  sext(Rd, Rd, 32);
}

// reverse bytes in doubleword
// Rd[63:0] = Rs[7:0] Rs[15:8] Rs[23:16] Rs[31:24] Rs[39:32] Rs[47,40] Rs[55,48] Rs[63:56]
void MacroAssembler::revb(Register Rd, Register Rs, Register tmp1, Register tmp2) {
  if (UseZbb) {
    rev8(Rd, Rs);
    return;
  }
  assert_different_registers(Rs, tmp1, tmp2);
  assert_different_registers(Rd, tmp1, tmp2);
  zext(tmp1, Rs, 8);
  slli(tmp1, tmp1, 8);
  for (int step = 8; step < 56; step += 8) {
    srli(tmp2, Rs, step);
    zext(tmp2, tmp2, 8);
    orr(tmp1, tmp1, tmp2);
    slli(tmp1, tmp1, 8);
  }
  srli(Rd, Rs, 56);
  orr(Rd, tmp1, Rd);
}

// rotate right with shift bits
void MacroAssembler::ror(Register dst, Register src, Register shift, Register tmp)
{
  if (UseZbb) {
    rorr(dst, src, shift);
    return;
  }

  assert_different_registers(dst, tmp);
  assert_different_registers(src, tmp);

  mv(tmp, 64);
  sub(tmp, tmp, shift);
  sll(tmp, src, tmp);
  srl(dst, src, shift);
  orr(dst, dst, tmp);
}

// rotate right with shift bits
void MacroAssembler::ror(Register dst, Register src, uint32_t shift, Register tmp)
{
  if (UseZbb) {
    rori(dst, src, shift);
    return;
  }

  assert_different_registers(dst, tmp);
  assert_different_registers(src, tmp);
  assert(shift < 64, "shift amount must be < 64");
  slli(tmp, src, 64 - shift);
  srli(dst, src, shift);
  orr(dst, dst, tmp);
}

// rotate left with shift bits, 32-bit version
void MacroAssembler::rolw(Register dst, Register src, uint32_t shift, Register tmp) {
  if (UseZbb) {
    // no roliw available
    roriw(dst, src, 32 - shift);
    return;
  }

  assert_different_registers(dst, tmp);
  assert_different_registers(src, tmp);
  assert(shift < 32, "shift amount must be < 32");
  srliw(tmp, src, 32 - shift);
  slliw(dst, src, shift);
  orr(dst, dst, tmp);
}

void MacroAssembler::orptr(Address adr, RegisterOrConstant src, Register tmp1, Register tmp2) {
  ld(tmp1, adr);
  if (src.is_register()) {
    orr(tmp1, tmp1, src.as_register());
  } else {
    if (is_simm12(src.as_constant())) {
      ori(tmp1, tmp1, src.as_constant());
    } else {
      assert_different_registers(tmp1, tmp2);
      mv(tmp2, src.as_constant());
      orr(tmp1, tmp1, tmp2);
    }
  }
  sd(tmp1, adr);
}

void MacroAssembler::cmp_klass_compressed(Register oop, Register trial_klass, Register tmp, Label &L, bool equal) {
  if (UseCompactObjectHeaders) {
    load_narrow_klass_compact(tmp, oop);
  } else if (UseCompressedClassPointers) {
    lwu(tmp, Address(oop, oopDesc::klass_offset_in_bytes()));
  } else {
    ld(tmp, Address(oop, oopDesc::klass_offset_in_bytes()));
  }
  if (equal) {
    beq(trial_klass, tmp, L);
  } else {
    bne(trial_klass, tmp, L);
  }
}

// Move an oop into a register.
void MacroAssembler::movoop(Register dst, jobject obj) {
  int oop_index;
  if (obj == nullptr) {
    oop_index = oop_recorder()->allocate_oop_index(obj);
  } else {
#ifdef ASSERT
    {
      ThreadInVMfromUnknown tiv;
      assert(Universe::heap()->is_in(JNIHandles::resolve(obj)), "should be real oop");
    }
#endif
    oop_index = oop_recorder()->find_index(obj);
  }
  RelocationHolder rspec = oop_Relocation::spec(oop_index);

  if (BarrierSet::barrier_set()->barrier_set_assembler()->supports_instruction_patching()) {
    movptr(dst, Address((address)obj, rspec));
  } else {
    address dummy = address(uintptr_t(pc()) & -wordSize); // A nearby aligned address
    ld(dst, Address(dummy, rspec));
  }
}

// Move a metadata address into a register.
void MacroAssembler::mov_metadata(Register dst, Metadata* obj) {
  assert((uintptr_t)obj < (1ull << 48), "48-bit overflow in metadata");
  int oop_index;
  if (obj == nullptr) {
    oop_index = oop_recorder()->allocate_metadata_index(obj);
  } else {
    oop_index = oop_recorder()->find_index(obj);
  }
  RelocationHolder rspec = metadata_Relocation::spec(oop_index);
  movptr(dst, Address((address)obj, rspec));
}

// Writes to stack successive pages until offset reached to check for
// stack overflow + shadow pages.  This clobbers tmp.
void MacroAssembler::bang_stack_size(Register size, Register tmp) {
  assert_different_registers(tmp, size, t0);
  // Bang stack for total size given plus shadow page size.
  // Bang one page at a time because large size can bang beyond yellow and
  // red zones.
  mv(t0, (int)os::vm_page_size());
  Label loop;
  bind(loop);
  sub(tmp, sp, t0);
  subw(size, size, t0);
  sd(size, Address(tmp));
  bgtz(size, loop);

  // Bang down shadow pages too.
  // At this point, (tmp-0) is the last address touched, so don't
  // touch it again.  (It was touched as (tmp-pagesize) but then tmp
  // was post-decremented.)  Skip this address by starting at i=1, and
  // touch a few more pages below.  N.B.  It is important to touch all
  // the way down to and including i=StackShadowPages.
  for (int i = 0; i < (int)(StackOverflow::stack_shadow_zone_size() / (int)os::vm_page_size()) - 1; i++) {
    // this could be any sized move but this is can be a debugging crumb
    // so the bigger the better.
    sub(tmp, tmp, (int)os::vm_page_size());
    sd(size, Address(tmp, 0));
  }
}

void MacroAssembler::load_mirror(Register dst, Register method, Register tmp1, Register tmp2) {
  const int mirror_offset = in_bytes(Klass::java_mirror_offset());
  ld(dst, Address(xmethod, Method::const_offset()));
  ld(dst, Address(dst, ConstMethod::constants_offset()));
  ld(dst, Address(dst, ConstantPool::pool_holder_offset()));
  ld(dst, Address(dst, mirror_offset));
  resolve_oop_handle(dst, tmp1, tmp2);
}

void MacroAssembler::resolve_oop_handle(Register result, Register tmp1, Register tmp2) {
  // OopHandle::resolve is an indirection.
  assert_different_registers(result, tmp1, tmp2);
  access_load_at(T_OBJECT, IN_NATIVE, result, Address(result, 0), tmp1, tmp2);
}

// ((WeakHandle)result).resolve()
void MacroAssembler::resolve_weak_handle(Register result, Register tmp1, Register tmp2) {
  assert_different_registers(result, tmp1, tmp2);
  Label resolved;

  // A null weak handle resolves to null.
  beqz(result, resolved);

  // Only 64 bit platforms support GCs that require a tmp register
  // Only IN_HEAP loads require a thread_tmp register
  // WeakHandle::resolve is an indirection like jweak.
  access_load_at(T_OBJECT, IN_NATIVE | ON_PHANTOM_OOP_REF,
                 result, Address(result), tmp1, tmp2);
  bind(resolved);
}

void MacroAssembler::access_load_at(BasicType type, DecoratorSet decorators,
                                    Register dst, Address src,
                                    Register tmp1, Register tmp2) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  decorators = AccessInternal::decorator_fixup(decorators, type);
  bool as_raw = (decorators & AS_RAW) != 0;
  if (as_raw) {
    bs->BarrierSetAssembler::load_at(this, decorators, type, dst, src, tmp1, tmp2);
  } else {
    bs->load_at(this, decorators, type, dst, src, tmp1, tmp2);
  }
}

void MacroAssembler::null_check(Register reg, int offset) {
  if (needs_explicit_null_check(offset)) {
    // provoke OS null exception if reg is null by
    // accessing M[reg] w/o changing any registers
    // NOTE: this is plenty to provoke a segv
    ld(zr, Address(reg, 0));
  } else {
    // nothing to do, (later) access of M[reg + offset]
    // will provoke OS null exception if reg is null
  }
}

void MacroAssembler::access_store_at(BasicType type, DecoratorSet decorators,
                                     Address dst, Register val,
                                     Register tmp1, Register tmp2, Register tmp3) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  decorators = AccessInternal::decorator_fixup(decorators, type);
  bool as_raw = (decorators & AS_RAW) != 0;
  if (as_raw) {
    bs->BarrierSetAssembler::store_at(this, decorators, type, dst, val, tmp1, tmp2, tmp3);
  } else {
    bs->store_at(this, decorators, type, dst, val, tmp1, tmp2, tmp3);
  }
}

// Algorithm must match CompressedOops::encode.
void MacroAssembler::encode_heap_oop(Register d, Register s) {
  verify_oop_msg(s, "broken oop in encode_heap_oop");
  if (CompressedOops::base() == nullptr) {
    if (CompressedOops::shift() != 0) {
      assert (LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
      srli(d, s, LogMinObjAlignmentInBytes);
    } else {
      mv(d, s);
    }
  } else {
    Label notNull;
    sub(d, s, xheapbase);
    bgez(d, notNull);
    mv(d, zr);
    bind(notNull);
    if (CompressedOops::shift() != 0) {
      assert (LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
      srli(d, d, CompressedOops::shift());
    }
  }
}

void MacroAssembler::encode_heap_oop_not_null(Register r) {
#ifdef ASSERT
  if (CheckCompressedOops) {
    Label ok;
    bnez(r, ok);
    stop("null oop passed to encode_heap_oop_not_null");
    bind(ok);
  }
#endif
  verify_oop_msg(r, "broken oop in encode_heap_oop_not_null");
  if (CompressedOops::base() != nullptr) {
    sub(r, r, xheapbase);
  }
  if (CompressedOops::shift() != 0) {
    assert(LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
    srli(r, r, LogMinObjAlignmentInBytes);
  }
}

void MacroAssembler::encode_heap_oop_not_null(Register dst, Register src) {
#ifdef ASSERT
  if (CheckCompressedOops) {
    Label ok;
    bnez(src, ok);
    stop("null oop passed to encode_heap_oop_not_null2");
    bind(ok);
  }
#endif
  verify_oop_msg(src, "broken oop in encode_heap_oop_not_null2");

  Register data = src;
  if (CompressedOops::base() != nullptr) {
    sub(dst, src, xheapbase);
    data = dst;
  }
  if (CompressedOops::shift() != 0) {
    assert(LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
    srli(dst, data, LogMinObjAlignmentInBytes);
    data = dst;
  }
  if (data == src) {
    mv(dst, src);
  }
}

void MacroAssembler::load_narrow_klass_compact(Register dst, Register src) {
  assert(UseCompactObjectHeaders, "expects UseCompactObjectHeaders");
  ld(dst, Address(src, oopDesc::mark_offset_in_bytes()));
  srli(dst, dst, markWord::klass_shift);
}

void MacroAssembler::load_klass(Register dst, Register src, Register tmp) {
  assert_different_registers(dst, tmp);
  assert_different_registers(src, tmp);
  if (UseCompactObjectHeaders) {
    load_narrow_klass_compact(dst, src);
    decode_klass_not_null(dst, tmp);
  } else if (UseCompressedClassPointers) {
    lwu(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    decode_klass_not_null(dst, tmp);
  } else {
    ld(dst, Address(src, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::store_klass(Register dst, Register src, Register tmp) {
  // FIXME: Should this be a store release? concurrent gcs assumes
  // klass length is valid if klass field is not null.
  assert(!UseCompactObjectHeaders, "not with compact headers");
  if (UseCompressedClassPointers) {
    encode_klass_not_null(src, tmp);
    sw(src, Address(dst, oopDesc::klass_offset_in_bytes()));
  } else {
    sd(src, Address(dst, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::store_klass_gap(Register dst, Register src) {
  assert(!UseCompactObjectHeaders, "not with compact headers");
  if (UseCompressedClassPointers) {
    // Store to klass gap in destination
    sw(src, Address(dst, oopDesc::klass_gap_offset_in_bytes()));
  }
}

void MacroAssembler::decode_klass_not_null(Register r, Register tmp) {
  assert_different_registers(r, tmp);
  decode_klass_not_null(r, r, tmp);
}

void MacroAssembler::decode_klass_not_null(Register dst, Register src, Register tmp) {
  assert(UseCompressedClassPointers, "should only be used for compressed headers");
  assert_different_registers(dst, tmp);
  assert_different_registers(src, tmp);

  if (CompressedKlassPointers::base() == nullptr) {
    if (CompressedKlassPointers::shift() != 0) {
      slli(dst, src, CompressedKlassPointers::shift());
    } else {
      mv(dst, src);
    }
    return;
  }

  Register xbase = tmp;

  mv(xbase, (uintptr_t)CompressedKlassPointers::base());

  if (CompressedKlassPointers::shift() != 0) {
    // dst = (src << shift) + xbase
    shadd(dst, src, xbase, dst /* temporary, dst != xbase */, CompressedKlassPointers::shift());
  } else {
    add(dst, xbase, src);
  }
}

void MacroAssembler::encode_klass_not_null(Register r, Register tmp) {
  assert_different_registers(r, tmp);
  encode_klass_not_null(r, r, tmp);
}

void MacroAssembler::encode_klass_not_null(Register dst, Register src, Register tmp) {
  assert(UseCompressedClassPointers, "should only be used for compressed headers");

  if (CompressedKlassPointers::base() == nullptr) {
    if (CompressedKlassPointers::shift() != 0) {
      srli(dst, src, CompressedKlassPointers::shift());
    } else {
      mv(dst, src);
    }
    return;
  }

  if (((uint64_t)CompressedKlassPointers::base() & 0xffffffff) == 0 &&
      CompressedKlassPointers::shift() == 0) {
    zext(dst, src, 32);
    return;
  }

  Register xbase = dst;
  if (dst == src) {
    xbase = tmp;
  }

  assert_different_registers(src, xbase);
  mv(xbase, (uintptr_t)CompressedKlassPointers::base());
  sub(dst, src, xbase);
  if (CompressedKlassPointers::shift() != 0) {
    srli(dst, dst, CompressedKlassPointers::shift());
  }
}

void MacroAssembler::decode_heap_oop_not_null(Register r) {
  decode_heap_oop_not_null(r, r);
}

void MacroAssembler::decode_heap_oop_not_null(Register dst, Register src) {
  assert(UseCompressedOops, "should only be used for compressed headers");
  assert(Universe::heap() != nullptr, "java heap should be initialized");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  if (CompressedOops::shift() != 0) {
    assert(LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
    slli(dst, src, LogMinObjAlignmentInBytes);
    if (CompressedOops::base() != nullptr) {
      add(dst, xheapbase, dst);
    }
  } else {
    assert(CompressedOops::base() == nullptr, "sanity");
    mv(dst, src);
  }
}

void  MacroAssembler::decode_heap_oop(Register d, Register s) {
  if (CompressedOops::base() == nullptr) {
    if (CompressedOops::shift() != 0 || d != s) {
      slli(d, s, CompressedOops::shift());
    }
  } else {
    Label done;
    mv(d, s);
    beqz(s, done);
    shadd(d, s, xheapbase, d, LogMinObjAlignmentInBytes);
    bind(done);
  }
  verify_oop_msg(d, "broken oop in decode_heap_oop");
}

void MacroAssembler::store_heap_oop(Address dst, Register val, Register tmp1,
                                    Register tmp2, Register tmp3, DecoratorSet decorators) {
  access_store_at(T_OBJECT, IN_HEAP | decorators, dst, val, tmp1, tmp2, tmp3);
}

void MacroAssembler::load_heap_oop(Register dst, Address src, Register tmp1,
                                   Register tmp2, DecoratorSet decorators) {
  access_load_at(T_OBJECT, IN_HEAP | decorators, dst, src, tmp1, tmp2);
}

void MacroAssembler::load_heap_oop_not_null(Register dst, Address src, Register tmp1,
                                            Register tmp2, DecoratorSet decorators) {
  access_load_at(T_OBJECT, IN_HEAP | IS_NOT_NULL, dst, src, tmp1, tmp2);
}

// Used for storing nulls.
void MacroAssembler::store_heap_oop_null(Address dst) {
  access_store_at(T_OBJECT, IN_HEAP, dst, noreg, noreg, noreg, noreg);
}

// Look up the method for a megamorphic invokeinterface call.
// The target method is determined by <intf_klass, itable_index>.
// The receiver klass is in recv_klass.
// On success, the result will be in method_result, and execution falls through.
// On failure, execution transfers to the given label.
void MacroAssembler::lookup_interface_method(Register recv_klass,
                                             Register intf_klass,
                                             RegisterOrConstant itable_index,
                                             Register method_result,
                                             Register scan_tmp,
                                             Label& L_no_such_interface,
                                             bool return_method) {
  assert_different_registers(recv_klass, intf_klass, scan_tmp);
  assert_different_registers(method_result, intf_klass, scan_tmp);
  assert(recv_klass != method_result || !return_method,
         "recv_klass can be destroyed when method isn't needed");
  assert(itable_index.is_constant() || itable_index.as_register() == method_result,
         "caller must use same register for non-constant itable index as for method");

  // Compute start of first itableOffsetEntry (which is at the end of the vtable).
  int vtable_base = in_bytes(Klass::vtable_start_offset());
  int itentry_off = in_bytes(itableMethodEntry::method_offset());
  int scan_step   = itableOffsetEntry::size() * wordSize;
  int vte_size    = vtableEntry::size_in_bytes();
  assert(vte_size == wordSize, "else adjust times_vte_scale");

  lwu(scan_tmp, Address(recv_klass, Klass::vtable_length_offset()));

  // Could store the aligned, prescaled offset in the klass.
  shadd(scan_tmp, scan_tmp, recv_klass, scan_tmp, 3);
  add(scan_tmp, scan_tmp, vtable_base);

  if (return_method) {
    // Adjust recv_klass by scaled itable_index, so we can free itable_index.
    assert(itableMethodEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");
    if (itable_index.is_register()) {
      slli(t0, itable_index.as_register(), 3);
    } else {
      mv(t0, itable_index.as_constant() << 3);
    }
    add(recv_klass, recv_klass, t0);
    if (itentry_off) {
      add(recv_klass, recv_klass, itentry_off);
    }
  }

  Label search, found_method;

  ld(method_result, Address(scan_tmp, itableOffsetEntry::interface_offset()));
  beq(intf_klass, method_result, found_method);
  bind(search);
  // Check that the previous entry is non-null. A null entry means that
  // the receiver class doesn't implement the interface, and wasn't the
  // same as when the caller was compiled.
  beqz(method_result, L_no_such_interface, /* is_far */ true);
  addi(scan_tmp, scan_tmp, scan_step);
  ld(method_result, Address(scan_tmp, itableOffsetEntry::interface_offset()));
  bne(intf_klass, method_result, search);

  bind(found_method);

  // Got a hit.
  if (return_method) {
    lwu(scan_tmp, Address(scan_tmp, itableOffsetEntry::offset_offset()));
    add(method_result, recv_klass, scan_tmp);
    ld(method_result, Address(method_result));
  }
}

// Look up the method for a megamorphic invokeinterface call in a single pass over itable:
// - check recv_klass (actual object class) is a subtype of resolved_klass from CompiledICData
// - find a holder_klass (class that implements the method) vtable offset and get the method from vtable by index
// The target method is determined by <holder_klass, itable_index>.
// The receiver klass is in recv_klass.
// On success, the result will be in method_result, and execution falls through.
// On failure, execution transfers to the given label.
void MacroAssembler::lookup_interface_method_stub(Register recv_klass,
                                                  Register holder_klass,
                                                  Register resolved_klass,
                                                  Register method_result,
                                                  Register temp_itbl_klass,
                                                  Register scan_temp,
                                                  int itable_index,
                                                  Label& L_no_such_interface) {
  // 'method_result' is only used as output register at the very end of this method.
  // Until then we can reuse it as 'holder_offset'.
  Register holder_offset = method_result;
  assert_different_registers(resolved_klass, recv_klass, holder_klass, temp_itbl_klass, scan_temp, holder_offset);

  int vtable_start_offset_bytes = in_bytes(Klass::vtable_start_offset());
  int scan_step = itableOffsetEntry::size() * wordSize;
  int ioffset_bytes = in_bytes(itableOffsetEntry::interface_offset());
  int ooffset_bytes = in_bytes(itableOffsetEntry::offset_offset());
  int itmentry_off_bytes = in_bytes(itableMethodEntry::method_offset());
  const int vte_scale = exact_log2(vtableEntry::size_in_bytes());

  Label L_loop_search_resolved_entry, L_resolved_found, L_holder_found;

  lwu(scan_temp, Address(recv_klass, Klass::vtable_length_offset()));
  add(recv_klass, recv_klass, vtable_start_offset_bytes + ioffset_bytes);
  // itableOffsetEntry[] itable = recv_klass + Klass::vtable_start_offset()
  //                            + sizeof(vtableEntry) * (recv_klass->_vtable_len);
  // scan_temp = &(itable[0]._interface)
  // temp_itbl_klass = itable[0]._interface;
  shadd(scan_temp, scan_temp, recv_klass, scan_temp, vte_scale);
  ld(temp_itbl_klass, Address(scan_temp));
  mv(holder_offset, zr);

  // Initial checks:
  //   - if (holder_klass != resolved_klass), go to "scan for resolved"
  //   - if (itable[0] == holder_klass), shortcut to "holder found"
  //   - if (itable[0] == 0), no such interface
  bne(resolved_klass, holder_klass, L_loop_search_resolved_entry);
  beq(holder_klass, temp_itbl_klass, L_holder_found);
  beqz(temp_itbl_klass, L_no_such_interface);

  // Loop: Look for holder_klass record in itable
  //   do {
  //     temp_itbl_klass = *(scan_temp += scan_step);
  //     if (temp_itbl_klass == holder_klass) {
  //       goto L_holder_found; // Found!
  //     }
  //   } while (temp_itbl_klass != 0);
  //   goto L_no_such_interface // Not found.
  Label L_search_holder;
  bind(L_search_holder);
    add(scan_temp, scan_temp, scan_step);
    ld(temp_itbl_klass, Address(scan_temp));
    beq(holder_klass, temp_itbl_klass, L_holder_found);
    bnez(temp_itbl_klass, L_search_holder);

  j(L_no_such_interface);

  // Loop: Look for resolved_class record in itable
  //   while (true) {
  //     temp_itbl_klass = *(scan_temp += scan_step);
  //     if (temp_itbl_klass == 0) {
  //       goto L_no_such_interface;
  //     }
  //     if (temp_itbl_klass == resolved_klass) {
  //        goto L_resolved_found;  // Found!
  //     }
  //     if (temp_itbl_klass == holder_klass) {
  //        holder_offset = scan_temp;
  //     }
  //   }
  //
  Label L_loop_search_resolved;
  bind(L_loop_search_resolved);
    add(scan_temp, scan_temp, scan_step);
    ld(temp_itbl_klass, Address(scan_temp));
  bind(L_loop_search_resolved_entry);
    beqz(temp_itbl_klass, L_no_such_interface);
    beq(resolved_klass, temp_itbl_klass, L_resolved_found);
    bne(holder_klass, temp_itbl_klass, L_loop_search_resolved);
    mv(holder_offset, scan_temp);
    j(L_loop_search_resolved);

  // See if we already have a holder klass. If not, go and scan for it.
  bind(L_resolved_found);
  beqz(holder_offset, L_search_holder);
  mv(scan_temp, holder_offset);

  // Finally, scan_temp contains holder_klass vtable offset
  bind(L_holder_found);
  lwu(method_result, Address(scan_temp, ooffset_bytes - ioffset_bytes));
  add(recv_klass, recv_klass, itable_index * wordSize + itmentry_off_bytes
                              - vtable_start_offset_bytes - ioffset_bytes); // substract offsets to restore the original value of recv_klass
  add(method_result, recv_klass, method_result);
  ld(method_result, Address(method_result));
}

// virtual method calling
void MacroAssembler::lookup_virtual_method(Register recv_klass,
                                           RegisterOrConstant vtable_index,
                                           Register method_result) {
  const ByteSize base = Klass::vtable_start_offset();
  assert(vtableEntry::size() * wordSize == 8,
         "adjust the scaling in the code below");
  int vtable_offset_in_bytes = in_bytes(base + vtableEntry::method_offset());

  if (vtable_index.is_register()) {
    shadd(method_result, vtable_index.as_register(), recv_klass, method_result, LogBytesPerWord);
    ld(method_result, Address(method_result, vtable_offset_in_bytes));
  } else {
    vtable_offset_in_bytes += vtable_index.as_constant() * wordSize;
    ld(method_result, form_address(method_result, recv_klass, vtable_offset_in_bytes));
  }
}

void MacroAssembler::membar(uint32_t order_constraint) {
  if (UseZtso && ((order_constraint & StoreLoad) != StoreLoad)) {
    // TSO allows for stores to be reordered after loads. When the compiler
    // generates a fence to disallow that, we are required to generate the
    // fence for correctness.
    BLOCK_COMMENT("elided tso membar");
    return;
  }

  address prev = pc() - MacroAssembler::instruction_size;
  address last = code()->last_insn();

  if (last != nullptr && is_membar(last) && prev == last) {
    // We are merging two memory barrier instructions.  On RISCV we
    // can do this simply by ORing them together.
    set_membar_kind(prev, get_membar_kind(prev) | order_constraint);
    BLOCK_COMMENT("merged membar");
    return;
  }

  code()->set_last_insn(pc());
  uint32_t predecessor = 0;
  uint32_t successor = 0;
  membar_mask_to_pred_succ(order_constraint, predecessor, successor);
  fence(predecessor, successor);
}

void MacroAssembler::cmodx_fence() {
  BLOCK_COMMENT("cmodx fence");
  if (VM_Version::supports_fencei_barrier()) {
    Assembler::fencei();
  }
}

// Form an address from base + offset in Rd. Rd my or may not
// actually be used: you must use the Address that is returned. It
// is up to you to ensure that the shift provided matches the size
// of your data.
Address MacroAssembler::form_address(Register Rd, Register base, int64_t byte_offset) {
  if (is_simm12(byte_offset)) { // 12: imm in range 2^12
    return Address(base, byte_offset);
  }

  assert_different_registers(Rd, base, noreg);

  // Do it the hard way
  mv(Rd, byte_offset);
  add(Rd, base, Rd);
  return Address(Rd);
}

void MacroAssembler::check_klass_subtype(Register sub_klass,
                                         Register super_klass,
                                         Register tmp_reg,
                                         Label& L_success) {
  Label L_failure;
  check_klass_subtype_fast_path(sub_klass, super_klass, tmp_reg, &L_success, &L_failure, nullptr);
  check_klass_subtype_slow_path(sub_klass, super_klass, tmp_reg, noreg, &L_success, nullptr);
  bind(L_failure);
}

void MacroAssembler::safepoint_poll(Label& slow_path, bool at_return, bool in_nmethod, Register tmp_reg) {
  ld(tmp_reg, Address(xthread, JavaThread::polling_word_offset()));
  if (at_return) {
    bgtu(in_nmethod ? sp : fp, tmp_reg, slow_path, /* is_far */ true);
  } else {
    test_bit(tmp_reg, tmp_reg, exact_log2(SafepointMechanism::poll_bit()));
    bnez(tmp_reg, slow_path, /* is_far */ true);
  }
}

void MacroAssembler::cmpxchgptr(Register oldv, Register newv, Register addr, Register tmp,
                                Label &succeed, Label *fail) {
  assert_different_registers(addr, tmp, t0);
  assert_different_registers(newv, tmp, t0);
  assert_different_registers(oldv, tmp, t0);

  // oldv holds comparison value
  // newv holds value to write in exchange
  // addr identifies memory word to compare against/update
  if (UseZacas) {
    mv(tmp, oldv);
    atomic_cas(tmp, newv, addr, Assembler::int64, Assembler::aq, Assembler::rl);
    beq(tmp, oldv, succeed);
  } else {
    Label retry_load, nope;
    bind(retry_load);
    // Load reserved from the memory location
    load_reserved(tmp, addr, int64, Assembler::aqrl);
    // Fail and exit if it is not what we expect
    bne(tmp, oldv, nope);
    // If the store conditional succeeds, tmp will be zero
    store_conditional(tmp, newv, addr, int64, Assembler::rl);
    beqz(tmp, succeed);
    // Retry only when the store conditional failed
    j(retry_load);

    bind(nope);
  }

  // neither amocas nor lr/sc have an implied barrier in the failing case
  membar(AnyAny);

  mv(oldv, tmp);
  if (fail != nullptr) {
    j(*fail);
  }
}

void MacroAssembler::cmpxchg_obj_header(Register oldv, Register newv, Register obj, Register tmp,
                                        Label &succeed, Label *fail) {
  assert(oopDesc::mark_offset_in_bytes() == 0, "assumption");
  cmpxchgptr(oldv, newv, obj, tmp, succeed, fail);
}

void MacroAssembler::load_reserved(Register dst,
                                   Register addr,
                                   Assembler::operand_size size,
                                   Assembler::Aqrl acquire) {
  switch (size) {
    case int64:
      lr_d(dst, addr, acquire);
      break;
    case int32:
      lr_w(dst, addr, acquire);
      break;
    case uint32:
      lr_w(dst, addr, acquire);
      zext(dst, dst, 32);
      break;
    default:
      ShouldNotReachHere();
  }
}

void MacroAssembler::store_conditional(Register dst,
                                       Register new_val,
                                       Register addr,
                                       Assembler::operand_size size,
                                       Assembler::Aqrl release) {
  switch (size) {
    case int64:
      sc_d(dst, addr, new_val, release);
      break;
    case int32:
    case uint32:
      sc_w(dst, addr, new_val, release);
      break;
    default:
      ShouldNotReachHere();
  }
}


void MacroAssembler::cmpxchg_narrow_value_helper(Register addr, Register expected, Register new_val,
                                                 Assembler::operand_size size,
                                                 Register shift, Register mask, Register aligned_addr) {
  assert(size == int8 || size == int16, "unsupported operand size");

  andi(shift, addr, 3);
  slli(shift, shift, 3);

  andi(aligned_addr, addr, ~3);

  if (size == int8) {
    mv(mask, 0xff);
  } else {
    // size == int16 case
    mv(mask, -1);
    zext(mask, mask, 16);
  }
  sll(mask, mask, shift);

  sll(expected, expected, shift);
  andr(expected, expected, mask);

  sll(new_val, new_val, shift);
  andr(new_val, new_val, mask);
}

// cmpxchg_narrow_value will kill t0, t1, expected, new_val and tmps.
// It's designed to implement compare and swap byte/boolean/char/short by lr.w/sc.w or amocas.w,
// which are forced to work with 4-byte aligned address.
void MacroAssembler::cmpxchg_narrow_value(Register addr, Register expected,
                                          Register new_val,
                                          Assembler::operand_size size,
                                          Assembler::Aqrl acquire, Assembler::Aqrl release,
                                          Register result, bool result_as_bool,
                                          Register tmp1, Register tmp2, Register tmp3) {
  assert(!(UseZacas && UseZabha), "Use amocas");
  assert_different_registers(addr, expected, new_val, result, tmp1, tmp2, tmp3, t0, t1);

  Register scratch0 = t0, aligned_addr = t1;
  Register shift = tmp1, mask = tmp2, scratch1 = tmp3;

  cmpxchg_narrow_value_helper(addr, expected, new_val, size, shift, mask, aligned_addr);

  Label retry, fail, done;

  if (UseZacas) {
    lw(result, aligned_addr);

    bind(retry); // amocas loads the current value into result
    notr(scratch1, mask);

    andr(scratch0, result, scratch1);  // scratch0 = word - cas bits
    orr(scratch1, expected, scratch0); // scratch1 = non-cas bits + cas bits
    bne(result, scratch1, fail);       // cas bits differ, cas failed

    // result is the same as expected, use as expected value.

    // scratch0 is still = word - cas bits
    // Or in the new value to create complete new value.
    orr(scratch0, scratch0, new_val);

    mv(scratch1, result); // save our expected value
    atomic_cas(result, scratch0, aligned_addr, operand_size::int32, acquire, release);
    bne(scratch1, result, retry);
  } else {
    notr(scratch1, mask);
    bind(retry);

    load_reserved(result, aligned_addr, operand_size::int32, acquire);
    andr(scratch0, result, mask);
    bne(scratch0, expected, fail);

    andr(scratch0, result, scratch1); // scratch1 is ~mask
    orr(scratch0, scratch0, new_val);
    store_conditional(scratch0, scratch0, aligned_addr, operand_size::int32, release);
    bnez(scratch0, retry);
  }

  if (result_as_bool) {
    mv(result, 1);
    j(done);

    bind(fail);
    mv(result, zr);

    bind(done);
  } else {
    bind(fail);

    andr(scratch0, result, mask);
    srl(result, scratch0, shift);

    if (size == int8) {
      sext(result, result, 8);
    } else {
      // size == int16 case
      sext(result, result, 16);
    }
  }
}

// weak_cmpxchg_narrow_value is a weak version of cmpxchg_narrow_value, to implement
// the weak CAS stuff. The major difference is that it just failed when store conditional
// failed.
void MacroAssembler::weak_cmpxchg_narrow_value(Register addr, Register expected,
                                               Register new_val,
                                               Assembler::operand_size size,
                                               Assembler::Aqrl acquire, Assembler::Aqrl release,
                                               Register result,
                                               Register tmp1, Register tmp2, Register tmp3) {
  assert(!(UseZacas && UseZabha), "Use amocas");
  assert_different_registers(addr, expected, new_val, result, tmp1, tmp2, tmp3, t0, t1);

  Register scratch0 = t0, aligned_addr = t1;
  Register shift = tmp1, mask = tmp2, scratch1 = tmp3;

  cmpxchg_narrow_value_helper(addr, expected, new_val, size, shift, mask, aligned_addr);

  Label fail, done;

  if (UseZacas) {
    lw(result, aligned_addr);

    notr(scratch1, mask);

    andr(scratch0, result, scratch1);  // scratch0 = word - cas bits
    orr(scratch1, expected, scratch0); // scratch1 = non-cas bits + cas bits
    bne(result, scratch1, fail);       // cas bits differ, cas failed

    // result is the same as expected, use as expected value.

    // scratch0 is still = word - cas bits
    // Or in the new value to create complete new value.
    orr(scratch0, scratch0, new_val);

    mv(scratch1, result); // save our expected value
    atomic_cas(result, scratch0, aligned_addr, operand_size::int32, acquire, release);
    bne(scratch1, result, fail); // This weak, so just bail-out.
  } else {
    notr(scratch1, mask);

    load_reserved(result, aligned_addr, operand_size::int32, acquire);
    andr(scratch0, result, mask);
    bne(scratch0, expected, fail);

    andr(scratch0, result, scratch1); // scratch1 is ~mask
    orr(scratch0, scratch0, new_val);
    store_conditional(scratch0, scratch0, aligned_addr, operand_size::int32, release);
    bnez(scratch0, fail);
  }

  // Success
  mv(result, 1);
  j(done);

  // Fail
  bind(fail);
  mv(result, zr);

  bind(done);
}

void MacroAssembler::cmpxchg(Register addr, Register expected,
                             Register new_val,
                             Assembler::operand_size size,
                             Assembler::Aqrl acquire, Assembler::Aqrl release,
                             Register result, bool result_as_bool) {
  assert((UseZacas && UseZabha) || (size != int8 && size != int16), "unsupported operand size");
  assert_different_registers(addr, t0);
  assert_different_registers(expected, t0);
  assert_different_registers(new_val, t0);

  // NOTE:
  // Register _result_ may be the same register as _new_val_ or _expected_.
  // Hence do NOT use _result_ until after 'cas'.
  //
  // Register _expected_ may be the same register as _new_val_ and is assumed to be preserved.
  // Hence do NOT change _expected_ or _new_val_.
  //
  // Having _expected_ and _new_val_ being the same register is a very puzzling cas.
  //
  // TODO: Address these issues.

  if (UseZacas) {
    if (result_as_bool) {
      mv(t0, expected);
      atomic_cas(t0, new_val, addr, size, acquire, release);
      xorr(t0, t0, expected);
      seqz(result, t0);
    } else {
      mv(t0, expected);
      atomic_cas(t0, new_val, addr, size, acquire, release);
      mv(result, t0);
    }
    return;
  }

  Label retry_load, done, ne_done;
  bind(retry_load);
  load_reserved(t0, addr, size, acquire);
  bne(t0, expected, ne_done);
  store_conditional(t0, new_val, addr, size, release);
  bnez(t0, retry_load);

  // equal, succeed
  if (result_as_bool) {
    mv(result, 1);
  } else {
    mv(result, expected);
  }
  j(done);

  // not equal, failed
  bind(ne_done);
  if (result_as_bool) {
    mv(result, zr);
  } else {
    mv(result, t0);
  }

  bind(done);
}

void MacroAssembler::weak_cmpxchg(Register addr, Register expected,
                                  Register new_val,
                                  Assembler::operand_size size,
                                  Assembler::Aqrl acquire, Assembler::Aqrl release,
                                  Register result) {
  assert((UseZacas && UseZabha) || (size != int8 && size != int16), "unsupported operand size");
  assert_different_registers(addr, t0);
  assert_different_registers(expected, t0);
  assert_different_registers(new_val, t0);

  if (UseZacas) {
    cmpxchg(addr, expected, new_val, size, acquire, release, result, true);
    return;
  }

  Label fail, done;
  load_reserved(t0, addr, size, acquire);
  bne(t0, expected, fail);
  store_conditional(t0, new_val, addr, size, release);
  bnez(t0, fail);

  // Success
  mv(result, 1);
  j(done);

  // Fail
  bind(fail);
  mv(result, zr);

  bind(done);
}

#define ATOMIC_OP(NAME, AOP, ACQUIRE, RELEASE)                                              \
void MacroAssembler::atomic_##NAME(Register prev, RegisterOrConstant incr, Register addr) { \
  prev = prev->is_valid() ? prev : zr;                                                      \
  if (incr.is_register()) {                                                                 \
    AOP(prev, addr, incr.as_register(), (Assembler::Aqrl)(ACQUIRE | RELEASE));              \
  } else {                                                                                  \
    mv(t0, incr.as_constant());                                                             \
    AOP(prev, addr, t0, (Assembler::Aqrl)(ACQUIRE | RELEASE));                              \
  }                                                                                         \
  return;                                                                                   \
}

ATOMIC_OP(add, amoadd_d, Assembler::relaxed, Assembler::relaxed)
ATOMIC_OP(addw, amoadd_w, Assembler::relaxed, Assembler::relaxed)
ATOMIC_OP(addal, amoadd_d, Assembler::aq, Assembler::rl)
ATOMIC_OP(addalw, amoadd_w, Assembler::aq, Assembler::rl)

#undef ATOMIC_OP

#define ATOMIC_XCHG(OP, AOP, ACQUIRE, RELEASE)                                       \
void MacroAssembler::atomic_##OP(Register prev, Register newv, Register addr) {      \
  prev = prev->is_valid() ? prev : zr;                                               \
  AOP(prev, addr, newv, (Assembler::Aqrl)(ACQUIRE | RELEASE));                       \
  return;                                                                            \
}

ATOMIC_XCHG(xchg, amoswap_d, Assembler::relaxed, Assembler::relaxed)
ATOMIC_XCHG(xchgw, amoswap_w, Assembler::relaxed, Assembler::relaxed)
ATOMIC_XCHG(xchgal, amoswap_d, Assembler::aq, Assembler::rl)
ATOMIC_XCHG(xchgalw, amoswap_w, Assembler::aq, Assembler::rl)

#undef ATOMIC_XCHG

#define ATOMIC_XCHGU(OP1, OP2)                                                       \
void MacroAssembler::atomic_##OP1(Register prev, Register newv, Register addr) {     \
  atomic_##OP2(prev, newv, addr);                                                    \
  zext(prev, prev, 32);                                                       \
  return;                                                                            \
}

ATOMIC_XCHGU(xchgwu, xchgw)
ATOMIC_XCHGU(xchgalwu, xchgalw)

#undef ATOMIC_XCHGU

void MacroAssembler::atomic_cas(Register prev, Register newv, Register addr,
                                Assembler::operand_size size, Assembler::Aqrl acquire, Assembler::Aqrl release) {
  switch (size) {
    case int64:
      amocas_d(prev, addr, newv, (Assembler::Aqrl)(acquire | release));
      break;
    case int32:
      amocas_w(prev, addr, newv, (Assembler::Aqrl)(acquire | release));
      break;
    case uint32:
      amocas_w(prev, addr, newv, (Assembler::Aqrl)(acquire | release));
      zext(prev, prev, 32);
      break;
    case int16:
      amocas_h(prev, addr, newv, (Assembler::Aqrl)(acquire | release));
      break;
    case int8:
      amocas_b(prev, addr, newv, (Assembler::Aqrl)(acquire | release));
      break;
    default:
      ShouldNotReachHere();
  }
}

void MacroAssembler::far_jump(const Address &entry, Register tmp) {
  assert(CodeCache::contains(entry.target()),
         "destination of far jump not found in code cache");
  assert(entry.rspec().type() == relocInfo::external_word_type
        || entry.rspec().type() == relocInfo::runtime_call_type
        || entry.rspec().type() == relocInfo::none, "wrong entry relocInfo type");
  // Fixed length: see MacroAssembler::far_branch_size()
  // We can use auipc + jr here because we know that the total size of
  // the code cache cannot exceed 2Gb.
  relocate(entry.rspec(), [&] {
    int64_t distance = entry.target() - pc();
    int32_t offset = ((int32_t)distance << 20) >> 20;
    assert(is_valid_32bit_offset(distance), "Far jump using wrong instructions.");
    auipc(tmp, (int32_t)distance + 0x800);
    jr(tmp, offset);
  });
}

void MacroAssembler::far_call(const Address &entry, Register tmp) {
  assert(tmp != x5, "tmp register must not be x5.");
  assert(CodeCache::contains(entry.target()),
         "destination of far call not found in code cache");
  assert(entry.rspec().type() == relocInfo::external_word_type
        || entry.rspec().type() == relocInfo::runtime_call_type
        || entry.rspec().type() == relocInfo::none, "wrong entry relocInfo type");
  // Fixed length: see MacroAssembler::far_branch_size()
  // We can use auipc + jalr here because we know that the total size of
  // the code cache cannot exceed 2Gb.
  relocate(entry.rspec(), [&] {
    int64_t distance = entry.target() - pc();
    int32_t offset = ((int32_t)distance << 20) >> 20;
    assert(is_valid_32bit_offset(distance), "Far call using wrong instructions.");
    auipc(tmp, (int32_t)distance + 0x800);
    jalr(tmp, offset);
  });
}

void MacroAssembler::check_klass_subtype_fast_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register tmp_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   Label* L_slow_path,
                                                   Register super_check_offset) {
  assert_different_registers(sub_klass, super_klass, tmp_reg, super_check_offset);
  bool must_load_sco = !super_check_offset->is_valid();
  if (must_load_sco) {
    assert(tmp_reg != noreg, "supply either a temp or a register offset");
  }

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == nullptr)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == nullptr)   { L_failure   = &L_fallthrough; label_nulls++; }
  if (L_slow_path == nullptr) { L_slow_path = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one null in batch");

  int sc_offset = in_bytes(Klass::secondary_super_cache_offset());
  int sco_offset = in_bytes(Klass::super_check_offset_offset());
  Address super_check_offset_addr(super_klass, sco_offset);

  // Hacked jmp, which may only be used just before L_fallthrough.
#define final_jmp(label)                                                \
  if (&(label) == &L_fallthrough) { /*do nothing*/ }                    \
  else                            j(label)             /*omit semi*/

  // If the pointers are equal, we are done (e.g., String[] elements).
  // This self-check enables sharing of secondary supertype arrays among
  // non-primary types such as array-of-interface. Otherwise, each such
  // type would need its own customized SSA.
  // We move this check to the front of the fast path because many
  // type checks are in fact trivially successful in this manner,
  // so we get a nicely predicted branch right at the start of the check.
  beq(sub_klass, super_klass, *L_success);

  // Check the supertype display:
  if (must_load_sco) {
    lwu(tmp_reg, super_check_offset_addr);
    super_check_offset = tmp_reg;
  }
  add(t0, sub_klass, super_check_offset);
  Address super_check_addr(t0);
  ld(t0, super_check_addr); // load displayed supertype
  beq(super_klass, t0, *L_success);

  // This check has worked decisively for primary supers.
  // Secondary supers are sought in the super_cache ('super_cache_addr').
  // (Secondary supers are interfaces and very deeply nested subtypes.)
  // This works in the same check above because of a tricky aliasing
  // between the super_Cache and the primary super display elements.
  // (The 'super_check_addr' can address either, as the case requires.)
  // Note that the cache is updated below if it does not help us find
  // what we need immediately.
  // So if it was a primary super, we can just fail immediately.
  // Otherwise, it's the slow path for us (no success at this point).

  mv(t1, sc_offset);
  if (L_failure == &L_fallthrough) {
    beq(super_check_offset, t1, *L_slow_path);
  } else {
    bne(super_check_offset, t1, *L_failure, /* is_far */ true);
    final_jmp(*L_slow_path);
  }

  bind(L_fallthrough);

#undef final_jmp
}

// Scans count pointer sized words at [addr] for occurrence of value,
// generic
void MacroAssembler::repne_scan(Register addr, Register value, Register count,
                                Register tmp) {
  Label Lloop, Lexit;
  beqz(count, Lexit);
  bind(Lloop);
  ld(tmp, addr);
  beq(value, tmp, Lexit);
  addi(addr, addr, wordSize);
  subi(count, count, 1);
  bnez(count, Lloop);
  bind(Lexit);
}

void MacroAssembler::check_klass_subtype_slow_path_linear(Register sub_klass,
                                                          Register super_klass,
                                                          Register tmp1_reg,
                                                          Register tmp2_reg,
                                                          Label* L_success,
                                                          Label* L_failure,
                                                          bool set_cond_codes) {
  assert_different_registers(sub_klass, super_klass, tmp1_reg);
  if (tmp2_reg != noreg) {
    assert_different_registers(sub_klass, super_klass, tmp1_reg, tmp2_reg, t0);
  }
#define IS_A_TEMP(reg) ((reg) == tmp1_reg || (reg) == tmp2_reg)

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == nullptr)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == nullptr)   { L_failure   = &L_fallthrough; label_nulls++; }

  assert(label_nulls <= 1, "at most one null in the batch");

  // A couple of useful fields in sub_klass:
  int ss_offset = in_bytes(Klass::secondary_supers_offset());
  int sc_offset = in_bytes(Klass::secondary_super_cache_offset());
  Address secondary_supers_addr(sub_klass, ss_offset);
  Address super_cache_addr(     sub_klass, sc_offset);

  BLOCK_COMMENT("check_klass_subtype_slow_path");

  // Do a linear scan of the secondary super-klass chain.
  // This code is rarely used, so simplicity is a virtue here.
  // The repne_scan instruction uses fixed registers, which we must spill.
  // Don't worry too much about pre-existing connections with the input regs.

  assert(sub_klass != x10, "killed reg"); // killed by mv(x10, super)
  assert(sub_klass != x12, "killed reg"); // killed by la(x12, &pst_counter)

  RegSet pushed_registers;
  if (!IS_A_TEMP(x12)) {
    pushed_registers += x12;
  }
  if (!IS_A_TEMP(x15)) {
    pushed_registers += x15;
  }

  if (super_klass != x10) {
    if (!IS_A_TEMP(x10)) {
      pushed_registers += x10;
    }
  }

  push_reg(pushed_registers, sp);

  // Get super_klass value into x10 (even if it was in x15 or x12)
  mv(x10, super_klass);

#ifndef PRODUCT
  incrementw(ExternalAddress((address)&SharedRuntime::_partial_subtype_ctr));
#endif // PRODUCT

  // We will consult the secondary-super array.
  ld(x15, secondary_supers_addr);
  // Load the array length.
  lwu(x12, Address(x15, Array<Klass*>::length_offset_in_bytes()));
  // Skip to start of data.
  addi(x15, x15, Array<Klass*>::base_offset_in_bytes());

  // Set t0 to an obvious invalid value, falling through by default
  mv(t0, -1);
  // Scan X12 words at [X15] for an occurrence of X10.
  repne_scan(x15, x10, x12, t0);

  // pop will restore x10, so we should use a temp register to keep its value
  mv(t1, x10);

  // Unspill the temp registers:
  pop_reg(pushed_registers, sp);

  bne(t1, t0, *L_failure);

  // Success. Cache the super we found an proceed in triumph.
  if (UseSecondarySupersCache) {
    sd(super_klass, super_cache_addr);
  }

  if (L_success != &L_fallthrough) {
    j(*L_success);
  }

#undef IS_A_TEMP

  bind(L_fallthrough);
}

// population_count variant for running without the CPOP
// instruction, which was introduced with Zbb extension.
void MacroAssembler::population_count(Register dst, Register src,
                                      Register tmp1, Register tmp2) {
  if (UsePopCountInstruction) {
    cpop(dst, src);
  } else {
    assert_different_registers(src, tmp1, tmp2);
    assert_different_registers(dst, tmp1, tmp2);
    Label loop, done;

    mv(tmp1, src);
    // dst = 0;
    // while(tmp1 != 0) {
    //   dst++;
    //   tmp1 &= (tmp1 - 1);
    // }
    mv(dst, zr);
    beqz(tmp1, done);
    {
      bind(loop);
      addi(dst, dst, 1);
      subi(tmp2, tmp1, 1);
      andr(tmp1, tmp1, tmp2);
      bnez(tmp1, loop);
    }
    bind(done);
  }
}

// If Register r is invalid, remove a new register from
// available_regs, and add new register to regs_to_push.
Register MacroAssembler::allocate_if_noreg(Register r,
                                  RegSetIterator<Register> &available_regs,
                                  RegSet &regs_to_push) {
  if (!r->is_valid()) {
    r = *available_regs++;
    regs_to_push += r;
  }
  return r;
}

// check_klass_subtype_slow_path_table() looks for super_klass in the
// hash table belonging to super_klass, branching to L_success or
// L_failure as appropriate. This is essentially a shim which
// allocates registers as necessary then calls
// lookup_secondary_supers_table() to do the work. Any of the tmp
// regs may be noreg, in which case this logic will chooses some
// registers push and pop them from the stack.
void MacroAssembler::check_klass_subtype_slow_path_table(Register sub_klass,
                                                         Register super_klass,
                                                         Register tmp1_reg,
                                                         Register tmp2_reg,
                                                         Label* L_success,
                                                         Label* L_failure,
                                                         bool set_cond_codes) {
  RegSet tmps = RegSet::of(tmp1_reg, tmp2_reg);

  assert_different_registers(sub_klass, super_klass, tmp1_reg, tmp2_reg);

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == nullptr)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == nullptr)   { L_failure   = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one null in the batch");

  BLOCK_COMMENT("check_klass_subtype_slow_path");

  RegSet caller_save_regs = RegSet::of(x7) + RegSet::range(x10, x17) + RegSet::range(x28, x31);
  RegSetIterator<Register> available_regs = (caller_save_regs - tmps - sub_klass - super_klass).begin();

  RegSet pushed_regs;

  tmp1_reg = allocate_if_noreg(tmp1_reg, available_regs, pushed_regs);
  tmp2_reg = allocate_if_noreg(tmp2_reg, available_regs, pushed_regs);

  Register tmp3_reg = noreg, tmp4_reg = noreg, result_reg = noreg;

  tmp3_reg = allocate_if_noreg(tmp3_reg, available_regs, pushed_regs);
  tmp4_reg = allocate_if_noreg(tmp4_reg, available_regs, pushed_regs);
  result_reg = allocate_if_noreg(result_reg, available_regs, pushed_regs);

  push_reg(pushed_regs, sp);

  lookup_secondary_supers_table_var(sub_klass,
                                    super_klass,
                                    result_reg,
                                    tmp1_reg, tmp2_reg, tmp3_reg,
                                    tmp4_reg, nullptr);

  // Move the result to t1 as we are about to unspill the tmp registers.
  mv(t1, result_reg);

  // Unspill the tmp. registers:
  pop_reg(pushed_regs, sp);

  // NB! Callers may assume that, when set_cond_codes is true, this
  // code sets tmp2_reg to a nonzero value.
  if (set_cond_codes) {
    mv(tmp2_reg, 1);
  }

  bnez(t1, *L_failure);

  if (L_success != &L_fallthrough) {
    j(*L_success);
  }

  bind(L_fallthrough);
}

void MacroAssembler::check_klass_subtype_slow_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register tmp1_reg,
                                                   Register tmp2_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   bool set_cond_codes) {
  if (UseSecondarySupersTable) {
    check_klass_subtype_slow_path_table
      (sub_klass, super_klass, tmp1_reg, tmp2_reg, L_success, L_failure, set_cond_codes);
  } else {
    check_klass_subtype_slow_path_linear
      (sub_klass, super_klass, tmp1_reg, tmp2_reg, L_success, L_failure, set_cond_codes);
  }
}

// Ensure that the inline code and the stub are using the same registers
// as we need to call the stub from inline code when there is a collision
// in the hashed lookup in the secondary supers array.
#define LOOKUP_SECONDARY_SUPERS_TABLE_REGISTERS(r_super_klass, r_array_base, r_array_length,  \
                                                r_array_index, r_sub_klass, result, r_bitmap) \
do {                                                                                          \
  assert(r_super_klass  == x10                             &&                                 \
         r_array_base   == x11                             &&                                 \
         r_array_length == x12                             &&                                 \
         (r_array_index == x13  || r_array_index == noreg) &&                                 \
         (r_sub_klass   == x14  || r_sub_klass   == noreg) &&                                 \
         (result        == x15  || result        == noreg) &&                                 \
         (r_bitmap      == x16  || r_bitmap      == noreg), "registers must match riscv.ad"); \
} while(0)

bool MacroAssembler::lookup_secondary_supers_table_const(Register r_sub_klass,
                                                         Register r_super_klass,
                                                         Register result,
                                                         Register tmp1,
                                                         Register tmp2,
                                                         Register tmp3,
                                                         Register tmp4,
                                                         u1 super_klass_slot,
                                                         bool stub_is_near) {
  assert_different_registers(r_sub_klass, r_super_klass, result, tmp1, tmp2, tmp3, tmp4, t0, t1);

  Label L_fallthrough;

  BLOCK_COMMENT("lookup_secondary_supers_table {");

  const Register
    r_array_base   = tmp1, // x11
    r_array_length = tmp2, // x12
    r_array_index  = tmp3, // x13
    r_bitmap       = tmp4; // x16

  LOOKUP_SECONDARY_SUPERS_TABLE_REGISTERS(r_super_klass, r_array_base, r_array_length,
                                          r_array_index, r_sub_klass, result, r_bitmap);

  u1 bit = super_klass_slot;

  // Initialize result value to 1 which means mismatch.
  mv(result, 1);

  ld(r_bitmap, Address(r_sub_klass, Klass::secondary_supers_bitmap_offset()));

  // First check the bitmap to see if super_klass might be present. If
  // the bit is zero, we are certain that super_klass is not one of
  // the secondary supers.
  test_bit(t0, r_bitmap, bit);
  beqz(t0, L_fallthrough);

  // Get the first array index that can contain super_klass into r_array_index.
  if (bit != 0) {
    slli(r_array_index, r_bitmap, (Klass::SECONDARY_SUPERS_TABLE_MASK - bit));
    population_count(r_array_index, r_array_index, tmp1, tmp2);
  } else {
    mv(r_array_index, (u1)1);
  }

  // We will consult the secondary-super array.
  ld(r_array_base, Address(r_sub_klass, in_bytes(Klass::secondary_supers_offset())));

  // The value i in r_array_index is >= 1, so even though r_array_base
  // points to the length, we don't need to adjust it to point to the data.
  assert(Array<Klass*>::base_offset_in_bytes() == wordSize, "Adjust this code");
  assert(Array<Klass*>::length_offset_in_bytes() == 0, "Adjust this code");

  shadd(result, r_array_index, r_array_base, result, LogBytesPerWord);
  ld(result, Address(result));
  xorr(result, result, r_super_klass);
  beqz(result, L_fallthrough); // Found a match

  // Is there another entry to check? Consult the bitmap.
  test_bit(t0, r_bitmap, (bit + 1) & Klass::SECONDARY_SUPERS_TABLE_MASK);
  beqz(t0, L_fallthrough);

  // Linear probe.
  if (bit != 0) {
    ror(r_bitmap, r_bitmap, bit);
  }

  // The slot we just inspected is at secondary_supers[r_array_index - 1].
  // The next slot to be inspected, by the stub we're about to call,
  // is secondary_supers[r_array_index]. Bits 0 and 1 in the bitmap
  // have been checked.
  rt_call(StubRoutines::lookup_secondary_supers_table_slow_path_stub());

  BLOCK_COMMENT("} lookup_secondary_supers_table");

  bind(L_fallthrough);

  if (VerifySecondarySupers) {
    verify_secondary_supers_table(r_sub_klass, r_super_klass, // x14, x10
                                  result, tmp1, tmp2, tmp3);  // x15, x11, x12, x13
  }
  return true;
}

// At runtime, return 0 in result if r_super_klass is a superclass of
// r_sub_klass, otherwise return nonzero. Use this version of
// lookup_secondary_supers_table() if you don't know ahead of time
// which superclass will be searched for. Used by interpreter and
// runtime stubs. It is larger and has somewhat greater latency than
// the version above, which takes a constant super_klass_slot.
void MacroAssembler::lookup_secondary_supers_table_var(Register r_sub_klass,
                                                       Register r_super_klass,
                                                       Register result,
                                                       Register tmp1,
                                                       Register tmp2,
                                                       Register tmp3,
                                                       Register tmp4,
                                                       Label *L_success) {
  assert_different_registers(r_sub_klass, r_super_klass, result, tmp1, tmp2, tmp3, tmp4, t0, t1);

  Label L_fallthrough;

  BLOCK_COMMENT("lookup_secondary_supers_table {");

  const Register
    r_array_index = tmp3,
    r_bitmap      = tmp4,
    slot          = t1;

  lbu(slot, Address(r_super_klass, Klass::hash_slot_offset()));

  // Make sure that result is nonzero if the test below misses.
  mv(result, 1);

  ld(r_bitmap, Address(r_sub_klass, Klass::secondary_supers_bitmap_offset()));

  // First check the bitmap to see if super_klass might be present. If
  // the bit is zero, we are certain that super_klass is not one of
  // the secondary supers.

  // This next instruction is equivalent to:
  // mv(tmp_reg, (u1)(Klass::SECONDARY_SUPERS_TABLE_SIZE - 1));
  // sub(r_array_index, slot, tmp_reg);
  xori(r_array_index, slot, (u1)(Klass::SECONDARY_SUPERS_TABLE_SIZE - 1));
  sll(r_array_index, r_bitmap, r_array_index);
  test_bit(t0, r_array_index, Klass::SECONDARY_SUPERS_TABLE_SIZE - 1);
  beqz(t0, L_fallthrough);

  // Get the first array index that can contain super_klass into r_array_index.
  population_count(r_array_index, r_array_index, tmp1, tmp2);

  // NB! r_array_index is off by 1. It is compensated by keeping r_array_base off by 1 word.

  const Register
    r_array_base   = tmp1,
    r_array_length = tmp2;

  // The value i in r_array_index is >= 1, so even though r_array_base
  // points to the length, we don't need to adjust it to point to the data.
  assert(Array<Klass*>::base_offset_in_bytes() == wordSize, "Adjust this code");
  assert(Array<Klass*>::length_offset_in_bytes() == 0, "Adjust this code");

  // We will consult the secondary-super array.
  ld(r_array_base, Address(r_sub_klass, in_bytes(Klass::secondary_supers_offset())));

  shadd(result, r_array_index, r_array_base, result, LogBytesPerWord);
  ld(result, Address(result));
  xorr(result, result, r_super_klass);
  beqz(result, L_success ? *L_success : L_fallthrough); // Found a match

  // Is there another entry to check? Consult the bitmap.
  ror(r_bitmap, r_bitmap, slot);
  test_bit(t0, r_bitmap, 1);
  beqz(t0, L_fallthrough);

  // The slot we just inspected is at secondary_supers[r_array_index - 1].
  // The next slot to be inspected, by the logic we're about to call,
  // is secondary_supers[r_array_index]. Bits 0 and 1 in the bitmap
  // have been checked.
  lookup_secondary_supers_table_slow_path(r_super_klass, r_array_base, r_array_index,
                                          r_bitmap, result, r_array_length, false /*is_stub*/);

  BLOCK_COMMENT("} lookup_secondary_supers_table");

  bind(L_fallthrough);

  if (VerifySecondarySupers) {
    verify_secondary_supers_table(r_sub_klass, r_super_klass,
                                  result, tmp1, tmp2, tmp3);
  }

  if (L_success) {
    beqz(result, *L_success);
  }
}

// Called by code generated by check_klass_subtype_slow_path
// above. This is called when there is a collision in the hashed
// lookup in the secondary supers array.
void MacroAssembler::lookup_secondary_supers_table_slow_path(Register r_super_klass,
                                                             Register r_array_base,
                                                             Register r_array_index,
                                                             Register r_bitmap,
                                                             Register result,
                                                             Register tmp,
                                                             bool is_stub) {
  assert_different_registers(r_super_klass, r_array_base, r_array_index, r_bitmap, tmp, result, t0);

  const Register
    r_array_length = tmp,
    r_sub_klass    = noreg; // unused

  if (is_stub) {
    LOOKUP_SECONDARY_SUPERS_TABLE_REGISTERS(r_super_klass, r_array_base, r_array_length,
                                            r_array_index, r_sub_klass, result, r_bitmap);
  }

  Label L_matched, L_fallthrough, L_bitmap_full;

  // Initialize result value to 1 which means mismatch.
  mv(result, 1);

  // Load the array length.
  lwu(r_array_length, Address(r_array_base, Array<Klass*>::length_offset_in_bytes()));
  // And adjust the array base to point to the data.
  // NB! Effectively increments current slot index by 1.
  assert(Array<Klass*>::base_offset_in_bytes() == wordSize, "");
  addi(r_array_base, r_array_base, Array<Klass*>::base_offset_in_bytes());

  // Check if bitmap is SECONDARY_SUPERS_BITMAP_FULL
  assert(Klass::SECONDARY_SUPERS_BITMAP_FULL == ~uintx(0), "Adjust this code");
  subw(t0, r_array_length, Klass::SECONDARY_SUPERS_TABLE_SIZE - 2);
  bgtz(t0, L_bitmap_full);

  // NB! Our caller has checked bits 0 and 1 in the bitmap. The
  // current slot (at secondary_supers[r_array_index]) has not yet
  // been inspected, and r_array_index may be out of bounds if we
  // wrapped around the end of the array.

  { // This is conventional linear probing, but instead of terminating
    // when a null entry is found in the table, we maintain a bitmap
    // in which a 0 indicates missing entries.
    // As long as the bitmap is not completely full,
    // array_length == popcount(bitmap). The array_length check above
    // guarantees there are 0s in the bitmap, so the loop eventually
    // terminates.
    Label L_loop;
    bind(L_loop);

    // Check for wraparound.
    Label skip;
    blt(r_array_index, r_array_length, skip);
    mv(r_array_index, zr);
    bind(skip);

    shadd(t0, r_array_index, r_array_base, t0, LogBytesPerWord);
    ld(t0, Address(t0));
    beq(t0, r_super_klass, L_matched);

    test_bit(t0, r_bitmap, 2);  // look-ahead check (Bit 2); result is non-zero
    beqz(t0, L_fallthrough);

    ror(r_bitmap, r_bitmap, 1);
    addi(r_array_index, r_array_index, 1);
    j(L_loop);
  }

  { // Degenerate case: more than 64 secondary supers.
    // FIXME: We could do something smarter here, maybe a vectorized
    // comparison or a binary search, but is that worth any added
    // complexity?
    bind(L_bitmap_full);
    repne_scan(r_array_base, r_super_klass, r_array_length, t0);
    bne(r_super_klass, t0, L_fallthrough);
  }

  bind(L_matched);
  mv(result, zr);

  bind(L_fallthrough);
}

// Make sure that the hashed lookup and a linear scan agree.
void MacroAssembler::verify_secondary_supers_table(Register r_sub_klass,
                                                   Register r_super_klass,
                                                   Register result,
                                                   Register tmp1,
                                                   Register tmp2,
                                                   Register tmp3) {
  assert_different_registers(r_sub_klass, r_super_klass, tmp1, tmp2, tmp3, result, t0, t1);

  const Register
    r_array_base   = tmp1,  // X11
    r_array_length = tmp2,  // X12
    r_array_index  = noreg, // unused
    r_bitmap       = noreg; // unused

  BLOCK_COMMENT("verify_secondary_supers_table {");

  // We will consult the secondary-super array.
  ld(r_array_base, Address(r_sub_klass, in_bytes(Klass::secondary_supers_offset())));

  // Load the array length.
  lwu(r_array_length, Address(r_array_base, Array<Klass*>::length_offset_in_bytes()));
  // And adjust the array base to point to the data.
  addi(r_array_base, r_array_base, Array<Klass*>::base_offset_in_bytes());

  repne_scan(r_array_base, r_super_klass, r_array_length, t0);
  Label failed;
  mv(tmp3, 1);
  bne(r_super_klass, t0, failed);
  mv(tmp3, zr);
  bind(failed);

  snez(result, result); // normalize result to 0/1 for comparison

  Label passed;
  beq(tmp3, result, passed);
  {
    mv(x10, r_super_klass);
    mv(x11, r_sub_klass);
    mv(x12, tmp3);
    mv(x13, result);
    mv(x14, (address)("mismatch"));
    rt_call(CAST_FROM_FN_PTR(address, Klass::on_secondary_supers_verification_failure));
    should_not_reach_here();
  }
  bind(passed);

  BLOCK_COMMENT("} verify_secondary_supers_table");
}

// Defines obj, preserves var_size_in_bytes, okay for tmp2 == var_size_in_bytes.
void MacroAssembler::tlab_allocate(Register obj,
                                   Register var_size_in_bytes,
                                   int con_size_in_bytes,
                                   Register tmp1,
                                   Register tmp2,
                                   Label& slow_case,
                                   bool is_far) {
  BarrierSetAssembler *bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->tlab_allocate(this, obj, var_size_in_bytes, con_size_in_bytes, tmp1, tmp2, slow_case, is_far);
}

// get_thread() can be called anywhere inside generated code so we
// need to save whatever non-callee save context might get clobbered
// by the call to Thread::current() or, indeed, the call setup code.
void MacroAssembler::get_thread(Register thread) {
  // save all call-clobbered regs except thread
  RegSet saved_regs = RegSet::range(x5, x7) + RegSet::range(x10, x17) +
                      RegSet::range(x28, x31) + ra - thread;
  push_reg(saved_regs, sp);

  mv(t1, CAST_FROM_FN_PTR(address, Thread::current));
  jalr(t1);
  if (thread != c_rarg0) {
    mv(thread, c_rarg0);
  }

  // restore pushed registers
  pop_reg(saved_regs, sp);
}

void MacroAssembler::load_byte_map_base(Register reg) {
  CardTableBarrierSet* ctbs = CardTableBarrierSet::barrier_set();
  mv(reg, (uint64_t)ctbs->card_table_base_const());
}

void MacroAssembler::build_frame(int framesize) {
  assert(framesize >= 2, "framesize must include space for FP/RA");
  assert(framesize % (2*wordSize) == 0, "must preserve 2*wordSize alignment");
  sub(sp, sp, framesize);
  sd(fp, Address(sp, framesize - 2 * wordSize));
  sd(ra, Address(sp, framesize - wordSize));
  if (PreserveFramePointer) { add(fp, sp, framesize); }
}

void MacroAssembler::remove_frame(int framesize) {
  assert(framesize >= 2, "framesize must include space for FP/RA");
  assert(framesize % (2*wordSize) == 0, "must preserve 2*wordSize alignment");
  ld(fp, Address(sp, framesize - 2 * wordSize));
  ld(ra, Address(sp, framesize - wordSize));
  add(sp, sp, framesize);
}

void MacroAssembler::reserved_stack_check() {
  // testing if reserved zone needs to be enabled
  Label no_reserved_zone_enabling;

  ld(t0, Address(xthread, JavaThread::reserved_stack_activation_offset()));
  bltu(sp, t0, no_reserved_zone_enabling);

  enter();   // RA and FP are live.
  mv(c_rarg0, xthread);
  rt_call(CAST_FROM_FN_PTR(address, SharedRuntime::enable_stack_reserved_zone));
  leave();

  // We have already removed our own frame.
  // throw_delayed_StackOverflowError will think that it's been
  // called by our caller.
  j(RuntimeAddress(SharedRuntime::throw_delayed_StackOverflowError_entry()));
  should_not_reach_here();

  bind(no_reserved_zone_enabling);
}

// Move the address of the polling page into dest.
void MacroAssembler::get_polling_page(Register dest, relocInfo::relocType rtype) {
  ld(dest, Address(xthread, JavaThread::polling_page_offset()));
}

// Read the polling page.  The address of the polling page must
// already be in r.
void MacroAssembler::read_polling_page(Register r, int32_t offset, relocInfo::relocType rtype) {
  relocate(rtype, [&] {
    lwu(zr, Address(r, offset));
  });
}

void MacroAssembler::set_narrow_oop(Register dst, jobject obj) {
#ifdef ASSERT
  {
    ThreadInVMfromUnknown tiv;
    assert (UseCompressedOops, "should only be used for compressed oops");
    assert (Universe::heap() != nullptr, "java heap should be initialized");
    assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
    assert(Universe::heap()->is_in(JNIHandles::resolve(obj)), "should be real oop");
  }
#endif
  int oop_index = oop_recorder()->find_index(obj);
  relocate(oop_Relocation::spec(oop_index), [&] {
    li32(dst, 0xDEADBEEF);
  });
  zext(dst, dst, 32);
}

void  MacroAssembler::set_narrow_klass(Register dst, Klass* k) {
  assert (UseCompressedClassPointers, "should only be used for compressed headers");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int index = oop_recorder()->find_index(k);

  narrowKlass nk = CompressedKlassPointers::encode(k);
  relocate(metadata_Relocation::spec(index), [&] {
    li32(dst, nk);
  });
  zext(dst, dst, 32);
}

address MacroAssembler::reloc_call(Address entry, Register tmp) {
  assert(entry.rspec().type() == relocInfo::runtime_call_type ||
         entry.rspec().type() == relocInfo::opt_virtual_call_type ||
         entry.rspec().type() == relocInfo::static_call_type ||
         entry.rspec().type() == relocInfo::virtual_call_type, "wrong reloc type");

  address target = entry.target();

  if (!in_scratch_emit_size()) {
    address stub = emit_reloc_call_address_stub(offset(), target);
    if (stub == nullptr) {
      postcond(pc() == badAddress);
      return nullptr; // CodeCache is full
    }
  }

  address call_pc = pc();
#ifdef ASSERT
  if (entry.rspec().type() != relocInfo::runtime_call_type) {
    assert_alignment(call_pc);
  }
#endif

  // The relocation created while emitting the stub will ensure this
  // call instruction is subsequently patched to call the stub.
  relocate(entry.rspec(), [&] {
    auipc(tmp, 0);
    ld(tmp, Address(tmp, 0));
    jalr(tmp);
  });

  postcond(pc() != badAddress);
  return call_pc;
}

address MacroAssembler::ic_call(address entry, jint method_index) {
  RelocationHolder rh = virtual_call_Relocation::spec(pc(), method_index);
  assert(!in_compressible_scope(), "Must be");
  movptr(t0, (address)Universe::non_oop_word(), t1);
  assert_cond(entry != nullptr);
  return reloc_call(Address(entry, rh));
}

int MacroAssembler::ic_check_size() {
  // No compressed
  return (MacroAssembler::instruction_size * (2 /* 2 loads */ + 1 /* branch */)) +
          far_branch_size() + (UseCompactObjectHeaders ? MacroAssembler::instruction_size * 1 : 0);
}

int MacroAssembler::ic_check(int end_alignment) {
  IncompressibleScope scope(this);
  Register receiver = j_rarg0;
  Register data = t0;

  Register tmp1 = t1; // scratch
  // t2 is saved on call, thus should have been saved before this check.
  // Hence we can clobber it.
  Register tmp2 = t2;

  // The UEP of a code blob ensures that the VEP is padded. However, the padding of the UEP is placed
  // before the inline cache check, so we don't have to execute any nop instructions when dispatching
  // through the UEP, yet we can ensure that the VEP is aligned appropriately. That's why we align
  // before the inline cache check here, and not after
  align(end_alignment, ic_check_size());
  int uep_offset = offset();

  if (UseCompactObjectHeaders) {
    load_narrow_klass_compact(tmp1, receiver);
    lwu(tmp2, Address(data, CompiledICData::speculated_klass_offset()));
  } else if (UseCompressedClassPointers) {
    lwu(tmp1, Address(receiver, oopDesc::klass_offset_in_bytes()));
    lwu(tmp2, Address(data, CompiledICData::speculated_klass_offset()));
  } else {
    ld(tmp1,  Address(receiver, oopDesc::klass_offset_in_bytes()));
    ld(tmp2, Address(data, CompiledICData::speculated_klass_offset()));
  }

  Label ic_hit;
  beq(tmp1, tmp2, ic_hit);
  // Note, far_jump is not fixed size.
  // Is this ever generates a movptr alignment/size will be off.
  far_jump(RuntimeAddress(SharedRuntime::get_ic_miss_stub()));
  bind(ic_hit);

  assert((offset() % end_alignment) == 0, "Misaligned verified entry point.");
  return uep_offset;
}

// Emit an address stub for a call to a target which is too far away.
// Note that we only put the target address of the call in the stub.
//
// code sequences:
//
// call-site:
//   load target address from stub
//   jump-and-link target address
//
// Related address stub for this call site in the stub section:
//   alignment nop
//   target address

address MacroAssembler::emit_reloc_call_address_stub(int insts_call_instruction_offset, address dest) {
  address stub = start_a_stub(max_reloc_call_address_stub_size());
  if (stub == nullptr) {
    return nullptr;  // CodeBuffer::expand failed
  }

  // We are always 4-byte aligned here.
  assert_alignment(pc());

  // Make sure the address of destination 8-byte aligned.
  align(wordSize, 0);

  RelocationHolder rh = trampoline_stub_Relocation::spec(code()->insts()->start() +
                                                         insts_call_instruction_offset);
  const int stub_start_offset = offset();
  relocate(rh, [&] {
    assert(offset() - stub_start_offset == 0,
           "%ld - %ld == %ld : should be", (long)offset(), (long)stub_start_offset, (long)0);
    assert(offset() % wordSize == 0, "bad alignment");
    emit_int64((int64_t)dest);
  });

  const address stub_start_addr = addr_at(stub_start_offset);
  end_a_stub();

  return stub_start_addr;
}

int MacroAssembler::max_reloc_call_address_stub_size() {
  // Max stub size: alignment nop, target address.
  return 1 * MacroAssembler::instruction_size + wordSize;
}

int MacroAssembler::static_call_stub_size() {
  // (lui, addi, slli, addi, slli, addi) + (lui + lui + slli + add) + jalr
  return 11 * MacroAssembler::instruction_size;
}

Address MacroAssembler::add_memory_helper(const Address dst, Register tmp) {
  switch (dst.getMode()) {
    case Address::base_plus_offset:
      // This is the expected mode, although we allow all the other
      // forms below.
      return form_address(tmp, dst.base(), dst.offset());
    default:
      la(tmp, dst);
      return Address(tmp);
  }
}

void MacroAssembler::increment(const Address dst, int64_t value, Register tmp1, Register tmp2) {
  assert(((dst.getMode() == Address::base_plus_offset &&
           is_simm12(dst.offset())) || is_simm12(value)),
          "invalid value and address mode combination");
  Address adr = add_memory_helper(dst, tmp2);
  assert(!adr.uses(tmp1), "invalid dst for address increment");
  ld(tmp1, adr);
  add(tmp1, tmp1, value, tmp2);
  sd(tmp1, adr);
}

void MacroAssembler::incrementw(const Address dst, int32_t value, Register tmp1, Register tmp2) {
  assert(((dst.getMode() == Address::base_plus_offset &&
           is_simm12(dst.offset())) || is_simm12(value)),
          "invalid value and address mode combination");
  Address adr = add_memory_helper(dst, tmp2);
  assert(!adr.uses(tmp1), "invalid dst for address increment");
  lwu(tmp1, adr);
  addw(tmp1, tmp1, value, tmp2);
  sw(tmp1, adr);
}

void MacroAssembler::decrement(const Address dst, int64_t value, Register tmp1, Register tmp2) {
  assert(((dst.getMode() == Address::base_plus_offset &&
           is_simm12(dst.offset())) || is_simm12(value)),
          "invalid value and address mode combination");
  Address adr = add_memory_helper(dst, tmp2);
  assert(!adr.uses(tmp1), "invalid dst for address decrement");
  ld(tmp1, adr);
  sub(tmp1, tmp1, value, tmp2);
  sd(tmp1, adr);
}

void MacroAssembler::decrementw(const Address dst, int32_t value, Register tmp1, Register tmp2) {
  assert(((dst.getMode() == Address::base_plus_offset &&
           is_simm12(dst.offset())) || is_simm12(value)),
          "invalid value and address mode combination");
  Address adr = add_memory_helper(dst, tmp2);
  assert(!adr.uses(tmp1), "invalid dst for address decrement");
  lwu(tmp1, adr);
  subw(tmp1, tmp1, value, tmp2);
  sw(tmp1, adr);
}

void MacroAssembler::cmpptr(Register src1, const Address &src2, Label& equal, Register tmp) {
  assert_different_registers(src1, tmp);
  assert(src2.getMode() == Address::literal, "must be applied to a literal address");
  ld(tmp, src2);
  beq(src1, tmp, equal);
}

void MacroAssembler::load_method_holder_cld(Register result, Register method) {
  load_method_holder(result, method);
  ld(result, Address(result, InstanceKlass::class_loader_data_offset()));
}

void MacroAssembler::load_method_holder(Register holder, Register method) {
  ld(holder, Address(method, Method::const_offset()));                      // ConstMethod*
  ld(holder, Address(holder, ConstMethod::constants_offset()));             // ConstantPool*
  ld(holder, Address(holder, ConstantPool::pool_holder_offset()));          // InstanceKlass*
}

// string indexof
// compute index by trailing zeros
void MacroAssembler::compute_index(Register haystack, Register trailing_zeros,
                                   Register match_mask, Register result,
                                   Register ch2, Register tmp,
                                   bool haystack_isL) {
  int haystack_chr_shift = haystack_isL ? 0 : 1;
  srl(match_mask, match_mask, trailing_zeros);
  srli(match_mask, match_mask, 1);
  srli(tmp, trailing_zeros, LogBitsPerByte);
  if (!haystack_isL) andi(tmp, tmp, 0xE);
  add(haystack, haystack, tmp);
  ld(ch2, Address(haystack));
  if (!haystack_isL) srli(tmp, tmp, haystack_chr_shift);
  add(result, result, tmp);
}

// string indexof
// Find pattern element in src, compute match mask,
// only the first occurrence of 0x80/0x8000 at low bits is the valid match index
// match mask patterns and corresponding indices would be like:
// - 0x8080808080808080 (Latin1)
// -   7 6 5 4 3 2 1 0  (match index)
// - 0x8000800080008000 (UTF16)
// -   3   2   1   0    (match index)
void MacroAssembler::compute_match_mask(Register src, Register pattern, Register match_mask,
                                        Register mask1, Register mask2) {
  xorr(src, pattern, src);
  sub(match_mask, src, mask1);
  orr(src, src, mask2);
  notr(src, src);
  andr(match_mask, match_mask, src);
}

#ifdef COMPILER2
// Code for BigInteger::mulAdd intrinsic
// out     = x10
// in      = x11
// offset  = x12  (already out.length-offset)
// len     = x13
// k       = x14
// tmp     = x28
//
// pseudo code from java implementation:
// long kLong = k & LONG_MASK;
// carry = 0;
// offset = out.length-offset - 1;
// for (int j = len - 1; j >= 0; j--) {
//     product = (in[j] & LONG_MASK) * kLong + (out[offset] & LONG_MASK) + carry;
//     out[offset--] = (int)product;
//     carry = product >>> 32;
// }
// return (int)carry;
void MacroAssembler::mul_add(Register out, Register in, Register offset,
                             Register len, Register k, Register tmp) {
  Label L_tail_loop, L_unroll, L_end;
  mv(tmp, out);
  mv(out, zr);
  blez(len, L_end);
  zext(k, k, 32);
  slliw(t0, offset, LogBytesPerInt);
  add(offset, tmp, t0);
  slliw(t0, len, LogBytesPerInt);
  add(in, in, t0);

  const int unroll = 8;
  mv(tmp, unroll);
  blt(len, tmp, L_tail_loop);
  bind(L_unroll);
  for (int i = 0; i < unroll; i++) {
    subi(in, in, BytesPerInt);
    lwu(t0, Address(in, 0));
    mul(t1, t0, k);
    add(t0, t1, out);
    subi(offset, offset, BytesPerInt);
    lwu(t1, Address(offset, 0));
    add(t0, t0, t1);
    sw(t0, Address(offset, 0));
    srli(out, t0, 32);
  }
  subw(len, len, tmp);
  bge(len, tmp, L_unroll);

  bind(L_tail_loop);
  blez(len, L_end);
  subi(in, in, BytesPerInt);
  lwu(t0, Address(in, 0));
  mul(t1, t0, k);
  add(t0, t1, out);
  subi(offset, offset, BytesPerInt);
  lwu(t1, Address(offset, 0));
  add(t0, t0, t1);
  sw(t0, Address(offset, 0));
  srli(out, t0, 32);
  subiw(len, len, 1);
  j(L_tail_loop);

  bind(L_end);
}

// Multiply and multiply-accumulate unsigned 64-bit registers.
void MacroAssembler::wide_mul(Register prod_lo, Register prod_hi, Register n, Register m) {
  assert_different_registers(prod_lo, prod_hi);

  mul(prod_lo, n, m);
  mulhu(prod_hi, n, m);
}

void MacroAssembler::wide_madd(Register sum_lo, Register sum_hi, Register n,
                               Register m, Register tmp1, Register tmp2) {
  assert_different_registers(sum_lo, sum_hi);
  assert_different_registers(sum_hi, tmp2);

  wide_mul(tmp1, tmp2, n, m);
  cad(sum_lo, sum_lo, tmp1, tmp1);  // Add tmp1 to sum_lo with carry output to tmp1
  adc(sum_hi, sum_hi, tmp2, tmp1);  // Add tmp2 with carry to sum_hi
}

// add two unsigned input and output carry
void MacroAssembler::cad(Register dst, Register src1, Register src2, Register carry)
{
  assert_different_registers(dst, carry);
  assert_different_registers(dst, src2);
  add(dst, src1, src2);
  sltu(carry, dst, src2);
}

// add two input with carry
void MacroAssembler::adc(Register dst, Register src1, Register src2, Register carry) {
  assert_different_registers(dst, carry);
  add(dst, src1, src2);
  add(dst, dst, carry);
}

// add two unsigned input with carry and output carry
void MacroAssembler::cadc(Register dst, Register src1, Register src2, Register carry) {
  assert_different_registers(dst, src2);
  adc(dst, src1, src2, carry);
  sltu(carry, dst, src2);
}

void MacroAssembler::add2_with_carry(Register final_dest_hi, Register dest_hi, Register dest_lo,
                                     Register src1, Register src2, Register carry) {
  cad(dest_lo, dest_lo, src1, carry);
  add(dest_hi, dest_hi, carry);
  cad(dest_lo, dest_lo, src2, carry);
  add(final_dest_hi, dest_hi, carry);
}

/**
 * Multiply 64 bit by 64 bit first loop.
 */
void MacroAssembler::multiply_64_x_64_loop(Register x, Register xstart, Register x_xstart,
                                           Register y, Register y_idx, Register z,
                                           Register carry, Register product,
                                           Register idx, Register kdx) {
  //
  //  jlong carry, x[], y[], z[];
  //  for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx--, kdx--) {
  //    huge_128 product = y[idx] * x[xstart] + carry;
  //    z[kdx] = (jlong)product;
  //    carry  = (jlong)(product >>> 64);
  //  }
  //  z[xstart] = carry;
  //

  Label L_first_loop, L_first_loop_exit;
  Label L_one_x, L_one_y, L_multiply;

  subiw(xstart, xstart, 1);
  bltz(xstart, L_one_x);

  shadd(t0, xstart, x, t0, LogBytesPerInt);
  ld(x_xstart, Address(t0, 0));
  ror(x_xstart, x_xstart, 32); // convert big-endian to little-endian

  bind(L_first_loop);
  subiw(idx, idx, 1);
  bltz(idx, L_first_loop_exit);
  subiw(idx, idx, 1);
  bltz(idx, L_one_y);

  shadd(t0, idx, y, t0, LogBytesPerInt);
  ld(y_idx, Address(t0, 0));
  ror(y_idx, y_idx, 32); // convert big-endian to little-endian
  bind(L_multiply);

  mulhu(t0, x_xstart, y_idx);
  mul(product, x_xstart, y_idx);
  cad(product, product, carry, t1);
  adc(carry, t0, zr, t1);

  subiw(kdx, kdx, 2);
  ror(product, product, 32); // back to big-endian
  shadd(t0, kdx, z, t0, LogBytesPerInt);
  sd(product, Address(t0, 0));

  j(L_first_loop);

  bind(L_one_y);
  lwu(y_idx, Address(y, 0));
  j(L_multiply);

  bind(L_one_x);
  lwu(x_xstart, Address(x, 0));
  j(L_first_loop);

  bind(L_first_loop_exit);
}

/**
 * Multiply 128 bit by 128 bit. Unrolled inner loop.
 *
 */
void MacroAssembler::multiply_128_x_128_loop(Register y, Register z,
                                             Register carry, Register carry2,
                                             Register idx, Register jdx,
                                             Register yz_idx1, Register yz_idx2,
                                             Register tmp, Register tmp3, Register tmp4,
                                             Register tmp6, Register product_hi) {
  //   jlong carry, x[], y[], z[];
  //   int kdx = xstart+1;
  //   for (int idx=ystart-2; idx >= 0; idx -= 2) { // Third loop
  //     huge_128 tmp3 = (y[idx+1] * product_hi) + z[kdx+idx+1] + carry;
  //     jlong carry2  = (jlong)(tmp3 >>> 64);
  //     huge_128 tmp4 = (y[idx]   * product_hi) + z[kdx+idx] + carry2;
  //     carry  = (jlong)(tmp4 >>> 64);
  //     z[kdx+idx+1] = (jlong)tmp3;
  //     z[kdx+idx] = (jlong)tmp4;
  //   }
  //   idx += 2;
  //   if (idx > 0) {
  //     yz_idx1 = (y[idx] * product_hi) + z[kdx+idx] + carry;
  //     z[kdx+idx] = (jlong)yz_idx1;
  //     carry  = (jlong)(yz_idx1 >>> 64);
  //   }
  //

  Label L_third_loop, L_third_loop_exit, L_post_third_loop_done;

  srliw(jdx, idx, 2);

  bind(L_third_loop);

  subw(jdx, jdx, 1);
  bltz(jdx, L_third_loop_exit);
  subw(idx, idx, 4);

  shadd(t0, idx, y, t0, LogBytesPerInt);
  ld(yz_idx2, Address(t0, 0));
  ld(yz_idx1, Address(t0, wordSize));

  shadd(tmp6, idx, z, t0, LogBytesPerInt);

  ror(yz_idx1, yz_idx1, 32); // convert big-endian to little-endian
  ror(yz_idx2, yz_idx2, 32);

  ld(t1, Address(tmp6, 0));
  ld(t0, Address(tmp6, wordSize));

  mul(tmp3, product_hi, yz_idx1); //  yz_idx1 * product_hi -> tmp4:tmp3
  mulhu(tmp4, product_hi, yz_idx1);

  ror(t0, t0, 32, tmp); // convert big-endian to little-endian
  ror(t1, t1, 32, tmp);

  mul(tmp, product_hi, yz_idx2); //  yz_idx2 * product_hi -> carry2:tmp
  mulhu(carry2, product_hi, yz_idx2);

  cad(tmp3, tmp3, carry, carry);
  adc(tmp4, tmp4, zr, carry);
  cad(tmp3, tmp3, t0, t0);
  cadc(tmp4, tmp4, tmp, t0);
  adc(carry, carry2, zr, t0);
  cad(tmp4, tmp4, t1, carry2);
  adc(carry, carry, zr, carry2);

  ror(tmp3, tmp3, 32); // convert little-endian to big-endian
  ror(tmp4, tmp4, 32);
  sd(tmp4, Address(tmp6, 0));
  sd(tmp3, Address(tmp6, wordSize));

  j(L_third_loop);

  bind(L_third_loop_exit);

  andi(idx, idx, 0x3);
  beqz(idx, L_post_third_loop_done);

  Label L_check_1;
  subiw(idx, idx, 2);
  bltz(idx, L_check_1);

  shadd(t0, idx, y, t0, LogBytesPerInt);
  ld(yz_idx1, Address(t0, 0));
  ror(yz_idx1, yz_idx1, 32);

  mul(tmp3, product_hi, yz_idx1); //  yz_idx1 * product_hi -> tmp4:tmp3
  mulhu(tmp4, product_hi, yz_idx1);

  shadd(t0, idx, z, t0, LogBytesPerInt);
  ld(yz_idx2, Address(t0, 0));
  ror(yz_idx2, yz_idx2, 32, tmp);

  add2_with_carry(carry, tmp4, tmp3, carry, yz_idx2, tmp);

  ror(tmp3, tmp3, 32, tmp);
  sd(tmp3, Address(t0, 0));

  bind(L_check_1);

  andi(idx, idx, 0x1);
  subiw(idx, idx, 1);
  bltz(idx, L_post_third_loop_done);
  shadd(t0, idx, y, t0, LogBytesPerInt);
  lwu(tmp4, Address(t0, 0));
  mul(tmp3, tmp4, product_hi); //  tmp4 * product_hi -> carry2:tmp3
  mulhu(carry2, tmp4, product_hi);

  shadd(t0, idx, z, t0, LogBytesPerInt);
  lwu(tmp4, Address(t0, 0));

  add2_with_carry(carry2, carry2, tmp3, tmp4, carry, t0);

  shadd(t0, idx, z, t0, LogBytesPerInt);
  sw(tmp3, Address(t0, 0));

  slli(t0, carry2, 32);
  srli(carry, tmp3, 32);
  orr(carry, carry, t0);

  bind(L_post_third_loop_done);
}

/**
 * Code for BigInteger::multiplyToLen() intrinsic.
 *
 * x10: x
 * x11: xlen
 * x12: y
 * x13: ylen
 * x14: z
 * x15: tmp0
 * x16: tmp1
 * x17: tmp2
 * x7:  tmp3
 * x28: tmp4
 * x29: tmp5
 * x30: tmp6
 * x31: tmp7
 */
void MacroAssembler::multiply_to_len(Register x, Register xlen, Register y, Register ylen,
                                     Register z, Register tmp0,
                                     Register tmp1, Register tmp2, Register tmp3, Register tmp4,
                                     Register tmp5, Register tmp6, Register product_hi) {
  assert_different_registers(x, xlen, y, ylen, z, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6);

  const Register idx = tmp1;
  const Register kdx = tmp2;
  const Register xstart = tmp3;

  const Register y_idx = tmp4;
  const Register carry = tmp5;
  const Register product = xlen;
  const Register x_xstart = tmp0;
  const Register jdx = tmp1;

  mv(idx, ylen);         // idx = ylen;
  addw(kdx, xlen, ylen); // kdx = xlen+ylen;
  mv(carry, zr);         // carry = 0;

  Label L_done;
  subiw(xstart, xlen, 1);
  bltz(xstart, L_done);

  multiply_64_x_64_loop(x, xstart, x_xstart, y, y_idx, z, carry, product, idx, kdx);

  Label L_second_loop_aligned;
  beqz(kdx, L_second_loop_aligned);

  Label L_carry;
  subiw(kdx, kdx, 1);
  beqz(kdx, L_carry);

  shadd(t0, kdx, z, t0, LogBytesPerInt);
  sw(carry, Address(t0, 0));
  srli(carry, carry, 32);
  subiw(kdx, kdx, 1);

  bind(L_carry);
  shadd(t0, kdx, z, t0, LogBytesPerInt);
  sw(carry, Address(t0, 0));

  // Second and third (nested) loops.
  //
  // for (int i = xstart-1; i >= 0; i--) { // Second loop
  //   carry = 0;
  //   for (int jdx=ystart, k=ystart+1+i; jdx >= 0; jdx--, k--) { // Third loop
  //     long product = (y[jdx] & LONG_MASK) * (x[i] & LONG_MASK) +
  //                    (z[k] & LONG_MASK) + carry;
  //     z[k] = (int)product;
  //     carry = product >>> 32;
  //   }
  //   z[i] = (int)carry;
  // }
  //
  // i = xlen, j = tmp1, k = tmp2, carry = tmp5, x[i] = product_hi

  bind(L_second_loop_aligned);
  mv(carry, zr); // carry = 0;
  mv(jdx, ylen); // j = ystart+1

  subiw(xstart, xstart, 1); // i = xstart-1;
  bltz(xstart, L_done);

  subi(sp, sp, 4 * wordSize);
  sd(z, Address(sp, 0));

  Label L_last_x;
  shadd(t0, xstart, z, t0, LogBytesPerInt);
  addi(z, t0, 4);
  subiw(xstart, xstart, 1); // i = xstart-1;
  bltz(xstart, L_last_x);

  shadd(t0, xstart, x, t0, LogBytesPerInt);
  ld(product_hi, Address(t0, 0));
  ror(product_hi, product_hi, 32); // convert big-endian to little-endian

  Label L_third_loop_prologue;
  bind(L_third_loop_prologue);

  sd(ylen, Address(sp, wordSize));
  sd(x, Address(sp, 2 * wordSize));
  sd(xstart, Address(sp, 3 * wordSize));
  multiply_128_x_128_loop(y, z, carry, x, jdx, ylen, product,
                          tmp2, x_xstart, tmp3, tmp4, tmp6, product_hi);
  ld(z, Address(sp, 0));
  ld(ylen, Address(sp, wordSize));
  ld(x, Address(sp, 2 * wordSize));
  ld(xlen, Address(sp, 3 * wordSize)); // copy old xstart -> xlen
  addi(sp, sp, 4 * wordSize);

  addiw(tmp3, xlen, 1);
  shadd(t0, tmp3, z, t0, LogBytesPerInt);
  sw(carry, Address(t0, 0));

  subiw(tmp3, tmp3, 1);
  bltz(tmp3, L_done);

  srli(carry, carry, 32);
  shadd(t0, tmp3, z, t0, LogBytesPerInt);
  sw(carry, Address(t0, 0));
  j(L_second_loop_aligned);

  // Next infrequent code is moved outside loops.
  bind(L_last_x);
  lwu(product_hi, Address(x, 0));
  j(L_third_loop_prologue);

  bind(L_done);
}
#endif

// Count bits of trailing zero chars from lsb to msb until first non-zero
// char seen. For the LL case, shift 8 bits once as there is only one byte
// per each char. For other cases, shift 16 bits once.
void MacroAssembler::ctzc_bits(Register Rd, Register Rs, bool isLL,
                               Register tmp1, Register tmp2) {
  int step = isLL ? 8 : 16;
  if (UseZbb) {
    ctz(Rd, Rs);
    andi(Rd, Rd, -step);
    return;
  }

  assert_different_registers(Rd, tmp1, tmp2);
  Label Loop;
  mv(tmp2, Rs);
  mv(Rd, -step);

  bind(Loop);
  addi(Rd, Rd, step);
  zext(tmp1, tmp2, step);
  srli(tmp2, tmp2, step);
  beqz(tmp1, Loop);
}

// This instruction reads adjacent 4 bytes from the lower half of source register,
// inflate into a register, for example:
// Rs: A7A6A5A4A3A2A1A0
// Rd: 00A300A200A100A0
void MacroAssembler::inflate_lo32(Register Rd, Register Rs, Register tmp1, Register tmp2) {
  assert_different_registers(Rd, Rs, tmp1, tmp2);

  mv(tmp1, 0xFF000000); // first byte mask at lower word
  andr(Rd, Rs, tmp1);
  for (int i = 0; i < 2; i++) {
    slli(Rd, Rd, wordSize);
    srli(tmp1, tmp1, wordSize);
    andr(tmp2, Rs, tmp1);
    orr(Rd, Rd, tmp2);
  }
  slli(Rd, Rd, wordSize);
  zext(tmp2, Rs, 8); // last byte mask at lower word
  orr(Rd, Rd, tmp2);
}

// This instruction reads adjacent 4 bytes from the upper half of source register,
// inflate into a register, for example:
// Rs: A7A6A5A4A3A2A1A0
// Rd: 00A700A600A500A4
void MacroAssembler::inflate_hi32(Register Rd, Register Rs, Register tmp1, Register tmp2) {
  assert_different_registers(Rd, Rs, tmp1, tmp2);
  srli(Rs, Rs, 32);   // only upper 32 bits are needed
  inflate_lo32(Rd, Rs, tmp1, tmp2);
}

// The size of the blocks erased by the zero_blocks stub.  We must
// handle anything smaller than this ourselves in zero_words().
const int MacroAssembler::zero_words_block_size = 8;

// zero_words() is used by C2 ClearArray patterns.  It is as small as
// possible, handling small word counts locally and delegating
// anything larger to the zero_blocks stub.  It is expanded many times
// in compiled code, so it is important to keep it short.

// ptr:   Address of a buffer to be zeroed.
// cnt:   Count in HeapWords.
//
// ptr, cnt, t1, and t0 are clobbered.
address MacroAssembler::zero_words(Register ptr, Register cnt) {
  assert(is_power_of_2(zero_words_block_size), "adjust this");
  assert(ptr == x28 && cnt == x29, "mismatch in register usage");
  assert_different_registers(cnt, t0, t1);

  BLOCK_COMMENT("zero_words {");

  mv(t0, zero_words_block_size);
  Label around, done, done16;
  bltu(cnt, t0, around);
  {
    RuntimeAddress zero_blocks(StubRoutines::riscv::zero_blocks());
    assert(zero_blocks.target() != nullptr, "zero_blocks stub has not been generated");
    if (StubRoutines::riscv::complete()) {
      address tpc = reloc_call(zero_blocks);
      if (tpc == nullptr) {
        DEBUG_ONLY(reset_labels(around));
        postcond(pc() == badAddress);
        return nullptr;
      }
    } else {
      // Clobbers t1
      rt_call(zero_blocks.target());
    }
  }
  bind(around);
  for (int i = zero_words_block_size >> 1; i > 1; i >>= 1) {
    Label l;
    test_bit(t0, cnt, exact_log2(i));
    beqz(t0, l);
    for (int j = 0; j < i; j++) {
      sd(zr, Address(ptr, j * wordSize));
    }
    addi(ptr, ptr, i * wordSize);
    bind(l);
  }
  {
    Label l;
    test_bit(t0, cnt, 0);
    beqz(t0, l);
    sd(zr, Address(ptr, 0));
    bind(l);
  }

  BLOCK_COMMENT("} zero_words");
  postcond(pc() != badAddress);
  return pc();
}

#define SmallArraySize (18 * BytesPerLong)

// base:  Address of a buffer to be zeroed, 8 bytes aligned.
// cnt:   Immediate count in HeapWords.
void MacroAssembler::zero_words(Register base, uint64_t cnt) {
  assert_different_registers(base, t0, t1);

  BLOCK_COMMENT("zero_words {");

  if (cnt <= SmallArraySize / BytesPerLong) {
    for (int i = 0; i < (int)cnt; i++) {
      sd(zr, Address(base, i * wordSize));
    }
  } else {
    const int unroll = 8; // Number of sd(zr, adr), instructions we'll unroll
    int remainder = cnt % unroll;
    for (int i = 0; i < remainder; i++) {
      sd(zr, Address(base, i * wordSize));
    }

    Label loop;
    Register cnt_reg = t0;
    Register loop_base = t1;
    cnt = cnt - remainder;
    mv(cnt_reg, cnt);
    addi(loop_base, base, remainder * wordSize);
    bind(loop);
    sub(cnt_reg, cnt_reg, unroll);
    for (int i = 0; i < unroll; i++) {
      sd(zr, Address(loop_base, i * wordSize));
    }
    addi(loop_base, loop_base, unroll * wordSize);
    bnez(cnt_reg, loop);
  }

  BLOCK_COMMENT("} zero_words");
}

// base:   Address of a buffer to be filled, 8 bytes aligned.
// cnt:    Count in 8-byte unit.
// value:  Value to be filled with.
// base will point to the end of the buffer after filling.
void MacroAssembler::fill_words(Register base, Register cnt, Register value) {
//  Algorithm:
//
//    t0 = cnt & 7
//    cnt -= t0
//    p += t0
//    switch (t0):
//      switch start:
//      do while cnt
//        cnt -= 8
//          p[-8] = value
//        case 7:
//          p[-7] = value
//        case 6:
//          p[-6] = value
//          // ...
//        case 1:
//          p[-1] = value
//        case 0:
//          p += 8
//      do-while end
//    switch end

  assert_different_registers(base, cnt, value, t0, t1);

  Label fini, skip, entry, loop;
  const int unroll = 8; // Number of sd instructions we'll unroll

  beqz(cnt, fini);

  andi(t0, cnt, unroll - 1);
  sub(cnt, cnt, t0);
  shadd(base, t0, base, t1, 3);
  la(t1, entry);
  slli(t0, t0, 2);
  sub(t1, t1, t0);
  jr(t1);

  bind(loop);
  addi(base, base, unroll * wordSize);
  {
    IncompressibleScope scope(this); // Fixed length
    for (int i = -unroll; i < 0; i++) {
      sd(value, Address(base, i * 8));
    }
  }
  bind(entry);
  subi(cnt, cnt, unroll);
  bgez(cnt, loop);

  bind(fini);
}

// Zero blocks of memory by using CBO.ZERO.
//
// Aligns the base address first sufficiently for CBO.ZERO, then uses
// CBO.ZERO repeatedly for every full block.  cnt is the size to be
// zeroed in HeapWords.  Returns the count of words left to be zeroed
// in cnt.
//
// NOTE: This is intended to be used in the zero_blocks() stub.  If
// you want to use it elsewhere, note that cnt must be >= zicboz_block_size.
void MacroAssembler::zero_dcache_blocks(Register base, Register cnt, Register tmp1, Register tmp2) {
  int zicboz_block_size = VM_Version::zicboz_block_size.value();
  Label initial_table_end, loop;

  // Align base with cache line size.
  neg(tmp1, base);
  andi(tmp1, tmp1, zicboz_block_size - 1);

  // tmp1: the number of bytes to be filled to align the base with cache line size.
  add(base, base, tmp1);
  srai(tmp2, tmp1, 3);
  sub(cnt, cnt, tmp2);
  srli(tmp2, tmp1, 1);
  la(tmp1, initial_table_end);
  sub(tmp2, tmp1, tmp2);
  jr(tmp2);
  for (int i = -zicboz_block_size + wordSize; i < 0; i += wordSize) {
    sd(zr, Address(base, i));
  }
  bind(initial_table_end);

  mv(tmp1, zicboz_block_size / wordSize);
  bind(loop);
  cbo_zero(base);
  sub(cnt, cnt, tmp1);
  addi(base, base, zicboz_block_size);
  bge(cnt, tmp1, loop);
}

// java.lang.Math.round(float a)
// Returns the closest int to the argument, with ties rounding to positive infinity.
void MacroAssembler::java_round_float(Register dst, FloatRegister src, FloatRegister ftmp) {
  // this instructions calling sequence provides performance improvement on all tested devices;
  // don't change it without re-verification
  Label done;
  mv(t0, jint_cast(0.5f));
  fmv_w_x(ftmp, t0);

  // dst = 0 if NaN
  feq_s(t0, src, src); // replacing fclass with feq as performance optimization
  mv(dst, zr);
  beqz(t0, done);

  // dst = (src + 0.5f) rounded down towards negative infinity
  //   Adding 0.5f to some floats exceeds the precision limits for a float and rounding takes place.
  //   RDN is required for fadd_s, RNE gives incorrect results:
  //     --------------------------------------------------------------------
  //     fadd.s rne (src + 0.5f): src = 8388609.000000  ftmp = 8388610.000000
  //     fcvt.w.s rdn: ftmp = 8388610.000000 dst = 8388610
  //     --------------------------------------------------------------------
  //     fadd.s rdn (src + 0.5f): src = 8388609.000000  ftmp = 8388609.000000
  //     fcvt.w.s rdn: ftmp = 8388609.000000 dst = 8388609
  //     --------------------------------------------------------------------
  fadd_s(ftmp, src, ftmp, RoundingMode::rdn);
  fcvt_w_s(dst, ftmp, RoundingMode::rdn);

  bind(done);
}

// java.lang.Math.round(double a)
// Returns the closest long to the argument, with ties rounding to positive infinity.
void MacroAssembler::java_round_double(Register dst, FloatRegister src, FloatRegister ftmp) {
  // this instructions calling sequence provides performance improvement on all tested devices;
  // don't change it without re-verification
  Label done;
  mv(t0, julong_cast(0.5));
  fmv_d_x(ftmp, t0);

  // dst = 0 if NaN
  feq_d(t0, src, src); // replacing fclass with feq as performance optimization
  mv(dst, zr);
  beqz(t0, done);

  // dst = (src + 0.5) rounded down towards negative infinity
  fadd_d(ftmp, src, ftmp, RoundingMode::rdn); // RDN is required here otherwise some inputs produce incorrect results
  fcvt_l_d(dst, ftmp, RoundingMode::rdn);

  bind(done);
}

// Helper routine processing the slow path of NaN when converting float to float16
void MacroAssembler::float_to_float16_NaN(Register dst, FloatRegister src,
                                          Register tmp1, Register tmp2) {
  fmv_x_w(dst, src);

  //  Float (32 bits)
  //    Bit:     31        30 to 23          22 to 0
  //          +---+------------------+-----------------------------+
  //          | S |     Exponent     |      Mantissa (Fraction)    |
  //          +---+------------------+-----------------------------+
  //          1 bit       8 bits                  23 bits
  //
  //  Float (16 bits)
  //    Bit:    15        14 to 10         9 to 0
  //          +---+----------------+------------------+
  //          | S |    Exponent    |     Mantissa     |
  //          +---+----------------+------------------+
  //          1 bit      5 bits          10 bits
  const int fp_sign_bits = 1;
  const int fp32_bits = 32;
  const int fp32_exponent_bits = 8;
  const int fp32_mantissa_1st_part_bits = 10;
  const int fp32_mantissa_2nd_part_bits = 9;
  const int fp32_mantissa_3rd_part_bits = 4;
  const int fp16_exponent_bits = 5;
  const int fp16_mantissa_bits = 10;

  // preserve the sign bit and exponent, clear mantissa.
  srai(tmp2, dst, fp32_bits - fp_sign_bits - fp16_exponent_bits);
  slli(tmp2, tmp2, fp16_mantissa_bits);

  // Preserve high order bit of float NaN in the
  // binary16 result NaN (tenth bit); OR in remaining
  // bits into lower 9 bits of binary 16 significand.
  //   | (doppel & 0x007f_e000) >> 13 // 10 bits
  //   | (doppel & 0x0000_1ff0) >> 4  //  9 bits
  //   | (doppel & 0x0000_000f));     //  4 bits
  //
  // Check j.l.Float.floatToFloat16 for more information.
  // 10 bits
  int left_shift = fp_sign_bits + fp32_exponent_bits + 32;
  int right_shift = left_shift + fp32_mantissa_2nd_part_bits + fp32_mantissa_3rd_part_bits;
  slli(tmp1, dst, left_shift);
  srli(tmp1, tmp1, right_shift);
  orr(tmp2, tmp2, tmp1);
  // 9 bits
  left_shift += fp32_mantissa_1st_part_bits;
  right_shift = left_shift + fp32_mantissa_3rd_part_bits;
  slli(tmp1, dst, left_shift);
  srli(tmp1, tmp1, right_shift);
  orr(tmp2, tmp2, tmp1);
  // 4 bits
  andi(tmp1, dst, 0xf);
  orr(dst, tmp2, tmp1);
}

#define FCVT_SAFE(FLOATCVT, FLOATSIG)                                                     \
void MacroAssembler::FLOATCVT##_safe(Register dst, FloatRegister src, Register tmp) {     \
  Label done;                                                                             \
  assert_different_registers(dst, tmp);                                                   \
  fclass_##FLOATSIG(tmp, src);                                                            \
  mv(dst, zr);                                                                            \
  /* check if src is NaN */                                                               \
  andi(tmp, tmp, FClassBits::nan);                                                        \
  bnez(tmp, done);                                                                        \
  FLOATCVT(dst, src);                                                                     \
  bind(done);                                                                             \
}

FCVT_SAFE(fcvt_w_s, s);
FCVT_SAFE(fcvt_l_s, s);
FCVT_SAFE(fcvt_w_d, d);
FCVT_SAFE(fcvt_l_d, d);

#undef FCVT_SAFE

#define FCMP(FLOATTYPE, FLOATSIG)                                                       \
void MacroAssembler::FLOATTYPE##_compare(Register result, FloatRegister Rs1,            \
                                         FloatRegister Rs2, int unordered_result) {     \
  Label Ldone;                                                                          \
  if (unordered_result < 0) {                                                           \
    /* we want -1 for unordered or less than, 0 for equal and 1 for greater than. */    \
    /* installs 1 if gt else 0 */                                                       \
    flt_##FLOATSIG(result, Rs2, Rs1);                                                   \
    /* Rs1 > Rs2, install 1 */                                                          \
    bgtz(result, Ldone);                                                                \
    feq_##FLOATSIG(result, Rs1, Rs2);                                                   \
    subi(result, result, 1);                                                            \
    /* Rs1 = Rs2, install 0 */                                                          \
    /* NaN or Rs1 < Rs2, install -1 */                                                  \
    bind(Ldone);                                                                        \
  } else {                                                                              \
    /* we want -1 for less than, 0 for equal and 1 for unordered or greater than. */    \
    /* installs 1 if gt or unordered else 0 */                                          \
    flt_##FLOATSIG(result, Rs1, Rs2);                                                   \
    /* Rs1 < Rs2, install -1 */                                                         \
    bgtz(result, Ldone);                                                                \
    feq_##FLOATSIG(result, Rs1, Rs2);                                                   \
    subi(result, result, 1);                                                            \
    /* Rs1 = Rs2, install 0 */                                                          \
    /* NaN or Rs1 > Rs2, install 1 */                                                   \
    bind(Ldone);                                                                        \
    neg(result, result);                                                                \
  }                                                                                     \
}

FCMP(float, s);
FCMP(double, d);

#undef FCMP

// Zero words; len is in bytes
// Destroys all registers except addr
// len must be a nonzero multiple of wordSize
void MacroAssembler::zero_memory(Register addr, Register len, Register tmp) {
  assert_different_registers(addr, len, tmp, t0, t1);

#ifdef ASSERT
  {
    Label L;
    andi(t0, len, BytesPerWord - 1);
    beqz(t0, L);
    stop("len is not a multiple of BytesPerWord");
    bind(L);
  }
#endif // ASSERT

#ifndef PRODUCT
  block_comment("zero memory");
#endif // PRODUCT

  Label loop;
  Label entry;

  // Algorithm:
  //
  //  t0 = cnt & 7
  //  cnt -= t0
  //  p += t0
  //  switch (t0) {
  //    do {
  //      cnt -= 8
  //        p[-8] = 0
  //      case 7:
  //        p[-7] = 0
  //      case 6:
  //        p[-6] = 0
  //        ...
  //      case 1:
  //        p[-1] = 0
  //      case 0:
  //        p += 8
  //     } while (cnt)
  //  }

  const int unroll = 8;   // Number of sd(zr) instructions we'll unroll

  srli(len, len, LogBytesPerWord);
  andi(t0, len, unroll - 1);  // t0 = cnt % unroll
  sub(len, len, t0);          // cnt -= unroll
  // tmp always points to the end of the region we're about to zero
  shadd(tmp, t0, addr, t1, LogBytesPerWord);
  la(t1, entry);
  slli(t0, t0, 2);
  sub(t1, t1, t0);
  jr(t1);

  bind(loop);
  sub(len, len, unroll);
  {
    IncompressibleScope scope(this); // Fixed length
    for (int i = -unroll; i < 0; i++) {
      sd(zr, Address(tmp, i * wordSize));
    }
  }
  bind(entry);
  add(tmp, tmp, unroll * wordSize);
  bnez(len, loop);
}

// shift left by shamt and add
// Rd = (Rs1 << shamt) + Rs2
void MacroAssembler::shadd(Register Rd, Register Rs1, Register Rs2, Register tmp, int shamt) {
  if (UseZba) {
    if (shamt == 1) {
      sh1add(Rd, Rs1, Rs2);
      return;
    } else if (shamt == 2) {
      sh2add(Rd, Rs1, Rs2);
      return;
    } else if (shamt == 3) {
      sh3add(Rd, Rs1, Rs2);
      return;
    }
  }

  if (shamt != 0) {
    assert_different_registers(Rs2, tmp);
    slli(tmp, Rs1, shamt);
    add(Rd, Rs2, tmp);
  } else {
    add(Rd, Rs1, Rs2);
  }
}

void MacroAssembler::zext(Register dst, Register src, int bits) {
  switch (bits) {
    case 32:
      if (UseZba) {
        zext_w(dst, src);
        return;
      }
      break;
    case 16:
      if (UseZbb) {
        zext_h(dst, src);
        return;
      }
      break;
    case 8:
      zext_b(dst, src);
      return;
    default:
      break;
  }

  slli(dst, src, XLEN - bits);
  srli(dst, dst, XLEN - bits);
}

void MacroAssembler::sext(Register dst, Register src, int bits) {
  switch (bits) {
    case 32:
      sext_w(dst, src);
      return;
    case 16:
      if (UseZbb) {
        sext_h(dst, src);
        return;
      }
      break;
    case 8:
      if (UseZbb) {
        sext_b(dst, src);
        return;
      }
      break;
    default:
      break;
  }

  slli(dst, src, XLEN - bits);
  srai(dst, dst, XLEN - bits);
}

void MacroAssembler::cmp_x2i(Register dst, Register src1, Register src2,
                             Register tmp, bool is_signed) {
  if (src1 == src2) {
    mv(dst, zr);
    return;
  }
  Label done;
  Register left = src1;
  Register right = src2;
  if (dst == src1) {
    assert_different_registers(dst, src2, tmp);
    mv(tmp, src1);
    left = tmp;
  } else if (dst == src2) {
    assert_different_registers(dst, src1, tmp);
    mv(tmp, src2);
    right = tmp;
  }

  // installs 1 if gt else 0
  if (is_signed) {
    slt(dst, right, left);
  } else {
    sltu(dst, right, left);
  }
  bnez(dst, done);
  if (is_signed) {
    slt(dst, left, right);
  } else {
    sltu(dst, left, right);
  }
  // dst = -1 if lt; else if eq , dst = 0
  neg(dst, dst);
  bind(done);
}

void MacroAssembler::cmp_l2i(Register dst, Register src1, Register src2, Register tmp)
{
  cmp_x2i(dst, src1, src2, tmp);
}

void MacroAssembler::cmp_ul2i(Register dst, Register src1, Register src2, Register tmp) {
  cmp_x2i(dst, src1, src2, tmp, false);
}

void MacroAssembler::cmp_uw2i(Register dst, Register src1, Register src2, Register tmp) {
  cmp_x2i(dst, src1, src2, tmp, false);
}

// The java_calling_convention describes stack locations as ideal slots on
// a frame with no abi restrictions. Since we must observe abi restrictions
// (like the placement of the register window) the slots must be biased by
// the following value.
static int reg2offset_in(VMReg r) {
  // Account for saved fp and ra
  // This should really be in_preserve_stack_slots
  return r->reg2stack() * VMRegImpl::stack_slot_size;
}

static int reg2offset_out(VMReg r) {
  return (r->reg2stack() + SharedRuntime::out_preserve_stack_slots()) * VMRegImpl::stack_slot_size;
}

// The C ABI specifies:
// "integer scalars narrower than XLEN bits are widened according to the sign
// of their type up to 32 bits, then sign-extended to XLEN bits."
// Applies for both passed in register and stack.
//
// Java uses 32-bit stack slots; jint, jshort, jchar, jbyte uses one slot.
// Native uses 64-bit stack slots for all integer scalar types.
//
// lw loads the Java stack slot, sign-extends and
// sd store this widened integer into a 64 bit native stack slot.
void MacroAssembler::move32_64(VMRegPair src, VMRegPair dst, Register tmp) {
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      lw(tmp, Address(fp, reg2offset_in(src.first())));
      sd(tmp, Address(sp, reg2offset_out(dst.first())));
    } else {
      // stack to reg
      lw(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    sd(src.first()->as_Register(), Address(sp, reg2offset_out(dst.first())));
  } else {
    if (dst.first() != src.first()) {
      sext(dst.first()->as_Register(), src.first()->as_Register(), 32);
    }
  }
}

// An oop arg. Must pass a handle not the oop itself
void MacroAssembler::object_move(OopMap* map,
                                 int oop_handle_offset,
                                 int framesize_in_slots,
                                 VMRegPair src,
                                 VMRegPair dst,
                                 bool is_receiver,
                                 int* receiver_offset) {
  assert_cond(map != nullptr && receiver_offset != nullptr);

  // must pass a handle. First figure out the location we use as a handle
  Register rHandle = dst.first()->is_stack() ? t1 : dst.first()->as_Register();

  // See if oop is null if it is we need no handle

  if (src.first()->is_stack()) {
    // Oop is already on the stack as an argument
    int offset_in_older_frame = src.first()->reg2stack() + SharedRuntime::out_preserve_stack_slots();
    map->set_oop(VMRegImpl::stack2reg(offset_in_older_frame + framesize_in_slots));
    if (is_receiver) {
      *receiver_offset = (offset_in_older_frame + framesize_in_slots) * VMRegImpl::stack_slot_size;
    }

    ld(t0, Address(fp, reg2offset_in(src.first())));
    la(rHandle, Address(fp, reg2offset_in(src.first())));
    // conditionally move a null
    Label notZero1;
    bnez(t0, notZero1);
    mv(rHandle, zr);
    bind(notZero1);
  } else {

    // Oop is in a register we must store it to the space we reserve
    // on the stack for oop_handles and pass a handle if oop is non-null

    const Register rOop = src.first()->as_Register();
    int oop_slot = -1;
    if (rOop == j_rarg0) {
      oop_slot = 0;
    } else if (rOop == j_rarg1) {
      oop_slot = 1;
    } else if (rOop == j_rarg2) {
      oop_slot = 2;
    } else if (rOop == j_rarg3) {
      oop_slot = 3;
    } else if (rOop == j_rarg4) {
      oop_slot = 4;
    } else if (rOop == j_rarg5) {
      oop_slot = 5;
    } else if (rOop == j_rarg6) {
      oop_slot = 6;
    } else {
      assert(rOop == j_rarg7, "wrong register");
      oop_slot = 7;
    }

    oop_slot = oop_slot * VMRegImpl::slots_per_word + oop_handle_offset;
    int offset = oop_slot * VMRegImpl::stack_slot_size;

    map->set_oop(VMRegImpl::stack2reg(oop_slot));
    // Store oop in handle area, may be null
    sd(rOop, Address(sp, offset));
    if (is_receiver) {
      *receiver_offset = offset;
    }

    //rOop maybe the same as rHandle
    if (rOop == rHandle) {
      Label isZero;
      beqz(rOop, isZero);
      la(rHandle, Address(sp, offset));
      bind(isZero);
    } else {
      Label notZero2;
      la(rHandle, Address(sp, offset));
      bnez(rOop, notZero2);
      mv(rHandle, zr);
      bind(notZero2);
    }
  }

  // If arg is on the stack then place it otherwise it is already in correct reg.
  if (dst.first()->is_stack()) {
    sd(rHandle, Address(sp, reg2offset_out(dst.first())));
  }
}

// A float arg may have to do float reg int reg conversion
void MacroAssembler::float_move(VMRegPair src, VMRegPair dst, Register tmp) {
  assert((src.first()->is_stack() && dst.first()->is_stack()) ||
         (src.first()->is_reg() && dst.first()->is_reg()) ||
         (src.first()->is_stack() && dst.first()->is_reg()), "Unexpected error");
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      lwu(tmp, Address(fp, reg2offset_in(src.first())));
      sw(tmp, Address(sp, reg2offset_out(dst.first())));
    } else if (dst.first()->is_Register()) {
      lwu(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    } else {
      ShouldNotReachHere();
    }
  } else if (src.first() != dst.first()) {
    if (src.is_single_phys_reg() && dst.is_single_phys_reg()) {
      fmv_s(dst.first()->as_FloatRegister(), src.first()->as_FloatRegister());
    } else {
      ShouldNotReachHere();
    }
  }
}

// A long move
void MacroAssembler::long_move(VMRegPair src, VMRegPair dst, Register tmp) {
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      ld(tmp, Address(fp, reg2offset_in(src.first())));
      sd(tmp, Address(sp, reg2offset_out(dst.first())));
    } else {
      // stack to reg
      ld(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    sd(src.first()->as_Register(), Address(sp, reg2offset_out(dst.first())));
  } else {
    if (dst.first() != src.first()) {
      mv(dst.first()->as_Register(), src.first()->as_Register());
    }
  }
}

// A double move
void MacroAssembler::double_move(VMRegPair src, VMRegPair dst, Register tmp) {
  assert((src.first()->is_stack() && dst.first()->is_stack()) ||
         (src.first()->is_reg() && dst.first()->is_reg()) ||
         (src.first()->is_stack() && dst.first()->is_reg()), "Unexpected error");
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      ld(tmp, Address(fp, reg2offset_in(src.first())));
      sd(tmp, Address(sp, reg2offset_out(dst.first())));
    } else if (dst.first()-> is_Register()) {
      ld(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    } else {
      ShouldNotReachHere();
    }
  } else if (src.first() != dst.first()) {
    if (src.is_single_phys_reg() && dst.is_single_phys_reg()) {
      fmv_d(dst.first()->as_FloatRegister(), src.first()->as_FloatRegister());
    } else {
      ShouldNotReachHere();
    }
  }
}

void MacroAssembler::test_bit(Register Rd, Register Rs, uint32_t bit_pos) {
  assert(bit_pos < 64, "invalid bit range");
  if (UseZbs) {
    bexti(Rd, Rs, bit_pos);
    return;
  }
  int64_t imm = (int64_t)(1UL << bit_pos);
  if (is_simm12(imm)) {
    andi(Rd, Rs, imm);
  } else {
    srli(Rd, Rs, bit_pos);
    andi(Rd, Rd, 1);
  }
}

// Implements fast-locking.
//
//  - obj: the object to be locked
//  - tmp1, tmp2, tmp3: temporary registers, will be destroyed
//  - slow: branched to if locking fails
void MacroAssembler::fast_lock(Register basic_lock, Register obj, Register tmp1, Register tmp2, Register tmp3, Label& slow) {
  assert_different_registers(basic_lock, obj, tmp1, tmp2, tmp3, t0);

  Label push;
  const Register top = tmp1;
  const Register mark = tmp2;
  const Register t = tmp3;

  // Preload the markWord. It is important that this is the first
  // instruction emitted as it is part of C1's null check semantics.
  ld(mark, Address(obj, oopDesc::mark_offset_in_bytes()));

  if (UseObjectMonitorTable) {
    // Clear cache in case fast locking succeeds or we need to take the slow-path.
    sd(zr, Address(basic_lock, BasicObjectLock::lock_offset() + in_ByteSize((BasicLock::object_monitor_cache_offset_in_bytes()))));
  }

  if (DiagnoseSyncOnValueBasedClasses != 0) {
    load_klass(tmp1, obj);
    lbu(tmp1, Address(tmp1, Klass::misc_flags_offset()));
    test_bit(tmp1, tmp1, exact_log2(KlassFlags::_misc_is_value_based_class));
    bnez(tmp1, slow, /* is_far */ true);
  }

  // Check if the lock-stack is full.
  lwu(top, Address(xthread, JavaThread::lock_stack_top_offset()));
  mv(t, (unsigned)LockStack::end_offset());
  bge(top, t, slow, /* is_far */ true);

  // Check for recursion.
  add(t, xthread, top);
  ld(t, Address(t, -oopSize));
  beq(obj, t, push);

  // Check header for monitor (0b10).
  test_bit(t, mark, exact_log2(markWord::monitor_value));
  bnez(t, slow, /* is_far */ true);

  // Try to lock. Transition lock-bits 0b01 => 0b00
  assert(oopDesc::mark_offset_in_bytes() == 0, "required to avoid a la");
  ori(mark, mark, markWord::unlocked_value);
  xori(t, mark, markWord::unlocked_value);
  cmpxchg(/*addr*/ obj, /*expected*/ mark, /*new*/ t, Assembler::int64,
          /*acquire*/ Assembler::aq, /*release*/ Assembler::relaxed, /*result*/ t);
  bne(mark, t, slow, /* is_far */ true);

  bind(push);
  // After successful lock, push object on lock-stack.
  add(t, xthread, top);
  sd(obj, Address(t));
  addiw(top, top, oopSize);
  sw(top, Address(xthread, JavaThread::lock_stack_top_offset()));
}

// Implements ligthweight-unlocking.
//
// - obj: the object to be unlocked
// - tmp1, tmp2, tmp3: temporary registers
// - slow: branched to if unlocking fails
void MacroAssembler::fast_unlock(Register obj, Register tmp1, Register tmp2, Register tmp3, Label& slow) {
  assert_different_registers(obj, tmp1, tmp2, tmp3, t0);

#ifdef ASSERT
  {
    // Check for lock-stack underflow.
    Label stack_ok;
    lwu(tmp1, Address(xthread, JavaThread::lock_stack_top_offset()));
    mv(tmp2, (unsigned)LockStack::start_offset());
    bge(tmp1, tmp2, stack_ok);
    STOP("Lock-stack underflow");
    bind(stack_ok);
  }
#endif

  Label unlocked, push_and_slow;
  const Register top = tmp1;
  const Register mark = tmp2;
  const Register t = tmp3;

  // Check if obj is top of lock-stack.
  lwu(top, Address(xthread, JavaThread::lock_stack_top_offset()));
  subiw(top, top, oopSize);
  add(t, xthread, top);
  ld(t, Address(t));
  bne(obj, t, slow, /* is_far */ true);

  // Pop lock-stack.
  DEBUG_ONLY(add(t, xthread, top);)
  DEBUG_ONLY(sd(zr, Address(t));)
  sw(top, Address(xthread, JavaThread::lock_stack_top_offset()));

  // Check if recursive.
  add(t, xthread, top);
  ld(t, Address(t, -oopSize));
  beq(obj, t, unlocked);

  // Not recursive. Check header for monitor (0b10).
  ld(mark, Address(obj, oopDesc::mark_offset_in_bytes()));
  test_bit(t, mark, exact_log2(markWord::monitor_value));
  bnez(t, push_and_slow);

#ifdef ASSERT
  // Check header not unlocked (0b01).
  Label not_unlocked;
  test_bit(t, mark, exact_log2(markWord::unlocked_value));
  beqz(t, not_unlocked);
  stop("fast_unlock already unlocked");
  bind(not_unlocked);
#endif

  // Try to unlock. Transition lock bits 0b00 => 0b01
  assert(oopDesc::mark_offset_in_bytes() == 0, "required to avoid lea");
  ori(t, mark, markWord::unlocked_value);
  cmpxchg(/*addr*/ obj, /*expected*/ mark, /*new*/ t, Assembler::int64,
          /*acquire*/ Assembler::relaxed, /*release*/ Assembler::rl, /*result*/ t);
  beq(mark, t, unlocked);

  bind(push_and_slow);
  // Restore lock-stack and handle the unlock in runtime.
  DEBUG_ONLY(add(t, xthread, top);)
  DEBUG_ONLY(sd(obj, Address(t));)
  addiw(top, top, oopSize);
  sw(top, Address(xthread, JavaThread::lock_stack_top_offset()));
  j(slow);

  bind(unlocked);
}
