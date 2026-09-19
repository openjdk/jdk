/*
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_C1_SHENANDOAHBARRIERSETC1_HPP
#define SHARE_GC_SHENANDOAH_C1_SHENANDOAHBARRIERSETC1_HPP

#include "c1/c1_CodeStubs.hpp"
#include "gc/shared/c1/barrierSetC1.hpp"

class ShenandoahKeepaliveBarrierStub: public CodeStub {
  friend class ShenandoahBarrierSetC1;
private:
  LIR_Opr _obj;
  LIR_Opr _addr;
  bool _do_load;

public:
  ShenandoahKeepaliveBarrierStub(LIR_Opr obj, LIR_Opr addr) :
    _obj(obj), _addr(addr), _do_load(true)
  {
    assert(_obj->is_register(), "should be temporary register");
    assert(_addr->is_address(), "should be the address of the field");
    FrameMap* f = Compilation::current()->frame_map();
    f->update_reserved_argument_area_size(1 * BytesPerWord);
  }

  ShenandoahKeepaliveBarrierStub(LIR_Opr obj) :
    _obj(obj), _addr(LIR_OprFact::illegalOpr), _do_load(false)
  {
    assert(_obj->is_register(), "should be a register");
    FrameMap* f = Compilation::current()->frame_map();
    f->update_reserved_argument_area_size(1 * BytesPerWord);
  }

  LIR_Opr addr() const { return _addr; }
  LIR_Opr obj() const { return _obj; }
  bool do_load() const { return _do_load; }

  virtual void emit_code(LIR_Assembler* e);
  virtual void visit(LIR_OpVisitState* visitor) {
    visitor->do_slow_case();
    if (_do_load) {
      visitor->do_input(_addr);
      visitor->do_temp(_addr);
      visitor->do_temp(_obj);
    } else {
      visitor->do_input(_obj);
    }
  }
#ifndef PRODUCT
  virtual void print_name(outputStream* out) const { out->print("ShenandoahKeepaliveBarrierStub"); }
#endif // PRODUCT
};

class ShenandoahLoadReferenceBarrierStub: public CodeStub {
  friend class ShenandoahBarrierSetC1;
private:
  LIR_Opr _obj;
  LIR_Opr _addr;
  LIR_Opr _slow_result;
  DecoratorSet _decorators;
public:
  ShenandoahLoadReferenceBarrierStub(LIR_Opr obj, LIR_Opr addr, LIR_Opr slow_result, DecoratorSet decorators) :
    _obj(obj), _addr(addr), _slow_result(slow_result), _decorators(decorators)
  {
    assert(_obj->is_register(), "should be register");
    assert(_addr->is_register(), "should be register");
    FrameMap* f = Compilation::current()->frame_map();
    f->update_reserved_argument_area_size(2 * BytesPerWord);
  }

  LIR_Opr obj() const { return _obj; }
  LIR_Opr addr() const { return _addr; }
  LIR_Opr slow_result() const { return _slow_result; }
  DecoratorSet decorators() const { return _decorators; }

  virtual void emit_code(LIR_Assembler* e);
  virtual void visit(LIR_OpVisitState* visitor) {
    visitor->do_slow_case();
    visitor->do_input(_obj);
    visitor->do_temp(_obj);
    visitor->do_output(_obj);
    visitor->do_input(_addr);
    visitor->do_temp(_addr);
    visitor->do_temp(_slow_result);
  }
#ifndef PRODUCT
  virtual void print_name(outputStream* out) const { out->print("ShenandoahLoadReferenceBarrierStub"); }
#endif // PRODUCT
};

class ShenandoahBarrierSetC1 : public BarrierSetC1 {
  friend class AOTCodeAddressTable;
private:
  CodeBlob* _keepalive_barrier_c1_runtime_code_blob;
  CodeBlob* _load_reference_barrier_strong_rt_code_blob;
  CodeBlob* _load_reference_barrier_strong_native_rt_code_blob;
  CodeBlob* _load_reference_barrier_weak_rt_code_blob;
  CodeBlob* _load_reference_barrier_phantom_rt_code_blob;

  void enter_if_gc_state(LIRGenerator* gen, int flags, CodeStub* slow_stub);

  void keepalive_barrier(LIRGenerator* gen, LIR_Opr obj, LIR_Opr addr, DecoratorSet decorators);
  void load_reference_barrier(LIRGenerator* gen, LIR_Opr obj, LIR_Opr addr, DecoratorSet decorators);
  void card_barrier(LIRGenerator* gen, LIR_Opr addr, DecoratorSet decorators);

  LIR_Opr ensure_in_register(LIRGenerator* gen, LIR_Opr obj, BasicType type);

public:
  ShenandoahBarrierSetC1();

  address keepalive_barrier_stub();
  address load_reference_barrier_stub(DecoratorSet decorators);

  virtual bool generate_c1_runtime_stubs(BufferBlob* buffer_blob);

  // Next methods are used by AOT code caching
  CodeBlob* keepalive_barrier_c1_runtime_code_blob() {
    assert(_keepalive_barrier_c1_runtime_code_blob != nullptr, "");
    return _keepalive_barrier_c1_runtime_code_blob;
  }

  CodeBlob* load_reference_barrier_strong_rt_code_blob() {
    assert(_load_reference_barrier_strong_rt_code_blob != nullptr, "");
    return _load_reference_barrier_strong_rt_code_blob;
  }

  CodeBlob* load_reference_barrier_strong_native_rt_code_blob() {
    assert(_load_reference_barrier_strong_native_rt_code_blob != nullptr, "");
    return _load_reference_barrier_strong_native_rt_code_blob;
  }

  CodeBlob* load_reference_barrier_weak_rt_code_blob() {
    assert(_load_reference_barrier_weak_rt_code_blob != nullptr, "");
    return _load_reference_barrier_weak_rt_code_blob;
  }

  CodeBlob* load_reference_barrier_phantom_rt_code_blob() {
    assert(_load_reference_barrier_phantom_rt_code_blob != nullptr, "");
    return _load_reference_barrier_phantom_rt_code_blob;
  }

protected:
  virtual void store_at_resolved(LIRAccess& access, LIR_Opr value);
  virtual LIR_Opr resolve_address(LIRAccess& access, bool resolve_in_register);
  virtual void load_at_resolved(LIRAccess& access, LIR_Opr result);

  virtual LIR_Opr atomic_cmpxchg_at_resolved(LIRAccess& access, LIRItem& cmp_value, LIRItem& new_value);
  virtual LIR_Opr atomic_xchg_at_resolved(LIRAccess& access, LIRItem& value);
};

#endif // SHARE_GC_SHENANDOAH_C1_SHENANDOAHBARRIERSETC1_HPP
