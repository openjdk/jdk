/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBERED_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBERED_HPP

// Terminology used within this source file:
//
// Card Entry:   This is the information that identifies whether a
//               particular card-table entry is Clean or Dirty.  A clean
//               card entry denotes that the associated memory does not
//               hold references to young-gen memory.
//
// Card Region, aka
// Card Memory:  This is the region of memory that is assocated with a
//               particular card entry.
//
// Card Cluster: A card cluster represents 64 card entries.  A card
//               cluster is the minimal amount of work performed at a
//               time by a parallel thread.  Note that the work required
//               to scan a card cluster is somewhat variable in that the
//               required effort depends on how many cards are dirty, how
//               many references are held within the objects that span a
//               DIRTY card's memory, and on the size of the object
//               that spans the end of a DIRTY card's memory (because
//               that object, if it's not an array, may need to be scanned in
//               its entirety, when the object is imprecisely dirtied. Imprecise
//               dirtying is when the card corresponding to the object header
//               is dirtied, rather than the card on which the updated field lives).
//               To better balance work amongst them, parallel worker threads dynamically
//               claim clusters and are flexible in the number of clusters they
//               process.
//
// A cluster represents a "natural" quantum of work to be performed by
// a parallel GC thread's background remembered set scanning efforts.
// The notion of cluster is similar to the notion of stripe in the
// implementation of parallel GC card scanning.  However, a cluster is
// typically smaller than a stripe, enabling finer grain division of
// labor between multiple threads, and potentially better load balancing
// when dirty cards are not uniformly distributed in the heap, as is often
// the case with generational workloads where more recently promoted objects
// may be dirtied more frequently that older objects.
//
// For illustration, consider the following possible JVM configurations:
//
//   Scenario 1:
//     RegionSize is 128 MB
//     Span of a card entry is 512 B
//     Each card table entry consumes 1 B
//     Assume one long word (8 B)of the card table represents a cluster.
//       This long word holds 8 card table entries, spanning a
//       total of 8*512 B = 4 KB of the heap
//     The number of clusters per region is 128 MB / 4 KB = 32 K
//
//   Scenario 2:
//     RegionSize is 128 MB
//     Span of each card entry is 128 B
//     Each card table entry consumes 1 bit
//     Assume one int word (4 B) of the card table represents a cluster.
//       This int word holds 32 b/1 b = 32 card table entries, spanning a
//       total of 32 * 128 B = 4 KB of the heap
//     The number of clusters per region is 128 MB / 4 KB = 32 K
//
//   Scenario 3:
//     RegionSize is 128 MB
//     Span of each card entry is 512 B
//     Each card table entry consumes 1 bit
//     Assume one long word (8 B) of card table represents a cluster.
//       This long word holds 64 b/ 1 b = 64 card table entries, spanning a
//       total of 64 * 512 B = 32 KB of the heap
//     The number of clusters per region is 128 MB / 32 KB = 4 K
//
// At the start of a new young-gen concurrent mark pass, the gang of
// Shenandoah worker threads collaborate in performing the following
// actions:
//
//  Let old_regions = number of ShenandoahHeapRegion comprising
//    old-gen memory
//  Let region_size = ShenandoahHeapRegion::region_size_bytes()
//    represent the number of bytes in each region
//  Let clusters_per_region = region_size / 512
//  Let rs represent the ShenandoahDirectCardMarkRememberedSet
//
//  for each ShenandoahHeapRegion old_region in the whole heap
//    determine the cluster number of the first cluster belonging
//      to that region
//    for each cluster contained within that region
//      Assure that exactly one worker thread processes each
//      cluster, each thread making a series of invocations of the
//      following:
//
//        rs->process_clusters(worker_id, ReferenceProcessor *,
//                             ShenandoahConcurrentMark *, cluster_no, cluster_count,
//                             HeapWord *end_of_range, OopClosure *oops);
//
//  For efficiency, divide up the clusters so that different threads
//  are responsible for processing different clusters.  Processing costs
//  may vary greatly between clusters for the following reasons:
//
//        a) some clusters contain mostly dirty cards and other
//           clusters contain mostly clean cards
//        b) some clusters contain mostly primitive data and other
//           clusters contain mostly reference data
//        c) some clusters are spanned by very large non-array objects that
//           begin in some other cluster.  When a large non-array object
//           beginning in a preceding cluster spans large portions of
//           this cluster, then because of imprecise dirtying, the
//           portion of the object in this cluster may be clean, but
//           will need to be processed by the worker responsible for
//           this cluster, potentially increasing its work.
//        d) in the case that the end of this cluster is spanned by a
//           very large non-array object, the worker for this cluster will
//           be responsible for processing the portion of the object
//           in this cluster.
//
// Though an initial division of labor between marking threads may
// assign equal numbers of clusters to be scanned by each thread, it
// should be expected that some threads will finish their assigned
// work before others.  Therefore, some amount of the full remembered
// set scanning effort should be held back and assigned incrementally
// to the threads that end up with excess capacity.  Consider the
// following strategy for dividing labor:
//
//        1. Assume there are 8 marking threads and 1024 remembered
//           set clusters to be scanned.
//        2. Assign each thread to scan 64 clusters.  This leaves
//           512 (1024 - (8*64)) clusters to still be scanned.
//        3. As the 8 server threads complete previous cluster
//           scanning assignments, issue each of the next 8 scanning
//           assignments as units of 32 additional cluster each.
//           In the case that there is high variance in effort
//           associated with previous cluster scanning assignments,
//           multiples of these next assignments may be serviced by
//           the server threads that were previously assigned lighter
//           workloads.
//        4. Make subsequent scanning assignments as follows:
//             a) 8 assignments of size 16 clusters
//             b) 8 assignments of size 8 clusters
//             c) 16 assignments of size 4 clusters
//
//    When there is no more remembered set processing work to be
//    assigned to a newly idled worker thread, that thread can move
//    on to work on other tasks associated with root scanning until such
//    time as all clusters have been examined.
//
// Remembered set scanning is designed to run concurrently with
// mutator threads, with multiple concurrent workers. Furthermore, the
// current implementation of remembered set scanning never clears a
// card once it has been marked.
//
// These limitations will be addressed in future enhancements to the
// existing implementation.

#include "gc/shared/workerThread.hpp"
#include "gc/shenandoah/shenandoahCardStats.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"
#include "gc/shenandoah/shenandoahNumberSeq.hpp"
#include "gc/shenandoah/shenandoahTaskqueue.hpp"
#include "memory/iterator.hpp"
#include "utilities/globalDefinitions.hpp"

class ShenandoahReferenceProcessor;
class ShenandoahConcurrentMark;
class ShenandoahHeap;
class ShenandoahHeapRegion;
class ShenandoahRegionIterator;
class ShenandoahMarkingContext;

class CardTable;
typedef CardTable::CardValue CardValue;

class ShenandoahDirectCardMarkRememberedSet: public CHeapObj<mtGC> {

private:

  // Use symbolic constants defined in cardTable.hpp
  //  CardTable::card_shift = 9;
  //  CardTable::card_size = 512;
  //  CardTable::card_size_in_words = 64;
  //  CardTable::clean_card_val()
  //  CardTable::dirty_card_val()

  const size_t LogCardValsPerIntPtr;    // the number of card values (entries) in an intptr_t
  const size_t LogCardSizeInWords;      // the size of a card in heap word units

  ShenandoahHeap* _heap;
  ShenandoahCardTable* _card_table;
  size_t _card_shift;
  size_t _total_card_count;
  HeapWord* _whole_heap_base;   // Points to first HeapWord of data contained within heap memory
  CardValue* _byte_map;         // Points to first entry within the card table
  CardValue* _byte_map_base;    // Points to byte_map minus the bias computed from address of heap memory

public:

  // count is the number of cards represented by the card table.
  ShenandoahDirectCardMarkRememberedSet(ShenandoahCardTable* card_table, size_t total_card_count);

  // Card index is zero-based relative to _byte_map.
  size_t last_valid_index() const;
  size_t total_cards() const;
  size_t card_index_for_addr(HeapWord* p) const;
  HeapWord* addr_for_card_index(size_t card_index) const;
  inline const CardValue* get_card_table_byte_map(bool use_write_table) const {
    return use_write_table ? _card_table->write_byte_map() : _card_table->read_byte_map();
  }

  inline bool is_card_dirty(size_t card_index) const;
  inline bool is_write_card_dirty(size_t card_index) const;
  inline void mark_card_as_dirty(size_t card_index);
  inline void mark_range_as_dirty(size_t card_index, size_t num_cards);
  inline void mark_card_as_clean(size_t card_index);
  inline void mark_range_as_clean(size_t card_index, size_t num_cards);
  inline bool is_card_dirty(HeapWord* p) const;
  inline bool is_write_card_dirty(HeapWord* p) const;
  inline void mark_card_as_dirty(HeapWord* p);
  inline void mark_range_as_dirty(HeapWord* p, size_t num_heap_words);
  inline void mark_card_as_clean(HeapWord* p);
  inline void mark_range_as_clean(HeapWord* p, size_t num_heap_words);

  // Merge any dirty values from write table into the read table, while leaving
  // the write table unchanged.
  void merge_write_table(HeapWord* start, size_t word_count);

  // Destructively copy the write table to the read table, and clean the write table.
  void reset_remset(HeapWord* start, size_t word_count);
};

// A ShenandoahCardCluster represents the minimal unit of work
// performed by independent parallel GC threads during scanning of
// remembered sets.
//
// The GC threads that perform card-table remembered set scanning may
// overwrite card-table entries to mark them as clean in the case that
// the associated memory no longer holds references to young-gen
// memory.  Rather than access the card-table entries directly, all GC
// thread access to card-table information is made by way of the
// ShenandoahCardCluster data abstraction.  This abstraction
// effectively manages access to multiple possible underlying
// remembered set implementations, including a traditional card-table
// approach and a SATB-based approach.
//
// The API services represent a compromise between efficiency and
// convenience.
//
// Multiple GC threads that scan the remembered set
// in parallel.  The desire is to divide the complete scanning effort
// into multiple clusters of work that can be independently processed
// by individual threads without need for synchronizing efforts
// between the work performed by each task.  The term "cluster" of
// work is similar to the term "stripe" as used in the implementation
// of Parallel GC.
//
// Complexity arises when an object to be scanned crosses the boundary
// between adjacent cluster regions.  Here is the protocol that we currently
// follow:
//
//  1. The thread responsible for scanning the cards in a cluster modifies
//     the associated card-table entries. Only cards that are dirty are
//     processed, except as described below for the case of objects that
//     straddle more than one card.
//  2. Object Arrays are precisely dirtied, so only the portion of the obj-array
//     that overlaps the range of dirty cards in its cluster are scanned
//     by each worker thread. This holds for portions of obj-arrays that extend
//     over clusters processed by different workers, with each worked responsible
//     for scanning the portion of the obj-array overlapping the dirty cards in
//     its cluster.
//  3. Non-array objects are precisely dirtied by the interpreter and the compilers
//     For such objects that extend over multiple cards, or even multiple clusters,
//     the entire object is scanned by the worker that processes the (dirty) card on
//     which the object's header lies. (However, GC workers should precisely dirty the
//     cards with inter-regional/inter-generational pointers in the body of this object,
//     thus making subsequent scans potentially less expensive.) Such larger non-array
//     objects are relatively rare.
//
//  A possible criticism:
//  C. The representation of pointer location descriptive information
//     within Klass representations is not designed for efficient
//     "random access".  An alternative approach to this design would
//     be to scan very large objects multiple times, once for each
//     cluster that is spanned by the object's range.  This reduces
//     unnecessary overscan, but it introduces different sorts of
//     overhead effort:
//       i) For each spanned cluster, we have to look up the start of
//          the crossing object.
//      ii) Each time we scan the very large object, we have to
//          sequentially walk through its pointer location
//          descriptors, skipping over all of the pointers that
//          precede the start of the range of addresses that we
//          consider relevant.


// Because old-gen heap memory is not necessarily contiguous, and
// because cards are not necessarily maintained for young-gen memory,
// consecutive card numbers do not necessarily correspond to consecutive
// address ranges.  For the traditional direct-card-marking
// implementation of this interface, consecutive card numbers are
// likely to correspond to contiguous regions of memory, but this
// should not be assumed.  Instead, rely only upon the following:
//
//  1. All card numbers for cards pertaining to the same
//     ShenandoahHeapRegion are consecutively numbered.
//  2. In the case that neighboring ShenandoahHeapRegions both
//     represent old-gen memory, the card regions that span the
//     boundary between these neighboring heap regions will be
//     consecutively numbered.
//  3. (A corollary) In the case that an old-gen object straddles the
//     boundary between two heap regions, the card regions that
//     correspond to the span of this object will be consecutively
//     numbered.
//
// ShenandoahCardCluster abstracts access to the remembered set
// and also keeps track of crossing map information to allow efficient
// resolution of object start addresses.
//
// ShenandoahCardCluster supports all of the services of
// DirectCardMarkRememberedSet, plus it supports register_object() and lookup_object().
// Note that we only need to register the start addresses of the object that
// overlays the first address of a card; we need to do this for every card.
// In other words, register_object() checks if the object crosses a card boundary,
// and updates the offset value for each card that the object crosses into.
// For objects that don't straddle cards, nothing needs to be done.
//
class ShenandoahCardCluster: public CHeapObj<mtGC> {

private:
  ShenandoahDirectCardMarkRememberedSet* _rs;

public:
  static const size_t CardsPerCluster = 64;

private:
  typedef struct cross_map { uint8_t first; uint8_t last; } xmap;
  typedef union crossing_info { uint16_t short_word; xmap offsets; } crossing_info;

  // ObjectStartsInCardRegion bit is set within a crossing_info.offsets.start iff at least one object starts within
  // a particular card region.  We pack this bit into start byte under assumption that start byte is accessed less
  // frequently than last byte.  This is true when number of clean cards is greater than number of dirty cards.
  static const uint8_t ObjectStartsInCardRegion = 0x80;
  static const uint8_t FirstStartBits           = 0x7f;

  // Check that we have enough bits to store the largest possible offset into a card for an object start.
  // The value for maximum card size is based on the constraints for GCCardSizeInBytes in gc_globals.hpp.
  static const int MaxCardSize = NOT_LP64(512) LP64_ONLY(1024);
  STATIC_ASSERT((MaxCardSize / HeapWordSize) - 1 <= FirstStartBits);

  crossing_info* _object_starts;

public:
  // If we're setting first_start, assume the card has an object.
  inline void set_first_start(size_t card_index, uint8_t value) {
    _object_starts[card_index].offsets.first = ObjectStartsInCardRegion | value;
  }

  inline void set_last_start(size_t card_index, uint8_t value) {
    _object_starts[card_index].offsets.last = value;
  }

  inline void set_starts_object_bit(size_t card_index) {
    _object_starts[card_index].offsets.first |= ObjectStartsInCardRegion;
  }

  inline void clear_starts_object_bit(size_t card_index) {
    _object_starts[card_index].offsets.first &= ~ObjectStartsInCardRegion;
  }

  // Returns true iff an object is known to start within the card memory associated with card card_index.
  inline bool starts_object(size_t card_index) const {
    return (_object_starts[card_index].offsets.first & ObjectStartsInCardRegion) != 0;
  }

  inline void clear_objects_in_range(HeapWord* addr, size_t num_words) {
    size_t card_index = _rs->card_index_for_addr(addr);
    size_t last_card_index = _rs->card_index_for_addr(addr + num_words - 1);
    while (card_index <= last_card_index)
      _object_starts[card_index++].short_word = 0;
  }

  ShenandoahCardCluster(ShenandoahDirectCardMarkRememberedSet* rs) {
    _rs = rs;
    _object_starts = NEW_C_HEAP_ARRAY(crossing_info, rs->total_cards(), mtGC);
    for (size_t i = 0; i < rs->total_cards(); i++) {
      _object_starts[i].short_word = 0;
    }
  }

  ~ShenandoahCardCluster() {
    FREE_C_HEAP_ARRAY(crossing_info, _object_starts);
    _object_starts = nullptr;
  }

  // There is one entry within the object_starts array for each card entry.
  //
  //  Suppose multiple garbage objects are coalesced during GC sweep
  //  into a single larger "free segment".  As each two objects are
  //  coalesced together, the start information pertaining to the second
  //  object must be removed from the objects_starts array.  If the
  //  second object had been the first object within card memory,
  //  the new first object is the object that follows that object if
  //  that starts within the same card memory, or NoObject if the
  //  following object starts within the following cluster.  If the
  //  second object had been the last object in the card memory,
  //  replace this entry with the newly coalesced object if it starts
  //  within the same card memory, or with NoObject if it starts in a
  //  preceding card's memory.
  //
  //  Suppose a large free segment is divided into a smaller free
  //  segment and a new object.  The second part of the newly divided
  //  memory must be registered as a new object, overwriting at most
  //  one first_start and one last_start entry.  Note that one of the
  //  newly divided two objects might be a new GCLAB.
  //
  //  Suppose postprocessing of a GCLAB finds that the original GCLAB
  //  has been divided into N objects.  Each of the N newly allocated
  //  objects will be registered, overwriting at most one first_start
  //  and one last_start entries.
  //
  //  No object registration operations are linear in the length of
  //  the registered objects.
  //
  // Consider further the following observations regarding object
  // registration costs:
  //
  //   1. The cost is paid once for each old-gen object (Except when
  //      an object is demoted and repromoted, in which case we would
  //      pay the cost again).
  //   2. The cost can be deferred so that there is no urgency during
  //      mutator copy-on-first-access promotion.  Background GC
  //      threads will update the object_starts array by post-
  //      processing the contents of retired PLAB buffers.
  //   3. The bet is that these costs are paid relatively rarely
  //      because:
  //      a) Most objects die young and objects that die in young-gen
  //         memory never need to be registered with the object_starts
  //         array.
  //      b) Most objects that are promoted into old-gen memory live
  //         there without further relocation for a relatively long
  //         time, so we get a lot of benefit from each investment
  //         in registering an object.

public:

  // The starting locations of objects contained within old-gen memory
  // are registered as part of the remembered set implementation.  This
  // information is required when scanning dirty card regions that are
  // spanned by objects beginning within preceding card regions.  It
  // is necessary to find the first and last objects that begin within
  // this card region.  Starting addresses of objects are required to
  // find the object headers, and object headers provide information
  // about which fields within the object hold addresses.
  //
  // The old-gen memory allocator invokes register_object() for any
  // object that is allocated within old-gen memory.  This identifies
  // the starting addresses of objects that span boundaries between
  // card regions.
  //
  // It is not necessary to invoke register_object at the very instant
  // an object is allocated.  It is only necessary to invoke it
  // prior to the next start of a garbage collection concurrent mark
  // or concurrent update-references phase.  An "ideal" time to register
  // objects is during post-processing of a GCLAB after the GCLAB is
  // retired due to depletion of its memory.
  //
  // register_object() does not perform synchronization.  In the case
  // that multiple threads are registering objects whose starting
  // addresses are within the same cluster, races between these
  // threads may result in corruption of the object-start data
  // structures.  Parallel GC threads should avoid registering objects
  // residing within the same cluster by adhering to the following
  // coordination protocols:
  //
  //  1. Align thread-local GCLAB buffers with some TBD multiple of
  //     card clusters.  The card cluster size is 32 KB.  If the
  //     desired GCLAB size is 128 KB, align the buffer on a multiple
  //     of 4 card clusters.
  //  2. Post-process the contents of GCLAB buffers to register the
  //     objects allocated therein.  Allow one GC thread at a
  //     time to do the post-processing of each GCLAB.
  //  3. Since only one GC thread at a time is registering objects
  //     belonging to a particular allocation buffer, no locking
  //     is performed when registering these objects.
  //  4. Any remnant of unallocated memory within an expended GC
  //     allocation buffer is not returned to the old-gen allocation
  //     pool until after the GC allocation buffer has been post
  //     processed.  Before any remnant memory is returned to the
  //     old-gen allocation pool, the GC thread that scanned this GC
  //     allocation buffer performs a write-commit memory barrier.
  //  5. Background GC threads that perform tenuring of young-gen
  //     objects without a GCLAB use a CAS lock before registering
  //     each tenured object.  The CAS lock assures both mutual
  //     exclusion and memory coherency/visibility.  Note that an
  //     object tenured by a background GC thread will not overlap
  //     with any of the clusters that are receiving tenured objects
  //     by way of GCLAB buffers.  Multiple independent GC threads may
  //     attempt to tenure objects into a shared cluster.  This is why
  //     sychronization may be necessary.  Consider the following
  //     scenarios:
  //
  //     a) If two objects are tenured into the same card region, each
  //        registration may attempt to modify the first-start or
  //        last-start information associated with that card region.
  //        Furthermore, because the representations of first-start
  //        and last-start information within the object_starts array
  //        entry uses different bits of a shared uint_16 to represent
  //        each, it is necessary to lock the entire card entry
  //        before modifying either the first-start or last-start
  //        information within the entry.
  //     b) Suppose GC thread X promotes a tenured object into
  //        card region A and this tenured object spans into
  //        neighboring card region B.  Suppose GC thread Y (not equal
  //        to X) promotes a tenured object into cluster B.  GC thread X
  //        will update the object_starts information for card A.  No
  //        synchronization is required.
  //     c) In summary, when background GC threads register objects
  //        newly tenured into old-gen memory, they must acquire a
  //        mutual exclusion lock on the card that holds the starting
  //        address of the newly tenured object.  This can be achieved
  //        by using a CAS instruction to assure that the previous
  //        values of first-offset and last-offset have not been
  //        changed since the same thread inquired as to their most
  //        current values.
  //
  //     One way to minimize the need for synchronization between
  //     background tenuring GC threads is for each tenuring GC thread
  //     to promote young-gen objects into distinct dedicated cluster
  //     ranges.
  //  6. The object_starts information is only required during the
  //     starting of concurrent marking and concurrent evacuation
  //     phases of GC.  Before we start either of these GC phases, the
  //     JVM enters a safe point and all GC threads perform
  //     commit-write barriers to assure that access to the
  //     object_starts information is coherent.


  // Notes on synchronization of register_object():
  //
  //  1. For efficiency, there is no locking in the implementation of register_object()
  //  2. Thus, it is required that users of this service assure that concurrent/parallel invocations of
  //     register_object() do pertain to the same card's memory range.  See discussion below to understand
  //     the risks.
  //  3. When allocating from a TLAB or GCLAB, the mutual exclusion can be guaranteed by assuring that each
  //     LAB's start and end are aligned on card memory boundaries.
  //  4. Use the same lock that guarantees exclusivity when performing free-list allocation within heap regions.
  //
  // Register the newly allocated object while we're holding the global lock since there's no synchronization
  // built in to the implementation of register_object().  There are potential races when multiple independent
  // threads are allocating objects, some of which might span the same card region.  For example, consider
  // a card table's memory region within which three objects are being allocated by three different threads:
  //
  // objects being "concurrently" allocated:
  //    [-----a------][-----b-----][--------------c------------------]
  //            [---- card table memory range --------------]
  //
  // Before any objects are allocated, this card's memory range holds no objects.  Note that:
  //   allocation of object a wants to set the has-object, first-start, and last-start attributes of the preceding card region.
  //   allocation of object b wants to set the has-object, first-start, and last-start attributes of this card region.
  //   allocation of object c also wants to set the has-object, first-start, and last-start attributes of this card region.
  //
  // The thread allocating b and the thread allocating c can "race" in various ways, resulting in confusion, such as last-start
  // representing object b while first-start represents object c.  This is why we need to require all register_object()
  // invocations associated with objects that are allocated from "free lists" to provide their own mutual exclusion locking
  // mechanism.

  // Reset the starts_object() information to false for all cards in the range between from and to.
  void reset_object_range(HeapWord* from, HeapWord* to);

  // register_object() requires that the caller hold the heap lock
  // before calling it.
  void register_object(HeapWord* address);

  // register_object_without_lock() does not require that the caller hold
  // the heap lock before calling it, under the assumption that the
  // caller has assured no other thread will endeavor to concurrently
  // register objects that start within the same card's memory region
  // as address.
  void register_object_without_lock(HeapWord* address);

  // During the reference updates phase of GC, we walk through each old-gen memory region that was
  // not part of the collection set and we invalidate all unmarked objects.  As part of this effort,
  // we coalesce neighboring dead objects in order to make future remembered set scanning more
  // efficient (since future remembered set scanning of any card region containing consecutive
  // dead objects can skip over all of them at once by reading only a single dead object header
  // instead of having to read the header of each of the coalesced dead objects.
  //
  // At some future time, we may implement a further optimization: satisfy future allocation requests
  // by carving new objects out of the range of memory that represents the coalesced dead objects.
  //
  // Suppose we want to combine several dead objects into a single coalesced object.  How does this
  // impact our representation of crossing map information?
  //  1. If the newly coalesced range is contained entirely within a card range, that card's last
  //     start entry either remains the same or it is changed to the start of the coalesced region.
  //  2. For the card that holds the start of the coalesced object, it will not impact the first start
  //     but it may impact the last start.
  //  3. For following cards spanned entirely by the newly coalesced object, it will change starts_object
  //     to false (and make first-start and last-start "undefined").
  //  4. For a following card that is spanned patially by the newly coalesced object, it may change
  //     first-start value, but it will not change the last-start value.
  //
  // The range of addresses represented by the arguments to coalesce_objects() must represent a range
  // of memory that was previously occupied exactly by one or more previously registered objects.  For
  // convenience, it is legal to invoke coalesce_objects() with arguments that span a single previously
  // registered object.
  //
  // The role of coalesce_objects is to change the crossing map information associated with all of the coalesced
  // objects.
  void coalesce_objects(HeapWord* address, size_t length_in_words);

  // The typical use case is going to look something like this:
  //   for each heapregion that comprises old-gen memory
  //     for each card number that corresponds to this heap region
  //       scan the objects contained therein if the card is dirty
  // To avoid excessive lookups in a sparse array, the API queries
  // the card number pertaining to a particular address and then uses the
  // card number for subsequent information lookups and stores.

  // If starts_object(card_index), this returns the word offset within this card
  // memory at which the first object begins.  If !starts_object(card_index), the
  // result is a don't care value -- asserts in a debug build.
  size_t get_first_start(size_t card_index) const;

  // If starts_object(card_index), this returns the word offset within this card
  // memory at which the last object begins.  If !starts_object(card_index), the
  // result is a don't care value.
  size_t get_last_start(size_t card_index) const;


  // Given a card_index, return the starting address of the first block in the heap
  // that straddles into the card. If the card is co-initial with an object, then
  // this would return the starting address of the heap that this card covers.
  // Expects to be called for a card affiliated with the old generation in
  // generational mode.
  HeapWord* block_start(size_t card_index) const;
};

// ShenandoahScanRemembered is a concrete class representing the
// ability to scan the old-gen remembered set for references to
// objects residing in young-gen memory.
//
// Scanning normally begins with an invocation of numRegions and ends
// after all clusters of all regions have been scanned.
//
// Throughout the scanning effort, the number of regions does not
// change.
//
// Even though the regions that comprise old-gen memory are not
// necessarily contiguous, the abstraction represented by this class
// identifies each of the old-gen regions with an integer value
// in the range from 0 to (numRegions() - 1) inclusive.
//

class ShenandoahScanRemembered: public CHeapObj<mtGC> {

private:
  ShenandoahDirectCardMarkRememberedSet* _rs;
  ShenandoahCardCluster* _scc;

  // Global card stats (cumulative)
  HdrSeq _card_stats_scan_rs[MAX_CARD_STAT_TYPE];
  HdrSeq _card_stats_update_refs[MAX_CARD_STAT_TYPE];
  // Per worker card stats (multiplexed by phase)
  HdrSeq** _card_stats;

  // The types of card metrics that we gather
  const char* _card_stats_name[MAX_CARD_STAT_TYPE] = {
   "dirty_run", "clean_run",
   "dirty_cards", "clean_cards",
   "max_dirty_run", "max_clean_run",
   "dirty_scan_objs",
   "alternations"
  };

  // The statistics are collected and logged separately for
  // card-scans for initial marking, and for updating refs.
  const char* _card_stat_log_type[MAX_CARD_STAT_LOG_TYPE] = {
   "Scan Remembered Set", "Update Refs"
  };

  int _card_stats_log_counter[2] = {0, 0};

public:
  ShenandoahScanRemembered(ShenandoahDirectCardMarkRememberedSet* rs) {
    _rs = rs;
    _scc = new ShenandoahCardCluster(rs);

    // We allocate ParallelGCThreads worth even though we usually only
    // use up to ConcGCThreads, because degenerate collections may employ
    // ParallelGCThreads for remembered set scanning.
    if (ShenandoahEnableCardStats) {
      _card_stats = NEW_C_HEAP_ARRAY(HdrSeq*, ParallelGCThreads, mtGC);
      for (uint i = 0; i < ParallelGCThreads; i++) {
        _card_stats[i] = new HdrSeq[MAX_CARD_STAT_TYPE];
      }
    } else {
      _card_stats = nullptr;
    }
  }

  ~ShenandoahScanRemembered() {
    delete _scc;
    if (ShenandoahEnableCardStats) {
      for (uint i = 0; i < ParallelGCThreads; i++) {
        delete _card_stats[i];
      }
      FREE_C_HEAP_ARRAY(HdrSeq*, _card_stats);
      _card_stats = nullptr;
    }
    assert(_card_stats == nullptr, "Error");
  }

  HdrSeq* card_stats(uint worker_id) {
    assert(worker_id < ParallelGCThreads, "Error");
    assert(ShenandoahEnableCardStats == (_card_stats != nullptr), "Error");
    return ShenandoahEnableCardStats ? _card_stats[worker_id] : nullptr;
  }

  HdrSeq* card_stats_for_phase(CardStatLogType t) {
    switch (t) {
      case CARD_STAT_SCAN_RS:
        return _card_stats_scan_rs;
      case CARD_STAT_UPDATE_REFS:
        return _card_stats_update_refs;
      default:
        guarantee(false, "No such CardStatLogType");
    }
    return nullptr; // Quiet compiler
  }

  // Card index is zero-based relative to first spanned card region.
  size_t card_index_for_addr(HeapWord* p);
  HeapWord* addr_for_card_index(size_t card_index);
  bool is_card_dirty(size_t card_index);
  bool is_write_card_dirty(size_t card_index);
  bool is_card_dirty(HeapWord* p);
  bool is_write_card_dirty(HeapWord* p);
  void mark_card_as_dirty(HeapWord* p);
  void mark_range_as_dirty(HeapWord* p, size_t num_heap_words);
  void mark_card_as_clean(HeapWord* p);
  void mark_range_as_clean(HeapWord* p, size_t num_heap_words);

  void reset_remset(HeapWord* start, size_t word_count) { _rs->reset_remset(start, word_count); }

  void merge_write_table(HeapWord* start, size_t word_count) { _rs->merge_write_table(start, word_count); }

  size_t cluster_for_addr(HeapWord* addr);
  HeapWord* addr_for_cluster(size_t cluster_no);

  void reset_object_range(HeapWord* from, HeapWord* to);
  void register_object(HeapWord* addr);
  void register_object_without_lock(HeapWord* addr);
  void coalesce_objects(HeapWord* addr, size_t length_in_words);

  HeapWord* first_object_in_card(size_t card_index) {
    if (_scc->starts_object(card_index)) {
      return addr_for_card_index(card_index) + _scc->get_first_start(card_index);
    } else {
      return nullptr;
    }
  }

  // Return true iff this object is "properly" registered.
  bool verify_registration(HeapWord* address, ShenandoahMarkingContext* ctx);

  // clear the cards to clean, and clear the object_starts info to no objects
  void mark_range_as_empty(HeapWord* addr, size_t length_in_words);

  // process_clusters() scans a portion of the remembered set
  // for references from old gen into young. Several worker threads
  // scan different portions of the remembered set by making parallel invocations
  // of process_clusters() with each invocation scanning different
  // "clusters" of the remembered set.
  //
  // An invocation of process_clusters() examines all of the
  // intergenerational references spanned by `count` clusters starting
  // with `first_cluster`.  The `oops` argument is a worker-thread-local
  // OopClosure that is applied to all "valid" references in the remembered set.
  //
  // A side-effect of executing process_clusters() is to update the remembered
  // set entries (e.g. marking dirty cards clean if they no longer
  // hold references to young-gen memory).
  //
  // An implementation of process_clusters() may choose to efficiently
  // address more typical scenarios in the structure of remembered sets. E.g.
  // in the generational setting, one might expect remembered sets to be very sparse
  // (low mutation rates in the old generation leading to sparse dirty cards,
  // each with very few intergenerational pointers). Specific implementations
  // may choose to degrade gracefully as the sparsity assumption fails to hold,
  // such as when there are sudden spikes in (premature) promotion or in the
  // case of an underprovisioned, poorly-tuned, or poorly-shaped heap.
  //
  // At the start of a concurrent young generation marking cycle, we invoke process_clusters
  // with ClosureType ShenandoahInitMarkRootsClosure.
  //
  // At the start of a concurrent evacuation phase, we invoke process_clusters with
  // ClosureType ShenandoahEvacuateUpdateRootsClosure.

  template <typename ClosureType>
  void process_clusters(size_t first_cluster, size_t count, HeapWord* end_of_range, ClosureType* oops,
                        bool use_write_table, uint worker_id);

  template <typename ClosureType>
  void process_humongous_clusters(ShenandoahHeapRegion* r, size_t first_cluster, size_t count,
                                  HeapWord* end_of_range, ClosureType* oops, bool use_write_table);

  template <typename ClosureType>
  void process_region_slice(ShenandoahHeapRegion* region, size_t offset, size_t clusters, HeapWord* end_of_range,
                            ClosureType* cl, bool use_write_table, uint worker_id);

  // To Do:
  //  Create subclasses of ShenandoahInitMarkRootsClosure and
  //  ShenandoahEvacuateUpdateRootsClosure and any other closures
  //  that need to participate in remembered set scanning.  Within the
  //  subclasses, add a (probably templated) instance variable that
  //  refers to the associated ShenandoahCardCluster object.  Use this
  //  ShenandoahCardCluster instance to "enhance" the do_oops
  //  processing so that we can:
  //
  //   1. Avoid processing references that correspond to clean card
  //      regions, and
  //   2. Set card status to CLEAN when the associated card region no
  //      longer holds inter-generatioanal references.
  //
  //  To enable efficient implementation of these behaviors, we
  //  probably also want to add a few fields into the
  //  ShenandoahCardCluster object that allow us to precompute and
  //  remember the addresses at which card status is going to change
  //  from dirty to clean and clean to dirty.  The do_oops
  //  implementations will want to update this value each time they
  //  cross one of these boundaries.
  void roots_do(OopIterateClosure* cl);

  // Log stats related to card/RS stats for given phase t
  void log_card_stats(uint nworkers, CardStatLogType t) PRODUCT_RETURN;
private:
  // Log stats for given worker id related into given summary card/RS stats
  void log_worker_card_stats(uint worker_id, HdrSeq* sum_stats) PRODUCT_RETURN;

  // Log given stats
  void log_card_stats(HdrSeq* stats) PRODUCT_RETURN;

  // Merge the stats from worked_id into the given summary stats, and clear the worker_id's stats.
  void merge_worker_card_stats_cumulative(HdrSeq* worker_stats, HdrSeq* sum_stats) PRODUCT_RETURN;
};


// A ShenandoahRegionChunk represents a contiguous interval of a ShenandoahHeapRegion, typically representing
// work to be done by a worker thread.
struct ShenandoahRegionChunk {
  ShenandoahHeapRegion* _r;      // The region of which this represents a chunk
  size_t _chunk_offset;          // HeapWordSize offset
  size_t _chunk_size;            // HeapWordSize qty
};

// ShenandoahRegionChunkIterator divides the total remembered set scanning effort into ShenandoahRegionChunks
// that are assigned one at a time to worker threads. (Here, we use the terms `assignments` and `chunks`
// interchangeably.) Note that the effort required to scan a range of memory is not necessarily a linear
// function of the size of the range.  Some memory ranges hold only a small number of live objects.
// Some ranges hold primarily primitive (non-pointer) data.  We start with larger chunk sizes because larger chunks
// reduce coordination overhead.  We expect that the GC worker threads that receive more difficult assignments
// will work longer on those chunks.  Meanwhile, other worker threads will repeatedly accept and complete multiple
// easier chunks.  As the total amount of work remaining to be completed decreases, we decrease the size of chunks
// given to individual threads.  This reduces the likelihood of significant imbalance between worker thread assignments
// when there is less meaningful work to be performed by the remaining worker threads while they wait for
// worker threads with difficult assignments to finish, reducing the overall duration of the phase.

class ShenandoahRegionChunkIterator : public StackObj {
private:
  // The largest chunk size is 4 MiB, measured in words.  Otherwise, remembered set scanning may become too unbalanced.
  // If the largest chunk size is too small, there is too much overhead sifting out assignments to individual worker threads.
  static const size_t _maximum_chunk_size_words = (4 * 1024 * 1024) / HeapWordSize;

  static const size_t _clusters_in_smallest_chunk = 4;

  // smallest_chunk_size is 4 clusters.  Each cluster spans 128 KiB.
  // This is computed from CardTable::card_size_in_words() * ShenandoahCardCluster::CardsPerCluster;
  static size_t smallest_chunk_size_words() {
      return _clusters_in_smallest_chunk * CardTable::card_size_in_words() *
             ShenandoahCardCluster::CardsPerCluster;
  }

  // The total remembered set scanning effort is divided into chunks of work that are assigned to individual worker tasks.
  // The chunks of assigned work are divided into groups, where the size of the typical group (_regular_group_size) is half the
  // total number of regions.  The first group may be larger than
  // _regular_group_size in the case that the first group's chunk
  // size is less than the region size.  The last group may be larger
  // than _regular_group_size because no group is allowed to
  // have smaller assignments than _smallest_chunk_size, which is 128 KB.

  // Under normal circumstances, no configuration needs more than _maximum_groups (default value of 16).
  // The first group "effectively" processes chunks of size 1 MiB (or smaller for smaller region sizes).
  // The last group processes chunks of size 128 KiB.  There are four groups total.

  // group[0] is 4 MiB chunk size (_maximum_chunk_size_words)
  // group[1] is 2 MiB chunk size
  // group[2] is 1 MiB chunk size
  // group[3] is 512 KiB chunk size
  // group[4] is 256 KiB chunk size
  // group[5] is 128 Kib shunk size (_smallest_chunk_size_words = 4 * 64 * 64
  static const size_t _maximum_groups = 6;

  const ShenandoahHeap* _heap;

  const size_t _regular_group_size;                        // Number of chunks in each group
  const size_t _first_group_chunk_size_b4_rebalance;
  const size_t _num_groups;                        // Number of groups in this configuration
  const size_t _total_chunks;

  shenandoah_padding(0);
  volatile size_t _index;
  shenandoah_padding(1);

  size_t _region_index[_maximum_groups];           // The region index for the first region spanned by this group
  size_t _group_offset[_maximum_groups];           // The offset at which group begins within first region spanned by this group
  size_t _group_chunk_size[_maximum_groups];       // The size of each chunk within this group
  size_t _group_entries[_maximum_groups];          // Total chunks spanned by this group and the ones before it.

  // No implicit copying: iterators should be passed by reference to capture the state
  NONCOPYABLE(ShenandoahRegionChunkIterator);

  // Makes use of _heap.
  size_t calc_regular_group_size();

  // Makes use of _regular_group_size, which must be initialized before call.
  size_t calc_first_group_chunk_size_b4_rebalance();

  // Makes use of _regular_group_size and _first_group_chunk_size_b4_rebalance, both of which must be initialized before call.
  size_t calc_num_groups();

  // Makes use of _regular_group_size, _first_group_chunk_size_b4_rebalance, which must be initialized before call.
  size_t calc_total_chunks();

public:
  ShenandoahRegionChunkIterator(size_t worker_count);
  ShenandoahRegionChunkIterator(ShenandoahHeap* heap, size_t worker_count);

  // Reset iterator to default state
  void reset();

  // Fills in assignment with next chunk of work and returns true iff there is more work.
  // Otherwise, returns false.  This is multi-thread-safe.
  inline bool next(struct ShenandoahRegionChunk* assignment);

  // This is *not* MT safe. However, in the absence of multithreaded access, it
  // can be used to determine if there is more work to do.
  inline bool has_next() const;
};


class ShenandoahScanRememberedTask : public WorkerTask {
 private:
  ShenandoahObjToScanQueueSet* _queue_set;
  ShenandoahObjToScanQueueSet* _old_queue_set;
  ShenandoahReferenceProcessor* _rp;
  ShenandoahRegionChunkIterator* _work_list;
  bool _is_concurrent;

 public:
  ShenandoahScanRememberedTask(ShenandoahObjToScanQueueSet* queue_set,
                               ShenandoahObjToScanQueueSet* old_queue_set,
                               ShenandoahReferenceProcessor* rp,
                               ShenandoahRegionChunkIterator* work_list,
                               bool is_concurrent);

  void work(uint worker_id);
  void do_work(uint worker_id);
};

// After Full GC is done, reconstruct the remembered set by iterating over OLD regions,
// registering all objects between bottom() and top(), and dirtying the cards containing
// cross-generational pointers.
class ShenandoahReconstructRememberedSetTask : public WorkerTask {
private:
  ShenandoahRegionIterator* _regions;

public:
  explicit ShenandoahReconstructRememberedSetTask(ShenandoahRegionIterator* regions);

  void work(uint worker_id) override;
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBERED_HPP
