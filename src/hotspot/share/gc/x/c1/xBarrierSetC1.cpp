/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "c1/c1_LIR.hpp"
#include "c1/c1_LIRGenerator.hpp"
#include "c1/c1_CodeStubs.hpp"
#include "gc/x/c1/xBarrierSetC1.hpp"
#include "gc/x/xBarrierSet.hpp"
#include "gc/x/xBarrierSetAssembler.hpp"
#include "gc/x/xThreadLocalData.hpp"
#include "utilities/macros.hpp"

XLoadBarrierStubC1::XLoadBarrierStubC1(LIRAccess& access, LIR_Opr ref, address runtime_stub) :
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

  FrameMap* f = Compilation::current()->frame_map();
  f->update_reserved_argument_area_size(2 * BytesPerWord);
}

DecoratorSet XLoadBarrierStubC1::decorators() const {
  return _decorators;
}

LIR_Opr XLoadBarrierStubC1::ref() const {
  return _ref;
}

LIR_Opr XLoadBarrierStubC1::ref_addr() const {
  return _ref_addr;
}

LIR_Opr XLoadBarrierStubC1::tmp() const {
  return _tmp;
}

address XLoadBarrierStubC1::runtime_stub() const {
  return _runtime_stub;
}

void XLoadBarrierStubC1::visit(LIR_OpVisitState* visitor) {
  visitor->do_slow_case();
  visitor->do_input(_ref_addr);
  visitor->do_output(_ref);
  if (_tmp->is_valid()) {
    visitor->do_temp(_tmp);
  }
}

void XLoadBarrierStubC1::emit_code(LIR_Assembler* ce) {
  XBarrierSet::assembler()->generate_c1_load_barrier_stub(ce, this);
}

#ifndef PRODUCT
void XLoadBarrierStubC1::print_name(outputStream* out) const {
  out->print("XLoadBarrierStubC1");
}
#endif // PRODUCT

class LIR_OpXLoadBarrierTest : public LIR_Op {
private:
  LIR_Opr _opr;

public:
  LIR_OpXLoadBarrierTest(LIR_Opr opr) :
      LIR_Op(lir_xloadbarrier_test, LIR_OprFact::illegalOpr, nullptr),
      _opr(opr) {}

  virtual void visit(LIR_OpVisitState* state) {
    state->do_input(_opr);
  }

  virtual void emit_code(LIR_Assembler* ce) {
    XBarrierSet::assembler()->generate_c1_load_barrier_test(ce, _opr);
  }

  virtual void print_instr(outputStream* out) const {
    _opr->print(out);
    out->print(" ");
  }

#ifndef PRODUCT
  virtual const char* name() const {
    return "lir_z_load_barrier_test";
  }
#endif // PRODUCT
};

static bool barrier_needed(LIRAccess& access) {
  return XBarrierSet::barrier_needed(access.decorators(), access.type());
}

XBarrierSetC1::XBarrierSetC1() :
    _load_barrier_on_oop_field_preloaded_runtime_stub(nullptr),
    _load_barrier_on_weak_oop_field_preloaded_runtime_stub(nullptr) {}

address XBarrierSetC1::load_barrier_on_oop_field_preloaded_runtime_stub(DecoratorSet decorators) const {
  assert((decorators & ON_PHANTOM_OOP_REF) == 0, "Unsupported decorator");
  //assert((decorators & ON_UNKNOWN_OOP_REF) == 0, "Unsupported decorator");

  if ((decorators & ON_WEAK_OOP_REF) != 0) {
    return _load_barrier_on_weak_oop_field_preloaded_runtime_stub;
  } else {
    return _load_barrier_on_oop_field_preloaded_runtime_stub;
  }
}

#ifdef ASSERT
#define __ access.gen()->lir(__FILE__, __LINE__)->
#else
#define __ access.gen()->lir()->
#endif

void XBarrierSetC1::load_barrier(LIRAccess& access, LIR_Opr result) const {
  // Fast path
  __ append(new LIR_OpXLoadBarrierTest(result));

  // Slow path
  const address runtime_stub = load_barrier_on_oop_field_preloaded_runtime_stub(access.decorators());
  CodeStub* const stub = new XLoadBarrierStubC1(access, result, runtime_stub);
  __ branch(lir_cond_notEqual, stub);
  __ branch_destination(stub->continuation());
}

LIR_Opr XBarrierSetC1::resolve_address(LIRAccess& access, bool resolve_in_register) {
  // We must resolve in register when patching. This is to avoid
  // having a patch area in the load barrier stub, since the call
  // into the runtime to patch will not have the proper oop map.
  const bool patch_before_barrier = barrier_needed(access) && (access.decorators() & C1_NEEDS_PATCHING) != 0;
  return BarrierSetC1::resolve_address(access, resolve_in_register || patch_before_barrier);
}

#undef __

void XBarrierSetC1::load_at_resolved(LIRAccess& access, LIR_Opr result) {
  BarrierSetC1::load_at_resolved(access, result);

  if (barrier_needed(access)) {
    load_barrier(access, result);
  }
}

static void pre_load_barrier(LIRAccess& access) {
  DecoratorSet decorators = access.decorators();

  // Downgrade access to MO_UNORDERED
  decorators = (decorators & ~MO_DECORATOR_MASK) | MO_UNORDERED;

  // Remove ACCESS_WRITE
  decorators = (decorators & ~ACCESS_WRITE);

  // Generate synthetic load at
  access.gen()->access_load_at(decorators,
                               access.type(),
                               access.base().item(),
                               access.offset().opr(),
                               access.gen()->new_register(access.type()),
                               nullptr /* patch_emit_info */,
                               nullptr /* load_emit_info */);
}

LIR_Opr XBarrierSetC1::atomic_xchg_at_resolved(LIRAccess& access, LIRItem& value) {
  if (barrier_needed(access)) {
    pre_load_barrier(access);
  }

  return BarrierSetC1::atomic_xchg_at_resolved(access, value);
}

LIR_Opr XBarrierSetC1::atomic_cmpxchg_at_resolved(LIRAccess& access, LIRItem& cmp_value, LIRItem& new_value) {
  if (barrier_needed(access)) {
    pre_load_barrier(access);
  }

  return BarrierSetC1::atomic_cmpxchg_at_resolved(access, cmp_value, new_value);
}

class XLoadBarrierRuntimeStubCodeGenClosure : public StubAssemblerCodeGenClosure {
private:
  const DecoratorSet _decorators;

public:
  XLoadBarrierRuntimeStubCodeGenClosure(DecoratorSet decorators) :
      _decorators(decorators) {}

  virtual OopMapSet* generate_code(StubAssembler* sasm) {
    XBarrierSet::assembler()->generate_c1_load_barrier_runtime_stub(sasm, _decorators);
    return nullptr;
  }
};

static address generate_c1_runtime_stub(BufferBlob* blob, DecoratorSet decorators, const char* name) {
  XLoadBarrierRuntimeStubCodeGenClosure cl(decorators);
  CodeBlob* const code_blob = Runtime1::generate_blob(blob, C1StubId::NO_STUBID /* stub_id */, name, false /* expect_oop_map*/, &cl);
  return code_blob->code_begin();
}

void XBarrierSetC1::generate_c1_runtime_stubs(BufferBlob* blob) {
  _load_barrier_on_oop_field_preloaded_runtime_stub =
    generate_c1_runtime_stub(blob, ON_STRONG_OOP_REF, "load_barrier_on_oop_field_preloaded_runtime_stub");
  _load_barrier_on_weak_oop_field_preloaded_runtime_stub =
    generate_c1_runtime_stub(blob, ON_WEAK_OOP_REF, "load_barrier_on_weak_oop_field_preloaded_runtime_stub");
}
