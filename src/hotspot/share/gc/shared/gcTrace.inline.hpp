#ifndef SHARE_GC_SHARED_GCTRACE_INLINE_HPP
#define SHARE_GC_SHARED_GCTRACE_INLINE_HPP

#include "gc/shared/gcTrace.hpp"
#include "gc/shared/objectCountEventSender.inline.hpp"
#include "memory/heapInspection.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_SERVICES

// The ObjectCountEventSenderClosure will decide whether to delete
// the entry and/or emit the ObjectCount and ObjectCountAfterGC
// events separately. Only set the delete entry flag to true if the same
// KlassInfoTable is being reused for this closure. The SeparateEventEmission
// determines if only the ObjectCount event will be emitted instead of
// ObjectCountAfterGC. If false, then both events will be emitted.

template <bool DeleteEntry, bool SeparateEventEmission>
class ObjectCountEventSenderClosure : public KlassInfoClosure {
  const double _size_threshold_percentage;
  size_t _total_size_in_words;
  const Ticks _timestamp;
  KlassInfoTable* _cit;

 public:
  ObjectCountEventSenderClosure(size_t total_size_in_words, const Ticks& timestamp, KlassInfoTable* cit=nullptr) :
    _size_threshold_percentage(ObjectCountCutOffPercent / 100),
    _total_size_in_words(total_size_in_words),
    _timestamp(timestamp),
    _cit(cit)
  {}
  
  virtual void do_cinfo(KlassInfoEntry* entry) {
    if (should_send_event(entry)) {
      if (SeparateEventEmission) {
        ObjectCountEventSender::send<true>(entry, _timestamp);
      } else {
        ObjectCountEventSender::send<false>(entry, _timestamp);
      }
    }

    // If the same KlassInfoTable is being used for every event emission,
    // delete the entry even if we don't send it. This ensure live objects that
    // weren't sent in a previous event emission are not monotonically increasing.
    if (DeleteEntry) {
      assert(_cit != nullptr, "KlassInfoTable should be initialized");
      _cit->delete_entry(entry);
    }
  }

 private:
  bool should_send_event(const KlassInfoEntry* entry) const {
    double percentage_of_heap = ((double) entry->words()) / _total_size_in_words;
    return percentage_of_heap >= _size_threshold_percentage;
  }
};

template <typename T>
void GCTracer::report_object_count() {
  if (!ObjectCountEventSender::should_send_event()) {
    return;
  }
  
  T* heap = T::heap();
  KlassInfoTable* cit = heap->get_cit();

  if (!cit->allocation_failed()) {
    ObjectCountEventSenderClosure<true, true> event_sender(cit->size_of_instances_in_words(), Ticks::now(), cit);
    cit->iterate(&event_sender);
    assert(cit->size_of_instances_in_words() == 0, "KlassInfoTable should be empty");
  }
}

#endif // INCLUDE_SERVICES

#endif // SHARE_GC_SHARED_GCTRACE_INLINE_HPP
