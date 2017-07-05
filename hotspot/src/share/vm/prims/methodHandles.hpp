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

#ifndef SHARE_VM_PRIMS_METHODHANDLES_HPP
#define SHARE_VM_PRIMS_METHODHANDLES_HPP

#include "classfile/javaClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/interfaceSupport.hpp"

class MacroAssembler;
class Label;
class MethodHandleEntry;

class MethodHandles: AllStatic {
  // JVM support for MethodHandle, MethodType, and related types
  // in java.dyn and java.dyn.hotspot.
  // See also  javaClasses for layouts java_dyn_Method{Handle,Type,Type::Form}.
 public:
  enum EntryKind {
    _raise_exception,           // stub for error generation from other stubs
    _invokestatic_mh,           // how a MH emulates invokestatic
    _invokespecial_mh,          // ditto for the other invokes...
    _invokevirtual_mh,
    _invokeinterface_mh,
    _bound_ref_mh,              // reference argument is bound
    _bound_int_mh,              // int argument is bound (via an Integer or Float)
    _bound_long_mh,             // long argument is bound (via a Long or Double)
    _bound_ref_direct_mh,       // same as above, with direct linkage to methodOop
    _bound_int_direct_mh,
    _bound_long_direct_mh,

    _adapter_mh_first,     // adapter sequence goes here...
    _adapter_retype_only   = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_RETYPE_ONLY,
    _adapter_retype_raw    = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_RETYPE_RAW,
    _adapter_check_cast    = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_CHECK_CAST,
    _adapter_prim_to_prim  = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_PRIM_TO_PRIM,
    _adapter_ref_to_prim   = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_REF_TO_PRIM,
    _adapter_prim_to_ref   = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_PRIM_TO_REF,
    _adapter_swap_args     = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_SWAP_ARGS,
    _adapter_rot_args      = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_ROT_ARGS,
    _adapter_dup_args      = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_DUP_ARGS,
    _adapter_drop_args     = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_DROP_ARGS,
    _adapter_collect_args  = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_COLLECT_ARGS,
    _adapter_spread_args   = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_SPREAD_ARGS,
    _adapter_flyby         = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_FLYBY,
    _adapter_ricochet      = _adapter_mh_first + sun_dyn_AdapterMethodHandle::OP_RICOCHET,
    _adapter_mh_last       = _adapter_mh_first + sun_dyn_AdapterMethodHandle::CONV_OP_LIMIT - 1,

    // Optimized adapter types

    // argument list reordering
    _adapter_opt_swap_1,
    _adapter_opt_swap_2,
    _adapter_opt_rot_1_up,
    _adapter_opt_rot_1_down,
    _adapter_opt_rot_2_up,
    _adapter_opt_rot_2_down,
    // primitive single to single:
    _adapter_opt_i2i,           // i2c, i2z, i2b, i2s
    // primitive double to single:
    _adapter_opt_l2i,
    _adapter_opt_d2f,
    // primitive single to double:
    _adapter_opt_i2l,
    _adapter_opt_f2d,
    // conversion between floating point and integer type is handled by Java

    // reference to primitive:
    _adapter_opt_unboxi,
    _adapter_opt_unboxl,

    // spreading (array length cases 0, 1, >=2)
    _adapter_opt_spread_0,
    _adapter_opt_spread_1,
    _adapter_opt_spread_more,

    _EK_LIMIT,
    _EK_FIRST = 0
  };

 public:
  static bool enabled()                         { return _enabled; }
  static void set_enabled(bool z);

 private:
  enum {  // import sun_dyn_AdapterMethodHandle::CONV_OP_*
    CONV_OP_LIMIT         = sun_dyn_AdapterMethodHandle::CONV_OP_LIMIT,
    CONV_OP_MASK          = sun_dyn_AdapterMethodHandle::CONV_OP_MASK,
    CONV_VMINFO_MASK      = sun_dyn_AdapterMethodHandle::CONV_VMINFO_MASK,
    CONV_VMINFO_SHIFT     = sun_dyn_AdapterMethodHandle::CONV_VMINFO_SHIFT,
    CONV_OP_SHIFT         = sun_dyn_AdapterMethodHandle::CONV_OP_SHIFT,
    CONV_DEST_TYPE_SHIFT  = sun_dyn_AdapterMethodHandle::CONV_DEST_TYPE_SHIFT,
    CONV_SRC_TYPE_SHIFT   = sun_dyn_AdapterMethodHandle::CONV_SRC_TYPE_SHIFT,
    CONV_STACK_MOVE_SHIFT = sun_dyn_AdapterMethodHandle::CONV_STACK_MOVE_SHIFT,
    CONV_STACK_MOVE_MASK  = sun_dyn_AdapterMethodHandle::CONV_STACK_MOVE_MASK
  };

  static bool _enabled;
  static MethodHandleEntry* _entries[_EK_LIMIT];
  static const char*        _entry_names[_EK_LIMIT+1];
  static jobject            _raise_exception_method;

  // Adapters.
  static MethodHandlesAdapterBlob* _adapter_code;
  static int                       _adapter_code_size;

  static bool ek_valid(EntryKind ek)            { return (uint)ek < (uint)_EK_LIMIT; }
  static bool conv_op_valid(int op)             { return (uint)op < (uint)CONV_OP_LIMIT; }

 public:
  static bool    have_entry(EntryKind ek)       { return ek_valid(ek) && _entries[ek] != NULL; }
  static MethodHandleEntry* entry(EntryKind ek) { assert(ek_valid(ek), "initialized");
                                                  return _entries[ek]; }
  static const char* entry_name(EntryKind ek)   { assert(ek_valid(ek), "oob");
                                                  return _entry_names[ek]; }
  static EntryKind adapter_entry_kind(int op)   { assert(conv_op_valid(op), "oob");
                                                  return EntryKind(_adapter_mh_first + op); }

  static void init_entry(EntryKind ek, MethodHandleEntry* me) {
    assert(ek_valid(ek), "oob");
    assert(_entries[ek] == NULL, "no double initialization");
    _entries[ek] = me;
  }

  // Some adapter helper functions.
  static void get_ek_bound_mh_info(EntryKind ek, BasicType& arg_type, int& arg_mask, int& arg_slots) {
    switch (ek) {
    case _bound_int_mh        : // fall-thru
    case _bound_int_direct_mh : arg_type = T_INT;    arg_mask = _INSERT_INT_MASK;  break;
    case _bound_long_mh       : // fall-thru
    case _bound_long_direct_mh: arg_type = T_LONG;   arg_mask = _INSERT_LONG_MASK; break;
    case _bound_ref_mh        : // fall-thru
    case _bound_ref_direct_mh : arg_type = T_OBJECT; arg_mask = _INSERT_REF_MASK;  break;
    default: ShouldNotReachHere();
    }
    arg_slots = type2size[arg_type];
  }

  static void get_ek_adapter_opt_swap_rot_info(EntryKind ek, int& swap_bytes, int& rotate) {
    int swap_slots = 0;
    switch (ek) {
    case _adapter_opt_swap_1:     swap_slots = 1; rotate =  0; break;
    case _adapter_opt_swap_2:     swap_slots = 2; rotate =  0; break;
    case _adapter_opt_rot_1_up:   swap_slots = 1; rotate =  1; break;
    case _adapter_opt_rot_1_down: swap_slots = 1; rotate = -1; break;
    case _adapter_opt_rot_2_up:   swap_slots = 2; rotate =  1; break;
    case _adapter_opt_rot_2_down: swap_slots = 2; rotate = -1; break;
    default: ShouldNotReachHere();
    }
    // Return the size of the stack slots to move in bytes.
    swap_bytes = swap_slots * Interpreter::stackElementSize;
  }

  static int get_ek_adapter_opt_spread_info(EntryKind ek) {
    switch (ek) {
    case _adapter_opt_spread_0: return  0;
    case _adapter_opt_spread_1: return  1;
    default                   : return -1;
    }
  }

  static methodOop raise_exception_method() {
    oop rem = JNIHandles::resolve(_raise_exception_method);
    assert(rem == NULL || rem->is_method(), "");
    return (methodOop) rem;
  }
  static void set_raise_exception_method(methodOop rem) {
    assert(_raise_exception_method == NULL, "");
    _raise_exception_method = JNIHandles::make_global(Handle(rem));
  }

  static jint adapter_conversion(int conv_op, BasicType src, BasicType dest,
                                 int stack_move = 0, int vminfo = 0) {
    assert(conv_op_valid(conv_op), "oob");
    jint conv = ((conv_op      << CONV_OP_SHIFT)
                 | (src        << CONV_SRC_TYPE_SHIFT)
                 | (dest       << CONV_DEST_TYPE_SHIFT)
                 | (stack_move << CONV_STACK_MOVE_SHIFT)
                 | (vminfo     << CONV_VMINFO_SHIFT)
                 );
    assert(adapter_conversion_op(conv) == conv_op, "decode conv_op");
    assert(adapter_conversion_src_type(conv) == src, "decode src");
    assert(adapter_conversion_dest_type(conv) == dest, "decode dest");
    assert(adapter_conversion_stack_move(conv) == stack_move, "decode stack_move");
    assert(adapter_conversion_vminfo(conv) == vminfo, "decode vminfo");
    return conv;
  }
  static int adapter_conversion_op(jint conv) {
    return ((conv >> CONV_OP_SHIFT) & 0xF);
  }
  static BasicType adapter_conversion_src_type(jint conv) {
    return (BasicType)((conv >> CONV_SRC_TYPE_SHIFT) & 0xF);
  }
  static BasicType adapter_conversion_dest_type(jint conv) {
    return (BasicType)((conv >> CONV_DEST_TYPE_SHIFT) & 0xF);
  }
  static int adapter_conversion_stack_move(jint conv) {
    return (conv >> CONV_STACK_MOVE_SHIFT);
  }
  static int adapter_conversion_vminfo(jint conv) {
    return (conv >> CONV_VMINFO_SHIFT) & CONV_VMINFO_MASK;
  }

  // Bit mask of conversion_op values.  May vary by platform.
  static int adapter_conversion_ops_supported_mask();

  // Offset in words that the interpreter stack pointer moves when an argument is pushed.
  // The stack_move value must always be a multiple of this.
  static int stack_move_unit() {
    return frame::interpreter_frame_expression_stack_direction() * Interpreter::stackElementWords;
  }

  enum { CONV_VMINFO_SIGN_FLAG = 0x80 };
  // Shift values for prim-to-prim conversions.
  static int adapter_prim_to_prim_subword_vminfo(BasicType dest) {
    if (dest == T_BOOLEAN) return (BitsPerInt - 1);  // boolean is 1 bit
    if (dest == T_CHAR)    return (BitsPerInt - BitsPerShort);
    if (dest == T_BYTE)    return (BitsPerInt - BitsPerByte ) | CONV_VMINFO_SIGN_FLAG;
    if (dest == T_SHORT)   return (BitsPerInt - BitsPerShort) | CONV_VMINFO_SIGN_FLAG;
    return 0;                   // case T_INT
  }
  // Shift values for unboxing a primitive.
  static int adapter_unbox_subword_vminfo(BasicType dest) {
    if (dest == T_BOOLEAN) return (BitsPerInt - BitsPerByte );  // implemented as 1 byte
    if (dest == T_CHAR)    return (BitsPerInt - BitsPerShort);
    if (dest == T_BYTE)    return (BitsPerInt - BitsPerByte ) | CONV_VMINFO_SIGN_FLAG;
    if (dest == T_SHORT)   return (BitsPerInt - BitsPerShort) | CONV_VMINFO_SIGN_FLAG;
    return 0;                   // case T_INT
  }
  // Here is the transformation the i2i adapter must perform:
  static int truncate_subword_from_vminfo(jint value, int vminfo) {
    jint tem = value << vminfo;
    if ((vminfo & CONV_VMINFO_SIGN_FLAG) != 0) {
      return (jint)tem >> vminfo;
    } else {
      return (juint)tem >> vminfo;
    }
  }

  static inline address from_compiled_entry(EntryKind ek);
  static inline address from_interpreted_entry(EntryKind ek);

  // helpers for decode_method.
  static methodOop decode_methodOop(methodOop m, int& decode_flags_result);
  static methodOop decode_vmtarget(oop vmtarget, int vmindex, oop mtype, klassOop& receiver_limit_result, int& decode_flags_result);
  static methodOop decode_MemberName(oop mname, klassOop& receiver_limit_result, int& decode_flags_result);
  static methodOop decode_MethodHandle(oop mh, klassOop& receiver_limit_result, int& decode_flags_result);
  static methodOop decode_DirectMethodHandle(oop mh, klassOop& receiver_limit_result, int& decode_flags_result);
  static methodOop decode_BoundMethodHandle(oop mh, klassOop& receiver_limit_result, int& decode_flags_result);
  static methodOop decode_AdapterMethodHandle(oop mh, klassOop& receiver_limit_result, int& decode_flags_result);

  // Find out how many stack slots an mh pushes or pops.
  // The result is *not* reported as a multiple of stack_move_unit();
  // It is a signed net number of pushes (a difference in vmslots).
  // To compare with a stack_move value, first multiply by stack_move_unit().
  static int decode_MethodHandle_stack_pushes(oop mh);

 public:
  // working with member names
  static void resolve_MemberName(Handle mname, TRAPS); // compute vmtarget/vmindex from name/type
  static void expand_MemberName(Handle mname, int suppress, TRAPS);  // expand defc/name/type if missing
  static Handle new_MemberName(TRAPS);  // must be followed by init_MemberName
  static void init_MemberName(oop mname_oop, oop target); // compute vmtarget/vmindex from target
  static void init_MemberName(oop mname_oop, methodOop m, bool do_dispatch = true);
  static void init_MemberName(oop mname_oop, klassOop field_holder, AccessFlags mods, int offset);
  static int find_MemberNames(klassOop k, symbolOop name, symbolOop sig,
                              int mflags, klassOop caller,
                              int skip, objArrayOop results);
  // bit values for suppress argument to expand_MemberName:
  enum { _suppress_defc = 1, _suppress_name = 2, _suppress_type = 4 };

  // Generate MethodHandles adapters.
  static void generate_adapters();

  // Called from InterpreterGenerator and MethodHandlesAdapterGenerator.
  static address generate_method_handle_interpreter_entry(MacroAssembler* _masm);
  static void generate_method_handle_stub(MacroAssembler* _masm, EntryKind ek);

  // argument list parsing
  static int argument_slot(oop method_type, int arg);
  static int argument_slot_count(oop method_type) { return argument_slot(method_type, -1); }
  static int argument_slot_to_argnum(oop method_type, int argslot);

  // Runtime support
  enum {                        // bit-encoded flags from decode_method or decode_vmref
    _dmf_has_receiver   = 0x01, // target method has leading reference argument
    _dmf_does_dispatch  = 0x02, // method handle performs virtual or interface dispatch
    _dmf_from_interface = 0x04, // peforms interface dispatch
    _DMF_DIRECT_MASK    = (_dmf_from_interface*2 - _dmf_has_receiver),
    _dmf_binds_method   = 0x08,
    _dmf_binds_argument = 0x10,
    _DMF_BOUND_MASK     = (_dmf_binds_argument*2 - _dmf_binds_method),
    _dmf_adapter_lsb    = 0x20,
    _DMF_ADAPTER_MASK   = (_dmf_adapter_lsb << CONV_OP_LIMIT) - _dmf_adapter_lsb
  };
  static methodOop decode_method(oop x, klassOop& receiver_limit_result, int& decode_flags_result);
  enum {
    // format of query to getConstant:
    GC_JVM_PUSH_LIMIT = 0,
    GC_JVM_STACK_MOVE_UNIT = 1,
    GC_CONV_OP_IMPLEMENTED_MASK = 2,

    // format of result from getTarget / encode_target:
    ETF_HANDLE_OR_METHOD_NAME = 0, // all available data (immediate MH or method)
    ETF_DIRECT_HANDLE         = 1, // ultimate method handle (will be a DMH, may be self)
    ETF_METHOD_NAME           = 2, // ultimate method as MemberName
    ETF_REFLECT_METHOD        = 3  // ultimate method as java.lang.reflect object (sans refClass)
  };
  static int get_named_constant(int which, Handle name_box, TRAPS);
  static oop encode_target(Handle mh, int format, TRAPS); // report vmtarget (to Java code)
  static bool class_cast_needed(klassOop src, klassOop dst);

  static instanceKlassHandle resolve_instance_klass(oop    java_mirror_oop, TRAPS);
  static instanceKlassHandle resolve_instance_klass(jclass java_mirror_jh,  TRAPS) {
    return resolve_instance_klass(JNIHandles::resolve(java_mirror_jh), THREAD);
  }

 private:
  // These checkers operate on a pair of whole MethodTypes:
  static const char* check_method_type_change(oop src_mtype, int src_beg, int src_end,
                                              int insert_argnum, oop insert_type,
                                              int change_argnum, oop change_type,
                                              int delete_argnum,
                                              oop dst_mtype, int dst_beg, int dst_end,
                                              bool raw = false);
  static const char* check_method_type_insertion(oop src_mtype,
                                                 int insert_argnum, oop insert_type,
                                                 oop dst_mtype) {
    oop no_ref = NULL;
    return check_method_type_change(src_mtype, 0, -1,
                                    insert_argnum, insert_type,
                                    -1, no_ref, -1, dst_mtype, 0, -1);
  }
  static const char* check_method_type_conversion(oop src_mtype,
                                                  int change_argnum, oop change_type,
                                                  oop dst_mtype) {
    oop no_ref = NULL;
    return check_method_type_change(src_mtype, 0, -1, -1, no_ref,
                                    change_argnum, change_type,
                                    -1, dst_mtype, 0, -1);
  }
  static const char* check_method_type_passthrough(oop src_mtype, oop dst_mtype, bool raw) {
    oop no_ref = NULL;
    return check_method_type_change(src_mtype, 0, -1,
                                    -1, no_ref, -1, no_ref, -1,
                                    dst_mtype, 0, -1, raw);
  }

  // These checkers operate on pairs of argument or return types:
  static const char* check_argument_type_change(BasicType src_type, klassOop src_klass,
                                                BasicType dst_type, klassOop dst_klass,
                                                int argnum, bool raw = false);

  static const char* check_argument_type_change(oop src_type, oop dst_type,
                                                int argnum, bool raw = false) {
    klassOop src_klass = NULL, dst_klass = NULL;
    BasicType src_bt = java_lang_Class::as_BasicType(src_type, &src_klass);
    BasicType dst_bt = java_lang_Class::as_BasicType(dst_type, &dst_klass);
    return check_argument_type_change(src_bt, src_klass,
                                      dst_bt, dst_klass, argnum, raw);
  }

  static const char* check_return_type_change(oop src_type, oop dst_type, bool raw = false) {
    return check_argument_type_change(src_type, dst_type, -1, raw);
  }

  static const char* check_return_type_change(BasicType src_type, klassOop src_klass,
                                              BasicType dst_type, klassOop dst_klass) {
    return check_argument_type_change(src_type, src_klass, dst_type, dst_klass, -1);
  }

  static const char* check_method_receiver(methodOop m, klassOop passed_recv_type);

  // These verifiers can block, and will throw an error if the checking fails:
  static void verify_vmslots(Handle mh, TRAPS);
  static void verify_vmargslot(Handle mh, int argnum, int argslot, TRAPS);

  static void verify_method_type(methodHandle m, Handle mtype,
                                 bool has_bound_oop,
                                 KlassHandle bound_oop_type,
                                 TRAPS);

  static void verify_method_signature(methodHandle m, Handle mtype,
                                      int first_ptype_pos,
                                      KlassHandle insert_ptype, TRAPS);

  static void verify_DirectMethodHandle(Handle mh, methodHandle m, TRAPS);
  static void verify_BoundMethodHandle(Handle mh, Handle target, int argnum,
                                       bool direct_to_method, TRAPS);
  static void verify_BoundMethodHandle_with_receiver(Handle mh, methodHandle m, TRAPS);
  static void verify_AdapterMethodHandle(Handle mh, int argnum, TRAPS);

 public:

  // Fill in the fields of a DirectMethodHandle mh.  (MH.type must be pre-filled.)
  static void init_DirectMethodHandle(Handle mh, methodHandle method, bool do_dispatch, TRAPS);

  // Fill in the fields of a BoundMethodHandle mh.  (MH.type, BMH.argument must be pre-filled.)
  static void init_BoundMethodHandle(Handle mh, Handle target, int argnum, TRAPS);
  static void init_BoundMethodHandle_with_receiver(Handle mh,
                                                   methodHandle original_m,
                                                   KlassHandle receiver_limit,
                                                   int decode_flags,
                                                   TRAPS);

  // Fill in the fields of an AdapterMethodHandle mh.  (MH.type must be pre-filled.)
  static void init_AdapterMethodHandle(Handle mh, Handle target, int argnum, TRAPS);

#ifdef ASSERT
  static bool spot_check_entry_names();
#endif

 private:
  static methodHandle dispatch_decoded_method(methodHandle m,
                                              KlassHandle receiver_limit,
                                              int decode_flags,
                                              KlassHandle receiver_klass,
                                              TRAPS);

  static bool same_basic_type_for_arguments(BasicType src, BasicType dst,
                                            bool raw = false,
                                            bool for_return = false);
  static bool same_basic_type_for_returns(BasicType src, BasicType dst, bool raw = false) {
    return same_basic_type_for_arguments(src, dst, raw, true);
  }

  enum {                        // arg_mask values
    _INSERT_NO_MASK   = -1,
    _INSERT_REF_MASK  = 0,
    _INSERT_INT_MASK  = 1,
    _INSERT_LONG_MASK = 3
  };
  static void insert_arg_slots(MacroAssembler* _masm,
                               RegisterOrConstant arg_slots,
                               int arg_mask,
                               Register argslot_reg,
                               Register temp_reg, Register temp2_reg, Register temp3_reg = noreg);

  static void remove_arg_slots(MacroAssembler* _masm,
                               RegisterOrConstant arg_slots,
                               Register argslot_reg,
                               Register temp_reg, Register temp2_reg, Register temp3_reg = noreg);

  static void trace_method_handle(MacroAssembler* _masm, const char* adaptername) PRODUCT_RETURN;
};


// Access methods for the "entry" field of a java.dyn.MethodHandle.
// The field is primarily a jump target for compiled calls.
// However, we squirrel away some nice pointers for other uses,
// just before the jump target.
// Aspects of a method handle entry:
//  - from_compiled_entry - stub used when compiled code calls the MH
//  - from_interpreted_entry - stub used when the interpreter calls the MH
//  - type_checking_entry - stub for runtime casting between MHForm siblings (NYI)
class MethodHandleEntry {
 public:
  class Data {
    friend class MethodHandleEntry;
    size_t              _total_size; // size including Data and code stub
    MethodHandleEntry*  _type_checking_entry;
    address             _from_interpreted_entry;
    MethodHandleEntry* method_entry() { return (MethodHandleEntry*)(this + 1); }
  };

  Data*     data()                              { return (Data*)this - 1; }

  address   start_address()                     { return (address) data(); }
  address   end_address()                       { return start_address() + data()->_total_size; }

  address   from_compiled_entry()               { return (address) this; }

  address   from_interpreted_entry()            { return data()->_from_interpreted_entry; }
  void  set_from_interpreted_entry(address e)   { data()->_from_interpreted_entry = e; }

  MethodHandleEntry* type_checking_entry()           { return data()->_type_checking_entry; }
  void set_type_checking_entry(MethodHandleEntry* e) { data()->_type_checking_entry = e; }

  void set_end_address(address end_addr) {
    size_t total_size = end_addr - start_address();
    assert(total_size > 0 && total_size < 0x1000, "reasonable end address");
    data()->_total_size = total_size;
  }

  // Compiler support:
  static int from_interpreted_entry_offset_in_bytes() {
    return (int)( offset_of(Data, _from_interpreted_entry) - sizeof(Data) );
  }
  static int type_checking_entry_offset_in_bytes() {
    return (int)( offset_of(Data, _from_interpreted_entry) - sizeof(Data) );
  }

  static address            start_compiled_entry(MacroAssembler* _masm,
                                                 address interpreted_entry = NULL);
  static MethodHandleEntry* finish_compiled_entry(MacroAssembler* masm, address start_addr);
};

address MethodHandles::from_compiled_entry(EntryKind ek) { return entry(ek)->from_compiled_entry(); }
address MethodHandles::from_interpreted_entry(EntryKind ek) { return entry(ek)->from_interpreted_entry(); }


//------------------------------------------------------------------------------
// MethodHandlesAdapterGenerator
//
class MethodHandlesAdapterGenerator : public StubCodeGenerator {
public:
  MethodHandlesAdapterGenerator(CodeBuffer* code) : StubCodeGenerator(code) {}

  void generate();
};

#endif // SHARE_VM_PRIMS_METHODHANDLES_HPP
