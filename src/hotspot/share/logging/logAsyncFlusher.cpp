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
#include "logging/logAsyncFlusher.hpp"
#include "logging/logFileOutput.hpp"
#include "logging/logHandle.hpp"
#include "runtime/atomic.hpp"

void AsyncLogMessage::writeback() {
  if (_message != NULL) {
    // should cache this object somehow
    LogDecorations decorations(_level, _tagset, _decorators);
    _output.write_blocking(decorations, _message);
  }
}

void LogAsyncFlusher::enqueue_impl(const AsyncLogMessage& msg) {
  assert_lock_strong(&_lock);

  if (_buffer.size() >= AsyncLogBufferSize)  {
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
  assert(_buffer.size() < AsyncLogBufferSize, "_buffer is over-sized.");
  _buffer.push_back(msg);

  size_t sz = _buffer.size();
  if (sz == (AsyncLogBufferSize >> 1) || sz == AsyncLogBufferSize) {
    _lock.notify();
  }
}

void LogAsyncFlusher::enqueue(LogFileOutput& output, const LogDecorations& decorations, const char* msg) {
  AsyncLogMessage m(output, decorations, msg);

  { // critical area
    MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);
    enqueue_impl(m);
  }
}

// LogMessageBuffer consists of a multiple-part/multiple-line messsage.
// the mutex here gurantees its interity.
void LogAsyncFlusher::enqueue(LogFileOutput& output, LogMessageBuffer::Iterator msg_iterator) {
  MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);

  for (; !msg_iterator.is_at_end(); msg_iterator++) {
    AsyncLogMessage m(output, msg_iterator.decorations(), msg_iterator.message());
    enqueue_impl(m);
  }
}

LogAsyncFlusher::LogAsyncFlusher()
  : _should_terminate(false),
    _lock(Mutex::tty, "async-log-monitor", true /* allow_vm_block */, Mutex::_safepoint_check_never),
    _stats(17 /*table_size*/) {
  if (os::create_thread(this, os::asynclog_thread)) {
    os::start_thread(this);
  }
}

bool AsyncLogMapIterator::do_entry(LogFileOutput* output, uintx* counter) {
  LogDecorators decorators = output->decorators();
  decorators.without(LogDecorators::tags_decorator);
  LogDecorations decorations(LogLevel::Warning, decorators);
  const int sz = 128;
  char out_of_band[sz];

  if (*counter > 0) {
    jio_snprintf(out_of_band, sz, UINTX_FORMAT " messages dropped...", *counter);
    output->write_blocking(decorations, out_of_band);
    *counter = 0;
  }

  return true;
}

void LogAsyncFlusher::flush() {
  LinkedListImpl<AsyncLogMessage, ResourceObj::C_HEAP, mtLogging> logs;

  { // critical area
    MutexLocker ml(&_lock, Mutex::_no_safepoint_check_flag);
    _buffer.pop_all(&logs);

    AsyncLogMapIterator iter;
    _stats.iterate(&iter);
  }

  LinkedListIterator<AsyncLogMessage> it(logs.head());
  while (!it.is_empty()) {
    AsyncLogMessage* e = it.next();
    e->writeback();
  }
}

void LogAsyncFlusher::run() {
  while (!_should_terminate) {
    {
      MonitorLocker m(&_lock, Mutex::_no_safepoint_check_flag);
      m.wait(LogAsyncInterval);
    }
    flush();
  }
}

LogAsyncFlusher* LogAsyncFlusher::_instance = nullptr;

void LogAsyncFlusher::initialize() {
  if (!_instance) {
    _instance = new LogAsyncFlusher();
  }
}

// Termination
// 1. issue an atomic store-&-fence to close the logging window.
// 2. flush itself in-place
// 3. signal the flusher thread to exit
// 4. (optional) deletes this in post_run()
void LogAsyncFlusher::terminate() {
  if (_instance != NULL) {
    LogAsyncFlusher* self = _instance;

    // make sure no new log entry will be enqueued after.
    Atomic::release_store_fence<LogAsyncFlusher*, LogAsyncFlusher*>(&_instance, nullptr);
    self->flush();
    {
      MonitorLocker m(&self->_lock, Mutex::_no_safepoint_check_flag);
      self->_should_terminate = true;
      m.notify();
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
