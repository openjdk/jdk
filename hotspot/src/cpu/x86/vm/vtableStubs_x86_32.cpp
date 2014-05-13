/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "code/vtableStubs.hpp"
#include "interp_masm_x86.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klassVtable.hpp"
#include "runtime/sharedRuntime.hpp"
#include "vmreg_x86.inline.hpp"
#ifdef COMPILER2
#include "opto/runtime.hpp"
#endif

// machine-dependent part of VtableStubs: create VtableStub of correct size and
// initialize its code

#define __ masm->

#ifndef PRODUCT
extern "C" void bad_compiled_vtable_index(JavaThread* thread, oop receiver, int index);
#endif

// These stubs are used by the compiler only.
// Argument registers, which must be preserved:
//   rcx - receiver (always first argument)
//   rdx - second argument (if any)
// Other registers that might be usable:
//   rax - inline cache register (is interface for itable stub)
//   rbx - method (used when calling out to interpreter)
// Available now, but may become callee-save at some point:
//   rsi, rdi
// Note that rax and rdx are also used for return values.
//
VtableStub* VtableStubs::create_vtable_stub(int vtable_index) {
  const int i486_code_length = VtableStub::pd_code_size_limit(true);
  VtableStub* s = new(i486_code_length) VtableStub(true, vtable_index);
  // Can be NULL if there is no free space in the code cache.
  if (s == NULL) {
    return NULL;
  }

  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), i486_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

#ifndef PRODUCT

  if (CountCompiledCalls) {
    __ incrementl(ExternalAddress((address) SharedRuntime::nof_megamorphic_calls_addr()));
  }
#endif /* PRODUCT */

  // get receiver (need to skip return address on top of stack)
  assert(VtableStub::receiver_location() == rcx->as_VMReg(), "receiver expected in rcx");

  // get receiver klass
  address npe_addr = __ pc();
  __ movptr(rax, Address(rcx, oopDesc::klass_offset_in_bytes()));

#ifndef PRODUCT
  if (DebugVtables) {
    Label L;
    // check offset vs vtable length
    __ cmpl(Address(rax, InstanceKlass::vtable_length_offset()*wordSize), vtable_index*vtableEntry::size());
    __ jcc(Assembler::greater, L);
    __ movl(rbx, vtable_index);
    __ call_VM(noreg, CAST_FROM_FN_PTR(address, bad_compiled_vtable_index), rcx, rbx);
    __ bind(L);
  }
#endif // PRODUCT

  const Register method = rbx;

  // load Method* and target address
  __ lookup_virtual_method(rax, vtable_index, method);

  if (DebugVtables) {
    Label L;
    __ cmpptr(method, (int32_t)NULL_WORD);
    __ jcc(Assembler::equal, L);
    __ cmpptr(Address(method, Method::from_compiled_offset()), (int32_t)NULL_WORD);
    __ jcc(Assembler::notZero, L);
    __ stop("Vtable entry is NULL");
    __ bind(L);
  }

  // rax,: receiver klass
  // method (rbx): Method*
  // rcx: receiver
  address ame_addr = __ pc();
  __ jmp( Address(method, Method::from_compiled_offset()));

  masm->flush();

  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    tty->print_cr("vtable #%d at "PTR_FORMAT"[%d] left over: %d",
                  vtable_index, p2i(s->entry_point()),
                  (int)(s->code_end() - s->entry_point()),
                  (int)(s->code_end() - __ pc()));
  }
  guarantee(__ pc() <= s->code_end(), "overflowed buffer");
  // shut the door on sizing bugs
  int slop = 3;  // 32-bit offset is this much larger than an 8-bit one
  assert(vtable_index > 10 || __ pc() + slop <= s->code_end(), "room for 32-bit offset");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}


VtableStub* VtableStubs::create_itable_stub(int itable_index) {
  // Note well: pd_code_size_limit is the absolute minimum we can get away with.  If you
  //            add code here, bump the code stub size returned by pd_code_size_limit!
  const int i486_code_length = VtableStub::pd_code_size_limit(false);
  VtableStub* s = new(i486_code_length) VtableStub(false, itable_index);
  // Can be NULL if there is no free space in the code cache.
  if (s == NULL) {
    return NULL;
  }

  ResourceMark rm;
  CodeBuffer cb(s->entry_point(), i486_code_length);
  MacroAssembler* masm = new MacroAssembler(&cb);

  // Entry arguments:
  //  rax,: Interface
  //  rcx: Receiver

#ifndef PRODUCT
  if (CountCompiledCalls) {
    __ incrementl(ExternalAddress((address) SharedRuntime::nof_megamorphic_calls_addr()));
  }
#endif /* PRODUCT */
  // get receiver (need to skip return address on top of stack)

  assert(VtableStub::receiver_location() == rcx->as_VMReg(), "receiver expected in rcx");

  // get receiver klass (also an implicit null-check)
  address npe_addr = __ pc();
  __ movptr(rsi, Address(rcx, oopDesc::klass_offset_in_bytes()));

  // Most registers are in use; we'll use rax, rbx, rsi, rdi
  // (If we need to make rsi, rdi callee-save, do a push/pop here.)
  const Register method = rbx;
  Label throw_icce;

  // Get Method* and entrypoint for compiler
  __ lookup_interface_method(// inputs: rec. class, interface, itable index
                             rsi, rax, itable_index,
                             // outputs: method, scan temp. reg
                             method, rdi,
                             throw_icce);

  // method (rbx): Method*
  // rcx: receiver

#ifdef ASSERT
  if (DebugVtables) {
      Label L1;
      __ cmpptr(method, (int32_t)NULL_WORD);
      __ jcc(Assembler::equal, L1);
      __ cmpptr(Address(method, Method::from_compiled_offset()), (int32_t)NULL_WORD);
      __ jcc(Assembler::notZero, L1);
      __ stop("Method* is null");
      __ bind(L1);
    }
#endif // ASSERT

  address ame_addr = __ pc();
  __ jmp(Address(method, Method::from_compiled_offset()));

  __ bind(throw_icce);
  __ jump(RuntimeAddress(StubRoutines::throw_IncompatibleClassChangeError_entry()));
  masm->flush();

  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    tty->print_cr("itable #%d at "PTR_FORMAT"[%d] left over: %d",
                  itable_index, p2i(s->entry_point()),
                  (int)(s->code_end() - s->entry_point()),
                  (int)(s->code_end() - __ pc()));
  }
  guarantee(__ pc() <= s->code_end(), "overflowed buffer");
  // shut the door on sizing bugs
  int slop = 3;  // 32-bit offset is this much larger than an 8-bit one
  assert(itable_index > 10 || __ pc() + slop <= s->code_end(), "room for 32-bit offset");

  s->set_exception_points(npe_addr, ame_addr);
  return s;
}



int VtableStub::pd_code_size_limit(bool is_vtable_stub) {
  if (is_vtable_stub) {
    // Vtable stub size
    return (DebugVtables ? 210 : 16) + (CountCompiledCalls ? 6 : 0);
  } else {
    // Itable stub size
    return (DebugVtables ? 256 : 66) + (CountCompiledCalls ? 6 : 0);
  }
  // In order to tune these parameters, run the JVM with VM options
  // +PrintMiscellaneous and +WizardMode to see information about
  // actual itable stubs.  Look for lines like this:
  //   itable #1 at 0x5551212[65] left over: 3
  // Reduce the constants so that the "left over" number is >=3
  // for the common cases.
  // Do not aim at a left-over number of zero, because a
  // large vtable or itable index (> 16) will require a 32-bit
  // immediate displacement instead of an 8-bit one.
  //
  // The JVM98 app. _202_jess has a megamorphic interface call.
  // The itable code looks like this:
  // Decoding VtableStub itbl[1]@1
  //   mov    0x4(%ecx),%esi
  //   mov    0xe8(%esi),%edi
  //   lea    0x130(%esi,%edi,4),%edi
  //   add    $0x7,%edi
  //   and    $0xfffffff8,%edi
  //   lea    0x4(%esi),%esi
  //   mov    (%edi),%ebx
  //   cmp    %ebx,%eax
  //   je     success
  // loop:
  //   test   %ebx,%ebx
  //   je     throw_icce
  //   add    $0x8,%edi
  //   mov    (%edi),%ebx
  //   cmp    %ebx,%eax
  //   jne    loop
  // success:
  //   mov    0x4(%edi),%edi
  //   mov    (%esi,%edi,1),%ebx
  //   jmp    *0x44(%ebx)
  // throw_icce:
  //   jmp    throw_ICCE_entry
}

int VtableStub::pd_code_alignment() {
  return wordSize;
}
