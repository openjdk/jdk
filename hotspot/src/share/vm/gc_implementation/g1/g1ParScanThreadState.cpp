/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "gc_implementation/g1/g1OopClosures.inline.hpp"
#include "gc_implementation/g1/g1ParScanThreadState.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oop.pcgc.inline.hpp"
#include "runtime/prefetch.inline.hpp"

#ifdef _MSC_VER // the use of 'this' below gets a warning, make it go away
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif // _MSC_VER

G1ParScanThreadState::G1ParScanThreadState(G1CollectedHeap* g1h, uint queue_num, ReferenceProcessor* rp)
  : _g1h(g1h),
    _refs(g1h->task_queue(queue_num)),
    _dcq(&g1h->dirty_card_queue_set()),
    _ct_bs(g1h->g1_barrier_set()),
    _g1_rem(g1h->g1_rem_set()),
    _hash_seed(17), _queue_num(queue_num),
    _term_attempts(0),
    _surviving_alloc_buffer(g1h->desired_plab_sz(GCAllocForSurvived)),
    _tenured_alloc_buffer(g1h->desired_plab_sz(GCAllocForTenured)),
    _age_table(false), _scanner(g1h, this, rp),
    _strong_roots_time(0), _term_time(0),
    _alloc_buffer_waste(0), _undo_waste(0) {
  // we allocate G1YoungSurvRateNumRegions plus one entries, since
  // we "sacrifice" entry 0 to keep track of surviving bytes for
  // non-young regions (where the age is -1)
  // We also add a few elements at the beginning and at the end in
  // an attempt to eliminate cache contention
  uint real_length = 1 + _g1h->g1_policy()->young_cset_region_length();
  uint array_length = PADDING_ELEM_NUM +
                      real_length +
                      PADDING_ELEM_NUM;
  _surviving_young_words_base = NEW_C_HEAP_ARRAY(size_t, array_length, mtGC);
  if (_surviving_young_words_base == NULL)
    vm_exit_out_of_memory(array_length * sizeof(size_t), OOM_MALLOC_ERROR,
                          "Not enough space for young surv histo.");
  _surviving_young_words = _surviving_young_words_base + PADDING_ELEM_NUM;
  memset(_surviving_young_words, 0, (size_t) real_length * sizeof(size_t));

  _alloc_buffers[GCAllocForSurvived] = &_surviving_alloc_buffer;
  _alloc_buffers[GCAllocForTenured]  = &_tenured_alloc_buffer;

  _start = os::elapsedTime();
}

G1ParScanThreadState::~G1ParScanThreadState() {
  retire_alloc_buffers();
  FREE_C_HEAP_ARRAY(size_t, _surviving_young_words_base, mtGC);
}

void
G1ParScanThreadState::print_termination_stats_hdr(outputStream* const st)
{
  st->print_raw_cr("GC Termination Stats");
  st->print_raw_cr("     elapsed  --strong roots-- -------termination-------"
                   " ------waste (KiB)------");
  st->print_raw_cr("thr     ms        ms      %        ms      %    attempts"
                   "  total   alloc    undo");
  st->print_raw_cr("--- --------- --------- ------ --------- ------ --------"
                   " ------- ------- -------");
}

void
G1ParScanThreadState::print_termination_stats(int i,
                                              outputStream* const st) const
{
  const double elapsed_ms = elapsed_time() * 1000.0;
  const double s_roots_ms = strong_roots_time() * 1000.0;
  const double term_ms    = term_time() * 1000.0;
  st->print_cr("%3d %9.2f %9.2f %6.2f "
               "%9.2f %6.2f " SIZE_FORMAT_W(8) " "
               SIZE_FORMAT_W(7) " " SIZE_FORMAT_W(7) " " SIZE_FORMAT_W(7),
               i, elapsed_ms, s_roots_ms, s_roots_ms * 100 / elapsed_ms,
               term_ms, term_ms * 100 / elapsed_ms, term_attempts(),
               (alloc_buffer_waste() + undo_waste()) * HeapWordSize / K,
               alloc_buffer_waste() * HeapWordSize / K,
               undo_waste() * HeapWordSize / K);
}

#ifdef ASSERT
bool G1ParScanThreadState::verify_ref(narrowOop* ref) const {
  assert(ref != NULL, "invariant");
  assert(UseCompressedOops, "sanity");
  assert(!has_partial_array_mask(ref), err_msg("ref=" PTR_FORMAT, p2i(ref)));
  oop p = oopDesc::load_decode_heap_oop(ref);
  assert(_g1h->is_in_g1_reserved(p),
         err_msg("ref=" PTR_FORMAT " p=" PTR_FORMAT, p2i(ref), p2i(p)));
  return true;
}

bool G1ParScanThreadState::verify_ref(oop* ref) const {
  assert(ref != NULL, "invariant");
  if (has_partial_array_mask(ref)) {
    // Must be in the collection set--it's already been copied.
    oop p = clear_partial_array_mask(ref);
    assert(_g1h->obj_in_cs(p),
           err_msg("ref=" PTR_FORMAT " p=" PTR_FORMAT, p2i(ref), p2i(p)));
  } else {
    oop p = oopDesc::load_decode_heap_oop(ref);
    assert(_g1h->is_in_g1_reserved(p),
           err_msg("ref=" PTR_FORMAT " p=" PTR_FORMAT, p2i(ref), p2i(p)));
  }
  return true;
}

bool G1ParScanThreadState::verify_task(StarTask ref) const {
  if (ref.is_narrow()) {
    return verify_ref((narrowOop*) ref);
  } else {
    return verify_ref((oop*) ref);
  }
}
#endif // ASSERT

void G1ParScanThreadState::trim_queue() {
  assert(_evac_failure_cl != NULL, "not set");

  StarTask ref;
  do {
    // Drain the overflow stack first, so other threads can steal.
    while (_refs->pop_overflow(ref)) {
      dispatch_reference(ref);
    }

    while (_refs->pop_local(ref)) {
      dispatch_reference(ref);
    }
  } while (!_refs->is_empty());
}

oop G1ParScanThreadState::copy_to_survivor_space(oop const old) {
  size_t word_sz = old->size();
  HeapRegion* from_region = _g1h->heap_region_containing_raw(old);
  // +1 to make the -1 indexes valid...
  int       young_index = from_region->young_index_in_cset()+1;
  assert( (from_region->is_young() && young_index >  0) ||
         (!from_region->is_young() && young_index == 0), "invariant" );
  G1CollectorPolicy* g1p = _g1h->g1_policy();
  markOop m = old->mark();
  int age = m->has_displaced_mark_helper() ? m->displaced_mark_helper()->age()
                                           : m->age();
  GCAllocPurpose alloc_purpose = g1p->evacuation_destination(from_region, age,
                                                             word_sz);
  HeapWord* obj_ptr = allocate(alloc_purpose, word_sz);
#ifndef PRODUCT
  // Should this evacuation fail?
  if (_g1h->evacuation_should_fail()) {
    if (obj_ptr != NULL) {
      undo_allocation(alloc_purpose, obj_ptr, word_sz);
      obj_ptr = NULL;
    }
  }
#endif // !PRODUCT

  if (obj_ptr == NULL) {
    // This will either forward-to-self, or detect that someone else has
    // installed a forwarding pointer.
    return _g1h->handle_evacuation_failure_par(this, old);
  }

  oop obj = oop(obj_ptr);

  // We're going to allocate linearly, so might as well prefetch ahead.
  Prefetch::write(obj_ptr, PrefetchCopyIntervalInBytes);

  oop forward_ptr = old->forward_to_atomic(obj);
  if (forward_ptr == NULL) {
    Copy::aligned_disjoint_words((HeapWord*) old, obj_ptr, word_sz);

    // alloc_purpose is just a hint to allocate() above, recheck the type of region
    // we actually allocated from and update alloc_purpose accordingly
    HeapRegion* to_region = _g1h->heap_region_containing_raw(obj_ptr);
    alloc_purpose = to_region->is_young() ? GCAllocForSurvived : GCAllocForTenured;

    if (g1p->track_object_age(alloc_purpose)) {
      // We could simply do obj->incr_age(). However, this causes a
      // performance issue. obj->incr_age() will first check whether
      // the object has a displaced mark by checking its mark word;
      // getting the mark word from the new location of the object
      // stalls. So, given that we already have the mark word and we
      // are about to install it anyway, it's better to increase the
      // age on the mark word, when the object does not have a
      // displaced mark word. We're not expecting many objects to have
      // a displaced marked word, so that case is not optimized
      // further (it could be...) and we simply call obj->incr_age().

      if (m->has_displaced_mark_helper()) {
        // in this case, we have to install the mark word first,
        // otherwise obj looks to be forwarded (the old mark word,
        // which contains the forward pointer, was copied)
        obj->set_mark(m);
        obj->incr_age();
      } else {
        m = m->incr_age();
        obj->set_mark(m);
      }
      age_table()->add(obj, word_sz);
    } else {
      obj->set_mark(m);
    }

    if (G1StringDedup::is_enabled()) {
      G1StringDedup::enqueue_from_evacuation(from_region->is_young(),
                                             to_region->is_young(),
                                             queue_num(),
                                             obj);
    }

    size_t* surv_young_words = surviving_young_words();
    surv_young_words[young_index] += word_sz;

    if (obj->is_objArray() && arrayOop(obj)->length() >= ParGCArrayScanChunk) {
      // We keep track of the next start index in the length field of
      // the to-space object. The actual length can be found in the
      // length field of the from-space object.
      arrayOop(obj)->set_length(0);
      oop* old_p = set_partial_array_mask(old);
      push_on_queue(old_p);
    } else {
      // No point in using the slower heap_region_containing() method,
      // given that we know obj is in the heap.
      _scanner.set_region(_g1h->heap_region_containing_raw(obj));
      obj->oop_iterate_backwards(&_scanner);
    }
  } else {
    undo_allocation(alloc_purpose, obj_ptr, word_sz);
    obj = forward_ptr;
  }
  return obj;
}

HeapWord* G1ParScanThreadState::allocate_slow(GCAllocPurpose purpose, size_t word_sz) {
  HeapWord* obj = NULL;
  size_t gclab_word_size = _g1h->desired_plab_sz(purpose);
  if (word_sz * 100 < gclab_word_size * ParallelGCBufferWastePct) {
    G1ParGCAllocBuffer* alloc_buf = alloc_buffer(purpose);
    add_to_alloc_buffer_waste(alloc_buf->words_remaining());
    alloc_buf->retire(false /* end_of_gc */, false /* retain */);

    HeapWord* buf = _g1h->par_allocate_during_gc(purpose, gclab_word_size);
    if (buf == NULL) {
      return NULL; // Let caller handle allocation failure.
    }
    // Otherwise.
    alloc_buf->set_word_size(gclab_word_size);
    alloc_buf->set_buf(buf);

    obj = alloc_buf->allocate(word_sz);
    assert(obj != NULL, "buffer was definitely big enough...");
  } else {
    obj = _g1h->par_allocate_during_gc(purpose, word_sz);
  }
  return obj;
}

void G1ParScanThreadState::undo_allocation(GCAllocPurpose purpose, HeapWord* obj, size_t word_sz) {
  if (alloc_buffer(purpose)->contains(obj)) {
    assert(alloc_buffer(purpose)->contains(obj + word_sz - 1),
           "should contain whole object");
    alloc_buffer(purpose)->undo_allocation(obj, word_sz);
  } else {
    CollectedHeap::fill_with_object(obj, word_sz);
    add_to_undo_waste(word_sz);
  }
}

HeapWord* G1ParScanThreadState::allocate(GCAllocPurpose purpose, size_t word_sz) {
  HeapWord* obj = alloc_buffer(purpose)->allocate(word_sz);
  if (obj != NULL) {
    return obj;
  }
  return allocate_slow(purpose, word_sz);
}

void G1ParScanThreadState::retire_alloc_buffers() {
  for (int ap = 0; ap < GCAllocPurposeCount; ++ap) {
    size_t waste = _alloc_buffers[ap]->words_remaining();
    add_to_alloc_buffer_waste(waste);
    _alloc_buffers[ap]->flush_stats_and_retire(_g1h->stats_for_purpose((GCAllocPurpose)ap),
                                               true /* end_of_gc */,
                                               false /* retain */);
  }
}
