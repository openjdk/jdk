
#include "precompiled.hpp"

#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/serialCompressor.hpp"
#include "gc/serial/serialGcRefProcProxyTask.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/serial/serialStringDedup.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/continuationGCSupport.inline.hpp"
#include "gc/shared/gcTrace.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/markBitMap.inline.hpp"
#include "gc/shared/referenceProcessor.hpp"
#include "gc/shared/referenceProcessorPhaseTimes.hpp"
#include "gc/shared/space.inline.hpp"
#include "gc/shared/strongRootsScope.hpp"
#include "gc/shared/taskqueue.hpp"
#include "gc/shared/weakProcessor.hpp"
#include "memory/iterator.hpp"
#include "memory/universe.hpp"
#include "oops/access.hpp"
#include "oops/compressedOops.inline.hpp"
#include "utilities/copy.hpp"
#include "utilities/stack.inline.hpp"

#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

uint SerialCompressor::_total_invocations = 0;

class SCDeadSpacer : StackObj {
  size_t _allowed_deadspace_words;
  bool _active;
  ContiguousSpace* _space;

public:
  explicit SCDeadSpacer(ContiguousSpace* space) :
  _allowed_deadspace_words(0),
  _active(false),
  _space(space) {
    size_t ratio = _space->allowed_dead_ratio();
    _active = ratio > 0;

    if (_active) {
      // We allow some amount of garbage towards the bottom of the space, so
      // we don't start compacting before there is a significant gain to be made.
      // Occasionally, we want to ensure a full compaction, which is determined
      // by the MarkSweepAlwaysCompactCount parameter.
      if ((SerialCompressor::_total_invocations % MarkSweepAlwaysCompactCount) != 0) {
        _allowed_deadspace_words = (space->capacity() * ratio / 100) / HeapWordSize;
      } else {
        _active = false;
      }
    }
  }

  bool insert_deadspace(HeapWord* dead_start, HeapWord* dead_end) {
    if (!_active) {
      return false;
    }

    size_t dead_length = pointer_delta(dead_end, dead_start);
    if (_allowed_deadspace_words >= dead_length) {
      _allowed_deadspace_words -= dead_length;
      CollectedHeap::fill_with_object(dead_start, dead_length);
      oop obj = cast_to_oop(dead_start);

      assert(dead_length == obj->size(), "bad filler object size");
      log_develop_trace(gc, compaction)("Inserting object to dead space: " PTR_FORMAT ", " PTR_FORMAT ", " SIZE_FORMAT "b",
                                        p2i(dead_start), p2i(dead_end), dead_length * HeapWordSize);

      return true;
    } else {
      _active = false;
      return false;
    }
  }
};

class SCUpdateRefsClosure;

// Implement the "compaction" part of the compressor GC algorithm.
class SCCompacter {
  friend class SCUpdateRefsClosure;

  // There are four spaces in total, but only the first three can be used after
  // compact. IOW, old and eden/from must be enough for all live objs
  static constexpr uint max_num_spaces = 4;

  // The number of heap words covered by each block as log-2 value.
  static inline constexpr uint log_words_per_block() {
    return LogBitsPerWord;
  }

  // The number of heap words covered by each block.
  static inline constexpr uint words_per_block() {
    return BitsPerWord;
  }

  // The number of bytes covered by each block.
  static inline constexpr uint bytes_per_block() {
    return BitsPerWord << LogBytesPerWord;
  }

  struct CompactionSpace {
    ContiguousSpace* _space;
    // Will be the new top after compaction is complete.
    HeapWord* _compaction_top;
    // The first dead word in this contiguous space. It's an optimization to
    // skip large chunk of live objects at the beginning of compaction.
    HeapWord* _first_dead;

    void init(ContiguousSpace* space) {
      _space = space;
      _compaction_top = space->bottom();
      _first_dead = nullptr;
    }
  };

  // The block offset table.
  HeapWord** const _bot;

  // The heap region covered by the BOT.
  const MemRegion _covered;

  // The marking bitmap.
  const MarkBitMap& _mark_bitmap;

  // The compaction spaces.
  CompactionSpace _spaces[max_num_spaces]{};

  // The num of spaces to be compacted, i.e. containing live objs.
  uint _num_spaces;

  // The index of the current space to compact into.
  uint _index;

  // For a given heap address, compute the index of the
  // corresponding block in the table.
  inline size_t addr_to_block_idx(HeapWord* addr) const {
    assert(addr >= _covered.start() && addr <= _covered.end(), "address must be in heap");
    return pointer_delta(addr, _covered.start()) >> log_words_per_block();
  }

  // Given a heap word (usually the start of an object), compute the forwarding address.
  inline HeapWord* forwardee(HeapWord* addr) const {
    assert(_mark_bitmap.is_marked(addr), "must be marked");
    HeapWord* block_base = align_down(addr, bytes_per_block());
    size_t block = addr_to_block_idx(addr);
    assert(_bot[block] != nullptr, "must have initialized BOT entry");
    return _bot[block] + _mark_bitmap.count_marked_words(block_base, addr);
  }

#if ASSERT
  // Clear table (only required for assertion in forwardee()).
  void clear(HeapWord* from, HeapWord* to) {
    size_t from_block = addr_to_block_idx(from);
    size_t to_block = addr_to_block_idx(align_up(to, bytes_per_block()));
    Copy::fill_to_words(reinterpret_cast<HeapWord*>(&_bot[from_block]), to_block - from_block);
  }
#endif // ASSERT

  // Get current compaction-top for space at index.
  HeapWord* get_compaction_top(uint index) const {
    return _spaces[index]._compaction_top;
  }

  // Get space at index.
  ContiguousSpace* get_space(uint index) const {
    return _spaces[index]._space;
  }

  // Get first dead word of space at index.
  HeapWord* get_first_dead(uint index) const {
    return _spaces[index]._first_dead;
  }

  // Records first dead word of space at index.
  void record_first_dead(uint index, HeapWord* first_dead) {
    assert(_spaces[index]._first_dead == nullptr, "should write only once");
    _spaces[index]._first_dead = first_dead;
  }

  // Build the block-offset-table for space at index.
  void build_table_for_space(uint idx) {
    ContiguousSpace* space = get_space(idx);
    HeapWord* bottom = space->bottom();
    HeapWord* top = space->top();

    // Clear table (only required for assertion in forwardee()).
    DEBUG_ONLY(clear(bottom, space->top());)

    bool record_first_dead_done = false;

    SCDeadSpacer dead_spacer(space);

    HeapWord* compact_top = get_compaction_top(_index);
    HeapWord* current = bottom;
    // Scan all live objects in the space.
    while (current < top) {
      HeapWord* next_marked = _mark_bitmap.get_next_marked_addr(current, top);
      // Handle unmarked chunk - either skip it, or dead-space it, to avoid excessive
      // copying by keeping subsequent objects in place.
      if (next_marked != current) {
        assert(!_mark_bitmap.is_marked(current), "must not be marked");
        if (!dead_spacer.insert_deadspace(current, next_marked)) {
          if (!record_first_dead_done) {
            record_first_dead(idx, current);
            record_first_dead_done = true;
          }
          // Store address of next live chunk into first non-live word to allow fast skip to next live during compaction.
          *(HeapWord**)current = next_marked;
          current = next_marked;
        }
      }

      HeapWord* next = _mark_bitmap.get_next_unmarked_addr(next_marked, top);
      size_t live_in_block = pointer_delta(next, current);

      while (live_in_block > pointer_delta(_spaces[_index]._space->end(),
                                           compact_top)) {
        // out-of-memory in this space
        _spaces[_index]._compaction_top = compact_top;
        _index++;
        assert(_index < max_num_spaces - 1, "the last space should not be used");
        compact_top = _spaces[_index]._compaction_top;
      }

      // Record addresses of the first live word of all blocks covered by the live span.
      current = next_marked;
      live_in_block = pointer_delta(next, current);
      size_t head = MIN2(pointer_delta(align_up(current, bytes_per_block()), current), live_in_block);
      if (head > 0) {
        HeapWord* block_start = align_down(current, bytes_per_block());
        // Count number of live words preceding the first object in the block. This must
        // be subtracted, because the BOT stores the forwarding address of the first live
        // *word*, not the first live *object* in the block.
        size_t num_live = _mark_bitmap.count_marked_words(block_start, current);
        // Note that we only record the address for blocks with live words. That
        // is ok, because we only ask for forwarding address of object-starts, i.e. live words.
        _bot[addr_to_block_idx(current)] = compact_top - num_live;
        assert(forwardee(current) == compact_top, "must match");
        compact_top += head;
        current += head;
      }
      // Middle block.
      while (pointer_delta(next, current) > words_per_block()) {
        _bot[addr_to_block_idx(current)] = compact_top;
        assert(forwardee(current) == compact_top, "must match");
        current += words_per_block();
        compact_top += words_per_block();
      }
      // Tail.
      size_t tail = pointer_delta(next, current);
      if (tail > 0) {
        _bot[addr_to_block_idx(current)] = compact_top;
        assert(forwardee(current) == compact_top, "must match");
        compact_top += tail;
        current += tail;
      }
      assert(current == next, "must arrive at next unmarked");
    }
    if (!record_first_dead_done) {
      record_first_dead(idx, top);
    }
    _spaces[_index]._compaction_top = compact_top;
  }

  // Allocate the block-offset-table.
  static HeapWord** allocate_table() {
    // TODO: Allocate table only for relevant (bottom-top) parts of spaces and keep them in
    // the CompactionSpace structure.
    MemRegion covered = SerialHeap::heap()->reserved_region();
    HeapWord* start = covered.start();
    HeapWord* end = covered.end();
    size_t num_blocks = align_up(pointer_delta(end, start), words_per_block()) / words_per_block();
    return NEW_C_HEAP_ARRAY(HeapWord*, num_blocks, mtGC);
  }

public:
  explicit SCCompacter(SerialHeap* heap, MarkBitMap& mark_bitmap) :
    _bot(allocate_table()),
    _covered(heap->reserved_region()),
    _mark_bitmap(mark_bitmap)
 {
    // In this order so that heap is compacted towards old-gen.
    _spaces[0].init(heap->old_gen()->space());
    _spaces[1].init(heap->young_gen()->eden());
    _spaces[2].init(heap->young_gen()->from());

    bool is_promotion_failed = (heap->young_gen()->from()->next_compaction_space() != nullptr);
    if (is_promotion_failed) {
      _spaces[3].init(heap->young_gen()->to());
      _num_spaces = 4;
    } else {
      _num_spaces = 3;
    }
    _index = 0;
  }

  ~SCCompacter() {
    FREE_C_HEAP_ARRAY(HeapWord*, _bot);
  }

  void phase2_prepare() {
    for (uint i = 0; i < _num_spaces; ++i) {
      build_table_for_space(i);
    }
  }

  // Compact live objects in a space.
  void compact_space(uint idx) const;

  void phase3_compact() {
    for (uint i = 0; i < _num_spaces; ++i) {
      compact_space(i);
    }
  }
};

// Updates references in GC roots and heap objects.
class SCUpdateRefsClosure : public BasicOopIterateClosure {
private:
  const SCCompacter& _compacter;

  template<class T>
  void do_oop_work(T* ptr) {
    T heap_oop = RawAccess<>::oop_load(ptr);
    if (!CompressedOops::is_null(heap_oop)) {
      oop obj = CompressedOops::decode_raw_not_null(heap_oop);
      assert(SerialHeap::heap()->is_in_reserved(obj), "should be in heap");
      oop forwardee = cast_to_oop(_compacter.forwardee(cast_from_oop<HeapWord*>(obj)));
      if (forwardee != obj) {
        RawAccess<IS_NOT_NULL>::oop_store(ptr, forwardee);
      }
    }
  }
public:
  explicit SCUpdateRefsClosure(const SCCompacter& compacter) :
    _compacter(compacter) {}

  void do_oop(oop* ptr) override {
    do_oop_work(ptr);
  }
  void do_oop(narrowOop* ptr) override {
    do_oop_work(ptr);
  }
};

// Compact live objects in a space.
void SCCompacter::compact_space(uint idx) const {
  ContiguousSpace* space = get_space(idx);
  HeapWord* bottom = space->bottom();
  HeapWord* top = space->top();
  HeapWord* current = _mark_bitmap.get_next_marked_addr(bottom, top);
  SCUpdateRefsClosure cl(*this);

  TenuredSpace* tenured_space = SerialHeap::heap()->old_gen()->space();

  // Visit all live objects in the space.
  while (current < top) {
    assert(_mark_bitmap.is_marked(current), "must be marked");
    HeapWord* next_dead = _mark_bitmap.get_next_unmarked_addr(current, top);
    HeapWord* obj_start = current;
    HeapWord* fwd = forwardee(obj_start);
    HeapWord* chunk_fwd = fwd;
    // Scan all consecutive live objects in the current live chunk and update their references.
    while (obj_start < next_dead) {
      assert(forwardee(obj_start) == fwd, "object and forwardee must move by same amount within chunk: forwardee: " PTR_FORMAT, p2i(forwardee(obj_start)));
      oop obj = cast_to_oop(obj_start);
      assert(oopDesc::is_oop(obj), "must be oop");

      // Update references of object.
      obj->oop_iterate(&cl);

      // We need to update the offset table so that the beginnings of objects can be
      // found during scavenge.  Note that we are updating the offset table based on
      // where the object will be once the compaction phase finishes.
      size_t size_in_words = obj->size();
      if (tenured_space->is_in_reserved(fwd)) {
        tenured_space->update_for_block(fwd, fwd + size_in_words);
      }

      // Advance to next object in chunk.
      obj_start += size_in_words;
      fwd += size_in_words;
    }
    assert(obj_start == next_dead, "sanity");

    // Copy the whole chunk.
    if (chunk_fwd != current) {
      Copy::aligned_conjoint_words(current, chunk_fwd, pointer_delta(next_dead, current));
    }

    // Advance to next live object.
    assert(!_mark_bitmap.is_marked(next_dead), "must not be live");
    if (next_dead >= top) {
      break;
    }
    if (next_dead < get_first_dead(idx)) {
      // Dead-spacer object, not a record of next live object.
      current = _mark_bitmap.get_next_marked_addr(next_dead, top);
    } else {
      // We stored the address of the next live object in the first unmarked word
      // after the current live chunk.
      HeapWord* next = *(HeapWord**)next_dead;
      assert(next == _mark_bitmap.get_next_marked_addr(next_dead, top), "must match");
      current = next;
    }
  }

  // Reset top and unused memory
  space->set_top(get_compaction_top(idx));
  if (ZapUnusedHeapArea) {
    space->mangle_unused_area();
  }
}

SerialCompressor::SerialCompressor(STWGCTimer* gc_timer):
  _mark_bitmap(),
  _marking_stack(),
  _objarray_stack(),
  _string_dedup_requests(),
  _gc_timer(gc_timer),
  _gc_tracer() {
  // Initialize underlying marking bitmap.
  SerialHeap* heap = SerialHeap::heap();
  MemRegion reserved = heap->reserved_region();
  size_t bitmap_size = MarkBitMap::compute_size(reserved.byte_size());
  ReservedSpace bitmap(bitmap_size);
  _mark_bitmap_region = MemRegion((HeapWord*) bitmap.base(), bitmap.size() / HeapWordSize);
  os::commit_memory_or_exit((char *)_mark_bitmap_region.start(), _mark_bitmap_region.byte_size(), false,
                            "Cannot commit bitmap memory");
  _mark_bitmap.initialize(heap->reserved_region(), _mark_bitmap_region);
}

SerialCompressor::~SerialCompressor() {
  os::release_memory((char*)_mark_bitmap_region.start(), _mark_bitmap_region.byte_size());
}


// Update all GC roots.
static void update_roots(SCCompacter& compacter) {
  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_adjust);
  SerialHeap* heap = SerialHeap::heap();
  SCUpdateRefsClosure adjust_pointer_closure(compacter);
  CLDToOopClosure adjust_cld_closure(&adjust_pointer_closure, ClassLoaderData::_claim_stw_fullgc_adjust);
  CodeBlobToOopClosure code_closure(&adjust_pointer_closure, CodeBlobToOopClosure::FixRelocations);
  heap->process_roots(SerialHeap::SO_AllCodeCache,
                      &adjust_pointer_closure,
                      &adjust_cld_closure,
                      &adjust_cld_closure,
                      &code_closure);

  WeakProcessor::oops_do(&adjust_pointer_closure);
}

// Entry point.
void SerialCompressor::invoke_at_safepoint(bool clear_all_softrefs) {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at a safepoint");

  SerialHeap* gch = SerialHeap::heap();
#ifdef ASSERT
  if (gch->soft_ref_policy()->should_clear_all_soft_refs()) {
    assert(clear_all_softrefs, "Policy should have been checked earlier");
  }
#endif

  gch->trace_heap_before_gc(&_gc_tracer);

  // Increment the invocation count
  _total_invocations++;

  // Capture used regions for each generation that will be
  // subject to collection, so that card table adjustments can
  // be made intelligently (see clear / invalidate further below).
  gch->save_used_regions();

  phase1_mark(clear_all_softrefs);

  SCCompacter compacter{gch, _mark_bitmap};
  {
    GCTraceTime(Info, gc, phases) tm("Phase 2: Build block-offset-table", _gc_timer);
    compacter.phase2_prepare();
  }

  // Don't add any more derived pointers during phase3
#if COMPILER2_OR_JVMCI
  assert(DerivedPointerTable::is_active(), "Sanity");
  DerivedPointerTable::set_active(false);
#endif

  {
    GCTraceTime(Info, gc, phases) tm("Phase 3: Compact heap", _gc_timer);
    update_roots(compacter);
    compacter.phase3_compact();
  }

  // Set saved marks for allocation profiler (and other things? -- dld)
  // (Should this be in general part?)
  gch->save_marks();

  bool is_young_gen_empty = (gch->young_gen()->used() == 0);
  gch->rem_set()->maintain_old_to_young_invariant(gch->old_gen(), is_young_gen_empty);

  gch->prune_scavengable_nmethods();

  // Update heap occupancy information which is used as
  // input to soft ref clearing policy at the next gc.
  Universe::heap()->update_capacity_and_used_at_gc();

  // Signal that we have completed a visit to all live objects.
  Universe::heap()->record_whole_heap_examined_timestamp();

  gch->trace_heap_after_gc(&_gc_tracer);
}

template<class T>
void SerialCompressor::mark_and_push(T* ptr) {
  T heap_oop = RawAccess<>::oop_load(ptr);
  if (!CompressedOops::is_null(heap_oop)) {
    oop obj = CompressedOops::decode_not_null(heap_oop);
    if (mark_object(obj)) {
      _marking_stack.push(obj);
    }
  }
}

class SCMarkAndPushClosure: public ClaimMetadataVisitingOopIterateClosure {
private:
  SerialCompressor& _compressor;

  template<typename T>
  void do_oop_work(T* ptr) {
    _compressor.mark_and_push(ptr);
  }

public:
  SCMarkAndPushClosure(ClassLoaderData::Claim claim, SerialCompressor& compressor) :
    ClaimMetadataVisitingOopIterateClosure(claim),
    _compressor(compressor) {
    set_ref_discoverer_internal(compressor.ref_processor());
  }

  void do_oop(oop* p)       override { do_oop_work(p); }
  void do_oop(narrowOop* p) override { do_oop_work(p); }
};

class SCFollowRootClosure: public BasicOopIterateClosure {
private:
  SerialCompressor& _compressor;

  template<class T>
  void follow_root(T* ptr) {
    assert(!Universe::heap()->is_in(ptr), "roots shouldn't be things within the heap");
    _compressor.mark_and_push(ptr);
    _compressor.follow_stack();
  }

public:
  explicit SCFollowRootClosure(SerialCompressor& compressor) :
    _compressor(compressor) { }

  void do_oop(oop* ptr)       override { follow_root(ptr); }
  void do_oop(narrowOop* ptr) override { follow_root(ptr); }
};

class SCFollowStackClosure: public VoidClosure {
private:
  SerialCompressor& _compressor;
public:
  explicit SCFollowStackClosure(SerialCompressor& compressor) :
    _compressor(compressor) { }
  void do_void() override {
    _compressor.follow_stack();
  }
};

class SCIsAliveClosure: public BoolObjectClosure {
  MarkBitMap& _mark_bitmap;
public:
  explicit SCIsAliveClosure(MarkBitMap& mark_bitmap) :
    _mark_bitmap(mark_bitmap) { }
  bool do_object_b(oop ptr) override {
    return _mark_bitmap.is_marked(ptr);
  }
};

class SCKeepAliveClosure: public OopClosure {
private:
  SerialCompressor& _compressor;
  template<class T>
  void do_oop_work(T* ptr) {
    _compressor.mark_and_push(ptr);
  }
public:
  explicit SCKeepAliveClosure(SerialCompressor& compressor) :
    _compressor(compressor) { }
  void do_oop(oop* p)       override { do_oop_work(p); }
  void do_oop(narrowOop* p) override { do_oop_work(p); }
};

bool SerialCompressor::mark_object(oop obj) {
  HeapWord* addr = cast_from_oop<HeapWord*>(obj);
  if (!_mark_bitmap.is_marked(addr)) {
    if (StringDedup::is_enabled() &&
        java_lang_String::is_instance(obj) &&
        SerialStringDedup::is_candidate_from_mark(obj)) {
      _string_dedup_requests.add(obj);
    }

    // Do the transform while we still have the header intact,
    // which might include important class information.
    ContinuationGCSupport::transform_stack_chunk(obj);

    _mark_bitmap.mark_range(addr, obj->size());
    return true;
  } else {
    return false;
  }
}

void SerialCompressor::push_objarray(objArrayOop array, size_t index) {
  ObjArrayTask task(array, index);
  assert(task.is_valid(), "bad ObjArrayTask");
  _objarray_stack.push(task);
}

void SerialCompressor::follow_array(objArrayOop array) {
  SCMarkAndPushClosure mark_and_push_closure(ClassLoaderData::_claim_stw_fullgc_mark, *this);
  mark_and_push_closure.do_klass(array->klass());

  if (array->length() > 0) {
    push_objarray(array, 0);
  }
}

void SerialCompressor::follow_object(oop obj) {
  assert(_mark_bitmap.is_marked(obj), "p must be marked");
  if (obj->is_objArray()) {
    follow_array((objArrayOop)obj);
  } else {
    SCMarkAndPushClosure mark_and_push_closure(ClassLoaderData::_claim_stw_fullgc_mark, *this);
    obj->oop_iterate(&mark_and_push_closure);
  }
}

void SerialCompressor::follow_array_chunk(objArrayOop array, int index) {
  const int len = array->length();
  const int beg_index = index;
  assert(beg_index < len || len == 0, "index too large");

  const int stride = MIN2(len - beg_index, (int) ObjArrayMarkingStride);
  const int end_index = beg_index + stride;

  SCMarkAndPushClosure mark_and_push_closure(ClassLoaderData::_claim_stw_fullgc_mark, *this);
  array->oop_iterate_range(&mark_and_push_closure, beg_index, end_index);

  if (end_index < len) {
    push_objarray(array, end_index); // Push the continuation.
  }
}

void SerialCompressor::follow_stack() {
  do {
    while (!_marking_stack.is_empty()) {
      oop obj = _marking_stack.pop();
      assert(_mark_bitmap.is_marked(obj), "p must be marked");
      follow_object(obj);
    }
    // Process ObjArrays one at a time to avoid marking stack bloat.
    if (!_objarray_stack.is_empty()) {
      ObjArrayTask task = _objarray_stack.pop();
      follow_array_chunk(objArrayOop(task.obj()), task.index());
    }
  } while (!_marking_stack.is_empty() || !_objarray_stack.is_empty());
}

void SerialCompressor::phase1_mark(bool clear_all_softrefs) {
  // Recursively traverse all live objects and mark them
  GCTraceTime(Info, gc, phases) tm("Phase 1: Mark live objects", _gc_timer);

  SerialHeap* gch = SerialHeap::heap();

  ClassLoaderDataGraph::verify_claimed_marks_cleared(ClassLoaderData::_claim_stw_fullgc_mark);

  AlwaysTrueClosure always_true_closure;
  _ref_processor = new ReferenceProcessor(&always_true_closure);
  _ref_processor->start_discovery(clear_all_softrefs);

  {
    StrongRootsScope srs(0);
    SCMarkAndPushClosure mark_and_push_closure(ClassLoaderData::_claim_stw_fullgc_mark, *this);
    CLDToOopClosure follow_cld_closure(&mark_and_push_closure, ClassLoaderData::_claim_stw_fullgc_mark);
    SCFollowRootClosure follow_root_closure(*this);

    CLDClosure* weak_cld_closure = ClassUnloading ? nullptr : &follow_cld_closure;
    MarkingCodeBlobClosure mark_code_closure(&follow_root_closure, !CodeBlobToOopClosure::FixRelocations, true);
    gch->process_roots(SerialHeap::SO_None,
                       &follow_root_closure,
                       &follow_cld_closure,
                       weak_cld_closure,
                       &mark_code_closure);
  }

  SCIsAliveClosure is_alive(_mark_bitmap);

  // Process reference objects found during marking
  {
    GCTraceTime(Debug, gc, phases) tm_m("Reference Processing", _gc_timer);

    SCKeepAliveClosure keep_alive(*this);
    SCFollowStackClosure follow_stack_closure(*this);
    ReferenceProcessorPhaseTimes pt(_gc_timer, _ref_processor->max_num_queues());
    SerialGCRefProcProxyTask task(is_alive, keep_alive, follow_stack_closure);
    const ReferenceProcessorStats& stats = _ref_processor->process_discovered_references(task, pt);
    pt.print_all_references();
    _gc_tracer.report_gc_reference_stats(stats);
  }

  // This is the point where the entire marking should have completed.
  assert(_marking_stack.is_empty(), "Marking should have completed");

  {
    GCTraceTime(Debug, gc, phases) tm_m("Weak Processing", _gc_timer);
    WeakProcessor::weak_oops_do(&is_alive, &do_nothing_cl);
  }

  {
    GCTraceTime(Debug, gc, phases) tm_m("Class Unloading", _gc_timer);

    ClassUnloadingContext* ctx = ClassUnloadingContext::context();

    bool unloading_occurred;
    {
      CodeCache::UnlinkingScope scope(&is_alive);

      // Unload classes and purge the SystemDictionary.
      unloading_occurred = SystemDictionary::do_unloading(_gc_timer);

      // Unload nmethods.
      CodeCache::do_unloading(unloading_occurred);
    }

    {
      GCTraceTime(Debug, gc, phases) t("Purge Unlinked NMethods", _gc_timer);
      // Release unloaded nmethod's memory.
      ctx->purge_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) ur("Unregister NMethods", _gc_timer);
      gch->prune_unlinked_nmethods();
    }
    {
      GCTraceTime(Debug, gc, phases) t("Free Code Blobs", _gc_timer);
      ctx->free_code_blobs();
    }

    // Prune dead klasses from subklass/sibling/implementor lists.
    Klass::clean_weak_klass_links(unloading_occurred);

    // Clean JVMCI metadata handles.
    JVMCI_ONLY(JVMCI::do_unloading(unloading_occurred));
  }

  {
    GCTraceTime(Debug, gc, phases) tm_m("Report Object Count", _gc_timer);
    _gc_tracer.report_object_count_after_gc(&is_alive, nullptr);
  }

  delete _ref_processor;
}
