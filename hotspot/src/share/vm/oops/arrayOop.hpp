/*
 * Copyright 1997-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

// arrayOopDesc is the abstract baseclass for all arrays.  It doesn't
// declare pure virtual to enforce this because that would allocate a vtbl
// in each instance, which we don't want.

// The layout of array Oops is:
//
//  markOop
//  klassOop  // 32 bits if compressed but declared 64 in LP64.
//  length    // shares klass memory or allocated after declared fields.


class arrayOopDesc : public oopDesc {
  friend class VMStructs;

  // Interpreter/Compiler offsets

  // Header size computation.
  // The header is considered the oop part of this type plus the length.
  // Returns the aligned header_size_in_bytes.  This is not equivalent to
  // sizeof(arrayOopDesc) which should not appear in the code.
  static int header_size_in_bytes() {
    size_t hs = align_size_up(length_offset_in_bytes() + sizeof(int),
                              HeapWordSize);
#ifdef ASSERT
    // make sure it isn't called before UseCompressedOops is initialized.
    static size_t arrayoopdesc_hs = 0;
    if (arrayoopdesc_hs == 0) arrayoopdesc_hs = hs;
    assert(arrayoopdesc_hs == hs, "header size can't change");
#endif // ASSERT
    return (int)hs;
  }

 public:
  // The _length field is not declared in C++.  It is allocated after the
  // declared nonstatic fields in arrayOopDesc if not compressed, otherwise
  // it occupies the second half of the _klass field in oopDesc.
  static int length_offset_in_bytes() {
    return UseCompressedOops ? klass_gap_offset_in_bytes() :
                               sizeof(arrayOopDesc);
  }

  // Returns the offset of the first element.
  static int base_offset_in_bytes(BasicType type) {
    return header_size(type) * HeapWordSize;
  }

  // Returns the address of the first element.
  void* base(BasicType type) const {
    return (void*) (((intptr_t) this) + base_offset_in_bytes(type));
  }

  // Tells whether index is within bounds.
  bool is_within_bounds(int index) const        { return 0 <= index && index < length(); }

  // Accessors for instance variable which is not a C++ declared nonstatic
  // field.
  int length() const {
    return *(int*)(((intptr_t)this) + length_offset_in_bytes());
  }
  void set_length(int length) {
    *(int*)(((intptr_t)this) + length_offset_in_bytes()) = length;
  }

  // Should only be called with constants as argument
  // (will not constant fold otherwise)
  // Returns the header size in words aligned to the requirements of the
  // array object type.
  static int header_size(BasicType type) {
    size_t typesize_in_bytes = header_size_in_bytes();
    return (int)(Universe::element_type_should_be_aligned(type)
      ? align_object_size(typesize_in_bytes/HeapWordSize)
      : typesize_in_bytes/HeapWordSize);
  }

  // This method returns the  maximum length that can passed into
  // typeArrayOop::object_size(scale, length, header_size) without causing an
  // overflow. We substract an extra 2*wordSize to guard against double word
  // alignments.  It gets the scale from the type2aelembytes array.
  static int32_t max_array_length(BasicType type) {
    assert(type >= 0 && type < T_CONFLICT, "wrong type");
    assert(type2aelembytes(type) != 0, "wrong type");
    // We use max_jint, since object_size is internally represented by an 'int'
    // This gives us an upper bound of max_jint words for the size of the oop.
    int32_t max_words = (max_jint - header_size(type) - 2);
    int elembytes = type2aelembytes(type);
    jlong len = ((jlong)max_words * HeapWordSize) / elembytes;
    return (len > max_jint) ? max_jint : (int32_t)len;
  }

};
