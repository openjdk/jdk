/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */


#include "gc/shared/gcId.hpp"
#include "gc/shared/objectCountEventSender.hpp"
#include "jfr/jfrEvents.hpp"
#include "runtime/mutex.hpp"
#include "memory/heapInspection.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"
#if INCLUDE_SERVICES

template <class Event>
bool ObjectCountEventSender::should_send_event() {
#if INCLUDE_JFR
  return _should_send_requestable_event || Event::is_enabled();
#else
  return false;
#endif // INCLUDE_JFR
}

bool ObjectCountEventSender::_should_send_requestable_event = false;

void ObjectCountEventSender::enable_requestable_event() {
  _should_send_requestable_event = true;
}

void ObjectCountEventSender::disable_requestable_event() {
  _should_send_requestable_event = false;
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
  // If sending ObjectCountAfterGCEvent, check if ObjectCount is enabled and send event data to ObjectCount
  // If sending ObjectCountEvent, do not send send ObjectCountAfterGCEvent
  if (std::is_same<Event, EventObjectCountAfterGC>::value && ObjectCountEventSender::should_send_event<EventObjectCount>()) {
    send_event_if_enabled<EventObjectCount>(klass, count, total_size, timestamp);
  }
}

template bool ObjectCountEventSender::should_send_event<EventObjectCount>();
template bool ObjectCountEventSender::should_send_event<EventObjectCountAfterGC>();

template void ObjectCountEventSender::send<EventObjectCount>(const KlassInfoEntry*, const Ticks&);
template void ObjectCountEventSender::send<EventObjectCountAfterGC>(const KlassInfoEntry*, const Ticks&);

#endif // INCLUDE_SERVICES
