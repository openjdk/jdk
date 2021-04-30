/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "c1/c1_FrameMap.hpp"
#include "c1/c1_LIR.hpp"
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_LIRGenerator.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_CodeStubs.hpp"
#include "gc/z/c1/zBarrierSetC1.hpp"
#include "gc/z/zBarrierSet.hpp"
#include "gc/z/zBarrierSetAssembler.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "utilities/macros.hpp"

ZLoadBarrierStubC1::ZLoadBarrierStubC1(LIRAccess& access, LIR_Opr ref, address runtime_stub) :
    _decorators(access.decorators()),
    _ref_addr(access.resolved_addr()),
    _ref(ref),
    _tmp(LIR_OprFact::illegalOpr),
    _runtime_stub(runtime_stub) {

  assert(_ref_addr->is_address(), "Must be an address");
  assert(_ref->is_register(), "Must be a register");

  // Allocate tmp register if needed
  if (_ref_addr->as_address_ptr()->index()->is_valid() ||
      _ref_addr->as_address_ptr()->disp() != 0) {
    // Has index or displacement, need tmp register to load address into
    _tmp = access.gen()->new_pointer_register();
  }
}

DecoratorSet ZLoadBarrierStubC1::decorators() const {
  return _decorators;
}

LIR_Opr ZLoadBarrierStubC1::ref() const {
  return _ref;
}

LIR_Opr ZLoadBarrierStubC1::ref_addr() const {
  return _ref_addr;
}

LIR_Opr ZLoadBarrierStubC1::tmp() const {
  return _tmp;
}

address ZLoadBarrierStubC1::runtime_stub() const {
  return _runtime_stub;
}

void ZLoadBarrierStubC1::visit(LIR_OpVisitState* visitor) {
  visitor->do_slow_case();
  visitor->do_input(_ref_addr);
  visitor->do_output(_ref);
  if (_tmp->is_valid()) {
    visitor->do_temp(_tmp);
  }
}

void ZLoadBarrierStubC1::emit_code(LIR_Assembler* ce) {
  ZBarrierSet::assembler()->generate_c1_load_barrier_stub(ce, this);
}

#ifndef PRODUCT
void ZLoadBarrierStubC1::print_name(outputStream* out) const {
  out->print("ZLoadBarrierStubC1");
}
#endif // PRODUCT

ZStoreBarrierStubC1::ZStoreBarrierStubC1(LIRAccess& access, LIR_Opr new_zaddress, LIR_Opr new_zpointer, bool is_atomic) :
    _ref_addr(access.resolved_addr()),
    _new_zaddress(new_zaddress),
    _new_zpointer(new_zpointer),
    _is_atomic(is_atomic) {
  assert(_ref_addr->is_address(), "Must be an address");
}

LIR_Opr ZStoreBarrierStubC1::ref_addr() const {
  return _ref_addr;
}

LIR_Opr ZStoreBarrierStubC1::new_zaddress() const {
  return _new_zaddress;
}

LIR_Opr ZStoreBarrierStubC1::new_zpointer() const {
  return _new_zpointer;
}

bool ZStoreBarrierStubC1::is_atomic() const {
  return _is_atomic;
}

void ZStoreBarrierStubC1::visit(LIR_OpVisitState* visitor) {
  visitor->do_slow_case();
  visitor->do_input(_ref_addr);
}

void ZStoreBarrierStubC1::emit_code(LIR_Assembler* ce) {
  ZBarrierSet::assembler()->generate_c1_store_barrier_stub(ce, this);
}

#ifndef PRODUCT
void ZStoreBarrierStubC1::print_name(outputStream* out) const {
  out->print("ZStoreBarrierStubC1");
}
#endif // PRODUCT

class LIR_OpZUncolor : public LIR_Op {
private:
  LIR_Opr    _opr;

public:
  LIR_OpZUncolor(LIR_Opr opr) :
      LIR_Op(),
      _opr(opr) {}

  virtual void visit(LIR_OpVisitState* state) {
    state->do_input(_opr);
    state->do_output(_opr);
  }

  virtual void emit_code(LIR_Assembler* ce) {
    ZBarrierSet::assembler()->generate_uncolor(ce, _opr);
  }

  virtual void print_instr(outputStream* out) const {
    _opr->print(out);
    out->print(" ");
  }

#ifndef PRODUCT
  virtual const char* name() const {
    return "lir_z_uncolor";
  }
#endif // PRODUCT
};

class LIR_OpZLoadBarrier : public LIR_Op {
private:
  LIR_Opr                    _opr;
  ZLoadBarrierStubC1* const  _stub;
  const bool                 _on_non_strong;

public:
  LIR_OpZLoadBarrier(LIR_Opr opr, ZLoadBarrierStubC1* stub, bool on_non_strong) :
      LIR_Op(),
      _opr(opr),
      _stub(stub),
      _on_non_strong(on_non_strong) {
    assert(stub != nullptr, "The stub is the load barrier slow path.");
  }

  virtual void visit(LIR_OpVisitState* state) {
    state->do_input(_opr);
    state->do_output(_opr);
    state->do_stub(_stub);
  }

  virtual void emit_code(LIR_Assembler* ce) {
    ZBarrierSet::assembler()->generate_c1_load_barrier(ce, _opr, _stub, _on_non_strong);
    ce->append_code_stub(_stub);
  }

  virtual void print_instr(outputStream* out) const {
    _opr->print(out);
    out->print(" ");
  }

#ifndef PRODUCT
  virtual const char* name() const {
    return "lir_z_load_barrier";
  }
#endif // PRODUCT
};

static bool barrier_needed(LIRAccess& access) {
  return ZBarrierSet::barrier_needed(access.decorators(), access.type());
}

ZBarrierSetC1::ZBarrierSetC1() :
    _load_barrier_on_oop_field_preloaded_runtime_stub(NULL),
    _load_barrier_on_weak_oop_field_preloaded_runtime_stub(NULL) {}

address ZBarrierSetC1::load_barrier_on_oop_field_preloaded_runtime_stub(DecoratorSet decorators) const {
  assert((decorators & ON_PHANTOM_OOP_REF) == 0, "Unsupported decorator");
  //assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Unsupported decorator");

  if ((decorators & ON_WEAK_OOP_REF) != 0) {
    return _load_barrier_on_weak_oop_field_preloaded_runtime_stub;
  } else {
    return _load_barrier_on_oop_field_preloaded_runtime_stub;
  }
}

class LIR_OpZStoreBarrier : public LIR_Op {
 friend class LIR_OpVisitState;

private:
  LIR_Opr   _addr;
  LIR_Opr   _new_zaddress;
  LIR_Opr   _new_zpointer;
  CodeStub* _stub;

public:
  LIR_OpZStoreBarrier(LIR_Opr addr,
                      LIR_Opr new_zaddress,
                      LIR_Opr new_zpointer,
                      CodeStub* stub) :
    LIR_Op(lir_none, new_zpointer, NULL /* info */),
    _addr(addr),
    _new_zaddress(new_zaddress),
    _new_zpointer(new_zpointer),
    _stub(stub) {}

  virtual void visit(LIR_OpVisitState* state) {
    state->do_input(_new_zaddress);
    state->do_input(_addr);

    // Use temp registers to ensure these they use different registers.
    state->do_temp(_addr);
    state->do_temp(_new_zaddress);

    state->do_output(_new_zpointer);
    state->do_stub(_stub);
  }

  virtual void emit_code(LIR_Assembler* ce) {
    const ZBarrierSetAssembler* bs_asm = (const ZBarrierSetAssembler*)BarrierSet::barrier_set()->barrier_set_assembler();
    bs_asm->generate_c1_store_barrier(ce,
                                      _addr->as_address_ptr(),
                                      _new_zaddress,
                                      _new_zpointer,
                                      (ZStoreBarrierStubC1*)_stub);
    if (_stub != NULL) {
      ce->append_code_stub(_stub);
    }
  }

  virtual void print_instr(outputStream* out) const {
    if (_stub != NULL) {
      _addr->print(out);         out->print(" ");
      _new_zaddress->print(out); out->print(" ");
      _new_zpointer->print(out); out->print(" ");
    } else {
      _new_zaddress->print(out); out->print(" ");
    }
  }

#ifndef PRODUCT
  virtual const char* name() const  {
    return "ZStoreBarrier";
  }
#endif // PRODUCT
};

#ifdef ASSERT
#define __ access.gen()->lir(__FILE__, __LINE__)->
#else
#define __ access.gen()->lir()->
#endif

LIR_Opr ZBarrierSetC1::color(LIRAccess& access, LIR_Opr new_zaddress) const {
  // Only used from CAS where we have control over the used register
  assert(new_zaddress->is_single_cpu(), "Should be using a register");

  LIR_Opr new_zpointer = new_zaddress;

  __ append(new LIR_OpZStoreBarrier(access.resolved_addr(),
                                    new_zaddress,
                                    new_zpointer,
                                    NULL /* stub */));

  return new_zpointer;
}

void ZBarrierSetC1::load_barrier(LIRAccess& access, LIR_Opr result) const {
  // Slow path
  const address runtime_stub = load_barrier_on_oop_field_preloaded_runtime_stub(access.decorators());
  auto stub = new ZLoadBarrierStubC1(access, result, runtime_stub);

  const bool on_non_strong =
      (access.decorators() & ON_WEAK_OOP_REF) != 0 ||
      (access.decorators() & ON_PHANTOM_OOP_REF) != 0;

  __ append(new LIR_OpZLoadBarrier(result, stub, on_non_strong));
}

LIR_Opr ZBarrierSetC1::store_barrier(LIRAccess& access, LIR_Opr new_zaddress, bool is_atomic) const {
  LIRGenerator* gen = access.gen();

  LIR_Opr new_zaddress_reg;
  if (new_zaddress->is_single_cpu()) {
    new_zaddress_reg = new_zaddress;
  } else if (new_zaddress->is_constant()) {
    new_zaddress_reg = gen->new_register(access.type());
    gen->lir()->move(new_zaddress, new_zaddress_reg);
  } else {
    ShouldNotReachHere();
  }

  LIR_Opr new_zpointer = gen->new_register(T_OBJECT);
  ZStoreBarrierStubC1* stub = new ZStoreBarrierStubC1(access, new_zaddress_reg, new_zpointer, is_atomic);

  __ append(new LIR_OpZStoreBarrier(access.resolved_addr(),
                                    new_zaddress_reg,
                                    new_zpointer,
                                    stub));

  return new_zpointer;
}

LIR_Opr ZBarrierSetC1::resolve_address(LIRAccess& access, bool resolve_in_register) {
  // We must resolve in register when patching. This is to avoid
  // having a patch area in the load barrier stub, since the call
  // into the runtime to patch will not have the proper oop map.
  const bool patch_before_barrier = barrier_needed(access) && (access.decorators() & C1_NEEDS_PATCHING) != 0;
  return BarrierSetC1::resolve_address(access, resolve_in_register || patch_before_barrier);
}

void ZBarrierSetC1::load_at_resolved(LIRAccess& access, LIR_Opr result) {
  BarrierSetC1::load_at_resolved(access, result);

  if (barrier_needed(access)) {
    load_barrier(access, result);
  }
}

void ZBarrierSetC1::store_at_resolved(LIRAccess& access, LIR_Opr value) {
  if (!barrier_needed(access)) {
    BarrierSetC1::store_at_resolved(access, value);
    return;
  }

  LIRGenerator* gen = access.gen();
  value = store_barrier(access, value, false /* is_atomic */);

  BarrierSetC1::store_at_resolved(access, value);
}

LIR_Opr ZBarrierSetC1::atomic_cmpxchg_at_resolved(LIRAccess& access, LIRItem& cmp_value, LIRItem& new_value) {
  if (!barrier_needed(access)) {
    return BarrierSetC1::atomic_cmpxchg_at_resolved(access, cmp_value, new_value);
  }

  new_value.load_item();
  LIR_Opr new_value_zpointer = store_barrier(access, new_value.result(), true /* is_atomic */);

#ifdef AMD64
  cmp_value.load_item_force(FrameMap::rax_oop_opr);
#else
  // TODO: Check that this actually works on AArch64
  cmp_value.load_item();
  cmp_value.set_destroys_register();
#endif
  color(access, cmp_value.result());

  __ cas_obj(access.resolved_addr()->as_address_ptr()->base(),
             cmp_value.result(),
             new_value_zpointer,
             LIR_OprFact::illegalOpr, LIR_OprFact::illegalOpr);
  LIR_Opr result = access.gen()->new_register(T_INT);
  __ cmove(lir_cond_equal, LIR_OprFact::intConst(1), LIR_OprFact::intConst(0),
           result, T_INT);

  return result;
}

LIR_Opr ZBarrierSetC1::atomic_xchg_at_resolved(LIRAccess& access, LIRItem& value) {
  if (!barrier_needed(access)) {
    return BarrierSetC1::atomic_xchg_at_resolved(access, value);
  }

  value.load_item();

  LIR_Opr value_zpointer = store_barrier(access, value.result(), true /* is_atomic */);

  // The parent class expects the in-parameter and out-parameter to be the same.
  // Move the colored pointer to the expected register.
  __ xchg(access.resolved_addr(), value_zpointer, value_zpointer, LIR_OprFact::illegalOpr);
  __ append(new LIR_OpZUncolor(value_zpointer));

  return value_zpointer;
}

#undef __

class ZLoadBarrierRuntimeStubCodeGenClosure : public StubAssemblerCodeGenClosure {
private:
  const DecoratorSet _decorators;

public:
  ZLoadBarrierRuntimeStubCodeGenClosure(DecoratorSet decorators) :
      _decorators(decorators) {}

  virtual OopMapSet* generate_code(StubAssembler* sasm) {
    ZBarrierSet::assembler()->generate_c1_load_barrier_runtime_stub(sasm, _decorators);
    return NULL;
  }
};

static address generate_c1_runtime_stub(BufferBlob* blob, DecoratorSet decorators, const char* name) {
  ZLoadBarrierRuntimeStubCodeGenClosure cl(decorators);
  CodeBlob* const code_blob = Runtime1::generate_blob(blob, -1 /* stub_id */, name, false /* expect_oop_map*/, &cl);
  return code_blob->code_begin();
}

void ZBarrierSetC1::generate_c1_runtime_stubs(BufferBlob* blob) {
  _load_barrier_on_oop_field_preloaded_runtime_stub =
    generate_c1_runtime_stub(blob, ON_STRONG_OOP_REF, "load_barrier_on_oop_field_preloaded_runtime_stub");
  _load_barrier_on_weak_oop_field_preloaded_runtime_stub =
    generate_c1_runtime_stub(blob, ON_WEAK_OOP_REF, "load_barrier_on_weak_oop_field_preloaded_runtime_stub");
}
