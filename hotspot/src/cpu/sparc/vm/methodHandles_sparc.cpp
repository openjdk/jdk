/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

// stack walking support

frame MethodHandles::ricochet_frame_sender(const frame& fr, RegisterMap *map) {
  //RicochetFrame* f = RicochetFrame::from_frame(fr);
  // Cf. is_interpreted_frame path of frame::sender
  intptr_t* younger_sp = fr.sp();
  intptr_t* sp         = fr.sender_sp();
  map->make_integer_regs_unsaved();
  map->shift_window(sp, younger_sp);
  bool this_frame_adjusted_stack = true;  // I5_savedSP is live in this RF
  return frame(sp, younger_sp, this_frame_adjusted_stack);
}

void MethodHandles::ricochet_frame_oops_do(const frame& fr, OopClosure* blk, const RegisterMap* reg_map) {
  ResourceMark rm;
  RicochetFrame* f = RicochetFrame::from_frame(fr);

  // pick up the argument type descriptor:
  Thread* thread = Thread::current();
  Handle cookie(thread, f->compute_saved_args_layout(true, true));

  // process fixed part
  blk->do_oop((oop*)f->saved_target_addr());
  blk->do_oop((oop*)f->saved_args_layout_addr());

  // process variable arguments:
  if (cookie.is_null())  return;  // no arguments to describe

  // the cookie is actually the invokeExact method for my target
  // his argument signature is what I'm interested in
  assert(cookie->is_method(), "");
  methodHandle invoker(thread, methodOop(cookie()));
  assert(invoker->name() == vmSymbols::invokeExact_name(), "must be this kind of method");
  assert(!invoker->is_static(), "must have MH argument");
  int slot_count = invoker->size_of_parameters();
  assert(slot_count >= 1, "must include 'this'");
  intptr_t* base = f->saved_args_base();
  intptr_t* retval = NULL;
  if (f->has_return_value_slot())
    retval = f->return_value_slot_addr();
  int slot_num = slot_count - 1;
  intptr_t* loc = &base[slot_num];
  //blk->do_oop((oop*) loc);   // original target, which is irrelevant
  int arg_num = 0;
  for (SignatureStream ss(invoker->signature()); !ss.is_done(); ss.next()) {
    if (ss.at_return_type())  continue;
    BasicType ptype = ss.type();
    if (ptype == T_ARRAY)  ptype = T_OBJECT; // fold all refs to T_OBJECT
    assert(ptype >= T_BOOLEAN && ptype <= T_OBJECT, "not array or void");
    slot_num -= type2size[ptype];
    loc = &base[slot_num];
    bool is_oop = (ptype == T_OBJECT && loc != retval);
    if (is_oop)  blk->do_oop((oop*)loc);
    arg_num += 1;
  }
  assert(slot_num == 0, "must have processed all the arguments");
}

// Ricochet Frames
const Register MethodHandles::RicochetFrame::L1_continuation      = L1;
const Register MethodHandles::RicochetFrame::L2_saved_target      = L2;
const Register MethodHandles::RicochetFrame::L3_saved_args_layout = L3;
const Register MethodHandles::RicochetFrame::L4_saved_args_base   = L4; // cf. Gargs = G4
const Register MethodHandles::RicochetFrame::L5_conversion        = L5;
#ifdef ASSERT
const Register MethodHandles::RicochetFrame::L0_magic_number_1    = L0;
#endif //ASSERT

oop MethodHandles::RicochetFrame::compute_saved_args_layout(bool read_cache, bool write_cache) {
  if (read_cache) {
    oop cookie = saved_args_layout();
    if (cookie != NULL)  return cookie;
  }
  oop target = saved_target();
  oop mtype  = java_lang_invoke_MethodHandle::type(target);
  oop mtform = java_lang_invoke_MethodType::form(mtype);
  oop cookie = java_lang_invoke_MethodTypeForm::vmlayout(mtform);
  if (write_cache)  {
    (*saved_args_layout_addr()) = cookie;
  }
  return cookie;
}

void MethodHandles::RicochetFrame::generate_ricochet_blob(MacroAssembler* _masm,
                                                          // output params:
                                                          int* bounce_offset,
                                                          int* exception_offset,
                                                          int* frame_size_in_words) {
  (*frame_size_in_words) = RicochetFrame::frame_size_in_bytes() / wordSize;

  address start = __ pc();

#ifdef ASSERT
  __ illtrap(0); __ illtrap(0); __ illtrap(0);
  // here's a hint of something special:
  __ set(MAGIC_NUMBER_1, G0);
  __ set(MAGIC_NUMBER_2, G0);
#endif //ASSERT
  __ illtrap(0);  // not reached

  // Return values are in registers.
  // L1_continuation contains a cleanup continuation we must return
  // to.

  (*bounce_offset) = __ pc() - start;
  BLOCK_COMMENT("ricochet_blob.bounce");

  if (VerifyMethodHandles)  RicochetFrame::verify_clean(_masm);
  trace_method_handle(_masm, "ricochet_blob.bounce");

  __ JMP(L1_continuation, 0);
  __ delayed()->nop();
  __ illtrap(0);

  DEBUG_ONLY(__ set(MAGIC_NUMBER_2, G0));

  (*exception_offset) = __ pc() - start;
  BLOCK_COMMENT("ricochet_blob.exception");

  // compare this to Interpreter::rethrow_exception_entry, which is parallel code
  // for example, see TemplateInterpreterGenerator::generate_throw_exception
  // Live registers in:
  //   Oexception  (O0): exception
  //   Oissuing_pc (O1): return address/pc that threw exception (ignored, always equal to bounce addr)
  __ verify_oop(Oexception);

  // Take down the frame.

  // Cf. InterpreterMacroAssembler::remove_activation.
  leave_ricochet_frame(_masm, /*recv_reg=*/ noreg, I5_savedSP, I7);

  // We are done with this activation frame; find out where to go next.
  // The continuation point will be an exception handler, which expects
  // the following registers set up:
  //
  // Oexception: exception
  // Oissuing_pc: the local call that threw exception
  // Other On: garbage
  // In/Ln:  the contents of the caller's register window
  //
  // We do the required restore at the last possible moment, because we
  // need to preserve some state across a runtime call.
  // (Remember that the caller activation is unknown--it might not be
  // interpreted, so things like Lscratch are useless in the caller.)
  __ mov(Oexception,  Oexception ->after_save());  // get exception in I0 so it will be on O0 after restore
  __ add(I7, frame::pc_return_offset, Oissuing_pc->after_save());  // likewise set I1 to a value local to the caller
  __ call_VM_leaf(L7_thread_cache,
                  CAST_FROM_FN_PTR(address, SharedRuntime::exception_handler_for_return_address),
                  G2_thread, Oissuing_pc->after_save());

  // The caller's SP was adjusted upon method entry to accomodate
  // the callee's non-argument locals. Undo that adjustment.
  __ JMP(O0, 0);                         // return exception handler in caller
  __ delayed()->restore(I5_savedSP, G0, SP);

  // (same old exception object is already in Oexception; see above)
  // Note that an "issuing PC" is actually the next PC after the call
}

void MethodHandles::RicochetFrame::enter_ricochet_frame(MacroAssembler* _masm,
                                                        Register recv_reg,
                                                        Register argv_reg,
                                                        address return_handler) {
  // does not include the __ save()
  assert(argv_reg == Gargs, "");
  Address G3_mh_vmtarget(   recv_reg, java_lang_invoke_MethodHandle::vmtarget_offset_in_bytes());
  Address G3_amh_conversion(recv_reg, java_lang_invoke_AdapterMethodHandle::conversion_offset_in_bytes());

  // Create the RicochetFrame.
  // Unlike on x86 we can store all required information in local
  // registers.
  BLOCK_COMMENT("push RicochetFrame {");
  __ set(ExternalAddress(return_handler),          L1_continuation);
  __ load_heap_oop(G3_mh_vmtarget,                 L2_saved_target);
  __ mov(G0,                                       L3_saved_args_layout);
  __ mov(Gargs,                                    L4_saved_args_base);
  __ lduw(G3_amh_conversion,                       L5_conversion);  // 32-bit field
  // I5, I6, I7 are already set up
  DEBUG_ONLY(__ set((int32_t) MAGIC_NUMBER_1,      L0_magic_number_1));
  BLOCK_COMMENT("} RicochetFrame");
}

void MethodHandles::RicochetFrame::leave_ricochet_frame(MacroAssembler* _masm,
                                                        Register recv_reg,
                                                        Register new_sp_reg,
                                                        Register sender_pc_reg) {
  assert(new_sp_reg == I5_savedSP, "exact_sender_sp already in place");
  assert(sender_pc_reg == I7, "in a fixed place");
  // does not include the __ ret() & __ restore()
  assert_different_registers(recv_reg, new_sp_reg, sender_pc_reg);
  // Take down the frame.
  // Cf. InterpreterMacroAssembler::remove_activation.
  BLOCK_COMMENT("end_ricochet_frame {");
  if (recv_reg->is_valid())
    __ mov(L2_saved_target, recv_reg);
  BLOCK_COMMENT("} end_ricochet_frame");
}

// Emit code to verify that FP is pointing at a valid ricochet frame.
#ifdef ASSERT
enum {
  ARG_LIMIT = 255, SLOP = 45,
  // use this parameter for checking for garbage stack movements:
  UNREASONABLE_STACK_MOVE = (ARG_LIMIT + SLOP)
  // the slop defends against false alarms due to fencepost errors
};

void MethodHandles::RicochetFrame::verify_clean(MacroAssembler* _masm) {
  // The stack should look like this:
  //    ... keep1 | dest=42 | keep2 | magic | handler | magic | recursive args | [RF]
  // Check various invariants.

  Register O7_temp = O7, O5_temp = O5;

  Label L_ok_1, L_ok_2, L_ok_3, L_ok_4;
  BLOCK_COMMENT("verify_clean {");
  // Magic numbers must check out:
  __ set((int32_t) MAGIC_NUMBER_1, O7_temp);
  __ cmp(O7_temp, L0_magic_number_1);
  __ br(Assembler::equal, false, Assembler::pt, L_ok_1);
  __ delayed()->nop();
  __ stop("damaged ricochet frame: MAGIC_NUMBER_1 not found");

  __ BIND(L_ok_1);

  // Arguments pointer must look reasonable:
#ifdef _LP64
  Register FP_temp = O5_temp;
  __ add(FP, STACK_BIAS, FP_temp);
#else
  Register FP_temp = FP;
#endif
  __ cmp(L4_saved_args_base, FP_temp);
  __ br(Assembler::greaterEqualUnsigned, false, Assembler::pt, L_ok_2);
  __ delayed()->nop();
  __ stop("damaged ricochet frame: L4 < FP");

  __ BIND(L_ok_2);
  // Disable until we decide on it's fate
  // __ sub(L4_saved_args_base, UNREASONABLE_STACK_MOVE * Interpreter::stackElementSize, O7_temp);
  // __ cmp(O7_temp, FP_temp);
  // __ br(Assembler::lessEqualUnsigned, false, Assembler::pt, L_ok_3);
  // __ delayed()->nop();
  // __ stop("damaged ricochet frame: (L4 - UNREASONABLE_STACK_MOVE) > FP");

  __ BIND(L_ok_3);
  extract_conversion_dest_type(_masm, L5_conversion, O7_temp);
  __ cmp(O7_temp, T_VOID);
  __ br(Assembler::equal, false, Assembler::pt, L_ok_4);
  __ delayed()->nop();
  extract_conversion_vminfo(_masm, L5_conversion, O5_temp);
  __ ld_ptr(L4_saved_args_base, __ argument_offset(O5_temp, O5_temp), O7_temp);
  assert(__ is_simm13(RETURN_VALUE_PLACEHOLDER), "must be simm13");
  __ cmp(O7_temp, (int32_t) RETURN_VALUE_PLACEHOLDER);
  __ brx(Assembler::equal, false, Assembler::pt, L_ok_4);
  __ delayed()->nop();
  __ stop("damaged ricochet frame: RETURN_VALUE_PLACEHOLDER not found");
  __ BIND(L_ok_4);
  BLOCK_COMMENT("} verify_clean");
}
#endif //ASSERT

void MethodHandles::load_klass_from_Class(MacroAssembler* _masm, Register klass_reg, Register temp_reg, Register temp2_reg) {
  if (VerifyMethodHandles)
    verify_klass(_masm, klass_reg, SystemDictionaryHandles::Class_klass(), temp_reg, temp2_reg,
                 "AMH argument is a Class");
  __ load_heap_oop(Address(klass_reg, java_lang_Class::klass_offset_in_bytes()), klass_reg);
}

void MethodHandles::load_conversion_vminfo(MacroAssembler* _masm, Address conversion_field_addr, Register reg) {
  assert(CONV_VMINFO_SHIFT == 0, "preshifted");
  assert(CONV_VMINFO_MASK == right_n_bits(BitsPerByte), "else change type of following load");
  __ ldub(conversion_field_addr.plus_disp(BytesPerInt - 1), reg);
}

void MethodHandles::extract_conversion_vminfo(MacroAssembler* _masm, Register conversion_field_reg, Register reg) {
  assert(CONV_VMINFO_SHIFT == 0, "preshifted");
  __ and3(conversion_field_reg, CONV_VMINFO_MASK, reg);
}

void MethodHandles::extract_conversion_dest_type(MacroAssembler* _masm, Register conversion_field_reg, Register reg) {
  __ srl(conversion_field_reg, CONV_DEST_TYPE_SHIFT, reg);
  __ and3(reg, 0x0F, reg);
}

void MethodHandles::load_stack_move(MacroAssembler* _masm,
                                    Address G3_amh_conversion,
                                    Register stack_move_reg) {
  BLOCK_COMMENT("load_stack_move {");
  __ ldsw(G3_amh_conversion, stack_move_reg);
  __ sra(stack_move_reg, CONV_STACK_MOVE_SHIFT, stack_move_reg);
  if (VerifyMethodHandles) {
    Label L_ok, L_bad;
    int32_t stack_move_limit = 0x0800;  // extra-large
    __ cmp(stack_move_reg, stack_move_limit);
    __ br(Assembler::greaterEqual, false, Assembler::pn, L_bad);
    __ delayed()->nop();
    __ cmp(stack_move_reg, -stack_move_limit);
    __ br(Assembler::greater, false, Assembler::pt, L_ok);
    __ delayed()->nop();
    __ BIND(L_bad);
    __ stop("load_stack_move of garbage value");
    __ BIND(L_ok);
  }
  BLOCK_COMMENT("} load_stack_move");
}

#ifdef ASSERT
void MethodHandles::RicochetFrame::verify() const {
  assert(magic_number_1() == MAGIC_NUMBER_1, "");
  if (!Universe::heap()->is_gc_active()) {
    if (saved_args_layout() != NULL) {
      assert(saved_args_layout()->is_method(), "must be valid oop");
    }
    if (saved_target() != NULL) {
      assert(java_lang_invoke_MethodHandle::is_instance(saved_target()), "checking frame value");
    }
  }
  int conv_op = adapter_conversion_op(conversion());
  assert(conv_op == java_lang_invoke_AdapterMethodHandle::OP_COLLECT_ARGS ||
         conv_op == java_lang_invoke_AdapterMethodHandle::OP_FOLD_ARGS ||
         conv_op == java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_REF,
         "must be a sane conversion");
  if (has_return_value_slot()) {
    assert(*return_value_slot_addr() == RETURN_VALUE_PLACEHOLDER, "");
  }
}

void MethodHandles::verify_argslot(MacroAssembler* _masm, Register argslot_reg, Register temp_reg, const char* error_message) {
  // Verify that argslot lies within (Gargs, FP].
  Label L_ok, L_bad;
  BLOCK_COMMENT("verify_argslot {");
  __ add(FP, STACK_BIAS, temp_reg);  // STACK_BIAS is zero on !_LP64
  __ cmp(argslot_reg, temp_reg);
  __ brx(Assembler::greaterUnsigned, false, Assembler::pn, L_bad);
  __ delayed()->nop();
  __ cmp(Gargs, argslot_reg);
  __ brx(Assembler::lessEqualUnsigned, false, Assembler::pt, L_ok);
  __ delayed()->nop();
  __ BIND(L_bad);
  __ stop(error_message);
  __ BIND(L_ok);
  BLOCK_COMMENT("} verify_argslot");
}

void MethodHandles::verify_argslots(MacroAssembler* _masm,
                                    RegisterOrConstant arg_slots,
                                    Register arg_slot_base_reg,
                                    Register temp_reg,
                                    Register temp2_reg,
                                    bool negate_argslots,
                                    const char* error_message) {
  // Verify that [argslot..argslot+size) lies within (Gargs, FP).
  Label L_ok, L_bad;
  BLOCK_COMMENT("verify_argslots {");
  if (negate_argslots) {
    if (arg_slots.is_constant()) {
      arg_slots = -1 * arg_slots.as_constant();
    } else {
      __ neg(arg_slots.as_register(), temp_reg);
      arg_slots = temp_reg;
    }
  }
  __ add(arg_slot_base_reg, __ argument_offset(arg_slots, temp_reg), temp_reg);
  __ add(FP, STACK_BIAS, temp2_reg);  // STACK_BIAS is zero on !_LP64
  __ cmp(temp_reg, temp2_reg);
  __ brx(Assembler::greaterUnsigned, false, Assembler::pn, L_bad);
  __ delayed()->nop();
  // Gargs points to the first word so adjust by BytesPerWord
  __ add(arg_slot_base_reg, BytesPerWord, temp_reg);
  __ cmp(Gargs, temp_reg);
  __ brx(Assembler::lessEqualUnsigned, false, Assembler::pt, L_ok);
  __ delayed()->nop();
  __ BIND(L_bad);
  __ stop(error_message);
  __ BIND(L_ok);
  BLOCK_COMMENT("} verify_argslots");
}

// Make sure that arg_slots has the same sign as the given direction.
// If (and only if) arg_slots is a assembly-time constant, also allow it to be zero.
void MethodHandles::verify_stack_move(MacroAssembler* _masm,
                                      RegisterOrConstant arg_slots, int direction) {
  enum { UNREASONABLE_STACK_MOVE = 256 * 4 };  // limit of 255 arguments
  bool allow_zero = arg_slots.is_constant();
  if (direction == 0) { direction = +1; allow_zero = true; }
  assert(stack_move_unit() == -1, "else add extra checks here");
  if (arg_slots.is_register()) {
    Label L_ok, L_bad;
    BLOCK_COMMENT("verify_stack_move {");
    // __ btst(-stack_move_unit() - 1, arg_slots.as_register());  // no need
    // __ br(Assembler::notZero, false, Assembler::pn, L_bad);
    // __ delayed()->nop();
    __ cmp(arg_slots.as_register(), (int32_t) NULL_WORD);
    if (direction > 0) {
      __ br(allow_zero ? Assembler::less : Assembler::lessEqual, false, Assembler::pn, L_bad);
      __ delayed()->nop();
      __ cmp(arg_slots.as_register(), (int32_t) UNREASONABLE_STACK_MOVE);
      __ br(Assembler::less, false, Assembler::pn, L_ok);
      __ delayed()->nop();
    } else {
      __ br(allow_zero ? Assembler::greater : Assembler::greaterEqual, false, Assembler::pn, L_bad);
      __ delayed()->nop();
      __ cmp(arg_slots.as_register(), (int32_t) -UNREASONABLE_STACK_MOVE);
      __ br(Assembler::greater, false, Assembler::pn, L_ok);
      __ delayed()->nop();
    }
    __ BIND(L_bad);
    if (direction > 0)
      __ stop("assert arg_slots > 0");
    else
      __ stop("assert arg_slots < 0");
    __ BIND(L_ok);
    BLOCK_COMMENT("} verify_stack_move");
  } else {
    intptr_t size = arg_slots.as_constant();
    if (direction < 0)  size = -size;
    assert(size >= 0, "correct direction of constant move");
    assert(size < UNREASONABLE_STACK_MOVE, "reasonable size of constant move");
  }
}

void MethodHandles::verify_klass(MacroAssembler* _masm,
                                 Register obj_reg, KlassHandle klass,
                                 Register temp_reg, Register temp2_reg,
                                 const char* error_message) {
  oop* klass_addr = klass.raw_value();
  assert(klass_addr >= SystemDictionaryHandles::Object_klass().raw_value() &&
         klass_addr <= SystemDictionaryHandles::Long_klass().raw_value(),
         "must be one of the SystemDictionaryHandles");
  Label L_ok, L_bad;
  BLOCK_COMMENT("verify_klass {");
  __ verify_oop(obj_reg);
  __ br_null(obj_reg, false, Assembler::pn, L_bad);
  __ delayed()->nop();
  __ load_klass(obj_reg, temp_reg);
  __ set(ExternalAddress(klass_addr), temp2_reg);
  __ ld_ptr(Address(temp2_reg, 0), temp2_reg);
  __ cmp(temp_reg, temp2_reg);
  __ brx(Assembler::equal, false, Assembler::pt, L_ok);
  __ delayed()->nop();
  intptr_t super_check_offset = klass->super_check_offset();
  __ ld_ptr(Address(temp_reg, super_check_offset), temp_reg);
  __ set(ExternalAddress(klass_addr), temp2_reg);
  __ ld_ptr(Address(temp2_reg, 0), temp2_reg);
  __ cmp(temp_reg, temp2_reg);
  __ brx(Assembler::equal, false, Assembler::pt, L_ok);
  __ delayed()->nop();
  __ BIND(L_bad);
  __ stop(error_message);
  __ BIND(L_ok);
  BLOCK_COMMENT("} verify_klass");
}
#endif // ASSERT


void MethodHandles::jump_from_method_handle(MacroAssembler* _masm, Register method, Register target, Register temp) {
  assert(method == G5_method, "interpreter calling convention");
  __ verify_oop(method);
  __ ld_ptr(G5_method, in_bytes(methodOopDesc::from_interpreted_offset()), target);
  if (JvmtiExport::can_post_interpreter_events()) {
    // JVMTI events, such as single-stepping, are implemented partly by avoiding running
    // compiled code in threads for which the event is enabled.  Check here for
    // interp_only_mode if these events CAN be enabled.
    __ verify_thread();
    Label skip_compiled_code;

    const Address interp_only(G2_thread, JavaThread::interp_only_mode_offset());
    __ ld(interp_only, temp);
    __ tst(temp);
    __ br(Assembler::notZero, true, Assembler::pn, skip_compiled_code);
    __ delayed()->ld_ptr(G5_method, in_bytes(methodOopDesc::interpreter_entry_offset()), target);
    __ bind(skip_compiled_code);
  }
  __ jmp(target, 0);
  __ delayed()->nop();
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
  __ mov(G3_method_handle, G3_method_handle);  // already in this register
  // O0 will be filled in with JavaThread in stub
  __ jump_to(AddressLiteral(StubRoutines::throw_WrongMethodTypeException_entry()), O3_scratch);
  __ delayed()->nop();

  // here's where control starts out:
  __ align(CodeEntryAlignment);
  address entry_point = __ pc();

  // fetch the MethodType from the method handle
  // FIXME: Interpreter should transmit pre-popped stack pointer, to locate base of arg list.
  // This would simplify several touchy bits of code.
  // See 6984712: JSR 292 method handle calls need a clean argument base pointer
  {
    Register tem = G5_method;
    for (jint* pchase = methodOopDesc::method_type_offsets_chain(); (*pchase) != -1; pchase++) {
      __ ld_ptr(Address(tem, *pchase), O0_mtype);
      tem = O0_mtype;          // in case there is another indirection
    }
  }

  // given the MethodType, find out where the MH argument is buried
  __ load_heap_oop(Address(O0_mtype,   __ delayed_value(java_lang_invoke_MethodType::form_offset_in_bytes,        O1_scratch)), O4_argslot);
  __ ldsw(         Address(O4_argslot, __ delayed_value(java_lang_invoke_MethodTypeForm::vmslots_offset_in_bytes, O1_scratch)), O4_argslot);
  __ add(__ argument_address(O4_argslot, O4_argslot, 1), O4_argbase);
  // Note: argument_address uses its input as a scratch register!
  Address mh_receiver_slot_addr(O4_argbase, -Interpreter::stackElementSize);
  __ ld_ptr(mh_receiver_slot_addr, G3_method_handle);

  trace_method_handle(_masm, "invokeExact");

  __ check_method_handle_type(O0_mtype, G3_method_handle, O1_scratch, wrong_method_type);

  // Nobody uses the MH receiver slot after this.  Make sure.
  DEBUG_ONLY(__ set((int32_t) 0x999999, O1_scratch); __ st_ptr(O1_scratch, mh_receiver_slot_addr));

  __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);

  // for invokeGeneric (only), apply argument and result conversions on the fly
  __ bind(invoke_generic_slow_path);
#ifdef ASSERT
  if (VerifyMethodHandles) {
    Label L;
    __ ldub(Address(G5_method, methodOopDesc::intrinsic_id_offset_in_bytes()), O1_scratch);
    __ cmp(O1_scratch, (int) vmIntrinsics::_invokeGeneric);
    __ brx(Assembler::equal, false, Assembler::pt, L);
    __ delayed()->nop();
    __ stop("bad methodOop::intrinsic_id");
    __ bind(L);
  }
#endif //ASSERT

  // make room on the stack for another pointer:
  insert_arg_slots(_masm, 2 * stack_move_unit(), O4_argbase, O1_scratch, O2_scratch, O3_scratch);
  // load up an adapter from the calling type (Java weaves this)
  Register O2_form    = O2_scratch;
  Register O3_adapter = O3_scratch;
  __ load_heap_oop(Address(O0_mtype, __ delayed_value(java_lang_invoke_MethodType::form_offset_in_bytes,               O1_scratch)), O2_form);
  __ load_heap_oop(Address(O2_form,  __ delayed_value(java_lang_invoke_MethodTypeForm::genericInvoker_offset_in_bytes, O1_scratch)), O3_adapter);
  __ verify_oop(O3_adapter);
  __ st_ptr(O3_adapter, Address(O4_argbase, 1 * Interpreter::stackElementSize));
  // As a trusted first argument, pass the type being called, so the adapter knows
  // the actual types of the arguments and return values.
  // (Generic invokers are shared among form-families of method-type.)
  __ st_ptr(O0_mtype,   Address(O4_argbase, 0 * Interpreter::stackElementSize));
  // FIXME: assert that O3_adapter is of the right method-type.
  __ mov(O3_adapter, G3_method_handle);
  trace_method_handle(_masm, "invokeGeneric");
  __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);

  return entry_point;
}

// Workaround for C++ overloading nastiness on '0' for RegisterOrConstant.
static RegisterOrConstant constant(int value) {
  return RegisterOrConstant(value);
}

static void load_vmargslot(MacroAssembler* _masm, Address vmargslot_addr, Register result) {
  __ ldsw(vmargslot_addr, result);
}

static RegisterOrConstant adjust_SP_and_Gargs_down_by_slots(MacroAssembler* _masm,
                                                            RegisterOrConstant arg_slots,
                                                            Register temp_reg, Register temp2_reg) {
  // Keep the stack pointer 2*wordSize aligned.
  const int TwoWordAlignmentMask = right_n_bits(LogBytesPerWord + 1);
  if (arg_slots.is_constant()) {
    const int        offset = arg_slots.as_constant() << LogBytesPerWord;
    const int masked_offset = round_to(offset, 2 * BytesPerWord);
    const int masked_offset2 = (offset + 1*BytesPerWord) & ~TwoWordAlignmentMask;
    assert(masked_offset == masked_offset2, "must agree");
    __ sub(Gargs,        offset, Gargs);
    __ sub(SP,    masked_offset, SP   );
    return offset;
  } else {
#ifdef ASSERT
    {
      Label L_ok;
      __ cmp(arg_slots.as_register(), 0);
      __ br(Assembler::greaterEqual, false, Assembler::pt, L_ok);
      __ delayed()->nop();
      __ stop("negative arg_slots");
      __ bind(L_ok);
    }
#endif
    __ sll_ptr(arg_slots.as_register(), LogBytesPerWord, temp_reg);
    __ add( temp_reg,  1*BytesPerWord,       temp2_reg);
    __ andn(temp2_reg, TwoWordAlignmentMask, temp2_reg);
    __ sub(Gargs, temp_reg,  Gargs);
    __ sub(SP,    temp2_reg, SP   );
    return temp_reg;
  }
}

static RegisterOrConstant adjust_SP_and_Gargs_up_by_slots(MacroAssembler* _masm,
                                                          RegisterOrConstant arg_slots,
                                                          Register temp_reg, Register temp2_reg) {
  // Keep the stack pointer 2*wordSize aligned.
  const int TwoWordAlignmentMask = right_n_bits(LogBytesPerWord + 1);
  if (arg_slots.is_constant()) {
    const int        offset = arg_slots.as_constant() << LogBytesPerWord;
    const int masked_offset = offset & ~TwoWordAlignmentMask;
    __ add(Gargs,        offset, Gargs);
    __ add(SP,    masked_offset, SP   );
    return offset;
  } else {
    __ sll_ptr(arg_slots.as_register(), LogBytesPerWord, temp_reg);
    __ andn(temp_reg, TwoWordAlignmentMask, temp2_reg);
    __ add(Gargs, temp_reg,  Gargs);
    __ add(SP,    temp2_reg, SP   );
    return temp_reg;
  }
}

// Helper to insert argument slots into the stack.
// arg_slots must be a multiple of stack_move_unit() and < 0
// argslot_reg is decremented to point to the new (shifted) location of the argslot
// But, temp_reg ends up holding the original value of argslot_reg.
void MethodHandles::insert_arg_slots(MacroAssembler* _masm,
                                     RegisterOrConstant arg_slots,
                                     Register argslot_reg,
                                     Register temp_reg, Register temp2_reg, Register temp3_reg) {
  // allow constant zero
  if (arg_slots.is_constant() && arg_slots.as_constant() == 0)
    return;

  assert_different_registers(argslot_reg, temp_reg, temp2_reg, temp3_reg,
                             (!arg_slots.is_register() ? Gargs : arg_slots.as_register()));

  BLOCK_COMMENT("insert_arg_slots {");
  if (VerifyMethodHandles)
    verify_argslot(_masm, argslot_reg, temp_reg, "insertion point must fall within current frame");
  if (VerifyMethodHandles)
    verify_stack_move(_masm, arg_slots, -1);

  // Make space on the stack for the inserted argument(s).
  // Then pull down everything shallower than argslot_reg.
  // The stacked return address gets pulled down with everything else.
  // That is, copy [sp, argslot) downward by -size words.  In pseudo-code:
  //   sp -= size;
  //   for (temp = sp + size; temp < argslot; temp++)
  //     temp[-size] = temp[0]
  //   argslot -= size;

  // offset is temp3_reg in case of arg_slots being a register.
  RegisterOrConstant offset = adjust_SP_and_Gargs_up_by_slots(_masm, arg_slots, temp3_reg, temp_reg);
  __ sub(Gargs, offset, temp_reg);  // source pointer for copy

  {
    Label loop;
    __ BIND(loop);
    // pull one word down each time through the loop
    __ ld_ptr(           Address(temp_reg, 0     ), temp2_reg);
    __ st_ptr(temp2_reg, Address(temp_reg, offset)           );
    __ add(temp_reg, wordSize, temp_reg);
    __ cmp(temp_reg, argslot_reg);
    __ brx(Assembler::lessUnsigned, false, Assembler::pt, loop);
    __ delayed()->nop();  // FILLME
  }

  // Now move the argslot down, to point to the opened-up space.
  __ add(argslot_reg, offset, argslot_reg);
  BLOCK_COMMENT("} insert_arg_slots");
}


// Helper to remove argument slots from the stack.
// arg_slots must be a multiple of stack_move_unit() and > 0
void MethodHandles::remove_arg_slots(MacroAssembler* _masm,
                                     RegisterOrConstant arg_slots,
                                     Register argslot_reg,
                                     Register temp_reg, Register temp2_reg, Register temp3_reg) {
  // allow constant zero
  if (arg_slots.is_constant() && arg_slots.as_constant() == 0)
    return;
  assert_different_registers(argslot_reg, temp_reg, temp2_reg, temp3_reg,
                             (!arg_slots.is_register() ? Gargs : arg_slots.as_register()));

  BLOCK_COMMENT("remove_arg_slots {");
  if (VerifyMethodHandles)
    verify_argslots(_masm, arg_slots, argslot_reg, temp_reg, temp2_reg, false,
                    "deleted argument(s) must fall within current frame");
  if (VerifyMethodHandles)
    verify_stack_move(_masm, arg_slots, +1);

  // Pull up everything shallower than argslot.
  // Then remove the excess space on the stack.
  // The stacked return address gets pulled up with everything else.
  // That is, copy [sp, argslot) upward by size words.  In pseudo-code:
  //   for (temp = argslot-1; temp >= sp; --temp)
  //     temp[size] = temp[0]
  //   argslot += size;
  //   sp += size;

  RegisterOrConstant offset = __ regcon_sll_ptr(arg_slots, LogBytesPerWord, temp3_reg);
  __ sub(argslot_reg, wordSize, temp_reg);  // source pointer for copy

  {
    Label L_loop;
    __ BIND(L_loop);
    // pull one word up each time through the loop
    __ ld_ptr(           Address(temp_reg, 0     ), temp2_reg);
    __ st_ptr(temp2_reg, Address(temp_reg, offset)           );
    __ sub(temp_reg, wordSize, temp_reg);
    __ cmp(temp_reg, Gargs);
    __ brx(Assembler::greaterEqualUnsigned, false, Assembler::pt, L_loop);
    __ delayed()->nop();  // FILLME
  }

  // And adjust the argslot address to point at the deletion point.
  __ add(argslot_reg, offset, argslot_reg);

  // We don't need the offset at this point anymore, just adjust SP and Gargs.
  (void) adjust_SP_and_Gargs_up_by_slots(_masm, arg_slots, temp3_reg, temp_reg);

  BLOCK_COMMENT("} remove_arg_slots");
}

// Helper to copy argument slots to the top of the stack.
// The sequence starts with argslot_reg and is counted by slot_count
// slot_count must be a multiple of stack_move_unit() and >= 0
// This function blows the temps but does not change argslot_reg.
void MethodHandles::push_arg_slots(MacroAssembler* _masm,
                                   Register argslot_reg,
                                   RegisterOrConstant slot_count,
                                   Register temp_reg, Register temp2_reg) {
  // allow constant zero
  if (slot_count.is_constant() && slot_count.as_constant() == 0)
    return;
  assert_different_registers(argslot_reg, temp_reg, temp2_reg,
                             (!slot_count.is_register() ? Gargs : slot_count.as_register()),
                             SP);
  assert(Interpreter::stackElementSize == wordSize, "else change this code");

  BLOCK_COMMENT("push_arg_slots {");
  if (VerifyMethodHandles)
    verify_stack_move(_masm, slot_count, 0);

  RegisterOrConstant offset = adjust_SP_and_Gargs_down_by_slots(_masm, slot_count, temp2_reg, temp_reg);

  if (slot_count.is_constant()) {
    for (int i = slot_count.as_constant() - 1; i >= 0; i--) {
      __ ld_ptr(          Address(argslot_reg, i * wordSize), temp_reg);
      __ st_ptr(temp_reg, Address(Gargs,       i * wordSize));
    }
  } else {
    Label L_plural, L_loop, L_break;
    // Emit code to dynamically check for the common cases, zero and one slot.
    __ cmp(slot_count.as_register(), (int32_t) 1);
    __ br(Assembler::greater, false, Assembler::pn, L_plural);
    __ delayed()->nop();
    __ br(Assembler::less, false, Assembler::pn, L_break);
    __ delayed()->nop();
    __ ld_ptr(          Address(argslot_reg, 0), temp_reg);
    __ st_ptr(temp_reg, Address(Gargs,       0));
    __ ba(false, L_break);
    __ delayed()->nop();  // FILLME
    __ BIND(L_plural);

    // Loop for 2 or more:
    //   top = &argslot[slot_count]
    //   while (top > argslot)  *(--Gargs) = *(--top)
    Register top_reg = temp_reg;
    __ add(argslot_reg, offset, top_reg);
    __ add(Gargs,       offset, Gargs  );  // move back up again so we can go down
    __ BIND(L_loop);
    __ sub(top_reg, wordSize, top_reg);
    __ sub(Gargs,   wordSize, Gargs  );
    __ ld_ptr(           Address(top_reg, 0), temp2_reg);
    __ st_ptr(temp2_reg, Address(Gargs,   0));
    __ cmp(top_reg, argslot_reg);
    __ brx(Assembler::greaterUnsigned, false, Assembler::pt, L_loop);
    __ delayed()->nop();  // FILLME
    __ BIND(L_break);
  }
  BLOCK_COMMENT("} push_arg_slots");
}

// in-place movement; no change to Gargs
// blows temp_reg, temp2_reg
void MethodHandles::move_arg_slots_up(MacroAssembler* _masm,
                                      Register bottom_reg,  // invariant
                                      Address  top_addr,    // can use temp_reg
                                      RegisterOrConstant positive_distance_in_slots,  // destroyed if register
                                      Register temp_reg, Register temp2_reg) {
  assert_different_registers(bottom_reg,
                             temp_reg, temp2_reg,
                             positive_distance_in_slots.register_or_noreg());
  BLOCK_COMMENT("move_arg_slots_up {");
  Label L_loop, L_break;
  Register top_reg = temp_reg;
  if (!top_addr.is_same_address(Address(top_reg, 0))) {
    __ add(top_addr, top_reg);
  }
  // Detect empty (or broken) loop:
#ifdef ASSERT
  if (VerifyMethodHandles) {
    // Verify that &bottom < &top (non-empty interval)
    Label L_ok, L_bad;
    if (positive_distance_in_slots.is_register()) {
      __ cmp(positive_distance_in_slots.as_register(), (int32_t) 0);
      __ br(Assembler::lessEqual, false, Assembler::pn, L_bad);
      __ delayed()->nop();
    }
    __ cmp(bottom_reg, top_reg);
    __ brx(Assembler::lessUnsigned, false, Assembler::pt, L_ok);
    __ delayed()->nop();
    __ BIND(L_bad);
    __ stop("valid bounds (copy up)");
    __ BIND(L_ok);
  }
#endif
  __ cmp(bottom_reg, top_reg);
  __ brx(Assembler::greaterEqualUnsigned, false, Assembler::pn, L_break);
  __ delayed()->nop();
  // work top down to bottom, copying contiguous data upwards
  // In pseudo-code:
  //   while (--top >= bottom) *(top + distance) = *(top + 0);
  RegisterOrConstant offset = __ argument_offset(positive_distance_in_slots, positive_distance_in_slots.register_or_noreg());
  __ BIND(L_loop);
  __ sub(top_reg, wordSize, top_reg);
  __ ld_ptr(           Address(top_reg, 0     ), temp2_reg);
  __ st_ptr(temp2_reg, Address(top_reg, offset)           );
  __ cmp(top_reg, bottom_reg);
  __ brx(Assembler::greaterUnsigned, false, Assembler::pt, L_loop);
  __ delayed()->nop();  // FILLME
  assert(Interpreter::stackElementSize == wordSize, "else change loop");
  __ BIND(L_break);
  BLOCK_COMMENT("} move_arg_slots_up");
}

// in-place movement; no change to rsp
// blows temp_reg, temp2_reg
void MethodHandles::move_arg_slots_down(MacroAssembler* _masm,
                                        Address  bottom_addr,  // can use temp_reg
                                        Register top_reg,      // invariant
                                        RegisterOrConstant negative_distance_in_slots,  // destroyed if register
                                        Register temp_reg, Register temp2_reg) {
  assert_different_registers(top_reg,
                             negative_distance_in_slots.register_or_noreg(),
                             temp_reg, temp2_reg);
  BLOCK_COMMENT("move_arg_slots_down {");
  Label L_loop, L_break;
  Register bottom_reg = temp_reg;
  if (!bottom_addr.is_same_address(Address(bottom_reg, 0))) {
    __ add(bottom_addr, bottom_reg);
  }
  // Detect empty (or broken) loop:
#ifdef ASSERT
  assert(!negative_distance_in_slots.is_constant() || negative_distance_in_slots.as_constant() < 0, "");
  if (VerifyMethodHandles) {
    // Verify that &bottom < &top (non-empty interval)
    Label L_ok, L_bad;
    if (negative_distance_in_slots.is_register()) {
      __ cmp(negative_distance_in_slots.as_register(), (int32_t) 0);
      __ br(Assembler::greaterEqual, false, Assembler::pn, L_bad);
      __ delayed()->nop();
    }
    __ cmp(bottom_reg, top_reg);
    __ brx(Assembler::lessUnsigned, false, Assembler::pt, L_ok);
    __ delayed()->nop();
    __ BIND(L_bad);
    __ stop("valid bounds (copy down)");
    __ BIND(L_ok);
  }
#endif
  __ cmp(bottom_reg, top_reg);
  __ brx(Assembler::greaterEqualUnsigned, false, Assembler::pn, L_break);
  __ delayed()->nop();
  // work bottom up to top, copying contiguous data downwards
  // In pseudo-code:
  //   while (bottom < top) *(bottom - distance) = *(bottom + 0), bottom++;
  RegisterOrConstant offset = __ argument_offset(negative_distance_in_slots, negative_distance_in_slots.register_or_noreg());
  __ BIND(L_loop);
  __ ld_ptr(           Address(bottom_reg, 0     ), temp2_reg);
  __ st_ptr(temp2_reg, Address(bottom_reg, offset)           );
  __ add(bottom_reg, wordSize, bottom_reg);
  __ cmp(bottom_reg, top_reg);
  __ brx(Assembler::lessUnsigned, false, Assembler::pt, L_loop);
  __ delayed()->nop();  // FILLME
  assert(Interpreter::stackElementSize == wordSize, "else change loop");
  __ BIND(L_break);
  BLOCK_COMMENT("} move_arg_slots_down");
}

// Copy from a field or array element to a stacked argument slot.
// is_element (ignored) says whether caller is loading an array element instead of an instance field.
void MethodHandles::move_typed_arg(MacroAssembler* _masm,
                                   BasicType type, bool is_element,
                                   Address value_src, Address slot_dest,
                                   Register temp_reg) {
  assert(!slot_dest.uses(temp_reg), "must be different register");
  BLOCK_COMMENT(!is_element ? "move_typed_arg {" : "move_typed_arg { (array element)");
  if (type == T_OBJECT || type == T_ARRAY) {
    __ load_heap_oop(value_src, temp_reg);
    __ verify_oop(temp_reg);
    __ st_ptr(temp_reg, slot_dest);
  } else if (type != T_VOID) {
    int  arg_size      = type2aelembytes(type);
    bool arg_is_signed = is_signed_subword_type(type);
    int  slot_size     = is_subword_type(type) ? type2aelembytes(T_INT) : arg_size;  // store int sub-words as int
    __ load_sized_value( value_src, temp_reg, arg_size, arg_is_signed);
    __ store_sized_value(temp_reg, slot_dest, slot_size              );
  }
  BLOCK_COMMENT("} move_typed_arg");
}

// Cf. TemplateInterpreterGenerator::generate_return_entry_for and
// InterpreterMacroAssembler::save_return_value
void MethodHandles::move_return_value(MacroAssembler* _masm, BasicType type,
                                      Address return_slot) {
  BLOCK_COMMENT("move_return_value {");
  // Look at the type and pull the value out of the corresponding register.
  if (type == T_VOID) {
    // nothing to do
  } else if (type == T_OBJECT) {
    __ verify_oop(O0);
    __ st_ptr(O0, return_slot);
  } else if (type == T_INT || is_subword_type(type)) {
    int type_size = type2aelembytes(T_INT);
    __ store_sized_value(O0, return_slot, type_size);
  } else if (type == T_LONG) {
    // store the value by parts
    // Note: We assume longs are continguous (if misaligned) on the interpreter stack.
#if !defined(_LP64) && defined(COMPILER2)
    __ stx(G1, return_slot);
#else
  #ifdef _LP64
    __ stx(O0, return_slot);
  #else
    if (return_slot.has_disp()) {
      // The displacement is a constant
      __ st(O0, return_slot);
      __ st(O1, return_slot.plus_disp(Interpreter::stackElementSize));
    } else {
      __ std(O0, return_slot);
    }
  #endif
#endif
  } else if (type == T_FLOAT) {
    __ stf(FloatRegisterImpl::S, Ftos_f, return_slot);
  } else if (type == T_DOUBLE) {
    __ stf(FloatRegisterImpl::D, Ftos_f, return_slot);
  } else {
    ShouldNotReachHere();
  }
  BLOCK_COMMENT("} move_return_value");
}

#ifndef PRODUCT
extern "C" void print_method_handle(oop mh);
void trace_method_handle_stub(const char* adaptername,
                              oopDesc* mh,
                              intptr_t* saved_sp) {
  bool has_mh = (strstr(adaptername, "return/") == NULL);  // return adapters don't have mh
  tty->print_cr("MH %s mh="INTPTR_FORMAT " saved_sp=" INTPTR_FORMAT, adaptername, (intptr_t) mh, saved_sp);
  if (has_mh)
    print_method_handle(mh);
}
void MethodHandles::trace_method_handle(MacroAssembler* _masm, const char* adaptername) {
  if (!TraceMethodHandles)  return;
  BLOCK_COMMENT("trace_method_handle {");
  // save: Gargs, O5_savedSP
  __ save_frame(16);
  __ set((intptr_t) adaptername, O0);
  __ mov(G3_method_handle, O1);
  __ mov(I5_savedSP, O2);
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
  return ((1<<java_lang_invoke_AdapterMethodHandle::OP_RETYPE_ONLY)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_RETYPE_RAW)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_CHECK_CAST)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_PRIM)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_REF_TO_PRIM)
          // OP_PRIM_TO_REF is below...
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_SWAP_ARGS)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_ROT_ARGS)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_DUP_ARGS)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_DROP_ARGS)
          // OP_COLLECT_ARGS is below...
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_SPREAD_ARGS)
         |(!UseRicochetFrames ? 0 :
           java_lang_invoke_MethodTypeForm::vmlayout_offset_in_bytes() <= 0 ? 0 :
           ((1<<java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_REF)
           |(1<<java_lang_invoke_AdapterMethodHandle::OP_COLLECT_ARGS)
           |(1<<java_lang_invoke_AdapterMethodHandle::OP_FOLD_ARGS)
           )
          )
         );
}

//------------------------------------------------------------------------------
// MethodHandles::generate_method_handle_stub
//
// Generate an "entry" field for a method handle.
// This determines how the method handle will respond to calls.
void MethodHandles::generate_method_handle_stub(MacroAssembler* _masm, MethodHandles::EntryKind ek) {
  MethodHandles::EntryKind ek_orig = ek_original_kind(ek);

  // Here is the register state during an interpreted call,
  // as set up by generate_method_handle_interpreter_entry():
  // - G5: garbage temp (was MethodHandle.invoke methodOop, unused)
  // - G3: receiver method handle
  // - O5_savedSP: sender SP (must preserve)

  const Register O0_scratch = O0;
  const Register O1_scratch = O1;
  const Register O2_scratch = O2;
  const Register O3_scratch = O3;
  const Register O4_scratch = O4;
  const Register G5_scratch = G5;

  // Often used names:
  const Register O0_argslot = O0;

  // Argument registers for _raise_exception:
  const Register O0_code     = O0;
  const Register O1_actual   = O1;
  const Register O2_required = O2;

  guarantee(java_lang_invoke_MethodHandle::vmentry_offset_in_bytes() != 0, "must have offsets");

  // Some handy addresses:
  Address G3_mh_vmtarget(   G3_method_handle, java_lang_invoke_MethodHandle::vmtarget_offset_in_bytes());

  Address G3_dmh_vmindex(   G3_method_handle, java_lang_invoke_DirectMethodHandle::vmindex_offset_in_bytes());

  Address G3_bmh_vmargslot( G3_method_handle, java_lang_invoke_BoundMethodHandle::vmargslot_offset_in_bytes());
  Address G3_bmh_argument(  G3_method_handle, java_lang_invoke_BoundMethodHandle::argument_offset_in_bytes());

  Address G3_amh_vmargslot( G3_method_handle, java_lang_invoke_AdapterMethodHandle::vmargslot_offset_in_bytes());
  Address G3_amh_argument ( G3_method_handle, java_lang_invoke_AdapterMethodHandle::argument_offset_in_bytes());
  Address G3_amh_conversion(G3_method_handle, java_lang_invoke_AdapterMethodHandle::conversion_offset_in_bytes());

  const int java_mirror_offset = klassOopDesc::klass_part_offset_in_bytes() + Klass::java_mirror_offset_in_bytes();

  if (have_entry(ek)) {
    __ nop();  // empty stubs make SG sick
    return;
  }

  address interp_entry = __ pc();

  trace_method_handle(_masm, entry_name(ek));

  BLOCK_COMMENT(err_msg("Entry %s {", entry_name(ek)));

  switch ((int) ek) {
  case _raise_exception:
    {
      // Not a real MH entry, but rather shared code for raising an
      // exception.  For sharing purposes the arguments are passed into registers
      // and then placed in the intepreter calling convention here.
      assert(raise_exception_method(), "must be set");
      assert(raise_exception_method()->from_compiled_entry(), "method must be linked");

      __ set(AddressLiteral((address) &_raise_exception_method), G5_method);
      __ ld_ptr(Address(G5_method, 0), G5_method);

      const int jobject_oop_offset = 0;
      __ ld_ptr(Address(G5_method, jobject_oop_offset), G5_method);

      adjust_SP_and_Gargs_down_by_slots(_masm, 3, noreg, noreg);

      __ st_ptr(O0_code,     __ argument_address(constant(2), noreg, 0));
      __ st_ptr(O1_actual,   __ argument_address(constant(1), noreg, 0));
      __ st_ptr(O2_required, __ argument_address(constant(0), noreg, 0));
      jump_from_method_handle(_masm, G5_method, O1_scratch, O2_scratch);
    }
    break;

  case _invokestatic_mh:
  case _invokespecial_mh:
    {
      __ load_heap_oop(G3_mh_vmtarget, G5_method);  // target is a methodOop
      // Same as TemplateTable::invokestatic or invokespecial,
      // minus the CP setup and profiling:
      if (ek == _invokespecial_mh) {
        // Must load & check the first argument before entering the target method.
        __ load_method_handle_vmslots(O0_argslot, G3_method_handle, O1_scratch);
        __ ld_ptr(__ argument_address(O0_argslot, O0_argslot, -1), G3_method_handle);
        __ null_check(G3_method_handle);
        __ verify_oop(G3_method_handle);
      }
      jump_from_method_handle(_masm, G5_method, O1_scratch, O2_scratch);
    }
    break;

  case _invokevirtual_mh:
    {
      // Same as TemplateTable::invokevirtual,
      // minus the CP setup and profiling:

      // Pick out the vtable index and receiver offset from the MH,
      // and then we can discard it:
      Register O2_index = O2_scratch;
      __ load_method_handle_vmslots(O0_argslot, G3_method_handle, O1_scratch);
      __ ldsw(G3_dmh_vmindex, O2_index);
      // Note:  The verifier allows us to ignore G3_mh_vmtarget.
      __ ld_ptr(__ argument_address(O0_argslot, O0_argslot, -1), G3_method_handle);
      __ null_check(G3_method_handle, oopDesc::klass_offset_in_bytes());

      // Get receiver klass:
      Register O0_klass = O0_argslot;
      __ load_klass(G3_method_handle, O0_klass);
      __ verify_oop(O0_klass);

      // Get target methodOop & entry point:
      const int base = instanceKlass::vtable_start_offset() * wordSize;
      assert(vtableEntry::size() * wordSize == wordSize, "adjust the scaling in the code below");

      __ sll_ptr(O2_index, LogBytesPerWord, O2_index);
      __ add(O0_klass, O2_index, O0_klass);
      Address vtable_entry_addr(O0_klass, base + vtableEntry::method_offset_in_bytes());
      __ ld_ptr(vtable_entry_addr, G5_method);

      jump_from_method_handle(_masm, G5_method, O1_scratch, O2_scratch);
    }
    break;

  case _invokeinterface_mh:
    {
      // Same as TemplateTable::invokeinterface,
      // minus the CP setup and profiling:
      __ load_method_handle_vmslots(O0_argslot, G3_method_handle, O1_scratch);
      Register O1_intf  = O1_scratch;
      Register G5_index = G5_scratch;
      __ load_heap_oop(G3_mh_vmtarget, O1_intf);
      __ ldsw(G3_dmh_vmindex, G5_index);
      __ ld_ptr(__ argument_address(O0_argslot, O0_argslot, -1), G3_method_handle);
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

      jump_from_method_handle(_masm, G5_method, O1_scratch, O2_scratch);

      __ bind(no_such_interface);
      // Throw an exception.
      // For historical reasons, it will be IncompatibleClassChangeError.
      __ unimplemented("not tested yet");
      __ ld_ptr(Address(O1_intf, java_mirror_offset), O2_required);  // required interface
      __ mov(   O0_klass,                             O1_actual);    // bad receiver
      __ jump_to(AddressLiteral(from_interpreted_entry(_raise_exception)), O3_scratch);
      __ delayed()->mov(Bytecodes::_invokeinterface,  O0_code);      // who is complaining?
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
      BasicType arg_type  = ek_bound_mh_arg_type(ek);
      int       arg_slots = type2size[arg_type];

      // Make room for the new argument:
      load_vmargslot(_masm, G3_bmh_vmargslot, O0_argslot);
      __ add(__ argument_address(O0_argslot, O0_argslot), O0_argslot);

      insert_arg_slots(_masm, arg_slots * stack_move_unit(), O0_argslot, O1_scratch, O2_scratch, O3_scratch);

      // Store bound argument into the new stack slot:
      __ load_heap_oop(G3_bmh_argument, O1_scratch);
      if (arg_type == T_OBJECT) {
        __ st_ptr(O1_scratch, Address(O0_argslot, 0));
      } else {
        Address prim_value_addr(O1_scratch, java_lang_boxing_object::value_offset_in_bytes(arg_type));
        move_typed_arg(_masm, arg_type, false,
                       prim_value_addr,
                       Address(O0_argslot, 0),
                       O2_scratch);  // must be an even register for !_LP64 long moves (uses O2/O3)
      }

      if (direct_to_method) {
        __ load_heap_oop(G3_mh_vmtarget, G5_method);  // target is a methodOop
        jump_from_method_handle(_masm, G5_method, O1_scratch, O2_scratch);
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
    __ verify_oop(G3_method_handle);
    __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    // This is OK when all parameter types widen.
    // It is also OK when a return type narrows.
    break;

  case _adapter_check_cast:
    {
      // Check a reference argument before jumping to the next layer of MH:
      load_vmargslot(_masm, G3_amh_vmargslot, O0_argslot);
      Address vmarg = __ argument_address(O0_argslot, O0_argslot);

      // What class are we casting to?
      Register O1_klass = O1_scratch;  // Interesting AMH data.
      __ load_heap_oop(G3_amh_argument, O1_klass);  // This is a Class object!
      load_klass_from_Class(_masm, O1_klass, O2_scratch, O3_scratch);

      Label L_done;
      __ ld_ptr(vmarg, O2_scratch);
      __ tst(O2_scratch);
      __ brx(Assembler::zero, false, Assembler::pn, L_done);  // No cast if null.
      __ delayed()->nop();
      __ load_klass(O2_scratch, O2_scratch);

      // Live at this point:
      // - O0_argslot      :  argslot index in vmarg; may be required in the failing path
      // - O1_klass        :  klass required by the target method
      // - O2_scratch      :  argument klass to test
      // - G3_method_handle:  adapter method handle
      __ check_klass_subtype(O2_scratch, O1_klass, O3_scratch, O4_scratch, L_done);

      // If we get here, the type check failed!
      __ load_heap_oop(G3_amh_argument,        O2_required);  // required class
      __ ld_ptr(       vmarg,                  O1_actual);    // bad object
      __ jump_to(AddressLiteral(from_interpreted_entry(_raise_exception)), O3_scratch);
      __ delayed()->mov(Bytecodes::_checkcast, O0_code);      // who is complaining?

      __ BIND(L_done);
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
      load_vmargslot(_masm, G3_amh_vmargslot, O0_argslot);
      Address value;
      Address vmarg;
      bool value_left_justified = false;

      switch (ek) {
      case _adapter_opt_i2i:
        value = vmarg = __ argument_address(O0_argslot, O0_argslot);
        break;
      case _adapter_opt_l2i:
        {
          // just delete the extra slot
#ifdef _LP64
          // In V9, longs are given 2 64-bit slots in the interpreter, but the
          // data is passed in only 1 slot.
          // Keep the second slot.
          __ add(__ argument_address(O0_argslot, O0_argslot, -1), O0_argslot);
          remove_arg_slots(_masm, -stack_move_unit(), O0_argslot, O1_scratch, O2_scratch, O3_scratch);
          value = Address(O0_argslot, 4);  // Get least-significant 32-bit of 64-bit value.
          vmarg = Address(O0_argslot, Interpreter::stackElementSize);
#else
          // Keep the first slot.
          __ add(__ argument_address(O0_argslot, O0_argslot), O0_argslot);
          remove_arg_slots(_masm, -stack_move_unit(), O0_argslot, O1_scratch, O2_scratch, O3_scratch);
          value = Address(O0_argslot, 0);
          vmarg = value;
#endif
        }
        break;
      case _adapter_opt_unboxi:
        {
          vmarg = __ argument_address(O0_argslot, O0_argslot);
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
      Register G5_vminfo = G5_scratch;
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
      load_vmargslot(_masm, G3_amh_vmargslot, O0_argslot);

      // On big-endian machine we duplicate the slot and store the MSW
      // in the first slot.
      __ add(__ argument_address(O0_argslot, O0_argslot, 1), O0_argslot);

      insert_arg_slots(_masm, stack_move_unit(), O0_argslot, O1_scratch, O2_scratch, O3_scratch);

      Address arg_lsw(O0_argslot, 0);
      Address arg_msw(O0_argslot, -Interpreter::stackElementSize);

      switch (ek) {
      case _adapter_opt_i2l:
        {
#ifdef _LP64
          __ ldsw(arg_lsw, O2_scratch);                 // Load LSW sign-extended
#else
          __ ldsw(arg_lsw, O3_scratch);                 // Load LSW sign-extended
          __ srlx(O3_scratch, BitsPerInt, O2_scratch);  // Move MSW value to lower 32-bits for std
#endif
          __ st_long(O2_scratch, arg_msw);              // Uses O2/O3 on !_LP64
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
      int swap_slots = ek_adapter_opt_swap_slots(ek);
      int rotate     = ek_adapter_opt_swap_mode(ek);

      // 'argslot' is the position of the first argument to swap.
      load_vmargslot(_masm, G3_amh_vmargslot, O0_argslot);
      __ add(__ argument_address(O0_argslot, O0_argslot), O0_argslot);
      if (VerifyMethodHandles)
        verify_argslot(_masm, O0_argslot, O2_scratch, "swap point must fall within current frame");

      // 'vminfo' is the second.
      Register O1_destslot = O1_scratch;
      load_conversion_vminfo(_masm, G3_amh_conversion, O1_destslot);
      __ add(__ argument_address(O1_destslot, O1_destslot), O1_destslot);
      if (VerifyMethodHandles)
        verify_argslot(_masm, O1_destslot, O2_scratch, "swap point must fall within current frame");

      assert(Interpreter::stackElementSize == wordSize, "else rethink use of wordSize here");
      if (!rotate) {
        // simple swap
        for (int i = 0; i < swap_slots; i++) {
          __ ld_ptr(            Address(O0_argslot,  i * wordSize), O2_scratch);
          __ ld_ptr(            Address(O1_destslot, i * wordSize), O3_scratch);
          __ st_ptr(O3_scratch, Address(O0_argslot,  i * wordSize));
          __ st_ptr(O2_scratch, Address(O1_destslot, i * wordSize));
        }
      } else {
        // A rotate is actually pair of moves, with an "odd slot" (or pair)
        // changing place with a series of other slots.
        // First, push the "odd slot", which is going to get overwritten
        switch (swap_slots) {
        case 2 :  __ ld_ptr(Address(O0_argslot, 1 * wordSize), O4_scratch); // fall-thru
        case 1 :  __ ld_ptr(Address(O0_argslot, 0 * wordSize), O3_scratch); break;
        default:  ShouldNotReachHere();
        }
        if (rotate > 0) {
          // Here is rotate > 0:
          // (low mem)                                          (high mem)
          //     | dest:     more_slots...     | arg: odd_slot :arg+1 |
          // =>
          //     | dest: odd_slot | dest+1: more_slots...      :arg+1 |
          // work argslot down to destslot, copying contiguous data upwards
          // pseudo-code:
          //   argslot  = src_addr - swap_bytes
          //   destslot = dest_addr
          //   while (argslot >= destslot) *(argslot + swap_bytes) = *(argslot + 0), argslot--;
          move_arg_slots_up(_masm,
                            O1_destslot,
                            Address(O0_argslot, 0),
                            swap_slots,
                            O0_argslot, O2_scratch);
        } else {
          // Here is the other direction, rotate < 0:
          // (low mem)                                          (high mem)
          //     | arg: odd_slot | arg+1: more_slots...       :dest+1 |
          // =>
          //     | arg:    more_slots...     | dest: odd_slot :dest+1 |
          // work argslot up to destslot, copying contiguous data downwards
          // pseudo-code:
          //   argslot  = src_addr + swap_bytes
          //   destslot = dest_addr
          //   while (argslot <= destslot) *(argslot - swap_bytes) = *(argslot + 0), argslot++;
          // dest_slot denotes an exclusive upper limit
          int limit_bias = OP_ROT_ARGS_DOWN_LIMIT_BIAS;
          if (limit_bias != 0)
            __ add(O1_destslot, - limit_bias * wordSize, O1_destslot);
          move_arg_slots_down(_masm,
                              Address(O0_argslot, swap_slots * wordSize),
                              O1_destslot,
                              -swap_slots,
                              O0_argslot, O2_scratch);

          __ sub(O1_destslot, swap_slots * wordSize, O1_destslot);
        }
        // pop the original first chunk into the destination slot, now free
        switch (swap_slots) {
        case 2 :  __ st_ptr(O4_scratch, Address(O1_destslot, 1 * wordSize)); // fall-thru
        case 1 :  __ st_ptr(O3_scratch, Address(O1_destslot, 0 * wordSize)); break;
        default:  ShouldNotReachHere();
        }
      }

      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_dup_args:
    {
      // 'argslot' is the position of the first argument to duplicate.
      load_vmargslot(_masm, G3_amh_vmargslot, O0_argslot);
      __ add(__ argument_address(O0_argslot, O0_argslot), O0_argslot);

      // 'stack_move' is negative number of words to duplicate.
      Register O1_stack_move = O1_scratch;
      load_stack_move(_masm, G3_amh_conversion, O1_stack_move);

      if (VerifyMethodHandles) {
        verify_argslots(_masm, O1_stack_move, O0_argslot, O2_scratch, O3_scratch, true,
                        "copied argument(s) must fall within current frame");
      }

      // insert location is always the bottom of the argument list:
      __ neg(O1_stack_move);
      push_arg_slots(_masm, O0_argslot, O1_stack_move, O2_scratch, O3_scratch);

      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_drop_args:
    {
      // 'argslot' is the position of the first argument to nuke.
      load_vmargslot(_masm, G3_amh_vmargslot, O0_argslot);
      __ add(__ argument_address(O0_argslot, O0_argslot), O0_argslot);

      // 'stack_move' is number of words to drop.
      Register O1_stack_move = O1_scratch;
      load_stack_move(_masm, G3_amh_conversion, O1_stack_move);

      remove_arg_slots(_masm, O1_stack_move, O0_argslot, O2_scratch, O3_scratch, O4_scratch);

      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_collect_args:
  case _adapter_fold_args:
  case _adapter_spread_args:
    // Handled completely by optimized cases.
    __ stop("init_AdapterMethodHandle should not issue this");
    break;

  case _adapter_opt_collect_ref:
  case _adapter_opt_collect_int:
  case _adapter_opt_collect_long:
  case _adapter_opt_collect_float:
  case _adapter_opt_collect_double:
  case _adapter_opt_collect_void:
  case _adapter_opt_collect_0_ref:
  case _adapter_opt_collect_1_ref:
  case _adapter_opt_collect_2_ref:
  case _adapter_opt_collect_3_ref:
  case _adapter_opt_collect_4_ref:
  case _adapter_opt_collect_5_ref:
  case _adapter_opt_filter_S0_ref:
  case _adapter_opt_filter_S1_ref:
  case _adapter_opt_filter_S2_ref:
  case _adapter_opt_filter_S3_ref:
  case _adapter_opt_filter_S4_ref:
  case _adapter_opt_filter_S5_ref:
  case _adapter_opt_collect_2_S0_ref:
  case _adapter_opt_collect_2_S1_ref:
  case _adapter_opt_collect_2_S2_ref:
  case _adapter_opt_collect_2_S3_ref:
  case _adapter_opt_collect_2_S4_ref:
  case _adapter_opt_collect_2_S5_ref:
  case _adapter_opt_fold_ref:
  case _adapter_opt_fold_int:
  case _adapter_opt_fold_long:
  case _adapter_opt_fold_float:
  case _adapter_opt_fold_double:
  case _adapter_opt_fold_void:
  case _adapter_opt_fold_1_ref:
  case _adapter_opt_fold_2_ref:
  case _adapter_opt_fold_3_ref:
  case _adapter_opt_fold_4_ref:
  case _adapter_opt_fold_5_ref:
    {
      // Given a fresh incoming stack frame, build a new ricochet frame.
      // On entry, TOS points at a return PC, and FP is the callers frame ptr.
      // RSI/R13 has the caller's exact stack pointer, which we must also preserve.
      // RCX contains an AdapterMethodHandle of the indicated kind.

      // Relevant AMH fields:
      // amh.vmargslot:
      //   points to the trailing edge of the arguments
      //   to filter, collect, or fold.  For a boxing operation,
      //   it points just after the single primitive value.
      // amh.argument:
      //   recursively called MH, on |collect| arguments
      // amh.vmtarget:
      //   final destination MH, on return value, etc.
      // amh.conversion.dest:
      //   tells what is the type of the return value
      //   (not needed here, since dest is also derived from ek)
      // amh.conversion.vminfo:
      //   points to the trailing edge of the return value
      //   when the vmtarget is to be called; this is
      //   equal to vmargslot + (retained ? |collect| : 0)

      // Pass 0 or more argument slots to the recursive target.
      int collect_count_constant = ek_adapter_opt_collect_count(ek);

      // The collected arguments are copied from the saved argument list:
      int collect_slot_constant = ek_adapter_opt_collect_slot(ek);

      assert(ek_orig == _adapter_collect_args ||
             ek_orig == _adapter_fold_args, "");
      bool retain_original_args = (ek_orig == _adapter_fold_args);

      // The return value is replaced (or inserted) at the 'vminfo' argslot.
      // Sometimes we can compute this statically.
      int dest_slot_constant = -1;
      if (!retain_original_args)
        dest_slot_constant = collect_slot_constant;
      else if (collect_slot_constant >= 0 && collect_count_constant >= 0)
        // We are preserving all the arguments, and the return value is prepended,
        // so the return slot is to the left (above) the |collect| sequence.
        dest_slot_constant = collect_slot_constant + collect_count_constant;

      // Replace all those slots by the result of the recursive call.
      // The result type can be one of ref, int, long, float, double, void.
      // In the case of void, nothing is pushed on the stack after return.
      BasicType dest = ek_adapter_opt_collect_type(ek);
      assert(dest == type2wfield[dest], "dest is a stack slot type");
      int dest_count = type2size[dest];
      assert(dest_count == 1 || dest_count == 2 || (dest_count == 0 && dest == T_VOID), "dest has a size");

      // Choose a return continuation.
      EntryKind ek_ret = _adapter_opt_return_any;
      if (dest != T_CONFLICT && OptimizeMethodHandles) {
        switch (dest) {
        case T_INT    : ek_ret = _adapter_opt_return_int;     break;
        case T_LONG   : ek_ret = _adapter_opt_return_long;    break;
        case T_FLOAT  : ek_ret = _adapter_opt_return_float;   break;
        case T_DOUBLE : ek_ret = _adapter_opt_return_double;  break;
        case T_OBJECT : ek_ret = _adapter_opt_return_ref;     break;
        case T_VOID   : ek_ret = _adapter_opt_return_void;    break;
        default       : ShouldNotReachHere();
        }
        if (dest == T_OBJECT && dest_slot_constant >= 0) {
          EntryKind ek_try = EntryKind(_adapter_opt_return_S0_ref + dest_slot_constant);
          if (ek_try <= _adapter_opt_return_LAST &&
              ek_adapter_opt_return_slot(ek_try) == dest_slot_constant) {
            ek_ret = ek_try;
          }
        }
        assert(ek_adapter_opt_return_type(ek_ret) == dest, "");
      }

      // Already pushed:  ... keep1 | collect | keep2 |

      // Push a few extra argument words, if we need them to store the return value.
      {
        int extra_slots = 0;
        if (retain_original_args) {
          extra_slots = dest_count;
        } else if (collect_count_constant == -1) {
          extra_slots = dest_count;  // collect_count might be zero; be generous
        } else if (dest_count > collect_count_constant) {
          extra_slots = (dest_count - collect_count_constant);
        } else {
          // else we know we have enough dead space in |collect| to repurpose for return values
        }
        if (extra_slots != 0) {
          __ sub(SP, round_to(extra_slots, 2) * Interpreter::stackElementSize, SP);
        }
      }

      // Set up Ricochet Frame.
      __ mov(SP, O5_savedSP);  // record SP for the callee

      // One extra (empty) slot for outgoing target MH (see Gargs computation below).
      __ save_frame(2);  // Note: we need to add 2 slots since frame::memory_parameter_word_sp_offset is 23.

      // Note: Gargs is live throughout the following, until we make our recursive call.
      // And the RF saves a copy in L4_saved_args_base.

      RicochetFrame::enter_ricochet_frame(_masm, G3_method_handle, Gargs,
                                          entry(ek_ret)->from_interpreted_entry());

      // Compute argument base:
      // Set up Gargs for current frame, extra (empty) slot is for outgoing target MH (space reserved by save_frame above).
      __ add(FP, STACK_BIAS - (1 * Interpreter::stackElementSize), Gargs);

      // Now pushed:  ... keep1 | collect | keep2 | extra | [RF]

#ifdef ASSERT
      if (VerifyMethodHandles && dest != T_CONFLICT) {
        BLOCK_COMMENT("verify AMH.conv.dest {");
        extract_conversion_dest_type(_masm, RicochetFrame::L5_conversion, O1_scratch);
        Label L_dest_ok;
        __ cmp(O1_scratch, (int) dest);
        __ br(Assembler::equal, false, Assembler::pt, L_dest_ok);
        __ delayed()->nop();
        if (dest == T_INT) {
          for (int bt = T_BOOLEAN; bt < T_INT; bt++) {
            if (is_subword_type(BasicType(bt))) {
              __ cmp(O1_scratch, (int) bt);
              __ br(Assembler::equal, false, Assembler::pt, L_dest_ok);
              __ delayed()->nop();
            }
          }
        }
        __ stop("bad dest in AMH.conv");
        __ BIND(L_dest_ok);
        BLOCK_COMMENT("} verify AMH.conv.dest");
      }
#endif //ASSERT

      // Find out where the original copy of the recursive argument sequence begins.
      Register O0_coll = O0_scratch;
      {
        RegisterOrConstant collect_slot = collect_slot_constant;
        if (collect_slot_constant == -1) {
          load_vmargslot(_masm, G3_amh_vmargslot, O1_scratch);
          collect_slot = O1_scratch;
        }
        // collect_slot might be 0, but we need the move anyway.
        __ add(RicochetFrame::L4_saved_args_base, __ argument_offset(collect_slot, collect_slot.register_or_noreg()), O0_coll);
        // O0_coll now points at the trailing edge of |collect| and leading edge of |keep2|
      }

      // Replace the old AMH with the recursive MH.  (No going back now.)
      // In the case of a boxing call, the recursive call is to a 'boxer' method,
      // such as Integer.valueOf or Long.valueOf.  In the case of a filter
      // or collect call, it will take one or more arguments, transform them,
      // and return some result, to store back into argument_base[vminfo].
      __ load_heap_oop(G3_amh_argument, G3_method_handle);
      if (VerifyMethodHandles)  verify_method_handle(_masm, G3_method_handle, O1_scratch, O2_scratch);

      // Calculate |collect|, the number of arguments we are collecting.
      Register O1_collect_count = O1_scratch;
      RegisterOrConstant collect_count;
      if (collect_count_constant < 0) {
        __ load_method_handle_vmslots(O1_collect_count, G3_method_handle, O2_scratch);
        collect_count = O1_collect_count;
      } else {
        collect_count = collect_count_constant;
#ifdef ASSERT
        if (VerifyMethodHandles) {
          BLOCK_COMMENT("verify collect_count_constant {");
          __ load_method_handle_vmslots(O3_scratch, G3_method_handle, O2_scratch);
          Label L_count_ok;
          __ cmp(O3_scratch, collect_count_constant);
          __ br(Assembler::equal, false, Assembler::pt, L_count_ok);
          __ delayed()->nop();
          __ stop("bad vminfo in AMH.conv");
          __ BIND(L_count_ok);
          BLOCK_COMMENT("} verify collect_count_constant");
        }
#endif //ASSERT
      }

      // copy |collect| slots directly to TOS:
      push_arg_slots(_masm, O0_coll, collect_count, O2_scratch, O3_scratch);
      // Now pushed:  ... keep1 | collect | keep2 | RF... | collect |
      // O0_coll still points at the trailing edge of |collect| and leading edge of |keep2|

      // If necessary, adjust the saved arguments to make room for the eventual return value.
      // Normal adjustment:  ... keep1 | +dest+ | -collect- | keep2 | RF... | collect |
      // If retaining args:  ... keep1 | +dest+ |  collect  | keep2 | RF... | collect |
      // In the non-retaining case, this might move keep2 either up or down.
      // We don't have to copy the whole | RF... collect | complex,
      // but we must adjust RF.saved_args_base.
      // Also, from now on, we will forget about the original copy of |collect|.
      // If we are retaining it, we will treat it as part of |keep2|.
      // For clarity we will define |keep3| = |collect|keep2| or |keep2|.

      BLOCK_COMMENT("adjust trailing arguments {");
      // Compare the sizes of |+dest+| and |-collect-|, which are opposed opening and closing movements.
      int                open_count  = dest_count;
      RegisterOrConstant close_count = collect_count_constant;
      Register O1_close_count = O1_collect_count;
      if (retain_original_args) {
        close_count = constant(0);
      } else if (collect_count_constant == -1) {
        close_count = O1_collect_count;
      }

      // How many slots need moving?  This is simply dest_slot (0 => no |keep3|).
      RegisterOrConstant keep3_count;
      Register O2_keep3_count = O2_scratch;
      if (dest_slot_constant < 0) {
        extract_conversion_vminfo(_masm, RicochetFrame::L5_conversion, O2_keep3_count);
        keep3_count = O2_keep3_count;
      } else  {
        keep3_count = dest_slot_constant;
#ifdef ASSERT
        if (VerifyMethodHandles && dest_slot_constant < 0) {
          BLOCK_COMMENT("verify dest_slot_constant {");
          extract_conversion_vminfo(_masm, RicochetFrame::L5_conversion, O3_scratch);
          Label L_vminfo_ok;
          __ cmp(O3_scratch, dest_slot_constant);
          __ br(Assembler::equal, false, Assembler::pt, L_vminfo_ok);
          __ delayed()->nop();
          __ stop("bad vminfo in AMH.conv");
          __ BIND(L_vminfo_ok);
          BLOCK_COMMENT("} verify dest_slot_constant");
        }
#endif //ASSERT
      }

      // tasks remaining:
      bool move_keep3 = (!keep3_count.is_constant() || keep3_count.as_constant() != 0);
      bool stomp_dest = (NOT_DEBUG(dest == T_OBJECT) DEBUG_ONLY(dest_count != 0));
      bool fix_arg_base = (!close_count.is_constant() || open_count != close_count.as_constant());

      // Old and new argument locations (based at slot 0).
      // Net shift (&new_argv - &old_argv) is (close_count - open_count).
      bool zero_open_count = (open_count == 0);  // remember this bit of info
      if (move_keep3 && fix_arg_base) {
        // It will be easier to have everything in one register:
        if (close_count.is_register()) {
          // Deduct open_count from close_count register to get a clean +/- value.
          __ sub(close_count.as_register(), open_count, close_count.as_register());
        } else {
          close_count = close_count.as_constant() - open_count;
        }
        open_count = 0;
      }
      Register L4_old_argv = RicochetFrame::L4_saved_args_base;
      Register O3_new_argv = O3_scratch;
      if (fix_arg_base) {
        __ add(L4_old_argv, __ argument_offset(close_count, O4_scratch), O3_new_argv,
               -(open_count * Interpreter::stackElementSize));
      }

      // First decide if any actual data are to be moved.
      // We can skip if (a) |keep3| is empty, or (b) the argument list size didn't change.
      // (As it happens, all movements involve an argument list size change.)

      // If there are variable parameters, use dynamic checks to skip around the whole mess.
      Label L_done;
      if (keep3_count.is_register()) {
        __ tst(keep3_count.as_register());
        __ br(Assembler::zero, false, Assembler::pn, L_done);
        __ delayed()->nop();
      }
      if (close_count.is_register()) {
        __ cmp(close_count.as_register(), open_count);
        __ br(Assembler::equal, false, Assembler::pn, L_done);
        __ delayed()->nop();
      }

      if (move_keep3 && fix_arg_base) {
        bool emit_move_down = false, emit_move_up = false, emit_guard = false;
        if (!close_count.is_constant()) {
          emit_move_down = emit_guard = !zero_open_count;
          emit_move_up   = true;
        } else if (open_count != close_count.as_constant()) {
          emit_move_down = (open_count > close_count.as_constant());
          emit_move_up   = !emit_move_down;
        }
        Label L_move_up;
        if (emit_guard) {
          __ cmp(close_count.as_register(), open_count);
          __ br(Assembler::greater, false, Assembler::pn, L_move_up);
          __ delayed()->nop();
        }

        if (emit_move_down) {
          // Move arguments down if |+dest+| > |-collect-|
          // (This is rare, except when arguments are retained.)
          // This opens space for the return value.
          if (keep3_count.is_constant()) {
            for (int i = 0; i < keep3_count.as_constant(); i++) {
              __ ld_ptr(            Address(L4_old_argv, i * Interpreter::stackElementSize), O4_scratch);
              __ st_ptr(O4_scratch, Address(O3_new_argv, i * Interpreter::stackElementSize)            );
            }
          } else {
            // Live: O1_close_count, O2_keep3_count, O3_new_argv
            Register argv_top = O0_scratch;
            __ add(L4_old_argv, __ argument_offset(keep3_count, O4_scratch), argv_top);
            move_arg_slots_down(_masm,
                                Address(L4_old_argv, 0),  // beginning of old argv
                                argv_top,                 // end of old argv
                                close_count,              // distance to move down (must be negative)
                                O4_scratch, G5_scratch);
          }
        }

        if (emit_guard) {
          __ ba(false, L_done);  // assumes emit_move_up is true also
          __ delayed()->nop();
          __ BIND(L_move_up);
        }

        if (emit_move_up) {
          // Move arguments up if |+dest+| < |-collect-|
          // (This is usual, except when |keep3| is empty.)
          // This closes up the space occupied by the now-deleted collect values.
          if (keep3_count.is_constant()) {
            for (int i = keep3_count.as_constant() - 1; i >= 0; i--) {
              __ ld_ptr(            Address(L4_old_argv, i * Interpreter::stackElementSize), O4_scratch);
              __ st_ptr(O4_scratch, Address(O3_new_argv, i * Interpreter::stackElementSize)            );
            }
          } else {
            Address argv_top(L4_old_argv, __ argument_offset(keep3_count, O4_scratch));
            // Live: O1_close_count, O2_keep3_count, O3_new_argv
            move_arg_slots_up(_masm,
                              L4_old_argv,  // beginning of old argv
                              argv_top,     // end of old argv
                              close_count,  // distance to move up (must be positive)
                              O4_scratch, G5_scratch);
          }
        }
      }
      __ BIND(L_done);

      if (fix_arg_base) {
        // adjust RF.saved_args_base
        __ mov(O3_new_argv, RicochetFrame::L4_saved_args_base);
      }

      if (stomp_dest) {
        // Stomp the return slot, so it doesn't hold garbage.
        // This isn't strictly necessary, but it may help detect bugs.
        __ set(RicochetFrame::RETURN_VALUE_PLACEHOLDER, O4_scratch);
        __ st_ptr(O4_scratch, Address(RicochetFrame::L4_saved_args_base,
                                      __ argument_offset(keep3_count, keep3_count.register_or_noreg())));  // uses O2_keep3_count
      }
      BLOCK_COMMENT("} adjust trailing arguments");

      BLOCK_COMMENT("do_recursive_call");
      __ mov(SP, O5_savedSP);  // record SP for the callee
      __ set(ExternalAddress(SharedRuntime::ricochet_blob()->bounce_addr() - frame::pc_return_offset), O7);
      // The globally unique bounce address has two purposes:
      // 1. It helps the JVM recognize this frame (frame::is_ricochet_frame).
      // 2. When returned to, it cuts back the stack and redirects control flow
      //    to the return handler.
      // The return handler will further cut back the stack when it takes
      // down the RF.  Perhaps there is a way to streamline this further.

      // State during recursive call:
      // ... keep1 | dest | dest=42 | keep3 | RF... | collect | bounce_pc |
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);
    }
    break;

  case _adapter_opt_return_ref:
  case _adapter_opt_return_int:
  case _adapter_opt_return_long:
  case _adapter_opt_return_float:
  case _adapter_opt_return_double:
  case _adapter_opt_return_void:
  case _adapter_opt_return_S0_ref:
  case _adapter_opt_return_S1_ref:
  case _adapter_opt_return_S2_ref:
  case _adapter_opt_return_S3_ref:
  case _adapter_opt_return_S4_ref:
  case _adapter_opt_return_S5_ref:
    {
      BasicType dest_type_constant = ek_adapter_opt_return_type(ek);
      int       dest_slot_constant = ek_adapter_opt_return_slot(ek);

      if (VerifyMethodHandles)  RicochetFrame::verify_clean(_masm);

      if (dest_slot_constant == -1) {
        // The current stub is a general handler for this dest_type.
        // It can be called from _adapter_opt_return_any below.
        // Stash the address in a little table.
        assert((dest_type_constant & CONV_TYPE_MASK) == dest_type_constant, "oob");
        address return_handler = __ pc();
        _adapter_return_handlers[dest_type_constant] = return_handler;
        if (dest_type_constant == T_INT) {
          // do the subword types too
          for (int bt = T_BOOLEAN; bt < T_INT; bt++) {
            if (is_subword_type(BasicType(bt)) &&
                _adapter_return_handlers[bt] == NULL) {
              _adapter_return_handlers[bt] = return_handler;
            }
          }
        }
      }

      // On entry to this continuation handler, make Gargs live again.
      __ mov(RicochetFrame::L4_saved_args_base, Gargs);

      Register O7_temp   = O7;
      Register O5_vminfo = O5;

      RegisterOrConstant dest_slot = dest_slot_constant;
      if (dest_slot_constant == -1) {
        extract_conversion_vminfo(_masm, RicochetFrame::L5_conversion, O5_vminfo);
        dest_slot = O5_vminfo;
      }
      // Store the result back into the argslot.
      // This code uses the interpreter calling sequence, in which the return value
      // is usually left in the TOS register, as defined by InterpreterMacroAssembler::pop.
      // There are certain irregularities with floating point values, which can be seen
      // in TemplateInterpreterGenerator::generate_return_entry_for.
      move_return_value(_masm, dest_type_constant, __ argument_address(dest_slot, O7_temp));

      RicochetFrame::leave_ricochet_frame(_masm, G3_method_handle, I5_savedSP, I7);

      // Load the final target and go.
      if (VerifyMethodHandles)  verify_method_handle(_masm, G3_method_handle, O0_scratch, O1_scratch);
      __ restore(I5_savedSP, G0, SP);
      __ jump_to_method_handle_entry(G3_method_handle, O0_scratch);
      __ illtrap(0);
    }
    break;

  case _adapter_opt_return_any:
    {
      Register O7_temp      = O7;
      Register O5_dest_type = O5;

      if (VerifyMethodHandles)  RicochetFrame::verify_clean(_masm);
      extract_conversion_dest_type(_masm, RicochetFrame::L5_conversion, O5_dest_type);
      __ set(ExternalAddress((address) &_adapter_return_handlers[0]), O7_temp);
      __ sll_ptr(O5_dest_type, LogBytesPerWord, O5_dest_type);
      __ ld_ptr(O7_temp, O5_dest_type, O7_temp);

#ifdef ASSERT
      { Label L_ok;
        __ br_notnull(O7_temp, false, Assembler::pt, L_ok);
        __ delayed()->nop();
        __ stop("bad method handle return");
        __ BIND(L_ok);
      }
#endif //ASSERT
      __ JMP(O7_temp, 0);
      __ delayed()->nop();
    }
    break;

  case _adapter_opt_spread_0:
  case _adapter_opt_spread_1_ref:
  case _adapter_opt_spread_2_ref:
  case _adapter_opt_spread_3_ref:
  case _adapter_opt_spread_4_ref:
  case _adapter_opt_spread_5_ref:
  case _adapter_opt_spread_ref:
  case _adapter_opt_spread_byte:
  case _adapter_opt_spread_char:
  case _adapter_opt_spread_short:
  case _adapter_opt_spread_int:
  case _adapter_opt_spread_long:
  case _adapter_opt_spread_float:
  case _adapter_opt_spread_double:
    {
      // spread an array out into a group of arguments
      int  length_constant    = ek_adapter_opt_spread_count(ek);
      bool length_can_be_zero = (length_constant == 0);
      if (length_constant < 0) {
        // some adapters with variable length must handle the zero case
        if (!OptimizeMethodHandles ||
            ek_adapter_opt_spread_type(ek) != T_OBJECT)
          length_can_be_zero = true;
      }

      // find the address of the array argument
      load_vmargslot(_masm, G3_amh_vmargslot, O0_argslot);
      __ add(__ argument_address(O0_argslot, O0_argslot), O0_argslot);

      // O0_argslot points both to the array and to the first output arg
      Address vmarg = Address(O0_argslot, 0);

      // Get the array value.
      Register  O1_array       = O1_scratch;
      Register  O2_array_klass = O2_scratch;
      BasicType elem_type      = ek_adapter_opt_spread_type(ek);
      int       elem_slots     = type2size[elem_type];  // 1 or 2
      int       array_slots    = 1;  // array is always a T_OBJECT
      int       length_offset  = arrayOopDesc::length_offset_in_bytes();
      int       elem0_offset   = arrayOopDesc::base_offset_in_bytes(elem_type);
      __ ld_ptr(vmarg, O1_array);

      Label L_array_is_empty, L_insert_arg_space, L_copy_args, L_args_done;
      if (length_can_be_zero) {
        // handle the null pointer case, if zero is allowed
        Label L_skip;
        if (length_constant < 0) {
          load_conversion_vminfo(_masm, G3_amh_conversion, O3_scratch);
          __ br_zero(Assembler::notZero, false, Assembler::pn, O3_scratch, L_skip);
          __ delayed()->nop();
        }
        __ br_null(O1_array, false, Assembler::pn, L_array_is_empty);
        __ delayed()->nop();
        __ BIND(L_skip);
      }
      __ null_check(O1_array, oopDesc::klass_offset_in_bytes());
      __ load_klass(O1_array, O2_array_klass);

      // Check the array type.
      Register O3_klass = O3_scratch;
      __ load_heap_oop(G3_amh_argument, O3_klass);  // this is a Class object!
      load_klass_from_Class(_masm, O3_klass, O4_scratch, G5_scratch);

      Label L_ok_array_klass, L_bad_array_klass, L_bad_array_length;
      __ check_klass_subtype(O2_array_klass, O3_klass, O4_scratch, G5_scratch, L_ok_array_klass);
      // If we get here, the type check failed!
      __ ba(false, L_bad_array_klass);
      __ delayed()->nop();
      __ BIND(L_ok_array_klass);

      // Check length.
      if (length_constant >= 0) {
        __ ldsw(Address(O1_array, length_offset), O4_scratch);
        __ cmp(O4_scratch, length_constant);
      } else {
        Register O3_vminfo = O3_scratch;
        load_conversion_vminfo(_masm, G3_amh_conversion, O3_vminfo);
        __ ldsw(Address(O1_array, length_offset), O4_scratch);
        __ cmp(O3_vminfo, O4_scratch);
      }
      __ br(Assembler::notEqual, false, Assembler::pn, L_bad_array_length);
      __ delayed()->nop();

      Register O2_argslot_limit = O2_scratch;

      // Array length checks out.  Now insert any required stack slots.
      if (length_constant == -1) {
        // Form a pointer to the end of the affected region.
        __ add(O0_argslot, Interpreter::stackElementSize, O2_argslot_limit);
        // 'stack_move' is negative number of words to insert
        // This number already accounts for elem_slots.
        Register O3_stack_move = O3_scratch;
        load_stack_move(_masm, G3_amh_conversion, O3_stack_move);
        __ cmp(O3_stack_move, 0);
        assert(stack_move_unit() < 0, "else change this comparison");
        __ br(Assembler::less, false, Assembler::pn, L_insert_arg_space);
        __ delayed()->nop();
        __ br(Assembler::equal, false, Assembler::pn, L_copy_args);
        __ delayed()->nop();
        // single argument case, with no array movement
        __ BIND(L_array_is_empty);
        remove_arg_slots(_masm, -stack_move_unit() * array_slots,
                         O0_argslot, O1_scratch, O2_scratch, O3_scratch);
        __ ba(false, L_args_done);  // no spreading to do
        __ delayed()->nop();
        __ BIND(L_insert_arg_space);
        // come here in the usual case, stack_move < 0 (2 or more spread arguments)
        // Live: O1_array, O2_argslot_limit, O3_stack_move
        insert_arg_slots(_masm, O3_stack_move,
                         O0_argslot, O4_scratch, G5_scratch, O1_scratch);
        // reload from rdx_argslot_limit since rax_argslot is now decremented
        __ ld_ptr(Address(O2_argslot_limit, -Interpreter::stackElementSize), O1_array);
      } else if (length_constant >= 1) {
        int new_slots = (length_constant * elem_slots) - array_slots;
        insert_arg_slots(_masm, new_slots * stack_move_unit(),
                         O0_argslot, O2_scratch, O3_scratch, O4_scratch);
      } else if (length_constant == 0) {
        __ BIND(L_array_is_empty);
        remove_arg_slots(_masm, -stack_move_unit() * array_slots,
                         O0_argslot, O1_scratch, O2_scratch, O3_scratch);
      } else {
        ShouldNotReachHere();
      }

      // Copy from the array to the new slots.
      // Note: Stack change code preserves integrity of O0_argslot pointer.
      // So even after slot insertions, O0_argslot still points to first argument.
      // Beware:  Arguments that are shallow on the stack are deep in the array,
      // and vice versa.  So a downward-growing stack (the usual) has to be copied
      // elementwise in reverse order from the source array.
      __ BIND(L_copy_args);
      if (length_constant == -1) {
        // [O0_argslot, O2_argslot_limit) is the area we are inserting into.
        // Array element [0] goes at O0_argslot_limit[-wordSize].
        Register O1_source = O1_array;
        __ add(Address(O1_array, elem0_offset), O1_source);
        Register O4_fill_ptr = O4_scratch;
        __ mov(O2_argslot_limit, O4_fill_ptr);
        Label L_loop;
        __ BIND(L_loop);
        __ add(O4_fill_ptr, -Interpreter::stackElementSize * elem_slots, O4_fill_ptr);
        move_typed_arg(_masm, elem_type, true,
                       Address(O1_source, 0), Address(O4_fill_ptr, 0),
                       O2_scratch);  // must be an even register for !_LP64 long moves (uses O2/O3)
        __ add(O1_source, type2aelembytes(elem_type), O1_source);
        __ cmp(O4_fill_ptr, O0_argslot);
        __ brx(Assembler::greaterUnsigned, false, Assembler::pt, L_loop);
        __ delayed()->nop();  // FILLME
      } else if (length_constant == 0) {
        // nothing to copy
      } else {
        int elem_offset = elem0_offset;
        int slot_offset = length_constant * Interpreter::stackElementSize;
        for (int index = 0; index < length_constant; index++) {
          slot_offset -= Interpreter::stackElementSize * elem_slots;  // fill backward
          move_typed_arg(_masm, elem_type, true,
                         Address(O1_array, elem_offset), Address(O0_argslot, slot_offset),
                         O2_scratch);  // must be an even register for !_LP64 long moves (uses O2/O3)
          elem_offset += type2aelembytes(elem_type);
        }
      }
      __ BIND(L_args_done);

      // Arguments are spread.  Move to next method handle.
      __ load_heap_oop(G3_mh_vmtarget, G3_method_handle);
      __ jump_to_method_handle_entry(G3_method_handle, O1_scratch);

      __ BIND(L_bad_array_klass);
      assert(!vmarg.uses(O2_required), "must be different registers");
      __ load_heap_oop(Address(O2_array_klass, java_mirror_offset), O2_required);  // required class
      __ ld_ptr(       vmarg,                                       O1_actual);    // bad object
      __ jump_to(AddressLiteral(from_interpreted_entry(_raise_exception)), O3_scratch);
      __ delayed()->mov(Bytecodes::_aaload,                         O0_code);      // who is complaining?

      __ bind(L_bad_array_length);
      assert(!vmarg.uses(O2_required), "must be different registers");
      __ mov(   G3_method_handle,                O2_required);  // required class
      __ ld_ptr(vmarg,                           O1_actual);    // bad object
      __ jump_to(AddressLiteral(from_interpreted_entry(_raise_exception)), O3_scratch);
      __ delayed()->mov(Bytecodes::_arraylength, O0_code);      // who is complaining?
    }
    break;

  default:
    DEBUG_ONLY(tty->print_cr("bad ek=%d (%s)", (int)ek, entry_name(ek)));
    ShouldNotReachHere();
  }
  BLOCK_COMMENT(err_msg("} Entry %s", entry_name(ek)));

  address me_cookie = MethodHandleEntry::start_compiled_entry(_masm, interp_entry);
  __ unimplemented(entry_name(ek)); // %%% FIXME: NYI

  init_entry(ek, MethodHandleEntry::finish_compiled_entry(_masm, me_cookie));
}
