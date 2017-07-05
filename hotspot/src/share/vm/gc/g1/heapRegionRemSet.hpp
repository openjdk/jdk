/*
 * Copyright (c) 2001, 2015, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_HEAPREGIONREMSET_HPP
#define SHARE_VM_GC_G1_HEAPREGIONREMSET_HPP

#include "gc/g1/g1CodeCacheRemSet.hpp"
#include "gc/g1/sparsePRT.hpp"

// Remembered set for a heap region.  Represent a set of "cards" that
// contain pointers into the owner heap region.  Cards are defined somewhat
// abstractly, in terms of what the "BlockOffsetTable" in use can parse.

class G1CollectedHeap;
class G1BlockOffsetSharedArray;
class HeapRegion;
class HeapRegionRemSetIterator;
class PerRegionTable;
class SparsePRT;
class nmethod;

// Essentially a wrapper around SparsePRTCleanupTask. See
// sparsePRT.hpp for more details.
class HRRSCleanupTask : public SparsePRTCleanupTask {
};

// The FromCardCache remembers the most recently processed card on the heap on
// a per-region and per-thread basis.
class FromCardCache : public AllStatic {
 private:
  // Array of card indices. Indexed by thread X and heap region to minimize
  // thread contention.
  static int** _cache;
  static uint _max_regions;
  static size_t _static_mem_size;

 public:
  enum {
    InvalidCard = -1 // Card value of an invalid card, i.e. a card index not otherwise used.
  };

  static void clear(uint region_idx);

  // Returns true if the given card is in the cache at the given location, or
  // replaces the card at that location and returns false.
  static bool contains_or_replace(uint worker_id, uint region_idx, int card) {
    int card_in_cache = at(worker_id, region_idx);
    if (card_in_cache == card) {
      return true;
    } else {
      set(worker_id, region_idx, card);
      return false;
    }
  }

  static int at(uint worker_id, uint region_idx) {
    return _cache[worker_id][region_idx];
  }

  static void set(uint worker_id, uint region_idx, int val) {
    _cache[worker_id][region_idx] = val;
  }

  static void initialize(uint n_par_rs, uint max_num_regions);

  static void invalidate(uint start_idx, size_t num_regions);

  static void print(outputStream* out = gclog_or_tty) PRODUCT_RETURN;

  static size_t static_mem_size() {
    return _static_mem_size;
  }
};

// The "_coarse_map" is a bitmap with one bit for each region, where set
// bits indicate that the corresponding region may contain some pointer
// into the owning region.

// The "_fine_grain_entries" array is an open hash table of PerRegionTables
// (PRTs), indicating regions for which we're keeping the RS as a set of
// cards.  The strategy is to cap the size of the fine-grain table,
// deleting an entry and setting the corresponding coarse-grained bit when
// we would overflow this cap.

// We use a mixture of locking and lock-free techniques here.  We allow
// threads to locate PRTs without locking, but threads attempting to alter
// a bucket list obtain a lock.  This means that any failing attempt to
// find a PRT must be retried with the lock.  It might seem dangerous that
// a read can find a PRT that is concurrently deleted.  This is all right,
// because:
//
//   1) We only actually free PRT's at safe points (though we reuse them at
//      other times).
//   2) We find PRT's in an attempt to add entries.  If a PRT is deleted,
//      it's _coarse_map bit is set, so the that we were attempting to add
//      is represented.  If a deleted PRT is re-used, a thread adding a bit,
//      thinking the PRT is for a different region, does no harm.

class OtherRegionsTable VALUE_OBJ_CLASS_SPEC {
  friend class HeapRegionRemSetIterator;

  G1CollectedHeap* _g1h;
  Mutex*           _m;
  HeapRegion*      _hr;

  // These are protected by "_m".
  BitMap      _coarse_map;
  size_t      _n_coarse_entries;
  static jint _n_coarsenings;

  PerRegionTable** _fine_grain_regions;
  size_t           _n_fine_entries;

  // The fine grain remembered sets are doubly linked together using
  // their 'next' and 'prev' fields.
  // This allows fast bulk freeing of all the fine grain remembered
  // set entries, and fast finding of all of them without iterating
  // over the _fine_grain_regions table.
  PerRegionTable * _first_all_fine_prts;
  PerRegionTable * _last_all_fine_prts;

  // Used to sample a subset of the fine grain PRTs to determine which
  // PRT to evict and coarsen.
  size_t        _fine_eviction_start;
  static size_t _fine_eviction_stride;
  static size_t _fine_eviction_sample_size;

  SparsePRT   _sparse_table;

  // These are static after init.
  static size_t _max_fine_entries;
  static size_t _mod_max_fine_entries_mask;

  // Requires "prt" to be the first element of the bucket list appropriate
  // for "hr".  If this list contains an entry for "hr", return it,
  // otherwise return "NULL".
  PerRegionTable* find_region_table(size_t ind, HeapRegion* hr) const;

  // Find, delete, and return a candidate PerRegionTable, if any exists,
  // adding the deleted region to the coarse bitmap.  Requires the caller
  // to hold _m, and the fine-grain table to be full.
  PerRegionTable* delete_region_table();

  // link/add the given fine grain remembered set into the "all" list
  void link_to_all(PerRegionTable * prt);
  // unlink/remove the given fine grain remembered set into the "all" list
  void unlink_from_all(PerRegionTable * prt);

  bool contains_reference_locked(OopOrNarrowOopStar from) const;

  // Clear the from_card_cache entries for this region.
  void clear_fcc();
public:
  // Create a new remembered set for the given heap region. The given mutex should
  // be used to ensure consistency.
  OtherRegionsTable(HeapRegion* hr, Mutex* m);

  // For now.  Could "expand" some tables in the future, so that this made
  // sense.
  void add_reference(OopOrNarrowOopStar from, uint tid);

  // Returns whether the remembered set contains the given reference.
  bool contains_reference(OopOrNarrowOopStar from) const;

  // Returns whether this remembered set (and all sub-sets) have an occupancy
  // that is less or equal than the given occupancy.
  bool occupancy_less_or_equal_than(size_t limit) const;

  // Removes any entries shown by the given bitmaps to contain only dead
  // objects. Not thread safe.
  // Set bits in the bitmaps indicate that the given region or card is live.
  void scrub(CardTableModRefBS* ctbs, BitMap* region_bm, BitMap* card_bm);

  // Returns whether this remembered set (and all sub-sets) does not contain any entry.
  bool is_empty() const;

  // Returns the number of cards contained in this remembered set.
  size_t occupied() const;
  size_t occ_fine() const;
  size_t occ_coarse() const;
  size_t occ_sparse() const;

  static jint n_coarsenings() { return _n_coarsenings; }

  // Returns size of the actual remembered set containers in bytes.
  size_t mem_size() const;
  // Returns the size of static data in bytes.
  static size_t static_mem_size();
  // Returns the size of the free list content in bytes.
  static size_t fl_mem_size();

  // Clear the entire contents of this remembered set.
  void clear();

  void do_cleanup_work(HRRSCleanupTask* hrrs_cleanup_task);
};

class HeapRegionRemSet : public CHeapObj<mtGC> {
  friend class VMStructs;
  friend class HeapRegionRemSetIterator;

public:
  enum Event {
    Event_EvacStart, Event_EvacEnd, Event_RSUpdateEnd, Event_illegal
  };

private:
  G1BlockOffsetSharedArray* _bosa;

  // A set of code blobs (nmethods) whose code contains pointers into
  // the region that owns this RSet.
  G1CodeRootSet _code_roots;

  Mutex _m;

  OtherRegionsTable _other_regions;

  enum ParIterState { Unclaimed, Claimed, Complete };
  volatile ParIterState _iter_state;
  volatile size_t _iter_claimed;

  // Unused unless G1RecordHRRSOops is true.

  static const int MaxRecorded = 1000000;
  static OopOrNarrowOopStar* _recorded_oops;
  static HeapWord**          _recorded_cards;
  static HeapRegion**        _recorded_regions;
  static int                 _n_recorded;

  static const int MaxRecordedEvents = 1000;
  static Event*       _recorded_events;
  static int*         _recorded_event_index;
  static int          _n_recorded_events;

  static void print_event(outputStream* str, Event evnt);

public:
  HeapRegionRemSet(G1BlockOffsetSharedArray* bosa, HeapRegion* hr);

  static uint num_par_rem_sets();
  static void setup_remset_size();

  bool is_empty() const {
    return (strong_code_roots_list_length() == 0) && _other_regions.is_empty();
  }

  bool occupancy_less_or_equal_than(size_t occ) const {
    return (strong_code_roots_list_length() == 0) && _other_regions.occupancy_less_or_equal_than(occ);
  }

  size_t occupied() {
    MutexLockerEx x(&_m, Mutex::_no_safepoint_check_flag);
    return occupied_locked();
  }
  size_t occupied_locked() {
    return _other_regions.occupied();
  }
  size_t occ_fine() const {
    return _other_regions.occ_fine();
  }
  size_t occ_coarse() const {
    return _other_regions.occ_coarse();
  }
  size_t occ_sparse() const {
    return _other_regions.occ_sparse();
  }

  static jint n_coarsenings() { return OtherRegionsTable::n_coarsenings(); }

  // Used in the sequential case.
  void add_reference(OopOrNarrowOopStar from) {
    _other_regions.add_reference(from, 0);
  }

  // Used in the parallel case.
  void add_reference(OopOrNarrowOopStar from, uint tid) {
    _other_regions.add_reference(from, tid);
  }

  // Removes any entries in the remembered set shown by the given bitmaps to
  // contain only dead objects. Not thread safe.
  // One bits in the bitmaps indicate that the given region or card is live.
  void scrub(CardTableModRefBS* ctbs, BitMap* region_bm, BitMap* card_bm);

  // The region is being reclaimed; clear its remset, and any mention of
  // entries for this region in other remsets.
  void clear();
  void clear_locked();

  // Attempt to claim the region.  Returns true iff this call caused an
  // atomic transition from Unclaimed to Claimed.
  bool claim_iter();
  // Sets the iteration state to "complete".
  void set_iter_complete();
  // Returns "true" iff the region's iteration is complete.
  bool iter_is_complete();

  // Support for claiming blocks of cards during iteration
  size_t iter_claimed() const { return _iter_claimed; }
  // Claim the next block of cards
  size_t iter_claimed_next(size_t step) {
    return Atomic::add(step, &_iter_claimed) - step;
  }

  void reset_for_par_iteration();

  bool verify_ready_for_par_iteration() {
    return (_iter_state == Unclaimed) && (_iter_claimed == 0);
  }

  // The actual # of bytes this hr_remset takes up.
  // Note also includes the strong code root set.
  size_t mem_size() {
    MutexLockerEx x(&_m, Mutex::_no_safepoint_check_flag);
    return _other_regions.mem_size()
      // This correction is necessary because the above includes the second
      // part.
      + (sizeof(HeapRegionRemSet) - sizeof(OtherRegionsTable))
      + strong_code_roots_mem_size();
  }

  // Returns the memory occupancy of all static data structures associated
  // with remembered sets.
  static size_t static_mem_size() {
    return OtherRegionsTable::static_mem_size() + G1CodeRootSet::static_mem_size();
  }

  // Returns the memory occupancy of all free_list data structures associated
  // with remembered sets.
  static size_t fl_mem_size() {
    return OtherRegionsTable::fl_mem_size();
  }

  bool contains_reference(OopOrNarrowOopStar from) const {
    return _other_regions.contains_reference(from);
  }

  // Routines for managing the list of code roots that point into
  // the heap region that owns this RSet.
  void add_strong_code_root(nmethod* nm);
  void add_strong_code_root_locked(nmethod* nm);
  void remove_strong_code_root(nmethod* nm);

  // Applies blk->do_code_blob() to each of the entries in
  // the strong code roots list
  void strong_code_roots_do(CodeBlobClosure* blk) const;

  void clean_strong_code_roots(HeapRegion* hr);

  // Returns the number of elements in the strong code roots list
  size_t strong_code_roots_list_length() const {
    return _code_roots.length();
  }

  // Returns true if the strong code roots contains the given
  // nmethod.
  bool strong_code_roots_list_contains(nmethod* nm) {
    return _code_roots.contains(nm);
  }

  // Returns the amount of memory, in bytes, currently
  // consumed by the strong code roots.
  size_t strong_code_roots_mem_size();

  void print() PRODUCT_RETURN;

  // Called during a stop-world phase to perform any deferred cleanups.
  static void cleanup();

  // Declare the heap size (in # of regions) to the HeapRegionRemSet(s).
  // (Uses it to initialize from_card_cache).
  static void init_heap(uint max_regions) {
    FromCardCache::initialize(num_par_rem_sets(), max_regions);
  }

  static void invalidate_from_card_cache(uint start_idx, size_t num_regions) {
    FromCardCache::invalidate(start_idx, num_regions);
  }

#ifndef PRODUCT
  static void print_from_card_cache() {
    FromCardCache::print();
  }
#endif

  static void record(HeapRegion* hr, OopOrNarrowOopStar f);
  static void print_recorded();
  static void record_event(Event evnt);

  // These are wrappers for the similarly-named methods on
  // SparsePRT. Look at sparsePRT.hpp for more details.
  static void reset_for_cleanup_tasks();
  void do_cleanup_work(HRRSCleanupTask* hrrs_cleanup_task);
  static void finish_cleanup_task(HRRSCleanupTask* hrrs_cleanup_task);

  // Run unit tests.
#ifndef PRODUCT
  static void test_prt();
  static void test();
#endif
};

class HeapRegionRemSetIterator : public StackObj {
 private:
  // The region RSet over which we are iterating.
  HeapRegionRemSet* _hrrs;

  // Local caching of HRRS fields.
  const BitMap*             _coarse_map;

  G1BlockOffsetSharedArray* _bosa;
  G1CollectedHeap*          _g1h;

  // The number of cards yielded since initialization.
  size_t _n_yielded_fine;
  size_t _n_yielded_coarse;
  size_t _n_yielded_sparse;

  // Indicates what granularity of table that we are currently iterating over.
  // We start iterating over the sparse table, progress to the fine grain
  // table, and then finish with the coarse table.
  enum IterState {
    Sparse,
    Fine,
    Coarse
  };
  IterState _is;

  // For both Coarse and Fine remembered set iteration this contains the
  // first card number of the heap region we currently iterate over.
  size_t _cur_region_card_offset;

  // Current region index for the Coarse remembered set iteration.
  int    _coarse_cur_region_index;
  size_t _coarse_cur_region_cur_card;

  bool coarse_has_next(size_t& card_index);

  // The PRT we are currently iterating over.
  PerRegionTable* _fine_cur_prt;
  // Card offset within the current PRT.
  size_t _cur_card_in_prt;

  // Update internal variables when switching to the given PRT.
  void switch_to_prt(PerRegionTable* prt);
  bool fine_has_next();
  bool fine_has_next(size_t& card_index);

  // The Sparse remembered set iterator.
  SparsePRTIter _sparse_iter;

 public:
  HeapRegionRemSetIterator(HeapRegionRemSet* hrrs);

  // If there remains one or more cards to be yielded, returns true and
  // sets "card_index" to one of those cards (which is then considered
  // yielded.)   Otherwise, returns false (and leaves "card_index"
  // undefined.)
  bool has_next(size_t& card_index);

  size_t n_yielded_fine() { return _n_yielded_fine; }
  size_t n_yielded_coarse() { return _n_yielded_coarse; }
  size_t n_yielded_sparse() { return _n_yielded_sparse; }
  size_t n_yielded() {
    return n_yielded_fine() + n_yielded_coarse() + n_yielded_sparse();
  }
};

#endif // SHARE_VM_GC_G1_HEAPREGIONREMSET_HPP
