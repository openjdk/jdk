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

#ifndef SHARE_VM_CODE_VTABLESTUBS_HPP
#define SHARE_VM_CODE_VTABLESTUBS_HPP

#include "code/vmreg.hpp"
#include "memory/allocation.hpp"

// A VtableStub holds an individual code stub for a pair (vtable index, #args) for either itables or vtables
// There's a one-to-one relationship between a VtableStub and such a pair.

class VtableStub {
 private:
  friend class VtableStubs;

  static address _chunk;             // For allocation
  static address _chunk_end;         // For allocation
  static VMReg   _receiver_location; // Where to find receiver

  VtableStub*    _next;              // Pointer to next entry in hash table
  const short    _index;             // vtable index
  short          _ame_offset;        // Where an AbstractMethodError might occur
  short          _npe_offset;        // Where a NullPointerException might occur
  bool           _is_vtable_stub;    // True if vtable stub, false, is itable stub
  /* code follows here */            // The vtableStub code

  void* operator new(size_t size, int code_size);

  VtableStub(bool is_vtable_stub, int index)
        : _next(NULL), _is_vtable_stub(is_vtable_stub),
          _index(index), _ame_offset(-1), _npe_offset(-1) {}
  VtableStub* next() const                       { return _next; }
  int index() const                              { return _index; }
  static VMReg receiver_location()               { return _receiver_location; }
  void set_next(VtableStub* n)                   { _next = n; }
  address code_begin() const                     { return (address)(this + 1); }
  address code_end() const                       { return code_begin() + pd_code_size_limit(_is_vtable_stub); }
  address entry_point() const                    { return code_begin(); }
  static int entry_offset()                      { return sizeof(class VtableStub); }

  bool matches(bool is_vtable_stub, int index) const {
    return _index == index && _is_vtable_stub == is_vtable_stub;
  }
  bool contains(address pc) const                { return code_begin() <= pc && pc < code_end(); }

  void set_exception_points(address npe_addr, address ame_addr) {
    _npe_offset = npe_addr - code_begin();
    _ame_offset = ame_addr - code_begin();
    assert(is_abstract_method_error(ame_addr),   "offset must be correct");
    assert(is_null_pointer_exception(npe_addr),  "offset must be correct");
    assert(!is_abstract_method_error(npe_addr),  "offset must be correct");
    assert(!is_null_pointer_exception(ame_addr), "offset must be correct");
  }

  // platform-dependent routines
  static int  pd_code_size_limit(bool is_vtable_stub);
  static int  pd_code_alignment();
  // CNC: Removed because vtable stubs are now made with an ideal graph
  // static bool pd_disregard_arg_size();

  static void align_chunk() {
    uintptr_t off = (uintptr_t)( _chunk + sizeof(VtableStub) ) % pd_code_alignment();
    if (off != 0)  _chunk += pd_code_alignment() - off;
  }

 public:
  // Query
  bool is_itable_stub()                          { return !_is_vtable_stub; }
  bool is_vtable_stub()                          { return  _is_vtable_stub; }
  bool is_abstract_method_error(address epc)     { return epc == code_begin()+_ame_offset; }
  bool is_null_pointer_exception(address epc)    { return epc == code_begin()+_npe_offset; }

  void print_on(outputStream* st) const;
  void print() const                             { print_on(tty); }

};


// VtableStubs creates the code stubs for compiled calls through vtables.
// There is one stub per (vtable index, args_size) pair, and the stubs are
// never deallocated. They don't need to be GCed because they contain no oops.

class VtableStubs : AllStatic {
 public:                                         // N must be public (some compilers need this for _table)
  enum {
    N    = 256,                                  // size of stub table; must be power of two
    mask = N - 1
  };

 private:
  static VtableStub* _table[N];                  // table of existing stubs
  static int         _number_of_vtable_stubs;    // number of stubs created so far (for statistics)

  static VtableStub* create_vtable_stub(int vtable_index);
  static VtableStub* create_itable_stub(int vtable_index);
  static VtableStub* lookup            (bool is_vtable_stub, int vtable_index);
  static void        enter             (bool is_vtable_stub, int vtable_index, VtableStub* s);
  static inline uint hash              (bool is_vtable_stub, int vtable_index);

 public:
  static address     create_stub(bool is_vtable_stub, int vtable_index, methodOop method); // return the entry point of a stub for this call
  static bool        is_entry_point(address pc);                     // is pc a vtable stub entry point?
  static bool        contains(address pc);                           // is pc within any stub?
  static VtableStub* stub_containing(address pc);                    // stub containing pc or NULL
  static int         number_of_vtable_stubs() { return _number_of_vtable_stubs; }
  static void        initialize();
};

#endif // SHARE_VM_CODE_VTABLESTUBS_HPP
