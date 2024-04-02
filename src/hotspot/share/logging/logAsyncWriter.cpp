/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

void AsyncLogWriter::enqueue(LogFileStreamOutput& output, const LogDecorations& decorations, const char* msg) {
  size_t size = strlen(msg);
  _circular_buffer.enqueue(msg, size+1, &output, decorations);
}

// LogMessageBuffer consists of a multiple-part/multiple-line message.
// The lock here guarantees its integrity.
void AsyncLogWriter::enqueue(LogFileStreamOutput& output, LogMessageBuffer::Iterator msg_iterator) {
  _circular_buffer.enqueue(output, msg_iterator);
}

AsyncLogWriter::AsyncLogWriter(bool should_stall)
:
  _stats_lock(),
  _stats(),
  _circular_buffer(_stats, _stats_lock, align_up(AsyncLogBufferSize, os::vm_page_size()), should_stall),
  _initialized(false) {

  log_info(logging)("AsyncLogBuffer estimates memory use: " SIZE_FORMAT " bytes", align_up(AsyncLogBufferSize, os::vm_page_size()));
  if (os::create_thread(this, os::asynclog_thread)) {
    _initialized = true;
  } else {
    log_warning(logging, thread)("AsyncLogging failed to create thread. Falling back to synchronous logging.");
  }
}

bool AsyncLogWriter::write(AsyncLogMap<AnyObj::RESOURCE_AREA>& snapshot,
                           char* write_buffer, size_t write_buffer_size) {
  int req = 0;
  CircularStringBuffer::Message msg;
  while (_circular_buffer.has_message()) {
    using DequeueResult = CircularStringBuffer::DequeueResult;
    DequeueResult result = _circular_buffer.dequeue(&msg, write_buffer, write_buffer_size);
    assert(result != DequeueResult::NoMessage, "Race detected but there is only one reading thread");
    if (result == DequeueResult::TooSmall) {
      // Need a larger buffer
      return false;
    }

    if (!msg.is_token()) {
      msg.output->write_blocking(msg.decorations, write_buffer);
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
    _circular_buffer.signal_flush();
  }
  return true;
}

void AsyncLogWriter::run() {
  // 16KiB ought to be enough.
  size_t write_buffer_size = 16 * 1024;
  char* write_buffer = NEW_C_HEAP_ARRAY(char, write_buffer_size, mtLogging);

  while (true) {
    ResourceMark rm;
    AsyncLogMap<AnyObj::RESOURCE_AREA> snapshot;
    _circular_buffer.await_message();

    // move counters to snapshot and reset them.
    {
      _stats_lock.lock();
      _stats.iterate([&](LogFileStreamOutput* output, uint32_t& counter) {
        if (counter > 0) {
          bool created = snapshot.put(output, counter);
          assert(created == true, "sanity check");
          counter = 0;
        }
        return true;
      });
      _stats_lock.unlock();
    }
    bool success = write(snapshot, write_buffer, write_buffer_size);
    if (!success) {
      // Buffer was too small, double it.
      FREE_C_HEAP_ARRAY(char, write_buffer);
      write_buffer_size *= 2;
      write_buffer = NEW_C_HEAP_ARRAY(char, write_buffer_size, mtLogging);
    }
  }
}

AsyncLogWriter* AsyncLogWriter::_instance = nullptr;

void AsyncLogWriter::initialize() {
  if (!LogConfiguration::is_async_mode()) return;

  assert(_instance == nullptr, "initialize() should only be invoked once.");

  AsyncLogWriter* self = new AsyncLogWriter(LogConfiguration::async_mode() == LogConfiguration::AsyncMode::Stall);
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
    _instance->_circular_buffer.flush();
  }
}
