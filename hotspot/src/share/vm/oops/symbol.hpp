/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_SYMBOL_HPP
#define SHARE_VM_OOPS_SYMBOL_HPP

#include "utilities/utf8.hpp"
#include "memory/allocation.hpp"
#include "runtime/atomic.hpp"

// A Symbol is a canonicalized string.
// All Symbols reside in global SymbolTable and are reference counted.

// Reference counting
//
// All Symbols are allocated and added to the SymbolTable.
// When a class is unloaded, the reference counts of the Symbol pointers in
// the ConstantPool and in InstanceKlass (see release_C_heap_structures) are
// decremented.  When the reference count for a Symbol goes to 0, the garbage
// collector can free the Symbol and remove it from the SymbolTable.
//
// 0) Symbols need to be reference counted when a pointer to the Symbol is
// saved in persistent storage.  This does not include the pointer
// in the SymbolTable bucket (the _literal field in HashtableEntry)
// that points to the Symbol.  All other stores of a Symbol*
// to a field of a persistent variable (e.g., the _name filed in
// FieldAccessInfo or _ptr in a CPSlot) is reference counted.
//
// 1) The lookup of a "name" in the SymbolTable either creates a Symbol F for
// "name" and returns a pointer to F or finds a pre-existing Symbol F for
// "name" and returns a pointer to it. In both cases the reference count for F
// is incremented under the assumption that a pointer to F will be created from
// the return value. Thus the increment of the reference count is on the lookup
// and not on the assignment to the new Symbol*.  That is
//    Symbol* G = lookup()
//                ^ increment on lookup()
// and not
//    Symbol* G = lookup()
//              ^ increment on assignmnet
// The reference count must be decremented manually when the copy of the
// pointer G is destroyed.
//
// 2) For a local Symbol* A that is a copy of an existing Symbol* B, the
// reference counting is elided when the scope of B is greater than the scope
// of A.  For example, in the code fragment
// below "klass" is passed as a parameter to the method.  Symbol* "kn"
// is a copy of the name in "klass".
//
//   Symbol*  kn = klass->name();
//   unsigned int d_hash = dictionary()->compute_hash(kn, class_loader);
//
// The scope of "klass" is greater than the scope of "kn" so the reference
// counting for "kn" is elided.
//
// Symbol* copied from ConstantPool entries are good candidates for reference
// counting elision.  The ConstantPool entries for a class C exist until C is
// unloaded.  If a Symbol* is copied out of the ConstantPool into Symbol* X,
// the Symbol* in the ConstantPool will in general out live X so the reference
// counting on X can be elided.
//
// For cases where the scope of A is not greater than the scope of B,
// the reference counting is explicitly done.  See ciSymbol,
// ResolutionErrorEntry and ClassVerifier for examples.
//
// 3) When a Symbol K is created for temporary use, generally for substrings of
// an existing symbol or to create a new symbol, assign it to a
// TempNewSymbol. The SymbolTable methods new_symbol(), lookup()
// and probe() all potentially return a pointer to a new Symbol.
// The allocation (or lookup) of K increments the reference count for K
// and the destructor decrements the reference count.
//
// Another example of TempNewSymbol usage is parsed_name used in
// ClassFileParser::parseClassFile() where parsed_name is used in the cleanup
// after a failed attempt to load a class.  Here parsed_name is a
// TempNewSymbol (passed in as a parameter) so the reference count on its symbol
// will be decremented when it goes out of scope.


// This cannot be inherited from ResourceObj because it cannot have a vtable.
// Since sometimes this is allocated from Metadata, pick a base allocation
// type without virtual functions.
class ClassLoaderData;

// We separate the fields in SymbolBase from Symbol::_body so that
// Symbol::size(int) can correctly calculate the space needed.
class SymbolBase : public MetaspaceObj {
 public:
  ATOMIC_SHORT_PAIR(
    volatile short _refcount,  // needs atomic operation
    unsigned short _length     // number of UTF8 characters in the symbol (does not need atomic op)
  );
  int            _identity_hash;
};

class Symbol : private SymbolBase {
  friend class VMStructs;
  friend class SymbolTable;
  friend class MoveSymbols;
 private:
  jbyte _body[1];

  enum {
    // max_symbol_length is constrained by type of _length
    max_symbol_length = (1 << 16) -1
  };

  static int size(int length) {
    size_t sz = heap_word_size(sizeof(SymbolBase) + (length > 0 ? length : 0));
    return align_object_size(sz);
  }

  void byte_at_put(int index, int value) {
    assert(index >=0 && index < _length, "symbol index overflow");
    _body[index] = value;
  }

  Symbol(const u1* name, int length, int refcount);
  void* operator new(size_t size, int len, TRAPS) throw();
  void* operator new(size_t size, int len, Arena* arena, TRAPS) throw();
  void* operator new(size_t size, int len, ClassLoaderData* loader_data, TRAPS) throw();

  void  operator delete(void* p);

 public:
  // Low-level access (used with care, since not GC-safe)
  const jbyte* base() const { return &_body[0]; }

  int size()                { return size(utf8_length()); }

  // Returns the largest size symbol we can safely hold.
  static int max_length()   { return max_symbol_length; }

  int identity_hash()       { return _identity_hash; }

  // For symbol table alternate hashing
  unsigned int new_hash(jint seed);

  // Reference counting.  See comments above this class for when to use.
  int refcount() const      { return _refcount; }
  void increment_refcount();
  void decrement_refcount();

  int byte_at(int index) const {
    assert(index >=0 && index < _length, "symbol index overflow");
    return base()[index];
  }

  const jbyte* bytes() const { return base(); }

  int utf8_length() const { return _length; }

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
  inline int fast_compare(Symbol* other) const;

  // Returns receiver converted to null-terminated UTF-8 string; string is
  // allocated in resource area, or in the char buffer provided by caller.
  char* as_C_string() const;
  char* as_C_string(char* buf, int size) const;
  // Use buf if needed buffer length is <= size.
  char* as_C_string_flexible_buffer(Thread* t, char* buf, int size) const;

  // Returns an escaped form of a Java string.
  char* as_quoted_ascii() const;

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

  // Printing
  void print_symbol_on(outputStream* st = NULL) const;
  void print_on(outputStream* st) const;         // First level print
  void print_value_on(outputStream* st) const;   // Second level print.

  // printing on default output stream
  void print()         { print_on(tty);       }
  void print_value()   { print_value_on(tty); }

#ifndef PRODUCT
  // Empty constructor to create a dummy symbol object on stack
  // only for getting its vtable pointer.
  Symbol() { }

  static int _total_count;
#endif
};

// Note: this comparison is used for vtable sorting only; it doesn't matter
// what order it defines, as long as it is a total, time-invariant order
// Since Symbol*s are in C_HEAP, their relative order in memory never changes,
// so use address comparison for speed
int Symbol::fast_compare(Symbol* other) const {
 return (((uintptr_t)this < (uintptr_t)other) ? -1
   : ((uintptr_t)this == (uintptr_t) other) ? 0 : 1);
}
#endif // SHARE_VM_OOPS_SYMBOL_HPP
