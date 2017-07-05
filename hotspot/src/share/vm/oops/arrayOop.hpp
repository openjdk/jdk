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

// arrayOopDesc is the abstract baseclass for all arrays.

class arrayOopDesc : public oopDesc {
  friend class VMStructs;
 private:
  int _length; // number of elements in the array

 public:
  // Interpreter/Compiler offsets
  static int length_offset_in_bytes()             { return offset_of(arrayOopDesc, _length); }
  static int base_offset_in_bytes(BasicType type) { return header_size(type) * HeapWordSize; }

  // Returns the address of the first element.
  void* base(BasicType type) const              { return (void*) (((intptr_t) this) + base_offset_in_bytes(type)); }

  // Tells whether index is within bounds.
  bool is_within_bounds(int index) const        { return 0 <= index && index < length(); }

  // Accessores for instance variable
  int length() const                            { return _length;   }
  void set_length(int length)                   { _length = length; }

  // Header size computation.
  // Should only be called with constants as argument (will not constant fold otherwise)
  static int header_size(BasicType type) {
    return Universe::element_type_should_be_aligned(type)
      ? align_object_size(sizeof(arrayOopDesc)/HeapWordSize)
      : sizeof(arrayOopDesc)/HeapWordSize;
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
    int elembytes = (type == T_OBJECT) ? T_OBJECT_aelem_bytes : type2aelembytes(type);
    jlong len = ((jlong)max_words * HeapWordSize) / elembytes;
    return (len > max_jint) ? max_jint : (int32_t)len;
  }

};
