/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_TYPEARRAYKLASS_HPP
#define SHARE_VM_OOPS_TYPEARRAYKLASS_HPP

#include "classfile/classLoaderData.hpp"
#include "oops/arrayKlass.hpp"

// A TypeArrayKlass is the klass of a typeArray
// It contains the type and size of the elements

class TypeArrayKlass : public ArrayKlass {
  friend class VMStructs;
 private:
  jint _max_length;            // maximum number of elements allowed in an array

  // Constructor
  TypeArrayKlass(BasicType type, Symbol* name);
  static TypeArrayKlass* allocate(ClassLoaderData* loader_data, BasicType type, Symbol* name, TRAPS);
 public:
  TypeArrayKlass() {} // For dummy objects.

  // instance variables
  jint max_length()                     { return _max_length; }
  void set_max_length(jint m)           { _max_length = m;    }

  // testers
  DEBUG_ONLY(bool is_typeArray_klass_slow() const  { return true; })

  // klass allocation
  static TypeArrayKlass* create_klass(BasicType type, const char* name_str,
                               TRAPS);
  static inline Klass* create_klass(BasicType type, int scale, TRAPS) {
    TypeArrayKlass* tak = create_klass(type, external_name(type), CHECK_NULL);
    assert(scale == (1 << tak->log2_element_size()), "scale must check out");
    return tak;
  }

  int oop_size(oop obj) const;

  bool compute_is_subtype_of(Klass* k);

  // Allocation
  typeArrayOop allocate_common(int length, bool do_zero, TRAPS);
  typeArrayOop allocate(int length, TRAPS) { return allocate_common(length, true, THREAD); }
  oop multi_allocate(int rank, jint* sizes, TRAPS);

  oop protection_domain() const { return NULL; }

  // Copying
  void  copy_array(arrayOop s, int src_pos, arrayOop d, int dst_pos, int length, TRAPS);

  // GC specific object visitors
  //
  // Mark Sweep
  int  oop_ms_adjust_pointers(oop obj);
#if INCLUDE_ALL_GCS
  // Parallel Scavenge
  void oop_ps_push_contents(  oop obj, PSPromotionManager* pm);
  // Parallel Compact
  void oop_pc_follow_contents(oop obj, ParCompactionManager* cm);
  void oop_pc_update_pointers(oop obj);
#endif

  // Oop iterators. Since there are no oops in TypeArrayKlasses,
  // these functions only return the size of the object.

 private:
  // The implementation used by all oop_oop_iterate functions in TypeArrayKlasses.
  inline void oop_oop_iterate_impl(oop obj, ExtendedOopClosure* closure);

  // Wraps oop_oop_iterate_impl to conform to macros.
  template <bool nv, typename OopClosureType>
  inline void oop_oop_iterate(oop obj, OopClosureType* closure);

  // Wraps oop_oop_iterate_impl to conform to macros.
  template <bool nv, typename OopClosureType>
  inline void oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr);

 public:

  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL_RANGE)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL_RANGE)

#if INCLUDE_ALL_GCS
  ALL_OOP_OOP_ITERATE_CLOSURES_1(OOP_OOP_ITERATE_DECL_NO_BACKWARDS)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(OOP_OOP_ITERATE_DECL_NO_BACKWARDS)
#endif // INCLUDE_ALL_GCS


 protected:
  // Find n'th dimensional array
  virtual Klass* array_klass_impl(bool or_null, int n, TRAPS);

  // Returns the array class with this class as element type
  virtual Klass* array_klass_impl(bool or_null, TRAPS);

 public:
  // Casting from Klass*
  static TypeArrayKlass* cast(Klass* k) {
    assert(k->is_typeArray_klass(), "cast to TypeArrayKlass");
    return static_cast<TypeArrayKlass*>(k);
  }

  // Naming
  static const char* external_name(BasicType type);

  // Sizing
  static int header_size()  { return sizeof(TypeArrayKlass)/HeapWordSize; }
  int size() const          { return ArrayKlass::static_size(header_size()); }

  // Initialization (virtual from Klass)
  void initialize(TRAPS);

 public:
  // Printing
#ifndef PRODUCT
  void oop_print_on(oop obj, outputStream* st);
#endif

  void print_on(outputStream* st) const;
  void print_value_on(outputStream* st) const;

 public:
  const char* internal_name() const;
};

#endif // SHARE_VM_OOPS_TYPEARRAYKLASS_HPP
