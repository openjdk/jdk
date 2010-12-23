/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_OBJARRAYKLASSKLASS_HPP
#define SHARE_VM_OOPS_OBJARRAYKLASSKLASS_HPP

#include "oops/arrayKlassKlass.hpp"
#include "oops/objArrayKlass.hpp"

// The objArrayKlassKlass is klass for all objArrayKlass'

class objArrayKlassKlass : public arrayKlassKlass {
 public:
  // Testing
  virtual bool oop_is_objArrayKlass() const { return true; }

  // Dispatched operation
  int oop_size(oop obj) const { return objArrayKlass::cast(klassOop(obj))->object_size(); }
  int klass_oop_size() const  { return object_size(); }

  // Allocation
  DEFINE_ALLOCATE_PERMANENT(objArrayKlassKlass);
  static klassOop create_klass(TRAPS);
  klassOop allocate_objArray_klass(int n, KlassHandle element_klass, TRAPS);
  klassOop allocate_system_objArray_klass(TRAPS); // Used for bootstrapping in Universe::genesis

  // Casting from klassOop
  static objArrayKlassKlass* cast(klassOop k) {
    assert(k->klass_part()->oop_is_klass(), "cast to objArrayKlassKlass");
    return (objArrayKlassKlass*) k->klass_part();
  }

  // Sizing
  static int header_size()  { return oopDesc::header_size() + sizeof(objArrayKlassKlass)/HeapWordSize; }
  int object_size() const   { return align_object_size(header_size()); }

  // Garbage collection
  void oop_follow_contents(oop obj);
  int oop_adjust_pointers(oop obj);

  // Parallel Scavenge and Parallel Old
  PARALLEL_GC_DECLS

  // Iterators
  int oop_oop_iterate(oop obj, OopClosure* blk);
  int oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr);

 private:
  // helpers
  static klassOop allocate_objArray_klass_impl(objArrayKlassKlassHandle this_oop, int n, KlassHandle element_klass, TRAPS);

 public:
  // Printing
  void oop_print_value_on(oop obj, outputStream* st);
#ifndef PRODUCT
  void oop_print_on(oop obj, outputStream* st);
#endif //PRODUCT

  // Verification
  const char* internal_name() const;
  void oop_verify_on(oop obj, outputStream* st);

};

#endif // SHARE_VM_OOPS_OBJARRAYKLASSKLASS_HPP
