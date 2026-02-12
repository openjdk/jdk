/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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
#include "code/aotCodeCache.hpp"
#include "code/compiledIC.hpp"
#include "compiler/compiler_globals.hpp"
#include "compiler/disassembler.hpp"
#include "crc32c.h"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/tlab_globals.hpp"
#include "interpreter/bytecodeHistogram.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interpreterRuntime.hpp"
#include "jvm.h"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/accessDecorators.hpp"
#include "oops/compressedKlass.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/klass.inline.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/continuation.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/os.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/safepointMechanism.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/macros.hpp"

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#define STOP(error) stop(error)
#else
#define BLOCK_COMMENT(str) block_comment(str)
#define STOP(error) block_comment(error); stop(error)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

#ifdef ASSERT
bool AbstractAssembler::pd_check_instruction_mark() { return true; }
#endif

static const Assembler::Condition reverse[] = {
    Assembler::noOverflow     /* overflow      = 0x0 */ ,
    Assembler::overflow       /* noOverflow    = 0x1 */ ,
    Assembler::aboveEqual     /* carrySet      = 0x2, below         = 0x2 */ ,
    Assembler::below          /* aboveEqual    = 0x3, carryClear    = 0x3 */ ,
    Assembler::notZero        /* zero          = 0x4, equal         = 0x4 */ ,
    Assembler::zero           /* notZero       = 0x5, notEqual      = 0x5 */ ,
    Assembler::above          /* belowEqual    = 0x6 */ ,
    Assembler::belowEqual     /* above         = 0x7 */ ,
    Assembler::positive       /* negative      = 0x8 */ ,
    Assembler::negative       /* positive      = 0x9 */ ,
    Assembler::noParity       /* parity        = 0xa */ ,
    Assembler::parity         /* noParity      = 0xb */ ,
    Assembler::greaterEqual   /* less          = 0xc */ ,
    Assembler::less           /* greaterEqual  = 0xd */ ,
    Assembler::greater        /* lessEqual     = 0xe */ ,
    Assembler::lessEqual      /* greater       = 0xf, */

};


// Implementation of MacroAssembler

Address MacroAssembler::as_Address(AddressLiteral adr) {
  // amd64 always does this as a pc-rel
  // we can be absolute or disp based on the instruction type
  // jmp/call are displacements others are absolute
  assert(!adr.is_lval(), "must be rval");
  assert(reachable(adr), "must be");
  return Address(checked_cast<int32_t>(adr.target() - pc()), adr.target(), adr.reloc());

}

Address MacroAssembler::as_Address(ArrayAddress adr, Register rscratch) {
  AddressLiteral base = adr.base();
  lea(rscratch, base);
  Address index = adr.index();
  assert(index._disp == 0, "must not have disp"); // maybe it can?
  Address array(rscratch, index._index, index._scale, index._disp);
  return array;
}

void MacroAssembler::call_VM_leaf_base(address entry_point, int num_args) {
  Label L, E;

#ifdef _WIN64
  // Windows always allocates space for it's register args
  assert(num_args <= 4, "only register arguments supported");
  subq(rsp,  frame::arg_reg_save_area_bytes);
#endif

  // Align stack if necessary
  testl(rsp, 15);
  jcc(Assembler::zero, L);

  subq(rsp, 8);
  call(RuntimeAddress(entry_point));
  addq(rsp, 8);
  jmp(E);

  bind(L);
  call(RuntimeAddress(entry_point));

  bind(E);

#ifdef _WIN64
  // restore stack pointer
  addq(rsp, frame::arg_reg_save_area_bytes);
#endif
}

void MacroAssembler::cmp64(Register src1, AddressLiteral src2, Register rscratch) {
  assert(!src2.is_lval(), "should use cmpptr");
  assert(rscratch != noreg || always_reachable(src2), "missing");

  if (reachable(src2)) {
    cmpq(src1, as_Address(src2));
  } else {
    lea(rscratch, src2);
    Assembler::cmpq(src1, Address(rscratch, 0));
  }
}

int MacroAssembler::corrected_idivq(Register reg) {
  // Full implementation of Java ldiv and lrem; checks for special
  // case as described in JVM spec., p.243 & p.271.  The function
  // returns the (pc) offset of the idivl instruction - may be needed
  // for implicit exceptions.
  //
  //         normal case                           special case
  //
  // input : rax: dividend                         min_long
  //         reg: divisor   (may not be eax/edx)   -1
  //
  // output: rax: quotient  (= rax idiv reg)       min_long
  //         rdx: remainder (= rax irem reg)       0
  assert(reg != rax && reg != rdx, "reg cannot be rax or rdx register");
  static const int64_t min_long = 0x8000000000000000;
  Label normal_case, special_case;

  // check for special case
  cmp64(rax, ExternalAddress((address) &min_long), rdx /*rscratch*/);
  jcc(Assembler::notEqual, normal_case);
  xorl(rdx, rdx); // prepare rdx for possible special case (where
                  // remainder = 0)
  cmpq(reg, -1);
  jcc(Assembler::equal, special_case);

  // handle normal case
  bind(normal_case);
  cdqq();
  int idivq_offset = offset();
  idivq(reg);

  // normal and special case exit
  bind(special_case);

  return idivq_offset;
}

void MacroAssembler::decrementq(Register reg, int value) {
  if (value == min_jint) { subq(reg, value); return; }
  if (value <  0) { incrementq(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decq(reg) ; return; }
  /* else */      { subq(reg, value)       ; return; }
}

void MacroAssembler::decrementq(Address dst, int value) {
  if (value == min_jint) { subq(dst, value); return; }
  if (value <  0) { incrementq(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decq(dst) ; return; }
  /* else */      { subq(dst, value)       ; return; }
}

void MacroAssembler::incrementq(AddressLiteral dst, Register rscratch) {
  assert(rscratch != noreg || always_reachable(dst), "missing");

  if (reachable(dst)) {
    incrementq(as_Address(dst));
  } else {
    lea(rscratch, dst);
    incrementq(Address(rscratch, 0));
  }
}

void MacroAssembler::incrementq(Register reg, int value) {
  if (value == min_jint) { addq(reg, value); return; }
  if (value <  0) { decrementq(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incq(reg) ; return; }
  /* else */      { addq(reg, value)       ; return; }
}

void MacroAssembler::incrementq(Address dst, int value) {
  if (value == min_jint) { addq(dst, value); return; }
  if (value <  0) { decrementq(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incq(dst) ; return; }
  /* else */      { addq(dst, value)       ; return; }
}

// 32bit can do a case table jump in one instruction but we no longer allow the base
// to be installed in the Address class
void MacroAssembler::jump(ArrayAddress entry, Register rscratch) {
  lea(rscratch, entry.base());
  Address dispatch = entry.index();
  assert(dispatch._base == noreg, "must be");
  dispatch._base = rscratch;
  jmp(dispatch);
}

void MacroAssembler::lcmp2int(Register x_hi, Register x_lo, Register y_hi, Register y_lo) {
  ShouldNotReachHere(); // 64bit doesn't use two regs
  cmpq(x_lo, y_lo);
}

void MacroAssembler::lea(Register dst, AddressLiteral src) {
  mov_literal64(dst, (intptr_t)src.target(), src.rspec());
}

void MacroAssembler::lea(Address dst, AddressLiteral adr, Register rscratch) {
  lea(rscratch, adr);
  movptr(dst, rscratch);
}

void MacroAssembler::leave() {
  // %%% is this really better? Why not on 32bit too?
  emit_int8((unsigned char)0xC9); // LEAVE
}

void MacroAssembler::lneg(Register hi, Register lo) {
  ShouldNotReachHere(); // 64bit doesn't use two regs
  negq(lo);
}

void MacroAssembler::movoop(Register dst, jobject obj) {
  mov_literal64(dst, (intptr_t)obj, oop_Relocation::spec_for_immediate());
}

void MacroAssembler::movoop(Address dst, jobject obj, Register rscratch) {
  mov_literal64(rscratch, (intptr_t)obj, oop_Relocation::spec_for_immediate());
  movq(dst, rscratch);
}

void MacroAssembler::mov_metadata(Register dst, Metadata* obj) {
  mov_literal64(dst, (intptr_t)obj, metadata_Relocation::spec_for_immediate());
}

void MacroAssembler::mov_metadata(Address dst, Metadata* obj, Register rscratch) {
  mov_literal64(rscratch, (intptr_t)obj, metadata_Relocation::spec_for_immediate());
  movq(dst, rscratch);
}

void MacroAssembler::movptr(Register dst, AddressLiteral src) {
  if (src.is_lval()) {
    mov_literal64(dst, (intptr_t)src.target(), src.rspec());
  } else {
    if (reachable(src)) {
      movq(dst, as_Address(src));
    } else {
      lea(dst, src);
      movq(dst, Address(dst, 0));
    }
  }
}

void MacroAssembler::movptr(ArrayAddress dst, Register src, Register rscratch) {
  movq(as_Address(dst, rscratch), src);
}

void MacroAssembler::movptr(Register dst, ArrayAddress src) {
  movq(dst, as_Address(src, dst /*rscratch*/));
}

// src should NEVER be a real pointer. Use AddressLiteral for true pointers
void MacroAssembler::movptr(Address dst, intptr_t src, Register rscratch) {
  if (is_simm32(src)) {
    movptr(dst, checked_cast<int32_t>(src));
  } else {
    mov64(rscratch, src);
    movq(dst, rscratch);
  }
}

void MacroAssembler::pushoop(jobject obj, Register rscratch) {
  movoop(rscratch, obj);
  push(rscratch);
}

void MacroAssembler::pushklass(Metadata* obj, Register rscratch) {
  mov_metadata(rscratch, obj);
  push(rscratch);
}

void MacroAssembler::pushptr(AddressLiteral src, Register rscratch) {
  lea(rscratch, src);
  if (src.is_lval()) {
    push(rscratch);
  } else {
    pushq(Address(rscratch, 0));
  }
}

static void pass_arg0(MacroAssembler* masm, Register arg) {
  if (c_rarg0 != arg ) {
    masm->mov(c_rarg0, arg);
  }
}

static void pass_arg1(MacroAssembler* masm, Register arg) {
  if (c_rarg1 != arg ) {
    masm->mov(c_rarg1, arg);
  }
}

static void pass_arg2(MacroAssembler* masm, Register arg) {
  if (c_rarg2 != arg ) {
    masm->mov(c_rarg2, arg);
  }
}

static void pass_arg3(MacroAssembler* masm, Register arg) {
  if (c_rarg3 != arg ) {
    masm->mov(c_rarg3, arg);
  }
}

void MacroAssembler::stop(const char* msg) {
  if (ShowMessageBoxOnError) {
    address rip = pc();
    pusha(); // get regs on stack
    lea(c_rarg1, InternalAddress(rip));
    movq(c_rarg2, rsp); // pass pointer to regs array
  }
  // Skip AOT caching C strings in scratch buffer.
  const char* str = (code_section()->scratch_emit()) ? msg : AOTCodeCache::add_C_string(msg);
  lea(c_rarg0, ExternalAddress((address) str));
  andq(rsp, -16); // align stack as required by ABI
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, MacroAssembler::debug64)));
  hlt();
}

void MacroAssembler::warn(const char* msg) {
  push(rbp);
  movq(rbp, rsp);
  andq(rsp, -16);     // align stack as required by push_CPU_state and call
  push_CPU_state();   // keeps alignment at 16 bytes

#ifdef _WIN64
  // Windows always allocates space for its register args
  subq(rsp,  frame::arg_reg_save_area_bytes);
#endif
  lea(c_rarg0, ExternalAddress((address) msg));
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, warning)));

#ifdef _WIN64
  // restore stack pointer
  addq(rsp, frame::arg_reg_save_area_bytes);
#endif
  pop_CPU_state();
  mov(rsp, rbp);
  pop(rbp);
}

void MacroAssembler::print_state() {
  address rip = pc();
  pusha();            // get regs on stack
  push(rbp);
  movq(rbp, rsp);
  andq(rsp, -16);     // align stack as required by push_CPU_state and call
  push_CPU_state();   // keeps alignment at 16 bytes

  lea(c_rarg0, InternalAddress(rip));
  lea(c_rarg1, Address(rbp, wordSize)); // pass pointer to regs array
  call_VM_leaf(CAST_FROM_FN_PTR(address, MacroAssembler::print_state64), c_rarg0, c_rarg1);

  pop_CPU_state();
  mov(rsp, rbp);
  pop(rbp);
  popa();
}

#ifndef PRODUCT
extern "C" void findpc(intptr_t x);
#endif

void MacroAssembler::debug64(char* msg, int64_t pc, int64_t regs[]) {
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
    // To see where a verify_oop failed, get $ebx+40/X for this frame.
    // XXX correct this offset for amd64
    // This is the value of eip which points to where verify_oop will return.
    if (os::message_box(msg, "Execution stopped, print registers?")) {
      print_state64(pc, regs);
      BREAKPOINT;
    }
  }
  fatal("DEBUG MESSAGE: %s", msg);
}

void MacroAssembler::print_state64(int64_t pc, int64_t regs[]) {
  ttyLocker ttyl;
  DebuggingContext debugging{};
  tty->print_cr("rip = 0x%016lx", (intptr_t)pc);
#ifndef PRODUCT
  tty->cr();
  findpc(pc);
  tty->cr();
#endif
#define PRINT_REG(rax, value) \
  { tty->print("%s = ", #rax); os::print_location(tty, value); }
  PRINT_REG(rax, regs[15]);
  PRINT_REG(rbx, regs[12]);
  PRINT_REG(rcx, regs[14]);
  PRINT_REG(rdx, regs[13]);
  PRINT_REG(rdi, regs[8]);
  PRINT_REG(rsi, regs[9]);
  PRINT_REG(rbp, regs[10]);
  // rsp is actually not stored by pusha(), compute the old rsp from regs (rsp after pusha): regs + 16 = old rsp
  PRINT_REG(rsp, (intptr_t)(&regs[16]));
  PRINT_REG(r8 , regs[7]);
  PRINT_REG(r9 , regs[6]);
  PRINT_REG(r10, regs[5]);
  PRINT_REG(r11, regs[4]);
  PRINT_REG(r12, regs[3]);
  PRINT_REG(r13, regs[2]);
  PRINT_REG(r14, regs[1]);
  PRINT_REG(r15, regs[0]);
#undef PRINT_REG
  // Print some words near the top of the stack.
  int64_t* rsp = &regs[16];
  int64_t* dump_sp = rsp;
  for (int col1 = 0; col1 < 8; col1++) {
    tty->print("(rsp+0x%03x) 0x%016lx: ", (int)((intptr_t)dump_sp - (intptr_t)rsp), (intptr_t)dump_sp);
    os::print_location(tty, *dump_sp++);
  }
  for (int row = 0; row < 25; row++) {
    tty->print("(rsp+0x%03x) 0x%016lx: ", (int)((intptr_t)dump_sp - (intptr_t)rsp), (intptr_t)dump_sp);
    for (int col = 0; col < 4; col++) {
      tty->print(" 0x%016lx", (intptr_t)*dump_sp++);
    }
    tty->cr();
  }
  // Print some instructions around pc:
  Disassembler::decode((address)pc-64, (address)pc);
  tty->print_cr("--------");
  Disassembler::decode((address)pc, (address)pc+32);
}

// The java_calling_convention describes stack locations as ideal slots on
// a frame with no abi restrictions. Since we must observe abi restrictions
// (like the placement of the register window) the slots must be biased by
// the following value.
static int reg2offset_in(VMReg r) {
  // Account for saved rbp and return address
  // This should really be in_preserve_stack_slots
  return (r->reg2stack() + 4) * VMRegImpl::stack_slot_size;
}

static int reg2offset_out(VMReg r) {
  return (r->reg2stack() + SharedRuntime::out_preserve_stack_slots()) * VMRegImpl::stack_slot_size;
}

// A long move
void MacroAssembler::long_move(VMRegPair src, VMRegPair dst, Register tmp, int in_stk_bias, int out_stk_bias) {

  // The calling conventions assures us that each VMregpair is either
  // all really one physical register or adjacent stack slots.

  if (src.is_single_phys_reg() ) {
    if (dst.is_single_phys_reg()) {
      if (dst.first() != src.first()) {
        mov(dst.first()->as_Register(), src.first()->as_Register());
      }
    } else {
      assert(dst.is_single_reg(), "not a stack pair: (%s, %s), (%s, %s)",
             src.first()->name(), src.second()->name(), dst.first()->name(), dst.second()->name());
      movq(Address(rsp, reg2offset_out(dst.first()) + out_stk_bias), src.first()->as_Register());
    }
  } else if (dst.is_single_phys_reg()) {
    assert(src.is_single_reg(),  "not a stack pair");
    movq(dst.first()->as_Register(), Address(rbp, reg2offset_in(src.first()) + in_stk_bias));
  } else {
    assert(src.is_single_reg() && dst.is_single_reg(), "not stack pairs");
    movq(tmp, Address(rbp, reg2offset_in(src.first()) + in_stk_bias));
    movq(Address(rsp, reg2offset_out(dst.first()) + out_stk_bias), tmp);
  }
}

// A double move
void MacroAssembler::double_move(VMRegPair src, VMRegPair dst, Register tmp, int in_stk_bias, int out_stk_bias) {

  // The calling conventions assures us that each VMregpair is either
  // all really one physical register or adjacent stack slots.

  if (src.is_single_phys_reg() ) {
    if (dst.is_single_phys_reg()) {
      // In theory these overlap but the ordering is such that this is likely a nop
      if ( src.first() != dst.first()) {
        movdbl(dst.first()->as_XMMRegister(), src.first()->as_XMMRegister());
      }
    } else {
      assert(dst.is_single_reg(), "not a stack pair");
      movdbl(Address(rsp, reg2offset_out(dst.first()) + out_stk_bias), src.first()->as_XMMRegister());
    }
  } else if (dst.is_single_phys_reg()) {
    assert(src.is_single_reg(),  "not a stack pair");
    movdbl(dst.first()->as_XMMRegister(), Address(rbp, reg2offset_in(src.first()) + in_stk_bias));
  } else {
    assert(src.is_single_reg() && dst.is_single_reg(), "not stack pairs");
    movq(tmp, Address(rbp, reg2offset_in(src.first()) + in_stk_bias));
    movq(Address(rsp, reg2offset_out(dst.first()) + out_stk_bias), tmp);
  }
}


// A float arg may have to do float reg int reg conversion
void MacroAssembler::float_move(VMRegPair src, VMRegPair dst, Register tmp, int in_stk_bias, int out_stk_bias) {
  assert(!src.second()->is_valid() && !dst.second()->is_valid(), "bad float_move");

  // The calling conventions assures us that each VMregpair is either
  // all really one physical register or adjacent stack slots.

  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      movl(tmp, Address(rbp, reg2offset_in(src.first()) + in_stk_bias));
      movptr(Address(rsp, reg2offset_out(dst.first()) + out_stk_bias), tmp);
    } else {
      // stack to reg
      assert(dst.first()->is_XMMRegister(), "only expect xmm registers as parameters");
      movflt(dst.first()->as_XMMRegister(), Address(rbp, reg2offset_in(src.first()) + in_stk_bias));
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    assert(src.first()->is_XMMRegister(), "only expect xmm registers as parameters");
    movflt(Address(rsp, reg2offset_out(dst.first()) + out_stk_bias), src.first()->as_XMMRegister());
  } else {
    // reg to reg
    // In theory these overlap but the ordering is such that this is likely a nop
    if ( src.first() != dst.first()) {
      movdbl(dst.first()->as_XMMRegister(),  src.first()->as_XMMRegister());
    }
  }
}

// On 64 bit we will store integer like items to the stack as
// 64 bits items (x86_32/64 abi) even though java would only store
// 32bits for a parameter. On 32bit it will simply be 32 bits
// So this routine will do 32->32 on 32bit and 32->64 on 64bit
void MacroAssembler::move32_64(VMRegPair src, VMRegPair dst, Register tmp, int in_stk_bias, int out_stk_bias) {
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      movslq(tmp, Address(rbp, reg2offset_in(src.first()) + in_stk_bias));
      movq(Address(rsp, reg2offset_out(dst.first()) + out_stk_bias), tmp);
    } else {
      // stack to reg
      movslq(dst.first()->as_Register(), Address(rbp, reg2offset_in(src.first()) + in_stk_bias));
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    // Do we really have to sign extend???
    // __ movslq(src.first()->as_Register(), src.first()->as_Register());
    movq(Address(rsp, reg2offset_out(dst.first()) + out_stk_bias), src.first()->as_Register());
  } else {
    // Do we really have to sign extend???
    // __ movslq(dst.first()->as_Register(), src.first()->as_Register());
    if (dst.first() != src.first()) {
      movq(dst.first()->as_Register(), src.first()->as_Register());
    }
  }
}

void MacroAssembler::move_ptr(VMRegPair src, VMRegPair dst) {
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      movq(rax, Address(rbp, reg2offset_in(src.first())));
      movq(Address(rsp, reg2offset_out(dst.first())), rax);
    } else {
      // stack to reg
      movq(dst.first()->as_Register(), Address(rbp, reg2offset_in(src.first())));
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    movq(Address(rsp, reg2offset_out(dst.first())), src.first()->as_Register());
  } else {
    if (dst.first() != src.first()) {
      movq(dst.first()->as_Register(), src.first()->as_Register());
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

  // must pass a handle. First figure out the location we use as a handle

  Register rHandle = dst.first()->is_stack() ? rax : dst.first()->as_Register();

  // See if oop is null if it is we need no handle

  if (src.first()->is_stack()) {

    // Oop is already on the stack as an argument
    int offset_in_older_frame = src.first()->reg2stack() + SharedRuntime::out_preserve_stack_slots();
    map->set_oop(VMRegImpl::stack2reg(offset_in_older_frame + framesize_in_slots));
    if (is_receiver) {
      *receiver_offset = (offset_in_older_frame + framesize_in_slots) * VMRegImpl::stack_slot_size;
    }

    cmpptr(Address(rbp, reg2offset_in(src.first())), NULL_WORD);
    lea(rHandle, Address(rbp, reg2offset_in(src.first())));
    // conditionally move a null
    cmovptr(Assembler::equal, rHandle, Address(rbp, reg2offset_in(src.first())));
  } else {

    // Oop is in a register we must store it to the space we reserve
    // on the stack for oop_handles and pass a handle if oop is non-null

    const Register rOop = src.first()->as_Register();
    int oop_slot;
    if (rOop == j_rarg0)
      oop_slot = 0;
    else if (rOop == j_rarg1)
      oop_slot = 1;
    else if (rOop == j_rarg2)
      oop_slot = 2;
    else if (rOop == j_rarg3)
      oop_slot = 3;
    else if (rOop == j_rarg4)
      oop_slot = 4;
    else {
      assert(rOop == j_rarg5, "wrong register");
      oop_slot = 5;
    }

    oop_slot = oop_slot * VMRegImpl::slots_per_word + oop_handle_offset;
    int offset = oop_slot*VMRegImpl::stack_slot_size;

    map->set_oop(VMRegImpl::stack2reg(oop_slot));
    // Store oop in handle area, may be null
    movptr(Address(rsp, offset), rOop);
    if (is_receiver) {
      *receiver_offset = offset;
    }

    cmpptr(rOop, NULL_WORD);
    lea(rHandle, Address(rsp, offset));
    // conditionally move a null from the handle area where it was just stored
    cmovptr(Assembler::equal, rHandle, Address(rsp, offset));
  }

  // If arg is on the stack then place it otherwise it is already in correct reg.
  if (dst.first()->is_stack()) {
    movptr(Address(rsp, reg2offset_out(dst.first())), rHandle);
  }
}

void MacroAssembler::addptr(Register dst, int32_t imm32) {
  addq(dst, imm32);
}

void MacroAssembler::addptr(Register dst, Register src) {
  addq(dst, src);
}

void MacroAssembler::addptr(Address dst, Register src) {
  addq(dst, src);
}

void MacroAssembler::addsd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::addsd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::addsd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::addss(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    addss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    addss(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::addpd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::addpd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::addpd(dst, Address(rscratch, 0));
  }
}

// See 8273459.  Function for ensuring 64-byte alignment, intended for stubs only.
// Stub code is generated once and never copied.
// NMethods can't use this because they get copied and we can't force alignment > 32 bytes.
void MacroAssembler::align64() {
  align(64, (uint)(uintptr_t)pc());
}

void MacroAssembler::align32() {
  align(32, (uint)(uintptr_t)pc());
}

void MacroAssembler::align(uint modulus) {
  // 8273459: Ensure alignment is possible with current segment alignment
  assert(modulus <= CodeEntryAlignment, "Alignment must be <= CodeEntryAlignment");
  align(modulus, offset());
}

void MacroAssembler::align(uint modulus, uint target) {
  if (target % modulus != 0) {
    nop(modulus - (target % modulus));
  }
}

void MacroAssembler::push_f(XMMRegister r) {
  subptr(rsp, wordSize);
  movflt(Address(rsp, 0), r);
}

void MacroAssembler::pop_f(XMMRegister r) {
  movflt(r, Address(rsp, 0));
  addptr(rsp, wordSize);
}

void MacroAssembler::push_d(XMMRegister r) {
  subptr(rsp, 2 * wordSize);
  movdbl(Address(rsp, 0), r);
}

void MacroAssembler::pop_d(XMMRegister r) {
  movdbl(r, Address(rsp, 0));
  addptr(rsp, 2 * Interpreter::stackElementSize);
}

void MacroAssembler::push_ppx(Register src) {
  if (VM_Version::supports_apx_f()) {
    pushp(src);
  } else {
    Assembler::push(src);
  }
}

void MacroAssembler::pop_ppx(Register dst) {
  if (VM_Version::supports_apx_f()) {
    popp(dst);
  } else {
    Assembler::pop(dst);
  }
}

void MacroAssembler::andpd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  // Used in sign-masking with aligned address.
  assert((UseAVX > 0) || (((intptr_t)src.target() & 15) == 0), "SSE mode requires address alignment 16 bytes");
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (UseAVX > 2 &&
      (!VM_Version::supports_avx512dq() || !VM_Version::supports_avx512vl()) &&
      (dst->encoding() >= 16)) {
    vpand(dst, dst, src, AVX_512bit, rscratch);
  } else if (reachable(src)) {
    Assembler::andpd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::andpd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::andps(XMMRegister dst, AddressLiteral src, Register rscratch) {
  // Used in sign-masking with aligned address.
  assert((UseAVX > 0) || (((intptr_t)src.target() & 15) == 0), "SSE mode requires address alignment 16 bytes");
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::andps(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::andps(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::andptr(Register dst, int32_t imm32) {
  andq(dst, imm32);
}

void MacroAssembler::andq(Register dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    andq(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    andq(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::atomic_incl(Address counter_addr) {
  lock();
  incrementl(counter_addr);
}

void MacroAssembler::atomic_incl(AddressLiteral counter_addr, Register rscratch) {
  assert(rscratch != noreg || always_reachable(counter_addr), "missing");

  if (reachable(counter_addr)) {
    atomic_incl(as_Address(counter_addr));
  } else {
    lea(rscratch, counter_addr);
    atomic_incl(Address(rscratch, 0));
  }
}

void MacroAssembler::atomic_incq(Address counter_addr) {
  lock();
  incrementq(counter_addr);
}

void MacroAssembler::atomic_incq(AddressLiteral counter_addr, Register rscratch) {
  assert(rscratch != noreg || always_reachable(counter_addr), "missing");

  if (reachable(counter_addr)) {
    atomic_incq(as_Address(counter_addr));
  } else {
    lea(rscratch, counter_addr);
    atomic_incq(Address(rscratch, 0));
  }
}

// Writes to stack successive pages until offset reached to check for
// stack overflow + shadow pages.  This clobbers tmp.
void MacroAssembler::bang_stack_size(Register size, Register tmp) {
  movptr(tmp, rsp);
  // Bang stack for total size given plus shadow page size.
  // Bang one page at a time because large size can bang beyond yellow and
  // red zones.
  Label loop;
  bind(loop);
  movl(Address(tmp, (-(int)os::vm_page_size())), size );
  subptr(tmp, (int)os::vm_page_size());
  subl(size, (int)os::vm_page_size());
  jcc(Assembler::greater, loop);

  // Bang down shadow pages too.
  // At this point, (tmp-0) is the last address touched, so don't
  // touch it again.  (It was touched as (tmp-pagesize) but then tmp
  // was post-decremented.)  Skip this address by starting at i=1, and
  // touch a few more pages below.  N.B.  It is important to touch all
  // the way down including all pages in the shadow zone.
  for (int i = 1; i < ((int)StackOverflow::stack_shadow_zone_size() / (int)os::vm_page_size()); i++) {
    // this could be any sized move but this is can be a debugging crumb
    // so the bigger the better.
    movptr(Address(tmp, (-i*(int)os::vm_page_size())), size );
  }
}

void MacroAssembler::reserved_stack_check() {
  // testing if reserved zone needs to be enabled
  Label no_reserved_zone_enabling;

  cmpptr(rsp, Address(r15_thread, JavaThread::reserved_stack_activation_offset()));
  jcc(Assembler::below, no_reserved_zone_enabling);

  call_VM_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::enable_stack_reserved_zone), r15_thread);
  jump(RuntimeAddress(SharedRuntime::throw_delayed_StackOverflowError_entry()));
  should_not_reach_here();

  bind(no_reserved_zone_enabling);
}

void MacroAssembler::c2bool(Register x) {
  // implements x == 0 ? 0 : 1
  // note: must only look at least-significant byte of x
  //       since C-style booleans are stored in one byte
  //       only! (was bug)
  andl(x, 0xFF);
  setb(Assembler::notZero, x);
}

// Wouldn't need if AddressLiteral version had new name
void MacroAssembler::call(Label& L, relocInfo::relocType rtype) {
  Assembler::call(L, rtype);
}

void MacroAssembler::call(Register entry) {
  Assembler::call(entry);
}

void MacroAssembler::call(AddressLiteral entry, Register rscratch) {
  assert(rscratch != noreg || always_reachable(entry), "missing");

  if (reachable(entry)) {
    Assembler::call_literal(entry.target(), entry.rspec());
  } else {
    lea(rscratch, entry);
    Assembler::call(rscratch);
  }
}

void MacroAssembler::ic_call(address entry, jint method_index) {
  RelocationHolder rh = virtual_call_Relocation::spec(pc(), method_index);
  // Needs full 64-bit immediate for later patching.
  mov64(rax, (int64_t)Universe::non_oop_word());
  call(AddressLiteral(entry, rh));
}

int MacroAssembler::ic_check_size() {
  return UseCompactObjectHeaders ? 17 : 14;
}

int MacroAssembler::ic_check(int end_alignment) {
  Register receiver = j_rarg0;
  Register data = rax;
  Register temp = rscratch1;

  // The UEP of a code blob ensures that the VEP is padded. However, the padding of the UEP is placed
  // before the inline cache check, so we don't have to execute any nop instructions when dispatching
  // through the UEP, yet we can ensure that the VEP is aligned appropriately. That's why we align
  // before the inline cache check here, and not after
  align(end_alignment, offset() + ic_check_size());

  int uep_offset = offset();

  if (UseCompactObjectHeaders) {
    load_narrow_klass_compact(temp, receiver);
    cmpl(temp, Address(data, CompiledICData::speculated_klass_offset()));
  } else if (UseCompressedClassPointers) {
    movl(temp, Address(receiver, oopDesc::klass_offset_in_bytes()));
    cmpl(temp, Address(data, CompiledICData::speculated_klass_offset()));
  } else {
    movptr(temp, Address(receiver, oopDesc::klass_offset_in_bytes()));
    cmpptr(temp, Address(data, CompiledICData::speculated_klass_offset()));
  }

  // if inline cache check fails, then jump to runtime routine
  jump_cc(Assembler::notEqual, RuntimeAddress(SharedRuntime::get_ic_miss_stub()));
  assert((offset() % end_alignment) == 0, "Misaligned verified entry point (%d, %d, %d)", uep_offset, offset(), end_alignment);

  return uep_offset;
}

void MacroAssembler::emit_static_call_stub() {
  // Static stub relocation also tags the Method* in the code-stream.
  mov_metadata(rbx, (Metadata*) nullptr);  // Method is zapped till fixup time.
  // This is recognized as unresolved by relocs/nativeinst/ic code.
  jump(RuntimeAddress(pc()));
}

// Implementation of call_VM versions

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);
  call_VM_helper(oop_result, entry_point, 0, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);
  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 1, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);

  assert_different_registers(arg_1, c_rarg2);

  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 2, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             address entry_point,
                             Register arg_1,
                             Register arg_2,
                             Register arg_3,
                             bool check_exceptions) {
  Label C, E;
  call(C, relocInfo::none);
  jmp(E);

  bind(C);

  assert_different_registers(arg_1, c_rarg2, c_rarg3);
  assert_different_registers(arg_2, c_rarg3);
  pass_arg3(this, arg_3);
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  call_VM_helper(oop_result, entry_point, 3, check_exceptions);
  ret(0);

  bind(E);
}

void MacroAssembler::call_VM(Register oop_result,
                             Register last_java_sp,
                             address entry_point,
                             int number_of_arguments,
                             bool check_exceptions) {
  call_VM_base(oop_result, last_java_sp, entry_point, number_of_arguments, check_exceptions);
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

void MacroAssembler::super_call_VM(Register oop_result,
                                   Register last_java_sp,
                                   address entry_point,
                                   int number_of_arguments,
                                   bool check_exceptions) {
  MacroAssembler::call_VM_base(oop_result, last_java_sp, entry_point, number_of_arguments, check_exceptions);
}

void MacroAssembler::super_call_VM(Register oop_result,
                                   Register last_java_sp,
                                   address entry_point,
                                   Register arg_1,
                                   bool check_exceptions) {
  pass_arg1(this, arg_1);
  super_call_VM(oop_result, last_java_sp, entry_point, 1, check_exceptions);
}

void MacroAssembler::super_call_VM(Register oop_result,
                                   Register last_java_sp,
                                   address entry_point,
                                   Register arg_1,
                                   Register arg_2,
                                   bool check_exceptions) {

  assert_different_registers(arg_1, c_rarg2);
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  super_call_VM(oop_result, last_java_sp, entry_point, 2, check_exceptions);
}

void MacroAssembler::super_call_VM(Register oop_result,
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
  super_call_VM(oop_result, last_java_sp, entry_point, 3, check_exceptions);
}

void MacroAssembler::call_VM_base(Register oop_result,
                                  Register last_java_sp,
                                  address  entry_point,
                                  int      number_of_arguments,
                                  bool     check_exceptions) {
  Register java_thread = r15_thread;

  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = rsp;
  }
  // debugging support
  assert(number_of_arguments >= 0   , "cannot have negative number of arguments");
#ifdef ASSERT
  // TraceBytecodes does not use r12 but saves it over the call, so don't verify
  // r12 is the heapbase.
  if (UseCompressedOops && !TraceBytecodes) verify_heapbase("call_VM_base: heap base corrupted?");
#endif // ASSERT

  assert(java_thread != oop_result  , "cannot use the same register for java_thread & oop_result");
  assert(java_thread != last_java_sp, "cannot use the same register for java_thread & last_java_sp");

  // push java thread (becomes first argument of C function)

  mov(c_rarg0, r15_thread);

  // set last Java frame before call
  assert(last_java_sp != rbp, "can't use ebp/rbp");

  // Only interpreter should have to set fp
  set_last_Java_frame(last_java_sp, rbp, nullptr, rscratch1);

  // do the call, remove parameters
  MacroAssembler::call_VM_leaf_base(entry_point, number_of_arguments);

#ifdef ASSERT
  // Check that thread register is not clobbered.
  guarantee(java_thread != rax, "change this code");
  push(rax);
  { Label L;
    get_thread_slow(rax);
    cmpptr(java_thread, rax);
    jcc(Assembler::equal, L);
    STOP("MacroAssembler::call_VM_base: java_thread not callee saved?");
    bind(L);
  }
  pop(rax);
#endif

  // reset last Java frame
  // Only interpreter should have to clear fp
  reset_last_Java_frame(true);

   // C++ interp handles this in the interpreter
  check_and_handle_popframe();
  check_and_handle_earlyret();

  if (check_exceptions) {
    // check for pending exceptions (java_thread is set upon return)
    cmpptr(Address(r15_thread, Thread::pending_exception_offset()), NULL_WORD);
    // This used to conditionally jump to forward_exception however it is
    // possible if we relocate that the branch will not reach. So we must jump
    // around so we can always reach

    Label ok;
    jcc(Assembler::equal, ok);
    jump(RuntimeAddress(StubRoutines::forward_exception_entry()));
    bind(ok);
  }

  // get oop result if there is one and reset the value in the thread
  if (oop_result->is_valid()) {
    get_vm_result_oop(oop_result);
  }
}

void MacroAssembler::call_VM_helper(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions) {
  // Calculate the value for last_Java_sp somewhat subtle.
  // call_VM does an intermediate call which places a return address on
  // the stack just under the stack pointer as the user finished with it.
  // This allows use to retrieve last_Java_pc from last_Java_sp[-1].

  // We've pushed one address, correct last_Java_sp
  lea(rax, Address(rsp, wordSize));

  call_VM_base(oop_result, rax, entry_point, number_of_arguments, check_exceptions);
}

// Use this method when MacroAssembler version of call_VM_leaf_base() should be called from Interpreter.
void MacroAssembler::call_VM_leaf0(address entry_point) {
  MacroAssembler::call_VM_leaf_base(entry_point, 0);
}

void MacroAssembler::call_VM_leaf(address entry_point, int number_of_arguments) {
  call_VM_leaf_base(entry_point, number_of_arguments);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0) {
  pass_arg0(this, arg_0);
  call_VM_leaf(entry_point, 1);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0, Register arg_1) {

  assert_different_registers(arg_0, c_rarg1);
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  call_VM_leaf(entry_point, 2);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0, Register arg_1, Register arg_2) {
  assert_different_registers(arg_0, c_rarg1, c_rarg2);
  assert_different_registers(arg_1, c_rarg2);
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  call_VM_leaf(entry_point, 3);
}

void MacroAssembler::call_VM_leaf(address entry_point, Register arg_0, Register arg_1, Register arg_2, Register arg_3) {
  assert_different_registers(arg_0, c_rarg1, c_rarg2, c_rarg3);
  assert_different_registers(arg_1, c_rarg2, c_rarg3);
  assert_different_registers(arg_2, c_rarg3);
  pass_arg3(this, arg_3);
  pass_arg2(this, arg_2);
  pass_arg1(this, arg_1);
  pass_arg0(this, arg_0);
  call_VM_leaf(entry_point, 3);
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

void MacroAssembler::get_vm_result_oop(Register oop_result) {
  movptr(oop_result, Address(r15_thread, JavaThread::vm_result_oop_offset()));
  movptr(Address(r15_thread, JavaThread::vm_result_oop_offset()), NULL_WORD);
  verify_oop_msg(oop_result, "broken oop in call_VM_base");
}

void MacroAssembler::get_vm_result_metadata(Register metadata_result) {
  movptr(metadata_result, Address(r15_thread, JavaThread::vm_result_metadata_offset()));
  movptr(Address(r15_thread, JavaThread::vm_result_metadata_offset()), NULL_WORD);
}

void MacroAssembler::check_and_handle_earlyret() {
}

void MacroAssembler::check_and_handle_popframe() {
}

void MacroAssembler::cmp32(AddressLiteral src1, int32_t imm, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src1), "missing");

  if (reachable(src1)) {
    cmpl(as_Address(src1), imm);
  } else {
    lea(rscratch, src1);
    cmpl(Address(rscratch, 0), imm);
  }
}

void MacroAssembler::cmp32(Register src1, AddressLiteral src2, Register rscratch) {
  assert(!src2.is_lval(), "use cmpptr");
  assert(rscratch != noreg || always_reachable(src2), "missing");

  if (reachable(src2)) {
    cmpl(src1, as_Address(src2));
  } else {
    lea(rscratch, src2);
    cmpl(src1, Address(rscratch, 0));
  }
}

void MacroAssembler::cmp32(Register src1, int32_t imm) {
  Assembler::cmpl(src1, imm);
}

void MacroAssembler::cmp32(Register src1, Address src2) {
  Assembler::cmpl(src1, src2);
}

void MacroAssembler::cmpsd2int(XMMRegister opr1, XMMRegister opr2, Register dst, bool unordered_is_less) {
  ucomisd(opr1, opr2);

  Label L;
  if (unordered_is_less) {
    movl(dst, -1);
    jcc(Assembler::parity, L);
    jcc(Assembler::below , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    increment(dst);
  } else { // unordered is greater
    movl(dst, 1);
    jcc(Assembler::parity, L);
    jcc(Assembler::above , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    decrementl(dst);
  }
  bind(L);
}

void MacroAssembler::cmpss2int(XMMRegister opr1, XMMRegister opr2, Register dst, bool unordered_is_less) {
  ucomiss(opr1, opr2);

  Label L;
  if (unordered_is_less) {
    movl(dst, -1);
    jcc(Assembler::parity, L);
    jcc(Assembler::below , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    increment(dst);
  } else { // unordered is greater
    movl(dst, 1);
    jcc(Assembler::parity, L);
    jcc(Assembler::above , L);
    movl(dst, 0);
    jcc(Assembler::equal , L);
    decrementl(dst);
  }
  bind(L);
}


void MacroAssembler::cmp8(AddressLiteral src1, int imm, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src1), "missing");

  if (reachable(src1)) {
    cmpb(as_Address(src1), imm);
  } else {
    lea(rscratch, src1);
    cmpb(Address(rscratch, 0), imm);
  }
}

void MacroAssembler::cmpptr(Register src1, AddressLiteral src2, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src2), "missing");

  if (src2.is_lval()) {
    movptr(rscratch, src2);
    Assembler::cmpq(src1, rscratch);
  } else if (reachable(src2)) {
    cmpq(src1, as_Address(src2));
  } else {
    lea(rscratch, src2);
    Assembler::cmpq(src1, Address(rscratch, 0));
  }
}

void MacroAssembler::cmpptr(Address src1, AddressLiteral src2, Register rscratch) {
  assert(src2.is_lval(), "not a mem-mem compare");
  // moves src2's literal address
  movptr(rscratch, src2);
  Assembler::cmpq(src1, rscratch);
}

void MacroAssembler::cmpoop(Register src1, Register src2) {
  cmpptr(src1, src2);
}

void MacroAssembler::cmpoop(Register src1, Address src2) {
  cmpptr(src1, src2);
}

void MacroAssembler::cmpoop(Register src1, jobject src2, Register rscratch) {
  movoop(rscratch, src2);
  cmpptr(src1, rscratch);
}

void MacroAssembler::locked_cmpxchgptr(Register reg, AddressLiteral adr, Register rscratch) {
  assert(rscratch != noreg || always_reachable(adr), "missing");

  if (reachable(adr)) {
    lock();
    cmpxchgptr(reg, as_Address(adr));
  } else {
    lea(rscratch, adr);
    lock();
    cmpxchgptr(reg, Address(rscratch, 0));
  }
}

void MacroAssembler::cmpxchgptr(Register reg, Address adr) {
  cmpxchgq(reg, adr);
}

void MacroAssembler::comisd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::comisd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::comisd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::comiss(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::comiss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::comiss(dst, Address(rscratch, 0));
  }
}


void MacroAssembler::cond_inc32(Condition cond, AddressLiteral counter_addr, Register rscratch) {
  assert(rscratch != noreg || always_reachable(counter_addr), "missing");

  Condition negated_cond = negate_condition(cond);
  Label L;
  jcc(negated_cond, L);
  pushf(); // Preserve flags
  atomic_incl(counter_addr, rscratch);
  popf();
  bind(L);
}

int MacroAssembler::corrected_idivl(Register reg) {
  // Full implementation of Java idiv and irem; checks for
  // special case as described in JVM spec., p.243 & p.271.
  // The function returns the (pc) offset of the idivl
  // instruction - may be needed for implicit exceptions.
  //
  //         normal case                           special case
  //
  // input : rax,: dividend                         min_int
  //         reg: divisor   (may not be rax,/rdx)   -1
  //
  // output: rax,: quotient  (= rax, idiv reg)       min_int
  //         rdx: remainder (= rax, irem reg)       0
  assert(reg != rax && reg != rdx, "reg cannot be rax, or rdx register");
  const int min_int = 0x80000000;
  Label normal_case, special_case;

  // check for special case
  cmpl(rax, min_int);
  jcc(Assembler::notEqual, normal_case);
  xorl(rdx, rdx); // prepare rdx for possible special case (where remainder = 0)
  cmpl(reg, -1);
  jcc(Assembler::equal, special_case);

  // handle normal case
  bind(normal_case);
  cdql();
  int idivl_offset = offset();
  idivl(reg);

  // normal and special case exit
  bind(special_case);

  return idivl_offset;
}



void MacroAssembler::decrementl(Register reg, int value) {
  if (value == min_jint) {subl(reg, value) ; return; }
  if (value <  0) { incrementl(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decl(reg) ; return; }
  /* else */      { subl(reg, value)       ; return; }
}

void MacroAssembler::decrementl(Address dst, int value) {
  if (value == min_jint) {subl(dst, value) ; return; }
  if (value <  0) { incrementl(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { decl(dst) ; return; }
  /* else */      { subl(dst, value)       ; return; }
}

void MacroAssembler::division_with_shift (Register reg, int shift_value) {
  assert(shift_value > 0, "illegal shift value");
  Label _is_positive;
  testl (reg, reg);
  jcc (Assembler::positive, _is_positive);
  int offset = (1 << shift_value) - 1 ;

  if (offset == 1) {
    incrementl(reg);
  } else {
    addl(reg, offset);
  }

  bind (_is_positive);
  sarl(reg, shift_value);
}

void MacroAssembler::divsd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::divsd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::divsd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::divss(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::divss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::divss(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::enter() {
  push(rbp);
  mov(rbp, rsp);
}

void MacroAssembler::post_call_nop() {
  if (!Continuations::enabled()) {
    return;
  }
  InstructionMark im(this);
  relocate(post_call_nop_Relocation::spec());
  InlineSkippedInstructionsCounter skipCounter(this);
  emit_int8((uint8_t)0x0f);
  emit_int8((uint8_t)0x1f);
  emit_int8((uint8_t)0x84);
  emit_int8((uint8_t)0x00);
  emit_int32(0x00);
}

void MacroAssembler::mulpd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");
  if (reachable(src)) {
    Assembler::mulpd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::mulpd(dst, Address(rscratch, 0));
  }
}

// dst = c = a * b + c
void MacroAssembler::fmad(XMMRegister dst, XMMRegister a, XMMRegister b, XMMRegister c) {
  Assembler::vfmadd231sd(c, a, b);
  if (dst != c) {
    movdbl(dst, c);
  }
}

// dst = c = a * b + c
void MacroAssembler::fmaf(XMMRegister dst, XMMRegister a, XMMRegister b, XMMRegister c) {
  Assembler::vfmadd231ss(c, a, b);
  if (dst != c) {
    movflt(dst, c);
  }
}

// dst = c = a * b + c
void MacroAssembler::vfmad(XMMRegister dst, XMMRegister a, XMMRegister b, XMMRegister c, int vector_len) {
  Assembler::vfmadd231pd(c, a, b, vector_len);
  if (dst != c) {
    vmovdqu(dst, c);
  }
}

// dst = c = a * b + c
void MacroAssembler::vfmaf(XMMRegister dst, XMMRegister a, XMMRegister b, XMMRegister c, int vector_len) {
  Assembler::vfmadd231ps(c, a, b, vector_len);
  if (dst != c) {
    vmovdqu(dst, c);
  }
}

// dst = c = a * b + c
void MacroAssembler::vfmad(XMMRegister dst, XMMRegister a, Address b, XMMRegister c, int vector_len) {
  Assembler::vfmadd231pd(c, a, b, vector_len);
  if (dst != c) {
    vmovdqu(dst, c);
  }
}

// dst = c = a * b + c
void MacroAssembler::vfmaf(XMMRegister dst, XMMRegister a, Address b, XMMRegister c, int vector_len) {
  Assembler::vfmadd231ps(c, a, b, vector_len);
  if (dst != c) {
    vmovdqu(dst, c);
  }
}

void MacroAssembler::incrementl(AddressLiteral dst, Register rscratch) {
  assert(rscratch != noreg || always_reachable(dst), "missing");

  if (reachable(dst)) {
    incrementl(as_Address(dst));
  } else {
    lea(rscratch, dst);
    incrementl(Address(rscratch, 0));
  }
}

void MacroAssembler::incrementl(ArrayAddress dst, Register rscratch) {
  incrementl(as_Address(dst, rscratch));
}

void MacroAssembler::incrementl(Register reg, int value) {
  if (value == min_jint) {addl(reg, value) ; return; }
  if (value <  0) { decrementl(reg, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incl(reg) ; return; }
  /* else */      { addl(reg, value)       ; return; }
}

void MacroAssembler::incrementl(Address dst, int value) {
  if (value == min_jint) {addl(dst, value) ; return; }
  if (value <  0) { decrementl(dst, -value); return; }
  if (value == 0) {                        ; return; }
  if (value == 1 && UseIncDec) { incl(dst) ; return; }
  /* else */      { addl(dst, value)       ; return; }
}

void MacroAssembler::jump(AddressLiteral dst, Register rscratch) {
  assert(rscratch != noreg || always_reachable(dst), "missing");
  assert(!dst.rspec().reloc()->is_data(), "should not use ExternalAddress for jump");
  if (reachable(dst)) {
    jmp_literal(dst.target(), dst.rspec());
  } else {
    lea(rscratch, dst);
    jmp(rscratch);
  }
}

void MacroAssembler::jump_cc(Condition cc, AddressLiteral dst, Register rscratch) {
  assert(rscratch != noreg || always_reachable(dst), "missing");
  assert(!dst.rspec().reloc()->is_data(), "should not use ExternalAddress for jump_cc");
  if (reachable(dst)) {
    InstructionMark im(this);
    relocate(dst.reloc());
    const int short_size = 2;
    const int long_size = 6;
    int offs = (intptr_t)dst.target() - ((intptr_t)pc());
    if (dst.reloc() == relocInfo::none && is8bit(offs - short_size)) {
      // 0111 tttn #8-bit disp
      emit_int8(0x70 | cc);
      emit_int8((offs - short_size) & 0xFF);
    } else {
      // 0000 1111 1000 tttn #32-bit disp
      emit_int8(0x0F);
      emit_int8((unsigned char)(0x80 | cc));
      emit_int32(offs - long_size);
    }
  } else {
#ifdef ASSERT
    warning("reversing conditional branch");
#endif /* ASSERT */
    Label skip;
    jccb(reverse[cc], skip);
    lea(rscratch, dst);
    Assembler::jmp(rscratch);
    bind(skip);
  }
}

void MacroAssembler::cmp32_mxcsr_std(Address mxcsr_save, Register tmp, Register rscratch) {
  ExternalAddress mxcsr_std(StubRoutines::x86::addr_mxcsr_std());
  assert(rscratch != noreg || always_reachable(mxcsr_std), "missing");

  stmxcsr(mxcsr_save);
  movl(tmp, mxcsr_save);
  if (EnableX86ECoreOpts) {
    // The mxcsr_std has status bits set for performance on ECore
    orl(tmp, 0x003f);
  } else {
    // Mask out status bits (only check control and mask bits)
    andl(tmp, 0xFFC0);
  }
  cmp32(tmp, mxcsr_std, rscratch);
}

void MacroAssembler::ldmxcsr(AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::ldmxcsr(as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::ldmxcsr(Address(rscratch, 0));
  }
}

int MacroAssembler::load_signed_byte(Register dst, Address src) {
  int off = offset();
  movsbl(dst, src); // movsxb
  return off;
}

// Note: load_signed_short used to be called load_signed_word.
// Although the 'w' in x86 opcodes refers to the term "word" in the assembler
// manual, which means 16 bits, that usage is found nowhere in HotSpot code.
// The term "word" in HotSpot means a 32- or 64-bit machine word.
int MacroAssembler::load_signed_short(Register dst, Address src) {
  // This is dubious to me since it seems safe to do a signed 16 => 64 bit
  // version but this is what 64bit has always done. This seems to imply
  // that users are only using 32bits worth.
  int off = offset();
  movswl(dst, src); // movsxw
  return off;
}

int MacroAssembler::load_unsigned_byte(Register dst, Address src) {
  // According to Intel Doc. AP-526, "Zero-Extension of Short", p.16,
  // and "3.9 Partial Register Penalties", p. 22).
  int off = offset();
  movzbl(dst, src); // movzxb
  return off;
}

// Note: load_unsigned_short used to be called load_unsigned_word.
int MacroAssembler::load_unsigned_short(Register dst, Address src) {
  // According to Intel Doc. AP-526, "Zero-Extension of Short", p.16,
  // and "3.9 Partial Register Penalties", p. 22).
  int off = offset();
  movzwl(dst, src); // movzxw
  return off;
}

void MacroAssembler::load_sized_value(Register dst, Address src, size_t size_in_bytes, bool is_signed, Register dst2) {
  switch (size_in_bytes) {
  case  8:  movq(dst, src); break;
  case  4:  movl(dst, src); break;
  case  2:  is_signed ? load_signed_short(dst, src) : load_unsigned_short(dst, src); break;
  case  1:  is_signed ? load_signed_byte( dst, src) : load_unsigned_byte( dst, src); break;
  default:  ShouldNotReachHere();
  }
}

void MacroAssembler::store_sized_value(Address dst, Register src, size_t size_in_bytes, Register src2) {
  switch (size_in_bytes) {
  case  8:  movq(dst, src); break;
  case  4:  movl(dst, src); break;
  case  2:  movw(dst, src); break;
  case  1:  movb(dst, src); break;
  default:  ShouldNotReachHere();
  }
}

void MacroAssembler::mov32(AddressLiteral dst, Register src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(dst), "missing");

  if (reachable(dst)) {
    movl(as_Address(dst), src);
  } else {
    lea(rscratch, dst);
    movl(Address(rscratch, 0), src);
  }
}

void MacroAssembler::mov32(Register dst, AddressLiteral src) {
  if (reachable(src)) {
    movl(dst, as_Address(src));
  } else {
    lea(dst, src);
    movl(dst, Address(dst, 0));
  }
}

// C++ bool manipulation

void MacroAssembler::movbool(Register dst, Address src) {
  if(sizeof(bool) == 1)
    movb(dst, src);
  else if(sizeof(bool) == 2)
    movw(dst, src);
  else if(sizeof(bool) == 4)
    movl(dst, src);
  else
    // unsupported
    ShouldNotReachHere();
}

void MacroAssembler::movbool(Address dst, bool boolconst) {
  if(sizeof(bool) == 1)
    movb(dst, (int) boolconst);
  else if(sizeof(bool) == 2)
    movw(dst, (int) boolconst);
  else if(sizeof(bool) == 4)
    movl(dst, (int) boolconst);
  else
    // unsupported
    ShouldNotReachHere();
}

void MacroAssembler::movbool(Address dst, Register src) {
  if(sizeof(bool) == 1)
    movb(dst, src);
  else if(sizeof(bool) == 2)
    movw(dst, src);
  else if(sizeof(bool) == 4)
    movl(dst, src);
  else
    // unsupported
    ShouldNotReachHere();
}

void MacroAssembler::movdl(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    movdl(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    movdl(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::movq(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    movq(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    movq(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::movdbl(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    if (UseXmmLoadAndClearUpper) {
      movsd (dst, as_Address(src));
    } else {
      movlpd(dst, as_Address(src));
    }
  } else {
    lea(rscratch, src);
    if (UseXmmLoadAndClearUpper) {
      movsd (dst, Address(rscratch, 0));
    } else {
      movlpd(dst, Address(rscratch, 0));
    }
  }
}

void MacroAssembler::movflt(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    movss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    movss(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::movptr(Register dst, Register src) {
  movq(dst, src);
}

void MacroAssembler::movptr(Register dst, Address src) {
  movq(dst, src);
}

// src should NEVER be a real pointer. Use AddressLiteral for true pointers
void MacroAssembler::movptr(Register dst, intptr_t src) {
  if (is_uimm32(src)) {
    movl(dst, checked_cast<uint32_t>(src));
  } else if (is_simm32(src)) {
    movq(dst, checked_cast<int32_t>(src));
  } else {
    mov64(dst, src);
  }
}

void MacroAssembler::movptr(Address dst, Register src) {
  movq(dst, src);
}

void MacroAssembler::movptr(Address dst, int32_t src) {
  movslq(dst, src);
}

void MacroAssembler::movdqu(Address dst, XMMRegister src) {
  assert(((src->encoding() < 16) || VM_Version::supports_avx512vl()),"XMM register should be 0-15");
  Assembler::movdqu(dst, src);
}

void MacroAssembler::movdqu(XMMRegister dst, Address src) {
  assert(((dst->encoding() < 16) || VM_Version::supports_avx512vl()),"XMM register should be 0-15");
  Assembler::movdqu(dst, src);
}

void MacroAssembler::movdqu(XMMRegister dst, XMMRegister src) {
  assert(((dst->encoding() < 16  && src->encoding() < 16) || VM_Version::supports_avx512vl()),"XMM register should be 0-15");
  Assembler::movdqu(dst, src);
}

void MacroAssembler::movdqu(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    movdqu(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    movdqu(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::vmovdqu(Address dst, XMMRegister src) {
  assert(((src->encoding() < 16) || VM_Version::supports_avx512vl()),"XMM register should be 0-15");
  Assembler::vmovdqu(dst, src);
}

void MacroAssembler::vmovdqu(XMMRegister dst, Address src) {
  assert(((dst->encoding() < 16) || VM_Version::supports_avx512vl()),"XMM register should be 0-15");
  Assembler::vmovdqu(dst, src);
}

void MacroAssembler::vmovdqu(XMMRegister dst, XMMRegister src) {
  assert(((dst->encoding() < 16  && src->encoding() < 16) || VM_Version::supports_avx512vl()),"XMM register should be 0-15");
  Assembler::vmovdqu(dst, src);
}

void MacroAssembler::vmovdqu(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vmovdqu(dst, as_Address(src));
  }
  else {
    lea(rscratch, src);
    vmovdqu(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::vmovdqu(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (vector_len == AVX_512bit) {
    evmovdquq(dst, src, AVX_512bit, rscratch);
  } else if (vector_len == AVX_256bit) {
    vmovdqu(dst, src, rscratch);
  } else {
    movdqu(dst, src, rscratch);
  }
}

void MacroAssembler::vmovdqu(XMMRegister dst, XMMRegister src, int vector_len) {
  if (vector_len == AVX_512bit) {
    evmovdquq(dst, src, AVX_512bit);
  } else if (vector_len == AVX_256bit) {
    vmovdqu(dst, src);
  } else {
    movdqu(dst, src);
  }
}

void MacroAssembler::vmovdqu(Address dst, XMMRegister src, int vector_len) {
  if (vector_len == AVX_512bit) {
    evmovdquq(dst, src, AVX_512bit);
  } else if (vector_len == AVX_256bit) {
    vmovdqu(dst, src);
  } else {
    movdqu(dst, src);
  }
}

void MacroAssembler::vmovdqu(XMMRegister dst, Address src, int vector_len) {
  if (vector_len == AVX_512bit) {
    evmovdquq(dst, src, AVX_512bit);
  } else if (vector_len == AVX_256bit) {
    vmovdqu(dst, src);
  } else {
    movdqu(dst, src);
  }
}

void MacroAssembler::vmovdqa(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vmovdqa(dst, as_Address(src));
  }
  else {
    lea(rscratch, src);
    vmovdqa(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::vmovdqa(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (vector_len == AVX_512bit) {
    evmovdqaq(dst, src, AVX_512bit, rscratch);
  } else if (vector_len == AVX_256bit) {
    vmovdqa(dst, src, rscratch);
  } else {
    movdqa(dst, src, rscratch);
  }
}

void MacroAssembler::kmov(KRegister dst, Address src) {
  if (VM_Version::supports_avx512bw()) {
    kmovql(dst, src);
  } else {
    assert(VM_Version::supports_evex(), "");
    kmovwl(dst, src);
  }
}

void MacroAssembler::kmov(Address dst, KRegister src) {
  if (VM_Version::supports_avx512bw()) {
    kmovql(dst, src);
  } else {
    assert(VM_Version::supports_evex(), "");
    kmovwl(dst, src);
  }
}

void MacroAssembler::kmov(KRegister dst, KRegister src) {
  if (VM_Version::supports_avx512bw()) {
    kmovql(dst, src);
  } else {
    assert(VM_Version::supports_evex(), "");
    kmovwl(dst, src);
  }
}

void MacroAssembler::kmov(Register dst, KRegister src) {
  if (VM_Version::supports_avx512bw()) {
    kmovql(dst, src);
  } else {
    assert(VM_Version::supports_evex(), "");
    kmovwl(dst, src);
  }
}

void MacroAssembler::kmov(KRegister dst, Register src) {
  if (VM_Version::supports_avx512bw()) {
    kmovql(dst, src);
  } else {
    assert(VM_Version::supports_evex(), "");
    kmovwl(dst, src);
  }
}

void MacroAssembler::kmovql(KRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    kmovql(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    kmovql(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::kmovwl(KRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    kmovwl(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    kmovwl(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::evmovdqub(XMMRegister dst, KRegister mask, AddressLiteral src, bool merge,
                               int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evmovdqub(dst, mask, as_Address(src), merge, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evmovdqub(dst, mask, Address(rscratch, 0), merge, vector_len);
  }
}

void MacroAssembler::evmovdquw(XMMRegister dst, KRegister mask, AddressLiteral src, bool merge,
                               int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evmovdquw(dst, mask, as_Address(src), merge, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evmovdquw(dst, mask, Address(rscratch, 0), merge, vector_len);
  }
}

void MacroAssembler::evmovdqul(XMMRegister dst, KRegister mask, AddressLiteral src, bool merge, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evmovdqul(dst, mask, as_Address(src), merge, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evmovdqul(dst, mask, Address(rscratch, 0), merge, vector_len);
  }
}

void MacroAssembler::evmovdquq(XMMRegister dst, KRegister mask, AddressLiteral src, bool merge, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evmovdquq(dst, mask, as_Address(src), merge, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evmovdquq(dst, mask, Address(rscratch, 0), merge, vector_len);
  }
}

void MacroAssembler::evmovdquq(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evmovdquq(dst, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evmovdquq(dst, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::evmovdqaq(XMMRegister dst, KRegister mask, AddressLiteral src, bool merge, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evmovdqaq(dst, mask, as_Address(src), merge, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evmovdqaq(dst, mask, Address(rscratch, 0), merge, vector_len);
  }
}

void MacroAssembler::evmovdqaq(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evmovdqaq(dst, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evmovdqaq(dst, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::movapd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::movapd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::movapd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::movdqa(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::movdqa(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::movdqa(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::movsd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::movsd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::movsd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::movss(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::movss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::movss(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::movddup(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::movddup(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::movddup(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::vmovddup(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vmovddup(dst, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vmovddup(dst, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::mulsd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::mulsd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::mulsd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::mulss(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::mulss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::mulss(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::null_check(Register reg, int offset) {
  if (needs_explicit_null_check(offset)) {
    // provoke OS null exception if reg is null by
    // accessing M[reg] w/o changing any (non-CC) registers
    // NOTE: cmpl is plenty here to provoke a segv
    cmpptr(rax, Address(reg, 0));
    // Note: should probably use testl(rax, Address(reg, 0));
    //       may be shorter code (however, this version of
    //       testl needs to be implemented first)
  } else {
    // nothing to do, (later) access of M[reg + offset]
    // will provoke OS null exception if reg is null
  }
}

void MacroAssembler::os_breakpoint() {
  // instead of directly emitting a breakpoint, call os:breakpoint for better debugability
  // (e.g., MSVC can't call ps() otherwise)
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, os::breakpoint)));
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

#define XSTATE_BV 0x200

void MacroAssembler::pop_CPU_state() {
  pop_FPU_state();
  pop_IU_state();
}

void MacroAssembler::pop_FPU_state() {
  fxrstor(Address(rsp, 0));
  addptr(rsp, FPUStateSizeInWords * wordSize);
}

void MacroAssembler::pop_IU_state() {
  popa();
  addq(rsp, 8);
  popf();
}

// Save Integer and Float state
// Warning: Stack must be 16 byte aligned (64bit)
void MacroAssembler::push_CPU_state() {
  push_IU_state();
  push_FPU_state();
}

void MacroAssembler::push_FPU_state() {
  subptr(rsp, FPUStateSizeInWords * wordSize);
  fxsave(Address(rsp, 0));
}

void MacroAssembler::push_IU_state() {
  // Push flags first because pusha kills them
  pushf();
  // Make sure rsp stays 16-byte aligned
  subq(rsp, 8);
  pusha();
}

void MacroAssembler::push_cont_fastpath() {
  if (!Continuations::enabled()) return;

  Label L_done;
  cmpptr(rsp, Address(r15_thread, JavaThread::cont_fastpath_offset()));
  jccb(Assembler::belowEqual, L_done);
  movptr(Address(r15_thread, JavaThread::cont_fastpath_offset()), rsp);
  bind(L_done);
}

void MacroAssembler::pop_cont_fastpath() {
  if (!Continuations::enabled()) return;

  Label L_done;
  cmpptr(rsp, Address(r15_thread, JavaThread::cont_fastpath_offset()));
  jccb(Assembler::below, L_done);
  movptr(Address(r15_thread, JavaThread::cont_fastpath_offset()), 0);
  bind(L_done);
}

#ifdef ASSERT
void MacroAssembler::stop_if_in_cont(Register cont, const char* name) {
  Label no_cont;
  movptr(cont, Address(r15_thread, JavaThread::cont_entry_offset()));
  testl(cont, cont);
  jcc(Assembler::zero, no_cont);
  stop(name);
  bind(no_cont);
}
#endif

void MacroAssembler::reset_last_Java_frame(bool clear_fp) { // determine java_thread register
  // we must set sp to zero to clear frame
  movptr(Address(r15_thread, JavaThread::last_Java_sp_offset()), NULL_WORD);
  // must clear fp, so that compiled frames are not confused; it is
  // possible that we need it only for debugging
  if (clear_fp) {
    movptr(Address(r15_thread, JavaThread::last_Java_fp_offset()), NULL_WORD);
  }
  // Always clear the pc because it could have been set by make_walkable()
  movptr(Address(r15_thread, JavaThread::last_Java_pc_offset()), NULL_WORD);
  vzeroupper();
}

void MacroAssembler::round_to(Register reg, int modulus) {
  addptr(reg, modulus - 1);
  andptr(reg, -modulus);
}

void MacroAssembler::safepoint_poll(Label& slow_path, bool at_return, bool in_nmethod) {
  if (at_return) {
    // Note that when in_nmethod is set, the stack pointer is incremented before the poll. Therefore,
    // we may safely use rsp instead to perform the stack watermark check.
    cmpptr(in_nmethod ? rsp : rbp, Address(r15_thread, JavaThread::polling_word_offset()));
    jcc(Assembler::above, slow_path);
    return;
  }
  testb(Address(r15_thread, JavaThread::polling_word_offset()), SafepointMechanism::poll_bit());
  jcc(Assembler::notZero, slow_path); // handshake bit set implies poll
}

// Calls to C land
//
// When entering C land, the rbp, & rsp of the last Java frame have to be recorded
// in the (thread-local) JavaThread object. When leaving C land, the last Java fp
// has to be reset to 0. This is required to allow proper stack traversal.
void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         address  last_java_pc,
                                         Register rscratch) {
  vzeroupper();
  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = rsp;
  }
  // last_java_fp is optional
  if (last_java_fp->is_valid()) {
    movptr(Address(r15_thread, JavaThread::last_Java_fp_offset()), last_java_fp);
  }
  // last_java_pc is optional
  if (last_java_pc != nullptr) {
    Address java_pc(r15_thread,
                    JavaThread::frame_anchor_offset() + JavaFrameAnchor::last_Java_pc_offset());
    lea(java_pc, InternalAddress(last_java_pc), rscratch);
  }
  movptr(Address(r15_thread, JavaThread::last_Java_sp_offset()), last_java_sp);
}

void MacroAssembler::set_last_Java_frame(Register last_java_sp,
                                         Register last_java_fp,
                                         Label &L,
                                         Register scratch) {
  lea(scratch, L);
  movptr(Address(r15_thread, JavaThread::last_Java_pc_offset()), scratch);
  set_last_Java_frame(last_java_sp, last_java_fp, nullptr, scratch);
}

void MacroAssembler::shlptr(Register dst, int imm8) {
  shlq(dst, imm8);
}

void MacroAssembler::shrptr(Register dst, int imm8) {
  shrq(dst, imm8);
}

void MacroAssembler::sign_extend_byte(Register reg) {
  movsbl(reg, reg); // movsxb
}

void MacroAssembler::sign_extend_short(Register reg) {
  movswl(reg, reg); // movsxw
}

void MacroAssembler::testl(Address dst, int32_t imm32) {
  if (imm32 >= 0 && is8bit(imm32)) {
    testb(dst, imm32);
  } else {
    Assembler::testl(dst, imm32);
  }
}

void MacroAssembler::testl(Register dst, int32_t imm32) {
  if (imm32 >= 0 && is8bit(imm32) && dst->has_byte_register()) {
    testb(dst, imm32);
  } else {
    Assembler::testl(dst, imm32);
  }
}

void MacroAssembler::testl(Register dst, AddressLiteral src) {
  assert(always_reachable(src), "Address should be reachable");
  testl(dst, as_Address(src));
}

void MacroAssembler::testq(Address dst, int32_t imm32) {
  if (imm32 >= 0) {
    testl(dst, imm32);
  } else {
    Assembler::testq(dst, imm32);
  }
}

void MacroAssembler::testq(Register dst, int32_t imm32) {
  if (imm32 >= 0) {
    testl(dst, imm32);
  } else {
    Assembler::testq(dst, imm32);
  }
}

void MacroAssembler::pcmpeqb(XMMRegister dst, XMMRegister src) {
  assert(((dst->encoding() < 16 && src->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::pcmpeqb(dst, src);
}

void MacroAssembler::pcmpeqw(XMMRegister dst, XMMRegister src) {
  assert(((dst->encoding() < 16 && src->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::pcmpeqw(dst, src);
}

void MacroAssembler::pcmpestri(XMMRegister dst, Address src, int imm8) {
  assert((dst->encoding() < 16),"XMM register should be 0-15");
  Assembler::pcmpestri(dst, src, imm8);
}

void MacroAssembler::pcmpestri(XMMRegister dst, XMMRegister src, int imm8) {
  assert((dst->encoding() < 16 && src->encoding() < 16),"XMM register should be 0-15");
  Assembler::pcmpestri(dst, src, imm8);
}

void MacroAssembler::pmovzxbw(XMMRegister dst, XMMRegister src) {
  assert(((dst->encoding() < 16 && src->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::pmovzxbw(dst, src);
}

void MacroAssembler::pmovzxbw(XMMRegister dst, Address src) {
  assert(((dst->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::pmovzxbw(dst, src);
}

void MacroAssembler::pmovmskb(Register dst, XMMRegister src) {
  assert((src->encoding() < 16),"XMM register should be 0-15");
  Assembler::pmovmskb(dst, src);
}

void MacroAssembler::ptest(XMMRegister dst, XMMRegister src) {
  assert((dst->encoding() < 16 && src->encoding() < 16),"XMM register should be 0-15");
  Assembler::ptest(dst, src);
}

void MacroAssembler::sqrtss(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::sqrtss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::sqrtss(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::subsd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::subsd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::subsd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::roundsd(XMMRegister dst, AddressLiteral src, int32_t rmode, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::roundsd(dst, as_Address(src), rmode);
  } else {
    lea(rscratch, src);
    Assembler::roundsd(dst, Address(rscratch, 0), rmode);
  }
}

void MacroAssembler::subss(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::subss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::subss(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::ucomisd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::ucomisd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::ucomisd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::vucomxsd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vucomxsd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::vucomxsd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::ucomiss(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::ucomiss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::ucomiss(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::vucomxss(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vucomxss(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::vucomxss(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::xorpd(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  // Used in sign-bit flipping with aligned address.
  assert((UseAVX > 0) || (((intptr_t)src.target() & 15) == 0), "SSE mode requires address alignment 16 bytes");

  if (UseAVX > 2 &&
      (!VM_Version::supports_avx512dq() || !VM_Version::supports_avx512vl()) &&
      (dst->encoding() >= 16)) {
    vpxor(dst, dst, src, Assembler::AVX_512bit, rscratch);
  } else if (reachable(src)) {
    Assembler::xorpd(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::xorpd(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::xorpd(XMMRegister dst, XMMRegister src) {
  if (UseAVX > 2 &&
      (!VM_Version::supports_avx512dq() || !VM_Version::supports_avx512vl()) &&
      ((dst->encoding() >= 16) || (src->encoding() >= 16))) {
    Assembler::vpxor(dst, dst, src, Assembler::AVX_512bit);
  } else {
    Assembler::xorpd(dst, src);
  }
}

void MacroAssembler::xorps(XMMRegister dst, XMMRegister src) {
  if (UseAVX > 2 &&
      (!VM_Version::supports_avx512dq() || !VM_Version::supports_avx512vl()) &&
      ((dst->encoding() >= 16) || (src->encoding() >= 16))) {
    Assembler::vpxor(dst, dst, src, Assembler::AVX_512bit);
  } else {
    Assembler::xorps(dst, src);
  }
}

void MacroAssembler::xorps(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  // Used in sign-bit flipping with aligned address.
  assert((UseAVX > 0) || (((intptr_t)src.target() & 15) == 0), "SSE mode requires address alignment 16 bytes");

  if (UseAVX > 2 &&
      (!VM_Version::supports_avx512dq() || !VM_Version::supports_avx512vl()) &&
      (dst->encoding() >= 16)) {
    vpxor(dst, dst, src, Assembler::AVX_512bit, rscratch);
  } else if (reachable(src)) {
    Assembler::xorps(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::xorps(dst, Address(rscratch, 0));
  }
}

void MacroAssembler::pshufb(XMMRegister dst, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  // Used in sign-bit flipping with aligned address.
  bool aligned_adr = (((intptr_t)src.target() & 15) == 0);
  assert((UseAVX > 0) || aligned_adr, "SSE mode requires address alignment 16 bytes");
  if (reachable(src)) {
    Assembler::pshufb(dst, as_Address(src));
  } else {
    lea(rscratch, src);
    Assembler::pshufb(dst, Address(rscratch, 0));
  }
}

// AVX 3-operands instructions

void MacroAssembler::vaddsd(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vaddsd(dst, nds, as_Address(src));
  } else {
    lea(rscratch, src);
    vaddsd(dst, nds, Address(rscratch, 0));
  }
}

void MacroAssembler::vaddss(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vaddss(dst, nds, as_Address(src));
  } else {
    lea(rscratch, src);
    vaddss(dst, nds, Address(rscratch, 0));
  }
}

void MacroAssembler::vpaddb(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(UseAVX > 0, "requires some form of AVX");
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vpaddb(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vpaddb(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vpaddd(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(UseAVX > 0, "requires some form of AVX");
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vpaddd(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vpaddd(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vabsss(XMMRegister dst, XMMRegister nds, XMMRegister src, AddressLiteral negate_field, int vector_len, Register rscratch) {
  assert(((dst->encoding() < 16 && src->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vldq()),"XMM register should be 0-15");
  assert(rscratch != noreg || always_reachable(negate_field), "missing");

  vandps(dst, nds, negate_field, vector_len, rscratch);
}

void MacroAssembler::vabssd(XMMRegister dst, XMMRegister nds, XMMRegister src, AddressLiteral negate_field, int vector_len, Register rscratch) {
  assert(((dst->encoding() < 16 && src->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vldq()),"XMM register should be 0-15");
  assert(rscratch != noreg || always_reachable(negate_field), "missing");

  vandpd(dst, nds, negate_field, vector_len, rscratch);
}

void MacroAssembler::vpaddb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(((dst->encoding() < 16 && src->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpaddb(dst, nds, src, vector_len);
}

void MacroAssembler::vpaddb(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpaddb(dst, nds, src, vector_len);
}

void MacroAssembler::vpaddw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(((dst->encoding() < 16 && src->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpaddw(dst, nds, src, vector_len);
}

void MacroAssembler::vpaddw(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpaddw(dst, nds, src, vector_len);
}

void MacroAssembler::vpand(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vpand(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vpand(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vpbroadcastd(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vpbroadcastd(dst, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vpbroadcastd(dst, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vbroadcasti128(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vbroadcasti128(dst, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vbroadcasti128(dst, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vpbroadcastq(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vpbroadcastq(dst, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vpbroadcastq(dst, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vbroadcastsd(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vbroadcastsd(dst, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vbroadcastsd(dst, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vbroadcastss(XMMRegister dst, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vbroadcastss(dst, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vbroadcastss(dst, Address(rscratch, 0), vector_len);
  }
}

// Vector float blend
// vblendvps(XMMRegister dst, XMMRegister nds, XMMRegister src, XMMRegister mask, int vector_len, bool compute_mask = true, XMMRegister scratch = xnoreg)
void MacroAssembler::vblendvps(XMMRegister dst, XMMRegister src1, XMMRegister src2, XMMRegister mask, int vector_len, bool compute_mask, XMMRegister scratch) {
  // WARN: Allow dst == (src1|src2), mask == scratch
  bool blend_emulation = EnableX86ECoreOpts && UseAVX > 1 &&
                         !(VM_Version::is_intel_darkmont() && (dst == src1)); // partially fixed on Darkmont
  bool scratch_available = scratch != xnoreg && scratch != src1 && scratch != src2 && scratch != dst;
  bool dst_available = dst != mask && (dst != src1 || dst != src2);
  if (blend_emulation && scratch_available && dst_available) {
    if (compute_mask) {
      vpsrad(scratch, mask, 32, vector_len);
      mask = scratch;
    }
    if (dst == src1) {
      vpandn(dst,     mask, src1, vector_len); // if mask == 0, src1
      vpand (scratch, mask, src2, vector_len); // if mask == 1, src2
    } else {
      vpand (dst,     mask, src2, vector_len); // if mask == 1, src2
      vpandn(scratch, mask, src1, vector_len); // if mask == 0, src1
    }
    vpor(dst, dst, scratch, vector_len);
  } else {
    Assembler::vblendvps(dst, src1, src2, mask, vector_len);
  }
}

// vblendvpd(XMMRegister dst, XMMRegister nds, XMMRegister src, XMMRegister mask, int vector_len, bool compute_mask = true, XMMRegister scratch = xnoreg)
void MacroAssembler::vblendvpd(XMMRegister dst, XMMRegister src1, XMMRegister src2, XMMRegister mask, int vector_len, bool compute_mask, XMMRegister scratch) {
  // WARN: Allow dst == (src1|src2), mask == scratch
  bool blend_emulation = EnableX86ECoreOpts && UseAVX > 1 &&
                         !(VM_Version::is_intel_darkmont() && (dst == src1)); // partially fixed on Darkmont
  bool scratch_available = scratch != xnoreg && scratch != src1 && scratch != src2 && scratch != dst && (!compute_mask || scratch != mask);
  bool dst_available = dst != mask && (dst != src1 || dst != src2);
  if (blend_emulation && scratch_available && dst_available) {
    if (compute_mask) {
      vpxor(scratch, scratch, scratch, vector_len);
      vpcmpgtq(scratch, scratch, mask, vector_len);
      mask = scratch;
    }
    if (dst == src1) {
      vpandn(dst,     mask, src1, vector_len); // if mask == 0, src
      vpand (scratch, mask, src2, vector_len); // if mask == 1, src2
    } else {
      vpand (dst,     mask, src2, vector_len); // if mask == 1, src2
      vpandn(scratch, mask, src1, vector_len); // if mask == 0, src
    }
    vpor(dst, dst, scratch, vector_len);
  } else {
    Assembler::vblendvpd(dst, src1, src2, mask, vector_len);
  }
}

void MacroAssembler::vpcmpeqb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(((dst->encoding() < 16 && src->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpcmpeqb(dst, nds, src, vector_len);
}

void MacroAssembler::vpcmpeqb(XMMRegister dst, XMMRegister src1, Address src2, int vector_len) {
  assert(((dst->encoding() < 16 && src1->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpcmpeqb(dst, src1, src2, vector_len);
}

void MacroAssembler::vpcmpeqw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(((dst->encoding() < 16 && src->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpcmpeqw(dst, nds, src, vector_len);
}

void MacroAssembler::vpcmpeqw(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpcmpeqw(dst, nds, src, vector_len);
}

void MacroAssembler::evpcmpeqd(KRegister kdst, KRegister mask, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evpcmpeqd(kdst, mask, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evpcmpeqd(kdst, mask, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::evpcmpd(KRegister kdst, KRegister mask, XMMRegister nds, AddressLiteral src,
                             int comparison, bool is_signed, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evpcmpd(kdst, mask, nds, as_Address(src), comparison, is_signed, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evpcmpd(kdst, mask, nds, Address(rscratch, 0), comparison, is_signed, vector_len);
  }
}

void MacroAssembler::evpcmpq(KRegister kdst, KRegister mask, XMMRegister nds, AddressLiteral src,
                             int comparison, bool is_signed, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evpcmpq(kdst, mask, nds, as_Address(src), comparison, is_signed, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evpcmpq(kdst, mask, nds, Address(rscratch, 0), comparison, is_signed, vector_len);
  }
}

void MacroAssembler::evpcmpb(KRegister kdst, KRegister mask, XMMRegister nds, AddressLiteral src,
                             int comparison, bool is_signed, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evpcmpb(kdst, mask, nds, as_Address(src), comparison, is_signed, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evpcmpb(kdst, mask, nds, Address(rscratch, 0), comparison, is_signed, vector_len);
  }
}

void MacroAssembler::evpcmpw(KRegister kdst, KRegister mask, XMMRegister nds, AddressLiteral src,
                             int comparison, bool is_signed, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evpcmpw(kdst, mask, nds, as_Address(src), comparison, is_signed, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evpcmpw(kdst, mask, nds, Address(rscratch, 0), comparison, is_signed, vector_len);
  }
}

void MacroAssembler::vpcmpCC(XMMRegister dst, XMMRegister nds, XMMRegister src, int cond_encoding, Width width, int vector_len) {
  if (width == Assembler::Q) {
    Assembler::vpcmpCCq(dst, nds, src, cond_encoding, vector_len);
  } else {
    Assembler::vpcmpCCbwd(dst, nds, src, cond_encoding, vector_len);
  }
}

void MacroAssembler::vpcmpCCW(XMMRegister dst, XMMRegister nds, XMMRegister src, XMMRegister xtmp, ComparisonPredicate cond, Width width, int vector_len) {
  int eq_cond_enc = 0x29;
  int gt_cond_enc = 0x37;
  if (width != Assembler::Q) {
    eq_cond_enc = 0x74 + width;
    gt_cond_enc = 0x64 + width;
  }
  switch (cond) {
  case eq:
    vpcmpCC(dst, nds, src, eq_cond_enc, width, vector_len);
    break;
  case neq:
    vpcmpCC(dst, nds, src, eq_cond_enc, width, vector_len);
    vallones(xtmp, vector_len);
    vpxor(dst, xtmp, dst, vector_len);
    break;
  case le:
    vpcmpCC(dst, nds, src, gt_cond_enc, width, vector_len);
    vallones(xtmp, vector_len);
    vpxor(dst, xtmp, dst, vector_len);
    break;
  case nlt:
    vpcmpCC(dst, src, nds, gt_cond_enc, width, vector_len);
    vallones(xtmp, vector_len);
    vpxor(dst, xtmp, dst, vector_len);
    break;
  case lt:
    vpcmpCC(dst, src, nds, gt_cond_enc, width, vector_len);
    break;
  case nle:
    vpcmpCC(dst, nds, src, gt_cond_enc, width, vector_len);
    break;
  default:
    assert(false, "Should not reach here");
  }
}

void MacroAssembler::vpmovzxbw(XMMRegister dst, Address src, int vector_len) {
  assert(((dst->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpmovzxbw(dst, src, vector_len);
}

void MacroAssembler::vpmovmskb(Register dst, XMMRegister src, int vector_len) {
  assert((src->encoding() < 16),"XMM register should be 0-15");
  Assembler::vpmovmskb(dst, src, vector_len);
}

void MacroAssembler::vpmullw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(((dst->encoding() < 16 && src->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpmullw(dst, nds, src, vector_len);
}

void MacroAssembler::vpmullw(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpmullw(dst, nds, src, vector_len);
}

void MacroAssembler::vpmulld(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert((UseAVX > 0), "AVX support is needed");
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vpmulld(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vpmulld(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vpsubb(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(((dst->encoding() < 16 && src->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsubb(dst, nds, src, vector_len);
}

void MacroAssembler::vpsubb(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsubb(dst, nds, src, vector_len);
}

void MacroAssembler::vpsubw(XMMRegister dst, XMMRegister nds, XMMRegister src, int vector_len) {
  assert(((dst->encoding() < 16 && src->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsubw(dst, nds, src, vector_len);
}

void MacroAssembler::vpsubw(XMMRegister dst, XMMRegister nds, Address src, int vector_len) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsubw(dst, nds, src, vector_len);
}

void MacroAssembler::vpsraw(XMMRegister dst, XMMRegister nds, XMMRegister shift, int vector_len) {
  assert(((dst->encoding() < 16 && shift->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsraw(dst, nds, shift, vector_len);
}

void MacroAssembler::vpsraw(XMMRegister dst, XMMRegister nds, int shift, int vector_len) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsraw(dst, nds, shift, vector_len);
}

void MacroAssembler::evpsraq(XMMRegister dst, XMMRegister nds, XMMRegister shift, int vector_len) {
  assert(UseAVX > 2,"");
  if (!VM_Version::supports_avx512vl() && vector_len < 2) {
     vector_len = 2;
  }
  Assembler::evpsraq(dst, nds, shift, vector_len);
}

void MacroAssembler::evpsraq(XMMRegister dst, XMMRegister nds, int shift, int vector_len) {
  assert(UseAVX > 2,"");
  if (!VM_Version::supports_avx512vl() && vector_len < 2) {
     vector_len = 2;
  }
  Assembler::evpsraq(dst, nds, shift, vector_len);
}

void MacroAssembler::vpsrlw(XMMRegister dst, XMMRegister nds, XMMRegister shift, int vector_len) {
  assert(((dst->encoding() < 16 && shift->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsrlw(dst, nds, shift, vector_len);
}

void MacroAssembler::vpsrlw(XMMRegister dst, XMMRegister nds, int shift, int vector_len) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsrlw(dst, nds, shift, vector_len);
}

void MacroAssembler::vpsllw(XMMRegister dst, XMMRegister nds, XMMRegister shift, int vector_len) {
  assert(((dst->encoding() < 16 && shift->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsllw(dst, nds, shift, vector_len);
}

void MacroAssembler::vpsllw(XMMRegister dst, XMMRegister nds, int shift, int vector_len) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::vpsllw(dst, nds, shift, vector_len);
}

void MacroAssembler::vptest(XMMRegister dst, XMMRegister src) {
  assert((dst->encoding() < 16 && src->encoding() < 16),"XMM register should be 0-15");
  Assembler::vptest(dst, src);
}

void MacroAssembler::punpcklbw(XMMRegister dst, XMMRegister src) {
  assert(((dst->encoding() < 16 && src->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::punpcklbw(dst, src);
}

void MacroAssembler::pshufd(XMMRegister dst, Address src, int mode) {
  assert(((dst->encoding() < 16) || VM_Version::supports_avx512vl()),"XMM register should be 0-15");
  Assembler::pshufd(dst, src, mode);
}

void MacroAssembler::pshuflw(XMMRegister dst, XMMRegister src, int mode) {
  assert(((dst->encoding() < 16 && src->encoding() < 16) || VM_Version::supports_avx512vlbw()),"XMM register should be 0-15");
  Assembler::pshuflw(dst, src, mode);
}

void MacroAssembler::vandpd(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vandpd(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    vandpd(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vandps(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vandps(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    vandps(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::evpord(XMMRegister dst, KRegister mask, XMMRegister nds, AddressLiteral src,
                            bool merge, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evpord(dst, mask, nds, as_Address(src), merge, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evpord(dst, mask, nds, Address(rscratch, 0), merge, vector_len);
  }
}

void MacroAssembler::vdivsd(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vdivsd(dst, nds, as_Address(src));
  } else {
    lea(rscratch, src);
    vdivsd(dst, nds, Address(rscratch, 0));
  }
}

void MacroAssembler::vdivss(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vdivss(dst, nds, as_Address(src));
  } else {
    lea(rscratch, src);
    vdivss(dst, nds, Address(rscratch, 0));
  }
}

void MacroAssembler::vmulsd(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vmulsd(dst, nds, as_Address(src));
  } else {
    lea(rscratch, src);
    vmulsd(dst, nds, Address(rscratch, 0));
  }
}

void MacroAssembler::vmulss(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vmulss(dst, nds, as_Address(src));
  } else {
    lea(rscratch, src);
    vmulss(dst, nds, Address(rscratch, 0));
  }
}

void MacroAssembler::vsubsd(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vsubsd(dst, nds, as_Address(src));
  } else {
    lea(rscratch, src);
    vsubsd(dst, nds, Address(rscratch, 0));
  }
}

void MacroAssembler::vsubss(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vsubss(dst, nds, as_Address(src));
  } else {
    lea(rscratch, src);
    vsubss(dst, nds, Address(rscratch, 0));
  }
}

void MacroAssembler::vnegatess(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vldq()),"XMM register should be 0-15");
  assert(rscratch != noreg || always_reachable(src), "missing");

  vxorps(dst, nds, src, Assembler::AVX_128bit, rscratch);
}

void MacroAssembler::vnegatesd(XMMRegister dst, XMMRegister nds, AddressLiteral src, Register rscratch) {
  assert(((dst->encoding() < 16 && nds->encoding() < 16) || VM_Version::supports_avx512vldq()),"XMM register should be 0-15");
  assert(rscratch != noreg || always_reachable(src), "missing");

  vxorpd(dst, nds, src, Assembler::AVX_128bit, rscratch);
}

void MacroAssembler::vxorpd(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vxorpd(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    vxorpd(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vxorps(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vxorps(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    vxorps(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vpxor(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (UseAVX > 1 || (vector_len < 1)) {
    if (reachable(src)) {
      Assembler::vpxor(dst, nds, as_Address(src), vector_len);
    } else {
      lea(rscratch, src);
      Assembler::vpxor(dst, nds, Address(rscratch, 0), vector_len);
    }
  } else {
    MacroAssembler::vxorpd(dst, nds, src, vector_len, rscratch);
  }
}

void MacroAssembler::vpermd(XMMRegister dst,  XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vpermd(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vpermd(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::clear_jobject_tag(Register possibly_non_local) {
  const int32_t inverted_mask = ~static_cast<int32_t>(JNIHandles::tag_mask);
  STATIC_ASSERT(inverted_mask == -4); // otherwise check this code
  // The inverted mask is sign-extended
  andptr(possibly_non_local, inverted_mask);
}

void MacroAssembler::resolve_jobject(Register value,
                                     Register tmp) {
  Register thread = r15_thread;
  assert_different_registers(value, thread, tmp);
  Label done, tagged, weak_tagged;
  testptr(value, value);
  jcc(Assembler::zero, done);           // Use null as-is.
  testptr(value, JNIHandles::tag_mask); // Test for tag.
  jcc(Assembler::notZero, tagged);

  // Resolve local handle
  access_load_at(T_OBJECT, IN_NATIVE | AS_RAW, value, Address(value, 0), tmp);
  verify_oop(value);
  jmp(done);

  bind(tagged);
  testptr(value, JNIHandles::TypeTag::weak_global); // Test for weak tag.
  jcc(Assembler::notZero, weak_tagged);

  // Resolve global handle
  access_load_at(T_OBJECT, IN_NATIVE, value, Address(value, -JNIHandles::TypeTag::global), tmp);
  verify_oop(value);
  jmp(done);

  bind(weak_tagged);
  // Resolve jweak.
  access_load_at(T_OBJECT, IN_NATIVE | ON_PHANTOM_OOP_REF,
                 value, Address(value, -JNIHandles::TypeTag::weak_global), tmp);
  verify_oop(value);

  bind(done);
}

void MacroAssembler::resolve_global_jobject(Register value,
                                            Register tmp) {
  Register thread = r15_thread;
  assert_different_registers(value, thread, tmp);
  Label done;

  testptr(value, value);
  jcc(Assembler::zero, done);           // Use null as-is.

#ifdef ASSERT
  {
    Label valid_global_tag;
    testptr(value, JNIHandles::TypeTag::global); // Test for global tag.
    jcc(Assembler::notZero, valid_global_tag);
    stop("non global jobject using resolve_global_jobject");
    bind(valid_global_tag);
  }
#endif

  // Resolve global handle
  access_load_at(T_OBJECT, IN_NATIVE, value, Address(value, -JNIHandles::TypeTag::global), tmp);
  verify_oop(value);

  bind(done);
}

void MacroAssembler::subptr(Register dst, int32_t imm32) {
  subq(dst, imm32);
}

// Force generation of a 4 byte immediate value even if it fits into 8bit
void MacroAssembler::subptr_imm32(Register dst, int32_t imm32) {
  subq_imm32(dst, imm32);
}

void MacroAssembler::subptr(Register dst, Register src) {
  subq(dst, src);
}

// C++ bool manipulation
void MacroAssembler::testbool(Register dst) {
  if(sizeof(bool) == 1)
    testb(dst, 0xff);
  else if(sizeof(bool) == 2) {
    // testw implementation needed for two byte bools
    ShouldNotReachHere();
  } else if(sizeof(bool) == 4)
    testl(dst, dst);
  else
    // unsupported
    ShouldNotReachHere();
}

void MacroAssembler::testptr(Register dst, Register src) {
  testq(dst, src);
}

// Defines obj, preserves var_size_in_bytes, okay for t2 == var_size_in_bytes.
void MacroAssembler::tlab_allocate(Register obj,
                                   Register var_size_in_bytes,
                                   int con_size_in_bytes,
                                   Register t1,
                                   Register t2,
                                   Label& slow_case) {
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  bs->tlab_allocate(this, obj, var_size_in_bytes, con_size_in_bytes, t1, t2, slow_case);
}

RegSet MacroAssembler::call_clobbered_gp_registers() {
  RegSet regs;
  regs += RegSet::of(rax, rcx, rdx);
#ifndef _WINDOWS
  regs += RegSet::of(rsi, rdi);
#endif
  regs += RegSet::range(r8, r11);
  if (UseAPX) {
    regs += RegSet::range(r16, as_Register(Register::number_of_registers - 1));
  }
  return regs;
}

XMMRegSet MacroAssembler::call_clobbered_xmm_registers() {
  int num_xmm_registers = XMMRegister::available_xmm_registers();
#if defined(_WINDOWS)
  XMMRegSet result = XMMRegSet::range(xmm0, xmm5);
  if (num_xmm_registers > 16) {
     result += XMMRegSet::range(xmm16, as_XMMRegister(num_xmm_registers - 1));
  }
  return result;
#else
  return XMMRegSet::range(xmm0, as_XMMRegister(num_xmm_registers - 1));
#endif
}

// C1 only ever uses the first double/float of the XMM register.
static int xmm_save_size() { return sizeof(double); }

static void save_xmm_register(MacroAssembler* masm, int offset, XMMRegister reg) {
  masm->movdbl(Address(rsp, offset), reg);
}

static void restore_xmm_register(MacroAssembler* masm, int offset, XMMRegister reg) {
  masm->movdbl(reg, Address(rsp, offset));
}

static int register_section_sizes(RegSet gp_registers, XMMRegSet xmm_registers,
                                  bool save_fpu, int& gp_area_size, int& xmm_area_size) {

  gp_area_size = align_up(gp_registers.size() * Register::max_slots_per_register * VMRegImpl::stack_slot_size,
                         StackAlignmentInBytes);
  xmm_area_size = save_fpu ? xmm_registers.size() * xmm_save_size() : 0;

  return gp_area_size + xmm_area_size;
}

void MacroAssembler::push_call_clobbered_registers_except(RegSet exclude, bool save_fpu) {
  block_comment("push_call_clobbered_registers start");
  // Regular registers
  RegSet gp_registers_to_push = call_clobbered_gp_registers() - exclude;

  int gp_area_size;
  int xmm_area_size;
  int total_save_size = register_section_sizes(gp_registers_to_push, call_clobbered_xmm_registers(), save_fpu,
                                               gp_area_size, xmm_area_size);
  subptr(rsp, total_save_size);

  push_set(gp_registers_to_push, 0);

  if (save_fpu) {
    push_set(call_clobbered_xmm_registers(), gp_area_size);
  }

  block_comment("push_call_clobbered_registers end");
}

void MacroAssembler::pop_call_clobbered_registers_except(RegSet exclude, bool restore_fpu) {
  block_comment("pop_call_clobbered_registers start");

  RegSet gp_registers_to_pop = call_clobbered_gp_registers() - exclude;

  int gp_area_size;
  int xmm_area_size;
  int total_save_size = register_section_sizes(gp_registers_to_pop, call_clobbered_xmm_registers(), restore_fpu,
                                               gp_area_size, xmm_area_size);

  if (restore_fpu) {
    pop_set(call_clobbered_xmm_registers(), gp_area_size);
  }

  pop_set(gp_registers_to_pop, 0);

  addptr(rsp, total_save_size);

  vzeroupper();

  block_comment("pop_call_clobbered_registers end");
}

void MacroAssembler::push_set(XMMRegSet set, int offset) {
  assert(is_aligned(set.size() * xmm_save_size(), StackAlignmentInBytes), "must be");
  int spill_offset = offset;

  for (RegSetIterator<XMMRegister> it = set.begin(); *it != xnoreg; ++it) {
    save_xmm_register(this, spill_offset, *it);
    spill_offset += xmm_save_size();
  }
}

void MacroAssembler::pop_set(XMMRegSet set, int offset) {
  int restore_size = set.size() * xmm_save_size();
  assert(is_aligned(restore_size, StackAlignmentInBytes), "must be");

  int restore_offset = offset + restore_size - xmm_save_size();

  for (ReverseRegSetIterator<XMMRegister> it = set.rbegin(); *it != xnoreg; ++it) {
    restore_xmm_register(this, restore_offset, *it);
    restore_offset -= xmm_save_size();
  }
}

void MacroAssembler::push_set(RegSet set, int offset) {
  int spill_offset;
  if (offset == -1) {
    int register_push_size = set.size() * Register::max_slots_per_register * VMRegImpl::stack_slot_size;
    int aligned_size = align_up(register_push_size, StackAlignmentInBytes);
    subptr(rsp, aligned_size);
    spill_offset = 0;
  } else {
    spill_offset = offset;
  }

  for (RegSetIterator<Register> it = set.begin(); *it != noreg; ++it) {
    movptr(Address(rsp, spill_offset), *it);
    spill_offset += Register::max_slots_per_register * VMRegImpl::stack_slot_size;
  }
}

void MacroAssembler::pop_set(RegSet set, int offset) {

  int gp_reg_size = Register::max_slots_per_register * VMRegImpl::stack_slot_size;
  int restore_size = set.size() * gp_reg_size;
  int aligned_size = align_up(restore_size, StackAlignmentInBytes);

  int restore_offset;
  if (offset == -1) {
    restore_offset = restore_size - gp_reg_size;
  } else {
    restore_offset = offset + restore_size - gp_reg_size;
  }
  for (ReverseRegSetIterator<Register> it = set.rbegin(); *it != noreg; ++it) {
    movptr(*it, Address(rsp, restore_offset));
    restore_offset -= gp_reg_size;
  }

  if (offset == -1) {
    addptr(rsp, aligned_size);
  }
}

// Preserves the contents of address, destroys the contents length_in_bytes and temp.
void MacroAssembler::zero_memory(Register address, Register length_in_bytes, int offset_in_bytes, Register temp) {
  assert(address != length_in_bytes && address != temp && temp != length_in_bytes, "registers must be different");
  assert((offset_in_bytes & (BytesPerWord - 1)) == 0, "offset must be a multiple of BytesPerWord");
  Label done;

  testptr(length_in_bytes, length_in_bytes);
  jcc(Assembler::zero, done);

  // initialize topmost word, divide index by 2, check if odd and test if zero
  // note: for the remaining code to work, index must be a multiple of BytesPerWord
#ifdef ASSERT
  {
    Label L;
    testptr(length_in_bytes, BytesPerWord - 1);
    jcc(Assembler::zero, L);
    stop("length must be a multiple of BytesPerWord");
    bind(L);
  }
#endif
  Register index = length_in_bytes;
  xorptr(temp, temp);    // use _zero reg to clear memory (shorter code)
  if (UseIncDec) {
    shrptr(index, 3);  // divide by 8/16 and set carry flag if bit 2 was set
  } else {
    shrptr(index, 2);  // use 2 instructions to avoid partial flag stall
    shrptr(index, 1);
  }

  // initialize remaining object fields: index is a multiple of 2 now
  {
    Label loop;
    bind(loop);
    movptr(Address(address, index, Address::times_8, offset_in_bytes - 1*BytesPerWord), temp);
    decrement(index);
    jcc(Assembler::notZero, loop);
  }

  bind(done);
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
                                             Register scan_temp,
                                             Label& L_no_such_interface,
                                             bool return_method) {
  assert_different_registers(recv_klass, intf_klass, scan_temp);
  assert_different_registers(method_result, intf_klass, scan_temp);
  assert(recv_klass != method_result || !return_method,
         "recv_klass can be destroyed when method isn't needed");

  assert(itable_index.is_constant() || itable_index.as_register() == method_result,
         "caller must use same register for non-constant itable index as for method");

  // Compute start of first itableOffsetEntry (which is at the end of the vtable)
  int vtable_base = in_bytes(Klass::vtable_start_offset());
  int itentry_off = in_bytes(itableMethodEntry::method_offset());
  int scan_step   = itableOffsetEntry::size() * wordSize;
  int vte_size    = vtableEntry::size_in_bytes();
  Address::ScaleFactor times_vte_scale = Address::times_ptr;
  assert(vte_size == wordSize, "else adjust times_vte_scale");

  movl(scan_temp, Address(recv_klass, Klass::vtable_length_offset()));

  // Could store the aligned, prescaled offset in the klass.
  lea(scan_temp, Address(recv_klass, scan_temp, times_vte_scale, vtable_base));

  if (return_method) {
    // Adjust recv_klass by scaled itable_index, so we can free itable_index.
    assert(itableMethodEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");
    lea(recv_klass, Address(recv_klass, itable_index, Address::times_ptr, itentry_off));
  }

  // for (scan = klass->itable(); scan->interface() != nullptr; scan += scan_step) {
  //   if (scan->interface() == intf) {
  //     result = (klass + scan->offset() + itable_index);
  //   }
  // }
  Label search, found_method;

  for (int peel = 1; peel >= 0; peel--) {
    movptr(method_result, Address(scan_temp, itableOffsetEntry::interface_offset()));
    cmpptr(intf_klass, method_result);

    if (peel) {
      jccb(Assembler::equal, found_method);
    } else {
      jccb(Assembler::notEqual, search);
      // (invert the test to fall through to found_method...)
    }

    if (!peel)  break;

    bind(search);

    // Check that the previous entry is non-null.  A null entry means that
    // the receiver class doesn't implement the interface, and wasn't the
    // same as when the caller was compiled.
    testptr(method_result, method_result);
    jcc(Assembler::zero, L_no_such_interface);
    addptr(scan_temp, scan_step);
  }

  bind(found_method);

  if (return_method) {
    // Got a hit.
    movl(scan_temp, Address(scan_temp, itableOffsetEntry::offset_offset()));
    movptr(method_result, Address(recv_klass, scan_temp, Address::times_1));
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
                                                  Register scan_temp,
                                                  Register temp_reg2,
                                                  Register receiver,
                                                  int itable_index,
                                                  Label& L_no_such_interface) {
  assert_different_registers(recv_klass, method_result, holder_klass, resolved_klass, scan_temp, temp_reg2, receiver);
  Register temp_itbl_klass = method_result;
  Register temp_reg = (temp_reg2 == noreg ? recv_klass : temp_reg2); // reuse recv_klass register on 32-bit x86 impl

  int vtable_base = in_bytes(Klass::vtable_start_offset());
  int itentry_off = in_bytes(itableMethodEntry::method_offset());
  int scan_step = itableOffsetEntry::size() * wordSize;
  int vte_size = vtableEntry::size_in_bytes();
  int ioffset = in_bytes(itableOffsetEntry::interface_offset());
  int ooffset = in_bytes(itableOffsetEntry::offset_offset());
  Address::ScaleFactor times_vte_scale = Address::times_ptr;
  assert(vte_size == wordSize, "adjust times_vte_scale");

  Label L_loop_scan_resolved_entry, L_resolved_found, L_holder_found;

  // temp_itbl_klass = recv_klass.itable[0]
  // scan_temp = &recv_klass.itable[0] + step
  movl(scan_temp, Address(recv_klass, Klass::vtable_length_offset()));
  movptr(temp_itbl_klass, Address(recv_klass, scan_temp, times_vte_scale, vtable_base + ioffset));
  lea(scan_temp, Address(recv_klass, scan_temp, times_vte_scale, vtable_base + ioffset + scan_step));
  xorptr(temp_reg, temp_reg);

  // Initial checks:
  //   - if (holder_klass != resolved_klass), go to "scan for resolved"
  //   - if (itable[0] == 0), no such interface
  //   - if (itable[0] == holder_klass), shortcut to "holder found"
  cmpptr(holder_klass, resolved_klass);
  jccb(Assembler::notEqual, L_loop_scan_resolved_entry);
  testptr(temp_itbl_klass, temp_itbl_klass);
  jccb(Assembler::zero, L_no_such_interface);
  cmpptr(holder_klass, temp_itbl_klass);
  jccb(Assembler::equal, L_holder_found);

  // Loop: Look for holder_klass record in itable
  //   do {
  //     tmp = itable[index];
  //     index += step;
  //     if (tmp == holder_klass) {
  //       goto L_holder_found; // Found!
  //     }
  //   } while (tmp != 0);
  //   goto L_no_such_interface // Not found.
  Label L_scan_holder;
  bind(L_scan_holder);
    movptr(temp_itbl_klass, Address(scan_temp, 0));
    addptr(scan_temp, scan_step);
    cmpptr(holder_klass, temp_itbl_klass);
    jccb(Assembler::equal, L_holder_found);
    testptr(temp_itbl_klass, temp_itbl_klass);
    jccb(Assembler::notZero, L_scan_holder);

  jmpb(L_no_such_interface);

  // Loop: Look for resolved_class record in itable
  //   do {
  //     tmp = itable[index];
  //     index += step;
  //     if (tmp == holder_klass) {
  //        // Also check if we have met a holder klass
  //        holder_tmp = itable[index-step-ioffset];
  //     }
  //     if (tmp == resolved_klass) {
  //        goto L_resolved_found;  // Found!
  //     }
  //   } while (tmp != 0);
  //   goto L_no_such_interface // Not found.
  //
  Label L_loop_scan_resolved;
  bind(L_loop_scan_resolved);
    movptr(temp_itbl_klass, Address(scan_temp, 0));
    addptr(scan_temp, scan_step);
    bind(L_loop_scan_resolved_entry);
    cmpptr(holder_klass, temp_itbl_klass);
    cmovl(Assembler::equal, temp_reg, Address(scan_temp, ooffset - ioffset - scan_step));
    cmpptr(resolved_klass, temp_itbl_klass);
    jccb(Assembler::equal, L_resolved_found);
    testptr(temp_itbl_klass, temp_itbl_klass);
    jccb(Assembler::notZero, L_loop_scan_resolved);

  jmpb(L_no_such_interface);

  Label L_ready;

  // See if we already have a holder klass. If not, go and scan for it.
  bind(L_resolved_found);
  testptr(temp_reg, temp_reg);
  jccb(Assembler::zero, L_scan_holder);
  jmpb(L_ready);

  bind(L_holder_found);
  movl(temp_reg, Address(scan_temp, ooffset - ioffset - scan_step));

  // Finally, temp_reg contains holder_klass vtable offset
  bind(L_ready);
  assert(itableMethodEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");
  if (temp_reg2 == noreg) { // recv_klass register is clobbered for 32-bit x86 impl
    load_klass(scan_temp, receiver, noreg);
    movptr(method_result, Address(scan_temp, temp_reg, Address::times_1, itable_index * wordSize + itentry_off));
  } else {
    movptr(method_result, Address(recv_klass, temp_reg, Address::times_1, itable_index * wordSize + itentry_off));
  }
}


// virtual method calling
void MacroAssembler::lookup_virtual_method(Register recv_klass,
                                           RegisterOrConstant vtable_index,
                                           Register method_result) {
  const ByteSize base = Klass::vtable_start_offset();
  assert(vtableEntry::size() * wordSize == wordSize, "else adjust the scaling in the code below");
  Address vtable_entry_addr(recv_klass,
                            vtable_index, Address::times_ptr,
                            base + vtableEntry::method_offset());
  movptr(method_result, vtable_entry_addr);
}


void MacroAssembler::check_klass_subtype(Register sub_klass,
                           Register super_klass,
                           Register temp_reg,
                           Label& L_success) {
  Label L_failure;
  check_klass_subtype_fast_path(sub_klass, super_klass, temp_reg,        &L_success, &L_failure, nullptr);
  check_klass_subtype_slow_path(sub_klass, super_klass, temp_reg, noreg, &L_success, nullptr);
  bind(L_failure);
}


void MacroAssembler::check_klass_subtype_fast_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   Label* L_slow_path,
                                        RegisterOrConstant super_check_offset) {
  assert_different_registers(sub_klass, super_klass, temp_reg);
  bool must_load_sco = (super_check_offset.constant_or_zero() == -1);
  if (super_check_offset.is_register()) {
    assert_different_registers(sub_klass, super_klass,
                               super_check_offset.as_register());
  } else if (must_load_sco) {
    assert(temp_reg != noreg, "supply either a temp or a register offset");
  }

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == nullptr)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == nullptr)   { L_failure   = &L_fallthrough; label_nulls++; }
  if (L_slow_path == nullptr) { L_slow_path = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one null in the batch");

  int sc_offset = in_bytes(Klass::secondary_super_cache_offset());
  int sco_offset = in_bytes(Klass::super_check_offset_offset());
  Address super_check_offset_addr(super_klass, sco_offset);

  // Hacked jcc, which "knows" that L_fallthrough, at least, is in
  // range of a jccb.  If this routine grows larger, reconsider at
  // least some of these.
#define local_jcc(assembler_cond, label)                                \
  if (&(label) == &L_fallthrough)  jccb(assembler_cond, label);         \
  else                             jcc( assembler_cond, label) /*omit semi*/

  // Hacked jmp, which may only be used just before L_fallthrough.
#define final_jmp(label)                                                \
  if (&(label) == &L_fallthrough) { /*do nothing*/ }                    \
  else                            jmp(label)                /*omit semi*/

  // If the pointers are equal, we are done (e.g., String[] elements).
  // This self-check enables sharing of secondary supertype arrays among
  // non-primary types such as array-of-interface.  Otherwise, each such
  // type would need its own customized SSA.
  // We move this check to the front of the fast path because many
  // type checks are in fact trivially successful in this manner,
  // so we get a nicely predicted branch right at the start of the check.
  cmpptr(sub_klass, super_klass);
  local_jcc(Assembler::equal, *L_success);

  // Check the supertype display:
  if (must_load_sco) {
    // Positive movl does right thing on LP64.
    movl(temp_reg, super_check_offset_addr);
    super_check_offset = RegisterOrConstant(temp_reg);
  }
  Address super_check_addr(sub_klass, super_check_offset, Address::times_1, 0);
  cmpptr(super_klass, super_check_addr); // load displayed supertype

  // This check has worked decisively for primary supers.
  // Secondary supers are sought in the super_cache ('super_cache_addr').
  // (Secondary supers are interfaces and very deeply nested subtypes.)
  // This works in the same check above because of a tricky aliasing
  // between the super_cache and the primary super display elements.
  // (The 'super_check_addr' can address either, as the case requires.)
  // Note that the cache is updated below if it does not help us find
  // what we need immediately.
  // So if it was a primary super, we can just fail immediately.
  // Otherwise, it's the slow path for us (no success at this point).

  if (super_check_offset.is_register()) {
    local_jcc(Assembler::equal, *L_success);
    cmpl(super_check_offset.as_register(), sc_offset);
    if (L_failure == &L_fallthrough) {
      local_jcc(Assembler::equal, *L_slow_path);
    } else {
      local_jcc(Assembler::notEqual, *L_failure);
      final_jmp(*L_slow_path);
    }
  } else if (super_check_offset.as_constant() == sc_offset) {
    // Need a slow path; fast failure is impossible.
    if (L_slow_path == &L_fallthrough) {
      local_jcc(Assembler::equal, *L_success);
    } else {
      local_jcc(Assembler::notEqual, *L_slow_path);
      final_jmp(*L_success);
    }
  } else {
    // No slow path; it's a fast decision.
    if (L_failure == &L_fallthrough) {
      local_jcc(Assembler::equal, *L_success);
    } else {
      local_jcc(Assembler::notEqual, *L_failure);
      final_jmp(*L_success);
    }
  }

  bind(L_fallthrough);

#undef local_jcc
#undef final_jmp
}


void MacroAssembler::check_klass_subtype_slow_path_linear(Register sub_klass,
                                                          Register super_klass,
                                                          Register temp_reg,
                                                          Register temp2_reg,
                                                          Label* L_success,
                                                          Label* L_failure,
                                                          bool set_cond_codes) {
  assert_different_registers(sub_klass, super_klass, temp_reg);
  if (temp2_reg != noreg)
    assert_different_registers(sub_klass, super_klass, temp_reg, temp2_reg);
#define IS_A_TEMP(reg) ((reg) == temp_reg || (reg) == temp2_reg)

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == nullptr)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == nullptr)   { L_failure   = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one null in the batch");

  // a couple of useful fields in sub_klass:
  int ss_offset = in_bytes(Klass::secondary_supers_offset());
  int sc_offset = in_bytes(Klass::secondary_super_cache_offset());
  Address secondary_supers_addr(sub_klass, ss_offset);
  Address super_cache_addr(     sub_klass, sc_offset);

  // Do a linear scan of the secondary super-klass chain.
  // This code is rarely used, so simplicity is a virtue here.
  // The repne_scan instruction uses fixed registers, which we must spill.
  // Don't worry too much about pre-existing connections with the input regs.

  assert(sub_klass != rax, "killed reg"); // killed by mov(rax, super)
  assert(sub_klass != rcx, "killed reg"); // killed by lea(rcx, &pst_counter)

  // Get super_klass value into rax (even if it was in rdi or rcx).
  bool pushed_rax = false, pushed_rcx = false, pushed_rdi = false;
  if (super_klass != rax) {
    if (!IS_A_TEMP(rax)) { push(rax); pushed_rax = true; }
    mov(rax, super_klass);
  }
  if (!IS_A_TEMP(rcx)) { push(rcx); pushed_rcx = true; }
  if (!IS_A_TEMP(rdi)) { push(rdi); pushed_rdi = true; }

#ifndef PRODUCT
  uint* pst_counter = &SharedRuntime::_partial_subtype_ctr;
  ExternalAddress pst_counter_addr((address) pst_counter);
  lea(rcx, pst_counter_addr);
  incrementl(Address(rcx, 0));
#endif //PRODUCT

  // We will consult the secondary-super array.
  movptr(rdi, secondary_supers_addr);
  // Load the array length.  (Positive movl does right thing on LP64.)
  movl(rcx, Address(rdi, Array<Klass*>::length_offset_in_bytes()));
  // Skip to start of data.
  addptr(rdi, Array<Klass*>::base_offset_in_bytes());

  // Scan RCX words at [RDI] for an occurrence of RAX.
  // Set NZ/Z based on last compare.
  // Z flag value will not be set by 'repne' if RCX == 0 since 'repne' does
  // not change flags (only scas instruction which is repeated sets flags).
  // Set Z = 0 (not equal) before 'repne' to indicate that class was not found.

    testptr(rax,rax); // Set Z = 0
    repne_scan();

  // Unspill the temp. registers:
  if (pushed_rdi)  pop(rdi);
  if (pushed_rcx)  pop(rcx);
  if (pushed_rax)  pop(rax);

  if (set_cond_codes) {
    // Special hack for the AD files:  rdi is guaranteed non-zero.
    assert(!pushed_rdi, "rdi must be left non-null");
    // Also, the condition codes are properly set Z/NZ on succeed/failure.
  }

  if (L_failure == &L_fallthrough)
        jccb(Assembler::notEqual, *L_failure);
  else  jcc(Assembler::notEqual, *L_failure);

  // Success.  Cache the super we found and proceed in triumph.
  movptr(super_cache_addr, super_klass);

  if (L_success != &L_fallthrough) {
    jmp(*L_success);
  }

#undef IS_A_TEMP

  bind(L_fallthrough);
}

void MacroAssembler::check_klass_subtype_slow_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Register temp2_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   bool set_cond_codes) {
  assert(set_cond_codes == false, "must be false on 64-bit x86");
  check_klass_subtype_slow_path
    (sub_klass, super_klass, temp_reg, temp2_reg, noreg, noreg,
     L_success, L_failure);
}

void MacroAssembler::check_klass_subtype_slow_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Register temp2_reg,
                                                   Register temp3_reg,
                                                   Register temp4_reg,
                                                   Label* L_success,
                                                   Label* L_failure) {
  if (UseSecondarySupersTable) {
    check_klass_subtype_slow_path_table
      (sub_klass, super_klass, temp_reg, temp2_reg, temp3_reg, temp4_reg,
       L_success, L_failure);
  } else {
    check_klass_subtype_slow_path_linear
      (sub_klass, super_klass, temp_reg, temp2_reg, L_success, L_failure, /*set_cond_codes*/false);
  }
}

Register MacroAssembler::allocate_if_noreg(Register r,
                                  RegSetIterator<Register> &available_regs,
                                  RegSet &regs_to_push) {
  if (!r->is_valid()) {
    r = *available_regs++;
    regs_to_push += r;
  }
  return r;
}

void MacroAssembler::check_klass_subtype_slow_path_table(Register sub_klass,
                                                         Register super_klass,
                                                         Register temp_reg,
                                                         Register temp2_reg,
                                                         Register temp3_reg,
                                                         Register result_reg,
                                                         Label* L_success,
                                                         Label* L_failure) {
  // NB! Callers may assume that, when temp2_reg is a valid register,
  // this code sets it to a nonzero value.
  bool temp2_reg_was_valid = temp2_reg->is_valid();

  RegSet temps = RegSet::of(temp_reg, temp2_reg, temp3_reg);

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == nullptr)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == nullptr)   { L_failure   = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one null in the batch");

  BLOCK_COMMENT("check_klass_subtype_slow_path_table");

  RegSetIterator<Register> available_regs
    = (RegSet::of(rax, rcx, rdx, r8) + r9 + r10 + r11 + r12 - temps - sub_klass - super_klass).begin();

  RegSet pushed_regs;

  temp_reg = allocate_if_noreg(temp_reg, available_regs, pushed_regs);
  temp2_reg = allocate_if_noreg(temp2_reg, available_regs, pushed_regs);
  temp3_reg = allocate_if_noreg(temp3_reg, available_regs, pushed_regs);
  result_reg = allocate_if_noreg(result_reg, available_regs, pushed_regs);
  Register temp4_reg = allocate_if_noreg(noreg, available_regs, pushed_regs);

  assert_different_registers(sub_klass, super_klass, temp_reg, temp2_reg, temp3_reg, result_reg);

  {

    int register_push_size = pushed_regs.size() * Register::max_slots_per_register * VMRegImpl::stack_slot_size;
    int aligned_size = align_up(register_push_size, StackAlignmentInBytes);
    subptr(rsp, aligned_size);
    push_set(pushed_regs, 0);

    lookup_secondary_supers_table_var(sub_klass,
                                      super_klass,
                                      temp_reg, temp2_reg, temp3_reg, temp4_reg, result_reg);
    cmpq(result_reg, 0);

    // Unspill the temp. registers:
    pop_set(pushed_regs, 0);
    // Increment SP but do not clobber flags.
    lea(rsp, Address(rsp, aligned_size));
  }

  if (temp2_reg_was_valid) {
    movq(temp2_reg, 1);
  }

  jcc(Assembler::notEqual, *L_failure);

  if (L_success != &L_fallthrough) {
    jmp(*L_success);
  }

  bind(L_fallthrough);
}

// population_count variant for running without the POPCNT
// instruction, which was introduced with SSE4.2 in 2008.
void MacroAssembler::population_count(Register dst, Register src,
                                      Register scratch1, Register scratch2) {
  assert_different_registers(src, scratch1, scratch2);
  if (UsePopCountInstruction) {
    Assembler::popcntq(dst, src);
  } else {
    assert_different_registers(src, scratch1, scratch2);
    assert_different_registers(dst, scratch1, scratch2);
    Label loop, done;

    mov(scratch1, src);
    // dst = 0;
    // while(scratch1 != 0) {
    //   dst++;
    //   scratch1 &= (scratch1 - 1);
    // }
    xorl(dst, dst);
    testq(scratch1, scratch1);
    jccb(Assembler::equal, done);
    {
      bind(loop);
      incq(dst);
      movq(scratch2, scratch1);
      decq(scratch2);
      andq(scratch1, scratch2);
      jccb(Assembler::notEqual, loop);
    }
    bind(done);
  }
#ifdef ASSERT
  mov64(scratch1, 0xCafeBabeDeadBeef);
  movq(scratch2, scratch1);
#endif
}

// Ensure that the inline code and the stub are using the same registers.
#define LOOKUP_SECONDARY_SUPERS_TABLE_REGISTERS                      \
do {                                                                 \
  assert(r_super_klass  == rax, "mismatch");                         \
  assert(r_array_base   == rbx, "mismatch");                         \
  assert(r_array_length == rcx, "mismatch");                         \
  assert(r_array_index  == rdx, "mismatch");                         \
  assert(r_sub_klass    == rsi || r_sub_klass == noreg, "mismatch"); \
  assert(r_bitmap       == r11 || r_bitmap    == noreg, "mismatch"); \
  assert(result         == rdi || result      == noreg, "mismatch"); \
} while(0)

// Versions of salq and rorq that don't need count to be in rcx

void MacroAssembler::salq(Register dest, Register count) {
  if (count == rcx) {
    Assembler::salq(dest);
  } else {
    assert_different_registers(rcx, dest);
    xchgq(rcx, count);
    Assembler::salq(dest);
    xchgq(rcx, count);
  }
}

void MacroAssembler::rorq(Register dest, Register count) {
  if (count == rcx) {
    Assembler::rorq(dest);
  } else {
    assert_different_registers(rcx, dest);
    xchgq(rcx, count);
    Assembler::rorq(dest);
    xchgq(rcx, count);
  }
}

// Return true: we succeeded in generating this code
//
// At runtime, return 0 in result if r_super_klass is a superclass of
// r_sub_klass, otherwise return nonzero. Use this if you know the
// super_klass_slot of the class you're looking for. This is always
// the case for instanceof and checkcast.
void MacroAssembler::lookup_secondary_supers_table_const(Register r_sub_klass,
                                                         Register r_super_klass,
                                                         Register temp1,
                                                         Register temp2,
                                                         Register temp3,
                                                         Register temp4,
                                                         Register result,
                                                         u1 super_klass_slot) {
  assert_different_registers(r_sub_klass, r_super_klass, temp1, temp2, temp3, temp4, result);

  Label L_fallthrough, L_success, L_failure;

  BLOCK_COMMENT("lookup_secondary_supers_table {");

  const Register
    r_array_index  = temp1,
    r_array_length = temp2,
    r_array_base   = temp3,
    r_bitmap       = temp4;

  LOOKUP_SECONDARY_SUPERS_TABLE_REGISTERS;

  xorq(result, result); // = 0

  movq(r_bitmap, Address(r_sub_klass, Klass::secondary_supers_bitmap_offset()));
  movq(r_array_index, r_bitmap);

  // First check the bitmap to see if super_klass might be present. If
  // the bit is zero, we are certain that super_klass is not one of
  // the secondary supers.
  u1 bit = super_klass_slot;
  {
    // NB: If the count in a x86 shift instruction is 0, the flags are
    // not affected, so we do a testq instead.
    int shift_count = Klass::SECONDARY_SUPERS_TABLE_MASK - bit;
    if (shift_count != 0) {
      salq(r_array_index, shift_count);
    } else {
      testq(r_array_index, r_array_index);
    }
  }
  // We test the MSB of r_array_index, i.e. its sign bit
  jcc(Assembler::positive, L_failure);

  // Get the first array index that can contain super_klass into r_array_index.
  if (bit != 0) {
    population_count(r_array_index, r_array_index, temp2, temp3);
  } else {
    movl(r_array_index, 1);
  }
  // NB! r_array_index is off by 1. It is compensated by keeping r_array_base off by 1 word.

  // We will consult the secondary-super array.
  movptr(r_array_base, Address(r_sub_klass, in_bytes(Klass::secondary_supers_offset())));

  // We're asserting that the first word in an Array<Klass*> is the
  // length, and the second word is the first word of the data. If
  // that ever changes, r_array_base will have to be adjusted here.
  assert(Array<Klass*>::base_offset_in_bytes() == wordSize, "Adjust this code");
  assert(Array<Klass*>::length_offset_in_bytes() == 0, "Adjust this code");

  cmpq(r_super_klass, Address(r_array_base, r_array_index, Address::times_8));
  jccb(Assembler::equal, L_success);

  // Is there another entry to check? Consult the bitmap.
  btq(r_bitmap, (bit + 1) & Klass::SECONDARY_SUPERS_TABLE_MASK);
  jccb(Assembler::carryClear, L_failure);

  // Linear probe. Rotate the bitmap so that the next bit to test is
  // in Bit 1.
  if (bit != 0) {
    rorq(r_bitmap, bit);
  }

  // Calls into the stub generated by lookup_secondary_supers_table_slow_path.
  // Arguments: r_super_klass, r_array_base, r_array_index, r_bitmap.
  // Kills: r_array_length.
  // Returns: result.
  call(RuntimeAddress(StubRoutines::lookup_secondary_supers_table_slow_path_stub()));
  // Result (0/1) is in rdi
  jmpb(L_fallthrough);

  bind(L_failure);
  incq(result); // 0 => 1

  bind(L_success);
  // result = 0;

  bind(L_fallthrough);
  BLOCK_COMMENT("} lookup_secondary_supers_table");

  if (VerifySecondarySupers) {
    verify_secondary_supers_table(r_sub_klass, r_super_klass, result,
                                  temp1, temp2, temp3);
  }
}

// At runtime, return 0 in result if r_super_klass is a superclass of
// r_sub_klass, otherwise return nonzero. Use this version of
// lookup_secondary_supers_table() if you don't know ahead of time
// which superclass will be searched for. Used by interpreter and
// runtime stubs. It is larger and has somewhat greater latency than
// the version above, which takes a constant super_klass_slot.
void MacroAssembler::lookup_secondary_supers_table_var(Register r_sub_klass,
                                                       Register r_super_klass,
                                                       Register temp1,
                                                       Register temp2,
                                                       Register temp3,
                                                       Register temp4,
                                                       Register result) {
  assert_different_registers(r_sub_klass, r_super_klass, temp1, temp2, temp3, temp4, result);
  assert_different_registers(r_sub_klass, r_super_klass, rcx);
  RegSet temps = RegSet::of(temp1, temp2, temp3, temp4);

  Label L_fallthrough, L_success, L_failure;

  BLOCK_COMMENT("lookup_secondary_supers_table {");

  RegSetIterator<Register> available_regs = (temps - rcx).begin();

  // FIXME. Once we are sure that all paths reaching this point really
  // do pass rcx as one of our temps we can get rid of the following
  // workaround.
  assert(temps.contains(rcx), "fix this code");

  // We prefer to have our shift count in rcx. If rcx is one of our
  // temps, use it for slot. If not, pick any of our temps.
  Register slot;
  if (!temps.contains(rcx)) {
    slot = *available_regs++;
  } else {
    slot = rcx;
  }

  const Register r_array_index = *available_regs++;
  const Register r_bitmap      = *available_regs++;

  // The logic above guarantees this property, but we state it here.
  assert_different_registers(r_array_index, r_bitmap, rcx);

  movq(r_bitmap, Address(r_sub_klass, Klass::secondary_supers_bitmap_offset()));
  movq(r_array_index, r_bitmap);

  // First check the bitmap to see if super_klass might be present. If
  // the bit is zero, we are certain that super_klass is not one of
  // the secondary supers.
  movb(slot, Address(r_super_klass, Klass::hash_slot_offset()));
  xorl(slot, (u1)(Klass::SECONDARY_SUPERS_TABLE_SIZE - 1)); // slot ^ 63 === 63 - slot (mod 64)
  salq(r_array_index, slot);

  testq(r_array_index, r_array_index);
  // We test the MSB of r_array_index, i.e. its sign bit
  jcc(Assembler::positive, L_failure);

  const Register r_array_base = *available_regs++;

  // Get the first array index that can contain super_klass into r_array_index.
  // Note: Clobbers r_array_base and slot.
  population_count(r_array_index, r_array_index, /*temp2*/r_array_base, /*temp3*/slot);

  // NB! r_array_index is off by 1. It is compensated by keeping r_array_base off by 1 word.

  // We will consult the secondary-super array.
  movptr(r_array_base, Address(r_sub_klass, in_bytes(Klass::secondary_supers_offset())));

  // We're asserting that the first word in an Array<Klass*> is the
  // length, and the second word is the first word of the data. If
  // that ever changes, r_array_base will have to be adjusted here.
  assert(Array<Klass*>::base_offset_in_bytes() == wordSize, "Adjust this code");
  assert(Array<Klass*>::length_offset_in_bytes() == 0, "Adjust this code");

  cmpq(r_super_klass, Address(r_array_base, r_array_index, Address::times_8));
  jccb(Assembler::equal, L_success);

  // Restore slot to its true value
  movb(slot, Address(r_super_klass, Klass::hash_slot_offset()));

  // Linear probe. Rotate the bitmap so that the next bit to test is
  // in Bit 1.
  rorq(r_bitmap, slot);

  // Is there another entry to check? Consult the bitmap.
  btq(r_bitmap, 1);
  jccb(Assembler::carryClear, L_failure);

  // Calls into the stub generated by lookup_secondary_supers_table_slow_path.
  // Arguments: r_super_klass, r_array_base, r_array_index, r_bitmap.
  // Kills: r_array_length.
  // Returns: result.
  lookup_secondary_supers_table_slow_path(r_super_klass,
                                          r_array_base,
                                          r_array_index,
                                          r_bitmap,
                                          /*temp1*/result,
                                          /*temp2*/slot,
                                          &L_success,
                                          nullptr);

  bind(L_failure);
  movq(result, 1);
  jmpb(L_fallthrough);

  bind(L_success);
  xorq(result, result); // = 0

  bind(L_fallthrough);
  BLOCK_COMMENT("} lookup_secondary_supers_table");

  if (VerifySecondarySupers) {
    verify_secondary_supers_table(r_sub_klass, r_super_klass, result,
                                  temp1, temp2, temp3);
  }
}

void MacroAssembler::repne_scanq(Register addr, Register value, Register count, Register limit,
                                 Label* L_success, Label* L_failure) {
  Label L_loop, L_fallthrough;
  {
    int label_nulls = 0;
    if (L_success == nullptr) { L_success = &L_fallthrough; label_nulls++; }
    if (L_failure == nullptr) { L_failure = &L_fallthrough; label_nulls++; }
    assert(label_nulls <= 1, "at most one null in the batch");
  }
  bind(L_loop);
  cmpq(value, Address(addr, count, Address::times_8));
  jcc(Assembler::equal, *L_success);
  addl(count, 1);
  cmpl(count, limit);
  jcc(Assembler::less, L_loop);

  if (&L_fallthrough != L_failure) {
    jmp(*L_failure);
  }
  bind(L_fallthrough);
}

// Called by code generated by check_klass_subtype_slow_path
// above. This is called when there is a collision in the hashed
// lookup in the secondary supers array.
void MacroAssembler::lookup_secondary_supers_table_slow_path(Register r_super_klass,
                                                             Register r_array_base,
                                                             Register r_array_index,
                                                             Register r_bitmap,
                                                             Register temp1,
                                                             Register temp2,
                                                             Label* L_success,
                                                             Label* L_failure) {
  assert_different_registers(r_super_klass, r_array_base, r_array_index, r_bitmap, temp1, temp2);

  const Register
    r_array_length = temp1,
    r_sub_klass    = noreg,
    result         = noreg;

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == nullptr)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == nullptr)   { L_failure   = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one null in the batch");

  // Load the array length.
  movl(r_array_length, Address(r_array_base, Array<Klass*>::length_offset_in_bytes()));
  // And adjust the array base to point to the data.
  // NB! Effectively increments current slot index by 1.
  assert(Array<Klass*>::base_offset_in_bytes() == wordSize, "");
  addptr(r_array_base, Array<Klass*>::base_offset_in_bytes());

  // Linear probe
  Label L_huge;

  // The bitmap is full to bursting.
  // Implicit invariant: BITMAP_FULL implies (length > 0)
  cmpl(r_array_length, (int32_t)Klass::SECONDARY_SUPERS_TABLE_SIZE - 2);
  jcc(Assembler::greater, L_huge);

  // NB! Our caller has checked bits 0 and 1 in the bitmap. The
  // current slot (at secondary_supers[r_array_index]) has not yet
  // been inspected, and r_array_index may be out of bounds if we
  // wrapped around the end of the array.

  { // This is conventional linear probing, but instead of terminating
    // when a null entry is found in the table, we maintain a bitmap
    // in which a 0 indicates missing entries.
    // The check above guarantees there are 0s in the bitmap, so the loop
    // eventually terminates.

    xorl(temp2, temp2); // = 0;

    Label L_again;
    bind(L_again);

    // Check for array wraparound.
    cmpl(r_array_index, r_array_length);
    cmovl(Assembler::greaterEqual, r_array_index, temp2);

    cmpq(r_super_klass, Address(r_array_base, r_array_index, Address::times_8));
    jcc(Assembler::equal, *L_success);

    // If the next bit in bitmap is zero, we're done.
    btq(r_bitmap, 2); // look-ahead check (Bit 2); Bits 0 and 1 are tested by now
    jcc(Assembler::carryClear, *L_failure);

    rorq(r_bitmap, 1); // Bits 1/2 => 0/1
    addl(r_array_index, 1);

    jmp(L_again);
  }

  { // Degenerate case: more than 64 secondary supers.
    // FIXME: We could do something smarter here, maybe a vectorized
    // comparison or a binary search, but is that worth any added
    // complexity?
    bind(L_huge);
    xorl(r_array_index, r_array_index); // = 0
    repne_scanq(r_array_base, r_super_klass, r_array_index, r_array_length,
                L_success,
                (&L_fallthrough != L_failure ? L_failure : nullptr));

    bind(L_fallthrough);
  }
}

struct VerifyHelperArguments {
  Klass* _super;
  Klass* _sub;
  intptr_t _linear_result;
  intptr_t _table_result;
};

static void verify_secondary_supers_table_helper(const char* msg, VerifyHelperArguments* args) {
  Klass::on_secondary_supers_verification_failure(args->_super,
                                                  args->_sub,
                                                  args->_linear_result,
                                                  args->_table_result,
                                                  msg);
}

// Make sure that the hashed lookup and a linear scan agree.
void MacroAssembler::verify_secondary_supers_table(Register r_sub_klass,
                                                   Register r_super_klass,
                                                   Register result,
                                                   Register temp1,
                                                   Register temp2,
                                                   Register temp3) {
  const Register
      r_array_index  = temp1,
      r_array_length = temp2,
      r_array_base   = temp3,
      r_bitmap       = noreg;

  BLOCK_COMMENT("verify_secondary_supers_table {");

  Label L_success, L_failure, L_check, L_done;

  movptr(r_array_base, Address(r_sub_klass, in_bytes(Klass::secondary_supers_offset())));
  movl(r_array_length, Address(r_array_base, Array<Klass*>::length_offset_in_bytes()));
  // And adjust the array base to point to the data.
  addptr(r_array_base, Array<Klass*>::base_offset_in_bytes());

  testl(r_array_length, r_array_length); // array_length == 0?
  jcc(Assembler::zero, L_failure);

  movl(r_array_index, 0);
  repne_scanq(r_array_base, r_super_klass, r_array_index, r_array_length, &L_success);
  // fall through to L_failure

  const Register linear_result = r_array_index; // reuse temp1

  bind(L_failure); // not present
  movl(linear_result, 1);
  jmp(L_check);

  bind(L_success); // present
  movl(linear_result, 0);

  bind(L_check);
  cmpl(linear_result, result);
  jcc(Assembler::equal, L_done);

  { // To avoid calling convention issues, build a record on the stack
    // and pass the pointer to that instead.
    push(result);
    push(linear_result);
    push(r_sub_klass);
    push(r_super_klass);
    movptr(c_rarg1, rsp);
    movptr(c_rarg0, (uintptr_t) "mismatch");
    call(RuntimeAddress(CAST_FROM_FN_PTR(address, verify_secondary_supers_table_helper)));
    should_not_reach_here();
  }
  bind(L_done);

  BLOCK_COMMENT("} verify_secondary_supers_table");
}

#undef LOOKUP_SECONDARY_SUPERS_TABLE_REGISTERS

void MacroAssembler::clinit_barrier(Register klass, Label* L_fast_path, Label* L_slow_path) {
  assert(L_fast_path != nullptr || L_slow_path != nullptr, "at least one is required");

  Label L_fallthrough;
  if (L_fast_path == nullptr) {
    L_fast_path = &L_fallthrough;
  } else if (L_slow_path == nullptr) {
    L_slow_path = &L_fallthrough;
  }

  // Fast path check: class is fully initialized.
  // init_state needs acquire, but x86 is TSO, and so we are already good.
  cmpb(Address(klass, InstanceKlass::init_state_offset()), InstanceKlass::fully_initialized);
  jcc(Assembler::equal, *L_fast_path);

  // Fast path check: current thread is initializer thread
  cmpptr(r15_thread, Address(klass, InstanceKlass::init_thread_offset()));
  if (L_slow_path == &L_fallthrough) {
    jcc(Assembler::equal, *L_fast_path);
    bind(*L_slow_path);
  } else if (L_fast_path == &L_fallthrough) {
    jcc(Assembler::notEqual, *L_slow_path);
    bind(*L_fast_path);
  } else {
    Unimplemented();
  }
}

void MacroAssembler::cmov32(Condition cc, Register dst, Address src) {
  if (VM_Version::supports_cmov()) {
    cmovl(cc, dst, src);
  } else {
    Label L;
    jccb(negate_condition(cc), L);
    movl(dst, src);
    bind(L);
  }
}

void MacroAssembler::cmov32(Condition cc, Register dst, Register src) {
  if (VM_Version::supports_cmov()) {
    cmovl(cc, dst, src);
  } else {
    Label L;
    jccb(negate_condition(cc), L);
    movl(dst, src);
    bind(L);
  }
}

void MacroAssembler::_verify_oop(Register reg, const char* s, const char* file, int line) {
  if (!VerifyOops) return;

  BLOCK_COMMENT("verify_oop {");
  push(rscratch1);
  push(rax);                          // save rax
  push(reg);                          // pass register argument

  // Pass register number to verify_oop_subroutine
  const char* b = nullptr;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("verify_oop: %s: %s (%s:%d)", reg->name(), s, file, line);
    b = code_string(ss.as_string());
  }
  AddressLiteral buffer((address) b, external_word_Relocation::spec_for_immediate());
  pushptr(buffer.addr(), rscratch1);

  // call indirectly to solve generation ordering problem
  movptr(rax, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  call(rax);
  // Caller pops the arguments (oop, message) and restores rax, r10
  BLOCK_COMMENT("} verify_oop");
}

void MacroAssembler::vallones(XMMRegister dst, int vector_len) {
  if (UseAVX > 2 && (vector_len == Assembler::AVX_512bit || VM_Version::supports_avx512vl())) {
    // Only pcmpeq has dependency breaking treatment (i.e the execution can begin without
    // waiting for the previous result on dst), not vpcmpeqd, so just use vpternlog
    vpternlogd(dst, 0xFF, dst, dst, vector_len);
  } else if (VM_Version::supports_avx()) {
    vpcmpeqd(dst, dst, dst, vector_len);
  } else {
    pcmpeqd(dst, dst);
  }
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
  Register             scale_reg    = noreg;
  Address::ScaleFactor scale_factor = Address::no_scale;
  if (arg_slot.is_constant()) {
    offset += arg_slot.as_constant() * stackElementSize;
  } else {
    scale_reg    = arg_slot.as_register();
    scale_factor = Address::times(stackElementSize);
  }
  offset += wordSize;           // return PC is on stack
  return Address(rsp, scale_reg, scale_factor, offset);
}

// Handle the receiver type profile update given the "recv" klass.
//
// Normally updates the ReceiverData (RD) that starts at "mdp" + "mdp_offset".
// If there are no matching or claimable receiver entries in RD, updates
// the polymorphic counter.
//
// This code expected to run by either the interpreter or JIT-ed code, without
// extra synchronization. For safety, receiver cells are claimed atomically, which
// avoids grossly misrepresenting the profiles under concurrent updates. For speed,
// counter updates are not atomic.
//
void MacroAssembler::profile_receiver_type(Register recv, Register mdp, int mdp_offset) {
  int base_receiver_offset   = in_bytes(ReceiverTypeData::receiver_offset(0));
  int end_receiver_offset    = in_bytes(ReceiverTypeData::receiver_offset(ReceiverTypeData::row_limit()));
  int poly_count_offset      = in_bytes(CounterData::count_offset());
  int receiver_step          = in_bytes(ReceiverTypeData::receiver_offset(1)) - base_receiver_offset;
  int receiver_to_count_step = in_bytes(ReceiverTypeData::receiver_count_offset(0)) - base_receiver_offset;

  // Adjust for MDP offsets. Slots are pointer-sized, so is the global offset.
  assert(is_aligned(mdp_offset, BytesPerWord), "sanity");
  base_receiver_offset += mdp_offset;
  end_receiver_offset  += mdp_offset;
  poly_count_offset    += mdp_offset;

  // Scale down to optimize encoding. Slots are pointer-sized.
  assert(is_aligned(base_receiver_offset,   BytesPerWord), "sanity");
  assert(is_aligned(end_receiver_offset,    BytesPerWord), "sanity");
  assert(is_aligned(poly_count_offset,      BytesPerWord), "sanity");
  assert(is_aligned(receiver_step,          BytesPerWord), "sanity");
  assert(is_aligned(receiver_to_count_step, BytesPerWord), "sanity");
  base_receiver_offset   >>= LogBytesPerWord;
  end_receiver_offset    >>= LogBytesPerWord;
  poly_count_offset      >>= LogBytesPerWord;
  receiver_step          >>= LogBytesPerWord;
  receiver_to_count_step >>= LogBytesPerWord;

#ifdef ASSERT
  // We are about to walk the MDO slots without asking for offsets.
  // Check that our math hits all the right spots.
  for (uint c = 0; c < ReceiverTypeData::row_limit(); c++) {
    int real_recv_offset  = mdp_offset + in_bytes(ReceiverTypeData::receiver_offset(c));
    int real_count_offset = mdp_offset + in_bytes(ReceiverTypeData::receiver_count_offset(c));
    int offset = base_receiver_offset + receiver_step*c;
    int count_offset = offset + receiver_to_count_step;
    assert((offset << LogBytesPerWord) == real_recv_offset, "receiver slot math");
    assert((count_offset << LogBytesPerWord) == real_count_offset, "receiver count math");
  }
  int real_poly_count_offset = mdp_offset + in_bytes(CounterData::count_offset());
  assert(poly_count_offset << LogBytesPerWord == real_poly_count_offset, "poly counter math");
#endif

  // Corner case: no profile table. Increment poly counter and exit.
  if (ReceiverTypeData::row_limit() == 0) {
    addptr(Address(mdp, poly_count_offset, Address::times_ptr), DataLayout::counter_increment);
    return;
  }

  Register offset = rscratch1;

  Label L_loop_search_receiver, L_loop_search_empty;
  Label L_restart, L_found_recv, L_found_empty, L_polymorphic, L_count_update;

  // The code here recognizes three major cases:
  //   A. Fastest: receiver found in the table
  //   B. Fast: no receiver in the table, and the table is full
  //   C. Slow: no receiver in the table, free slots in the table
  //
  // The case A performance is most important, as perfectly-behaved code would end up
  // there, especially with larger TypeProfileWidth. The case B performance is
  // important as well, this is where bulk of code would land for normally megamorphic
  // cases. The case C performance is not essential, its job is to deal with installation
  // races, we optimize for code density instead. Case C needs to make sure that receiver
  // rows are only claimed once. This makes sure we never overwrite a row for another
  // receiver and never duplicate the receivers in the list, making profile type-accurate.
  //
  // It is very tempting to handle these cases in a single loop, and claim the first slot
  // without checking the rest of the table. But, profiling code should tolerate free slots
  // in the table, as class unloading can clear them. After such cleanup, the receiver
  // we need might be _after_ the free slot. Therefore, we need to let at least full scan
  // to complete, before trying to install new slots. Splitting the code in several tight
  // loops also helpfully optimizes for cases A and B.
  //
  // This code is effectively:
  //
  // restart:
  //   // Fastest: receiver is already installed
  //   for (i = 0; i < receiver_count(); i++) {
  //     if (receiver(i) == recv) goto found_recv(i);
  //   }
  //
  //   // Fast: no receiver, but profile is full
  //   for (i = 0; i < receiver_count(); i++) {
  //     if (receiver(i) == null) goto found_null(i);
  //   }
  //   goto polymorphic
  //
  //   // Slow: try to install receiver
  // found_null(i):
  //   CAS(&receiver(i), null, recv);
  //   goto restart
  //
  // polymorphic:
  //   count++;
  //   return
  //
  // found_recv(i):
  //   *receiver_count(i)++
  //

  bind(L_restart);

  // Fastest: receiver is already installed
  movptr(offset, base_receiver_offset);
  bind(L_loop_search_receiver);
    cmpptr(recv, Address(mdp, offset, Address::times_ptr));
    jccb(Assembler::equal, L_found_recv);
  addptr(offset, receiver_step);
  cmpptr(offset, end_receiver_offset);
  jccb(Assembler::notEqual, L_loop_search_receiver);

  // Fast: no receiver, but profile is full
  movptr(offset, base_receiver_offset);
  bind(L_loop_search_empty);
    cmpptr(Address(mdp, offset, Address::times_ptr), NULL_WORD);
    jccb(Assembler::equal, L_found_empty);
  addptr(offset, receiver_step);
  cmpptr(offset, end_receiver_offset);
  jccb(Assembler::notEqual, L_loop_search_empty);
  jmpb(L_polymorphic);

  // Slow: try to install receiver
  bind(L_found_empty);

  // Atomically swing receiver slot: null -> recv.
  //
  // The update code uses CAS, which wants RAX register specifically, *and* it needs
  // other important registers untouched, as they form the address. Therefore, we need
  // to shift any important registers from RAX into some other spare register. If we
  // have a spare register, we are forced to save it on stack here.

  Register spare_reg = noreg;
  Register shifted_mdp = mdp;
  Register shifted_recv = recv;
  if (recv == rax || mdp == rax) {
    spare_reg = (recv != rbx && mdp != rbx) ? rbx :
                (recv != rcx && mdp != rcx) ? rcx :
                rdx;
    assert_different_registers(mdp, recv, offset, spare_reg);

    push(spare_reg);
    if (recv == rax) {
      movptr(spare_reg, recv);
      shifted_recv = spare_reg;
    } else {
      assert(mdp == rax, "Remaining case");
      movptr(spare_reg, mdp);
      shifted_mdp = spare_reg;
    }
  } else {
    push(rax);
  }

  // None of the important registers are in RAX after this shuffle.
  assert_different_registers(rax, shifted_mdp, shifted_recv, offset);

  xorptr(rax, rax);
  cmpxchgptr(shifted_recv, Address(shifted_mdp, offset, Address::times_ptr));

  // Unshift registers.
  if (recv == rax || mdp == rax) {
    movptr(rax, spare_reg);
    pop(spare_reg);
  } else {
    pop(rax);
  }

  // CAS success means the slot now has the receiver we want. CAS failure means
  // something had claimed the slot concurrently: it can be the same receiver we want,
  // or something else. Since this is a slow path, we can optimize for code density,
  // and just restart the search from the beginning.
  jmpb(L_restart);

  // Counter updates:

  // Increment polymorphic counter instead of receiver slot.
  bind(L_polymorphic);
  movptr(offset, poly_count_offset);
  jmpb(L_count_update);

  // Found a receiver, convert its slot offset to corresponding count offset.
  bind(L_found_recv);
  addptr(offset, receiver_to_count_step);

  bind(L_count_update);
  addptr(Address(mdp, offset, Address::times_ptr), DataLayout::counter_increment);
}

void MacroAssembler::_verify_oop_addr(Address addr, const char* s, const char* file, int line) {
  if (!VerifyOops) return;

  push(rscratch1);
  push(rax); // save rax,
  // addr may contain rsp so we will have to adjust it based on the push
  // we just did (and on 64 bit we do two pushes)
  // NOTE: 64bit seemed to have had a bug in that it did movq(addr, rax); which
  // stores rax into addr which is backwards of what was intended.
  if (addr.uses(rsp)) {
    lea(rax, addr);
    pushptr(Address(rax, 2 * BytesPerWord));
  } else {
    pushptr(addr);
  }

  // Pass register number to verify_oop_subroutine
  const char* b = nullptr;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("verify_oop_addr: %s (%s:%d)", s, file, line);
    b = code_string(ss.as_string());
  }
  AddressLiteral buffer((address) b, external_word_Relocation::spec_for_immediate());
  pushptr(buffer.addr(), rscratch1);

  // call indirectly to solve generation ordering problem
  movptr(rax, ExternalAddress(StubRoutines::verify_oop_subroutine_entry_address()));
  call(rax);
  // Caller pops the arguments (addr, message) and restores rax, r10.
}

void MacroAssembler::verify_tlab() {
#ifdef ASSERT
  if (UseTLAB && VerifyOops) {
    Label next, ok;
    Register t1 = rsi;

    push(t1);

    movptr(t1, Address(r15_thread, in_bytes(JavaThread::tlab_top_offset())));
    cmpptr(t1, Address(r15_thread, in_bytes(JavaThread::tlab_start_offset())));
    jcc(Assembler::aboveEqual, next);
    STOP("assert(top >= start)");
    should_not_reach_here();

    bind(next);
    movptr(t1, Address(r15_thread, in_bytes(JavaThread::tlab_end_offset())));
    cmpptr(t1, Address(r15_thread, in_bytes(JavaThread::tlab_top_offset())));
    jcc(Assembler::aboveEqual, ok);
    STOP("assert(top <= end)");
    should_not_reach_here();

    bind(ok);
    pop(t1);
  }
#endif
}

class ControlWord {
 public:
  int32_t _value;

  int  rounding_control() const        { return  (_value >> 10) & 3      ; }
  int  precision_control() const       { return  (_value >>  8) & 3      ; }
  bool precision() const               { return ((_value >>  5) & 1) != 0; }
  bool underflow() const               { return ((_value >>  4) & 1) != 0; }
  bool overflow() const                { return ((_value >>  3) & 1) != 0; }
  bool zero_divide() const             { return ((_value >>  2) & 1) != 0; }
  bool denormalized() const            { return ((_value >>  1) & 1) != 0; }
  bool invalid() const                 { return ((_value >>  0) & 1) != 0; }

  void print() const {
    // rounding control
    const char* rc;
    switch (rounding_control()) {
      case 0: rc = "round near"; break;
      case 1: rc = "round down"; break;
      case 2: rc = "round up  "; break;
      case 3: rc = "chop      "; break;
      default:
        rc = nullptr; // silence compiler warnings
        fatal("Unknown rounding control: %d", rounding_control());
    };
    // precision control
    const char* pc;
    switch (precision_control()) {
      case 0: pc = "24 bits "; break;
      case 1: pc = "reserved"; break;
      case 2: pc = "53 bits "; break;
      case 3: pc = "64 bits "; break;
      default:
        pc = nullptr; // silence compiler warnings
        fatal("Unknown precision control: %d", precision_control());
    };
    // flags
    char f[9];
    f[0] = ' ';
    f[1] = ' ';
    f[2] = (precision   ()) ? 'P' : 'p';
    f[3] = (underflow   ()) ? 'U' : 'u';
    f[4] = (overflow    ()) ? 'O' : 'o';
    f[5] = (zero_divide ()) ? 'Z' : 'z';
    f[6] = (denormalized()) ? 'D' : 'd';
    f[7] = (invalid     ()) ? 'I' : 'i';
    f[8] = '\x0';
    // output
    printf("%04x  masks = %s, %s, %s", _value & 0xFFFF, f, rc, pc);
  }

};

class StatusWord {
 public:
  int32_t _value;

  bool busy() const                    { return ((_value >> 15) & 1) != 0; }
  bool C3() const                      { return ((_value >> 14) & 1) != 0; }
  bool C2() const                      { return ((_value >> 10) & 1) != 0; }
  bool C1() const                      { return ((_value >>  9) & 1) != 0; }
  bool C0() const                      { return ((_value >>  8) & 1) != 0; }
  int  top() const                     { return  (_value >> 11) & 7      ; }
  bool error_status() const            { return ((_value >>  7) & 1) != 0; }
  bool stack_fault() const             { return ((_value >>  6) & 1) != 0; }
  bool precision() const               { return ((_value >>  5) & 1) != 0; }
  bool underflow() const               { return ((_value >>  4) & 1) != 0; }
  bool overflow() const                { return ((_value >>  3) & 1) != 0; }
  bool zero_divide() const             { return ((_value >>  2) & 1) != 0; }
  bool denormalized() const            { return ((_value >>  1) & 1) != 0; }
  bool invalid() const                 { return ((_value >>  0) & 1) != 0; }

  void print() const {
    // condition codes
    char c[5];
    c[0] = (C3()) ? '3' : '-';
    c[1] = (C2()) ? '2' : '-';
    c[2] = (C1()) ? '1' : '-';
    c[3] = (C0()) ? '0' : '-';
    c[4] = '\x0';
    // flags
    char f[9];
    f[0] = (error_status()) ? 'E' : '-';
    f[1] = (stack_fault ()) ? 'S' : '-';
    f[2] = (precision   ()) ? 'P' : '-';
    f[3] = (underflow   ()) ? 'U' : '-';
    f[4] = (overflow    ()) ? 'O' : '-';
    f[5] = (zero_divide ()) ? 'Z' : '-';
    f[6] = (denormalized()) ? 'D' : '-';
    f[7] = (invalid     ()) ? 'I' : '-';
    f[8] = '\x0';
    // output
    printf("%04x  flags = %s, cc =  %s, top = %d", _value & 0xFFFF, f, c, top());
  }

};

class TagWord {
 public:
  int32_t _value;

  int tag_at(int i) const              { return (_value >> (i*2)) & 3; }

  void print() const {
    printf("%04x", _value & 0xFFFF);
  }

};

class FPU_Register {
 public:
  int32_t _m0;
  int32_t _m1;
  int16_t _ex;

  bool is_indefinite() const           {
    return _ex == -1 && _m1 == (int32_t)0xC0000000 && _m0 == 0;
  }

  void print() const {
    char  sign = (_ex < 0) ? '-' : '+';
    const char* kind = (_ex == 0x7FFF || _ex == (int16_t)-1) ? "NaN" : "   ";
    printf("%c%04hx.%08x%08x  %s", sign, _ex, _m1, _m0, kind);
  };

};

class FPU_State {
 public:
  enum {
    register_size       = 10,
    number_of_registers =  8,
    register_mask       =  7
  };

  ControlWord  _control_word;
  StatusWord   _status_word;
  TagWord      _tag_word;
  int32_t      _error_offset;
  int32_t      _error_selector;
  int32_t      _data_offset;
  int32_t      _data_selector;
  int8_t       _register[register_size * number_of_registers];

  int tag_for_st(int i) const          { return _tag_word.tag_at((_status_word.top() + i) & register_mask); }
  FPU_Register* st(int i) const        { return (FPU_Register*)&_register[register_size * i]; }

  const char* tag_as_string(int tag) const {
    switch (tag) {
      case 0: return "valid";
      case 1: return "zero";
      case 2: return "special";
      case 3: return "empty";
    }
    ShouldNotReachHere();
    return nullptr;
  }

  void print() const {
    // print computation registers
    { int t = _status_word.top();
      for (int i = 0; i < number_of_registers; i++) {
        int j = (i - t) & register_mask;
        printf("%c r%d = ST%d = ", (j == 0 ? '*' : ' '), i, j);
        st(j)->print();
        printf(" %s\n", tag_as_string(_tag_word.tag_at(i)));
      }
    }
    printf("\n");
    // print control registers
    printf("ctrl = "); _control_word.print(); printf("\n");
    printf("stat = "); _status_word .print(); printf("\n");
    printf("tags = "); _tag_word    .print(); printf("\n");
  }

};

class Flag_Register {
 public:
  int32_t _value;

  bool overflow() const                { return ((_value >> 11) & 1) != 0; }
  bool direction() const               { return ((_value >> 10) & 1) != 0; }
  bool sign() const                    { return ((_value >>  7) & 1) != 0; }
  bool zero() const                    { return ((_value >>  6) & 1) != 0; }
  bool auxiliary_carry() const         { return ((_value >>  4) & 1) != 0; }
  bool parity() const                  { return ((_value >>  2) & 1) != 0; }
  bool carry() const                   { return ((_value >>  0) & 1) != 0; }

  void print() const {
    // flags
    char f[8];
    f[0] = (overflow       ()) ? 'O' : '-';
    f[1] = (direction      ()) ? 'D' : '-';
    f[2] = (sign           ()) ? 'S' : '-';
    f[3] = (zero           ()) ? 'Z' : '-';
    f[4] = (auxiliary_carry()) ? 'A' : '-';
    f[5] = (parity         ()) ? 'P' : '-';
    f[6] = (carry          ()) ? 'C' : '-';
    f[7] = '\x0';
    // output
    printf("%08x  flags = %s", _value, f);
  }

};

class IU_Register {
 public:
  int32_t _value;

  void print() const {
    printf("%08x  %11d", _value, _value);
  }

};

class IU_State {
 public:
  Flag_Register _eflags;
  IU_Register   _rdi;
  IU_Register   _rsi;
  IU_Register   _rbp;
  IU_Register   _rsp;
  IU_Register   _rbx;
  IU_Register   _rdx;
  IU_Register   _rcx;
  IU_Register   _rax;

  void print() const {
    // computation registers
    printf("rax,  = "); _rax.print(); printf("\n");
    printf("rbx,  = "); _rbx.print(); printf("\n");
    printf("rcx  = "); _rcx.print(); printf("\n");
    printf("rdx  = "); _rdx.print(); printf("\n");
    printf("rdi  = "); _rdi.print(); printf("\n");
    printf("rsi  = "); _rsi.print(); printf("\n");
    printf("rbp,  = "); _rbp.print(); printf("\n");
    printf("rsp  = "); _rsp.print(); printf("\n");
    printf("\n");
    // control registers
    printf("flgs = "); _eflags.print(); printf("\n");
  }
};


class CPU_State {
 public:
  FPU_State _fpu_state;
  IU_State  _iu_state;

  void print() const {
    printf("--------------------------------------------------\n");
    _iu_state .print();
    printf("\n");
    _fpu_state.print();
    printf("--------------------------------------------------\n");
  }

};


static void _print_CPU_state(CPU_State* state) {
  state->print();
};


void MacroAssembler::print_CPU_state() {
  push_CPU_state();
  push(rsp);                // pass CPU state
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, _print_CPU_state)));
  addptr(rsp, wordSize);       // discard argument
  pop_CPU_state();
}

void MacroAssembler::restore_cpu_control_state_after_jni(Register rscratch) {
  // Either restore the MXCSR register after returning from the JNI Call
  // or verify that it wasn't changed (with -Xcheck:jni flag).
  if (VM_Version::supports_sse()) {
    if (RestoreMXCSROnJNICalls) {
      ldmxcsr(ExternalAddress(StubRoutines::x86::addr_mxcsr_std()), rscratch);
    } else if (CheckJNICalls) {
      call(RuntimeAddress(StubRoutines::x86::verify_mxcsr_entry()));
    }
  }
  // Clear upper bits of YMM registers to avoid SSE <-> AVX transition penalty.
  vzeroupper();
}

// ((OopHandle)result).resolve();
void MacroAssembler::resolve_oop_handle(Register result, Register tmp) {
  assert_different_registers(result, tmp);

  // Only 64 bit platforms support GCs that require a tmp register
  // Only IN_HEAP loads require a thread_tmp register
  // OopHandle::resolve is an indirection like jobject.
  access_load_at(T_OBJECT, IN_NATIVE,
                 result, Address(result, 0), tmp);
}

// ((WeakHandle)result).resolve();
void MacroAssembler::resolve_weak_handle(Register rresult, Register rtmp) {
  assert_different_registers(rresult, rtmp);
  Label resolved;

  // A null weak handle resolves to null.
  cmpptr(rresult, 0);
  jcc(Assembler::equal, resolved);

  // Only 64 bit platforms support GCs that require a tmp register
  // Only IN_HEAP loads require a thread_tmp register
  // WeakHandle::resolve is an indirection like jweak.
  access_load_at(T_OBJECT, IN_NATIVE | ON_PHANTOM_OOP_REF,
                 rresult, Address(rresult, 0), rtmp);
  bind(resolved);
}

void MacroAssembler::load_mirror(Register mirror, Register method, Register tmp) {
  // get mirror
  const int mirror_offset = in_bytes(Klass::java_mirror_offset());
  load_method_holder(mirror, method);
  movptr(mirror, Address(mirror, mirror_offset));
  resolve_oop_handle(mirror, tmp);
}

void MacroAssembler::load_method_holder_cld(Register rresult, Register rmethod) {
  load_method_holder(rresult, rmethod);
  movptr(rresult, Address(rresult, InstanceKlass::class_loader_data_offset()));
}

void MacroAssembler::load_method_holder(Register holder, Register method) {
  movptr(holder, Address(method, Method::const_offset()));                      // ConstMethod*
  movptr(holder, Address(holder, ConstMethod::constants_offset()));             // ConstantPool*
  movptr(holder, Address(holder, ConstantPool::pool_holder_offset()));          // InstanceKlass*
}

void MacroAssembler::load_narrow_klass_compact(Register dst, Register src) {
  assert(UseCompactObjectHeaders, "expect compact object headers");
  movq(dst, Address(src, oopDesc::mark_offset_in_bytes()));
  shrq(dst, markWord::klass_shift);
}

void MacroAssembler::load_klass(Register dst, Register src, Register tmp) {
  assert_different_registers(src, tmp);
  assert_different_registers(dst, tmp);

  if (UseCompactObjectHeaders) {
    load_narrow_klass_compact(dst, src);
    decode_klass_not_null(dst, tmp);
  } else if (UseCompressedClassPointers) {
    movl(dst, Address(src, oopDesc::klass_offset_in_bytes()));
    decode_klass_not_null(dst, tmp);
  } else {
    movptr(dst, Address(src, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::store_klass(Register dst, Register src, Register tmp) {
  assert(!UseCompactObjectHeaders, "not with compact headers");
  assert_different_registers(src, tmp);
  assert_different_registers(dst, tmp);
  if (UseCompressedClassPointers) {
    encode_klass_not_null(src, tmp);
    movl(Address(dst, oopDesc::klass_offset_in_bytes()), src);
  } else {
    movptr(Address(dst, oopDesc::klass_offset_in_bytes()), src);
  }
}

void MacroAssembler::cmp_klass(Register klass, Register obj, Register tmp) {
  if (UseCompactObjectHeaders) {
    assert(tmp != noreg, "need tmp");
    assert_different_registers(klass, obj, tmp);
    load_narrow_klass_compact(tmp, obj);
    cmpl(klass, tmp);
  } else if (UseCompressedClassPointers) {
    cmpl(klass, Address(obj, oopDesc::klass_offset_in_bytes()));
  } else {
    cmpptr(klass, Address(obj, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::cmp_klasses_from_objects(Register obj1, Register obj2, Register tmp1, Register tmp2) {
  if (UseCompactObjectHeaders) {
    assert(tmp2 != noreg, "need tmp2");
    assert_different_registers(obj1, obj2, tmp1, tmp2);
    load_narrow_klass_compact(tmp1, obj1);
    load_narrow_klass_compact(tmp2, obj2);
    cmpl(tmp1, tmp2);
  } else if (UseCompressedClassPointers) {
    movl(tmp1, Address(obj1, oopDesc::klass_offset_in_bytes()));
    cmpl(tmp1, Address(obj2, oopDesc::klass_offset_in_bytes()));
  } else {
    movptr(tmp1, Address(obj1, oopDesc::klass_offset_in_bytes()));
    cmpptr(tmp1, Address(obj2, oopDesc::klass_offset_in_bytes()));
  }
}

void MacroAssembler::access_load_at(BasicType type, DecoratorSet decorators, Register dst, Address src,
                                    Register tmp1) {
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  decorators = AccessInternal::decorator_fixup(decorators, type);
  bool as_raw = (decorators & AS_RAW) != 0;
  if (as_raw) {
    bs->BarrierSetAssembler::load_at(this, decorators, type, dst, src, tmp1);
  } else {
    bs->load_at(this, decorators, type, dst, src, tmp1);
  }
}

void MacroAssembler::access_store_at(BasicType type, DecoratorSet decorators, Address dst, Register val,
                                     Register tmp1, Register tmp2, Register tmp3) {
  BarrierSetAssembler* bs = BarrierSet::barrier_set()->barrier_set_assembler();
  decorators = AccessInternal::decorator_fixup(decorators, type);
  bool as_raw = (decorators & AS_RAW) != 0;
  if (as_raw) {
    bs->BarrierSetAssembler::store_at(this, decorators, type, dst, val, tmp1, tmp2, tmp3);
  } else {
    bs->store_at(this, decorators, type, dst, val, tmp1, tmp2, tmp3);
  }
}

void MacroAssembler::load_heap_oop(Register dst, Address src, Register tmp1, DecoratorSet decorators) {
  access_load_at(T_OBJECT, IN_HEAP | decorators, dst, src, tmp1);
}

// Doesn't do verification, generates fixed size code
void MacroAssembler::load_heap_oop_not_null(Register dst, Address src, Register tmp1, DecoratorSet decorators) {
  access_load_at(T_OBJECT, IN_HEAP | IS_NOT_NULL | decorators, dst, src, tmp1);
}

void MacroAssembler::store_heap_oop(Address dst, Register val, Register tmp1,
                                    Register tmp2, Register tmp3, DecoratorSet decorators) {
  access_store_at(T_OBJECT, IN_HEAP | decorators, dst, val, tmp1, tmp2, tmp3);
}

// Used for storing nulls.
void MacroAssembler::store_heap_oop_null(Address dst) {
  access_store_at(T_OBJECT, IN_HEAP, dst, noreg, noreg, noreg, noreg);
}

void MacroAssembler::store_klass_gap(Register dst, Register src) {
  assert(!UseCompactObjectHeaders, "Don't use with compact headers");
  if (UseCompressedClassPointers) {
    // Store to klass gap in destination
    movl(Address(dst, oopDesc::klass_gap_offset_in_bytes()), src);
  }
}

#ifdef ASSERT
void MacroAssembler::verify_heapbase(const char* msg) {
  assert (UseCompressedOops, "should be compressed");
  assert (Universe::heap() != nullptr, "java heap should be initialized");
  if (CheckCompressedOops) {
    Label ok;
    ExternalAddress src2(CompressedOops::base_addr());
    const bool is_src2_reachable = reachable(src2);
    if (!is_src2_reachable) {
      push(rscratch1);  // cmpptr trashes rscratch1
    }
    cmpptr(r12_heapbase, src2, rscratch1);
    jcc(Assembler::equal, ok);
    STOP(msg);
    bind(ok);
    if (!is_src2_reachable) {
      pop(rscratch1);
    }
  }
}
#endif

// Algorithm must match oop.inline.hpp encode_heap_oop.
void MacroAssembler::encode_heap_oop(Register r) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::encode_heap_oop: heap base corrupted?");
#endif
  verify_oop_msg(r, "broken oop in encode_heap_oop");
  if (CompressedOops::base() == nullptr) {
    if (CompressedOops::shift() != 0) {
      assert (LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
      shrq(r, LogMinObjAlignmentInBytes);
    }
    return;
  }
  testq(r, r);
  cmovq(Assembler::equal, r, r12_heapbase);
  subq(r, r12_heapbase);
  shrq(r, LogMinObjAlignmentInBytes);
}

void MacroAssembler::encode_heap_oop_not_null(Register r) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::encode_heap_oop_not_null: heap base corrupted?");
  if (CheckCompressedOops) {
    Label ok;
    testq(r, r);
    jcc(Assembler::notEqual, ok);
    STOP("null oop passed to encode_heap_oop_not_null");
    bind(ok);
  }
#endif
  verify_oop_msg(r, "broken oop in encode_heap_oop_not_null");
  if (CompressedOops::base() != nullptr) {
    subq(r, r12_heapbase);
  }
  if (CompressedOops::shift() != 0) {
    assert (LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
    shrq(r, LogMinObjAlignmentInBytes);
  }
}

void MacroAssembler::encode_heap_oop_not_null(Register dst, Register src) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::encode_heap_oop_not_null2: heap base corrupted?");
  if (CheckCompressedOops) {
    Label ok;
    testq(src, src);
    jcc(Assembler::notEqual, ok);
    STOP("null oop passed to encode_heap_oop_not_null2");
    bind(ok);
  }
#endif
  verify_oop_msg(src, "broken oop in encode_heap_oop_not_null2");
  if (dst != src) {
    movq(dst, src);
  }
  if (CompressedOops::base() != nullptr) {
    subq(dst, r12_heapbase);
  }
  if (CompressedOops::shift() != 0) {
    assert (LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
    shrq(dst, LogMinObjAlignmentInBytes);
  }
}

void  MacroAssembler::decode_heap_oop(Register r) {
#ifdef ASSERT
  verify_heapbase("MacroAssembler::decode_heap_oop: heap base corrupted?");
#endif
  if (CompressedOops::base() == nullptr) {
    if (CompressedOops::shift() != 0) {
      assert (LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
      shlq(r, LogMinObjAlignmentInBytes);
    }
  } else {
    Label done;
    shlq(r, LogMinObjAlignmentInBytes);
    jccb(Assembler::equal, done);
    addq(r, r12_heapbase);
    bind(done);
  }
  verify_oop_msg(r, "broken oop in decode_heap_oop");
}

void  MacroAssembler::decode_heap_oop_not_null(Register r) {
  // Note: it will change flags
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != nullptr, "java heap should be initialized");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  if (CompressedOops::shift() != 0) {
    assert(LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
    shlq(r, LogMinObjAlignmentInBytes);
    if (CompressedOops::base() != nullptr) {
      addq(r, r12_heapbase);
    }
  } else {
    assert (CompressedOops::base() == nullptr, "sanity");
  }
}

void  MacroAssembler::decode_heap_oop_not_null(Register dst, Register src) {
  // Note: it will change flags
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != nullptr, "java heap should be initialized");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  if (CompressedOops::shift() != 0) {
    assert(LogMinObjAlignmentInBytes == CompressedOops::shift(), "decode alg wrong");
    if (LogMinObjAlignmentInBytes == Address::times_8) {
      leaq(dst, Address(r12_heapbase, src, Address::times_8, 0));
    } else {
      if (dst != src) {
        movq(dst, src);
      }
      shlq(dst, LogMinObjAlignmentInBytes);
      if (CompressedOops::base() != nullptr) {
        addq(dst, r12_heapbase);
      }
    }
  } else {
    assert (CompressedOops::base() == nullptr, "sanity");
    if (dst != src) {
      movq(dst, src);
    }
  }
}

void MacroAssembler::encode_klass_not_null(Register r, Register tmp) {
  BLOCK_COMMENT("encode_klass_not_null {");
  assert_different_registers(r, tmp);
  if (CompressedKlassPointers::base() != nullptr) {
    if (AOTCodeCache::is_on_for_dump()) {
      movptr(tmp, ExternalAddress(CompressedKlassPointers::base_addr()));
    } else {
      movptr(tmp, (intptr_t)CompressedKlassPointers::base());
    }
    subq(r, tmp);
  }
  if (CompressedKlassPointers::shift() != 0) {
    shrq(r, CompressedKlassPointers::shift());
  }
  BLOCK_COMMENT("} encode_klass_not_null");
}

void MacroAssembler::encode_and_move_klass_not_null(Register dst, Register src) {
  BLOCK_COMMENT("encode_and_move_klass_not_null {");
  assert_different_registers(src, dst);
  if (CompressedKlassPointers::base() != nullptr) {
    movptr(dst, -(intptr_t)CompressedKlassPointers::base());
    addq(dst, src);
  } else {
    movptr(dst, src);
  }
  if (CompressedKlassPointers::shift() != 0) {
    shrq(dst, CompressedKlassPointers::shift());
  }
  BLOCK_COMMENT("} encode_and_move_klass_not_null");
}

void  MacroAssembler::decode_klass_not_null(Register r, Register tmp) {
  BLOCK_COMMENT("decode_klass_not_null {");
  assert_different_registers(r, tmp);
  // Note: it will change flags
  assert(UseCompressedClassPointers, "should only be used for compressed headers");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  if (CompressedKlassPointers::shift() != 0) {
    shlq(r, CompressedKlassPointers::shift());
  }
  if (CompressedKlassPointers::base() != nullptr) {
    if (AOTCodeCache::is_on_for_dump()) {
      movptr(tmp, ExternalAddress(CompressedKlassPointers::base_addr()));
    } else {
      movptr(tmp, (intptr_t)CompressedKlassPointers::base());
    }
    addq(r, tmp);
  }
  BLOCK_COMMENT("} decode_klass_not_null");
}

void  MacroAssembler::decode_and_move_klass_not_null(Register dst, Register src) {
  BLOCK_COMMENT("decode_and_move_klass_not_null {");
  assert_different_registers(src, dst);
  // Note: it will change flags
  assert (UseCompressedClassPointers, "should only be used for compressed headers");
  // Cannot assert, unverified entry point counts instructions (see .ad file)
  // vtableStubs also counts instructions in pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.

  if (CompressedKlassPointers::base() == nullptr &&
      CompressedKlassPointers::shift() == 0) {
    // The best case scenario is that there is no base or shift. Then it is already
    // a pointer that needs nothing but a register rename.
    movl(dst, src);
  } else {
    if (CompressedKlassPointers::shift() <= Address::times_8) {
      if (CompressedKlassPointers::base() != nullptr) {
        movptr(dst, (intptr_t)CompressedKlassPointers::base());
      } else {
        xorq(dst, dst);
      }
      if (CompressedKlassPointers::shift() != 0) {
        assert(CompressedKlassPointers::shift() == Address::times_8, "klass not aligned on 64bits?");
        leaq(dst, Address(dst, src, Address::times_8, 0));
      } else {
        addq(dst, src);
      }
    } else {
      if (CompressedKlassPointers::base() != nullptr) {
        const intptr_t base_right_shifted =
            (intptr_t)CompressedKlassPointers::base() >> CompressedKlassPointers::shift();
        movptr(dst, base_right_shifted);
      } else {
        xorq(dst, dst);
      }
      addq(dst, src);
      shlq(dst, CompressedKlassPointers::shift());
    }
  }
  BLOCK_COMMENT("} decode_and_move_klass_not_null");
}

void  MacroAssembler::set_narrow_oop(Register dst, jobject obj) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != nullptr, "java heap should be initialized");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  mov_narrow_oop(dst, oop_index, rspec);
}

void  MacroAssembler::set_narrow_oop(Address dst, jobject obj) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != nullptr, "java heap should be initialized");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  mov_narrow_oop(dst, oop_index, rspec);
}

void  MacroAssembler::set_narrow_klass(Register dst, Klass* k) {
  assert (UseCompressedClassPointers, "should only be used for compressed headers");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int klass_index = oop_recorder()->find_index(k);
  RelocationHolder rspec = metadata_Relocation::spec(klass_index);
  mov_narrow_oop(dst, CompressedKlassPointers::encode(k), rspec);
}

void  MacroAssembler::set_narrow_klass(Address dst, Klass* k) {
  assert (UseCompressedClassPointers, "should only be used for compressed headers");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int klass_index = oop_recorder()->find_index(k);
  RelocationHolder rspec = metadata_Relocation::spec(klass_index);
  mov_narrow_oop(dst, CompressedKlassPointers::encode(k), rspec);
}

void  MacroAssembler::cmp_narrow_oop(Register dst, jobject obj) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != nullptr, "java heap should be initialized");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  Assembler::cmp_narrow_oop(dst, oop_index, rspec);
}

void  MacroAssembler::cmp_narrow_oop(Address dst, jobject obj) {
  assert (UseCompressedOops, "should only be used for compressed headers");
  assert (Universe::heap() != nullptr, "java heap should be initialized");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);
  Assembler::cmp_narrow_oop(dst, oop_index, rspec);
}

void  MacroAssembler::cmp_narrow_klass(Register dst, Klass* k) {
  assert (UseCompressedClassPointers, "should only be used for compressed headers");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int klass_index = oop_recorder()->find_index(k);
  RelocationHolder rspec = metadata_Relocation::spec(klass_index);
  Assembler::cmp_narrow_oop(dst, CompressedKlassPointers::encode(k), rspec);
}

void  MacroAssembler::cmp_narrow_klass(Address dst, Klass* k) {
  assert (UseCompressedClassPointers, "should only be used for compressed headers");
  assert (oop_recorder() != nullptr, "this assembler needs an OopRecorder");
  int klass_index = oop_recorder()->find_index(k);
  RelocationHolder rspec = metadata_Relocation::spec(klass_index);
  Assembler::cmp_narrow_oop(dst, CompressedKlassPointers::encode(k), rspec);
}

void MacroAssembler::reinit_heapbase() {
  if (UseCompressedOops) {
    if (Universe::heap() != nullptr) {
      if (CompressedOops::base() == nullptr) {
        MacroAssembler::xorptr(r12_heapbase, r12_heapbase);
      } else {
        mov64(r12_heapbase, (int64_t)CompressedOops::base());
      }
    } else {
      movptr(r12_heapbase, ExternalAddress(CompressedOops::base_addr()));
    }
  }
}

#if COMPILER2_OR_JVMCI

// clear memory of size 'cnt' qwords, starting at 'base' using XMM/YMM/ZMM registers
void MacroAssembler::xmm_clear_mem(Register base, Register cnt, Register rtmp, XMMRegister xtmp, KRegister mask) {
  // cnt - number of qwords (8-byte words).
  // base - start address, qword aligned.
  Label L_zero_64_bytes, L_loop, L_sloop, L_tail, L_end;
  bool use64byteVector = (MaxVectorSize == 64) && (VM_Version::avx3_threshold() == 0);
  if (use64byteVector) {
    vpxor(xtmp, xtmp, xtmp, AVX_512bit);
  } else if (MaxVectorSize >= 32) {
    vpxor(xtmp, xtmp, xtmp, AVX_256bit);
  } else {
    pxor(xtmp, xtmp);
  }
  jmp(L_zero_64_bytes);

  BIND(L_loop);
  if (MaxVectorSize >= 32) {
    fill64(base, 0, xtmp, use64byteVector);
  } else {
    movdqu(Address(base,  0), xtmp);
    movdqu(Address(base, 16), xtmp);
    movdqu(Address(base, 32), xtmp);
    movdqu(Address(base, 48), xtmp);
  }
  addptr(base, 64);

  BIND(L_zero_64_bytes);
  subptr(cnt, 8);
  jccb(Assembler::greaterEqual, L_loop);

  // Copy trailing 64 bytes
  if (use64byteVector) {
    addptr(cnt, 8);
    jccb(Assembler::equal, L_end);
    fill64_masked(3, base, 0, xtmp, mask, cnt, rtmp, true);
    jmp(L_end);
  } else {
    addptr(cnt, 4);
    jccb(Assembler::less, L_tail);
    if (MaxVectorSize >= 32) {
      vmovdqu(Address(base, 0), xtmp);
    } else {
      movdqu(Address(base,  0), xtmp);
      movdqu(Address(base, 16), xtmp);
    }
  }
  addptr(base, 32);
  subptr(cnt, 4);

  BIND(L_tail);
  addptr(cnt, 4);
  jccb(Assembler::lessEqual, L_end);
  if (UseAVX > 2 && MaxVectorSize >= 32 && VM_Version::supports_avx512vl()) {
    fill32_masked(3, base, 0, xtmp, mask, cnt, rtmp);
  } else {
    decrement(cnt);

    BIND(L_sloop);
    movq(Address(base, 0), xtmp);
    addptr(base, 8);
    decrement(cnt);
    jccb(Assembler::greaterEqual, L_sloop);
  }
  BIND(L_end);
}

// Clearing constant sized memory using YMM/ZMM registers.
void MacroAssembler::clear_mem(Register base, int cnt, Register rtmp, XMMRegister xtmp, KRegister mask) {
  assert(UseAVX > 2 && VM_Version::supports_avx512vl(), "");
  bool use64byteVector = (MaxVectorSize > 32) && (VM_Version::avx3_threshold() == 0);

  int vector64_count = (cnt & (~0x7)) >> 3;
  cnt = cnt & 0x7;
  const int fill64_per_loop = 4;
  const int max_unrolled_fill64 = 8;

  // 64 byte initialization loop.
  vpxor(xtmp, xtmp, xtmp, use64byteVector ? AVX_512bit : AVX_256bit);
  int start64 = 0;
  if (vector64_count > max_unrolled_fill64) {
    Label LOOP;
    Register index = rtmp;

    start64 = vector64_count - (vector64_count % fill64_per_loop);

    movl(index, 0);
    BIND(LOOP);
    for (int i = 0; i < fill64_per_loop; i++) {
      fill64(Address(base, index, Address::times_1, i * 64), xtmp, use64byteVector);
    }
    addl(index, fill64_per_loop * 64);
    cmpl(index, start64 * 64);
    jccb(Assembler::less, LOOP);
  }
  for (int i = start64; i < vector64_count; i++) {
    fill64(base, i * 64, xtmp, use64byteVector);
  }

  // Clear remaining 64 byte tail.
  int disp = vector64_count * 64;
  if (cnt) {
    switch (cnt) {
      case 1:
        movq(Address(base, disp), xtmp);
        break;
      case 2:
        evmovdqu(T_LONG, k0, Address(base, disp), xtmp, false, Assembler::AVX_128bit);
        break;
      case 3:
        movl(rtmp, 0x7);
        kmovwl(mask, rtmp);
        evmovdqu(T_LONG, mask, Address(base, disp), xtmp, true, Assembler::AVX_256bit);
        break;
      case 4:
        evmovdqu(T_LONG, k0, Address(base, disp), xtmp, false, Assembler::AVX_256bit);
        break;
      case 5:
        if (use64byteVector) {
          movl(rtmp, 0x1F);
          kmovwl(mask, rtmp);
          evmovdqu(T_LONG, mask, Address(base, disp), xtmp, true, Assembler::AVX_512bit);
        } else {
          evmovdqu(T_LONG, k0, Address(base, disp), xtmp, false, Assembler::AVX_256bit);
          movq(Address(base, disp + 32), xtmp);
        }
        break;
      case 6:
        if (use64byteVector) {
          movl(rtmp, 0x3F);
          kmovwl(mask, rtmp);
          evmovdqu(T_LONG, mask, Address(base, disp), xtmp, true, Assembler::AVX_512bit);
        } else {
          evmovdqu(T_LONG, k0, Address(base, disp), xtmp, false, Assembler::AVX_256bit);
          evmovdqu(T_LONG, k0, Address(base, disp + 32), xtmp, false, Assembler::AVX_128bit);
        }
        break;
      case 7:
        if (use64byteVector) {
          movl(rtmp, 0x7F);
          kmovwl(mask, rtmp);
          evmovdqu(T_LONG, mask, Address(base, disp), xtmp, true, Assembler::AVX_512bit);
        } else {
          evmovdqu(T_LONG, k0, Address(base, disp), xtmp, false, Assembler::AVX_256bit);
          movl(rtmp, 0x7);
          kmovwl(mask, rtmp);
          evmovdqu(T_LONG, mask, Address(base, disp + 32), xtmp, true, Assembler::AVX_256bit);
        }
        break;
      default:
        fatal("Unexpected length : %d\n",cnt);
        break;
    }
  }
}

void MacroAssembler::clear_mem(Register base, Register cnt, Register tmp, XMMRegister xtmp,
                               bool is_large, KRegister mask) {
  // cnt      - number of qwords (8-byte words).
  // base     - start address, qword aligned.
  // is_large - if optimizers know cnt is larger than InitArrayShortSize
  assert(base==rdi, "base register must be edi for rep stos");
  assert(tmp==rax,   "tmp register must be eax for rep stos");
  assert(cnt==rcx,   "cnt register must be ecx for rep stos");
  assert(InitArrayShortSize % BytesPerLong == 0,
    "InitArrayShortSize should be the multiple of BytesPerLong");

  Label DONE;
  if (!is_large || !UseXMMForObjInit) {
    xorptr(tmp, tmp);
  }

  if (!is_large) {
    Label LOOP, LONG;
    cmpptr(cnt, InitArrayShortSize/BytesPerLong);
    jccb(Assembler::greater, LONG);

    decrement(cnt);
    jccb(Assembler::negative, DONE); // Zero length

    // Use individual pointer-sized stores for small counts:
    BIND(LOOP);
    movptr(Address(base, cnt, Address::times_ptr), tmp);
    decrement(cnt);
    jccb(Assembler::greaterEqual, LOOP);
    jmpb(DONE);

    BIND(LONG);
  }

  // Use longer rep-prefixed ops for non-small counts:
  if (UseFastStosb) {
    shlptr(cnt, 3); // convert to number of bytes
    rep_stosb();
  } else if (UseXMMForObjInit) {
    xmm_clear_mem(base, cnt, tmp, xtmp, mask);
  } else {
    rep_stos();
  }

  BIND(DONE);
}

#endif //COMPILER2_OR_JVMCI


void MacroAssembler::generate_fill(BasicType t, bool aligned,
                                   Register to, Register value, Register count,
                                   Register rtmp, XMMRegister xtmp) {
  ShortBranchVerifier sbv(this);
  assert_different_registers(to, value, count, rtmp);
  Label L_exit;
  Label L_fill_2_bytes, L_fill_4_bytes;

#if defined(COMPILER2)
  if(MaxVectorSize >=32 &&
     VM_Version::supports_avx512vlbw() &&
     VM_Version::supports_bmi2()) {
    generate_fill_avx3(t, to, value, count, rtmp, xtmp);
    return;
  }
#endif

  int shift = -1;
  switch (t) {
    case T_BYTE:
      shift = 2;
      break;
    case T_SHORT:
      shift = 1;
      break;
    case T_INT:
      shift = 0;
      break;
    default: ShouldNotReachHere();
  }

  if (t == T_BYTE) {
    andl(value, 0xff);
    movl(rtmp, value);
    shll(rtmp, 8);
    orl(value, rtmp);
  }
  if (t == T_SHORT) {
    andl(value, 0xffff);
  }
  if (t == T_BYTE || t == T_SHORT) {
    movl(rtmp, value);
    shll(rtmp, 16);
    orl(value, rtmp);
  }

  cmpptr(count, 8 << shift); // Short arrays (< 32 bytes) fill by element
  jcc(Assembler::below, L_fill_4_bytes); // use unsigned cmp
  if (!UseUnalignedLoadStores && !aligned && (t == T_BYTE || t == T_SHORT)) {
    Label L_skip_align2;
    // align source address at 4 bytes address boundary
    if (t == T_BYTE) {
      Label L_skip_align1;
      // One byte misalignment happens only for byte arrays
      testptr(to, 1);
      jccb(Assembler::zero, L_skip_align1);
      movb(Address(to, 0), value);
      increment(to);
      decrement(count);
      BIND(L_skip_align1);
    }
    // Two bytes misalignment happens only for byte and short (char) arrays
    testptr(to, 2);
    jccb(Assembler::zero, L_skip_align2);
    movw(Address(to, 0), value);
    addptr(to, 2);
    subptr(count, 1<<(shift-1));
    BIND(L_skip_align2);
  }
  {
    Label L_fill_32_bytes;
    if (!UseUnalignedLoadStores) {
      // align to 8 bytes, we know we are 4 byte aligned to start
      testptr(to, 4);
      jccb(Assembler::zero, L_fill_32_bytes);
      movl(Address(to, 0), value);
      addptr(to, 4);
      subptr(count, 1<<shift);
    }
    BIND(L_fill_32_bytes);
    {
      Label L_fill_32_bytes_loop, L_check_fill_8_bytes, L_fill_8_bytes_loop, L_fill_8_bytes;
      movdl(xtmp, value);
      if (UseAVX >= 2 && UseUnalignedLoadStores) {
        Label L_check_fill_32_bytes;
        if (UseAVX > 2) {
          // Fill 64-byte chunks
          Label L_fill_64_bytes_loop_avx3, L_check_fill_64_bytes_avx2;

          // If number of bytes to fill < VM_Version::avx3_threshold(), perform fill using AVX2
          cmpptr(count, VM_Version::avx3_threshold());
          jccb(Assembler::below, L_check_fill_64_bytes_avx2);

          vpbroadcastd(xtmp, xtmp, Assembler::AVX_512bit);

          subptr(count, 16 << shift);
          jcc(Assembler::less, L_check_fill_32_bytes);
          align(16);

          BIND(L_fill_64_bytes_loop_avx3);
          evmovdqul(Address(to, 0), xtmp, Assembler::AVX_512bit);
          addptr(to, 64);
          subptr(count, 16 << shift);
          jcc(Assembler::greaterEqual, L_fill_64_bytes_loop_avx3);
          jmpb(L_check_fill_32_bytes);

          BIND(L_check_fill_64_bytes_avx2);
        }
        // Fill 64-byte chunks
        vpbroadcastd(xtmp, xtmp, Assembler::AVX_256bit);

        subptr(count, 16 << shift);
        jcc(Assembler::less, L_check_fill_32_bytes);

        // align data for 64-byte chunks
        Label L_fill_64_bytes_loop, L_align_64_bytes_loop;
        if (EnableX86ECoreOpts) {
            // align 'big' arrays to cache lines to minimize split_stores
            cmpptr(count, 96 << shift);
            jcc(Assembler::below, L_fill_64_bytes_loop);

            // Find the bytes needed for alignment
            movptr(rtmp, to);
            andptr(rtmp, 0x1c);
            jcc(Assembler::zero, L_fill_64_bytes_loop);
            negptr(rtmp);           // number of bytes to fill 32-rtmp. it filled by 2 mov by 32
            addptr(rtmp, 32);
            shrptr(rtmp, 2 - shift);// get number of elements from bytes
            subptr(count, rtmp);    // adjust count by number of elements

            align(16);
            BIND(L_align_64_bytes_loop);
            movdl(Address(to, 0), xtmp);
            addptr(to, 4);
            subptr(rtmp, 1 << shift);
            jcc(Assembler::greater, L_align_64_bytes_loop);
        }

        align(16);
        BIND(L_fill_64_bytes_loop);
        vmovdqu(Address(to, 0), xtmp);
        vmovdqu(Address(to, 32), xtmp);
        addptr(to, 64);
        subptr(count, 16 << shift);
        jcc(Assembler::greaterEqual, L_fill_64_bytes_loop);

        align(16);
        BIND(L_check_fill_32_bytes);
        addptr(count, 8 << shift);
        jccb(Assembler::less, L_check_fill_8_bytes);
        vmovdqu(Address(to, 0), xtmp);
        addptr(to, 32);
        subptr(count, 8 << shift);

        BIND(L_check_fill_8_bytes);
        // clean upper bits of YMM registers
        movdl(xtmp, value);
        pshufd(xtmp, xtmp, 0);
      } else {
        // Fill 32-byte chunks
        pshufd(xtmp, xtmp, 0);

        subptr(count, 8 << shift);
        jcc(Assembler::less, L_check_fill_8_bytes);
        align(16);

        BIND(L_fill_32_bytes_loop);

        if (UseUnalignedLoadStores) {
          movdqu(Address(to, 0), xtmp);
          movdqu(Address(to, 16), xtmp);
        } else {
          movq(Address(to, 0), xtmp);
          movq(Address(to, 8), xtmp);
          movq(Address(to, 16), xtmp);
          movq(Address(to, 24), xtmp);
        }

        addptr(to, 32);
        subptr(count, 8 << shift);
        jcc(Assembler::greaterEqual, L_fill_32_bytes_loop);

        BIND(L_check_fill_8_bytes);
      }
      addptr(count, 8 << shift);
      jccb(Assembler::zero, L_exit);
      jmpb(L_fill_8_bytes);

      //
      // length is too short, just fill qwords
      //
      align(16);
      BIND(L_fill_8_bytes_loop);
      movq(Address(to, 0), xtmp);
      addptr(to, 8);
      BIND(L_fill_8_bytes);
      subptr(count, 1 << (shift + 1));
      jcc(Assembler::greaterEqual, L_fill_8_bytes_loop);
    }
  }

  Label L_fill_4_bytes_loop;
  testl(count, 1 << shift);
  jccb(Assembler::zero, L_fill_2_bytes);

  align(16);
  BIND(L_fill_4_bytes_loop);
  movl(Address(to, 0), value);
  addptr(to, 4);

  BIND(L_fill_4_bytes);
  subptr(count, 1 << shift);
  jccb(Assembler::greaterEqual, L_fill_4_bytes_loop);

  if (t == T_BYTE || t == T_SHORT) {
    Label L_fill_byte;
    BIND(L_fill_2_bytes);
    // fill trailing 2 bytes
    testl(count, 1<<(shift-1));
    jccb(Assembler::zero, L_fill_byte);
    movw(Address(to, 0), value);
    if (t == T_BYTE) {
      addptr(to, 2);
      BIND(L_fill_byte);
      // fill trailing byte
      testl(count, 1);
      jccb(Assembler::zero, L_exit);
      movb(Address(to, 0), value);
    } else {
      BIND(L_fill_byte);
    }
  } else {
    BIND(L_fill_2_bytes);
  }
  BIND(L_exit);
}

void MacroAssembler::evpbroadcast(BasicType type, XMMRegister dst, Register src, int vector_len) {
  switch(type) {
    case T_BYTE:
    case T_BOOLEAN:
      evpbroadcastb(dst, src, vector_len);
      break;
    case T_SHORT:
    case T_CHAR:
      evpbroadcastw(dst, src, vector_len);
      break;
    case T_INT:
    case T_FLOAT:
      evpbroadcastd(dst, src, vector_len);
      break;
    case T_LONG:
    case T_DOUBLE:
      evpbroadcastq(dst, src, vector_len);
      break;
    default:
      fatal("Unhandled type : %s", type2name(type));
      break;
  }
}

// Encode given char[]/byte[] to byte[] in ISO_8859_1 or ASCII
//
// @IntrinsicCandidate
// int sun.nio.cs.ISO_8859_1.Encoder#encodeISOArray0(
//         char[] sa, int sp, byte[] da, int dp, int len) {
//     int i = 0;
//     for (; i < len; i++) {
//         char c = sa[sp++];
//         if (c > '\u00FF')
//             break;
//         da[dp++] = (byte) c;
//     }
//     return i;
// }
//
// @IntrinsicCandidate
// int java.lang.StringCoding.encodeISOArray0(
//         byte[] sa, int sp, byte[] da, int dp, int len) {
//   int i = 0;
//   for (; i < len; i++) {
//     char c = StringUTF16.getChar(sa, sp++);
//     if (c > '\u00FF')
//       break;
//     da[dp++] = (byte) c;
//   }
//   return i;
// }
//
// @IntrinsicCandidate
// int java.lang.StringCoding.encodeAsciiArray0(
//         char[] sa, int sp, byte[] da, int dp, int len) {
//   int i = 0;
//   for (; i < len; i++) {
//     char c = sa[sp++];
//     if (c >= '\u0080')
//       break;
//     da[dp++] = (byte) c;
//   }
//   return i;
// }
void MacroAssembler::encode_iso_array(Register src, Register dst, Register len,
  XMMRegister tmp1Reg, XMMRegister tmp2Reg,
  XMMRegister tmp3Reg, XMMRegister tmp4Reg,
  Register tmp5, Register result, bool ascii) {

  // rsi: src
  // rdi: dst
  // rdx: len
  // rcx: tmp5
  // rax: result
  ShortBranchVerifier sbv(this);
  assert_different_registers(src, dst, len, tmp5, result);
  Label L_done, L_copy_1_char, L_copy_1_char_exit;

  int mask = ascii ? 0xff80ff80 : 0xff00ff00;
  int short_mask = ascii ? 0xff80 : 0xff00;

  // set result
  xorl(result, result);
  // check for zero length
  testl(len, len);
  jcc(Assembler::zero, L_done);

  movl(result, len);

  // Setup pointers
  lea(src, Address(src, len, Address::times_2)); // char[]
  lea(dst, Address(dst, len, Address::times_1)); // byte[]
  negptr(len);

  if (UseSSE42Intrinsics || UseAVX >= 2) {
    Label L_copy_8_chars, L_copy_8_chars_exit;
    Label L_chars_16_check, L_copy_16_chars, L_copy_16_chars_exit;

    if (UseAVX >= 2) {
      Label L_chars_32_check, L_copy_32_chars, L_copy_32_chars_exit;
      movl(tmp5, mask);   // create mask to test for Unicode or non-ASCII chars in vector
      movdl(tmp1Reg, tmp5);
      vpbroadcastd(tmp1Reg, tmp1Reg, Assembler::AVX_256bit);
      jmp(L_chars_32_check);

      bind(L_copy_32_chars);
      vmovdqu(tmp3Reg, Address(src, len, Address::times_2, -64));
      vmovdqu(tmp4Reg, Address(src, len, Address::times_2, -32));
      vpor(tmp2Reg, tmp3Reg, tmp4Reg, /* vector_len */ 1);
      vptest(tmp2Reg, tmp1Reg);       // check for Unicode or non-ASCII chars in vector
      jccb(Assembler::notZero, L_copy_32_chars_exit);
      vpackuswb(tmp3Reg, tmp3Reg, tmp4Reg, /* vector_len */ 1);
      vpermq(tmp4Reg, tmp3Reg, 0xD8, /* vector_len */ 1);
      vmovdqu(Address(dst, len, Address::times_1, -32), tmp4Reg);

      bind(L_chars_32_check);
      addptr(len, 32);
      jcc(Assembler::lessEqual, L_copy_32_chars);

      bind(L_copy_32_chars_exit);
      subptr(len, 16);
      jccb(Assembler::greater, L_copy_16_chars_exit);

    } else if (UseSSE42Intrinsics) {
      movl(tmp5, mask);   // create mask to test for Unicode or non-ASCII chars in vector
      movdl(tmp1Reg, tmp5);
      pshufd(tmp1Reg, tmp1Reg, 0);
      jmpb(L_chars_16_check);
    }

    bind(L_copy_16_chars);
    if (UseAVX >= 2) {
      vmovdqu(tmp2Reg, Address(src, len, Address::times_2, -32));
      vptest(tmp2Reg, tmp1Reg);
      jcc(Assembler::notZero, L_copy_16_chars_exit);
      vpackuswb(tmp2Reg, tmp2Reg, tmp1Reg, /* vector_len */ 1);
      vpermq(tmp3Reg, tmp2Reg, 0xD8, /* vector_len */ 1);
    } else {
      if (UseAVX > 0) {
        movdqu(tmp3Reg, Address(src, len, Address::times_2, -32));
        movdqu(tmp4Reg, Address(src, len, Address::times_2, -16));
        vpor(tmp2Reg, tmp3Reg, tmp4Reg, /* vector_len */ 0);
      } else {
        movdqu(tmp3Reg, Address(src, len, Address::times_2, -32));
        por(tmp2Reg, tmp3Reg);
        movdqu(tmp4Reg, Address(src, len, Address::times_2, -16));
        por(tmp2Reg, tmp4Reg);
      }
      ptest(tmp2Reg, tmp1Reg);       // check for Unicode or non-ASCII chars in vector
      jccb(Assembler::notZero, L_copy_16_chars_exit);
      packuswb(tmp3Reg, tmp4Reg);
    }
    movdqu(Address(dst, len, Address::times_1, -16), tmp3Reg);

    bind(L_chars_16_check);
    addptr(len, 16);
    jcc(Assembler::lessEqual, L_copy_16_chars);

    bind(L_copy_16_chars_exit);
    if (UseAVX >= 2) {
      // clean upper bits of YMM registers
      vpxor(tmp2Reg, tmp2Reg);
      vpxor(tmp3Reg, tmp3Reg);
      vpxor(tmp4Reg, tmp4Reg);
      movdl(tmp1Reg, tmp5);
      pshufd(tmp1Reg, tmp1Reg, 0);
    }
    subptr(len, 8);
    jccb(Assembler::greater, L_copy_8_chars_exit);

    bind(L_copy_8_chars);
    movdqu(tmp3Reg, Address(src, len, Address::times_2, -16));
    ptest(tmp3Reg, tmp1Reg);
    jccb(Assembler::notZero, L_copy_8_chars_exit);
    packuswb(tmp3Reg, tmp1Reg);
    movq(Address(dst, len, Address::times_1, -8), tmp3Reg);
    addptr(len, 8);
    jccb(Assembler::lessEqual, L_copy_8_chars);

    bind(L_copy_8_chars_exit);
    subptr(len, 8);
    jccb(Assembler::zero, L_done);
  }

  bind(L_copy_1_char);
  load_unsigned_short(tmp5, Address(src, len, Address::times_2, 0));
  testl(tmp5, short_mask);      // check if Unicode or non-ASCII char
  jccb(Assembler::notZero, L_copy_1_char_exit);
  movb(Address(dst, len, Address::times_1, 0), tmp5);
  addptr(len, 1);
  jccb(Assembler::less, L_copy_1_char);

  bind(L_copy_1_char_exit);
  addptr(result, len); // len is negative count of not processed elements

  bind(L_done);
}

/**
 * Helper for multiply_to_len().
 */
void MacroAssembler::add2_with_carry(Register dest_hi, Register dest_lo, Register src1, Register src2) {
  addq(dest_lo, src1);
  adcq(dest_hi, 0);
  addq(dest_lo, src2);
  adcq(dest_hi, 0);
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
  //  for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx-, kdx--) {
  //    huge_128 product = y[idx] * x[xstart] + carry;
  //    z[kdx] = (jlong)product;
  //    carry  = (jlong)(product >>> 64);
  //  }
  //  z[xstart] = carry;
  //

  Label L_first_loop, L_first_loop_exit;
  Label L_one_x, L_one_y, L_multiply;

  decrementl(xstart);
  jcc(Assembler::negative, L_one_x);

  movq(x_xstart, Address(x, xstart, Address::times_4,  0));
  rorq(x_xstart, 32); // convert big-endian to little-endian

  bind(L_first_loop);
  decrementl(idx);
  jcc(Assembler::negative, L_first_loop_exit);
  decrementl(idx);
  jcc(Assembler::negative, L_one_y);
  movq(y_idx, Address(y, idx, Address::times_4,  0));
  rorq(y_idx, 32); // convert big-endian to little-endian
  bind(L_multiply);
  movq(product, x_xstart);
  mulq(y_idx); // product(rax) * y_idx -> rdx:rax
  addq(product, carry);
  adcq(rdx, 0);
  subl(kdx, 2);
  movl(Address(z, kdx, Address::times_4,  4), product);
  shrq(product, 32);
  movl(Address(z, kdx, Address::times_4,  0), product);
  movq(carry, rdx);
  jmp(L_first_loop);

  bind(L_one_y);
  movl(y_idx, Address(y,  0));
  jmp(L_multiply);

  bind(L_one_x);
  movl(x_xstart, Address(x,  0));
  jmp(L_first_loop);

  bind(L_first_loop_exit);
}

/**
 * Multiply 64 bit by 64 bit and add 128 bit.
 */
void MacroAssembler::multiply_add_128_x_128(Register x_xstart, Register y, Register z,
                                            Register yz_idx, Register idx,
                                            Register carry, Register product, int offset) {
  //     huge_128 product = (y[idx] * x_xstart) + z[kdx] + carry;
  //     z[kdx] = (jlong)product;

  movq(yz_idx, Address(y, idx, Address::times_4,  offset));
  rorq(yz_idx, 32); // convert big-endian to little-endian
  movq(product, x_xstart);
  mulq(yz_idx);     // product(rax) * yz_idx -> rdx:product(rax)
  movq(yz_idx, Address(z, idx, Address::times_4,  offset));
  rorq(yz_idx, 32); // convert big-endian to little-endian

  add2_with_carry(rdx, product, carry, yz_idx);

  movl(Address(z, idx, Address::times_4,  offset+4), product);
  shrq(product, 32);
  movl(Address(z, idx, Address::times_4,  offset), product);

}

/**
 * Multiply 128 bit by 128 bit. Unrolled inner loop.
 */
void MacroAssembler::multiply_128_x_128_loop(Register x_xstart, Register y, Register z,
                                             Register yz_idx, Register idx, Register jdx,
                                             Register carry, Register product,
                                             Register carry2) {
  //   jlong carry, x[], y[], z[];
  //   int kdx = ystart+1;
  //   for (int idx=ystart-2; idx >= 0; idx -= 2) { // Third loop
  //     huge_128 product = (y[idx+1] * x_xstart) + z[kdx+idx+1] + carry;
  //     z[kdx+idx+1] = (jlong)product;
  //     jlong carry2  = (jlong)(product >>> 64);
  //     product = (y[idx] * x_xstart) + z[kdx+idx] + carry2;
  //     z[kdx+idx] = (jlong)product;
  //     carry  = (jlong)(product >>> 64);
  //   }
  //   idx += 2;
  //   if (idx > 0) {
  //     product = (y[idx] * x_xstart) + z[kdx+idx] + carry;
  //     z[kdx+idx] = (jlong)product;
  //     carry  = (jlong)(product >>> 64);
  //   }
  //

  Label L_third_loop, L_third_loop_exit, L_post_third_loop_done;

  movl(jdx, idx);
  andl(jdx, 0xFFFFFFFC);
  shrl(jdx, 2);

  bind(L_third_loop);
  subl(jdx, 1);
  jcc(Assembler::negative, L_third_loop_exit);
  subl(idx, 4);

  multiply_add_128_x_128(x_xstart, y, z, yz_idx, idx, carry, product, 8);
  movq(carry2, rdx);

  multiply_add_128_x_128(x_xstart, y, z, yz_idx, idx, carry2, product, 0);
  movq(carry, rdx);
  jmp(L_third_loop);

  bind (L_third_loop_exit);

  andl (idx, 0x3);
  jcc(Assembler::zero, L_post_third_loop_done);

  Label L_check_1;
  subl(idx, 2);
  jcc(Assembler::negative, L_check_1);

  multiply_add_128_x_128(x_xstart, y, z, yz_idx, idx, carry, product, 0);
  movq(carry, rdx);

  bind (L_check_1);
  addl (idx, 0x2);
  andl (idx, 0x1);
  subl(idx, 1);
  jcc(Assembler::negative, L_post_third_loop_done);

  movl(yz_idx, Address(y, idx, Address::times_4,  0));
  movq(product, x_xstart);
  mulq(yz_idx); // product(rax) * yz_idx -> rdx:product(rax)
  movl(yz_idx, Address(z, idx, Address::times_4,  0));

  add2_with_carry(rdx, product, yz_idx, carry);

  movl(Address(z, idx, Address::times_4,  0), product);
  shrq(product, 32);

  shlq(rdx, 32);
  orq(product, rdx);
  movq(carry, product);

  bind(L_post_third_loop_done);
}

/**
 * Multiply 128 bit by 128 bit using BMI2. Unrolled inner loop.
 *
 */
void MacroAssembler::multiply_128_x_128_bmi2_loop(Register y, Register z,
                                                  Register carry, Register carry2,
                                                  Register idx, Register jdx,
                                                  Register yz_idx1, Register yz_idx2,
                                                  Register tmp, Register tmp3, Register tmp4) {
  assert(UseBMI2Instructions, "should be used only when BMI2 is available");

  //   jlong carry, x[], y[], z[];
  //   int kdx = ystart+1;
  //   for (int idx=ystart-2; idx >= 0; idx -= 2) { // Third loop
  //     huge_128 tmp3 = (y[idx+1] * rdx) + z[kdx+idx+1] + carry;
  //     jlong carry2  = (jlong)(tmp3 >>> 64);
  //     huge_128 tmp4 = (y[idx]   * rdx) + z[kdx+idx] + carry2;
  //     carry  = (jlong)(tmp4 >>> 64);
  //     z[kdx+idx+1] = (jlong)tmp3;
  //     z[kdx+idx] = (jlong)tmp4;
  //   }
  //   idx += 2;
  //   if (idx > 0) {
  //     yz_idx1 = (y[idx] * rdx) + z[kdx+idx] + carry;
  //     z[kdx+idx] = (jlong)yz_idx1;
  //     carry  = (jlong)(yz_idx1 >>> 64);
  //   }
  //

  Label L_third_loop, L_third_loop_exit, L_post_third_loop_done;

  movl(jdx, idx);
  andl(jdx, 0xFFFFFFFC);
  shrl(jdx, 2);

  bind(L_third_loop);
  subl(jdx, 1);
  jcc(Assembler::negative, L_third_loop_exit);
  subl(idx, 4);

  movq(yz_idx1,  Address(y, idx, Address::times_4,  8));
  rorxq(yz_idx1, yz_idx1, 32); // convert big-endian to little-endian
  movq(yz_idx2, Address(y, idx, Address::times_4,  0));
  rorxq(yz_idx2, yz_idx2, 32);

  mulxq(tmp4, tmp3, yz_idx1);  //  yz_idx1 * rdx -> tmp4:tmp3
  mulxq(carry2, tmp, yz_idx2); //  yz_idx2 * rdx -> carry2:tmp

  movq(yz_idx1,  Address(z, idx, Address::times_4,  8));
  rorxq(yz_idx1, yz_idx1, 32);
  movq(yz_idx2, Address(z, idx, Address::times_4,  0));
  rorxq(yz_idx2, yz_idx2, 32);

  if (VM_Version::supports_adx()) {
    adcxq(tmp3, carry);
    adoxq(tmp3, yz_idx1);

    adcxq(tmp4, tmp);
    adoxq(tmp4, yz_idx2);

    movl(carry, 0); // does not affect flags
    adcxq(carry2, carry);
    adoxq(carry2, carry);
  } else {
    add2_with_carry(tmp4, tmp3, carry, yz_idx1);
    add2_with_carry(carry2, tmp4, tmp, yz_idx2);
  }
  movq(carry, carry2);

  movl(Address(z, idx, Address::times_4, 12), tmp3);
  shrq(tmp3, 32);
  movl(Address(z, idx, Address::times_4,  8), tmp3);

  movl(Address(z, idx, Address::times_4,  4), tmp4);
  shrq(tmp4, 32);
  movl(Address(z, idx, Address::times_4,  0), tmp4);

  jmp(L_third_loop);

  bind (L_third_loop_exit);

  andl (idx, 0x3);
  jcc(Assembler::zero, L_post_third_loop_done);

  Label L_check_1;
  subl(idx, 2);
  jcc(Assembler::negative, L_check_1);

  movq(yz_idx1, Address(y, idx, Address::times_4,  0));
  rorxq(yz_idx1, yz_idx1, 32);
  mulxq(tmp4, tmp3, yz_idx1); //  yz_idx1 * rdx -> tmp4:tmp3
  movq(yz_idx2, Address(z, idx, Address::times_4,  0));
  rorxq(yz_idx2, yz_idx2, 32);

  add2_with_carry(tmp4, tmp3, carry, yz_idx2);

  movl(Address(z, idx, Address::times_4,  4), tmp3);
  shrq(tmp3, 32);
  movl(Address(z, idx, Address::times_4,  0), tmp3);
  movq(carry, tmp4);

  bind (L_check_1);
  addl (idx, 0x2);
  andl (idx, 0x1);
  subl(idx, 1);
  jcc(Assembler::negative, L_post_third_loop_done);
  movl(tmp4, Address(y, idx, Address::times_4,  0));
  mulxq(carry2, tmp3, tmp4);  //  tmp4 * rdx -> carry2:tmp3
  movl(tmp4, Address(z, idx, Address::times_4,  0));

  add2_with_carry(carry2, tmp3, tmp4, carry);

  movl(Address(z, idx, Address::times_4,  0), tmp3);
  shrq(tmp3, 32);

  shlq(carry2, 32);
  orq(tmp3, carry2);
  movq(carry, tmp3);

  bind(L_post_third_loop_done);
}

/**
 * Code for BigInteger::multiplyToLen() intrinsic.
 *
 * rdi: x
 * rax: xlen
 * rsi: y
 * rcx: ylen
 * r8:  z
 * r11: tmp0
 * r12: tmp1
 * r13: tmp2
 * r14: tmp3
 * r15: tmp4
 * rbx: tmp5
 *
 */
void MacroAssembler::multiply_to_len(Register x, Register xlen, Register y, Register ylen, Register z, Register tmp0,
                                     Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5) {
  ShortBranchVerifier sbv(this);
  assert_different_registers(x, xlen, y, ylen, z, tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, rdx);

  push(tmp0);
  push(tmp1);
  push(tmp2);
  push(tmp3);
  push(tmp4);
  push(tmp5);

  push(xlen);

  const Register idx = tmp1;
  const Register kdx = tmp2;
  const Register xstart = tmp3;

  const Register y_idx = tmp4;
  const Register carry = tmp5;
  const Register product  = xlen;
  const Register x_xstart = tmp0;

  // First Loop.
  //
  //  final static long LONG_MASK = 0xffffffffL;
  //  int xstart = xlen - 1;
  //  int ystart = ylen - 1;
  //  long carry = 0;
  //  for (int idx=ystart, kdx=ystart+1+xstart; idx >= 0; idx-, kdx--) {
  //    long product = (y[idx] & LONG_MASK) * (x[xstart] & LONG_MASK) + carry;
  //    z[kdx] = (int)product;
  //    carry = product >>> 32;
  //  }
  //  z[xstart] = (int)carry;
  //

  movl(idx, ylen);               // idx = ylen;
  lea(kdx, Address(xlen, ylen)); // kdx = xlen+ylen;
  xorq(carry, carry);            // carry = 0;

  Label L_done;

  movl(xstart, xlen);
  decrementl(xstart);
  jcc(Assembler::negative, L_done);

  multiply_64_x_64_loop(x, xstart, x_xstart, y, y_idx, z, carry, product, idx, kdx);

  Label L_second_loop;
  testl(kdx, kdx);
  jcc(Assembler::zero, L_second_loop);

  Label L_carry;
  subl(kdx, 1);
  jcc(Assembler::zero, L_carry);

  movl(Address(z, kdx, Address::times_4,  0), carry);
  shrq(carry, 32);
  subl(kdx, 1);

  bind(L_carry);
  movl(Address(z, kdx, Address::times_4,  0), carry);

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
  // i = xlen, j = tmp1, k = tmp2, carry = tmp5, x[i] = rdx

  const Register jdx = tmp1;

  bind(L_second_loop);
  xorl(carry, carry);    // carry = 0;
  movl(jdx, ylen);       // j = ystart+1

  subl(xstart, 1);       // i = xstart-1;
  jcc(Assembler::negative, L_done);

  push (z);

  Label L_last_x;
  lea(z, Address(z, xstart, Address::times_4, 4)); // z = z + k - j
  subl(xstart, 1);       // i = xstart-1;
  jcc(Assembler::negative, L_last_x);

  if (UseBMI2Instructions) {
    movq(rdx,  Address(x, xstart, Address::times_4,  0));
    rorxq(rdx, rdx, 32); // convert big-endian to little-endian
  } else {
    movq(x_xstart, Address(x, xstart, Address::times_4,  0));
    rorq(x_xstart, 32);  // convert big-endian to little-endian
  }

  Label L_third_loop_prologue;
  bind(L_third_loop_prologue);

  push (x);
  push (xstart);
  push (ylen);


  if (UseBMI2Instructions) {
    multiply_128_x_128_bmi2_loop(y, z, carry, x, jdx, ylen, product, tmp2, x_xstart, tmp3, tmp4);
  } else { // !UseBMI2Instructions
    multiply_128_x_128_loop(x_xstart, y, z, y_idx, jdx, ylen, carry, product, x);
  }

  pop(ylen);
  pop(xlen);
  pop(x);
  pop(z);

  movl(tmp3, xlen);
  addl(tmp3, 1);
  movl(Address(z, tmp3, Address::times_4,  0), carry);
  subl(tmp3, 1);
  jccb(Assembler::negative, L_done);

  shrq(carry, 32);
  movl(Address(z, tmp3, Address::times_4,  0), carry);
  jmp(L_second_loop);

  // Next infrequent code is moved outside loops.
  bind(L_last_x);
  if (UseBMI2Instructions) {
    movl(rdx, Address(x,  0));
  } else {
    movl(x_xstart, Address(x,  0));
  }
  jmp(L_third_loop_prologue);

  bind(L_done);

  pop(xlen);

  pop(tmp5);
  pop(tmp4);
  pop(tmp3);
  pop(tmp2);
  pop(tmp1);
  pop(tmp0);
}

void MacroAssembler::vectorized_mismatch(Register obja, Register objb, Register length, Register log2_array_indxscale,
  Register result, Register tmp1, Register tmp2, XMMRegister rymm0, XMMRegister rymm1, XMMRegister rymm2){
  assert(UseSSE42Intrinsics, "SSE4.2 must be enabled.");
  Label VECTOR16_LOOP, VECTOR8_LOOP, VECTOR4_LOOP;
  Label VECTOR8_TAIL, VECTOR4_TAIL;
  Label VECTOR32_NOT_EQUAL, VECTOR16_NOT_EQUAL, VECTOR8_NOT_EQUAL, VECTOR4_NOT_EQUAL;
  Label SAME_TILL_END, DONE;
  Label BYTES_LOOP, BYTES_TAIL, BYTES_NOT_EQUAL;

  //scale is in rcx in both Win64 and Unix
  ShortBranchVerifier sbv(this);

  shlq(length);
  xorq(result, result);

  if ((AVX3Threshold == 0) && (UseAVX > 2) &&
      VM_Version::supports_avx512vlbw()) {
    Label VECTOR64_LOOP, VECTOR64_NOT_EQUAL, VECTOR32_TAIL;

    cmpq(length, 64);
    jcc(Assembler::less, VECTOR32_TAIL);

    movq(tmp1, length);
    andq(tmp1, 0x3F);      // tail count
    andq(length, ~(0x3F)); //vector count

    bind(VECTOR64_LOOP);
    // AVX512 code to compare 64 byte vectors.
    evmovdqub(rymm0, Address(obja, result), Assembler::AVX_512bit);
    evpcmpeqb(k7, rymm0, Address(objb, result), Assembler::AVX_512bit);
    kortestql(k7, k7);
    jcc(Assembler::aboveEqual, VECTOR64_NOT_EQUAL);     // mismatch
    addq(result, 64);
    subq(length, 64);
    jccb(Assembler::notZero, VECTOR64_LOOP);

    //bind(VECTOR64_TAIL);
    testq(tmp1, tmp1);
    jcc(Assembler::zero, SAME_TILL_END);

    //bind(VECTOR64_TAIL);
    // AVX512 code to compare up to 63 byte vectors.
    mov64(tmp2, 0xFFFFFFFFFFFFFFFF);
    shlxq(tmp2, tmp2, tmp1);
    notq(tmp2);
    kmovql(k3, tmp2);

    evmovdqub(rymm0, k3, Address(obja, result), false, Assembler::AVX_512bit);
    evpcmpeqb(k7, k3, rymm0, Address(objb, result), Assembler::AVX_512bit);

    ktestql(k7, k3);
    jcc(Assembler::below, SAME_TILL_END);     // not mismatch

    bind(VECTOR64_NOT_EQUAL);
    kmovql(tmp1, k7);
    notq(tmp1);
    tzcntq(tmp1, tmp1);
    addq(result, tmp1);
    shrq(result);
    jmp(DONE);
    bind(VECTOR32_TAIL);
  }

  cmpq(length, 8);
  jcc(Assembler::equal, VECTOR8_LOOP);
  jcc(Assembler::less, VECTOR4_TAIL);

  if (UseAVX >= 2) {
    Label VECTOR16_TAIL, VECTOR32_LOOP;

    cmpq(length, 16);
    jcc(Assembler::equal, VECTOR16_LOOP);
    jcc(Assembler::less, VECTOR8_LOOP);

    cmpq(length, 32);
    jccb(Assembler::less, VECTOR16_TAIL);

    subq(length, 32);
    bind(VECTOR32_LOOP);
    vmovdqu(rymm0, Address(obja, result));
    vmovdqu(rymm1, Address(objb, result));
    vpxor(rymm2, rymm0, rymm1, Assembler::AVX_256bit);
    vptest(rymm2, rymm2);
    jcc(Assembler::notZero, VECTOR32_NOT_EQUAL);//mismatch found
    addq(result, 32);
    subq(length, 32);
    jcc(Assembler::greaterEqual, VECTOR32_LOOP);
    addq(length, 32);
    jcc(Assembler::equal, SAME_TILL_END);
    //falling through if less than 32 bytes left //close the branch here.

    bind(VECTOR16_TAIL);
    cmpq(length, 16);
    jccb(Assembler::less, VECTOR8_TAIL);
    bind(VECTOR16_LOOP);
    movdqu(rymm0, Address(obja, result));
    movdqu(rymm1, Address(objb, result));
    vpxor(rymm2, rymm0, rymm1, Assembler::AVX_128bit);
    ptest(rymm2, rymm2);
    jcc(Assembler::notZero, VECTOR16_NOT_EQUAL);//mismatch found
    addq(result, 16);
    subq(length, 16);
    jcc(Assembler::equal, SAME_TILL_END);
    //falling through if less than 16 bytes left
  } else {//regular intrinsics

    cmpq(length, 16);
    jccb(Assembler::less, VECTOR8_TAIL);

    subq(length, 16);
    bind(VECTOR16_LOOP);
    movdqu(rymm0, Address(obja, result));
    movdqu(rymm1, Address(objb, result));
    pxor(rymm0, rymm1);
    ptest(rymm0, rymm0);
    jcc(Assembler::notZero, VECTOR16_NOT_EQUAL);//mismatch found
    addq(result, 16);
    subq(length, 16);
    jccb(Assembler::greaterEqual, VECTOR16_LOOP);
    addq(length, 16);
    jcc(Assembler::equal, SAME_TILL_END);
    //falling through if less than 16 bytes left
  }

  bind(VECTOR8_TAIL);
  cmpq(length, 8);
  jccb(Assembler::less, VECTOR4_TAIL);
  bind(VECTOR8_LOOP);
  movq(tmp1, Address(obja, result));
  movq(tmp2, Address(objb, result));
  xorq(tmp1, tmp2);
  testq(tmp1, tmp1);
  jcc(Assembler::notZero, VECTOR8_NOT_EQUAL);//mismatch found
  addq(result, 8);
  subq(length, 8);
  jcc(Assembler::equal, SAME_TILL_END);
  //falling through if less than 8 bytes left

  bind(VECTOR4_TAIL);
  cmpq(length, 4);
  jccb(Assembler::less, BYTES_TAIL);
  bind(VECTOR4_LOOP);
  movl(tmp1, Address(obja, result));
  xorl(tmp1, Address(objb, result));
  testl(tmp1, tmp1);
  jcc(Assembler::notZero, VECTOR4_NOT_EQUAL);//mismatch found
  addq(result, 4);
  subq(length, 4);
  jcc(Assembler::equal, SAME_TILL_END);
  //falling through if less than 4 bytes left

  bind(BYTES_TAIL);
  bind(BYTES_LOOP);
  load_unsigned_byte(tmp1, Address(obja, result));
  load_unsigned_byte(tmp2, Address(objb, result));
  xorl(tmp1, tmp2);
  testl(tmp1, tmp1);
  jcc(Assembler::notZero, BYTES_NOT_EQUAL);//mismatch found
  decq(length);
  jcc(Assembler::zero, SAME_TILL_END);
  incq(result);
  load_unsigned_byte(tmp1, Address(obja, result));
  load_unsigned_byte(tmp2, Address(objb, result));
  xorl(tmp1, tmp2);
  testl(tmp1, tmp1);
  jcc(Assembler::notZero, BYTES_NOT_EQUAL);//mismatch found
  decq(length);
  jcc(Assembler::zero, SAME_TILL_END);
  incq(result);
  load_unsigned_byte(tmp1, Address(obja, result));
  load_unsigned_byte(tmp2, Address(objb, result));
  xorl(tmp1, tmp2);
  testl(tmp1, tmp1);
  jcc(Assembler::notZero, BYTES_NOT_EQUAL);//mismatch found
  jmp(SAME_TILL_END);

  if (UseAVX >= 2) {
    bind(VECTOR32_NOT_EQUAL);
    vpcmpeqb(rymm2, rymm2, rymm2, Assembler::AVX_256bit);
    vpcmpeqb(rymm0, rymm0, rymm1, Assembler::AVX_256bit);
    vpxor(rymm0, rymm0, rymm2, Assembler::AVX_256bit);
    vpmovmskb(tmp1, rymm0);
    bsfq(tmp1, tmp1);
    addq(result, tmp1);
    shrq(result);
    jmp(DONE);
  }

  bind(VECTOR16_NOT_EQUAL);
  if (UseAVX >= 2) {
    vpcmpeqb(rymm2, rymm2, rymm2, Assembler::AVX_128bit);
    vpcmpeqb(rymm0, rymm0, rymm1, Assembler::AVX_128bit);
    pxor(rymm0, rymm2);
  } else {
    pcmpeqb(rymm2, rymm2);
    pxor(rymm0, rymm1);
    pcmpeqb(rymm0, rymm1);
    pxor(rymm0, rymm2);
  }
  pmovmskb(tmp1, rymm0);
  bsfq(tmp1, tmp1);
  addq(result, tmp1);
  shrq(result);
  jmpb(DONE);

  bind(VECTOR8_NOT_EQUAL);
  bind(VECTOR4_NOT_EQUAL);
  bsfq(tmp1, tmp1);
  shrq(tmp1, 3);
  addq(result, tmp1);
  bind(BYTES_NOT_EQUAL);
  shrq(result);
  jmpb(DONE);

  bind(SAME_TILL_END);
  mov64(result, -1);

  bind(DONE);
}

//Helper functions for square_to_len()

/**
 * Store the squares of x[], right shifted one bit (divided by 2) into z[]
 * Preserves x and z and modifies rest of the registers.
 */
void MacroAssembler::square_rshift(Register x, Register xlen, Register z, Register tmp1, Register tmp3, Register tmp4, Register tmp5, Register rdxReg, Register raxReg) {
  // Perform square and right shift by 1
  // Handle odd xlen case first, then for even xlen do the following
  // jlong carry = 0;
  // for (int j=0, i=0; j < xlen; j+=2, i+=4) {
  //     huge_128 product = x[j:j+1] * x[j:j+1];
  //     z[i:i+1] = (carry << 63) | (jlong)(product >>> 65);
  //     z[i+2:i+3] = (jlong)(product >>> 1);
  //     carry = (jlong)product;
  // }

  xorq(tmp5, tmp5);     // carry
  xorq(rdxReg, rdxReg);
  xorl(tmp1, tmp1);     // index for x
  xorl(tmp4, tmp4);     // index for z

  Label L_first_loop, L_first_loop_exit;

  testl(xlen, 1);
  jccb(Assembler::zero, L_first_loop); //jump if xlen is even

  // Square and right shift by 1 the odd element using 32 bit multiply
  movl(raxReg, Address(x, tmp1, Address::times_4, 0));
  imulq(raxReg, raxReg);
  shrq(raxReg, 1);
  adcq(tmp5, 0);
  movq(Address(z, tmp4, Address::times_4, 0), raxReg);
  incrementl(tmp1);
  addl(tmp4, 2);

  // Square and  right shift by 1 the rest using 64 bit multiply
  bind(L_first_loop);
  cmpptr(tmp1, xlen);
  jccb(Assembler::equal, L_first_loop_exit);

  // Square
  movq(raxReg, Address(x, tmp1, Address::times_4,  0));
  rorq(raxReg, 32);    // convert big-endian to little-endian
  mulq(raxReg);        // 64-bit multiply rax * rax -> rdx:rax

  // Right shift by 1 and save carry
  shrq(tmp5, 1);       // rdx:rax:tmp5 = (tmp5:rdx:rax) >>> 1
  rcrq(rdxReg, 1);
  rcrq(raxReg, 1);
  adcq(tmp5, 0);

  // Store result in z
  movq(Address(z, tmp4, Address::times_4, 0), rdxReg);
  movq(Address(z, tmp4, Address::times_4, 8), raxReg);

  // Update indices for x and z
  addl(tmp1, 2);
  addl(tmp4, 4);
  jmp(L_first_loop);

  bind(L_first_loop_exit);
}


/**
 * Perform the following multiply add operation using BMI2 instructions
 * carry:sum = sum + op1*op2 + carry
 * op2 should be in rdx
 * op2 is preserved, all other registers are modified
 */
void MacroAssembler::multiply_add_64_bmi2(Register sum, Register op1, Register op2, Register carry, Register tmp2) {
  // assert op2 is rdx
  mulxq(tmp2, op1, op1);  //  op1 * op2 -> tmp2:op1
  addq(sum, carry);
  adcq(tmp2, 0);
  addq(sum, op1);
  adcq(tmp2, 0);
  movq(carry, tmp2);
}

/**
 * Perform the following multiply add operation:
 * carry:sum = sum + op1*op2 + carry
 * Preserves op1, op2 and modifies rest of registers
 */
void MacroAssembler::multiply_add_64(Register sum, Register op1, Register op2, Register carry, Register rdxReg, Register raxReg) {
  // rdx:rax = op1 * op2
  movq(raxReg, op2);
  mulq(op1);

  //  rdx:rax = sum + carry + rdx:rax
  addq(sum, carry);
  adcq(rdxReg, 0);
  addq(sum, raxReg);
  adcq(rdxReg, 0);

  // carry:sum = rdx:sum
  movq(carry, rdxReg);
}

/**
 * Add 64 bit long carry into z[] with carry propagation.
 * Preserves z and carry register values and modifies rest of registers.
 *
 */
void MacroAssembler::add_one_64(Register z, Register zlen, Register carry, Register tmp1) {
  Label L_fourth_loop, L_fourth_loop_exit;

  movl(tmp1, 1);
  subl(zlen, 2);
  addq(Address(z, zlen, Address::times_4, 0), carry);

  bind(L_fourth_loop);
  jccb(Assembler::carryClear, L_fourth_loop_exit);
  subl(zlen, 2);
  jccb(Assembler::negative, L_fourth_loop_exit);
  addq(Address(z, zlen, Address::times_4, 0), tmp1);
  jmp(L_fourth_loop);
  bind(L_fourth_loop_exit);
}

/**
 * Shift z[] left by 1 bit.
 * Preserves x, len, z and zlen registers and modifies rest of the registers.
 *
 */
void MacroAssembler::lshift_by_1(Register x, Register len, Register z, Register zlen, Register tmp1, Register tmp2, Register tmp3, Register tmp4) {

  Label L_fifth_loop, L_fifth_loop_exit;

  // Fifth loop
  // Perform primitiveLeftShift(z, zlen, 1)

  const Register prev_carry = tmp1;
  const Register new_carry = tmp4;
  const Register value = tmp2;
  const Register zidx = tmp3;

  // int zidx, carry;
  // long value;
  // carry = 0;
  // for (zidx = zlen-2; zidx >=0; zidx -= 2) {
  //    (carry:value)  = (z[i] << 1) | carry ;
  //    z[i] = value;
  // }

  movl(zidx, zlen);
  xorl(prev_carry, prev_carry); // clear carry flag and prev_carry register

  bind(L_fifth_loop);
  decl(zidx);  // Use decl to preserve carry flag
  decl(zidx);
  jccb(Assembler::negative, L_fifth_loop_exit);

  if (UseBMI2Instructions) {
     movq(value, Address(z, zidx, Address::times_4, 0));
     rclq(value, 1);
     rorxq(value, value, 32);
     movq(Address(z, zidx, Address::times_4,  0), value);  // Store back in big endian form
  }
  else {
    // clear new_carry
    xorl(new_carry, new_carry);

    // Shift z[i] by 1, or in previous carry and save new carry
    movq(value, Address(z, zidx, Address::times_4, 0));
    shlq(value, 1);
    adcl(new_carry, 0);

    orq(value, prev_carry);
    rorq(value, 0x20);
    movq(Address(z, zidx, Address::times_4,  0), value);  // Store back in big endian form

    // Set previous carry = new carry
    movl(prev_carry, new_carry);
  }
  jmp(L_fifth_loop);

  bind(L_fifth_loop_exit);
}


/**
 * Code for BigInteger::squareToLen() intrinsic
 *
 * rdi: x
 * rsi: len
 * r8:  z
 * rcx: zlen
 * r12: tmp1
 * r13: tmp2
 * r14: tmp3
 * r15: tmp4
 * rbx: tmp5
 *
 */
void MacroAssembler::square_to_len(Register x, Register len, Register z, Register zlen, Register tmp1, Register tmp2, Register tmp3, Register tmp4, Register tmp5, Register rdxReg, Register raxReg) {

  Label L_second_loop, L_second_loop_exit, L_third_loop, L_third_loop_exit, L_last_x, L_multiply;
  push(tmp1);
  push(tmp2);
  push(tmp3);
  push(tmp4);
  push(tmp5);

  // First loop
  // Store the squares, right shifted one bit (i.e., divided by 2).
  square_rshift(x, len, z, tmp1, tmp3, tmp4, tmp5, rdxReg, raxReg);

  // Add in off-diagonal sums.
  //
  // Second, third (nested) and fourth loops.
  // zlen +=2;
  // for (int xidx=len-2,zidx=zlen-4; xidx > 0; xidx-=2,zidx-=4) {
  //    carry = 0;
  //    long op2 = x[xidx:xidx+1];
  //    for (int j=xidx-2,k=zidx; j >= 0; j-=2) {
  //       k -= 2;
  //       long op1 = x[j:j+1];
  //       long sum = z[k:k+1];
  //       carry:sum = multiply_add_64(sum, op1, op2, carry, tmp_regs);
  //       z[k:k+1] = sum;
  //    }
  //    add_one_64(z, k, carry, tmp_regs);
  // }

  const Register carry = tmp5;
  const Register sum = tmp3;
  const Register op1 = tmp4;
  Register op2 = tmp2;

  push(zlen);
  push(len);
  addl(zlen,2);
  bind(L_second_loop);
  xorq(carry, carry);
  subl(zlen, 4);
  subl(len, 2);
  push(zlen);
  push(len);
  cmpl(len, 0);
  jccb(Assembler::lessEqual, L_second_loop_exit);

  // Multiply an array by one 64 bit long.
  if (UseBMI2Instructions) {
    op2 = rdxReg;
    movq(op2, Address(x, len, Address::times_4,  0));
    rorxq(op2, op2, 32);
  }
  else {
    movq(op2, Address(x, len, Address::times_4,  0));
    rorq(op2, 32);
  }

  bind(L_third_loop);
  decrementl(len);
  jccb(Assembler::negative, L_third_loop_exit);
  decrementl(len);
  jccb(Assembler::negative, L_last_x);

  movq(op1, Address(x, len, Address::times_4,  0));
  rorq(op1, 32);

  bind(L_multiply);
  subl(zlen, 2);
  movq(sum, Address(z, zlen, Address::times_4,  0));

  // Multiply 64 bit by 64 bit and add 64 bits lower half and upper 64 bits as carry.
  if (UseBMI2Instructions) {
    multiply_add_64_bmi2(sum, op1, op2, carry, tmp2);
  }
  else {
    multiply_add_64(sum, op1, op2, carry, rdxReg, raxReg);
  }

  movq(Address(z, zlen, Address::times_4, 0), sum);

  jmp(L_third_loop);
  bind(L_third_loop_exit);

  // Fourth loop
  // Add 64 bit long carry into z with carry propagation.
  // Uses offsetted zlen.
  add_one_64(z, zlen, carry, tmp1);

  pop(len);
  pop(zlen);
  jmp(L_second_loop);

  // Next infrequent code is moved outside loops.
  bind(L_last_x);
  movl(op1, Address(x, 0));
  jmp(L_multiply);

  bind(L_second_loop_exit);
  pop(len);
  pop(zlen);
  pop(len);
  pop(zlen);

  // Fifth loop
  // Shift z left 1 bit.
  lshift_by_1(x, len, z, zlen, tmp1, tmp2, tmp3, tmp4);

  // z[zlen-1] |= x[len-1] & 1;
  movl(tmp3, Address(x, len, Address::times_4, -4));
  andl(tmp3, 1);
  orl(Address(z, zlen, Address::times_4,  -4), tmp3);

  pop(tmp5);
  pop(tmp4);
  pop(tmp3);
  pop(tmp2);
  pop(tmp1);
}

/**
 * Helper function for mul_add()
 * Multiply the in[] by int k and add to out[] starting at offset offs using
 * 128 bit by 32 bit multiply and return the carry in tmp5.
 * Only quad int aligned length of in[] is operated on in this function.
 * k is in rdxReg for BMI2Instructions, for others it is in tmp2.
 * This function preserves out, in and k registers.
 * len and offset point to the appropriate index in "in" & "out" correspondingly
 * tmp5 has the carry.
 * other registers are temporary and are modified.
 *
 */
void MacroAssembler::mul_add_128_x_32_loop(Register out, Register in,
  Register offset, Register len, Register tmp1, Register tmp2, Register tmp3,
  Register tmp4, Register tmp5, Register rdxReg, Register raxReg) {

  Label L_first_loop, L_first_loop_exit;

  movl(tmp1, len);
  shrl(tmp1, 2);

  bind(L_first_loop);
  subl(tmp1, 1);
  jccb(Assembler::negative, L_first_loop_exit);

  subl(len, 4);
  subl(offset, 4);

  Register op2 = tmp2;
  const Register sum = tmp3;
  const Register op1 = tmp4;
  const Register carry = tmp5;

  if (UseBMI2Instructions) {
    op2 = rdxReg;
  }

  movq(op1, Address(in, len, Address::times_4,  8));
  rorq(op1, 32);
  movq(sum, Address(out, offset, Address::times_4,  8));
  rorq(sum, 32);
  if (UseBMI2Instructions) {
    multiply_add_64_bmi2(sum, op1, op2, carry, raxReg);
  }
  else {
    multiply_add_64(sum, op1, op2, carry, rdxReg, raxReg);
  }
  // Store back in big endian from little endian
  rorq(sum, 0x20);
  movq(Address(out, offset, Address::times_4,  8), sum);

  movq(op1, Address(in, len, Address::times_4,  0));
  rorq(op1, 32);
  movq(sum, Address(out, offset, Address::times_4,  0));
  rorq(sum, 32);
  if (UseBMI2Instructions) {
    multiply_add_64_bmi2(sum, op1, op2, carry, raxReg);
  }
  else {
    multiply_add_64(sum, op1, op2, carry, rdxReg, raxReg);
  }
  // Store back in big endian from little endian
  rorq(sum, 0x20);
  movq(Address(out, offset, Address::times_4,  0), sum);

  jmp(L_first_loop);
  bind(L_first_loop_exit);
}

/**
 * Code for BigInteger::mulAdd() intrinsic
 *
 * rdi: out
 * rsi: in
 * r11: offs (out.length - offset)
 * rcx: len
 * r8:  k
 * r12: tmp1
 * r13: tmp2
 * r14: tmp3
 * r15: tmp4
 * rbx: tmp5
 * Multiply the in[] by word k and add to out[], return the carry in rax
 */
void MacroAssembler::mul_add(Register out, Register in, Register offs,
   Register len, Register k, Register tmp1, Register tmp2, Register tmp3,
   Register tmp4, Register tmp5, Register rdxReg, Register raxReg) {

  Label L_carry, L_last_in, L_done;

// carry = 0;
// for (int j=len-1; j >= 0; j--) {
//    long product = (in[j] & LONG_MASK) * kLong +
//                   (out[offs] & LONG_MASK) + carry;
//    out[offs--] = (int)product;
//    carry = product >>> 32;
// }
//
  push(tmp1);
  push(tmp2);
  push(tmp3);
  push(tmp4);
  push(tmp5);

  Register op2 = tmp2;
  const Register sum = tmp3;
  const Register op1 = tmp4;
  const Register carry =  tmp5;

  if (UseBMI2Instructions) {
    op2 = rdxReg;
    movl(op2, k);
  }
  else {
    movl(op2, k);
  }

  xorq(carry, carry);

  //First loop

  //Multiply in[] by k in a 4 way unrolled loop using 128 bit by 32 bit multiply
  //The carry is in tmp5
  mul_add_128_x_32_loop(out, in, offs, len, tmp1, tmp2, tmp3, tmp4, tmp5, rdxReg, raxReg);

  //Multiply the trailing in[] entry using 64 bit by 32 bit, if any
  decrementl(len);
  jccb(Assembler::negative, L_carry);
  decrementl(len);
  jccb(Assembler::negative, L_last_in);

  movq(op1, Address(in, len, Address::times_4,  0));
  rorq(op1, 32);

  subl(offs, 2);
  movq(sum, Address(out, offs, Address::times_4,  0));
  rorq(sum, 32);

  if (UseBMI2Instructions) {
    multiply_add_64_bmi2(sum, op1, op2, carry, raxReg);
  }
  else {
    multiply_add_64(sum, op1, op2, carry, rdxReg, raxReg);
  }

  // Store back in big endian from little endian
  rorq(sum, 0x20);
  movq(Address(out, offs, Address::times_4,  0), sum);

  testl(len, len);
  jccb(Assembler::zero, L_carry);

  //Multiply the last in[] entry, if any
  bind(L_last_in);
  movl(op1, Address(in, 0));
  movl(sum, Address(out, offs, Address::times_4,  -4));

  movl(raxReg, k);
  mull(op1); //tmp4 * eax -> edx:eax
  addl(sum, carry);
  adcl(rdxReg, 0);
  addl(sum, raxReg);
  adcl(rdxReg, 0);
  movl(carry, rdxReg);

  movl(Address(out, offs, Address::times_4,  -4), sum);

  bind(L_carry);
  //return tmp5/carry as carry in rax
  movl(rax, carry);

  bind(L_done);
  pop(tmp5);
  pop(tmp4);
  pop(tmp3);
  pop(tmp2);
  pop(tmp1);
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
  xorl(val, crc);
  andl(val, 0xFF);
  shrl(crc, 8); // unsigned shift
  xorl(crc, Address(table, val, Address::times_4, 0));
}

/**
 * Fold 128-bit data chunk
 */
void MacroAssembler::fold_128bit_crc32(XMMRegister xcrc, XMMRegister xK, XMMRegister xtmp, Register buf, int offset) {
  if (UseAVX > 0) {
    vpclmulhdq(xtmp, xK, xcrc); // [123:64]
    vpclmulldq(xcrc, xK, xcrc); // [63:0]
    vpxor(xcrc, xcrc, Address(buf, offset), 0 /* vector_len */);
    pxor(xcrc, xtmp);
  } else {
    movdqa(xtmp, xcrc);
    pclmulhdq(xtmp, xK);   // [123:64]
    pclmulldq(xcrc, xK);   // [63:0]
    pxor(xcrc, xtmp);
    movdqu(xtmp, Address(buf, offset));
    pxor(xcrc, xtmp);
  }
}

void MacroAssembler::fold_128bit_crc32(XMMRegister xcrc, XMMRegister xK, XMMRegister xtmp, XMMRegister xbuf) {
  if (UseAVX > 0) {
    vpclmulhdq(xtmp, xK, xcrc);
    vpclmulldq(xcrc, xK, xcrc);
    pxor(xcrc, xbuf);
    pxor(xcrc, xtmp);
  } else {
    movdqa(xtmp, xcrc);
    pclmulhdq(xtmp, xK);
    pclmulldq(xcrc, xK);
    pxor(xcrc, xbuf);
    pxor(xcrc, xtmp);
  }
}

/**
 * 8-bit folds to compute 32-bit CRC
 *
 * uint64_t xcrc;
 * timesXtoThe32[xcrc & 0xFF] ^ (xcrc >> 8);
 */
void MacroAssembler::fold_8bit_crc32(XMMRegister xcrc, Register table, XMMRegister xtmp, Register tmp) {
  movdl(tmp, xcrc);
  andl(tmp, 0xFF);
  movdl(xtmp, Address(table, tmp, Address::times_4, 0));
  psrldq(xcrc, 1); // unsigned shift one byte
  pxor(xcrc, xtmp);
}

/**
 * uint32_t crc;
 * timesXtoThe32[crc & 0xFF] ^ (crc >> 8);
 */
void MacroAssembler::fold_8bit_crc32(Register crc, Register table, Register tmp) {
  movl(tmp, crc);
  andl(tmp, 0xFF);
  shrl(crc, 8);
  xorl(crc, Address(table, tmp, Address::times_4, 0));
}

/**
 * @param crc   register containing existing CRC (32-bit)
 * @param buf   register pointing to input byte buffer (byte*)
 * @param len   register containing number of bytes
 * @param table register that will contain address of CRC table
 * @param tmp   scratch register
 */
void MacroAssembler::kernel_crc32(Register crc, Register buf, Register len, Register table, Register tmp) {
  assert_different_registers(crc, buf, len, table, tmp, rax);

  Label L_tail, L_tail_restore, L_tail_loop, L_exit, L_align_loop, L_aligned;
  Label L_fold_tail, L_fold_128b, L_fold_512b, L_fold_512b_loop, L_fold_tail_loop;

  // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
  // context for the registers used, where all instructions below are using 128-bit mode
  // On EVEX without VL and BW, these instructions will all be AVX.
  lea(table, ExternalAddress(StubRoutines::crc_table_addr()));
  notl(crc); // ~crc
  cmpl(len, 16);
  jcc(Assembler::less, L_tail);

  // Align buffer to 16 bytes
  movl(tmp, buf);
  andl(tmp, 0xF);
  jccb(Assembler::zero, L_aligned);
  subl(tmp,  16);
  addl(len, tmp);

  align(4);
  BIND(L_align_loop);
  movsbl(rax, Address(buf, 0)); // load byte with sign extension
  update_byte_crc32(crc, rax, table);
  increment(buf);
  incrementl(tmp);
  jccb(Assembler::less, L_align_loop);

  BIND(L_aligned);
  movl(tmp, len); // save
  shrl(len, 4);
  jcc(Assembler::zero, L_tail_restore);

  // Fold crc into first bytes of vector
  movdqa(xmm1, Address(buf, 0));
  movdl(rax, xmm1);
  xorl(crc, rax);
  if (VM_Version::supports_sse4_1()) {
    pinsrd(xmm1, crc, 0);
  } else {
    pinsrw(xmm1, crc, 0);
    shrl(crc, 16);
    pinsrw(xmm1, crc, 1);
  }
  addptr(buf, 16);
  subl(len, 4); // len > 0
  jcc(Assembler::less, L_fold_tail);

  movdqa(xmm2, Address(buf,  0));
  movdqa(xmm3, Address(buf, 16));
  movdqa(xmm4, Address(buf, 32));
  addptr(buf, 48);
  subl(len, 3);
  jcc(Assembler::lessEqual, L_fold_512b);

  // Fold total 512 bits of polynomial on each iteration,
  // 128 bits per each of 4 parallel streams.
  movdqu(xmm0, ExternalAddress(StubRoutines::x86::crc_by128_masks_addr() + 32), rscratch1);

  align32();
  BIND(L_fold_512b_loop);
  fold_128bit_crc32(xmm1, xmm0, xmm5, buf,  0);
  fold_128bit_crc32(xmm2, xmm0, xmm5, buf, 16);
  fold_128bit_crc32(xmm3, xmm0, xmm5, buf, 32);
  fold_128bit_crc32(xmm4, xmm0, xmm5, buf, 48);
  addptr(buf, 64);
  subl(len, 4);
  jcc(Assembler::greater, L_fold_512b_loop);

  // Fold 512 bits to 128 bits.
  BIND(L_fold_512b);
  movdqu(xmm0, ExternalAddress(StubRoutines::x86::crc_by128_masks_addr() + 16), rscratch1);
  fold_128bit_crc32(xmm1, xmm0, xmm5, xmm2);
  fold_128bit_crc32(xmm1, xmm0, xmm5, xmm3);
  fold_128bit_crc32(xmm1, xmm0, xmm5, xmm4);

  // Fold the rest of 128 bits data chunks
  BIND(L_fold_tail);
  addl(len, 3);
  jccb(Assembler::lessEqual, L_fold_128b);
  movdqu(xmm0, ExternalAddress(StubRoutines::x86::crc_by128_masks_addr() + 16), rscratch1);

  BIND(L_fold_tail_loop);
  fold_128bit_crc32(xmm1, xmm0, xmm5, buf,  0);
  addptr(buf, 16);
  decrementl(len);
  jccb(Assembler::greater, L_fold_tail_loop);

  // Fold 128 bits in xmm1 down into 32 bits in crc register.
  BIND(L_fold_128b);
  movdqu(xmm0, ExternalAddress(StubRoutines::x86::crc_by128_masks_addr()), rscratch1);
  if (UseAVX > 0) {
    vpclmulqdq(xmm2, xmm0, xmm1, 0x1);
    vpand(xmm3, xmm0, xmm2, 0 /* vector_len */);
    vpclmulqdq(xmm0, xmm0, xmm3, 0x1);
  } else {
    movdqa(xmm2, xmm0);
    pclmulqdq(xmm2, xmm1, 0x1);
    movdqa(xmm3, xmm0);
    pand(xmm3, xmm2);
    pclmulqdq(xmm0, xmm3, 0x1);
  }
  psrldq(xmm1, 8);
  psrldq(xmm2, 4);
  pxor(xmm0, xmm1);
  pxor(xmm0, xmm2);

  // 8 8-bit folds to compute 32-bit CRC.
  for (int j = 0; j < 4; j++) {
    fold_8bit_crc32(xmm0, table, xmm1, rax);
  }
  movdl(crc, xmm0); // mov 32 bits to general register
  for (int j = 0; j < 4; j++) {
    fold_8bit_crc32(crc, table, rax);
  }

  BIND(L_tail_restore);
  movl(len, tmp); // restore
  BIND(L_tail);
  andl(len, 0xf);
  jccb(Assembler::zero, L_exit);

  // Fold the rest of bytes
  align(4);
  BIND(L_tail_loop);
  movsbl(rax, Address(buf, 0)); // load byte with sign extension
  update_byte_crc32(crc, rax, table);
  increment(buf);
  decrementl(len);
  jccb(Assembler::greater, L_tail_loop);

  BIND(L_exit);
  notl(crc); // ~c
}

// Helper function for AVX 512 CRC32
// Fold 512-bit data chunks
void MacroAssembler::fold512bit_crc32_avx512(XMMRegister xcrc, XMMRegister xK, XMMRegister xtmp, Register buf,
                                             Register pos, int offset) {
  evmovdquq(xmm3, Address(buf, pos, Address::times_1, offset), Assembler::AVX_512bit);
  evpclmulqdq(xtmp, xcrc, xK, 0x10, Assembler::AVX_512bit); // [123:64]
  evpclmulqdq(xmm2, xcrc, xK, 0x01, Assembler::AVX_512bit); // [63:0]
  evpxorq(xcrc, xtmp, xmm2, Assembler::AVX_512bit /* vector_len */);
  evpxorq(xcrc, xcrc, xmm3, Assembler::AVX_512bit /* vector_len */);
}

// Helper function for AVX 512 CRC32
// Compute CRC32 for < 256B buffers
void MacroAssembler::kernel_crc32_avx512_256B(Register crc, Register buf, Register len, Register table, Register pos,
                                              Register tmp1, Register tmp2, Label& L_barrett, Label& L_16B_reduction_loop,
                                              Label& L_get_last_two_xmms, Label& L_128_done, Label& L_cleanup) {

  Label L_less_than_32, L_exact_16_left, L_less_than_16_left;
  Label L_less_than_8_left, L_less_than_4_left, L_less_than_2_left, L_zero_left;
  Label L_only_less_than_4, L_only_less_than_3, L_only_less_than_2;

  // check if there is enough buffer to be able to fold 16B at a time
  cmpl(len, 32);
  jcc(Assembler::less, L_less_than_32);

  // if there is, load the constants
  movdqu(xmm10, Address(table, 1 * 16));    //rk1 and rk2 in xmm10
  movdl(xmm0, crc);                        // get the initial crc value
  movdqu(xmm7, Address(buf, pos, Address::times_1, 0 * 16)); //load the plaintext
  pxor(xmm7, xmm0);

  // update the buffer pointer
  addl(pos, 16);
  //update the counter.subtract 32 instead of 16 to save one instruction from the loop
  subl(len, 32);
  jmp(L_16B_reduction_loop);

  bind(L_less_than_32);
  //mov initial crc to the return value. this is necessary for zero - length buffers.
  movl(rax, crc);
  testl(len, len);
  jcc(Assembler::equal, L_cleanup);

  movdl(xmm0, crc);                        //get the initial crc value

  cmpl(len, 16);
  jcc(Assembler::equal, L_exact_16_left);
  jcc(Assembler::less, L_less_than_16_left);

  movdqu(xmm7, Address(buf, pos, Address::times_1, 0 * 16)); //load the plaintext
  pxor(xmm7, xmm0);                       //xor the initial crc value
  addl(pos, 16);
  subl(len, 16);
  movdqu(xmm10, Address(table, 1 * 16));    // rk1 and rk2 in xmm10
  jmp(L_get_last_two_xmms);

  bind(L_less_than_16_left);
  //use stack space to load data less than 16 bytes, zero - out the 16B in memory first.
  pxor(xmm1, xmm1);
  movptr(tmp1, rsp);
  movdqu(Address(tmp1, 0 * 16), xmm1);

  cmpl(len, 4);
  jcc(Assembler::less, L_only_less_than_4);

  //backup the counter value
  movl(tmp2, len);
  cmpl(len, 8);
  jcc(Assembler::less, L_less_than_8_left);

  //load 8 Bytes
  movq(rax, Address(buf, pos, Address::times_1, 0 * 16));
  movq(Address(tmp1, 0 * 16), rax);
  addptr(tmp1, 8);
  subl(len, 8);
  addl(pos, 8);

  bind(L_less_than_8_left);
  cmpl(len, 4);
  jcc(Assembler::less, L_less_than_4_left);

  //load 4 Bytes
  movl(rax, Address(buf, pos, Address::times_1, 0));
  movl(Address(tmp1, 0 * 16), rax);
  addptr(tmp1, 4);
  subl(len, 4);
  addl(pos, 4);

  bind(L_less_than_4_left);
  cmpl(len, 2);
  jcc(Assembler::less, L_less_than_2_left);

  // load 2 Bytes
  movw(rax, Address(buf, pos, Address::times_1, 0));
  movl(Address(tmp1, 0 * 16), rax);
  addptr(tmp1, 2);
  subl(len, 2);
  addl(pos, 2);

  bind(L_less_than_2_left);
  cmpl(len, 1);
  jcc(Assembler::less, L_zero_left);

  // load 1 Byte
  movb(rax, Address(buf, pos, Address::times_1, 0));
  movb(Address(tmp1, 0 * 16), rax);

  bind(L_zero_left);
  movdqu(xmm7, Address(rsp, 0));
  pxor(xmm7, xmm0);                       //xor the initial crc value

  lea(rax, ExternalAddress(StubRoutines::x86::shuf_table_crc32_avx512_addr()));
  movdqu(xmm0, Address(rax, tmp2));
  pshufb(xmm7, xmm0);
  jmp(L_128_done);

  bind(L_exact_16_left);
  movdqu(xmm7, Address(buf, pos, Address::times_1, 0));
  pxor(xmm7, xmm0);                       //xor the initial crc value
  jmp(L_128_done);

  bind(L_only_less_than_4);
  cmpl(len, 3);
  jcc(Assembler::less, L_only_less_than_3);

  // load 3 Bytes
  movb(rax, Address(buf, pos, Address::times_1, 0));
  movb(Address(tmp1, 0), rax);

  movb(rax, Address(buf, pos, Address::times_1, 1));
  movb(Address(tmp1, 1), rax);

  movb(rax, Address(buf, pos, Address::times_1, 2));
  movb(Address(tmp1, 2), rax);

  movdqu(xmm7, Address(rsp, 0));
  pxor(xmm7, xmm0);                     //xor the initial crc value

  pslldq(xmm7, 0x5);
  jmp(L_barrett);
  bind(L_only_less_than_3);
  cmpl(len, 2);
  jcc(Assembler::less, L_only_less_than_2);

  // load 2 Bytes
  movb(rax, Address(buf, pos, Address::times_1, 0));
  movb(Address(tmp1, 0), rax);

  movb(rax, Address(buf, pos, Address::times_1, 1));
  movb(Address(tmp1, 1), rax);

  movdqu(xmm7, Address(rsp, 0));
  pxor(xmm7, xmm0);                     //xor the initial crc value

  pslldq(xmm7, 0x6);
  jmp(L_barrett);

  bind(L_only_less_than_2);
  //load 1 Byte
  movb(rax, Address(buf, pos, Address::times_1, 0));
  movb(Address(tmp1, 0), rax);

  movdqu(xmm7, Address(rsp, 0));
  pxor(xmm7, xmm0);                     //xor the initial crc value

  pslldq(xmm7, 0x7);
}

/**
* Compute CRC32 using AVX512 instructions
* param crc   register containing existing CRC (32-bit)
* param buf   register pointing to input byte buffer (byte*)
* param len   register containing number of bytes
* param table address of crc or crc32c table
* param tmp1  scratch register
* param tmp2  scratch register
* return rax  result register
*
* This routine is identical for crc32c with the exception of the precomputed constant
* table which will be passed as the table argument.  The calculation steps are
* the same for both variants.
*/
void MacroAssembler::kernel_crc32_avx512(Register crc, Register buf, Register len, Register table, Register tmp1, Register tmp2) {
  assert_different_registers(crc, buf, len, table, tmp1, tmp2, rax, r12);

  Label L_tail, L_tail_restore, L_tail_loop, L_exit, L_align_loop, L_aligned;
  Label L_fold_tail, L_fold_128b, L_fold_512b, L_fold_512b_loop, L_fold_tail_loop;
  Label L_less_than_256, L_fold_128_B_loop, L_fold_256_B_loop;
  Label L_fold_128_B_register, L_final_reduction_for_128, L_16B_reduction_loop;
  Label L_128_done, L_get_last_two_xmms, L_barrett, L_cleanup;

  const Register pos = r12;
  push(r12);
  subptr(rsp, 16 * 2 + 8);

  // For EVEX with VL and BW, provide a standard mask, VL = 128 will guide the merge
  // context for the registers used, where all instructions below are using 128-bit mode
  // On EVEX without VL and BW, these instructions will all be AVX.
  movl(pos, 0);

  // check if smaller than 256B
  cmpl(len, 256);
  jcc(Assembler::less, L_less_than_256);

  // load the initial crc value
  movdl(xmm10, crc);

  // receive the initial 64B data, xor the initial crc value
  evmovdquq(xmm0, Address(buf, pos, Address::times_1, 0 * 64), Assembler::AVX_512bit);
  evmovdquq(xmm4, Address(buf, pos, Address::times_1, 1 * 64), Assembler::AVX_512bit);
  evpxorq(xmm0, xmm0, xmm10, Assembler::AVX_512bit);
  evbroadcasti32x4(xmm10, Address(table, 2 * 16), Assembler::AVX_512bit); //zmm10 has rk3 and rk4

  subl(len, 256);
  cmpl(len, 256);
  jcc(Assembler::less, L_fold_128_B_loop);

  evmovdquq(xmm7, Address(buf, pos, Address::times_1, 2 * 64), Assembler::AVX_512bit);
  evmovdquq(xmm8, Address(buf, pos, Address::times_1, 3 * 64), Assembler::AVX_512bit);
  evbroadcasti32x4(xmm16, Address(table, 0 * 16), Assembler::AVX_512bit); //zmm16 has rk-1 and rk-2
  subl(len, 256);

  bind(L_fold_256_B_loop);
  addl(pos, 256);
  fold512bit_crc32_avx512(xmm0, xmm16, xmm1, buf, pos, 0 * 64);
  fold512bit_crc32_avx512(xmm4, xmm16, xmm1, buf, pos, 1 * 64);
  fold512bit_crc32_avx512(xmm7, xmm16, xmm1, buf, pos, 2 * 64);
  fold512bit_crc32_avx512(xmm8, xmm16, xmm1, buf, pos, 3 * 64);

  subl(len, 256);
  jcc(Assembler::greaterEqual, L_fold_256_B_loop);

  // Fold 256 into 128
  addl(pos, 256);
  evpclmulqdq(xmm1, xmm0, xmm10, 0x01, Assembler::AVX_512bit);
  evpclmulqdq(xmm2, xmm0, xmm10, 0x10, Assembler::AVX_512bit);
  vpternlogq(xmm7, 0x96, xmm1, xmm2, Assembler::AVX_512bit); // xor ABC

  evpclmulqdq(xmm5, xmm4, xmm10, 0x01, Assembler::AVX_512bit);
  evpclmulqdq(xmm6, xmm4, xmm10, 0x10, Assembler::AVX_512bit);
  vpternlogq(xmm8, 0x96, xmm5, xmm6, Assembler::AVX_512bit); // xor ABC

  evmovdquq(xmm0, xmm7, Assembler::AVX_512bit);
  evmovdquq(xmm4, xmm8, Assembler::AVX_512bit);

  addl(len, 128);
  jmp(L_fold_128_B_register);

  // at this section of the code, there is 128 * x + y(0 <= y<128) bytes of buffer.The fold_128_B_loop
  // loop will fold 128B at a time until we have 128 + y Bytes of buffer

  // fold 128B at a time.This section of the code folds 8 xmm registers in parallel
  bind(L_fold_128_B_loop);
  addl(pos, 128);
  fold512bit_crc32_avx512(xmm0, xmm10, xmm1, buf, pos, 0 * 64);
  fold512bit_crc32_avx512(xmm4, xmm10, xmm1, buf, pos, 1 * 64);

  subl(len, 128);
  jcc(Assembler::greaterEqual, L_fold_128_B_loop);

  addl(pos, 128);

  // at this point, the buffer pointer is pointing at the last y Bytes of the buffer, where 0 <= y < 128
  // the 128B of folded data is in 8 of the xmm registers : xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7
  bind(L_fold_128_B_register);
  evmovdquq(xmm16, Address(table, 5 * 16), Assembler::AVX_512bit); // multiply by rk9-rk16
  evmovdquq(xmm11, Address(table, 9 * 16), Assembler::AVX_512bit); // multiply by rk17-rk20, rk1,rk2, 0,0
  evpclmulqdq(xmm1, xmm0, xmm16, 0x01, Assembler::AVX_512bit);
  evpclmulqdq(xmm2, xmm0, xmm16, 0x10, Assembler::AVX_512bit);
  // save last that has no multiplicand
  vextracti64x2(xmm7, xmm4, 3);

  evpclmulqdq(xmm5, xmm4, xmm11, 0x01, Assembler::AVX_512bit);
  evpclmulqdq(xmm6, xmm4, xmm11, 0x10, Assembler::AVX_512bit);
  // Needed later in reduction loop
  movdqu(xmm10, Address(table, 1 * 16));
  vpternlogq(xmm1, 0x96, xmm2, xmm5, Assembler::AVX_512bit); // xor ABC
  vpternlogq(xmm1, 0x96, xmm6, xmm7, Assembler::AVX_512bit); // xor ABC

  // Swap 1,0,3,2 - 01 00 11 10
  evshufi64x2(xmm8, xmm1, xmm1, 0x4e, Assembler::AVX_512bit);
  evpxorq(xmm8, xmm8, xmm1, Assembler::AVX_256bit);
  vextracti128(xmm5, xmm8, 1);
  evpxorq(xmm7, xmm5, xmm8, Assembler::AVX_128bit);

  // instead of 128, we add 128 - 16 to the loop counter to save 1 instruction from the loop
  // instead of a cmp instruction, we use the negative flag with the jl instruction
  addl(len, 128 - 16);
  jcc(Assembler::less, L_final_reduction_for_128);

  bind(L_16B_reduction_loop);
  vpclmulqdq(xmm8, xmm7, xmm10, 0x01);
  vpclmulqdq(xmm7, xmm7, xmm10, 0x10);
  vpxor(xmm7, xmm7, xmm8, Assembler::AVX_128bit);
  movdqu(xmm0, Address(buf, pos, Address::times_1, 0 * 16));
  vpxor(xmm7, xmm7, xmm0, Assembler::AVX_128bit);
  addl(pos, 16);
  subl(len, 16);
  jcc(Assembler::greaterEqual, L_16B_reduction_loop);

  bind(L_final_reduction_for_128);
  addl(len, 16);
  jcc(Assembler::equal, L_128_done);

  bind(L_get_last_two_xmms);
  movdqu(xmm2, xmm7);
  addl(pos, len);
  movdqu(xmm1, Address(buf, pos, Address::times_1, -16));
  subl(pos, len);

  // get rid of the extra data that was loaded before
  // load the shift constant
  lea(rax, ExternalAddress(StubRoutines::x86::shuf_table_crc32_avx512_addr()));
  movdqu(xmm0, Address(rax, len));
  addl(rax, len);

  vpshufb(xmm7, xmm7, xmm0, Assembler::AVX_128bit);
  //Change mask to 512
  vpxor(xmm0, xmm0, ExternalAddress(StubRoutines::x86::crc_by128_masks_avx512_addr() + 2 * 16), Assembler::AVX_128bit, tmp2);
  vpshufb(xmm2, xmm2, xmm0, Assembler::AVX_128bit);

  blendvpb(xmm2, xmm2, xmm1, xmm0, Assembler::AVX_128bit);
  vpclmulqdq(xmm8, xmm7, xmm10, 0x01);
  vpclmulqdq(xmm7, xmm7, xmm10, 0x10);
  vpxor(xmm7, xmm7, xmm8, Assembler::AVX_128bit);
  vpxor(xmm7, xmm7, xmm2, Assembler::AVX_128bit);

  bind(L_128_done);
  // compute crc of a 128-bit value
  movdqu(xmm10, Address(table, 3 * 16));
  movdqu(xmm0, xmm7);

  // 64b fold
  vpclmulqdq(xmm7, xmm7, xmm10, 0x0);
  vpsrldq(xmm0, xmm0, 0x8, Assembler::AVX_128bit);
  vpxor(xmm7, xmm7, xmm0, Assembler::AVX_128bit);

  // 32b fold
  movdqu(xmm0, xmm7);
  vpslldq(xmm7, xmm7, 0x4, Assembler::AVX_128bit);
  vpclmulqdq(xmm7, xmm7, xmm10, 0x10);
  vpxor(xmm7, xmm7, xmm0, Assembler::AVX_128bit);
  jmp(L_barrett);

  bind(L_less_than_256);
  kernel_crc32_avx512_256B(crc, buf, len, table, pos, tmp1, tmp2, L_barrett, L_16B_reduction_loop, L_get_last_two_xmms, L_128_done, L_cleanup);

  //barrett reduction
  bind(L_barrett);
  vpand(xmm7, xmm7, ExternalAddress(StubRoutines::x86::crc_by128_masks_avx512_addr() + 1 * 16), Assembler::AVX_128bit, tmp2);
  movdqu(xmm1, xmm7);
  movdqu(xmm2, xmm7);
  movdqu(xmm10, Address(table, 4 * 16));

  pclmulqdq(xmm7, xmm10, 0x0);
  pxor(xmm7, xmm2);
  vpand(xmm7, xmm7, ExternalAddress(StubRoutines::x86::crc_by128_masks_avx512_addr()), Assembler::AVX_128bit, tmp2);
  movdqu(xmm2, xmm7);
  pclmulqdq(xmm7, xmm10, 0x10);
  pxor(xmm7, xmm2);
  pxor(xmm7, xmm1);
  pextrd(crc, xmm7, 2);

  bind(L_cleanup);
  addptr(rsp, 16 * 2 + 8);
  pop(r12);
}

// S. Gueron / Information Processing Letters 112 (2012) 184
// Algorithm 4: Computing carry-less multiplication using a precomputed lookup table.
// Input: A 32 bit value B = [byte3, byte2, byte1, byte0].
// Output: the 64-bit carry-less product of B * CONST
void MacroAssembler::crc32c_ipl_alg4(Register in, uint32_t n,
                                     Register tmp1, Register tmp2, Register tmp3) {
  lea(tmp3, ExternalAddress(StubRoutines::crc32c_table_addr()));
  if (n > 0) {
    addq(tmp3, n * 256 * 8);
  }
  //    Q1 = TABLEExt[n][B & 0xFF];
  movl(tmp1, in);
  andl(tmp1, 0x000000FF);
  shll(tmp1, 3);
  addq(tmp1, tmp3);
  movq(tmp1, Address(tmp1, 0));

  //    Q2 = TABLEExt[n][B >> 8 & 0xFF];
  movl(tmp2, in);
  shrl(tmp2, 8);
  andl(tmp2, 0x000000FF);
  shll(tmp2, 3);
  addq(tmp2, tmp3);
  movq(tmp2, Address(tmp2, 0));

  shlq(tmp2, 8);
  xorq(tmp1, tmp2);

  //    Q3 = TABLEExt[n][B >> 16 & 0xFF];
  movl(tmp2, in);
  shrl(tmp2, 16);
  andl(tmp2, 0x000000FF);
  shll(tmp2, 3);
  addq(tmp2, tmp3);
  movq(tmp2, Address(tmp2, 0));

  shlq(tmp2, 16);
  xorq(tmp1, tmp2);

  //    Q4 = TABLEExt[n][B >> 24 & 0xFF];
  shrl(in, 24);
  andl(in, 0x000000FF);
  shll(in, 3);
  addq(in, tmp3);
  movq(in, Address(in, 0));

  shlq(in, 24);
  xorq(in, tmp1);
  //    return Q1 ^ Q2 << 8 ^ Q3 << 16 ^ Q4 << 24;
}

void MacroAssembler::crc32c_pclmulqdq(XMMRegister w_xtmp1,
                                      Register in_out,
                                      uint32_t const_or_pre_comp_const_index, bool is_pclmulqdq_supported,
                                      XMMRegister w_xtmp2,
                                      Register tmp1,
                                      Register n_tmp2, Register n_tmp3) {
  if (is_pclmulqdq_supported) {
    movdl(w_xtmp1, in_out); // modified blindly

    movl(tmp1, const_or_pre_comp_const_index);
    movdl(w_xtmp2, tmp1);
    pclmulqdq(w_xtmp1, w_xtmp2, 0);

    movdq(in_out, w_xtmp1);
  } else {
    crc32c_ipl_alg4(in_out, const_or_pre_comp_const_index, tmp1, n_tmp2, n_tmp3);
  }
}

// Recombination Alternative 2: No bit-reflections
// T1 = (CRC_A * U1) << 1
// T2 = (CRC_B * U2) << 1
// C1 = T1 >> 32
// C2 = T2 >> 32
// T1 = T1 & 0xFFFFFFFF
// T2 = T2 & 0xFFFFFFFF
// T1 = CRC32(0, T1)
// T2 = CRC32(0, T2)
// C1 = C1 ^ T1
// C2 = C2 ^ T2
// CRC = C1 ^ C2 ^ CRC_C
void MacroAssembler::crc32c_rec_alt2(uint32_t const_or_pre_comp_const_index_u1, uint32_t const_or_pre_comp_const_index_u2, bool is_pclmulqdq_supported, Register in_out, Register in1, Register in2,
                                     XMMRegister w_xtmp1, XMMRegister w_xtmp2, XMMRegister w_xtmp3,
                                     Register tmp1, Register tmp2,
                                     Register n_tmp3) {
  crc32c_pclmulqdq(w_xtmp1, in_out, const_or_pre_comp_const_index_u1, is_pclmulqdq_supported, w_xtmp3, tmp1, tmp2, n_tmp3);
  crc32c_pclmulqdq(w_xtmp2, in1, const_or_pre_comp_const_index_u2, is_pclmulqdq_supported, w_xtmp3, tmp1, tmp2, n_tmp3);
  shlq(in_out, 1);
  movl(tmp1, in_out);
  shrq(in_out, 32);
  xorl(tmp2, tmp2);
  crc32(tmp2, tmp1, 4);
  xorl(in_out, tmp2); // we don't care about upper 32 bit contents here
  shlq(in1, 1);
  movl(tmp1, in1);
  shrq(in1, 32);
  xorl(tmp2, tmp2);
  crc32(tmp2, tmp1, 4);
  xorl(in1, tmp2);
  xorl(in_out, in1);
  xorl(in_out, in2);
}

// Set N to predefined value
// Subtract from a length of a buffer
// execute in a loop:
// CRC_A = 0xFFFFFFFF, CRC_B = 0, CRC_C = 0
// for i = 1 to N do
//  CRC_A = CRC32(CRC_A, A[i])
//  CRC_B = CRC32(CRC_B, B[i])
//  CRC_C = CRC32(CRC_C, C[i])
// end for
// Recombine
void MacroAssembler::crc32c_proc_chunk(uint32_t size, uint32_t const_or_pre_comp_const_index_u1, uint32_t const_or_pre_comp_const_index_u2, bool is_pclmulqdq_supported,
                                       Register in_out1, Register in_out2, Register in_out3,
                                       Register tmp1, Register tmp2, Register tmp3,
                                       XMMRegister w_xtmp1, XMMRegister w_xtmp2, XMMRegister w_xtmp3,
                                       Register tmp4, Register tmp5,
                                       Register n_tmp6) {
  Label L_processPartitions;
  Label L_processPartition;
  Label L_exit;

  bind(L_processPartitions);
  cmpl(in_out1, 3 * size);
  jcc(Assembler::less, L_exit);
    xorl(tmp1, tmp1);
    xorl(tmp2, tmp2);
    movq(tmp3, in_out2);
    addq(tmp3, size);

    bind(L_processPartition);
      crc32(in_out3, Address(in_out2, 0), 8);
      crc32(tmp1, Address(in_out2, size), 8);
      crc32(tmp2, Address(in_out2, size * 2), 8);
      addq(in_out2, 8);
      cmpq(in_out2, tmp3);
      jcc(Assembler::less, L_processPartition);
    crc32c_rec_alt2(const_or_pre_comp_const_index_u1, const_or_pre_comp_const_index_u2, is_pclmulqdq_supported, in_out3, tmp1, tmp2,
            w_xtmp1, w_xtmp2, w_xtmp3,
            tmp4, tmp5,
            n_tmp6);
    addq(in_out2, 2 * size);
    subl(in_out1, 3 * size);
    jmp(L_processPartitions);

  bind(L_exit);
}

// Algorithm 2: Pipelined usage of the CRC32 instruction.
// Input: A buffer I of L bytes.
// Output: the CRC32C value of the buffer.
// Notations:
// Write L = 24N + r, with N = floor (L/24).
// r = L mod 24 (0 <= r < 24).
// Consider I as the concatenation of A|B|C|R, where A, B, C, each,
// N quadwords, and R consists of r bytes.
// A[j] = I [8j+7:8j], j= 0, 1, ..., N-1
// B[j] = I [N + 8j+7:N + 8j], j= 0, 1, ..., N-1
// C[j] = I [2N + 8j+7:2N + 8j], j= 0, 1, ..., N-1
// if r > 0 R[j] = I [3N +j], j= 0, 1, ...,r-1
void MacroAssembler::crc32c_ipl_alg2_alt2(Register in_out, Register in1, Register in2,
                                          Register tmp1, Register tmp2, Register tmp3,
                                          Register tmp4, Register tmp5, Register tmp6,
                                          XMMRegister w_xtmp1, XMMRegister w_xtmp2, XMMRegister w_xtmp3,
                                          bool is_pclmulqdq_supported) {
  uint32_t const_or_pre_comp_const_index[CRC32C_NUM_PRECOMPUTED_CONSTANTS];
  Label L_wordByWord;
  Label L_byteByByteProlog;
  Label L_byteByByte;
  Label L_exit;

  if (is_pclmulqdq_supported ) {
    const_or_pre_comp_const_index[1] = *(uint32_t *)StubRoutines::crc32c_table_addr();
    const_or_pre_comp_const_index[0] = *((uint32_t *)StubRoutines::crc32c_table_addr() + 1);

    const_or_pre_comp_const_index[3] = *((uint32_t *)StubRoutines::crc32c_table_addr() + 2);
    const_or_pre_comp_const_index[2] = *((uint32_t *)StubRoutines::crc32c_table_addr() + 3);

    const_or_pre_comp_const_index[5] = *((uint32_t *)StubRoutines::crc32c_table_addr() + 4);
    const_or_pre_comp_const_index[4] = *((uint32_t *)StubRoutines::crc32c_table_addr() + 5);
    assert((CRC32C_NUM_PRECOMPUTED_CONSTANTS - 1 ) == 5, "Checking whether you declared all of the constants based on the number of \"chunks\"");
  } else {
    const_or_pre_comp_const_index[0] = 1;
    const_or_pre_comp_const_index[1] = 0;

    const_or_pre_comp_const_index[2] = 3;
    const_or_pre_comp_const_index[3] = 2;

    const_or_pre_comp_const_index[4] = 5;
    const_or_pre_comp_const_index[5] = 4;
   }
  crc32c_proc_chunk(CRC32C_HIGH, const_or_pre_comp_const_index[0], const_or_pre_comp_const_index[1], is_pclmulqdq_supported,
                    in2, in1, in_out,
                    tmp1, tmp2, tmp3,
                    w_xtmp1, w_xtmp2, w_xtmp3,
                    tmp4, tmp5,
                    tmp6);
  crc32c_proc_chunk(CRC32C_MIDDLE, const_or_pre_comp_const_index[2], const_or_pre_comp_const_index[3], is_pclmulqdq_supported,
                    in2, in1, in_out,
                    tmp1, tmp2, tmp3,
                    w_xtmp1, w_xtmp2, w_xtmp3,
                    tmp4, tmp5,
                    tmp6);
  crc32c_proc_chunk(CRC32C_LOW, const_or_pre_comp_const_index[4], const_or_pre_comp_const_index[5], is_pclmulqdq_supported,
                    in2, in1, in_out,
                    tmp1, tmp2, tmp3,
                    w_xtmp1, w_xtmp2, w_xtmp3,
                    tmp4, tmp5,
                    tmp6);
  movl(tmp1, in2);
  andl(tmp1, 0x00000007);
  negl(tmp1);
  addl(tmp1, in2);
  addq(tmp1, in1);

  cmpq(in1, tmp1);
  jccb(Assembler::greaterEqual, L_byteByByteProlog);
  align(16);
  BIND(L_wordByWord);
    crc32(in_out, Address(in1, 0), 8);
    addq(in1, 8);
    cmpq(in1, tmp1);
    jcc(Assembler::less, L_wordByWord);

  BIND(L_byteByByteProlog);
  andl(in2, 0x00000007);
  movl(tmp2, 1);

  cmpl(tmp2, in2);
  jccb(Assembler::greater, L_exit);
  BIND(L_byteByByte);
    crc32(in_out, Address(in1, 0), 1);
    incq(in1);
    incl(tmp2);
    cmpl(tmp2, in2);
    jcc(Assembler::lessEqual, L_byteByByte);

  BIND(L_exit);
}
#undef BIND
#undef BLOCK_COMMENT

// Compress char[] array to byte[].
// Intrinsic for java.lang.StringUTF16.compress(char[] src, int srcOff, byte[] dst, int dstOff, int len)
// Return the array length if every element in array can be encoded,
// otherwise, the index of first non-latin1 (> 0xff) character.
//   @IntrinsicCandidate
//   public static int compress(char[] src, int srcOff, byte[] dst, int dstOff, int len) {
//     for (int i = 0; i < len; i++) {
//       char c = src[srcOff];
//       if (c > 0xff) {
//           return i;  // return index of non-latin1 char
//       }
//       dst[dstOff] = (byte)c;
//       srcOff++;
//       dstOff++;
//     }
//     return len;
//   }
void MacroAssembler::char_array_compress(Register src, Register dst, Register len,
  XMMRegister tmp1Reg, XMMRegister tmp2Reg,
  XMMRegister tmp3Reg, XMMRegister tmp4Reg,
  Register tmp5, Register result, KRegister mask1, KRegister mask2) {
  Label copy_chars_loop, done, reset_sp, copy_tail;

  // rsi: src
  // rdi: dst
  // rdx: len
  // rcx: tmp5
  // rax: result

  // rsi holds start addr of source char[] to be compressed
  // rdi holds start addr of destination byte[]
  // rdx holds length

  assert(len != result, "");

  // save length for return
  movl(result, len);

  if ((AVX3Threshold == 0) && (UseAVX > 2) && // AVX512
    VM_Version::supports_avx512vlbw() &&
    VM_Version::supports_bmi2()) {

    Label copy_32_loop, copy_loop_tail, below_threshold, reset_for_copy_tail;

    // alignment
    Label post_alignment;

    // if length of the string is less than 32, handle it the old fashioned way
    testl(len, -32);
    jcc(Assembler::zero, below_threshold);

    // First check whether a character is compressible ( <= 0xFF).
    // Create mask to test for Unicode chars inside zmm vector
    movl(tmp5, 0x00FF);
    evpbroadcastw(tmp2Reg, tmp5, Assembler::AVX_512bit);

    testl(len, -64);
    jccb(Assembler::zero, post_alignment);

    movl(tmp5, dst);
    andl(tmp5, (32 - 1));
    negl(tmp5);
    andl(tmp5, (32 - 1));

    // bail out when there is nothing to be done
    testl(tmp5, 0xFFFFFFFF);
    jccb(Assembler::zero, post_alignment);

    // ~(~0 << len), where len is the # of remaining elements to process
    movl(len, 0xFFFFFFFF);
    shlxl(len, len, tmp5);
    notl(len);
    kmovdl(mask2, len);
    movl(len, result);

    evmovdquw(tmp1Reg, mask2, Address(src, 0), /*merge*/ false, Assembler::AVX_512bit);
    evpcmpw(mask1, mask2, tmp1Reg, tmp2Reg, Assembler::le, /*signed*/ false, Assembler::AVX_512bit);
    ktestd(mask1, mask2);
    jcc(Assembler::carryClear, copy_tail);

    evpmovwb(Address(dst, 0), mask2, tmp1Reg, Assembler::AVX_512bit);

    addptr(src, tmp5);
    addptr(src, tmp5);
    addptr(dst, tmp5);
    subl(len, tmp5);

    bind(post_alignment);
    // end of alignment

    movl(tmp5, len);
    andl(tmp5, (32 - 1));    // tail count (in chars)
    andl(len, ~(32 - 1));    // vector count (in chars)
    jccb(Assembler::zero, copy_loop_tail);

    lea(src, Address(src, len, Address::times_2));
    lea(dst, Address(dst, len, Address::times_1));
    negptr(len);

    bind(copy_32_loop);
    evmovdquw(tmp1Reg, Address(src, len, Address::times_2), Assembler::AVX_512bit);
    evpcmpuw(mask1, tmp1Reg, tmp2Reg, Assembler::le, Assembler::AVX_512bit);
    kortestdl(mask1, mask1);
    jccb(Assembler::carryClear, reset_for_copy_tail);

    // All elements in current processed chunk are valid candidates for
    // compression. Write a truncated byte elements to the memory.
    evpmovwb(Address(dst, len, Address::times_1), tmp1Reg, Assembler::AVX_512bit);
    addptr(len, 32);
    jccb(Assembler::notZero, copy_32_loop);

    bind(copy_loop_tail);
    // bail out when there is nothing to be done
    testl(tmp5, 0xFFFFFFFF);
    jcc(Assembler::zero, done);

    movl(len, tmp5);

    // ~(~0 << len), where len is the # of remaining elements to process
    movl(tmp5, 0xFFFFFFFF);
    shlxl(tmp5, tmp5, len);
    notl(tmp5);

    kmovdl(mask2, tmp5);

    evmovdquw(tmp1Reg, mask2, Address(src, 0), /*merge*/ false, Assembler::AVX_512bit);
    evpcmpw(mask1, mask2, tmp1Reg, tmp2Reg, Assembler::le, /*signed*/ false, Assembler::AVX_512bit);
    ktestd(mask1, mask2);
    jcc(Assembler::carryClear, copy_tail);

    evpmovwb(Address(dst, 0), mask2, tmp1Reg, Assembler::AVX_512bit);
    jmp(done);

    bind(reset_for_copy_tail);
    lea(src, Address(src, tmp5, Address::times_2));
    lea(dst, Address(dst, tmp5, Address::times_1));
    subptr(len, tmp5);
    jmp(copy_chars_loop);

    bind(below_threshold);
  }

  if (UseSSE42Intrinsics) {
    Label copy_32_loop, copy_16, copy_tail_sse, reset_for_copy_tail;

    // vectored compression
    testl(len, 0xfffffff8);
    jcc(Assembler::zero, copy_tail);

    movl(tmp5, 0xff00ff00);   // create mask to test for Unicode chars in vectors
    movdl(tmp1Reg, tmp5);
    pshufd(tmp1Reg, tmp1Reg, 0);   // store Unicode mask in tmp1Reg

    andl(len, 0xfffffff0);
    jccb(Assembler::zero, copy_16);

    // compress 16 chars per iter
    pxor(tmp4Reg, tmp4Reg);

    lea(src, Address(src, len, Address::times_2));
    lea(dst, Address(dst, len, Address::times_1));
    negptr(len);

    bind(copy_32_loop);
    movdqu(tmp2Reg, Address(src, len, Address::times_2));     // load 1st 8 characters
    por(tmp4Reg, tmp2Reg);
    movdqu(tmp3Reg, Address(src, len, Address::times_2, 16)); // load next 8 characters
    por(tmp4Reg, tmp3Reg);
    ptest(tmp4Reg, tmp1Reg);       // check for Unicode chars in next vector
    jccb(Assembler::notZero, reset_for_copy_tail);
    packuswb(tmp2Reg, tmp3Reg);    // only ASCII chars; compress each to 1 byte
    movdqu(Address(dst, len, Address::times_1), tmp2Reg);
    addptr(len, 16);
    jccb(Assembler::notZero, copy_32_loop);

    // compress next vector of 8 chars (if any)
    bind(copy_16);
    // len = 0
    testl(result, 0x00000008);     // check if there's a block of 8 chars to compress
    jccb(Assembler::zero, copy_tail_sse);

    pxor(tmp3Reg, tmp3Reg);

    movdqu(tmp2Reg, Address(src, 0));
    ptest(tmp2Reg, tmp1Reg);       // check for Unicode chars in vector
    jccb(Assembler::notZero, reset_for_copy_tail);
    packuswb(tmp2Reg, tmp3Reg);    // only LATIN1 chars; compress each to 1 byte
    movq(Address(dst, 0), tmp2Reg);
    addptr(src, 16);
    addptr(dst, 8);
    jmpb(copy_tail_sse);

    bind(reset_for_copy_tail);
    movl(tmp5, result);
    andl(tmp5, 0x0000000f);
    lea(src, Address(src, tmp5, Address::times_2));
    lea(dst, Address(dst, tmp5, Address::times_1));
    subptr(len, tmp5);
    jmpb(copy_chars_loop);

    bind(copy_tail_sse);
    movl(len, result);
    andl(len, 0x00000007);    // tail count (in chars)
  }
  // compress 1 char per iter
  bind(copy_tail);
  testl(len, len);
  jccb(Assembler::zero, done);
  lea(src, Address(src, len, Address::times_2));
  lea(dst, Address(dst, len, Address::times_1));
  negptr(len);

  bind(copy_chars_loop);
  load_unsigned_short(tmp5, Address(src, len, Address::times_2));
  testl(tmp5, 0xff00);      // check if Unicode char
  jccb(Assembler::notZero, reset_sp);
  movb(Address(dst, len, Address::times_1), tmp5);  // ASCII char; compress to 1 byte
  increment(len);
  jccb(Assembler::notZero, copy_chars_loop);

  // add len then return (len will be zero if compress succeeded, otherwise negative)
  bind(reset_sp);
  addl(result, len);

  bind(done);
}

// Inflate byte[] array to char[].
//   ..\jdk\src\java.base\share\classes\java\lang\StringLatin1.java
//   @IntrinsicCandidate
//   private static void inflate(byte[] src, int srcOff, char[] dst, int dstOff, int len) {
//     for (int i = 0; i < len; i++) {
//       dst[dstOff++] = (char)(src[srcOff++] & 0xff);
//     }
//   }
void MacroAssembler::byte_array_inflate(Register src, Register dst, Register len,
  XMMRegister tmp1, Register tmp2, KRegister mask) {
  Label copy_chars_loop, done, below_threshold, avx3_threshold;
  // rsi: src
  // rdi: dst
  // rdx: len
  // rcx: tmp2

  // rsi holds start addr of source byte[] to be inflated
  // rdi holds start addr of destination char[]
  // rdx holds length
  assert_different_registers(src, dst, len, tmp2);
  movl(tmp2, len);
  if ((UseAVX > 2) && // AVX512
    VM_Version::supports_avx512vlbw() &&
    VM_Version::supports_bmi2()) {

    Label copy_32_loop, copy_tail;
    Register tmp3_aliased = len;

    // if length of the string is less than 16, handle it in an old fashioned way
    testl(len, -16);
    jcc(Assembler::zero, below_threshold);

    testl(len, -1 * AVX3Threshold);
    jcc(Assembler::zero, avx3_threshold);

    // In order to use only one arithmetic operation for the main loop we use
    // this pre-calculation
    andl(tmp2, (32 - 1)); // tail count (in chars), 32 element wide loop
    andl(len, -32);     // vector count
    jccb(Assembler::zero, copy_tail);

    lea(src, Address(src, len, Address::times_1));
    lea(dst, Address(dst, len, Address::times_2));
    negptr(len);


    // inflate 32 chars per iter
    bind(copy_32_loop);
    vpmovzxbw(tmp1, Address(src, len, Address::times_1), Assembler::AVX_512bit);
    evmovdquw(Address(dst, len, Address::times_2), tmp1, Assembler::AVX_512bit);
    addptr(len, 32);
    jcc(Assembler::notZero, copy_32_loop);

    bind(copy_tail);
    // bail out when there is nothing to be done
    testl(tmp2, -1); // we don't destroy the contents of tmp2 here
    jcc(Assembler::zero, done);

    // ~(~0 << length), where length is the # of remaining elements to process
    movl(tmp3_aliased, -1);
    shlxl(tmp3_aliased, tmp3_aliased, tmp2);
    notl(tmp3_aliased);
    kmovdl(mask, tmp3_aliased);
    evpmovzxbw(tmp1, mask, Address(src, 0), Assembler::AVX_512bit);
    evmovdquw(Address(dst, 0), mask, tmp1, /*merge*/ true, Assembler::AVX_512bit);

    jmp(done);
    bind(avx3_threshold);
  }
  if (UseSSE42Intrinsics) {
    Label copy_16_loop, copy_8_loop, copy_bytes, copy_new_tail, copy_tail;

    if (UseAVX > 1) {
      andl(tmp2, (16 - 1));
      andl(len, -16);
      jccb(Assembler::zero, copy_new_tail);
    } else {
      andl(tmp2, 0x00000007);   // tail count (in chars)
      andl(len, 0xfffffff8);    // vector count (in chars)
      jccb(Assembler::zero, copy_tail);
    }

    // vectored inflation
    lea(src, Address(src, len, Address::times_1));
    lea(dst, Address(dst, len, Address::times_2));
    negptr(len);

    if (UseAVX > 1) {
      bind(copy_16_loop);
      vpmovzxbw(tmp1, Address(src, len, Address::times_1), Assembler::AVX_256bit);
      vmovdqu(Address(dst, len, Address::times_2), tmp1);
      addptr(len, 16);
      jcc(Assembler::notZero, copy_16_loop);

      bind(below_threshold);
      bind(copy_new_tail);
      movl(len, tmp2);
      andl(tmp2, 0x00000007);
      andl(len, 0xFFFFFFF8);
      jccb(Assembler::zero, copy_tail);

      pmovzxbw(tmp1, Address(src, 0));
      movdqu(Address(dst, 0), tmp1);
      addptr(src, 8);
      addptr(dst, 2 * 8);

      jmp(copy_tail, true);
    }

    // inflate 8 chars per iter
    bind(copy_8_loop);
    pmovzxbw(tmp1, Address(src, len, Address::times_1));  // unpack to 8 words
    movdqu(Address(dst, len, Address::times_2), tmp1);
    addptr(len, 8);
    jcc(Assembler::notZero, copy_8_loop);

    bind(copy_tail);
    movl(len, tmp2);

    cmpl(len, 4);
    jccb(Assembler::less, copy_bytes);

    movdl(tmp1, Address(src, 0));  // load 4 byte chars
    pmovzxbw(tmp1, tmp1);
    movq(Address(dst, 0), tmp1);
    subptr(len, 4);
    addptr(src, 4);
    addptr(dst, 8);

    bind(copy_bytes);
  } else {
    bind(below_threshold);
  }

  testl(len, len);
  jccb(Assembler::zero, done);
  lea(src, Address(src, len, Address::times_1));
  lea(dst, Address(dst, len, Address::times_2));
  negptr(len);

  // inflate 1 char per iter
  bind(copy_chars_loop);
  load_unsigned_byte(tmp2, Address(src, len, Address::times_1));  // load byte char
  movw(Address(dst, len, Address::times_2), tmp2);  // inflate byte char to word
  increment(len);
  jcc(Assembler::notZero, copy_chars_loop);

  bind(done);
}

void MacroAssembler::evmovdqu(BasicType type, KRegister kmask, XMMRegister dst, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
    case T_BOOLEAN:
      evmovdqub(dst, kmask, src, merge, vector_len);
      break;
    case T_CHAR:
    case T_SHORT:
      evmovdquw(dst, kmask, src, merge, vector_len);
      break;
    case T_INT:
    case T_FLOAT:
      evmovdqul(dst, kmask, src, merge, vector_len);
      break;
    case T_LONG:
    case T_DOUBLE:
      evmovdquq(dst, kmask, src, merge, vector_len);
      break;
    default:
      fatal("Unexpected type argument %s", type2name(type));
      break;
  }
}


void MacroAssembler::evmovdqu(BasicType type, KRegister kmask, XMMRegister dst, Address src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
    case T_BOOLEAN:
      evmovdqub(dst, kmask, src, merge, vector_len);
      break;
    case T_CHAR:
    case T_SHORT:
      evmovdquw(dst, kmask, src, merge, vector_len);
      break;
    case T_INT:
    case T_FLOAT:
      evmovdqul(dst, kmask, src, merge, vector_len);
      break;
    case T_LONG:
    case T_DOUBLE:
      evmovdquq(dst, kmask, src, merge, vector_len);
      break;
    default:
      fatal("Unexpected type argument %s", type2name(type));
      break;
  }
}

void MacroAssembler::evmovdqu(BasicType type, KRegister kmask, Address dst, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
    case T_BOOLEAN:
      evmovdqub(dst, kmask, src, merge, vector_len);
      break;
    case T_CHAR:
    case T_SHORT:
      evmovdquw(dst, kmask, src, merge, vector_len);
      break;
    case T_INT:
    case T_FLOAT:
      evmovdqul(dst, kmask, src, merge, vector_len);
      break;
    case T_LONG:
    case T_DOUBLE:
      evmovdquq(dst, kmask, src, merge, vector_len);
      break;
    default:
      fatal("Unexpected type argument %s", type2name(type));
      break;
  }
}

void MacroAssembler::knot(uint masklen, KRegister dst, KRegister src, KRegister ktmp, Register rtmp) {
  switch(masklen) {
    case 2:
       knotbl(dst, src);
       movl(rtmp, 3);
       kmovbl(ktmp, rtmp);
       kandbl(dst, ktmp, dst);
       break;
    case 4:
       knotbl(dst, src);
       movl(rtmp, 15);
       kmovbl(ktmp, rtmp);
       kandbl(dst, ktmp, dst);
       break;
    case 8:
       knotbl(dst, src);
       break;
    case 16:
       knotwl(dst, src);
       break;
    case 32:
       knotdl(dst, src);
       break;
    case 64:
       knotql(dst, src);
       break;
    default:
      fatal("Unexpected vector length %d", masklen);
      break;
  }
}

void MacroAssembler::kand(BasicType type, KRegister dst, KRegister src1, KRegister src2) {
  switch(type) {
    case T_BOOLEAN:
    case T_BYTE:
       kandbl(dst, src1, src2);
       break;
    case T_CHAR:
    case T_SHORT:
       kandwl(dst, src1, src2);
       break;
    case T_INT:
    case T_FLOAT:
       kanddl(dst, src1, src2);
       break;
    case T_LONG:
    case T_DOUBLE:
       kandql(dst, src1, src2);
       break;
    default:
      fatal("Unexpected type argument %s", type2name(type));
      break;
  }
}

void MacroAssembler::kor(BasicType type, KRegister dst, KRegister src1, KRegister src2) {
  switch(type) {
    case T_BOOLEAN:
    case T_BYTE:
       korbl(dst, src1, src2);
       break;
    case T_CHAR:
    case T_SHORT:
       korwl(dst, src1, src2);
       break;
    case T_INT:
    case T_FLOAT:
       kordl(dst, src1, src2);
       break;
    case T_LONG:
    case T_DOUBLE:
       korql(dst, src1, src2);
       break;
    default:
      fatal("Unexpected type argument %s", type2name(type));
      break;
  }
}

void MacroAssembler::kxor(BasicType type, KRegister dst, KRegister src1, KRegister src2) {
  switch(type) {
    case T_BOOLEAN:
    case T_BYTE:
       kxorbl(dst, src1, src2);
       break;
    case T_CHAR:
    case T_SHORT:
       kxorwl(dst, src1, src2);
       break;
    case T_INT:
    case T_FLOAT:
       kxordl(dst, src1, src2);
       break;
    case T_LONG:
    case T_DOUBLE:
       kxorql(dst, src1, src2);
       break;
    default:
      fatal("Unexpected type argument %s", type2name(type));
      break;
  }
}

void MacroAssembler::evperm(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_BOOLEAN:
    case T_BYTE:
      evpermb(dst, mask, nds, src, merge, vector_len); break;
    case T_CHAR:
    case T_SHORT:
      evpermw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
    case T_FLOAT:
      evpermd(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
    case T_DOUBLE:
      evpermq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evperm(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, Address src, bool merge, int vector_len) {
  switch(type) {
    case T_BOOLEAN:
    case T_BYTE:
      evpermb(dst, mask, nds, src, merge, vector_len); break;
    case T_CHAR:
    case T_SHORT:
      evpermw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
    case T_FLOAT:
      evpermd(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
    case T_DOUBLE:
      evpermq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evpminu(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, Address src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
      evpminub(dst, mask, nds, src, merge, vector_len); break;
    case T_SHORT:
      evpminuw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
      evpminud(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpminuq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evpmaxu(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, Address src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
      evpmaxub(dst, mask, nds, src, merge, vector_len); break;
    case T_SHORT:
      evpmaxuw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
      evpmaxud(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpmaxuq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evpminu(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
      evpminub(dst, mask, nds, src, merge, vector_len); break;
    case T_SHORT:
      evpminuw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
      evpminud(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpminuq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evpmaxu(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
      evpmaxub(dst, mask, nds, src, merge, vector_len); break;
    case T_SHORT:
      evpmaxuw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
      evpmaxud(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpmaxuq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evpmins(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, Address src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
      evpminsb(dst, mask, nds, src, merge, vector_len); break;
    case T_SHORT:
      evpminsw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
      evpminsd(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpminsq(dst, mask, nds, src, merge, vector_len); break;
    case T_FLOAT:
      evminmaxps(dst, mask, nds, src, merge, AVX10_2_MINMAX_MIN_COMPARE_SIGN, vector_len); break;
    case T_DOUBLE:
      evminmaxpd(dst, mask, nds, src, merge, AVX10_2_MINMAX_MIN_COMPARE_SIGN, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evpmaxs(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, Address src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
      evpmaxsb(dst, mask, nds, src, merge, vector_len); break;
    case T_SHORT:
      evpmaxsw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
      evpmaxsd(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpmaxsq(dst, mask, nds, src, merge, vector_len); break;
    case T_FLOAT:
      evminmaxps(dst, mask, nds, src, merge, AVX10_2_MINMAX_MAX_COMPARE_SIGN, vector_len); break;
    case T_DOUBLE:
      evminmaxpd(dst, mask, nds, src, merge, AVX10_2_MINMAX_MAX_COMPARE_SIGN, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evpmins(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
      evpminsb(dst, mask, nds, src, merge, vector_len); break;
    case T_SHORT:
      evpminsw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
      evpminsd(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpminsq(dst, mask, nds, src, merge, vector_len); break;
    case T_FLOAT:
      evminmaxps(dst, mask, nds, src, merge, AVX10_2_MINMAX_MIN_COMPARE_SIGN, vector_len); break;
    case T_DOUBLE:
      evminmaxpd(dst, mask, nds, src, merge, AVX10_2_MINMAX_MIN_COMPARE_SIGN, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evpmaxs(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_BYTE:
      evpmaxsb(dst, mask, nds, src, merge, vector_len); break;
    case T_SHORT:
      evpmaxsw(dst, mask, nds, src, merge, vector_len); break;
    case T_INT:
      evpmaxsd(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpmaxsq(dst, mask, nds, src, merge, vector_len); break;
    case T_FLOAT:
      evminmaxps(dst, mask, nds, src, merge, AVX10_2_MINMAX_MAX_COMPARE_SIGN, vector_len); break;
    case T_DOUBLE:
      evminmaxps(dst, mask, nds, src, merge, AVX10_2_MINMAX_MAX_COMPARE_SIGN, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evxor(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_INT:
      evpxord(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpxorq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evxor(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, Address src, bool merge, int vector_len) {
  switch(type) {
    case T_INT:
      evpxord(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpxorq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evor(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_INT:
      Assembler::evpord(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evporq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evor(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, Address src, bool merge, int vector_len) {
  switch(type) {
    case T_INT:
      Assembler::evpord(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evporq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evand(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, XMMRegister src, bool merge, int vector_len) {
  switch(type) {
    case T_INT:
      evpandd(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpandq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evand(BasicType type, XMMRegister dst, KRegister mask, XMMRegister nds, Address src, bool merge, int vector_len) {
  switch(type) {
    case T_INT:
      evpandd(dst, mask, nds, src, merge, vector_len); break;
    case T_LONG:
      evpandq(dst, mask, nds, src, merge, vector_len); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::kortest(uint masklen, KRegister src1, KRegister src2) {
  switch(masklen) {
    case 8:
       kortestbl(src1, src2);
       break;
    case 16:
       kortestwl(src1, src2);
       break;
    case 32:
       kortestdl(src1, src2);
       break;
    case 64:
       kortestql(src1, src2);
       break;
    default:
      fatal("Unexpected mask length %d", masklen);
      break;
  }
}


void MacroAssembler::ktest(uint masklen, KRegister src1, KRegister src2) {
  switch(masklen)  {
    case 8:
       ktestbl(src1, src2);
       break;
    case 16:
       ktestwl(src1, src2);
       break;
    case 32:
       ktestdl(src1, src2);
       break;
    case 64:
       ktestql(src1, src2);
       break;
    default:
      fatal("Unexpected mask length %d", masklen);
      break;
  }
}

void MacroAssembler::evrold(BasicType type, XMMRegister dst, KRegister mask, XMMRegister src, int shift, bool merge, int vlen_enc) {
  switch(type) {
    case T_INT:
      evprold(dst, mask, src, shift, merge, vlen_enc); break;
    case T_LONG:
      evprolq(dst, mask, src, shift, merge, vlen_enc); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
      break;
  }
}

void MacroAssembler::evrord(BasicType type, XMMRegister dst, KRegister mask, XMMRegister src, int shift, bool merge, int vlen_enc) {
  switch(type) {
    case T_INT:
      evprord(dst, mask, src, shift, merge, vlen_enc); break;
    case T_LONG:
      evprorq(dst, mask, src, shift, merge, vlen_enc); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evrold(BasicType type, XMMRegister dst, KRegister mask, XMMRegister src1, XMMRegister src2, bool merge, int vlen_enc) {
  switch(type) {
    case T_INT:
      evprolvd(dst, mask, src1, src2, merge, vlen_enc); break;
    case T_LONG:
      evprolvq(dst, mask, src1, src2, merge, vlen_enc); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evrord(BasicType type, XMMRegister dst, KRegister mask, XMMRegister src1, XMMRegister src2, bool merge, int vlen_enc) {
  switch(type) {
    case T_INT:
      evprorvd(dst, mask, src1, src2, merge, vlen_enc); break;
    case T_LONG:
      evprorvq(dst, mask, src1, src2, merge, vlen_enc); break;
    default:
      fatal("Unexpected type argument %s", type2name(type)); break;
  }
}

void MacroAssembler::evpandq(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    evpandq(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    evpandq(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::evpaddq(XMMRegister dst, KRegister mask, XMMRegister nds, AddressLiteral src, bool merge, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::evpaddq(dst, mask, nds, as_Address(src), merge, vector_len);
  } else {
    lea(rscratch, src);
    Assembler::evpaddq(dst, mask, nds, Address(rscratch, 0), merge, vector_len);
  }
}

void MacroAssembler::evporq(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    evporq(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    evporq(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vpshufb(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    vpshufb(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    vpshufb(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vpor(XMMRegister dst, XMMRegister nds, AddressLiteral src, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src), "missing");

  if (reachable(src)) {
    Assembler::vpor(dst, nds, as_Address(src), vector_len);
  } else {
    lea(rscratch, src);
    Assembler::vpor(dst, nds, Address(rscratch, 0), vector_len);
  }
}

void MacroAssembler::vpternlogq(XMMRegister dst, int imm8, XMMRegister src2, AddressLiteral src3, int vector_len, Register rscratch) {
  assert(rscratch != noreg || always_reachable(src3), "missing");

  if (reachable(src3)) {
    vpternlogq(dst, imm8, src2, as_Address(src3), vector_len);
  } else {
    lea(rscratch, src3);
    vpternlogq(dst, imm8, src2, Address(rscratch, 0), vector_len);
  }
}

#if COMPILER2_OR_JVMCI

void MacroAssembler::fill_masked(BasicType bt, Address dst, XMMRegister xmm, KRegister mask,
                                 Register length, Register temp, int vec_enc) {
  // Computing mask for predicated vector store.
  movptr(temp, -1);
  bzhiq(temp, temp, length);
  kmov(mask, temp);
  evmovdqu(bt, mask, dst, xmm, true, vec_enc);
}

// Set memory operation for length "less than" 64 bytes.
void MacroAssembler::fill64_masked(uint shift, Register dst, int disp,
                                       XMMRegister xmm, KRegister mask, Register length,
                                       Register temp, bool use64byteVector) {
  assert(MaxVectorSize >= 32, "vector length should be >= 32");
  const BasicType type[] = { T_BYTE, T_SHORT, T_INT, T_LONG};
  if (!use64byteVector) {
    fill32(dst, disp, xmm);
    subptr(length, 32 >> shift);
    fill32_masked(shift, dst, disp + 32, xmm, mask, length, temp);
  } else {
    assert(MaxVectorSize == 64, "vector length != 64");
    fill_masked(type[shift], Address(dst, disp), xmm, mask, length, temp, Assembler::AVX_512bit);
  }
}


void MacroAssembler::fill32_masked(uint shift, Register dst, int disp,
                                       XMMRegister xmm, KRegister mask, Register length,
                                       Register temp) {
  assert(MaxVectorSize >= 32, "vector length should be >= 32");
  const BasicType type[] = { T_BYTE, T_SHORT, T_INT, T_LONG};
  fill_masked(type[shift], Address(dst, disp), xmm, mask, length, temp, Assembler::AVX_256bit);
}


void MacroAssembler::fill32(Address dst, XMMRegister xmm) {
  assert(MaxVectorSize >= 32, "vector length should be >= 32");
  vmovdqu(dst, xmm);
}

void MacroAssembler::fill32(Register dst, int disp, XMMRegister xmm) {
  fill32(Address(dst, disp), xmm);
}

void MacroAssembler::fill64(Address dst, XMMRegister xmm, bool use64byteVector) {
  assert(MaxVectorSize >= 32, "vector length should be >= 32");
  if (!use64byteVector) {
    fill32(dst, xmm);
    fill32(dst.plus_disp(32), xmm);
  } else {
    evmovdquq(dst, xmm, Assembler::AVX_512bit);
  }
}

void MacroAssembler::fill64(Register dst, int disp, XMMRegister xmm, bool use64byteVector) {
  fill64(Address(dst, disp), xmm, use64byteVector);
}

void MacroAssembler::generate_fill_avx3(BasicType type, Register to, Register value,
                                        Register count, Register rtmp, XMMRegister xtmp) {
  Label L_exit;
  Label L_fill_start;
  Label L_fill_64_bytes;
  Label L_fill_96_bytes;
  Label L_fill_128_bytes;
  Label L_fill_128_bytes_loop;
  Label L_fill_128_loop_header;
  Label L_fill_128_bytes_loop_header;
  Label L_fill_128_bytes_loop_pre_header;
  Label L_fill_zmm_sequence;

  int shift = -1;
  int avx3threshold = VM_Version::avx3_threshold();
  switch(type) {
    case T_BYTE:  shift = 0;
      break;
    case T_SHORT: shift = 1;
      break;
    case T_INT:   shift = 2;
      break;
    /* Uncomment when LONG fill stubs are supported.
    case T_LONG:  shift = 3;
      break;
    */
    default:
      fatal("Unhandled type: %s\n", type2name(type));
  }

  if ((avx3threshold != 0)  || (MaxVectorSize == 32)) {

    if (MaxVectorSize == 64) {
      cmpq(count, avx3threshold >> shift);
      jcc(Assembler::greater, L_fill_zmm_sequence);
    }

    evpbroadcast(type, xtmp, value, Assembler::AVX_256bit);

    bind(L_fill_start);

    cmpq(count, 32 >> shift);
    jccb(Assembler::greater, L_fill_64_bytes);
    fill32_masked(shift, to, 0, xtmp, k2, count, rtmp);
    jmp(L_exit);

    bind(L_fill_64_bytes);
    cmpq(count, 64 >> shift);
    jccb(Assembler::greater, L_fill_96_bytes);
    fill64_masked(shift, to, 0, xtmp, k2, count, rtmp);
    jmp(L_exit);

    bind(L_fill_96_bytes);
    cmpq(count, 96 >> shift);
    jccb(Assembler::greater, L_fill_128_bytes);
    fill64(to, 0, xtmp);
    subq(count, 64 >> shift);
    fill32_masked(shift, to, 64, xtmp, k2, count, rtmp);
    jmp(L_exit);

    bind(L_fill_128_bytes);
    cmpq(count, 128 >> shift);
    jccb(Assembler::greater, L_fill_128_bytes_loop_pre_header);
    fill64(to, 0, xtmp);
    fill32(to, 64, xtmp);
    subq(count, 96 >> shift);
    fill32_masked(shift, to, 96, xtmp, k2, count, rtmp);
    jmp(L_exit);

    bind(L_fill_128_bytes_loop_pre_header);
    {
      mov(rtmp, to);
      andq(rtmp, 31);
      jccb(Assembler::zero, L_fill_128_bytes_loop_header);
      negq(rtmp);
      addq(rtmp, 32);
      mov64(r8, -1L);
      bzhiq(r8, r8, rtmp);
      kmovql(k2, r8);
      evmovdqu(T_BYTE, k2, Address(to, 0), xtmp, true, Assembler::AVX_256bit);
      addq(to, rtmp);
      shrq(rtmp, shift);
      subq(count, rtmp);
    }

    cmpq(count, 128 >> shift);
    jcc(Assembler::less, L_fill_start);

    bind(L_fill_128_bytes_loop_header);
    subq(count, 128 >> shift);

    align32();
    bind(L_fill_128_bytes_loop);
      fill64(to, 0, xtmp);
      fill64(to, 64, xtmp);
      addq(to, 128);
      subq(count, 128 >> shift);
      jccb(Assembler::greaterEqual, L_fill_128_bytes_loop);

    addq(count, 128 >> shift);
    jcc(Assembler::zero, L_exit);
    jmp(L_fill_start);
  }

  if (MaxVectorSize == 64) {
    // Sequence using 64 byte ZMM register.
    Label L_fill_128_bytes_zmm;
    Label L_fill_192_bytes_zmm;
    Label L_fill_192_bytes_loop_zmm;
    Label L_fill_192_bytes_loop_header_zmm;
    Label L_fill_192_bytes_loop_pre_header_zmm;
    Label L_fill_start_zmm_sequence;

    bind(L_fill_zmm_sequence);
    evpbroadcast(type, xtmp, value, Assembler::AVX_512bit);

    bind(L_fill_start_zmm_sequence);
    cmpq(count, 64 >> shift);
    jccb(Assembler::greater, L_fill_128_bytes_zmm);
    fill64_masked(shift, to, 0, xtmp, k2, count, rtmp, true);
    jmp(L_exit);

    bind(L_fill_128_bytes_zmm);
    cmpq(count, 128 >> shift);
    jccb(Assembler::greater, L_fill_192_bytes_zmm);
    fill64(to, 0, xtmp, true);
    subq(count, 64 >> shift);
    fill64_masked(shift, to, 64, xtmp, k2, count, rtmp, true);
    jmp(L_exit);

    bind(L_fill_192_bytes_zmm);
    cmpq(count, 192 >> shift);
    jccb(Assembler::greater, L_fill_192_bytes_loop_pre_header_zmm);
    fill64(to, 0, xtmp, true);
    fill64(to, 64, xtmp, true);
    subq(count, 128 >> shift);
    fill64_masked(shift, to, 128, xtmp, k2, count, rtmp, true);
    jmp(L_exit);

    bind(L_fill_192_bytes_loop_pre_header_zmm);
    {
      movq(rtmp, to);
      andq(rtmp, 63);
      jccb(Assembler::zero, L_fill_192_bytes_loop_header_zmm);
      negq(rtmp);
      addq(rtmp, 64);
      mov64(r8, -1L);
      bzhiq(r8, r8, rtmp);
      kmovql(k2, r8);
      evmovdqu(T_BYTE, k2, Address(to, 0), xtmp, true, Assembler::AVX_512bit);
      addq(to, rtmp);
      shrq(rtmp, shift);
      subq(count, rtmp);
    }

    cmpq(count, 192 >> shift);
    jcc(Assembler::less, L_fill_start_zmm_sequence);

    bind(L_fill_192_bytes_loop_header_zmm);
    subq(count, 192 >> shift);

    align32();
    bind(L_fill_192_bytes_loop_zmm);
      fill64(to, 0, xtmp, true);
      fill64(to, 64, xtmp, true);
      fill64(to, 128, xtmp, true);
      addq(to, 192);
      subq(count, 192 >> shift);
      jccb(Assembler::greaterEqual, L_fill_192_bytes_loop_zmm);

    addq(count, 192 >> shift);
    jcc(Assembler::zero, L_exit);
    jmp(L_fill_start_zmm_sequence);
  }
  bind(L_exit);
}
#endif //COMPILER2_OR_JVMCI


void MacroAssembler::convert_f2i(Register dst, XMMRegister src) {
  Label done;
  cvttss2sil(dst, src);
  // Conversion instructions do not match JLS for overflow, underflow and NaN -> fixup in stub
  cmpl(dst, 0x80000000); // float_sign_flip
  jccb(Assembler::notEqual, done);
  subptr(rsp, 8);
  movflt(Address(rsp, 0), src);
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::x86::f2i_fixup())));
  pop(dst);
  bind(done);
}

void MacroAssembler::convert_d2i(Register dst, XMMRegister src) {
  Label done;
  cvttsd2sil(dst, src);
  // Conversion instructions do not match JLS for overflow, underflow and NaN -> fixup in stub
  cmpl(dst, 0x80000000); // float_sign_flip
  jccb(Assembler::notEqual, done);
  subptr(rsp, 8);
  movdbl(Address(rsp, 0), src);
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::x86::d2i_fixup())));
  pop(dst);
  bind(done);
}

void MacroAssembler::convert_f2l(Register dst, XMMRegister src) {
  Label done;
  cvttss2siq(dst, src);
  cmp64(dst, ExternalAddress((address) StubRoutines::x86::double_sign_flip()));
  jccb(Assembler::notEqual, done);
  subptr(rsp, 8);
  movflt(Address(rsp, 0), src);
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::x86::f2l_fixup())));
  pop(dst);
  bind(done);
}

void MacroAssembler::round_float(Register dst, XMMRegister src, Register rtmp, Register rcx) {
  // Following code is line by line assembly translation rounding algorithm.
  // Please refer to java.lang.Math.round(float) algorithm for details.
  const int32_t FloatConsts_EXP_BIT_MASK = 0x7F800000;
  const int32_t FloatConsts_SIGNIFICAND_WIDTH = 24;
  const int32_t FloatConsts_EXP_BIAS = 127;
  const int32_t FloatConsts_SIGNIF_BIT_MASK = 0x007FFFFF;
  const int32_t MINUS_32 = 0xFFFFFFE0;
  Label L_special_case, L_block1, L_exit;
  movl(rtmp, FloatConsts_EXP_BIT_MASK);
  movdl(dst, src);
  andl(dst, rtmp);
  sarl(dst, FloatConsts_SIGNIFICAND_WIDTH - 1);
  movl(rtmp, FloatConsts_SIGNIFICAND_WIDTH - 2 + FloatConsts_EXP_BIAS);
  subl(rtmp, dst);
  movl(rcx, rtmp);
  movl(dst, MINUS_32);
  testl(rtmp, dst);
  jccb(Assembler::notEqual, L_special_case);
  movdl(dst, src);
  andl(dst, FloatConsts_SIGNIF_BIT_MASK);
  orl(dst, FloatConsts_SIGNIF_BIT_MASK + 1);
  movdl(rtmp, src);
  testl(rtmp, rtmp);
  jccb(Assembler::greaterEqual, L_block1);
  negl(dst);
  bind(L_block1);
  sarl(dst);
  addl(dst, 0x1);
  sarl(dst, 0x1);
  jmp(L_exit);
  bind(L_special_case);
  convert_f2i(dst, src);
  bind(L_exit);
}

void MacroAssembler::round_double(Register dst, XMMRegister src, Register rtmp, Register rcx) {
  // Following code is line by line assembly translation rounding algorithm.
  // Please refer to java.lang.Math.round(double) algorithm for details.
  const int64_t DoubleConsts_EXP_BIT_MASK = 0x7FF0000000000000L;
  const int64_t DoubleConsts_SIGNIFICAND_WIDTH = 53;
  const int64_t DoubleConsts_EXP_BIAS = 1023;
  const int64_t DoubleConsts_SIGNIF_BIT_MASK = 0x000FFFFFFFFFFFFFL;
  const int64_t MINUS_64 = 0xFFFFFFFFFFFFFFC0L;
  Label L_special_case, L_block1, L_exit;
  mov64(rtmp, DoubleConsts_EXP_BIT_MASK);
  movq(dst, src);
  andq(dst, rtmp);
  sarq(dst, DoubleConsts_SIGNIFICAND_WIDTH - 1);
  mov64(rtmp, DoubleConsts_SIGNIFICAND_WIDTH - 2 + DoubleConsts_EXP_BIAS);
  subq(rtmp, dst);
  movq(rcx, rtmp);
  mov64(dst, MINUS_64);
  testq(rtmp, dst);
  jccb(Assembler::notEqual, L_special_case);
  movq(dst, src);
  mov64(rtmp, DoubleConsts_SIGNIF_BIT_MASK);
  andq(dst, rtmp);
  mov64(rtmp, DoubleConsts_SIGNIF_BIT_MASK + 1);
  orq(dst, rtmp);
  movq(rtmp, src);
  testq(rtmp, rtmp);
  jccb(Assembler::greaterEqual, L_block1);
  negq(dst);
  bind(L_block1);
  sarq(dst);
  addq(dst, 0x1);
  sarq(dst, 0x1);
  jmp(L_exit);
  bind(L_special_case);
  convert_d2l(dst, src);
  bind(L_exit);
}

void MacroAssembler::convert_d2l(Register dst, XMMRegister src) {
  Label done;
  cvttsd2siq(dst, src);
  cmp64(dst, ExternalAddress((address) StubRoutines::x86::double_sign_flip()));
  jccb(Assembler::notEqual, done);
  subptr(rsp, 8);
  movdbl(Address(rsp, 0), src);
  call(RuntimeAddress(CAST_FROM_FN_PTR(address, StubRoutines::x86::d2l_fixup())));
  pop(dst);
  bind(done);
}

void MacroAssembler::cache_wb(Address line)
{
  // 64 bit cpus always support clflush
  assert(VM_Version::supports_clflush(), "clflush should be available");
  bool optimized = VM_Version::supports_clflushopt();
  bool no_evict = VM_Version::supports_clwb();

  // prefer clwb (writeback without evict) otherwise
  // prefer clflushopt (potentially parallel writeback with evict)
  // otherwise fallback on clflush (serial writeback with evict)

  if (optimized) {
    if (no_evict) {
      clwb(line);
    } else {
      clflushopt(line);
    }
  } else {
    // no need for fence when using CLFLUSH
    clflush(line);
  }
}

void MacroAssembler::cache_wbsync(bool is_pre)
{
  assert(VM_Version::supports_clflush(), "clflush should be available");
  bool optimized = VM_Version::supports_clflushopt();
  bool no_evict = VM_Version::supports_clwb();

  // pick the correct implementation

  if (!is_pre && (optimized || no_evict)) {
    // need an sfence for post flush when using clflushopt or clwb
    // otherwise no no need for any synchroniaztion

    sfence();
  }
}

Assembler::Condition MacroAssembler::negate_condition(Assembler::Condition cond) {
  switch (cond) {
    // Note some conditions are synonyms for others
    case Assembler::zero:         return Assembler::notZero;
    case Assembler::notZero:      return Assembler::zero;
    case Assembler::less:         return Assembler::greaterEqual;
    case Assembler::lessEqual:    return Assembler::greater;
    case Assembler::greater:      return Assembler::lessEqual;
    case Assembler::greaterEqual: return Assembler::less;
    case Assembler::below:        return Assembler::aboveEqual;
    case Assembler::belowEqual:   return Assembler::above;
    case Assembler::above:        return Assembler::belowEqual;
    case Assembler::aboveEqual:   return Assembler::below;
    case Assembler::overflow:     return Assembler::noOverflow;
    case Assembler::noOverflow:   return Assembler::overflow;
    case Assembler::negative:     return Assembler::positive;
    case Assembler::positive:     return Assembler::negative;
    case Assembler::parity:       return Assembler::noParity;
    case Assembler::noParity:     return Assembler::parity;
  }
  ShouldNotReachHere(); return Assembler::overflow;
}

// This is simply a call to Thread::current()
void MacroAssembler::get_thread_slow(Register thread) {
  if (thread != rax) {
    push(rax);
  }
  push(rdi);
  push(rsi);
  push(rdx);
  push(rcx);
  push(r8);
  push(r9);
  push(r10);
  push(r11);

  MacroAssembler::call_VM_leaf_base(CAST_FROM_FN_PTR(address, Thread::current), 0);

  pop(r11);
  pop(r10);
  pop(r9);
  pop(r8);
  pop(rcx);
  pop(rdx);
  pop(rsi);
  pop(rdi);
  if (thread != rax) {
    mov(thread, rax);
    pop(rax);
  }
}

void MacroAssembler::check_stack_alignment(Register sp, const char* msg, unsigned bias, Register tmp) {
  Label L_stack_ok;
  if (bias == 0) {
    testptr(sp, 2 * wordSize - 1);
  } else {
    // lea(tmp, Address(rsp, bias);
    mov(tmp, sp);
    addptr(tmp, bias);
    testptr(tmp, 2 * wordSize - 1);
  }
  jcc(Assembler::equal, L_stack_ok);
  block_comment(msg);
  stop(msg);
  bind(L_stack_ok);
}

// Implements fast-locking.
//
// obj: the object to be locked
// reg_rax: rax
// thread: the thread which attempts to lock obj
// tmp: a temporary register
void MacroAssembler::fast_lock(Register basic_lock, Register obj, Register reg_rax, Register tmp, Label& slow) {
  Register thread = r15_thread;

  assert(reg_rax == rax, "");
  assert_different_registers(basic_lock, obj, reg_rax, thread, tmp);

  Label push;
  const Register top = tmp;

  // Preload the markWord. It is important that this is the first
  // instruction emitted as it is part of C1's null check semantics.
  movptr(reg_rax, Address(obj, oopDesc::mark_offset_in_bytes()));

  if (UseObjectMonitorTable) {
    // Clear cache in case fast locking succeeds or we need to take the slow-path.
    movptr(Address(basic_lock, BasicObjectLock::lock_offset() + in_ByteSize((BasicLock::object_monitor_cache_offset_in_bytes()))), 0);
  }

  if (DiagnoseSyncOnValueBasedClasses != 0) {
    load_klass(tmp, obj, rscratch1);
    testb(Address(tmp, Klass::misc_flags_offset()), KlassFlags::_misc_is_value_based_class);
    jcc(Assembler::notZero, slow);
  }

  // Load top.
  movl(top, Address(thread, JavaThread::lock_stack_top_offset()));

  // Check if the lock-stack is full.
  cmpl(top, LockStack::end_offset());
  jcc(Assembler::greaterEqual, slow);

  // Check for recursion.
  cmpptr(obj, Address(thread, top, Address::times_1, -oopSize));
  jcc(Assembler::equal, push);

  // Check header for monitor (0b10).
  testptr(reg_rax, markWord::monitor_value);
  jcc(Assembler::notZero, slow);

  // Try to lock. Transition lock bits 0b01 => 0b00
  movptr(tmp, reg_rax);
  andptr(tmp, ~(int32_t)markWord::unlocked_value);
  orptr(reg_rax, markWord::unlocked_value);
  lock(); cmpxchgptr(tmp, Address(obj, oopDesc::mark_offset_in_bytes()));
  jcc(Assembler::notEqual, slow);

  // Restore top, CAS clobbers register.
  movl(top, Address(thread, JavaThread::lock_stack_top_offset()));

  bind(push);
  // After successful lock, push object on lock-stack.
  movptr(Address(thread, top), obj);
  incrementl(top, oopSize);
  movl(Address(thread, JavaThread::lock_stack_top_offset()), top);
}

// Implements fast-unlocking.
//
// obj: the object to be unlocked
// reg_rax: rax
// thread: the thread
// tmp: a temporary register
void MacroAssembler::fast_unlock(Register obj, Register reg_rax, Register tmp, Label& slow) {
  Register thread = r15_thread;

  assert(reg_rax == rax, "");
  assert_different_registers(obj, reg_rax, thread, tmp);

  Label unlocked, push_and_slow;
  const Register top = tmp;

  // Check if obj is top of lock-stack.
  movl(top, Address(thread, JavaThread::lock_stack_top_offset()));
  cmpptr(obj, Address(thread, top, Address::times_1, -oopSize));
  jcc(Assembler::notEqual, slow);

  // Pop lock-stack.
  DEBUG_ONLY(movptr(Address(thread, top, Address::times_1, -oopSize), 0);)
  subl(Address(thread, JavaThread::lock_stack_top_offset()), oopSize);

  // Check if recursive.
  cmpptr(obj, Address(thread, top, Address::times_1, -2 * oopSize));
  jcc(Assembler::equal, unlocked);

  // Not recursive. Check header for monitor (0b10).
  movptr(reg_rax, Address(obj, oopDesc::mark_offset_in_bytes()));
  testptr(reg_rax, markWord::monitor_value);
  jcc(Assembler::notZero, push_and_slow);

#ifdef ASSERT
  // Check header not unlocked (0b01).
  Label not_unlocked;
  testptr(reg_rax, markWord::unlocked_value);
  jcc(Assembler::zero, not_unlocked);
  stop("fast_unlock already unlocked");
  bind(not_unlocked);
#endif

  // Try to unlock. Transition lock bits 0b00 => 0b01
  movptr(tmp, reg_rax);
  orptr(tmp, markWord::unlocked_value);
  lock(); cmpxchgptr(tmp, Address(obj, oopDesc::mark_offset_in_bytes()));
  jcc(Assembler::equal, unlocked);

  bind(push_and_slow);
  // Restore lock-stack and handle the unlock in runtime.
#ifdef ASSERT
  movl(top, Address(thread, JavaThread::lock_stack_top_offset()));
  movptr(Address(thread, top), obj);
#endif
  addl(Address(thread, JavaThread::lock_stack_top_offset()), oopSize);
  jmp(slow);

  bind(unlocked);
}

// Saves legacy GPRs state on stack.
void MacroAssembler::save_legacy_gprs() {
  subq(rsp, 16 * wordSize);
  movq(Address(rsp, 15 * wordSize), rax);
  movq(Address(rsp, 14 * wordSize), rcx);
  movq(Address(rsp, 13 * wordSize), rdx);
  movq(Address(rsp, 12 * wordSize), rbx);
  movq(Address(rsp, 10 * wordSize), rbp);
  movq(Address(rsp, 9 * wordSize), rsi);
  movq(Address(rsp, 8 * wordSize), rdi);
  movq(Address(rsp, 7 * wordSize), r8);
  movq(Address(rsp, 6 * wordSize), r9);
  movq(Address(rsp, 5 * wordSize), r10);
  movq(Address(rsp, 4 * wordSize), r11);
  movq(Address(rsp, 3 * wordSize), r12);
  movq(Address(rsp, 2 * wordSize), r13);
  movq(Address(rsp, wordSize), r14);
  movq(Address(rsp, 0), r15);
}

// Resotres back legacy GPRs state from stack.
void MacroAssembler::restore_legacy_gprs() {
  movq(r15, Address(rsp, 0));
  movq(r14, Address(rsp, wordSize));
  movq(r13, Address(rsp, 2 * wordSize));
  movq(r12, Address(rsp, 3 * wordSize));
  movq(r11, Address(rsp, 4 * wordSize));
  movq(r10, Address(rsp, 5 * wordSize));
  movq(r9,  Address(rsp, 6 * wordSize));
  movq(r8,  Address(rsp, 7 * wordSize));
  movq(rdi, Address(rsp, 8 * wordSize));
  movq(rsi, Address(rsp, 9 * wordSize));
  movq(rbp, Address(rsp, 10 * wordSize));
  movq(rbx, Address(rsp, 12 * wordSize));
  movq(rdx, Address(rsp, 13 * wordSize));
  movq(rcx, Address(rsp, 14 * wordSize));
  movq(rax, Address(rsp, 15 * wordSize));
  addq(rsp, 16 * wordSize);
}

void MacroAssembler::setcc(Assembler::Condition comparison, Register dst) {
  if (VM_Version::supports_apx_f()) {
    esetzucc(comparison, dst);
  } else {
    setb(comparison, dst);
    movzbl(dst, dst);
  }
}
