/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

// A symbolOop is a canonicalized string.
// All symbolOops reside in global symbolTable.
// See oopFactory::new_symbol for how to allocate a symbolOop

class symbolOopDesc : public oopDesc {
  friend class VMStructs;
 private:
  unsigned short _length; // number of UTF8 characters in the symbol
  jbyte _body[1];

  enum {
    // max_symbol_length is constrained by type of _length
    max_symbol_length = (1 << 16) -1
  };
 public:

  // Low-level access (used with care, since not GC-safe)
  jbyte* base() { return &_body[0]; }


  // Returns the largest size symbol we can safely hold.
  static int max_length() {
    return max_symbol_length;
  }

  static int object_size(int length) {
    int size = header_size() + (sizeof(unsigned short) + length + HeapWordSize - 1) / HeapWordSize;
    return align_object_size(size);
  }

  int object_size() { return object_size(utf8_length()); }

  int byte_at(int index) const {
    assert(index >=0 && index < _length, "symbol index overflow");
    return ((symbolOopDesc*)this)->base()[index];
  }

  void byte_at_put(int index, int value) {
    assert(index >=0 && index < _length, "symbol index overflow");
    ((symbolOopDesc*)this)->base()[index] = value;
  }

  jbyte* bytes() { return base(); }

  int utf8_length() const { return _length; }

  void set_utf8_length(int len) { _length = len; }

  // Compares the symbol with a string.
  bool equals(const char* str, int len) const;
  bool equals(const char* str) const { return equals(str, (int) strlen(str)); }

  // Tests if the symbol starts with the given prefix.
  bool starts_with(const char* prefix, int len) const;
  bool starts_with(const char* prefix) const {
    return starts_with(prefix, (int) strlen(prefix));
  }

  // Tests if the symbol starts with the given prefix.
  int index_of_at(int i, const char* str, int len) const;
  int index_of_at(int i, const char* str) const {
    return index_of_at(i, str, (int) strlen(str));
  }

  // Three-way compare for sorting; returns -1/0/1 if receiver is </==/> than arg
  // note that the ordering is not alfabetical
  inline int fast_compare(symbolOop other) const;

  // Returns receiver converted to null-terminated UTF-8 string; string is
  // allocated in resource area, or in the char buffer provided by caller.
  char* as_C_string() const;
  char* as_C_string(char* buf, int size) const;
  // Use buf if needed buffer length is <= size.
  char* as_C_string_flexible_buffer(Thread* t, char* buf, int size) const;


  // Returns a null terminated utf8 string in a resource array
  char* as_utf8() const { return as_C_string(); }
  char* as_utf8_flexible_buffer(Thread* t, char* buf, int size) const {
    return as_C_string_flexible_buffer(t, buf, size);
  }

  jchar* as_unicode(int& length) const;

  // Treating this symbol as a class name, returns the Java name for the class.
  // String is allocated in resource area if buffer is not provided.
  // See Klass::external_name()
  const char* as_klass_external_name() const;
  const char* as_klass_external_name(char* buf, int size) const;

  bool object_is_parsable() const {
    return (utf8_length() > 0 || (oop)this == Universe::emptySymbol());
  }

  // Printing
  void print_symbol_on(outputStream* st = NULL);
};


// Note: this comparison is used for vtable sorting only; it doesn't matter
// what order it defines, as long as it is a total, time-invariant order
// Since symbolOops are in permSpace, their relative order in memory never changes,
// so use address comparison for speed
int symbolOopDesc::fast_compare(symbolOop other) const {
 return (((uintptr_t)this < (uintptr_t)other) ? -1
   : ((uintptr_t)this == (uintptr_t) other) ? 0 : 1);
}
