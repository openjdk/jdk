#ifndef SHARE_GC_G1_G1CONCURRENTBOTUPDATE_HPP
#define SHARE_GC_G1_G1CONCURRENTBOTUPDATE_HPP

#include "utilities/globalDefinitions.hpp"

class G1CollectedHeap;
class G1PLABCardQueue;

class G1ConcurrentBOTUpdate: public CHeapObj<mtGC> {
  G1CollectedHeap* _g1h;

  // The plab size recorded before evacuation.
  size_t _plab_word_size;

  // A flag to turn recording on/off. Mainly to disable recording for full gcs.
  bool _plab_recording_in_progress;

  void record_plab_allocation_work(G1PLABCardQueue* q, HeapWord* plab_allocation, size_t word_size);

public:
  G1ConcurrentBOTUpdate(G1CollectedHeap* g1h);

  // Prepare BOT update with necessary information, e.g., plab size. Called before recording plabs.
  void pre_record_plab_allocation();

  // Record each plab allocation.
  void record_plab_allocation(G1PLABCardQueue* q, HeapWord* plab_allocation, size_t word_size) {
    if (_plab_recording_in_progress) {
      record_plab_allocation_work(q, plab_allocation, word_size);
    }
  }

  // Called after recording plabs.
  void post_record_plab_allocation();

  void update_bot_for_plab(HeapWord* card_boundary);
  // This version will update BOT for part of the plab, allowing for more prompt pause (for gc).
  // Return true if the plab has more parts to update; otherwise return false.
  bool update_bot_for_plab_part(HeapWord* card_boundary);
};

#endif // SHARE_GC_G1_G1CONCURRENTBOTUPDATE_HPP
