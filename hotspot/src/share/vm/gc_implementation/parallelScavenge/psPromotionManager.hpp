/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

//
// psPromotionManager is used by a single thread to manage object survival
// during a scavenge. The promotion manager contains thread local data only.
//
// NOTE! Be carefull when allocating the stacks on cheap. If you are going
// to use a promotion manager in more than one thread, the stacks MUST be
// on cheap. This can lead to memory leaks, though, as they are not auto
// deallocated.
//
// FIX ME FIX ME Add a destructor, and don't rely on the user to drain/flush/deallocate!
//

// Move to some global location
#define HAS_BEEN_MOVED 0x1501d01d
// End move to some global location

class MutableSpace;
class PSOldGen;
class ParCompactionManager;

#define PS_PM_STATS         0

class PSPromotionManager : public CHeapObj {
  friend class PSScavenge;
  friend class PSRefProcTaskExecutor;
 private:
  static PSPromotionManager**         _manager_array;
  static OopStarTaskQueueSet*         _stack_array_depth;
  static OopTaskQueueSet*             _stack_array_breadth;
  static PSOldGen*                    _old_gen;
  static MutableSpace*                _young_space;

#if PS_PM_STATS
  uint                                _total_pushes;
  uint                                _masked_pushes;

  uint                                _overflow_pushes;
  uint                                _max_overflow_length;

  uint                                _arrays_chunked;
  uint                                _array_chunks_processed;

  uint                                _total_steals;
  uint                                _masked_steals;

  void print_stats(uint i);
  static void print_stats();
#endif // PS_PM_STATS

  PSYoungPromotionLAB                 _young_lab;
  PSOldPromotionLAB                   _old_lab;
  bool                                _young_gen_is_full;
  bool                                _old_gen_is_full;
  PrefetchQueue                       _prefetch_queue;

  OopStarTaskQueue                    _claimed_stack_depth;
  OverflowTaskQueue<oop>              _claimed_stack_breadth;

  bool                                _depth_first;
  bool                                _totally_drain;
  uint                                _target_stack_size;

  uint                                _array_chunk_size;
  uint                                _min_array_size_for_chunking;

  // Accessors
  static PSOldGen* old_gen()         { return _old_gen; }
  static MutableSpace* young_space() { return _young_space; }

  inline static PSPromotionManager* manager_array(int index);
  template <class T> inline void claim_or_forward_internal_depth(T* p);
  template <class T> inline void claim_or_forward_internal_breadth(T* p);

  // On the task queues we push reference locations as well as
  // partially-scanned arrays (in the latter case, we push an oop to
  // the from-space image of the array and the length on the
  // from-space image indicates how many entries on the array we still
  // need to scan; this is basically how ParNew does partial array
  // scanning too). To be able to distinguish between reference
  // locations and partially-scanned array oops we simply mask the
  // latter oops with 0x01. The next three methods do the masking,
  // unmasking, and checking whether the oop is masked or not. Notice
  // that the signature of the mask and unmask methods looks a bit
  // strange, as they accept and return different types (oop and
  // oop*). This is because of the difference in types between what
  // the task queue holds (oop*) and oops to partially-scanned arrays
  // (oop). We do all the necessary casting in the mask / unmask
  // methods to avoid sprinkling the rest of the code with more casts.

  // These are added to the taskqueue so PS_CHUNKED_ARRAY_OOP_MASK (or any
  // future masks) can't conflict with COMPRESSED_OOP_MASK
#define PS_CHUNKED_ARRAY_OOP_MASK  0x2

  bool is_oop_masked(StarTask p) {
    // If something is marked chunked it's always treated like wide oop*
    return (((intptr_t)(oop*)p) & PS_CHUNKED_ARRAY_OOP_MASK) ==
                                  PS_CHUNKED_ARRAY_OOP_MASK;
  }

  oop* mask_chunked_array_oop(oop obj) {
    assert(!is_oop_masked((oop*) obj), "invariant");
    oop* ret = (oop*) ((uintptr_t)obj | PS_CHUNKED_ARRAY_OOP_MASK);
    assert(is_oop_masked(ret), "invariant");
    return ret;
  }

  oop unmask_chunked_array_oop(StarTask p) {
    assert(is_oop_masked(p), "invariant");
    assert(!p.is_narrow(), "chunked array oops cannot be narrow");
    oop *chunk = (oop*)p;  // cast p to oop (uses conversion operator)
    oop ret = oop((oop*)((uintptr_t)chunk & ~PS_CHUNKED_ARRAY_OOP_MASK));
    assert(!is_oop_masked((oop*) ret), "invariant");
    return ret;
  }

  template <class T> void  process_array_chunk_work(oop obj,
                                                    int start, int end);
  void process_array_chunk(oop old);

  template <class T> void push_depth(T* p) {
    assert(depth_first(), "pre-condition");

#if PS_PM_STATS
    ++_total_pushes;
    int stack_length = claimed_stack_depth()->overflow_stack()->length();
#endif // PS_PM_STATS

    claimed_stack_depth()->push(p);

#if PS_PM_STATS
    if (claimed_stack_depth()->overflow_stack()->length() != stack_length) {
      ++_overflow_pushes;
      if ((uint)stack_length + 1 > _max_overflow_length) {
        _max_overflow_length = (uint)stack_length + 1;
      }
    }
#endif // PS_PM_STATS
  }

  void push_breadth(oop o) {
    assert(!depth_first(), "pre-condition");

#if PS_PM_STATS
    ++_total_pushes;
    int stack_length = claimed_stack_breadth()->overflow_stack()->length();
#endif // PS_PM_STATS

    claimed_stack_breadth()->push(o);

#if PS_PM_STATS
    if (claimed_stack_breadth()->overflow_stack()->length() != stack_length) {
      ++_overflow_pushes;
      if ((uint)stack_length + 1 > _max_overflow_length) {
        _max_overflow_length = (uint)stack_length + 1;
      }
    }
#endif // PS_PM_STATS
  }

 protected:
  static OopStarTaskQueueSet* stack_array_depth()   { return _stack_array_depth; }
  static OopTaskQueueSet*     stack_array_breadth() { return _stack_array_breadth; }

 public:
  // Static
  static void initialize();

  static void pre_scavenge();
  static void post_scavenge();

  static PSPromotionManager* gc_thread_promotion_manager(int index);
  static PSPromotionManager* vm_thread_promotion_manager();

  static bool steal_depth(int queue_num, int* seed, StarTask& t) {
    return stack_array_depth()->steal(queue_num, seed, t);
  }

  static bool steal_breadth(int queue_num, int* seed, oop& t) {
    return stack_array_breadth()->steal(queue_num, seed, t);
  }

  PSPromotionManager();

  // Accessors
  OopStarTaskQueue* claimed_stack_depth() {
    return &_claimed_stack_depth;
  }
  OverflowTaskQueue<oop>* claimed_stack_breadth() {
    return &_claimed_stack_breadth;
  }

  bool young_gen_is_full()             { return _young_gen_is_full; }

  bool old_gen_is_full()               { return _old_gen_is_full; }
  void set_old_gen_is_full(bool state) { _old_gen_is_full = state; }

  // Promotion methods
  oop copy_to_survivor_space(oop o, bool depth_first);
  oop oop_promotion_failed(oop obj, markOop obj_mark);

  void reset();

  void flush_labs();
  void drain_stacks(bool totally_drain) {
    if (depth_first()) {
      drain_stacks_depth(totally_drain);
    } else {
      drain_stacks_breadth(totally_drain);
    }
  }
 public:
  void drain_stacks_cond_depth() {
    if (claimed_stack_depth()->size() > _target_stack_size) {
      drain_stacks_depth(false);
    }
  }
  void drain_stacks_depth(bool totally_drain);
  void drain_stacks_breadth(bool totally_drain);

  bool depth_first() const {
    return _depth_first;
  }
  bool stacks_empty() {
    return depth_first() ?
      claimed_stack_depth()->is_empty() :
      claimed_stack_breadth()->is_empty();
  }

  inline void process_popped_location_depth(StarTask p);

  inline void flush_prefetch_queue();
  template <class T> inline void claim_or_forward_depth(T* p);
  template <class T> inline void claim_or_forward_breadth(T* p);

#if PS_PM_STATS
  void increment_steals(oop* p = NULL) {
    _total_steals += 1;
    if (p != NULL && is_oop_masked(p)) {
      _masked_steals += 1;
    }
  }
#endif // PS_PM_STATS
};
