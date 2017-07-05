/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
  adapter_code_size = NOT_LP64(16000 DEBUG_ONLY(+ 15000)) LP64_ONLY(32000 DEBUG_ONLY(+ 120000))
};

public:

// The stack just after the recursive call from a ricochet frame
// looks something like this.  Offsets are marked in words, not bytes.
// rsi (r13 on LP64) is part of the interpreter calling sequence
// which tells the callee where is my real rsp (for frame walking).
// (...lower memory addresses)
// rsp:     [ return pc                 ]   always the global RicochetBlob::bounce_addr
// rsp+1:   [ recursive arg N           ]
// rsp+2:   [ recursive arg N-1         ]
// ...
// rsp+N:   [ recursive arg 1           ]
// rsp+N+1: [ recursive method handle   ]
// ...
// rbp-6:   [ cleanup continuation pc   ]   <-- (struct RicochetFrame)
// rbp-5:   [ saved target MH           ]   the MH we will call on the saved args
// rbp-4:   [ saved args layout oop     ]   an int[] array which describes argument layout
// rbp-3:   [ saved args pointer        ]   address of transformed adapter arg M (slot 0)
// rbp-2:   [ conversion                ]   information about how the return value is used
// rbp-1:   [ exact sender sp           ]   exact TOS (rsi/r13) of original sender frame
// rbp+0:   [ saved sender fp           ]   (for original sender of AMH)
// rbp+1:   [ saved sender pc           ]   (back to original sender of AMH)
// rbp+2:   [ transformed adapter arg M ]   <-- (extended TOS of original sender)
// rbp+3:   [ transformed adapter arg M-1]
// ...
// rbp+M+1: [ transformed adapter arg 1 ]
// rbp+M+2: [ padding                   ] <-- (rbp + saved args base offset)
// ...      [ optional padding]
// (higher memory addresses...)
//
// The arguments originally passed by the original sender
// are lost, and arbitrary amounts of stack motion might have
// happened due to argument transformation.
// (This is done by C2I/I2C adapters and non-direct method handles.)
// This is why there is an unpredictable amount of memory between
// the extended and exact TOS of the sender.
// The ricochet adapter itself will also (in general) perform
// transformations before the recursive call.
//
// The transformed and saved arguments, immediately above the saved
// return PC, are a well-formed method handle invocation ready to execute.
// When the GC needs to walk the stack, these arguments are described
// via the saved arg types oop, an int[] array with a private format.
// This array is derived from the type of the transformed adapter
// method handle, which also sits at the base of the saved argument
// bundle.  Since the GC may not be able to fish out the int[]
// array, so it is pushed explicitly on the stack.  This may be
// an unnecessary expense.
//
// The following register conventions are significant at this point:
// rsp       the thread stack, as always; preserved by caller
// rsi/r13   exact TOS of recursive frame (contents of [rbp-2])
// rcx       recursive method handle (contents of [rsp+N+1])
// rbp       preserved by caller (not used by caller)
// Unless otherwise specified, all registers can be blown by the call.
//
// If this frame must be walked, the transformed adapter arguments
// will be found with the help of the saved arguments descriptor.
//
// Therefore, the descriptor must match the referenced arguments.
// The arguments must be followed by at least one word of padding,
// which will be necessary to complete the final method handle call.
// That word is not treated as holding an oop.  Neither is the word
//
// The word pointed to by the return argument pointer is not
// treated as an oop, even if points to a saved argument.
// This allows the saved argument list to have a "hole" in it
// to receive an oop from the recursive call.
// (The hole might temporarily contain RETURN_VALUE_PLACEHOLDER.)
//
// When the recursive callee returns, RicochetBlob::bounce_addr will
// immediately jump to the continuation stored in the RF.
// This continuation will merge the recursive return value
// into the saved argument list.  At that point, the original
// rsi, rbp, and rsp will be reloaded, the ricochet frame will
// disappear, and the final target of the adapter method handle
// will be invoked on the transformed argument list.

class RicochetFrame {
  friend class MethodHandles;
  friend class VMStructs;

 private:
  intptr_t* _continuation;          // what to do when control gets back here
  oopDesc*  _saved_target;          // target method handle to invoke on saved_args
  oopDesc*  _saved_args_layout;     // caching point for MethodTypeForm.vmlayout cookie
  intptr_t* _saved_args_base;       // base of pushed arguments (slot 0, arg N) (-3)
  intptr_t  _conversion;            // misc. information from original AdapterMethodHandle (-2)
  intptr_t* _exact_sender_sp;       // parallel to interpreter_frame_sender_sp (-1)
  intptr_t* _sender_link;           // *must* coincide with frame::link_offset (0)
  address   _sender_pc;             // *must* coincide with frame::return_addr_offset (1)

 public:
  intptr_t* continuation() const        { return _continuation; }
  oop       saved_target() const        { return _saved_target; }
  oop       saved_args_layout() const   { return _saved_args_layout; }
  intptr_t* saved_args_base() const     { return _saved_args_base; }
  intptr_t  conversion() const          { return _conversion; }
  intptr_t* exact_sender_sp() const     { return _exact_sender_sp; }
  intptr_t* sender_link() const         { return _sender_link; }
  address   sender_pc() const           { return _sender_pc; }

  intptr_t* extended_sender_sp() const {
    // The extended sender SP is above the current RicochetFrame.
    return (intptr_t*) (((address) this) + sizeof(RicochetFrame));
  }

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
  oop*  saved_target_addr()                     { return (oop*)&_saved_target; }
  oop*  saved_args_layout_addr()                { return (oop*)&_saved_args_layout; }

  oop  compute_saved_args_layout(bool read_cache, bool write_cache);

  // Compiler/assembler interface.
  static int continuation_offset_in_bytes()     { return offset_of(RicochetFrame, _continuation); }
  static int saved_target_offset_in_bytes()     { return offset_of(RicochetFrame, _saved_target); }
  static int saved_args_layout_offset_in_bytes(){ return offset_of(RicochetFrame, _saved_args_layout); }
  static int saved_args_base_offset_in_bytes()  { return offset_of(RicochetFrame, _saved_args_base); }
  static int conversion_offset_in_bytes()       { return offset_of(RicochetFrame, _conversion); }
  static int exact_sender_sp_offset_in_bytes()  { return offset_of(RicochetFrame, _exact_sender_sp); }
  static int sender_link_offset_in_bytes()      { return offset_of(RicochetFrame, _sender_link); }
  static int sender_pc_offset_in_bytes()        { return offset_of(RicochetFrame, _sender_pc); }

  // This value is not used for much, but it apparently must be nonzero.
  static int frame_size_in_bytes()              { return sender_link_offset_in_bytes(); }

#ifdef ASSERT
  // The magic number is supposed to help find ricochet frames within the bytes of stack dumps.
  enum { MAGIC_NUMBER_1 = 0xFEED03E, MAGIC_NUMBER_2 = 0xBEEF03E };
  static int magic_number_1_offset_in_bytes()   { return -wordSize; }
  static int magic_number_2_offset_in_bytes()   { return sizeof(RicochetFrame); }
  intptr_t magic_number_1() const               { return *(intptr_t*)((address)this + magic_number_1_offset_in_bytes()); };
  intptr_t magic_number_2() const               { return *(intptr_t*)((address)this + magic_number_2_offset_in_bytes()); };
#endif //ASSERT

  enum { RETURN_VALUE_PLACEHOLDER = (NOT_DEBUG(0) DEBUG_ONLY(42)) };

  static void verify_offsets() NOT_DEBUG_RETURN;
  void verify() const NOT_DEBUG_RETURN; // check for MAGIC_NUMBER, etc.
  void zap_arguments() NOT_DEBUG_RETURN;

  static void generate_ricochet_blob(MacroAssembler* _masm,
                                     // output params:
                                     int* bounce_offset,
                                     int* exception_offset,
                                     int* frame_size_in_words);

  static void enter_ricochet_frame(MacroAssembler* _masm,
                                   Register rcx_recv,
                                   Register rax_argv,
                                   address return_handler,
                                   Register rbx_temp);
  static void leave_ricochet_frame(MacroAssembler* _masm,
                                   Register rcx_recv,
                                   Register new_sp_reg,
                                   Register sender_pc_reg);

  static Address frame_address(int offset = 0) {
    // The RicochetFrame is found by subtracting a constant offset from rbp.
    return Address(rbp, - sender_link_offset_in_bytes() + offset);
  }

  static RicochetFrame* from_frame(const frame& fr) {
    address bp = (address) fr.fp();
    RicochetFrame* rf = (RicochetFrame*)(bp - sender_link_offset_in_bytes());
    rf->verify();
    return rf;
  }

  static void verify_clean(MacroAssembler* _masm) NOT_DEBUG_RETURN;

  static void describe(const frame* fr, FrameValues& values, int frame_no) PRODUCT_RETURN;
};

// Additional helper methods for MethodHandles code generation:
public:
  static void load_klass_from_Class(MacroAssembler* _masm, Register klass_reg);
  static void load_conversion_vminfo(MacroAssembler* _masm, Register reg, Address conversion_field_addr);
  static void load_conversion_dest_type(MacroAssembler* _masm, Register reg, Address conversion_field_addr);

  static void load_stack_move(MacroAssembler* _masm,
                              Register rdi_stack_move,
                              Register rcx_amh,
                              bool might_be_negative);

  static void insert_arg_slots(MacroAssembler* _masm,
                               RegisterOrConstant arg_slots,
                               Register rax_argslot,
                               Register rbx_temp, Register rdx_temp);

  static void remove_arg_slots(MacroAssembler* _masm,
                               RegisterOrConstant arg_slots,
                               Register rax_argslot,
                               Register rbx_temp, Register rdx_temp);

  static void push_arg_slots(MacroAssembler* _masm,
                                   Register rax_argslot,
                                   RegisterOrConstant slot_count,
                                   int skip_words_count,
                                   Register rbx_temp, Register rdx_temp);

  static void move_arg_slots_up(MacroAssembler* _masm,
                                Register rbx_bottom,  // invariant
                                Address  top_addr,    // can use rax_temp
                                RegisterOrConstant positive_distance_in_slots,
                                Register rax_temp, Register rdx_temp);

  static void move_arg_slots_down(MacroAssembler* _masm,
                                  Address  bottom_addr,  // can use rax_temp
                                  Register rbx_top,      // invariant
                                  RegisterOrConstant negative_distance_in_slots,
                                  Register rax_temp, Register rdx_temp);

  static void move_typed_arg(MacroAssembler* _masm,
                             BasicType type, bool is_element,
                             Address slot_dest, Address value_src,
                             Register rbx_temp, Register rdx_temp);

  static void move_return_value(MacroAssembler* _masm, BasicType type,
                                Address return_slot);

  static void verify_argslot(MacroAssembler* _masm, Register argslot_reg,
                             const char* error_message) NOT_DEBUG_RETURN;

  static void verify_argslots(MacroAssembler* _masm,
                              RegisterOrConstant argslot_count,
                              Register argslot_reg,
                              bool negate_argslot,
                              const char* error_message) NOT_DEBUG_RETURN;

  static void verify_stack_move(MacroAssembler* _masm,
                                RegisterOrConstant arg_slots,
                                int direction) NOT_DEBUG_RETURN;

  static void verify_klass(MacroAssembler* _masm,
                           Register obj, KlassHandle klass,
                           const char* error_message = "wrong klass") NOT_DEBUG_RETURN;

  static void verify_method_handle(MacroAssembler* _masm, Register mh_reg) {
    verify_klass(_masm, mh_reg, SystemDictionaryHandles::MethodHandle_klass(),
                 "reference is a MH");
  }

  // Similar to InterpreterMacroAssembler::jump_from_interpreted.
  // Takes care of special dispatch from single stepping too.
  static void jump_from_method_handle(MacroAssembler* _masm, Register method, Register temp);

  static void trace_method_handle(MacroAssembler* _masm, const char* adaptername) PRODUCT_RETURN;

  static Register saved_last_sp_register() {
    // Should be in sharedRuntime, not here.
    return LP64_ONLY(r13) NOT_LP64(rsi);
  }
