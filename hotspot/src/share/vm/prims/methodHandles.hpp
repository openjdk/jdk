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
  // in java.lang.invoke and sun.invoke.
  // See also  javaClasses for layouts java_lang_invoke_Method{Handle,Type,Type::Form}.
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
    _adapter_retype_only   = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_RETYPE_ONLY,
    _adapter_retype_raw    = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_RETYPE_RAW,
    _adapter_check_cast    = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_CHECK_CAST,
    _adapter_prim_to_prim  = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_PRIM,
    _adapter_ref_to_prim   = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_REF_TO_PRIM,
    _adapter_prim_to_ref   = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_PRIM_TO_REF,
    _adapter_swap_args     = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_SWAP_ARGS,
    _adapter_rot_args      = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_ROT_ARGS,
    _adapter_dup_args      = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_DUP_ARGS,
    _adapter_drop_args     = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_DROP_ARGS,
    _adapter_collect_args  = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_COLLECT_ARGS,
    _adapter_spread_args   = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_SPREAD_ARGS,
    _adapter_fold_args     = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::OP_FOLD_ARGS,
    _adapter_unused_13     = _adapter_mh_first + 13,  //hole in the CONV_OP enumeration
    _adapter_mh_last       = _adapter_mh_first + java_lang_invoke_AdapterMethodHandle::CONV_OP_LIMIT - 1,

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

    // %% Maybe tame the following with a VM_SYMBOLS_DO type macro?

    // how a blocking adapter returns (platform-dependent)
    _adapter_opt_return_ref,
    _adapter_opt_return_int,
    _adapter_opt_return_long,
    _adapter_opt_return_float,
    _adapter_opt_return_double,
    _adapter_opt_return_void,
    _adapter_opt_return_S0_ref,  // return ref to S=0 (last slot)
    _adapter_opt_return_S1_ref,  // return ref to S=1 (2nd-to-last slot)
    _adapter_opt_return_S2_ref,
    _adapter_opt_return_S3_ref,
    _adapter_opt_return_S4_ref,
    _adapter_opt_return_S5_ref,
    _adapter_opt_return_any,     // dynamically select r/i/l/f/d
    _adapter_opt_return_FIRST = _adapter_opt_return_ref,
    _adapter_opt_return_LAST  = _adapter_opt_return_any,

    // spreading (array length cases 0, 1, ...)
    _adapter_opt_spread_0,       // spread empty array to N=0 arguments
    _adapter_opt_spread_1_ref,   // spread Object[] to N=1 argument
    _adapter_opt_spread_2_ref,   // spread Object[] to N=2 arguments
    _adapter_opt_spread_3_ref,   // spread Object[] to N=3 arguments
    _adapter_opt_spread_4_ref,   // spread Object[] to N=4 arguments
    _adapter_opt_spread_5_ref,   // spread Object[] to N=5 arguments
    _adapter_opt_spread_ref,     // spread Object[] to N arguments
    _adapter_opt_spread_byte,    // spread byte[] or boolean[] to N arguments
    _adapter_opt_spread_char,    // spread char[], etc., to N arguments
    _adapter_opt_spread_short,   // spread short[], etc., to N arguments
    _adapter_opt_spread_int,     // spread int[], short[], etc., to N arguments
    _adapter_opt_spread_long,    // spread long[] to N arguments
    _adapter_opt_spread_float,   // spread float[] to N arguments
    _adapter_opt_spread_double,  // spread double[] to N arguments
    _adapter_opt_spread_FIRST = _adapter_opt_spread_0,
    _adapter_opt_spread_LAST  = _adapter_opt_spread_double,

    // blocking filter/collect conversions
    // These collect N arguments and replace them (at slot S) by a return value
    // which is passed to the final target, along with the unaffected arguments.
    // collect_{N}_{T} collects N arguments at any position into a T value
    // collect_{N}_S{S}_{T} collects N arguments at slot S into a T value
    // collect_{T} collects any number of arguments at any position
    // filter_S{S}_{T} is the same as collect_1_S{S}_{T} (a unary collection)
    // (collect_2 is also usable as a filter, with long or double arguments)
    _adapter_opt_collect_ref,    // combine N arguments, replace with a reference
    _adapter_opt_collect_int,    // combine N arguments, replace with an int, short, etc.
    _adapter_opt_collect_long,   // combine N arguments, replace with a long
    _adapter_opt_collect_float,  // combine N arguments, replace with a float
    _adapter_opt_collect_double, // combine N arguments, replace with a double
    _adapter_opt_collect_void,   // combine N arguments, replace with nothing
    // if there is a small fixed number to push, do so without a loop:
    _adapter_opt_collect_0_ref,  // collect N=0 arguments, insert a reference
    _adapter_opt_collect_1_ref,  // collect N=1 argument, replace with a reference
    _adapter_opt_collect_2_ref,  // combine N=2 arguments, replace with a reference
    _adapter_opt_collect_3_ref,  // combine N=3 arguments, replace with a reference
    _adapter_opt_collect_4_ref,  // combine N=4 arguments, replace with a reference
    _adapter_opt_collect_5_ref,  // combine N=5 arguments, replace with a reference
    // filters are an important special case because they never move arguments:
    _adapter_opt_filter_S0_ref,  // filter N=1 argument at S=0, replace with a reference
    _adapter_opt_filter_S1_ref,  // filter N=1 argument at S=1, replace with a reference
    _adapter_opt_filter_S2_ref,  // filter N=1 argument at S=2, replace with a reference
    _adapter_opt_filter_S3_ref,  // filter N=1 argument at S=3, replace with a reference
    _adapter_opt_filter_S4_ref,  // filter N=1 argument at S=4, replace with a reference
    _adapter_opt_filter_S5_ref,  // filter N=1 argument at S=5, replace with a reference
    // these move arguments, but they are important for boxing
    _adapter_opt_collect_2_S0_ref,  // combine last N=2 arguments, replace with a reference
    _adapter_opt_collect_2_S1_ref,  // combine N=2 arguments at S=1, replace with a reference
    _adapter_opt_collect_2_S2_ref,  // combine N=2 arguments at S=2, replace with a reference
    _adapter_opt_collect_2_S3_ref,  // combine N=2 arguments at S=3, replace with a reference
    _adapter_opt_collect_2_S4_ref,  // combine N=2 arguments at S=4, replace with a reference
    _adapter_opt_collect_2_S5_ref,  // combine N=2 arguments at S=5, replace with a reference
    _adapter_opt_collect_FIRST = _adapter_opt_collect_ref,
    _adapter_opt_collect_LAST  = _adapter_opt_collect_2_S5_ref,

    // blocking folding conversions
    // these are like collects, but retain all the N arguments for the final target
    //_adapter_opt_fold_0_ref,   // same as _adapter_opt_collect_0_ref
    // fold_{N}_{T} processes N arguments at any position into a T value, which it inserts
    // fold_{T} processes any number of arguments at any position
    _adapter_opt_fold_ref,       // process N arguments, prepend a reference
    _adapter_opt_fold_int,       // process N arguments, prepend an int, short, etc.
    _adapter_opt_fold_long,      // process N arguments, prepend a long
    _adapter_opt_fold_float,     // process N arguments, prepend a float
    _adapter_opt_fold_double,    // process N arguments, prepend a double
    _adapter_opt_fold_void,      // process N arguments, but leave the list unchanged
    _adapter_opt_fold_1_ref,     // process N=1 argument, prepend a reference
    _adapter_opt_fold_2_ref,     // process N=2 arguments, prepend a reference
    _adapter_opt_fold_3_ref,     // process N=3 arguments, prepend a reference
    _adapter_opt_fold_4_ref,     // process N=4 arguments, prepend a reference
    _adapter_opt_fold_5_ref,     // process N=5 arguments, prepend a reference
    _adapter_opt_fold_FIRST = _adapter_opt_fold_ref,
    _adapter_opt_fold_LAST  = _adapter_opt_fold_5_ref,

    _adapter_opt_profiling,

    _EK_LIMIT,
    _EK_FIRST = 0
  };

 public:
  static bool enabled()                         { return _enabled; }
  static void set_enabled(bool z);

 private:
  enum {  // import java_lang_invoke_AdapterMethodHandle::CONV_OP_*
    CONV_OP_LIMIT         = java_lang_invoke_AdapterMethodHandle::CONV_OP_LIMIT,
    CONV_OP_MASK          = java_lang_invoke_AdapterMethodHandle::CONV_OP_MASK,
    CONV_TYPE_MASK        = java_lang_invoke_AdapterMethodHandle::CONV_TYPE_MASK,
    CONV_VMINFO_MASK      = java_lang_invoke_AdapterMethodHandle::CONV_VMINFO_MASK,
    CONV_VMINFO_SHIFT     = java_lang_invoke_AdapterMethodHandle::CONV_VMINFO_SHIFT,
    CONV_OP_SHIFT         = java_lang_invoke_AdapterMethodHandle::CONV_OP_SHIFT,
    CONV_DEST_TYPE_SHIFT  = java_lang_invoke_AdapterMethodHandle::CONV_DEST_TYPE_SHIFT,
    CONV_SRC_TYPE_SHIFT   = java_lang_invoke_AdapterMethodHandle::CONV_SRC_TYPE_SHIFT,
    CONV_STACK_MOVE_SHIFT = java_lang_invoke_AdapterMethodHandle::CONV_STACK_MOVE_SHIFT,
    CONV_STACK_MOVE_MASK  = java_lang_invoke_AdapterMethodHandle::CONV_STACK_MOVE_MASK
  };

  static bool _enabled;
  static MethodHandleEntry* _entries[_EK_LIMIT];
  static const char*        _entry_names[_EK_LIMIT+1];
  static jobject            _raise_exception_method;
  static address            _adapter_return_handlers[CONV_TYPE_MASK+1];

  // Adapters.
  static MethodHandlesAdapterBlob* _adapter_code;

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
  static EntryKind ek_original_kind(EntryKind ek) {
    if (ek <= _adapter_mh_last)  return ek;
    switch (ek) {
    case _adapter_opt_swap_1:
    case _adapter_opt_swap_2:
      return _adapter_swap_args;
    case _adapter_opt_rot_1_up:
    case _adapter_opt_rot_1_down:
    case _adapter_opt_rot_2_up:
    case _adapter_opt_rot_2_down:
      return _adapter_rot_args;
    case _adapter_opt_i2i:
    case _adapter_opt_l2i:
    case _adapter_opt_d2f:
    case _adapter_opt_i2l:
    case _adapter_opt_f2d:
      return _adapter_prim_to_prim;
    case _adapter_opt_unboxi:
    case _adapter_opt_unboxl:
      return _adapter_ref_to_prim;
    }
    if (ek >= _adapter_opt_spread_FIRST && ek <= _adapter_opt_spread_LAST)
      return _adapter_spread_args;
    if (ek >= _adapter_opt_collect_FIRST && ek <= _adapter_opt_collect_LAST)
      return _adapter_collect_args;
    if (ek >= _adapter_opt_fold_FIRST && ek <= _adapter_opt_fold_LAST)
      return _adapter_fold_args;
    if (ek >= _adapter_opt_return_FIRST && ek <= _adapter_opt_return_LAST)
      return _adapter_opt_return_any;
    if (ek == _adapter_opt_profiling)
      return _adapter_retype_only;
    assert(false, "oob");
    return _EK_LIMIT;
  }

  static bool ek_supported(MethodHandles::EntryKind ek);

  static BasicType ek_bound_mh_arg_type(EntryKind ek) {
    switch (ek) {
    case _bound_int_mh         : // fall-thru
    case _bound_int_direct_mh  : return T_INT;
    case _bound_long_mh        : // fall-thru
    case _bound_long_direct_mh : return T_LONG;
    default                    : return T_OBJECT;
    }
  }

  static int ek_adapter_opt_swap_slots(EntryKind ek) {
    switch (ek) {
    case _adapter_opt_swap_1        : return  1;
    case _adapter_opt_swap_2        : return  2;
    case _adapter_opt_rot_1_up      : return  1;
    case _adapter_opt_rot_1_down    : return  1;
    case _adapter_opt_rot_2_up      : return  2;
    case _adapter_opt_rot_2_down    : return  2;
    default : ShouldNotReachHere();   return -1;
    }
  }

  static int ek_adapter_opt_swap_mode(EntryKind ek) {
    switch (ek) {
    case _adapter_opt_swap_1       : return  0;
    case _adapter_opt_swap_2       : return  0;
    case _adapter_opt_rot_1_up     : return  1;
    case _adapter_opt_rot_1_down   : return -1;
    case _adapter_opt_rot_2_up     : return  1;
    case _adapter_opt_rot_2_down   : return -1;
    default : ShouldNotReachHere();  return  0;
    }
  }

  static int ek_adapter_opt_collect_count(EntryKind ek) {
    assert(ek >= _adapter_opt_collect_FIRST && ek <= _adapter_opt_collect_LAST ||
           ek >= _adapter_opt_fold_FIRST    && ek <= _adapter_opt_fold_LAST, "");
    switch (ek) {
    case _adapter_opt_collect_0_ref    : return  0;
    case _adapter_opt_filter_S0_ref    :
    case _adapter_opt_filter_S1_ref    :
    case _adapter_opt_filter_S2_ref    :
    case _adapter_opt_filter_S3_ref    :
    case _adapter_opt_filter_S4_ref    :
    case _adapter_opt_filter_S5_ref    :
    case _adapter_opt_fold_1_ref       :
    case _adapter_opt_collect_1_ref    : return  1;
    case _adapter_opt_collect_2_S0_ref :
    case _adapter_opt_collect_2_S1_ref :
    case _adapter_opt_collect_2_S2_ref :
    case _adapter_opt_collect_2_S3_ref :
    case _adapter_opt_collect_2_S4_ref :
    case _adapter_opt_collect_2_S5_ref :
    case _adapter_opt_fold_2_ref       :
    case _adapter_opt_collect_2_ref    : return  2;
    case _adapter_opt_fold_3_ref       :
    case _adapter_opt_collect_3_ref    : return  3;
    case _adapter_opt_fold_4_ref       :
    case _adapter_opt_collect_4_ref    : return  4;
    case _adapter_opt_fold_5_ref       :
    case _adapter_opt_collect_5_ref    : return  5;
    default                            : return -1;  // sentinel value for "variable"
    }
  }

  static int ek_adapter_opt_collect_slot(EntryKind ek) {
    assert(ek >= _adapter_opt_collect_FIRST && ek <= _adapter_opt_collect_LAST ||
           ek >= _adapter_opt_fold_FIRST    && ek <= _adapter_opt_fold_LAST, "");
    switch (ek) {
    case _adapter_opt_collect_2_S0_ref  :
    case _adapter_opt_filter_S0_ref     : return 0;
    case _adapter_opt_collect_2_S1_ref  :
    case _adapter_opt_filter_S1_ref     : return 1;
    case _adapter_opt_collect_2_S2_ref  :
    case _adapter_opt_filter_S2_ref     : return 2;
    case _adapter_opt_collect_2_S3_ref  :
    case _adapter_opt_filter_S3_ref     : return 3;
    case _adapter_opt_collect_2_S4_ref  :
    case _adapter_opt_filter_S4_ref     : return 4;
    case _adapter_opt_collect_2_S5_ref  :
    case _adapter_opt_filter_S5_ref     : return 5;
    default                             : return -1;  // sentinel value for "variable"
    }
  }

  static BasicType ek_adapter_opt_collect_type(EntryKind ek) {
    assert(ek >= _adapter_opt_collect_FIRST && ek <= _adapter_opt_collect_LAST ||
           ek >= _adapter_opt_fold_FIRST    && ek <= _adapter_opt_fold_LAST, "");
    switch (ek) {
    case _adapter_opt_fold_int          :
    case _adapter_opt_collect_int       : return T_INT;
    case _adapter_opt_fold_long         :
    case _adapter_opt_collect_long      : return T_LONG;
    case _adapter_opt_fold_float        :
    case _adapter_opt_collect_float     : return T_FLOAT;
    case _adapter_opt_fold_double       :
    case _adapter_opt_collect_double    : return T_DOUBLE;
    case _adapter_opt_fold_void         :
    case _adapter_opt_collect_void      : return T_VOID;
    default                             : return T_OBJECT;
    }
  }

  static int ek_adapter_opt_return_slot(EntryKind ek) {
    assert(ek >= _adapter_opt_return_FIRST && ek <= _adapter_opt_return_LAST, "");
    switch (ek) {
    case _adapter_opt_return_S0_ref : return 0;
    case _adapter_opt_return_S1_ref : return 1;
    case _adapter_opt_return_S2_ref : return 2;
    case _adapter_opt_return_S3_ref : return 3;
    case _adapter_opt_return_S4_ref : return 4;
    case _adapter_opt_return_S5_ref : return 5;
    default                         : return -1;  // sentinel value for "variable"
    }
  }

  static BasicType ek_adapter_opt_return_type(EntryKind ek) {
    assert(ek >= _adapter_opt_return_FIRST && ek <= _adapter_opt_return_LAST, "");
    switch (ek) {
    case _adapter_opt_return_int    : return T_INT;
    case _adapter_opt_return_long   : return T_LONG;
    case _adapter_opt_return_float  : return T_FLOAT;
    case _adapter_opt_return_double : return T_DOUBLE;
    case _adapter_opt_return_void   : return T_VOID;
    case _adapter_opt_return_any    : return T_CONFLICT;  // sentinel value for "variable"
    default                         : return T_OBJECT;
    }
  }

  static int ek_adapter_opt_spread_count(EntryKind ek) {
    assert(ek >= _adapter_opt_spread_FIRST && ek <= _adapter_opt_spread_LAST, "");
    switch (ek) {
    case _adapter_opt_spread_0     : return  0;
    case _adapter_opt_spread_1_ref : return  1;
    case _adapter_opt_spread_2_ref : return  2;
    case _adapter_opt_spread_3_ref : return  3;
    case _adapter_opt_spread_4_ref : return  4;
    case _adapter_opt_spread_5_ref : return  5;
    default                        : return -1;  // sentinel value for "variable"
    }
  }

  static BasicType ek_adapter_opt_spread_type(EntryKind ek) {
    assert(ek >= _adapter_opt_spread_FIRST && ek <= _adapter_opt_spread_LAST, "");
    switch (ek) {
    // (there is no _adapter_opt_spread_boolean; we use byte)
    case _adapter_opt_spread_byte   : return T_BYTE;
    case _adapter_opt_spread_char   : return T_CHAR;
    case _adapter_opt_spread_short  : return T_SHORT;
    case _adapter_opt_spread_int    : return T_INT;
    case _adapter_opt_spread_long   : return T_LONG;
    case _adapter_opt_spread_float  : return T_FLOAT;
    case _adapter_opt_spread_double : return T_DOUBLE;
    default                         : return T_OBJECT;
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
  static methodOop resolve_raise_exception_method(TRAPS);
  // call raise_exception_method from C code:
  static void raise_exception(int code, oop actual, oop required, TRAPS);

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

  static bool conv_op_supported(int conv_op) {
    assert(conv_op_valid(conv_op), "");
    return ((adapter_conversion_ops_supported_mask() & nth_bit(conv_op)) != 0);
  }

  // Offset in words that the interpreter stack pointer moves when an argument is pushed.
  // The stack_move value must always be a multiple of this.
  static int stack_move_unit() {
    return frame::interpreter_frame_expression_stack_direction() * Interpreter::stackElementWords;
  }

  // Adapter frame traversal.  (Implementation-specific.)
  static frame ricochet_frame_sender(const frame& fr, RegisterMap* reg_map);
  static void ricochet_frame_oops_do(const frame& fr, OopClosure* blk, const RegisterMap* reg_map);

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
    int shift = vminfo & ~CONV_VMINFO_SIGN_FLAG;
    jint tem = value << shift;
    if ((vminfo & CONV_VMINFO_SIGN_FLAG) != 0) {
      return (jint)tem >> shift;
    } else {
      return (juint)tem >> shift;
    }
  }

  static inline address from_compiled_entry(EntryKind ek);
  static inline address from_interpreted_entry(EntryKind ek);

  // helpers for decode_method.
  static methodOop    decode_methodOop(methodOop m, int& decode_flags_result);
  static methodHandle decode_vmtarget(oop vmtarget, int vmindex, oop mtype, KlassHandle& receiver_limit_result, int& decode_flags_result);
  static methodHandle decode_MemberName(oop mname, KlassHandle& receiver_limit_result, int& decode_flags_result);
  static methodHandle decode_MethodHandle(oop mh, KlassHandle& receiver_limit_result, int& decode_flags_result);
  static methodHandle decode_DirectMethodHandle(oop mh, KlassHandle& receiver_limit_result, int& decode_flags_result);
  static methodHandle decode_BoundMethodHandle(oop mh, KlassHandle& receiver_limit_result, int& decode_flags_result);
  static methodHandle decode_AdapterMethodHandle(oop mh, KlassHandle& receiver_limit_result, int& decode_flags_result);

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
  static int find_MemberNames(klassOop k, Symbol* name, Symbol* sig,
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
  static methodHandle decode_method(oop x, KlassHandle& receiver_limit_result, int& decode_flags_result);
  enum {
    // format of query to getConstant:
    GC_JVM_PUSH_LIMIT = 0,
    GC_JVM_STACK_MOVE_UNIT = 1,
    GC_CONV_OP_IMPLEMENTED_MASK = 2,
    GC_OP_ROT_ARGS_DOWN_LIMIT_BIAS = 3,
    GC_COUNT_GWT = 4,

    // format of result from getTarget / encode_target:
    ETF_HANDLE_OR_METHOD_NAME = 0, // all available data (immediate MH or method)
    ETF_DIRECT_HANDLE         = 1, // ultimate method handle (will be a DMH, may be self)
    ETF_METHOD_NAME           = 2, // ultimate method as MemberName
    ETF_REFLECT_METHOD        = 3, // ultimate method as java.lang.reflect object (sans refClass)
    ETF_FORCE_DIRECT_HANDLE   = 64,
    ETF_COMPILE_DIRECT_HANDLE = 65,

    // ad hoc constants
    OP_ROT_ARGS_DOWN_LIMIT_BIAS = -1
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
  static void ensure_vmlayout_field(Handle target, TRAPS);

#ifdef ASSERT
  static bool spot_check_entry_names();
#endif

 private:
  static methodHandle dispatch_decoded_method(methodHandle m,
                                              KlassHandle receiver_limit,
                                              int decode_flags,
                                              KlassHandle receiver_klass,
                                              TRAPS);

public:
  static bool is_float_fixed_reinterpretation_cast(BasicType src, BasicType dst);
  static bool same_basic_type_for_arguments(BasicType src, BasicType dst,
                                            bool raw = false,
                                            bool for_return = false);
  static bool same_basic_type_for_returns(BasicType src, BasicType dst, bool raw = false) {
    return same_basic_type_for_arguments(src, dst, raw, true);
  }

  static Symbol* convert_to_signature(oop type_str, bool polymorphic, TRAPS);

#ifdef TARGET_ARCH_x86
# include "methodHandles_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "methodHandles_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "methodHandles_zero.hpp"
#endif
#ifdef TARGET_ARCH_arm
# include "methodHandles_arm.hpp"
#endif
#ifdef TARGET_ARCH_ppc
# include "methodHandles_ppc.hpp"
#endif
};


// Access methods for the "entry" field of a java.lang.invoke.MethodHandle.
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
  MethodHandlesAdapterGenerator(CodeBuffer* code) : StubCodeGenerator(code, PrintMethodHandleStubs) {}

  void generate();
};

#endif // SHARE_VM_PRIMS_METHODHANDLES_HPP
