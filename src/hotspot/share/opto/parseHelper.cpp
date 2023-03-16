/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "ci/ciSymbols.hpp"
#include "compiler/compileLog.hpp"
#include "oops/objArrayKlass.hpp"
#include "opto/addnode.hpp"
#include "opto/memnode.hpp"
#include "opto/mulnode.hpp"
#include "opto/parse.hpp"
#include "opto/rootnode.hpp"
#include "opto/runtime.hpp"
#include "runtime/sharedRuntime.hpp"

//------------------------------make_dtrace_method_entry_exit ----------------
// Dtrace -- record entry or exit of a method if compiled with dtrace support
void GraphKit::make_dtrace_method_entry_exit(ciMethod* method, bool is_entry) {
  const TypeFunc *call_type    = OptoRuntime::dtrace_method_entry_exit_Type();
  address         call_address = is_entry ? CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_entry) :
                                            CAST_FROM_FN_PTR(address, SharedRuntime::dtrace_method_exit);
  const char     *call_name    = is_entry ? "dtrace_method_entry" : "dtrace_method_exit";

  // Get base of thread-local storage area
  Node* thread = _gvn.transform( new ThreadLocalNode() );

  // Get method
  const TypePtr* method_type = TypeMetadataPtr::make(method);
  Node *method_node = _gvn.transform(ConNode::make(method_type));

  kill_dead_locals();

  // For some reason, this call reads only raw memory.
  const TypePtr* raw_adr_type = TypeRawPtr::BOTTOM;
  make_runtime_call(RC_LEAF | RC_NARROW_MEM,
                    call_type, call_address,
                    call_name, raw_adr_type,
                    thread, method_node);
}


//=============================================================================
//------------------------------do_checkcast-----------------------------------
void Parse::do_checkcast() {
  bool will_link;
  ciKlass* klass = iter().get_klass(will_link);

  Node *obj = peek();

  // Throw uncommon trap if class is not loaded or the value we are casting
  // _from_ is not loaded, and value is not null.  If the value _is_ null,
  // then the checkcast does nothing.
  const TypeOopPtr *tp = _gvn.type(obj)->isa_oopptr();
  if (!will_link || (tp && !tp->is_loaded())) {
    if (C->log() != nullptr) {
      if (!will_link) {
        C->log()->elem("assert_null reason='checkcast' klass='%d'",
                       C->log()->identify(klass));
      }
      if (tp && !tp->is_loaded()) {
        // %%% Cannot happen?
        ciKlass* klass = tp->unloaded_klass();
        C->log()->elem("assert_null reason='checkcast source' klass='%d'",
                       C->log()->identify(klass));
      }
    }
    null_assert(obj);
    assert( stopped() || _gvn.type(peek())->higher_equal(TypePtr::NULL_PTR), "what's left behind is null" );
    return;
  }

  Node* res = gen_checkcast(obj, makecon(TypeKlassPtr::make(klass, Type::trust_interfaces)));
  if (stopped()) {
    return;
  }

  // Pop from stack AFTER gen_checkcast because it can uncommon trap and
  // the debug info has to be correct.
  pop();
  push(res);
}


//------------------------------do_instanceof----------------------------------
void Parse::do_instanceof() {
  if (stopped())  return;
  // We would like to return false if class is not loaded, emitting a
  // dependency, but Java requires instanceof to load its operand.

  // Throw uncommon trap if class is not loaded
  bool will_link;
  ciKlass* klass = iter().get_klass(will_link);

  if (!will_link) {
    if (C->log() != nullptr) {
      C->log()->elem("assert_null reason='instanceof' klass='%d'",
                     C->log()->identify(klass));
    }
    null_assert(peek());
    assert( stopped() || _gvn.type(peek())->higher_equal(TypePtr::NULL_PTR), "what's left behind is null" );
    if (!stopped()) {
      // The object is now known to be null.
      // Shortcut the effect of gen_instanceof and return "false" directly.
      pop();                   // pop the null
      push(_gvn.intcon(0));    // push false answer
    }
    return;
  }

  // Push the bool result back on stack
  Node* res = gen_instanceof(peek(), makecon(TypeKlassPtr::make(klass, Type::trust_interfaces)), true);

  // Pop from stack AFTER gen_instanceof because it can uncommon trap.
  pop();
  push(res);
}

//------------------------------array_store_check------------------------------
// pull array from stack and check that the store is valid
void Parse::array_store_check() {

  // Shorthand access to array store elements without popping them.
  Node *obj = peek(0);
  Node *idx = peek(1);
  Node *ary = peek(2);

  if (_gvn.type(obj) == TypePtr::NULL_PTR) {
    // There's never a type check on null values.
    // This cutout lets us avoid the uncommon_trap(Reason_array_check)
    // below, which turns into a performance liability if the
    // gen_checkcast folds up completely.
    return;
  }

  // Extract the array klass type
  int klass_offset = oopDesc::klass_offset_in_bytes();
  Node* p = basic_plus_adr( ary, ary, klass_offset );
  // p's type is array-of-OOPS plus klass_offset
  Node* array_klass = _gvn.transform(LoadKlassNode::make(_gvn, nullptr, immutable_memory(), p, TypeInstPtr::KLASS));
  // Get the array klass
  const TypeKlassPtr *tak = _gvn.type(array_klass)->is_klassptr();

  // The type of array_klass is usually INexact array-of-oop.  Heroically
  // cast array_klass to EXACT array and uncommon-trap if the cast fails.
  // Make constant out of the inexact array klass, but use it only if the cast
  // succeeds.
  bool always_see_exact_class = false;
  if (MonomorphicArrayCheck
      && !too_many_traps(Deoptimization::Reason_array_check)
      && !tak->klass_is_exact()
      && tak != TypeInstKlassPtr::OBJECT) {
      // Regarding the fourth condition in the if-statement from above:
      //
      // If the compiler has determined that the type of array 'ary' (represented
      // by 'array_klass') is java/lang/Object, the compiler must not assume that
      // the array 'ary' is monomorphic.
      //
      // If 'ary' were of type java/lang/Object, this arraystore would have to fail,
      // because it is not possible to perform a arraystore into an object that is not
      // a "proper" array.
      //
      // Therefore, let's obtain at runtime the type of 'ary' and check if we can still
      // successfully perform the store.
      //
      // The implementation reasons for the condition are the following:
      //
      // java/lang/Object is the superclass of all arrays, but it is represented by the VM
      // as an InstanceKlass. The checks generated by gen_checkcast() (see below) expect
      // 'array_klass' to be ObjArrayKlass, which can result in invalid memory accesses.
      //
      // See issue JDK-8057622 for details.

    always_see_exact_class = true;
    // (If no MDO at all, hope for the best, until a trap actually occurs.)

    // Make a constant out of the inexact array klass
    const TypeKlassPtr *extak = tak->cast_to_exactness(true);

    if (extak->exact_klass(true) != nullptr) {
      Node* con = makecon(extak);
      Node* cmp = _gvn.transform(new CmpPNode( array_klass, con ));
      Node* bol = _gvn.transform(new BoolNode( cmp, BoolTest::eq ));
      Node* ctrl= control();
      { BuildCutout unless(this, bol, PROB_MAX);
        uncommon_trap(Deoptimization::Reason_array_check,
                      Deoptimization::Action_maybe_recompile,
                      extak->exact_klass());
      }
      if (stopped()) {          // MUST uncommon-trap?
        set_control(ctrl);      // Then Don't Do It, just fall into the normal checking
      } else {                  // Cast array klass to exactness:
        // Use the exact constant value we know it is.
        replace_in_map(array_klass,con);
        CompileLog* log = C->log();
        if (log != nullptr) {
          log->elem("cast_up reason='monomorphic_array' from='%d' to='(exact)'",
                    log->identify(extak->exact_klass()));
        }
        array_klass = con;      // Use cast value moving forward
      }
    }
  }

  // Come here for polymorphic array klasses

  // Extract the array element class
  int element_klass_offset = in_bytes(ObjArrayKlass::element_klass_offset());
  Node *p2 = basic_plus_adr(array_klass, array_klass, element_klass_offset);
  // We are allowed to use the constant type only if cast succeeded. If always_see_exact_class is true,
  // we must set a control edge from the IfTrue node created by the uncommon_trap above to the
  // LoadKlassNode.
  Node* a_e_klass = _gvn.transform(LoadKlassNode::make(_gvn, always_see_exact_class ? control() : nullptr,
                                                       immutable_memory(), p2, tak));

  // Check (the hard way) and throw if not a subklass.
  // Result is ignored, we just need the CFG effects.
  gen_checkcast(obj, a_e_klass);
}


//------------------------------do_new-----------------------------------------
void Parse::do_new() {
  kill_dead_locals();

  bool will_link;
  ciInstanceKlass* klass = iter().get_klass(will_link)->as_instance_klass();
  assert(will_link, "_new: typeflow responsibility");

  // Should throw an InstantiationError?
  if (klass->is_abstract() || klass->is_interface() ||
      klass->name() == ciSymbols::java_lang_Class() ||
      iter().is_unresolved_klass()) {
    uncommon_trap(Deoptimization::Reason_unhandled,
                  Deoptimization::Action_none,
                  klass);
    return;
  }

  if (C->needs_clinit_barrier(klass, method())) {
    clinit_barrier(klass, method());
    if (stopped())  return;
  }

  Node* kls = makecon(TypeKlassPtr::make(klass));
  Node* obj = new_instance(kls);

  // Push resultant oop onto stack
  push(obj);

  // Keep track of whether opportunities exist for StringBuilder
  // optimizations.
  if (OptimizeStringConcat &&
      (klass == C->env()->StringBuilder_klass() ||
       klass == C->env()->StringBuffer_klass())) {
    C->set_has_stringbuilder(true);
  }

  // Keep track of boxed values for EliminateAutoBox optimizations.
  if (C->eliminate_boxing() && klass->is_box_klass()) {
    C->set_has_boxed_value(true);
  }

  if (DoPartialEscapeAnalysis) {
    // obj is a CheckCastPP Node, aka. cooked oop.
    jvms()->alloc_state().add_new_allocation(obj);
  }
}

#ifndef PRODUCT
//------------------------------dump_map_adr_mem-------------------------------
// Debug dump of the mapping from address types to MergeMemNode indices.
void Parse::dump_map_adr_mem() const {
  tty->print_cr("--- Mapping from address types to memory Nodes ---");
  MergeMemNode *mem = map() == nullptr ? nullptr : (map()->memory()->is_MergeMem() ?
                                      map()->memory()->as_MergeMem() : nullptr);
  for (uint i = 0; i < (uint)C->num_alias_types(); i++) {
    C->alias_type(i)->print_on(tty);
    tty->print("\t");
    // Node mapping, if any
    if (mem && i < mem->req() && mem->in(i) && mem->in(i) != mem->empty_memory()) {
      mem->in(i)->dump();
    } else {
      tty->cr();
    }
  }
}

#endif

//
// Partial Escape Analysis
// Stadler, Lukas, Thomas Würthinger, and Hanspeter Mössenböck. "Partial escape analysis and scalar replacement for Java."
//
// Our adaption to C2.
// https://gist.github.com/navyxliu/62a510a5c6b0245164569745d758935b
//
VirtualState::VirtualState(int nfields): _lockCount(0), _nfields(nfields) {
  Compile* C = Compile::current();
  _entries = NEW_ARENA_ARRAY(C->parser_arena(), Node*, nfields);
  // only track explicit stores.
  // see IntializeNode semantics in memnode.cpp
  for (int i = 0; i < nfields; ++i) {
    _entries[i] = nullptr;
  }
}

// do NOT call base's copy constructor. we would like to reset refcnt!
VirtualState::VirtualState(const VirtualState& other) {
  _lockCount = other._lockCount;
  _nfields   = other._nfields;
  _entries = NEW_ARENA_ARRAY(Compile::current()->parser_arena(), Node*, _nfields);

  Node** dst = _entries;
  Node** src = other._entries;
  int sz = _nfields;
  while (sz-- > 0) {
    *dst++ = *src++;
  }
}

void VirtualState::set_field(int idx, Node* val) {
  assert(idx >= 0 && idx < _nfields, "sanity check");
  _entries[idx] = val;
}

ObjectState& VirtualState::merge(ObjectState& newin, GraphKit* kit, const TypeOopPtr* oop_type,
                                 RegionNode* r, int pnum) {
  assert(newin.is_virtual(), "only support VirtualState");
  VirtualState* vs = static_cast<VirtualState*>(&newin);

  if (this != vs) {
    ciInstanceKlass* ik = oop_type->is_instptr()->instance_klass();
    assert(_nfields == ik->nof_nonstatic_fields(), "_nfields should be consistent with instanceKlass");

    for (int i = 0; i < _nfields; ++i) {
      Node* m = _entries[i];

      if (m != vs->_entries[i]) {
        ciField* field = ik->nonstatic_field_at(i);
        BasicType bt = field->layout_type();
        const Type* type = Type::get_const_basic_type(bt);

        if (m == nullptr || !m->is_Phi()) {
          if (m == nullptr) {
            m = kit->zerocon(bt);
          }
          m = PhiNode::make(r, m, type);
          kit->gvn().set_type(m, type);
          _entries[i] = m;
        }

        Node* n = vs->_entries[i];
        if (n == nullptr) {
          n = kit->zerocon(bt);
        }
        m->set_req(pnum, n);
        if (pnum == 1) {
          _entries[i] = kit->gvn().transform(m);
        }
      }
    }
  }

  return *this;
}

#ifndef PRODUCT
void VirtualState::print_on(outputStream* os) const {
  os->print_cr("Virt = %p", this);

  for (int i = 0; i < _nfields; ++i) {
    Node* val = _entries[i];
    os->print("#%d: ", i);
    if (val != nullptr) {
      val->dump();
    } else {
      os->print_cr("_");
    }
  }
}
#endif

void PEAState::add_new_allocation(Node* obj) {
  int nfields;
  const TypeOopPtr* oop_type = obj->as_Type()->type()->is_oopptr();

  if (oop_type->isa_aryptr()) {
    const TypeAryPtr* ary_type = oop_type->is_aryptr();
    const TypeInt* size = ary_type->size();
    if (size->is_con() && size->get_con() <= EliminateAllocationArraySizeLimit) {
      nfields = size->get_con();
    } else {
      // length of array is too long or unknown
      return;
    }
  } else {
    const TypeInstPtr* inst_type = oop_type->is_instptr();
    nfields = inst_type->instance_klass()->nof_nonstatic_fields();
  }

  if (nfields >= 0) {
    AllocateNode* alloc = obj->in(1)->in(0)->as_Allocate();
#ifndef PRODUCT
    // node_idx_t is unsigned. Use static_cast<> here to avoid comparison between signed and unsigned.
    if (PEA_debug_idx > 0 && alloc->_idx != static_cast<node_idx_t>(PEA_debug_idx)) {         // only allow PEA_debug_idx
      return;
    } else if (PEA_debug_idx < 0 && alloc->_idx == static_cast<node_idx_t>(-PEA_debug_idx)) { // block PEA_debug_idx
      return;
    }
#endif
    bool result = _state.put(alloc, new VirtualState(nfields));
    assert(result, "the key existed in _state");
    add_alias(alloc, obj);
  }
}

PEAState& PEAState::operator=(const PEAState& init) {
  if (this != &init) {
    _state.unlink_all();
    _alias.unlink_all();

    init._state.iterate([&](ObjID key, ObjectState* value) {
      _state.put(key, value->clone());
      return true;
    });

    init._alias.iterate([&](Node* key, ObjID id) {
      add_alias(id, key);
      return true;
    });
  }

#ifdef ASSERT
    validate();
#endif
  return *this;
}

void PEAState::remove_alias(ObjID id, Node* var) {
  assert(contains(id), "sanity check");
  assert(_alias.contains(var) && (*_alias.get(var)) == id, "sanity check");
  _alias.remove(var);

  if (!get_object_state(id)->ref_dec()) {
    _state.remove(id);
  }
}

// Inspired by GraphKit::replace_in_map. Besides the replacement of old object
// we also need to scout map() and find loaded fields of old object. they may
// lay in stack, locals or even argument section.
static void replace_in_map(GraphKit* kit, Node* old, Node* neww) {
  PhaseGVN& gvn = kit->gvn();
  SafePointNode* map = kit->jvms()->map();

  for (uint i = 0; i < map->req(); ++i) {
    Node* x = map->in(i);

    if (x == old) {
      map->set_req(i, neww); // safepointNode is not hashashable.
      map->record_replaced_node(old, neww); // flush to caller.
    } else {
      if (x->is_DecodeN()) {
        x = x->in(1);
        assert(x->Opcode() == Op_LoadN, "sanity check");
      }

      if (x->is_Load()) {
        Node* addr = x->in(MemNode::Address);
        Node_List stack(4);

        while (addr->is_AddP() && addr->in(AddPNode::Base) == old) {
          stack.push(addr);
          addr = addr->in(AddPNode::Address);
        }

        if (stack.size() > 0) {
          Node* prev = neww;
          do {
            addr = stack.pop();
            prev = gvn.transform(new AddPNode(neww, prev, addr->in(AddPNode::Offset)));
          } while (stack.size() > 0);

          bool is_in_table = gvn.hash_delete(x);
          x->set_req(MemNode::Address, prev);

          // TODO: also need to update memory if it's from old object's memory!
          if (is_in_table) {
            gvn.hash_find_insert(x);
          }
        }
      } // x->is_Load()
    }
  }
}

EscapedState* PEAState::materialize(GraphKit* kit, Node* var) {
  ObjID alloc = is_alias(var);
  assert(alloc != nullptr && get_object_state(alloc)->is_virtual(), "sanity check");
#ifndef PRODUCT
  if (Verbose) {
    tty->print_cr("PEA materializes a virtual object: %d", alloc->_idx);
  }
#endif
  Compile* C = kit->C;
  const TypeOopPtr* oop_type = var->as_Type()->type()->is_oopptr();
  Node* objx = kit->materialize_object(alloc, oop_type);
  VirtualState* virt = static_cast<VirtualState*>(get_object_state(alloc));

  if (oop_type->isa_instptr()) {
    ciInstanceKlass* ik = oop_type->is_instptr()->instance_klass();
#ifndef PRODUCT
    if (Verbose) {
      tty->print("ciInstanceKlass: ");
      ik->print_name_on(tty);
      tty->cr();
    }
#endif

    for (int i = 0; i < ik->nof_nonstatic_fields(); ++i) {
      ciField* field = ik->nonstatic_field_at(i);
      BasicType bt = field->layout_type();
      const Type* type = Type::get_const_basic_type(bt);
      Node* val = virt->get_field(i);

#ifndef PRODUCT
      if (Verbose) {
        tty->print("flt#%2d: ", i);
        field->print_name_on(tty);
        tty->cr();
      }
#endif
      // no initial value or is captured by InitializeNode
      if (val == nullptr) continue;

      int offset = field->offset_in_bytes();
      Node* adr = kit->basic_plus_adr(objx, objx, offset);
      const TypePtr* adr_type = C->alias_type(field)->adr_type();
      DecoratorSet decorators = IN_HEAP;

      bool is_obj = is_reference_type(bt);
      // Store the value.
      const Type* field_type;
      if (!field->type()->is_loaded()) {
        field_type = TypeInstPtr::BOTTOM;
      } else {
        if (is_obj) {
          field_type = TypeOopPtr::make_from_klass(field->type()->as_klass());
        } else {
          field_type = Type::BOTTOM;
        }
      }
      decorators |= field->is_volatile() ? MO_SEQ_CST : MO_UNORDERED;


      val = kit->gvn().transform(val);
#ifndef PRODUCT
      if (Verbose) {
        val->dump();
      }
#endif
      kit->access_store_at(objx, adr, adr_type, val, field_type, bt, decorators);
    }
  } else {
    assert(false, "array not support yet!");
  }
  EscapedState* escaped = new EscapedState(objx);
  update(alloc, escaped);

#ifndef PRODUCT
  if (Verbose) {
    tty->print("new object: ");
    objx->dump();
  }
#endif

  // replace obj with objx
  replace_in_map(kit, var, objx);
  _alias.put(objx, alloc);
  _alias.remove(var);

#ifdef ASSERT
  validate();
#endif
  return escaped;
}

#ifndef PRODUCT
void PEAState::print_on(outputStream* os) const {
  os->print_cr("PEAState:");

  _state.iterate([&](ObjID obj, ObjectState* state) {
    bool is_virt = state->is_virtual();
    os->print("Obj#%d(%s) ref = %d aliases = [", obj->_idx, is_virt ? "Virt" : "Mat", state->ref_cnt());

    _alias.iterate([&](Node* node, ObjID obj2) {
      if (obj == obj2){
        os->print("%d, ", node->_idx);
      }
      return true;
    });

    os->print_cr("]");

    if (is_virt) {
      VirtualState* vs = static_cast<VirtualState*>(state);
      vs->print_on(os);
    }
    return true;
  });
}
#endif

#ifdef ASSERT
void PEAState::validate() const {
  _state.iterate([&](ObjID obj, ObjectState* state) {
    int cnt = 0;
    _alias.iterate([&](Node* node, ObjID obj2) {
      if (obj == obj2){
        cnt++;
      }
      return true;
    });
    assert(cnt == state->ref_cnt(), "refcount is broken");
    return true;
  });


  _alias.iterate([&](Node* var, ObjID obj) {
    assert(contains(obj), "id must exist");
    return true;
  });
}
#endif
