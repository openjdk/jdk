/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_SERVICE_JFREVENT_HPP
#define SHARE_JFR_RECORDER_SERVICE_JFREVENT_HPP

#include "jfr/recorder/jfrEventSetting.inline.hpp"
#include "jfr/recorder/service/jfrEventThrottler.hpp"
#include "jfr/recorder/stacktrace/jfrStackTraceRepository.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "jfr/writers/jfrNativeEventWriter.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/ticks.hpp"
#ifdef ASSERT
#include "utilities/bitMap.hpp"
#endif

#ifdef ASSERT
class JfrEventVerifier {
  template <typename>
  friend class JfrEvent;
 private:
  // Verification of fields.
  BitMap::bm_word_t _verification_storage[1];
  BitMapView _verification_bit_map;
  bool _committed;

  JfrEventVerifier();
  void check(BitMap::idx_t field_idx) const;
  void set_field_bit(size_t field_idx);
  bool verify_field_bit(size_t field_idx) const;
  void set_committed();
  void clear_committed();
  bool committed() const;
};
#endif // ASSERT

template <typename T>
class JfrEvent {
 private:
  jlong _start_time;
  jlong _end_time;
  bool _untimed;
  bool _should_commit;
  bool _evaluated;

 protected:
  JfrEvent(EventStartTime timing=TIMED) : _start_time(0), _end_time(0),
                                          _untimed(timing == UNTIMED),
                                          _should_commit(false), _evaluated(false)
#ifdef ASSERT
  , _verifier()
#endif
  {
    if (!T::isInstant && !_untimed && is_enabled()) {
      set_starttime(JfrTicks::now());
    }
  }

  void commit() {
    assert(!_verifier.committed(), "event already committed");
    if (!should_write()) {
      return;
    }
    write_event();
    DEBUG_ONLY(_verifier.set_committed();)
  }

 public:
  void set_starttime(const JfrTicks& time) {
    _start_time = time.value();
  }

  void set_endtime(const JfrTicks& time) {
    _end_time = time.value();
  }

  void set_starttime(const Ticks& time) {
    _start_time = JfrTime::is_ft_enabled() ? time.ft_value() : time.value();
  }

  void set_endtime(const Ticks& time) {
    _end_time = JfrTime::is_ft_enabled() ? time.ft_value() : time.value();
  }

  static bool is_enabled() {
    return JfrEventSetting::is_enabled(T::eventId);
  }

  static bool is_stacktrace_enabled() {
    return JfrEventSetting::has_stacktrace(T::eventId);
  }

  static JfrEventId id() {
    return T::eventId;
  }

  static bool is_instant() {
    return T::isInstant;
  }

  static bool is_requestable() {
    return T::isRequestable;
  }

  static bool has_thread() {
    return T::hasThread;
  }

  static bool has_stacktrace() {
    return T::hasStackTrace;
  }

  bool is_started() {
    return is_instant() || _start_time != 0 || _untimed;
  }

  bool should_commit() {
    if (!is_enabled()) {
      return false;
    }
    if (_untimed) {
      return true;
    }
    _should_commit = evaluate();
    _evaluated = true;
    return _should_commit;
  }

 private:
  bool should_write() {
    if (_evaluated) {
      return _should_commit;
    }
    if (!is_enabled()) {
      return false;
    }
    return evaluate() && JfrThreadLocal::is_included(Thread::current());
  }

  bool evaluate() {
    if (_start_time == 0) {
      set_starttime(JfrTicks::now());
    } else if (_end_time == 0) {
      set_endtime(JfrTicks::now());
    }
    if (T::isInstant || T::isRequestable) {
      return T::hasThrottle ? JfrEventThrottler::accept(T::eventId, _untimed ? 0 : _start_time) : true;
    }
    if (_end_time - _start_time < JfrEventSetting::threshold(T::eventId)) {
      return false;
    }
    return T::hasThrottle ? JfrEventThrottler::accept(T::eventId, _untimed ? 0 : _end_time) : true;
  }

  traceid thread_id(Thread* thread) {
    return T::hasThread ? JfrThreadLocal::thread_id(thread) : 0;
  }

  traceid stack_trace_id(Thread* thread, const JfrThreadLocal* tl) {
    return T::hasStackTrace && is_stacktrace_enabled() ?
      tl->has_cached_stack_trace() ? tl->cached_stack_trace_id() :
        JfrStackTraceRepository::record(thread) : 0;
  }

  /*
   * Support for virtual threads involves oops, access of which may trigger
   * events, i.e. load barriers. Hence, write_event() must be re-entrant
   * for recursion. Getting the thread id and capturing a stacktrace may
   * involve oop access, and are therefore hoisted before claiming a buffer
   * and binding it to a writer.
   */
  void write_event() {
    DEBUG_ONLY(assert_precondition();)
    Thread* const thread = Thread::current();
    JfrThreadLocal* const tl = thread->jfr_thread_local();
    const traceid tid = thread_id(thread);
    const traceid sid = stack_trace_id(thread, tl);
    // Keep tid and sid above this line.
    JfrBuffer* const buffer = tl->native_buffer();
    if (buffer == nullptr) {
      // Most likely a pending OOM.
      return;
    }
    bool large = is_large();
    if (write_sized_event(buffer, thread, tid, sid, large)) {
      // Event written successfully
      return;
    }
    if (!large) {
      // Try large size.
      if (write_sized_event(buffer, thread, tid, sid, true)) {
        // Event written successfully, use large size from now on.
        set_large();
      }
    }
  }

  bool write_sized_event(JfrBuffer* buffer, Thread* thread, traceid tid, traceid sid, bool large_size) {
    JfrNativeEventWriter writer(buffer, thread);
    writer.begin_event_write(large_size);
    writer.write<u8>(T::eventId);
    assert(_start_time != 0, "invariant");
    writer.write(_start_time);
    if (!(T::isInstant || T::isRequestable) || T::hasCutoff) {
      assert(_end_time != 0, "invariant");
      writer.write(_end_time - _start_time);
    }
    if (T::hasThread) {
      writer.write(tid);
    }
    if (T::hasStackTrace) {
      writer.write(sid);
    }
    // Payload.
    static_cast<T*>(this)->writeData(writer);
    return writer.end_event_write(large_size) > 0;
  }

  static bool is_large() {
    return JfrEventSetting::is_large(T::eventId);
  }

  static void set_large() {
    JfrEventSetting::set_large(T::eventId);
  }

#ifdef ASSERT
 private:
  // Verification of fields.
  JfrEventVerifier _verifier;

  void assert_precondition() {
    assert(T::eventId >= FIRST_EVENT_ID, "event id underflow invariant");
    assert(T::eventId <= LAST_EVENT_ID, "event id overflow invariant");
    DEBUG_ONLY(static_cast<T*>(this)->verify());
  }

 protected:
  void set_field_bit(size_t field_idx) {
    _verifier.set_field_bit(field_idx);
    // It is ok to reuse an already committed event
    // granted you provide new informational content.
    _verifier.clear_committed();
  }

  bool verify_field_bit(size_t field_idx) const {
    return _verifier.verify_field_bit(field_idx);
  }
#endif // ASSERT
};

#endif // SHARE_JFR_RECORDER_SERVICE_JFREVENT_HPP
