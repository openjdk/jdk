/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "memory/allocation.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/osThread.hpp"
#include "runtime/threadLocalStorage.hpp"
#include "runtime/timer.hpp"
#include "utilities/events.hpp"
#ifdef TARGET_OS_FAMILY_linux
# include "thread_linux.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_solaris
# include "thread_solaris.inline.hpp"
#endif
#ifdef TARGET_OS_FAMILY_windows
# include "thread_windows.inline.hpp"
#endif


#ifndef PRODUCT

////////////////////////////////////////////////////////////////////////////
// Event

typedef u4 EventID;

class Event VALUE_OBJ_CLASS_SPEC  {
 private:
  jlong       _time_tick;
  intx        _thread_id;
  const char* _format;
  int         _indent;
  intptr_t    _arg_1;
  intptr_t    _arg_2;
  intptr_t    _arg_3;

  // only EventBuffer::add_event() can assign event id
  friend class EventBuffer;
  EventID     _id;

 public:

  void clear() { _format = NULL; }

  EventID id() const { return _id; }

  void fill(int indent, const char* format, intptr_t arg_1, intptr_t arg_2, intptr_t arg_3) {
    _format = format;
    _arg_1  = arg_1;
    _arg_2  = arg_2;
    _arg_3  = arg_3;

    _indent = indent;

    _thread_id = os::current_thread_id();
    _time_tick = os::elapsed_counter();
  }

  void print_on(outputStream *st) {
    if (_format == NULL) return;
    st->print("  %d", _thread_id);
    st->print("  %3.2g   ", (double)_time_tick / os::elapsed_frequency());
    st->fill_to(20);
    for (int index = 0; index < _indent; index++) {
      st->print("| ");
    }
    st->print_cr(_format, _arg_1, _arg_2, _arg_3);
  }
};

////////////////////////////////////////////////////////////////////////////
// EventBuffer
//
// Simple lock-free event queue. Every event has a unique 32-bit id.
// It's fine if two threads add events at the same time, because they
// will get different event id, and then write to different buffer location.
// However, it is assumed that add_event() is quick enough (or buffer size
// is big enough), so when one thread is adding event, there can't be more
// than "size" events created by other threads; otherwise we'll end up having
// two threads writing to the same location.

class EventBuffer : AllStatic {
 private:
  static Event* buffer;
  static int    size;
  static jint   indent;
  static volatile EventID _current_event_id;

  static EventID get_next_event_id() {
    return (EventID)Atomic::add(1, (jint*)&_current_event_id);
  }

 public:
  static void inc_indent() { Atomic::inc(&indent); }
  static void dec_indent() { Atomic::dec(&indent); }

  static bool get_event(EventID id, Event* event) {
    int index = (int)(id % size);
    if (buffer[index].id() == id) {
      memcpy(event, &buffer[index], sizeof(Event));
      // check id again; if buffer[index] is being updated by another thread,
      // event->id() will contain different value.
      return (event->id() == id);
    } else {
      // id does not match - id is invalid, or event is overwritten
      return false;
    }
  }

  // add a new event to the queue; if EventBuffer is full, this call will
  // overwrite the oldest event in the queue
  static EventID add_event(const char* format,
                           intptr_t arg_1, intptr_t arg_2, intptr_t arg_3) {
    // assign a unique id
    EventID id = get_next_event_id();

    // event will be copied to buffer[index]
    int index = (int)(id % size);

    // first, invalidate id, buffer[index] can't have event with id = index + 2
    buffer[index]._id = index + 2;

    // make sure everyone has seen that buffer[index] is invalid
    OrderAccess::fence();

    // ... before updating its value
    buffer[index].fill(indent, format, arg_1, arg_2, arg_3);

    // finally, set up real event id, now buffer[index] contains valid event
    OrderAccess::release_store(&(buffer[index]._id), id);

    return id;
  }

  static void print_last(outputStream *st, int number) {
    st->print_cr("[Last %d events in the event buffer]", number);
    st->print_cr("-<thd>-<elapsed sec>-<description>---------------------");

    int count = 0;
    EventID id = _current_event_id;
    while (count < number) {
      Event event;
      if (get_event(id, &event)) {
         event.print_on(st);
      }
      id--;
      count++;
    }
  }

  static void print_all(outputStream* st) {
    print_last(st, size);
  }

  static void init() {
    // Allocate the event buffer
    size   = EventLogLength;
    buffer = NEW_C_HEAP_ARRAY(Event, size);

    _current_event_id = 0;

    // Clear the event buffer
    for (int index = 0; index < size; index++) {
      buffer[index]._id = index + 1;       // index + 1 is invalid id
      buffer[index].clear();
    }
  }
};

Event*           EventBuffer::buffer;
int              EventBuffer::size;
volatile EventID EventBuffer::_current_event_id;
int              EventBuffer::indent;

////////////////////////////////////////////////////////////////////////////
// Events

// Events::log() is safe for signal handlers
void Events::log(const char* format, ...) {
  if (LogEvents) {
    va_list ap;
    va_start(ap, format);
    intptr_t arg_1 = va_arg(ap, intptr_t);
    intptr_t arg_2 = va_arg(ap, intptr_t);
    intptr_t arg_3 = va_arg(ap, intptr_t);
    va_end(ap);

    EventBuffer::add_event(format, arg_1, arg_2, arg_3);
  }
}

void Events::print_all(outputStream *st) {
  EventBuffer::print_all(st);
}

void Events::print_last(outputStream *st, int number) {
  EventBuffer::print_last(st, number);
}

///////////////////////////////////////////////////////////////////////////
// EventMark

EventMark::EventMark(const char* format, ...) {
  if (LogEvents) {
    va_list ap;
    va_start(ap, format);
    intptr_t arg_1 = va_arg(ap, intptr_t);
    intptr_t arg_2 = va_arg(ap, intptr_t);
    intptr_t arg_3 = va_arg(ap, intptr_t);
    va_end(ap);

    EventBuffer::add_event(format, arg_1, arg_2, arg_3);
    EventBuffer::inc_indent();
  }
}

EventMark::~EventMark() {
  if (LogEvents) {
    EventBuffer::dec_indent();
    EventBuffer::add_event("done", 0, 0, 0);
  }
}

///////////////////////////////////////////////////////////////////////////

void eventlog_init() {
  EventBuffer::init();
}

int print_all_events(outputStream *st) {
  EventBuffer::print_all(st);
  return 1;
}

#else

void eventlog_init() {}
int print_all_events(outputStream *st) { return 0; }

#endif // PRODUCT
