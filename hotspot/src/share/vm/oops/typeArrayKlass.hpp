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

// A typeArrayKlass is the klass of a typeArray
// It contains the type and size of the elements

class typeArrayKlass : public arrayKlass {
  friend class VMStructs;
 private:
  jint _max_length;            // maximum number of elements allowed in an array
 public:
  // instance variables
  jint max_length()                     { return _max_length; }
  void set_max_length(jint m)           { _max_length = m;    }

  // testers
  bool oop_is_typeArray_slow() const    { return true; }

  // klass allocation
  DEFINE_ALLOCATE_PERMANENT(typeArrayKlass);
  static klassOop create_klass(BasicType type, int scale, const char* name_str,
                               TRAPS);
  static inline klassOop create_klass(BasicType type, int scale, TRAPS) {
    return create_klass(type, scale, external_name(type), CHECK_NULL);
  }

  int oop_size(oop obj) const;
  int klass_oop_size() const  { return object_size(); }

  bool compute_is_subtype_of(klassOop k);

  // Allocation
  typeArrayOop allocate(int length, TRAPS);
  typeArrayOop allocate_permanent(int length, TRAPS);  // used for class file structures
  oop multi_allocate(int rank, jint* sizes, TRAPS);

  // Copying
  void  copy_array(arrayOop s, int src_pos, arrayOop d, int dst_pos, int length, TRAPS);

  // Iteration
  int oop_oop_iterate(oop obj, OopClosure* blk);
  int oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr);

  // Garbage collection
  void oop_follow_contents(oop obj);
  int  oop_adjust_pointers(oop obj);

  // Parallel Scavenge and Parallel Old
  PARALLEL_GC_DECLS

 protected:
  // Find n'th dimensional array
  virtual klassOop array_klass_impl(bool or_null, int n, TRAPS);

  // Returns the array class with this class as element type
  virtual klassOop array_klass_impl(bool or_null, TRAPS);

 public:
  // Casting from klassOop
  static typeArrayKlass* cast(klassOop k) {
    assert(k->klass_part()->oop_is_typeArray_slow(), "cast to typeArrayKlass");
    return (typeArrayKlass*) k->klass_part();
  }

  // Naming
  static const char* external_name(BasicType type);

  // Sizing
  static int header_size()  { return oopDesc::header_size() + sizeof(typeArrayKlass)/HeapWordSize; }
  int object_size() const   { return arrayKlass::object_size(header_size()); }

  // Initialization (virtual from Klass)
  void initialize(TRAPS);

 private:
   // Helpers
   static klassOop array_klass_impl(typeArrayKlassHandle h_this, bool or_null, int n, TRAPS);

#ifndef PRODUCT
 public:
  // Printing
  void oop_print_on(oop obj, outputStream* st);
#endif
 public:
  const char* internal_name() const;
};
