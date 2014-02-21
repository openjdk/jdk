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
#include "interp_masm_x86.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/methodData.hpp"

#ifndef CC_INTERP
void InterpreterMacroAssembler::profile_obj_type(Register obj, const Address& mdo_addr) {
  Label update, next, none;

  verify_oop(obj);

  testptr(obj, obj);
  jccb(Assembler::notZero, update);
  orptr(mdo_addr, TypeEntries::null_seen);
  jmpb(next);

  bind(update);
  load_klass(obj, obj);

  xorptr(obj, mdo_addr);
  testptr(obj, TypeEntries::type_klass_mask);
  jccb(Assembler::zero, next); // klass seen before, nothing to
                               // do. The unknown bit may have been
                               // set already but no need to check.

  testptr(obj, TypeEntries::type_unknown);
  jccb(Assembler::notZero, next); // already unknown. Nothing to do anymore.

  cmpptr(mdo_addr, 0);
  jccb(Assembler::equal, none);
  cmpptr(mdo_addr, TypeEntries::null_seen);
  jccb(Assembler::equal, none);
  // There is a chance that the checks above (re-reading profiling
  // data from memory) fail if another thread has just set the
  // profiling to this obj's klass
  xorptr(obj, mdo_addr);
  testptr(obj, TypeEntries::type_klass_mask);
  jccb(Assembler::zero, next);

  // different than before. Cannot keep accurate profile.
  orptr(mdo_addr, TypeEntries::type_unknown);
  jmpb(next);

  bind(none);
  // first time here. Set profile type.
  movptr(mdo_addr, obj);

  bind(next);
}

void InterpreterMacroAssembler::profile_arguments_type(Register mdp, Register callee, Register tmp, bool is_virtual) {
  if (!ProfileInterpreter) {
    return;
  }

  if (MethodData::profile_arguments() || MethodData::profile_return()) {
    Label profile_continue;

    test_method_data_pointer(mdp, profile_continue);

    int off_to_start = is_virtual ? in_bytes(VirtualCallData::virtual_call_data_size()) : in_bytes(CounterData::counter_data_size());

    cmpb(Address(mdp, in_bytes(DataLayout::tag_offset()) - off_to_start), is_virtual ? DataLayout::virtual_call_type_data_tag : DataLayout::call_type_data_tag);
    jcc(Assembler::notEqual, profile_continue);

    if (MethodData::profile_arguments()) {
      Label done;
      int off_to_args = in_bytes(TypeEntriesAtCall::args_data_offset());
      addptr(mdp, off_to_args);

      for (int i = 0; i < TypeProfileArgsLimit; i++) {
        if (i > 0 || MethodData::profile_return()) {
          // If return value type is profiled we may have no argument to profile
          movptr(tmp, Address(mdp, in_bytes(TypeEntriesAtCall::cell_count_offset())-off_to_args));
          subl(tmp, i*TypeStackSlotEntries::per_arg_count());
          cmpl(tmp, TypeStackSlotEntries::per_arg_count());
          jcc(Assembler::less, done);
        }
        movptr(tmp, Address(callee, Method::const_offset()));
        load_unsigned_short(tmp, Address(tmp, ConstMethod::size_of_parameters_offset()));
        // stack offset o (zero based) from the start of the argument
        // list, for n arguments translates into offset n - o - 1 from
        // the end of the argument list
        subptr(tmp, Address(mdp, in_bytes(TypeEntriesAtCall::stack_slot_offset(i))-off_to_args));
        subl(tmp, 1);
        Address arg_addr = argument_address(tmp);
        movptr(tmp, arg_addr);

        Address mdo_arg_addr(mdp, in_bytes(TypeEntriesAtCall::argument_type_offset(i))-off_to_args);
        profile_obj_type(tmp, mdo_arg_addr);

        int to_add = in_bytes(TypeStackSlotEntries::per_arg_size());
        addptr(mdp, to_add);
        off_to_args += to_add;
      }

      if (MethodData::profile_return()) {
        movptr(tmp, Address(mdp, in_bytes(TypeEntriesAtCall::cell_count_offset())-off_to_args));
        subl(tmp, TypeProfileArgsLimit*TypeStackSlotEntries::per_arg_count());
      }

      bind(done);

      if (MethodData::profile_return()) {
        // We're right after the type profile for the last
        // argument. tmp is the number of cells left in the
        // CallTypeData/VirtualCallTypeData to reach its end. Non null
        // if there's a return to profile.
        assert(ReturnTypeEntry::static_cell_count() < TypeStackSlotEntries::per_arg_count(), "can't move past ret type");
        shll(tmp, exact_log2(DataLayout::cell_size));
        addptr(mdp, tmp);
      }
      movptr(Address(rbp, frame::interpreter_frame_mdx_offset * wordSize), mdp);
    } else {
      assert(MethodData::profile_return(), "either profile call args or call ret");
      update_mdp_by_constant(mdp, in_bytes(ReturnTypeEntry::size()));
    }

    // mdp points right after the end of the
    // CallTypeData/VirtualCallTypeData, right after the cells for the
    // return value type if there's one

    bind(profile_continue);
  }
}

void InterpreterMacroAssembler::profile_return_type(Register mdp, Register ret, Register tmp) {
  assert_different_registers(mdp, ret, tmp, _bcp_register);
  if (ProfileInterpreter && MethodData::profile_return()) {
    Label profile_continue, done;

    test_method_data_pointer(mdp, profile_continue);

    if (MethodData::profile_return_jsr292_only()) {
      // If we don't profile all invoke bytecodes we must make sure
      // it's a bytecode we indeed profile. We can't go back to the
      // begining of the ProfileData we intend to update to check its
      // type because we're right after it and we don't known its
      // length
      Label do_profile;
      cmpb(Address(_bcp_register, 0), Bytecodes::_invokedynamic);
      jcc(Assembler::equal, do_profile);
      cmpb(Address(_bcp_register, 0), Bytecodes::_invokehandle);
      jcc(Assembler::equal, do_profile);
      get_method(tmp);
      cmpb(Address(tmp, Method::intrinsic_id_offset_in_bytes()), vmIntrinsics::_compiledLambdaForm);
      jcc(Assembler::notEqual, profile_continue);

      bind(do_profile);
    }

    Address mdo_ret_addr(mdp, -in_bytes(ReturnTypeEntry::size()));
    mov(tmp, ret);
    profile_obj_type(tmp, mdo_ret_addr);

    bind(profile_continue);
  }
}

void InterpreterMacroAssembler::profile_parameters_type(Register mdp, Register tmp1, Register tmp2) {
  if (ProfileInterpreter && MethodData::profile_parameters()) {
    Label profile_continue, done;

    test_method_data_pointer(mdp, profile_continue);

    // Load the offset of the area within the MDO used for
    // parameters. If it's negative we're not profiling any parameters
    movl(tmp1, Address(mdp, in_bytes(MethodData::parameters_type_data_di_offset()) - in_bytes(MethodData::data_offset())));
    testl(tmp1, tmp1);
    jcc(Assembler::negative, profile_continue);

    // Compute a pointer to the area for parameters from the offset
    // and move the pointer to the slot for the last
    // parameters. Collect profiling from last parameter down.
    // mdo start + parameters offset + array length - 1
    addptr(mdp, tmp1);
    movptr(tmp1, Address(mdp, ArrayData::array_len_offset()));
    decrement(tmp1, TypeStackSlotEntries::per_arg_count());

    Label loop;
    bind(loop);

    int off_base = in_bytes(ParametersTypeData::stack_slot_offset(0));
    int type_base = in_bytes(ParametersTypeData::type_offset(0));
    Address::ScaleFactor per_arg_scale = Address::times(DataLayout::cell_size);
    Address arg_off(mdp, tmp1, per_arg_scale, off_base);
    Address arg_type(mdp, tmp1, per_arg_scale, type_base);

    // load offset on the stack from the slot for this parameter
    movptr(tmp2, arg_off);
    negptr(tmp2);
    // read the parameter from the local area
    movptr(tmp2, Address(_locals_register, tmp2, Interpreter::stackElementScale()));

    // profile the parameter
    profile_obj_type(tmp2, arg_type);

    // go to next parameter
    decrement(tmp1, TypeStackSlotEntries::per_arg_count());
    jcc(Assembler::positive, loop);

    bind(profile_continue);
  }
}
#endif
