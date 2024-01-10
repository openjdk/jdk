/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_RESOLVEDMETHODENTRY_HPP
#define SHARE_OOPS_RESOLVEDMETHODENTRY_HPP

#include "interpreter/bytecodes.hpp"
#include "runtime/atomic.hpp"
#include "utilities/sizes.hpp"

// ResolvedMethodEntry contains the resolution information for the invoke bytecodes
// invokestatic, invokespecial, invokeinterface, invokevirtual, and invokehandle but
// NOT invokedynamic (see resolvedIndyEntry.hpp). A member of this class can be initialized
// with the constant pool index associated with the bytecode before any resolution is done,
// where "resolution" refers to populating the bytecode1 and bytecode2 fields and other
// relevant information. These entries are contained within the ConstantPoolCache and are
// accessed with indices added to the bytecode after rewriting.

// Invoke bytecodes start with a constant pool index as their operand, which is then
// rewritten to a "method index", which is an index into the array of ResolvedMethodEntry.
// This structure has fields for every type of invoke bytecode but each entry may only
// use some of the fields. All entries have a TOS state, number of parameters, flags,
// and a constant pool index.

// Types of invokes
// invokestatic
// invokespecial
//   Method*
// invokehandle
//   Method*
//   resolved references index
// invokevirtual
//   Method* (if vfinal is true)
//   vtable/itable index
// invokeinterface
//   Klass*
//   Method*

// Note: invokevirtual & invokespecial bytecodes can share the same constant
//       pool entry and thus the same resolved method entry.
// The is_vfinal flag indicates method pointer for a final method or an index.

class InstanceKlass;
class ResolvedMethodEntry {
  friend class VMStructs;

  Method* _method;                   // Method for non virtual calls, adapter method for invokevirtual, final method for virtual
  union {                            // These fields are mutually exclusive and are only used by some invoke codes
    InstanceKlass* _interface_klass; // for interface and static
    u2 _resolved_references_index;   // Index of resolved references array that holds the appendix oop for invokehandle
    u2 _table_index;                 // vtable/itable index for virtual and interface calls
  } _entry_specific;

  u2 _cpool_index;                   // Constant pool index
  u2 _number_of_parameters;          // Number of arguments for method
  u1 _tos_state;                     // TOS state
  u1 _flags;                         // Flags: [00|has_resolved_ref_index|has_local_signature|has_appendix|forced_virtual|final|virtual_final]
  u1 _bytecode1, _bytecode2;         // Resolved invoke codes
#ifdef ASSERT
  bool _has_interface_klass;
  bool _has_table_index;
#endif

  // Constructors
  public:
    ResolvedMethodEntry(u2 cpi) :
      _method(nullptr),
      _cpool_index(cpi),
      _number_of_parameters(0),
      _tos_state(0),
      _flags(0),
      _bytecode1(0),
      _bytecode2(0) {
        _entry_specific._interface_klass = nullptr;
        DEBUG_ONLY(_has_interface_klass = false;)
        DEBUG_ONLY(_has_table_index = false;)
      }
    ResolvedMethodEntry() :
      ResolvedMethodEntry(0) {}

  // Bit shift to get flags
  enum {
      is_vfinal_shift           = 0,
      is_final_shift            = 1,
      is_forced_virtual_shift   = 2,
      has_appendix_shift        = 3,
      has_local_signature_shift = 4,
      has_resolved_ref_shift    = 5
  };

  // Flags
  bool is_vfinal()                     const { return (_flags & (1 << is_vfinal_shift))           != 0; }
  bool is_final()                      const { return (_flags & (1 << is_final_shift))            != 0; }
  bool is_forced_virtual()             const { return (_flags & (1 << is_forced_virtual_shift))   != 0; }
  bool has_appendix()                  const { return (_flags & (1 << has_appendix_shift))        != 0; }
  bool has_local_signature()           const { return (_flags & (1 << has_local_signature_shift)) != 0; }
  bool has_resolved_references_index() const { return (_flags & (1 << has_resolved_ref_shift))    != 0; }

  // Getters
  Method* method() const { return Atomic::load_acquire(&_method); }
  InstanceKlass* interface_klass() const {
    assert(_bytecode1 == Bytecodes::_invokeinterface, "Only invokeinterface has a klass %d", _bytecode1);
    assert(_has_interface_klass, "sanity");
    return _entry_specific._interface_klass;
  }
  u2 resolved_references_index() const {
    // This index may be read before resolution completes
    assert(has_resolved_references_index(), "sanity");
    return _entry_specific._resolved_references_index;
  }
  u2 table_index() const {
    assert(_bytecode2 == Bytecodes::_invokevirtual, "Only invokevirtual has a vtable/itable index %d", _bytecode2);
    assert(_has_table_index, "sanity");
    return _entry_specific._table_index;
  }
  u2 constant_pool_index() const { return _cpool_index; }
  u1 tos_state() const { return _tos_state; }
  u2 number_of_parameters() const { return _number_of_parameters; }
  u1 bytecode1() const { return Atomic::load_acquire(&_bytecode1); }
  u1 bytecode2() const { return Atomic::load_acquire(&_bytecode2); }

  bool is_resolved(Bytecodes::Code code) const {
    switch(code) {
      case Bytecodes::_invokeinterface:
      case Bytecodes::_invokehandle:
      case Bytecodes::_invokespecial:
      case Bytecodes::_invokestatic:
        return (bytecode1() == code);
      case Bytecodes::_invokevirtual:
        return (bytecode2() == code);
    default:
      ShouldNotReachHere();
      return false;
    }
  }

  void adjust_method_entry(Method* new_method) {
    // this is done during the redefinition safepoint
    _method = new_method;
  }
  bool check_no_old_or_obsolete_entry();

  // Printing
  void print_on(outputStream* st) const;

  // Setters
  void set_flags(u1 flags) { _flags |= flags; }

  inline void set_bytecode(u1* code, u1 new_code) {
  #ifdef ASSERT
    // Read once.
    volatile Bytecodes::Code c = (Bytecodes::Code)*code;
    assert(c == 0 || c == new_code || new_code == 0, "update must be consistent old: %d, new: %d", c, new_code);
  #endif
    Atomic::release_store(code, new_code);
  }

  void set_bytecode1(u1 b1) {
    set_bytecode(&_bytecode1, b1);
  }

  void set_bytecode2(u1 b2) {
    set_bytecode(&_bytecode2, b2);
  }

  void set_method(Method* m) {
    Atomic::release_store(&_method, m);
  }

  void set_klass(InstanceKlass* klass) {
    assert(!has_resolved_references_index() &&
           !_has_table_index,
           "Mutually exclusive fields %d %d %d", has_resolved_references_index(), _has_interface_klass, _has_table_index);
    DEBUG_ONLY(_has_interface_klass = true;)
    _entry_specific._interface_klass = klass;
  }

  void set_resolved_references_index(u2 ref_index) {
    assert(!_has_interface_klass &&
           !_has_table_index,
           "Mutually exclusive fields %d %d %d", has_resolved_references_index(), _has_interface_klass, _has_table_index);
    set_flags(1 << has_resolved_ref_shift);
    _entry_specific._resolved_references_index = ref_index;
  }

  void set_table_index(u2 table_index) {
    assert(!has_resolved_references_index() &&
           !_has_interface_klass,
           "Mutually exclusive fields %d %d %d", has_resolved_references_index(), _has_interface_klass, _has_table_index);
    DEBUG_ONLY(_has_table_index = true;)
    _entry_specific._table_index = table_index;
  }

  void set_num_parameters(u2 num_params) {
    _number_of_parameters = num_params;
  }

  void fill_in(u1 tos_state, u2 num_params) {
    _tos_state = tos_state;
    _number_of_parameters = num_params;
  }

  void reset_entry();

  // CDS
  void remove_unshareable_info();

  // Offsets
  static ByteSize klass_offset()                     { return byte_offset_of(ResolvedMethodEntry, _entry_specific._interface_klass); }
  static ByteSize method_offset()                    { return byte_offset_of(ResolvedMethodEntry, _method);       }
  static ByteSize resolved_references_index_offset() { return byte_offset_of(ResolvedMethodEntry, _entry_specific._resolved_references_index); }
  static ByteSize table_index_offset()               { return byte_offset_of(ResolvedMethodEntry, _entry_specific._table_index);       }
  static ByteSize num_parameters_offset()            { return byte_offset_of(ResolvedMethodEntry, _number_of_parameters);      }
  static ByteSize type_offset()                      { return byte_offset_of(ResolvedMethodEntry, _tos_state); }
  static ByteSize flags_offset()                     { return byte_offset_of(ResolvedMethodEntry, _flags);        }
  static ByteSize bytecode1_offset()                 { return byte_offset_of(ResolvedMethodEntry, _bytecode1);        }
  static ByteSize bytecode2_offset()                 { return byte_offset_of(ResolvedMethodEntry, _bytecode2);        }

};

#endif //SHARE_OOPS_RESOLVEDMETHODENTRY_HPP
