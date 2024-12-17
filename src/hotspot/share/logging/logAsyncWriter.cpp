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
#include "memory/allocation.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/atomic.hpp"

class AsyncLogWriter::ProducerLocker : public StackObj {
 public:
  ProducerLocker() {
    assert(_instance != nullptr, "AsyncLogWriter::_lock is unavailable");
    _instance->_producer_lock.lock();
  }

  ~ProducerLocker() {
    _instance->_producer_lock.unlock();
  }

  void notify() { _instance->_consumer_lock.notify(); }
  void wait() { _instance->_consumer_lock.wait(0); }
};

class AsyncLogWriter::ConsumerLocker : public StackObj {
 public:
  ConsumerLocker() {
    assert(_instance != nullptr, "AsyncLogWriter::_lock is unavailable");
    _instance->_consumer_lock.lock();
  }

  ~ConsumerLocker() {
    _instance->_consumer_lock.unlock();
  }

  void notify() { _instance->_consumer_lock.notify(); }
  void wait() { _instance->_consumer_lock.wait(0); }
};

// LogDecorator::None applies to 'constant initialization' because of its constexpr constructor.
const LogDecorations& AsyncLogWriter::None = LogDecorations(LogLevel::Warning, LogTagSetMapping<LogTag::__NO_TAG>::tagset(),
                                      LogDecorators::None);

bool AsyncLogWriter::Buffer::push_back(LogFileStreamOutput* output, const LogDecorations& decorations, const char* msg, const size_t msg_len) {
  const size_t len = msg_len;
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
  bool result = push_back(nullptr, AsyncLogWriter::None, "", 0);
  assert(result, "fail to enqueue the flush token.");
}

void AsyncLogWriter::enqueue_locked(ConsumerLocker& clocker, LogFileStreamOutput* output, const LogDecorations& decorations, const char* msg) {
  // To save space and streamline execution, we just ignore null message.
  // client should use "" instead.
  assert(msg != nullptr, "enqueuing a null message!");

  size_t msg_len = strlen(msg);

  if (_buffer->push_back(output, decorations, msg, msg_len)) {
    _data_available = true;
    clocker.notify();
    return;
  }

  if (LogConfiguration::async_mode() == LogConfiguration::AsyncMode::Stall) {
    size_t size = Message::calc_size(msg_len);
    void* ptr = os::malloc(size, mtLogging);
    if (ptr == nullptr) {
      // Out of memory. We bail without any notice.
      // Some other part of the system will probably fail later.
      return;
    }
    new (ptr) Message(output, decorations, msg, msg_len);
    _stalled_message = (Message*)ptr;
    while (_stalled_message != nullptr) {
      clocker.wait();
    }
    os::free(ptr);
  } else {
    bool p_created;
    uint32_t* counter = _stats.put_if_absent(output, 0, &p_created);
    *counter = *counter + 1;
  }
}

void AsyncLogWriter::enqueue(LogFileStreamOutput& output, const LogDecorations& decorations, const char* msg) {
  ProducerLocker plocker;
  ConsumerLocker clocker;
  enqueue_locked(clocker, &output, decorations, msg);
}

// LogMessageBuffer consists of a multiple-part/multiple-line message.
// The lock here guarantees its integrity.
void AsyncLogWriter::enqueue(LogFileStreamOutput& output, LogMessageBuffer::Iterator msg_iterator) {
  ProducerLocker plocker;
  ConsumerLocker clocker;
  for (; !msg_iterator.is_at_end(); msg_iterator++) {
    enqueue_locked(clocker, &output, msg_iterator.decorations(), msg_iterator.message());
  }
}

AsyncLogWriter::AsyncLogWriter()
  : _flush_sem(0),
    _producer_lock(),
    _consumer_lock(),
    _data_available(false),
    _initialized(false),
    _stats(),
    _stalled_message(nullptr) {

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
      ConsumerLocker clocker;

      while (!_data_available && _stalled_message == nullptr) {
        clocker.wait();
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

    if (_stalled_message != nullptr) {
      assert(LogConfiguration::async_mode() == LogConfiguration::AsyncMode::Stall, "must be");
      ConsumerLocker clocker;
      Message* m = (Message*)_stalled_message;
      m->output()->write_blocking(m->decorations(), m->message());
      _stalled_message = nullptr;
      clocker.notify();
    }
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
      ProducerLocker plocker;
      ConsumerLocker clocker;
      // Push directly in-case we are at logical max capacity, as this must not get dropped.
      _instance->_buffer->push_flush_token();
      _instance->_data_available = true;
      clocker.notify();
    }

    _instance->_flush_sem.wait();
  }
}

AsyncLogWriter::BufferUpdater::BufferUpdater(size_t newsize) {
  ConsumerLocker clocker;
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
    ConsumerLocker clocker;

    delete p->_buffer;
    delete p->_buffer_staging;
    p->_buffer = _buf1;
    p->_buffer_staging = _buf2;
  }
}
