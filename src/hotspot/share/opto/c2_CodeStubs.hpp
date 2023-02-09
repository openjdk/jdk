/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "utilities/growableArray.hpp"

#ifndef SHARE_OPTO_C2_CODESTUBS_HPP
#define SHARE_OPTO_C2_CODESTUBS_HPP

class C2CodeStub : public ArenaObj {
private:
  Label _entry;
  Label _continuation;

protected:
  C2CodeStub() :
    _entry(),
    _continuation() {}

public:
  Label& entry()        { return _entry; }
  Label& continuation() { return _continuation; }

  virtual void emit(C2_MacroAssembler& masm) = 0;
  virtual int max_size() const = 0;
};

class C2CodeStubList {
private:
  GrowableArray<C2CodeStub*> _stubs;

public:
  C2CodeStubList();

  void add_stub(C2CodeStub* stub) { _stubs.append(stub); }
  void emit(CodeBuffer& cb);
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

class C2CheckLockStackStub : public C2CodeStub {
public:
  C2CheckLockStackStub() : C2CodeStub() {}

  int max_size() const;
  void emit(C2_MacroAssembler& masm);
};

#ifdef _LP64
class C2FixAnonOMOwnerStub : public C2CodeStub {
private:
  Register _monitor;
  Register _tmp;
public:
  C2FixAnonOMOwnerStub(Register monitor, Register tmp = noreg) : C2CodeStub(),
    _monitor(monitor), _tmp(tmp) {}
  Register monitor() { return _monitor; }
  Register tmp() { return _tmp; }
  int max_size() const;
  void emit(C2_MacroAssembler& masm);
};
#endif

#endif // SHARE_OPTO_C2_CODESTUBS_HPP
