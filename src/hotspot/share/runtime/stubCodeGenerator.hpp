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
  void        print_on(outputStream* st) const;
  void        print() const;
};

// forward declare blob and stub id enums

// The base class for all stub-generating code generators.
// Provides utility functions.

class StubCodeGenerator: public StackObj {
 private:
  bool _print_code;
  BlobId _blob_id;
 protected:
  MacroAssembler*  _masm;

 public:
  StubCodeGenerator(CodeBuffer* code, bool print_code = false);
  StubCodeGenerator(CodeBuffer* code, BlobId blob_id, bool print_code = false);
  ~StubCodeGenerator();

  MacroAssembler* assembler() const              { return _masm; }
  BlobId blob_id()                               { return _blob_id; }

  virtual void stub_prolog(StubCodeDesc* cdesc); // called by StubCodeMark constructor
  virtual void stub_epilog(StubCodeDesc* cdesc); // called by StubCodeMark destructor

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
