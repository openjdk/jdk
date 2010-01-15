/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// A constantPoolKlass is the klass of a constantPoolOop

class constantPoolKlass : public Klass {
  juint    _alloc_size;        // allocation profiling support
 public:
  // Dispatched klass operations
  bool oop_is_constantPool() const  { return true; }
  int oop_size(oop obj) const;
  int klass_oop_size() const        { return object_size(); }

  // Allocation
  DEFINE_ALLOCATE_PERMANENT(constantPoolKlass);
  constantPoolOop allocate(int length, bool is_conc_safe, TRAPS);
  static klassOop create_klass(TRAPS);

  // Casting from klassOop
  static constantPoolKlass* cast(klassOop k) {
    assert(k->klass_part()->oop_is_constantPool(), "cast to constantPoolKlass");
    return (constantPoolKlass*) k->klass_part();
  }

  // Sizing
  static int header_size()        { return oopDesc::header_size() + sizeof(constantPoolKlass)/HeapWordSize; }
  int object_size() const        { return align_object_size(header_size()); }

  // Garbage collection
  // Returns true is the object is safe for GC concurrent processing.
  virtual bool oop_is_conc_safe(oop obj) const;
  void oop_follow_contents(oop obj);
  int oop_adjust_pointers(oop obj);

  // Parallel Scavenge and Parallel Old
  PARALLEL_GC_DECLS

  // Iterators
  int oop_oop_iterate(oop obj, OopClosure* blk);
  int oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr);

  // Allocation profiling support
  // no idea why this is pure virtual and not in Klass ???
  juint alloc_size() const              { return _alloc_size; }
  void set_alloc_size(juint n)          { _alloc_size = n; }

 public:
  // Printing
  void oop_print_value_on(oop obj, outputStream* st);
#ifndef PRODUCT
  void oop_print_on(oop obj, outputStream* st);
#endif

 public:
  // Verification
  const char* internal_name() const;
  void oop_verify_on(oop obj, outputStream* st);
  // tells whether obj is partially constructed (gc during class loading)
  bool oop_partially_loaded(oop obj) const;
  void oop_set_partially_loaded(oop obj);
#ifndef PRODUCT
  // Compile the world support
  static void preload_and_initialize_all_classes(oop constant_pool, TRAPS);
#endif
};
