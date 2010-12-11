/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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


// Code generation
address MethodHandles::generate_method_handle_interpreter_entry(MacroAssembler* _masm) {
  // I5_savedSP/O5_savedSP: sender SP (must preserve)
  // G4 (Gargs): incoming argument list (must preserve)
  // G5_method:  invoke methodOop
  // G3_method_handle: receiver method handle (must load from sp[MethodTypeForm.vmslots])
  // O0, O1, O2, O3, O4: garbage temps, blown away
  Register O0_mtype   = O0;
  Register O1_scratch = O1;
  Register O2_scratch = O2;
  Register O3_scratch = O3;
  Register O4_argslot = O4;
  Register O4_argbase = O4;

  // emit WrongMethodType path first, to enable back-branch from main path
  Label wrong_method_type;
  __ bind(wrong_method_type);
  Label invoke_generic_slow_path;
  assert(methodOopDesc::intrinsic_id_size_in_bytes() == sizeof(u1), "");;
  __ ldub(Address(G5_method, methodOopDesc::intrinsic_id_offset_in_bytes()), O1_scratch);
  __ cmp(O1_scratch, (int) vmIntrinsics::_invokeExact);
  __ brx(Assembler::notEqual, false, Assembler::pt, invoke_generic_slow_path);
  __ delayed()->nop();
  __ mov(O0_mtype, G5_method_type);  // required by throw_WrongMethodType
  // mov(G3_method_handle, G3_method_handle);  // already in this register
  __ jump_to(AddressLiteral(Interpreter::throw_WrongMethodType_entry()), O1_scratch);
  __ delayed()->nop();

  // here's where control starts out:
  __ align(CodeEntryAlignment);
  address entry_point = __ pc();

  // fetch the MethodType from the method handle
  {
    Register tem = G5_method;
    for (jint* pchase = methodOopDesc::method_type_offsets_chain(); (*pchase) != -1; pchase++) {
      __ ld_ptr(Address(tem, *pchase), O0_mtype);
      tem = O0_mtype;          // in case there is another indirection
    }
  }

  // given the MethodType, find out where the MH argument is buried
  __ load_heap_oop(Address(O0_mtype,   __ delayed_value(java_dyn_MethodType::form_offset_in_bytes,        O1_scratch)), O4_argslot);
  __ ldsw(         Address(O4_argslot, __ delayed_value(java_dyn_MethodTypeForm::vmslots_offset_in_bytes, O1_scratch)), O4_argslot);
  __ add(Gargs, __ argument_offset(O4_argslot, 1), O4_argbase);
  // Note: argument_address uses its input as a scratch register!
  __ ld_ptr(Address(O4_argbase, -Interpreter::stackElementSize), G3_method_handle);

  trace_method_handle(_masm, "invokeExact");

  __ check_method_handle_type(O0_mtype, G3_method_handle, O1_scratch, wrong_method_type);
  __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);

  // for invokeGeneric (only), apply argument and result conversions on the fly
  __ bind(invoke_generic_slow_path);
#ifdef ASSERT
  { Label L;
    __ ldub(Address(G5_method, methodOopDesc::intrinsic_id_offset_in_bytes()), O1_scratch);
    __ cmp(O1_scratch, (int) vmIntrinsics::_invokeGeneric);
    __ brx(Assembler::equal, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("bad methodOop::intrinsic_id");
    __ bind(L);
  }
#endif //ASSERT

  // make room on the stack for another pointer:
  insert_arg_slots(_masm, 2 * stack_move_unit(), _INSERT_REF_MASK, O4_argbase, O1_scratch, O2_scratch, O3_scratch);
  // load up an adapter from the calling type (Java weaves this)
  Register O2_form    = O2_scratch;
  Register O3_adapter = O3_scratch;
  __ load_heap_oop(Address(O0_mtype, __ delayed_value(java_dyn_MethodType::form_offset_in_bytes,               O1_scratch)), O2_form);
  // load_heap_oop(Address(O2_form,  __ delayed_value(java_dyn_MethodTypeForm::genericInvoker_offset_in_bytes, O1_scratch)), O3_adapter);
  // deal with old JDK versions:
  __ add(          Address(O2_form,  __ delayed_value(java_dyn_MethodTypeForm::genericInvoker_offset_in_bytes, O1_scratch)), O3_adapter);
  __ cmp(O3_adapter, O2_form);
  Label sorry_no_invoke_generic;
  __ brx(Assembler::lessUnsigned, false, Assembler::pn, sorry_no_invoke_generic);
  __ delayed()->nop();

  __ load_heap_oop(Address(O3_adapter, 0), O3_adapter);
  __ tst(O3_adapter);
  __ brx(Assembler::zero, false, Assembler::pn, sorry_no_invoke_generic);
  __ delayed()->nop();
  __ st_ptr(O3_adapter, Address(O4_argbase, 1 * Interpreter::stackElementSize));
  // As a trusted first argument, pass the type being called, so the adapter knows
  // the actual types of the arguments and return values.
  // (Generic invokers are shared among form-families of method-type.)
  __ st_ptr(O0_mtype,   Address(O4_argbase, 0 * Interpreter::stackElementSize));
  // FIXME: assert that O3_adapter is of the right method-type.
  __ mov(O3_adapter, G3_method_handle);
  trace_method_handle(_masm, "invokeGeneric");
  __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);

  __ bind(sorry_no_invoke_generic); // no invokeGeneric implementation available!
  __ mov(O0_mtype, G5_method_type);  // required by throw_WrongMethodType
  // mov(G3_method_handle, G3_method_handle);  // already in this register
  __ jump_to(AddressLiteral(Interpreter::throw_WrongMethodType_entry()), O1_scratch);
  __ delayed()->nop();

  return entry_point;
}


#ifdef ASSERT
static void verify_argslot(MacroAssembler* _masm, Register argslot_reg, Register temp_reg, const char* error_message) {
  // Verify that argslot lies within (Gargs, FP].
  Label L_ok, L_bad;
  BLOCK_COMMENT("{ verify_argslot");
#ifdef _LP64
  __ add(FP, STACK_BIAS, temp_reg);
  __ cmp(argslot_reg, temp_reg);
#else
  __ cmp(argslot_reg, FP);
#endif
  __ brx(Assembler::greaterUnsigned, false, Assembler::pn, L_bad);
  __ delayed()->nop();
  __ cmp(Gargs, argslot_reg);
  __ brx(Assembler::lessEqualUnsigned, false, Assembler::pt, L_ok);
  __ delayed()->nop();
  __ bind(L_bad);
  __ stop(error_message);
  __ bind(L_ok);
  BLOCK_COMMENT("} verify_argslot");
}
#endif


// Helper to insert argument slots into the stack.
// arg_slots must be a multiple of stack_move_unit() and <= 0
void MethodHandles::insert_arg_slots(MacroAssembler* _masm,
                                     RegisterOrConstant arg_slots,
                                     int arg_mask,
                                     Register argslot_reg,
                                     Register temp_reg, Register temp2_reg, Register temp3_reg) {
  assert(temp3_reg != noreg, "temp3 required");
  assert_different_registers(argslot_reg, temp_reg, temp2_reg, temp3_reg,
                             (!arg_slots.is_register() ? Gargs : arg_slots.as_register()));

#ifdef ASSERT
  verify_argslot(_masm, argslot_reg, temp_reg, "insertion point must fall within current frame");
  if (arg_slots.is_register()) {
    Label L_ok, L_bad;
    __ cmp(arg_slots.as_register(), (int32_t) NULL_WORD);
    __ br(Assembler::greater, false, Assembler::pn, L_bad);
    __ delayed()->nop();
    __ btst(-stack_move_unit() - 1, arg_slots.as_register());
    __ br(Assembler::zero, false, Assembler::pt, L_ok);
    __ delayed()->nop();
    __ bind(L_bad);
    __ stop("assert arg_slots <= 0 and clear low bits");
    __ bind(L_ok);
  } else {
    assert(arg_slots.as_constant() <= 0, "");
    assert(arg_slots.as_constant() % -stack_move_unit() == 0, "");
  }
#endif // ASSERT

#ifdef _LP64
  if (arg_slots.is_register()) {
    // Was arg_slots register loaded as signed int?
    Label L_ok;
    __ sll(arg_slots.as_register(), BitsPerInt, temp_reg);
    __ sra(temp_reg, BitsPerInt, temp_reg);
    __ cmp(arg_slots.as_register(), temp_reg);
    __ br(Assembler::equal, false, Assembler::pt, L_ok);
    __ delayed()->nop();
    __ stop("arg_slots register not loaded as signed int");
    __ bind(L_ok);
  }
#endif

  // Make space on the stack for the inserted argument(s).
  // Then pull down everything shallower than argslot_reg.
  // The stacked return address gets pulled down with everything else.
  // That is, copy [sp, argslot) downward by -size words.  In pseudo-code:
  //   sp -= size;
  //   for (temp = sp + size; temp < argslot; temp++)
  //     temp[-size] = temp[0]
  //   argslot -= size;
  BLOCK_COMMENT("insert_arg_slots {");
  RegisterOrConstant offset = __ regcon_sll_ptr(arg_slots, LogBytesPerWord, temp3_reg);

  // Keep the stack pointer 2*wordSize aligned.
  const int TwoWordAlignmentMask = right_n_bits(LogBytesPerWord + 1);
  RegisterOrConstant masked_offset = __ regcon_andn_ptr(offset, TwoWordAlignmentMask, temp_reg);
  __ add(SP, masked_offset, SP);

  __ mov(Gargs, temp_reg);  // source pointer for copy
  __ add(Gargs, offset, Gargs);

  {
    Label loop;
    __ BIND(loop);
    // pull one word down each time through the loop
    __ ld_ptr(Address(temp_reg, 0), temp2_reg);
    __ st_ptr(temp2_reg, Address(temp_reg, offset));
    __ add(temp_reg, wordSize, temp_reg);
    __ cmp(temp_reg, argslot_reg);
    __ brx(Assembler::less, false, Assembler::pt, loop);
    __ delayed()->nop();  // FILLME
  }

  // Now move the argslot down, to point to the opened-up space.
  __ add(argslot_reg, offset, argslot_reg);
  BLOCK_COMMENT("} insert_arg_slots");
}


// Helper to remove argument slots from the stack.
// arg_slots must be a multiple of stack_move_unit() and >= 0
void MethodHandles::remove_arg_slots(MacroAssembler* _masm,
                                     RegisterOrConstant arg_slots,
                                     Register argslot_reg,
                                     Register temp_reg, Register temp2_reg, Register temp3_reg) {
  assert(temp3_reg != noreg, "temp3 required");
  assert_different_registers(argslot_reg, temp_reg, temp2_reg, temp3_reg,
                             (!arg_slots.is_register() ? Gargs : arg_slots.as_register()));

  RegisterOrConstant offset = __ regcon_sll_ptr(arg_slots, LogBytesPerWord, temp3_reg);

#ifdef ASSERT
  // Verify that [argslot..argslot+size) lies within (Gargs, FP).
  __ add(argslot_reg, offset, temp2_reg);
  verify_argslot(_masm, temp2_reg, temp_reg, "deleted argument(s) must fall within current frame");
  if (arg_slots.is_register()) {
    Label L_ok, L_bad;
    __ cmp(arg_slots.as_register(), (int32_t) NULL_WORD);
    __ br(Assembler::less, false, Assembler::pn, L_bad);
    __ delayed()->nop();
    __ btst(-stack_move_unit() - 1, arg_slots.as_register());
    __ br(Assembler::zero, false, Assembler::pt, L_ok);
    __ delayed()->nop();
    __ bind(L_bad);
    __ stop("assert arg_slots >= 0 and clear low bits");
    __ bind(L_ok);
  } else {
    assert(arg_slots.as_constant() >= 0, "");
    assert(arg_slots.as_constant() % -stack_move_unit() == 0, "");
  }
#endif // ASSERT

  BLOCK_COMMENT("remove_arg_slots {");
  // Pull up everything shallower than argslot.
  // Then remove the excess space on the stack.
  // The stacked return address gets pulled up with everything else.
  // That is, copy [sp, argslot) upward by size words.  In pseudo-code:
  //   for (temp = argslot-1; temp >= sp; --temp)
  //     temp[size] = temp[0]
  //   argslot += size;
  //   sp += size;
  __ sub(argslot_reg, wordSize, temp_reg);  // source pointer for copy
  {
    Label loop;
    __ BIND(loop);
    // pull one word up each time through the loop
    __ ld_ptr(Address(temp_reg, 0), temp2_reg);
    __ st_ptr(temp2_reg, Address(temp_reg, offset));
    __ sub(temp_reg, wordSize, temp_reg);
    __ cmp(temp_reg, Gargs);
    __ brx(Assembler::greaterEqual, false, Assembler::pt, loop);
    __ delayed()->nop();  // FILLME
  }

  // Now move the argslot up, to point to the just-copied block.
  __ add(Gargs, offset, Gargs);
  // And adjust the argslot address to point at the deletion point.
  __ add(argslot_reg, offset, argslot_reg);

  // Keep the stack pointer 2*wordSize aligned.
  const int TwoWordAlignmentMask = right_n_bits(LogBytesPerWord + 1);
  RegisterOrConstant masked_offset = __ regcon_andn_ptr(offset, TwoWordAlignmentMask, temp_reg);
  __ add(SP, masked_offset, SP);
  BLOCK_COMMENT("} remove_arg_slots");
}


#ifndef PRODUCT
extern "C" void print_method_handle(oop mh);
void trace_method_handle_stub(const char* adaptername,
                              oopDesc* mh) {
  printf("MH %s mh="INTPTR_FORMAT"\n", adaptername, (intptr_t) mh);
  print_method_handle(mh);
}
void MethodHandles::trace_method_handle(MacroAssembler* _masm, const char* adaptername) {
  if (!TraceMethodHandles)  return;
  BLOCK_COMMENT("trace_method_handle {");
  // save: Gargs, O5_savedSP
  __ save_frame(16);
  __ set((intptr_t) adaptername, O0);
  __ mov(G3_method_handle, O1);
  __ mov(G3_method_handle, L3);
  __ mov(Gargs, L4);
  __ mov(G5_method_type, L5);
  __ call_VM_leaf(L7, CAST_FROM_FN_PTR(address, trace_method_handle_stub));

  __ mov(L3, G3_method_handle);
  __ mov(L4, Gargs);
  __ mov(L5, G5_method_type);
  __ restore();
  BLOCK_COMMENT("} trace_method_handle");
}
#endif // PRODUCT

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

//------------------------------------------------------------------------------
// MethodHandles::generate_method_handle_stub
//
// Generate an "entry" field for a method handle.
// This determines how the method handle will respond to calls.
void MethodHandles::generate_method_handle_stub(MacroAssembler* _masm, MethodHandles::EntryKind ek) {
  // Here is the register state during an interpreted call,
  // as set up by generate_method_handle_interpreter_entry():
  // - G5: garbage temp (was MethodHandle.invoke methodOop, unused)
  // - G3: receiver method handle
  // - O5_savedSP: sender SP (must preserve)

  Register O0_argslot = O0;
  Register O1_scratch = O1;
  Register O2_scratch = O2;
  Register O3_scratch = O3;
  Register G5_index   = G5;

  guarantee(java_dyn_MethodHandle::vmentry_offset_in_bytes() != 0, "must have offsets");

  // Some handy addresses:
  Address G5_method_fie(    G5_method,        in_bytes(methodOopDesc::from_interpreted_offset()));

  Address G3_mh_vmtarget(   G3_method_handle, java_dyn_MethodHandle::vmtarget_offset_in_bytes());

  Address G3_dmh_vmindex(   G3_method_handle, sun_dyn_DirectMethodHandle::vmindex_offset_in_bytes());

  Address G3_bmh_vmargslot( G3_method_handle, sun_dyn_BoundMethodHandle::vmargslot_offset_in_bytes());
  Address G3_bmh_argument(  G3_method_handle, sun_dyn_BoundMethodHandle::argument_offset_in_bytes());

  Address G3_amh_vmargslot( G3_method_handle, sun_dyn_AdapterMethodHandle::vmargslot_offset_in_bytes());
  Address G3_amh_argument ( G3_method_handle, sun_dyn_AdapterMethodHandle::argument_offset_in_bytes());
  Address G3_amh_conversion(G3_method_handle, sun_dyn_AdapterMethodHandle::conversion_offset_in_bytes());

  const int java_mirror_offset = klassOopDesc::klass_part_offset_in_bytes() + Klass::java_mirror_offset_in_bytes();

  if (have_entry(ek)) {
    __ nop();  // empty stubs make SG sick
    return;
  }

  address interp_entry = __ pc();

  trace_method_handle(_masm, entry_name(ek));

  switch ((int) ek) {
  case _raise_exception:
    {
      // Not a real MH entry, but rather shared code for raising an
      // exception.  Extra local arguments are passed in scratch
      // registers, as required type in O3, failing object (or NULL)
      // in O2, failing bytecode type in O1.

      __ mov(O5_savedSP, SP);  // Cut the stack back to where the caller started.

      // Push arguments as if coming from the interpreter.
      Register O0_scratch = O0_argslot;
      int stackElementSize = Interpreter::stackElementSize;

      // Make space on the stack for the arguments and set Gargs
      // correctly.
      __ sub(SP, 4*stackElementSize, SP);  // Keep stack aligned.
      __ add(SP, (frame::varargs_offset)*wordSize - 1*Interpreter::stackElementSize + STACK_BIAS + BytesPerWord, Gargs);

      // void raiseException(int code, Object actual, Object required)
      __ st(    O1_scratch, Address(Gargs, 2*stackElementSize));  // code
      __ st_ptr(O2_scratch, Address(Gargs, 1*stackElementSize));  // actual
      __ st_ptr(O3_scratch, Address(Gargs, 0*stackElementSize));  // required

      Label no_method;
      // FIXME: fill in _raise_exception_method with a suitable sun.dyn method
      __ set(AddressLiteral((address) &_raise_exception_method), G5_method);
      __ ld_ptr(Address(G5_method, 0), G5_method);
      __ tst(G5_method);
      __ brx(Assembler::zero, false, Assembler::pn, no_method);
      __ delayed()->nop();

      int jobject_oop_offset = 0;
      __ ld_ptr(Address(G5_method, jobject_oop_offset), G5_method);
      __ tst(G5_method);
      __ brx(Assembler::zero, false, Assembler::pn, no_method);
      __ delayed()->nop();

      __ verify_oop(G5_method);
      __ jump_indirect_to(G5_method_fie, O1_scratch);
      __ delayed()->nop();

      // If we get here, the Java runtime did not do its job of creating the exception.
      // Do something that is at least causes a valid throw from the interpreter.
      __ bind(no_method);
      __ unimplemented("_raise_exception no method");
    }
    break;

  case _invokestatic_mh:
  case _invokespecial_mh:
    {
      __ load_heap_oop(G3_mh_vmtarget, G5_method);  // target is a methodOop
      __ verify_oop(G5_method);
      // Same as TemplateTable::invokestatic or invokespecial,
      // minus the CP setup and profiling:
      if (ek == _invokespecial_mh) {
        // Must load & check the first argument before entering the target method.
        __ load_method_handle_vmslots(O0_argslot, G3_method_handle, O1_scratch);
        __ ld_ptr(__ argument_address(O0_argslot), G3_method_handle);
        __ null_check(G3_method_handle);
        __ verify_oop(G3_method_handle);
      }
      __ jump_indirect_to(G5_method_fie, O1_scratch);
      __ delayed()->nop();
    }
    break;

  case _invokevirtual_mh:
    {
      // Same as TemplateTable::invokevirtual,
      // minus the CP setup and profiling:

      // Pick out the vtable index and receiver offset from the MH,
      // and then we can discard it:
      __ load_method_handle_vmslots(O0_argslot, G3_method_handle, O1_scratch);
      __ ldsw(G3_dmh_vmindex, G5_index);
      // Note:  The verifier allows us to ignore G3_mh_vmtarget.
      __ ld_ptr(__ argument_address(O0_argslot, -1), G3_method_handle);
      __ null_check(G3_method_handle, oopDesc::klass_offset_in_bytes());

      // Get receiver klass:
      Register O0_klass = O0_argslot;
      __ load_klass(G3_method_handle, O0_klass);
      __ verify_oop(O0_klass);

      // Get target methodOop & entry point:
      const int base = instanceKlass::vtable_start_offset() * wordSize;
      assert(vtableEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");

      __ sll_ptr(G5_index, LogBytesPerWord, G5_index);
      __ add(O0_klass, G5_index, O0_klass);
      Address vtable_entry_addr(O0_klass, base + vtableEntry::method_offset_in_bytes());
      __ ld_ptr(vtable_entry_addr, G5_method);

      __ verify_oop(G5_method);
      __ jump_indirect_to(G5_method_fie, O1_scratch);
      __ delayed()->nop();
    }
    break;

  case _invokeinterface_mh:
    {
      // Same as TemplateTable::invokeinterface,
      // minus the CP setup and profiling:
      __ load_method_handle_vmslots(O0_argslot, G3_method_handle, O1_scratch);
      Register O1_intf  = O1_scratch;
      __ load_heap_oop(G3_mh_vmtarget, O1_intf);
      __ ldsw(G3_dmh_vmindex, G5_index);
      __ ld_ptr(__ argument_address(O0_argslot, -1), G3_method_handle);
      __ null_check(G3_method_handle, oopDesc::klass_offset_in_bytes());

      // Get receiver klass:
      Register O0_klass = O0_argslot;
      __ load_klass(G3_method_handle, O0_klass);
      __ verify_oop(O0_klass);

      // Get interface:
      Label no_such_interface;
      __ verify_oop(O1_intf);
      __ lookup_interface_method(O0_klass, O1_intf,
                                 // Note: next two args must be the same:
                                 G5_index, G5_method,
                                 O2_scratch,
                                 O3_scratch,
                                 no_such_interface);

      __ verify_oop(G5_method);
      __ jump_indirect_to(G5_method_fie, O1_scratch);
      __ delayed()->nop();

      __ bind(no_such_interface);
      // Throw an exception.
      // For historical reasons, it will be IncompatibleClassChangeError.
      __ unimplemented("not tested yet");
      __ ld_ptr(Address(O1_intf, java_mirror_offset), O3_scratch);  // required interface
      __ mov(O0_klass, O2_scratch);  // bad receiver
      __ jump_to(AddressLiteral(from_interpreted_entry(_raise_exception)), O0_argslot);
      __ delayed()->mov(Bytecodes::_invokeinterface, O1_scratch);  // who is complaining?
    }
    break;

  case _bound_ref_mh:
  case _bound_int_mh:
  case _bound_long_mh:
  case _bound_ref_direct_mh:
  case _bound_int_direct_mh:
  case _bound_long_direct_mh:
    {
      const bool direct_to_method = (ek >= _bound_ref_direct_mh);
      BasicType arg_type  = T_ILLEGAL;
      int       arg_mask  = _INSERT_NO_MASK;
      int       arg_slots = -1;
      get_ek_bound_mh_info(ek, arg_type, arg_mask, arg_slots);

      // Make room for the new argument:
      __ ldsw(G3_bmh_vmargslot, O0_argslot);
      __ add(Gargs, __ argument_offset(O0_argslot), O0_argslot);

      insert_arg_slots(_masm, arg_slots * stack_move_unit(), arg_mask, O0_argslot, O1_scratch, O2_scratch, G5_index);

      // Store bound argument into the new stack slot:
      __ load_heap_oop(G3_bmh_argument, O1_scratch);
      if (arg_type == T_OBJECT) {
        __ st_ptr(O1_scratch, Address(O0_argslot, 0));
      } else {
        Address prim_value_addr(O1_scratch, java_lang_boxing_object::value_offset_in_bytes(arg_type));
        __ load_sized_value(prim_value_addr, O2_scratch, type2aelembytes(arg_type), is_signed_subword_type(arg_type));
        if (arg_slots == 2) {
          __ unimplemented("not yet tested");
#ifndef _LP64
          __ signx(O2_scratch, O3_scratch);  // Sign extend
#endif
          __ st_long(O2_scratch, Address(O0_argslot, 0));  // Uses O2/O3 on !_LP64
        } else {
          __ st_ptr( O2_scratch, Address(O0_argslot, 0));
        }
      }

      if (direct_to_method) {
        __ load_heap_oop(G3_mh_vmtarget, G5_method);  // target is a methodOop
        __ verify_oop(G5_method);
        __ jump_indirect_to(G5_method_fie, O1_scratch);
        __ delayed()->nop();
      } else {
        __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);  // target is a methodOop
        __ verify_oop(G3_method_handle);
        __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
      }
    }
    break;

  case _adapter_retype_only:
  case _adapter_retype_raw:
    // Immediately jump to the next MH layer:
    __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
    __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    // This is OK when all parameter types widen.
    // It is also OK when a return type narrows.
    break;

  case _adapter_check_cast:
    {
      // Temps:
      Register G5_klass = G5_index;  // Interesting AMH data.

      // Check a reference argument before jumping to the next layer of MH:
      __ ldsw(G3_amh_vmargslot, O0_argslot);
      Address vmarg = __ argument_address(O0_argslot);

      // What class are we casting to?
      __ load_heap_oop(G3_amh_argument, G5_klass);  // This is a Class object!
      __ load_heap_oop(Address(G5_klass, java_lang_Class::klass_offset_in_bytes()), G5_klass);

      Label done;
      __ ld_ptr(vmarg, O1_scratch);
      __ tst(O1_scratch);
      __ brx(Assembler::zero, false, Assembler::pn, done);  // No cast if null.
      __ delayed()->nop();
      __ load_klass(O1_scratch, O1_scratch);

      // Live at this point:
      // - G5_klass        :  klass required by the target method
      // - O1_scratch      :  argument klass to test
      // - G3_method_handle:  adapter method handle
      __ check_klass_subtype(O1_scratch, G5_klass, O0_argslot, O2_scratch, done);

      // If we get here, the type check failed!
      __ ldsw(G3_amh_vmargslot, O0_argslot);  // reload argslot field
      __ load_heap_oop(G3_amh_argument, O3_scratch);  // required class
      __ ld_ptr(vmarg, O2_scratch);  // bad object
      __ jump_to(AddressLiteral(from_interpreted_entry(_raise_exception)), O0_argslot);
      __ delayed()->mov(Bytecodes::_checkcast, O1_scratch);  // who is complaining?

      __ bind(done);
      // Get the new MH:
      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_prim_to_prim:
  case _adapter_ref_to_prim:
    // Handled completely by optimized cases.
    __ stop("init_AdapterMethodHandle should not issue this");
    break;

  case _adapter_opt_i2i:        // optimized subcase of adapt_prim_to_prim
//case _adapter_opt_f2i:        // optimized subcase of adapt_prim_to_prim
  case _adapter_opt_l2i:        // optimized subcase of adapt_prim_to_prim
  case _adapter_opt_unboxi:     // optimized subcase of adapt_ref_to_prim
    {
      // Perform an in-place conversion to int or an int subword.
      __ ldsw(G3_amh_vmargslot, O0_argslot);
      Address vmarg = __ argument_address(O0_argslot);
      Address value;
      bool value_left_justified = false;

      switch (ek) {
      case _adapter_opt_i2i:
        value = vmarg;
        break;
      case _adapter_opt_l2i:
        {
          // just delete the extra slot
          __ add(Gargs, __ argument_offset(O0_argslot), O0_argslot);
          remove_arg_slots(_masm, -stack_move_unit(), O0_argslot, O1_scratch, O2_scratch, O3_scratch);
          value = vmarg = Address(O0_argslot, 0);
        }
        break;
      case _adapter_opt_unboxi:
        {
          // Load the value up from the heap.
          __ ld_ptr(vmarg, O1_scratch);
          int value_offset = java_lang_boxing_object::value_offset_in_bytes(T_INT);
#ifdef ASSERT
          for (int bt = T_BOOLEAN; bt < T_INT; bt++) {
            if (is_subword_type(BasicType(bt)))
              assert(value_offset == java_lang_boxing_object::value_offset_in_bytes(BasicType(bt)), "");
          }
#endif
          __ null_check(O1_scratch, value_offset);
          value = Address(O1_scratch, value_offset);
#ifdef _BIG_ENDIAN
          // Values stored in objects are packed.
          value_left_justified = true;
#endif
        }
        break;
      default:
        ShouldNotReachHere();
      }

      // This check is required on _BIG_ENDIAN
      Register G5_vminfo = G5_index;
      __ ldsw(G3_amh_conversion, G5_vminfo);
      assert(CONV_VMINFO_SHIFT == 0, "preshifted");

      // Original 32-bit vmdata word must be of this form:
      // | MBZ:6 | signBitCount:8 | srcDstTypes:8 | conversionOp:8 |
      __ lduw(value, O1_scratch);
      if (!value_left_justified)
        __ sll(O1_scratch, G5_vminfo, O1_scratch);
      Label zero_extend, done;
      __ btst(CONV_VMINFO_SIGN_FLAG, G5_vminfo);
      __ br(Assembler::zero, false, Assembler::pn, zero_extend);
      __ delayed()->nop();

      // this path is taken for int->byte, int->short
      __ sra(O1_scratch, G5_vminfo, O1_scratch);
      __ ba(false, done);
      __ delayed()->nop();

      __ bind(zero_extend);
      // this is taken for int->char
      __ srl(O1_scratch, G5_vminfo, O1_scratch);

      __ bind(done);
      __ st(O1_scratch, vmarg);

      // Get the new MH:
      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_opt_i2l:        // optimized subcase of adapt_prim_to_prim
  case _adapter_opt_unboxl:     // optimized subcase of adapt_ref_to_prim
    {
      // Perform an in-place int-to-long or ref-to-long conversion.
      __ ldsw(G3_amh_vmargslot, O0_argslot);

      // On big-endian machine we duplicate the slot and store the MSW
      // in the first slot.
      __ add(Gargs, __ argument_offset(O0_argslot, 1), O0_argslot);

      insert_arg_slots(_masm, stack_move_unit(), _INSERT_INT_MASK, O0_argslot, O1_scratch, O2_scratch, G5_index);

      Address arg_lsw(O0_argslot, 0);
      Address arg_msw(O0_argslot, -Interpreter::stackElementSize);

      switch (ek) {
      case _adapter_opt_i2l:
        {
          __ ldsw(arg_lsw, O2_scratch);      // Load LSW
#ifndef _LP64
          __ signx(O2_scratch, O3_scratch);  // Sign extend
#endif
          __ st_long(O2_scratch, arg_msw);   // Uses O2/O3 on !_LP64
        }
        break;
      case _adapter_opt_unboxl:
        {
          // Load the value up from the heap.
          __ ld_ptr(arg_lsw, O1_scratch);
          int value_offset = java_lang_boxing_object::value_offset_in_bytes(T_LONG);
          assert(value_offset == java_lang_boxing_object::value_offset_in_bytes(T_DOUBLE), "");
          __ null_check(O1_scratch, value_offset);
          __ ld_long(Address(O1_scratch, value_offset), O2_scratch);  // Uses O2/O3 on !_LP64
          __ st_long(O2_scratch, arg_msw);
        }
        break;
      default:
        ShouldNotReachHere();
      }

      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_opt_f2d:        // optimized subcase of adapt_prim_to_prim
  case _adapter_opt_d2f:        // optimized subcase of adapt_prim_to_prim
    {
      // perform an in-place floating primitive conversion
      __ unimplemented(entry_name(ek));
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

      // 'argslot' is the position of the first argument to swap.
      __ ldsw(G3_amh_vmargslot, O0_argslot);
      __ add(Gargs, __ argument_offset(O0_argslot), O0_argslot);

      // 'vminfo' is the second.
      Register O1_destslot = O1_scratch;
      __ ldsw(G3_amh_conversion, O1_destslot);
      assert(CONV_VMINFO_SHIFT == 0, "preshifted");
      __ and3(O1_destslot, CONV_VMINFO_MASK, O1_destslot);
      __ add(Gargs, __ argument_offset(O1_destslot), O1_destslot);

      if (!rotate) {
        for (int i = 0; i < swap_bytes; i += wordSize) {
          __ ld_ptr(Address(O0_argslot,  i), O2_scratch);
          __ ld_ptr(Address(O1_destslot, i), O3_scratch);
          __ st_ptr(O3_scratch, Address(O0_argslot,  i));
          __ st_ptr(O2_scratch, Address(O1_destslot, i));
        }
      } else {
        // Save the first chunk, which is going to get overwritten.
        switch (swap_bytes) {
        case 4 : __ lduw(Address(O0_argslot, 0), O2_scratch); break;
        case 16: __ ldx( Address(O0_argslot, 8), O3_scratch); //fall-thru
        case 8 : __ ldx( Address(O0_argslot, 0), O2_scratch); break;
        default: ShouldNotReachHere();
        }

        if (rotate > 0) {
          // Rorate upward.
          __ sub(O0_argslot, swap_bytes, O0_argslot);
#if ASSERT
          {
            // Verify that argslot > destslot, by at least swap_bytes.
            Label L_ok;
            __ cmp(O0_argslot, O1_destslot);
            __ brx(Assembler::greaterEqualUnsigned, false, Assembler::pt, L_ok);
            __ delayed()->nop();
            __ stop("source must be above destination (upward rotation)");
            __ bind(L_ok);
          }
#endif
          // Work argslot down to destslot, copying contiguous data upwards.
          // Pseudo-code:
          //   argslot  = src_addr - swap_bytes
          //   destslot = dest_addr
          //   while (argslot >= destslot) {
          //     *(argslot + swap_bytes) = *(argslot + 0);
          //     argslot--;
          //   }
          Label loop;
          __ bind(loop);
          __ ld_ptr(Address(O0_argslot, 0), G5_index);
          __ st_ptr(G5_index, Address(O0_argslot, swap_bytes));
          __ sub(O0_argslot, wordSize, O0_argslot);
          __ cmp(O0_argslot, O1_destslot);
          __ brx(Assembler::greaterEqualUnsigned, false, Assembler::pt, loop);
          __ delayed()->nop();  // FILLME
        } else {
          __ add(O0_argslot, swap_bytes, O0_argslot);
#if ASSERT
          {
            // Verify that argslot < destslot, by at least swap_bytes.
            Label L_ok;
            __ cmp(O0_argslot, O1_destslot);
            __ brx(Assembler::lessEqualUnsigned, false, Assembler::pt, L_ok);
            __ delayed()->nop();
            __ stop("source must be above destination (upward rotation)");
            __ bind(L_ok);
          }
#endif
          // Work argslot up to destslot, copying contiguous data downwards.
          // Pseudo-code:
          //   argslot  = src_addr + swap_bytes
          //   destslot = dest_addr
          //   while (argslot >= destslot) {
          //     *(argslot - swap_bytes) = *(argslot + 0);
          //     argslot++;
          //   }
          Label loop;
          __ bind(loop);
          __ ld_ptr(Address(O0_argslot, 0), G5_index);
          __ st_ptr(G5_index, Address(O0_argslot, -swap_bytes));
          __ add(O0_argslot, wordSize, O0_argslot);
          __ cmp(O0_argslot, O1_destslot);
          __ brx(Assembler::lessEqualUnsigned, false, Assembler::pt, loop);
          __ delayed()->nop();  // FILLME
        }

        // Store the original first chunk into the destination slot, now free.
        switch (swap_bytes) {
        case 4 : __ stw(O2_scratch, Address(O1_destslot, 0)); break;
        case 16: __ stx(O3_scratch, Address(O1_destslot, 8)); // fall-thru
        case 8 : __ stx(O2_scratch, Address(O1_destslot, 0)); break;
        default: ShouldNotReachHere();
        }
      }

      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_dup_args:
    {
      // 'argslot' is the position of the first argument to duplicate.
      __ ldsw(G3_amh_vmargslot, O0_argslot);
      __ add(Gargs, __ argument_offset(O0_argslot), O0_argslot);

      // 'stack_move' is negative number of words to duplicate.
      Register G5_stack_move = G5_index;
      __ ldsw(G3_amh_conversion, G5_stack_move);
      __ sra(G5_stack_move, CONV_STACK_MOVE_SHIFT, G5_stack_move);

      // Remember the old Gargs (argslot[0]).
      Register O1_oldarg = O1_scratch;
      __ mov(Gargs, O1_oldarg);

      // Move Gargs down to make room for dups.
      __ sll_ptr(G5_stack_move, LogBytesPerWord, G5_stack_move);
      __ add(Gargs, G5_stack_move, Gargs);

      // Compute the new Gargs (argslot[0]).
      Register O2_newarg = O2_scratch;
      __ mov(Gargs, O2_newarg);

      // Copy from oldarg[0...] down to newarg[0...]
      // Pseude-code:
      //   O1_oldarg  = old-Gargs
      //   O2_newarg  = new-Gargs
      //   O0_argslot = argslot
      //   while (O2_newarg < O1_oldarg) *O2_newarg = *O0_argslot++
      Label loop;
      __ bind(loop);
      __ ld_ptr(Address(O0_argslot, 0), O3_scratch);
      __ st_ptr(O3_scratch, Address(O2_newarg, 0));
      __ add(O0_argslot, wordSize, O0_argslot);
      __ add(O2_newarg,  wordSize, O2_newarg);
      __ cmp(O2_newarg, O1_oldarg);
      __ brx(Assembler::less, false, Assembler::pt, loop);
      __ delayed()->nop();  // FILLME

      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_drop_args:
    {
      // 'argslot' is the position of the first argument to nuke.
      __ ldsw(G3_amh_vmargslot, O0_argslot);
      __ add(Gargs, __ argument_offset(O0_argslot), O0_argslot);

      // 'stack_move' is number of words to drop.
      Register G5_stack_move = G5_index;
      __ ldsw(G3_amh_conversion, G5_stack_move);
      __ sra(G5_stack_move, CONV_STACK_MOVE_SHIFT, G5_stack_move);

      remove_arg_slots(_masm, G5_stack_move, O0_argslot, O1_scratch, O2_scratch, O3_scratch);

      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_collect_args:
    __ unimplemented(entry_name(ek)); // %%% FIXME: NYI
    break;

  case _adapter_spread_args:
    // Handled completely by optimized cases.
    __ stop("init_AdapterMethodHandle should not issue this");
    break;

  case _adapter_opt_spread_0:
  case _adapter_opt_spread_1:
  case _adapter_opt_spread_more:
    {
      // spread an array out into a group of arguments
      __ unimplemented(entry_name(ek));
    }
    break;

  case _adapter_flyby:
  case _adapter_ricochet:
    __ unimplemented(entry_name(ek)); // %%% FIXME: NYI
    break;

  default:
    ShouldNotReachHere();
  }

  address me_cookie = MethodHandleEntry::start_compiled_entry(_masm, interp_entry);
  __ unimplemented(entry_name(ek)); // %%% FIXME: NYI

  init_entry(ek, MethodHandleEntry::finish_compiled_entry(_masm, me_cookie));
}
