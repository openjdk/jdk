/*
 * Copyright (c) 1997, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_TYPEARRAYOOP_HPP
#define SHARE_VM_OOPS_TYPEARRAYOOP_HPP

#include "oops/arrayOop.hpp"
#include "oops/typeArrayKlass.hpp"
#include "runtime/orderAccess.inline.hpp"

// A typeArrayOop is an array containing basic types (non oop elements).
// It is used for arrays of {characters, singles, doubles, bytes, shorts, integers, longs}
#include <limits.h>

class typeArrayOopDesc : public arrayOopDesc {
 protected:
  jchar*    char_base()   const { return (jchar*)   base(T_CHAR); }
  jboolean* bool_base()   const { return (jboolean*)base(T_BOOLEAN); }
  jbyte*    byte_base()   const { return (jbyte*)   base(T_BYTE); }
  jint*     int_base()    const { return (jint*)    base(T_INT); }
  jlong*    long_base()   const { return (jlong*)   base(T_LONG); }
  jshort*   short_base()  const { return (jshort*)  base(T_SHORT); }
  jfloat*   float_base()  const { return (jfloat*)  base(T_FLOAT); }
  jdouble*  double_base() const { return (jdouble*) base(T_DOUBLE); }

  friend class TypeArrayKlass;

 public:
  jbyte* byte_at_addr(int which) const {
    assert(is_within_bounds(which), "index %d out of bounds %d", which, length());
    return &byte_base()[which];
  }

  jboolean* bool_at_addr(int which) const {
    assert(is_within_bounds(which), "index %d out of bounds %d", which, length());
    return &bool_base()[which];
  }

  jchar* char_at_addr(int which) const {
    assert(is_within_bounds(which), "index %d out of bounds %d", which, length());
    return &char_base()[which];
  }

  jint* int_at_addr(int which) const {
    assert(is_within_bounds(which), "index %d out of bounds %d", which, length());
    return &int_base()[which];
  }

  jshort* short_at_addr(int which) const {
    assert(is_within_bounds(which), "index %d out of bounds %d", which, length());
    return &short_base()[which];
  }

  jushort* ushort_at_addr(int which) const {  // for field descriptor arrays
    assert(is_within_bounds(which), "index %d out of bounds %d", which, length());
    return (jushort*) &short_base()[which];
  }

  jlong* long_at_addr(int which) const {
    assert(is_within_bounds(which), "index %d out of bounds %d", which, length());
    return &long_base()[which];
  }

  jfloat* float_at_addr(int which) const {
    assert(is_within_bounds(which), "index %d out of bounds %d", which, length());
    return &float_base()[which];
  }

  jdouble* double_at_addr(int which) const {
    assert(is_within_bounds(which), "index %d out of bounds %d", which, length());
    return &double_base()[which];
  }

  jbyte byte_at(int which) const                  { return *byte_at_addr(which); }
  void byte_at_put(int which, jbyte contents)     { *byte_at_addr(which) = contents; }

  jboolean bool_at(int which) const               { return *bool_at_addr(which); }
  void bool_at_put(int which, jboolean contents)  { *bool_at_addr(which) = contents; }

  jchar char_at(int which) const                  { return *char_at_addr(which); }
  void char_at_put(int which, jchar contents)     { *char_at_addr(which) = contents; }

  jint int_at(int which) const                    { return *int_at_addr(which); }
  void int_at_put(int which, jint contents)       { *int_at_addr(which) = contents; }

  jshort short_at(int which) const                { return *short_at_addr(which); }
  void short_at_put(int which, jshort contents)   { *short_at_addr(which) = contents; }

  jushort ushort_at(int which) const              { return *ushort_at_addr(which); }
  void ushort_at_put(int which, jushort contents) { *ushort_at_addr(which) = contents; }

  jlong long_at(int which) const                  { return *long_at_addr(which); }
  void long_at_put(int which, jlong contents)     { *long_at_addr(which) = contents; }

  jfloat float_at(int which) const                { return *float_at_addr(which); }
  void float_at_put(int which, jfloat contents)   { *float_at_addr(which) = contents; }

  jdouble double_at(int which) const              { return *double_at_addr(which); }
  void double_at_put(int which, jdouble contents) { *double_at_addr(which) = contents; }

  jbyte byte_at_acquire(int which) const              { return OrderAccess::load_acquire(byte_at_addr(which)); }
  void release_byte_at_put(int which, jbyte contents) { OrderAccess::release_store(byte_at_addr(which), contents); }

  // Java thinks metadata arrays are just arrays of either long or int, since
  // there doesn't seem to be T_ADDRESS, so this is a bit of unfortunate
  // casting
#ifdef _LP64
  Metadata* metadata_at(int which) const {
    return (Metadata*)*long_at_addr(which); }
  void metadata_at_put(int which, Metadata* contents) {
    *long_at_addr(which) = (jlong)contents;
  }
#else
  Metadata* metadata_at(int which) const {
    return (Metadata*)*int_at_addr(which); }
  void metadata_at_put(int which, Metadata* contents) {
    *int_at_addr(which) = (int)contents;
  }
#endif // _LP64

  // Sizing

  // Returns the number of words necessary to hold an array of "len"
  // elements each of the given "byte_size".
 private:
  static int object_size(int lh, int length) {
    int instance_header_size = Klass::layout_helper_header_size(lh);
    int element_shift = Klass::layout_helper_log2_element_size(lh);
    DEBUG_ONLY(BasicType etype = Klass::layout_helper_element_type(lh));
    assert(length <= arrayOopDesc::max_array_length(etype), "no overflow");

    julong size_in_bytes = (juint)length;
    size_in_bytes <<= element_shift;
    size_in_bytes += instance_header_size;
    julong size_in_words = ((size_in_bytes + (HeapWordSize-1)) >> LogHeapWordSize);
    assert(size_in_words <= (julong)max_jint, "no overflow");

    return align_object_size((intptr_t)size_in_words);
  }

 public:
  inline int object_size();
};

#endif // SHARE_VM_OOPS_TYPEARRAYOOP_HPP
