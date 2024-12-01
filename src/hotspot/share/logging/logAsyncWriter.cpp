/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/logFileStreamOutput.hpp"
#include "logging/logHandle.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"
#include "runtime/os.inline.hpp"

class AsyncLogWriter::AsyncLogLocker : public StackObj {
 public:
  AsyncLogLocker() {
    assert(_instance != nullptr, "AsyncLogWriter::_lock is unavailable");
    _instance->_lock.lock();
  }

  ~AsyncLogLocker() {
    _instance->_lock.unlock();
  }
};

// LogDecorator::None applies to 'constant initialization' because of its constexpr constructor.
const LogDecorations& AsyncLogWriter::None = LogDecorations(LogLevel::Warning, LogTagSetMapping<LogTag::__NO_TAG>::tagset(),
                                      LogDecorators::None);

bool AsyncLogWriter::Buffer::push_back(LogFileStreamOutput* output, const LogDecorations& decorations, const char* msg) {
  const size_t len = strlen(msg);
  const size_t sz = Message::calc_size(len);
  const bool is_token = output == nullptr;
  // Always leave headroom for the flush token. Pushing a token must succeed.
  const size_t headroom = (!is_token) ? Message::calc_size(0) : 0;

  if (_pos + sz <= (_capacity - headroom)) {
    new(_buf + _pos) Message(output, decorations, msg, len);
    _pos += sz;
    return true;
  }

  return false;
}

void AsyncLogWriter::Buffer::push_flush_token() {
  bool result = push_back(nullptr, AsyncLogWriter::None, "");
  assert(result, "fail to enqueue the flush token.");
}

void AsyncLogWriter::enqueue_locked(LogFileStreamOutput* output, const LogDecorations& decorations, const char* msg) {
  // To save space and streamline execution, we just ignore null message.
  // client should use "" instead.
  assert(msg != nullptr, "enqueuing a null message!");

  if (!_buffer->push_back(output, decorations, msg)) {
    bool p_created;
    uint32_t* counter = _stats.put_if_absent(output, 0, &p_created);
    *counter = *counter + 1;
    return;
  }

  _data_available = true;
  _lock.notify();
}

void AsyncLogWriter::enqueue(LogFileStreamOutput& output, const LogDecorations& decorations, const char* msg) {
  AsyncLogLocker locker;
  enqueue_locked(&output, decorations, msg);
}

// LogMessageBuffer consists of a multiple-part/multiple-line message.
// The lock here guarantees its integrity.
void AsyncLogWriter::enqueue(LogFileStreamOutput& output, LogMessageBuffer::Iterator msg_iterator) {
  AsyncLogLocker locker;

  for (; !msg_iterator.is_at_end(); msg_iterator++) {
    enqueue_locked(&output, msg_iterator.decorations(), msg_iterator.message());
  }
}

AsyncLogWriter::AsyncLogWriter()
  : _flush_sem(0), _lock(), _data_available(false),
    _initialized(false),
    _stats() {

  size_t size = AsyncLogBufferSize / 2;
  _buffer = new Buffer(size);
  _buffer_staging = new Buffer(size);
  log_info(logging)("AsyncLogBuffer estimates memory use: " SIZE_FORMAT " bytes", size * 2);
  if (os::create_thread(this, os::asynclog_thread)) {
    _initialized = true;
  } else {
    log_warning(logging, thread)("AsyncLogging failed to create thread. Falling back to synchronous logging.");
  }
}

void AsyncLogWriter::write(AsyncLogMap<AnyObj::RESOURCE_AREA>& snapshot) {
  int req = 0;
  auto it = _buffer_staging->iterator();
  while (it.hasNext()) {
    const Message* e = it.next();

    if (!e->is_token()){
      e->output()->write_blocking(e->decorations(), e->message());
    } else {
      // This is a flush token. Record that we found it and then
      // signal the flushing thread after the loop.
      req++;
    }
  }

  LogDecorations decorations(LogLevel::Warning, LogTagSetMapping<LogTag::__NO_TAG>::tagset(),
                             LogDecorators::All);
  snapshot.iterate([&](LogFileStreamOutput* output, uint32_t& counter) {
    if (counter > 0) {
      stringStream ss;
      ss.print(UINT32_FORMAT_W(6) " messages dropped due to async logging", counter);
      output->write_blocking(decorations, ss.freeze());
    }
    return true;
  });

  if (req > 0) {
    assert(req == 1, "Only one token is allowed in queue. AsyncLogWriter::flush() is NOT MT-safe!");
    _flush_sem.signal(req);
  }
}

void AsyncLogWriter::run() {
  while (true) {
    ResourceMark rm;
    AsyncLogMap<AnyObj::RESOURCE_AREA> snapshot;
    {
      AsyncLogLocker locker;

      while (!_data_available) {
        _lock.wait(0/* no timeout */);
      }
      // Only doing a swap and statistics under the lock to
      // guarantee that I/O jobs don't block logsites.
      _buffer_staging->reset();
      swap(_buffer, _buffer_staging);

      // move counters to snapshot and reset them.
      _stats.iterate([&] (LogFileStreamOutput* output, uint32_t& counter) {
        if (counter > 0) {
          bool created = snapshot.put(output, counter);
          assert(created == true, "sanity check");
          counter = 0;
        }
        return true;
      });
      _data_available = false;
    }
    write(snapshot);
  }
}

AsyncLogWriter* AsyncLogWriter::_instance = nullptr;

void AsyncLogWriter::initialize() {
  if (!LogConfiguration::is_async_mode()) return;

  assert(_instance == nullptr, "initialize() should only be invoked once.");

  AsyncLogWriter* self = new AsyncLogWriter();
  if (self->_initialized) {
    Atomic::release_store_fence(&AsyncLogWriter::_instance, self);
    // All readers of _instance after the fence see non-null.
    // We use LogOutputList's RCU counters to ensure all synchronous logsites have completed.
    // After that, we start AsyncLog Thread and it exclusively takes over all logging I/O.
    for (LogTagSet* ts = LogTagSet::first(); ts != nullptr; ts = ts->next()) {
      ts->wait_until_no_readers();
    }
    os::start_thread(self);
    log_debug(logging, thread)("Async logging thread started.");
  } else {
    delete self;
  }
}

AsyncLogWriter* AsyncLogWriter::instance() {
  return _instance;
}

// Inserts a flush token into the async output buffer and waits until the AsyncLog thread
// signals that it has seen it and completed all dequeued message processing.
// This method is not MT-safe in itself, but is guarded by another lock in the usual
// usecase - see the comments in the header file for more details.
void AsyncLogWriter::flush() {
  if (_instance != nullptr) {
    {
      AsyncLogLocker locker;
      // Push directly in-case we are at logical max capacity, as this must not get dropped.
      _instance->_buffer->push_flush_token();
      _instance->_data_available = true;
      _instance->_lock.notify();
    }

    _instance->_flush_sem.wait();
  }
}

AsyncLogWriter::BufferUpdater::BufferUpdater(size_t newsize) {
  AsyncLogLocker locker;
  auto p = AsyncLogWriter::_instance;

  _buf1 = p->_buffer;
  _buf2 = p->_buffer_staging;
  p->_buffer = new Buffer(newsize);
  p->_buffer_staging = new Buffer(newsize);
}

AsyncLogWriter::BufferUpdater::~BufferUpdater() {
  AsyncLogWriter::flush();
  auto p = AsyncLogWriter::_instance;

  {
    AsyncLogLocker locker;

    delete p->_buffer;
    delete p->_buffer_staging;
    p->_buffer = _buf1;
    p->_buffer_staging = _buf2;
  }
}
