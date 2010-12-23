/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "interpreter/interpreter.hpp"
#include "memory/allocation.inline.hpp"
#include "prims/methodHandles.hpp"

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

address MethodHandleEntry::start_compiled_entry(MacroAssembler* _masm,
                                                address interpreted_entry) {
  // Just before the actual machine code entry point, allocate space
  // for a MethodHandleEntry::Data record, so that we can manage everything
  // from one base pointer.
  __ align(wordSize);
  address target = __ pc() + sizeof(Data);
  while (__ pc() < target) {
    __ nop();
    __ align(wordSize);
  }

  MethodHandleEntry* me = (MethodHandleEntry*) __ pc();
  me->set_end_address(__ pc());         // set a temporary end_address
  me->set_from_interpreted_entry(interpreted_entry);
  me->set_type_checking_entry(NULL);

  return (address) me;
}

MethodHandleEntry* MethodHandleEntry::finish_compiled_entry(MacroAssembler* _masm,
                                                address start_addr) {
  MethodHandleEntry* me = (MethodHandleEntry*) start_addr;
  assert(me->end_address() == start_addr, "valid ME");

  // Fill in the real end_address:
  __ align(wordSize);
  me->set_end_address(__ pc());

  return me;
}

#ifdef ASSERT
static void verify_argslot(MacroAssembler* _masm, Register argslot_reg,
                           const char* error_message) {
  // Verify that argslot lies within (rsp, rbp].
  Label L_ok, L_bad;
  BLOCK_COMMENT("{ verify_argslot");
  __ cmpptr(argslot_reg, rbp);
  __ jccb(Assembler::above, L_bad);
  __ cmpptr(rsp, argslot_reg);
  __ jccb(Assembler::below, L_ok);
  __ bind(L_bad);
  __ stop(error_message);
  __ bind(L_ok);
  BLOCK_COMMENT("} verify_argslot");
}
#endif


// Code generation
address MethodHandles::generate_method_handle_interpreter_entry(MacroAssembler* _masm) {
  // rbx: methodOop
  // rcx: receiver method handle (must load from sp[MethodTypeForm.vmslots])
  // rsi/r13: sender SP (must preserve; see prepare_to_jump_from_interpreted)
  // rdx, rdi: garbage temp, blown away

  Register rbx_method = rbx;
  Register rcx_recv   = rcx;
  Register rax_mtype  = rax;
  Register rdx_temp   = rdx;
  Register rdi_temp   = rdi;

  // emit WrongMethodType path first, to enable jccb back-branch from main path
  Label wrong_method_type;
  __ bind(wrong_method_type);
  Label invoke_generic_slow_path;
  assert(methodOopDesc::intrinsic_id_size_in_bytes() == sizeof(u1), "");;
  __ cmpb(Address(rbx_method, methodOopDesc::intrinsic_id_offset_in_bytes()), (int) vmIntrinsics::_invokeExact);
  __ jcc(Assembler::notEqual, invoke_generic_slow_path);
  __ push(rax_mtype);       // required mtype
  __ push(rcx_recv);        // bad mh (1st stacked argument)
  __ jump(ExternalAddress(Interpreter::throw_WrongMethodType_entry()));

  // here's where control starts out:
  __ align(CodeEntryAlignment);
  address entry_point = __ pc();

  // fetch the MethodType from the method handle into rax (the 'check' register)
  {
    Register tem = rbx_method;
    for (jint* pchase = methodOopDesc::method_type_offsets_chain(); (*pchase) != -1; pchase++) {
      __ movptr(rax_mtype, Address(tem, *pchase));
      tem = rax_mtype;          // in case there is another indirection
    }
  }

  // given the MethodType, find out where the MH argument is buried
  __ load_heap_oop(rdx_temp, Address(rax_mtype, __ delayed_value(java_dyn_MethodType::form_offset_in_bytes, rdi_temp)));
  Register rdx_vmslots = rdx_temp;
  __ movl(rdx_vmslots, Address(rdx_temp, __ delayed_value(java_dyn_MethodTypeForm::vmslots_offset_in_bytes, rdi_temp)));
  __ movptr(rcx_recv, __ argument_address(rdx_vmslots));

  trace_method_handle(_masm, "invokeExact");

  __ check_method_handle_type(rax_mtype, rcx_recv, rdi_temp, wrong_method_type);
  __ jump_to_method_handle_entry(rcx_recv, rdi_temp);

  // for invokeGeneric (only), apply argument and result conversions on the fly
  __ bind(invoke_generic_slow_path);
#ifdef ASSERT
  { Label L;
    __ cmpb(Address(rbx_method, methodOopDesc::intrinsic_id_offset_in_bytes()), (int) vmIntrinsics::_invokeGeneric);
    __ jcc(Assembler::equal, L);
    __ stop("bad methodOop::intrinsic_id");
    __ bind(L);
  }
#endif //ASSERT
  Register rbx_temp = rbx_method;  // don't need it now

  // make room on the stack for another pointer:
  Register rcx_argslot = rcx_recv;
  __ lea(rcx_argslot, __ argument_address(rdx_vmslots, 1));
  insert_arg_slots(_masm, 2 * stack_move_unit(), _INSERT_REF_MASK,
                   rcx_argslot, rbx_temp, rdx_temp);

  // load up an adapter from the calling type (Java weaves this)
  __ load_heap_oop(rdx_temp, Address(rax_mtype, __ delayed_value(java_dyn_MethodType::form_offset_in_bytes, rdi_temp)));
  Register rdx_adapter = rdx_temp;
  // __ load_heap_oop(rdx_adapter, Address(rdx_temp, java_dyn_MethodTypeForm::genericInvoker_offset_in_bytes()));
  // deal with old JDK versions:
  __ lea(rdi_temp, Address(rdx_temp, __ delayed_value(java_dyn_MethodTypeForm::genericInvoker_offset_in_bytes, rdi_temp)));
  __ cmpptr(rdi_temp, rdx_temp);
  Label sorry_no_invoke_generic;
  __ jcc(Assembler::below, sorry_no_invoke_generic);

  __ load_heap_oop(rdx_adapter, Address(rdi_temp, 0));
  __ testptr(rdx_adapter, rdx_adapter);
  __ jcc(Assembler::zero, sorry_no_invoke_generic);
  __ movptr(Address(rcx_argslot, 1 * Interpreter::stackElementSize), rdx_adapter);
  // As a trusted first argument, pass the type being called, so the adapter knows
  // the actual types of the arguments and return values.
  // (Generic invokers are shared among form-families of method-type.)
  __ movptr(Address(rcx_argslot, 0 * Interpreter::stackElementSize), rax_mtype);
  // FIXME: assert that rdx_adapter is of the right method-type.
  __ mov(rcx, rdx_adapter);
  trace_method_handle(_masm, "invokeGeneric");
  __ jump_to_method_handle_entry(rcx, rdi_temp);

  __ bind(sorry_no_invoke_generic); // no invokeGeneric implementation available!
  __ movptr(rcx_recv, Address(rcx_argslot, -1 * Interpreter::stackElementSize));  // recover original MH
  __ push(rax_mtype);       // required mtype
  __ push(rcx_recv);        // bad mh (1st stacked argument)
  __ jump(ExternalAddress(Interpreter::throw_WrongMethodType_entry()));

  return entry_point;
}

// Helper to insert argument slots into the stack.
// arg_slots must be a multiple of stack_move_unit() and <= 0
void MethodHandles::insert_arg_slots(MacroAssembler* _masm,
                                     RegisterOrConstant arg_slots,
                                     int arg_mask,
                                     Register rax_argslot,
                                     Register rbx_temp, Register rdx_temp, Register temp3_reg) {
  assert(temp3_reg == noreg, "temp3 not required");
  assert_different_registers(rax_argslot, rbx_temp, rdx_temp,
                             (!arg_slots.is_register() ? rsp : arg_slots.as_register()));

#ifdef ASSERT
  verify_argslot(_masm, rax_argslot, "insertion point must fall within current frame");
  if (arg_slots.is_register()) {
    Label L_ok, L_bad;
    __ cmpptr(arg_slots.as_register(), (int32_t) NULL_WORD);
    __ jccb(Assembler::greater, L_bad);
    __ testl(arg_slots.as_register(), -stack_move_unit() - 1);
    __ jccb(Assembler::zero, L_ok);
    __ bind(L_bad);
    __ stop("assert arg_slots <= 0 and clear low bits");
    __ bind(L_ok);
  } else {
    assert(arg_slots.as_constant() <= 0, "");
    assert(arg_slots.as_constant() % -stack_move_unit() == 0, "");
  }
#endif //ASSERT

#ifdef _LP64
  if (arg_slots.is_register()) {
    // clean high bits of stack motion register (was loaded as an int)
    __ movslq(arg_slots.as_register(), arg_slots.as_register());
  }
#endif

  // Make space on the stack for the inserted argument(s).
  // Then pull down everything shallower than rax_argslot.
  // The stacked return address gets pulled down with everything else.
  // That is, copy [rsp, argslot) downward by -size words.  In pseudo-code:
  //   rsp -= size;
  //   for (rdx = rsp + size; rdx < argslot; rdx++)
  //     rdx[-size] = rdx[0]
  //   argslot -= size;
  BLOCK_COMMENT("insert_arg_slots {");
  __ mov(rdx_temp, rsp);                        // source pointer for copy
  __ lea(rsp, Address(rsp, arg_slots, Address::times_ptr));
  {
    Label loop;
    __ BIND(loop);
    // pull one word down each time through the loop
    __ movptr(rbx_temp, Address(rdx_temp, 0));
    __ movptr(Address(rdx_temp, arg_slots, Address::times_ptr), rbx_temp);
    __ addptr(rdx_temp, wordSize);
    __ cmpptr(rdx_temp, rax_argslot);
    __ jccb(Assembler::less, loop);
  }

  // Now move the argslot down, to point to the opened-up space.
  __ lea(rax_argslot, Address(rax_argslot, arg_slots, Address::times_ptr));
  BLOCK_COMMENT("} insert_arg_slots");
}

// Helper to remove argument slots from the stack.
// arg_slots must be a multiple of stack_move_unit() and >= 0
void MethodHandles::remove_arg_slots(MacroAssembler* _masm,
                                    RegisterOrConstant arg_slots,
                                    Register rax_argslot,
                                     Register rbx_temp, Register rdx_temp, Register temp3_reg) {
  assert(temp3_reg == noreg, "temp3 not required");
  assert_different_registers(rax_argslot, rbx_temp, rdx_temp,
                             (!arg_slots.is_register() ? rsp : arg_slots.as_register()));

#ifdef ASSERT
  // Verify that [argslot..argslot+size) lies within (rsp, rbp).
  __ lea(rbx_temp, Address(rax_argslot, arg_slots, Address::times_ptr));
  verify_argslot(_masm, rbx_temp, "deleted argument(s) must fall within current frame");
  if (arg_slots.is_register()) {
    Label L_ok, L_bad;
    __ cmpptr(arg_slots.as_register(), (int32_t) NULL_WORD);
    __ jccb(Assembler::less, L_bad);
    __ testl(arg_slots.as_register(), -stack_move_unit() - 1);
    __ jccb(Assembler::zero, L_ok);
    __ bind(L_bad);
    __ stop("assert arg_slots >= 0 and clear low bits");
    __ bind(L_ok);
  } else {
    assert(arg_slots.as_constant() >= 0, "");
    assert(arg_slots.as_constant() % -stack_move_unit() == 0, "");
  }
#endif //ASSERT

#ifdef _LP64
  if (false) {                  // not needed, since register is positive
    // clean high bits of stack motion register (was loaded as an int)
    if (arg_slots.is_register())
      __ movslq(arg_slots.as_register(), arg_slots.as_register());
  }
#endif

  BLOCK_COMMENT("remove_arg_slots {");
  // Pull up everything shallower than rax_argslot.
  // Then remove the excess space on the stack.
  // The stacked return address gets pulled up with everything else.
  // That is, copy [rsp, argslot) upward by size words.  In pseudo-code:
  //   for (rdx = argslot-1; rdx >= rsp; --rdx)
  //     rdx[size] = rdx[0]
  //   argslot += size;
  //   rsp += size;
  __ lea(rdx_temp, Address(rax_argslot, -wordSize)); // source pointer for copy
  {
    Label loop;
    __ BIND(loop);
    // pull one word up each time through the loop
    __ movptr(rbx_temp, Address(rdx_temp, 0));
    __ movptr(Address(rdx_temp, arg_slots, Address::times_ptr), rbx_temp);
    __ addptr(rdx_temp, -wordSize);
    __ cmpptr(rdx_temp, rsp);
    __ jccb(Assembler::greaterEqual, loop);
  }

  // Now move the argslot up, to point to the just-copied block.
  __ lea(rsp, Address(rsp, arg_slots, Address::times_ptr));
  // And adjust the argslot address to point at the deletion point.
  __ lea(rax_argslot, Address(rax_argslot, arg_slots, Address::times_ptr));
  BLOCK_COMMENT("} remove_arg_slots");
}

#ifndef PRODUCT
extern "C" void print_method_handle(oop mh);
void trace_method_handle_stub(const char* adaptername,
                              oop mh,
                              intptr_t* saved_regs,
                              intptr_t* entry_sp,
                              intptr_t* saved_sp,
                              intptr_t* saved_bp) {
  // called as a leaf from native code: do not block the JVM!
  intptr_t* last_sp = (intptr_t*) saved_bp[frame::interpreter_frame_last_sp_offset];
  intptr_t* base_sp = (intptr_t*) saved_bp[frame::interpreter_frame_monitor_block_top_offset];
  printf("MH %s mh="INTPTR_FORMAT" sp=("INTPTR_FORMAT"+"INTX_FORMAT") stack_size="INTX_FORMAT" bp="INTPTR_FORMAT"\n",
         adaptername, (intptr_t)mh, (intptr_t)entry_sp, (intptr_t)(saved_sp - entry_sp), (intptr_t)(base_sp - last_sp), (intptr_t)saved_bp);
  if (last_sp != saved_sp && last_sp != NULL)
    printf("*** last_sp="INTPTR_FORMAT"\n", (intptr_t)last_sp);
  if (Verbose) {
    printf(" reg dump: ");
    int saved_regs_count = (entry_sp-1) - saved_regs;
    // 32 bit: rdi rsi rbp rsp; rbx rdx rcx (*) rax
    int i;
    for (i = 0; i <= saved_regs_count; i++) {
      if (i > 0 && i % 4 == 0 && i != saved_regs_count)
        printf("\n   + dump: ");
      printf(" %d: "INTPTR_FORMAT, i, saved_regs[i]);
    }
    printf("\n");
    int stack_dump_count = 16;
    if (stack_dump_count < (int)(saved_bp + 2 - saved_sp))
      stack_dump_count = (int)(saved_bp + 2 - saved_sp);
    if (stack_dump_count > 64)  stack_dump_count = 48;
    for (i = 0; i < stack_dump_count; i += 4) {
      printf(" dump at SP[%d] "INTPTR_FORMAT": "INTPTR_FORMAT" "INTPTR_FORMAT" "INTPTR_FORMAT" "INTPTR_FORMAT"\n",
             i, (intptr_t) &entry_sp[i+0], entry_sp[i+0], entry_sp[i+1], entry_sp[i+2], entry_sp[i+3]);
    }
    print_method_handle(mh);
  }
}
void MethodHandles::trace_method_handle(MacroAssembler* _masm, const char* adaptername) {
  if (!TraceMethodHandles)  return;
  BLOCK_COMMENT("trace_method_handle {");
  __ push(rax);
  __ lea(rax, Address(rsp, wordSize*6)); // entry_sp
  __ pusha();
  // arguments:
  __ push(rbp);               // interpreter frame pointer
  __ push(rsi);               // saved_sp
  __ push(rax);               // entry_sp
  __ push(rcx);               // mh
  __ push(rcx);
  __ movptr(Address(rsp, 0), (intptr_t) adaptername);
  __ call_VM_leaf(CAST_FROM_FN_PTR(address, trace_method_handle_stub), 5);
  __ popa();
  __ pop(rax);
  BLOCK_COMMENT("} trace_method_handle");
}
#endif //PRODUCT

// which conversion op types are implemented here?
int MethodHandles::adapter_conversion_ops_supported_mask() {
  return ((1<<sun_dyn_AdapterMethodHandle::OP_RETYPE_ONLY)
         |(1<<sun_dyn_AdapterMethodHandle::OP_RETYPE_RAW)
         |(1<<sun_dyn_AdapterMethodHandle::OP_CHECK_CAST)
         |(1<<sun_dyn_AdapterMethodHandle::OP_PRIM_TO_PRIM)
         |(1<<sun_dyn_AdapterMethodHandle::OP_REF_TO_PRIM)
         |(1<<sun_dyn_AdapterMethodHandle::OP_SWAP_ARGS)
         |(1<<sun_dyn_AdapterMethodHandle::OP_ROT_ARGS)
         |(1<<sun_dyn_AdapterMethodHandle::OP_DUP_ARGS)
         |(1<<sun_dyn_AdapterMethodHandle::OP_DROP_ARGS)
         //|(1<<sun_dyn_AdapterMethodHandle::OP_SPREAD_ARGS) //BUG!
         );
  // FIXME: MethodHandlesTest gets a crash if we enable OP_SPREAD_ARGS.
}

// Generate an "entry" field for a method handle.
// This determines how the method handle will respond to calls.
void MethodHandles::generate_method_handle_stub(MacroAssembler* _masm, MethodHandles::EntryKind ek) {
  // Here is the register state during an interpreted call,
  // as set up by generate_method_handle_interpreter_entry():
  // - rbx: garbage temp (was MethodHandle.invoke methodOop, unused)
  // - rcx: receiver method handle
  // - rax: method handle type (only used by the check_mtype entry point)
  // - rsi/r13: sender SP (must preserve; see prepare_to_jump_from_interpreted)
  // - rdx: garbage temp, can blow away

  Register rcx_recv    = rcx;
  Register rax_argslot = rax;
  Register rbx_temp    = rbx;
  Register rdx_temp    = rdx;

  // This guy is set up by prepare_to_jump_from_interpreted (from interpreted calls)
  // and gen_c2i_adapter (from compiled calls):
  Register saved_last_sp = LP64_ONLY(r13) NOT_LP64(rsi);

  guarantee(java_dyn_MethodHandle::vmentry_offset_in_bytes() != 0, "must have offsets");

  // some handy addresses
  Address rbx_method_fie(     rbx,      methodOopDesc::from_interpreted_offset() );

  Address rcx_mh_vmtarget(    rcx_recv, java_dyn_MethodHandle::vmtarget_offset_in_bytes() );
  Address rcx_dmh_vmindex(    rcx_recv, sun_dyn_DirectMethodHandle::vmindex_offset_in_bytes() );

  Address rcx_bmh_vmargslot(  rcx_recv, sun_dyn_BoundMethodHandle::vmargslot_offset_in_bytes() );
  Address rcx_bmh_argument(   rcx_recv, sun_dyn_BoundMethodHandle::argument_offset_in_bytes() );

  Address rcx_amh_vmargslot(  rcx_recv, sun_dyn_AdapterMethodHandle::vmargslot_offset_in_bytes() );
  Address rcx_amh_argument(   rcx_recv, sun_dyn_AdapterMethodHandle::argument_offset_in_bytes() );
  Address rcx_amh_conversion( rcx_recv, sun_dyn_AdapterMethodHandle::conversion_offset_in_bytes() );
  Address vmarg;                // __ argument_address(vmargslot)

  const int java_mirror_offset = klassOopDesc::klass_part_offset_in_bytes() + Klass::java_mirror_offset_in_bytes();

  if (have_entry(ek)) {
    __ nop();                   // empty stubs make SG sick
    return;
  }

  address interp_entry = __ pc();

  trace_method_handle(_masm, entry_name(ek));

  BLOCK_COMMENT(entry_name(ek));

  switch ((int) ek) {
  case _raise_exception:
    {
      // Not a real MH entry, but rather shared code for raising an exception.
      // Extra local arguments are pushed on stack, as required type at TOS+8,
      // failing object (or NULL) at TOS+4, failing bytecode type at TOS.
      // Beyond those local arguments are the PC, of course.
      Register rdx_code = rdx_temp;
      Register rcx_fail = rcx_recv;
      Register rax_want = rax_argslot;
      Register rdi_pc   = rdi;
      __ pop(rdx_code);  // TOS+0
      __ pop(rcx_fail);  // TOS+4
      __ pop(rax_want);  // TOS+8
      __ pop(rdi_pc);    // caller PC

      __ mov(rsp, rsi);   // cut the stack back to where the caller started

      // Repush the arguments as if coming from the interpreter.
      __ push(rdx_code);
      __ push(rcx_fail);
      __ push(rax_want);

      Register rbx_method = rbx_temp;
      Label no_method;
      // FIXME: fill in _raise_exception_method with a suitable sun.dyn method
      __ movptr(rbx_method, ExternalAddress((address) &_raise_exception_method));
      __ testptr(rbx_method, rbx_method);
      __ jccb(Assembler::zero, no_method);
      int jobject_oop_offset = 0;
      __ movptr(rbx_method, Address(rbx_method, jobject_oop_offset));  // dereference the jobject
      __ testptr(rbx_method, rbx_method);
      __ jccb(Assembler::zero, no_method);
      __ verify_oop(rbx_method);
      __ push(rdi_pc);          // and restore caller PC
      __ jmp(rbx_method_fie);

      // If we get here, the Java runtime did not do its job of creating the exception.
      // Do something that is at least causes a valid throw from the interpreter.
      __ bind(no_method);
      __ pop(rax_want);
      __ pop(rcx_fail);
      __ push(rax_want);
      __ push(rcx_fail);
      __ jump(ExternalAddress(Interpreter::throw_WrongMethodType_entry()));
    }
    break;

  case _invokestatic_mh:
  case _invokespecial_mh:
    {
      Register rbx_method = rbx_temp;
      __ load_heap_oop(rbx_method, rcx_mh_vmtarget); // target is a methodOop
      __ verify_oop(rbx_method);
      // same as TemplateTable::invokestatic or invokespecial,
      // minus the CP setup and profiling:
      if (ek == _invokespecial_mh) {
        // Must load & check the first argument before entering the target method.
        __ load_method_handle_vmslots(rax_argslot, rcx_recv, rdx_temp);
        __ movptr(rcx_recv, __ argument_address(rax_argslot, -1));
        __ null_check(rcx_recv);
        __ verify_oop(rcx_recv);
      }
      __ jmp(rbx_method_fie);
    }
    break;

  case _invokevirtual_mh:
    {
      // same as TemplateTable::invokevirtual,
      // minus the CP setup and profiling:

      // pick out the vtable index and receiver offset from the MH,
      // and then we can discard it:
      __ load_method_handle_vmslots(rax_argslot, rcx_recv, rdx_temp);
      Register rbx_index = rbx_temp;
      __ movl(rbx_index, rcx_dmh_vmindex);
      // Note:  The verifier allows us to ignore rcx_mh_vmtarget.
      __ movptr(rcx_recv, __ argument_address(rax_argslot, -1));
      __ null_check(rcx_recv, oopDesc::klass_offset_in_bytes());

      // get receiver klass
      Register rax_klass = rax_argslot;
      __ load_klass(rax_klass, rcx_recv);
      __ verify_oop(rax_klass);

      // get target methodOop & entry point
      const int base = instanceKlass::vtable_start_offset() * wordSize;
      assert(vtableEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");
      Address vtable_entry_addr(rax_klass,
                                rbx_index, Address::times_ptr,
                                base + vtableEntry::method_offset_in_bytes());
      Register rbx_method = rbx_temp;
      __ movptr(rbx_method, vtable_entry_addr);

      __ verify_oop(rbx_method);
      __ jmp(rbx_method_fie);
    }
    break;

  case _invokeinterface_mh:
    {
      // same as TemplateTable::invokeinterface,
      // minus the CP setup and profiling:

      // pick out the interface and itable index from the MH.
      __ load_method_handle_vmslots(rax_argslot, rcx_recv, rdx_temp);
      Register rdx_intf  = rdx_temp;
      Register rbx_index = rbx_temp;
      __ load_heap_oop(rdx_intf, rcx_mh_vmtarget);
      __ movl(rbx_index, rcx_dmh_vmindex);
      __ movptr(rcx_recv, __ argument_address(rax_argslot, -1));
      __ null_check(rcx_recv, oopDesc::klass_offset_in_bytes());

      // get receiver klass
      Register rax_klass = rax_argslot;
      __ load_klass(rax_klass, rcx_recv);
      __ verify_oop(rax_klass);

      Register rdi_temp   = rdi;
      Register rbx_method = rbx_index;

      // get interface klass
      Label no_such_interface;
      __ verify_oop(rdx_intf);
      __ lookup_interface_method(rax_klass, rdx_intf,
                                 // note: next two args must be the same:
                                 rbx_index, rbx_method,
                                 rdi_temp,
                                 no_such_interface);

      __ verify_oop(rbx_method);
      __ jmp(rbx_method_fie);
      __ hlt();

      __ bind(no_such_interface);
      // Throw an exception.
      // For historical reasons, it will be IncompatibleClassChangeError.
      __ pushptr(Address(rdx_intf, java_mirror_offset));  // required interface
      __ push(rcx_recv);        // bad receiver
      __ push((int)Bytecodes::_invokeinterface);  // who is complaining?
      __ jump(ExternalAddress(from_interpreted_entry(_raise_exception)));
    }
    break;

  case _bound_ref_mh:
  case _bound_int_mh:
  case _bound_long_mh:
  case _bound_ref_direct_mh:
  case _bound_int_direct_mh:
  case _bound_long_direct_mh:
    {
      bool direct_to_method = (ek >= _bound_ref_direct_mh);
      BasicType arg_type  = T_ILLEGAL;
      int       arg_mask  = _INSERT_NO_MASK;
      int       arg_slots = -1;
      get_ek_bound_mh_info(ek, arg_type, arg_mask, arg_slots);

      // make room for the new argument:
      __ movl(rax_argslot, rcx_bmh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot));
      insert_arg_slots(_masm, arg_slots * stack_move_unit(), arg_mask,
                       rax_argslot, rbx_temp, rdx_temp);

      // store bound argument into the new stack slot:
      __ load_heap_oop(rbx_temp, rcx_bmh_argument);
      Address prim_value_addr(rbx_temp, java_lang_boxing_object::value_offset_in_bytes(arg_type));
      if (arg_type == T_OBJECT) {
        __ movptr(Address(rax_argslot, 0), rbx_temp);
      } else {
        __ load_sized_value(rdx_temp, prim_value_addr,
                            type2aelembytes(arg_type), is_signed_subword_type(arg_type));
        __ movptr(Address(rax_argslot, 0), rdx_temp);
#ifndef _LP64
        if (arg_slots == 2) {
          __ movl(rdx_temp, prim_value_addr.plus_disp(wordSize));
          __ movl(Address(rax_argslot, Interpreter::stackElementSize), rdx_temp);
        }
#endif //_LP64
      }

      if (direct_to_method) {
        Register rbx_method = rbx_temp;
        __ load_heap_oop(rbx_method, rcx_mh_vmtarget);
        __ verify_oop(rbx_method);
        __ jmp(rbx_method_fie);
      } else {
        __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
        __ verify_oop(rcx_recv);
        __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
      }
    }
    break;

  case _adapter_retype_only:
  case _adapter_retype_raw:
    // immediately jump to the next MH layer:
    __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
    __ verify_oop(rcx_recv);
    __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    // This is OK when all parameter types widen.
    // It is also OK when a return type narrows.
    break;

  case _adapter_check_cast:
    {
      // temps:
      Register rbx_klass = rbx_temp; // interesting AMH data

      // check a reference argument before jumping to the next layer of MH:
      __ movl(rax_argslot, rcx_amh_vmargslot);
      vmarg = __ argument_address(rax_argslot);

      // What class are we casting to?
      __ load_heap_oop(rbx_klass, rcx_amh_argument); // this is a Class object!
      __ load_heap_oop(rbx_klass, Address(rbx_klass, java_lang_Class::klass_offset_in_bytes()));

      Label done;
      __ movptr(rdx_temp, vmarg);
      __ testptr(rdx_temp, rdx_temp);
      __ jcc(Assembler::zero, done);         // no cast if null
      __ load_klass(rdx_temp, rdx_temp);

      // live at this point:
      // - rbx_klass:  klass required by the target method
      // - rdx_temp:   argument klass to test
      // - rcx_recv:   adapter method handle
      __ check_klass_subtype(rdx_temp, rbx_klass, rax_argslot, done);

      // If we get here, the type check failed!
      // Call the wrong_method_type stub, passing the failing argument type in rax.
      Register rax_mtype = rax_argslot;
      __ movl(rax_argslot, rcx_amh_vmargslot);  // reload argslot field
      __ movptr(rdx_temp, vmarg);

      __ load_heap_oop(rbx_klass, rcx_amh_argument); // required class
      __ push(rbx_klass);
      __ push(rdx_temp);                             // bad object
      __ push((int)Bytecodes::_checkcast);           // who is complaining?
      __ jump(ExternalAddress(from_interpreted_entry(_raise_exception)));

      __ bind(done);
      // get the new MH:
      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_prim_to_prim:
  case _adapter_ref_to_prim:
    // handled completely by optimized cases
    __ stop("init_AdapterMethodHandle should not issue this");
    break;

  case _adapter_opt_i2i:        // optimized subcase of adapt_prim_to_prim
//case _adapter_opt_f2i:        // optimized subcase of adapt_prim_to_prim
  case _adapter_opt_l2i:        // optimized subcase of adapt_prim_to_prim
  case _adapter_opt_unboxi:     // optimized subcase of adapt_ref_to_prim
    {
      // perform an in-place conversion to int or an int subword
      __ movl(rax_argslot, rcx_amh_vmargslot);
      vmarg = __ argument_address(rax_argslot);

      switch (ek) {
      case _adapter_opt_i2i:
        __ movl(rdx_temp, vmarg);
        break;
      case _adapter_opt_l2i:
        {
          // just delete the extra slot; on a little-endian machine we keep the first
          __ lea(rax_argslot, __ argument_address(rax_argslot, 1));
          remove_arg_slots(_masm, -stack_move_unit(),
                           rax_argslot, rbx_temp, rdx_temp);
          vmarg = Address(rax_argslot, -Interpreter::stackElementSize);
          __ movl(rdx_temp, vmarg);
        }
        break;
      case _adapter_opt_unboxi:
        {
          // Load the value up from the heap.
          __ movptr(rdx_temp, vmarg);
          int value_offset = java_lang_boxing_object::value_offset_in_bytes(T_INT);
#ifdef ASSERT
          for (int bt = T_BOOLEAN; bt < T_INT; bt++) {
            if (is_subword_type(BasicType(bt)))
              assert(value_offset == java_lang_boxing_object::value_offset_in_bytes(BasicType(bt)), "");
          }
#endif
          __ null_check(rdx_temp, value_offset);
          __ movl(rdx_temp, Address(rdx_temp, value_offset));
          // We load this as a word.  Because we are little-endian,
          // the low bits will be correct, but the high bits may need cleaning.
          // The vminfo will guide us to clean those bits.
        }
        break;
      default:
        ShouldNotReachHere();
      }

      // Do the requested conversion and store the value.
      Register rbx_vminfo = rbx_temp;
      __ movl(rbx_vminfo, rcx_amh_conversion);
      assert(CONV_VMINFO_SHIFT == 0, "preshifted");

      // get the new MH:
      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      // (now we are done with the old MH)

      // original 32-bit vmdata word must be of this form:
      //    | MBZ:6 | signBitCount:8 | srcDstTypes:8 | conversionOp:8 |
      __ xchgptr(rcx, rbx_vminfo);                // free rcx for shifts
      __ shll(rdx_temp /*, rcx*/);
      Label zero_extend, done;
      __ testl(rcx, CONV_VMINFO_SIGN_FLAG);
      __ jccb(Assembler::zero, zero_extend);

      // this path is taken for int->byte, int->short
      __ sarl(rdx_temp /*, rcx*/);
      __ jmpb(done);

      __ bind(zero_extend);
      // this is taken for int->char
      __ shrl(rdx_temp /*, rcx*/);

      __ bind(done);
      __ movl(vmarg, rdx_temp);  // Store the value.
      __ xchgptr(rcx, rbx_vminfo);                // restore rcx_recv

      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_opt_i2l:        // optimized subcase of adapt_prim_to_prim
  case _adapter_opt_unboxl:     // optimized subcase of adapt_ref_to_prim
    {
      // perform an in-place int-to-long or ref-to-long conversion
      __ movl(rax_argslot, rcx_amh_vmargslot);

      // on a little-endian machine we keep the first slot and add another after
      __ lea(rax_argslot, __ argument_address(rax_argslot, 1));
      insert_arg_slots(_masm, stack_move_unit(), _INSERT_INT_MASK,
                       rax_argslot, rbx_temp, rdx_temp);
      Address vmarg1(rax_argslot, -Interpreter::stackElementSize);
      Address vmarg2 = vmarg1.plus_disp(Interpreter::stackElementSize);

      switch (ek) {
      case _adapter_opt_i2l:
        {
#ifdef _LP64
          __ movslq(rdx_temp, vmarg1);  // Load sign-extended
          __ movq(vmarg1, rdx_temp);    // Store into first slot
#else
          __ movl(rdx_temp, vmarg1);
          __ sarl(rdx_temp, BitsPerInt - 1);  // __ extend_sign()
          __ movl(vmarg2, rdx_temp); // store second word
#endif
        }
        break;
      case _adapter_opt_unboxl:
        {
          // Load the value up from the heap.
          __ movptr(rdx_temp, vmarg1);
          int value_offset = java_lang_boxing_object::value_offset_in_bytes(T_LONG);
          assert(value_offset == java_lang_boxing_object::value_offset_in_bytes(T_DOUBLE), "");
          __ null_check(rdx_temp, value_offset);
#ifdef _LP64
          __ movq(rbx_temp, Address(rdx_temp, value_offset));
          __ movq(vmarg1, rbx_temp);
#else
          __ movl(rbx_temp, Address(rdx_temp, value_offset + 0*BytesPerInt));
          __ movl(rdx_temp, Address(rdx_temp, value_offset + 1*BytesPerInt));
          __ movl(vmarg1, rbx_temp);
          __ movl(vmarg2, rdx_temp);
#endif
        }
        break;
      default:
        ShouldNotReachHere();
      }

      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_opt_f2d:        // optimized subcase of adapt_prim_to_prim
  case _adapter_opt_d2f:        // optimized subcase of adapt_prim_to_prim
    {
      // perform an in-place floating primitive conversion
      __ movl(rax_argslot, rcx_amh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot, 1));
      if (ek == _adapter_opt_f2d) {
        insert_arg_slots(_masm, stack_move_unit(), _INSERT_INT_MASK,
                         rax_argslot, rbx_temp, rdx_temp);
      }
      Address vmarg(rax_argslot, -Interpreter::stackElementSize);

#ifdef _LP64
      if (ek == _adapter_opt_f2d) {
        __ movflt(xmm0, vmarg);
        __ cvtss2sd(xmm0, xmm0);
        __ movdbl(vmarg, xmm0);
      } else {
        __ movdbl(xmm0, vmarg);
        __ cvtsd2ss(xmm0, xmm0);
        __ movflt(vmarg, xmm0);
      }
#else //_LP64
      if (ek == _adapter_opt_f2d) {
        __ fld_s(vmarg);        // load float to ST0
        __ fstp_s(vmarg);       // store single
      } else {
        __ fld_d(vmarg);        // load double to ST0
        __ fstp_s(vmarg);       // store single
      }
#endif //_LP64

      if (ek == _adapter_opt_d2f) {
        remove_arg_slots(_masm, -stack_move_unit(),
                         rax_argslot, rbx_temp, rdx_temp);
      }

      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_prim_to_ref:
    __ unimplemented(entry_name(ek)); // %%% FIXME: NYI
    break;

  case _adapter_swap_args:
  case _adapter_rot_args:
    // handled completely by optimized cases
    __ stop("init_AdapterMethodHandle should not issue this");
    break;

  case _adapter_opt_swap_1:
  case _adapter_opt_swap_2:
  case _adapter_opt_rot_1_up:
  case _adapter_opt_rot_1_down:
  case _adapter_opt_rot_2_up:
  case _adapter_opt_rot_2_down:
    {
      int swap_bytes = 0, rotate = 0;
      get_ek_adapter_opt_swap_rot_info(ek, swap_bytes, rotate);

      // 'argslot' is the position of the first argument to swap
      __ movl(rax_argslot, rcx_amh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot));

      // 'vminfo' is the second
      Register rbx_destslot = rbx_temp;
      __ movl(rbx_destslot, rcx_amh_conversion);
      assert(CONV_VMINFO_SHIFT == 0, "preshifted");
      __ andl(rbx_destslot, CONV_VMINFO_MASK);
      __ lea(rbx_destslot, __ argument_address(rbx_destslot));
      DEBUG_ONLY(verify_argslot(_masm, rbx_destslot, "swap point must fall within current frame"));

      if (!rotate) {
        for (int i = 0; i < swap_bytes; i += wordSize) {
          __ movptr(rdx_temp, Address(rax_argslot , i));
          __ push(rdx_temp);
          __ movptr(rdx_temp, Address(rbx_destslot, i));
          __ movptr(Address(rax_argslot, i), rdx_temp);
          __ pop(rdx_temp);
          __ movptr(Address(rbx_destslot, i), rdx_temp);
        }
      } else {
        // push the first chunk, which is going to get overwritten
        for (int i = swap_bytes; (i -= wordSize) >= 0; ) {
          __ movptr(rdx_temp, Address(rax_argslot, i));
          __ push(rdx_temp);
        }

        if (rotate > 0) {
          // rotate upward
          __ subptr(rax_argslot, swap_bytes);
#ifdef ASSERT
          {
            // Verify that argslot > destslot, by at least swap_bytes.
            Label L_ok;
            __ cmpptr(rax_argslot, rbx_destslot);
            __ jccb(Assembler::aboveEqual, L_ok);
            __ stop("source must be above destination (upward rotation)");
            __ bind(L_ok);
          }
#endif
          // work argslot down to destslot, copying contiguous data upwards
          // pseudo-code:
          //   rax = src_addr - swap_bytes
          //   rbx = dest_addr
          //   while (rax >= rbx) *(rax + swap_bytes) = *(rax + 0), rax--;
          Label loop;
          __ bind(loop);
          __ movptr(rdx_temp, Address(rax_argslot, 0));
          __ movptr(Address(rax_argslot, swap_bytes), rdx_temp);
          __ addptr(rax_argslot, -wordSize);
          __ cmpptr(rax_argslot, rbx_destslot);
          __ jccb(Assembler::aboveEqual, loop);
        } else {
          __ addptr(rax_argslot, swap_bytes);
#ifdef ASSERT
          {
            // Verify that argslot < destslot, by at least swap_bytes.
            Label L_ok;
            __ cmpptr(rax_argslot, rbx_destslot);
            __ jccb(Assembler::belowEqual, L_ok);
            __ stop("source must be below destination (downward rotation)");
            __ bind(L_ok);
          }
#endif
          // work argslot up to destslot, copying contiguous data downwards
          // pseudo-code:
          //   rax = src_addr + swap_bytes
          //   rbx = dest_addr
          //   while (rax <= rbx) *(rax - swap_bytes) = *(rax + 0), rax++;
          Label loop;
          __ bind(loop);
          __ movptr(rdx_temp, Address(rax_argslot, 0));
          __ movptr(Address(rax_argslot, -swap_bytes), rdx_temp);
          __ addptr(rax_argslot, wordSize);
          __ cmpptr(rax_argslot, rbx_destslot);
          __ jccb(Assembler::belowEqual, loop);
        }

        // pop the original first chunk into the destination slot, now free
        for (int i = 0; i < swap_bytes; i += wordSize) {
          __ pop(rdx_temp);
          __ movptr(Address(rbx_destslot, i), rdx_temp);
        }
      }

      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_dup_args:
    {
      // 'argslot' is the position of the first argument to duplicate
      __ movl(rax_argslot, rcx_amh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot));

      // 'stack_move' is negative number of words to duplicate
      Register rdx_stack_move = rdx_temp;
      __ movl2ptr(rdx_stack_move, rcx_amh_conversion);
      __ sarptr(rdx_stack_move, CONV_STACK_MOVE_SHIFT);

      int argslot0_num = 0;
      Address argslot0 = __ argument_address(RegisterOrConstant(argslot0_num));
      assert(argslot0.base() == rsp, "");
      int pre_arg_size = argslot0.disp();
      assert(pre_arg_size % wordSize == 0, "");
      assert(pre_arg_size > 0, "must include PC");

      // remember the old rsp+1 (argslot[0])
      Register rbx_oldarg = rbx_temp;
      __ lea(rbx_oldarg, argslot0);

      // move rsp down to make room for dups
      __ lea(rsp, Address(rsp, rdx_stack_move, Address::times_ptr));

      // compute the new rsp+1 (argslot[0])
      Register rdx_newarg = rdx_temp;
      __ lea(rdx_newarg, argslot0);

      __ push(rdi);             // need a temp
      // (preceding push must be done after arg addresses are taken!)

      // pull down the pre_arg_size data (PC)
      for (int i = -pre_arg_size; i < 0; i += wordSize) {
        __ movptr(rdi, Address(rbx_oldarg, i));
        __ movptr(Address(rdx_newarg, i), rdi);
      }

      // copy from rax_argslot[0...] down to new_rsp[1...]
      // pseudo-code:
      //   rbx = old_rsp+1
      //   rdx = new_rsp+1
      //   rax = argslot
      //   while (rdx < rbx) *rdx++ = *rax++
      Label loop;
      __ bind(loop);
      __ movptr(rdi, Address(rax_argslot, 0));
      __ movptr(Address(rdx_newarg, 0), rdi);
      __ addptr(rax_argslot, wordSize);
      __ addptr(rdx_newarg, wordSize);
      __ cmpptr(rdx_newarg, rbx_oldarg);
      __ jccb(Assembler::less, loop);

      __ pop(rdi);              // restore temp

      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_drop_args:
    {
      // 'argslot' is the position of the first argument to nuke
      __ movl(rax_argslot, rcx_amh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot));

      __ push(rdi);             // need a temp
      // (must do previous push after argslot address is taken)

      // 'stack_move' is number of words to drop
      Register rdi_stack_move = rdi;
      __ movl2ptr(rdi_stack_move, rcx_amh_conversion);
      __ sarptr(rdi_stack_move, CONV_STACK_MOVE_SHIFT);
      remove_arg_slots(_masm, rdi_stack_move,
                       rax_argslot, rbx_temp, rdx_temp);

      __ pop(rdi);              // restore temp

      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_collect_args:
    __ unimplemented(entry_name(ek)); // %%% FIXME: NYI
    break;

  case _adapter_spread_args:
    // handled completely by optimized cases
    __ stop("init_AdapterMethodHandle should not issue this");
    break;

  case _adapter_opt_spread_0:
  case _adapter_opt_spread_1:
  case _adapter_opt_spread_more:
    {
      // spread an array out into a group of arguments
      int length_constant = get_ek_adapter_opt_spread_info(ek);

      // find the address of the array argument
      __ movl(rax_argslot, rcx_amh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot));

      // grab some temps
      { __ push(rsi); __ push(rdi); }
      // (preceding pushes must be done after argslot address is taken!)
#define UNPUSH_RSI_RDI \
      { __ pop(rdi); __ pop(rsi); }

      // arx_argslot points both to the array and to the first output arg
      vmarg = Address(rax_argslot, 0);

      // Get the array value.
      Register  rsi_array       = rsi;
      Register  rdx_array_klass = rdx_temp;
      BasicType elem_type       = T_OBJECT;
      int       length_offset   = arrayOopDesc::length_offset_in_bytes();
      int       elem0_offset    = arrayOopDesc::base_offset_in_bytes(elem_type);
      __ movptr(rsi_array, vmarg);
      Label skip_array_check;
      if (length_constant == 0) {
        __ testptr(rsi_array, rsi_array);
        __ jcc(Assembler::zero, skip_array_check);
      }
      __ null_check(rsi_array, oopDesc::klass_offset_in_bytes());
      __ load_klass(rdx_array_klass, rsi_array);

      // Check the array type.
      Register rbx_klass = rbx_temp;
      __ load_heap_oop(rbx_klass, rcx_amh_argument); // this is a Class object!
      __ load_heap_oop(rbx_klass, Address(rbx_klass, java_lang_Class::klass_offset_in_bytes()));

      Label ok_array_klass, bad_array_klass, bad_array_length;
      __ check_klass_subtype(rdx_array_klass, rbx_klass, rdi, ok_array_klass);
      // If we get here, the type check failed!
      __ jmp(bad_array_klass);
      __ bind(ok_array_klass);

      // Check length.
      if (length_constant >= 0) {
        __ cmpl(Address(rsi_array, length_offset), length_constant);
      } else {
        Register rbx_vminfo = rbx_temp;
        __ movl(rbx_vminfo, rcx_amh_conversion);
        assert(CONV_VMINFO_SHIFT == 0, "preshifted");
        __ andl(rbx_vminfo, CONV_VMINFO_MASK);
        __ cmpl(rbx_vminfo, Address(rsi_array, length_offset));
      }
      __ jcc(Assembler::notEqual, bad_array_length);

      Register rdx_argslot_limit = rdx_temp;

      // Array length checks out.  Now insert any required stack slots.
      if (length_constant == -1) {
        // Form a pointer to the end of the affected region.
        __ lea(rdx_argslot_limit, Address(rax_argslot, Interpreter::stackElementSize));
        // 'stack_move' is negative number of words to insert
        Register rdi_stack_move = rdi;
        __ movl2ptr(rdi_stack_move, rcx_amh_conversion);
        __ sarptr(rdi_stack_move, CONV_STACK_MOVE_SHIFT);
        Register rsi_temp = rsi_array;  // spill this
        insert_arg_slots(_masm, rdi_stack_move, -1,
                         rax_argslot, rbx_temp, rsi_temp);
        // reload the array (since rsi was killed)
        __ movptr(rsi_array, vmarg);
      } else if (length_constant > 1) {
        int arg_mask = 0;
        int new_slots = (length_constant - 1);
        for (int i = 0; i < new_slots; i++) {
          arg_mask <<= 1;
          arg_mask |= _INSERT_REF_MASK;
        }
        insert_arg_slots(_masm, new_slots * stack_move_unit(), arg_mask,
                         rax_argslot, rbx_temp, rdx_temp);
      } else if (length_constant == 1) {
        // no stack resizing required
      } else if (length_constant == 0) {
        remove_arg_slots(_masm, -stack_move_unit(),
                         rax_argslot, rbx_temp, rdx_temp);
      }

      // Copy from the array to the new slots.
      // Note: Stack change code preserves integrity of rax_argslot pointer.
      // So even after slot insertions, rax_argslot still points to first argument.
      if (length_constant == -1) {
        // [rax_argslot, rdx_argslot_limit) is the area we are inserting into.
        Register rsi_source = rsi_array;
        __ lea(rsi_source, Address(rsi_array, elem0_offset));
        Label loop;
        __ bind(loop);
        __ movptr(rbx_temp, Address(rsi_source, 0));
        __ movptr(Address(rax_argslot, 0), rbx_temp);
        __ addptr(rsi_source, type2aelembytes(elem_type));
        __ addptr(rax_argslot, Interpreter::stackElementSize);
        __ cmpptr(rax_argslot, rdx_argslot_limit);
        __ jccb(Assembler::less, loop);
      } else if (length_constant == 0) {
        __ bind(skip_array_check);
        // nothing to copy
      } else {
        int elem_offset = elem0_offset;
        int slot_offset = 0;
        for (int index = 0; index < length_constant; index++) {
          __ movptr(rbx_temp, Address(rsi_array, elem_offset));
          __ movptr(Address(rax_argslot, slot_offset), rbx_temp);
          elem_offset += type2aelembytes(elem_type);
           slot_offset += Interpreter::stackElementSize;
        }
      }

      // Arguments are spread.  Move to next method handle.
      UNPUSH_RSI_RDI;
      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);

      __ bind(bad_array_klass);
      UNPUSH_RSI_RDI;
      __ pushptr(Address(rdx_array_klass, java_mirror_offset)); // required type
      __ pushptr(vmarg);                // bad array
      __ push((int)Bytecodes::_aaload); // who is complaining?
      __ jump(ExternalAddress(from_interpreted_entry(_raise_exception)));

      __ bind(bad_array_length);
      UNPUSH_RSI_RDI;
      __ push(rcx_recv);        // AMH requiring a certain length
      __ pushptr(vmarg);        // bad array
      __ push((int)Bytecodes::_arraylength); // who is complaining?
      __ jump(ExternalAddress(from_interpreted_entry(_raise_exception)));

#undef UNPUSH_RSI_RDI
    }
    break;

  case _adapter_flyby:
  case _adapter_ricochet:
    __ unimplemented(entry_name(ek)); // %%% FIXME: NYI
    break;

  default:  ShouldNotReachHere();
  }
  __ hlt();

  address me_cookie = MethodHandleEntry::start_compiled_entry(_masm, interp_entry);
  __ unimplemented(entry_name(ek)); // %%% FIXME: NYI

  init_entry(ek, MethodHandleEntry::finish_compiled_entry(_masm, me_cookie));
}
