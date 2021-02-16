/*
 * Copyright (c) 2021, Amazon.com, Inc. or its affiliates.  All rights reserved.
 *
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

#include <stdint.h>
#include "memory/iterator.hpp"
#include "gc/shenandoah/shenandoahCardTable.hpp"


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
//               that object will be scanned in its entirety). For these
//               reasons, it is advisable for the multiple worker threads
//               to be flexible in the number of clusters to be
//               processed by each thread.
//
// A cluster represents a "natural" quantum of work to be performed by
// a parallel GC thread's background remembered set scanning efforts.
// The notion of cluster is similar to the notion of stripe in the
// implementation of parallel GC card scanning.  However, a cluster is
// typically smaller than a stripe, enabling finer grain division of
// labor between multiple threads.
//
// For illustration, consider the following possible JVM configurations:
//
//   Scenario 1:
//     RegionSize is 128 MB
//     Span of a card entry is 512 B
//     Each card table entry consumes 1 B
//     Assume one long word of card table entries represents a cluster.
//       This long word holds 8 card table entries, spanning a
//       total of 4KB
//     The number of clusters per region is 128 MB / 4 KB = 32K
//
//   Scenario 2:
//     RegionSize is 128 MB
//     Span of each card entry is 128 B
//     Each card table entry consumes 1 bit
//     Assume one int word of card tables represents a cluster.
//       This int word holds 32 card table entries, spanning a
//       total of 4KB
//     The number of clusters per region is 128 MB / 4 KB = 32K
//
//   Scenario 3:
//     RegionSize is 128 MB
//     Span of each card entry is 512 B
//     Each card table entry consumes 1 bit
//     Assume one long word of card tables represents a cluster.
//       This long word holds 64 card table entries, spanning a
//       total of 32 KB
//     The number of clusters per region is 128 MB / 32 KB = 4K
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
//  Let rs represent the relevant RememberedSet implementation
//    (an instance of ShenandoahDirectCardMarkRememberedSet or an instance
//     of a to-be-implemented ShenandoahBufferWithSATBRememberedSet)
//
//  for each ShenandoahHeapRegion old_region in the whole heap
//    determine the cluster number of the first cluster belonging
//      to that region
//    for each cluster contained within that region
//      Assure that exactly one worker thread initializes each
//      cluster of overreach memory by invoking:
//
//        rs->initialize_overreach(cluster_no, cluster_count)
//
//      in separate threads.  (Divide up the clusters so that
//      different threads are responsible for initializing different
//      clusters.  Initialization cost is essentially identical for
//      each cluster.)
//
//  Next, we repeat the process for invocations of process_Clusters.
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
//        c) some clusters are spanned by very large objects that
//           begin in some other cluster.  When a large object
//           beginning in a preceding cluster spans large portions of
//           this cluster, the processing of this cluster gets a
//           "free ride" because the thread responsible for processing
//           the cluster that holds the object's header does the
//           processing.
//        d) in the case that the end of this cluster is spanned by a
//           very large object, the processing of this cluster will
//           be responsible for examining the entire object,
//           potentially requiring this thread to process large amounts
//           of memory pertaining to other clusters.
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
//  Once all clusters have been processed, the gang of GC worker
//  threads collaborate to merge the overreach data.
//
//  for each ShenandoahHeapRegion old_region in the whole heap
//    determine the cluster number of the first cluster belonging
//      to that region
//    for each cluster contained within that region
//      Assure that exactly one worker thread initializes each
//      cluster of overreach memory by invoking:
//
//        rs->merge_overreach(cluster_no, cluster_count)
//
//      in separate threads.  (Divide up the clusters so that
//      different threads are responsible for merging different
//      clusters.  Merging cost is essentially identical for
//      each cluster.)
//
// Though remembered set scanning is designed to run concurrently with
// mutator threads, the current implementation of remembered set
// scanning runs in parallel during a GC safepoint.  Furthermore, the
// current implementation of remembered set scanning never clears a
// card once it has been marked.  Since the current implementation
// never clears marked pages, the current implementation does not
// invoke initialize_overreach() or merge_overreach().
//
// These limitations will be addressed in future enhancements to the
// existing implementation.

class ShenandoahReferenceProcessor;
class ShenandoahConcurrentMark;
class ShenandoahHeap;
class CardTable;

class ShenandoahDirectCardMarkRememberedSet: public CHeapObj<mtGC> {

private:

  // Use symbolic constants defined in cardTable.hpp
  //  CardTable::card_shift = 9;
  //  CardTable::card_size = 512;
  //  CardTable::card_size_in_words = 64;

  //  CardTable::clean_card_val()
  //  CardTable::dirty_card_val()

  ShenandoahHeap *_heap;
  CardTable *_card_table;
  uint32_t _card_shift;
  size_t _total_card_count;
  uint32_t _cluster_count;
  HeapWord *_whole_heap_base;   // Points to first HeapWord of data contained within heap memory
  HeapWord *_whole_heap_end;
  uint8_t *_byte_map;           // Points to first entry within the card table
  uint8_t *_byte_map_base;      // Points to byte_map minus the bias computed from address of heap memory
  uint8_t *_overreach_map;      // Points to first entry within the overreach card table
  uint8_t *_overreach_map_base; // Points to overreach_map minus the bias computed from address of heap memory

public:
  // count is the number of cards represented by the card table.
  ShenandoahDirectCardMarkRememberedSet(CardTable *card_table, size_t total_card_count);
  ~ShenandoahDirectCardMarkRememberedSet();

  // Card index is zero-based relative to _byte_map.
  uint32_t card_index_for_addr(HeapWord *p);
  HeapWord *addr_for_card_index(uint32_t card_index);
  bool is_card_dirty(uint32_t card_index);
  void mark_card_as_dirty(uint32_t card_index);
  void mark_card_as_clean(uint32_t card_index);
  void mark_overreach_card_as_dirty(uint32_t card_index);
  bool is_card_dirty(HeapWord *p);
  void mark_card_as_dirty(HeapWord *p);
  void mark_card_as_clean(HeapWord *p);
  void mark_overreach_card_as_dirty(void *p);
  uint32_t cluster_count();

  // Called by multiple GC threads at start of concurrent mark and evacuation phases.  Each parallel GC thread typically
  // initializes a different subranges of all overreach entries.
  void initialize_overreach(uint32_t first_cluster, uint32_t count);

  // Called by GC thread at end of concurrent mark or evacuation phase.  Each parallel GC thread typically merges different
  // subranges of all overreach entries.
  void merge_overreach(uint32_t first_cluster, uint32_t count);
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
// In the initial implementation, we assume that scanning of card
// table entries occurs only while the JVM is at a safe point.  Thus,
// there is no synchronization required between GC threads that are
// scanning card-table entries and marking certain entries that were
// previously dirty as clean, and mutator threads which would possibly
// be marking certain card-table entries as dirty.
//
// There is however a need to implement concurrency control and memory
// coherency between multiple GC threads that scan the remembered set
// in parallel.  The desire is to divide the complete scanning effort
// into multiple clusters of work that can be independently processed
// by individual threads without need for synchronizing efforts
// between the work performed by each task.  The term "cluster" of
// work is similar to the term "stripe" as used in the implementation
// of Parallel GC.
//
// Complexity arises when an object to be scanned crosses the boundary
// between adjacent cluster regions.  Here is the protocol that is
// followed:
//
//  1. We implement a supplemental data structure known as the overreach
//     card table.  The thread that is responsible for scanning each
//     cluster of card-table entries is granted exclusive access to
//     modify the associated card-table entries.  In the case that a
//     thread scans a very large object that reaches into one or more
//     following clusters, that thread has exclusive access to the
//     overreach card table for all of the entries belonging to the
//     following clusters that are spanned by this large object.
//     After all clusters have been scanned, the scanning threads
//     briefly synchronize to merge the contents of the overreach
//     entries with the traditional card table entries using logical-
//     and operations.
//  2. Every object is scanned in its "entirety" by the thread that is
//     responsible for the cluster that holds its starting address.
//     Entirety is in quotes because there are various situations in
//     which some portions of the object will not be scanned by this
//     thread:
//     a) If an object spans multiple card regions, all of which are
//        contained within the same cluster, the scanning thread
//        consults the existing card-table entries and does not scan
//        portions of the object that are not currently dirty.
//     b) For any cluster that is spanned in its entirety by a very
//        large object, the GC thread that scans this object assumes
//        full responsibility for maintenance of the associated
//        card-table entries.
//     c) If a cluster is partially spanned by an object originating
//        in a preceding cluster, the portion of the object that
//        partially spans the following cluster is scanned in its
//        entirety (because the thread that is responsible for
//        scanning the object cannot rely upon the card-table entries
//        associated with the following cluster).  Whenever references
//        to young-gen memory are found within the scanned data, the
//        associated overreach card table entries are marked as dirty
//        by the scanning thread.
//  3. If a cluster is spanned in its entirety by an object that
//     originates within a preceding cluster's memory, the thread
//     assigned to examine this cluster does absolutely nothing.  The
//     thread assigned to scan the cluster that holds the object's
//     starting address takes full responsibility for scanning the
//     entire object and updating the associated card-table entries.
//  4. If a cluster is spanned partially by an object that originates
//     within a preceding cluster's memory, the thread assigned to
//     examine this cluster marks the card-table entry as clean for
//     each card table that is fully spanned by this overreaching
//     object.  If a card-table entry's memory is partially spanned
//     by the overreaching object, the thread sets the card-table
//     entry to clean if it was previously dirty and if the portion
//     of the card-table entry's memory that is not spanned by the
//     overreaching object does not hold pointers to young-gen
//     memory.
//  5. While examining a particular card belonging to a particular
//     cluster, if an object reaches beyond the end of its card
//     memory, the thread "scans" all portions of the object that
//     correspond to DIRTY card entries within the current cluster and
//     all portions of the object that reach into following clustesr.
//     After this object is scanned, continue scanning with the memory
//     that follows this object if this memory pertains to the same
//     cluster.  Otherwise, consider this cluster's memory to have
//     been fully examined.
//
// Discussion:
//  Though this design results from careful consideration of multiple
//  design objectives, it is subject to various criticisms.  Some
//  discussion of the design choices is provided here:
//
//  1. Note that remembered sets are a heuristic technique to avoid
//     the need to scan all of old-gen memory with each young-gen
//     collection.  If we sometimes scan a bit more memory than is
//     absolutely necessary, that should be considered a reasonable
//     compromise.  This compromise is already present in the sizing
//     of card table memory areas.  Note that a single dirty pointer
//     within a 512-byte card region forces the "unnecessary" scanning
//     of 63 = ((512 - 8 = 504) / 8) pointers.
//  2. One undesirable aspect of this design is that we sometimes have
//     to scan large amounts of memory belonging to very large
//     objects, even for parts of the very large object that do not
//     correspond to dirty card table entries.  Note that this design
//     limits the amount of non-dirty scanning that might have to
//     be performed for these very large objects.  In particular, only
//     the last part of the very large object that extends into but
//     does not completely span a particular cluster is unnecessarily
//     scanned.  Thus, for each very large object, the maximum
//     over-scan is the size of memory spanned by a single cluster.
//  3. The representation of pointer location descriptive information
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
//  3. (A corollary) In the case that an old-gen object spans the
//     boundary between two heap regions, the card regions that
//     correspond to the span of this object will be consecutively
//     numbered.


// ShenandoahCardCluster abstracts access to the remembered set
// and also keeps track of crossing map information to allow efficient
// resolution of object start addresses.
//
// ShenandoahCardCluster supports all of the services of
// RememberedSet, plus it supports register_object() and lookup_object().
//
// There are two situations under which we need to know the location
// at which the object spanning the start of a particular card-table
// memory region begins:
//
// 1. When we begin to scan dirty card memory that is not the
//    first card region within a cluster, and the object that
//    crosses into this card memory was not previously scanned,
//    we need to find where that object starts so we can scan it.
//    (Asides: if the objects starts within a previous cluster, it
//     has already been scanned.  If the object starts within this
//     cluster and it spans at least one card region that is dirty
//     and precedes this card region within the cluster, then it has
//     already been scanned.)
// 2. When we are otherwise done scanning a complete cluster, if the
//    last object within the cluster reaches into the following
//    cluster, we need to scan this object.  Thus, we need to find
//    its starting location.
//
// The RememberedSet template parameter is intended to represent either
//     ShenandoahDirectCardMarkRememberedSet, or a to-be-implemented
//     ShenandoahBufferWithSATBRememberedSet.
template<typename RememberedSet>
class ShenandoahCardCluster: public CHeapObj<mtGC> {

private:
  RememberedSet *_rs;

public:
  static const uint32_t CardsPerCluster = 64;

  ShenandoahCardCluster(RememberedSet *rs) {
    _rs = rs;
  }

  ~ShenandoahCardCluster() {
  }

  // There is one entry within the object_starts array for each
  // card entry.  The interpretation of the data contained within each
  // object_starts entry is described below:
  //
  // Bits 0x003f: Value ranges from 0-63, which is multiplied by 8
  //              to obtain the offset at which the first object
  //              beginning within this card region begins.
  // Bits 0x0fc0: Value ranges from 0-63, which is multiplied by 8 to
  //              obtain the offset at which the last object beginning
  //              within this card region begins.
  // Bits 0x8000: This bit is on if an object starts within this card
  //              region.
  //
  // In the most recent implementation of ShenandoahScanRemembered::process_clusters(),
  // there is no need for the get_crossing_object_start() method function, so there is no
  // need to maintain the following information.  The comment is left in place for now in
  // case we find it necessary to add support for this service at a later time.
  //
  // Bits 0x7fff: If no object starts within this card region, the
  //              remaining bits of the object_starts array represent
  //              the absolute word offset within the enclosing
  //              cluster's memory of the starting address for the
  //              object that spans the start of this card region's
  //              memory.  If the spanning object begins in memory
  //              that precedes this card region's cluster, the value
  //              stored in these bits is the special value 0x7fff.
  //              (Note that the maximum value required to represent a
  //              spanning object from within the current cluster is
  //              ((63 * 64) - 8), which equals 0x0fbf.
  //
  // In the absence of the need to support get_crossing_object_start(),
  // here is discussion of performance:
  //
  //  Suppose multiple garbage objects are coalesced during GC sweep
  //  into a single larger "free segment".  As each two objects are
  //  coalesced together, the start information pertaining to the second
  //  object must be removed from the objects_starts array.  If the
  //  second object had been been the first object within card memory,
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
  //      processing the contents of retired GCLAB buffers.
  //   3. The bet is that these costs are paid relatively rarely
  //      because:
  //      a) Most objects die young and objects that die in young-gen
  //         memory never need to be registered with the object_starts
  //         array.
  //      b) Most objects that are promoted into old-gen memory live
  //         there without further relocation for a relatively long
  //         time, so we get a lot of benefit from each investment
  //         in registering an object.

private:
  const uint32_t CardByteOffsetMultiplier = 8;
  const uint32_t CardWordOffsetMultiplier = 1;

#ifdef IMPLEMENT_THIS_OPTIMIZATION_LATER

  // This bit is set iff at least one object starts within a
  // particular card region.
  const uint_16 ObjectStartsInCardRegion = 0x8000;
  const uint_16 FirstStartBits = 0x003f;
  const uint_16 LastStartBits = 0x0fc0;
  const uint_16 FirstStartShift = 0;
  const uint_16 LastStartShift = 6;
  const uint_16 CrossingObjectOverflow = 0x7fff;

  uint_16 *object_starts;

public:
  inline void set_first_start(uint32_t card_index, uint8_t value) {
    object_starts[card_index] &= ~FirstStartBits;
    object_starts[card_index] |= (FirstStartBits & (value << FirstStartShift));
  }

  inline void set_last_start(uint32_t card_index, uint8_t value) {
    object_starts[card_index] &= ~LastStartBits;
    object_starts[card_index] |= (LastStartBits & (value << LastStartShift));
  }

  inline void set_has_object_bit(uint32_t card_index) {
    object_starts[card_index] |= ObjectStartsInCardRegion;
  }

  // This has side effect of clearing ObjectStartsInCardRegion bit.
  inline void set_crossing_object_start(uint32_t card_index, uint_16 crossing_offset) {
    object_starts[card_index] = crossing_offset;
  }
#endif  // IMPLEMENT_THIS_OPTIMIZATION_LATER

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
  // or concurrent evacuation phase.  An "ideal" time to register
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

  static void register_object(void *address, uint32_t length_in_bytes) {

#ifdef IMPLEMENT_THIS_OPTIMIZATION_LATER
    uint32_t card_at_start = cardAtAddress(address);
    // The end of this object is contained within this card
    uint32_t card_at_end = cardAtAddress(address + length_in_bytes);

    uint32_t cluster_at_start = clusterAtCard(card_at_start);
    uint32_t cluster_at_end = clusterAtCard(card_at_end);

    void *card_start_address = addressAtCard(card_at_start);
    void *cluster_start_address = addressAtCluster(cluster_at_start);

    uint8_t offset_in_card =
      (address - card_start_address) / CardOffsetMultiplier;

    if (!getHashObjectBit(card_at_start)) {
      set_has_object_bit(card_at_start);
      set_first_start(card_at_start, offset_in_card);
      set_last_start(card_at_start, offset_in_card);
    } else {
      if (offset_in_card < get_first_start(card_at_start))
        set_first_start(card_at_start, offset_in_card);
      if (offset_in_card > get_last_start(card_at_start))
        set_last_start(card_at_last, offset_in_card);
    }

#ifdef SUPPORT_FOR_GET_CROSSING_OBJECT_START_NO_LONGER_REQUIRED

    // What is the last card within the current cluster?
    uint32_t next_cluster = cluster_at_start + 1;
    uint32_t card_at_next_cluster = cardAtCluster(next_cluster);
    uint32_t last_card_of_this_cluster = card_at_next_cluster - 1;
    uint32_t last_card_in_cluster = ((card_at_end < last_card_of_this_cluster) ? card_at_end: last_card_of_this_cluster);

    uint_16 crossing_map_within_cluster = address - cluster_at_start;

    for (uint32_t card_to_update = card_at_start + 1; card_to_update < last_card_in_cluster; card_to_update++)
      set_crossing_object_start(card_to_update, crossing_map_within_cluster);

    // If the last card region of this cluster is completely spanned
    // by this new object,  set its crossing object start as well.
    if (cluster_at_end > cluster_at_start) {
      set_crossing_object_start(card_to_update++, crossing_map_within_cluster);

      // Now, we have to update all spanned cards that reside within the
      // following clusters.
      while (card_to_update < card_at_end)
        set_crossing_object_start(card_to_update++, CrossingObjectOverflow);

      // Cases:
      //
      // 1. The region for card_at_end is not spanned at all because the
      //    end of the new object aligns with the start of the last
      //    card region.  In this case, we do nothing with the
      //    card_at_end entry of the object_starts array.
      //
      // 2. The region for card_at_end is spanned partially.  In this
      //    case, we do nothing with the card_at_end entry of the
      //    object_starts array.
      //
      // 3. Do not need to consider the case that the region for
      //    card_at_end is spanned entirely.  If the last region
      //    spanned by a newly registered object is spanned in its
      //    entirety, then card_at_end will identify the card region
      //    that follows the object rather than the last region spanned
      //    by the object.

      // Bottom line: no further work to be done in any of these cases.
    }
    // Otherwise, the last card region of this cluster is not
    // completely spanned by this new object.  Leave the object_starts
    // array entry alone.  Two cases:
    //
    //  a) This newly registered object represents a consolidation of
    //     multiple smaller objects, not including the object that
    //     follows the consolidation.
    //  b) This newly registered object is created by splitting a
    //     previously existing object.  In this case, the other
    //     objects resulting from the split will be registered
    //     separately before it is necessary to lookup any information
    //     within the object_starts array.
    //

#endif  // SUPPORT_FOR_GET_CROSSING_OBJECT_START_NO_LONGER_REQUIRED

#else   // IMPLEMENT_THIS_OPTIMIZATION_LATER

    // Do nothing for now as we have a brute-force implementation
    // of findSpanningObject().

#endif  // IMPLEMENT_THIS_OPTIMIZATION_LATER
  }

  // The typical use case is going to look something like this:
  //   for each heapregion that comprises olg-gen memory
  //     for each card number that corresponds to this heap region
  //       scan the objects contained therein if the card is dirty
  // To avoid excessive lookups in a sparse array, the API queries
  // the card number pertaining to a particular address and then uses the
  // card noumber for subsequent information lookups and stores.

  // Returns true iff an object is known to start within the card memory
  // associated with addr p.
  // Returns true iff an object is known to start within the card memory
  // associated with addr p.
  bool has_object(uint32_t card_index);

  // If has_object(card_index), this returns the word offset within this card
  // memory at which the first object begins.
  uint32_t get_first_start(uint32_t card_index);

  // If has_object(card_index), this returns the word offset within this card
  // memory at which the last object begins.
  uint32_t get_last_start(uint32_t card_index);

  // If !has_object(card_index), this returns the offset within the
  // enclosing cluster at which the object spanning the start of this
  // card memory begins, or returns 0x7fff if the spanning object
  // starts before the enclosing cluster.
  uint32_t get_crossing_object_start(uint32_t card_index);

  // Card index is zero-based relative to first spanned card region.
  uint32_t card_index_for_addr(HeapWord *p);
  HeapWord *addr_for_card_index(uint32_t card_index);
  bool is_card_dirty(uint32_t card_index);
  void mark_card_as_dirty(uint32_t card_index);
  void mark_card_as_clean(uint32_t card_index);
  void mark_overreach_card_as_dirty(uint32_t card_index);
  bool is_card_dirty(HeapWord *p);
  void mark_card_as_dirty(HeapWord *p);
  void mark_card_as_clean(HeapWord *p);
  void mark_overreach_card_as_dirty(void *p);
  uint32_t cluster_count();
  void initialize_overreach(uint32_t first_cluster, uint32_t count);
  void merge_overreach(uint32_t first_cluster, uint32_t count);
};

// ShenandoahScanRemembered is a concrete class representing the
// ability to scan the old-gen remembered set for references to
// objects residing in young-gen memory.
//
// In an initial implementation, remembered set scanning happens
// during a HotSpot safepoint.  This greatly simplifies the
// implementation and improves efficiency of remembered set scanning,
// but this design choice increases pause times experienced at the
// start of concurrent marking and concurrent evacuation.  Pause times
// will be especially long if old-gen memory holds many pointers to
// young-gen memory.
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

template<typename RememberedSet>
class ShenandoahScanRemembered: public CHeapObj<mtGC> {

private:

  ShenandoahCardCluster<RememberedSet> *_scc;

public:
  // How to instantiate this object?
  //   ShenandoahDirectCardMarkRememberedSet *rs =
  //       new ShenandoahDirectCardMarkRememberedSet();
  //   scr = new
  //     ShenandoahScanRememberd<ShenandoahDirectCardMarkRememberedSet>(rs);
  //
  // or, after the planned implementation of
  // ShenandoahBufferWithSATBRememberedSet has been completed:
  //
  //   ShenandoahBufferWithSATBRememberedSet *rs =
  //       new ShenandoahBufferWithSATBRememberedSet();
  //   scr = new
  //     ShenandoahScanRememberd<ShenandoahBufferWithSATBRememberedSet>(rs);


  ShenandoahScanRemembered(RememberedSet *rs);
  ~ShenandoahScanRemembered();

  // process_clusters() scans a portion of the remembered set during a JVM
  // safepoint as part of the root scanning activities that serve to
  // initiate concurrent scanning and concurrent evacuation.  Multiple
  // threads may scan different portions of the remembered set by
  // making parallel invocations of process_clusters() with each
  // invocation scanning different clusters of the remembered set.
  //
  // An invocation of process_clusters() examines all of the
  // intergenerational references spanned by count clusters starting
  // with first_cluster.  The oops argument is assumed to represent a
  // thread-local OopClosure into which addresses of intergenerational
  // pointer values will be accumulated for the purposes of root scanning.
  //
  // A side effect of executing process_clusters() is to update the card
  // table entries, marking dirty cards as clean if they no longer
  // hold references to young-gen memory.  (THIS IS NOT YET IMPLEMENTED.)
  //
  // The implementation of process_clusters() is designed to efficiently
  // minimize work in the large majority of cases for which the
  // associated cluster has very few dirty card-table entries.
  //
  // At initialization of concurrent marking, invoke process_clusters with
  // ClosureType equal to ShenandoahInitMarkRootsClosure.
  //
  // At initialization of concurrent evacuation, invoke process_clusters with
  // ClosureType equal to ShenandoahEvacuateUpdateRootsClosure.

  // This is big enough it probably shouldn't be in-lined.  On the other hand, there are only a few places this
  // code is called from, so it might as well be in-lined.  The "real" reason I'm inlining at the moment is because
  // the template expansions were making it difficult for the link/loader to resolve references to the template-
  // parameterized implementations of this service.
  template <typename ClosureType>
  void process_clusters(uint worker_id, ShenandoahReferenceProcessor* rp, ShenandoahConcurrentMark* cm, uint32_t first_cluster, uint32_t count,
                        HeapWord *end_of_range, ClosureType *oops);

  uint32_t cluster_for_addr(HeapWord *addr);

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

};

typedef ShenandoahScanRemembered<ShenandoahDirectCardMarkRememberedSet> RememberedScanner;

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHSCANREMEMBERED_HPP
