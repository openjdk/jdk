/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_OBJARRAYKLASS_HPP
#define SHARE_VM_OOPS_OBJARRAYKLASS_HPP

#include "classfile/classLoaderData.hpp"
#include "memory/specialized_oop_closures.hpp"
#include "oops/arrayKlass.hpp"
#include "utilities/macros.hpp"

// ObjArrayKlass is the klass for objArrays

class ObjArrayKlass : public ArrayKlass {
  friend class VMStructs;
 private:
  Klass* _element_klass;            // The klass of the elements of this array type
  Klass* _bottom_klass;             // The one-dimensional type (InstanceKlass or TypeArrayKlass)

  // Constructor
  ObjArrayKlass(int n, KlassHandle element_klass, Symbol* name);
  static ObjArrayKlass* allocate(ClassLoaderData* loader_data, int n, KlassHandle klass_handle, Symbol* name, TRAPS);
 public:
  // For dummy objects
  ObjArrayKlass() {}

  // Instance variables
  Klass* element_klass() const      { return _element_klass; }
  void set_element_klass(Klass* k)  { _element_klass = k; }
  Klass** element_klass_addr()      { return &_element_klass; }

  Klass* bottom_klass() const       { return _bottom_klass; }
  void set_bottom_klass(Klass* k)   { _bottom_klass = k; }
  Klass** bottom_klass_addr()       { return &_bottom_klass; }

  // Compiler/Interpreter offset
  static ByteSize element_klass_offset() { return in_ByteSize(offset_of(ObjArrayKlass, _element_klass)); }

  // Dispatched operation
  bool can_be_primary_super_slow() const;
  GrowableArray<Klass*>* compute_secondary_supers(int num_extra_slots);
  bool compute_is_subtype_of(Klass* k);
  bool oop_is_objArray_slow()  const  { return true; }
  int oop_size(oop obj) const;

  // Allocation
  static Klass* allocate_objArray_klass(ClassLoaderData* loader_data,
                                          int n, KlassHandle element_klass, TRAPS);

  objArrayOop allocate(int length, TRAPS);
  oop multi_allocate(int rank, jint* sizes, TRAPS);

  // Copying
  void  copy_array(arrayOop s, int src_pos, arrayOop d, int dst_pos, int length, TRAPS);

  // Compute protection domain
  oop protection_domain() const { return bottom_klass()->protection_domain(); }

 private:
  // Either oop or narrowOop depending on UseCompressedOops.
  // must be called from within ObjArrayKlass.cpp
  template <class T> void do_copy(arrayOop s, T* src, arrayOop d,
                                  T* dst, int length, TRAPS);
 protected:
  // Returns the ObjArrayKlass for n'th dimension.
  virtual Klass* array_klass_impl(bool or_null, int n, TRAPS);

  // Returns the array class with this class as element type.
  virtual Klass* array_klass_impl(bool or_null, TRAPS);

 public:
  // Casting from Klass*
  static ObjArrayKlass* cast(Klass* k) {
    assert(k->oop_is_objArray(), "cast to ObjArrayKlass");
    return (ObjArrayKlass*) k;
  }

  // Sizing
  static int header_size()                { return sizeof(ObjArrayKlass)/HeapWordSize; }
  int size() const                        { return ArrayKlass::static_size(header_size()); }

  // Initialization (virtual from Klass)
  void initialize(TRAPS);

  // Garbage collection
  void oop_follow_contents(oop obj);
  inline void oop_follow_contents(oop obj, int index);
  template <class T> inline void objarray_follow_contents(oop obj, int index);

  int  oop_adjust_pointers(oop obj);

  // Parallel Scavenge and Parallel Old
  PARALLEL_GC_DECLS
#if INCLUDE_ALL_GCS
  inline void oop_follow_contents(ParCompactionManager* cm, oop obj, int index);
  template <class T> inline void
    objarray_follow_contents(ParCompactionManager* cm, oop obj, int index);
#endif // INCLUDE_ALL_GCS

  // Iterators
  int oop_oop_iterate(oop obj, ExtendedOopClosure* blk) {
    return oop_oop_iterate_v(obj, blk);
  }
  int oop_oop_iterate_m(oop obj, ExtendedOopClosure* blk, MemRegion mr) {
    return oop_oop_iterate_v_m(obj, blk, mr);
  }
#define ObjArrayKlass_OOP_OOP_ITERATE_DECL(OopClosureType, nv_suffix)   \
  int oop_oop_iterate##nv_suffix(oop obj, OopClosureType* blk);         \
  int oop_oop_iterate##nv_suffix##_m(oop obj, OopClosureType* blk,      \
                                     MemRegion mr);                     \
  int oop_oop_iterate_range##nv_suffix(oop obj, OopClosureType* blk,    \
                                     int start, int end);

  ALL_OOP_OOP_ITERATE_CLOSURES_1(ObjArrayKlass_OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(ObjArrayKlass_OOP_OOP_ITERATE_DECL)

  // JVM support
  jint compute_modifier_flags(TRAPS) const;

 public:
  // Printing
  void print_on(outputStream* st) const;
  void print_value_on(outputStream* st) const;

  void oop_print_value_on(oop obj, outputStream* st);
#ifndef PRODUCT
  void oop_print_on      (oop obj, outputStream* st);
#endif //PRODUCT

  const char* internal_name() const;

  // Verification
  void verify_on(outputStream* st, bool check_dictionary);

  void oop_verify_on(oop obj, outputStream* st);
};

#endif // SHARE_VM_OOPS_OBJARRAYKLASS_HPP
