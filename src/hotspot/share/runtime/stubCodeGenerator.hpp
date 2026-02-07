/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_RUNTIME_STUBCODEGENERATOR_HPP
#define SHARE_RUNTIME_STUBCODEGENERATOR_HPP

#include "asm/assembler.hpp"
#include "code/aotCodeCache.hpp"
#include "memory/allocation.hpp"
#include "runtime/stubInfo.hpp"

// All the basic framework for stub code generation/debugging/printing.


// A StubCodeDesc describes a piece of generated code (usually stubs).
// This information is mainly useful for debugging and printing.
// Currently, code descriptors are simply chained in a linked list,
// this may have to change if searching becomes too slow.

class StubCodeDesc: public CHeapObj<mtCode> {
 private:
  static StubCodeDesc* _list;     // the list of all descriptors
  static bool          _frozen;   // determines whether _list modifications are allowed

  StubCodeDesc*        _next;     // the next element in the linked list
  const char*          _group;    // the group to which the stub code belongs
  const char*          _name;     // the name assigned to the stub code
  address              _begin;    // points to the first byte of the stub code    (included)
  address              _end;      // points to the first byte after the stub code (excluded)
  uint                 _disp;     // Displacement relative base address in buffer.
  bool                 _loaded_from_cache;

  friend class StubCodeMark;
  friend class StubCodeGenerator;

  void set_begin(address begin) {
    assert(begin >= _begin, "begin may not decrease");
    assert(_end == nullptr || begin <= _end, "begin & end not properly ordered");
    _begin = begin;
  }

  void set_end(address end) {
    assert(_begin <= end, "begin & end not properly ordered");
    _end = end;
  }

  void set_disp(uint disp) { _disp = disp; }

  void set_loaded_from_cache() { _loaded_from_cache = true; }

 public:
  static StubCodeDesc* first() { return _list; }
  static StubCodeDesc* next(StubCodeDesc* desc)  { return desc->_next; }

  static StubCodeDesc* desc_for(address pc);     // returns the code descriptor for the code containing pc or null

  StubCodeDesc(const char* group, const char* name, address begin, address end = nullptr) {
    assert(!_frozen, "no modifications allowed");
    assert(name != nullptr, "no name specified");
    _next           = _list;
    _group          = group;
    _name           = name;
    _begin          = begin;
    _end            = end;
    _disp           = 0;
    _list           = this;
    _loaded_from_cache = false;
  };

  static void freeze();
  static void unfreeze();

  const char* group() const                      { return _group; }
  const char* name() const                       { return _name; }
  address     begin() const                      { return _begin; }
  address     end() const                        { return _end; }
  uint        disp() const                       { return _disp; }
  int         size_in_bytes() const              { return pointer_delta_as_int(_end, _begin); }
  bool        contains(address pc) const         { return _begin <= pc && pc < _end; }
  bool        loaded_from_cache() const          { return _loaded_from_cache; }
  void        print_on(outputStream* st) const;
  void        print() const;
};

// The base class for all stub-generating code generators.
// Provides utility functions.

class StubCodeGenerator: public StackObj {
 private:
  bool _print_code;
  BlobId _blob_id;
 protected:
  MacroAssembler*  _masm;
  AOTStubData* _stub_data;

  void setup_code_desc(const char* name, address start, address end, bool loaded_from_cache);
  // unsafe handler management
  void register_unsafe_access_handlers(GrowableArray<address> &entries, int begin, int count);
  void retrieve_unsafe_access_handlers(address start, address end, GrowableArray<address> &entries);

 public:
  StubCodeGenerator(CodeBuffer* code, bool print_code = false);
  StubCodeGenerator(CodeBuffer* code, BlobId blob_id, AOTStubData* stub_data = nullptr, bool print_code = false);
  ~StubCodeGenerator();

  MacroAssembler* assembler() const              { return _masm; }
  BlobId blob_id()                               { return _blob_id; }

  virtual void stub_prolog(StubCodeDesc* cdesc); // called by StubCodeMark constructor
  virtual void stub_epilog(StubCodeDesc* cdesc); // called by StubCodeMark destructor

  void print_stub_code_desc(StubCodeDesc* cdesc);

  static void print_statistics_on(outputStream* st);

  // load_archive_data should be called before generating the stub
  // identified by stub_id. If AOT caching of stubs is enabled and the
  // stubis found then the address of the stub's first and, possibly,
  // only entry is returned and the caller should use it instead of
  // generating thestub. Otherwise a null address is returned and the
  // caller should proceed to generate the stub.
  //
  // store_archive_data should be called when a stub has been
  // successfully generated into the current blob irrespctive of
  // whether the current JVM is generating or consuming an AOT archive
  // (the caller should not check for either case). When generating an
  // archive the stub entry and end addresses are recorded for storage
  // along with the current blob and also to allow rences to the stub
  // from other stubs or from compiled Java methods can be detected
  // and marked as requiring relocation. When consuming an archive the
  // stub entry address is still inorer to identify it as a relocation
  // target. When no archive is in use the call has no side effects.
  //
  // start and end identify the inclusive start and exclusive end
  // address for stub code and must lie in the current blob's code
  // range. Stubs presented via this interface must declare at least
  // one entry and start is always taken to be the first entry.
  //
  // Optional arrays entries and extras store other addresses of
  // interest all of which must either lie in the interval (start,
  // end) or be nullptr (verified by load and store methods).
  //
  // entries lists secondary entries for the stub each of which must
  // match a corresponding entry declaration for the stub (entry count
  // verified by load and store methods). Null entry addresses are
  // allowed when an architecture does not require a specific entry
  // but may not vary from one run to the next. If the cache is in use
  // at a store (for loading or saving code) then non-null entry
  // addresses are entered into the AOT cache stub address table
  // allowing references to them from other stubs or nmethods to be
  // relocated.
  //
  // extras lists other non-entry stub addresses of interest such as
  // memory protection ranges and associated handler addresses
  // (potentially including a null address). These do do not need to
  // be declared as entries and their number and meaning may vary
  // according to the architecture.

  address load_archive_data(StubId stub_id, GrowableArray<address> *entries = nullptr, GrowableArray<address>* extras = nullptr);
  void store_archive_data(StubId stub_id, address start, address end, GrowableArray<address> *entries = nullptr, GrowableArray<address>* extras = nullptr);
#ifdef ASSERT
  void verify_stub(StubId stub_id);
#endif

};

// Stack-allocated helper class used to associate a stub code with a name.
// All stub code generating functions that use a StubCodeMark will be registered
// in the global StubCodeDesc list and the generated stub code can be identified
// later via an address pointing into it.

class StubCodeMark: public StackObj {
 private:
  StubCodeGenerator* _cgen;
  StubCodeDesc*      _cdesc;

 public:
  StubCodeMark(StubCodeGenerator* cgen, const char* group, const char* name);
  StubCodeMark(StubCodeGenerator* cgen, StubId stub_id);
  ~StubCodeMark();

};

#endif // SHARE_RUNTIME_STUBCODEGENERATOR_HPP
