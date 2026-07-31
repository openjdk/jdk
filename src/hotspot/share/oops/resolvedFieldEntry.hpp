/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_RESOLVEDFIELDENTRY_HPP
#define SHARE_OOPS_RESOLVEDFIELDENTRY_HPP

#include "interpreter/bytecodes.hpp"
#include "runtime/atomicAccess.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/sizes.hpp"

// ResolvedFieldEntry contains the resolution information for field related bytecodes like
// like getfield, putfield, getstatic, and putstatic. A member of this class can be initialized
// with the constant pool index associated with the bytecode before any resolution is done, where
// "resolution" refers to populating the getcode and putcode fields and other relevant information.
// The field's type (TOS), offset, holder klass, and index within that class can all be acquired
// together and are used to populate this structure. These entries are contained
// within the ConstantPoolCache and are accessed with indices added to the bytecode after
// rewriting.

// Field bytecodes start with a constant pool index as their operand, which is then rewritten to
// a "field index", which is an index into the array of ResolvedFieldEntry.

// The explicit paddings are necessary for generating deterministic CDS archives. They prevent
// the C++ compiler from potentially inserting random values in unused gaps.

class InstanceKlass;

class ResolvedFieldEntry {
  friend class VMStructs;

  InstanceKlass* _field_holder; // Field holder klass
  int _field_offset;            // Field offset in bytes
  u2 _field_index;              // Index into field information in holder InstanceKlass
  u2 _cpool_index;              // Constant pool index
  u1 _tos_state;                // TOS state
  u1 _flags;                    // Flags: [000|has_null_marker|is_null_free_inline_type|is_flat|is_final|is_volatile]
  u1 _get_code, _put_code;      // Get and Put bytecodes of the field
#ifdef _LP64
  u4 _padding;
#endif

public:
  ResolvedFieldEntry(u2 cpi) :
    _field_holder(nullptr),
    _field_offset(0),
    _field_index(0),
    _cpool_index(cpi),
    _tos_state(0),
    _flags(0),
    _get_code(0),
    _put_code(0)
#ifdef _LP64
    , _padding(0)
#endif
    {}

  ResolvedFieldEntry() :
    ResolvedFieldEntry(0) {}

  // Bit shift to get flags
  enum {
      is_volatile_shift     = 0,
      is_final_shift        = 1, // unused
      is_flat_shift         = 2,
      is_null_free_inline_type_shift = 3,
      has_null_marker_shift = 4,
      max_flag_shift = has_null_marker_shift
  };

  // Getters
  InstanceKlass* field_holder()   const { return _field_holder; }
  int field_offset()              const { return _field_offset; }
  u2 field_index()                const { return _field_index;  }
  u2 constant_pool_index()        const { return _cpool_index;  }
  u1 tos_state()                  const { return _tos_state;    }
  u1 get_code()                   const { return AtomicAccess::load_acquire(&_get_code);   }
  u1 put_code()                   const { return AtomicAccess::load_acquire(&_put_code);   }
  bool is_volatile ()             const { return (_flags & (1 << is_volatile_shift)) != 0; }
  bool is_final()                 const { return (_flags & (1 << is_final_shift))    != 0; }
  bool is_flat()                  const { return (_flags & (1 << is_flat_shift))     != 0; }
  bool is_null_free_inline_type() const { return (_flags & (1 << is_null_free_inline_type_shift)) != 0; }
  bool has_null_marker()          const { return (_flags & (1 << has_null_marker_shift)) != 0; }
  bool is_resolved(Bytecodes::Code code) const {
    switch(code) {
    case Bytecodes::_getstatic:
    case Bytecodes::_getfield:
      return (get_code() == code);
    case Bytecodes::_putstatic:
    case Bytecodes::_putfield:
      return (put_code() == code);
    default:
      ShouldNotReachHere();
      return false;
    }
  }

  // Printing
  void print_on(outputStream* st) const;

 private:
  void set_flags(bool is_volatile_flag,
                 bool is_final_flag,
                 bool is_flat_flag,
                 bool is_null_free_inline_type_flag,
                 bool has_null_marker_flag) {
    int new_flags =
        ((is_volatile_flag ? 1 : 0) << is_volatile_shift) |
        ((is_final_flag ? 1 : 0) << is_final_shift) |
        ((is_flat_flag ? 1 : 0) << is_flat_shift) |
        ((is_null_free_inline_type_flag ? 1 : 0) << is_null_free_inline_type_shift) |
        ((has_null_marker_flag  ? 1 : 0) << has_null_marker_shift);
    _flags = checked_cast<u1>(new_flags);
    assert(is_volatile() == is_volatile_flag, "Must be");
    assert(is_final() == is_final_flag, "Must be");
    assert(is_flat() == is_flat_flag, "Must be");
    assert(is_null_free_inline_type() == is_null_free_inline_type_flag, "Must be");
    assert(has_null_marker() == has_null_marker_flag, "Must be");
  }

  inline void set_bytecode(u1* code, u1 new_code) {
  #ifdef ASSERT
    // Read once.
    volatile Bytecodes::Code c = (Bytecodes::Code)*code;
    assert(c == 0 || c == new_code || new_code == 0, "update must be consistent");
  #endif
    AtomicAccess::release_store(code, new_code);
  }

   // Debug help
  void assert_is_valid() const NOT_DEBUG_RETURN;

 public:
  // Populate the strucutre with resolution information
  void fill_in(const fieldDescriptor& info, u1 tos_state, u1 get_code, u1 put_code);

  // CDS
#if INCLUDE_CDS
  void remove_unshareable_info();
  void mark_and_relocate();
#endif

  // Offsets
  static ByteSize field_holder_offset() { return byte_offset_of(ResolvedFieldEntry, _field_holder); }
  static ByteSize field_offset_offset() { return byte_offset_of(ResolvedFieldEntry, _field_offset); }
  static ByteSize field_index_offset()  { return byte_offset_of(ResolvedFieldEntry, _field_index);  }
  static ByteSize get_code_offset()     { return byte_offset_of(ResolvedFieldEntry, _get_code);     }
  static ByteSize put_code_offset()     { return byte_offset_of(ResolvedFieldEntry, _put_code);     }
  static ByteSize type_offset()         { return byte_offset_of(ResolvedFieldEntry, _tos_state);    }
  static ByteSize flags_offset()        { return byte_offset_of(ResolvedFieldEntry, _flags);        }
};

#endif //SHARE_OOPS_RESOLVEDFIELDENTRY_HPP
