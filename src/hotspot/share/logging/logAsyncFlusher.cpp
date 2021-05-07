/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
#include "jvm.h"
#include "logging/logAsyncFlusher.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logFileOutput.hpp"
#include "logging/logHandle.hpp"
#include "runtime/atomic.hpp"

void AsyncLogMessage::writeback() {
  if (_message != NULL) {
    assert(_decorations != NULL, "sanity check");
    _output.write_blocking(*_decorations, _message);
  }
}

void LogAsyncFlusher::enqueue_impl(const AsyncLogMessage& msg) {
  assert_lock_strong(&_lock);

  if (_buffer.size() >= _buffer_max_size)  {
    const AsyncLogMessage* h = _buffer.front();
    assert(h != NULL, "sanity check");

    if (h->message() != nullptr) {
      bool p_created;
      uintx* counter = _stats.add_if_absent(h->output(), 0, &p_created);
      *counter = *counter + 1;

      if (Verbose) {
        // Temporarily turn off SerializeVMOutput so defaultStream will not
        // invoke set_owner(self) for tty_lock.
        FlagSetting t(SerializeVMOutput, false);
        // The writing below can not guarantee non-blocking because tty may be piped by the filesystems
        // or throttled by XOFF, so only dump the dropping message in Verbose mode.
        tty->print_cr("asynclog dropping message: %s", h->message());
      }
    }

    _buffer.pop_front();
  }
  assert(_buffer.size() < _buffer_max_size, "_buffer is over-sized.");
  _buffer.push_back(msg);

  // notify asynclog thread if occupancy is over 3/4
  size_t sz = _buffer.size();
  if (sz > (_buffer_max_size >> 2) * 3 ) {
    _lock.notify();
  }
}

void LogAsyncFlusher::enqueue(LogFileOutput& output, const LogDecorations& decorations, const char* msg) {
  AsyncLogMessage m(output, decorations, msg);

  { // critical area
    // The rank of _lock is same as _tty_lock on purpuse.
    // if logging thread is holding _tty_lock now, temporarily yield to _lock.
    ttyUnlocker ttyul;
    MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);
    enqueue_impl(m);
  }
}

// LogMessageBuffer consists of a multiple-part/multiple-line messsages.
// the mutex here gurantees its integrity.
void LogAsyncFlusher::enqueue(LogFileOutput& output, LogMessageBuffer::Iterator msg_iterator) {
  ttyUnlocker ttyul;
  MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);

  for (; !msg_iterator.is_at_end(); msg_iterator++) {
    AsyncLogMessage m(output, msg_iterator.decorations(), msg_iterator.message());
    enqueue_impl(m);
  }
}

LogAsyncFlusher::LogAsyncFlusher()
  : _state(ThreadState::Running),
    _lock(Mutex::tty, "async-log-monitor", true /* allow_vm_block */, Mutex::_safepoint_check_never),
    _stats(17 /*table_size*/) {
  if (os::create_thread(this, os::asynclog_thread)) {
    os::start_thread(this);
  }

  log_info(logging)("The maximum entries of AsyncLogBuffer: " SIZE_FORMAT ", estimated memory use: " SIZE_FORMAT " bytes",
                    _buffer_max_size, AsyncLogBufferSize);
}

bool AsyncLogMapIterator::do_entry(LogFileOutput* output, uintx* counter) {
  using dummy = LogTagSetMapping<LogTag::__NO_TAG>;
  LogDecorations decorations(LogLevel::Warning, dummy::tagset(), output->decorators());
  const int sz = 128;
  char out_of_band[sz];

  if (*counter > 0) {
    jio_snprintf(out_of_band, sz, UINTX_FORMAT_W(6) " messages dropped...", *counter);
    output->write_blocking(decorations, out_of_band);
    *counter = 0;
  }

  return true;
}

void LogAsyncFlusher::writeback(const LinkedList<AsyncLogMessage>& logs) {
  LinkedListIterator<AsyncLogMessage> it(logs.head());
  while (!it.is_empty()) {
    AsyncLogMessage* e = it.next();
    e->writeback();
    e->destroy();
  }
}

void LogAsyncFlusher::flush(bool with_lock) {
  LinkedListImpl<AsyncLogMessage, ResourceObj::C_HEAP, mtLogging> logs;

  if (with_lock) { // critical area
    // Caveat: current thread must not hold _tty_lock or other lower rank lockers.
    // Cannot install ttyUnlocker here because flush() may be invoked before defaultStream
    // initialization.
    MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);
    _buffer.pop_all(&logs);
    AsyncLogMapIterator iter;
    _stats.iterate(&iter);
  } else {
    // C++ lambda can simplify the code snippet.
    _buffer.pop_all(&logs);
    AsyncLogMapIterator iter;
    _stats.iterate(&iter);
  }

  writeback(logs);
}

void LogAsyncFlusher::run() {
  while (_state == ThreadState::Running) {
    {
      MonitorLocker m(&_lock, Mutex::_no_safepoint_check_flag);
      m.wait(500 /* ms, timeout*/);
    }
    flush();
  }

  // Signal thread has terminated
  MonitorLocker ml(Terminator_lock);
  _state = ThreadState::Terminated;
  ml.notify_all();
}

LogAsyncFlusher* LogAsyncFlusher::_instance = nullptr;

void LogAsyncFlusher::initialize() {
  if (!LogConfiguration::is_async_mode()) return;

  if (_instance == NULL) {
    Atomic::release_store(&LogAsyncFlusher::_instance, new LogAsyncFlusher());
  }
}

// Termination
// 1. issue an atomic release_store to close the logging window.
// 2. flush itself in-place.
// 3. signal asynclog thread to exit.
// 4. wait until asynclog thread exits.
// 5. (optional) delete this in post_run().
void LogAsyncFlusher::terminate() {
  if (_instance != NULL) {
    LogAsyncFlusher* self = _instance;

    Atomic::release_store<LogAsyncFlusher*, LogAsyncFlusher*>(&_instance, nullptr);
    self->flush();
    {
      MonitorLocker ml(&self->_lock, Mutex::_no_safepoint_check_flag);
      self->_state = ThreadState::Terminating;
      ml.notify();
    }
    {
      MonitorLocker ml(Terminator_lock, Mutex::_no_safepoint_check_flag);
      while (self->_state != ThreadState::Terminated) {
        ml.wait();
      }
    }
  }
}

LogAsyncFlusher* LogAsyncFlusher::instance() {
  if (Thread::current_or_null() != nullptr) {
    return _instance;
  } else {
    // current thread may has been detached.
    return nullptr;
  }
}

// Different from terminate(), abort is invoked by os::abort().
// There are 2 constraints:
// 1. must be async-safe because os::abort() may be invoked by a signal handler while other
// threads are executing.
// 2. must not obtain _lock. eg. gtest.MutexRank.mutex_lock_access_leaf(test_mutex_rank.cpp)
// holds a 'assess' lock and then traps SIGSEGV on purpose.
//
// Unlike terminate, abort() just ensures all pending log messages are flushed. It doesnot
// exit asynclog thread.
void LogAsyncFlusher::abort() {
  if (_instance != nullptr) {
    // To meet prior constraints, I borrow the idea in LogConfiguration::disable_outputs(),
    // the following code shut down all outputs for all tagsets with a RCU synchroniziation.
    // After then, I can flush pending queue without a lock.
    for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
      ts->disable_outputs();
    }
    _instance->flush(false /*with_lock*/);
  }
}
