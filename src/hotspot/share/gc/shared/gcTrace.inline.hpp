#ifndef SHARE_GC_SHARED_GCTRACE_INLINE_HPP
#define SHARE_GC_SHARED_GCTRACE_INLINE_HPP

#include "gc/shared/gcTrace.hpp"
#include "gc/shared/objectCountEventSender.inline.hpp"
#include "memory/heapInspection.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_SERVICES

// The ObjectCountEventSenderClosure will determine if only the ObjectCount
// event will be emitted instead of ObjectCountAfterGC. If false, then both
// events will be emitted.

template <bool SeparateEventEmission>
class ObjectCountEventSenderClosure : public KlassInfoClosure {
  const double _size_threshold_percentage;
  size_t _total_size_in_words;
  const Ticks _timestamp;

 public:
  ObjectCountEventSenderClosure(size_t total_size_in_words, const Ticks& timestamp) :
    _size_threshold_percentage(ObjectCountCutOffPercent / 100),
    _total_size_in_words(total_size_in_words),
    _timestamp(timestamp)
  {}
  
  virtual void do_cinfo(KlassInfoEntry* entry) {
    if (should_send_event(entry)) {
      if (SeparateEventEmission) {
        ObjectCountEventSender::send<true>(entry, _timestamp);
      } else {
        ObjectCountEventSender::send<false>(entry, _timestamp);
      }
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
    // Allow for separate event emission to distinguish if ObjectCount event
    // triggered this method.
    ObjectCountEventSenderClosure<true> event_sender(cit->size_of_instances_in_words(), Ticks::now());
    cit->iterate(&event_sender);
  }
}

#endif // INCLUDE_SERVICES

#endif // SHARE_GC_SHARED_GCTRACE_INLINE_HPP
