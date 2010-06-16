/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_c1_LIRAssembler_x86.cpp.incl"


// These masks are used to provide 128-bit aligned bitmasks to the XMM
// instructions, to allow sign-masking or sign-bit flipping.  They allow
// fast versions of NegF/NegD and AbsF/AbsD.

// Note: 'double' and 'long long' have 32-bits alignment on x86.
static jlong* double_quadword(jlong *adr, jlong lo, jlong hi) {
  // Use the expression (adr)&(~0xF) to provide 128-bits aligned address
  // of 128-bits operands for SSE instructions.
  jlong *operand = (jlong*)(((long)adr)&((long)(~0xF)));
  // Store the value to a 128-bits operand.
  operand[0] = lo;
  operand[1] = hi;
  return operand;
}

// Buffer for 128-bits masks used by SSE instructions.
static jlong fp_signmask_pool[(4+1)*2]; // 4*128bits(data) + 128bits(alignment)

// Static initialization during VM startup.
static jlong *float_signmask_pool  = double_quadword(&fp_signmask_pool[1*2], CONST64(0x7FFFFFFF7FFFFFFF), CONST64(0x7FFFFFFF7FFFFFFF));
static jlong *double_signmask_pool = double_quadword(&fp_signmask_pool[2*2], CONST64(0x7FFFFFFFFFFFFFFF), CONST64(0x7FFFFFFFFFFFFFFF));
static jlong *float_signflip_pool  = double_quadword(&fp_signmask_pool[3*2], CONST64(0x8000000080000000), CONST64(0x8000000080000000));
static jlong *double_signflip_pool = double_quadword(&fp_signmask_pool[4*2], CONST64(0x8000000000000000), CONST64(0x8000000000000000));



NEEDS_CLEANUP // remove this definitions ?
const Register IC_Klass    = rax;   // where the IC klass is cached
const Register SYNC_header = rax;   // synchronization header
const Register SHIFT_count = rcx;   // where count for shift operations must be

#define __ _masm->


static void select_different_registers(Register preserve,
                                       Register extra,
                                       Register &tmp1,
                                       Register &tmp2) {
  if (tmp1 == preserve) {
    assert_different_registers(tmp1, tmp2, extra);
    tmp1 = extra;
  } else if (tmp2 == preserve) {
    assert_different_registers(tmp1, tmp2, extra);
    tmp2 = extra;
  }
  assert_different_registers(preserve, tmp1, tmp2);
}



static void select_different_registers(Register preserve,
                                       Register extra,
                                       Register &tmp1,
                                       Register &tmp2,
                                       Register &tmp3) {
  if (tmp1 == preserve) {
    assert_different_registers(tmp1, tmp2, tmp3, extra);
    tmp1 = extra;
  } else if (tmp2 == preserve) {
    assert_different_registers(tmp1, tmp2, tmp3, extra);
    tmp2 = extra;
  } else if (tmp3 == preserve) {
    assert_different_registers(tmp1, tmp2, tmp3, extra);
    tmp3 = extra;
  }
  assert_different_registers(preserve, tmp1, tmp2, tmp3);
}



bool LIR_Assembler::is_small_constant(LIR_Opr opr) {
  if (opr->is_constant()) {
    LIR_Const* constant = opr->as_constant_ptr();
    switch (constant->type()) {
      case T_INT: {
        return true;
      }

      default:
        return false;
    }
  }
  return false;
}


LIR_Opr LIR_Assembler::receiverOpr() {
  return FrameMap::receiver_opr;
}

LIR_Opr LIR_Assembler::incomingReceiverOpr() {
  return receiverOpr();
}

LIR_Opr LIR_Assembler::osrBufferPointer() {
  return FrameMap::as_pointer_opr(receiverOpr()->as_register());
}

//--------------fpu register translations-----------------------


address LIR_Assembler::float_constant(float f) {
  address const_addr = __ float_constant(f);
  if (const_addr == NULL) {
    bailout("const section overflow");
    return __ code()->consts()->start();
  } else {
    return const_addr;
  }
}


address LIR_Assembler::double_constant(double d) {
  address const_addr = __ double_constant(d);
  if (const_addr == NULL) {
    bailout("const section overflow");
    return __ code()->consts()->start();
  } else {
    return const_addr;
  }
}


void LIR_Assembler::set_24bit_FPU() {
  __ fldcw(ExternalAddress(StubRoutines::addr_fpu_cntrl_wrd_24()));
}

void LIR_Assembler::reset_FPU() {
  __ fldcw(ExternalAddress(StubRoutines::addr_fpu_cntrl_wrd_std()));
}

void LIR_Assembler::fpop() {
  __ fpop();
}

void LIR_Assembler::fxch(int i) {
  __ fxch(i);
}

void LIR_Assembler::fld(int i) {
  __ fld_s(i);
}

void LIR_Assembler::ffree(int i) {
  __ ffree(i);
}

void LIR_Assembler::breakpoint() {
  __ int3();
}

void LIR_Assembler::push(LIR_Opr opr) {
  if (opr->is_single_cpu()) {
    __ push_reg(opr->as_register());
  } else if (opr->is_double_cpu()) {
    NOT_LP64(__ push_reg(opr->as_register_hi()));
    __ push_reg(opr->as_register_lo());
  } else if (opr->is_stack()) {
    __ push_addr(frame_map()->address_for_slot(opr->single_stack_ix()));
  } else if (opr->is_constant()) {
    LIR_Const* const_opr = opr->as_constant_ptr();
    if (const_opr->type() == T_OBJECT) {
      __ push_oop(const_opr->as_jobject());
    } else if (const_opr->type() == T_INT) {
      __ push_jint(const_opr->as_jint());
    } else {
      ShouldNotReachHere();
    }

  } else {
    ShouldNotReachHere();
  }
}

void LIR_Assembler::pop(LIR_Opr opr) {
  if (opr->is_single_cpu()) {
    __ pop_reg(opr->as_register());
  } else {
    ShouldNotReachHere();
  }
}

bool LIR_Assembler::is_literal_address(LIR_Address* addr) {
  return addr->base()->is_illegal() && addr->index()->is_illegal();
}

//-------------------------------------------

Address LIR_Assembler::as_Address(LIR_Address* addr) {
  return as_Address(addr, rscratch1);
}

Address LIR_Assembler::as_Address(LIR_Address* addr, Register tmp) {
  if (addr->base()->is_illegal()) {
    assert(addr->index()->is_illegal(), "must be illegal too");
    AddressLiteral laddr((address)addr->disp(), relocInfo::none);
    if (! __ reachable(laddr)) {
      __ movptr(tmp, laddr.addr());
      Address res(tmp, 0);
      return res;
    } else {
      return __ as_Address(laddr);
    }
  }

  Register base = addr->base()->as_pointer_register();

  if (addr->index()->is_illegal()) {
    return Address( base, addr->disp());
  } else if (addr->index()->is_cpu_register()) {
    Register index = addr->index()->as_pointer_register();
    return Address(base, index, (Address::ScaleFactor) addr->scale(), addr->disp());
  } else if (addr->index()->is_constant()) {
    intptr_t addr_offset = (addr->index()->as_constant_ptr()->as_jint() << addr->scale()) + addr->disp();
    assert(Assembler::is_simm32(addr_offset), "must be");

    return Address(base, addr_offset);
  } else {
    Unimplemented();
    return Address();
  }
}


Address LIR_Assembler::as_Address_hi(LIR_Address* addr) {
  Address base = as_Address(addr);
  return Address(base._base, base._index, base._scale, base._disp + BytesPerWord);
}


Address LIR_Assembler::as_Address_lo(LIR_Address* addr) {
  return as_Address(addr);
}


void LIR_Assembler::osr_entry() {
  offsets()->set_value(CodeOffsets::OSR_Entry, code_offset());
  BlockBegin* osr_entry = compilation()->hir()->osr_entry();
  ValueStack* entry_state = osr_entry->state();
  int number_of_locks = entry_state->locks_size();

  // we jump here if osr happens with the interpreter
  // state set up to continue at the beginning of the
  // loop that triggered osr - in particular, we have
  // the following registers setup:
  //
  // rcx: osr buffer
  //

  // build frame
  ciMethod* m = compilation()->method();
  __ build_frame(initial_frame_size_in_bytes());

  // OSR buffer is
  //
  // locals[nlocals-1..0]
  // monitors[0..number_of_locks]
  //
  // locals is a direct copy of the interpreter frame so in the osr buffer
  // so first slot in the local array is the last local from the interpreter
  // and last slot is local[0] (receiver) from the interpreter
  //
  // Similarly with locks. The first lock slot in the osr buffer is the nth lock
  // from the interpreter frame, the nth lock slot in the osr buffer is 0th lock
  // in the interpreter frame (the method lock if a sync method)

  // Initialize monitors in the compiled activation.
  //   rcx: pointer to osr buffer
  //
  // All other registers are dead at this point and the locals will be
  // copied into place by code emitted in the IR.

  Register OSR_buf = osrBufferPointer()->as_pointer_register();
  { assert(frame::interpreter_frame_monitor_size() == BasicObjectLock::size(), "adjust code below");
    int monitor_offset = BytesPerWord * method()->max_locals() +
      (2 * BytesPerWord) * (number_of_locks - 1);
    // SharedRuntime::OSR_migration_begin() packs BasicObjectLocks in
    // the OSR buffer using 2 word entries: first the lock and then
    // the oop.
    for (int i = 0; i < number_of_locks; i++) {
      int slot_offset = monitor_offset - ((i * 2) * BytesPerWord);
#ifdef ASSERT
      // verify the interpreter's monitor has a non-null object
      {
        Label L;
        __ cmpptr(Address(OSR_buf, slot_offset + 1*BytesPerWord), (int32_t)NULL_WORD);
        __ jcc(Assembler::notZero, L);
        __ stop("locked object is NULL");
        __ bind(L);
      }
#endif
      __ movptr(rbx, Address(OSR_buf, slot_offset + 0));
      __ movptr(frame_map()->address_for_monitor_lock(i), rbx);
      __ movptr(rbx, Address(OSR_buf, slot_offset + 1*BytesPerWord));
      __ movptr(frame_map()->address_for_monitor_object(i), rbx);
    }
  }
}


// inline cache check; done before the frame is built.
int LIR_Assembler::check_icache() {
  Register receiver = FrameMap::receiver_opr->as_register();
  Register ic_klass = IC_Klass;
  const int ic_cmp_size = LP64_ONLY(10) NOT_LP64(9);

  if (!VerifyOops) {
    // insert some nops so that the verified entry point is aligned on CodeEntryAlignment
    while ((__ offset() + ic_cmp_size) % CodeEntryAlignment != 0) {
      __ nop();
    }
  }
  int offset = __ offset();
  __ inline_cache_check(receiver, IC_Klass);
  assert(__ offset() % CodeEntryAlignment == 0 || VerifyOops, "alignment must be correct");
  if (VerifyOops) {
    // force alignment after the cache check.
    // It's been verified to be aligned if !VerifyOops
    __ align(CodeEntryAlignment);
  }
  return offset;
}


void LIR_Assembler::jobject2reg_with_patching(Register reg, CodeEmitInfo* info) {
  jobject o = NULL;
  PatchingStub* patch = new PatchingStub(_masm, PatchingStub::load_klass_id);
  __ movoop(reg, o);
  patching_epilog(patch, lir_patch_normal, reg, info);
}


void LIR_Assembler::monitorexit(LIR_Opr obj_opr, LIR_Opr lock_opr, Register new_hdr, int monitor_no, Register exception) {
  if (exception->is_valid()) {
    // preserve exception
    // note: the monitor_exit runtime call is a leaf routine
    //       and cannot block => no GC can happen
    // The slow case (MonitorAccessStub) uses the first two stack slots
    // ([esp+0] and [esp+4]), therefore we store the exception at [esp+8]
    __ movptr (Address(rsp, 2*wordSize), exception);
  }

  Register obj_reg  = obj_opr->as_register();
  Register lock_reg = lock_opr->as_register();

  // setup registers (lock_reg must be rax, for lock_object)
  assert(obj_reg != SYNC_header && lock_reg != SYNC_header, "rax, must be available here");
  Register hdr = lock_reg;
  assert(new_hdr == SYNC_header, "wrong register");
  lock_reg = new_hdr;
  // compute pointer to BasicLock
  Address lock_addr = frame_map()->address_for_monitor_lock(monitor_no);
  __ lea(lock_reg, lock_addr);
  // unlock object
  MonitorAccessStub* slow_case = new MonitorExitStub(lock_opr, true, monitor_no);
  // _slow_case_stubs->append(slow_case);
  // temporary fix: must be created after exceptionhandler, therefore as call stub
  _slow_case_stubs->append(slow_case);
  if (UseFastLocking) {
    // try inlined fast unlocking first, revert to slow locking if it fails
    // note: lock_reg points to the displaced header since the displaced header offset is 0!
    assert(BasicLock::displaced_header_offset_in_bytes() == 0, "lock_reg must point to the displaced header");
    __ unlock_object(hdr, obj_reg, lock_reg, *slow_case->entry());
  } else {
    // always do slow unlocking
    // note: the slow unlocking code could be inlined here, however if we use
    //       slow unlocking, speed doesn't matter anyway and this solution is
    //       simpler and requires less duplicated code - additionally, the
    //       slow unlocking code is the same in either case which simplifies
    //       debugging
    __ jmp(*slow_case->entry());
  }
  // done
  __ bind(*slow_case->continuation());

  if (exception->is_valid()) {
    // restore exception
    __ movptr (exception, Address(rsp, 2 * wordSize));
  }
}

// This specifies the rsp decrement needed to build the frame
int LIR_Assembler::initial_frame_size_in_bytes() {
  // if rounding, must let FrameMap know!

  // The frame_map records size in slots (32bit word)

  // subtract two words to account for return address and link
  return (frame_map()->framesize() - (2*VMRegImpl::slots_per_word))  * VMRegImpl::stack_slot_size;
}


int LIR_Assembler::emit_exception_handler() {
  // if the last instruction is a call (typically to do a throw which
  // is coming at the end after block reordering) the return address
  // must still point into the code area in order to avoid assertion
  // failures when searching for the corresponding bci => add a nop
  // (was bug 5/14/1999 - gri)
  __ nop();

  // generate code for exception handler
  address handler_base = __ start_a_stub(exception_handler_size);
  if (handler_base == NULL) {
    // not enough space left for the handler
    bailout("exception handler overflow");
    return -1;
  }

  int offset = code_offset();

  // the exception oop and pc are in rax, and rdx
  // no other registers need to be preserved, so invalidate them
  __ invalidate_registers(false, true, true, false, true, true);

  // check that there is really an exception
  __ verify_not_null_oop(rax);

  // search an exception handler (rax: exception oop, rdx: throwing pc)
  __ call(RuntimeAddress(Runtime1::entry_for(Runtime1::handle_exception_nofpu_id)));

  __ stop("should not reach here");

  assert(code_offset() - offset <= exception_handler_size, "overflow");
  __ end_a_stub();

  return offset;
}


// Emit the code to remove the frame from the stack in the exception
// unwind path.
int LIR_Assembler::emit_unwind_handler() {
#ifndef PRODUCT
  if (CommentedAssembly) {
    _masm->block_comment("Unwind handler");
  }
#endif

  int offset = code_offset();

  // Fetch the exception from TLS and clear out exception related thread state
  __ get_thread(rsi);
  __ movptr(rax, Address(rsi, JavaThread::exception_oop_offset()));
  __ movptr(Address(rsi, JavaThread::exception_oop_offset()), (int32_t)NULL_WORD);
  __ movptr(Address(rsi, JavaThread::exception_pc_offset()), (int32_t)NULL_WORD);

  __ bind(_unwind_handler_entry);
  __ verify_not_null_oop(rax);
  if (method()->is_synchronized() || compilation()->env()->dtrace_method_probes()) {
    __ mov(rsi, rax);  // Preserve the exception
  }

  // Preform needed unlocking
  MonitorExitStub* stub = NULL;
  if (method()->is_synchronized()) {
    monitor_address(0, FrameMap::rax_opr);
    stub = new MonitorExitStub(FrameMap::rax_opr, true, 0);
    __ unlock_object(rdi, rbx, rax, *stub->entry());
    __ bind(*stub->continuation());
  }

  if (compilation()->env()->dtrace_method_probes()) {
    __ movoop(Address(rsp, 0), method()->constant_encoding());
    __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_exit)));
  }

  if (method()->is_synchronized() || compilation()->env()->dtrace_method_probes()) {
    __ mov(rax, rsi);  // Restore the exception
  }

  // remove the activation and dispatch to the unwind handler
  __ remove_frame(initial_frame_size_in_bytes());
  __ jump(RuntimeAddress(Runtime1::entry_for(Runtime1::unwind_exception_id)));

  // Emit the slow path assembly
  if (stub != NULL) {
    stub->emit_code(this);
  }

  return offset;
}


int LIR_Assembler::emit_deopt_handler() {
  // if the last instruction is a call (typically to do a throw which
  // is coming at the end after block reordering) the return address
  // must still point into the code area in order to avoid assertion
  // failures when searching for the corresponding bci => add a nop
  // (was bug 5/14/1999 - gri)
  __ nop();

  // generate code for exception handler
  address handler_base = __ start_a_stub(deopt_handler_size);
  if (handler_base == NULL) {
    // not enough space left for the handler
    bailout("deopt handler overflow");
    return -1;
  }

  int offset = code_offset();
  InternalAddress here(__ pc());

  __ pushptr(here.addr());
  __ jump(RuntimeAddress(SharedRuntime::deopt_blob()->unpack()));

  assert(code_offset() - offset <= deopt_handler_size, "overflow");
  __ end_a_stub();

  return offset;
}


// This is the fast version of java.lang.String.compare; it has not
// OSR-entry and therefore, we generate a slow version for OSR's
void LIR_Assembler::emit_string_compare(LIR_Opr arg0, LIR_Opr arg1, LIR_Opr dst, CodeEmitInfo* info) {
  __ movptr (rbx, rcx); // receiver is in rcx
  __ movptr (rax, arg1->as_register());

  // Get addresses of first characters from both Strings
  __ movptr (rsi, Address(rax, java_lang_String::value_offset_in_bytes()));
  __ movptr (rcx, Address(rax, java_lang_String::offset_offset_in_bytes()));
  __ lea    (rsi, Address(rsi, rcx, Address::times_2, arrayOopDesc::base_offset_in_bytes(T_CHAR)));


  // rbx, may be NULL
  add_debug_info_for_null_check_here(info);
  __ movptr (rdi, Address(rbx, java_lang_String::value_offset_in_bytes()));
  __ movptr (rcx, Address(rbx, java_lang_String::offset_offset_in_bytes()));
  __ lea    (rdi, Address(rdi, rcx, Address::times_2, arrayOopDesc::base_offset_in_bytes(T_CHAR)));

  // compute minimum length (in rax) and difference of lengths (on top of stack)
  if (VM_Version::supports_cmov()) {
    __ movl     (rbx, Address(rbx, java_lang_String::count_offset_in_bytes()));
    __ movl     (rax, Address(rax, java_lang_String::count_offset_in_bytes()));
    __ mov      (rcx, rbx);
    __ subptr   (rbx, rax); // subtract lengths
    __ push     (rbx);      // result
    __ cmov     (Assembler::lessEqual, rax, rcx);
  } else {
    Label L;
    __ movl     (rbx, Address(rbx, java_lang_String::count_offset_in_bytes()));
    __ movl     (rcx, Address(rax, java_lang_String::count_offset_in_bytes()));
    __ mov      (rax, rbx);
    __ subptr   (rbx, rcx);
    __ push     (rbx);
    __ jcc      (Assembler::lessEqual, L);
    __ mov      (rax, rcx);
    __ bind (L);
  }
  // is minimum length 0?
  Label noLoop, haveResult;
  __ testptr (rax, rax);
  __ jcc (Assembler::zero, noLoop);

  // compare first characters
  __ load_unsigned_short(rcx, Address(rdi, 0));
  __ load_unsigned_short(rbx, Address(rsi, 0));
  __ subl(rcx, rbx);
  __ jcc(Assembler::notZero, haveResult);
  // starting loop
  __ decrement(rax); // we already tested index: skip one
  __ jcc(Assembler::zero, noLoop);

  // set rsi.edi to the end of the arrays (arrays have same length)
  // negate the index

  __ lea(rsi, Address(rsi, rax, Address::times_2, type2aelembytes(T_CHAR)));
  __ lea(rdi, Address(rdi, rax, Address::times_2, type2aelembytes(T_CHAR)));
  __ negptr(rax);

  // compare the strings in a loop

  Label loop;
  __ align(wordSize);
  __ bind(loop);
  __ load_unsigned_short(rcx, Address(rdi, rax, Address::times_2, 0));
  __ load_unsigned_short(rbx, Address(rsi, rax, Address::times_2, 0));
  __ subl(rcx, rbx);
  __ jcc(Assembler::notZero, haveResult);
  __ increment(rax);
  __ jcc(Assembler::notZero, loop);

  // strings are equal up to min length

  __ bind(noLoop);
  __ pop(rax);
  return_op(LIR_OprFact::illegalOpr);

  __ bind(haveResult);
  // leave instruction is going to discard the TOS value
  __ mov (rax, rcx); // result of call is in rax,
}


void LIR_Assembler::return_op(LIR_Opr result) {
  assert(result->is_illegal() || !result->is_single_cpu() || result->as_register() == rax, "word returns are in rax,");
  if (!result->is_illegal() && result->is_float_kind() && !result->is_xmm_register()) {
    assert(result->fpu() == 0, "result must already be on TOS");
  }

  // Pop the stack before the safepoint code
  __ remove_frame(initial_frame_size_in_bytes());

  bool result_is_oop = result->is_valid() ? result->is_oop() : false;

  // Note: we do not need to round double result; float result has the right precision
  // the poll sets the condition code, but no data registers
  AddressLiteral polling_page(os::get_polling_page() + (SafepointPollOffset % os::vm_page_size()),
                              relocInfo::poll_return_type);

  // NOTE: the requires that the polling page be reachable else the reloc
  // goes to the movq that loads the address and not the faulting instruction
  // which breaks the signal handler code

  __ test32(rax, polling_page);

  __ ret(0);
}


int LIR_Assembler::safepoint_poll(LIR_Opr tmp, CodeEmitInfo* info) {
  AddressLiteral polling_page(os::get_polling_page() + (SafepointPollOffset % os::vm_page_size()),
                              relocInfo::poll_type);

  if (info != NULL) {
    add_debug_info_for_branch(info);
  } else {
    ShouldNotReachHere();
  }

  int offset = __ offset();

  // NOTE: the requires that the polling page be reachable else the reloc
  // goes to the movq that loads the address and not the faulting instruction
  // which breaks the signal handler code

  __ test32(rax, polling_page);
  return offset;
}


void LIR_Assembler::move_regs(Register from_reg, Register to_reg) {
  if (from_reg != to_reg) __ mov(to_reg, from_reg);
}

void LIR_Assembler::swap_reg(Register a, Register b) {
  __ xchgptr(a, b);
}


void LIR_Assembler::const2reg(LIR_Opr src, LIR_Opr dest, LIR_PatchCode patch_code, CodeEmitInfo* info) {
  assert(src->is_constant(), "should not call otherwise");
  assert(dest->is_register(), "should not call otherwise");
  LIR_Const* c = src->as_constant_ptr();

  switch (c->type()) {
    case T_INT:
    case T_ADDRESS: {
      assert(patch_code == lir_patch_none, "no patching handled here");
      __ movl(dest->as_register(), c->as_jint());
      break;
    }

    case T_LONG: {
      assert(patch_code == lir_patch_none, "no patching handled here");
#ifdef _LP64
      __ movptr(dest->as_register_lo(), (intptr_t)c->as_jlong());
#else
      __ movptr(dest->as_register_lo(), c->as_jint_lo());
      __ movptr(dest->as_register_hi(), c->as_jint_hi());
#endif // _LP64
      break;
    }

    case T_OBJECT: {
      if (patch_code != lir_patch_none) {
        jobject2reg_with_patching(dest->as_register(), info);
      } else {
        __ movoop(dest->as_register(), c->as_jobject());
      }
      break;
    }

    case T_FLOAT: {
      if (dest->is_single_xmm()) {
        if (c->is_zero_float()) {
          __ xorps(dest->as_xmm_float_reg(), dest->as_xmm_float_reg());
        } else {
          __ movflt(dest->as_xmm_float_reg(),
                   InternalAddress(float_constant(c->as_jfloat())));
        }
      } else {
        assert(dest->is_single_fpu(), "must be");
        assert(dest->fpu_regnr() == 0, "dest must be TOS");
        if (c->is_zero_float()) {
          __ fldz();
        } else if (c->is_one_float()) {
          __ fld1();
        } else {
          __ fld_s (InternalAddress(float_constant(c->as_jfloat())));
        }
      }
      break;
    }

    case T_DOUBLE: {
      if (dest->is_double_xmm()) {
        if (c->is_zero_double()) {
          __ xorpd(dest->as_xmm_double_reg(), dest->as_xmm_double_reg());
        } else {
          __ movdbl(dest->as_xmm_double_reg(),
                    InternalAddress(double_constant(c->as_jdouble())));
        }
      } else {
        assert(dest->is_double_fpu(), "must be");
        assert(dest->fpu_regnrLo() == 0, "dest must be TOS");
        if (c->is_zero_double()) {
          __ fldz();
        } else if (c->is_one_double()) {
          __ fld1();
        } else {
          __ fld_d (InternalAddress(double_constant(c->as_jdouble())));
        }
      }
      break;
    }

    default:
      ShouldNotReachHere();
  }
}

void LIR_Assembler::const2stack(LIR_Opr src, LIR_Opr dest) {
  assert(src->is_constant(), "should not call otherwise");
  assert(dest->is_stack(), "should not call otherwise");
  LIR_Const* c = src->as_constant_ptr();

  switch (c->type()) {
    case T_INT:  // fall through
    case T_FLOAT:
    case T_ADDRESS:
      __ movl(frame_map()->address_for_slot(dest->single_stack_ix()), c->as_jint_bits());
      break;

    case T_OBJECT:
      __ movoop(frame_map()->address_for_slot(dest->single_stack_ix()), c->as_jobject());
      break;

    case T_LONG:  // fall through
    case T_DOUBLE:
#ifdef _LP64
      __ movptr(frame_map()->address_for_slot(dest->double_stack_ix(),
                                            lo_word_offset_in_bytes), (intptr_t)c->as_jlong_bits());
#else
      __ movptr(frame_map()->address_for_slot(dest->double_stack_ix(),
                                              lo_word_offset_in_bytes), c->as_jint_lo_bits());
      __ movptr(frame_map()->address_for_slot(dest->double_stack_ix(),
                                              hi_word_offset_in_bytes), c->as_jint_hi_bits());
#endif // _LP64
      break;

    default:
      ShouldNotReachHere();
  }
}

void LIR_Assembler::const2mem(LIR_Opr src, LIR_Opr dest, BasicType type, CodeEmitInfo* info ) {
  assert(src->is_constant(), "should not call otherwise");
  assert(dest->is_address(), "should not call otherwise");
  LIR_Const* c = src->as_constant_ptr();
  LIR_Address* addr = dest->as_address_ptr();

  int null_check_here = code_offset();
  switch (type) {
    case T_INT:    // fall through
    case T_FLOAT:
    case T_ADDRESS:
      __ movl(as_Address(addr), c->as_jint_bits());
      break;

    case T_OBJECT:  // fall through
    case T_ARRAY:
      if (c->as_jobject() == NULL) {
        __ movptr(as_Address(addr), NULL_WORD);
      } else {
        if (is_literal_address(addr)) {
          ShouldNotReachHere();
          __ movoop(as_Address(addr, noreg), c->as_jobject());
        } else {
#ifdef _LP64
          __ movoop(rscratch1, c->as_jobject());
          null_check_here = code_offset();
          __ movptr(as_Address_lo(addr), rscratch1);
#else
          __ movoop(as_Address(addr), c->as_jobject());
#endif
        }
      }
      break;

    case T_LONG:    // fall through
    case T_DOUBLE:
#ifdef _LP64
      if (is_literal_address(addr)) {
        ShouldNotReachHere();
        __ movptr(as_Address(addr, r15_thread), (intptr_t)c->as_jlong_bits());
      } else {
        __ movptr(r10, (intptr_t)c->as_jlong_bits());
        null_check_here = code_offset();
        __ movptr(as_Address_lo(addr), r10);
      }
#else
      // Always reachable in 32bit so this doesn't produce useless move literal
      __ movptr(as_Address_hi(addr), c->as_jint_hi_bits());
      __ movptr(as_Address_lo(addr), c->as_jint_lo_bits());
#endif // _LP64
      break;

    case T_BOOLEAN: // fall through
    case T_BYTE:
      __ movb(as_Address(addr), c->as_jint() & 0xFF);
      break;

    case T_CHAR:    // fall through
    case T_SHORT:
      __ movw(as_Address(addr), c->as_jint() & 0xFFFF);
      break;

    default:
      ShouldNotReachHere();
  };

  if (info != NULL) {
    add_debug_info_for_null_check(null_check_here, info);
  }
}


void LIR_Assembler::reg2reg(LIR_Opr src, LIR_Opr dest) {
  assert(src->is_register(), "should not call otherwise");
  assert(dest->is_register(), "should not call otherwise");

  // move between cpu-registers
  if (dest->is_single_cpu()) {
#ifdef _LP64
    if (src->type() == T_LONG) {
      // Can do LONG -> OBJECT
      move_regs(src->as_register_lo(), dest->as_register());
      return;
    }
#endif
    assert(src->is_single_cpu(), "must match");
    if (src->type() == T_OBJECT) {
      __ verify_oop(src->as_register());
    }
    move_regs(src->as_register(), dest->as_register());

  } else if (dest->is_double_cpu()) {
#ifdef _LP64
    if (src->type() == T_OBJECT || src->type() == T_ARRAY) {
      // Surprising to me but we can see move of a long to t_object
      __ verify_oop(src->as_register());
      move_regs(src->as_register(), dest->as_register_lo());
      return;
    }
#endif
    assert(src->is_double_cpu(), "must match");
    Register f_lo = src->as_register_lo();
    Register f_hi = src->as_register_hi();
    Register t_lo = dest->as_register_lo();
    Register t_hi = dest->as_register_hi();
#ifdef _LP64
    assert(f_hi == f_lo, "must be same");
    assert(t_hi == t_lo, "must be same");
    move_regs(f_lo, t_lo);
#else
    assert(f_lo != f_hi && t_lo != t_hi, "invalid register allocation");


    if (f_lo == t_hi && f_hi == t_lo) {
      swap_reg(f_lo, f_hi);
    } else if (f_hi == t_lo) {
      assert(f_lo != t_hi, "overwriting register");
      move_regs(f_hi, t_hi);
      move_regs(f_lo, t_lo);
    } else {
      assert(f_hi != t_lo, "overwriting register");
      move_regs(f_lo, t_lo);
      move_regs(f_hi, t_hi);
    }
#endif // LP64

    // special moves from fpu-register to xmm-register
    // necessary for method results
  } else if (src->is_single_xmm() && !dest->is_single_xmm()) {
    __ movflt(Address(rsp, 0), src->as_xmm_float_reg());
    __ fld_s(Address(rsp, 0));
  } else if (src->is_double_xmm() && !dest->is_double_xmm()) {
    __ movdbl(Address(rsp, 0), src->as_xmm_double_reg());
    __ fld_d(Address(rsp, 0));
  } else if (dest->is_single_xmm() && !src->is_single_xmm()) {
    __ fstp_s(Address(rsp, 0));
    __ movflt(dest->as_xmm_float_reg(), Address(rsp, 0));
  } else if (dest->is_double_xmm() && !src->is_double_xmm()) {
    __ fstp_d(Address(rsp, 0));
    __ movdbl(dest->as_xmm_double_reg(), Address(rsp, 0));

    // move between xmm-registers
  } else if (dest->is_single_xmm()) {
    assert(src->is_single_xmm(), "must match");
    __ movflt(dest->as_xmm_float_reg(), src->as_xmm_float_reg());
  } else if (dest->is_double_xmm()) {
    assert(src->is_double_xmm(), "must match");
    __ movdbl(dest->as_xmm_double_reg(), src->as_xmm_double_reg());

    // move between fpu-registers (no instruction necessary because of fpu-stack)
  } else if (dest->is_single_fpu() || dest->is_double_fpu()) {
    assert(src->is_single_fpu() || src->is_double_fpu(), "must match");
    assert(src->fpu() == dest->fpu(), "currently should be nothing to do");
  } else {
    ShouldNotReachHere();
  }
}

void LIR_Assembler::reg2stack(LIR_Opr src, LIR_Opr dest, BasicType type, bool pop_fpu_stack) {
  assert(src->is_register(), "should not call otherwise");
  assert(dest->is_stack(), "should not call otherwise");

  if (src->is_single_cpu()) {
    Address dst = frame_map()->address_for_slot(dest->single_stack_ix());
    if (type == T_OBJECT || type == T_ARRAY) {
      __ verify_oop(src->as_register());
      __ movptr (dst, src->as_register());
    } else {
      __ movl (dst, src->as_register());
    }

  } else if (src->is_double_cpu()) {
    Address dstLO = frame_map()->address_for_slot(dest->double_stack_ix(), lo_word_offset_in_bytes);
    Address dstHI = frame_map()->address_for_slot(dest->double_stack_ix(), hi_word_offset_in_bytes);
    __ movptr (dstLO, src->as_register_lo());
    NOT_LP64(__ movptr (dstHI, src->as_register_hi()));

  } else if (src->is_single_xmm()) {
    Address dst_addr = frame_map()->address_for_slot(dest->single_stack_ix());
    __ movflt(dst_addr, src->as_xmm_float_reg());

  } else if (src->is_double_xmm()) {
    Address dst_addr = frame_map()->address_for_slot(dest->double_stack_ix());
    __ movdbl(dst_addr, src->as_xmm_double_reg());

  } else if (src->is_single_fpu()) {
    assert(src->fpu_regnr() == 0, "argument must be on TOS");
    Address dst_addr = frame_map()->address_for_slot(dest->single_stack_ix());
    if (pop_fpu_stack)     __ fstp_s (dst_addr);
    else                   __ fst_s  (dst_addr);

  } else if (src->is_double_fpu()) {
    assert(src->fpu_regnrLo() == 0, "argument must be on TOS");
    Address dst_addr = frame_map()->address_for_slot(dest->double_stack_ix());
    if (pop_fpu_stack)     __ fstp_d (dst_addr);
    else                   __ fst_d  (dst_addr);

  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::reg2mem(LIR_Opr src, LIR_Opr dest, BasicType type, LIR_PatchCode patch_code, CodeEmitInfo* info, bool pop_fpu_stack, bool /* unaligned */) {
  LIR_Address* to_addr = dest->as_address_ptr();
  PatchingStub* patch = NULL;

  if (type == T_ARRAY || type == T_OBJECT) {
    __ verify_oop(src->as_register());
  }
  if (patch_code != lir_patch_none) {
    patch = new PatchingStub(_masm, PatchingStub::access_field_id);
    Address toa = as_Address(to_addr);
    assert(toa.disp() != 0, "must have");
  }
  if (info != NULL) {
    add_debug_info_for_null_check_here(info);
  }

  switch (type) {
    case T_FLOAT: {
      if (src->is_single_xmm()) {
        __ movflt(as_Address(to_addr), src->as_xmm_float_reg());
      } else {
        assert(src->is_single_fpu(), "must be");
        assert(src->fpu_regnr() == 0, "argument must be on TOS");
        if (pop_fpu_stack)      __ fstp_s(as_Address(to_addr));
        else                    __ fst_s (as_Address(to_addr));
      }
      break;
    }

    case T_DOUBLE: {
      if (src->is_double_xmm()) {
        __ movdbl(as_Address(to_addr), src->as_xmm_double_reg());
      } else {
        assert(src->is_double_fpu(), "must be");
        assert(src->fpu_regnrLo() == 0, "argument must be on TOS");
        if (pop_fpu_stack)      __ fstp_d(as_Address(to_addr));
        else                    __ fst_d (as_Address(to_addr));
      }
      break;
    }

    case T_ADDRESS: // fall through
    case T_ARRAY:   // fall through
    case T_OBJECT:  // fall through
#ifdef _LP64
      __ movptr(as_Address(to_addr), src->as_register());
      break;
#endif // _LP64
    case T_INT:
      __ movl(as_Address(to_addr), src->as_register());
      break;

    case T_LONG: {
      Register from_lo = src->as_register_lo();
      Register from_hi = src->as_register_hi();
#ifdef _LP64
      __ movptr(as_Address_lo(to_addr), from_lo);
#else
      Register base = to_addr->base()->as_register();
      Register index = noreg;
      if (to_addr->index()->is_register()) {
        index = to_addr->index()->as_register();
      }
      if (base == from_lo || index == from_lo) {
        assert(base != from_hi, "can't be");
        assert(index == noreg || (index != base && index != from_hi), "can't handle this");
        __ movl(as_Address_hi(to_addr), from_hi);
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_high, base, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_low;
        }
        __ movl(as_Address_lo(to_addr), from_lo);
      } else {
        assert(index == noreg || (index != base && index != from_lo), "can't handle this");
        __ movl(as_Address_lo(to_addr), from_lo);
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_low, base, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_high;
        }
        __ movl(as_Address_hi(to_addr), from_hi);
      }
#endif // _LP64
      break;
    }

    case T_BYTE:    // fall through
    case T_BOOLEAN: {
      Register src_reg = src->as_register();
      Address dst_addr = as_Address(to_addr);
      assert(VM_Version::is_P6() || src_reg->has_byte_register(), "must use byte registers if not P6");
      __ movb(dst_addr, src_reg);
      break;
    }

    case T_CHAR:    // fall through
    case T_SHORT:
      __ movw(as_Address(to_addr), src->as_register());
      break;

    default:
      ShouldNotReachHere();
  }

  if (patch_code != lir_patch_none) {
    patching_epilog(patch, patch_code, to_addr->base()->as_register(), info);
  }
}


void LIR_Assembler::stack2reg(LIR_Opr src, LIR_Opr dest, BasicType type) {
  assert(src->is_stack(), "should not call otherwise");
  assert(dest->is_register(), "should not call otherwise");

  if (dest->is_single_cpu()) {
    if (type == T_ARRAY || type == T_OBJECT) {
      __ movptr(dest->as_register(), frame_map()->address_for_slot(src->single_stack_ix()));
      __ verify_oop(dest->as_register());
    } else {
      __ movl(dest->as_register(), frame_map()->address_for_slot(src->single_stack_ix()));
    }

  } else if (dest->is_double_cpu()) {
    Address src_addr_LO = frame_map()->address_for_slot(src->double_stack_ix(), lo_word_offset_in_bytes);
    Address src_addr_HI = frame_map()->address_for_slot(src->double_stack_ix(), hi_word_offset_in_bytes);
    __ movptr(dest->as_register_lo(), src_addr_LO);
    NOT_LP64(__ movptr(dest->as_register_hi(), src_addr_HI));

  } else if (dest->is_single_xmm()) {
    Address src_addr = frame_map()->address_for_slot(src->single_stack_ix());
    __ movflt(dest->as_xmm_float_reg(), src_addr);

  } else if (dest->is_double_xmm()) {
    Address src_addr = frame_map()->address_for_slot(src->double_stack_ix());
    __ movdbl(dest->as_xmm_double_reg(), src_addr);

  } else if (dest->is_single_fpu()) {
    assert(dest->fpu_regnr() == 0, "dest must be TOS");
    Address src_addr = frame_map()->address_for_slot(src->single_stack_ix());
    __ fld_s(src_addr);

  } else if (dest->is_double_fpu()) {
    assert(dest->fpu_regnrLo() == 0, "dest must be TOS");
    Address src_addr = frame_map()->address_for_slot(src->double_stack_ix());
    __ fld_d(src_addr);

  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::stack2stack(LIR_Opr src, LIR_Opr dest, BasicType type) {
  if (src->is_single_stack()) {
    if (type == T_OBJECT || type == T_ARRAY) {
      __ pushptr(frame_map()->address_for_slot(src ->single_stack_ix()));
      __ popptr (frame_map()->address_for_slot(dest->single_stack_ix()));
    } else {
#ifndef _LP64
      __ pushl(frame_map()->address_for_slot(src ->single_stack_ix()));
      __ popl (frame_map()->address_for_slot(dest->single_stack_ix()));
#else
      //no pushl on 64bits
      __ movl(rscratch1, frame_map()->address_for_slot(src ->single_stack_ix()));
      __ movl(frame_map()->address_for_slot(dest->single_stack_ix()), rscratch1);
#endif
    }

  } else if (src->is_double_stack()) {
#ifdef _LP64
    __ pushptr(frame_map()->address_for_slot(src ->double_stack_ix()));
    __ popptr (frame_map()->address_for_slot(dest->double_stack_ix()));
#else
    __ pushl(frame_map()->address_for_slot(src ->double_stack_ix(), 0));
    // push and pop the part at src + wordSize, adding wordSize for the previous push
    __ pushl(frame_map()->address_for_slot(src ->double_stack_ix(), 2 * wordSize));
    __ popl (frame_map()->address_for_slot(dest->double_stack_ix(), 2 * wordSize));
    __ popl (frame_map()->address_for_slot(dest->double_stack_ix(), 0));
#endif // _LP64

  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::mem2reg(LIR_Opr src, LIR_Opr dest, BasicType type, LIR_PatchCode patch_code, CodeEmitInfo* info, bool /* unaligned */) {
  assert(src->is_address(), "should not call otherwise");
  assert(dest->is_register(), "should not call otherwise");

  LIR_Address* addr = src->as_address_ptr();
  Address from_addr = as_Address(addr);

  switch (type) {
    case T_BOOLEAN: // fall through
    case T_BYTE:    // fall through
    case T_CHAR:    // fall through
    case T_SHORT:
      if (!VM_Version::is_P6() && !from_addr.uses(dest->as_register())) {
        // on pre P6 processors we may get partial register stalls
        // so blow away the value of to_rinfo before loading a
        // partial word into it.  Do it here so that it precedes
        // the potential patch point below.
        __ xorptr(dest->as_register(), dest->as_register());
      }
      break;
  }

  PatchingStub* patch = NULL;
  if (patch_code != lir_patch_none) {
    patch = new PatchingStub(_masm, PatchingStub::access_field_id);
    assert(from_addr.disp() != 0, "must have");
  }
  if (info != NULL) {
    add_debug_info_for_null_check_here(info);
  }

  switch (type) {
    case T_FLOAT: {
      if (dest->is_single_xmm()) {
        __ movflt(dest->as_xmm_float_reg(), from_addr);
      } else {
        assert(dest->is_single_fpu(), "must be");
        assert(dest->fpu_regnr() == 0, "dest must be TOS");
        __ fld_s(from_addr);
      }
      break;
    }

    case T_DOUBLE: {
      if (dest->is_double_xmm()) {
        __ movdbl(dest->as_xmm_double_reg(), from_addr);
      } else {
        assert(dest->is_double_fpu(), "must be");
        assert(dest->fpu_regnrLo() == 0, "dest must be TOS");
        __ fld_d(from_addr);
      }
      break;
    }

    case T_ADDRESS: // fall through
    case T_OBJECT:  // fall through
    case T_ARRAY:   // fall through
#ifdef _LP64
      __ movptr(dest->as_register(), from_addr);
      break;
#endif // _L64
    case T_INT:
      __ movl(dest->as_register(), from_addr);
      break;

    case T_LONG: {
      Register to_lo = dest->as_register_lo();
      Register to_hi = dest->as_register_hi();
#ifdef _LP64
      __ movptr(to_lo, as_Address_lo(addr));
#else
      Register base = addr->base()->as_register();
      Register index = noreg;
      if (addr->index()->is_register()) {
        index = addr->index()->as_register();
      }
      if ((base == to_lo && index == to_hi) ||
          (base == to_hi && index == to_lo)) {
        // addresses with 2 registers are only formed as a result of
        // array access so this code will never have to deal with
        // patches or null checks.
        assert(info == NULL && patch == NULL, "must be");
        __ lea(to_hi, as_Address(addr));
        __ movl(to_lo, Address(to_hi, 0));
        __ movl(to_hi, Address(to_hi, BytesPerWord));
      } else if (base == to_lo || index == to_lo) {
        assert(base != to_hi, "can't be");
        assert(index == noreg || (index != base && index != to_hi), "can't handle this");
        __ movl(to_hi, as_Address_hi(addr));
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_high, base, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_low;
        }
        __ movl(to_lo, as_Address_lo(addr));
      } else {
        assert(index == noreg || (index != base && index != to_lo), "can't handle this");
        __ movl(to_lo, as_Address_lo(addr));
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_low, base, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_high;
        }
        __ movl(to_hi, as_Address_hi(addr));
      }
#endif // _LP64
      break;
    }

    case T_BOOLEAN: // fall through
    case T_BYTE: {
      Register dest_reg = dest->as_register();
      assert(VM_Version::is_P6() || dest_reg->has_byte_register(), "must use byte registers if not P6");
      if (VM_Version::is_P6() || from_addr.uses(dest_reg)) {
        __ movsbl(dest_reg, from_addr);
      } else {
        __ movb(dest_reg, from_addr);
        __ shll(dest_reg, 24);
        __ sarl(dest_reg, 24);
      }
      break;
    }

    case T_CHAR: {
      Register dest_reg = dest->as_register();
      assert(VM_Version::is_P6() || dest_reg->has_byte_register(), "must use byte registers if not P6");
      if (VM_Version::is_P6() || from_addr.uses(dest_reg)) {
        __ movzwl(dest_reg, from_addr);
      } else {
        __ movw(dest_reg, from_addr);
      }
      break;
    }

    case T_SHORT: {
      Register dest_reg = dest->as_register();
      if (VM_Version::is_P6() || from_addr.uses(dest_reg)) {
        __ movswl(dest_reg, from_addr);
      } else {
        __ movw(dest_reg, from_addr);
        __ shll(dest_reg, 16);
        __ sarl(dest_reg, 16);
      }
      break;
    }

    default:
      ShouldNotReachHere();
  }

  if (patch != NULL) {
    patching_epilog(patch, patch_code, addr->base()->as_register(), info);
  }

  if (type == T_ARRAY || type == T_OBJECT) {
    __ verify_oop(dest->as_register());
  }
}


void LIR_Assembler::prefetchr(LIR_Opr src) {
  LIR_Address* addr = src->as_address_ptr();
  Address from_addr = as_Address(addr);

  if (VM_Version::supports_sse()) {
    switch (ReadPrefetchInstr) {
      case 0:
        __ prefetchnta(from_addr); break;
      case 1:
        __ prefetcht0(from_addr); break;
      case 2:
        __ prefetcht2(from_addr); break;
      default:
        ShouldNotReachHere(); break;
    }
  } else if (VM_Version::supports_3dnow()) {
    __ prefetchr(from_addr);
  }
}


void LIR_Assembler::prefetchw(LIR_Opr src) {
  LIR_Address* addr = src->as_address_ptr();
  Address from_addr = as_Address(addr);

  if (VM_Version::supports_sse()) {
    switch (AllocatePrefetchInstr) {
      case 0:
        __ prefetchnta(from_addr); break;
      case 1:
        __ prefetcht0(from_addr); break;
      case 2:
        __ prefetcht2(from_addr); break;
      case 3:
        __ prefetchw(from_addr); break;
      default:
        ShouldNotReachHere(); break;
    }
  } else if (VM_Version::supports_3dnow()) {
    __ prefetchw(from_addr);
  }
}


NEEDS_CLEANUP; // This could be static?
Address::ScaleFactor LIR_Assembler::array_element_size(BasicType type) const {
  int elem_size = type2aelembytes(type);
  switch (elem_size) {
    case 1: return Address::times_1;
    case 2: return Address::times_2;
    case 4: return Address::times_4;
    case 8: return Address::times_8;
  }
  ShouldNotReachHere();
  return Address::no_scale;
}


void LIR_Assembler::emit_op3(LIR_Op3* op) {
  switch (op->code()) {
    case lir_idiv:
    case lir_irem:
      arithmetic_idiv(op->code(),
                      op->in_opr1(),
                      op->in_opr2(),
                      op->in_opr3(),
                      op->result_opr(),
                      op->info());
      break;
    default:      ShouldNotReachHere(); break;
  }
}

void LIR_Assembler::emit_opBranch(LIR_OpBranch* op) {
#ifdef ASSERT
  assert(op->block() == NULL || op->block()->label() == op->label(), "wrong label");
  if (op->block() != NULL)  _branch_target_blocks.append(op->block());
  if (op->ublock() != NULL) _branch_target_blocks.append(op->ublock());
#endif

  if (op->cond() == lir_cond_always) {
    if (op->info() != NULL) add_debug_info_for_branch(op->info());
    __ jmp (*(op->label()));
  } else {
    Assembler::Condition acond = Assembler::zero;
    if (op->code() == lir_cond_float_branch) {
      assert(op->ublock() != NULL, "must have unordered successor");
      __ jcc(Assembler::parity, *(op->ublock()->label()));
      switch(op->cond()) {
        case lir_cond_equal:        acond = Assembler::equal;      break;
        case lir_cond_notEqual:     acond = Assembler::notEqual;   break;
        case lir_cond_less:         acond = Assembler::below;      break;
        case lir_cond_lessEqual:    acond = Assembler::belowEqual; break;
        case lir_cond_greaterEqual: acond = Assembler::aboveEqual; break;
        case lir_cond_greater:      acond = Assembler::above;      break;
        default:                         ShouldNotReachHere();
      }
    } else {
      switch (op->cond()) {
        case lir_cond_equal:        acond = Assembler::equal;       break;
        case lir_cond_notEqual:     acond = Assembler::notEqual;    break;
        case lir_cond_less:         acond = Assembler::less;        break;
        case lir_cond_lessEqual:    acond = Assembler::lessEqual;   break;
        case lir_cond_greaterEqual: acond = Assembler::greaterEqual;break;
        case lir_cond_greater:      acond = Assembler::greater;     break;
        case lir_cond_belowEqual:   acond = Assembler::belowEqual;  break;
        case lir_cond_aboveEqual:   acond = Assembler::aboveEqual;  break;
        default:                         ShouldNotReachHere();
      }
    }
    __ jcc(acond,*(op->label()));
  }
}

void LIR_Assembler::emit_opConvert(LIR_OpConvert* op) {
  LIR_Opr src  = op->in_opr();
  LIR_Opr dest = op->result_opr();

  switch (op->bytecode()) {
    case Bytecodes::_i2l:
#ifdef _LP64
      __ movl2ptr(dest->as_register_lo(), src->as_register());
#else
      move_regs(src->as_register(), dest->as_register_lo());
      move_regs(src->as_register(), dest->as_register_hi());
      __ sarl(dest->as_register_hi(), 31);
#endif // LP64
      break;

    case Bytecodes::_l2i:
      move_regs(src->as_register_lo(), dest->as_register());
      break;

    case Bytecodes::_i2b:
      move_regs(src->as_register(), dest->as_register());
      __ sign_extend_byte(dest->as_register());
      break;

    case Bytecodes::_i2c:
      move_regs(src->as_register(), dest->as_register());
      __ andl(dest->as_register(), 0xFFFF);
      break;

    case Bytecodes::_i2s:
      move_regs(src->as_register(), dest->as_register());
      __ sign_extend_short(dest->as_register());
      break;


    case Bytecodes::_f2d:
    case Bytecodes::_d2f:
      if (dest->is_single_xmm()) {
        __ cvtsd2ss(dest->as_xmm_float_reg(), src->as_xmm_double_reg());
      } else if (dest->is_double_xmm()) {
        __ cvtss2sd(dest->as_xmm_double_reg(), src->as_xmm_float_reg());
      } else {
        assert(src->fpu() == dest->fpu(), "register must be equal");
        // do nothing (float result is rounded later through spilling)
      }
      break;

    case Bytecodes::_i2f:
    case Bytecodes::_i2d:
      if (dest->is_single_xmm()) {
        __ cvtsi2ssl(dest->as_xmm_float_reg(), src->as_register());
      } else if (dest->is_double_xmm()) {
        __ cvtsi2sdl(dest->as_xmm_double_reg(), src->as_register());
      } else {
        assert(dest->fpu() == 0, "result must be on TOS");
        __ movl(Address(rsp, 0), src->as_register());
        __ fild_s(Address(rsp, 0));
      }
      break;

    case Bytecodes::_f2i:
    case Bytecodes::_d2i:
      if (src->is_single_xmm()) {
        __ cvttss2sil(dest->as_register(), src->as_xmm_float_reg());
      } else if (src->is_double_xmm()) {
        __ cvttsd2sil(dest->as_register(), src->as_xmm_double_reg());
      } else {
        assert(src->fpu() == 0, "input must be on TOS");
        __ fldcw(ExternalAddress(StubRoutines::addr_fpu_cntrl_wrd_trunc()));
        __ fist_s(Address(rsp, 0));
        __ movl(dest->as_register(), Address(rsp, 0));
        __ fldcw(ExternalAddress(StubRoutines::addr_fpu_cntrl_wrd_std()));
      }

      // IA32 conversion instructions do not match JLS for overflow, underflow and NaN -> fixup in stub
      assert(op->stub() != NULL, "stub required");
      __ cmpl(dest->as_register(), 0x80000000);
      __ jcc(Assembler::equal, *op->stub()->entry());
      __ bind(*op->stub()->continuation());
      break;

    case Bytecodes::_l2f:
    case Bytecodes::_l2d:
      assert(!dest->is_xmm_register(), "result in xmm register not supported (no SSE instruction present)");
      assert(dest->fpu() == 0, "result must be on TOS");

      __ movptr(Address(rsp, 0),            src->as_register_lo());
      NOT_LP64(__ movl(Address(rsp, BytesPerWord), src->as_register_hi()));
      __ fild_d(Address(rsp, 0));
      // float result is rounded later through spilling
      break;

    case Bytecodes::_f2l:
    case Bytecodes::_d2l:
      assert(!src->is_xmm_register(), "input in xmm register not supported (no SSE instruction present)");
      assert(src->fpu() == 0, "input must be on TOS");
      assert(dest == FrameMap::long0_opr, "runtime stub places result in these registers");

      // instruction sequence too long to inline it here
      {
        __ call(RuntimeAddress(Runtime1::entry_for(Runtime1::fpu2long_stub_id)));
      }
      break;

    default: ShouldNotReachHere();
  }
}

void LIR_Assembler::emit_alloc_obj(LIR_OpAllocObj* op) {
  if (op->init_check()) {
    __ cmpl(Address(op->klass()->as_register(),
                    instanceKlass::init_state_offset_in_bytes() + sizeof(oopDesc)),
            instanceKlass::fully_initialized);
    add_debug_info_for_null_check_here(op->stub()->info());
    __ jcc(Assembler::notEqual, *op->stub()->entry());
  }
  __ allocate_object(op->obj()->as_register(),
                     op->tmp1()->as_register(),
                     op->tmp2()->as_register(),
                     op->header_size(),
                     op->object_size(),
                     op->klass()->as_register(),
                     *op->stub()->entry());
  __ bind(*op->stub()->continuation());
}

void LIR_Assembler::emit_alloc_array(LIR_OpAllocArray* op) {
  if (UseSlowPath ||
      (!UseFastNewObjectArray && (op->type() == T_OBJECT || op->type() == T_ARRAY)) ||
      (!UseFastNewTypeArray   && (op->type() != T_OBJECT && op->type() != T_ARRAY))) {
    __ jmp(*op->stub()->entry());
  } else {
    Register len =  op->len()->as_register();
    Register tmp1 = op->tmp1()->as_register();
    Register tmp2 = op->tmp2()->as_register();
    Register tmp3 = op->tmp3()->as_register();
    if (len == tmp1) {
      tmp1 = tmp3;
    } else if (len == tmp2) {
      tmp2 = tmp3;
    } else if (len == tmp3) {
      // everything is ok
    } else {
      __ mov(tmp3, len);
    }
    __ allocate_array(op->obj()->as_register(),
                      len,
                      tmp1,
                      tmp2,
                      arrayOopDesc::header_size(op->type()),
                      array_element_size(op->type()),
                      op->klass()->as_register(),
                      *op->stub()->entry());
  }
  __ bind(*op->stub()->continuation());
}



void LIR_Assembler::emit_opTypeCheck(LIR_OpTypeCheck* op) {
  LIR_Code code = op->code();
  if (code == lir_store_check) {
    Register value = op->object()->as_register();
    Register array = op->array()->as_register();
    Register k_RInfo = op->tmp1()->as_register();
    Register klass_RInfo = op->tmp2()->as_register();
    Register Rtmp1 = op->tmp3()->as_register();

    CodeStub* stub = op->stub();
    Label done;
    __ cmpptr(value, (int32_t)NULL_WORD);
    __ jcc(Assembler::equal, done);
    add_debug_info_for_null_check_here(op->info_for_exception());
    __ movptr(k_RInfo, Address(array, oopDesc::klass_offset_in_bytes()));
    __ movptr(klass_RInfo, Address(value, oopDesc::klass_offset_in_bytes()));

    // get instance klass
    __ movptr(k_RInfo, Address(k_RInfo, objArrayKlass::element_klass_offset_in_bytes() + sizeof(oopDesc)));
    // perform the fast part of the checking logic
    __ check_klass_subtype_fast_path(klass_RInfo, k_RInfo, Rtmp1, &done, stub->entry(), NULL);
    // call out-of-line instance of __ check_klass_subtype_slow_path(...):
    __ push(klass_RInfo);
    __ push(k_RInfo);
    __ call(RuntimeAddress(Runtime1::entry_for(Runtime1::slow_subtype_check_id)));
    __ pop(klass_RInfo);
    __ pop(k_RInfo);
    // result is a boolean
    __ cmpl(k_RInfo, 0);
    __ jcc(Assembler::equal, *stub->entry());
    __ bind(done);
  } else if (op->code() == lir_checkcast) {
    // we always need a stub for the failure case.
    CodeStub* stub = op->stub();
    Register obj = op->object()->as_register();
    Register k_RInfo = op->tmp1()->as_register();
    Register klass_RInfo = op->tmp2()->as_register();
    Register dst = op->result_opr()->as_register();
    ciKlass* k = op->klass();
    Register Rtmp1 = noreg;

    Label done;
    if (obj == k_RInfo) {
      k_RInfo = dst;
    } else if (obj == klass_RInfo) {
      klass_RInfo = dst;
    }
    if (k->is_loaded()) {
      select_different_registers(obj, dst, k_RInfo, klass_RInfo);
    } else {
      Rtmp1 = op->tmp3()->as_register();
      select_different_registers(obj, dst, k_RInfo, klass_RInfo, Rtmp1);
    }

    assert_different_registers(obj, k_RInfo, klass_RInfo);
    if (!k->is_loaded()) {
      jobject2reg_with_patching(k_RInfo, op->info_for_patch());
    } else {
#ifdef _LP64
      __ movoop(k_RInfo, k->constant_encoding());
#else
      k_RInfo = noreg;
#endif // _LP64
    }
    assert(obj != k_RInfo, "must be different");
    __ cmpptr(obj, (int32_t)NULL_WORD);
    if (op->profiled_method() != NULL) {
      ciMethod* method = op->profiled_method();
      int bci          = op->profiled_bci();

      Label profile_done;
      __ jcc(Assembler::notEqual, profile_done);
      // Object is null; update methodDataOop
      ciMethodData* md = method->method_data();
      if (md == NULL) {
        bailout("out of memory building methodDataOop");
        return;
      }
      ciProfileData* data = md->bci_to_data(bci);
      assert(data != NULL,       "need data for checkcast");
      assert(data->is_BitData(), "need BitData for checkcast");
      Register mdo  = klass_RInfo;
      __ movoop(mdo, md->constant_encoding());
      Address data_addr(mdo, md->byte_offset_of_slot(data, DataLayout::header_offset()));
      int header_bits = DataLayout::flag_mask_to_header_mask(BitData::null_seen_byte_constant());
      __ orl(data_addr, header_bits);
      __ jmp(done);
      __ bind(profile_done);
    } else {
      __ jcc(Assembler::equal, done);
    }
    __ verify_oop(obj);

    if (op->fast_check()) {
      // get object classo
      // not a safepoint as obj null check happens earlier
      if (k->is_loaded()) {
#ifdef _LP64
        __ cmpptr(k_RInfo, Address(obj, oopDesc::klass_offset_in_bytes()));
#else
        __ cmpoop(Address(obj, oopDesc::klass_offset_in_bytes()), k->constant_encoding());
#endif // _LP64
      } else {
        __ cmpptr(k_RInfo, Address(obj, oopDesc::klass_offset_in_bytes()));

      }
      __ jcc(Assembler::notEqual, *stub->entry());
      __ bind(done);
    } else {
      // get object class
      // not a safepoint as obj null check happens earlier
      __ movptr(klass_RInfo, Address(obj, oopDesc::klass_offset_in_bytes()));
      if (k->is_loaded()) {
        // See if we get an immediate positive hit
#ifdef _LP64
        __ cmpptr(k_RInfo, Address(klass_RInfo, k->super_check_offset()));
#else
        __ cmpoop(Address(klass_RInfo, k->super_check_offset()), k->constant_encoding());
#endif // _LP64
        if (sizeof(oopDesc) + Klass::secondary_super_cache_offset_in_bytes() != k->super_check_offset()) {
          __ jcc(Assembler::notEqual, *stub->entry());
        } else {
          // See if we get an immediate positive hit
          __ jcc(Assembler::equal, done);
          // check for self
#ifdef _LP64
          __ cmpptr(klass_RInfo, k_RInfo);
#else
          __ cmpoop(klass_RInfo, k->constant_encoding());
#endif // _LP64
          __ jcc(Assembler::equal, done);

          __ push(klass_RInfo);
#ifdef _LP64
          __ push(k_RInfo);
#else
          __ pushoop(k->constant_encoding());
#endif // _LP64
          __ call(RuntimeAddress(Runtime1::entry_for(Runtime1::slow_subtype_check_id)));
          __ pop(klass_RInfo);
          __ pop(klass_RInfo);
          // result is a boolean
          __ cmpl(klass_RInfo, 0);
          __ jcc(Assembler::equal, *stub->entry());
        }
        __ bind(done);
      } else {
        // perform the fast part of the checking logic
        __ check_klass_subtype_fast_path(klass_RInfo, k_RInfo, Rtmp1, &done, stub->entry(), NULL);
        // call out-of-line instance of __ check_klass_subtype_slow_path(...):
        __ push(klass_RInfo);
        __ push(k_RInfo);
        __ call(RuntimeAddress(Runtime1::entry_for(Runtime1::slow_subtype_check_id)));
        __ pop(klass_RInfo);
        __ pop(k_RInfo);
        // result is a boolean
        __ cmpl(k_RInfo, 0);
        __ jcc(Assembler::equal, *stub->entry());
        __ bind(done);
      }

    }
    if (dst != obj) {
      __ mov(dst, obj);
    }
  } else if (code == lir_instanceof) {
    Register obj = op->object()->as_register();
    Register k_RInfo = op->tmp1()->as_register();
    Register klass_RInfo = op->tmp2()->as_register();
    Register dst = op->result_opr()->as_register();
    ciKlass* k = op->klass();

    Label done;
    Label zero;
    Label one;
    if (obj == k_RInfo) {
      k_RInfo = klass_RInfo;
      klass_RInfo = obj;
    }
    // patching may screw with our temporaries on sparc,
    // so let's do it before loading the class
    if (!k->is_loaded()) {
      jobject2reg_with_patching(k_RInfo, op->info_for_patch());
    } else {
      LP64_ONLY(__ movoop(k_RInfo, k->constant_encoding()));
    }
    assert(obj != k_RInfo, "must be different");

    __ verify_oop(obj);
    if (op->fast_check()) {
      __ cmpptr(obj, (int32_t)NULL_WORD);
      __ jcc(Assembler::equal, zero);
      // get object class
      // not a safepoint as obj null check happens earlier
      if (LP64_ONLY(false &&) k->is_loaded()) {
        NOT_LP64(__ cmpoop(Address(obj, oopDesc::klass_offset_in_bytes()), k->constant_encoding()));
        k_RInfo = noreg;
      } else {
        __ cmpptr(k_RInfo, Address(obj, oopDesc::klass_offset_in_bytes()));

      }
      __ jcc(Assembler::equal, one);
    } else {
      // get object class
      // not a safepoint as obj null check happens earlier
      __ cmpptr(obj, (int32_t)NULL_WORD);
      __ jcc(Assembler::equal, zero);
      __ movptr(klass_RInfo, Address(obj, oopDesc::klass_offset_in_bytes()));

#ifndef _LP64
      if (k->is_loaded()) {
        // See if we get an immediate positive hit
        __ cmpoop(Address(klass_RInfo, k->super_check_offset()), k->constant_encoding());
        __ jcc(Assembler::equal, one);
        if (sizeof(oopDesc) + Klass::secondary_super_cache_offset_in_bytes() == k->super_check_offset()) {
          // check for self
          __ cmpoop(klass_RInfo, k->constant_encoding());
          __ jcc(Assembler::equal, one);
          __ push(klass_RInfo);
          __ pushoop(k->constant_encoding());
          __ call(RuntimeAddress(Runtime1::entry_for(Runtime1::slow_subtype_check_id)));
          __ pop(klass_RInfo);
          __ pop(dst);
          __ jmp(done);
        }
      }
        else // next block is unconditional if LP64:
#endif // LP64
      {
        assert(dst != klass_RInfo && dst != k_RInfo, "need 3 registers");

        // perform the fast part of the checking logic
        __ check_klass_subtype_fast_path(klass_RInfo, k_RInfo, dst, &one, &zero, NULL);
        // call out-of-line instance of __ check_klass_subtype_slow_path(...):
        __ push(klass_RInfo);
        __ push(k_RInfo);
        __ call(RuntimeAddress(Runtime1::entry_for(Runtime1::slow_subtype_check_id)));
        __ pop(klass_RInfo);
        __ pop(dst);
        __ jmp(done);
      }
    }
    __ bind(zero);
    __ xorptr(dst, dst);
    __ jmp(done);
    __ bind(one);
    __ movptr(dst, 1);
    __ bind(done);
  } else {
    ShouldNotReachHere();
  }

}


void LIR_Assembler::emit_compare_and_swap(LIR_OpCompareAndSwap* op) {
  if (LP64_ONLY(false &&) op->code() == lir_cas_long && VM_Version::supports_cx8()) {
    assert(op->cmp_value()->as_register_lo() == rax, "wrong register");
    assert(op->cmp_value()->as_register_hi() == rdx, "wrong register");
    assert(op->new_value()->as_register_lo() == rbx, "wrong register");
    assert(op->new_value()->as_register_hi() == rcx, "wrong register");
    Register addr = op->addr()->as_register();
    if (os::is_MP()) {
      __ lock();
    }
    NOT_LP64(__ cmpxchg8(Address(addr, 0)));

  } else if (op->code() == lir_cas_int || op->code() == lir_cas_obj ) {
    NOT_LP64(assert(op->addr()->is_single_cpu(), "must be single");)
    Register addr = (op->addr()->is_single_cpu() ? op->addr()->as_register() : op->addr()->as_register_lo());
    Register newval = op->new_value()->as_register();
    Register cmpval = op->cmp_value()->as_register();
    assert(cmpval == rax, "wrong register");
    assert(newval != NULL, "new val must be register");
    assert(cmpval != newval, "cmp and new values must be in different registers");
    assert(cmpval != addr, "cmp and addr must be in different registers");
    assert(newval != addr, "new value and addr must be in different registers");
    if (os::is_MP()) {
      __ lock();
    }
    if ( op->code() == lir_cas_obj) {
      __ cmpxchgptr(newval, Address(addr, 0));
    } else if (op->code() == lir_cas_int) {
      __ cmpxchgl(newval, Address(addr, 0));
    } else {
      LP64_ONLY(__ cmpxchgq(newval, Address(addr, 0)));
    }
#ifdef _LP64
  } else if (op->code() == lir_cas_long) {
    Register addr = (op->addr()->is_single_cpu() ? op->addr()->as_register() : op->addr()->as_register_lo());
    Register newval = op->new_value()->as_register_lo();
    Register cmpval = op->cmp_value()->as_register_lo();
    assert(cmpval == rax, "wrong register");
    assert(newval != NULL, "new val must be register");
    assert(cmpval != newval, "cmp and new values must be in different registers");
    assert(cmpval != addr, "cmp and addr must be in different registers");
    assert(newval != addr, "new value and addr must be in different registers");
    if (os::is_MP()) {
      __ lock();
    }
    __ cmpxchgq(newval, Address(addr, 0));
#endif // _LP64
  } else {
    Unimplemented();
  }
}


void LIR_Assembler::cmove(LIR_Condition condition, LIR_Opr opr1, LIR_Opr opr2, LIR_Opr result) {
  Assembler::Condition acond, ncond;
  switch (condition) {
    case lir_cond_equal:        acond = Assembler::equal;        ncond = Assembler::notEqual;     break;
    case lir_cond_notEqual:     acond = Assembler::notEqual;     ncond = Assembler::equal;        break;
    case lir_cond_less:         acond = Assembler::less;         ncond = Assembler::greaterEqual; break;
    case lir_cond_lessEqual:    acond = Assembler::lessEqual;    ncond = Assembler::greater;      break;
    case lir_cond_greaterEqual: acond = Assembler::greaterEqual; ncond = Assembler::less;         break;
    case lir_cond_greater:      acond = Assembler::greater;      ncond = Assembler::lessEqual;    break;
    case lir_cond_belowEqual:   acond = Assembler::belowEqual;   ncond = Assembler::above;        break;
    case lir_cond_aboveEqual:   acond = Assembler::aboveEqual;   ncond = Assembler::below;        break;
    default:                    ShouldNotReachHere();
  }

  if (opr1->is_cpu_register()) {
    reg2reg(opr1, result);
  } else if (opr1->is_stack()) {
    stack2reg(opr1, result, result->type());
  } else if (opr1->is_constant()) {
    const2reg(opr1, result, lir_patch_none, NULL);
  } else {
    ShouldNotReachHere();
  }

  if (VM_Version::supports_cmov() && !opr2->is_constant()) {
    // optimized version that does not require a branch
    if (opr2->is_single_cpu()) {
      assert(opr2->cpu_regnr() != result->cpu_regnr(), "opr2 already overwritten by previous move");
      __ cmov(ncond, result->as_register(), opr2->as_register());
    } else if (opr2->is_double_cpu()) {
      assert(opr2->cpu_regnrLo() != result->cpu_regnrLo() && opr2->cpu_regnrLo() != result->cpu_regnrHi(), "opr2 already overwritten by previous move");
      assert(opr2->cpu_regnrHi() != result->cpu_regnrLo() && opr2->cpu_regnrHi() != result->cpu_regnrHi(), "opr2 already overwritten by previous move");
      __ cmovptr(ncond, result->as_register_lo(), opr2->as_register_lo());
      NOT_LP64(__ cmovptr(ncond, result->as_register_hi(), opr2->as_register_hi());)
    } else if (opr2->is_single_stack()) {
      __ cmovl(ncond, result->as_register(), frame_map()->address_for_slot(opr2->single_stack_ix()));
    } else if (opr2->is_double_stack()) {
      __ cmovptr(ncond, result->as_register_lo(), frame_map()->address_for_slot(opr2->double_stack_ix(), lo_word_offset_in_bytes));
      NOT_LP64(__ cmovptr(ncond, result->as_register_hi(), frame_map()->address_for_slot(opr2->double_stack_ix(), hi_word_offset_in_bytes));)
    } else {
      ShouldNotReachHere();
    }

  } else {
    Label skip;
    __ jcc (acond, skip);
    if (opr2->is_cpu_register()) {
      reg2reg(opr2, result);
    } else if (opr2->is_stack()) {
      stack2reg(opr2, result, result->type());
    } else if (opr2->is_constant()) {
      const2reg(opr2, result, lir_patch_none, NULL);
    } else {
      ShouldNotReachHere();
    }
    __ bind(skip);
  }
}


void LIR_Assembler::arith_op(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest, CodeEmitInfo* info, bool pop_fpu_stack) {
  assert(info == NULL, "should never be used, idiv/irem and ldiv/lrem not handled by this method");

  if (left->is_single_cpu()) {
    assert(left == dest, "left and dest must be equal");
    Register lreg = left->as_register();

    if (right->is_single_cpu()) {
      // cpu register - cpu register
      Register rreg = right->as_register();
      switch (code) {
        case lir_add: __ addl (lreg, rreg); break;
        case lir_sub: __ subl (lreg, rreg); break;
        case lir_mul: __ imull(lreg, rreg); break;
        default:      ShouldNotReachHere();
      }

    } else if (right->is_stack()) {
      // cpu register - stack
      Address raddr = frame_map()->address_for_slot(right->single_stack_ix());
      switch (code) {
        case lir_add: __ addl(lreg, raddr); break;
        case lir_sub: __ subl(lreg, raddr); break;
        default:      ShouldNotReachHere();
      }

    } else if (right->is_constant()) {
      // cpu register - constant
      jint c = right->as_constant_ptr()->as_jint();
      switch (code) {
        case lir_add: {
          __ increment(lreg, c);
          break;
        }
        case lir_sub: {
          __ decrement(lreg, c);
          break;
        }
        default: ShouldNotReachHere();
      }

    } else {
      ShouldNotReachHere();
    }

  } else if (left->is_double_cpu()) {
    assert(left == dest, "left and dest must be equal");
    Register lreg_lo = left->as_register_lo();
    Register lreg_hi = left->as_register_hi();

    if (right->is_double_cpu()) {
      // cpu register - cpu register
      Register rreg_lo = right->as_register_lo();
      Register rreg_hi = right->as_register_hi();
      NOT_LP64(assert_different_registers(lreg_lo, lreg_hi, rreg_lo, rreg_hi));
      LP64_ONLY(assert_different_registers(lreg_lo, rreg_lo));
      switch (code) {
        case lir_add:
          __ addptr(lreg_lo, rreg_lo);
          NOT_LP64(__ adcl(lreg_hi, rreg_hi));
          break;
        case lir_sub:
          __ subptr(lreg_lo, rreg_lo);
          NOT_LP64(__ sbbl(lreg_hi, rreg_hi));
          break;
        case lir_mul:
#ifdef _LP64
          __ imulq(lreg_lo, rreg_lo);
#else
          assert(lreg_lo == rax && lreg_hi == rdx, "must be");
          __ imull(lreg_hi, rreg_lo);
          __ imull(rreg_hi, lreg_lo);
          __ addl (rreg_hi, lreg_hi);
          __ mull (rreg_lo);
          __ addl (lreg_hi, rreg_hi);
#endif // _LP64
          break;
        default:
          ShouldNotReachHere();
      }

    } else if (right->is_constant()) {
      // cpu register - constant
#ifdef _LP64
      jlong c = right->as_constant_ptr()->as_jlong_bits();
      __ movptr(r10, (intptr_t) c);
      switch (code) {
        case lir_add:
          __ addptr(lreg_lo, r10);
          break;
        case lir_sub:
          __ subptr(lreg_lo, r10);
          break;
        default:
          ShouldNotReachHere();
      }
#else
      jint c_lo = right->as_constant_ptr()->as_jint_lo();
      jint c_hi = right->as_constant_ptr()->as_jint_hi();
      switch (code) {
        case lir_add:
          __ addptr(lreg_lo, c_lo);
          __ adcl(lreg_hi, c_hi);
          break;
        case lir_sub:
          __ subptr(lreg_lo, c_lo);
          __ sbbl(lreg_hi, c_hi);
          break;
        default:
          ShouldNotReachHere();
      }
#endif // _LP64

    } else {
      ShouldNotReachHere();
    }

  } else if (left->is_single_xmm()) {
    assert(left == dest, "left and dest must be equal");
    XMMRegister lreg = left->as_xmm_float_reg();

    if (right->is_single_xmm()) {
      XMMRegister rreg = right->as_xmm_float_reg();
      switch (code) {
        case lir_add: __ addss(lreg, rreg);  break;
        case lir_sub: __ subss(lreg, rreg);  break;
        case lir_mul_strictfp: // fall through
        case lir_mul: __ mulss(lreg, rreg);  break;
        case lir_div_strictfp: // fall through
        case lir_div: __ divss(lreg, rreg);  break;
        default: ShouldNotReachHere();
      }
    } else {
      Address raddr;
      if (right->is_single_stack()) {
        raddr = frame_map()->address_for_slot(right->single_stack_ix());
      } else if (right->is_constant()) {
        // hack for now
        raddr = __ as_Address(InternalAddress(float_constant(right->as_jfloat())));
      } else {
        ShouldNotReachHere();
      }
      switch (code) {
        case lir_add: __ addss(lreg, raddr);  break;
        case lir_sub: __ subss(lreg, raddr);  break;
        case lir_mul_strictfp: // fall through
        case lir_mul: __ mulss(lreg, raddr);  break;
        case lir_div_strictfp: // fall through
        case lir_div: __ divss(lreg, raddr);  break;
        default: ShouldNotReachHere();
      }
    }

  } else if (left->is_double_xmm()) {
    assert(left == dest, "left and dest must be equal");

    XMMRegister lreg = left->as_xmm_double_reg();
    if (right->is_double_xmm()) {
      XMMRegister rreg = right->as_xmm_double_reg();
      switch (code) {
        case lir_add: __ addsd(lreg, rreg);  break;
        case lir_sub: __ subsd(lreg, rreg);  break;
        case lir_mul_strictfp: // fall through
        case lir_mul: __ mulsd(lreg, rreg);  break;
        case lir_div_strictfp: // fall through
        case lir_div: __ divsd(lreg, rreg);  break;
        default: ShouldNotReachHere();
      }
    } else {
      Address raddr;
      if (right->is_double_stack()) {
        raddr = frame_map()->address_for_slot(right->double_stack_ix());
      } else if (right->is_constant()) {
        // hack for now
        raddr = __ as_Address(InternalAddress(double_constant(right->as_jdouble())));
      } else {
        ShouldNotReachHere();
      }
      switch (code) {
        case lir_add: __ addsd(lreg, raddr);  break;
        case lir_sub: __ subsd(lreg, raddr);  break;
        case lir_mul_strictfp: // fall through
        case lir_mul: __ mulsd(lreg, raddr);  break;
        case lir_div_strictfp: // fall through
        case lir_div: __ divsd(lreg, raddr);  break;
        default: ShouldNotReachHere();
      }
    }

  } else if (left->is_single_fpu()) {
    assert(dest->is_single_fpu(),  "fpu stack allocation required");

    if (right->is_single_fpu()) {
      arith_fpu_implementation(code, left->fpu_regnr(), right->fpu_regnr(), dest->fpu_regnr(), pop_fpu_stack);

    } else {
      assert(left->fpu_regnr() == 0, "left must be on TOS");
      assert(dest->fpu_regnr() == 0, "dest must be on TOS");

      Address raddr;
      if (right->is_single_stack()) {
        raddr = frame_map()->address_for_slot(right->single_stack_ix());
      } else if (right->is_constant()) {
        address const_addr = float_constant(right->as_jfloat());
        assert(const_addr != NULL, "incorrect float/double constant maintainance");
        // hack for now
        raddr = __ as_Address(InternalAddress(const_addr));
      } else {
        ShouldNotReachHere();
      }

      switch (code) {
        case lir_add: __ fadd_s(raddr); break;
        case lir_sub: __ fsub_s(raddr); break;
        case lir_mul_strictfp: // fall through
        case lir_mul: __ fmul_s(raddr); break;
        case lir_div_strictfp: // fall through
        case lir_div: __ fdiv_s(raddr); break;
        default:      ShouldNotReachHere();
      }
    }

  } else if (left->is_double_fpu()) {
    assert(dest->is_double_fpu(),  "fpu stack allocation required");

    if (code == lir_mul_strictfp || code == lir_div_strictfp) {
      // Double values require special handling for strictfp mul/div on x86
      __ fld_x(ExternalAddress(StubRoutines::addr_fpu_subnormal_bias1()));
      __ fmulp(left->fpu_regnrLo() + 1);
    }

    if (right->is_double_fpu()) {
      arith_fpu_implementation(code, left->fpu_regnrLo(), right->fpu_regnrLo(), dest->fpu_regnrLo(), pop_fpu_stack);

    } else {
      assert(left->fpu_regnrLo() == 0, "left must be on TOS");
      assert(dest->fpu_regnrLo() == 0, "dest must be on TOS");

      Address raddr;
      if (right->is_double_stack()) {
        raddr = frame_map()->address_for_slot(right->double_stack_ix());
      } else if (right->is_constant()) {
        // hack for now
        raddr = __ as_Address(InternalAddress(double_constant(right->as_jdouble())));
      } else {
        ShouldNotReachHere();
      }

      switch (code) {
        case lir_add: __ fadd_d(raddr); break;
        case lir_sub: __ fsub_d(raddr); break;
        case lir_mul_strictfp: // fall through
        case lir_mul: __ fmul_d(raddr); break;
        case lir_div_strictfp: // fall through
        case lir_div: __ fdiv_d(raddr); break;
        default: ShouldNotReachHere();
      }
    }

    if (code == lir_mul_strictfp || code == lir_div_strictfp) {
      // Double values require special handling for strictfp mul/div on x86
      __ fld_x(ExternalAddress(StubRoutines::addr_fpu_subnormal_bias2()));
      __ fmulp(dest->fpu_regnrLo() + 1);
    }

  } else if (left->is_single_stack() || left->is_address()) {
    assert(left == dest, "left and dest must be equal");

    Address laddr;
    if (left->is_single_stack()) {
      laddr = frame_map()->address_for_slot(left->single_stack_ix());
    } else if (left->is_address()) {
      laddr = as_Address(left->as_address_ptr());
    } else {
      ShouldNotReachHere();
    }

    if (right->is_single_cpu()) {
      Register rreg = right->as_register();
      switch (code) {
        case lir_add: __ addl(laddr, rreg); break;
        case lir_sub: __ subl(laddr, rreg); break;
        default:      ShouldNotReachHere();
      }
    } else if (right->is_constant()) {
      jint c = right->as_constant_ptr()->as_jint();
      switch (code) {
        case lir_add: {
          __ incrementl(laddr, c);
          break;
        }
        case lir_sub: {
          __ decrementl(laddr, c);
          break;
        }
        default: ShouldNotReachHere();
      }
    } else {
      ShouldNotReachHere();
    }

  } else {
    ShouldNotReachHere();
  }
}

void LIR_Assembler::arith_fpu_implementation(LIR_Code code, int left_index, int right_index, int dest_index, bool pop_fpu_stack) {
  assert(pop_fpu_stack  || (left_index     == dest_index || right_index     == dest_index), "invalid LIR");
  assert(!pop_fpu_stack || (left_index - 1 == dest_index || right_index - 1 == dest_index), "invalid LIR");
  assert(left_index == 0 || right_index == 0, "either must be on top of stack");

  bool left_is_tos = (left_index == 0);
  bool dest_is_tos = (dest_index == 0);
  int non_tos_index = (left_is_tos ? right_index : left_index);

  switch (code) {
    case lir_add:
      if (pop_fpu_stack)       __ faddp(non_tos_index);
      else if (dest_is_tos)    __ fadd (non_tos_index);
      else                     __ fadda(non_tos_index);
      break;

    case lir_sub:
      if (left_is_tos) {
        if (pop_fpu_stack)     __ fsubrp(non_tos_index);
        else if (dest_is_tos)  __ fsub  (non_tos_index);
        else                   __ fsubra(non_tos_index);
      } else {
        if (pop_fpu_stack)     __ fsubp (non_tos_index);
        else if (dest_is_tos)  __ fsubr (non_tos_index);
        else                   __ fsuba (non_tos_index);
      }
      break;

    case lir_mul_strictfp: // fall through
    case lir_mul:
      if (pop_fpu_stack)       __ fmulp(non_tos_index);
      else if (dest_is_tos)    __ fmul (non_tos_index);
      else                     __ fmula(non_tos_index);
      break;

    case lir_div_strictfp: // fall through
    case lir_div:
      if (left_is_tos) {
        if (pop_fpu_stack)     __ fdivrp(non_tos_index);
        else if (dest_is_tos)  __ fdiv  (non_tos_index);
        else                   __ fdivra(non_tos_index);
      } else {
        if (pop_fpu_stack)     __ fdivp (non_tos_index);
        else if (dest_is_tos)  __ fdivr (non_tos_index);
        else                   __ fdiva (non_tos_index);
      }
      break;

    case lir_rem:
      assert(left_is_tos && dest_is_tos && right_index == 1, "must be guaranteed by FPU stack allocation");
      __ fremr(noreg);
      break;

    default:
      ShouldNotReachHere();
  }
}


void LIR_Assembler::intrinsic_op(LIR_Code code, LIR_Opr value, LIR_Opr unused, LIR_Opr dest, LIR_Op* op) {
  if (value->is_double_xmm()) {
    switch(code) {
      case lir_abs :
        {
          if (dest->as_xmm_double_reg() != value->as_xmm_double_reg()) {
            __ movdbl(dest->as_xmm_double_reg(), value->as_xmm_double_reg());
          }
          __ andpd(dest->as_xmm_double_reg(),
                    ExternalAddress((address)double_signmask_pool));
        }
        break;

      case lir_sqrt: __ sqrtsd(dest->as_xmm_double_reg(), value->as_xmm_double_reg()); break;
      // all other intrinsics are not available in the SSE instruction set, so FPU is used
      default      : ShouldNotReachHere();
    }

  } else if (value->is_double_fpu()) {
    assert(value->fpu_regnrLo() == 0 && dest->fpu_regnrLo() == 0, "both must be on TOS");
    switch(code) {
      case lir_log   : __ flog() ; break;
      case lir_log10 : __ flog10() ; break;
      case lir_abs   : __ fabs() ; break;
      case lir_sqrt  : __ fsqrt(); break;
      case lir_sin   :
        // Should consider not saving rbx, if not necessary
        __ trigfunc('s', op->as_Op2()->fpu_stack_size());
        break;
      case lir_cos :
        // Should consider not saving rbx, if not necessary
        assert(op->as_Op2()->fpu_stack_size() <= 6, "sin and cos need two free stack slots");
        __ trigfunc('c', op->as_Op2()->fpu_stack_size());
        break;
      case lir_tan :
        // Should consider not saving rbx, if not necessary
        __ trigfunc('t', op->as_Op2()->fpu_stack_size());
        break;
      default      : ShouldNotReachHere();
    }
  } else {
    Unimplemented();
  }
}

void LIR_Assembler::logic_op(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dst) {
  // assert(left->destroys_register(), "check");
  if (left->is_single_cpu()) {
    Register reg = left->as_register();
    if (right->is_constant()) {
      int val = right->as_constant_ptr()->as_jint();
      switch (code) {
        case lir_logic_and: __ andl (reg, val); break;
        case lir_logic_or:  __ orl  (reg, val); break;
        case lir_logic_xor: __ xorl (reg, val); break;
        default: ShouldNotReachHere();
      }
    } else if (right->is_stack()) {
      // added support for stack operands
      Address raddr = frame_map()->address_for_slot(right->single_stack_ix());
      switch (code) {
        case lir_logic_and: __ andl (reg, raddr); break;
        case lir_logic_or:  __ orl  (reg, raddr); break;
        case lir_logic_xor: __ xorl (reg, raddr); break;
        default: ShouldNotReachHere();
      }
    } else {
      Register rright = right->as_register();
      switch (code) {
        case lir_logic_and: __ andptr (reg, rright); break;
        case lir_logic_or : __ orptr  (reg, rright); break;
        case lir_logic_xor: __ xorptr (reg, rright); break;
        default: ShouldNotReachHere();
      }
    }
    move_regs(reg, dst->as_register());
  } else {
    Register l_lo = left->as_register_lo();
    Register l_hi = left->as_register_hi();
    if (right->is_constant()) {
#ifdef _LP64
      __ mov64(rscratch1, right->as_constant_ptr()->as_jlong());
      switch (code) {
        case lir_logic_and:
          __ andq(l_lo, rscratch1);
          break;
        case lir_logic_or:
          __ orq(l_lo, rscratch1);
          break;
        case lir_logic_xor:
          __ xorq(l_lo, rscratch1);
          break;
        default: ShouldNotReachHere();
      }
#else
      int r_lo = right->as_constant_ptr()->as_jint_lo();
      int r_hi = right->as_constant_ptr()->as_jint_hi();
      switch (code) {
        case lir_logic_and:
          __ andl(l_lo, r_lo);
          __ andl(l_hi, r_hi);
          break;
        case lir_logic_or:
          __ orl(l_lo, r_lo);
          __ orl(l_hi, r_hi);
          break;
        case lir_logic_xor:
          __ xorl(l_lo, r_lo);
          __ xorl(l_hi, r_hi);
          break;
        default: ShouldNotReachHere();
      }
#endif // _LP64
    } else {
      Register r_lo = right->as_register_lo();
      Register r_hi = right->as_register_hi();
      assert(l_lo != r_hi, "overwriting registers");
      switch (code) {
        case lir_logic_and:
          __ andptr(l_lo, r_lo);
          NOT_LP64(__ andptr(l_hi, r_hi);)
          break;
        case lir_logic_or:
          __ orptr(l_lo, r_lo);
          NOT_LP64(__ orptr(l_hi, r_hi);)
          break;
        case lir_logic_xor:
          __ xorptr(l_lo, r_lo);
          NOT_LP64(__ xorptr(l_hi, r_hi);)
          break;
        default: ShouldNotReachHere();
      }
    }

    Register dst_lo = dst->as_register_lo();
    Register dst_hi = dst->as_register_hi();

#ifdef _LP64
    move_regs(l_lo, dst_lo);
#else
    if (dst_lo == l_hi) {
      assert(dst_hi != l_lo, "overwriting registers");
      move_regs(l_hi, dst_hi);
      move_regs(l_lo, dst_lo);
    } else {
      assert(dst_lo != l_hi, "overwriting registers");
      move_regs(l_lo, dst_lo);
      move_regs(l_hi, dst_hi);
    }
#endif // _LP64
  }
}


// we assume that rax, and rdx can be overwritten
void LIR_Assembler::arithmetic_idiv(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr temp, LIR_Opr result, CodeEmitInfo* info) {

  assert(left->is_single_cpu(),   "left must be register");
  assert(right->is_single_cpu() || right->is_constant(),  "right must be register or constant");
  assert(result->is_single_cpu(), "result must be register");

  //  assert(left->destroys_register(), "check");
  //  assert(right->destroys_register(), "check");

  Register lreg = left->as_register();
  Register dreg = result->as_register();

  if (right->is_constant()) {
    int divisor = right->as_constant_ptr()->as_jint();
    assert(divisor > 0 && is_power_of_2(divisor), "must be");
    if (code == lir_idiv) {
      assert(lreg == rax, "must be rax,");
      assert(temp->as_register() == rdx, "tmp register must be rdx");
      __ cdql(); // sign extend into rdx:rax
      if (divisor == 2) {
        __ subl(lreg, rdx);
      } else {
        __ andl(rdx, divisor - 1);
        __ addl(lreg, rdx);
      }
      __ sarl(lreg, log2_intptr(divisor));
      move_regs(lreg, dreg);
    } else if (code == lir_irem) {
      Label done;
      __ mov(dreg, lreg);
      __ andl(dreg, 0x80000000 | (divisor - 1));
      __ jcc(Assembler::positive, done);
      __ decrement(dreg);
      __ orl(dreg, ~(divisor - 1));
      __ increment(dreg);
      __ bind(done);
    } else {
      ShouldNotReachHere();
    }
  } else {
    Register rreg = right->as_register();
    assert(lreg == rax, "left register must be rax,");
    assert(rreg != rdx, "right register must not be rdx");
    assert(temp->as_register() == rdx, "tmp register must be rdx");

    move_regs(lreg, rax);

    int idivl_offset = __ corrected_idivl(rreg);
    add_debug_info_for_div0(idivl_offset, info);
    if (code == lir_irem) {
      move_regs(rdx, dreg); // result is in rdx
    } else {
      move_regs(rax, dreg);
    }
  }
}


void LIR_Assembler::comp_op(LIR_Condition condition, LIR_Opr opr1, LIR_Opr opr2, LIR_Op2* op) {
  if (opr1->is_single_cpu()) {
    Register reg1 = opr1->as_register();
    if (opr2->is_single_cpu()) {
      // cpu register - cpu register
      if (opr1->type() == T_OBJECT || opr1->type() == T_ARRAY) {
        __ cmpptr(reg1, opr2->as_register());
      } else {
        assert(opr2->type() != T_OBJECT && opr2->type() != T_ARRAY, "cmp int, oop?");
        __ cmpl(reg1, opr2->as_register());
      }
    } else if (opr2->is_stack()) {
      // cpu register - stack
      if (opr1->type() == T_OBJECT || opr1->type() == T_ARRAY) {
        __ cmpptr(reg1, frame_map()->address_for_slot(opr2->single_stack_ix()));
      } else {
        __ cmpl(reg1, frame_map()->address_for_slot(opr2->single_stack_ix()));
      }
    } else if (opr2->is_constant()) {
      // cpu register - constant
      LIR_Const* c = opr2->as_constant_ptr();
      if (c->type() == T_INT) {
        __ cmpl(reg1, c->as_jint());
      } else if (c->type() == T_OBJECT || c->type() == T_ARRAY) {
        // In 64bit oops are single register
        jobject o = c->as_jobject();
        if (o == NULL) {
          __ cmpptr(reg1, (int32_t)NULL_WORD);
        } else {
#ifdef _LP64
          __ movoop(rscratch1, o);
          __ cmpptr(reg1, rscratch1);
#else
          __ cmpoop(reg1, c->as_jobject());
#endif // _LP64
        }
      } else {
        ShouldNotReachHere();
      }
      // cpu register - address
    } else if (opr2->is_address()) {
      if (op->info() != NULL) {
        add_debug_info_for_null_check_here(op->info());
      }
      __ cmpl(reg1, as_Address(opr2->as_address_ptr()));
    } else {
      ShouldNotReachHere();
    }

  } else if(opr1->is_double_cpu()) {
    Register xlo = opr1->as_register_lo();
    Register xhi = opr1->as_register_hi();
    if (opr2->is_double_cpu()) {
#ifdef _LP64
      __ cmpptr(xlo, opr2->as_register_lo());
#else
      // cpu register - cpu register
      Register ylo = opr2->as_register_lo();
      Register yhi = opr2->as_register_hi();
      __ subl(xlo, ylo);
      __ sbbl(xhi, yhi);
      if (condition == lir_cond_equal || condition == lir_cond_notEqual) {
        __ orl(xhi, xlo);
      }
#endif // _LP64
    } else if (opr2->is_constant()) {
      // cpu register - constant 0
      assert(opr2->as_jlong() == (jlong)0, "only handles zero");
#ifdef _LP64
      __ cmpptr(xlo, (int32_t)opr2->as_jlong());
#else
      assert(condition == lir_cond_equal || condition == lir_cond_notEqual, "only handles equals case");
      __ orl(xhi, xlo);
#endif // _LP64
    } else {
      ShouldNotReachHere();
    }

  } else if (opr1->is_single_xmm()) {
    XMMRegister reg1 = opr1->as_xmm_float_reg();
    if (opr2->is_single_xmm()) {
      // xmm register - xmm register
      __ ucomiss(reg1, opr2->as_xmm_float_reg());
    } else if (opr2->is_stack()) {
      // xmm register - stack
      __ ucomiss(reg1, frame_map()->address_for_slot(opr2->single_stack_ix()));
    } else if (opr2->is_constant()) {
      // xmm register - constant
      __ ucomiss(reg1, InternalAddress(float_constant(opr2->as_jfloat())));
    } else if (opr2->is_address()) {
      // xmm register - address
      if (op->info() != NULL) {
        add_debug_info_for_null_check_here(op->info());
      }
      __ ucomiss(reg1, as_Address(opr2->as_address_ptr()));
    } else {
      ShouldNotReachHere();
    }

  } else if (opr1->is_double_xmm()) {
    XMMRegister reg1 = opr1->as_xmm_double_reg();
    if (opr2->is_double_xmm()) {
      // xmm register - xmm register
      __ ucomisd(reg1, opr2->as_xmm_double_reg());
    } else if (opr2->is_stack()) {
      // xmm register - stack
      __ ucomisd(reg1, frame_map()->address_for_slot(opr2->double_stack_ix()));
    } else if (opr2->is_constant()) {
      // xmm register - constant
      __ ucomisd(reg1, InternalAddress(double_constant(opr2->as_jdouble())));
    } else if (opr2->is_address()) {
      // xmm register - address
      if (op->info() != NULL) {
        add_debug_info_for_null_check_here(op->info());
      }
      __ ucomisd(reg1, as_Address(opr2->pointer()->as_address()));
    } else {
      ShouldNotReachHere();
    }

  } else if(opr1->is_single_fpu() || opr1->is_double_fpu()) {
    assert(opr1->is_fpu_register() && opr1->fpu() == 0, "currently left-hand side must be on TOS (relax this restriction)");
    assert(opr2->is_fpu_register(), "both must be registers");
    __ fcmp(noreg, opr2->fpu(), op->fpu_pop_count() > 0, op->fpu_pop_count() > 1);

  } else if (opr1->is_address() && opr2->is_constant()) {
    LIR_Const* c = opr2->as_constant_ptr();
#ifdef _LP64
    if (c->type() == T_OBJECT || c->type() == T_ARRAY) {
      assert(condition == lir_cond_equal || condition == lir_cond_notEqual, "need to reverse");
      __ movoop(rscratch1, c->as_jobject());
    }
#endif // LP64
    if (op->info() != NULL) {
      add_debug_info_for_null_check_here(op->info());
    }
    // special case: address - constant
    LIR_Address* addr = opr1->as_address_ptr();
    if (c->type() == T_INT) {
      __ cmpl(as_Address(addr), c->as_jint());
    } else if (c->type() == T_OBJECT || c->type() == T_ARRAY) {
#ifdef _LP64
      // %%% Make this explode if addr isn't reachable until we figure out a
      // better strategy by giving noreg as the temp for as_Address
      __ cmpptr(rscratch1, as_Address(addr, noreg));
#else
      __ cmpoop(as_Address(addr), c->as_jobject());
#endif // _LP64
    } else {
      ShouldNotReachHere();
    }

  } else {
    ShouldNotReachHere();
  }
}

void LIR_Assembler::comp_fl2i(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dst, LIR_Op2* op) {
  if (code == lir_cmp_fd2i || code == lir_ucmp_fd2i) {
    if (left->is_single_xmm()) {
      assert(right->is_single_xmm(), "must match");
      __ cmpss2int(left->as_xmm_float_reg(), right->as_xmm_float_reg(), dst->as_register(), code == lir_ucmp_fd2i);
    } else if (left->is_double_xmm()) {
      assert(right->is_double_xmm(), "must match");
      __ cmpsd2int(left->as_xmm_double_reg(), right->as_xmm_double_reg(), dst->as_register(), code == lir_ucmp_fd2i);

    } else {
      assert(left->is_single_fpu() || left->is_double_fpu(), "must be");
      assert(right->is_single_fpu() || right->is_double_fpu(), "must match");

      assert(left->fpu() == 0, "left must be on TOS");
      __ fcmp2int(dst->as_register(), code == lir_ucmp_fd2i, right->fpu(),
                  op->fpu_pop_count() > 0, op->fpu_pop_count() > 1);
    }
  } else {
    assert(code == lir_cmp_l2i, "check");
#ifdef _LP64
    Label done;
    Register dest = dst->as_register();
    __ cmpptr(left->as_register_lo(), right->as_register_lo());
    __ movl(dest, -1);
    __ jccb(Assembler::less, done);
    __ set_byte_if_not_zero(dest);
    __ movzbl(dest, dest);
    __ bind(done);
#else
    __ lcmp2int(left->as_register_hi(),
                left->as_register_lo(),
                right->as_register_hi(),
                right->as_register_lo());
    move_regs(left->as_register_hi(), dst->as_register());
#endif // _LP64
  }
}


void LIR_Assembler::align_call(LIR_Code code) {
  if (os::is_MP()) {
    // make sure that the displacement word of the call ends up word aligned
    int offset = __ offset();
    switch (code) {
      case lir_static_call:
      case lir_optvirtual_call:
      case lir_dynamic_call:
        offset += NativeCall::displacement_offset;
        break;
      case lir_icvirtual_call:
        offset += NativeCall::displacement_offset + NativeMovConstReg::instruction_size;
      break;
      case lir_virtual_call:  // currently, sparc-specific for niagara
      default: ShouldNotReachHere();
    }
    while (offset++ % BytesPerWord != 0) {
      __ nop();
    }
  }
}


void LIR_Assembler::call(LIR_OpJavaCall* op, relocInfo::relocType rtype) {
  assert(!os::is_MP() || (__ offset() + NativeCall::displacement_offset) % BytesPerWord == 0,
         "must be aligned");
  __ call(AddressLiteral(op->addr(), rtype));
  add_call_info(code_offset(), op->info(), op->is_method_handle_invoke());
}


void LIR_Assembler::ic_call(LIR_OpJavaCall* op) {
  RelocationHolder rh = virtual_call_Relocation::spec(pc());
  __ movoop(IC_Klass, (jobject)Universe::non_oop_word());
  assert(!os::is_MP() ||
         (__ offset() + NativeCall::displacement_offset) % BytesPerWord == 0,
         "must be aligned");
  __ call(AddressLiteral(op->addr(), rh));
  add_call_info(code_offset(), op->info(), op->is_method_handle_invoke());
}


/* Currently, vtable-dispatch is only enabled for sparc platforms */
void LIR_Assembler::vtable_call(LIR_OpJavaCall* op) {
  ShouldNotReachHere();
}


void LIR_Assembler::preserve_SP(LIR_OpJavaCall* op) {
  __ movptr(FrameMap::method_handle_invoke_SP_save_opr()->as_register(), rsp);
}


void LIR_Assembler::restore_SP(LIR_OpJavaCall* op) {
  __ movptr(rsp, FrameMap::method_handle_invoke_SP_save_opr()->as_register());
}


void LIR_Assembler::emit_static_call_stub() {
  address call_pc = __ pc();
  address stub = __ start_a_stub(call_stub_size);
  if (stub == NULL) {
    bailout("static call stub overflow");
    return;
  }

  int start = __ offset();
  if (os::is_MP()) {
    // make sure that the displacement word of the call ends up word aligned
    int offset = __ offset() + NativeMovConstReg::instruction_size + NativeCall::displacement_offset;
    while (offset++ % BytesPerWord != 0) {
      __ nop();
    }
  }
  __ relocate(static_stub_Relocation::spec(call_pc));
  __ movoop(rbx, (jobject)NULL);
  // must be set to -1 at code generation time
  assert(!os::is_MP() || ((__ offset() + 1) % BytesPerWord) == 0, "must be aligned on MP");
  // On 64bit this will die since it will take a movq & jmp, must be only a jmp
  __ jump(RuntimeAddress(__ pc()));

  assert(__ offset() - start <= call_stub_size, "stub too big");
  __ end_a_stub();
}


void LIR_Assembler::throw_op(LIR_Opr exceptionPC, LIR_Opr exceptionOop, CodeEmitInfo* info) {
  assert(exceptionOop->as_register() == rax, "must match");
  assert(exceptionPC->as_register() == rdx, "must match");

  // exception object is not added to oop map by LinearScan
  // (LinearScan assumes that no oops are in fixed registers)
  info->add_register_oop(exceptionOop);
  Runtime1::StubID unwind_id;

  // get current pc information
  // pc is only needed if the method has an exception handler, the unwind code does not need it.
  int pc_for_athrow_offset = __ offset();
  InternalAddress pc_for_athrow(__ pc());
  __ lea(exceptionPC->as_register(), pc_for_athrow);
  add_call_info(pc_for_athrow_offset, info); // for exception handler

  __ verify_not_null_oop(rax);
  // search an exception handler (rax: exception oop, rdx: throwing pc)
  if (compilation()->has_fpu_code()) {
    unwind_id = Runtime1::handle_exception_id;
  } else {
    unwind_id = Runtime1::handle_exception_nofpu_id;
  }
  __ call(RuntimeAddress(Runtime1::entry_for(unwind_id)));

  // enough room for two byte trap
  __ nop();
}


void LIR_Assembler::unwind_op(LIR_Opr exceptionOop) {
  assert(exceptionOop->as_register() == rax, "must match");

  __ jmp(_unwind_handler_entry);
}


void LIR_Assembler::shift_op(LIR_Code code, LIR_Opr left, LIR_Opr count, LIR_Opr dest, LIR_Opr tmp) {

  // optimized version for linear scan:
  // * count must be already in ECX (guaranteed by LinearScan)
  // * left and dest must be equal
  // * tmp must be unused
  assert(count->as_register() == SHIFT_count, "count must be in ECX");
  assert(left == dest, "left and dest must be equal");
  assert(tmp->is_illegal(), "wasting a register if tmp is allocated");

  if (left->is_single_cpu()) {
    Register value = left->as_register();
    assert(value != SHIFT_count, "left cannot be ECX");

    switch (code) {
      case lir_shl:  __ shll(value); break;
      case lir_shr:  __ sarl(value); break;
      case lir_ushr: __ shrl(value); break;
      default: ShouldNotReachHere();
    }
  } else if (left->is_double_cpu()) {
    Register lo = left->as_register_lo();
    Register hi = left->as_register_hi();
    assert(lo != SHIFT_count && hi != SHIFT_count, "left cannot be ECX");
#ifdef _LP64
    switch (code) {
      case lir_shl:  __ shlptr(lo);        break;
      case lir_shr:  __ sarptr(lo);        break;
      case lir_ushr: __ shrptr(lo);        break;
      default: ShouldNotReachHere();
    }
#else

    switch (code) {
      case lir_shl:  __ lshl(hi, lo);        break;
      case lir_shr:  __ lshr(hi, lo, true);  break;
      case lir_ushr: __ lshr(hi, lo, false); break;
      default: ShouldNotReachHere();
    }
#endif // LP64
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::shift_op(LIR_Code code, LIR_Opr left, jint count, LIR_Opr dest) {
  if (dest->is_single_cpu()) {
    // first move left into dest so that left is not destroyed by the shift
    Register value = dest->as_register();
    count = count & 0x1F; // Java spec

    move_regs(left->as_register(), value);
    switch (code) {
      case lir_shl:  __ shll(value, count); break;
      case lir_shr:  __ sarl(value, count); break;
      case lir_ushr: __ shrl(value, count); break;
      default: ShouldNotReachHere();
    }
  } else if (dest->is_double_cpu()) {
#ifndef _LP64
    Unimplemented();
#else
    // first move left into dest so that left is not destroyed by the shift
    Register value = dest->as_register_lo();
    count = count & 0x1F; // Java spec

    move_regs(left->as_register_lo(), value);
    switch (code) {
      case lir_shl:  __ shlptr(value, count); break;
      case lir_shr:  __ sarptr(value, count); break;
      case lir_ushr: __ shrptr(value, count); break;
      default: ShouldNotReachHere();
    }
#endif // _LP64
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::store_parameter(Register r, int offset_from_rsp_in_words) {
  assert(offset_from_rsp_in_words >= 0, "invalid offset from rsp");
  int offset_from_rsp_in_bytes = offset_from_rsp_in_words * BytesPerWord;
  assert(offset_from_rsp_in_bytes < frame_map()->reserved_argument_area_size(), "invalid offset");
  __ movptr (Address(rsp, offset_from_rsp_in_bytes), r);
}


void LIR_Assembler::store_parameter(jint c,     int offset_from_rsp_in_words) {
  assert(offset_from_rsp_in_words >= 0, "invalid offset from rsp");
  int offset_from_rsp_in_bytes = offset_from_rsp_in_words * BytesPerWord;
  assert(offset_from_rsp_in_bytes < frame_map()->reserved_argument_area_size(), "invalid offset");
  __ movptr (Address(rsp, offset_from_rsp_in_bytes), c);
}


void LIR_Assembler::store_parameter(jobject o,  int offset_from_rsp_in_words) {
  assert(offset_from_rsp_in_words >= 0, "invalid offset from rsp");
  int offset_from_rsp_in_bytes = offset_from_rsp_in_words * BytesPerWord;
  assert(offset_from_rsp_in_bytes < frame_map()->reserved_argument_area_size(), "invalid offset");
  __ movoop (Address(rsp, offset_from_rsp_in_bytes), o);
}


// This code replaces a call to arraycopy; no exception may
// be thrown in this code, they must be thrown in the System.arraycopy
// activation frame; we could save some checks if this would not be the case
void LIR_Assembler::emit_arraycopy(LIR_OpArrayCopy* op) {
  ciArrayKlass* default_type = op->expected_type();
  Register src = op->src()->as_register();
  Register dst = op->dst()->as_register();
  Register src_pos = op->src_pos()->as_register();
  Register dst_pos = op->dst_pos()->as_register();
  Register length  = op->length()->as_register();
  Register tmp = op->tmp()->as_register();

  CodeStub* stub = op->stub();
  int flags = op->flags();
  BasicType basic_type = default_type != NULL ? default_type->element_type()->basic_type() : T_ILLEGAL;
  if (basic_type == T_ARRAY) basic_type = T_OBJECT;

  // if we don't know anything or it's an object array, just go through the generic arraycopy
  if (default_type == NULL) {
    Label done;
    // save outgoing arguments on stack in case call to System.arraycopy is needed
    // HACK ALERT. This code used to push the parameters in a hardwired fashion
    // for interpreter calling conventions. Now we have to do it in new style conventions.
    // For the moment until C1 gets the new register allocator I just force all the
    // args to the right place (except the register args) and then on the back side
    // reload the register args properly if we go slow path. Yuck

    // These are proper for the calling convention

    store_parameter(length, 2);
    store_parameter(dst_pos, 1);
    store_parameter(dst, 0);

    // these are just temporary placements until we need to reload
    store_parameter(src_pos, 3);
    store_parameter(src, 4);
    NOT_LP64(assert(src == rcx && src_pos == rdx, "mismatch in calling convention");)

    address entry = CAST_FROM_FN_PTR(address, Runtime1::arraycopy);

    // pass arguments: may push as this is not a safepoint; SP must be fix at each safepoint
#ifdef _LP64
    // The arguments are in java calling convention so we can trivially shift them to C
    // convention
    assert_different_registers(c_rarg0, j_rarg1, j_rarg2, j_rarg3, j_rarg4);
    __ mov(c_rarg0, j_rarg0);
    assert_different_registers(c_rarg1, j_rarg2, j_rarg3, j_rarg4);
    __ mov(c_rarg1, j_rarg1);
    assert_different_registers(c_rarg2, j_rarg3, j_rarg4);
    __ mov(c_rarg2, j_rarg2);
    assert_different_registers(c_rarg3, j_rarg4);
    __ mov(c_rarg3, j_rarg3);
#ifdef _WIN64
    // Allocate abi space for args but be sure to keep stack aligned
    __ subptr(rsp, 6*wordSize);
    store_parameter(j_rarg4, 4);
    __ call(RuntimeAddress(entry));
    __ addptr(rsp, 6*wordSize);
#else
    __ mov(c_rarg4, j_rarg4);
    __ call(RuntimeAddress(entry));
#endif // _WIN64
#else
    __ push(length);
    __ push(dst_pos);
    __ push(dst);
    __ push(src_pos);
    __ push(src);
    __ call_VM_leaf(entry, 5); // removes pushed parameter from the stack

#endif // _LP64

    __ cmpl(rax, 0);
    __ jcc(Assembler::equal, *stub->continuation());

    // Reload values from the stack so they are where the stub
    // expects them.
    __ movptr   (dst,     Address(rsp, 0*BytesPerWord));
    __ movptr   (dst_pos, Address(rsp, 1*BytesPerWord));
    __ movptr   (length,  Address(rsp, 2*BytesPerWord));
    __ movptr   (src_pos, Address(rsp, 3*BytesPerWord));
    __ movptr   (src,     Address(rsp, 4*BytesPerWord));
    __ jmp(*stub->entry());

    __ bind(*stub->continuation());
    return;
  }

  assert(default_type != NULL && default_type->is_array_klass() && default_type->is_loaded(), "must be true at this point");

  int elem_size = type2aelembytes(basic_type);
  int shift_amount;
  Address::ScaleFactor scale;

  switch (elem_size) {
    case 1 :
      shift_amount = 0;
      scale = Address::times_1;
      break;
    case 2 :
      shift_amount = 1;
      scale = Address::times_2;
      break;
    case 4 :
      shift_amount = 2;
      scale = Address::times_4;
      break;
    case 8 :
      shift_amount = 3;
      scale = Address::times_8;
      break;
    default:
      ShouldNotReachHere();
  }

  Address src_length_addr = Address(src, arrayOopDesc::length_offset_in_bytes());
  Address dst_length_addr = Address(dst, arrayOopDesc::length_offset_in_bytes());
  Address src_klass_addr = Address(src, oopDesc::klass_offset_in_bytes());
  Address dst_klass_addr = Address(dst, oopDesc::klass_offset_in_bytes());

  // length and pos's are all sign extended at this point on 64bit

  // test for NULL
  if (flags & LIR_OpArrayCopy::src_null_check) {
    __ testptr(src, src);
    __ jcc(Assembler::zero, *stub->entry());
  }
  if (flags & LIR_OpArrayCopy::dst_null_check) {
    __ testptr(dst, dst);
    __ jcc(Assembler::zero, *stub->entry());
  }

  // check if negative
  if (flags & LIR_OpArrayCopy::src_pos_positive_check) {
    __ testl(src_pos, src_pos);
    __ jcc(Assembler::less, *stub->entry());
  }
  if (flags & LIR_OpArrayCopy::dst_pos_positive_check) {
    __ testl(dst_pos, dst_pos);
    __ jcc(Assembler::less, *stub->entry());
  }
  if (flags & LIR_OpArrayCopy::length_positive_check) {
    __ testl(length, length);
    __ jcc(Assembler::less, *stub->entry());
  }

  if (flags & LIR_OpArrayCopy::src_range_check) {
    __ lea(tmp, Address(src_pos, length, Address::times_1, 0));
    __ cmpl(tmp, src_length_addr);
    __ jcc(Assembler::above, *stub->entry());
  }
  if (flags & LIR_OpArrayCopy::dst_range_check) {
    __ lea(tmp, Address(dst_pos, length, Address::times_1, 0));
    __ cmpl(tmp, dst_length_addr);
    __ jcc(Assembler::above, *stub->entry());
  }

  if (flags & LIR_OpArrayCopy::type_check) {
    __ movptr(tmp, src_klass_addr);
    __ cmpptr(tmp, dst_klass_addr);
    __ jcc(Assembler::notEqual, *stub->entry());
  }

#ifdef ASSERT
  if (basic_type != T_OBJECT || !(flags & LIR_OpArrayCopy::type_check)) {
    // Sanity check the known type with the incoming class.  For the
    // primitive case the types must match exactly with src.klass and
    // dst.klass each exactly matching the default type.  For the
    // object array case, if no type check is needed then either the
    // dst type is exactly the expected type and the src type is a
    // subtype which we can't check or src is the same array as dst
    // but not necessarily exactly of type default_type.
    Label known_ok, halt;
    __ movoop(tmp, default_type->constant_encoding());
    if (basic_type != T_OBJECT) {
      __ cmpptr(tmp, dst_klass_addr);
      __ jcc(Assembler::notEqual, halt);
      __ cmpptr(tmp, src_klass_addr);
      __ jcc(Assembler::equal, known_ok);
    } else {
      __ cmpptr(tmp, dst_klass_addr);
      __ jcc(Assembler::equal, known_ok);
      __ cmpptr(src, dst);
      __ jcc(Assembler::equal, known_ok);
    }
    __ bind(halt);
    __ stop("incorrect type information in arraycopy");
    __ bind(known_ok);
  }
#endif

  if (shift_amount > 0 && basic_type != T_OBJECT) {
    __ shlptr(length, shift_amount);
  }

#ifdef _LP64
  assert_different_registers(c_rarg0, dst, dst_pos, length);
  __ movl2ptr(src_pos, src_pos); //higher 32bits must be null
  __ lea(c_rarg0, Address(src, src_pos, scale, arrayOopDesc::base_offset_in_bytes(basic_type)));
  assert_different_registers(c_rarg1, length);
  __ movl2ptr(dst_pos, dst_pos); //higher 32bits must be null
  __ lea(c_rarg1, Address(dst, dst_pos, scale, arrayOopDesc::base_offset_in_bytes(basic_type)));
  __ mov(c_rarg2, length);

#else
  __ lea(tmp, Address(src, src_pos, scale, arrayOopDesc::base_offset_in_bytes(basic_type)));
  store_parameter(tmp, 0);
  __ lea(tmp, Address(dst, dst_pos, scale, arrayOopDesc::base_offset_in_bytes(basic_type)));
  store_parameter(tmp, 1);
  store_parameter(length, 2);
#endif // _LP64
  if (basic_type == T_OBJECT) {
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, Runtime1::oop_arraycopy), 0);
  } else {
    __ call_VM_leaf(CAST_FROM_FN_PTR(address, Runtime1::primitive_arraycopy), 0);
  }

  __ bind(*stub->continuation());
}


void LIR_Assembler::emit_lock(LIR_OpLock* op) {
  Register obj = op->obj_opr()->as_register();  // may not be an oop
  Register hdr = op->hdr_opr()->as_register();
  Register lock = op->lock_opr()->as_register();
  if (!UseFastLocking) {
    __ jmp(*op->stub()->entry());
  } else if (op->code() == lir_lock) {
    Register scratch = noreg;
    if (UseBiasedLocking) {
      scratch = op->scratch_opr()->as_register();
    }
    assert(BasicLock::displaced_header_offset_in_bytes() == 0, "lock_reg must point to the displaced header");
    // add debug info for NullPointerException only if one is possible
    int null_check_offset = __ lock_object(hdr, obj, lock, scratch, *op->stub()->entry());
    if (op->info() != NULL) {
      add_debug_info_for_null_check(null_check_offset, op->info());
    }
    // done
  } else if (op->code() == lir_unlock) {
    assert(BasicLock::displaced_header_offset_in_bytes() == 0, "lock_reg must point to the displaced header");
    __ unlock_object(hdr, obj, lock, *op->stub()->entry());
  } else {
    Unimplemented();
  }
  __ bind(*op->stub()->continuation());
}


void LIR_Assembler::emit_profile_call(LIR_OpProfileCall* op) {
  ciMethod* method = op->profiled_method();
  int bci          = op->profiled_bci();

  // Update counter for all call types
  ciMethodData* md = method->method_data();
  if (md == NULL) {
    bailout("out of memory building methodDataOop");
    return;
  }
  ciProfileData* data = md->bci_to_data(bci);
  assert(data->is_CounterData(), "need CounterData for calls");
  assert(op->mdo()->is_single_cpu(),  "mdo must be allocated");
  Register mdo  = op->mdo()->as_register();
  __ movoop(mdo, md->constant_encoding());
  Address counter_addr(mdo, md->byte_offset_of_slot(data, CounterData::count_offset()));
  Bytecodes::Code bc = method->java_code_at_bci(bci);
  // Perform additional virtual call profiling for invokevirtual and
  // invokeinterface bytecodes
  if ((bc == Bytecodes::_invokevirtual || bc == Bytecodes::_invokeinterface) &&
      Tier1ProfileVirtualCalls) {
    assert(op->recv()->is_single_cpu(), "recv must be allocated");
    Register recv = op->recv()->as_register();
    assert_different_registers(mdo, recv);
    assert(data->is_VirtualCallData(), "need VirtualCallData for virtual calls");
    ciKlass* known_klass = op->known_holder();
    if (Tier1OptimizeVirtualCallProfiling && known_klass != NULL) {
      // We know the type that will be seen at this call site; we can
      // statically update the methodDataOop rather than needing to do
      // dynamic tests on the receiver type

      // NOTE: we should probably put a lock around this search to
      // avoid collisions by concurrent compilations
      ciVirtualCallData* vc_data = (ciVirtualCallData*) data;
      uint i;
      for (i = 0; i < VirtualCallData::row_limit(); i++) {
        ciKlass* receiver = vc_data->receiver(i);
        if (known_klass->equals(receiver)) {
          Address data_addr(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_count_offset(i)));
          __ addl(data_addr, DataLayout::counter_increment);
          return;
        }
      }

      // Receiver type not found in profile data; select an empty slot

      // Note that this is less efficient than it should be because it
      // always does a write to the receiver part of the
      // VirtualCallData rather than just the first time
      for (i = 0; i < VirtualCallData::row_limit(); i++) {
        ciKlass* receiver = vc_data->receiver(i);
        if (receiver == NULL) {
          Address recv_addr(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_offset(i)));
          __ movoop(recv_addr, known_klass->constant_encoding());
          Address data_addr(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_count_offset(i)));
          __ addl(data_addr, DataLayout::counter_increment);
          return;
        }
      }
    } else {
      __ movptr(recv, Address(recv, oopDesc::klass_offset_in_bytes()));
      Label update_done;
      uint i;
      for (i = 0; i < VirtualCallData::row_limit(); i++) {
        Label next_test;
        // See if the receiver is receiver[n].
        __ cmpptr(recv, Address(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_offset(i))));
        __ jcc(Assembler::notEqual, next_test);
        Address data_addr(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_count_offset(i)));
        __ addl(data_addr, DataLayout::counter_increment);
        __ jmp(update_done);
        __ bind(next_test);
      }

      // Didn't find receiver; find next empty slot and fill it in
      for (i = 0; i < VirtualCallData::row_limit(); i++) {
        Label next_test;
        Address recv_addr(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_offset(i)));
        __ cmpptr(recv_addr, (int32_t)NULL_WORD);
        __ jcc(Assembler::notEqual, next_test);
        __ movptr(recv_addr, recv);
        __ movl(Address(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_count_offset(i))), DataLayout::counter_increment);
        __ jmp(update_done);
        __ bind(next_test);
      }
      // Receiver did not match any saved receiver and there is no empty row for it.
      // Increment total counter to indicate polymorphic case.
      __ addl(counter_addr, DataLayout::counter_increment);

      __ bind(update_done);
    }
  } else {
    // Static call
    __ addl(counter_addr, DataLayout::counter_increment);
  }
}


void LIR_Assembler::emit_delay(LIR_OpDelay*) {
  Unimplemented();
}


void LIR_Assembler::monitor_address(int monitor_no, LIR_Opr dst) {
  __ lea(dst->as_register(), frame_map()->address_for_monitor_lock(monitor_no));
}


void LIR_Assembler::align_backward_branch_target() {
  __ align(BytesPerWord);
}


void LIR_Assembler::negate(LIR_Opr left, LIR_Opr dest) {
  if (left->is_single_cpu()) {
    __ negl(left->as_register());
    move_regs(left->as_register(), dest->as_register());

  } else if (left->is_double_cpu()) {
    Register lo = left->as_register_lo();
#ifdef _LP64
    Register dst = dest->as_register_lo();
    __ movptr(dst, lo);
    __ negptr(dst);
#else
    Register hi = left->as_register_hi();
    __ lneg(hi, lo);
    if (dest->as_register_lo() == hi) {
      assert(dest->as_register_hi() != lo, "destroying register");
      move_regs(hi, dest->as_register_hi());
      move_regs(lo, dest->as_register_lo());
    } else {
      move_regs(lo, dest->as_register_lo());
      move_regs(hi, dest->as_register_hi());
    }
#endif // _LP64

  } else if (dest->is_single_xmm()) {
    if (left->as_xmm_float_reg() != dest->as_xmm_float_reg()) {
      __ movflt(dest->as_xmm_float_reg(), left->as_xmm_float_reg());
    }
    __ xorps(dest->as_xmm_float_reg(),
             ExternalAddress((address)float_signflip_pool));

  } else if (dest->is_double_xmm()) {
    if (left->as_xmm_double_reg() != dest->as_xmm_double_reg()) {
      __ movdbl(dest->as_xmm_double_reg(), left->as_xmm_double_reg());
    }
    __ xorpd(dest->as_xmm_double_reg(),
             ExternalAddress((address)double_signflip_pool));

  } else if (left->is_single_fpu() || left->is_double_fpu()) {
    assert(left->fpu() == 0, "arg must be on TOS");
    assert(dest->fpu() == 0, "dest must be TOS");
    __ fchs();

  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::leal(LIR_Opr addr, LIR_Opr dest) {
  assert(addr->is_address() && dest->is_register(), "check");
  Register reg;
  reg = dest->as_pointer_register();
  __ lea(reg, as_Address(addr->as_address_ptr()));
}



void LIR_Assembler::rt_call(LIR_Opr result, address dest, const LIR_OprList* args, LIR_Opr tmp, CodeEmitInfo* info) {
  assert(!tmp->is_valid(), "don't need temporary");
  __ call(RuntimeAddress(dest));
  if (info != NULL) {
    add_call_info_here(info);
  }
}


void LIR_Assembler::volatile_move_op(LIR_Opr src, LIR_Opr dest, BasicType type, CodeEmitInfo* info) {
  assert(type == T_LONG, "only for volatile long fields");

  if (info != NULL) {
    add_debug_info_for_null_check_here(info);
  }

  if (src->is_double_xmm()) {
    if (dest->is_double_cpu()) {
#ifdef _LP64
      __ movdq(dest->as_register_lo(), src->as_xmm_double_reg());
#else
      __ movdl(dest->as_register_lo(), src->as_xmm_double_reg());
      __ psrlq(src->as_xmm_double_reg(), 32);
      __ movdl(dest->as_register_hi(), src->as_xmm_double_reg());
#endif // _LP64
    } else if (dest->is_double_stack()) {
      __ movdbl(frame_map()->address_for_slot(dest->double_stack_ix()), src->as_xmm_double_reg());
    } else if (dest->is_address()) {
      __ movdbl(as_Address(dest->as_address_ptr()), src->as_xmm_double_reg());
    } else {
      ShouldNotReachHere();
    }

  } else if (dest->is_double_xmm()) {
    if (src->is_double_stack()) {
      __ movdbl(dest->as_xmm_double_reg(), frame_map()->address_for_slot(src->double_stack_ix()));
    } else if (src->is_address()) {
      __ movdbl(dest->as_xmm_double_reg(), as_Address(src->as_address_ptr()));
    } else {
      ShouldNotReachHere();
    }

  } else if (src->is_double_fpu()) {
    assert(src->fpu_regnrLo() == 0, "must be TOS");
    if (dest->is_double_stack()) {
      __ fistp_d(frame_map()->address_for_slot(dest->double_stack_ix()));
    } else if (dest->is_address()) {
      __ fistp_d(as_Address(dest->as_address_ptr()));
    } else {
      ShouldNotReachHere();
    }

  } else if (dest->is_double_fpu()) {
    assert(dest->fpu_regnrLo() == 0, "must be TOS");
    if (src->is_double_stack()) {
      __ fild_d(frame_map()->address_for_slot(src->double_stack_ix()));
    } else if (src->is_address()) {
      __ fild_d(as_Address(src->as_address_ptr()));
    } else {
      ShouldNotReachHere();
    }
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::membar() {
  // QQQ sparc TSO uses this,
  __ membar( Assembler::Membar_mask_bits(Assembler::StoreLoad));
}

void LIR_Assembler::membar_acquire() {
  // No x86 machines currently require load fences
  // __ load_fence();
}

void LIR_Assembler::membar_release() {
  // No x86 machines currently require store fences
  // __ store_fence();
}

void LIR_Assembler::get_thread(LIR_Opr result_reg) {
  assert(result_reg->is_register(), "check");
#ifdef _LP64
  // __ get_thread(result_reg->as_register_lo());
  __ mov(result_reg->as_register(), r15_thread);
#else
  __ get_thread(result_reg->as_register());
#endif // _LP64
}


void LIR_Assembler::peephole(LIR_List*) {
  // do nothing for now
}


#undef __
