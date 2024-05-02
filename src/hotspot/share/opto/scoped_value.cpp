/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "opto/callGenerator.hpp"
#include "opto/castnode.hpp"
#include "opto/graphKit.hpp"
#include "opto/rootnode.hpp"
#include "opto/scoped_value.hpp"

bool ScopedValueGetPatternMatcher::match_cache_null_check_with_input(Node* maybe_cache, Node* maybe_nullptr, IfNode* iff) {
  if (!maybe_cache->is_Proj() ||
      !maybe_cache->in(0)->is_Call() ||
      maybe_cache->in(0)->as_CallJava()->method()->intrinsic_id() != vmIntrinsics::_scopedValueCache) {
    return false;
  }
  assert(maybe_nullptr->bottom_type() == TypePtr::NULL_PTR, "should be a test with null");
  assert(_cache_not_null_iff == nullptr, "should only find one get_cache_if");
  _cache_not_null_iff = iff;
  assert(_scoped_value_cache == nullptr || _scoped_value_cache == maybe_cache->in(0),
         "should only find one scoped_value_cache");
  _scoped_value_cache = maybe_cache->in(0)->as_Call();
  return true;
}

// Pattern matches:
// if ((objects = scopedValueCache()) != null) {
bool ScopedValueGetPatternMatcher::match_cache_null_check(Node* maybe_iff) {
  if (maybe_iff->Opcode() != Op_If) {
    return false;
  }
  IfNode* iff = maybe_iff->as_If();
  BoolNode* bol = iff->in(1)->as_Bool();
  Node* cmp = bol->in(1);
  assert(cmp->Opcode() == Op_CmpP, "only reference comparisons in ScopedValue.get()");
  Node* cmp_in1 = cmp->in(1)->uncast();
  Node* cmp_in2 = cmp->in(2)->uncast();
  if (match_cache_null_check_with_input(cmp_in1, cmp_in2, iff)) {
    return true;
  }
  if (match_cache_null_check_with_input(cmp_in2, cmp_in1, iff)) {
    return true;
  }
  return false;
}

// Pattern matches:
// if (objects[n] == this) {
bool ScopedValueGetPatternMatcher::match_cache_probe(Node* maybe_iff) {
  if (maybe_iff->Opcode() != Op_If) {
    return false;
  }
  BoolNode* bol = maybe_iff->in(1)->as_Bool();
  Node* cmp = bol->in(1);
  assert(cmp->Opcode() == Op_CmpP, "only reference comparisons cache_array_load ScopedValue.get()");
  Node* cmp_in1 = cmp->in(1)->uncast();
  Node* cmp_in2 = cmp->in(2)->uncast();
  Node* uncasted_scoped_value_object = _scoped_value_object->uncast();
  assert(cmp_in1 == uncasted_scoped_value_object || cmp_in2 == uncasted_scoped_value_object,
         "one of the comparison inputs must be the scoped value oop");
  Node* cache_array_load = cmp_in1 == uncasted_scoped_value_object ? cmp_in2 : cmp_in1;
  BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
  cache_array_load = bs->step_over_gc_barrier(cache_array_load);
  if (cache_array_load->Opcode() == Op_DecodeN) {
    cache_array_load = cache_array_load->in(1);
  }
  assert(cache_array_load->Opcode() == Op_LoadP || cache_array_load->Opcode() == Op_LoadN,
         "load from cache array expected");
  assert(_kit.C->get_alias_index(cache_array_load->adr_type()) == _kit.C->get_alias_index(TypeAryPtr::OOPS),
         "load from cache array expected");
  AddPNode* array_cache_load_adr = cache_array_load->in(MemNode::Address)->as_AddP();
  ProjNode* scoped_value_cache_proj = array_cache_load_adr->in(AddPNode::Base)->uncast()->as_Proj();
  assert(scoped_value_cache_proj->in(0)->as_CallJava()->method()->intrinsic_id() == vmIntrinsics::_scopedValueCache,
         "should be call to Thread.scopedValueCache()");
  assert(_scoped_value_cache == nullptr || _scoped_value_cache == scoped_value_cache_proj->in(0),
         "only one cache expected");
  _scoped_value_cache = scoped_value_cache_proj->in(0)->as_Call();
  assert(cache_array_load->in(MemNode::Memory)->is_Proj() &&
         cache_array_load->in(MemNode::Memory)->in(0) == _scoped_value_cache,
         "load from cache expected right after Thread.scopedValueCache() call");
  Node* second_addp_for_array_cache_load_adr = array_cache_load_adr->in(AddPNode::Address);
  Node* array_cache_load_offset = array_cache_load_adr->in(AddPNode::Offset);
  intptr_t array_cache_load_const_offset = array_cache_load_offset->find_intptr_t_con(-1);
  BasicType bt = TypeAryPtr::OOPS->array_element_basic_type();
  int shift_for_cache_array_load = exact_log2(type2aelembytes(bt));
  int header_size_for_cache_array_load = arrayOopDesc::base_offset_in_bytes(bt);
  assert(array_cache_load_const_offset >= header_size_for_cache_array_load,
         "load from cache doesn't access the cache array?");
  intptr_t array_cache_load_offset_in_body = array_cache_load_const_offset - header_size_for_cache_array_load;

  Node* index_in_cache_array = _kit.gvn().intcon(
          checked_cast<int>(array_cache_load_offset_in_body >> shift_for_cache_array_load));
  if (second_addp_for_array_cache_load_adr->is_AddP()) {
    assert(!second_addp_for_array_cache_load_adr->in(AddPNode::Address)->is_AddP() &&
           second_addp_for_array_cache_load_adr->in(AddPNode::Base) == array_cache_load_adr->in(AddPNode::Base),
           "only 2 AddPs for address computation");
    Node* array_cache_load_offset_from_second_addp = second_addp_for_array_cache_load_adr->in(AddPNode::Offset);
    assert(array_cache_load_offset_from_second_addp->Opcode() == Op_LShiftX &&
           array_cache_load_offset_from_second_addp->in(2)->find_int_con(-1) == shift_for_cache_array_load,
           "Not an array access?");
    Node* array_cache_load_index_from_second_addp = array_cache_load_offset_from_second_addp->in(1);
#ifdef _LP64
    assert(array_cache_load_index_from_second_addp->Opcode() == Op_ConvI2L,
           "unexpected address calculation shape");
    array_cache_load_index_from_second_addp = array_cache_load_index_from_second_addp->in(1);
    assert(!(array_cache_load_index_from_second_addp->Opcode() == Op_CastII &&
             array_cache_load_index_from_second_addp->in(0)->is_Proj() &&
             array_cache_load_index_from_second_addp->in(0)->in(0) == _cache_not_null_iff),
           "no CastII because index_in_cache_array is known to be positive");
#endif
            index_in_cache_array = _kit.gvn().transform(new AddINode(array_cache_load_index_from_second_addp, index_in_cache_array));
  }

  if (_first_cache_probe_iff == nullptr) {
    _first_cache_probe_iff = maybe_iff->as_If();
    _first_index_in_cache = index_in_cache_array;
  } else {
    assert(_second_cache_probe_iff == nullptr, "no more than 2 cache probes");
    _second_cache_probe_iff = maybe_iff->as_If();
    _second_index_in_cache = index_in_cache_array;
  }
  return true;
}

// First traversal of the get() subgraph starts from the end of the method and follows control paths until it reaches
// the Thread.scopedValueCache() call. Given the shape of the method and some paths may have been trimmed and end with
// an uncommon trap, it could reach either the first or the second cache probe if first. Figure out which is the first
// here.
void ScopedValueGetPatternMatcher::adjust_order_of_first_and_second_probe_if(const Unique_Node_List& scoped_value_get_subgraph) {
  if (_second_cache_probe_iff == nullptr) {
    return;
  }
  assert(_first_cache_probe_iff != nullptr, "can't have a second iff if there's no first one");
  ResourceMark rm;
  Node_Stack stack(0);
  stack.push(_cache_not_null_iff, 0);
  while (stack.is_nonempty()) {
    Node* c = stack.node();
    assert(c->is_CFG(), "only cfg nodes");
    uint i = stack.index();
    if (i < c->outcnt()) {
      stack.set_index(i + 1);
      Node* u = c->raw_out(i);
      if (scoped_value_get_subgraph.member(u) && u != c) {
        if (u == _first_cache_probe_iff) {
          return;
        } else if (u == _second_cache_probe_iff) {
          swap(_first_cache_probe_iff, _second_cache_probe_iff);
          swap(_first_index_in_cache, _second_index_in_cache);
          return;
        }
        stack.push(u, 0);
      }
    } else {
      stack.pop();
    }
  }
  fatal("should have found the cache probe ifs");
}

// ScopedValue.get() probes 2 cache locations. If, when pattern matching the get() subgraph, we found 2 ifs, then the
// first and second locations were probed. If the first if's other branch is to an uncommon trap, then that location
// never saw a cache hit. In that case, when the ScopedValueGetHitsInCacheNode is expanded, only code to probe
// the second location is added back to the IR.
//
// Before transformation:        After transformation:                      After expansion:
// cache = scopedValueCache();   cache = currentThread.scopedValueCache;    cache = currentThread.scopedValueCache;
// if (cache == null) {          if (hits_in_cache(cache)) {                if (cache != null && second_entry_hits) {
//   goto slow_call;               result = load_from_cache;                  result = second_entry;
// }                             } else {                                   } else {
// if (first_entry_hits) {         if (cache == null) {                       if (cache == null) {
//   uncommon_trap();                goto slow_call;                            goto slow_call;
// } else {                        }                                          }
//   if (second_entry_hits) {      if (first_entry_hits) {                    if (first_entry_hits) {
//     result = second_entry;        uncommon_trap();                           uncommon_trap();
//   } else {                      } else {                                   } else {
//     goto slow_call;               if (second_entry_hits) {                   if (second_entry_hits) {
//   }                                  halt;                                      halt;
// }                                  } else {                                   } else {
// continue:                            goto slow_call;                            goto slow_call;
// ...                               }                                          }
// return;                         }                                          }
//                               }                                          }
// slow_call:                    continue:                                  continue:
// result = slowGet();           ...                                        ...
// goto continue;                return;                                    return;
//
//                               slow_call:                                 slow_call:
//                               result = slowGet();                        result = slowGet();
//                               goto continue;                             goto continue;
//
void ScopedValueGetPatternMatcher::remove_first_probe_if_when_it_never_hits() {
  if (_first_cache_probe_iff == nullptr || _second_cache_probe_iff == nullptr) {
    return;
  }
  ProjNode* get_first_iff_failure = _first_cache_probe_iff->proj_out(
          _first_cache_probe_iff->in(1)->as_Bool()->_test._test == BoolTest::ne ? 0 : 1);
  CallStaticJavaNode* get_first_iff_unc = get_first_iff_failure->is_uncommon_trap_proj(Deoptimization::Reason_none);
  if (get_first_iff_unc == nullptr) {
    return;
  }
  // first cache check never hits, keep only the second.
  swap(_first_cache_probe_iff, _second_cache_probe_iff);
  swap(_first_index_in_cache, _second_index_in_cache);
  _second_cache_probe_iff = nullptr;
  _second_index_in_cache = nullptr;
}

// The call for ScopedValue.get() was just inlined. The code here pattern matches the resulting subgraph. To make it
// easier:
// - the slow path call to slowGet() is not inlined. If heuristics decided it should be, it was enqueued for late
// inlining which will happen later.
// - The call to Thread.scopedValueCache() is not inlined either.
//
// The pattern matching starts from the current control (end of inlining) and looks for the call for
// Thread.scopedValueCache() which acts as a marker for the beginning of the subgraph for ScopedValue.get(). That
// subgraph is connected to the graph of the current compilation but there's no risk of "escaping" ScopedValue.get()
// during pattern matching because the call to Thread.scopedValueCache() dominates the entire subgraph for
// ScopedValue.get().
// In the process of pattern matching a number of checks from the java code of ScopedValue.get() are expected to
// be encountered. They are recorded to be used later when the subgraph for ScopedValue.get() is transformed.
void ScopedValueGetPatternMatcher::pattern_match() {
  ResourceMark rm;
  Unique_Node_List scoped_value_get_subgraph;
  scoped_value_get_subgraph.push(_kit.control());
  for (uint i = 0; i < scoped_value_get_subgraph.size(); ++i) {
    Node* c = scoped_value_get_subgraph.at(i);
    assert(c->is_CFG(), "only control flow here");
    if (c->is_Region()) {
      for (uint j = 1; j < c->req(); ++j) {
        Node* in = c->in(j);
        if (in != nullptr) {
          assert(!in->is_top(), "no dead path here");
          scoped_value_get_subgraph.push(in);
        }
      }
    } else if (match_cache_null_check(c)) {
      // we reached the start of ScopedValue.get()
    } else if (match_cache_probe(c)) {
      scoped_value_get_subgraph.push(c->in(0));
    } else if (c->is_RangeCheck()) {
      // Range checks for:
      // objects = scopedValueCache()
      // int n = (hash & Cache.SLOT_MASK) * 2;
      // if (objects[n] == this) {
      //
      // always succeeds because the cache is of size CACHE_TABLE_SIZE * 2, CACHE_TABLE_SIZE is a power of 2 and
      // SLOT_MASK = CACHE_TABLE_SIZE - 1
#ifdef ASSERT
      // Verify the range check is against the return value from Thread.scopedValueCache()
      BoolNode* rc_bol = c->in(1)->as_Bool();
      CmpNode* rc_cmp = rc_bol->in(1)->as_Cmp();
      assert(rc_cmp->Opcode() == Op_CmpU, "unexpected range check shape");
      Node* rc_range = rc_cmp->in(rc_bol->_test.is_less() ? 2 : 1);
      assert(rc_range->Opcode() == Op_LoadRange, "unexpected range check shape");
      AddPNode* rc_range_address = rc_range->in(MemNode::Address)->as_AddP();
      ProjNode* rc_range_base = rc_range_address->in(AddPNode::Base)->uncast()->as_Proj();
      CallJavaNode* scoped_value_cache = rc_range_base->in(0)->as_CallJava();
      assert(scoped_value_cache->method()->intrinsic_id() == vmIntrinsics::_scopedValueCache, "unexpected range check shape");
#endif
      _kit.gvn().hash_delete(c);
      c->set_req(1, _kit.gvn().intcon(1));
      _kit.C->record_for_igvn(c);
      scoped_value_get_subgraph.push(c->in(0));
    } else if (c->is_CallStaticJava()) {
      assert(_slow_call == nullptr &&
             c->as_CallStaticJava()->method()->intrinsic_id() == vmIntrinsics::_ScopedValue_slowGet,
             "ScopedValue.slowGet() call expected");
      _slow_call = c->as_CallStaticJava();
      scoped_value_get_subgraph.push(c->in(0));
    } else {
      assert(c->is_Proj() || c->is_Catch(), "unexpected node when pattern matching ScopedValue.get()");
      scoped_value_get_subgraph.push(c->in(0));
    }
  }
  assert(_cache_not_null_iff != nullptr, "pattern matching should find cache null check");
  assert(_second_cache_probe_iff == nullptr || _first_cache_probe_iff != nullptr,
         "second cache probe iff only if first one exists");

  // get_first_iff/get_second_iff contain the first/second check we ran into during the graph traversal. They are not
  // guaranteed to be the first/second one in execution order. Indeed, the graph traversal started from the end of
  // ScopedValue.get() and followed control flow inputs towards the start. In the process and in the general case, it
  // encountered regions merging the results from the 3 paths that can produce the get() result: slowGet() call, first
  // cache location, second cache location. Depending on the order of region inputs, the first or second cache
  // location test can be encountered first or second.
  // Perform another traversal to figure out which is first.
  adjust_order_of_first_and_second_probe_if(scoped_value_get_subgraph);
  remove_first_probe_if_when_it_never_hits();
}

// (1) is the subgraph before transformation (some branches may
// not be present depending on profile data), in pseudo code. (4)
// is the subgraph after transformation. (2) and (3) are
// intermediate steps referenced in the code below.
//
//            (1)                          (2)                               (3)                                      (4)
// cache = scopedValueCache();  cache = scopedValueCache()  cache = currentThread.scopedValueCache;  cache = currentThread.scopedValueCache;
// if (cache == null) {         if (cache == null) {        if (hits_in_cache(cache)) {              if (hits_in_cache(cache)) {
//   goto slow_call;              goto slow_call;             result = load_from_cache;                result = load_from_cache;
// }                            }                             goto region_fast_slow;                 } else {
// if (first_entry_hits) {      if (first_entry_hits) {     } else {                                   if (cache == null) {
//   result = first_entry;        result = first_entry;       if (cache == null) {                       goto slow_call;
// } else {                     } else {                        goto slow_call;                        }
//   if (second_entry_hits) {     if (second_entry_hits) {    }                                        if (first_entry_hits) {
//     result = second_entry;       result = second_entry;    if (first_entry_hits) {                    halt;
//   } else {                     } else {                      result = first_entry;                  } else {
//     goto slow_call;              goto slow_call;           } else {                                   if (second_entry_hits) {
//   }                            }                             if (second_entry_hits) {                    halt;
// }                            }                                 result = second_entry;                  } else {
// continue:                    continue:                       } else {                                    goto slow_call;
// ...                          halt;                             goto slow_call;                        }
// return;                                                      }                                      }
//                              slow_call:                    }                                      }
// slow_call:                   result = slowGet();         }                                        continue:
// result = slowGet();          goto continue;              continue:                                ...
// goto continue;                                           halt;                                    return;
//                                                          region_fast_slow;
//                                                                                                   slow_call:
//                                                          slow_call:                               result = slowGet();
//                                                          result = slowGet();                      goto continue;
//                                                          goto continue;
//
//
// the transformed graph includes 2 copies of the cache probing logic. One represented by the
// ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache pair that is amenable to optimizations. The other from
// the result of the parsing of the java code where the success path ends with a Halt node. The reason for that is
// that some paths may end with an uncommon trap and if one traps, we want the trap to be recorded for the right bci.
// When the ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache pair is expanded, split if finds the duplicate
// logic and cleans it up.
void ScopedValueTransformer::transform_get_subgraph() {
  Compile* C = _kit.C;
  replace_current_exit_of_get_with_halt();

  // Graph now is (2)

  // Move right above the scopedValueCache() call
  CallNode* scoped_value_cache = _pattern_matcher.scoped_value_cache();
  Node* input_mem = scoped_value_cache->in(TypeFunc::Memory);
  Node* input_ctrl = scoped_value_cache->in(TypeFunc::Control);
  Node* input_io = scoped_value_cache->in(TypeFunc::I_O);

  _kit.set_control(input_ctrl);
  _kit.set_all_memory(input_mem);
  _kit.set_i_o(input_io);

  // replace it with its intrinsic code:
  Node* scoped_value_cache_load = _kit.make_scopedValueCache();
  // A single ScopedValueGetHitsInCache node represents all checks that are needed to probe the cache (cache not null,
  // cache_miss_prob with first hash, cache_miss_prob with second hash)
  // It will later be expanded back to all the checks so record profile data
  IfNode* cache_not_null_iff = _pattern_matcher.cache_not_null_iff();
  IfNode* first_cache_probe_iff = _pattern_matcher.first_cache_probe_iff();
  IfNode* second_cache_probe_iff = _pattern_matcher.second_cache_probe_iff();
  float probability_cache_exists = canonical_if_prob(cache_not_null_iff);
  float probability_first_cache_probe_fails = canonical_if_prob(first_cache_probe_iff);
  float probability_second_cache_probe_fails = canonical_if_prob(second_cache_probe_iff);
  Node* first_index_in_cache = _pattern_matcher.first_index_in_cache();
  Node* second_index_in_cache = _pattern_matcher.second_index_in_cache();
  ScopedValueGetHitsInCacheNode* hits_in_cache = new ScopedValueGetHitsInCacheNode(C, _kit.control(),
                                                                                   scoped_value_cache_load,
                                                                                   _kit.gvn().makecon(TypePtr::NULL_PTR),
                                                                                   _kit.memory(TypeAryPtr::OOPS),
                                                                                   _scoped_value_object,
                                                                                   first_index_in_cache == nullptr ? C->top() : first_index_in_cache,
                                                                                   second_index_in_cache == nullptr ? C->top() : second_index_in_cache,
                                                                                   cache_not_null_iff->_fcnt, probability_cache_exists,
                                                                                   if_cnt(first_cache_probe_iff), probability_first_cache_probe_fails,
                                                                                   if_cnt(second_cache_probe_iff), probability_second_cache_probe_fails);

  Node* transformed_sv_hits_in_cache = _kit.gvn().transform(hits_in_cache);
  assert(transformed_sv_hits_in_cache == hits_in_cache, "shouldn't be transformed to new node");

  // And compute the probability of a miss in the cache
  float cache_miss_prob;
  // probability_cache_exists: probability that cache array is not null
  // probability_first_cache_probe_fails: probability of a miss
  // probability_second_cache_probe_fails: probability of a miss
  if (probability_cache_exists == PROB_UNKNOWN || probability_first_cache_probe_fails == PROB_UNKNOWN || probability_second_cache_probe_fails == PROB_UNKNOWN) {
    cache_miss_prob = PROB_UNKNOWN;
  } else {
    float probability_cache_does_not_exist = 1 - probability_cache_exists;
    cache_miss_prob = probability_cache_does_not_exist + probability_cache_exists * probability_first_cache_probe_fails * probability_second_cache_probe_fails;
  }

  // Add the control flow that checks whether ScopedValueGetHitsInCache succeeds
  Node* bol = _kit.gvn().transform(new BoolNode(hits_in_cache, BoolTest::ne));
  IfNode* iff = new IfNode(_kit.control(), bol, 1 - cache_miss_prob, cache_not_null_iff->_fcnt);
  Node* transformed_iff = _kit.gvn().transform(iff);
  assert(transformed_iff == iff, "shouldn't be transformed to new node");
  Node* not_in_cache_proj = _kit.gvn().transform(new IfFalseNode(iff));
  Node* in_cache_proj = _kit.gvn().transform(new IfTrueNode(iff));

  // Merge the paths that produce the result (in case there's a slow path)
  CallStaticJavaNode* slow_call = _pattern_matcher.slow_call();
  Node* region_fast_slow = new RegionNode(slow_call == nullptr ? 2 : 3);
  Node* phi_cache_value = new PhiNode(region_fast_slow, TypeInstPtr::BOTTOM);
  Node* phi_mem = new PhiNode(region_fast_slow, Type::MEMORY, TypePtr::BOTTOM);
  Node* phi_io = new PhiNode(region_fast_slow, Type::ABIO);

  // remove the scopedValueCache() call
  remove_scoped_value_cache_call(not_in_cache_proj, scoped_value_cache_load);

  // ScopedValueGetLoadFromCache is a single that represents the result of a hit in the cache
  Node* sv_load_from_cache = _kit.gvn().transform(new ScopedValueGetLoadFromCacheNode(C, in_cache_proj, hits_in_cache));
  region_fast_slow->init_req(1, in_cache_proj);
  phi_cache_value->init_req(1, sv_load_from_cache);
  phi_mem->init_req(1, _kit.reset_memory());
  phi_io->init_req(1, _kit.i_o());

  // Graph now is (3)

  if (slow_call != nullptr) {
    // At this point, return from slowGet() falls through to a Halt node. Connect it to the new normal exit (region_fast_slow)
    CallProjections slow_projs;
    slow_call->extract_projections(&slow_projs, false);
    Node* fallthrough = slow_projs.fallthrough_catchproj->clone();
    _kit.gvn().set_type(fallthrough, fallthrough->bottom_type());
    C->gvn_replace_by(slow_projs.fallthrough_catchproj, C->top());
    region_fast_slow->init_req(2, fallthrough);
    phi_mem->init_req(2, slow_projs.fallthrough_memproj);
    phi_io->init_req(2, slow_projs.fallthrough_ioproj);
    phi_cache_value->init_req(2, slow_projs.resproj);
  }

  _kit.set_all_memory(_kit.gvn().transform(phi_mem));
  _kit.set_i_o(_kit.gvn().transform(phi_io));
  _kit.set_control(_kit.gvn().transform(region_fast_slow));
  C->record_for_igvn(region_fast_slow);
  _kit.pop();
  _kit.push(phi_cache_value);
  // The if nodes from parsing are now only reachable if get() doesn't hit in the cache. Adjust count/probability for
  // those nodes.
  float cache_miss_cnt = cache_miss_prob * cache_not_null_iff->_fcnt;
  reset_iff_prob_and_cnt(cache_not_null_iff, true, cache_miss_cnt);
  reset_iff_prob_and_cnt(first_cache_probe_iff, false, cache_miss_cnt);
  reset_iff_prob_and_cnt(second_cache_probe_iff, false, cache_miss_cnt);
}

void ScopedValueTransformer::remove_scoped_value_cache_call(Node* not_in_cache, Node* scoped_value_cache_load) const {
  CallProjections scoped_value_cache_projs;
  CallNode* scoped_value_cache = _pattern_matcher.scoped_value_cache();
  scoped_value_cache->extract_projections(&scoped_value_cache_projs, true);
  Compile* C = _kit.C;
  C->gvn_replace_by(scoped_value_cache_projs.fallthrough_memproj, _kit.merged_memory());
  C->gvn_replace_by(scoped_value_cache_projs.fallthrough_ioproj, _kit.i_o());
  C->gvn_replace_by(scoped_value_cache_projs.fallthrough_catchproj, not_in_cache);
  C->gvn_replace_by(scoped_value_cache_projs.resproj, scoped_value_cache_load);

  _kit.gvn().hash_delete(scoped_value_cache);
  scoped_value_cache->set_req(0, C->top());
  C->record_for_igvn(scoped_value_cache);
}

// Either the if leads to a Halt: that branch is never taken or it leads to an uncommon trap and the probability is
// left unchanged.
void ScopedValueTransformer::reset_iff_prob_and_cnt(IfNode* iff, bool expected, float cnt) {
  if (iff == nullptr) {
    return;
  }
  if (!iff->in(1)->as_Bool()->_test.is_canonical()) {
    ProjNode* proj = iff->proj_out(expected);
    if (!proj->is_uncommon_trap_proj()) {
      float prob = expected ? PROB_ALWAYS : PROB_NEVER;
      iff->_prob = prob;
    }
  } else {
    ProjNode* proj = iff->proj_out(!expected);
    if (!proj->is_uncommon_trap_proj()) {
      float prob = expected ? PROB_NEVER : PROB_ALWAYS;
      iff->_prob = prob;
    }
  }
  iff->_fcnt = cnt;
}

void ScopedValueTransformer::replace_current_exit_of_get_with_halt() const {
  // The path on exit of the method from parsing ends here
  Compile* C = _kit.C;
  Node* current_ctrl = _kit.control();
  Node* frame = _kit.gvn().transform(new ParmNode(C->start(), TypeFunc::FramePtr));
  Node* halt = _kit.gvn().transform(new HaltNode(current_ctrl, frame, "Dead path for ScopedValueCall::get"));
  C->root()->add_req(halt);
}

void Compile::inline_scoped_value_get_calls(PhaseIterGVN& igvn) {
  if (_scoped_value_late_inlines.is_empty()) {
    return;
  }
  PhaseGVN* gvn = initial_gvn();
  set_inlining_incrementally(true);

  igvn_worklist()->ensure_empty(); // should be done with igvn

  _late_inlines_pos = _late_inlines.length();

  while (_scoped_value_late_inlines.length() > 0) {
    CallGenerator* cg = _scoped_value_late_inlines.pop();
    assert(cg->method()->intrinsic_id() == vmIntrinsics::_ScopedValue_get, "only calls to ScopedValue.get() here");
    if (has_scoped_value_invalidate()) {
      // ScopedValue$Cache.invalidate() is called so pessimistically assume we can't optimize ScopedValue.get() and
      // enqueue the call for regular late inlining
      cg->set_process_result(false);
      C->add_late_inline(cg);
      continue;
    }
    C->set_has_scoped_value_get_nodes(true);
    CallNode* call = cg->call_node();
    CallProjections call_projs;
    call->extract_projections(&call_projs, true);
    Node* scoped_value_object = call->in(TypeFunc::Parms);
    Node* control_out = call_projs.fallthrough_catchproj;
    Node* scoped_value_get_result = call_projs.resproj;
    // Insert a ScopedValueGetResult node after the call with the result of ScopedValue.get() as input
    if (scoped_value_get_result == nullptr) {
      scoped_value_get_result = gvn->transform(new ProjNode(call, TypeFunc::Parms));
    }
    // Clone the control and result projections of the call and add them as input to the ScopedValueGetResult node
    // Updating uses of the call result/control is then done by replacing the initial control and result projections
    // of the call with the new control and result projections of the ScopedValueGetResult node.
    control_out = control_out->clone();
    gvn->set_type_bottom(control_out);
    gvn->record_for_igvn(control_out);
    scoped_value_get_result = scoped_value_get_result->clone();
    gvn->set_type_bottom(scoped_value_get_result);
    gvn->record_for_igvn(scoped_value_get_result);

    ScopedValueGetResultNode* get_result = new ScopedValueGetResultNode(C, control_out, scoped_value_object, scoped_value_get_result);
    Node* sv_get_resultx = gvn->transform(get_result);
    assert(sv_get_resultx == get_result, "this breaks if gvn returns new node");
    Node* control_proj = gvn->transform(new ProjNode(get_result, ScopedValueGetResultNode::ControlOut));
    Node* res_proj = gvn->transform(new ProjNode(get_result, ScopedValueGetResultNode::Result));

    C->gvn_replace_by(call_projs.fallthrough_catchproj, control_proj);
    if (call_projs.resproj != nullptr) {
      C->gvn_replace_by(call_projs.resproj, res_proj);
    }

    Node* control_projx = gvn->transform(control_proj);
    assert(control_projx == control_proj, "this breaks if gvn returns new node");
    Node* res_projx = gvn->transform(res_proj);
    assert(res_projx == res_proj, "this breaks if gvn returns new node");

    // Inline the call to ScopedValue.get(). That triggers the execution of LateInlineScopedValueCallGenerator::process_result()
    cg->do_late_inline();
    if (failing()) return;

    C->set_has_split_ifs(true);
  }

  inline_incrementally_cleanup(igvn);

  set_inlining_incrementally(false);

  inline_incrementally(igvn);
}

const Type* ScopedValueGetResultNode::Value(PhaseGVN* phase) const {
  if (phase->type(in(0)) == Type::TOP) {
    return Type::TOP;
  }
  return Node::Value(phase);
}

Node* ScopedValueGetLoadFromCacheNode::scoped_value() const {
  Node* hits_in_cache = in(1);
  return hits_in_cache->as_ScopedValueGetHitsInCache()->scoped_value();
}

IfNode* ScopedValueGetLoadFromCacheNode::iff() const {
  return in(0)->as_IfTrue()->in(0)->as_If();
}

#ifdef ASSERT
void ScopedValueGetLoadFromCacheNode::verify() const {
  // check a ScopedValueGetHitsInCache guards this ScopedValueGetLoadFromCache
  IfNode* iff = this->iff();
  assert(iff->in(1)->is_Bool(), "unexpected ScopedValueGetLoadFromCache shape");
  assert(iff->in(1)->in(1)->Opcode() == Op_ScopedValueGetHitsInCache, "unexpected ScopedValueGetLoadFromCache shape");
  assert(iff->in(1)->in(1) == in(1), "unexpected ScopedValueGetLoadFromCache shape");
}
#endif

IfProjNode* ScopedValueGetHitsInCacheNode::success_proj() const {
  ScopedValueGetLoadFromCacheNode* load_from_cache = this->load_from_cache();
  BoolNode* bol = find_out_with(Op_Bool, true)->as_Bool();
  assert(bol->_test._test == BoolTest::ne, "unexpected ScopedValueGetHitsInCache shape");
  IfNode* iff = bol->find_out_with(Op_If, true)->as_If();
  assert(load_from_cache == nullptr || load_from_cache->iff() == iff, "unexpected ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache shape");
  IfProjNode* dom = iff->proj_out(1)->as_IfProj();
  assert(load_from_cache == nullptr || dom == load_from_cache->in(0), "unexpected ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache shape");
  return dom;
}

#ifdef ASSERT
void ScopedValueGetHitsInCacheNode::verify() const {
  for (DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++) {
    Node* u = fast_out(i);
    assert(u->is_Bool() || u->Opcode() == Op_ScopedValueGetLoadFromCache, "wrong ScopedValueGetHitsInCache shape");
  }
  ScopedValueGetLoadFromCacheNode* load = load_from_cache();
  if (load != nullptr) {
    assert(load->in(0)->Opcode() == Op_IfTrue, "wrong ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache shape");
    assert(load->in(0)->in(0)->in(1)->is_Bool(), "wrong ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache shape");
    assert(load->in(0)->in(0)->in(1)->in(1) == this, "wrong ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache shape");
  }
}
#endif

// Loop predication support
bool PhaseIdealLoop::is_uncommon_or_multi_uncommon_trap_if_pattern(IfProjNode* proj) {
  if (proj->is_uncommon_trap_if_pattern()) {
    return true;
  }
  if (proj->in(0)->in(1)->is_Bool() &&
      proj->in(0)->in(1)->in(1)->Opcode() == Op_ScopedValueGetHitsInCache &&
      proj->is_multi_uncommon_trap_if_pattern()) {
    return true;
  }
  return false;
}

// A ScopedValueGetHitsInCache check is loop invariant if the scoped value object it is applied to is loop invariant
bool PhaseIdealLoop::loop_predication_for_scoped_value_get(IdealLoopTree* loop, IfProjNode* if_success_proj,
                                                           ParsePredicateSuccessProj* parse_predicate_proj,
                                                           Invariance& invar, Deoptimization::DeoptReason reason,
                                                           IfNode* iff, IfProjNode*& new_predicate_proj) {
  BoolNode* bol = iff->in(1)->as_Bool();
  if (bol->in(1)->Opcode() != Op_ScopedValueGetHitsInCache) {
    return false;
  }
  ScopedValueGetHitsInCacheNode* hits_in_the_cache = bol->in(1)->as_ScopedValueGetHitsInCache();
  if (!invar.is_invariant(hits_in_the_cache->scoped_value()) ||
      !invar.is_invariant(hits_in_the_cache->index1()) ||
      !invar.is_invariant(hits_in_the_cache->index2())) {
    return false;
  }
  Node* load_from_cache = if_success_proj->find_out_with(Op_ScopedValueGetLoadFromCache, true);
  assert(load_from_cache->in(1) == hits_in_the_cache, "unexpected ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache shape");
  assert(if_success_proj->is_IfTrue(), "unexpected ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache shape");
  new_predicate_proj = create_new_if_for_predicate(parse_predicate_proj, nullptr,
                                                   reason,
                                                   iff->Opcode());
  Node* ctrl = new_predicate_proj->in(0)->in(0);
  Node* new_bol = bol->clone();
  register_new_node(new_bol, ctrl);
  Node* new_hits_in_the_cache = hits_in_the_cache->clone();
  register_new_node(new_hits_in_the_cache, ctrl);
  _igvn.replace_input_of(load_from_cache, 1, new_hits_in_the_cache);

  CallStaticJavaNode* call = new_predicate_proj->is_uncommon_trap_if_pattern();
  assert(call != nullptr, "Where's the uncommon trap call?");

  Node* all_mem = call->in(TypeFunc::Memory);
  MergeMemNode* mm = all_mem->isa_MergeMem();
  Node* raw_mem = mm != nullptr ? mm->memory_at(Compile::AliasIdxRaw) : all_mem;

  // The scoped value cache may be loop variant because it depends on raw memory which may keep the
  // ScopedValueGetHitsInCache in the loop. It's legal to hoist it out of loop though but we need to update the scoped
  // value cache to be out of loop as well.
  Node* scoped_value_cache_load = make_scoped_value_cache_node(raw_mem);

  _igvn.replace_input_of(new_hits_in_the_cache, 1, scoped_value_cache_load);
  Node* oop_mem = mm != nullptr ? mm->memory_at(C->get_alias_index(TypeAryPtr::OOPS)) : all_mem;
  _igvn.replace_input_of(new_hits_in_the_cache, ScopedValueGetHitsInCacheNode::Memory, oop_mem);
  _igvn.replace_input_of(new_hits_in_the_cache, 0, ctrl);
  _igvn.replace_input_of(new_hits_in_the_cache, ScopedValueGetHitsInCacheNode::ScopedValue,
                         invar.clone(hits_in_the_cache->scoped_value(), ctrl));
  _igvn.replace_input_of(new_hits_in_the_cache, ScopedValueGetHitsInCacheNode::Index1,
                         invar.clone(hits_in_the_cache->index1(), ctrl));
  _igvn.replace_input_of(new_hits_in_the_cache, ScopedValueGetHitsInCacheNode::Index2,
                         invar.clone(hits_in_the_cache->index2(), ctrl));

  _igvn.replace_input_of(new_bol, 1, new_hits_in_the_cache);

  assert(invar.is_invariant(new_bol), "should be loop invariant");

  IfNode* new_predicate_iff = new_predicate_proj->in(0)->as_If();
  _igvn.hash_delete(new_predicate_iff);
  new_predicate_iff->set_req(1, new_bol);
#ifndef PRODUCT
  if (TraceLoopPredicate) {
    tty->print("Predicate invariant if: %d ", new_predicate_iff->_idx);
    loop->dump_head();
  } else if (TraceLoopOpts) {
    tty->print("Predicate IC ");
    loop->dump_head();
  }
#endif
  return true;
}

// It is easier to re-create the cache load subgraph rather than trying to change the inputs of the existing one to move
// it out of loops
Node* PhaseIdealLoop::make_scoped_value_cache_node(Node* raw_mem) {
  Node* thread = new ThreadLocalNode();
  register_new_node(thread, C->root());
  Node* scoped_value_cache_offset = _igvn.MakeConX(in_bytes(JavaThread::scopedValueCache_offset()));
  set_ctrl(scoped_value_cache_offset, C->root());
  Node* p = new AddPNode(C->top(), thread, scoped_value_cache_offset);
  register_new_node(p, C->root());
  Node* handle_load = LoadNode::make(_igvn, nullptr, raw_mem, p, p->bottom_type()->is_ptr(), TypeRawPtr::NOTNULL,
                                     T_ADDRESS, MemNode::unordered);
  _igvn.register_new_node_with_optimizer(handle_load);
  set_subtree_ctrl(handle_load, true);

  ciInstanceKlass* object_klass = ciEnv::current()->Object_klass();
  const TypeOopPtr* etype = TypeOopPtr::make_from_klass(object_klass);
  const TypeAry* arr0 = TypeAry::make(etype, TypeInt::POS);
  const TypeAryPtr* objects_type = TypeAryPtr::make(TypePtr::BotPTR, arr0, nullptr, true, 0);

  DecoratorSet decorators = C2_READ_ACCESS | IN_NATIVE;
  C2AccessValuePtr addr(handle_load, TypeRawPtr::NOTNULL);
  C2OptAccess access(_igvn, nullptr, raw_mem, decorators, T_OBJECT, nullptr, addr);
  BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
  Node* load_of_cache = bs->load_at(access, objects_type);
  set_subtree_ctrl(load_of_cache, true);
  return load_of_cache;
}


// Peeling support

// If a ScopedValueGetResult dominates the back edge, peeling one iteration will allow the elimination of the
// ScopedValue.get() nodes in the loop body.
bool IdealLoopTree::policy_peeling_for_scoped_value(PhaseIdealLoop* phase) {
  uint estimate = estimate_if_peeling_possible(phase);

  if (estimate == 0) {
    return false;
  }

  Node* test = tail();

  while (test != _head) {   // Scan till run off top of loop
    if (test->Opcode() == Op_ScopedValueGetResult &&
        !phase->is_member(this, phase->get_ctrl((test->as_ScopedValueGetResult())->scoped_value()))) {
      return phase->may_require_nodes(estimate);
    }
    // Walk up dominators to loop _head looking for test which is executed on
    // every path through the loop.
    test = phase->idom(test);
  }
  return false;
}

// ScopedValueGetHitsInCache node ended up on the peel list but its companion ScopedValueGetLoadFromCache is not.
// Peeling will separate the two, breaking the expected shape for ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache.
// Move the ScopedValueGetHitsInCache out of the peel list where it doesn't need to be: its uses are in the not_peel
// part of the loop body.
void PhaseIdealLoop::move_scoped_value_nodes_to_avoid_peeling_it(VectorSet& peel, VectorSet& not_peel, Node_List& peel_list,
                                                                 Node_List& sink_list, uint i) {
  ScopedValueGetHitsInCacheNode* hits_in_cache = peel_list.at(i)->as_ScopedValueGetHitsInCache();
  hits_in_cache->verify();
#ifdef ASSERT
  ScopedValueGetLoadFromCacheNode* load_from_cache = hits_in_cache->load_from_cache();
  assert(load_from_cache == nullptr || not_peel.test(load_from_cache->_idx), "unexpected ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache shape");
  Node* bol = hits_in_cache->find_out_with(Op_Bool, true);
  assert(not_peel.test(bol->_idx), "should be in not peel subgraph");
  Node* iff = bol->unique_ctrl_out();
  assert(not_peel.test(iff->_idx), "should be in not peel subgraph");
#endif
  sink_to_not_peel(peel, not_peel, peel_list, sink_list, i);
}

// This handles a pattern that may show up with ScopedValue.get():
//
// if (hits_in_the_cache) {
//   result = load_from_cache;
// } else {
//   if (cache == null) {
//     unc;
//   }
//   if (first_entry_hits) {
//     halt;
//   } else {
//     if (second_entry_hits) {
//        halt;
//      } else {
//        unc;
//     }
//   }
// }
//
// The paths that end with a Halt node are never taken. So in practice, all taken paths end with an uncommon trap. Loop
// predication takes advantage of this, to hoist:
// if (hits_in_the_cache) {
bool ProjNode::is_multi_uncommon_trap_if_pattern() {
  Node* iff = in(0);
  if (!iff->is_If() || iff->outcnt() < 2) {
    // Not a projection of an If or variation of a dead If node.
    return false;
  }
  assert(iff->in(1)->is_Bool() &&
         iff->in(1)->in(1)->Opcode() == Op_ScopedValueGetHitsInCache, "this only makes sense for ScopedValueGetHitsInCache");
  return other_if_proj()->is_multi_uncommon_trap_proj();
}

bool ProjNode::is_multi_uncommon_trap_proj() {
  ResourceMark rm;
  Unique_Node_List wq;
  wq.push(this);
  const int path_limit = 100;
  uint unc_count = 0;
  for (uint i = 0; i < wq.size(); ++i) {
    Node* n = wq.at(i);
    if (n->is_CallStaticJava()) {
      CallStaticJavaNode* call = n->as_CallStaticJava();
      int req = call->uncommon_trap_request();
      if (req == 0) {
        return false;
      }
      unc_count++;
    } else if (n->is_Region() || n->is_If() || n->is_IfProj()) {
      for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
        Node* u = n->fast_out(j);
        if (u->is_CFG()) {
          if (wq.size() >= path_limit) {
            // conservatively return false. Worst case, we won't apply an optimization that we could have applied but
            // correctness can't be affected.
            return false;
          }
          wq.push(u);
        }
      }
    } else if (n->Opcode() != Op_Halt) {
      return false;
    }
  }
  return unc_count > 0;
}

bool ProjNode::returns_pointer_from_call() const {
  return _con == TypeFunc::Parms && in(0)->is_Call() && in(0)->as_Call()->returns_pointer();
}

bool ProjNode::is_result_from_scoped_value_get() const {
  return _con == ScopedValueGetResultNode::Result && in(0)->Opcode() == Op_ScopedValueGetResult;
}

// Support for elimination and expansion of redundant ScopedValue.get() nodes

// Expansion of ScopedValue nodes happen during loop opts because their expansion creates an opportunity for
// further loop optimizations (see comment in LateInlineScopedValueCallGenerator::process_result)
bool PhaseIdealLoop::expand_scoped_value_get_nodes() {
  bool progress = false;
  assert(!_igvn.delay_transform(), "about to delay igvn transform");
  _igvn.set_delay_transform(true);
  while (_scoped_value_get_nodes.size() > 0) {
    Node* n = _scoped_value_get_nodes.pop();
    if (n->Opcode() == Op_ScopedValueGetResult) {
      // Remove the ScopedValueGetResult and its projections entirely
      ScopedValueGetResultNode* get_result = n->as_ScopedValueGetResult();
      Node* result_out_proj = get_result->result_out_or_null();
      Node* result_in = get_result->in(ScopedValueGetResultNode::GetResult);
      if (result_out_proj != nullptr) {
        _igvn.replace_node(result_out_proj, result_in);
      } else {
        _igvn.replace_input_of(get_result, ScopedValueGetResultNode::GetResult, C->top());
      }
      lazy_replace(get_result->control_out(), get_result->in(ScopedValueGetResultNode::Control));
    } else {
      ScopedValueGetHitsInCacheNode* hits_in_cache = n->as_ScopedValueGetHitsInCache();
      expand_sv_get_hits_in_cache_and_load_from_cache(hits_in_cache);
    }
    progress = true;
  }
  _igvn.set_delay_transform(false);
  return progress;
}

// On entry to this, IR shape in pseudo-code:
//
// if (hits_in_the_cache) {
//   result = load_from_cache;
// } else {
//   if (cache == null) {
//     goto slow_call;
//   }
//   if (first_entry_hits) {
//     halt;
//   } else {
//     if (second_entry_hits) {
//        halt;
//      } else {
//        goto slow_call;
//     }
//   }
// }
// continue:
// ...
// return;
//
// slow_call:
// result = slowGet();
// goto continue;
//
// The hits_in_the_cache and load_from_cache are expanded back:
//
// if (cache == null) {
//   goto slow_path;
// }
// if (first_entry_hits) {
//   goto continue;
// } else {
//   if (second_entry_hits) {
//      goto continue;
//    } else {
//      goto slow_path;
//   }
// }
// slow_path:
// if (cache == null) {
//   goto slow_call;
// }
// if (first_entry_hits) {
//   halt;
// } else {
//   if (second_entry_hits) {
//      halt;
//    } else {
//      goto slow_call;
//   }
// }
// continue:
// ...
// return;
//
// slow_call:
// result = slowGet();
// goto continue;
//
// Split if in subsequent loop opts rounds will have a chance to clean the duplicated cache null, first_entry_hits,
// second_entry_hits checks
// The reason for having the duplicate checks is so that, if some checks branch to an uncommon trap, and a trap is hit,
// the right bci in the java method is marked as having trapped.
void PhaseIdealLoop::expand_sv_get_hits_in_cache_and_load_from_cache(ScopedValueGetHitsInCacheNode* hits_in_cache) {
  hits_in_cache->verify();
  BoolNode* bol = hits_in_cache->find_out_with(Op_Bool, true)->as_Bool();
  assert(bol->_test._test == BoolTest::ne, "unexpected ScopedValueGetHitsInCache shape");
  IfNode* iff = bol->find_out_with(Op_If, true)->as_If();
  ProjNode* success = iff->proj_out(1);
  ProjNode* failure = iff->proj_out(0);

  ScopedValueGetLoadFromCacheNode* load_from_cache = hits_in_cache->load_from_cache();
  if (load_from_cache != nullptr) {
    load_from_cache->verify();
  }
  Node* first_index = hits_in_cache->index1();
  Node* second_index = hits_in_cache->index2();

  // The cache was always seen to be null so no code to probe the cache was added to the IR.
  if (first_index == C->top() && second_index == C->top()) {
    Node* zero = _igvn.intcon(0);
    set_ctrl(zero, C->root());
    _igvn.replace_input_of(iff, 1, zero);
    _igvn.replace_node(hits_in_cache, C->top());
    return;
  }

  Node* load_of_cache = hits_in_cache->in(1);

  Node* null_ptr = hits_in_cache->in(2);
  Node* cache_not_null_cmp = new CmpPNode(load_of_cache, null_ptr);
  _igvn.register_new_node_with_optimizer(cache_not_null_cmp);
  Node* cache_not_null_bol = new BoolNode(cache_not_null_cmp, BoolTest::ne);
  _igvn.register_new_node_with_optimizer(cache_not_null_bol);
  set_subtree_ctrl(cache_not_null_bol, true);
  IfNode* cache_not_null_iff = new IfNode(iff->in(0), cache_not_null_bol, hits_in_cache->prob_cache_exists(),
                                          hits_in_cache->cnt_cache_exists());
  IdealLoopTree* loop = get_loop(iff->in(0));
  register_control(cache_not_null_iff, loop, iff->in(0));
  Node* cache_not_null_proj = new IfTrueNode(cache_not_null_iff);
  register_control(cache_not_null_proj, loop, cache_not_null_iff);
  Node* cache_null_proj = new IfFalseNode(cache_not_null_iff);
  register_control(cache_null_proj, loop, cache_not_null_iff);

  Node* not_null_load_of_cache = new CastPPNode(cache_not_null_proj, load_of_cache, _igvn.type(load_of_cache)->join(TypePtr::NOTNULL));
  register_new_node(not_null_load_of_cache, cache_not_null_proj);

  Node* mem = hits_in_cache->mem();

  Node* sv = hits_in_cache->scoped_value();
  Node* hit_proj = nullptr;
  Node* failure_proj = nullptr;
  Node* res = nullptr;
  Node* success_region = new RegionNode(3);
  Node* success_phi = new PhiNode(success_region, TypeInstPtr::BOTTOM);
  Node* failure_region = new RegionNode(3);
  float prob_cache_miss_at_first_if;
  float first_if_cnt;
  float prob_cache_miss_at_second_if;
  float second_if_cnt;
  find_most_likely_cache_index(hits_in_cache, first_index, second_index, prob_cache_miss_at_first_if, first_if_cnt,
                               prob_cache_miss_at_second_if,
                               second_if_cnt);

  test_and_load_from_cache(not_null_load_of_cache, mem, first_index, cache_not_null_proj,
                           prob_cache_miss_at_first_if, first_if_cnt, sv, failure_proj, hit_proj, res);
  Node* success_region_dom = hit_proj;
  success_region->init_req(1, hit_proj);
  success_phi->init_req(1, res);
  if (second_index != C->top()) {
    test_and_load_from_cache(not_null_load_of_cache, mem, second_index, failure_proj,
                             prob_cache_miss_at_second_if, second_if_cnt, sv, failure_proj, hit_proj, res);
    success_region->init_req(2, hit_proj);
    success_phi->init_req(2, res);
    success_region_dom = success_region_dom->in(0);
  }

  failure_region->init_req(1, cache_null_proj);
  failure_region->init_req(2, failure_proj);

  register_control(success_region, loop, success_region_dom);
  register_control(failure_region, loop, cache_not_null_iff);
  register_new_node(success_phi, success_region);

  Node* failure_path = failure->unique_ctrl_out();

  lazy_replace(success, success_region);
  lazy_replace(failure, failure_region);
  if (load_from_cache != nullptr) {
    _igvn.replace_node(load_from_cache, success_phi);
  }
  _igvn.replace_node(hits_in_cache, C->top());
  lazy_update(iff, cache_not_null_iff);
}

// Java code for ScopedValue.get() probes a first cache location and in case of a miss, a second one. We should have
// probabilities for both tests. If the second location is more likely than the first one, have it be tested first.
void PhaseIdealLoop::find_most_likely_cache_index(const ScopedValueGetHitsInCacheNode* hits_in_cache, Node*& first_index,
                                                  Node*& second_index, float& prob_cache_miss_at_first_if,
                                                  float& first_if_cnt, float& prob_cache_miss_at_second_if,
                                                  float& second_if_cnt) const {
  prob_cache_miss_at_first_if= hits_in_cache->prob_first_cache_probe_fails();
  first_if_cnt= hits_in_cache->cnt_first_cache_probe_fails();
  prob_cache_miss_at_second_if= hits_in_cache->prob_second_cache_probe_fails();
  second_if_cnt= hits_in_cache->cnt_second_cache_probe_fails();
  if (prob_cache_miss_at_first_if != PROB_UNKNOWN && prob_cache_miss_at_second_if != PROB_UNKNOWN) {
    float prob_cache_miss_at_first_index = prob_cache_miss_at_first_if;
    float prob_cache_hit_at_second_if = 1 - prob_cache_miss_at_second_if;
    // Compute the probability of a hit in the second location. We have the probability that the test at the second
    // location fails once the test at the first location has failed.
    float prob_cache_hit_at_second_index = prob_cache_miss_at_first_if * prob_cache_hit_at_second_if;
    float prob_cache_miss_at_second_index = 1 - prob_cache_hit_at_second_index;
    if (second_index != C->top() && prob_cache_miss_at_second_index < prob_cache_miss_at_first_index) {
      // The second location is more likely to lead to a hit than the first one. Have it be tested first.
      swap(first_index, second_index);
      swap(prob_cache_miss_at_first_index, prob_cache_miss_at_second_index);
      prob_cache_miss_at_first_if = prob_cache_miss_at_first_index;
      prob_cache_hit_at_second_index = 1 - prob_cache_miss_at_second_index;
      prob_cache_hit_at_second_if = prob_cache_hit_at_second_index / prob_cache_miss_at_first_if;
      prob_cache_miss_at_second_if = 1 - prob_cache_hit_at_second_if;
      if (first_if_cnt != COUNT_UNKNOWN) {
        second_if_cnt = first_if_cnt * prob_cache_miss_at_first_if;
      }
    }
  }
}

void PhaseIdealLoop::test_and_load_from_cache(Node* load_of_cache, Node* mem, Node* index, Node* c, float prob, float cnt,
                                              Node* sv, Node*& failure, Node*& hit, Node*& res) {
  BasicType bt = TypeAryPtr::OOPS->array_element_basic_type();
  uint shift  = exact_log2(type2aelembytes(bt));
  uint header = arrayOopDesc::base_offset_in_bytes(bt);

  Node* header_offset = _igvn.MakeConX(header);
  set_ctrl(header_offset, C->root());
  Node* base  = new AddPNode(load_of_cache, load_of_cache, header_offset);
  _igvn.register_new_node_with_optimizer(base);
  Node* casted_idx = Compile::conv_I2X_index(&_igvn, index, nullptr, c);
  ConINode* shift_node = _igvn.intcon(shift);
  set_ctrl(shift_node, C->root());
  Node* scale = new LShiftXNode(casted_idx, shift_node);
  _igvn.register_new_node_with_optimizer(scale);
  Node* adr = new AddPNode(load_of_cache, base, scale);
  _igvn.register_new_node_with_optimizer(adr);

  DecoratorSet decorators = C2_READ_ACCESS | IN_HEAP | IS_ARRAY | C2_CONTROL_DEPENDENT_LOAD;
  C2AccessValuePtr addr(adr, TypeAryPtr::OOPS);
  C2OptAccess access(_igvn, c, mem, decorators, bt, load_of_cache, addr);
  BarrierSetC2* bs = BarrierSet::barrier_set()->barrier_set_c2();
  Node* cache_load = bs->load_at(access, TypeAryPtr::OOPS->elem());

  Node* cmp = new CmpPNode(cache_load, sv);
  _igvn.register_new_node_with_optimizer(cmp);
  Node* bol = new BoolNode(cmp, BoolTest::ne);
  _igvn.register_new_node_with_optimizer(bol);
  set_subtree_ctrl(bol, true);
  IfNode* iff = new IfNode(c, bol, prob, cnt);
  IdealLoopTree* loop = get_loop(c);
  register_control(iff, loop, c);
  failure = new IfTrueNode(iff);
  register_control(failure, loop, iff);
  hit = new IfFalseNode(iff);
  register_control(hit, loop, iff);

  index = new AddINode(index, _igvn.intcon(1));
  _igvn.register_new_node_with_optimizer(index);
  casted_idx = Compile::conv_I2X_index(&_igvn, index, nullptr, hit);
  scale = new LShiftXNode(casted_idx, shift_node);
  _igvn.register_new_node_with_optimizer(scale);
  adr = new AddPNode(load_of_cache, base, scale);
  _igvn.register_new_node_with_optimizer(adr);
  C2AccessValuePtr addr_res(adr, TypeAryPtr::OOPS);
  C2OptAccess access_res(_igvn, c, mem, decorators, bt, load_of_cache, addr_res);
  res = bs->load_at(access_res, TypeAryPtr::OOPS->elem());
  set_subtree_ctrl(res, true);
}

bool PhaseIdealLoop::optimize_scoped_value_get_nodes() {
  bool progress = false;
  // Iterate in reverse order so we can remove the element we're processing from the `_scoped_value_get_nodes` list.
  for (uint i = _scoped_value_get_nodes.size(); i > 0; i--) {
    Node* n = _scoped_value_get_nodes.at(i - 1);
    // Look for a node that dominates n and can replace it.
    for (uint j = 0; j < _scoped_value_get_nodes.size(); j++) {
      Node* m = _scoped_value_get_nodes.at(j);
      if (m == n) {
        continue;
      }

      if (hits_in_cache_replaced_by_dominating_hits_in_cache(n, m) ||
          hits_in_cache_replaced_by_dominating_get_result(n, m) ||
          get_result_replaced_by_dominating_hits_in_cache(n, m) ||
          get_result_replaced_by_dominating_get_result(n, m)) {
        _scoped_value_get_nodes.delete_at(i - 1);
        progress = true;
        break;
      }
    }
  }
  return progress;
}

bool PhaseIdealLoop::hits_in_cache_replaced_by_dominating_hits_in_cache(Node* n, Node* m) {
  if (!n->is_ScopedValueGetHitsInCache() || !m->is_ScopedValueGetHitsInCache()) {
    return false;
  }
  ScopedValueGetHitsInCacheNode* hits_in_cache = n->as_ScopedValueGetHitsInCache();
  hits_in_cache->verify();
  ScopedValueGetLoadFromCacheNode* load_from_cache = hits_in_cache->load_from_cache();
  if (load_from_cache != nullptr) {
    load_from_cache->verify();
  }
  IfNode* iff = hits_in_cache->success_proj()->in(0)->as_If();
  ScopedValueGetHitsInCacheNode* hits_in_cache_dom = m->as_ScopedValueGetHitsInCache();
  ScopedValueGetLoadFromCacheNode* load_from_cache_dom = hits_in_cache_dom->load_from_cache();
  IfProjNode* dom_proj = hits_in_cache_dom->success_proj();
  if (hits_in_cache_dom->scoped_value() != hits_in_cache->scoped_value() ||
      !is_dominator(dom_proj, iff)) {
    return false;
  }
  // The success projection of a dominating ScopedValueGetHitsInCache dominates this ScopedValueGetHitsInCache
  // for the same ScopedValue object: replace this ScopedValueGetHitsInCache by the dominating one
  _igvn.replace_node(hits_in_cache, hits_in_cache_dom);
  if (load_from_cache_dom != nullptr && load_from_cache != nullptr) {
    _igvn.replace_node(load_from_cache, load_from_cache_dom);
  }
  Node* bol = iff->in(1);
  dominated_by(dom_proj, iff, false, false);
  _igvn.replace_node(bol, C->top());

  return true;
}

bool PhaseIdealLoop::hits_in_cache_replaced_by_dominating_get_result(Node* n, Node* m) {
  if (!n->is_ScopedValueGetHitsInCache() || !m->is_ScopedValueGetResult()) {
    return false;
  }
  ScopedValueGetHitsInCacheNode* hits_in_cache = n->as_ScopedValueGetHitsInCache();
  hits_in_cache->verify();
  ScopedValueGetLoadFromCacheNode* load_from_cache = hits_in_cache->load_from_cache();
  if (load_from_cache != nullptr) {
    load_from_cache->verify();
  }
  IfNode* iff = hits_in_cache->success_proj()->in(0)->as_If();
  ScopedValueGetResultNode* get_result_dom = m->as_ScopedValueGetResult();
  if (get_result_dom->scoped_value() != hits_in_cache->scoped_value() ||
      !is_dominator(get_result_dom, iff)) {
    return false;
  }
  // A ScopedValueGetResult dominates this ScopedValueGetHitsInCache for the same ScopedValue object:
  // the result of the dominating ScopedValue.get() makes this ScopedValueGetHitsInCache useless
  Node* one = _igvn.intcon(1);
  set_ctrl(one, C->root());
  _igvn.replace_input_of(iff, 1, one);
  if (load_from_cache != nullptr) {
    Node* result_out = get_result_dom->result_out_or_null();
    if (result_out == nullptr) {
      result_out = new ProjNode(get_result_dom, ScopedValueGetResultNode::Result);
      register_new_node(result_out, get_result_dom);
    }
    _igvn.replace_node(load_from_cache, result_out);
  }
  _igvn.replace_node(hits_in_cache, C->top());

  return true;
}

bool PhaseIdealLoop::get_result_replaced_by_dominating_hits_in_cache(Node* n, Node* m) {
  if (!n->is_ScopedValueGetResult() || !m->is_ScopedValueGetHitsInCache()) {
    return false;
  }
  ScopedValueGetResultNode* get_result = n->as_ScopedValueGetResult();
  ScopedValueGetHitsInCacheNode* hits_in_cache_dom = m->as_ScopedValueGetHitsInCache();
  IfProjNode* dom_proj = hits_in_cache_dom->success_proj();
  if (replace_scoped_value_result_by_dominator(get_result, hits_in_cache_dom->scoped_value(), dom_proj)) {
    // This ScopedValueGetResult is dominated by the success projection of ScopedValueGetHitsInCache for the same
    // ScopedValue object: either the ScopedValueGetResult and ScopedValueGetHitsInCache are from the same
    // ScopedValue.get() and we remove the ScopedValueGetResult because it's only useful to optimize
    // ScopedValue.get() where the slow path is taken. Or they are from different ScopedValue.get() and we
    // remove the ScopedValueGetResult. Its companion ScopedValueGetHitsInCache should be removed as well as part
    // of this round of optimizations.
    return true;
  }
  return false;
}

bool PhaseIdealLoop::get_result_replaced_by_dominating_get_result(Node* n, Node* m) {
  if (!n->is_ScopedValueGetResult() || !m->is_ScopedValueGetResult()) {
    return false;
  }
  ScopedValueGetResultNode* get_result = n->as_ScopedValueGetResult();
  ScopedValueGetResultNode* get_result_dom = m->as_ScopedValueGetResult();
  if (replace_scoped_value_result_by_dominator(get_result, get_result_dom->scoped_value(), get_result_dom)) {
    // This ScopedValueGetResult is dominated by another ScopedValueGetResult for the same ScopedValue object:
    // remove this one and use the result from the dominating ScopedValue.get()
    return true;
  }
  return false;
}

bool PhaseIdealLoop::replace_scoped_value_result_by_dominator(ScopedValueGetResultNode* get_result, Node* scoped_value_object, Node* dom_ctrl) {
  if (scoped_value_object == get_result->scoped_value() &&
      is_dominator(dom_ctrl, get_result)) {
    lazy_replace(get_result->control_out(), get_result->in(0));
    ProjNode* result_out = get_result->result_out_or_null();
    if (result_out != nullptr) {
      _igvn.replace_node(result_out, get_result->in(ScopedValueGetResultNode::GetResult));
    }
    return true;
  }
  return false;
}
