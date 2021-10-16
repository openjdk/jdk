#include "gc/g1/g1BarrierSet.hpp"
#include "gc/g1/g1BlockOffsetTable.hpp"
#include "gc/g1/g1CardTable.hpp"
#include "gc/g1/g1CollectedHeap.hpp"
#include "gc/g1/g1ConcurrentBOTUpdate.hpp"
#include "gc/g1/heapRegion.inline.hpp"

#include "logging/log.hpp"

G1ConcurrentBOTUpdate::G1ConcurrentBOTUpdate(G1CollectedHeap* g1h) :
  _g1h(g1h),
  _plab_word_size(0),
  _plab_recording_in_progress(false) {}

void G1ConcurrentBOTUpdate::update_bot_for_plab(HeapWord* card_boundary) {
  HeapRegion* r = _g1h->heap_region_containing(card_boundary);

  assert(r->is_old(), "Only do this for heap regions with BOT");
  assert(_g1h->card_table()->is_card_aligned(card_boundary), "Need plab card boundary");

  Ticks start = Ticks::now();
  r->update_bot(card_boundary);
  log_info(gc, bot)("Concurrent BOT Update: cr updated 1 plab, took %8.2lf ms",
                    (Ticks::now() - start).seconds() * MILLIUNITS);
}

bool G1ConcurrentBOTUpdate::update_bot_for_plab_part(HeapWord* card_boundary) {
  // TODO
  return false;
}

void G1ConcurrentBOTUpdate::pre_record_plab_allocation() {
  assert_at_safepoint_on_vm_thread();
  _plab_word_size = _g1h->desired_plab_sz(G1HeapRegionAttr::Old);
  // A threshold to control the cost of managing the plabs. If plab size is too small,
  // it costs a lot to store them, yet the benefit of updating them becomes unnoticeable.
  // The threshold is chosen based on the BOT mechanics: when a plab is smaller than this value,
  // BOT entries only make skipping one card at a time. So partial updates to the BOT are not
  // likely to incur duplicated work. On the other hand, if a plab is larger than this value,
  // BOT makes large skips (e.g., 16 cards at a time), which might induce duplicated work for
  // partial BOT updates. This is when concurrent (non-partial) BOT update becomes very beneficial.
  if (_plab_word_size > (BOTConstants::Base * BOTConstants::N_words)) {
    _plab_recording_in_progress = true;
  }
}

void G1ConcurrentBOTUpdate::record_plab_allocation_work(G1PLABCardQueue* plab_card_queue,
                                                        HeapWord* plab_allocation,
                                                        size_t word_size) {
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
    // PLABs not crossing boundary could not have changed BOT. No need to update them.
    return;
  }

  size_t batch_size = HeapRegion::GrainWords / _plab_word_size; // TODO
  assert(batch_size > 1, "At least 2 plabs per region");
  G1BarrierSet::dirty_card_queue_set().enqueue_plab_card(*plab_card_queue,
                                                         last_card_boundary, batch_size);
}

void G1ConcurrentBOTUpdate::post_record_plab_allocation() {
  assert_at_safepoint_on_vm_thread();
  _plab_recording_in_progress = false;
}
