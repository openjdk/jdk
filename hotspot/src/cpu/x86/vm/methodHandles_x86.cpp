/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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
  RicochetFrame* f = RicochetFrame::from_frame(fr);
  if (map->update_map())
    frame::update_map_with_saved_link(map, &f->_sender_link);
  return frame(f->extended_sender_sp(), f->exact_sender_sp(), f->sender_link(), f->sender_pc());
}

void MethodHandles::ricochet_frame_oops_do(const frame& fr, OopClosure* blk, const RegisterMap* reg_map) {
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
  int slot_num = slot_count;
  intptr_t* loc = &base[slot_num -= 1];
  //blk->do_oop((oop*) loc);   // original target, which is irrelevant
  int arg_num = 0;
  for (SignatureStream ss(invoker->signature()); !ss.is_done(); ss.next()) {
    if (ss.at_return_type())  continue;
    BasicType ptype = ss.type();
    if (ptype == T_ARRAY)  ptype = T_OBJECT; // fold all refs to T_OBJECT
    assert(ptype >= T_BOOLEAN && ptype <= T_OBJECT, "not array or void");
    loc = &base[slot_num -= type2size[ptype]];
    bool is_oop = (ptype == T_OBJECT && loc != retval);
    if (is_oop)  blk->do_oop((oop*)loc);
    arg_num += 1;
  }
  assert(slot_num == 0, "must have processed all the arguments");
}

oop MethodHandles::RicochetFrame::compute_saved_args_layout(bool read_cache, bool write_cache) {
  oop cookie = NULL;
  if (read_cache) {
    cookie = saved_args_layout();
    if (cookie != NULL)  return cookie;
  }
  oop target = saved_target();
  oop mtype  = java_lang_invoke_MethodHandle::type(target);
  oop mtform = java_lang_invoke_MethodType::form(mtype);
  cookie = java_lang_invoke_MethodTypeForm::vmlayout(mtform);
  if (write_cache)  {
    (*saved_args_layout_addr()) = cookie;
  }
  return cookie;
}

void MethodHandles::RicochetFrame::generate_ricochet_blob(MacroAssembler* _masm,
                                                          // output params:
                                                          int* frame_size_in_words,
                                                          int* bounce_offset,
                                                          int* exception_offset) {
  (*frame_size_in_words) = RicochetFrame::frame_size_in_bytes() / wordSize;

  address start = __ pc();

#ifdef ASSERT
  __ hlt(); __ hlt(); __ hlt();
  // here's a hint of something special:
  __ push(MAGIC_NUMBER_1);
  __ push(MAGIC_NUMBER_2);
#endif //ASSERT
  __ hlt();  // not reached

  // A return PC has just been popped from the stack.
  // Return values are in registers.
  // The ebp points into the RicochetFrame, which contains
  // a cleanup continuation we must return to.

  (*bounce_offset) = __ pc() - start;
  BLOCK_COMMENT("ricochet_blob.bounce");

  if (VerifyMethodHandles)  RicochetFrame::verify_clean(_masm);
  trace_method_handle(_masm, "ricochet_blob.bounce");

  __ jmp(frame_address(continuation_offset_in_bytes()));
  __ hlt();
  DEBUG_ONLY(__ push(MAGIC_NUMBER_2));

  (*exception_offset) = __ pc() - start;
  BLOCK_COMMENT("ricochet_blob.exception");

  // compare this to Interpreter::rethrow_exception_entry, which is parallel code
  // for example, see TemplateInterpreterGenerator::generate_throw_exception
  // Live registers in:
  //   rax: exception
  //   rdx: return address/pc that threw exception (ignored, always equal to bounce addr)
  __ verify_oop(rax);

  // no need to empty_FPU_stack or reinit_heapbase, since caller frame will do the same if needed

  // Take down the frame.

  // Cf. InterpreterMacroAssembler::remove_activation.
  leave_ricochet_frame(_masm, /*rcx_recv=*/ noreg,
                       saved_last_sp_register(),
                       /*sender_pc_reg=*/ rdx);

  // In between activations - previous activation type unknown yet
  // compute continuation point - the continuation point expects the
  // following registers set up:
  //
  // rax: exception
  // rdx: return address/pc that threw exception
  // rsp: expression stack of caller
  // rbp: ebp of caller
  __ push(rax);                                  // save exception
  __ push(rdx);                                  // save return address
  Register thread_reg = LP64_ONLY(r15_thread) NOT_LP64(rdi);
  NOT_LP64(__ get_thread(thread_reg));
  __ call_VM_leaf(CAST_FROM_FN_PTR(address,
                                   SharedRuntime::exception_handler_for_return_address),
                  thread_reg, rdx);
  __ mov(rbx, rax);                              // save exception handler
  __ pop(rdx);                                   // restore return address
  __ pop(rax);                                   // restore exception
  __ jmp(rbx);                                   // jump to exception
                                                 // handler of caller
}

void MethodHandles::RicochetFrame::enter_ricochet_frame(MacroAssembler* _masm,
                                                        Register rcx_recv,
                                                        Register rax_argv,
                                                        address return_handler,
                                                        Register rbx_temp) {
  const Register saved_last_sp = saved_last_sp_register();
  Address rcx_mh_vmtarget(    rcx_recv, java_lang_invoke_MethodHandle::vmtarget_offset_in_bytes() );
  Address rcx_amh_conversion( rcx_recv, java_lang_invoke_AdapterMethodHandle::conversion_offset_in_bytes() );

  // Push the RicochetFrame a word at a time.
  // This creates something similar to an interpreter frame.
  // Cf. TemplateInterpreterGenerator::generate_fixed_frame.
  BLOCK_COMMENT("push RicochetFrame {");
  DEBUG_ONLY(int rfo = (int) sizeof(RicochetFrame));
  assert((rfo -= wordSize) == RicochetFrame::sender_pc_offset_in_bytes(), "");
#define RF_FIELD(push_value, name)                                      \
  { push_value;                                                         \
    assert((rfo -= wordSize) == RicochetFrame::name##_offset_in_bytes(), ""); }
  RF_FIELD(__ push(rbp),                   sender_link);
  RF_FIELD(__ push(saved_last_sp),         exact_sender_sp);  // rsi/r13
  RF_FIELD(__ pushptr(rcx_amh_conversion), conversion);
  RF_FIELD(__ push(rax_argv),              saved_args_base);   // can be updated if args are shifted
  RF_FIELD(__ push((int32_t) NULL_WORD),   saved_args_layout); // cache for GC layout cookie
  if (UseCompressedOops) {
    __ load_heap_oop(rbx_temp, rcx_mh_vmtarget);
    RF_FIELD(__ push(rbx_temp),            saved_target);
  } else {
    RF_FIELD(__ pushptr(rcx_mh_vmtarget),  saved_target);
  }
  __ lea(rbx_temp, ExternalAddress(return_handler));
  RF_FIELD(__ push(rbx_temp),              continuation);
#undef RF_FIELD
  assert(rfo == 0, "fully initialized the RicochetFrame");
  // compute new frame pointer:
  __ lea(rbp, Address(rsp, RicochetFrame::sender_link_offset_in_bytes()));
  // Push guard word #1 in debug mode.
  DEBUG_ONLY(__ push((int32_t) RicochetFrame::MAGIC_NUMBER_1));
  // For debugging, leave behind an indication of which stub built this frame.
  DEBUG_ONLY({ Label L; __ call(L, relocInfo::none); __ bind(L); });
  BLOCK_COMMENT("} RicochetFrame");
}

void MethodHandles::RicochetFrame::leave_ricochet_frame(MacroAssembler* _masm,
                                                        Register rcx_recv,
                                                        Register new_sp_reg,
                                                        Register sender_pc_reg) {
  assert_different_registers(rcx_recv, new_sp_reg, sender_pc_reg);
  const Register saved_last_sp = saved_last_sp_register();
  // Take down the frame.
  // Cf. InterpreterMacroAssembler::remove_activation.
  BLOCK_COMMENT("end_ricochet_frame {");
  // TO DO: If (exact_sender_sp - extended_sender_sp) > THRESH, compact the frame down.
  // This will keep stack in bounds even with unlimited tailcalls, each with an adapter.
  if (rcx_recv->is_valid())
    __ movptr(rcx_recv,    RicochetFrame::frame_address(RicochetFrame::saved_target_offset_in_bytes()));
  __ movptr(sender_pc_reg, RicochetFrame::frame_address(RicochetFrame::sender_pc_offset_in_bytes()));
  __ movptr(saved_last_sp, RicochetFrame::frame_address(RicochetFrame::exact_sender_sp_offset_in_bytes()));
  __ movptr(rbp,           RicochetFrame::frame_address(RicochetFrame::sender_link_offset_in_bytes()));
  __ mov(rsp, new_sp_reg);
  BLOCK_COMMENT("} end_ricochet_frame");
}

// Emit code to verify that RBP is pointing at a valid ricochet frame.
#ifdef ASSERT
enum {
  ARG_LIMIT = 255, SLOP = 4,
  // use this parameter for checking for garbage stack movements:
  UNREASONABLE_STACK_MOVE = (ARG_LIMIT + SLOP)
  // the slop defends against false alarms due to fencepost errors
};

void MethodHandles::RicochetFrame::verify_clean(MacroAssembler* _masm) {
  // The stack should look like this:
  //    ... keep1 | dest=42 | keep2 | RF | magic | handler | magic | recursive args |
  // Check various invariants.
  verify_offsets();

  Register rdi_temp = rdi;
  Register rcx_temp = rcx;
  { __ push(rdi_temp); __ push(rcx_temp); }
#define UNPUSH_TEMPS \
  { __ pop(rcx_temp);  __ pop(rdi_temp); }

  Address magic_number_1_addr  = RicochetFrame::frame_address(RicochetFrame::magic_number_1_offset_in_bytes());
  Address magic_number_2_addr  = RicochetFrame::frame_address(RicochetFrame::magic_number_2_offset_in_bytes());
  Address continuation_addr    = RicochetFrame::frame_address(RicochetFrame::continuation_offset_in_bytes());
  Address conversion_addr      = RicochetFrame::frame_address(RicochetFrame::conversion_offset_in_bytes());
  Address saved_args_base_addr = RicochetFrame::frame_address(RicochetFrame::saved_args_base_offset_in_bytes());

  Label L_bad, L_ok;
  BLOCK_COMMENT("verify_clean {");
  // Magic numbers must check out:
  __ cmpptr(magic_number_1_addr, (int32_t) MAGIC_NUMBER_1);
  __ jcc(Assembler::notEqual, L_bad);
  __ cmpptr(magic_number_2_addr, (int32_t) MAGIC_NUMBER_2);
  __ jcc(Assembler::notEqual, L_bad);

  // Arguments pointer must look reasonable:
  __ movptr(rcx_temp, saved_args_base_addr);
  __ cmpptr(rcx_temp, rbp);
  __ jcc(Assembler::below, L_bad);
  __ subptr(rcx_temp, UNREASONABLE_STACK_MOVE * Interpreter::stackElementSize);
  __ cmpptr(rcx_temp, rbp);
  __ jcc(Assembler::above, L_bad);

  load_conversion_dest_type(_masm, rdi_temp, conversion_addr);
  __ cmpl(rdi_temp, T_VOID);
  __ jcc(Assembler::equal, L_ok);
  __ movptr(rcx_temp, saved_args_base_addr);
  load_conversion_vminfo(_masm, rdi_temp, conversion_addr);
  __ cmpptr(Address(rcx_temp, rdi_temp, Interpreter::stackElementScale()),
            (int32_t) RETURN_VALUE_PLACEHOLDER);
  __ jcc(Assembler::equal, L_ok);
  __ BIND(L_bad);
  UNPUSH_TEMPS;
  __ stop("damaged ricochet frame");
  __ BIND(L_ok);
  UNPUSH_TEMPS;
  BLOCK_COMMENT("} verify_clean");

#undef UNPUSH_TEMPS

}
#endif //ASSERT

void MethodHandles::load_klass_from_Class(MacroAssembler* _masm, Register klass_reg) {
  if (VerifyMethodHandles)
    verify_klass(_masm, klass_reg, SystemDictionaryHandles::Class_klass(),
                 "AMH argument is a Class");
  __ load_heap_oop(klass_reg, Address(klass_reg, java_lang_Class::klass_offset_in_bytes()));
}

void MethodHandles::load_conversion_vminfo(MacroAssembler* _masm, Register reg, Address conversion_field_addr) {
  int bits   = BitsPerByte;
  int offset = (CONV_VMINFO_SHIFT / bits);
  int shift  = (CONV_VMINFO_SHIFT % bits);
  __ load_unsigned_byte(reg, conversion_field_addr.plus_disp(offset));
  assert(CONV_VMINFO_MASK == right_n_bits(bits - shift), "else change type of previous load");
  assert(shift == 0, "no shift needed");
}

void MethodHandles::load_conversion_dest_type(MacroAssembler* _masm, Register reg, Address conversion_field_addr) {
  int bits   = BitsPerByte;
  int offset = (CONV_DEST_TYPE_SHIFT / bits);
  int shift  = (CONV_DEST_TYPE_SHIFT % bits);
  __ load_unsigned_byte(reg, conversion_field_addr.plus_disp(offset));
  assert(CONV_TYPE_MASK == right_n_bits(bits - shift), "else change type of previous load");
  __ shrl(reg, shift);
  DEBUG_ONLY(int conv_type_bits = (int) exact_log2(CONV_TYPE_MASK+1));
  assert((shift + conv_type_bits) == bits, "left justified in byte");
}

void MethodHandles::load_stack_move(MacroAssembler* _masm,
                                    Register rdi_stack_move,
                                    Register rcx_amh,
                                    bool might_be_negative) {
  BLOCK_COMMENT("load_stack_move");
  Address rcx_amh_conversion(rcx_amh, java_lang_invoke_AdapterMethodHandle::conversion_offset_in_bytes());
  __ movl(rdi_stack_move, rcx_amh_conversion);
  __ sarl(rdi_stack_move, CONV_STACK_MOVE_SHIFT);
#ifdef _LP64
  if (might_be_negative) {
    // clean high bits of stack motion register (was loaded as an int)
    __ movslq(rdi_stack_move, rdi_stack_move);
  }
#endif //_LP64
  if (VerifyMethodHandles) {
    Label L_ok, L_bad;
    int32_t stack_move_limit = 0x4000;  // extra-large
    __ cmpptr(rdi_stack_move, stack_move_limit);
    __ jcc(Assembler::greaterEqual, L_bad);
    __ cmpptr(rdi_stack_move, -stack_move_limit);
    __ jcc(Assembler::greater, L_ok);
    __ bind(L_bad);
    __ stop("load_stack_move of garbage value");
    __ BIND(L_ok);
  }
}

#ifndef PRODUCT
void MethodHandles::RicochetFrame::verify_offsets() {
  // Check compatibility of this struct with the more generally used offsets of class frame:
  int ebp_off = sender_link_offset_in_bytes();  // offset from struct base to local rbp value
  assert(ebp_off + wordSize*frame::interpreter_frame_method_offset      == saved_args_base_offset_in_bytes(), "");
  assert(ebp_off + wordSize*frame::interpreter_frame_last_sp_offset     == conversion_offset_in_bytes(), "");
  assert(ebp_off + wordSize*frame::interpreter_frame_sender_sp_offset   == exact_sender_sp_offset_in_bytes(), "");
  // These last two have to be exact:
  assert(ebp_off + wordSize*frame::link_offset                          == sender_link_offset_in_bytes(), "");
  assert(ebp_off + wordSize*frame::return_addr_offset                   == sender_pc_offset_in_bytes(), "");
}

void MethodHandles::RicochetFrame::verify() const {
  verify_offsets();
  assert(magic_number_1() == MAGIC_NUMBER_1, "");
  assert(magic_number_2() == MAGIC_NUMBER_2, "");
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
#endif //PRODUCT

#ifdef ASSERT
void MethodHandles::verify_argslot(MacroAssembler* _masm,
                                   Register argslot_reg,
                                   const char* error_message) {
  // Verify that argslot lies within (rsp, rbp].
  Label L_ok, L_bad;
  BLOCK_COMMENT("verify_argslot {");
  __ cmpptr(argslot_reg, rbp);
  __ jccb(Assembler::above, L_bad);
  __ cmpptr(rsp, argslot_reg);
  __ jccb(Assembler::below, L_ok);
  __ bind(L_bad);
  __ stop(error_message);
  __ BIND(L_ok);
  BLOCK_COMMENT("} verify_argslot");
}

void MethodHandles::verify_argslots(MacroAssembler* _masm,
                                    RegisterOrConstant arg_slots,
                                    Register arg_slot_base_reg,
                                    bool negate_argslots,
                                    const char* error_message) {
  // Verify that [argslot..argslot+size) lies within (rsp, rbp).
  Label L_ok, L_bad;
  Register rdi_temp = rdi;
  BLOCK_COMMENT("verify_argslots {");
  __ push(rdi_temp);
  if (negate_argslots) {
    if (arg_slots.is_constant()) {
      arg_slots = -1 * arg_slots.as_constant();
    } else {
      __ movptr(rdi_temp, arg_slots);
      __ negptr(rdi_temp);
      arg_slots = rdi_temp;
    }
  }
  __ lea(rdi_temp, Address(arg_slot_base_reg, arg_slots, Interpreter::stackElementScale()));
  __ cmpptr(rdi_temp, rbp);
  __ pop(rdi_temp);
  __ jcc(Assembler::above, L_bad);
  __ cmpptr(rsp, arg_slot_base_reg);
  __ jcc(Assembler::below, L_ok);
  __ bind(L_bad);
  __ stop(error_message);
  __ BIND(L_ok);
  BLOCK_COMMENT("} verify_argslots");
}

// Make sure that arg_slots has the same sign as the given direction.
// If (and only if) arg_slots is a assembly-time constant, also allow it to be zero.
void MethodHandles::verify_stack_move(MacroAssembler* _masm,
                                      RegisterOrConstant arg_slots, int direction) {
  bool allow_zero = arg_slots.is_constant();
  if (direction == 0) { direction = +1; allow_zero = true; }
  assert(stack_move_unit() == -1, "else add extra checks here");
  if (arg_slots.is_register()) {
    Label L_ok, L_bad;
    BLOCK_COMMENT("verify_stack_move {");
    // testl(arg_slots.as_register(), -stack_move_unit() - 1);  // no need
    // jcc(Assembler::notZero, L_bad);
    __ cmpptr(arg_slots.as_register(), (int32_t) NULL_WORD);
    if (direction > 0) {
      __ jcc(allow_zero ? Assembler::less : Assembler::lessEqual, L_bad);
      __ cmpptr(arg_slots.as_register(), (int32_t) UNREASONABLE_STACK_MOVE);
      __ jcc(Assembler::less, L_ok);
    } else {
      __ jcc(allow_zero ? Assembler::greater : Assembler::greaterEqual, L_bad);
      __ cmpptr(arg_slots.as_register(), (int32_t) -UNREASONABLE_STACK_MOVE);
      __ jcc(Assembler::greater, L_ok);
    }
    __ bind(L_bad);
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
                                 Register obj, KlassHandle klass,
                                 const char* error_message) {
  oop* klass_addr = klass.raw_value();
  assert(klass_addr >= SystemDictionaryHandles::Object_klass().raw_value() &&
         klass_addr <= SystemDictionaryHandles::Long_klass().raw_value(),
         "must be one of the SystemDictionaryHandles");
  Register temp = rdi;
  Label L_ok, L_bad;
  BLOCK_COMMENT("verify_klass {");
  __ verify_oop(obj);
  __ testptr(obj, obj);
  __ jcc(Assembler::zero, L_bad);
  __ push(temp);
  __ load_klass(temp, obj);
  __ cmpptr(temp, ExternalAddress((address) klass_addr));
  __ jcc(Assembler::equal, L_ok);
  intptr_t super_check_offset = klass->super_check_offset();
  __ movptr(temp, Address(temp, super_check_offset));
  __ cmpptr(temp, ExternalAddress((address) klass_addr));
  __ jcc(Assembler::equal, L_ok);
  __ pop(temp);
  __ bind(L_bad);
  __ stop(error_message);
  __ BIND(L_ok);
  __ pop(temp);
  BLOCK_COMMENT("} verify_klass");
}
#endif //ASSERT

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
  // FIXME: Interpreter should transmit pre-popped stack pointer, to locate base of arg list.
  // This would simplify several touchy bits of code.
  // See 6984712: JSR 292 method handle calls need a clean argument base pointer
  {
    Register tem = rbx_method;
    for (jint* pchase = methodOopDesc::method_type_offsets_chain(); (*pchase) != -1; pchase++) {
      __ movptr(rax_mtype, Address(tem, *pchase));
      tem = rax_mtype;          // in case there is another indirection
    }
  }

  // given the MethodType, find out where the MH argument is buried
  __ load_heap_oop(rdx_temp, Address(rax_mtype, __ delayed_value(java_lang_invoke_MethodType::form_offset_in_bytes, rdi_temp)));
  Register rdx_vmslots = rdx_temp;
  __ movl(rdx_vmslots, Address(rdx_temp, __ delayed_value(java_lang_invoke_MethodTypeForm::vmslots_offset_in_bytes, rdi_temp)));
  Address mh_receiver_slot_addr = __ argument_address(rdx_vmslots);
  __ movptr(rcx_recv, mh_receiver_slot_addr);

  trace_method_handle(_masm, "invokeExact");

  __ check_method_handle_type(rax_mtype, rcx_recv, rdi_temp, wrong_method_type);

  // Nobody uses the MH receiver slot after this.  Make sure.
  DEBUG_ONLY(__ movptr(mh_receiver_slot_addr, (int32_t)0x999999));

  __ jump_to_method_handle_entry(rcx_recv, rdi_temp);

  // for invokeGeneric (only), apply argument and result conversions on the fly
  __ bind(invoke_generic_slow_path);
#ifdef ASSERT
  if (VerifyMethodHandles) {
    Label L;
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
  insert_arg_slots(_masm, 2 * stack_move_unit(),
                   rcx_argslot, rbx_temp, rdx_temp);

  // load up an adapter from the calling type (Java weaves this)
  __ load_heap_oop(rdx_temp, Address(rax_mtype, __ delayed_value(java_lang_invoke_MethodType::form_offset_in_bytes, rdi_temp)));
  Register rdx_adapter = rdx_temp;
  // __ load_heap_oop(rdx_adapter, Address(rdx_temp, java_lang_invoke_MethodTypeForm::genericInvoker_offset_in_bytes()));
  // deal with old JDK versions:
  __ lea(rdi_temp, Address(rdx_temp, __ delayed_value(java_lang_invoke_MethodTypeForm::genericInvoker_offset_in_bytes, rdi_temp)));
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

// Workaround for C++ overloading nastiness on '0' for RegisterOrConstant.
static RegisterOrConstant constant(int value) {
  return RegisterOrConstant(value);
}

// Helper to insert argument slots into the stack.
// arg_slots must be a multiple of stack_move_unit() and < 0
// rax_argslot is decremented to point to the new (shifted) location of the argslot
// But, rdx_temp ends up holding the original value of rax_argslot.
void MethodHandles::insert_arg_slots(MacroAssembler* _masm,
                                     RegisterOrConstant arg_slots,
                                     Register rax_argslot,
                                     Register rbx_temp, Register rdx_temp) {
  // allow constant zero
  if (arg_slots.is_constant() && arg_slots.as_constant() == 0)
    return;
  assert_different_registers(rax_argslot, rbx_temp, rdx_temp,
                             (!arg_slots.is_register() ? rsp : arg_slots.as_register()));
  if (VerifyMethodHandles)
    verify_argslot(_masm, rax_argslot, "insertion point must fall within current frame");
  if (VerifyMethodHandles)
    verify_stack_move(_masm, arg_slots, -1);

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
  __ lea(rsp, Address(rsp, arg_slots, Interpreter::stackElementScale()));
  {
    Label loop;
    __ BIND(loop);
    // pull one word down each time through the loop
    __ movptr(rbx_temp, Address(rdx_temp, 0));
    __ movptr(Address(rdx_temp, arg_slots, Interpreter::stackElementScale()), rbx_temp);
    __ addptr(rdx_temp, wordSize);
    __ cmpptr(rdx_temp, rax_argslot);
    __ jcc(Assembler::less, loop);
  }

  // Now move the argslot down, to point to the opened-up space.
  __ lea(rax_argslot, Address(rax_argslot, arg_slots, Interpreter::stackElementScale()));
  BLOCK_COMMENT("} insert_arg_slots");
}

// Helper to remove argument slots from the stack.
// arg_slots must be a multiple of stack_move_unit() and > 0
void MethodHandles::remove_arg_slots(MacroAssembler* _masm,
                                     RegisterOrConstant arg_slots,
                                     Register rax_argslot,
                                     Register rbx_temp, Register rdx_temp) {
  // allow constant zero
  if (arg_slots.is_constant() && arg_slots.as_constant() == 0)
    return;
  assert_different_registers(rax_argslot, rbx_temp, rdx_temp,
                             (!arg_slots.is_register() ? rsp : arg_slots.as_register()));
  if (VerifyMethodHandles)
    verify_argslots(_masm, arg_slots, rax_argslot, false,
                    "deleted argument(s) must fall within current frame");
  if (VerifyMethodHandles)
    verify_stack_move(_masm, arg_slots, +1);

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
    __ movptr(Address(rdx_temp, arg_slots, Interpreter::stackElementScale()), rbx_temp);
    __ addptr(rdx_temp, -wordSize);
    __ cmpptr(rdx_temp, rsp);
    __ jcc(Assembler::greaterEqual, loop);
  }

  // Now move the argslot up, to point to the just-copied block.
  __ lea(rsp, Address(rsp, arg_slots, Interpreter::stackElementScale()));
  // And adjust the argslot address to point at the deletion point.
  __ lea(rax_argslot, Address(rax_argslot, arg_slots, Interpreter::stackElementScale()));
  BLOCK_COMMENT("} remove_arg_slots");
}

// Helper to copy argument slots to the top of the stack.
// The sequence starts with rax_argslot and is counted by slot_count
// slot_count must be a multiple of stack_move_unit() and >= 0
// This function blows the temps but does not change rax_argslot.
void MethodHandles::push_arg_slots(MacroAssembler* _masm,
                                   Register rax_argslot,
                                   RegisterOrConstant slot_count,
                                   int skip_words_count,
                                   Register rbx_temp, Register rdx_temp) {
  assert_different_registers(rax_argslot, rbx_temp, rdx_temp,
                             (!slot_count.is_register() ? rbp : slot_count.as_register()),
                             rsp);
  assert(Interpreter::stackElementSize == wordSize, "else change this code");

  if (VerifyMethodHandles)
    verify_stack_move(_masm, slot_count, 0);

  // allow constant zero
  if (slot_count.is_constant() && slot_count.as_constant() == 0)
    return;

  BLOCK_COMMENT("push_arg_slots {");

  Register rbx_top = rbx_temp;

  // There is at most 1 word to carry down with the TOS.
  switch (skip_words_count) {
  case 1: __ pop(rdx_temp); break;
  case 0:                   break;
  default: ShouldNotReachHere();
  }

  if (slot_count.is_constant()) {
    for (int i = slot_count.as_constant() - 1; i >= 0; i--) {
      __ pushptr(Address(rax_argslot, i * wordSize));
    }
  } else {
    Label L_plural, L_loop, L_break;
    // Emit code to dynamically check for the common cases, zero and one slot.
    __ cmpl(slot_count.as_register(), (int32_t) 1);
    __ jccb(Assembler::greater, L_plural);
    __ jccb(Assembler::less, L_break);
    __ pushptr(Address(rax_argslot, 0));
    __ jmpb(L_break);
    __ BIND(L_plural);

    // Loop for 2 or more:
    //   rbx = &rax[slot_count]
    //   while (rbx > rax)  *(--rsp) = *(--rbx)
    __ lea(rbx_top, Address(rax_argslot, slot_count, Address::times_ptr));
    __ BIND(L_loop);
    __ subptr(rbx_top, wordSize);
    __ pushptr(Address(rbx_top, 0));
    __ cmpptr(rbx_top, rax_argslot);
    __ jcc(Assembler::above, L_loop);
    __ bind(L_break);
  }
  switch (skip_words_count) {
  case 1: __ push(rdx_temp); break;
  case 0:                    break;
  default: ShouldNotReachHere();
  }
  BLOCK_COMMENT("} push_arg_slots");
}

// in-place movement; no change to rsp
// blows rax_temp, rdx_temp
void MethodHandles::move_arg_slots_up(MacroAssembler* _masm,
                                      Register rbx_bottom,  // invariant
                                      Address  top_addr,     // can use rax_temp
                                      RegisterOrConstant positive_distance_in_slots,
                                      Register rax_temp, Register rdx_temp) {
  BLOCK_COMMENT("move_arg_slots_up {");
  assert_different_registers(rbx_bottom,
                             rax_temp, rdx_temp,
                             positive_distance_in_slots.register_or_noreg());
  Label L_loop, L_break;
  Register rax_top = rax_temp;
  if (!top_addr.is_same_address(Address(rax_top, 0)))
    __ lea(rax_top, top_addr);
  // Detect empty (or broken) loop:
#ifdef ASSERT
  if (VerifyMethodHandles) {
    // Verify that &bottom < &top (non-empty interval)
    Label L_ok, L_bad;
    if (positive_distance_in_slots.is_register()) {
      __ cmpptr(positive_distance_in_slots.as_register(), (int32_t) 0);
      __ jcc(Assembler::lessEqual, L_bad);
    }
    __ cmpptr(rbx_bottom, rax_top);
    __ jcc(Assembler::below, L_ok);
    __ bind(L_bad);
    __ stop("valid bounds (copy up)");
    __ BIND(L_ok);
  }
#endif
  __ cmpptr(rbx_bottom, rax_top);
  __ jccb(Assembler::aboveEqual, L_break);
  // work rax down to rbx, copying contiguous data upwards
  // In pseudo-code:
  //   [rbx, rax) = &[bottom, top)
  //   while (--rax >= rbx) *(rax + distance) = *(rax + 0), rax--;
  __ BIND(L_loop);
  __ subptr(rax_top, wordSize);
  __ movptr(rdx_temp, Address(rax_top, 0));
  __ movptr(          Address(rax_top, positive_distance_in_slots, Address::times_ptr), rdx_temp);
  __ cmpptr(rax_top, rbx_bottom);
  __ jcc(Assembler::above, L_loop);
  assert(Interpreter::stackElementSize == wordSize, "else change loop");
  __ bind(L_break);
  BLOCK_COMMENT("} move_arg_slots_up");
}

// in-place movement; no change to rsp
// blows rax_temp, rdx_temp
void MethodHandles::move_arg_slots_down(MacroAssembler* _masm,
                                        Address  bottom_addr,  // can use rax_temp
                                        Register rbx_top,      // invariant
                                        RegisterOrConstant negative_distance_in_slots,
                                        Register rax_temp, Register rdx_temp) {
  BLOCK_COMMENT("move_arg_slots_down {");
  assert_different_registers(rbx_top,
                             negative_distance_in_slots.register_or_noreg(),
                             rax_temp, rdx_temp);
  Label L_loop, L_break;
  Register rax_bottom = rax_temp;
  if (!bottom_addr.is_same_address(Address(rax_bottom, 0)))
    __ lea(rax_bottom, bottom_addr);
  // Detect empty (or broken) loop:
#ifdef ASSERT
  assert(!negative_distance_in_slots.is_constant() || negative_distance_in_slots.as_constant() < 0, "");
  if (VerifyMethodHandles) {
    // Verify that &bottom < &top (non-empty interval)
    Label L_ok, L_bad;
    if (negative_distance_in_slots.is_register()) {
      __ cmpptr(negative_distance_in_slots.as_register(), (int32_t) 0);
      __ jcc(Assembler::greaterEqual, L_bad);
    }
    __ cmpptr(rax_bottom, rbx_top);
    __ jcc(Assembler::below, L_ok);
    __ bind(L_bad);
    __ stop("valid bounds (copy down)");
    __ BIND(L_ok);
  }
#endif
  __ cmpptr(rax_bottom, rbx_top);
  __ jccb(Assembler::aboveEqual, L_break);
  // work rax up to rbx, copying contiguous data downwards
  // In pseudo-code:
  //   [rax, rbx) = &[bottom, top)
  //   while (rax < rbx) *(rax - distance) = *(rax + 0), rax++;
  __ BIND(L_loop);
  __ movptr(rdx_temp, Address(rax_bottom, 0));
  __ movptr(          Address(rax_bottom, negative_distance_in_slots, Address::times_ptr), rdx_temp);
  __ addptr(rax_bottom, wordSize);
  __ cmpptr(rax_bottom, rbx_top);
  __ jcc(Assembler::below, L_loop);
  assert(Interpreter::stackElementSize == wordSize, "else change loop");
  __ bind(L_break);
  BLOCK_COMMENT("} move_arg_slots_down");
}

// Copy from a field or array element to a stacked argument slot.
// is_element (ignored) says whether caller is loading an array element instead of an instance field.
void MethodHandles::move_typed_arg(MacroAssembler* _masm,
                                   BasicType type, bool is_element,
                                   Address slot_dest, Address value_src,
                                   Register rbx_temp, Register rdx_temp) {
  BLOCK_COMMENT(!is_element ? "move_typed_arg {" : "move_typed_arg { (array element)");
  if (type == T_OBJECT || type == T_ARRAY) {
    __ load_heap_oop(rbx_temp, value_src);
    __ movptr(slot_dest, rbx_temp);
  } else if (type != T_VOID) {
    int  arg_size      = type2aelembytes(type);
    bool arg_is_signed = is_signed_subword_type(type);
    int  slot_size     = (arg_size > wordSize) ? arg_size : wordSize;
    __ load_sized_value(  rdx_temp,  value_src, arg_size, arg_is_signed, rbx_temp);
    __ store_sized_value( slot_dest, rdx_temp,  slot_size,               rbx_temp);
  }
  BLOCK_COMMENT("} move_typed_arg");
}

void MethodHandles::move_return_value(MacroAssembler* _masm, BasicType type,
                                      Address return_slot) {
  BLOCK_COMMENT("move_return_value {");
  // Old versions of the JVM must clean the FPU stack after every return.
#ifndef _LP64
#ifdef COMPILER2
  // The FPU stack is clean if UseSSE >= 2 but must be cleaned in other cases
  if ((type == T_FLOAT && UseSSE < 1) || (type == T_DOUBLE && UseSSE < 2)) {
    for (int i = 1; i < 8; i++) {
        __ ffree(i);
    }
  } else if (UseSSE < 2) {
    __ empty_FPU_stack();
  }
#endif //COMPILER2
#endif //!_LP64

  // Look at the type and pull the value out of the corresponding register.
  if (type == T_VOID) {
    // nothing to do
  } else if (type == T_OBJECT) {
    __ movptr(return_slot, rax);
  } else if (type == T_INT || is_subword_type(type)) {
    // write the whole word, even if only 32 bits is significant
    __ movptr(return_slot, rax);
  } else if (type == T_LONG) {
    // store the value by parts
    // Note: We assume longs are continguous (if misaligned) on the interpreter stack.
    __ store_sized_value(return_slot, rax, BytesPerLong, rdx);
  } else if (NOT_LP64((type == T_FLOAT  && UseSSE < 1) ||
                      (type == T_DOUBLE && UseSSE < 2) ||)
             false) {
    // Use old x86 FPU registers:
    if (type == T_FLOAT)
      __ fstp_s(return_slot);
    else
      __ fstp_d(return_slot);
  } else if (type == T_FLOAT) {
    __ movflt(return_slot, xmm0);
  } else if (type == T_DOUBLE) {
    __ movdbl(return_slot, xmm0);
  } else {
    ShouldNotReachHere();
  }
  BLOCK_COMMENT("} move_return_value");
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
  tty->print_cr("MH %s mh="INTPTR_FORMAT" sp=("INTPTR_FORMAT"+"INTX_FORMAT") stack_size="INTX_FORMAT" bp="INTPTR_FORMAT,
                adaptername, (intptr_t)mh, (intptr_t)entry_sp, (intptr_t)(saved_sp - entry_sp), (intptr_t)(base_sp - last_sp), (intptr_t)saved_bp);
  if (last_sp != saved_sp && last_sp != NULL)
    tty->print_cr("*** last_sp="INTPTR_FORMAT, (intptr_t)last_sp);
  if (Verbose) {
    tty->print(" reg dump: ");
    int saved_regs_count = (entry_sp-1) - saved_regs;
    // 32 bit: rdi rsi rbp rsp; rbx rdx rcx (*) rax
    int i;
    for (i = 0; i <= saved_regs_count; i++) {
      if (i > 0 && i % 4 == 0 && i != saved_regs_count) {
        tty->cr();
        tty->print("   + dump: ");
      }
      tty->print(" %d: "INTPTR_FORMAT, i, saved_regs[i]);
    }
    tty->cr();
    int stack_dump_count = 16;
    if (stack_dump_count < (int)(saved_bp + 2 - saved_sp))
      stack_dump_count = (int)(saved_bp + 2 - saved_sp);
    if (stack_dump_count > 64)  stack_dump_count = 48;
    for (i = 0; i < stack_dump_count; i += 4) {
      tty->print_cr(" dump at SP[%d] "INTPTR_FORMAT": "INTPTR_FORMAT" "INTPTR_FORMAT" "INTPTR_FORMAT" "INTPTR_FORMAT,
                    i, (intptr_t) &entry_sp[i+0], entry_sp[i+0], entry_sp[i+1], entry_sp[i+2], entry_sp[i+3]);
    }
    print_method_handle(mh);
  }
}

// The stub wraps the arguments in a struct on the stack to avoid
// dealing with the different calling conventions for passing 6
// arguments.
struct MethodHandleStubArguments {
  const char* adaptername;
  oopDesc* mh;
  intptr_t* saved_regs;
  intptr_t* entry_sp;
  intptr_t* saved_sp;
  intptr_t* saved_bp;
};
void trace_method_handle_stub_wrapper(MethodHandleStubArguments* args) {
  trace_method_handle_stub(args->adaptername,
                           args->mh,
                           args->saved_regs,
                           args->entry_sp,
                           args->saved_sp,
                           args->saved_bp);
}

void MethodHandles::trace_method_handle(MacroAssembler* _masm, const char* adaptername) {
  if (!TraceMethodHandles)  return;
  BLOCK_COMMENT("trace_method_handle {");
  __ push(rax);
  __ lea(rax, Address(rsp, wordSize * NOT_LP64(6) LP64_ONLY(14))); // entry_sp  __ pusha();
  __ pusha();
  __ mov(rbx, rsp);
  __ enter();
  // incoming state:
  // rcx: method handle
  // r13 or rsi: saved sp
  // To avoid calling convention issues, build a record on the stack and pass the pointer to that instead.
  __ push(rbp);               // saved_bp
  __ push(rsi);               // saved_sp
  __ push(rax);               // entry_sp
  __ push(rbx);               // pusha saved_regs
  __ push(rcx);               // mh
  __ push(rcx);               // adaptername
  __ movptr(Address(rsp, 0), (intptr_t) adaptername);
  __ super_call_VM_leaf(CAST_FROM_FN_PTR(address, trace_method_handle_stub_wrapper), rsp);
  __ leave();
  __ popa();
  __ pop(rax);
  BLOCK_COMMENT("} trace_method_handle");
}
#endif //PRODUCT

// which conversion op types are implemented here?
int MethodHandles::adapter_conversion_ops_supported_mask() {
  return ((1<<java_lang_invoke_AdapterMethodHandle::OP_RETYPE_ONLY)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_RETYPE_RAW)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_CHECK_CAST)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_PRIM)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_REF_TO_PRIM)
          //OP_PRIM_TO_REF is below...
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_SWAP_ARGS)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_ROT_ARGS)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_DUP_ARGS)
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_DROP_ARGS)
          //OP_COLLECT_ARGS is below...
         |(1<<java_lang_invoke_AdapterMethodHandle::OP_SPREAD_ARGS)
         |(!UseRicochetFrames ? 0 :
           LP64_ONLY(FLAG_IS_DEFAULT(UseRicochetFrames) ? 0 :)
           java_lang_invoke_MethodTypeForm::vmlayout_offset_in_bytes() <= 0 ? 0 :
           ((1<<java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_REF)
           |(1<<java_lang_invoke_AdapterMethodHandle::OP_COLLECT_ARGS)
           |(1<<java_lang_invoke_AdapterMethodHandle::OP_FOLD_ARGS)
            ))
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
  // - rbx: garbage temp (was MethodHandle.invoke methodOop, unused)
  // - rcx: receiver method handle
  // - rax: method handle type (only used by the check_mtype entry point)
  // - rsi/r13: sender SP (must preserve; see prepare_to_jump_from_interpreted)
  // - rdx: garbage temp, can blow away

  const Register rcx_recv    = rcx;
  const Register rax_argslot = rax;
  const Register rbx_temp    = rbx;
  const Register rdx_temp    = rdx;
  const Register rdi_temp    = rdi;

  // This guy is set up by prepare_to_jump_from_interpreted (from interpreted calls)
  // and gen_c2i_adapter (from compiled calls):
  const Register saved_last_sp = saved_last_sp_register();

  // Argument registers for _raise_exception.
  // 32-bit: Pass first two oop/int args in registers ECX and EDX.
  const Register rarg0_code     = LP64_ONLY(j_rarg0) NOT_LP64(rcx);
  const Register rarg1_actual   = LP64_ONLY(j_rarg1) NOT_LP64(rdx);
  const Register rarg2_required = LP64_ONLY(j_rarg2) NOT_LP64(rdi);
  assert_different_registers(rarg0_code, rarg1_actual, rarg2_required, saved_last_sp);

  guarantee(java_lang_invoke_MethodHandle::vmentry_offset_in_bytes() != 0, "must have offsets");

  // some handy addresses
  Address rbx_method_fie(     rbx,      methodOopDesc::from_interpreted_offset() );
  Address rbx_method_fce(     rbx,      methodOopDesc::from_compiled_offset() );

  Address rcx_mh_vmtarget(    rcx_recv, java_lang_invoke_MethodHandle::vmtarget_offset_in_bytes() );
  Address rcx_dmh_vmindex(    rcx_recv, java_lang_invoke_DirectMethodHandle::vmindex_offset_in_bytes() );

  Address rcx_bmh_vmargslot(  rcx_recv, java_lang_invoke_BoundMethodHandle::vmargslot_offset_in_bytes() );
  Address rcx_bmh_argument(   rcx_recv, java_lang_invoke_BoundMethodHandle::argument_offset_in_bytes() );

  Address rcx_amh_vmargslot(  rcx_recv, java_lang_invoke_AdapterMethodHandle::vmargslot_offset_in_bytes() );
  Address rcx_amh_argument(   rcx_recv, java_lang_invoke_AdapterMethodHandle::argument_offset_in_bytes() );
  Address rcx_amh_conversion( rcx_recv, java_lang_invoke_AdapterMethodHandle::conversion_offset_in_bytes() );
  Address vmarg;                // __ argument_address(vmargslot)

  const int java_mirror_offset = klassOopDesc::klass_part_offset_in_bytes() + Klass::java_mirror_offset_in_bytes();

  if (have_entry(ek)) {
    __ nop();                   // empty stubs make SG sick
    return;
  }

#ifdef ASSERT
  __ push((int32_t) 0xEEEEEEEE);
  __ push((int32_t) (intptr_t) entry_name(ek));
  LP64_ONLY(__ push((int32_t) high((intptr_t) entry_name(ek))));
  __ push((int32_t) 0x33333333);
#endif //ASSERT

  address interp_entry = __ pc();

  trace_method_handle(_masm, entry_name(ek));

  BLOCK_COMMENT(entry_name(ek));

  switch ((int) ek) {
  case _raise_exception:
    {
      // Not a real MH entry, but rather shared code for raising an
      // exception.  Since we use the compiled entry, arguments are
      // expected in compiler argument registers.
      assert(raise_exception_method(), "must be set");
      assert(raise_exception_method()->from_compiled_entry(), "method must be linked");

      const Register rdi_pc = rax;
      __ pop(rdi_pc);  // caller PC
      __ mov(rsp, saved_last_sp);  // cut the stack back to where the caller started

      Register rbx_method = rbx_temp;
      Label L_no_method;
      // FIXME: fill in _raise_exception_method with a suitable java.lang.invoke method
      __ movptr(rbx_method, ExternalAddress((address) &_raise_exception_method));
      __ testptr(rbx_method, rbx_method);
      __ jccb(Assembler::zero, L_no_method);

      const int jobject_oop_offset = 0;
      __ movptr(rbx_method, Address(rbx_method, jobject_oop_offset));  // dereference the jobject
      __ testptr(rbx_method, rbx_method);
      __ jccb(Assembler::zero, L_no_method);
      __ verify_oop(rbx_method);

      NOT_LP64(__ push(rarg2_required));
      __ push(rdi_pc);         // restore caller PC
      __ jmp(rbx_method_fce);  // jump to compiled entry

      // Do something that is at least causes a valid throw from the interpreter.
      __ bind(L_no_method);
      __ push(rarg2_required);
      __ push(rarg1_actual);
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
      __ mov(rbx_temp, rcx_recv);  // rarg2_required might be RCX
      assert_different_registers(rarg2_required, rbx_temp);
      __ movptr(rarg2_required, Address(rdx_intf, java_mirror_offset));  // required interface
      __ mov(   rarg1_actual,   rbx_temp);                               // bad receiver
      __ movl(  rarg0_code,     (int) Bytecodes::_invokeinterface);      // who is complaining?
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
      BasicType arg_type  = ek_bound_mh_arg_type(ek);
      int       arg_slots = type2size[arg_type];

      // make room for the new argument:
      __ movl(rax_argslot, rcx_bmh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot));

      insert_arg_slots(_masm, arg_slots * stack_move_unit(), rax_argslot, rbx_temp, rdx_temp);

      // store bound argument into the new stack slot:
      __ load_heap_oop(rbx_temp, rcx_bmh_argument);
      if (arg_type == T_OBJECT) {
        __ movptr(Address(rax_argslot, 0), rbx_temp);
      } else {
        Address prim_value_addr(rbx_temp, java_lang_boxing_object::value_offset_in_bytes(arg_type));
        move_typed_arg(_masm, arg_type, false,
                       Address(rax_argslot, 0),
                       prim_value_addr,
                       rbx_temp, rdx_temp);
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
      load_klass_from_Class(_masm, rbx_klass);

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

      assert_different_registers(rarg2_required, rdx_temp);
      __ load_heap_oop(rarg2_required, rcx_amh_argument);             // required class
      __ mov(          rarg1_actual,   rdx_temp);                     // bad object
      __ movl(         rarg0_code,     (int) Bytecodes::_checkcast);  // who is complaining?
      __ jump(ExternalAddress(from_interpreted_entry(_raise_exception)));

      __ bind(done);
      // get the new MH:
      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_prim_to_prim:
  case _adapter_ref_to_prim:
  case _adapter_prim_to_ref:
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
      load_conversion_vminfo(_masm, rbx_vminfo, rcx_amh_conversion);

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
      insert_arg_slots(_masm, stack_move_unit(),
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
        insert_arg_slots(_masm, stack_move_unit(),
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

      // 'argslot' is the position of the first argument to swap
      __ movl(rax_argslot, rcx_amh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot));

      // 'vminfo' is the second
      Register rbx_destslot = rbx_temp;
      load_conversion_vminfo(_masm, rbx_destslot, rcx_amh_conversion);
      __ lea(rbx_destslot, __ argument_address(rbx_destslot));
      if (VerifyMethodHandles)
        verify_argslot(_masm, rbx_destslot, "swap point must fall within current frame");

      assert(Interpreter::stackElementSize == wordSize, "else rethink use of wordSize here");
      if (!rotate) {
        // simple swap
        for (int i = 0; i < swap_slots; i++) {
          __ movptr(rdi_temp, Address(rax_argslot,  i * wordSize));
          __ movptr(rdx_temp, Address(rbx_destslot, i * wordSize));
          __ movptr(Address(rax_argslot,  i * wordSize), rdx_temp);
          __ movptr(Address(rbx_destslot, i * wordSize), rdi_temp);
        }
      } else {
        // A rotate is actually pair of moves, with an "odd slot" (or pair)
        // changing place with a series of other slots.
        // First, push the "odd slot", which is going to get overwritten
        for (int i = swap_slots - 1; i >= 0; i--) {
          // handle one with rdi_temp instead of a push:
          if (i == 0)  __ movptr(rdi_temp, Address(rax_argslot, i * wordSize));
          else         __ pushptr(         Address(rax_argslot, i * wordSize));
        }
        if (rotate > 0) {
          // Here is rotate > 0:
          // (low mem)                                          (high mem)
          //     | dest:     more_slots...     | arg: odd_slot :arg+1 |
          // =>
          //     | dest: odd_slot | dest+1: more_slots...      :arg+1 |
          // work argslot down to destslot, copying contiguous data upwards
          // pseudo-code:
          //   rax = src_addr - swap_bytes
          //   rbx = dest_addr
          //   while (rax >= rbx) *(rax + swap_bytes) = *(rax + 0), rax--;
          move_arg_slots_up(_masm,
                            rbx_destslot,
                            Address(rax_argslot, 0),
                            swap_slots,
                            rax_argslot, rdx_temp);
        } else {
          // Here is the other direction, rotate < 0:
          // (low mem)                                          (high mem)
          //     | arg: odd_slot | arg+1: more_slots...       :dest+1 |
          // =>
          //     | arg:    more_slots...     | dest: odd_slot :dest+1 |
          // work argslot up to destslot, copying contiguous data downwards
          // pseudo-code:
          //   rax = src_addr + swap_bytes
          //   rbx = dest_addr
          //   while (rax <= rbx) *(rax - swap_bytes) = *(rax + 0), rax++;
          __ addptr(rbx_destslot, wordSize);
          move_arg_slots_down(_masm,
                              Address(rax_argslot, swap_slots * wordSize),
                              rbx_destslot,
                              -swap_slots,
                              rax_argslot, rdx_temp);

          __ subptr(rbx_destslot, wordSize);
        }
        // pop the original first chunk into the destination slot, now free
        for (int i = 0; i < swap_slots; i++) {
          if (i == 0)  __ movptr(Address(rbx_destslot, i * wordSize), rdi_temp);
          else         __ popptr(Address(rbx_destslot, i * wordSize));
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
      Register rdi_stack_move = rdi_temp;
      load_stack_move(_masm, rdi_stack_move, rcx_recv, true);

      if (VerifyMethodHandles) {
        verify_argslots(_masm, rdi_stack_move, rax_argslot, true,
                        "copied argument(s) must fall within current frame");
      }

      // insert location is always the bottom of the argument list:
      Address insert_location = __ argument_address(constant(0));
      int pre_arg_words = insert_location.disp() / wordSize;   // return PC is pushed
      assert(insert_location.base() == rsp, "");

      __ negl(rdi_stack_move);
      push_arg_slots(_masm, rax_argslot, rdi_stack_move,
                     pre_arg_words, rbx_temp, rdx_temp);

      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_drop_args:
    {
      // 'argslot' is the position of the first argument to nuke
      __ movl(rax_argslot, rcx_amh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot));

      // (must do previous push after argslot address is taken)

      // 'stack_move' is number of words to drop
      Register rdi_stack_move = rdi_temp;
      load_stack_move(_masm, rdi_stack_move, rcx_recv, false);
      remove_arg_slots(_masm, rdi_stack_move,
                       rax_argslot, rbx_temp, rdx_temp);

      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
    }
    break;

  case _adapter_collect_args:
  case _adapter_fold_args:
  case _adapter_spread_args:
    // handled completely by optimized cases
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
      // On entry, TOS points at a return PC, and RBP is the callers frame ptr.
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

      // Already pushed:  ... keep1 | collect | keep2 | sender_pc |
      // push(sender_pc);

      // Compute argument base:
      Register rax_argv = rax_argslot;
      __ lea(rax_argv, __ argument_address(constant(0)));

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
        DEBUG_ONLY(extra_slots += 1);
        if (extra_slots > 0) {
          __ pop(rbx_temp);   // return value
          __ subptr(rsp, (extra_slots * Interpreter::stackElementSize));
          // Push guard word #2 in debug mode.
          DEBUG_ONLY(__ movptr(Address(rsp, 0), (int32_t) RicochetFrame::MAGIC_NUMBER_2));
          __ push(rbx_temp);
        }
      }

      RicochetFrame::enter_ricochet_frame(_masm, rcx_recv, rax_argv,
                                          entry(ek_ret)->from_interpreted_entry(), rbx_temp);

      // Now pushed:  ... keep1 | collect | keep2 | RF |
      // some handy frame slots:
      Address exact_sender_sp_addr = RicochetFrame::frame_address(RicochetFrame::exact_sender_sp_offset_in_bytes());
      Address conversion_addr      = RicochetFrame::frame_address(RicochetFrame::conversion_offset_in_bytes());
      Address saved_args_base_addr = RicochetFrame::frame_address(RicochetFrame::saved_args_base_offset_in_bytes());

#ifdef ASSERT
      if (VerifyMethodHandles && dest != T_CONFLICT) {
        BLOCK_COMMENT("verify AMH.conv.dest");
        load_conversion_dest_type(_masm, rbx_temp, conversion_addr);
        Label L_dest_ok;
        __ cmpl(rbx_temp, (int) dest);
        __ jcc(Assembler::equal, L_dest_ok);
        if (dest == T_INT) {
          for (int bt = T_BOOLEAN; bt < T_INT; bt++) {
            if (is_subword_type(BasicType(bt))) {
              __ cmpl(rbx_temp, (int) bt);
              __ jcc(Assembler::equal, L_dest_ok);
            }
          }
        }
        __ stop("bad dest in AMH.conv");
        __ BIND(L_dest_ok);
      }
#endif //ASSERT

      // Find out where the original copy of the recursive argument sequence begins.
      Register rax_coll = rax_argv;
      {
        RegisterOrConstant collect_slot = collect_slot_constant;
        if (collect_slot_constant == -1) {
          __ movl(rdi_temp, rcx_amh_vmargslot);
          collect_slot = rdi_temp;
        }
        if (collect_slot_constant != 0)
          __ lea(rax_coll, Address(rax_argv, collect_slot, Interpreter::stackElementScale()));
        // rax_coll now points at the trailing edge of |collect| and leading edge of |keep2|
      }

      // Replace the old AMH with the recursive MH.  (No going back now.)
      // In the case of a boxing call, the recursive call is to a 'boxer' method,
      // such as Integer.valueOf or Long.valueOf.  In the case of a filter
      // or collect call, it will take one or more arguments, transform them,
      // and return some result, to store back into argument_base[vminfo].
      __ load_heap_oop(rcx_recv, rcx_amh_argument);
      if (VerifyMethodHandles)  verify_method_handle(_masm, rcx_recv);

      // Push a space for the recursively called MH first:
      __ push((int32_t)NULL_WORD);

      // Calculate |collect|, the number of arguments we are collecting.
      Register rdi_collect_count = rdi_temp;
      RegisterOrConstant collect_count;
      if (collect_count_constant >= 0) {
        collect_count = collect_count_constant;
      } else {
        __ load_method_handle_vmslots(rdi_collect_count, rcx_recv, rdx_temp);
        collect_count = rdi_collect_count;
      }
#ifdef ASSERT
      if (VerifyMethodHandles && collect_count_constant >= 0) {
        __ load_method_handle_vmslots(rbx_temp, rcx_recv, rdx_temp);
        Label L_count_ok;
        __ cmpl(rbx_temp, collect_count_constant);
        __ jcc(Assembler::equal, L_count_ok);
        __ stop("bad vminfo in AMH.conv");
        __ BIND(L_count_ok);
      }
#endif //ASSERT

      // copy |collect| slots directly to TOS:
      push_arg_slots(_masm, rax_coll, collect_count, 0, rbx_temp, rdx_temp);
      // Now pushed:  ... keep1 | collect | keep2 | RF... | collect |
      // rax_coll still points at the trailing edge of |collect| and leading edge of |keep2|

      // If necessary, adjust the saved arguments to make room for the eventual return value.
      // Normal adjustment:  ... keep1 | +dest+ | -collect- | keep2 | RF... | collect |
      // If retaining args:  ... keep1 | +dest+ |  collect  | keep2 | RF... | collect |
      // In the non-retaining case, this might move keep2 either up or down.
      // We don't have to copy the whole | RF... collect | complex,
      // but we must adjust RF.saved_args_base.
      // Also, from now on, we will forget about the origial copy of |collect|.
      // If we are retaining it, we will treat it as part of |keep2|.
      // For clarity we will define |keep3| = |collect|keep2| or |keep2|.

      BLOCK_COMMENT("adjust trailing arguments {");
      // Compare the sizes of |+dest+| and |-collect-|, which are opposed opening and closing movements.
      int                open_count  = dest_count;
      RegisterOrConstant close_count = collect_count_constant;
      Register rdi_close_count = rdi_collect_count;
      if (retain_original_args) {
        close_count = constant(0);
      } else if (collect_count_constant == -1) {
        close_count = rdi_collect_count;
      }

      // How many slots need moving?  This is simply dest_slot (0 => no |keep3|).
      RegisterOrConstant keep3_count;
      Register rsi_keep3_count = rsi;  // can repair from RF.exact_sender_sp
      if (dest_slot_constant >= 0) {
        keep3_count = dest_slot_constant;
      } else  {
        load_conversion_vminfo(_masm, rsi_keep3_count, conversion_addr);
        keep3_count = rsi_keep3_count;
      }
#ifdef ASSERT
      if (VerifyMethodHandles && dest_slot_constant >= 0) {
        load_conversion_vminfo(_masm, rbx_temp, conversion_addr);
        Label L_vminfo_ok;
        __ cmpl(rbx_temp, dest_slot_constant);
        __ jcc(Assembler::equal, L_vminfo_ok);
        __ stop("bad vminfo in AMH.conv");
        __ BIND(L_vminfo_ok);
      }
#endif //ASSERT

      // tasks remaining:
      bool move_keep3 = (!keep3_count.is_constant() || keep3_count.as_constant() != 0);
      bool stomp_dest = (NOT_DEBUG(dest == T_OBJECT) DEBUG_ONLY(dest_count != 0));
      bool fix_arg_base = (!close_count.is_constant() || open_count != close_count.as_constant());

      if (stomp_dest | fix_arg_base) {
        // we will probably need an updated rax_argv value
        if (collect_slot_constant >= 0) {
          // rax_coll already holds the leading edge of |keep2|, so tweak it
          assert(rax_coll == rax_argv, "elided a move");
          if (collect_slot_constant != 0)
            __ subptr(rax_argv, collect_slot_constant * Interpreter::stackElementSize);
        } else {
          // Just reload from RF.saved_args_base.
          __ movptr(rax_argv, saved_args_base_addr);
        }
      }

      // Old and new argument locations (based at slot 0).
      // Net shift (&new_argv - &old_argv) is (close_count - open_count).
      bool zero_open_count = (open_count == 0);  // remember this bit of info
      if (move_keep3 && fix_arg_base) {
        // It will be easier t have everything in one register:
        if (close_count.is_register()) {
          // Deduct open_count from close_count register to get a clean +/- value.
          __ subptr(close_count.as_register(), open_count);
        } else {
          close_count = close_count.as_constant() - open_count;
        }
        open_count = 0;
      }
      Address old_argv(rax_argv, 0);
      Address new_argv(rax_argv, close_count,  Interpreter::stackElementScale(),
                                - open_count * Interpreter::stackElementSize);

      // First decide if any actual data are to be moved.
      // We can skip if (a) |keep3| is empty, or (b) the argument list size didn't change.
      // (As it happens, all movements involve an argument list size change.)

      // If there are variable parameters, use dynamic checks to skip around the whole mess.
      Label L_done;
      if (!keep3_count.is_constant()) {
        __ testl(keep3_count.as_register(), keep3_count.as_register());
        __ jcc(Assembler::zero, L_done);
      }
      if (!close_count.is_constant()) {
        __ cmpl(close_count.as_register(), open_count);
        __ jcc(Assembler::equal, L_done);
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
          __ cmpl(close_count.as_register(), open_count);
          __ jcc(Assembler::greater, L_move_up);
        }

        if (emit_move_down) {
          // Move arguments down if |+dest+| > |-collect-|
          // (This is rare, except when arguments are retained.)
          // This opens space for the return value.
          if (keep3_count.is_constant()) {
            for (int i = 0; i < keep3_count.as_constant(); i++) {
              __ movptr(rdx_temp, old_argv.plus_disp(i * Interpreter::stackElementSize));
              __ movptr(          new_argv.plus_disp(i * Interpreter::stackElementSize), rdx_temp);
            }
          } else {
            Register rbx_argv_top = rbx_temp;
            __ lea(rbx_argv_top, old_argv.plus_disp(keep3_count, Interpreter::stackElementScale()));
            move_arg_slots_down(_masm,
                                old_argv,     // beginning of old argv
                                rbx_argv_top, // end of old argv
                                close_count,  // distance to move down (must be negative)
                                rax_argv, rdx_temp);
            // Used argv as an iteration variable; reload from RF.saved_args_base.
            __ movptr(rax_argv, saved_args_base_addr);
          }
        }

        if (emit_guard) {
          __ jmp(L_done);  // assumes emit_move_up is true also
          __ BIND(L_move_up);
        }

        if (emit_move_up) {

          // Move arguments up if |+dest+| < |-collect-|
          // (This is usual, except when |keep3| is empty.)
          // This closes up the space occupied by the now-deleted collect values.
          if (keep3_count.is_constant()) {
            for (int i = keep3_count.as_constant() - 1; i >= 0; i--) {
              __ movptr(rdx_temp, old_argv.plus_disp(i * Interpreter::stackElementSize));
              __ movptr(          new_argv.plus_disp(i * Interpreter::stackElementSize), rdx_temp);
            }
          } else {
            Address argv_top = old_argv.plus_disp(keep3_count, Interpreter::stackElementScale());
            move_arg_slots_up(_masm,
                              rax_argv,     // beginning of old argv
                              argv_top,     // end of old argv
                              close_count,  // distance to move up (must be positive)
                              rbx_temp, rdx_temp);
          }
        }
      }
      __ BIND(L_done);

      if (fix_arg_base) {
        // adjust RF.saved_args_base by adding (close_count - open_count)
        if (!new_argv.is_same_address(Address(rax_argv, 0)))
          __ lea(rax_argv, new_argv);
        __ movptr(saved_args_base_addr, rax_argv);
      }

      if (stomp_dest) {
        // Stomp the return slot, so it doesn't hold garbage.
        // This isn't strictly necessary, but it may help detect bugs.
        int forty_two = RicochetFrame::RETURN_VALUE_PLACEHOLDER;
        __ movptr(Address(rax_argv, keep3_count, Address::times_ptr),
                  (int32_t) forty_two);
        // uses rsi_keep3_count
      }
      BLOCK_COMMENT("} adjust trailing arguments");

      BLOCK_COMMENT("do_recursive_call");
      __ mov(saved_last_sp, rsp);    // set rsi/r13 for callee
      __ pushptr(ExternalAddress(SharedRuntime::ricochet_blob()->bounce_addr()).addr());
      // The globally unique bounce address has two purposes:
      // 1. It helps the JVM recognize this frame (frame::is_ricochet_frame).
      // 2. When returned to, it cuts back the stack and redirects control flow
      //    to the return handler.
      // The return handler will further cut back the stack when it takes
      // down the RF.  Perhaps there is a way to streamline this further.

      // State during recursive call:
      // ... keep1 | dest | dest=42 | keep3 | RF... | collect | bounce_pc |
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);

      break;
    }

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

      Register rbx_arg_base = rbx_temp;
      assert_different_registers(rax, rdx,  // possibly live return value registers
                                 rdi_temp, rbx_arg_base);

      Address conversion_addr      = RicochetFrame::frame_address(RicochetFrame::conversion_offset_in_bytes());
      Address saved_args_base_addr = RicochetFrame::frame_address(RicochetFrame::saved_args_base_offset_in_bytes());

      __ movptr(rbx_arg_base, saved_args_base_addr);
      RegisterOrConstant dest_slot = dest_slot_constant;
      if (dest_slot_constant == -1) {
        load_conversion_vminfo(_masm, rdi_temp, conversion_addr);
        dest_slot = rdi_temp;
      }
      // Store the result back into the argslot.
      // This code uses the interpreter calling sequence, in which the return value
      // is usually left in the TOS register, as defined by InterpreterMacroAssembler::pop.
      // There are certain irregularities with floating point values, which can be seen
      // in TemplateInterpreterGenerator::generate_return_entry_for.
      move_return_value(_masm, dest_type_constant, Address(rbx_arg_base, dest_slot, Interpreter::stackElementScale()));

      RicochetFrame::leave_ricochet_frame(_masm, rcx_recv, rbx_arg_base, rdx_temp);
      __ push(rdx_temp);  // repush the return PC

      // Load the final target and go.
      if (VerifyMethodHandles)  verify_method_handle(_masm, rcx_recv);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);
      __ hlt(); // --------------------
      break;
    }

  case _adapter_opt_return_any:
    {
      if (VerifyMethodHandles)  RicochetFrame::verify_clean(_masm);
      Register rdi_conv = rdi_temp;
      assert_different_registers(rax, rdx,  // possibly live return value registers
                                 rdi_conv, rbx_temp);

      Address conversion_addr = RicochetFrame::frame_address(RicochetFrame::conversion_offset_in_bytes());
      load_conversion_dest_type(_masm, rdi_conv, conversion_addr);
      __ lea(rbx_temp, ExternalAddress((address) &_adapter_return_handlers[0]));
      __ movptr(rbx_temp, Address(rbx_temp, rdi_conv, Address::times_ptr));

#ifdef ASSERT
      { Label L_badconv;
        __ testptr(rbx_temp, rbx_temp);
        __ jccb(Assembler::zero, L_badconv);
        __ jmp(rbx_temp);
        __ bind(L_badconv);
        __ stop("bad method handle return");
      }
#else //ASSERT
      __ jmp(rbx_temp);
#endif //ASSERT
      break;
    }

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
      int length_constant = ek_adapter_opt_spread_count(ek);
      bool length_can_be_zero = (length_constant == 0);
      if (length_constant < 0) {
        // some adapters with variable length must handle the zero case
        if (!OptimizeMethodHandles ||
            ek_adapter_opt_spread_type(ek) != T_OBJECT)
          length_can_be_zero = true;
      }

      // find the address of the array argument
      __ movl(rax_argslot, rcx_amh_vmargslot);
      __ lea(rax_argslot, __ argument_address(rax_argslot));

      // grab another temp
      Register rsi_temp = rsi;
      { if (rsi_temp == saved_last_sp)  __ push(saved_last_sp); }
      // (preceding push must be done after argslot address is taken!)
#define UNPUSH_RSI \
      { if (rsi_temp == saved_last_sp)  __ pop(saved_last_sp); }

      // arx_argslot points both to the array and to the first output arg
      vmarg = Address(rax_argslot, 0);

      // Get the array value.
      Register  rsi_array       = rsi_temp;
      Register  rdx_array_klass = rdx_temp;
      BasicType elem_type = ek_adapter_opt_spread_type(ek);
      int       elem_slots = type2size[elem_type];  // 1 or 2
      int       array_slots = 1;  // array is always a T_OBJECT
      int       length_offset   = arrayOopDesc::length_offset_in_bytes();
      int       elem0_offset    = arrayOopDesc::base_offset_in_bytes(elem_type);
      __ movptr(rsi_array, vmarg);

      Label L_array_is_empty, L_insert_arg_space, L_copy_args, L_args_done;
      if (length_can_be_zero) {
        // handle the null pointer case, if zero is allowed
        Label L_skip;
        if (length_constant < 0) {
          load_conversion_vminfo(_masm, rbx_temp, rcx_amh_conversion);
          __ testl(rbx_temp, rbx_temp);
          __ jcc(Assembler::notZero, L_skip);
        }
        __ testptr(rsi_array, rsi_array);
        __ jcc(Assembler::zero, L_array_is_empty);
        __ bind(L_skip);
      }
      __ null_check(rsi_array, oopDesc::klass_offset_in_bytes());
      __ load_klass(rdx_array_klass, rsi_array);

      // Check the array type.
      Register rbx_klass = rbx_temp;
      __ load_heap_oop(rbx_klass, rcx_amh_argument); // this is a Class object!
      load_klass_from_Class(_masm, rbx_klass);

      Label ok_array_klass, bad_array_klass, bad_array_length;
      __ check_klass_subtype(rdx_array_klass, rbx_klass, rdi_temp, ok_array_klass);
      // If we get here, the type check failed!
      __ jmp(bad_array_klass);
      __ BIND(ok_array_klass);

      // Check length.
      if (length_constant >= 0) {
        __ cmpl(Address(rsi_array, length_offset), length_constant);
      } else {
        Register rbx_vminfo = rbx_temp;
        load_conversion_vminfo(_masm, rbx_vminfo, rcx_amh_conversion);
        __ cmpl(rbx_vminfo, Address(rsi_array, length_offset));
      }
      __ jcc(Assembler::notEqual, bad_array_length);

      Register rdx_argslot_limit = rdx_temp;

      // Array length checks out.  Now insert any required stack slots.
      if (length_constant == -1) {
        // Form a pointer to the end of the affected region.
        __ lea(rdx_argslot_limit, Address(rax_argslot, Interpreter::stackElementSize));
        // 'stack_move' is negative number of words to insert
        // This number already accounts for elem_slots.
        Register rdi_stack_move = rdi_temp;
        load_stack_move(_masm, rdi_stack_move, rcx_recv, true);
        __ cmpptr(rdi_stack_move, 0);
        assert(stack_move_unit() < 0, "else change this comparison");
        __ jcc(Assembler::less, L_insert_arg_space);
        __ jcc(Assembler::equal, L_copy_args);
        // single argument case, with no array movement
        __ BIND(L_array_is_empty);
        remove_arg_slots(_masm, -stack_move_unit() * array_slots,
                         rax_argslot, rbx_temp, rdx_temp);
        __ jmp(L_args_done);  // no spreading to do
        __ BIND(L_insert_arg_space);
        // come here in the usual case, stack_move < 0 (2 or more spread arguments)
        Register rsi_temp = rsi_array;  // spill this
        insert_arg_slots(_masm, rdi_stack_move,
                         rax_argslot, rbx_temp, rsi_temp);
        // reload the array since rsi was killed
        // reload from rdx_argslot_limit since rax_argslot is now decremented
        __ movptr(rsi_array, Address(rdx_argslot_limit, -Interpreter::stackElementSize));
      } else if (length_constant >= 1) {
        int new_slots = (length_constant * elem_slots) - array_slots;
        insert_arg_slots(_masm, new_slots * stack_move_unit(),
                         rax_argslot, rbx_temp, rdx_temp);
      } else if (length_constant == 0) {
        __ BIND(L_array_is_empty);
        remove_arg_slots(_masm, -stack_move_unit() * array_slots,
                         rax_argslot, rbx_temp, rdx_temp);
      } else {
        ShouldNotReachHere();
      }

      // Copy from the array to the new slots.
      // Note: Stack change code preserves integrity of rax_argslot pointer.
      // So even after slot insertions, rax_argslot still points to first argument.
      // Beware:  Arguments that are shallow on the stack are deep in the array,
      // and vice versa.  So a downward-growing stack (the usual) has to be copied
      // elementwise in reverse order from the source array.
      __ BIND(L_copy_args);
      if (length_constant == -1) {
        // [rax_argslot, rdx_argslot_limit) is the area we are inserting into.
        // Array element [0] goes at rdx_argslot_limit[-wordSize].
        Register rsi_source = rsi_array;
        __ lea(rsi_source, Address(rsi_array, elem0_offset));
        Register rdx_fill_ptr = rdx_argslot_limit;
        Label loop;
        __ BIND(loop);
        __ addptr(rdx_fill_ptr, -Interpreter::stackElementSize * elem_slots);
        move_typed_arg(_masm, elem_type, true,
                       Address(rdx_fill_ptr, 0), Address(rsi_source, 0),
                       rbx_temp, rdi_temp);
        __ addptr(rsi_source, type2aelembytes(elem_type));
        __ cmpptr(rdx_fill_ptr, rax_argslot);
        __ jcc(Assembler::greater, loop);
      } else if (length_constant == 0) {
        // nothing to copy
      } else {
        int elem_offset = elem0_offset;
        int slot_offset = length_constant * Interpreter::stackElementSize;
        for (int index = 0; index < length_constant; index++) {
          slot_offset -= Interpreter::stackElementSize * elem_slots;  // fill backward
          move_typed_arg(_masm, elem_type, true,
                         Address(rax_argslot, slot_offset), Address(rsi_array, elem_offset),
                         rbx_temp, rdi_temp);
          elem_offset += type2aelembytes(elem_type);
        }
      }
      __ BIND(L_args_done);

      // Arguments are spread.  Move to next method handle.
      UNPUSH_RSI;
      __ load_heap_oop(rcx_recv, rcx_mh_vmtarget);
      __ jump_to_method_handle_entry(rcx_recv, rdx_temp);

      __ bind(bad_array_klass);
      UNPUSH_RSI;
      assert(!vmarg.uses(rarg2_required), "must be different registers");
      __ load_heap_oop( rarg2_required, Address(rdx_array_klass, java_mirror_offset));  // required type
      __ movptr(        rarg1_actual,   vmarg);                                         // bad array
      __ movl(          rarg0_code,     (int) Bytecodes::_aaload);                      // who is complaining?
      __ jump(ExternalAddress(from_interpreted_entry(_raise_exception)));

      __ bind(bad_array_length);
      UNPUSH_RSI;
      assert(!vmarg.uses(rarg2_required), "must be different registers");
      __ mov(    rarg2_required, rcx_recv);                       // AMH requiring a certain length
      __ movptr( rarg1_actual,   vmarg);                          // bad array
      __ movl(   rarg0_code,     (int) Bytecodes::_arraylength);  // who is complaining?
      __ jump(ExternalAddress(from_interpreted_entry(_raise_exception)));
#undef UNPUSH_RSI

      break;
    }

  default:
    // do not require all platforms to recognize all adapter types
    __ nop();
    return;
  }
  __ hlt();

  address me_cookie = MethodHandleEntry::start_compiled_entry(_masm, interp_entry);
  __ unimplemented(entry_name(ek)); // %%% FIXME: NYI

  init_entry(ek, MethodHandleEntry::finish_compiled_entry(_masm, me_cookie));
}
