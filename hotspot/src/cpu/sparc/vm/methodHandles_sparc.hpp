/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

// Platform-specific definitions for method handles.
// These definitions are inlined into class MethodHandles.

// Adapters
enum /* platform_dependent_constants */ {
  adapter_code_size = NOT_LP64(23000 DEBUG_ONLY(+ 40000)) LP64_ONLY(35000 DEBUG_ONLY(+ 50000))
};

public:

class RicochetFrame : public ResourceObj {
  friend class MethodHandles;

 private:
  /*
    RF field            x86                 SPARC
    sender_pc           *(rsp+0)            I7-0x8
    sender_link         rbp                 I6+BIAS
    exact_sender_sp     rsi/r13             I5_savedSP
    conversion          *(rcx+&amh_conv)    L5_conv
    saved_args_base     rax                 L4_sab (cf. Gargs = G4)
    saved_args_layout   #NULL               L3_sal
    saved_target        *(rcx+&mh_vmtgt)    L2_stgt
    continuation        #STUB_CON           L1_cont
   */
  static const Register L1_continuation     ;  // what to do when control gets back here
  static const Register L2_saved_target     ;  // target method handle to invoke on saved_args
  static const Register L3_saved_args_layout;  // caching point for MethodTypeForm.vmlayout cookie
  static const Register L4_saved_args_base  ;  // base of pushed arguments (slot 0, arg N) (-3)
  static const Register L5_conversion       ;  // misc. information from original AdapterMethodHandle (-2)

  frame _fr;

  RicochetFrame(const frame& fr) : _fr(fr) { }

  intptr_t* register_addr(Register reg) const  {
    assert((_fr.sp() + reg->sp_offset_in_saved_window()) == _fr.register_addr(reg), "must agree");
    return _fr.register_addr(reg);
  }
  intptr_t  register_value(Register reg) const { return *register_addr(reg); }

 public:
  intptr_t* continuation() const        { return (intptr_t*) register_value(L1_continuation); }
  oop       saved_target() const        { return (oop)       register_value(L2_saved_target); }
  oop       saved_args_layout() const   { return (oop)       register_value(L3_saved_args_layout); }
  intptr_t* saved_args_base() const     { return (intptr_t*) register_value(L4_saved_args_base); }
  intptr_t  conversion() const          { return             register_value(L5_conversion); }
  intptr_t* exact_sender_sp() const     { return (intptr_t*) register_value(I5_savedSP); }
  intptr_t* sender_link() const         { return _fr.sender_sp(); }  // XXX
  address   sender_pc() const           { return _fr.sender_pc(); }

  // This value is not used for much, but it apparently must be nonzero.
  static int frame_size_in_bytes()              { return wordSize * 4; }

  intptr_t* extended_sender_sp() const  { return saved_args_base(); }

  intptr_t  return_value_slot_number() const {
    return adapter_conversion_vminfo(conversion());
  }
  BasicType return_value_type() const {
    return adapter_conversion_dest_type(conversion());
  }
  bool has_return_value_slot() const {
    return return_value_type() != T_VOID;
  }
  intptr_t* return_value_slot_addr() const {
    assert(has_return_value_slot(), "");
    return saved_arg_slot_addr(return_value_slot_number());
  }
  intptr_t* saved_target_slot_addr() const {
    return saved_arg_slot_addr(saved_args_length());
  }
  intptr_t* saved_arg_slot_addr(int slot) const {
    assert(slot >= 0, "");
    return (intptr_t*)( (address)saved_args_base() + (slot * Interpreter::stackElementSize) );
  }

  jint      saved_args_length() const;
  jint      saved_arg_offset(int arg) const;

  // GC interface
  oop*  saved_target_addr()                     { return (oop*)register_addr(L2_saved_target); }
  oop*  saved_args_layout_addr()                { return (oop*)register_addr(L3_saved_args_layout); }

  oop  compute_saved_args_layout(bool read_cache, bool write_cache);

#ifdef ASSERT
  // The magic number is supposed to help find ricochet frames within the bytes of stack dumps.
  enum { MAGIC_NUMBER_1 = 0xFEED03E, MAGIC_NUMBER_2 = 0xBEEF03E };
  static const Register L0_magic_number_1   ;  // cookie for debugging, at start of RSA
  static Address magic_number_2_addr()  { return Address(L4_saved_args_base, -wordSize); }
  intptr_t magic_number_1() const       { return register_value(L0_magic_number_1); }
  intptr_t magic_number_2() const       { return saved_args_base()[-1]; }
#endif //ASSERT

 public:
  enum { RETURN_VALUE_PLACEHOLDER = (NOT_DEBUG(0) DEBUG_ONLY(42)) };

  void verify() const NOT_DEBUG_RETURN; // check for MAGIC_NUMBER, etc.

  static void generate_ricochet_blob(MacroAssembler* _masm,
                                     // output params:
                                     int* bounce_offset,
                                     int* exception_offset,
                                     int* frame_size_in_words);

  static void enter_ricochet_frame(MacroAssembler* _masm,
                                   Register recv_reg,
                                   Register argv_reg,
                                   address return_handler);

  static void leave_ricochet_frame(MacroAssembler* _masm,
                                   Register recv_reg,
                                   Register new_sp_reg,
                                   Register sender_pc_reg);

  static RicochetFrame* from_frame(const frame& fr) {
    RicochetFrame* rf = new RicochetFrame(fr);
    rf->verify();
    return rf;
  }

  static void verify_clean(MacroAssembler* _masm) NOT_DEBUG_RETURN;

  static void describe(const frame* fr, FrameValues& values, int frame_no) NOT_DEBUG_RETURN;
};

// Additional helper methods for MethodHandles code generation:
public:
  static void load_klass_from_Class(MacroAssembler* _masm, Register klass_reg, Register temp_reg, Register temp2_reg);
  static void load_conversion_vminfo(MacroAssembler* _masm, Address conversion_field_addr, Register reg);
  static void extract_conversion_vminfo(MacroAssembler* _masm, Register conversion_field_reg, Register reg);
  static void extract_conversion_dest_type(MacroAssembler* _masm, Register conversion_field_reg, Register reg);

  static void load_stack_move(MacroAssembler* _masm,
                              Address G3_amh_conversion,
                              Register G5_stack_move);

  static void insert_arg_slots(MacroAssembler* _masm,
                               RegisterOrConstant arg_slots,
                               Register argslot_reg,
                               Register temp_reg, Register temp2_reg, Register temp3_reg);

  static void remove_arg_slots(MacroAssembler* _masm,
                               RegisterOrConstant arg_slots,
                               Register argslot_reg,
                               Register temp_reg, Register temp2_reg, Register temp3_reg);

  static void push_arg_slots(MacroAssembler* _masm,
                             Register argslot_reg,
                             RegisterOrConstant slot_count,
                             Register temp_reg, Register temp2_reg);

  static void move_arg_slots_up(MacroAssembler* _masm,
                                Register bottom_reg,  // invariant
                                Address  top_addr,    // can use temp_reg
                                RegisterOrConstant positive_distance_in_slots,
                                Register temp_reg, Register temp2_reg);

  static void move_arg_slots_down(MacroAssembler* _masm,
                                  Address  bottom_addr,  // can use temp_reg
                                  Register top_reg,      // invariant
                                  RegisterOrConstant negative_distance_in_slots,
                                  Register temp_reg, Register temp2_reg);

  static void move_typed_arg(MacroAssembler* _masm,
                             BasicType type, bool is_element,
                             Address value_src, Address slot_dest,
                             Register temp_reg);

  static void move_return_value(MacroAssembler* _masm, BasicType type,
                                Address return_slot);

  static void verify_argslot(MacroAssembler* _masm, Register argslot_reg,
                             Register temp_reg,
                             const char* error_message) NOT_DEBUG_RETURN;

  static void verify_argslots(MacroAssembler* _masm,
                              RegisterOrConstant argslot_count,
                              Register argslot_reg,
                              Register temp_reg,
                              Register temp2_reg,
                              bool negate_argslot,
                              const char* error_message) NOT_DEBUG_RETURN;

  static void verify_stack_move(MacroAssembler* _masm,
                                RegisterOrConstant arg_slots,
                                int direction) NOT_DEBUG_RETURN;

  static void verify_klass(MacroAssembler* _masm,
                           Register obj_reg, KlassHandle klass,
                           Register temp_reg, Register temp2_reg,
                           const char* error_message = "wrong klass") NOT_DEBUG_RETURN;

  static void verify_method_handle(MacroAssembler* _masm, Register mh_reg,
                                   Register temp_reg, Register temp2_reg) {
    verify_klass(_masm, mh_reg, SystemDictionaryHandles::MethodHandle_klass(),
                 temp_reg, temp2_reg,
                 "reference is a MH");
  }

  // Similar to InterpreterMacroAssembler::jump_from_interpreted.
  // Takes care of special dispatch from single stepping too.
  static void jump_from_method_handle(MacroAssembler* _masm, Register method, Register temp, Register temp2);

  static void trace_method_handle(MacroAssembler* _masm, const char* adaptername) PRODUCT_RETURN;
