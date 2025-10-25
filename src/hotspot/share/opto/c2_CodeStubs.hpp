/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/assembler.hpp"
#include "asm/codeBuffer.hpp"
#include "memory/allocation.hpp"
#include "opto/c2_MacroAssembler.hpp"
#include "opto/compile.hpp"
#include "opto/output.hpp"
#include "utilities/growableArray.hpp"

#ifndef SHARE_OPTO_C2_CODESTUBS_HPP
#define SHARE_OPTO_C2_CODESTUBS_HPP

class C2CodeStub : public ArenaObj {
private:
  Label _entry;
  Label _continuation;

  void add_to_stub_list();
protected:
  C2CodeStub() :
    _entry(),
    _continuation() {}

  ~C2CodeStub() = default;

public:
  NONCOPYABLE(C2CodeStub);

  Label& entry()        { return _entry; }
  Label& continuation() { return _continuation; }

  virtual void emit(C2_MacroAssembler& masm) = 0;
  virtual int max_size() const = 0;

  template <typename StubType, typename... Args>
  static C2CodeStub* make(const Args&... args) {
    auto stub = new (Compile::current()->comp_arena()) StubType(args...);
    stub->add_to_stub_list();
    return stub;
  }
};

class C2CodeStubList {
private:
  GrowableArray<C2CodeStub*> _stubs;

public:
  C2CodeStubList();

  void add_stub(C2CodeStub* stub) { _stubs.append(stub); }
  void emit(C2_MacroAssembler& masm);
};

class C2SafepointPollStub : public C2CodeStub {
private:
  uintptr_t _safepoint_offset;

public:
  C2SafepointPollStub(uintptr_t safepoint_offset) :
    _safepoint_offset(safepoint_offset) {}
  int max_size() const;
  void emit(C2_MacroAssembler& masm);
};

// We move non-hot code of the nmethod entry barrier to an out-of-line stub
class C2EntryBarrierStub: public C2CodeStub {
private:
  Label _guard; // Used on AArch64 and RISCV

public:
  C2EntryBarrierStub() : C2CodeStub(),
    _guard() {}

  Label& guard() { return _guard; }

  int max_size() const;
  void emit(C2_MacroAssembler& masm);
};

class C2FastUnlockLightweightStub : public C2CodeStub {
private:
  Register _obj;
  Register _mark;
  Register _t;
  Register _thread;
  Label _slow_path;
  Label _push_and_slow_path;
  Label _unlocked_continuation;
public:
  C2FastUnlockLightweightStub(Register obj, Register mark, Register t, Register thread) : C2CodeStub(),
    _obj(obj), _mark(mark), _t(t), _thread(thread) {}
  int max_size() const;
  void emit(C2_MacroAssembler& masm);
  Label& slow_path() { return _slow_path; }
  Label& push_and_slow_path() { return _push_and_slow_path; }
  Label& unlocked_continuation() { return _unlocked_continuation; }
  Label& slow_path_continuation() { return continuation(); }
};

#endif // SHARE_OPTO_C2_CODESTUBS_HPP
