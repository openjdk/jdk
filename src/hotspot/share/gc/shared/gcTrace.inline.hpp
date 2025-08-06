#ifndef SHARE_GC_SHARED_GCTRACE_INLINE_HPP
#define SHARE_GC_SHARED_GCTRACE_INLINE_HPP

#include "gc/shared/gcTrace.hpp"
#include "gc/shared/objectCountEventSender.inline.hpp"
#include "jfr/jfrEvents.hpp"
#include "memory/heapInspection.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_SERVICES

template <typename Event>
class ObjectCountEventSenderClosure : public KlassInfoClosure {
  const double _size_threshold_percentage;
  size_t _total_size_in_words;
  const Ticks _timestamp;
  KlassInfoTable* _cit;

 public:
  ObjectCountEventSenderClosure(size_t total_size_in_words, const Ticks& timestamp, KlassInfoTable* cit) :
    _size_threshold_percentage(ObjectCountCutOffPercent / 100),
    _total_size_in_words(total_size_in_words),
    _timestamp(timestamp),
    _cit(cit)
  {}

  virtual void do_cinfo(KlassInfoEntry* entry) {
    if (should_send_event(entry)) {
      ObjectCountEventSender::send<Event>(entry, _timestamp);
      _cit->delete_entry(entry, &_total_size_in_words);
    }
  }

 private:
  bool should_send_event(const KlassInfoEntry* entry) const {
    double percentage_of_heap = ((double) entry->words()) / _total_size_in_words;
    return percentage_of_heap >= _size_threshold_percentage;
  }
};

template <typename T>
void GCTracer::report_object_count(T* heap) {
  KlassInfoTable* cit = heap->get_cit();

  if (cit == nullptr || !ObjectCountEventSender::should_send_event<EventObjectCountAfterGC>()) {
    return;
  }
  ObjectCountEventSenderClosure<EventObjectCountAfterGC> event_sender(cit->size_of_instances_in_words(), Ticks::now(), cit);
  cit->iterate(&event_sender);
}

#endif // INCLUDE_SERVICES

#endif // SHARE_GC_SHARED_GCTRACE_INLINE_HPP
