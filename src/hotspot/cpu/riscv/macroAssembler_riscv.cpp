/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, 2020, Red Hat Inc. All rights reserved.
 * Copyright (c) 2020, 2023, Huawei Technologies Co., Ltd. All rights reserved.
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

#include "precompiled.hpp"
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
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "nativeInst_riscv.hpp"
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
  CompressibleRegion cr(this);
  intptr_t before = offset();
  while ((offset() + extra_offset) % modulus != 0) { nop(); }
  return (int)(offset() - before);
}

void MacroAssembler::call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions) {
  call_VM_base(oop_result, noreg, noreg, entry_point, number_of_arguments, check_exceptions);
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
  call_VM_base(oop_result, xthread, last_java_sp, entry_point, number_of_arguments, check_exceptions);
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
  if (!Continuations::enabled()) {
    return;
  }
  relocate(post_call_nop_Relocation::spec(), [&] {
    InlineSkippedInstructionsCounter skipCounter(this);
    nop();
    li32(zr, 0);
  });
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
                                         Register last_java_pc,
                                         Register tmp) {

  if (last_java_pc->is_valid()) {
      sd(last_java_pc, Address(xthread,
                               JavaThread::frame_anchor_offset() +
                               JavaFrameAnchor::last_Java_pc_offset()));
  }

  // determine last_java_sp register
  if (last_java_sp == sp) {
    mv(tmp, sp);
    last_java_sp = tmp;
  } else if (!last_java_sp->is_valid()) {
    last_java_sp = esp;
  }

  sd(last_java_sp, Address(xthread, JavaThread::last_Java_sp_offset()));

  // last_java_fp is optional
  if (last_java_fp->is_valid()) {
    sd(last_java_fp, Address(xthread, JavaThread::last_Java_fp_offset()));
  }
}

void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         address  last_java_pc,
                                         Register tmp) {
  assert(last_java_pc != nullptr, "must provide a valid PC");

  la(tmp, last_java_pc);
  sd(tmp, Address(xthread, JavaThread::frame_anchor_offset() + JavaFrameAnchor::last_Java_pc_offset()));

  set_last_Java_frame(last_java_sp, last_java_fp, noreg, tmp);
}

void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         Label &L,
                                         Register tmp) {
  if (L.is_bound()) {
    set_last_Java_frame(last_java_sp, last_java_fp, target(L), tmp);
  } else {
    L.add_patch_at(code(), locator());
    IncompressibleRegion ir(this);  // the label address will be patched back.
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
  set_last_Java_frame(last_java_sp, fp, l, t0);

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
    RuntimeAddress target(StubRoutines::forward_exception_entry());
    relocate(target.rspec(), [&] {
      int32_t offset;
      la(t0, target.target(), offset);
      jalr(x0, t0, offset);
    });
    bind(ok);
  }

  // get oop result if there is one and reset the value in the thread
  if (oop_result->is_valid()) {
    get_vm_result(oop_result, java_thread);
  }
}

void MacroAssembler::get_vm_result(Register oop_result, Register java_thread) {
  ld(oop_result, Address(java_thread, JavaThread::vm_result_offset()));
  sd(zr, Address(java_thread, JavaThread::vm_result_offset()));
  verify_oop_msg(oop_result, "broken oop in call_VM_base");
}

void MacroAssembler::get_vm_result_2(Register metadata_result, Register java_thread) {
  ld(metadata_result, Address(java_thread, JavaThread::vm_result_2_offset()));
  sd(zr, Address(java_thread, JavaThread::vm_result_2_offset()));
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
    IncompressibleRegion ir(this);  // Fixed length
    movptr(t0, (address) b);
  }

  // call indirectly to solve generation ordering problem
  ExternalAddress target(StubRoutines::verify_oop_subroutine_entry_address());
  relocate(target.rspec(), [&] {
    int32_t offset;
    la(t1, target.target(), offset);
    ld(t1, Address(t1, offset));
  });
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
    IncompressibleRegion ir(this);  // Fixed length
    movptr(t0, (address) b);
  }

  // call indirectly to solve generation ordering problem
  ExternalAddress target(StubRoutines::verify_oop_subroutine_entry_address());
  relocate(target.rspec(), [&] {
    int32_t offset;
    la(t1, target.target(), offset);
    ld(t1, Address(t1, offset));
  });
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
  IncompressibleRegion ir(this);  // Fixed length: see CompiledDirectCall::to_interp_stub_size().
  // CompiledDirectCall::set_to_interpreted knows the
  // exact layout of this stub.

  mov_metadata(xmethod, (Metadata*)nullptr);

  // Jump to the entry point of the c2i stub.
  int32_t offset = 0;
  movptr(t0, 0, offset);
  jalr(x0, t0, offset);
}

void MacroAssembler::call_VM_leaf_base(address entry_point,
                                       int number_of_arguments,
                                       Label *retaddr) {
  push_reg(RegSet::of(t0, xmethod), sp);   // push << t0 & xmethod >> to sp
  call(entry_point);
  if (retaddr != nullptr) {
    bind(*retaddr);
  }
  pop_reg(RegSet::of(t0, xmethod), sp);   // pop << t0 & xmethod >> from sp
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
  int64_t offset = addr - pc();
  if (is_simm32(offset)) {
    auipc(Rd, (int32_t)offset + 0x800);  //0x800, Note:the 11th sign bit
    addi(Rd, Rd, ((int64_t)offset << 52) >> 52);
  } else {
    movptr(Rd, addr);
  }
}

void MacroAssembler::la(Register Rd, const address addr, int32_t &offset) {
  assert((uintptr_t)addr < (1ull << 48), "bad address");

  unsigned long target_address = (uintptr_t)addr;
  unsigned long low_address = (uintptr_t)CodeCache::low_bound();
  unsigned long high_address = (uintptr_t)CodeCache::high_bound();
  long offset_low = target_address - low_address;
  long offset_high = target_address - high_address;

  // RISC-V doesn't compute a page-aligned address, in order to partially
  // compensate for the use of *signed* offsets in its base+disp12
  // addressing mode (RISC-V's PC-relative reach remains asymmetric
  // [-(2G + 2K), 2G - 2K).
  if (offset_high >= -((1L << 31) + (1L << 11)) && offset_low < (1L << 31) - (1L << 11)) {
    int64_t distance = addr - pc();
    auipc(Rd, (int32_t)distance + 0x800);
    offset = ((int32_t)distance << 20) >> 20;
  } else {
    movptr(Rd, addr, offset);
  }
}

void MacroAssembler::la(Register Rd, const Address &adr) {
  switch (adr.getMode()) {
    case Address::literal: {
      relocInfo::relocType rtype = adr.rspec().reloc()->type();
      if (rtype == relocInfo::none) {
        mv(Rd, (intptr_t)(adr.target()));
      } else {
        relocate(adr.rspec(), [&] {
          movptr(Rd, adr.target());
        });
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
  IncompressibleRegion ir(this);   // the label address may be patched back.
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
  // use addiw to distinguish li32 to li64
  addiw(Rd, Rd, lower);
}

void MacroAssembler::li64(Register Rd, int64_t imm) {
  // Load upper 32 bits. upper = imm[63:32], but if imm[31] == 1 or
  // (imm[31:20] == 0x7ff && imm[19] == 1), upper = imm[63:32] + 1.
  int64_t lower = imm & 0xffffffff;
  lower -= ((lower << 44) >> 44);
  int64_t tmp_imm = ((uint64_t)(imm & 0xffffffff00000000)) + (uint64_t)lower;
  int32_t upper = (tmp_imm - (int32_t)lower) >> 32;

  // Load upper 32 bits
  int64_t up = upper, lo = upper;
  lo = (lo << 52) >> 52;
  up -= lo;
  up = (int32_t)up;
  lui(Rd, up);
  addi(Rd, Rd, lo);

  // Load the rest 32 bits.
  slli(Rd, Rd, 12);
  addi(Rd, Rd, (int32_t)lower >> 20);
  slli(Rd, Rd, 12);
  lower = ((int32_t)imm << 12) >> 20;
  addi(Rd, Rd, lower);
  slli(Rd, Rd, 8);
  lower = imm & 0xff;
  addi(Rd, Rd, lower);
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

#define INSN(NAME, REGISTER)                                       \
  void MacroAssembler::NAME(const address dest, Register temp) {   \
    assert_cond(dest != nullptr);                                  \
    int64_t distance = dest - pc();                                \
    if (is_simm21(distance) && ((distance % 2) == 0)) {            \
      Assembler::jal(REGISTER, distance);                          \
    } else {                                                       \
      assert(temp != noreg, "expecting a register");               \
      int32_t offset = 0;                                          \
      movptr(temp, dest, offset);                                  \
      Assembler::jalr(REGISTER, temp, offset);                     \
    }                                                              \
  }                                                                \

  INSN(j,   x0);
  INSN(jal, x1);

#undef INSN

#define INSN(NAME, REGISTER)                                       \
  void MacroAssembler::NAME(const Address &adr, Register temp) {   \
    switch (adr.getMode()) {                                       \
      case Address::literal: {                                     \
        relocate(adr.rspec(), [&] {                                \
          NAME(adr.target(), temp);                                \
        });                                                        \
        break;                                                     \
      }                                                            \
      case Address::base_plus_offset: {                            \
        int32_t offset = ((int32_t)adr.offset() << 20) >> 20;      \
        la(temp, Address(adr.base(), adr.offset() - offset));      \
        Assembler::jalr(REGISTER, temp, offset);                   \
        break;                                                     \
      }                                                            \
      default:                                                     \
        ShouldNotReachHere();                                      \
    }                                                              \
  }

  INSN(j,   x0);
  INSN(jal, x1);

#undef INSN

#define INSN(NAME)                                                                    \
  void MacroAssembler::NAME(Register Rd, const address dest, Register temp) {         \
    assert_cond(dest != nullptr);                                                     \
    int64_t distance = dest - pc();                                                   \
    if (is_simm21(distance) && ((distance % 2) == 0)) {                               \
      Assembler::NAME(Rd, distance);                                                  \
    } else {                                                                          \
      assert_different_registers(Rd, temp);                                           \
      int32_t offset = 0;                                                             \
      movptr(temp, dest, offset);                                                     \
      jalr(Rd, temp, offset);                                                         \
    }                                                                                 \
  }                                                                                   \
  void MacroAssembler::NAME(Register Rd, Label &L, Register temp) {                   \
    assert_different_registers(Rd, temp);                                             \
    wrap_label(Rd, L, temp, &MacroAssembler::NAME);                                   \
  }

  INSN(jal);

#undef INSN

#define INSN(NAME, REGISTER)                                       \
  void MacroAssembler::NAME(Label &l, Register temp) {             \
    jal(REGISTER, l, temp);                                        \
  }                                                                \

  INSN(j,   x0);
  INSN(jal, x1);

#undef INSN

void MacroAssembler::wrap_label(Register Rt, Label &L, Register tmp, load_insn_by_temp insn) {
  if (L.is_bound()) {
    (this->*insn)(Rt, target(L), tmp);
  } else {
    L.add_patch_at(code(), locator());
    (this->*insn)(Rt, pc(), tmp);
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


#define INSN(NAME, CSR)                       \
  void MacroAssembler::NAME(Register Rd) {    \
    csrr(Rd, CSR);                            \
  }

  INSN(rdinstret,  CSR_INSTRET);
  INSN(rdcycle,    CSR_CYCLE);
  INSN(rdtime,     CSR_TIME);
  INSN(frcsr,      CSR_FCSR);
  INSN(frrm,       CSR_FRM);
  INSN(frflags,    CSR_FFLAGS);

#undef INSN

void MacroAssembler::csrr(Register Rd, unsigned csr) {
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

void MacroAssembler::push_reg(Register Rs)
{
  addi(esp, esp, 0 - wordSize);
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
    addi(stack, stack, -count * wordSize - offset);
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
    addi(stack, stack, count * wordSize + offset);
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
    addi(stack, stack, -push_slots * wordSize);
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
  addi(sp, sp, - wordSize * 20);
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
  addi(sp, sp, - 32 * wordSize);
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
         "offset is too large to be patched in one jal instruction!\n");
  Assembler::patch(branch, 31, 31, (offset >> 20) & 0x1);                       // offset[20]    ==> branch[31]
  Assembler::patch(branch, 30, 21, (offset >> 1)  & 0x3ff);                     // offset[10:1]  ==> branch[30:21]
  Assembler::patch(branch, 20, 20, (offset >> 11) & 0x1);                       // offset[11]    ==> branch[20]
  Assembler::patch(branch, 19, 12, (offset >> 12) & 0xff);                      // offset[19:12] ==> branch[19:12]
  return NativeInstruction::instruction_size;                                   // only one instruction
}

static int patch_offset_in_conditional_branch(address branch, int64_t offset) {
  assert(Assembler::is_simm13(offset) && ((offset % 2) == 0),
         "offset is too large to be patched in one beq/bge/bgeu/blt/bltu/bne instruction!\n");
  Assembler::patch(branch, 31, 31, (offset >> 12) & 0x1);                       // offset[12]    ==> branch[31]
  Assembler::patch(branch, 30, 25, (offset >> 5)  & 0x3f);                      // offset[10:5]  ==> branch[30:25]
  Assembler::patch(branch, 7,  7,  (offset >> 11) & 0x1);                       // offset[11]    ==> branch[7]
  Assembler::patch(branch, 11, 8,  (offset >> 1)  & 0xf);                       // offset[4:1]   ==> branch[11:8]
  return NativeInstruction::instruction_size;                                   // only one instruction
}

static int patch_offset_in_pc_relative(address branch, int64_t offset) {
  const int PC_RELATIVE_INSTRUCTION_NUM = 2;                                    // auipc, addi/jalr/load
  Assembler::patch(branch, 31, 12, ((offset + 0x800) >> 12) & 0xfffff);         // Auipc.          offset[31:12]  ==> branch[31:12]
  Assembler::patch(branch + 4, 31, 20, offset & 0xfff);                         // Addi/Jalr/Load. offset[11:0]   ==> branch[31:20]
  return PC_RELATIVE_INSTRUCTION_NUM * NativeInstruction::instruction_size;
}

static int patch_addr_in_movptr(address branch, address target) {
  const int MOVPTR_INSTRUCTIONS_NUM = 6;                                        // lui + addi + slli + addi + slli + addi/jalr/load
  int32_t lower = ((intptr_t)target << 35) >> 35;
  int64_t upper = ((intptr_t)target - lower) >> 29;
  Assembler::patch(branch + 0,  31, 12, upper & 0xfffff);                       // Lui.             target[48:29] + target[28] ==> branch[31:12]
  Assembler::patch(branch + 4,  31, 20, (lower >> 17) & 0xfff);                 // Addi.            target[28:17] ==> branch[31:20]
  Assembler::patch(branch + 12, 31, 20, (lower >> 6) & 0x7ff);                  // Addi.            target[16: 6] ==> branch[31:20]
  Assembler::patch(branch + 20, 31, 20, lower & 0x3f);                          // Addi/Jalr/Load.  target[ 5: 0] ==> branch[31:20]
  return MOVPTR_INSTRUCTIONS_NUM * NativeInstruction::instruction_size;
}

static int patch_imm_in_li64(address branch, address target) {
  const int LI64_INSTRUCTIONS_NUM = 8;                                          // lui + addi + slli + addi + slli + addi + slli + addi
  int64_t lower = (intptr_t)target & 0xffffffff;
  lower = lower - ((lower << 44) >> 44);
  int64_t tmp_imm = ((uint64_t)((intptr_t)target & 0xffffffff00000000)) + (uint64_t)lower;
  int32_t upper =  (tmp_imm - (int32_t)lower) >> 32;
  int64_t tmp_upper = upper, tmp_lower = upper;
  tmp_lower = (tmp_lower << 52) >> 52;
  tmp_upper -= tmp_lower;
  tmp_upper >>= 12;
  // Load upper 32 bits. Upper = target[63:32], but if target[31] = 1 or (target[31:20] == 0x7ff && target[19] == 1),
  // upper = target[63:32] + 1.
  Assembler::patch(branch + 0,  31, 12, tmp_upper & 0xfffff);                       // Lui.
  Assembler::patch(branch + 4,  31, 20, tmp_lower & 0xfff);                         // Addi.
  // Load the rest 32 bits.
  Assembler::patch(branch + 12, 31, 20, ((int32_t)lower >> 20) & 0xfff);            // Addi.
  Assembler::patch(branch + 20, 31, 20, (((intptr_t)target << 44) >> 52) & 0xfff);  // Addi.
  Assembler::patch(branch + 28, 31, 20, (intptr_t)target & 0xff);                   // Addi.
  return LI64_INSTRUCTIONS_NUM * NativeInstruction::instruction_size;
}

static int patch_imm_in_li16u(address branch, uint16_t target) {
  Assembler::patch(branch, 31, 12, target); // patch lui only
  return NativeInstruction::instruction_size;
}

int MacroAssembler::patch_imm_in_li32(address branch, int32_t target) {
  const int LI32_INSTRUCTIONS_NUM = 2;                                          // lui + addiw
  int64_t upper = (intptr_t)target;
  int32_t lower = (((int32_t)target) << 20) >> 20;
  upper -= lower;
  upper = (int32_t)upper;
  Assembler::patch(branch + 0,  31, 12, (upper >> 12) & 0xfffff);               // Lui.
  Assembler::patch(branch + 4,  31, 20, lower & 0xfff);                         // Addiw.
  return LI32_INSTRUCTIONS_NUM * NativeInstruction::instruction_size;
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

static address get_target_of_movptr(address insn_addr) {
  assert_cond(insn_addr != nullptr);
  intptr_t target_address = (((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr), 31, 12)) & 0xfffff) << 29; // Lui.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 4), 31, 20)) << 17;                 // Addi.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 12), 31, 20)) << 6;                 // Addi.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 20), 31, 20));                      // Addi/Jalr/Load.
  return (address) target_address;
}

static address get_target_of_li64(address insn_addr) {
  assert_cond(insn_addr != nullptr);
  intptr_t target_address = (((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr), 31, 12)) & 0xfffff) << 44; // Lui.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 4), 31, 20)) << 32;                 // Addi.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 12), 31, 20)) << 20;                // Addi.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 20), 31, 20)) << 8;                 // Addi.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 28), 31, 20));                      // Addi.
  return (address)target_address;
}

address MacroAssembler::get_target_of_li32(address insn_addr) {
  assert_cond(insn_addr != nullptr);
  intptr_t target_address = (((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr), 31, 12)) & 0xfffff) << 12; // Lui.
  target_address += ((int64_t)Assembler::sextract(Assembler::ld_instr(insn_addr + 4), 31, 20));                       // Addiw.
  return (address)target_address;
}

// Patch any kind of instruction; there may be several instructions.
// Return the total length (in bytes) of the instructions.
int MacroAssembler::pd_patch_instruction_size(address branch, address target) {
  assert_cond(branch != nullptr);
  int64_t offset = target - branch;
  if (NativeInstruction::is_jal_at(branch)) {                         // jal
    return patch_offset_in_jal(branch, offset);
  } else if (NativeInstruction::is_branch_at(branch)) {               // beq/bge/bgeu/blt/bltu/bne
    return patch_offset_in_conditional_branch(branch, offset);
  } else if (NativeInstruction::is_pc_relative_at(branch)) {          // auipc, addi/jalr/load
    return patch_offset_in_pc_relative(branch, offset);
  } else if (NativeInstruction::is_movptr_at(branch)) {               // movptr
    return patch_addr_in_movptr(branch, target);
  } else if (NativeInstruction::is_li64_at(branch)) {                 // li64
    return patch_imm_in_li64(branch, target);
  } else if (NativeInstruction::is_li32_at(branch)) {                 // li32
    int64_t imm = (intptr_t)target;
    return patch_imm_in_li32(branch, (int32_t)imm);
  } else if (NativeInstruction::is_li16u_at(branch)) {
    int64_t imm = (intptr_t)target;
    return patch_imm_in_li16u(branch, (uint16_t)imm);
  } else {
#ifdef ASSERT
    tty->print_cr("pd_patch_instruction_size: instruction 0x%x at " INTPTR_FORMAT " could not be patched!\n",
                  Assembler::ld_instr(branch), p2i(branch));
    Disassembler::decode(branch - 16, branch + 16);
#endif
    ShouldNotReachHere();
    return -1;
  }
}

address MacroAssembler::target_addr_for_insn(address insn_addr) {
  long offset = 0;
  assert_cond(insn_addr != nullptr);
  if (NativeInstruction::is_jal_at(insn_addr)) {                     // jal
    offset = get_offset_of_jal(insn_addr);
  } else if (NativeInstruction::is_branch_at(insn_addr)) {           // beq/bge/bgeu/blt/bltu/bne
    offset = get_offset_of_conditional_branch(insn_addr);
  } else if (NativeInstruction::is_pc_relative_at(insn_addr)) {      // auipc, addi/jalr/load
    offset = get_offset_of_pc_relative(insn_addr);
  } else if (NativeInstruction::is_movptr_at(insn_addr)) {           // movptr
    return get_target_of_movptr(insn_addr);
  } else if (NativeInstruction::is_li64_at(insn_addr)) {             // li64
    return get_target_of_li64(insn_addr);
  } else if (NativeInstruction::is_li32_at(insn_addr)) {             // li32
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
  if (NativeInstruction::is_li32_at(insn_addr)) {
    // Move narrow OOP
    uint32_t n = CompressedOops::narrow_oop_value(cast_to_oop(o));
    return patch_imm_in_li32(insn_addr, (int32_t)n);
  } else if (NativeInstruction::is_movptr_at(insn_addr)) {
    // Move wide OOP
    return patch_addr_in_movptr(insn_addr, o);
  }
  ShouldNotReachHere();
  return -1;
}

void MacroAssembler::reinit_heapbase() {
  if (UseCompressedOops) {
    if (Universe::is_fully_initialized()) {
      mv(xheapbase, CompressedOops::ptrs_base());
    } else {
      ExternalAddress target(CompressedOops::ptrs_base_addr());
      relocate(target.rspec(), [&] {
        int32_t offset;
        la(xheapbase, target.target(), offset);
        ld(xheapbase, Address(xheapbase, offset));
      });
    }
  }
}

void MacroAssembler::movptr(Register Rd, address addr, int32_t &offset) {
  int64_t imm64 = (int64_t)addr;
#ifndef PRODUCT
  {
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "0x%" PRIx64, imm64);
    block_comment(buffer);
  }
#endif
  assert((uintptr_t)imm64 < (1ull << 48), "48-bit overflow in address constant");
  // Load upper 31 bits
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

void MacroAssembler::add(Register Rd, Register Rn, int64_t increment, Register temp) {
  if (is_simm12(increment)) {
    addi(Rd, Rn, increment);
  } else {
    assert_different_registers(Rn, temp);
    li(temp, increment);
    add(Rd, Rn, temp);
  }
}

void MacroAssembler::addw(Register Rd, Register Rn, int32_t increment, Register temp) {
  if (is_simm12(increment)) {
    addiw(Rd, Rn, increment);
  } else {
    assert_different_registers(Rn, temp);
    li(temp, increment);
    addw(Rd, Rn, temp);
  }
}

void MacroAssembler::sub(Register Rd, Register Rn, int64_t decrement, Register temp) {
  if (is_simm12(-decrement)) {
    addi(Rd, Rn, -decrement);
  } else {
    assert_different_registers(Rn, temp);
    li(temp, decrement);
    sub(Rd, Rn, temp);
  }
}

void MacroAssembler::subw(Register Rd, Register Rn, int32_t decrement, Register temp) {
  if (is_simm12(-decrement)) {
    addiw(Rd, Rn, -decrement);
  } else {
    assert_different_registers(Rn, temp);
    li(temp, decrement);
    subw(Rd, Rn, temp);
  }
}

void MacroAssembler::andrw(Register Rd, Register Rs1, Register Rs2) {
  andr(Rd, Rs1, Rs2);
  sign_extend(Rd, Rd, 32);
}

void MacroAssembler::orrw(Register Rd, Register Rs1, Register Rs2) {
  orr(Rd, Rs1, Rs2);
  sign_extend(Rd, Rd, 32);
}

void MacroAssembler::xorrw(Register Rd, Register Rs1, Register Rs2) {
  xorr(Rd, Rs1, Rs2);
  sign_extend(Rd, Rd, 32);
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


// reverse bytes in halfword in lower 16 bits and sign-extend
// Rd[15:0] = Rs[7:0] Rs[15:8] (sign-extend to 64 bits)
void MacroAssembler::revb_h_h(Register Rd, Register Rs, Register tmp) {
  if (UseZbb) {
    rev8(Rd, Rs);
    srai(Rd, Rd, 48);
    return;
  }
  assert_different_registers(Rs, tmp);
  assert_different_registers(Rd, tmp);
  srli(tmp, Rs, 8);
  andi(tmp, tmp, 0xFF);
  slli(Rd, Rs, 56);
  srai(Rd, Rd, 48); // sign-extend
  orr(Rd, Rd, tmp);
}

// reverse bytes in lower word and sign-extend
// Rd[31:0] = Rs[7:0] Rs[15:8] Rs[23:16] Rs[31:24] (sign-extend to 64 bits)
void MacroAssembler::revb_w_w(Register Rd, Register Rs, Register tmp1, Register tmp2) {
  if (UseZbb) {
    rev8(Rd, Rs);
    srai(Rd, Rd, 32);
    return;
  }
  assert_different_registers(Rs, tmp1, tmp2);
  assert_different_registers(Rd, tmp1, tmp2);
  revb_h_w_u(Rd, Rs, tmp1, tmp2);
  slli(tmp2, Rd, 48);
  srai(tmp2, tmp2, 32); // sign-extend
  srli(Rd, Rd, 16);
  orr(Rd, Rd, tmp2);
}

// reverse bytes in halfword in lower 16 bits and zero-extend
// Rd[15:0] = Rs[7:0] Rs[15:8] (zero-extend to 64 bits)
void MacroAssembler::revb_h_h_u(Register Rd, Register Rs, Register tmp) {
  if (UseZbb) {
    rev8(Rd, Rs);
    srli(Rd, Rd, 48);
    return;
  }
  assert_different_registers(Rs, tmp);
  assert_different_registers(Rd, tmp);
  srli(tmp, Rs, 8);
  andi(tmp, tmp, 0xFF);
  andi(Rd, Rs, 0xFF);
  slli(Rd, Rd, 8);
  orr(Rd, Rd, tmp);
}

// reverse bytes in halfwords in lower 32 bits and zero-extend
// Rd[31:0] = Rs[23:16] Rs[31:24] Rs[7:0] Rs[15:8] (zero-extend to 64 bits)
void MacroAssembler::revb_h_w_u(Register Rd, Register Rs, Register tmp1, Register tmp2) {
  if (UseZbb) {
    rev8(Rd, Rs);
    rori(Rd, Rd, 32);
    roriw(Rd, Rd, 16);
    zero_extend(Rd, Rd, 32);
    return;
  }
  assert_different_registers(Rs, tmp1, tmp2);
  assert_different_registers(Rd, tmp1, tmp2);
  srli(tmp2, Rs, 16);
  revb_h_h_u(tmp2, tmp2, tmp1);
  revb_h_h_u(Rd, Rs, tmp1);
  slli(tmp2, tmp2, 16);
  orr(Rd, Rd, tmp2);
}

// This method is only used for revb_h
// Rd = Rs[47:0] Rs[55:48] Rs[63:56]
void MacroAssembler::revb_h_helper(Register Rd, Register Rs, Register tmp1, Register tmp2) {
  assert_different_registers(Rs, tmp1, tmp2);
  assert_different_registers(Rd, tmp1);
  srli(tmp1, Rs, 48);
  andi(tmp2, tmp1, 0xFF);
  slli(tmp2, tmp2, 8);
  srli(tmp1, tmp1, 8);
  orr(tmp1, tmp1, tmp2);
  slli(Rd, Rs, 16);
  orr(Rd, Rd, tmp1);
}

// reverse bytes in each halfword
// Rd[63:0] = Rs[55:48] Rs[63:56] Rs[39:32] Rs[47:40] Rs[23:16] Rs[31:24] Rs[7:0] Rs[15:8]
void MacroAssembler::revb_h(Register Rd, Register Rs, Register tmp1, Register tmp2) {
  if (UseZbb) {
    assert_different_registers(Rs, tmp1);
    assert_different_registers(Rd, tmp1);
    rev8(Rd, Rs);
    zero_extend(tmp1, Rd, 32);
    roriw(tmp1, tmp1, 16);
    slli(tmp1, tmp1, 32);
    srli(Rd, Rd, 32);
    roriw(Rd, Rd, 16);
    zero_extend(Rd, Rd, 32);
    orr(Rd, Rd, tmp1);
    return;
  }
  assert_different_registers(Rs, tmp1, tmp2);
  assert_different_registers(Rd, tmp1, tmp2);
  revb_h_helper(Rd, Rs, tmp1, tmp2);
  for (int i = 0; i < 3; ++i) {
    revb_h_helper(Rd, Rd, tmp1, tmp2);
  }
}

// reverse bytes in each word
// Rd[63:0] = Rs[39:32] Rs[47:40] Rs[55:48] Rs[63:56] Rs[7:0] Rs[15:8] Rs[23:16] Rs[31:24]
void MacroAssembler::revb_w(Register Rd, Register Rs, Register tmp1, Register tmp2) {
  if (UseZbb) {
    rev8(Rd, Rs);
    rori(Rd, Rd, 32);
    return;
  }
  assert_different_registers(Rs, tmp1, tmp2);
  assert_different_registers(Rd, tmp1, tmp2);
  revb(Rd, Rs, tmp1, tmp2);
  ror_imm(Rd, Rd, 32);
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
  andi(tmp1, Rs, 0xFF);
  slli(tmp1, tmp1, 8);
  for (int step = 8; step < 56; step += 8) {
    srli(tmp2, Rs, step);
    andi(tmp2, tmp2, 0xFF);
    orr(tmp1, tmp1, tmp2);
    slli(tmp1, tmp1, 8);
  }
  srli(Rd, Rs, 56);
  andi(Rd, Rd, 0xFF);
  orr(Rd, tmp1, Rd);
}

// rotate right with shift bits
void MacroAssembler::ror_imm(Register dst, Register src, uint32_t shift, Register tmp)
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
void MacroAssembler::rolw_imm(Register dst, Register src, uint32_t shift, Register tmp) {
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

void MacroAssembler::andi(Register Rd, Register Rn, int64_t imm, Register tmp) {
  if (is_simm12(imm)) {
    and_imm12(Rd, Rn, imm);
  } else {
    assert_different_registers(Rn, tmp);
    mv(tmp, imm);
    andr(Rd, Rn, tmp);
  }
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

void MacroAssembler::cmp_klass(Register oop, Register trial_klass, Register tmp1, Register tmp2, Label &L) {
  assert_different_registers(oop, trial_klass, tmp1, tmp2);
  if (UseCompressedClassPointers) {
    lwu(tmp1, Address(oop, oopDesc::klass_offset_in_bytes()));
    if (CompressedKlassPointers::base() == nullptr) {
      slli(tmp1, tmp1, CompressedKlassPointers::shift());
      beq(trial_klass, tmp1, L);
      return;
    }
    decode_klass_not_null(tmp1, tmp2);
  } else {
    ld(tmp1, Address(oop, oopDesc::klass_offset_in_bytes()));
  }
  beq(trial_klass, tmp1, L);
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
    mv(dst, Address((address)obj, rspec));
  } else {
    address dummy = address(uintptr_t(pc()) & -wordSize); // A nearby aligned address
    ld_constant(dst, Address(dummy, rspec));
  }
}

// Move a metadata address into a register.
void MacroAssembler::mov_metadata(Register dst, Metadata* obj) {
  int oop_index;
  if (obj == nullptr) {
    oop_index = oop_recorder()->allocate_metadata_index(obj);
  } else {
    oop_index = oop_recorder()->find_index(obj);
  }
  RelocationHolder rspec = metadata_Relocation::spec(oop_index);
  mv(dst, Address((address)obj, rspec));
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

SkipIfEqual::SkipIfEqual(MacroAssembler* masm, const bool* flag_addr, bool value) {
  int32_t offset = 0;
  _masm = masm;
  ExternalAddress target((address)flag_addr);
  _masm->relocate(target.rspec(), [&] {
    int32_t offset;
    _masm->la(t0, target.target(), offset);
    _masm->lbu(t0, Address(t0, offset));
  });
  if (value) {
    _masm->bnez(t0, _label);
  } else {
    _masm->beqz(t0, _label);
  }
}

SkipIfEqual::~SkipIfEqual() {
  _masm->bind(_label);
  _masm = nullptr;
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

void MacroAssembler::load_klass(Register dst, Register src, Register tmp) {
  assert_different_registers(dst, tmp);
  assert_different_registers(src, tmp);
  if (UseCompressedClassPointers) {
    lwu(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    decode_klass_not_null(dst, tmp);
  } else {
    ld(dst, Address(src, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::store_klass(Register dst, Register src, Register tmp) {
  // FIXME: Should this be a store release? concurrent gcs assumes
  // klass length is valid if klass field is not null.
  if (UseCompressedClassPointers) {
    encode_klass_not_null(src, tmp);
    sw(src, Address(dst, oopDesc::klass_offset_in_bytes()));
  } else {
    sd(src, Address(dst, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::store_klass_gap(Register dst, Register src) {
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

  if (CompressedKlassPointers::base() == nullptr) {
    if (CompressedKlassPointers::shift() != 0) {
      assert(LogKlassAlignmentInBytes == CompressedKlassPointers::shift(), "decode alg wrong");
      slli(dst, src, LogKlassAlignmentInBytes);
    } else {
      mv(dst, src);
    }
    return;
  }

  Register xbase = dst;
  if (dst == src) {
    xbase = tmp;
  }

  assert_different_registers(src, xbase);
  mv(xbase, (uintptr_t)CompressedKlassPointers::base());

  if (CompressedKlassPointers::shift() != 0) {
    assert(LogKlassAlignmentInBytes == CompressedKlassPointers::shift(), "decode alg wrong");
    assert_different_registers(t0, xbase);
    shadd(dst, src, xbase, t0, LogKlassAlignmentInBytes);
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
      assert(LogKlassAlignmentInBytes == CompressedKlassPointers::shift(), "decode alg wrong");
      srli(dst, src, LogKlassAlignmentInBytes);
    } else {
      mv(dst, src);
    }
    return;
  }

  if (((uint64_t)CompressedKlassPointers::base() & 0xffffffff) == 0 &&
      CompressedKlassPointers::shift() == 0) {
    zero_extend(dst, src, 32);
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
    assert(LogKlassAlignmentInBytes == CompressedKlassPointers::shift(), "decode alg wrong");
    srli(dst, dst, LogKlassAlignmentInBytes);
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

int MacroAssembler::corrected_idivl(Register result, Register rs1, Register rs2,
                                    bool want_remainder, bool is_signed)
{
  // Full implementation of Java idiv and irem.  The function
  // returns the (pc) offset of the div instruction - may be needed
  // for implicit exceptions.
  //
  // input : rs1: dividend
  //         rs2: divisor
  //
  // result: either
  //         quotient  (= rs1 idiv rs2)
  //         remainder (= rs1 irem rs2)


  int idivl_offset = offset();
  if (!want_remainder) {
    if (is_signed) {
      divw(result, rs1, rs2);
    } else {
      divuw(result, rs1, rs2);
    }
  } else {
    // result = rs1 % rs2;
    if (is_signed) {
      remw(result, rs1, rs2);
    } else {
      remuw(result, rs1, rs2);
    }
  }
  return idivl_offset;
}

int MacroAssembler::corrected_idivq(Register result, Register rs1, Register rs2,
                                    bool want_remainder, bool is_signed)
{
  // Full implementation of Java ldiv and lrem.  The function
  // returns the (pc) offset of the div instruction - may be needed
  // for implicit exceptions.
  //
  // input : rs1: dividend
  //         rs2: divisor
  //
  // result: either
  //         quotient  (= rs1 idiv rs2)
  //         remainder (= rs1 irem rs2)

  int idivq_offset = offset();
  if (!want_remainder) {
    if (is_signed) {
      div(result, rs1, rs2);
    } else {
      divu(result, rs1, rs2);
    }
  } else {
    // result = rs1 % rs2;
    if (is_signed) {
      rem(result, rs1, rs2);
    } else {
      remu(result, rs1, rs2);
    }
  }
  return idivq_offset;
}

// Look up the method for a megamorpic invkkeinterface call.
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
         "recv_klass can be destroyed when mehtid isn't needed");
  assert(itable_index.is_constant() || itable_index.as_register() == method_result,
         "caller must be same register for non-constant itable index as for method");

  // Compute start of first itableOffsetEntry (which is at the end of the vtable).
  int vtable_base = in_bytes(Klass::vtable_start_offset());
  int itentry_off = in_bytes(itableMethodEntry::method_offset());
  int scan_step   = itableOffsetEntry::size() * wordSize;
  int vte_size    = vtableEntry::size_in_bytes();
  assert(vte_size == wordSize, "else adjust times_vte_scale");

  lwu(scan_tmp, Address(recv_klass, Klass::vtable_length_offset()));

  // %%% Could store the aligned, prescaled offset in the klassoop.
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
  address prev = pc() - NativeMembar::instruction_size;
  address last = code()->last_insn();

  if (last != nullptr && nativeInstruction_at(last)->is_membar() && prev == last) {
    NativeMembar *bar = NativeMembar_at(prev);
    // We are merging two memory barrier instructions.  On RISCV we
    // can do this simply by ORing them together.
    bar->set_kind(bar->get_kind() | order_constraint);
    BLOCK_COMMENT("merged membar");
  } else {
    code()->set_last_insn(pc());

    uint32_t predecessor = 0;
    uint32_t successor = 0;

    membar_mask_to_pred_succ(order_constraint, predecessor, successor);
    fence(predecessor, successor);
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

void MacroAssembler::safepoint_poll(Label& slow_path, bool at_return, bool acquire, bool in_nmethod) {
  ld(t0, Address(xthread, JavaThread::polling_word_offset()));
  if (acquire) {
    membar(MacroAssembler::LoadLoad | MacroAssembler::LoadStore);
  }
  if (at_return) {
    bgtu(in_nmethod ? sp : fp, t0, slow_path, /* is_far */ true);
  } else {
    test_bit(t0, t0, exact_log2(SafepointMechanism::poll_bit()));
    bnez(t0, slow_path, true /* is_far */);
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
                                   enum operand_size size,
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
      zero_extend(dst, dst, 32);
      break;
    default:
      ShouldNotReachHere();
  }
}

void MacroAssembler::store_conditional(Register dst,
                                       Register new_val,
                                       Register addr,
                                       enum operand_size size,
                                       Assembler::Aqrl release) {
  switch (size) {
    case int64:
      sc_d(dst, new_val, addr, release);
      break;
    case int32:
    case uint32:
      sc_w(dst, new_val, addr, release);
      break;
    default:
      ShouldNotReachHere();
  }
}


void MacroAssembler::cmpxchg_narrow_value_helper(Register addr, Register expected,
                                                 Register new_val,
                                                 enum operand_size size,
                                                 Register tmp1, Register tmp2, Register tmp3) {
  assert(size == int8 || size == int16, "unsupported operand size");

  Register aligned_addr = t1, shift = tmp1, mask = tmp2, not_mask = tmp3;

  andi(shift, addr, 3);
  slli(shift, shift, 3);

  andi(aligned_addr, addr, ~3);

  if (size == int8) {
    mv(mask, 0xff);
  } else {
    // size == int16 case
    mv(mask, -1);
    zero_extend(mask, mask, 16);
  }
  sll(mask, mask, shift);

  notr(not_mask, mask);

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
                                          enum operand_size size,
                                          Assembler::Aqrl acquire, Assembler::Aqrl release,
                                          Register result, bool result_as_bool,
                                          Register tmp1, Register tmp2, Register tmp3) {
  Register aligned_addr = t1, shift = tmp1, mask = tmp2, not_mask = tmp3, old = result, tmp = t0;
  assert_different_registers(addr, old, mask, not_mask, new_val, expected, shift, tmp);
  cmpxchg_narrow_value_helper(addr, expected, new_val, size, tmp1, tmp2, tmp3);

  Label retry, fail, done;

  bind(retry);

  if (UseZacas) {
    lw(old, aligned_addr);

    // if old & mask != expected
    andr(tmp, old, mask);
    bne(tmp, expected, fail);

    andr(tmp, old, not_mask);
    orr(tmp, tmp, new_val);

    atomic_cas(old, tmp, aligned_addr, operand_size::int32, acquire, release);
    bne(tmp, old, retry);
  } else {
    lr_w(old, aligned_addr, acquire);
    andr(tmp, old, mask);
    bne(tmp, expected, fail);

    andr(tmp, old, not_mask);
    orr(tmp, tmp, new_val);
    sc_w(tmp, tmp, aligned_addr, release);
    bnez(tmp, retry);
  }

  if (result_as_bool) {
    mv(result, 1);
    j(done);

    bind(fail);
    mv(result, zr);

    bind(done);
  } else {
    andr(tmp, old, mask);

    bind(fail);
    srl(result, tmp, shift);

    if (size == int8) {
      sign_extend(result, result, 8);
    } else {
      // size == int16 case
      sign_extend(result, result, 16);
    }
  }
}

// weak_cmpxchg_narrow_value is a weak version of cmpxchg_narrow_value, to implement
// the weak CAS stuff. The major difference is that it just failed when store conditional
// failed.
void MacroAssembler::weak_cmpxchg_narrow_value(Register addr, Register expected,
                                               Register new_val,
                                               enum operand_size size,
                                               Assembler::Aqrl acquire, Assembler::Aqrl release,
                                               Register result,
                                               Register tmp1, Register tmp2, Register tmp3) {
  Register aligned_addr = t1, shift = tmp1, mask = tmp2, not_mask = tmp3, old = result, tmp = t0;
  assert_different_registers(addr, old, mask, not_mask, new_val, expected, shift, tmp);
  cmpxchg_narrow_value_helper(addr, expected, new_val, size, tmp1, tmp2, tmp3);

  Label fail, done;

  if (UseZacas) {
    lw(old, aligned_addr);

    // if old & mask != expected
    andr(tmp, old, mask);
    bne(tmp, expected, fail);

    andr(tmp, old, not_mask);
    orr(tmp, tmp, new_val);

    atomic_cas(tmp, new_val, addr, operand_size::int32, acquire, release);
    bne(tmp, old, fail);
  } else {
    lr_w(old, aligned_addr, acquire);
    andr(tmp, old, mask);
    bne(tmp, expected, fail);

    andr(tmp, old, not_mask);
    orr(tmp, tmp, new_val);
    sc_w(tmp, tmp, aligned_addr, release);
    bnez(tmp, fail);
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
                             enum operand_size size,
                             Assembler::Aqrl acquire, Assembler::Aqrl release,
                             Register result, bool result_as_bool) {
  assert(size != int8 && size != int16, "unsupported operand size");
  assert_different_registers(addr, t0);
  assert_different_registers(expected, t0);
  assert_different_registers(new_val, t0);

  if (UseZacas) {
    if (result_as_bool) {
      mv(t0, expected);
      atomic_cas(t0, new_val, addr, size, acquire, release);
      xorr(t0, t0, expected);
      seqz(result, t0);
    } else {
      mv(result, expected);
      atomic_cas(result, new_val, addr, size, acquire, release);
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

void MacroAssembler::cmpxchg_weak(Register addr, Register expected,
                                  Register new_val,
                                  enum operand_size size,
                                  Assembler::Aqrl acquire, Assembler::Aqrl release,
                                  Register result) {
  if (UseZacas) {
    cmpxchg(addr, expected, new_val, size, acquire, release, result, true);
    return;
  }

  assert_different_registers(addr, t0);
  assert_different_registers(expected, t0);
  assert_different_registers(new_val, t0);

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
  zero_extend(prev, prev, 32);                                                       \
  return;                                                                            \
}

ATOMIC_XCHGU(xchgwu, xchgw)
ATOMIC_XCHGU(xchgalwu, xchgalw)

#undef ATOMIC_XCHGU

#define ATOMIC_CAS(OP, AOP, ACQUIRE, RELEASE)                                        \
void MacroAssembler::atomic_##OP(Register prev, Register newv, Register addr) {      \
  assert(UseZacas, "invariant");                                                     \
  prev = prev->is_valid() ? prev : zr;                                               \
  AOP(prev, addr, newv, (Assembler::Aqrl)(ACQUIRE | RELEASE));                       \
  return;                                                                            \
}

ATOMIC_CAS(cas, amocas_d, Assembler::relaxed, Assembler::relaxed)
ATOMIC_CAS(casw, amocas_w, Assembler::relaxed, Assembler::relaxed)
ATOMIC_CAS(casl, amocas_d, Assembler::relaxed, Assembler::rl)
ATOMIC_CAS(caslw, amocas_w, Assembler::relaxed, Assembler::rl)
ATOMIC_CAS(casal, amocas_d, Assembler::aq, Assembler::rl)
ATOMIC_CAS(casalw, amocas_w, Assembler::aq, Assembler::rl)

#undef ATOMIC_CAS

#define ATOMIC_CASU(OP1, OP2)                                                        \
void MacroAssembler::atomic_##OP1(Register prev, Register newv, Register addr) {     \
  atomic_##OP2(prev, newv, addr);                                                    \
  zero_extend(prev, prev, 32);                                                       \
  return;                                                                            \
}

ATOMIC_CASU(caswu, casw)
ATOMIC_CASU(caslwu, caslw)
ATOMIC_CASU(casalwu, casalw)

#undef ATOMIC_CASU

void MacroAssembler::atomic_cas(
    Register prev, Register newv, Register addr, enum operand_size size, Assembler::Aqrl acquire, Assembler::Aqrl release) {
  switch (size) {
    case int64:
      switch ((Assembler::Aqrl)(acquire | release)) {
        case Assembler::relaxed:
          atomic_cas(prev, newv, addr);
          break;
        case Assembler::rl:
          atomic_casl(prev, newv, addr);
          break;
        case Assembler::aqrl:
          atomic_casal(prev, newv, addr);
          break;
        default:
          ShouldNotReachHere();
      }
      break;
    case int32:
      switch ((Assembler::Aqrl)(acquire | release)) {
        case Assembler::relaxed:
          atomic_casw(prev, newv, addr);
          break;
        case Assembler::rl:
          atomic_caslw(prev, newv, addr);
          break;
        case Assembler::aqrl:
          atomic_casalw(prev, newv, addr);
          break;
        default:
          ShouldNotReachHere();
      }
      break;
    case uint32:
      switch ((Assembler::Aqrl)(acquire | release)) {
        case Assembler::relaxed:
          atomic_caswu(prev, newv, addr);
          break;
        case Assembler::rl:
          atomic_caslwu(prev, newv, addr);
          break;
        case Assembler::aqrl:
          atomic_casalwu(prev, newv, addr);
          break;
        default:
          ShouldNotReachHere();
      }
      break;
    default:
      ShouldNotReachHere();
  }
}

void MacroAssembler::far_jump(const Address &entry, Register tmp) {
  assert(ReservedCodeCacheSize < 4*G, "branch out of range");
  assert(CodeCache::find_blob(entry.target()) != nullptr,
         "destination of far call not found in code cache");
  assert(entry.rspec().type() == relocInfo::external_word_type
        || entry.rspec().type() == relocInfo::runtime_call_type
        || entry.rspec().type() == relocInfo::none, "wrong entry relocInfo type");
  // Fixed length: see MacroAssembler::far_branch_size()
  relocate(entry.rspec(), [&] {
    int32_t offset;
    la(tmp, entry.target(), offset);
    jalr(x0, tmp, offset);
  });
}

void MacroAssembler::far_call(const Address &entry, Register tmp) {
  assert(ReservedCodeCacheSize < 4*G, "branch out of range");
  assert(CodeCache::find_blob(entry.target()) != nullptr,
         "destination of far call not found in code cache");
  assert(entry.rspec().type() == relocInfo::external_word_type
        || entry.rspec().type() == relocInfo::runtime_call_type
        || entry.rspec().type() == relocInfo::none, "wrong entry relocInfo type");
  // Fixed length: see MacroAssembler::far_branch_size()
  // We can use auipc + jalr here because we know that the total size of
  // the code cache cannot exceed 2Gb.
  relocate(entry.rspec(), [&] {
    int32_t offset;
    la(tmp, entry.target(), offset);
    jalr(x1, tmp, offset); // link
  });
}

void MacroAssembler::check_klass_subtype_fast_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register tmp_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   Label* L_slow_path,
                                                   Register super_check_offset) {
  assert_different_registers(sub_klass, super_klass, tmp_reg);
  bool must_load_sco = (super_check_offset == noreg);
  if (must_load_sco) {
    assert(tmp_reg != noreg, "supply either a temp or a register offset");
  } else {
    assert_different_registers(sub_klass, super_klass, super_check_offset);
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

  beq(super_klass, t0, *L_success);
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
  add(addr, addr, wordSize);
  sub(count, count, 1);
  bnez(count, Lloop);
  bind(Lexit);
}

void MacroAssembler::check_klass_subtype_slow_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register tmp1_reg,
                                                   Register tmp2_reg,
                                                   Label* L_success,
                                                   Label* L_failure) {
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
  mv(t1, (address)&SharedRuntime::_partial_subtype_ctr);
  Address pst_counter_addr(t1);
  ld(t0, pst_counter_addr);
  add(t0, t0, 1);
  sd(t0, pst_counter_addr);
#endif // PRODUCT

  // We will consult the secondary-super array.
  ld(x15, secondary_supers_addr);
  // Load the array length.
  lwu(x12, Address(x15, Array<Klass*>::length_offset_in_bytes()));
  // Skip to start of data.
  add(x15, x15, Array<Klass*>::base_offset_in_bytes());

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
  sd(super_klass, super_cache_addr);

  if (L_success != &L_fallthrough) {
    j(*L_success);
  }

#undef IS_A_TEMP

  bind(L_fallthrough);
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

  mv(ra, CAST_FROM_FN_PTR(address, Thread::current));
  jalr(ra);
  if (thread != c_rarg0) {
    mv(thread, c_rarg0);
  }

  // restore pushed registers
  pop_reg(saved_regs, sp);
}

void MacroAssembler::load_byte_map_base(Register reg) {
  CardTable::CardValue* byte_map_base =
    ((CardTableBarrierSet*)(BarrierSet::barrier_set()))->card_table()->byte_map_base();
  mv(reg, (uint64_t)byte_map_base);
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
    RuntimeAddress target(StubRoutines::throw_delayed_StackOverflowError_entry());
    relocate(target.rspec(), [&] {
      int32_t offset;
      movptr(t0, target.target(), offset);
      jalr(x0, t0, offset);
    });
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

void  MacroAssembler::set_narrow_oop(Register dst, jobject obj) {
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
  zero_extend(dst, dst, 32);
}

void  MacroAssembler::set_narrow_klass(Register dst, Klass* k) {
  assert (UseCompressedClassPointers, "should only be used for compressed headers");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int index = oop_recorder()->find_index(k);
  assert(!Universe::heap()->is_in(k), "should not be an oop");

  narrowKlass nk = CompressedKlassPointers::encode(k);
  relocate(metadata_Relocation::spec(index), [&] {
    li32(dst, nk);
  });
  zero_extend(dst, dst, 32);
}

// Maybe emit a call via a trampoline. If the code cache is small
// trampolines won't be emitted.
address MacroAssembler::trampoline_call(Address entry) {
  assert(entry.rspec().type() == relocInfo::runtime_call_type ||
         entry.rspec().type() == relocInfo::opt_virtual_call_type ||
         entry.rspec().type() == relocInfo::static_call_type ||
         entry.rspec().type() == relocInfo::virtual_call_type, "wrong reloc type");

  address target = entry.target();

  // We need a trampoline if branches are far.
  if (!in_scratch_emit_size()) {
    if (entry.rspec().type() == relocInfo::runtime_call_type) {
      assert(CodeBuffer::supports_shared_stubs(), "must support shared stubs");
      code()->share_trampoline_for(entry.target(), offset());
    } else {
      address stub = emit_trampoline_stub(offset(), target);
      if (stub == nullptr) {
        postcond(pc() == badAddress);
        return nullptr; // CodeCache is full
      }
    }
  }
  target = pc();

  address call_pc = pc();
#ifdef ASSERT
  if (entry.rspec().type() != relocInfo::runtime_call_type) {
    assert_alignment(call_pc);
  }
#endif
  relocate(entry.rspec(), [&] {
    jal(target);
  });

  postcond(pc() != badAddress);
  return call_pc;
}

address MacroAssembler::ic_call(address entry, jint method_index) {
  RelocationHolder rh = virtual_call_Relocation::spec(pc(), method_index);
  IncompressibleRegion ir(this);  // relocations
  movptr(t1, (address)Universe::non_oop_word());
  assert_cond(entry != nullptr);
  return trampoline_call(Address(entry, rh));
}

int MacroAssembler::ic_check_size() {
  // No compressed
  return (NativeInstruction::instruction_size * (2 /* 2 loads */ + 1 /* branch */)) +
          far_branch_size();
}

int MacroAssembler::ic_check(int end_alignment) {
  IncompressibleRegion ir(this);
  Register receiver = j_rarg0;
  Register data = t1;

  Register tmp1 = t0; // t0 always scratch
  // t2 is saved on call, thus should have been saved before this check.
  // Hence we can clobber it.
  Register tmp2 = t2;

  // The UEP of a code blob ensures that the VEP is padded. However, the padding of the UEP is placed
  // before the inline cache check, so we don't have to execute any nop instructions when dispatching
  // through the UEP, yet we can ensure that the VEP is aligned appropriately. That's why we align
  // before the inline cache check here, and not after
  align(end_alignment, ic_check_size());
  int uep_offset = offset();

  if (UseCompressedClassPointers) {
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

// Emit a trampoline stub for a call to a target which is too far away.
//
// code sequences:
//
// call-site:
//   branch-and-link to <destination> or <trampoline stub>
//
// Related trampoline stub for this call site in the stub section:
//   load the call target from the constant pool
//   branch (RA still points to the call site above)

address MacroAssembler::emit_trampoline_stub(int insts_call_instruction_offset,
                                             address dest) {
  // Max stub size: alignment nop, TrampolineStub.
  address stub = start_a_stub(max_trampoline_stub_size());
  if (stub == nullptr) {
    return nullptr;  // CodeBuffer::expand failed
  }

  // We are always 4-byte aligned here.
  assert_alignment(pc());

  // Create a trampoline stub relocation which relates this trampoline stub
  // with the call instruction at insts_call_instruction_offset in the
  // instructions code-section.

  // Make sure the address of destination 8-byte aligned after 3 instructions.
  align(wordSize, NativeCallTrampolineStub::data_offset);

  RelocationHolder rh = trampoline_stub_Relocation::spec(code()->insts()->start() +
                                                         insts_call_instruction_offset);
  const int stub_start_offset = offset();
  relocate(rh, [&] {
    // Now, create the trampoline stub's code:
    // - load the call
    // - call
    Label target;
    ld(t0, target);  // auipc + ld
    jr(t0);          // jalr
    bind(target);
    assert(offset() - stub_start_offset == NativeCallTrampolineStub::data_offset,
           "should be");
    assert(offset() % wordSize == 0, "bad alignment");
    emit_int64((int64_t)dest);
  });

  const address stub_start_addr = addr_at(stub_start_offset);

  assert(is_NativeCallTrampolineStub_at(stub_start_addr), "doesn't look like a trampoline");

  end_a_stub();
  return stub_start_addr;
}

int MacroAssembler::max_trampoline_stub_size() {
  // Max stub size: alignment nop, TrampolineStub.
  return NativeInstruction::instruction_size + NativeCallTrampolineStub::instruction_size;
}

int MacroAssembler::static_call_stub_size() {
  // (lui, addi, slli, addi, slli, addi) + (lui, addi, slli, addi, slli) + jalr
  return 12 * NativeInstruction::instruction_size;
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

void MacroAssembler::cmpptr(Register src1, Address src2, Label& equal) {
  assert_different_registers(src1, t0);
  relocate(src2.rspec(), [&] {
    int32_t offset;
    la(t0, src2.target(), offset);
    ld(t0, Address(t0, offset));
  });
  beq(src1, t0, equal);
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
  zero_extend(k, k, 32);
  slliw(t0, offset, LogBytesPerInt);
  add(offset, tmp, t0);
  slliw(t0, len, LogBytesPerInt);
  add(in, in, t0);

  const int unroll = 8;
  mv(tmp, unroll);
  blt(len, tmp, L_tail_loop);
  bind(L_unroll);
  for (int i = 0; i < unroll; i++) {
    sub(in, in, BytesPerInt);
    lwu(t0, Address(in, 0));
    mul(t1, t0, k);
    add(t0, t1, out);
    sub(offset, offset, BytesPerInt);
    lwu(t1, Address(offset, 0));
    add(t0, t0, t1);
    sw(t0, Address(offset, 0));
    srli(out, t0, 32);
  }
  subw(len, len, tmp);
  bge(len, tmp, L_unroll);

  bind(L_tail_loop);
  blez(len, L_end);
  sub(in, in, BytesPerInt);
  lwu(t0, Address(in, 0));
  mul(t1, t0, k);
  add(t0, t1, out);
  sub(offset, offset, BytesPerInt);
  lwu(t1, Address(offset, 0));
  add(t0, t0, t1);
  sw(t0, Address(offset, 0));
  srli(out, t0, 32);
  subw(len, len, 1);
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
 * Multiply 32 bit by 32 bit first loop.
 */
void MacroAssembler::multiply_32_x_32_loop(Register x, Register xstart, Register x_xstart,
                                           Register y, Register y_idx, Register z,
                                           Register carry, Register product,
                                           Register idx, Register kdx) {
  // jlong carry, x[], y[], z[];
  // for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx--, kdx--) {
  //     long product = y[idx] * x[xstart] + carry;
  //     z[kdx] = (int)product;
  //     carry = product >>> 32;
  // }
  // z[xstart] = (int)carry;

  Label L_first_loop, L_first_loop_exit;
  blez(idx, L_first_loop_exit);

  shadd(t0, xstart, x, t0, LogBytesPerInt);
  lwu(x_xstart, Address(t0, 0));

  bind(L_first_loop);
  subw(idx, idx, 1);
  shadd(t0, idx, y, t0, LogBytesPerInt);
  lwu(y_idx, Address(t0, 0));
  mul(product, x_xstart, y_idx);
  add(product, product, carry);
  srli(carry, product, 32);
  subw(kdx, kdx, 1);
  shadd(t0, kdx, z, t0, LogBytesPerInt);
  sw(product, Address(t0, 0));
  bgtz(idx, L_first_loop);

  bind(L_first_loop_exit);
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

  subw(xstart, xstart, 1);
  bltz(xstart, L_one_x);

  shadd(t0, xstart, x, t0, LogBytesPerInt);
  ld(x_xstart, Address(t0, 0));
  ror_imm(x_xstart, x_xstart, 32); // convert big-endian to little-endian

  bind(L_first_loop);
  subw(idx, idx, 1);
  bltz(idx, L_first_loop_exit);
  subw(idx, idx, 1);
  bltz(idx, L_one_y);

  shadd(t0, idx, y, t0, LogBytesPerInt);
  ld(y_idx, Address(t0, 0));
  ror_imm(y_idx, y_idx, 32); // convert big-endian to little-endian
  bind(L_multiply);

  mulhu(t0, x_xstart, y_idx);
  mul(product, x_xstart, y_idx);
  cad(product, product, carry, t1);
  adc(carry, t0, zr, t1);

  subw(kdx, kdx, 2);
  ror_imm(product, product, 32); // back to big-endian
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

  ror_imm(yz_idx1, yz_idx1, 32); // convert big-endian to little-endian
  ror_imm(yz_idx2, yz_idx2, 32);

  ld(t1, Address(tmp6, 0));
  ld(t0, Address(tmp6, wordSize));

  mul(tmp3, product_hi, yz_idx1); //  yz_idx1 * product_hi -> tmp4:tmp3
  mulhu(tmp4, product_hi, yz_idx1);

  ror_imm(t0, t0, 32, tmp); // convert big-endian to little-endian
  ror_imm(t1, t1, 32, tmp);

  mul(tmp, product_hi, yz_idx2); //  yz_idx2 * product_hi -> carry2:tmp
  mulhu(carry2, product_hi, yz_idx2);

  cad(tmp3, tmp3, carry, carry);
  adc(tmp4, tmp4, zr, carry);
  cad(tmp3, tmp3, t0, t0);
  cadc(tmp4, tmp4, tmp, t0);
  adc(carry, carry2, zr, t0);
  cad(tmp4, tmp4, t1, carry2);
  adc(carry, carry, zr, carry2);

  ror_imm(tmp3, tmp3, 32); // convert little-endian to big-endian
  ror_imm(tmp4, tmp4, 32);
  sd(tmp4, Address(tmp6, 0));
  sd(tmp3, Address(tmp6, wordSize));

  j(L_third_loop);

  bind(L_third_loop_exit);

  andi(idx, idx, 0x3);
  beqz(idx, L_post_third_loop_done);

  Label L_check_1;
  subw(idx, idx, 2);
  bltz(idx, L_check_1);

  shadd(t0, idx, y, t0, LogBytesPerInt);
  ld(yz_idx1, Address(t0, 0));
  ror_imm(yz_idx1, yz_idx1, 32);

  mul(tmp3, product_hi, yz_idx1); //  yz_idx1 * product_hi -> tmp4:tmp3
  mulhu(tmp4, product_hi, yz_idx1);

  shadd(t0, idx, z, t0, LogBytesPerInt);
  ld(yz_idx2, Address(t0, 0));
  ror_imm(yz_idx2, yz_idx2, 32, tmp);

  add2_with_carry(carry, tmp4, tmp3, carry, yz_idx2, tmp);

  ror_imm(tmp3, tmp3, 32, tmp);
  sd(tmp3, Address(t0, 0));

  bind(L_check_1);

  andi(idx, idx, 0x1);
  subw(idx, idx, 1);
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
 * x15: zlen
 * x16: tmp1
 * x17: tmp2
 * x7:  tmp3
 * x28: tmp4
 * x29: tmp5
 * x30: tmp6
 * x31: tmp7
 */
void MacroAssembler::multiply_to_len(Register x, Register xlen, Register y, Register ylen,
                                     Register z, Register zlen,
                                     Register tmp1, Register tmp2, Register tmp3, Register tmp4,
                                     Register tmp5, Register tmp6, Register product_hi) {
  assert_different_registers(x, xlen, y, ylen, z, zlen, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6);

  const Register idx = tmp1;
  const Register kdx = tmp2;
  const Register xstart = tmp3;

  const Register y_idx = tmp4;
  const Register carry = tmp5;
  const Register product = xlen;
  const Register x_xstart = zlen; // reuse register

  mv(idx, ylen); // idx = ylen;
  mv(kdx, zlen); // kdx = xlen+ylen;
  mv(carry, zr); // carry = 0;

  Label L_multiply_64_x_64_loop, L_done;

  subw(xstart, xlen, 1);
  bltz(xstart, L_done);

  const Register jdx = tmp1;

  if (AvoidUnalignedAccesses) {
    // Check if x and y are both 8-byte aligned.
    orr(t0, xlen, ylen);
    test_bit(t0, t0, 0);
    beqz(t0, L_multiply_64_x_64_loop);

    multiply_32_x_32_loop(x, xstart, x_xstart, y, y_idx, z, carry, product, idx, kdx);
    shadd(t0, xstart, z, t0, LogBytesPerInt);
    sw(carry, Address(t0, 0));

    Label L_second_loop_unaligned;
    bind(L_second_loop_unaligned);
    mv(carry, zr);
    mv(jdx, ylen);
    subw(xstart, xstart, 1);
    bltz(xstart, L_done);
    sub(sp, sp, 2 * wordSize);
    sd(z, Address(sp, 0));
    sd(zr, Address(sp, wordSize));
    shadd(t0, xstart, z, t0, LogBytesPerInt);
    addi(z, t0, 4);
    shadd(t0, xstart, x, t0, LogBytesPerInt);
    lwu(product, Address(t0, 0));
    Label L_third_loop, L_third_loop_exit;

    blez(jdx, L_third_loop_exit);

    bind(L_third_loop);
    subw(jdx, jdx, 1);
    shadd(t0, jdx, y, t0, LogBytesPerInt);
    lwu(t0, Address(t0, 0));
    mul(t1, t0, product);
    add(t0, t1, carry);
    shadd(tmp6, jdx, z, t1, LogBytesPerInt);
    lwu(t1, Address(tmp6, 0));
    add(t0, t0, t1);
    sw(t0, Address(tmp6, 0));
    srli(carry, t0, 32);
    bgtz(jdx, L_third_loop);

    bind(L_third_loop_exit);
    ld(z, Address(sp, 0));
    addi(sp, sp, 2 * wordSize);
    shadd(t0, xstart, z, t0, LogBytesPerInt);
    sw(carry, Address(t0, 0));

    j(L_second_loop_unaligned);
  }

  bind(L_multiply_64_x_64_loop);
  multiply_64_x_64_loop(x, xstart, x_xstart, y, y_idx, z, carry, product, idx, kdx);

  Label L_second_loop_aligned;
  beqz(kdx, L_second_loop_aligned);

  Label L_carry;
  subw(kdx, kdx, 1);
  beqz(kdx, L_carry);

  shadd(t0, kdx, z, t0, LogBytesPerInt);
  sw(carry, Address(t0, 0));
  srli(carry, carry, 32);
  subw(kdx, kdx, 1);

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

  subw(xstart, xstart, 1); // i = xstart-1;
  bltz(xstart, L_done);

  sub(sp, sp, 4 * wordSize);
  sd(z, Address(sp, 0));

  Label L_last_x;
  shadd(t0, xstart, z, t0, LogBytesPerInt);
  addi(z, t0, 4);
  subw(xstart, xstart, 1); // i = xstart-1;
  bltz(xstart, L_last_x);

  shadd(t0, xstart, x, t0, LogBytesPerInt);
  ld(product_hi, Address(t0, 0));
  ror_imm(product_hi, product_hi, 32); // convert big-endian to little-endian

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

  subw(tmp3, tmp3, 1);
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

// Count bits of trailing zero chars from lsb to msb until first non-zero element.
// For LL case, one byte for one element, so shift 8 bits once, and for other case,
// shift 16 bits once.
void MacroAssembler::ctzc_bit(Register Rd, Register Rs, bool isLL, Register tmp1, Register tmp2) {
  if (UseZbb) {
    assert_different_registers(Rd, Rs, tmp1);
    int step = isLL ? 8 : 16;
    ctz(Rd, Rs);
    andi(tmp1, Rd, step - 1);
    sub(Rd, Rd, tmp1);
    return;
  }

  assert_different_registers(Rd, Rs, tmp1, tmp2);
  Label Loop;
  int step = isLL ? 8 : 16;
  mv(Rd, -step);
  mv(tmp2, Rs);

  bind(Loop);
  addi(Rd, Rd, step);
  andi(tmp1, tmp2, ((1 << step) - 1));
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
  andi(tmp2, Rs, 0xFF); // last byte mask at lower word
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
// ptr, cnt, and t0 are clobbered.
address MacroAssembler::zero_words(Register ptr, Register cnt) {
  assert(is_power_of_2(zero_words_block_size), "adjust this");
  assert(ptr == x28 && cnt == x29, "mismatch in register usage");
  assert_different_registers(cnt, t0);

  BLOCK_COMMENT("zero_words {");

  mv(t0, zero_words_block_size);
  Label around, done, done16;
  bltu(cnt, t0, around);
  {
    RuntimeAddress zero_blocks(StubRoutines::riscv::zero_blocks());
    assert(zero_blocks.target() != nullptr, "zero_blocks stub has not been generated");
    if (StubRoutines::riscv::complete()) {
      address tpc = trampoline_call(zero_blocks);
      if (tpc == nullptr) {
        DEBUG_ONLY(reset_labels(around));
        postcond(pc() == badAddress);
        return nullptr;
      }
    } else {
      jal(zero_blocks);
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
    add(loop_base, base, remainder * wordSize);
    bind(loop);
    sub(cnt_reg, cnt_reg, unroll);
    for (int i = 0; i < unroll; i++) {
      sd(zr, Address(loop_base, i * wordSize));
    }
    add(loop_base, loop_base, unroll * wordSize);
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
  // align 8, so first sd n % 8 = mod, next loop sd 8 * n.
  shadd(base, t0, base, t1, 3);
  la(t1, entry);
  slli(t0, t0, 2); // sd_inst_nums * 4; t0 is cnt % 8, so t1 = t1 - sd_inst_nums * 4, 4 is sizeof(inst)
  sub(t1, t1, t0);
  jr(t1);

  bind(loop);
  add(base, base, unroll * 8);
  for (int i = -unroll; i < 0; i++) {
    sd(value, Address(base, i * 8));
  }
  bind(entry);
  sub(cnt, cnt, unroll);
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
// you want to use it elsewhere, note that cnt must be >= CacheLineSize.
void MacroAssembler::zero_dcache_blocks(Register base, Register cnt, Register tmp1, Register tmp2) {
  Label initial_table_end, loop;

  // Align base with cache line size.
  neg(tmp1, base);
  andi(tmp1, tmp1, CacheLineSize - 1);

  // tmp1: the number of bytes to be filled to align the base with cache line size.
  add(base, base, tmp1);
  srai(tmp2, tmp1, 3);
  sub(cnt, cnt, tmp2);
  srli(tmp2, tmp1, 1);
  la(tmp1, initial_table_end);
  sub(tmp2, tmp1, tmp2);
  jr(tmp2);
  for (int i = -CacheLineSize + wordSize; i < 0; i += wordSize) {
    sd(zr, Address(base, i));
  }
  bind(initial_table_end);

  mv(tmp1, CacheLineSize / wordSize);
  bind(loop);
  cbo_zero(base);
  sub(cnt, cnt, tmp1);
  add(base, base, CacheLineSize);
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

#define FCVT_SAFE(FLOATCVT, FLOATSIG)                                                     \
void MacroAssembler::FLOATCVT##_safe(Register dst, FloatRegister src, Register tmp) {     \
  Label done;                                                                             \
  assert_different_registers(dst, tmp);                                                   \
  fclass_##FLOATSIG(tmp, src);                                                            \
  mv(dst, zr);                                                                            \
  /* check if src is NaN */                                                               \
  andi(tmp, tmp, fclass_mask::nan);                                                       \
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
    addi(result, result, -1);                                                           \
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
    addi(result, result, -1);                                                           \
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
  for (int i = -unroll; i < 0; i++) {
    sd(zr, Address(tmp, i * wordSize));
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

void MacroAssembler::zero_extend(Register dst, Register src, int bits) {
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
      if (UseZbb) {
        zext_b(dst, src);
        return;
      }
      break;
    default:
      break;
  }
  slli(dst, src, XLEN - bits);
  srli(dst, dst, XLEN - bits);
}

void MacroAssembler::sign_extend(Register dst, Register src, int bits) {
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

// On 64 bit we will store integer like items to the stack as
// 64 bits items (riscv64 abi) even though java would only store
// 32bits for a parameter. On 32bit it will simply be 32 bits
// So this routine will do 32->32 on 32bit and 32->64 on 64bit
void MacroAssembler::move32_64(VMRegPair src, VMRegPair dst, Register tmp) {
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      ld(tmp, Address(fp, reg2offset_in(src.first())));
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
      sign_extend(dst.first()->as_Register(), src.first()->as_Register(), 32);
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

void MacroAssembler::rt_call(address dest, Register tmp) {
  CodeBlob *cb = CodeCache::find_blob(dest);
  RuntimeAddress target(dest);
  if (cb) {
    far_call(target, tmp);
  } else {
    relocate(target.rspec(), [&] {
      int32_t offset;
      movptr(tmp, target.target(), offset);
      jalr(x1, tmp, offset);
    });
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
    and_imm12(Rd, Rs, imm);
  } else {
    srli(Rd, Rs, bit_pos);
    and_imm12(Rd, Rd, 1);
  }
}

// Implements lightweight-locking.
// Branches to slow upon failure to lock the object.
// Falls through upon success.
//
//  - obj: the object to be locked
//  - hdr: the header, already loaded from obj, will be destroyed
//  - tmp1, tmp2: temporary registers, will be destroyed
void MacroAssembler::lightweight_lock(Register obj, Register hdr, Register tmp1, Register tmp2, Label& slow) {
  assert(LockingMode == LM_LIGHTWEIGHT, "only used with new lightweight locking");
  assert_different_registers(obj, hdr, tmp1, tmp2, t0);

  // Check if we would have space on lock-stack for the object.
  lwu(tmp1, Address(xthread, JavaThread::lock_stack_top_offset()));
  mv(tmp2, (unsigned)LockStack::end_offset());
  bge(tmp1, tmp2, slow, /* is_far */ true);

  // Load (object->mark() | 1) into hdr
  ori(hdr, hdr, markWord::unlocked_value);
  // Clear lock-bits, into tmp2
  xori(tmp2, hdr, markWord::unlocked_value);

  // Try to swing header from unlocked to locked
  Label success;
  cmpxchgptr(hdr, tmp2, obj, tmp1, success, &slow);
  bind(success);

  // After successful lock, push object on lock-stack
  lwu(tmp1, Address(xthread, JavaThread::lock_stack_top_offset()));
  add(tmp2, xthread, tmp1);
  sd(obj, Address(tmp2, 0));
  addw(tmp1, tmp1, oopSize);
  sw(tmp1, Address(xthread, JavaThread::lock_stack_top_offset()));
}

// Implements ligthweight-unlocking.
// Branches to slow upon failure.
// Falls through upon success.
//
// - obj: the object to be unlocked
// - hdr: the (pre-loaded) header of the object
// - tmp1, tmp2: temporary registers
void MacroAssembler::lightweight_unlock(Register obj, Register hdr, Register tmp1, Register tmp2, Label& slow) {
  assert(LockingMode == LM_LIGHTWEIGHT, "only used with new lightweight locking");
  assert_different_registers(obj, hdr, tmp1, tmp2, t0);

#ifdef ASSERT
  {
    // The following checks rely on the fact that LockStack is only ever modified by
    // its owning thread, even if the lock got inflated concurrently; removal of LockStack
    // entries after inflation will happen delayed in that case.

    // Check for lock-stack underflow.
    Label stack_ok;
    lwu(tmp1, Address(xthread, JavaThread::lock_stack_top_offset()));
    mv(tmp2, (unsigned)LockStack::start_offset());
    bgt(tmp1, tmp2, stack_ok);
    STOP("Lock-stack underflow");
    bind(stack_ok);
  }
  {
    // Check if the top of the lock-stack matches the unlocked object.
    Label tos_ok;
    subw(tmp1, tmp1, oopSize);
    add(tmp1, xthread, tmp1);
    ld(tmp1, Address(tmp1, 0));
    beq(tmp1, obj, tos_ok);
    STOP("Top of lock-stack does not match the unlocked object");
    bind(tos_ok);
  }
  {
    // Check that hdr is fast-locked.
   Label hdr_ok;
    andi(tmp1, hdr, markWord::lock_mask_in_place);
    beqz(tmp1, hdr_ok);
    STOP("Header is not fast-locked");
    bind(hdr_ok);
  }
#endif

  // Load the new header (unlocked) into tmp1
  ori(tmp1, hdr, markWord::unlocked_value);

  // Try to swing header from locked to unlocked
  Label success;
  cmpxchgptr(hdr, tmp1, obj, tmp2, success, &slow);
  bind(success);

  // After successful unlock, pop object from lock-stack
  lwu(tmp1, Address(xthread, JavaThread::lock_stack_top_offset()));
  subw(tmp1, tmp1, oopSize);
#ifdef ASSERT
  add(tmp2, xthread, tmp1);
  sd(zr, Address(tmp2, 0));
#endif
  sw(tmp1, Address(xthread, JavaThread::lock_stack_top_offset()));
}
