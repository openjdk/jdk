/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "c1/c1_Compilation.hpp"
#include "c1/c1_FrameMap.hpp"
#include "c1/c1_Instruction.hpp"
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_LIRGenerator.hpp"
#include "c1/c1_Runtime1.hpp"
#include "c1/c1_ValueStack.hpp"
#include "ci/ciArray.hpp"
#include "ci/ciObjArrayKlass.hpp"
#include "ci/ciTypeArrayKlass.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubRoutines.hpp"
#include "vmreg_x86.inline.hpp"

#ifdef ASSERT
#define __ gen()->lir(__FILE__, __LINE__)->
#else
#define __ gen()->lir()->
#endif

// Item will be loaded into a byte register; Intel only
void LIRItem::load_byte_item() {
  load_item();
  LIR_Opr res = result();

  if (!res->is_virtual() || !_gen->is_vreg_flag_set(res, LIRGenerator::byte_reg)) {
    // make sure that it is a byte register
    assert(!value()->type()->is_float() && !value()->type()->is_double(),
           "can't load floats in byte register");
    LIR_Opr reg = _gen->rlock_byte(T_BYTE);
    __ move(res, reg);

    _result = reg;
  }
}


void LIRItem::load_nonconstant() {
  LIR_Opr r = value()->operand();
  if (r->is_constant()) {
    _result = r;
  } else {
    load_item();
  }
}

//--------------------------------------------------------------
//               LIRGenerator
//--------------------------------------------------------------


LIR_Opr LIRGenerator::exceptionOopOpr() { return FrameMap::rax_oop_opr; }
LIR_Opr LIRGenerator::exceptionPcOpr()  { return FrameMap::rdx_opr; }
LIR_Opr LIRGenerator::divInOpr()        { return FrameMap::rax_opr; }
LIR_Opr LIRGenerator::divOutOpr()       { return FrameMap::rax_opr; }
LIR_Opr LIRGenerator::remOutOpr()       { return FrameMap::rdx_opr; }
LIR_Opr LIRGenerator::shiftCountOpr()   { return FrameMap::rcx_opr; }
LIR_Opr LIRGenerator::syncLockOpr()     { return new_register(T_INT); }
LIR_Opr LIRGenerator::syncTempOpr()     { return FrameMap::rax_opr; }
LIR_Opr LIRGenerator::getThreadTemp()   { return LIR_OprFact::illegalOpr; }


LIR_Opr LIRGenerator::result_register_for(ValueType* type, bool callee) {
  LIR_Opr opr;
  switch (type->tag()) {
    case intTag:     opr = FrameMap::rax_opr;          break;
    case objectTag:  opr = FrameMap::rax_oop_opr;      break;
    case longTag:    opr = FrameMap::long0_opr;        break;
    case floatTag:   opr = UseSSE >= 1 ? FrameMap::xmm0_float_opr  : FrameMap::fpu0_float_opr;  break;
    case doubleTag:  opr = UseSSE >= 2 ? FrameMap::xmm0_double_opr : FrameMap::fpu0_double_opr;  break;

    case addressTag:
    default: ShouldNotReachHere(); return LIR_OprFact::illegalOpr;
  }

  assert(opr->type_field() == as_OprType(as_BasicType(type)), "type mismatch");
  return opr;
}


LIR_Opr LIRGenerator::rlock_byte(BasicType type) {
  LIR_Opr reg = new_register(T_INT);
  set_vreg_flag(reg, LIRGenerator::byte_reg);
  return reg;
}


//--------- loading items into registers --------------------------------


// i486 instructions can inline constants
bool LIRGenerator::can_store_as_constant(Value v, BasicType type) const {
  if (type == T_SHORT || type == T_CHAR) {
    // there is no immediate move of word values in asembler_i486.?pp
    return false;
  }
  Constant* c = v->as_Constant();
  if (c && c->state_before() == NULL) {
    // constants of any type can be stored directly, except for
    // unloaded object constants.
    return true;
  }
  return false;
}


bool LIRGenerator::can_inline_as_constant(Value v) const {
  if (v->type()->tag() == longTag) return false;
  return v->type()->tag() != objectTag ||
    (v->type()->is_constant() && v->type()->as_ObjectType()->constant_value()->is_null_object());
}


bool LIRGenerator::can_inline_as_constant(LIR_Const* c) const {
  if (c->type() == T_LONG) return false;
  return c->type() != T_OBJECT || c->as_jobject() == NULL;
}


LIR_Opr LIRGenerator::safepoint_poll_register() {
  return LIR_OprFact::illegalOpr;
}


LIR_Address* LIRGenerator::generate_address(LIR_Opr base, LIR_Opr index,
                                            int shift, int disp, BasicType type) {
  assert(base->is_register(), "must be");
  if (index->is_constant()) {
    return new LIR_Address(base,
                           (index->as_constant_ptr()->as_jint() << shift) + disp,
                           type);
  } else {
    return new LIR_Address(base, index, (LIR_Address::Scale)shift, disp, type);
  }
}


LIR_Address* LIRGenerator::emit_array_address(LIR_Opr array_opr, LIR_Opr index_opr,
                                              BasicType type, bool needs_card_mark) {
  int offset_in_bytes = arrayOopDesc::base_offset_in_bytes(type);

  LIR_Address* addr;
  if (index_opr->is_constant()) {
    int elem_size = type2aelembytes(type);
    addr = new LIR_Address(array_opr,
                           offset_in_bytes + index_opr->as_jint() * elem_size, type);
  } else {
#ifdef _LP64
    if (index_opr->type() == T_INT) {
      LIR_Opr tmp = new_register(T_LONG);
      __ convert(Bytecodes::_i2l, index_opr, tmp);
      index_opr = tmp;
    }
#endif // _LP64
    addr =  new LIR_Address(array_opr,
                            index_opr,
                            LIR_Address::scale(type),
                            offset_in_bytes, type);
  }
  if (needs_card_mark) {
    // This store will need a precise card mark, so go ahead and
    // compute the full adddres instead of computing once for the
    // store and again for the card mark.
    LIR_Opr tmp = new_pointer_register();
    __ leal(LIR_OprFact::address(addr), tmp);
    return new LIR_Address(tmp, type);
  } else {
    return addr;
  }
}


LIR_Opr LIRGenerator::load_immediate(int x, BasicType type) {
  LIR_Opr r = NULL;
  if (type == T_LONG) {
    r = LIR_OprFact::longConst(x);
  } else if (type == T_INT) {
    r = LIR_OprFact::intConst(x);
  } else {
    ShouldNotReachHere();
  }
  return r;
}

void LIRGenerator::increment_counter(address counter, BasicType type, int step) {
  LIR_Opr pointer = new_pointer_register();
  __ move(LIR_OprFact::intptrConst(counter), pointer);
  LIR_Address* addr = new LIR_Address(pointer, type);
  increment_counter(addr, step);
}


void LIRGenerator::increment_counter(LIR_Address* addr, int step) {
  __ add((LIR_Opr)addr, LIR_OprFact::intConst(step), (LIR_Opr)addr);
}

void LIRGenerator::cmp_mem_int(LIR_Condition condition, LIR_Opr base, int disp, int c, CodeEmitInfo* info) {
  __ cmp_mem_int(condition, base, disp, c, info);
}


void LIRGenerator::cmp_reg_mem(LIR_Condition condition, LIR_Opr reg, LIR_Opr base, int disp, BasicType type, CodeEmitInfo* info) {
  __ cmp_reg_mem(condition, reg, new LIR_Address(base, disp, type), info);
}


void LIRGenerator::cmp_reg_mem(LIR_Condition condition, LIR_Opr reg, LIR_Opr base, LIR_Opr disp, BasicType type, CodeEmitInfo* info) {
  __ cmp_reg_mem(condition, reg, new LIR_Address(base, disp, type), info);
}


bool LIRGenerator::strength_reduce_multiply(LIR_Opr left, int c, LIR_Opr result, LIR_Opr tmp) {
  if (tmp->is_valid()) {
    if (is_power_of_2(c + 1)) {
      __ move(left, tmp);
      __ shift_left(left, log2_intptr(c + 1), left);
      __ sub(left, tmp, result);
      return true;
    } else if (is_power_of_2(c - 1)) {
      __ move(left, tmp);
      __ shift_left(left, log2_intptr(c - 1), left);
      __ add(left, tmp, result);
      return true;
    }
  }
  return false;
}


void LIRGenerator::store_stack_parameter (LIR_Opr item, ByteSize offset_from_sp) {
  BasicType type = item->type();
  __ store(item, new LIR_Address(FrameMap::rsp_opr, in_bytes(offset_from_sp), type));
}

//----------------------------------------------------------------------
//             visitor functions
//----------------------------------------------------------------------


void LIRGenerator::do_StoreIndexed(StoreIndexed* x) {
  assert(x->is_pinned(),"");
  bool needs_range_check = x->compute_needs_range_check();
  bool use_length = x->length() != NULL;
  bool obj_store = x->elt_type() == T_ARRAY || x->elt_type() == T_OBJECT;
  bool needs_store_check = obj_store && (x->value()->as_Constant() == NULL ||
                                         !get_jobject_constant(x->value())->is_null_object() ||
                                         x->should_profile());

  LIRItem array(x->array(), this);
  LIRItem index(x->index(), this);
  LIRItem value(x->value(), this);
  LIRItem length(this);

  array.load_item();
  index.load_nonconstant();

  if (use_length && needs_range_check) {
    length.set_instruction(x->length());
    length.load_item();

  }
  if (needs_store_check) {
    value.load_item();
  } else {
    value.load_for_store(x->elt_type());
  }

  set_no_result(x);

  // the CodeEmitInfo must be duplicated for each different
  // LIR-instruction because spilling can occur anywhere between two
  // instructions and so the debug information must be different
  CodeEmitInfo* range_check_info = state_for(x);
  CodeEmitInfo* null_check_info = NULL;
  if (x->needs_null_check()) {
    null_check_info = new CodeEmitInfo(range_check_info);
  }

  // emit array address setup early so it schedules better
  LIR_Address* array_addr = emit_array_address(array.result(), index.result(), x->elt_type(), obj_store);

  if (GenerateRangeChecks && needs_range_check) {
    if (use_length) {
      __ cmp(lir_cond_belowEqual, length.result(), index.result());
      __ branch(lir_cond_belowEqual, T_INT, new RangeCheckStub(range_check_info, index.result()));
    } else {
      array_range_check(array.result(), index.result(), null_check_info, range_check_info);
      // range_check also does the null check
      null_check_info = NULL;
    }
  }

  if (GenerateArrayStoreCheck && needs_store_check) {
    LIR_Opr tmp1 = new_register(objectType);
    LIR_Opr tmp2 = new_register(objectType);
    LIR_Opr tmp3 = new_register(objectType);

    CodeEmitInfo* store_check_info = new CodeEmitInfo(range_check_info);
    __ store_check(value.result(), array.result(), tmp1, tmp2, tmp3, store_check_info, x->profiled_method(), x->profiled_bci());
  }

  if (obj_store) {
    // Needs GC write barriers.
    pre_barrier(LIR_OprFact::address(array_addr), LIR_OprFact::illegalOpr /* pre_val */,
                true /* do_load */, false /* patch */, NULL);
    __ move(value.result(), array_addr, null_check_info);
    // Seems to be a precise
    post_barrier(LIR_OprFact::address(array_addr), value.result());
  } else {
    __ move(value.result(), array_addr, null_check_info);
  }
}


void LIRGenerator::do_MonitorEnter(MonitorEnter* x) {
  assert(x->is_pinned(),"");
  LIRItem obj(x->obj(), this);
  obj.load_item();

  set_no_result(x);

  // "lock" stores the address of the monitor stack slot, so this is not an oop
  LIR_Opr lock = new_register(T_INT);
  // Need a scratch register for biased locking on x86
  LIR_Opr scratch = LIR_OprFact::illegalOpr;
  if (UseBiasedLocking) {
    scratch = new_register(T_INT);
  }

  CodeEmitInfo* info_for_exception = NULL;
  if (x->needs_null_check()) {
    info_for_exception = state_for(x);
  }
  // this CodeEmitInfo must not have the xhandlers because here the
  // object is already locked (xhandlers expect object to be unlocked)
  CodeEmitInfo* info = state_for(x, x->state(), true);
  monitor_enter(obj.result(), lock, syncTempOpr(), scratch,
                        x->monitor_no(), info_for_exception, info);
}


void LIRGenerator::do_MonitorExit(MonitorExit* x) {
  assert(x->is_pinned(),"");

  LIRItem obj(x->obj(), this);
  obj.dont_load_item();

  LIR_Opr lock = new_register(T_INT);
  LIR_Opr obj_temp = new_register(T_INT);
  set_no_result(x);
  monitor_exit(obj_temp, lock, syncTempOpr(), LIR_OprFact::illegalOpr, x->monitor_no());
}


// _ineg, _lneg, _fneg, _dneg
void LIRGenerator::do_NegateOp(NegateOp* x) {
  LIRItem value(x->x(), this);
  value.set_destroys_register();
  value.load_item();
  LIR_Opr reg = rlock(x);
  __ negate(value.result(), reg);

  set_result(x, round_item(reg));
}


// for  _fadd, _fmul, _fsub, _fdiv, _frem
//      _dadd, _dmul, _dsub, _ddiv, _drem
void LIRGenerator::do_ArithmeticOp_FPU(ArithmeticOp* x) {
  LIRItem left(x->x(),  this);
  LIRItem right(x->y(), this);
  LIRItem* left_arg  = &left;
  LIRItem* right_arg = &right;
  assert(!left.is_stack() || !right.is_stack(), "can't both be memory operands");
  bool must_load_both = (x->op() == Bytecodes::_frem || x->op() == Bytecodes::_drem);
  if (left.is_register() || x->x()->type()->is_constant() || must_load_both) {
    left.load_item();
  } else {
    left.dont_load_item();
  }

  // do not load right operand if it is a constant.  only 0 and 1 are
  // loaded because there are special instructions for loading them
  // without memory access (not needed for SSE2 instructions)
  bool must_load_right = false;
  if (right.is_constant()) {
    LIR_Const* c = right.result()->as_constant_ptr();
    assert(c != NULL, "invalid constant");
    assert(c->type() == T_FLOAT || c->type() == T_DOUBLE, "invalid type");

    if (c->type() == T_FLOAT) {
      must_load_right = UseSSE < 1 && (c->is_one_float() || c->is_zero_float());
    } else {
      must_load_right = UseSSE < 2 && (c->is_one_double() || c->is_zero_double());
    }
  }

  if (must_load_both) {
    // frem and drem destroy also right operand, so move it to a new register
    right.set_destroys_register();
    right.load_item();
  } else if (right.is_register() || must_load_right) {
    right.load_item();
  } else {
    right.dont_load_item();
  }
  LIR_Opr reg = rlock(x);
  LIR_Opr tmp = LIR_OprFact::illegalOpr;
  if (x->is_strictfp() && (x->op() == Bytecodes::_dmul || x->op() == Bytecodes::_ddiv)) {
    tmp = new_register(T_DOUBLE);
  }

  if ((UseSSE >= 1 && x->op() == Bytecodes::_frem) || (UseSSE >= 2 && x->op() == Bytecodes::_drem)) {
    // special handling for frem and drem: no SSE instruction, so must use FPU with temporary fpu stack slots
    LIR_Opr fpu0, fpu1;
    if (x->op() == Bytecodes::_frem) {
      fpu0 = LIR_OprFact::single_fpu(0);
      fpu1 = LIR_OprFact::single_fpu(1);
    } else {
      fpu0 = LIR_OprFact::double_fpu(0);
      fpu1 = LIR_OprFact::double_fpu(1);
    }
    __ move(right.result(), fpu1); // order of left and right operand is important!
    __ move(left.result(), fpu0);
    __ rem (fpu0, fpu1, fpu0);
    __ move(fpu0, reg);

  } else {
    arithmetic_op_fpu(x->op(), reg, left.result(), right.result(), x->is_strictfp(), tmp);
  }

  set_result(x, round_item(reg));
}


// for  _ladd, _lmul, _lsub, _ldiv, _lrem
void LIRGenerator::do_ArithmeticOp_Long(ArithmeticOp* x) {
  if (x->op() == Bytecodes::_ldiv || x->op() == Bytecodes::_lrem ) {
    // long division is implemented as a direct call into the runtime
    LIRItem left(x->x(), this);
    LIRItem right(x->y(), this);

    // the check for division by zero destroys the right operand
    right.set_destroys_register();

    BasicTypeList signature(2);
    signature.append(T_LONG);
    signature.append(T_LONG);
    CallingConvention* cc = frame_map()->c_calling_convention(&signature);

    // check for division by zero (destroys registers of right operand!)
    CodeEmitInfo* info = state_for(x);

    const LIR_Opr result_reg = result_register_for(x->type());
    left.load_item_force(cc->at(1));
    right.load_item();

    __ move(right.result(), cc->at(0));

    __ cmp(lir_cond_equal, right.result(), LIR_OprFact::longConst(0));
    __ branch(lir_cond_equal, T_LONG, new DivByZeroStub(info));

    address entry = NULL;
    switch (x->op()) {
    case Bytecodes::_lrem:
      entry = CAST_FROM_FN_PTR(address, SharedRuntime::lrem);
      break; // check if dividend is 0 is done elsewhere
    case Bytecodes::_ldiv:
      entry = CAST_FROM_FN_PTR(address, SharedRuntime::ldiv);
      break; // check if dividend is 0 is done elsewhere
    case Bytecodes::_lmul:
      entry = CAST_FROM_FN_PTR(address, SharedRuntime::lmul);
      break;
    default:
      ShouldNotReachHere();
    }

    LIR_Opr result = rlock_result(x);
    __ call_runtime_leaf(entry, getThreadTemp(), result_reg, cc->args());
    __ move(result_reg, result);
  } else if (x->op() == Bytecodes::_lmul) {
    // missing test if instr is commutative and if we should swap
    LIRItem left(x->x(), this);
    LIRItem right(x->y(), this);

    // right register is destroyed by the long mul, so it must be
    // copied to a new register.
    right.set_destroys_register();

    left.load_item();
    right.load_item();

    LIR_Opr reg = FrameMap::long0_opr;
    arithmetic_op_long(x->op(), reg, left.result(), right.result(), NULL);
    LIR_Opr result = rlock_result(x);
    __ move(reg, result);
  } else {
    // missing test if instr is commutative and if we should swap
    LIRItem left(x->x(), this);
    LIRItem right(x->y(), this);

    left.load_item();
    // don't load constants to save register
    right.load_nonconstant();
    rlock_result(x);
    arithmetic_op_long(x->op(), x->operand(), left.result(), right.result(), NULL);
  }
}



// for: _iadd, _imul, _isub, _idiv, _irem
void LIRGenerator::do_ArithmeticOp_Int(ArithmeticOp* x) {
  if (x->op() == Bytecodes::_idiv || x->op() == Bytecodes::_irem) {
    // The requirements for division and modulo
    // input : rax,: dividend                         min_int
    //         reg: divisor   (may not be rax,/rdx)   -1
    //
    // output: rax,: quotient  (= rax, idiv reg)       min_int
    //         rdx: remainder (= rax, irem reg)       0

    // rax, and rdx will be destroyed

    // Note: does this invalidate the spec ???
    LIRItem right(x->y(), this);
    LIRItem left(x->x() , this);   // visit left second, so that the is_register test is valid

    // call state_for before load_item_force because state_for may
    // force the evaluation of other instructions that are needed for
    // correct debug info.  Otherwise the live range of the fix
    // register might be too long.
    CodeEmitInfo* info = state_for(x);

    left.load_item_force(divInOpr());

    right.load_item();

    LIR_Opr result = rlock_result(x);
    LIR_Opr result_reg;
    if (x->op() == Bytecodes::_idiv) {
      result_reg = divOutOpr();
    } else {
      result_reg = remOutOpr();
    }

    if (!ImplicitDiv0Checks) {
      __ cmp(lir_cond_equal, right.result(), LIR_OprFact::intConst(0));
      __ branch(lir_cond_equal, T_INT, new DivByZeroStub(info));
    }
    LIR_Opr tmp = FrameMap::rdx_opr; // idiv and irem use rdx in their implementation
    if (x->op() == Bytecodes::_irem) {
      __ irem(left.result(), right.result(), result_reg, tmp, info);
    } else if (x->op() == Bytecodes::_idiv) {
      __ idiv(left.result(), right.result(), result_reg, tmp, info);
    } else {
      ShouldNotReachHere();
    }

    __ move(result_reg, result);
  } else {
    // missing test if instr is commutative and if we should swap
    LIRItem left(x->x(),  this);
    LIRItem right(x->y(), this);
    LIRItem* left_arg = &left;
    LIRItem* right_arg = &right;
    if (x->is_commutative() && left.is_stack() && right.is_register()) {
      // swap them if left is real stack (or cached) and right is real register(not cached)
      left_arg = &right;
      right_arg = &left;
    }

    left_arg->load_item();

    // do not need to load right, as we can handle stack and constants
    if (x->op() == Bytecodes::_imul ) {
      // check if we can use shift instead
      bool use_constant = false;
      bool use_tmp = false;
      if (right_arg->is_constant()) {
        int iconst = right_arg->get_jint_constant();
        if (iconst > 0) {
          if (is_power_of_2(iconst)) {
            use_constant = true;
          } else if (is_power_of_2(iconst - 1) || is_power_of_2(iconst + 1)) {
            use_constant = true;
            use_tmp = true;
          }
        }
      }
      if (use_constant) {
        right_arg->dont_load_item();
      } else {
        right_arg->load_item();
      }
      LIR_Opr tmp = LIR_OprFact::illegalOpr;
      if (use_tmp) {
        tmp = new_register(T_INT);
      }
      rlock_result(x);

      arithmetic_op_int(x->op(), x->operand(), left_arg->result(), right_arg->result(), tmp);
    } else {
      right_arg->dont_load_item();
      rlock_result(x);
      LIR_Opr tmp = LIR_OprFact::illegalOpr;
      arithmetic_op_int(x->op(), x->operand(), left_arg->result(), right_arg->result(), tmp);
    }
  }
}


void LIRGenerator::do_ArithmeticOp(ArithmeticOp* x) {
  // when an operand with use count 1 is the left operand, then it is
  // likely that no move for 2-operand-LIR-form is necessary
  if (x->is_commutative() && x->y()->as_Constant() == NULL && x->x()->use_count() > x->y()->use_count()) {
    x->swap_operands();
  }

  ValueTag tag = x->type()->tag();
  assert(x->x()->type()->tag() == tag && x->y()->type()->tag() == tag, "wrong parameters");
  switch (tag) {
    case floatTag:
    case doubleTag:  do_ArithmeticOp_FPU(x);  return;
    case longTag:    do_ArithmeticOp_Long(x); return;
    case intTag:     do_ArithmeticOp_Int(x);  return;
  }
  ShouldNotReachHere();
}


// _ishl, _lshl, _ishr, _lshr, _iushr, _lushr
void LIRGenerator::do_ShiftOp(ShiftOp* x) {
  // count must always be in rcx
  LIRItem value(x->x(), this);
  LIRItem count(x->y(), this);

  ValueTag elemType = x->type()->tag();
  bool must_load_count = !count.is_constant() || elemType == longTag;
  if (must_load_count) {
    // count for long must be in register
    count.load_item_force(shiftCountOpr());
  } else {
    count.dont_load_item();
  }
  value.load_item();
  LIR_Opr reg = rlock_result(x);

  shift_op(x->op(), reg, value.result(), count.result(), LIR_OprFact::illegalOpr);
}


// _iand, _land, _ior, _lor, _ixor, _lxor
void LIRGenerator::do_LogicOp(LogicOp* x) {
  // when an operand with use count 1 is the left operand, then it is
  // likely that no move for 2-operand-LIR-form is necessary
  if (x->is_commutative() && x->y()->as_Constant() == NULL && x->x()->use_count() > x->y()->use_count()) {
    x->swap_operands();
  }

  LIRItem left(x->x(), this);
  LIRItem right(x->y(), this);

  left.load_item();
  right.load_nonconstant();
  LIR_Opr reg = rlock_result(x);

  logic_op(x->op(), reg, left.result(), right.result());
}



// _lcmp, _fcmpl, _fcmpg, _dcmpl, _dcmpg
void LIRGenerator::do_CompareOp(CompareOp* x) {
  LIRItem left(x->x(), this);
  LIRItem right(x->y(), this);
  ValueTag tag = x->x()->type()->tag();
  if (tag == longTag) {
    left.set_destroys_register();
  }
  left.load_item();
  right.load_item();
  LIR_Opr reg = rlock_result(x);

  if (x->x()->type()->is_float_kind()) {
    Bytecodes::Code code = x->op();
    __ fcmp2int(left.result(), right.result(), reg, (code == Bytecodes::_fcmpl || code == Bytecodes::_dcmpl));
  } else if (x->x()->type()->tag() == longTag) {
    __ lcmp2int(left.result(), right.result(), reg);
  } else {
    Unimplemented();
  }
}


void LIRGenerator::do_CompareAndSwap(Intrinsic* x, ValueType* type) {
  assert(x->number_of_arguments() == 4, "wrong type");
  LIRItem obj   (x->argument_at(0), this);  // object
  LIRItem offset(x->argument_at(1), this);  // offset of field
  LIRItem cmp   (x->argument_at(2), this);  // value to compare with field
  LIRItem val   (x->argument_at(3), this);  // replace field with val if matches cmp

  assert(obj.type()->tag() == objectTag, "invalid type");

  // In 64bit the type can be long, sparc doesn't have this assert
  // assert(offset.type()->tag() == intTag, "invalid type");

  assert(cmp.type()->tag() == type->tag(), "invalid type");
  assert(val.type()->tag() == type->tag(), "invalid type");

  // get address of field
  obj.load_item();
  offset.load_nonconstant();

  LIR_Opr addr = new_pointer_register();
  LIR_Address* a;
  if(offset.result()->is_constant()) {
#ifdef _LP64
    jlong c = offset.result()->as_jlong();
    if ((jlong)((jint)c) == c) {
      a = new LIR_Address(obj.result(),
                          (jint)c,
                          as_BasicType(type));
    } else {
      LIR_Opr tmp = new_register(T_LONG);
      __ move(offset.result(), tmp);
      a = new LIR_Address(obj.result(),
                          tmp,
                          as_BasicType(type));
    }
#else
    a = new LIR_Address(obj.result(),
                        offset.result()->as_jint(),
                        as_BasicType(type));
#endif
  } else {
    a = new LIR_Address(obj.result(),
                        offset.result(),
                        LIR_Address::times_1,
                        0,
                        as_BasicType(type));
  }
  __ leal(LIR_OprFact::address(a), addr);

  if (type == objectType) {  // Write-barrier needed for Object fields.
    // Do the pre-write barrier, if any.
    pre_barrier(addr, LIR_OprFact::illegalOpr /* pre_val */,
                true /* do_load */, false /* patch */, NULL);
  }

  if (type == objectType) {
    cmp.load_item_force(FrameMap::rax_oop_opr);
    val.load_item();
  } else if (type == intType) {
    cmp.load_item_force(FrameMap::rax_opr);
    val.load_item();
  } else if (type == longType) {
    cmp.load_item_force(FrameMap::long0_opr);
    val.load_item_force(FrameMap::long1_opr);
  } else {
    ShouldNotReachHere();
  }

  LIR_Opr ill = LIR_OprFact::illegalOpr;  // for convenience
  if (type == objectType)
    __ cas_obj(addr, cmp.result(), val.result(), ill, ill);
  else if (type == intType)
    __ cas_int(addr, cmp.result(), val.result(), ill, ill);
  else if (type == longType)
    __ cas_long(addr, cmp.result(), val.result(), ill, ill);
  else {
    ShouldNotReachHere();
  }

  // generate conditional move of boolean result
  LIR_Opr result = rlock_result(x);
  __ cmove(lir_cond_equal, LIR_OprFact::intConst(1), LIR_OprFact::intConst(0),
           result, as_BasicType(type));
  if (type == objectType) {   // Write-barrier needed for Object fields.
    // Seems to be precise
    post_barrier(addr, val.result());
  }
}


void LIRGenerator::do_MathIntrinsic(Intrinsic* x) {
  assert(x->number_of_arguments() == 1 || (x->number_of_arguments() == 2 && x->id() == vmIntrinsics::_dpow), "wrong type");

  if (x->id() == vmIntrinsics::_dexp || x->id() == vmIntrinsics::_dlog) {
    do_LibmIntrinsic(x);
    return;
  }

  LIRItem value(x->argument_at(0), this);

  bool use_fpu = false;
  if (UseSSE >= 2) {
    switch(x->id()) {
      case vmIntrinsics::_dsin:
      case vmIntrinsics::_dcos:
      case vmIntrinsics::_dtan:
      case vmIntrinsics::_dlog10:
      case vmIntrinsics::_dpow:
        use_fpu = true;
    }
  } else {
    value.set_destroys_register();
  }

  value.load_item();

  LIR_Opr calc_input = value.result();
  LIR_Opr calc_input2 = NULL;
  if (x->id() == vmIntrinsics::_dpow) {
    LIRItem extra_arg(x->argument_at(1), this);
    if (UseSSE < 2) {
      extra_arg.set_destroys_register();
    }
    extra_arg.load_item();
    calc_input2 = extra_arg.result();
  }
  LIR_Opr calc_result = rlock_result(x);

  // sin, cos, pow and exp need two free fpu stack slots, so register
  // two temporary operands
  LIR_Opr tmp1 = FrameMap::caller_save_fpu_reg_at(0);
  LIR_Opr tmp2 = FrameMap::caller_save_fpu_reg_at(1);

  if (use_fpu) {
    LIR_Opr tmp = FrameMap::fpu0_double_opr;
    int tmp_start = 1;
    if (calc_input2 != NULL) {
      __ move(calc_input2, tmp);
      tmp_start = 2;
      calc_input2 = tmp;
    }
    __ move(calc_input, tmp);

    calc_input = tmp;
    calc_result = tmp;

    tmp1 = FrameMap::caller_save_fpu_reg_at(tmp_start);
    tmp2 = FrameMap::caller_save_fpu_reg_at(tmp_start + 1);
  }

  switch(x->id()) {
    case vmIntrinsics::_dabs:   __ abs  (calc_input, calc_result, LIR_OprFact::illegalOpr); break;
    case vmIntrinsics::_dsqrt:  __ sqrt (calc_input, calc_result, LIR_OprFact::illegalOpr); break;
    case vmIntrinsics::_dsin:   __ sin  (calc_input, calc_result, tmp1, tmp2);              break;
    case vmIntrinsics::_dcos:   __ cos  (calc_input, calc_result, tmp1, tmp2);              break;
    case vmIntrinsics::_dtan:   __ tan  (calc_input, calc_result, tmp1, tmp2);              break;
    case vmIntrinsics::_dlog10: __ log10(calc_input, calc_result, tmp1);                    break;
    case vmIntrinsics::_dpow:   __ pow  (calc_input, calc_input2, calc_result, tmp1, tmp2, FrameMap::rax_opr, FrameMap::rcx_opr, FrameMap::rdx_opr); break;
    default:                    ShouldNotReachHere();
  }

  if (use_fpu) {
    __ move(calc_result, x->operand());
  }
}

void LIRGenerator::do_LibmIntrinsic(Intrinsic* x) {
  LIRItem value(x->argument_at(0), this);
  value.set_destroys_register();

  LIR_Opr calc_result = rlock_result(x);
  LIR_Opr result_reg = result_register_for(x->type());

  BasicTypeList signature(1);
  signature.append(T_DOUBLE);
  CallingConvention* cc = frame_map()->c_calling_convention(&signature);

  value.load_item_force(cc->at(0));

#ifndef _LP64
  LIR_Opr tmp = FrameMap::fpu0_double_opr;
  result_reg = tmp;
  switch(x->id()) {
    case vmIntrinsics::_dexp:
      if (VM_Version::supports_sse2()) {
        __ call_runtime_leaf(StubRoutines::dexp(), getThreadTemp(), result_reg, cc->args());
      } else {
        __ call_runtime_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::dexp), getThreadTemp(), result_reg, cc->args());
      }
      break;
    case vmIntrinsics::_dlog:
      if (VM_Version::supports_sse2()) {
        __ call_runtime_leaf(StubRoutines::dlog(), getThreadTemp(), result_reg, cc->args());
      }
      else {
        __ call_runtime_leaf(CAST_FROM_FN_PTR(address, SharedRuntime::dlog), getThreadTemp(), result_reg, cc->args());
      }
      break;
    default:  ShouldNotReachHere();
  }
#else
  switch (x->id()) {
    case vmIntrinsics::_dexp:
      __ call_runtime_leaf(StubRoutines::dexp(), getThreadTemp(), result_reg, cc->args());
      break;
    case vmIntrinsics::_dlog:
      __ call_runtime_leaf(StubRoutines::dlog(), getThreadTemp(), result_reg, cc->args());
      break;
  }
#endif
  __ move(result_reg, calc_result);
}

void LIRGenerator::do_ArrayCopy(Intrinsic* x) {
  assert(x->number_of_arguments() == 5, "wrong type");

  // Make all state_for calls early since they can emit code
  CodeEmitInfo* info = state_for(x, x->state());

  LIRItem src(x->argument_at(0), this);
  LIRItem src_pos(x->argument_at(1), this);
  LIRItem dst(x->argument_at(2), this);
  LIRItem dst_pos(x->argument_at(3), this);
  LIRItem length(x->argument_at(4), this);

  // operands for arraycopy must use fixed registers, otherwise
  // LinearScan will fail allocation (because arraycopy always needs a
  // call)

#ifndef _LP64
  src.load_item_force     (FrameMap::rcx_oop_opr);
  src_pos.load_item_force (FrameMap::rdx_opr);
  dst.load_item_force     (FrameMap::rax_oop_opr);
  dst_pos.load_item_force (FrameMap::rbx_opr);
  length.load_item_force  (FrameMap::rdi_opr);
  LIR_Opr tmp =           (FrameMap::rsi_opr);
#else

  // The java calling convention will give us enough registers
  // so that on the stub side the args will be perfect already.
  // On the other slow/special case side we call C and the arg
  // positions are not similar enough to pick one as the best.
  // Also because the java calling convention is a "shifted" version
  // of the C convention we can process the java args trivially into C
  // args without worry of overwriting during the xfer

  src.load_item_force     (FrameMap::as_oop_opr(j_rarg0));
  src_pos.load_item_force (FrameMap::as_opr(j_rarg1));
  dst.load_item_force     (FrameMap::as_oop_opr(j_rarg2));
  dst_pos.load_item_force (FrameMap::as_opr(j_rarg3));
  length.load_item_force  (FrameMap::as_opr(j_rarg4));

  LIR_Opr tmp =           FrameMap::as_opr(j_rarg5);
#endif // LP64

  set_no_result(x);

  int flags;
  ciArrayKlass* expected_type;
  arraycopy_helper(x, &flags, &expected_type);

  __ arraycopy(src.result(), src_pos.result(), dst.result(), dst_pos.result(), length.result(), tmp, expected_type, flags, info); // does add_safepoint
}

void LIRGenerator::do_update_CRC32(Intrinsic* x) {
  assert(UseCRC32Intrinsics, "need AVX and LCMUL instructions support");
  // Make all state_for calls early since they can emit code
  LIR_Opr result = rlock_result(x);
  int flags = 0;
  switch (x->id()) {
    case vmIntrinsics::_updateCRC32: {
      LIRItem crc(x->argument_at(0), this);
      LIRItem val(x->argument_at(1), this);
      // val is destroyed by update_crc32
      val.set_destroys_register();
      crc.load_item();
      val.load_item();
      __ update_crc32(crc.result(), val.result(), result);
      break;
    }
    case vmIntrinsics::_updateBytesCRC32:
    case vmIntrinsics::_updateByteBufferCRC32: {
      bool is_updateBytes = (x->id() == vmIntrinsics::_updateBytesCRC32);

      LIRItem crc(x->argument_at(0), this);
      LIRItem buf(x->argument_at(1), this);
      LIRItem off(x->argument_at(2), this);
      LIRItem len(x->argument_at(3), this);
      buf.load_item();
      off.load_nonconstant();

      LIR_Opr index = off.result();
      int offset = is_updateBytes ? arrayOopDesc::base_offset_in_bytes(T_BYTE) : 0;
      if(off.result()->is_constant()) {
        index = LIR_OprFact::illegalOpr;
       offset += off.result()->as_jint();
      }
      LIR_Opr base_op = buf.result();

#ifndef _LP64
      if (!is_updateBytes) { // long b raw address
         base_op = new_register(T_INT);
         __ convert(Bytecodes::_l2i, buf.result(), base_op);
      }
#else
      if (index->is_valid()) {
        LIR_Opr tmp = new_register(T_LONG);
        __ convert(Bytecodes::_i2l, index, tmp);
        index = tmp;
      }
#endif

      LIR_Address* a = new LIR_Address(base_op,
                                       index,
                                       LIR_Address::times_1,
                                       offset,
                                       T_BYTE);
      BasicTypeList signature(3);
      signature.append(T_INT);
      signature.append(T_ADDRESS);
      signature.append(T_INT);
      CallingConvention* cc = frame_map()->c_calling_convention(&signature);
      const LIR_Opr result_reg = result_register_for(x->type());

      LIR_Opr addr = new_pointer_register();
      __ leal(LIR_OprFact::address(a), addr);

      crc.load_item_force(cc->at(0));
      __ move(addr, cc->at(1));
      len.load_item_force(cc->at(2));

      __ call_runtime_leaf(StubRoutines::updateBytesCRC32(), getThreadTemp(), result_reg, cc->args());
      __ move(result_reg, result);

      break;
    }
    default: {
      ShouldNotReachHere();
    }
  }
}

// _i2l, _i2f, _i2d, _l2i, _l2f, _l2d, _f2i, _f2l, _f2d, _d2i, _d2l, _d2f
// _i2b, _i2c, _i2s
LIR_Opr fixed_register_for(BasicType type) {
  switch (type) {
    case T_FLOAT:  return FrameMap::fpu0_float_opr;
    case T_DOUBLE: return FrameMap::fpu0_double_opr;
    case T_INT:    return FrameMap::rax_opr;
    case T_LONG:   return FrameMap::long0_opr;
    default:       ShouldNotReachHere(); return LIR_OprFact::illegalOpr;
  }
}

void LIRGenerator::do_Convert(Convert* x) {
  // flags that vary for the different operations and different SSE-settings
  bool fixed_input = false, fixed_result = false, round_result = false, needs_stub = false;

  switch (x->op()) {
    case Bytecodes::_i2l: // fall through
    case Bytecodes::_l2i: // fall through
    case Bytecodes::_i2b: // fall through
    case Bytecodes::_i2c: // fall through
    case Bytecodes::_i2s: fixed_input = false;       fixed_result = false;       round_result = false;      needs_stub = false; break;

    case Bytecodes::_f2d: fixed_input = UseSSE == 1; fixed_result = false;       round_result = false;      needs_stub = false; break;
    case Bytecodes::_d2f: fixed_input = false;       fixed_result = UseSSE == 1; round_result = UseSSE < 1; needs_stub = false; break;
    case Bytecodes::_i2f: fixed_input = false;       fixed_result = false;       round_result = UseSSE < 1; needs_stub = false; break;
    case Bytecodes::_i2d: fixed_input = false;       fixed_result = false;       round_result = false;      needs_stub = false; break;
    case Bytecodes::_f2i: fixed_input = false;       fixed_result = false;       round_result = false;      needs_stub = true;  break;
    case Bytecodes::_d2i: fixed_input = false;       fixed_result = false;       round_result = false;      needs_stub = true;  break;
    case Bytecodes::_l2f: fixed_input = false;       fixed_result = UseSSE >= 1; round_result = UseSSE < 1; needs_stub = false; break;
    case Bytecodes::_l2d: fixed_input = false;       fixed_result = UseSSE >= 2; round_result = UseSSE < 2; needs_stub = false; break;
    case Bytecodes::_f2l: fixed_input = true;        fixed_result = true;        round_result = false;      needs_stub = false; break;
    case Bytecodes::_d2l: fixed_input = true;        fixed_result = true;        round_result = false;      needs_stub = false; break;
    default: ShouldNotReachHere();
  }

  LIRItem value(x->value(), this);
  value.load_item();
  LIR_Opr input = value.result();
  LIR_Opr result = rlock(x);

  // arguments of lir_convert
  LIR_Opr conv_input = input;
  LIR_Opr conv_result = result;
  ConversionStub* stub = NULL;

  if (fixed_input) {
    conv_input = fixed_register_for(input->type());
    __ move(input, conv_input);
  }

  assert(fixed_result == false || round_result == false, "cannot set both");
  if (fixed_result) {
    conv_result = fixed_register_for(result->type());
  } else if (round_result) {
    result = new_register(result->type());
    set_vreg_flag(result, must_start_in_memory);
  }

  if (needs_stub) {
    stub = new ConversionStub(x->op(), conv_input, conv_result);
  }

  __ convert(x->op(), conv_input, conv_result, stub);

  if (result != conv_result) {
    __ move(conv_result, result);
  }

  assert(result->is_virtual(), "result must be virtual register");
  set_result(x, result);
}


void LIRGenerator::do_NewInstance(NewInstance* x) {
  print_if_not_loaded(x);

  CodeEmitInfo* info = state_for(x, x->state());
  LIR_Opr reg = result_register_for(x->type());
  new_instance(reg, x->klass(), x->is_unresolved(),
                       FrameMap::rcx_oop_opr,
                       FrameMap::rdi_oop_opr,
                       FrameMap::rsi_oop_opr,
                       LIR_OprFact::illegalOpr,
                       FrameMap::rdx_metadata_opr, info);
  LIR_Opr result = rlock_result(x);
  __ move(reg, result);
}


void LIRGenerator::do_NewTypeArray(NewTypeArray* x) {
  CodeEmitInfo* info = state_for(x, x->state());

  LIRItem length(x->length(), this);
  length.load_item_force(FrameMap::rbx_opr);

  LIR_Opr reg = result_register_for(x->type());
  LIR_Opr tmp1 = FrameMap::rcx_oop_opr;
  LIR_Opr tmp2 = FrameMap::rsi_oop_opr;
  LIR_Opr tmp3 = FrameMap::rdi_oop_opr;
  LIR_Opr tmp4 = reg;
  LIR_Opr klass_reg = FrameMap::rdx_metadata_opr;
  LIR_Opr len = length.result();
  BasicType elem_type = x->elt_type();

  __ metadata2reg(ciTypeArrayKlass::make(elem_type)->constant_encoding(), klass_reg);

  CodeStub* slow_path = new NewTypeArrayStub(klass_reg, len, reg, info);
  __ allocate_array(reg, len, tmp1, tmp2, tmp3, tmp4, elem_type, klass_reg, slow_path);

  LIR_Opr result = rlock_result(x);
  __ move(reg, result);
}


void LIRGenerator::do_NewObjectArray(NewObjectArray* x) {
  LIRItem length(x->length(), this);
  // in case of patching (i.e., object class is not yet loaded), we need to reexecute the instruction
  // and therefore provide the state before the parameters have been consumed
  CodeEmitInfo* patching_info = NULL;
  if (!x->klass()->is_loaded() || PatchALot) {
    patching_info =  state_for(x, x->state_before());
  }

  CodeEmitInfo* info = state_for(x, x->state());

  const LIR_Opr reg = result_register_for(x->type());
  LIR_Opr tmp1 = FrameMap::rcx_oop_opr;
  LIR_Opr tmp2 = FrameMap::rsi_oop_opr;
  LIR_Opr tmp3 = FrameMap::rdi_oop_opr;
  LIR_Opr tmp4 = reg;
  LIR_Opr klass_reg = FrameMap::rdx_metadata_opr;

  length.load_item_force(FrameMap::rbx_opr);
  LIR_Opr len = length.result();

  CodeStub* slow_path = new NewObjectArrayStub(klass_reg, len, reg, info);
  ciKlass* obj = (ciKlass*) ciObjArrayKlass::make(x->klass());
  if (obj == ciEnv::unloaded_ciobjarrayklass()) {
    BAILOUT("encountered unloaded_ciobjarrayklass due to out of memory error");
  }
  klass2reg_with_patching(klass_reg, obj, patching_info);
  __ allocate_array(reg, len, tmp1, tmp2, tmp3, tmp4, T_OBJECT, klass_reg, slow_path);

  LIR_Opr result = rlock_result(x);
  __ move(reg, result);
}


void LIRGenerator::do_NewMultiArray(NewMultiArray* x) {
  Values* dims = x->dims();
  int i = dims->length();
  LIRItemList* items = new LIRItemList(dims->length(), NULL);
  while (i-- > 0) {
    LIRItem* size = new LIRItem(dims->at(i), this);
    items->at_put(i, size);
  }

  // Evaluate state_for early since it may emit code.
  CodeEmitInfo* patching_info = NULL;
  if (!x->klass()->is_loaded() || PatchALot) {
    patching_info = state_for(x, x->state_before());

    // Cannot re-use same xhandlers for multiple CodeEmitInfos, so
    // clone all handlers (NOTE: Usually this is handled transparently
    // by the CodeEmitInfo cloning logic in CodeStub constructors but
    // is done explicitly here because a stub isn't being used).
    x->set_exception_handlers(new XHandlers(x->exception_handlers()));
  }
  CodeEmitInfo* info = state_for(x, x->state());

  i = dims->length();
  while (i-- > 0) {
    LIRItem* size = items->at(i);
    size->load_nonconstant();

    store_stack_parameter(size->result(), in_ByteSize(i*4));
  }

  LIR_Opr klass_reg = FrameMap::rax_metadata_opr;
  klass2reg_with_patching(klass_reg, x->klass(), patching_info);

  LIR_Opr rank = FrameMap::rbx_opr;
  __ move(LIR_OprFact::intConst(x->rank()), rank);
  LIR_Opr varargs = FrameMap::rcx_opr;
  __ move(FrameMap::rsp_opr, varargs);
  LIR_OprList* args = new LIR_OprList(3);
  args->append(klass_reg);
  args->append(rank);
  args->append(varargs);
  LIR_Opr reg = result_register_for(x->type());
  __ call_runtime(Runtime1::entry_for(Runtime1::new_multi_array_id),
                  LIR_OprFact::illegalOpr,
                  reg, args, info);

  LIR_Opr result = rlock_result(x);
  __ move(reg, result);
}


void LIRGenerator::do_BlockBegin(BlockBegin* x) {
  // nothing to do for now
}


void LIRGenerator::do_CheckCast(CheckCast* x) {
  LIRItem obj(x->obj(), this);

  CodeEmitInfo* patching_info = NULL;
  if (!x->klass()->is_loaded() || (PatchALot && !x->is_incompatible_class_change_check())) {
    // must do this before locking the destination register as an oop register,
    // and before the obj is loaded (the latter is for deoptimization)
    patching_info = state_for(x, x->state_before());
  }
  obj.load_item();

  // info for exceptions
  CodeEmitInfo* info_for_exception = state_for(x);

  CodeStub* stub;
  if (x->is_incompatible_class_change_check()) {
    assert(patching_info == NULL, "can't patch this");
    stub = new SimpleExceptionStub(Runtime1::throw_incompatible_class_change_error_id, LIR_OprFact::illegalOpr, info_for_exception);
  } else {
    stub = new SimpleExceptionStub(Runtime1::throw_class_cast_exception_id, obj.result(), info_for_exception);
  }
  LIR_Opr reg = rlock_result(x);
  LIR_Opr tmp3 = LIR_OprFact::illegalOpr;
  if (!x->klass()->is_loaded() || UseCompressedClassPointers) {
    tmp3 = new_register(objectType);
  }
  __ checkcast(reg, obj.result(), x->klass(),
               new_register(objectType), new_register(objectType), tmp3,
               x->direct_compare(), info_for_exception, patching_info, stub,
               x->profiled_method(), x->profiled_bci());
}


void LIRGenerator::do_InstanceOf(InstanceOf* x) {
  LIRItem obj(x->obj(), this);

  // result and test object may not be in same register
  LIR_Opr reg = rlock_result(x);
  CodeEmitInfo* patching_info = NULL;
  if ((!x->klass()->is_loaded() || PatchALot)) {
    // must do this before locking the destination register as an oop register
    patching_info = state_for(x, x->state_before());
  }
  obj.load_item();
  LIR_Opr tmp3 = LIR_OprFact::illegalOpr;
  if (!x->klass()->is_loaded() || UseCompressedClassPointers) {
    tmp3 = new_register(objectType);
  }
  __ instanceof(reg, obj.result(), x->klass(),
                new_register(objectType), new_register(objectType), tmp3,
                x->direct_compare(), patching_info, x->profiled_method(), x->profiled_bci());
}


void LIRGenerator::do_If(If* x) {
  assert(x->number_of_sux() == 2, "inconsistency");
  ValueTag tag = x->x()->type()->tag();
  bool is_safepoint = x->is_safepoint();

  If::Condition cond = x->cond();

  LIRItem xitem(x->x(), this);
  LIRItem yitem(x->y(), this);
  LIRItem* xin = &xitem;
  LIRItem* yin = &yitem;

  if (tag == longTag) {
    // for longs, only conditions "eql", "neq", "lss", "geq" are valid;
    // mirror for other conditions
    if (cond == If::gtr || cond == If::leq) {
      cond = Instruction::mirror(cond);
      xin = &yitem;
      yin = &xitem;
    }
    xin->set_destroys_register();
  }
  xin->load_item();
  if (tag == longTag && yin->is_constant() && yin->get_jlong_constant() == 0 && (cond == If::eql || cond == If::neq)) {
    // inline long zero
    yin->dont_load_item();
  } else if (tag == longTag || tag == floatTag || tag == doubleTag) {
    // longs cannot handle constants at right side
    yin->load_item();
  } else {
    yin->dont_load_item();
  }

  // add safepoint before generating condition code so it can be recomputed
  if (x->is_safepoint()) {
    // increment backedge counter if needed
    increment_backedge_counter(state_for(x, x->state_before()), x->profiled_bci());
    __ safepoint(LIR_OprFact::illegalOpr, state_for(x, x->state_before()));
  }
  set_no_result(x);

  LIR_Opr left = xin->result();
  LIR_Opr right = yin->result();
  __ cmp(lir_cond(cond), left, right);
  // Generate branch profiling. Profiling code doesn't kill flags.
  profile_branch(x, cond);
  move_to_phi(x->state());
  if (x->x()->type()->is_float_kind()) {
    __ branch(lir_cond(cond), right->type(), x->tsux(), x->usux());
  } else {
    __ branch(lir_cond(cond), right->type(), x->tsux());
  }
  assert(x->default_sux() == x->fsux(), "wrong destination above");
  __ jump(x->default_sux());
}


LIR_Opr LIRGenerator::getThreadPointer() {
#ifdef _LP64
  return FrameMap::as_pointer_opr(r15_thread);
#else
  LIR_Opr result = new_register(T_INT);
  __ get_thread(result);
  return result;
#endif //
}

void LIRGenerator::trace_block_entry(BlockBegin* block) {
  store_stack_parameter(LIR_OprFact::intConst(block->block_id()), in_ByteSize(0));
  LIR_OprList* args = new LIR_OprList();
  address func = CAST_FROM_FN_PTR(address, Runtime1::trace_block_entry);
  __ call_runtime_leaf(func, LIR_OprFact::illegalOpr, LIR_OprFact::illegalOpr, args);
}


void LIRGenerator::volatile_field_store(LIR_Opr value, LIR_Address* address,
                                        CodeEmitInfo* info) {
  if (address->type() == T_LONG) {
    address = new LIR_Address(address->base(),
                              address->index(), address->scale(),
                              address->disp(), T_DOUBLE);
    // Transfer the value atomically by using FP moves.  This means
    // the value has to be moved between CPU and FPU registers.  It
    // always has to be moved through spill slot since there's no
    // quick way to pack the value into an SSE register.
    LIR_Opr temp_double = new_register(T_DOUBLE);
    LIR_Opr spill = new_register(T_LONG);
    set_vreg_flag(spill, must_start_in_memory);
    __ move(value, spill);
    __ volatile_move(spill, temp_double, T_LONG);
    __ volatile_move(temp_double, LIR_OprFact::address(address), T_LONG, info);
  } else {
    __ store(value, address, info);
  }
}



void LIRGenerator::volatile_field_load(LIR_Address* address, LIR_Opr result,
                                       CodeEmitInfo* info) {
  if (address->type() == T_LONG) {
    address = new LIR_Address(address->base(),
                              address->index(), address->scale(),
                              address->disp(), T_DOUBLE);
    // Transfer the value atomically by using FP moves.  This means
    // the value has to be moved between CPU and FPU registers.  In
    // SSE0 and SSE1 mode it has to be moved through spill slot but in
    // SSE2+ mode it can be moved directly.
    LIR_Opr temp_double = new_register(T_DOUBLE);
    __ volatile_move(LIR_OprFact::address(address), temp_double, T_LONG, info);
    __ volatile_move(temp_double, result, T_LONG);
    if (UseSSE < 2) {
      // no spill slot needed in SSE2 mode because xmm->cpu register move is possible
      set_vreg_flag(result, must_start_in_memory);
    }
  } else {
    __ load(address, result, info);
  }
}

void LIRGenerator::get_Object_unsafe(LIR_Opr dst, LIR_Opr src, LIR_Opr offset,
                                     BasicType type, bool is_volatile) {
  if (is_volatile && type == T_LONG) {
    LIR_Address* addr = new LIR_Address(src, offset, T_DOUBLE);
    LIR_Opr tmp = new_register(T_DOUBLE);
    __ load(addr, tmp);
    LIR_Opr spill = new_register(T_LONG);
    set_vreg_flag(spill, must_start_in_memory);
    __ move(tmp, spill);
    __ move(spill, dst);
  } else {
    LIR_Address* addr = new LIR_Address(src, offset, type);
    __ load(addr, dst);
  }
}


void LIRGenerator::put_Object_unsafe(LIR_Opr src, LIR_Opr offset, LIR_Opr data,
                                     BasicType type, bool is_volatile) {
  if (is_volatile && type == T_LONG) {
    LIR_Address* addr = new LIR_Address(src, offset, T_DOUBLE);
    LIR_Opr tmp = new_register(T_DOUBLE);
    LIR_Opr spill = new_register(T_DOUBLE);
    set_vreg_flag(spill, must_start_in_memory);
    __ move(data, spill);
    __ move(spill, tmp);
    __ move(tmp, addr);
  } else {
    LIR_Address* addr = new LIR_Address(src, offset, type);
    bool is_obj = (type == T_ARRAY || type == T_OBJECT);
    if (is_obj) {
      // Do the pre-write barrier, if any.
      pre_barrier(LIR_OprFact::address(addr), LIR_OprFact::illegalOpr /* pre_val */,
                  true /* do_load */, false /* patch */, NULL);
      __ move(data, addr);
      assert(src->is_register(), "must be register");
      // Seems to be a precise address
      post_barrier(LIR_OprFact::address(addr), data);
    } else {
      __ move(data, addr);
    }
  }
}

void LIRGenerator::do_UnsafeGetAndSetObject(UnsafeGetAndSetObject* x) {
  BasicType type = x->basic_type();
  LIRItem src(x->object(), this);
  LIRItem off(x->offset(), this);
  LIRItem value(x->value(), this);

  src.load_item();
  value.load_item();
  off.load_nonconstant();

  LIR_Opr dst = rlock_result(x, type);
  LIR_Opr data = value.result();
  bool is_obj = (type == T_ARRAY || type == T_OBJECT);
  LIR_Opr offset = off.result();

  assert (type == T_INT || (!x->is_add() && is_obj) LP64_ONLY( || type == T_LONG ), "unexpected type");
  LIR_Address* addr;
  if (offset->is_constant()) {
#ifdef _LP64
    jlong c = offset->as_jlong();
    if ((jlong)((jint)c) == c) {
      addr = new LIR_Address(src.result(), (jint)c, type);
    } else {
      LIR_Opr tmp = new_register(T_LONG);
      __ move(offset, tmp);
      addr = new LIR_Address(src.result(), tmp, type);
    }
#else
    addr = new LIR_Address(src.result(), offset->as_jint(), type);
#endif
  } else {
    addr = new LIR_Address(src.result(), offset, type);
  }

  // Because we want a 2-arg form of xchg and xadd
  __ move(data, dst);

  if (x->is_add()) {
    __ xadd(LIR_OprFact::address(addr), dst, dst, LIR_OprFact::illegalOpr);
  } else {
    if (is_obj) {
      // Do the pre-write barrier, if any.
      pre_barrier(LIR_OprFact::address(addr), LIR_OprFact::illegalOpr /* pre_val */,
                  true /* do_load */, false /* patch */, NULL);
    }
    __ xchg(LIR_OprFact::address(addr), dst, dst, LIR_OprFact::illegalOpr);
    if (is_obj) {
      // Seems to be a precise address
      post_barrier(LIR_OprFact::address(addr), data);
    }
  }
}
