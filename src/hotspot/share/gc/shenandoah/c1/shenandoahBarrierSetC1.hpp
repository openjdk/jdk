/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

class ShenandoahPreBarrierStub: public CodeStub {
  friend class ShenandoahBarrierSetC1;
private:
  bool _do_load;
  LIR_Opr _addr;
  LIR_Opr _pre_val;
  LIR_PatchCode _patch_code;
  CodeEmitInfo* _info;

public:
  // Version that _does_ generate a load of the previous value from addr.
  // addr (the address of the field to be read) must be a LIR_Address
  // pre_val (a temporary register) must be a register;
  ShenandoahPreBarrierStub(LIR_Opr addr, LIR_Opr pre_val, LIR_PatchCode patch_code, CodeEmitInfo* info) :
    _do_load(true), _addr(addr), _pre_val(pre_val),
    _patch_code(patch_code), _info(info)
  {
    assert(_pre_val->is_register(), "should be temporary register");
    assert(_addr->is_address(), "should be the address of the field");
  }

  // Version that _does not_ generate load of the previous value; the
  // previous value is assumed to have already been loaded into pre_val.
  ShenandoahPreBarrierStub(LIR_Opr pre_val) :
    _do_load(false), _addr(LIR_OprFact::illegalOpr), _pre_val(pre_val),
    _patch_code(lir_patch_none), _info(NULL)
  {
    assert(_pre_val->is_register(), "should be a register");
  }

  LIR_Opr addr() const { return _addr; }
  LIR_Opr pre_val() const { return _pre_val; }
  LIR_PatchCode patch_code() const { return _patch_code; }
  CodeEmitInfo* info() const { return _info; }
  bool do_load() const { return _do_load; }

  virtual void emit_code(LIR_Assembler* e);
  virtual void visit(LIR_OpVisitState* visitor) {
    if (_do_load) {
      // don't pass in the code emit info since it's processed in the fast
      // path
      if (_info != NULL)
        visitor->do_slow_case(_info);
      else
        visitor->do_slow_case();

      visitor->do_input(_addr);
      visitor->do_temp(_pre_val);
    } else {
      visitor->do_slow_case();
      visitor->do_input(_pre_val);
    }
  }
#ifndef PRODUCT
  virtual void print_name(outputStream* out) const { out->print("ShenandoahPreBarrierStub"); }
#endif // PRODUCT
};

class ShenandoahLoadReferenceBarrierStub: public CodeStub {
  friend class ShenandoahBarrierSetC1;
private:
  LIR_Opr _obj;
  LIR_Opr _result;
  CodeEmitInfo* _info;
  bool _needs_null_check;

public:
  ShenandoahLoadReferenceBarrierStub(LIR_Opr obj, LIR_Opr result, CodeEmitInfo* info, bool needs_null_check) :
    _obj(obj), _result(result), _info(info), _needs_null_check(needs_null_check)
  {
    assert(_obj->is_register(), "should be register");
    assert(_result->is_register(), "should be register");
  }

  LIR_Opr obj() const { return _obj; }
  LIR_Opr result() const { return _result; }
  CodeEmitInfo* info() const { return _info; }
  bool needs_null_check() const { return _needs_null_check; }

  virtual void emit_code(LIR_Assembler* e);
  virtual void visit(LIR_OpVisitState* visitor) {
    visitor->do_slow_case();
    visitor->do_input(_obj);
    visitor->do_temp(_result);
  }
#ifndef PRODUCT
  virtual void print_name(outputStream* out) const { out->print("ShenandoahLoadReferenceBarrierStub"); }
#endif // PRODUCT
};

class LIR_OpShenandoahCompareAndSwap : public LIR_Op {
 friend class LIR_OpVisitState;

private:
  LIR_Opr _addr;
  LIR_Opr _cmp_value;
  LIR_Opr _new_value;
  LIR_Opr _tmp1;
  LIR_Opr _tmp2;

public:
  LIR_OpShenandoahCompareAndSwap(LIR_Opr addr, LIR_Opr cmp_value, LIR_Opr new_value,
                                 LIR_Opr t1, LIR_Opr t2, LIR_Opr result)
    : LIR_Op(lir_none, result, NULL)  // no info
    , _addr(addr)
    , _cmp_value(cmp_value)
    , _new_value(new_value)
    , _tmp1(t1)
    , _tmp2(t2)                                  { }

  LIR_Opr addr()        const                    { return _addr;  }
  LIR_Opr cmp_value()   const                    { return _cmp_value; }
  LIR_Opr new_value()   const                    { return _new_value; }
  LIR_Opr tmp1()        const                    { return _tmp1;      }
  LIR_Opr tmp2()        const                    { return _tmp2;      }

  virtual void visit(LIR_OpVisitState* state) {
      assert(_addr->is_valid(),      "used");
      assert(_cmp_value->is_valid(), "used");
      assert(_new_value->is_valid(), "used");
      if (_info)                    state->do_info(_info);
                                    state->do_input(_addr);
                                    state->do_temp(_addr);
                                    state->do_input(_cmp_value);
                                    state->do_temp(_cmp_value);
                                    state->do_input(_new_value);
                                    state->do_temp(_new_value);
      if (_tmp1->is_valid())        state->do_temp(_tmp1);
      if (_tmp2->is_valid())        state->do_temp(_tmp2);
      if (_result->is_valid())      state->do_output(_result);
  }

  virtual void emit_code(LIR_Assembler* masm);

  virtual void print_instr(outputStream* out) const {
    addr()->print(out);      out->print(" ");
    cmp_value()->print(out); out->print(" ");
    new_value()->print(out); out->print(" ");
    tmp1()->print(out);      out->print(" ");
    tmp2()->print(out);      out->print(" ");
  }
#ifndef PRODUCT
  virtual const char* name() const {
    return "shenandoah_cas_obj";
  }
#endif // PRODUCT
};

class ShenandoahBarrierSetC1 : public BarrierSetC1 {
private:
  CodeBlob* _pre_barrier_c1_runtime_code_blob;

  void pre_barrier(LIRGenerator* gen, CodeEmitInfo* info, DecoratorSet decorators, LIR_Opr addr_opr, LIR_Opr pre_val);

  LIR_Opr load_reference_barrier(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, bool need_null_check);
  LIR_Opr storeval_barrier(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, DecoratorSet decorators);

  LIR_Opr load_reference_barrier_impl(LIRGenerator* gen, LIR_Opr obj, CodeEmitInfo* info, bool need_null_check);

  LIR_Opr ensure_in_register(LIRGenerator* gen, LIR_Opr obj);

public:
  CodeBlob* pre_barrier_c1_runtime_code_blob() { return _pre_barrier_c1_runtime_code_blob; }

protected:

  virtual void store_at_resolved(LIRAccess& access, LIR_Opr value);
  virtual void load_at_resolved(LIRAccess& access, LIR_Opr result);

  virtual LIR_Opr atomic_cmpxchg_at_resolved(LIRAccess& access, LIRItem& cmp_value, LIRItem& new_value);

  virtual LIR_Opr atomic_xchg_at_resolved(LIRAccess& access, LIRItem& value);

public:

  virtual void generate_c1_runtime_stubs(BufferBlob* buffer_blob);
};

#endif // SHARE_GC_SHENANDOAH_C1_SHENANDOAHBARRIERSETC1_HPP
