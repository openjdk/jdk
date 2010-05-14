/*
 * Copyright 2001-2010 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_heapRegion.cpp.incl"

int HeapRegion::LogOfHRGrainBytes = 0;
int HeapRegion::LogOfHRGrainWords = 0;
int HeapRegion::GrainBytes        = 0;
int HeapRegion::GrainWords        = 0;
int HeapRegion::CardsPerRegion    = 0;

HeapRegionDCTOC::HeapRegionDCTOC(G1CollectedHeap* g1,
                                 HeapRegion* hr, OopClosure* cl,
                                 CardTableModRefBS::PrecisionStyle precision,
                                 FilterKind fk) :
  ContiguousSpaceDCTOC(hr, cl, precision, NULL),
  _hr(hr), _fk(fk), _g1(g1)
{}

FilterOutOfRegionClosure::FilterOutOfRegionClosure(HeapRegion* r,
                                                   OopClosure* oc) :
  _r_bottom(r->bottom()), _r_end(r->end()),
  _oc(oc), _out_of_region(0)
{}

class VerifyLiveClosure: public OopClosure {
private:
  G1CollectedHeap* _g1h;
  CardTableModRefBS* _bs;
  oop _containing_obj;
  bool _failures;
  int _n_failures;
  bool _use_prev_marking;
public:
  // use_prev_marking == true  -> use "prev" marking information,
  // use_prev_marking == false -> use "next" marking information
  VerifyLiveClosure(G1CollectedHeap* g1h, bool use_prev_marking) :
    _g1h(g1h), _bs(NULL), _containing_obj(NULL),
    _failures(false), _n_failures(0), _use_prev_marking(use_prev_marking)
  {
    BarrierSet* bs = _g1h->barrier_set();
    if (bs->is_a(BarrierSet::CardTableModRef))
      _bs = (CardTableModRefBS*)bs;
  }

  void set_containing_obj(oop obj) {
    _containing_obj = obj;
  }

  bool failures() { return _failures; }
  int n_failures() { return _n_failures; }

  virtual void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual void do_oop(      oop* p) { do_oop_work(p); }

  void print_object(outputStream* out, oop obj) {
#ifdef PRODUCT
    klassOop k = obj->klass();
    const char* class_name = instanceKlass::cast(k)->external_name();
    out->print_cr("class name %s", class_name);
#else // PRODUCT
    obj->print_on(out);
#endif // PRODUCT
  }

  template <class T> void do_oop_work(T* p) {
    assert(_containing_obj != NULL, "Precondition");
    assert(!_g1h->is_obj_dead_cond(_containing_obj, _use_prev_marking),
           "Precondition");
    T heap_oop = oopDesc::load_heap_oop(p);
    if (!oopDesc::is_null(heap_oop)) {
      oop obj = oopDesc::decode_heap_oop_not_null(heap_oop);
      bool failed = false;
      if (!_g1h->is_in_closed_subset(obj) ||
          _g1h->is_obj_dead_cond(obj, _use_prev_marking)) {
        if (!_failures) {
          gclog_or_tty->print_cr("");
          gclog_or_tty->print_cr("----------");
        }
        if (!_g1h->is_in_closed_subset(obj)) {
          HeapRegion* from = _g1h->heap_region_containing((HeapWord*)p);
          gclog_or_tty->print_cr("Field "PTR_FORMAT
                                 " of live obj "PTR_FORMAT" in region "
                                 "["PTR_FORMAT", "PTR_FORMAT")",
                                 p, (void*) _containing_obj,
                                 from->bottom(), from->end());
          print_object(gclog_or_tty, _containing_obj);
          gclog_or_tty->print_cr("points to obj "PTR_FORMAT" not in the heap",
                                 (void*) obj);
        } else {
          HeapRegion* from = _g1h->heap_region_containing((HeapWord*)p);
          HeapRegion* to   = _g1h->heap_region_containing((HeapWord*)obj);
          gclog_or_tty->print_cr("Field "PTR_FORMAT
                                 " of live obj "PTR_FORMAT" in region "
                                 "["PTR_FORMAT", "PTR_FORMAT")",
                                 p, (void*) _containing_obj,
                                 from->bottom(), from->end());
          print_object(gclog_or_tty, _containing_obj);
          gclog_or_tty->print_cr("points to dead obj "PTR_FORMAT" in region "
                                 "["PTR_FORMAT", "PTR_FORMAT")",
                                 (void*) obj, to->bottom(), to->end());
          print_object(gclog_or_tty, obj);
        }
        gclog_or_tty->print_cr("----------");
        _failures = true;
        failed = true;
        _n_failures++;
      }

      if (!_g1h->full_collection()) {
        HeapRegion* from = _g1h->heap_region_containing((HeapWord*)p);
        HeapRegion* to   = _g1h->heap_region_containing(obj);
        if (from != NULL && to != NULL &&
            from != to &&
            !to->isHumongous()) {
          jbyte cv_obj = *_bs->byte_for_const(_containing_obj);
          jbyte cv_field = *_bs->byte_for_const(p);
          const jbyte dirty = CardTableModRefBS::dirty_card_val();

          bool is_bad = !(from->is_young()
                          || to->rem_set()->contains_reference(p)
                          || !G1HRRSFlushLogBuffersOnVerify && // buffers were not flushed
                              (_containing_obj->is_objArray() ?
                                  cv_field == dirty
                               : cv_obj == dirty || cv_field == dirty));
          if (is_bad) {
            if (!_failures) {
              gclog_or_tty->print_cr("");
              gclog_or_tty->print_cr("----------");
            }
            gclog_or_tty->print_cr("Missing rem set entry:");
            gclog_or_tty->print_cr("Field "PTR_FORMAT
                          " of obj "PTR_FORMAT
                          ", in region %d ["PTR_FORMAT
                          ", "PTR_FORMAT"),",
                          p, (void*) _containing_obj,
                          from->hrs_index(),
                          from->bottom(),
                          from->end());
            _containing_obj->print_on(gclog_or_tty);
            gclog_or_tty->print_cr("points to obj "PTR_FORMAT
                          " in region %d ["PTR_FORMAT
                          ", "PTR_FORMAT").",
                          (void*) obj, to->hrs_index(),
                          to->bottom(), to->end());
            obj->print_on(gclog_or_tty);
            gclog_or_tty->print_cr("Obj head CTE = %d, field CTE = %d.",
                          cv_obj, cv_field);
            gclog_or_tty->print_cr("----------");
            _failures = true;
            if (!failed) _n_failures++;
          }
        }
      }
    }
  }
};

template<class ClosureType>
HeapWord* walk_mem_region_loop(ClosureType* cl, G1CollectedHeap* g1h,
                               HeapRegion* hr,
                               HeapWord* cur, HeapWord* top) {
  oop cur_oop = oop(cur);
  int oop_size = cur_oop->size();
  HeapWord* next_obj = cur + oop_size;
  while (next_obj < top) {
    // Keep filtering the remembered set.
    if (!g1h->is_obj_dead(cur_oop, hr)) {
      // Bottom lies entirely below top, so we can call the
      // non-memRegion version of oop_iterate below.
      cur_oop->oop_iterate(cl);
    }
    cur = next_obj;
    cur_oop = oop(cur);
    oop_size = cur_oop->size();
    next_obj = cur + oop_size;
  }
  return cur;
}

void HeapRegionDCTOC::walk_mem_region_with_cl(MemRegion mr,
                                              HeapWord* bottom,
                                              HeapWord* top,
                                              OopClosure* cl) {
  G1CollectedHeap* g1h = _g1;

  int oop_size;

  OopClosure* cl2 = cl;
  FilterIntoCSClosure intoCSFilt(this, g1h, cl);
  FilterOutOfRegionClosure outOfRegionFilt(_hr, cl);
  switch (_fk) {
  case IntoCSFilterKind:      cl2 = &intoCSFilt; break;
  case OutOfRegionFilterKind: cl2 = &outOfRegionFilt; break;
  }

  // Start filtering what we add to the remembered set. If the object is
  // not considered dead, either because it is marked (in the mark bitmap)
  // or it was allocated after marking finished, then we add it. Otherwise
  // we can safely ignore the object.
  if (!g1h->is_obj_dead(oop(bottom), _hr)) {
    oop_size = oop(bottom)->oop_iterate(cl2, mr);
  } else {
    oop_size = oop(bottom)->size();
  }

  bottom += oop_size;

  if (bottom < top) {
    // We replicate the loop below for several kinds of possible filters.
    switch (_fk) {
    case NoFilterKind:
      bottom = walk_mem_region_loop(cl, g1h, _hr, bottom, top);
      break;
    case IntoCSFilterKind: {
      FilterIntoCSClosure filt(this, g1h, cl);
      bottom = walk_mem_region_loop(&filt, g1h, _hr, bottom, top);
      break;
    }
    case OutOfRegionFilterKind: {
      FilterOutOfRegionClosure filt(_hr, cl);
      bottom = walk_mem_region_loop(&filt, g1h, _hr, bottom, top);
      break;
    }
    default:
      ShouldNotReachHere();
    }

    // Last object. Need to do dead-obj filtering here too.
    if (!g1h->is_obj_dead(oop(bottom), _hr)) {
      oop(bottom)->oop_iterate(cl2, mr);
    }
  }
}

// Minimum region size; we won't go lower than that.
// We might want to decrease this in the future, to deal with small
// heaps a bit more efficiently.
#define MIN_REGION_SIZE  (      1024 * 1024 )

// Maximum region size; we don't go higher than that. There's a good
// reason for having an upper bound. We don't want regions to get too
// large, otherwise cleanup's effectiveness would decrease as there
// will be fewer opportunities to find totally empty regions after
// marking.
#define MAX_REGION_SIZE  ( 32 * 1024 * 1024 )

// The automatic region size calculation will try to have around this
// many regions in the heap (based on the min heap size).
#define TARGET_REGION_NUMBER          2048

void HeapRegion::setup_heap_region_size(uintx min_heap_size) {
  // region_size in bytes
  uintx region_size = G1HeapRegionSize;
  if (FLAG_IS_DEFAULT(G1HeapRegionSize)) {
    // We base the automatic calculation on the min heap size. This
    // can be problematic if the spread between min and max is quite
    // wide, imagine -Xms128m -Xmx32g. But, if we decided it based on
    // the max size, the region size might be way too large for the
    // min size. Either way, some users might have to set the region
    // size manually for some -Xms / -Xmx combos.

    region_size = MAX2(min_heap_size / TARGET_REGION_NUMBER,
                       (uintx) MIN_REGION_SIZE);
  }

  int region_size_log = log2_long((jlong) region_size);
  // Recalculate the region size to make sure it's a power of
  // 2. This means that region_size is the largest power of 2 that's
  // <= what we've calculated so far.
  region_size = 1 << region_size_log;

  // Now make sure that we don't go over or under our limits.
  if (region_size < MIN_REGION_SIZE) {
    region_size = MIN_REGION_SIZE;
  } else if (region_size > MAX_REGION_SIZE) {
    region_size = MAX_REGION_SIZE;
  }

  // And recalculate the log.
  region_size_log = log2_long((jlong) region_size);

  // Now, set up the globals.
  guarantee(LogOfHRGrainBytes == 0, "we should only set it once");
  LogOfHRGrainBytes = region_size_log;

  guarantee(LogOfHRGrainWords == 0, "we should only set it once");
  LogOfHRGrainWords = LogOfHRGrainBytes - LogHeapWordSize;

  guarantee(GrainBytes == 0, "we should only set it once");
  // The cast to int is safe, given that we've bounded region_size by
  // MIN_REGION_SIZE and MAX_REGION_SIZE.
  GrainBytes = (int) region_size;

  guarantee(GrainWords == 0, "we should only set it once");
  GrainWords = GrainBytes >> LogHeapWordSize;
  guarantee(1 << LogOfHRGrainWords == GrainWords, "sanity");

  guarantee(CardsPerRegion == 0, "we should only set it once");
  CardsPerRegion = GrainBytes >> CardTableModRefBS::card_shift;
}

void HeapRegion::reset_after_compaction() {
  G1OffsetTableContigSpace::reset_after_compaction();
  // After a compaction the mark bitmap is invalid, so we must
  // treat all objects as being inside the unmarked area.
  zero_marked_bytes();
  init_top_at_mark_start();
}

DirtyCardToOopClosure*
HeapRegion::new_dcto_closure(OopClosure* cl,
                             CardTableModRefBS::PrecisionStyle precision,
                             HeapRegionDCTOC::FilterKind fk) {
  return new HeapRegionDCTOC(G1CollectedHeap::heap(),
                             this, cl, precision, fk);
}

void HeapRegion::hr_clear(bool par, bool clear_space) {
  _humongous_type = NotHumongous;
  _humongous_start_region = NULL;
  _in_collection_set = false;
  _is_gc_alloc_region = false;

  // Age stuff (if parallel, this will be done separately, since it needs
  // to be sequential).
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  set_young_index_in_cset(-1);
  uninstall_surv_rate_group();
  set_young_type(NotYoung);

  // In case it had been the start of a humongous sequence, reset its end.
  set_end(_orig_end);

  if (!par) {
    // If this is parallel, this will be done later.
    HeapRegionRemSet* hrrs = rem_set();
    if (hrrs != NULL) hrrs->clear();
    _claimed = InitialClaimValue;
  }
  zero_marked_bytes();
  set_sort_index(-1);

  _offsets.resize(HeapRegion::GrainWords);
  init_top_at_mark_start();
  if (clear_space) clear(SpaceDecorator::Mangle);
}

// <PREDICTION>
void HeapRegion::calc_gc_efficiency() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  _gc_efficiency = (double) garbage_bytes() /
                            g1h->predict_region_elapsed_time_ms(this, false);
}
// </PREDICTION>

void HeapRegion::set_startsHumongous() {
  _humongous_type = StartsHumongous;
  _humongous_start_region = this;
  assert(end() == _orig_end, "Should be normal before alloc.");
}

bool HeapRegion::claimHeapRegion(jint claimValue) {
  jint current = _claimed;
  if (current != claimValue) {
    jint res = Atomic::cmpxchg(claimValue, &_claimed, current);
    if (res == current) {
      return true;
    }
  }
  return false;
}

HeapWord* HeapRegion::next_block_start_careful(HeapWord* addr) {
  HeapWord* low = addr;
  HeapWord* high = end();
  while (low < high) {
    size_t diff = pointer_delta(high, low);
    // Must add one below to bias toward the high amount.  Otherwise, if
  // "high" were at the desired value, and "low" were one less, we
    // would not converge on "high".  This is not symmetric, because
    // we set "high" to a block start, which might be the right one,
    // which we don't do for "low".
    HeapWord* middle = low + (diff+1)/2;
    if (middle == high) return high;
    HeapWord* mid_bs = block_start_careful(middle);
    if (mid_bs < addr) {
      low = middle;
    } else {
      high = mid_bs;
    }
  }
  assert(low == high && low >= addr, "Didn't work.");
  return low;
}

void HeapRegion::set_next_on_unclean_list(HeapRegion* r) {
  assert(r == NULL || r->is_on_unclean_list(), "Malformed unclean list.");
  _next_in_special_set = r;
}

void HeapRegion::set_on_unclean_list(bool b) {
  _is_on_unclean_list = b;
}

void HeapRegion::initialize(MemRegion mr, bool clear_space, bool mangle_space) {
  G1OffsetTableContigSpace::initialize(mr, false, mangle_space);
  hr_clear(false/*par*/, clear_space);
}
#ifdef _MSC_VER // the use of 'this' below gets a warning, make it go away
#pragma warning( disable:4355 ) // 'this' : used in base member initializer list
#endif // _MSC_VER


HeapRegion::
HeapRegion(G1BlockOffsetSharedArray* sharedOffsetArray,
                     MemRegion mr, bool is_zeroed)
  : G1OffsetTableContigSpace(sharedOffsetArray, mr, is_zeroed),
    _next_fk(HeapRegionDCTOC::NoFilterKind),
    _hrs_index(-1),
    _humongous_type(NotHumongous), _humongous_start_region(NULL),
    _in_collection_set(false), _is_gc_alloc_region(false),
    _is_on_free_list(false), _is_on_unclean_list(false),
    _next_in_special_set(NULL), _orig_end(NULL),
    _claimed(InitialClaimValue), _evacuation_failed(false),
    _prev_marked_bytes(0), _next_marked_bytes(0), _sort_index(-1),
    _young_type(NotYoung), _next_young_region(NULL),
    _next_dirty_cards_region(NULL),
    _young_index_in_cset(-1), _surv_rate_group(NULL), _age_index(-1),
    _rem_set(NULL), _zfs(NotZeroFilled),
    _recorded_rs_length(0), _predicted_elapsed_time_ms(0),
    _predicted_bytes_to_copy(0)
{
  _orig_end = mr.end();
  // Note that initialize() will set the start of the unmarked area of the
  // region.
  this->initialize(mr, !is_zeroed, SpaceDecorator::Mangle);
  set_top(bottom());
  set_saved_mark();

  _rem_set =  new HeapRegionRemSet(sharedOffsetArray, this);

  assert(HeapRegionRemSet::num_par_rem_sets() > 0, "Invariant.");
  // In case the region is allocated during a pause, note the top.
  // We haven't done any counting on a brand new region.
  _top_at_conc_mark_count = bottom();
}

class NextCompactionHeapRegionClosure: public HeapRegionClosure {
  const HeapRegion* _target;
  bool _target_seen;
  HeapRegion* _last;
  CompactibleSpace* _res;
public:
  NextCompactionHeapRegionClosure(const HeapRegion* target) :
    _target(target), _target_seen(false), _res(NULL) {}
  bool doHeapRegion(HeapRegion* cur) {
    if (_target_seen) {
      if (!cur->isHumongous()) {
        _res = cur;
        return true;
      }
    } else if (cur == _target) {
      _target_seen = true;
    }
    return false;
  }
  CompactibleSpace* result() { return _res; }
};

CompactibleSpace* HeapRegion::next_compaction_space() const {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  // cast away const-ness
  HeapRegion* r = (HeapRegion*) this;
  NextCompactionHeapRegionClosure blk(r);
  g1h->heap_region_iterate_from(r, &blk);
  return blk.result();
}

void HeapRegion::set_continuesHumongous(HeapRegion* start) {
  // The order is important here.
  start->add_continuingHumongousRegion(this);
  _humongous_type = ContinuesHumongous;
  _humongous_start_region = start;
}

void HeapRegion::add_continuingHumongousRegion(HeapRegion* cont) {
  // Must join the blocks of the current H region seq with the block of the
  // added region.
  offsets()->join_blocks(bottom(), cont->bottom());
  arrayOop obj = (arrayOop)(bottom());
  obj->set_length((int) (obj->length() + cont->capacity()/jintSize));
  set_end(cont->end());
  set_top(cont->end());
}

void HeapRegion::save_marks() {
  set_saved_mark();
}

void HeapRegion::oops_in_mr_iterate(MemRegion mr, OopClosure* cl) {
  HeapWord* p = mr.start();
  HeapWord* e = mr.end();
  oop obj;
  while (p < e) {
    obj = oop(p);
    p += obj->oop_iterate(cl);
  }
  assert(p == e, "bad memregion: doesn't end on obj boundary");
}

#define HeapRegion_OOP_SINCE_SAVE_MARKS_DEFN(OopClosureType, nv_suffix) \
void HeapRegion::oop_since_save_marks_iterate##nv_suffix(OopClosureType* cl) { \
  ContiguousSpace::oop_since_save_marks_iterate##nv_suffix(cl);              \
}
SPECIALIZED_SINCE_SAVE_MARKS_CLOSURES(HeapRegion_OOP_SINCE_SAVE_MARKS_DEFN)


void HeapRegion::oop_before_save_marks_iterate(OopClosure* cl) {
  oops_in_mr_iterate(MemRegion(bottom(), saved_mark_word()), cl);
}

#ifdef DEBUG
HeapWord* HeapRegion::allocate(size_t size) {
  jint state = zero_fill_state();
  assert(!G1CollectedHeap::heap()->allocs_are_zero_filled() ||
         zero_fill_is_allocated(),
         "When ZF is on, only alloc in ZF'd regions");
  return G1OffsetTableContigSpace::allocate(size);
}
#endif

void HeapRegion::set_zero_fill_state_work(ZeroFillState zfs) {
  assert(ZF_mon->owned_by_self() ||
         Universe::heap()->is_gc_active(),
         "Must hold the lock or be a full GC to modify.");
#ifdef ASSERT
  if (top() != bottom() && zfs != Allocated) {
    ResourceMark rm;
    stringStream region_str;
    print_on(&region_str);
    assert(top() == bottom() || zfs == Allocated,
           err_msg("Region must be empty, or we must be setting it to allocated. "
                   "_zfs=%d, zfs=%d, region: %s", _zfs, zfs, region_str.as_string()));
  }
#endif
  _zfs = zfs;
}

void HeapRegion::set_zero_fill_complete() {
  set_zero_fill_state_work(ZeroFilled);
  if (ZF_mon->owned_by_self()) {
    ZF_mon->notify_all();
  }
}


void HeapRegion::ensure_zero_filled() {
  MutexLockerEx x(ZF_mon, Mutex::_no_safepoint_check_flag);
  ensure_zero_filled_locked();
}

void HeapRegion::ensure_zero_filled_locked() {
  assert(ZF_mon->owned_by_self(), "Precondition");
  bool should_ignore_zf = SafepointSynchronize::is_at_safepoint();
  assert(should_ignore_zf || Heap_lock->is_locked(),
         "Either we're in a GC or we're allocating a region.");
  switch (zero_fill_state()) {
  case HeapRegion::NotZeroFilled:
    set_zero_fill_in_progress(Thread::current());
    {
      ZF_mon->unlock();
      Copy::fill_to_words(bottom(), capacity()/HeapWordSize);
      ZF_mon->lock_without_safepoint_check();
    }
    // A trap.
    guarantee(zero_fill_state() == HeapRegion::ZeroFilling
              && zero_filler() == Thread::current(),
              "AHA!  Tell Dave D if you see this...");
    set_zero_fill_complete();
    // gclog_or_tty->print_cr("Did sync ZF.");
    ConcurrentZFThread::note_sync_zfs();
    break;
  case HeapRegion::ZeroFilling:
    if (should_ignore_zf) {
      // We can "break" the lock and take over the work.
      Copy::fill_to_words(bottom(), capacity()/HeapWordSize);
      set_zero_fill_complete();
      ConcurrentZFThread::note_sync_zfs();
      break;
    } else {
      ConcurrentZFThread::wait_for_ZF_completed(this);
    }
  case HeapRegion::ZeroFilled:
    // Nothing to do.
    break;
  case HeapRegion::Allocated:
    guarantee(false, "Should not call on allocated regions.");
  }
  assert(zero_fill_state() == HeapRegion::ZeroFilled, "Post");
}

HeapWord*
HeapRegion::object_iterate_mem_careful(MemRegion mr,
                                                 ObjectClosure* cl) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  // We used to use "block_start_careful" here.  But we're actually happy
  // to update the BOT while we do this...
  HeapWord* cur = block_start(mr.start());
  mr = mr.intersection(used_region());
  if (mr.is_empty()) return NULL;
  // Otherwise, find the obj that extends onto mr.start().

  assert(cur <= mr.start()
         && (oop(cur)->klass_or_null() == NULL ||
             cur + oop(cur)->size() > mr.start()),
         "postcondition of block_start");
  oop obj;
  while (cur < mr.end()) {
    obj = oop(cur);
    if (obj->klass_or_null() == NULL) {
      // Ran into an unparseable point.
      return cur;
    } else if (!g1h->is_obj_dead(obj)) {
      cl->do_object(obj);
    }
    if (cl->abort()) return cur;
    // The check above must occur before the operation below, since an
    // abort might invalidate the "size" operation.
    cur += obj->size();
  }
  return NULL;
}

HeapWord*
HeapRegion::
oops_on_card_seq_iterate_careful(MemRegion mr,
                                     FilterOutOfRegionClosure* cl) {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  // If we're within a stop-world GC, then we might look at a card in a
  // GC alloc region that extends onto a GC LAB, which may not be
  // parseable.  Stop such at the "saved_mark" of the region.
  if (G1CollectedHeap::heap()->is_gc_active()) {
    mr = mr.intersection(used_region_at_save_marks());
  } else {
    mr = mr.intersection(used_region());
  }
  if (mr.is_empty()) return NULL;
  // Otherwise, find the obj that extends onto mr.start().

  // We used to use "block_start_careful" here.  But we're actually happy
  // to update the BOT while we do this...
  HeapWord* cur = block_start(mr.start());
  assert(cur <= mr.start(), "Postcondition");

  while (cur <= mr.start()) {
    if (oop(cur)->klass_or_null() == NULL) {
      // Ran into an unparseable point.
      return cur;
    }
    // Otherwise...
    int sz = oop(cur)->size();
    if (cur + sz > mr.start()) break;
    // Otherwise, go on.
    cur = cur + sz;
  }
  oop obj;
  obj = oop(cur);
  // If we finish this loop...
  assert(cur <= mr.start()
         && obj->klass_or_null() != NULL
         && cur + obj->size() > mr.start(),
         "Loop postcondition");
  if (!g1h->is_obj_dead(obj)) {
    obj->oop_iterate(cl, mr);
  }

  HeapWord* next;
  while (cur < mr.end()) {
    obj = oop(cur);
    if (obj->klass_or_null() == NULL) {
      // Ran into an unparseable point.
      return cur;
    };
    // Otherwise:
    next = (cur + obj->size());
    if (!g1h->is_obj_dead(obj)) {
      if (next < mr.end()) {
        obj->oop_iterate(cl);
      } else {
        // this obj spans the boundary.  If it's an array, stop at the
        // boundary.
        if (obj->is_objArray()) {
          obj->oop_iterate(cl, mr);
        } else {
          obj->oop_iterate(cl);
        }
      }
    }
    cur = next;
  }
  return NULL;
}

void HeapRegion::print() const { print_on(gclog_or_tty); }
void HeapRegion::print_on(outputStream* st) const {
  if (isHumongous()) {
    if (startsHumongous())
      st->print(" HS");
    else
      st->print(" HC");
  } else {
    st->print("   ");
  }
  if (in_collection_set())
    st->print(" CS");
  else if (is_gc_alloc_region())
    st->print(" A ");
  else
    st->print("   ");
  if (is_young())
    st->print(is_survivor() ? " SU" : " Y ");
  else
    st->print("   ");
  if (is_empty())
    st->print(" F");
  else
    st->print("  ");
  st->print(" %5d", _gc_time_stamp);
  st->print(" PTAMS "PTR_FORMAT" NTAMS "PTR_FORMAT,
            prev_top_at_mark_start(), next_top_at_mark_start());
  G1OffsetTableContigSpace::print_on(st);
}

void HeapRegion::verify(bool allow_dirty) const {
  bool dummy = false;
  verify(allow_dirty, /* use_prev_marking */ true, /* failures */ &dummy);
}

#define OBJ_SAMPLE_INTERVAL 0
#define BLOCK_SAMPLE_INTERVAL 100

// This really ought to be commoned up into OffsetTableContigSpace somehow.
// We would need a mechanism to make that code skip dead objects.

void HeapRegion::verify(bool allow_dirty,
                        bool use_prev_marking,
                        bool* failures) const {
  G1CollectedHeap* g1 = G1CollectedHeap::heap();
  *failures = false;
  HeapWord* p = bottom();
  HeapWord* prev_p = NULL;
  int objs = 0;
  int blocks = 0;
  VerifyLiveClosure vl_cl(g1, use_prev_marking);
  while (p < top()) {
    size_t size = oop(p)->size();
    if (blocks == BLOCK_SAMPLE_INTERVAL) {
      HeapWord* res = block_start_const(p + (size/2));
      if (p != res) {
        gclog_or_tty->print_cr("offset computation 1 for "PTR_FORMAT" and "
                               SIZE_FORMAT" returned "PTR_FORMAT,
                               p, size, res);
        *failures = true;
        return;
      }
      blocks = 0;
    } else {
      blocks++;
    }
    if (objs == OBJ_SAMPLE_INTERVAL) {
      oop obj = oop(p);
      if (!g1->is_obj_dead_cond(obj, this, use_prev_marking)) {
        if (obj->is_oop()) {
          klassOop klass = obj->klass();
          if (!klass->is_perm()) {
            gclog_or_tty->print_cr("klass "PTR_FORMAT" of object "PTR_FORMAT" "
                                   "not in perm", klass, obj);
            *failures = true;
            return;
          } else if (!klass->is_klass()) {
            gclog_or_tty->print_cr("klass "PTR_FORMAT" of object "PTR_FORMAT" "
                                   "not a klass", klass, obj);
            *failures = true;
            return;
          } else {
            vl_cl.set_containing_obj(obj);
            obj->oop_iterate(&vl_cl);
            if (vl_cl.failures()) {
              *failures = true;
            }
            if (G1MaxVerifyFailures >= 0 &&
                vl_cl.n_failures() >= G1MaxVerifyFailures) {
              return;
            }
          }
        } else {
          gclog_or_tty->print_cr(PTR_FORMAT" no an oop", obj);
          *failures = true;
          return;
        }
      }
      objs = 0;
    } else {
      objs++;
    }
    prev_p = p;
    p += size;
  }
  HeapWord* rend = end();
  HeapWord* rtop = top();
  if (rtop < rend) {
    HeapWord* res = block_start_const(rtop + (rend - rtop) / 2);
    if (res != rtop) {
        gclog_or_tty->print_cr("offset computation 2 for "PTR_FORMAT" and "
                               PTR_FORMAT" returned "PTR_FORMAT,
                               rtop, rend, res);
        *failures = true;
        return;
    }
  }

  if (p != top()) {
    gclog_or_tty->print_cr("end of last object "PTR_FORMAT" "
                           "does not match top "PTR_FORMAT, p, top());
    *failures = true;
    return;
  }
}

// G1OffsetTableContigSpace code; copied from space.cpp.  Hope this can go
// away eventually.

void G1OffsetTableContigSpace::initialize(MemRegion mr, bool clear_space, bool mangle_space) {
  // false ==> we'll do the clearing if there's clearing to be done.
  ContiguousSpace::initialize(mr, false, mangle_space);
  _offsets.zero_bottom_entry();
  _offsets.initialize_threshold();
  if (clear_space) clear(mangle_space);
}

void G1OffsetTableContigSpace::clear(bool mangle_space) {
  ContiguousSpace::clear(mangle_space);
  _offsets.zero_bottom_entry();
  _offsets.initialize_threshold();
}

void G1OffsetTableContigSpace::set_bottom(HeapWord* new_bottom) {
  Space::set_bottom(new_bottom);
  _offsets.set_bottom(new_bottom);
}

void G1OffsetTableContigSpace::set_end(HeapWord* new_end) {
  Space::set_end(new_end);
  _offsets.resize(new_end - bottom());
}

void G1OffsetTableContigSpace::print() const {
  print_short();
  gclog_or_tty->print_cr(" [" INTPTR_FORMAT ", " INTPTR_FORMAT ", "
                INTPTR_FORMAT ", " INTPTR_FORMAT ")",
                bottom(), top(), _offsets.threshold(), end());
}

HeapWord* G1OffsetTableContigSpace::initialize_threshold() {
  return _offsets.initialize_threshold();
}

HeapWord* G1OffsetTableContigSpace::cross_threshold(HeapWord* start,
                                                    HeapWord* end) {
  _offsets.alloc_block(start, end);
  return _offsets.threshold();
}

HeapWord* G1OffsetTableContigSpace::saved_mark_word() const {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  assert( _gc_time_stamp <= g1h->get_gc_time_stamp(), "invariant" );
  if (_gc_time_stamp < g1h->get_gc_time_stamp())
    return top();
  else
    return ContiguousSpace::saved_mark_word();
}

void G1OffsetTableContigSpace::set_saved_mark() {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  unsigned curr_gc_time_stamp = g1h->get_gc_time_stamp();

  if (_gc_time_stamp < curr_gc_time_stamp) {
    // The order of these is important, as another thread might be
    // about to start scanning this region. If it does so after
    // set_saved_mark and before _gc_time_stamp = ..., then the latter
    // will be false, and it will pick up top() as the high water mark
    // of region. If it does so after _gc_time_stamp = ..., then it
    // will pick up the right saved_mark_word() as the high water mark
    // of the region. Either way, the behaviour will be correct.
    ContiguousSpace::set_saved_mark();
    OrderAccess::storestore();
    _gc_time_stamp = curr_gc_time_stamp;
    // The following fence is to force a flush of the writes above, but
    // is strictly not needed because when an allocating worker thread
    // calls set_saved_mark() it does so under the ParGCRareEvent_lock;
    // when the lock is released, the write will be flushed.
    // OrderAccess::fence();
  }
}

G1OffsetTableContigSpace::
G1OffsetTableContigSpace(G1BlockOffsetSharedArray* sharedOffsetArray,
                         MemRegion mr, bool is_zeroed) :
  _offsets(sharedOffsetArray, mr),
  _par_alloc_lock(Mutex::leaf, "OffsetTableContigSpace par alloc lock", true),
  _gc_time_stamp(0)
{
  _offsets.set_space(this);
  initialize(mr, !is_zeroed, SpaceDecorator::Mangle);
}

size_t RegionList::length() {
  size_t len = 0;
  HeapRegion* cur = hd();
  DEBUG_ONLY(HeapRegion* last = NULL);
  while (cur != NULL) {
    len++;
    DEBUG_ONLY(last = cur);
    cur = get_next(cur);
  }
  assert(last == tl(), "Invariant");
  return len;
}

void RegionList::insert_before_head(HeapRegion* r) {
  assert(well_formed(), "Inv");
  set_next(r, hd());
  _hd = r;
  _sz++;
  if (tl() == NULL) _tl = r;
  assert(well_formed(), "Inv");
}

void RegionList::prepend_list(RegionList* new_list) {
  assert(well_formed(), "Precondition");
  assert(new_list->well_formed(), "Precondition");
  HeapRegion* new_tl = new_list->tl();
  if (new_tl != NULL) {
    set_next(new_tl, hd());
    _hd = new_list->hd();
    _sz += new_list->sz();
    if (tl() == NULL) _tl = new_list->tl();
  } else {
    assert(new_list->hd() == NULL && new_list->sz() == 0, "Inv");
  }
  assert(well_formed(), "Inv");
}

void RegionList::delete_after(HeapRegion* r) {
  assert(well_formed(), "Precondition");
  HeapRegion* next = get_next(r);
  assert(r != NULL, "Precondition");
  HeapRegion* next_tl = get_next(next);
  set_next(r, next_tl);
  dec_sz();
  if (next == tl()) {
    assert(next_tl == NULL, "Inv");
    _tl = r;
  }
  assert(well_formed(), "Inv");
}

HeapRegion* RegionList::pop() {
  assert(well_formed(), "Inv");
  HeapRegion* res = hd();
  if (res != NULL) {
    _hd = get_next(res);
    _sz--;
    set_next(res, NULL);
    if (sz() == 0) _tl = NULL;
  }
  assert(well_formed(), "Inv");
  return res;
}
