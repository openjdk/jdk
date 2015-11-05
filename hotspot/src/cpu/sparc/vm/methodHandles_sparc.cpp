/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/javaClasses.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "interpreter/interp_masm.hpp"
#include "memory/allocation.inline.hpp"
#include "prims/methodHandles.hpp"

#define __ _masm->

#ifdef PRODUCT
#define BLOCK_COMMENT(str) /* nothing */
#define STOP(error) stop(error)
#else
#define BLOCK_COMMENT(str) __ block_comment(str)
#define STOP(error) block_comment(error); __ stop(error)
#endif

#define BIND(label) bind(label); BLOCK_COMMENT(#label ":")

// Workaround for C++ overloading nastiness on '0' for RegisterOrConstant.
static RegisterOrConstant constant(int value) {
  return RegisterOrConstant(value);
}

void MethodHandles::load_klass_from_Class(MacroAssembler* _masm, Register klass_reg, Register temp_reg, Register temp2_reg) {
  if (VerifyMethodHandles)
    verify_klass(_masm, klass_reg, SystemDictionary::WK_KLASS_ENUM_NAME(java_lang_Class), temp_reg, temp2_reg,
                 "MH argument is a Class");
  __ ld_ptr(Address(klass_reg, java_lang_Class::klass_offset_in_bytes()), klass_reg);
}

#ifdef ASSERT
static int check_nonzero(const char* xname, int x) {
  assert(x != 0, "%s should be nonzero", xname);
  return x;
}
#define NONZERO(x) check_nonzero(#x, x)
#else //ASSERT
#define NONZERO(x) (x)
#endif //ASSERT

#ifdef ASSERT
void MethodHandles::verify_klass(MacroAssembler* _masm,
                                 Register obj_reg, SystemDictionary::WKID klass_id,
                                 Register temp_reg, Register temp2_reg,
                                 const char* error_message) {
  Klass** klass_addr = SystemDictionary::well_known_klass_addr(klass_id);
  KlassHandle klass = SystemDictionary::well_known_klass(klass_id);
  bool did_save = false;
  if (temp_reg == noreg || temp2_reg == noreg) {
    temp_reg = L1;
    temp2_reg = L2;
    __ save_frame_and_mov(0, obj_reg, L0);
    obj_reg = L0;
    did_save = true;
  }
  Label L_ok, L_bad;
  BLOCK_COMMENT("verify_klass {");
  __ verify_oop(obj_reg);
  __ br_null_short(obj_reg, Assembler::pn, L_bad);
  __ load_klass(obj_reg, temp_reg);
  __ set(ExternalAddress((Metadata**)klass_addr), temp2_reg);
  __ ld_ptr(Address(temp2_reg, 0), temp2_reg);
  __ cmp_and_brx_short(temp_reg, temp2_reg, Assembler::equal, Assembler::pt, L_ok);
  intptr_t super_check_offset = klass->super_check_offset();
  __ ld_ptr(Address(temp_reg, super_check_offset), temp_reg);
  __ set(ExternalAddress((Metadata**)klass_addr), temp2_reg);
  __ ld_ptr(Address(temp2_reg, 0), temp2_reg);
  __ cmp_and_brx_short(temp_reg, temp2_reg, Assembler::equal, Assembler::pt, L_ok);
  __ BIND(L_bad);
  if (did_save)  __ restore();
  __ STOP(error_message);
  __ BIND(L_ok);
  if (did_save)  __ restore();
  BLOCK_COMMENT("} verify_klass");
}

void MethodHandles::verify_ref_kind(MacroAssembler* _masm, int ref_kind, Register member_reg, Register temp) {
  Label L;
  BLOCK_COMMENT("verify_ref_kind {");
  __ lduw(Address(member_reg, NONZERO(java_lang_invoke_MemberName::flags_offset_in_bytes())), temp);
  __ srl( temp, java_lang_invoke_MemberName::MN_REFERENCE_KIND_SHIFT, temp);
  __ and3(temp, java_lang_invoke_MemberName::MN_REFERENCE_KIND_MASK,  temp);
  __ cmp_and_br_short(temp, ref_kind, Assembler::equal, Assembler::pt, L);
  { char* buf = NEW_C_HEAP_ARRAY(char, 100, mtInternal);
    jio_snprintf(buf, 100, "verify_ref_kind expected %x", ref_kind);
    if (ref_kind == JVM_REF_invokeVirtual ||
        ref_kind == JVM_REF_invokeSpecial)
      // could do this for all ref_kinds, but would explode assembly code size
      trace_method_handle(_masm, buf);
    __ STOP(buf);
  }
  BLOCK_COMMENT("} verify_ref_kind");
  __ bind(L);
}

#endif // ASSERT

void MethodHandles::jump_from_method_handle(MacroAssembler* _masm, Register method, Register target, Register temp,
                                            bool for_compiler_entry) {
  Label L_no_such_method;
  assert(method == G5_method, "interpreter calling convention");
  assert_different_registers(method, target, temp);

  if (!for_compiler_entry && JvmtiExport::can_post_interpreter_events()) {
    Label run_compiled_code;
    // JVMTI events, such as single-stepping, are implemented partly by avoiding running
    // compiled code in threads for which the event is enabled.  Check here for
    // interp_only_mode if these events CAN be enabled.
    __ verify_thread();
    const Address interp_only(G2_thread, JavaThread::interp_only_mode_offset());
    __ ld(interp_only, temp);
    __ cmp_and_br_short(temp, 0, Assembler::zero, Assembler::pt, run_compiled_code);
    // Null method test is replicated below in compiled case,
    // it might be able to address across the verify_thread()
    __ br_null_short(G5_method, Assembler::pn, L_no_such_method);
    __ ld_ptr(G5_method, in_bytes(Method::interpreter_entry_offset()), target);
    __ jmp(target, 0);
    __ delayed()->nop();
    __ BIND(run_compiled_code);
    // Note: we could fill some delay slots here, but
    // it doesn't matter, since this is interpreter code.
  }

  // Compiled case, either static or fall-through from runtime conditional
  __ br_null_short(G5_method, Assembler::pn, L_no_such_method);

  const ByteSize entry_offset = for_compiler_entry ? Method::from_compiled_offset() :
                                                     Method::from_interpreted_offset();
  __ ld_ptr(G5_method, in_bytes(entry_offset), target);
  __ jmp(target, 0);
  __ delayed()->nop();

  __ bind(L_no_such_method);
  AddressLiteral ame(StubRoutines::throw_AbstractMethodError_entry());
  __ jump_to(ame, temp);
  __ delayed()->nop();
}

void MethodHandles::jump_to_lambda_form(MacroAssembler* _masm,
                                        Register recv, Register method_temp,
                                        Register temp2, Register temp3,
                                        bool for_compiler_entry) {
  BLOCK_COMMENT("jump_to_lambda_form {");
  // This is the initial entry point of a lazy method handle.
  // After type checking, it picks up the invoker from the LambdaForm.
  assert_different_registers(recv, method_temp, temp2);  // temp3 is only passed on
  assert(method_temp == G5_method, "required register for loading method");

  //NOT_PRODUCT({ FlagSetting fs(TraceMethodHandles, true); trace_method_handle(_masm, "LZMH"); });

  // Load the invoker, as MH -> MH.form -> LF.vmentry
  __ verify_oop(recv);
  __ load_heap_oop(Address(recv,        NONZERO(java_lang_invoke_MethodHandle::form_offset_in_bytes())),   method_temp);
  __ verify_oop(method_temp);
  __ load_heap_oop(Address(method_temp, NONZERO(java_lang_invoke_LambdaForm::vmentry_offset_in_bytes())),  method_temp);
  __ verify_oop(method_temp);
  // the following assumes that a Method* is normally compressed in the vmtarget field:
  __ ld_ptr(       Address(method_temp, NONZERO(java_lang_invoke_MemberName::vmtarget_offset_in_bytes())), method_temp);

  if (VerifyMethodHandles && !for_compiler_entry) {
    // make sure recv is already on stack
    __ ld_ptr(method_temp, in_bytes(Method::const_offset()), temp2);
    __ load_sized_value(Address(temp2, ConstMethod::size_of_parameters_offset()),
                        temp2,
                        sizeof(u2), /*is_signed*/ false);
    // assert(sizeof(u2) == sizeof(Method::_size_of_parameters), "");
    Label L;
    __ ld_ptr(__ argument_address(temp2, temp2, -1), temp2);
    __ cmp_and_br_short(temp2, recv, Assembler::equal, Assembler::pt, L);
    __ STOP("receiver not on stack");
    __ BIND(L);
  }

  jump_from_method_handle(_masm, method_temp, temp2, temp3, for_compiler_entry);
  BLOCK_COMMENT("} jump_to_lambda_form");
}


// Code generation
address MethodHandles::generate_method_handle_interpreter_entry(MacroAssembler* _masm,
                                                                vmIntrinsics::ID iid) {
  const bool not_for_compiler_entry = false;  // this is the interpreter entry
  assert(is_signature_polymorphic(iid), "expected invoke iid");
  if (iid == vmIntrinsics::_invokeGeneric ||
      iid == vmIntrinsics::_compiledLambdaForm) {
    // Perhaps surprisingly, the symbolic references visible to Java are not directly used.
    // They are linked to Java-generated adapters via MethodHandleNatives.linkMethod.
    // They all allow an appendix argument.
    __ should_not_reach_here();           // empty stubs make SG sick
    return NULL;
  }

  // I5_savedSP/O5_savedSP: sender SP (must preserve; see prepare_to_jump_from_interpreted)
  // G5_method:  Method*
  // G4 (Gargs): incoming argument list (must preserve)
  // O0: used as temp to hold mh or receiver
  // O1, O4: garbage temps, blown away
  Register O1_scratch    = O1;
  Register O4_param_size = O4;   // size of parameters

  // here's where control starts out:
  __ align(CodeEntryAlignment);
  address entry_point = __ pc();

  if (VerifyMethodHandles) {
    assert(Method::intrinsic_id_size_in_bytes() == 2, "assuming Method::_intrinsic_id is u2");

    Label L;
    BLOCK_COMMENT("verify_intrinsic_id {");
    __ lduh(Address(G5_method, Method::intrinsic_id_offset_in_bytes()), O1_scratch);
    __ cmp_and_br_short(O1_scratch, (int) iid, Assembler::equal, Assembler::pt, L);
    if (iid == vmIntrinsics::_linkToVirtual ||
        iid == vmIntrinsics::_linkToSpecial) {
      // could do this for all kinds, but would explode assembly code size
      trace_method_handle(_masm, "bad Method*::intrinsic_id");
    }
    __ STOP("bad Method*::intrinsic_id");
    __ bind(L);
    BLOCK_COMMENT("} verify_intrinsic_id");
  }

  // First task:  Find out how big the argument list is.
  Address O4_first_arg_addr;
  int ref_kind = signature_polymorphic_intrinsic_ref_kind(iid);
  assert(ref_kind != 0 || iid == vmIntrinsics::_invokeBasic, "must be _invokeBasic or a linkTo intrinsic");
  if (ref_kind == 0 || MethodHandles::ref_kind_has_receiver(ref_kind)) {
    __ ld_ptr(G5_method, in_bytes(Method::const_offset()), O4_param_size);
    __ load_sized_value(Address(O4_param_size, ConstMethod::size_of_parameters_offset()),
                        O4_param_size,
                        sizeof(u2), /*is_signed*/ false);
    // assert(sizeof(u2) == sizeof(Method::_size_of_parameters), "");
    O4_first_arg_addr = __ argument_address(O4_param_size, O4_param_size, -1);
  } else {
    DEBUG_ONLY(O4_param_size = noreg);
  }

  Register O0_mh = noreg;
  if (!is_signature_polymorphic_static(iid)) {
    __ ld_ptr(O4_first_arg_addr, O0_mh = O0);
    DEBUG_ONLY(O4_param_size = noreg);
  }

  // O4_first_arg_addr is live!

  if (TraceMethodHandles) {
    if (O0_mh != noreg)
      __ mov(O0_mh, G3_method_handle);  // make stub happy
    trace_method_handle_interpreter_entry(_masm, iid);
  }

  if (iid == vmIntrinsics::_invokeBasic) {
    generate_method_handle_dispatch(_masm, iid, O0_mh, noreg, not_for_compiler_entry);

  } else {
    // Adjust argument list by popping the trailing MemberName argument.
    Register O0_recv = noreg;
    if (MethodHandles::ref_kind_has_receiver(ref_kind)) {
      // Load the receiver (not the MH; the actual MemberName's receiver) up from the interpreter stack.
      __ ld_ptr(O4_first_arg_addr, O0_recv = O0);
      DEBUG_ONLY(O4_param_size = noreg);
    }
    Register G5_member = G5_method;  // MemberName ptr; incoming method ptr is dead now
    __ ld_ptr(__ argument_address(constant(0)), G5_member);
    __ add(Gargs, Interpreter::stackElementSize, Gargs);
    generate_method_handle_dispatch(_masm, iid, O0_recv, G5_member, not_for_compiler_entry);
  }

  return entry_point;
}

void MethodHandles::generate_method_handle_dispatch(MacroAssembler* _masm,
                                                    vmIntrinsics::ID iid,
                                                    Register receiver_reg,
                                                    Register member_reg,
                                                    bool for_compiler_entry) {
  assert(is_signature_polymorphic(iid), "expected invoke iid");
  Register temp1 = (for_compiler_entry ? G1_scratch : O1);
  Register temp2 = (for_compiler_entry ? G3_scratch : O2);
  Register temp3 = (for_compiler_entry ? G4_scratch : O3);
  Register temp4 = (for_compiler_entry ? noreg      : O4);
  if (for_compiler_entry) {
    assert(receiver_reg == (iid == vmIntrinsics::_linkToStatic ? noreg : O0), "only valid assignment");
    assert_different_registers(temp1, O0, O1, O2, O3, O4, O5);
    assert_different_registers(temp2, O0, O1, O2, O3, O4, O5);
    assert_different_registers(temp3, O0, O1, O2, O3, O4, O5);
    assert_different_registers(temp4, O0, O1, O2, O3, O4, O5);
  } else {
    assert_different_registers(temp1, temp2, temp3, temp4, O5_savedSP);  // don't trash lastSP
  }
  if (receiver_reg != noreg)  assert_different_registers(temp1, temp2, temp3, temp4, receiver_reg);
  if (member_reg   != noreg)  assert_different_registers(temp1, temp2, temp3, temp4, member_reg);

  if (iid == vmIntrinsics::_invokeBasic) {
    // indirect through MH.form.vmentry.vmtarget
    jump_to_lambda_form(_masm, receiver_reg, G5_method, temp1, temp2, for_compiler_entry);

  } else {
    // The method is a member invoker used by direct method handles.
    if (VerifyMethodHandles) {
      // make sure the trailing argument really is a MemberName (caller responsibility)
      verify_klass(_masm, member_reg, SystemDictionary::WK_KLASS_ENUM_NAME(MemberName_klass),
                   temp1, temp2,
                   "MemberName required for invokeVirtual etc.");
    }

    Address member_clazz(    member_reg, NONZERO(java_lang_invoke_MemberName::clazz_offset_in_bytes()));
    Address member_vmindex(  member_reg, NONZERO(java_lang_invoke_MemberName::vmindex_offset_in_bytes()));
    Address member_vmtarget( member_reg, NONZERO(java_lang_invoke_MemberName::vmtarget_offset_in_bytes()));

    Register temp1_recv_klass = temp1;
    if (iid != vmIntrinsics::_linkToStatic) {
      __ verify_oop(receiver_reg);
      if (iid == vmIntrinsics::_linkToSpecial) {
        // Don't actually load the klass; just null-check the receiver.
        __ null_check(receiver_reg);
      } else {
        // load receiver klass itself
        __ null_check(receiver_reg, oopDesc::klass_offset_in_bytes());
        __ load_klass(receiver_reg, temp1_recv_klass);
        __ verify_klass_ptr(temp1_recv_klass);
      }
      BLOCK_COMMENT("check_receiver {");
      // The receiver for the MemberName must be in receiver_reg.
      // Check the receiver against the MemberName.clazz
      if (VerifyMethodHandles && iid == vmIntrinsics::_linkToSpecial) {
        // Did not load it above...
        __ load_klass(receiver_reg, temp1_recv_klass);
        __ verify_klass_ptr(temp1_recv_klass);
      }
      if (VerifyMethodHandles && iid != vmIntrinsics::_linkToInterface) {
        Label L_ok;
        Register temp2_defc = temp2;
        __ load_heap_oop(member_clazz, temp2_defc);
        load_klass_from_Class(_masm, temp2_defc, temp3, temp4);
        __ verify_klass_ptr(temp2_defc);
        __ check_klass_subtype(temp1_recv_klass, temp2_defc, temp3, temp4, L_ok);
        // If we get here, the type check failed!
        __ STOP("receiver class disagrees with MemberName.clazz");
        __ bind(L_ok);
      }
      BLOCK_COMMENT("} check_receiver");
    }
    if (iid == vmIntrinsics::_linkToSpecial ||
        iid == vmIntrinsics::_linkToStatic) {
      DEBUG_ONLY(temp1_recv_klass = noreg);  // these guys didn't load the recv_klass
    }

    // Live registers at this point:
    //  member_reg - MemberName that was the trailing argument
    //  temp1_recv_klass - klass of stacked receiver, if needed
    //  O5_savedSP - interpreter linkage (if interpreted)
    //  O0..O5 - compiler arguments (if compiled)

    Label L_incompatible_class_change_error;
    switch (iid) {
    case vmIntrinsics::_linkToSpecial:
      if (VerifyMethodHandles) {
        verify_ref_kind(_masm, JVM_REF_invokeSpecial, member_reg, temp2);
      }
      __ ld_ptr(member_vmtarget, G5_method);
      break;

    case vmIntrinsics::_linkToStatic:
      if (VerifyMethodHandles) {
        verify_ref_kind(_masm, JVM_REF_invokeStatic, member_reg, temp2);
      }
      __ ld_ptr(member_vmtarget, G5_method);
      break;

    case vmIntrinsics::_linkToVirtual:
    {
      // same as TemplateTable::invokevirtual,
      // minus the CP setup and profiling:

      if (VerifyMethodHandles) {
        verify_ref_kind(_masm, JVM_REF_invokeVirtual, member_reg, temp2);
      }

      // pick out the vtable index from the MemberName, and then we can discard it:
      Register temp2_index = temp2;
      __ ld_ptr(member_vmindex, temp2_index);

      if (VerifyMethodHandles) {
        Label L_index_ok;
        __ cmp_and_br_short(temp2_index, (int) 0, Assembler::greaterEqual, Assembler::pn, L_index_ok);
        __ STOP("no virtual index");
        __ BIND(L_index_ok);
      }

      // Note:  The verifier invariants allow us to ignore MemberName.clazz and vmtarget
      // at this point.  And VerifyMethodHandles has already checked clazz, if needed.

      // get target Method* & entry point
      __ lookup_virtual_method(temp1_recv_klass, temp2_index, G5_method);
      break;
    }

    case vmIntrinsics::_linkToInterface:
    {
      // same as TemplateTable::invokeinterface
      // (minus the CP setup and profiling, with different argument motion)
      if (VerifyMethodHandles) {
        verify_ref_kind(_masm, JVM_REF_invokeInterface, member_reg, temp2);
      }

      Register temp2_intf = temp2;
      __ load_heap_oop(member_clazz, temp2_intf);
      load_klass_from_Class(_masm, temp2_intf, temp3, temp4);
      __ verify_klass_ptr(temp2_intf);

      Register G5_index = G5_method;
      __ ld_ptr(member_vmindex, G5_index);
      if (VerifyMethodHandles) {
        Label L;
        __ cmp_and_br_short(G5_index, 0, Assembler::greaterEqual, Assembler::pt, L);
        __ STOP("invalid vtable index for MH.invokeInterface");
        __ bind(L);
      }

      // given intf, index, and recv klass, dispatch to the implementation method
      __ lookup_interface_method(temp1_recv_klass, temp2_intf,
                                 // note: next two args must be the same:
                                 G5_index, G5_method,
                                 temp3, temp4,
                                 L_incompatible_class_change_error);
      break;
    }

    default:
      fatal("unexpected intrinsic %d: %s", iid, vmIntrinsics::name_at(iid));
      break;
    }

    // Live at this point:
    //   G5_method
    //   O5_savedSP (if interpreted)

    // After figuring out which concrete method to call, jump into it.
    // Note that this works in the interpreter with no data motion.
    // But the compiled version will require that rcx_recv be shifted out.
    __ verify_method_ptr(G5_method);
    jump_from_method_handle(_masm, G5_method, temp1, temp2, for_compiler_entry);

    if (iid == vmIntrinsics::_linkToInterface) {
      __ BIND(L_incompatible_class_change_error);
      AddressLiteral icce(StubRoutines::throw_IncompatibleClassChangeError_entry());
      __ jump_to(icce, temp1);
      __ delayed()->nop();
    }
  }
}

#ifndef PRODUCT
void trace_method_handle_stub(const char* adaptername,
                              oopDesc* mh,
                              intptr_t* saved_sp,
                              intptr_t* args,
                              intptr_t* tracing_fp) {
  bool has_mh = (strstr(adaptername, "/static") == NULL &&
                 strstr(adaptername, "linkTo") == NULL);    // static linkers don't have MH
  const char* mh_reg_name = has_mh ? "G3_mh" : "G3";
  tty->print_cr("MH %s %s=" INTPTR_FORMAT " saved_sp=" INTPTR_FORMAT " args=" INTPTR_FORMAT,
                adaptername, mh_reg_name,
                p2i(mh), p2i(saved_sp), p2i(args));

  if (Verbose) {
    // dumping last frame with frame::describe

    JavaThread* p = JavaThread::active();

    ResourceMark rm;
    PRESERVE_EXCEPTION_MARK; // may not be needed by safer and unexpensive here
    FrameValues values;

    // Note: We want to allow trace_method_handle from any call site.
    // While trace_method_handle creates a frame, it may be entered
    // without a valid return PC in O7 (e.g. not just after a call).
    // Walking that frame could lead to failures due to that invalid PC.
    // => carefully detect that frame when doing the stack walking

    // walk up to the right frame using the "tracing_fp" argument
    intptr_t* cur_sp = StubRoutines::Sparc::flush_callers_register_windows_func()();
    frame cur_frame(cur_sp, frame::unpatchable, NULL);

    while (cur_frame.fp() != (intptr_t *)(STACK_BIAS+(uintptr_t)tracing_fp)) {
      cur_frame = os::get_sender_for_C_frame(&cur_frame);
    }

    // safely create a frame and call frame::describe
    intptr_t *dump_sp = cur_frame.sender_sp();
    intptr_t *dump_fp = cur_frame.link();

    bool walkable = has_mh; // whether the traced frame shoud be walkable

    // the sender for cur_frame is the caller of trace_method_handle
    if (walkable) {
      // The previous definition of walkable may have to be refined
      // if new call sites cause the next frame constructor to start
      // failing. Alternatively, frame constructors could be
      // modified to support the current or future non walkable
      // frames (but this is more intrusive and is not considered as
      // part of this RFE, which will instead use a simpler output).
      frame dump_frame = frame(dump_sp,
                               cur_frame.sp(), // younger_sp
                               false); // no adaptation
      dump_frame.describe(values, 1);
    } else {
      // Robust dump for frames which cannot be constructed from sp/younger_sp
      // Add descriptions without building a Java frame to avoid issues
      values.describe(-1, dump_fp, "fp for #1 <not parsed, cannot trust pc>");
      values.describe(-1, dump_sp, "sp");
    }

    bool has_args = has_mh; // whether Gargs is meaningful

    // mark args, if seems valid (may not be valid for some adapters)
    if (has_args) {
      if ((args >= dump_sp) && (args < dump_fp)) {
        values.describe(-1, args, "*G4_args");
      }
    }

    // mark saved_sp, if seems valid (may not be valid for some adapters)
    intptr_t *unbiased_sp = (intptr_t *)(STACK_BIAS+(uintptr_t)saved_sp);
    const int ARG_LIMIT = 255, SLOP = 45, UNREASONABLE_STACK_MOVE = (ARG_LIMIT + SLOP);
    if ((unbiased_sp >= dump_sp - UNREASONABLE_STACK_MOVE) && (unbiased_sp < dump_fp)) {
      values.describe(-1, unbiased_sp, "*saved_sp+STACK_BIAS");
    }

    // Note: the unextended_sp may not be correct
    tty->print_cr("  stack layout:");
    values.print(p);
    if (has_mh && mh->is_oop()) {
      mh->print();
      if (java_lang_invoke_MethodHandle::is_instance(mh)) {
        if (java_lang_invoke_MethodHandle::form_offset_in_bytes() != 0)
          java_lang_invoke_MethodHandle::form(mh)->print();
      }
    }
  }
}

void MethodHandles::trace_method_handle(MacroAssembler* _masm, const char* adaptername) {
  if (!TraceMethodHandles)  return;
  BLOCK_COMMENT("trace_method_handle {");
  // save: Gargs, O5_savedSP
  __ save_frame(16); // need space for saving required FPU state

  __ set((intptr_t) adaptername, O0);
  __ mov(G3_method_handle, O1);
  __ mov(I5_savedSP, O2);
  __ mov(Gargs, O3);
  __ mov(I6, O4); // frame identifier for safe stack walking

  // Save scratched registers that might be needed. Robustness is more
  // important than optimizing the saves for this debug only code.

  // save FP result, valid at some call sites (adapter_opt_return_float, ...)
  Address d_save(FP, -sizeof(jdouble) + STACK_BIAS);
  __ stf(FloatRegisterImpl::D, Ftos_d, d_save);
  // Safely save all globals but G2 (handled by call_VM_leaf) and G7
  // (OS reserved).
  __ mov(G3_method_handle, L3);
  __ mov(Gargs, L4);
  __ mov(G5_method_type, L5);
  __ mov(G6, L6);
  __ mov(G1, L1);

  __ call_VM_leaf(L2 /* for G2 */, CAST_FROM_FN_PTR(address, trace_method_handle_stub));

  __ mov(L3, G3_method_handle);
  __ mov(L4, Gargs);
  __ mov(L5, G5_method_type);
  __ mov(L6, G6);
  __ mov(L1, G1);
  __ ldf(FloatRegisterImpl::D, d_save, Ftos_d);

  __ restore();
  BLOCK_COMMENT("} trace_method_handle");
}
#endif // PRODUCT
