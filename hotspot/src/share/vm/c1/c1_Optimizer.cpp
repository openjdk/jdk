/*
 * Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_c1_Optimizer.cpp.incl"

define_array(ValueSetArray, ValueSet*);
define_stack(ValueSetList, ValueSetArray);


Optimizer::Optimizer(IR* ir) {
  assert(ir->is_valid(), "IR must be valid");
  _ir = ir;
}

class CE_Eliminator: public BlockClosure {
 private:
  IR* _hir;
  int _cee_count;                                // the number of CEs successfully eliminated
  int _has_substitution;

 public:
  CE_Eliminator(IR* hir) : _cee_count(0), _hir(hir) {
    _has_substitution = false;
    _hir->iterate_preorder(this);
    if (_has_substitution) {
      // substituted some phis so resolve the substitution
      SubstitutionResolver sr(_hir);
    }
  }
  int cee_count() const                          { return _cee_count; }

  void adjust_exception_edges(BlockBegin* block, BlockBegin* sux) {
    int e = sux->number_of_exception_handlers();
    for (int i = 0; i < e; i++) {
      BlockBegin* xhandler = sux->exception_handler_at(i);
      block->add_exception_handler(xhandler);

      assert(xhandler->is_predecessor(sux), "missing predecessor");
      if (sux->number_of_preds() == 0) {
        // sux is disconnected from graph so disconnect from exception handlers
        xhandler->remove_predecessor(sux);
      }
      if (!xhandler->is_predecessor(block)) {
        xhandler->add_predecessor(block);
      }
    }
  }

  virtual void block_do(BlockBegin* block) {
    // 1) find conditional expression
    // check if block ends with an If
    If* if_ = block->end()->as_If();
    if (if_ == NULL) return;

    // check if If works on int or object types
    // (we cannot handle If's working on long, float or doubles yet,
    // since IfOp doesn't support them - these If's show up if cmp
    // operations followed by If's are eliminated)
    ValueType* if_type = if_->x()->type();
    if (!if_type->is_int() && !if_type->is_object()) return;

    BlockBegin* t_block = if_->tsux();
    BlockBegin* f_block = if_->fsux();
    Instruction* t_cur = t_block->next();
    Instruction* f_cur = f_block->next();

    // one Constant may be present between BlockBegin and BlockEnd
    Value t_const = NULL;
    Value f_const = NULL;
    if (t_cur->as_Constant() != NULL && !t_cur->can_trap()) {
      t_const = t_cur;
      t_cur = t_cur->next();
    }
    if (f_cur->as_Constant() != NULL && !f_cur->can_trap()) {
      f_const = f_cur;
      f_cur = f_cur->next();
    }

    // check if both branches end with a goto
    Goto* t_goto = t_cur->as_Goto();
    if (t_goto == NULL) return;
    Goto* f_goto = f_cur->as_Goto();
    if (f_goto == NULL) return;

    // check if both gotos merge into the same block
    BlockBegin* sux = t_goto->default_sux();
    if (sux != f_goto->default_sux()) return;

    // check if at least one word was pushed on sux_state
    ValueStack* sux_state = sux->state();
    if (sux_state->stack_size() <= if_->state()->stack_size()) return;

    // check if phi function is present at end of successor stack and that
    // only this phi was pushed on the stack
    Value sux_phi = sux_state->stack_at(if_->state()->stack_size());
    if (sux_phi == NULL || sux_phi->as_Phi() == NULL || sux_phi->as_Phi()->block() != sux) return;
    if (sux_phi->type()->size() != sux_state->stack_size() - if_->state()->stack_size()) return;

    // get the values that were pushed in the true- and false-branch
    Value t_value = t_goto->state()->stack_at(if_->state()->stack_size());
    Value f_value = f_goto->state()->stack_at(if_->state()->stack_size());

    // backend does not support floats
    assert(t_value->type()->base() == f_value->type()->base(), "incompatible types");
    if (t_value->type()->is_float_kind()) return;

    // check that successor has no other phi functions but sux_phi
    // this can happen when t_block or f_block contained additonal stores to local variables
    // that are no longer represented by explicit instructions
    for_each_phi_fun(sux, phi,
                     if (phi != sux_phi) return;
                     );
    // true and false blocks can't have phis
    for_each_phi_fun(t_block, phi, return; );
    for_each_phi_fun(f_block, phi, return; );

    // 2) substitute conditional expression
    //    with an IfOp followed by a Goto
    // cut if_ away and get node before
    Instruction* cur_end = if_->prev(block);
    int bci = if_->bci();

    // append constants of true- and false-block if necessary
    // clone constants because original block must not be destroyed
    assert((t_value != f_const && f_value != t_const) || t_const == f_const, "mismatch");
    if (t_value == t_const) {
      t_value = new Constant(t_const->type());
      cur_end = cur_end->set_next(t_value, bci);
    }
    if (f_value == f_const) {
      f_value = new Constant(f_const->type());
      cur_end = cur_end->set_next(f_value, bci);
    }

    // it is very unlikely that the condition can be statically decided
    // (this was checked previously by the Canonicalizer), so always
    // append IfOp
    Value result = new IfOp(if_->x(), if_->cond(), if_->y(), t_value, f_value);
    cur_end = cur_end->set_next(result, bci);

    // append Goto to successor
    ValueStack* state_before = if_->is_safepoint() ? if_->state_before() : NULL;
    Goto* goto_ = new Goto(sux, state_before, if_->is_safepoint() || t_goto->is_safepoint() || f_goto->is_safepoint());

    // prepare state for Goto
    ValueStack* goto_state = if_->state();
    while (sux_state->scope() != goto_state->scope()) {
      goto_state = goto_state->pop_scope();
      assert(goto_state != NULL, "states do not match up");
    }
    goto_state = goto_state->copy();
    goto_state->push(result->type(), result);
    assert(goto_state->is_same_across_scopes(sux_state), "states must match now");
    goto_->set_state(goto_state);

    // Steal the bci for the goto from the sux
    cur_end = cur_end->set_next(goto_, sux->bci());

    // Adjust control flow graph
    BlockBegin::disconnect_edge(block, t_block);
    BlockBegin::disconnect_edge(block, f_block);
    if (t_block->number_of_preds() == 0) {
      BlockBegin::disconnect_edge(t_block, sux);
    }
    adjust_exception_edges(block, t_block);
    if (f_block->number_of_preds() == 0) {
      BlockBegin::disconnect_edge(f_block, sux);
    }
    adjust_exception_edges(block, f_block);

    // update block end
    block->set_end(goto_);

    // substitute the phi if possible
    if (sux_phi->as_Phi()->operand_count() == 1) {
      assert(sux_phi->as_Phi()->operand_at(0) == result, "screwed up phi");
      sux_phi->set_subst(result);
      _has_substitution = true;
    }

    // 3) successfully eliminated a conditional expression
    _cee_count++;
    if (PrintCEE) {
      tty->print_cr("%d. CEE in B%d (B%d B%d)", cee_count(), block->block_id(), t_block->block_id(), f_block->block_id());
    }

    _hir->verify();
  }
};


void Optimizer::eliminate_conditional_expressions() {
  // find conditional expressions & replace them with IfOps
  CE_Eliminator ce(ir());
}


class BlockMerger: public BlockClosure {
 private:
  IR* _hir;
  int _merge_count;              // the number of block pairs successfully merged

 public:
  BlockMerger(IR* hir)
  : _hir(hir)
  , _merge_count(0)
  {
    _hir->iterate_preorder(this);
  }

  bool try_merge(BlockBegin* block) {
    BlockEnd* end = block->end();
    if (end->as_Goto() != NULL) {
      assert(end->number_of_sux() == 1, "end must have exactly one successor");
      // Note: It would be sufficient to check for the number of successors (= 1)
      //       in order to decide if this block can be merged potentially. That
      //       would then also include switch statements w/ only a default case.
      //       However, in that case we would need to make sure the switch tag
      //       expression is executed if it can produce observable side effects.
      //       We should probably have the canonicalizer simplifying such switch
      //       statements and then we are sure we don't miss these merge opportunities
      //       here (was bug - gri 7/7/99).
      BlockBegin* sux = end->default_sux();
      if (sux->number_of_preds() == 1 && !sux->is_entry_block() && !end->is_safepoint()) {
        // merge the two blocks

#ifdef ASSERT
        // verify that state at the end of block and at the beginning of sux are equal
        // no phi functions must be present at beginning of sux
        ValueStack* sux_state = sux->state();
        ValueStack* end_state = end->state();
        while (end_state->scope() != sux_state->scope()) {
          // match up inlining level
          end_state = end_state->pop_scope();
        }
        assert(end_state->stack_size() == sux_state->stack_size(), "stack not equal");
        assert(end_state->locals_size() == sux_state->locals_size(), "locals not equal");

        int index;
        Value sux_value;
        for_each_stack_value(sux_state, index, sux_value) {
          assert(sux_value == end_state->stack_at(index), "stack not equal");
        }
        for_each_local_value(sux_state, index, sux_value) {
          assert(sux_value == end_state->local_at(index), "locals not equal");
        }
        assert(sux_state->caller_state() == end_state->caller_state(), "caller not equal");
#endif

        // find instruction before end & append first instruction of sux block
        Instruction* prev = end->prev(block);
        Instruction* next = sux->next();
        assert(prev->as_BlockEnd() == NULL, "must not be a BlockEnd");
        prev->set_next(next, next->bci());
        sux->disconnect_from_graph();
        block->set_end(sux->end());
        // add exception handlers of deleted block, if any
        for (int k = 0; k < sux->number_of_exception_handlers(); k++) {
          BlockBegin* xhandler = sux->exception_handler_at(k);
          block->add_exception_handler(xhandler);

          // also substitute predecessor of exception handler
          assert(xhandler->is_predecessor(sux), "missing predecessor");
          xhandler->remove_predecessor(sux);
          if (!xhandler->is_predecessor(block)) {
            xhandler->add_predecessor(block);
          }
        }

        // debugging output
        _merge_count++;
        if (PrintBlockElimination) {
          tty->print_cr("%d. merged B%d & B%d (stack size = %d)",
                        _merge_count, block->block_id(), sux->block_id(), sux->state()->stack_size());
        }

        _hir->verify();

        If* if_ = block->end()->as_If();
        if (if_) {
          IfOp* ifop    = if_->x()->as_IfOp();
          Constant* con = if_->y()->as_Constant();
          bool swapped = false;
          if (!con || !ifop) {
            ifop = if_->y()->as_IfOp();
            con  = if_->x()->as_Constant();
            swapped = true;
          }
          if (con && ifop) {
            Constant* tval = ifop->tval()->as_Constant();
            Constant* fval = ifop->fval()->as_Constant();
            if (tval && fval) {
              // Find the instruction before if_, starting with ifop.
              // When if_ and ifop are not in the same block, prev
              // becomes NULL In such (rare) cases it is not
              // profitable to perform the optimization.
              Value prev = ifop;
              while (prev != NULL && prev->next() != if_) {
                prev = prev->next();
              }

              if (prev != NULL) {
                Instruction::Condition cond = if_->cond();
                BlockBegin* tsux = if_->tsux();
                BlockBegin* fsux = if_->fsux();
                if (swapped) {
                  cond = Instruction::mirror(cond);
                }

                BlockBegin* tblock = tval->compare(cond, con, tsux, fsux);
                BlockBegin* fblock = fval->compare(cond, con, tsux, fsux);
                if (tblock != fblock && !if_->is_safepoint()) {
                  If* newif = new If(ifop->x(), ifop->cond(), false, ifop->y(),
                                     tblock, fblock, if_->state_before(), if_->is_safepoint());
                  newif->set_state(if_->state()->copy());

                  assert(prev->next() == if_, "must be guaranteed by above search");
                  prev->set_next(newif, if_->bci());
                  block->set_end(newif);

                  _merge_count++;
                  if (PrintBlockElimination) {
                    tty->print_cr("%d. replaced If and IfOp at end of B%d with single If", _merge_count, block->block_id());
                  }

                  _hir->verify();
                }
              }
            }
          }
        }

        return true;
      }
    }
    return false;
  }

  virtual void block_do(BlockBegin* block) {
    _hir->verify();
    // repeat since the same block may merge again
    while (try_merge(block)) {
      _hir->verify();
    }
  }
};


void Optimizer::eliminate_blocks() {
  // merge blocks if possible
  BlockMerger bm(ir());
}


class NullCheckEliminator;
class NullCheckVisitor: public InstructionVisitor {
private:
  NullCheckEliminator* _nce;
  NullCheckEliminator* nce() { return _nce; }

public:
  NullCheckVisitor() {}

  void set_eliminator(NullCheckEliminator* nce) { _nce = nce; }

  void do_Phi            (Phi*             x);
  void do_Local          (Local*           x);
  void do_Constant       (Constant*        x);
  void do_LoadField      (LoadField*       x);
  void do_StoreField     (StoreField*      x);
  void do_ArrayLength    (ArrayLength*     x);
  void do_LoadIndexed    (LoadIndexed*     x);
  void do_StoreIndexed   (StoreIndexed*    x);
  void do_NegateOp       (NegateOp*        x);
  void do_ArithmeticOp   (ArithmeticOp*    x);
  void do_ShiftOp        (ShiftOp*         x);
  void do_LogicOp        (LogicOp*         x);
  void do_CompareOp      (CompareOp*       x);
  void do_IfOp           (IfOp*            x);
  void do_Convert        (Convert*         x);
  void do_NullCheck      (NullCheck*       x);
  void do_Invoke         (Invoke*          x);
  void do_NewInstance    (NewInstance*     x);
  void do_NewTypeArray   (NewTypeArray*    x);
  void do_NewObjectArray (NewObjectArray*  x);
  void do_NewMultiArray  (NewMultiArray*   x);
  void do_CheckCast      (CheckCast*       x);
  void do_InstanceOf     (InstanceOf*      x);
  void do_MonitorEnter   (MonitorEnter*    x);
  void do_MonitorExit    (MonitorExit*     x);
  void do_Intrinsic      (Intrinsic*       x);
  void do_BlockBegin     (BlockBegin*      x);
  void do_Goto           (Goto*            x);
  void do_If             (If*              x);
  void do_IfInstanceOf   (IfInstanceOf*    x);
  void do_TableSwitch    (TableSwitch*     x);
  void do_LookupSwitch   (LookupSwitch*    x);
  void do_Return         (Return*          x);
  void do_Throw          (Throw*           x);
  void do_Base           (Base*            x);
  void do_OsrEntry       (OsrEntry*        x);
  void do_ExceptionObject(ExceptionObject* x);
  void do_RoundFP        (RoundFP*         x);
  void do_UnsafeGetRaw   (UnsafeGetRaw*    x);
  void do_UnsafePutRaw   (UnsafePutRaw*    x);
  void do_UnsafeGetObject(UnsafeGetObject* x);
  void do_UnsafePutObject(UnsafePutObject* x);
  void do_UnsafePrefetchRead (UnsafePrefetchRead*  x);
  void do_UnsafePrefetchWrite(UnsafePrefetchWrite* x);
  void do_ProfileCall    (ProfileCall*     x);
  void do_ProfileCounter (ProfileCounter*  x);
};


// Because of a static contained within (for the purpose of iteration
// over instructions), it is only valid to have one of these active at
// a time
class NullCheckEliminator {
 private:
  static NullCheckEliminator* _static_nce;
  static void                 do_value(Value* vp);

  Optimizer*        _opt;

  ValueSet*         _visitable_instructions;        // Visit each instruction only once per basic block
  BlockList*        _work_list;                   // Basic blocks to visit

  bool visitable(Value x) {
    assert(_visitable_instructions != NULL, "check");
    return _visitable_instructions->contains(x);
  }
  void mark_visited(Value x) {
    assert(_visitable_instructions != NULL, "check");
    _visitable_instructions->remove(x);
  }
  void mark_visitable(Value x) {
    assert(_visitable_instructions != NULL, "check");
    _visitable_instructions->put(x);
  }
  void clear_visitable_state() {
    assert(_visitable_instructions != NULL, "check");
    _visitable_instructions->clear();
  }

  ValueSet*         _set;                         // current state, propagated to subsequent BlockBegins
  ValueSetList      _block_states;                // BlockBegin null-check states for all processed blocks
  NullCheckVisitor  _visitor;
  NullCheck*        _last_explicit_null_check;

  bool set_contains(Value x)                      { assert(_set != NULL, "check"); return _set->contains(x); }
  void set_put     (Value x)                      { assert(_set != NULL, "check"); _set->put(x); }
  void set_remove  (Value x)                      { assert(_set != NULL, "check"); _set->remove(x); }

  BlockList* work_list()                          { return _work_list; }

  void iterate_all();
  void iterate_one(BlockBegin* block);

  ValueSet* state()                               { return _set; }
  void      set_state_from (ValueSet* state)      { _set->set_from(state); }
  ValueSet* state_for      (BlockBegin* block)    { return _block_states[block->block_id()]; }
  void      set_state_for  (BlockBegin* block, ValueSet* stack) { _block_states[block->block_id()] = stack; }
  // Returns true if caused a change in the block's state.
  bool      merge_state_for(BlockBegin* block,
                            ValueSet*   incoming_state);

 public:
  // constructor
  NullCheckEliminator(Optimizer* opt)
    : _opt(opt)
    , _set(new ValueSet())
    , _last_explicit_null_check(NULL)
    , _block_states(BlockBegin::number_of_blocks(), NULL)
    , _work_list(new BlockList()) {
    _visitable_instructions = new ValueSet();
    _visitor.set_eliminator(this);
  }

  Optimizer*  opt()                               { return _opt; }
  IR*         ir ()                               { return opt()->ir(); }

  // Process a graph
  void iterate(BlockBegin* root);

  // In some situations (like NullCheck(x); getfield(x)) the debug
  // information from the explicit NullCheck can be used to populate
  // the getfield, even if the two instructions are in different
  // scopes; this allows implicit null checks to be used but the
  // correct exception information to be generated. We must clear the
  // last-traversed NullCheck when we reach a potentially-exception-
  // throwing instruction, as well as in some other cases.
  void        set_last_explicit_null_check(NullCheck* check) { _last_explicit_null_check = check; }
  NullCheck*  last_explicit_null_check()                     { return _last_explicit_null_check; }
  Value       last_explicit_null_check_obj()                 { return (_last_explicit_null_check
                                                                         ? _last_explicit_null_check->obj()
                                                                         : NULL); }
  NullCheck*  consume_last_explicit_null_check() {
    _last_explicit_null_check->unpin(Instruction::PinExplicitNullCheck);
    _last_explicit_null_check->set_can_trap(false);
    return _last_explicit_null_check;
  }
  void        clear_last_explicit_null_check()               { _last_explicit_null_check = NULL; }

  // Handlers for relevant instructions
  // (separated out from NullCheckVisitor for clarity)

  // The basic contract is that these must leave the instruction in
  // the desired state; must not assume anything about the state of
  // the instruction. We make multiple passes over some basic blocks
  // and the last pass is the only one whose result is valid.
  void handle_AccessField     (AccessField* x);
  void handle_ArrayLength     (ArrayLength* x);
  void handle_LoadIndexed     (LoadIndexed* x);
  void handle_StoreIndexed    (StoreIndexed* x);
  void handle_NullCheck       (NullCheck* x);
  void handle_Invoke          (Invoke* x);
  void handle_NewInstance     (NewInstance* x);
  void handle_NewArray        (NewArray* x);
  void handle_AccessMonitor   (AccessMonitor* x);
  void handle_Intrinsic       (Intrinsic* x);
  void handle_ExceptionObject (ExceptionObject* x);
  void handle_Phi             (Phi* x);
};


// NEEDS_CLEANUP
// There may be other instructions which need to clear the last
// explicit null check. Anything across which we can not hoist the
// debug information for a NullCheck instruction must clear it. It
// might be safer to pattern match "NullCheck ; {AccessField,
// ArrayLength, LoadIndexed}" but it is more easily structured this way.
// Should test to see performance hit of clearing it for all handlers
// with empty bodies below. If it is negligible then we should leave
// that in for safety, otherwise should think more about it.
void NullCheckVisitor::do_Phi            (Phi*             x) { nce()->handle_Phi(x);      }
void NullCheckVisitor::do_Local          (Local*           x) {}
void NullCheckVisitor::do_Constant       (Constant*        x) { /* FIXME: handle object constants */ }
void NullCheckVisitor::do_LoadField      (LoadField*       x) { nce()->handle_AccessField(x); }
void NullCheckVisitor::do_StoreField     (StoreField*      x) { nce()->handle_AccessField(x); }
void NullCheckVisitor::do_ArrayLength    (ArrayLength*     x) { nce()->handle_ArrayLength(x); }
void NullCheckVisitor::do_LoadIndexed    (LoadIndexed*     x) { nce()->handle_LoadIndexed(x); }
void NullCheckVisitor::do_StoreIndexed   (StoreIndexed*    x) { nce()->handle_StoreIndexed(x); }
void NullCheckVisitor::do_NegateOp       (NegateOp*        x) {}
void NullCheckVisitor::do_ArithmeticOp   (ArithmeticOp*    x) { if (x->can_trap()) nce()->clear_last_explicit_null_check(); }
void NullCheckVisitor::do_ShiftOp        (ShiftOp*         x) {}
void NullCheckVisitor::do_LogicOp        (LogicOp*         x) {}
void NullCheckVisitor::do_CompareOp      (CompareOp*       x) {}
void NullCheckVisitor::do_IfOp           (IfOp*            x) {}
void NullCheckVisitor::do_Convert        (Convert*         x) {}
void NullCheckVisitor::do_NullCheck      (NullCheck*       x) { nce()->handle_NullCheck(x); }
void NullCheckVisitor::do_Invoke         (Invoke*          x) { nce()->handle_Invoke(x); }
void NullCheckVisitor::do_NewInstance    (NewInstance*     x) { nce()->handle_NewInstance(x); }
void NullCheckVisitor::do_NewTypeArray   (NewTypeArray*    x) { nce()->handle_NewArray(x); }
void NullCheckVisitor::do_NewObjectArray (NewObjectArray*  x) { nce()->handle_NewArray(x); }
void NullCheckVisitor::do_NewMultiArray  (NewMultiArray*   x) { nce()->handle_NewArray(x); }
void NullCheckVisitor::do_CheckCast      (CheckCast*       x) {}
void NullCheckVisitor::do_InstanceOf     (InstanceOf*      x) {}
void NullCheckVisitor::do_MonitorEnter   (MonitorEnter*    x) { nce()->handle_AccessMonitor(x); }
void NullCheckVisitor::do_MonitorExit    (MonitorExit*     x) { nce()->handle_AccessMonitor(x); }
void NullCheckVisitor::do_Intrinsic      (Intrinsic*       x) { nce()->clear_last_explicit_null_check(); }
void NullCheckVisitor::do_BlockBegin     (BlockBegin*      x) {}
void NullCheckVisitor::do_Goto           (Goto*            x) {}
void NullCheckVisitor::do_If             (If*              x) {}
void NullCheckVisitor::do_IfInstanceOf   (IfInstanceOf*    x) {}
void NullCheckVisitor::do_TableSwitch    (TableSwitch*     x) {}
void NullCheckVisitor::do_LookupSwitch   (LookupSwitch*    x) {}
void NullCheckVisitor::do_Return         (Return*          x) {}
void NullCheckVisitor::do_Throw          (Throw*           x) { nce()->clear_last_explicit_null_check(); }
void NullCheckVisitor::do_Base           (Base*            x) {}
void NullCheckVisitor::do_OsrEntry       (OsrEntry*        x) {}
void NullCheckVisitor::do_ExceptionObject(ExceptionObject* x) { nce()->handle_ExceptionObject(x); }
void NullCheckVisitor::do_RoundFP        (RoundFP*         x) {}
void NullCheckVisitor::do_UnsafeGetRaw   (UnsafeGetRaw*    x) {}
void NullCheckVisitor::do_UnsafePutRaw   (UnsafePutRaw*    x) {}
void NullCheckVisitor::do_UnsafeGetObject(UnsafeGetObject* x) {}
void NullCheckVisitor::do_UnsafePutObject(UnsafePutObject* x) {}
void NullCheckVisitor::do_UnsafePrefetchRead (UnsafePrefetchRead*  x) {}
void NullCheckVisitor::do_UnsafePrefetchWrite(UnsafePrefetchWrite* x) {}
void NullCheckVisitor::do_ProfileCall    (ProfileCall*     x) { nce()->clear_last_explicit_null_check(); }
void NullCheckVisitor::do_ProfileCounter (ProfileCounter*  x) {}


NullCheckEliminator* NullCheckEliminator::_static_nce = NULL;


void NullCheckEliminator::do_value(Value* p) {
  assert(*p != NULL, "should not find NULL instructions");
  if (_static_nce->visitable(*p)) {
    _static_nce->mark_visited(*p);
    (*p)->visit(&_static_nce->_visitor);
  }
}

bool NullCheckEliminator::merge_state_for(BlockBegin* block, ValueSet* incoming_state) {
  ValueSet* state = state_for(block);
  if (state == NULL) {
    state = incoming_state->copy();
    set_state_for(block, state);
    return true;
  } else {
    bool changed = state->set_intersect(incoming_state);
    if (PrintNullCheckElimination && changed) {
      tty->print_cr("Block %d's null check state changed", block->block_id());
    }
    return changed;
  }
}


void NullCheckEliminator::iterate_all() {
  while (work_list()->length() > 0) {
    iterate_one(work_list()->pop());
  }
}


void NullCheckEliminator::iterate_one(BlockBegin* block) {
  _static_nce = this;
  clear_visitable_state();
  // clear out an old explicit null checks
  set_last_explicit_null_check(NULL);

  if (PrintNullCheckElimination) {
    tty->print_cr(" ...iterating block %d in null check elimination for %s::%s%s",
                  block->block_id(),
                  ir()->method()->holder()->name()->as_utf8(),
                  ir()->method()->name()->as_utf8(),
                  ir()->method()->signature()->as_symbol()->as_utf8());
  }

  // Create new state if none present (only happens at root)
  if (state_for(block) == NULL) {
    ValueSet* tmp_state = new ValueSet();
    set_state_for(block, tmp_state);
    // Initial state is that local 0 (receiver) is non-null for
    // non-static methods
    ValueStack* stack  = block->state();
    IRScope*    scope  = stack->scope();
    ciMethod*   method = scope->method();
    if (!method->is_static()) {
      Local* local0 = stack->local_at(0)->as_Local();
      assert(local0 != NULL, "must be");
      assert(local0->type() == objectType, "invalid type of receiver");

      if (local0 != NULL) {
        // Local 0 is used in this scope
        tmp_state->put(local0);
        if (PrintNullCheckElimination) {
          tty->print_cr("Local 0 (value %d) proven non-null upon entry", local0->id());
        }
      }
    }
  }

  // Must copy block's state to avoid mutating it during iteration
  // through the block -- otherwise "not-null" states can accidentally
  // propagate "up" through the block during processing of backward
  // branches and algorithm is incorrect (and does not converge)
  set_state_from(state_for(block));

  // allow visiting of Phis belonging to this block
  for_each_phi_fun(block, phi,
                   mark_visitable(phi);
                   );

  BlockEnd* e = block->end();
  assert(e != NULL, "incomplete graph");
  int i;

  // Propagate the state before this block into the exception
  // handlers.  They aren't true successors since we aren't guaranteed
  // to execute the whole block before executing them.  Also putting
  // them on first seems to help reduce the amount of iteration to
  // reach a fixed point.
  for (i = 0; i < block->number_of_exception_handlers(); i++) {
    BlockBegin* next = block->exception_handler_at(i);
    if (merge_state_for(next, state())) {
      if (!work_list()->contains(next)) {
        work_list()->push(next);
      }
    }
  }

  // Iterate through block, updating state.
  for (Instruction* instr = block; instr != NULL; instr = instr->next()) {
    // Mark instructions in this block as visitable as they are seen
    // in the instruction list.  This keeps the iteration from
    // visiting instructions which are references in other blocks or
    // visiting instructions more than once.
    mark_visitable(instr);
    if (instr->is_root() || instr->can_trap() || (instr->as_NullCheck() != NULL)) {
      mark_visited(instr);
      instr->input_values_do(&NullCheckEliminator::do_value);
      instr->visit(&_visitor);
    }
  }

  // Propagate state to successors if necessary
  for (i = 0; i < e->number_of_sux(); i++) {
    BlockBegin* next = e->sux_at(i);
    if (merge_state_for(next, state())) {
      if (!work_list()->contains(next)) {
        work_list()->push(next);
      }
    }
  }
}


void NullCheckEliminator::iterate(BlockBegin* block) {
  work_list()->push(block);
  iterate_all();
}

void NullCheckEliminator::handle_AccessField(AccessField* x) {
  if (x->is_static()) {
    if (x->as_LoadField() != NULL) {
      // If the field is a non-null static final object field (as is
      // often the case for sun.misc.Unsafe), put this LoadField into
      // the non-null map
      ciField* field = x->field();
      if (field->is_constant()) {
        ciConstant field_val = field->constant_value();
        BasicType field_type = field_val.basic_type();
        if (field_type == T_OBJECT || field_type == T_ARRAY) {
          ciObject* obj_val = field_val.as_object();
          if (!obj_val->is_null_object()) {
            if (PrintNullCheckElimination) {
              tty->print_cr("AccessField %d proven non-null by static final non-null oop check",
                            x->id());
            }
            set_put(x);
          }
        }
      }
    }
    // Be conservative
    clear_last_explicit_null_check();
    return;
  }

  Value obj = x->obj();
  if (set_contains(obj)) {
    // Value is non-null => update AccessField
    if (last_explicit_null_check_obj() == obj && !x->needs_patching()) {
      x->set_explicit_null_check(consume_last_explicit_null_check());
      x->set_needs_null_check(true);
      if (PrintNullCheckElimination) {
        tty->print_cr("Folded NullCheck %d into AccessField %d's null check for value %d",
                      x->explicit_null_check()->id(), x->id(), obj->id());
      }
    } else {
      x->set_explicit_null_check(NULL);
      x->set_needs_null_check(false);
      if (PrintNullCheckElimination) {
        tty->print_cr("Eliminated AccessField %d's null check for value %d", x->id(), obj->id());
      }
    }
  } else {
    set_put(obj);
    if (PrintNullCheckElimination) {
      tty->print_cr("AccessField %d of value %d proves value to be non-null", x->id(), obj->id());
    }
    // Ensure previous passes do not cause wrong state
    x->set_needs_null_check(true);
    x->set_explicit_null_check(NULL);
  }
  clear_last_explicit_null_check();
}


void NullCheckEliminator::handle_ArrayLength(ArrayLength* x) {
  Value array = x->array();
  if (set_contains(array)) {
    // Value is non-null => update AccessArray
    if (last_explicit_null_check_obj() == array) {
      x->set_explicit_null_check(consume_last_explicit_null_check());
      x->set_needs_null_check(true);
      if (PrintNullCheckElimination) {
        tty->print_cr("Folded NullCheck %d into ArrayLength %d's null check for value %d",
                      x->explicit_null_check()->id(), x->id(), array->id());
      }
    } else {
      x->set_explicit_null_check(NULL);
      x->set_needs_null_check(false);
      if (PrintNullCheckElimination) {
        tty->print_cr("Eliminated ArrayLength %d's null check for value %d", x->id(), array->id());
      }
    }
  } else {
    set_put(array);
    if (PrintNullCheckElimination) {
      tty->print_cr("ArrayLength %d of value %d proves value to be non-null", x->id(), array->id());
    }
    // Ensure previous passes do not cause wrong state
    x->set_needs_null_check(true);
    x->set_explicit_null_check(NULL);
  }
  clear_last_explicit_null_check();
}


void NullCheckEliminator::handle_LoadIndexed(LoadIndexed* x) {
  Value array = x->array();
  if (set_contains(array)) {
    // Value is non-null => update AccessArray
    if (last_explicit_null_check_obj() == array) {
      x->set_explicit_null_check(consume_last_explicit_null_check());
      x->set_needs_null_check(true);
      if (PrintNullCheckElimination) {
        tty->print_cr("Folded NullCheck %d into LoadIndexed %d's null check for value %d",
                      x->explicit_null_check()->id(), x->id(), array->id());
      }
    } else {
      x->set_explicit_null_check(NULL);
      x->set_needs_null_check(false);
      if (PrintNullCheckElimination) {
        tty->print_cr("Eliminated LoadIndexed %d's null check for value %d", x->id(), array->id());
      }
    }
  } else {
    set_put(array);
    if (PrintNullCheckElimination) {
      tty->print_cr("LoadIndexed %d of value %d proves value to be non-null", x->id(), array->id());
    }
    // Ensure previous passes do not cause wrong state
    x->set_needs_null_check(true);
    x->set_explicit_null_check(NULL);
  }
  clear_last_explicit_null_check();
}


void NullCheckEliminator::handle_StoreIndexed(StoreIndexed* x) {
  Value array = x->array();
  if (set_contains(array)) {
    // Value is non-null => update AccessArray
    if (PrintNullCheckElimination) {
      tty->print_cr("Eliminated StoreIndexed %d's null check for value %d", x->id(), array->id());
    }
    x->set_needs_null_check(false);
  } else {
    set_put(array);
    if (PrintNullCheckElimination) {
      tty->print_cr("StoreIndexed %d of value %d proves value to be non-null", x->id(), array->id());
    }
    // Ensure previous passes do not cause wrong state
    x->set_needs_null_check(true);
  }
  clear_last_explicit_null_check();
}


void NullCheckEliminator::handle_NullCheck(NullCheck* x) {
  Value obj = x->obj();
  if (set_contains(obj)) {
    // Already proven to be non-null => this NullCheck is useless
    if (PrintNullCheckElimination) {
      tty->print_cr("Eliminated NullCheck %d for value %d", x->id(), obj->id());
    }
    // Don't unpin since that may shrink obj's live range and make it unavailable for debug info.
    // The code generator won't emit LIR for a NullCheck that cannot trap.
    x->set_can_trap(false);
  } else {
    // May be null => add to map and set last explicit NullCheck
    x->set_can_trap(true);
    // make sure it's pinned if it can trap
    x->pin(Instruction::PinExplicitNullCheck);
    set_put(obj);
    set_last_explicit_null_check(x);
    if (PrintNullCheckElimination) {
      tty->print_cr("NullCheck %d of value %d proves value to be non-null", x->id(), obj->id());
    }
  }
}


void NullCheckEliminator::handle_Invoke(Invoke* x) {
  if (!x->has_receiver()) {
    // Be conservative
    clear_last_explicit_null_check();
    return;
  }

  Value recv = x->receiver();
  if (!set_contains(recv)) {
    set_put(recv);
    if (PrintNullCheckElimination) {
      tty->print_cr("Invoke %d of value %d proves value to be non-null", x->id(), recv->id());
    }
  }
  clear_last_explicit_null_check();
}


void NullCheckEliminator::handle_NewInstance(NewInstance* x) {
  set_put(x);
  if (PrintNullCheckElimination) {
    tty->print_cr("NewInstance %d is non-null", x->id());
  }
}


void NullCheckEliminator::handle_NewArray(NewArray* x) {
  set_put(x);
  if (PrintNullCheckElimination) {
    tty->print_cr("NewArray %d is non-null", x->id());
  }
}


void NullCheckEliminator::handle_ExceptionObject(ExceptionObject* x) {
  set_put(x);
  if (PrintNullCheckElimination) {
    tty->print_cr("ExceptionObject %d is non-null", x->id());
  }
}


void NullCheckEliminator::handle_AccessMonitor(AccessMonitor* x) {
  Value obj = x->obj();
  if (set_contains(obj)) {
    // Value is non-null => update AccessMonitor
    if (PrintNullCheckElimination) {
      tty->print_cr("Eliminated AccessMonitor %d's null check for value %d", x->id(), obj->id());
    }
    x->set_needs_null_check(false);
  } else {
    set_put(obj);
    if (PrintNullCheckElimination) {
      tty->print_cr("AccessMonitor %d of value %d proves value to be non-null", x->id(), obj->id());
    }
    // Ensure previous passes do not cause wrong state
    x->set_needs_null_check(true);
  }
  clear_last_explicit_null_check();
}


void NullCheckEliminator::handle_Intrinsic(Intrinsic* x) {
  if (!x->has_receiver()) {
    // Be conservative
    clear_last_explicit_null_check();
    return;
  }

  Value recv = x->receiver();
  if (set_contains(recv)) {
    // Value is non-null => update Intrinsic
    if (PrintNullCheckElimination) {
      tty->print_cr("Eliminated Intrinsic %d's null check for value %d", x->id(), recv->id());
    }
    x->set_needs_null_check(false);
  } else {
    set_put(recv);
    if (PrintNullCheckElimination) {
      tty->print_cr("Intrinsic %d of value %d proves value to be non-null", x->id(), recv->id());
    }
    // Ensure previous passes do not cause wrong state
    x->set_needs_null_check(true);
  }
  clear_last_explicit_null_check();
}


void NullCheckEliminator::handle_Phi(Phi* x) {
  int i;
  bool all_non_null = true;
  if (x->is_illegal()) {
    all_non_null = false;
  } else {
    for (i = 0; i < x->operand_count(); i++) {
      Value input = x->operand_at(i);
      if (!set_contains(input)) {
        all_non_null = false;
      }
    }
  }

  if (all_non_null) {
    // Value is non-null => update Phi
    if (PrintNullCheckElimination) {
      tty->print_cr("Eliminated Phi %d's null check for phifun because all inputs are non-null", x->id());
    }
    x->set_needs_null_check(false);
  } else if (set_contains(x)) {
    set_remove(x);
  }
}


void Optimizer::eliminate_null_checks() {
  ResourceMark rm;

  NullCheckEliminator nce(this);

  if (PrintNullCheckElimination) {
    tty->print_cr("Starting null check elimination for method %s::%s%s",
                  ir()->method()->holder()->name()->as_utf8(),
                  ir()->method()->name()->as_utf8(),
                  ir()->method()->signature()->as_symbol()->as_utf8());
  }

  // Apply to graph
  nce.iterate(ir()->start());

  // walk over the graph looking for exception
  // handlers and iterate over them as well
  int nblocks = BlockBegin::number_of_blocks();
  BlockList blocks(nblocks);
  boolArray visited_block(nblocks, false);

  blocks.push(ir()->start());
  visited_block[ir()->start()->block_id()] = true;
  for (int i = 0; i < blocks.length(); i++) {
    BlockBegin* b = blocks[i];
    // exception handlers need to be treated as additional roots
    for (int e = b->number_of_exception_handlers(); e-- > 0; ) {
      BlockBegin* excp = b->exception_handler_at(e);
      int id = excp->block_id();
      if (!visited_block[id]) {
        blocks.push(excp);
        visited_block[id] = true;
        nce.iterate(excp);
      }
    }
    // traverse successors
    BlockEnd *end = b->end();
    for (int s = end->number_of_sux(); s-- > 0; ) {
      BlockBegin* next = end->sux_at(s);
      int id = next->block_id();
      if (!visited_block[id]) {
        blocks.push(next);
        visited_block[id] = true;
      }
    }
  }


  if (PrintNullCheckElimination) {
    tty->print_cr("Done with null check elimination for method %s::%s%s",
                  ir()->method()->holder()->name()->as_utf8(),
                  ir()->method()->name()->as_utf8(),
                  ir()->method()->signature()->as_symbol()->as_utf8());
  }
}
