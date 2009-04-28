/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

#include "incls/_precompiled.incl"
#include "incls/_vtableStubs_sparc.cpp.incl"

// machine-dependent part of VtableStubs: create vtableStub of correct size and
// initialize its code

#define __ masm->


#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread, oopDesc* receiver, int index);
#endif


// Used by compiler only; may use only caller saved, non-argument registers
// NOTE:  %%%% if any change is made to this stub make sure that the function
//             pd_code_size_limit is changed to ensure the correct size for VtableStub
VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {
  const int sparc_code_length = VtableStub::pd_code_size_limit(true);
  VtableStub* s = new(sparc_code_length) VtableStub(true, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), sparc_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#ifndef PRODUCT
  if (CountCompiledCalls) {
    Address ctr(G5, SharedRuntime::nof_megamorphic_calls_addr());
    __ sethi(ctr);
    __ ld(ctr, G3_scratch);
    __ inc(G3_scratch);
    __ st(G3_scratch, ctr);
  }
#endif /* PRODUCT */

  assert(VtableStub::receiver_location() == O0->as_VMReg(), "receiver expected in O0");

  // get receiver klass
  address npe_addr = __ pc();
  __ load_klass(O0, G3_scratch);

  // set methodOop (in case of interpreted method), and destination address
  int entry_offset = instanceKlass::vtable_start_offset() + vtable_index*vtableEntry::size();
#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    // check offset vs vtable length
    __ ld(G3_scratch, instanceKlass::vtable_length_offset()*wordSize, G5);
    __ cmp(G5, vtable_index*vtableEntry::size());
    __ br(Assembler::greaterUnsigned, false, Assembler::pt, L);
    __ delayed()->nop();
    __ set(vtable_index, O2);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, bad_compiled_vtable_index), O0, O2);
    __ bind(L);
  }
#endif
  int v_off = entry_offset*wordSize + vtableEntry::method_offset_in_bytes();
  if( __ is_simm13(v_off) ) {
    __ ld_ptr(G3, v_off, G5_method);
  } else {
    __ set(v_off,G5);
    __ ld_ptr(G3, G5, G5_method);
  }

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    __ br_notnull(G5_method, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("Vtable entry is ZERO");
    __ bind(L);
  }
#endif

  address ame_addr = __ pc();  // if the vtable entry is null, the method is abstract
                               // NOTE: for vtable dispatches, the vtable entry will never be null.

  __ ld_ptr(G5_method, in_bytes(methodOopDesc::from_compiled_offset()), G3_scratch);

  // jump to target (either compiled code or c2iadapter)
  __ JMP(G3_scratch, 0);
  // load methodOop (in case we call c2iadapter)
  __ delayed()->nop();

  masm->flush();

  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    tty->print_cr("vtable #%d at "PTR_FORMAT"[%d] left over: %d",
                  vtable_index, s->entry_point(),
                  (int)(s->code_end() - s->entry_point()),
                  (int)(s->code_end() - __ pc()));
  }
  guarantee(__ pc() <= s->code_end(), "overflowed buffer");
  // shut the door on sizing bugs
  int slop = 2*BytesPerInstWord;  // 32-bit offset is this much larger than a 13-bit one
  assert(vtable_index > 10 || __ pc() + slop <= s->code_end(), "room for sethi;add");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}


// NOTE:  %%%% if any change is made to this stub make sure that the function
//             pd_code_size_limit is changed to ensure the correct size for VtableStub
VtableStub* VtableStubs::create_itable_stub(int itable_index) {
  const int sparc_code_length = VtableStub::pd_code_size_limit(false);
  VtableStub* s = new(sparc_code_length) VtableStub(false, itable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), sparc_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

  Register G3_klassOop = G3_scratch;
  Register G5_interface = G5;  // Passed in as an argument
  Label search;

  // Entry arguments:
  //  G5_interface: Interface
  //  O0:           Receiver
  assert(VtableStub::receiver_location() == O0->as_VMReg(), "receiver expected in O0");

  // get receiver klass (also an implicit null-check)
  address npe_addr = __ pc();
  __ load_klass(O0, G3_klassOop);
  __ verify_oop(G3_klassOop);

  // Push a new window to get some temp registers.  This chops the head of all
  // my 64-bit %o registers in the LION build, but this is OK because no longs
  // are passed in the %o registers.  Instead, longs are passed in G1 and G4
  // and so those registers are not available here.
  __ save(SP,-frame::register_save_words*wordSize,SP);

#ifndef PRODUCT
  if (CountCompiledCalls) {
    Address ctr(L0, SharedRuntime::nof_megamorphic_calls_addr());
    __ sethi(ctr);
    __ ld(ctr, L1);
    __ inc(L1);
    __ st(L1, ctr);
  }
#endif /* PRODUCT */

  Label throw_icce;

  Register L5_method = L5;
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             G3_klassOop, G5_interface, itable_index,
                             // outputs: method, scan temp. reg
                             L5_method, L2, L3,
                             throw_icce);

#ifndef PRODUCT
  if (DebugVtables) {
    Label L01;
    __ bpr(Assembler::rc_nz, false, Assembler::pt, L5_method, L01);
    __ delayed()->nop();
    __ stop("methodOop is null");
    __ bind(L01);
    __ verify_oop(L5_method);
  }
#endif

  // If the following load is through a NULL pointer, we'll take an OS
  // exception that should translate into an AbstractMethodError.  We need the
  // window count to be correct at that time.
  __ restore(L5_method, 0, G5_method);
  // Restore registers *before* the AME point.

  address ame_addr = __ pc();   // if the vtable entry is null, the method is abstract
  __ ld_ptr(G5_method, in_bytes(methodOopDesc::from_compiled_offset()), G3_scratch);

  // G5_method:  methodOop
  // O0:         Receiver
  // G3_scratch: entry point
  __ JMP(G3_scratch, 0);
  __ delayed()->nop();

  __ bind(throw_icce);
  Address icce(G3_scratch, StubRoutines::throw_IncompatibleClassChangeError_entry());
  __ jump_to(icce, 0);
  __ delayed()->restore();

  masm->flush();

  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    tty->print_cr("itable #%d at "PTR_FORMAT"[%d] left over: %d",
                  itable_index, s->entry_point(),
                  (int)(s->code_end() - s->entry_point()),
                  (int)(s->code_end() - __ pc()));
  }
  guarantee(__ pc() <= s->code_end(), "overflowed buffer");
  // shut the door on sizing bugs
  int slop = 2*BytesPerInstWord;  // 32-bit offset is this much larger than a 13-bit one
  assert(itable_index > 10 || __ pc() + slop <= s->code_end(), "room for sethi;add");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}


int VtableStub::pd_code_size_limit(bool is_vtable_stub) {
  if (TraceJumps || DebugVtables || CountCompiledCalls || VerifyOops) return 1000;
  else {
    const int slop = 2*BytesPerInstWord; // sethi;add  (needed for long offsets)
    if (is_vtable_stub) {
      // ld;ld;ld,jmp,nop
      const int basic = 5*BytesPerInstWord +
                        // shift;add for load_klass (only shift with zero heap based)
                        (UseCompressedOops ?
                         ((Universe::narrow_oop_base() == NULL) ? BytesPerInstWord : 2*BytesPerInstWord) : 0);
      return basic + slop;
    } else {
      const int basic = (28 LP64_ONLY(+ 6)) * BytesPerInstWord +
                        // shift;add for load_klass (only shift with zero heap based)
                        (UseCompressedOops ?
                         ((Universe::narrow_oop_base() == NULL) ? BytesPerInstWord : 2*BytesPerInstWord) : 0);
      return (basic + slop);
    }
  }

  // In order to tune these parameters, run the JVM with VM options
  // +PrintMiscellaneous and +WizardMode to see information about
  // actual itable stubs.  Look for lines like this:
  //   itable #1 at 0x5551212[116] left over: 8
  // Reduce the constants so that the "left over" number is 8
  // Do not aim at a left-over number of zero, because a very
  // large vtable or itable offset (> 4K) will require an extra
  // sethi/or pair of instructions.
  //
  // The JVM98 app. _202_jess has a megamorphic interface call.
  // The itable code looks like this:
  // Decoding VtableStub itbl[1]@16
  //   ld  [ %o0 + 4 ], %g3
  //   save  %sp, -64, %sp
  //   ld  [ %g3 + 0xe8 ], %l2
  //   sll  %l2, 2, %l2
  //   add  %l2, 0x134, %l2
  //   and  %l2, -8, %l2        ! NOT_LP64 only
  //   add  %g3, %l2, %l2
  //   add  %g3, 4, %g3
  //   ld  [ %l2 ], %l5
  //   brz,pn   %l5, throw_icce
  //   cmp  %l5, %g5
  //   be  %icc, success
  //   add  %l2, 8, %l2
  // loop:
  //   ld  [ %l2 ], %l5
  //   brz,pn   %l5, throw_icce
  //   cmp  %l5, %g5
  //   bne,pn   %icc, loop
  //   add  %l2, 8, %l2
  // success:
  //   ld  [ %l2 + -4 ], %l2
  //   ld  [ %g3 + %l2 ], %l5
  //   restore  %l5, 0, %g5
  //   ld  [ %g5 + 0x44 ], %g3
  //   jmp  %g3
  //   nop
  // throw_icce:
  //   sethi  %hi(throw_ICCE_entry), %g3
  //   ! 5 more instructions here, LP64_ONLY
  //   jmp  %g3 + %lo(throw_ICCE_entry)
  //   restore
}


int VtableStub::pd_code_alignment() {
  // UltraSPARC cache line size is 8 instructions:
  const unsigned int icache_line_size = 32;
  return icache_line_size;
}
