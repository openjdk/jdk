#ifndef SHARE_GC_SHARED_GCTRACE_INLINE_HPP
#define SHARE_GC_SHARED_GCTRACE_INLINE_HPP

#include "gc/shared/gcTrace.hpp"
#include "memory/heapInspection.hpp"
#include "utilities/macros.hpp"

#if INCLUDE_SERVICES
template <typename T>
void GCTracer::report_object_count() {
  if (!ObjectCountEventSender::should_send_event()) {
    return;
  }
  
  T* heap = T::heap();
  KlassInfoTable* cit = heap->get_cit();

  if (!cit->allocation_failed()) {
    ObjectCountEventSenderClosure event_sender(cit->size_of_instances_in_words(), Ticks::now());
    cit->iterate(&event_sender);
  }
}
#endif // INCLUDE_SERVICES

#endif // SHARE_GC_SHARED_GCTRACE_INLINE_HPP
