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
#include "logging/logAsyncWriter.hpp"
#include "logging/logConfiguration.hpp"
#include "logging/logFileOutput.hpp"
#include "logging/logHandle.hpp"
#include "runtime/atomic.hpp"

Semaphore AsyncLogWriter::_sem(0);
Semaphore AsyncLogWriter::_io_sem(1);

class AsyncLogLocker : public StackObj {
 private:
  static Semaphore _lock;
 public:
  AsyncLogLocker() {
    _lock.wait();
  }

  ~AsyncLogLocker() {
    _lock.signal();
  }
};

Semaphore AsyncLogLocker::_lock(1);

void AsyncLogWriter::enqueue_locked(const AsyncLogMessage& msg) {
  if (_buffer.size() >= _buffer_max_size)  {
    bool p_created;
    uint32_t* counter = _stats.add_if_absent(msg.output(), 0, &p_created);
    *counter = *counter + 1;
    // drop the enqueueing message.
    return;
  }

  assert(_buffer.size() < _buffer_max_size, "_buffer is over-sized.");
  _buffer.push_back(msg);
  _sem.signal();
}

void AsyncLogWriter::enqueue(LogFileOutput& output, const LogDecorations& decorations, const char* msg) {
  AsyncLogMessage m(output, decorations, os::strdup(msg));

  { // critical area
    AsyncLogLocker lock;
    enqueue_locked(m);
  }
}

// LogMessageBuffer consists of a multiple-part/multiple-line messsage.
// The lock here guarantees its integrity.
void AsyncLogWriter::enqueue(LogFileOutput& output, LogMessageBuffer::Iterator msg_iterator) {
  AsyncLogLocker lock;

  for (; !msg_iterator.is_at_end(); msg_iterator++) {
    AsyncLogMessage m(output, msg_iterator.decorations(), os::strdup(msg_iterator.message()));
    enqueue_locked(m);
  }
}

AsyncLogWriter::AsyncLogWriter()
  : _initialized(false),
    _stats(17 /*table_size*/) {
  if (os::create_thread(this, os::asynclog_thread)) {
    _initialized = true;
  } else {
    log_warning(logging, thread)("AsyncLogging failed to create thread. Falling back to synchronous logging.");
  }

  log_info(logging)("The maximum entries of AsyncLogBuffer: " SIZE_FORMAT ", estimated memory use: " SIZE_FORMAT " bytes",
                    _buffer_max_size, AsyncLogBufferSize);
}

class AsyncLogMapIterator {
  AsyncLogBuffer& _logs;

 public:
  AsyncLogMapIterator(AsyncLogBuffer& logs) :_logs(logs) {}
  bool do_entry(LogFileOutput* output, uint32_t* counter) {
    using none = LogTagSetMapping<LogTag::__NO_TAG>;

    if (*counter > 0) {
      LogDecorations decorations(LogLevel::Warning, none::tagset(), output->decorators());
      stringStream ss;
      ss.print(UINT32_FORMAT_W(6) " messages dropped due to async logging", *counter);
      AsyncLogMessage msg(*output, decorations, ss.as_string(true /*c_heap*/));
      _logs.push_back(msg);
      *counter = 0;
    }

    return true;
  }
};

void AsyncLogWriter::write() {
  // Use kind of copy-and-swap idiom here.
  // Empty 'logs' swaps the content with _buffer.
  // Along with logs destruction, all processed messages are deleted.
  //
  // The operation 'pop_all()' is done in O(1). All I/O jobs are then performed without
  // lock protection. This guarantees I/O jobs don't block logsites.
  AsyncLogBuffer logs;
  bool own_io = false;

  { // critical region
    AsyncLogLocker lock;

    _buffer.pop_all(&logs);
    // append meta-messages of dropped counters
    AsyncLogMapIterator dropped_counters_iter(logs);
    _stats.iterate(&dropped_counters_iter);
    own_io = _io_sem.trywait();
  }

  LinkedListIterator<AsyncLogMessage> it(logs.head());
  if (!own_io) {
    _io_sem.wait();
  }

  while (!it.is_empty()) {
    AsyncLogMessage* e = it.next();
    char* msg = e->message();

    if (msg != nullptr) {
      e->output()->write_blocking(e->decorations(), msg);
      os::free(msg);
    }
  }
  _io_sem.signal();
}

void AsyncLogWriter::run() {
  while (true) {
    // The value of a semphore cannot be negative. Therefore, the current thread falls asleep
    // when its value is zero. It will be waken up when new messages are enqueued.
    _sem.wait();
    write();
  }
}

AsyncLogWriter* AsyncLogWriter::_instance = nullptr;

void AsyncLogWriter::initialize() {
  if (!LogConfiguration::is_async_mode()) return;

  assert(_instance == nullptr, "initialize() should only be invoked once.");

  AsyncLogWriter* self = new AsyncLogWriter();
  if (self->_initialized) {
    Atomic::release_store_fence(&AsyncLogWriter::_instance, self);
    // All readers of _instance after the fence see non-NULL.
    // We use LogOutputList's RCU counters to ensure all synchronous logsites have completed.
    // After that, we start AsyncLog Thread and it exclusively takes over all logging I/O.
    for (LogTagSet* ts = LogTagSet::first(); ts != NULL; ts = ts->next()) {
      ts->wait_until_no_readers();
    }
    os::start_thread(self);
    log_debug(logging, thread)("Async logging thread started.");
  }
}

AsyncLogWriter* AsyncLogWriter::instance() {
  return _instance;
}

// write() acquires and releases _io_sem even _buffer is empty.
// This guarantees all logging I/O of dequeued messages are done when it returns.
void AsyncLogWriter::flush() {
  if (_instance != nullptr) {
    _instance->write();
  }
}
