/*
 * Copyright 2001-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

// Remembered set for a heap region.  Represent a set of "cards" that
// contain pointers into the owner heap region.  Cards are defined somewhat
// abstractly, in terms of what the "BlockOffsetTable" in use can parse.

class G1CollectedHeap;
class G1BlockOffsetSharedArray;
class HeapRegion;
class HeapRegionRemSetIterator;
class PosParPRT;
class SparsePRT;


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
  Mutex            _m;
  HeapRegion*      _hr;

  // These are protected by "_m".
  BitMap      _coarse_map;
  size_t      _n_coarse_entries;
  static jint _n_coarsenings;

  PosParPRT** _fine_grain_regions;
  size_t      _n_fine_entries;

#define SAMPLE_FOR_EVICTION 1
#if SAMPLE_FOR_EVICTION
  size_t        _fine_eviction_start;
  static size_t _fine_eviction_stride;
  static size_t _fine_eviction_sample_size;
#endif

  SparsePRT   _sparse_table;

  // These are static after init.
  static size_t _max_fine_entries;
  static size_t _mod_max_fine_entries_mask;

  // Requires "prt" to be the first element of the bucket list appropriate
  // for "hr".  If this list contains an entry for "hr", return it,
  // otherwise return "NULL".
  PosParPRT* find_region_table(size_t ind, HeapRegion* hr) const;

  // Find, delete, and return a candidate PosParPRT, if any exists,
  // adding the deleted region to the coarse bitmap.  Requires the caller
  // to hold _m, and the fine-grain table to be full.
  PosParPRT* delete_region_table();

  // If a PRT for "hr" is in the bucket list indicated by "ind" (which must
  // be the correct index for "hr"), delete it and return true; else return
  // false.
  bool del_single_region_table(size_t ind, HeapRegion* hr);

  static jint _cache_probes;
  static jint _cache_hits;

  // Indexed by thread X heap region, to minimize thread contention.
  static int** _from_card_cache;
  static size_t _from_card_cache_max_regions;
  static size_t _from_card_cache_mem_size;

public:
  OtherRegionsTable(HeapRegion* hr);

  HeapRegion* hr() const { return _hr; }

  // For now.  Could "expand" some tables in the future, so that this made
  // sense.
  void add_reference(oop* from, int tid);

  void add_reference(oop* from) {
    return add_reference(from, 0);
  }

  // Removes any entries shown by the given bitmaps to contain only dead
  // objects.
  void scrub(CardTableModRefBS* ctbs, BitMap* region_bm, BitMap* card_bm);

  // Not const because it takes a lock.
  size_t occupied() const;
  size_t occ_fine() const;
  size_t occ_coarse() const;
  size_t occ_sparse() const;

  static jint n_coarsenings() { return _n_coarsenings; }

  // Returns size in bytes.
  // Not const because it takes a lock.
  size_t mem_size() const;
  static size_t static_mem_size();
  static size_t fl_mem_size();

  bool contains_reference(oop* from) const;
  bool contains_reference_locked(oop* from) const;

  void clear();

  // Specifically clear the from_card_cache.
  void clear_fcc();

  // "from_hr" is being cleared; remove any entries from it.
  void clear_incoming_entry(HeapRegion* from_hr);

  // Declare the heap size (in # of regions) to the OtherRegionsTable.
  // (Uses it to initialize from_card_cache).
  static void init_from_card_cache(size_t max_regions);

  // Declares that only regions i s.t. 0 <= i < new_n_regs are in use.
  // Make sure any entries for higher regions are invalid.
  static void shrink_from_card_cache(size_t new_n_regs);

  static void print_from_card_cache();

};


class HeapRegionRemSet : public CHeapObj {
  friend class VMStructs;
  friend class HeapRegionRemSetIterator;

public:
  enum Event {
    Event_EvacStart, Event_EvacEnd, Event_RSUpdateEnd
  };

private:
  G1BlockOffsetSharedArray* _bosa;
  G1BlockOffsetSharedArray* bosa() const { return _bosa; }

  OtherRegionsTable _other_regions;

  // One set bit for every region that has an entry for this one.
  BitMap _outgoing_region_map;

  // Clear entries for the current region in any rem sets named in
  // the _outgoing_region_map.
  void clear_outgoing_entries();

  enum ParIterState { Unclaimed, Claimed, Complete };
  ParIterState _iter_state;

  // Unused unless G1RecordHRRSOops is true.

  static const int MaxRecorded = 1000000;
  static oop**        _recorded_oops;
  static HeapWord**   _recorded_cards;
  static HeapRegion** _recorded_regions;
  static int          _n_recorded;

  static const int MaxRecordedEvents = 1000;
  static Event*       _recorded_events;
  static int*         _recorded_event_index;
  static int          _n_recorded_events;

  static void print_event(outputStream* str, Event evnt);

public:
  HeapRegionRemSet(G1BlockOffsetSharedArray* bosa,
                   HeapRegion* hr);

  static int num_par_rem_sets();

  HeapRegion* hr() const {
    return _other_regions.hr();
  }

  size_t occupied() const {
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

  /* Used in the sequential case.  Returns "true" iff this addition causes
     the size limit to be reached. */
  void add_reference(oop* from) {
    _other_regions.add_reference(from);
  }

  /* Used in the parallel case.  Returns "true" iff this addition causes
     the size limit to be reached. */
  void add_reference(oop* from, int tid) {
    _other_regions.add_reference(from, tid);
  }

  // Records the fact that the current region contains an outgoing
  // reference into "to_hr".
  void add_outgoing_reference(HeapRegion* to_hr);

  // Removes any entries shown by the given bitmaps to contain only dead
  // objects.
  void scrub(CardTableModRefBS* ctbs, BitMap* region_bm, BitMap* card_bm);

  // The region is being reclaimed; clear its remset, and any mention of
  // entries for this region in other remsets.
  void clear();

  // Forget any entries due to pointers from "from_hr".
  void clear_incoming_entry(HeapRegion* from_hr) {
    _other_regions.clear_incoming_entry(from_hr);
  }

#if 0
  virtual void cleanup() = 0;
#endif

  // Should be called from single-threaded code.
  void init_for_par_iteration();
  // Attempt to claim the region.  Returns true iff this call caused an
  // atomic transition from Unclaimed to Claimed.
  bool claim_iter();
  // Sets the iteration state to "complete".
  void set_iter_complete();
  // Returns "true" iff the region's iteration is complete.
  bool iter_is_complete();

  // Initialize the given iterator to iterate over this rem set.
  void init_iterator(HeapRegionRemSetIterator* iter) const;

#if 0
  // Apply the "do_card" method to the start address of every card in the
  // rem set.  Returns false if some application of the closure aborted.
  virtual bool card_iterate(CardClosure* iter) = 0;
#endif

  // The actual # of bytes this hr_remset takes up.
  size_t mem_size() {
    return _other_regions.mem_size()
      // This correction is necessary because the above includes the second
      // part.
      + sizeof(this) - sizeof(OtherRegionsTable);
  }

  // Returns the memory occupancy of all static data structures associated
  // with remembered sets.
  static size_t static_mem_size() {
    return OtherRegionsTable::static_mem_size();
  }

  // Returns the memory occupancy of all free_list data structures associated
  // with remembered sets.
  static size_t fl_mem_size() {
    return OtherRegionsTable::fl_mem_size();
  }

  bool contains_reference(oop* from) const {
    return _other_regions.contains_reference(from);
  }
  void print() const;

  // Called during a stop-world phase to perform any deferred cleanups.
  // The second version may be called by parallel threads after then finish
  // collection work.
  static void cleanup();
  static void par_cleanup();

  // Declare the heap size (in # of regions) to the HeapRegionRemSet(s).
  // (Uses it to initialize from_card_cache).
  static void init_heap(size_t max_regions) {
    OtherRegionsTable::init_from_card_cache(max_regions);
  }

  // Declares that only regions i s.t. 0 <= i < new_n_regs are in use.
  static void shrink_heap(size_t new_n_regs) {
    OtherRegionsTable::shrink_from_card_cache(new_n_regs);
  }

#ifndef PRODUCT
  static void print_from_card_cache() {
    OtherRegionsTable::print_from_card_cache();
  }
#endif

  static void record(HeapRegion* hr, oop* f);
  static void print_recorded();
  static void record_event(Event evnt);

  // Run unit tests.
#ifndef PRODUCT
  static void test();
#endif

};

class HeapRegionRemSetIterator : public CHeapObj {

  // The region over which we're iterating.
  const HeapRegionRemSet* _hrrs;

  // Local caching of HRRS fields.
  const BitMap*             _coarse_map;
  PosParPRT**               _fine_grain_regions;

  G1BlockOffsetSharedArray* _bosa;
  G1CollectedHeap*          _g1h;

  // The number yielded since initialization.
  size_t _n_yielded_fine;
  size_t _n_yielded_coarse;
  size_t _n_yielded_sparse;

  // If true we're iterating over the coarse table; if false the fine
  // table.
  enum IterState {
    Sparse,
    Fine,
    Coarse
  };
  IterState _is;

  // In both kinds of iteration, heap offset of first card of current
  // region.
  size_t _cur_region_card_offset;
  // Card offset within cur region.
  size_t _cur_region_cur_card;

  // Coarse table iteration fields:

  // Current region index;
  int _coarse_cur_region_index;
  int _coarse_cur_region_cur_card;

  bool coarse_has_next(size_t& card_index);

  // Fine table iteration fields:

  // Index of bucket-list we're working on.
  int _fine_array_index;
  // Per Region Table we're doing within current bucket list.
  PosParPRT* _fine_cur_prt;

  /* SparsePRT::*/ SparsePRTIter _sparse_iter;

  void fine_find_next_non_null_prt();

  bool fine_has_next();
  bool fine_has_next(size_t& card_index);

public:
  // We require an iterator to be initialized before use, so the
  // constructor does little.
  HeapRegionRemSetIterator();

  void initialize(const HeapRegionRemSet* hrrs);

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

#if 0
class CardClosure: public Closure {
public:
  virtual void do_card(HeapWord* card_start) = 0;
};

#endif
