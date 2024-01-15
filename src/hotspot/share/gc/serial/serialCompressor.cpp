
#include "precompiled.hpp"

#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/systemDictionary.hpp"
#include "code/codeCache.hpp"
#include "gc/serial/cardTableRS.hpp"
#include "gc/serial/generation.hpp"
#include "gc/serial/serialCompressor.hpp"
#include "gc/serial/serialGcRefProcProxyTask.hpp"
#include "gc/serial/serialHeap.hpp"
#include "gc/serial/serialStringDedup.hpp"
#include "gc/shared/classUnloadingContext.hpp"
#include "gc/shared/continuationGCSupport.hpp"
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
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "utilities/copy.hpp"
#include "utilities/stack.inline.hpp"

#if INCLUDE_JVMCI
#include "jvmci/jvmci.hpp"
#endif

// Return the number of heap words covered by each block.
static inline int words_per_block() {
  return BitsPerWord << LogMinObjAlignment;
}

static inline int bytes_per_block() {
  return BitsPerWord << (LogMinObjAlignment + LogBytesPerWord);
}

static HeapWord** allocate_table() {
  MemRegion covered = SerialHeap::heap()->reserved_region();
  HeapWord* start = covered.start();
  HeapWord* end = covered.end();
  size_t num_blocks = align_up(pointer_delta(end, start), words_per_block()) / words_per_block();
  return NEW_C_HEAP_ARRAY(HeapWord*, num_blocks, mtGC);
}

class SCUpdateRefsClosure;

// Implement the "compaction" part of the compressor GC algorithm.
class SCCompacter {
  friend class SCUpdateRefsClosure;

  // There are four spaces in total, but only the first three can be used after
  // compact. IOW, old and eden/from must be enough for all live objs
  static constexpr uint max_num_spaces = 4;

  struct CompactionSpace {
    ContiguousSpace* _space;
    // Will be the new top after compaction is complete.
    HeapWord* _compaction_top;
    // The first dead word in this contiguous space. It's an optimization to
    // skip large chunk of live objects at the beginning.
    HeapWord* _first_dead;

    void init(ContiguousSpace* space) {
      _space = space;
      _compaction_top = space->bottom();
      _first_dead = nullptr;
    }
  };

  // The block offset table.
  HeapWord** _bot;

  // The marking bitmap.
  const MarkBitMap& _mark_bitmap;

  // The heap region covered by the BOT.
  const MemRegion _covered;

  CompactionSpace _spaces[max_num_spaces];

  // The num of spaces to be compacted, i.e. containing live objs.
  uint _num_spaces;

  uint _index;

  // For a given heap address, compute the index of the
  // corresponding block in the table.
  inline size_t addr_to_block_idx(HeapWord* addr) const {
    assert(addr >= _covered.start() && addr <= _covered.end(), "address must be in heap");
    return pointer_delta(addr, _covered.start()) / words_per_block();
  }

  inline HeapWord* forwardee(HeapWord* addr) const {
    assert(_mark_bitmap.is_marked(addr), "must be marked");
    HeapWord* block_base = align_down(addr, bytes_per_block());
    size_t block = addr_to_block_idx(addr);
    assert(_bot[block] != nullptr, "must have initialized BOT entry");
    return _bot[block] + _mark_bitmap.count_marked_words(block_base, addr);
  }

  void clear(HeapWord* from, HeapWord* to) {
    size_t from_block = addr_to_block_idx(from);
    size_t to_block = addr_to_block_idx(align_up(to, bytes_per_block()));
    Copy::fill_to_words(reinterpret_cast<HeapWord*>(&_bot[from_block]), to_block - from_block);
  }

  void record(HeapWord* first_in_block, HeapWord* to) {
    HeapWord* block_start = align_down(first_in_block, bytes_per_block());
    // Count number of live words preceding the first object in the block. This must
    // be subtracted, because the BOT stores the forwarding address of the first live
    // *word*, not the first live *object* in the block.
    size_t num_live = _mark_bitmap.count_marked_words(block_start, first_in_block);
    // Note that we only record the address for blocks where objects start. That
    // is ok, because we only ask for forwarding address of first word of objects.
    _bot[addr_to_block_idx(first_in_block)] = to - num_live;
    assert(forwardee(first_in_block) == to, "must match");
  }

  HeapWord* get_compaction_top(uint index) const {
    return _spaces[index]._compaction_top;
  }

  HeapWord* get_first_dead(uint index) const {
    return _spaces[index]._first_dead;
  }

  ContiguousSpace* get_space(uint index) const {
    return _spaces[index]._space;
  }

  void record_first_dead(uint index, HeapWord* first_dead) {
    assert(_spaces[index]._first_dead == nullptr, "should write only once");
    _spaces[index]._first_dead = first_dead;
  }

  void build_table_for_space(ContiguousSpace* space) {
    HeapWord* bottom = space->bottom();
    HeapWord* top = space->top();

    // Clear table.
    clear(bottom, space->top());

    HeapWord* compact_top = get_compaction_top(_index);
    HeapWord* current = _mark_bitmap.get_next_marked_addr(bottom, top);
    // Scan all live objects in the space.
    while (current < top) {
      assert(_bot[addr_to_block_idx(current)] == nullptr, "must be new block");
      HeapWord* block_end = MIN2(top, align_up(current + 1, bytes_per_block()));
      size_t live_in_block = 0;
      HeapWord* first_in_block = current;
      // Scan all live objects in the block to calculate the number of live words of all
      // objects in the block. Note that this can be larger than the block when a
      // trailing object spans into subsequent block(s). This is intentional:
      // All objects which start in a block will share the same block-base-address,
      // and thus must be compacted into the same destinatioin space.
      while (current < block_end) {
	oop obj = cast_to_oop(current);
	assert(oopDesc::is_oop(obj), "must be oop start");
	size_t obj_size = obj->size();
	live_in_block += obj_size;

	// Advance to next live object.
	current = _mark_bitmap.get_next_marked_addr(current + obj_size, top);
      }

      // Check if block fits into current compaction space, and switch to next,
      // if necessary. The compaction space must have enough space left to
      // accomodate all objects that start in the block.
      while (live_in_block > pointer_delta(_spaces[_index]._space->end(),
	                                   _spaces[_index]._compaction_top)) {
        // out-of-memory in this space
	_index++;
	assert(_index < max_num_spaces - 1, "the last space should not be used");
      }

      // Record address of the first live word in this block.
      record(first_in_block, _spaces[_index]._compaction_top);

      _spaces[_index]._compaction_top += live_in_block;
    }
  }

public:
  explicit SCCompacter(SerialHeap* heap, MarkBitMap& mark_bitmap) :
    _bot(allocate_table()),
    _mark_bitmap(mark_bitmap),
    _covered(heap->reserved_region())
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
      ContiguousSpace* space = get_space(i);
      build_table_for_space(space);
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

class SCUpdateRefsClosure : public BasicOopIterateClosure {
private:
  const SCCompacter& _compacter;

  template<class T>
  void do_oop_work(T* p) {
    T heap_oop = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(heap_oop)) {
      oop obj = CompressedOops::decode_raw_not_null(heap_oop);
      assert(SerialHeap::heap()->is_in_reserved(obj), "should be in heap");
      oop forwardee = cast_to_oop(_compacter.forwardee(cast_from_oop<HeapWord*>(obj)));
      if (forwardee != obj) {
        RawAccess<IS_NOT_NULL>::oop_store(p, forwardee);
      }
    }
  }
public:
  SCUpdateRefsClosure(const SCCompacter& compacter) :
    _compacter(compacter) {}

  void do_oop(oop* p) {
    do_oop_work(p);
  }
  void do_oop(narrowOop* p) {
    do_oop_work(p);
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
    oop obj = cast_to_oop(current);
    assert(oopDesc::is_oop(obj), "must be oop");
    size_t size_in_words = obj->size();

    // Update references of object.
    obj->oop_iterate(&cl);

    // Copy object itself.
    HeapWord* fwd = forwardee(current);
    if (current != fwd) {
      Copy::aligned_conjoint_words(current, fwd, size_in_words);
    }

    // We need to update the offset table so that the beginnings of objects can be
    // found during scavenge.  Note that we are updating the offset table based on
    // where the object will be once the compaction phase finishes.
    if (tenured_space->is_in_reserved(fwd)) {
      tenured_space->update_for_block(fwd, fwd + size_in_words);
    }

    // Advance to next live object.
    current = _mark_bitmap.get_next_marked_addr(current + size_in_words, top);
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
  ReservedSpace bitmap(bitmap_size, MAX2(os::vm_page_size(), (size_t)bytes_per_block()));
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
void SerialCompressor::mark_and_push(T* p) {
  T heap_oop = RawAccess<>::oop_load(p);
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
  void do_oop_work(T* p) {
    _compressor.mark_and_push(p);
  }

public:
  SCMarkAndPushClosure(int claim, SerialCompressor& compressor) :
    ClaimMetadataVisitingOopIterateClosure(claim),
    _compressor(compressor) {
    set_ref_discoverer_internal(compressor.ref_processor());
  }

  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
};

class SCFollowRootClosure: public BasicOopIterateClosure {
private:
  SerialCompressor& _compressor;

  template<class T>
  void follow_root(T* p) {
    assert(!Universe::heap()->is_in(p), "roots shouldn't be things within the heap");
    _compressor.mark_and_push(p);
    _compressor.follow_stack();
  }

public:
  SCFollowRootClosure(SerialCompressor& compressor) :
    _compressor(compressor) { }

  void do_oop(oop* p)       { follow_root(p); }
  void do_oop(narrowOop* p) { follow_root(p); }
};

class SCFollowStackClosure: public VoidClosure {
private:
  SerialCompressor& _compressor;
public:
  SCFollowStackClosure(SerialCompressor& compressor) :
    _compressor(compressor) { }
  void do_void() {
    _compressor.follow_stack();
  }
};

class SCIsAliveClosure: public BoolObjectClosure {
  MarkBitMap& _mark_bitmap;
public:
  SCIsAliveClosure(MarkBitMap& mark_bitmap) :
    _mark_bitmap(mark_bitmap) { }
  bool do_object_b(oop p) {
    return _mark_bitmap.is_marked(p);
  }
};

class SCKeepAliveClosure: public OopClosure {
private:
  SerialCompressor& _compressor;
  template<class T>
  void do_oop_work(T* p) {
    _compressor.mark_and_push(p);
  }
public:
  SCKeepAliveClosure(SerialCompressor& compressor) :
    _compressor(compressor) { }
  void do_oop(oop* p)       { do_oop_work(p); }
  void do_oop(narrowOop* p) { do_oop_work(p); }
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

void SerialCompressor::iterate_spaces_of_generation(CSpaceClosure& cl, Generation* gen) const {
  ContiguousSpace* space = gen->first_compaction_space();
  while (space != nullptr) {
    cl.do_space(space);
    space = space->next_compaction_space();
  }
}

void SerialCompressor::iterate_spaces(CSpaceClosure& cl) const {
  SerialHeap* heap = SerialHeap::heap();
  iterate_spaces_of_generation(cl, heap->old_gen());
  iterate_spaces_of_generation(cl, heap->young_gen());
}
