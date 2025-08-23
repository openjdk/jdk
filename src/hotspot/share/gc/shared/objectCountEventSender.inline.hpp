#ifndef SHARE_GC_SHARED_OBJECTCOUNTEVENTSENDER_INLINE_HPP
#define SHARE_GC_SHARED_OBJECTCOUNTEVENTSENDER_INLINE_HPP

#include "gc/shared/objectCountEventSender.hpp"

#if INCLUDE_SERVICES

inline void ObjectCountEventSender::enable_requestable_event() {
  ObjectCountEventSender::_should_send_requestable_event = true;
}

inline void ObjectCountEventSender::disable_requestable_event() {
  ObjectCountEventSender::_should_send_requestable_event = false;
}

template <typename T>
void ObjectCountEventSender::send_event_if_enabled(Klass* klass, jlong count, julong size, const Ticks& timestamp) {
  T event(UNTIMED);
  if (event.should_commit()) {
    event.set_starttime(timestamp);
    event.set_endtime(timestamp);
    event.set_gcId(GCId::current());
    event.set_objectClass(klass);
    event.set_count(count);
    event.set_totalSize(size);
    event.commit();
  }
}

template <class Event>
void ObjectCountEventSender::send(const KlassInfoEntry* entry, const Ticks& timestamp) {
  Klass* klass = entry->klass();
  jlong count = entry->count();
  julong total_size = entry->words() * BytesPerWord;

  send_event_if_enabled<Event>(klass, count, total_size, timestamp);

  // If sending ObjectCountAfterGCEvent and ObjectCountEvent is enabled,
  // Then we should emit data to both of these events.
  // If sending ObjectCountEvent, do not send send ObjectCountAfterGCEvent.
  if (std::is_same<Event, EventObjectCountAfterGC>::value) {
    send_event_if_enabled<EventObjectCount>(klass, count, total_size, timestamp);
  }
}

#endif // INCLUDE_SERVICES

#endif // SHARE_GC_SHARED_OBJECTCOUNTEVENTSENDER_INLINE_HPP
