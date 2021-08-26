/*
 * Copyright (c) 2021, Huawei and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP
#define SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP

#include "memory/iterator.hpp"
#include "oops/oop.hpp"

// This class
//   1. records the objects per region which have failed to evacuate.
//   2. speeds up removing self forwarded ptrs in post evacuation phase.
//
class G1EvacuationFailureObjsInHR {
  template<uint32_t LEN, typename Elem>
  class Array;


  // === Node ===

  template<uint32_t LEN, typename Elem>
  class Node : public CHeapObj<mtGC>{
    friend G1EvacuationFailureObjsInHR;
    friend Array<LEN, Elem>;

  private:
    static const uint32_t LENGTH = LEN;
    static const size_t SIZE = LENGTH * sizeof(Elem);
    Elem* _oop_offsets;

  public:
    Node() {
      _oop_offsets = (Elem*)AllocateHeap(SIZE, mtGC);
    }
    Elem& operator[] (size_t idx) {
      return _oop_offsets[idx];
    }
    static Node<LEN, Elem>* create_node() {
      return new Node<LEN, Elem>();
    }
    static void free_node(Node<LEN, Elem>* node) {
      assert(node != NULL, "must be");
      FreeHeap(node->_oop_offsets);
      delete node;
    }
  };


  // === Array ===

  template<uint32_t NODE_SIZE, typename Elem>
  class Array : public CHeapObj<mtGC> {
  public:
    typedef Node<NODE_SIZE, Elem> NODE_XXX;

  private:
    const uint64_t low_mask;
    const uint64_t high_mask;
    const uint32_t _max_nodes_length;

    volatile uint64_t _cur_pos;
    NODE_XXX* volatile * _nodes;
    volatile uint _elements_num;

  private:
    uint64_t low(uint64_t n) {
      return (n & low_mask);
    }
    uint64_t high(uint64_t n) {
      return (n & high_mask);
    }
    uint32_t elem_index(uint64_t n) {
      assert(low(n) < NODE_XXX::LENGTH, "must be");
      return low(n);
    }
    uint32_t node_index(uint64_t n) {
      uint32_t hi = high(n) >> 32;
      assert(hi < _max_nodes_length, "must be");
      return hi;
    }

    uint64_t next(uint64_t n) {
      uint64_t lo = low(n);
      uint64_t hi = high(n);
      assert((lo < NODE_XXX::LENGTH) && (NODE_XXX::LENGTH <= low_mask), "must be");
      assert(hi < high_mask, "must be");
      if ((lo+1) == NODE_XXX::LENGTH) {
        lo = 0;
        hi += ((uint64_t)1 << 32);
      } else {
        lo++;
      }
      assert(hi <= high_mask, "must be");
      return hi | lo;
    }

  public:
    Array(uint32_t max_nodes_length) :
      low_mask(((uint64_t)1 << 32) - 1),
      high_mask(low_mask << 32),
      _max_nodes_length(max_nodes_length) {

      _nodes = (NODE_XXX**)AllocateHeap(_max_nodes_length * sizeof(NODE_XXX*), mtGC);
      for (uint32_t i = 0; i < _max_nodes_length; i++) {
        Atomic::store(&_nodes[i], (NODE_XXX *)NULL);
      }

      Atomic::store(&_elements_num, 0u);
      Atomic::store(&_cur_pos, (uint64_t)0);
    }

    ~Array() {
      assert(_nodes != NULL, "must be");
      reset();
      FreeHeap((NODE_XXX**)_nodes);
    }

    uint objs_num() {
      return Atomic::load(&_elements_num);
    }

    void add(Elem elem) {
      while (true) {
        uint64_t pos = Atomic::load(&_cur_pos);
        uint64_t next_pos = next(pos);
        uint64_t res = Atomic::cmpxchg(&_cur_pos, pos, next_pos);
        if (res == pos) {
          uint32_t hi = node_index(pos);
          uint32_t lo = elem_index(pos);
          if (lo == 0) {
            Atomic::store(&_nodes[hi], NODE_XXX::create_node());
          }
          NODE_XXX* node = NULL;
          while ((node = Atomic::load(&_nodes[hi])) == NULL);

          node->operator[](lo) = elem;
          Atomic::inc(&_elements_num);
          break;
        }
      }
    }

    template<typename VISITOR>
    void iterate_elements(VISITOR v) {
      int64_t pos = Atomic::load(&_cur_pos);
      DEBUG_ONLY(uint total = 0);
      uint32_t hi = node_index(pos);
      uint32_t lo = elem_index(pos);
      for (uint32_t i = 0; i <= hi; i++) {
        uint32_t limit = (i == hi) ? lo : NODE_XXX::LENGTH;
        NODE_XXX* node = Atomic::load(&_nodes[i]);
        for (uint32_t j = 0; j < limit; j++) {
          v->visit(node->operator[](j));
          DEBUG_ONLY(total++);
        }
      }
      assert(total == Atomic::load(&_elements_num), "must be");
    }

    template<typename VISITOR>
    void iterate_nodes(VISITOR v) {
      int64_t pos = Atomic::load(&_cur_pos);
      uint32_t hi = node_index(pos);
      uint32_t lo = elem_index(pos);
      for (uint32_t i = 0; i <= hi; i++) {
        NODE_XXX* node = Atomic::load(&_nodes[i]);
        uint32_t limit = (i == hi) ? lo : NODE_XXX::LENGTH;
        if (limit == 0) {
          break;
        }
        v->visit(node, limit);
      }
    }

    void reset() {
      int64_t pos = Atomic::load(&_cur_pos);
      uint32_t hi = node_index(pos);
      uint32_t lo = elem_index(pos);
      for (uint32_t i = 0; i <= hi; i++) {
        NODE_XXX* node = Atomic::load(&_nodes[i]);
        assert(node != NULL || ((i == hi) && (lo == 0)), "must be");
        if (node == NULL) {
          break;
        }
        NODE_XXX::free_node(node);
        Atomic::store(&_nodes[i], (NODE_XXX *)NULL);
      }
      Atomic::store(&_elements_num, 0u);
      Atomic::store(&_cur_pos, (uint64_t)0);
    }
  };


  // === G1EvacuationFailureObjsInHR ===

public:
  typedef uint32_t Elem;

private:
  static const uint32_t NODE_LENGTH = 256;
  const Elem _max_offset;
  const uint _region_idx;
  const HeapWord* _bottom;
  Array<NODE_LENGTH, Elem> _nodes_array;
  Elem* _offset_array;
  uint _objs_num;

private:
  oop cast_from_offset(Elem offset) {
    return cast_to_oop(_bottom + offset);
  }
  Elem cast_from_oop_addr(oop obj) {
    const HeapWord* o = cast_from_oop<const HeapWord*>(obj);
    size_t offset = pointer_delta(o, _bottom);
    return static_cast<Elem>(offset);
  }
  void visit(Elem);
  void visit(Array<NODE_LENGTH, Elem>::NODE_XXX* node, uint32_t limit);
  void compact();
  void sort();
  void clear_array();
  void iterate_internal(ObjectClosure* closure);

public:
  G1EvacuationFailureObjsInHR(uint region_idx, HeapWord* bottom);
  ~G1EvacuationFailureObjsInHR();

  void record(oop obj);
  void iterate(ObjectClosure* closure);
};


#endif //SHARE_GC_G1_G1EVACUATIONFAILUREOBJSINHR_HPP
