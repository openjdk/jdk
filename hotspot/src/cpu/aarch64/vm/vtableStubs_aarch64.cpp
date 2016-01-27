/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "assembler_aarch64.inline.hpp"
#include "code/vtableStubs.hpp"
#include "interp_masm_aarch64.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klassVtable.hpp"
#include "runtime/sharedRuntime.hpp"
#include "vmreg_aarch64.inline.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// machine-dependent part of VtableStubs: create VtableStub of correct size and
// initialize its code

#define __ masm->

#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread,
                                          oop receiver,
                                          int index);
#endif

VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {
  const int aarch64_code_length = VtableStub::pd_code_size_limit(true);
  VtableStub* s = new(aarch64_code_length) VtableStub(true, vtable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), aarch64_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#ifndef PRODUCT
  if (CountCompiledCalls) {
    __ lea(r19, ExternalAddress((address) SharedRuntime::nof_megamorphic_calls_addr()));
    __ incrementw(Address(r19));
  }
#endif

  // get receiver (need to skip return address on top of stack)
  assert(VtableStub::receiver_location() == j_rarg0->as_VMReg(), "receiver expected in j_rarg0");

  // get receiver klass
  address npe_addr = __ pc();
  __ load_klass(r19, j_rarg0);

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    // check offset vs vtable length
    __ ldrw(rscratch1, Address(r19, InstanceKlass::vtable_length_offset()));
    __ cmpw(rscratch1, vtable_index * vtableEntry::size());
    __ br(Assembler::GT, L);
    __ enter();
    __ mov(r2, vtable_index);
    __ call_VM(noreg,
               CAST_FROM_FN_PTR(address, bad_compiled_vtable_index), j_rarg0, r2);
    __ leave();
    __ bind(L);
  }
#endif // PRODUCT

  __ lookup_virtual_method(r19, vtable_index, rmethod);

  if (DebugVtables) {
    Label L;
    __ cbz(rmethod, L);
    __ ldr(rscratch1, Address(rmethod, Method::from_compiled_offset()));
    __ cbnz(rscratch1, L);
    __ stop("Vtable entry is NULL");
    __ bind(L);
  }
  // r0: receiver klass
  // rmethod: Method*
  // r2: receiver
  address ame_addr = __ pc();
  __ ldr(rscratch1, Address(rmethod, Method::from_compiled_offset()));
  __ br(rscratch1);

  __ flush();

  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    tty->print_cr("vtable #%d at " PTR_FORMAT "[%d] left over: %d",
                  vtable_index, p2i(s->entry_point()),
                  (int)(s->code_end() - s->entry_point()),
                  (int)(s->code_end() - __ pc()));
  }
  guarantee(__ pc() <= s->code_end(), "overflowed buffer");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}


VtableStub* VtableStubs::create_itable_stub(int itable_index) {
  // Note well: pd_code_size_limit is the absolute minimum we can get
  // away with.  If you add code here, bump the code stub size
  // returned by pd_code_size_limit!
  const int code_length = VtableStub::pd_code_size_limit(false);
  VtableStub* s = new(code_length) VtableStub(false, itable_index);
  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#ifndef PRODUCT
  if (CountCompiledCalls) {
    __ lea(r10, ExternalAddress((address) SharedRuntime::nof_megamorphic_calls_addr()));
    __ incrementw(Address(r10));
  }
#endif

  // Entry arguments:
  //  rscratch2: Interface
  //  j_rarg0: Receiver

  // Free registers (non-args) are r0 (interface), rmethod

  // get receiver (need to skip return address on top of stack)

  assert(VtableStub::receiver_location() == j_rarg0->as_VMReg(), "receiver expected in j_rarg0");
  // get receiver klass (also an implicit null-check)
  address npe_addr = __ pc();

  // Most registers are in use; we'll use r0, rmethod, r10, r11
  __ load_klass(r10, j_rarg0);

  Label throw_icce;

  // Get Method* and entrypoint for compiler
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             r10, rscratch2, itable_index,
                             // outputs: method, scan temp. reg
                             rmethod, r11,
                             throw_icce);

  // method (rmethod): Method*
  // j_rarg0: receiver

#ifdef ASSERT
  if (DebugVtables) {
    Label L2;
    __ cbz(rmethod, L2);
    __ ldr(rscratch1, Address(rmethod, Method::from_compiled_offset()));
    __ cbnz(rscratch1, L2);
    __ stop("compiler entrypoint is null");
    __ bind(L2);
  }
#endif // ASSERT

  // rmethod: Method*
  // j_rarg0: receiver
  address ame_addr = __ pc();
  __ ldr(rscratch1, Address(rmethod, Method::from_compiled_offset()));
  __ br(rscratch1);

  __ bind(throw_icce);
  __ far_jump(RuntimeAddress(StubRoutines::throw_IncompatibleClassChangeError_entry()));

  __ flush();

  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    tty->print_cr("itable #%d at " PTR_FORMAT "[%d] left over: %d",
                  itable_index, p2i(s->entry_point()),
                  (int)(s->code_end() - s->entry_point()),
                  (int)(s->code_end() - __ pc()));
  }
  guarantee(__ pc() <= s->code_end(), "overflowed buffer");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}


int VtableStub::pd_code_size_limit(bool is_vtable_stub) {
  int size = DebugVtables ? 216 : 0;
  if (CountCompiledCalls)
    size += 6 * 4;
  // FIXME
  if (is_vtable_stub)
    size += 52;
  else
    size += 104;
  return size;

  // In order to tune these parameters, run the JVM with VM options
  // +PrintMiscellaneous and +WizardMode to see information about
  // actual itable stubs.  Run it with -Xmx31G -XX:+UseCompressedOops.
  //
  // If Universe::narrow_klass_base is nonzero, decoding a compressed
  // class can take zeveral instructions.  Run it with -Xmx31G
  // -XX:+UseCompressedOops.
  //
  // The JVM98 app. _202_jess has a megamorphic interface call.
  // The itable code looks like this:
  // Decoding VtableStub itbl[1]@12
  //     ldr     w10, [x1,#8]
  //     lsl     x10, x10, #3
  //     ldr     w11, [x10,#280]
  //     add     x11, x10, x11, uxtx #3
  //     add     x11, x11, #0x1b8
  //     ldr     x12, [x11]
  //     cmp     x9, x12
  //     b.eq    success
  // loop:
  //     cbz     x12, throw_icce
  //     add     x11, x11, #0x10
  //     ldr     x12, [x11]
  //     cmp     x9, x12
  //     b.ne    loop
  // success:
  //     ldr     x11, [x11,#8]
  //     ldr     x12, [x10,x11]
  //     ldr     x8, [x12,#72]
  //     br      x8
  // throw_icce:
  //     b      throw_ICCE_entry

}

int VtableStub::pd_code_alignment() { return 4; }
