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

#include "gc/shared/c2/modRefBarrierSetC2.hpp"
#include "opto/arraycopynode.hpp"
#include "opto/graphKit.hpp"
#include "opto/idealKit.hpp"

Node* ModRefBarrierSetC2::store_at_resolved(C2Access& access, C2AccessValue& val) const {
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

Node* ModRefBarrierSetC2::atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                         Node* new_val, const Type* value_type) const {
  if (!access.is_oop()) {
    return BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);
  }

  Node* result = BarrierSetC2::atomic_cmpxchg_val_at_resolved(access, expected_val, new_val, value_type);

  post_barrier(access.kit(), access.base(), access.addr().node(), new_val, true);

  return result;
}

Node* ModRefBarrierSetC2::atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
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

Node* ModRefBarrierSetC2::atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* value_type) const {
  Node* result = BarrierSetC2::atomic_xchg_at_resolved(access, new_val, value_type);
  if (!access.is_oop()) {
    return result;
  }

  post_barrier(access.kit(), access.base(), access.addr().node(), new_val, true);

  return result;
}
