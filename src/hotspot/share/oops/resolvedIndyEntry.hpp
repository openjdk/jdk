/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_RESOLVEDINDYENTRY_HPP
#define SHARE_OOPS_RESOLVEDINDYENTRY_HPP

// ResolvedIndyEntry contains the resolution information for invokedynamic bytecodes.
// A member of this class can be initialized with the resolved references index and
// constant pool index before any resolution is done, where "resolution" refers to finding the target
// method and its relevant information, like number of parameters and return type. These entries are contained
// within the ConstantPoolCache and are accessed with indices added to the invokedynamic bytecode after
// rewriting.

// The invokedynamic bytecode starts with an Constant Pool index as its operand which is then rewritten
// to become an "indy index", an index into the array of ResolvedIndyEntry. The method here is an adapter method
// which will be something like linkToTargetMethod. When an indy call is resolved, we no longer need to invoke
// the bootstrap method (BSM) and we can get the target method (the method actually doing stuff, i.e. a string concat)
// from the CallSite. The CallSite is generated when the BSM is invoked and it simply contains a MethodHandle for
// the target method. The adapter will propagate information to and from the target method and the JVM.


class Method;
class ResolvedIndyEntry {
  friend class VMStructs;

  Method* _method;               // Adapter method for indy call
  u2 _resolved_references_index; // Index of resolved references array that holds the appendix oop
  u2 _cpool_index;               // Constant pool index
  u2 _number_of_parameters;      // Number of arguments for adapter method
  u1 _return_type;               // Adapter method return type
  u1 _flags;                     // Flags: [0000|00|has_appendix|resolution_failed]

public:
  ResolvedIndyEntry() :
    _method(nullptr),
    _resolved_references_index(0),
    _cpool_index(0),
    _number_of_parameters(0),
    _return_type(0),
    _flags(0) {}
  ResolvedIndyEntry(u2 resolved_references_index, u2 cpool_index) :
    _method(nullptr),
    _resolved_references_index(resolved_references_index),
    _cpool_index(cpool_index),
    _number_of_parameters(0),
    _return_type(0),
    _flags(0) {}

  // Bit shift to get flags
  enum {
    resolution_failed_shift = 0,
    has_appendix_shift      = 1,
  };

  // Getters
  Method* method()               const { return Atomic::load_acquire(&_method); }
  u2 resolved_references_index() const { return _resolved_references_index;     }
  u2 constant_pool_index()       const { return _cpool_index;                   }
  u2 num_parameters()            const { return _number_of_parameters;          }
  u1 return_type()               const { return _return_type;                   }
  bool is_resolved()             const { return method() != nullptr;            }
  bool has_appendix()            const { return (_flags & (1 << has_appendix_shift)) != 0; }
  bool resolution_failed()       const { return (_flags & 1) != 0; }
  bool is_vfinal()               const { return false; }
  bool is_final()                const { return false; }
  bool has_local_signature()     const { return true;  }

  // Printing
  void print_on(outputStream* st) const;

  // Initialize with fields available before resolution
  void init(u2 resolved_references_index, u2 cpool_index) {
    _resolved_references_index = resolved_references_index;
    _cpool_index = cpool_index;
  }

  void set_num_parameters(int value) {
    assert(_number_of_parameters == 0 || _number_of_parameters == value,
      "size must not change: parameter_size=%d, value=%d", _number_of_parameters, value);
    Atomic::store(&_number_of_parameters, (u2)value);
    guarantee(_number_of_parameters == value,
      "size must not change: parameter_size=%d, value=%d", _number_of_parameters, value);
  }

  // Populate structure with resolution information
  void fill_in(Method* m, u2 num_params, u1 return_type, bool has_appendix) {
    set_num_parameters(num_params);
    _return_type = return_type;
    set_has_appendix(has_appendix);
    // Set the method last since it is read lock free.
    // Resolution is indicated by whether or not the method is set.
    Atomic::release_store(&_method, m);
  }

  void set_has_appendix(bool has_appendix) {
    u1 new_flags = (has_appendix ? 1 : 0) << has_appendix_shift;
    u1 old_flags = _flags & ~(1 << has_appendix_shift);
    // Preserve the unaffected bits
    _flags = old_flags | new_flags;
  }


  void set_resolution_failed() {
    _flags = _flags | (1 << resolution_failed_shift);
  }

  void adjust_method_entry(Method* new_method) { _method = new_method; }
  bool check_no_old_or_obsolete_entry();

  // CDS
#if INCLUDE_CDS
  void remove_unshareable_info();
  void mark_and_relocate();
#endif

  // Offsets
  static ByteSize method_offset()                    { return byte_offset_of(ResolvedIndyEntry, _method);                    }
  static ByteSize resolved_references_index_offset() { return byte_offset_of(ResolvedIndyEntry, _resolved_references_index); }
  static ByteSize result_type_offset()               { return byte_offset_of(ResolvedIndyEntry, _return_type);               }
  static ByteSize num_parameters_offset()            { return byte_offset_of(ResolvedIndyEntry, _number_of_parameters);      }
  static ByteSize flags_offset()                     { return byte_offset_of(ResolvedIndyEntry, _flags);                     }
};

#endif // SHARE_OOPS_RESOLVEDINDYENTRY_HPP
