#include "gc/g1/g1BlockOffsetTable.inline.hpp"
#include "gc/g1/g1BOTUpdateCardSet.inline.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1ConcurrentBOTUpdate.hpp"
#include "gc/g1/heapRegion.inline.hpp"

#include "logging/log.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.hpp"

using CardIndex = G1BOTUpdateCardSet::CardIndex;

class G1ConcurrentBOTUpdateThread: public ConcurrentGCThread {
  friend class G1ConcurrentBOTUpdate;

  double _vtime_accum;
  G1ConcurrentBOTUpdate* _fixer;

  G1ConcurrentBOTUpdateThread(G1ConcurrentBOTUpdate* fixer, uint i);

  void wait_for_work(bool more_work);

  void run_service();
  void stop_service();

  double vtime_accum() const { return _vtime_accum; }
};

G1ConcurrentBOTUpdate::G1ConcurrentBOTUpdate(G1CollectedHeap* g1h) :
  _g1h(g1h),
  _in_progress(false),
  _should_abort(false),
  _n_workers(ConcGCThreads),
  _inactive_count(0),
  _stopped_count(0),
  _fixer_threads(NULL),
  _plab_word_size(0),
  _plab_recording_in_progress(false),
  _card_sets(NULL),
  _current(NULL),
  _stats() {
  _fixer_threads = NEW_C_HEAP_ARRAY(G1ConcurrentBOTUpdateThread*, _n_workers, mtGC);
  for (uint i = 0; i < _n_workers; i++) {
    G1ConcurrentBOTUpdateThread* t = NULL;
    if (!InjectGCWorkerCreationFailure) {
      t = new G1ConcurrentBOTUpdateThread(this, i);
    }
    if (t == NULL || t->osthread() == NULL) {
      log_warning(gc, bot)("Unable to create G1ConcurrentBOTUpdateThread #%d", i);
      if (i == 0) {
        G1UseConcurrentBOTUpdate = false;
      }
      _n_workers = i; // Actual number of threads created
      break;
    }
    _fixer_threads[i] = t;
  }
}

void G1ConcurrentBOTUpdate::fix_bot_for_card_set(G1BOTUpdateCardSet* card_set) {
  assert(!card_set->is_empty(), "We should be the only one emptying it");
  card_set->print_stats();
  class BOTUpdateCardSetClosure: public G1BOTUpdateCardSet::CardIterator {
    G1ConcurrentBOTUpdate* _fixer;
    HeapRegion* _hr;

  public:
    uint _num_plabs;

    BOTUpdateCardSetClosure(G1ConcurrentBOTUpdate* fixer, HeapRegion* hr) :
      _fixer(fixer), _hr(hr), _num_plabs(0) {}
    bool do_card(CardIndex card_index) {
      HeapWord* card_boundary = _hr->bot_fixing_card_set()->card_boundary_for(card_index);
      // We have the last card boundary cover by a plab.
      // We will fix the block (normally the block will be the plab) that covers this card boundary.
      _hr->update_bot(card_boundary);
      _num_plabs++;
      return (_fixer->should_abort() == false); // Stop iteration if it aborts
    }
  } cl(this, card_set->hr());

  Ticks start = Ticks::now();
  card_set->iterate_cards(cl);
  card_set->mark_as_done();
  log_info(gc, bot)("Concurrent BOT Update: fixed %d plabs, took %8.2lf ms",
                    cl._num_plabs, (Ticks::now() - start).seconds() * MILLIUNITS);
}

bool G1ConcurrentBOTUpdate::fix_bot_step() {
  G1BOTUpdateCardSet* old_val = _current;
  G1BOTUpdateCardSet* expect = NULL;
  G1BOTUpdateCardSet* new_val = NULL;
  do {
    if (old_val == NULL) return false;

    expect = old_val;
    new_val = expect->next();
    old_val = Atomic::cmpxchg(&_current, expect, new_val, memory_order_relaxed);
  } while (old_val != expect);

  fix_bot_for_card_set(old_val);

  return (new_val != NULL) && !_should_abort;
}

void G1ConcurrentBOTUpdate::fix_bot_before_refine(HeapRegion* r, HeapWord* card_boundary) {
  assert(r->is_old(), "Only do this for heap regions with BOT");
  assert(_g1h->card_table()->is_card_aligned(card_boundary), "Only do this for cards to refine");

  Ticks start = Ticks::now();
  // If the region doesn't have plabs or if the card is below where plabs are allocated.
  G1BOTUpdateCardSet* card_set = r->bot_fixing_card_set();
  if (card_set->is_empty() || card_set->is_below_start(card_boundary)) return;

  // If the card points into an object instead of a plab.
  HeapWord* latest_plab_start = r->need_fixing(card_boundary);
  if (latest_plab_start == NULL) return;

  // If the plab has been claimed.
  CardIndex c = card_set->find_plab_covering(card_boundary, latest_plab_start);
  if (c == 0) return;
  // In some rare cases, the plab have been claimed and we get the plab after that plab.
  // Since it's rare, we do not check this case and let this thread fix the wrong plab.
  // This will (nicely) leave more time for the fix result of first plab to be visible to us.
  if (!card_set->claim_card(c)) return;

  r->update_bot(card_set->card_boundary_for(c));
  log_info(gc, bot)("Concurrent BOT Update: fixed 1 plab before refine, took %8.2lf ms",
                    (Ticks::now() - start).seconds() * MILLIUNITS);
}

void G1ConcurrentBOTUpdate::pre_record_plab_allocation() {
  assert_at_safepoint_on_vm_thread();
  assert(_card_sets == NULL, "Sanity");
  _plab_word_size = _g1h->desired_plab_sz(G1HeapRegionAttr::Old);
  G1BOTUpdateCardSet::prepare(_plab_word_size);
  _plab_recording_in_progress = true;
}

void G1ConcurrentBOTUpdate::enlist_card_set(G1BOTUpdateCardSet* card_set) {
  assert(!card_set->is_empty(), "Invalid card set");
  G1BOTUpdateCardSet* old_val = _card_sets;
  G1BOTUpdateCardSet* expect = NULL;
  do {
    expect = old_val;
    old_val = Atomic::cmpxchg(&_card_sets, expect, card_set, memory_order_relaxed);
  } while (old_val != expect);
  card_set->set_next(old_val);
}

void G1ConcurrentBOTUpdate::record_plab_allocation(HeapWord* plab_allocation, size_t word_size) {
  if (!_plab_recording_in_progress) return;

  HeapRegion* r = _g1h->heap_region_containing(plab_allocation);
  assert(r->is_old(), "Only old regions need this");
  assert(word_size > 0, "Sanity");
  // Only when a region is full can a plab be smaller than its desired size.
  assert(word_size == _plab_word_size ||
         (word_size < _plab_word_size && plab_allocation + word_size == r->end()),
         "Invalid plab size");

  HeapWord* first_card_boundary = align_down(plab_allocation, BOTConstants::N_bytes);
  HeapWord* last_card_boundary = align_down(plab_allocation + word_size - 1, BOTConstants::N_bytes);
  if (first_card_boundary == last_card_boundary) {
    // PLABs not crossing boundary could not have changed BOT. No need to fix them.
    return;
  }

  G1BOTUpdateCardSet* card_set = r->bot_fixing_card_set();
  bool should_enlist = card_set->add_card(last_card_boundary);

  if (should_enlist) {
    enlist_card_set(card_set);
  }
}

void G1ConcurrentBOTUpdate::post_record_plab_allocation() {
  assert_at_safepoint_on_vm_thread();
  _plab_recording_in_progress = false;
  _current = _card_sets;
}

void G1ConcurrentBOTUpdate::clear_card_sets() {
  assert_at_safepoint_on_vm_thread();
  uint count[2] = { 0, 0 };
  while (_card_sets != NULL) {
    G1BOTUpdateCardSet* card_set = _card_sets;
    _card_sets = _card_sets->next();
    count[card_set->is_empty()]++;
    card_set->clear();
  }
  _current = NULL;
  log_info(gc, bot)("Concurrent BOT Update: processed/aborted = %d/%d", count[1], count[0]);
}

void G1ConcurrentBOTUpdate::threads_do(ThreadClosure* tc) {
  for (uint i = 0; i < _n_workers; i++) {
    tc->do_thread(_fixer_threads[i]);
  }
}

void G1ConcurrentBOTUpdate::print_summary_info() {
  Log(gc, bot) log;
  if (log.is_trace()) {
    log.trace(" Concurrent BOT fixing:");
    for (uint i = 0; i < _n_workers; i++) {
      log.trace("  Worker #%d concurrent time = %8.2f s.", i, _fixer_threads[i]->vtime_accum());
    }
  }
}

// Synchronization between the BOT fixing threads and the activating/aborting VM thread.

// Called by VM thread.
void G1ConcurrentBOTUpdate::activate() {
  MutexLocker ml(ConcurrentBOTUpdate_lock, Mutex::_no_safepoint_check_flag);
  assert(_in_progress == false, "Activated twice");
  assert(_should_abort == false, "Sanity");
  if (_stopped_count == _n_workers) return; // No workers
  _in_progress = true;
  ConcurrentBOTUpdate_lock->notify_all();
  _stats.concurrent_phase_start_time = Ticks::now();
}

// Called by VM thread.
void G1ConcurrentBOTUpdate::abort_and_wait() {
  MonitorLocker ml(ConcurrentBOTUpdate_lock, Mutex::_no_safepoint_check_flag);
  if (_in_progress) {
    _should_abort = true;
  } else {
    assert(_should_abort == false, "Must have cleared this");
  }
  while (_in_progress) {
    assert(_should_abort, "Who changed this?");
    ml.wait();
  }
}

// Called by one of the workers.
void G1ConcurrentBOTUpdate::deactivate() {
  assert(ConcurrentBOTUpdate_lock->owned_by_self(), "Must be locked by self");
  if (_in_progress && _inactive_count + _stopped_count == _n_workers) {
    _in_progress = false;
    _should_abort = false;
    ConcurrentBOTUpdate_lock->notify_all(); // Notify that all workers are now inactive/stopped
    log_trace(gc, bot)("Concurrent BOT fixing: took %8.2lf ms",
                       (Ticks::now() - _stats.concurrent_phase_start_time).seconds() * MILLIUNITS);
  }
}

void G1ConcurrentBOTUpdate::note_active() {
  assert(ConcurrentBOTUpdate_lock->owned_by_self(), "Must be locked by self");
  _inactive_count--;
}

void G1ConcurrentBOTUpdate::note_inactive() {
  assert(ConcurrentBOTUpdate_lock->owned_by_self(), "Must be locked by self");
  _inactive_count++;
  deactivate();
}

void G1ConcurrentBOTUpdate::note_stopped() {
  assert(ConcurrentBOTUpdate_lock->owned_by_self(), "Must be locked by self");
  _stopped_count++;
  deactivate();
}

void G1ConcurrentBOTUpdate::stop() {
  for (uint i = 0; i < _n_workers; i++) {
    _fixer_threads[i]->stop();
  }
}

G1ConcurrentBOTUpdateThread::G1ConcurrentBOTUpdateThread(G1ConcurrentBOTUpdate* fixer, uint i) :
  ConcurrentGCThread(), _vtime_accum(0.0), _fixer(fixer) {
  set_name("G1 BOT Update #%d", i);
  create_and_start();
}

void G1ConcurrentBOTUpdateThread::wait_for_work(bool more_work) {
  MonitorLocker ml(ConcurrentBOTUpdate_lock, Mutex::_no_safepoint_check_flag);
  _fixer->note_inactive();
  while ((!more_work || _fixer->should_abort()) && !should_terminate()) {
    ml.wait();
    more_work = _fixer->in_progress();
  }
  _fixer->note_active();
}

void G1ConcurrentBOTUpdateThread::run_service() {
  double vtime_start = os::elapsedVTime();

  bool more_work = false;
  while (!should_terminate()) {
    wait_for_work(more_work);
    if (should_terminate()) {
      break;
    }

    more_work = _fixer->fix_bot_step();

    if (os::supports_vtime()) {
      _vtime_accum = (os::elapsedVTime() - vtime_start);
    } else {
      _vtime_accum = 0.0;
    }
  }

  MutexLocker ml(ConcurrentBOTUpdate_lock, Mutex::_no_safepoint_check_flag);
  _fixer->note_stopped();
}

void G1ConcurrentBOTUpdateThread::stop_service() {
  MutexLocker ml(ConcurrentBOTUpdate_lock, Mutex::_no_safepoint_check_flag);
  ConcurrentBOTUpdate_lock->notify_all();
}

// End of synchronization between the BOT fixing threads and the activating/aborting VM thread.
