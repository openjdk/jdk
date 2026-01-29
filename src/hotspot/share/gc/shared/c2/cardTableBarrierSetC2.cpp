/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "ci/ciUtilities.hpp"
#include "gc/shared/c2/cardTableBarrierSetC2.hpp"
#include "gc/shared/cardTable.hpp"
#include "gc/shared/cardTableBarrierSet.hpp"
#include "gc/shared/gc_globals.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"
#include "opto/macro.hpp"
#include "utilities/macros.hpp"

#define __ ideal.

Node* CardTableBarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
  DecoratorSet decorators = access.decorators();

  Node* adr = access.addr().node();

  bool is_array = (decorators & IS_ARRAY) != 0;
  bool anonymous = (decorators & ON_UNKNOWN_OOP_REF) != 0;
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool use_precise = is_array || anonymous;
  bool tightly_coupled_alloc = (decorators & C2_TIGHTLY_COUPLED_ALLOC) != 0;

  if (!access.is_oop() || tightly_coupled_alloc || (!in_heap && !anonymous)) {
    return BarrierSetC2::store_at_resolved(access, val);
  }

  assert(access.is_parse_access(), "entry not supported at optimization time");
  C2ParseAccess& parse_access = static_cast<C2ParseAccess&>(access);

  Node* store = BarrierSetC2::store_at_resolved(access, val);
  post_barrier(parse_access.kit(), access.base(), adr, val.node(), use_precise);

  return store;
}

Node* CardTableBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                            Node* new_val, const Type* value_type) const {
  if (!access.is_oop()) {
    return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
  }

  Node* result = BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);

  post_barrier(access.kit(), access.base(), access.addr().node(), new_val, true);

  return result;
}

Node* CardTableBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                             Node* new_val, const Type* value_type) const {
  GraphKit* kit = access.kit();

  if (!access.is_oop()) {
    return BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);
  }

  Node* load_store = BarrierSetC2::atomic_cmpxchg_bool_at_resolved(access, expected_val, new_val, value_type);

  // Emit the post barrier only when the actual store happened. This makes sense
  // to check only for LS_cmp_* that can fail to set the value.
  // LS_cmp_exchange does not produce any branches by default, so there is no
  // boolean result to piggyback on. TODO: When we merge CompareAndSwap with
  // CompareAndExchange and move branches here, it would make sense to conditionalize
  // post_barriers for LS_cmp_exchange as well.
  //
  // CAS success path is marked more likely since we anticipate this is a performance
  // critical path, while CAS failure path can use the penalty for going through unlikely
  // path as backoff. Which is still better than doing a store barrier there.
  IdealKit ideal(kit);
  ideal.if_then(load_store, BoolTest::ne, ideal.ConI(0), PROB_STATIC_FREQUENT); {
    kit->sync_kit(ideal);
    post_barrier(kit, access.base(), access.addr().node(), new_val, true);
    ideal.sync_kit(kit);
  } ideal.end_if();
  kit->final_sync(ideal);

  return load_store;
}

Node* CardTableBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* value_type) const {
  Node* result = BarrierSetC2::atomic_xchg_at_resolved(access, new_val, value_type);
  if (!access.is_oop()) {
    return result;
  }

  post_barrier(access.kit(), access.base(), access.addr().node(), new_val, true);

  return result;
}

Node* CardTableBarrierSetC2::byte_map_base_node(GraphKit* kit) const {
  // Get base of card map
  CardTable::CardValue* card_table_base = ci_card_table_address_const();
   if (card_table_base != nullptr) {
     return kit->makecon(TypeRawPtr::make((address)card_table_base));
   } else {
     return kit->null();
   }
}

// vanilla post barrier
// Insert a write-barrier store.  This is to let generational GC work; we have
// to flag all oop-stores before the next GC point.
void CardTableBarrierSetC2::post_barrier(GraphKit* kit,
                                         Node* obj,
                                         Node* adr,
                                         Node* val,
                                         bool use_precise) const {
  // No store check needed if we're storing a null.
  if (val != nullptr && val->is_Con()) {
    const Type* t = val->bottom_type();
    if (t == TypePtr::NULL_PTR || t == Type::TOP) {
      return;
    }
  }

  if (use_ReduceInitialCardMarks()
      && obj == kit->just_allocated_object(kit->control())) {
    // We can skip marks on a freshly-allocated object in Eden.
    // Keep this code in sync with CardTableBarrierSet::on_slowpath_allocation_exit.
    // That routine informs GC to take appropriate compensating steps,
    // upon a slow-path allocation, so as to make this card-mark
    // elision safe.
    return;
  }

  if (!use_precise) {
    // All card marks for a (non-array) instance are in one place:
    adr = obj;
  } else {
    // Else it's an array (or unknown), and we want more precise card marks.
  }

  assert(adr != nullptr, "");

  IdealKit ideal(kit, true);

  // Convert the pointer to an int prior to doing math on it
  Node* cast = __ CastPX(__ ctrl(), adr);

  // Divide by card size
  Node* card_offset = __ URShiftX(cast, __ ConI(CardTable::card_shift()));

  // Combine card table base and card offset
  Node* card_adr = __ AddP(__ top(), byte_map_base_node(kit), card_offset);

  // Get the alias_index for raw card-mark memory
  int adr_type = Compile::AliasIdxRaw;

  // Dirty card value to store
  Node* dirty = __ ConI(CardTable::dirty_card_val());

  if (UseCondCardMark) {
    // The classic GC reference write barrier is typically implemented
    // as a store into the global card mark table.  Unfortunately
    // unconditional stores can result in false sharing and excessive
    // coherence traffic as well as false transactional aborts.
    // UseCondCardMark enables MP "polite" conditional card mark
    // stores.  In theory we could relax the load from ctrl() to
    // no_ctrl, but that doesn't buy much latitude.
    Node* card_val = __ load( __ ctrl(), card_adr, TypeInt::BYTE, T_BYTE, adr_type);
    __ if_then(card_val, BoolTest::ne, dirty);
  }

  // Smash dirty value into card
  __ store(__ ctrl(), card_adr, dirty, T_BYTE, adr_type, MemNode::unordered);

  if (UseCondCardMark) {
    __ end_if();
  }

  // Final sync IdealKit and GraphKit.
  kit->final_sync(ideal);
}

bool CardTableBarrierSetC2::use_ReduceInitialCardMarks() {
  return ReduceInitialCardMarks;
}

void CardTableBarrierSetC2::eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const {
  assert(node->Opcode() == Op_CastP2X, "ConvP2XNode required");
  Node *shift = node->unique_out();
  Node *addp = shift->unique_out();
  for (DUIterator_Last jmin, j = addp->last_outs(jmin); j >= jmin; --j) {
    Node *mem = addp->last_out(j);
    if (UseCondCardMark && mem->is_Load()) {
      assert(mem->Opcode() == Op_LoadB, "unexpected code shape");
      // The load is checking if the card has been written so
      // replace it with zero to fold the test.
      macro->replace_node(mem, macro->intcon(0));
      continue;
    }
    assert(mem->is_Store(), "store required");
    macro->replace_node(mem, mem->in(MemNode::Memory));
  }
}

bool CardTableBarrierSetC2::array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, bool is_clone_instance, ArrayCopyPhase phase) const {
  bool is_oop = is_reference_type(type);
  return is_oop && (!tightly_coupled_alloc || !use_ReduceInitialCardMarks());
}
