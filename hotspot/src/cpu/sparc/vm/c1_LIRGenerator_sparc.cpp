/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

# include "incls/_precompiled.incl"
# include "incls/_c1_LIRGenerator_sparc.cpp.incl"

#ifdef ASSERT
#define __ gen()->lir(__FILE__, __LINE__)->
#else
#define __ gen()->lir()->
#endif

void LIRItem::load_byte_item() {
  // byte loads use same registers as other loads
  load_item();
}


void LIRItem::load_nonconstant() {
  LIR_Opr r = value()->operand();
  if (_gen->can_inline_as_constant(value())) {
    if (!r->is_constant()) {
      r = LIR_OprFact::value_type(value()->type());
    }
    _result = r;
  } else {
    load_item();
  }
}


//--------------------------------------------------------------
//               LIRGenerator
//--------------------------------------------------------------

LIR_Opr LIRGenerator::exceptionOopOpr()              { return FrameMap::Oexception_opr;  }
LIR_Opr LIRGenerator::exceptionPcOpr()               { return FrameMap::Oissuing_pc_opr; }
LIR_Opr LIRGenerator::syncTempOpr()                  { return new_register(T_OBJECT); }
LIR_Opr LIRGenerator::getThreadTemp()                { return rlock_callee_saved(T_INT); }

LIR_Opr LIRGenerator::result_register_for(ValueType* type, bool callee) {
  LIR_Opr opr;
  switch (type->tag()) {
  case intTag:     opr = callee ? FrameMap::I0_opr      : FrameMap::O0_opr;       break;
  case objectTag:  opr = callee ? FrameMap::I0_oop_opr  : FrameMap::O0_oop_opr;   break;
  case longTag:    opr = callee ? FrameMap::in_long_opr : FrameMap::out_long_opr; break;
  case floatTag:   opr = FrameMap::F0_opr;                                        break;
  case doubleTag:  opr = FrameMap::F0_double_opr;                                 break;

  case addressTag:
  default: ShouldNotReachHere(); return LIR_OprFact::illegalOpr;
  }

  assert(opr->type_field() == as_OprType(as_BasicType(type)), "type mismatch");
  return opr;
}

LIR_Opr LIRGenerator::rlock_callee_saved(BasicType type) {
  LIR_Opr reg = new_register(type);
  set_vreg_flag(reg, callee_saved);
  return reg;
}


LIR_Opr LIRGenerator::rlock_byte(BasicType type) {
  return new_register(T_INT);
}





//--------- loading items into registers --------------------------------

// SPARC cannot inline all constants
bool LIRGenerator::can_store_as_constant(Value v, BasicType type) const {
  if (v->type()->as_IntConstant() != NULL) {
    return v->type()->as_IntConstant()->value() == 0;
  } else if (v->type()->as_LongConstant() != NULL) {
    return v->type()->as_LongConstant()->value() == 0L;
  } else if (v->type()->as_ObjectConstant() != NULL) {
    return v->type()->as_ObjectConstant()->value()->is_null_object();
  } else {
    return false;
  }
}


// only simm13 constants can be inlined
bool LIRGenerator:: can_inline_as_constant(Value i) const {
  if (i->type()->as_IntConstant() != NULL) {
    return Assembler::is_simm13(i->type()->as_IntConstant()->value());
  } else {
    return can_store_as_constant(i, as_BasicType(i->type()));
  }
}


bool LIRGenerator:: can_inline_as_constant(LIR_Const* c) const {
  if (c->type() == T_INT) {
    return Assembler::is_simm13(c->as_jint());
  }
  return false;
}


LIR_Opr LIRGenerator::safepoint_poll_register() {
  return new_register(T_INT);
}



LIR_Address* LIRGenerator::generate_address(LIR_Opr base, LIR_Opr index,
                                            int shift, int disp, BasicType type) {
  assert(base->is_register(), "must be");

  // accumulate fixed displacements
  if (index->is_constant()) {
    disp += index->as_constant_ptr()->as_jint() << shift;
    index = LIR_OprFact::illegalOpr;
  }

  if (index->is_register()) {
    // apply the shift and accumulate the displacement
    if (shift > 0) {
      LIR_Opr tmp = new_register(T_INT);
      __ shift_left(index, shift, tmp);
      index = tmp;
    }
    if (disp != 0) {
      LIR_Opr tmp = new_register(T_INT);
      if (Assembler::is_simm13(disp)) {
        __ add(tmp, LIR_OprFact::intConst(disp), tmp);
        index = tmp;
      } else {
        __ move(LIR_OprFact::intConst(disp), tmp);
        __ add(tmp, index, tmp);
        index = tmp;
      }
      disp = 0;
    }
  } else if (disp != 0 && !Assembler::is_simm13(disp)) {
    // index is illegal so replace it with the displacement loaded into a register
    index = new_register(T_INT);
    __ move(LIR_OprFact::intConst(disp), index);
    disp = 0;
  }

  // at this point we either have base + index or base + displacement
  if (disp == 0) {
    return new LIR_Address(base, index, type);
  } else {
    assert(Assembler::is_simm13(disp), "must be");
    return new LIR_Address(base, disp, type);
  }
}


LIR_Address* LIRGenerator::emit_array_address(LIR_Opr array_opr, LIR_Opr index_opr,
                                              BasicType type, bool needs_card_mark) {
  int elem_size = type2aelembytes(type);
  int shift = exact_log2(elem_size);

  LIR_Opr base_opr;
  int offset = arrayOopDesc::base_offset_in_bytes(type);

  if (index_opr->is_constant()) {
    int i = index_opr->as_constant_ptr()->as_jint();
    int array_offset = i * elem_size;
    if (Assembler::is_simm13(array_offset + offset)) {
      base_opr = array_opr;
      offset = array_offset + offset;
    } else {
      base_opr = new_pointer_register();
      if (Assembler::is_simm13(array_offset)) {
        __ add(array_opr, LIR_OprFact::intptrConst(array_offset), base_opr);
      } else {
        __ move(LIR_OprFact::intptrConst(array_offset), base_opr);
        __ add(base_opr, array_opr, base_opr);
      }
    }
  } else {
#ifdef _LP64
    if (index_opr->type() == T_INT) {
      LIR_Opr tmp = new_register(T_LONG);
      __ convert(Bytecodes::_i2l, index_opr, tmp);
      index_opr = tmp;
    }
#endif

    base_opr = new_pointer_register();
    assert (index_opr->is_register(), "Must be register");
    if (shift > 0) {
      __ shift_left(index_opr, shift, base_opr);
      __ add(base_opr, array_opr, base_opr);
    } else {
      __ add(index_opr, array_opr, base_opr);
    }
  }
  if (needs_card_mark) {
    LIR_Opr ptr = new_pointer_register();
    __ add(base_opr, LIR_OprFact::intptrConst(offset), ptr);
    return new LIR_Address(ptr, 0, type);
  } else {
    return new LIR_Address(base_opr, offset, type);
  }
}


void LIRGenerator::increment_counter(address counter, int step) {
  LIR_Opr pointer = new_pointer_register();
  __ move(LIR_OprFact::intptrConst(counter), pointer);
  LIR_Address* addr = new LIR_Address(pointer, 0, T_INT);
  increment_counter(addr, step);
}

void LIRGenerator::increment_counter(LIR_Address* addr, int step) {
  LIR_Opr temp = new_register(T_INT);
  __ move(addr, temp);
  LIR_Opr c = LIR_OprFact::intConst(step);
  if (Assembler::is_simm13(step)) {
    __ add(temp, c, temp);
  } else {
    LIR_Opr temp2 = new_register(T_INT);
    __ move(c, temp2);
    __ add(temp, temp2, temp);
  }
  __ move(temp, addr);
}


void LIRGenerator::cmp_mem_int(LIR_Condition condition, LIR_Opr base, int disp, int c, CodeEmitInfo* info) {
  LIR_Opr o7opr = FrameMap::O7_opr;
  __ load(new LIR_Address(base, disp, T_INT), o7opr, info);
  __ cmp(condition, o7opr, c);
}


void LIRGenerator::cmp_reg_mem(LIR_Condition condition, LIR_Opr reg, LIR_Opr base, int disp, BasicType type, CodeEmitInfo* info) {
  LIR_Opr o7opr = FrameMap::O7_opr;
  __ load(new LIR_Address(base, disp, type), o7opr, info);
  __ cmp(condition, reg, o7opr);
}


void LIRGenerator::cmp_reg_mem(LIR_Condition condition, LIR_Opr reg, LIR_Opr base, LIR_Opr disp, BasicType type, CodeEmitInfo* info) {
  LIR_Opr o7opr = FrameMap::O7_opr;
  __ load(new LIR_Address(base, disp, type), o7opr, info);
  __ cmp(condition, reg, o7opr);
}


bool LIRGenerator::strength_reduce_multiply(LIR_Opr left, int c, LIR_Opr result, LIR_Opr tmp) {
  assert(left != result, "should be different registers");
  if (is_power_of_2(c + 1)) {
    __ shift_left(left, log2_intptr(c + 1), result);
    __ sub(result, left, result);
    return true;
  } else if (is_power_of_2(c - 1)) {
    __ shift_left(left, log2_intptr(c - 1), result);
    __ add(result, left, result);
    return true;
  }
  return false;
}


void LIRGenerator::store_stack_parameter (LIR_Opr item, ByteSize offset_from_sp) {
  BasicType t = item->type();
  LIR_Opr sp_opr = FrameMap::SP_opr;
  if ((t == T_LONG || t == T_DOUBLE) &&
      ((in_bytes(offset_from_sp) - STACK_BIAS) % 8 != 0)) {
    __ unaligned_move(item, new LIR_Address(sp_opr, in_bytes(offset_from_sp), t));
  } else {
    __ move(item, new LIR_Address(sp_opr, in_bytes(offset_from_sp), t));
  }
}

//----------------------------------------------------------------------
//             visitor functions
//----------------------------------------------------------------------


void LIRGenerator::do_StoreIndexed(StoreIndexed* x) {
  assert(x->is_root(),"");
  bool needs_range_check = true;
  bool use_length = x->length() != NULL;
  bool obj_store = x->elt_type() == T_ARRAY || x->elt_type() == T_OBJECT;
  bool needs_store_check = obj_store && (x->value()->as_Constant() == NULL ||
                                         !get_jobject_constant(x->value())->is_null_object());

  LIRItem array(x->array(), this);
  LIRItem index(x->index(), this);
  LIRItem value(x->value(), this);
  LIRItem length(this);

  array.load_item();
  index.load_nonconstant();

  if (use_length) {
    needs_range_check = x->compute_needs_range_check();
    if (needs_range_check) {
      length.set_instruction(x->length());
      length.load_item();
    }
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
    LIR_Opr tmp1 = FrameMap::G1_opr;
    LIR_Opr tmp2 = FrameMap::G3_opr;
    LIR_Opr tmp3 = FrameMap::G5_opr;

    CodeEmitInfo* store_check_info = new CodeEmitInfo(range_check_info);
    __ store_check(value.result(), array.result(), tmp1, tmp2, tmp3, store_check_info);
  }

  if (obj_store) {
    // Needs GC write barriers.
    pre_barrier(LIR_OprFact::address(array_addr), false, NULL);
  }
  __ move(value.result(), array_addr, null_check_info);
  if (obj_store) {
    // Precise card mark
    post_barrier(LIR_OprFact::address(array_addr), value.result());
  }
}


void LIRGenerator::do_MonitorEnter(MonitorEnter* x) {
  assert(x->is_root(),"");
  LIRItem obj(x->obj(), this);
  obj.load_item();

  set_no_result(x);

  LIR_Opr lock    = FrameMap::G1_opr;
  LIR_Opr scratch = FrameMap::G3_opr;
  LIR_Opr hdr     = FrameMap::G4_opr;

  CodeEmitInfo* info_for_exception = NULL;
  if (x->needs_null_check()) {
    info_for_exception = state_for(x, x->lock_stack_before());
  }

  // this CodeEmitInfo must not have the xhandlers because here the
  // object is already locked (xhandlers expects object to be unlocked)
  CodeEmitInfo* info = state_for(x, x->state(), true);
  monitor_enter(obj.result(), lock, hdr, scratch, x->monitor_no(), info_for_exception, info);
}


void LIRGenerator::do_MonitorExit(MonitorExit* x) {
  assert(x->is_root(),"");
  LIRItem obj(x->obj(), this);
  obj.dont_load_item();

  set_no_result(x);
  LIR_Opr lock      = FrameMap::G1_opr;
  LIR_Opr hdr       = FrameMap::G3_opr;
  LIR_Opr obj_temp  = FrameMap::G4_opr;
  monitor_exit(obj_temp, lock, hdr, x->monitor_no());
}


// _ineg, _lneg, _fneg, _dneg
void LIRGenerator::do_NegateOp(NegateOp* x) {
  LIRItem value(x->x(), this);
  value.load_item();
  LIR_Opr reg = rlock_result(x);
  __ negate(value.result(), reg);
}



// for  _fadd, _fmul, _fsub, _fdiv, _frem
//      _dadd, _dmul, _dsub, _ddiv, _drem
void LIRGenerator::do_ArithmeticOp_FPU(ArithmeticOp* x) {
  switch (x->op()) {
  case Bytecodes::_fadd:
  case Bytecodes::_fmul:
  case Bytecodes::_fsub:
  case Bytecodes::_fdiv:
  case Bytecodes::_dadd:
  case Bytecodes::_dmul:
  case Bytecodes::_dsub:
  case Bytecodes::_ddiv: {
    LIRItem left(x->x(), this);
    LIRItem right(x->y(), this);
    left.load_item();
    right.load_item();
    rlock_result(x);
    arithmetic_op_fpu(x->op(), x->operand(), left.result(), right.result(), x->is_strictfp());
  }
  break;

  case Bytecodes::_frem:
  case Bytecodes::_drem: {
    address entry;
    switch (x->op()) {
    case Bytecodes::_frem:
      entry = CAST_FROM_FN_PTR(address, SharedRuntime::frem);
      break;
    case Bytecodes::_drem:
      entry = CAST_FROM_FN_PTR(address, SharedRuntime::drem);
      break;
    default:
      ShouldNotReachHere();
    }
    LIR_Opr result = call_runtime(x->x(), x->y(), entry, x->type(), NULL);
    set_result(x, result);
  }
  break;

  default: ShouldNotReachHere();
  }
}


// for  _ladd, _lmul, _lsub, _ldiv, _lrem
void LIRGenerator::do_ArithmeticOp_Long(ArithmeticOp* x) {
  switch (x->op()) {
  case Bytecodes::_lrem:
  case Bytecodes::_lmul:
  case Bytecodes::_ldiv: {

    if (x->op() == Bytecodes::_ldiv || x->op() == Bytecodes::_lrem) {
      LIRItem right(x->y(), this);
      right.load_item();

      CodeEmitInfo* info = state_for(x);
      LIR_Opr item = right.result();
      assert(item->is_register(), "must be");
      __ cmp(lir_cond_equal, item, LIR_OprFact::longConst(0));
      __ branch(lir_cond_equal, T_LONG, new DivByZeroStub(info));
    }

    address entry;
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

    // order of arguments to runtime call is reversed.
    LIR_Opr result = call_runtime(x->y(), x->x(), entry, x->type(), NULL);
    set_result(x, result);
    break;
  }
  case Bytecodes::_ladd:
  case Bytecodes::_lsub: {
    LIRItem left(x->x(), this);
    LIRItem right(x->y(), this);
    left.load_item();
    right.load_item();
    rlock_result(x);

    arithmetic_op_long(x->op(), x->operand(), left.result(), right.result(), NULL);
    break;
  }
  default: ShouldNotReachHere();
  }
}


// Returns if item is an int constant that can be represented by a simm13
static bool is_simm13(LIR_Opr item) {
  if (item->is_constant() && item->type() == T_INT) {
    return Assembler::is_simm13(item->as_constant_ptr()->as_jint());
  } else {
    return false;
  }
}


// for: _iadd, _imul, _isub, _idiv, _irem
void LIRGenerator::do_ArithmeticOp_Int(ArithmeticOp* x) {
  bool is_div_rem = x->op() == Bytecodes::_idiv || x->op() == Bytecodes::_irem;
  LIRItem left(x->x(), this);
  LIRItem right(x->y(), this);
  // missing test if instr is commutative and if we should swap
  right.load_nonconstant();
  assert(right.is_constant() || right.is_register(), "wrong state of right");
  left.load_item();
  rlock_result(x);
  if (is_div_rem) {
    CodeEmitInfo* info = state_for(x);
    LIR_Opr tmp = FrameMap::G1_opr;
    if (x->op() == Bytecodes::_irem) {
      __ irem(left.result(), right.result(), x->operand(), tmp, info);
    } else if (x->op() == Bytecodes::_idiv) {
      __ idiv(left.result(), right.result(), x->operand(), tmp, info);
    }
  } else {
    arithmetic_op_int(x->op(), x->operand(), left.result(), right.result(), FrameMap::G1_opr);
  }
}


void LIRGenerator::do_ArithmeticOp(ArithmeticOp* x) {
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
  LIRItem value(x->x(), this);
  LIRItem count(x->y(), this);
  // Long shift destroys count register
  if (value.type()->is_long()) {
    count.set_destroys_register();
  }
  value.load_item();
  // the old backend doesn't support this
  if (count.is_constant() && count.type()->as_IntConstant() != NULL && value.type()->is_int()) {
    jint c = count.get_jint_constant() & 0x1f;
    assert(c >= 0 && c < 32, "should be small");
    count.dont_load_item();
  } else {
    count.load_item();
  }
  LIR_Opr reg = rlock_result(x);
  shift_op(x->op(), reg, value.result(), count.result(), LIR_OprFact::illegalOpr);
}


// _iand, _land, _ior, _lor, _ixor, _lxor
void LIRGenerator::do_LogicOp(LogicOp* x) {
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


void LIRGenerator::do_AttemptUpdate(Intrinsic* x) {
  assert(x->number_of_arguments() == 3, "wrong type");
  LIRItem obj       (x->argument_at(0), this);  // AtomicLong object
  LIRItem cmp_value (x->argument_at(1), this);  // value to compare with field
  LIRItem new_value (x->argument_at(2), this);  // replace field with new_value if it matches cmp_value

  obj.load_item();
  cmp_value.load_item();
  new_value.load_item();

  // generate compare-and-swap and produce zero condition if swap occurs
  int value_offset = sun_misc_AtomicLongCSImpl::value_offset();
  LIR_Opr addr = FrameMap::O7_opr;
  __ add(obj.result(), LIR_OprFact::intConst(value_offset), addr);
  LIR_Opr t1 = FrameMap::G1_opr;  // temp for 64-bit value
  LIR_Opr t2 = FrameMap::G3_opr;  // temp for 64-bit value
  __ cas_long(addr, cmp_value.result(), new_value.result(), t1, t2);

  // generate conditional move of boolean result
  LIR_Opr result = rlock_result(x);
  __ cmove(lir_cond_equal, LIR_OprFact::intConst(1), LIR_OprFact::intConst(0), result);
}


void LIRGenerator::do_CompareAndSwap(Intrinsic* x, ValueType* type) {
  assert(x->number_of_arguments() == 4, "wrong type");
  LIRItem obj   (x->argument_at(0), this);  // object
  LIRItem offset(x->argument_at(1), this);  // offset of field
  LIRItem cmp   (x->argument_at(2), this);  // value to compare with field
  LIRItem val   (x->argument_at(3), this);  // replace field with val if matches cmp

  // Use temps to avoid kills
  LIR_Opr t1 = FrameMap::G1_opr;
  LIR_Opr t2 = FrameMap::G3_opr;
  LIR_Opr addr = new_pointer_register();

  // get address of field
  obj.load_item();
  offset.load_item();
  cmp.load_item();
  val.load_item();

  __ add(obj.result(), offset.result(), addr);

  if (type == objectType) {  // Write-barrier needed for Object fields.
    pre_barrier(obj.result(), false, NULL);
  }

  if (type == objectType)
    __ cas_obj(addr, cmp.result(), val.result(), t1, t2);
  else if (type == intType)
    __ cas_int(addr, cmp.result(), val.result(), t1, t2);
  else if (type == longType)
    __ cas_long(addr, cmp.result(), val.result(), t1, t2);
  else {
    ShouldNotReachHere();
  }

  // generate conditional move of boolean result
  LIR_Opr result = rlock_result(x);
  __ cmove(lir_cond_equal, LIR_OprFact::intConst(1), LIR_OprFact::intConst(0), result);
  if (type == objectType) {  // Write-barrier needed for Object fields.
    // Precise card mark since could either be object or array
    post_barrier(addr, val.result());
  }
}


void LIRGenerator::do_MathIntrinsic(Intrinsic* x) {
  switch (x->id()) {
    case vmIntrinsics::_dabs:
    case vmIntrinsics::_dsqrt: {
      assert(x->number_of_arguments() == 1, "wrong type");
      LIRItem value(x->argument_at(0), this);
      value.load_item();
      LIR_Opr dst = rlock_result(x);

      switch (x->id()) {
      case vmIntrinsics::_dsqrt: {
        __ sqrt(value.result(), dst, LIR_OprFact::illegalOpr);
        break;
      }
      case vmIntrinsics::_dabs: {
        __ abs(value.result(), dst, LIR_OprFact::illegalOpr);
        break;
      }
      }
      break;
    }
    case vmIntrinsics::_dlog10: // fall through
    case vmIntrinsics::_dlog: // fall through
    case vmIntrinsics::_dsin: // fall through
    case vmIntrinsics::_dtan: // fall through
    case vmIntrinsics::_dcos: {
      assert(x->number_of_arguments() == 1, "wrong type");

      address runtime_entry = NULL;
      switch (x->id()) {
      case vmIntrinsics::_dsin:
        runtime_entry = CAST_FROM_FN_PTR(address, SharedRuntime::dsin);
        break;
      case vmIntrinsics::_dcos:
        runtime_entry = CAST_FROM_FN_PTR(address, SharedRuntime::dcos);
        break;
      case vmIntrinsics::_dtan:
        runtime_entry = CAST_FROM_FN_PTR(address, SharedRuntime::dtan);
        break;
      case vmIntrinsics::_dlog:
        runtime_entry = CAST_FROM_FN_PTR(address, SharedRuntime::dlog);
        break;
      case vmIntrinsics::_dlog10:
        runtime_entry = CAST_FROM_FN_PTR(address, SharedRuntime::dlog10);
        break;
      default:
        ShouldNotReachHere();
      }

      LIR_Opr result = call_runtime(x->argument_at(0), runtime_entry, x->type(), NULL);
      set_result(x, result);
    }
  }
}


void LIRGenerator::do_ArrayCopy(Intrinsic* x) {
  assert(x->number_of_arguments() == 5, "wrong type");

  // Make all state_for calls early since they can emit code
  CodeEmitInfo* info = state_for(x, x->state());

  // Note: spill caller save before setting the item
  LIRItem src     (x->argument_at(0), this);
  LIRItem src_pos (x->argument_at(1), this);
  LIRItem dst     (x->argument_at(2), this);
  LIRItem dst_pos (x->argument_at(3), this);
  LIRItem length  (x->argument_at(4), this);
  // load all values in callee_save_registers, as this makes the
  // parameter passing to the fast case simpler
  src.load_item_force     (rlock_callee_saved(T_OBJECT));
  src_pos.load_item_force (rlock_callee_saved(T_INT));
  dst.load_item_force     (rlock_callee_saved(T_OBJECT));
  dst_pos.load_item_force (rlock_callee_saved(T_INT));
  length.load_item_force  (rlock_callee_saved(T_INT));

  int flags;
  ciArrayKlass* expected_type;
  arraycopy_helper(x, &flags, &expected_type);

  __ arraycopy(src.result(), src_pos.result(), dst.result(), dst_pos.result(),
               length.result(), rlock_callee_saved(T_INT),
               expected_type, flags, info);
  set_no_result(x);
}

// _i2l, _i2f, _i2d, _l2i, _l2f, _l2d, _f2i, _f2l, _f2d, _d2i, _d2l, _d2f
// _i2b, _i2c, _i2s
void LIRGenerator::do_Convert(Convert* x) {

  switch (x->op()) {
    case Bytecodes::_f2l:
    case Bytecodes::_d2l:
    case Bytecodes::_d2i:
    case Bytecodes::_l2f:
    case Bytecodes::_l2d: {

      address entry;
      switch (x->op()) {
      case Bytecodes::_l2f:
        entry = CAST_FROM_FN_PTR(address, SharedRuntime::l2f);
        break;
      case Bytecodes::_l2d:
        entry = CAST_FROM_FN_PTR(address, SharedRuntime::l2d);
        break;
      case Bytecodes::_f2l:
        entry = CAST_FROM_FN_PTR(address, SharedRuntime::f2l);
        break;
      case Bytecodes::_d2l:
        entry = CAST_FROM_FN_PTR(address, SharedRuntime::d2l);
        break;
      case Bytecodes::_d2i:
        entry = CAST_FROM_FN_PTR(address, SharedRuntime::d2i);
        break;
      default:
        ShouldNotReachHere();
      }
      LIR_Opr result = call_runtime(x->value(), entry, x->type(), NULL);
      set_result(x, result);
      break;
    }

    case Bytecodes::_i2f:
    case Bytecodes::_i2d: {
      LIRItem value(x->value(), this);

      LIR_Opr reg = rlock_result(x);
      // To convert an int to double, we need to load the 32-bit int
      // from memory into a single precision floating point register
      // (even numbered). Then the sparc fitod instruction takes care
      // of the conversion. This is a bit ugly, but is the best way to
      // get the int value in a single precision floating point register
      value.load_item();
      LIR_Opr tmp = force_to_spill(value.result(), T_FLOAT);
      __ convert(x->op(), tmp, reg);
      break;
    }
    break;

    case Bytecodes::_i2l:
    case Bytecodes::_i2b:
    case Bytecodes::_i2c:
    case Bytecodes::_i2s:
    case Bytecodes::_l2i:
    case Bytecodes::_f2d:
    case Bytecodes::_d2f: { // inline code
      LIRItem value(x->value(), this);

      value.load_item();
      LIR_Opr reg = rlock_result(x);
      __ convert(x->op(), value.result(), reg, false);
    }
    break;

    case Bytecodes::_f2i: {
      LIRItem value (x->value(), this);
      value.set_destroys_register();
      value.load_item();
      LIR_Opr reg = rlock_result(x);
      set_vreg_flag(reg, must_start_in_memory);
      __ convert(x->op(), value.result(), reg, false);
    }
    break;

    default: ShouldNotReachHere();
  }
}


void LIRGenerator::do_NewInstance(NewInstance* x) {
  // This instruction can be deoptimized in the slow path : use
  // O0 as result register.
  const LIR_Opr reg = result_register_for(x->type());

  if (PrintNotLoaded && !x->klass()->is_loaded()) {
    tty->print_cr("   ###class not loaded at new bci %d", x->bci());
  }
  CodeEmitInfo* info = state_for(x, x->state());
  LIR_Opr tmp1 = FrameMap::G1_oop_opr;
  LIR_Opr tmp2 = FrameMap::G3_oop_opr;
  LIR_Opr tmp3 = FrameMap::G4_oop_opr;
  LIR_Opr tmp4 = FrameMap::O1_oop_opr;
  LIR_Opr klass_reg = FrameMap::G5_oop_opr;
  new_instance(reg, x->klass(), tmp1, tmp2, tmp3, tmp4, klass_reg, info);
  LIR_Opr result = rlock_result(x);
  __ move(reg, result);
}


void LIRGenerator::do_NewTypeArray(NewTypeArray* x) {
  // Evaluate state_for early since it may emit code
  CodeEmitInfo* info = state_for(x, x->state());

  LIRItem length(x->length(), this);
  length.load_item();

  LIR_Opr reg = result_register_for(x->type());
  LIR_Opr tmp1 = FrameMap::G1_oop_opr;
  LIR_Opr tmp2 = FrameMap::G3_oop_opr;
  LIR_Opr tmp3 = FrameMap::G4_oop_opr;
  LIR_Opr tmp4 = FrameMap::O1_oop_opr;
  LIR_Opr klass_reg = FrameMap::G5_oop_opr;
  LIR_Opr len = length.result();
  BasicType elem_type = x->elt_type();

  __ oop2reg(ciTypeArrayKlass::make(elem_type)->constant_encoding(), klass_reg);

  CodeStub* slow_path = new NewTypeArrayStub(klass_reg, len, reg, info);
  __ allocate_array(reg, len, tmp1, tmp2, tmp3, tmp4, elem_type, klass_reg, slow_path);

  LIR_Opr result = rlock_result(x);
  __ move(reg, result);
}


void LIRGenerator::do_NewObjectArray(NewObjectArray* x) {
  // Evaluate state_for early since it may emit code.
  CodeEmitInfo* info = state_for(x, x->state());
  // in case of patching (i.e., object class is not yet loaded), we need to reexecute the instruction
  // and therefore provide the state before the parameters have been consumed
  CodeEmitInfo* patching_info = NULL;
  if (!x->klass()->is_loaded() || PatchALot) {
    patching_info = state_for(x, x->state_before());
  }

  LIRItem length(x->length(), this);
  length.load_item();

  const LIR_Opr reg = result_register_for(x->type());
  LIR_Opr tmp1 = FrameMap::G1_oop_opr;
  LIR_Opr tmp2 = FrameMap::G3_oop_opr;
  LIR_Opr tmp3 = FrameMap::G4_oop_opr;
  LIR_Opr tmp4 = FrameMap::O1_oop_opr;
  LIR_Opr klass_reg = FrameMap::G5_oop_opr;
  LIR_Opr len = length.result();

  CodeStub* slow_path = new NewObjectArrayStub(klass_reg, len, reg, info);
  ciObject* obj = (ciObject*) ciObjArrayKlass::make(x->klass());
  if (obj == ciEnv::unloaded_ciobjarrayklass()) {
    BAILOUT("encountered unloaded_ciobjarrayklass due to out of memory error");
  }
  jobject2reg_with_patching(klass_reg, obj, patching_info);
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

    // cannot re-use same xhandlers for multiple CodeEmitInfos, so
    // clone all handlers.  This is handled transparently in other
    // places by the CodeEmitInfo cloning logic but is handled
    // specially here because a stub isn't being used.
    x->set_exception_handlers(new XHandlers(x->exception_handlers()));
  }
  CodeEmitInfo* info = state_for(x, x->state());

  i = dims->length();
  while (i-- > 0) {
    LIRItem* size = items->at(i);
    size->load_item();
    store_stack_parameter (size->result(),
                           in_ByteSize(STACK_BIAS +
                                       frame::memory_parameter_word_sp_offset * wordSize +
                                       i * sizeof(jint)));
  }

  // This instruction can be deoptimized in the slow path : use
  // O0 as result register.
  const LIR_Opr reg = result_register_for(x->type());
  jobject2reg_with_patching(reg, x->klass(), patching_info);
  LIR_Opr rank = FrameMap::O1_opr;
  __ move(LIR_OprFact::intConst(x->rank()), rank);
  LIR_Opr varargs = FrameMap::as_pointer_opr(O2);
  int offset_from_sp = (frame::memory_parameter_word_sp_offset * wordSize) + STACK_BIAS;
  __ add(FrameMap::SP_opr,
         LIR_OprFact::intptrConst(offset_from_sp),
         varargs);
  LIR_OprList* args = new LIR_OprList(3);
  args->append(reg);
  args->append(rank);
  args->append(varargs);
  __ call_runtime(Runtime1::entry_for(Runtime1::new_multi_array_id),
                  LIR_OprFact::illegalOpr,
                  reg, args, info);

  LIR_Opr result = rlock_result(x);
  __ move(reg, result);
}


void LIRGenerator::do_BlockBegin(BlockBegin* x) {
}


void LIRGenerator::do_CheckCast(CheckCast* x) {
  LIRItem obj(x->obj(), this);
  CodeEmitInfo* patching_info = NULL;
  if (!x->klass()->is_loaded() || (PatchALot && !x->is_incompatible_class_change_check())) {
    // must do this before locking the destination register as an oop register,
    // and before the obj is loaded (so x->obj()->item() is valid for creating a debug info location)
    patching_info = state_for(x, x->state_before());
  }
  obj.load_item();
  LIR_Opr out_reg = rlock_result(x);
  CodeStub* stub;
  CodeEmitInfo* info_for_exception = state_for(x, x->state()->copy_locks());

  if (x->is_incompatible_class_change_check()) {
    assert(patching_info == NULL, "can't patch this");
    stub = new SimpleExceptionStub(Runtime1::throw_incompatible_class_change_error_id, LIR_OprFact::illegalOpr, info_for_exception);
  } else {
    stub = new SimpleExceptionStub(Runtime1::throw_class_cast_exception_id, obj.result(), info_for_exception);
  }
  LIR_Opr tmp1 = FrameMap::G1_oop_opr;
  LIR_Opr tmp2 = FrameMap::G3_oop_opr;
  LIR_Opr tmp3 = FrameMap::G4_oop_opr;
  __ checkcast(out_reg, obj.result(), x->klass(), tmp1, tmp2, tmp3,
               x->direct_compare(), info_for_exception, patching_info, stub,
               x->profiled_method(), x->profiled_bci());
}


void LIRGenerator::do_InstanceOf(InstanceOf* x) {
  LIRItem obj(x->obj(), this);
  CodeEmitInfo* patching_info = NULL;
  if (!x->klass()->is_loaded() || PatchALot) {
    patching_info = state_for(x, x->state_before());
  }
  // ensure the result register is not the input register because the result is initialized before the patching safepoint
  obj.load_item();
  LIR_Opr out_reg = rlock_result(x);
  LIR_Opr tmp1 = FrameMap::G1_oop_opr;
  LIR_Opr tmp2 = FrameMap::G3_oop_opr;
  LIR_Opr tmp3 = FrameMap::G4_oop_opr;
  __ instanceof(out_reg, obj.result(), x->klass(), tmp1, tmp2, tmp3,  x->direct_compare(), patching_info);
}


void LIRGenerator::do_If(If* x) {
  assert(x->number_of_sux() == 2, "inconsistency");
  ValueTag tag = x->x()->type()->tag();
  LIRItem xitem(x->x(), this);
  LIRItem yitem(x->y(), this);
  LIRItem* xin = &xitem;
  LIRItem* yin = &yitem;
  If::Condition cond = x->cond();

  if (tag == longTag) {
    // for longs, only conditions "eql", "neq", "lss", "geq" are valid;
    // mirror for other conditions
    if (cond == If::gtr || cond == If::leq) {
      // swap inputs
      cond = Instruction::mirror(cond);
      xin = &yitem;
      yin = &xitem;
    }
    xin->set_destroys_register();
  }

  LIR_Opr left = LIR_OprFact::illegalOpr;
  LIR_Opr right = LIR_OprFact::illegalOpr;

  xin->load_item();
  left = xin->result();

  if (is_simm13(yin->result())) {
    // inline int constants which are small enough to be immediate operands
    right = LIR_OprFact::value_type(yin->value()->type());
  } else if (tag == longTag && yin->is_constant() && yin->get_jlong_constant() == 0 &&
             (cond == If::eql || cond == If::neq)) {
    // inline long zero
    right = LIR_OprFact::value_type(yin->value()->type());
  } else if (tag == objectTag && yin->is_constant() && (yin->get_jobject_constant()->is_null_object())) {
    right = LIR_OprFact::value_type(yin->value()->type());
  } else {
    yin->load_item();
    right = yin->result();
  }
  set_no_result(x);

  // add safepoint before generating condition code so it can be recomputed
  if (x->is_safepoint()) {
    // increment backedge counter if needed
    increment_backedge_counter(state_for(x, x->state_before()));

    __ safepoint(new_register(T_INT), state_for(x, x->state_before()));
  }

  __ cmp(lir_cond(cond), left, right);
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
  return FrameMap::as_pointer_opr(G2);
}


void LIRGenerator::trace_block_entry(BlockBegin* block) {
  __ move(LIR_OprFact::intConst(block->block_id()), FrameMap::O0_opr);
  LIR_OprList* args = new LIR_OprList(1);
  args->append(FrameMap::O0_opr);
  address func = CAST_FROM_FN_PTR(address, Runtime1::trace_block_entry);
  __ call_runtime_leaf(func, rlock_callee_saved(T_INT), LIR_OprFact::illegalOpr, args);
}


void LIRGenerator::volatile_field_store(LIR_Opr value, LIR_Address* address,
                                        CodeEmitInfo* info) {
#ifdef _LP64
  __ store(value, address, info);
#else
  __ volatile_store_mem_reg(value, address, info);
#endif
}

void LIRGenerator::volatile_field_load(LIR_Address* address, LIR_Opr result,
                                       CodeEmitInfo* info) {
#ifdef _LP64
  __ load(address, result, info);
#else
  __ volatile_load_mem_reg(address, result, info);
#endif
}


void LIRGenerator::put_Object_unsafe(LIR_Opr src, LIR_Opr offset, LIR_Opr data,
                                     BasicType type, bool is_volatile) {
  LIR_Opr base_op = src;
  LIR_Opr index_op = offset;

  bool is_obj = (type == T_ARRAY || type == T_OBJECT);
#ifndef _LP64
  if (is_volatile && type == T_LONG) {
    __ volatile_store_unsafe_reg(data, src, offset, type, NULL, lir_patch_none);
  } else
#endif
    {
      if (type == T_BOOLEAN) {
        type = T_BYTE;
      }
      LIR_Address* addr;
      if (type == T_ARRAY || type == T_OBJECT) {
        LIR_Opr tmp = new_pointer_register();
        __ add(base_op, index_op, tmp);
        addr = new LIR_Address(tmp, 0, type);
      } else {
        addr = new LIR_Address(base_op, index_op, type);
      }

      if (is_obj) {
        pre_barrier(LIR_OprFact::address(addr), false, NULL);
        // _bs->c1_write_barrier_pre(this, LIR_OprFact::address(addr));
      }
      __ move(data, addr);
      if (is_obj) {
        // This address is precise
        post_barrier(LIR_OprFact::address(addr), data);
      }
    }
}


void LIRGenerator::get_Object_unsafe(LIR_Opr dst, LIR_Opr src, LIR_Opr offset,
                                     BasicType type, bool is_volatile) {
#ifndef _LP64
  if (is_volatile && type == T_LONG) {
    __ volatile_load_unsafe_reg(src, offset, dst, type, NULL, lir_patch_none);
  } else
#endif
    {
    LIR_Address* addr = new LIR_Address(src, offset, type);
    __ load(addr, dst);
  }
}
