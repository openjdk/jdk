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

#ifndef SHARE_OPTO_SCOPED_VALUE_HPP
#define SHARE_OPTO_SCOPED_VALUE_HPP

#include "opto/multnode.hpp"
#include "opto/node.hpp"
#include "opto/subnode.hpp"

// Optimizations for calls to ScopedValue.get(). Indeed, in:
//
// v1 = scopedValue.get();
// ...
// v2 = scopedValue.get();
//
// v2 can be replaced by v1 and the second call to get() can be
// optimized out. That's true whatever is between the 2 calls unless a
// new mapping for scopedValue is created in between (when that
// happens no optimizations is performed for the method being
// compiled). Hoisting a get() call out of loop for a loop invariant
// scopedValue should also be legal in most cases.
//
// ScopedValue.get() is implemented in java code as a 2 step
// process. A cache is attached to the current thread object. If the
// ScopedValue object is in the cache then the result from get() is
// read from there. Otherwise a slow call is performed that also
// inserts the mapping in the cache. The cache itself is lazily
// allocated. One ScopedValue can be hashed to 2 different indexes in
// the cache. On a cache probe, both indexes are checked. As a
// consequence, the process of probing the cache is a multi step
// process (check if the cache is present, check first index, check
// second index if first index failed). If the cache is populated
// early on, then when the method that calls ScopedValue.get() is
// compiled, profile reports the slow path as never taken and only the
// read from the cache is compiled.
//
// 3 ScopedValue.get() specific nodes are used to support
// optimizations:
//
// - the pair ScopedValueGetHitsInCacheNode/ScopedValueGetLoadFromCacheNode
//   for the cache probe
//
// - a cfg node ScopedValueGetResultNode to help locate the result of the
//   get() call in the IR graph.
//
// In pseudo code, once the nodes are inserted, the code of a get() is:
//
//  hits_in_the_cache = ScopedValueGetHitsInCache(scopedValue)
//  if (hits_in_the_cache) {
//    res = ScopedValueGetLoadFromCache(hits_in_the_cache);
//  } else {
//    res = ..; //slow call possibly inlined. Subgraph can be arbitray complex
//  }
//  res = ScopedValueGetResult(res)
//
// In the snippet above, Replacing v2 by v1 is then done by starting
// from the ScopedValueGetResult node for the second get() and looking
// for a dominating ScopedValueGetResult for the same ScopedValue
// object. When one is found, it is used as a replacement. Eliminating
// the second get() call is achieved by making
// ScopedValueGetHitsInCache always successful if there's a dominating
// ScopedValueGetResult and replacing its companion
// ScopedValueGetLoadFromCache by the dominating ScopedValueGetResult.
//
// Hoisting a get() out of loop is achieved by peeling one iteration
// of the loop. The optimization above then finds a dominating get()
// and removed the get() from the loop body.
//
// An important case, is when profile predicts the slow case to never
// taken. Then the code of get() is:
//
// hits_in_the_cache = ScopedValueGetHitsInCache(scopedValue)
// if (hits_in_the_cache) {
//    res = ScopedValueGetLoadFromCache(hits_in_the_cache);
// } else {
//   trap();
// }
// res = ScopedValueGetResult(res);
//
// The ScopedValueGetResult doesn't help and is removed early one. The
// optimization process then looks for a pair of
// ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache that
// dominates the current pair of
// ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache and can
// replace them. In that case, hoisting a ScopedValue.get() can be
// done by predication.
//
// Adding the new nodes to the graph when a ScopedValue.get() call is
// encountered is done in several steps:
//
// 1- inlining of ScopedValue.get() is delayed and the call is
// enqueued for late inlining.
//
// 2- Once the graph is fully constructed, for each call to
// ScopedValue.get(), a ScopedValueGetResult is added between the
// result of the call and its uses.
//
// 3- the call is then inlined by parsing the ScopedValue.get() method
//
// 4- finally the subgraph that results is pattern matched and the
// pieces required to perform the cache probe are extracted and
// attached to new
// ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache nodes
//
// There are a couple of reasons for steps 3 and 4:
//
// - Probing the cache is a multi step process. Having only 2 nodes in
//   a simple graph shape to represent it makes it easier to write
//   robust optimizations
//
// - The subgraph for the method after parsing contains valuable
//   pieces of information: profile data that captures which of the 2
//   locations in the cache is the most likely to causee a
//   hit. Profile data is attached to the nodes.
//
// Removal of redundant nodes is done during loop opts. The
// ScopedValue nodes are then expanded. That also happens during loop
// opts because once expansion is over, there are opportunities for
// further optimizations/clean up that can only happens during loop
// opts. During expansion, ScopedValueGetResult nodes are removed and
// ScopedValueGetHitsInCache/ScopedValueGetLoadFromCache are expanded
// to the multi step process of probing the cache. Profile data
// attached to the nodes are used to assign correct frequencies/counts
// to the If nodes. Of the 2 locations in the cache that are tested,
// the one that's the most likely to see a hit (from profile data) is
// done first.

class ScopedValueGetPatternMatcher : public StackObj {
 private:
  GraphKit& _kit;
  Node* _scoped_value_object;
  CallNode* _scoped_value_cache; // call to Thread.scopedValueCache()
  IfNode* _cache_not_null_iff; // test that scopedValueCache() is not null
  IfNode* _first_cache_probe_iff; // test for a hit in the cache with first hash
  IfNode* _second_cache_probe_iff; // test for a hit in the cache with second hash
  Node* _first_index_in_cache; // index in the cache for first hash
  Node* _second_index_in_cache; // index in the cache for second hash
  CallStaticJavaNode* _slow_call; // slowGet() call if any

  bool match_cache_null_check_with_input(Node* maybe_cache, Node* maybe_nullptr, IfNode* iff);
  bool match_cache_null_check(Node* maybe_iff);
  bool match_cache_probe(Node* maybe_iff);
  void adjust_order_of_first_and_second_probe_if(const Unique_Node_List &scoped_value_get_subgraph);
  void remove_first_probe_if_when_it_never_hits();
  void pattern_match();

 public:
  ScopedValueGetPatternMatcher(GraphKit& kit, Node* scoped_value_object) :
          _kit(kit),
          _scoped_value_object(scoped_value_object),
          _scoped_value_cache(nullptr),
          _cache_not_null_iff(nullptr),
          _first_cache_probe_iff(nullptr),
          _second_cache_probe_iff(nullptr),
          _first_index_in_cache(nullptr),
          _second_index_in_cache(nullptr),
          _slow_call(nullptr)
  {
    pattern_match();
    assert(_scoped_value_cache != nullptr, "must have found Thread.scopedValueCache() call");
  }
  NONCOPYABLE(ScopedValueGetPatternMatcher);

  CallNode* scoped_value_cache() const {
    return _scoped_value_cache;
  }

  IfNode* cache_not_null_iff() const {
    return _cache_not_null_iff;
  }

  IfNode* first_cache_probe_iff() const {
    return _first_cache_probe_iff;
  }

  IfNode* second_cache_probe_iff() const {
    return _second_cache_probe_iff;
  }

  Node* first_index_in_cache() const {
    return _first_index_in_cache;
  }

  Node* second_index_in_cache() const {
    return _second_index_in_cache;
  }

  CallStaticJavaNode* slow_call() const {
    return _slow_call;
  }
};

class ScopedValueTransformer : public StackObj {
 private:
  GraphKit& _kit;
  Node* _scoped_value_object;
  const ScopedValueGetPatternMatcher& _pattern_matcher;

  void transform_get_subgraph();

  float canonical_if_prob(IfNode* iff) const {
    if (iff == nullptr) {
      return 0;
    }
    return iff->canonical_prob();
  }

  float if_cnt(IfNode* iff) const {
    if (iff == nullptr) {
      return 0;
    }
    return iff->_fcnt;
  }

  void remove_scoped_value_cache_call(Node* not_in_cache, Node* scoped_value_cache_load) const;
  void replace_current_exit_of_get_with_halt() const;
  static void reset_iff_prob_and_cnt(IfNode* iff, bool expected, float cnt);

 public:
  ScopedValueTransformer(GraphKit& kit, Node* scopedValueObject, const ScopedValueGetPatternMatcher &patternMatcher) :
          _kit(kit), _scoped_value_object(scopedValueObject), _pattern_matcher(patternMatcher) {
    transform_get_subgraph();
  }
  NONCOPYABLE(ScopedValueTransformer);
};

// The result from a successful load from the ScopedValue cache. Goes in pair with ScopedValueGetHitsInCache
class ScopedValueGetLoadFromCacheNode : public Node {
public:
  ScopedValueGetLoadFromCacheNode(Compile* C, Node* ctrl, Node* hits_in_cache)
          : Node(ctrl, hits_in_cache) {
    init_class_id(Class_ScopedValueGetLoadFromCache);
  }

  Node* scoped_value() const;
  IfNode* iff() const;

  virtual int Opcode() const;

  const Type* bottom_type() const {
    return TypeInstPtr::BOTTOM;
  }

  void verify() const NOT_DEBUG_RETURN;
};

// The result of a ScopedValue.get()
class ScopedValueGetResultNode : public  MultiNode {
public:
  enum {
      Control = 0,
      ScopedValue, // which ScopedValue object is this for?
      GetResult // subgraph that produces the result
  };
  enum {
      ControlOut = 0,
      Result // The ScopedValue.get() result
  };
  ScopedValueGetResultNode(Compile* C, Node* ctrl, Node* sv, Node* res) : MultiNode(3) {
    init_req(Control, ctrl);
    init_req(ScopedValue, sv);
    init_req(GetResult, res);
    init_class_id(Class_ScopedValueGetResult);
  }
  virtual int   Opcode() const;
  virtual const Type* bottom_type() const { return TypeTuple::SV_GET_RESULT; }

  ProjNode* result_out_or_null() {
    return proj_out_or_null(Result);
  }

  ProjNode* control_out() {
    return proj_out(ControlOut);
  }

  Node* scoped_value() const {
    return in(ScopedValue);
  }
  Node* result_in() const {
    return in(GetResult);
  }

  const Type* Value(PhaseGVN* phase) const;
};

// Does a ScopedValue.get() hits in the cache?
// This node returns true in case of cache hit (cache reference not null, and at least one of the indices leads to a hit).
class ScopedValueGetLoadFromCacheNode;
class ScopedValueGetHitsInCacheNode : public CmpNode {
private:
  // There are multiple checks involved, keep track of their profile data
  struct ProfileData {
      float _cnt;
      float _prob;
  };
  ProfileData _cache_exists;
  ProfileData _first_cache_probe_fails;
  ProfileData _second_cache_probe_fails;

  virtual uint size_of() const { return sizeof(*this); }
  uint hash() const { return NO_HASH; }

public:
  enum {
      ScopedValue = 3, // What ScopedValue object is it for?
      Memory, // Memory for the cache loads
      Index1, // index for the first check
      Index2  // index for the second check
  };

  ScopedValueGetHitsInCacheNode(Compile* C, Node* c, Node* scoped_value_cache, Node* null_con, Node* mem, Node* sv,
                                Node* index1, Node* index2, float cnt_cache_exists, float prob_cache_exists,
                                float cnt_first_cache_probe_fails, float prob_first_cache_probe_fails,
                                float cnt_second_cache_probe_fails, float prob_second_cache_probe_fails) :
          CmpNode(scoped_value_cache, null_con),
          _cache_exists({cnt_cache_exists, prob_cache_exists }),
          _first_cache_probe_fails({cnt_first_cache_probe_fails, prob_first_cache_probe_fails }),
          _second_cache_probe_fails({cnt_second_cache_probe_fails, prob_second_cache_probe_fails }) {
    init_class_id(Class_ScopedValueGetHitsInCache);
    init_req(0, c);
    assert(req() == ScopedValue, "wrong number of inputs for ScopedValueGetHitsInCacheNode");
    add_req(sv);
    assert(req() == Memory, "wrong number of inputs for ScopedValueGetHitsInCacheNode");
    add_req(mem);
    assert(req() == Index1, "wrong number of inputs for ScopedValueGetHitsInCacheNode");
    add_req(index1);
    assert(req() == Index2, "wrong number of inputs for ScopedValueGetHitsInCacheNode");
    add_req(index2);
  }

  Node* scoped_value() const {
    return in(ScopedValue);
  }

  Node* mem() const {
    return in(Memory);
  }

  Node* index1() const {
    return in(Index1);
  }

  Node* index2() const {
    return in(Index2);
  }

  ScopedValueGetLoadFromCacheNode* load_from_cache() const {
    return (ScopedValueGetLoadFromCacheNode*)find_out_with(Op_ScopedValueGetLoadFromCache, true);
  }

  virtual int Opcode() const;

  const Type* sub(const Type* type, const Type* type1) const {
    return CmpNode::bottom_type();
  }

  float prob_cache_exists() const {
    return _cache_exists._prob;
  }

  float cnt_cache_exists() const {
    return _cache_exists._cnt;
  }

  float prob_first_cache_probe_fails() const {
    return _first_cache_probe_fails._prob;
  }

  float cnt_first_cache_probe_fails() const {
    return _first_cache_probe_fails._cnt;
  }

  float prob_second_cache_probe_fails() const {
    return _second_cache_probe_fails._prob;
  }

  float cnt_second_cache_probe_fails() const {
    return _second_cache_probe_fails._cnt;
  }

  IfProjNode* success_proj() const;

  void verify() const NOT_DEBUG_RETURN;

  virtual bool depends_only_on_test() const {
    return false;
  }
};

#endif // SHARE_OPTO_SCOPED_VALUE_HPP
