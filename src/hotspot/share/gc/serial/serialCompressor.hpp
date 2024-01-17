
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

class ReferenceProcessor;
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
 *   of each block of the heap. A block spans 64 words of the heap. Note that the
 *   sizes have been chosen such that we achieve a reasonable compromise between
 *   the size of the table (1/64th of the heap size) and performance (for each
 *   forwarding, we only need to scan at most 64 bits - which can be done very
 *   efficiently, see population_count.hpp.
 *
 * The algorithm then works as follows:
 *
 * 1. Marking: This is pretty much a textbook marking algorithm, with the difference
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

class SerialCompressor : public StackObj {
  friend class SCDeadSpacer;
  friend class SCFollowRootClosure;
  friend class SCFollowStackClosure;
  friend class SCKeepAliveClosure;
  friend class SCMarkAndPushClosure;
private:

  static uint _total_invocations;

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

  // Phase 1: Marking. (Phases 2 and 3 are implemented in the SCCompacter class,
  // see serialCompressor.cpp)
  void phase1_mark(bool clear_all_softrefs);

  // Various marking support methods.
  bool mark_object(oop obj);
  void follow_array(objArrayOop array);
  void follow_array_chunk(objArrayOop array, int index);
  void follow_object(oop obj);
  void push_objarray(objArrayOop array, size_t index);
  ReferenceProcessor* ref_processor() const { return _ref_processor; }
  void follow_stack();
  template<class T>
  void mark_and_push(T* ptr);

public:
  explicit SerialCompressor(STWGCTimer* gc_timer);
  ~SerialCompressor();

  // Entry point.
  void invoke_at_safepoint(bool clear_all_softrefs);
};

#endif // SHARE_GC_SERIAL_SERIALCOMPRESSOR_HPP
