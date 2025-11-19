/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_REFARRAYKLASS_HPP
#define SHARE_OOPS_REFARRAYKLASS_HPP

#include "oops/arrayKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "utilities/macros.hpp"

class ClassLoaderData;

// RefArrayKlass is the klass for arrays of references

class RefArrayKlass : public ObjArrayKlass {
  friend class VMStructs;
  friend class JVMCIVMStructs;

 public:
  static const KlassKind Kind = RefArrayKlassKind;

 private:
  // Constructor
  RefArrayKlass(int n, Klass* element_klass, Symbol* name);
  static RefArrayKlass* allocate_klass(ClassLoaderData* loader_data, int n, Klass* k, Symbol* name,
                                       TRAPS);

 public:
  // For dummy objects
  RefArrayKlass() {}

  // Dispatched operation
  DEBUG_ONLY(bool is_refArray_klass_slow() const { return true; })
  size_t oop_size(oop obj) const;  // TODO FIXME make it virtual in objArrayKlass

  // Allocation
  static RefArrayKlass* allocate_refArray_klass(ClassLoaderData* loader_data,
                                                int n, Klass* element_klass,
                                                TRAPS);

  objArrayOop allocate_instance(int length, TRAPS);

  // Copying TODO FIXME make copying method in objArrayKlass virtual and default implementation invalid (ShouldNotReachHere())
  void copy_array(arrayOop s, int src_pos, arrayOop d, int dst_pos, int length, TRAPS);

  virtual void metaspace_pointers_do(MetaspaceClosure* iter);

 private:
  // Either oop or narrowOop depending on UseCompressedOops.
  // must be called from within ObjArrayKlass.cpp
  void do_copy(arrayOop s, size_t src_offset,
               arrayOop d, size_t dst_offset,
               int length, TRAPS);

 public:
  static RefArrayKlass *cast(Klass* k) {
    assert(k->is_refArray_klass(), "cast to RefArrayKlass");
    return const_cast<RefArrayKlass *>(cast(const_cast<const Klass *>(k)));
  }

  static const RefArrayKlass *cast(const Klass* k) {
    assert(k->is_refArray_klass(), "cast to RefArrayKlass");
    return static_cast<const RefArrayKlass *>(k);
  }

  // Sizing
  static int header_size() { return sizeof(RefArrayKlass) / wordSize; }
  int size() const { return ArrayKlass::static_size(header_size()); }

  // Initialization (virtual from Klass)
  void initialize(TRAPS);

  // Oop fields (and metadata) iterators
  //
  // The RefArrayKlass iterators also visits the Object's klass.

  // Iterate over oop elements and metadata.
  template <typename T, typename OopClosureType>
  inline void oop_oop_iterate(oop obj, OopClosureType* closure);

  // Iterate over oop elements and metadata.
  template <typename T, typename OopClosureType>
  inline void oop_oop_iterate_reverse(oop obj, OopClosureType* closure);

  // Iterate over oop elements within mr, and metadata.
  template <typename T, typename OopClosureType>
  inline void oop_oop_iterate_bounded(oop obj, OopClosureType* closure, MemRegion mr);

  // Iterate over oop elements within [start, end), and metadata.
  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_range(refArrayOop a, OopClosureType* closure, int start, int end);

 public:
  // Iterate over all oop elements.
  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_elements(refArrayOop a, OopClosureType* closure);

 private:
  // Iterate over all oop elements with indices within mr.
  template <typename T, class OopClosureType>
  inline void oop_oop_iterate_elements_bounded(refArrayOop a, OopClosureType* closure, void* low, void* high);

 public:
  // Printing
  void print_on(outputStream* st) const;
  void print_value_on(outputStream* st) const;

  void oop_print_value_on(oop obj, outputStream* st);
#ifndef PRODUCT
  void oop_print_on(oop obj, outputStream* st);
#endif // PRODUCT

  // Verification
  void verify_on(outputStream* st);

  void oop_verify_on(oop obj, outputStream* st);
};

#endif // SHARE_OOPS_REFARRAYKLASS_HPP
