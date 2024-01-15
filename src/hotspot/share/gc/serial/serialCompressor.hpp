
#ifndef SHARE_GC_SERIAL_SERIALCOMPRESSOR_HPP
#define SHARE_GC_SERIAL_SERIALCOMPRESSOR_HPP

#include "gc/shared/gcTrace.hpp"
#include "gc/shared/markBitMap.hpp"
#include "gc/shared/space.hpp"
#include "gc/shared/stringdedup/stringDedup.hpp"
#include "gc/shared/taskqueue.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.hpp"
#include "utilities/stack.hpp"

class ContiguousSpace;
class Generation;
class SCCompactClosure;
class SCFollowRootClosure;
class SCFollowStackClosure;
class SCKeepAliveClosure;
class SCMarkAndPushClosure;
class Space;
class STWGCTimer;

/**
 * Implements compacting full-GC for the Serial GC. This is based on
 * Abuaiadh et al. [2004] and Kermany and Petrank [2006], as described in
 * The Garbage Collection Handbook, Second Edition by Jones, Hosking and Moss [2023].
 *
 * The Full GC is carried out in 3 phases:
 * 1. Marking
 * 2. Preparation
 * 3. Compaction
 *
 * The algorithm uses 2 major data-structures:
 * - A marking bitmap. Each bit represents one word of the heap (or larger blocks
 *   according to MinObjAlignment).
 * - A block-offset-table. Each word of the table stores the destination address
 *   of each block of the heap. A block spans as many words (or larger blocks,
 *   according to MinObjAlignment) as can be represented in a single word in
 *   the marking bitmap. For example, on a 64-bit system and the default
 *   1-word-alignment, each block would span 64 words of the heap. Note that the
 *   sizes have been chosen such that we achieve a reasonable compromise between
 *   the size of the table (1/64th of the heap size) and performance (for each
 *   forwarding, we only need to scan at most 64 bits - which can be done very
 *   efficiently, see population_count.hpp. It could be done even more efficiently
 *   once we allow ~SSE4.2 ISA and use the popcount instruction on x86.
 *
 * The algorithm then works as follows:
 *
 * 1. Marking: This is pretty much textbook marking algorithm, with the difference
 *    that we are setting one bit for each live object in the heap, not only one
 *    bit per object. We are going to use this information to calculate the
 *    forwarding pointers of each object.
 * 2. Preparation: Here we are building the block-offset-table. The basic idea
 *    is to scan the heap bottom to top, keep track of compaction-top for each
 *    block and record the compaction target for the first live word of each
 *    block in the block-offset-table. (Notice that the first live word of a block
 *    will often be from an object that is overlapping from a previous block.)
 *    Later (during compaction) we can easily calculate the forwarding address
 *    of each object by finding its block, loading the corresponding
 *    block-destination, and adding the number of live words preceding the object
 *    in its block:
 *    forwarding(obj) = bot[block(obj)] + count_live_words(block_base(obj), obj)
 * 3. Compaction: This compacts the heap and updates all references in a single
 *    sequential pass over the heap.
 *    Scan heap bottom to top, for each live object:
 *    - Update all its references to point to their forwarded locations
 *    - Copy the object itself to its forwarded location
 *
 * Notice that the actual implementation is more complex than this description.
 * In particular, during marking, we also need to take care of reference-processing,
 * class-unloading, string-deduplication. The preparation phase is complicated by
 * the heap being divided into generations and spaces - we need to ensure that whole
 * blocks are compacted into the same space, and that all its objects, including
 * the tails that overlap into an adjacent block, fit into the destination space.
 */

class CSpaceClosure : public StackObj {
public:
  virtual void do_space(ContiguousSpace* space) = 0;
};

// A structure to represent a point at which objects are being copied
// during compaction.
class CompactPoint : public StackObj {
public:
  Generation* gen;
  ContiguousSpace* space;

  CompactPoint(Generation* g = nullptr) :
    gen(g), space(nullptr) {}
};

class SerialCompressor : public StackObj {
  friend class SCCompactClosure;
  friend class SCFollowRootClosure;
  friend class SCFollowStackClosure;
  friend class SCKeepAliveClosure;
  friend class SCMarkAndPushClosure;
private:

  // Memory area of the underlying marking bitmap.
  MemRegion  _mark_bitmap_region;
  // The marking bitmap.
  MarkBitMap _mark_bitmap;
  // The marking stack.
  Stack<oop,mtGC> _marking_stack;
  // Separate marking stack for object-array-chunks.
  Stack<ObjArrayTask, mtGC> _objarray_stack;

  // String-dedup support.
  StringDedup::Requests _string_dedup_requests;

  STWGCTimer* _gc_timer;
  SerialOldTracer _gc_tracer;
  ReferenceProcessor* _ref_processor;

  // Space iteration support.
  void iterate_spaces_of_generation(CSpaceClosure& cl, Generation* gen) const;
  void iterate_spaces(CSpaceClosure& cl) const;

  // Phase 1: Marking.
  void phase1_mark(bool clear_all_softrefs);
  // Phase 2: Building the block-offset-table.
  void phase2_build_bot();
  // Phase 3: Compacting and updating references.
  void phase3_compact_and_update();

  // Various markingsupport methods.
  bool mark_object(oop obj);
  void follow_array(objArrayOop array);
  void follow_array_chunk(objArrayOop array, int index);
  void follow_object(oop obj);
  void push_objarray(objArrayOop array, size_t index);
  ReferenceProcessor* ref_processor() const { return _ref_processor; }
  void follow_stack();
  template<class T>
  void mark_and_push(T* p);

  // Update GC roots.
  void compact_space(ContiguousSpace* space) const;

public:
  SerialCompressor(STWGCTimer* gc_timer);
  ~SerialCompressor();

  // Entry point.
  void invoke_at_safepoint(bool clear_all_softrefs);
};

#endif // SHARE_GC_SERIAL_SERIALCOMPRESSOR_HPP
