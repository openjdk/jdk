/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_concurrentMarkSweepGeneration.cpp.incl"

// statics
CMSCollector* ConcurrentMarkSweepGeneration::_collector = NULL;
bool          CMSCollector::_full_gc_requested          = false;

//////////////////////////////////////////////////////////////////
// In support of CMS/VM thread synchronization
//////////////////////////////////////////////////////////////////
// We split use of the CGC_lock into 2 "levels".
// The low-level locking is of the usual CGC_lock monitor. We introduce
// a higher level "token" (hereafter "CMS token") built on top of the
// low level monitor (hereafter "CGC lock").
// The token-passing protocol gives priority to the VM thread. The
// CMS-lock doesn't provide any fairness guarantees, but clients
// should ensure that it is only held for very short, bounded
// durations.
//
// When either of the CMS thread or the VM thread is involved in
// collection operations during which it does not want the other
// thread to interfere, it obtains the CMS token.
//
// If either thread tries to get the token while the other has
// it, that thread waits. However, if the VM thread and CMS thread
// both want the token, then the VM thread gets priority while the
// CMS thread waits. This ensures, for instance, that the "concurrent"
// phases of the CMS thread's work do not block out the VM thread
// for long periods of time as the CMS thread continues to hog
// the token. (See bug 4616232).
//
// The baton-passing functions are, however, controlled by the
// flags _foregroundGCShouldWait and _foregroundGCIsActive,
// and here the low-level CMS lock, not the high level token,
// ensures mutual exclusion.
//
// Two important conditions that we have to satisfy:
// 1. if a thread does a low-level wait on the CMS lock, then it
//    relinquishes the CMS token if it were holding that token
//    when it acquired the low-level CMS lock.
// 2. any low-level notifications on the low-level lock
//    should only be sent when a thread has relinquished the token.
//
// In the absence of either property, we'd have potential deadlock.
//
// We protect each of the CMS (concurrent and sequential) phases
// with the CMS _token_, not the CMS _lock_.
//
// The only code protected by CMS lock is the token acquisition code
// itself, see ConcurrentMarkSweepThread::[de]synchronize(), and the
// baton-passing code.
//
// Unfortunately, i couldn't come up with a good abstraction to factor and
// hide the naked CGC_lock manipulation in the baton-passing code
// further below. That's something we should try to do. Also, the proof
// of correctness of this 2-level locking scheme is far from obvious,
// and potentially quite slippery. We have an uneasy supsicion, for instance,
// that there may be a theoretical possibility of delay/starvation in the
// low-level lock/wait/notify scheme used for the baton-passing because of
// potential intereference with the priority scheme embodied in the
// CMS-token-passing protocol. See related comments at a CGC_lock->wait()
// invocation further below and marked with "XXX 20011219YSR".
// Indeed, as we note elsewhere, this may become yet more slippery
// in the presence of multiple CMS and/or multiple VM threads. XXX

class CMSTokenSync: public StackObj {
 private:
  bool _is_cms_thread;
 public:
  CMSTokenSync(bool is_cms_thread):
    _is_cms_thread(is_cms_thread) {
    assert(is_cms_thread == Thread::current()->is_ConcurrentGC_thread(),
           "Incorrect argument to constructor");
    ConcurrentMarkSweepThread::synchronize(_is_cms_thread);
  }

  ~CMSTokenSync() {
    assert(_is_cms_thread ?
             ConcurrentMarkSweepThread::cms_thread_has_cms_token() :
             ConcurrentMarkSweepThread::vm_thread_has_cms_token(),
          "Incorrect state");
    ConcurrentMarkSweepThread::desynchronize(_is_cms_thread);
  }
};

// Convenience class that does a CMSTokenSync, and then acquires
// upto three locks.
class CMSTokenSyncWithLocks: public CMSTokenSync {
 private:
  // Note: locks are acquired in textual declaration order
  // and released in the opposite order
  MutexLockerEx _locker1, _locker2, _locker3;
 public:
  CMSTokenSyncWithLocks(bool is_cms_thread, Mutex* mutex1,
                        Mutex* mutex2 = NULL, Mutex* mutex3 = NULL):
    CMSTokenSync(is_cms_thread),
    _locker1(mutex1, Mutex::_no_safepoint_check_flag),
    _locker2(mutex2, Mutex::_no_safepoint_check_flag),
    _locker3(mutex3, Mutex::_no_safepoint_check_flag)
  { }
};


// Wrapper class to temporarily disable icms during a foreground cms collection.
class ICMSDisabler: public StackObj {
 public:
  // The ctor disables icms and wakes up the thread so it notices the change;
  // the dtor re-enables icms.  Note that the CMSCollector methods will check
  // CMSIncrementalMode.
  ICMSDisabler()  { CMSCollector::disable_icms(); CMSCollector::start_icms(); }
  ~ICMSDisabler() { CMSCollector::enable_icms(); }
};

//////////////////////////////////////////////////////////////////
//  Concurrent Mark-Sweep Generation /////////////////////////////
//////////////////////////////////////////////////////////////////

NOT_PRODUCT(CompactibleFreeListSpace* debug_cms_space;)

// This struct contains per-thread things necessary to support parallel
// young-gen collection.
class CMSParGCThreadState: public CHeapObj {
 public:
  CFLS_LAB lab;
  PromotionInfo promo;

  // Constructor.
  CMSParGCThreadState(CompactibleFreeListSpace* cfls) : lab(cfls) {
    promo.setSpace(cfls);
  }
};

ConcurrentMarkSweepGeneration::ConcurrentMarkSweepGeneration(
     ReservedSpace rs, size_t initial_byte_size, int level,
     CardTableRS* ct, bool use_adaptive_freelists,
     FreeBlockDictionary::DictionaryChoice dictionaryChoice) :
  CardGeneration(rs, initial_byte_size, level, ct),
  _dilatation_factor(((double)MinChunkSize)/((double)(CollectedHeap::min_fill_size()))),
  _debug_collection_type(Concurrent_collection_type)
{
  HeapWord* bottom = (HeapWord*) _virtual_space.low();
  HeapWord* end    = (HeapWord*) _virtual_space.high();

  _direct_allocated_words = 0;
  NOT_PRODUCT(
    _numObjectsPromoted = 0;
    _numWordsPromoted = 0;
    _numObjectsAllocated = 0;
    _numWordsAllocated = 0;
  )

  _cmsSpace = new CompactibleFreeListSpace(_bts, MemRegion(bottom, end),
                                           use_adaptive_freelists,
                                           dictionaryChoice);
  NOT_PRODUCT(debug_cms_space = _cmsSpace;)
  if (_cmsSpace == NULL) {
    vm_exit_during_initialization(
      "CompactibleFreeListSpace allocation failure");
  }
  _cmsSpace->_gen = this;

  _gc_stats = new CMSGCStats();

  // Verify the assumption that FreeChunk::_prev and OopDesc::_klass
  // offsets match. The ability to tell free chunks from objects
  // depends on this property.
  debug_only(
    FreeChunk* junk = NULL;
    assert(UseCompressedOops ||
           junk->prev_addr() == (void*)(oop(junk)->klass_addr()),
           "Offset of FreeChunk::_prev within FreeChunk must match"
           "  that of OopDesc::_klass within OopDesc");
  )
  if (ParallelGCThreads > 0) {
    typedef CMSParGCThreadState* CMSParGCThreadStatePtr;
    _par_gc_thread_states =
      NEW_C_HEAP_ARRAY(CMSParGCThreadStatePtr, ParallelGCThreads);
    if (_par_gc_thread_states == NULL) {
      vm_exit_during_initialization("Could not allocate par gc structs");
    }
    for (uint i = 0; i < ParallelGCThreads; i++) {
      _par_gc_thread_states[i] = new CMSParGCThreadState(cmsSpace());
      if (_par_gc_thread_states[i] == NULL) {
        vm_exit_during_initialization("Could not allocate par gc structs");
      }
    }
  } else {
    _par_gc_thread_states = NULL;
  }
  _incremental_collection_failed = false;
  // The "dilatation_factor" is the expansion that can occur on
  // account of the fact that the minimum object size in the CMS
  // generation may be larger than that in, say, a contiguous young
  //  generation.
  // Ideally, in the calculation below, we'd compute the dilatation
  // factor as: MinChunkSize/(promoting_gen's min object size)
  // Since we do not have such a general query interface for the
  // promoting generation, we'll instead just use the mimimum
  // object size (which today is a header's worth of space);
  // note that all arithmetic is in units of HeapWords.
  assert(MinChunkSize >= CollectedHeap::min_fill_size(), "just checking");
  assert(_dilatation_factor >= 1.0, "from previous assert");
}


// The field "_initiating_occupancy" represents the occupancy percentage
// at which we trigger a new collection cycle.  Unless explicitly specified
// via CMSInitiating[Perm]OccupancyFraction (argument "io" below), it
// is calculated by:
//
//   Let "f" be MinHeapFreeRatio in
//
//    _intiating_occupancy = 100-f +
//                           f * (CMSTrigger[Perm]Ratio/100)
//   where CMSTrigger[Perm]Ratio is the argument "tr" below.
//
// That is, if we assume the heap is at its desired maximum occupancy at the
// end of a collection, we let CMSTrigger[Perm]Ratio of the (purported) free
// space be allocated before initiating a new collection cycle.
//
void ConcurrentMarkSweepGeneration::init_initiating_occupancy(intx io, intx tr) {
  assert(io <= 100 && tr >= 0 && tr <= 100, "Check the arguments");
  if (io >= 0) {
    _initiating_occupancy = (double)io / 100.0;
  } else {
    _initiating_occupancy = ((100 - MinHeapFreeRatio) +
                             (double)(tr * MinHeapFreeRatio) / 100.0)
                            / 100.0;
  }
}

void ConcurrentMarkSweepGeneration::ref_processor_init() {
  assert(collector() != NULL, "no collector");
  collector()->ref_processor_init();
}

void CMSCollector::ref_processor_init() {
  if (_ref_processor == NULL) {
    // Allocate and initialize a reference processor
    _ref_processor = ReferenceProcessor::create_ref_processor(
        _span,                               // span
        _cmsGen->refs_discovery_is_atomic(), // atomic_discovery
        _cmsGen->refs_discovery_is_mt(),     // mt_discovery
        &_is_alive_closure,
        ParallelGCThreads,
        ParallelRefProcEnabled);
    // Initialize the _ref_processor field of CMSGen
    _cmsGen->set_ref_processor(_ref_processor);

    // Allocate a dummy ref processor for perm gen.
    ReferenceProcessor* rp2 = new ReferenceProcessor();
    if (rp2 == NULL) {
      vm_exit_during_initialization("Could not allocate ReferenceProcessor object");
    }
    _permGen->set_ref_processor(rp2);
  }
}

CMSAdaptiveSizePolicy* CMSCollector::size_policy() {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  assert(gch->kind() == CollectedHeap::GenCollectedHeap,
    "Wrong type of heap");
  CMSAdaptiveSizePolicy* sp = (CMSAdaptiveSizePolicy*)
    gch->gen_policy()->size_policy();
  assert(sp->is_gc_cms_adaptive_size_policy(),
    "Wrong type of size policy");
  return sp;
}

CMSGCAdaptivePolicyCounters* CMSCollector::gc_adaptive_policy_counters() {
  CMSGCAdaptivePolicyCounters* results =
    (CMSGCAdaptivePolicyCounters*) collector_policy()->counters();
  assert(
    results->kind() == GCPolicyCounters::CMSGCAdaptivePolicyCountersKind,
    "Wrong gc policy counter kind");
  return results;
}


void ConcurrentMarkSweepGeneration::initialize_performance_counters() {

  const char* gen_name = "old";

  // Generation Counters - generation 1, 1 subspace
  _gen_counters = new GenerationCounters(gen_name, 1, 1, &_virtual_space);

  _space_counters = new GSpaceCounters(gen_name, 0,
                                       _virtual_space.reserved_size(),
                                       this, _gen_counters);
}

CMSStats::CMSStats(ConcurrentMarkSweepGeneration* cms_gen, unsigned int alpha):
  _cms_gen(cms_gen)
{
  assert(alpha <= 100, "bad value");
  _saved_alpha = alpha;

  // Initialize the alphas to the bootstrap value of 100.
  _gc0_alpha = _cms_alpha = 100;

  _cms_begin_time.update();
  _cms_end_time.update();

  _gc0_duration = 0.0;
  _gc0_period = 0.0;
  _gc0_promoted = 0;

  _cms_duration = 0.0;
  _cms_period = 0.0;
  _cms_allocated = 0;

  _cms_used_at_gc0_begin = 0;
  _cms_used_at_gc0_end = 0;
  _allow_duty_cycle_reduction = false;
  _valid_bits = 0;
  _icms_duty_cycle = CMSIncrementalDutyCycle;
}

double CMSStats::cms_free_adjustment_factor(size_t free) const {
  // TBD: CR 6909490
  return 1.0;
}

void CMSStats::adjust_cms_free_adjustment_factor(bool fail, size_t free) {
}

// If promotion failure handling is on use
// the padded average size of the promotion for each
// young generation collection.
double CMSStats::time_until_cms_gen_full() const {
  size_t cms_free = _cms_gen->cmsSpace()->free();
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  size_t expected_promotion = gch->get_gen(0)->capacity();
  if (HandlePromotionFailure) {
    expected_promotion = MIN2(
        (size_t) _cms_gen->gc_stats()->avg_promoted()->padded_average(),
        expected_promotion);
  }
  if (cms_free > expected_promotion) {
    // Start a cms collection if there isn't enough space to promote
    // for the next minor collection.  Use the padded average as
    // a safety factor.
    cms_free -= expected_promotion;

    // Adjust by the safety factor.
    double cms_free_dbl = (double)cms_free;
    double cms_adjustment = (100.0 - CMSIncrementalSafetyFactor)/100.0;
    // Apply a further correction factor which tries to adjust
    // for recent occurance of concurrent mode failures.
    cms_adjustment = cms_adjustment * cms_free_adjustment_factor(cms_free);
    cms_free_dbl = cms_free_dbl * cms_adjustment;

    if (PrintGCDetails && Verbose) {
      gclog_or_tty->print_cr("CMSStats::time_until_cms_gen_full: cms_free "
        SIZE_FORMAT " expected_promotion " SIZE_FORMAT,
        cms_free, expected_promotion);
      gclog_or_tty->print_cr("  cms_free_dbl %f cms_consumption_rate %f",
        cms_free_dbl, cms_consumption_rate() + 1.0);
    }
    // Add 1 in case the consumption rate goes to zero.
    return cms_free_dbl / (cms_consumption_rate() + 1.0);
  }
  return 0.0;
}

// Compare the duration of the cms collection to the
// time remaining before the cms generation is empty.
// Note that the time from the start of the cms collection
// to the start of the cms sweep (less than the total
// duration of the cms collection) can be used.  This
// has been tried and some applications experienced
// promotion failures early in execution.  This was
// possibly because the averages were not accurate
// enough at the beginning.
double CMSStats::time_until_cms_start() const {
  // We add "gc0_period" to the "work" calculation
  // below because this query is done (mostly) at the
  // end of a scavenge, so we need to conservatively
  // account for that much possible delay
  // in the query so as to avoid concurrent mode failures
  // due to starting the collection just a wee bit too
  // late.
  double work = cms_duration() + gc0_period();
  double deadline = time_until_cms_gen_full();
  // If a concurrent mode failure occurred recently, we want to be
  // more conservative and halve our expected time_until_cms_gen_full()
  if (work > deadline) {
    if (Verbose && PrintGCDetails) {
      gclog_or_tty->print(
        " CMSCollector: collect because of anticipated promotion "
        "before full %3.7f + %3.7f > %3.7f ", cms_duration(),
        gc0_period(), time_until_cms_gen_full());
    }
    return 0.0;
  }
  return work - deadline;
}

// Return a duty cycle based on old_duty_cycle and new_duty_cycle, limiting the
// amount of change to prevent wild oscillation.
unsigned int CMSStats::icms_damped_duty_cycle(unsigned int old_duty_cycle,
                                              unsigned int new_duty_cycle) {
  assert(old_duty_cycle <= 100, "bad input value");
  assert(new_duty_cycle <= 100, "bad input value");

  // Note:  use subtraction with caution since it may underflow (values are
  // unsigned).  Addition is safe since we're in the range 0-100.
  unsigned int damped_duty_cycle = new_duty_cycle;
  if (new_duty_cycle < old_duty_cycle) {
    const unsigned int largest_delta = MAX2(old_duty_cycle / 4, 5U);
    if (new_duty_cycle + largest_delta < old_duty_cycle) {
      damped_duty_cycle = old_duty_cycle - largest_delta;
    }
  } else if (new_duty_cycle > old_duty_cycle) {
    const unsigned int largest_delta = MAX2(old_duty_cycle / 4, 15U);
    if (new_duty_cycle > old_duty_cycle + largest_delta) {
      damped_duty_cycle = MIN2(old_duty_cycle + largest_delta, 100U);
    }
  }
  assert(damped_duty_cycle <= 100, "invalid duty cycle computed");

  if (CMSTraceIncrementalPacing) {
    gclog_or_tty->print(" [icms_damped_duty_cycle(%d,%d) = %d] ",
                           old_duty_cycle, new_duty_cycle, damped_duty_cycle);
  }
  return damped_duty_cycle;
}

unsigned int CMSStats::icms_update_duty_cycle_impl() {
  assert(CMSIncrementalPacing && valid(),
         "should be handled in icms_update_duty_cycle()");

  double cms_time_so_far = cms_timer().seconds();
  double scaled_duration = cms_duration_per_mb() * _cms_used_at_gc0_end / M;
  double scaled_duration_remaining = fabsd(scaled_duration - cms_time_so_far);

  // Avoid division by 0.
  double time_until_full = MAX2(time_until_cms_gen_full(), 0.01);
  double duty_cycle_dbl = 100.0 * scaled_duration_remaining / time_until_full;

  unsigned int new_duty_cycle = MIN2((unsigned int)duty_cycle_dbl, 100U);
  if (new_duty_cycle > _icms_duty_cycle) {
    // Avoid very small duty cycles (1 or 2); 0 is allowed.
    if (new_duty_cycle > 2) {
      _icms_duty_cycle = icms_damped_duty_cycle(_icms_duty_cycle,
                                                new_duty_cycle);
    }
  } else if (_allow_duty_cycle_reduction) {
    // The duty cycle is reduced only once per cms cycle (see record_cms_end()).
    new_duty_cycle = icms_damped_duty_cycle(_icms_duty_cycle, new_duty_cycle);
    // Respect the minimum duty cycle.
    unsigned int min_duty_cycle = (unsigned int)CMSIncrementalDutyCycleMin;
    _icms_duty_cycle = MAX2(new_duty_cycle, min_duty_cycle);
  }

  if (PrintGCDetails || CMSTraceIncrementalPacing) {
    gclog_or_tty->print(" icms_dc=%d ", _icms_duty_cycle);
  }

  _allow_duty_cycle_reduction = false;
  return _icms_duty_cycle;
}

#ifndef PRODUCT
void CMSStats::print_on(outputStream *st) const {
  st->print(" gc0_alpha=%d,cms_alpha=%d", _gc0_alpha, _cms_alpha);
  st->print(",gc0_dur=%g,gc0_per=%g,gc0_promo=" SIZE_FORMAT,
               gc0_duration(), gc0_period(), gc0_promoted());
  st->print(",cms_dur=%g,cms_dur_per_mb=%g,cms_per=%g,cms_alloc=" SIZE_FORMAT,
            cms_duration(), cms_duration_per_mb(),
            cms_period(), cms_allocated());
  st->print(",cms_since_beg=%g,cms_since_end=%g",
            cms_time_since_begin(), cms_time_since_end());
  st->print(",cms_used_beg=" SIZE_FORMAT ",cms_used_end=" SIZE_FORMAT,
            _cms_used_at_gc0_begin, _cms_used_at_gc0_end);
  if (CMSIncrementalMode) {
    st->print(",dc=%d", icms_duty_cycle());
  }

  if (valid()) {
    st->print(",promo_rate=%g,cms_alloc_rate=%g",
              promotion_rate(), cms_allocation_rate());
    st->print(",cms_consumption_rate=%g,time_until_full=%g",
              cms_consumption_rate(), time_until_cms_gen_full());
  }
  st->print(" ");
}
#endif // #ifndef PRODUCT

CMSCollector::CollectorState CMSCollector::_collectorState =
                             CMSCollector::Idling;
bool CMSCollector::_foregroundGCIsActive = false;
bool CMSCollector::_foregroundGCShouldWait = false;

CMSCollector::CMSCollector(ConcurrentMarkSweepGeneration* cmsGen,
                           ConcurrentMarkSweepGeneration* permGen,
                           CardTableRS*                   ct,
                           ConcurrentMarkSweepPolicy*     cp):
  _cmsGen(cmsGen),
  _permGen(permGen),
  _ct(ct),
  _ref_processor(NULL),    // will be set later
  _conc_workers(NULL),     // may be set later
  _abort_preclean(false),
  _start_sampling(false),
  _between_prologue_and_epilogue(false),
  _markBitMap(0, Mutex::leaf + 1, "CMS_markBitMap_lock"),
  _perm_gen_verify_bit_map(0, -1 /* no mutex */, "No_lock"),
  _modUnionTable((CardTableModRefBS::card_shift - LogHeapWordSize),
                 -1 /* lock-free */, "No_lock" /* dummy */),
  _modUnionClosure(&_modUnionTable),
  _modUnionClosurePar(&_modUnionTable),
  // Adjust my span to cover old (cms) gen and perm gen
  _span(cmsGen->reserved()._union(permGen->reserved())),
  // Construct the is_alive_closure with _span & markBitMap
  _is_alive_closure(_span, &_markBitMap),
  _restart_addr(NULL),
  _overflow_list(NULL),
  _preserved_oop_stack(NULL),
  _preserved_mark_stack(NULL),
  _stats(cmsGen),
  _eden_chunk_array(NULL),     // may be set in ctor body
  _eden_chunk_capacity(0),     // -- ditto --
  _eden_chunk_index(0),        // -- ditto --
  _survivor_plab_array(NULL),  // -- ditto --
  _survivor_chunk_array(NULL), // -- ditto --
  _survivor_chunk_capacity(0), // -- ditto --
  _survivor_chunk_index(0),    // -- ditto --
  _ser_pmc_preclean_ovflw(0),
  _ser_kac_preclean_ovflw(0),
  _ser_pmc_remark_ovflw(0),
  _par_pmc_remark_ovflw(0),
  _ser_kac_ovflw(0),
  _par_kac_ovflw(0),
#ifndef PRODUCT
  _num_par_pushes(0),
#endif
  _collection_count_start(0),
  _verifying(false),
  _icms_start_limit(NULL),
  _icms_stop_limit(NULL),
  _verification_mark_bm(0, Mutex::leaf + 1, "CMS_verification_mark_bm_lock"),
  _completed_initialization(false),
  _collector_policy(cp),
  _should_unload_classes(false),
  _concurrent_cycles_since_last_unload(0),
  _roots_scanning_options(0),
  _inter_sweep_estimate(CMS_SweepWeight, CMS_SweepPadding),
  _intra_sweep_estimate(CMS_SweepWeight, CMS_SweepPadding)
{
  if (ExplicitGCInvokesConcurrentAndUnloadsClasses) {
    ExplicitGCInvokesConcurrent = true;
  }
  // Now expand the span and allocate the collection support structures
  // (MUT, marking bit map etc.) to cover both generations subject to
  // collection.

  // First check that _permGen is adjacent to _cmsGen and above it.
  assert(   _cmsGen->reserved().word_size()  > 0
         && _permGen->reserved().word_size() > 0,
         "generations should not be of zero size");
  assert(_cmsGen->reserved().intersection(_permGen->reserved()).is_empty(),
         "_cmsGen and _permGen should not overlap");
  assert(_cmsGen->reserved().end() == _permGen->reserved().start(),
         "_cmsGen->end() different from _permGen->start()");

  // For use by dirty card to oop closures.
  _cmsGen->cmsSpace()->set_collector(this);
  _permGen->cmsSpace()->set_collector(this);

  // Allocate MUT and marking bit map
  {
    MutexLockerEx x(_markBitMap.lock(), Mutex::_no_safepoint_check_flag);
    if (!_markBitMap.allocate(_span)) {
      warning("Failed to allocate CMS Bit Map");
      return;
    }
    assert(_markBitMap.covers(_span), "_markBitMap inconsistency?");
  }
  {
    _modUnionTable.allocate(_span);
    assert(_modUnionTable.covers(_span), "_modUnionTable inconsistency?");
  }

  if (!_markStack.allocate(MarkStackSize)) {
    warning("Failed to allocate CMS Marking Stack");
    return;
  }
  if (!_revisitStack.allocate(CMSRevisitStackSize)) {
    warning("Failed to allocate CMS Revisit Stack");
    return;
  }

  // Support for multi-threaded concurrent phases
  if (ParallelGCThreads > 0 && CMSConcurrentMTEnabled) {
    if (FLAG_IS_DEFAULT(ConcGCThreads)) {
      // just for now
      FLAG_SET_DEFAULT(ConcGCThreads, (ParallelGCThreads + 3)/4);
    }
    if (ConcGCThreads > 1) {
      _conc_workers = new YieldingFlexibleWorkGang("Parallel CMS Threads",
                                 ConcGCThreads, true);
      if (_conc_workers == NULL) {
        warning("GC/CMS: _conc_workers allocation failure: "
              "forcing -CMSConcurrentMTEnabled");
        CMSConcurrentMTEnabled = false;
      }
    } else {
      CMSConcurrentMTEnabled = false;
    }
  }
  if (!CMSConcurrentMTEnabled) {
    ConcGCThreads = 0;
  } else {
    // Turn off CMSCleanOnEnter optimization temporarily for
    // the MT case where it's not fixed yet; see 6178663.
    CMSCleanOnEnter = false;
  }
  assert((_conc_workers != NULL) == (ConcGCThreads > 1),
         "Inconsistency");

  // Parallel task queues; these are shared for the
  // concurrent and stop-world phases of CMS, but
  // are not shared with parallel scavenge (ParNew).
  {
    uint i;
    uint num_queues = (uint) MAX2(ParallelGCThreads, ConcGCThreads);

    if ((CMSParallelRemarkEnabled || CMSConcurrentMTEnabled
         || ParallelRefProcEnabled)
        && num_queues > 0) {
      _task_queues = new OopTaskQueueSet(num_queues);
      if (_task_queues == NULL) {
        warning("task_queues allocation failure.");
        return;
      }
      _hash_seed = NEW_C_HEAP_ARRAY(int, num_queues);
      if (_hash_seed == NULL) {
        warning("_hash_seed array allocation failure");
        return;
      }

      // XXX use a global constant instead of 64!
      typedef struct OopTaskQueuePadded {
        OopTaskQueue work_queue;
        char pad[64 - sizeof(OopTaskQueue)];  // prevent false sharing
      } OopTaskQueuePadded;

      for (i = 0; i < num_queues; i++) {
        OopTaskQueuePadded *q_padded = new OopTaskQueuePadded();
        if (q_padded == NULL) {
          warning("work_queue allocation failure.");
          return;
        }
        _task_queues->register_queue(i, &q_padded->work_queue);
      }
      for (i = 0; i < num_queues; i++) {
        _task_queues->queue(i)->initialize();
        _hash_seed[i] = 17;  // copied from ParNew
      }
    }
  }

  _cmsGen ->init_initiating_occupancy(CMSInitiatingOccupancyFraction, CMSTriggerRatio);
  _permGen->init_initiating_occupancy(CMSInitiatingPermOccupancyFraction, CMSTriggerPermRatio);

  // Clip CMSBootstrapOccupancy between 0 and 100.
  _bootstrap_occupancy = ((double)MIN2((uintx)100, MAX2((uintx)0, CMSBootstrapOccupancy)))
                         /(double)100;

  _full_gcs_since_conc_gc = 0;

  // Now tell CMS generations the identity of their collector
  ConcurrentMarkSweepGeneration::set_collector(this);

  // Create & start a CMS thread for this CMS collector
  _cmsThread = ConcurrentMarkSweepThread::start(this);
  assert(cmsThread() != NULL, "CMS Thread should have been created");
  assert(cmsThread()->collector() == this,
         "CMS Thread should refer to this gen");
  assert(CGC_lock != NULL, "Where's the CGC_lock?");

  // Support for parallelizing young gen rescan
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  _young_gen = gch->prev_gen(_cmsGen);
  if (gch->supports_inline_contig_alloc()) {
    _top_addr = gch->top_addr();
    _end_addr = gch->end_addr();
    assert(_young_gen != NULL, "no _young_gen");
    _eden_chunk_index = 0;
    _eden_chunk_capacity = (_young_gen->max_capacity()+CMSSamplingGrain)/CMSSamplingGrain;
    _eden_chunk_array = NEW_C_HEAP_ARRAY(HeapWord*, _eden_chunk_capacity);
    if (_eden_chunk_array == NULL) {
      _eden_chunk_capacity = 0;
      warning("GC/CMS: _eden_chunk_array allocation failure");
    }
  }
  assert(_eden_chunk_array != NULL || _eden_chunk_capacity == 0, "Error");

  // Support for parallelizing survivor space rescan
  if (CMSParallelRemarkEnabled && CMSParallelSurvivorRemarkEnabled) {
    const size_t max_plab_samples =
      ((DefNewGeneration*)_young_gen)->max_survivor_size()/MinTLABSize;

    _survivor_plab_array  = NEW_C_HEAP_ARRAY(ChunkArray, ParallelGCThreads);
    _survivor_chunk_array = NEW_C_HEAP_ARRAY(HeapWord*, 2*max_plab_samples);
    _cursor               = NEW_C_HEAP_ARRAY(size_t, ParallelGCThreads);
    if (_survivor_plab_array == NULL || _survivor_chunk_array == NULL
        || _cursor == NULL) {
      warning("Failed to allocate survivor plab/chunk array");
      if (_survivor_plab_array  != NULL) {
        FREE_C_HEAP_ARRAY(ChunkArray, _survivor_plab_array);
        _survivor_plab_array = NULL;
      }
      if (_survivor_chunk_array != NULL) {
        FREE_C_HEAP_ARRAY(HeapWord*, _survivor_chunk_array);
        _survivor_chunk_array = NULL;
      }
      if (_cursor != NULL) {
        FREE_C_HEAP_ARRAY(size_t, _cursor);
        _cursor = NULL;
      }
    } else {
      _survivor_chunk_capacity = 2*max_plab_samples;
      for (uint i = 0; i < ParallelGCThreads; i++) {
        HeapWord** vec = NEW_C_HEAP_ARRAY(HeapWord*, max_plab_samples);
        if (vec == NULL) {
          warning("Failed to allocate survivor plab array");
          for (int j = i; j > 0; j--) {
            FREE_C_HEAP_ARRAY(HeapWord*, _survivor_plab_array[j-1].array());
          }
          FREE_C_HEAP_ARRAY(ChunkArray, _survivor_plab_array);
          FREE_C_HEAP_ARRAY(HeapWord*, _survivor_chunk_array);
          _survivor_plab_array = NULL;
          _survivor_chunk_array = NULL;
          _survivor_chunk_capacity = 0;
          break;
        } else {
          ChunkArray* cur =
            ::new (&_survivor_plab_array[i]) ChunkArray(vec,
                                                        max_plab_samples);
          assert(cur->end() == 0, "Should be 0");
          assert(cur->array() == vec, "Should be vec");
          assert(cur->capacity() == max_plab_samples, "Error");
        }
      }
    }
  }
  assert(   (   _survivor_plab_array  != NULL
             && _survivor_chunk_array != NULL)
         || (   _survivor_chunk_capacity == 0
             && _survivor_chunk_index == 0),
         "Error");

  // Choose what strong roots should be scanned depending on verification options
  // and perm gen collection mode.
  if (!CMSClassUnloadingEnabled) {
    // If class unloading is disabled we want to include all classes into the root set.
    add_root_scanning_option(SharedHeap::SO_AllClasses);
  } else {
    add_root_scanning_option(SharedHeap::SO_SystemClasses);
  }

  NOT_PRODUCT(_overflow_counter = CMSMarkStackOverflowInterval;)
  _gc_counters = new CollectorCounters("CMS", 1);
  _completed_initialization = true;
  _inter_sweep_timer.start();  // start of time
#ifdef SPARC
  // Issue a stern warning, but allow use for experimentation and debugging.
  if (VM_Version::is_sun4v() && UseMemSetInBOT) {
    assert(!FLAG_IS_DEFAULT(UseMemSetInBOT), "Error");
    warning("Experimental flag -XX:+UseMemSetInBOT is known to cause instability"
            " on sun4v; please understand that you are using at your own risk!");
  }
#endif
}

const char* ConcurrentMarkSweepGeneration::name() const {
  return "concurrent mark-sweep generation";
}
void ConcurrentMarkSweepGeneration::update_counters() {
  if (UsePerfData) {
    _space_counters->update_all();
    _gen_counters->update_all();
  }
}

// this is an optimized version of update_counters(). it takes the
// used value as a parameter rather than computing it.
//
void ConcurrentMarkSweepGeneration::update_counters(size_t used) {
  if (UsePerfData) {
    _space_counters->update_used(used);
    _space_counters->update_capacity();
    _gen_counters->update_all();
  }
}

void ConcurrentMarkSweepGeneration::print() const {
  Generation::print();
  cmsSpace()->print();
}

#ifndef PRODUCT
void ConcurrentMarkSweepGeneration::print_statistics() {
  cmsSpace()->printFLCensus(0);
}
#endif

void ConcurrentMarkSweepGeneration::printOccupancy(const char *s) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  if (PrintGCDetails) {
    if (Verbose) {
      gclog_or_tty->print(" [%d %s-%s: "SIZE_FORMAT"("SIZE_FORMAT")]",
        level(), short_name(), s, used(), capacity());
    } else {
      gclog_or_tty->print(" [%d %s-%s: "SIZE_FORMAT"K("SIZE_FORMAT"K)]",
        level(), short_name(), s, used() / K, capacity() / K);
    }
  }
  if (Verbose) {
    gclog_or_tty->print(" "SIZE_FORMAT"("SIZE_FORMAT")",
              gch->used(), gch->capacity());
  } else {
    gclog_or_tty->print(" "SIZE_FORMAT"K("SIZE_FORMAT"K)",
              gch->used() / K, gch->capacity() / K);
  }
}

size_t
ConcurrentMarkSweepGeneration::contiguous_available() const {
  // dld proposes an improvement in precision here. If the committed
  // part of the space ends in a free block we should add that to
  // uncommitted size in the calculation below. Will make this
  // change later, staying with the approximation below for the
  // time being. -- ysr.
  return MAX2(_virtual_space.uncommitted_size(), unsafe_max_alloc_nogc());
}

size_t
ConcurrentMarkSweepGeneration::unsafe_max_alloc_nogc() const {
  return _cmsSpace->max_alloc_in_words() * HeapWordSize;
}

size_t ConcurrentMarkSweepGeneration::max_available() const {
  return free() + _virtual_space.uncommitted_size();
}

bool ConcurrentMarkSweepGeneration::promotion_attempt_is_safe(
    size_t max_promotion_in_bytes,
    bool younger_handles_promotion_failure) const {

  // This is the most conservative test.  Full promotion is
  // guaranteed if this is used. The multiplicative factor is to
  // account for the worst case "dilatation".
  double adjusted_max_promo_bytes = _dilatation_factor * max_promotion_in_bytes;
  if (adjusted_max_promo_bytes > (double)max_uintx) { // larger than size_t
    adjusted_max_promo_bytes = (double)max_uintx;
  }
  bool result = (max_contiguous_available() >= (size_t)adjusted_max_promo_bytes);

  if (younger_handles_promotion_failure && !result) {
    // Full promotion is not guaranteed because fragmentation
    // of the cms generation can prevent the full promotion.
    result = (max_available() >= (size_t)adjusted_max_promo_bytes);

    if (!result) {
      // With promotion failure handling the test for the ability
      // to support the promotion does not have to be guaranteed.
      // Use an average of the amount promoted.
      result = max_available() >= (size_t)
        gc_stats()->avg_promoted()->padded_average();
      if (PrintGC && Verbose && result) {
        gclog_or_tty->print_cr(
          "\nConcurrentMarkSweepGeneration::promotion_attempt_is_safe"
          " max_available: " SIZE_FORMAT
          " avg_promoted: " SIZE_FORMAT,
          max_available(), (size_t)
          gc_stats()->avg_promoted()->padded_average());
      }
    } else {
      if (PrintGC && Verbose) {
        gclog_or_tty->print_cr(
          "\nConcurrentMarkSweepGeneration::promotion_attempt_is_safe"
          " max_available: " SIZE_FORMAT
          " adj_max_promo_bytes: " SIZE_FORMAT,
          max_available(), (size_t)adjusted_max_promo_bytes);
      }
    }
  } else {
    if (PrintGC && Verbose) {
      gclog_or_tty->print_cr(
        "\nConcurrentMarkSweepGeneration::promotion_attempt_is_safe"
        " contiguous_available: " SIZE_FORMAT
        " adj_max_promo_bytes: " SIZE_FORMAT,
        max_contiguous_available(), (size_t)adjusted_max_promo_bytes);
    }
  }
  return result;
}

// At a promotion failure dump information on block layout in heap
// (cms old generation).
void ConcurrentMarkSweepGeneration::promotion_failure_occurred() {
  if (CMSDumpAtPromotionFailure) {
    cmsSpace()->dump_at_safepoint_with_locks(collector(), gclog_or_tty);
  }
}

CompactibleSpace*
ConcurrentMarkSweepGeneration::first_compaction_space() const {
  return _cmsSpace;
}

void ConcurrentMarkSweepGeneration::reset_after_compaction() {
  // Clear the promotion information.  These pointers can be adjusted
  // along with all the other pointers into the heap but
  // compaction is expected to be a rare event with
  // a heap using cms so don't do it without seeing the need.
  if (ParallelGCThreads > 0) {
    for (uint i = 0; i < ParallelGCThreads; i++) {
      _par_gc_thread_states[i]->promo.reset();
    }
  }
}

void ConcurrentMarkSweepGeneration::space_iterate(SpaceClosure* blk, bool usedOnly) {
  blk->do_space(_cmsSpace);
}

void ConcurrentMarkSweepGeneration::compute_new_size() {
  assert_locked_or_safepoint(Heap_lock);

  // If incremental collection failed, we just want to expand
  // to the limit.
  if (incremental_collection_failed()) {
    clear_incremental_collection_failed();
    grow_to_reserved();
    return;
  }

  size_t expand_bytes = 0;
  double free_percentage = ((double) free()) / capacity();
  double desired_free_percentage = (double) MinHeapFreeRatio / 100;
  double maximum_free_percentage = (double) MaxHeapFreeRatio / 100;

  // compute expansion delta needed for reaching desired free percentage
  if (free_percentage < desired_free_percentage) {
    size_t desired_capacity = (size_t)(used() / ((double) 1 - desired_free_percentage));
    assert(desired_capacity >= capacity(), "invalid expansion size");
    expand_bytes = MAX2(desired_capacity - capacity(), MinHeapDeltaBytes);
  }
  if (expand_bytes > 0) {
    if (PrintGCDetails && Verbose) {
      size_t desired_capacity = (size_t)(used() / ((double) 1 - desired_free_percentage));
      gclog_or_tty->print_cr("\nFrom compute_new_size: ");
      gclog_or_tty->print_cr("  Free fraction %f", free_percentage);
      gclog_or_tty->print_cr("  Desired free fraction %f",
        desired_free_percentage);
      gclog_or_tty->print_cr("  Maximum free fraction %f",
        maximum_free_percentage);
      gclog_or_tty->print_cr("  Capactiy "SIZE_FORMAT, capacity()/1000);
      gclog_or_tty->print_cr("  Desired capacity "SIZE_FORMAT,
        desired_capacity/1000);
      int prev_level = level() - 1;
      if (prev_level >= 0) {
        size_t prev_size = 0;
        GenCollectedHeap* gch = GenCollectedHeap::heap();
        Generation* prev_gen = gch->_gens[prev_level];
        prev_size = prev_gen->capacity();
          gclog_or_tty->print_cr("  Younger gen size "SIZE_FORMAT,
                                 prev_size/1000);
      }
      gclog_or_tty->print_cr("  unsafe_max_alloc_nogc "SIZE_FORMAT,
        unsafe_max_alloc_nogc()/1000);
      gclog_or_tty->print_cr("  contiguous available "SIZE_FORMAT,
        contiguous_available()/1000);
      gclog_or_tty->print_cr("  Expand by "SIZE_FORMAT" (bytes)",
        expand_bytes);
    }
    // safe if expansion fails
    expand(expand_bytes, 0, CMSExpansionCause::_satisfy_free_ratio);
    if (PrintGCDetails && Verbose) {
      gclog_or_tty->print_cr("  Expanded free fraction %f",
        ((double) free()) / capacity());
    }
  }
}

Mutex* ConcurrentMarkSweepGeneration::freelistLock() const {
  return cmsSpace()->freelistLock();
}

HeapWord* ConcurrentMarkSweepGeneration::allocate(size_t size,
                                                  bool   tlab) {
  CMSSynchronousYieldRequest yr;
  MutexLockerEx x(freelistLock(),
                  Mutex::_no_safepoint_check_flag);
  return have_lock_and_allocate(size, tlab);
}

HeapWord* ConcurrentMarkSweepGeneration::have_lock_and_allocate(size_t size,
                                                  bool   tlab) {
  assert_lock_strong(freelistLock());
  size_t adjustedSize = CompactibleFreeListSpace::adjustObjectSize(size);
  HeapWord* res = cmsSpace()->allocate(adjustedSize);
  // Allocate the object live (grey) if the background collector has
  // started marking. This is necessary because the marker may
  // have passed this address and consequently this object will
  // not otherwise be greyed and would be incorrectly swept up.
  // Note that if this object contains references, the writing
  // of those references will dirty the card containing this object
  // allowing the object to be blackened (and its references scanned)
  // either during a preclean phase or at the final checkpoint.
  if (res != NULL) {
    collector()->direct_allocated(res, adjustedSize);
    _direct_allocated_words += adjustedSize;
    // allocation counters
    NOT_PRODUCT(
      _numObjectsAllocated++;
      _numWordsAllocated += (int)adjustedSize;
    )
  }
  return res;
}

// In the case of direct allocation by mutators in a generation that
// is being concurrently collected, the object must be allocated
// live (grey) if the background collector has started marking.
// This is necessary because the marker may
// have passed this address and consequently this object will
// not otherwise be greyed and would be incorrectly swept up.
// Note that if this object contains references, the writing
// of those references will dirty the card containing this object
// allowing the object to be blackened (and its references scanned)
// either during a preclean phase or at the final checkpoint.
void CMSCollector::direct_allocated(HeapWord* start, size_t size) {
  assert(_markBitMap.covers(start, size), "Out of bounds");
  if (_collectorState >= Marking) {
    MutexLockerEx y(_markBitMap.lock(),
                    Mutex::_no_safepoint_check_flag);
    // [see comments preceding SweepClosure::do_blk() below for details]
    // 1. need to mark the object as live so it isn't collected
    // 2. need to mark the 2nd bit to indicate the object may be uninitialized
    // 3. need to mark the end of the object so sweeper can skip over it
    //    if it's uninitialized when the sweeper reaches it.
    _markBitMap.mark(start);          // object is live
    _markBitMap.mark(start + 1);      // object is potentially uninitialized?
    _markBitMap.mark(start + size - 1);
                                      // mark end of object
  }
  // check that oop looks uninitialized
  assert(oop(start)->klass_or_null() == NULL, "_klass should be NULL");
}

void CMSCollector::promoted(bool par, HeapWord* start,
                            bool is_obj_array, size_t obj_size) {
  assert(_markBitMap.covers(start), "Out of bounds");
  // See comment in direct_allocated() about when objects should
  // be allocated live.
  if (_collectorState >= Marking) {
    // we already hold the marking bit map lock, taken in
    // the prologue
    if (par) {
      _markBitMap.par_mark(start);
    } else {
      _markBitMap.mark(start);
    }
    // We don't need to mark the object as uninitialized (as
    // in direct_allocated above) because this is being done with the
    // world stopped and the object will be initialized by the
    // time the sweeper gets to look at it.
    assert(SafepointSynchronize::is_at_safepoint(),
           "expect promotion only at safepoints");

    if (_collectorState < Sweeping) {
      // Mark the appropriate cards in the modUnionTable, so that
      // this object gets scanned before the sweep. If this is
      // not done, CMS generation references in the object might
      // not get marked.
      // For the case of arrays, which are otherwise precisely
      // marked, we need to dirty the entire array, not just its head.
      if (is_obj_array) {
        // The [par_]mark_range() method expects mr.end() below to
        // be aligned to the granularity of a bit's representation
        // in the heap. In the case of the MUT below, that's a
        // card size.
        MemRegion mr(start,
                     (HeapWord*)round_to((intptr_t)(start + obj_size),
                        CardTableModRefBS::card_size /* bytes */));
        if (par) {
          _modUnionTable.par_mark_range(mr);
        } else {
          _modUnionTable.mark_range(mr);
        }
      } else {  // not an obj array; we can just mark the head
        if (par) {
          _modUnionTable.par_mark(start);
        } else {
          _modUnionTable.mark(start);
        }
      }
    }
  }
}

static inline size_t percent_of_space(Space* space, HeapWord* addr)
{
  size_t delta = pointer_delta(addr, space->bottom());
  return (size_t)(delta * 100.0 / (space->capacity() / HeapWordSize));
}

void CMSCollector::icms_update_allocation_limits()
{
  Generation* gen0 = GenCollectedHeap::heap()->get_gen(0);
  EdenSpace* eden = gen0->as_DefNewGeneration()->eden();

  const unsigned int duty_cycle = stats().icms_update_duty_cycle();
  if (CMSTraceIncrementalPacing) {
    stats().print();
  }

  assert(duty_cycle <= 100, "invalid duty cycle");
  if (duty_cycle != 0) {
    // The duty_cycle is a percentage between 0 and 100; convert to words and
    // then compute the offset from the endpoints of the space.
    size_t free_words = eden->free() / HeapWordSize;
    double free_words_dbl = (double)free_words;
    size_t duty_cycle_words = (size_t)(free_words_dbl * duty_cycle / 100.0);
    size_t offset_words = (free_words - duty_cycle_words) / 2;

    _icms_start_limit = eden->top() + offset_words;
    _icms_stop_limit = eden->end() - offset_words;

    // The limits may be adjusted (shifted to the right) by
    // CMSIncrementalOffset, to allow the application more mutator time after a
    // young gen gc (when all mutators were stopped) and before CMS starts and
    // takes away one or more cpus.
    if (CMSIncrementalOffset != 0) {
      double adjustment_dbl = free_words_dbl * CMSIncrementalOffset / 100.0;
      size_t adjustment = (size_t)adjustment_dbl;
      HeapWord* tmp_stop = _icms_stop_limit + adjustment;
      if (tmp_stop > _icms_stop_limit && tmp_stop < eden->end()) {
        _icms_start_limit += adjustment;
        _icms_stop_limit = tmp_stop;
      }
    }
  }
  if (duty_cycle == 0 || (_icms_start_limit == _icms_stop_limit)) {
    _icms_start_limit = _icms_stop_limit = eden->end();
  }

  // Install the new start limit.
  eden->set_soft_end(_icms_start_limit);

  if (CMSTraceIncrementalMode) {
    gclog_or_tty->print(" icms alloc limits:  "
                           PTR_FORMAT "," PTR_FORMAT
                           " (" SIZE_FORMAT "%%," SIZE_FORMAT "%%) ",
                           _icms_start_limit, _icms_stop_limit,
                           percent_of_space(eden, _icms_start_limit),
                           percent_of_space(eden, _icms_stop_limit));
    if (Verbose) {
      gclog_or_tty->print("eden:  ");
      eden->print_on(gclog_or_tty);
    }
  }
}

// Any changes here should try to maintain the invariant
// that if this method is called with _icms_start_limit
// and _icms_stop_limit both NULL, then it should return NULL
// and not notify the icms thread.
HeapWord*
CMSCollector::allocation_limit_reached(Space* space, HeapWord* top,
                                       size_t word_size)
{
  // A start_limit equal to end() means the duty cycle is 0, so treat that as a
  // nop.
  if (CMSIncrementalMode && _icms_start_limit != space->end()) {
    if (top <= _icms_start_limit) {
      if (CMSTraceIncrementalMode) {
        space->print_on(gclog_or_tty);
        gclog_or_tty->stamp();
        gclog_or_tty->print_cr(" start limit top=" PTR_FORMAT
                               ", new limit=" PTR_FORMAT
                               " (" SIZE_FORMAT "%%)",
                               top, _icms_stop_limit,
                               percent_of_space(space, _icms_stop_limit));
      }
      ConcurrentMarkSweepThread::start_icms();
      assert(top < _icms_stop_limit, "Tautology");
      if (word_size < pointer_delta(_icms_stop_limit, top)) {
        return _icms_stop_limit;
      }

      // The allocation will cross both the _start and _stop limits, so do the
      // stop notification also and return end().
      if (CMSTraceIncrementalMode) {
        space->print_on(gclog_or_tty);
        gclog_or_tty->stamp();
        gclog_or_tty->print_cr(" +stop limit top=" PTR_FORMAT
                               ", new limit=" PTR_FORMAT
                               " (" SIZE_FORMAT "%%)",
                               top, space->end(),
                               percent_of_space(space, space->end()));
      }
      ConcurrentMarkSweepThread::stop_icms();
      return space->end();
    }

    if (top <= _icms_stop_limit) {
      if (CMSTraceIncrementalMode) {
        space->print_on(gclog_or_tty);
        gclog_or_tty->stamp();
        gclog_or_tty->print_cr(" stop limit top=" PTR_FORMAT
                               ", new limit=" PTR_FORMAT
                               " (" SIZE_FORMAT "%%)",
                               top, space->end(),
                               percent_of_space(space, space->end()));
      }
      ConcurrentMarkSweepThread::stop_icms();
      return space->end();
    }

    if (CMSTraceIncrementalMode) {
      space->print_on(gclog_or_tty);
      gclog_or_tty->stamp();
      gclog_or_tty->print_cr(" end limit top=" PTR_FORMAT
                             ", new limit=" PTR_FORMAT,
                             top, NULL);
    }
  }

  return NULL;
}

oop ConcurrentMarkSweepGeneration::promote(oop obj, size_t obj_size) {
  assert(obj_size == (size_t)obj->size(), "bad obj_size passed in");
  // allocate, copy and if necessary update promoinfo --
  // delegate to underlying space.
  assert_lock_strong(freelistLock());

#ifndef PRODUCT
  if (Universe::heap()->promotion_should_fail()) {
    return NULL;
  }
#endif  // #ifndef PRODUCT

  oop res = _cmsSpace->promote(obj, obj_size);
  if (res == NULL) {
    // expand and retry
    size_t s = _cmsSpace->expansionSpaceRequired(obj_size);  // HeapWords
    expand(s*HeapWordSize, MinHeapDeltaBytes,
      CMSExpansionCause::_satisfy_promotion);
    // Since there's currently no next generation, we don't try to promote
    // into a more senior generation.
    assert(next_gen() == NULL, "assumption, based upon which no attempt "
                               "is made to pass on a possibly failing "
                               "promotion to next generation");
    res = _cmsSpace->promote(obj, obj_size);
  }
  if (res != NULL) {
    // See comment in allocate() about when objects should
    // be allocated live.
    assert(obj->is_oop(), "Will dereference klass pointer below");
    collector()->promoted(false,           // Not parallel
                          (HeapWord*)res, obj->is_objArray(), obj_size);
    // promotion counters
    NOT_PRODUCT(
      _numObjectsPromoted++;
      _numWordsPromoted +=
        (int)(CompactibleFreeListSpace::adjustObjectSize(obj->size()));
    )
  }
  return res;
}


HeapWord*
ConcurrentMarkSweepGeneration::allocation_limit_reached(Space* space,
                                             HeapWord* top,
                                             size_t word_sz)
{
  return collector()->allocation_limit_reached(space, top, word_sz);
}

// Things to support parallel young-gen collection.
oop
ConcurrentMarkSweepGeneration::par_promote(int thread_num,
                                           oop old, markOop m,
                                           size_t word_sz) {
#ifndef PRODUCT
  if (Universe::heap()->promotion_should_fail()) {
    return NULL;
  }
#endif  // #ifndef PRODUCT

  CMSParGCThreadState* ps = _par_gc_thread_states[thread_num];
  PromotionInfo* promoInfo = &ps->promo;
  // if we are tracking promotions, then first ensure space for
  // promotion (including spooling space for saving header if necessary).
  // then allocate and copy, then track promoted info if needed.
  // When tracking (see PromotionInfo::track()), the mark word may
  // be displaced and in this case restoration of the mark word
  // occurs in the (oop_since_save_marks_)iterate phase.
  if (promoInfo->tracking() && !promoInfo->ensure_spooling_space()) {
    // Out of space for allocating spooling buffers;
    // try expanding and allocating spooling buffers.
    if (!expand_and_ensure_spooling_space(promoInfo)) {
      return NULL;
    }
  }
  assert(promoInfo->has_spooling_space(), "Control point invariant");
  HeapWord* obj_ptr = ps->lab.alloc(word_sz);
  if (obj_ptr == NULL) {
     obj_ptr = expand_and_par_lab_allocate(ps, word_sz);
     if (obj_ptr == NULL) {
       return NULL;
     }
  }
  oop obj = oop(obj_ptr);
  assert(obj->klass_or_null() == NULL, "Object should be uninitialized here.");
  // Otherwise, copy the object.  Here we must be careful to insert the
  // klass pointer last, since this marks the block as an allocated object.
  // Except with compressed oops it's the mark word.
  HeapWord* old_ptr = (HeapWord*)old;
  if (word_sz > (size_t)oopDesc::header_size()) {
    Copy::aligned_disjoint_words(old_ptr + oopDesc::header_size(),
                                 obj_ptr + oopDesc::header_size(),
                                 word_sz - oopDesc::header_size());
  }

  if (UseCompressedOops) {
    // Copy gap missed by (aligned) header size calculation above
    obj->set_klass_gap(old->klass_gap());
  }

  // Restore the mark word copied above.
  obj->set_mark(m);

  // Now we can track the promoted object, if necessary.  We take care
  // to delay the transition from uninitialized to full object
  // (i.e., insertion of klass pointer) until after, so that it
  // atomically becomes a promoted object.
  if (promoInfo->tracking()) {
    promoInfo->track((PromotedObject*)obj, old->klass());
  }

  // Finally, install the klass pointer (this should be volatile).
  obj->set_klass(old->klass());

  assert(old->is_oop(), "Will dereference klass ptr below");
  collector()->promoted(true,          // parallel
                        obj_ptr, old->is_objArray(), word_sz);

  NOT_PRODUCT(
    Atomic::inc(&_numObjectsPromoted);
    Atomic::add((jint)CompactibleFreeListSpace::adjustObjectSize(obj->size()),
                &_numWordsPromoted);
  )

  return obj;
}

void
ConcurrentMarkSweepGeneration::
par_promote_alloc_undo(int thread_num,
                       HeapWord* obj, size_t word_sz) {
  // CMS does not support promotion undo.
  ShouldNotReachHere();
}

void
ConcurrentMarkSweepGeneration::
par_promote_alloc_done(int thread_num) {
  CMSParGCThreadState* ps = _par_gc_thread_states[thread_num];
  ps->lab.retire(thread_num);
}

void
ConcurrentMarkSweepGeneration::
par_oop_since_save_marks_iterate_done(int thread_num) {
  CMSParGCThreadState* ps = _par_gc_thread_states[thread_num];
  ParScanWithoutBarrierClosure* dummy_cl = NULL;
  ps->promo.promoted_oops_iterate_nv(dummy_cl);
}

// XXXPERM
bool ConcurrentMarkSweepGeneration::should_collect(bool   full,
                                                   size_t size,
                                                   bool   tlab)
{
  // We allow a STW collection only if a full
  // collection was requested.
  return full || should_allocate(size, tlab); // FIX ME !!!
  // This and promotion failure handling are connected at the
  // hip and should be fixed by untying them.
}

bool CMSCollector::shouldConcurrentCollect() {
  if (_full_gc_requested) {
    if (Verbose && PrintGCDetails) {
      gclog_or_tty->print_cr("CMSCollector: collect because of explicit "
                             " gc request (or gc_locker)");
    }
    return true;
  }

  // For debugging purposes, change the type of collection.
  // If the rotation is not on the concurrent collection
  // type, don't start a concurrent collection.
  NOT_PRODUCT(
    if (RotateCMSCollectionTypes &&
        (_cmsGen->debug_collection_type() !=
          ConcurrentMarkSweepGeneration::Concurrent_collection_type)) {
      assert(_cmsGen->debug_collection_type() !=
        ConcurrentMarkSweepGeneration::Unknown_collection_type,
        "Bad cms collection type");
      return false;
    }
  )

  FreelistLocker x(this);
  // ------------------------------------------------------------------
  // Print out lots of information which affects the initiation of
  // a collection.
  if (PrintCMSInitiationStatistics && stats().valid()) {
    gclog_or_tty->print("CMSCollector shouldConcurrentCollect: ");
    gclog_or_tty->stamp();
    gclog_or_tty->print_cr("");
    stats().print_on(gclog_or_tty);
    gclog_or_tty->print_cr("time_until_cms_gen_full %3.7f",
      stats().time_until_cms_gen_full());
    gclog_or_tty->print_cr("free="SIZE_FORMAT, _cmsGen->free());
    gclog_or_tty->print_cr("contiguous_available="SIZE_FORMAT,
                           _cmsGen->contiguous_available());
    gclog_or_tty->print_cr("promotion_rate=%g", stats().promotion_rate());
    gclog_or_tty->print_cr("cms_allocation_rate=%g", stats().cms_allocation_rate());
    gclog_or_tty->print_cr("occupancy=%3.7f", _cmsGen->occupancy());
    gclog_or_tty->print_cr("initiatingOccupancy=%3.7f", _cmsGen->initiating_occupancy());
    gclog_or_tty->print_cr("initiatingPermOccupancy=%3.7f", _permGen->initiating_occupancy());
  }
  // ------------------------------------------------------------------

  // If the estimated time to complete a cms collection (cms_duration())
  // is less than the estimated time remaining until the cms generation
  // is full, start a collection.
  if (!UseCMSInitiatingOccupancyOnly) {
    if (stats().valid()) {
      if (stats().time_until_cms_start() == 0.0) {
        return true;
      }
    } else {
      // We want to conservatively collect somewhat early in order
      // to try and "bootstrap" our CMS/promotion statistics;
      // this branch will not fire after the first successful CMS
      // collection because the stats should then be valid.
      if (_cmsGen->occupancy() >= _bootstrap_occupancy) {
        if (Verbose && PrintGCDetails) {
          gclog_or_tty->print_cr(
            " CMSCollector: collect for bootstrapping statistics:"
            " occupancy = %f, boot occupancy = %f", _cmsGen->occupancy(),
            _bootstrap_occupancy);
        }
        return true;
      }
    }
  }

  // Otherwise, we start a collection cycle if either the perm gen or
  // old gen want a collection cycle started. Each may use
  // an appropriate criterion for making this decision.
  // XXX We need to make sure that the gen expansion
  // criterion dovetails well with this. XXX NEED TO FIX THIS
  if (_cmsGen->should_concurrent_collect()) {
    if (Verbose && PrintGCDetails) {
      gclog_or_tty->print_cr("CMS old gen initiated");
    }
    return true;
  }

  // We start a collection if we believe an incremental collection may fail;
  // this is not likely to be productive in practice because it's probably too
  // late anyway.
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  assert(gch->collector_policy()->is_two_generation_policy(),
         "You may want to check the correctness of the following");
  if (gch->incremental_collection_will_fail()) {
    if (PrintGCDetails && Verbose) {
      gclog_or_tty->print("CMSCollector: collect because incremental collection will fail ");
    }
    return true;
  }

  if (CMSClassUnloadingEnabled && _permGen->should_concurrent_collect()) {
    bool res = update_should_unload_classes();
    if (res) {
      if (Verbose && PrintGCDetails) {
        gclog_or_tty->print_cr("CMS perm gen initiated");
      }
      return true;
    }
  }
  return false;
}

// Clear _expansion_cause fields of constituent generations
void CMSCollector::clear_expansion_cause() {
  _cmsGen->clear_expansion_cause();
  _permGen->clear_expansion_cause();
}

// We should be conservative in starting a collection cycle.  To
// start too eagerly runs the risk of collecting too often in the
// extreme.  To collect too rarely falls back on full collections,
// which works, even if not optimum in terms of concurrent work.
// As a work around for too eagerly collecting, use the flag
// UseCMSInitiatingOccupancyOnly.  This also has the advantage of
// giving the user an easily understandable way of controlling the
// collections.
// We want to start a new collection cycle if any of the following
// conditions hold:
// . our current occupancy exceeds the configured initiating occupancy
//   for this generation, or
// . we recently needed to expand this space and have not, since that
//   expansion, done a collection of this generation, or
// . the underlying space believes that it may be a good idea to initiate
//   a concurrent collection (this may be based on criteria such as the
//   following: the space uses linear allocation and linear allocation is
//   going to fail, or there is believed to be excessive fragmentation in
//   the generation, etc... or ...
// [.(currently done by CMSCollector::shouldConcurrentCollect() only for
//   the case of the old generation, not the perm generation; see CR 6543076):
//   we may be approaching a point at which allocation requests may fail because
//   we will be out of sufficient free space given allocation rate estimates.]
bool ConcurrentMarkSweepGeneration::should_concurrent_collect() const {

  assert_lock_strong(freelistLock());
  if (occupancy() > initiating_occupancy()) {
    if (PrintGCDetails && Verbose) {
      gclog_or_tty->print(" %s: collect because of occupancy %f / %f  ",
        short_name(), occupancy(), initiating_occupancy());
    }
    return true;
  }
  if (UseCMSInitiatingOccupancyOnly) {
    return false;
  }
  if (expansion_cause() == CMSExpansionCause::_satisfy_allocation) {
    if (PrintGCDetails && Verbose) {
      gclog_or_tty->print(" %s: collect because expanded for allocation ",
        short_name());
    }
    return true;
  }
  if (_cmsSpace->should_concurrent_collect()) {
    if (PrintGCDetails && Verbose) {
      gclog_or_tty->print(" %s: collect because cmsSpace says so ",
        short_name());
    }
    return true;
  }
  return false;
}

void ConcurrentMarkSweepGeneration::collect(bool   full,
                                            bool   clear_all_soft_refs,
                                            size_t size,
                                            bool   tlab)
{
  collector()->collect(full, clear_all_soft_refs, size, tlab);
}

void CMSCollector::collect(bool   full,
                           bool   clear_all_soft_refs,
                           size_t size,
                           bool   tlab)
{
  if (!UseCMSCollectionPassing && _collectorState > Idling) {
    // For debugging purposes skip the collection if the state
    // is not currently idle
    if (TraceCMSState) {
      gclog_or_tty->print_cr("Thread " INTPTR_FORMAT " skipped full:%d CMS state %d",
        Thread::current(), full, _collectorState);
    }
    return;
  }

  // The following "if" branch is present for defensive reasons.
  // In the current uses of this interface, it can be replaced with:
  // assert(!GC_locker.is_active(), "Can't be called otherwise");
  // But I am not placing that assert here to allow future
  // generality in invoking this interface.
  if (GC_locker::is_active()) {
    // A consistency test for GC_locker
    assert(GC_locker::needs_gc(), "Should have been set already");
    // Skip this foreground collection, instead
    // expanding the heap if necessary.
    // Need the free list locks for the call to free() in compute_new_size()
    compute_new_size();
    return;
  }
  acquire_control_and_collect(full, clear_all_soft_refs);
  _full_gcs_since_conc_gc++;

}

void CMSCollector::request_full_gc(unsigned int full_gc_count) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  unsigned int gc_count = gch->total_full_collections();
  if (gc_count == full_gc_count) {
    MutexLockerEx y(CGC_lock, Mutex::_no_safepoint_check_flag);
    _full_gc_requested = true;
    CGC_lock->notify();   // nudge CMS thread
  }
}


// The foreground and background collectors need to coordinate in order
// to make sure that they do not mutually interfere with CMS collections.
// When a background collection is active,
// the foreground collector may need to take over (preempt) and
// synchronously complete an ongoing collection. Depending on the
// frequency of the background collections and the heap usage
// of the application, this preemption can be seldom or frequent.
// There are only certain
// points in the background collection that the "collection-baton"
// can be passed to the foreground collector.
//
// The foreground collector will wait for the baton before
// starting any part of the collection.  The foreground collector
// will only wait at one location.
//
// The background collector will yield the baton before starting a new
// phase of the collection (e.g., before initial marking, marking from roots,
// precleaning, final re-mark, sweep etc.)  This is normally done at the head
// of the loop which switches the phases. The background collector does some
// of the phases (initial mark, final re-mark) with the world stopped.
// Because of locking involved in stopping the world,
// the foreground collector should not block waiting for the background
// collector when it is doing a stop-the-world phase.  The background
// collector will yield the baton at an additional point just before
// it enters a stop-the-world phase.  Once the world is stopped, the
// background collector checks the phase of the collection.  If the
// phase has not changed, it proceeds with the collection.  If the
// phase has changed, it skips that phase of the collection.  See
// the comments on the use of the Heap_lock in collect_in_background().
//
// Variable used in baton passing.
//   _foregroundGCIsActive - Set to true by the foreground collector when
//      it wants the baton.  The foreground clears it when it has finished
//      the collection.
//   _foregroundGCShouldWait - Set to true by the background collector
//        when it is running.  The foreground collector waits while
//      _foregroundGCShouldWait is true.
//  CGC_lock - monitor used to protect access to the above variables
//      and to notify the foreground and background collectors.
//  _collectorState - current state of the CMS collection.
//
// The foreground collector
//   acquires the CGC_lock
//   sets _foregroundGCIsActive
//   waits on the CGC_lock for _foregroundGCShouldWait to be false
//     various locks acquired in preparation for the collection
//     are released so as not to block the background collector
//     that is in the midst of a collection
//   proceeds with the collection
//   clears _foregroundGCIsActive
//   returns
//
// The background collector in a loop iterating on the phases of the
//      collection
//   acquires the CGC_lock
//   sets _foregroundGCShouldWait
//   if _foregroundGCIsActive is set
//     clears _foregroundGCShouldWait, notifies _CGC_lock
//     waits on _CGC_lock for _foregroundGCIsActive to become false
//     and exits the loop.
//   otherwise
//     proceed with that phase of the collection
//     if the phase is a stop-the-world phase,
//       yield the baton once more just before enqueueing
//       the stop-world CMS operation (executed by the VM thread).
//   returns after all phases of the collection are done
//

void CMSCollector::acquire_control_and_collect(bool full,
        bool clear_all_soft_refs) {
  assert(SafepointSynchronize::is_at_safepoint(), "should be at safepoint");
  assert(!Thread::current()->is_ConcurrentGC_thread(),
         "shouldn't try to acquire control from self!");

  // Start the protocol for acquiring control of the
  // collection from the background collector (aka CMS thread).
  assert(ConcurrentMarkSweepThread::vm_thread_has_cms_token(),
         "VM thread should have CMS token");
  // Remember the possibly interrupted state of an ongoing
  // concurrent collection
  CollectorState first_state = _collectorState;

  // Signal to a possibly ongoing concurrent collection that
  // we want to do a foreground collection.
  _foregroundGCIsActive = true;

  // Disable incremental mode during a foreground collection.
  ICMSDisabler icms_disabler;

  // release locks and wait for a notify from the background collector
  // releasing the locks in only necessary for phases which
  // do yields to improve the granularity of the collection.
  assert_lock_strong(bitMapLock());
  // We need to lock the Free list lock for the space that we are
  // currently collecting.
  assert(haveFreelistLocks(), "Must be holding free list locks");
  bitMapLock()->unlock();
  releaseFreelistLocks();
  {
    MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
    if (_foregroundGCShouldWait) {
      // We are going to be waiting for action for the CMS thread;
      // it had better not be gone (for instance at shutdown)!
      assert(ConcurrentMarkSweepThread::cmst() != NULL,
             "CMS thread must be running");
      // Wait here until the background collector gives us the go-ahead
      ConcurrentMarkSweepThread::clear_CMS_flag(
        ConcurrentMarkSweepThread::CMS_vm_has_token);  // release token
      // Get a possibly blocked CMS thread going:
      //   Note that we set _foregroundGCIsActive true above,
      //   without protection of the CGC_lock.
      CGC_lock->notify();
      assert(!ConcurrentMarkSweepThread::vm_thread_wants_cms_token(),
             "Possible deadlock");
      while (_foregroundGCShouldWait) {
        // wait for notification
        CGC_lock->wait(Mutex::_no_safepoint_check_flag);
        // Possibility of delay/starvation here, since CMS token does
        // not know to give priority to VM thread? Actually, i think
        // there wouldn't be any delay/starvation, but the proof of
        // that "fact" (?) appears non-trivial. XXX 20011219YSR
      }
      ConcurrentMarkSweepThread::set_CMS_flag(
        ConcurrentMarkSweepThread::CMS_vm_has_token);
    }
  }
  // The CMS_token is already held.  Get back the other locks.
  assert(ConcurrentMarkSweepThread::vm_thread_has_cms_token(),
         "VM thread should have CMS token");
  getFreelistLocks();
  bitMapLock()->lock_without_safepoint_check();
  if (TraceCMSState) {
    gclog_or_tty->print_cr("CMS foreground collector has asked for control "
      INTPTR_FORMAT " with first state %d", Thread::current(), first_state);
    gclog_or_tty->print_cr("    gets control with state %d", _collectorState);
  }

  // Check if we need to do a compaction, or if not, whether
  // we need to start the mark-sweep from scratch.
  bool should_compact    = false;
  bool should_start_over = false;
  decide_foreground_collection_type(clear_all_soft_refs,
    &should_compact, &should_start_over);

NOT_PRODUCT(
  if (RotateCMSCollectionTypes) {
    if (_cmsGen->debug_collection_type() ==
        ConcurrentMarkSweepGeneration::MSC_foreground_collection_type) {
      should_compact = true;
    } else if (_cmsGen->debug_collection_type() ==
               ConcurrentMarkSweepGeneration::MS_foreground_collection_type) {
      should_compact = false;
    }
  }
)

  if (PrintGCDetails && first_state > Idling) {
    GCCause::Cause cause = GenCollectedHeap::heap()->gc_cause();
    if (GCCause::is_user_requested_gc(cause) ||
        GCCause::is_serviceability_requested_gc(cause)) {
      gclog_or_tty->print(" (concurrent mode interrupted)");
    } else {
      gclog_or_tty->print(" (concurrent mode failure)");
    }
  }

  if (should_compact) {
    // If the collection is being acquired from the background
    // collector, there may be references on the discovered
    // references lists that have NULL referents (being those
    // that were concurrently cleared by a mutator) or
    // that are no longer active (having been enqueued concurrently
    // by the mutator).
    // Scrub the list of those references because Mark-Sweep-Compact
    // code assumes referents are not NULL and that all discovered
    // Reference objects are active.
    ref_processor()->clean_up_discovered_references();

    do_compaction_work(clear_all_soft_refs);

    // Has the GC time limit been exceeded?
    DefNewGeneration* young_gen = _young_gen->as_DefNewGeneration();
    size_t max_eden_size = young_gen->max_capacity() -
                           young_gen->to()->capacity() -
                           young_gen->from()->capacity();
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    GCCause::Cause gc_cause = gch->gc_cause();
    size_policy()->check_gc_overhead_limit(_young_gen->used(),
                                           young_gen->eden()->used(),
                                           _cmsGen->max_capacity(),
                                           max_eden_size,
                                           full,
                                           gc_cause,
                                           gch->collector_policy());
  } else {
    do_mark_sweep_work(clear_all_soft_refs, first_state,
      should_start_over);
  }
  // Reset the expansion cause, now that we just completed
  // a collection cycle.
  clear_expansion_cause();
  _foregroundGCIsActive = false;
  return;
}

// Resize the perm generation and the tenured generation
// after obtaining the free list locks for the
// two generations.
void CMSCollector::compute_new_size() {
  assert_locked_or_safepoint(Heap_lock);
  FreelistLocker z(this);
  _permGen->compute_new_size();
  _cmsGen->compute_new_size();
}

// A work method used by foreground collection to determine
// what type of collection (compacting or not, continuing or fresh)
// it should do.
// NOTE: the intent is to make UseCMSCompactAtFullCollection
// and CMSCompactWhenClearAllSoftRefs the default in the future
// and do away with the flags after a suitable period.
void CMSCollector::decide_foreground_collection_type(
  bool clear_all_soft_refs, bool* should_compact,
  bool* should_start_over) {
  // Normally, we'll compact only if the UseCMSCompactAtFullCollection
  // flag is set, and we have either requested a System.gc() or
  // the number of full gc's since the last concurrent cycle
  // has exceeded the threshold set by CMSFullGCsBeforeCompaction,
  // or if an incremental collection has failed
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  assert(gch->collector_policy()->is_two_generation_policy(),
         "You may want to check the correctness of the following");
  // Inform cms gen if this was due to partial collection failing.
  // The CMS gen may use this fact to determine its expansion policy.
  if (gch->incremental_collection_will_fail()) {
    assert(!_cmsGen->incremental_collection_failed(),
           "Should have been noticed, reacted to and cleared");
    _cmsGen->set_incremental_collection_failed();
  }
  *should_compact =
    UseCMSCompactAtFullCollection &&
    ((_full_gcs_since_conc_gc >= CMSFullGCsBeforeCompaction) ||
     GCCause::is_user_requested_gc(gch->gc_cause()) ||
     gch->incremental_collection_will_fail());
  *should_start_over = false;
  if (clear_all_soft_refs && !*should_compact) {
    // We are about to do a last ditch collection attempt
    // so it would normally make sense to do a compaction
    // to reclaim as much space as possible.
    if (CMSCompactWhenClearAllSoftRefs) {
      // Default: The rationale is that in this case either
      // we are past the final marking phase, in which case
      // we'd have to start over, or so little has been done
      // that there's little point in saving that work. Compaction
      // appears to be the sensible choice in either case.
      *should_compact = true;
    } else {
      // We have been asked to clear all soft refs, but not to
      // compact. Make sure that we aren't past the final checkpoint
      // phase, for that is where we process soft refs. If we are already
      // past that phase, we'll need to redo the refs discovery phase and
      // if necessary clear soft refs that weren't previously
      // cleared. We do so by remembering the phase in which
      // we came in, and if we are past the refs processing
      // phase, we'll choose to just redo the mark-sweep
      // collection from scratch.
      if (_collectorState > FinalMarking) {
        // We are past the refs processing phase;
        // start over and do a fresh synchronous CMS cycle
        _collectorState = Resetting; // skip to reset to start new cycle
        reset(false /* == !asynch */);
        *should_start_over = true;
      } // else we can continue a possibly ongoing current cycle
    }
  }
}

// A work method used by the foreground collector to do
// a mark-sweep-compact.
void CMSCollector::do_compaction_work(bool clear_all_soft_refs) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  TraceTime t("CMS:MSC ", PrintGCDetails && Verbose, true, gclog_or_tty);
  if (PrintGC && Verbose && !(GCCause::is_user_requested_gc(gch->gc_cause()))) {
    gclog_or_tty->print_cr("Compact ConcurrentMarkSweepGeneration after %d "
      "collections passed to foreground collector", _full_gcs_since_conc_gc);
  }

  // Sample collection interval time and reset for collection pause.
  if (UseAdaptiveSizePolicy) {
    size_policy()->msc_collection_begin();
  }

  // Temporarily widen the span of the weak reference processing to
  // the entire heap.
  MemRegion new_span(GenCollectedHeap::heap()->reserved_region());
  ReferenceProcessorSpanMutator x(ref_processor(), new_span);

  // Temporarily, clear the "is_alive_non_header" field of the
  // reference processor.
  ReferenceProcessorIsAliveMutator y(ref_processor(), NULL);

  // Temporarily make reference _processing_ single threaded (non-MT).
  ReferenceProcessorMTProcMutator z(ref_processor(), false);

  // Temporarily make refs discovery atomic
  ReferenceProcessorAtomicMutator w(ref_processor(), true);

  ref_processor()->set_enqueuing_is_done(false);
  ref_processor()->enable_discovery();
  ref_processor()->setup_policy(clear_all_soft_refs);
  // If an asynchronous collection finishes, the _modUnionTable is
  // all clear.  If we are assuming the collection from an asynchronous
  // collection, clear the _modUnionTable.
  assert(_collectorState != Idling || _modUnionTable.isAllClear(),
    "_modUnionTable should be clear if the baton was not passed");
  _modUnionTable.clear_all();

  // We must adjust the allocation statistics being maintained
  // in the free list space. We do so by reading and clearing
  // the sweep timer and updating the block flux rate estimates below.
  assert(!_intra_sweep_timer.is_active(), "_intra_sweep_timer should be inactive");
  if (_inter_sweep_timer.is_active()) {
    _inter_sweep_timer.stop();
    // Note that we do not use this sample to update the _inter_sweep_estimate.
    _cmsGen->cmsSpace()->beginSweepFLCensus((float)(_inter_sweep_timer.seconds()),
                                            _inter_sweep_estimate.padded_average(),
                                            _intra_sweep_estimate.padded_average());
  }

  {
    TraceCMSMemoryManagerStats();
  }
  GenMarkSweep::invoke_at_safepoint(_cmsGen->level(),
    ref_processor(), clear_all_soft_refs);
  #ifdef ASSERT
    CompactibleFreeListSpace* cms_space = _cmsGen->cmsSpace();
    size_t free_size = cms_space->free();
    assert(free_size ==
           pointer_delta(cms_space->end(), cms_space->compaction_top())
           * HeapWordSize,
      "All the free space should be compacted into one chunk at top");
    assert(cms_space->dictionary()->totalChunkSize(
                                      debug_only(cms_space->freelistLock())) == 0 ||
           cms_space->totalSizeInIndexedFreeLists() == 0,
      "All the free space should be in a single chunk");
    size_t num = cms_space->totalCount();
    assert((free_size == 0 && num == 0) ||
           (free_size > 0  && (num == 1 || num == 2)),
         "There should be at most 2 free chunks after compaction");
  #endif // ASSERT
  _collectorState = Resetting;
  assert(_restart_addr == NULL,
         "Should have been NULL'd before baton was passed");
  reset(false /* == !asynch */);
  _cmsGen->reset_after_compaction();
  _concurrent_cycles_since_last_unload = 0;

  if (verifying() && !should_unload_classes()) {
    perm_gen_verify_bit_map()->clear_all();
  }

  // Clear any data recorded in the PLAB chunk arrays.
  if (_survivor_plab_array != NULL) {
    reset_survivor_plab_arrays();
  }

  // Adjust the per-size allocation stats for the next epoch.
  _cmsGen->cmsSpace()->endSweepFLCensus(sweep_count() /* fake */);
  // Restart the "inter sweep timer" for the next epoch.
  _inter_sweep_timer.reset();
  _inter_sweep_timer.start();

  // Sample collection pause time and reset for collection interval.
  if (UseAdaptiveSizePolicy) {
    size_policy()->msc_collection_end(gch->gc_cause());
  }

  // For a mark-sweep-compact, compute_new_size() will be called
  // in the heap's do_collection() method.
}

// A work method used by the foreground collector to do
// a mark-sweep, after taking over from a possibly on-going
// concurrent mark-sweep collection.
void CMSCollector::do_mark_sweep_work(bool clear_all_soft_refs,
  CollectorState first_state, bool should_start_over) {
  if (PrintGC && Verbose) {
    gclog_or_tty->print_cr("Pass concurrent collection to foreground "
      "collector with count %d",
      _full_gcs_since_conc_gc);
  }
  switch (_collectorState) {
    case Idling:
      if (first_state == Idling || should_start_over) {
        // The background GC was not active, or should
        // restarted from scratch;  start the cycle.
        _collectorState = InitialMarking;
      }
      // If first_state was not Idling, then a background GC
      // was in progress and has now finished.  No need to do it
      // again.  Leave the state as Idling.
      break;
    case Precleaning:
      // In the foreground case don't do the precleaning since
      // it is not done concurrently and there is extra work
      // required.
      _collectorState = FinalMarking;
  }
  if (PrintGCDetails &&
      (_collectorState > Idling ||
       !GCCause::is_user_requested_gc(GenCollectedHeap::heap()->gc_cause()))) {
    gclog_or_tty->print(" (concurrent mode failure)");
  }
  collect_in_foreground(clear_all_soft_refs);

  // For a mark-sweep, compute_new_size() will be called
  // in the heap's do_collection() method.
}


void CMSCollector::getFreelistLocks() const {
  // Get locks for all free lists in all generations that this
  // collector is responsible for
  _cmsGen->freelistLock()->lock_without_safepoint_check();
  _permGen->freelistLock()->lock_without_safepoint_check();
}

void CMSCollector::releaseFreelistLocks() const {
  // Release locks for all free lists in all generations that this
  // collector is responsible for
  _cmsGen->freelistLock()->unlock();
  _permGen->freelistLock()->unlock();
}

bool CMSCollector::haveFreelistLocks() const {
  // Check locks for all free lists in all generations that this
  // collector is responsible for
  assert_lock_strong(_cmsGen->freelistLock());
  assert_lock_strong(_permGen->freelistLock());
  PRODUCT_ONLY(ShouldNotReachHere());
  return true;
}

// A utility class that is used by the CMS collector to
// temporarily "release" the foreground collector from its
// usual obligation to wait for the background collector to
// complete an ongoing phase before proceeding.
class ReleaseForegroundGC: public StackObj {
 private:
  CMSCollector* _c;
 public:
  ReleaseForegroundGC(CMSCollector* c) : _c(c) {
    assert(_c->_foregroundGCShouldWait, "Else should not need to call");
    MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
    // allow a potentially blocked foreground collector to proceed
    _c->_foregroundGCShouldWait = false;
    if (_c->_foregroundGCIsActive) {
      CGC_lock->notify();
    }
    assert(!ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
           "Possible deadlock");
  }

  ~ReleaseForegroundGC() {
    assert(!_c->_foregroundGCShouldWait, "Usage protocol violation?");
    MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
    _c->_foregroundGCShouldWait = true;
  }
};

// There are separate collect_in_background and collect_in_foreground because of
// the different locking requirements of the background collector and the
// foreground collector.  There was originally an attempt to share
// one "collect" method between the background collector and the foreground
// collector but the if-then-else required made it cleaner to have
// separate methods.
void CMSCollector::collect_in_background(bool clear_all_soft_refs) {
  assert(Thread::current()->is_ConcurrentGC_thread(),
    "A CMS asynchronous collection is only allowed on a CMS thread.");

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  {
    bool safepoint_check = Mutex::_no_safepoint_check_flag;
    MutexLockerEx hl(Heap_lock, safepoint_check);
    FreelistLocker fll(this);
    MutexLockerEx x(CGC_lock, safepoint_check);
    if (_foregroundGCIsActive || !UseAsyncConcMarkSweepGC) {
      // The foreground collector is active or we're
      // not using asynchronous collections.  Skip this
      // background collection.
      assert(!_foregroundGCShouldWait, "Should be clear");
      return;
    } else {
      assert(_collectorState == Idling, "Should be idling before start.");
      _collectorState = InitialMarking;
      // Reset the expansion cause, now that we are about to begin
      // a new cycle.
      clear_expansion_cause();
    }
    // Decide if we want to enable class unloading as part of the
    // ensuing concurrent GC cycle.
    update_should_unload_classes();
    _full_gc_requested = false;           // acks all outstanding full gc requests
    // Signal that we are about to start a collection
    gch->increment_total_full_collections();  // ... starting a collection cycle
    _collection_count_start = gch->total_full_collections();
  }

  // Used for PrintGC
  size_t prev_used;
  if (PrintGC && Verbose) {
    prev_used = _cmsGen->used(); // XXXPERM
  }

  // The change of the collection state is normally done at this level;
  // the exceptions are phases that are executed while the world is
  // stopped.  For those phases the change of state is done while the
  // world is stopped.  For baton passing purposes this allows the
  // background collector to finish the phase and change state atomically.
  // The foreground collector cannot wait on a phase that is done
  // while the world is stopped because the foreground collector already
  // has the world stopped and would deadlock.
  while (_collectorState != Idling) {
    if (TraceCMSState) {
      gclog_or_tty->print_cr("Thread " INTPTR_FORMAT " in CMS state %d",
        Thread::current(), _collectorState);
    }
    // The foreground collector
    //   holds the Heap_lock throughout its collection.
    //   holds the CMS token (but not the lock)
    //     except while it is waiting for the background collector to yield.
    //
    // The foreground collector should be blocked (not for long)
    //   if the background collector is about to start a phase
    //   executed with world stopped.  If the background
    //   collector has already started such a phase, the
    //   foreground collector is blocked waiting for the
    //   Heap_lock.  The stop-world phases (InitialMarking and FinalMarking)
    //   are executed in the VM thread.
    //
    // The locking order is
    //   PendingListLock (PLL)  -- if applicable (FinalMarking)
    //   Heap_lock  (both this & PLL locked in VM_CMS_Operation::prologue())
    //   CMS token  (claimed in
    //                stop_world_and_do() -->
    //                  safepoint_synchronize() -->
    //                    CMSThread::synchronize())

    {
      // Check if the FG collector wants us to yield.
      CMSTokenSync x(true); // is cms thread
      if (waitForForegroundGC()) {
        // We yielded to a foreground GC, nothing more to be
        // done this round.
        assert(_foregroundGCShouldWait == false, "We set it to false in "
               "waitForForegroundGC()");
        if (TraceCMSState) {
          gclog_or_tty->print_cr("CMS Thread " INTPTR_FORMAT
            " exiting collection CMS state %d",
            Thread::current(), _collectorState);
        }
        return;
      } else {
        // The background collector can run but check to see if the
        // foreground collector has done a collection while the
        // background collector was waiting to get the CGC_lock
        // above.  If yes, break so that _foregroundGCShouldWait
        // is cleared before returning.
        if (_collectorState == Idling) {
          break;
        }
      }
    }

    assert(_foregroundGCShouldWait, "Foreground collector, if active, "
      "should be waiting");

    switch (_collectorState) {
      case InitialMarking:
        {
          ReleaseForegroundGC x(this);
          stats().record_cms_begin();

          VM_CMS_Initial_Mark initial_mark_op(this);
          VMThread::execute(&initial_mark_op);
        }
        // The collector state may be any legal state at this point
        // since the background collector may have yielded to the
        // foreground collector.
        break;
      case Marking:
        // initial marking in checkpointRootsInitialWork has been completed
        if (markFromRoots(true)) { // we were successful
          assert(_collectorState == Precleaning, "Collector state should "
            "have changed");
        } else {
          assert(_foregroundGCIsActive, "Internal state inconsistency");
        }
        break;
      case Precleaning:
        if (UseAdaptiveSizePolicy) {
          size_policy()->concurrent_precleaning_begin();
        }
        // marking from roots in markFromRoots has been completed
        preclean();
        if (UseAdaptiveSizePolicy) {
          size_policy()->concurrent_precleaning_end();
        }
        assert(_collectorState == AbortablePreclean ||
               _collectorState == FinalMarking,
               "Collector state should have changed");
        break;
      case AbortablePreclean:
        if (UseAdaptiveSizePolicy) {
        size_policy()->concurrent_phases_resume();
        }
        abortable_preclean();
        if (UseAdaptiveSizePolicy) {
          size_policy()->concurrent_precleaning_end();
        }
        assert(_collectorState == FinalMarking, "Collector state should "
          "have changed");
        break;
      case FinalMarking:
        {
          ReleaseForegroundGC x(this);

          VM_CMS_Final_Remark final_remark_op(this);
          VMThread::execute(&final_remark_op);
        }
        assert(_foregroundGCShouldWait, "block post-condition");
        break;
      case Sweeping:
        if (UseAdaptiveSizePolicy) {
          size_policy()->concurrent_sweeping_begin();
        }
        // final marking in checkpointRootsFinal has been completed
        sweep(true);
        assert(_collectorState == Resizing, "Collector state change "
          "to Resizing must be done under the free_list_lock");
        _full_gcs_since_conc_gc = 0;

        // Stop the timers for adaptive size policy for the concurrent phases
        if (UseAdaptiveSizePolicy) {
          size_policy()->concurrent_sweeping_end();
          size_policy()->concurrent_phases_end(gch->gc_cause(),
                                             gch->prev_gen(_cmsGen)->capacity(),
                                             _cmsGen->free());
        }

      case Resizing: {
        // Sweeping has been completed...
        // At this point the background collection has completed.
        // Don't move the call to compute_new_size() down
        // into code that might be executed if the background
        // collection was preempted.
        {
          ReleaseForegroundGC x(this);   // unblock FG collection
          MutexLockerEx       y(Heap_lock, Mutex::_no_safepoint_check_flag);
          CMSTokenSync        z(true);   // not strictly needed.
          if (_collectorState == Resizing) {
            compute_new_size();
            _collectorState = Resetting;
          } else {
            assert(_collectorState == Idling, "The state should only change"
                   " because the foreground collector has finished the collection");
          }
        }
        break;
      }
      case Resetting:
        // CMS heap resizing has been completed
        reset(true);
        assert(_collectorState == Idling, "Collector state should "
          "have changed");
        stats().record_cms_end();
        // Don't move the concurrent_phases_end() and compute_new_size()
        // calls to here because a preempted background collection
        // has it's state set to "Resetting".
        break;
      case Idling:
      default:
        ShouldNotReachHere();
        break;
    }
    if (TraceCMSState) {
      gclog_or_tty->print_cr("  Thread " INTPTR_FORMAT " done - next CMS state %d",
        Thread::current(), _collectorState);
    }
    assert(_foregroundGCShouldWait, "block post-condition");
  }

  // Should this be in gc_epilogue?
  collector_policy()->counters()->update_counters();

  {
    // Clear _foregroundGCShouldWait and, in the event that the
    // foreground collector is waiting, notify it, before
    // returning.
    MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
    _foregroundGCShouldWait = false;
    if (_foregroundGCIsActive) {
      CGC_lock->notify();
    }
    assert(!ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
           "Possible deadlock");
  }
  if (TraceCMSState) {
    gclog_or_tty->print_cr("CMS Thread " INTPTR_FORMAT
      " exiting collection CMS state %d",
      Thread::current(), _collectorState);
  }
  if (PrintGC && Verbose) {
    _cmsGen->print_heap_change(prev_used);
  }
}

void CMSCollector::collect_in_foreground(bool clear_all_soft_refs) {
  assert(_foregroundGCIsActive && !_foregroundGCShouldWait,
         "Foreground collector should be waiting, not executing");
  assert(Thread::current()->is_VM_thread(), "A foreground collection"
    "may only be done by the VM Thread with the world stopped");
  assert(ConcurrentMarkSweepThread::vm_thread_has_cms_token(),
         "VM thread should have CMS token");

  NOT_PRODUCT(TraceTime t("CMS:MS (foreground) ", PrintGCDetails && Verbose,
    true, gclog_or_tty);)
  if (UseAdaptiveSizePolicy) {
    size_policy()->ms_collection_begin();
  }
  COMPILER2_PRESENT(DerivedPointerTableDeactivate dpt_deact);

  HandleMark hm;  // Discard invalid handles created during verification

  if (VerifyBeforeGC &&
      GenCollectedHeap::heap()->total_collections() >= VerifyGCStartAt) {
    Universe::verify(true);
  }

  // Snapshot the soft reference policy to be used in this collection cycle.
  ref_processor()->setup_policy(clear_all_soft_refs);

  bool init_mark_was_synchronous = false; // until proven otherwise
  while (_collectorState != Idling) {
    if (TraceCMSState) {
      gclog_or_tty->print_cr("Thread " INTPTR_FORMAT " in CMS state %d",
        Thread::current(), _collectorState);
    }
    switch (_collectorState) {
      case InitialMarking:
        init_mark_was_synchronous = true;  // fact to be exploited in re-mark
        checkpointRootsInitial(false);
        assert(_collectorState == Marking, "Collector state should have changed"
          " within checkpointRootsInitial()");
        break;
      case Marking:
        // initial marking in checkpointRootsInitialWork has been completed
        if (VerifyDuringGC &&
            GenCollectedHeap::heap()->total_collections() >= VerifyGCStartAt) {
          gclog_or_tty->print("Verify before initial mark: ");
          Universe::verify(true);
        }
        {
          bool res = markFromRoots(false);
          assert(res && _collectorState == FinalMarking, "Collector state should "
            "have changed");
          break;
        }
      case FinalMarking:
        if (VerifyDuringGC &&
            GenCollectedHeap::heap()->total_collections() >= VerifyGCStartAt) {
          gclog_or_tty->print("Verify before re-mark: ");
          Universe::verify(true);
        }
        checkpointRootsFinal(false, clear_all_soft_refs,
                             init_mark_was_synchronous);
        assert(_collectorState == Sweeping, "Collector state should not "
          "have changed within checkpointRootsFinal()");
        break;
      case Sweeping:
        // final marking in checkpointRootsFinal has been completed
        if (VerifyDuringGC &&
            GenCollectedHeap::heap()->total_collections() >= VerifyGCStartAt) {
          gclog_or_tty->print("Verify before sweep: ");
          Universe::verify(true);
        }
        sweep(false);
        assert(_collectorState == Resizing, "Incorrect state");
        break;
      case Resizing: {
        // Sweeping has been completed; the actual resize in this case
        // is done separately; nothing to be done in this state.
        _collectorState = Resetting;
        break;
      }
      case Resetting:
        // The heap has been resized.
        if (VerifyDuringGC &&
            GenCollectedHeap::heap()->total_collections() >= VerifyGCStartAt) {
          gclog_or_tty->print("Verify before reset: ");
          Universe::verify(true);
        }
        reset(false);
        assert(_collectorState == Idling, "Collector state should "
          "have changed");
        break;
      case Precleaning:
      case AbortablePreclean:
        // Elide the preclean phase
        _collectorState = FinalMarking;
        break;
      default:
        ShouldNotReachHere();
    }
    if (TraceCMSState) {
      gclog_or_tty->print_cr("  Thread " INTPTR_FORMAT " done - next CMS state %d",
        Thread::current(), _collectorState);
    }
  }

  if (UseAdaptiveSizePolicy) {
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    size_policy()->ms_collection_end(gch->gc_cause());
  }

  if (VerifyAfterGC &&
      GenCollectedHeap::heap()->total_collections() >= VerifyGCStartAt) {
    Universe::verify(true);
  }
  if (TraceCMSState) {
    gclog_or_tty->print_cr("CMS Thread " INTPTR_FORMAT
      " exiting collection CMS state %d",
      Thread::current(), _collectorState);
  }
}

bool CMSCollector::waitForForegroundGC() {
  bool res = false;
  assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "CMS thread should have CMS token");
  // Block the foreground collector until the
  // background collectors decides whether to
  // yield.
  MutexLockerEx x(CGC_lock, Mutex::_no_safepoint_check_flag);
  _foregroundGCShouldWait = true;
  if (_foregroundGCIsActive) {
    // The background collector yields to the
    // foreground collector and returns a value
    // indicating that it has yielded.  The foreground
    // collector can proceed.
    res = true;
    _foregroundGCShouldWait = false;
    ConcurrentMarkSweepThread::clear_CMS_flag(
      ConcurrentMarkSweepThread::CMS_cms_has_token);
    ConcurrentMarkSweepThread::set_CMS_flag(
      ConcurrentMarkSweepThread::CMS_cms_wants_token);
    // Get a possibly blocked foreground thread going
    CGC_lock->notify();
    if (TraceCMSState) {
      gclog_or_tty->print_cr("CMS Thread " INTPTR_FORMAT " waiting at CMS state %d",
        Thread::current(), _collectorState);
    }
    while (_foregroundGCIsActive) {
      CGC_lock->wait(Mutex::_no_safepoint_check_flag);
    }
    ConcurrentMarkSweepThread::set_CMS_flag(
      ConcurrentMarkSweepThread::CMS_cms_has_token);
    ConcurrentMarkSweepThread::clear_CMS_flag(
      ConcurrentMarkSweepThread::CMS_cms_wants_token);
  }
  if (TraceCMSState) {
    gclog_or_tty->print_cr("CMS Thread " INTPTR_FORMAT " continuing at CMS state %d",
      Thread::current(), _collectorState);
  }
  return res;
}

// Because of the need to lock the free lists and other structures in
// the collector, common to all the generations that the collector is
// collecting, we need the gc_prologues of individual CMS generations
// delegate to their collector. It may have been simpler had the
// current infrastructure allowed one to call a prologue on a
// collector. In the absence of that we have the generation's
// prologue delegate to the collector, which delegates back
// some "local" work to a worker method in the individual generations
// that it's responsible for collecting, while itself doing any
// work common to all generations it's responsible for. A similar
// comment applies to the  gc_epilogue()'s.
// The role of the varaible _between_prologue_and_epilogue is to
// enforce the invocation protocol.
void CMSCollector::gc_prologue(bool full) {
  // Call gc_prologue_work() for each CMSGen and PermGen that
  // we are responsible for.

  // The following locking discipline assumes that we are only called
  // when the world is stopped.
  assert(SafepointSynchronize::is_at_safepoint(), "world is stopped assumption");

  // The CMSCollector prologue must call the gc_prologues for the
  // "generations" (including PermGen if any) that it's responsible
  // for.

  assert(   Thread::current()->is_VM_thread()
         || (   CMSScavengeBeforeRemark
             && Thread::current()->is_ConcurrentGC_thread()),
         "Incorrect thread type for prologue execution");

  if (_between_prologue_and_epilogue) {
    // We have already been invoked; this is a gc_prologue delegation
    // from yet another CMS generation that we are responsible for, just
    // ignore it since all relevant work has already been done.
    return;
  }

  // set a bit saying prologue has been called; cleared in epilogue
  _between_prologue_and_epilogue = true;
  // Claim locks for common data structures, then call gc_prologue_work()
  // for each CMSGen and PermGen that we are responsible for.

  getFreelistLocks();   // gets free list locks on constituent spaces
  bitMapLock()->lock_without_safepoint_check();

  // Should call gc_prologue_work() for all cms gens we are responsible for
  bool registerClosure =    _collectorState >= Marking
                         && _collectorState < Sweeping;
  ModUnionClosure* muc = ParallelGCThreads > 0 ? &_modUnionClosurePar
                                               : &_modUnionClosure;
  _cmsGen->gc_prologue_work(full, registerClosure, muc);
  _permGen->gc_prologue_work(full, registerClosure, muc);

  if (!full) {
    stats().record_gc0_begin();
  }
}

void ConcurrentMarkSweepGeneration::gc_prologue(bool full) {
  // Delegate to CMScollector which knows how to coordinate between
  // this and any other CMS generations that it is responsible for
  // collecting.
  collector()->gc_prologue(full);
}

// This is a "private" interface for use by this generation's CMSCollector.
// Not to be called directly by any other entity (for instance,
// GenCollectedHeap, which calls the "public" gc_prologue method above).
void ConcurrentMarkSweepGeneration::gc_prologue_work(bool full,
  bool registerClosure, ModUnionClosure* modUnionClosure) {
  assert(!incremental_collection_failed(), "Shouldn't be set yet");
  assert(cmsSpace()->preconsumptionDirtyCardClosure() == NULL,
    "Should be NULL");
  if (registerClosure) {
    cmsSpace()->setPreconsumptionDirtyCardClosure(modUnionClosure);
  }
  cmsSpace()->gc_prologue();
  // Clear stat counters
  NOT_PRODUCT(
    assert(_numObjectsPromoted == 0, "check");
    assert(_numWordsPromoted   == 0, "check");
    if (Verbose && PrintGC) {
      gclog_or_tty->print("Allocated "SIZE_FORMAT" objects, "
                          SIZE_FORMAT" bytes concurrently",
      _numObjectsAllocated, _numWordsAllocated*sizeof(HeapWord));
    }
    _numObjectsAllocated = 0;
    _numWordsAllocated   = 0;
  )
}

void CMSCollector::gc_epilogue(bool full) {
  // The following locking discipline assumes that we are only called
  // when the world is stopped.
  assert(SafepointSynchronize::is_at_safepoint(),
         "world is stopped assumption");

  // Currently the CMS epilogue (see CompactibleFreeListSpace) merely checks
  // if linear allocation blocks need to be appropriately marked to allow the
  // the blocks to be parsable. We also check here whether we need to nudge the
  // CMS collector thread to start a new cycle (if it's not already active).
  assert(   Thread::current()->is_VM_thread()
         || (   CMSScavengeBeforeRemark
             && Thread::current()->is_ConcurrentGC_thread()),
         "Incorrect thread type for epilogue execution");

  if (!_between_prologue_and_epilogue) {
    // We have already been invoked; this is a gc_epilogue delegation
    // from yet another CMS generation that we are responsible for, just
    // ignore it since all relevant work has already been done.
    return;
  }
  assert(haveFreelistLocks(), "must have freelist locks");
  assert_lock_strong(bitMapLock());

  _cmsGen->gc_epilogue_work(full);
  _permGen->gc_epilogue_work(full);

  if (_collectorState == AbortablePreclean || _collectorState == Precleaning) {
    // in case sampling was not already enabled, enable it
    _start_sampling = true;
  }
  // reset _eden_chunk_array so sampling starts afresh
  _eden_chunk_index = 0;

  size_t cms_used   = _cmsGen->cmsSpace()->used();
  size_t perm_used  = _permGen->cmsSpace()->used();

  // update performance counters - this uses a special version of
  // update_counters() that allows the utilization to be passed as a
  // parameter, avoiding multiple calls to used().
  //
  _cmsGen->update_counters(cms_used);
  _permGen->update_counters(perm_used);

  if (CMSIncrementalMode) {
    icms_update_allocation_limits();
  }

  bitMapLock()->unlock();
  releaseFreelistLocks();

  _between_prologue_and_epilogue = false;  // ready for next cycle
}

void ConcurrentMarkSweepGeneration::gc_epilogue(bool full) {
  collector()->gc_epilogue(full);

  // Also reset promotion tracking in par gc thread states.
  if (ParallelGCThreads > 0) {
    for (uint i = 0; i < ParallelGCThreads; i++) {
      _par_gc_thread_states[i]->promo.stopTrackingPromotions(i);
    }
  }
}

void ConcurrentMarkSweepGeneration::gc_epilogue_work(bool full) {
  assert(!incremental_collection_failed(), "Should have been cleared");
  cmsSpace()->setPreconsumptionDirtyCardClosure(NULL);
  cmsSpace()->gc_epilogue();
    // Print stat counters
  NOT_PRODUCT(
    assert(_numObjectsAllocated == 0, "check");
    assert(_numWordsAllocated == 0, "check");
    if (Verbose && PrintGC) {
      gclog_or_tty->print("Promoted "SIZE_FORMAT" objects, "
                          SIZE_FORMAT" bytes",
                 _numObjectsPromoted, _numWordsPromoted*sizeof(HeapWord));
    }
    _numObjectsPromoted = 0;
    _numWordsPromoted   = 0;
  )

  if (PrintGC && Verbose) {
    // Call down the chain in contiguous_available needs the freelistLock
    // so print this out before releasing the freeListLock.
    gclog_or_tty->print(" Contiguous available "SIZE_FORMAT" bytes ",
                        contiguous_available());
  }
}

#ifndef PRODUCT
bool CMSCollector::have_cms_token() {
  Thread* thr = Thread::current();
  if (thr->is_VM_thread()) {
    return ConcurrentMarkSweepThread::vm_thread_has_cms_token();
  } else if (thr->is_ConcurrentGC_thread()) {
    return ConcurrentMarkSweepThread::cms_thread_has_cms_token();
  } else if (thr->is_GC_task_thread()) {
    return ConcurrentMarkSweepThread::vm_thread_has_cms_token() &&
           ParGCRareEvent_lock->owned_by_self();
  }
  return false;
}
#endif

// Check reachability of the given heap address in CMS generation,
// treating all other generations as roots.
bool CMSCollector::is_cms_reachable(HeapWord* addr) {
  // We could "guarantee" below, rather than assert, but i'll
  // leave these as "asserts" so that an adventurous debugger
  // could try this in the product build provided some subset of
  // the conditions were met, provided they were intersted in the
  // results and knew that the computation below wouldn't interfere
  // with other concurrent computations mutating the structures
  // being read or written.
  assert(SafepointSynchronize::is_at_safepoint(),
         "Else mutations in object graph will make answer suspect");
  assert(have_cms_token(), "Should hold cms token");
  assert(haveFreelistLocks(), "must hold free list locks");
  assert_lock_strong(bitMapLock());

  // Clear the marking bit map array before starting, but, just
  // for kicks, first report if the given address is already marked
  gclog_or_tty->print_cr("Start: Address 0x%x is%s marked", addr,
                _markBitMap.isMarked(addr) ? "" : " not");

  if (verify_after_remark()) {
    MutexLockerEx x(verification_mark_bm()->lock(), Mutex::_no_safepoint_check_flag);
    bool result = verification_mark_bm()->isMarked(addr);
    gclog_or_tty->print_cr("TransitiveMark: Address 0x%x %s marked", addr,
                           result ? "IS" : "is NOT");
    return result;
  } else {
    gclog_or_tty->print_cr("Could not compute result");
    return false;
  }
}

////////////////////////////////////////////////////////
// CMS Verification Support
////////////////////////////////////////////////////////
// Following the remark phase, the following invariant
// should hold -- each object in the CMS heap which is
// marked in markBitMap() should be marked in the verification_mark_bm().

class VerifyMarkedClosure: public BitMapClosure {
  CMSBitMap* _marks;
  bool       _failed;

 public:
  VerifyMarkedClosure(CMSBitMap* bm): _marks(bm), _failed(false) {}

  bool do_bit(size_t offset) {
    HeapWord* addr = _marks->offsetToHeapWord(offset);
    if (!_marks->isMarked(addr)) {
      oop(addr)->print_on(gclog_or_tty);
      gclog_or_tty->print_cr(" ("INTPTR_FORMAT" should have been marked)", addr);
      _failed = true;
    }
    return true;
  }

  bool failed() { return _failed; }
};

bool CMSCollector::verify_after_remark() {
  gclog_or_tty->print(" [Verifying CMS Marking... ");
  MutexLockerEx ml(verification_mark_bm()->lock(), Mutex::_no_safepoint_check_flag);
  static bool init = false;

  assert(SafepointSynchronize::is_at_safepoint(),
         "Else mutations in object graph will make answer suspect");
  assert(have_cms_token(),
         "Else there may be mutual interference in use of "
         " verification data structures");
  assert(_collectorState > Marking && _collectorState <= Sweeping,
         "Else marking info checked here may be obsolete");
  assert(haveFreelistLocks(), "must hold free list locks");
  assert_lock_strong(bitMapLock());


  // Allocate marking bit map if not already allocated
  if (!init) { // first time
    if (!verification_mark_bm()->allocate(_span)) {
      return false;
    }
    init = true;
  }

  assert(verification_mark_stack()->isEmpty(), "Should be empty");

  // Turn off refs discovery -- so we will be tracing through refs.
  // This is as intended, because by this time
  // GC must already have cleared any refs that need to be cleared,
  // and traced those that need to be marked; moreover,
  // the marking done here is not going to intefere in any
  // way with the marking information used by GC.
  NoRefDiscovery no_discovery(ref_processor());

  COMPILER2_PRESENT(DerivedPointerTableDeactivate dpt_deact;)

  // Clear any marks from a previous round
  verification_mark_bm()->clear_all();
  assert(verification_mark_stack()->isEmpty(), "markStack should be empty");
  verify_work_stacks_empty();

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  gch->ensure_parsability(false);  // fill TLABs, but no need to retire them
  // Update the saved marks which may affect the root scans.
  gch->save_marks();

  if (CMSRemarkVerifyVariant == 1) {
    // In this first variant of verification, we complete
    // all marking, then check if the new marks-verctor is
    // a subset of the CMS marks-vector.
    verify_after_remark_work_1();
  } else if (CMSRemarkVerifyVariant == 2) {
    // In this second variant of verification, we flag an error
    // (i.e. an object reachable in the new marks-vector not reachable
    // in the CMS marks-vector) immediately, also indicating the
    // identify of an object (A) that references the unmarked object (B) --
    // presumably, a mutation to A failed to be picked up by preclean/remark?
    verify_after_remark_work_2();
  } else {
    warning("Unrecognized value %d for CMSRemarkVerifyVariant",
            CMSRemarkVerifyVariant);
  }
  gclog_or_tty->print(" done] ");
  return true;
}

void CMSCollector::verify_after_remark_work_1() {
  ResourceMark rm;
  HandleMark  hm;
  GenCollectedHeap* gch = GenCollectedHeap::heap();

  // Mark from roots one level into CMS
  MarkRefsIntoClosure notOlder(_span, verification_mark_bm());
  gch->rem_set()->prepare_for_younger_refs_iterate(false); // Not parallel.

  gch->gen_process_strong_roots(_cmsGen->level(),
                                true,   // younger gens are roots
                                true,   // activate StrongRootsScope
                                true,   // collecting perm gen
                                SharedHeap::ScanningOption(roots_scanning_options()),
                                &notOlder,
                                true,   // walk code active on stacks
                                NULL);

  // Now mark from the roots
  assert(_revisitStack.isEmpty(), "Should be empty");
  MarkFromRootsClosure markFromRootsClosure(this, _span,
    verification_mark_bm(), verification_mark_stack(), &_revisitStack,
    false /* don't yield */, true /* verifying */);
  assert(_restart_addr == NULL, "Expected pre-condition");
  verification_mark_bm()->iterate(&markFromRootsClosure);
  while (_restart_addr != NULL) {
    // Deal with stack overflow: by restarting at the indicated
    // address.
    HeapWord* ra = _restart_addr;
    markFromRootsClosure.reset(ra);
    _restart_addr = NULL;
    verification_mark_bm()->iterate(&markFromRootsClosure, ra, _span.end());
  }
  assert(verification_mark_stack()->isEmpty(), "Should have been drained");
  verify_work_stacks_empty();
  // Should reset the revisit stack above, since no class tree
  // surgery is forthcoming.
  _revisitStack.reset(); // throwing away all contents

  // Marking completed -- now verify that each bit marked in
  // verification_mark_bm() is also marked in markBitMap(); flag all
  // errors by printing corresponding objects.
  VerifyMarkedClosure vcl(markBitMap());
  verification_mark_bm()->iterate(&vcl);
  if (vcl.failed()) {
    gclog_or_tty->print("Verification failed");
    Universe::heap()->print_on(gclog_or_tty);
    fatal("CMS: failed marking verification after remark");
  }
}

void CMSCollector::verify_after_remark_work_2() {
  ResourceMark rm;
  HandleMark  hm;
  GenCollectedHeap* gch = GenCollectedHeap::heap();

  // Mark from roots one level into CMS
  MarkRefsIntoVerifyClosure notOlder(_span, verification_mark_bm(),
                                     markBitMap());
  gch->rem_set()->prepare_for_younger_refs_iterate(false); // Not parallel.
  gch->gen_process_strong_roots(_cmsGen->level(),
                                true,   // younger gens are roots
                                true,   // activate StrongRootsScope
                                true,   // collecting perm gen
                                SharedHeap::ScanningOption(roots_scanning_options()),
                                &notOlder,
                                true,   // walk code active on stacks
                                NULL);

  // Now mark from the roots
  assert(_revisitStack.isEmpty(), "Should be empty");
  MarkFromRootsVerifyClosure markFromRootsClosure(this, _span,
    verification_mark_bm(), markBitMap(), verification_mark_stack());
  assert(_restart_addr == NULL, "Expected pre-condition");
  verification_mark_bm()->iterate(&markFromRootsClosure);
  while (_restart_addr != NULL) {
    // Deal with stack overflow: by restarting at the indicated
    // address.
    HeapWord* ra = _restart_addr;
    markFromRootsClosure.reset(ra);
    _restart_addr = NULL;
    verification_mark_bm()->iterate(&markFromRootsClosure, ra, _span.end());
  }
  assert(verification_mark_stack()->isEmpty(), "Should have been drained");
  verify_work_stacks_empty();
  // Should reset the revisit stack above, since no class tree
  // surgery is forthcoming.
  _revisitStack.reset(); // throwing away all contents

  // Marking completed -- now verify that each bit marked in
  // verification_mark_bm() is also marked in markBitMap(); flag all
  // errors by printing corresponding objects.
  VerifyMarkedClosure vcl(markBitMap());
  verification_mark_bm()->iterate(&vcl);
  assert(!vcl.failed(), "Else verification above should not have succeeded");
}

void ConcurrentMarkSweepGeneration::save_marks() {
  // delegate to CMS space
  cmsSpace()->save_marks();
  for (uint i = 0; i < ParallelGCThreads; i++) {
    _par_gc_thread_states[i]->promo.startTrackingPromotions();
  }
}

bool ConcurrentMarkSweepGeneration::no_allocs_since_save_marks() {
  return cmsSpace()->no_allocs_since_save_marks();
}

#define CMS_SINCE_SAVE_MARKS_DEFN(OopClosureType, nv_suffix)    \
                                                                \
void ConcurrentMarkSweepGeneration::                            \
oop_since_save_marks_iterate##nv_suffix(OopClosureType* cl) {   \
  cl->set_generation(this);                                     \
  cmsSpace()->oop_since_save_marks_iterate##nv_suffix(cl);      \
  cl->reset_generation();                                       \
  save_marks();                                                 \
}

ALL_SINCE_SAVE_MARKS_CLOSURES(CMS_SINCE_SAVE_MARKS_DEFN)

void
ConcurrentMarkSweepGeneration::object_iterate_since_last_GC(ObjectClosure* blk)
{
  // Not currently implemented; need to do the following. -- ysr.
  // dld -- I think that is used for some sort of allocation profiler.  So it
  // really means the objects allocated by the mutator since the last
  // GC.  We could potentially implement this cheaply by recording only
  // the direct allocations in a side data structure.
  //
  // I think we probably ought not to be required to support these
  // iterations at any arbitrary point; I think there ought to be some
  // call to enable/disable allocation profiling in a generation/space,
  // and the iterator ought to return the objects allocated in the
  // gen/space since the enable call, or the last iterator call (which
  // will probably be at a GC.)  That way, for gens like CM&S that would
  // require some extra data structure to support this, we only pay the
  // cost when it's in use...
  cmsSpace()->object_iterate_since_last_GC(blk);
}

void
ConcurrentMarkSweepGeneration::younger_refs_iterate(OopsInGenClosure* cl) {
  cl->set_generation(this);
  younger_refs_in_space_iterate(_cmsSpace, cl);
  cl->reset_generation();
}

void
ConcurrentMarkSweepGeneration::oop_iterate(MemRegion mr, OopClosure* cl) {
  if (freelistLock()->owned_by_self()) {
    Generation::oop_iterate(mr, cl);
  } else {
    MutexLockerEx x(freelistLock(), Mutex::_no_safepoint_check_flag);
    Generation::oop_iterate(mr, cl);
  }
}

void
ConcurrentMarkSweepGeneration::oop_iterate(OopClosure* cl) {
  if (freelistLock()->owned_by_self()) {
    Generation::oop_iterate(cl);
  } else {
    MutexLockerEx x(freelistLock(), Mutex::_no_safepoint_check_flag);
    Generation::oop_iterate(cl);
  }
}

void
ConcurrentMarkSweepGeneration::object_iterate(ObjectClosure* cl) {
  if (freelistLock()->owned_by_self()) {
    Generation::object_iterate(cl);
  } else {
    MutexLockerEx x(freelistLock(), Mutex::_no_safepoint_check_flag);
    Generation::object_iterate(cl);
  }
}

void
ConcurrentMarkSweepGeneration::safe_object_iterate(ObjectClosure* cl) {
  if (freelistLock()->owned_by_self()) {
    Generation::safe_object_iterate(cl);
  } else {
    MutexLockerEx x(freelistLock(), Mutex::_no_safepoint_check_flag);
    Generation::safe_object_iterate(cl);
  }
}

void
ConcurrentMarkSweepGeneration::pre_adjust_pointers() {
}

void
ConcurrentMarkSweepGeneration::post_compact() {
}

void
ConcurrentMarkSweepGeneration::prepare_for_verify() {
  // Fix the linear allocation blocks to look like free blocks.

  // Locks are normally acquired/released in gc_prologue/gc_epilogue, but those
  // are not called when the heap is verified during universe initialization and
  // at vm shutdown.
  if (freelistLock()->owned_by_self()) {
    cmsSpace()->prepare_for_verify();
  } else {
    MutexLockerEx fll(freelistLock(), Mutex::_no_safepoint_check_flag);
    cmsSpace()->prepare_for_verify();
  }
}

void
ConcurrentMarkSweepGeneration::verify(bool allow_dirty /* ignored */) {
  // Locks are normally acquired/released in gc_prologue/gc_epilogue, but those
  // are not called when the heap is verified during universe initialization and
  // at vm shutdown.
  if (freelistLock()->owned_by_self()) {
    cmsSpace()->verify(false /* ignored */);
  } else {
    MutexLockerEx fll(freelistLock(), Mutex::_no_safepoint_check_flag);
    cmsSpace()->verify(false /* ignored */);
  }
}

void CMSCollector::verify(bool allow_dirty /* ignored */) {
  _cmsGen->verify(allow_dirty);
  _permGen->verify(allow_dirty);
}

#ifndef PRODUCT
bool CMSCollector::overflow_list_is_empty() const {
  assert(_num_par_pushes >= 0, "Inconsistency");
  if (_overflow_list == NULL) {
    assert(_num_par_pushes == 0, "Inconsistency");
  }
  return _overflow_list == NULL;
}

// The methods verify_work_stacks_empty() and verify_overflow_empty()
// merely consolidate assertion checks that appear to occur together frequently.
void CMSCollector::verify_work_stacks_empty() const {
  assert(_markStack.isEmpty(), "Marking stack should be empty");
  assert(overflow_list_is_empty(), "Overflow list should be empty");
}

void CMSCollector::verify_overflow_empty() const {
  assert(overflow_list_is_empty(), "Overflow list should be empty");
  assert(no_preserved_marks(), "No preserved marks");
}
#endif // PRODUCT

// Decide if we want to enable class unloading as part of the
// ensuing concurrent GC cycle. We will collect the perm gen and
// unload classes if it's the case that:
// (1) an explicit gc request has been made and the flag
//     ExplicitGCInvokesConcurrentAndUnloadsClasses is set, OR
// (2) (a) class unloading is enabled at the command line, and
//     (b) (i)   perm gen threshold has been crossed, or
//         (ii)  old gen is getting really full, or
//         (iii) the previous N CMS collections did not collect the
//               perm gen
// NOTE: Provided there is no change in the state of the heap between
// calls to this method, it should have idempotent results. Moreover,
// its results should be monotonically increasing (i.e. going from 0 to 1,
// but not 1 to 0) between successive calls between which the heap was
// not collected. For the implementation below, it must thus rely on
// the property that concurrent_cycles_since_last_unload()
// will not decrease unless a collection cycle happened and that
// _permGen->should_concurrent_collect() and _cmsGen->is_too_full() are
// themselves also monotonic in that sense. See check_monotonicity()
// below.
bool CMSCollector::update_should_unload_classes() {
  _should_unload_classes = false;
  // Condition 1 above
  if (_full_gc_requested && ExplicitGCInvokesConcurrentAndUnloadsClasses) {
    _should_unload_classes = true;
  } else if (CMSClassUnloadingEnabled) { // Condition 2.a above
    // Disjuncts 2.b.(i,ii,iii) above
    _should_unload_classes = (concurrent_cycles_since_last_unload() >=
                              CMSClassUnloadingMaxInterval)
                           || _permGen->should_concurrent_collect()
                           || _cmsGen->is_too_full();
  }
  return _should_unload_classes;
}

bool ConcurrentMarkSweepGeneration::is_too_full() const {
  bool res = should_concurrent_collect();
  res = res && (occupancy() > (double)CMSIsTooFullPercentage/100.0);
  return res;
}

void CMSCollector::setup_cms_unloading_and_verification_state() {
  const  bool should_verify =    VerifyBeforeGC || VerifyAfterGC || VerifyDuringGC
                             || VerifyBeforeExit;
  const  int  rso           =    SharedHeap::SO_Symbols | SharedHeap::SO_Strings
                             |   SharedHeap::SO_CodeCache;

  if (should_unload_classes()) {   // Should unload classes this cycle
    remove_root_scanning_option(rso);  // Shrink the root set appropriately
    set_verifying(should_verify);    // Set verification state for this cycle
    return;                            // Nothing else needs to be done at this time
  }

  // Not unloading classes this cycle
  assert(!should_unload_classes(), "Inconsitency!");
  if ((!verifying() || unloaded_classes_last_cycle()) && should_verify) {
    // We were not verifying, or we _were_ unloading classes in the last cycle,
    // AND some verification options are enabled this cycle; in this case,
    // we must make sure that the deadness map is allocated if not already so,
    // and cleared (if already allocated previously --
    // CMSBitMap::sizeInBits() is used to determine if it's allocated).
    if (perm_gen_verify_bit_map()->sizeInBits() == 0) {
      if (!perm_gen_verify_bit_map()->allocate(_permGen->reserved())) {
        warning("Failed to allocate permanent generation verification CMS Bit Map;\n"
                "permanent generation verification disabled");
        return;  // Note that we leave verification disabled, so we'll retry this
                 // allocation next cycle. We _could_ remember this failure
                 // and skip further attempts and permanently disable verification
                 // attempts if that is considered more desirable.
      }
      assert(perm_gen_verify_bit_map()->covers(_permGen->reserved()),
              "_perm_gen_ver_bit_map inconsistency?");
    } else {
      perm_gen_verify_bit_map()->clear_all();
    }
    // Include symbols, strings and code cache elements to prevent their resurrection.
    add_root_scanning_option(rso);
    set_verifying(true);
  } else if (verifying() && !should_verify) {
    // We were verifying, but some verification flags got disabled.
    set_verifying(false);
    // Exclude symbols, strings and code cache elements from root scanning to
    // reduce IM and RM pauses.
    remove_root_scanning_option(rso);
  }
}


#ifndef PRODUCT
HeapWord* CMSCollector::block_start(const void* p) const {
  const HeapWord* addr = (HeapWord*)p;
  if (_span.contains(p)) {
    if (_cmsGen->cmsSpace()->is_in_reserved(addr)) {
      return _cmsGen->cmsSpace()->block_start(p);
    } else {
      assert(_permGen->cmsSpace()->is_in_reserved(addr),
             "Inconsistent _span?");
      return _permGen->cmsSpace()->block_start(p);
    }
  }
  return NULL;
}
#endif

HeapWord*
ConcurrentMarkSweepGeneration::expand_and_allocate(size_t word_size,
                                                   bool   tlab,
                                                   bool   parallel) {
  assert(!tlab, "Can't deal with TLAB allocation");
  MutexLockerEx x(freelistLock(), Mutex::_no_safepoint_check_flag);
  expand(word_size*HeapWordSize, MinHeapDeltaBytes,
    CMSExpansionCause::_satisfy_allocation);
  if (GCExpandToAllocateDelayMillis > 0) {
    os::sleep(Thread::current(), GCExpandToAllocateDelayMillis, false);
  }
  return have_lock_and_allocate(word_size, tlab);
}

// YSR: All of this generation expansion/shrinking stuff is an exact copy of
// OneContigSpaceCardGeneration, which makes me wonder if we should move this
// to CardGeneration and share it...
bool ConcurrentMarkSweepGeneration::expand(size_t bytes, size_t expand_bytes) {
  return CardGeneration::expand(bytes, expand_bytes);
}

void ConcurrentMarkSweepGeneration::expand(size_t bytes, size_t expand_bytes,
  CMSExpansionCause::Cause cause)
{

  bool success = expand(bytes, expand_bytes);

  // remember why we expanded; this information is used
  // by shouldConcurrentCollect() when making decisions on whether to start
  // a new CMS cycle.
  if (success) {
    set_expansion_cause(cause);
    if (PrintGCDetails && Verbose) {
      gclog_or_tty->print_cr("Expanded CMS gen for %s",
        CMSExpansionCause::to_string(cause));
    }
  }
}

HeapWord* ConcurrentMarkSweepGeneration::expand_and_par_lab_allocate(CMSParGCThreadState* ps, size_t word_sz) {
  HeapWord* res = NULL;
  MutexLocker x(ParGCRareEvent_lock);
  while (true) {
    // Expansion by some other thread might make alloc OK now:
    res = ps->lab.alloc(word_sz);
    if (res != NULL) return res;
    // If there's not enough expansion space available, give up.
    if (_virtual_space.uncommitted_size() < (word_sz * HeapWordSize)) {
      return NULL;
    }
    // Otherwise, we try expansion.
    expand(word_sz*HeapWordSize, MinHeapDeltaBytes,
      CMSExpansionCause::_allocate_par_lab);
    // Now go around the loop and try alloc again;
    // A competing par_promote might beat us to the expansion space,
    // so we may go around the loop again if promotion fails agaion.
    if (GCExpandToAllocateDelayMillis > 0) {
      os::sleep(Thread::current(), GCExpandToAllocateDelayMillis, false);
    }
  }
}


bool ConcurrentMarkSweepGeneration::expand_and_ensure_spooling_space(
  PromotionInfo* promo) {
  MutexLocker x(ParGCRareEvent_lock);
  size_t refill_size_bytes = promo->refillSize() * HeapWordSize;
  while (true) {
    // Expansion by some other thread might make alloc OK now:
    if (promo->ensure_spooling_space()) {
      assert(promo->has_spooling_space(),
             "Post-condition of successful ensure_spooling_space()");
      return true;
    }
    // If there's not enough expansion space available, give up.
    if (_virtual_space.uncommitted_size() < refill_size_bytes) {
      return false;
    }
    // Otherwise, we try expansion.
    expand(refill_size_bytes, MinHeapDeltaBytes,
      CMSExpansionCause::_allocate_par_spooling_space);
    // Now go around the loop and try alloc again;
    // A competing allocation might beat us to the expansion space,
    // so we may go around the loop again if allocation fails again.
    if (GCExpandToAllocateDelayMillis > 0) {
      os::sleep(Thread::current(), GCExpandToAllocateDelayMillis, false);
    }
  }
}



void ConcurrentMarkSweepGeneration::shrink(size_t bytes) {
  assert_locked_or_safepoint(Heap_lock);
  size_t size = ReservedSpace::page_align_size_down(bytes);
  if (size > 0) {
    shrink_by(size);
  }
}

bool ConcurrentMarkSweepGeneration::grow_by(size_t bytes) {
  assert_locked_or_safepoint(Heap_lock);
  bool result = _virtual_space.expand_by(bytes);
  if (result) {
    HeapWord* old_end = _cmsSpace->end();
    size_t new_word_size =
      heap_word_size(_virtual_space.committed_size());
    MemRegion mr(_cmsSpace->bottom(), new_word_size);
    _bts->resize(new_word_size);  // resize the block offset shared array
    Universe::heap()->barrier_set()->resize_covered_region(mr);
    // Hmmmm... why doesn't CFLS::set_end verify locking?
    // This is quite ugly; FIX ME XXX
    _cmsSpace->assert_locked(freelistLock());
    _cmsSpace->set_end((HeapWord*)_virtual_space.high());

    // update the space and generation capacity counters
    if (UsePerfData) {
      _space_counters->update_capacity();
      _gen_counters->update_all();
    }

    if (Verbose && PrintGC) {
      size_t new_mem_size = _virtual_space.committed_size();
      size_t old_mem_size = new_mem_size - bytes;
      gclog_or_tty->print_cr("Expanding %s from %ldK by %ldK to %ldK",
                    name(), old_mem_size/K, bytes/K, new_mem_size/K);
    }
  }
  return result;
}

bool ConcurrentMarkSweepGeneration::grow_to_reserved() {
  assert_locked_or_safepoint(Heap_lock);
  bool success = true;
  const size_t remaining_bytes = _virtual_space.uncommitted_size();
  if (remaining_bytes > 0) {
    success = grow_by(remaining_bytes);
    DEBUG_ONLY(if (!success) warning("grow to reserved failed");)
  }
  return success;
}

void ConcurrentMarkSweepGeneration::shrink_by(size_t bytes) {
  assert_locked_or_safepoint(Heap_lock);
  assert_lock_strong(freelistLock());
  // XXX Fix when compaction is implemented.
  warning("Shrinking of CMS not yet implemented");
  return;
}


// Simple ctor/dtor wrapper for accounting & timer chores around concurrent
// phases.
class CMSPhaseAccounting: public StackObj {
 public:
  CMSPhaseAccounting(CMSCollector *collector,
                     const char *phase,
                     bool print_cr = true);
  ~CMSPhaseAccounting();

 private:
  CMSCollector *_collector;
  const char *_phase;
  elapsedTimer _wallclock;
  bool _print_cr;

 public:
  // Not MT-safe; so do not pass around these StackObj's
  // where they may be accessed by other threads.
  jlong wallclock_millis() {
    assert(_wallclock.is_active(), "Wall clock should not stop");
    _wallclock.stop();  // to record time
    jlong ret = _wallclock.milliseconds();
    _wallclock.start(); // restart
    return ret;
  }
};

CMSPhaseAccounting::CMSPhaseAccounting(CMSCollector *collector,
                                       const char *phase,
                                       bool print_cr) :
  _collector(collector), _phase(phase), _print_cr(print_cr) {

  if (PrintCMSStatistics != 0) {
    _collector->resetYields();
  }
  if (PrintGCDetails && PrintGCTimeStamps) {
    gclog_or_tty->date_stamp(PrintGCDateStamps);
    gclog_or_tty->stamp();
    gclog_or_tty->print_cr(": [%s-concurrent-%s-start]",
      _collector->cmsGen()->short_name(), _phase);
  }
  _collector->resetTimer();
  _wallclock.start();
  _collector->startTimer();
}

CMSPhaseAccounting::~CMSPhaseAccounting() {
  assert(_wallclock.is_active(), "Wall clock should not have stopped");
  _collector->stopTimer();
  _wallclock.stop();
  if (PrintGCDetails) {
    gclog_or_tty->date_stamp(PrintGCDateStamps);
    if (PrintGCTimeStamps) {
      gclog_or_tty->stamp();
      gclog_or_tty->print(": ");
    }
    gclog_or_tty->print("[%s-concurrent-%s: %3.3f/%3.3f secs]",
                 _collector->cmsGen()->short_name(),
                 _phase, _collector->timerValue(), _wallclock.seconds());
    if (_print_cr) {
      gclog_or_tty->print_cr("");
    }
    if (PrintCMSStatistics != 0) {
      gclog_or_tty->print_cr(" (CMS-concurrent-%s yielded %d times)", _phase,
                    _collector->yields());
    }
  }
}

// CMS work

// Checkpoint the roots into this generation from outside
// this generation. [Note this initial checkpoint need only
// be approximate -- we'll do a catch up phase subsequently.]
void CMSCollector::checkpointRootsInitial(bool asynch) {
  assert(_collectorState == InitialMarking, "Wrong collector state");
  check_correct_thread_executing();
  TraceCMSMemoryManagerStats tms(_collectorState);
  ReferenceProcessor* rp = ref_processor();
  SpecializationStats::clear();
  assert(_restart_addr == NULL, "Control point invariant");
  if (asynch) {
    // acquire locks for subsequent manipulations
    MutexLockerEx x(bitMapLock(),
                    Mutex::_no_safepoint_check_flag);
    checkpointRootsInitialWork(asynch);
    rp->verify_no_references_recorded();
    rp->enable_discovery(); // enable ("weak") refs discovery
    _collectorState = Marking;
  } else {
    // (Weak) Refs discovery: this is controlled from genCollectedHeap::do_collection
    // which recognizes if we are a CMS generation, and doesn't try to turn on
    // discovery; verify that they aren't meddling.
    assert(!rp->discovery_is_atomic(),
           "incorrect setting of discovery predicate");
    assert(!rp->discovery_enabled(), "genCollectedHeap shouldn't control "
           "ref discovery for this generation kind");
    // already have locks
    checkpointRootsInitialWork(asynch);
    rp->enable_discovery(); // now enable ("weak") refs discovery
    _collectorState = Marking;
  }
  SpecializationStats::print();
}

void CMSCollector::checkpointRootsInitialWork(bool asynch) {
  assert(SafepointSynchronize::is_at_safepoint(), "world should be stopped");
  assert(_collectorState == InitialMarking, "just checking");

  // If there has not been a GC[n-1] since last GC[n] cycle completed,
  // precede our marking with a collection of all
  // younger generations to keep floating garbage to a minimum.
  // XXX: we won't do this for now -- it's an optimization to be done later.

  // already have locks
  assert_lock_strong(bitMapLock());
  assert(_markBitMap.isAllClear(), "was reset at end of previous cycle");

  // Setup the verification and class unloading state for this
  // CMS collection cycle.
  setup_cms_unloading_and_verification_state();

  NOT_PRODUCT(TraceTime t("\ncheckpointRootsInitialWork",
    PrintGCDetails && Verbose, true, gclog_or_tty);)
  if (UseAdaptiveSizePolicy) {
    size_policy()->checkpoint_roots_initial_begin();
  }

  // Reset all the PLAB chunk arrays if necessary.
  if (_survivor_plab_array != NULL && !CMSPLABRecordAlways) {
    reset_survivor_plab_arrays();
  }

  ResourceMark rm;
  HandleMark  hm;

  FalseClosure falseClosure;
  // In the case of a synchronous collection, we will elide the
  // remark step, so it's important to catch all the nmethod oops
  // in this step.
  // The final 'true' flag to gen_process_strong_roots will ensure this.
  // If 'async' is true, we can relax the nmethod tracing.
  MarkRefsIntoClosure notOlder(_span, &_markBitMap);
  GenCollectedHeap* gch = GenCollectedHeap::heap();

  verify_work_stacks_empty();
  verify_overflow_empty();

  gch->ensure_parsability(false);  // fill TLABs, but no need to retire them
  // Update the saved marks which may affect the root scans.
  gch->save_marks();

  // weak reference processing has not started yet.
  ref_processor()->set_enqueuing_is_done(false);

  {
    // This is not needed. DEBUG_ONLY(RememberKlassesChecker imx(true);)
    COMPILER2_PRESENT(DerivedPointerTableDeactivate dpt_deact;)
    gch->rem_set()->prepare_for_younger_refs_iterate(false); // Not parallel.
    gch->gen_process_strong_roots(_cmsGen->level(),
                                  true,   // younger gens are roots
                                  true,   // activate StrongRootsScope
                                  true,   // collecting perm gen
                                  SharedHeap::ScanningOption(roots_scanning_options()),
                                  &notOlder,
                                  true,   // walk all of code cache if (so & SO_CodeCache)
                                  NULL);
  }

  // Clear mod-union table; it will be dirtied in the prologue of
  // CMS generation per each younger generation collection.

  assert(_modUnionTable.isAllClear(),
       "Was cleared in most recent final checkpoint phase"
       " or no bits are set in the gc_prologue before the start of the next "
       "subsequent marking phase.");

  // Temporarily disabled, since pre/post-consumption closures don't
  // care about precleaned cards
  #if 0
  {
    MemRegion mr = MemRegion((HeapWord*)_virtual_space.low(),
                             (HeapWord*)_virtual_space.high());
    _ct->ct_bs()->preclean_dirty_cards(mr);
  }
  #endif

  // Save the end of the used_region of the constituent generations
  // to be used to limit the extent of sweep in each generation.
  save_sweep_limits();
  if (UseAdaptiveSizePolicy) {
    size_policy()->checkpoint_roots_initial_end(gch->gc_cause());
  }
  verify_overflow_empty();
}

bool CMSCollector::markFromRoots(bool asynch) {
  // we might be tempted to assert that:
  // assert(asynch == !SafepointSynchronize::is_at_safepoint(),
  //        "inconsistent argument?");
  // However that wouldn't be right, because it's possible that
  // a safepoint is indeed in progress as a younger generation
  // stop-the-world GC happens even as we mark in this generation.
  assert(_collectorState == Marking, "inconsistent state?");
  check_correct_thread_executing();
  verify_overflow_empty();

  bool res;
  if (asynch) {

    // Start the timers for adaptive size policy for the concurrent phases
    // Do it here so that the foreground MS can use the concurrent
    // timer since a foreground MS might has the sweep done concurrently
    // or STW.
    if (UseAdaptiveSizePolicy) {
      size_policy()->concurrent_marking_begin();
    }

    // Weak ref discovery note: We may be discovering weak
    // refs in this generation concurrent (but interleaved) with
    // weak ref discovery by a younger generation collector.

    CMSTokenSyncWithLocks ts(true, bitMapLock());
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    CMSPhaseAccounting pa(this, "mark", !PrintGCDetails);
    res = markFromRootsWork(asynch);
    if (res) {
      _collectorState = Precleaning;
    } else { // We failed and a foreground collection wants to take over
      assert(_foregroundGCIsActive, "internal state inconsistency");
      assert(_restart_addr == NULL,  "foreground will restart from scratch");
      if (PrintGCDetails) {
        gclog_or_tty->print_cr("bailing out to foreground collection");
      }
    }
    if (UseAdaptiveSizePolicy) {
      size_policy()->concurrent_marking_end();
    }
  } else {
    assert(SafepointSynchronize::is_at_safepoint(),
           "inconsistent with asynch == false");
    if (UseAdaptiveSizePolicy) {
      size_policy()->ms_collection_marking_begin();
    }
    // already have locks
    res = markFromRootsWork(asynch);
    _collectorState = FinalMarking;
    if (UseAdaptiveSizePolicy) {
      GenCollectedHeap* gch = GenCollectedHeap::heap();
      size_policy()->ms_collection_marking_end(gch->gc_cause());
    }
  }
  verify_overflow_empty();
  return res;
}

bool CMSCollector::markFromRootsWork(bool asynch) {
  // iterate over marked bits in bit map, doing a full scan and mark
  // from these roots using the following algorithm:
  // . if oop is to the right of the current scan pointer,
  //   mark corresponding bit (we'll process it later)
  // . else (oop is to left of current scan pointer)
  //   push oop on marking stack
  // . drain the marking stack

  // Note that when we do a marking step we need to hold the
  // bit map lock -- recall that direct allocation (by mutators)
  // and promotion (by younger generation collectors) is also
  // marking the bit map. [the so-called allocate live policy.]
  // Because the implementation of bit map marking is not
  // robust wrt simultaneous marking of bits in the same word,
  // we need to make sure that there is no such interference
  // between concurrent such updates.

  // already have locks
  assert_lock_strong(bitMapLock());

  // Clear the revisit stack, just in case there are any
  // obsolete contents from a short-circuited previous CMS cycle.
  _revisitStack.reset();
  verify_work_stacks_empty();
  verify_overflow_empty();
  assert(_revisitStack.isEmpty(), "tabula rasa");
  DEBUG_ONLY(RememberKlassesChecker cmx(should_unload_classes());)
  bool result = false;
  if (CMSConcurrentMTEnabled && ConcGCThreads > 0) {
    result = do_marking_mt(asynch);
  } else {
    result = do_marking_st(asynch);
  }
  return result;
}

// Forward decl
class CMSConcMarkingTask;

class CMSConcMarkingTerminator: public ParallelTaskTerminator {
  CMSCollector*       _collector;
  CMSConcMarkingTask* _task;
  bool _yield;
 protected:
  virtual void yield();
 public:
  // "n_threads" is the number of threads to be terminated.
  // "queue_set" is a set of work queues of other threads.
  // "collector" is the CMS collector associated with this task terminator.
  // "yield" indicates whether we need the gang as a whole to yield.
  CMSConcMarkingTerminator(int n_threads, TaskQueueSetSuper* queue_set,
                           CMSCollector* collector, bool yield) :
    ParallelTaskTerminator(n_threads, queue_set),
    _collector(collector),
    _yield(yield) { }

  void set_task(CMSConcMarkingTask* task) {
    _task = task;
  }
};

// MT Concurrent Marking Task
class CMSConcMarkingTask: public YieldingFlexibleGangTask {
  CMSCollector* _collector;
  YieldingFlexibleWorkGang* _workers;        // the whole gang
  int           _n_workers;                  // requested/desired # workers
  bool          _asynch;
  bool          _result;
  CompactibleFreeListSpace*  _cms_space;
  CompactibleFreeListSpace* _perm_space;
  HeapWord*     _global_finger;
  HeapWord*     _restart_addr;

  //  Exposed here for yielding support
  Mutex* const _bit_map_lock;

  // The per thread work queues, available here for stealing
  OopTaskQueueSet*  _task_queues;
  CMSConcMarkingTerminator _term;

 public:
  CMSConcMarkingTask(CMSCollector* collector,
                 CompactibleFreeListSpace* cms_space,
                 CompactibleFreeListSpace* perm_space,
                 bool asynch, int n_workers,
                 YieldingFlexibleWorkGang* workers,
                 OopTaskQueueSet* task_queues):
    YieldingFlexibleGangTask("Concurrent marking done multi-threaded"),
    _collector(collector),
    _cms_space(cms_space),
    _perm_space(perm_space),
    _asynch(asynch), _n_workers(n_workers), _result(true),
    _workers(workers), _task_queues(task_queues),
    _term(n_workers, task_queues, _collector, asynch),
    _bit_map_lock(collector->bitMapLock())
  {
    assert(n_workers <= workers->total_workers(),
           "Else termination won't work correctly today"); // XXX FIX ME!
    _requested_size = n_workers;
    _term.set_task(this);
    assert(_cms_space->bottom() < _perm_space->bottom(),
           "Finger incorrectly initialized below");
    _restart_addr = _global_finger = _cms_space->bottom();
  }


  OopTaskQueueSet* task_queues()  { return _task_queues; }

  OopTaskQueue* work_queue(int i) { return task_queues()->queue(i); }

  HeapWord** global_finger_addr() { return &_global_finger; }

  CMSConcMarkingTerminator* terminator() { return &_term; }

  void work(int i);

  virtual void coordinator_yield();  // stuff done by coordinator
  bool result() { return _result; }

  void reset(HeapWord* ra) {
    assert(_global_finger >= _cms_space->end(),  "Postcondition of ::work(i)");
    assert(_global_finger >= _perm_space->end(), "Postcondition of ::work(i)");
    assert(ra             <  _perm_space->end(), "ra too large");
    _restart_addr = _global_finger = ra;
    _term.reset_for_reuse();
  }

  static bool get_work_from_overflow_stack(CMSMarkStack* ovflw_stk,
                                           OopTaskQueue* work_q);

 private:
  void do_scan_and_mark(int i, CompactibleFreeListSpace* sp);
  void do_work_steal(int i);
  void bump_global_finger(HeapWord* f);
};

void CMSConcMarkingTerminator::yield() {
  if (ConcurrentMarkSweepThread::should_yield() &&
      !_collector->foregroundGCIsActive() &&
      _yield) {
    _task->yield();
  } else {
    ParallelTaskTerminator::yield();
  }
}

////////////////////////////////////////////////////////////////
// Concurrent Marking Algorithm Sketch
////////////////////////////////////////////////////////////////
// Until all tasks exhausted (both spaces):
// -- claim next available chunk
// -- bump global finger via CAS
// -- find first object that starts in this chunk
//    and start scanning bitmap from that position
// -- scan marked objects for oops
// -- CAS-mark target, and if successful:
//    . if target oop is above global finger (volatile read)
//      nothing to do
//    . if target oop is in chunk and above local finger
//        then nothing to do
//    . else push on work-queue
// -- Deal with possible overflow issues:
//    . local work-queue overflow causes stuff to be pushed on
//      global (common) overflow queue
//    . always first empty local work queue
//    . then get a batch of oops from global work queue if any
//    . then do work stealing
// -- When all tasks claimed (both spaces)
//    and local work queue empty,
//    then in a loop do:
//    . check global overflow stack; steal a batch of oops and trace
//    . try to steal from other threads oif GOS is empty
//    . if neither is available, offer termination
// -- Terminate and return result
//
void CMSConcMarkingTask::work(int i) {
  elapsedTimer _timer;
  ResourceMark rm;
  HandleMark hm;

  DEBUG_ONLY(_collector->verify_overflow_empty();)

  // Before we begin work, our work queue should be empty
  assert(work_queue(i)->size() == 0, "Expected to be empty");
  // Scan the bitmap covering _cms_space, tracing through grey objects.
  _timer.start();
  do_scan_and_mark(i, _cms_space);
  _timer.stop();
  if (PrintCMSStatistics != 0) {
    gclog_or_tty->print_cr("Finished cms space scanning in %dth thread: %3.3f sec",
      i, _timer.seconds()); // XXX: need xxx/xxx type of notation, two timers
  }

  // ... do the same for the _perm_space
  _timer.reset();
  _timer.start();
  do_scan_and_mark(i, _perm_space);
  _timer.stop();
  if (PrintCMSStatistics != 0) {
    gclog_or_tty->print_cr("Finished perm space scanning in %dth thread: %3.3f sec",
      i, _timer.seconds()); // XXX: need xxx/xxx type of notation, two timers
  }

  // ... do work stealing
  _timer.reset();
  _timer.start();
  do_work_steal(i);
  _timer.stop();
  if (PrintCMSStatistics != 0) {
    gclog_or_tty->print_cr("Finished work stealing in %dth thread: %3.3f sec",
      i, _timer.seconds()); // XXX: need xxx/xxx type of notation, two timers
  }
  assert(_collector->_markStack.isEmpty(), "Should have been emptied");
  assert(work_queue(i)->size() == 0, "Should have been emptied");
  // Note that under the current task protocol, the
  // following assertion is true even of the spaces
  // expanded since the completion of the concurrent
  // marking. XXX This will likely change under a strict
  // ABORT semantics.
  assert(_global_finger >  _cms_space->end() &&
         _global_finger >= _perm_space->end(),
         "All tasks have been completed");
  DEBUG_ONLY(_collector->verify_overflow_empty();)
}

void CMSConcMarkingTask::bump_global_finger(HeapWord* f) {
  HeapWord* read = _global_finger;
  HeapWord* cur  = read;
  while (f > read) {
    cur = read;
    read = (HeapWord*) Atomic::cmpxchg_ptr(f, &_global_finger, cur);
    if (cur == read) {
      // our cas succeeded
      assert(_global_finger >= f, "protocol consistency");
      break;
    }
  }
}

// This is really inefficient, and should be redone by
// using (not yet available) block-read and -write interfaces to the
// stack and the work_queue. XXX FIX ME !!!
bool CMSConcMarkingTask::get_work_from_overflow_stack(CMSMarkStack* ovflw_stk,
                                                      OopTaskQueue* work_q) {
  // Fast lock-free check
  if (ovflw_stk->length() == 0) {
    return false;
  }
  assert(work_q->size() == 0, "Shouldn't steal");
  MutexLockerEx ml(ovflw_stk->par_lock(),
                   Mutex::_no_safepoint_check_flag);
  // Grab up to 1/4 the size of the work queue
  size_t num = MIN2((size_t)(work_q->max_elems() - work_q->size())/4,
                    (size_t)ParGCDesiredObjsFromOverflowList);
  num = MIN2(num, ovflw_stk->length());
  for (int i = (int) num; i > 0; i--) {
    oop cur = ovflw_stk->pop();
    assert(cur != NULL, "Counted wrong?");
    work_q->push(cur);
  }
  return num > 0;
}

void CMSConcMarkingTask::do_scan_and_mark(int i, CompactibleFreeListSpace* sp) {
  SequentialSubTasksDone* pst = sp->conc_par_seq_tasks();
  int n_tasks = pst->n_tasks();
  // We allow that there may be no tasks to do here because
  // we are restarting after a stack overflow.
  assert(pst->valid() || n_tasks == 0, "Uninitialized use?");
  int nth_task = 0;

  HeapWord* aligned_start = sp->bottom();
  if (sp->used_region().contains(_restart_addr)) {
    // Align down to a card boundary for the start of 0th task
    // for this space.
    aligned_start =
      (HeapWord*)align_size_down((uintptr_t)_restart_addr,
                                 CardTableModRefBS::card_size);
  }

  size_t chunk_size = sp->marking_task_size();
  while (!pst->is_task_claimed(/* reference */ nth_task)) {
    // Having claimed the nth task in this space,
    // compute the chunk that it corresponds to:
    MemRegion span = MemRegion(aligned_start + nth_task*chunk_size,
                               aligned_start + (nth_task+1)*chunk_size);
    // Try and bump the global finger via a CAS;
    // note that we need to do the global finger bump
    // _before_ taking the intersection below, because
    // the task corresponding to that region will be
    // deemed done even if the used_region() expands
    // because of allocation -- as it almost certainly will
    // during start-up while the threads yield in the
    // closure below.
    HeapWord* finger = span.end();
    bump_global_finger(finger);   // atomically
    // There are null tasks here corresponding to chunks
    // beyond the "top" address of the space.
    span = span.intersection(sp->used_region());
    if (!span.is_empty()) {  // Non-null task
      HeapWord* prev_obj;
      assert(!span.contains(_restart_addr) || nth_task == 0,
             "Inconsistency");
      if (nth_task == 0) {
        // For the 0th task, we'll not need to compute a block_start.
        if (span.contains(_restart_addr)) {
          // In the case of a restart because of stack overflow,
          // we might additionally skip a chunk prefix.
          prev_obj = _restart_addr;
        } else {
          prev_obj = span.start();
        }
      } else {
        // We want to skip the first object because
        // the protocol is to scan any object in its entirety
        // that _starts_ in this span; a fortiori, any
        // object starting in an earlier span is scanned
        // as part of an earlier claimed task.
        // Below we use the "careful" version of block_start
        // so we do not try to navigate uninitialized objects.
        prev_obj = sp->block_start_careful(span.start());
        // Below we use a variant of block_size that uses the
        // Printezis bits to avoid waiting for allocated
        // objects to become initialized/parsable.
        while (prev_obj < span.start()) {
          size_t sz = sp->block_size_no_stall(prev_obj, _collector);
          if (sz > 0) {
            prev_obj += sz;
          } else {
            // In this case we may end up doing a bit of redundant
            // scanning, but that appears unavoidable, short of
            // locking the free list locks; see bug 6324141.
            break;
          }
        }
      }
      if (prev_obj < span.end()) {
        MemRegion my_span = MemRegion(prev_obj, span.end());
        // Do the marking work within a non-empty span --
        // the last argument to the constructor indicates whether the
        // iteration should be incremental with periodic yields.
        Par_MarkFromRootsClosure cl(this, _collector, my_span,
                                    &_collector->_markBitMap,
                                    work_queue(i),
                                    &_collector->_markStack,
                                    &_collector->_revisitStack,
                                    _asynch);
        _collector->_markBitMap.iterate(&cl, my_span.start(), my_span.end());
      } // else nothing to do for this task
    }   // else nothing to do for this task
  }
  // We'd be tempted to assert here that since there are no
  // more tasks left to claim in this space, the global_finger
  // must exceed space->top() and a fortiori space->end(). However,
  // that would not quite be correct because the bumping of
  // global_finger occurs strictly after the claiming of a task,
  // so by the time we reach here the global finger may not yet
  // have been bumped up by the thread that claimed the last
  // task.
  pst->all_tasks_completed();
}

class Par_ConcMarkingClosure: public Par_KlassRememberingOopClosure {
 private:
  MemRegion     _span;
  CMSBitMap*    _bit_map;
  CMSMarkStack* _overflow_stack;
  OopTaskQueue* _work_queue;
 protected:
  DO_OOP_WORK_DEFN
 public:
  Par_ConcMarkingClosure(CMSCollector* collector, OopTaskQueue* work_queue,
                         CMSBitMap* bit_map, CMSMarkStack* overflow_stack,
                         CMSMarkStack* revisit_stack):
    Par_KlassRememberingOopClosure(collector, NULL, revisit_stack),
    _span(_collector->_span),
    _work_queue(work_queue),
    _bit_map(bit_map),
    _overflow_stack(overflow_stack)
  { }
  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p);
  void trim_queue(size_t max);
  void handle_stack_overflow(HeapWord* lost);
};

// Grey object scanning during work stealing phase --
// the salient assumption here is that any references
// that are in these stolen objects being scanned must
// already have been initialized (else they would not have
// been published), so we do not need to check for
// uninitialized objects before pushing here.
void Par_ConcMarkingClosure::do_oop(oop obj) {
  assert(obj->is_oop_or_null(true), "expected an oop or NULL");
  HeapWord* addr = (HeapWord*)obj;
  // Check if oop points into the CMS generation
  // and is not marked
  if (_span.contains(addr) && !_bit_map->isMarked(addr)) {
    // a white object ...
    // If we manage to "claim" the object, by being the
    // first thread to mark it, then we push it on our
    // marking stack
    if (_bit_map->par_mark(addr)) {     // ... now grey
      // push on work queue (grey set)
      bool simulate_overflow = false;
      NOT_PRODUCT(
        if (CMSMarkStackOverflowALot &&
            _collector->simulate_overflow()) {
          // simulate a stack overflow
          simulate_overflow = true;
        }
      )
      if (simulate_overflow ||
          !(_work_queue->push(obj) || _overflow_stack->par_push(obj))) {
        // stack overflow
        if (PrintCMSStatistics != 0) {
          gclog_or_tty->print_cr("CMS marking stack overflow (benign) at "
                                 SIZE_FORMAT, _overflow_stack->capacity());
        }
        // We cannot assert that the overflow stack is full because
        // it may have been emptied since.
        assert(simulate_overflow ||
               _work_queue->size() == _work_queue->max_elems(),
              "Else push should have succeeded");
        handle_stack_overflow(addr);
      }
    } // Else, some other thread got there first
  }
}

void Par_ConcMarkingClosure::do_oop(oop* p)       { Par_ConcMarkingClosure::do_oop_work(p); }
void Par_ConcMarkingClosure::do_oop(narrowOop* p) { Par_ConcMarkingClosure::do_oop_work(p); }

void Par_ConcMarkingClosure::trim_queue(size_t max) {
  while (_work_queue->size() > max) {
    oop new_oop;
    if (_work_queue->pop_local(new_oop)) {
      assert(new_oop->is_oop(), "Should be an oop");
      assert(_bit_map->isMarked((HeapWord*)new_oop), "Grey object");
      assert(_span.contains((HeapWord*)new_oop), "Not in span");
      assert(new_oop->is_parsable(), "Should be parsable");
      new_oop->oop_iterate(this);  // do_oop() above
    }
  }
}

// Upon stack overflow, we discard (part of) the stack,
// remembering the least address amongst those discarded
// in CMSCollector's _restart_address.
void Par_ConcMarkingClosure::handle_stack_overflow(HeapWord* lost) {
  // We need to do this under a mutex to prevent other
  // workers from interfering with the work done below.
  MutexLockerEx ml(_overflow_stack->par_lock(),
                   Mutex::_no_safepoint_check_flag);
  // Remember the least grey address discarded
  HeapWord* ra = (HeapWord*)_overflow_stack->least_value(lost);
  _collector->lower_restart_addr(ra);
  _overflow_stack->reset();  // discard stack contents
  _overflow_stack->expand(); // expand the stack if possible
}


void CMSConcMarkingTask::do_work_steal(int i) {
  OopTaskQueue* work_q = work_queue(i);
  oop obj_to_scan;
  CMSBitMap* bm = &(_collector->_markBitMap);
  CMSMarkStack* ovflw = &(_collector->_markStack);
  CMSMarkStack* revisit = &(_collector->_revisitStack);
  int* seed = _collector->hash_seed(i);
  Par_ConcMarkingClosure cl(_collector, work_q, bm, ovflw, revisit);
  while (true) {
    cl.trim_queue(0);
    assert(work_q->size() == 0, "Should have been emptied above");
    if (get_work_from_overflow_stack(ovflw, work_q)) {
      // Can't assert below because the work obtained from the
      // overflow stack may already have been stolen from us.
      // assert(work_q->size() > 0, "Work from overflow stack");
      continue;
    } else if (task_queues()->steal(i, seed, /* reference */ obj_to_scan)) {
      assert(obj_to_scan->is_oop(), "Should be an oop");
      assert(bm->isMarked((HeapWord*)obj_to_scan), "Grey object");
      obj_to_scan->oop_iterate(&cl);
    } else if (terminator()->offer_termination()) {
      assert(work_q->size() == 0, "Impossible!");
      break;
    }
  }
}

// This is run by the CMS (coordinator) thread.
void CMSConcMarkingTask::coordinator_yield() {
  assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "CMS thread should hold CMS token");
  DEBUG_ONLY(RememberKlassesChecker mux(false);)
  // First give up the locks, then yield, then re-lock
  // We should probably use a constructor/destructor idiom to
  // do this unlock/lock or modify the MutexUnlocker class to
  // serve our purpose. XXX
  assert_lock_strong(_bit_map_lock);
  _bit_map_lock->unlock();
  ConcurrentMarkSweepThread::desynchronize(true);
  ConcurrentMarkSweepThread::acknowledge_yield_request();
  _collector->stopTimer();
  if (PrintCMSStatistics != 0) {
    _collector->incrementYields();
  }
  _collector->icms_wait();

  // It is possible for whichever thread initiated the yield request
  // not to get a chance to wake up and take the bitmap lock between
  // this thread releasing it and reacquiring it. So, while the
  // should_yield() flag is on, let's sleep for a bit to give the
  // other thread a chance to wake up. The limit imposed on the number
  // of iterations is defensive, to avoid any unforseen circumstances
  // putting us into an infinite loop. Since it's always been this
  // (coordinator_yield()) method that was observed to cause the
  // problem, we are using a parameter (CMSCoordinatorYieldSleepCount)
  // which is by default non-zero. For the other seven methods that
  // also perform the yield operation, as are using a different
  // parameter (CMSYieldSleepCount) which is by default zero. This way we
  // can enable the sleeping for those methods too, if necessary.
  // See 6442774.
  //
  // We really need to reconsider the synchronization between the GC
  // thread and the yield-requesting threads in the future and we
  // should really use wait/notify, which is the recommended
  // way of doing this type of interaction. Additionally, we should
  // consolidate the eight methods that do the yield operation and they
  // are almost identical into one for better maintenability and
  // readability. See 6445193.
  //
  // Tony 2006.06.29
  for (unsigned i = 0; i < CMSCoordinatorYieldSleepCount &&
                   ConcurrentMarkSweepThread::should_yield() &&
                   !CMSCollector::foregroundGCIsActive(); ++i) {
    os::sleep(Thread::current(), 1, false);
    ConcurrentMarkSweepThread::acknowledge_yield_request();
  }

  ConcurrentMarkSweepThread::synchronize(true);
  _bit_map_lock->lock_without_safepoint_check();
  _collector->startTimer();
}

bool CMSCollector::do_marking_mt(bool asynch) {
  assert(ConcGCThreads > 0 && conc_workers() != NULL, "precondition");
  // In the future this would be determined ergonomically, based
  // on #cpu's, # active mutator threads (and load), and mutation rate.
  int num_workers = ConcGCThreads;

  CompactibleFreeListSpace* cms_space  = _cmsGen->cmsSpace();
  CompactibleFreeListSpace* perm_space = _permGen->cmsSpace();

  CMSConcMarkingTask tsk(this, cms_space, perm_space,
                         asynch, num_workers /* number requested XXX */,
                         conc_workers(), task_queues());

  // Since the actual number of workers we get may be different
  // from the number we requested above, do we need to do anything different
  // below? In particular, may be we need to subclass the SequantialSubTasksDone
  // class?? XXX
  cms_space ->initialize_sequential_subtasks_for_marking(num_workers);
  perm_space->initialize_sequential_subtasks_for_marking(num_workers);

  // Refs discovery is already non-atomic.
  assert(!ref_processor()->discovery_is_atomic(), "Should be non-atomic");
  // Mutate the Refs discovery so it is MT during the
  // multi-threaded marking phase.
  ReferenceProcessorMTMutator mt(ref_processor(), num_workers > 1);
  DEBUG_ONLY(RememberKlassesChecker cmx(should_unload_classes());)
  conc_workers()->start_task(&tsk);
  while (tsk.yielded()) {
    tsk.coordinator_yield();
    conc_workers()->continue_task(&tsk);
  }
  // If the task was aborted, _restart_addr will be non-NULL
  assert(tsk.completed() || _restart_addr != NULL, "Inconsistency");
  while (_restart_addr != NULL) {
    // XXX For now we do not make use of ABORTED state and have not
    // yet implemented the right abort semantics (even in the original
    // single-threaded CMS case). That needs some more investigation
    // and is deferred for now; see CR# TBF. 07252005YSR. XXX
    assert(!CMSAbortSemantics || tsk.aborted(), "Inconsistency");
    // If _restart_addr is non-NULL, a marking stack overflow
    // occurred; we need to do a fresh marking iteration from the
    // indicated restart address.
    if (_foregroundGCIsActive && asynch) {
      // We may be running into repeated stack overflows, having
      // reached the limit of the stack size, while making very
      // slow forward progress. It may be best to bail out and
      // let the foreground collector do its job.
      // Clear _restart_addr, so that foreground GC
      // works from scratch. This avoids the headache of
      // a "rescan" which would otherwise be needed because
      // of the dirty mod union table & card table.
      _restart_addr = NULL;
      return false;
    }
    // Adjust the task to restart from _restart_addr
    tsk.reset(_restart_addr);
    cms_space ->initialize_sequential_subtasks_for_marking(num_workers,
                  _restart_addr);
    perm_space->initialize_sequential_subtasks_for_marking(num_workers,
                  _restart_addr);
    _restart_addr = NULL;
    // Get the workers going again
    conc_workers()->start_task(&tsk);
    while (tsk.yielded()) {
      tsk.coordinator_yield();
      conc_workers()->continue_task(&tsk);
    }
  }
  assert(tsk.completed(), "Inconsistency");
  assert(tsk.result() == true, "Inconsistency");
  return true;
}

bool CMSCollector::do_marking_st(bool asynch) {
  ResourceMark rm;
  HandleMark   hm;

  MarkFromRootsClosure markFromRootsClosure(this, _span, &_markBitMap,
    &_markStack, &_revisitStack, CMSYield && asynch);
  // the last argument to iterate indicates whether the iteration
  // should be incremental with periodic yields.
  _markBitMap.iterate(&markFromRootsClosure);
  // If _restart_addr is non-NULL, a marking stack overflow
  // occurred; we need to do a fresh iteration from the
  // indicated restart address.
  while (_restart_addr != NULL) {
    if (_foregroundGCIsActive && asynch) {
      // We may be running into repeated stack overflows, having
      // reached the limit of the stack size, while making very
      // slow forward progress. It may be best to bail out and
      // let the foreground collector do its job.
      // Clear _restart_addr, so that foreground GC
      // works from scratch. This avoids the headache of
      // a "rescan" which would otherwise be needed because
      // of the dirty mod union table & card table.
      _restart_addr = NULL;
      return false;  // indicating failure to complete marking
    }
    // Deal with stack overflow:
    // we restart marking from _restart_addr
    HeapWord* ra = _restart_addr;
    markFromRootsClosure.reset(ra);
    _restart_addr = NULL;
    _markBitMap.iterate(&markFromRootsClosure, ra, _span.end());
  }
  return true;
}

void CMSCollector::preclean() {
  check_correct_thread_executing();
  assert(Thread::current()->is_ConcurrentGC_thread(), "Wrong thread");
  verify_work_stacks_empty();
  verify_overflow_empty();
  _abort_preclean = false;
  if (CMSPrecleaningEnabled) {
    _eden_chunk_index = 0;
    size_t used = get_eden_used();
    size_t capacity = get_eden_capacity();
    // Don't start sampling unless we will get sufficiently
    // many samples.
    if (used < (capacity/(CMSScheduleRemarkSamplingRatio * 100)
                * CMSScheduleRemarkEdenPenetration)) {
      _start_sampling = true;
    } else {
      _start_sampling = false;
    }
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    CMSPhaseAccounting pa(this, "preclean", !PrintGCDetails);
    preclean_work(CMSPrecleanRefLists1, CMSPrecleanSurvivors1);
  }
  CMSTokenSync x(true); // is cms thread
  if (CMSPrecleaningEnabled) {
    sample_eden();
    _collectorState = AbortablePreclean;
  } else {
    _collectorState = FinalMarking;
  }
  verify_work_stacks_empty();
  verify_overflow_empty();
}

// Try and schedule the remark such that young gen
// occupancy is CMSScheduleRemarkEdenPenetration %.
void CMSCollector::abortable_preclean() {
  check_correct_thread_executing();
  assert(CMSPrecleaningEnabled,  "Inconsistent control state");
  assert(_collectorState == AbortablePreclean, "Inconsistent control state");

  // If Eden's current occupancy is below this threshold,
  // immediately schedule the remark; else preclean
  // past the next scavenge in an effort to
  // schedule the pause as described avove. By choosing
  // CMSScheduleRemarkEdenSizeThreshold >= max eden size
  // we will never do an actual abortable preclean cycle.
  if (get_eden_used() > CMSScheduleRemarkEdenSizeThreshold) {
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    CMSPhaseAccounting pa(this, "abortable-preclean", !PrintGCDetails);
    // We need more smarts in the abortable preclean
    // loop below to deal with cases where allocation
    // in young gen is very very slow, and our precleaning
    // is running a losing race against a horde of
    // mutators intent on flooding us with CMS updates
    // (dirty cards).
    // One, admittedly dumb, strategy is to give up
    // after a certain number of abortable precleaning loops
    // or after a certain maximum time. We want to make
    // this smarter in the next iteration.
    // XXX FIX ME!!! YSR
    size_t loops = 0, workdone = 0, cumworkdone = 0, waited = 0;
    while (!(should_abort_preclean() ||
             ConcurrentMarkSweepThread::should_terminate())) {
      workdone = preclean_work(CMSPrecleanRefLists2, CMSPrecleanSurvivors2);
      cumworkdone += workdone;
      loops++;
      // Voluntarily terminate abortable preclean phase if we have
      // been at it for too long.
      if ((CMSMaxAbortablePrecleanLoops != 0) &&
          loops >= CMSMaxAbortablePrecleanLoops) {
        if (PrintGCDetails) {
          gclog_or_tty->print(" CMS: abort preclean due to loops ");
        }
        break;
      }
      if (pa.wallclock_millis() > CMSMaxAbortablePrecleanTime) {
        if (PrintGCDetails) {
          gclog_or_tty->print(" CMS: abort preclean due to time ");
        }
        break;
      }
      // If we are doing little work each iteration, we should
      // take a short break.
      if (workdone < CMSAbortablePrecleanMinWorkPerIteration) {
        // Sleep for some time, waiting for work to accumulate
        stopTimer();
        cmsThread()->wait_on_cms_lock(CMSAbortablePrecleanWaitMillis);
        startTimer();
        waited++;
      }
    }
    if (PrintCMSStatistics > 0) {
      gclog_or_tty->print(" [%d iterations, %d waits, %d cards)] ",
                          loops, waited, cumworkdone);
    }
  }
  CMSTokenSync x(true); // is cms thread
  if (_collectorState != Idling) {
    assert(_collectorState == AbortablePreclean,
           "Spontaneous state transition?");
    _collectorState = FinalMarking;
  } // Else, a foreground collection completed this CMS cycle.
  return;
}

// Respond to an Eden sampling opportunity
void CMSCollector::sample_eden() {
  // Make sure a young gc cannot sneak in between our
  // reading and recording of a sample.
  assert(Thread::current()->is_ConcurrentGC_thread(),
         "Only the cms thread may collect Eden samples");
  assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "Should collect samples while holding CMS token");
  if (!_start_sampling) {
    return;
  }
  if (_eden_chunk_array) {
    if (_eden_chunk_index < _eden_chunk_capacity) {
      _eden_chunk_array[_eden_chunk_index] = *_top_addr;   // take sample
      assert(_eden_chunk_array[_eden_chunk_index] <= *_end_addr,
             "Unexpected state of Eden");
      // We'd like to check that what we just sampled is an oop-start address;
      // however, we cannot do that here since the object may not yet have been
      // initialized. So we'll instead do the check when we _use_ this sample
      // later.
      if (_eden_chunk_index == 0 ||
          (pointer_delta(_eden_chunk_array[_eden_chunk_index],
                         _eden_chunk_array[_eden_chunk_index-1])
           >= CMSSamplingGrain)) {
        _eden_chunk_index++;  // commit sample
      }
    }
  }
  if ((_collectorState == AbortablePreclean) && !_abort_preclean) {
    size_t used = get_eden_used();
    size_t capacity = get_eden_capacity();
    assert(used <= capacity, "Unexpected state of Eden");
    if (used >  (capacity/100 * CMSScheduleRemarkEdenPenetration)) {
      _abort_preclean = true;
    }
  }
}


size_t CMSCollector::preclean_work(bool clean_refs, bool clean_survivor) {
  assert(_collectorState == Precleaning ||
         _collectorState == AbortablePreclean, "incorrect state");
  ResourceMark rm;
  HandleMark   hm;
  // Do one pass of scrubbing the discovered reference lists
  // to remove any reference objects with strongly-reachable
  // referents.
  if (clean_refs) {
    ReferenceProcessor* rp = ref_processor();
    CMSPrecleanRefsYieldClosure yield_cl(this);
    assert(rp->span().equals(_span), "Spans should be equal");
    CMSKeepAliveClosure keep_alive(this, _span, &_markBitMap,
                                   &_markStack, &_revisitStack,
                                   true /* preclean */);
    CMSDrainMarkingStackClosure complete_trace(this,
                                   _span, &_markBitMap, &_markStack,
                                   &keep_alive, true /* preclean */);

    // We don't want this step to interfere with a young
    // collection because we don't want to take CPU
    // or memory bandwidth away from the young GC threads
    // (which may be as many as there are CPUs).
    // Note that we don't need to protect ourselves from
    // interference with mutators because they can't
    // manipulate the discovered reference lists nor affect
    // the computed reachability of the referents, the
    // only properties manipulated by the precleaning
    // of these reference lists.
    stopTimer();
    CMSTokenSyncWithLocks x(true /* is cms thread */,
                            bitMapLock());
    startTimer();
    sample_eden();

    // The following will yield to allow foreground
    // collection to proceed promptly. XXX YSR:
    // The code in this method may need further
    // tweaking for better performance and some restructuring
    // for cleaner interfaces.
    rp->preclean_discovered_references(
          rp->is_alive_non_header(), &keep_alive, &complete_trace,
          &yield_cl, should_unload_classes());
  }

  if (clean_survivor) {  // preclean the active survivor space(s)
    assert(_young_gen->kind() == Generation::DefNew ||
           _young_gen->kind() == Generation::ParNew ||
           _young_gen->kind() == Generation::ASParNew,
         "incorrect type for cast");
    DefNewGeneration* dng = (DefNewGeneration*)_young_gen;
    PushAndMarkClosure pam_cl(this, _span, ref_processor(),
                             &_markBitMap, &_modUnionTable,
                             &_markStack, &_revisitStack,
                             true /* precleaning phase */);
    stopTimer();
    CMSTokenSyncWithLocks ts(true /* is cms thread */,
                             bitMapLock());
    startTimer();
    unsigned int before_count =
      GenCollectedHeap::heap()->total_collections();
    SurvivorSpacePrecleanClosure
      sss_cl(this, _span, &_markBitMap, &_markStack,
             &pam_cl, before_count, CMSYield);
    DEBUG_ONLY(RememberKlassesChecker mx(should_unload_classes());)
    dng->from()->object_iterate_careful(&sss_cl);
    dng->to()->object_iterate_careful(&sss_cl);
  }
  MarkRefsIntoAndScanClosure
    mrias_cl(_span, ref_processor(), &_markBitMap, &_modUnionTable,
             &_markStack, &_revisitStack, this, CMSYield,
             true /* precleaning phase */);
  // CAUTION: The following closure has persistent state that may need to
  // be reset upon a decrease in the sequence of addresses it
  // processes.
  ScanMarkedObjectsAgainCarefullyClosure
    smoac_cl(this, _span,
      &_markBitMap, &_markStack, &_revisitStack, &mrias_cl, CMSYield);

  // Preclean dirty cards in ModUnionTable and CardTable using
  // appropriate convergence criterion;
  // repeat CMSPrecleanIter times unless we find that
  // we are losing.
  assert(CMSPrecleanIter < 10, "CMSPrecleanIter is too large");
  assert(CMSPrecleanNumerator < CMSPrecleanDenominator,
         "Bad convergence multiplier");
  assert(CMSPrecleanThreshold >= 100,
         "Unreasonably low CMSPrecleanThreshold");

  size_t numIter, cumNumCards, lastNumCards, curNumCards;
  for (numIter = 0, cumNumCards = lastNumCards = curNumCards = 0;
       numIter < CMSPrecleanIter;
       numIter++, lastNumCards = curNumCards, cumNumCards += curNumCards) {
    curNumCards  = preclean_mod_union_table(_cmsGen, &smoac_cl);
    if (CMSPermGenPrecleaningEnabled) {
      curNumCards  += preclean_mod_union_table(_permGen, &smoac_cl);
    }
    if (Verbose && PrintGCDetails) {
      gclog_or_tty->print(" (modUnionTable: %d cards)", curNumCards);
    }
    // Either there are very few dirty cards, so re-mark
    // pause will be small anyway, or our pre-cleaning isn't
    // that much faster than the rate at which cards are being
    // dirtied, so we might as well stop and re-mark since
    // precleaning won't improve our re-mark time by much.
    if (curNumCards <= CMSPrecleanThreshold ||
        (numIter > 0 &&
         (curNumCards * CMSPrecleanDenominator >
         lastNumCards * CMSPrecleanNumerator))) {
      numIter++;
      cumNumCards += curNumCards;
      break;
    }
  }
  curNumCards = preclean_card_table(_cmsGen, &smoac_cl);
  if (CMSPermGenPrecleaningEnabled) {
    curNumCards += preclean_card_table(_permGen, &smoac_cl);
  }
  cumNumCards += curNumCards;
  if (PrintGCDetails && PrintCMSStatistics != 0) {
    gclog_or_tty->print_cr(" (cardTable: %d cards, re-scanned %d cards, %d iterations)",
                  curNumCards, cumNumCards, numIter);
  }
  return cumNumCards;   // as a measure of useful work done
}

// PRECLEANING NOTES:
// Precleaning involves:
// . reading the bits of the modUnionTable and clearing the set bits.
// . For the cards corresponding to the set bits, we scan the
//   objects on those cards. This means we need the free_list_lock
//   so that we can safely iterate over the CMS space when scanning
//   for oops.
// . When we scan the objects, we'll be both reading and setting
//   marks in the marking bit map, so we'll need the marking bit map.
// . For protecting _collector_state transitions, we take the CGC_lock.
//   Note that any races in the reading of of card table entries by the
//   CMS thread on the one hand and the clearing of those entries by the
//   VM thread or the setting of those entries by the mutator threads on the
//   other are quite benign. However, for efficiency it makes sense to keep
//   the VM thread from racing with the CMS thread while the latter is
//   dirty card info to the modUnionTable. We therefore also use the
//   CGC_lock to protect the reading of the card table and the mod union
//   table by the CM thread.
// . We run concurrently with mutator updates, so scanning
//   needs to be done carefully  -- we should not try to scan
//   potentially uninitialized objects.
//
// Locking strategy: While holding the CGC_lock, we scan over and
// reset a maximal dirty range of the mod union / card tables, then lock
// the free_list_lock and bitmap lock to do a full marking, then
// release these locks; and repeat the cycle. This allows for a
// certain amount of fairness in the sharing of these locks between
// the CMS collector on the one hand, and the VM thread and the
// mutators on the other.

// NOTE: preclean_mod_union_table() and preclean_card_table()
// further below are largely identical; if you need to modify
// one of these methods, please check the other method too.

size_t CMSCollector::preclean_mod_union_table(
  ConcurrentMarkSweepGeneration* gen,
  ScanMarkedObjectsAgainCarefullyClosure* cl) {
  verify_work_stacks_empty();
  verify_overflow_empty();

  // Turn off checking for this method but turn it back on
  // selectively.  There are yield points in this method
  // but it is difficult to turn the checking off just around
  // the yield points.  It is simpler to selectively turn
  // it on.
  DEBUG_ONLY(RememberKlassesChecker mux(false);)

  // strategy: starting with the first card, accumulate contiguous
  // ranges of dirty cards; clear these cards, then scan the region
  // covered by these cards.

  // Since all of the MUT is committed ahead, we can just use
  // that, in case the generations expand while we are precleaning.
  // It might also be fine to just use the committed part of the
  // generation, but we might potentially miss cards when the
  // generation is rapidly expanding while we are in the midst
  // of precleaning.
  HeapWord* startAddr = gen->reserved().start();
  HeapWord* endAddr   = gen->reserved().end();

  cl->setFreelistLock(gen->freelistLock());   // needed for yielding

  size_t numDirtyCards, cumNumDirtyCards;
  HeapWord *nextAddr, *lastAddr;
  for (cumNumDirtyCards = numDirtyCards = 0,
       nextAddr = lastAddr = startAddr;
       nextAddr < endAddr;
       nextAddr = lastAddr, cumNumDirtyCards += numDirtyCards) {

    ResourceMark rm;
    HandleMark   hm;

    MemRegion dirtyRegion;
    {
      stopTimer();
      // Potential yield point
      CMSTokenSync ts(true);
      startTimer();
      sample_eden();
      // Get dirty region starting at nextOffset (inclusive),
      // simultaneously clearing it.
      dirtyRegion =
        _modUnionTable.getAndClearMarkedRegion(nextAddr, endAddr);
      assert(dirtyRegion.start() >= nextAddr,
             "returned region inconsistent?");
    }
    // Remember where the next search should begin.
    // The returned region (if non-empty) is a right open interval,
    // so lastOffset is obtained from the right end of that
    // interval.
    lastAddr = dirtyRegion.end();
    // Should do something more transparent and less hacky XXX
    numDirtyCards =
      _modUnionTable.heapWordDiffToOffsetDiff(dirtyRegion.word_size());

    // We'll scan the cards in the dirty region (with periodic
    // yields for foreground GC as needed).
    if (!dirtyRegion.is_empty()) {
      assert(numDirtyCards > 0, "consistency check");
      HeapWord* stop_point = NULL;
      stopTimer();
      // Potential yield point
      CMSTokenSyncWithLocks ts(true, gen->freelistLock(),
                               bitMapLock());
      startTimer();
      {
        verify_work_stacks_empty();
        verify_overflow_empty();
        sample_eden();
        DEBUG_ONLY(RememberKlassesChecker mx(should_unload_classes());)
        stop_point =
          gen->cmsSpace()->object_iterate_careful_m(dirtyRegion, cl);
      }
      if (stop_point != NULL) {
        // The careful iteration stopped early either because it found an
        // uninitialized object, or because we were in the midst of an
        // "abortable preclean", which should now be aborted. Redirty
        // the bits corresponding to the partially-scanned or unscanned
        // cards. We'll either restart at the next block boundary or
        // abort the preclean.
        assert((CMSPermGenPrecleaningEnabled && (gen == _permGen)) ||
               (_collectorState == AbortablePreclean && should_abort_preclean()),
               "Unparsable objects should only be in perm gen.");
        _modUnionTable.mark_range(MemRegion(stop_point, dirtyRegion.end()));
        if (should_abort_preclean()) {
          break; // out of preclean loop
        } else {
          // Compute the next address at which preclean should pick up;
          // might need bitMapLock in order to read P-bits.
          lastAddr = next_card_start_after_block(stop_point);
        }
      }
    } else {
      assert(lastAddr == endAddr, "consistency check");
      assert(numDirtyCards == 0, "consistency check");
      break;
    }
  }
  verify_work_stacks_empty();
  verify_overflow_empty();
  return cumNumDirtyCards;
}

// NOTE: preclean_mod_union_table() above and preclean_card_table()
// below are largely identical; if you need to modify
// one of these methods, please check the other method too.

size_t CMSCollector::preclean_card_table(ConcurrentMarkSweepGeneration* gen,
  ScanMarkedObjectsAgainCarefullyClosure* cl) {
  // strategy: it's similar to precleamModUnionTable above, in that
  // we accumulate contiguous ranges of dirty cards, mark these cards
  // precleaned, then scan the region covered by these cards.
  HeapWord* endAddr   = (HeapWord*)(gen->_virtual_space.high());
  HeapWord* startAddr = (HeapWord*)(gen->_virtual_space.low());

  cl->setFreelistLock(gen->freelistLock());   // needed for yielding

  size_t numDirtyCards, cumNumDirtyCards;
  HeapWord *lastAddr, *nextAddr;

  for (cumNumDirtyCards = numDirtyCards = 0,
       nextAddr = lastAddr = startAddr;
       nextAddr < endAddr;
       nextAddr = lastAddr, cumNumDirtyCards += numDirtyCards) {

    ResourceMark rm;
    HandleMark   hm;

    MemRegion dirtyRegion;
    {
      // See comments in "Precleaning notes" above on why we
      // do this locking. XXX Could the locking overheads be
      // too high when dirty cards are sparse? [I don't think so.]
      stopTimer();
      CMSTokenSync x(true); // is cms thread
      startTimer();
      sample_eden();
      // Get and clear dirty region from card table
      dirtyRegion = _ct->ct_bs()->dirty_card_range_after_reset(
                                    MemRegion(nextAddr, endAddr),
                                    true,
                                    CardTableModRefBS::precleaned_card_val());

      assert(dirtyRegion.start() >= nextAddr,
             "returned region inconsistent?");
    }
    lastAddr = dirtyRegion.end();
    numDirtyCards =
      dirtyRegion.word_size()/CardTableModRefBS::card_size_in_words;

    if (!dirtyRegion.is_empty()) {
      stopTimer();
      CMSTokenSyncWithLocks ts(true, gen->freelistLock(), bitMapLock());
      startTimer();
      sample_eden();
      verify_work_stacks_empty();
      verify_overflow_empty();
      DEBUG_ONLY(RememberKlassesChecker mx(should_unload_classes());)
      HeapWord* stop_point =
        gen->cmsSpace()->object_iterate_careful_m(dirtyRegion, cl);
      if (stop_point != NULL) {
        // The careful iteration stopped early because it found an
        // uninitialized object.  Redirty the bits corresponding to the
        // partially-scanned or unscanned cards, and start again at the
        // next block boundary.
        assert(CMSPermGenPrecleaningEnabled ||
               (_collectorState == AbortablePreclean && should_abort_preclean()),
               "Unparsable objects should only be in perm gen.");
        _ct->ct_bs()->invalidate(MemRegion(stop_point, dirtyRegion.end()));
        if (should_abort_preclean()) {
          break; // out of preclean loop
        } else {
          // Compute the next address at which preclean should pick up.
          lastAddr = next_card_start_after_block(stop_point);
        }
      }
    } else {
      break;
    }
  }
  verify_work_stacks_empty();
  verify_overflow_empty();
  return cumNumDirtyCards;
}

void CMSCollector::checkpointRootsFinal(bool asynch,
  bool clear_all_soft_refs, bool init_mark_was_synchronous) {
  assert(_collectorState == FinalMarking, "incorrect state transition?");
  check_correct_thread_executing();
  // world is stopped at this checkpoint
  assert(SafepointSynchronize::is_at_safepoint(),
         "world should be stopped");
  TraceCMSMemoryManagerStats tms(_collectorState);
  verify_work_stacks_empty();
  verify_overflow_empty();

  SpecializationStats::clear();
  if (PrintGCDetails) {
    gclog_or_tty->print("[YG occupancy: "SIZE_FORMAT" K ("SIZE_FORMAT" K)]",
                        _young_gen->used() / K,
                        _young_gen->capacity() / K);
  }
  if (asynch) {
    if (CMSScavengeBeforeRemark) {
      GenCollectedHeap* gch = GenCollectedHeap::heap();
      // Temporarily set flag to false, GCH->do_collection will
      // expect it to be false and set to true
      FlagSetting fl(gch->_is_gc_active, false);
      NOT_PRODUCT(TraceTime t("Scavenge-Before-Remark",
        PrintGCDetails && Verbose, true, gclog_or_tty);)
      int level = _cmsGen->level() - 1;
      if (level >= 0) {
        gch->do_collection(true,        // full (i.e. force, see below)
                           false,       // !clear_all_soft_refs
                           0,           // size
                           false,       // is_tlab
                           level        // max_level
                          );
      }
    }
    FreelistLocker x(this);
    MutexLockerEx y(bitMapLock(),
                    Mutex::_no_safepoint_check_flag);
    assert(!init_mark_was_synchronous, "but that's impossible!");
    checkpointRootsFinalWork(asynch, clear_all_soft_refs, false);
  } else {
    // already have all the locks
    checkpointRootsFinalWork(asynch, clear_all_soft_refs,
                             init_mark_was_synchronous);
  }
  verify_work_stacks_empty();
  verify_overflow_empty();
  SpecializationStats::print();
}

void CMSCollector::checkpointRootsFinalWork(bool asynch,
  bool clear_all_soft_refs, bool init_mark_was_synchronous) {

  NOT_PRODUCT(TraceTime tr("checkpointRootsFinalWork", PrintGCDetails, false, gclog_or_tty);)

  assert(haveFreelistLocks(), "must have free list locks");
  assert_lock_strong(bitMapLock());

  if (UseAdaptiveSizePolicy) {
    size_policy()->checkpoint_roots_final_begin();
  }

  ResourceMark rm;
  HandleMark   hm;

  GenCollectedHeap* gch = GenCollectedHeap::heap();

  if (should_unload_classes()) {
    CodeCache::gc_prologue();
  }
  assert(haveFreelistLocks(), "must have free list locks");
  assert_lock_strong(bitMapLock());

  DEBUG_ONLY(RememberKlassesChecker fmx(should_unload_classes());)
  if (!init_mark_was_synchronous) {
    // We might assume that we need not fill TLAB's when
    // CMSScavengeBeforeRemark is set, because we may have just done
    // a scavenge which would have filled all TLAB's -- and besides
    // Eden would be empty. This however may not always be the case --
    // for instance although we asked for a scavenge, it may not have
    // happened because of a JNI critical section. We probably need
    // a policy for deciding whether we can in that case wait until
    // the critical section releases and then do the remark following
    // the scavenge, and skip it here. In the absence of that policy,
    // or of an indication of whether the scavenge did indeed occur,
    // we cannot rely on TLAB's having been filled and must do
    // so here just in case a scavenge did not happen.
    gch->ensure_parsability(false);  // fill TLAB's, but no need to retire them
    // Update the saved marks which may affect the root scans.
    gch->save_marks();

    {
      COMPILER2_PRESENT(DerivedPointerTableDeactivate dpt_deact;)

      // Note on the role of the mod union table:
      // Since the marker in "markFromRoots" marks concurrently with
      // mutators, it is possible for some reachable objects not to have been
      // scanned. For instance, an only reference to an object A was
      // placed in object B after the marker scanned B. Unless B is rescanned,
      // A would be collected. Such updates to references in marked objects
      // are detected via the mod union table which is the set of all cards
      // dirtied since the first checkpoint in this GC cycle and prior to
      // the most recent young generation GC, minus those cleaned up by the
      // concurrent precleaning.
      if (CMSParallelRemarkEnabled && ParallelGCThreads > 0) {
        TraceTime t("Rescan (parallel) ", PrintGCDetails, false, gclog_or_tty);
        do_remark_parallel();
      } else {
        TraceTime t("Rescan (non-parallel) ", PrintGCDetails, false,
                    gclog_or_tty);
        do_remark_non_parallel();
      }
    }
  } else {
    assert(!asynch, "Can't have init_mark_was_synchronous in asynch mode");
    // The initial mark was stop-world, so there's no rescanning to
    // do; go straight on to the next step below.
  }
  verify_work_stacks_empty();
  verify_overflow_empty();

  {
    NOT_PRODUCT(TraceTime ts("refProcessingWork", PrintGCDetails, false, gclog_or_tty);)
    refProcessingWork(asynch, clear_all_soft_refs);
  }
  verify_work_stacks_empty();
  verify_overflow_empty();

  if (should_unload_classes()) {
    CodeCache::gc_epilogue();
  }

  // If we encountered any (marking stack / work queue) overflow
  // events during the current CMS cycle, take appropriate
  // remedial measures, where possible, so as to try and avoid
  // recurrence of that condition.
  assert(_markStack.isEmpty(), "No grey objects");
  size_t ser_ovflw = _ser_pmc_remark_ovflw + _ser_pmc_preclean_ovflw +
                     _ser_kac_ovflw        + _ser_kac_preclean_ovflw;
  if (ser_ovflw > 0) {
    if (PrintCMSStatistics != 0) {
      gclog_or_tty->print_cr("Marking stack overflow (benign) "
        "(pmc_pc="SIZE_FORMAT", pmc_rm="SIZE_FORMAT", kac="SIZE_FORMAT
        ", kac_preclean="SIZE_FORMAT")",
        _ser_pmc_preclean_ovflw, _ser_pmc_remark_ovflw,
        _ser_kac_ovflw, _ser_kac_preclean_ovflw);
    }
    _markStack.expand();
    _ser_pmc_remark_ovflw = 0;
    _ser_pmc_preclean_ovflw = 0;
    _ser_kac_preclean_ovflw = 0;
    _ser_kac_ovflw = 0;
  }
  if (_par_pmc_remark_ovflw > 0 || _par_kac_ovflw > 0) {
    if (PrintCMSStatistics != 0) {
      gclog_or_tty->print_cr("Work queue overflow (benign) "
        "(pmc_rm="SIZE_FORMAT", kac="SIZE_FORMAT")",
        _par_pmc_remark_ovflw, _par_kac_ovflw);
    }
    _par_pmc_remark_ovflw = 0;
    _par_kac_ovflw = 0;
  }
  if (PrintCMSStatistics != 0) {
     if (_markStack._hit_limit > 0) {
       gclog_or_tty->print_cr(" (benign) Hit max stack size limit ("SIZE_FORMAT")",
                              _markStack._hit_limit);
     }
     if (_markStack._failed_double > 0) {
       gclog_or_tty->print_cr(" (benign) Failed stack doubling ("SIZE_FORMAT"),"
                              " current capacity "SIZE_FORMAT,
                              _markStack._failed_double,
                              _markStack.capacity());
     }
  }
  _markStack._hit_limit = 0;
  _markStack._failed_double = 0;

  // Check that all the klasses have been checked
  assert(_revisitStack.isEmpty(), "Not all klasses revisited");

  if ((VerifyAfterGC || VerifyDuringGC) &&
      GenCollectedHeap::heap()->total_collections() >= VerifyGCStartAt) {
    verify_after_remark();
  }

  // Change under the freelistLocks.
  _collectorState = Sweeping;
  // Call isAllClear() under bitMapLock
  assert(_modUnionTable.isAllClear(), "Should be clear by end of the"
    " final marking");
  if (UseAdaptiveSizePolicy) {
    size_policy()->checkpoint_roots_final_end(gch->gc_cause());
  }
}

// Parallel remark task
class CMSParRemarkTask: public AbstractGangTask {
  CMSCollector* _collector;
  WorkGang*     _workers;
  int           _n_workers;
  CompactibleFreeListSpace* _cms_space;
  CompactibleFreeListSpace* _perm_space;

  // The per-thread work queues, available here for stealing.
  OopTaskQueueSet*       _task_queues;
  ParallelTaskTerminator _term;

 public:
  CMSParRemarkTask(CMSCollector* collector,
                   CompactibleFreeListSpace* cms_space,
                   CompactibleFreeListSpace* perm_space,
                   int n_workers, WorkGang* workers,
                   OopTaskQueueSet* task_queues):
    AbstractGangTask("Rescan roots and grey objects in parallel"),
    _collector(collector),
    _cms_space(cms_space), _perm_space(perm_space),
    _n_workers(n_workers),
    _workers(workers),
    _task_queues(task_queues),
    _term(workers->total_workers(), task_queues) { }

  OopTaskQueueSet* task_queues() { return _task_queues; }

  OopTaskQueue* work_queue(int i) { return task_queues()->queue(i); }

  ParallelTaskTerminator* terminator() { return &_term; }

  void work(int i);

 private:
  // Work method in support of parallel rescan ... of young gen spaces
  void do_young_space_rescan(int i, Par_MarkRefsIntoAndScanClosure* cl,
                             ContiguousSpace* space,
                             HeapWord** chunk_array, size_t chunk_top);

  // ... of  dirty cards in old space
  void do_dirty_card_rescan_tasks(CompactibleFreeListSpace* sp, int i,
                                  Par_MarkRefsIntoAndScanClosure* cl);

  // ... work stealing for the above
  void do_work_steal(int i, Par_MarkRefsIntoAndScanClosure* cl, int* seed);
};

void CMSParRemarkTask::work(int i) {
  elapsedTimer _timer;
  ResourceMark rm;
  HandleMark   hm;

  // ---------- rescan from roots --------------
  _timer.start();
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  Par_MarkRefsIntoAndScanClosure par_mrias_cl(_collector,
    _collector->_span, _collector->ref_processor(),
    &(_collector->_markBitMap),
    work_queue(i), &(_collector->_revisitStack));

  // Rescan young gen roots first since these are likely
  // coarsely partitioned and may, on that account, constitute
  // the critical path; thus, it's best to start off that
  // work first.
  // ---------- young gen roots --------------
  {
    DefNewGeneration* dng = _collector->_young_gen->as_DefNewGeneration();
    EdenSpace* eden_space = dng->eden();
    ContiguousSpace* from_space = dng->from();
    ContiguousSpace* to_space   = dng->to();

    HeapWord** eca = _collector->_eden_chunk_array;
    size_t     ect = _collector->_eden_chunk_index;
    HeapWord** sca = _collector->_survivor_chunk_array;
    size_t     sct = _collector->_survivor_chunk_index;

    assert(ect <= _collector->_eden_chunk_capacity, "out of bounds");
    assert(sct <= _collector->_survivor_chunk_capacity, "out of bounds");

    do_young_space_rescan(i, &par_mrias_cl, to_space, NULL, 0);
    do_young_space_rescan(i, &par_mrias_cl, from_space, sca, sct);
    do_young_space_rescan(i, &par_mrias_cl, eden_space, eca, ect);

    _timer.stop();
    if (PrintCMSStatistics != 0) {
      gclog_or_tty->print_cr(
        "Finished young gen rescan work in %dth thread: %3.3f sec",
        i, _timer.seconds());
    }
  }

  // ---------- remaining roots --------------
  _timer.reset();
  _timer.start();
  gch->gen_process_strong_roots(_collector->_cmsGen->level(),
                                false,     // yg was scanned above
                                false,     // this is parallel code
                                true,      // collecting perm gen
                                SharedHeap::ScanningOption(_collector->CMSCollector::roots_scanning_options()),
                                &par_mrias_cl,
                                true,   // walk all of code cache if (so & SO_CodeCache)
                                NULL);
  assert(_collector->should_unload_classes()
         || (_collector->CMSCollector::roots_scanning_options() & SharedHeap::SO_CodeCache),
         "if we didn't scan the code cache, we have to be ready to drop nmethods with expired weak oops");
  _timer.stop();
  if (PrintCMSStatistics != 0) {
    gclog_or_tty->print_cr(
      "Finished remaining root rescan work in %dth thread: %3.3f sec",
      i, _timer.seconds());
  }

  // ---------- rescan dirty cards ------------
  _timer.reset();
  _timer.start();

  // Do the rescan tasks for each of the two spaces
  // (cms_space and perm_space) in turn.
  do_dirty_card_rescan_tasks(_cms_space, i, &par_mrias_cl);
  do_dirty_card_rescan_tasks(_perm_space, i, &par_mrias_cl);
  _timer.stop();
  if (PrintCMSStatistics != 0) {
    gclog_or_tty->print_cr(
      "Finished dirty card rescan work in %dth thread: %3.3f sec",
      i, _timer.seconds());
  }

  // ---------- steal work from other threads ...
  // ---------- ... and drain overflow list.
  _timer.reset();
  _timer.start();
  do_work_steal(i, &par_mrias_cl, _collector->hash_seed(i));
  _timer.stop();
  if (PrintCMSStatistics != 0) {
    gclog_or_tty->print_cr(
      "Finished work stealing in %dth thread: %3.3f sec",
      i, _timer.seconds());
  }
}

void
CMSParRemarkTask::do_young_space_rescan(int i,
  Par_MarkRefsIntoAndScanClosure* cl, ContiguousSpace* space,
  HeapWord** chunk_array, size_t chunk_top) {
  // Until all tasks completed:
  // . claim an unclaimed task
  // . compute region boundaries corresponding to task claimed
  //   using chunk_array
  // . par_oop_iterate(cl) over that region

  ResourceMark rm;
  HandleMark   hm;

  SequentialSubTasksDone* pst = space->par_seq_tasks();
  assert(pst->valid(), "Uninitialized use?");

  int nth_task = 0;
  int n_tasks  = pst->n_tasks();

  HeapWord *start, *end;
  while (!pst->is_task_claimed(/* reference */ nth_task)) {
    // We claimed task # nth_task; compute its boundaries.
    if (chunk_top == 0) {  // no samples were taken
      assert(nth_task == 0 && n_tasks == 1, "Can have only 1 EdenSpace task");
      start = space->bottom();
      end   = space->top();
    } else if (nth_task == 0) {
      start = space->bottom();
      end   = chunk_array[nth_task];
    } else if (nth_task < (jint)chunk_top) {
      assert(nth_task >= 1, "Control point invariant");
      start = chunk_array[nth_task - 1];
      end   = chunk_array[nth_task];
    } else {
      assert(nth_task == (jint)chunk_top, "Control point invariant");
      start = chunk_array[chunk_top - 1];
      end   = space->top();
    }
    MemRegion mr(start, end);
    // Verify that mr is in space
    assert(mr.is_empty() || space->used_region().contains(mr),
           "Should be in space");
    // Verify that "start" is an object boundary
    assert(mr.is_empty() || oop(mr.start())->is_oop(),
           "Should be an oop");
    space->par_oop_iterate(mr, cl);
  }
  pst->all_tasks_completed();
}

void
CMSParRemarkTask::do_dirty_card_rescan_tasks(
  CompactibleFreeListSpace* sp, int i,
  Par_MarkRefsIntoAndScanClosure* cl) {
  // Until all tasks completed:
  // . claim an unclaimed task
  // . compute region boundaries corresponding to task claimed
  // . transfer dirty bits ct->mut for that region
  // . apply rescanclosure to dirty mut bits for that region

  ResourceMark rm;
  HandleMark   hm;

  OopTaskQueue* work_q = work_queue(i);
  ModUnionClosure modUnionClosure(&(_collector->_modUnionTable));
  // CAUTION! CAUTION! CAUTION! CAUTION! CAUTION! CAUTION! CAUTION!
  // CAUTION: This closure has state that persists across calls to
  // the work method dirty_range_iterate_clear() in that it has
  // imbedded in it a (subtype of) UpwardsObjectClosure. The
  // use of that state in the imbedded UpwardsObjectClosure instance
  // assumes that the cards are always iterated (even if in parallel
  // by several threads) in monotonically increasing order per each
  // thread. This is true of the implementation below which picks
  // card ranges (chunks) in monotonically increasing order globally
  // and, a-fortiori, in monotonically increasing order per thread
  // (the latter order being a subsequence of the former).
  // If the work code below is ever reorganized into a more chaotic
  // work-partitioning form than the current "sequential tasks"
  // paradigm, the use of that persistent state will have to be
  // revisited and modified appropriately. See also related
  // bug 4756801 work on which should examine this code to make
  // sure that the changes there do not run counter to the
  // assumptions made here and necessary for correctness and
  // efficiency. Note also that this code might yield inefficient
  // behaviour in the case of very large objects that span one or
  // more work chunks. Such objects would potentially be scanned
  // several times redundantly. Work on 4756801 should try and
  // address that performance anomaly if at all possible. XXX
  MemRegion  full_span  = _collector->_span;
  CMSBitMap* bm    = &(_collector->_markBitMap);     // shared
  CMSMarkStack* rs = &(_collector->_revisitStack);   // shared
  MarkFromDirtyCardsClosure
    greyRescanClosure(_collector, full_span, // entire span of interest
                      sp, bm, work_q, rs, cl);

  SequentialSubTasksDone* pst = sp->conc_par_seq_tasks();
  assert(pst->valid(), "Uninitialized use?");
  int nth_task = 0;
  const int alignment = CardTableModRefBS::card_size * BitsPerWord;
  MemRegion span = sp->used_region();
  HeapWord* start_addr = span.start();
  HeapWord* end_addr = (HeapWord*)round_to((intptr_t)span.end(),
                                           alignment);
  const size_t chunk_size = sp->rescan_task_size(); // in HeapWord units
  assert((HeapWord*)round_to((intptr_t)start_addr, alignment) ==
         start_addr, "Check alignment");
  assert((size_t)round_to((intptr_t)chunk_size, alignment) ==
         chunk_size, "Check alignment");

  while (!pst->is_task_claimed(/* reference */ nth_task)) {
    // Having claimed the nth_task, compute corresponding mem-region,
    // which is a-fortiori aligned correctly (i.e. at a MUT bopundary).
    // The alignment restriction ensures that we do not need any
    // synchronization with other gang-workers while setting or
    // clearing bits in thus chunk of the MUT.
    MemRegion this_span = MemRegion(start_addr + nth_task*chunk_size,
                                    start_addr + (nth_task+1)*chunk_size);
    // The last chunk's end might be way beyond end of the
    // used region. In that case pull back appropriately.
    if (this_span.end() > end_addr) {
      this_span.set_end(end_addr);
      assert(!this_span.is_empty(), "Program logic (calculation of n_tasks)");
    }
    // Iterate over the dirty cards covering this chunk, marking them
    // precleaned, and setting the corresponding bits in the mod union
    // table. Since we have been careful to partition at Card and MUT-word
    // boundaries no synchronization is needed between parallel threads.
    _collector->_ct->ct_bs()->dirty_card_iterate(this_span,
                                                 &modUnionClosure);

    // Having transferred these marks into the modUnionTable,
    // rescan the marked objects on the dirty cards in the modUnionTable.
    // Even if this is at a synchronous collection, the initial marking
    // may have been done during an asynchronous collection so there
    // may be dirty bits in the mod-union table.
    _collector->_modUnionTable.dirty_range_iterate_clear(
                  this_span, &greyRescanClosure);
    _collector->_modUnionTable.verifyNoOneBitsInRange(
                                 this_span.start(),
                                 this_span.end());
  }
  pst->all_tasks_completed();  // declare that i am done
}

// . see if we can share work_queues with ParNew? XXX
void
CMSParRemarkTask::do_work_steal(int i, Par_MarkRefsIntoAndScanClosure* cl,
                                int* seed) {
  OopTaskQueue* work_q = work_queue(i);
  NOT_PRODUCT(int num_steals = 0;)
  oop obj_to_scan;
  CMSBitMap* bm = &(_collector->_markBitMap);

  while (true) {
    // Completely finish any left over work from (an) earlier round(s)
    cl->trim_queue(0);
    size_t num_from_overflow_list = MIN2((size_t)(work_q->max_elems() - work_q->size())/4,
                                         (size_t)ParGCDesiredObjsFromOverflowList);
    // Now check if there's any work in the overflow list
    if (_collector->par_take_from_overflow_list(num_from_overflow_list,
                                                work_q)) {
      // found something in global overflow list;
      // not yet ready to go stealing work from others.
      // We'd like to assert(work_q->size() != 0, ...)
      // because we just took work from the overflow list,
      // but of course we can't since all of that could have
      // been already stolen from us.
      // "He giveth and He taketh away."
      continue;
    }
    // Verify that we have no work before we resort to stealing
    assert(work_q->size() == 0, "Have work, shouldn't steal");
    // Try to steal from other queues that have work
    if (task_queues()->steal(i, seed, /* reference */ obj_to_scan)) {
      NOT_PRODUCT(num_steals++;)
      assert(obj_to_scan->is_oop(), "Oops, not an oop!");
      assert(bm->isMarked((HeapWord*)obj_to_scan), "Stole an unmarked oop?");
      // Do scanning work
      obj_to_scan->oop_iterate(cl);
      // Loop around, finish this work, and try to steal some more
    } else if (terminator()->offer_termination()) {
        break;  // nirvana from the infinite cycle
    }
  }
  NOT_PRODUCT(
    if (PrintCMSStatistics != 0) {
      gclog_or_tty->print("\n\t(%d: stole %d oops)", i, num_steals);
    }
  )
  assert(work_q->size() == 0 && _collector->overflow_list_is_empty(),
         "Else our work is not yet done");
}

// Return a thread-local PLAB recording array, as appropriate.
void* CMSCollector::get_data_recorder(int thr_num) {
  if (_survivor_plab_array != NULL &&
      (CMSPLABRecordAlways ||
       (_collectorState > Marking && _collectorState < FinalMarking))) {
    assert(thr_num < (int)ParallelGCThreads, "thr_num is out of bounds");
    ChunkArray* ca = &_survivor_plab_array[thr_num];
    ca->reset();   // clear it so that fresh data is recorded
    return (void*) ca;
  } else {
    return NULL;
  }
}

// Reset all the thread-local PLAB recording arrays
void CMSCollector::reset_survivor_plab_arrays() {
  for (uint i = 0; i < ParallelGCThreads; i++) {
    _survivor_plab_array[i].reset();
  }
}

// Merge the per-thread plab arrays into the global survivor chunk
// array which will provide the partitioning of the survivor space
// for CMS rescan.
void CMSCollector::merge_survivor_plab_arrays(ContiguousSpace* surv) {
  assert(_survivor_plab_array  != NULL, "Error");
  assert(_survivor_chunk_array != NULL, "Error");
  assert(_collectorState == FinalMarking, "Error");
  for (uint j = 0; j < ParallelGCThreads; j++) {
    _cursor[j] = 0;
  }
  HeapWord* top = surv->top();
  size_t i;
  for (i = 0; i < _survivor_chunk_capacity; i++) {  // all sca entries
    HeapWord* min_val = top;          // Higher than any PLAB address
    uint      min_tid = 0;            // position of min_val this round
    for (uint j = 0; j < ParallelGCThreads; j++) {
      ChunkArray* cur_sca = &_survivor_plab_array[j];
      if (_cursor[j] == cur_sca->end()) {
        continue;
      }
      assert(_cursor[j] < cur_sca->end(), "ctl pt invariant");
      HeapWord* cur_val = cur_sca->nth(_cursor[j]);
      assert(surv->used_region().contains(cur_val), "Out of bounds value");
      if (cur_val < min_val) {
        min_tid = j;
        min_val = cur_val;
      } else {
        assert(cur_val < top, "All recorded addresses should be less");
      }
    }
    // At this point min_val and min_tid are respectively
    // the least address in _survivor_plab_array[j]->nth(_cursor[j])
    // and the thread (j) that witnesses that address.
    // We record this address in the _survivor_chunk_array[i]
    // and increment _cursor[min_tid] prior to the next round i.
    if (min_val == top) {
      break;
    }
    _survivor_chunk_array[i] = min_val;
    _cursor[min_tid]++;
  }
  // We are all done; record the size of the _survivor_chunk_array
  _survivor_chunk_index = i; // exclusive: [0, i)
  if (PrintCMSStatistics > 0) {
    gclog_or_tty->print(" (Survivor:" SIZE_FORMAT "chunks) ", i);
  }
  // Verify that we used up all the recorded entries
  #ifdef ASSERT
    size_t total = 0;
    for (uint j = 0; j < ParallelGCThreads; j++) {
      assert(_cursor[j] == _survivor_plab_array[j].end(), "Ctl pt invariant");
      total += _cursor[j];
    }
    assert(total == _survivor_chunk_index, "Ctl Pt Invariant");
    // Check that the merged array is in sorted order
    if (total > 0) {
      for (size_t i = 0; i < total - 1; i++) {
        if (PrintCMSStatistics > 0) {
          gclog_or_tty->print(" (chunk" SIZE_FORMAT ":" INTPTR_FORMAT ") ",
                              i, _survivor_chunk_array[i]);
        }
        assert(_survivor_chunk_array[i] < _survivor_chunk_array[i+1],
               "Not sorted");
      }
    }
  #endif // ASSERT
}

// Set up the space's par_seq_tasks structure for work claiming
// for parallel rescan of young gen.
// See ParRescanTask where this is currently used.
void
CMSCollector::
initialize_sequential_subtasks_for_young_gen_rescan(int n_threads) {
  assert(n_threads > 0, "Unexpected n_threads argument");
  DefNewGeneration* dng = (DefNewGeneration*)_young_gen;

  // Eden space
  {
    SequentialSubTasksDone* pst = dng->eden()->par_seq_tasks();
    assert(!pst->valid(), "Clobbering existing data?");
    // Each valid entry in [0, _eden_chunk_index) represents a task.
    size_t n_tasks = _eden_chunk_index + 1;
    assert(n_tasks == 1 || _eden_chunk_array != NULL, "Error");
    pst->set_par_threads(n_threads);
    pst->set_n_tasks((int)n_tasks);
  }

  // Merge the survivor plab arrays into _survivor_chunk_array
  if (_survivor_plab_array != NULL) {
    merge_survivor_plab_arrays(dng->from());
  } else {
    assert(_survivor_chunk_index == 0, "Error");
  }

  // To space
  {
    SequentialSubTasksDone* pst = dng->to()->par_seq_tasks();
    assert(!pst->valid(), "Clobbering existing data?");
    pst->set_par_threads(n_threads);
    pst->set_n_tasks(1);
    assert(pst->valid(), "Error");
  }

  // From space
  {
    SequentialSubTasksDone* pst = dng->from()->par_seq_tasks();
    assert(!pst->valid(), "Clobbering existing data?");
    size_t n_tasks = _survivor_chunk_index + 1;
    assert(n_tasks == 1 || _survivor_chunk_array != NULL, "Error");
    pst->set_par_threads(n_threads);
    pst->set_n_tasks((int)n_tasks);
    assert(pst->valid(), "Error");
  }
}

// Parallel version of remark
void CMSCollector::do_remark_parallel() {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  WorkGang* workers = gch->workers();
  assert(workers != NULL, "Need parallel worker threads.");
  int n_workers = workers->total_workers();
  CompactibleFreeListSpace* cms_space  = _cmsGen->cmsSpace();
  CompactibleFreeListSpace* perm_space = _permGen->cmsSpace();

  CMSParRemarkTask tsk(this,
    cms_space, perm_space,
    n_workers, workers, task_queues());

  // Set up for parallel process_strong_roots work.
  gch->set_par_threads(n_workers);
  // We won't be iterating over the cards in the card table updating
  // the younger_gen cards, so we shouldn't call the following else
  // the verification code as well as subsequent younger_refs_iterate
  // code would get confused. XXX
  // gch->rem_set()->prepare_for_younger_refs_iterate(true); // parallel

  // The young gen rescan work will not be done as part of
  // process_strong_roots (which currently doesn't knw how to
  // parallelize such a scan), but rather will be broken up into
  // a set of parallel tasks (via the sampling that the [abortable]
  // preclean phase did of EdenSpace, plus the [two] tasks of
  // scanning the [two] survivor spaces. Further fine-grain
  // parallelization of the scanning of the survivor spaces
  // themselves, and of precleaning of the younger gen itself
  // is deferred to the future.
  initialize_sequential_subtasks_for_young_gen_rescan(n_workers);

  // The dirty card rescan work is broken up into a "sequence"
  // of parallel tasks (per constituent space) that are dynamically
  // claimed by the parallel threads.
  cms_space->initialize_sequential_subtasks_for_rescan(n_workers);
  perm_space->initialize_sequential_subtasks_for_rescan(n_workers);

  // It turns out that even when we're using 1 thread, doing the work in a
  // separate thread causes wide variance in run times.  We can't help this
  // in the multi-threaded case, but we special-case n=1 here to get
  // repeatable measurements of the 1-thread overhead of the parallel code.
  if (n_workers > 1) {
    // Make refs discovery MT-safe
    ReferenceProcessorMTMutator mt(ref_processor(), true);
    GenCollectedHeap::StrongRootsScope srs(gch);
    workers->run_task(&tsk);
  } else {
    GenCollectedHeap::StrongRootsScope srs(gch);
    tsk.work(0);
  }
  gch->set_par_threads(0);  // 0 ==> non-parallel.
  // restore, single-threaded for now, any preserved marks
  // as a result of work_q overflow
  restore_preserved_marks_if_any();
}

// Non-parallel version of remark
void CMSCollector::do_remark_non_parallel() {
  ResourceMark rm;
  HandleMark   hm;
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  MarkRefsIntoAndScanClosure
    mrias_cl(_span, ref_processor(), &_markBitMap, &_modUnionTable,
             &_markStack, &_revisitStack, this,
             false /* should_yield */, false /* not precleaning */);
  MarkFromDirtyCardsClosure
    markFromDirtyCardsClosure(this, _span,
                              NULL,  // space is set further below
                              &_markBitMap, &_markStack, &_revisitStack,
                              &mrias_cl);
  {
    TraceTime t("grey object rescan", PrintGCDetails, false, gclog_or_tty);
    // Iterate over the dirty cards, setting the corresponding bits in the
    // mod union table.
    {
      ModUnionClosure modUnionClosure(&_modUnionTable);
      _ct->ct_bs()->dirty_card_iterate(
                      _cmsGen->used_region(),
                      &modUnionClosure);
      _ct->ct_bs()->dirty_card_iterate(
                      _permGen->used_region(),
                      &modUnionClosure);
    }
    // Having transferred these marks into the modUnionTable, we just need
    // to rescan the marked objects on the dirty cards in the modUnionTable.
    // The initial marking may have been done during an asynchronous
    // collection so there may be dirty bits in the mod-union table.
    const int alignment =
      CardTableModRefBS::card_size * BitsPerWord;
    {
      // ... First handle dirty cards in CMS gen
      markFromDirtyCardsClosure.set_space(_cmsGen->cmsSpace());
      MemRegion ur = _cmsGen->used_region();
      HeapWord* lb = ur.start();
      HeapWord* ub = (HeapWord*)round_to((intptr_t)ur.end(), alignment);
      MemRegion cms_span(lb, ub);
      _modUnionTable.dirty_range_iterate_clear(cms_span,
                                               &markFromDirtyCardsClosure);
      verify_work_stacks_empty();
      if (PrintCMSStatistics != 0) {
        gclog_or_tty->print(" (re-scanned "SIZE_FORMAT" dirty cards in cms gen) ",
          markFromDirtyCardsClosure.num_dirty_cards());
      }
    }
    {
      // .. and then repeat for dirty cards in perm gen
      markFromDirtyCardsClosure.set_space(_permGen->cmsSpace());
      MemRegion ur = _permGen->used_region();
      HeapWord* lb = ur.start();
      HeapWord* ub = (HeapWord*)round_to((intptr_t)ur.end(), alignment);
      MemRegion perm_span(lb, ub);
      _modUnionTable.dirty_range_iterate_clear(perm_span,
                                               &markFromDirtyCardsClosure);
      verify_work_stacks_empty();
      if (PrintCMSStatistics != 0) {
        gclog_or_tty->print(" (re-scanned "SIZE_FORMAT" dirty cards in perm gen) ",
          markFromDirtyCardsClosure.num_dirty_cards());
      }
    }
  }
  if (VerifyDuringGC &&
      GenCollectedHeap::heap()->total_collections() >= VerifyGCStartAt) {
    HandleMark hm;  // Discard invalid handles created during verification
    Universe::verify(true);
  }
  {
    TraceTime t("root rescan", PrintGCDetails, false, gclog_or_tty);

    verify_work_stacks_empty();

    gch->rem_set()->prepare_for_younger_refs_iterate(false); // Not parallel.
    GenCollectedHeap::StrongRootsScope srs(gch);
    gch->gen_process_strong_roots(_cmsGen->level(),
                                  true,  // younger gens as roots
                                  false, // use the local StrongRootsScope
                                  true,  // collecting perm gen
                                  SharedHeap::ScanningOption(roots_scanning_options()),
                                  &mrias_cl,
                                  true,   // walk code active on stacks
                                  NULL);
    assert(should_unload_classes()
           || (roots_scanning_options() & SharedHeap::SO_CodeCache),
           "if we didn't scan the code cache, we have to be ready to drop nmethods with expired weak oops");
  }
  verify_work_stacks_empty();
  // Restore evacuated mark words, if any, used for overflow list links
  if (!CMSOverflowEarlyRestoration) {
    restore_preserved_marks_if_any();
  }
  verify_overflow_empty();
}

////////////////////////////////////////////////////////
// Parallel Reference Processing Task Proxy Class
////////////////////////////////////////////////////////
class CMSRefProcTaskProxy: public AbstractGangTask {
  typedef AbstractRefProcTaskExecutor::ProcessTask ProcessTask;
  CMSCollector*          _collector;
  CMSBitMap*             _mark_bit_map;
  const MemRegion        _span;
  OopTaskQueueSet*       _task_queues;
  ParallelTaskTerminator _term;
  ProcessTask&           _task;

public:
  CMSRefProcTaskProxy(ProcessTask&     task,
                      CMSCollector*    collector,
                      const MemRegion& span,
                      CMSBitMap*       mark_bit_map,
                      int              total_workers,
                      OopTaskQueueSet* task_queues):
    AbstractGangTask("Process referents by policy in parallel"),
    _task(task),
    _collector(collector), _span(span), _mark_bit_map(mark_bit_map),
    _task_queues(task_queues),
    _term(total_workers, task_queues)
    {
      assert(_collector->_span.equals(_span) && !_span.is_empty(),
             "Inconsistency in _span");
    }

  OopTaskQueueSet* task_queues() { return _task_queues; }

  OopTaskQueue* work_queue(int i) { return task_queues()->queue(i); }

  ParallelTaskTerminator* terminator() { return &_term; }

  void do_work_steal(int i,
                     CMSParDrainMarkingStackClosure* drain,
                     CMSParKeepAliveClosure* keep_alive,
                     int* seed);

  virtual void work(int i);
};

void CMSRefProcTaskProxy::work(int i) {
  assert(_collector->_span.equals(_span), "Inconsistency in _span");
  CMSParKeepAliveClosure par_keep_alive(_collector, _span,
                                        _mark_bit_map,
                                        &_collector->_revisitStack,
                                        work_queue(i));
  CMSParDrainMarkingStackClosure par_drain_stack(_collector, _span,
                                                 _mark_bit_map,
                                                 &_collector->_revisitStack,
                                                 work_queue(i));
  CMSIsAliveClosure is_alive_closure(_span, _mark_bit_map);
  _task.work(i, is_alive_closure, par_keep_alive, par_drain_stack);
  if (_task.marks_oops_alive()) {
    do_work_steal(i, &par_drain_stack, &par_keep_alive,
                  _collector->hash_seed(i));
  }
  assert(work_queue(i)->size() == 0, "work_queue should be empty");
  assert(_collector->_overflow_list == NULL, "non-empty _overflow_list");
}

class CMSRefEnqueueTaskProxy: public AbstractGangTask {
  typedef AbstractRefProcTaskExecutor::EnqueueTask EnqueueTask;
  EnqueueTask& _task;

public:
  CMSRefEnqueueTaskProxy(EnqueueTask& task)
    : AbstractGangTask("Enqueue reference objects in parallel"),
      _task(task)
  { }

  virtual void work(int i)
  {
    _task.work(i);
  }
};

CMSParKeepAliveClosure::CMSParKeepAliveClosure(CMSCollector* collector,
  MemRegion span, CMSBitMap* bit_map, CMSMarkStack* revisit_stack,
  OopTaskQueue* work_queue):
   Par_KlassRememberingOopClosure(collector, NULL, revisit_stack),
   _span(span),
   _bit_map(bit_map),
   _work_queue(work_queue),
   _mark_and_push(collector, span, bit_map, revisit_stack, work_queue),
   _low_water_mark(MIN2((uint)(work_queue->max_elems()/4),
                        (uint)(CMSWorkQueueDrainThreshold * ParallelGCThreads)))
{ }

// . see if we can share work_queues with ParNew? XXX
void CMSRefProcTaskProxy::do_work_steal(int i,
  CMSParDrainMarkingStackClosure* drain,
  CMSParKeepAliveClosure* keep_alive,
  int* seed) {
  OopTaskQueue* work_q = work_queue(i);
  NOT_PRODUCT(int num_steals = 0;)
  oop obj_to_scan;

  while (true) {
    // Completely finish any left over work from (an) earlier round(s)
    drain->trim_queue(0);
    size_t num_from_overflow_list = MIN2((size_t)(work_q->max_elems() - work_q->size())/4,
                                         (size_t)ParGCDesiredObjsFromOverflowList);
    // Now check if there's any work in the overflow list
    if (_collector->par_take_from_overflow_list(num_from_overflow_list,
                                                work_q)) {
      // Found something in global overflow list;
      // not yet ready to go stealing work from others.
      // We'd like to assert(work_q->size() != 0, ...)
      // because we just took work from the overflow list,
      // but of course we can't, since all of that might have
      // been already stolen from us.
      continue;
    }
    // Verify that we have no work before we resort to stealing
    assert(work_q->size() == 0, "Have work, shouldn't steal");
    // Try to steal from other queues that have work
    if (task_queues()->steal(i, seed, /* reference */ obj_to_scan)) {
      NOT_PRODUCT(num_steals++;)
      assert(obj_to_scan->is_oop(), "Oops, not an oop!");
      assert(_mark_bit_map->isMarked((HeapWord*)obj_to_scan), "Stole an unmarked oop?");
      // Do scanning work
      obj_to_scan->oop_iterate(keep_alive);
      // Loop around, finish this work, and try to steal some more
    } else if (terminator()->offer_termination()) {
      break;  // nirvana from the infinite cycle
    }
  }
  NOT_PRODUCT(
    if (PrintCMSStatistics != 0) {
      gclog_or_tty->print("\n\t(%d: stole %d oops)", i, num_steals);
    }
  )
}

void CMSRefProcTaskExecutor::execute(ProcessTask& task)
{
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  WorkGang* workers = gch->workers();
  assert(workers != NULL, "Need parallel worker threads.");
  int n_workers = workers->total_workers();
  CMSRefProcTaskProxy rp_task(task, &_collector,
                              _collector.ref_processor()->span(),
                              _collector.markBitMap(),
                              n_workers, _collector.task_queues());
  workers->run_task(&rp_task);
}

void CMSRefProcTaskExecutor::execute(EnqueueTask& task)
{

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  WorkGang* workers = gch->workers();
  assert(workers != NULL, "Need parallel worker threads.");
  CMSRefEnqueueTaskProxy enq_task(task);
  workers->run_task(&enq_task);
}

void CMSCollector::refProcessingWork(bool asynch, bool clear_all_soft_refs) {

  ResourceMark rm;
  HandleMark   hm;

  ReferenceProcessor* rp = ref_processor();
  assert(rp->span().equals(_span), "Spans should be equal");
  assert(!rp->enqueuing_is_done(), "Enqueuing should not be complete");
  // Process weak references.
  rp->setup_policy(clear_all_soft_refs);
  verify_work_stacks_empty();

  CMSKeepAliveClosure cmsKeepAliveClosure(this, _span, &_markBitMap,
                                          &_markStack, &_revisitStack,
                                          false /* !preclean */);
  CMSDrainMarkingStackClosure cmsDrainMarkingStackClosure(this,
                                _span, &_markBitMap, &_markStack,
                                &cmsKeepAliveClosure, false /* !preclean */);
  {
    TraceTime t("weak refs processing", PrintGCDetails, false, gclog_or_tty);
    if (rp->processing_is_mt()) {
      CMSRefProcTaskExecutor task_executor(*this);
      rp->process_discovered_references(&_is_alive_closure,
                                        &cmsKeepAliveClosure,
                                        &cmsDrainMarkingStackClosure,
                                        &task_executor);
    } else {
      rp->process_discovered_references(&_is_alive_closure,
                                        &cmsKeepAliveClosure,
                                        &cmsDrainMarkingStackClosure,
                                        NULL);
    }
    verify_work_stacks_empty();
  }

  if (should_unload_classes()) {
    {
      TraceTime t("class unloading", PrintGCDetails, false, gclog_or_tty);

      // Follow SystemDictionary roots and unload classes
      bool purged_class = SystemDictionary::do_unloading(&_is_alive_closure);

      // Follow CodeCache roots and unload any methods marked for unloading
      CodeCache::do_unloading(&_is_alive_closure,
                              &cmsKeepAliveClosure,
                              purged_class);

      cmsDrainMarkingStackClosure.do_void();
      verify_work_stacks_empty();

      // Update subklass/sibling/implementor links in KlassKlass descendants
      assert(!_revisitStack.isEmpty(), "revisit stack should not be empty");
      oop k;
      while ((k = _revisitStack.pop()) != NULL) {
        ((Klass*)(oopDesc*)k)->follow_weak_klass_links(
                       &_is_alive_closure,
                       &cmsKeepAliveClosure);
      }
      assert(!ClassUnloading ||
             (_markStack.isEmpty() && overflow_list_is_empty()),
             "Should not have found new reachable objects");
      assert(_revisitStack.isEmpty(), "revisit stack should have been drained");
      cmsDrainMarkingStackClosure.do_void();
      verify_work_stacks_empty();
    }

    {
      TraceTime t("scrub symbol & string tables", PrintGCDetails, false, gclog_or_tty);
      // Now clean up stale oops in SymbolTable and StringTable
      SymbolTable::unlink(&_is_alive_closure);
      StringTable::unlink(&_is_alive_closure);
    }
  }

  verify_work_stacks_empty();
  // Restore any preserved marks as a result of mark stack or
  // work queue overflow
  restore_preserved_marks_if_any();  // done single-threaded for now

  rp->set_enqueuing_is_done(true);
  if (rp->processing_is_mt()) {
    CMSRefProcTaskExecutor task_executor(*this);
    rp->enqueue_discovered_references(&task_executor);
  } else {
    rp->enqueue_discovered_references(NULL);
  }
  rp->verify_no_references_recorded();
  assert(!rp->discovery_enabled(), "should have been disabled");

  // JVMTI object tagging is based on JNI weak refs. If any of these
  // refs were cleared then JVMTI needs to update its maps and
  // maybe post ObjectFrees to agents.
  JvmtiExport::cms_ref_processing_epilogue();
}

#ifndef PRODUCT
void CMSCollector::check_correct_thread_executing() {
  Thread* t = Thread::current();
  // Only the VM thread or the CMS thread should be here.
  assert(t->is_ConcurrentGC_thread() || t->is_VM_thread(),
         "Unexpected thread type");
  // If this is the vm thread, the foreground process
  // should not be waiting.  Note that _foregroundGCIsActive is
  // true while the foreground collector is waiting.
  if (_foregroundGCShouldWait) {
    // We cannot be the VM thread
    assert(t->is_ConcurrentGC_thread(),
           "Should be CMS thread");
  } else {
    // We can be the CMS thread only if we are in a stop-world
    // phase of CMS collection.
    if (t->is_ConcurrentGC_thread()) {
      assert(_collectorState == InitialMarking ||
             _collectorState == FinalMarking,
             "Should be a stop-world phase");
      // The CMS thread should be holding the CMS_token.
      assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
             "Potential interference with concurrently "
             "executing VM thread");
    }
  }
}
#endif

void CMSCollector::sweep(bool asynch) {
  assert(_collectorState == Sweeping, "just checking");
  check_correct_thread_executing();
  verify_work_stacks_empty();
  verify_overflow_empty();
  increment_sweep_count();
  TraceCMSMemoryManagerStats tms(_collectorState);

  _inter_sweep_timer.stop();
  _inter_sweep_estimate.sample(_inter_sweep_timer.seconds());
  size_policy()->avg_cms_free_at_sweep()->sample(_cmsGen->free());

  // PermGen verification support: If perm gen sweeping is disabled in
  // this cycle, we preserve the perm gen object "deadness" information
  // in the perm_gen_verify_bit_map. In order to do that we traverse
  // all blocks in perm gen and mark all dead objects.
  if (verifying() && !should_unload_classes()) {
    assert(perm_gen_verify_bit_map()->sizeInBits() != 0,
           "Should have already been allocated");
    MarkDeadObjectsClosure mdo(this, _permGen->cmsSpace(),
                               markBitMap(), perm_gen_verify_bit_map());
    if (asynch) {
      CMSTokenSyncWithLocks ts(true, _permGen->freelistLock(),
                               bitMapLock());
      _permGen->cmsSpace()->blk_iterate(&mdo);
    } else {
      // In the case of synchronous sweep, we already have
      // the requisite locks/tokens.
      _permGen->cmsSpace()->blk_iterate(&mdo);
    }
  }

  assert(!_intra_sweep_timer.is_active(), "Should not be active");
  _intra_sweep_timer.reset();
  _intra_sweep_timer.start();
  if (asynch) {
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    CMSPhaseAccounting pa(this, "sweep", !PrintGCDetails);
    // First sweep the old gen then the perm gen
    {
      CMSTokenSyncWithLocks ts(true, _cmsGen->freelistLock(),
                               bitMapLock());
      sweepWork(_cmsGen, asynch);
    }

    // Now repeat for perm gen
    if (should_unload_classes()) {
      CMSTokenSyncWithLocks ts(true, _permGen->freelistLock(),
                             bitMapLock());
      sweepWork(_permGen, asynch);
    }

    // Update Universe::_heap_*_at_gc figures.
    // We need all the free list locks to make the abstract state
    // transition from Sweeping to Resetting. See detailed note
    // further below.
    {
      CMSTokenSyncWithLocks ts(true, _cmsGen->freelistLock(),
                               _permGen->freelistLock());
      // Update heap occupancy information which is used as
      // input to soft ref clearing policy at the next gc.
      Universe::update_heap_info_at_gc();
      _collectorState = Resizing;
    }
  } else {
    // already have needed locks
    sweepWork(_cmsGen,  asynch);

    if (should_unload_classes()) {
      sweepWork(_permGen, asynch);
    }
    // Update heap occupancy information which is used as
    // input to soft ref clearing policy at the next gc.
    Universe::update_heap_info_at_gc();
    _collectorState = Resizing;
  }
  verify_work_stacks_empty();
  verify_overflow_empty();

  _intra_sweep_timer.stop();
  _intra_sweep_estimate.sample(_intra_sweep_timer.seconds());

  _inter_sweep_timer.reset();
  _inter_sweep_timer.start();

  update_time_of_last_gc(os::javaTimeMillis());

  // NOTE on abstract state transitions:
  // Mutators allocate-live and/or mark the mod-union table dirty
  // based on the state of the collection.  The former is done in
  // the interval [Marking, Sweeping] and the latter in the interval
  // [Marking, Sweeping).  Thus the transitions into the Marking state
  // and out of the Sweeping state must be synchronously visible
  // globally to the mutators.
  // The transition into the Marking state happens with the world
  // stopped so the mutators will globally see it.  Sweeping is
  // done asynchronously by the background collector so the transition
  // from the Sweeping state to the Resizing state must be done
  // under the freelistLock (as is the check for whether to
  // allocate-live and whether to dirty the mod-union table).
  assert(_collectorState == Resizing, "Change of collector state to"
    " Resizing must be done under the freelistLocks (plural)");

  // Now that sweeping has been completed, if the GCH's
  // incremental_collection_will_fail flag is set, clear it,
  // thus inviting a younger gen collection to promote into
  // this generation. If such a promotion may still fail,
  // the flag will be set again when a young collection is
  // attempted.
  // I think the incremental_collection_will_fail flag's use
  // is specific to a 2 generation collection policy, so i'll
  // assert that that's the configuration we are operating within.
  // The use of the flag can and should be generalized appropriately
  // in the future to deal with a general n-generation system.

  GenCollectedHeap* gch = GenCollectedHeap::heap();
  assert(gch->collector_policy()->is_two_generation_policy(),
         "Resetting of incremental_collection_will_fail flag"
         " may be incorrect otherwise");
  gch->clear_incremental_collection_will_fail();
  gch->update_full_collections_completed(_collection_count_start);
}

// FIX ME!!! Looks like this belongs in CFLSpace, with
// CMSGen merely delegating to it.
void ConcurrentMarkSweepGeneration::setNearLargestChunk() {
  double nearLargestPercent = FLSLargestBlockCoalesceProximity;
  HeapWord*  minAddr        = _cmsSpace->bottom();
  HeapWord*  largestAddr    =
    (HeapWord*) _cmsSpace->dictionary()->findLargestDict();
  if (largestAddr == NULL) {
    // The dictionary appears to be empty.  In this case
    // try to coalesce at the end of the heap.
    largestAddr = _cmsSpace->end();
  }
  size_t largestOffset     = pointer_delta(largestAddr, minAddr);
  size_t nearLargestOffset =
    (size_t)((double)largestOffset * nearLargestPercent) - MinChunkSize;
  if (PrintFLSStatistics != 0) {
    gclog_or_tty->print_cr(
      "CMS: Large Block: " PTR_FORMAT ";"
      " Proximity: " PTR_FORMAT " -> " PTR_FORMAT,
      largestAddr,
      _cmsSpace->nearLargestChunk(), minAddr + nearLargestOffset);
  }
  _cmsSpace->set_nearLargestChunk(minAddr + nearLargestOffset);
}

bool ConcurrentMarkSweepGeneration::isNearLargestChunk(HeapWord* addr) {
  return addr >= _cmsSpace->nearLargestChunk();
}

FreeChunk* ConcurrentMarkSweepGeneration::find_chunk_at_end() {
  return _cmsSpace->find_chunk_at_end();
}

void ConcurrentMarkSweepGeneration::update_gc_stats(int current_level,
                                                    bool full) {
  // The next lower level has been collected.  Gather any statistics
  // that are of interest at this point.
  if (!full && (current_level + 1) == level()) {
    // Gather statistics on the young generation collection.
    collector()->stats().record_gc0_end(used());
  }
}

CMSAdaptiveSizePolicy* ConcurrentMarkSweepGeneration::size_policy() {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  assert(gch->kind() == CollectedHeap::GenCollectedHeap,
    "Wrong type of heap");
  CMSAdaptiveSizePolicy* sp = (CMSAdaptiveSizePolicy*)
    gch->gen_policy()->size_policy();
  assert(sp->is_gc_cms_adaptive_size_policy(),
    "Wrong type of size policy");
  return sp;
}

void ConcurrentMarkSweepGeneration::rotate_debug_collection_type() {
  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print("Rotate from %d ", _debug_collection_type);
  }
  _debug_collection_type = (CollectionTypes) (_debug_collection_type + 1);
  _debug_collection_type =
    (CollectionTypes) (_debug_collection_type % Unknown_collection_type);
  if (PrintGCDetails && Verbose) {
    gclog_or_tty->print_cr("to %d ", _debug_collection_type);
  }
}

void CMSCollector::sweepWork(ConcurrentMarkSweepGeneration* gen,
  bool asynch) {
  // We iterate over the space(s) underlying this generation,
  // checking the mark bit map to see if the bits corresponding
  // to specific blocks are marked or not. Blocks that are
  // marked are live and are not swept up. All remaining blocks
  // are swept up, with coalescing on-the-fly as we sweep up
  // contiguous free and/or garbage blocks:
  // We need to ensure that the sweeper synchronizes with allocators
  // and stop-the-world collectors. In particular, the following
  // locks are used:
  // . CMS token: if this is held, a stop the world collection cannot occur
  // . freelistLock: if this is held no allocation can occur from this
  //                 generation by another thread
  // . bitMapLock: if this is held, no other thread can access or update
  //

  // Note that we need to hold the freelistLock if we use
  // block iterate below; else the iterator might go awry if
  // a mutator (or promotion) causes block contents to change
  // (for instance if the allocator divvies up a block).
  // If we hold the free list lock, for all practical purposes
  // young generation GC's can't occur (they'll usually need to
  // promote), so we might as well prevent all young generation
  // GC's while we do a sweeping step. For the same reason, we might
  // as well take the bit map lock for the entire duration

  // check that we hold the requisite locks
  assert(have_cms_token(), "Should hold cms token");
  assert(   (asynch && ConcurrentMarkSweepThread::cms_thread_has_cms_token())
         || (!asynch && ConcurrentMarkSweepThread::vm_thread_has_cms_token()),
        "Should possess CMS token to sweep");
  assert_lock_strong(gen->freelistLock());
  assert_lock_strong(bitMapLock());

  assert(!_inter_sweep_timer.is_active(), "Was switched off in an outer context");
  assert(_intra_sweep_timer.is_active(),  "Was switched on  in an outer context");
  gen->cmsSpace()->beginSweepFLCensus((float)(_inter_sweep_timer.seconds()),
                                      _inter_sweep_estimate.padded_average(),
                                      _intra_sweep_estimate.padded_average());
  gen->setNearLargestChunk();

  {
    SweepClosure sweepClosure(this, gen, &_markBitMap,
                            CMSYield && asynch);
    gen->cmsSpace()->blk_iterate_careful(&sweepClosure);
    // We need to free-up/coalesce garbage/blocks from a
    // co-terminal free run. This is done in the SweepClosure
    // destructor; so, do not remove this scope, else the
    // end-of-sweep-census below will be off by a little bit.
  }
  gen->cmsSpace()->sweep_completed();
  gen->cmsSpace()->endSweepFLCensus(sweep_count());
  if (should_unload_classes()) {                // unloaded classes this cycle,
    _concurrent_cycles_since_last_unload = 0;   // ... reset count
  } else {                                      // did not unload classes,
    _concurrent_cycles_since_last_unload++;     // ... increment count
  }
}

// Reset CMS data structures (for now just the marking bit map)
// preparatory for the next cycle.
void CMSCollector::reset(bool asynch) {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  CMSAdaptiveSizePolicy* sp = size_policy();
  AdaptiveSizePolicyOutput(sp, gch->total_collections());
  if (asynch) {
    CMSTokenSyncWithLocks ts(true, bitMapLock());

    // If the state is not "Resetting", the foreground  thread
    // has done a collection and the resetting.
    if (_collectorState != Resetting) {
      assert(_collectorState == Idling, "The state should only change"
        " because the foreground collector has finished the collection");
      return;
    }

    // Clear the mark bitmap (no grey objects to start with)
    // for the next cycle.
    TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
    CMSPhaseAccounting cmspa(this, "reset", !PrintGCDetails);

    HeapWord* curAddr = _markBitMap.startWord();
    while (curAddr < _markBitMap.endWord()) {
      size_t remaining  = pointer_delta(_markBitMap.endWord(), curAddr);
      MemRegion chunk(curAddr, MIN2(CMSBitMapYieldQuantum, remaining));
      _markBitMap.clear_large_range(chunk);
      if (ConcurrentMarkSweepThread::should_yield() &&
          !foregroundGCIsActive() &&
          CMSYield) {
        assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
               "CMS thread should hold CMS token");
        assert_lock_strong(bitMapLock());
        bitMapLock()->unlock();
        ConcurrentMarkSweepThread::desynchronize(true);
        ConcurrentMarkSweepThread::acknowledge_yield_request();
        stopTimer();
        if (PrintCMSStatistics != 0) {
          incrementYields();
        }
        icms_wait();

        // See the comment in coordinator_yield()
        for (unsigned i = 0; i < CMSYieldSleepCount &&
                         ConcurrentMarkSweepThread::should_yield() &&
                         !CMSCollector::foregroundGCIsActive(); ++i) {
          os::sleep(Thread::current(), 1, false);
          ConcurrentMarkSweepThread::acknowledge_yield_request();
        }

        ConcurrentMarkSweepThread::synchronize(true);
        bitMapLock()->lock_without_safepoint_check();
        startTimer();
      }
      curAddr = chunk.end();
    }
    // A successful mostly concurrent collection has been done.
    // Because only the full (i.e., concurrent mode failure) collections
    // are being measured for gc overhead limits, clean the "near" flag
    // and count.
    sp->reset_gc_overhead_limit_count();
    _collectorState = Idling;
  } else {
    // already have the lock
    assert(_collectorState == Resetting, "just checking");
    assert_lock_strong(bitMapLock());
    _markBitMap.clear_all();
    _collectorState = Idling;
  }

  // Stop incremental mode after a cycle completes, so that any future cycles
  // are triggered by allocation.
  stop_icms();

  NOT_PRODUCT(
    if (RotateCMSCollectionTypes) {
      _cmsGen->rotate_debug_collection_type();
    }
  )
}

void CMSCollector::do_CMS_operation(CMS_op_type op) {
  gclog_or_tty->date_stamp(PrintGC && PrintGCDateStamps);
  TraceCPUTime tcpu(PrintGCDetails, true, gclog_or_tty);
  TraceTime t("GC", PrintGC, !PrintGCDetails, gclog_or_tty);
  TraceCollectorStats tcs(counters());

  switch (op) {
    case CMS_op_checkpointRootsInitial: {
      checkpointRootsInitial(true);       // asynch
      if (PrintGC) {
        _cmsGen->printOccupancy("initial-mark");
      }
      break;
    }
    case CMS_op_checkpointRootsFinal: {
      checkpointRootsFinal(true,    // asynch
                           false,   // !clear_all_soft_refs
                           false);  // !init_mark_was_synchronous
      if (PrintGC) {
        _cmsGen->printOccupancy("remark");
      }
      break;
    }
    default:
      fatal("No such CMS_op");
  }
}

#ifndef PRODUCT
size_t const CMSCollector::skip_header_HeapWords() {
  return FreeChunk::header_size();
}

// Try and collect here conditions that should hold when
// CMS thread is exiting. The idea is that the foreground GC
// thread should not be blocked if it wants to terminate
// the CMS thread and yet continue to run the VM for a while
// after that.
void CMSCollector::verify_ok_to_terminate() const {
  assert(Thread::current()->is_ConcurrentGC_thread(),
         "should be called by CMS thread");
  assert(!_foregroundGCShouldWait, "should be false");
  // We could check here that all the various low-level locks
  // are not held by the CMS thread, but that is overkill; see
  // also CMSThread::verify_ok_to_terminate() where the CGC_lock
  // is checked.
}
#endif

size_t CMSCollector::block_size_using_printezis_bits(HeapWord* addr) const {
   assert(_markBitMap.isMarked(addr) && _markBitMap.isMarked(addr + 1),
          "missing Printezis mark?");
  HeapWord* nextOneAddr = _markBitMap.getNextMarkedWordAddress(addr + 2);
  size_t size = pointer_delta(nextOneAddr + 1, addr);
  assert(size == CompactibleFreeListSpace::adjustObjectSize(size),
         "alignment problem");
  assert(size >= 3, "Necessary for Printezis marks to work");
  return size;
}

// A variant of the above (block_size_using_printezis_bits()) except
// that we return 0 if the P-bits are not yet set.
size_t CMSCollector::block_size_if_printezis_bits(HeapWord* addr) const {
  if (_markBitMap.isMarked(addr)) {
    assert(_markBitMap.isMarked(addr + 1), "Missing Printezis bit?");
    HeapWord* nextOneAddr = _markBitMap.getNextMarkedWordAddress(addr + 2);
    size_t size = pointer_delta(nextOneAddr + 1, addr);
    assert(size == CompactibleFreeListSpace::adjustObjectSize(size),
           "alignment problem");
    assert(size >= 3, "Necessary for Printezis marks to work");
    return size;
  } else {
    assert(!_markBitMap.isMarked(addr + 1), "Bit map inconsistency?");
    return 0;
  }
}

HeapWord* CMSCollector::next_card_start_after_block(HeapWord* addr) const {
  size_t sz = 0;
  oop p = (oop)addr;
  if (p->klass_or_null() != NULL && p->is_parsable()) {
    sz = CompactibleFreeListSpace::adjustObjectSize(p->size());
  } else {
    sz = block_size_using_printezis_bits(addr);
  }
  assert(sz > 0, "size must be nonzero");
  HeapWord* next_block = addr + sz;
  HeapWord* next_card  = (HeapWord*)round_to((uintptr_t)next_block,
                                             CardTableModRefBS::card_size);
  assert(round_down((uintptr_t)addr,      CardTableModRefBS::card_size) <
         round_down((uintptr_t)next_card, CardTableModRefBS::card_size),
         "must be different cards");
  return next_card;
}


// CMS Bit Map Wrapper /////////////////////////////////////////

// Construct a CMS bit map infrastructure, but don't create the
// bit vector itself. That is done by a separate call CMSBitMap::allocate()
// further below.
CMSBitMap::CMSBitMap(int shifter, int mutex_rank, const char* mutex_name):
  _bm(),
  _shifter(shifter),
  _lock(mutex_rank >= 0 ? new Mutex(mutex_rank, mutex_name, true) : NULL)
{
  _bmStartWord = 0;
  _bmWordSize  = 0;
}

bool CMSBitMap::allocate(MemRegion mr) {
  _bmStartWord = mr.start();
  _bmWordSize  = mr.word_size();
  ReservedSpace brs(ReservedSpace::allocation_align_size_up(
                     (_bmWordSize >> (_shifter + LogBitsPerByte)) + 1));
  if (!brs.is_reserved()) {
    warning("CMS bit map allocation failure");
    return false;
  }
  // For now we'll just commit all of the bit map up fromt.
  // Later on we'll try to be more parsimonious with swap.
  if (!_virtual_space.initialize(brs, brs.size())) {
    warning("CMS bit map backing store failure");
    return false;
  }
  assert(_virtual_space.committed_size() == brs.size(),
         "didn't reserve backing store for all of CMS bit map?");
  _bm.set_map((BitMap::bm_word_t*)_virtual_space.low());
  assert(_virtual_space.committed_size() << (_shifter + LogBitsPerByte) >=
         _bmWordSize, "inconsistency in bit map sizing");
  _bm.set_size(_bmWordSize >> _shifter);

  // bm.clear(); // can we rely on getting zero'd memory? verify below
  assert(isAllClear(),
         "Expected zero'd memory from ReservedSpace constructor");
  assert(_bm.size() == heapWordDiffToOffsetDiff(sizeInWords()),
         "consistency check");
  return true;
}

void CMSBitMap::dirty_range_iterate_clear(MemRegion mr, MemRegionClosure* cl) {
  HeapWord *next_addr, *end_addr, *last_addr;
  assert_locked();
  assert(covers(mr), "out-of-range error");
  // XXX assert that start and end are appropriately aligned
  for (next_addr = mr.start(), end_addr = mr.end();
       next_addr < end_addr; next_addr = last_addr) {
    MemRegion dirty_region = getAndClearMarkedRegion(next_addr, end_addr);
    last_addr = dirty_region.end();
    if (!dirty_region.is_empty()) {
      cl->do_MemRegion(dirty_region);
    } else {
      assert(last_addr == end_addr, "program logic");
      return;
    }
  }
}

#ifndef PRODUCT
void CMSBitMap::assert_locked() const {
  CMSLockVerifier::assert_locked(lock());
}

bool CMSBitMap::covers(MemRegion mr) const {
  // assert(_bm.map() == _virtual_space.low(), "map inconsistency");
  assert((size_t)_bm.size() == (_bmWordSize >> _shifter),
         "size inconsistency");
  return (mr.start() >= _bmStartWord) &&
         (mr.end()   <= endWord());
}

bool CMSBitMap::covers(HeapWord* start, size_t size) const {
    return (start >= _bmStartWord && (start + size) <= endWord());
}

void CMSBitMap::verifyNoOneBitsInRange(HeapWord* left, HeapWord* right) {
  // verify that there are no 1 bits in the interval [left, right)
  FalseBitMapClosure falseBitMapClosure;
  iterate(&falseBitMapClosure, left, right);
}

void CMSBitMap::region_invariant(MemRegion mr)
{
  assert_locked();
  // mr = mr.intersection(MemRegion(_bmStartWord, _bmWordSize));
  assert(!mr.is_empty(), "unexpected empty region");
  assert(covers(mr), "mr should be covered by bit map");
  // convert address range into offset range
  size_t start_ofs = heapWordToOffset(mr.start());
  // Make sure that end() is appropriately aligned
  assert(mr.end() == (HeapWord*)round_to((intptr_t)mr.end(),
                        (1 << (_shifter+LogHeapWordSize))),
         "Misaligned mr.end()");
  size_t end_ofs   = heapWordToOffset(mr.end());
  assert(end_ofs > start_ofs, "Should mark at least one bit");
}

#endif

bool CMSMarkStack::allocate(size_t size) {
  // allocate a stack of the requisite depth
  ReservedSpace rs(ReservedSpace::allocation_align_size_up(
                   size * sizeof(oop)));
  if (!rs.is_reserved()) {
    warning("CMSMarkStack allocation failure");
    return false;
  }
  if (!_virtual_space.initialize(rs, rs.size())) {
    warning("CMSMarkStack backing store failure");
    return false;
  }
  assert(_virtual_space.committed_size() == rs.size(),
         "didn't reserve backing store for all of CMS stack?");
  _base = (oop*)(_virtual_space.low());
  _index = 0;
  _capacity = size;
  NOT_PRODUCT(_max_depth = 0);
  return true;
}

// XXX FIX ME !!! In the MT case we come in here holding a
// leaf lock. For printing we need to take a further lock
// which has lower rank. We need to recallibrate the two
// lock-ranks involved in order to be able to rpint the
// messages below. (Or defer the printing to the caller.
// For now we take the expedient path of just disabling the
// messages for the problematic case.)
void CMSMarkStack::expand() {
  assert(_capacity <= MarkStackSizeMax, "stack bigger than permitted");
  if (_capacity == MarkStackSizeMax) {
    if (_hit_limit++ == 0 && !CMSConcurrentMTEnabled && PrintGCDetails) {
      // We print a warning message only once per CMS cycle.
      gclog_or_tty->print_cr(" (benign) Hit CMSMarkStack max size limit");
    }
    return;
  }
  // Double capacity if possible
  size_t new_capacity = MIN2(_capacity*2, MarkStackSizeMax);
  // Do not give up existing stack until we have managed to
  // get the double capacity that we desired.
  ReservedSpace rs(ReservedSpace::allocation_align_size_up(
                   new_capacity * sizeof(oop)));
  if (rs.is_reserved()) {
    // Release the backing store associated with old stack
    _virtual_space.release();
    // Reinitialize virtual space for new stack
    if (!_virtual_space.initialize(rs, rs.size())) {
      fatal("Not enough swap for expanded marking stack");
    }
    _base = (oop*)(_virtual_space.low());
    _index = 0;
    _capacity = new_capacity;
  } else if (_failed_double++ == 0 && !CMSConcurrentMTEnabled && PrintGCDetails) {
    // Failed to double capacity, continue;
    // we print a detail message only once per CMS cycle.
    gclog_or_tty->print(" (benign) Failed to expand marking stack from "SIZE_FORMAT"K to "
            SIZE_FORMAT"K",
            _capacity / K, new_capacity / K);
  }
}


// Closures
// XXX: there seems to be a lot of code  duplication here;
// should refactor and consolidate common code.

// This closure is used to mark refs into the CMS generation in
// the CMS bit map. Called at the first checkpoint. This closure
// assumes that we do not need to re-mark dirty cards; if the CMS
// generation on which this is used is not an oldest (modulo perm gen)
// generation then this will lose younger_gen cards!

MarkRefsIntoClosure::MarkRefsIntoClosure(
  MemRegion span, CMSBitMap* bitMap):
    _span(span),
    _bitMap(bitMap)
{
    assert(_ref_processor == NULL, "deliberately left NULL");
    assert(_bitMap->covers(_span), "_bitMap/_span mismatch");
}

void MarkRefsIntoClosure::do_oop(oop obj) {
  // if p points into _span, then mark corresponding bit in _markBitMap
  assert(obj->is_oop(), "expected an oop");
  HeapWord* addr = (HeapWord*)obj;
  if (_span.contains(addr)) {
    // this should be made more efficient
    _bitMap->mark(addr);
  }
}

void MarkRefsIntoClosure::do_oop(oop* p)       { MarkRefsIntoClosure::do_oop_work(p); }
void MarkRefsIntoClosure::do_oop(narrowOop* p) { MarkRefsIntoClosure::do_oop_work(p); }

// A variant of the above, used for CMS marking verification.
MarkRefsIntoVerifyClosure::MarkRefsIntoVerifyClosure(
  MemRegion span, CMSBitMap* verification_bm, CMSBitMap* cms_bm):
    _span(span),
    _verification_bm(verification_bm),
    _cms_bm(cms_bm)
{
    assert(_ref_processor == NULL, "deliberately left NULL");
    assert(_verification_bm->covers(_span), "_verification_bm/_span mismatch");
}

void MarkRefsIntoVerifyClosure::do_oop(oop obj) {
  // if p points into _span, then mark corresponding bit in _markBitMap
  assert(obj->is_oop(), "expected an oop");
  HeapWord* addr = (HeapWord*)obj;
  if (_span.contains(addr)) {
    _verification_bm->mark(addr);
    if (!_cms_bm->isMarked(addr)) {
      oop(addr)->print();
      gclog_or_tty->print_cr(" (" INTPTR_FORMAT " should have been marked)", addr);
      fatal("... aborting");
    }
  }
}

void MarkRefsIntoVerifyClosure::do_oop(oop* p)       { MarkRefsIntoVerifyClosure::do_oop_work(p); }
void MarkRefsIntoVerifyClosure::do_oop(narrowOop* p) { MarkRefsIntoVerifyClosure::do_oop_work(p); }

//////////////////////////////////////////////////
// MarkRefsIntoAndScanClosure
//////////////////////////////////////////////////

MarkRefsIntoAndScanClosure::MarkRefsIntoAndScanClosure(MemRegion span,
                                                       ReferenceProcessor* rp,
                                                       CMSBitMap* bit_map,
                                                       CMSBitMap* mod_union_table,
                                                       CMSMarkStack*  mark_stack,
                                                       CMSMarkStack*  revisit_stack,
                                                       CMSCollector* collector,
                                                       bool should_yield,
                                                       bool concurrent_precleaning):
  _collector(collector),
  _span(span),
  _bit_map(bit_map),
  _mark_stack(mark_stack),
  _pushAndMarkClosure(collector, span, rp, bit_map, mod_union_table,
                      mark_stack, revisit_stack, concurrent_precleaning),
  _yield(should_yield),
  _concurrent_precleaning(concurrent_precleaning),
  _freelistLock(NULL)
{
  _ref_processor = rp;
  assert(_ref_processor != NULL, "_ref_processor shouldn't be NULL");
}

// This closure is used to mark refs into the CMS generation at the
// second (final) checkpoint, and to scan and transitively follow
// the unmarked oops. It is also used during the concurrent precleaning
// phase while scanning objects on dirty cards in the CMS generation.
// The marks are made in the marking bit map and the marking stack is
// used for keeping the (newly) grey objects during the scan.
// The parallel version (Par_...) appears further below.
void MarkRefsIntoAndScanClosure::do_oop(oop obj) {
  if (obj != NULL) {
    assert(obj->is_oop(), "expected an oop");
    HeapWord* addr = (HeapWord*)obj;
    assert(_mark_stack->isEmpty(), "pre-condition (eager drainage)");
    assert(_collector->overflow_list_is_empty(),
           "overflow list should be empty");
    if (_span.contains(addr) &&
        !_bit_map->isMarked(addr)) {
      // mark bit map (object is now grey)
      _bit_map->mark(addr);
      // push on marking stack (stack should be empty), and drain the
      // stack by applying this closure to the oops in the oops popped
      // from the stack (i.e. blacken the grey objects)
      bool res = _mark_stack->push(obj);
      assert(res, "Should have space to push on empty stack");
      do {
        oop new_oop = _mark_stack->pop();
        assert(new_oop != NULL && new_oop->is_oop(), "Expected an oop");
        assert(new_oop->is_parsable(), "Found unparsable oop");
        assert(_bit_map->isMarked((HeapWord*)new_oop),
               "only grey objects on this stack");
        // iterate over the oops in this oop, marking and pushing
        // the ones in CMS heap (i.e. in _span).
        new_oop->oop_iterate(&_pushAndMarkClosure);
        // check if it's time to yield
        do_yield_check();
      } while (!_mark_stack->isEmpty() ||
               (!_concurrent_precleaning && take_from_overflow_list()));
        // if marking stack is empty, and we are not doing this
        // during precleaning, then check the overflow list
    }
    assert(_mark_stack->isEmpty(), "post-condition (eager drainage)");
    assert(_collector->overflow_list_is_empty(),
           "overflow list was drained above");
    // We could restore evacuated mark words, if any, used for
    // overflow list links here because the overflow list is
    // provably empty here. That would reduce the maximum
    // size requirements for preserved_{oop,mark}_stack.
    // But we'll just postpone it until we are all done
    // so we can just stream through.
    if (!_concurrent_precleaning && CMSOverflowEarlyRestoration) {
      _collector->restore_preserved_marks_if_any();
      assert(_collector->no_preserved_marks(), "No preserved marks");
    }
    assert(!CMSOverflowEarlyRestoration || _collector->no_preserved_marks(),
           "All preserved marks should have been restored above");
  }
}

void MarkRefsIntoAndScanClosure::do_oop(oop* p)       { MarkRefsIntoAndScanClosure::do_oop_work(p); }
void MarkRefsIntoAndScanClosure::do_oop(narrowOop* p) { MarkRefsIntoAndScanClosure::do_oop_work(p); }

void MarkRefsIntoAndScanClosure::do_yield_work() {
  assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "CMS thread should hold CMS token");
  assert_lock_strong(_freelistLock);
  assert_lock_strong(_bit_map->lock());
  // relinquish the free_list_lock and bitMaplock()
  DEBUG_ONLY(RememberKlassesChecker mux(false);)
  _bit_map->lock()->unlock();
  _freelistLock->unlock();
  ConcurrentMarkSweepThread::desynchronize(true);
  ConcurrentMarkSweepThread::acknowledge_yield_request();
  _collector->stopTimer();
  GCPauseTimer p(_collector->size_policy()->concurrent_timer_ptr());
  if (PrintCMSStatistics != 0) {
    _collector->incrementYields();
  }
  _collector->icms_wait();

  // See the comment in coordinator_yield()
  for (unsigned i = 0;
       i < CMSYieldSleepCount &&
       ConcurrentMarkSweepThread::should_yield() &&
       !CMSCollector::foregroundGCIsActive();
       ++i) {
    os::sleep(Thread::current(), 1, false);
    ConcurrentMarkSweepThread::acknowledge_yield_request();
  }

  ConcurrentMarkSweepThread::synchronize(true);
  _freelistLock->lock_without_safepoint_check();
  _bit_map->lock()->lock_without_safepoint_check();
  _collector->startTimer();
}

///////////////////////////////////////////////////////////
// Par_MarkRefsIntoAndScanClosure: a parallel version of
//                                 MarkRefsIntoAndScanClosure
///////////////////////////////////////////////////////////
Par_MarkRefsIntoAndScanClosure::Par_MarkRefsIntoAndScanClosure(
  CMSCollector* collector, MemRegion span, ReferenceProcessor* rp,
  CMSBitMap* bit_map, OopTaskQueue* work_queue, CMSMarkStack*  revisit_stack):
  _span(span),
  _bit_map(bit_map),
  _work_queue(work_queue),
  _low_water_mark(MIN2((uint)(work_queue->max_elems()/4),
                       (uint)(CMSWorkQueueDrainThreshold * ParallelGCThreads))),
  _par_pushAndMarkClosure(collector, span, rp, bit_map, work_queue,
                          revisit_stack)
{
  _ref_processor = rp;
  assert(_ref_processor != NULL, "_ref_processor shouldn't be NULL");
}

// This closure is used to mark refs into the CMS generation at the
// second (final) checkpoint, and to scan and transitively follow
// the unmarked oops. The marks are made in the marking bit map and
// the work_queue is used for keeping the (newly) grey objects during
// the scan phase whence they are also available for stealing by parallel
// threads. Since the marking bit map is shared, updates are
// synchronized (via CAS).
void Par_MarkRefsIntoAndScanClosure::do_oop(oop obj) {
  if (obj != NULL) {
    // Ignore mark word because this could be an already marked oop
    // that may be chained at the end of the overflow list.
    assert(obj->is_oop(true), "expected an oop");
    HeapWord* addr = (HeapWord*)obj;
    if (_span.contains(addr) &&
        !_bit_map->isMarked(addr)) {
      // mark bit map (object will become grey):
      // It is possible for several threads to be
      // trying to "claim" this object concurrently;
      // the unique thread that succeeds in marking the
      // object first will do the subsequent push on
      // to the work queue (or overflow list).
      if (_bit_map->par_mark(addr)) {
        // push on work_queue (which may not be empty), and trim the
        // queue to an appropriate length by applying this closure to
        // the oops in the oops popped from the stack (i.e. blacken the
        // grey objects)
        bool res = _work_queue->push(obj);
        assert(res, "Low water mark should be less than capacity?");
        trim_queue(_low_water_mark);
      } // Else, another thread claimed the object
    }
  }
}

void Par_MarkRefsIntoAndScanClosure::do_oop(oop* p)       { Par_MarkRefsIntoAndScanClosure::do_oop_work(p); }
void Par_MarkRefsIntoAndScanClosure::do_oop(narrowOop* p) { Par_MarkRefsIntoAndScanClosure::do_oop_work(p); }

// This closure is used to rescan the marked objects on the dirty cards
// in the mod union table and the card table proper.
size_t ScanMarkedObjectsAgainCarefullyClosure::do_object_careful_m(
  oop p, MemRegion mr) {

  size_t size = 0;
  HeapWord* addr = (HeapWord*)p;
  DEBUG_ONLY(_collector->verify_work_stacks_empty();)
  assert(_span.contains(addr), "we are scanning the CMS generation");
  // check if it's time to yield
  if (do_yield_check()) {
    // We yielded for some foreground stop-world work,
    // and we have been asked to abort this ongoing preclean cycle.
    return 0;
  }
  if (_bitMap->isMarked(addr)) {
    // it's marked; is it potentially uninitialized?
    if (p->klass_or_null() != NULL) {
      // If is_conc_safe is false, the object may be undergoing
      // change by the VM outside a safepoint.  Don't try to
      // scan it, but rather leave it for the remark phase.
      if (CMSPermGenPrecleaningEnabled &&
          (!p->is_conc_safe() || !p->is_parsable())) {
        // Signal precleaning to redirty the card since
        // the klass pointer is already installed.
        assert(size == 0, "Initial value");
      } else {
        assert(p->is_parsable(), "must be parsable.");
        // an initialized object; ignore mark word in verification below
        // since we are running concurrent with mutators
        assert(p->is_oop(true), "should be an oop");
        if (p->is_objArray()) {
          // objArrays are precisely marked; restrict scanning
          // to dirty cards only.
          size = CompactibleFreeListSpace::adjustObjectSize(
                   p->oop_iterate(_scanningClosure, mr));
        } else {
          // A non-array may have been imprecisely marked; we need
          // to scan object in its entirety.
          size = CompactibleFreeListSpace::adjustObjectSize(
                   p->oop_iterate(_scanningClosure));
        }
        #ifdef DEBUG
          size_t direct_size =
            CompactibleFreeListSpace::adjustObjectSize(p->size());
          assert(size == direct_size, "Inconsistency in size");
          assert(size >= 3, "Necessary for Printezis marks to work");
          if (!_bitMap->isMarked(addr+1)) {
            _bitMap->verifyNoOneBitsInRange(addr+2, addr+size);
          } else {
            _bitMap->verifyNoOneBitsInRange(addr+2, addr+size-1);
            assert(_bitMap->isMarked(addr+size-1),
                   "inconsistent Printezis mark");
          }
        #endif // DEBUG
      }
    } else {
      // an unitialized object
      assert(_bitMap->isMarked(addr+1), "missing Printezis mark?");
      HeapWord* nextOneAddr = _bitMap->getNextMarkedWordAddress(addr + 2);
      size = pointer_delta(nextOneAddr + 1, addr);
      assert(size == CompactibleFreeListSpace::adjustObjectSize(size),
             "alignment problem");
      // Note that pre-cleaning needn't redirty the card. OopDesc::set_klass()
      // will dirty the card when the klass pointer is installed in the
      // object (signalling the completion of initialization).
    }
  } else {
    // Either a not yet marked object or an uninitialized object
    if (p->klass_or_null() == NULL || !p->is_parsable()) {
      // An uninitialized object, skip to the next card, since
      // we may not be able to read its P-bits yet.
      assert(size == 0, "Initial value");
    } else {
      // An object not (yet) reached by marking: we merely need to
      // compute its size so as to go look at the next block.
      assert(p->is_oop(true), "should be an oop");
      size = CompactibleFreeListSpace::adjustObjectSize(p->size());
    }
  }
  DEBUG_ONLY(_collector->verify_work_stacks_empty();)
  return size;
}

void ScanMarkedObjectsAgainCarefullyClosure::do_yield_work() {
  assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "CMS thread should hold CMS token");
  assert_lock_strong(_freelistLock);
  assert_lock_strong(_bitMap->lock());
  DEBUG_ONLY(RememberKlassesChecker mux(false);)
  // relinquish the free_list_lock and bitMaplock()
  _bitMap->lock()->unlock();
  _freelistLock->unlock();
  ConcurrentMarkSweepThread::desynchronize(true);
  ConcurrentMarkSweepThread::acknowledge_yield_request();
  _collector->stopTimer();
  GCPauseTimer p(_collector->size_policy()->concurrent_timer_ptr());
  if (PrintCMSStatistics != 0) {
    _collector->incrementYields();
  }
  _collector->icms_wait();

  // See the comment in coordinator_yield()
  for (unsigned i = 0; i < CMSYieldSleepCount &&
                   ConcurrentMarkSweepThread::should_yield() &&
                   !CMSCollector::foregroundGCIsActive(); ++i) {
    os::sleep(Thread::current(), 1, false);
    ConcurrentMarkSweepThread::acknowledge_yield_request();
  }

  ConcurrentMarkSweepThread::synchronize(true);
  _freelistLock->lock_without_safepoint_check();
  _bitMap->lock()->lock_without_safepoint_check();
  _collector->startTimer();
}


//////////////////////////////////////////////////////////////////
// SurvivorSpacePrecleanClosure
//////////////////////////////////////////////////////////////////
// This (single-threaded) closure is used to preclean the oops in
// the survivor spaces.
size_t SurvivorSpacePrecleanClosure::do_object_careful(oop p) {

  HeapWord* addr = (HeapWord*)p;
  DEBUG_ONLY(_collector->verify_work_stacks_empty();)
  assert(!_span.contains(addr), "we are scanning the survivor spaces");
  assert(p->klass_or_null() != NULL, "object should be initializd");
  assert(p->is_parsable(), "must be parsable.");
  // an initialized object; ignore mark word in verification below
  // since we are running concurrent with mutators
  assert(p->is_oop(true), "should be an oop");
  // Note that we do not yield while we iterate over
  // the interior oops of p, pushing the relevant ones
  // on our marking stack.
  size_t size = p->oop_iterate(_scanning_closure);
  do_yield_check();
  // Observe that below, we do not abandon the preclean
  // phase as soon as we should; rather we empty the
  // marking stack before returning. This is to satisfy
  // some existing assertions. In general, it may be a
  // good idea to abort immediately and complete the marking
  // from the grey objects at a later time.
  while (!_mark_stack->isEmpty()) {
    oop new_oop = _mark_stack->pop();
    assert(new_oop != NULL && new_oop->is_oop(), "Expected an oop");
    assert(new_oop->is_parsable(), "Found unparsable oop");
    assert(_bit_map->isMarked((HeapWord*)new_oop),
           "only grey objects on this stack");
    // iterate over the oops in this oop, marking and pushing
    // the ones in CMS heap (i.e. in _span).
    new_oop->oop_iterate(_scanning_closure);
    // check if it's time to yield
    do_yield_check();
  }
  unsigned int after_count =
    GenCollectedHeap::heap()->total_collections();
  bool abort = (_before_count != after_count) ||
               _collector->should_abort_preclean();
  return abort ? 0 : size;
}

void SurvivorSpacePrecleanClosure::do_yield_work() {
  assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "CMS thread should hold CMS token");
  assert_lock_strong(_bit_map->lock());
  DEBUG_ONLY(RememberKlassesChecker smx(false);)
  // Relinquish the bit map lock
  _bit_map->lock()->unlock();
  ConcurrentMarkSweepThread::desynchronize(true);
  ConcurrentMarkSweepThread::acknowledge_yield_request();
  _collector->stopTimer();
  GCPauseTimer p(_collector->size_policy()->concurrent_timer_ptr());
  if (PrintCMSStatistics != 0) {
    _collector->incrementYields();
  }
  _collector->icms_wait();

  // See the comment in coordinator_yield()
  for (unsigned i = 0; i < CMSYieldSleepCount &&
                       ConcurrentMarkSweepThread::should_yield() &&
                       !CMSCollector::foregroundGCIsActive(); ++i) {
    os::sleep(Thread::current(), 1, false);
    ConcurrentMarkSweepThread::acknowledge_yield_request();
  }

  ConcurrentMarkSweepThread::synchronize(true);
  _bit_map->lock()->lock_without_safepoint_check();
  _collector->startTimer();
}

// This closure is used to rescan the marked objects on the dirty cards
// in the mod union table and the card table proper. In the parallel
// case, although the bitMap is shared, we do a single read so the
// isMarked() query is "safe".
bool ScanMarkedObjectsAgainClosure::do_object_bm(oop p, MemRegion mr) {
  // Ignore mark word because we are running concurrent with mutators
  assert(p->is_oop_or_null(true), "expected an oop or null");
  HeapWord* addr = (HeapWord*)p;
  assert(_span.contains(addr), "we are scanning the CMS generation");
  bool is_obj_array = false;
  #ifdef DEBUG
    if (!_parallel) {
      assert(_mark_stack->isEmpty(), "pre-condition (eager drainage)");
      assert(_collector->overflow_list_is_empty(),
             "overflow list should be empty");

    }
  #endif // DEBUG
  if (_bit_map->isMarked(addr)) {
    // Obj arrays are precisely marked, non-arrays are not;
    // so we scan objArrays precisely and non-arrays in their
    // entirety.
    if (p->is_objArray()) {
      is_obj_array = true;
      if (_parallel) {
        p->oop_iterate(_par_scan_closure, mr);
      } else {
        p->oop_iterate(_scan_closure, mr);
      }
    } else {
      if (_parallel) {
        p->oop_iterate(_par_scan_closure);
      } else {
        p->oop_iterate(_scan_closure);
      }
    }
  }
  #ifdef DEBUG
    if (!_parallel) {
      assert(_mark_stack->isEmpty(), "post-condition (eager drainage)");
      assert(_collector->overflow_list_is_empty(),
             "overflow list should be empty");

    }
  #endif // DEBUG
  return is_obj_array;
}

MarkFromRootsClosure::MarkFromRootsClosure(CMSCollector* collector,
                        MemRegion span,
                        CMSBitMap* bitMap, CMSMarkStack*  markStack,
                        CMSMarkStack*  revisitStack,
                        bool should_yield, bool verifying):
  _collector(collector),
  _span(span),
  _bitMap(bitMap),
  _mut(&collector->_modUnionTable),
  _markStack(markStack),
  _revisitStack(revisitStack),
  _yield(should_yield),
  _skipBits(0)
{
  assert(_markStack->isEmpty(), "stack should be empty");
  _finger = _bitMap->startWord();
  _threshold = _finger;
  assert(_collector->_restart_addr == NULL, "Sanity check");
  assert(_span.contains(_finger), "Out of bounds _finger?");
  DEBUG_ONLY(_verifying = verifying;)
}

void MarkFromRootsClosure::reset(HeapWord* addr) {
  assert(_markStack->isEmpty(), "would cause duplicates on stack");
  assert(_span.contains(addr), "Out of bounds _finger?");
  _finger = addr;
  _threshold = (HeapWord*)round_to(
                 (intptr_t)_finger, CardTableModRefBS::card_size);
}

// Should revisit to see if this should be restructured for
// greater efficiency.
bool MarkFromRootsClosure::do_bit(size_t offset) {
  if (_skipBits > 0) {
    _skipBits--;
    return true;
  }
  // convert offset into a HeapWord*
  HeapWord* addr = _bitMap->startWord() + offset;
  assert(_bitMap->endWord() && addr < _bitMap->endWord(),
         "address out of range");
  assert(_bitMap->isMarked(addr), "tautology");
  if (_bitMap->isMarked(addr+1)) {
    // this is an allocated but not yet initialized object
    assert(_skipBits == 0, "tautology");
    _skipBits = 2;  // skip next two marked bits ("Printezis-marks")
    oop p = oop(addr);
    if (p->klass_or_null() == NULL || !p->is_parsable()) {
      DEBUG_ONLY(if (!_verifying) {)
        // We re-dirty the cards on which this object lies and increase
        // the _threshold so that we'll come back to scan this object
        // during the preclean or remark phase. (CMSCleanOnEnter)
        if (CMSCleanOnEnter) {
          size_t sz = _collector->block_size_using_printezis_bits(addr);
          HeapWord* end_card_addr   = (HeapWord*)round_to(
                                         (intptr_t)(addr+sz), CardTableModRefBS::card_size);
          MemRegion redirty_range = MemRegion(addr, end_card_addr);
          assert(!redirty_range.is_empty(), "Arithmetical tautology");
          // Bump _threshold to end_card_addr; note that
          // _threshold cannot possibly exceed end_card_addr, anyhow.
          // This prevents future clearing of the card as the scan proceeds
          // to the right.
          assert(_threshold <= end_card_addr,
                 "Because we are just scanning into this object");
          if (_threshold < end_card_addr) {
            _threshold = end_card_addr;
          }
          if (p->klass_or_null() != NULL) {
            // Redirty the range of cards...
            _mut->mark_range(redirty_range);
          } // ...else the setting of klass will dirty the card anyway.
        }
      DEBUG_ONLY(})
      return true;
    }
  }
  scanOopsInOop(addr);
  return true;
}

// We take a break if we've been at this for a while,
// so as to avoid monopolizing the locks involved.
void MarkFromRootsClosure::do_yield_work() {
  // First give up the locks, then yield, then re-lock
  // We should probably use a constructor/destructor idiom to
  // do this unlock/lock or modify the MutexUnlocker class to
  // serve our purpose. XXX
  assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "CMS thread should hold CMS token");
  assert_lock_strong(_bitMap->lock());
  DEBUG_ONLY(RememberKlassesChecker mux(false);)
  _bitMap->lock()->unlock();
  ConcurrentMarkSweepThread::desynchronize(true);
  ConcurrentMarkSweepThread::acknowledge_yield_request();
  _collector->stopTimer();
  GCPauseTimer p(_collector->size_policy()->concurrent_timer_ptr());
  if (PrintCMSStatistics != 0) {
    _collector->incrementYields();
  }
  _collector->icms_wait();

  // See the comment in coordinator_yield()
  for (unsigned i = 0; i < CMSYieldSleepCount &&
                       ConcurrentMarkSweepThread::should_yield() &&
                       !CMSCollector::foregroundGCIsActive(); ++i) {
    os::sleep(Thread::current(), 1, false);
    ConcurrentMarkSweepThread::acknowledge_yield_request();
  }

  ConcurrentMarkSweepThread::synchronize(true);
  _bitMap->lock()->lock_without_safepoint_check();
  _collector->startTimer();
}

void MarkFromRootsClosure::scanOopsInOop(HeapWord* ptr) {
  assert(_bitMap->isMarked(ptr), "expected bit to be set");
  assert(_markStack->isEmpty(),
         "should drain stack to limit stack usage");
  // convert ptr to an oop preparatory to scanning
  oop obj = oop(ptr);
  // Ignore mark word in verification below, since we
  // may be running concurrent with mutators.
  assert(obj->is_oop(true), "should be an oop");
  assert(_finger <= ptr, "_finger runneth ahead");
  // advance the finger to right end of this object
  _finger = ptr + obj->size();
  assert(_finger > ptr, "we just incremented it above");
  // On large heaps, it may take us some time to get through
  // the marking phase (especially if running iCMS). During
  // this time it's possible that a lot of mutations have
  // accumulated in the card table and the mod union table --
  // these mutation records are redundant until we have
  // actually traced into the corresponding card.
  // Here, we check whether advancing the finger would make
  // us cross into a new card, and if so clear corresponding
  // cards in the MUT (preclean them in the card-table in the
  // future).

  DEBUG_ONLY(if (!_verifying) {)
    // The clean-on-enter optimization is disabled by default,
    // until we fix 6178663.
    if (CMSCleanOnEnter && (_finger > _threshold)) {
      // [_threshold, _finger) represents the interval
      // of cards to be cleared  in MUT (or precleaned in card table).
      // The set of cards to be cleared is all those that overlap
      // with the interval [_threshold, _finger); note that
      // _threshold is always kept card-aligned but _finger isn't
      // always card-aligned.
      HeapWord* old_threshold = _threshold;
      assert(old_threshold == (HeapWord*)round_to(
              (intptr_t)old_threshold, CardTableModRefBS::card_size),
             "_threshold should always be card-aligned");
      _threshold = (HeapWord*)round_to(
                     (intptr_t)_finger, CardTableModRefBS::card_size);
      MemRegion mr(old_threshold, _threshold);
      assert(!mr.is_empty(), "Control point invariant");
      assert(_span.contains(mr), "Should clear within span");
      // XXX When _finger crosses from old gen into perm gen
      // we may be doing unnecessary cleaning; do better in the
      // future by detecting that condition and clearing fewer
      // MUT/CT entries.
      _mut->clear_range(mr);
    }
  DEBUG_ONLY(})
  // Note: the finger doesn't advance while we drain
  // the stack below.
  PushOrMarkClosure pushOrMarkClosure(_collector,
                                      _span, _bitMap, _markStack,
                                      _revisitStack,
                                      _finger, this);
  bool res = _markStack->push(obj);
  assert(res, "Empty non-zero size stack should have space for single push");
  while (!_markStack->isEmpty()) {
    oop new_oop = _markStack->pop();
    // Skip verifying header mark word below because we are
    // running concurrent with mutators.
    assert(new_oop->is_oop(true), "Oops! expected to pop an oop");
    // now scan this oop's oops
    new_oop->oop_iterate(&pushOrMarkClosure);
    do_yield_check();
  }
  assert(_markStack->isEmpty(), "tautology, emphasizing post-condition");
}

Par_MarkFromRootsClosure::Par_MarkFromRootsClosure(CMSConcMarkingTask* task,
                       CMSCollector* collector, MemRegion span,
                       CMSBitMap* bit_map,
                       OopTaskQueue* work_queue,
                       CMSMarkStack*  overflow_stack,
                       CMSMarkStack*  revisit_stack,
                       bool should_yield):
  _collector(collector),
  _whole_span(collector->_span),
  _span(span),
  _bit_map(bit_map),
  _mut(&collector->_modUnionTable),
  _work_queue(work_queue),
  _overflow_stack(overflow_stack),
  _revisit_stack(revisit_stack),
  _yield(should_yield),
  _skip_bits(0),
  _task(task)
{
  assert(_work_queue->size() == 0, "work_queue should be empty");
  _finger = span.start();
  _threshold = _finger;     // XXX Defer clear-on-enter optimization for now
  assert(_span.contains(_finger), "Out of bounds _finger?");
}

// Should revisit to see if this should be restructured for
// greater efficiency.
bool Par_MarkFromRootsClosure::do_bit(size_t offset) {
  if (_skip_bits > 0) {
    _skip_bits--;
    return true;
  }
  // convert offset into a HeapWord*
  HeapWord* addr = _bit_map->startWord() + offset;
  assert(_bit_map->endWord() && addr < _bit_map->endWord(),
         "address out of range");
  assert(_bit_map->isMarked(addr), "tautology");
  if (_bit_map->isMarked(addr+1)) {
    // this is an allocated object that might not yet be initialized
    assert(_skip_bits == 0, "tautology");
    _skip_bits = 2;  // skip next two marked bits ("Printezis-marks")
    oop p = oop(addr);
    if (p->klass_or_null() == NULL || !p->is_parsable()) {
      // in the case of Clean-on-Enter optimization, redirty card
      // and avoid clearing card by increasing  the threshold.
      return true;
    }
  }
  scan_oops_in_oop(addr);
  return true;
}

void Par_MarkFromRootsClosure::scan_oops_in_oop(HeapWord* ptr) {
  assert(_bit_map->isMarked(ptr), "expected bit to be set");
  // Should we assert that our work queue is empty or
  // below some drain limit?
  assert(_work_queue->size() == 0,
         "should drain stack to limit stack usage");
  // convert ptr to an oop preparatory to scanning
  oop obj = oop(ptr);
  // Ignore mark word in verification below, since we
  // may be running concurrent with mutators.
  assert(obj->is_oop(true), "should be an oop");
  assert(_finger <= ptr, "_finger runneth ahead");
  // advance the finger to right end of this object
  _finger = ptr + obj->size();
  assert(_finger > ptr, "we just incremented it above");
  // On large heaps, it may take us some time to get through
  // the marking phase (especially if running iCMS). During
  // this time it's possible that a lot of mutations have
  // accumulated in the card table and the mod union table --
  // these mutation records are redundant until we have
  // actually traced into the corresponding card.
  // Here, we check whether advancing the finger would make
  // us cross into a new card, and if so clear corresponding
  // cards in the MUT (preclean them in the card-table in the
  // future).

  // The clean-on-enter optimization is disabled by default,
  // until we fix 6178663.
  if (CMSCleanOnEnter && (_finger > _threshold)) {
    // [_threshold, _finger) represents the interval
    // of cards to be cleared  in MUT (or precleaned in card table).
    // The set of cards to be cleared is all those that overlap
    // with the interval [_threshold, _finger); note that
    // _threshold is always kept card-aligned but _finger isn't
    // always card-aligned.
    HeapWord* old_threshold = _threshold;
    assert(old_threshold == (HeapWord*)round_to(
            (intptr_t)old_threshold, CardTableModRefBS::card_size),
           "_threshold should always be card-aligned");
    _threshold = (HeapWord*)round_to(
                   (intptr_t)_finger, CardTableModRefBS::card_size);
    MemRegion mr(old_threshold, _threshold);
    assert(!mr.is_empty(), "Control point invariant");
    assert(_span.contains(mr), "Should clear within span"); // _whole_span ??
    // XXX When _finger crosses from old gen into perm gen
    // we may be doing unnecessary cleaning; do better in the
    // future by detecting that condition and clearing fewer
    // MUT/CT entries.
    _mut->clear_range(mr);
  }

  // Note: the local finger doesn't advance while we drain
  // the stack below, but the global finger sure can and will.
  HeapWord** gfa = _task->global_finger_addr();
  Par_PushOrMarkClosure pushOrMarkClosure(_collector,
                                      _span, _bit_map,
                                      _work_queue,
                                      _overflow_stack,
                                      _revisit_stack,
                                      _finger,
                                      gfa, this);
  bool res = _work_queue->push(obj);   // overflow could occur here
  assert(res, "Will hold once we use workqueues");
  while (true) {
    oop new_oop;
    if (!_work_queue->pop_local(new_oop)) {
      // We emptied our work_queue; check if there's stuff that can
      // be gotten from the overflow stack.
      if (CMSConcMarkingTask::get_work_from_overflow_stack(
            _overflow_stack, _work_queue)) {
        do_yield_check();
        continue;
      } else {  // done
        break;
      }
    }
    // Skip verifying header mark word below because we are
    // running concurrent with mutators.
    assert(new_oop->is_oop(true), "Oops! expected to pop an oop");
    // now scan this oop's oops
    new_oop->oop_iterate(&pushOrMarkClosure);
    do_yield_check();
  }
  assert(_work_queue->size() == 0, "tautology, emphasizing post-condition");
}

// Yield in response to a request from VM Thread or
// from mutators.
void Par_MarkFromRootsClosure::do_yield_work() {
  assert(_task != NULL, "sanity");
  _task->yield();
}

// A variant of the above used for verifying CMS marking work.
MarkFromRootsVerifyClosure::MarkFromRootsVerifyClosure(CMSCollector* collector,
                        MemRegion span,
                        CMSBitMap* verification_bm, CMSBitMap* cms_bm,
                        CMSMarkStack*  mark_stack):
  _collector(collector),
  _span(span),
  _verification_bm(verification_bm),
  _cms_bm(cms_bm),
  _mark_stack(mark_stack),
  _pam_verify_closure(collector, span, verification_bm, cms_bm,
                      mark_stack)
{
  assert(_mark_stack->isEmpty(), "stack should be empty");
  _finger = _verification_bm->startWord();
  assert(_collector->_restart_addr == NULL, "Sanity check");
  assert(_span.contains(_finger), "Out of bounds _finger?");
}

void MarkFromRootsVerifyClosure::reset(HeapWord* addr) {
  assert(_mark_stack->isEmpty(), "would cause duplicates on stack");
  assert(_span.contains(addr), "Out of bounds _finger?");
  _finger = addr;
}

// Should revisit to see if this should be restructured for
// greater efficiency.
bool MarkFromRootsVerifyClosure::do_bit(size_t offset) {
  // convert offset into a HeapWord*
  HeapWord* addr = _verification_bm->startWord() + offset;
  assert(_verification_bm->endWord() && addr < _verification_bm->endWord(),
         "address out of range");
  assert(_verification_bm->isMarked(addr), "tautology");
  assert(_cms_bm->isMarked(addr), "tautology");

  assert(_mark_stack->isEmpty(),
         "should drain stack to limit stack usage");
  // convert addr to an oop preparatory to scanning
  oop obj = oop(addr);
  assert(obj->is_oop(), "should be an oop");
  assert(_finger <= addr, "_finger runneth ahead");
  // advance the finger to right end of this object
  _finger = addr + obj->size();
  assert(_finger > addr, "we just incremented it above");
  // Note: the finger doesn't advance while we drain
  // the stack below.
  bool res = _mark_stack->push(obj);
  assert(res, "Empty non-zero size stack should have space for single push");
  while (!_mark_stack->isEmpty()) {
    oop new_oop = _mark_stack->pop();
    assert(new_oop->is_oop(), "Oops! expected to pop an oop");
    // now scan this oop's oops
    new_oop->oop_iterate(&_pam_verify_closure);
  }
  assert(_mark_stack->isEmpty(), "tautology, emphasizing post-condition");
  return true;
}

PushAndMarkVerifyClosure::PushAndMarkVerifyClosure(
  CMSCollector* collector, MemRegion span,
  CMSBitMap* verification_bm, CMSBitMap* cms_bm,
  CMSMarkStack*  mark_stack):
  OopClosure(collector->ref_processor()),
  _collector(collector),
  _span(span),
  _verification_bm(verification_bm),
  _cms_bm(cms_bm),
  _mark_stack(mark_stack)
{ }

void PushAndMarkVerifyClosure::do_oop(oop* p)       { PushAndMarkVerifyClosure::do_oop_work(p); }
void PushAndMarkVerifyClosure::do_oop(narrowOop* p) { PushAndMarkVerifyClosure::do_oop_work(p); }

// Upon stack overflow, we discard (part of) the stack,
// remembering the least address amongst those discarded
// in CMSCollector's _restart_address.
void PushAndMarkVerifyClosure::handle_stack_overflow(HeapWord* lost) {
  // Remember the least grey address discarded
  HeapWord* ra = (HeapWord*)_mark_stack->least_value(lost);
  _collector->lower_restart_addr(ra);
  _mark_stack->reset();  // discard stack contents
  _mark_stack->expand(); // expand the stack if possible
}

void PushAndMarkVerifyClosure::do_oop(oop obj) {
  assert(obj->is_oop_or_null(), "expected an oop or NULL");
  HeapWord* addr = (HeapWord*)obj;
  if (_span.contains(addr) && !_verification_bm->isMarked(addr)) {
    // Oop lies in _span and isn't yet grey or black
    _verification_bm->mark(addr);            // now grey
    if (!_cms_bm->isMarked(addr)) {
      oop(addr)->print();
      gclog_or_tty->print_cr(" (" INTPTR_FORMAT " should have been marked)",
                             addr);
      fatal("... aborting");
    }

    if (!_mark_stack->push(obj)) { // stack overflow
      if (PrintCMSStatistics != 0) {
        gclog_or_tty->print_cr("CMS marking stack overflow (benign) at "
                               SIZE_FORMAT, _mark_stack->capacity());
      }
      assert(_mark_stack->isFull(), "Else push should have succeeded");
      handle_stack_overflow(addr);
    }
    // anything including and to the right of _finger
    // will be scanned as we iterate over the remainder of the
    // bit map
  }
}

PushOrMarkClosure::PushOrMarkClosure(CMSCollector* collector,
                     MemRegion span,
                     CMSBitMap* bitMap, CMSMarkStack*  markStack,
                     CMSMarkStack*  revisitStack,
                     HeapWord* finger, MarkFromRootsClosure* parent) :
  KlassRememberingOopClosure(collector, collector->ref_processor(), revisitStack),
  _span(span),
  _bitMap(bitMap),
  _markStack(markStack),
  _finger(finger),
  _parent(parent)
{ }

Par_PushOrMarkClosure::Par_PushOrMarkClosure(CMSCollector* collector,
                     MemRegion span,
                     CMSBitMap* bit_map,
                     OopTaskQueue* work_queue,
                     CMSMarkStack*  overflow_stack,
                     CMSMarkStack*  revisit_stack,
                     HeapWord* finger,
                     HeapWord** global_finger_addr,
                     Par_MarkFromRootsClosure* parent) :
  Par_KlassRememberingOopClosure(collector,
                            collector->ref_processor(),
                            revisit_stack),
  _whole_span(collector->_span),
  _span(span),
  _bit_map(bit_map),
  _work_queue(work_queue),
  _overflow_stack(overflow_stack),
  _finger(finger),
  _global_finger_addr(global_finger_addr),
  _parent(parent)
{ }

// Assumes thread-safe access by callers, who are
// responsible for mutual exclusion.
void CMSCollector::lower_restart_addr(HeapWord* low) {
  assert(_span.contains(low), "Out of bounds addr");
  if (_restart_addr == NULL) {
    _restart_addr = low;
  } else {
    _restart_addr = MIN2(_restart_addr, low);
  }
}

// Upon stack overflow, we discard (part of) the stack,
// remembering the least address amongst those discarded
// in CMSCollector's _restart_address.
void PushOrMarkClosure::handle_stack_overflow(HeapWord* lost) {
  // Remember the least grey address discarded
  HeapWord* ra = (HeapWord*)_markStack->least_value(lost);
  _collector->lower_restart_addr(ra);
  _markStack->reset();  // discard stack contents
  _markStack->expand(); // expand the stack if possible
}

// Upon stack overflow, we discard (part of) the stack,
// remembering the least address amongst those discarded
// in CMSCollector's _restart_address.
void Par_PushOrMarkClosure::handle_stack_overflow(HeapWord* lost) {
  // We need to do this under a mutex to prevent other
  // workers from interfering with the work done below.
  MutexLockerEx ml(_overflow_stack->par_lock(),
                   Mutex::_no_safepoint_check_flag);
  // Remember the least grey address discarded
  HeapWord* ra = (HeapWord*)_overflow_stack->least_value(lost);
  _collector->lower_restart_addr(ra);
  _overflow_stack->reset();  // discard stack contents
  _overflow_stack->expand(); // expand the stack if possible
}

void PushOrMarkClosure::do_oop(oop obj) {
  // Ignore mark word because we are running concurrent with mutators.
  assert(obj->is_oop_or_null(true), "expected an oop or NULL");
  HeapWord* addr = (HeapWord*)obj;
  if (_span.contains(addr) && !_bitMap->isMarked(addr)) {
    // Oop lies in _span and isn't yet grey or black
    _bitMap->mark(addr);            // now grey
    if (addr < _finger) {
      // the bit map iteration has already either passed, or
      // sampled, this bit in the bit map; we'll need to
      // use the marking stack to scan this oop's oops.
      bool simulate_overflow = false;
      NOT_PRODUCT(
        if (CMSMarkStackOverflowALot &&
            _collector->simulate_overflow()) {
          // simulate a stack overflow
          simulate_overflow = true;
        }
      )
      if (simulate_overflow || !_markStack->push(obj)) { // stack overflow
        if (PrintCMSStatistics != 0) {
          gclog_or_tty->print_cr("CMS marking stack overflow (benign) at "
                                 SIZE_FORMAT, _markStack->capacity());
        }
        assert(simulate_overflow || _markStack->isFull(), "Else push should have succeeded");
        handle_stack_overflow(addr);
      }
    }
    // anything including and to the right of _finger
    // will be scanned as we iterate over the remainder of the
    // bit map
    do_yield_check();
  }
}

void PushOrMarkClosure::do_oop(oop* p)       { PushOrMarkClosure::do_oop_work(p); }
void PushOrMarkClosure::do_oop(narrowOop* p) { PushOrMarkClosure::do_oop_work(p); }

void Par_PushOrMarkClosure::do_oop(oop obj) {
  // Ignore mark word because we are running concurrent with mutators.
  assert(obj->is_oop_or_null(true), "expected an oop or NULL");
  HeapWord* addr = (HeapWord*)obj;
  if (_whole_span.contains(addr) && !_bit_map->isMarked(addr)) {
    // Oop lies in _span and isn't yet grey or black
    // We read the global_finger (volatile read) strictly after marking oop
    bool res = _bit_map->par_mark(addr);    // now grey
    volatile HeapWord** gfa = (volatile HeapWord**)_global_finger_addr;
    // Should we push this marked oop on our stack?
    // -- if someone else marked it, nothing to do
    // -- if target oop is above global finger nothing to do
    // -- if target oop is in chunk and above local finger
    //      then nothing to do
    // -- else push on work queue
    if (   !res       // someone else marked it, they will deal with it
        || (addr >= *gfa)  // will be scanned in a later task
        || (_span.contains(addr) && addr >= _finger)) { // later in this chunk
      return;
    }
    // the bit map iteration has already either passed, or
    // sampled, this bit in the bit map; we'll need to
    // use the marking stack to scan this oop's oops.
    bool simulate_overflow = false;
    NOT_PRODUCT(
      if (CMSMarkStackOverflowALot &&
          _collector->simulate_overflow()) {
        // simulate a stack overflow
        simulate_overflow = true;
      }
    )
    if (simulate_overflow ||
        !(_work_queue->push(obj) || _overflow_stack->par_push(obj))) {
      // stack overflow
      if (PrintCMSStatistics != 0) {
        gclog_or_tty->print_cr("CMS marking stack overflow (benign) at "
                               SIZE_FORMAT, _overflow_stack->capacity());
      }
      // We cannot assert that the overflow stack is full because
      // it may have been emptied since.
      assert(simulate_overflow ||
             _work_queue->size() == _work_queue->max_elems(),
            "Else push should have succeeded");
      handle_stack_overflow(addr);
    }
    do_yield_check();
  }
}

void Par_PushOrMarkClosure::do_oop(oop* p)       { Par_PushOrMarkClosure::do_oop_work(p); }
void Par_PushOrMarkClosure::do_oop(narrowOop* p) { Par_PushOrMarkClosure::do_oop_work(p); }

KlassRememberingOopClosure::KlassRememberingOopClosure(CMSCollector* collector,
                                             ReferenceProcessor* rp,
                                             CMSMarkStack* revisit_stack) :
  OopClosure(rp),
  _collector(collector),
  _revisit_stack(revisit_stack),
  _should_remember_klasses(collector->should_unload_classes()) {}

PushAndMarkClosure::PushAndMarkClosure(CMSCollector* collector,
                                       MemRegion span,
                                       ReferenceProcessor* rp,
                                       CMSBitMap* bit_map,
                                       CMSBitMap* mod_union_table,
                                       CMSMarkStack*  mark_stack,
                                       CMSMarkStack*  revisit_stack,
                                       bool           concurrent_precleaning):
  KlassRememberingOopClosure(collector, rp, revisit_stack),
  _span(span),
  _bit_map(bit_map),
  _mod_union_table(mod_union_table),
  _mark_stack(mark_stack),
  _concurrent_precleaning(concurrent_precleaning)
{
  assert(_ref_processor != NULL, "_ref_processor shouldn't be NULL");
}

// Grey object rescan during pre-cleaning and second checkpoint phases --
// the non-parallel version (the parallel version appears further below.)
void PushAndMarkClosure::do_oop(oop obj) {
  // Ignore mark word verification. If during concurrent precleaning,
  // the object monitor may be locked. If during the checkpoint
  // phases, the object may already have been reached by a  different
  // path and may be at the end of the global overflow list (so
  // the mark word may be NULL).
  assert(obj->is_oop_or_null(true /* ignore mark word */),
         "expected an oop or NULL");
  HeapWord* addr = (HeapWord*)obj;
  // Check if oop points into the CMS generation
  // and is not marked
  if (_span.contains(addr) && !_bit_map->isMarked(addr)) {
    // a white object ...
    _bit_map->mark(addr);         // ... now grey
    // push on the marking stack (grey set)
    bool simulate_overflow = false;
    NOT_PRODUCT(
      if (CMSMarkStackOverflowALot &&
          _collector->simulate_overflow()) {
        // simulate a stack overflow
        simulate_overflow = true;
      }
    )
    if (simulate_overflow || !_mark_stack->push(obj)) {
      if (_concurrent_precleaning) {
         // During precleaning we can just dirty the appropriate card(s)
         // in the mod union table, thus ensuring that the object remains
         // in the grey set  and continue. In the case of object arrays
         // we need to dirty all of the cards that the object spans,
         // since the rescan of object arrays will be limited to the
         // dirty cards.
         // Note that no one can be intefering with us in this action
         // of dirtying the mod union table, so no locking or atomics
         // are required.
         if (obj->is_objArray()) {
           size_t sz = obj->size();
           HeapWord* end_card_addr = (HeapWord*)round_to(
                                        (intptr_t)(addr+sz), CardTableModRefBS::card_size);
           MemRegion redirty_range = MemRegion(addr, end_card_addr);
           assert(!redirty_range.is_empty(), "Arithmetical tautology");
           _mod_union_table->mark_range(redirty_range);
         } else {
           _mod_union_table->mark(addr);
         }
         _collector->_ser_pmc_preclean_ovflw++;
      } else {
         // During the remark phase, we need to remember this oop
         // in the overflow list.
         _collector->push_on_overflow_list(obj);
         _collector->_ser_pmc_remark_ovflw++;
      }
    }
  }
}

Par_PushAndMarkClosure::Par_PushAndMarkClosure(CMSCollector* collector,
                                               MemRegion span,
                                               ReferenceProcessor* rp,
                                               CMSBitMap* bit_map,
                                               OopTaskQueue* work_queue,
                                               CMSMarkStack* revisit_stack):
  Par_KlassRememberingOopClosure(collector, rp, revisit_stack),
  _span(span),
  _bit_map(bit_map),
  _work_queue(work_queue)
{
  assert(_ref_processor != NULL, "_ref_processor shouldn't be NULL");
}

void PushAndMarkClosure::do_oop(oop* p)       { PushAndMarkClosure::do_oop_work(p); }
void PushAndMarkClosure::do_oop(narrowOop* p) { PushAndMarkClosure::do_oop_work(p); }

// Grey object rescan during second checkpoint phase --
// the parallel version.
void Par_PushAndMarkClosure::do_oop(oop obj) {
  // In the assert below, we ignore the mark word because
  // this oop may point to an already visited object that is
  // on the overflow stack (in which case the mark word has
  // been hijacked for chaining into the overflow stack --
  // if this is the last object in the overflow stack then
  // its mark word will be NULL). Because this object may
  // have been subsequently popped off the global overflow
  // stack, and the mark word possibly restored to the prototypical
  // value, by the time we get to examined this failing assert in
  // the debugger, is_oop_or_null(false) may subsequently start
  // to hold.
  assert(obj->is_oop_or_null(true),
         "expected an oop or NULL");
  HeapWord* addr = (HeapWord*)obj;
  // Check if oop points into the CMS generation
  // and is not marked
  if (_span.contains(addr) && !_bit_map->isMarked(addr)) {
    // a white object ...
    // If we manage to "claim" the object, by being the
    // first thread to mark it, then we push it on our
    // marking stack
    if (_bit_map->par_mark(addr)) {     // ... now grey
      // push on work queue (grey set)
      bool simulate_overflow = false;
      NOT_PRODUCT(
        if (CMSMarkStackOverflowALot &&
            _collector->par_simulate_overflow()) {
          // simulate a stack overflow
          simulate_overflow = true;
        }
      )
      if (simulate_overflow || !_work_queue->push(obj)) {
        _collector->par_push_on_overflow_list(obj);
        _collector->_par_pmc_remark_ovflw++; //  imprecise OK: no need to CAS
      }
    } // Else, some other thread got there first
  }
}

void Par_PushAndMarkClosure::do_oop(oop* p)       { Par_PushAndMarkClosure::do_oop_work(p); }
void Par_PushAndMarkClosure::do_oop(narrowOop* p) { Par_PushAndMarkClosure::do_oop_work(p); }

void PushAndMarkClosure::remember_mdo(DataLayout* v) {
  // TBD
}

void Par_PushAndMarkClosure::remember_mdo(DataLayout* v) {
  // TBD
}

void CMSPrecleanRefsYieldClosure::do_yield_work() {
  DEBUG_ONLY(RememberKlassesChecker mux(false);)
  Mutex* bml = _collector->bitMapLock();
  assert_lock_strong(bml);
  assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "CMS thread should hold CMS token");

  bml->unlock();
  ConcurrentMarkSweepThread::desynchronize(true);

  ConcurrentMarkSweepThread::acknowledge_yield_request();

  _collector->stopTimer();
  GCPauseTimer p(_collector->size_policy()->concurrent_timer_ptr());
  if (PrintCMSStatistics != 0) {
    _collector->incrementYields();
  }
  _collector->icms_wait();

  // See the comment in coordinator_yield()
  for (unsigned i = 0; i < CMSYieldSleepCount &&
                       ConcurrentMarkSweepThread::should_yield() &&
                       !CMSCollector::foregroundGCIsActive(); ++i) {
    os::sleep(Thread::current(), 1, false);
    ConcurrentMarkSweepThread::acknowledge_yield_request();
  }

  ConcurrentMarkSweepThread::synchronize(true);
  bml->lock();

  _collector->startTimer();
}

bool CMSPrecleanRefsYieldClosure::should_return() {
  if (ConcurrentMarkSweepThread::should_yield()) {
    do_yield_work();
  }
  return _collector->foregroundGCIsActive();
}

void MarkFromDirtyCardsClosure::do_MemRegion(MemRegion mr) {
  assert(((size_t)mr.start())%CardTableModRefBS::card_size_in_words == 0,
         "mr should be aligned to start at a card boundary");
  // We'd like to assert:
  // assert(mr.word_size()%CardTableModRefBS::card_size_in_words == 0,
  //        "mr should be a range of cards");
  // However, that would be too strong in one case -- the last
  // partition ends at _unallocated_block which, in general, can be
  // an arbitrary boundary, not necessarily card aligned.
  if (PrintCMSStatistics != 0) {
    _num_dirty_cards +=
         mr.word_size()/CardTableModRefBS::card_size_in_words;
  }
  _space->object_iterate_mem(mr, &_scan_cl);
}

SweepClosure::SweepClosure(CMSCollector* collector,
                           ConcurrentMarkSweepGeneration* g,
                           CMSBitMap* bitMap, bool should_yield) :
  _collector(collector),
  _g(g),
  _sp(g->cmsSpace()),
  _limit(_sp->sweep_limit()),
  _freelistLock(_sp->freelistLock()),
  _bitMap(bitMap),
  _yield(should_yield),
  _inFreeRange(false),           // No free range at beginning of sweep
  _freeRangeInFreeLists(false),  // No free range at beginning of sweep
  _lastFreeRangeCoalesced(false),
  _freeFinger(g->used_region().start())
{
  NOT_PRODUCT(
    _numObjectsFreed = 0;
    _numWordsFreed   = 0;
    _numObjectsLive = 0;
    _numWordsLive = 0;
    _numObjectsAlreadyFree = 0;
    _numWordsAlreadyFree = 0;
    _last_fc = NULL;

    _sp->initializeIndexedFreeListArrayReturnedBytes();
    _sp->dictionary()->initializeDictReturnedBytes();
  )
  assert(_limit >= _sp->bottom() && _limit <= _sp->end(),
         "sweep _limit out of bounds");
  if (CMSTraceSweeper) {
    gclog_or_tty->print("\n====================\nStarting new sweep\n");
  }
}

// We need this destructor to reclaim any space at the end
// of the space, which do_blk below may not have added back to
// the free lists. [basically dealing with the "fringe effect"]
SweepClosure::~SweepClosure() {
  assert_lock_strong(_freelistLock);
  // this should be treated as the end of a free run if any
  // The current free range should be returned to the free lists
  // as one coalesced chunk.
  if (inFreeRange()) {
    flushCurFreeChunk(freeFinger(),
      pointer_delta(_limit, freeFinger()));
    assert(freeFinger() < _limit, "the finger pointeth off base");
    if (CMSTraceSweeper) {
      gclog_or_tty->print("destructor:");
      gclog_or_tty->print("Sweep:put_free_blk 0x%x ("SIZE_FORMAT") "
                 "[coalesced:"SIZE_FORMAT"]\n",
                 freeFinger(), pointer_delta(_limit, freeFinger()),
                 lastFreeRangeCoalesced());
    }
  }
  NOT_PRODUCT(
    if (Verbose && PrintGC) {
      gclog_or_tty->print("Collected "SIZE_FORMAT" objects, "
                          SIZE_FORMAT " bytes",
                 _numObjectsFreed, _numWordsFreed*sizeof(HeapWord));
      gclog_or_tty->print_cr("\nLive "SIZE_FORMAT" objects,  "
                             SIZE_FORMAT" bytes  "
        "Already free "SIZE_FORMAT" objects, "SIZE_FORMAT" bytes",
        _numObjectsLive, _numWordsLive*sizeof(HeapWord),
        _numObjectsAlreadyFree, _numWordsAlreadyFree*sizeof(HeapWord));
      size_t totalBytes = (_numWordsFreed + _numWordsLive + _numWordsAlreadyFree) *
        sizeof(HeapWord);
      gclog_or_tty->print_cr("Total sweep: "SIZE_FORMAT" bytes", totalBytes);

      if (PrintCMSStatistics && CMSVerifyReturnedBytes) {
        size_t indexListReturnedBytes = _sp->sumIndexedFreeListArrayReturnedBytes();
        size_t dictReturnedBytes = _sp->dictionary()->sumDictReturnedBytes();
        size_t returnedBytes = indexListReturnedBytes + dictReturnedBytes;
        gclog_or_tty->print("Returned "SIZE_FORMAT" bytes", returnedBytes);
        gclog_or_tty->print("   Indexed List Returned "SIZE_FORMAT" bytes",
          indexListReturnedBytes);
        gclog_or_tty->print_cr("        Dictionary Returned "SIZE_FORMAT" bytes",
          dictReturnedBytes);
      }
    }
  )
  // Now, in debug mode, just null out the sweep_limit
  NOT_PRODUCT(_sp->clear_sweep_limit();)
  if (CMSTraceSweeper) {
    gclog_or_tty->print("end of sweep\n================\n");
  }
}

void SweepClosure::initialize_free_range(HeapWord* freeFinger,
    bool freeRangeInFreeLists) {
  if (CMSTraceSweeper) {
    gclog_or_tty->print("---- Start free range 0x%x with free block [%d] (%d)\n",
               freeFinger, _sp->block_size(freeFinger),
               freeRangeInFreeLists);
  }
  assert(!inFreeRange(), "Trampling existing free range");
  set_inFreeRange(true);
  set_lastFreeRangeCoalesced(false);

  set_freeFinger(freeFinger);
  set_freeRangeInFreeLists(freeRangeInFreeLists);
  if (CMSTestInFreeList) {
    if (freeRangeInFreeLists) {
      FreeChunk* fc = (FreeChunk*) freeFinger;
      assert(fc->isFree(), "A chunk on the free list should be free.");
      assert(fc->size() > 0, "Free range should have a size");
      assert(_sp->verifyChunkInFreeLists(fc), "Chunk is not in free lists");
    }
  }
}

// Note that the sweeper runs concurrently with mutators. Thus,
// it is possible for direct allocation in this generation to happen
// in the middle of the sweep. Note that the sweeper also coalesces
// contiguous free blocks. Thus, unless the sweeper and the allocator
// synchronize appropriately freshly allocated blocks may get swept up.
// This is accomplished by the sweeper locking the free lists while
// it is sweeping. Thus blocks that are determined to be free are
// indeed free. There is however one additional complication:
// blocks that have been allocated since the final checkpoint and
// mark, will not have been marked and so would be treated as
// unreachable and swept up. To prevent this, the allocator marks
// the bit map when allocating during the sweep phase. This leads,
// however, to a further complication -- objects may have been allocated
// but not yet initialized -- in the sense that the header isn't yet
// installed. The sweeper can not then determine the size of the block
// in order to skip over it. To deal with this case, we use a technique
// (due to Printezis) to encode such uninitialized block sizes in the
// bit map. Since the bit map uses a bit per every HeapWord, but the
// CMS generation has a minimum object size of 3 HeapWords, it follows
// that "normal marks" won't be adjacent in the bit map (there will
// always be at least two 0 bits between successive 1 bits). We make use
// of these "unused" bits to represent uninitialized blocks -- the bit
// corresponding to the start of the uninitialized object and the next
// bit are both set. Finally, a 1 bit marks the end of the object that
// started with the two consecutive 1 bits to indicate its potentially
// uninitialized state.

size_t SweepClosure::do_blk_careful(HeapWord* addr) {
  FreeChunk* fc = (FreeChunk*)addr;
  size_t res;

  // check if we are done sweepinrg
  if (addr == _limit) { // we have swept up to the limit, do nothing more
    assert(_limit >= _sp->bottom() && _limit <= _sp->end(),
           "sweep _limit out of bounds");
    // help the closure application finish
    return pointer_delta(_sp->end(), _limit);
  }
  assert(addr <= _limit, "sweep invariant");

  // check if we should yield
  do_yield_check(addr);
  if (fc->isFree()) {
    // Chunk that is already free
    res = fc->size();
    doAlreadyFreeChunk(fc);
    debug_only(_sp->verifyFreeLists());
    assert(res == fc->size(), "Don't expect the size to change");
    NOT_PRODUCT(
      _numObjectsAlreadyFree++;
      _numWordsAlreadyFree += res;
    )
    NOT_PRODUCT(_last_fc = fc;)
  } else if (!_bitMap->isMarked(addr)) {
    // Chunk is fresh garbage
    res = doGarbageChunk(fc);
    debug_only(_sp->verifyFreeLists());
    NOT_PRODUCT(
      _numObjectsFreed++;
      _numWordsFreed += res;
    )
  } else {
    // Chunk that is alive.
    res = doLiveChunk(fc);
    debug_only(_sp->verifyFreeLists());
    NOT_PRODUCT(
        _numObjectsLive++;
        _numWordsLive += res;
    )
  }
  return res;
}

// For the smart allocation, record following
//  split deaths - a free chunk is removed from its free list because
//      it is being split into two or more chunks.
//  split birth - a free chunk is being added to its free list because
//      a larger free chunk has been split and resulted in this free chunk.
//  coal death - a free chunk is being removed from its free list because
//      it is being coalesced into a large free chunk.
//  coal birth - a free chunk is being added to its free list because
//      it was created when two or more free chunks where coalesced into
//      this free chunk.
//
// These statistics are used to determine the desired number of free
// chunks of a given size.  The desired number is chosen to be relative
// to the end of a CMS sweep.  The desired number at the end of a sweep
// is the
//      count-at-end-of-previous-sweep (an amount that was enough)
//              - count-at-beginning-of-current-sweep  (the excess)
//              + split-births  (gains in this size during interval)
//              - split-deaths  (demands on this size during interval)
// where the interval is from the end of one sweep to the end of the
// next.
//
// When sweeping the sweeper maintains an accumulated chunk which is
// the chunk that is made up of chunks that have been coalesced.  That
// will be termed the left-hand chunk.  A new chunk of garbage that
// is being considered for coalescing will be referred to as the
// right-hand chunk.
//
// When making a decision on whether to coalesce a right-hand chunk with
// the current left-hand chunk, the current count vs. the desired count
// of the left-hand chunk is considered.  Also if the right-hand chunk
// is near the large chunk at the end of the heap (see
// ConcurrentMarkSweepGeneration::isNearLargestChunk()), then the
// left-hand chunk is coalesced.
//
// When making a decision about whether to split a chunk, the desired count
// vs. the current count of the candidate to be split is also considered.
// If the candidate is underpopulated (currently fewer chunks than desired)
// a chunk of an overpopulated (currently more chunks than desired) size may
// be chosen.  The "hint" associated with a free list, if non-null, points
// to a free list which may be overpopulated.
//

void SweepClosure::doAlreadyFreeChunk(FreeChunk* fc) {
  size_t size = fc->size();
  // Chunks that cannot be coalesced are not in the
  // free lists.
  if (CMSTestInFreeList && !fc->cantCoalesce()) {
    assert(_sp->verifyChunkInFreeLists(fc),
      "free chunk should be in free lists");
  }
  // a chunk that is already free, should not have been
  // marked in the bit map
  HeapWord* addr = (HeapWord*) fc;
  assert(!_bitMap->isMarked(addr), "free chunk should be unmarked");
  // Verify that the bit map has no bits marked between
  // addr and purported end of this block.
  _bitMap->verifyNoOneBitsInRange(addr + 1, addr + size);

  // Some chunks cannot be coalesced in under any circumstances.
  // See the definition of cantCoalesce().
  if (!fc->cantCoalesce()) {
    // This chunk can potentially be coalesced.
    if (_sp->adaptive_freelists()) {
      // All the work is done in
      doPostIsFreeOrGarbageChunk(fc, size);
    } else {  // Not adaptive free lists
      // this is a free chunk that can potentially be coalesced by the sweeper;
      if (!inFreeRange()) {
        // if the next chunk is a free block that can't be coalesced
        // it doesn't make sense to remove this chunk from the free lists
        FreeChunk* nextChunk = (FreeChunk*)(addr + size);
        assert((HeapWord*)nextChunk <= _limit, "sweep invariant");
        if ((HeapWord*)nextChunk < _limit  &&    // there's a next chunk...
            nextChunk->isFree()    &&            // which is free...
            nextChunk->cantCoalesce()) {         // ... but cant be coalesced
          // nothing to do
        } else {
          // Potentially the start of a new free range:
          // Don't eagerly remove it from the free lists.
          // No need to remove it if it will just be put
          // back again.  (Also from a pragmatic point of view
          // if it is a free block in a region that is beyond
          // any allocated blocks, an assertion will fail)
          // Remember the start of a free run.
          initialize_free_range(addr, true);
          // end - can coalesce with next chunk
        }
      } else {
        // the midst of a free range, we are coalescing
        debug_only(record_free_block_coalesced(fc);)
        if (CMSTraceSweeper) {
          gclog_or_tty->print("  -- pick up free block 0x%x (%d)\n", fc, size);
        }
        // remove it from the free lists
        _sp->removeFreeChunkFromFreeLists(fc);
        set_lastFreeRangeCoalesced(true);
        // If the chunk is being coalesced and the current free range is
        // in the free lists, remove the current free range so that it
        // will be returned to the free lists in its entirety - all
        // the coalesced pieces included.
        if (freeRangeInFreeLists()) {
          FreeChunk* ffc = (FreeChunk*) freeFinger();
          assert(ffc->size() == pointer_delta(addr, freeFinger()),
            "Size of free range is inconsistent with chunk size.");
          if (CMSTestInFreeList) {
            assert(_sp->verifyChunkInFreeLists(ffc),
              "free range is not in free lists");
          }
          _sp->removeFreeChunkFromFreeLists(ffc);
          set_freeRangeInFreeLists(false);
        }
      }
    }
  } else {
    // Code path common to both original and adaptive free lists.

    // cant coalesce with previous block; this should be treated
    // as the end of a free run if any
    if (inFreeRange()) {
      // we kicked some butt; time to pick up the garbage
      assert(freeFinger() < addr, "the finger pointeth off base");
      flushCurFreeChunk(freeFinger(), pointer_delta(addr, freeFinger()));
    }
    // else, nothing to do, just continue
  }
}

size_t SweepClosure::doGarbageChunk(FreeChunk* fc) {
  // This is a chunk of garbage.  It is not in any free list.
  // Add it to a free list or let it possibly be coalesced into
  // a larger chunk.
  HeapWord* addr = (HeapWord*) fc;
  size_t size = CompactibleFreeListSpace::adjustObjectSize(oop(addr)->size());

  if (_sp->adaptive_freelists()) {
    // Verify that the bit map has no bits marked between
    // addr and purported end of just dead object.
    _bitMap->verifyNoOneBitsInRange(addr + 1, addr + size);

    doPostIsFreeOrGarbageChunk(fc, size);
  } else {
    if (!inFreeRange()) {
      // start of a new free range
      assert(size > 0, "A free range should have a size");
      initialize_free_range(addr, false);

    } else {
      // this will be swept up when we hit the end of the
      // free range
      if (CMSTraceSweeper) {
        gclog_or_tty->print("  -- pick up garbage 0x%x (%d) \n", fc, size);
      }
      // If the chunk is being coalesced and the current free range is
      // in the free lists, remove the current free range so that it
      // will be returned to the free lists in its entirety - all
      // the coalesced pieces included.
      if (freeRangeInFreeLists()) {
        FreeChunk* ffc = (FreeChunk*)freeFinger();
        assert(ffc->size() == pointer_delta(addr, freeFinger()),
          "Size of free range is inconsistent with chunk size.");
        if (CMSTestInFreeList) {
          assert(_sp->verifyChunkInFreeLists(ffc),
            "free range is not in free lists");
        }
        _sp->removeFreeChunkFromFreeLists(ffc);
        set_freeRangeInFreeLists(false);
      }
      set_lastFreeRangeCoalesced(true);
    }
    // this will be swept up when we hit the end of the free range

    // Verify that the bit map has no bits marked between
    // addr and purported end of just dead object.
    _bitMap->verifyNoOneBitsInRange(addr + 1, addr + size);
  }
  return size;
}

size_t SweepClosure::doLiveChunk(FreeChunk* fc) {
  HeapWord* addr = (HeapWord*) fc;
  // The sweeper has just found a live object. Return any accumulated
  // left hand chunk to the free lists.
  if (inFreeRange()) {
    if (_sp->adaptive_freelists()) {
      flushCurFreeChunk(freeFinger(),
                        pointer_delta(addr, freeFinger()));
    } else { // not adaptive freelists
      set_inFreeRange(false);
      // Add the free range back to the free list if it is not already
      // there.
      if (!freeRangeInFreeLists()) {
        assert(freeFinger() < addr, "the finger pointeth off base");
        if (CMSTraceSweeper) {
          gclog_or_tty->print("Sweep:put_free_blk 0x%x (%d) "
            "[coalesced:%d]\n",
            freeFinger(), pointer_delta(addr, freeFinger()),
            lastFreeRangeCoalesced());
        }
        _sp->addChunkAndRepairOffsetTable(freeFinger(),
          pointer_delta(addr, freeFinger()), lastFreeRangeCoalesced());
      }
    }
  }

  // Common code path for original and adaptive free lists.

  // this object is live: we'd normally expect this to be
  // an oop, and like to assert the following:
  // assert(oop(addr)->is_oop(), "live block should be an oop");
  // However, as we commented above, this may be an object whose
  // header hasn't yet been initialized.
  size_t size;
  assert(_bitMap->isMarked(addr), "Tautology for this control point");
  if (_bitMap->isMarked(addr + 1)) {
    // Determine the size from the bit map, rather than trying to
    // compute it from the object header.
    HeapWord* nextOneAddr = _bitMap->getNextMarkedWordAddress(addr + 2);
    size = pointer_delta(nextOneAddr + 1, addr);
    assert(size == CompactibleFreeListSpace::adjustObjectSize(size),
           "alignment problem");

    #ifdef DEBUG
      if (oop(addr)->klass_or_null() != NULL &&
          (   !_collector->should_unload_classes()
           || (oop(addr)->is_parsable()) &&
               oop(addr)->is_conc_safe())) {
        // Ignore mark word because we are running concurrent with mutators
        assert(oop(addr)->is_oop(true), "live block should be an oop");
        // is_conc_safe is checked before performing this assertion
        // because an object that is not is_conc_safe may yet have
        // the return from size() correct.
        assert(size ==
               CompactibleFreeListSpace::adjustObjectSize(oop(addr)->size()),
               "P-mark and computed size do not agree");
      }
    #endif

  } else {
    // This should be an initialized object that's alive.
    assert(oop(addr)->klass_or_null() != NULL &&
           (!_collector->should_unload_classes()
            || oop(addr)->is_parsable()),
           "Should be an initialized object");
    // Note that there are objects used during class redefinition
    // (e.g., merge_cp in VM_RedefineClasses::merge_cp_and_rewrite()
    // which are discarded with their is_conc_safe state still
    // false.  These object may be floating garbage so may be
    // seen here.  If they are floating garbage their size
    // should be attainable from their klass.  Do not that
    // is_conc_safe() is true for oop(addr).
    // Ignore mark word because we are running concurrent with mutators
    assert(oop(addr)->is_oop(true), "live block should be an oop");
    // Verify that the bit map has no bits marked between
    // addr and purported end of this block.
    size = CompactibleFreeListSpace::adjustObjectSize(oop(addr)->size());
    assert(size >= 3, "Necessary for Printezis marks to work");
    assert(!_bitMap->isMarked(addr+1), "Tautology for this control point");
    DEBUG_ONLY(_bitMap->verifyNoOneBitsInRange(addr+2, addr+size);)
  }
  return size;
}

void SweepClosure::doPostIsFreeOrGarbageChunk(FreeChunk* fc,
                                            size_t chunkSize) {
  // doPostIsFreeOrGarbageChunk() should only be called in the smart allocation
  // scheme.
  bool fcInFreeLists = fc->isFree();
  assert(_sp->adaptive_freelists(), "Should only be used in this case.");
  assert((HeapWord*)fc <= _limit, "sweep invariant");
  if (CMSTestInFreeList && fcInFreeLists) {
    assert(_sp->verifyChunkInFreeLists(fc),
      "free chunk is not in free lists");
  }


  if (CMSTraceSweeper) {
    gclog_or_tty->print_cr("  -- pick up another chunk at 0x%x (%d)", fc, chunkSize);
  }

  HeapWord* addr = (HeapWord*) fc;

  bool coalesce;
  size_t left  = pointer_delta(addr, freeFinger());
  size_t right = chunkSize;
  switch (FLSCoalescePolicy) {
    // numeric value forms a coalition aggressiveness metric
    case 0:  { // never coalesce
      coalesce = false;
      break;
    }
    case 1: { // coalesce if left & right chunks on overpopulated lists
      coalesce = _sp->coalOverPopulated(left) &&
                 _sp->coalOverPopulated(right);
      break;
    }
    case 2: { // coalesce if left chunk on overpopulated list (default)
      coalesce = _sp->coalOverPopulated(left);
      break;
    }
    case 3: { // coalesce if left OR right chunk on overpopulated list
      coalesce = _sp->coalOverPopulated(left) ||
                 _sp->coalOverPopulated(right);
      break;
    }
    case 4: { // always coalesce
      coalesce = true;
      break;
    }
    default:
     ShouldNotReachHere();
  }

  // Should the current free range be coalesced?
  // If the chunk is in a free range and either we decided to coalesce above
  // or the chunk is near the large block at the end of the heap
  // (isNearLargestChunk() returns true), then coalesce this chunk.
  bool doCoalesce = inFreeRange() &&
    (coalesce || _g->isNearLargestChunk((HeapWord*)fc));
  if (doCoalesce) {
    // Coalesce the current free range on the left with the new
    // chunk on the right.  If either is on a free list,
    // it must be removed from the list and stashed in the closure.
    if (freeRangeInFreeLists()) {
      FreeChunk* ffc = (FreeChunk*)freeFinger();
      assert(ffc->size() == pointer_delta(addr, freeFinger()),
        "Size of free range is inconsistent with chunk size.");
      if (CMSTestInFreeList) {
        assert(_sp->verifyChunkInFreeLists(ffc),
          "Chunk is not in free lists");
      }
      _sp->coalDeath(ffc->size());
      _sp->removeFreeChunkFromFreeLists(ffc);
      set_freeRangeInFreeLists(false);
    }
    if (fcInFreeLists) {
      _sp->coalDeath(chunkSize);
      assert(fc->size() == chunkSize,
        "The chunk has the wrong size or is not in the free lists");
      _sp->removeFreeChunkFromFreeLists(fc);
    }
    set_lastFreeRangeCoalesced(true);
  } else {  // not in a free range and/or should not coalesce
    // Return the current free range and start a new one.
    if (inFreeRange()) {
      // In a free range but cannot coalesce with the right hand chunk.
      // Put the current free range into the free lists.
      flushCurFreeChunk(freeFinger(),
        pointer_delta(addr, freeFinger()));
    }
    // Set up for new free range.  Pass along whether the right hand
    // chunk is in the free lists.
    initialize_free_range((HeapWord*)fc, fcInFreeLists);
  }
}
void SweepClosure::flushCurFreeChunk(HeapWord* chunk, size_t size) {
  assert(inFreeRange(), "Should only be called if currently in a free range.");
  assert(size > 0,
    "A zero sized chunk cannot be added to the free lists.");
  if (!freeRangeInFreeLists()) {
    if(CMSTestInFreeList) {
      FreeChunk* fc = (FreeChunk*) chunk;
      fc->setSize(size);
      assert(!_sp->verifyChunkInFreeLists(fc),
        "chunk should not be in free lists yet");
    }
    if (CMSTraceSweeper) {
      gclog_or_tty->print_cr(" -- add free block 0x%x (%d) to free lists",
                    chunk, size);
    }
    // A new free range is going to be starting.  The current
    // free range has not been added to the free lists yet or
    // was removed so add it back.
    // If the current free range was coalesced, then the death
    // of the free range was recorded.  Record a birth now.
    if (lastFreeRangeCoalesced()) {
      _sp->coalBirth(size);
    }
    _sp->addChunkAndRepairOffsetTable(chunk, size,
            lastFreeRangeCoalesced());
  }
  set_inFreeRange(false);
  set_freeRangeInFreeLists(false);
}

// We take a break if we've been at this for a while,
// so as to avoid monopolizing the locks involved.
void SweepClosure::do_yield_work(HeapWord* addr) {
  // Return current free chunk being used for coalescing (if any)
  // to the appropriate freelist.  After yielding, the next
  // free block encountered will start a coalescing range of
  // free blocks.  If the next free block is adjacent to the
  // chunk just flushed, they will need to wait for the next
  // sweep to be coalesced.
  if (inFreeRange()) {
    flushCurFreeChunk(freeFinger(), pointer_delta(addr, freeFinger()));
  }

  // First give up the locks, then yield, then re-lock.
  // We should probably use a constructor/destructor idiom to
  // do this unlock/lock or modify the MutexUnlocker class to
  // serve our purpose. XXX
  assert_lock_strong(_bitMap->lock());
  assert_lock_strong(_freelistLock);
  assert(ConcurrentMarkSweepThread::cms_thread_has_cms_token(),
         "CMS thread should hold CMS token");
  _bitMap->lock()->unlock();
  _freelistLock->unlock();
  ConcurrentMarkSweepThread::desynchronize(true);
  ConcurrentMarkSweepThread::acknowledge_yield_request();
  _collector->stopTimer();
  GCPauseTimer p(_collector->size_policy()->concurrent_timer_ptr());
  if (PrintCMSStatistics != 0) {
    _collector->incrementYields();
  }
  _collector->icms_wait();

  // See the comment in coordinator_yield()
  for (unsigned i = 0; i < CMSYieldSleepCount &&
                       ConcurrentMarkSweepThread::should_yield() &&
                       !CMSCollector::foregroundGCIsActive(); ++i) {
    os::sleep(Thread::current(), 1, false);
    ConcurrentMarkSweepThread::acknowledge_yield_request();
  }

  ConcurrentMarkSweepThread::synchronize(true);
  _freelistLock->lock();
  _bitMap->lock()->lock_without_safepoint_check();
  _collector->startTimer();
}

#ifndef PRODUCT
// This is actually very useful in a product build if it can
// be called from the debugger.  Compile it into the product
// as needed.
bool debug_verifyChunkInFreeLists(FreeChunk* fc) {
  return debug_cms_space->verifyChunkInFreeLists(fc);
}

void SweepClosure::record_free_block_coalesced(FreeChunk* fc) const {
  if (CMSTraceSweeper) {
    gclog_or_tty->print("Sweep:coal_free_blk 0x%x (%d)\n", fc, fc->size());
  }
}
#endif

// CMSIsAliveClosure
bool CMSIsAliveClosure::do_object_b(oop obj) {
  HeapWord* addr = (HeapWord*)obj;
  return addr != NULL &&
         (!_span.contains(addr) || _bit_map->isMarked(addr));
}

CMSKeepAliveClosure::CMSKeepAliveClosure( CMSCollector* collector,
                      MemRegion span,
                      CMSBitMap* bit_map, CMSMarkStack* mark_stack,
                      CMSMarkStack* revisit_stack, bool cpc):
  KlassRememberingOopClosure(collector, NULL, revisit_stack),
  _span(span),
  _bit_map(bit_map),
  _mark_stack(mark_stack),
  _concurrent_precleaning(cpc) {
  assert(!_span.is_empty(), "Empty span could spell trouble");
}


// CMSKeepAliveClosure: the serial version
void CMSKeepAliveClosure::do_oop(oop obj) {
  HeapWord* addr = (HeapWord*)obj;
  if (_span.contains(addr) &&
      !_bit_map->isMarked(addr)) {
    _bit_map->mark(addr);
    bool simulate_overflow = false;
    NOT_PRODUCT(
      if (CMSMarkStackOverflowALot &&
          _collector->simulate_overflow()) {
        // simulate a stack overflow
        simulate_overflow = true;
      }
    )
    if (simulate_overflow || !_mark_stack->push(obj)) {
      if (_concurrent_precleaning) {
        // We dirty the overflown object and let the remark
        // phase deal with it.
        assert(_collector->overflow_list_is_empty(), "Error");
        // In the case of object arrays, we need to dirty all of
        // the cards that the object spans. No locking or atomics
        // are needed since no one else can be mutating the mod union
        // table.
        if (obj->is_objArray()) {
          size_t sz = obj->size();
          HeapWord* end_card_addr =
            (HeapWord*)round_to((intptr_t)(addr+sz), CardTableModRefBS::card_size);
          MemRegion redirty_range = MemRegion(addr, end_card_addr);
          assert(!redirty_range.is_empty(), "Arithmetical tautology");
          _collector->_modUnionTable.mark_range(redirty_range);
        } else {
          _collector->_modUnionTable.mark(addr);
        }
        _collector->_ser_kac_preclean_ovflw++;
      } else {
        _collector->push_on_overflow_list(obj);
        _collector->_ser_kac_ovflw++;
      }
    }
  }
}

void CMSKeepAliveClosure::do_oop(oop* p)       { CMSKeepAliveClosure::do_oop_work(p); }
void CMSKeepAliveClosure::do_oop(narrowOop* p) { CMSKeepAliveClosure::do_oop_work(p); }

// CMSParKeepAliveClosure: a parallel version of the above.
// The work queues are private to each closure (thread),
// but (may be) available for stealing by other threads.
void CMSParKeepAliveClosure::do_oop(oop obj) {
  HeapWord* addr = (HeapWord*)obj;
  if (_span.contains(addr) &&
      !_bit_map->isMarked(addr)) {
    // In general, during recursive tracing, several threads
    // may be concurrently getting here; the first one to
    // "tag" it, claims it.
    if (_bit_map->par_mark(addr)) {
      bool res = _work_queue->push(obj);
      assert(res, "Low water mark should be much less than capacity");
      // Do a recursive trim in the hope that this will keep
      // stack usage lower, but leave some oops for potential stealers
      trim_queue(_low_water_mark);
    } // Else, another thread got there first
  }
}

void CMSParKeepAliveClosure::do_oop(oop* p)       { CMSParKeepAliveClosure::do_oop_work(p); }
void CMSParKeepAliveClosure::do_oop(narrowOop* p) { CMSParKeepAliveClosure::do_oop_work(p); }

void CMSParKeepAliveClosure::trim_queue(uint max) {
  while (_work_queue->size() > max) {
    oop new_oop;
    if (_work_queue->pop_local(new_oop)) {
      assert(new_oop != NULL && new_oop->is_oop(), "Expected an oop");
      assert(_bit_map->isMarked((HeapWord*)new_oop),
             "no white objects on this stack!");
      assert(_span.contains((HeapWord*)new_oop), "Out of bounds oop");
      // iterate over the oops in this oop, marking and pushing
      // the ones in CMS heap (i.e. in _span).
      new_oop->oop_iterate(&_mark_and_push);
    }
  }
}

CMSInnerParMarkAndPushClosure::CMSInnerParMarkAndPushClosure(
                                CMSCollector* collector,
                                MemRegion span, CMSBitMap* bit_map,
                                CMSMarkStack* revisit_stack,
                                OopTaskQueue* work_queue):
  Par_KlassRememberingOopClosure(collector, NULL, revisit_stack),
  _span(span),
  _bit_map(bit_map),
  _work_queue(work_queue) { }

void CMSInnerParMarkAndPushClosure::do_oop(oop obj) {
  HeapWord* addr = (HeapWord*)obj;
  if (_span.contains(addr) &&
      !_bit_map->isMarked(addr)) {
    if (_bit_map->par_mark(addr)) {
      bool simulate_overflow = false;
      NOT_PRODUCT(
        if (CMSMarkStackOverflowALot &&
            _collector->par_simulate_overflow()) {
          // simulate a stack overflow
          simulate_overflow = true;
        }
      )
      if (simulate_overflow || !_work_queue->push(obj)) {
        _collector->par_push_on_overflow_list(obj);
        _collector->_par_kac_ovflw++;
      }
    } // Else another thread got there already
  }
}

void CMSInnerParMarkAndPushClosure::do_oop(oop* p)       { CMSInnerParMarkAndPushClosure::do_oop_work(p); }
void CMSInnerParMarkAndPushClosure::do_oop(narrowOop* p) { CMSInnerParMarkAndPushClosure::do_oop_work(p); }

//////////////////////////////////////////////////////////////////
//  CMSExpansionCause                /////////////////////////////
//////////////////////////////////////////////////////////////////
const char* CMSExpansionCause::to_string(CMSExpansionCause::Cause cause) {
  switch (cause) {
    case _no_expansion:
      return "No expansion";
    case _satisfy_free_ratio:
      return "Free ratio";
    case _satisfy_promotion:
      return "Satisfy promotion";
    case _satisfy_allocation:
      return "allocation";
    case _allocate_par_lab:
      return "Par LAB";
    case _allocate_par_spooling_space:
      return "Par Spooling Space";
    case _adaptive_size_policy:
      return "Ergonomics";
    default:
      return "unknown";
  }
}

void CMSDrainMarkingStackClosure::do_void() {
  // the max number to take from overflow list at a time
  const size_t num = _mark_stack->capacity()/4;
  assert(!_concurrent_precleaning || _collector->overflow_list_is_empty(),
         "Overflow list should be NULL during concurrent phases");
  while (!_mark_stack->isEmpty() ||
         // if stack is empty, check the overflow list
         _collector->take_from_overflow_list(num, _mark_stack)) {
    oop obj = _mark_stack->pop();
    HeapWord* addr = (HeapWord*)obj;
    assert(_span.contains(addr), "Should be within span");
    assert(_bit_map->isMarked(addr), "Should be marked");
    assert(obj->is_oop(), "Should be an oop");
    obj->oop_iterate(_keep_alive);
  }
}

void CMSParDrainMarkingStackClosure::do_void() {
  // drain queue
  trim_queue(0);
}

// Trim our work_queue so its length is below max at return
void CMSParDrainMarkingStackClosure::trim_queue(uint max) {
  while (_work_queue->size() > max) {
    oop new_oop;
    if (_work_queue->pop_local(new_oop)) {
      assert(new_oop->is_oop(), "Expected an oop");
      assert(_bit_map->isMarked((HeapWord*)new_oop),
             "no white objects on this stack!");
      assert(_span.contains((HeapWord*)new_oop), "Out of bounds oop");
      // iterate over the oops in this oop, marking and pushing
      // the ones in CMS heap (i.e. in _span).
      new_oop->oop_iterate(&_mark_and_push);
    }
  }
}

////////////////////////////////////////////////////////////////////
// Support for Marking Stack Overflow list handling and related code
////////////////////////////////////////////////////////////////////
// Much of the following code is similar in shape and spirit to the
// code used in ParNewGC. We should try and share that code
// as much as possible in the future.

#ifndef PRODUCT
// Debugging support for CMSStackOverflowALot

// It's OK to call this multi-threaded;  the worst thing
// that can happen is that we'll get a bunch of closely
// spaced simulated oveflows, but that's OK, in fact
// probably good as it would exercise the overflow code
// under contention.
bool CMSCollector::simulate_overflow() {
  if (_overflow_counter-- <= 0) { // just being defensive
    _overflow_counter = CMSMarkStackOverflowInterval;
    return true;
  } else {
    return false;
  }
}

bool CMSCollector::par_simulate_overflow() {
  return simulate_overflow();
}
#endif

// Single-threaded
bool CMSCollector::take_from_overflow_list(size_t num, CMSMarkStack* stack) {
  assert(stack->isEmpty(), "Expected precondition");
  assert(stack->capacity() > num, "Shouldn't bite more than can chew");
  size_t i = num;
  oop  cur = _overflow_list;
  const markOop proto = markOopDesc::prototype();
  NOT_PRODUCT(ssize_t n = 0;)
  for (oop next; i > 0 && cur != NULL; cur = next, i--) {
    next = oop(cur->mark());
    cur->set_mark(proto);   // until proven otherwise
    assert(cur->is_oop(), "Should be an oop");
    bool res = stack->push(cur);
    assert(res, "Bit off more than can chew?");
    NOT_PRODUCT(n++;)
  }
  _overflow_list = cur;
#ifndef PRODUCT
  assert(_num_par_pushes >= n, "Too many pops?");
  _num_par_pushes -=n;
#endif
  return !stack->isEmpty();
}

#define BUSY  (oop(0x1aff1aff))
// (MT-safe) Get a prefix of at most "num" from the list.
// The overflow list is chained through the mark word of
// each object in the list. We fetch the entire list,
// break off a prefix of the right size and return the
// remainder. If other threads try to take objects from
// the overflow list at that time, they will wait for
// some time to see if data becomes available. If (and
// only if) another thread places one or more object(s)
// on the global list before we have returned the suffix
// to the global list, we will walk down our local list
// to find its end and append the global list to
// our suffix before returning it. This suffix walk can
// prove to be expensive (quadratic in the amount of traffic)
// when there are many objects in the overflow list and
// there is much producer-consumer contention on the list.
// *NOTE*: The overflow list manipulation code here and
// in ParNewGeneration:: are very similar in shape,
// except that in the ParNew case we use the old (from/eden)
// copy of the object to thread the list via its klass word.
// Because of the common code, if you make any changes in
// the code below, please check the ParNew version to see if
// similar changes might be needed.
// CR 6797058 has been filed to consolidate the common code.
bool CMSCollector::par_take_from_overflow_list(size_t num,
                                               OopTaskQueue* work_q) {
  assert(work_q->size() == 0, "First empty local work queue");
  assert(num < work_q->max_elems(), "Can't bite more than we can chew");
  if (_overflow_list == NULL) {
    return false;
  }
  // Grab the entire list; we'll put back a suffix
  oop prefix = (oop)Atomic::xchg_ptr(BUSY, &_overflow_list);
  Thread* tid = Thread::current();
  size_t CMSOverflowSpinCount = (size_t)ParallelGCThreads;
  size_t sleep_time_millis = MAX2((size_t)1, num/100);
  // If the list is busy, we spin for a short while,
  // sleeping between attempts to get the list.
  for (size_t spin = 0; prefix == BUSY && spin < CMSOverflowSpinCount; spin++) {
    os::sleep(tid, sleep_time_millis, false);
    if (_overflow_list == NULL) {
      // Nothing left to take
      return false;
    } else if (_overflow_list != BUSY) {
      // Try and grab the prefix
      prefix = (oop)Atomic::xchg_ptr(BUSY, &_overflow_list);
    }
  }
  // If the list was found to be empty, or we spun long
  // enough, we give up and return empty-handed. If we leave
  // the list in the BUSY state below, it must be the case that
  // some other thread holds the overflow list and will set it
  // to a non-BUSY state in the future.
  if (prefix == NULL || prefix == BUSY) {
     // Nothing to take or waited long enough
     if (prefix == NULL) {
       // Write back the NULL in case we overwrote it with BUSY above
       // and it is still the same value.
       (void) Atomic::cmpxchg_ptr(NULL, &_overflow_list, BUSY);
     }
     return false;
  }
  assert(prefix != NULL && prefix != BUSY, "Error");
  size_t i = num;
  oop cur = prefix;
  // Walk down the first "num" objects, unless we reach the end.
  for (; i > 1 && cur->mark() != NULL; cur = oop(cur->mark()), i--);
  if (cur->mark() == NULL) {
    // We have "num" or fewer elements in the list, so there
    // is nothing to return to the global list.
    // Write back the NULL in lieu of the BUSY we wrote
    // above, if it is still the same value.
    if (_overflow_list == BUSY) {
      (void) Atomic::cmpxchg_ptr(NULL, &_overflow_list, BUSY);
    }
  } else {
    // Chop off the suffix and rerturn it to the global list.
    assert(cur->mark() != BUSY, "Error");
    oop suffix_head = cur->mark(); // suffix will be put back on global list
    cur->set_mark(NULL);           // break off suffix
    // It's possible that the list is still in the empty(busy) state
    // we left it in a short while ago; in that case we may be
    // able to place back the suffix without incurring the cost
    // of a walk down the list.
    oop observed_overflow_list = _overflow_list;
    oop cur_overflow_list = observed_overflow_list;
    bool attached = false;
    while (observed_overflow_list == BUSY || observed_overflow_list == NULL) {
      observed_overflow_list =
        (oop) Atomic::cmpxchg_ptr(suffix_head, &_overflow_list, cur_overflow_list);
      if (cur_overflow_list == observed_overflow_list) {
        attached = true;
        break;
      } else cur_overflow_list = observed_overflow_list;
    }
    if (!attached) {
      // Too bad, someone else sneaked in (at least) an element; we'll need
      // to do a splice. Find tail of suffix so we can prepend suffix to global
      // list.
      for (cur = suffix_head; cur->mark() != NULL; cur = (oop)(cur->mark()));
      oop suffix_tail = cur;
      assert(suffix_tail != NULL && suffix_tail->mark() == NULL,
             "Tautology");
      observed_overflow_list = _overflow_list;
      do {
        cur_overflow_list = observed_overflow_list;
        if (cur_overflow_list != BUSY) {
          // Do the splice ...
          suffix_tail->set_mark(markOop(cur_overflow_list));
        } else { // cur_overflow_list == BUSY
          suffix_tail->set_mark(NULL);
        }
        // ... and try to place spliced list back on overflow_list ...
        observed_overflow_list =
          (oop) Atomic::cmpxchg_ptr(suffix_head, &_overflow_list, cur_overflow_list);
      } while (cur_overflow_list != observed_overflow_list);
      // ... until we have succeeded in doing so.
    }
  }

  // Push the prefix elements on work_q
  assert(prefix != NULL, "control point invariant");
  const markOop proto = markOopDesc::prototype();
  oop next;
  NOT_PRODUCT(ssize_t n = 0;)
  for (cur = prefix; cur != NULL; cur = next) {
    next = oop(cur->mark());
    cur->set_mark(proto);   // until proven otherwise
    assert(cur->is_oop(), "Should be an oop");
    bool res = work_q->push(cur);
    assert(res, "Bit off more than we can chew?");
    NOT_PRODUCT(n++;)
  }
#ifndef PRODUCT
  assert(_num_par_pushes >= n, "Too many pops?");
  Atomic::add_ptr(-(intptr_t)n, &_num_par_pushes);
#endif
  return true;
}

// Single-threaded
void CMSCollector::push_on_overflow_list(oop p) {
  NOT_PRODUCT(_num_par_pushes++;)
  assert(p->is_oop(), "Not an oop");
  preserve_mark_if_necessary(p);
  p->set_mark((markOop)_overflow_list);
  _overflow_list = p;
}

// Multi-threaded; use CAS to prepend to overflow list
void CMSCollector::par_push_on_overflow_list(oop p) {
  NOT_PRODUCT(Atomic::inc_ptr(&_num_par_pushes);)
  assert(p->is_oop(), "Not an oop");
  par_preserve_mark_if_necessary(p);
  oop observed_overflow_list = _overflow_list;
  oop cur_overflow_list;
  do {
    cur_overflow_list = observed_overflow_list;
    if (cur_overflow_list != BUSY) {
      p->set_mark(markOop(cur_overflow_list));
    } else {
      p->set_mark(NULL);
    }
    observed_overflow_list =
      (oop) Atomic::cmpxchg_ptr(p, &_overflow_list, cur_overflow_list);
  } while (cur_overflow_list != observed_overflow_list);
}
#undef BUSY

// Single threaded
// General Note on GrowableArray: pushes may silently fail
// because we are (temporarily) out of C-heap for expanding
// the stack. The problem is quite ubiquitous and affects
// a lot of code in the JVM. The prudent thing for GrowableArray
// to do (for now) is to exit with an error. However, that may
// be too draconian in some cases because the caller may be
// able to recover without much harm. For such cases, we
// should probably introduce a "soft_push" method which returns
// an indication of success or failure with the assumption that
// the caller may be able to recover from a failure; code in
// the VM can then be changed, incrementally, to deal with such
// failures where possible, thus, incrementally hardening the VM
// in such low resource situations.
void CMSCollector::preserve_mark_work(oop p, markOop m) {
  if (_preserved_oop_stack == NULL) {
    assert(_preserved_mark_stack == NULL,
           "bijection with preserved_oop_stack");
    // Allocate the stacks
    _preserved_oop_stack  = new (ResourceObj::C_HEAP)
      GrowableArray<oop>(PreserveMarkStackSize, true);
    _preserved_mark_stack = new (ResourceObj::C_HEAP)
      GrowableArray<markOop>(PreserveMarkStackSize, true);
    if (_preserved_oop_stack == NULL || _preserved_mark_stack == NULL) {
      vm_exit_out_of_memory(2* PreserveMarkStackSize * sizeof(oop) /* punt */,
                            "Preserved Mark/Oop Stack for CMS (C-heap)");
    }
  }
  _preserved_oop_stack->push(p);
  _preserved_mark_stack->push(m);
  assert(m == p->mark(), "Mark word changed");
  assert(_preserved_oop_stack->length() == _preserved_mark_stack->length(),
         "bijection");
}

// Single threaded
void CMSCollector::preserve_mark_if_necessary(oop p) {
  markOop m = p->mark();
  if (m->must_be_preserved(p)) {
    preserve_mark_work(p, m);
  }
}

void CMSCollector::par_preserve_mark_if_necessary(oop p) {
  markOop m = p->mark();
  if (m->must_be_preserved(p)) {
    MutexLockerEx x(ParGCRareEvent_lock, Mutex::_no_safepoint_check_flag);
    // Even though we read the mark word without holding
    // the lock, we are assured that it will not change
    // because we "own" this oop, so no other thread can
    // be trying to push it on the overflow list; see
    // the assertion in preserve_mark_work() that checks
    // that m == p->mark().
    preserve_mark_work(p, m);
  }
}

// We should be able to do this multi-threaded,
// a chunk of stack being a task (this is
// correct because each oop only ever appears
// once in the overflow list. However, it's
// not very easy to completely overlap this with
// other operations, so will generally not be done
// until all work's been completed. Because we
// expect the preserved oop stack (set) to be small,
// it's probably fine to do this single-threaded.
// We can explore cleverer concurrent/overlapped/parallel
// processing of preserved marks if we feel the
// need for this in the future. Stack overflow should
// be so rare in practice and, when it happens, its
// effect on performance so great that this will
// likely just be in the noise anyway.
void CMSCollector::restore_preserved_marks_if_any() {
  if (_preserved_oop_stack == NULL) {
    assert(_preserved_mark_stack == NULL,
           "bijection with preserved_oop_stack");
    return;
  }

  assert(SafepointSynchronize::is_at_safepoint(),
         "world should be stopped");
  assert(Thread::current()->is_ConcurrentGC_thread() ||
         Thread::current()->is_VM_thread(),
         "should be single-threaded");

  int length = _preserved_oop_stack->length();
  assert(_preserved_mark_stack->length() == length, "bijection");
  for (int i = 0; i < length; i++) {
    oop p = _preserved_oop_stack->at(i);
    assert(p->is_oop(), "Should be an oop");
    assert(_span.contains(p), "oop should be in _span");
    assert(p->mark() == markOopDesc::prototype(),
           "Set when taken from overflow list");
    markOop m = _preserved_mark_stack->at(i);
    p->set_mark(m);
  }
  _preserved_mark_stack->clear();
  _preserved_oop_stack->clear();
  assert(_preserved_mark_stack->is_empty() &&
         _preserved_oop_stack->is_empty(),
         "stacks were cleared above");
}

#ifndef PRODUCT
bool CMSCollector::no_preserved_marks() const {
  return (   (   _preserved_mark_stack == NULL
              && _preserved_oop_stack == NULL)
          || (   _preserved_mark_stack->is_empty()
              && _preserved_oop_stack->is_empty()));
}
#endif

CMSAdaptiveSizePolicy* ASConcurrentMarkSweepGeneration::cms_size_policy() const
{
  GenCollectedHeap* gch = (GenCollectedHeap*) GenCollectedHeap::heap();
  CMSAdaptiveSizePolicy* size_policy =
    (CMSAdaptiveSizePolicy*) gch->gen_policy()->size_policy();
  assert(size_policy->is_gc_cms_adaptive_size_policy(),
    "Wrong type for size policy");
  return size_policy;
}

void ASConcurrentMarkSweepGeneration::resize(size_t cur_promo_size,
                                           size_t desired_promo_size) {
  if (cur_promo_size < desired_promo_size) {
    size_t expand_bytes = desired_promo_size - cur_promo_size;
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr(" ASConcurrentMarkSweepGeneration::resize "
        "Expanding tenured generation by " SIZE_FORMAT " (bytes)",
        expand_bytes);
    }
    expand(expand_bytes,
           MinHeapDeltaBytes,
           CMSExpansionCause::_adaptive_size_policy);
  } else if (desired_promo_size < cur_promo_size) {
    size_t shrink_bytes = cur_promo_size - desired_promo_size;
    if (PrintAdaptiveSizePolicy && Verbose) {
      gclog_or_tty->print_cr(" ASConcurrentMarkSweepGeneration::resize "
        "Shrinking tenured generation by " SIZE_FORMAT " (bytes)",
        shrink_bytes);
    }
    shrink(shrink_bytes);
  }
}

CMSGCAdaptivePolicyCounters* ASConcurrentMarkSweepGeneration::gc_adaptive_policy_counters() {
  GenCollectedHeap* gch = GenCollectedHeap::heap();
  CMSGCAdaptivePolicyCounters* counters =
    (CMSGCAdaptivePolicyCounters*) gch->collector_policy()->counters();
  assert(counters->kind() == GCPolicyCounters::CMSGCAdaptivePolicyCountersKind,
    "Wrong kind of counters");
  return counters;
}


void ASConcurrentMarkSweepGeneration::update_counters() {
  if (UsePerfData) {
    _space_counters->update_all();
    _gen_counters->update_all();
    CMSGCAdaptivePolicyCounters* counters = gc_adaptive_policy_counters();
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    CMSGCStats* gc_stats_l = (CMSGCStats*) gc_stats();
    assert(gc_stats_l->kind() == GCStats::CMSGCStatsKind,
      "Wrong gc statistics type");
    counters->update_counters(gc_stats_l);
  }
}

void ASConcurrentMarkSweepGeneration::update_counters(size_t used) {
  if (UsePerfData) {
    _space_counters->update_used(used);
    _space_counters->update_capacity();
    _gen_counters->update_all();

    CMSGCAdaptivePolicyCounters* counters = gc_adaptive_policy_counters();
    GenCollectedHeap* gch = GenCollectedHeap::heap();
    CMSGCStats* gc_stats_l = (CMSGCStats*) gc_stats();
    assert(gc_stats_l->kind() == GCStats::CMSGCStatsKind,
      "Wrong gc statistics type");
    counters->update_counters(gc_stats_l);
  }
}

// The desired expansion delta is computed so that:
// . desired free percentage or greater is used
void ASConcurrentMarkSweepGeneration::compute_new_size() {
  assert_locked_or_safepoint(Heap_lock);

  GenCollectedHeap* gch = (GenCollectedHeap*) GenCollectedHeap::heap();

  // If incremental collection failed, we just want to expand
  // to the limit.
  if (incremental_collection_failed()) {
    clear_incremental_collection_failed();
    grow_to_reserved();
    return;
  }

  assert(UseAdaptiveSizePolicy, "Should be using adaptive sizing");

  assert(gch->kind() == CollectedHeap::GenCollectedHeap,
    "Wrong type of heap");
  int prev_level = level() - 1;
  assert(prev_level >= 0, "The cms generation is the lowest generation");
  Generation* prev_gen = gch->get_gen(prev_level);
  assert(prev_gen->kind() == Generation::ASParNew,
    "Wrong type of young generation");
  ParNewGeneration* younger_gen = (ParNewGeneration*) prev_gen;
  size_t cur_eden = younger_gen->eden()->capacity();
  CMSAdaptiveSizePolicy* size_policy = cms_size_policy();
  size_t cur_promo = free();
  size_policy->compute_tenured_generation_free_space(cur_promo,
                                                       max_available(),
                                                       cur_eden);
  resize(cur_promo, size_policy->promo_size());

  // Record the new size of the space in the cms generation
  // that is available for promotions.  This is temporary.
  // It should be the desired promo size.
  size_policy->avg_cms_promo()->sample(free());
  size_policy->avg_old_live()->sample(used());

  if (UsePerfData) {
    CMSGCAdaptivePolicyCounters* counters = gc_adaptive_policy_counters();
    counters->update_cms_capacity_counter(capacity());
  }
}

void ASConcurrentMarkSweepGeneration::shrink_by(size_t desired_bytes) {
  assert_locked_or_safepoint(Heap_lock);
  assert_lock_strong(freelistLock());
  HeapWord* old_end = _cmsSpace->end();
  HeapWord* unallocated_start = _cmsSpace->unallocated_block();
  assert(old_end >= unallocated_start, "Miscalculation of unallocated_start");
  FreeChunk* chunk_at_end = find_chunk_at_end();
  if (chunk_at_end == NULL) {
    // No room to shrink
    if (PrintGCDetails && Verbose) {
      gclog_or_tty->print_cr("No room to shrink: old_end  "
        PTR_FORMAT "  unallocated_start  " PTR_FORMAT
        " chunk_at_end  " PTR_FORMAT,
        old_end, unallocated_start, chunk_at_end);
    }
    return;
  } else {

    // Find the chunk at the end of the space and determine
    // how much it can be shrunk.
    size_t shrinkable_size_in_bytes = chunk_at_end->size();
    size_t aligned_shrinkable_size_in_bytes =
      align_size_down(shrinkable_size_in_bytes, os::vm_page_size());
    assert(unallocated_start <= chunk_at_end->end(),
      "Inconsistent chunk at end of space");
    size_t bytes = MIN2(desired_bytes, aligned_shrinkable_size_in_bytes);
    size_t word_size_before = heap_word_size(_virtual_space.committed_size());

    // Shrink the underlying space
    _virtual_space.shrink_by(bytes);
    if (PrintGCDetails && Verbose) {
      gclog_or_tty->print_cr("ConcurrentMarkSweepGeneration::shrink_by:"
        " desired_bytes " SIZE_FORMAT
        " shrinkable_size_in_bytes " SIZE_FORMAT
        " aligned_shrinkable_size_in_bytes " SIZE_FORMAT
        "  bytes  " SIZE_FORMAT,
        desired_bytes, shrinkable_size_in_bytes,
        aligned_shrinkable_size_in_bytes, bytes);
      gclog_or_tty->print_cr("          old_end  " SIZE_FORMAT
        "  unallocated_start  " SIZE_FORMAT,
        old_end, unallocated_start);
    }

    // If the space did shrink (shrinking is not guaranteed),
    // shrink the chunk at the end by the appropriate amount.
    if (((HeapWord*)_virtual_space.high()) < old_end) {
      size_t new_word_size =
        heap_word_size(_virtual_space.committed_size());

      // Have to remove the chunk from the dictionary because it is changing
      // size and might be someplace elsewhere in the dictionary.

      // Get the chunk at end, shrink it, and put it
      // back.
      _cmsSpace->removeChunkFromDictionary(chunk_at_end);
      size_t word_size_change = word_size_before - new_word_size;
      size_t chunk_at_end_old_size = chunk_at_end->size();
      assert(chunk_at_end_old_size >= word_size_change,
        "Shrink is too large");
      chunk_at_end->setSize(chunk_at_end_old_size -
                          word_size_change);
      _cmsSpace->freed((HeapWord*) chunk_at_end->end(),
        word_size_change);

      _cmsSpace->returnChunkToDictionary(chunk_at_end);

      MemRegion mr(_cmsSpace->bottom(), new_word_size);
      _bts->resize(new_word_size);  // resize the block offset shared array
      Universe::heap()->barrier_set()->resize_covered_region(mr);
      _cmsSpace->assert_locked();
      _cmsSpace->set_end((HeapWord*)_virtual_space.high());

      NOT_PRODUCT(_cmsSpace->dictionary()->verify());

      // update the space and generation capacity counters
      if (UsePerfData) {
        _space_counters->update_capacity();
        _gen_counters->update_all();
      }

      if (Verbose && PrintGCDetails) {
        size_t new_mem_size = _virtual_space.committed_size();
        size_t old_mem_size = new_mem_size + bytes;
        gclog_or_tty->print_cr("Shrinking %s from %ldK by %ldK to %ldK",
                      name(), old_mem_size/K, bytes/K, new_mem_size/K);
      }
    }

    assert(_cmsSpace->unallocated_block() <= _cmsSpace->end(),
      "Inconsistency at end of space");
    assert(chunk_at_end->end() == _cmsSpace->end(),
      "Shrinking is inconsistent");
    return;
  }
}

// Transfer some number of overflown objects to usual marking
// stack. Return true if some objects were transferred.
bool MarkRefsIntoAndScanClosure::take_from_overflow_list() {
  size_t num = MIN2((size_t)(_mark_stack->capacity() - _mark_stack->length())/4,
                    (size_t)ParGCDesiredObjsFromOverflowList);

  bool res = _collector->take_from_overflow_list(num, _mark_stack);
  assert(_collector->overflow_list_is_empty() || res,
         "If list is not empty, we should have taken something");
  assert(!res || !_mark_stack->isEmpty(),
         "If we took something, it should now be on our stack");
  return res;
}

size_t MarkDeadObjectsClosure::do_blk(HeapWord* addr) {
  size_t res = _sp->block_size_no_stall(addr, _collector);
  assert(res != 0, "Should always be able to compute a size");
  if (_sp->block_is_obj(addr)) {
    if (_live_bit_map->isMarked(addr)) {
      // It can't have been dead in a previous cycle
      guarantee(!_dead_bit_map->isMarked(addr), "No resurrection!");
    } else {
      _dead_bit_map->mark(addr);      // mark the dead object
    }
  }
  return res;
}

TraceCMSMemoryManagerStats::TraceCMSMemoryManagerStats(CMSCollector::CollectorState phase): TraceMemoryManagerStats() {

  switch (phase) {
    case CMSCollector::InitialMarking:
      initialize(true  /* fullGC */ ,
                 true  /* recordGCBeginTime */,
                 true  /* recordPreGCUsage */,
                 false /* recordPeakUsage */,
                 false /* recordPostGCusage */,
                 true  /* recordAccumulatedGCTime */,
                 false /* recordGCEndTime */,
                 false /* countCollection */  );
      break;

    case CMSCollector::FinalMarking:
      initialize(true  /* fullGC */ ,
                 false /* recordGCBeginTime */,
                 false /* recordPreGCUsage */,
                 false /* recordPeakUsage */,
                 false /* recordPostGCusage */,
                 true  /* recordAccumulatedGCTime */,
                 false /* recordGCEndTime */,
                 false /* countCollection */  );
      break;

    case CMSCollector::Sweeping:
      initialize(true  /* fullGC */ ,
                 false /* recordGCBeginTime */,
                 false /* recordPreGCUsage */,
                 true  /* recordPeakUsage */,
                 true  /* recordPostGCusage */,
                 false /* recordAccumulatedGCTime */,
                 true  /* recordGCEndTime */,
                 true  /* countCollection */  );
      break;

    default:
      ShouldNotReachHere();
  }
}

// when bailing out of cms in concurrent mode failure
TraceCMSMemoryManagerStats::TraceCMSMemoryManagerStats(): TraceMemoryManagerStats() {
  initialize(true /* fullGC */ ,
             true /* recordGCBeginTime */,
             true /* recordPreGCUsage */,
             true /* recordPeakUsage */,
             true /* recordPostGCusage */,
             true /* recordAccumulatedGCTime */,
             true /* recordGCEndTime */,
             true /* countCollection */ );
}

