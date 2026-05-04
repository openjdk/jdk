/*
 * Copyright (c) 2001, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/g1/g1Analytics.hpp"
#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1CardTableClaimTable.inline.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1CollectionSet.hpp"
#include "gc/g1/g1ConcurrentRefine.hpp"
#include "gc/g1/g1ConcurrentRefineStats.inline.hpp"
#include "gc/g1/g1ConcurrentRefineSweepTask.hpp"
#include "gc/g1/g1ConcurrentRefineThread.hpp"
#include "gc/g1/g1HeapRegion.inline.hpp"
#include "gc/g1/g1HeapRegionRemSet.inline.hpp"
#include "gc/g1/g1Policy.hpp"
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/gcTraceTime.inline.hpp"
#include "gc/shared/workerThread.hpp"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/iterator.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

#include <math.h>

G1ConcurrentRefineThread* G1ConcurrentRefineThreadControl::create_refinement_thread() {
  G1ConcurrentRefineThread* result = nullptr;
  result = G1ConcurrentRefineThread::create(_cr);
  if (result == nullptr || result->osthread() == nullptr) {
    log_warning(gc)("Failed to create refinement control thread, no more %s",
                    result == nullptr ? "memory" : "OS threads");
    if (result != nullptr) {
      delete result;
      result = nullptr;
    }
  }
  return result;
}

G1ConcurrentRefineThreadControl::G1ConcurrentRefineThreadControl(uint max_num_threads) :
  _cr(nullptr),
  _control_thread(nullptr),
  _workers(nullptr),
  _max_num_threads(max_num_threads)
{}

G1ConcurrentRefineThreadControl::~G1ConcurrentRefineThreadControl() {
  delete _control_thread;
  delete _workers;
}

jint G1ConcurrentRefineThreadControl::initialize(G1ConcurrentRefine* cr) {
  assert(cr != nullptr, "G1ConcurrentRefine must not be null");
  _cr = cr;

  if (is_refinement_enabled()) {
    _control_thread = create_refinement_thread();
    if (_control_thread == nullptr) {
      vm_shutdown_during_initialization("Could not allocate refinement control thread");
      return JNI_ENOMEM;
    }
    _workers = new WorkerThreads("G1 Refinement Workers", max_num_threads());
    _workers->initialize_workers();
  }
  return JNI_OK;
}

#ifdef ASSERT
void G1ConcurrentRefineThreadControl::assert_current_thread_is_control_refinement_thread() const {
  assert(Thread::current() == _control_thread, "Not refinement control thread");
}
#endif // ASSERT

void G1ConcurrentRefineThreadControl::activate() {
  _control_thread->activate();
}

void G1ConcurrentRefineThreadControl::run_task(WorkerTask* task, uint num_workers) {
  WithActiveWorkers w(_workers, num_workers);
  _workers->run_task(task);
}

void G1ConcurrentRefineThreadControl::control_thread_do(ThreadClosure* tc) {
  if (is_refinement_enabled()) {
    tc->do_thread(_control_thread);
  }
}

void G1ConcurrentRefineThreadControl::worker_threads_do(ThreadClosure* tc) {
  if (is_refinement_enabled()) {
    _workers->threads_do(tc);
  }
}

void G1ConcurrentRefineThreadControl::stop() {
  if (is_refinement_enabled()) {
    _control_thread->stop();
  }
}

G1ConcurrentRefineSweepState::G1ConcurrentRefineSweepState(uint max_reserved_regions) :
  _state(State::Idle),
  _sweep_table(new G1CardTableClaimTable(G1CollectedHeap::get_chunks_per_region_for_merge())),
  _stats()
{
  _sweep_table->initialize(max_reserved_regions);
}

G1ConcurrentRefineSweepState::~G1ConcurrentRefineSweepState() {
  delete _sweep_table;
}

void G1ConcurrentRefineSweepState::enter_state(State state, Ticks timestamp) {
  assert(state > State::Idle, "precondition");
  assert(state != State::Last, "preconditon");
  assert(_state == State(static_cast<uint>(state) - 1),
         "must come from previous state but is %s", state_name(_state));

  _state_start[static_cast<uint>(state)] = timestamp;
  _state = state;
}

Tickspan G1ConcurrentRefineSweepState::get_duration(State start, State end) const {
  assert(end >= start, "precondition");
  return _state_start[static_cast<uint>(end)] - _state_start[static_cast<uint>(start)];
}

Tickspan G1ConcurrentRefineSweepState::time_since_start(Ticks completion_time) const {
  assert(_state >= State::SwapGlobalCT, "precondition");
  return completion_time - _state_start[static_cast<uint>(State::SwapGlobalCT)];
}

void G1ConcurrentRefineSweepState::reset_stats() {
  stats()->reset();
}

void G1ConcurrentRefineSweepState::add_yield_during_sweep_duration(jlong duration) {
  stats()->inc_yield_during_sweep_duration(duration);
}

bool G1ConcurrentRefineSweepState::swap_global_card_table() {
  enter_state(State::SwapGlobalCT, Ticks::now());
  _stats.reset();

  GCTraceTime(Info, gc, refine) tm("Concurrent Refine Global Card Table Swap");

  {
    // We can't have any new threads being in the process of created while we
    // swap the card table because we read the current card table state during
    // initialization.
    // A safepoint may occur during that time, so leave the STS temporarily.
    SuspendibleThreadSetLeaver sts_leave;

    MutexLocker mu(Threads_lock);
    // A GC that advanced the epoch might have happened, which already switched
    // the global card table. Do nothing.
    if (is_in_progress()) {
      G1BarrierSet::g1_barrier_set()->swap_global_card_table();
    }
  }

  return is_in_progress();
}

bool G1ConcurrentRefineSweepState::swap_java_threads_ct() {
  enter_state(State::SwapJavaThreadsCT, Ticks::now());

  GCTraceTime(Info, gc, refine) tm("Concurrent Refine Java Thread CT swap");

  {
    // Need to leave the STS to avoid potential deadlock in the handshake.
    SuspendibleThreadSetLeaver sts;

    class G1SwapThreadCardTableClosure : public HandshakeClosure {
    public:
      G1SwapThreadCardTableClosure() : HandshakeClosure("G1 Java Thread CT swap") { }

      virtual void do_thread(Thread* thread) {
        G1BarrierSet* bs = G1BarrierSet::g1_barrier_set();
        bs->update_card_table_base(thread);
      }
    } cl;
    Handshake::execute(&cl);
  }

  return is_in_progress();
}

bool G1ConcurrentRefineSweepState::swap_gc_threads_ct() {
  enter_state(State::SynchronizeGCThreads, Ticks::now());

  GCTraceTime(Info, gc, refine) tm("Concurrent Refine GC Thread CT swap");

  {
    class RendezvousGCThreads: public VM_Operation {
    public:
      VMOp_Type type() const { return VMOp_G1RendezvousGCThreads; }

      virtual bool evaluate_at_safepoint() const {
        // We only care about synchronizing the GC threads.
        // Leave the Java threads running.
        return false;
      }

      virtual bool skip_thread_oop_barriers() const {
        fatal("Concurrent VMOps should not call this");
        return true;
      }

      void doit() {
        // Light weight "handshake" of the GC threads for memory synchronization;
        // both changes to the Java heap need to be synchronized as well as the
        // previous global card table reference change, so that no GC thread
        // accesses the wrong card table.
        // For example in the rebuild remset process the marking threads write
        // marks into the card table, and that card table reference must be the
        // correct one.
        SuspendibleThreadSet::synchronize();
        SuspendibleThreadSet::desynchronize();
      };
    } op;

    SuspendibleThreadSetLeaver sts_leave;
    VMThread::execute(&op);
  }

  return is_in_progress();
}

void G1ConcurrentRefineSweepState::snapshot_heap() {
  enter_state(State::SnapshotHeap, Ticks::now());

  GCTraceTime(Info, gc, refine) tm("Concurrent Refine Snapshot Heap");

  snapshot_heap_inner();
}

bool G1ConcurrentRefineSweepState::sweep_refinement_table(jlong& total_yield_duration) {
  enter_state(State::SweepRT, Ticks::now());

  while (true) {
    {
      GCTraceTime(Info, gc, refine) tm("Concurrent Refine Table Step");

      G1ConcurrentRefine* cr = G1CollectedHeap::heap()->concurrent_refine();

      G1ConcurrentRefineSweepTask task(_sweep_table, &_stats, cr->num_threads_wanted());
      cr->run_with_refinement_workers(&task);

      assert(is_in_progress(), "inv");
      if (task.sweep_completed()) {
        return true;
      }
    }

    assert(SuspendibleThreadSet::should_yield(), "must be");
    // Interrupted by safepoint request.
    {
      jlong yield_start = os::elapsed_counter();
      SuspendibleThreadSet::yield();

      if (!is_in_progress()) {
        return false;
      } else {
        jlong yield_during_sweep_duration = os::elapsed_counter() - yield_start;
        log_trace(gc, refine)("Yielded from card table sweeping for %.2fms, no GC inbetween, continue",
                              TimeHelper::counter_to_millis(yield_during_sweep_duration));
        total_yield_duration += yield_during_sweep_duration;
      }
    }
  }
}

static void print_refinement_stats(const Tickspan& total_duration,
                                   const Tickspan& pre_sweep_duration,
                                   const G1ConcurrentRefineStats* stats) {
  assert(total_duration >= Tickspan(), "must be non-negative");
  assert(pre_sweep_duration >= Tickspan(), "must be non-negative");
  assert(pre_sweep_duration <= total_duration, "must be bounded by total duration");

  log_debug(gc, refine)("Refinement took %.2fms (pre-sweep %.2fms card refine %.2fms) "
                        "(scanned %zu clean %zu (%.2f%%) not_clean %zu (%.2f%%) not_parsable %zu "
                        "refers_to_cset %zu (%.2f%%) still_refers_to_cset %zu (%.2f%%) no_cross_region %zu pending %zu)",
                        total_duration.seconds() * 1000.0,
                        pre_sweep_duration.seconds() * 1000.0,
                        TimeHelper::counter_to_millis(stats->refine_duration()),
                        stats->cards_scanned(),
                        stats->cards_clean(),
                        percent_of(stats->cards_clean(), stats->cards_scanned()),
                        stats->cards_not_clean(),
                        percent_of(stats->cards_not_clean(), stats->cards_scanned()),
                        stats->cards_not_parsable(),
                        stats->cards_refer_to_cset(),
                        percent_of(stats->cards_refer_to_cset(), stats->cards_not_clean()),
                        stats->cards_already_refer_to_cset(),
                        percent_of(stats->cards_already_refer_to_cset(), stats->cards_not_clean()),
                        stats->cards_no_cross_region(),
                        stats->cards_pending()
                       );
}

void G1ConcurrentRefineSweepState::handle_ongoing_refinement_at_safepoint() {
  assert_at_safepoint();
  if (!is_in_progress()) {
    return;
  }

  const Ticks completion_time = Ticks::now();

  const Tickspan total_duration = time_since_start(completion_time);
  const Tickspan pre_sweep_duration = get_duration(State::SwapGlobalCT, MIN2(_state, State::SweepRT));

  print_refinement_stats(total_duration, pre_sweep_duration, &_stats);

  const bool is_in_sweep_rt = _state == State::SweepRT;

  if (!is_in_sweep_rt) {
    // Refinement has been interrupted without having a snapshot. There may
    // be a mix of already swapped and not-swapped card tables assigned to threads,
    // so they might have already dirtied the swapped card tables.
    // Conservatively scan all (non-free, non-committed) region's card tables,
    // creating the snapshot right now.
    log_debug(gc, refine)("Create work from scratch");
    snapshot_heap_inner();
  } else {
    log_debug(gc, refine)("Continue existing work");
  }

  _state = State::Idle;
}

void G1ConcurrentRefineSweepState::cancel_refinement() {
  _state = State::Idle;
}

void G1ConcurrentRefineSweepState::complete_refinement(jlong total_yield_during_sweep_duration,
                                                       jlong epoch_yield_duration,
                                                       jlong next_epoch_start) {
  enter_state(State::CompleteRefineWork, Ticks::now());

  GCTraceTime(Info, gc, refine) tm("Concurrent Refine Complete Work");

  add_yield_during_sweep_duration(total_yield_during_sweep_duration);

  const Ticks completion_time = Ticks::now();

  const Tickspan total_duration = time_since_start(completion_time);
  const Tickspan pre_sweep_duration = get_duration(State::SwapGlobalCT, State::SweepRT);

  print_refinement_stats(total_duration, pre_sweep_duration, &_stats);

  G1CollectedHeap* g1h = G1CollectedHeap::heap();
  G1Policy* policy = g1h->policy();
  policy->record_refinement_stats(stats());

  {
    MutexLocker x(G1ReviseYoungLength_lock, Mutex::_no_safepoint_check_flag);
    policy->record_dirtying_stats(TimeHelper::counter_to_millis(g1h->last_refinement_epoch_start()),
                                  TimeHelper::counter_to_millis(next_epoch_start),
                                  _stats.cards_pending(),
                                  TimeHelper::counter_to_millis(epoch_yield_duration),
                                  0 /* pending_cards_from_gc */,
                                  _stats.cards_to_cset());
    g1h->set_last_refinement_epoch_start(next_epoch_start, epoch_yield_duration);
  }
  _stats.reset();

  _state = State::Idle;
}

void G1ConcurrentRefineSweepState::snapshot_heap_inner() {
  // G1CollectedHeap::heap_region_iterate() below will only visit currently committed
  // regions. Initialize all entries in the state table here and later in this method
  // selectively enable regions that we are interested. This way regions committed
  // later will be automatically excluded from iteration.
  // Their refinement table must be completely empty anyway.
  _sweep_table->reset_all_to_claimed();

  class SnapshotRegionsClosure : public G1HeapRegionClosure {
    G1CardTableClaimTable* _sweep_table;

  public:
    SnapshotRegionsClosure(G1CardTableClaimTable* sweep_table) : G1HeapRegionClosure(), _sweep_table(sweep_table) { }

    bool do_heap_region(G1HeapRegion* r) override {
      if (!r->is_free()) {
        // Need to scan all parts of non-free regions, so reset the claim.
        // No need for synchronization: we are only interested in regions
        // that were allocated before the handshake; the handshake makes such
        // regions' metadata visible to all threads, and we do not care about
        // humongous regions that were allocated afterwards.
        _sweep_table->reset_to_unclaimed(r->hrm_index());
      }
      return false;
    }
  } cl(_sweep_table);
  G1CollectedHeap::heap()->heap_region_iterate(&cl);
}

bool G1ConcurrentRefineSweepState::are_java_threads_synched() const {
  return _state > State::SwapJavaThreadsCT || !is_in_progress();
}

uint64_t G1ConcurrentRefine::adjust_threads_period_ms() const {
  // Instead of a fixed value, this could be a command line option.  But then
  // we might also want to allow configuration of adjust_threads_wait_ms().

  // Use a prime number close to 50ms, different to other components that derive
  // their wait time from the try_get_available_bytes_estimate() call to minimize
  // interference.
  return 53;
}

static size_t minimum_pending_cards_target() {
  return ParallelGCThreads * G1PerThreadPendingCardThreshold;
}

G1ConcurrentRefine::G1ConcurrentRefine(G1CollectedHeap* g1h) :
  _policy(g1h->policy()),
  _num_threads_wanted(0),
  _pending_cards_target(PendingCardsTargetUninitialized),
  _last_adjust(),
  _needs_adjust(false),
  _heap_was_locked(false),
  _threads_needed(g1h->policy(), adjust_threads_period_ms()),
  _thread_control(G1ConcRefinementThreads),
  _sweep_state(g1h->max_num_regions())
{ }

jint G1ConcurrentRefine::initialize() {
  return _thread_control.initialize(this);
}

G1ConcurrentRefineSweepState& G1ConcurrentRefine::sweep_state_for_merge() {
  sweep_state().handle_ongoing_refinement_at_safepoint();
  assert(!sweep_state().is_in_progress(), "postcondition");
  return sweep_state();
}

void G1ConcurrentRefine::run_with_refinement_workers(WorkerTask* task) {
  _thread_control.run_task(task, num_threads_wanted());
}

void G1ConcurrentRefine::notify_region_reclaimed(G1HeapRegion* r) {
  assert_at_safepoint();
  if (_sweep_state.is_in_progress()) {
    _sweep_state.sweep_table()->claim_all_cards(r->hrm_index());
  }
}

G1ConcurrentRefine* G1ConcurrentRefine::create(G1CollectedHeap* g1h, jint* ecode) {
  G1ConcurrentRefine* cr = new G1ConcurrentRefine(g1h);
  *ecode = cr->initialize();
  if (*ecode != 0) {
    delete cr;
    cr = nullptr;
  }
  return cr;
}

void G1ConcurrentRefine::stop() {
  _thread_control.stop();
}

G1ConcurrentRefine::~G1ConcurrentRefine() {
}

void G1ConcurrentRefine::threads_do(ThreadClosure *tc) {
  worker_threads_do(tc);
  control_thread_do(tc);
}

void G1ConcurrentRefine::worker_threads_do(ThreadClosure *tc) {
  _thread_control.worker_threads_do(tc);
}

void G1ConcurrentRefine::control_thread_do(ThreadClosure *tc) {
  _thread_control.control_thread_do(tc);
}

void G1ConcurrentRefine::update_pending_cards_target(double pending_cards_time_ms,
                                                     size_t processed_pending_cards,
                                                     double goal_ms) {
  size_t minimum = minimum_pending_cards_target();
  if ((processed_pending_cards < minimum) || (pending_cards_time_ms == 0.0)) {
    log_debug(gc, ergo, refine)("Unchanged pending cards target: %zu (processed %zu minimum %zu time %1.2f)",
                                _pending_cards_target, processed_pending_cards, minimum, pending_cards_time_ms);
    return;
  }

  // Base the pending cards budget on the measured rate.
  double rate = processed_pending_cards / pending_cards_time_ms;
  size_t new_target = static_cast<size_t>(goal_ms * rate);
  // Add some hysteresis with previous values.
  if (is_pending_cards_target_initialized()) {
    new_target = (new_target + _pending_cards_target) / 2;
  }
  // Apply minimum target.
  new_target = MAX2(new_target, minimum_pending_cards_target());
  _pending_cards_target = new_target;
  log_debug(gc, ergo, refine)("New pending cards target: %zu", new_target);
}

void G1ConcurrentRefine::adjust_after_gc(double pending_cards_time_ms,
                                         size_t processed_pending_cards,
                                         double goal_ms) {
  if (!G1UseConcRefinement) {
    return;
  }

  update_pending_cards_target(pending_cards_time_ms,
                              processed_pending_cards,
                              goal_ms);
  if (_thread_control.is_refinement_enabled()) {
    _needs_adjust = true;
    if (is_pending_cards_target_initialized()) {
      _thread_control.activate();
    }
  }
}

uint64_t G1ConcurrentRefine::adjust_threads_wait_ms() const {
  assert_current_thread_is_control_refinement_thread();
  if (is_pending_cards_target_initialized()) {
    // Retry asap when the cause for not getting a prediction was that we temporarily
    // did not get the heap lock. Otherwise we might wait for too long until we get
    // back here.
    if (_heap_was_locked) {
      return 1;
    }
    double available_time_ms = _threads_needed.predicted_time_until_next_gc_ms();

    return _policy->adjust_wait_time_ms(available_time_ms, adjust_threads_period_ms());
  } else {
    // If target not yet initialized then wait forever (until explicitly
    // activated).  This happens during startup, when we don't bother with
    // refinement.
    return 0;
  }
}

bool G1ConcurrentRefine::adjust_num_threads_periodically() {
  assert_current_thread_is_control_refinement_thread();

  _heap_was_locked = false;
  // Check whether it's time to do a periodic adjustment if there is no explicit
  // request pending. We might have spuriously woken up.
  if (!_needs_adjust) {
    Tickspan since_adjust = Ticks::now() - _last_adjust;
    if (since_adjust.milliseconds() < adjust_threads_period_ms()) {
      _num_threads_wanted = 0;
      return false;
    }
  }

  // Reset pending request.
  _needs_adjust = false;
  size_t available_bytes = 0;
  if (_policy->try_get_available_bytes_estimate(available_bytes)) {
    adjust_threads_wanted(available_bytes);
    _last_adjust = Ticks::now();
  } else {
    _heap_was_locked = true;
    // Defer adjustment to next time.
    _needs_adjust = true;
  }

  return (_num_threads_wanted > 0) && !heap_was_locked();
}

void G1ConcurrentRefine::adjust_threads_wanted(size_t available_bytes) {
  assert_current_thread_is_control_refinement_thread();

  G1Policy* policy = G1CollectedHeap::heap()->policy();
  const G1Analytics* analytics = policy->analytics();

  size_t num_cards = policy->current_pending_cards();

  _threads_needed.update(_num_threads_wanted,
                         available_bytes,
                         num_cards,
                         _pending_cards_target);
  uint new_wanted = _threads_needed.threads_needed();
  if (new_wanted > _thread_control.max_num_threads()) {
    // Bound the wanted threads by maximum available.
    new_wanted = _thread_control.max_num_threads();
  }

  _num_threads_wanted = new_wanted;

  log_debug(gc, refine)("Concurrent refinement: wanted %u, pending cards: %zu (pending-from-gc %zu), "
                        "predicted: %zu, goal %zu, time-until-next-gc: %1.2fms pred-refine-rate %1.2fc/ms log-rate %1.2fc/ms",
                        new_wanted,
                        num_cards,
                        G1CollectedHeap::heap()->policy()->pending_cards_from_gc(),
                        _threads_needed.predicted_cards_at_next_gc(),
                        _pending_cards_target,
                        _threads_needed.predicted_time_until_next_gc_ms(),
                        analytics->predict_concurrent_refine_rate_ms(),
                        analytics->predict_dirtied_cards_rate_ms()
                        );
}

bool G1ConcurrentRefine::is_thread_adjustment_needed() const {
  assert_current_thread_is_control_refinement_thread();
  return _needs_adjust;
}

void G1ConcurrentRefine::record_thread_adjustment_needed() {
  assert_current_thread_is_control_refinement_thread();
  _needs_adjust = true;
}
