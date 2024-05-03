/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "code/nmethod.hpp"
#include "gc/g1/g1Allocator.inline.hpp"
#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1CollectionSetCandidates.inline.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionBounds.inline.hpp"
#include "gc/g1/g1HeapRegionManager.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1HeapRegionTracer.hpp"
#include "gc/g1/g1HeapRegionTraceType.hpp"
#include "gc/g1/g1NUMA.hpp"
#include "gc/g1/g1OopClosures.inline.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/access.inline.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/atomic.hpp"
#include "runtime/globals_extension.hpp"
#include "utilities/powerOfTwo.hpp"

uint   HeapRegion::LogOfHRGrainBytes = 0;
uint   HeapRegion::LogCardsPerRegion = 0;
size_t HeapRegion::GrainBytes        = 0;
size_t HeapRegion::GrainWords        = 0;
size_t HeapRegion::CardsPerRegion    = 0;

size_t HeapRegion::max_region_size() {
  return HeapRegionBounds::max_size();
}

size_t HeapRegion::min_region_size_in_words() {
  return HeapRegionBounds::min_size() >> LogHeapWordSize;
}

void HeapRegion::setup_heap_region_size(size_t max_heap_size) {
  size_t region_size = G1HeapRegionSize;
  // G1HeapRegionSize = 0 means decide ergonomically.
  if (region_size == 0) {
    region_size = clamp(max_heap_size / HeapRegionBounds::target_number(),
                        HeapRegionBounds::min_size(),
                        HeapRegionBounds::max_ergonomics_size());
  }

  // Make sure region size is a power of 2. Rounding up since this
  // is beneficial in most cases.
  region_size = round_up_power_of_2(region_size);

  // Now make sure that we don't go over or under our limits.
  region_size = clamp(region_size, HeapRegionBounds::min_size(), HeapRegionBounds::max_size());

  // Now, set up the globals.
  guarantee(LogOfHRGrainBytes == 0, "we should only set it once");
  LogOfHRGrainBytes = log2i_exact(region_size);

  guarantee(GrainBytes == 0, "we should only set it once");
  GrainBytes = region_size;

  guarantee(GrainWords == 0, "we should only set it once");
  GrainWords = GrainBytes >> LogHeapWordSize;

  guarantee(CardsPerRegion == 0, "we should only set it once");
  CardsPerRegion = GrainBytes >> G1CardTable::card_shift();

  LogCardsPerRegion = log2i_exact(CardsPerRegion);

  if (G1HeapRegionSize != GrainBytes) {
    FLAG_SET_ERGO(G1HeapRegionSize, GrainBytes);
  }
}

void HeapRegion::handle_evacuation_failure(bool retain) {
  uninstall_surv_rate_group();
  clear_young_index_in_cset();
  clear_index_in_opt_cset();
  move_to_old();

  _rem_set->clean_code_roots(this);
  _rem_set->clear(true /* only_cardset */, retain /* keep_tracked */);
}

void HeapRegion::unlink_from_list() {
  set_next(nullptr);
  set_prev(nullptr);
  set_containing_set(nullptr);
}

void HeapRegion::hr_clear(bool clear_space) {
  set_top(bottom());
  clear_young_index_in_cset();
  clear_index_in_opt_cset();
  uninstall_surv_rate_group();
  set_free();
  reset_pre_dummy_top();

  rem_set()->clear();

  G1CollectedHeap::heap()->concurrent_mark()->reset_top_at_mark_start(this);

  _parsable_bottom = bottom();
  _garbage_bytes = 0;

  if (clear_space) clear(SpaceDecorator::Mangle);
}

void HeapRegion::clear_cardtable() {
  G1CardTable* ct = G1CollectedHeap::heap()->card_table();
  ct->clear_MemRegion(MemRegion(bottom(), end()));
}

double HeapRegion::calc_gc_efficiency() {
  // GC efficiency is the ratio of how much space would be
  // reclaimed over how long we predict it would take to reclaim it.
  G1Policy* policy = G1CollectedHeap::heap()->policy();

  // Retrieve a prediction of the elapsed time for this region for
  // a mixed gc because the region will only be evacuated during a
  // mixed gc.
  double region_elapsed_time_ms = policy->predict_region_total_time_ms(this, false /* for_young_only_phase */);
  return (double)reclaimable_bytes() / region_elapsed_time_ms;
}

void HeapRegion::set_free() {
  report_region_type_change(G1HeapRegionTraceType::Free);
  _type.set_free();
}

void HeapRegion::set_eden() {
  report_region_type_change(G1HeapRegionTraceType::Eden);
  _type.set_eden();
}

void HeapRegion::set_eden_pre_gc() {
  report_region_type_change(G1HeapRegionTraceType::Eden);
  _type.set_eden_pre_gc();
}

void HeapRegion::set_survivor() {
  report_region_type_change(G1HeapRegionTraceType::Survivor);
  _type.set_survivor();
}

void HeapRegion::move_to_old() {
  if (_type.relabel_as_old()) {
    report_region_type_change(G1HeapRegionTraceType::Old);
  }
}

void HeapRegion::set_old() {
  report_region_type_change(G1HeapRegionTraceType::Old);
  _type.set_old();
}

void HeapRegion::set_starts_humongous(HeapWord* obj_top, size_t fill_size) {
  assert(!is_humongous(), "sanity / pre-condition");
  assert(top() == bottom(), "should be empty");

  report_region_type_change(G1HeapRegionTraceType::StartsHumongous);
  _type.set_starts_humongous();
  _humongous_start_region = this;

  _bot->update_for_block(bottom(), obj_top);
  if (fill_size > 0) {
    _bot->update_for_block(obj_top, obj_top + fill_size);
  }
}

void HeapRegion::set_continues_humongous(HeapRegion* first_hr) {
  assert(!is_humongous(), "sanity / pre-condition");
  assert(top() == bottom(), "should be empty");
  assert(first_hr->is_starts_humongous(), "pre-condition");

  report_region_type_change(G1HeapRegionTraceType::ContinuesHumongous);
  _type.set_continues_humongous();
  _humongous_start_region = first_hr;
}

void HeapRegion::clear_humongous() {
  assert(is_humongous(), "pre-condition");

  assert(capacity() == HeapRegion::GrainBytes, "pre-condition");
  _humongous_start_region = nullptr;
}

void HeapRegion::prepare_remset_for_scan() {
  _rem_set->reset_table_scanner();
}

HeapRegion::HeapRegion(uint hrm_index,
                       G1BlockOffsetTable* bot,
                       MemRegion mr,
                       G1CardSetConfiguration* config) :
  _bottom(mr.start()),
  _end(mr.end()),
  _top(nullptr),
  _bot(bot),
  _pre_dummy_top(nullptr),
  _rem_set(nullptr),
  _hrm_index(hrm_index),
  _type(),
  _humongous_start_region(nullptr),
  _index_in_opt_cset(InvalidCSetIndex),
  _next(nullptr), _prev(nullptr),
#ifdef ASSERT
  _containing_set(nullptr),
#endif
  _parsable_bottom(nullptr),
  _garbage_bytes(0),
  _young_index_in_cset(-1),
  _surv_rate_group(nullptr),
  _age_index(G1SurvRateGroup::InvalidAgeIndex),
  _node_index(G1NUMA::UnknownNodeIndex),
  _pinned_object_count(0)
{
  assert(Universe::on_page_boundary(mr.start()) && Universe::on_page_boundary(mr.end()),
         "invalid space boundaries");

  _rem_set = new HeapRegionRemSet(this, config);
  initialize();
}

void HeapRegion::initialize(bool clear_space, bool mangle_space) {
  assert(_rem_set->is_empty(), "Remembered set must be empty");

  if (clear_space) {
    clear(mangle_space);
  }

  set_top(bottom());

  hr_clear(false /*clear_space*/);
}

void HeapRegion::report_region_type_change(G1HeapRegionTraceType::Type to) {
  HeapRegionTracer::send_region_type_change(_hrm_index,
                                            get_trace_type(),
                                            to,
                                            (uintptr_t)bottom(),
                                            used());
}

 void HeapRegion::note_evacuation_failure() {
  // PB must be bottom - we only evacuate old gen regions after scrubbing, and
  // young gen regions never have their PB set to anything other than bottom.
  assert(parsable_bottom_acquire() == bottom(), "must be");

  _garbage_bytes = 0;
}

void HeapRegion::note_self_forward_chunk_done(size_t garbage_bytes) {
  Atomic::add(&_garbage_bytes, garbage_bytes, memory_order_relaxed);
}

// Code roots support
void HeapRegion::add_code_root(nmethod* nm) {
  rem_set()->add_code_root(nm);
}

void HeapRegion::remove_code_root(nmethod* nm) {
  rem_set()->remove_code_root(nm);
}

void HeapRegion::code_roots_do(NMethodClosure* blk) const {
  rem_set()->code_roots_do(blk);
}

class VerifyCodeRootOopClosure: public OopClosure {
  const HeapRegion* _hr;
  bool _failures;
  bool _has_oops_in_region;

  template <class T> void do_oop_work(T* p) {
    T heap_oop = RawAccess<>::oop_load(p);
    if (!CompressedOops::is_null(heap_oop)) {
      oop obj = CompressedOops::decode_not_null(heap_oop);

      // Note: not all the oops embedded in the nmethod are in the
      // current region. We only look at those which are.
      if (_hr->is_in(obj)) {
        // Object is in the region. Check that its less than top
        if (_hr->top() <= cast_from_oop<HeapWord*>(obj)) {
          // Object is above top
          log_error(gc, verify)("Object " PTR_FORMAT " in region " HR_FORMAT " is above top ",
                                p2i(obj), HR_FORMAT_PARAMS(_hr));
          _failures = true;
          return;
        }
        // Nmethod has at least one oop in the current region
        _has_oops_in_region = true;
      }
    }
  }

public:
  VerifyCodeRootOopClosure(const HeapRegion* hr):
    _hr(hr), _failures(false), _has_oops_in_region(false) {}

  void do_oop(narrowOop* p) { do_oop_work(p); }
  void do_oop(oop* p)       { do_oop_work(p); }

  bool failures()           { return _failures; }
  bool has_oops_in_region() { return _has_oops_in_region; }
};

class VerifyCodeRootNMethodClosure: public NMethodClosure {
  const HeapRegion* _hr;
  bool _failures;
public:
  VerifyCodeRootNMethodClosure(const HeapRegion* hr) :
    _hr(hr), _failures(false) {}

  void do_nmethod(nmethod* nm) {
    assert(nm != nullptr, "Sanity");

    // Verify that the nmethod is live
    VerifyCodeRootOopClosure oop_cl(_hr);
    nm->oops_do(&oop_cl);
    if (!oop_cl.has_oops_in_region()) {
      log_error(gc, verify)("region [" PTR_FORMAT "," PTR_FORMAT "] has nmethod " PTR_FORMAT " in its code roots with no pointers into region",
          p2i(_hr->bottom()), p2i(_hr->end()), p2i(nm));
      _failures = true;
    } else if (oop_cl.failures()) {
      log_error(gc, verify)("region [" PTR_FORMAT "," PTR_FORMAT "] has other failures for nmethod " PTR_FORMAT,
          p2i(_hr->bottom()), p2i(_hr->end()), p2i(nm));
      _failures = true;
    }
  }

  bool failures()       { return _failures; }
};

bool HeapRegion::verify_code_roots(VerifyOption vo) const {
  if (!G1VerifyHeapRegionCodeRoots) {
    // We're not verifying code roots.
    return false;
  }
  if (vo == VerifyOption::G1UseFullMarking) {
    // Marking verification during a full GC is performed after class
    // unloading, code cache unloading, etc so the code roots
    // attached to each heap region are in an inconsistent state. They won't
    // be consistent until the code roots are rebuilt after the
    // actual GC. Skip verifying the code roots in this particular
    // time.
    assert(VerifyDuringGC, "only way to get here");
    return false;
  }

  HeapRegionRemSet* hrrs = rem_set();
  size_t code_roots_length = hrrs->code_roots_list_length();

  // if this region is empty then there should be no entries
  // on its code root list
  if (is_empty()) {
    bool has_code_roots = code_roots_length > 0;
    if (has_code_roots) {
      log_error(gc, verify)("region " HR_FORMAT " is empty but has " SIZE_FORMAT " code root entries",
                            HR_FORMAT_PARAMS(this), code_roots_length);
    }
    return has_code_roots;
  }

  if (is_continues_humongous()) {
    bool has_code_roots = code_roots_length > 0;
    if (has_code_roots) {
      log_error(gc, verify)("region " HR_FORMAT " is a continuation of a humongous region but has " SIZE_FORMAT " code root entries",
                            HR_FORMAT_PARAMS(this), code_roots_length);
    }
    return has_code_roots;
  }

  VerifyCodeRootNMethodClosure nm_cl(this);
  code_roots_do(&nm_cl);

  return nm_cl.failures();
}

void HeapRegion::print() const { print_on(tty); }

void HeapRegion::print_on(outputStream* st) const {
  st->print("|%4u", this->_hrm_index);
  st->print("|" PTR_FORMAT ", " PTR_FORMAT ", " PTR_FORMAT,
            p2i(bottom()), p2i(top()), p2i(end()));
  st->print("|%3d%%", (int) ((double) used() * 100 / capacity()));
  st->print("|%2s", get_short_type_str());
  if (in_collection_set()) {
    st->print("|CS");
  } else if (is_collection_set_candidate()) {
    G1CollectionSetCandidates* candidates = G1CollectedHeap::heap()->collection_set()->candidates();
    st->print("|%s", candidates->get_short_type_str(this));
  } else {
    st->print("|  ");
  }
  G1ConcurrentMark* cm = G1CollectedHeap::heap()->concurrent_mark();
  st->print("|TAMS " PTR_FORMAT "| PB " PTR_FORMAT "| %s ",
            p2i(cm->top_at_mark_start(this)), p2i(parsable_bottom_acquire()), rem_set()->get_state_str());
  if (UseNUMA) {
    G1NUMA* numa = G1NUMA::numa();
    if (node_index() < numa->num_active_nodes()) {
      st->print("|%u", numa->numa_id(node_index()));
    } else {
      st->print("|-");
    }
  }
  st->print("|%3zu", Atomic::load(&_pinned_object_count));
  st->print_cr("");
}

static bool is_oop_safe(oop obj) {
  // Make a few sanity checks of the class before calling the full-fledged
  // is_oop check (which also performs its own klass verification).
  Klass* klass = obj->klass_without_asserts();

  if (klass == nullptr) {
    log_error(gc, verify)("Object " PTR_FORMAT " has a null klass", p2i(obj));
    return false;
  }

  if (!Metaspace::contains(klass)) {
    log_error(gc, verify)("klass " PTR_FORMAT " of object " PTR_FORMAT " "
                          "is not in metaspace", p2i(klass), p2i(obj));
    return false;
  }

  if (!klass->is_klass()) {
    log_error(gc, verify)("klass " PTR_FORMAT " of object " PTR_FORMAT " "
                          "not a klass", p2i(klass), p2i(obj));
    return false;
  }

  // Now, perform the more in-depth verification of the object.
  if (!oopDesc::is_oop(obj)) {
    log_error(gc, verify)(PTR_FORMAT " not an oop", p2i(obj));
    return false;
  }

  return true;
}

class G1VerifyFailureCounter {
  size_t _count;

public:
  G1VerifyFailureCounter() : _count(0) {}

  // Increases the failure counter and return whether this has been the first failure.
  bool record_failure() {
    _count++;
    return _count == 1;
  }

  size_t count() const { return _count; }
};

// Closure that glues together validity check for oop references (first),
// then optionally verifies the remembered set for that reference.
class G1VerifyLiveAndRemSetClosure : public BasicOopIterateClosure {
  const VerifyOption _vo;
  const oop _containing_obj;
  G1VerifyFailureCounter* const _failures;

  // Increases the failure counter and return whether this has been the first failure.
  bool record_failure() {
    return _failures->record_failure();
  }

  static void print_object(outputStream* out, oop obj) {
#ifdef PRODUCT
    obj->print_name_on(out);
#else // PRODUCT
    obj->print_on(out);
#endif // PRODUCT
  }

  template <class T>
  struct Checker {
    G1CollectedHeap* _g1h;
    G1VerifyFailureCounter* _failures;
    oop _containing_obj;
    T* _p;
    oop _obj;

    Checker(G1VerifyFailureCounter* failures, oop containing_obj, T* p, oop obj) :
      _g1h(G1CollectedHeap::heap()),
      _failures(failures),
      _containing_obj(containing_obj),
      _p(p),
      _obj(obj) { }

    bool record_failure() {
      return _failures->record_failure();
    }

    void print_containing_obj(outputStream* out, HeapRegion* from) {
      log_error(gc, verify)("Field " PTR_FORMAT " of obj " PTR_FORMAT " in region " HR_FORMAT,
                            p2i(_p), p2i(_containing_obj), HR_FORMAT_PARAMS(from));
      print_object(out, _containing_obj);
    }

    void print_referenced_obj(outputStream* out, HeapRegion* to, const char* explanation) {
      log_error(gc, verify)("points to %sobj " PTR_FORMAT " in region " HR_FORMAT " remset %s",
                            explanation, p2i(_obj), HR_FORMAT_PARAMS(to), to->rem_set()->get_state_str());
      print_object(out, _obj);
    }
  };

  template <class T>
  struct LiveChecker : public Checker<T> {
    VerifyOption _vo;
    bool _is_in_heap;

    LiveChecker(G1VerifyFailureCounter* failures, oop containing_obj, T* p, oop obj, VerifyOption vo)
      : Checker<T>(failures, containing_obj, p, obj) {
      _vo = vo;
      _is_in_heap = this->_g1h->is_in(obj);
    }

    bool failed() const {
      return !_is_in_heap || this->_g1h->is_obj_dead_cond(this->_obj, _vo);
    }

    void report_error() {
      ResourceMark rm;
      Log(gc, verify) log;
      LogStream ls(log.error());

      MutexLocker x(G1RareEvent_lock, Mutex::_no_safepoint_check_flag);

      if (this->record_failure()) {
        log.error("----------");
      }

      HeapRegion* from = this->_g1h->heap_region_containing(this->_p);
      this->print_containing_obj(&ls, from);

      if (!_is_in_heap) {
        log.error("points to address " PTR_FORMAT " outside of heap", p2i(this->_obj));
      } else {
        HeapRegion* to = this->_g1h->heap_region_containing(this->_obj);
        this->print_referenced_obj(&ls, to, "dead ");
      }
      log.error("----------");
    }
  };

  template <class T>
  struct RemSetChecker : public Checker<T> {
    using CardValue = CardTable::CardValue;

    HeapRegion* _from;
    HeapRegion* _to;
    CardValue _cv_obj;
    CardValue _cv_field;

    RemSetChecker(G1VerifyFailureCounter* failures, oop containing_obj, T* p, oop obj)
      : Checker<T>(failures, containing_obj, p, obj) {
      _from = this->_g1h->heap_region_containing(p);
      _to = this->_g1h->heap_region_containing(obj);

      CardTable* ct = this->_g1h->card_table();
      _cv_obj = *ct->byte_for_const(this->_containing_obj);
      _cv_field = *ct->byte_for_const(p);
    }

    bool failed() const {
      if (_from != _to && !_from->is_young() && _to->rem_set()->is_complete()) {
        const CardValue dirty = G1CardTable::dirty_card_val();
        return !(_to->rem_set()->contains_reference(this->_p) ||
                 (this->_containing_obj->is_objArray() ?
                  _cv_field == dirty :
                  _cv_obj == dirty || _cv_field == dirty));
      }
      return false;
    }

    void report_error() {
      ResourceMark rm;
      Log(gc, verify) log;
      LogStream ls(log.error());

      MutexLocker x(G1RareEvent_lock, Mutex::_no_safepoint_check_flag);

      if (this->record_failure()) {
        log.error("----------");
      }
      log.error("Missing rem set entry:");
      this->print_containing_obj(&ls, _from);
      this->print_referenced_obj(&ls, _to, "");
      log.error("Obj head CV = %d, field CV = %d.", _cv_obj, _cv_field);
      log.error("----------");
    }
  };

  template <class T>
  void do_oop_work(T* p) {
    // Check for null references first - they are fairly common and since there is
    // nothing to do for them anyway (they can't fail verification), it makes sense
    // to handle them first.
    T heap_oop = RawAccess<>::oop_load(p);
    if (CompressedOops::is_null(heap_oop)) {
      return;
    }

    if (_failures->count() >= G1MaxVerifyFailures) {
      return;
    }

    oop obj = CompressedOops::decode_raw_not_null(heap_oop);

    LiveChecker<T> live_check(_failures, _containing_obj, p, obj, _vo);
    if (live_check.failed()) {
      live_check.report_error();
      // There is no point in doing remset verification if the reference is bad.
      return;
    }

    RemSetChecker<T> remset_check(_failures, _containing_obj, p, obj);
    if (remset_check.failed()) {
      remset_check.report_error();
    }
  }

public:
  G1VerifyLiveAndRemSetClosure(oop containing_obj, VerifyOption vo, G1VerifyFailureCounter* failures)
    : _vo(vo),
      _containing_obj(containing_obj),
      _failures(failures) {}

  virtual inline void do_oop(narrowOop* p) { do_oop_work(p); }
  virtual inline void do_oop(oop* p) { do_oop_work(p); }
};

bool HeapRegion::verify_liveness_and_remset(VerifyOption vo) const {
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  G1VerifyFailureCounter failures;

  HeapWord* p;
  for (p = bottom(); p < top(); p += block_size(p)) {
    oop obj = cast_to_oop(p);

    if (g1h->is_obj_dead_cond(obj, this, vo)) {
      continue;
    }

    if (is_oop_safe(obj)) {
      G1VerifyLiveAndRemSetClosure cl(obj, vo, &failures);
      obj->oop_iterate(&cl);
    } else {
      failures.record_failure();
    }

    if (failures.count() >= G1MaxVerifyFailures) {
      return true;
    }
  }

  if (!is_humongous() && p != top()) {
    log_error(gc, verify)("end of last object " PTR_FORMAT " does not match top " PTR_FORMAT,
                          p2i(p), p2i(top()));
    return true;
  }
  return failures.count() != 0;
}

bool HeapRegion::verify(VerifyOption vo) const {
  // We cast p to an oop, so region-bottom must be an obj-start.
  assert(!is_humongous() || is_starts_humongous(), "invariant");

  if (verify_liveness_and_remset(vo)) {
    return true;
  }

  if (is_humongous()) {
    oop obj = cast_to_oop(this->humongous_start_region()->bottom());
    if (cast_from_oop<HeapWord*>(obj) > bottom() || cast_from_oop<HeapWord*>(obj) + obj->size() < bottom()) {
      log_error(gc, verify)("this humongous region is not part of its' humongous object " PTR_FORMAT, p2i(obj));
      return true;
    }
  }

  return verify_code_roots(vo);
}

void HeapRegion::clear(bool mangle_space) {
  set_top(bottom());

  if (ZapUnusedHeapArea && mangle_space) {
    mangle_unused_area();
  }
}

#ifndef PRODUCT
void HeapRegion::mangle_unused_area() {
  SpaceMangler::mangle_region(MemRegion(top(), end()));
}
#endif

void HeapRegion::object_iterate(ObjectClosure* blk) {
  HeapWord* p = bottom();
  while (p < top()) {
    if (block_is_obj(p, parsable_bottom())) {
      blk->do_object(cast_to_oop(p));
    }
    p += block_size(p);
  }
}

void HeapRegion::fill_with_dummy_object(HeapWord* address, size_t word_size, bool zap) {
  // Keep the BOT in sync for old generation regions.
  if (is_old()) {
    update_bot_for_block(address, address + word_size);
  }
  // Fill in the object.
  CollectedHeap::fill_with_object(address, word_size, zap);
}

void HeapRegion::fill_range_with_dead_objects(HeapWord* start, HeapWord* end) {
  size_t range_size = pointer_delta(end, start);

  // We must be a bit careful with regions that contain pinned objects. While the
  // ranges passed in here corresponding to the space between live objects, it is
  // possible that there is a pinned object that is not any more referenced by
  // Java code (only by native).
  //
  // In this case we must not zap contents of such an array but we can overwrite
  // the header; since only pinned typearrays are allowed, this fits nicely with
  // putting filler arrays into the dead range as the object header sizes match and
  // no user data is overwritten.
  //
  // In particular String Deduplication might change the reference to the character
  // array of the j.l.String after native code obtained a raw reference to it (via
  // GetStringCritical()).
  CollectedHeap::fill_with_objects(start, range_size, !has_pinned_objects());
  HeapWord* current = start;
  do {
    // Update the BOT if the a threshold is crossed.
    size_t obj_size = cast_to_oop(current)->size();
    update_bot_for_block(current, current + obj_size);

    // Advance to the next object.
    current += obj_size;
    guarantee(current <= end, "Should never go past end");
  } while (current != end);
}
