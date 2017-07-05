/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "asm/assembler.inline.hpp"
#include "compiler/disassembler.hpp"
#include "gc_interface/collectedHeap.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "memory/cardTableModRefBS.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "prims/methodHandles.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/interfaceSupport.hpp"
#include "runtime/objectMonitor.hpp"
#include "runtime/os.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/macros.hpp"
#if INCLUDE_ALL_GCS
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1SATBCardTableModRefBS.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#endif // INCLUDE_ALL_GCS

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#define STOP(error) stop(error)
#else
#define BLOCK_COMMENT(str) block_comment(str)
#define STOP(error) block_comment(error); stop(error)
#endif

// Convert the raw encoding form into the form expected by the
// constructor for Address.
Address Address::make_raw(int base, int index, int scale, int disp, relocInfo::relocType disp_reloc) {
  assert(scale == 0, "not supported");
  RelocationHolder rspec;
  if (disp_reloc != relocInfo::none) {
    rspec = Relocation::spec_simple(disp_reloc);
  }

  Register rindex = as_Register(index);
  if (rindex != G0) {
    Address madr(as_Register(base), rindex);
    madr._rspec = rspec;
    return madr;
  } else {
    Address madr(as_Register(base), disp);
    madr._rspec = rspec;
    return madr;
  }
}

Address Argument::address_in_frame() const {
  // Warning: In LP64 mode disp will occupy more than 10 bits, but
  //          op codes such as ld or ldx, only access disp() to get
  //          their simm13 argument.
  int disp = ((_number - Argument::n_register_parameters + frame::memory_parameter_word_sp_offset) * BytesPerWord) + STACK_BIAS;
  if (is_in())
    return Address(FP, disp); // In argument.
  else
    return Address(SP, disp); // Out argument.
}

static const char* argumentNames[][2] = {
  {"A0","P0"}, {"A1","P1"}, {"A2","P2"}, {"A3","P3"}, {"A4","P4"},
  {"A5","P5"}, {"A6","P6"}, {"A7","P7"}, {"A8","P8"}, {"A9","P9"},
  {"A(n>9)","P(n>9)"}
};

const char* Argument::name() const {
  int nofArgs = sizeof argumentNames / sizeof argumentNames[0];
  int num = number();
  if (num >= nofArgs)  num = nofArgs - 1;
  return argumentNames[num][is_in() ? 1 : 0];
}

#ifdef ASSERT
// On RISC, there's no benefit to verifying instruction boundaries.
bool AbstractAssembler::pd_check_instruction_mark() { return false; }
#endif

// Patch instruction inst at offset inst_pos to refer to dest_pos
// and return the resulting instruction.
// We should have pcs, not offsets, but since all is relative, it will work out
// OK.
int MacroAssembler::patched_branch(int dest_pos, int inst, int inst_pos) {
  int m; // mask for displacement field
  int v; // new value for displacement field
  const int word_aligned_ones = -4;
  switch (inv_op(inst)) {
  default: ShouldNotReachHere();
  case call_op:    m = wdisp(word_aligned_ones, 0, 30);  v = wdisp(dest_pos, inst_pos, 30); break;
  case branch_op:
    switch (inv_op2(inst)) {
      case fbp_op2:    m = wdisp(  word_aligned_ones, 0, 19);  v = wdisp(  dest_pos, inst_pos, 19); break;
      case bp_op2:     m = wdisp(  word_aligned_ones, 0, 19);  v = wdisp(  dest_pos, inst_pos, 19); break;
      case fb_op2:     m = wdisp(  word_aligned_ones, 0, 22);  v = wdisp(  dest_pos, inst_pos, 22); break;
      case br_op2:     m = wdisp(  word_aligned_ones, 0, 22);  v = wdisp(  dest_pos, inst_pos, 22); break;
      case bpr_op2: {
        if (is_cbcond(inst)) {
          m = wdisp10(word_aligned_ones, 0);
          v = wdisp10(dest_pos, inst_pos);
        } else {
          m = wdisp16(word_aligned_ones, 0);
          v = wdisp16(dest_pos, inst_pos);
        }
        break;
      }
      default: ShouldNotReachHere();
    }
  }
  return  inst & ~m  |  v;
}

// Return the offset of the branch destionation of instruction inst
// at offset pos.
// Should have pcs, but since all is relative, it works out.
int MacroAssembler::branch_destination(int inst, int pos) {
  int r;
  switch (inv_op(inst)) {
  default: ShouldNotReachHere();
  case call_op:        r = inv_wdisp(inst, pos, 30);  break;
  case branch_op:
    switch (inv_op2(inst)) {
      case fbp_op2:    r = inv_wdisp(  inst, pos, 19);  break;
      case bp_op2:     r = inv_wdisp(  inst, pos, 19);  break;
      case fb_op2:     r = inv_wdisp(  inst, pos, 22);  break;
      case br_op2:     r = inv_wdisp(  inst, pos, 22);  break;
      case bpr_op2: {
        if (is_cbcond(inst)) {
          r = inv_wdisp10(inst, pos);
        } else {
          r = inv_wdisp16(inst, pos);
        }
        break;
      }
      default: ShouldNotReachHere();
    }
  }
  return r;
}

void MacroAssembler::null_check(Register reg, int offset) {
  if (needs_explicit_null_check((intptr_t)offset)) {
    // provoke OS NULL exception if reg = NULL by
    // accessing M[reg] w/o changing any registers
    ld_ptr(reg, 0, G0);
  }
  else {
    // nothing to do, (later) access of M[reg + offset]
    // will provoke OS NULL exception if reg = NULL
  }
}

// Ring buffer jumps

#ifndef PRODUCT
void MacroAssembler::ret(  bool trace )   { if (trace) {
                                                    mov(I7, O7); // traceable register
                                                    JMP(O7, 2 * BytesPerInstWord);
                                                  } else {
                                                    jmpl( I7, 2 * BytesPerInstWord, G0 );
                                                  }
                                                }

void MacroAssembler::retl( bool trace )  { if (trace) JMP(O7, 2 * BytesPerInstWord);
                                                 else jmpl( O7, 2 * BytesPerInstWord, G0 ); }
#endif /* PRODUCT */


void MacroAssembler::jmp2(Register r1, Register r2, const char* file, int line ) {
  assert_not_delayed();
  // This can only be traceable if r1 & r2 are visible after a window save
  if (TraceJumps) {
#ifndef PRODUCT
    save_frame(0);
    verify_thread();
    ld(G2_thread, in_bytes(JavaThread::jmp_ring_index_offset()), O0);
    add(G2_thread, in_bytes(JavaThread::jmp_ring_offset()), O1);
    sll(O0, exact_log2(4*sizeof(intptr_t)), O2);
    add(O2, O1, O1);

    add(r1->after_save(), r2->after_save(), O2);
    set((intptr_t)file, O3);
    set(line, O4);
    Label L;
    // get nearby pc, store jmp target
    call(L, relocInfo::none);  // No relocation for call to pc+0x8
    delayed()->st(O2, O1, 0);
    bind(L);

    // store nearby pc
    st(O7, O1, sizeof(intptr_t));
    // store file
    st(O3, O1, 2*sizeof(intptr_t));
    // store line
    st(O4, O1, 3*sizeof(intptr_t));
    add(O0, 1, O0);
    and3(O0, JavaThread::jump_ring_buffer_size  - 1, O0);
    st(O0, G2_thread, in_bytes(JavaThread::jmp_ring_index_offset()));
    restore();
#endif /* PRODUCT */
  }
  jmpl(r1, r2, G0);
}
void MacroAssembler::jmp(Register r1, int offset, const char* file, int line ) {
  assert_not_delayed();
  // This can only be traceable if r1 is visible after a window save
  if (TraceJumps) {
#ifndef PRODUCT
    save_frame(0);
    verify_thread();
    ld(G2_thread, in_bytes(JavaThread::jmp_ring_index_offset()), O0);
    add(G2_thread, in_bytes(JavaThread::jmp_ring_offset()), O1);
    sll(O0, exact_log2(4*sizeof(intptr_t)), O2);
    add(O2, O1, O1);

    add(r1->after_save(), offset, O2);
    set((intptr_t)file, O3);
    set(line, O4);
    Label L;
    // get nearby pc, store jmp target
    call(L, relocInfo::none);  // No relocation for call to pc+0x8
    delayed()->st(O2, O1, 0);
    bind(L);

    // store nearby pc
    st(O7, O1, sizeof(intptr_t));
    // store file
    st(O3, O1, 2*sizeof(intptr_t));
    // store line
    st(O4, O1, 3*sizeof(intptr_t));
    add(O0, 1, O0);
    and3(O0, JavaThread::jump_ring_buffer_size  - 1, O0);
    st(O0, G2_thread, in_bytes(JavaThread::jmp_ring_index_offset()));
    restore();
#endif /* PRODUCT */
  }
  jmp(r1, offset);
}

// This code sequence is relocatable to any address, even on LP64.
void MacroAssembler::jumpl(const AddressLiteral& addrlit, Register temp, Register d, int offset, const char* file, int line) {
  assert_not_delayed();
  // Force fixed length sethi because NativeJump and NativeFarCall don't handle
  // variable length instruction streams.
  patchable_sethi(addrlit, temp);
  Address a(temp, addrlit.low10() + offset);  // Add the offset to the displacement.
  if (TraceJumps) {
#ifndef PRODUCT
    // Must do the add here so relocation can find the remainder of the
    // value to be relocated.
    add(a.base(), a.disp(), a.base(), addrlit.rspec(offset));
    save_frame(0);
    verify_thread();
    ld(G2_thread, in_bytes(JavaThread::jmp_ring_index_offset()), O0);
    add(G2_thread, in_bytes(JavaThread::jmp_ring_offset()), O1);
    sll(O0, exact_log2(4*sizeof(intptr_t)), O2);
    add(O2, O1, O1);

    set((intptr_t)file, O3);
    set(line, O4);
    Label L;

    // get nearby pc, store jmp target
    call(L, relocInfo::none);  // No relocation for call to pc+0x8
    delayed()->st(a.base()->after_save(), O1, 0);
    bind(L);

    // store nearby pc
    st(O7, O1, sizeof(intptr_t));
    // store file
    st(O3, O1, 2*sizeof(intptr_t));
    // store line
    st(O4, O1, 3*sizeof(intptr_t));
    add(O0, 1, O0);
    and3(O0, JavaThread::jump_ring_buffer_size  - 1, O0);
    st(O0, G2_thread, in_bytes(JavaThread::jmp_ring_index_offset()));
    restore();
    jmpl(a.base(), G0, d);
#else
    jmpl(a.base(), a.disp(), d);
#endif /* PRODUCT */
  } else {
    jmpl(a.base(), a.disp(), d);
  }
}

void MacroAssembler::jump(const AddressLiteral& addrlit, Register temp, int offset, const char* file, int line) {
  jumpl(addrlit, temp, G0, offset, file, line);
}


// Conditional breakpoint (for assertion checks in assembly code)
void MacroAssembler::breakpoint_trap(Condition c, CC cc) {
  trap(c, cc, G0, ST_RESERVED_FOR_USER_0);
}

// We want to use ST_BREAKPOINT here, but the debugger is confused by it.
void MacroAssembler::breakpoint_trap() {
  trap(ST_RESERVED_FOR_USER_0);
}

// Write serialization page so VM thread can do a pseudo remote membar
// We use the current thread pointer to calculate a thread specific
// offset to write to within the page. This minimizes bus traffic
// due to cache line collision.
void MacroAssembler::serialize_memory(Register thread, Register tmp1, Register tmp2) {
  srl(thread, os::get_serialize_page_shift_count(), tmp2);
  if (Assembler::is_simm13(os::vm_page_size())) {
    and3(tmp2, (os::vm_page_size() - sizeof(int)), tmp2);
  }
  else {
    set((os::vm_page_size() - sizeof(int)), tmp1);
    and3(tmp2, tmp1, tmp2);
  }
  set(os::get_memory_serialize_page(), tmp1);
  st(G0, tmp1, tmp2);
}



void MacroAssembler::enter() {
  Unimplemented();
}

void MacroAssembler::leave() {
  Unimplemented();
}

// Calls to C land

#ifdef ASSERT
// a hook for debugging
static Thread* reinitialize_thread() {
  return ThreadLocalStorage::thread();
}
#else
#define reinitialize_thread ThreadLocalStorage::thread
#endif

#ifdef ASSERT
address last_get_thread = NULL;
#endif

// call this when G2_thread is not known to be valid
void MacroAssembler::get_thread() {
  save_frame(0);                // to avoid clobbering O0
  mov(G1, L0);                  // avoid clobbering G1
  mov(G5_method, L1);           // avoid clobbering G5
  mov(G3, L2);                  // avoid clobbering G3 also
  mov(G4, L5);                  // avoid clobbering G4
#ifdef ASSERT
  AddressLiteral last_get_thread_addrlit(&last_get_thread);
  set(last_get_thread_addrlit, L3);
  rdpc(L4);
  inc(L4, 3 * BytesPerInstWord); // skip rdpc + inc + st_ptr to point L4 at call  st_ptr(L4, L3, 0);
#endif
  call(CAST_FROM_FN_PTR(address, reinitialize_thread), relocInfo::runtime_call_type);
  delayed()->nop();
  mov(L0, G1);
  mov(L1, G5_method);
  mov(L2, G3);
  mov(L5, G4);
  restore(O0, 0, G2_thread);
}

static Thread* verify_thread_subroutine(Thread* gthread_value) {
  Thread* correct_value = ThreadLocalStorage::thread();
  guarantee(gthread_value == correct_value, "G2_thread value must be the thread");
  return correct_value;
}

void MacroAssembler::verify_thread() {
  if (VerifyThread) {
    // NOTE: this chops off the heads of the 64-bit O registers.
#ifdef CC_INTERP
    save_frame(0);
#else
    // make sure G2_thread contains the right value
    save_frame_and_mov(0, Lmethod, Lmethod);   // to avoid clobbering O0 (and propagate Lmethod for -Xprof)
    mov(G1, L1);                // avoid clobbering G1
    // G2 saved below
    mov(G3, L3);                // avoid clobbering G3
    mov(G4, L4);                // avoid clobbering G4
    mov(G5_method, L5);         // avoid clobbering G5_method
#endif /* CC_INTERP */
#if defined(COMPILER2) && !defined(_LP64)
    // Save & restore possible 64-bit Long arguments in G-regs
    srlx(G1,32,L0);
    srlx(G4,32,L6);
#endif
    call(CAST_FROM_FN_PTR(address,verify_thread_subroutine), relocInfo::runtime_call_type);
    delayed()->mov(G2_thread, O0);

    mov(L1, G1);                // Restore G1
    // G2 restored below
    mov(L3, G3);                // restore G3
    mov(L4, G4);                // restore G4
    mov(L5, G5_method);         // restore G5_method
#if defined(COMPILER2) && !defined(_LP64)
    // Save & restore possible 64-bit Long arguments in G-regs
    sllx(L0,32,G2);             // Move old high G1 bits high in G2
    srl(G1, 0,G1);              // Clear current high G1 bits
    or3 (G1,G2,G1);             // Recover 64-bit G1
    sllx(L6,32,G2);             // Move old high G4 bits high in G2
    srl(G4, 0,G4);              // Clear current high G4 bits
    or3 (G4,G2,G4);             // Recover 64-bit G4
#endif
    restore(O0, 0, G2_thread);
  }
}


void MacroAssembler::save_thread(const Register thread_cache) {
  verify_thread();
  if (thread_cache->is_valid()) {
    assert(thread_cache->is_local() || thread_cache->is_in(), "bad volatile");
    mov(G2_thread, thread_cache);
  }
  if (VerifyThread) {
    // smash G2_thread, as if the VM were about to anyway
    set(0x67676767, G2_thread);
  }
}


void MacroAssembler::restore_thread(const Register thread_cache) {
  if (thread_cache->is_valid()) {
    assert(thread_cache->is_local() || thread_cache->is_in(), "bad volatile");
    mov(thread_cache, G2_thread);
    verify_thread();
  } else {
    // do it the slow way
    get_thread();
  }
}


// %%% maybe get rid of [re]set_last_Java_frame
void MacroAssembler::set_last_Java_frame(Register last_java_sp, Register last_Java_pc) {
  assert_not_delayed();
  Address flags(G2_thread, JavaThread::frame_anchor_offset() +
                           JavaFrameAnchor::flags_offset());
  Address pc_addr(G2_thread, JavaThread::last_Java_pc_offset());

  // Always set last_Java_pc and flags first because once last_Java_sp is visible
  // has_last_Java_frame is true and users will look at the rest of the fields.
  // (Note: flags should always be zero before we get here so doesn't need to be set.)

#ifdef ASSERT
  // Verify that flags was zeroed on return to Java
  Label PcOk;
  save_frame(0);                // to avoid clobbering O0
  ld_ptr(pc_addr, L0);
  br_null_short(L0, Assembler::pt, PcOk);
  STOP("last_Java_pc not zeroed before leaving Java");
  bind(PcOk);

  // Verify that flags was zeroed on return to Java
  Label FlagsOk;
  ld(flags, L0);
  tst(L0);
  br(Assembler::zero, false, Assembler::pt, FlagsOk);
  delayed() -> restore();
  STOP("flags not zeroed before leaving Java");
  bind(FlagsOk);
#endif /* ASSERT */
  //
  // When returning from calling out from Java mode the frame anchor's last_Java_pc
  // will always be set to NULL. It is set here so that if we are doing a call to
  // native (not VM) that we capture the known pc and don't have to rely on the
  // native call having a standard frame linkage where we can find the pc.

  if (last_Java_pc->is_valid()) {
    st_ptr(last_Java_pc, pc_addr);
  }

#ifdef _LP64
#ifdef ASSERT
  // Make sure that we have an odd stack
  Label StackOk;
  andcc(last_java_sp, 0x01, G0);
  br(Assembler::notZero, false, Assembler::pt, StackOk);
  delayed()->nop();
  STOP("Stack Not Biased in set_last_Java_frame");
  bind(StackOk);
#endif // ASSERT
  assert( last_java_sp != G4_scratch, "bad register usage in set_last_Java_frame");
  add( last_java_sp, STACK_BIAS, G4_scratch );
  st_ptr(G4_scratch, G2_thread, JavaThread::last_Java_sp_offset());
#else
  st_ptr(last_java_sp, G2_thread, JavaThread::last_Java_sp_offset());
#endif // _LP64
}

void MacroAssembler::reset_last_Java_frame(void) {
  assert_not_delayed();

  Address sp_addr(G2_thread, JavaThread::last_Java_sp_offset());
  Address pc_addr(G2_thread, JavaThread::frame_anchor_offset() + JavaFrameAnchor::last_Java_pc_offset());
  Address flags  (G2_thread, JavaThread::frame_anchor_offset() + JavaFrameAnchor::flags_offset());

#ifdef ASSERT
  // check that it WAS previously set
#ifdef CC_INTERP
    save_frame(0);
#else
    save_frame_and_mov(0, Lmethod, Lmethod);     // Propagate Lmethod to helper frame for -Xprof
#endif /* CC_INTERP */
    ld_ptr(sp_addr, L0);
    tst(L0);
    breakpoint_trap(Assembler::zero, Assembler::ptr_cc);
    restore();
#endif // ASSERT

  st_ptr(G0, sp_addr);
  // Always return last_Java_pc to zero
  st_ptr(G0, pc_addr);
  // Always null flags after return to Java
  st(G0, flags);
}


void MacroAssembler::call_VM_base(
  Register        oop_result,
  Register        thread_cache,
  Register        last_java_sp,
  address         entry_point,
  int             number_of_arguments,
  bool            check_exceptions)
{
  assert_not_delayed();

  // determine last_java_sp register
  if (!last_java_sp->is_valid()) {
    last_java_sp = SP;
  }
  // debugging support
  assert(number_of_arguments >= 0   , "cannot have negative number of arguments");

  // 64-bit last_java_sp is biased!
  set_last_Java_frame(last_java_sp, noreg);
  if (VerifyThread)  mov(G2_thread, O0); // about to be smashed; pass early
  save_thread(thread_cache);
  // do the call
  call(entry_point, relocInfo::runtime_call_type);
  if (!VerifyThread)
    delayed()->mov(G2_thread, O0);  // pass thread as first argument
  else
    delayed()->nop();             // (thread already passed)
  restore_thread(thread_cache);
  reset_last_Java_frame();

  // check for pending exceptions. use Gtemp as scratch register.
  if (check_exceptions) {
    check_and_forward_exception(Gtemp);
  }

#ifdef ASSERT
  set(badHeapWordVal, G3);
  set(badHeapWordVal, G4);
  set(badHeapWordVal, G5);
#endif

  // get oop result if there is one and reset the value in the thread
  if (oop_result->is_valid()) {
    get_vm_result(oop_result);
  }
}

void MacroAssembler::check_and_forward_exception(Register scratch_reg)
{
  Label L;

  check_and_handle_popframe(scratch_reg);
  check_and_handle_earlyret(scratch_reg);

  Address exception_addr(G2_thread, Thread::pending_exception_offset());
  ld_ptr(exception_addr, scratch_reg);
  br_null_short(scratch_reg, pt, L);
  // we use O7 linkage so that forward_exception_entry has the issuing PC
  call(StubRoutines::forward_exception_entry(), relocInfo::runtime_call_type);
  delayed()->nop();
  bind(L);
}


void MacroAssembler::check_and_handle_popframe(Register scratch_reg) {
}


void MacroAssembler::check_and_handle_earlyret(Register scratch_reg) {
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, int number_of_arguments, bool check_exceptions) {
  call_VM_base(oop_result, noreg, noreg, entry_point, number_of_arguments, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, bool check_exceptions) {
  // O0 is reserved for the thread
  mov(arg_1, O1);
  call_VM(oop_result, entry_point, 1, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, bool check_exceptions) {
  // O0 is reserved for the thread
  mov(arg_1, O1);
  mov(arg_2, O2); assert(arg_2 != O1, "smashed argument");
  call_VM(oop_result, entry_point, 2, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions) {
  // O0 is reserved for the thread
  mov(arg_1, O1);
  mov(arg_2, O2); assert(arg_2 != O1,                "smashed argument");
  mov(arg_3, O3); assert(arg_3 != O1 && arg_3 != O2, "smashed argument");
  call_VM(oop_result, entry_point, 3, check_exceptions);
}



// Note: The following call_VM overloadings are useful when a "save"
// has already been performed by a stub, and the last Java frame is
// the previous one.  In that case, last_java_sp must be passed as FP
// instead of SP.


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, int number_of_arguments, bool check_exceptions) {
  call_VM_base(oop_result, noreg, last_java_sp, entry_point, number_of_arguments, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, bool check_exceptions) {
  // O0 is reserved for the thread
  mov(arg_1, O1);
  call_VM(oop_result, last_java_sp, entry_point, 1, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, bool check_exceptions) {
  // O0 is reserved for the thread
  mov(arg_1, O1);
  mov(arg_2, O2); assert(arg_2 != O1, "smashed argument");
  call_VM(oop_result, last_java_sp, entry_point, 2, check_exceptions);
}


void MacroAssembler::call_VM(Register oop_result, Register last_java_sp, address entry_point, Register arg_1, Register arg_2, Register arg_3, bool check_exceptions) {
  // O0 is reserved for the thread
  mov(arg_1, O1);
  mov(arg_2, O2); assert(arg_2 != O1,                "smashed argument");
  mov(arg_3, O3); assert(arg_3 != O1 && arg_3 != O2, "smashed argument");
  call_VM(oop_result, last_java_sp, entry_point, 3, check_exceptions);
}



void MacroAssembler::call_VM_leaf_base(Register thread_cache, address entry_point, int number_of_arguments) {
  assert_not_delayed();
  save_thread(thread_cache);
  // do the call
  call(entry_point, relocInfo::runtime_call_type);
  delayed()->nop();
  restore_thread(thread_cache);
#ifdef ASSERT
  set(badHeapWordVal, G3);
  set(badHeapWordVal, G4);
  set(badHeapWordVal, G5);
#endif
}


void MacroAssembler::call_VM_leaf(Register thread_cache, address entry_point, int number_of_arguments) {
  call_VM_leaf_base(thread_cache, entry_point, number_of_arguments);
}


void MacroAssembler::call_VM_leaf(Register thread_cache, address entry_point, Register arg_1) {
  mov(arg_1, O0);
  call_VM_leaf(thread_cache, entry_point, 1);
}


void MacroAssembler::call_VM_leaf(Register thread_cache, address entry_point, Register arg_1, Register arg_2) {
  mov(arg_1, O0);
  mov(arg_2, O1); assert(arg_2 != O0, "smashed argument");
  call_VM_leaf(thread_cache, entry_point, 2);
}


void MacroAssembler::call_VM_leaf(Register thread_cache, address entry_point, Register arg_1, Register arg_2, Register arg_3) {
  mov(arg_1, O0);
  mov(arg_2, O1); assert(arg_2 != O0,                "smashed argument");
  mov(arg_3, O2); assert(arg_3 != O0 && arg_3 != O1, "smashed argument");
  call_VM_leaf(thread_cache, entry_point, 3);
}


void MacroAssembler::get_vm_result(Register oop_result) {
  verify_thread();
  Address vm_result_addr(G2_thread, JavaThread::vm_result_offset());
  ld_ptr(    vm_result_addr, oop_result);
  st_ptr(G0, vm_result_addr);
  verify_oop(oop_result);
}


void MacroAssembler::get_vm_result_2(Register metadata_result) {
  verify_thread();
  Address vm_result_addr_2(G2_thread, JavaThread::vm_result_2_offset());
  ld_ptr(vm_result_addr_2, metadata_result);
  st_ptr(G0, vm_result_addr_2);
}


// We require that C code which does not return a value in vm_result will
// leave it undisturbed.
void MacroAssembler::set_vm_result(Register oop_result) {
  verify_thread();
  Address vm_result_addr(G2_thread, JavaThread::vm_result_offset());
  verify_oop(oop_result);

# ifdef ASSERT
    // Check that we are not overwriting any other oop.
#ifdef CC_INTERP
    save_frame(0);
#else
    save_frame_and_mov(0, Lmethod, Lmethod);     // Propagate Lmethod for -Xprof
#endif /* CC_INTERP */
    ld_ptr(vm_result_addr, L0);
    tst(L0);
    restore();
    breakpoint_trap(notZero, Assembler::ptr_cc);
    // }
# endif

  st_ptr(oop_result, vm_result_addr);
}


void MacroAssembler::ic_call(address entry, bool emit_delay) {
  RelocationHolder rspec = virtual_call_Relocation::spec(pc());
  patchable_set((intptr_t)Universe::non_oop_word(), G5_inline_cache_reg);
  relocate(rspec);
  call(entry, relocInfo::none);
  if (emit_delay) {
    delayed()->nop();
  }
}


void MacroAssembler::card_table_write(jbyte* byte_map_base,
                                      Register tmp, Register obj) {
#ifdef _LP64
  srlx(obj, CardTableModRefBS::card_shift, obj);
#else
  srl(obj, CardTableModRefBS::card_shift, obj);
#endif
  assert(tmp != obj, "need separate temp reg");
  set((address) byte_map_base, tmp);
  stb(G0, tmp, obj);
}


void MacroAssembler::internal_sethi(const AddressLiteral& addrlit, Register d, bool ForceRelocatable) {
  address save_pc;
  int shiftcnt;
#ifdef _LP64
# ifdef CHECK_DELAY
  assert_not_delayed((char*) "cannot put two instructions in delay slot");
# endif
  v9_dep();
  save_pc = pc();

  int msb32 = (int) (addrlit.value() >> 32);
  int lsb32 = (int) (addrlit.value());

  if (msb32 == 0 && lsb32 >= 0) {
    Assembler::sethi(lsb32, d, addrlit.rspec());
  }
  else if (msb32 == -1) {
    Assembler::sethi(~lsb32, d, addrlit.rspec());
    xor3(d, ~low10(~0), d);
  }
  else {
    Assembler::sethi(msb32, d, addrlit.rspec());  // msb 22-bits
    if (msb32 & 0x3ff)                            // Any bits?
      or3(d, msb32 & 0x3ff, d);                   // msb 32-bits are now in lsb 32
    if (lsb32 & 0xFFFFFC00) {                     // done?
      if ((lsb32 >> 20) & 0xfff) {                // Any bits set?
        sllx(d, 12, d);                           // Make room for next 12 bits
        or3(d, (lsb32 >> 20) & 0xfff, d);         // Or in next 12
        shiftcnt = 0;                             // We already shifted
      }
      else
        shiftcnt = 12;
      if ((lsb32 >> 10) & 0x3ff) {
        sllx(d, shiftcnt + 10, d);                // Make room for last 10 bits
        or3(d, (lsb32 >> 10) & 0x3ff, d);         // Or in next 10
        shiftcnt = 0;
      }
      else
        shiftcnt = 10;
      sllx(d, shiftcnt + 10, d);                  // Shift leaving disp field 0'd
    }
    else
      sllx(d, 32, d);
  }
  // Pad out the instruction sequence so it can be patched later.
  if (ForceRelocatable || (addrlit.rtype() != relocInfo::none &&
                           addrlit.rtype() != relocInfo::runtime_call_type)) {
    while (pc() < (save_pc + (7 * BytesPerInstWord)))
      nop();
  }
#else
  Assembler::sethi(addrlit.value(), d, addrlit.rspec());
#endif
}


void MacroAssembler::sethi(const AddressLiteral& addrlit, Register d) {
  internal_sethi(addrlit, d, false);
}


void MacroAssembler::patchable_sethi(const AddressLiteral& addrlit, Register d) {
  internal_sethi(addrlit, d, true);
}


int MacroAssembler::insts_for_sethi(address a, bool worst_case) {
#ifdef _LP64
  if (worst_case)  return 7;
  intptr_t iaddr = (intptr_t) a;
  int msb32 = (int) (iaddr >> 32);
  int lsb32 = (int) (iaddr);
  int count;
  if (msb32 == 0 && lsb32 >= 0)
    count = 1;
  else if (msb32 == -1)
    count = 2;
  else {
    count = 2;
    if (msb32 & 0x3ff)
      count++;
    if (lsb32 & 0xFFFFFC00 ) {
      if ((lsb32 >> 20) & 0xfff)  count += 2;
      if ((lsb32 >> 10) & 0x3ff)  count += 2;
    }
  }
  return count;
#else
  return 1;
#endif
}

int MacroAssembler::worst_case_insts_for_set() {
  return insts_for_sethi(NULL, true) + 1;
}


// Keep in sync with MacroAssembler::insts_for_internal_set
void MacroAssembler::internal_set(const AddressLiteral& addrlit, Register d, bool ForceRelocatable) {
  intptr_t value = addrlit.value();

  if (!ForceRelocatable && addrlit.rspec().type() == relocInfo::none) {
    // can optimize
    if (-4096 <= value && value <= 4095) {
      or3(G0, value, d); // setsw (this leaves upper 32 bits sign-extended)
      return;
    }
    if (inv_hi22(hi22(value)) == value) {
      sethi(addrlit, d);
      return;
    }
  }
  assert_not_delayed((char*) "cannot put two instructions in delay slot");
  internal_sethi(addrlit, d, ForceRelocatable);
  if (ForceRelocatable || addrlit.rspec().type() != relocInfo::none || addrlit.low10() != 0) {
    add(d, addrlit.low10(), d, addrlit.rspec());
  }
}

// Keep in sync with MacroAssembler::internal_set
int MacroAssembler::insts_for_internal_set(intptr_t value) {
  // can optimize
  if (-4096 <= value && value <= 4095) {
    return 1;
  }
  if (inv_hi22(hi22(value)) == value) {
    return insts_for_sethi((address) value);
  }
  int count = insts_for_sethi((address) value);
  AddressLiteral al(value);
  if (al.low10() != 0) {
    count++;
  }
  return count;
}

void MacroAssembler::set(const AddressLiteral& al, Register d) {
  internal_set(al, d, false);
}

void MacroAssembler::set(intptr_t value, Register d) {
  AddressLiteral al(value);
  internal_set(al, d, false);
}

void MacroAssembler::set(address addr, Register d, RelocationHolder const& rspec) {
  AddressLiteral al(addr, rspec);
  internal_set(al, d, false);
}

void MacroAssembler::patchable_set(const AddressLiteral& al, Register d) {
  internal_set(al, d, true);
}

void MacroAssembler::patchable_set(intptr_t value, Register d) {
  AddressLiteral al(value);
  internal_set(al, d, true);
}


void MacroAssembler::set64(jlong value, Register d, Register tmp) {
  assert_not_delayed();
  v9_dep();

  int hi = (int)(value >> 32);
  int lo = (int)(value & ~0);
  // (Matcher::isSimpleConstant64 knows about the following optimizations.)
  if (Assembler::is_simm13(lo) && value == lo) {
    or3(G0, lo, d);
  } else if (hi == 0) {
    Assembler::sethi(lo, d);   // hardware version zero-extends to upper 32
    if (low10(lo) != 0)
      or3(d, low10(lo), d);
  }
  else if (hi == -1) {
    Assembler::sethi(~lo, d);  // hardware version zero-extends to upper 32
    xor3(d, low10(lo) ^ ~low10(~0), d);
  }
  else if (lo == 0) {
    if (Assembler::is_simm13(hi)) {
      or3(G0, hi, d);
    } else {
      Assembler::sethi(hi, d);   // hardware version zero-extends to upper 32
      if (low10(hi) != 0)
        or3(d, low10(hi), d);
    }
    sllx(d, 32, d);
  }
  else {
    Assembler::sethi(hi, tmp);
    Assembler::sethi(lo,   d); // macro assembler version sign-extends
    if (low10(hi) != 0)
      or3 (tmp, low10(hi), tmp);
    if (low10(lo) != 0)
      or3 (  d, low10(lo),   d);
    sllx(tmp, 32, tmp);
    or3 (d, tmp, d);
  }
}

int MacroAssembler::insts_for_set64(jlong value) {
  v9_dep();

  int hi = (int) (value >> 32);
  int lo = (int) (value & ~0);
  int count = 0;

  // (Matcher::isSimpleConstant64 knows about the following optimizations.)
  if (Assembler::is_simm13(lo) && value == lo) {
    count++;
  } else if (hi == 0) {
    count++;
    if (low10(lo) != 0)
      count++;
  }
  else if (hi == -1) {
    count += 2;
  }
  else if (lo == 0) {
    if (Assembler::is_simm13(hi)) {
      count++;
    } else {
      count++;
      if (low10(hi) != 0)
        count++;
    }
    count++;
  }
  else {
    count += 2;
    if (low10(hi) != 0)
      count++;
    if (low10(lo) != 0)
      count++;
    count += 2;
  }
  return count;
}

// compute size in bytes of sparc frame, given
// number of extraWords
int MacroAssembler::total_frame_size_in_bytes(int extraWords) {

  int nWords = frame::memory_parameter_word_sp_offset;

  nWords += extraWords;

  if (nWords & 1) ++nWords; // round up to double-word

  return nWords * BytesPerWord;
}


// save_frame: given number of "extra" words in frame,
// issue approp. save instruction (p 200, v8 manual)

void MacroAssembler::save_frame(int extraWords) {
  int delta = -total_frame_size_in_bytes(extraWords);
  if (is_simm13(delta)) {
    save(SP, delta, SP);
  } else {
    set(delta, G3_scratch);
    save(SP, G3_scratch, SP);
  }
}


void MacroAssembler::save_frame_c1(int size_in_bytes) {
  if (is_simm13(-size_in_bytes)) {
    save(SP, -size_in_bytes, SP);
  } else {
    set(-size_in_bytes, G3_scratch);
    save(SP, G3_scratch, SP);
  }
}


void MacroAssembler::save_frame_and_mov(int extraWords,
                                        Register s1, Register d1,
                                        Register s2, Register d2) {
  assert_not_delayed();

  // The trick here is to use precisely the same memory word
  // that trap handlers also use to save the register.
  // This word cannot be used for any other purpose, but
  // it works fine to save the register's value, whether or not
  // an interrupt flushes register windows at any given moment!
  Address s1_addr;
  if (s1->is_valid() && (s1->is_in() || s1->is_local())) {
    s1_addr = s1->address_in_saved_window();
    st_ptr(s1, s1_addr);
  }

  Address s2_addr;
  if (s2->is_valid() && (s2->is_in() || s2->is_local())) {
    s2_addr = s2->address_in_saved_window();
    st_ptr(s2, s2_addr);
  }

  save_frame(extraWords);

  if (s1_addr.base() == SP) {
    ld_ptr(s1_addr.after_save(), d1);
  } else if (s1->is_valid()) {
    mov(s1->after_save(), d1);
  }

  if (s2_addr.base() == SP) {
    ld_ptr(s2_addr.after_save(), d2);
  } else if (s2->is_valid()) {
    mov(s2->after_save(), d2);
  }
}


AddressLiteral MacroAssembler::allocate_metadata_address(Metadata* obj) {
  assert(oop_recorder() != NULL, "this assembler needs a Recorder");
  int index = oop_recorder()->allocate_metadata_index(obj);
  RelocationHolder rspec = metadata_Relocation::spec(index);
  return AddressLiteral((address)obj, rspec);
}

AddressLiteral MacroAssembler::constant_metadata_address(Metadata* obj) {
  assert(oop_recorder() != NULL, "this assembler needs a Recorder");
  int index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = metadata_Relocation::spec(index);
  return AddressLiteral((address)obj, rspec);
}


AddressLiteral MacroAssembler::constant_oop_address(jobject obj) {
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  assert(Universe::heap()->is_in_reserved(JNIHandles::resolve(obj)), "not an oop");
  int oop_index = oop_recorder()->find_index(obj);
  return AddressLiteral(obj, oop_Relocation::spec(oop_index));
}

void  MacroAssembler::set_narrow_oop(jobject obj, Register d) {
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int oop_index = oop_recorder()->find_index(obj);
  RelocationHolder rspec = oop_Relocation::spec(oop_index);

  assert_not_delayed();
  // Relocation with special format (see relocInfo_sparc.hpp).
  relocate(rspec, 1);
  // Assembler::sethi(0x3fffff, d);
  emit_int32( op(branch_op) | rd(d) | op2(sethi_op2) | hi22(0x3fffff) );
  // Don't add relocation for 'add'. Do patching during 'sethi' processing.
  add(d, 0x3ff, d);

}

void  MacroAssembler::set_narrow_klass(Klass* k, Register d) {
  assert(oop_recorder() != NULL, "this assembler needs an OopRecorder");
  int klass_index = oop_recorder()->find_index(k);
  RelocationHolder rspec = metadata_Relocation::spec(klass_index);
  narrowOop encoded_k = Klass::encode_klass(k);

  assert_not_delayed();
  // Relocation with special format (see relocInfo_sparc.hpp).
  relocate(rspec, 1);
  // Assembler::sethi(encoded_k, d);
  emit_int32( op(branch_op) | rd(d) | op2(sethi_op2) | hi22(encoded_k) );
  // Don't add relocation for 'add'. Do patching during 'sethi' processing.
  add(d, low10(encoded_k), d);

}

void MacroAssembler::align(int modulus) {
  while (offset() % modulus != 0) nop();
}

void RegistersForDebugging::print(outputStream* s) {
  FlagSetting fs(Debugging, true);
  int j;
  for (j = 0; j < 8; ++j) {
    if (j != 6) { s->print("i%d = ", j); os::print_location(s, i[j]); }
    else        { s->print( "fp = "   ); os::print_location(s, i[j]); }
  }
  s->cr();

  for (j = 0;  j < 8;  ++j) {
    s->print("l%d = ", j); os::print_location(s, l[j]);
  }
  s->cr();

  for (j = 0; j < 8; ++j) {
    if (j != 6) { s->print("o%d = ", j); os::print_location(s, o[j]); }
    else        { s->print( "sp = "   ); os::print_location(s, o[j]); }
  }
  s->cr();

  for (j = 0; j < 8; ++j) {
    s->print("g%d = ", j); os::print_location(s, g[j]);
  }
  s->cr();

  // print out floats with compression
  for (j = 0; j < 32; ) {
    jfloat val = f[j];
    int last = j;
    for ( ;  last+1 < 32;  ++last ) {
      char b1[1024], b2[1024];
      sprintf(b1, "%f", val);
      sprintf(b2, "%f", f[last+1]);
      if (strcmp(b1, b2))
        break;
    }
    s->print("f%d", j);
    if ( j != last )  s->print(" - f%d", last);
    s->print(" = %f", val);
    s->fill_to(25);
    s->print_cr(" (0x%x)", val);
    j = last + 1;
  }
  s->cr();

  // and doubles (evens only)
  for (j = 0; j < 32; ) {
    jdouble val = d[j];
    int last = j;
    for ( ;  last+1 < 32;  ++last ) {
      char b1[1024], b2[1024];
      sprintf(b1, "%f", val);
      sprintf(b2, "%f", d[last+1]);
      if (strcmp(b1, b2))
        break;
    }
    s->print("d%d", 2 * j);
    if ( j != last )  s->print(" - d%d", last);
    s->print(" = %f", val);
    s->fill_to(30);
    s->print("(0x%x)", *(int*)&val);
    s->fill_to(42);
    s->print_cr("(0x%x)", *(1 + (int*)&val));
    j = last + 1;
  }
  s->cr();
}

void RegistersForDebugging::save_registers(MacroAssembler* a) {
  a->sub(FP, round_to(sizeof(RegistersForDebugging), sizeof(jdouble)) - STACK_BIAS, O0);
  a->flushw();
  int i;
  for (i = 0; i < 8; ++i) {
    a->ld_ptr(as_iRegister(i)->address_in_saved_window().after_save(), L1);  a->st_ptr( L1, O0, i_offset(i));
    a->ld_ptr(as_lRegister(i)->address_in_saved_window().after_save(), L1);  a->st_ptr( L1, O0, l_offset(i));
    a->st_ptr(as_oRegister(i)->after_save(), O0, o_offset(i));
    a->st_ptr(as_gRegister(i)->after_save(), O0, g_offset(i));
  }
  for (i = 0;  i < 32; ++i) {
    a->stf(FloatRegisterImpl::S, as_FloatRegister(i), O0, f_offset(i));
  }
  for (i = 0; i < 64; i += 2) {
    a->stf(FloatRegisterImpl::D, as_FloatRegister(i), O0, d_offset(i));
  }
}

void RegistersForDebugging::restore_registers(MacroAssembler* a, Register r) {
  for (int i = 1; i < 8;  ++i) {
    a->ld_ptr(r, g_offset(i), as_gRegister(i));
  }
  for (int j = 0; j < 32; ++j) {
    a->ldf(FloatRegisterImpl::S, O0, f_offset(j), as_FloatRegister(j));
  }
  for (int k = 0; k < 64; k += 2) {
    a->ldf(FloatRegisterImpl::D, O0, d_offset(k), as_FloatRegister(k));
  }
}


// pushes double TOS element of FPU stack on CPU stack; pops from FPU stack
void MacroAssembler::push_fTOS() {
  // %%%%%% need to implement this
}

// pops double TOS element from CPU stack and pushes on FPU stack
void MacroAssembler::pop_fTOS() {
  // %%%%%% need to implement this
}

void MacroAssembler::empty_FPU_stack() {
  // %%%%%% need to implement this
}

void MacroAssembler::_verify_oop(Register reg, const char* msg, const char * file, int line) {
  // plausibility check for oops
  if (!VerifyOops) return;

  if (reg == G0)  return;       // always NULL, which is always an oop

  BLOCK_COMMENT("verify_oop {");
  char buffer[64];
#ifdef COMPILER1
  if (CommentedAssembly) {
    snprintf(buffer, sizeof(buffer), "verify_oop at %d", offset());
    block_comment(buffer);
  }
#endif

  const char* real_msg = NULL;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("%s at offset %d (%s:%d)", msg, offset(), file, line);
    real_msg = code_string(ss.as_string());
  }

  // Call indirectly to solve generation ordering problem
  AddressLiteral a(StubRoutines::verify_oop_subroutine_entry_address());

  // Make some space on stack above the current register window.
  // Enough to hold 8 64-bit registers.
  add(SP,-8*8,SP);

  // Save some 64-bit registers; a normal 'save' chops the heads off
  // of 64-bit longs in the 32-bit build.
  stx(O0,SP,frame::register_save_words*wordSize+STACK_BIAS+0*8);
  stx(O1,SP,frame::register_save_words*wordSize+STACK_BIAS+1*8);
  mov(reg,O0); // Move arg into O0; arg might be in O7 which is about to be crushed
  stx(O7,SP,frame::register_save_words*wordSize+STACK_BIAS+7*8);

  // Size of set() should stay the same
  patchable_set((intptr_t)real_msg, O1);
  // Load address to call to into O7
  load_ptr_contents(a, O7);
  // Register call to verify_oop_subroutine
  callr(O7, G0);
  delayed()->nop();
  // recover frame size
  add(SP, 8*8,SP);
  BLOCK_COMMENT("} verify_oop");
}

void MacroAssembler::_verify_oop_addr(Address addr, const char* msg, const char * file, int line) {
  // plausibility check for oops
  if (!VerifyOops) return;

  const char* real_msg = NULL;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("%s at SP+%d (%s:%d)", msg, addr.disp(), file, line);
    real_msg = code_string(ss.as_string());
  }

  // Call indirectly to solve generation ordering problem
  AddressLiteral a(StubRoutines::verify_oop_subroutine_entry_address());

  // Make some space on stack above the current register window.
  // Enough to hold 8 64-bit registers.
  add(SP,-8*8,SP);

  // Save some 64-bit registers; a normal 'save' chops the heads off
  // of 64-bit longs in the 32-bit build.
  stx(O0,SP,frame::register_save_words*wordSize+STACK_BIAS+0*8);
  stx(O1,SP,frame::register_save_words*wordSize+STACK_BIAS+1*8);
  ld_ptr(addr.base(), addr.disp() + 8*8, O0); // Load arg into O0; arg might be in O7 which is about to be crushed
  stx(O7,SP,frame::register_save_words*wordSize+STACK_BIAS+7*8);

  // Size of set() should stay the same
  patchable_set((intptr_t)real_msg, O1);
  // Load address to call to into O7
  load_ptr_contents(a, O7);
  // Register call to verify_oop_subroutine
  callr(O7, G0);
  delayed()->nop();
  // recover frame size
  add(SP, 8*8,SP);
}

// side-door communication with signalHandler in os_solaris.cpp
address MacroAssembler::_verify_oop_implicit_branch[3] = { NULL };

// This macro is expanded just once; it creates shared code.  Contract:
// receives an oop in O0.  Must restore O0 & O7 from TLS.  Must not smash ANY
// registers, including flags.  May not use a register 'save', as this blows
// the high bits of the O-regs if they contain Long values.  Acts as a 'leaf'
// call.
void MacroAssembler::verify_oop_subroutine() {
  // Leaf call; no frame.
  Label succeed, fail, null_or_fail;

  // O0 and O7 were saved already (O0 in O0's TLS home, O7 in O5's TLS home).
  // O0 is now the oop to be checked.  O7 is the return address.
  Register O0_obj = O0;

  // Save some more registers for temps.
  stx(O2,SP,frame::register_save_words*wordSize+STACK_BIAS+2*8);
  stx(O3,SP,frame::register_save_words*wordSize+STACK_BIAS+3*8);
  stx(O4,SP,frame::register_save_words*wordSize+STACK_BIAS+4*8);
  stx(O5,SP,frame::register_save_words*wordSize+STACK_BIAS+5*8);

  // Save flags
  Register O5_save_flags = O5;
  rdccr( O5_save_flags );

  { // count number of verifies
    Register O2_adr   = O2;
    Register O3_accum = O3;
    inc_counter(StubRoutines::verify_oop_count_addr(), O2_adr, O3_accum);
  }

  Register O2_mask = O2;
  Register O3_bits = O3;
  Register O4_temp = O4;

  // mark lower end of faulting range
  assert(_verify_oop_implicit_branch[0] == NULL, "set once");
  _verify_oop_implicit_branch[0] = pc();

  // We can't check the mark oop because it could be in the process of
  // locking or unlocking while this is running.
  set(Universe::verify_oop_mask (), O2_mask);
  set(Universe::verify_oop_bits (), O3_bits);

  // assert((obj & oop_mask) == oop_bits);
  and3(O0_obj, O2_mask, O4_temp);
  cmp_and_brx_short(O4_temp, O3_bits, notEqual, pn, null_or_fail);

  if ((NULL_WORD & Universe::verify_oop_mask()) == Universe::verify_oop_bits()) {
    // the null_or_fail case is useless; must test for null separately
    br_null_short(O0_obj, pn, succeed);
  }

  // Check the Klass* of this object for being in the right area of memory.
  // Cannot do the load in the delay above slot in case O0 is null
  load_klass(O0_obj, O0_obj);
  // assert((klass != NULL)
  br_null_short(O0_obj, pn, fail);

  wrccr( O5_save_flags ); // Restore CCR's

  // mark upper end of faulting range
  _verify_oop_implicit_branch[1] = pc();

  //-----------------------
  // all tests pass
  bind(succeed);

  // Restore prior 64-bit registers
  ldx(SP,frame::register_save_words*wordSize+STACK_BIAS+0*8,O0);
  ldx(SP,frame::register_save_words*wordSize+STACK_BIAS+1*8,O1);
  ldx(SP,frame::register_save_words*wordSize+STACK_BIAS+2*8,O2);
  ldx(SP,frame::register_save_words*wordSize+STACK_BIAS+3*8,O3);
  ldx(SP,frame::register_save_words*wordSize+STACK_BIAS+4*8,O4);
  ldx(SP,frame::register_save_words*wordSize+STACK_BIAS+5*8,O5);

  retl();                       // Leaf return; restore prior O7 in delay slot
  delayed()->ldx(SP,frame::register_save_words*wordSize+STACK_BIAS+7*8,O7);

  //-----------------------
  bind(null_or_fail);           // nulls are less common but OK
  br_null(O0_obj, false, pt, succeed);
  delayed()->wrccr( O5_save_flags ); // Restore CCR's

  //-----------------------
  // report failure:
  bind(fail);
  _verify_oop_implicit_branch[2] = pc();

  wrccr( O5_save_flags ); // Restore CCR's

  save_frame(::round_to(sizeof(RegistersForDebugging) / BytesPerWord, 2));

  // stop_subroutine expects message pointer in I1.
  mov(I1, O1);

  // Restore prior 64-bit registers
  ldx(FP,frame::register_save_words*wordSize+STACK_BIAS+0*8,I0);
  ldx(FP,frame::register_save_words*wordSize+STACK_BIAS+1*8,I1);
  ldx(FP,frame::register_save_words*wordSize+STACK_BIAS+2*8,I2);
  ldx(FP,frame::register_save_words*wordSize+STACK_BIAS+3*8,I3);
  ldx(FP,frame::register_save_words*wordSize+STACK_BIAS+4*8,I4);
  ldx(FP,frame::register_save_words*wordSize+STACK_BIAS+5*8,I5);

  // factor long stop-sequence into subroutine to save space
  assert(StubRoutines::Sparc::stop_subroutine_entry_address(), "hasn't been generated yet");

  // call indirectly to solve generation ordering problem
  AddressLiteral al(StubRoutines::Sparc::stop_subroutine_entry_address());
  load_ptr_contents(al, O5);
  jmpl(O5, 0, O7);
  delayed()->nop();
}


void MacroAssembler::stop(const char* msg) {
  // save frame first to get O7 for return address
  // add one word to size in case struct is odd number of words long
  // It must be doubleword-aligned for storing doubles into it.

    save_frame(::round_to(sizeof(RegistersForDebugging) / BytesPerWord, 2));

    // stop_subroutine expects message pointer in I1.
    // Size of set() should stay the same
    patchable_set((intptr_t)msg, O1);

    // factor long stop-sequence into subroutine to save space
    assert(StubRoutines::Sparc::stop_subroutine_entry_address(), "hasn't been generated yet");

    // call indirectly to solve generation ordering problem
    AddressLiteral a(StubRoutines::Sparc::stop_subroutine_entry_address());
    load_ptr_contents(a, O5);
    jmpl(O5, 0, O7);
    delayed()->nop();

    breakpoint_trap();   // make stop actually stop rather than writing
                         // unnoticeable results in the output files.

    // restore(); done in callee to save space!
}


void MacroAssembler::warn(const char* msg) {
  save_frame(::round_to(sizeof(RegistersForDebugging) / BytesPerWord, 2));
  RegistersForDebugging::save_registers(this);
  mov(O0, L0);
  // Size of set() should stay the same
  patchable_set((intptr_t)msg, O0);
  call( CAST_FROM_FN_PTR(address, warning) );
  delayed()->nop();
//  ret();
//  delayed()->restore();
  RegistersForDebugging::restore_registers(this, L0);
  restore();
}


void MacroAssembler::untested(const char* what) {
  // We must be able to turn interactive prompting off
  // in order to run automated test scripts on the VM
  // Use the flag ShowMessageBoxOnError

  const char* b = NULL;
  {
    ResourceMark rm;
    stringStream ss;
    ss.print("untested: %s", what);
    b = code_string(ss.as_string());
  }
  if (ShowMessageBoxOnError) { STOP(b); }
  else                       { warn(b); }
}


void MacroAssembler::stop_subroutine() {
  RegistersForDebugging::save_registers(this);

  // for the sake of the debugger, stick a PC on the current frame
  // (this assumes that the caller has performed an extra "save")
  mov(I7, L7);
  add(O7, -7 * BytesPerInt, I7);

  save_frame(); // one more save to free up another O7 register
  mov(I0, O1); // addr of reg save area

  // We expect pointer to message in I1. Caller must set it up in O1
  mov(I1, O0); // get msg
  call (CAST_FROM_FN_PTR(address, MacroAssembler::debug), relocInfo::runtime_call_type);
  delayed()->nop();

  restore();

  RegistersForDebugging::restore_registers(this, O0);

  save_frame(0);
  call(CAST_FROM_FN_PTR(address,breakpoint));
  delayed()->nop();
  restore();

  mov(L7, I7);
  retl();
  delayed()->restore(); // see stop above
}


void MacroAssembler::debug(char* msg, RegistersForDebugging* regs) {
  if ( ShowMessageBoxOnError ) {
    JavaThread* thread = JavaThread::current();
    JavaThreadState saved_state = thread->thread_state();
    thread->set_thread_state(_thread_in_vm);
      {
        // In order to get locks work, we need to fake a in_VM state
        ttyLocker ttyl;
        ::tty->print_cr("EXECUTION STOPPED: %s\n", msg);
        if (CountBytecodes || TraceBytecodes || StopInterpreterAt) {
        BytecodeCounter::print();
        }
        if (os::message_box(msg, "Execution stopped, print registers?"))
          regs->print(::tty);
      }
    BREAKPOINT;
      ThreadStateTransition::transition(JavaThread::current(), _thread_in_vm, saved_state);
  }
  else {
     ::tty->print_cr("=============== DEBUG MESSAGE: %s ================\n", msg);
  }
  assert(false, err_msg("DEBUG MESSAGE: %s", msg));
}


void MacroAssembler::calc_mem_param_words(Register Rparam_words, Register Rresult) {
  subcc( Rparam_words, Argument::n_register_parameters, Rresult); // how many mem words?
  Label no_extras;
  br( negative, true, pt, no_extras ); // if neg, clear reg
  delayed()->set(0, Rresult);          // annuled, so only if taken
  bind( no_extras );
}


void MacroAssembler::calc_frame_size(Register Rextra_words, Register Rresult) {
#ifdef _LP64
  add(Rextra_words, frame::memory_parameter_word_sp_offset, Rresult);
#else
  add(Rextra_words, frame::memory_parameter_word_sp_offset + 1, Rresult);
#endif
  bclr(1, Rresult);
  sll(Rresult, LogBytesPerWord, Rresult);  // Rresult has total frame bytes
}


void MacroAssembler::calc_frame_size_and_save(Register Rextra_words, Register Rresult) {
  calc_frame_size(Rextra_words, Rresult);
  neg(Rresult);
  save(SP, Rresult, SP);
}


// ---------------------------------------------------------
Assembler::RCondition cond2rcond(Assembler::Condition c) {
  switch (c) {
    /*case zero: */
    case Assembler::equal:        return Assembler::rc_z;
    case Assembler::lessEqual:    return Assembler::rc_lez;
    case Assembler::less:         return Assembler::rc_lz;
    /*case notZero:*/
    case Assembler::notEqual:     return Assembler::rc_nz;
    case Assembler::greater:      return Assembler::rc_gz;
    case Assembler::greaterEqual: return Assembler::rc_gez;
  }
  ShouldNotReachHere();
  return Assembler::rc_z;
}

// compares (32 bit) register with zero and branches.  NOT FOR USE WITH 64-bit POINTERS
void MacroAssembler::cmp_zero_and_br(Condition c, Register s1, Label& L, bool a, Predict p) {
  tst(s1);
  br (c, a, p, L);
}

// Compares a pointer register with zero and branches on null.
// Does a test & branch on 32-bit systems and a register-branch on 64-bit.
void MacroAssembler::br_null( Register s1, bool a, Predict p, Label& L ) {
  assert_not_delayed();
#ifdef _LP64
  bpr( rc_z, a, p, s1, L );
#else
  tst(s1);
  br ( zero, a, p, L );
#endif
}

void MacroAssembler::br_notnull( Register s1, bool a, Predict p, Label& L ) {
  assert_not_delayed();
#ifdef _LP64
  bpr( rc_nz, a, p, s1, L );
#else
  tst(s1);
  br ( notZero, a, p, L );
#endif
}

// Compare registers and branch with nop in delay slot or cbcond without delay slot.

// Compare integer (32 bit) values (icc only).
void MacroAssembler::cmp_and_br_short(Register s1, Register s2, Condition c,
                                      Predict p, Label& L) {
  assert_not_delayed();
  if (use_cbcond(L)) {
    Assembler::cbcond(c, icc, s1, s2, L);
  } else {
    cmp(s1, s2);
    br(c, false, p, L);
    delayed()->nop();
  }
}

// Compare integer (32 bit) values (icc only).
void MacroAssembler::cmp_and_br_short(Register s1, int simm13a, Condition c,
                                      Predict p, Label& L) {
  assert_not_delayed();
  if (is_simm(simm13a,5) && use_cbcond(L)) {
    Assembler::cbcond(c, icc, s1, simm13a, L);
  } else {
    cmp(s1, simm13a);
    br(c, false, p, L);
    delayed()->nop();
  }
}

// Branch that tests xcc in LP64 and icc in !LP64
void MacroAssembler::cmp_and_brx_short(Register s1, Register s2, Condition c,
                                       Predict p, Label& L) {
  assert_not_delayed();
  if (use_cbcond(L)) {
    Assembler::cbcond(c, ptr_cc, s1, s2, L);
  } else {
    cmp(s1, s2);
    brx(c, false, p, L);
    delayed()->nop();
  }
}

// Branch that tests xcc in LP64 and icc in !LP64
void MacroAssembler::cmp_and_brx_short(Register s1, int simm13a, Condition c,
                                       Predict p, Label& L) {
  assert_not_delayed();
  if (is_simm(simm13a,5) && use_cbcond(L)) {
    Assembler::cbcond(c, ptr_cc, s1, simm13a, L);
  } else {
    cmp(s1, simm13a);
    brx(c, false, p, L);
    delayed()->nop();
  }
}

// Short branch version for compares a pointer with zero.

void MacroAssembler::br_null_short(Register s1, Predict p, Label& L) {
  assert_not_delayed();
  if (use_cbcond(L)) {
    Assembler::cbcond(zero, ptr_cc, s1, 0, L);
    return;
  }
  br_null(s1, false, p, L);
  delayed()->nop();
}

void MacroAssembler::br_notnull_short(Register s1, Predict p, Label& L) {
  assert_not_delayed();
  if (use_cbcond(L)) {
    Assembler::cbcond(notZero, ptr_cc, s1, 0, L);
    return;
  }
  br_notnull(s1, false, p, L);
  delayed()->nop();
}

// Unconditional short branch
void MacroAssembler::ba_short(Label& L) {
  if (use_cbcond(L)) {
    Assembler::cbcond(equal, icc, G0, G0, L);
    return;
  }
  br(always, false, pt, L);
  delayed()->nop();
}

// instruction sequences factored across compiler & interpreter


void MacroAssembler::lcmp( Register Ra_hi, Register Ra_low,
                           Register Rb_hi, Register Rb_low,
                           Register Rresult) {

  Label check_low_parts, done;

  cmp(Ra_hi, Rb_hi );  // compare hi parts
  br(equal, true, pt, check_low_parts);
  delayed()->cmp(Ra_low, Rb_low); // test low parts

  // And, with an unsigned comparison, it does not matter if the numbers
  // are negative or not.
  // E.g., -2 cmp -1: the low parts are 0xfffffffe and 0xffffffff.
  // The second one is bigger (unsignedly).

  // Other notes:  The first move in each triplet can be unconditional
  // (and therefore probably prefetchable).
  // And the equals case for the high part does not need testing,
  // since that triplet is reached only after finding the high halves differ.

  mov(-1, Rresult);
  ba(done);
  delayed()->movcc(greater, false, icc,  1, Rresult);

  bind(check_low_parts);

  mov(                               -1, Rresult);
  movcc(equal,           false, icc,  0, Rresult);
  movcc(greaterUnsigned, false, icc,  1, Rresult);

  bind(done);
}

void MacroAssembler::lneg( Register Rhi, Register Rlow ) {
  subcc(  G0, Rlow, Rlow );
  subc(   G0, Rhi,  Rhi  );
}

void MacroAssembler::lshl( Register Rin_high,  Register Rin_low,
                           Register Rcount,
                           Register Rout_high, Register Rout_low,
                           Register Rtemp ) {


  Register Ralt_count = Rtemp;
  Register Rxfer_bits = Rtemp;

  assert( Ralt_count != Rin_high
      &&  Ralt_count != Rin_low
      &&  Ralt_count != Rcount
      &&  Rxfer_bits != Rin_low
      &&  Rxfer_bits != Rin_high
      &&  Rxfer_bits != Rcount
      &&  Rxfer_bits != Rout_low
      &&  Rout_low   != Rin_high,
        "register alias checks");

  Label big_shift, done;

  // This code can be optimized to use the 64 bit shifts in V9.
  // Here we use the 32 bit shifts.

  and3( Rcount, 0x3f, Rcount);     // take least significant 6 bits
  subcc(Rcount,   31, Ralt_count);
  br(greater, true, pn, big_shift);
  delayed()->dec(Ralt_count);

  // shift < 32 bits, Ralt_count = Rcount-31

  // We get the transfer bits by shifting right by 32-count the low
  // register. This is done by shifting right by 31-count and then by one
  // more to take care of the special (rare) case where count is zero
  // (shifting by 32 would not work).

  neg(Ralt_count);

  // The order of the next two instructions is critical in the case where
  // Rin and Rout are the same and should not be reversed.

  srl(Rin_low, Ralt_count, Rxfer_bits); // shift right by 31-count
  if (Rcount != Rout_low) {
    sll(Rin_low, Rcount, Rout_low); // low half
  }
  sll(Rin_high, Rcount, Rout_high);
  if (Rcount == Rout_low) {
    sll(Rin_low, Rcount, Rout_low); // low half
  }
  srl(Rxfer_bits, 1, Rxfer_bits ); // shift right by one more
  ba(done);
  delayed()->or3(Rout_high, Rxfer_bits, Rout_high);   // new hi value: or in shifted old hi part and xfer from low

  // shift >= 32 bits, Ralt_count = Rcount-32
  bind(big_shift);
  sll(Rin_low, Ralt_count, Rout_high  );
  clr(Rout_low);

  bind(done);
}


void MacroAssembler::lshr( Register Rin_high,  Register Rin_low,
                           Register Rcount,
                           Register Rout_high, Register Rout_low,
                           Register Rtemp ) {

  Register Ralt_count = Rtemp;
  Register Rxfer_bits = Rtemp;

  assert( Ralt_count != Rin_high
      &&  Ralt_count != Rin_low
      &&  Ralt_count != Rcount
      &&  Rxfer_bits != Rin_low
      &&  Rxfer_bits != Rin_high
      &&  Rxfer_bits != Rcount
      &&  Rxfer_bits != Rout_high
      &&  Rout_high  != Rin_low,
        "register alias checks");

  Label big_shift, done;

  // This code can be optimized to use the 64 bit shifts in V9.
  // Here we use the 32 bit shifts.

  and3( Rcount, 0x3f, Rcount);     // take least significant 6 bits
  subcc(Rcount,   31, Ralt_count);
  br(greater, true, pn, big_shift);
  delayed()->dec(Ralt_count);

  // shift < 32 bits, Ralt_count = Rcount-31

  // We get the transfer bits by shifting left by 32-count the high
  // register. This is done by shifting left by 31-count and then by one
  // more to take care of the special (rare) case where count is zero
  // (shifting by 32 would not work).

  neg(Ralt_count);
  if (Rcount != Rout_low) {
    srl(Rin_low, Rcount, Rout_low);
  }

  // The order of the next two instructions is critical in the case where
  // Rin and Rout are the same and should not be reversed.

  sll(Rin_high, Ralt_count, Rxfer_bits); // shift left by 31-count
  sra(Rin_high,     Rcount, Rout_high ); // high half
  sll(Rxfer_bits,        1, Rxfer_bits); // shift left by one more
  if (Rcount == Rout_low) {
    srl(Rin_low, Rcount, Rout_low);
  }
  ba(done);
  delayed()->or3(Rout_low, Rxfer_bits, Rout_low); // new low value: or shifted old low part and xfer from high

  // shift >= 32 bits, Ralt_count = Rcount-32
  bind(big_shift);

  sra(Rin_high, Ralt_count, Rout_low);
  sra(Rin_high,         31, Rout_high); // sign into hi

  bind( done );
}



void MacroAssembler::lushr( Register Rin_high,  Register Rin_low,
                            Register Rcount,
                            Register Rout_high, Register Rout_low,
                            Register Rtemp ) {

  Register Ralt_count = Rtemp;
  Register Rxfer_bits = Rtemp;

  assert( Ralt_count != Rin_high
      &&  Ralt_count != Rin_low
      &&  Ralt_count != Rcount
      &&  Rxfer_bits != Rin_low
      &&  Rxfer_bits != Rin_high
      &&  Rxfer_bits != Rcount
      &&  Rxfer_bits != Rout_high
      &&  Rout_high  != Rin_low,
        "register alias checks");

  Label big_shift, done;

  // This code can be optimized to use the 64 bit shifts in V9.
  // Here we use the 32 bit shifts.

  and3( Rcount, 0x3f, Rcount);     // take least significant 6 bits
  subcc(Rcount,   31, Ralt_count);
  br(greater, true, pn, big_shift);
  delayed()->dec(Ralt_count);

  // shift < 32 bits, Ralt_count = Rcount-31

  // We get the transfer bits by shifting left by 32-count the high
  // register. This is done by shifting left by 31-count and then by one
  // more to take care of the special (rare) case where count is zero
  // (shifting by 32 would not work).

  neg(Ralt_count);
  if (Rcount != Rout_low) {
    srl(Rin_low, Rcount, Rout_low);
  }

  // The order of the next two instructions is critical in the case where
  // Rin and Rout are the same and should not be reversed.

  sll(Rin_high, Ralt_count, Rxfer_bits); // shift left by 31-count
  srl(Rin_high,     Rcount, Rout_high ); // high half
  sll(Rxfer_bits,        1, Rxfer_bits); // shift left by one more
  if (Rcount == Rout_low) {
    srl(Rin_low, Rcount, Rout_low);
  }
  ba(done);
  delayed()->or3(Rout_low, Rxfer_bits, Rout_low); // new low value: or shifted old low part and xfer from high

  // shift >= 32 bits, Ralt_count = Rcount-32
  bind(big_shift);

  srl(Rin_high, Ralt_count, Rout_low);
  clr(Rout_high);

  bind( done );
}

#ifdef _LP64
void MacroAssembler::lcmp( Register Ra, Register Rb, Register Rresult) {
  cmp(Ra, Rb);
  mov(-1, Rresult);
  movcc(equal,   false, xcc,  0, Rresult);
  movcc(greater, false, xcc,  1, Rresult);
}
#endif


void MacroAssembler::load_sized_value(Address src, Register dst, size_t size_in_bytes, bool is_signed) {
  switch (size_in_bytes) {
  case  8:  ld_long(src, dst); break;
  case  4:  ld(     src, dst); break;
  case  2:  is_signed ? ldsh(src, dst) : lduh(src, dst); break;
  case  1:  is_signed ? ldsb(src, dst) : ldub(src, dst); break;
  default:  ShouldNotReachHere();
  }
}

void MacroAssembler::store_sized_value(Register src, Address dst, size_t size_in_bytes) {
  switch (size_in_bytes) {
  case  8:  st_long(src, dst); break;
  case  4:  st(     src, dst); break;
  case  2:  sth(    src, dst); break;
  case  1:  stb(    src, dst); break;
  default:  ShouldNotReachHere();
  }
}


void MacroAssembler::float_cmp( bool is_float, int unordered_result,
                                FloatRegister Fa, FloatRegister Fb,
                                Register Rresult) {
  if (is_float) {
    fcmp(FloatRegisterImpl::S, fcc0, Fa, Fb);
  } else {
    fcmp(FloatRegisterImpl::D, fcc0, Fa, Fb);
  }

  if (unordered_result == 1) {
    mov(                                    -1, Rresult);
    movcc(f_equal,              true, fcc0,  0, Rresult);
    movcc(f_unorderedOrGreater, true, fcc0,  1, Rresult);
  } else {
    mov(                                    -1, Rresult);
    movcc(f_equal,              true, fcc0,  0, Rresult);
    movcc(f_greater,            true, fcc0,  1, Rresult);
  }
}


void MacroAssembler::save_all_globals_into_locals() {
  mov(G1,L1);
  mov(G2,L2);
  mov(G3,L3);
  mov(G4,L4);
  mov(G5,L5);
  mov(G6,L6);
  mov(G7,L7);
}

void MacroAssembler::restore_globals_from_locals() {
  mov(L1,G1);
  mov(L2,G2);
  mov(L3,G3);
  mov(L4,G4);
  mov(L5,G5);
  mov(L6,G6);
  mov(L7,G7);
}

RegisterOrConstant MacroAssembler::delayed_value_impl(intptr_t* delayed_value_addr,
                                                      Register tmp,
                                                      int offset) {
  intptr_t value = *delayed_value_addr;
  if (value != 0)
    return RegisterOrConstant(value + offset);

  // load indirectly to solve generation ordering problem
  AddressLiteral a(delayed_value_addr);
  load_ptr_contents(a, tmp);

#ifdef ASSERT
  tst(tmp);
  breakpoint_trap(zero, xcc);
#endif

  if (offset != 0)
    add(tmp, offset, tmp);

  return RegisterOrConstant(tmp);
}


RegisterOrConstant MacroAssembler::regcon_andn_ptr(RegisterOrConstant s1, RegisterOrConstant s2, RegisterOrConstant d, Register temp) {
  assert(d.register_or_noreg() != G0, "lost side effect");
  if ((s2.is_constant() && s2.as_constant() == 0) ||
      (s2.is_register() && s2.as_register() == G0)) {
    // Do nothing, just move value.
    if (s1.is_register()) {
      if (d.is_constant())  d = temp;
      mov(s1.as_register(), d.as_register());
      return d;
    } else {
      return s1;
    }
  }

  if (s1.is_register()) {
    assert_different_registers(s1.as_register(), temp);
    if (d.is_constant())  d = temp;
    andn(s1.as_register(), ensure_simm13_or_reg(s2, temp), d.as_register());
    return d;
  } else {
    if (s2.is_register()) {
      assert_different_registers(s2.as_register(), temp);
      if (d.is_constant())  d = temp;
      set(s1.as_constant(), temp);
      andn(temp, s2.as_register(), d.as_register());
      return d;
    } else {
      intptr_t res = s1.as_constant() & ~s2.as_constant();
      return res;
    }
  }
}

RegisterOrConstant MacroAssembler::regcon_inc_ptr(RegisterOrConstant s1, RegisterOrConstant s2, RegisterOrConstant d, Register temp) {
  assert(d.register_or_noreg() != G0, "lost side effect");
  if ((s2.is_constant() && s2.as_constant() == 0) ||
      (s2.is_register() && s2.as_register() == G0)) {
    // Do nothing, just move value.
    if (s1.is_register()) {
      if (d.is_constant())  d = temp;
      mov(s1.as_register(), d.as_register());
      return d;
    } else {
      return s1;
    }
  }

  if (s1.is_register()) {
    assert_different_registers(s1.as_register(), temp);
    if (d.is_constant())  d = temp;
    add(s1.as_register(), ensure_simm13_or_reg(s2, temp), d.as_register());
    return d;
  } else {
    if (s2.is_register()) {
      assert_different_registers(s2.as_register(), temp);
      if (d.is_constant())  d = temp;
      add(s2.as_register(), ensure_simm13_or_reg(s1, temp), d.as_register());
      return d;
    } else {
      intptr_t res = s1.as_constant() + s2.as_constant();
      return res;
    }
  }
}

RegisterOrConstant MacroAssembler::regcon_sll_ptr(RegisterOrConstant s1, RegisterOrConstant s2, RegisterOrConstant d, Register temp) {
  assert(d.register_or_noreg() != G0, "lost side effect");
  if (!is_simm13(s2.constant_or_zero()))
    s2 = (s2.as_constant() & 0xFF);
  if ((s2.is_constant() && s2.as_constant() == 0) ||
      (s2.is_register() && s2.as_register() == G0)) {
    // Do nothing, just move value.
    if (s1.is_register()) {
      if (d.is_constant())  d = temp;
      mov(s1.as_register(), d.as_register());
      return d;
    } else {
      return s1;
    }
  }

  if (s1.is_register()) {
    assert_different_registers(s1.as_register(), temp);
    if (d.is_constant())  d = temp;
    sll_ptr(s1.as_register(), ensure_simm13_or_reg(s2, temp), d.as_register());
    return d;
  } else {
    if (s2.is_register()) {
      assert_different_registers(s2.as_register(), temp);
      if (d.is_constant())  d = temp;
      set(s1.as_constant(), temp);
      sll_ptr(temp, s2.as_register(), d.as_register());
      return d;
    } else {
      intptr_t res = s1.as_constant() << s2.as_constant();
      return res;
    }
  }
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
                                             Register sethi_temp,
                                             Label& L_no_such_interface) {
  assert_different_registers(recv_klass, intf_klass, method_result, scan_temp);
  assert(itable_index.is_constant() || itable_index.as_register() == method_result,
         "caller must use same register for non-constant itable index as for method");

  Label L_no_such_interface_restore;
  bool did_save = false;
  if (scan_temp == noreg || sethi_temp == noreg) {
    Register recv_2 = recv_klass->is_global() ? recv_klass : L0;
    Register intf_2 = intf_klass->is_global() ? intf_klass : L1;
    assert(method_result->is_global(), "must be able to return value");
    scan_temp  = L2;
    sethi_temp = L3;
    save_frame_and_mov(0, recv_klass, recv_2, intf_klass, intf_2);
    recv_klass = recv_2;
    intf_klass = intf_2;
    did_save = true;
  }

  // Compute start of first itableOffsetEntry (which is at the end of the vtable)
  int vtable_base = InstanceKlass::vtable_start_offset() * wordSize;
  int scan_step   = itableOffsetEntry::size() * wordSize;
  int vte_size    = vtableEntry::size() * wordSize;

  lduw(recv_klass, InstanceKlass::vtable_length_offset() * wordSize, scan_temp);
  // %%% We should store the aligned, prescaled offset in the klassoop.
  // Then the next several instructions would fold away.

  int round_to_unit = ((HeapWordsPerLong > 1) ? BytesPerLong : 0);
  int itb_offset = vtable_base;
  if (round_to_unit != 0) {
    // hoist first instruction of round_to(scan_temp, BytesPerLong):
    itb_offset += round_to_unit - wordSize;
  }
  int itb_scale = exact_log2(vtableEntry::size() * wordSize);
  sll(scan_temp, itb_scale,  scan_temp);
  add(scan_temp, itb_offset, scan_temp);
  if (round_to_unit != 0) {
    // Round up to align_object_offset boundary
    // see code for InstanceKlass::start_of_itable!
    // Was: round_to(scan_temp, BytesPerLong);
    // Hoisted: add(scan_temp, BytesPerLong-1, scan_temp);
    and3(scan_temp, -round_to_unit, scan_temp);
  }
  add(recv_klass, scan_temp, scan_temp);

  // Adjust recv_klass by scaled itable_index, so we can free itable_index.
  RegisterOrConstant itable_offset = itable_index;
  itable_offset = regcon_sll_ptr(itable_index, exact_log2(itableMethodEntry::size() * wordSize), itable_offset);
  itable_offset = regcon_inc_ptr(itable_offset, itableMethodEntry::method_offset_in_bytes(), itable_offset);
  add(recv_klass, ensure_simm13_or_reg(itable_offset, sethi_temp), recv_klass);

  // for (scan = klass->itable(); scan->interface() != NULL; scan += scan_step) {
  //   if (scan->interface() == intf) {
  //     result = (klass + scan->offset() + itable_index);
  //   }
  // }
  Label L_search, L_found_method;

  for (int peel = 1; peel >= 0; peel--) {
    // %%%% Could load both offset and interface in one ldx, if they were
    // in the opposite order.  This would save a load.
    ld_ptr(scan_temp, itableOffsetEntry::interface_offset_in_bytes(), method_result);

    // Check that this entry is non-null.  A null entry means that
    // the receiver class doesn't implement the interface, and wasn't the
    // same as when the caller was compiled.
    bpr(Assembler::rc_z, false, Assembler::pn, method_result, did_save ? L_no_such_interface_restore : L_no_such_interface);
    delayed()->cmp(method_result, intf_klass);

    if (peel) {
      brx(Assembler::equal,    false, Assembler::pt, L_found_method);
    } else {
      brx(Assembler::notEqual, false, Assembler::pn, L_search);
      // (invert the test to fall through to found_method...)
    }
    delayed()->add(scan_temp, scan_step, scan_temp);

    if (!peel)  break;

    bind(L_search);
  }

  bind(L_found_method);

  // Got a hit.
  int ito_offset = itableOffsetEntry::offset_offset_in_bytes();
  // scan_temp[-scan_step] points to the vtable offset we need
  ito_offset -= scan_step;
  lduw(scan_temp, ito_offset, scan_temp);
  ld_ptr(recv_klass, scan_temp, method_result);

  if (did_save) {
    Label L_done;
    ba(L_done);
    delayed()->restore();

    bind(L_no_such_interface_restore);
    ba(L_no_such_interface);
    delayed()->restore();

    bind(L_done);
  }
}


// virtual method calling
void MacroAssembler::lookup_virtual_method(Register recv_klass,
                                           RegisterOrConstant vtable_index,
                                           Register method_result) {
  assert_different_registers(recv_klass, method_result, vtable_index.register_or_noreg());
  Register sethi_temp = method_result;
  const int base = (InstanceKlass::vtable_start_offset() * wordSize +
                    // method pointer offset within the vtable entry:
                    vtableEntry::method_offset_in_bytes());
  RegisterOrConstant vtable_offset = vtable_index;
  // Each of the following three lines potentially generates an instruction.
  // But the total number of address formation instructions will always be
  // at most two, and will often be zero.  In any case, it will be optimal.
  // If vtable_index is a register, we will have (sll_ptr N,x; inc_ptr B,x; ld_ptr k,x).
  // If vtable_index is a constant, we will have at most (set B+X<<N,t; ld_ptr k,t).
  vtable_offset = regcon_sll_ptr(vtable_index, exact_log2(vtableEntry::size() * wordSize), vtable_offset);
  vtable_offset = regcon_inc_ptr(vtable_offset, base, vtable_offset, sethi_temp);
  Address vtable_entry_addr(recv_klass, ensure_simm13_or_reg(vtable_offset, sethi_temp));
  ld_ptr(vtable_entry_addr, method_result);
}


void MacroAssembler::check_klass_subtype(Register sub_klass,
                                         Register super_klass,
                                         Register temp_reg,
                                         Register temp2_reg,
                                         Label& L_success) {
  Register sub_2 = sub_klass;
  Register sup_2 = super_klass;
  if (!sub_2->is_global())  sub_2 = L0;
  if (!sup_2->is_global())  sup_2 = L1;
  bool did_save = false;
  if (temp_reg == noreg || temp2_reg == noreg) {
    temp_reg = L2;
    temp2_reg = L3;
    save_frame_and_mov(0, sub_klass, sub_2, super_klass, sup_2);
    sub_klass = sub_2;
    super_klass = sup_2;
    did_save = true;
  }
  Label L_failure, L_pop_to_failure, L_pop_to_success;
  check_klass_subtype_fast_path(sub_klass, super_klass,
                                temp_reg, temp2_reg,
                                (did_save ? &L_pop_to_success : &L_success),
                                (did_save ? &L_pop_to_failure : &L_failure), NULL);

  if (!did_save)
    save_frame_and_mov(0, sub_klass, sub_2, super_klass, sup_2);
  check_klass_subtype_slow_path(sub_2, sup_2,
                                L2, L3, L4, L5,
                                NULL, &L_pop_to_failure);

  // on success:
  bind(L_pop_to_success);
  restore();
  ba_short(L_success);

  // on failure:
  bind(L_pop_to_failure);
  restore();
  bind(L_failure);
}


void MacroAssembler::check_klass_subtype_fast_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register temp_reg,
                                                   Register temp2_reg,
                                                   Label* L_success,
                                                   Label* L_failure,
                                                   Label* L_slow_path,
                                        RegisterOrConstant super_check_offset) {
  int sc_offset = in_bytes(Klass::secondary_super_cache_offset());
  int sco_offset = in_bytes(Klass::super_check_offset_offset());

  bool must_load_sco  = (super_check_offset.constant_or_zero() == -1);
  bool need_slow_path = (must_load_sco ||
                         super_check_offset.constant_or_zero() == sco_offset);

  assert_different_registers(sub_klass, super_klass, temp_reg);
  if (super_check_offset.is_register()) {
    assert_different_registers(sub_klass, super_klass, temp_reg,
                               super_check_offset.as_register());
  } else if (must_load_sco) {
    assert(temp2_reg != noreg, "supply either a temp or a register offset");
  }

  Label L_fallthrough;
  int label_nulls = 0;
  if (L_success == NULL)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == NULL)   { L_failure   = &L_fallthrough; label_nulls++; }
  if (L_slow_path == NULL) { L_slow_path = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1 ||
         (L_slow_path == &L_fallthrough && label_nulls <= 2 && !need_slow_path),
         "at most one NULL in the batch, usually");

  // If the pointers are equal, we are done (e.g., String[] elements).
  // This self-check enables sharing of secondary supertype arrays among
  // non-primary types such as array-of-interface.  Otherwise, each such
  // type would need its own customized SSA.
  // We move this check to the front of the fast path because many
  // type checks are in fact trivially successful in this manner,
  // so we get a nicely predicted branch right at the start of the check.
  cmp(super_klass, sub_klass);
  brx(Assembler::equal, false, Assembler::pn, *L_success);
  delayed()->nop();

  // Check the supertype display:
  if (must_load_sco) {
    // The super check offset is always positive...
    lduw(super_klass, sco_offset, temp2_reg);
    super_check_offset = RegisterOrConstant(temp2_reg);
    // super_check_offset is register.
    assert_different_registers(sub_klass, super_klass, temp_reg, super_check_offset.as_register());
  }
  ld_ptr(sub_klass, super_check_offset, temp_reg);
  cmp(super_klass, temp_reg);

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

  // Hacked ba(), which may only be used just before L_fallthrough.
#define FINAL_JUMP(label)            \
  if (&(label) != &L_fallthrough) {  \
    ba(label);  delayed()->nop();    \
  }

  if (super_check_offset.is_register()) {
    brx(Assembler::equal, false, Assembler::pn, *L_success);
    delayed()->cmp(super_check_offset.as_register(), sc_offset);

    if (L_failure == &L_fallthrough) {
      brx(Assembler::equal, false, Assembler::pt, *L_slow_path);
      delayed()->nop();
    } else {
      brx(Assembler::notEqual, false, Assembler::pn, *L_failure);
      delayed()->nop();
      FINAL_JUMP(*L_slow_path);
    }
  } else if (super_check_offset.as_constant() == sc_offset) {
    // Need a slow path; fast failure is impossible.
    if (L_slow_path == &L_fallthrough) {
      brx(Assembler::equal, false, Assembler::pt, *L_success);
      delayed()->nop();
    } else {
      brx(Assembler::notEqual, false, Assembler::pn, *L_slow_path);
      delayed()->nop();
      FINAL_JUMP(*L_success);
    }
  } else {
    // No slow path; it's a fast decision.
    if (L_failure == &L_fallthrough) {
      brx(Assembler::equal, false, Assembler::pt, *L_success);
      delayed()->nop();
    } else {
      brx(Assembler::notEqual, false, Assembler::pn, *L_failure);
      delayed()->nop();
      FINAL_JUMP(*L_success);
    }
  }

  bind(L_fallthrough);

#undef FINAL_JUMP
}


void MacroAssembler::check_klass_subtype_slow_path(Register sub_klass,
                                                   Register super_klass,
                                                   Register count_temp,
                                                   Register scan_temp,
                                                   Register scratch_reg,
                                                   Register coop_reg,
                                                   Label* L_success,
                                                   Label* L_failure) {
  assert_different_registers(sub_klass, super_klass,
                             count_temp, scan_temp, scratch_reg, coop_reg);

  Label L_fallthrough, L_loop;
  int label_nulls = 0;
  if (L_success == NULL)   { L_success   = &L_fallthrough; label_nulls++; }
  if (L_failure == NULL)   { L_failure   = &L_fallthrough; label_nulls++; }
  assert(label_nulls <= 1, "at most one NULL in the batch");

  // a couple of useful fields in sub_klass:
  int ss_offset = in_bytes(Klass::secondary_supers_offset());
  int sc_offset = in_bytes(Klass::secondary_super_cache_offset());

  // Do a linear scan of the secondary super-klass chain.
  // This code is rarely used, so simplicity is a virtue here.

#ifndef PRODUCT
  int* pst_counter = &SharedRuntime::_partial_subtype_ctr;
  inc_counter((address) pst_counter, count_temp, scan_temp);
#endif

  // We will consult the secondary-super array.
  ld_ptr(sub_klass, ss_offset, scan_temp);

  Register search_key = super_klass;

  // Load the array length.  (Positive movl does right thing on LP64.)
  lduw(scan_temp, Array<Klass*>::length_offset_in_bytes(), count_temp);

  // Check for empty secondary super list
  tst(count_temp);

  // In the array of super classes elements are pointer sized.
  int element_size = wordSize;

  // Top of search loop
  bind(L_loop);
  br(Assembler::equal, false, Assembler::pn, *L_failure);
  delayed()->add(scan_temp, element_size, scan_temp);

  // Skip the array header in all array accesses.
  int elem_offset = Array<Klass*>::base_offset_in_bytes();
  elem_offset -= element_size;   // the scan pointer was pre-incremented also

  // Load next super to check
    ld_ptr( scan_temp, elem_offset, scratch_reg );

  // Look for Rsuper_klass on Rsub_klass's secondary super-class-overflow list
  cmp(scratch_reg, search_key);

  // A miss means we are NOT a subtype and need to keep looping
  brx(Assembler::notEqual, false, Assembler::pn, L_loop);
  delayed()->deccc(count_temp); // decrement trip counter in delay slot

  // Success.  Cache the super we found and proceed in triumph.
  st_ptr(super_klass, sub_klass, sc_offset);

  if (L_success != &L_fallthrough) {
    ba(*L_success);
    delayed()->nop();
  }

  bind(L_fallthrough);
}


RegisterOrConstant MacroAssembler::argument_offset(RegisterOrConstant arg_slot,
                                                   Register temp_reg,
                                                   int extra_slot_offset) {
  // cf. TemplateTable::prepare_invoke(), if (load_receiver).
  int stackElementSize = Interpreter::stackElementSize;
  int offset = extra_slot_offset * stackElementSize;
  if (arg_slot.is_constant()) {
    offset += arg_slot.as_constant() * stackElementSize;
    return offset;
  } else {
    assert(temp_reg != noreg, "must specify");
    sll_ptr(arg_slot.as_register(), exact_log2(stackElementSize), temp_reg);
    if (offset != 0)
      add(temp_reg, offset, temp_reg);
    return temp_reg;
  }
}


Address MacroAssembler::argument_address(RegisterOrConstant arg_slot,
                                         Register temp_reg,
                                         int extra_slot_offset) {
  return Address(Gargs, argument_offset(arg_slot, temp_reg, extra_slot_offset));
}


void MacroAssembler::biased_locking_enter(Register obj_reg, Register mark_reg,
                                          Register temp_reg,
                                          Label& done, Label* slow_case,
                                          BiasedLockingCounters* counters) {
  assert(UseBiasedLocking, "why call this otherwise?");

  if (PrintBiasedLockingStatistics) {
    assert_different_registers(obj_reg, mark_reg, temp_reg, O7);
    if (counters == NULL)
      counters = BiasedLocking::counters();
  }

  Label cas_label;

  // Biased locking
  // See whether the lock is currently biased toward our thread and
  // whether the epoch is still valid
  // Note that the runtime guarantees sufficient alignment of JavaThread
  // pointers to allow age to be placed into low bits
  assert(markOopDesc::age_shift == markOopDesc::lock_bits + markOopDesc::biased_lock_bits, "biased locking makes assumptions about bit layout");
  and3(mark_reg, markOopDesc::biased_lock_mask_in_place, temp_reg);
  cmp_and_brx_short(temp_reg, markOopDesc::biased_lock_pattern, Assembler::notEqual, Assembler::pn, cas_label);

  load_klass(obj_reg, temp_reg);
  ld_ptr(Address(temp_reg, Klass::prototype_header_offset()), temp_reg);
  or3(G2_thread, temp_reg, temp_reg);
  xor3(mark_reg, temp_reg, temp_reg);
  andcc(temp_reg, ~((int) markOopDesc::age_mask_in_place), temp_reg);
  if (counters != NULL) {
    cond_inc(Assembler::equal, (address) counters->biased_lock_entry_count_addr(), mark_reg, temp_reg);
    // Reload mark_reg as we may need it later
    ld_ptr(Address(obj_reg, oopDesc::mark_offset_in_bytes()), mark_reg);
  }
  brx(Assembler::equal, true, Assembler::pt, done);
  delayed()->nop();

  Label try_revoke_bias;
  Label try_rebias;
  Address mark_addr = Address(obj_reg, oopDesc::mark_offset_in_bytes());
  assert(mark_addr.disp() == 0, "cas must take a zero displacement");

  // At this point we know that the header has the bias pattern and
  // that we are not the bias owner in the current epoch. We need to
  // figure out more details about the state of the header in order to
  // know what operations can be legally performed on the object's
  // header.

  // If the low three bits in the xor result aren't clear, that means
  // the prototype header is no longer biased and we have to revoke
  // the bias on this object.
  btst(markOopDesc::biased_lock_mask_in_place, temp_reg);
  brx(Assembler::notZero, false, Assembler::pn, try_revoke_bias);

  // Biasing is still enabled for this data type. See whether the
  // epoch of the current bias is still valid, meaning that the epoch
  // bits of the mark word are equal to the epoch bits of the
  // prototype header. (Note that the prototype header's epoch bits
  // only change at a safepoint.) If not, attempt to rebias the object
  // toward the current thread. Note that we must be absolutely sure
  // that the current epoch is invalid in order to do this because
  // otherwise the manipulations it performs on the mark word are
  // illegal.
  delayed()->btst(markOopDesc::epoch_mask_in_place, temp_reg);
  brx(Assembler::notZero, false, Assembler::pn, try_rebias);

  // The epoch of the current bias is still valid but we know nothing
  // about the owner; it might be set or it might be clear. Try to
  // acquire the bias of the object using an atomic operation. If this
  // fails we will go in to the runtime to revoke the object's bias.
  // Note that we first construct the presumed unbiased header so we
  // don't accidentally blow away another thread's valid bias.
  delayed()->and3(mark_reg,
                  markOopDesc::biased_lock_mask_in_place | markOopDesc::age_mask_in_place | markOopDesc::epoch_mask_in_place,
                  mark_reg);
  or3(G2_thread, mark_reg, temp_reg);
  cas_ptr(mark_addr.base(), mark_reg, temp_reg);
  // If the biasing toward our thread failed, this means that
  // another thread succeeded in biasing it toward itself and we
  // need to revoke that bias. The revocation will occur in the
  // interpreter runtime in the slow case.
  cmp(mark_reg, temp_reg);
  if (counters != NULL) {
    cond_inc(Assembler::zero, (address) counters->anonymously_biased_lock_entry_count_addr(), mark_reg, temp_reg);
  }
  if (slow_case != NULL) {
    brx(Assembler::notEqual, true, Assembler::pn, *slow_case);
    delayed()->nop();
  }
  ba_short(done);

  bind(try_rebias);
  // At this point we know the epoch has expired, meaning that the
  // current "bias owner", if any, is actually invalid. Under these
  // circumstances _only_, we are allowed to use the current header's
  // value as the comparison value when doing the cas to acquire the
  // bias in the current epoch. In other words, we allow transfer of
  // the bias from one thread to another directly in this situation.
  //
  // FIXME: due to a lack of registers we currently blow away the age
  // bits in this situation. Should attempt to preserve them.
  load_klass(obj_reg, temp_reg);
  ld_ptr(Address(temp_reg, Klass::prototype_header_offset()), temp_reg);
  or3(G2_thread, temp_reg, temp_reg);
  cas_ptr(mark_addr.base(), mark_reg, temp_reg);
  // If the biasing toward our thread failed, this means that
  // another thread succeeded in biasing it toward itself and we
  // need to revoke that bias. The revocation will occur in the
  // interpreter runtime in the slow case.
  cmp(mark_reg, temp_reg);
  if (counters != NULL) {
    cond_inc(Assembler::zero, (address) counters->rebiased_lock_entry_count_addr(), mark_reg, temp_reg);
  }
  if (slow_case != NULL) {
    brx(Assembler::notEqual, true, Assembler::pn, *slow_case);
    delayed()->nop();
  }
  ba_short(done);

  bind(try_revoke_bias);
  // The prototype mark in the klass doesn't have the bias bit set any
  // more, indicating that objects of this data type are not supposed
  // to be biased any more. We are going to try to reset the mark of
  // this object to the prototype value and fall through to the
  // CAS-based locking scheme. Note that if our CAS fails, it means
  // that another thread raced us for the privilege of revoking the
  // bias of this particular object, so it's okay to continue in the
  // normal locking code.
  //
  // FIXME: due to a lack of registers we currently blow away the age
  // bits in this situation. Should attempt to preserve them.
  load_klass(obj_reg, temp_reg);
  ld_ptr(Address(temp_reg, Klass::prototype_header_offset()), temp_reg);
  cas_ptr(mark_addr.base(), mark_reg, temp_reg);
  // Fall through to the normal CAS-based lock, because no matter what
  // the result of the above CAS, some thread must have succeeded in
  // removing the bias bit from the object's header.
  if (counters != NULL) {
    cmp(mark_reg, temp_reg);
    cond_inc(Assembler::zero, (address) counters->revoked_lock_entry_count_addr(), mark_reg, temp_reg);
  }

  bind(cas_label);
}

void MacroAssembler::biased_locking_exit (Address mark_addr, Register temp_reg, Label& done,
                                          bool allow_delay_slot_filling) {
  // Check for biased locking unlock case, which is a no-op
  // Note: we do not have to check the thread ID for two reasons.
  // First, the interpreter checks for IllegalMonitorStateException at
  // a higher level. Second, if the bias was revoked while we held the
  // lock, the object could not be rebiased toward another thread, so
  // the bias bit would be clear.
  ld_ptr(mark_addr, temp_reg);
  and3(temp_reg, markOopDesc::biased_lock_mask_in_place, temp_reg);
  cmp(temp_reg, markOopDesc::biased_lock_pattern);
  brx(Assembler::equal, allow_delay_slot_filling, Assembler::pt, done);
  delayed();
  if (!allow_delay_slot_filling) {
    nop();
  }
}


// compiler_lock_object() and compiler_unlock_object() are direct transliterations
// of i486.ad fast_lock() and fast_unlock().  See those methods for detailed comments.
// The code could be tightened up considerably.
//
// box->dhw disposition - post-conditions at DONE_LABEL.
// -   Successful inflated lock:  box->dhw != 0.
//     Any non-zero value suffices.
//     Consider G2_thread, rsp, boxReg, or unused_mark()
// -   Successful Stack-lock: box->dhw == mark.
//     box->dhw must contain the displaced mark word value
// -   Failure -- icc.ZFlag == 0 and box->dhw is undefined.
//     The slow-path fast_enter() and slow_enter() operators
//     are responsible for setting box->dhw = NonZero (typically ::unused_mark).
// -   Biased: box->dhw is undefined
//
// SPARC refworkload performance - specifically jetstream and scimark - are
// extremely sensitive to the size of the code emitted by compiler_lock_object
// and compiler_unlock_object.  Critically, the key factor is code size, not path
// length.  (Simply experiments to pad CLO with unexecuted NOPs demonstrte the
// effect).


void MacroAssembler::compiler_lock_object(Register Roop, Register Rmark,
                                          Register Rbox, Register Rscratch,
                                          BiasedLockingCounters* counters,
                                          bool try_bias) {
   Address mark_addr(Roop, oopDesc::mark_offset_in_bytes());

   verify_oop(Roop);
   Label done ;

   if (counters != NULL) {
     inc_counter((address) counters->total_entry_count_addr(), Rmark, Rscratch);
   }

   if (EmitSync & 1) {
     mov(3, Rscratch);
     st_ptr(Rscratch, Rbox, BasicLock::displaced_header_offset_in_bytes());
     cmp(SP, G0);
     return ;
   }

   if (EmitSync & 2) {

     // Fetch object's markword
     ld_ptr(mark_addr, Rmark);

     if (try_bias) {
        biased_locking_enter(Roop, Rmark, Rscratch, done, NULL, counters);
     }

     // Save Rbox in Rscratch to be used for the cas operation
     mov(Rbox, Rscratch);

     // set Rmark to markOop | markOopDesc::unlocked_value
     or3(Rmark, markOopDesc::unlocked_value, Rmark);

     // Initialize the box.  (Must happen before we update the object mark!)
     st_ptr(Rmark, Rbox, BasicLock::displaced_header_offset_in_bytes());

     // compare object markOop with Rmark and if equal exchange Rscratch with object markOop
     assert(mark_addr.disp() == 0, "cas must take a zero displacement");
     cas_ptr(mark_addr.base(), Rmark, Rscratch);

     // if compare/exchange succeeded we found an unlocked object and we now have locked it
     // hence we are done
     cmp(Rmark, Rscratch);
#ifdef _LP64
     sub(Rscratch, STACK_BIAS, Rscratch);
#endif
     brx(Assembler::equal, false, Assembler::pt, done);
     delayed()->sub(Rscratch, SP, Rscratch);  //pull next instruction into delay slot

     // we did not find an unlocked object so see if this is a recursive case
     // sub(Rscratch, SP, Rscratch);
     assert(os::vm_page_size() > 0xfff, "page size too small - change the constant");
     andcc(Rscratch, 0xfffff003, Rscratch);
     st_ptr(Rscratch, Rbox, BasicLock::displaced_header_offset_in_bytes());
     bind (done);
     return ;
   }

   Label Egress ;

   if (EmitSync & 256) {
      Label IsInflated ;

      ld_ptr(mark_addr, Rmark);           // fetch obj->mark
      // Triage: biased, stack-locked, neutral, inflated
      if (try_bias) {
        biased_locking_enter(Roop, Rmark, Rscratch, done, NULL, counters);
        // Invariant: if control reaches this point in the emitted stream
        // then Rmark has not been modified.
      }

      // Store mark into displaced mark field in the on-stack basic-lock "box"
      // Critically, this must happen before the CAS
      // Maximize the ST-CAS distance to minimize the ST-before-CAS penalty.
      st_ptr(Rmark, Rbox, BasicLock::displaced_header_offset_in_bytes());
      andcc(Rmark, 2, G0);
      brx(Assembler::notZero, false, Assembler::pn, IsInflated);
      delayed()->

      // Try stack-lock acquisition.
      // Beware: the 1st instruction is in a delay slot
      mov(Rbox,  Rscratch);
      or3(Rmark, markOopDesc::unlocked_value, Rmark);
      assert(mark_addr.disp() == 0, "cas must take a zero displacement");
      cas_ptr(mark_addr.base(), Rmark, Rscratch);
      cmp(Rmark, Rscratch);
      brx(Assembler::equal, false, Assembler::pt, done);
      delayed()->sub(Rscratch, SP, Rscratch);

      // Stack-lock attempt failed - check for recursive stack-lock.
      // See the comments below about how we might remove this case.
#ifdef _LP64
      sub(Rscratch, STACK_BIAS, Rscratch);
#endif
      assert(os::vm_page_size() > 0xfff, "page size too small - change the constant");
      andcc(Rscratch, 0xfffff003, Rscratch);
      br(Assembler::always, false, Assembler::pt, done);
      delayed()-> st_ptr(Rscratch, Rbox, BasicLock::displaced_header_offset_in_bytes());

      bind(IsInflated);
      if (EmitSync & 64) {
         // If m->owner != null goto IsLocked
         // Pessimistic form: Test-and-CAS vs CAS
         // The optimistic form avoids RTS->RTO cache line upgrades.
         ld_ptr(Rmark, ObjectMonitor::owner_offset_in_bytes() - 2, Rscratch);
         andcc(Rscratch, Rscratch, G0);
         brx(Assembler::notZero, false, Assembler::pn, done);
         delayed()->nop();
         // m->owner == null : it's unlocked.
      }

      // Try to CAS m->owner from null to Self
      // Invariant: if we acquire the lock then _recursions should be 0.
      add(Rmark, ObjectMonitor::owner_offset_in_bytes()-2, Rmark);
      mov(G2_thread, Rscratch);
      cas_ptr(Rmark, G0, Rscratch);
      cmp(Rscratch, G0);
      // Intentional fall-through into done
   } else {
      // Aggressively avoid the Store-before-CAS penalty
      // Defer the store into box->dhw until after the CAS
      Label IsInflated, Recursive ;

// Anticipate CAS -- Avoid RTS->RTO upgrade
// prefetch (mark_addr, Assembler::severalWritesAndPossiblyReads);

      ld_ptr(mark_addr, Rmark);           // fetch obj->mark
      // Triage: biased, stack-locked, neutral, inflated

      if (try_bias) {
        biased_locking_enter(Roop, Rmark, Rscratch, done, NULL, counters);
        // Invariant: if control reaches this point in the emitted stream
        // then Rmark has not been modified.
      }
      andcc(Rmark, 2, G0);
      brx(Assembler::notZero, false, Assembler::pn, IsInflated);
      delayed()->                         // Beware - dangling delay-slot

      // Try stack-lock acquisition.
      // Transiently install BUSY (0) encoding in the mark word.
      // if the CAS of 0 into the mark was successful then we execute:
      //   ST box->dhw  = mark   -- save fetched mark in on-stack basiclock box
      //   ST obj->mark = box    -- overwrite transient 0 value
      // This presumes TSO, of course.

      mov(0, Rscratch);
      or3(Rmark, markOopDesc::unlocked_value, Rmark);
      assert(mark_addr.disp() == 0, "cas must take a zero displacement");
      cas_ptr(mark_addr.base(), Rmark, Rscratch);
// prefetch (mark_addr, Assembler::severalWritesAndPossiblyReads);
      cmp(Rscratch, Rmark);
      brx(Assembler::notZero, false, Assembler::pn, Recursive);
      delayed()->st_ptr(Rmark, Rbox, BasicLock::displaced_header_offset_in_bytes());
      if (counters != NULL) {
        cond_inc(Assembler::equal, (address) counters->fast_path_entry_count_addr(), Rmark, Rscratch);
      }
      ba(done);
      delayed()->st_ptr(Rbox, mark_addr);

      bind(Recursive);
      // Stack-lock attempt failed - check for recursive stack-lock.
      // Tests show that we can remove the recursive case with no impact
      // on refworkload 0.83.  If we need to reduce the size of the code
      // emitted by compiler_lock_object() the recursive case is perfect
      // candidate.
      //
      // A more extreme idea is to always inflate on stack-lock recursion.
      // This lets us eliminate the recursive checks in compiler_lock_object
      // and compiler_unlock_object and the (box->dhw == 0) encoding.
      // A brief experiment - requiring changes to synchronizer.cpp, interpreter,
      // and showed a performance *increase*.  In the same experiment I eliminated
      // the fast-path stack-lock code from the interpreter and always passed
      // control to the "slow" operators in synchronizer.cpp.

      // RScratch contains the fetched obj->mark value from the failed CAS.
#ifdef _LP64
      sub(Rscratch, STACK_BIAS, Rscratch);
#endif
      sub(Rscratch, SP, Rscratch);
      assert(os::vm_page_size() > 0xfff, "page size too small - change the constant");
      andcc(Rscratch, 0xfffff003, Rscratch);
      if (counters != NULL) {
        // Accounting needs the Rscratch register
        st_ptr(Rscratch, Rbox, BasicLock::displaced_header_offset_in_bytes());
        cond_inc(Assembler::equal, (address) counters->fast_path_entry_count_addr(), Rmark, Rscratch);
        ba_short(done);
      } else {
        ba(done);
        delayed()->st_ptr(Rscratch, Rbox, BasicLock::displaced_header_offset_in_bytes());
      }

      bind   (IsInflated);
      if (EmitSync & 64) {
         // If m->owner != null goto IsLocked
         // Test-and-CAS vs CAS
         // Pessimistic form avoids futile (doomed) CAS attempts
         // The optimistic form avoids RTS->RTO cache line upgrades.
         ld_ptr(Rmark, ObjectMonitor::owner_offset_in_bytes() - 2, Rscratch);
         andcc(Rscratch, Rscratch, G0);
         brx(Assembler::notZero, false, Assembler::pn, done);
         delayed()->nop();
         // m->owner == null : it's unlocked.
      }

      // Try to CAS m->owner from null to Self
      // Invariant: if we acquire the lock then _recursions should be 0.
      add(Rmark, ObjectMonitor::owner_offset_in_bytes()-2, Rmark);
      mov(G2_thread, Rscratch);
      cas_ptr(Rmark, G0, Rscratch);
      cmp(Rscratch, G0);
      // ST box->displaced_header = NonZero.
      // Any non-zero value suffices:
      //    unused_mark(), G2_thread, RBox, RScratch, rsp, etc.
      st_ptr(Rbox, Rbox, BasicLock::displaced_header_offset_in_bytes());
      // Intentional fall-through into done
   }

   bind   (done);
}

void MacroAssembler::compiler_unlock_object(Register Roop, Register Rmark,
                                            Register Rbox, Register Rscratch,
                                            bool try_bias) {
   Address mark_addr(Roop, oopDesc::mark_offset_in_bytes());

   Label done ;

   if (EmitSync & 4) {
     cmp(SP, G0);
     return ;
   }

   if (EmitSync & 8) {
     if (try_bias) {
        biased_locking_exit(mark_addr, Rscratch, done);
     }

     // Test first if it is a fast recursive unlock
     ld_ptr(Rbox, BasicLock::displaced_header_offset_in_bytes(), Rmark);
     br_null_short(Rmark, Assembler::pt, done);

     // Check if it is still a light weight lock, this is is true if we see
     // the stack address of the basicLock in the markOop of the object
     assert(mark_addr.disp() == 0, "cas must take a zero displacement");
     cas_ptr(mark_addr.base(), Rbox, Rmark);
     ba(done);
     delayed()->cmp(Rbox, Rmark);
     bind(done);
     return ;
   }

   // Beware ... If the aggregate size of the code emitted by CLO and CUO is
   // is too large performance rolls abruptly off a cliff.
   // This could be related to inlining policies, code cache management, or
   // I$ effects.
   Label LStacked ;

   if (try_bias) {
      // TODO: eliminate redundant LDs of obj->mark
      biased_locking_exit(mark_addr, Rscratch, done);
   }

   ld_ptr(Roop, oopDesc::mark_offset_in_bytes(), Rmark);
   ld_ptr(Rbox, BasicLock::displaced_header_offset_in_bytes(), Rscratch);
   andcc(Rscratch, Rscratch, G0);
   brx(Assembler::zero, false, Assembler::pn, done);
   delayed()->nop();      // consider: relocate fetch of mark, above, into this DS
   andcc(Rmark, 2, G0);
   brx(Assembler::zero, false, Assembler::pt, LStacked);
   delayed()->nop();

   // It's inflated
   // Conceptually we need a #loadstore|#storestore "release" MEMBAR before
   // the ST of 0 into _owner which releases the lock.  This prevents loads
   // and stores within the critical section from reordering (floating)
   // past the store that releases the lock.  But TSO is a strong memory model
   // and that particular flavor of barrier is a noop, so we can safely elide it.
   // Note that we use 1-0 locking by default for the inflated case.  We
   // close the resultant (and rare) race by having contented threads in
   // monitorenter periodically poll _owner.
   ld_ptr(Rmark, ObjectMonitor::owner_offset_in_bytes() - 2, Rscratch);
   ld_ptr(Rmark, ObjectMonitor::recursions_offset_in_bytes() - 2, Rbox);
   xor3(Rscratch, G2_thread, Rscratch);
   orcc(Rbox, Rscratch, Rbox);
   brx(Assembler::notZero, false, Assembler::pn, done);
   delayed()->
   ld_ptr(Rmark, ObjectMonitor::EntryList_offset_in_bytes() - 2, Rscratch);
   ld_ptr(Rmark, ObjectMonitor::cxq_offset_in_bytes() - 2, Rbox);
   orcc(Rbox, Rscratch, G0);
   if (EmitSync & 65536) {
      Label LSucc ;
      brx(Assembler::notZero, false, Assembler::pn, LSucc);
      delayed()->nop();
      ba(done);
      delayed()->st_ptr(G0, Rmark, ObjectMonitor::owner_offset_in_bytes() - 2);

      bind(LSucc);
      st_ptr(G0, Rmark, ObjectMonitor::owner_offset_in_bytes() - 2);
      if (os::is_MP()) { membar (StoreLoad); }
      ld_ptr(Rmark, ObjectMonitor::succ_offset_in_bytes() - 2, Rscratch);
      andcc(Rscratch, Rscratch, G0);
      brx(Assembler::notZero, false, Assembler::pt, done);
      delayed()->andcc(G0, G0, G0);
      add(Rmark, ObjectMonitor::owner_offset_in_bytes()-2, Rmark);
      mov(G2_thread, Rscratch);
      cas_ptr(Rmark, G0, Rscratch);
      // invert icc.zf and goto done
      br_notnull(Rscratch, false, Assembler::pt, done);
      delayed()->cmp(G0, G0);
      ba(done);
      delayed()->cmp(G0, 1);
   } else {
      brx(Assembler::notZero, false, Assembler::pn, done);
      delayed()->nop();
      ba(done);
      delayed()->st_ptr(G0, Rmark, ObjectMonitor::owner_offset_in_bytes() - 2);
   }

   bind   (LStacked);
   // Consider: we could replace the expensive CAS in the exit
   // path with a simple ST of the displaced mark value fetched from
   // the on-stack basiclock box.  That admits a race where a thread T2
   // in the slow lock path -- inflating with monitor M -- could race a
   // thread T1 in the fast unlock path, resulting in a missed wakeup for T2.
   // More precisely T1 in the stack-lock unlock path could "stomp" the
   // inflated mark value M installed by T2, resulting in an orphan
   // object monitor M and T2 becoming stranded.  We can remedy that situation
   // by having T2 periodically poll the object's mark word using timed wait
   // operations.  If T2 discovers that a stomp has occurred it vacates
   // the monitor M and wakes any other threads stranded on the now-orphan M.
   // In addition the monitor scavenger, which performs deflation,
   // would also need to check for orpan monitors and stranded threads.
   //
   // Finally, inflation is also used when T2 needs to assign a hashCode
   // to O and O is stack-locked by T1.  The "stomp" race could cause
   // an assigned hashCode value to be lost.  We can avoid that condition
   // and provide the necessary hashCode stability invariants by ensuring
   // that hashCode generation is idempotent between copying GCs.
   // For example we could compute the hashCode of an object O as
   // O's heap address XOR some high quality RNG value that is refreshed
   // at GC-time.  The monitor scavenger would install the hashCode
   // found in any orphan monitors.  Again, the mechanism admits a
   // lost-update "stomp" WAW race but detects and recovers as needed.
   //
   // A prototype implementation showed excellent results, although
   // the scavenger and timeout code was rather involved.

   cas_ptr(mark_addr.base(), Rbox, Rscratch);
   cmp(Rbox, Rscratch);
   // Intentional fall through into done ...

   bind(done);
}



void MacroAssembler::print_CPU_state() {
  // %%%%% need to implement this
}

void MacroAssembler::verify_FPU(int stack_depth, const char* s) {
  // %%%%% need to implement this
}

void MacroAssembler::push_IU_state() {
  // %%%%% need to implement this
}


void MacroAssembler::pop_IU_state() {
  // %%%%% need to implement this
}


void MacroAssembler::push_FPU_state() {
  // %%%%% need to implement this
}


void MacroAssembler::pop_FPU_state() {
  // %%%%% need to implement this
}


void MacroAssembler::push_CPU_state() {
  // %%%%% need to implement this
}


void MacroAssembler::pop_CPU_state() {
  // %%%%% need to implement this
}



void MacroAssembler::verify_tlab() {
#ifdef ASSERT
  if (UseTLAB && VerifyOops) {
    Label next, next2, ok;
    Register t1 = L0;
    Register t2 = L1;
    Register t3 = L2;

    save_frame(0);
    ld_ptr(G2_thread, in_bytes(JavaThread::tlab_top_offset()), t1);
    ld_ptr(G2_thread, in_bytes(JavaThread::tlab_start_offset()), t2);
    or3(t1, t2, t3);
    cmp_and_br_short(t1, t2, Assembler::greaterEqual, Assembler::pn, next);
    STOP("assert(top >= start)");
    should_not_reach_here();

    bind(next);
    ld_ptr(G2_thread, in_bytes(JavaThread::tlab_top_offset()), t1);
    ld_ptr(G2_thread, in_bytes(JavaThread::tlab_end_offset()), t2);
    or3(t3, t2, t3);
    cmp_and_br_short(t1, t2, Assembler::lessEqual, Assembler::pn, next2);
    STOP("assert(top <= end)");
    should_not_reach_here();

    bind(next2);
    and3(t3, MinObjAlignmentInBytesMask, t3);
    cmp_and_br_short(t3, 0, Assembler::lessEqual, Assembler::pn, ok);
    STOP("assert(aligned)");
    should_not_reach_here();

    bind(ok);
    restore();
  }
#endif
}


void MacroAssembler::eden_allocate(
  Register obj,                        // result: pointer to object after successful allocation
  Register var_size_in_bytes,          // object size in bytes if unknown at compile time; invalid otherwise
  int      con_size_in_bytes,          // object size in bytes if   known at compile time
  Register t1,                         // temp register
  Register t2,                         // temp register
  Label&   slow_case                   // continuation point if fast allocation fails
){
  // make sure arguments make sense
  assert_different_registers(obj, var_size_in_bytes, t1, t2);
  assert(0 <= con_size_in_bytes && Assembler::is_simm13(con_size_in_bytes), "illegal object size");
  assert((con_size_in_bytes & MinObjAlignmentInBytesMask) == 0, "object size is not multiple of alignment");

  if (CMSIncrementalMode || !Universe::heap()->supports_inline_contig_alloc()) {
    // No allocation in the shared eden.
    ba(slow_case);
    delayed()->nop();
  } else {
    // get eden boundaries
    // note: we need both top & top_addr!
    const Register top_addr = t1;
    const Register end      = t2;

    CollectedHeap* ch = Universe::heap();
    set((intx)ch->top_addr(), top_addr);
    intx delta = (intx)ch->end_addr() - (intx)ch->top_addr();
    ld_ptr(top_addr, delta, end);
    ld_ptr(top_addr, 0, obj);

    // try to allocate
    Label retry;
    bind(retry);
#ifdef ASSERT
    // make sure eden top is properly aligned
    {
      Label L;
      btst(MinObjAlignmentInBytesMask, obj);
      br(Assembler::zero, false, Assembler::pt, L);
      delayed()->nop();
      STOP("eden top is not properly aligned");
      bind(L);
    }
#endif // ASSERT
    const Register free = end;
    sub(end, obj, free);                                   // compute amount of free space
    if (var_size_in_bytes->is_valid()) {
      // size is unknown at compile time
      cmp(free, var_size_in_bytes);
      br(Assembler::lessUnsigned, false, Assembler::pn, slow_case); // if there is not enough space go the slow case
      delayed()->add(obj, var_size_in_bytes, end);
    } else {
      // size is known at compile time
      cmp(free, con_size_in_bytes);
      br(Assembler::lessUnsigned, false, Assembler::pn, slow_case); // if there is not enough space go the slow case
      delayed()->add(obj, con_size_in_bytes, end);
    }
    // Compare obj with the value at top_addr; if still equal, swap the value of
    // end with the value at top_addr. If not equal, read the value at top_addr
    // into end.
    cas_ptr(top_addr, obj, end);
    // if someone beat us on the allocation, try again, otherwise continue
    cmp(obj, end);
    brx(Assembler::notEqual, false, Assembler::pn, retry);
    delayed()->mov(end, obj);                              // nop if successfull since obj == end

#ifdef ASSERT
    // make sure eden top is properly aligned
    {
      Label L;
      const Register top_addr = t1;

      set((intx)ch->top_addr(), top_addr);
      ld_ptr(top_addr, 0, top_addr);
      btst(MinObjAlignmentInBytesMask, top_addr);
      br(Assembler::zero, false, Assembler::pt, L);
      delayed()->nop();
      STOP("eden top is not properly aligned");
      bind(L);
    }
#endif // ASSERT
  }
}


void MacroAssembler::tlab_allocate(
  Register obj,                        // result: pointer to object after successful allocation
  Register var_size_in_bytes,          // object size in bytes if unknown at compile time; invalid otherwise
  int      con_size_in_bytes,          // object size in bytes if   known at compile time
  Register t1,                         // temp register
  Label&   slow_case                   // continuation point if fast allocation fails
){
  // make sure arguments make sense
  assert_different_registers(obj, var_size_in_bytes, t1);
  assert(0 <= con_size_in_bytes && is_simm13(con_size_in_bytes), "illegal object size");
  assert((con_size_in_bytes & MinObjAlignmentInBytesMask) == 0, "object size is not multiple of alignment");

  const Register free  = t1;

  verify_tlab();

  ld_ptr(G2_thread, in_bytes(JavaThread::tlab_top_offset()), obj);

  // calculate amount of free space
  ld_ptr(G2_thread, in_bytes(JavaThread::tlab_end_offset()), free);
  sub(free, obj, free);

  Label done;
  if (var_size_in_bytes == noreg) {
    cmp(free, con_size_in_bytes);
  } else {
    cmp(free, var_size_in_bytes);
  }
  br(Assembler::less, false, Assembler::pn, slow_case);
  // calculate the new top pointer
  if (var_size_in_bytes == noreg) {
    delayed()->add(obj, con_size_in_bytes, free);
  } else {
    delayed()->add(obj, var_size_in_bytes, free);
  }

  bind(done);

#ifdef ASSERT
  // make sure new free pointer is properly aligned
  {
    Label L;
    btst(MinObjAlignmentInBytesMask, free);
    br(Assembler::zero, false, Assembler::pt, L);
    delayed()->nop();
    STOP("updated TLAB free is not properly aligned");
    bind(L);
  }
#endif // ASSERT

  // update the tlab top pointer
  st_ptr(free, G2_thread, in_bytes(JavaThread::tlab_top_offset()));
  verify_tlab();
}


void MacroAssembler::tlab_refill(Label& retry, Label& try_eden, Label& slow_case) {
  Register top = O0;
  Register t1 = G1;
  Register t2 = G3;
  Register t3 = O1;
  assert_different_registers(top, t1, t2, t3, G4, G5 /* preserve G4 and G5 */);
  Label do_refill, discard_tlab;

  if (CMSIncrementalMode || !Universe::heap()->supports_inline_contig_alloc()) {
    // No allocation in the shared eden.
    ba(slow_case);
    delayed()->nop();
  }

  ld_ptr(G2_thread, in_bytes(JavaThread::tlab_top_offset()), top);
  ld_ptr(G2_thread, in_bytes(JavaThread::tlab_end_offset()), t1);
  ld_ptr(G2_thread, in_bytes(JavaThread::tlab_refill_waste_limit_offset()), t2);

  // calculate amount of free space
  sub(t1, top, t1);
  srl_ptr(t1, LogHeapWordSize, t1);

  // Retain tlab and allocate object in shared space if
  // the amount free in the tlab is too large to discard.
  cmp(t1, t2);
  brx(Assembler::lessEqual, false, Assembler::pt, discard_tlab);

  // increment waste limit to prevent getting stuck on this slow path
  delayed()->add(t2, ThreadLocalAllocBuffer::refill_waste_limit_increment(), t2);
  st_ptr(t2, G2_thread, in_bytes(JavaThread::tlab_refill_waste_limit_offset()));
  if (TLABStats) {
    // increment number of slow_allocations
    ld(G2_thread, in_bytes(JavaThread::tlab_slow_allocations_offset()), t2);
    add(t2, 1, t2);
    stw(t2, G2_thread, in_bytes(JavaThread::tlab_slow_allocations_offset()));
  }
  ba(try_eden);
  delayed()->nop();

  bind(discard_tlab);
  if (TLABStats) {
    // increment number of refills
    ld(G2_thread, in_bytes(JavaThread::tlab_number_of_refills_offset()), t2);
    add(t2, 1, t2);
    stw(t2, G2_thread, in_bytes(JavaThread::tlab_number_of_refills_offset()));
    // accumulate wastage
    ld(G2_thread, in_bytes(JavaThread::tlab_fast_refill_waste_offset()), t2);
    add(t2, t1, t2);
    stw(t2, G2_thread, in_bytes(JavaThread::tlab_fast_refill_waste_offset()));
  }

  // if tlab is currently allocated (top or end != null) then
  // fill [top, end + alignment_reserve) with array object
  br_null_short(top, Assembler::pn, do_refill);

  set((intptr_t)markOopDesc::prototype()->copy_set_hash(0x2), t2);
  st_ptr(t2, top, oopDesc::mark_offset_in_bytes()); // set up the mark word
  // set klass to intArrayKlass
  sub(t1, typeArrayOopDesc::header_size(T_INT), t1);
  add(t1, ThreadLocalAllocBuffer::alignment_reserve(), t1);
  sll_ptr(t1, log2_intptr(HeapWordSize/sizeof(jint)), t1);
  st(t1, top, arrayOopDesc::length_offset_in_bytes());
  set((intptr_t)Universe::intArrayKlassObj_addr(), t2);
  ld_ptr(t2, 0, t2);
  // store klass last.  concurrent gcs assumes klass length is valid if
  // klass field is not null.
  store_klass(t2, top);
  verify_oop(top);

  ld_ptr(G2_thread, in_bytes(JavaThread::tlab_start_offset()), t1);
  sub(top, t1, t1); // size of tlab's allocated portion
  incr_allocated_bytes(t1, t2, t3);

  // refill the tlab with an eden allocation
  bind(do_refill);
  ld_ptr(G2_thread, in_bytes(JavaThread::tlab_size_offset()), t1);
  sll_ptr(t1, LogHeapWordSize, t1);
  // allocate new tlab, address returned in top
  eden_allocate(top, t1, 0, t2, t3, slow_case);

  st_ptr(top, G2_thread, in_bytes(JavaThread::tlab_start_offset()));
  st_ptr(top, G2_thread, in_bytes(JavaThread::tlab_top_offset()));
#ifdef ASSERT
  // check that tlab_size (t1) is still valid
  {
    Label ok;
    ld_ptr(G2_thread, in_bytes(JavaThread::tlab_size_offset()), t2);
    sll_ptr(t2, LogHeapWordSize, t2);
    cmp_and_br_short(t1, t2, Assembler::equal, Assembler::pt, ok);
    STOP("assert(t1 == tlab_size)");
    should_not_reach_here();

    bind(ok);
  }
#endif // ASSERT
  add(top, t1, top); // t1 is tlab_size
  sub(top, ThreadLocalAllocBuffer::alignment_reserve_in_bytes(), top);
  st_ptr(top, G2_thread, in_bytes(JavaThread::tlab_end_offset()));
  verify_tlab();
  ba(retry);
  delayed()->nop();
}

void MacroAssembler::incr_allocated_bytes(RegisterOrConstant size_in_bytes,
                                          Register t1, Register t2) {
  // Bump total bytes allocated by this thread
  assert(t1->is_global(), "must be global reg"); // so all 64 bits are saved on a context switch
  assert_different_registers(size_in_bytes.register_or_noreg(), t1, t2);
  // v8 support has gone the way of the dodo
  ldx(G2_thread, in_bytes(JavaThread::allocated_bytes_offset()), t1);
  add(t1, ensure_simm13_or_reg(size_in_bytes, t2), t1);
  stx(t1, G2_thread, in_bytes(JavaThread::allocated_bytes_offset()));
}

Assembler::Condition MacroAssembler::negate_condition(Assembler::Condition cond) {
  switch (cond) {
    // Note some conditions are synonyms for others
    case Assembler::never:                return Assembler::always;
    case Assembler::zero:                 return Assembler::notZero;
    case Assembler::lessEqual:            return Assembler::greater;
    case Assembler::less:                 return Assembler::greaterEqual;
    case Assembler::lessEqualUnsigned:    return Assembler::greaterUnsigned;
    case Assembler::lessUnsigned:         return Assembler::greaterEqualUnsigned;
    case Assembler::negative:             return Assembler::positive;
    case Assembler::overflowSet:          return Assembler::overflowClear;
    case Assembler::always:               return Assembler::never;
    case Assembler::notZero:              return Assembler::zero;
    case Assembler::greater:              return Assembler::lessEqual;
    case Assembler::greaterEqual:         return Assembler::less;
    case Assembler::greaterUnsigned:      return Assembler::lessEqualUnsigned;
    case Assembler::greaterEqualUnsigned: return Assembler::lessUnsigned;
    case Assembler::positive:             return Assembler::negative;
    case Assembler::overflowClear:        return Assembler::overflowSet;
  }

  ShouldNotReachHere(); return Assembler::overflowClear;
}

void MacroAssembler::cond_inc(Assembler::Condition cond, address counter_ptr,
                              Register Rtmp1, Register Rtmp2 /*, Register Rtmp3, Register Rtmp4 */) {
  Condition negated_cond = negate_condition(cond);
  Label L;
  brx(negated_cond, false, Assembler::pt, L);
  delayed()->nop();
  inc_counter(counter_ptr, Rtmp1, Rtmp2);
  bind(L);
}

void MacroAssembler::inc_counter(address counter_addr, Register Rtmp1, Register Rtmp2) {
  AddressLiteral addrlit(counter_addr);
  sethi(addrlit, Rtmp1);                 // Move hi22 bits into temporary register.
  Address addr(Rtmp1, addrlit.low10());  // Build an address with low10 bits.
  ld(addr, Rtmp2);
  inc(Rtmp2);
  st(Rtmp2, addr);
}

void MacroAssembler::inc_counter(int* counter_addr, Register Rtmp1, Register Rtmp2) {
  inc_counter((address) counter_addr, Rtmp1, Rtmp2);
}

SkipIfEqual::SkipIfEqual(
    MacroAssembler* masm, Register temp, const bool* flag_addr,
    Assembler::Condition condition) {
  _masm = masm;
  AddressLiteral flag(flag_addr);
  _masm->sethi(flag, temp);
  _masm->ldub(temp, flag.low10(), temp);
  _masm->tst(temp);
  _masm->br(condition, false, Assembler::pt, _label);
  _masm->delayed()->nop();
}

SkipIfEqual::~SkipIfEqual() {
  _masm->bind(_label);
}


// Writes to stack successive pages until offset reached to check for
// stack overflow + shadow pages.  This clobbers tsp and scratch.
void MacroAssembler::bang_stack_size(Register Rsize, Register Rtsp,
                                     Register Rscratch) {
  // Use stack pointer in temp stack pointer
  mov(SP, Rtsp);

  // Bang stack for total size given plus stack shadow page size.
  // Bang one page at a time because a large size can overflow yellow and
  // red zones (the bang will fail but stack overflow handling can't tell that
  // it was a stack overflow bang vs a regular segv).
  int offset = os::vm_page_size();
  Register Roffset = Rscratch;

  Label loop;
  bind(loop);
  set((-offset)+STACK_BIAS, Rscratch);
  st(G0, Rtsp, Rscratch);
  set(offset, Roffset);
  sub(Rsize, Roffset, Rsize);
  cmp(Rsize, G0);
  br(Assembler::greater, false, Assembler::pn, loop);
  delayed()->sub(Rtsp, Roffset, Rtsp);

  // Bang down shadow pages too.
  // At this point, (tmp-0) is the last address touched, so don't
  // touch it again.  (It was touched as (tmp-pagesize) but then tmp
  // was post-decremented.)  Skip this address by starting at i=1, and
  // touch a few more pages below.  N.B.  It is important to touch all
  // the way down to and including i=StackShadowPages.
  for (int i = 1; i < StackShadowPages; i++) {
    set((-i*offset)+STACK_BIAS, Rscratch);
    st(G0, Rtsp, Rscratch);
  }
}

///////////////////////////////////////////////////////////////////////////////////
#if INCLUDE_ALL_GCS

static address satb_log_enqueue_with_frame = NULL;
static u_char* satb_log_enqueue_with_frame_end = NULL;

static address satb_log_enqueue_frameless = NULL;
static u_char* satb_log_enqueue_frameless_end = NULL;

static int EnqueueCodeSize = 128 DEBUG_ONLY( + 256); // Instructions?

static void generate_satb_log_enqueue(bool with_frame) {
  BufferBlob* bb = BufferBlob::create("enqueue_with_frame", EnqueueCodeSize);
  CodeBuffer buf(bb);
  MacroAssembler masm(&buf);

#define __ masm.

  address start = __ pc();
  Register pre_val;

  Label refill, restart;
  if (with_frame) {
    __ save_frame(0);
    pre_val = I0;  // Was O0 before the save.
  } else {
    pre_val = O0;
  }

  int satb_q_index_byte_offset =
    in_bytes(JavaThread::satb_mark_queue_offset() +
             PtrQueue::byte_offset_of_index());

  int satb_q_buf_byte_offset =
    in_bytes(JavaThread::satb_mark_queue_offset() +
             PtrQueue::byte_offset_of_buf());

  assert(in_bytes(PtrQueue::byte_width_of_index()) == sizeof(intptr_t) &&
         in_bytes(PtrQueue::byte_width_of_buf()) == sizeof(intptr_t),
         "check sizes in assembly below");

  __ bind(restart);

  // Load the index into the SATB buffer. PtrQueue::_index is a size_t
  // so ld_ptr is appropriate.
  __ ld_ptr(G2_thread, satb_q_index_byte_offset, L0);

  // index == 0?
  __ cmp_and_brx_short(L0, G0, Assembler::equal, Assembler::pn, refill);

  __ ld_ptr(G2_thread, satb_q_buf_byte_offset, L1);
  __ sub(L0, oopSize, L0);

  __ st_ptr(pre_val, L1, L0);  // [_buf + index] := I0
  if (!with_frame) {
    // Use return-from-leaf
    __ retl();
    __ delayed()->st_ptr(L0, G2_thread, satb_q_index_byte_offset);
  } else {
    // Not delayed.
    __ st_ptr(L0, G2_thread, satb_q_index_byte_offset);
  }
  if (with_frame) {
    __ ret();
    __ delayed()->restore();
  }
  __ bind(refill);

  address handle_zero =
    CAST_FROM_FN_PTR(address,
                     &SATBMarkQueueSet::handle_zero_index_for_thread);
  // This should be rare enough that we can afford to save all the
  // scratch registers that the calling context might be using.
  __ mov(G1_scratch, L0);
  __ mov(G3_scratch, L1);
  __ mov(G4, L2);
  // We need the value of O0 above (for the write into the buffer), so we
  // save and restore it.
  __ mov(O0, L3);
  // Since the call will overwrite O7, we save and restore that, as well.
  __ mov(O7, L4);
  __ call_VM_leaf(L5, handle_zero, G2_thread);
  __ mov(L0, G1_scratch);
  __ mov(L1, G3_scratch);
  __ mov(L2, G4);
  __ mov(L3, O0);
  __ br(Assembler::always, /*annul*/false, Assembler::pt, restart);
  __ delayed()->mov(L4, O7);

  if (with_frame) {
    satb_log_enqueue_with_frame = start;
    satb_log_enqueue_with_frame_end = __ pc();
  } else {
    satb_log_enqueue_frameless = start;
    satb_log_enqueue_frameless_end = __ pc();
  }

#undef __
}

static inline void generate_satb_log_enqueue_if_necessary(bool with_frame) {
  if (with_frame) {
    if (satb_log_enqueue_with_frame == 0) {
      generate_satb_log_enqueue(with_frame);
      assert(satb_log_enqueue_with_frame != 0, "postcondition.");
      if (G1SATBPrintStubs) {
        tty->print_cr("Generated with-frame satb enqueue:");
        Disassembler::decode((u_char*)satb_log_enqueue_with_frame,
                             satb_log_enqueue_with_frame_end,
                             tty);
      }
    }
  } else {
    if (satb_log_enqueue_frameless == 0) {
      generate_satb_log_enqueue(with_frame);
      assert(satb_log_enqueue_frameless != 0, "postcondition.");
      if (G1SATBPrintStubs) {
        tty->print_cr("Generated frameless satb enqueue:");
        Disassembler::decode((u_char*)satb_log_enqueue_frameless,
                             satb_log_enqueue_frameless_end,
                             tty);
      }
    }
  }
}

void MacroAssembler::g1_write_barrier_pre(Register obj,
                                          Register index,
                                          int offset,
                                          Register pre_val,
                                          Register tmp,
                                          bool preserve_o_regs) {
  Label filtered;

  if (obj == noreg) {
    // We are not loading the previous value so make
    // sure that we don't trash the value in pre_val
    // with the code below.
    assert_different_registers(pre_val, tmp);
  } else {
    // We will be loading the previous value
    // in this code so...
    assert(offset == 0 || index == noreg, "choose one");
    assert(pre_val == noreg, "check this code");
  }

  // Is marking active?
  if (in_bytes(PtrQueue::byte_width_of_active()) == 4) {
    ld(G2,
       in_bytes(JavaThread::satb_mark_queue_offset() +
                PtrQueue::byte_offset_of_active()),
       tmp);
  } else {
    guarantee(in_bytes(PtrQueue::byte_width_of_active()) == 1,
              "Assumption");
    ldsb(G2,
         in_bytes(JavaThread::satb_mark_queue_offset() +
                  PtrQueue::byte_offset_of_active()),
         tmp);
  }

  // Is marking active?
  cmp_and_br_short(tmp, G0, Assembler::equal, Assembler::pt, filtered);

  // Do we need to load the previous value?
  if (obj != noreg) {
    // Load the previous value...
    if (index == noreg) {
      if (Assembler::is_simm13(offset)) {
        load_heap_oop(obj, offset, tmp);
      } else {
        set(offset, tmp);
        load_heap_oop(obj, tmp, tmp);
      }
    } else {
      load_heap_oop(obj, index, tmp);
    }
    // Previous value has been loaded into tmp
    pre_val = tmp;
  }

  assert(pre_val != noreg, "must have a real register");

  // Is the previous value null?
  cmp_and_brx_short(pre_val, G0, Assembler::equal, Assembler::pt, filtered);

  // OK, it's not filtered, so we'll need to call enqueue.  In the normal
  // case, pre_val will be a scratch G-reg, but there are some cases in
  // which it's an O-reg.  In the first case, do a normal call.  In the
  // latter, do a save here and call the frameless version.

  guarantee(pre_val->is_global() || pre_val->is_out(),
            "Or we need to think harder.");

  if (pre_val->is_global() && !preserve_o_regs) {
    generate_satb_log_enqueue_if_necessary(true); // with frame

    call(satb_log_enqueue_with_frame);
    delayed()->mov(pre_val, O0);
  } else {
    generate_satb_log_enqueue_if_necessary(false); // frameless

    save_frame(0);
    call(satb_log_enqueue_frameless);
    delayed()->mov(pre_val->after_save(), O0);
    restore();
  }

  bind(filtered);
}

static address dirty_card_log_enqueue = 0;
static u_char* dirty_card_log_enqueue_end = 0;

// This gets to assume that o0 contains the object address.
static void generate_dirty_card_log_enqueue(jbyte* byte_map_base) {
  BufferBlob* bb = BufferBlob::create("dirty_card_enqueue", EnqueueCodeSize*2);
  CodeBuffer buf(bb);
  MacroAssembler masm(&buf);
#define __ masm.
  address start = __ pc();

  Label not_already_dirty, restart, refill, young_card;

#ifdef _LP64
  __ srlx(O0, CardTableModRefBS::card_shift, O0);
#else
  __ srl(O0, CardTableModRefBS::card_shift, O0);
#endif
  AddressLiteral addrlit(byte_map_base);
  __ set(addrlit, O1); // O1 := <card table base>
  __ ldub(O0, O1, O2); // O2 := [O0 + O1]

  __ cmp_and_br_short(O2, G1SATBCardTableModRefBS::g1_young_card_val(), Assembler::equal, Assembler::pt, young_card);

  __ membar(Assembler::Membar_mask_bits(Assembler::StoreLoad));
  __ ldub(O0, O1, O2); // O2 := [O0 + O1]

  assert(CardTableModRefBS::dirty_card_val() == 0, "otherwise check this code");
  __ cmp_and_br_short(O2, G0, Assembler::notEqual, Assembler::pt, not_already_dirty);

  __ bind(young_card);
  // We didn't take the branch, so we're already dirty: return.
  // Use return-from-leaf
  __ retl();
  __ delayed()->nop();

  // Not dirty.
  __ bind(not_already_dirty);

  // Get O0 + O1 into a reg by itself
  __ add(O0, O1, O3);

  // First, dirty it.
  __ stb(G0, O3, G0);  // [cardPtr] := 0  (i.e., dirty).

  int dirty_card_q_index_byte_offset =
    in_bytes(JavaThread::dirty_card_queue_offset() +
             PtrQueue::byte_offset_of_index());
  int dirty_card_q_buf_byte_offset =
    in_bytes(JavaThread::dirty_card_queue_offset() +
             PtrQueue::byte_offset_of_buf());
  __ bind(restart);

  // Load the index into the update buffer. PtrQueue::_index is
  // a size_t so ld_ptr is appropriate here.
  __ ld_ptr(G2_thread, dirty_card_q_index_byte_offset, L0);

  // index == 0?
  __ cmp_and_brx_short(L0, G0, Assembler::equal, Assembler::pn, refill);

  __ ld_ptr(G2_thread, dirty_card_q_buf_byte_offset, L1);
  __ sub(L0, oopSize, L0);

  __ st_ptr(O3, L1, L0);  // [_buf + index] := I0
  // Use return-from-leaf
  __ retl();
  __ delayed()->st_ptr(L0, G2_thread, dirty_card_q_index_byte_offset);

  __ bind(refill);
  address handle_zero =
    CAST_FROM_FN_PTR(address,
                     &DirtyCardQueueSet::handle_zero_index_for_thread);
  // This should be rare enough that we can afford to save all the
  // scratch registers that the calling context might be using.
  __ mov(G1_scratch, L3);
  __ mov(G3_scratch, L5);
  // We need the value of O3 above (for the write into the buffer), so we
  // save and restore it.
  __ mov(O3, L6);
  // Since the call will overwrite O7, we save and restore that, as well.
  __ mov(O7, L4);

  __ call_VM_leaf(L7_thread_cache, handle_zero, G2_thread);
  __ mov(L3, G1_scratch);
  __ mov(L5, G3_scratch);
  __ mov(L6, O3);
  __ br(Assembler::always, /*annul*/false, Assembler::pt, restart);
  __ delayed()->mov(L4, O7);

  dirty_card_log_enqueue = start;
  dirty_card_log_enqueue_end = __ pc();
  // XXX Should have a guarantee here about not going off the end!
  // Does it already do so?  Do an experiment...

#undef __

}

static inline void
generate_dirty_card_log_enqueue_if_necessary(jbyte* byte_map_base) {
  if (dirty_card_log_enqueue == 0) {
    generate_dirty_card_log_enqueue(byte_map_base);
    assert(dirty_card_log_enqueue != 0, "postcondition.");
    if (G1SATBPrintStubs) {
      tty->print_cr("Generated dirty_card enqueue:");
      Disassembler::decode((u_char*)dirty_card_log_enqueue,
                           dirty_card_log_enqueue_end,
                           tty);
    }
  }
}


void MacroAssembler::g1_write_barrier_post(Register store_addr, Register new_val, Register tmp) {

  Label filtered;
  MacroAssembler* post_filter_masm = this;

  if (new_val == G0) return;

  G1SATBCardTableModRefBS* bs = (G1SATBCardTableModRefBS*) Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::G1SATBCT ||
         bs->kind() == BarrierSet::G1SATBCTLogging, "wrong barrier");

  if (G1RSBarrierRegionFilter) {
    xor3(store_addr, new_val, tmp);
#ifdef _LP64
    srlx(tmp, HeapRegion::LogOfHRGrainBytes, tmp);
#else
    srl(tmp, HeapRegion::LogOfHRGrainBytes, tmp);
#endif

    // XXX Should I predict this taken or not?  Does it matter?
    cmp_and_brx_short(tmp, G0, Assembler::equal, Assembler::pt, filtered);
  }

  // If the "store_addr" register is an "in" or "local" register, move it to
  // a scratch reg so we can pass it as an argument.
  bool use_scr = !(store_addr->is_global() || store_addr->is_out());
  // Pick a scratch register different from "tmp".
  Register scr = (tmp == G1_scratch ? G3_scratch : G1_scratch);
  // Make sure we use up the delay slot!
  if (use_scr) {
    post_filter_masm->mov(store_addr, scr);
  } else {
    post_filter_masm->nop();
  }
  generate_dirty_card_log_enqueue_if_necessary(bs->byte_map_base);
  save_frame(0);
  call(dirty_card_log_enqueue);
  if (use_scr) {
    delayed()->mov(scr, O0);
  } else {
    delayed()->mov(store_addr->after_save(), O0);
  }
  restore();

  bind(filtered);
}

#endif // INCLUDE_ALL_GCS
///////////////////////////////////////////////////////////////////////////////////

void MacroAssembler::card_write_barrier_post(Register store_addr, Register new_val, Register tmp) {
  // If we're writing constant NULL, we can skip the write barrier.
  if (new_val == G0) return;
  CardTableModRefBS* bs = (CardTableModRefBS*) Universe::heap()->barrier_set();
  assert(bs->kind() == BarrierSet::CardTableModRef ||
         bs->kind() == BarrierSet::CardTableExtension, "wrong barrier");
  card_table_write(bs->byte_map_base, tmp, store_addr);
}

void MacroAssembler::load_klass(Register src_oop, Register klass) {
  // The number of bytes in this code is used by
  // MachCallDynamicJavaNode::ret_addr_offset()
  // if this changes, change that.
  if (UseCompressedClassPointers) {
    lduw(src_oop, oopDesc::klass_offset_in_bytes(), klass);
    decode_klass_not_null(klass);
  } else {
    ld_ptr(src_oop, oopDesc::klass_offset_in_bytes(), klass);
  }
}

void MacroAssembler::store_klass(Register klass, Register dst_oop) {
  if (UseCompressedClassPointers) {
    assert(dst_oop != klass, "not enough registers");
    encode_klass_not_null(klass);
    st(klass, dst_oop, oopDesc::klass_offset_in_bytes());
  } else {
    st_ptr(klass, dst_oop, oopDesc::klass_offset_in_bytes());
  }
}

void MacroAssembler::store_klass_gap(Register s, Register d) {
  if (UseCompressedClassPointers) {
    assert(s != d, "not enough registers");
    st(s, d, oopDesc::klass_gap_offset_in_bytes());
  }
}

void MacroAssembler::load_heap_oop(const Address& s, Register d) {
  if (UseCompressedOops) {
    lduw(s, d);
    decode_heap_oop(d);
  } else {
    ld_ptr(s, d);
  }
}

void MacroAssembler::load_heap_oop(Register s1, Register s2, Register d) {
   if (UseCompressedOops) {
    lduw(s1, s2, d);
    decode_heap_oop(d, d);
  } else {
    ld_ptr(s1, s2, d);
  }
}

void MacroAssembler::load_heap_oop(Register s1, int simm13a, Register d) {
   if (UseCompressedOops) {
    lduw(s1, simm13a, d);
    decode_heap_oop(d, d);
  } else {
    ld_ptr(s1, simm13a, d);
  }
}

void MacroAssembler::load_heap_oop(Register s1, RegisterOrConstant s2, Register d) {
  if (s2.is_constant())  load_heap_oop(s1, s2.as_constant(), d);
  else                   load_heap_oop(s1, s2.as_register(), d);
}

void MacroAssembler::store_heap_oop(Register d, Register s1, Register s2) {
  if (UseCompressedOops) {
    assert(s1 != d && s2 != d, "not enough registers");
    encode_heap_oop(d);
    st(d, s1, s2);
  } else {
    st_ptr(d, s1, s2);
  }
}

void MacroAssembler::store_heap_oop(Register d, Register s1, int simm13a) {
  if (UseCompressedOops) {
    assert(s1 != d, "not enough registers");
    encode_heap_oop(d);
    st(d, s1, simm13a);
  } else {
    st_ptr(d, s1, simm13a);
  }
}

void MacroAssembler::store_heap_oop(Register d, const Address& a, int offset) {
  if (UseCompressedOops) {
    assert(a.base() != d, "not enough registers");
    encode_heap_oop(d);
    st(d, a, offset);
  } else {
    st_ptr(d, a, offset);
  }
}


void MacroAssembler::encode_heap_oop(Register src, Register dst) {
  assert (UseCompressedOops, "must be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
  verify_oop(src);
  if (Universe::narrow_oop_base() == NULL) {
    srlx(src, LogMinObjAlignmentInBytes, dst);
    return;
  }
  Label done;
  if (src == dst) {
    // optimize for frequent case src == dst
    bpr(rc_nz, true, Assembler::pt, src, done);
    delayed() -> sub(src, G6_heapbase, dst); // annuled if not taken
    bind(done);
    srlx(src, LogMinObjAlignmentInBytes, dst);
  } else {
    bpr(rc_z, false, Assembler::pn, src, done);
    delayed() -> mov(G0, dst);
    // could be moved before branch, and annulate delay,
    // but may add some unneeded work decoding null
    sub(src, G6_heapbase, dst);
    srlx(dst, LogMinObjAlignmentInBytes, dst);
    bind(done);
  }
}


void MacroAssembler::encode_heap_oop_not_null(Register r) {
  assert (UseCompressedOops, "must be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
  verify_oop(r);
  if (Universe::narrow_oop_base() != NULL)
    sub(r, G6_heapbase, r);
  srlx(r, LogMinObjAlignmentInBytes, r);
}

void MacroAssembler::encode_heap_oop_not_null(Register src, Register dst) {
  assert (UseCompressedOops, "must be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
  verify_oop(src);
  if (Universe::narrow_oop_base() == NULL) {
    srlx(src, LogMinObjAlignmentInBytes, dst);
  } else {
    sub(src, G6_heapbase, dst);
    srlx(dst, LogMinObjAlignmentInBytes, dst);
  }
}

// Same algorithm as oops.inline.hpp decode_heap_oop.
void  MacroAssembler::decode_heap_oop(Register src, Register dst) {
  assert (UseCompressedOops, "must be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
  sllx(src, LogMinObjAlignmentInBytes, dst);
  if (Universe::narrow_oop_base() != NULL) {
    Label done;
    bpr(rc_nz, true, Assembler::pt, dst, done);
    delayed() -> add(dst, G6_heapbase, dst); // annuled if not taken
    bind(done);
  }
  verify_oop(dst);
}

void  MacroAssembler::decode_heap_oop_not_null(Register r) {
  // Do not add assert code to this unless you change vtableStubs_sparc.cpp
  // pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  assert (UseCompressedOops, "must be compressed");
  assert (Universe::heap() != NULL, "java heap should be initialized");
  assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
  sllx(r, LogMinObjAlignmentInBytes, r);
  if (Universe::narrow_oop_base() != NULL)
    add(r, G6_heapbase, r);
}

void  MacroAssembler::decode_heap_oop_not_null(Register src, Register dst) {
  // Do not add assert code to this unless you change vtableStubs_sparc.cpp
  // pd_code_size_limit.
  // Also do not verify_oop as this is called by verify_oop.
  assert (UseCompressedOops, "must be compressed");
  assert (LogMinObjAlignmentInBytes == Universe::narrow_oop_shift(), "decode alg wrong");
  sllx(src, LogMinObjAlignmentInBytes, dst);
  if (Universe::narrow_oop_base() != NULL)
    add(dst, G6_heapbase, dst);
}

void MacroAssembler::encode_klass_not_null(Register r) {
  assert (UseCompressedClassPointers, "must be compressed");
  if (Universe::narrow_klass_base() != NULL) {
    assert(r != G6_heapbase, "bad register choice");
    set((intptr_t)Universe::narrow_klass_base(), G6_heapbase);
    sub(r, G6_heapbase, r);
    if (Universe::narrow_klass_shift() != 0) {
      assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift(), "decode alg wrong");
      srlx(r, LogKlassAlignmentInBytes, r);
    }
    reinit_heapbase();
  } else {
    assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift() || Universe::narrow_klass_shift() == 0, "decode alg wrong");
    srlx(r, Universe::narrow_klass_shift(), r);
  }
}

void MacroAssembler::encode_klass_not_null(Register src, Register dst) {
  if (src == dst) {
    encode_klass_not_null(src);
  } else {
    assert (UseCompressedClassPointers, "must be compressed");
    if (Universe::narrow_klass_base() != NULL) {
      set((intptr_t)Universe::narrow_klass_base(), dst);
      sub(src, dst, dst);
      if (Universe::narrow_klass_shift() != 0) {
        srlx(dst, LogKlassAlignmentInBytes, dst);
      }
    } else {
      // shift src into dst
      assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift() || Universe::narrow_klass_shift() == 0, "decode alg wrong");
      srlx(src, Universe::narrow_klass_shift(), dst);
    }
  }
}

// Function instr_size_for_decode_klass_not_null() counts the instructions
// generated by decode_klass_not_null() and reinit_heapbase().  Hence, if
// the instructions they generate change, then this method needs to be updated.
int MacroAssembler::instr_size_for_decode_klass_not_null() {
  assert (UseCompressedClassPointers, "only for compressed klass ptrs");
  int num_instrs = 1;  // shift src,dst or add
  if (Universe::narrow_klass_base() != NULL) {
    // set + add + set
    num_instrs += insts_for_internal_set((intptr_t)Universe::narrow_klass_base()) +
                  insts_for_internal_set((intptr_t)Universe::narrow_ptrs_base());
    if (Universe::narrow_klass_shift() != 0) {
      num_instrs += 1;  // sllx
    }
  }
  return num_instrs * BytesPerInstWord;
}

// !!! If the instructions that get generated here change then function
// instr_size_for_decode_klass_not_null() needs to get updated.
void  MacroAssembler::decode_klass_not_null(Register r) {
  // Do not add assert code to this unless you change vtableStubs_sparc.cpp
  // pd_code_size_limit.
  assert (UseCompressedClassPointers, "must be compressed");
  if (Universe::narrow_klass_base() != NULL) {
    assert(r != G6_heapbase, "bad register choice");
    set((intptr_t)Universe::narrow_klass_base(), G6_heapbase);
    if (Universe::narrow_klass_shift() != 0)
      sllx(r, LogKlassAlignmentInBytes, r);
    add(r, G6_heapbase, r);
    reinit_heapbase();
  } else {
    assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift() || Universe::narrow_klass_shift() == 0, "decode alg wrong");
    sllx(r, Universe::narrow_klass_shift(), r);
  }
}

void  MacroAssembler::decode_klass_not_null(Register src, Register dst) {
  if (src == dst) {
    decode_klass_not_null(src);
  } else {
    // Do not add assert code to this unless you change vtableStubs_sparc.cpp
    // pd_code_size_limit.
    assert (UseCompressedClassPointers, "must be compressed");
    if (Universe::narrow_klass_base() != NULL) {
      if (Universe::narrow_klass_shift() != 0) {
        assert((src != G6_heapbase) && (dst != G6_heapbase), "bad register choice");
        set((intptr_t)Universe::narrow_klass_base(), G6_heapbase);
        sllx(src, LogKlassAlignmentInBytes, dst);
        add(dst, G6_heapbase, dst);
        reinit_heapbase();
      } else {
        set((intptr_t)Universe::narrow_klass_base(), dst);
        add(src, dst, dst);
      }
    } else {
      // shift/mov src into dst.
      assert (LogKlassAlignmentInBytes == Universe::narrow_klass_shift() || Universe::narrow_klass_shift() == 0, "decode alg wrong");
      sllx(src, Universe::narrow_klass_shift(), dst);
    }
  }
}

void MacroAssembler::reinit_heapbase() {
  if (UseCompressedOops || UseCompressedClassPointers) {
    if (Universe::heap() != NULL) {
      set((intptr_t)Universe::narrow_ptrs_base(), G6_heapbase);
    } else {
      AddressLiteral base(Universe::narrow_ptrs_base_addr());
      load_ptr_contents(base, G6_heapbase);
    }
  }
}

// Compare char[] arrays aligned to 4 bytes.
void MacroAssembler::char_arrays_equals(Register ary1, Register ary2,
                                        Register limit, Register result,
                                        Register chr1, Register chr2, Label& Ldone) {
  Label Lvector, Lloop;
  assert(chr1 == result, "should be the same");

  // Note: limit contains number of bytes (2*char_elements) != 0.
  andcc(limit, 0x2, chr1); // trailing character ?
  br(Assembler::zero, false, Assembler::pt, Lvector);
  delayed()->nop();

  // compare the trailing char
  sub(limit, sizeof(jchar), limit);
  lduh(ary1, limit, chr1);
  lduh(ary2, limit, chr2);
  cmp(chr1, chr2);
  br(Assembler::notEqual, true, Assembler::pt, Ldone);
  delayed()->mov(G0, result);     // not equal

  // only one char ?
  cmp_zero_and_br(zero, limit, Ldone, true, Assembler::pn);
  delayed()->add(G0, 1, result); // zero-length arrays are equal

  // word by word compare, dont't need alignment check
  bind(Lvector);
  // Shift ary1 and ary2 to the end of the arrays, negate limit
  add(ary1, limit, ary1);
  add(ary2, limit, ary2);
  neg(limit, limit);

  lduw(ary1, limit, chr1);
  bind(Lloop);
  lduw(ary2, limit, chr2);
  cmp(chr1, chr2);
  br(Assembler::notEqual, true, Assembler::pt, Ldone);
  delayed()->mov(G0, result);     // not equal
  inccc(limit, 2*sizeof(jchar));
  // annul LDUW if branch is not taken to prevent access past end of array
  br(Assembler::notZero, true, Assembler::pt, Lloop);
  delayed()->lduw(ary1, limit, chr1); // hoisted

  // Caller should set it:
  // add(G0, 1, result); // equals
}

// Use BIS for zeroing (count is in bytes).
void MacroAssembler::bis_zeroing(Register to, Register count, Register temp, Label& Ldone) {
  assert(UseBlockZeroing && VM_Version::has_block_zeroing(), "only works with BIS zeroing");
  Register end = count;
  int cache_line_size = VM_Version::prefetch_data_size();
  // Minimum count when BIS zeroing can be used since
  // it needs membar which is expensive.
  int block_zero_size  = MAX2(cache_line_size*3, (int)BlockZeroingLowLimit);

  Label small_loop;
  // Check if count is negative (dead code) or zero.
  // Note, count uses 64bit in 64 bit VM.
  cmp_and_brx_short(count, 0, Assembler::lessEqual, Assembler::pn, Ldone);

  // Use BIS zeroing only for big arrays since it requires membar.
  if (Assembler::is_simm13(block_zero_size)) { // < 4096
    cmp(count, block_zero_size);
  } else {
    set(block_zero_size, temp);
    cmp(count, temp);
  }
  br(Assembler::lessUnsigned, false, Assembler::pt, small_loop);
  delayed()->add(to, count, end);

  // Note: size is >= three (32 bytes) cache lines.

  // Clean the beginning of space up to next cache line.
  for (int offs = 0; offs < cache_line_size; offs += 8) {
    stx(G0, to, offs);
  }

  // align to next cache line
  add(to, cache_line_size, to);
  and3(to, -cache_line_size, to);

  // Note: size left >= two (32 bytes) cache lines.

  // BIS should not be used to zero tail (64 bytes)
  // to avoid zeroing a header of the following object.
  sub(end, (cache_line_size*2)-8, end);

  Label bis_loop;
  bind(bis_loop);
  stxa(G0, to, G0, Assembler::ASI_ST_BLKINIT_PRIMARY);
  add(to, cache_line_size, to);
  cmp_and_brx_short(to, end, Assembler::lessUnsigned, Assembler::pt, bis_loop);

  // BIS needs membar.
  membar(Assembler::StoreLoad);

  add(end, (cache_line_size*2)-8, end); // restore end
  cmp_and_brx_short(to, end, Assembler::greaterEqualUnsigned, Assembler::pn, Ldone);

  // Clean the tail.
  bind(small_loop);
  stx(G0, to, 0);
  add(to, 8, to);
  cmp_and_brx_short(to, end, Assembler::lessUnsigned, Assembler::pt, small_loop);
  nop(); // Separate short branches
}
