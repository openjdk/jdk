// #ifndef SHARE_GC_SHARED_OBJECTCOUNTEVENTSENDER_INLINE_HPP
// #define SHARE_GC_SHARED_OBJECTCOUNTEVENTSENDER_INLINE_HPP

// #include "gc/shared/objectCountEventSender.hpp"

// #if INCLUDE_SERVICES

// inline void ObjectCountEventSender::enable_requestable_event() {
//   ObjectCountEventSender::_should_send_requestable_event = true;
// }

// inline void ObjectCountEventSender::disable_requestable_event() {
//   ObjectCountEventSender::_should_send_requestable_event = false;
// }

// template <typename T>
// void ObjectCountEventSender::send_event_if_enabled(Klass* klass, jlong count, julong size, const Ticks& timestamp) {
//   T event(UNTIMED);
//   if (event.should_commit()) {
//     event.set_starttime(timestamp);
//     event.set_endtime(timestamp);
//     event.set_gcId(GCId::current());
//     event.set_objectClass(klass);
//     event.set_count(count);
//     event.set_totalSize(size);
//     event.commit();
//   }
// }

// #endif // INCLUDE_SERVICES

// #endif // SHARE_GC_SHARED_OBJECTCOUNTEVENTSENDER_INLINE_HPP
