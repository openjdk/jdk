#ifndef SHARE_GC_G1_G1CONCURRENTBOTFIXING_HPP
#define SHARE_GC_G1_G1CONCURRENTBOTFIXING_HPP

#include "gc/g1/g1BOTFixingCardSet.hpp"

#include "memory/iterator.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

struct G1BOTFixingStats {
  Ticks concurrent_phase_start_time;
};

class G1CollectedHeap;
class G1ConcurrentBOTFixingThread;

class G1ConcurrentBOTFixing: public CHeapObj<mtGC> {
  G1CollectedHeap* _g1h;

  volatile bool _in_progress;
  volatile bool _should_abort;
  // Workers.
  uint _n_workers;
  // A counter to know when all workers have finished.
  uint _inactive_count;
  G1ConcurrentBOTFixingThread** _fixer_threads;

  // The plab size recorded before evacuation.
  size_t _plab_word_size;

  // A flag to know turn recording on/off. Mainly to disable recording for full gcs.
  bool _plab_recording_in_progress;

  // A list of card sets, each recording the cards (of plabs) that need to be fixed.
  G1BOTFixingCardSet* _card_sets;
  // A pointer into the list for job dispatching.
  G1BOTFixingCardSet* _current;

  G1BOTFixingStats _stats;

  void enlist_card_set(G1BOTFixingCardSet* card_set);

  void fix_bot_for_card_set(G1BOTFixingCardSet* card_set);

public:
  G1ConcurrentBOTFixing(G1CollectedHeap* g1h);

  bool in_progress() const { return _in_progress; }
  bool should_abort() const { return _should_abort; }

  // Signal the workers to concurrently process the card sets.
  void activate();
  // Abort the jobs and wait for workers to stop.
  void abort_and_wait();
  // Workers use these to maintain _inactive_count and notify possible waiters
  // waiting for them to finish.
  void note_active();
  void note_inactive();
  // Terminate the threads.
  void stop();

  // Clear the card sets from previous gcs.
  void clear_card_sets();

  // Prepare BOT fixing with necessary information, e.g., plab size. Called before recording plabs.
  void pre_record_plab_allocation();

  // Record each plab allocation.
  void record_plab_allocation(HeapWord* plab_allocation, size_t word_size);

  // Setup for the concurrent phase after plab recording.
  void post_record_plab_allocation();

  // Entry point for the fixer threads. Claim and process one of the card sets from the list.
  // Return whether there are possibly more. Return false if someone asked us to abort.
  bool fix_bot_step();

  // Entry point for concurrent refinement threads or mutators that tries to do conc refinement.
  // These threads always have a specific card in mind, that is, the dirty card it wants to refine.
  void fix_bot_before_refine(HeapRegion* r, HeapWord* card_boundary);

  void threads_do(ThreadClosure* tc);
  void print_summary_info();
};

#endif // SHARE_GC_G1_G1CONCURRENTBOTFIXING_HPP
